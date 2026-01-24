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
    ),
    suite("Type unions")(
      test("union type accepts any of the types") {
        val schema = JsonSchema.SchemaObject(
          `type` = Some(SchemaType.Union(::(JsonType.String, List(JsonType.Number))))
        )
        assertTrue(
          schema.conforms(Json.str("hello")),
          schema.conforms(Json.number(42)),
          !schema.conforms(Json.bool(true)),
          !schema.conforms(Json.Null)
        )
      }
    ),
    suite("Numeric constraints extended")(
      test("exclusiveMinimum constraint") {
        val schema = JsonSchema.number(exclusiveMinimum = Some(BigDecimal(0)))
        assertTrue(
          schema.conforms(Json.number(1)),
          !schema.conforms(Json.number(0)),
          !schema.conforms(Json.number(-1))
        )
      },
      test("exclusiveMaximum constraint") {
        val schema = JsonSchema.number(exclusiveMaximum = Some(BigDecimal(100)))
        assertTrue(
          schema.conforms(Json.number(99)),
          !schema.conforms(Json.number(100)),
          !schema.conforms(Json.number(101))
        )
      },
      test("multipleOf constraint") {
        val schema = JsonSchema.number(multipleOf = Some(PositiveNumber.unsafe(BigDecimal(5))))
        assertTrue(
          schema.conforms(Json.number(0)),
          schema.conforms(Json.number(10)),
          schema.conforms(Json.number(-15)),
          !schema.conforms(Json.number(7))
        )
      }
    ),
    suite("Array constraints extended")(
      test("maxItems constraint") {
        val schema = JsonSchema.array(maxItems = Some(NonNegativeInt.unsafe(3)))
        assertTrue(
          schema.conforms(Json.arr(Json.number(1), Json.number(2))),
          schema.conforms(Json.arr(Json.number(1), Json.number(2), Json.number(3))),
          !schema.conforms(Json.arr(Json.number(1), Json.number(2), Json.number(3), Json.number(4)))
        )
      },
      test("uniqueItems constraint") {
        val schema = JsonSchema.array(uniqueItems = Some(true))
        assertTrue(
          schema.conforms(Json.arr(Json.number(1), Json.number(2), Json.number(3))),
          !schema.conforms(Json.arr(Json.number(1), Json.number(2), Json.number(1)))
        )
      },
      test("prefixItems constraint") {
        val schema = JsonSchema.array(prefixItems = Some(::(JsonSchema.string(), List(JsonSchema.integer()))))
        assertTrue(
          schema.conforms(Json.arr(Json.str("a"), Json.number(1))),
          schema.conforms(Json.arr(Json.str("a"), Json.number(1), Json.bool(true))),
          !schema.conforms(Json.arr(Json.number(1), Json.str("a")))
        )
      },
      test("contains constraint") {
        val schema = JsonSchema.array(contains = Some(JsonSchema.string()))
        assertTrue(
          schema.conforms(Json.arr(Json.number(1), Json.str("found"), Json.number(2))),
          !schema.conforms(Json.arr(Json.number(1), Json.number(2)))
        )
      }
    ),
    suite("Object constraints extended")(
      test("minProperties constraint") {
        val schema = JsonSchema.`object`(minProperties = Some(NonNegativeInt.unsafe(2)))
        assertTrue(
          schema.conforms(Json.obj("a" -> Json.number(1), "b" -> Json.number(2))),
          !schema.conforms(Json.obj("a" -> Json.number(1)))
        )
      },
      test("maxProperties constraint") {
        val schema = JsonSchema.`object`(maxProperties = Some(NonNegativeInt.unsafe(2)))
        assertTrue(
          schema.conforms(Json.obj("a" -> Json.number(1), "b" -> Json.number(2))),
          !schema.conforms(Json.obj("a" -> Json.number(1), "b" -> Json.number(2), "c" -> Json.number(3)))
        )
      },
      test("propertyNames constraint") {
        val schema = JsonSchema.`object`(
          propertyNames = Some(JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$"))))
        )
        assertTrue(
          schema.conforms(Json.obj("name" -> Json.number(1))),
          !schema.conforms(Json.obj("Name" -> Json.number(1)))
        )
      },
      test("dependentRequired constraint") {
        val schema = JsonSchema.SchemaObject(
          dependentRequired = Some(Map("credit_card" -> Set("billing_address")))
        )
        assertTrue(
          schema.conforms(Json.obj("name" -> Json.str("Alice"))),
          schema.conforms(
            Json.obj("credit_card" -> Json.str("1234"), "billing_address" -> Json.str("123 Main St"))
          ),
          !schema.conforms(Json.obj("credit_card" -> Json.str("1234")))
        )
      }
    ),
    suite("Conditional validation")(
      test("if/then/else validation") {
        val schema = JsonSchema.SchemaObject(
          `if` = Some(JsonSchema.`object`(properties = Some(Map("country" -> JsonSchema.constOf(Json.str("USA")))))),
          `then` = Some(JsonSchema.`object`(required = Some(Set("postal_code")))),
          `else` = Some(JsonSchema.True)
        )
        assertTrue(
          schema.conforms(Json.obj("country" -> Json.str("USA"), "postal_code" -> Json.str("12345"))),
          schema.conforms(Json.obj("country" -> Json.str("Canada"))),
          !schema.conforms(Json.obj("country" -> Json.str("USA")))
        )
      }
    ),
    suite("oneOf validation")(
      test("oneOf accepts exactly one match") {
        val schema = JsonSchema.SchemaObject(
          oneOf = Some(
            ::(
              JsonSchema.`object`(required = Some(Set("a"))),
              List(JsonSchema.`object`(required = Some(Set("b"))))
            )
          )
        )
        assertTrue(
          schema.conforms(Json.obj("a" -> Json.number(1))),
          schema.conforms(Json.obj("b" -> Json.number(1))),
          !schema.conforms(Json.obj("a" -> Json.number(1), "b" -> Json.number(2))),
          !schema.conforms(Json.obj("c" -> Json.number(1)))
        )
      }
    ),
    suite("Error accumulation")(
      test("multiple errors are accumulated") {
        val schema = JsonSchema.`object`(
          properties = Some(Map("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val result = schema.check(Json.obj())
        assertTrue(
          result.isDefined,
          result.get.errors.length >= 2
        )
      }
    ),
    suite("Roundtrip extended")(
      test("complex schema roundtrip preserves structure") {
        val schema = JsonSchema.`object`(
          properties = Some(
            Map(
              "name"    -> JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(1))),
              "age"     -> JsonSchema.integer(minimum = Some(BigDecimal(0))),
              "tags"    -> JsonSchema.array(items = Some(JsonSchema.string())),
              "address" -> JsonSchema.`object`(properties = Some(Map("city" -> JsonSchema.string())))
            )
          ),
          required = Some(Set("name"))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip.isRight)
      },
      test("empty object parses to empty SchemaObject") {
        val result = JsonSchema.fromJson(Json.obj())
        assertTrue(
          result.isRight,
          result.exists(_.conforms(Json.str("anything"))),
          result.exists(_.conforms(Json.number(42)))
        )
      }
    ),
    suite("Combinator properties")(
      test("&& is associative") {
        val a      = JsonSchema.integer()
        val b      = JsonSchema.number(minimum = Some(BigDecimal(0)))
        val c      = JsonSchema.number(maximum = Some(BigDecimal(100)))
        val left   = (a && b) && c
        val right  = a && (b && c)
        val value1 = Json.number(50)
        val value2 = Json.number(-1)
        val value3 = Json.number(150)
        assertTrue(
          left.conforms(value1) == right.conforms(value1),
          left.conforms(value2) == right.conforms(value2),
          left.conforms(value3) == right.conforms(value3)
        )
      },
      test("|| is associative") {
        val a      = JsonSchema.string()
        val b      = JsonSchema.integer()
        val c      = JsonSchema.boolean
        val left   = (a || b) || c
        val right  = a || (b || c)
        val value1 = Json.str("test")
        val value2 = Json.number(42)
        val value3 = Json.bool(true)
        val value4 = Json.Null
        assertTrue(
          left.conforms(value1) == right.conforms(value1),
          left.conforms(value2) == right.conforms(value2),
          left.conforms(value3) == right.conforms(value3),
          left.conforms(value4) == right.conforms(value4)
        )
      },
      test("True && x == x semantically") {
        val x = JsonSchema.string()
        assertTrue(
          (JsonSchema.True && x).conforms(Json.str("hello")),
          !(JsonSchema.True && x).conforms(Json.number(42))
        )
      },
      test("False || x == x semantically") {
        val x = JsonSchema.integer()
        assertTrue(
          (JsonSchema.False || x).conforms(Json.number(42)),
          !(JsonSchema.False || x).conforms(Json.str("hello"))
        )
      }
    ),
    suite("Schema.fromJsonSchema")(
      test("valid JSON passes validation") {
        val jsonSchema = JsonSchema.`object`(
          properties = Some(Map("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val schemaForJson = Schema.fromJsonSchema(jsonSchema)
        val dv            = Json.obj("name" -> Json.str("Alice")).toDynamicValue
        val result        = schemaForJson.fromDynamicValue(dv)
        assertTrue(result.isRight)
      },
      test("invalid JSON fails validation") {
        val jsonSchema = JsonSchema.`object`(
          properties = Some(Map("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val schemaForJson = Schema.fromJsonSchema(jsonSchema)
        val dv            = Json.obj().toDynamicValue
        val result        = schemaForJson.fromDynamicValue(dv)
        assertTrue(result.isLeft)
      }
    ),
    suite("Schema[Json]")(
      test("Schema[Json] round-trips through DynamicValue") {
        val json   = Json.obj("name" -> Json.str("test"), "count" -> Json.number(42))
        val dv     = Schema.json.toDynamicValue(json)
        val result = Schema.json.fromDynamicValue(dv)
        assertTrue(result == Right(json))
      }
    ),
    suite("Format validation")(
      test("date-time format validates RFC 3339 date-times") {
        val schema = JsonSchema.string(format = Some("date-time"))
        assertTrue(
          schema.conforms(Json.str("2024-01-15T10:30:00Z")),
          schema.conforms(Json.str("2024-01-15T10:30:00+05:00")),
          schema.conforms(Json.str("2024-01-15T10:30:00.123Z")),
          !schema.conforms(Json.str("2024-01-15")),
          !schema.conforms(Json.str("not-a-date-time")),
          !schema.conforms(Json.str("2024-13-15T10:30:00Z")),
          !schema.conforms(Json.str("2024-02-30T10:30:00Z"))
        )
      },
      test("date format validates RFC 3339 dates") {
        val schema = JsonSchema.string(format = Some("date"))
        assertTrue(
          schema.conforms(Json.str("2024-01-15")),
          schema.conforms(Json.str("2024-02-29")),
          !schema.conforms(Json.str("2023-02-29")),
          !schema.conforms(Json.str("2024-13-01")),
          !schema.conforms(Json.str("not-a-date"))
        )
      },
      test("time format validates RFC 3339 times") {
        val schema = JsonSchema.string(format = Some("time"))
        assertTrue(
          schema.conforms(Json.str("10:30:00Z")),
          schema.conforms(Json.str("10:30:00+05:00")),
          schema.conforms(Json.str("10:30:00.123Z")),
          !schema.conforms(Json.str("25:00:00Z")),
          !schema.conforms(Json.str("10:60:00Z")),
          !schema.conforms(Json.str("not-a-time"))
        )
      },
      test("email format validates email addresses") {
        val schema = JsonSchema.string(format = Some("email"))
        assertTrue(
          schema.conforms(Json.str("user@example.com")),
          schema.conforms(Json.str("user.name@sub.domain.org")),
          !schema.conforms(Json.str("not-an-email")),
          !schema.conforms(Json.str("@missing-local.com")),
          !schema.conforms(Json.str("missing-at.com"))
        )
      },
      test("uuid format validates RFC 4122 UUIDs") {
        val schema = JsonSchema.string(format = Some("uuid"))
        assertTrue(
          schema.conforms(Json.str("550e8400-e29b-41d4-a716-446655440000")),
          schema.conforms(Json.str("550E8400-E29B-41D4-A716-446655440000")),
          !schema.conforms(Json.str("not-a-uuid")),
          !schema.conforms(Json.str("550e8400-e29b-41d4-a716")),
          !schema.conforms(Json.str("550e8400e29b41d4a716446655440000"))
        )
      },
      test("uri format validates URIs with scheme") {
        val schema = JsonSchema.string(format = Some("uri"))
        assertTrue(
          schema.conforms(Json.str("https://example.com")),
          schema.conforms(Json.str("http://localhost:8080/path?query=value")),
          schema.conforms(Json.str("mailto:user@example.com")),
          !schema.conforms(Json.str("/relative/path")),
          !schema.conforms(Json.str("example.com"))
        )
      },
      test("uri-reference format validates URIs and relative references") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(
          schema.conforms(Json.str("https://example.com")),
          schema.conforms(Json.str("/relative/path")),
          schema.conforms(Json.str("../parent")),
          schema.conforms(Json.str("#anchor"))
        )
      },
      test("ipv4 format validates IPv4 addresses") {
        val schema = JsonSchema.string(format = Some("ipv4"))
        assertTrue(
          schema.conforms(Json.str("192.168.1.1")),
          schema.conforms(Json.str("0.0.0.0")),
          schema.conforms(Json.str("255.255.255.255")),
          !schema.conforms(Json.str("256.1.1.1")),
          !schema.conforms(Json.str("192.168.1")),
          !schema.conforms(Json.str("not-an-ip"))
        )
      },
      test("ipv6 format validates IPv6 addresses") {
        val schema = JsonSchema.string(format = Some("ipv6"))
        assertTrue(
          schema.conforms(Json.str("2001:0db8:85a3:0000:0000:8a2e:0370:7334")),
          schema.conforms(Json.str("2001:db8:85a3::8a2e:370:7334")),
          schema.conforms(Json.str("::1")),
          schema.conforms(Json.str("::")),
          !schema.conforms(Json.str("not-an-ipv6")),
          !schema.conforms(Json.str("192.168.1.1"))
        )
      },
      test("hostname format validates RFC 1123 hostnames") {
        val schema = JsonSchema.string(format = Some("hostname"))
        assertTrue(
          schema.conforms(Json.str("example.com")),
          schema.conforms(Json.str("sub.example.com")),
          schema.conforms(Json.str("localhost")),
          !schema.conforms(Json.str("-invalid.com")),
          !schema.conforms(Json.str("invalid-.com"))
        )
      },
      test("regex format validates ECMA-262 regular expressions") {
        val schema = JsonSchema.string(format = Some("regex"))
        assertTrue(
          schema.conforms(Json.str("^[a-z]+$")),
          schema.conforms(Json.str("\\d{3}-\\d{4}")),
          !schema.conforms(Json.str("[invalid"))
        )
      },
      test("duration format validates ISO 8601 durations") {
        val schema = JsonSchema.string(format = Some("duration"))
        assertTrue(
          schema.conforms(Json.str("P1Y")),
          schema.conforms(Json.str("P1M")),
          schema.conforms(Json.str("P1D")),
          schema.conforms(Json.str("PT1H")),
          schema.conforms(Json.str("PT1M")),
          schema.conforms(Json.str("PT1S")),
          schema.conforms(Json.str("P1Y2M3DT4H5M6S")),
          !schema.conforms(Json.str("P")),
          !schema.conforms(Json.str("PT")),
          !schema.conforms(Json.str("not-a-duration"))
        )
      },
      test("json-pointer format validates RFC 6901 JSON Pointers") {
        val schema = JsonSchema.string(format = Some("json-pointer"))
        assertTrue(
          schema.conforms(Json.str("")),
          schema.conforms(Json.str("/foo")),
          schema.conforms(Json.str("/foo/bar")),
          schema.conforms(Json.str("/foo~0bar")),
          schema.conforms(Json.str("/foo~1bar")),
          !schema.conforms(Json.str("no-leading-slash"))
        )
      },
      test("unknown formats pass validation (annotation-only)") {
        val schema = JsonSchema.string(format = Some("custom-unknown-format"))
        assertTrue(
          schema.conforms(Json.str("any value")),
          schema.conforms(Json.str("passes because unknown"))
        )
      },
      test("format validation is skipped with annotationOnly options") {
        val schema  = JsonSchema.string(format = Some("email"))
        val options = ValidationOptions.annotationOnly
        assertTrue(
          schema.conforms(Json.str("not-an-email"), options),
          schema.conforms(Json.str("anything"), options)
        )
      },
      test("format validation is enabled with formatAssertion options") {
        val schema  = JsonSchema.string(format = Some("email"))
        val options = ValidationOptions.formatAssertion
        assertTrue(
          schema.conforms(Json.str("user@example.com"), options),
          !schema.conforms(Json.str("not-an-email"), options)
        )
      },
      test("format validation propagates through nested schemas") {
        val schema = JsonSchema.`object`(
          properties = Some(
            Map(
              "email"    -> JsonSchema.string(format = Some("email")),
              "website"  -> JsonSchema.string(format = Some("uri")),
              "contacts" -> JsonSchema.array(items = Some(JsonSchema.string(format = Some("email"))))
            )
          )
        )
        assertTrue(
          schema.conforms(
            Json.obj(
              "email"    -> Json.str("user@example.com"),
              "website"  -> Json.str("https://example.com"),
              "contacts" -> Json.arr(Json.str("a@b.com"), Json.str("c@d.org"))
            )
          ),
          !schema.conforms(
            Json.obj(
              "email"   -> Json.str("invalid-email"),
              "website" -> Json.str("https://example.com")
            )
          ),
          !schema.conforms(
            Json.obj(
              "email"    -> Json.str("user@example.com"),
              "contacts" -> Json.arr(Json.str("a@b.com"), Json.str("invalid"))
            )
          )
        )
      },
      test("format validation only applies to strings") {
        val schema = JsonSchema.SchemaObject(format = Some("email"))
        assertTrue(
          schema.conforms(Json.number(42)),
          schema.conforms(Json.bool(true)),
          schema.conforms(Json.Null),
          schema.conforms(Json.arr()),
          schema.conforms(Json.obj())
        )
      }
    )
  )
}
