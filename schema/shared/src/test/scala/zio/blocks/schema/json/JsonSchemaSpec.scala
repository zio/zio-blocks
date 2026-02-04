package zio.blocks.schema.json

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema._
import zio.test._

object JsonSchemaSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaSpec")(
    suite("JsonSchema ADT")(
      test("True accepts all JSON values") {
        assertTrue(
          JsonSchema.True.conforms(Json.Null),
          JsonSchema.True.conforms(Json.Boolean(true)),
          JsonSchema.True.conforms(Json.String("hello")),
          JsonSchema.True.conforms(Json.Number(42)),
          JsonSchema.True.conforms(Json.Array()),
          JsonSchema.True.conforms(Json.Object())
        )
      },
      test("False rejects all JSON values") {
        assertTrue(
          !JsonSchema.False.conforms(Json.Null),
          !JsonSchema.False.conforms(Json.Boolean(true)),
          !JsonSchema.False.conforms(Json.String("hello"))
        )
      }
    ),
    suite("Type validation")(
      test("string type accepts strings only") {
        val schema = JsonSchema.string()
        assertTrue(
          schema.conforms(Json.String("hello")),
          !schema.conforms(Json.Number(42)),
          !schema.conforms(Json.Boolean(true)),
          !schema.conforms(Json.Null)
        )
      },
      test("integer type accepts integers only") {
        val schema = JsonSchema.integer()
        assertTrue(
          schema.conforms(Json.Number(42)),
          schema.conforms(Json.Number(-100)),
          !schema.conforms(Json.Number(3.14)),
          !schema.conforms(Json.String("42"))
        )
      },
      test("number type accepts all numbers") {
        val schema = JsonSchema.number()
        assertTrue(
          schema.conforms(Json.Number(42)),
          schema.conforms(Json.Number(3.14)),
          !schema.conforms(Json.String("42"))
        )
      },
      test("boolean type accepts booleans only") {
        assertTrue(
          JsonSchema.boolean.conforms(Json.Boolean(true)),
          JsonSchema.boolean.conforms(Json.Boolean(false)),
          !JsonSchema.boolean.conforms(Json.String("true"))
        )
      }
    ),
    suite("Numeric constraints")(
      test("minimum constraint") {
        val schema = JsonSchema.number(minimum = Some(BigDecimal(0)))
        assertTrue(
          schema.conforms(Json.Number(0)),
          schema.conforms(Json.Number(100)),
          !schema.conforms(Json.Number(-1))
        )
      },
      test("maximum constraint") {
        val schema = JsonSchema.number(maximum = Some(BigDecimal(100)))
        assertTrue(
          schema.conforms(Json.Number(100)),
          schema.conforms(Json.Number(0)),
          !schema.conforms(Json.Number(101))
        )
      }
    ),
    suite("String constraints")(
      test("minLength constraint") {
        val schema = JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(3)))
        assertTrue(
          schema.conforms(Json.String("abc")),
          schema.conforms(Json.String("abcd")),
          !schema.conforms(Json.String("ab"))
        )
      },
      test("maxLength constraint") {
        val schema = JsonSchema.string(maxLength = Some(NonNegativeInt.unsafe(5)))
        assertTrue(
          schema.conforms(Json.String("12345")),
          schema.conforms(Json.String("123")),
          !schema.conforms(Json.String("123456"))
        )
      },
      test("pattern constraint") {
        val schema = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$")))
        assertTrue(
          schema.conforms(Json.String("hello")),
          !schema.conforms(Json.String("Hello")),
          !schema.conforms(Json.String("hello123"))
        )
      }
    ),
    suite("Array constraints")(
      test("items constraint") {
        val schema = JsonSchema.array(items = Some(JsonSchema.integer()))
        assertTrue(
          schema.conforms(Json.Array(Json.Number(1), Json.Number(2))),
          schema.conforms(Json.Array()),
          !schema.conforms(Json.Array(Json.String("a")))
        )
      },
      test("minItems constraint") {
        val schema = JsonSchema.array(minItems = Some(NonNegativeInt.unsafe(2)))
        assertTrue(
          schema.conforms(Json.Array(Json.Number(1), Json.Number(2))),
          !schema.conforms(Json.Array(Json.Number(1))),
          !schema.conforms(Json.Array())
        )
      }
    ),
    suite("Object constraints")(
      test("properties constraint") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer()))
        )
        assertTrue(
          schema.conforms(Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))),
          schema.conforms(Json.Object("name" -> Json.String("Bob"))),
          !schema.conforms(Json.Object("name" -> Json.Number(42)))
        )
      },
      test("required constraint") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        assertTrue(
          schema.conforms(Json.Object("name" -> Json.String("Alice"))),
          !schema.conforms(Json.Object())
        )
      },
      test("additionalProperties false") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          additionalProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("name" -> Json.String("Alice"))),
          !schema.conforms(Json.Object("name" -> Json.String("Alice"), "extra" -> Json.Number(1)))
        )
      }
    ),
    suite("Composition")(
      test("allOf combinator (&&)") {
        val schema = JsonSchema.integer() && JsonSchema.number(minimum = Some(BigDecimal(0)))
        assertTrue(
          schema.conforms(Json.Number(42)),
          !schema.conforms(Json.Number(-1)),
          !schema.conforms(Json.Number(3.14))
        )
      },
      test("anyOf combinator (||)") {
        val schema = JsonSchema.string() || JsonSchema.integer()
        assertTrue(
          schema.conforms(Json.String("hello")),
          schema.conforms(Json.Number(42)),
          !schema.conforms(Json.Boolean(true))
        )
      },
      test("not combinator (!)") {
        val schema = !JsonSchema.string()
        assertTrue(
          !schema.conforms(Json.String("hello")),
          schema.conforms(Json.Number(42)),
          schema.conforms(Json.Boolean(true))
        )
      }
    ),
    suite("enum and const")(
      test("enum constraint") {
        val schema = JsonSchema.enumOf(::(Json.String("red"), List(Json.String("green"), Json.String("blue"))))
        assertTrue(
          schema.conforms(Json.String("red")),
          schema.conforms(Json.String("green")),
          !schema.conforms(Json.String("yellow"))
        )
      },
      test("const constraint") {
        val schema = JsonSchema.constOf(Json.String("fixed"))
        assertTrue(
          schema.conforms(Json.String("fixed")),
          !schema.conforms(Json.String("other"))
        )
      }
    ),
    suite("Roundtrip")(
      test("toJson and fromJson roundtrip for simple schema") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
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
          nullable.conforms(Json.String("hello")),
          nullable.conforms(Json.Null),
          !nullable.conforms(Json.Number(42))
        )
      },
      test("withNullable on True returns True") {
        assertTrue(JsonSchema.True.withNullable == JsonSchema.True)
      },
      test("withNullable on False returns Null schema") {
        val result = JsonSchema.False.withNullable
        assertTrue(result match {
          case obj: JsonSchema.Object =>
            obj.`type`.exists {
              case SchemaType.Single(t) => t == JsonSchemaType.Null
              case _                    => false
            }
          case _ => false
        })
      },
      test("withNullable on schema already with Null type returns unchanged") {
        val schema = JsonSchema.ofType(JsonSchemaType.Null)
        assertTrue(schema.withNullable == schema)
      },
      test("withNullable on single non-null type adds Null to union") {
        val schema = JsonSchema.ofType(JsonSchemaType.String)
        val result = schema.withNullable
        assertTrue(result match {
          case obj: JsonSchema.Object =>
            obj.`type`.exists {
              case SchemaType.Union(ts) => ts.contains(JsonSchemaType.Null) && ts.contains(JsonSchemaType.String)
              case _                    => false
            }
          case _ => false
        })
      },
      test("withNullable on union already with Null returns unchanged") {
        val schema =
          JsonSchema.Object(`type` = Some(SchemaType.Union(new ::(JsonSchemaType.Null, JsonSchemaType.String :: Nil))))
        assertTrue(schema.withNullable == schema)
      },
      test("withNullable on union without Null adds Null") {
        val schema = JsonSchema.Object(`type` =
          Some(SchemaType.Union(new ::(JsonSchemaType.String, JsonSchemaType.Integer :: Nil)))
        )
        val result = schema.withNullable
        assertTrue(result match {
          case obj: JsonSchema.Object =>
            obj.`type`.exists {
              case SchemaType.Union(ts) =>
                ts.contains(JsonSchemaType.Null) &&
                ts.contains(JsonSchemaType.String) &&
                ts.contains(JsonSchemaType.Integer)
              case _ => false
            }
          case _ => false
        })
      },
      test("withNullable on schema with no type uses anyOf") {
        val schema = JsonSchema.Object(minLength = Some(NonNegativeInt.one))
        val result = schema.withNullable
        assertTrue(result match {
          case obj: JsonSchema.Object => obj.anyOf.isDefined
          case _                      => false
        })
      }
    ),
    suite("FormatValidator via schema validation")(
      test("uri-reference validation succeeds for valid reference") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(schema.conforms(Json.String("/path/to/resource"), ValidationOptions.formatAssertion))
      },
      test("uri-reference validation fails for invalid reference") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(!schema.conforms(Json.String("://invalid"), ValidationOptions.formatAssertion))
      },
      test("uri-reference validation with relative path") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(schema.conforms(Json.String("relative/path"), ValidationOptions.formatAssertion))
      },
      test("uri-reference validation with fragment") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(schema.conforms(Json.String("#fragment"), ValidationOptions.formatAssertion))
      }
    ),
    suite("Type unions")(
      test("union type accepts any of the types") {
        val schema = JsonSchema.Object(
          `type` = Some(SchemaType.Union(::(JsonSchemaType.String, List(JsonSchemaType.Number))))
        )
        assertTrue(
          schema.conforms(Json.String("hello")),
          schema.conforms(Json.Number(42)),
          !schema.conforms(Json.Boolean(true)),
          !schema.conforms(Json.Null)
        )
      }
    ),
    suite("Numeric constraints extended")(
      test("exclusiveMinimum constraint") {
        val schema = JsonSchema.number(exclusiveMinimum = Some(BigDecimal(0)))
        assertTrue(
          schema.conforms(Json.Number(1)),
          !schema.conforms(Json.Number(0)),
          !schema.conforms(Json.Number(-1))
        )
      },
      test("exclusiveMaximum constraint") {
        val schema = JsonSchema.number(exclusiveMaximum = Some(BigDecimal(100)))
        assertTrue(
          schema.conforms(Json.Number(99)),
          !schema.conforms(Json.Number(100)),
          !schema.conforms(Json.Number(101))
        )
      },
      test("multipleOf constraint") {
        val schema = JsonSchema.number(multipleOf = Some(PositiveNumber.unsafe(BigDecimal(5))))
        assertTrue(
          schema.conforms(Json.Number(0)),
          schema.conforms(Json.Number(10)),
          schema.conforms(Json.Number(-15)),
          !schema.conforms(Json.Number(7))
        )
      }
    ),
    suite("Array constraints extended")(
      test("maxItems constraint") {
        val schema = JsonSchema.array(maxItems = Some(NonNegativeInt.unsafe(3)))
        assertTrue(
          schema.conforms(Json.Array(Json.Number(1), Json.Number(2))),
          schema.conforms(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))),
          !schema.conforms(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4)))
        )
      },
      test("uniqueItems constraint") {
        val schema = JsonSchema.array(uniqueItems = Some(true))
        assertTrue(
          schema.conforms(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))),
          !schema.conforms(Json.Array(Json.Number(1), Json.Number(2), Json.Number(1)))
        )
      },
      test("prefixItems constraint") {
        val schema = JsonSchema.array(prefixItems = Some(::(JsonSchema.string(), List(JsonSchema.integer()))))
        assertTrue(
          schema.conforms(Json.Array(Json.String("a"), Json.Number(1))),
          schema.conforms(Json.Array(Json.String("a"), Json.Number(1), Json.Boolean(true))),
          !schema.conforms(Json.Array(Json.Number(1), Json.String("a")))
        )
      },
      test("contains constraint") {
        val schema = JsonSchema.array(contains = Some(JsonSchema.string()))
        assertTrue(
          schema.conforms(Json.Array(Json.Number(1), Json.String("found"), Json.Number(2))),
          !schema.conforms(Json.Array(Json.Number(1), Json.Number(2)))
        )
      }
    ),
    suite("Object constraints extended")(
      test("minProperties constraint") {
        val schema = JsonSchema.obj(minProperties = Some(NonNegativeInt.unsafe(2)))
        assertTrue(
          schema.conforms(Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))),
          !schema.conforms(Json.Object("a" -> Json.Number(1)))
        )
      },
      test("maxProperties constraint") {
        val schema = JsonSchema.obj(maxProperties = Some(NonNegativeInt.unsafe(2)))
        assertTrue(
          schema.conforms(Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))),
          !schema.conforms(Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2), "c" -> Json.Number(3)))
        )
      },
      test("propertyNames constraint") {
        val schema = JsonSchema.obj(
          propertyNames = Some(JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$"))))
        )
        assertTrue(
          schema.conforms(Json.Object("name" -> Json.Number(1))),
          !schema.conforms(Json.Object("Name" -> Json.Number(1)))
        )
      },
      test("dependentRequired constraint") {
        val schema = JsonSchema.Object(
          dependentRequired = Some(ChunkMap("credit_card" -> Set("billing_address")))
        )
        assertTrue(
          schema.conforms(Json.Object("name" -> Json.String("Alice"))),
          schema.conforms(
            Json.Object("credit_card" -> Json.String("1234"), "billing_address" -> Json.String("123 Main St"))
          ),
          !schema.conforms(Json.Object("credit_card" -> Json.String("1234")))
        )
      }
    ),
    suite("Conditional validation")(
      test("if/then/else validation") {
        val schema = JsonSchema.Object(
          `if` = Some(JsonSchema.obj(properties = Some(ChunkMap("country" -> JsonSchema.constOf(Json.String("USA")))))),
          `then` = Some(JsonSchema.obj(required = Some(Set("postal_code")))),
          `else` = Some(JsonSchema.True)
        )
        assertTrue(
          schema.conforms(Json.Object("country" -> Json.String("USA"), "postal_code" -> Json.String("12345"))),
          schema.conforms(Json.Object("country" -> Json.String("Canada"))),
          !schema.conforms(Json.Object("country" -> Json.String("USA")))
        )
      }
    ),
    suite("oneOf validation")(
      test("oneOf accepts exactly one match") {
        val schema = JsonSchema.Object(
          oneOf = Some(
            ::(
              JsonSchema.obj(required = Some(Set("a"))),
              List(JsonSchema.obj(required = Some(Set("b"))))
            )
          )
        )
        assertTrue(
          schema.conforms(Json.Object("a" -> Json.Number(1))),
          schema.conforms(Json.Object("b" -> Json.Number(1))),
          !schema.conforms(Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))),
          !schema.conforms(Json.Object("c" -> Json.Number(1)))
        )
      }
    ),
    suite("Error accumulation")(
      test("multiple errors are accumulated") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val result = schema.check(Json.Object())
        assertTrue(
          result.isDefined,
          result.get.errors.length >= 2
        )
      }
    ),
    suite("Roundtrip extended")(
      test("complex schema roundtrip preserves structure") {
        val schema = JsonSchema.obj(
          properties = Some(
            ChunkMap(
              "name"    -> JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(1))),
              "age"     -> JsonSchema.integer(minimum = Some(BigDecimal(0))),
              "tags"    -> JsonSchema.array(items = Some(JsonSchema.string())),
              "address" -> JsonSchema.obj(properties = Some(ChunkMap("city" -> JsonSchema.string())))
            )
          ),
          required = Some(Set("name"))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip.isRight)
      },
      test("empty object parses to empty Object") {
        val result = JsonSchema.fromJson(Json.Object())
        assertTrue(
          result.isRight,
          result.exists(_.conforms(Json.String("anything"))),
          result.exists(_.conforms(Json.Number(42)))
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
        val value1 = Json.Number(50)
        val value2 = Json.Number(-1)
        val value3 = Json.Number(150)
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
        val value1 = Json.String("test")
        val value2 = Json.Number(42)
        val value3 = Json.Boolean(true)
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
          (JsonSchema.True && x).conforms(Json.String("hello")),
          !(JsonSchema.True && x).conforms(Json.Number(42))
        )
      },
      test("False || x == x semantically") {
        val x = JsonSchema.integer()
        assertTrue(
          (JsonSchema.False || x).conforms(Json.Number(42)),
          !(JsonSchema.False || x).conforms(Json.String("hello"))
        )
      }
    ),
    suite("Schema.fromJsonSchema")(
      test("valid JSON passes validation") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val schemaForJson = Schema.fromJsonSchema(jsonSchema)
        val dv            = Json.Object("name" -> Json.String("Alice")).toDynamicValue
        val result        = schemaForJson.fromDynamicValue(dv)
        assertTrue(result.isRight)
      },
      test("invalid JSON fails validation") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val schemaForJson = Schema.fromJsonSchema(jsonSchema)
        val dv            = Json.Object().toDynamicValue
        val result        = schemaForJson.fromDynamicValue(dv)
        assertTrue(result.isLeft)
      }
    ),
    suite("Schema[Json]")(
      test("Schema[Json] round-trips through DynamicValue") {
        val json   = Json.Object("name" -> Json.String("test"), "count" -> Json.Number(42))
        val dv     = Json.schema.toDynamicValue(json)
        val result = Json.schema.fromDynamicValue(dv)
        assertTrue(result == Right(json))
      }
    ),
    suite("Format validation")(
      test("date-time format validates RFC 3339 date-times") {
        val schema = JsonSchema.string(format = Some("date-time"))
        assertTrue(
          schema.conforms(Json.String("2024-01-15T10:30:00Z")),
          schema.conforms(Json.String("2024-01-15T10:30:00+05:00")),
          schema.conforms(Json.String("2024-01-15T10:30:00.123Z")),
          !schema.conforms(Json.String("2024-01-15")),
          !schema.conforms(Json.String("not-a-date-time")),
          !schema.conforms(Json.String("2024-13-15T10:30:00Z")),
          !schema.conforms(Json.String("2024-02-30T10:30:00Z"))
        )
      },
      test("date format validates RFC 3339 dates") {
        val schema = JsonSchema.string(format = Some("date"))
        assertTrue(
          schema.conforms(Json.String("2024-01-15")),
          schema.conforms(Json.String("2024-02-29")),
          !schema.conforms(Json.String("2023-02-29")),
          !schema.conforms(Json.String("2024-13-01")),
          !schema.conforms(Json.String("not-a-date"))
        )
      },
      test("time format validates RFC 3339 times") {
        val schema = JsonSchema.string(format = Some("time"))
        assertTrue(
          schema.conforms(Json.String("10:30:00Z")),
          schema.conforms(Json.String("10:30:00+05:00")),
          schema.conforms(Json.String("10:30:00.123Z")),
          !schema.conforms(Json.String("25:00:00Z")),
          !schema.conforms(Json.String("10:60:00Z")),
          !schema.conforms(Json.String("not-a-time"))
        )
      },
      test("email format validates email addresses") {
        val schema = JsonSchema.string(format = Some("email"))
        assertTrue(
          schema.conforms(Json.String("user@example.com")),
          schema.conforms(Json.String("user.name@sub.domain.org")),
          !schema.conforms(Json.String("not-an-email")),
          !schema.conforms(Json.String("@missing-local.com")),
          !schema.conforms(Json.String("missing-at.com"))
        )
      },
      test("uuid format validates RFC 4122 UUIDs") {
        val schema = JsonSchema.string(format = Some("uuid"))
        assertTrue(
          schema.conforms(Json.String("550e8400-e29b-41d4-a716-446655440000")),
          schema.conforms(Json.String("550E8400-E29B-41D4-A716-446655440000")),
          !schema.conforms(Json.String("not-a-uuid")),
          !schema.conforms(Json.String("550e8400-e29b-41d4-a716")),
          !schema.conforms(Json.String("550e8400e29b41d4a716446655440000"))
        )
      },
      test("uri format validates URIs with scheme") {
        val schema = JsonSchema.string(format = Some("uri"))
        assertTrue(
          schema.conforms(Json.String("https://example.com")),
          schema.conforms(Json.String("http://localhost:8080/path?query=value")),
          schema.conforms(Json.String("mailto:user@example.com")),
          !schema.conforms(Json.String("/relative/path")),
          !schema.conforms(Json.String("example.com"))
        )
      },
      test("uri-reference format validates URIs and relative references") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(
          schema.conforms(Json.String("https://example.com")),
          schema.conforms(Json.String("/relative/path")),
          schema.conforms(Json.String("../parent")),
          schema.conforms(Json.String("#anchor"))
        )
      },
      test("ipv4 format validates IPv4 addresses") {
        val schema = JsonSchema.string(format = Some("ipv4"))
        assertTrue(
          schema.conforms(Json.String("192.168.1.1")),
          schema.conforms(Json.String("0.0.0.0")),
          schema.conforms(Json.String("255.255.255.255")),
          !schema.conforms(Json.String("256.1.1.1")),
          !schema.conforms(Json.String("192.168.1")),
          !schema.conforms(Json.String("not-an-ip"))
        )
      },
      test("ipv6 format validates IPv6 addresses") {
        val schema = JsonSchema.string(format = Some("ipv6"))
        assertTrue(
          schema.conforms(Json.String("2001:0db8:85a3:0000:0000:8a2e:0370:7334")),
          schema.conforms(Json.String("2001:db8:85a3::8a2e:370:7334")),
          schema.conforms(Json.String("::1")),
          schema.conforms(Json.String("::")),
          !schema.conforms(Json.String("not-an-ipv6")),
          !schema.conforms(Json.String("192.168.1.1"))
        )
      },
      test("hostname format validates RFC 1123 hostnames") {
        val schema = JsonSchema.string(format = Some("hostname"))
        assertTrue(
          schema.conforms(Json.String("example.com")),
          schema.conforms(Json.String("sub.example.com")),
          schema.conforms(Json.String("localhost")),
          !schema.conforms(Json.String("-invalid.com")),
          !schema.conforms(Json.String("invalid-.com"))
        )
      },
      test("regex format validates ECMA-262 regular expressions") {
        val schema = JsonSchema.string(format = Some("regex"))
        assertTrue(
          schema.conforms(Json.String("^[a-z]+$")),
          schema.conforms(Json.String("\\d{3}-\\d{4}")),
          !schema.conforms(Json.String("[invalid"))
        )
      },
      test("duration format validates ISO 8601 durations") {
        val schema = JsonSchema.string(format = Some("duration"))
        assertTrue(
          schema.conforms(Json.String("P1Y")),
          schema.conforms(Json.String("P1M")),
          schema.conforms(Json.String("P1D")),
          schema.conforms(Json.String("PT1H")),
          schema.conforms(Json.String("PT1M")),
          schema.conforms(Json.String("PT1S")),
          schema.conforms(Json.String("P1Y2M3DT4H5M6S")),
          !schema.conforms(Json.String("P")),
          !schema.conforms(Json.String("PT")),
          !schema.conforms(Json.String("not-a-duration"))
        )
      },
      test("json-pointer format validates RFC 6901 JSON Pointers") {
        val schema = JsonSchema.string(format = Some("json-pointer"))
        assertTrue(
          schema.conforms(Json.String("")),
          schema.conforms(Json.String("/foo")),
          schema.conforms(Json.String("/foo/bar")),
          schema.conforms(Json.String("/foo~0bar")),
          schema.conforms(Json.String("/foo~1bar")),
          !schema.conforms(Json.String("no-leading-slash"))
        )
      },
      test("unknown formats pass validation (annotation-only)") {
        val schema = JsonSchema.string(format = Some("custom-unknown-format"))
        assertTrue(
          schema.conforms(Json.String("any value")),
          schema.conforms(Json.String("passes because unknown"))
        )
      },
      test("format validation is skipped with annotationOnly options") {
        val schema  = JsonSchema.string(format = Some("email"))
        val options = ValidationOptions.annotationOnly
        assertTrue(
          schema.conforms(Json.String("not-an-email"), options),
          schema.conforms(Json.String("anything"), options)
        )
      },
      test("format validation is enabled with formatAssertion options") {
        val schema  = JsonSchema.string(format = Some("email"))
        val options = ValidationOptions.formatAssertion
        assertTrue(
          schema.conforms(Json.String("user@example.com"), options),
          !schema.conforms(Json.String("not-an-email"), options)
        )
      },
      test("format validation propagates through nested schemas") {
        val schema = JsonSchema.obj(
          properties = Some(
            ChunkMap(
              "email"    -> JsonSchema.string(format = Some("email")),
              "website"  -> JsonSchema.string(format = Some("uri")),
              "contacts" -> JsonSchema.array(items = Some(JsonSchema.string(format = Some("email"))))
            )
          )
        )
        assertTrue(
          schema.conforms(
            Json.Object(
              "email"    -> Json.String("user@example.com"),
              "website"  -> Json.String("https://example.com"),
              "contacts" -> Json.Array(Json.String("a@b.com"), Json.String("c@d.org"))
            )
          ),
          !schema.conforms(
            Json.Object(
              "email"   -> Json.String("invalid-email"),
              "website" -> Json.String("https://example.com")
            )
          ),
          !schema.conforms(
            Json.Object(
              "email"    -> Json.String("user@example.com"),
              "contacts" -> Json.Array(Json.String("a@b.com"), Json.String("invalid"))
            )
          )
        )
      },
      test("format validation only applies to strings") {
        val schema = JsonSchema.Object(format = Some("email"))
        assertTrue(
          schema.conforms(Json.Number(42)),
          schema.conforms(Json.Boolean(true)),
          schema.conforms(Json.Null),
          schema.conforms(Json.Array()),
          schema.conforms(Json.Object())
        )
      }
    ),
    suite("unevaluatedProperties")(
      test("rejects unevaluated properties when schema is False") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("name" -> Json.String("Alice"))),
          !schema.conforms(Json.Object("name" -> Json.String("Alice"), "extra" -> Json.Number(1)))
        )
      },
      test("validates unevaluated properties against schema") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          unevaluatedProperties = Some(JsonSchema.integer())
        )
        assertTrue(
          schema.conforms(Json.Object("name" -> Json.String("Alice"))),
          schema.conforms(Json.Object("name" -> Json.String("Alice"), "extra" -> Json.Number(42))),
          !schema.conforms(Json.Object("name" -> Json.String("Alice"), "extra" -> Json.String("not-an-int")))
        )
      },
      test("properties evaluated by patternProperties are not unevaluated") {
        val schema = JsonSchema.obj(
          patternProperties = Some(ChunkMap(RegexPattern.unsafe("^x_") -> JsonSchema.string())),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("x_foo" -> Json.String("bar"))),
          !schema.conforms(Json.Object("foo" -> Json.String("bar")))
        )
      },
      test("properties evaluated by additionalProperties are not unevaluated") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          additionalProperties = Some(JsonSchema.number()),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))),
          !schema.conforms(Json.Object("name" -> Json.String("Alice"), "age" -> Json.String("thirty")))
        )
      },
      test("properties from allOf subschemas are evaluated") {
        val schema = JsonSchema.Object(
          allOf = Some(
            ::(
              JsonSchema.obj(properties = Some(ChunkMap("foo" -> JsonSchema.string()))),
              List(JsonSchema.obj(properties = Some(ChunkMap("bar" -> JsonSchema.integer()))))
            )
          ),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("foo" -> Json.String("a"), "bar" -> Json.Number(1))),
          !schema.conforms(Json.Object("foo" -> Json.String("a"), "bar" -> Json.Number(1), "baz" -> Json.Boolean(true)))
        )
      },
      test("properties from anyOf valid subschemas are evaluated") {
        val schema = JsonSchema.Object(
          anyOf = Some(
            ::(
              JsonSchema.obj(properties = Some(ChunkMap("foo" -> JsonSchema.string()))),
              List(JsonSchema.obj(properties = Some(ChunkMap("bar" -> JsonSchema.integer()))))
            )
          ),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("foo" -> Json.String("a"))),
          schema.conforms(Json.Object("bar" -> Json.Number(1))),
          !schema.conforms(Json.Object("baz" -> Json.Boolean(true)))
        )
      },
      test("properties from oneOf valid subschema are evaluated") {
        val schema = JsonSchema.Object(
          oneOf = Some(
            ::(
              JsonSchema.obj(properties =
                Some(ChunkMap("type" -> JsonSchema.constOf(Json.String("a")), "a" -> JsonSchema.string()))
              ),
              List(
                JsonSchema.obj(properties =
                  Some(ChunkMap("type" -> JsonSchema.constOf(Json.String("b")), "b" -> JsonSchema.integer()))
                )
              )
            )
          ),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("type" -> Json.String("a"), "a" -> Json.String("value"))),
          schema.conforms(Json.Object("type" -> Json.String("b"), "b" -> Json.Number(42))),
          !schema.conforms(
            Json.Object("type" -> Json.String("a"), "a" -> Json.String("value"), "extra" -> Json.Boolean(true))
          )
        )
      },
      test("properties from if/then branch are evaluated") {
        val schema = JsonSchema.Object(
          `if` = Some(JsonSchema.obj(properties = Some(ChunkMap("type" -> JsonSchema.constOf(Json.String("a")))))),
          `then` = Some(JsonSchema.obj(properties = Some(ChunkMap("a" -> JsonSchema.string())))),
          `else` = Some(JsonSchema.obj(properties = Some(ChunkMap("b" -> JsonSchema.integer())))),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("type" -> Json.String("a"), "a" -> Json.String("value"))),
          schema.conforms(Json.Object("type" -> Json.String("b"), "b" -> Json.Number(42))),
          !schema.conforms(
            Json.Object("type" -> Json.String("a"), "a" -> Json.String("value"), "extra" -> Json.Boolean(true))
          )
        )
      },
      test("properties from $ref are evaluated") {
        val schema = JsonSchema.Object(
          $defs = Some(ChunkMap("base" -> JsonSchema.obj(properties = Some(ChunkMap("foo" -> JsonSchema.string()))))),
          $ref = Some(UriReference("#/$defs/base")),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Object("foo" -> Json.String("bar"))),
          !schema.conforms(Json.Object("foo" -> Json.String("bar"), "extra" -> Json.Number(1)))
        )
      }
    ),
    suite("unevaluatedItems")(
      test("rejects unevaluated items when schema is False") {
        val schema = JsonSchema.array(
          prefixItems = Some(::(JsonSchema.string(), List(JsonSchema.integer()))),
          unevaluatedItems = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Array(Json.String("a"), Json.Number(1))),
          !schema.conforms(Json.Array(Json.String("a"), Json.Number(1), Json.Boolean(true)))
        )
      },
      test("validates unevaluated items against schema") {
        val schema = JsonSchema.array(
          prefixItems = Some(::(JsonSchema.string(), Nil)),
          unevaluatedItems = Some(JsonSchema.integer())
        )
        assertTrue(
          schema.conforms(Json.Array(Json.String("a"))),
          schema.conforms(Json.Array(Json.String("a"), Json.Number(1), Json.Number(2))),
          !schema.conforms(Json.Array(Json.String("a"), Json.String("not-an-int")))
        )
      },
      test("items keyword evaluates all remaining items") {
        val schema = JsonSchema.array(
          prefixItems = Some(::(JsonSchema.string(), Nil)),
          items = Some(JsonSchema.number()),
          unevaluatedItems = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Array(Json.String("a"), Json.Number(1), Json.Number(2))),
          !schema.conforms(Json.Array(Json.String("a"), Json.String("not-a-number")))
        )
      },
      test("items from allOf subschemas are evaluated") {
        val schema = JsonSchema.Object(
          `type` = Some(SchemaType.Single(JsonSchemaType.Array)),
          allOf = Some(
            ::(
              JsonSchema.array(prefixItems = Some(::(JsonSchema.string(), Nil))),
              List(JsonSchema.array(prefixItems = Some(::(JsonSchema.True, List(JsonSchema.integer())))))
            )
          ),
          unevaluatedItems = Some(JsonSchema.False)
        )
        assertTrue(
          schema.conforms(Json.Array(Json.String("a"), Json.Number(1))),
          !schema.conforms(Json.Array(Json.String("a"), Json.Number(1), Json.Boolean(true)))
        )
      },
      test("contains does not mark items as evaluated") {
        val schema = JsonSchema.array(
          contains = Some(JsonSchema.string()),
          unevaluatedItems = Some(JsonSchema.False)
        )
        assertTrue(
          !schema.conforms(Json.Array(Json.String("a"))),
          !schema.conforms(Json.Array(Json.Number(1), Json.String("b")))
        )
      }
    )
  )
}
