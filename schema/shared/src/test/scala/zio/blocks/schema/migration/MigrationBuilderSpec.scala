package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {

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

  case class UserV1(name: String, email: String)
  object UserV1 {
    implicit val schema: Schema[UserV1] = Schema.derived[UserV1]
  }

  case class UserV2(fullName: String, email: String, age: Int)
  object UserV2 {
    implicit val schema: Schema[UserV2] = Schema.derived[UserV2]
  }

  case class Point2D(x: Int, y: Int)
  object Point2D {
    implicit val schema: Schema[Point2D] = Schema.derived[Point2D]
  }

  case class Point2DWithZ(x: Int, y: Int, z: Int)
  object Point2DWithZ {
    implicit val schema: Schema[Point2DWithZ] = Schema.derived[Point2DWithZ]
  }

  def spec = suite("MigrationBuilderSpec")(
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
    )
  )
}
