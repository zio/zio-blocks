/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.comptime

import scala.quoted.*
import zio.blocks.schema.comptime.internal.AllowsErrorMessages

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

    val color = AllowsErrorMessages.Colors.shouldUseColor

    sealed trait GrammarNode
    case object GPrimitive                                extends GrammarNode // any primitive (catch-all)
    case object GDynamic                                  extends GrammarNode
    case object GSelf                                     extends GrammarNode
    case class GRecord(inner: GrammarNode)                extends GrammarNode
    case class GSequence(inner: GrammarNode)              extends GrammarNode // any sequence
    case class GSeqList(inner: GrammarNode)               extends GrammarNode // List only
    case class GSeqVector(inner: GrammarNode)             extends GrammarNode // Vector only
    case class GSeqSet(inner: GrammarNode)                extends GrammarNode // Set only
    case class GSeqArray(inner: GrammarNode)              extends GrammarNode // Array only
    case class GSeqChunk(inner: GrammarNode)              extends GrammarNode // Chunk only
    case class GMap(key: GrammarNode, value: GrammarNode) extends GrammarNode
    case class GOptional(inner: GrammarNode)              extends GrammarNode
    case class GWrapped(inner: GrammarNode)               extends GrammarNode
    case class GIsType(targetTpe: TypeRepr)               extends GrammarNode // exact type match
    case class GUnion(branches: List[GrammarNode])        extends GrammarNode

    // -----------------------------------------------------------------------
    // Grammar class symbols (looked up once)
    // -----------------------------------------------------------------------

    val primitiveClass = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Primitive")
    val dynamicClass   = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Dynamic")
    val selfClass      = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Self")
    val recordClass    = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Record")
    val sequenceClass  = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Sequence")
    val seqListClass   = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Sequence.List")
    val seqVectorClass = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Sequence.Vector")
    val seqSetClass    = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Sequence.Set")
    val seqArrayClass  = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Sequence.Array")
    val seqChunkClass  = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Sequence.Chunk")
    val mapClass       = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Map")
    val optionalClass  = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Optional")
    val wrappedClass   = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.Wrapped")
    val isTypeClass    = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.IsType")
    val pipeClass      = Symbol.requiredClass("zio.blocks.schema.comptime.Allows.|")

    // Inverse map: Scala type FQN → Primitive.Xxx name, used in describeGrammar
    // to render IsType[scala.Int] as "Primitive.Int" for readable error messages.
    val primitiveNameMap: Map[String, String] = Map(
      "scala.Unit"               -> "Unit",
      "scala.Boolean"            -> "Boolean",
      "scala.Byte"               -> "Byte",
      "scala.Short"              -> "Short",
      "scala.Int"                -> "Int",
      "scala.Long"               -> "Long",
      "scala.Float"              -> "Float",
      "scala.Double"             -> "Double",
      "scala.Char"               -> "Char",
      "java.lang.String"         -> "String",
      "scala.math.BigInt"        -> "BigInt",
      "scala.math.BigDecimal"    -> "BigDecimal",
      "java.util.UUID"           -> "UUID",
      "java.util.Currency"       -> "Currency",
      "java.time.DayOfWeek"      -> "DayOfWeek",
      "java.time.Duration"       -> "Duration",
      "java.time.Instant"        -> "Instant",
      "java.time.LocalDate"      -> "LocalDate",
      "java.time.LocalDateTime"  -> "LocalDateTime",
      "java.time.LocalTime"      -> "LocalTime",
      "java.time.Month"          -> "Month",
      "java.time.MonthDay"       -> "MonthDay",
      "java.time.OffsetDateTime" -> "OffsetDateTime",
      "java.time.OffsetTime"     -> "OffsetTime",
      "java.time.Period"         -> "Period",
      "java.time.Year"           -> "Year",
      "java.time.YearMonth"      -> "YearMonth",
      "java.time.ZoneId"         -> "ZoneId",
      "java.time.ZoneOffset"     -> "ZoneOffset",
      "java.time.ZonedDateTime"  -> "ZonedDateTime"
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
        else if (sym == sequenceClass) GSequence(decomposeGrammar(args(0)))
        else if (sym == seqListClass) GSeqList(decomposeGrammar(args(0)))
        else if (sym == seqVectorClass) GSeqVector(decomposeGrammar(args(0)))
        else if (sym == seqSetClass) GSeqSet(decomposeGrammar(args(0)))
        else if (sym == seqArrayClass) GSeqArray(decomposeGrammar(args(0)))
        else if (sym == seqChunkClass) GSeqChunk(decomposeGrammar(args(0)))
        else if (sym == mapClass) GMap(decomposeGrammar(args(0)), decomposeGrammar(args(1)))
        else if (sym == optionalClass) GOptional(decomposeGrammar(args(0)))
        else if (sym == wrappedClass) GWrapped(decomposeGrammar(args(0)))
        else if (sym == isTypeClass) GIsType(args(0).dealias)
        else
          report.errorAndAbort(
            AllowsErrorMessages.renderUnknownGrammarNode(tpe.show, color),
            Position.ofMacroExpansion
          )
      case other =>
        val sym = other.typeSymbol
        if (sym == primitiveClass) GPrimitive
        else if (sym == dynamicClass) GDynamic
        else if (sym == selfClass) GSelf
        else
          report.errorAndAbort(
            AllowsErrorMessages.renderUnknownGrammarNode(tpe.show, color),
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

    val chunkClass = Symbol.requiredClass("zio.blocks.chunk.Chunk")

    def isOption(tpe: TypeRepr): Boolean  = tpe <:< TypeRepr.of[Option[?]]
    def isDynamic(tpe: TypeRepr): Boolean = tpe <:< dynamicValueTpe
    def isMapType(tpe: TypeRepr): Boolean = tpe <:< scalaMapWild
    def isSeqType(tpe: TypeRepr): Boolean =
      (tpe <:< iterableWild || tpe <:< iteratorWild || tpe <:< arrayWild) &&
        !isOption(tpe) && !isMapType(tpe)

    val listWild   = Symbol.requiredClass("scala.collection.immutable.List").typeRef.appliedTo(wildcard)
    val vectorWild = Symbol.requiredClass("scala.collection.immutable.Vector").typeRef.appliedTo(wildcard)
    val setWild    = Symbol.requiredClass("scala.collection.immutable.Set").typeRef.appliedTo(wildcard)
    val chunkWild  = chunkClass.typeRef.appliedTo(wildcard)

    def isListType(tpe: TypeRepr): Boolean   = tpe <:< listWild
    def isVectorType(tpe: TypeRepr): Boolean = tpe <:< vectorWild
    def isSetType(tpe: TypeRepr): Boolean    = tpe <:< setWild
    def isArrayType(tpe: TypeRepr): Boolean  = tpe <:< arrayWild
    def isChunkType(tpe: TypeRepr): Boolean  = tpe <:< chunkWild

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
      val seen  = scala.collection.mutable.HashSet.empty[TypeRepr]
      val types = scala.collection.mutable.ListBuffer.empty[TypeRepr]

      def loop(t: TypeRepr): Unit = t.dealias match {
        case OrType(l, r) =>
          loop(l)
          loop(r)
        case dealiased => if (seen.add(dealiased)) types += dealiased
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
      errors += AllowsErrorMessages.renderShapeViolation(pathStr, found, required, hint, color)
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
        val cycle = (seen.map(_.name).toList :+ tpeSym.name :+ seen.head.name)
        errors += AllowsErrorMessages.renderMutualRecursion(tpeSym.name, cycle, color)
        return
      }

      if (isAlready && (dt =:= rootTpe.dealias)) {
        grammar match {
          case GSelf =>
            return // self-recursion via Self: accepted
          case GRecord(_) =>
            return // root type satisfies its own Record grammar by assumption
          case GUnion(_) =>
            // Probe each branch without the root in seen so Self can re-add it
            checkAgainstUnion(tpe, grammar, path, seen - tpeSym, rootTpe)
            return
          case other =>
            err(path, describeType(tpe), describeGrammar(other))
            return
        }
      }

      // Auto-unwrap: if the type is a user-defined sealed trait / enum (not a
      // stdlib collection, Option, or Java/Scala stdlib type), recursively check
      // all cases against the same grammar. This makes Variant unnecessary.
      // Java enums (DayOfWeek, Month, etc.) are sealed in Scala but are primitives —
      // they must NOT be auto-unwrapped.
      val isJavaOrStdlib =
        tpeSym.flags.is(Flags.JavaDefined) ||
          dt.typeSymbol.fullName.startsWith("java.") ||
          dt.typeSymbol.fullName.startsWith("javax.") ||
          dt.typeSymbol.fullName.startsWith("scala.collection.")
      if ((isSealed(dt) || isOrType(dt)) && !isOption(dt) && !isSeqType(dt) && !isMapType(dt) && !isJavaOrStdlib) {
        val cases = if (isOrType(dt)) allOrTypes(dt) else casesOf(dt)
        cases.foreach { caseTpe =>
          check(caseTpe, grammar, caseTpe.typeSymbol.name :: path, seen + tpeSym, rootTpe)
        }
        return
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

        case GSequence(inner) =>
          if (isSeqType(dt)) {
            val elemTpe = typeArgs(dt).headOption.getOrElse(defn.AnyClass.typeRef)
            // Don't add collection class to `seen` — stdlib collections can appear nested
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence[...]")
          }

        case GSeqList(inner) =>
          if (isListType(dt)) {
            val elemTpe = typeArgs(dt).headOption.getOrElse(defn.AnyClass.typeRef)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.List[...]")
          }

        case GSeqVector(inner) =>
          if (isVectorType(dt)) {
            val elemTpe = typeArgs(dt).headOption.getOrElse(defn.AnyClass.typeRef)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.Vector[...]")
          }

        case GSeqSet(inner) =>
          if (isSetType(dt)) {
            val elemTpe = typeArgs(dt).headOption.getOrElse(defn.AnyClass.typeRef)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.Set[...]")
          }

        case GSeqArray(inner) =>
          if (isArrayType(dt)) {
            val elemTpe = typeArgs(dt).headOption.getOrElse(defn.AnyClass.typeRef)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.Array[...]")
          }

        case GSeqChunk(inner) =>
          if (isChunkType(dt)) {
            val elemTpe = typeArgs(dt).headOption.getOrElse(defn.AnyClass.typeRef)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.Chunk[...]")
          }

        case GIsType(targetTpe) =>
          if (!(dt =:= targetTpe))
            err(path, describeType(tpe), describeGrammar(GIsType(targetTpe)))

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
      else if (isSealed(dt) || isOrType(dt)) s"SealedTrait(${dt.typeSymbol.name})"
      else if (isOption(dt)) s"Optional[${typeArgs(dt).headOption.map(_.show).getOrElse("?")}]"
      else if (isSeqType(dt)) s"Sequence[${typeArgs(dt).headOption.map(_.show).getOrElse("?")}]"
      else if (isMapType(dt)) {
        val args = typeArgs(dt)
        s"Map[${args.headOption.map(_.show).getOrElse("?")}, ${args.lastOption.map(_.show).getOrElse("?")}]"
      } else if (isWrapper(dt)) s"Wrapped[${tpe.show}]"
      else dt.show
    }

    def describeGrammar(g: GrammarNode): String = g match {
      case GPrimitive         => "Primitive"
      case GDynamic           => "Dynamic"
      case GSelf              => "Self"
      case GRecord(inner)     => s"Record[${describeGrammar(inner)}]"
      case GSequence(inner)   => s"Sequence[${describeGrammar(inner)}]"
      case GSeqList(inner)    => s"Sequence.List[${describeGrammar(inner)}]"
      case GSeqVector(inner)  => s"Sequence.Vector[${describeGrammar(inner)}]"
      case GSeqSet(inner)     => s"Sequence.Set[${describeGrammar(inner)}]"
      case GSeqArray(inner)   => s"Sequence.Array[${describeGrammar(inner)}]"
      case GSeqChunk(inner)   => s"Sequence.Chunk[${describeGrammar(inner)}]"
      case GIsType(targetTpe) =>
        primitiveNameMap
          .get(targetTpe.typeSymbol.fullName)
          .fold(s"IsType[${targetTpe.show}]")(n => s"Primitive.$n")
      case GMap(k, v)       => s"Map[${describeGrammar(k)}, ${describeGrammar(v)}]"
      case GOptional(inner) => s"Optional[${describeGrammar(inner)}]"
      case GWrapped(inner)  => s"Wrapped[${describeGrammar(inner)}]"
      case GUnion(branches) => branches.map(describeGrammar).mkString(" | ")
    }

    // -----------------------------------------------------------------------
    // Run the check
    // -----------------------------------------------------------------------

    val rootTpe = TypeRepr.of[A]
    check(rootTpe, rootGrammar, List(rootTpe.typeSymbol.name), Set.empty, rootTpe)

    if (errors.nonEmpty) {
      report.errorAndAbort(
        AllowsErrorMessages.renderMultipleViolations(errors.toList),
        Position.ofMacroExpansion
      )
    }

    '{ Allows.instance.asInstanceOf[Allows[A, S]] }
  }
}
