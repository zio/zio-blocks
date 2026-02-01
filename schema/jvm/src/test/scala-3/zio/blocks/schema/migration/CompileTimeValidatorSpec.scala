package zio.blocks.schema.migration

import zio.test._

/**
 * Tests for CompileTimeValidator.SchemaShape and ValidationResult types. These
 * have 0% branch coverage according to scoverage reports.
 */
object CompileTimeValidatorSpec extends ZIOSpecDefault {

  import CompileTimeValidator.SchemaShape._
  import CompileTimeValidator.ValidationResult

  def spec = suite("CompileTimeValidatorSpec")(
    suite("SchemaShape.Record")(
      test("fieldNames returns all field names") {
        val record = Record(
          Map(
            "name" -> FieldInfo("name", "String", false, None),
            "age"  -> FieldInfo("age", "Int", false, None)
          )
        )
        assertTrue(record.fieldNames == Set("name", "age"))
      },
      test("isSubsetOf returns true for subset") {
        val smaller = Record(Map("a" -> FieldInfo("a", "Int", false, None)))
        val larger  = Record(
          Map(
            "a" -> FieldInfo("a", "Int", false, None),
            "b" -> FieldInfo("b", "Int", false, None)
          )
        )
        assertTrue(smaller.isSubsetOf(larger))
      },
      test("isSubsetOf returns false for non-subset") {
        val r1 = Record(Map("x" -> FieldInfo("x", "Int", false, None)))
        val r2 = Record(Map("y" -> FieldInfo("y", "Int", false, None)))
        assertTrue(!r1.isSubsetOf(r2))
      },
      test("isSubsetOf returns false for non-Record") {
        val record    = Record(Map("a" -> FieldInfo("a", "Int", false, None)))
        val primitive = Primitive("Int")
        assertTrue(!record.isSubsetOf(primitive))
      },
      test("difference returns fields not in other") {
        val r1 = Record(
          Map(
            "a" -> FieldInfo("a", "Int", false, None),
            "b" -> FieldInfo("b", "Int", false, None)
          )
        )
        val r2 = Record(Map("a" -> FieldInfo("a", "Int", false, None)))
        assertTrue(r1.difference(r2) == Set("b"))
      },
      test("difference returns all fields for non-Record") {
        val record    = Record(Map("a" -> FieldInfo("a", "Int", false, None)))
        val primitive = Primitive("Int")
        assertTrue(record.difference(primitive) == Set("a"))
      }
    ),
    suite("SchemaShape.Enum")(
      test("fieldNames returns case names") {
        val enumShape = Enum(
          Map(
            "Success" -> Primitive("Unit"),
            "Failure" -> Primitive("String")
          )
        )
        assertTrue(enumShape.fieldNames == Set("Success", "Failure"))
      },
      test("isSubsetOf returns true for subset of cases") {
        val smaller = Enum(Map("A" -> Primitive("Int")))
        val larger  = Enum(
          Map(
            "A" -> Primitive("Int"),
            "B" -> Primitive("String")
          )
        )
        assertTrue(smaller.isSubsetOf(larger))
      },
      test("isSubsetOf returns false for non-Enum") {
        val enumShape = Enum(Map("A" -> Primitive("Int")))
        val record    = Record(Map.empty)
        assertTrue(!enumShape.isSubsetOf(record))
      },
      test("difference returns cases not in other Enum") {
        val e1 = Enum(Map("A" -> Primitive("Int"), "B" -> Primitive("String")))
        val e2 = Enum(Map("A" -> Primitive("Int")))
        assertTrue(e1.difference(e2) == Set("B"))
      },
      test("difference returns all cases for non-Enum") {
        val enumShape = Enum(Map("X" -> Primitive("Int")))
        val record    = Record(Map.empty)
        assertTrue(enumShape.difference(record) == Set("X"))
      }
    ),
    suite("SchemaShape.Primitive")(
      test("fieldNames returns empty set") {
        assertTrue(Primitive("Int").fieldNames.isEmpty)
      },
      test("isSubsetOf returns true for same type") {
        assertTrue(Primitive("Int").isSubsetOf(Primitive("Int")))
      },
      test("isSubsetOf returns false for different type") {
        assertTrue(!Primitive("Int").isSubsetOf(Primitive("String")))
      },
      test("isSubsetOf returns false for non-Primitive") {
        assertTrue(!Primitive("Int").isSubsetOf(Record(Map.empty)))
      },
      test("difference returns empty set") {
        assertTrue(Primitive("Int").difference(Primitive("String")).isEmpty)
      }
    ),
    suite("SchemaShape.Collection")(
      test("fieldNames delegates to element shape") {
        val element    = Record(Map("x" -> FieldInfo("x", "Int", false, None)))
        val collection = Collection(element)
        assertTrue(collection.fieldNames == Set("x"))
      },
      test("isSubsetOf returns true when element is subset") {
        val smaller = Collection(Record(Map("a" -> FieldInfo("a", "Int", false, None))))
        val larger  = Collection(
          Record(
            Map(
              "a" -> FieldInfo("a", "Int", false, None),
              "b" -> FieldInfo("b", "Int", false, None)
            )
          )
        )
        assertTrue(smaller.isSubsetOf(larger))
      },
      test("isSubsetOf returns false for non-Collection") {
        val collection = Collection(Primitive("Int"))
        val primitive  = Primitive("Int")
        assertTrue(!collection.isSubsetOf(primitive))
      },
      test("difference delegates to element shape") {
        val c1 = Collection(
          Record(
            Map(
              "a" -> FieldInfo("a", "Int", false, None),
              "b" -> FieldInfo("b", "Int", false, None)
            )
          )
        )
        val c2 = Collection(Record(Map("a" -> FieldInfo("a", "Int", false, None))))
        assertTrue(c1.difference(c2) == Set("b"))
      },
      test("difference returns empty for non-Collection") {
        val collection = Collection(Primitive("Int"))
        val primitive  = Primitive("Int")
        assertTrue(collection.difference(primitive).isEmpty)
      }
    ),
    suite("SchemaShape.FieldInfo")(
      test("creates non-optional field without nested shape") {
        val info = FieldInfo("name", "String", false, None)
        assertTrue(info.name == "name" && info.typeName == "String" && !info.isOptional && info.nestedShape.isEmpty)
      },
      test("creates optional field") {
        val info = FieldInfo("maybe", "Option[Int]", true, None)
        assertTrue(info.isOptional)
      },
      test("creates field with nested shape") {
        val nested = Record(Map("x" -> FieldInfo("x", "Int", false, None)))
        val info   = FieldInfo("nested", "Nested", false, Some(nested))
        assertTrue(info.nestedShape.isDefined)
      }
    ),
    suite("ValidationResult")(
      test("Valid.isValid returns true") {
        assertTrue(ValidationResult.Valid.isValid)
      },
      test("Valid.errors returns empty list") {
        assertTrue(ValidationResult.Valid.errors.isEmpty)
      },
      test("Invalid.isValid returns false") {
        val invalid = ValidationResult.Invalid(List("error1"))
        assertTrue(!invalid.isValid)
      },
      test("Invalid.errors returns error list") {
        val errors  = List("error1", "error2")
        val invalid = ValidationResult.Invalid(errors)
        assertTrue(invalid.errors == errors)
      },
      test("fromErrors returns Valid for empty list") {
        assertTrue(ValidationResult.fromErrors(Nil) == ValidationResult.Valid)
      },
      test("fromErrors returns Invalid for non-empty list") {
        val result = ValidationResult.fromErrors(List("error"))
        assertTrue(result match {
          case ValidationResult.Invalid(errs) => errs == List("error")
          case _                              => false
        })
      }
    )
  )
}
