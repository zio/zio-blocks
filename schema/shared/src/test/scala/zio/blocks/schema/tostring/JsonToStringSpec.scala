package zio.blocks.schema.tostring

import zio.blocks.schema.json.{Json, JsonType, WriterConfig}
import zio.test._

object JsonToStringSpec extends ZIOSpecDefault {
  def spec = suite("JsonToStringSpec")(
    suite("Primitives")(
      test("renders boolean true") {
        assertTrue(Json.True.toString == "true")
      },
      test("renders boolean false") {
        assertTrue(Json.False.toString == "false")
      },
      test("renders null") {
        assertTrue(Json.Null.toString == "null")
      },
      test("renders integer number") {
        assertTrue(Json.Number(42).toString == "42")
      },
      test("renders decimal number") {
        assertTrue(Json.Number(42.5).toString == "42.5")
      },
      test("renders negative number") {
        assertTrue(Json.Number(-123).toString == "-123")
      },
      test("renders zero") {
        assertTrue(Json.Number(0).toString == "0")
      },
      test("renders simple string") {
        assertTrue(Json.String("hello").toString == "\"hello\"")
      },
      test("renders empty string") {
        assertTrue(Json.String("").toString == "\"\"")
      },
      test("renders string with double quotes") {
        assertTrue(Json.String("hello \"world\"").toString == "\"hello \\\"world\\\"\"")
      },
      test("renders string with backslash") {
        assertTrue(Json.String("path\\to\\file").toString == "\"path\\\\to\\\\file\"")
      },
      test("renders string with newline") {
        assertTrue(Json.String("line1\nline2").toString == "\"line1\\nline2\"")
      },
      test("renders string with tab") {
        assertTrue(Json.String("col1\tcol2").toString == "\"col1\\tcol2\"")
      }
    ),
    suite("Arrays")(
      test("renders empty array") {
        val actual = Json.Array().toString
        assertTrue(actual.startsWith("[") && actual.endsWith("]") && actual.contains("\n"))
      },
      test("renders array with single element") {
        assertTrue(Json.Array(Json.Number(1)).toString == "[\n  1\n]")
      },
      test("renders array with multiple primitives") {
        val json = Json.Array(Json.Number(1), Json.String("a"), Json.True, Json.Null)
        assertTrue(json.toString == "[\n  1,\n  \"a\",\n  true,\n  null\n]")
      },
      test("renders nested array") {
        val json = Json.Array(Json.Array(Json.Number(1), Json.Number(2)), Json.Number(3))
        assertTrue(json.toString == "[\n  [\n    1,\n    2\n  ],\n  3\n]")
      },
      test("renders deeply nested array") {
        val json = Json.Array(Json.Array(Json.Array(Json.Number(1))))
        assertTrue(json.toString == "[\n  [\n    [\n      1\n    ]\n  ]\n]")
      },
      test("renders array with mixed types") {
        val json = Json.Array(
          Json.Number(1),
          Json.String("text"),
          Json.Object("key" -> Json.True),
          Json.Array(Json.Number(2))
        )
        assertTrue(json.toString == "[\n  1,\n  \"text\",\n  {\n    \"key\": true\n  },\n  [\n    2\n  ]\n]")
      }
    ),
    suite("Objects")(
      test("renders empty object") {
        val actual = Json.Object().toString
        assertTrue(actual.startsWith("{") && actual.endsWith("}") && actual.contains("\n"))
      },
      test("renders object with single field") {
        val json = Json.Object("name" -> Json.String("Alice"))
        assertTrue(json.toString == "{\n  \"name\": \"Alice\"\n}")
      },
      test("renders object with multiple fields") {
        val json = Json.Object(
          "name"   -> Json.String("Alice"),
          "age"    -> Json.Number(30),
          "active" -> Json.True
        )
        assertTrue(json.toString == "{\n  \"name\": \"Alice\",\n  \"age\": 30,\n  \"active\": true\n}")
      },
      test("renders nested object") {
        val json = Json.Object(
          "user" -> Json.Object(
            "name" -> Json.String("Alice"),
            "age"  -> Json.Number(30)
          )
        )
        assertTrue(json.toString == "{\n  \"user\": {\n    \"name\": \"Alice\",\n    \"age\": 30\n  }\n}")
      },
      test("renders deeply nested object") {
        val json = Json.Object(
          "level1" -> Json.Object(
            "level2" -> Json.Object(
              "level3" -> Json.String("deep")
            )
          )
        )
        assertTrue(json.toString == "{\n  \"level1\": {\n    \"level2\": {\n      \"level3\": \"deep\"\n    }\n  }\n}")
      },
      test("renders object with array field") {
        val json = Json.Object(
          "items" -> Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
        )
        assertTrue(json.toString == "{\n  \"items\": [\n    1,\n    2,\n    3\n  ]\n}")
      },
      test("renders object with null field") {
        val json = Json.Object("value" -> Json.Null)
        assertTrue(json.toString == "{\n  \"value\": null\n}")
      }
    ),
    suite("Complex Structures")(
      test("renders array of objects") {
        val json = Json.Array(
          Json.Object("id" -> Json.Number(1), "name" -> Json.String("Alice")),
          Json.Object("id" -> Json.Number(2), "name" -> Json.String("Bob"))
        )
        assertTrue(
          json.toString == "[\n  {\n    \"id\": 1,\n    \"name\": \"Alice\"\n  },\n  {\n    \"id\": 2,\n    \"name\": \"Bob\"\n  }\n]"
        )
      },
      test("renders object with mixed nested structures") {
        val json = Json.Object(
          "user" -> Json.Object(
            "name"   -> Json.String("Alice"),
            "scores" -> Json.Array(Json.Number(95), Json.Number(87), Json.Number(92))
          ),
          "tags" -> Json.Array(Json.String("verified"), Json.String("premium"))
        )
        assertTrue(
          json.toString ==
            "{\n  \"user\": {\n    \"name\": \"Alice\",\n    \"scores\": [\n      95,\n      87,\n      92\n    ]\n  },\n  \"tags\": [\n    \"verified\",\n    \"premium\"\n  ]\n}"
        )
      }
    ),
    suite("Edge Cases")(
      test("renders very large number") {
        val json = Json.Number(BigDecimal("123456789012345678901234567890"))
        assertTrue(json.toString == "123456789012345678901234567890")
      },
      test("renders very small decimal") {
        val json = Json.Number(BigDecimal("0.000000000001"))
        assertTrue(json.toString.contains("1E-12"))
      },
      test("renders scientific notation") {
        val json = Json.Number(1.23e10)
        // Exact representation may vary, just verify it parses back
        assertTrue(Json.parse(json.toString).isRight)
      },
      test("renders unicode string") {
        val json   = Json.String("Hello 世界 🌍")
        val parsed = Json.parse(json.toString)
        assertTrue(parsed.map(_.unwrap(JsonType.String)) == Right(Some("Hello 世界 🌍")))
      }
    ),
    suite("Roundtrip")(
      test("roundtrips primitives") {
        check(
          Gen.oneOf(
            Gen.const(Json.True),
            Gen.const(Json.False),
            Gen.const(Json.Null),
            Gen.int.map(Json.Number(_)),
            Gen.double.filter(!_.isNaN).filter(!_.isInfinity).map(Json.Number(_)),
            Gen.alphaNumericString.map(Json.String(_))
          )
        ) { json =>
          assertTrue(Json.parse(json.toString) == Right(json))
        }
      },
      test("roundtrips simple arrays") {
        check(Gen.listOf(Gen.int).map(l => Json.Array(l.map(Json.Number(_)): _*))) { json =>
          assertTrue(Json.parse(json.toString) == Right(json))
        }
      },
      test("roundtrips simple objects") {
        check(
          Gen
            .listOf(
              for {
                key   <- Gen.alphaNumericStringBounded(1, 10)
                value <- Gen.int
              } yield (key, value)
            )
            .map { pairs =>
              Json.Object(pairs.map { case (k, v) => k -> Json.Number(v) }: _*)
            }
        ) { json =>
          assertTrue(Json.parse(json.toString) == Right(json))
        }
      },
      test("roundtrips nested structures") {
        check(
          for {
            name <- Gen.alphaNumericStringBounded(1, 10)
            age  <- Gen.int(1, 100)
            tags <- Gen.listOfBounded(1, 5)(Gen.alphaNumericStringBounded(1, 10))
          } yield Json.Object(
            "name" -> Json.String(name),
            "age"  -> Json.Number(age),
            "tags" -> Json.Array(tags.map(Json.String(_)): _*)
          )
        ) { json =>
          assertTrue(Json.parse(json.toString) == Right(json))
        }
      }
    ),
    suite("toString uses pretty printing")(
      test("toString produces pretty-printed output") {
        val json = Json.Object(
          "name"   -> Json.String("test"),
          "value"  -> Json.Number(42),
          "nested" -> Json.Array(Json.True, Json.False, Json.Null)
        )
        // toString should use 2-space indentation
        assertTrue(json.toString == json.print(WriterConfig.withIndentionStep(2)))
      },
      test("all Json types use pretty printing in toString") {
        val cases = List(
          Json.True,
          Json.False,
          Json.Null,
          Json.Number(42),
          Json.String("test"),
          Json.Array(Json.Number(1)),
          Json.Object("key" -> Json.String("value"))
        )
        // All should use pretty printing with 2-space indentation
        assertTrue(cases.forall(j => j.toString == j.print(WriterConfig.withIndentionStep(2))))
      },
      test("print without args still uses compact format") {
        val json = Json.Object("name" -> Json.String("test"))
        // print() without args should be compact
        assertTrue(json.print == "{\"name\":\"test\"}")
        // toString should be pretty-printed
        assertTrue(json.toString == "{\n  \"name\": \"test\"\n}")
      }
    )
  )
}
