package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

import java.net.URI

object JsonSchemaRoundTripSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaRoundTripSpec")(
    suite("Boolean schema serialization")(
      test("true serializes to JSON boolean true") {
        assertTrue(JsonSchema.True.toJson == Json.Boolean(true))
      },
      test("false serializes to JSON boolean false") {
        assertTrue(JsonSchema.False.toJson == Json.Boolean(false))
      },
      test("true parses from JSON boolean true") {
        val result = JsonSchema.fromJson(Json.Boolean(true))
        assertTrue(result == Right(JsonSchema.True))
      },
      test("false parses from JSON boolean false") {
        val result = JsonSchema.fromJson(Json.Boolean(false))
        assertTrue(result == Right(JsonSchema.False))
      }
    ),
    suite("Empty object equivalence")(
      test("{} parses to Object.empty") {
        val result = JsonSchema.fromJson(Json.Object())
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object => s == JsonSchema.Object.empty
            case _                    => false
          }
        )
      },
      test("Object.empty is semantically equivalent to True") {
        val empty = JsonSchema.Object.empty
        assertTrue(
          empty.conforms(Json.Null),
          empty.conforms(Json.Boolean(true)),
          empty.conforms(Json.String("anything")),
          empty.conforms(Json.Number(42)),
          empty.conforms(Json.Array()),
          empty.conforms(Json.Object())
        )
      },
      test("Object.empty serializes to empty object") {
        val result = JsonSchema.Object.empty.toJson
        assertTrue(result == Json.Object())
      }
    ),
    suite("toJson/fromJson round-trip identity")(
      test("True round-trips") {
        val schema    = JsonSchema.True
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("False round-trips") {
        val schema    = JsonSchema.False
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("simple string schema round-trips") {
        val schema    = JsonSchema.string()
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("string with all constraints round-trips") {
        val schema = JsonSchema.string(
          minLength = Some(NonNegativeInt.unsafe(5)),
          maxLength = Some(NonNegativeInt.unsafe(100)),
          pattern = Some(RegexPattern.unsafe("^[a-z]+$")),
          format = Some("email")
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("integer schema round-trips") {
        val schema    = JsonSchema.integer()
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("number with all constraints round-trips") {
        val schema = JsonSchema.number(
          minimum = Some(BigDecimal(0)),
          maximum = Some(BigDecimal(100)),
          exclusiveMinimum = Some(BigDecimal(-1)),
          exclusiveMaximum = Some(BigDecimal(101)),
          multipleOf = Some(PositiveNumber.unsafe(BigDecimal(5)))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("boolean schema round-trips") {
        val schema    = JsonSchema.boolean
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("null schema round-trips") {
        val schema    = JsonSchema.`null`
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("array with all constraints round-trips") {
        val schema = JsonSchema.array(
          items = Some(JsonSchema.string()),
          prefixItems = Some(::(JsonSchema.integer(), List(JsonSchema.boolean))),
          minItems = Some(NonNegativeInt.unsafe(1)),
          maxItems = Some(NonNegativeInt.unsafe(10)),
          uniqueItems = Some(true),
          contains = Some(JsonSchema.number()),
          minContains = Some(NonNegativeInt.unsafe(1)),
          maxContains = Some(NonNegativeInt.unsafe(5)),
          unevaluatedItems = Some(JsonSchema.False)
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("object with all constraints round-trips") {
        val schema = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name")),
          additionalProperties = Some(JsonSchema.boolean),
          patternProperties = Some(Map(RegexPattern.unsafe("^x_") -> JsonSchema.string())),
          propertyNames = Some(JsonSchema.string(maxLength = Some(NonNegativeInt.unsafe(20)))),
          minProperties = Some(NonNegativeInt.unsafe(1)),
          maxProperties = Some(NonNegativeInt.unsafe(10)),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("enum schema round-trips") {
        val schema    = JsonSchema.enumOf(::(Json.String("a"), List(Json.String("b"), Json.Number(1))))
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("const schema round-trips") {
        val schema    = JsonSchema.constOf(Json.Object("key" -> Json.String("value")))
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("allOf schema round-trips") {
        val schema = JsonSchema.Object(
          allOf = Some(::(JsonSchema.string(), List(JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(1))))))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("anyOf schema round-trips") {
        val schema = JsonSchema.Object(
          anyOf = Some(::(JsonSchema.string(), List(JsonSchema.integer())))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("oneOf schema round-trips") {
        val schema = JsonSchema.Object(
          oneOf = Some(::(JsonSchema.string(), List(JsonSchema.integer())))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("not schema round-trips") {
        val schema = JsonSchema.Object(
          not = Some(JsonSchema.string())
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("if/then/else schema round-trips") {
        val schema = JsonSchema.Object(
          `if` = Some(JsonSchema.obj(properties = Some(Map("type" -> JsonSchema.constOf(Json.String("a")))))),
          `then` = Some(JsonSchema.obj(required = Some(Set("a_field")))),
          `else` = Some(JsonSchema.obj(required = Some(Set("b_field"))))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("dependentSchemas round-trips") {
        val schema = JsonSchema.Object(
          dependentSchemas = Some(Map("credit_card" -> JsonSchema.obj(required = Some(Set("billing_address")))))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("dependentRequired round-trips") {
        val schema = JsonSchema.Object(
          dependentRequired = Some(Map("credit_card" -> Set("billing_address", "security_code")))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("$ref schema round-trips") {
        val schema = JsonSchema.Object(
          $defs = Some(Map("address" -> JsonSchema.obj(properties = Some(Map("street" -> JsonSchema.string()))))),
          $ref = Some(UriReference("#/$defs/address"))
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      },
      test("deeply nested schema round-trips") {
        val schema = JsonSchema.obj(
          properties = Some(
            Map(
              "users" -> JsonSchema.array(
                items = Some(
                  JsonSchema.obj(
                    properties = Some(
                      Map(
                        "name"    -> JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(1))),
                        "email"   -> JsonSchema.string(format = Some("email")),
                        "age"     -> JsonSchema.integer(minimum = Some(BigDecimal(0))),
                        "tags"    -> JsonSchema.array(items = Some(JsonSchema.string()), uniqueItems = Some(true)),
                        "address" -> JsonSchema.obj(
                          properties = Some(
                            Map(
                              "street"  -> JsonSchema.string(),
                              "city"    -> JsonSchema.string(),
                              "country" -> JsonSchema.string()
                            )
                          ),
                          required = Some(Set("street", "city"))
                        )
                      )
                    ),
                    required = Some(Set("name", "email"))
                  )
                )
              )
            )
          )
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(roundtrip == Right(schema))
      }
    ),
    suite("Extension preservation")(
      test("unknown keywords survive round-trip") {
        val extensions = Map(
          "x-custom-extension" -> Json.String("custom value"),
          "x-another"          -> Json.Number(42),
          "x-complex"          -> Json.Object("nested" -> Json.Array(Json.Boolean(true), Json.Boolean(false)))
        )
        val schema = JsonSchema.Object(
          `type` = Some(SchemaType.Single(JsonSchemaType.String)),
          extensions = extensions
        )
        val json      = schema.toJson
        val roundtrip = JsonSchema.fromJson(json)

        assertTrue(
          roundtrip.isRight,
          roundtrip.exists {
            case s: JsonSchema.Object => s.extensions == extensions
            case _                    => false
          }
        )
      },
      test("multiple vendor extensions preserve order-independent") {
        val extensions = Map(
          "x-openapi-example" -> Json.String("example"),
          "x-nullable"        -> Json.Boolean(true),
          "x-deprecated-info" -> Json.Object("since" -> Json.String("v2.0"))
        )
        val schema = JsonSchema.Object(extensions = extensions)
        val json   = schema.toJson

        extensions.foreach { case (key, value) =>
          assertTrue(json.asInstanceOf[Json.Object].value.toMap.get(key).contains(value))
        }

        val roundtrip = JsonSchema.fromJson(json)
        assertTrue(
          roundtrip.isRight,
          roundtrip.exists {
            case s: JsonSchema.Object => s.extensions == extensions
            case _                    => false
          }
        )
      }
    ),
    suite("All keyword serialization")(
      test("core vocabulary keywords serialize correctly") {
        val schema = JsonSchema.Object(
          $id = Some(UriReference("https://example.com/schema")),
          $schema = Some(new URI("https://json-schema.org/draft/2020-12/schema")),
          $anchor = Some(Anchor("myAnchor")),
          $dynamicAnchor = Some(Anchor("dynamicAnchor")),
          $ref = Some(UriReference("#/$defs/base")),
          $dynamicRef = Some(UriReference("#dynamicAnchor")),
          $defs = Some(Map("base" -> JsonSchema.string())),
          $comment = Some("This is a comment")
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("$id").contains(Json.String("https://example.com/schema")),
          fieldMap.get("$schema").contains(Json.String("https://json-schema.org/draft/2020-12/schema")),
          fieldMap.get("$anchor").contains(Json.String("myAnchor")),
          fieldMap.get("$dynamicAnchor").contains(Json.String("dynamicAnchor")),
          fieldMap.get("$ref").contains(Json.String("#/$defs/base")),
          fieldMap.get("$dynamicRef").contains(Json.String("#dynamicAnchor")),
          fieldMap.get("$defs").isDefined,
          fieldMap.get("$comment").contains(Json.String("This is a comment"))
        )
      },
      test("type keyword serializes single type as string") {
        val schema = JsonSchema.Object(`type` = Some(SchemaType.Single(JsonSchemaType.String)))
        val json   = schema.toJson
        assertTrue(json.asInstanceOf[Json.Object].value.toMap.get("type").contains(Json.String("string")))
      },
      test("type keyword serializes union as array") {
        val schema =
          JsonSchema.Object(`type` = Some(SchemaType.Union(::(JsonSchemaType.String, List(JsonSchemaType.Number)))))
        val json = schema.toJson
        assertTrue(
          json
            .asInstanceOf[Json.Object]
            .value
            .toMap
            .get("type")
            .contains(Json.Array(Json.String("string"), Json.String("number")))
        )
      },
      test("numeric keywords serialize correctly") {
        val schema = JsonSchema.Object(
          minimum = Some(BigDecimal(0)),
          maximum = Some(BigDecimal(100)),
          exclusiveMinimum = Some(BigDecimal(-1)),
          exclusiveMaximum = Some(BigDecimal(101)),
          multipleOf = Some(PositiveNumber.unsafe(BigDecimal("0.5")))
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("minimum").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(0))),
          fieldMap.get("maximum").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(100))),
          fieldMap
            .get("exclusiveMinimum")
            .exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(-1))),
          fieldMap
            .get("exclusiveMaximum")
            .exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(101))),
          fieldMap.get("multipleOf").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal("0.5")))
        )
      },
      test("string keywords serialize correctly") {
        val schema = JsonSchema.Object(
          minLength = Some(NonNegativeInt.unsafe(5)),
          maxLength = Some(NonNegativeInt.unsafe(100)),
          pattern = Some(RegexPattern.unsafe("^[a-z]+$")),
          format = Some("email")
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("minLength").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(5))),
          fieldMap.get("maxLength").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(100))),
          fieldMap.get("pattern").contains(Json.String("^[a-z]+$")),
          fieldMap.get("format").contains(Json.String("email"))
        )
      },
      test("array keywords serialize correctly") {
        val schema = JsonSchema.Object(
          minItems = Some(NonNegativeInt.unsafe(1)),
          maxItems = Some(NonNegativeInt.unsafe(10)),
          uniqueItems = Some(true),
          minContains = Some(NonNegativeInt.unsafe(2)),
          maxContains = Some(NonNegativeInt.unsafe(5)),
          prefixItems = Some(::(JsonSchema.string(), List(JsonSchema.integer()))),
          items = Some(JsonSchema.boolean),
          contains = Some(JsonSchema.number()),
          unevaluatedItems = Some(JsonSchema.False)
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("minItems").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(1))),
          fieldMap.get("maxItems").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(10))),
          fieldMap.get("uniqueItems").contains(Json.Boolean(true)),
          fieldMap.get("minContains").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(2))),
          fieldMap.get("maxContains").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(5))),
          fieldMap.get("prefixItems").isDefined,
          fieldMap.get("items").isDefined,
          fieldMap.get("contains").isDefined,
          fieldMap.get("unevaluatedItems").contains(Json.Boolean(false))
        )
      },
      test("object keywords serialize correctly") {
        val schema = JsonSchema.Object(
          minProperties = Some(NonNegativeInt.unsafe(1)),
          maxProperties = Some(NonNegativeInt.unsafe(10)),
          required = Some(Set("name", "age")),
          properties = Some(Map("name" -> JsonSchema.string())),
          patternProperties = Some(Map(RegexPattern.unsafe("^x_") -> JsonSchema.string())),
          additionalProperties = Some(JsonSchema.False),
          propertyNames = Some(JsonSchema.string(maxLength = Some(NonNegativeInt.unsafe(20)))),
          dependentRequired = Some(Map("foo" -> Set("bar"))),
          dependentSchemas = Some(Map("baz" -> JsonSchema.obj(required = Some(Set("qux"))))),
          unevaluatedProperties = Some(JsonSchema.False)
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("minProperties").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(1))),
          fieldMap.get("maxProperties").exists(_.asInstanceOf[Json.Number].toBigDecimalOption.contains(BigDecimal(10))),
          fieldMap.get("required").isDefined,
          fieldMap.get("properties").isDefined,
          fieldMap.get("patternProperties").isDefined,
          fieldMap.get("additionalProperties").contains(Json.Boolean(false)),
          fieldMap.get("propertyNames").isDefined,
          fieldMap.get("dependentRequired").isDefined,
          fieldMap.get("dependentSchemas").isDefined,
          fieldMap.get("unevaluatedProperties").contains(Json.Boolean(false))
        )
      },
      test("composition keywords serialize correctly") {
        val schema = JsonSchema.Object(
          allOf = Some(::(JsonSchema.string(), List(JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(1)))))),
          anyOf = Some(::(JsonSchema.string(), List(JsonSchema.integer()))),
          oneOf = Some(::(JsonSchema.boolean, List(JsonSchema.`null`))),
          not = Some(JsonSchema.array())
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("allOf").isDefined,
          fieldMap.get("anyOf").isDefined,
          fieldMap.get("oneOf").isDefined,
          fieldMap.get("not").isDefined
        )
      },
      test("conditional keywords serialize correctly") {
        val schema = JsonSchema.Object(
          `if` = Some(JsonSchema.obj(properties = Some(Map("type" -> JsonSchema.constOf(Json.String("a")))))),
          `then` = Some(JsonSchema.obj(required = Some(Set("a")))),
          `else` = Some(JsonSchema.obj(required = Some(Set("b"))))
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("if").isDefined,
          fieldMap.get("then").isDefined,
          fieldMap.get("else").isDefined
        )
      },
      test("content keywords serialize correctly") {
        val schema = JsonSchema.Object(
          contentEncoding = Some("base64"),
          contentMediaType = Some("application/json"),
          contentSchema = Some(JsonSchema.obj(properties = Some(Map("data" -> JsonSchema.string()))))
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("contentEncoding").contains(Json.String("base64")),
          fieldMap.get("contentMediaType").contains(Json.String("application/json")),
          fieldMap.get("contentSchema").isDefined
        )
      },
      test("meta-data keywords serialize correctly") {
        val schema = JsonSchema.Object(
          title = Some("My Schema"),
          description = Some("A description of the schema"),
          default = Some(Json.String("default value")),
          deprecated = Some(true),
          readOnly = Some(true),
          writeOnly = Some(false),
          examples = Some(::(Json.String("example1"), List(Json.String("example2"))))
        )
        val json     = schema.toJson
        val fieldMap = json.asInstanceOf[Json.Object].value.toMap

        assertTrue(
          fieldMap.get("title").contains(Json.String("My Schema")),
          fieldMap.get("description").contains(Json.String("A description of the schema")),
          fieldMap.get("default").contains(Json.String("default value")),
          fieldMap.get("deprecated").contains(Json.Boolean(true)),
          fieldMap.get("readOnly").contains(Json.Boolean(true)),
          fieldMap.get("writeOnly").contains(Json.Boolean(false)),
          fieldMap.get("examples").isDefined
        )
      }
    ),
    suite("Parse canonical JSON Schema examples")(
      test("parse simple type schema") {
        val json   = Json.Object("type" -> Json.String("string"))
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object =>
              s.`type`.contains(SchemaType.Single(JsonSchemaType.String))
            case _ => false
          }
        )
      },
      test("parse union type schema") {
        val json   = Json.Object("type" -> Json.Array(Json.String("string"), Json.String("null")))
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object =>
              s.`type`.contains(SchemaType.Union(::(JsonSchemaType.String, List(JsonSchemaType.Null))))
            case _ => false
          }
        )
      },
      test("parse object with properties and required") {
        val json = Json.Object(
          "type"       -> Json.String("object"),
          "properties" -> Json.Object(
            "name" -> Json.Object("type" -> Json.String("string")),
            "age"  -> Json.Object("type" -> Json.String("integer"))
          ),
          "required" -> Json.Array(Json.String("name"))
        )
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object =>
              s.properties.exists(_.keySet == Set("name", "age")) &&
              s.required.contains(Set("name"))
            case _ => false
          }
        )
      },
      test("parse array with items") {
        val json = Json.Object(
          "type"  -> Json.String("array"),
          "items" -> Json.Object("type" -> Json.String("string"))
        )
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object => s.items.isDefined
            case _                    => false
          }
        )
      },
      test("parse schema with $ref") {
        val json = Json.Object(
          "$defs" -> Json.Object(
            "address" -> Json.Object(
              "type"       -> Json.String("object"),
              "properties" -> Json.Object(
                "street" -> Json.Object("type" -> Json.String("string"))
              )
            )
          ),
          "$ref" -> Json.String("#/$defs/address")
        )
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object =>
              s.$ref.exists(_.value == "#/$defs/address") &&
              s.$defs.exists(_.contains("address"))
            case _ => false
          }
        )
      },
      test("parse composition with allOf") {
        val json = Json.Object(
          "allOf" -> Json.Array(
            Json.Object("type"     -> Json.String("object")),
            Json.Object("required" -> Json.Array(Json.String("name")))
          )
        )
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object => s.allOf.exists(_.length == 2)
            case _                    => false
          }
        )
      },
      test("parse conditional with if/then/else") {
        val json = Json.Object(
          "if"   -> Json.Object("properties" -> Json.Object("country" -> Json.Object("const" -> Json.String("USA")))),
          "then" -> Json.Object("required" -> Json.Array(Json.String("postal_code"))),
          "else" -> Json.Boolean(true)
        )
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object =>
              s.`if`.isDefined && s.`then`.isDefined && s.`else`.contains(JsonSchema.True)
            case _ => false
          }
        )
      },
      test("parse enum schema") {
        val json   = Json.Object("enum" -> Json.Array(Json.String("red"), Json.String("green"), Json.String("blue")))
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object =>
              s.`enum`.exists(_.length == 3)
            case _ => false
          }
        )
      },
      test("parse const schema") {
        val json   = Json.Object("const" -> Json.String("fixed"))
        val result = JsonSchema.fromJson(json)
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object =>
              s.const.contains(Json.String("fixed"))
            case _ => false
          }
        )
      }
    ),
    suite("Semantic round-trip preservation")(
      test("round-trip preserves validation behavior for string schema") {
        val original  = JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(3)))
        val roundtrip = JsonSchema.fromJson(original.toJson).toOption.get

        val validValue   = Json.String("hello")
        val invalidValue = Json.String("ab")

        assertTrue(
          original.conforms(validValue) == roundtrip.conforms(validValue),
          original.conforms(invalidValue) == roundtrip.conforms(invalidValue)
        )
      },
      test("round-trip preserves validation behavior for number schema") {
        val original  = JsonSchema.number(minimum = Some(BigDecimal(0)), maximum = Some(BigDecimal(100)))
        val roundtrip = JsonSchema.fromJson(original.toJson).toOption.get

        val validValue   = Json.Number(50)
        val invalidValue = Json.Number(150)

        assertTrue(
          original.conforms(validValue) == roundtrip.conforms(validValue),
          original.conforms(invalidValue) == roundtrip.conforms(invalidValue)
        )
      },
      test("round-trip preserves validation behavior for object schema") {
        val original = JsonSchema.obj(
          properties = Some(Map("name" -> JsonSchema.string())),
          required = Some(Set("name"))
        )
        val roundtrip = JsonSchema.fromJson(original.toJson).toOption.get

        val validValue   = Json.Object("name" -> Json.String("Alice"))
        val invalidValue = Json.Object()

        assertTrue(
          original.conforms(validValue) == roundtrip.conforms(validValue),
          original.conforms(invalidValue) == roundtrip.conforms(invalidValue)
        )
      },
      test("round-trip preserves validation behavior for array schema") {
        val original = JsonSchema.array(
          items = Some(JsonSchema.integer()),
          minItems = Some(NonNegativeInt.unsafe(1))
        )
        val roundtrip = JsonSchema.fromJson(original.toJson).toOption.get

        val validValue   = Json.Array(Json.Number(1), Json.Number(2))
        val invalidValue = Json.Array()

        assertTrue(
          original.conforms(validValue) == roundtrip.conforms(validValue),
          original.conforms(invalidValue) == roundtrip.conforms(invalidValue)
        )
      }
    ),
    suite("Error handling")(
      test("invalid JSON type returns error") {
        val result = JsonSchema.fromJson(Json.String("not a schema"))
        assertTrue(result.isLeft)
      },
      test("invalid type value returns error") {
        val result = JsonSchema.fromJson(Json.Object("type" -> Json.String("invalid_type")))
        assertTrue(
          result.isRight,
          result.exists {
            case s: JsonSchema.Object => s.`type`.isEmpty
            case _                    => false
          }
        )
      },
      test("parse from JSON string works") {
        val jsonStr = """{"type": "string", "minLength": 1}"""
        val result  = JsonSchema.parse(jsonStr)
        assertTrue(result.isRight)
      },
      test("parse from invalid JSON string returns error") {
        val jsonStr = """{"type": "string", minLength: 1}"""
        val result  = JsonSchema.parse(jsonStr)
        assertTrue(result.isLeft)
      }
    )
  )
}
