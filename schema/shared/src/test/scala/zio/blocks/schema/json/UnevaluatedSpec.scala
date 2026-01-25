package zio.blocks.schema.json

import zio.test._

/**
 * Tests for unevaluatedProperties and unevaluatedItems keywords.
 */
object UnevaluatedSpec extends ZIOSpecDefault {
  def spec = suite("UnevaluatedSpec")(
    suite("unevaluatedProperties")(
      test("unevaluatedProperties: false rejects extra properties") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(JsonType.Object),
          properties = Some(Map("name" -> JsonSchema.string)),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        val valid   = Json.obj("name" -> Json.str("John"))
        val invalid = Json.obj("name" -> Json.str("John"), "extra" -> Json.number(1))

        assertTrue(schema.validate(valid).isRight) &&
        assertTrue(schema.validate(invalid).isLeft)
      },
      test("unevaluatedProperties allows properties evaluated by properties keyword") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(JsonType.Object),
          properties = Some(
            Map(
              "name" -> JsonSchema.string,
              "age"  -> JsonSchema.integer
            )
          ),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        val json = Json.obj("name" -> Json.str("John"), "age" -> Json.number(30))
        assertTrue(schema.validate(json).isRight)
      },
      test("unevaluatedProperties with allOf merges evaluated properties") {
        val schema = JsonSchema.SchemaObject(
          allOf = Some(
            Vector(
              JsonSchema.SchemaObject(
                properties = Some(Map("name" -> JsonSchema.string))
              ),
              JsonSchema.SchemaObject(
                properties = Some(Map("age" -> JsonSchema.integer))
              )
            )
          ),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        val valid   = Json.obj("name" -> Json.str("John"), "age" -> Json.number(30))
        val invalid = Json.obj("name" -> Json.str("John"), "age" -> Json.number(30), "extra" -> Json.True)

        assertTrue(schema.validate(valid).isRight) &&
        assertTrue(schema.validate(invalid).isLeft)
      },
      test("unevaluatedProperties with schema validates remaining props") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(JsonType.Object),
          properties = Some(Map("name" -> JsonSchema.string)),
          unevaluatedProperties = Some(JsonSchema.integer)
        )
        val valid   = Json.obj("name" -> Json.str("John"), "extra" -> Json.number(42))
        val invalid = Json.obj("name" -> Json.str("John"), "extra" -> Json.str("not a number"))

        assertTrue(schema.validate(valid).isRight) &&
        assertTrue(schema.validate(invalid).isLeft)
      }
    ),
    suite("unevaluatedItems")(
      test("unevaluatedItems: false rejects extra items") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(JsonType.Array),
          prefixItems = Some(Vector(JsonSchema.string, JsonSchema.integer)),
          unevaluatedItems = Some(JsonSchema.False)
        )
        val valid   = Json.arr(Json.str("hello"), Json.number(42))
        val invalid = Json.arr(Json.str("hello"), Json.number(42), Json.True)

        assertTrue(schema.validate(valid).isRight) &&
        assertTrue(schema.validate(invalid).isLeft)
      },
      test("unevaluatedItems allows items validated by items keyword") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(JsonType.Array),
          prefixItems = Some(Vector(JsonSchema.string)),
          items = Some(JsonSchema.integer),
          unevaluatedItems = Some(JsonSchema.False)
        )
        val json = Json.arr(Json.str("hello"), Json.number(1), Json.number(2), Json.number(3))
        assertTrue(schema.validate(json).isRight)
      },
      test("unevaluatedItems with schema validates remaining items") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(JsonType.Array),
          prefixItems = Some(Vector(JsonSchema.string)),
          unevaluatedItems = Some(JsonSchema.boolean)
        )
        val valid   = Json.arr(Json.str("hello"), Json.True, Json.False)
        val invalid = Json.arr(Json.str("hello"), Json.number(42))

        assertTrue(schema.validate(valid).isRight) &&
        assertTrue(schema.validate(invalid).isLeft)
      },
      test("unevaluatedItems respects contains evaluation") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(JsonType.Array),
          contains = Some(JsonSchema.string),
          unevaluatedItems = Some(JsonSchema.False)
        )
        // String items should be evaluated by contains, so only non-string items should be checked
        val validOnlyStrings = Json.arr(Json.str("a"), Json.str("b"))
        assertTrue(schema.validate(validOnlyStrings).isRight)
      }
    )
  )
}
