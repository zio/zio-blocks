package zio.blocks.schema.json

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema._
import zio.test._

object SchemaFromJsonSchemaSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("SchemaFromJsonSchemaSpec")(
    suite("Valid JSON passes validation")(
      test("string schema accepts string values") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode(""""hello world"""")
        assertTrue(result.isRight)
      },
      test("integer schema accepts integer values") {
        val jsonSchema  = JsonSchema.integer()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("42")
        assertTrue(result.isRight)
      },
      test("number schema accepts number values") {
        val jsonSchema  = JsonSchema.number()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("3.14159")
        assertTrue(result.isRight)
      },
      test("boolean schema accepts boolean values") {
        val jsonSchema  = JsonSchema.boolean
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val resultTrue  = codec.decode("true")
        val resultFalse = codec.decode("false")
        assertTrue(resultTrue.isRight, resultFalse.isRight)
      },
      test("null schema accepts null values") {
        val jsonSchema  = JsonSchema.nullSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("null")
        assertTrue(result.isRight)
      },
      test("array schema accepts array values") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("[1, 2, 3]")
        assertTrue(result.isRight)
      },
      test("object schema accepts object values with required properties") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Alice", "age": 30}""")
        assertTrue(result.isRight)
      },
      test("nested object schema validates correctly") {
        val addressSchema = JsonSchema.obj(
          properties = Some(ChunkMap("city" -> JsonSchema.string(), "zip" -> JsonSchema.string())),
          required = Some(Set("city", "zip"))
        )
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "address" -> addressSchema)),
          required = Some(Set("name", "address"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Bob", "address": {"city": "NYC", "zip": "10001"}}""")
        assertTrue(result.isRight)
      }
    ),
    suite("Invalid JSON fails validation")(
      test("string schema rejects non-string values") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("42")
        assertTrue(result.isLeft)
      },
      test("integer schema rejects string values") {
        val jsonSchema  = JsonSchema.integer()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode(""""not a number"""")
        assertTrue(result.isLeft)
      },
      test("boolean schema rejects non-boolean values") {
        val jsonSchema  = JsonSchema.boolean
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("42")
        assertTrue(result.isLeft)
      },
      test("array schema rejects non-array values") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode(""""not an array"""")
        assertTrue(result.isLeft)
      },
      test("object schema rejects non-object values") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string()))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("[1, 2, 3]")
        assertTrue(result.isLeft)
      },
      test("object schema rejects missing required properties") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Alice"}""")
        assertTrue(result.isLeft)
      },
      test("array items schema validation fails for wrong item type") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""[1, "two", 3]""")
        assertTrue(result.isLeft)
      },
      test("nested object validation fails for invalid nested property") {
        val addressSchema = JsonSchema.obj(
          properties = Some(ChunkMap("city" -> JsonSchema.string())),
          required = Some(Set("city"))
        )
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "address" -> addressSchema)),
          required = Some(Set("name", "address"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Bob", "address": {"city": 12345}}""")
        assertTrue(result.isLeft)
      }
    ),
    suite("Error messages include path information")(
      test("error for wrong type at root is descriptive") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("42")
        assertTrue(result.isLeft)
      },
      test("error for nested property is descriptive") {
        val jsonSchema = JsonSchema.obj(
          properties =
            Some(ChunkMap("user" -> JsonSchema.obj(properties = Some(ChunkMap("age" -> JsonSchema.integer())))))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"user": {"age": "not-a-number"}}""")
        assertTrue(result.isLeft)
      },
      test("error for array item is descriptive") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""[1, 2, "three", 4]""")
        assertTrue(result.isLeft)
      },
      test("missing required field error is descriptive") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{}""")
        assertTrue(result.isLeft)
      }
    ),
    suite("Round-trip through DynamicValue")(
      test("JSON object survives round-trip through DynamicValue") {
        val original = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
        val dv       = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assertTrue(original == restored)
      },
      test("JSON array survives round-trip through DynamicValue") {
        val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
        val dv       = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assertTrue(original == restored)
      },
      test("nested JSON structure survives round-trip") {
        val original = Json.Object(
          "users" -> Json.Array(
            Json.Object("name" -> Json.String("Alice"), "active" -> Json.True),
            Json.Object("name" -> Json.String("Bob"), "active"   -> Json.False)
          ),
          "count" -> Json.Number(2)
        )
        val dv       = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assertTrue(original == restored)
      },
      test("primitive JSON values survive round-trip") {
        val stringVal = Json.String("hello")
        val numberVal = Json.Number(42)
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
        val original = Json.Object(
          "config" -> Json.Object(
            "settings" -> Json.Object(
              "enabled" -> Json.True,
              "values"  -> Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
            ),
            "name" -> Json.String("test")
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
        val codec       = schemaForJs.derive(JsonFormat)

        val original = Json.Object("key" -> Json.String("value"))
        val encoded  = codec.encodeToString(original)
        val decoded  = codec.decode(encoded)

        assertTrue(decoded == Right(original))
      },
      test("validated Schema[Json] accepts valid JSON") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("status" -> JsonSchema.string())),
          required = Some(Set("status"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val result = codec.decode("""{"status": "ok"}""")
        assertTrue(result.isRight)
      },
      test("validated Schema[Json] rejects invalid JSON") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("status" -> JsonSchema.string())),
          required = Some(Set("status"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val result = codec.decode("""{"status": 123}""")
        assertTrue(result.isLeft)
      },
      test("encode then decode preserves JSON structure") {
        val jsonSchema  = JsonSchema.True
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val original = Json.Array(
          Json.Object("id" -> Json.Number(1), "name" -> Json.String("first")),
          Json.Object("id" -> Json.Number(2), "name" -> Json.String("second"))
        )
        val encoded = codec.encodeToString(original)
        val decoded = codec.decode(encoded)

        assertTrue(decoded == Right(original))
      },
      test("complex schema with constraints validates correctly") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(
            ChunkMap(
              "name" -> JsonSchema.string(minLength = Some(NonNegativeInt.one)),
              "age"  -> JsonSchema.integer(minimum = Some(BigDecimal(0)), maximum = Some(BigDecimal(150))),
              "tags" -> JsonSchema.array(items = Some(JsonSchema.string()))
            )
          ),
          required = Some(Set("name"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val valid   = codec.decode("""{"name": "Alice", "age": 30, "tags": ["developer"]}""")
        val invalid = codec.decode("""{"name": "", "age": 30}""")

        assertTrue(valid.isRight, invalid.isLeft)
      }
    ),
    suite("Schema[Json] implicit instance")(
      test("implicit Schema[Json] round-trips all JSON types") {
        val codec = Schema[Json].derive(JsonFormat)

        val values = List(
          Json.String("string"),
          Json.Number(42),
          Json.Boolean(true),
          Json.Null,
          Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)),
          Json.Object("key" -> Json.String("value"))
        )

        val results = values.map(v => codec.decode(codec.encodeToString(v)))

        assertTrue(results.zip(values).forall { case (result, original) => result == Right(original) })
      },
      test("implicit Schema[Json] round-trips correctly") {
        val codec    = Schema[Json].derive(JsonFormat)
        val original = Json.Object("nested" -> Json.Object("array" -> Json.Array(Json.Number(1), Json.String("two"))))
        val encoded  = codec.encodeToString(original)
        val decoded  = codec.decode(encoded)

        assertTrue(decoded == Right(original))
      }
    )
  )
}
