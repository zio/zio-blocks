package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

object JsonSchemaSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaSpec")(
    suite("Boolean schemas")(
      test("true schema accepts any value") {
        assertTrue(
          JsonSchema.True.validate(Json.str("hello")).isRight,
          JsonSchema.True.validate(Json.number(42)).isRight,
          JsonSchema.True.validate(Json.Null).isRight,
          JsonSchema.True.validate(Json.arr()).isRight,
          JsonSchema.True.validate(Json.obj()).isRight
        )
      },
      test("false schema rejects all values") {
        assertTrue(
          JsonSchema.False.validate(Json.str("hello")).isLeft,
          JsonSchema.False.validate(Json.number(42)).isLeft,
          JsonSchema.False.validate(Json.Null).isLeft
        )
      }
    ),
    suite("Type validation")(
      test("string type validates strings") {
        val schema = JsonSchema.string
        assertTrue(
          schema.validate(Json.str("hello")).isRight,
          schema.validate(Json.number(42)).isLeft,
          schema.validate(Json.Null).isLeft
        )
      },
      test("number type validates numbers") {
        val schema = JsonSchema.number
        assertTrue(
          schema.validate(Json.number(42)).isRight,
          schema.validate(Json.number(3.14)).isRight,
          schema.validate(Json.str("42")).isLeft
        )
      },
      test("integer type validates integers") {
        val schema = JsonSchema.integer
        assertTrue(
          schema.validate(Json.number(42)).isRight,
          schema.validate(Json.number(42.0)).isRight,
          schema.validate(Json.number(3.14)).isLeft
        )
      },
      test("boolean type validates booleans") {
        val schema = JsonSchema.boolean
        assertTrue(
          schema.validate(Json.True).isRight,
          schema.validate(Json.False).isRight,
          schema.validate(Json.str("true")).isLeft
        )
      },
      test("null type validates null") {
        val schema = JsonSchema.`null`
        assertTrue(
          schema.validate(Json.Null).isRight,
          schema.validate(Json.str("null")).isLeft
        )
      },
      test("array type validates arrays") {
        val schema = JsonSchema.array
        assertTrue(
          schema.validate(Json.arr(Json.number(1), Json.number(2))).isRight,
          schema.validate(Json.obj()).isLeft
        )
      },
      test("object type validates objects") {
        val schema = JsonSchema.`object`
        assertTrue(
          schema.validate(Json.obj("a" -> Json.number(1))).isRight,
          schema.validate(Json.arr()).isLeft
        )
      }
    ),
    suite("String constraints")(
      test("minLength constraint") {
        val schema = JsonSchema.string.withMinLength(3)
        assertTrue(
          schema.validate(Json.str("abc")).isRight,
          schema.validate(Json.str("abcd")).isRight,
          schema.validate(Json.str("ab")).isLeft
        )
      },
      test("maxLength constraint") {
        val schema = JsonSchema.string.withMaxLength(5)
        assertTrue(
          schema.validate(Json.str("abc")).isRight,
          schema.validate(Json.str("abcdef")).isLeft
        )
      },
      test("pattern constraint") {
        val schema = JsonSchema.string.withPattern("^[a-z]+$")
        assertTrue(
          schema.validate(Json.str("abc")).isRight,
          schema.validate(Json.str("ABC")).isLeft,
          schema.validate(Json.str("abc123")).isLeft
        )
      }
    ),
    suite("Numeric constraints")(
      test("minimum constraint") {
        val schema = JsonSchema.number.withMinimum(10)
        assertTrue(
          schema.validate(Json.number(10)).isRight,
          schema.validate(Json.number(15)).isRight,
          schema.validate(Json.number(5)).isLeft
        )
      },
      test("maximum constraint") {
        val schema = JsonSchema.number.withMaximum(100)
        assertTrue(
          schema.validate(Json.number(100)).isRight,
          schema.validate(Json.number(50)).isRight,
          schema.validate(Json.number(150)).isLeft
        )
      },
      test("range constraint") {
        val schema = JsonSchema.number.withRange(1, 10)
        assertTrue(
          schema.validate(Json.number(5)).isRight,
          schema.validate(Json.number(1)).isRight,
          schema.validate(Json.number(10)).isRight,
          schema.validate(Json.number(0)).isLeft,
          schema.validate(Json.number(11)).isLeft
        )
      }
    ),
    suite("Array constraints")(
      test("items constraint validates all elements") {
        val schema = JsonSchema.array(JsonSchema.string)
        assertTrue(
          schema.validate(Json.arr(Json.str("a"), Json.str("b"))).isRight,
          schema.validate(Json.arr(Json.str("a"), Json.number(1))).isLeft
        )
      },
      test("minItems constraint") {
        val schema = JsonSchema.array.withMinItems(2)
        assertTrue(
          schema.validate(Json.arr(Json.number(1), Json.number(2))).isRight,
          schema.validate(Json.arr(Json.number(1))).isLeft
        )
      },
      test("maxItems constraint") {
        val schema = JsonSchema.array.withMaxItems(3)
        assertTrue(
          schema.validate(Json.arr(Json.number(1), Json.number(2))).isRight,
          schema.validate(Json.arr(Json.number(1), Json.number(2), Json.number(3), Json.number(4))).isLeft
        )
      },
      test("uniqueItems constraint") {
        val schema = JsonSchema.array.withUniqueItems
        assertTrue(
          schema.validate(Json.arr(Json.number(1), Json.number(2), Json.number(3))).isRight,
          schema.validate(Json.arr(Json.number(1), Json.number(2), Json.number(1))).isLeft
        )
      }
    ),
    suite("Object constraints")(
      test("properties constraint") {
        val schema = JsonSchema.`object`(
          "name" -> JsonSchema.string,
          "age"  -> JsonSchema.number
        )
        assertTrue(
          schema.validate(Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))).isRight,
          schema.validate(Json.obj("name" -> Json.number(123), "age" -> Json.number(30))).isLeft
        )
      },
      test("required constraint") {
        val schema = JsonSchema
          .`object`(
            "name" -> JsonSchema.string
          )
          .withRequired("name")
        assertTrue(
          schema.validate(Json.obj("name" -> Json.str("Alice"))).isRight,
          schema.validate(Json.obj()).isLeft
        )
      },
      test("additionalProperties false") {
        val schema = JsonSchema
          .`object`(
            "name" -> JsonSchema.string
          )
          .noAdditionalProperties
        assertTrue(
          schema.validate(Json.obj("name" -> Json.str("Alice"))).isRight,
          schema.validate(Json.obj("name" -> Json.str("Alice"), "extra" -> Json.number(1))).isLeft
        )
      }
    ),
    suite("Composition")(
      test("allOf requires all schemas to match") {
        val schema = JsonSchema.allOf(
          JsonSchema.string.withMinLength(2),
          JsonSchema.string.withMaxLength(5)
        )
        assertTrue(
          schema.validate(Json.str("abc")).isRight,
          schema.validate(Json.str("a")).isLeft,
          schema.validate(Json.str("abcdef")).isLeft
        )
      },
      test("anyOf requires at least one schema to match") {
        val schema = JsonSchema.anyOf(
          JsonSchema.string,
          JsonSchema.number
        )
        assertTrue(
          schema.validate(Json.str("hello")).isRight,
          schema.validate(Json.number(42)).isRight,
          schema.validate(Json.True).isLeft
        )
      },
      test("oneOf requires exactly one schema to match") {
        val schema = JsonSchema.oneOf(
          JsonSchema.string.withMinLength(5),
          JsonSchema.string.withMaxLength(3)
        )
        assertTrue(
          schema.validate(Json.str("abcdef")).isRight, // matches first only
          schema.validate(Json.str("ab")).isRight,     // matches second only
          schema.validate(Json.str("abcd")).isLeft     // matches neither
        )
      },
      test("not negates a schema") {
        val schema = JsonSchema.not(JsonSchema.string)
        assertTrue(
          schema.validate(Json.number(42)).isRight,
          schema.validate(Json.str("hello")).isLeft
        )
      }
    ),
    suite("Enum and const")(
      test("enum validates against allowed values") {
        val schema = JsonSchema.`enum`(Json.str("red"), Json.str("green"), Json.str("blue"))
        assertTrue(
          schema.validate(Json.str("red")).isRight,
          schema.validate(Json.str("yellow")).isLeft
        )
      },
      test("const validates exact match") {
        val schema = JsonSchema.const(Json.number(42))
        assertTrue(
          schema.validate(Json.number(42)).isRight,
          schema.validate(Json.number(43)).isLeft
        )
      }
    ),
    suite("Serialization")(
      test("toJson produces valid JSON schema") {
        val schema = JsonSchema.string.withMinLength(1).withMaxLength(100)
        val json   = schema.toJson
        assertTrue(
          json.isObject,
          json.get("type").string == Right("string"),
          json.get("minLength").number == Right(BigDecimal(1)),
          json.get("maxLength").number == Right(BigDecimal(100))
        )
      },
      test("fromJson parses boolean schemas") {
        assertTrue(
          JsonSchema.fromJson(Json.True) == Right(JsonSchema.True),
          JsonSchema.fromJson(Json.False) == Right(JsonSchema.False)
        )
      },
      test("roundtrip serialization") {
        val schema = JsonSchema
          .`object`(
            "name" -> JsonSchema.string.withMinLength(1),
            "age"  -> JsonSchema.integer.withMinimum(0)
          )
          .withRequired("name", "age")

        val json   = schema.toJson
        val parsed = JsonSchema.fromJson(json)

        assertTrue(
          parsed.isRight,
          parsed.exists(_.validate(Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))).isRight),
          parsed.exists(_.validate(Json.obj()).isLeft)
        )
      }
    ),
    suite("Json.check/conforms integration")(
      test("Json.check with JsonSchema object") {
        val schema  = JsonSchema.string.withMinLength(1)
        val valid   = Json.str("hello")
        val invalid = Json.str("")

        assertTrue(
          valid.check(schema).isRight,
          invalid.check(schema).isLeft,
          valid.conforms(schema),
          !invalid.conforms(schema)
        )
      },
      test("Json.check with Json schema") {
        val schemaJson = Json.obj(
          "type"      -> Json.str("string"),
          "minLength" -> Json.number(1)
        )
        val valid   = Json.str("hello")
        val invalid = Json.str("")

        assertTrue(
          valid.check(schemaJson).isRight,
          invalid.check(schemaJson).isLeft,
          valid.conforms(schemaJson),
          !invalid.conforms(schemaJson)
        )
      }
    ),
    suite("Advanced composition")(
      test("nested allOf/anyOf") {
        val schema = JsonSchema.allOf(
          JsonSchema.anyOf(JsonSchema.string, JsonSchema.number),
          JsonSchema.SchemaObject(not = Some(JsonSchema.`null`))
        )
        assertTrue(
          schema.validate(Json.str("hello")).isRight,
          schema.validate(Json.number(42)).isRight,
          schema.validate(Json.Null).isLeft
        )
      },
      test("if/then/else conditional") {
        val ifSchema = JsonSchema.SchemaObject(
          properties = Some(Map("type" -> JsonSchema.SchemaObject(const = Some(Json.str("person")))))
        )
        val thenSchema = JsonSchema.SchemaObject(
          required = Some(Set("name"))
        )
        val schema = JsonSchema.SchemaObject(
          `if` = Some(ifSchema),
          `then` = Some(thenSchema),
          `else` = Some(JsonSchema.True)
        )
        assertTrue(
          schema.validate(Json.obj("type" -> Json.str("person"), "name" -> Json.str("Alice"))).isRight,
          schema.validate(Json.obj("type" -> Json.str("other"))).isRight
        )
      }
    ),
    suite("New keywords serialization")(
      test("unevaluatedProperties serializes") {
        val schema = JsonSchema.SchemaObject(unevaluatedProperties = Some(JsonSchema.False))
        val json   = schema.toJson
        // Verify the key exists in serialization
        val jsonStr = json match {
          case obj: Json.Object => obj.value.exists(_._1 == "unevaluatedProperties")
          case _                => false
        }
        assertTrue(jsonStr)
      },
      test("unevaluatedItems serializes") {
        val schema  = JsonSchema.SchemaObject(unevaluatedItems = Some(JsonSchema.string))
        val json    = schema.toJson
        val jsonStr = json match {
          case obj: Json.Object => obj.value.exists(_._1 == "unevaluatedItems")
          case _                => false
        }
        assertTrue(jsonStr)
      },
      test("extensions are preserved") {
        val schema = JsonSchema.SchemaObject(extensions = Some(Map("x-custom" -> Json.str("value"))))
        val json   = schema.toJson
        assertTrue(json.get("x-custom").string == Right("value"))
      },
      test("contentEncoding serializes") {
        val schema = JsonSchema.SchemaObject(contentEncoding = Some("base64"))
        val json   = schema.toJson
        assertTrue(json.get("contentEncoding").string == Right("base64"))
      },
      test("contentMediaType serializes") {
        val schema = JsonSchema.SchemaObject(contentMediaType = Some("application/json"))
        val json   = schema.toJson
        assertTrue(json.get("contentMediaType").string == Right("application/json"))
      },
      test("$dynamicAnchor serializes") {
        val schema = JsonSchema.SchemaObject(`$dynamicAnchor` = Some("test"))
        val json   = schema.toJson
        assertTrue(json.get("$dynamicAnchor").string == Right("test"))
      },
      test("$dynamicRef serializes") {
        val schema = JsonSchema.SchemaObject(`$dynamicRef` = Some("#test"))
        val json   = schema.toJson
        assertTrue(json.get("$dynamicRef").string == Right("#test"))
      }
    )
  )
}
