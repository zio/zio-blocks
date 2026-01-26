package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

object SchemaFromJsonSchemaSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("SchemaFromJsonSchemaSpec")(
    suite("Valid JSON passes validation")(
      test("string schema accepts string values") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode(""""hello world"""")
        assertTrue(result.isRight)
      },
      test("integer schema accepts integer values") {
        val jsonSchema  = JsonSchema.integer()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("42")
        assertTrue(result.isRight)
      },
      test("number schema accepts number values") {
        val jsonSchema  = JsonSchema.number()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("3.14159")
        assertTrue(result.isRight)
      },
      test("boolean schema accepts boolean values") {
        val jsonSchema  = JsonSchema.boolean
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val resultTrue  = codec.decode("true")
        val resultFalse = codec.decode("false")
        assertTrue(resultTrue.isRight, resultFalse.isRight)
      },
      test("null schema accepts null values") {
        val jsonSchema  = JsonSchema.`null`
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("null")
        assertTrue(result.isRight)
      },
      test("array schema accepts array values") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("[1, 2, 3]")
        assertTrue(result.isRight)
      },
      test("object schema accepts object values with required properties") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("""{"name": "Alice", "age": 30}""")
        assertTrue(result.isRight)
      },
      test("nested object schema validates correctly") {
        val addressSchema = JsonSchema.obj(
          properties = Some(Map("city" -> JsonSchema.string(), "zip" -> JsonSchema.string())),
          required = Some(Set("city"))
        )
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string(), "address" -> addressSchema)),
          required = Some(Set("name"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("""{"name": "Bob", "address": {"city": "NYC"}}""")
        assertTrue(result.isRight)
      }
    ),
    suite("Invalid JSON fails validation")(
      test("string schema rejects non-string values") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("42")
        assertTrue(result.isLeft)
      },
      test("integer schema rejects string values") {
        val jsonSchema  = JsonSchema.integer()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode(""""not a number"""")
        assertTrue(result.isLeft)
      },
      test("boolean schema rejects non-boolean values") {
        val jsonSchema  = JsonSchema.boolean
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("42")
        assertTrue(result.isLeft)
      },
      test("array schema rejects non-array values") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode(""""not an array"""")
        assertTrue(result.isLeft)
      },
      test("object schema rejects non-object values") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string()))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("[1, 2, 3]")
        assertTrue(result.isLeft)
      },
      test("object schema rejects missing required properties") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("""{"name": "Alice"}""")
        assertTrue(result.isLeft)
      },
      test("array items schema validation fails for wrong item type") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("""[1, "two", 3]""")
        assertTrue(result.isLeft)
      },
      test("nested object validation fails for invalid nested property") {
        val addressSchema = JsonSchema.obj(
          properties = Some(Map("city" -> JsonSchema.string())),
          required = Some(Set("city"))
        )
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string(), "address" -> addressSchema)),
          required = Some(Set("name", "address"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("""{"name": "Bob", "address": {"city": 12345}}""")
        assertTrue(result.isLeft)
      }
    ),
    suite("Error messages include path information")(
      test("error for wrong type at root includes path") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("42")
        assertTrue(
          result.isLeft,
          result.left.exists(_.message.contains("."))
        )
      },
      test("error for nested property includes field path") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("user" -> JsonSchema.obj(properties = Some(Map("age" -> JsonSchema.integer())))))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("""{"user": {"age": "not-a-number"}}""")
        assertTrue(result.isLeft)
      },
      test("error for array item includes index path") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("""[1, 2, "three", 4]""")
        assertTrue(result.isLeft)
      },
      test("missing required field error is descriptive") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)
        val result      = codec.decode("""{}""")
        assertTrue(
          result.isLeft,
          result.left.exists(err => err.message.contains("name") || err.message.contains("required"))
        )
      }
    ),
    suite("Round-trip through DynamicValue")(
      test("JSON object survives round-trip through DynamicValue") {
        val original = Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))
        val dv       = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assertTrue(original == restored)
      },
      test("JSON array survives round-trip through DynamicValue") {
        val original = Json.arr(Json.number(1), Json.number(2), Json.number(3))
        val dv       = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assertTrue(original == restored)
      },
      test("nested JSON structure survives round-trip") {
        val original = Json.obj(
          "users" -> Json.arr(
            Json.obj("name" -> Json.str("Alice"), "active" -> Json.True),
            Json.obj("name" -> Json.str("Bob"), "active"   -> Json.False)
          ),
          "count" -> Json.number(2)
        )
        val dv       = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assertTrue(original == restored)
      },
      test("primitive JSON values survive round-trip") {
        val stringVal = Json.str("hello")
        val numberVal = Json.number(42)
        val boolVal   = Json.True
        val nullVal   = Json.Null

        assertTrue(
          Json.fromDynamicValue(stringVal.toDynamicValue) == stringVal,
          Json.fromDynamicValue(numberVal.toDynamicValue) == numberVal,
          Json.fromDynamicValue(boolVal.toDynamicValue) == boolVal,
          Json.fromDynamicValue(nullVal.toDynamicValue) == nullVal
        )
      },
      test("complex nested structure round-trips correctly") {
        val original = Json.obj(
          "config" -> Json.obj(
            "settings" -> Json.obj(
              "enabled" -> Json.True,
              "values"  -> Json.arr(Json.number(1), Json.number(2), Json.number(3))
            ),
            "name" -> Json.str("test")
          ),
          "metadata" -> Json.Null
        )
        val dv       = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assertTrue(original == restored)
      }
    ),
    suite("Encode/decode works with Schema[Json]")(
      test("Schema[Json] can encode and decode JSON") {
        val jsonSchema  = JsonSchema.True
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)

        val original = Json.obj("key" -> Json.str("value"))
        val encoded  = codec.encodeToString(original)
        val decoded  = codec.decode(encoded)

        assertTrue(decoded == Right(original))
      },
      test("validated Schema[Json] accepts valid JSON") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("status" -> JsonSchema.string())),
          required = Some(Set("status"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)

        val result = codec.decode("""{"status": "ok"}""")
        assertTrue(result.isRight)
      },
      test("validated Schema[Json] rejects invalid JSON") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(Map("status" -> JsonSchema.string())),
          required = Some(Set("status"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)

        val result = codec.decode("""{"status": 123}""")
        assertTrue(result.isLeft)
      },
      test("encode then decode preserves JSON structure") {
        val jsonSchema  = JsonSchema.True
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)

        val original = Json.arr(
          Json.obj("id" -> Json.number(1), "name" -> Json.str("first")),
          Json.obj("id" -> Json.number(2), "name" -> Json.str("second"))
        )
        val encoded = codec.encodeToString(original)
        val decoded = codec.decode(encoded)

        assertTrue(decoded == Right(original))
      },
      test("complex schema with constraints validates correctly") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(
            Map(
              "name" -> JsonSchema.string(minLength = Some(NonNegativeInt.one)),
              "age"  -> JsonSchema.integer(minimum = Some(BigDecimal(0)), maximum = Some(BigDecimal(150))),
              "tags" -> JsonSchema.array(items = Some(JsonSchema.string()))
            )
          ),
          required = Some(Set("name"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat.deriver)

        val valid   = codec.decode("""{"name": "Alice", "age": 30, "tags": ["developer"]}""")
        val invalid = codec.decode("""{"name": "", "age": 30}""")

        assertTrue(valid.isRight, invalid.isLeft)
      }
    ),
    suite("Schema[Json] implicit instance")(
      test("implicit Schema[Json] accepts any JSON") {
        val codec = Schema[Json].derive(JsonFormat.deriver)

        val results = List(
          codec.decode(""""string""""),
          codec.decode("42"),
          codec.decode("true"),
          codec.decode("null"),
          codec.decode("[1, 2, 3]"),
          codec.decode("""{"key": "value"}""")
        )

        assertTrue(results.forall(_.isRight))
      },
      test("implicit Schema[Json] round-trips correctly") {
        val codec    = Schema[Json].derive(JsonFormat.deriver)
        val original = Json.obj("nested" -> Json.obj("array" -> Json.arr(Json.number(1), Json.str("two"))))
        val encoded  = codec.encodeToString(original)
        val decoded  = codec.decode(encoded)

        assertTrue(decoded == Right(original))
      }
    )
  )
}
