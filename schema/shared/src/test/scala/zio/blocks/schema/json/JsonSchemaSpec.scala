package zio.blocks.schema.json

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._
import zio.blocks.schema.json.JsonSchema.ObjectSchema

object JsonSchemaSpec extends SchemaBaseSpec {

  case class User(name: String, age: Int)
  object User {
    implicit val schema: Schema[User] = Schema.derived[User]
  }

  sealed trait Payment
  case class CreditCard(number: String) extends Payment
  case class PayPal(email: String)      extends Payment
  object Payment {
    implicit val schema: Schema[Payment] = Schema.derived[Payment]
  }

  def spec = suite("JsonSchemaSpec")(
    test("derive primitive string") {
      val schema     = Schema[String]
      val jsonSchema = schema.toJsonSchema
      assertTrue(jsonSchema.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.String)))
    },
    test("derive primitive int") {
      val schema     = Schema[Int]
      val jsonSchema = schema.toJsonSchema
      assertTrue(jsonSchema.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.Number)))
    },
    test("derive boolean") {
      val schema     = Schema[Boolean]
      val jsonSchema = schema.toJsonSchema
      assertTrue(jsonSchema.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.Boolean)))
    },
    test("derive sequence") {
      val schema     = Schema[List[String]]
      val jsonSchema = schema.toJsonSchema
      val obj        = jsonSchema.asInstanceOf[ObjectSchema]
      assertTrue(
        obj.schemaType == Some(List(JsonType.Array)),
        obj.items.exists(_.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.String)))
      )
    },
    test("derive record") {
      val schema     = Schema[User]
      val jsonSchema = schema.toJsonSchema
      val obj        = jsonSchema.asInstanceOf[ObjectSchema]
      assertTrue(
        obj.schemaType == Some(List(JsonType.Object)),
        obj.required.exists(_.contains("name")),
        obj.required.exists(_.contains("age")),
        obj.properties.exists(
          _.get("name").exists(_.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.String)))
        ),
        obj.properties.exists(
          _.get("age").exists(_.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.Number)))
        )
      )
    },
    test("derive map") {
      val schema     = Schema[Map[String, Int]]
      val jsonSchema = schema.toJsonSchema
      val obj        = jsonSchema.asInstanceOf[ObjectSchema]
      assertTrue(
        obj.schemaType == Some(List(JsonType.Object)),
        obj.additionalProperties.exists(_.asInstanceOf[ObjectSchema].schemaType == Some(List(JsonType.Number)))
      )
    },
    test("derive variant (sealed trait)") {
      val schema     = Schema[Payment]
      val jsonSchema = schema.toJsonSchema
      val obj        = jsonSchema.asInstanceOf[ObjectSchema]
      assertTrue(
        obj.oneOf.exists(_.size == 2)
      )
    }
  )
}
