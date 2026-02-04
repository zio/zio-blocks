package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.json.{DiscriminatorKind, JsonBinaryCodecDeriver}
import zio.test._

object DynamicSchemaExprSerializationSpec extends SchemaBaseSpec {
  import MigrationSchemas._

  // Use a discriminator for variant types so decoding works correctly
  private val deriver = JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("_type"))

  def spec: Spec[TestEnvironment, Any] =
    suite("DynamicSchemaExprSerializationSpec")(
      test("Literal encodes/decodes via JsonBinaryCodec") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val codec                   = Schema[DynamicSchemaExpr].derive(deriver)
        val json                    = codec.encodeToString(expr)
        assertTrue(codec.decode(json) == Right(expr))
      },
      test("Path encodes/decodes via JsonBinaryCodec") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Path(DynamicOptic.root.field("test"))
        val codec                   = Schema[DynamicSchemaExpr].derive(deriver)
        val json                    = codec.encodeToString(expr)
        assertTrue(codec.decode(json) == Right(expr))
      },
      test("Arithmetic encodes/decodes via JsonBinaryCodec") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Path(DynamicOptic.root.field("age")),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        val codec = Schema[DynamicSchemaExpr].derive(deriver)
        val json  = codec.encodeToString(expr)
        assertTrue(codec.decode(json) == Right(expr))
      }
    )
}
