package zio.blocks.schema.comptime

import scala.quoted.*

trait AllowsCompanionVersionSpecific {
  inline given derived[S <: Allows.Structural, A]: Allows[A, S] =
    ${ AllowsMacroImpl.deriveAllows[S, A] }
}

private[comptime] object AllowsMacroImpl {

  def deriveAllows[S <: Allows.Structural: Type, A: Type](using q: Quotes): Expr[Allows[A, S]] = {
    import q.reflect.*

    // -----------------------------------------------------------------------
    // Grammar node ADT (internal, compile-time only)
    // -----------------------------------------------------------------------

    sealed trait GrammarNode
    case object GPrimitive                                extends GrammarNode // any primitive
    case class GSpecificPrimitive(scalaFQN: String)       extends GrammarNode // one exact primitive
    case object GDynamic                                  extends GrammarNode
    case object GSelf                                     extends GrammarNode
    case class GRecord(inner: GrammarNode)                extends GrammarNode
    case class GVariant(inner: GrammarNode)               extends GrammarNode
    case class GSequence(inner: GrammarNode)              extends GrammarNode
    case class GMap(key: GrammarNode, value: GrammarNode) extends GrammarNode
    case class GOptional(inner: GrammarNode)              extends GrammarNode
    case class GWrapped(inner: GrammarNode)               extends GrammarNode
    case class GUnion(branches: List[GrammarNode])        extends GrammarNode

    // -----------------------------------------------------------------------
    // Grammar class symbols (looked up once)
    // -----------------------------------------------------------------------

    val primitiveClass = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Primitive")
    val dynamicClass   = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Dynamic")
    val selfClass      = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Self")
    val recordClass    = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Record")
    val variantClass   = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Variant")
    val sequenceClass  = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Sequence")
    val mapClass       = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Map")
    val optionalClass  = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Optional")
    val wrappedClass   = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Wrapped")
    val pipeClass      = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.|")

    // Map from Allows.Primitive.Xxx grammar class FQN → the Scala type FQN it matches.
    // In Scala 3, Symbol.fullName for nested classes inside objects uses '$' separators
    // e.g. "zio.blocks.schema.comptime.Allows$.Primitive$.Int"
    val specificPrimitiveMap: Map[String, String] = Map(
      "zio.blocks.schema.comptime.Allows$.Primitive$.Unit"           -> "scala.Unit",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Boolean"        -> "scala.Boolean",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Byte"           -> "scala.Byte",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Short"          -> "scala.Short",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Int"            -> "scala.Int",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Long"           -> "scala.Long",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Float"          -> "scala.Float",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Double"         -> "scala.Double",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Char"           -> "scala.Char",
      "zio.blocks.schema.comptime.Allows$.Primitive$.String"         -> "java.lang.String",
      "zio.blocks.schema.comptime.Allows$.Primitive$.BigInt"         -> "scala.math.BigInt",
      "zio.blocks.schema.comptime.Allows$.Primitive$.BigDecimal"     -> "scala.math.BigDecimal",
      "zio.blocks.schema.comptime.Allows$.Primitive$.UUID"           -> "java.util.UUID",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Currency"       -> "java.util.Currency",
      "zio.blocks.schema.comptime.Allows$.Primitive$.DayOfWeek"      -> "java.time.DayOfWeek",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Duration"       -> "java.time.Duration",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Instant"        -> "java.time.Instant",
      "zio.blocks.schema.comptime.Allows$.Primitive$.LocalDate"      -> "java.time.LocalDate",
      "zio.blocks.schema.comptime.Allows$.Primitive$.LocalDateTime"  -> "java.time.LocalDateTime",
      "zio.blocks.schema.comptime.Allows$.Primitive$.LocalTime"      -> "java.time.LocalTime",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Month"          -> "java.time.Month",
      "zio.blocks.schema.comptime.Allows$.Primitive$.MonthDay"       -> "java.time.MonthDay",
      "zio.blocks.schema.comptime.Allows$.Primitive$.OffsetDateTime" -> "java.time.OffsetDateTime",
      "zio.blocks.schema.comptime.Allows$.Primitive$.OffsetTime"     -> "java.time.OffsetTime",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Period"         -> "java.time.Period",
      "zio.blocks.schema.comptime.Allows$.Primitive$.Year"           -> "java.time.Year",
      "zio.blocks.schema.comptime.Allows$.Primitive$.YearMonth"      -> "java.time.YearMonth",
      "zio.blocks.schema.comptime.Allows$.Primitive$.ZoneId"         -> "java.time.ZoneId",
      "zio.blocks.schema.comptime.Allows$.Primitive$.ZoneOffset"     -> "java.time.ZoneOffset",
      "zio.blocks.schema.comptime.Allows$.Primitive$.ZonedDateTime"  -> "java.time.ZonedDateTime"
    )

    // -----------------------------------------------------------------------
    // Grammar decomposition from S
    // -----------------------------------------------------------------------

    def decomposeGrammar(tpe: TypeRepr): GrammarNode = tpe.dealias match {
      case OrType(left, right) =>
        def flattenOr(t: TypeRepr): List[GrammarNode] = t.dealias match {
          case OrType(l, r) => flattenOr(l) ++ flattenOr(r)
          case other        => List(decomposeGrammar(other))
        }
        GUnion(flattenOr(left) ++ flattenOr(right))
      case AppliedType(fn, args) =>
        val sym = fn.typeSymbol
        if (sym == pipeClass) GUnion(List(decomposeGrammar(args(0)), decomposeGrammar(args(1))))
        else if (sym == recordClass) GRecord(decomposeGrammar(args(0)))
        else if (sym == variantClass) GVariant(decomposeGrammar(args(0)))
        else if (sym == sequenceClass) GSequence(decomposeGrammar(args(0)))
        else if (sym == mapClass) GMap(decomposeGrammar(args(0)), decomposeGrammar(args(1)))
        else if (sym == optionalClass) GOptional(decomposeGrammar(args(0)))
        else if (sym == wrappedClass) GWrapped(decomposeGrammar(args(0)))
        else
          report.errorAndAbort(
            s"Unknown Allows grammar node: ${tpe.show}. " +
              "Expected Primitive, Record, Variant, Sequence, Map, Optional, Wrapped, Dynamic, Self, or |.",
            Position.ofMacroExpansion
          )
      case other =>
        val sym    = other.typeSymbol
        val symFQN = sym.fullName
        if (sym == primitiveClass) GPrimitive
        else if (sym == dynamicClass) GDynamic
        else if (sym == selfClass) GSelf
        else if (specificPrimitiveMap.contains(symFQN)) GSpecificPrimitive(specificPrimitiveMap(symFQN))
        else
          report.errorAndAbort(
            s"Unknown Allows grammar node: ${tpe.show}. " +
              "Expected Primitive (or a specific Primitive.Xxx subtype), Record, Variant, Sequence, " +
              "Map, Optional, Wrapped, Dynamic, Self, or |.",
            Position.ofMacroExpansion
          )
    }

    val rootGrammar: GrammarNode = decomposeGrammar(TypeRepr.of[S])

    // -----------------------------------------------------------------------
    // Type inspection helpers (self-contained, no CommonMacroOps dependency)
    // -----------------------------------------------------------------------

    val wildcard          = TypeBounds(defn.NothingClass.typeRef, defn.AnyClass.typeRef)
    val iterableWild      = Symbol.requiredClass("scala.collection.Iterable").typeRef.appliedTo(wildcard)
    val iteratorWild      = Symbol.requiredClass("scala.collection.Iterator").typeRef.appliedTo(wildcard)
    val arrayWild         = defn.ArrayClass.typeRef.appliedTo(wildcard)
    val scalaMapWild      = Symbol.requiredClass("scala.collection.Map").typeRef.appliedTo(List(wildcard, wildcard))
    val dynamicValueClass = Symbol.requiredClass("zio.blocks.schema.DynamicValue")
    val dynamicValueTpe   = dynamicValueClass.typeRef

    def isOption(tpe: TypeRepr): Boolean  = tpe <:< TypeRepr.of[Option[?]]
    def isDynamic(tpe: TypeRepr): Boolean = tpe <:< dynamicValueTpe
    def isMapType(tpe: TypeRepr): Boolean = tpe <:< scalaMapWild
    def isSeqType(tpe: TypeRepr): Boolean =
      (tpe <:< iterableWild || tpe <:< iteratorWild || tpe <:< arrayWild) &&
        !isOption(tpe) && !isMapType(tpe)

    def isSealed(tpe: TypeRepr): Boolean =
      tpe.classSymbol.exists { sym =>
        val f = sym.flags
        f.is(Flags.Sealed) && (f.is(Flags.Abstract) || f.is(Flags.Trait))
      }

    def isProduct(tpe: TypeRepr): Boolean =
      tpe.classSymbol.exists { sym =>
        val f = sym.flags
        !(f.is(Flags.Abstract) || f.is(Flags.JavaDefined) || f.is(Flags.Trait))
      }

    def isModule(tpe: TypeRepr): Boolean =
      tpe.termSymbol.flags.is(Flags.Enum) ||
        tpe.typeSymbol.flags.is(Flags.Module)

    def isOrType(tpe: TypeRepr): Boolean = tpe.dealias match {
      case _: OrType => true
      case _         => false
    }

    def allOrTypes(tpe: TypeRepr): List[TypeRepr] = {
      val seen                    = scala.collection.mutable.HashSet.empty[TypeRepr]
      val types                   = scala.collection.mutable.ListBuffer.empty[TypeRepr]
      def loop(t: TypeRepr): Unit = t.dealias match {
        case OrType(l, r) => loop(l); loop(r)
        case dealiased    => if (seen.add(dealiased)) types += dealiased
      }
      loop(tpe)
      types.toList.sortBy(_.typeSymbol.fullName)
    }

    def isOpaque(tpe: TypeRepr): Boolean = tpe.typeSymbol.flags.is(Flags.Opaque)

    def isZioPrelude(tpe: TypeRepr): Boolean = tpe match {
      case TypeRef(compTpe, "Type") => compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
      case _                        => false
    }

    def isNeotype(tpe: TypeRepr): Boolean = tpe match {
      case TypeRef(compTpe, "Type") =>
        compTpe.baseClasses.exists { cls =>
          val fn = cls.fullName
          fn == "neotype.Newtype" || fn == "neotype.Subtype"
        }
      case _ => false
    }

    def isWrapper(tpe: TypeRepr): Boolean = isOpaque(tpe) || isZioPrelude(tpe) || isNeotype(tpe)

    def opaqueDealias(tpe: TypeRepr): TypeRepr = {
      import scala.annotation.tailrec
      @tailrec def loop(t: TypeRepr): TypeRepr = t match {
        case tr: TypeRef         => if (tr.isOpaqueAlias) loop(tr.translucentSuperType.dealias) else t
        case AppliedType(a, _)   => loop(a.dealias)
        case TypeLambda(_, _, b) => loop(b.dealias)
        case _                   => t
      }
      loop(tpe)
    }

    def zioPreludeDealias(tpe: TypeRepr): TypeRepr = tpe match {
      case TypeRef(compTpe, _) =>
        compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
          case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
          case None      =>
            report.errorAndAbort(s"Cannot unwrap ZIO Prelude newtype: ${tpe.show}", Position.ofMacroExpansion)
        }
      case _ => report.errorAndAbort(s"Cannot unwrap ZIO Prelude newtype: ${tpe.show}", Position.ofMacroExpansion)
    }

    def neotypeDealias(tpe: TypeRepr): TypeRepr = tpe match {
      case TypeRef(compTpe, "Type") =>
        compTpe.baseClasses.find { cls =>
          val fn = cls.fullName
          fn == "neotype.Newtype" || fn == "neotype.Subtype"
        } match {
          case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
          case None      =>
            report.errorAndAbort(s"Cannot unwrap neotype: ${tpe.show}", Position.ofMacroExpansion)
        }
      case _ => report.errorAndAbort(s"Cannot unwrap neotype: ${tpe.show}", Position.ofMacroExpansion)
    }

    def unwrap(tpe: TypeRepr): TypeRepr =
      if (isOpaque(tpe)) opaqueDealias(tpe)
      else if (isZioPrelude(tpe)) zioPreludeDealias(tpe)
      else neotypeDealias(tpe)

    def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match {
      case AppliedType(_, args) => args.map(_.dealias)
      case _                    => Nil
    }

    def casesOf(tpe: TypeRepr): List[TypeRepr] = {
      val sym   = tpe.typeSymbol
      val tArgs = typeArgs(tpe)
      sym.children.map { child =>
        if (child.isType) {
          val sub = child.typeRef
          sub.memberType(child.primaryConstructor) match {
            case _: MethodType                                              => sub
            case PolyType(names, _, MethodType(_, _, AppliedType(base, _))) =>
              val binding = typeArgs(sub.baseType(sym))
                .zip(tArgs)
                .foldLeft(Map.empty[String, TypeRepr]) { case (m, (ca, pa)) =>
                  val cs = ca.typeSymbol
                  if (cs.isTypeParam) m.updated(cs.name, pa) else m
                }
              base.appliedTo(names.map(n => binding.getOrElse(n, defn.AnyClass.typeRef)))
            case _ => sub
          }
        } else Ref(child).tpe
      }
    }

    def fieldTypes(tpe: TypeRepr): List[(String, TypeRepr)] =
      tpe.classSymbol match {
        case None      => Nil
        case Some(sym) =>
          val tParams = sym.primaryConstructor.paramSymss match {
            case tps :: _ if tps.exists(_.isTypeParam) => tps
            case _                                     => Nil
          }
          val tArgs = if (tParams.nonEmpty) typeArgs(tpe) else Nil
          sym.caseFields.map { f =>
            var fTpe = tpe.memberType(f).dealias
            if (tArgs.nonEmpty) fTpe = fTpe.substituteTypes(tParams, tArgs)
            (f.name, fTpe)
          }
      }

    // Full set of java.time primitive type names (from PrimitiveType.scala)
    val javaTimePrimitiveNames = Set(
      "java.time.DayOfWeek",
      "java.time.Duration",
      "java.time.Instant",
      "java.time.LocalDate",
      "java.time.LocalDateTime",
      "java.time.LocalTime",
      "java.time.Month",
      "java.time.MonthDay",
      "java.time.OffsetDateTime",
      "java.time.OffsetTime",
      "java.time.Period",
      "java.time.Year",
      "java.time.YearMonth",
      "java.time.ZoneId",
      "java.time.ZoneOffset",
      "java.time.ZonedDateTime"
    )

    def isPrimitive(tpe: TypeRepr): Boolean = {
      val t = tpe.dealias
      t =:= defn.BooleanClass.typeRef ||
      t =:= defn.ByteClass.typeRef ||
      t =:= defn.ShortClass.typeRef ||
      t =:= defn.IntClass.typeRef ||
      t =:= defn.LongClass.typeRef ||
      t =:= defn.FloatClass.typeRef ||
      t =:= defn.DoubleClass.typeRef ||
      t =:= defn.CharClass.typeRef ||
      t =:= defn.UnitClass.typeRef ||
      t =:= defn.StringClass.typeRef ||
      t <:< TypeRepr.of[BigInt] ||
      t <:< TypeRepr.of[BigDecimal] ||
      t <:< TypeRepr.of[java.util.UUID] ||
      t <:< TypeRepr.of[java.util.Currency] ||
      javaTimePrimitiveNames.contains(t.typeSymbol.fullName)
    }

    // -----------------------------------------------------------------------
    // Violation accumulator
    // -----------------------------------------------------------------------

    val errors = scala.collection.mutable.ListBuffer.empty[String]

    def err(path: List[String], found: String, required: String, hint: String = ""): Unit = {
      val pathStr = if (path.isEmpty) "<root>" else path.reverse.mkString(".")
      val msg     = s"Schema shape violation at $pathStr: found $found, required $required" +
        (if (hint.nonEmpty) s"\n  Hint: $hint" else "")
      errors += msg
    }

    // -----------------------------------------------------------------------
    // Core check: does `tpe` satisfy `grammar`?
    // -----------------------------------------------------------------------

    def check(
      tpe: TypeRepr,
      grammar: GrammarNode,
      path: List[String],
      seen: Set[Symbol],
      rootTpe: TypeRepr
    ): Unit = {
      val dt        = tpe.dealias
      val tpeSym    = dt.typeSymbol
      val isAlready = seen.contains(tpeSym)

      if (isAlready && !(dt =:= rootTpe.dealias)) {
        err(
          path,
          tpe.show,
          "no mutual recursion",
          s"Mutually recursive types are not supported by Allows. " +
            s"Cycle: ${seen.map(_.name).mkString(" -> ")} -> ${tpeSym.name}"
        )
        return
      }

      if (isAlready && (dt =:= rootTpe.dealias)) {
        grammar match {
          case GSelf =>
            return // self-recursion via Self: accepted
          case GRecord(_) =>
            return // root type satisfies Record[...] by assumption (we're in its own check)
          case GUnion(_) =>
            // Probe each branch. Use seen without this type to allow re-examination
            // (the Self branch will re-add it and terminate correctly).
            checkAgainstUnion(tpe, grammar, path, seen - tpeSym, rootTpe)
            return
          case other =>
            // Root type re-encountered under a terminal grammar (Primitive, Dynamic,
            // Sequence, Map, Optional, Wrapped, Variant). The root type clearly
            // does not satisfy these directly — report a violation.
            err(path, describeType(tpe), describeGrammar(other))
            return
        }
      }

      grammar match {
        case GSelf =>
          // Expand Self to the root grammar. Only add tpeSym to `seen` if it IS
          // the root type (preventing infinite self-recursion). For non-root types
          // (e.g. a nested record Author being checked via Self), do not add to
          // seen — adding it would incorrectly trigger the mutual-recursion guard.
          val seenNext = if (dt =:= rootTpe.dealias) seen + tpeSym else seen
          check(tpe, rootGrammar, path, seenNext, rootTpe)

        case GPrimitive =>
          if (!isPrimitive(dt))
            err(path, describeType(tpe), "Primitive")

        case GSpecificPrimitive(scalaFQN) =>
          // The type must be exactly the named primitive (or a subtype of it)
          val actualFQN = dt.typeSymbol.fullName
          if (actualFQN != scalaFQN)
            err(path, describeType(tpe), s"Primitive.${scalaFQN.split('.').last}")

        case GDynamic =>
          if (!isDynamic(dt))
            err(path, describeType(tpe), "Dynamic")

        case GRecord(inner) =>
          if (isModule(dt)) {
            // zero-field record: vacuously satisfies any Record[X]
          } else if (isProduct(dt)) {
            fieldTypes(dt).foreach { case (name, fTpe) =>
              checkAgainstUnion(fTpe, inner, name :: path, seen + tpeSym, rootTpe)
            }
          } else {
            err(path, describeType(tpe), "Record[...]")
          }

        case GVariant(inner) =>
          if (isOrType(dt)) {
            allOrTypes(dt).foreach { caseTpe =>
              checkAgainstUnion(caseTpe, inner, caseTpe.typeSymbol.name :: path, seen + tpeSym, rootTpe)
            }
          } else if (isSealed(dt)) {
            casesOf(dt).foreach { caseTpe =>
              checkAgainstUnion(caseTpe, inner, caseTpe.typeSymbol.name :: path, seen + tpeSym, rootTpe)
            }
          } else {
            err(path, describeType(tpe), "Variant[...]")
          }

        case GSequence(inner) =>
          if (isSeqType(dt)) {
            val elemTpe = typeArgs(dt).headOption.getOrElse(defn.AnyClass.typeRef)
            // Don't add collection class to `seen` — stdlib collections can appear nested
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence[...]")
          }

        case GMap(keyG, valG) =>
          if (isMapType(dt)) {
            val args   = typeArgs(dt)
            val keyTpe = args.headOption.getOrElse(defn.AnyClass.typeRef)
            val valTpe = args.lastOption.getOrElse(defn.AnyClass.typeRef)
            checkAgainstUnion(keyTpe, keyG, "<key>" :: path, seen, rootTpe)
            checkAgainstUnion(valTpe, valG, "<value>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Map[...]")
          }

        case GOptional(inner) =>
          if (isOption(dt)) {
            val innerTpe = typeArgs(dt).headOption.getOrElse(defn.AnyClass.typeRef)
            // Don't add Option to `seen` — it's a stdlib type, not user recursion
            checkAgainstUnion(innerTpe, inner, "<inner>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Optional[...]")
          }

        case GWrapped(inner) =>
          if (isWrapper(dt)) {
            val unwrapped = unwrap(dt)
            checkAgainstUnion(unwrapped, inner, "<wrapped>" :: path, seen + tpeSym, rootTpe)
          } else {
            err(path, describeType(tpe), "Wrapped[...]")
          }

        case GUnion(branches) =>
          checkAgainstUnion(tpe, GUnion(branches), path, seen, rootTpe)
      }
    }

    def checkAgainstUnion(
      tpe: TypeRepr,
      grammar: GrammarNode,
      path: List[String],
      seen: Set[Symbol],
      rootTpe: TypeRepr
    ): Unit = grammar match {
      case GUnion(branches) =>
        val tempErrors = branches.map { branch =>
          val buf = scala.collection.mutable.ListBuffer.empty[String]
          checkCollecting(tpe, branch, path, seen, rootTpe, buf)
          buf.toList
        }
        if (!tempErrors.exists(_.isEmpty)) {
          err(
            path,
            describeType(tpe),
            branches.map(describeGrammar).mkString(" | "),
            s"Type '${tpe.show}' does not match any allowed shape"
          )
        }
      case other =>
        check(tpe, other, path, seen, rootTpe)
    }

    def checkCollecting(
      tpe: TypeRepr,
      grammar: GrammarNode,
      path: List[String],
      seen: Set[Symbol],
      rootTpe: TypeRepr,
      buf: scala.collection.mutable.ListBuffer[String]
    ): Unit = {
      val saved = errors.toList
      errors.clear()
      check(tpe, grammar, path, seen, rootTpe)
      val newErrors = errors.toList
      errors.clear()
      errors ++= saved
      buf ++= newErrors
    }

    // -----------------------------------------------------------------------
    // Describe helpers
    // -----------------------------------------------------------------------

    def describeType(tpe: TypeRepr): String = {
      val dt = tpe.dealias
      if (isPrimitive(dt)) s"Primitive(${dt.show})"
      else if (isDynamic(dt)) "Dynamic"
      else if (isModule(dt)) s"Record(case object ${dt.typeSymbol.name})"
      else if (isProduct(dt)) s"Record(${dt.typeSymbol.name})"
      else if (isSealed(dt) || isOrType(dt)) s"Variant(${dt.show})"
      else if (isOption(dt)) s"Optional[${typeArgs(dt).headOption.map(_.show).getOrElse("?")}]"
      else if (isSeqType(dt)) s"Sequence[${typeArgs(dt).headOption.map(_.show).getOrElse("?")}]"
      else if (isMapType(dt)) {
        val args = typeArgs(dt)
        s"Map[${args.headOption.map(_.show).getOrElse("?")}, ${args.lastOption.map(_.show).getOrElse("?")}]"
      } else if (isWrapper(dt)) s"Wrapped[${tpe.show}]"
      else dt.show
    }

    def describeGrammar(g: GrammarNode): String = g match {
      case GPrimitive              => "Primitive"
      case GSpecificPrimitive(fqn) => s"Primitive.${fqn.split('.').last}"
      case GDynamic                => "Dynamic"
      case GSelf                   => "Self"
      case GRecord(inner)          => s"Record[${describeGrammar(inner)}]"
      case GVariant(inner)         => s"Variant[${describeGrammar(inner)}]"
      case GSequence(inner)        => s"Sequence[${describeGrammar(inner)}]"
      case GMap(k, v)              => s"Map[${describeGrammar(k)}, ${describeGrammar(v)}]"
      case GOptional(inner)        => s"Optional[${describeGrammar(inner)}]"
      case GWrapped(inner)         => s"Wrapped[${describeGrammar(inner)}]"
      case GUnion(branches)        => branches.map(describeGrammar).mkString(" | ")
    }

    // -----------------------------------------------------------------------
    // Run the check
    // -----------------------------------------------------------------------

    val rootTpe = TypeRepr.of[A]
    check(rootTpe, rootGrammar, List(rootTpe.typeSymbol.name), Set.empty, rootTpe)

    if (errors.nonEmpty) {
      report.errorAndAbort(errors.mkString("\n"), Position.ofMacroExpansion)
    }

    '{ Allows.instance.asInstanceOf[Allows[A, S]] }
  }
}
