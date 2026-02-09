package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.MacroCore
import zio.blocks.scope.internal.WireCodeGen
import scala.compiletime.summonInline
import scala.quoted.*

/**
 * Macro implementations for Resource.from[T] derivation.
 *
 * The key insight is to compose Resources via flatMap, not accumulate values in
 * a Context. This correctly preserves sharing/uniqueness semantics:
 *   - Wire.shared → Resource.Shared → one instance, ref-counted
 *   - Wire.unique → Resource.Unique → fresh instance per flatMap call
 */
private[scope] object ResourceMacros {

  // ─────────────────────────────────────────────────────────────────────────
  // Resource.from[T] - no arguments version (zero dependencies)
  // ─────────────────────────────────────────────────────────────────────────

  def deriveResourceImpl[T: Type](using Quotes): Expr[Resource[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
      MacroCore.abortNotAClass(tpe.show)
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      MacroCore.abortNoPrimaryCtor(tpe.show)
    }

    val paramLists = ctor.paramSymss

    // Separate regular params from implicit/given params
    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(p => !p.flags.is(Flags.Given) && !p.flags.is(Flags.Implicit))
    }

    val allRegularParams = regularParams.flatten

    // Check for Finalizer parameter in implicits (not Scope - Scope is not supported)
    val hasFinalizerParam = implicitParams.flatten.exists { param =>
      val paramType = tpe.memberType(param)
      MacroCore.isFinalizerType(paramType)
    }

    // Collect dependencies (non-Finalizer regular params)
    val depTypes: List[TypeRepr] = allRegularParams.flatMap { param =>
      val paramType = tpe.memberType(param).dealias.simplified
      if (MacroCore.isFinalizerType(paramType)) None
      else Some(paramType)
    }

    if (depTypes.nonEmpty) {
      MacroCore.abortHasDependencies(tpe.show, depTypes.map(_.show))
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val ctorSym         = sym.primaryConstructor

    // Pre-compute which params are Finalizer type (before entering splice)
    val finalizerParamFlags: List[List[Boolean]] = paramLists.map { params =>
      params.map { param =>
        val paramType = tpe.memberType(param)
        MacroCore.isFinalizerType(paramType)
      }
    }

    // Generate resource body
    if (hasFinalizerParam) {
      // Constructor takes implicit Finalizer - build correct Apply chain
      '{
        Resource.shared[T] { finalizer =>
          ${
            val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
            // Build Apply chain matching the actual param lists
            val applied =
              paramLists.zip(finalizerParamFlags).foldLeft(ctorTerm: Term) { case (acc, (params, isFinalizerFlags)) =>
                if (params.isEmpty) {
                  Apply(acc, Nil)
                } else if (params.exists(p => p.flags.is(Flags.Given) || p.flags.is(Flags.Implicit))) {
                  // Implicit/given param list - inject finalizer for Finalizer params
                  val args = params.zip(isFinalizerFlags).map { case (param, isFinalizer) =>
                    if (isFinalizer) '{ finalizer }.asTerm
                    else {
                      val paramType = tpe.memberType(param)
                      MacroCore.abortUnsupportedImplicitParam(tpe.show, paramType.show)
                    }
                  }
                  Apply(acc, args)
                } else {
                  Apply(acc, Nil)
                }
              }
            applied.asExprOf[T]
          }
        }
      }
    } else if (isAutoCloseable) {
      // AutoCloseable - register finalizer
      '{
        Resource.shared[T] { finalizer =>
          val instance = ${
            val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctorTerm, Nil).asExprOf[T]
          }
          finalizer.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        }
      }
    } else {
      // Simple case - no dependencies, no cleanup
      '{
        Resource.shared[T] { _ =>
          ${
            val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctorTerm, Nil).asExprOf[T]
          }
        }
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Resource.from[T](wires*) - with explicit wires
  // ─────────────────────────────────────────────────────────────────────────

  def deriveResourceWithOverridesImpl[T: Type](wiresExpr: Expr[Seq[Wire[?, ?]]])(using Quotes): Expr[Resource[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]

    // Helper to normalize type for consistent map keys
    def normalizeType(t: TypeRepr): TypeRepr = t.dealias.widen.simplified
    def typeKey(t: TypeRepr): String         = normalizeType(t).show

    // Extract wire expressions from varargs
    val wireExprs: List[Expr[Wire[?, ?]]] = wiresExpr match {
      case Varargs(wires) => wires.toList
      case other          =>
        MacroCore.abortInvalidVarargs(other.show)
    }

    // Parse explicit wires: Map from type key to (wireExpr, outType, inTypes, isShared)
    case class WireInfo(
      wireExpr: Expr[Wire[?, ?]],
      outType: TypeRepr,
      inTypes: List[TypeRepr],
      isShared: Boolean,
      isExplicit: Boolean
    )

    def parseWireExpr(wireExpr: Expr[Wire[?, ?]]): WireInfo = {
      val wireTpe = wireExpr.asTerm.tpe.widen.dealias.simplified

      val (inType, outType) = wireTpe match {
        case AppliedType(_, List(in, out)) => (in.dealias.simplified, out.dealias.simplified)
        case other                         =>
          MacroCore.abortCannotExtractWireTypes(other.show)
      }

      val inTypes  = MacroCore.flattenIntersection(inType)
      val isShared = wireTpe.baseType(TypeRepr.of[Wire.Shared[?, ?]].typeSymbol).typeSymbol != defn.NothingClass

      WireInfo(wireExpr, outType, inTypes, isShared, isExplicit = true)
    }

    val explicitWires: List[WireInfo] = wireExprs.map(parseWireExpr)

    // Check for unmakeable types
    def isUnmakeableType(t: TypeRepr): Boolean = {
      val sym  = t.typeSymbol
      val name = sym.fullName

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
      if (collections.exists(c => name.startsWith(c))) return true

      false
    }

    // Auto-create a Wire.shared for a type
    def autoCreateWire(targetType: TypeRepr, chain: List[String]): WireInfo = {
      val sym = targetType.typeSymbol

      if (isUnmakeableType(targetType)) {
        MacroCore.abortUnmakeableType(targetType.show, chain)
      }

      if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
        MacroCore.abortAbstractType(targetType.show, chain)
      }

      val ctor = sym.primaryConstructor
      if (ctor == Symbol.noSymbol) {
        MacroCore.abortNoCtorForAutoCreate(targetType.show, chain)
      }

      // Use WireCodeGen to derive the wire
      targetType.asType match {
        case '[t] =>
          val (inTypeRepr, wireExpr) = WireCodeGen.deriveWire[t](WireCodeGen.WireKind.Shared)
          val inTypes                = MacroCore.flattenIntersection(inTypeRepr)
          WireInfo(wireExpr.asExprOf[Wire[?, ?]], targetType, inTypes, isShared = true, isExplicit = false)
      }
    }

    // Build the wire map by resolving T and all its dependencies
    // wireMap: keyed by the wire's OUTPUT type (canonical key)
    // aliasMap: maps required types to their canonical key (for subtype resolution)
    val wireMap  = scala.collection.mutable.Map[String, WireInfo]()
    val aliasMap = scala.collection.mutable.Map[String, String]()

    def resolveWire(targetType: TypeRepr, chain: List[String]): Unit = {
      val requiredKey = typeKey(targetType)

      // Check if we already have an alias for this type
      if (aliasMap.contains(requiredKey)) return

      // Check for cycle using required key
      if (chain.contains(requiredKey)) {
        val cycleStart = chain.indexOf(requiredKey)
        val cyclePath  = chain.drop(cycleStart) :+ requiredKey
        MacroCore.abortDependencyCycle(cyclePath)
      }

      // Find matching wire from explicit wires (including subtype matches)
      val matchingWires = explicitWires.filter { we =>
        normalizeType(we.outType) <:< normalizeType(targetType)
      }

      val wire: WireInfo = matchingWires match {
        case Nil =>
          // No explicit wire - auto-create
          autoCreateWire(targetType, chain)

        case List(single) =>
          // Exactly one match - use it
          single

        case multiple =>
          // Multiple matches - ambiguity error
          import zio.blocks.scope.internal.ErrorMessages
          val providers = multiple.map { we =>
            ErrorMessages.ProviderInfo(we.outType.show, None)
          }
          MacroCore.abortDuplicateProvider(targetType.show, providers)
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

    // Start resolution from T
    resolveWire(tpe, Nil)

    // Validate: check for duplicate parameter types within constructors
    wireMap.values.foreach { we =>
      val paramTypes = we.inTypes.map(t => typeKey(t))
      val duplicates = paramTypes.groupBy(identity).filter(_._2.size > 1).keys
      if (duplicates.nonEmpty) {
        MacroCore.abortDuplicateParamType(we.outType.show, duplicates.head)
      }
    }

    // Topological sort - leaves first, T last
    // Use aliasMap to resolve dependency types to their canonical keys
    val aliasMapFinal = aliasMap.toMap

    def canonicalKeyFor(depType: TypeRepr): String = {
      val requiredKey = typeKey(depType)
      aliasMapFinal.getOrElse(requiredKey, requiredKey)
    }

    def topologicalSort(): List[String] = {
      val visited = scala.collection.mutable.Set[String]()
      val result  = scala.collection.mutable.ListBuffer[String]()

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
    // PHASE 4: Generate Resource Composition
    // ═══════════════════════════════════════════════════════════════════════
    //
    // The key insight: Resource.shared must wrap the ENTIRE construction
    // including dependency acquisition. If we put Resource.shared inside a
    // flatMap, we get a new Resource.shared instance per call, breaking sharing.
    //
    // Correct structure for shared type Mid with dependency Leaf:
    //   val resMid = Resource.shared[Mid] { finalizer =>
    //     val leaf = resLeaf.make(finalizer)
    //     wire.make(finalizer, ctx.add(leaf))
    //   }
    //
    // NOT:
    //   val resMid = resLeaf.flatMap { leaf =>
    //     Resource.shared[Mid](f => wire.make(f, ctx))  // Wrong: new instance per call!
    //   }

    // Build a helper to create context from values
    def buildContextFromValues(values: List[(TypeRepr, Expr[?])]): Expr[Context[?]] =
      values.foldLeft('{ Context.empty }: Expr[Context[?]]) { case (ctxExpr, (t, valueExpr)) =>
        t.asType match {
          case '[vt] =>
            val typedValue = valueExpr.asExprOf[vt]
            '{ $ctxExpr.add[vt]($typedValue)(using summonInline[IsNominalType[vt]]) }
        }
      }

    // Create a Resource expression for a type given its dependencies' Resource expressions
    // For shared resources, wrap the entire construction (including dep acquisition) in Resource.shared
    // For unique resources, use flatMap chain so each use creates fresh instances
    def createResourceExpr(
      we: WireInfo,
      depResources: Map[String, (TypeRepr, Expr[Resource[?]])]
    ): Expr[Resource[?]] = {
      val deps = we.inTypes

      we.outType.asType match {
        case '[outT] =>
          if (deps.isEmpty) {
            // Leaf resource - no dependencies
            val wireExpr = we.wireExpr
            if (we.isShared) {
              '{
                val wire = $wireExpr.asInstanceOf[Wire[Any, outT]]
                Resource.shared[outT](f => wire.make(f, Context.empty.asInstanceOf[Context[Any]]))
              }
            } else {
              '{
                val wire = $wireExpr.asInstanceOf[Wire[Any, outT]]
                Resource.unique[outT](f => wire.make(f, Context.empty.asInstanceOf[Context[Any]]))
              }
            }
          } else if (we.isShared) {
            // Shared type with dependencies - wrap entire construction in Resource.shared
            // This ensures we get ONE Resource.shared instance, not one per use
            val wireExpr = we.wireExpr

            // Generate code to acquire all deps and build context inside Resource.shared
            def buildDepAcquisition(
              remainingDeps: List[TypeRepr],
              boundValues: List[(TypeRepr, Expr[?])],
              finalizerExpr: Expr[Finalizer]
            ): Expr[outT] =
              remainingDeps match {
                case Nil =>
                  val ctxExpr = buildContextFromValues(boundValues)
                  '{
                    val wire = $wireExpr.asInstanceOf[Wire[Any, outT]]
                    wire.make($finalizerExpr, $ctxExpr.asInstanceOf[Context[Any]])
                  }

                case dep :: rest =>
                  val depKey      = canonicalKeyFor(dep)
                  val (_, depRes) = depResources(depKey)

                  dep.asType match {
                    case '[depT] =>
                      val typedDepRes = depRes.asExprOf[Resource[depT]]
                      '{
                        val depValue: depT = $typedDepRes.make($finalizerExpr)
                        ${ buildDepAcquisition(rest, boundValues :+ (dep, 'depValue), finalizerExpr) }
                      }
                  }
              }

            '{
              Resource.shared[outT] { finalizer =>
                ${ buildDepAcquisition(deps, Nil, 'finalizer) }
              }
            }
          } else {
            // Unique type with dependencies - use flatMap chain
            // Each use creates fresh instances (that's what unique means)
            val wireExpr = we.wireExpr

            def buildChain(
              remainingDeps: List[TypeRepr],
              boundValues: List[(TypeRepr, Expr[?])]
            ): Expr[Resource[outT]] =
              remainingDeps match {
                case Nil =>
                  val ctxExpr = buildContextFromValues(boundValues)
                  '{
                    val wire = $wireExpr.asInstanceOf[Wire[Any, outT]]
                    Resource.unique[outT](f => wire.make(f, $ctxExpr.asInstanceOf[Context[Any]]))
                  }

                case dep :: rest =>
                  val depKey      = canonicalKeyFor(dep)
                  val (_, depRes) = depResources(depKey)

                  dep.asType match {
                    case '[depT] =>
                      val typedDepRes = depRes.asExprOf[Resource[depT]]
                      '{
                        $typedDepRes.flatMap { (depValue: depT) =>
                          ${ buildChain(rest, boundValues :+ (dep, 'depValue)) }
                        }
                      }
                  }
              }

            buildChain(deps, Nil)
          }
      }
    }

    // Now we need to generate val bindings for each resource.
    // We'll use a recursive let pattern to build up the block.
    def generateResourceBlock(
      remaining: List[String],
      built: Map[String, (TypeRepr, Expr[Resource[?]])]
    ): Expr[Resource[T]] =
      remaining match {
        case Nil =>
          // All resources built, return the target
          val (_, targetRes) = built(targetKey)
          targetRes.asExprOf[Resource[T]]

        case key :: rest =>
          val we      = wireMapFinal(key)
          val resExpr = createResourceExpr(we, built)

          we.outType.asType match {
            case '[outT] =>
              val typedResExpr = resExpr.asExprOf[Resource[outT]]
              '{
                val res: Resource[outT] = $typedResExpr
                ${
                  val newBuilt = built + (key -> (we.outType, '{ res }.asExprOf[Resource[?]]))
                  generateResourceBlock(rest, newBuilt)
                }
              }
          }
      }

    generateResourceBlock(sorted, Map.empty)
  }
}
