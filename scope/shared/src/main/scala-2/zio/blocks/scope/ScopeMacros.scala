package zio.blocks.scope

import scala.reflect.macros.blackbox
import zio.blocks.scope.internal.{MacroCore => MC}

private[scope] object ScopeMacros {

  def sharedImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Wire.Shared[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    val wireableTpe =
      c.typecheck(q"_root_.scala.Predef.implicitly[_root_.zio.blocks.scope.Wireable[$tpe]]", silent = true)

    if (wireableTpe.nonEmpty && wireableTpe.tpe != NoType) {
      c.Expr[Wire.Shared[_, T]](q"$wireableTpe.wire.shared")
    } else {
      if (!sym.isClass) {
        MC.abortNotAClass(c)(tpe.toString)
      }
      deriveSharedWire[T](c)
    }
  }

  def uniqueImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Wire.Unique[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    val wireableTpe =
      c.typecheck(q"_root_.scala.Predef.implicitly[_root_.zio.blocks.scope.Wireable[$tpe]]", silent = true)

    if (wireableTpe.nonEmpty && wireableTpe.tpe != NoType) {
      c.Expr[Wire.Unique[_, T]](q"$wireableTpe.wire.unique")
    } else {
      if (!sym.isClass) {
        MC.abortNotAClass(c)(tpe.toString)
      }
      deriveUniqueWire[T](c)
    }
  }

  private def deriveSharedWire[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Wire.Shared[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

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
      case Some((sub, sup)) =>
        MC.abortSubtypeConflict(c)(tpe.toString, sub.toString, sup.toString)
      case None => // ok
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

    val result = if (isAutoCloseable) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$inType, $tpe] { scope =>
          val instance = $ctorCall
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$inType, $tpe] { scope =>
          val instance = $ctorCall
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    }
    c.Expr[Wire.Shared[_, T]](result)
  }

  private def deriveUniqueWire[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Wire.Unique[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

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
      case Some((sub, sup)) =>
        MC.abortSubtypeConflict(c)(tpe.toString, sub.toString, sup.toString)
      case None => // ok
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

    val result = if (isAutoCloseable) {
      q"""
        _root_.zio.blocks.scope.Wire.Unique.fromFunction[$inType, $tpe] { scope =>
          val instance = $ctorCall
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Wire.Unique.fromFunction[$inType, $tpe] { scope =>
          val instance = $ctorCall
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    }
    c.Expr[Wire.Unique[_, T]](result)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // injected[T] implementations
  // ─────────────────────────────────────────────────────────────────────────

  def injectedImpl[T: c.WeakTypeTag](c: blackbox.Context)(
    wires: c.Expr[Wire[_, _]]*
  )(
    scope: c.Expr[Scope.Any]
  ): c.Expr[Scope.Closeable[T, _]] =
    injectedImplWithScope[T](c)(wires, scope)

  def injectedFromPrefixImpl[T: c.WeakTypeTag](c: blackbox.Context)(
    wires: c.Expr[Wire[_, _]]*
  ): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val prefix    = c.prefix.tree
    val scopeExpr = prefix match {
      case Apply(_, List(scopeArg)) =>
        c.Expr[Scope.Any](scopeArg)
      case _ =>
        c.Expr[Scope.Any](q"$prefix.scope")
    }
    injectedImplWithScope[T](c)(wires, scopeExpr)
  }

  def injectedFromSelfImpl[T: c.WeakTypeTag](c: blackbox.Context)(
    wires: c.Expr[Wire[_, _]]*
  ): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val prefix    = c.prefix.tree
    val scopeExpr = c.Expr[Scope.Any](q"$prefix")
    injectedImplWithScope[T](c)(wires, scopeExpr)
  }

  private def injectedImplWithScope[T: c.WeakTypeTag](c: blackbox.Context)(
    wires: Seq[c.Expr[Wire[_, _]]],
    scopeExpr: c.Expr[Scope.Any]
  ): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    val wireableTpe =
      c.typecheck(q"_root_.scala.Predef.implicitly[_root_.zio.blocks.scope.Wireable[$tpe]]", silent = true)

    if (!sym.isClass && (wireableTpe.isEmpty || wireableTpe.tpe == NoType)) {
      c.abort(
        c.enclosingPosition,
        s"Cannot inject ${tpe.toString}: not a class and no Wireable[${tpe.toString}] available. " +
          "Either use a concrete class or provide a Wireable instance."
      )
    }

    val ctorOpt = if (sym.isClass) {
      tpe.decls.collectFirst { case m: MethodSymbol if m.isPrimaryConstructor => m }
    } else {
      None
    }

    val (allRegularParams, hasScopeParam, isAutoCloseable) = ctorOpt match {
      case Some(ctor) =>
        val paramLists                      = ctor.paramLists
        val (regularParams, implicitParams) = paramLists.partition { params =>
          params.headOption.forall(!_.isImplicit)
        }
        val hasScope = implicitParams.flatten.exists { param =>
          param.typeSignature <:< typeOf[Scope.Any]
        }
        val isAC = tpe <:< typeOf[AutoCloseable]
        (regularParams.flatten, hasScope, isAC)
      case None =>
        (Nil, false, false)
    }

    val depTypes = allRegularParams.map(_.typeSignature)

    if (wireableTpe.nonEmpty && wireableTpe.tpe != NoType && depTypes.isEmpty) {
      val result = q"""
        {
          val parentScope = $scopeExpr
          val finalizers = new _root_.zio.blocks.scope.internal.Finalizers
          val w = $wireableTpe.wire.asInstanceOf[_root_.zio.blocks.scope.Wire.Shared[Any, $tpe]]
          val childScope = _root_.zio.blocks.scope.Scope.makeCloseable[Any, _root_.zio.blocks.scope.TNil](
            parentScope, _root_.zio.blocks.context.Context.empty.asInstanceOf[_root_.zio.blocks.context.Context[Any]], finalizers)
          val ctx = w.constructFn(childScope.asInstanceOf[_root_.zio.blocks.scope.Scope.Has[Any]])
          _root_.zio.blocks.scope.Scope.makeCloseable(parentScope, ctx, finalizers)
        }
      """
      c.Expr[Scope.Closeable[T, _]](result)
    } else {
      depTypes match {
        case Nil =>
          generateInjected0[T](c)(hasScopeParam, isAutoCloseable, scopeExpr)
        case List(dep1Tpe) =>
          generateInjected1[T](c)(dep1Tpe, hasScopeParam, isAutoCloseable, wires, scopeExpr)
        case List(dep1Tpe, dep2Tpe) =>
          generateInjected2[T](c)(dep1Tpe, dep2Tpe, hasScopeParam, isAutoCloseable, wires, scopeExpr)
        case _ =>
          MC.abortTooManyParams(c)("injected", tpe.toString, depTypes.length, 2)
      }
    }
  }

  private def generateInjected0[T: c.WeakTypeTag](c: blackbox.Context)(
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    scopeExpr: c.Expr[Scope.Any]
  ): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

    val result = if (hasScopeParam) {
      q"""
        {
          val parentScope = $scopeExpr
          val finalizers = new _root_.zio.blocks.scope.internal.Finalizers
          val instance = new $tpe()(parentScope)
          val ctx = _root_.zio.blocks.context.Context[$tpe](instance)
          _root_.zio.blocks.scope.Scope.makeCloseable(parentScope, ctx, finalizers)
        }
      """
    } else if (isAutoCloseable) {
      q"""
        {
          val parentScope = $scopeExpr
          val finalizers = new _root_.zio.blocks.scope.internal.Finalizers
          val instance = new $tpe()
          finalizers.add(instance.asInstanceOf[AutoCloseable].close())
          val ctx = _root_.zio.blocks.context.Context[$tpe](instance)
          _root_.zio.blocks.scope.Scope.makeCloseable(parentScope, ctx, finalizers)
        }
      """
    } else {
      q"""
        {
          val parentScope = $scopeExpr
          val finalizers = new _root_.zio.blocks.scope.internal.Finalizers
          val instance = new $tpe()
          val ctx = _root_.zio.blocks.context.Context[$tpe](instance)
          _root_.zio.blocks.scope.Scope.makeCloseable(parentScope, ctx, finalizers)
        }
      """
    }
    c.Expr[Scope.Closeable[T, _]](result)
  }

  private def generateInjected1[T: c.WeakTypeTag](c: blackbox.Context)(
    dep1Tpe: c.Type,
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    wires: Seq[c.Expr[Wire[_, _]]],
    scopeExpr: c.Expr[Scope.Any]
  ): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val tpe      = weakTypeOf[T]
    val dep1Name = dep1Tpe.toString

    val wireSeq = q"_root_.scala.Seq(..$wires)"

    val instanceExpr = if (hasScopeParam) {
      q"new $tpe(arg1)(parentScope)"
    } else {
      q"new $tpe(arg1)"
    }

    val cleanupExpr = if (isAutoCloseable && !hasScopeParam) {
      q"finalizers.add(instance.asInstanceOf[AutoCloseable].close())"
    } else {
      q"()"
    }

    val result = q"""
      {
        val parentScope = $scopeExpr
        val finalizers = new _root_.zio.blocks.scope.internal.Finalizers
        val wiresSeq = $wireSeq

        val wire1 = wiresSeq.headOption.getOrElse {
          throw new IllegalStateException("Missing wire for dependency: " + $dep1Name)
        }.asInstanceOf[_root_.zio.blocks.scope.Wire.Shared[Any, $dep1Tpe]]

        val depCtx = _root_.zio.blocks.context.Context.empty.asInstanceOf[_root_.zio.blocks.context.Context[Any]]
        val dep1Scope = _root_.zio.blocks.scope.Scope.makeCloseable[Any, _root_.zio.blocks.scope.TNil](parentScope, depCtx, finalizers)
        val dep1Ctx = wire1.constructFn(dep1Scope.asInstanceOf[_root_.zio.blocks.scope.Scope.Has[Any]])
        val arg1 = dep1Ctx.get[$dep1Tpe]

        val instance = $instanceExpr
        $cleanupExpr

        val ctx = _root_.zio.blocks.context.Context[$tpe](instance)
        _root_.zio.blocks.scope.Scope.makeCloseable(parentScope, ctx, finalizers)
      }
    """
    c.Expr[Scope.Closeable[T, _]](result)
  }

  private def generateInjected2[T: c.WeakTypeTag](c: blackbox.Context)(
    dep1Tpe: c.Type,
    dep2Tpe: c.Type,
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    wires: Seq[c.Expr[Wire[_, _]]],
    scopeExpr: c.Expr[Scope.Any]
  ): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val tpe      = weakTypeOf[T]
    val dep1Name = dep1Tpe.toString
    val dep2Name = dep2Tpe.toString

    val wireSeq = q"_root_.scala.Seq(..$wires)"

    val instanceExpr = if (hasScopeParam) {
      q"new $tpe(arg1, arg2)(parentScope)"
    } else {
      q"new $tpe(arg1, arg2)"
    }

    val cleanupExpr = if (isAutoCloseable && !hasScopeParam) {
      q"finalizers.add(instance.asInstanceOf[AutoCloseable].close())"
    } else {
      q"()"
    }

    val result = q"""
      {
        val parentScope = $scopeExpr
        val finalizers = new _root_.zio.blocks.scope.internal.Finalizers
        val wiresSeq = $wireSeq

        val wire1 = wiresSeq.lift(0).getOrElse {
          throw new IllegalStateException("Missing wire for dependency: " + $dep1Name)
        }.asInstanceOf[_root_.zio.blocks.scope.Wire.Shared[Any, $dep1Tpe]]

        val wire2 = wiresSeq.lift(1).getOrElse {
          throw new IllegalStateException("Missing wire for dependency: " + $dep2Name)
        }.asInstanceOf[_root_.zio.blocks.scope.Wire.Shared[Any, $dep2Tpe]]

        val depCtx = _root_.zio.blocks.context.Context.empty.asInstanceOf[_root_.zio.blocks.context.Context[Any]]
        val dep1Scope = _root_.zio.blocks.scope.Scope.makeCloseable[Any, _root_.zio.blocks.scope.TNil](parentScope, depCtx, finalizers)
        val dep1Ctx = wire1.constructFn(dep1Scope.asInstanceOf[_root_.zio.blocks.scope.Scope.Has[Any]])
        val arg1 = dep1Ctx.get[$dep1Tpe]

        val dep2Scope = _root_.zio.blocks.scope.Scope.makeCloseable[$dep1Tpe, _root_.zio.blocks.scope.TNil](
          parentScope, dep1Ctx.asInstanceOf[_root_.zio.blocks.context.Context[$dep1Tpe]], finalizers)
        val dep2Ctx = wire2.constructFn(dep2Scope.asInstanceOf[_root_.zio.blocks.scope.Scope.Has[Any]])
        val arg2 = dep2Ctx.get[$dep2Tpe]

        val instance = $instanceExpr
        $cleanupExpr

        val ctx = _root_.zio.blocks.context.Context[$tpe](instance)
        _root_.zio.blocks.scope.Scope.makeCloseable(parentScope, ctx, finalizers)
      }
    """
    c.Expr[Scope.Closeable[T, _]](result)
  }
}
