package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._

object StructuralSchemaTransformer {

  def transform[A](
    nominalSchema: Schema[A],
    @annotation.unused toStructural: ToStructural[A]
  ): Schema[StructuralRecord] = {
    val nominalRecord = nominalSchema.reflect.asRecord.getOrElse(
      throw new IllegalArgumentException(s"Expected Record schema, got: ${nominalSchema.reflect}")
    )

    // Field names from nominal schema
    val fieldNames: IndexedSeq[String] = nominalRecord.fields.map(_.name)

    // For each field, transform nested structurl types to structural schemas
    val fieldSchemas: IndexedSeq[Reflect.Bound[_]] = nominalRecord.fields.map { term =>
      transformReflect(term.value)
    }

    val usedRegs = nominalRecord.constructor.usedRegisters

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
    "scala.None"
  )

  private def isWrapperType(typeName: TypeName[_]): Boolean = {
    val elements = typeName.namespace.elements
    val fullName = (if (elements.isEmpty) "" else elements.mkString(".") + ".") + typeName.name
    wrapperTypeNames.contains(fullName)
  }

  private def transformReflect(reflect: Reflect.Bound[_]): Reflect.Bound[_] =
    reflect match {
      case record: Reflect.Record.Bound[_] if record.fields.nonEmpty && !isWrapperType(record.typeName) =>
        transformRecord(record)

      case record: Reflect.Record.Bound[_] if record.fields.nonEmpty && isWrapperType(record.typeName) =>
        // Wrapper type (Some, None): recursively transform fields but keep wrapper structure
        val transformedFields = record.fields.map { term =>
          val transformedValue = transformReflect(term.value)
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

      case seq: (Reflect.Sequence[Binding, _, _] @unchecked) =>
        // Sequence (List, Vector, etc.): transform element schema
        val transformedElement = transformReflect(seq.element)
        if (transformedElement eq seq.element) {
          seq
        } else {
          new Reflect.Sequence[Binding, Any, Any](
            element = transformedElement.asInstanceOf[Reflect.Bound[Any]],
            typeName = seq.typeName.asInstanceOf[TypeName[Any]],
            seqBinding = seq.seqBinding.asInstanceOf[Binding.Seq[Any, Any]]
          ).asInstanceOf[Reflect.Bound[_]]
        }

      case map: (Reflect.Map[Binding, _, _, _] @unchecked) =>
        // Map: transform key and value schemas
        val transformedKey   = transformReflect(map.key)
        val transformedValue = transformReflect(map.value)
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
          val transformedSchema = transformReflect(cse.value)
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

  private def transformRecord(
    nestedRecord: Reflect.Record.Bound[_]
  ): Reflect.Bound[StructuralRecord] = {
    val fieldNames = nestedRecord.fields.map(_.name)

    val fieldSchemas: IndexedSeq[Reflect.Bound[_]] = nestedRecord.fields.map { term =>
      transformReflect(term.value)
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
