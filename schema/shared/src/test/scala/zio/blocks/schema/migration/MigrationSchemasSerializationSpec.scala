package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.json.{DiscriminatorKind, JsonBinaryCodecDeriver}
import zio.test._

object MigrationSchemasSerializationSpec extends SchemaBaseSpec {
  import MigrationSchemas._

  // Use a discriminator for variant types so decoding works correctly
  private val deriver = JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("_type"))

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationSchemasSerializationSpec")(
      test("DynamicMigration encodes/decodes via JsonBinaryCodec") {
        val migration =
          DynamicMigration(
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root,
                "country",
                DynamicSchemaExpr.ResolvedDefault(DynamicValue.Primitive(PrimitiveValue.String("US")))
              ),
              MigrationAction.RenameField(DynamicOptic.root, "name", "fullName"),
              MigrationAction.TransformValue(
                DynamicOptic.root.field("age"),
                DynamicSchemaExpr.Arithmetic(
                  DynamicSchemaExpr.Path(DynamicOptic.root.field("age")),
                  DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
                  DynamicSchemaExpr.ArithmeticOperator.Multiply
                ),
                DynamicSchemaExpr.Path(DynamicOptic.root.field("age"))
              )
            )
          )

        val codec = Schema[DynamicMigration].derive(deriver)
        val json  = codec.encodeToString(migration)

        assertTrue(codec.decode(json) == Right(migration))
      }
    )
}
