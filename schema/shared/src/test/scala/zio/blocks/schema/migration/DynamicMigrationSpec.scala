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
    )
  )
}
