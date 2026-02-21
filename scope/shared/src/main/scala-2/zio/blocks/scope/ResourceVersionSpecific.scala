package zio.blocks.scope

import scala.language.experimental.macros

private[scope] trait ResourceCompanionVersionSpecific {

  /**
   * Derives a Resource[T] from T's constructor.
   *
   * Only works for types with no dependencies. If T has constructor parameters
   * (other than constructor parameters of type Scope/Finalizer), use
   * [[Wire]][T] and call `.toResource(deps)`.
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
   * Derives a Resource[T] from T's constructor with wire overrides for
   * dependencies.
   *
   * The provided wires act as overrides for constructor dependencies. Any
   * remaining dependencies are derived automatically when possible. If a
   * dependency cannot be satisfied (no override and not derivable), a
   * compile-time error is produced.
   *
   * This is useful when you want to create a standalone resource that fully
   * encapsulates its dependency graph.
   *
   * @example
   *   {{{
   *   class Service(db: Database, config: Config)
   *
   *   // Provide wires for all dependencies
   *   val resource = Resource.from[Service](
   *     Wire.shared[Database],
   *     Wire(Config("localhost", 8080))
   *   )
   *   }}}
   *
   * @tparam T
   *   the type to construct
   * @param wires
   *   wires that provide all required dependencies
   * @return
   *   a resource that creates T instances
   */
  def from[T](wires: Wire[_, _]*): Resource[T] = macro ResourceMacros.deriveResourceWithOverridesImpl[T]
}

