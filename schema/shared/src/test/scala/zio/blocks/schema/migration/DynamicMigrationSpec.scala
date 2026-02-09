package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  def spec = suite("DynamicMigrationSpec")(
    suite("DynamicMigration")(
      test("empty migration returns identity") {
        val migration = DynamicMigration.empty
        val value     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        assertTrue(migration(value) == Right(value))
      },
      test("addField adds a new field") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "age")
        )
      },
      test("dropField removes a field") {
        val action = MigrationAction.DropField(
          DynamicOptic.root.field("value"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "name"  -> DynamicValue.Primitive(PrimitiveValue.String("test")),
            "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          !result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "value")
        )
      },
      test("rename renames a field") {
        val action = MigrationAction.Rename(
          DynamicOptic.root.field("name"),
          "fullName"
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "fullName"),
          !result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "name")
        )
      },
      test("composition applies actions in order") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            DynamicValue.Primitive(PrimitiveValue.Int(0))
          )
        )
        val m2 = DynamicMigration.single(
          MigrationAction.Rename(
            DynamicOptic.root.field("name"),
            "fullName"
          )
        )
        val combined = m1 ++ m2
        val input    = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result   = combined(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "fullName"),
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "age")
        )
      },
      test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val m2 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("b"), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
        val m3 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("c"), DynamicValue.Primitive(PrimitiveValue.Int(3)))
        )
        val leftAssoc   = (m1 ++ m2) ++ m3
        val rightAssoc  = m1 ++ (m2 ++ m3)
        val input       = DynamicValue.Record(Chunk.empty)
        val leftResult  = leftAssoc(input)
        val rightResult = rightAssoc(input)
        assertTrue(
          leftResult == rightResult,
          leftAssoc.actions == rightAssoc.actions
        )
      },
      test("reverse reverses actions in order") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            MigrationAction.AddField(DynamicOptic.root.field("b"), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val reversed = m.reverse
        assertTrue(
          reversed.actions.size == 2,
          reversed.actions(0).isInstanceOf[MigrationAction.DropField],
          reversed.actions(1).isInstanceOf[MigrationAction.DropField]
        )
      },
      test("structural reverse law: m.reverse.reverse == m") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            MigrationAction.Rename(DynamicOptic.root.field("old"), "new"),
            MigrationAction.DropField(
              DynamicOptic.root.field("removed"),
              DynamicValue.Primitive(PrimitiveValue.String("default"))
            )
          )
        )
        val doubleReversed = m.reverse.reverse
        assertTrue(
          m.actions.size == doubleReversed.actions.size,
          m.actions.zip(doubleReversed.actions).forall { case (a, b) =>
            a.getClass == b.getClass && a.at == b.at
          }
        )
      }
    ),
    suite("Path navigation tests")(
      test("navigate nested fields") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("nested").field("newField"),
          DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk("nested" -> DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))))
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "nested")
            .exists {
              case (_, DynamicValue.Record(fields)) => fields.exists(_._1 == "newField")
              case _                                => false
            }
        )
      },
      test("navigate fails when field not found") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("missing").field("newField"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("other" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("navigate through Case path") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.caseOf("Active").field("newField"),
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Variant("Active", DynamicValue.Record(Chunk.empty))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Variant("Active", DynamicValue.Record(fields)) =>
              fields.exists(_._1 == "newField")
            case _ => false
          }
        )
      },
      test("Case path fails when case doesn't match") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.caseOf("Active").field("newField"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Variant("Inactive", DynamicValue.Record(Chunk.empty))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("Case path fails on non-Variant") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.caseOf("Active").field("x"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("navigate through Elements path") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.elements.field("extra"),
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk.empty),
            DynamicValue.Record(Chunk.empty)
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Sequence(elements) =>
              elements.forall {
                case DynamicValue.Record(fields) => fields.exists(_._1 == "extra")
                case _                           => false
              }
            case _ => false
          }
        )
      },
      test("Elements path fails on non-Sequence") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.elements.field("x"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("navigate through MapKeys path") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root.mapKeys,
          DynamicValue.Primitive(PrimitiveValue.String("transformed"))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("key1")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Map(entries) =>
              entries.exists {
                case (DynamicValue.Primitive(PrimitiveValue.String("transformed")), _) => true
                case _                                                                 => false
              }
            case _ => false
          }
        )
      },
      test("MapKeys path fails on non-Map") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root.mapKeys, DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("navigate through MapValues path") {
        val action =
          MigrationAction.TransformValue(DynamicOptic.root.mapValues, DynamicValue.Primitive(PrimitiveValue.Int(99)))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("key1")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Map(entries) =>
              entries.exists {
                case (_, DynamicValue.Primitive(PrimitiveValue.Int(99))) => true
                case _                                                   => false
              }
            case _ => false
          }
        )
      },
      test("MapValues path fails on non-Map") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root.mapValues, DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("navigate through AtIndex path") {
        val action =
          MigrationAction.TransformValue(DynamicOptic.root.at(1), DynamicValue.Primitive(PrimitiveValue.Int(99)))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Sequence(elements) =>
              elements(1) == DynamicValue.Primitive(PrimitiveValue.Int(99))
            case _ => false
          }
        )
      },
      test("AtIndex path fails when out of bounds") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root.at(10), DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("AtIndex path fails on non-Sequence") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root.at(0), DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("navigate through AtMapKey path") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root.atKey("target"),
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("other")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("target")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Map(entries) =>
              entries.exists {
                case (
                      DynamicValue.Primitive(PrimitiveValue.String("target")),
                      DynamicValue.Primitive(PrimitiveValue.Int(99))
                    ) =>
                  true
                case _ => false
              }
            case _ => false
          }
        )
      },
      test("AtMapKey path fails when key not found") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root.atKey("missing"), DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("other")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("AtMapKey path fails on non-Map") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root.atKey("key"), DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("navigate through Wrapped path") {
        val action =
          MigrationAction.TransformValue(DynamicOptic.root.wrapped, DynamicValue.Primitive(PrimitiveValue.Int(99)))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get == DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
      }
    ),
    suite("DynamicMigration additional tests")(
      test("andThen alias for ++") {
        val m1       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Null))
        val m2       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root.field("b"), DynamicValue.Null))
        val combined = m1.andThen(m2)
        assertTrue(combined.size == 2)
      },
      test("size returns action count") {
        val m = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Null),
          MigrationAction.AddField(DynamicOptic.root.field("b"), DynamicValue.Null),
          MigrationAction.AddField(DynamicOptic.root.field("c"), DynamicValue.Null)
        )
        assertTrue(m.size == 3)
      },
      test("isEmpty is false for non-empty migration") {
        val m = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Null))
        assertTrue(!m.isEmpty)
      },
      test("action execution stops on first error") {
        val m = DynamicMigration(
          MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), DynamicValue.Null),
          MigrationAction.AddField(DynamicOptic.root.field("new"), DynamicValue.Null)
        )
        val input  = DynamicValue.Record(Chunk.empty)
        val result = m(input)
        assertTrue(result.isLeft)
      }
    ),
    suite("Edge cases for action paths")(
      test("Rename fails when path doesn't end with Field node") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "newName")
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("Join fails when path doesn't end with Field node") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          Vector.empty,
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk.empty)
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("Split fails when target path doesn't end with Field node") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("source" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("AtIndex path fails with negative index") {
        val action    = MigrationAction.TransformValue(DynamicOptic.root.at(-1), DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("navigate through nested field in map value") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.mapValues.field("extra"),
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("key1")),
              DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Map(entries) =>
              entries.exists {
                case (_, DynamicValue.Record(fields)) => fields.exists(_._1 == "extra")
                case _                                => false
              }
            case _ => false
          }
        )
      },
      test("navigate through nested field in sequence element") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.elements.field("extra"),
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))),
            DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Sequence(elements) =>
              elements.forall {
                case DynamicValue.Record(fields) => fields.exists(_._1 == "extra")
                case _                           => false
              }
            case _ => false
          }
        )
      },
      test("navigate through nested field in map key") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.mapKeys.field("extra"),
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))),
              DynamicValue.Primitive(PrimitiveValue.Int(100))
            )
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Map(entries) =>
              entries.exists {
                case (DynamicValue.Record(fields), _) => fields.exists(_._1 == "extra")
                case _                                => false
              }
            case _ => false
          }
        )
      },
      test("navigate through index then field") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.at(0).field("extra"),
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))),
            DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Sequence(elements) =>
              elements(0) match {
                case DynamicValue.Record(fields) => fields.exists(_._1 == "extra")
                case _                           => false
              }
            case _ => false
          }
        )
      },
      test("navigate through map key then field") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.atKey("target").field("extra"),
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("target")),
              DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Map(entries) =>
              entries.exists {
                case (DynamicValue.Primitive(PrimitiveValue.String("target")), DynamicValue.Record(fields)) =>
                  fields.exists(_._1 == "extra")
                case _ => false
              }
            case _ => false
          }
        )
      }
    ),
    suite("Additional DynamicMigration edge cases")(
      test("empty sequence transformation") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("items"),
          Vector(MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("items" -> DynamicValue.Sequence(Chunk.empty)))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("items", DynamicValue.Sequence(elements)) => elements.isEmpty
            case _                                          => false
          }
        )
      },
      test("empty map key transformation") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root.field("map"),
          Vector(MigrationAction.TransformValue(DynamicOptic.root, DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("map" -> DynamicValue.Map(Chunk.empty)))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("map", DynamicValue.Map(entries)) => entries.isEmpty
            case _                                  => false
          }
        )
      },
      test("empty map value transformation") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root.field("map"),
          Vector(MigrationAction.TransformValue(DynamicOptic.root, DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("map" -> DynamicValue.Map(Chunk.empty)))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("map", DynamicValue.Map(entries)) => entries.isEmpty
            case _                                  => false
          }
        )
      },
      test("multiple elements in sequence transformation") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Vector(
            MigrationAction.AddField(DynamicOptic.root.field("added"), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk.empty),
            DynamicValue.Record(Chunk.empty),
            DynamicValue.Record(Chunk.empty)
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Sequence(elements) =>
              elements.length == 3 && elements.forall {
                case DynamicValue.Record(fields) => fields.exists(_._1 == "added")
                case _                           => false
              }
            case _ => false
          }
        )
      },
      test("multiple entries in map key transformation") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Vector(
            MigrationAction.TransformValue(DynamicOptic.root, DynamicValue.Primitive(PrimitiveValue.String("key")))
          )
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Map(entries) => entries.length == 2
            case _                         => false
          }
        )
      },
      test("multiple entries in map value transformation") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Vector(MigrationAction.TransformValue(DynamicOptic.root, DynamicValue.Primitive(PrimitiveValue.Int(99))))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Map(entries) =>
              entries.forall {
                case (_, DynamicValue.Primitive(PrimitiveValue.Int(99))) => true
                case _                                                   => false
              }
            case _ => false
          }
        )
      },
      test("nested field navigation fails on non-Record") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("nested").field("inner"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("nested" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("transformation error in sequence element propagates") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Vector(MigrationAction.DropField(DynamicOptic.root.field("missing"), DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))))
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("transformation error in map key propagates") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Vector(MigrationAction.DropField(DynamicOptic.root.field("missing"), DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk((DynamicValue.Record(Chunk.empty), DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("transformation error in map value propagates") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Vector(MigrationAction.DropField(DynamicOptic.root.field("missing"), DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk((DynamicValue.Primitive(PrimitiveValue.String("k")), DynamicValue.Record(Chunk.empty)))
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("error in nested element path navigation propagates") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.elements.field("nested").field("deep"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Sequence(
          Chunk(DynamicValue.Record(Chunk("nested" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))))
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("error in nested map key path navigation propagates") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.mapKeys.field("nested").field("deep"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Record(Chunk("nested" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))),
              DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("error in nested map value path navigation propagates") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.mapValues.field("nested").field("deep"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("k")),
              DynamicValue.Record(Chunk("nested" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )
          )
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      }
    ),
    suite("Nest/Unnest operations")(
      test("nest fields into sub-record") {
        val action = MigrationAction.Nest(
          DynamicOptic.root,
          "address",
          Vector("street", "city", "zip")
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "name"   -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
            "city"   -> DynamicValue.Primitive(PrimitiveValue.String("Springfield")),
            "zip"    -> DynamicValue.Primitive(PrimitiveValue.String("62701"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.length == 2 &&
              fields.exists(_._1 == "name") &&
              fields.exists { case (n, v) =>
                n == "address" && (v match {
                  case DynamicValue.Record(nested) =>
                    nested.length == 3 &&
                    nested.exists(_._1 == "street") &&
                    nested.exists(_._1 == "city") &&
                    nested.exists(_._1 == "zip")
                  case _ => false
                })
              }
            case _ => false
          }
        )
      },
      test("unnest fields from sub-record") {
        val action = MigrationAction.Unnest(
          DynamicOptic.root,
          "address",
          Vector("street", "city")
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "address" -> DynamicValue.Record(
              Chunk(
                "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
                "city"   -> DynamicValue.Primitive(PrimitiveValue.String("Springfield"))
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.length == 3 &&
              fields.exists(_._1 == "name") &&
              fields.exists(_._1 == "street") &&
              fields.exists(_._1 == "city")
            case _ => false
          }
        )
      },
      test("nest at nested path") {
        val action = MigrationAction.Nest(
          DynamicOptic.root.field("user"),
          "location",
          Vector("city", "country")
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "user" -> DynamicValue.Record(
              Chunk(
                "name"    -> DynamicValue.Primitive(PrimitiveValue.String("John")),
                "city"    -> DynamicValue.Primitive(PrimitiveValue.String("NYC")),
                "country" -> DynamicValue.Primitive(PrimitiveValue.String("US"))
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(result.isRight)
      },
      test("nest errors on missing source field") {
        val action = MigrationAction.Nest(
          DynamicOptic.root,
          "address",
          Vector("street", "nonexistent")
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk("street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")))
        )
        assertTrue(migration(input).isLeft)
      },
      test("nest errors on non-record") {
        val action    = MigrationAction.Nest(DynamicOptic.root, "sub", Vector("a"))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(migration(input).isLeft)
      },
      test("unnest errors on missing sub-record") {
        val action    = MigrationAction.Unnest(DynamicOptic.root, "missing", Vector("a"))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )
        assertTrue(migration(input).isLeft)
      },
      test("unnest errors when field is not a record") {
        val action    = MigrationAction.Unnest(DynamicOptic.root, "name", Vector("a"))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )
        assertTrue(migration(input).isLeft)
      },
      test("unnest errors on missing nested field") {
        val action    = MigrationAction.Unnest(DynamicOptic.root, "sub", Vector("missing"))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "sub" -> DynamicValue.Record(
              Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )
          )
        )
        assertTrue(migration(input).isLeft)
      },
      test("nest then unnest is round-trip") {
        val nest      = MigrationAction.Nest(DynamicOptic.root, "address", Vector("street", "city"))
        val unnest    = MigrationAction.Unnest(DynamicOptic.root, "address", Vector("street", "city"))
        val migration = DynamicMigration(nest, unnest)
        val input     = DynamicValue.Record(
          Chunk(
            "name"   -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main")),
            "city"   -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.exists(_._1 == "name") &&
              fields.exists(_._1 == "street") &&
              fields.exists(_._1 == "city")
            case _ => false
          }
        )
      },
      test("nest with empty sourceFields creates empty sub-record") {
        val action    = MigrationAction.Nest(DynamicOptic.root, "empty", Vector.empty)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.exists { case (n, v) =>
                n == "empty" && (v match {
                  case DynamicValue.Record(inner) => inner.isEmpty
                  case _                          => false
                })
              }
            case _ => false
          }
        )
      }
    ),
    suite("Multi-step migration scenarios")(
      test("rename + add + nest combined migration") {
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "name"),
          MigrationAction.AddField(DynamicOptic.root.field("age"), DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationAction.Nest(DynamicOptic.root, "meta", Vector("age"))
        )
        val input = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "email"     -> DynamicValue.Primitive(PrimitiveValue.String("j@e.com"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.exists(_._1 == "name") &&
              fields.exists(_._1 == "email") &&
              fields.exists(_._1 == "meta")
            case _ => false
          }
        )
      },
      test("drop + rename + transform composed migration") {
        val migration = DynamicMigration(
          MigrationAction.DropField(
            DynamicOptic.root.field("unused"),
            DynamicValue.Primitive(PrimitiveValue.String("default"))
          ),
          MigrationAction.Rename(DynamicOptic.root.field("old_name"), "new_name"),
          MigrationAction.TransformValue(
            DynamicOptic.root.field("status"),
            DynamicValue.Primitive(PrimitiveValue.Boolean(true))
          )
        )
        val input = DynamicValue.Record(
          Chunk(
            "unused"   -> DynamicValue.Primitive(PrimitiveValue.String("x")),
            "old_name" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
            "status"   -> DynamicValue.Primitive(PrimitiveValue.String("active"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.length == 2 &&
              fields.exists(_._1 == "new_name") &&
              fields.exists { case (n, v) =>
                n == "status" && v == DynamicValue.Primitive(PrimitiveValue.Boolean(true))
              }
            case _ => false
          }
        )
      },
      test("full schema evolution: v1 -> v2 with multiple transforms") {
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root.field("first_name"), "name"),
          MigrationAction.DropField(
            DynamicOptic.root.field("last_name"),
            DynamicValue.Primitive(PrimitiveValue.String(""))
          ),
          MigrationAction.AddField(
            DynamicOptic.root.field("active"),
            DynamicValue.Primitive(PrimitiveValue.Boolean(true))
          ),
          MigrationAction.AddField(
            DynamicOptic.root.field("role"),
            DynamicValue.Primitive(PrimitiveValue.String("user"))
          )
        )
        val input = DynamicValue.Record(
          Chunk(
            "first_name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "last_name"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
            "email"      -> DynamicValue.Primitive(PrimitiveValue.String("j@e.com"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.length == 4 &&
              fields.exists(_._1 == "name") &&
              fields.exists(_._1 == "email") &&
              fields.exists(_._1 == "active") &&
              fields.exists(_._1 == "role")
            case _ => false
          }
        )
      },
      test("migration with nested path operations") {
        val migration = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root.field("user").field("role"),
            DynamicValue.Primitive(PrimitiveValue.String("admin"))
          ),
          MigrationAction.Rename(
            DynamicOptic.root.field("user").field("name"),
            "fullName"
          )
        )
        val input = DynamicValue.Record(
          Chunk(
            "user" -> DynamicValue.Record(
              Chunk(
                "name"  -> DynamicValue.Primitive(PrimitiveValue.String("John")),
                "email" -> DynamicValue.Primitive(PrimitiveValue.String("j@e.com"))
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(outer) =>
              outer.exists { case (n, v) =>
                n == "user" && (v match {
                  case DynamicValue.Record(inner) =>
                    inner.exists(_._1 == "fullName") &&
                    inner.exists(_._1 == "email") &&
                    inner.exists(_._1 == "role")
                  case _ => false
                })
              }
            case _ => false
          }
        )
      },
      test("5-action chain preserves all fields correctly") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          MigrationAction.AddField(DynamicOptic.root.field("b"), DynamicValue.Primitive(PrimitiveValue.Int(2))),
          MigrationAction.AddField(DynamicOptic.root.field("c"), DynamicValue.Primitive(PrimitiveValue.Int(3))),
          MigrationAction.Rename(DynamicOptic.root.field("x"), "y"),
          MigrationAction.DropField(DynamicOptic.root.field("temp"), DynamicValue.Null)
        )
        val input = DynamicValue.Record(
          Chunk(
            "x"    -> DynamicValue.Primitive(PrimitiveValue.String("val")),
            "temp" -> DynamicValue.Primitive(PrimitiveValue.String("remove"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.length == 4 &&
              fields.exists(_._1 == "y") &&
              fields.exists(_._1 == "a") &&
              fields.exists(_._1 == "b") &&
              fields.exists(_._1 == "c")
            case _ => false
          }
        )
      },
      test("composed migration using ++ operator") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val m2 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("b"), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
        val m3 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
        )
        val composed = m1 ++ m2 ++ m3
        val input    = DynamicValue.Record(
          Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.String("val")))
        )
        val result = composed(input)
        assertTrue(
          result.isRight,
          composed.size == 3,
          result.toOption.get match {
            case DynamicValue.Record(fields) =>
              fields.exists(_._1 == "y") &&
              fields.exists(_._1 == "a") &&
              fields.exists(_._1 == "b")
            case _ => false
          }
        )
      },
      test("migration fails fast on first error in chain") {
        val migration = DynamicMigration(
          MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), DynamicValue.Null),
          MigrationAction.AddField(DynamicOptic.root.field("new"), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val input = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        assertTrue(migration(input).isLeft)
      },
      test("TransformElements with multi-action chain per element") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("active"),
                DynamicValue.Primitive(PrimitiveValue.Boolean(true))
              ),
              MigrationAction.Rename(DynamicOptic.root.field("id"), "itemId")
            )
          )
        )
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("id" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))),
            DynamicValue.Record(Chunk("id" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get match {
            case DynamicValue.Sequence(elems) =>
              elems.length == 2 &&
              elems.forall {
                case DynamicValue.Record(fields) =>
                  fields.exists(_._1 == "itemId") && fields.exists(_._1 == "active")
                case _ => false
              }
            case _ => false
          }
        )
      }
    ),
    suite("Laws")(
      test("identity migration preserves value") {
        val input = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val result = DynamicMigration.empty(input)
        assertTrue(result == Right(input))
      },
      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val m2 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("b"), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
        val m3 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("c"), DynamicValue.Primitive(PrimitiveValue.Int(3)))
        )
        val input = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.String("val"))))

        val leftAssoc  = (m1 ++ m2) ++ m3
        val rightAssoc = m1 ++ (m2 ++ m3)

        val r1 = leftAssoc(input)
        val r2 = rightAssoc(input)
        assertTrue(r1 == r2)
      },
      test("reverse of reverse equals original") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root.field("age"), DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
        )
        val reversed = migration.reverse.reverse
        assertTrue(
          reversed.actions.length == migration.actions.length,
          reversed.actions.zip(migration.actions).forall { case (a, b) => a == b }
        )
      }
    ),
    suite("Deep path operations")(
      test("4-level nested field operation") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("level1").field("level2").field("level3").field("newField"),
          DynamicValue.Primitive(PrimitiveValue.String("deep"))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "level1" -> DynamicValue.Record(
              Chunk(
                "level2" -> DynamicValue.Record(
                  Chunk(
                    "level3" -> DynamicValue.Record(
                      Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
                    )
                  )
                )
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(result.isRight)
      },
      test("cross-type path: record -> sequence -> record -> field") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("items"),
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("details").field("status"),
              DynamicValue.Primitive(PrimitiveValue.String("active"))
            )
          )
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "items" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Record(
                  Chunk(
                    "details" -> DynamicValue.Record(
                      Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("item1")))
                    )
                  )
                )
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(result.isRight)
      },
      test("nested enum case transformation") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root.field("payment"),
          "CreditCard",
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("num"), "number")
          )
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "payment" -> DynamicValue.Variant(
              "CreditCard",
              DynamicValue.Record(
                Chunk("num" -> DynamicValue.Primitive(PrimitiveValue.String("4111")))
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(result.isRight)
      },
      test("map keys + nested record transformation") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("processed"),
              DynamicValue.Primitive(PrimitiveValue.Boolean(true))
            )
          )
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("key1")),
              DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
            ),
            (
              DynamicValue.Primitive(PrimitiveValue.String("key2")),
              DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
            )
          )
        )
        val result = migration(input)
        assertTrue(result.isRight)
      }
    )
  )
}
