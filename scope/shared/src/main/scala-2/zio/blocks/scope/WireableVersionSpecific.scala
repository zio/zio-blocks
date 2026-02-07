package zio.blocks.scope

import scala.language.experimental.macros

private[scope] trait WireableVersionSpecific {

  /**
   * Derives a [[Wireable]] for type T from its primary constructor.
   *
   * Constructor parameters are analyzed to determine dependencies:
   *   - Regular parameters: become dependencies (part of `In` type)
   *   - `Scope.Has[Y]` parameters: Y becomes a dependency, scope is passed
   *     narrowed
   *   - `Scope.Any` parameters: scope is passed but no dependency added
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

    // Extract dependencies from all param lists
    val allDepTypes: List[Type] = paramLists.flatten.flatMap { param =>
      val paramType = param.typeSignature
      MC.classifyAndExtractDep(c)(paramType)
    }

    // Check for subtype conflicts
    MC.checkSubtypeConflicts(c)(tpe.toString, allDepTypes) match {
      case Some(error) => MC.abort(c)(error)
      case None        => // ok
    }

    val isAutoCloseable = tpe <:< typeOf[AutoCloseable]
    val inType          =
      if (allDepTypes.isEmpty) typeOf[Any]
      else allDepTypes.reduceLeft((a, b) => c.universe.internal.refinedType(List(a, b), NoSymbol))

    // Generate argument expressions for constructor
    def generateArgs(params: List[Symbol]): List[Tree] =
      params.map { param =>
        val paramType = param.typeSignature
        if (MC.isScopeType(c)(paramType)) {
          MC.extractScopeHasType(c)(paramType) match {
            case Some(depType) =>
              q"scope.asInstanceOf[_root_.zio.blocks.scope.Scope.Has[$depType]]"
            case None =>
              q"scope.asInstanceOf[_root_.zio.blocks.scope.Scope.Any]"
          }
        } else {
          q"scope.get[$paramType]"
        }
      }

    // Generate constructor call with all param lists
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
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$inType, $tpe] { scope =>
          val instance = $ctorCall
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$inType, $tpe] { scope =>
          val instance = $ctorCall
          instance
        }
      """
    }

    // Whitebox macro: returning a tree with refined type allows the compiler
    // to infer Wireable[T] { type In = inType } as the return type
    val result = q"""
      new _root_.zio.blocks.scope.Wireable[$tpe] {
        type In = $inType
        def wire: _root_.zio.blocks.scope.Wire[$inType, $tpe] = $wireBody
      }
    """
    c.Expr[Wireable[T]](result)
  }
}
