package zio.blocks.schema

import scala.collection.immutable.ArraySeq
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

object StructuralSchemaTransformer {

  def transform(reflect: Reflect.Bound[?]): Reflect.Bound[?] =
    transformReflect(reflect, Set.empty)

  private def transformReflect(reflect: Reflect.Bound[?], seen: Set[String]): Reflect.Bound[?] = {
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
        // Option stays as Some/None at runtime - keep variant binding, but transform nested schemas
        if (isOptionVariant(v)) {
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

  private def isOptionVariant(variant: Reflect.Variant[Binding, ?]): Boolean = {
    val caseNames = variant.cases.map(_.name).toSet
    caseNames == Set("scala.Some", "scala.None") ||
    caseNames == Set("Some", "None") ||
    variant.typeName.name == "Option"
  }

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

  private def transformRecord(
    record: Reflect.Record[Binding, ?],
    seen: Set[String]
  ): Reflect.Bound[StructuralRecord] = {
    val fieldNames = record.fields.map(_.name)

    val transformedFieldSchemas: IndexedSeq[Reflect.Bound[Any]] =
      record.fields.map { term =>
        transformReflect(term.value, seen).asInstanceOf[Reflect.Bound[Any]]
      }

    val structuralFields: IndexedSeq[Term[Binding, StructuralRecord, Any]] =
      fieldNames.zip(transformedFieldSchemas).map { case (name, fieldSchema) =>
        new Term[Binding, StructuralRecord, Any](name, fieldSchema)
      }

    val fieldValues  = structuralFields.map(_.value.asInstanceOf[Reflect[Binding, ?]]).toArray
    val registersArr = Reflect.Record.registers(fieldValues)
    val registers    = ArraySeq.unsafeWrapArray(registersArr).asInstanceOf[IndexedSeq[Register[Any]]]
    val usedRegs     = Reflect.Record.usedRegisters(registersArr)

    val structuralConstructor = new Constructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord = {
        val fieldMap = fieldNames.zipWithIndex.map { case (name, idx) =>
          val reg   = registers(idx)
          val value = reg.get(in, baseOffset)
          name -> value // Build Map entry
        }.toMap
        new StructuralRecord(fieldMap)
      }
    }

    val structuralDeconstructor = new Deconstructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit =
        fieldNames.zipWithIndex.foreach { case (name, idx) =>
          val fieldValue = in.selectDynamic(name) // Dynamic access!
          val reg        = registers(idx)
          reg.set(out, baseOffset, fieldValue)
        }
    }

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

  private def transformVariant(
    variant: Reflect.Variant[Binding, ?],
    seen: Set[String]
  ): Reflect.Bound[StructuralRecord] = {
    val caseNames = variant.cases.map(_.name)

    val isEither = caseNames.toSet == Set("scala.Left", "scala.Right") ||
      caseNames.toSet == Set("Left", "Right")

    val tagNames: IndexedSeq[String] =
      if (isEither) {
        caseNames.map { name =>
          if (name.endsWith("Left")) "Left"
          else if (name.endsWith("Right")) "Right"
          else name
        }
      } else {
        caseNames.map { name =>
          // Extract simple name from fully qualified name: "com.example.Dog" -> "Dog"
          name.split('.').last
        }
      }

    val structuralCases: IndexedSeq[Term[Binding, StructuralRecord, StructuralRecord]] =
      caseNames.zip(tagNames).zipWithIndex.map { case ((_, tagName), idx) =>
        val originalCase = variant.cases(idx)

        val caseSchema = originalCase.value.asRecord match {
          case Some(caseRecord) =>
            // Case class with fields: transform record + add Tag
            transformCaseWithTag(caseRecord, tagName, seen)
          case None =>
            // Case object with no fields: just create {Tag: "CaseName"}
            createTagOnlyRecord(tagName)
        }

        new Term[Binding, StructuralRecord, StructuralRecord](tagName, caseSchema)
      }

    val tagToIndex: Map[String, Int] = tagNames.zipWithIndex.toMap

    val structuralDiscriminator = new Discriminator[StructuralRecord] {
      def discriminate(a: StructuralRecord): Int = {
        val tag = a.selectDynamic("Tag").asInstanceOf[String]
        tagToIndex.getOrElse(tag, -1)
      }
    }

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

    val caseTypeNameStrs                               = structuralCases.map(_.value.typeName.name)
    val variantTypeNameStr                             = TypeName.formatVariantUnion(caseTypeNameStrs)
    val structuralTypeName: TypeName[StructuralRecord] =
      new TypeName[StructuralRecord](Namespace.empty, variantTypeNameStr, Nil)

    new Reflect.Variant[Binding, StructuralRecord](
      cases = structuralCases.asInstanceOf[IndexedSeq[Term[Binding, StructuralRecord, ? <: StructuralRecord]]],
      typeName = structuralTypeName,
      variantBinding = new Binding.Variant[StructuralRecord](
        discriminator = structuralDiscriminator,
        matchers = structuralMatchers
      )
    )
  }

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

    val dataFieldStrs                            = fieldNames.zip(transformedFieldSchemas.map(_.typeName.toSimpleName))
    val caseTypeNameStr                          = TypeName.formatTaggedCaseWithFields(tagName, dataFieldStrs)
    val caseTypeName: TypeName[StructuralRecord] =
      new TypeName[StructuralRecord](Namespace.empty, caseTypeNameStr, Nil)

    new Reflect.Record[Binding, StructuralRecord](
      fields = dataFields.asInstanceOf[IndexedSeq[Term[Binding, StructuralRecord, ?]]],
      typeName = caseTypeName,
      recordBinding = new Binding.Record[StructuralRecord](
        constructor = structuralConstructor,
        deconstructor = structuralDeconstructor
      )
    )
  }

