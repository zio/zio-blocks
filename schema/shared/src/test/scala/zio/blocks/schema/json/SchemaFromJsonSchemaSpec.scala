package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Schema.fromJsonSchema - constructing Schema[Json] from JsonSchema.
 *
 * Verifies that the resulting Schema validates JSON values against the schema.
 */
object SchemaFromJsonSchemaSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("SchemaFromJsonSchemaSpec")(
    suite("Valid JSON passes")(
      test("string schema accepts valid strings") {
        val schema = Schema.fromJsonSchema(JsonSchema.string)
        val dv     = Json.str("hello").toDynamicValue
        val result = schema.fromDynamicValue(dv)
        assertTrue(result.isRight)
      },
      test("number schema accepts valid numbers") {
        val schema = Schema.fromJsonSchema(JsonSchema.number)
        val dv     = Json.number(42).toDynamicValue
        val result = schema.fromDynamicValue(dv)
        assertTrue(result.isRight)
      },
      test("object schema accepts valid objects") {
        val jsonSchema = JsonSchema
          .`object`(
            "name" -> JsonSchema.string,
            "age"  -> JsonSchema.integer
          )
          .withRequired("name", "age")
        val schema = Schema.fromJsonSchema(jsonSchema)
        val dv     = Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30)).toDynamicValue
        val result = schema.fromDynamicValue(dv)
        assertTrue(result.isRight)
      },
      test("array schema accepts valid arrays") {
        val jsonSchema = JsonSchema.array(JsonSchema.string)
        val schema     = Schema.fromJsonSchema(jsonSchema)
        val dv         = Json.arr(Json.str("a"), Json.str("b")).toDynamicValue
        val result     = schema.fromDynamicValue(dv)
        assertTrue(result.isRight)
      }
    ),
    suite("Invalid JSON fails")(
      test("string schema rejects numbers") {
        val schema = Schema.fromJsonSchema(JsonSchema.string)
        val dv     = Json.number(42).toDynamicValue
        val result = schema.fromDynamicValue(dv)
        assertTrue(result.isLeft)
      },
      test("number schema rejects strings") {
        val schema = Schema.fromJsonSchema(JsonSchema.number)
        val dv     = Json.str("not a number").toDynamicValue
        val result = schema.fromDynamicValue(dv)
        assertTrue(result.isLeft)
      },
      test("object with required fields rejects missing fields") {
        val jsonSchema = JsonSchema.`object`("name" -> JsonSchema.string).withRequired("name")
        val schema     = Schema.fromJsonSchema(jsonSchema)
        val dv         = Json.obj().toDynamicValue
        val result     = schema.fromDynamicValue(dv)
        assertTrue(result.isLeft)
      },
      test("constrained string rejects invalid values") {
        val jsonSchema = JsonSchema.string.withMinLength(5)
        val schema     = Schema.fromJsonSchema(jsonSchema)
        val dv         = Json.str("hi").toDynamicValue
        val result     = schema.fromDynamicValue(dv)
        assertTrue(result.isLeft)
      }
    ),
    suite("Error messages")(
      test("error includes descriptive message") {
        val jsonSchema = JsonSchema.string.withMinLength(10)
        val schema     = Schema.fromJsonSchema(jsonSchema)
        val dv         = Json.str("short").toDynamicValue
        val result     = schema.fromDynamicValue(dv)
        assertTrue(
          result.isLeft,
          result.left.exists(_.message.nonEmpty)
        )
      }
    ),
    suite("Round-trip through DynamicValue")(
      test("JSON → DynamicValue → JSON preserves structure") {
        val original = Json.obj(
          "name"   -> Json.str("Test"),
          "values" -> Json.arr(Json.number(1), Json.number(2), Json.number(3))
        )
        val schema = Schema.fromJsonSchema(JsonSchema.True)
        val dv     = original.toDynamicValue
        val result = schema.fromDynamicValue(dv)
        assertTrue(
          result.isRight,
          result.exists(_ == original)
        )
      },
      test("nested objects preserve structure") {
        val original = Json.obj(
          "outer" -> Json.obj(
            "inner" -> Json.str("value")
          )
        )
        val schema = Schema.fromJsonSchema(JsonSchema.True)
        val dv     = original.toDynamicValue
        val result = schema.fromDynamicValue(dv)
        assertTrue(result.isRight)
      }
    ),
    suite("Constraint validation")(
      test("minLength is enforced") {
        val schema = Schema.fromJsonSchema(JsonSchema.string.withMinLength(3))
        assertTrue(
          schema.fromDynamicValue(Json.str("abc").toDynamicValue).isRight,
          schema.fromDynamicValue(Json.str("ab").toDynamicValue).isLeft
        )
      },
      test("maxLength is enforced") {
        val schema = Schema.fromJsonSchema(JsonSchema.string.withMaxLength(5))
        assertTrue(
          schema.fromDynamicValue(Json.str("hello").toDynamicValue).isRight,
          schema.fromDynamicValue(Json.str("toolong").toDynamicValue).isLeft
        )
      },
      test("minimum is enforced") {
        val schema = Schema.fromJsonSchema(JsonSchema.number.withMinimum(0))
        assertTrue(
          schema.fromDynamicValue(Json.number(10).toDynamicValue).isRight,
          schema.fromDynamicValue(Json.number(-1).toDynamicValue).isLeft
        )
      },
      test("maximum is enforced") {
        val schema = Schema.fromJsonSchema(JsonSchema.number.withMaximum(100))
        assertTrue(
          schema.fromDynamicValue(Json.number(50).toDynamicValue).isRight,
          schema.fromDynamicValue(Json.number(150).toDynamicValue).isLeft
        )
      },
      test("pattern is enforced") {
        val schema = Schema.fromJsonSchema(JsonSchema.string.withPattern("^[a-z]+$"))
        assertTrue(
          schema.fromDynamicValue(Json.str("abc").toDynamicValue).isRight,
          schema.fromDynamicValue(Json.str("ABC").toDynamicValue).isLeft
        )
      }
    ),
    suite("Composition validation")(
      test("allOf is enforced") {
        val jsonSchema = JsonSchema.allOf(
          JsonSchema.string.withMinLength(2),
          JsonSchema.string.withMaxLength(10)
        )
        val schema = Schema.fromJsonSchema(jsonSchema)
        assertTrue(
          schema.fromDynamicValue(Json.str("hello").toDynamicValue).isRight,
          schema.fromDynamicValue(Json.str("a").toDynamicValue).isLeft
        )
      },
      test("anyOf is enforced") {
        val jsonSchema = JsonSchema.anyOf(JsonSchema.string, JsonSchema.number)
        val schema     = Schema.fromJsonSchema(jsonSchema)
        assertTrue(
          schema.fromDynamicValue(Json.str("test").toDynamicValue).isRight,
          schema.fromDynamicValue(Json.number(42).toDynamicValue).isRight,
          schema.fromDynamicValue(Json.True.toDynamicValue).isLeft
        )
      }
    )
  )
}
