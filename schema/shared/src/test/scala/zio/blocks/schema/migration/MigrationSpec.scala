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
        import UserV1._
        import UserV2._

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
        import UserV2._
        import UserV1._

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
        import UserV1._
        import UserV2._

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
        import Point2D._
        import Point2DWithZ._

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
        import UserV1._
        import UserV2._

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
    )
  )
}
