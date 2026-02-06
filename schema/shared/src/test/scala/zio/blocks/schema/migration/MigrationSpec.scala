package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  // Test case classes
  case class PersonV1(firstName: String, lastName: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  case class SimpleRecord(name: String, value: Int)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived[SimpleRecord]
  }

  case class SimpleRecordWithOptional(name: String, value: Option[Int])
  object SimpleRecordWithOptional {
    implicit val schema: Schema[SimpleRecordWithOptional] = Schema.derived[SimpleRecordWithOptional]
  }

  // Test for MigrationBuilder with different fields
  case class UserV1(name: String, email: String)
  object UserV1 {
    implicit val schema: Schema[UserV1] = Schema.derived[UserV1]
  }

  case class UserV2(fullName: String, email: String, age: Int)
  object UserV2 {
    implicit val schema: Schema[UserV2] = Schema.derived[UserV2]
  }

  // Same-field case classes for auto-mapping
  case class Point2D(x: Int, y: Int)
  object Point2D {
    implicit val schema: Schema[Point2D] = Schema.derived[Point2D]
  }

  case class Point2DWithZ(x: Int, y: Int, z: Int)
  object Point2DWithZ {
    implicit val schema: Schema[Point2DWithZ] = Schema.derived[Point2DWithZ]
  }

  def spec = suite("MigrationSpec")(
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
    suite("Migration[A, B]")(
      test("identity migration preserves value") {
        import SimpleRecord._
        val migration = Migration.identity[SimpleRecord]
        val input     = SimpleRecord("test", 42)
        val result    = migration(input)
        assertTrue(result == Right(input))
      },
      test("composition with ++ works correctly") {
        import SimpleRecord._
        val m1       = Migration.identity[SimpleRecord]
        val m2       = Migration.identity[SimpleRecord]
        val combined = m1 ++ m2
        assertTrue(combined.isEmpty)
      }
    ),
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
    suite("MigrationBuilder")(
      test("builder with rename and add field creates valid migration") {
        val migration = Migration
          .builder[UserV1, UserV2]
          .renameField(_.name, _.fullName)
          .addField(_.age, 0)
          .buildPartial

        val input  = UserV1("John Doe", "john@example.com")
        val result = migration(input)

        assertTrue(
          result.isRight,
          result.toOption.get == UserV2("John Doe", "john@example.com", 0)
        )
      },
      test("builder with drop field creates valid migration") {
        val migration = Migration
          .builder[UserV2, UserV1]
          .renameField(_.fullName, _.name)
          .dropField(_.age, 0)
          .buildPartial

        val input  = UserV2("Jane Doe", "jane@example.com", 30)
        val result = migration(input)

        assertTrue(
          result.isRight,
          result.toOption.get == UserV1("Jane Doe", "jane@example.com")
        )
      },
      test("builder migration can be reversed") {
        val forward = Migration
          .builder[UserV1, UserV2]
          .renameField(_.name, _.fullName)
          .addField(_.age, 0)
          .buildPartial

        val backward = forward.reverse
        val input    = UserV2("Test User", "test@example.com", 25)
        val result   = backward(input)

        assertTrue(
          result.isRight,
          result.toOption.get == UserV1("Test User", "test@example.com")
        )
      },
      test("builder with auto-mapped fields only needs new field") {
        // x and y are auto-mapped (same name), only need to add z
        val migration = Migration
          .builder[Point2D, Point2DWithZ]
          .addField(_.z, 0)
          .buildPartial

        val input  = Point2D(10, 20)
        val result = migration(input)

        assertTrue(
          result.isRight,
          result.toOption.get == Point2DWithZ(10, 20, 0)
        )
      },
      test("builder tracks actions correctly") {
        val migration = Migration
          .builder[UserV1, UserV2]
          .renameField(_.name, _.fullName)
          .addField(_.age, 25)
          .buildPartial

        assertTrue(
          migration.size == 2,
          migration.actions.exists {
            case MigrationAction.Rename(_, to) => to == "fullName"
            case _                             => false
          },
          migration.actions.exists {
            case MigrationAction.AddField(at, _) =>
              at.nodes.lastOption.exists {
                case DynamicOptic.Node.Field(name) => name == "age"
                case _                             => false
              }
            case _ => false
          }
        )
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
    suite("MigrationExpr")(
      test("Literal evaluates to its value") {
        val expr   = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val input  = DynamicValue.Record(Chunk.empty)
        val result = expr.eval(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("FieldRef extracts field from record") {
        val expr   = MigrationExpr.FieldRef(DynamicOptic.root.field("name"))
        val input  = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result = expr.eval(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("test"))))
      },
      test("FieldRef fails on missing field") {
        val expr   = MigrationExpr.FieldRef(DynamicOptic.root.field("missing"))
        val input  = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("StringConcat concatenates strings") {
        val expr = MigrationExpr.StringConcat(
          MigrationExpr.FieldRef(DynamicOptic.root.field("first")),
          MigrationExpr.FieldRef(DynamicOptic.root.field("last"))
        )
        val input = DynamicValue.Record(
          Chunk(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("Hello")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("World"))
          )
        )
        val result = expr.eval(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("HelloWorld"))))
      },
      test("Arithmetic Add works with integers") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(15))))
      },
      test("Arithmetic Subtract works with integers") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          MigrationExpr.ArithmeticOp.Subtract
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(7))))
      },
      test("Arithmetic Multiply works with doubles") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(2.5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(4.0))),
          MigrationExpr.ArithmeticOp.Multiply
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(10.0))))
      },
      test("Arithmetic Divide works with longs") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(20L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(4L))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(5L))))
      },
      test("Arithmetic Divide by zero fails for integers") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Arithmetic works with floats") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.0f))),
          MigrationExpr.ArithmeticOp.Multiply
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(6.0f))))
      },
      test("Convert ToString converts int to string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("Convert ToInt converts string to int") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("123"))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(123))))
      },
      test("Convert ToInt fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("abc"))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToLong converts various types") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("Convert ToDouble converts various types") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(42.0))))
      },
      test("Convert ToFloat converts from double") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(
          result
            .map(_.asInstanceOf[DynamicValue.Primitive].value.asInstanceOf[PrimitiveValue.Float].value)
            .map(f => Math.abs(f - 3.14f) < 0.01f) == Right(true)
        )
      },
      test("Convert ToBoolean converts string true") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("true"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Convert ToBoolean converts int to boolean") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("Convert ToBigInt converts from long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(12345678901L))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345678901L)))))
      },
      test("Convert ToBigDecimal converts from float") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.14f))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isRight)
      },
      test("Conditional returns ifTrue when condition is true") {
        val expr = MigrationExpr.Conditional(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("yes"))))
      },
      test("Conditional returns ifFalse when condition is false") {
        val expr = MigrationExpr.Conditional(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("no"))))
      },
      test("Compare Eq returns true for equal values") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Eq
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Ne returns true for unequal values") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          MigrationExpr.CompareOp.Ne
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Lt returns true when left < right") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Lt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Le returns true when left <= right") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Le
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Gt returns true when left > right") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(7))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Gt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Ge returns true when left >= right") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Ge
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DefaultValue returns fallback") {
        val expr   = MigrationExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(0))))
      },
      test("DSL + operator creates Add arithmetic") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(4)))
        val expr   = a + b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(7))))
      },
      test("DSL ++ operator creates StringConcat") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.String("Hello")))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.String("World")))
        val expr   = a ++ b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("HelloWorld"))))
      },
      test("FieldRef fails when path expects Record but gets Primitive") {
        val expr   = MigrationExpr.FieldRef(DynamicOptic.root.field("name"))
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Arithmetic fails with non-primitive values") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert fails with non-primitive input") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("DSL * operator creates Multiply arithmetic") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(4)))
        val expr   = a * b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(12))))
      },
      test("DSL / operator creates Divide arithmetic") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(12)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(4)))
        val expr   = a / b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(3))))
      },
      test("DSL < operator creates Lt comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val expr   = a < b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL <= operator creates Le comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val expr   = a <= b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL > operator creates Gt comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val expr   = a > b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL >= operator creates Ge comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val expr   = a >= b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL toLong creates ToLong conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))).toLong
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("DSL toFloat creates ToFloat conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))).toFloat
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(42.0f))))
      },
      test("DSL toDouble creates ToDouble conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))).toDouble
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(42.0))))
      },
      test("DSL toBigInt creates ToBigInt conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Long(1234567890123L))).toBigInt
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1234567890123L)))))
      },
      test("DSL toBigDecimal creates ToBigDecimal conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))).toBigDecimal
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14)))))
      },
      test("DSL toBoolean creates ToBoolean conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))).toBoolean
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL asString creates ToString conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))).asString
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("DSL - operator creates Subtract arithmetic") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(10)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val expr   = a - b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(7))))
      },
      test("DSL === operator creates Eq comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val expr   = a === b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL =!= operator creates Ne comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val expr   = a =!= b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL toInt creates ToInt conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.String("42"))).toInt
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("MigrationExpr.field creates FieldRef") {
        val expr   = MigrationExpr.field(DynamicOptic.root.field("name"))
        val input  = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result = expr.eval(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("test"))))
      },
      test("Convert ToString works with Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(9876543210L))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("9876543210"))))
      },
      test("Convert ToString works with Double") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("3.14"))))
      },
      test("Convert ToString works with Float") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("2.5"))))
      },
      test("Convert ToString works with Boolean") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("true"))))
      },
      test("Convert ToString works with BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("123456789012345")))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("123456789012345"))))
      },
      test("Convert ToString works with BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("999.99")))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("999.99"))))
      },
      test("Convert ToString works with Char") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Char('X'))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("X"))))
      }
    ),
    suite("Expression-based MigrationActions")(
      test("TransformValueExpr evaluates expression") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.FieldRef(DynamicOptic.root),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          MigrationExpr.ArithmeticOp.Add
        )
        val action    = MigrationAction.TransformValueExpr(DynamicOptic.root.field("value"), expr)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "value").map(_._2) == Some(
            DynamicValue.Primitive(PrimitiveValue.Int(15))
          )
        )
      },
      test("TransformValueExpr reverse swaps expressions") {
        val expr        = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val reverseExpr = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val action      = MigrationAction.TransformValueExpr(DynamicOptic.root.field("x"), expr, Some(reverseExpr))
        val reversed    = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformValueExpr])
      },
      test("ChangeTypeExpr converts field type") {
        val expr =
          MigrationExpr.Convert(MigrationExpr.FieldRef(DynamicOptic.root), MigrationExpr.PrimitiveTargetType.ToString)
        val action    = MigrationAction.ChangeTypeExpr(DynamicOptic.root.field("value"), expr)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "value").map(_._2) == Some(
            DynamicValue.Primitive(PrimitiveValue.String("42"))
          )
        )
      },
      test("JoinExpr combines fields using expression") {
        val combineExpr = MigrationExpr.StringConcat(
          MigrationExpr.FieldRef(DynamicOptic.root.field("first")),
          MigrationExpr.StringConcat(
            MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" "))),
            MigrationExpr.FieldRef(DynamicOptic.root.field("last"))
          )
        )
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          combineExpr
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("fullName", DynamicValue.Primitive(PrimitiveValue.String("John Doe"))) => true
            case _                                                                       => false
          }
        )
      },
      test("JoinExpr fails when path doesn't end with Field") {
        val action    = MigrationAction.JoinExpr(DynamicOptic.root, Vector.empty, MigrationExpr.Literal(DynamicValue.Null))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk.empty)
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("SplitExpr splits field using expressions") {
        val firstExpr = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("John")))
        val lastExpr  = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Doe")))
        val action    = MigrationAction.SplitExpr(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Vector(firstExpr, lastExpr)
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk("fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "first"),
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "last")
        )
      },
      test("SplitExpr fails when targetPaths and splitExprs have different lengths") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          Vector(MigrationExpr.Literal(DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("source" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("SplitExpr fails when target path doesn't end with Field") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root),
          Vector(MigrationExpr.Literal(DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("source" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("JoinExpr reverse is SplitExpr") {
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          MigrationExpr.Literal(DynamicValue.Null)
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.SplitExpr])
      },
      test("SplitExpr reverse is JoinExpr") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          Vector(MigrationExpr.Literal(DynamicValue.Null))
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.JoinExpr])
      },
      test("SplitExpr reverse with combineExpr uses provided expression") {
        val combineExpr = MigrationExpr.StringConcat(
          MigrationExpr.FieldRef(DynamicOptic.root.field("a")),
          MigrationExpr.FieldRef(DynamicOptic.root.field("b"))
        )
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          Vector(
            MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("first"))),
            MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("second")))
          ),
          Some(combineExpr)
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.JoinExpr])
      }
    ),
    suite("Additional arithmetic and conversion coverage")(
      test("Arithmetic Multiply with Long") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(5L))),
          MigrationExpr.ArithmeticOp.Multiply
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(500L))))
      },
      test("Arithmetic Subtract with Double") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(10.5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.5))),
          MigrationExpr.ArithmeticOp.Subtract
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(7.0))))
      },
      test("Arithmetic Add with Float") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(1.5f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(4.0f))))
      },
      test("Arithmetic Subtract with Float") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(5.5f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.0f))),
          MigrationExpr.ArithmeticOp.Subtract
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.5f))))
      },
      test("Arithmetic Multiply with Float") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(4.0f))),
          MigrationExpr.ArithmeticOp.Multiply
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(10.0f))))
      },
      test("Arithmetic Divide with Float") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(9.0f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))))
      },
      test("Arithmetic Add with Long") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(50L))),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(150L))))
      },
      test("Arithmetic Subtract with Long") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(30L))),
          MigrationExpr.ArithmeticOp.Subtract
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(70L))))
      },
      test("Arithmetic Divide by zero with Long fails") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(0L))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Arithmetic with BigDecimal fails gracefully") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(100)))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(4)))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Arithmetic with BigInt fails gracefully") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1000)))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(234)))),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToInt from Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(42L))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("Convert ToDouble from Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(100.0))))
      },
      test("Convert ToBigDecimal from Int") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(42)))))
      },
      test("Convert ToLong from Double") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(99.9))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(99L))))
      },
      test("Convert ToBigInt from String") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("12345"))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345)))))
      },
      test("Convert ToFloat from String") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("3.14"))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.14f))))
      },
      test("Convert ToBoolean from 0") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("Convert ToBoolean from false string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("false"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("Convert ToString works with Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(123))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("123"))))
      },
      test("Convert ToString works with Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(42))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("Convert ToInt from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(100))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100))))
      },
      test("Convert ToInt from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(50))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(50))))
      },
      test("Convert ToInt from Float") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.14f))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(3))))
      },
      test("Convert ToInt from Double") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(9.99))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(9))))
      },
      test("Convert ToInt from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(999)))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(999))))
      },
      test("Convert ToInt from BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(777)))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(777))))
      },
      test("Convert ToLong from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(200))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(200L))))
      },
      test("Convert ToLong from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(10))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(10L))))
      },
      test("Convert ToLong from Float") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(5.5f))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(5L))))
      },
      test("Convert ToLong from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345L)))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(12345L))))
      },
      test("Convert ToLong from BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(9999L)))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(9999L))))
      },
      test("Convert ToDouble from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(30))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(30.0))))
      },
      test("Convert ToDouble from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(5))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(5.0))))
      },
      test("Convert ToDouble from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1000)))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(1000.0))))
      },
      test("Convert ToDouble from BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14)))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(3.14))))
      },
      test("Convert ToFloat from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(15))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(15.0f))))
      },
      test("Convert ToFloat from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(3))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))))
      },
      test("Convert ToFloat from Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(100.0f))))
      },
      test("Convert ToFloat from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(500)))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(500.0f))))
      },
      test("Convert ToFloat from BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(2.5)))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))))
      },
      test("Convert ToBigInt from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(42))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(42)))))
      },
      test("Convert ToBigInt from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(7))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(7)))))
      },
      test("Convert ToBigInt from Int") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(123))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(123)))))
      },
      test("Convert ToBigDecimal from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(50))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(50)))))
      },
      test("Convert ToBigDecimal from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(8))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(8)))))
      },
      test("Convert ToBigDecimal from Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(999L))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(999)))))
      },
      test("Convert ToBigDecimal from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345)))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(12345)))))
      },
      test("Convert ToInt fails for invalid Boolean") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToDouble fails for invalid Char") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Char('x'))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToBoolean from Long fails") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(1L))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToLong from Int") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("Convert ToBigDecimal from String") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("123.456"))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456")))))
      }
    ),
    suite("MigrationExpr error path coverage")(
      test("Conditional fails when condition is not boolean") {
        val expr = MigrationExpr.Conditional(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("StringConcat fails when operand is not primitive") {
        val expr = MigrationExpr.StringConcat(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("world")))
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Compare fails with non-primitive values") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.CompareOp.Eq
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("FieldRef fails with unsupported path node") {
        val expr  = MigrationExpr.FieldRef(DynamicOptic.root.elements)
        val input = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(expr.eval(input).isLeft)
      },
      test("Arithmetic fails with mismatched numeric types") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(2L))),
          MigrationExpr.ArithmeticOp.Add
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBoolean from string 'no' returns false") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        assertTrue(expr.eval(DynamicValue.Null) == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("Convert ToBoolean from string 'yes' returns true") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        assertTrue(expr.eval(DynamicValue.Null) == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Convert ToBoolean fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("maybe"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBigInt fails for unsupported type") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBigInt fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not_a_number"))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBigDecimal fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("xyz"))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToLong fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not_long"))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToDouble fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not_double"))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToFloat fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not_float"))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToFloat fails for unsupported type") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToLong fails for unsupported type") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBigDecimal fails for unsupported type") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Char('x'))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Type conversion fails with non-primitive value") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      }
    )
  )
}
