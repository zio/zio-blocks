package zio.blocks.schema

import scala.collection.immutable.ArraySeq
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * Transforms nominal schemas to structural schemas.
 *
 * When `toStructural` converts a nominal value to a `StructuralRecord`:
 *   - Case classes → StructuralRecord with field name keys
 *   - Tuples → StructuralRecord with _1, _2, ... keys
 *   - Either → StructuralRecord with Tag ("Left"/"Right") and value fields
 *   - Sealed traits → StructuralRecord with Tag and case fields
 *
 * This transformer creates schemas that match those runtime representations,
 * ensuring `toDynamicValue` and `fromDynamicValue` work correctly.
 */
object StructuralSchemaTransformer {

  /**
   * Transforms a nominal schema to a structural schema. The returned schema
   * expects StructuralRecord values at runtime (except for primitives and
   * simple enums which are kept as-is).
   */
  def transform(reflect: Reflect.Bound[?]): Reflect.Bound[?] =
    transformReflect(reflect, Set.empty)

  private def transformReflect(reflect: Reflect.Bound[?], seen: Set[String]): Reflect.Bound[?] = {
    // Prevent infinite recursion for recursive types
    // Use toSimpleName which includes type parameters to distinguish e.g. Tuple2[Int,String] from Tuple2[Boolean,Double]
    val typeId = reflect.typeName.toSimpleName
    if (seen.contains(typeId)) {
      return reflect
    }
    val newSeen = seen + typeId

    reflect match {
      case p: Reflect.Primitive[Binding, ?] =>
        // Primitives stay as-is
        p

      case r: Reflect.Record[Binding, ?] =>
        // Transform record (case class or tuple) to structural record
        transformRecord(r, newSeen)

      case v: Reflect.Variant[Binding, ?] =>
        // Only transform variants that toStructural converts to StructuralRecord
        // Option stays as Some/None at runtime - keep variant binding, but transform nested schemas
        // Simple enums (all case objects) stay as-is completely
        if (isSimpleEnumVariant(v)) {
          // Simple enums: no transformation needed at all
          v
        } else if (isOptionVariant(v)) {
          // Option: keep binding but transform nested element schemas
          transformOptionVariant(v, newSeen)
        } else {
          // Transform to Tag-based structural variant (Either, sealed traits with data)
          transformVariant(v, newSeen)
        }

      case s: Reflect.Sequence[Binding, ?, ?] =>
        // Transform sequence with transformed element
        transformSequence(s, newSeen)

      case m: Reflect.Map[Binding, ?, ?, ?] =>
        // Transform map with transformed key/value
        transformMap(m, newSeen)

      case w: Reflect.Wrapper[Binding, ?, ?] =>
        // Transform wrapper with transformed inner type
        transformWrapper(w, newSeen)

      case d: Reflect.Deferred[Binding, ?] =>
        // Force and transform
        transformReflect(d.value, newSeen)

      case _ =>
        reflect
    }
  }

  /**
   * Check if this is an Option variant (Some/None cases).
   */
  private def isOptionVariant(variant: Reflect.Variant[Binding, ?]): Boolean = {
    val caseNames = variant.cases.map(_.name).toSet
    caseNames == Set("scala.Some", "scala.None") ||
    caseNames == Set("Some", "None") ||
    variant.typeName.name == "Option"
  }

  /**
   * Check if this is a simple enum (all cases have no fields - case objects
   * only).
   */
  private def isSimpleEnumVariant(variant: Reflect.Variant[Binding, ?]): Boolean =
    variant.cases.forall { term =>
      term.value.asRecord match {
        case Some(record) => record.fields.isEmpty
        case None         => true // Case object
      }
    }

  /**
   * Transform Option variant: keep the variant binding (Some/None
   * discrimination) but transform the element type inside Some to expect
   * StructuralRecord where needed.
   */
  private def transformOptionVariant(
    variant: Reflect.Variant[Binding, ?],
    seen: Set[String]
  ): Reflect.Bound[?] = {
    // Transform each case's inner schema (for Some, this transforms the element field)
    var anyChanged       = false
    val transformedCases = variant.cases.map { term =>
      term.value.asRecord match {
        case Some(caseRecord) =>
          // This is Some or None case - transform field schemas while keeping binding
          val transformedFieldSchemas = caseRecord.fields.map { field =>
            val transformed = transformReflect(field.value, seen)
            if (!(transformed eq field.value)) anyChanged = true
            (field.name, transformed)
          }

          if (!anyChanged) {
            term
          } else {
            // Create new Record with transformed field schemas but SAME binding
            val newFields = transformedFieldSchemas.map { case (name, schema) =>
              new Term[Binding, Any, Any](name, schema.asInstanceOf[Reflect.Bound[Any]])
            }
            val newCaseRecord = new Reflect.Record[Binding, Any](
              fields = newFields.asInstanceOf[IndexedSeq[Term[Binding, Any, ?]]],
              typeName = caseRecord.typeName.asInstanceOf[TypeName[Any]],
              recordBinding = caseRecord.recordBinding.asInstanceOf[Binding.Record[Any]]
            )
            new Term[Binding, Any, Any](term.name, newCaseRecord)
              .asInstanceOf[Term[Binding, ?, ? <: Any]]
          }

        case None =>
          // Case object (like None) - no fields to transform
          term
      }
    }

    if (!anyChanged) {
      return variant
    }

    // Create new variant with transformed cases but SAME variant binding
    new Reflect.Variant[Binding, Any](
      cases = transformedCases.asInstanceOf[IndexedSeq[Term[Binding, Any, ? <: Any]]],
      typeName = variant.typeName.asInstanceOf[TypeName[Any]],
      variantBinding = variant.variantBinding.asInstanceOf[Binding.Variant[Any]]
    ).asInstanceOf[Reflect.Bound[?]]
  }

