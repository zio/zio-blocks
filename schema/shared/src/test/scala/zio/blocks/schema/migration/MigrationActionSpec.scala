package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for individual `MigrationAction` operations.
 *
 * Organized by action type: Record, Enum/Variant, Collection.
 */
object MigrationActionSpec extends ZIOSpecDefault {

  private val simpleRecord = DynamicValue.Record(Chunk(
    ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
    ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
  ))

  private val nestedRecord = DynamicValue.Record(Chunk(
    ("user", DynamicValue.Record(Chunk(
      ("name", DynamicValue.Primitive(PrimitiveValue.String("Bob"))),
      ("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
    ))),
    ("role", DynamicValue.Primitive(PrimitiveValue.String("admin")))
  ))

  private val sampleVariant = DynamicValue.Variant("Circle", DynamicValue.Record(Chunk(
    ("radius", DynamicValue.Primitive(PrimitiveValue.Double(5.0)))
  )))

  private val sampleSequence = DynamicValue.Sequence(Chunk(
    DynamicValue.Record(Chunk(("id", DynamicValue.Primitive(PrimitiveValue.Int(1))))),
    DynamicValue.Record(Chunk(("id", DynamicValue.Primitive(PrimitiveValue.Int(2)))))
  ))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionSpec")(
    recordActions,
    variantActions,
    collectionActions,
    identityAction
  )

