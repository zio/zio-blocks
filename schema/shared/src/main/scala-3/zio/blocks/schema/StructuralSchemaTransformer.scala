package zio.blocks.schema

import scala.collection.immutable.ArraySeq
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * PHASE 3: Schema Transformation for Structural Types (Scala 3)
 * ═════════════════════════════════════════════════════════════════════════════
 *
 * Transforms nominal schemas (Schema[CaseClass]) to structural schemas
 * (Schema[StructuralRecord]) so serialization/deserialization works correctly.
 *
 * The Problem:
 * ─────────────────────────────────────────────────────────────────────────────
 * After Phase 2 converts values to StructuralRecord, the original schema's
 * bindings no longer work:
 *
 *   - Constructor: Registers → CaseClass (expects to create CaseClass)
 *   - Deconstructor: CaseClass → Registers (expects to access case class
 *     fields)
 *   - Discriminator: CaseClass → Int (expects instanceof checks)
 *
 * But we have StructuralRecord, not CaseClass!
 *
 * The Solution:
 * ─────────────────────────────────────────────────────────────────────────────
 * Transform each binding to work with StructuralRecord:
 *
 *   - Constructor: Registers → StructuralRecord (creates StructuralRecord)
 *   - Deconstructor: StructuralRecord → Registers (uses selectDynamic for
 *     fields)
 *   - Discriminator: StructuralRecord → Int (reads "Tag" field)
 *
 * Transformations by Type:
 * ─────────────────────────────────────────────────────────────────────────────
 *   - Case classes → StructuralRecord with field names as keys
 *   - Tuples → StructuralRecord with _1, _2, ... keys
 *   - Either[L, R] → StructuralRecord with Tag ("Left"/"Right") and value
 *     fields
 *   - Sealed traits → StructuralRecord with Tag (case name) and case data
 *     fields
 *   - Option → Keep as Option (Some/None discrimination works as-is)
 *   - Simple enums → Keep original type (no data fields to transform)
 *   - Primitives → Keep as-is
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
   *
   * EXAMPLE: Person(name: String, age: Int) schema transformation
   *
   * BEFORE: Constructor: Registers → new Person(reg.getString(0),
   * reg.getInt(1)) Deconstructor: person → reg.setString(0, person.name);
   * reg.setInt(1, person.age)
   *
   * AFTER: Constructor: Registers → new StructuralRecord(Map("name" ->
   * reg.getString(0), "age" -> reg.getInt(1))) Deconstructor: record →
   * reg.setString(0, record.selectDynamic("name")); reg.setInt(1,
   * record.selectDynamic("age"))
   */
  private def transformRecord(
    record: Reflect.Record[Binding, ?],
    seen: Set[String]
  ): Reflect.Bound[StructuralRecord] = {
    val fieldNames = record.fields.map(_.name)

    // STEP 1: Transform each field's schema recursively (handles nested case classes)
    // If field type is Person, recursively transform Person's schema too
    val transformedFieldSchemas: IndexedSeq[Reflect.Bound[Any]] =
      record.fields.map { term =>
        transformReflect(term.value, seen).asInstanceOf[Reflect.Bound[Any]]
      }

    // STEP 2: Create new Terms pointing to StructuralRecord instead of nominal type
    val structuralFields: IndexedSeq[Term[Binding, StructuralRecord, Any]] =
      fieldNames.zip(transformedFieldSchemas).map { case (name, fieldSchema) =>
        new Term[Binding, StructuralRecord, Any](name, fieldSchema)
      }

    // STEP 3: Create registers (storage slots) for field values
    val fieldValues  = structuralFields.map(_.value.asInstanceOf[Reflect[Binding, ?]]).toArray
    val registersArr = Reflect.Record.registers(fieldValues)
    val registers    = ArraySeq.unsafeWrapArray(registersArr).asInstanceOf[IndexedSeq[Register[Any]]]
    val usedRegs     = Reflect.Record.usedRegisters(registersArr)

    // STEP 4: Build NEW Constructor that creates StructuralRecord
    // OLD: new Person(registers[0], registers[1])
    // NEW: new StructuralRecord(Map("name" -> registers[0], "age" -> registers[1]))
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

    // STEP 5: Build NEW Deconstructor that uses selectDynamic
    // OLD: person.name (field accessor)
    // NEW: record.selectDynamic("name") (dynamic field access)
    val structuralDeconstructor = new Deconstructor[StructuralRecord] {
      def usedRegisters: RegisterOffset = usedRegs

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit =
        fieldNames.zipWithIndex.foreach { case (name, idx) =>
          val fieldValue = in.selectDynamic(name) // Dynamic access!
          val reg        = registers(idx)
          reg.set(out, baseOffset, fieldValue)
        }
    }

    // STEP 6: Create TypeName for the structural type
    // TypeName: {age:Int,name:String} (fields sorted alphabetically)
    // This is used for schema comparison and serialization format identification
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
   *
   * EXAMPLE: sealed trait Animal; case class Dog(name: String); case object Cat
   *
   * BEFORE: Discriminator: (a: Animal) => if (a.isInstanceOf[Dog]) 0 else if
   * (a.isInstanceOf[Cat]) 1 else -1 Matcher[Dog]: downcast to Dog or null
   * Matcher[Cat]: downcast to Cat or null
   *
   * AFTER: Discriminator: (a: StructuralRecord) => a.selectDynamic("Tag") match
   * { "Dog" -> 0; "Cat" -> 1 } Matcher: all cases are StructuralRecord, no
   * downcast needed
   */
  private def transformVariant(
    variant: Reflect.Variant[Binding, ?],
    seen: Set[String]
  ): Reflect.Bound[StructuralRecord] = {
    val caseNames = variant.cases.map(_.name)

    // STEP 1: Normalize case names for Either (use "Left"/"Right" instead of "scala.Left"/"scala.Right")
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

    // STEP 2: Transform each case's schema to structural form
    // Dog(name: String) → {Tag: "Dog", name: String}
    // Cat (case object) → {Tag: "Cat"}
    val structuralCases: IndexedSeq[Term[Binding, StructuralRecord, StructuralRecord]] =
      caseNames.zip(tagNames).zipWithIndex.map { case ((caseName, tagName), idx) =>
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

    // STEP 3: Build NEW Discriminator that reads Tag field
    // OLD: a.isInstanceOf[Dog] → 0; a.isInstanceOf[Cat] → 1
    // NEW: a.selectDynamic("Tag") == "Dog" → 0; == "Cat" → 1
    val tagToIndex: Map[String, Int] = tagNames.zipWithIndex.toMap

    val structuralDiscriminator = new Discriminator[StructuralRecord] {
      def discriminate(a: StructuralRecord): Int = {
        val tag = a.selectDynamic("Tag").asInstanceOf[String]
        tagToIndex.getOrElse(tag, -1)
      }
    }

    // STEP 4: Build NEW Matchers (simplified - no downcasting needed)
    // OLD: downcast Animal to Dog or Cat
    // NEW: all cases are StructuralRecord already, just return as-is
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

    // STEP 5: Create TypeName for the variant (UNION TYPE)
    // TypeName: ({Tag:"Cat"}|{Tag:"Dog",name:String})  - sorted alphabetically
    // This represents the structural type signature for schema comparison
    val caseTypeNameStrs                               = structuralCases.map(_.value.typeName.name).sorted
    val variantTypeNameStr                             = "(" + caseTypeNameStrs.mkString("|") + ")"
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

    // STEP 6: Create TypeName for this case
    // TYPENAME USAGE: Creates a normalized, comparable representation
    //
    // Example: Dog(name: String) becomes {Tag:"Dog",name:String}
    //
    // Why sorted alphabetically? So these are equal:
    //   {name:String,Tag:"Dog"} == {Tag:"Dog",name:String}
    //
    // The Tag value is a quoted STRING LITERAL in the type name:
    //   Tag:"Dog"  not  Tag:Dog
    val tagFieldType                             = s""""$tagName"""" // Creates "Dog" with quotes
    val allFieldStrs                             = ("Tag" -> tagFieldType) +: fieldNames.zip(transformedFieldSchemas.map(_.typeName.toSimpleName))
    val caseTypeName: TypeName[StructuralRecord] =
      TypeName.structural(allFieldStrs) // Sorts alphabetically inside

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

    // TypeName for case object: {Tag:"CaseName"}
    val tagFieldType                             = s""""$tagName""""
    val caseTypeName: TypeName[StructuralRecord] =
      TypeName.structural(Seq("Tag" -> tagFieldType))

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
