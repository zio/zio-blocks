package zio.blocks.schema.json

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._
import zio.blocks.schema.json.JsonSchema.ObjectSchema

object JsonSchemaSpec extends SchemaBaseSpec {
  def spec = suite("JsonSchemaSpec")(
    test("derive primitive string") {
      val schema = Schema[String]
      val jsonSchema = schema.toJsonSchema
      assertTrue(jsonSchema.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.String)))
    },
    test("derive primitive int") {
      val schema = Schema[Int]
      val jsonSchema = schema.toJsonSchema
      assertTrue(jsonSchema.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.Number)))
    },
    test("derive sequence") {
      val schema = Schema[List[String]]
      val jsonSchema = schema.toJsonSchema
      assertTrue(jsonSchema.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.Array)))
    }
  )
}