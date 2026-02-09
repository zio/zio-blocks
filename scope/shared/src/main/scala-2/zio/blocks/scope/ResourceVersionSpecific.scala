package zio.blocks.scope

import scala.language.experimental.macros

private[scope] trait ResourceCompanionVersionSpecific {

  /**
   * Derives a Resource[T] from T's constructor.
   *
   * Only works for types with no dependencies. If T has constructor parameters
   * (other than an implicit Scope/Finalizer), use [[Wire]][T] and call
   * `.toResource(deps)`.
   *
   * If T extends `AutoCloseable`, its `close()` method is automatically
   * registered as a finalizer.
   *
   * @tparam T
   *   the type to construct (must be a class with no dependencies)
   * @return
   *   a resource that creates T instances
   */
  def from[T]: Resource[T] = macro ResourceMacros.deriveResourceImpl[T]

  /**
   * Derives a Resource[T] from T's constructor with wire overrides for all
   * dependencies.
   *
   * All of T's constructor dependencies must be satisfied by the provided
   * wires. If any dependency is not covered, a compile-time error is produced.
   *
   * This is useful when you want to create a standalone resource that fully
   * encapsulates its dependency graph.
   */
  def from[T](wires: Wire[_, _]*): Resource[T] = macro ResourceMacros.deriveResourceWithOverridesImpl[T]
}

private[scope] object ResourceMacros {
  import scala.reflect.macros.whitebox
  import zio.blocks.scope.internal.{MacroCore => MC}

  def deriveResourceImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Resource[T]] = {
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

    // Separate regular params from implicit params
    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(!_.isImplicit)
    }

    val allRegularParams = regularParams.flatten

    // Check for Scope/Finalizer parameter in implicits
    val hasScopeParam = implicitParams.flatten.exists { param =>
      val paramType = param.typeSignature
      MC.isScopeType(c)(paramType) || MC.isFinalizerType(c)(paramType)
    }

    // Collect dependencies (non-Scope/Finalizer regular params)
    val depTypes: List[Type] = allRegularParams.flatMap { param =>
      val paramType = param.typeSignature
      MC.classifyAndExtractDep(c)(paramType)
    }

    if (depTypes.nonEmpty) {
      c.abort(
        c.enclosingPosition,
        s"Resource.from[${tpe}] cannot be derived: ${tpe} has dependencies: ${depTypes.mkString(", ")}. " +
          s"Use Resource.from[${tpe}](wire1, wire2, ...) to provide wires for all dependencies."
      )
    }

    val isAutoCloseable = tpe <:< typeOf[AutoCloseable]

    val resourceBody = if (hasScopeParam) {
      // Constructor takes implicit Scope/Finalizer
      q"""
        _root_.zio.blocks.scope.Resource.shared[$tpe] { scope =>
          new $tpe()(scope)
        }
      """
    } else if (isAutoCloseable) {
      // AutoCloseable - register finalizer
      q"""
        _root_.zio.blocks.scope.Resource.shared[$tpe] { scope =>
          val instance = new $tpe()
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        }
      """
    } else {
      // Simple case - no dependencies, no cleanup
      q"""
        _root_.zio.blocks.scope.Resource.shared[$tpe] { _ =>
          new $tpe()
        }
      """
    }

    c.Expr[Resource[T]](resourceBody)
  }

  def deriveResourceWithOverridesImpl[T: c.WeakTypeTag](
    c: whitebox.Context
  )(wires: c.Expr[Wire[_, _]]*): c.Expr[Resource[T]] = {
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

    // Check all deps are covered
    val (_, remainingDeps) = allDepTypes.partition { depType =>
      wireOutTypes.exists(outType => outType <:< depType)
    }

    if (remainingDeps.nonEmpty) {
      val missing  = remainingDeps.mkString(", ")
      val provided = wireOutTypes.mkString(", ")
      c.abort(
        c.enclosingPosition,
        s"Resource.from[${tpe}] has unresolved dependencies: $missing. " +
          s"Provided wires produce: $provided. " +
          s"Add wires for the missing dependencies."
      )
    }

    val isAutoCloseable = tpe <:< typeOf[AutoCloseable]

    // Find which wire provides a given type
    def findWireForType(depType: Type): Option[(c.Expr[Wire[_, _]], Type)] =
      wires.toList.zip(wireOutTypes).find { case (_, outType) =>
        outType <:< depType
      }

    // Build override context from wires at runtime, passing accumulated context to each wire
    val buildOverrideCtx: Tree = wires.toList
      .zip(wireOutTypes)
      .foldLeft[Tree](
        q"_root_.zio.blocks.context.Context.empty"
      ) { case (ctxExpr, (wireExpr, outType)) =>
        q"""
        {
          val ctx = $ctxExpr
          val wire = ${wireExpr.tree}.asInstanceOf[_root_.zio.blocks.scope.Wire[Any, $outType]]
          val value = wire.make(scope, ctx.asInstanceOf[_root_.zio.blocks.context.Context[Any]])
          ctx.add[$outType](value)
        }
      """
      }

    def generateArgsWithOverrides(params: List[Symbol]): List[Tree] =
      params.map { param =>
        val paramType = param.typeSignature
        if (MC.isFinalizerType(c)(paramType)) {
          q"scope"
        } else {
          findWireForType(paramType) match {
            case Some((_, outType)) =>
              q"overrideCtx.get[$outType].asInstanceOf[$paramType]"
            case None =>
              c.abort(c.enclosingPosition, s"No wire found for dependency: $paramType")
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

    val resourceBody = if (isAutoCloseable) {
      q"""
        _root_.zio.blocks.scope.Resource.shared[$tpe] { scope =>
          val overrideCtx = $buildOverrideCtx
          val instance = $ctorCall
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Resource.shared[$tpe] { scope =>
          val overrideCtx = $buildOverrideCtx
          val instance = $ctorCall
          instance
        }
      """
    }

    c.Expr[Resource[T]](resourceBody)
  }
}
