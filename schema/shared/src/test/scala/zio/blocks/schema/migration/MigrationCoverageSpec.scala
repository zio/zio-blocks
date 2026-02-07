package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Supplementary coverage tests for [[Migration]].
 *
 * Covers factory methods and accessors not exercised by [[MigrationSpec]],
 * which already covers identity, apply, compose, and reverse semantics.
 */
object MigrationCoverageSpec extends SchemaBaseSpec {

  case class PersonV1(name: String, age: Int)
  object PersonV1 { implicit val schema: Schema[PersonV1] = Schema.derived }

  case class PersonV2(name: String, age: Int, email: String)
  object PersonV2 { implicit val schema: Schema[PersonV2] = Schema.derived }

  private val root = DynamicOptic.root
  private val litS = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default@test.com")))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationCoverageSpec")(
    test("newBuilder creates an empty builder") {
      val builder = Migration.newBuilder[PersonV1, PersonV2]
      assertTrue(builder.actions.isEmpty)
    },
    test("fromAction creates migration with single action") {
      val m = Migration.fromAction[PersonV1, PersonV1](MigrationAction.Identity)
      assertTrue(m.actions.length == 1)
    },
    test("fromDynamic wraps a DynamicMigration") {
      val dm = DynamicMigration(MigrationAction.Identity)
      val m  = Migration.fromDynamic[PersonV1, PersonV1](dm)
      assertTrue(m.nonEmpty)
    },
    test("applyDynamic works with DynamicValue") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .addField(root.field("email"), litS)
        .buildPartial
      val dv     = PersonV1.schema.toDynamicValue(PersonV1("Bob", 25))
      val result = m.applyDynamic(dv)
      assertTrue(result.isRight)
    },
    test("actions accessor returns underlying actions") {
      val actions = Vector(MigrationAction.Identity, MigrationAction.RenameField(root, "a", "b"))
      val m       = Migration.fromDynamic[PersonV1, PersonV1](new DynamicMigration(actions))
      assertTrue(m.actions == actions)
    }
  )
}
