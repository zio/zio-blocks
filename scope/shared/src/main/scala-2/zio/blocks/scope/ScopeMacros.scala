package zio.blocks.scope

import scala.reflect.macros.whitebox
import zio.blocks.scope.internal.{MacroCore => MC}

private[scope] object ScopeMacros {

  /**
   * Scala 2 macro implementation for Scope.leak.
   *
   * Emits a compiler warning via MacroCore.warnLeak and returns the unwrapped
   * value (using asInstanceOf which is sound since $[A] = A at runtime).
   */
  def leakImpl(c: whitebox.Context)(sa: c.Tree): c.Tree = {
    import c.universe._

    val self = c.prefix.tree

    // Extract source code for the warning message
    val sourceCode = sa match {
      case Ident(name)     => name.toString
      case Select(_, name) => name.toString
      case _               => show(sa)
    }

    // Extract scope name for the warning message
    val scopeName = self.tpe.widen.toString

    MC.warnLeak(c)(sa.pos, sourceCode, scopeName)

    // Extract the underlying type A from $[A]
    val saType         = sa.tpe.widen
    val underlyingType = saType match {
      case TypeRef(_, sym, List(arg)) if sym.name.toString == "$" => arg
      case _                                                      => saType
    }

    q"$sa.asInstanceOf[$underlyingType]"
  }

  /**
   * Production Scope.scoped macro implementation for Scala 2.
   *
   * This macro enables ergonomic `scoped { child => ... }` syntax in Scala 2
   * where SAM conversion fails for path-dependent return types.
   *
   * The macro:
   *   1. Accepts a lambda with Any return type (enables SAM conversion)
   *   2. Type-checks the lambda body to extract the actual return type
   *   3. Validates scope ownership (child.$[A] must belong to the child scope)
   *   4. Validates Unscoped[A] evidence
   *   5. Generates properly typed code with full finalizer handling
   */
  def scopedImpl(c: whitebox.Context)(f: c.Tree): c.Tree = {
    import c.universe._

    val self = c.prefix.tree

    // Type-check f to get its type
    val fTyped = c.typecheck(f)

    // Extract the return type from the lambda body directly
    val (bodyType: Type, lambdaParamName: Option[TermName]) = fTyped match {
      case Function(List(param), body) =>
        (body.tpe.widen, Some(param.name))
      case Block(_, Function(List(param), body)) =>
        (body.tpe.widen, Some(param.name))
      case _ =>
        c.abort(
          c.enclosingPosition,
          "In Scala 2, scoped blocks must be lambda literals: `.scoped { child => ... }`. " +
            "Passing a variable or method reference is not supported."
        )
    }

    // Extract underlying type from child.$[T] -> T
    // Also validate that the $ belongs to the lambda parameter (the child scope)
    val underlyingType = bodyType match {
      case TypeRef(pre, sym, args) if sym.name.toString == "$" && args.nonEmpty =>
        val underlying = args.head

        // Check if prefix matches lambda parameter
        val prefixName = pre match {
          case SingleType(_, termName) => Some(termName.name)
          case _                       => None
        }

        // Validate ownership: prefix must be the lambda parameter
        (prefixName, lambdaParamName) match {
          case (Some(pn), Some(lpn)) if pn != lpn =>
            c.abort(
              c.enclosingPosition,
              s"Scope mismatch: returning $bodyType but expected ${lpn}.$$[...]. " +
                s"Cannot return a value from scope '$pn' inside scope '$lpn'."
            )
          case _ => // OK
        }

        underlying
      case _ =>
        bodyType
    }

    // Validate Unscoped[underlyingType] if not Any
    if (!(underlyingType =:= typeOf[Any])) {
      val unscopedType     = appliedType(typeOf[Unscoped[_]].typeConstructor, underlyingType)
      val unscopedInstance = c.inferImplicitValue(unscopedType)

      if (unscopedInstance == EmptyTree) {
        c.abort(
          c.enclosingPosition,
          s"Cannot return $bodyType from scoped block: no Unscoped[$underlyingType] instance found. " +
            s"Only types with Unscoped evidence can escape scope boundaries."
        )
      }
    }

    // Generate production code with full finalizer handling.
    // If the parent scope is already closed, create a born-closed child
    // so that operations inside the block become no-ops.
    q"""
      val parent = $self
      val fins = if (parent.isClosed) _root_.zio.blocks.scope.internal.Finalizers.closed
                 else new _root_.zio.blocks.scope.internal.Finalizers
      val child = new _root_.zio.blocks.scope.Scope.Child[parent.type](
        parent,
        fins
      )
      var primary: Throwable = null
      var unwrapped: $underlyingType = null.asInstanceOf[$underlyingType]
      try {
        val rawResult: Any = $f(child)
        unwrapped = rawResult.asInstanceOf[$underlyingType]
      } catch {
        case t: Throwable =>
          primary = t
          throw t
      } finally {
        val errors = child.close()
        if (primary != null) {
          errors.foreach(primary.addSuppressed)
        } else if (errors.nonEmpty) {
          val first = errors.head
          errors.tail.foreach(first.addSuppressed)
          throw first
        }
      }
      unwrapped
    """
  }

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
