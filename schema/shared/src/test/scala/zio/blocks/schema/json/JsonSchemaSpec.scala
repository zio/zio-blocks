package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

object JsonSchemaSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaSpec")(
    suite("JsonSchema ADT")(
      test("True accepts all JSON values") {
        assertTrue(
          JsonSchema.True.conforms(Json.Null),
          JsonSchema.True.conforms(Json.bool(true)),
          JsonSchema.True.conforms(Json.str("hello")),
          JsonSchema.True.conforms(Json.number(42)),
          JsonSchema.True.conforms(Json.arr()),
          JsonSchema.True.conforms(Json.obj())
        )
      },
      test("False rejects all JSON values") {
        assertTrue(
          !JsonSchema.False.conforms(Json.Null),
          !JsonSchema.False.conforms(Json.bool(true)),
          !JsonSchema.False.conforms(Json.str("hello"))
        )
      }
    ),
    suite("Type validation")(
      test("string type accepts strings only") {
        val schema = JsonSchema.string()
        assertTrue(
          schema.conforms(Json.str("hello")),
          !schema.conforms(Json.number(42)),
          !schema.conforms(Json.bool(true)),
          !schema.conforms(Json.Null)
        )
      },
      test("integer type accepts integers only") {
        val schema = JsonSchema.integer()
        assertTrue(
          schema.conforms(Json.number(42)),
          schema.conforms(Json.number(-100)),
          !schema.conforms(Json.number(3.14)),
          !schema.conforms(Json.str("42"))
        )
      },
      test("number type accepts all numbers") {
        val schema = JsonSchema.number()
        assertTrue(
          schema.conforms(Json.number(42)),
          schema.conforms(Json.number(3.14)),
          !schema.conforms(Json.str("42"))
        )
      },
      test("boolean type accepts booleans only") {
        assertTrue(
          JsonSchema.boolean.conforms(Json.bool(true)),
          JsonSchema.boolean.conforms(Json.bool(false)),
          !JsonSchema.boolean.conforms(Json.str("true"))
        )
      }
    ),
    suite("Numeric constraints")(
      test("minimum constraint") {
        val schema = JsonSchema.number(minimum = Some(BigDecimal(0)))
        assertTrue(
          schema.conforms(Json.number(0)),
          schema.conforms(Json.number(100)),
          !schema.conforms(Json.number(-1))
        )
      },
      test("maximum constraint") {
        val schema = JsonSchema.number(maximum = Some(BigDecimal(100)))
        assertTrue(
          schema.conforms(Json.number(100)),
          schema.conforms(Json.number(0)),
          !schema.conforms(Json.number(101))
        )
      }
    ),
    suite("String constraints")(
      test("minLength constraint") {
        val schema = JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(3)))
        assertTrue(
          schema.conforms(Json.str("abc")),
          schema.conforms(Json.str("abcd")),
          !schema.conforms(Json.str("ab"))
        )
      },
      test("maxLength constraint") {
        val schema = JsonSchema.string(maxLength = Some(NonNegativeInt.unsafe(5)))
        assertTrue(
          schema.conforms(Json.str("12345")),
          schema.conforms(Json.str("123")),
          !schema.conforms(Json.str("123456"))
        )
      },
      test("pattern constraint") {
        val schema = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$")))
        assertTrue(
          schema.conforms(Json.str("hello")),
          !schema.conforms(Json.str("Hello")),
          !schema.conforms(Json.str("hello123"))
        )
      }
    ),
    suite("Array constraints")(
      test("items constraint") {
        val schema = JsonSchema.array(items = Some(JsonSchema.integer()))
        assertTrue(
          schema.conforms(Json.arr(Json.number(1), Json.number(2))),
          schema.conforms(Json.arr()),
          !schema.conforms(Json.arr(Json.str("a")))
        )
      },
      test("minItems constraint") {
        val schema = JsonSchema.array(minItems = Some(NonNegativeInt.unsafe(2)))
        assertTrue(
          schema.conforms(Json.arr(Json.number(1), Json.number(2))),
          !schema.conforms(Json.arr(Json.number(1))),
          !schema.conforms(Json.arr())
        )
      }
    ),
    suite("Object constraints")(
      test("properties constraint") {
        val schema = JsonSchema.`object`(
          properties = Some(Map("name" -> JsonSchema.string(), "age" -> JsonSchema.integer()))
        )
        assertTrue(
          schema.conforms(Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))),
          schema.conforms(Json.obj("name" -> Json.str("Bob"))),
          !schema.conforms(Json.obj("name" -> Json.number(42)))
        )
      },
      test("required constraint") {
        val schema = JsonSchema.`object`(
          properties = Some(Map("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        assertTrue(
          schema.conforms(Json.obj("name" -> Json.str("Alice"))),
          !schema.conforms(Json.obj())
        )
      },
      test("additionalProperties false") {
        val schema = JsonSchema.`object`(
          properties = Some(Map("name" -> JsonSchema.string())),
          additionalProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.obj("name" -> Json.str("Alice"))),
          !schema.conforms(Json.obj("name" -> Json.str("Alice"), "extra" -> Json.number(1)))
        )
      }
    ),
    suite("Composition")(
      test("allOf combinator (&&)") {
        val schema = JsonSchema.integer() && JsonSchema.number(minimum = Some(BigDecimal(0)))
        assertTrue(
          schema.conforms(Json.number(42)),
          !schema.conforms(Json.number(-1)),
          !schema.conforms(Json.number(3.14))
        )
      },
      test("anyOf combinator (||)") {
        val schema = JsonSchema.string() || JsonSchema.integer()
        assertTrue(
          schema.conforms(Json.str("hello")),
          schema.conforms(Json.number(42)),
          !schema.conforms(Json.bool(true))
        )
      },
      test("not combinator (!)") {
        val schema = !JsonSchema.string()
        assertTrue(
          !schema.conforms(Json.str("hello")),
          schema.conforms(Json.number(42)),
          schema.conforms(Json.bool(true))
        )
      }
    ),
    suite("enum and const")(
      test("enum constraint") {
        val schema = JsonSchema.enumOf(::(Json.str("red"), List(Json.str("green"), Json.str("blue"))))
        assertTrue(
          schema.conforms(Json.str("red")),
          schema.conforms(Json.str("green")),
          !schema.conforms(Json.str("yellow"))
        )
      },
      test("const constraint") {
        val schema = JsonSchema.constOf(Json.str("fixed"))
        assertTrue(
          schema.conforms(Json.str("fixed")),
          !schema.conforms(Json.str("other"))
        )
      }
    ),
    suite("Roundtrip")(
      test("toJson and fromJson roundtrip for simple schema") {
        val schema = JsonSchema.`object`(
          properties = Some(Map("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name"))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip.isRight)
      },
      test("True serializes to true") {
        assertTrue(JsonSchema.True.toJson == Json.Boolean(true))
      },
      test("False serializes to false") {
        assertTrue(JsonSchema.False.toJson == Json.Boolean(false))
      }
    ),
    suite("withNullable")(
      test("adds null to single type") {
        val schema   = JsonSchema.string()
        val nullable = schema.withNullable
        assertTrue(
          nullable.conforms(Json.str("hello")),
          nullable.conforms(Json.Null),
          !nullable.conforms(Json.number(42))
        )
      }
    )
  )
}
