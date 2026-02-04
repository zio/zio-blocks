package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object DefaultValueResolutionSpec extends SchemaBaseSpec {

  final case class Source(a: Int)
  object Source {
    implicit val schema: Schema[Source] = Schema.derived
  }

  final case class TargetWithDefault(a: Int, b: String = "default")
  object TargetWithDefault {
    implicit val schema: Schema[TargetWithDefault] = Schema.derived
  }

  final case class TargetWithoutDefault(a: Int, b: String)
  object TargetWithoutDefault {
    implicit val schema: Schema[TargetWithoutDefault] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("DefaultValueResolutionSpec")(
    test("resolves DefaultValue for AddField at build time") {
      val migration =
        MigrationBuilder[Source, TargetWithDefault]
          .addField(DynamicOptic.root.field("b"), DynamicSchemaExpr.DefaultValue)
          .buildPartial

      val addFieldExpr = migration.actions.collectFirst { case MigrationAction.AddField(_, "b", expr) => expr }

      assertTrue(
        addFieldExpr.contains(
          DynamicSchemaExpr.ResolvedDefault(DynamicValue.Primitive(PrimitiveValue.String("default")))
        ),
        migration(Source(1)) == Right(TargetWithDefault(1, "default"))
      )
    },
    test("throws if DefaultValue is used but no schema default exists") {
      val result = scala.util.Try {
        MigrationBuilder[Source, TargetWithoutDefault]
          .addField(DynamicOptic.root.field("b"), DynamicSchemaExpr.DefaultValue)
          .buildPartial
      }

      assertTrue(result.isFailure)
    }
  )
}

