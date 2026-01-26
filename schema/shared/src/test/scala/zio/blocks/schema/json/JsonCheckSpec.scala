package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

object JsonCheckSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonCheckSpec")(
    suite("Method signatures")(
      test("check returns Option[SchemaError]") {
        val json: Json                  = Json.str("hello")
        val schema: JsonSchema          = JsonSchema.string()
        val result: Option[SchemaError] = json.check(schema)
        assertTrue(result.isEmpty)
      },
      test("conforms returns Boolean") {
        val json: Json         = Json.str("hello")
        val schema: JsonSchema = JsonSchema.string()
        val result: Boolean    = json.conforms(schema)
        assertTrue(result)
      },
      test("check returns Some(error) for invalid JSON") {
        val json   = Json.number(42)
        val schema = JsonSchema.string()
        val result = json.check(schema)
        assertTrue(result.isDefined)
      },
      test("conforms returns false for invalid JSON") {
        val json   = Json.number(42)
        val schema = JsonSchema.string()
        val result = json.conforms(schema)
        assertTrue(!result)
      }
    ),
    suite("Delegation to JsonSchema.check")(
      test("Json.check delegates to JsonSchema.check") {
        val json   = Json.str("hello")
        val schema = JsonSchema.string()
        assertTrue(json.check(schema) == schema.check(json))
      },
      test("Json.conforms delegates to JsonSchema.conforms") {
        val json   = Json.str("hello")
        val schema = JsonSchema.string()
        assertTrue(json.conforms(schema) == schema.conforms(json))
      },
      test("delegation works for complex schemas") {
        val json   = Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))
        val schema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        assertTrue(
          json.check(schema) == schema.check(json),
          json.conforms(schema) == schema.conforms(json)
        )
      }
    ),
    suite("Type validation via Json methods")(
      test("string validation") {
        val schema = JsonSchema.string()
        assertTrue(
          Json.str("hello").conforms(schema),
          !Json.number(42).conforms(schema),
          !Json.True.conforms(schema),
          !Json.Null.conforms(schema)
        )
      },
      test("integer validation") {
        val schema = JsonSchema.integer()
        assertTrue(
          Json.number(42).conforms(schema),
          !Json.number(3.14).conforms(schema),
          !Json.str("42").conforms(schema)
        )
      },
      test("number validation") {
        val schema = JsonSchema.number()
        assertTrue(
          Json.number(42).conforms(schema),
          Json.number(3.14).conforms(schema),
          !Json.str("42").conforms(schema)
        )
      },
      test("boolean validation") {
        val schema = JsonSchema.boolean
        assertTrue(
          Json.True.conforms(schema),
          Json.False.conforms(schema),
          !Json.number(1).conforms(schema),
          !Json.str("true").conforms(schema)
        )
      },
      test("null validation") {
        val schema = JsonSchema.`null`
        assertTrue(
          Json.Null.conforms(schema),
          !Json.str("null").conforms(schema),
          !Json.number(0).conforms(schema)
        )
      },
      test("array validation") {
        val schema = JsonSchema.array(items = Some(JsonSchema.integer()))
        assertTrue(
          Json.arr(Json.number(1), Json.number(2)).conforms(schema),
          Json.arr().conforms(schema),
          !Json.arr(Json.str("a")).conforms(schema),
          !Json.obj().conforms(schema)
        )
      },
      test("object validation") {
        val schema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        assertTrue(
          Json.obj("name" -> Json.str("Alice")).conforms(schema),
          !Json.obj("name" -> Json.number(42)).conforms(schema),
          !Json.obj().conforms(schema),
          !Json.arr().conforms(schema)
        )
      }
    ),
    suite("Constraint validation via Json methods")(
      test("string minLength/maxLength") {
        val schema = JsonSchema.string(
          minLength = NonNegativeInt(2),
          maxLength = NonNegativeInt(5)
        )
        assertTrue(
          Json.str("ab").conforms(schema),
          Json.str("abcde").conforms(schema),
          !Json.str("a").conforms(schema),
          !Json.str("abcdef").conforms(schema)
        )
      },
      test("string pattern") {
        val schema = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$")))
        assertTrue(
          Json.str("abc").conforms(schema),
          !Json.str("ABC").conforms(schema),
          !Json.str("123").conforms(schema)
        )
      },
      test("number minimum/maximum") {
        val schema = JsonSchema.number(
          minimum = Some(BigDecimal(0)),
          maximum = Some(BigDecimal(100))
        )
        assertTrue(
          Json.number(0).conforms(schema),
          Json.number(50).conforms(schema),
          Json.number(100).conforms(schema),
          !Json.number(-1).conforms(schema),
          !Json.number(101).conforms(schema)
        )
      },
      test("array minItems/maxItems") {
        val schema = JsonSchema.array(
          items = Some(JsonSchema.integer()),
          minItems = NonNegativeInt(1),
          maxItems = NonNegativeInt(3)
        )
        assertTrue(
          Json.arr(Json.number(1)).conforms(schema),
          Json.arr(Json.number(1), Json.number(2), Json.number(3)).conforms(schema),
          !Json.arr().conforms(schema),
          !Json.arr(Json.number(1), Json.number(2), Json.number(3), Json.number(4)).conforms(schema)
        )
      },
      test("enum validation") {
        val schema = JsonSchema.Object(
          `enum` = Some(::(Json.str("red"), List(Json.str("green"), Json.str("blue"))))
        )
        assertTrue(
          Json.str("red").conforms(schema),
          Json.str("green").conforms(schema),
          !Json.str("yellow").conforms(schema)
        )
      },
      test("const validation") {
        val schema = JsonSchema.Object(const = Some(Json.str("fixed")))
        assertTrue(
          Json.str("fixed").conforms(schema),
          !Json.str("other").conforms(schema)
        )
      }
    ),
    suite("Nested validation via Json methods")(
      test("nested object validation") {
        val addressSchema = JsonSchema.obj(
          properties = Some(Map("city" -> JsonSchema.string())),
          required = Some(Set("city"))
        )
        val schema = JsonSchema.obj(
          properties = Some(Map("address" -> addressSchema)),
          required = Some(Set("address"))
        )
        val valid   = Json.obj("address" -> Json.obj("city" -> Json.str("NYC")))
        val invalid = Json.obj("address" -> Json.obj("city" -> Json.number(123)))
        assertTrue(
          valid.conforms(schema),
          !invalid.conforms(schema)
        )
      },
      test("array items validation") {
        val itemSchema = JsonSchema.obj(
          properties = Some(Map("id" -> JsonSchema.integer())),
          required = Some(Set("id"))
        )
        val schema  = JsonSchema.array(items = Some(itemSchema))
        val valid   = Json.arr(Json.obj("id" -> Json.number(1)), Json.obj("id" -> Json.number(2)))
        val invalid = Json.arr(Json.obj("id" -> Json.str("not-int")))
        assertTrue(
          valid.conforms(schema),
          !invalid.conforms(schema)
        )
      }
    ),
    suite("Error information from check")(
      test("check provides error details for type mismatch") {
        val json   = Json.number(42)
        val schema = JsonSchema.string()
        val error  = json.check(schema)
        assertTrue(
          error.isDefined,
          error.exists(_.message.nonEmpty)
        )
      },
      test("check provides error details for missing required field") {
        val schema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val error = Json.obj().check(schema)
        assertTrue(
          error.isDefined,
          error.exists(e => e.message.contains("name") || e.message.contains("required"))
        )
      },
      test("check provides error details for constraint violation") {
        val schema = JsonSchema.string(minLength = NonNegativeInt(5))
        val error  = Json.str("ab").check(schema)
        assertTrue(
          error.isDefined,
          error.exists(_.message.nonEmpty)
        )
      }
    ),
    suite("Boolean schemas via Json methods")(
      test("True schema accepts everything") {
        val schema = JsonSchema.True
        assertTrue(
          Json.str("hello").conforms(schema),
          Json.number(42).conforms(schema),
          Json.True.conforms(schema),
          Json.Null.conforms(schema),
          Json.arr().conforms(schema),
          Json.obj().conforms(schema)
        )
      },
      test("False schema rejects everything") {
        val schema = JsonSchema.False
        assertTrue(
          !Json.str("hello").conforms(schema),
          !Json.number(42).conforms(schema),
          !Json.True.conforms(schema),
          !Json.Null.conforms(schema),
          !Json.arr().conforms(schema),
          !Json.obj().conforms(schema)
        )
      }
    )
  )
}
