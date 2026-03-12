package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test.Assertion._
import zio.test._

object MigrationIntegrationSpec extends SchemaBaseSpec {

  case class UserV1(name: String, age: Int)
  case class UserV2(fullName: String, age: Int, email: String)

  implicit val schemaV1: Schema[UserV1] = Schema.derived[UserV1]
  implicit val schemaV2: Schema[UserV2] = Schema.derived[UserV2]

  case class ConfigV1(host: String, port: Int, debug: Boolean)
  case class ConfigV2(host: String, port: Int)

  implicit val schemaConfigV1: Schema[ConfigV1] = Schema.derived[ConfigV1]
  implicit val schemaConfigV2: Schema[ConfigV2] = Schema.derived[ConfigV2]

  private val strDV  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
  private val boolDV = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationIntegrationSpec")(
    endToEndSuite,
    roundTripSuite,
    dynamicMigrationSuite
  )

  private val endToEndSuite = suite("Typed migration")(
    test("rename + add field") {
      val migration = Migration.fromActions[UserV1, UserV2](
        MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"),
        MigrationAction.AddField(DynamicOptic.root.field("email"), strDV)
      )
      val result = migration(UserV1("Alice", 30))
      assert(result)(isRight(equalTo(UserV2("Alice", 30, ""))))
    },
    test("drop field") {
      val migration = Migration.fromActions[ConfigV1, ConfigV2](
        MigrationAction.DropField(DynamicOptic.root.field("debug"), boolDV)
      )
      val result = migration(ConfigV1("localhost", 8080, true))
      assert(result)(isRight(equalTo(ConfigV2("localhost", 8080))))
    },
    test("composition with identity") {
      val m1 = Migration.fromActions[UserV1, UserV2](
        MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"),
        MigrationAction.AddField(DynamicOptic.root.field("email"), strDV)
      )
      val m2       = Migration.identity[UserV2]
      val composed = m1 ++ m2
      val result   = composed(UserV1("Bob", 25))
      assert(result)(isRight(equalTo(UserV2("Bob", 25, ""))))
    },
    test("reverse migration") {
      val forward = Migration.fromActions[UserV1, UserV2](
        MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"),
        MigrationAction.AddField(DynamicOptic.root.field("email"), strDV)
      )
      val backward = forward.reverse
      val result   = backward(UserV2("Charlie", 40, "charlie@example.com"))
      assert(result)(isRight(equalTo(UserV1("Charlie", 40))))
    }
  )

  private val roundTripSuite = suite("Round-trip")(
    test("forward then reverse recovers original value") {
      val forward = Migration.fromActions[ConfigV1, ConfigV2](
        MigrationAction.DropField(DynamicOptic.root.field("debug"), boolDV)
      )
      val backward = forward.reverse
      val original = ConfigV1("db.example.com", 5432, false)
      val result   = for {
        v2 <- forward(original)
        v1 <- backward(v2)
      } yield v1
      assert(result)(isRight(equalTo(original)))
    }
  )

  private val dynamicMigrationSuite = suite("DynamicMigration extraction")(
    test("actions are case classes") {
      val migration = Migration.fromActions[UserV1, UserV2](
        MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"),
        MigrationAction.AddField(DynamicOptic.root.field("email"), strDV)
      )
      val actions = migration.actions
      assert(actions.length)(equalTo(2)) &&
      assert(actions(0))(isSubtype[MigrationAction.Rename](anything)) &&
      assert(actions(1))(isSubtype[MigrationAction.AddField](anything))
    },
    test("DynamicMigration can be extracted and reused") {
      val migration = Migration.fromActions[UserV1, UserV2](
        MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"),
        MigrationAction.AddField(DynamicOptic.root.field("email"), strDV)
      )
      val dm     = migration.dynamicMigration
      val dv     = schemaV1.toDynamicValue(UserV1("Diana", 35))
      val result = dm(dv).flatMap(schemaV2.fromDynamicValue)
      assert(result)(isRight(equalTo(UserV2("Diana", 35, ""))))
    }
  )
}
