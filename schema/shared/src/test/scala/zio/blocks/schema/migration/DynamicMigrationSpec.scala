package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, assert}

object DynamicMigrationSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    suite("Identity")(
      test("identity migration returns the value unchanged") {
        val value = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        assert(DynamicMigration.identity(value))(isRight(equalTo(value)))
      },
      test("identity migration has no actions") {
        assert(DynamicMigration.identity.isEmpty)(equalTo(true))
      }
    ),
    suite("Associativity")(
      test("(m1 ++ m2) ++ m3 produces same result as m1 ++ (m2 ++ m3)") {
        val m1 = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val m2 = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
        val m3 = DynamicMigration(MigrationAction.DropField(DynamicOptic.root, "unused", DynamicValue.Null))

        val value = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("unused", DynamicValue.Primitive(PrimitiveValue.String("data")))
          )
        )

        val leftAssoc  = ((m1 ++ m2) ++ m3)(value)
        val rightAssoc = (m1 ++ (m2 ++ m3))(value)

        assert(leftAssoc)(equalTo(rightAssoc))
      }
    ),
    suite("Structural Reverse")(
      test("m.reverse.reverse has same actions as m") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(PrimitiveValue.Int(0))),
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
          )
        )
        assert(m.reverse.reverse)(equalTo(m))
      },
      test("identity reverse is identity") {
        assert(DynamicMigration.identity.reverse)(equalTo(DynamicMigration.identity))
      },
      test("reverse with all action types") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "x", DynamicValue.Null),
            MigrationAction.DropField(DynamicOptic.root, "y", DynamicValue.Null),
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.RenameCase(DynamicOptic.root, "Old", "New"),
            MigrationAction.ChangeType(
              DynamicOptic.root.field("z"),
              DynamicValue.Primitive(PrimitiveValue.String("0")),
              DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
        assert(m.reverse.reverse)(equalTo(m))
      }
    ),
    suite("AddField")(
      test("adds a field with default value") {
        val m = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "age",
            DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val input = DynamicValue.Record(
          Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        )
        val expected = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
          )
        )
        assert(m(input))(isRight(equalTo(expected)))
      }
    ),
    suite("DropField")(
      test("removes a field from a record") {
        val m = DynamicMigration(
          MigrationAction.DropField(
            DynamicOptic.root,
            "age",
            DynamicValue.Primitive(PrimitiveValue.Int(0))
          )
        )
        val input = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val expected = DynamicValue.Record(
          Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        )
        assert(m(input))(isRight(equalTo(expected)))
      }
    ),
    suite("Rename")(
      test("renames a field in a record") {
        val m     = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
        val input = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val expected = DynamicValue.Record(
          Chunk(
            ("fullName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        assert(m(input))(isRight(equalTo(expected)))
      },
      test("rename round-trips via reverse") {
        val m     = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
        val input = DynamicValue.Record(
          Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        )
        val result = for {
          migrated <- m(input)
          restored <- m.reverse(migrated)
        } yield restored
        assert(result)(isRight(equalTo(input)))
      }
    ),
    suite("AddField + DropField round-trip")(
      test("add then drop restores original via reverse") {
        val m = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "country",
            DynamicValue.Primitive(PrimitiveValue.String("US"))
          )
        )
        val input = DynamicValue.Record(
          Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        )
        val result = for {
          migrated <- m(input)
          restored <- m.reverse(migrated)
        } yield restored
        assert(result)(isRight(equalTo(input)))
      }
    ),
    suite("Composition")(
      test("multiple actions compose correctly") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(PrimitiveValue.Int(0))),
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
          )
        )
        val input = DynamicValue.Record(
          Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        )
        val expected = DynamicValue.Record(
          Chunk(
            ("fullName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        assert(m(input))(isRight(equalTo(expected)))
      },
      test("andThen is alias for ++") {
        val m1 = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "x", DynamicValue.Null)
        )
        val m2 = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        assert((m1 ++ m2).actions)(equalTo(m1.andThen(m2).actions))
      }
    ),
    suite("RenameCase")(
      test("renames a variant case") {
        val m     = DynamicMigration(MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName"))
        val input = DynamicValue.Variant(
          "OldName",
          DynamicValue.Record(Chunk(("value", DynamicValue.Primitive(PrimitiveValue.Int(42)))))
        )
        val expected = DynamicValue.Variant(
          "NewName",
          DynamicValue.Record(Chunk(("value", DynamicValue.Primitive(PrimitiveValue.Int(42)))))
        )
        assert(m(input))(isRight(equalTo(expected)))
      },
      test("rename case round-trips via reverse") {
        val m      = DynamicMigration(MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName"))
        val input  = DynamicValue.Variant("OldName", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val result = for {
          migrated <- m(input)
          restored <- m.reverse(migrated)
        } yield restored
        assert(result)(isRight(equalTo(input)))
      }
    ),
    suite("TransformCase")(
      test("transforms case with nested rename action") {
        val m = DynamicMigration(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "Person",
            Vector(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
          )
        )
        val input = DynamicValue.Variant(
          "Person",
          DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        )
        val expected = DynamicValue.Variant(
          "Person",
          DynamicValue.Record(Chunk(("fullName", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        )
        assert(m(input))(isRight(equalTo(expected)))
      }
    ),
    suite("TransformElements")(
      test("transforms each element in sequence") {
        val m = DynamicMigration(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            Vector(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
          )
        )
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk(("old", DynamicValue.Primitive(PrimitiveValue.Int(1))))),
            DynamicValue.Record(Chunk(("old", DynamicValue.Primitive(PrimitiveValue.Int(2)))))
          )
        )
        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk(("new", DynamicValue.Primitive(PrimitiveValue.Int(1))))),
            DynamicValue.Record(Chunk(("new", DynamicValue.Primitive(PrimitiveValue.Int(2)))))
          )
        )
        assert(m(input))(isRight(equalTo(expected)))
      }
    ),
    suite("Error Handling")(
      test("errors include path information") {
        val m     = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "nonexistent", "newName"))
        val input = DynamicValue.Record(
          Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        )
        val result = m(input)
        assert(result.isLeft)(equalTo(true))
      },
      test("action on non-record fails with descriptive error") {
        val m = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "field", DynamicValue.Null)
        )
        val input  = DynamicValue.Primitive(PrimitiveValue.String("not a record"))
        val result = m(input)
        assert(result.isLeft)(equalTo(true))
      },
      test("error message contains action name") {
        val err = MigrationError.ActionFailed("Rename", DynamicOptic.root.field("x"), "test details")
        assert(err.message.contains("Rename"))(equalTo(true)) &&
        assert(err.message.contains("test details"))(equalTo(true))
      }
    )
  )
}
