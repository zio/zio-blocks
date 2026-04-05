package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, assert}

object MigrationActionSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionSpec")(
    suite("AddField")(
      test("adds a field to root record") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "age",
          DynamicValue.Primitive(PrimitiveValue.Int(25))
        )
        val input    = DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        val expected = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
          )
        )
        assert(action(input))(isRight(equalTo(expected)))
      },
      test("fails on non-record") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "field",
          DynamicValue.Null
        )
        val input = DynamicValue.Primitive(PrimitiveValue.String("not a record"))
        assert(action(input).isLeft)(equalTo(true))
      },
      test("reverse is DropField") {
        val action  = MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val reverse = action.reverse
        assert(reverse.isInstanceOf[MigrationAction.DropField])(equalTo(true))
      }
    ),
    suite("DropField")(
      test("removes a field from root record") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          "age",
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val input = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val expected = DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        assert(action(input))(isRight(equalTo(expected)))
      },
      test("fails when field does not exist") {
        val action = MigrationAction.DropField(DynamicOptic.root, "nonexistent", DynamicValue.Null)
        val input  = DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        assert(action(input).isLeft)(equalTo(true))
      }
    ),
    suite("Rename")(
      test("renames a field") {
        val action   = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        val input    = DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        val expected = DynamicValue.Record(Chunk(("fullName", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        assert(action(input))(isRight(equalTo(expected)))
      },
      test("fails when field doesn't exist") {
        val action = MigrationAction.Rename(DynamicOptic.root, "missing", "newName")
        val input  = DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        assert(action(input).isLeft)(equalTo(true))
      },
      test("fails on non-record") {
        val action = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        val input  = DynamicValue.Primitive(PrimitiveValue.String("not a record"))
        assert(action(input).isLeft)(equalTo(true))
      },
      test("reverse swaps from and to") {
        val action  = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val reverse = action.reverse.asInstanceOf[MigrationAction.Rename]
        assert(reverse.from)(equalTo("b")) && assert(reverse.to)(equalTo("a"))
      }
    ),
    suite("TransformValue")(
      test("sets a value at a path") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root.field("age"),
          DynamicValue.Primitive(PrimitiveValue.Int(99)),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val input  = DynamicValue.Record(Chunk(("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))))
        val result = action(input)
        assert(result)(isRight) &&
        assert(result.toOption.get.fields.head._2)(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(99))))
      },
      test("reverse swaps transform and reverseTransform") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root.field("x"),
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val reverse = action.reverse.asInstanceOf[MigrationAction.TransformValue]
        assert(reverse.transform)(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(2)))) &&
        assert(reverse.reverseTransform)(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(1))))
      }
    ),
    suite("Mandate")(
      test("converts None variant to default") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root.field("opt"),
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        )
        val input = DynamicValue.Record(
          Chunk(("opt", DynamicValue.Variant("None", DynamicValue.Record.empty)))
        )
        val result = action(input)
        assert(result)(isRight) &&
        assert(result.toOption.get.fields.head._2)(
          equalTo(DynamicValue.Primitive(PrimitiveValue.String("default")))
        )
      },
      test("unwraps Some variant") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root.field("opt"),
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        )
        val inner  = DynamicValue.Primitive(PrimitiveValue.String("value"))
        val input  = DynamicValue.Record(Chunk(("opt", DynamicValue.Variant("Some", inner))))
        val result = action(input)
        assert(result)(isRight) &&
        assert(result.toOption.get.fields.head._2)(equalTo(inner))
      },
      test("reverse is Optionalize") {
        val action = MigrationAction.Mandate(DynamicOptic.root.field("x"), DynamicValue.Null)
        assert(action.reverse.isInstanceOf[MigrationAction.Optionalize])(equalTo(true))
      }
    ),
    suite("Optionalize")(
      test("wraps value in Some variant") {
        val action = MigrationAction.Optionalize(DynamicOptic.root.field("x"), DynamicValue.Null)
        val inner  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val input  = DynamicValue.Record(Chunk(("x", inner)))
        val result = action(input)
        assert(result)(isRight) &&
        assert(result.toOption.get.fields.head._2)(equalTo(DynamicValue.Variant("Some", inner)))
      },
      test("leaves Null as Null") {
        val action = MigrationAction.Optionalize(DynamicOptic.root.field("x"), DynamicValue.Null)
        val input  = DynamicValue.Record(Chunk(("x", DynamicValue.Null)))
        val result = action(input)
        assert(result)(isRight) &&
        assert(result.toOption.get.fields.head._2)(equalTo(DynamicValue.Null))
      },
      test("reverse is Mandate with stored default") {
        val dflt    = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val action  = MigrationAction.Optionalize(DynamicOptic.root.field("x"), dflt)
        val reverse = action.reverse.asInstanceOf[MigrationAction.Mandate]
        assert(reverse.default)(equalTo(dflt))
      }
    ),
    suite("ChangeType")(
      test("replaces value at path") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root.field("val"),
          DynamicValue.Primitive(PrimitiveValue.String("42")),
          DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
        val input  = DynamicValue.Record(Chunk(("val", DynamicValue.Primitive(PrimitiveValue.Int(42)))))
        val result = action(input)
        assert(result)(isRight) &&
        assert(result.toOption.get.fields.head._2)(
          equalTo(DynamicValue.Primitive(PrimitiveValue.String("42")))
        )
      },
      test("reverse swaps converter and reverseConverter") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root.field("x"),
          DynamicValue.Primitive(PrimitiveValue.String("1")),
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val reverse = action.reverse.asInstanceOf[MigrationAction.ChangeType]
        assert(reverse.converter)(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(1))))
      }
    ),
    suite("RenameCase")(
      test("renames a variant case") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val input    = DynamicValue.Variant("Old", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val expected = DynamicValue.Variant("New", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assert(action(input))(isRight(equalTo(expected)))
      },
      test("fails when case doesn't match") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "Nonexistent", "New")
        val input  = DynamicValue.Variant("Other", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assert(action(input).isLeft)(equalTo(true))
      },
      test("reverse swaps from and to") {
        val action  = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val reverse = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assert(reverse.from)(equalTo("New")) && assert(reverse.to)(equalTo("Old"))
      }
    ),
    suite("TransformCase")(
      test("transforms case value with nested actions") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "Person",
          Vector(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
        )
        val input = DynamicValue.Variant(
          "Person",
          DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        )
        val expected = DynamicValue.Variant(
          "Person",
          DynamicValue.Record(Chunk(("fullName", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        )
        assert(action(input))(isRight(equalTo(expected)))
      },
      test("skips non-matching cases") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "Person",
          Vector(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
        )
        val input = DynamicValue.Variant(
          "Animal",
          DynamicValue.Record(Chunk(("species", DynamicValue.Primitive(PrimitiveValue.String("Dog")))))
        )
        assert(action(input))(isRight(equalTo(input)))
      },
      test("reverse reverses nested actions") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "X",
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", DynamicValue.Null)
          )
        )
        val reverse = action.reverse.asInstanceOf[MigrationAction.TransformCase]
        assert(reverse.actions.length)(equalTo(2)) &&
        assert(reverse.actions.head.isInstanceOf[MigrationAction.DropField])(equalTo(true)) &&
        assert(reverse.actions(1).isInstanceOf[MigrationAction.Rename])(equalTo(true))
      }
    ),
    suite("TransformElements")(
      test("transforms each element in a sequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Vector(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
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
        assert(action(input))(isRight(equalTo(expected)))
      },
      test("fails on non-sequence") {
        val action = MigrationAction.TransformElements(DynamicOptic.root, Vector.empty)
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assert(action(input).isLeft)(equalTo(true))
      }
    ),
    suite("TransformKeys")(
      test("transforms each key in a map") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "extra", DynamicValue.Null)
          )
        )
        val key1  = DynamicValue.Record(Chunk(("id", DynamicValue.Primitive(PrimitiveValue.Int(1)))))
        val val1  = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val input = DynamicValue.Map(Chunk((key1, val1)))

        val result = action(input)
        assert(result.isRight)(equalTo(true))
      },
      test("fails on non-map") {
        val action = MigrationAction.TransformKeys(DynamicOptic.root, Vector.empty)
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assert(action(input).isLeft)(equalTo(true))
      }
    ),
    suite("TransformValues")(
      test("transforms each value in a map") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "extra", DynamicValue.Null)
          )
        )
        val key1  = DynamicValue.Primitive(PrimitiveValue.String("key"))
        val val1  = DynamicValue.Record(Chunk(("data", DynamicValue.Primitive(PrimitiveValue.Int(42)))))
        val input = DynamicValue.Map(Chunk((key1, val1)))

        val result = action(input)
        assert(result.isRight)(equalTo(true))
      },
      test("fails on non-map") {
        val action = MigrationAction.TransformValues(DynamicOptic.root, Vector.empty)
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assert(action(input).isLeft)(equalTo(true))
      }
    ),
    suite("Join")(
      test("sets combiner value at target path") {
        val action = MigrationAction.Join(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
        )
        val input = DynamicValue.Record(
          Chunk(
            ("first", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("last", DynamicValue.Primitive(PrimitiveValue.String("Doe"))),
            ("fullName", DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = action(input)
        assert(result.isRight)(equalTo(true))
      },
      test("reverse is Split") {
        val action = MigrationAction.Join(
          DynamicOptic.root.field("x"),
          Vector(DynamicOptic.root.field("a")),
          DynamicValue.Null
        )
        assert(action.reverse.isInstanceOf[MigrationAction.Split])(equalTo(true))
      }
    ),
    suite("Split")(
      test("sets splitter value at source path") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          DynamicValue.Primitive(PrimitiveValue.String(""))
        )
        val input = DynamicValue.Record(
          Chunk(
            ("fullName", DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
          )
        )
        val result = action(input)
        assert(result.isRight)(equalTo(true))
      },
      test("reverse is Join") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("x"),
          Vector(DynamicOptic.root.field("a")),
          DynamicValue.Null
        )
        assert(action.reverse.isInstanceOf[MigrationAction.Join])(equalTo(true))
      }
    )
  )
}
