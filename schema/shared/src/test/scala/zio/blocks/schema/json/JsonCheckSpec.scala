package zio.blocks.schema.json

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema._
import zio.test._

object JsonCheckSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonCheckSpec")(
    suite("Method signatures")(
      test("check returns Option[SchemaError]") {
        val json: Json                  = Json.String("hello")
        val schema: JsonSchema          = JsonSchema.string()
        val result: Option[SchemaError] = json.check(schema)
        assertTrue(result.isEmpty)
      },
      test("conforms returns Boolean") {
        val json: Json         = Json.String("hello")
        val schema: JsonSchema = JsonSchema.string()
        val result: Boolean    = json.conforms(schema)
        assertTrue(result)
      },
      test("check returns Some(error) for invalid JSON") {
        val json   = Json.Number(42)
        val schema = JsonSchema.string()
        val result = json.check(schema)
        assertTrue(result.isDefined)
      },
      test("conforms returns false for invalid JSON") {
        val json   = Json.Number(42)
        val schema = JsonSchema.string()
        val result = json.conforms(schema)
        assertTrue(!result)
      }
    ),
    suite("Delegation to JsonSchema.check")(
      test("Json.check delegates to JsonSchema.check") {
        val json   = Json.String("hello")
        val schema = JsonSchema.string()
        assertTrue(json.check(schema) == schema.check(json))
      },
      test("Json.conforms delegates to JsonSchema.conforms") {
        val json   = Json.String("hello")
        val schema = JsonSchema.string()
        assertTrue(json.conforms(schema) == schema.conforms(json))
      },
      test("delegation works for complex schemas") {
        val json   = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
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
          Json.String("hello").conforms(schema),
          !Json.Number(42).conforms(schema),
          !Json.True.conforms(schema),
          !Json.Null.conforms(schema)
        )
      },
      test("integer validation") {
        val schema = JsonSchema.integer()
        assertTrue(
          Json.Number(42).conforms(schema),
          !Json.Number(3.14).conforms(schema),
          !Json.String("42").conforms(schema)
        )
      },
      test("number validation") {
        val schema = JsonSchema.number()
        assertTrue(
          Json.Number(42).conforms(schema),
          Json.Number(3.14).conforms(schema),
          !Json.String("42").conforms(schema)
        )
      },
      test("boolean validation") {
        val schema = JsonSchema.boolean
        assertTrue(
          Json.True.conforms(schema),
          Json.False.conforms(schema),
          !Json.Number(1).conforms(schema),
          !Json.String("true").conforms(schema)
        )
      },
      test("null validation") {
        val schema = JsonSchema.nullSchema
        assertTrue(
          Json.Null.conforms(schema),
          !Json.String("null").conforms(schema),
          !Json.Number(0).conforms(schema)
        )
      },
      test("array validation") {
        val schema = JsonSchema.array(items = Some(JsonSchema.integer()))
        assertTrue(
          Json.Array(Json.Number(1), Json.Number(2)).conforms(schema),
          Json.Array().conforms(schema),
          !Json.Array(Json.String("a")).conforms(schema),
          !Json.Object().conforms(schema)
        )
      },
      test("object validation") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        assertTrue(
          Json.Object("name" -> Json.String("Alice")).conforms(schema),
          !Json.Object("name" -> Json.Number(42)).conforms(schema),
          !Json.Object().conforms(schema),
          !Json.Array().conforms(schema)
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
          Json.String("ab").conforms(schema),
          Json.String("abcde").conforms(schema),
          !Json.String("a").conforms(schema),
          !Json.String("abcdef").conforms(schema)
        )
      },
      test("string pattern") {
        val schema = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$")))
        assertTrue(
          Json.String("abc").conforms(schema),
          !Json.String("ABC").conforms(schema),
          !Json.String("123").conforms(schema)
        )
      },
      test("number minimum/maximum") {
        val schema = JsonSchema.number(
          minimum = Some(BigDecimal(0)),
          maximum = Some(BigDecimal(100))
        )
        assertTrue(
          Json.Number(0).conforms(schema),
          Json.Number(50).conforms(schema),
          Json.Number(100).conforms(schema),
          !Json.Number(-1).conforms(schema),
          !Json.Number(101).conforms(schema)
        )
      },
      test("array minItems/maxItems") {
        val schema = JsonSchema.array(
          items = Some(JsonSchema.integer()),
          minItems = NonNegativeInt(1),
          maxItems = NonNegativeInt(3)
        )
        assertTrue(
          Json.Array(Json.Number(1)).conforms(schema),
          Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)).conforms(schema),
          !Json.Array().conforms(schema),
          !Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4)).conforms(schema)
        )
      },
      test("enum validation") {
        val schema = JsonSchema.Object(
          `enum` = Some(::(Json.String("red"), List(Json.String("green"), Json.String("blue"))))
        )
        assertTrue(
          Json.String("red").conforms(schema),
          Json.String("green").conforms(schema),
          !Json.String("yellow").conforms(schema)
        )
      },
      test("const validation") {
        val schema = JsonSchema.Object(const = Some(Json.String("fixed")))
        assertTrue(
          Json.String("fixed").conforms(schema),
          !Json.String("other").conforms(schema)
        )
      }
    ),
    suite("Nested validation via Json methods")(
      test("nested object validation") {
        val addressSchema = JsonSchema.obj(
          properties = Some(ChunkMap("city" -> JsonSchema.string())),
          required = Some(Set("city"))
        )
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("address" -> addressSchema)),
          required = Some(Set("address"))
        )
        val valid   = Json.Object("address" -> Json.Object("city" -> Json.String("NYC")))
        val invalid = Json.Object("address" -> Json.Object("city" -> Json.Number(123)))
        assertTrue(
          valid.conforms(schema),
          !invalid.conforms(schema)
        )
      },
      test("array items validation") {
        val itemSchema = JsonSchema.obj(
          properties = Some(ChunkMap("id" -> JsonSchema.integer())),
          required = Some(Set("id"))
        )
        val schema  = JsonSchema.array(items = Some(itemSchema))
        val valid   = Json.Array(Json.Object("id" -> Json.Number(1)), Json.Object("id" -> Json.Number(2)))
        val invalid = Json.Array(Json.Object("id" -> Json.String("not-int")))
        assertTrue(
          valid.conforms(schema),
          !invalid.conforms(schema)
        )
      }
    ),
    suite("Error information from check")(
      test("check provides error details for type mismatch") {
        val json   = Json.Number(42)
        val schema = JsonSchema.string()
        val error  = json.check(schema)
        assertTrue(
          error.isDefined,
          error.exists(_.message.nonEmpty)
        )
      },
      test("check provides error details for missing required field") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val error = Json.Object().check(schema)
        assertTrue(
          error.isDefined,
          error.exists(e => e.message.contains("name") || e.message.contains("required"))
        )
      },
      test("check provides error details for constraint violation") {
        val schema = JsonSchema.string(minLength = NonNegativeInt(5))
        val error  = Json.String("ab").check(schema)
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
          Json.String("hello").conforms(schema),
          Json.Number(42).conforms(schema),
          Json.True.conforms(schema),
          Json.Null.conforms(schema),
          Json.Array().conforms(schema),
          Json.Object().conforms(schema)
        )
      },
      test("False schema rejects everything") {
        val schema = JsonSchema.False
        assertTrue(
          !Json.String("hello").conforms(schema),
          !Json.Number(42).conforms(schema),
          !Json.True.conforms(schema),
          !Json.Null.conforms(schema),
          !Json.Array().conforms(schema),
          !Json.Object().conforms(schema)
        )
      }
    )
  )
}
