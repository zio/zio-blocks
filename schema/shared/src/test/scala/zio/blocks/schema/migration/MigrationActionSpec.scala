package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.test._

/**
 * Comprehensive tests for MigrationAction covering reverse, prefixPath,
 * execute, and schema serialization/deserialization across all action types.
 */
object MigrationActionSpec extends ZIOSpecDefault {

  // Helper factory methods
  private def str(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def int(i: Int): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Int(i))

  def spec = suite("MigrationActionSpec")(
    suite("AddField")(
      test("reverse creates DropField") {
        val action  = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal(str("default")))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.DropField])
      },
      test("prefixPath updates path correctly") {
        val action   = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal(str("default")))
        val prefixed = action.prefixPath(DynamicOptic.root.field("user"))
        assertTrue(prefixed.at.toScalaString.contains("user"))
      },
      test("schema round-trip serialization") {
        val action    = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal(str("default")))
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("DropField")(
      test("reverse creates AddField") {
        val action  = MigrationAction.DropField(DynamicOptic.root, "email", Resolved.Literal(str("default")))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AddField])
      },
      test("prefixPath updates path correctly") {
        val action   = MigrationAction.DropField(DynamicOptic.root, "email", Resolved.Literal(str("default")))
        val prefixed = action.prefixPath(DynamicOptic.root.field("user"))
        assertTrue(prefixed.at.toScalaString.contains("user"))
      },
      test("schema round-trip serialization") {
        val action    = MigrationAction.DropField(DynamicOptic.root, "email", Resolved.Literal(str("default")))
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("Rename")(
      test("reverse swaps from and to") {
        val action  = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        val reverse = action.reverse
        reverse match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "newName", to == "oldName")
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates path correctly") {
        val action   = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val prefixed = action.prefixPath(DynamicOptic.root.field("user"))
        assertTrue(prefixed.at.toScalaString.contains("user"))
      },
      test("schema round-trip serialization") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("TransformValue")(
      test("reverse swaps forward and reverse transforms") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "value",
          Resolved.Literal(int(1)),
          Resolved.Literal(int(2))
        )
        val reverse = action.reverse
        reverse match {
          case MigrationAction.TransformValue(_, _, fwd, rev) =>
            assertTrue(fwd == Resolved.Literal(int(2)), rev == Resolved.Literal(int(1)))
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates path correctly") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "value",
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("data"))
        assertTrue(prefixed.at.toScalaString.contains("data"))
      },
      test("schema round-trip serialization") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "value",
          Resolved.Identity,
          Resolved.Identity
        )
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("Mandate")(
      test("reverse creates Optionalize") {
        val action  = MigrationAction.Mandate(DynamicOptic.root, "status", Resolved.Literal(str("active")))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Optionalize])
      },
      test("prefixPath updates path correctly") {
        val action   = MigrationAction.Mandate(DynamicOptic.root, "status", Resolved.Literal(str("active")))
        val prefixed = action.prefixPath(DynamicOptic.root.field("record"))
        assertTrue(prefixed.at.toScalaString.contains("record"))
      },
      test("schema round-trip serialization") {
        val action    = MigrationAction.Mandate(DynamicOptic.root, "status", Resolved.Literal(str("active")))
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("Optionalize")(
      test("reverse creates Mandate with Fail") {
        val action  = MigrationAction.Optionalize(DynamicOptic.root, "status")
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Mandate])
      },
      test("prefixPath updates path correctly") {
        val action   = MigrationAction.Optionalize(DynamicOptic.root, "status")
        val prefixed = action.prefixPath(DynamicOptic.root.field("record"))
        assertTrue(prefixed.at.toScalaString.contains("record"))
      },
      test("schema round-trip serialization") {
        val action    = MigrationAction.Optionalize(DynamicOptic.root, "status")
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("ChangeType")(
      test("reverse swaps converters") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Literal(int(42)),
          Resolved.Identity
        )
        val reverse = action.reverse
        reverse match {
          case MigrationAction.ChangeType(_, _, fwd, rev) =>
            assertTrue(fwd == Resolved.Identity, rev == Resolved.Literal(int(42)))
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates path correctly") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("data"))
        assertTrue(prefixed.at.toScalaString.contains("data"))
      },
      test("schema round-trip serialization") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Identity,
          Resolved.Identity
        )
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("RenameCase")(
      test("reverse swaps from and to case names") {
        val action  = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val reverse = action.reverse
        reverse match {
          case MigrationAction.RenameCase(_, from, to) =>
            assertTrue(from == "NewCase", to == "OldCase")
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates path correctly") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val prefixed = action.prefixPath(DynamicOptic.root.field("enum"))
        assertTrue(prefixed.at.toScalaString.contains("enum"))
      },
      test("schema round-trip serialization") {
        val action    = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("TransformCase")(
      test("reverse preserves case and reverses inner actions") {
        val innerAction = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal(str("val")))
        val action      = MigrationAction.TransformCase(DynamicOptic.root, "SomeCase", Chunk(innerAction))
        val reverse     = action.reverse
        reverse match {
          case MigrationAction.TransformCase(_, caseName, actions) =>
            assertTrue(caseName == "SomeCase", actions.nonEmpty)
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates path correctly") {
        val innerAction = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val action      = MigrationAction.TransformCase(DynamicOptic.root, "Case", Chunk(innerAction))
        val prefixed    = action.prefixPath(DynamicOptic.root.field("variant"))
        assertTrue(prefixed.at.toScalaString.contains("variant"))
      },
      test("schema round-trip serialization") {
        val innerAction = MigrationAction.Rename(DynamicOptic.root, "x", "y")
        val action      = MigrationAction.TransformCase(DynamicOptic.root, "Case", Chunk(innerAction))
        val dv          = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip   = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("TransformElements")(
      test("reverse swaps element and reverse transforms") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Literal(int(1)),
          Resolved.Literal(int(2))
        )
        val reverse = action.reverse
        reverse match {
          case MigrationAction.TransformElements(_, elem, rev) =>
            assertTrue(elem == Resolved.Literal(int(2)), rev == Resolved.Literal(int(1)))
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates path correctly") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("items"))
        assertTrue(prefixed.at.toScalaString.contains("items"))
      },
      test("schema round-trip serialization") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("TransformKeys")(
      test("reverse swaps key and reverse transforms") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Literal(str("a")),
          Resolved.Literal(str("b"))
        )
        val reverse = action.reverse
        reverse match {
          case MigrationAction.TransformKeys(_, key, rev) =>
            assertTrue(key == Resolved.Literal(str("b")), rev == Resolved.Literal(str("a")))
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates path correctly") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("map"))
        assertTrue(prefixed.at.toScalaString.contains("map"))
      },
      test("schema round-trip serialization") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("TransformValues")(
      test("reverse swaps value and reverse transforms") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Literal(int(10)),
          Resolved.Literal(int(20))
        )
        val reverse = action.reverse
        reverse match {
          case MigrationAction.TransformValues(_, val1, val2) =>
            assertTrue(val1 == Resolved.Literal(int(20)), val2 == Resolved.Literal(int(10)))
          case _ => assertTrue(false)
        }
      },
      test("prefixPath updates path correctly") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("record"))
        assertTrue(prefixed.at.toScalaString.contains("record"))
      },
      test("schema round-trip serialization") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("Join")(
      test("reverse creates Split with swapped expressions") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Concat(Vector(Resolved.Identity), " "),
          Resolved.Identity
        )
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Split])
      },
      test("prefixPath updates all paths") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("user"))
        prefixed match {
          case MigrationAction.Join(at, _, srcPaths, _, _) =>
            assertTrue(
              at.toScalaString.contains("user"),
              srcPaths.forall(_.toScalaString.contains("user"))
            )
          case _ => assertTrue(false)
        }
      },
      test("schema round-trip serialization") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("first")),
          Resolved.Identity,
          Resolved.Identity
        )
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("Split")(
      test("reverse creates Join with swapped expressions") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Identity,
          Resolved.SplitString(Resolved.Identity, " ", 0)
        )
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Join])
      },
      test("prefixPath updates all paths") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("user"))
        prefixed match {
          case MigrationAction.Split(at, _, tgtPaths, _, _) =>
            assertTrue(
              at.toScalaString.contains("user"),
              tgtPaths.forall(_.toScalaString.contains("user"))
            )
          case _ => assertTrue(false)
        }
      },
      test("schema round-trip serialization") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("first")),
          Resolved.Identity,
          Resolved.Identity
        )
        val dv        = Schema[MigrationAction].toDynamicValue(action)
        val roundTrip = Schema[MigrationAction].fromDynamicValue(dv)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("execute method")(
      test("AddField execute adds field to record") {
        val action = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal(str("test@example.com")))
        val record = DynamicValue.Record("name" -> str("John"))
        val result = action.execute(record)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "email"))
          case _ => assertTrue(false)
        }
      },
      test("DropField execute removes field from record") {
        val action = MigrationAction.DropField(DynamicOptic.root, "email", Resolved.Literal(str("")))
        val record = DynamicValue.Record("name" -> str("John"), "email" -> str("test@example.com"))
        val result = action.execute(record)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(!fields.exists(_._1 == "email"))
          case _ => assertTrue(false)
        }
      },
      test("Rename execute renames field") {
        val action = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        val record = DynamicValue.Record("name" -> str("John"))
        val result = action.execute(record)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(
              fields.exists(_._1 == "fullName"),
              !fields.exists(_._1 == "name")
            )
          case _ => assertTrue(false)
        }
      },
      test("Optionalize execute wraps in Some") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "age")
        val record = DynamicValue.Record("age" -> int(30))
        val result = action.execute(record)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "age").map(_._2) match {
              case Some(DynamicValue.Variant("Some", _)) => assertTrue(true)
              case _                                     => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("RenameCase execute renames variant case") {
        val action  = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val variant = DynamicValue.Variant("OldCase", DynamicValue.Record("value" -> int(1)))
        val result  = action.execute(variant)
        result match {
          case Right(DynamicValue.Variant(name, _)) =>
            assertTrue(name == "NewCase")
          case _ => assertTrue(false)
        }
      },
      test("execute on non-matching value fails gracefully") {
        val action    = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal(str("val")))
        val primitive = int(42)
        val result    = action.execute(primitive)
        assertTrue(result.isLeft)
      }
    ),
    suite("double reverse property")(
      test("AddField double reverse returns semantically equivalent action") {
        val action = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal(str("default")))
        val result = action.reverse.reverse
        result match {
          case MigrationAction.AddField(_, fieldName, _) =>
            assertTrue(fieldName == "email")
          case _ => assertTrue(false)
        }
      },
      test("Rename double reverse returns original action") {
        val action = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val result = action.reverse.reverse
        result match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "a", to == "b")
          case _ => assertTrue(false)
        }
      },
      test("RenameCase double reverse returns original action") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val result = action.reverse.reverse
        result match {
          case MigrationAction.RenameCase(_, from, to) =>
            assertTrue(from == "OldCase", to == "NewCase")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("nested path prefixing")(
      test("deeply nested prefix updates all path components") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("inner"),
          "newField",
          Resolved.Literal(str("value"))
        )
        val prefix   = DynamicOptic.root.field("outer").field("middle")
        val prefixed = action.prefixPath(prefix)
        assertTrue(prefixed.at.toScalaString.contains("outer"))
      },
      test("prefixPath preserves field-level optic") {
        val action   = MigrationAction.DropField(DynamicOptic.root.field("data"), "old", Resolved.Literal(str("")))
        val prefixed = action.prefixPath(DynamicOptic.root.field("nested"))
        assertTrue(prefixed.at.toScalaString.contains("nested"))
      }
    ),
    suite("schema error cases")(
      test("invalid DynamicValue decoding fails with error") {
        val invalidDV = DynamicValue.Primitive(PrimitiveValue.String("invalid"))
        val result    = Schema[MigrationAction].fromDynamicValue(invalidDV)
        assertTrue(result.isLeft)
      },
      test("Variant with unknown case name fails with error") {
        val unknownVariant =
          DynamicValue.Variant("UnknownAction", DynamicValue.Record())
        val result = Schema[MigrationAction].fromDynamicValue(unknownVariant)
        assertTrue(result.isLeft)
      }
    )
  )
}