  private val recordActions = suite("Record Actions")(
    suite("AddField")(
      test("adds field to flat record") {
        val action = MigrationAction.AddField(
          DynamicOptic.root, "email",
          DynamicValue.Primitive(PrimitiveValue.String("alice@example.com"))
        )
        val result = action(simpleRecord)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(record.fields.exists(f => f._1 == "email"))
        assertTrue(record.fields.length == 3)
      },
      test("adds field to nested record") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("user"), "email",
          DynamicValue.Primitive(PrimitiveValue.String("bob@example.com"))
        )
        val result = action(nestedRecord)
        assertTrue(result.isRight)
      },
      test("errors when field already exists at root") {
        val action = MigrationAction.AddField(
          DynamicOptic.root, "name",
          DynamicValue.Primitive(PrimitiveValue.String("duplicate"))
        )
        val result = action(simpleRecord)
        assertTrue(result.isLeft)
      },
      test("reverse of AddField is DropField") {
        val action = MigrationAction.AddField(
          DynamicOptic.root, "email",
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        )
        val rev = action.reverse
        assertTrue(rev.isDefined)
        assertTrue(rev.get.isInstanceOf[MigrationAction.DropField])
      }
    ),
    suite("DropField")(
      test("removes field from record") {
        val action = MigrationAction.DropField(DynamicOptic.root, "age", None)
        val result = action(simpleRecord)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(!record.fields.exists(_._1 == "age"))
        assertTrue(record.fields.length == 1)
      },
      test("irreversible without default") {
        val action = MigrationAction.DropField(DynamicOptic.root, "age", None)
        assertTrue(action.reverse.isEmpty)
      },
      test("reversible with default") {
        val default = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val action  = MigrationAction.DropField(DynamicOptic.root, "age", Some(default))
        assertTrue(action.reverse.isDefined)
      }
    ),
    suite("RenameField")(
      test("renames field in record") {
        val action = MigrationAction.RenameField(DynamicOptic.root, "name", "fullName")
        val result = action(simpleRecord)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(record.fields.exists(_._1 == "fullName"))
        assertTrue(!record.fields.exists(_._1 == "name"))
      },
      test("errors when source field missing") {
        val action = MigrationAction.RenameField(DynamicOptic.root, "nonexistent", "something")
        val result = action(simpleRecord)
        assertTrue(result.isLeft)
      },
      test("errors when target name already exists") {
        val action = MigrationAction.RenameField(DynamicOptic.root, "name", "age")
        val result = action(simpleRecord)
        assertTrue(result.isLeft)
      },
      test("reverse of rename swaps old and new") {
        val action = MigrationAction.RenameField(DynamicOptic.root, "name", "fullName")
        val rev    = action.reverse.get.asInstanceOf[MigrationAction.RenameField]
        assertTrue(rev.oldName == "fullName")
        assertTrue(rev.newName == "name")
      }
    ),
    suite("SetFieldValue")(
      test("replaces field value") {
        val action = MigrationAction.SetFieldValue(
          DynamicOptic.root, "age",
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        val result = action(simpleRecord)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val ageVal = record.fields.find(_._1 == "age").map(_._2)
        assertTrue(ageVal == Some(DynamicValue.Primitive(PrimitiveValue.Int(99))))
      },
      test("is irreversible") {
        val action = MigrationAction.SetFieldValue(
          DynamicOptic.root, "age",
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        assertTrue(action.reverse.isEmpty)
      }
    ),
    suite("ChangeFieldType")(
      test("maps field values according to mapping") {
        val mapping = Chunk(
          (DynamicValue.Primitive(PrimitiveValue.Int(30)), DynamicValue.Primitive(PrimitiveValue.String("thirty")))
        )
        val action = MigrationAction.ChangeFieldType(DynamicOptic.root, "age", mapping)
        val result = action(simpleRecord)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val ageVal = record.fields.find(_._1 == "age").map(_._2)
        assertTrue(ageVal == Some(DynamicValue.Primitive(PrimitiveValue.String("thirty"))))
      },
      test("unmapped values pass through unchanged") {
        val mapping = Chunk(
          (DynamicValue.Primitive(PrimitiveValue.Int(99)), DynamicValue.Primitive(PrimitiveValue.String("ninety-nine")))
        )
        val action = MigrationAction.ChangeFieldType(DynamicOptic.root, "age", mapping)
        val result = action(simpleRecord)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val ageVal = record.fields.find(_._1 == "age").map(_._2)
        assertTrue(ageVal == Some(DynamicValue.Primitive(PrimitiveValue.Int(30)))) // unchanged
      },
      test("reverse swaps mapping direction") {
        val mapping = Chunk(
          (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.String("one")))
        )
        val action = MigrationAction.ChangeFieldType(DynamicOptic.root, "age", mapping)
        val rev    = action.reverse.get.asInstanceOf[MigrationAction.ChangeFieldType]
        assertTrue(rev.valueMapping.head._1 == DynamicValue.Primitive(PrimitiveValue.String("one")))
        assertTrue(rev.valueMapping.head._2 == DynamicValue.Primitive(PrimitiveValue.Int(1)))
      }
    )
  )

  private val variantActions = suite("Variant Actions")(
    suite("RenameCase")(
      test("renames matching case") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "Circle", "Ellipse")
        val result = action(sampleVariant)
        assertTrue(result.isRight)
        val variant = result.toOption.get.asInstanceOf[DynamicValue.Variant]
        assertTrue(variant.caseNameValue == "Ellipse")
      },
      test("non-matching case passes through") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "Square", "Rectangle")
        val result = action(sampleVariant)
        assertTrue(result.isRight)
        val variant = result.toOption.get.asInstanceOf[DynamicValue.Variant]
        assertTrue(variant.caseNameValue == "Circle") // unchanged
      },
      test("reverse swaps old and new case names") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "Circle", "Ellipse")
        val rev    = action.reverse.get.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(rev.oldCaseName == "Ellipse")
        assertTrue(rev.newCaseName == "Circle")
      }
    ),
    suite("TransformCase")(
      test("transforms matching case inner value") {
        val innerMigration = DynamicMigration(
          MigrationAction.RenameField(DynamicOptic.root, "radius", "r")
        )
        val action = MigrationAction.TransformCase(DynamicOptic.root, "Circle", innerMigration)
        val result = action(sampleVariant)
        assertTrue(result.isRight)
        val variant = result.toOption.get.asInstanceOf[DynamicValue.Variant]
        val inner   = variant.value.asInstanceOf[DynamicValue.Record]
        assertTrue(inner.fields.exists(_._1 == "r"))
        assertTrue(!inner.fields.exists(_._1 == "radius"))
      }
    )
  )

  private val collectionActions = suite("Collection Actions")(
    suite("TransformElements")(
      test("transforms each element in sequence") {
        val elemMigration = DynamicMigration(
          MigrationAction.RenameField(DynamicOptic.root, "id", "identifier")
        )
        val action = MigrationAction.TransformElements(DynamicOptic.root, elemMigration)
        val result = action(sampleSequence)
        assertTrue(result.isRight)
        val seq = result.toOption.get.asInstanceOf[DynamicValue.Sequence]
        assertTrue(seq.elements.length == 2)
        val first = seq.elements(0).asInstanceOf[DynamicValue.Record]
        assertTrue(first.fields.exists(_._1 == "identifier"))
      }
    )
  )

  private val identityAction = suite("Identity Action")(
    test("identity returns value unchanged") {
      val result = MigrationAction.Identity(simpleRecord)
      assertTrue(result == Right(simpleRecord))
    },
    test("identity reverse is identity") {
      assertTrue(MigrationAction.Identity.reverse == Some(MigrationAction.Identity))
    }
  )
}
