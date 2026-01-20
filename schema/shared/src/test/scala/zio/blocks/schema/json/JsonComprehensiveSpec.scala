package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.json.interpolators._
import zio.blocks.schema.json.MergeStrategy._

object JsonComprehensiveSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonComprehensiveSpec")(
    // ===========================================================================
    // ADT Construction Tests
    // ===========================================================================
    suite("ADT Construction")(
      test("constructs all JSON types") {
        val obj  = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
        val arr  = Json.Array(Json.number(1), Json.number(2), Json.number(3))
        val str  = Json.String("hello")
        val num  = Json.number(42.5)
        val bool = Json.Boolean(true)
        val nul  = Json.Null

        assertTrue(
          obj.isObject && arr.isArray && str.isString &&
            num.isNumber && bool.isBoolean && nul.isNull
        )
      },

      test("pattern matching works") {
        val json   = Json.String("test")
        val result = json match {
          case Json.String(value) => value.toUpperCase
          case null               => "null value"
        }
        assert(result)(equalTo("TEST"))
      }
    ),

    // ===========================================================================
    // Navigation Tests
    // ===========================================================================
    suite("Navigation")(
      test("navigate with DynamicOptic") {
        val json = Json.Object(
          "user" -> Json.Object(
            "name" -> Json.String("Alice"),
            "tags" -> Json.Array(Json.String("scala"), Json.String("zio"))
          )
        )

        val nameSelection = json.get(p"user.name")
        assert(nameSelection.one)(isRight(equalTo(Json.String("Alice"))))
      },

      test("navigate to array elements") {
        val json   = Json.Object("items" -> Json.Array(Json.number(1), Json.number(2), Json.number(3)))
        val second = json.get(p"items[1]")
        assert(second.one)(isRight(equalTo(Json.number(2))))
      },

      test("navigate to all array elements") {
        val json = Json.Object("items" -> Json.Array(Json.number(1), Json.number(2)))
        val all  = json.get(p"items[*]")
        assert(all.toEither)(isRight(hasSize(equalTo(2))))
      },

      test("chained navigation") {
        val json = Json.Object(
          "users" -> Json.Array(
            Json.Object("name" -> Json.String("Alice")),
            Json.Object("name" -> Json.String("Bob"))
          )
        )

        val names = json.get(p"users[*].name")
        assert(names.toEither)(isRight(equalTo(Vector(Json.String("Alice"), Json.String("Bob")))))
      }
    ),

    // ===========================================================================
    // Modification Tests
    // ===========================================================================
    suite("Modification")(
      test("modify values") {
        val json     = Json.Object("count" -> Json.number(1))
        val modified = json.modify(
          p"count",
          {
            case Json.Number(n) => Json.number(n.toInt + 1)
            case other          => other
          }
        )
        assert(modified.get(p"count").one)(isRight(equalTo(Json.number(2))))
      },

      test("set nested values") {
        val json    = Json.Object("user" -> Json.Object())
        val updated = json.set(p"user.name", Json.String("Bob"))
        assert(updated.get(p"user.name").one)(isRight(equalTo(Json.String("Bob"))))
      },

      test("delete values") {
        val json    = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
        val deleted = json.delete(p"b")
        assert(deleted.get(p"b").one)(isLeft)
      },

      test("insert into array") {
        val json     = Json.Object("items" -> Json.Array(Json.number(1), Json.number(3)))
        val inserted = json.insert(p"items[1]", Json.number(2))
        assert(inserted.get(p"items[1]").one)(isRight(equalTo(Json.number(2))))
      }
    ),

    // ===========================================================================
    // Transformation Tests
    // ===========================================================================
    suite("Transformation")(
      test("transformUp - bottom-up") {
        val json = Json.Object(
          "a" -> Json.Object("value" -> Json.number(1)),
          "b" -> Json.Object("value" -> Json.number(2))
        )

        val transformed = json.transformUp { (path, value) =>
          if (
            path.nodes.lastOption.exists {
              case DynamicOptic.Node.Field("value") => true
              case _                                => false
            }
          ) {
            Json.number(value.asInstanceOf[Json.Number].toInt * 10)
          } else value
        }

        assert(transformed.get(p"a.value").one)(isRight(equalTo(Json.number(10))))
      },

      test("transformDown - top-down") {
        val json = Json.Object("numbers" -> Json.Array(Json.number(1), Json.number(2)))

        val transformed = json.transformDown { (path, value) =>
          value match {
            case Json.Array(_) if path.nodes.contains(DynamicOptic.Node.Field("numbers")) =>
              Json.Array(value.asInstanceOf[Json.Array].elements.map(_ => Json.number(0)))
            case _ => value
          }
        }

        assert(transformed.get(p"numbers[0]").one)(isRight(equalTo(Json.number(0))))
      },

      test("transformKeys") {
        val json        = Json.Object("old_key1" -> Json.number(1), "old_key2" -> Json.number(2))
        val transformed =
          json.transformKeys((_, key) => if (key.startsWith("old_")) key.replace("old_", "new_") else key)

        assert(transformed.get(p"new_key1").one)(isRight(equalTo(Json.number(1))))
      },

      test("filter values") {
        val json = Json.Object(
          "keep"   -> Json.String("yes"),
          "remove" -> Json.String("no"),
          "number" -> Json.number(42)
        )

        val filtered = json.filter((_, value) => value.isString && value.stringValue.contains("yes"))
        assert(filtered.get(p"keep").one)(isRight(equalTo(Json.String("yes"))))
        assert(filtered.get(p"remove").one)(isLeft)
      }
    ),

    // ===========================================================================
    // Folding Tests
    // ===========================================================================
    suite("Folding")(
      test("foldUp - collect all numbers") {
        val json = Json.Object(
          "a" -> Json.number(1),
          "b" -> Json.Object("c" -> Json.number(2), "d" -> Json.String("test"))
        )

        val sum = json.foldUp(0) { (_, value, acc) =>
          value match {
            case Json.Number(n) => acc + n.toInt
            case _              => acc
          }
        }

        assert(sum)(equalTo(3))
      },

      test("foldDown - count objects") {
        val json = Json.Object(
          "level1" -> Json.Object(
            "level2" -> Json.Object("value" -> Json.number(1))
          )
        )

        val count = json.foldDown(0) { (_, value, acc) =>
          if (value.isObject) acc + 1 else acc
        }

        assert(count)(equalTo(3)) // Includes root object
      }
    ),

    // ===========================================================================
    // Utility Tests
    // ===========================================================================
    suite("Utilities")(
      test("normalize sorts keys and normalizes numbers") {
        val json = Json.Object(
          "z" -> Json.Number("01.0"),
          "a" -> Json.Object("y" -> Json.number(2), "x" -> Json.number(1))
        )

        val normalized = json.normalize
        val str        = normalized.encode

        assertTrue(str.contains("\"a\"") && str.indexOf("\"a\"") < str.indexOf("\"z\""))
      },

      test("sortKeys recursively") {
        val json = Json.Object(
          "c" -> Json.Object("b" -> Json.number(1), "a" -> Json.number(2)),
          "a" -> Json.number(3)
        )

        val sorted = json.sortKeys
        val str    = sorted.encode

        assertTrue(str.indexOf("\"a\"") < str.indexOf("\"c\""))
      },

      test("dropNulls removes null values") {
        val json = Json.Object(
          "keep"   -> Json.String("value"),
          "remove" -> Json.Null,
          "nested" -> Json.Object("null" -> Json.Null, "value" -> Json.String("keep"))
        )

        val cleaned = json.dropNulls
        assert(cleaned.get(p"remove").one)(isLeft)
        assert(cleaned.get(p"nested.null").one)(isLeft)
        assert(cleaned.get(p"nested.value").one)(isRight(equalTo(Json.String("keep"))))
      },

      test("dropEmpty removes empty objects and arrays") {
        val json = Json.Object(
          "emptyObj" -> Json.Object(),
          "emptyArr" -> Json.Array(),
          "keep"     -> Json.String("value")
        )

        val cleaned = json.dropEmpty
        assert(cleaned.get(p"emptyObj").one)(isLeft)
        assert(cleaned.get(p"keep").one)(isRight(equalTo(Json.String("value"))))
      },

      test("toKV flattens JSON") {
        val json = Json.Object(
          "a" -> Json.Object("b" -> Json.number(1)),
          "c" -> Json.Array(Json.number(2), Json.number(3))
        )

        val kv = json.toKV
        assert(kv.size)(equalTo(3))
        assertTrue(kv.exists { case (path, value) =>
          path.toString == ".a.b" && value == Json.number(1)
        })
      },

      test("fromKV assembles JSON") {
        val kv = Seq(
          p"a.b"  -> Json.number(1),
          p"a.c"  -> Json.String("test"),
          p"d[0]" -> Json.Boolean(true)
        )

        val json = Json.fromKVUnsafe(kv)
        assert(json.get(p"a.b").one)(isRight(equalTo(Json.number(1))))
        assert(json.get(p"a.c").one)(isRight(equalTo(Json.String("test"))))
        assert(json.get(p"d[0]").one)(isRight(equalTo(Json.Boolean(true))))
      },

      test("project selects specific paths") {
        val json = Json.Object(
          "user" -> Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30)),
          "meta" -> Json.Object("created" -> Json.String("2023-01-01"))
        )

        val projected = json.project(p"user.name", p"meta.created")
        assert(projected.get(p"user.name").one)(isRight(equalTo(Json.String("Alice"))))
        assert(projected.get(p"user.age").one)(isLeft) // age not projected
      },

      test("partition splits JSON") {
        val json = Json.Object(
          "strings" -> Json.Array(Json.String("a"), Json.String("b")),
          "numbers" -> Json.Array(Json.number(1), Json.number(2))
        )

        val (strings, _) = json.partition((_, value) =>
          value match {
            case Json.Array(elems) => elems.forall(_.isString)
            case _                 => false
          }
        )

        assert(strings.get(p"strings").one)(isRight)
        assert(strings.get(p"numbers").one)(isLeft)
      },

      test("query finds matching values") {
        val json = Json.Object(
          "a" -> Json.number(10),
          "b" -> Json.number(20),
          "c" -> Json.String("30")
        )

        val results = json.query((_, value) => value.isNumber && value.asInstanceOf[Json.Number].toInt > 15)
        assert(results.toEither)(isRight(hasSize(equalTo(1))))
      }
    ),

    // ===========================================================================
    // Merging Tests
    // ===========================================================================
    suite("Merging")(
      test("deep merge") {
        val json1 = Json.Object(
          "a"      -> Json.number(1),
          "nested" -> Json.Object("x" -> Json.String("old"))
        )
        val json2 = Json.Object(
          "b"      -> Json.number(2),
          "nested" -> Json.Object("y" -> Json.String("new"))
        )

        val merged = json1.merge(json2, Deep)
        assert(merged.get(p"a").one)(isRight(equalTo(Json.number(1))))
        assert(merged.get(p"b").one)(isRight(equalTo(Json.number(2))))
        assert(merged.get(p"nested.x").one)(isRight(equalTo(Json.String("old"))))
        assert(merged.get(p"nested.y").one)(isRight(equalTo(Json.String("new"))))
      },

      test("replace strategy") {
        val json1 = Json.Object("a" -> Json.number(1))
        val json2 = Json.Object("a" -> Json.number(2), "b" -> Json.number(3))

        val merged = json1.merge(json2, Replace)
        assert(merged)(equalTo(json2))
      }
    ),

    // ===========================================================================
    // DynamicValue Interop Tests
    // ===========================================================================
    suite("DynamicValue Interop")(
      test("convert to DynamicValue") {
        val json = Json.Object(
          "name"   -> Json.String("Alice"),
          "age"    -> Json.number(30),
          "active" -> Json.Boolean(true)
        )

        val dynamic = json.toDynamicValue
        assert(dynamic)(isSubtype[DynamicValue.Record](anything))
      },

      test("convert from DynamicValue") {
        val dynamic = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "tags" -> DynamicValue.Sequence(
              Vector(
                DynamicValue.Primitive(PrimitiveValue.String("scala")),
                DynamicValue.Primitive(PrimitiveValue.String("zio"))
              )
            )
          )
        )

        val json = Json.fromDynamicValue(dynamic)
        assert(json.get(p"name").one)(isRight(equalTo(Json.String("Bob"))))
        assert(json.get(p"tags[0]").one)(isRight(equalTo(Json.String("scala"))))
      }
    ),

    // ===========================================================================
    // Schema Validation Tests
    // ===========================================================================
    suite("Schema Validation")(
      test("validate with basic schemas") {
        val stringSchema = JsonSchema.String
        val numberSchema = JsonSchema.Number

        assertTrue(Json.String("test").conforms(stringSchema))
        assertTrue(!Json.number(42).conforms(stringSchema))
        assertTrue(Json.number(42).conforms(numberSchema))
      },

      test("validate object schema") {
        val userSchema = JsonSchema.Object(
          fields = Map(
            "name"  -> JsonSchema.String,
            "age"   -> JsonSchema.Number,
            "email" -> JsonSchema.Optional(JsonSchema.String)
          ),
          required = Set("name", "age")
        )

        val validUser = Json.Object(
          "name"  -> Json.String("Alice"),
          "age"   -> Json.number(30),
          "email" -> Json.String("alice@example.com")
        )

        val invalidUser = Json.Object(
          "name" -> Json.String("Bob")
          // missing required age
        )

        assertTrue(validUser.conforms(userSchema))
        assertTrue(!invalidUser.conforms(userSchema))
      },

      test("validate array schema") {
        val numberArraySchema = JsonSchema.Array(JsonSchema.Number)

        val validArray   = Json.Array(Json.number(1), Json.number(2), Json.number(3))
        val invalidArray = Json.Array(Json.number(1), Json.String("two"))

        assertTrue(validArray.conforms(numberArraySchema))
        assertTrue(!invalidArray.conforms(numberArraySchema))
      },

      test("validate union schema") {
        val stringOrNumberSchema = JsonSchema.Union(JsonSchema.String, JsonSchema.Number)

        assertTrue(Json.String("test").conforms(stringOrNumberSchema))
        assertTrue(Json.number(42).conforms(stringOrNumberSchema))
        assertTrue(!Json.Boolean(true).conforms(stringOrNumberSchema))
      }
    ),

    // ===========================================================================
    // Encoding/Decoding Tests
    // ===========================================================================
    suite("Encoding/Decoding")(
      test("encode to compact string") {
        val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
        val str  = json.encode
        assertTrue(str.contains("\"name\":\"Alice\"") && str.contains("\"age\":30"))
      },

      test("encode with pretty printing") {
        val json   = Json.Object("nested" -> Json.Object("value" -> Json.number(1)))
        val pretty = json.print(WriterConfig.withIndentionStep(2))
        assertTrue(pretty.contains("\n") && pretty.contains("  "))
      },

      test("encode to bytes") {
        val json  = Json.String("test")
        val bytes = json.encodeToBytes
        assertTrue(new String(bytes, "UTF-8") == "\"test\"")
      }
    ),

    // ===========================================================================
    // Comparison Tests
    // ===========================================================================
    suite("Comparison")(
      test("ordering follows JSON spec") {
        assertTrue(Json.Null.compare(Json.Boolean(true)) < 0)
        assertTrue(Json.Boolean(false).compare(Json.number(1)) < 0)
        assertTrue(Json.number(1).compare(Json.String("test")) < 0)
        assertTrue(Json.String("test").compare(Json.Array()) < 0)
        assertTrue(Json.Array().compare(Json.Object()) < 0)
      },

      test("object comparison ignores key order") {
        val obj1 = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
        val obj2 = Json.Object("b" -> Json.number(2), "a" -> Json.number(1))
        assertTrue(obj1.compare(obj2) == 0)
      },

      test("array comparison is element-wise") {
        val arr1 = Json.Array(Json.number(1), Json.number(2))
        val arr2 = Json.Array(Json.number(1), Json.number(3))
        assertTrue(arr1.compare(arr2) < 0)
      }
    ),

    // ===========================================================================
    // Error Handling Tests
    // ===========================================================================
    suite("Error Handling")(
      test("JsonError includes path information") {
        val error = JsonError("Test error", DynamicOptic.root.field("user").field("name"))
        assertTrue(error.getMessage.contains("user.name"))
      },

      test("navigation returns empty selection for invalid paths") {
        val json   = Json.Object("existing" -> Json.String("value"))
        val result = json.get(p"nonexistent")
        assertTrue(result.isEmpty)
      }
    ),

    // ===========================================================================
    // Performance Tests
    // ===========================================================================
    suite("Performance")(
      test("large JSON operations") {
        val largeArray = Json.Array((1 to 1000).map(i => Json.Object("id" -> Json.number(i))): _*)

        // Test navigation performance
        val first = largeArray.get(p"[0].id")
        assert(first.one)(isRight(equalTo(Json.number(1))))

        // Test transformation performance
        val transformed = largeArray.transformDown { (_, value) =>
          value match {
            case Json.Object(fields) if fields.exists(_._1 == "id") =>
              Json.Object(fields.map {
                case (k, v) if k == "id" => ("doubled", Json.number(v.asInstanceOf[Json.Number].toInt * 2))
                case other               => other
              })
            case _ => value
          }
        }

        assert(transformed.get(p"[0].doubled").one)(isRight(equalTo(Json.number(2))))
      }
    ),

    // ===========================================================================
    // Edge Cases Tests
    // ===========================================================================
    suite("Edge Cases")(
      test("deeply nested structures") {
        val deep = (1 to 10).foldLeft(Json.Object("value" -> Json.String("deep")): Json) { (acc, _) =>
          Json.Object("nested" -> acc)
        }

        // Build path manually for 10 levels deep
        val path = (1 to 10)
          .foldLeft(DynamicOptic.root) { (acc, _) =>
            acc.field("nested")
          }
          .field("value")

        val value = deep.get(path)
        assert(value.one)(isRight(equalTo(Json.String("deep"))))
      },

      test("empty structures") {
        val emptyObj = Json.Object()
        val emptyArr = Json.Array()

        assertTrue(emptyObj.fields.isEmpty)
        assertTrue(emptyArr.elements.isEmpty)
        assertTrue(emptyObj.get(p"any").isEmpty)
        assertTrue(emptyArr.get(p"[0]").isEmpty)
      },

      test("Unicode handling") {
        val unicode = Json.String("🚀 Unicode test: ñáéíóú")
        val encoded = unicode.encode
        // Unicode characters are properly escaped in JSON output
        assertTrue(encoded.contains("\\ud83d\\ude80") && encoded.contains("\\u00f1\\u00e1\\u00e9\\u00ed\\u00f3\\u00fa"))
      }
    )
  )

  // Helper extension for repeating path segments
  implicit class StringOps(val s: String) extends AnyVal {
    def repeat(n: Int): String = s * n
  }
}
