package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

object SchemaToJsonSchemaSpec extends SchemaBaseSpec {
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color

    implicit val schema: Schema[Color] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaToJsonSchemaSpec")(
    suite("Primitive types")(
      test("String schema produces string JSON Schema") {
        val schema     = Schema[String].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").string
        assertTrue(typeString == Right("string"))
      },
      test("Int schema produces integer JSON Schema") {
        val schema     = Schema[Int].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").string
        assertTrue(typeString == Right("integer"))
      },
      test("Boolean schema produces boolean JSON Schema") {
        val schema     = Schema[Boolean].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").string
        assertTrue(typeString == Right("boolean"))
      },
      test("Double schema produces number JSON Schema") {
        val schema     = Schema[Double].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").string
        assertTrue(typeString == Right("number"))
      }
    ),
    suite("Record types")(
      test("case class produces object JSON Schema with properties") {
        val schema     = Schema[Person].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").string
        val hasName    = json.get("properties").get("name").isSuccess
        val hasAge     = json.get("properties").get("age").isSuccess
        assertTrue(typeString == Right("object"), hasName, hasAge)
      },
      test("generated schema validates matching JSON") {
        val schema   = Schema[Person].toJsonSchema
        val validObj = Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))
        assertTrue(schema.conforms(validObj))
      },
      test("generated schema rejects non-matching JSON") {
        val schema     = Schema[Person].toJsonSchema
        val invalidObj = Json.obj("name" -> Json.number(123), "age" -> Json.str("not-a-number"))
        assertTrue(!schema.conforms(invalidObj))
      }
    ),
    suite("Collection types")(
      test("List[Int] produces array JSON Schema") {
        val schema     = Schema[List[Int]].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").string
        assertTrue(typeString == Right("array"))
      },
      test("Map[String, Int] produces object JSON Schema") {
        val schema     = Schema[Map[String, Int]].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").string
        assertTrue(typeString == Right("object"))
      }
    ),
    suite("Option types")(
      test("Option[String] produces nullable string JSON Schema") {
        val schema = Schema[Option[String]].toJsonSchema
        assertTrue(
          schema.conforms(Json.str("hello")),
          schema.conforms(Json.Null)
        )
      }
    ),
    suite("Enum/Variant types")(
      test("sealed trait enum produces enum JSON Schema") {
        val schema = Schema[Color].toJsonSchema
        assertTrue(
          schema.conforms(Json.str("Red")),
          schema.conforms(Json.str("Green")),
          schema.conforms(Json.str("Blue"))
        )
      }
    )
  )
}