  // Creates an empty record schema for case objects. DynamicValue.Record is empty, but constructor adds Tag at runtime.

  private def createTagOnlyRecord(tagName: String): Reflect.Record[Binding, StructuralRecord] = {
    val fields = IndexedSeq.empty[Term[Binding, StructuralRecord, ?]]

    val structuralConstructor = new Constructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = 0

      def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord =
        new StructuralRecord(Map("Tag" -> tagName))
    }

    val structuralDeconstructor = new Deconstructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = 0

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit = {}
    }

    val caseTypeNameStr                          = TypeName.formatTaggedCaseWithFields(tagName, Nil)
    val caseTypeName: TypeName[StructuralRecord] =
      new TypeName[StructuralRecord](Namespace.empty, caseTypeNameStr, Nil)

    new Reflect.Record[Binding, StructuralRecord](
      fields = fields.asInstanceOf[IndexedSeq[Term[Binding, StructuralRecord, ?]]],
      typeName = caseTypeName,
      recordBinding = new Binding.Record[StructuralRecord](
        constructor = structuralConstructor,
        deconstructor = structuralDeconstructor
      )
    )
  }

  private def transformSequence(
    seq: Reflect.Sequence[Binding, ?, ?],
    seen: Set[String]
  ): Reflect.Bound[?] = {
    val transformedElement = transformReflect(seq.element.asInstanceOf[Reflect.Bound[Any]], seen)

    // If element didn't change, return original
    if (transformedElement eq seq.element) {
      return seq
    }

    // Create new TypeName with transformed element type name
    val newTypeName = seq.typeName
      .copy(
        params = Seq(transformedElement.typeName)
      )
      .asInstanceOf[TypeName[Any]]

    // Create new sequence with transformed element
    // We need to preserve the collection type and binding
    new Reflect.Sequence[Binding, Any, ({ type L[X] = Any })#L](
      element = transformedElement.asInstanceOf[Reflect.Bound[Any]],
      typeName = newTypeName,
      seqBinding = seq.seqBinding.asInstanceOf[Binding[BindingType.Seq[({ type L[X] = Any })#L], Any]]
    ).asInstanceOf[Reflect.Bound[?]]
  }

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

    // Create new TypeName with transformed key/value type names
    val newTypeName = map.typeName
      .copy(
        params = Seq(transformedKey.typeName, transformedValue.typeName)
      )
      .asInstanceOf[TypeName[Any]]

    // Create new map with transformed key/value
    new Reflect.Map[Binding, Any, Any, ({ type L[X, Y] = Any })#L](
      key = transformedKey.asInstanceOf[Reflect.Bound[Any]],
      value = transformedValue.asInstanceOf[Reflect.Bound[Any]],
      typeName = newTypeName,
      mapBinding = map.mapBinding.asInstanceOf[Binding[BindingType.Map[({ type L[X, Y] = Any })#L], Any]]
    ).asInstanceOf[Reflect.Bound[?]]
  }

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
