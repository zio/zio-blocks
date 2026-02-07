package zio.blocks.scope

import scala.reflect.macros.whitebox
import zio.blocks.scope.internal.{MacroCore => MC}

private[scope] object ScopeMacros {

  // Using whitebox macros to allow refined return types (preserving In type)

  def sharedImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Shared[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    val wireableTpe =
      c.typecheck(q"_root_.scala.Predef.implicitly[_root_.zio.blocks.scope.Wireable[$tpe]]", silent = true)

    if (wireableTpe.nonEmpty && wireableTpe.tpe != NoType) {
      // The tree is: Apply(TypeApply(implicitly, types), List(actualImplicitVal))
      // Extract the actual implicit value's declared type which preserves refinements
      val actualImplicitTpe = wireableTpe match {
        case Apply(_, List(implicitVal)) if implicitVal.symbol != null && implicitVal.symbol != NoSymbol =>
          implicitVal.symbol.typeSignature
        case _ =>
          wireableTpe.tpe
      }
      val inType = extractWireableInType(c)(actualImplicitTpe)
      // Construct the proper Wire.Shared[In, T] type for the Expr
      val wireSharedType = appliedType(typeOf[Wire.Shared[_, _]].typeConstructor, List(inType, tpe))
      // Use asInstanceOf to break the path-dependent type and establish the concrete type
      val result = q"$wireableTpe.wire.shared.asInstanceOf[$wireSharedType]"
      c.Expr(result)(c.WeakTypeTag(wireSharedType))
    } else {
      // Must be a concrete class (not trait, not abstract)
      if (!sym.isClass || sym.asClass.isTrait || sym.asClass.isAbstract) {
        MC.abortNotAClass(c)(tpe.toString)
      }
      deriveSharedWire[T](c)
    }
  }

  def uniqueImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Unique[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    val wireableTpe =
      c.typecheck(q"_root_.scala.Predef.implicitly[_root_.zio.blocks.scope.Wireable[$tpe]]", silent = true)

    if (wireableTpe.nonEmpty && wireableTpe.tpe != NoType) {
      // The tree is: Apply(TypeApply(implicitly, types), List(actualImplicitVal))
      // Extract the actual implicit value's declared type which preserves refinements
      val actualImplicitTpe = wireableTpe match {
        case Apply(_, List(implicitVal)) if implicitVal.symbol != null && implicitVal.symbol != NoSymbol =>
          implicitVal.symbol.typeSignature
        case _ =>
          wireableTpe.tpe
      }
      val inType = extractWireableInType(c)(actualImplicitTpe)
      // Construct the proper Wire.Unique[In, T] type for the Expr
      val wireUniqueType = appliedType(typeOf[Wire.Unique[_, _]].typeConstructor, List(inType, tpe))
      // Use asInstanceOf to break the path-dependent type and establish the concrete type
      val result = q"$wireableTpe.wire.unique.asInstanceOf[$wireUniqueType]"
      c.Expr(result)(c.WeakTypeTag(wireUniqueType))
    } else {
      // Must be a concrete class (not trait, not abstract)
      if (!sym.isClass || sym.asClass.isTrait || sym.asClass.isAbstract) {
        MC.abortNotAClass(c)(tpe.toString)
      }
      deriveUniqueWire[T](c)
    }
  }

  /** Extract the In type member from a Wireable type */
  private def extractWireableInType(c: whitebox.Context)(wireableTpe: c.Type): c.Type = {
    import c.universe._

    // Unwrap NullaryMethodType to get the actual result type (for vals)
    val unwrapped = wireableTpe match {
      case NullaryMethodType(resultType) => resultType
      case other                         => other
    }

    // First, check if this is Wireable.Typed[In, Out] - extract In from type args directly
    unwrapped match {
      case TypeRef(_, sym, args) if sym.fullName == "zio.blocks.scope.Wireable.Typed" && args.nonEmpty =>
        return args.head
      case _ => // continue
    }

    // Otherwise, dealias and look for the In type member in refinements
    val dealiased = wireableTpe.dealias
    dealiased match {
      // Handle refinement type like Wireable[T] { type In >: X }
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
        // Fallback: look for In member
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

  private def deriveSharedWire[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Shared[_, T]] = {
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

  private def deriveUniqueWire[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Unique[_, T]] = {
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

  def injectedImpl[T: c.WeakTypeTag](c: whitebox.Context)(
    wires: c.Expr[Wire[_, _]]*
  )(
    scope: c.Expr[Scope.Any]
  ): c.Expr[Scope.Closeable[T, _]] =
    injectedImplWithScope[T](c)(wires, scope)

  def injectedNoArgsImpl[T: c.WeakTypeTag](c: whitebox.Context)(
    scope: c.Expr[Scope.Any]
  ): c.Expr[Scope.Closeable[T, _]] =
    injectedImplWithScope[T](c)(Seq.empty, scope)

  def injectedFromPrefixImpl[T: c.WeakTypeTag](c: whitebox.Context)(
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

  def injectedFromSelfImpl[T: c.WeakTypeTag](c: whitebox.Context)(
    wires: c.Expr[Wire[_, _]]*
  ): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val prefix    = c.prefix.tree
    val scopeExpr = c.Expr[Scope.Any](q"$prefix")
    injectedImplWithScope[T](c)(wires, scopeExpr)
  }

  def injectedFromSelfNoArgsImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val prefix    = c.prefix.tree
    val scopeExpr = c.Expr[Scope.Any](q"$prefix")
    injectedImplWithScope[T](c)(Seq.empty, scopeExpr)
  }

  private def injectedImplWithScope[T: c.WeakTypeTag](c: whitebox.Context)(
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
      // Supports arbitrary number of constructor parameters
      generateInjectedN[T](c)(depTypes, hasScopeParam, isAutoCloseable, wires, scopeExpr)
    }
  }

  private def generateInjectedN[T: c.WeakTypeTag](c: whitebox.Context)(
    depTypes: List[c.Type],
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    wires: Seq[c.Expr[Wire[_, _]]],
    scopeExpr: c.Expr[Scope.Any]
  ): c.Expr[Scope.Closeable[T, _]] = {
    import c.universe._

    val tpe     = weakTypeOf[T]
    val wireSeq = q"_root_.scala.Seq(..$wires)"

    if (depTypes.isEmpty) {
      // No dependencies - just construct
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
    } else {
      // Has dependencies - generate wire extraction for each and then construct
      val argNames = depTypes.zipWithIndex.map { case (_, i) => TermName(s"arg$i") }

      // Generate individual wire extraction statements (not blocks)
      val wireExtractions: List[List[Tree]] = depTypes.zipWithIndex.map { case (depTpe, i) =>
        val depName   = depTpe.toString
        val argName   = argNames(i)
        val wireName  = TermName(s"wire$i")
        val ctxName   = TermName(s"ctx$i")
        val scopeName = TermName(s"scope$i")

        List(
          q"""val $wireName = wiresSeq.lift($i).getOrElse {
            throw new IllegalStateException("Missing wire for dependency: " + $depName)
          }.asInstanceOf[_root_.zio.blocks.scope.Wire.Shared[Any, $depTpe]]""",
          q"""val $scopeName = _root_.zio.blocks.scope.Scope.makeCloseable[Any, _root_.zio.blocks.scope.TNil](
            parentScope,
            _root_.zio.blocks.context.Context.empty.asInstanceOf[_root_.zio.blocks.context.Context[Any]],
            finalizers
          )""",
          q"val $ctxName = $wireName.constructFn($scopeName.asInstanceOf[_root_.zio.blocks.scope.Scope.Has[Any]])",
          q"val $argName = $ctxName.get[$depTpe]"
        )
      }

      // Flatten all statements
      val allWireStatements = wireExtractions.flatten

      // Build constructor call with all arguments
      val argRefs      = argNames.map(name => q"$name")
      val instanceExpr = if (hasScopeParam) {
        q"new $tpe(..$argRefs)(parentScope)"
      } else {
        q"new $tpe(..$argRefs)"
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

          ..$allWireStatements

          val instance = $instanceExpr
          $cleanupExpr

          val ctx = _root_.zio.blocks.context.Context[$tpe](instance)
          _root_.zio.blocks.scope.Scope.makeCloseable(parentScope, ctx, finalizers)
        }
      """
      c.Expr[Scope.Closeable[T, _]](result)
    }
  }
}
