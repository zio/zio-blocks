package zio.blocks.schema.json

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema._
import zio.test._

import java.net.URI

/**
 * Tests for JSON Schema helper types to ensure error paths are covered.
 */
object JsonSchemaHelperTypesSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaHelperTypesSpec")(
    suite("NonNegativeInt")(
      test("apply returns Some for non-negative integers") {
        assertTrue(
          NonNegativeInt(0).isDefined,
          NonNegativeInt(1).isDefined,
          NonNegativeInt(100).isDefined,
          NonNegativeInt(Int.MaxValue).isDefined
        )
      },
      test("apply returns None for negative integers") {
        assertTrue(
          NonNegativeInt(-1).isEmpty,
          NonNegativeInt(-100).isEmpty,
          NonNegativeInt(Int.MinValue).isEmpty
        )
      },
      test("unsafe throws for negative integers") {
        val thrown = try {
          NonNegativeInt.unsafe(-1)
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Throwable                => false
        }
        assertTrue(thrown)
      },
      test("unsafe returns value for non-negative integers") {
        assertTrue(
          NonNegativeInt.unsafe(0).value == 0,
          NonNegativeInt.unsafe(42).value == 42
        )
      },
      test("zero and one constants exist") {
        assertTrue(
          NonNegativeInt.zero.value == 0,
          NonNegativeInt.one.value == 1
        )
      }
    ),
    suite("PositiveNumber")(
      test("apply returns Some for positive numbers") {
        assertTrue(
          PositiveNumber(BigDecimal(1)).isDefined,
          PositiveNumber(BigDecimal(0.001)).isDefined,
          PositiveNumber(BigDecimal(100)).isDefined
        )
      },
      test("apply returns None for zero and negative numbers") {
        assertTrue(
          PositiveNumber(BigDecimal(0)).isEmpty,
          PositiveNumber(BigDecimal(-1)).isEmpty,
          PositiveNumber(BigDecimal(-0.001)).isEmpty
        )
      },
      test("unsafe throws for zero") {
        val thrown = try {
          PositiveNumber.unsafe(BigDecimal(0))
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Throwable                => false
        }
        assertTrue(thrown)
      },
      test("unsafe throws for negative numbers") {
        val thrown = try {
          PositiveNumber.unsafe(BigDecimal(-1))
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Throwable                => false
        }
        assertTrue(thrown)
      },
      test("unsafe returns value for positive numbers") {
        assertTrue(
          PositiveNumber.unsafe(BigDecimal(5)).value == BigDecimal(5),
          PositiveNumber.unsafe(BigDecimal(0.5)).value == BigDecimal(0.5)
        )
      },
      test("fromInt returns Some for positive integers") {
        assertTrue(
          PositiveNumber.fromInt(1).isDefined,
          PositiveNumber.fromInt(100).isDefined
        )
      },
      test("fromInt returns None for zero and negative integers") {
        assertTrue(
          PositiveNumber.fromInt(0).isEmpty,
          PositiveNumber.fromInt(-1).isEmpty
        )
      }
    ),
    suite("RegexPattern")(
      test("apply returns Right for valid patterns") {
        assertTrue(
          RegexPattern("^[a-z]+$").isRight,
          RegexPattern("\\d{3}-\\d{4}").isRight,
          RegexPattern(".*").isRight,
          RegexPattern("").isRight
        )
      },
      test("apply returns Left for invalid patterns") {
        assertTrue(
          RegexPattern("[invalid").isLeft,
          RegexPattern("(unclosed").isLeft,
          RegexPattern("*invalid").isLeft
        )
      },
      test("apply error message contains pattern info") {
        val result = RegexPattern("[invalid")
        assertTrue(
          result.isLeft,
          result.left.exists(_.nonEmpty)
        )
      },
      test("unsafe creates pattern without validation") {
        val pattern = RegexPattern.unsafe("[a-z]+")
        assertTrue(pattern.value == "[a-z]+")
      },
      test("compiled returns Right for valid patterns") {
        val pattern = RegexPattern.unsafe("^[a-z]+$")
        val result  = pattern.compiled
        assertTrue(
          result.isRight,
          result.exists(_.pattern() == "^[a-z]+$")
        )
      },
      test("compiled returns Left for invalid patterns created with unsafe") {
        val pattern = RegexPattern.unsafe("[invalid")
        val result  = pattern.compiled
        assertTrue(result.isLeft)
      }
    ),
    suite("UriReference")(
      test("apply creates UriReference") {
        val ref = UriReference("/path/to/resource")
        assertTrue(ref.value == "/path/to/resource")
      },
      test("resolve succeeds for valid references") {
        val base   = new URI("https://example.com/base/")
        val ref    = UriReference("resource")
        val result = ref.resolve(base)
        assertTrue(
          result.isRight,
          result.exists(_.toString == "https://example.com/base/resource")
        )
      },
      test("resolve handles absolute URIs") {
        val base   = new URI("https://example.com/base/")
        val ref    = UriReference("https://other.com/path")
        val result = ref.resolve(base)
        assertTrue(
          result.isRight,
          result.exists(_.toString == "https://other.com/path")
        )
      },
      test("resolve handles fragment references") {
        val base   = new URI("https://example.com/doc")
        val ref    = UriReference("#section")
        val result = ref.resolve(base)
        assertTrue(
          result.isRight,
          result.exists(_.toString == "https://example.com/doc#section")
        )
      }
    ),
    suite("Anchor")(
      test("apply creates Anchor") {
        val anchor = Anchor("my-anchor")
        assertTrue(anchor.value == "my-anchor")
      }
    ),
    suite("JsonSchemaType")(
      test("all JSON types have correct fromString") {
        assertTrue(
          JsonSchemaType.fromString("string") == Some(JsonSchemaType.String),
          JsonSchemaType.fromString("number") == Some(JsonSchemaType.Number),
          JsonSchemaType.fromString("integer") == Some(JsonSchemaType.Integer),
          JsonSchemaType.fromString("boolean") == Some(JsonSchemaType.Boolean),
          JsonSchemaType.fromString("null") == Some(JsonSchemaType.Null),
          JsonSchemaType.fromString("array") == Some(JsonSchemaType.Array),
          JsonSchemaType.fromString("object") == Some(JsonSchemaType.Object)
        )
      },
      test("fromString returns None for unknown types") {
        assertTrue(
          JsonSchemaType.fromString("unknown").isEmpty,
          JsonSchemaType.fromString("").isEmpty,
          JsonSchemaType.fromString("STRING").isEmpty
        )
      },
      test("all JSON types have correct toJsonString") {
        assertTrue(
          JsonSchemaType.String.toJsonString == "string",
          JsonSchemaType.Number.toJsonString == "number",
          JsonSchemaType.Integer.toJsonString == "integer",
          JsonSchemaType.Boolean.toJsonString == "boolean",
          JsonSchemaType.Null.toJsonString == "null",
          JsonSchemaType.Array.toJsonString == "array",
          JsonSchemaType.Object.toJsonString == "object"
        )
      },
      test("all constants list") {
        assertTrue(JsonSchemaType.all.size == 7)
      }
    ),
    suite("SchemaType")(
      test("Single contains checks type equality") {
        val st = SchemaType.Single(JsonSchemaType.String)
        assertTrue(
          st.contains(JsonSchemaType.String),
          !st.contains(JsonSchemaType.Number)
        )
      },
      test("Union contains checks type membership") {
        val st = SchemaType.Union(::(JsonSchemaType.String, List(JsonSchemaType.Number)))
        assertTrue(
          st.contains(JsonSchemaType.String),
          st.contains(JsonSchemaType.Number),
          !st.contains(JsonSchemaType.Boolean)
        )
      },
      test("Single toJson produces string") {
        val st = SchemaType.Single(JsonSchemaType.String)
        assertTrue(st.toJson == Json.String("string"))
      },
      test("Union toJson produces array") {
        val st = SchemaType.Union(::(JsonSchemaType.String, List(JsonSchemaType.Number)))
        assertTrue(st.toJson == Json.Array(Json.String("string"), Json.String("number")))
      },
      test("fromJson parses single type") {
        val result = SchemaType.fromJson(Json.String("string"))
        assertTrue(result == Right(SchemaType.Single(JsonSchemaType.String)))
      },
      test("fromJson parses type array") {
        val result = SchemaType.fromJson(Json.Array(Json.String("string"), Json.String("number")))
        assertTrue(result == Right(SchemaType.Union(::(JsonSchemaType.String, List(JsonSchemaType.Number)))))
      },
      test("fromJson returns error for unknown type") {
        val result = SchemaType.fromJson(Json.String("unknown"))
        assertTrue(result.isLeft)
      },
      test("fromJson returns error for non-string in array") {
        val result = SchemaType.fromJson(Json.Array(Json.Number(42)))
        assertTrue(result.isLeft)
      },
      test("fromJson returns error for empty array") {
        val result = SchemaType.fromJson(Json.Array())
        assertTrue(result.isLeft)
      }
    ),
    suite("EvaluationResult")(
      test("empty has no errors") {
        val result = EvaluationResult.empty
        assertTrue(
          result.errors.isEmpty,
          result.evaluatedProperties.isEmpty,
          result.evaluatedItems.isEmpty
        )
      },
      test("fromError creates result with single error") {
        val result = EvaluationResult.fromError(Nil, "test error")
        assertTrue(result.errors.nonEmpty)
      },
      test("++ combines two results") {
        val r1       = EvaluationResult.empty.withEvaluatedProperty("a")
        val r2       = EvaluationResult.empty.withEvaluatedProperty("b")
        val combined = r1 ++ r2
        assertTrue(combined.evaluatedProperties == Set("a", "b"))
      },
      test("addError appends error") {
        val result = EvaluationResult.empty.addError(Nil, "error message")
        assertTrue(result.errors.nonEmpty)
      },
      test("withEvaluatedProperty adds property") {
        val result = EvaluationResult.empty.withEvaluatedProperty("prop")
        assertTrue(result.evaluatedProperties.contains("prop"))
      },
      test("withEvaluatedProperties adds multiple properties") {
        val result = EvaluationResult.empty.withEvaluatedProperties(Set("a", "b", "c"))
        assertTrue(result.evaluatedProperties == Set("a", "b", "c"))
      },
      test("withEvaluatedItem adds item index") {
        val result = EvaluationResult.empty.withEvaluatedItem(5)
        assertTrue(result.evaluatedItems.contains(5))
      },
      test("withEvaluatedItems adds multiple item indices") {
        val result = EvaluationResult.empty.withEvaluatedItems(Set(0, 1, 2))
        assertTrue(result.evaluatedItems == Set(0, 1, 2))
      },
      test("toSchemaError returns None when no errors") {
        val result = EvaluationResult.empty
        assertTrue(result.toSchemaError.isEmpty)
      },
      test("toSchemaError returns Some when errors exist") {
        val result = EvaluationResult.fromError(Nil, "error")
        assertTrue(result.toSchemaError.isDefined)
      },
      test("addErrors appends multiple errors") {
        val errors = List(
          SchemaError.expectationMismatch(Nil, "error1").errors.head,
          SchemaError.expectationMismatch(Nil, "error2").errors.head
        )
        val result = EvaluationResult.empty.addErrors(errors)
        assertTrue(result.errors.size == 2)
      }
    ),
    suite("ValidationOptions")(
      test("default options exist") {
        assertTrue(
          ValidationOptions.default.validateFormats == true,
          ValidationOptions.annotationOnly.validateFormats == false,
          ValidationOptions.formatAssertion.validateFormats == true
        )
      }
    ),
    suite("JsonSchema factory methods")(
      test("enumOfStrings creates enum of string values") {
        val schema = JsonSchema.enumOfStrings(::("red", List("green", "blue")))
        assertTrue(
          schema.conforms(Json.String("red")),
          schema.conforms(Json.String("green")),
          schema.conforms(Json.String("blue")),
          !schema.conforms(Json.String("yellow"))
        )
      },
      test("ref creates $ref schema") {
        val schema = JsonSchema.ref(UriReference("#/$defs/myType"))
        schema match {
          case s: JsonSchema.Object =>
            assertTrue(s.$ref.isDefined, s.$ref.get.value == "#/$defs/myType")
          case _ => assertTrue(false)
        }
      },
      test("refString creates $ref schema from string") {
        val schema = JsonSchema.refString("#/$defs/anotherType")
        schema match {
          case s: JsonSchema.Object =>
            assertTrue(s.$ref.isDefined, s.$ref.get.value == "#/$defs/anotherType")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("JsonSchema operator edge cases")(
      test("False && anything returns False") {
        val schema = JsonSchema.False && JsonSchema.string()
        assertTrue(schema == JsonSchema.False)
      },
      test("True || anything returns True") {
        val schema = JsonSchema.True || JsonSchema.string()
        assertTrue(schema == JsonSchema.True)
      },
      test("!True returns False (rejects everything)") {
        val schema = !JsonSchema.True
        assertTrue(
          schema == JsonSchema.False,
          !schema.conforms(Json.Null),
          !schema.conforms(Json.String("hello")),
          !schema.conforms(Json.Number(42))
        )
      },
      test("!False returns True") {
        val schema = !JsonSchema.False
        assertTrue(schema == JsonSchema.True)
      }
    ),
    suite("Format validation edge cases")(
      test("date format rejects malformed date") {
        val schema = JsonSchema.string(format = Some("date"))
        assertTrue(!schema.conforms(Json.String("not-a-date")))
      },
      test("time format validates time strings") {
        val schema = JsonSchema.string(format = Some("time"))
        assertTrue(
          schema.conforms(Json.String("12:30:45Z")),
          !schema.conforms(Json.String("not-a-time"))
        )
      },
      test("uri format rejects malformed URIs") {
        val schema = JsonSchema.string(format = Some("uri"))
        assertTrue(
          schema.conforms(Json.String("https://example.com")),
          !schema.conforms(Json.String("not a valid uri with spaces"))
        )
      },
      test("uri-reference format handles edge cases") {
        val schema = JsonSchema.string(format = Some("uri-reference"))
        assertTrue(
          schema.conforms(Json.String("/path/to/resource")),
          schema.conforms(Json.String("../relative")),
          schema.conforms(Json.String("#fragment"))
        )
      }
    ),
    suite("SchemaType.fromJson edge cases")(
      test("fromJson with unexpected type returns error") {
        val result = SchemaType.fromJson(Json.Null)
        assertTrue(result.isLeft)
      },
      test("fromJson with object returns error") {
        val result = SchemaType.fromJson(Json.Object())
        assertTrue(result.isLeft)
      }
    ),
    suite("JsonSchema.fromJson edge cases")(
      test("fromJson with invalid allOf field returns error") {
        val json   = Json.Object("allOf" -> Json.String("not-an-array"))
        val result = JsonSchema.fromJson(json)
        assertTrue(result.isLeft)
      },
      test("fromJson with invalid properties field returns error") {
        val json   = Json.Object("properties" -> Json.String("not-an-object"))
        val result = JsonSchema.fromJson(json)
        assertTrue(result.isLeft)
      }
    ),
    suite("maxContains validation")(
      test("maxContains limits matching items") {
        val schema = JsonSchema.array(
          contains = Some(JsonSchema.string()),
          maxContains = Some(NonNegativeInt.unsafe(2))
        )
        assertTrue(
          schema.conforms(Json.Array(Json.String("a"), Json.Number(1))),
          schema.conforms(Json.Array(Json.String("a"), Json.String("b"), Json.Number(1))),
          !schema.conforms(Json.Array(Json.String("a"), Json.String("b"), Json.String("c")))
        )
      }
    ),
    suite("dependentSchemas validation")(
      test("dependentSchemas applies schema when property is present") {
        val schema = JsonSchema.Object(
          dependentSchemas = Some(
            ChunkMap(
              "credit_card" -> JsonSchema.obj(
                properties = Some(ChunkMap("billing_address" -> JsonSchema.string())),
                required = Some(Set("billing_address"))
              )
            )
          )
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
    suite("JsonSchema toString")(
      test("toString produces formatted JSON") {
        val schema = JsonSchema.string(minLength = Some(NonNegativeInt.one))
        val str    = schema.toString
        assertTrue(
          str.contains("\"type\""),
          str.contains("\"string\""),
          str.contains("\"minLength\"")
        )
      },
      test("toString matches toJson.print with indentation") {
        val schema   = JsonSchema.string()
        val expected = schema.toJson.print(WriterConfig.withIndentionStep(2))
        assertTrue(schema.toString == expected)
      },
      test("True.toString returns true") {
        assertTrue(JsonSchema.True.toString == "true")
      },
      test("False.toString returns false") {
        assertTrue(JsonSchema.False.toString == "false")
      }
    )
  )
}
