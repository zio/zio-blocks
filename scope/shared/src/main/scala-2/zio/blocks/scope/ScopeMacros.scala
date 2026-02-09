package zio.blocks.scope

import scala.reflect.macros.whitebox
import zio.blocks.scope.internal.{MacroCore => MC}

private[scope] object ScopeMacros {

  private sealed trait WireKind
  private object WireKind {
    case object Shared extends WireKind
    case object Unique extends WireKind
  }

  def sharedImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Shared[_, T]] =
    deriveWire[T](c)(WireKind.Shared).asInstanceOf[c.Expr[Wire.Shared[_, T]]]

  def uniqueImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Unique[_, T]] =
    deriveWire[T](c)(WireKind.Unique).asInstanceOf[c.Expr[Wire.Unique[_, T]]]

  private def deriveWire[T: c.WeakTypeTag](c: whitebox.Context)(kind: WireKind): c.Expr[Wire[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    if (!sym.isClass || sym.asClass.isTrait || sym.asClass.isAbstract) {
      MC.abortNotAClass(c)(tpe.toString)
    }

    val ctor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(MC.abortNoPrimaryCtor(c)(tpe.toString))

    val paramLists = ctor.paramLists

    val allDepTypes: List[Type] = paramLists.flatten.flatMap { param =>
      val paramType = param.typeSignature
      MC.classifyAndExtractDep(c)(paramType)
    }

    MC.checkSubtypeConflicts(c)(allDepTypes) match {
      case Some((subtype, supertype)) => MC.abortSubtypeConflict(c)(tpe.toString, subtype, supertype)
      case None                       =>
    }

    val isAutoCloseable = tpe <:< typeOf[AutoCloseable]
    val inType          =
      if (allDepTypes.isEmpty) typeOf[Any]
      else allDepTypes.reduceLeft((a, b) => c.universe.internal.refinedType(List(a, b), NoSymbol))

    def generateArgs(params: List[Symbol]): List[Tree] =
      params.map { param =>
        val paramType = param.typeSignature
        if (MC.isFinalizerType(c)(paramType)) {
          q"finalizer"
        } else {
          q"ctx.get[$paramType]"
        }
      }

    val argLists = paramLists.map(generateArgs)
    val ctorCall = if (argLists.isEmpty) {
      q"new $tpe()"
    } else {
      argLists.foldLeft[Tree](Select(New(TypeTree(tpe)), termNames.CONSTRUCTOR)) { (acc, args) =>
        Apply(acc, args)
      }
    }

    val wireFactory = kind match {
      case WireKind.Shared => q"_root_.zio.blocks.scope.Wire.Shared"
      case WireKind.Unique => q"_root_.zio.blocks.scope.Wire.Unique"
    }

    val wireBody = if (isAutoCloseable) {
      q"""
        val instance = $ctorCall
        finalizer.defer(instance.asInstanceOf[AutoCloseable].close())
        instance
      """
    } else {
      q"""
        $ctorCall
      """
    }

    val result = q"$wireFactory.apply[$inType, $tpe] { (finalizer, ctx) => $wireBody }"
    c.Expr[Wire[_, T]](result)
  }
}
