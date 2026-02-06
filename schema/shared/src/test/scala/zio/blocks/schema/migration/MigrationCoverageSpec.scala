package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationCoverageSpec extends SchemaBaseSpec {

  case class PersonV1(name: String, age: Int)
  object PersonV1 { implicit val schema: Schema[PersonV1] = Schema.derived }

  case class PersonV2(name: String, age: Int, email: String)
  object PersonV2 { implicit val schema: Schema[PersonV2] = Schema.derived }

  private val root = DynamicOptic.root
  private val litS = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default@test.com")))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationCoverageSpec")(
    suite("Migration.identity")(
      test("identity migration is empty") {
        val m = Migration.identity[PersonV1]
        assertTrue(m.isEmpty && !m.nonEmpty)
      },
      test("identity migration returns same value") {
        val m      = Migration.identity[PersonV1]
        val result = m(PersonV1("Alice", 30))
        assertTrue(result == Right(PersonV1("Alice", 30)))
      }
    ),
    suite("Migration.newBuilder")(
      test("creates an empty builder") {
        val builder = Migration.newBuilder[PersonV1, PersonV2]
        assertTrue(builder.actions.isEmpty)
      }
    ),
    suite("Migration.fromAction")(
      test("creates migration with single action") {
        val m = Migration.fromAction[PersonV1, PersonV1](MigrationAction.Identity)
        assertTrue(m.actions.length == 1)
      }
    ),
    suite("Migration.fromActions")(
      test("creates migration with multiple actions") {
        val m = Migration.fromActions[PersonV1, PersonV1](
          MigrationAction.Identity,
          MigrationAction.Identity
        )
        assertTrue(m.actions.length == 2)
      }
    ),
    suite("Migration.fromDynamic")(
      test("creates migration from DynamicMigration") {
        val dm = DynamicMigration(MigrationAction.Identity)
        val m  = Migration.fromDynamic[PersonV1, PersonV1](dm)
        assertTrue(m.nonEmpty)
      }
    ),
    suite("Migration.apply typed")(
      test("applies migration to typed value") {
        val m = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(root.field("email"), litS)
          .buildPartial
        val result = m(PersonV1("Alice", 30))
        assertTrue(result == Right(PersonV2("Alice", 30, "default@test.com")))
      },
      test("applyDynamic works with DynamicValue") {
        val m = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(root.field("email"), litS)
          .buildPartial
        val dv     = PersonV1.schema.toDynamicValue(PersonV1("Bob", 25))
        val result = m.applyDynamic(dv)
        assertTrue(result.isRight)
      }
    ),
    suite("Migration composition")(
      test("++ composes two migrations") {
        val m1       = Migration.identity[PersonV1]
        val m2       = Migration.fromAction[PersonV1, PersonV1](MigrationAction.Identity)
        val composed = m1 ++ m2
        assertTrue(composed.actions.length == 1)
      },
      test("andThen is alias for ++") {
        val m1       = Migration.identity[PersonV1]
        val m2       = Migration.identity[PersonV1]
        val composed = m1.andThen(m2)
        assertTrue(composed.isEmpty)
      }
    ),
    suite("Migration.reverse")(
      test("reverse creates inverse migration") {
        val m = Migration.fromAction[PersonV1, PersonV2](
          MigrationAction.AddField(root, "email", litS)
        )
        val rev = m.reverse
        assertTrue(rev.actions.head.isInstanceOf[MigrationAction.DropField])
      },
      test("reverse swaps source and target schemas") {
        val m = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(root.field("email"), litS)
          .buildPartial
        val rev = m.reverse
        assertTrue(rev.sourceSchema == PersonV2.schema && rev.targetSchema == PersonV1.schema)
      }
    ),
    suite("Migration isEmpty/nonEmpty")(
      test("isEmpty for identity") {
        assertTrue(Migration.identity[PersonV1].isEmpty)
      },
      test("nonEmpty for migration with actions") {
        val m = Migration.fromAction[PersonV1, PersonV1](MigrationAction.Identity)
        assertTrue(m.nonEmpty)
      }
    ),
    suite("Migration.actions")(
      test("returns underlying actions") {
        val actions = Vector(MigrationAction.Identity, MigrationAction.RenameField(root, "a", "b"))
        val m       = Migration.fromDynamic[PersonV1, PersonV1](new DynamicMigration(actions))
        assertTrue(m.actions == actions)
      }
    )
  )
}
