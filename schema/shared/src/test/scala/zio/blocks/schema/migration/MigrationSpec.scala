package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema, SchemaExpr}

object MigrationSpec extends ZIOSpecDefault {

  // Simple Schema Definitions for Testing
  case class UserV1(name: String, age: Int)
  case class UserV2(fullName: String, age: Int)
  case class UserV3(fullName: String, age: Int, active: Option[Boolean])

  // Mock Schemas (using implicit resolution)
  implicit val userV1Schema: Schema[UserV1] = Schema.derived
  implicit val userV2Schema: Schema[UserV2] = Schema.derived

  // Helper to create constant expressions using SchemaExpr.Literal
  def const[A](value: A)(implicit schema: Schema[A]): SchemaExpr[Any, A] =
    SchemaExpr.Literal(value, schema).asInstanceOf[SchemaExpr[Any, A]]

  def spec = suite("MigrationSpec")(
    test("RenameField") {
      val migration = DynamicMigration(
        Vector(
          MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName")
        )
      )

      val v1 = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        )
      )

      val expected = DynamicValue.Record(
        Vector(
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        )
      )

      assert(migration(v1))(isRight(equalTo(expected)))
    },
    test("AddField") {
      // Use implicit Schema[Boolean]
      val defaultExpr = const(true)

      val migration = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("active"), defaultExpr)
        )
      )

      val v2 = DynamicValue.Record(
        Vector(
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Bob"))
        )
      )

      val expected = DynamicValue.Record(
        Vector(
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "active"   -> DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        )
      )

      assert(migration(v2))(isRight(equalTo(expected)))
    },
    test("DropField") {
      val migration = DynamicMigration(
        Vector(
          MigrationAction.DropField(DynamicOptic.root.field("age"))
        )
      )

      val v1 = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Charlie")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(40))
        )
      )

      val expected = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Charlie"))
        )
      )

      assert(migration(v1))(isRight(equalTo(expected)))
    },
    test("Chained Migration") {
      // V1 -> V2: Rename name -> fullName
      // V2 -> V3: Add active field (Option[Boolean] = Some(true))

      val m1 = DynamicMigration(
        Vector(
          MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName")
        )
      )

      // Use implicit Schema[Option[Boolean]]
      // Note: We need to explicitly type the Option to help implicit search if needed,
      // but usually const(Option(true)) works if Schema.option exists.
      // If Schema.option is missing, we might need a workaround, but let's try implicit first.
      val defaultExpr = const(Option(true))

      val m2 = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("active"), defaultExpr)
        )
      )

      val composite = m1 ++ m2

      val start = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("Dave"))))

      val result = composite(start)

      // Verify structure contains both changes
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.map(_._1)))(
        isRight(contains("fullName") && contains("active"))
      )
    }
  )
}
