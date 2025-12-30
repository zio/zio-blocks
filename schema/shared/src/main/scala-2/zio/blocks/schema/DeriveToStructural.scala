package zio.blocks.schema

import scala.reflect.macros.whitebox

object DeriveToStructural {
  def derivedImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToStructural[A]] = {
    import c.universe._

    def fail(msg: String): Nothing =
      c.abort(c.enclosingPosition, msg)

    val tpe    = weakTypeOf[A]
    val symbol = tpe.typeSymbol

    // Validate: sealed traits/classes are not supported in Scala 2
    if (symbol.isClass && symbol.asClass.isSealed) {
      fail(
        s"""Cannot generate structural type for sum types in Scala 2 ($tpe).
           |Structural representation of sum types requires union types,
           |which are only available in Scala 3.
           |Consider upgrading to Scala 3 or using a different approach.""".stripMargin
      )
    }

    // Validate: must be a case class
    if (!symbol.isClass || !symbol.asClass.isCaseClass) {
      fail(s"ToStructural derivation only supports case classes, found: $tpe")
    }

    // Type categorization ADT
    sealed trait TypeCategory
    object TypeCategory {
      case object PrimitiveLike                                    extends TypeCategory
      case class OptionType(element: Type)                         extends TypeCategory
      case class ListType(element: Type)                           extends TypeCategory
      case class VectorType(element: Type)                         extends TypeCategory
      case class SeqType(element: Type)                            extends TypeCategory
      case class SetType(element: Type)                            extends TypeCategory
      case class MapType(key: Type, value: Type)                   extends TypeCategory
      case class EitherType(left: Type, right: Type)               extends TypeCategory
      case class TupleType(elements: List[Type])                   extends TypeCategory
      case class CaseClassType(fields: List[(MethodSymbol, Type)]) extends TypeCategory
      case object Unknown                                          extends TypeCategory
    }

    // Single categorization function
    def categorize(t: Type): TypeCategory = {
      val dealt = t.dealias

      // Check primitives first
      if (
        dealt <:< definitions.BooleanTpe ||
        dealt <:< definitions.ByteTpe ||
        dealt <:< definitions.ShortTpe ||
        dealt <:< definitions.IntTpe ||
        dealt <:< definitions.LongTpe ||
        dealt <:< definitions.FloatTpe ||
        dealt <:< definitions.DoubleTpe ||
        dealt <:< definitions.CharTpe ||
        dealt <:< definitions.UnitTpe ||
        dealt =:= typeOf[String] ||
        dealt =:= typeOf[BigDecimal] ||
        dealt =:= typeOf[BigInt] ||
        dealt =:= typeOf[java.util.UUID] ||
        dealt =:= typeOf[java.util.Currency] ||
        dealt.typeSymbol.fullName.startsWith("java.time.")
      ) {
        return TypeCategory.PrimitiveLike
      }

      // Check containers and structural types
      if (dealt.typeConstructor =:= typeOf[Option[Any]].typeConstructor) {
        TypeCategory.OptionType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[List[Any]].typeConstructor) {
        TypeCategory.ListType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[Vector[Any]].typeConstructor) {
        TypeCategory.VectorType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[Seq[Any]].typeConstructor) {
        TypeCategory.SeqType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[Set[Any]].typeConstructor) {
        TypeCategory.SetType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[Map[Any, Any]].typeConstructor) {
        TypeCategory.MapType(dealt.typeArgs(0), dealt.typeArgs(1))
      } else if (dealt.typeConstructor =:= typeOf[Either[Any, Any]].typeConstructor) {
        TypeCategory.EitherType(dealt.typeArgs(0), dealt.typeArgs(1))
      } else if (dealt.typeSymbol.fullName.startsWith("scala.Tuple")) {
        TypeCategory.TupleType(dealt.typeArgs)
      } else {
        val sym = dealt.typeSymbol
        if (sym.isClass && sym.asClass.isCaseClass && !sym.asClass.isSealed) {
          val fields = dealt.decls.collect {
            case m: MethodSymbol if m.isCaseAccessor => m
          }.toList.map(f => (f, f.returnType.asSeenFrom(dealt, sym)))
          TypeCategory.CaseClassType(fields)
        } else {
          TypeCategory.Unknown
        }
      }
    }

    // Recursion detection
    def failRecursion(loopType: Type, stack: List[Type]): Nothing = {
      val cycle = stack.takeWhile(t => !(t =:= loopType)) :+ loopType

      val structuralTypesInCycle = cycle.filter { t =>
        categorize(t) match {
          case TypeCategory.CaseClassType(_) => true
          case _                             => false
        }
      }.map(_.typeSymbol.name.decodedName.toString).distinct

      if (structuralTypesInCycle.size > 1) {
        fail(
          s"Cannot generate structural type: mutually recursive types detected: ${structuralTypesInCycle.mkString(", ")}. " +
            "Structural types cannot represent cyclic dependencies."
        )
      } else {
        fail(
          s"Cannot generate structural type: recursive type detected: ${loopType.typeSymbol.name.decodedName.toString}. " +
            "Structural types cannot represent recursive structures."
        )
      }
    }

    def checkRecursive(t: Type, stack: List[Type]): Unit = {
      val dealt = t.dealias

      if (stack.exists(_ =:= dealt)) {
        failRecursion(dealt, stack)
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike =>
          ()

        case TypeCategory.Unknown =>
          fail(
            s"""Cannot generate structural type for unsupported type: ${dealt.typeSymbol.name.decodedName.toString}.
               |ToStructural only supports:
               |  - Primitive types (Int, String, Boolean, etc.)
               |  - Case classes
               |  - Collections (List, Vector, Set, Seq, Option, Map)
               |  - Tuples
               |The type '${dealt.typeSymbol.fullName}' is not supported.
               |If this is a regular class, consider converting it to a case class.""".stripMargin
          )

        case TypeCategory.OptionType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.ListType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.VectorType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.SeqType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.SetType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.MapType(key, value) =>
          checkRecursive(key, dealt :: stack)
          checkRecursive(value, dealt :: stack)

        case TypeCategory.EitherType(_, _) =>
          fail(
            s"""Cannot generate structural type for Either in Scala 2.
               |Either structural representation requires union types,
               |which are only available in Scala 3.
               |Consider upgrading to Scala 3 or using a different approach.""".stripMargin
          )

        case TypeCategory.TupleType(elements) =>
          elements.foreach(elem => checkRecursive(elem, dealt :: stack))

        case TypeCategory.CaseClassType(fields) =>
          fields.foreach { case (_, fieldTpe) =>
            checkRecursive(fieldTpe, dealt :: stack)
          }
      }
    }

    // Recursion Validation
    checkRecursive(tpe, Nil)

    // Get case class fields
    val fields: List[(MethodSymbol, Type)] = tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }.toList.map(f => (f, f.returnType.asSeenFrom(tpe, symbol)))

    // Generate converter expression for a value of given type
    def convertExpr(expr: Tree, t: Type, seen: Set[Type]): Tree = {
      val dealt = t.dealias

      if (seen.contains(dealt)) {
        return expr
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike | TypeCategory.Unknown =>
          expr

        case TypeCategory.OptionType(elem) =>
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.ListType(elem) =>
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.VectorType(elem) =>
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.SeqType(elem) =>
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.SetType(elem) =>
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.MapType(key, value) =>
          val kbody = convertExpr(Ident(TermName("k")), key, seen + dealt)
          val vbody = convertExpr(Ident(TermName("v")), value, seen + dealt)
          q"$expr.map { case (k, v) => ($kbody, $vbody) }"

        case TypeCategory.EitherType(_, _) =>
          // Should never reach here - Either is rejected in checkRecursive
          fail("Either types are not supported in Scala 2 structural types")

        case TypeCategory.TupleType(elements) =>
          // Convert tuple to StructuralRecord with _1, _2, etc. fields
          val entries = elements.zipWithIndex.map { case (argTpe, idx) =>
            val nameLit = Literal(Constant(s"_${idx + 1}"))
            val value   = q"$expr.${TermName(s"_${idx + 1}")}"
            val conv    = convertExpr(value, argTpe, seen + dealt)
            q"$nameLit -> $conv"
          }
          q"new _root_.zio.blocks.schema.StructuralRecord(_root_.scala.collection.immutable.Map[String, Any](..$entries))"

        case TypeCategory.CaseClassType(cFields) =>
          // Nested case class: convert to StructuralRecord
          val entries = cFields.map { case (f, fieldTpe) =>
            val nameLit   = Literal(Constant(f.name.decodedName.toString.trim))
            val fieldExpr = q"$expr.${f.name}"
            val conv      = convertExpr(fieldExpr, fieldTpe, seen + dealt)
            q"$nameLit -> $conv"
          }
          q"new _root_.zio.blocks.schema.StructuralRecord(_root_.scala.collection.immutable.Map[String, Any](..$entries))"
      }
    }

    // Generate field entries for the root type
    val fieldEntries = fields.map { case (f, fieldTpe) =>
      val nameLit   = Literal(Constant(f.name.decodedName.toString.trim))
      val fieldExpr = q"value.${f.name}"
      val conv      = convertExpr(fieldExpr, fieldTpe, Set(tpe))
      q"$nameLit -> $conv"
    }

    // Generate the ToStructural instance
    val tree = q"""
      new _root_.zio.blocks.schema.ToStructural[$tpe] {
        type StructuralType = _root_.zio.blocks.schema.StructuralRecord

        def toStructural(value: $tpe): StructuralType = {
          new _root_.zio.blocks.schema.StructuralRecord(
            _root_.scala.collection.immutable.Map[String, Any](..$fieldEntries)
          )
        }

        def structuralSchema(implicit schema: _root_.zio.blocks.schema.Schema[$tpe]): _root_.zio.blocks.schema.Schema[StructuralType] = {
          _root_.zio.blocks.schema.DeriveToStructural.createStructuralSchema[$tpe](schema, this)
        }
      }
    """

    c.Expr[ToStructural[A]](tree)
  }

  /**
   * Runtime helper to create structural schema from nominal schema. This is
   * called from the macro-generated code.
   */
  def createStructuralSchema[A](
    nominalSchema: Schema[A],
    @annotation.unused toStructural: ToStructural[A]
  ): Schema[StructuralRecord] = {
    import zio.blocks.schema.binding._
    import zio.blocks.schema.binding.RegisterOffset._

    val nominalRecord = nominalSchema.reflect.asRecord.getOrElse(
      throw new IllegalArgumentException(s"Expected Record schema, got: ${nominalSchema.reflect}")
    )

    // Field names from nominal schema
    val fieldNames: IndexedSeq[String] = nominalRecord.fields.map(_.name)

    // For each field, transform nested case classes to structural schemas
    // This is needed because nested case classes are converted to StructuralRecord by toStructural
    val fieldSchemas: IndexedSeq[Reflect.Bound[_]] = nominalRecord.fields.map { term =>
      transformNestedToStructural(term.value)
    }

    // Reuse the nominal schema's register layout
    val usedRegs = nominalRecord.constructor.usedRegisters

    // Create Terms for structural record
    val structuralFields: IndexedSeq[Term[Binding, StructuralRecord, _]] =
      fieldNames.zip(fieldSchemas).map { case (name, fieldReflect) =>
        new Term[Binding, StructuralRecord, Any](
          name,
          fieldReflect.asInstanceOf[Reflect.Bound[Any]]
        )
      }

    // Create TypeName for structural type
    val structuralTypeName: TypeName[StructuralRecord] =
      TypeName.structuralFromTypeNames(fieldNames.zip(fieldSchemas.map(_.typeName)))

    // Constructor: Registers -> StructuralRecord
    val structuralConstructor = new Constructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord = {
        val fieldMap = nominalRecord.fields.map { term =>
          val regIdx     = nominalRecord.fieldIndexByName(term.name)
          val reg        = nominalRecord.registers(regIdx)
          val fieldValue = reg.asInstanceOf[Register[Any]].get(in, baseOffset)
          term.name -> fieldValue
        }.toMap
        new StructuralRecord(fieldMap)
      }
    }

    // Deconstructor: StructuralRecord -> Registers
    val structuralDeconstructor = new Deconstructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit =
        nominalRecord.fields.foreach { term =>
          val fieldValue = in.selectDynamic(term.name)
          val regIdx     = nominalRecord.fieldIndexByName(term.name)
          val reg        = nominalRecord.registers(regIdx)
          reg.asInstanceOf[Register[Any]].set(out, baseOffset, fieldValue)
        }
    }

    new Schema[StructuralRecord](
      new Reflect.Record[Binding, StructuralRecord](
        fields = structuralFields,
        typeName = structuralTypeName,
        recordBinding = new Binding.Record[StructuralRecord](
          constructor = structuralConstructor,
          deconstructor = structuralDeconstructor
        )
      )
    )
  }

  // Wrapper types that should NOT be converted to structural schemas
  // These are standard library case classes used as containers
  private val wrapperTypeNames: Set[String] = Set(
    "scala.Some",
    "scala.None",
    "scala.util.Left",
    "scala.util.Right"
  )

  private def isWrapperType(typeName: TypeName[_]): Boolean = {
    val elements = typeName.namespace.elements
    val fullName = (if (elements.isEmpty) "" else elements.mkString(".") + ".") + typeName.name
    wrapperTypeNames.contains(fullName)
  }

  /**
   * Recursively transform a schema to handle StructuralRecord for nested case
   * classes. This is needed because toStructural converts nested case classes
   * to StructuralRecord, so the schema needs to expect StructuralRecord instead
   * of the nominal type.
   */
  private def transformNestedToStructural(reflect: Reflect.Bound[_]): Reflect.Bound[_] = {
    import zio.blocks.schema.binding._

    reflect match {
      case record: Reflect.Record.Bound[_] if record.fields.nonEmpty && !isWrapperType(record.typeName) =>
        // User-defined case class: convert to structural schema
        createNestedStructuralSchema(record)

      case record: Reflect.Record.Bound[_] if record.fields.nonEmpty && isWrapperType(record.typeName) =>
        // Wrapper type (Some, None, Left, Right, Tuple): recursively transform fields but keep wrapper structure
        val transformedFields = record.fields.map { term =>
          val transformedValue = transformNestedToStructural(term.value)
          if (transformedValue eq term.value) {
            term
          } else {
            new Term[Binding, Any, Any](
              name = term.name,
              value = transformedValue.asInstanceOf[Reflect.Bound[Any]],
              doc = term.doc,
              modifiers = term.modifiers
            )
          }
        }
        if (transformedFields.zip(record.fields).forall { case (t, o) => t eq o }) {
          record
        } else {
          new Reflect.Record[Binding, Any](
            fields = transformedFields.asInstanceOf[IndexedSeq[Term[Binding, Any, _]]],
            typeName = record.typeName.asInstanceOf[TypeName[Any]],
            recordBinding = record.recordBinding.asInstanceOf[Binding.Record[Any]]
          ).asInstanceOf[Reflect.Bound[_]]
        }

      case seq: Reflect.Sequence[Binding, _, _] =>
        // Sequence (List, Vector, etc.): transform element schema
        val transformedElement = transformNestedToStructural(seq.element)
        if (transformedElement eq seq.element) {
          seq
        } else {
          new Reflect.Sequence[Binding, Any, Any](
            element = transformedElement.asInstanceOf[Reflect.Bound[Any]],
            typeName = seq.typeName.asInstanceOf[TypeName[Any]],
            seqBinding = seq.seqBinding.asInstanceOf[Binding.Seq[Any, Any]]
          ).asInstanceOf[Reflect.Bound[_]]
        }

      case map: Reflect.Map[Binding, _, _, _] =>
        // Map: transform key and value schemas
        val transformedKey   = transformNestedToStructural(map.key)
        val transformedValue = transformNestedToStructural(map.value)
        if ((transformedKey eq map.key) && (transformedValue eq map.value)) {
          map
        } else {
          new Reflect.Map[Binding, Any, Any, scala.collection.immutable.Map](
            key = transformedKey.asInstanceOf[Reflect.Bound[Any]],
            value = transformedValue.asInstanceOf[Reflect.Bound[Any]],
            typeName = map.typeName.asInstanceOf[TypeName[scala.collection.immutable.Map[Any, Any]]],
            mapBinding = map.mapBinding.asInstanceOf[Binding.Map[scala.collection.immutable.Map, Any, Any]]
          ).asInstanceOf[Reflect.Bound[_]]
        }

      case variant: Reflect.Variant.Bound[_] =>
        // Variant (Option, Either, sealed trait): transform case schemas
        val transformedCases = variant.cases.map { cse =>
          val transformedSchema = transformNestedToStructural(cse.value)
          if (transformedSchema eq cse.value) {
            cse
          } else {
            new Term[Binding, Any, Any](
              name = cse.name,
              value = transformedSchema.asInstanceOf[Reflect.Bound[Any]],
              doc = cse.doc,
              modifiers = cse.modifiers
            )
          }
        }
        if (transformedCases.zip(variant.cases).forall { case (t, o) => t eq o }) {
          variant
        } else {
          new Reflect.Variant[Binding, Any](
            cases = transformedCases.asInstanceOf[IndexedSeq[Term[Binding, Any, _ <: Any]]],
            typeName = variant.typeName.asInstanceOf[TypeName[Any]],
            variantBinding = variant.variantBinding.asInstanceOf[Binding.Variant[Any]]
          ).asInstanceOf[Reflect.Bound[_]]
        }

      case _ =>
        // Primitive or other: return as-is
        reflect
    }
  }

  /**
   * Create a structural schema for a nested case class. This handles the case
   * where the nested value is actually a StructuralRecord at runtime (converted
   * by toStructural).
   */
  private def createNestedStructuralSchema(
    nestedRecord: Reflect.Record.Bound[_]
  ): Reflect.Bound[StructuralRecord] = {
    import zio.blocks.schema.binding._
    import zio.blocks.schema.binding.RegisterOffset._

    val fieldNames = nestedRecord.fields.map(_.name)

    // Recursively convert nested field schemas (handles case classes inside collections too)
    val fieldSchemas: IndexedSeq[Reflect.Bound[_]] = nestedRecord.fields.map { term =>
      transformNestedToStructural(term.value)
    }

    val usedRegs = nestedRecord.constructor.usedRegisters

    val structuralFields: IndexedSeq[Term[Binding, StructuralRecord, _]] =
      fieldNames.zip(fieldSchemas).map { case (name, fieldReflect) =>
        new Term[Binding, StructuralRecord, Any](
          name,
          fieldReflect.asInstanceOf[Reflect.Bound[Any]]
        )
      }

    val structuralTypeName: TypeName[StructuralRecord] =
      TypeName.structuralFromTypeNames(fieldNames.zip(fieldSchemas.map(_.typeName)))

    // Constructor: Registers -> StructuralRecord
    val structuralConstructor = new Constructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord = {
        val fieldMap = nestedRecord.fields.map { term =>
          val regIdx     = nestedRecord.fieldIndexByName(term.name)
          val reg        = nestedRecord.registers(regIdx)
          val fieldValue = reg.asInstanceOf[Register[Any]].get(in, baseOffset)
          term.name -> fieldValue
        }.toMap
        new StructuralRecord(fieldMap)
      }
    }

    // Deconstructor: StructuralRecord -> Registers
    // This is the key fix: we expect StructuralRecord, not the nominal type
    val structuralDeconstructor = new Deconstructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit =
        nestedRecord.fields.foreach { term =>
          val fieldValue = in.selectDynamic(term.name)
          val regIdx     = nestedRecord.fieldIndexByName(term.name)
          val reg        = nestedRecord.registers(regIdx)
          reg.asInstanceOf[Register[Any]].set(out, baseOffset, fieldValue)
        }
    }

    new Reflect.Record[Binding, StructuralRecord](
      fields = structuralFields,
      typeName = structuralTypeName,
      recordBinding = new Binding.Record[StructuralRecord](
        constructor = structuralConstructor,
        deconstructor = structuralDeconstructor
      )
    )
  }
}
