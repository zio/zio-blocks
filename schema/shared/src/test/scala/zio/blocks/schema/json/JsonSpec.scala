package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.test._

object JsonSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    suite("parsing")(
      test("parse null") {
        assertTrue(Json.parse("null") == Right(Json.Null))
      },
      test("parse true") {
        assertTrue(Json.parse("true") == Right(Json.Boolean.True))
      },
      test("parse false") {
        assertTrue(Json.parse("false") == Right(Json.Boolean.False))
      },
      test("parse integer") {
        assertTrue(Json.parse("42") == Right(Json.Number("42")))
      },
      test("parse negative number") {
        assertTrue(Json.parse("-123") == Right(Json.Number("-123")))
      },
      test("parse decimal") {
        assertTrue(Json.parse("3.14") == Right(Json.Number("3.14")))
      },
      test("parse string") {
        assertTrue(Json.parse("\"hello\"") == Right(Json.String("hello")))
      },
      test("parse string with escapes") {
        assertTrue(Json.parse("\"hello\\nworld\"") == Right(Json.String("hello\nworld")))
      },
      test("parse empty object") {
        assertTrue(Json.parse("{}") == Right(Json.Object.empty))
      },
      test("parse object") {
        val expected = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
        assertTrue(Json.parse("{\"name\":\"Alice\",\"age\":30}") == Right(expected))
      },
      test("parse empty array") {
        assertTrue(Json.parse("[]") == Right(Json.Array.empty))
      },
      test("parse array") {
        val expected = Json.Array(Json.number(1), Json.number(2), Json.number(3))
        assertTrue(Json.parse("[1,2,3]") == Right(expected))
      }
    ),
    suite("printing")(
      test("print null") {
        assertTrue(Json.Null.print == "null")
      },
      test("print boolean") {
        assertTrue(Json.Boolean.True.print == "true") &&
        assertTrue(Json.Boolean.False.print == "false")
      },
      test("print number") {
        assertTrue(Json.number(42).print == "42") &&
        assertTrue(Json.number(3.14).print == "3.14")
      },
      test("print string") {
        assertTrue(Json.String("hello").print == "\"hello\"")
      },
      test("print string with escapes") {
        assertTrue(Json.String("hello\nworld").print == "\"hello\\nworld\"")
      },
      test("print object") {
        val json = Json.Object("a" -> Json.number(1))
        assertTrue(json.print == "{\"a\":1}")
      },
      test("print array") {
        val json = Json.Array(Json.number(1), Json.number(2))
        assertTrue(json.print == "[1,2]")
      }
    ),
    suite("type tests")(
      test("isObject") {
        assertTrue(Json.Object.empty.isObject) &&
        assertTrue(!Json.Array.empty.isObject)
      },
      test("isArray") {
        assertTrue(Json.Array.empty.isArray) &&
        assertTrue(!Json.Object.empty.isArray)
      },
      test("isString") {
        assertTrue(Json.String("").isString) &&
        assertTrue(!Json.number(0).isString)
      },
      test("isNumber") {
        assertTrue(Json.number(0).isNumber) &&
        assertTrue(!Json.String("").isNumber)
      },
      test("isBoolean") {
        assertTrue(Json.Boolean(true).isBoolean) &&
        assertTrue(!Json.Null.isBoolean)
      },
      test("isNull") {
        assertTrue(Json.Null.isNull) &&
        assertTrue(!Json.Boolean(false).isNull)
      }
    ),
    suite("navigation")(
      test("apply on object") {
        val json = Json.Object("name" -> Json.String("Alice"))
        assertTrue(json("name").one == Right(Json.String("Alice")))
      },
      test("apply on array") {
        val json = Json.Array(Json.number(1), Json.number(2), Json.number(3))
        assertTrue(json(0).one == Right(Json.number(1))) &&
        assertTrue(json(2).one == Right(Json.number(3)))
      },
      test("nested navigation") {
        val json = Json.Object("person" -> Json.Object("name" -> Json.String("Alice")))
        assertTrue(json("person")("name").one == Right(Json.String("Alice")))
      },
      test("get with DynamicOptic") {
        val json = Json.Object("users" -> Json.Array(Json.Object("name" -> Json.String("Alice"))))
        val path = DynamicOptic.root.field("users").at(0).field("name")
        assertTrue(json.get(path).one == Right(Json.String("Alice")))
      }
    ),
    suite("modification")(
      test("set value") {
        val json     = Json.Object("a" -> Json.number(1))
        val path     = DynamicOptic.root.field("a")
        val modified = json.set(path, Json.number(2))
        assertTrue(modified("a").one == Right(Json.number(2)))
      },
      test("modify value") {
        val json     = Json.Object("a" -> Json.number(1))
        val path     = DynamicOptic.root.field("a")
        val modified = json.modify(
          path,
          {
            case Json.Number(v) => Json.number(v.toInt + 1)
            case other          => other
          }
        )
        assertTrue(modified("a").one == Right(Json.number(2)))
      },
      test("delete field") {
        val json     = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
        val path     = DynamicOptic.root.field("a")
        val modified = json.delete(path)
        assertTrue(modified("a").isEmpty && modified("b").one == Right(Json.number(2)))
      }
    ),
    suite("merge")(
      test("merge objects") {
        val left     = Json.Object("a" -> Json.number(1))
        val right    = Json.Object("b" -> Json.number(2))
        val expected = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
        assertTrue(left.merge(right) == expected)
      },
      test("merge overwrites") {
        val left     = Json.Object("a" -> Json.number(1))
        val right    = Json.Object("a" -> Json.number(2))
        val expected = Json.Object("a" -> Json.number(2))
        assertTrue(left.merge(right) == expected)
      }
    ),
    suite("transformations")(
      test("sortKeys") {
        val json   = Json.Object("c" -> Json.number(3), "a" -> Json.number(1), "b" -> Json.number(2))
        val sorted = json.sortKeys
        assertTrue(sorted.fields.map(_._1) == Seq("a", "b", "c"))
      },
      test("dropNulls") {
        val json   = Json.Object("a" -> Json.number(1), "b" -> Json.Null, "c" -> Json.number(3))
        val result = json.dropNulls
        assertTrue(result.fields.length == 2)
      },
      test("normalize") {
        val json       = Json.Object("b" -> Json.number(2), "a" -> Json.number(1))
        val normalized = json.normalize
        assertTrue(normalized.fields.map(_._1) == Seq("a", "b"))
      },
      test("transformUp") {
        val json   = Json.Object("a" -> Json.number(1))
        val result = json.transformUp((_, v) =>
          v match {
            case Json.Number(n) => Json.number(n.toInt * 2)
            case other          => other
          }
        )
        assertTrue(result("a").one == Right(Json.number(2)))
      },
      test("transformKeys") {
        val json   = Json.Object("firstName" -> Json.String("Alice"))
        val result = json.transformKeys((_, k) => k.toUpperCase)
        assertTrue(result("FIRSTNAME").one == Right(Json.String("Alice")))
      }
    ),
    suite("filtering")(
      test("filter values") {
        val json   = Json.Object("a" -> Json.number(1), "b" -> Json.number(2), "c" -> Json.number(3))
        val result = json.filter((_, v) =>
          v match {
            case Json.Number(n) => n.toInt > 1
            case _              => false
          }
        )
        assertTrue(result.fields.length == 2)
      },
      test("filterNot values") {
        val json   = Json.Object("a" -> Json.number(1), "b" -> Json.number(2), "c" -> Json.number(3))
        val result = json.filterNot((_, v) =>
          v match {
            case Json.Number(n) => n.toInt == 2
            case _              => false
          }
        )
        assertTrue(result.fields.length == 2)
      }
    ),
    suite("folding")(
      test("foldDown") {
        val json = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
        val sum  = json.foldDown(0) { (_, v, acc) =>
          v match {
            case Json.Number(n) => acc + n.toInt
            case _              => acc
          }
        }
        assertTrue(sum == 3)
      },
      test("foldUp") {
        val json = Json.Array(Json.number(1), Json.number(2), Json.number(3))
        val sum  = json.foldUp(0) { (_, v, acc) =>
          v match {
            case Json.Number(n) => acc + n.toInt
            case _              => acc
          }
        }
        assertTrue(sum == 6)
      }
    ),
    suite("DynamicValue interop")(
      test("toDynamicValue for null") {
        val dv = Json.Null.toDynamicValue
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Unit))
      },
      test("toDynamicValue for boolean") {
        val dv = Json.Boolean(true).toDynamicValue
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      },
      test("toDynamicValue for number") {
        val dv = Json.number(42).toDynamicValue
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(42))))
      },
      test("toDynamicValue for string") {
        val dv = Json.String("hello").toDynamicValue
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.String("hello")))
      },
      test("toDynamicValue for array") {
        val dv = Json.Array(Json.number(1), Json.number(2)).toDynamicValue
        assertTrue(
          dv == DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1))),
              DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(2)))
            )
          )
        )
      },
      test("toDynamicValue for object") {
        val dv = Json.Object("a" -> Json.number(1)).toDynamicValue
        assertTrue(
          dv == DynamicValue.Record(
            Vector(
              ("a", DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1))))
            )
          )
        )
      },
      test("fromDynamicValue for primitive Unit") {
        val json = Json.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(json == Json.Null)
      },
      test("fromDynamicValue for primitive Boolean") {
        val json = Json.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        assertTrue(json == Json.Boolean(true))
      },
      test("fromDynamicValue for primitive Int") {
        val json = Json.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(json == Json.number(42))
      },
      test("fromDynamicValue for primitive String") {
        val json = Json.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        assertTrue(json == Json.String("hello"))
      },
      test("fromDynamicValue for Sequence") {
        val json = Json.fromDynamicValue(
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
        )
        assertTrue(json == Json.Array(Json.number(1), Json.number(2)))
      },
      test("fromDynamicValue for Record") {
        val json = Json.fromDynamicValue(
          DynamicValue.Record(
            Vector(
              ("a", DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )
          )
        )
        assertTrue(json == Json.Object("a" -> Json.number(1)))
      }
    ),
    suite("comparison")(
      test("compare nulls") {
        assertTrue(Json.Null.compare(Json.Null) == 0)
      },
      test("null less than boolean") {
        assertTrue(Json.Null.compare(Json.Boolean(false)) < 0)
      },
      test("boolean less than number") {
        assertTrue(Json.Boolean(true).compare(Json.number(0)) < 0)
      },
      test("number less than string") {
        assertTrue(Json.number(100).compare(Json.String("")) < 0)
      },
      test("compare booleans") {
        assertTrue(Json.Boolean(false).compare(Json.Boolean(true)) < 0)
      },
      test("compare numbers") {
        assertTrue(Json.number(1).compare(Json.number(2)) < 0) &&
        assertTrue(Json.number(2).compare(Json.number(2)) == 0) &&
        assertTrue(Json.number(3).compare(Json.number(2)) > 0)
      },
      test("compare strings") {
        assertTrue(Json.String("a").compare(Json.String("b")) < 0)
      },
      test("ordering implicit") {
        val jsons: List[Json] = List(Json.number(3), Json.number(1), Json.number(2))
        val sorted            = jsons.sorted
        assertTrue(sorted == List[Json](Json.number(1), Json.number(2), Json.number(3)))
      }
    ),
    suite("toKV and fromKV")(
      test("toKV for flat object") {
        val json = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
        val kv   = json.toKV
        assertTrue(kv.size == 2)
      },
      test("toKV for nested object") {
        val json = Json.Object("a" -> Json.Object("b" -> Json.number(1)))
        val kv   = json.toKV
        assertTrue(kv.size == 1) &&
        assertTrue(kv.head._2 == Json.number(1))
      },
      test("toKV for array") {
        val json = Json.Array(Json.number(1), Json.number(2))
        val kv   = json.toKV
        assertTrue(kv.size == 2)
      },
      test("fromKV simple") {
        val kv   = Seq((DynamicOptic.root.field("a"), Json.number(1)))
        val json = Json.fromKV(kv)
        assertTrue(json.isRight)
      },
      test("fromKV and toKV roundtrip") {
        val original = Json.Object("a" -> Json.number(1), "b" -> Json.String("test"))
        val kv       = original.toKV
        val restored = Json.fromKV(kv)
        assertTrue(restored == Right(original))
      }
    ),
    suite("WriterConfig")(
      test("print with config indentation") {
        val json   = Json.Object("a" -> Json.number(1))
        val config = WriterConfig.withIndentionStep(2)
        val result = json.print(config)
        assertTrue(result.contains("\n"))
      },
      test("encode with default config") {
        val json   = Json.Object("a" -> Json.number(1))
        val result = json.encode(WriterConfig)
        assertTrue(result == "{\"a\":1}")
      }
    ),
    suite("query")(
      test("query numbers") {
        val json = Json.Object(
          "a" -> Json.number(1),
          "b" -> Json.String("test"),
          "c" -> Json.number(2)
        )
        val numbers = json.query((_, v) => v.isNumber)
        assertTrue(numbers.toVector.map(_.length).contains(2))
      }
    ),
    suite("Number conversions")(
      test("toInt") {
        assertTrue(Json.number(42).asInstanceOf[Json.Number].toInt == 42)
      },
      test("toLong") {
        assertTrue(Json.number(42L).asInstanceOf[Json.Number].toLong == 42L)
      },
      test("toDouble") {
        assertTrue(Json.number(3.14).asInstanceOf[Json.Number].toDouble == 3.14)
      },
      test("toBigDecimal") {
        assertTrue(Json.number(42).asInstanceOf[Json.Number].toBigDecimal == BigDecimal(42))
      }
    ),
    suite("interpolators")(
      suite("path interpolator")(
        test("parse simple field") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p"name"
          val json = Json.Object("name" -> Json.String("Alice"))
          assertTrue(json.get(path).one == Right(Json.String("Alice")))
        },
        test("parse nested fields") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p"user.name"
          val json = Json.Object("user" -> Json.Object("name" -> Json.String("Alice")))
          assertTrue(json.get(path).one == Right(Json.String("Alice")))
        },
        test("parse array index") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p"users[0]"
          val json = Json.Object("users" -> Json.Array(Json.String("Alice"), Json.String("Bob")))
          assertTrue(json.get(path).one == Right(Json.String("Alice")))
        },
        test("parse nested field with array index") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p"users[0].name"
          val json = Json.Object(
            "users" -> Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Bob"))
            )
          )
          assertTrue(json.get(path).one == Right(Json.String("Alice")))
        },
        test("parse all elements wildcard") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p"users[*]"
          val json = Json.Object("users" -> Json.Array(Json.String("Alice"), Json.String("Bob")))
          assertTrue(json.get(path).toEither == Right(Vector(Json.String("Alice"), Json.String("Bob"))))
        },
        test("parse field with backticks") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p"`field.with.dots`"
          val json = Json.Object("field.with.dots" -> Json.String("value"))
          assertTrue(json.get(path).one == Right(Json.String("value")))
        },
        test("parse root path with double dollar") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p"$$"
          val json = Json.String("root")
          assertTrue(json.get(path).one == Right(Json.String("root")))
        },
        test("parse empty path") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p""
          val json = Json.String("root")
          assertTrue(json.get(path).one == Right(Json.String("root")))
        },
        test("parse path with dollar prefix") {
          import zio.blocks.schema.json.JsonInterpolators._
          val path = p"$$.user.name"
          val json = Json.Object("user" -> Json.Object("name" -> Json.String("Alice")))
          assertTrue(json.get(path).one == Right(Json.String("Alice")))
        }
      ),
      suite("json interpolator")(
        test("parse null") {
          import zio.blocks.schema.json.JsonInterpolators._
          val json = j"null"
          assertTrue(json == Json.Null)
        },
        test("parse boolean") {
          import zio.blocks.schema.json.JsonInterpolators._
          val json = j"true"
          assertTrue(json == Json.Boolean.True)
        },
        test("parse number") {
          import zio.blocks.schema.json.JsonInterpolators._
          val json = j"42"
          assertTrue(json == Json.Number("42"))
        },
        test("parse string") {
          import zio.blocks.schema.json.JsonInterpolators._
          val json = j""""hello""""
          assertTrue(json == Json.String("hello"))
        },
        test("parse object") {
          import zio.blocks.schema.json.JsonInterpolators._
          val json     = j"""{"name": "Alice", "age": 30}"""
          val expected = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number("30"))
          assertTrue(json == expected)
        },
        test("parse array") {
          import zio.blocks.schema.json.JsonInterpolators._
          val json     = j"""[1, 2, 3]"""
          val expected = Json.Array(Json.Number("1"), Json.Number("2"), Json.Number("3"))
          assertTrue(json == expected)
        }
      )
    )
  )
}
