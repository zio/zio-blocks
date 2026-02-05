package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.chunk.Chunk
import zio.test._

object MigrationValidatorOptionalitySpec extends SchemaBaseSpec {

  final case class Source(a: Int)
  object Source {
    implicit val schema: Schema[Source] = Schema.derived
  }

  final case class Target(a: Int, extra: Option[String])
  object Target {
    implicit val schema: Schema[Target] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationValidatorOptionalitySpec")(
    test("detects optionality mismatch even when structure is Dynamic") {
      val actions = Vector(
        MigrationAction.AddField(
          DynamicOptic.root,
          "extra",
          DynamicSchemaExpr.Literal(DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty)))
        )
      )

      val validation = MigrationValidator.validate(Source.schema, Target.schema, actions)

      assertTrue(
        !validation.isValid,
        validation.errors.exists(_.contains("Optionality mismatch"))
      )
    }
  )
}