private[scope] object ResourceMacros {
  import scala.collection.mutable
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

    // Pre-compute which params are Scope/Finalizer type
    val finalizerParamFlags: List[List[Boolean]] = paramLists.map { params =>
      params.map { param =>
        val paramType = param.typeSignature
        MC.isScopeType(c)(paramType) || MC.isFinalizerType(c)(paramType)
      }
    }

    // Check for Scope/Finalizer parameter in ANY param list
    val hasScopeParam = finalizerParamFlags.flatten.exists(identity)

    // Collect dependencies (non-Scope/Finalizer params from all lists)
    val depTypes: List[Type] = paramLists.flatten.flatMap { param =>
      val paramType = param.typeSignature
      MC.classifyAndExtractDep(c)(paramType)
    }

    if (depTypes.nonEmpty) {
      MC.abortHasDependencies(c)(tpe.toString, depTypes.map(_.toString))
    }

    val isAutoCloseable = tpe <:< typeOf[AutoCloseable]

    val resourceBody = if (hasScopeParam) {
      // Constructor takes Scope/Finalizer in some param list - build correct Apply chain
      val scopeName = TermName(c.freshName("scope"))
      val ctorTerm  = Select(New(TypeTree(tpe)), termNames.CONSTRUCTOR)
      val applied   =
        paramLists.zip(finalizerParamFlags).foldLeft(ctorTerm: Tree) { case (acc, (params, isFinalizerFlags)) =>
          if (params.isEmpty) {
            Apply(acc, Nil)
          } else {
            val args = params.zip(isFinalizerFlags).map { case (_, isFinalizer) =>
              if (isFinalizer) q"$scopeName"
              else {
                val color = zio.blocks.scope.internal.ErrorMessages.Colors.shouldUseColor
                c.abort(
                  c.enclosingPosition,
                  zio.blocks.scope.internal.ErrorMessages
                    .renderUnsupportedImplicitParam(tpe.toString, "non-Finalizer/Scope", color)
                )
              }
            }
            Apply(acc, args)
          }
        }
      q"""
        _root_.zio.blocks.scope.Resource.shared[$tpe] { ($scopeName: _root_.zio.blocks.scope.Scope) =>
          $applied
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

    // ═══════════════════════════════════════════════════════════════════════
    // DATA STRUCTURES
    // ═══════════════════════════════════════════════════════════════════════

    case class WireInfo(
      wireExpr: Tree,
      outType: Type,
      inTypes: List[Type],
      isShared: Boolean,
      isExplicit: Boolean
    )

    // Helper to normalize type for consistent map keys
    def normalizeType(t: Type): Type = t.dealias.widen
    def typeKey(t: Type): String     = normalizeType(t).toString

    // ═══════════════════════════════════════════════════════════════════════
    // PARSE EXPLICIT WIRES
    // ═══════════════════════════════════════════════════════════════════════

    def parseWireExpr(wireExpr: c.Expr[Wire[_, _]]): WireInfo = {
      val wireTpe = wireExpr.actualType.dealias

      val (inType, outType) = wireTpe.typeArgs match {
        case List(in, out) => (in.dealias, out.dealias)
        case _             =>
          val color = zio.blocks.scope.internal.ErrorMessages.Colors.shouldUseColor
          c.abort(
            c.enclosingPosition,
            zio.blocks.scope.internal.ErrorMessages.renderCannotExtractWireTypes(wireTpe.toString, color)
          )
      }

      val inTypes = flattenIntersection(inType)

      // Check if it's Wire.Shared or Wire.Unique by looking at the type symbol name
      val isShared = wireTpe.typeSymbol.fullName.contains("Shared") ||
        wireTpe.baseClasses.exists(_.fullName.contains("Wire$Shared"))

      WireInfo(wireExpr.tree, outType, inTypes, isShared, isExplicit = true)
    }

    def flattenIntersection(tpe: Type): List[Type] = {
      val dealiased = tpe.dealias
      dealiased match {
        case RefinedType(parents, _) =>
          parents.flatMap(flattenIntersection)
        case t if t =:= typeOf[Any] =>
          Nil
        case t =>
          List(t)
      }
    }

    val explicitWires: List[WireInfo] = wires.toList.map(parseWireExpr)

    // ═══════════════════════════════════════════════════════════════════════
    // UNMAKEABLE TYPE CHECK
    // ═══════════════════════════════════════════════════════════════════════

    def isUnmakeableType(t: Type): Boolean = {
      val tsym = t.typeSymbol
      val name = tsym.fullName

      val primitives = Set(
        "scala.Predef.String",
        "java.lang.String",
        "scala.Int",
        "scala.Long",
        "scala.Double",
        "scala.Float",
        "scala.Boolean",
        "scala.Byte",
        "scala.Short",
        "scala.Char",
        "scala.Unit",
        "scala.Nothing",
        "scala.Any",
        "scala.AnyRef"
      )
      if (primitives.contains(name)) return true
      if (name.startsWith("scala.Function")) return true

      val collections = Set(
        "scala.collection.immutable.List",
        "scala.collection.immutable.Seq",
        "scala.collection.immutable.Set",
        "scala.collection.immutable.Map",
        "scala.Option"
      )
      if (collections.exists(coll => name.startsWith(coll))) return true

      false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AUTO-CREATE WIRE
    // ═══════════════════════════════════════════════════════════════════════

    def autoCreateWire(targetType: Type, chain: List[String]): WireInfo = {
      val tsym = targetType.typeSymbol

      if (isUnmakeableType(targetType)) {
        MC.abortUnmakeableType(c)(targetType.toString, chain)
      }

      if (!tsym.isClass || tsym.asClass.isTrait || tsym.asClass.isAbstract) {
        MC.abortAbstractType(c)(targetType.toString, chain)
      }

      val ctor = targetType.decls.collectFirst {
        case m: MethodSymbol if m.isPrimaryConstructor => m
      }.getOrElse {
        MC.abortNoCtorForAutoCreate(c)(targetType.toString, chain)
      }

      // Generate Wire.shared[targetType] inline
      val paramLists  = ctor.paramLists
      val allDepTypes = paramLists.flatten.flatMap { param =>
        MC.classifyAndExtractDep(c)(param.typeSignature)
      }

      val inType =
        if (allDepTypes.isEmpty) typeOf[Any]
        else allDepTypes.reduceLeft((a, b) => c.universe.internal.refinedType(List(a, b), NoSymbol))

      val isAutoCloseable = targetType <:< typeOf[AutoCloseable]

      def generateArgs(params: List[Symbol]): List[Tree] =
        params.map { param =>
          val paramType = param.typeSignature
          if (MC.isFinalizerType(c)(paramType)) {
            q"scope"
          } else {
            q"ctx.get[$paramType]"
          }
        }

      val argLists = paramLists.map(generateArgs)
      val ctorCall = if (argLists.isEmpty) {
        q"new $targetType()"
      } else {
        argLists.foldLeft[Tree](Select(New(TypeTree(targetType)), termNames.CONSTRUCTOR)) { (acc, args) =>
          Apply(acc, args)
        }
      }

      val wireBody = if (isAutoCloseable) {
        q"""
          val instance = $ctorCall
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        """
      } else {
        q"$ctorCall"
      }

      val wireExpr =
        q"_root_.zio.blocks.scope.Wire.Shared.apply[$inType, $targetType] { (scope: _root_.zio.blocks.scope.Scope, ctx) => $wireBody }"

      WireInfo(wireExpr, targetType, allDepTypes, isShared = true, isExplicit = false)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 1: COLLECT WIRES
    // ═══════════════════════════════════════════════════════════════════════

    // wireMap: keyed by the wire's OUTPUT type (canonical key)
    // aliasMap: maps required types to their canonical key (for subtype resolution)
    val wireMap  = mutable.Map[String, WireInfo]()
    val aliasMap = mutable.Map[String, String]()

    def resolveWire(targetType: Type, chain: List[String]): Unit = {
      val requiredKey = typeKey(targetType)

      // Check if we already have an alias for this type
      if (aliasMap.contains(requiredKey)) return

      // Check for cycle using required key
      if (chain.contains(requiredKey)) {
        val cycleStart = chain.indexOf(requiredKey)
        val cyclePath  = chain.drop(cycleStart) :+ requiredKey
        MC.abortDependencyCycle(c)(cyclePath)
      }

      // Find matching wire from explicit wires (including subtype matches)
      val matchingWires = explicitWires.filter { we =>
        normalizeType(we.outType) <:< normalizeType(targetType)
      }

      val wire: WireInfo = matchingWires match {
        case Nil =>
          autoCreateWire(targetType, chain)

        case single :: Nil =>
          single

        case multiple =>
          val providers =
            multiple.map(we => zio.blocks.scope.internal.ErrorMessages.ProviderInfo(we.outType.toString, None))
          MC.abortDuplicateProvider(c)(targetType.toString, providers)
      }

      // Use the wire's output type as the canonical key
      val canonicalKey = typeKey(wire.outType)

      // Register the alias from required type to canonical type
      aliasMap(requiredKey) = canonicalKey

      // Only add to wireMap if not already present (another required type may have added it)
      if (!wireMap.contains(canonicalKey)) {
        wireMap(canonicalKey) = wire

        // Recurse into dependencies using canonical key in chain
        val newChain = chain :+ requiredKey
        wire.inTypes.foreach { depType =>
          resolveWire(depType, newChain)
        }
      }
    }

    resolveWire(tpe, Nil)

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 2: VALIDATE
    // ═══════════════════════════════════════════════════════════════════════

    wireMap.values.foreach { we =>
      val paramTypes = we.inTypes.map(t => typeKey(t))
      val duplicates = paramTypes.groupBy(identity).filter(_._2.size > 1).keys
      if (duplicates.nonEmpty) {
        MC.abortDuplicateParamType(c)(we.outType.toString, duplicates.head)
      }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 3: TOPOLOGICAL SORT
    // ═══════════════════════════════════════════════════════════════════════

    // Use aliasMap to resolve dependency types to their canonical keys
    val aliasMapFinal = aliasMap.toMap

    def canonicalKeyFor(depType: Type): String = {
      val requiredKey = typeKey(depType)
      aliasMapFinal.getOrElse(requiredKey, requiredKey)
    }

    def topologicalSort(): List[String] = {
      val visited = mutable.Set[String]()
      val result  = mutable.ListBuffer[String]()

      def visit(key: String): Unit = {
        if (visited.contains(key)) return
        visited += key

        wireMap.get(key).foreach { we =>
          we.inTypes.foreach { dep =>
            visit(canonicalKeyFor(dep))
          }
        }

        result += key
      }

      wireMap.keys.toList.sorted.foreach(visit)
      result.toList
    }

    val sorted       = topologicalSort()
    val targetKey    = canonicalKeyFor(tpe)
    val wireMapFinal = wireMap.toMap

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 4: GENERATE RESOURCE COMPOSITION
    // ═══════════════════════════════════════════════════════════════════════

    // Generate val names for each resource
    val keyToValName: Map[String, TermName] = sorted.zipWithIndex.map { case (key, idx) =>
      (key, TermName(s"res$idx"))
    }.toMap

    // Create Resource expression for a type given its dependencies' val names
    def createResourceExpr(
      we: WireInfo,
      depValNames: Map[String, TermName]
    ): Tree = {
      val deps = we.inTypes

      if (deps.isEmpty) {
        // Leaf resource
        if (we.isShared) {
          q"""
            _root_.zio.blocks.scope.Resource.shared[${we.outType}] { scope =>
              val wire = ${we.wireExpr}.asInstanceOf[_root_.zio.blocks.scope.Wire[Any, ${we.outType}]]
              wire.make(scope, _root_.zio.blocks.context.Context.empty.asInstanceOf[_root_.zio.blocks.context.Context[Any]])
            }
          """
        } else {
          q"""
            _root_.zio.blocks.scope.Resource.unique[${we.outType}] { scope =>
              val wire = ${we.wireExpr}.asInstanceOf[_root_.zio.blocks.scope.Wire[Any, ${we.outType}]]
              wire.make(scope, _root_.zio.blocks.context.Context.empty.asInstanceOf[_root_.zio.blocks.context.Context[Any]])
            }
          """
        }
      } else if (we.isShared) {
        // Shared type with dependencies - wrap entire construction in Resource.shared
        // This ensures we get ONE Resource.shared instance, not one per use
        def buildDepAcquisition(
          remainingDeps: List[Type],
          boundValues: List[(Type, TermName)],
          scopeName: TermName
        ): Tree =
          remainingDeps match {
            case Nil =>
              val ctxExpr = boundValues.foldLeft[Tree](
                q"_root_.zio.blocks.context.Context.empty"
              ) { case (ctx, (depType, valName)) =>
                q"$ctx.add[$depType]($valName)"
              }
              q"""
                val wire = ${we.wireExpr}.asInstanceOf[_root_.zio.blocks.scope.Wire[Any, ${we.outType}]]
                wire.make($scopeName, $ctxExpr.asInstanceOf[_root_.zio.blocks.context.Context[Any]])
              """

            case dep :: rest =>
              val depKey     = canonicalKeyFor(dep)
              val depValName = depValNames(depKey)
              val valName    = TermName(c.freshName("dep"))
              q"""
                val $valName: $dep = $depValName.make($scopeName)
                ${buildDepAcquisition(rest, boundValues :+ (dep, valName), scopeName)}
              """
          }

        val scopeName = TermName(c.freshName("scope"))
        q"""
          _root_.zio.blocks.scope.Resource.shared[${we.outType}] { ($scopeName: _root_.zio.blocks.scope.Scope) =>
            ${buildDepAcquisition(deps, Nil, scopeName)}
          }
        """
      } else {
        // Unique type with dependencies - use flatMap chain
        // Each use creates fresh instances (that's what unique means)
        def buildChain(
          remainingDeps: List[Type],
          boundValues: List[(Type, TermName)]
        ): Tree =
          remainingDeps match {
            case Nil =>
              val ctxExpr = boundValues.foldLeft[Tree](
                q"_root_.zio.blocks.context.Context.empty"
              ) { case (ctx, (depType, valName)) =>
                q"$ctx.add[$depType]($valName)"
              }
              q"""
                _root_.zio.blocks.scope.Resource.unique[${we.outType}] { scope =>
                  val wire = ${we.wireExpr}.asInstanceOf[_root_.zio.blocks.scope.Wire[Any, ${we.outType}]]
                  wire.make(scope, $ctxExpr.asInstanceOf[_root_.zio.blocks.context.Context[Any]])
                }
              """

            case dep :: rest =>
              val depKey     = canonicalKeyFor(dep)
              val depValName = depValNames(depKey)
              val paramName  = TermName(c.freshName("dep"))

              q"""
                $depValName.flatMap { ($paramName: $dep) =>
                  ${buildChain(rest, boundValues :+ (dep, paramName))}
                }
              """
          }

        buildChain(deps, Nil)
      }
    }

    // Generate val definitions and the final expression
    // We generate:
    //   val res0: Resource[A] = ...
    //   val res1: Resource[B] = ...
    //   ...
    //   resN
    var builtValNames = Map[String, TermName]()
    val valDefs       = sorted.map { key =>
      val we      = wireMapFinal(key)
      val valName = keyToValName(key)
      val resExpr = createResourceExpr(we, builtValNames)
      builtValNames = builtValNames + (key -> valName)
      q"val $valName: _root_.zio.blocks.scope.Resource[${we.outType}] = $resExpr"
    }

    val targetValName = keyToValName(targetKey)
    val result        = q"""
      {
        ..$valDefs
        $targetValName
      }
    """

    c.Expr[Resource[T]](result)
  }
}
