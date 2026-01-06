package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object MigrationSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("empty migration is identity") {
        val value = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        assertTrue(DynamicMigration.empty(value) == Right(value))
      },

      test("addField") {
        val input = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val expected = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(0))
        ))
        assertTrue(migration(input) == Right(expected))
      },

      test("dropField") {
        val input = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        ))
        val migration = DynamicMigration.single(MigrationAction.DropField(DynamicOptic.root, "age", None))
        val expected = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        assertTrue(migration(input) == Right(expected))
      },

      test("rename") {
        val input = DynamicValue.Record(Vector("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
        val expected = DynamicValue.Record(Vector("new" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(migration(input) == Right(expected))
      },

      test("transformValue") {
        val input = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(DynamicOptic.root, DynamicTransform.ToString, DynamicTransform.ParseInt)
        )
        assertTrue(migration(input) == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },

      test("renameCase") {
        val input = DynamicValue.Variant("Old", DynamicValue.Record(Vector.empty))
        val migration = DynamicMigration.single(MigrationAction.RenameCase(DynamicOptic.root, "Old", "New"))
        assertTrue(migration(input) == Right(DynamicValue.Variant("New", DynamicValue.Record(Vector.empty))))
      },

      test("composition") {
        val input = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
        val m2 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "c", DynamicValue.Primitive(PrimitiveValue.Int(2))))
        val expected = DynamicValue.Record(Vector(
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "c" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        ))
        assertTrue((m1 ++ m2)(input) == Right(expected))
      }
    ),

    suite("Laws")(
      test("associativity") {
        val m1 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "a", DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val m2 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "b", DynamicValue.Primitive(PrimitiveValue.Int(2))))
        val m3 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "c", DynamicValue.Primitive(PrimitiveValue.Int(3))))
        val input = DynamicValue.Record(Vector.empty)
        assertTrue(((m1 ++ m2) ++ m3)(input) == (m1 ++ (m2 ++ m3))(input))
      },

      test("structural reverse") {
        val m = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "x", DynamicValue.Primitive(PrimitiveValue.Int(1))),
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        )
        assertTrue(m.reverse.reverse.actions == m.actions)
      },

      test("semantic inverse for rename") {
        val input = DynamicValue.Record(Vector("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
        assertTrue(m(input).flatMap(m.reverse(_)) == Right(input))
      }
    ),

    suite("Errors")(
      test("dropField missing") {
        val input = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(MigrationAction.DropField(DynamicOptic.root, "b", None))
        assertTrue(migration(input).isLeft)
      },

      test("addField duplicate") {
        val input = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "a", DynamicValue.Primitive(PrimitiveValue.Int(2))))
        assertTrue(migration(input).isLeft)
      },

      test("transform type mismatch") {
        val input = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val migration = DynamicMigration.single(MigrationAction.TransformValue(DynamicOptic.root, DynamicTransform.ParseInt, DynamicTransform.ToString))
        assertTrue(migration(input).isLeft)
      }
    ),

    suite("Nested paths")(
      test("rename in nested record") {
        val input = DynamicValue.Record(Vector(
          "outer" -> DynamicValue.Record(Vector("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        ))
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("outer").field("old"), "new"))
        val expected = DynamicValue.Record(Vector(
          "outer" -> DynamicValue.Record(Vector("new" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        ))
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("DynamicTransform")(
      test("type conversions") {
        val intVal = DynamicValue.Primitive(PrimitiveValue.Int(42))
        
        def transform(t: DynamicTransform, v: DynamicValue) =
          DynamicMigration.single(MigrationAction.TransformValue(DynamicOptic.root, t, DynamicTransform.Identity))(v)
        
        assertTrue(
          transform(DynamicTransform.IntToLong, intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))) &&
          transform(DynamicTransform.IntToDouble, intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(42.0)))
        )
      }
    )
  )
}

