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
        val expected = Json.obj("name" -> Json.str("Alice"), "age" -> Json.num(30))
        assertTrue(Json.parse("{\"name\":\"Alice\",\"age\":30}") == Right(expected))
      },
      test("parse empty array") {
        assertTrue(Json.parse("[]") == Right(Json.Array.empty))
      },
      test("parse array") {
        val expected = Json.arr(Json.num(1), Json.num(2), Json.num(3))
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
        assertTrue(Json.num(42).print == "42") &&
        assertTrue(Json.num(3.14).print == "3.14")
      },
      test("print string") {
        assertTrue(Json.str("hello").print == "\"hello\"")
      },
      test("print string with escapes") {
        assertTrue(Json.str("hello\nworld").print == "\"hello\\nworld\"")
      },
      test("print object") {
        val json = Json.obj("a" -> Json.num(1))
        assertTrue(json.print == "{\"a\":1}")
      },
      test("print array") {
        val json = Json.arr(Json.num(1), Json.num(2))
        assertTrue(json.print == "[1,2]")
      }
    ),
    suite("type tests")(
      test("isObject") {
        assertTrue(Json.obj().isObject) &&
        assertTrue(!Json.arr().isObject)
      },
      test("isArray") {
        assertTrue(Json.arr().isArray) &&
        assertTrue(!Json.obj().isArray)
      },
      test("isString") {
        assertTrue(Json.str("").isString) &&
        assertTrue(!Json.num(0).isString)
      },
      test("isNumber") {
        assertTrue(Json.num(0).isNumber) &&
        assertTrue(!Json.str("").isNumber)
      },
      test("isBoolean") {
        assertTrue(Json.bool(true).isBoolean) &&
        assertTrue(!Json.Null.isBoolean)
      },
      test("isNull") {
        assertTrue(Json.Null.isNull) &&
        assertTrue(!Json.bool(false).isNull)
      }
    ),
    suite("navigation")(
      test("apply on object") {
        val json = Json.obj("name" -> Json.str("Alice"))
        assertTrue(json("name").single == Right(Json.str("Alice")))
      },
      test("apply on array") {
        val json = Json.arr(Json.num(1), Json.num(2), Json.num(3))
        assertTrue(json(0).single == Right(Json.num(1))) &&
        assertTrue(json(2).single == Right(Json.num(3)))
      },
      test("nested navigation") {
        val json = Json.obj("person" -> Json.obj("name" -> Json.str("Alice")))
        assertTrue(json("person")("name").single == Right(Json.str("Alice")))
      }
    ),
    suite("merge")(
      test("merge objects") {
        val left     = Json.obj("a" -> Json.num(1))
        val right    = Json.obj("b" -> Json.num(2))
        val expected = Json.obj("a" -> Json.num(1), "b" -> Json.num(2))
        assertTrue(left.merge(right) == expected)
      },
      test("merge overwrites") {
        val left     = Json.obj("a" -> Json.num(1))
        val right    = Json.obj("a" -> Json.num(2))
        val expected = Json.obj("a" -> Json.num(2))
        assertTrue(left.merge(right) == expected)
      }
    ),
    suite("transformations")(
      test("sortKeys") {
        val json   = Json.obj("c" -> Json.num(3), "a" -> Json.num(1), "b" -> Json.num(2))
        val sorted = json.sortKeys
        assertTrue(sorted.fields.map(_._1) == Seq("a", "b", "c"))
      },
      test("dropNulls") {
        val json   = Json.obj("a" -> Json.num(1), "b" -> Json.Null, "c" -> Json.num(3))
        val result = json.dropNulls
        assertTrue(result.fields.length == 2)
      },
      test("normalize") {
        val json       = Json.obj("b" -> Json.num(2), "a" -> Json.num(1))
        val normalized = json.normalize
        assertTrue(normalized.fields.map(_._1) == Seq("a", "b"))
      }
    ),
    suite("DynamicValue interop")(
      test("toDynamicValue for null") {
        val dv = Json.Null.toDynamicValue
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Unit))
      },
      test("toDynamicValue for boolean") {
        val dv = Json.bool(true).toDynamicValue
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      },
      test("toDynamicValue for number") {
        val dv = Json.num(42).toDynamicValue
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(42))))
      },
      test("toDynamicValue for string") {
        val dv = Json.str("hello").toDynamicValue
        assertTrue(dv == DynamicValue.Primitive(PrimitiveValue.String("hello")))
      },
      test("toDynamicValue for array") {
        val dv = Json.arr(Json.num(1), Json.num(2)).toDynamicValue
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
        val dv = Json.obj("a" -> Json.num(1)).toDynamicValue
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
        assertTrue(json == Json.bool(true))
      },
      test("fromDynamicValue for primitive Int") {
        val json = Json.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(json == Json.num(42))
      },
      test("fromDynamicValue for primitive String") {
        val json = Json.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        assertTrue(json == Json.str("hello"))
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
        assertTrue(json == Json.arr(Json.num(1), Json.num(2)))
      },
      test("fromDynamicValue for Record") {
        val json = Json.fromDynamicValue(
          DynamicValue.Record(
            Vector(
              ("a", DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )
          )
        )
        assertTrue(json == Json.obj("a" -> Json.num(1)))
      }
    ),
    suite("comparison")(
      test("compare nulls") {
        assertTrue(Json.Null.compare(Json.Null) == 0)
      },
      test("null less than boolean") {
        assertTrue(Json.Null.compare(Json.bool(false)) < 0)
      },
      test("boolean less than number") {
        assertTrue(Json.bool(true).compare(Json.num(0)) < 0)
      },
      test("number less than string") {
        assertTrue(Json.num(100).compare(Json.str("")) < 0)
      },
      test("compare booleans") {
        assertTrue(Json.bool(false).compare(Json.bool(true)) < 0)
      },
      test("compare numbers") {
        assertTrue(Json.num(1).compare(Json.num(2)) < 0) &&
        assertTrue(Json.num(2).compare(Json.num(2)) == 0) &&
        assertTrue(Json.num(3).compare(Json.num(2)) > 0)
      },
      test("compare strings") {
        assertTrue(Json.str("a").compare(Json.str("b")) < 0)
      },
      test("ordering implicit") {
        val jsons: List[Json] = List(Json.num(3), Json.num(1), Json.num(2))
        val sorted            = jsons.sorted
        assertTrue(sorted == List[Json](Json.num(1), Json.num(2), Json.num(3)))
      }
    ),
    suite("toKV and fromKV")(
      test("toKV for flat object") {
        val json = Json.obj("a" -> Json.num(1), "b" -> Json.num(2))
        val kv   = json.toKV
        assertTrue(kv.size == 2)
      },
      test("toKV for nested object") {
        val json = Json.obj("a" -> Json.obj("b" -> Json.num(1)))
        val kv   = json.toKV
        assertTrue(kv.size == 1) &&
        assertTrue(kv.head._2 == Json.num(1))
      },
      test("toKV for array") {
        val json = Json.arr(Json.num(1), Json.num(2))
        val kv   = json.toKV
        assertTrue(kv.size == 2)
      },
      test("fromKV simple") {
        val kv   = Seq((DynamicOptic.root.field("a"), Json.num(1)))
        val json = Json.fromKV(kv)
        assertTrue(json.isRight)
      }
    ),
    suite("WriterConfig")(
      test("print with config indentation") {
        val json   = Json.obj("a" -> Json.num(1))
        val config = WriterConfig.withIndentionStep(2)
        val result = json.print(config)
        assertTrue(result.contains("\n"))
      },
      test("encode with default config") {
        val json   = Json.obj("a" -> Json.num(1))
        val result = json.encode(WriterConfig)
        assertTrue(result == "{\"a\":1}")
      }
    )
  )
}
