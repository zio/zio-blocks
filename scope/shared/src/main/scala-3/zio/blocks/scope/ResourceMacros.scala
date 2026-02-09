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
      MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      MacroCore.abort(MacroCore.ScopeMacroError.NoPrimaryCtor(tpe.show))
    }

    val paramLists = ctor.paramSymss

    // Separate regular params from implicit/given params
    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(p => !p.flags.is(Flags.Given) && !p.flags.is(Flags.Implicit))
    }

    val allRegularParams = regularParams.flatten

    // Check for Scope/Finalizer parameter in implicits
    val hasScopeParam = implicitParams.flatten.exists { param =>
      val paramType = tpe.memberType(param)
      paramType <:< TypeRepr.of[Scope[?, ?]] || MacroCore.isFinalizerType(paramType)
    }

    // Collect dependencies (non-Finalizer regular params)
    val depTypes: List[TypeRepr] = allRegularParams.flatMap { param =>
      val paramType = tpe.memberType(param).dealias.simplified
      if (MacroCore.isFinalizerType(paramType)) None
      else Some(paramType)
    }

    if (depTypes.nonEmpty) {
      report.errorAndAbort(
        s"Resource.from[${tpe.show}] cannot be derived: ${tpe.show} has dependencies: ${depTypes.map(_.show).mkString(", ")}. " +
          s"Use Resource.from[${tpe.show}](wire1, wire2, ...) to provide wires for all dependencies."
      )
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val ctorSym         = sym.primaryConstructor

    // Generate resource body
    if (hasScopeParam) {
      // Constructor takes implicit Scope/Finalizer
      '{
        Resource.shared[T] { finalizer =>
          ${
            val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
            val applied  = Apply(Apply(ctorTerm, Nil), List('{ finalizer }.asTerm))
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
        report.errorAndAbort(s"Expected varargs of Wire expressions, got: ${other.show}")
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
          report.errorAndAbort(s"Cannot extract types from wire: ${other.show}")
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
        val chainStr = chain.map(s => s"  → $s").mkString("\n")
        report.errorAndAbort(
          s"""Cannot auto-create ${targetType.show}: this type cannot be auto-created.
             |
             |Required by:
             |$chainStr
             |
             |Fix: provide Wire(value) with the desired value:
             |
             |  Resource.from[...](
             |    Wire(...),  // provide a value for ${targetType.show}
             |    ...
             |  )""".stripMargin
        )
      }

      if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
        val chainStr = chain.map(s => s"  → $s").mkString("\n")
        report.errorAndAbort(
          s"""Cannot auto-create ${targetType.show}: it is abstract.
             |
             |Required by:
             |$chainStr
             |
             |Fix: provide a wire for a concrete implementation:
             |
             |  Resource.from[...](
             |    Wire.shared[ConcreteImpl],  // provides ${targetType.show}
             |    ...
             |  )""".stripMargin
        )
      }

      val ctor = sym.primaryConstructor
      if (ctor == Symbol.noSymbol) {
        report.errorAndAbort(s"Cannot auto-create ${targetType.show}: no primary constructor")
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
    val wireMap = scala.collection.mutable.Map[String, WireInfo]()

    def resolveWire(targetType: TypeRepr, chain: List[String]): Unit = {
      val key = typeKey(targetType)
      if (wireMap.contains(key)) return

      // Check for cycle
      if (chain.contains(key)) {
        val cycleStart = chain.indexOf(key)
        val cyclePath  = chain.drop(cycleStart) :+ key
        MacroCore.abort(MacroCore.ScopeMacroError.DependencyCycle(cyclePath))
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
          val providers = multiple.map { we =>
            MacroCore.ProviderInfo(we.outType.show, None)
          }
          MacroCore.abort(MacroCore.ScopeMacroError.DuplicateProvider(targetType.show, providers))
      }

      wireMap(key) = wire

      // Recurse into dependencies
      val newChain = chain :+ key
      wire.inTypes.foreach { depType =>
        resolveWire(depType, newChain)
      }
    }

    // Start resolution from T
    resolveWire(tpe, Nil)

    // Validate: check for duplicate parameter types within constructors
    wireMap.values.foreach { we =>
      val paramTypes = we.inTypes.map(t => typeKey(t))
      val duplicates = paramTypes.groupBy(identity).filter(_._2.size > 1).keys
      if (duplicates.nonEmpty) {
        report.errorAndAbort(
          s"Constructor of ${we.outType.show} has multiple parameters of type ${duplicates.head}.\n" +
            s"Context is type-indexed and cannot supply distinct values.\n" +
            s"Fix: wrap one parameter in an opaque type to distinguish them."
        )
      }
    }

    // Topological sort - leaves first, T last
    def topologicalSort(): List[String] = {
      val visited = scala.collection.mutable.Set[String]()
      val result  = scala.collection.mutable.ListBuffer[String]()

      def visit(key: String): Unit = {
        if (visited.contains(key)) return
        visited += key

        wireMap.get(key).foreach { we =>
          we.inTypes.foreach { dep =>
            visit(typeKey(dep))
          }
        }

        result += key
      }

      wireMap.keys.toList.sorted.foreach(visit)
      result.toList
    }

    val sorted       = topologicalSort()
    val targetKey    = typeKey(tpe)
    val wireMapFinal = wireMap.toMap

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 4: Generate Resource Composition
    // ═══════════════════════════════════════════════════════════════════════
    //
    // We generate a block that creates Resource values for each type in
    // topological order. The key is that Resource.Shared provides memoization -
    // calling .make() on the same Resource.Shared instance returns the same value.
    //
    // We use ValDef.let to properly bind each Resource to a val, then reference
    // those vals in subsequent flatMap chains.

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
          } else {
            // Has dependencies - generate flatMap chain
            def buildChain(
              remainingDeps: List[TypeRepr],
              boundValues: List[(TypeRepr, Expr[?])]
            ): Expr[Resource[outT]] =
              remainingDeps match {
                case Nil =>
                  val ctxExpr  = buildContextFromValues(boundValues)
                  val wireExpr = we.wireExpr
                  if (we.isShared) {
                    '{
                      val wire = $wireExpr.asInstanceOf[Wire[Any, outT]]
                      Resource.shared[outT](f => wire.make(f, $ctxExpr.asInstanceOf[Context[Any]]))
                    }
                  } else {
                    '{
                      val wire = $wireExpr.asInstanceOf[Wire[Any, outT]]
                      Resource.unique[outT](f => wire.make(f, $ctxExpr.asInstanceOf[Context[Any]]))
                    }
                  }

                case dep :: rest =>
                  val depKey      = typeKey(dep)
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
