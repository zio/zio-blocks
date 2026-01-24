package zio.blocks.schema.tostring

import zio.blocks.schema.json.{Json, WriterConfig}
import zio.test._

object JsonToStringSpec extends ZIOSpecDefault {
  def spec = suite("Json toString")(
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
        assertTrue(Json.number(42).toString == "42")
      },
      test("renders decimal number") {
        assertTrue(Json.number(42.5).toString == "42.5")
      },
      test("renders negative number") {
        assertTrue(Json.number(-123).toString == "-123")
      },
      test("renders zero") {
        assertTrue(Json.number(0).toString == "0")
      },
      test("renders simple string") {
        assertTrue(Json.str("hello").toString == "\"hello\"")
      },
      test("renders empty string") {
        assertTrue(Json.str("").toString == "\"\"")
      },
      test("renders string with double quotes") {
        assertTrue(Json.str("hello \"world\"").toString == "\"hello \\\"world\\\"\"")
      },
      test("renders string with backslash") {
        assertTrue(Json.str("path\\to\\file").toString == "\"path\\\\to\\\\file\"")
      },
      test("renders string with newline") {
        assertTrue(Json.str("line1\nline2").toString == "\"line1\\nline2\"")
      },
      test("renders string with tab") {
        assertTrue(Json.str("col1\tcol2").toString == "\"col1\\tcol2\"")
      }
    ),
    suite("Arrays")(
      test("renders empty array") {
        val actual = Json.arr().toString
        assertTrue(actual.startsWith("[") && actual.endsWith("]") && actual.contains("\n"))
      },
      test("renders array with single element") {
        assertTrue(Json.arr(Json.number(1)).toString == "[\n  1\n]")
      },
      test("renders array with multiple primitives") {
        val json = Json.arr(Json.number(1), Json.str("a"), Json.True, Json.Null)
        assertTrue(json.toString == "[\n  1,\n  \"a\",\n  true,\n  null\n]")
      },
      test("renders nested array") {
        val json = Json.arr(Json.arr(Json.number(1), Json.number(2)), Json.number(3))
        assertTrue(json.toString == "[\n  [\n    1,\n    2\n  ],\n  3\n]")
      },
      test("renders deeply nested array") {
        val json = Json.arr(Json.arr(Json.arr(Json.number(1))))
        assertTrue(json.toString == "[\n  [\n    [\n      1\n    ]\n  ]\n]")
      },
      test("renders array with mixed types") {
        val json = Json.arr(
          Json.number(1),
          Json.str("text"),
          Json.obj("key" -> Json.True),
          Json.arr(Json.number(2))
        )
        assertTrue(json.toString == "[\n  1,\n  \"text\",\n  {\n    \"key\": true\n  },\n  [\n    2\n  ]\n]")
      }
    ),
    suite("Objects")(
      test("renders empty object") {
        val actual = Json.obj().toString
        assertTrue(actual.startsWith("{") && actual.endsWith("}") && actual.contains("\n"))
      },
      test("renders object with single field") {
        val json = Json.obj("name" -> Json.str("Alice"))
        assertTrue(json.toString == "{\n  \"name\": \"Alice\"\n}")
      },
      test("renders object with multiple fields") {
        val json = Json.obj(
          "name"   -> Json.str("Alice"),
          "age"    -> Json.number(30),
          "active" -> Json.True
        )
        assertTrue(json.toString == "{\n  \"name\": \"Alice\",\n  \"age\": 30,\n  \"active\": true\n}")
      },
      test("renders nested object") {
        val json = Json.obj(
          "user" -> Json.obj(
            "name" -> Json.str("Alice"),
            "age"  -> Json.number(30)
          )
        )
        assertTrue(json.toString == "{\n  \"user\": {\n    \"name\": \"Alice\",\n    \"age\": 30\n  }\n}")
      },
      test("renders deeply nested object") {
        val json = Json.obj(
          "level1" -> Json.obj(
            "level2" -> Json.obj(
              "level3" -> Json.str("deep")
            )
          )
        )
        assertTrue(json.toString == "{\n  \"level1\": {\n    \"level2\": {\n      \"level3\": \"deep\"\n    }\n  }\n}")
      },
      test("renders object with array field") {
        val json = Json.obj(
          "items" -> Json.arr(Json.number(1), Json.number(2), Json.number(3))
        )
        assertTrue(json.toString == "{\n  \"items\": [\n    1,\n    2,\n    3\n  ]\n}")
      },
      test("renders object with null field") {
        val json = Json.obj("value" -> Json.Null)
        assertTrue(json.toString == "{\n  \"value\": null\n}")
      }
    ),
    suite("Complex Structures")(
      test("renders array of objects") {
        val json = Json.arr(
          Json.obj("id" -> Json.number(1), "name" -> Json.str("Alice")),
          Json.obj("id" -> Json.number(2), "name" -> Json.str("Bob"))
        )
        assertTrue(
          json.toString == "[\n  {\n    \"id\": 1,\n    \"name\": \"Alice\"\n  },\n  {\n    \"id\": 2,\n    \"name\": \"Bob\"\n  }\n]"
        )
      },
      test("renders object with mixed nested structures") {
        val json = Json.obj(
          "user" -> Json.obj(
            "name"   -> Json.str("Alice"),
            "scores" -> Json.arr(Json.number(95), Json.number(87), Json.number(92))
          ),
          "tags" -> Json.arr(Json.str("verified"), Json.str("premium"))
        )
        assertTrue(
          json.toString ==
            "{\n  \"user\": {\n    \"name\": \"Alice\",\n    \"scores\": [\n      95,\n      87,\n      92\n    ]\n  },\n  \"tags\": [\n    \"verified\",\n    \"premium\"\n  ]\n}"
        )
      }
    ),
    suite("Edge Cases")(
      test("renders very large number") {
        val json = Json.number(BigDecimal("123456789012345678901234567890"))
        assertTrue(json.toString == "123456789012345678901234567890")
      },
      test("renders very small decimal") {
        val json = Json.number(BigDecimal("0.000000000001"))
        assertTrue(json.toString.contains("1E-12"))
      },
      test("renders scientific notation") {
        val json = Json.number(1.23e10)
        // Exact representation may vary, just verify it parses back
        assertTrue(Json.parse(json.toString).isRight)
      },
      test("renders unicode string") {
        val json   = Json.str("Hello 世界 🌍")
        val parsed = Json.parse(json.toString)
        assertTrue(parsed.map(_.stringValue) == Right(Some("Hello 世界 🌍")))
      }
    ),
    suite("Roundtrip")(
      test("roundtrips primitives") {
        check(
          Gen.oneOf(
            Gen.const(Json.True),
            Gen.const(Json.False),
            Gen.const(Json.Null),
            Gen.int.map(Json.number),
            Gen.double.filter(!_.isNaN).filter(!_.isInfinity).map(Json.number),
            Gen.alphaNumericString.map(Json.str)
          )
        ) { json =>
          assertTrue(Json.parse(json.toString) == Right(json))
        }
      },
      test("roundtrips simple arrays") {
        check(Gen.listOf(Gen.int).map(l => Json.arr(l.map(Json.number): _*))) { json =>
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
              Json.obj(pairs.map { case (k, v) => k -> Json.number(v) }: _*)
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
          } yield Json.obj(
            "name" -> Json.str(name),
            "age"  -> Json.number(age),
            "tags" -> Json.arr(tags.map(Json.str): _*)
          )
        ) { json =>
          assertTrue(Json.parse(json.toString) == Right(json))
        }
      }
    ),
    suite("toString uses pretty printing")(
      test("toString produces pretty-printed output") {
        val json = Json.obj(
          "name"   -> Json.str("test"),
          "value"  -> Json.number(42),
          "nested" -> Json.arr(Json.True, Json.False, Json.Null)
        )
        // toString should use 2-space indentation
        assertTrue(json.toString == json.print(WriterConfig.withIndentionStep(2)))
      },
      test("all Json types use pretty printing in toString") {
        val cases = List(
          Json.True,
          Json.False,
          Json.Null,
          Json.number(42),
          Json.str("test"),
          Json.arr(Json.number(1)),
          Json.obj("key" -> Json.str("value"))
        )
        // All should use pretty printing with 2-space indentation
        assertTrue(cases.forall(j => j.toString == j.print(WriterConfig.withIndentionStep(2))))
      },
      test("print without args still uses compact format") {
        val json = Json.obj("name" -> Json.str("test"))
        // print() without args should be compact
        assertTrue(json.print == "{\"name\":\"test\"}")
        // toString should be pretty-printed
        assertTrue(json.toString == "{\n  \"name\": \"test\"\n}")
      }
    )
  )
}