  /**
   * Transform variant without changing its structure - only transform case
   * schemas recursively. Used for Option and simple enums that shouldn't become
   * Tag-based.
   */
  private def transformVariantCasesOnly(
    variant: Reflect.Variant[Binding, ?],
    seen: Set[String]
  ): Reflect.Bound[?] = {
    // Transform each case's inner schema
    val transformedCases = variant.cases.map { term =>
      val transformedValue = transformReflect(term.value, seen)
      if (transformedValue eq term.value) {
        term
      } else {
        new Term[Binding, Any, Any](term.name, transformedValue.asInstanceOf[Reflect.Bound[Any]])
          .asInstanceOf[Term[Binding, ?, ? <: Any]]
      }
    }

    // If nothing changed, return original
    if (transformedCases.zip(variant.cases).forall { case (a, b) => a eq b }) {
      return variant
    }

    // Create new variant with transformed cases but same binding
    new Reflect.Variant[Binding, Any](
      cases = transformedCases.asInstanceOf[IndexedSeq[Term[Binding, Any, ? <: Any]]],
      typeName = variant.typeName.asInstanceOf[TypeName[Any]],
      variantBinding = variant.variantBinding.asInstanceOf[Binding.Variant[Any]]
    ).asInstanceOf[Reflect.Bound[?]]
  }

  /**
   * Transforms a Record schema to expect StructuralRecord at runtime.
   */
  private def transformRecord(
    record: Reflect.Record[Binding, ?],
    seen: Set[String]
  ): Reflect.Bound[StructuralRecord] = {
    val fieldNames = record.fields.map(_.name)

    // Transform each field's schema recursively
    val transformedFieldSchemas: IndexedSeq[Reflect.Bound[Any]] =
      record.fields.map { term =>
        transformReflect(term.value, seen).asInstanceOf[Reflect.Bound[Any]]
      }

    // Create new Terms with transformed schemas
    val structuralFields: IndexedSeq[Term[Binding, StructuralRecord, Any]] =
      fieldNames.zip(transformedFieldSchemas).map { case (name, fieldSchema) =>
        new Term[Binding, StructuralRecord, Any](name, fieldSchema)
      }

    // Create registers for the structural record
    val fieldValues  = structuralFields.map(_.value.asInstanceOf[Reflect[Binding, ?]]).toArray
    val registersArr = Reflect.Record.registers(fieldValues)
    val registers    = ArraySeq.unsafeWrapArray(registersArr).asInstanceOf[IndexedSeq[Register[Any]]]
    val usedRegs     = Reflect.Record.usedRegisters(registersArr)

    // Constructor: Registers → StructuralRecord
    val structuralConstructor = new Constructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord = {
        val fieldMap = fieldNames.zipWithIndex.map { case (name, idx) =>
          val reg   = registers(idx)
          val value = reg.get(in, baseOffset)
          name -> value
        }.toMap
        new StructuralRecord(fieldMap)
      }
    }

