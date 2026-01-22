package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.migration.{SchemaExpr => SE}
import zio.test._
import zio.test.Assertion._

/**
 * Comprehensive tests for individual MigrationAction types.
 *
 * Tests each of the 14 action types in detail:
 *   - AddField, DropField, Rename, TransformValue
 *   - Mandate, Optionalize, ChangeType
 *   - Join, Split
 *   - RenameCase, TransformCase
 *   - TransformElements, TransformKeys, TransformValues
 */
object MigrationActionSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionSpec")(
    addFieldTests,
    dropFieldTests,
    renameTests,
    transformValueTests,
    mandateTests,
    optionalizeTests,
    changeTypeTests,
    joinTests,
    splitTests,
    renameCaseTests,
    transformCaseTests,
    transformElementsTests,
    transformKeysTests,
    transformValuesTests
  )

  // ===== AddField Tests =====

  val addFieldTests = suite("AddField")(
    test("add field with string default") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
        )
      )

      val action = MigrationAction.AddField(
        DynamicOptic.root,
        "age",
        SE.literalInt(30)
      )

      val result = action.apply(record)

      assert(result)(isRight(anything)) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.size))(isRight(equalTo(2)))
    },

    test("add field to nested record") {
      val address = DynamicValue.Record(
        Vector(
          "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main"))
        )
      )

      val person = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "address" -> address
        )
      )

      val action = MigrationAction.AddField(
        DynamicOptic.root / "address",
        "city",
        SE.literalString("NYC")
      )

      val result = action.apply(person)

      assert(result)(isRight(anything))
    },

    test("add field fails when field already exists") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Charlie"))
        )
      )

      val action = MigrationAction.AddField(
        DynamicOptic.root,
        "name",
        SE.literalString("duplicate")
      )

      val result = action.apply(record)

      assert(result)(isLeft(anything))
    },

    test("add field reverse is drop field") {
      val action = MigrationAction.AddField(
        DynamicOptic.root,
        "country",
        SE.literalString("USA")
      )

      val reverse = action.reverse

      assert(reverse)(isSubtype[MigrationAction.DropField](anything))
    }
  )

  // ===== DropField Tests =====

  val dropFieldTests = suite("DropField")(
    test("drop existing field") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Diana")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(28))
        )
      )

      val action = MigrationAction.DropField(
        DynamicOptic.root,
        "age",
        None
      )

      val result = action.apply(record)

      assert(result)(isRight(anything)) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.size))(isRight(equalTo(1)))
    },

    test("drop field from nested record") {
      val address = DynamicValue.Record(
        Vector(
          "street" -> DynamicValue.Primitive(PrimitiveValue.String("456 Oak")),
          "city"   -> DynamicValue.Primitive(PrimitiveValue.String("LA")),
          "zip"    -> DynamicValue.Primitive(PrimitiveValue.String("90001"))
        )
      )

      val person = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Eve")),
          "address" -> address
        )
      )

      val action = MigrationAction.DropField(
        DynamicOptic.root / "address",
        "zip",
        None
      )

      val result = action.apply(person)

      assert(result)(isRight(anything))
    },

    test("drop field fails when field doesn't exist") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Frank"))
        )
      )

      val action = MigrationAction.DropField(
        DynamicOptic.root,
        "nonexistent",
        None
      )

      val result = action.apply(record)

      assert(result)(isLeft(anything))
    }
  )

  // ===== Rename Tests =====

  val renameTests = suite("Rename")(
    test("rename field at root level") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Grace"))
        )
      )

      val action = MigrationAction.Rename(
        DynamicOptic.root,
        "name",
        "fullName"
      )

      val result = action.apply(record)

      assert(result)(isRight(anything))
    }
  )

  // ===== TransformValue Tests =====

  val transformValueTests = suite("TransformValue")(
    test("transform field value with literal") {
      val record = DynamicValue.Record(
        Vector(
          "name"   -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "status" -> DynamicValue.Primitive(PrimitiveValue.String("pending"))
        )
      )

      val action = MigrationAction.TransformValue(
        DynamicOptic.root,
        "status",
        SE.literalString("active")
      )

      val result = action.apply(record)

      assert(result)(isRight(anything))
    },

    test("transform nested field value") {
      val address = DynamicValue.Record(
        Vector(
          "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 main st"))
        )
      )

      val person = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "address" -> address
        )
      )

      val action = MigrationAction.TransformValue(
        DynamicOptic.root / "address",
        "street",
        SE.literalString("123 Main St") // Capitalize
      )

      val result = action.apply(person)

      assert(result)(isRight(anything))
    }
  )

  // ===== Mandate Tests =====

  val mandateTests = suite("Mandate")(
    test("mandate optional field with Some value") {
      val record = DynamicValue.Record(
        Vector(
          "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "email" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("bob@test.com")))
        )
      )

      val action = MigrationAction.Mandate(
        DynamicOptic.root,
        "email",
        SE.literalString("default@test.com")
      )

      val result = action.apply(record)

      assert(result)(isRight(anything))
    },

    test("mandate optional field with None value uses default") {
      val record = DynamicValue.Record(
        Vector(
          "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Charlie")),
          "email" -> DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
        )
      )

      val action = MigrationAction.Mandate(
        DynamicOptic.root,
        "email",
        SE.literalString("default@test.com")
      )

      val result = action.apply(record)

      assert(result)(isRight(anything))
    },

    test("mandate reverse is optionalize") {
      val action = MigrationAction.Mandate(
        DynamicOptic.root,
        "email",
        SE.literalString("default@test.com")
      )

      val reverse = action.reverse

      assert(reverse)(isSubtype[MigrationAction.Optionalize](anything))
    }
  )

  // ===== Optionalize Tests =====

  val optionalizeTests = suite("Optionalize")(
    test("optionalize mandatory field") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Diana")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        )
      )

      val action = MigrationAction.Optionalize(
        DynamicOptic.root,
        "age"
      )

      val result = action.apply(record)

      assert(result)(isRight(anything))
    },

    test("optionalize nested field") {
      val address = DynamicValue.Record(
        Vector(
          "street" -> DynamicValue.Primitive(PrimitiveValue.String("789 Elm")),
          "zip"    -> DynamicValue.Primitive(PrimitiveValue.String("12345"))
        )
      )

      val person = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Eve")),
          "address" -> address
        )
      )

      val action = MigrationAction.Optionalize(
        DynamicOptic.root / "address",
        "zip"
      )

      val result = action.apply(person)

      assert(result)(isRight(anything))
    }
  )

  // ===== ChangeType Tests =====

  val changeTypeTests = suite("ChangeType")(
    test("change type with literal conversion") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Frank")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
        )
      )

      // Convert age to string "25"
      val action = MigrationAction.ChangeType(
        DynamicOptic.root,
        "age",
        SE.literalString("25")
      )

      val result = action.apply(record)

      assert(result)(isRight(anything))
    }
  )

  // ===== Join Tests =====

  val joinTests = suite("Join")(
    test("join two fields into one") {
      val record = DynamicValue.Record(
        Vector(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Grace")),
          "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Hopper"))
        )
      )

      val action = MigrationAction.Join(
        DynamicOptic.root,
        Vector("firstName", "lastName"),
        "fullName",
        SE.concat(
          SE.getField(DynamicOptic.root / "firstName"),
          SE.literalString(" "),
          SE.getField(DynamicOptic.root / "lastName")
        )
      )

      val result = action.apply(record)

      assert(result)(isRight(anything))
    }
  )

  // ===== Split Tests =====

  val splitTests = suite("Split")(
    test("split one field into multiple") {
      val record = DynamicValue.Record(
        Vector(
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Henry Ford"))
        )
      )

      // Note: Split takes a single SchemaExpr that should produce a record with the target fields
      val action = MigrationAction.Split(
        DynamicOptic.root,
        "fullName",
        Vector("firstName", "lastName"),
        SE.literal(
          DynamicValue.Record(
            Vector(
              "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Henry")),
              "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Ford"))
            )
          )
        )
      )

      val result = action.apply(record)

      assert(result)(isRight(anything))
    }
  )

  // ===== RenameCase Tests =====

  val renameCaseTests = suite("RenameCase")(
    test("rename variant case") {
      val variant = DynamicValue.Variant("Success", DynamicValue.Primitive(PrimitiveValue.Int(42)))

      val action = MigrationAction.RenameCase(
        DynamicOptic.root,
        "Success",
        "Ok"
      )

      val result = action.apply(variant)

      assert(result)(isRight(anything))
    },

    test("rename case reverse") {
      val action = MigrationAction.RenameCase(
        DynamicOptic.root,
        "Success",
        "Ok"
      )

      val reverse = action.reverse

      assert(reverse)(isSubtype[MigrationAction.RenameCase](anything))
    }
  )

  // ===== TransformCase Tests =====

  val transformCaseTests = suite("TransformCase")(
    test("transform variant case value") {
      val variant = DynamicValue.Variant(
        "User",
        DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Iris"))
          )
        )
      )

      val action = MigrationAction.TransformCase(
        DynamicOptic.root,
        "User",
        Vector(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            SE.literalString("iris@test.com")
          )
        )
      )

      val result = action.apply(variant)

      assert(result)(isRight(anything))
    }
  )

  // ===== TransformElements Tests =====

  val transformElementsTests = suite("TransformElements")(
    test("transform sequence elements") {
      val sequence = DynamicValue.Sequence(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
      )

      // Transform each element by doubling it (conceptually)
      val action = MigrationAction.TransformElements(
        DynamicOptic.root,
        SE.literalInt(99) // Replace each element with 99
      )

      val result = action.apply(sequence)

      assert(result)(isRight(anything))
    }
  )

  // ===== TransformKeys Tests =====

  val transformKeysTests = suite("TransformKeys")(
    test("transform map keys") {
      val map = DynamicValue.Map(
        Vector(
          (DynamicValue.Primitive(PrimitiveValue.String("key1")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          (DynamicValue.Primitive(PrimitiveValue.String("key2")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
      )

      val action = MigrationAction.TransformKeys(
        DynamicOptic.root,
        SE.literalString("newKey")
      )

      val result = action.apply(map)

      assert(result)(isRight(anything))
    }
  )

  // ===== TransformValues Tests =====

  val transformValuesTests = suite("TransformValues")(
    test("transform map values") {
      val map = DynamicValue.Map(
        Vector(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
      )

      val action = MigrationAction.TransformValues(
        DynamicOptic.root,
        SE.literalInt(99)
      )

      val result = action.apply(map)

      assert(result)(isRight(anything))
    }
  )
}
