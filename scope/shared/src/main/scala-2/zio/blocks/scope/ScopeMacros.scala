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
    deriveWireWithWireable[T](c)(WireKind.Shared).asInstanceOf[c.Expr[Wire.Shared[_, T]]]

  def uniqueImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Unique[_, T]] =
    deriveWireWithWireable[T](c)(WireKind.Unique).asInstanceOf[c.Expr[Wire.Unique[_, T]]]

  private def deriveWireWithWireable[T: c.WeakTypeTag](c: whitebox.Context)(kind: WireKind): c.Expr[Wire[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    val wireableTpe =
      c.typecheck(q"_root_.scala.Predef.implicitly[_root_.zio.blocks.scope.Wireable[$tpe]]", silent = true)

    if (wireableTpe.nonEmpty && wireableTpe.tpe != NoType) {
      val actualImplicitTpe = wireableTpe match {
        case Apply(_, List(implicitVal)) if implicitVal.symbol != null && implicitVal.symbol != NoSymbol =>
          implicitVal.symbol.typeSignature
        case _ =>
          wireableTpe.tpe
      }
      val inType                    = extractWireableInType(c)(actualImplicitTpe)
      val (wireTpe, wireMethodName) = kind match {
        case WireKind.Shared =>
          (appliedType(typeOf[Wire.Shared[_, _]].typeConstructor, List(inType, tpe)), TermName("shared"))
        case WireKind.Unique =>
          (appliedType(typeOf[Wire.Unique[_, _]].typeConstructor, List(inType, tpe)), TermName("unique"))
      }
      val result = q"$wireableTpe.wire.$wireMethodName.asInstanceOf[$wireTpe]"
      c.Expr(result)(c.WeakTypeTag(wireTpe))
    } else {
      if (!sym.isClass || sym.asClass.isTrait || sym.asClass.isAbstract) {
        MC.abortNotAClass(c)(tpe.toString)
      }
      deriveWire[T](c)(kind)
    }
  }

  /** Extract the In type member from a Wireable type */
  private def extractWireableInType(c: whitebox.Context)(wireableTpe: c.Type): c.Type = {
    import c.universe._

    val unwrapped = wireableTpe match {
      case NullaryMethodType(resultType) => resultType
      case other                         => other
    }

    unwrapped match {
      case TypeRef(_, sym, args) if sym.fullName == "zio.blocks.scope.Wireable.Typed" && args.nonEmpty =>
        return args.head
      case _ =>
    }

    val dealiased = wireableTpe.dealias
    dealiased match {
      case RefinedType(_, scope) =>
        val inSym = scope.find(_.name == TypeName("In"))
        inSym.map { sym =>
          sym.typeSignature match {
            case TypeBounds(lo, _) if !(lo =:= typeOf[Nothing]) => lo.dealias
            case TypeBounds(_, hi)                              => hi.dealias
            case t                                              => t.dealias
          }
        }.getOrElse(typeOf[Any])
      case _ =>
        val inMember = dealiased.member(TypeName("In"))
        if (inMember != NoSymbol) {
          val sig = inMember.typeSignatureIn(dealiased).dealias
          sig match {
            case TypeBounds(lo, hi) if lo =:= hi => lo.dealias
            case TypeBounds(_, hi)               => hi.dealias
            case t                               => t
          }
        } else {
          typeOf[Any]
        }
    }
  }

  private def deriveWire[T: c.WeakTypeTag](c: whitebox.Context)(kind: WireKind): c.Expr[Wire[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

    val ctor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(MC.abortNoPrimaryCtor(c)(tpe.toString))

    val paramLists = ctor.paramLists

    val allDepTypes: List[Type] = paramLists.flatten.flatMap { param =>
      val paramType = param.typeSignature
      MC.classifyAndExtractDep(c)(paramType)
    }

    MC.checkSubtypeConflicts(c)(tpe.toString, allDepTypes) match {
      case Some(error) => MC.abort(c)(error)
      case None        =>
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