    // Deconstructor: StructuralRecord → Registers
    val structuralDeconstructor = new Deconstructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit =
        fieldNames.zipWithIndex.foreach { case (name, idx) =>
          val fieldValue = in.selectDynamic(name)
          val reg        = registers(idx)
          reg.set(out, baseOffset, fieldValue)
        }
    }

    // Create TypeName
    val structuralTypeName: TypeName[StructuralRecord] =
      TypeName.structuralFromTypeNames(fieldNames.zip(transformedFieldSchemas.map(_.typeName)))

    new Reflect.Record[Binding, StructuralRecord](
      fields = structuralFields,
      typeName = structuralTypeName,
      recordBinding = new Binding.Record[StructuralRecord](
        constructor = structuralConstructor,
        deconstructor = structuralDeconstructor
      )
    )
  }

  /**
   * Transforms a Variant schema to expect StructuralRecord at runtime. The
   * discriminator reads the "Tag" field from StructuralRecord.
   */
  private def transformVariant(
    variant: Reflect.Variant[Binding, ?],
    seen: Set[String]
  ): Reflect.Bound[StructuralRecord] = {
    val caseNames = variant.cases.map(_.name)

    // Check if this is an Either (has Left/Right cases)
    val isEither = caseNames.toSet == Set("scala.Left", "scala.Right") ||
      caseNames.toSet == Set("Left", "Right")

    // For Either, use "Left"/"Right" as Tag values; for sealed traits, use case names
    val tagNames: IndexedSeq[String] =
      if (isEither) {
        caseNames.map { name =>
          if (name.endsWith("Left")) "Left"
          else if (name.endsWith("Right")) "Right"
          else name
        }
      } else {
        caseNames.map { name =>
          // Extract simple name from fully qualified name
          name.split('.').last
        }
      }

    // Transform each case's schema
    val structuralCases: IndexedSeq[Term[Binding, StructuralRecord, StructuralRecord]] =
      caseNames.zip(tagNames).zipWithIndex.map { case ((caseName, tagName), idx) =>
        val originalCase = variant.cases(idx)

        // Transform the case schema
        val caseSchema = originalCase.value.asRecord match {
          case Some(caseRecord) =>
            // Case class: transform to structural record with Tag field
            transformCaseWithTag(caseRecord, tagName, seen)
          case None =>
            // Case object or primitive: just Tag field
            createTagOnlyRecord(tagName)
        }

        new Term[Binding, StructuralRecord, StructuralRecord](tagName, caseSchema)
      }

    // Tag-to-index map for discriminator
    val tagToIndex: Map[String, Int] = tagNames.zipWithIndex.toMap

    // Discriminator: read Tag field
    val structuralDiscriminator = new Discriminator[StructuralRecord] {
      def discriminate(a: StructuralRecord): Int = {
        val tag = a.selectDynamic("Tag").asInstanceOf[String]
        tagToIndex.getOrElse(tag, -1)
      }
    }

    // Matchers: each case returns StructuralRecord as-is
    val structuralMatchers = Matchers(
      tagNames.map { _ =>
        new Matcher[StructuralRecord] {
          def downcastOrNull(a: Any): StructuralRecord = a match {
            case sr: StructuralRecord => sr
            case _                    => null
          }
        }.asInstanceOf[Matcher[? <: StructuralRecord]]
      }*
    )

    // Create variant TypeName
    val structuralTypeName: TypeName[StructuralRecord] = TypeName.variant(tagNames)

    new Reflect.Variant[Binding, StructuralRecord](
      cases = structuralCases.asInstanceOf[IndexedSeq[Term[Binding, StructuralRecord, ? <: StructuralRecord]]],
      typeName = structuralTypeName,
      variantBinding = new Binding.Variant[StructuralRecord](
        discriminator = structuralDiscriminator,
        matchers = structuralMatchers
      )
    )
  }

  /**
   * Creates a structural record schema for a case with data fields. Note: Tag
   * is NOT included in the schema fields because DynamicValue.Variant handles
   * discrimination separately. Tag is added at runtime by constructor.
   */
  private def transformCaseWithTag(
    caseRecord: Reflect.Record[Binding, ?],
    tagName: String,
    seen: Set[String]
  ): Reflect.Record[Binding, StructuralRecord] = {
    val fieldNames = caseRecord.fields.map(_.name)

    // Transform each field's schema recursively
    val transformedFieldSchemas: IndexedSeq[Reflect.Bound[Any]] =
      caseRecord.fields.map { term =>
        transformReflect(term.value, seen).asInstanceOf[Reflect.Bound[Any]]
      }

    // Create fields: only data fields (Tag is handled by variant, not record)
    val dataFields: IndexedSeq[Term[Binding, StructuralRecord, Any]] =
      fieldNames.zip(transformedFieldSchemas).map { case (name, fieldSchema) =>
        new Term[Binding, StructuralRecord, Any](name, fieldSchema)
      }

    // Create registers for data fields only
    val fieldValues  = dataFields.map(_.value.asInstanceOf[Reflect[Binding, ?]]).toArray
    val registersArr = Reflect.Record.registers(fieldValues)
    val registers    = ArraySeq.unsafeWrapArray(registersArr).asInstanceOf[IndexedSeq[Register[Any]]]
    val usedRegs     = Reflect.Record.usedRegisters(registersArr)

    // Constructor: Registers → StructuralRecord (adds Tag at runtime)
    val structuralConstructor = new Constructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord = {
        // Read data fields from registers and add Tag
        val fieldMap = fieldNames.zipWithIndex.map { case (name, idx) =>
          val reg   = registers(idx)
          val value = reg.get(in, baseOffset)
          name -> value
        }.toMap + ("Tag" -> tagName)
        new StructuralRecord(fieldMap)
      }
    }

    // Deconstructor: StructuralRecord → Registers (only data fields, not Tag)
    val structuralDeconstructor = new Deconstructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit =
        fieldNames.zipWithIndex.foreach { case (name, idx) =>
          val fieldValue = in.selectDynamic(name)
          val reg        = registers(idx)
          reg.set(out, baseOffset, fieldValue)
        }
    }

    // TypeName
    val caseTypeName: TypeName[StructuralRecord] = TypeName.taggedCase(tagName)

    new Reflect.Record[Binding, StructuralRecord](
      fields = dataFields.asInstanceOf[IndexedSeq[Term[Binding, StructuralRecord, ?]]],
      typeName = caseTypeName,
      recordBinding = new Binding.Record[StructuralRecord](
        constructor = structuralConstructor,
        deconstructor = structuralDeconstructor
      )
    )
  }

  /**
   * Creates an empty record schema for case objects. DynamicValue.Record is
   * empty, but constructor adds Tag at runtime.
   */
  private def createTagOnlyRecord(tagName: String): Reflect.Record[Binding, StructuralRecord] = {
    // No fields in schema - Tag is not serialized to DynamicValue
    val fields = IndexedSeq.empty[Term[Binding, StructuralRecord, ?]]

    val structuralConstructor = new Constructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = 0

      def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord =
        new StructuralRecord(Map("Tag" -> tagName))
    }

    val structuralDeconstructor = new Deconstructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = 0

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit = {
        // No data fields to deconstruct
      }
    }

    val caseTypeName: TypeName[StructuralRecord] = TypeName.taggedCase(tagName)

    new Reflect.Record[Binding, StructuralRecord](
      fields = fields.asInstanceOf[IndexedSeq[Term[Binding, StructuralRecord, ?]]],
      typeName = caseTypeName,
      recordBinding = new Binding.Record[StructuralRecord](
        constructor = structuralConstructor,
        deconstructor = structuralDeconstructor
      )
    )
  }

  /**
   * Transforms a Sequence schema with transformed element type.
   */
  private def transformSequence(
    seq: Reflect.Sequence[Binding, ?, ?],
    seen: Set[String]
  ): Reflect.Bound[?] = {
    val transformedElement = transformReflect(seq.element.asInstanceOf[Reflect.Bound[Any]], seen)

    // If element didn't change, return original
    if (transformedElement eq seq.element) {
      return seq
    }

    // Create new sequence with transformed element
    // We need to preserve the collection type and binding
    new Reflect.Sequence[Binding, Any, ({ type L[X] = Any })#L](
      element = transformedElement.asInstanceOf[Reflect.Bound[Any]],
      typeName = seq.typeName.asInstanceOf[TypeName[Any]],
      seqBinding = seq.seqBinding.asInstanceOf[Binding[BindingType.Seq[({ type L[X] = Any })#L], Any]]
    ).asInstanceOf[Reflect.Bound[?]]
  }

  /**
   * Transforms a Map schema with transformed key/value types.
   */
  private def transformMap(
    map: Reflect.Map[Binding, ?, ?, ?],
    seen: Set[String]
  ): Reflect.Bound[?] = {
    val transformedKey   = transformReflect(map.key.asInstanceOf[Reflect.Bound[Any]], seen)
    val transformedValue = transformReflect(map.value.asInstanceOf[Reflect.Bound[Any]], seen)

    // If neither changed, return original
    if ((transformedKey eq map.key) && (transformedValue eq map.value)) {
      return map
    }

    // Create new map with transformed key/value
    new Reflect.Map[Binding, Any, Any, ({ type L[X, Y] = Any })#L](
      key = transformedKey.asInstanceOf[Reflect.Bound[Any]],
      value = transformedValue.asInstanceOf[Reflect.Bound[Any]],
      typeName = map.typeName.asInstanceOf[TypeName[Any]],
      mapBinding = map.mapBinding.asInstanceOf[Binding[BindingType.Map[({ type L[X, Y] = Any })#L], Any]]
    ).asInstanceOf[Reflect.Bound[?]]
  }

  /**
   * Transforms a Wrapper schema with transformed inner type.
   */
  private def transformWrapper(
    wrapper: Reflect.Wrapper[Binding, ?, ?],
    seen: Set[String]
  ): Reflect.Bound[?] = {
    val transformedInner = transformReflect(wrapper.wrapped.asInstanceOf[Reflect.Bound[Any]], seen)

    // If inner didn't change, return original
    if (transformedInner eq wrapper.wrapped) {
      return wrapper
    }

    // For wrappers, we generally keep them as-is since they represent newtypes
    wrapper
  }
}
