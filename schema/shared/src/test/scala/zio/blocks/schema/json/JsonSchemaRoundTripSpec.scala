package zio.blocks.schema.json

import zio.test._

/**
 * Tests for JsonSchema serialization round-trips.
 *
 * Verifies that toJson/fromJson are inverses for all schema types.
 */
object JsonSchemaRoundTripSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaRoundTripSpec")(
    suite("Boolean schema round-trip")(
      test("true schema round-trips correctly") {
        val schema = JsonSchema.True
        val json   = schema.toJson
        val parsed = JsonSchema.fromJson(json)
        assertTrue(
          json == Json.True,
          parsed == Right(JsonSchema.True)
        )
      },
      test("false schema round-trips correctly") {
        val schema = JsonSchema.False
        val json   = schema.toJson
        val parsed = JsonSchema.fromJson(json)
        assertTrue(
          json == Json.False,
          parsed == Right(JsonSchema.False)
        )
      }
    ),
    suite("Empty schema equivalence")(
      test("empty object parses to SchemaObject.empty") {
        val emptyJson = Json.obj()
        val parsed    = JsonSchema.fromJson(emptyJson)
        assertTrue(parsed == Right(JsonSchema.SchemaObject.empty))
      },
      test("SchemaObject.empty is equivalent to True for validation") {
        val json = Json.str("any value")
        assertTrue(
          JsonSchema.True.validate(json).isRight,
          JsonSchema.SchemaObject.empty.validate(json).isRight
        )
      }
    ),
    suite("Type schemas round-trip")(
      test("string type round-trips") {
        val schema = JsonSchema.string
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(parsed.isRight, parsed.exists(_.validate(Json.str("test")).isRight))
      },
      test("number type round-trips") {
        val schema = JsonSchema.number
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(parsed.isRight, parsed.exists(_.validate(Json.number(42)).isRight))
      },
      test("integer type round-trips") {
        val schema = JsonSchema.integer
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(parsed.isRight, parsed.exists(_.validate(Json.number(42)).isRight))
      },
      test("boolean type round-trips") {
        val schema = JsonSchema.boolean
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(parsed.isRight, parsed.exists(_.validate(Json.True).isRight))
      },
      test("null type round-trips") {
        val schema = JsonSchema.`null`
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(parsed.isRight, parsed.exists(_.validate(Json.Null).isRight))
      },
      test("array type round-trips") {
        val schema = JsonSchema.array
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(parsed.isRight, parsed.exists(_.validate(Json.arr()).isRight))
      },
      test("object type round-trips") {
        val schema = JsonSchema.`object`
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(parsed.isRight, parsed.exists(_.validate(Json.obj()).isRight))
      }
    ),
    suite("Constraint schemas round-trip")(
      test("string with minLength/maxLength round-trips") {
        val schema = JsonSchema.string.withMinLength(1).withMaxLength(100)
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.str("hello")).isRight),
          parsed.exists(_.validate(Json.str("")).isLeft)
        )
      },
      test("string with pattern round-trips") {
        val schema = JsonSchema.string.withPattern("^[a-z]+$")
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.str("abc")).isRight),
          parsed.exists(_.validate(Json.str("ABC")).isLeft)
        )
      },
      test("number with range round-trips") {
        val schema = JsonSchema.number.withRange(0, 100)
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.number(50)).isRight),
          parsed.exists(_.validate(Json.number(-1)).isLeft)
        )
      },
      test("array with items round-trips") {
        val schema = JsonSchema.array(JsonSchema.string)
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.arr(Json.str("a"))).isRight),
          parsed.exists(_.validate(Json.arr(Json.number(1))).isLeft)
        )
      },
      test("object with properties round-trips") {
        val schema = JsonSchema
          .`object`(
            "name" -> JsonSchema.string,
            "age"  -> JsonSchema.integer
          )
          .withRequired("name")
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.obj("name" -> Json.str("Alice"))).isRight),
          parsed.exists(_.validate(Json.obj()).isLeft)
        )
      }
    ),
    suite("Composition schemas round-trip")(
      test("allOf round-trips") {
        val schema = JsonSchema.allOf(
          JsonSchema.string.withMinLength(2),
          JsonSchema.string.withMaxLength(10)
        )
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.str("hello")).isRight),
          parsed.exists(_.validate(Json.str("a")).isLeft)
        )
      },
      test("anyOf round-trips") {
        val schema = JsonSchema.anyOf(JsonSchema.string, JsonSchema.number)
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.str("test")).isRight),
          parsed.exists(_.validate(Json.number(42)).isRight),
          parsed.exists(_.validate(Json.True).isLeft)
        )
      },
      test("oneOf round-trips") {
        val schema = JsonSchema.oneOf(
          JsonSchema.string.withMinLength(5),
          JsonSchema.string.withMaxLength(3)
        )
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(parsed.isRight)
      },
      test("not round-trips") {
        val schema = JsonSchema.not(JsonSchema.string)
        val parsed = JsonSchema.fromJson(schema.toJson)
        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.number(42)).isRight),
          parsed.exists(_.validate(Json.str("test")).isLeft)
        )
      }
    ),
    suite("Metadata round-trip")(
      test("title and description round-trip") {
        val schema = JsonSchema.string
          .withTitle("Name")
          .withDescription("The person's name")
        val json = schema.toJson
        assertTrue(
          json.get("title").string == Right("Name"),
          json.get("description").string == Right("The person's name")
        )
      },
      test("deprecated round-trips") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(JsonType.String),
          deprecated = Some(true)
        )
        val json = schema.toJson
        assertTrue(json.get("deprecated").boolean == Right(true))
      },
      test("examples round-trip") {
        val schema   = JsonSchema.string.withExamples(Json.str("Alice"), Json.str("Bob"))
        val json     = schema.toJson
        val examples = json.get("examples").values
        assertTrue(examples.exists(_.headOption.exists(_.elements.length == 2)))
      }
    ),
    suite("All keyword serialization")(
      test("$id serializes correctly") {
        val schema = JsonSchema.SchemaObject(`$id` = Some("https://example.com/schema"))
        val json   = schema.toJson
        assertTrue(json.get("$id").string == Right("https://example.com/schema"))
      },
      test("$ref serializes correctly") {
        val schema = JsonSchema.SchemaObject(`$ref` = Some("#/$defs/Person"))
        val json   = schema.toJson
        assertTrue(json.get("$ref").string == Right("#/$defs/Person"))
      },
      test("$defs and $ref work together") {
        val schema = JsonSchema.SchemaObject(
          `$defs` = Some(Map("PositiveInt" -> JsonSchema.integer.withMinimum(1))),
          `$ref` = Some("#/$defs/PositiveInt")
        )
        val json = schema.toJson
        assertTrue(
          json.get("$defs").values.exists(_.headOption.exists(_.isObject)),
          json.get("$ref").string == Right("#/$defs/PositiveInt")
        )
      }
    )
  )
}
