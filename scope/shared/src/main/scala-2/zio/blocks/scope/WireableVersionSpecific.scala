package zio.blocks.scope

import scala.language.experimental.macros

private[scope] trait WireableVersionSpecific {

  /**
   * Derives a [[Wireable]] for type T from its primary constructor.
   *
   * Constructor parameters are analyzed to determine dependencies:
   *   - Regular parameters: become dependencies (part of `In` type)
   *   - `Finalizer` parameters: finalizer is passed
   *
   * The `In` type is the intersection of all dependencies.
   *
   * For AutoCloseable types, `close()` is automatically registered as a
   * finalizer.
   *
   * Note: This is a whitebox macro that refines the return type to preserve the
   * `In` type member. The actual return type is `Wireable[T] { type In = ...
   * }`.
   */
  def from[T]: Wireable[T] = macro WireableMacros.fromImpl[T]

  /**
   * Derives a [[Wireable]] for type T with wire overrides for some
   * dependencies.
   *
   * Wire overrides allow you to provide specific wires for some of T's
   * dependencies. The overridden dependencies are resolved using the provided
   * wires, and the remaining dependencies become the `In` type.
   */
  def from[T](wires: Wire[_, _]*): Wireable[T] = macro WireableMacros.fromWithOverridesImpl[T]
}

private[scope] object WireableMacros {
  import scala.reflect.macros.whitebox
  import zio.blocks.scope.internal.{MacroCore => MC}

  def fromImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wireable[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    if (!sym.isClass) {
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

    val wireBody = if (isAutoCloseable) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.apply[$inType, $tpe] { (finalizer, ctx) =>
          val instance = $ctorCall
          finalizer.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.apply[$inType, $tpe] { (finalizer, ctx) =>
          val instance = $ctorCall
          instance
        }
      """
    }

    val result = q"""
      new _root_.zio.blocks.scope.Wireable[$tpe] {
        type In = $inType
        def wire: _root_.zio.blocks.scope.Wire[$inType, $tpe] = $wireBody
      }
    """
    c.Expr[Wireable[T]](result)
  }

  def fromWithOverridesImpl[T: c.WeakTypeTag](
    c: whitebox.Context
  )(wires: c.Expr[Wire[_, _]]*): c.Expr[Wireable[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    if (!sym.isClass) {
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

    MC.checkSubtypeConflicts(c)(tpe.toString, allDepTypes) match {
      case Some(error) => MC.abort(c)(error)
      case None        =>
    }

    // Extract output types from wires
    val wireOutTypes: List[Type] = wires.toList.map { wireExpr =>
      val wireTpe = wireExpr.actualType.dealias
      wireTpe.typeArgs match {
        case List(_, outType) => outType.dealias
        case _                =>
          c.abort(c.enclosingPosition, s"Cannot extract output type from wire: ${wireTpe}")
      }
    }

    // Partition deps into covered (by wires) and remaining
    val (_, remainingDeps) = allDepTypes.partition { depType =>
      wireOutTypes.exists(outType => outType <:< depType)
    }

    val isAutoCloseable = tpe <:< typeOf[AutoCloseable]
    val inType          =
      if (remainingDeps.isEmpty) typeOf[Any]
      else remainingDeps.reduceLeft((a, b) => c.universe.internal.refinedType(List(a, b), NoSymbol))

    // Find which wire provides a given type
    def findWireForType(depType: Type): Option[(c.Expr[Wire[_, _]], Type)] =
      wires.toList.zip(wireOutTypes).find { case (_, outType) =>
        outType <:< depType
      }

    // Build override context from wires at runtime
    val buildOverrideCtx: Tree = wires.toList
      .zip(wireOutTypes)
      .foldLeft[Tree](
        q"_root_.zio.blocks.context.Context.empty"
      ) { case (ctxExpr, (wireExpr, outType)) =>
        q"""
        {
          val wire = ${wireExpr.tree}.asInstanceOf[_root_.zio.blocks.scope.Wire[Any, $outType]]
          val value = wire.make(finalizer, _root_.zio.blocks.context.Context.empty)
          $ctxExpr.add[$outType](value)
        }
      """
      }

    def generateArgsWithOverrides(params: List[Symbol]): List[Tree] =
      params.map { param =>
        val paramType = param.typeSignature
        if (MC.isFinalizerType(c)(paramType)) {
          q"finalizer"
        } else {
          findWireForType(paramType) match {
            case Some((_, outType)) =>
              q"overrideCtx.get[$outType].asInstanceOf[$paramType]"
            case None =>
              q"ctx.get[$paramType]"
          }
        }
      }

    val argLists = paramLists.map(generateArgsWithOverrides)
    val ctorCall = if (argLists.isEmpty) {
      q"new $tpe()"
    } else {
      argLists.foldLeft[Tree](Select(New(TypeTree(tpe)), termNames.CONSTRUCTOR)) { (acc, args) =>
        Apply(acc, args)
      }
    }

    val wireBody = if (isAutoCloseable) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.apply[$inType, $tpe] { (finalizer, ctx) =>
          val overrideCtx = $buildOverrideCtx
          val instance = $ctorCall
          finalizer.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.apply[$inType, $tpe] { (finalizer, ctx) =>
          val overrideCtx = $buildOverrideCtx
          val instance = $ctorCall
          instance
        }
      """
    }

    val result = q"""
      new _root_.zio.blocks.scope.Wireable[$tpe] {
        type In = $inType
        def wire: _root_.zio.blocks.scope.Wire[$inType, $tpe] = $wireBody
      }
    """
    c.Expr[Wireable[T]](result)
  }
}
