package zio.blocks.schema.comptime

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.reflect.NameTransformer
import zio.blocks.schema.comptime.internal.AllowsErrorMessages

trait AllowsCompanionVersionSpecific {
  implicit def derived[S <: Allows.Structural, A]: Allows[A, S] = macro AllowsMacroImpl.deriveAllows[S, A]
}

private[comptime] object AllowsMacroImpl {

  def deriveAllows[S <: Allows.Structural, A](
    c: whitebox.Context
  ): c.Expr[Allows[A, S]] = {
    import c.universe._

    // -----------------------------------------------------------------------
    // Grammar node ADT (internal, compile-time only)
    // -----------------------------------------------------------------------

    val color = AllowsErrorMessages.Colors.shouldUseColor

    sealed trait GrammarNode
    case object GPrimitive                    extends GrammarNode // any primitive (catch-all)
    case object GDynamic                      extends GrammarNode
    case object GSelf                         extends GrammarNode
    case class GRecord(inner: GrammarNode)    extends GrammarNode
    case class GSequence(inner: GrammarNode)  extends GrammarNode // any sequence
    case class GSeqList(inner: GrammarNode)   extends GrammarNode // List only
    case class GSeqVector(inner: GrammarNode) extends GrammarNode // Vector only
    case class GSeqSet(inner: GrammarNode)    extends GrammarNode // Set only
    case class GSeqArray(inner: GrammarNode)  extends GrammarNode // Array only
    case class GSeqChunk(inner: GrammarNode)  extends GrammarNode // Chunk only

    case class GMap(key: GrammarNode, value: GrammarNode) extends GrammarNode
    case class GOptional(inner: GrammarNode)              extends GrammarNode
    case class GWrapped(inner: GrammarNode)               extends GrammarNode
    case class GIsType(targetType: Type)                  extends GrammarNode // exact type match (=:=)
    case class GUnion(branches: List[GrammarNode])        extends GrammarNode

    // In Scala 2 whitebox macros, weakTypeOf[S] and weakTypeOf[A] are abstract type
    // variables. Extract the concrete types from the macro application's return type.
    //
    // c.macroApplication.tpe is the primary source: it holds the return type of the
    // macro call as determined by the typer before expansion.  When the macro is
    // used as an implicit and Scala 2's typer has already committed the expected
    // type (which is the normal case), this gives us Allows[ConcreteA, ConcreteS]
    // with both type arguments fully resolved.
    //
    // In rare cases c.macroApplication.tpe can be null (e.g. some incremental
    // compilation states) or its type arguments can contain unresolved type variables
    // (isAbstract type parameters or existential skolems).  The fallback reads
    // c.enclosingImplicits: when the macro is triggered as part of an implicit
    // search, c.enclosingImplicits.head.pt is the "point type" — the expected
    // type the search is trying to satisfy — which is always concrete.
    val allowsTypeCon = typeOf[Allows[_, _]].typeConstructor

    // Returns Some((A, S)) only when both type arguments are fully concrete —
    // i.e. neither is an unresolved type parameter (typeSymbol.isParameter is true
    // for type-parameter symbols such as the A and S in derived[S, A], but false
    // for concrete types including abstract classes used as grammar nodes).
    def isConcrete(t: Type): Boolean =
      !t.typeSymbol.isParameter

    def typeArgsFromPt(pt: Type): Option[(Type, Type)] = {
      val dealiased = pt.dealias
      if (dealiased.typeConstructor =:= allowsTypeCon && dealiased.typeArgs.length == 2) {
        val a = dealiased.typeArgs.head
        val s = dealiased.typeArgs.last
        if (isConcrete(a) && isConcrete(s)) Some((a, s)) else None
      } else None
    }

    val (rootTpe0, sTpe): (Type, Type) = {
      val fromApp: Option[(Type, Type)] =
        Option(c.macroApplication.tpe).flatMap(t => typeArgsFromPt(t))
      val fromImplicits: Option[(Type, Type)] =
        c.enclosingImplicits.headOption.flatMap(ic => typeArgsFromPt(ic.pt))
      fromApp
        .orElse(fromImplicits)
        .getOrElse(
          c.abort(
            c.enclosingPosition,
            "Allows macro: could not determine concrete type arguments A and S. " +
              "Make sure the full Allows[A, S] type is fully inferred at the call site."
          )
        )
    }

    val primitiveBase = typeOf[Allows.Primitive]
    val dynamicBase   = typeOf[Allows.Dynamic]
    val selfBase      = typeOf[Allows.Self]

    // Inverse map: Scala type FQN → Primitive.Xxx name, used in describeGrammar
    // to render GIsType(scala.Int) as "Primitive.Int" for readable error messages.
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
    val recordBase    = typeOf[Allows.Record[_]].typeConstructor
    val sequenceBase  = typeOf[Allows.Sequence[_]].typeConstructor
    val seqListBase   = typeOf[Allows.Sequence.List[_]].typeConstructor
    val seqVectorBase = typeOf[Allows.Sequence.Vector[_]].typeConstructor
    val seqSetBase    = typeOf[Allows.Sequence.Set[_]].typeConstructor
    val seqArrayBase  = typeOf[Allows.Sequence.Array[_]].typeConstructor
    val seqChunkBase  = typeOf[Allows.Sequence.Chunk[_]].typeConstructor
    val mapBase       = typeOf[Allows.Map[_, _]].typeConstructor
    val optionalBase  = typeOf[Allows.Optional[_]].typeConstructor
    val wrappedBase   = typeOf[Allows.Wrapped[_]].typeConstructor
    val isTypeBase    = typeOf[Allows.IsType[_]].typeConstructor
    val pipeBase      = typeOf[Allows.`|`[_, _]].typeConstructor

    def typeConstructorOf(tpe: Type): Type = tpe.dealias match {
      case TypeRef(_, sym, _) => sym.asType.toType.typeConstructor
      case _                  => tpe.typeConstructor
    }

    def decomposeGrammar(tpe: Type): GrammarNode = {
      val dt   = tpe.dealias
      val args = dt.typeArgs
      if (dt =:= primitiveBase) GPrimitive
      else if (dt =:= dynamicBase) GDynamic
      else if (dt =:= selfBase) GSelf
      else {
        val tc = typeConstructorOf(dt)
        if (tc =:= recordBase) GRecord(decomposeGrammar(args.head))
        else if (tc =:= seqListBase) GSeqList(decomposeGrammar(args.head))
        else if (tc =:= seqVectorBase) GSeqVector(decomposeGrammar(args.head))
        else if (tc =:= seqSetBase) GSeqSet(decomposeGrammar(args.head))
        else if (tc =:= seqArrayBase) GSeqArray(decomposeGrammar(args.head))
        else if (tc =:= seqChunkBase) GSeqChunk(decomposeGrammar(args.head))
        else if (tc =:= sequenceBase) GSequence(decomposeGrammar(args.head))
        else if (tc =:= mapBase) GMap(decomposeGrammar(args.head), decomposeGrammar(args.last))
        else if (tc =:= optionalBase) GOptional(decomposeGrammar(args.head))
        else if (tc =:= wrappedBase) GWrapped(decomposeGrammar(args.head))
        else if (tc =:= isTypeBase) GIsType(args.head.dealias)
        else if (tc =:= pipeBase) GUnion(List(decomposeGrammar(args.head), decomposeGrammar(args.last)))
        else
          c.abort(
            c.enclosingPosition,
            AllowsErrorMessages.renderUnknownGrammarNode(tpe.toString, color)
          )
      }
    }

    val rootGrammar: GrammarNode = decomposeGrammar(sTpe)

    // -----------------------------------------------------------------------
    // Type inspection helpers
    // -----------------------------------------------------------------------

    val dynamicValueTpe = typeOf[zio.blocks.schema.DynamicValue]

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

    def isPrimitive(tpe: Type): Boolean = {
      val dt = tpe.dealias
      dt =:= definitions.BooleanTpe ||
      dt =:= definitions.ByteTpe ||
      dt =:= definitions.ShortTpe ||
      dt =:= definitions.IntTpe ||
      dt =:= definitions.LongTpe ||
      dt =:= definitions.FloatTpe ||
      dt =:= definitions.DoubleTpe ||
      dt =:= definitions.CharTpe ||
      dt =:= definitions.UnitTpe ||
      dt <:< typeOf[String] ||
      dt <:< typeOf[BigInt] ||
      dt <:< typeOf[BigDecimal] ||
      dt <:< typeOf[java.util.UUID] ||
      dt <:< typeOf[java.util.Currency] ||
      javaTimePrimitiveNames.contains(dt.typeSymbol.fullName)
    }

    def isDynamic(tpe: Type): Boolean = tpe.dealias <:< dynamicValueTpe
    def isOption(tpe: Type): Boolean  = tpe.dealias <:< typeOf[Option[_]]
    def isMap(tpe: Type): Boolean     = tpe.dealias <:< typeOf[scala.collection.Map[_, _]]
    def isSeq(tpe: Type): Boolean     =
      (tpe.dealias <:< typeOf[Iterable[_]] || tpe.dealias <:< typeOf[Iterator[_]] ||
        tpe.dealias <:< typeOf[Array[_]]) && !isOption(tpe) && !isMap(tpe)

    def isListType(tpe: Type): Boolean   = tpe.dealias <:< typeOf[scala.collection.immutable.List[_]]
    def isVectorType(tpe: Type): Boolean = tpe.dealias <:< typeOf[scala.collection.immutable.Vector[_]]
    def isSetType(tpe: Type): Boolean    = tpe.dealias <:< typeOf[scala.collection.immutable.Set[_]]
    def isArrayType(tpe: Type): Boolean  = tpe.dealias <:< typeOf[Array[_]]
    def isChunkType(tpe: Type): Boolean  = tpe.dealias <:< typeOf[zio.blocks.chunk.Chunk[_]]

    def isSealed(tpe: Type): Boolean = {
      val sym = tpe.dealias.typeSymbol
      sym.isClass && { val cs = sym.asClass; cs.isSealed && (cs.isAbstract || cs.isTrait) }
    }

    def isProduct(tpe: Type): Boolean =
      tpe.dealias.typeSymbol.isClass &&
        !tpe.dealias.typeSymbol.isAbstract &&
        !tpe.dealias.typeSymbol.isJava

    def isModule(tpe: Type): Boolean = tpe.dealias.typeSymbol.isModuleClass

    def isWrapper(tpe: Type): Boolean = tpe.dealias match {
      case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
        compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
      case _ => false
    }

    def unwrapType(tpe: Type): Type = tpe.dealias match {
      case TypeRef(compTpe, _, _) =>
        compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
          case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
          case None      => c.abort(c.enclosingPosition, s"Cannot unwrap wrapper type: $tpe")
        }
      case _ => c.abort(c.enclosingPosition, s"Cannot unwrap wrapper type: $tpe")
    }

    def fieldTypes(tpe: Type): List[(String, Type)] = {
      val sym = tpe.dealias.typeSymbol
      if (!sym.isClass) return Nil
      val primary = sym.asClass.primaryConstructor
      if (primary == NoSymbol) return Nil
      primary.asMethod.paramLists.flatten.map { param =>
        val pTpe = param.typeSignatureIn(tpe).dealias
        (scala.reflect.NameTransformer.decode(param.name.toString), pTpe)
      }
    }

    def casesOf(tpe: Type): List[Type] = {
      implicit val positionOrdering: Ordering[Symbol] = (x: Symbol, y: Symbol) => {
        val xPos  = x.pos; val yPos                      = y.pos
        val xFile = xPos.source.file.absolute; val yFile = yPos.source.file.absolute
        var diff  = xFile.path.compareTo(yFile.path)
        if (diff == 0) diff = xFile.name.compareTo(yFile.name)
        if (diff == 0) diff = xPos.line.compareTo(yPos.line)
        if (diff == 0) diff = xPos.column.compareTo(yPos.column)
        if (diff == 0) diff = NameTransformer.decode(x.fullName).compareTo(NameTransformer.decode(y.fullName))
        diff
      }
      val tpeClass    = tpe.typeSymbol.asClass
      val tpeTypeArgs = tpe.typeArgs.map(_.dealias)
      val subTypes    = scala.collection.mutable.ListBuffer.empty[Type]
      tpeClass.knownDirectSubclasses.toArray.sortInPlace().foreach { sym =>
        val cs = sym.asClass
        var ct = if (cs.isModuleClass) cs.module.typeSignature else cs.toType
        if (tpeTypeArgs.nonEmpty) {
          val typeParams = cs.typeParams
          if (typeParams.nonEmpty) {
            val childBaseTypeArgs = ct.baseType(tpeClass).typeArgs.map(_.dealias)
            var childParamsToArgs = Map.empty[String, Type]
            childBaseTypeArgs.zip(tpeTypeArgs).foreach { case (ba, pa) =>
              ba match {
                case TypeRef(_, s, Nil) if s.isType =>
                  childParamsToArgs = childParamsToArgs.updated(s.name.toString, pa)
                case _ =>
              }
            }
            ct = ct.substituteTypes(
              typeParams,
              typeParams.map(tp => childParamsToArgs.getOrElse(tp.name.toString, tp.asType.toType))
            )
          }
        }
        subTypes += ct
      }
      subTypes.toList
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
    // Core check
    // -----------------------------------------------------------------------

    def check(
      tpe: Type,
      grammar: GrammarNode,
      path: List[String],
      seen: Set[Symbol],
      rootTpe: Type
    ): Unit = {
      val dt        = tpe.dealias
      val tpeSym    = dt.typeSymbol
      val isAlready = seen.contains(tpeSym)

      if (isAlready && !(dt =:= rootTpe.dealias)) {
        val cycle = (seen
          .map(_.name.decodedName.toString)
          .toList :+ tpeSym.name.decodedName.toString :+ seen.head.name.decodedName.toString)
        errors += AllowsErrorMessages.renderMutualRecursion(tpeSym.name.decodedName.toString, cycle, color)
        return
      }

      if (isAlready && (dt =:= rootTpe.dealias)) {
        grammar match {
          case GSelf =>
            return
          case GRecord(_) =>
            return
          case GUnion(_) =>
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
      // Java enums (DayOfWeek, Month, etc.) are sealed in Scala but are primitives.
      val isJavaOrStdlib =
        dt.typeSymbol.isJava ||
          dt.typeSymbol.fullName.startsWith("java.") ||
          dt.typeSymbol.fullName.startsWith("javax.") ||
          dt.typeSymbol.fullName.startsWith("scala.collection.")
      if (isSealed(dt) && !isOption(dt) && !isSeq(dt) && !isMap(dt) && !isJavaOrStdlib) {
        casesOf(dt).foreach { caseTpe =>
          val caseName = caseTpe.typeSymbol.name.decodedName.toString
          check(caseTpe, grammar, caseName :: path, seen + tpeSym, rootTpe)
        }
        return
      }

      grammar match {
        case GSelf =>
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
            // zero-field — vacuous
          } else if (isProduct(dt)) {
            fieldTypes(dt).foreach { case (name, fTpe) =>
              checkAgainstUnion(fTpe, inner, name :: path, seen + tpeSym, rootTpe)
            }
          } else {
            err(path, describeType(tpe), "Record[...]")
          }

        case GSequence(inner) =>
          if (isSeq(dt)) {
            val elemTpe = dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)
            // Don't add collection class to `seen` — stdlib collections can appear nested
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence[...]")
          }

        case GSeqList(inner) =>
          if (isListType(dt)) {
            val elemTpe = dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.List[...]")
          }

        case GSeqVector(inner) =>
          if (isVectorType(dt)) {
            val elemTpe = dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.Vector[...]")
          }

        case GSeqSet(inner) =>
          if (isSetType(dt)) {
            val elemTpe = dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.Set[...]")
          }

        case GSeqArray(inner) =>
          if (isArrayType(dt)) {
            val elemTpe = dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.Array[...]")
          }

        case GSeqChunk(inner) =>
          if (isChunkType(dt)) {
            val elemTpe = dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)
            checkAgainstUnion(elemTpe, inner, "<element>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Sequence.Chunk[...]")
          }

        case GIsType(targetType) =>
          if (!(dt =:= targetType))
            err(path, describeType(tpe), describeGrammar(GIsType(targetType)))

        case GMap(keyG, valG) =>
          if (isMap(dt)) {
            val args   = dt.typeArgs
            val keyTpe = args.headOption.getOrElse(definitions.AnyTpe)
            val valTpe = args.lastOption.getOrElse(definitions.AnyTpe)
            checkAgainstUnion(keyTpe, keyG, "<key>" :: path, seen, rootTpe)
            checkAgainstUnion(valTpe, valG, "<value>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Map[...]")
          }

        case GOptional(inner) =>
          if (isOption(dt)) {
            val innerTpe = dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)
            // Don't add Option to `seen` — it's a stdlib type, not user recursion
            checkAgainstUnion(innerTpe, inner, "<inner>" :: path, seen, rootTpe)
          } else {
            err(path, describeType(tpe), "Optional[...]")
          }

        case GWrapped(inner) =>
          if (isWrapper(dt)) {
            val unwrapped = unwrapType(dt)
            checkAgainstUnion(unwrapped, inner, "<wrapped>" :: path, seen + tpeSym, rootTpe)
          } else {
            err(path, describeType(tpe), "Wrapped[...]")
          }

        case GUnion(branches) =>
          checkAgainstUnion(tpe, GUnion(branches), path, seen, rootTpe)
      }
    }

    def checkAgainstUnion(
      tpe: Type,
      grammar: GrammarNode,
      path: List[String],
      seen: Set[Symbol],
      rootTpe: Type
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
            s"Type '$tpe' does not match any allowed shape"
          )
        }
      case other =>
        check(tpe, other, path, seen, rootTpe)
    }

    def checkCollecting(
      tpe: Type,
      grammar: GrammarNode,
      path: List[String],
      seen: Set[Symbol],
      rootTpe: Type,
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
    // Helpers
    // -----------------------------------------------------------------------

    def describeType(tpe: Type): String = {
      val dt = tpe.dealias
      if (isPrimitive(dt)) s"Primitive(${dt})"
      else if (isDynamic(dt)) "Dynamic"
      else if (isModule(dt)) s"Record(case object ${dt.typeSymbol.name})"
      else if (isProduct(dt)) s"Record(${dt.typeSymbol.name})"
      else if (isSealed(dt)) s"SealedTrait(${dt.typeSymbol.name})"
      else if (isOption(dt)) s"Optional[${dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)}]"
      else if (isSeq(dt)) s"Sequence[${dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)}]"
      else if (isMap(dt))
        s"Map[${dt.typeArgs.headOption.getOrElse(definitions.AnyTpe)}, " +
          s"${dt.typeArgs.lastOption.getOrElse(definitions.AnyTpe)}]"
      else if (isWrapper(dt)) s"Wrapped[$tpe]"
      else dt.toString
    }

    def describeGrammar(g: GrammarNode): String = g match {
      case GPrimitive          => "Primitive"
      case GDynamic            => "Dynamic"
      case GSelf               => "Self"
      case GRecord(inner)      => s"Record[${describeGrammar(inner)}]"
      case GSequence(inner)    => s"Sequence[${describeGrammar(inner)}]"
      case GSeqList(inner)     => s"Sequence.List[${describeGrammar(inner)}]"
      case GSeqVector(inner)   => s"Sequence.Vector[${describeGrammar(inner)}]"
      case GSeqSet(inner)      => s"Sequence.Set[${describeGrammar(inner)}]"
      case GSeqArray(inner)    => s"Sequence.Array[${describeGrammar(inner)}]"
      case GSeqChunk(inner)    => s"Sequence.Chunk[${describeGrammar(inner)}]"
      case GIsType(targetType) =>
        primitiveNameMap
          .get(targetType.typeSymbol.fullName)
          .fold(s"IsType[${targetType.toString}]")(n => s"Primitive.$n")
      case GMap(k, v)       => s"Map[${describeGrammar(k)}, ${describeGrammar(v)}]"
      case GOptional(inner) => s"Optional[${describeGrammar(inner)}]"
      case GWrapped(inner)  => s"Wrapped[${describeGrammar(inner)}]"
      case GUnion(branches) => branches.map(describeGrammar).mkString(" | ")
    }

    // -----------------------------------------------------------------------
    // Run the check
    // -----------------------------------------------------------------------

    val rootTpe = rootTpe0
    check(rootTpe, rootGrammar, List(rootTpe.typeSymbol.name.decodedName.toString), Set.empty, rootTpe)

    if (errors.nonEmpty)
      c.abort(
        c.enclosingPosition,
        AllowsErrorMessages.renderMultipleViolations(errors.toList)
      )

    c.Expr[Allows[A, S]](
      q"_root_.zio.blocks.schema.comptime.Allows.instance.asInstanceOf[_root_.zio.blocks.schema.comptime.Allows[${rootTpe0}, ${sTpe}]]"
    )
  }
}
