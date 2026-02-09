package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object MigrationActionSpec extends SchemaBaseSpec {

  def spec = suite("MigrationActionSpec")(
    suite("MigrationAction laws")(
      test("reverse.reverse == original (structural)") {
        val action   = MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        val reversed = action.reverse.reverse
        assertTrue(
          reversed match {
            case MigrationAction.Rename(at, to) =>
              at.nodes.lastOption.exists {
                case DynamicOptic.Node.Field(name) => name == "a"
                case _                             => false
              } && to == "b"
            case _ => false
          }
        )
      },
      test("RenameCase reverse swaps from and to") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val reversed = action.reverse
        assertTrue(
          reversed match {
            case MigrationAction.RenameCase(_, from, to) => from == "NewCase" && to == "OldCase"
            case _                                       => false
          }
        )
      }
    ),
    suite("Error handling")(
      test("addField fails if field already exists") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("name"),
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("existing"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("dropField fails if field doesn't exist") {
        val action = MigrationAction.DropField(
          DynamicOptic.root.field("nonexistent"),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("rename fails if source field doesn't exist") {
        val action    = MigrationAction.Rename(DynamicOptic.root.field("nonexistent"), "newName")
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      }
    ),
    suite("Additional MigrationAction tests")(
      test("TransformValue replaces a value") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root.field("name"),
          DynamicValue.Primitive(PrimitiveValue.String("transformed"))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("original"))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("name", DynamicValue.Primitive(PrimitiveValue.String("transformed"))) => true
            case _                                                                      => false
          }
        )
      },
      test("TransformValue reverse is self") {
        val action   = MigrationAction.TransformValue(DynamicOptic.root.field("a"), DynamicValue.Null)
        val reversed = action.reverse
        assertTrue(
          reversed match {
            case MigrationAction.TransformValue(at, _) =>
              at.nodes.lastOption.exists {
                case DynamicOptic.Node.Field(name) => name == "a"
                case _                             => false
              }
            case _ => false
          }
        )
      },
      test("Mandate converts None to default") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root.field("value"),
          DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
        val migration = DynamicMigration.single(action)
        val input     =
          DynamicValue.Record(Chunk("value" -> DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))))
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("value", DynamicValue.Primitive(PrimitiveValue.Int(42))) => true
            case _                                                         => false
          }
        )
      },
      test("Mandate extracts Some value") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root.field("value"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk("value" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(99))))
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("value", DynamicValue.Primitive(PrimitiveValue.Int(99))) => true
            case _                                                         => false
          }
        )
      },
      test("Mandate passes through non-optional values") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root.field("value"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(77))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("value", DynamicValue.Primitive(PrimitiveValue.Int(77))) => true
            case _                                                         => false
          }
        )
      },
      test("Mandate reverse is Optionalize") {
        val action   = MigrationAction.Mandate(DynamicOptic.root.field("a"), DynamicValue.Null)
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Optionalize])
      },
      test("Optionalize wraps value in Some") {
        val action    = MigrationAction.Optionalize(DynamicOptic.root.field("value"))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("value", DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))) => true
            case _                                                                                       => false
          }
        )
      },
      test("Optionalize reverse is Mandate with Null default") {
        val action   = MigrationAction.Optionalize(DynamicOptic.root.field("a"))
        val reversed = action.reverse
        assertTrue(
          reversed match {
            case MigrationAction.Mandate(_, DynamicValue.Null) => true
            case _                                             => false
          }
        )
      },
      test("ChangeType replaces a value") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root.field("count"),
          DynamicValue.Primitive(PrimitiveValue.String("42"))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("count" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("count", DynamicValue.Primitive(PrimitiveValue.String("42"))) => true
            case _                                                              => false
          }
        )
      },
      test("ChangeType reverse is self") {
        val action   = MigrationAction.ChangeType(DynamicOptic.root.field("a"), DynamicValue.Null)
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.ChangeType])
      },
      test("RenameCase renames an enum case") {
        val action    = MigrationAction.RenameCase(DynamicOptic.root.field("status"), "Active", "Enabled")
        val migration = DynamicMigration.single(action)
        val input     =
          DynamicValue.Record(Chunk("status" -> DynamicValue.Variant("Active", DynamicValue.Record(Chunk.empty))))
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("status", DynamicValue.Variant("Enabled", _)) => true
            case _                                              => false
          }
        )
      },
      test("RenameCase preserves other cases") {
        val action    = MigrationAction.RenameCase(DynamicOptic.root.field("status"), "Active", "Enabled")
        val migration = DynamicMigration.single(action)
        val input     =
          DynamicValue.Record(Chunk("status" -> DynamicValue.Variant("Inactive", DynamicValue.Record(Chunk.empty))))
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("status", DynamicValue.Variant("Inactive", _)) => true
            case _                                               => false
          }
        )
      },
      test("RenameCase fails on non-Variant") {
        val action    = MigrationAction.RenameCase(DynamicOptic.root.field("status"), "Active", "Enabled")
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("status" -> DynamicValue.Primitive(PrimitiveValue.String("active"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("TransformCase transforms matching case") {
        val innerAction = MigrationAction.AddField(
          DynamicOptic.root.field("extra"),
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val action    = MigrationAction.TransformCase(DynamicOptic.root.field("status"), "Active", Vector(innerAction))
        val migration = DynamicMigration.single(action)
        val input     =
          DynamicValue.Record(Chunk("status" -> DynamicValue.Variant("Active", DynamicValue.Record(Chunk.empty))))
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "status")
            .exists {
              case (_, DynamicValue.Variant("Active", DynamicValue.Record(fields))) =>
                fields.exists(_._1 == "extra")
              case _ => false
            }
        )
      },
      test("TransformCase preserves non-matching case") {
        val innerAction = MigrationAction.AddField(DynamicOptic.root.field("extra"), DynamicValue.Null)
        val action      = MigrationAction.TransformCase(DynamicOptic.root.field("status"), "Active", Vector(innerAction))
        val migration   = DynamicMigration.single(action)
        val input       =
          DynamicValue.Record(Chunk("status" -> DynamicValue.Variant("Inactive", DynamicValue.Record(Chunk.empty))))
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("status", DynamicValue.Variant("Inactive", DynamicValue.Record(fields))) =>
              !fields.exists(_._1 == "extra")
            case _ => false
          }
        )
      },
      test("TransformCase fails on non-Variant") {
        val action    = MigrationAction.TransformCase(DynamicOptic.root.field("status"), "Active", Vector.empty)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("status" -> DynamicValue.Primitive(PrimitiveValue.String("active"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("TransformCase reverse reverses inner actions") {
        val innerAction = MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicValue.Null)
        val action      = MigrationAction.TransformCase(DynamicOptic.root, "A", Vector(innerAction))
        val reversed    = action.reverse
        assertTrue(
          reversed match {
            case MigrationAction.TransformCase(_, "A", actions) =>
              actions.headOption.exists(_.isInstanceOf[MigrationAction.DropField])
            case _ => false
          }
        )
      },
      test("TransformElements transforms each element") {
        val innerAction = MigrationAction.AddField(
          DynamicOptic.root.field("extra"),
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val action    = MigrationAction.TransformElements(DynamicOptic.root.field("items"), Vector(innerAction))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "items" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Record(Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))),
                DynamicValue.Record(Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "items")
            .exists {
              case (_, DynamicValue.Sequence(elements)) =>
                elements.forall {
                  case DynamicValue.Record(fields) => fields.exists(_._1 == "extra")
                  case _                           => false
                }
              case _ => false
            }
        )
      },
      test("TransformElements fails on non-Sequence") {
        val action    = MigrationAction.TransformElements(DynamicOptic.root.field("items"), Vector.empty)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("items" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("TransformElements reverse reverses inner actions") {
        val innerAction = MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicValue.Null)
        val action      = MigrationAction.TransformElements(DynamicOptic.root, Vector(innerAction))
        val reversed    = action.reverse
        assertTrue(
          reversed match {
            case MigrationAction.TransformElements(_, actions) =>
              actions.headOption.exists(_.isInstanceOf[MigrationAction.DropField])
            case _ => false
          }
        )
      },
      test("TransformKeys transforms map keys") {
        val innerAction = MigrationAction.TransformValue(
          DynamicOptic.root,
          DynamicValue.Primitive(PrimitiveValue.String("newKey"))
        )
        val action    = MigrationAction.TransformKeys(DynamicOptic.root.field("map"), Vector(innerAction))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "map" -> DynamicValue.Map(
              Chunk(
                (DynamicValue.Primitive(PrimitiveValue.String("oldKey")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "map")
            .exists {
              case (_, DynamicValue.Map(entries)) =>
                entries.exists {
                  case (DynamicValue.Primitive(PrimitiveValue.String("newKey")), _) => true
                  case _                                                            => false
                }
              case _ => false
            }
        )
      },
      test("TransformKeys fails on non-Map") {
        val action    = MigrationAction.TransformKeys(DynamicOptic.root.field("map"), Vector.empty)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("map" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("TransformKeys reverse reverses inner actions") {
        val innerAction = MigrationAction.TransformValue(DynamicOptic.root, DynamicValue.Null)
        val action      = MigrationAction.TransformKeys(DynamicOptic.root, Vector(innerAction))
        val reversed    = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformKeys])
      },
      test("TransformValues transforms map values") {
        val innerAction = MigrationAction.TransformValue(
          DynamicOptic.root,
          DynamicValue.Primitive(PrimitiveValue.Int(99))
        )
        val action    = MigrationAction.TransformValues(DynamicOptic.root.field("map"), Vector(innerAction))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "map" -> DynamicValue.Map(
              Chunk(
                (DynamicValue.Primitive(PrimitiveValue.String("key")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
              )
            )
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "map")
            .exists {
              case (_, DynamicValue.Map(entries)) =>
                entries.exists {
                  case (_, DynamicValue.Primitive(PrimitiveValue.Int(99))) => true
                  case _                                                   => false
                }
              case _ => false
            }
        )
      },
      test("TransformValues fails on non-Map") {
        val action    = MigrationAction.TransformValues(DynamicOptic.root.field("map"), Vector.empty)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("map" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("TransformValues reverse reverses inner actions") {
        val innerAction = MigrationAction.TransformValue(DynamicOptic.root, DynamicValue.Null)
        val action      = MigrationAction.TransformValues(DynamicOptic.root, Vector(innerAction))
        val reversed    = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformValues])
      },
      test("Join adds combined value") {
        val action = MigrationAction.Join(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          DynamicValue.Primitive(PrimitiveValue.String("joined"))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "combined")
        )
      },
      test("Join fails on non-Record") {
        val action = MigrationAction.Join(
          DynamicOptic.root.field("combined"),
          Vector.empty,
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("Join reverse is Split") {
        val action =
          MigrationAction.Join(DynamicOptic.root.field("c"), Vector(DynamicOptic.root.field("a")), DynamicValue.Null)
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Split])
      },
      test("Split adds fields") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("source" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "a"),
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "b")
        )
      },
      test("Split fails on non-Record target") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          DynamicValue.Null
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("Split reverse is Join") {
        val action =
          MigrationAction.Split(DynamicOptic.root.field("s"), Vector(DynamicOptic.root.field("a")), DynamicValue.Null)
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Join])
      },
      test("Rename fails when target field already exists") {
        val action    = MigrationAction.Rename(DynamicOptic.root.field("name"), "email")
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "name"  -> DynamicValue.Primitive(PrimitiveValue.String("test")),
            "email" -> DynamicValue.Primitive(PrimitiveValue.String("test@test.com"))
          )
        )
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("AddField fails on non-Record") {
        val action    = MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("DropField fails on non-Record") {
        val action    = MigrationAction.DropField(DynamicOptic.root.field("x"), DynamicValue.Null)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("Rename fails on non-Record") {
        val action    = MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val result    = migration(input)
        assertTrue(result.isLeft)
      }
    ),
    suite("AddField and DropField fieldName extraction")(
      test("AddField.fieldName extracts name from path") {
        val action = MigrationAction.AddField(DynamicOptic.root.field("myField"), DynamicValue.Null)
        assertTrue(action.fieldName == "myField")
      },
      test("DropField.fieldName extracts name from path") {
        val action = MigrationAction.DropField(DynamicOptic.root.field("myField"), DynamicValue.Null)
        assertTrue(action.fieldName == "myField")
      },
      test("Rename.from extracts source name from path") {
        val action = MigrationAction.Rename(DynamicOptic.root.field("oldName"), "newName")
        assertTrue(action.from == "oldName")
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
    suite("MigrationAction fieldName/from exception branches")(
      test("AddField.fieldName throws on root path") {
        val action = MigrationAction.AddField(DynamicOptic.root, DynamicValue.Null)
        val result = scala.util.Try(action.fieldName)
        assertTrue(result.isFailure)
      },
      test("AddField.fieldName throws on non-Field terminal node") {
        val action = MigrationAction.AddField(DynamicOptic.root.elements, DynamicValue.Null)
        val result = scala.util.Try(action.fieldName)
        assertTrue(result.isFailure)
      },
      test("DropField.fieldName throws on root path") {
        val action = MigrationAction.DropField(DynamicOptic.root, DynamicValue.Null)
        val result = scala.util.Try(action.fieldName)
        assertTrue(result.isFailure)
      },
      test("DropField.fieldName throws on non-Field terminal node") {
        val action = MigrationAction.DropField(DynamicOptic.root.elements, DynamicValue.Null)
        val result = scala.util.Try(action.fieldName)
        assertTrue(result.isFailure)
      },
      test("Rename.from throws on root path") {
        val action = MigrationAction.Rename(DynamicOptic.root, "newName")
        val result = scala.util.Try(action.from)
        assertTrue(result.isFailure)
      },
      test("Rename.from throws on non-Field terminal node") {
        val action = MigrationAction.Rename(DynamicOptic.root.elements, "newName")
        val result = scala.util.Try(action.from)
        assertTrue(result.isFailure)
      }
    ),
    suite("Executor error paths and reverse coverage")(
      test("JoinExpr fails when combine expression fails") {
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a")),
          MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent"))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(migration(input).isLeft)
      },
      test("JoinExpr fails on non-Record parent") {
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("combined"),
          Vector.empty,
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration(input).isLeft)
      },
      test("SplitExpr fails when split expression fails") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          Vector(MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent")))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("source" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
        assertTrue(migration(input).isLeft)
      },
      test("SplitExpr fails on non-Record parent") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          Vector(MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration(input).isLeft)
      },
      test("TransformValueExpr fails when expression fails") {
        val expr      = MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent"))
        val action    = MigrationAction.TransformValueExpr(DynamicOptic.root.field("value"), expr)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
        assertTrue(migration(input).isLeft)
      },
      test("ChangeTypeExpr fails when expression fails") {
        val expr      = MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent"))
        val action    = MigrationAction.ChangeTypeExpr(DynamicOptic.root.field("value"), expr)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
        assertTrue(migration(input).isLeft)
      },
      test("Join fails on non-Record parent") {
        val action = MigrationAction.Join(
          DynamicOptic.root.field("combined"),
          Vector.empty,
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration(input).isLeft)
      },
      test("Split fails on non-Record parent") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration(input).isLeft)
      },
      test("Rename fails when path doesn't end with Field node") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "newName")
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(migration(input).isLeft)
      },
      test("JoinExpr reverse without splitExprs uses FieldRef fallback") {
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          MigrationExpr.Literal(DynamicValue.Null),
          None
        )
        val reversed = action.reverse
        assertTrue(reversed match {
          case MigrationAction.SplitExpr(_, paths, exprs, Some(_)) =>
            paths.length == 2 && exprs.length == 2 && exprs.forall(_.isInstanceOf[MigrationExpr.FieldRef])
          case _ => false
        })
      },
      test("SplitExpr reverse without combineExpr uses FieldRef fallback") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          Vector(MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))),
          None
        )
        val reversed = action.reverse
        assertTrue(reversed match {
          case MigrationAction.JoinExpr(_, _, MigrationExpr.FieldRef(_), Some(_)) => true
          case _                                                                  => false
        })
      },
      test("ChangeTypeExpr reverse without reverseExpr uses original expr") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.FieldRef(DynamicOptic.root),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val action   = MigrationAction.ChangeTypeExpr(DynamicOptic.root.field("v"), expr, None)
        val reversed = action.reverse
        assertTrue(reversed match {
          case MigrationAction.ChangeTypeExpr(_, e, Some(re)) => e == expr && re == expr
          case _                                              => false
        })
      },
      test("TransformValueExpr reverse without reverseExpr uses original expr") {
        val expr     = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val action   = MigrationAction.TransformValueExpr(DynamicOptic.root.field("x"), expr, None)
        val reversed = action.reverse
        assertTrue(reversed match {
          case MigrationAction.TransformValueExpr(_, e, Some(re)) => e == expr && re == expr
          case _                                                  => false
        })
      }
    )
  )
}
