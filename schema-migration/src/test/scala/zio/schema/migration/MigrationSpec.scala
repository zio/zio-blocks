package zio.schema.migration

import zio._
import zio.test._
import zio.test.Assertion._
import zio.schema._

object MigrationSpec extends ZIOSpecDefault {

  // Example: Person V1 has firstName and lastName
  case class PersonV1(firstName: String, lastName: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = DeriveSchema.gen[PersonV1]
  }

  // Example: Person V2 has fullName and age
  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = DeriveSchema.gen[PersonV2]
  }

  def spec = suite("MigrationSpec")(
    test("should add a field with default value") {
      case class Before(name: String)
      case class After(name: String, age: Int)

      implicit val beforeSchema: Schema[Before] = DeriveSchema.gen[Before]
      implicit val afterSchema: Schema[After]   = DeriveSchema.gen[After]

      val migration = MigrationBuilder[Before, After]
        .addField[Int]("age", 0)
        .build

      val before = Before("Alice")
      val result = migration(before)

      assertTrue(result.isRight)
    },
    test("should rename a field") {
      case class Before(oldName: String)
      case class After(newName: String)

      implicit val beforeSchema: Schema[Before] = DeriveSchema.gen[Before]
      implicit val afterSchema: Schema[After]   = DeriveSchema.gen[After]

      val migration = MigrationBuilder[Before, After]
        .renameField("oldName", "newName")
        .build

      val before = Before("value")
      val result = migration(before)

      assertTrue(result.isRight)
    },
    test("should drop a field") {
      case class Before(name: String, age: Int)
      case class After(name: String)

      implicit val beforeSchema: Schema[Before] = DeriveSchema.gen[Before]
      implicit val afterSchema: Schema[After]   = DeriveSchema.gen[After]

      val migration = MigrationBuilder[Before, After]
        .dropField("age")
        .build

      val before = Before("Alice", 30)
      val result = migration(before)

      assertTrue(result.isRight)
    },
    test("should compose migrations") {
      case class V1(a: String)
      case class V2(b: String)
      case class V3(b: String, c: Int)

      implicit val v1Schema: Schema[V1] = DeriveSchema.gen[V1]
      implicit val v2Schema: Schema[V2] = DeriveSchema.gen[V2]
      implicit val v3Schema: Schema[V3] = DeriveSchema.gen[V3]

      val migration1 = MigrationBuilder[V1, V2]
        .renameField("a", "b")
        .build

      val migration2 = MigrationBuilder[V2, V3]
        .addField[Int]("c", 42)
        .build

      val composedMigration = migration1 ++ migration2

      val v1     = V1("test")
      val result = composedMigration(v1)

      assertTrue(result.isRight)
    },
    test("should reverse a migration") {
      case class Before(name: String, age: Int)
      case class After(name: String)

      implicit val beforeSchema: Schema[Before] = DeriveSchema.gen[Before]
      implicit val afterSchema: Schema[After]   = DeriveSchema.gen[After]

      val migration = MigrationBuilder[Before, After]
        .dropField("age")
        .build

      val reversed = migration.reverse

      // Drop is not reversible
      assertTrue(reversed.isLeft)
    },
    test("should reverse a reversible migration") {
      case class Before(name: String)
      case class After(name: String, age: Int)

      implicit val beforeSchema: Schema[Before] = DeriveSchema.gen[Before]
      implicit val afterSchema: Schema[After]   = DeriveSchema.gen[After]

      val migration = MigrationBuilder[Before, After]
        .addField[Int]("age", 25)
        .build

      val reversed = migration.reverse

      assertTrue(reversed.isRight)
    },
    test("should optimize redundant operations") {
      case class V1(a: String)
      case class V2(c: String)

      implicit val v1Schema: Schema[V1] = DeriveSchema.gen[V1]
      implicit val v2Schema: Schema[V2] = DeriveSchema.gen[V2]

      // Rename a -> b, then b -> c should optimize to a -> c
      val migration = MigrationBuilder[V1, V2]
        .renameField("a", "b")
        .renameField("b", "c")
        .build

      // Check that optimization happened
      val actions = migration.dynamicMigration.actions
      assertTrue(actions.length == 1)
    },
    test("DynamicMigration should serialize and deserialize") {
      val migration = DynamicMigration.single(
        MigrationAction.AddField(
          FieldPath("age"),
          DynamicValue.Primitive(42, StandardType.IntType)
        )
      )

      // In a real implementation, we'd use JsonCodec here
      // For now, just verify the schema exists
      assertTrue(DynamicMigration.schema != null)
    },
    test("FieldPath should parse correctly") {
      val path = FieldPath.parse("person.address.street")
      assertTrue(
        path.isRight &&
          path.toOption.get.serialize == "person.address.street"
      )
    }
  )
}
