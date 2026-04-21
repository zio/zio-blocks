package zio.blocks.template

import zio.test._

object ToJsSpec extends ZIOSpecDefault {
  def spec = suite("ToJs")(
    test("String is quoted and escaped") {
      assertTrue(ToJs[String].toJs("hello") == """"hello"""")
    },
    test("String with special chars") {
      assertTrue(ToJs[String].toJs("""a"b""") == """"a\"b"""")
    },
    test("Int renders as number") {
      assertTrue(ToJs[Int].toJs(42) == "42")
    },
    test("Long renders as number") {
      assertTrue(ToJs[Long].toJs(100L) == "100")
    },
    test("Double renders as number") {
      assertTrue(ToJs[Double].toJs(3.14) == "3.14")
    },
    test("Double NaN") {
      assertTrue(ToJs[Double].toJs(Double.NaN) == "NaN")
    },
    test("Double Infinity") {
      assertTrue(
        ToJs[Double].toJs(Double.PositiveInfinity) == "Infinity",
        ToJs[Double].toJs(Double.NegativeInfinity) == "-Infinity"
      )
    },
    test("Boolean true/false") {
      assertTrue(
        ToJs[Boolean].toJs(true) == "true",
        ToJs[Boolean].toJs(false) == "false"
      )
    },
    test("Option[Int] Some") {
      assertTrue(ToJs[Option[Int]].toJs(Some(42)) == "42")
    },
    test("Option[Int] None") {
      assertTrue(ToJs[Option[Int]].toJs(None) == "null")
    },
    test("List[Int]") {
      assertTrue(ToJs[List[Int]].toJs(List(1, 2, 3)) == "[1,2,3]")
    },
    test("List empty") {
      assertTrue(ToJs[List[Int]].toJs(List.empty) == "[]")
    },
    test("Unit renders as undefined") {
      assertTrue(ToJs[Unit].toJs(()) == "undefined")
    },
    test("Js passthrough") {
      assertTrue(ToJs[Js].toJs(Js("x + 1")) == "x + 1")
    },
    test("Map[String, Int]") {
      val result = ToJs[Map[String, Int]].toJs(Map("a" -> 1))
      assertTrue(result == """{"a":1}""")
    },
    test("Float normal") {
      assertTrue(ToJs[Float].toJs(1.5f) == "1.5")
    },
    test("Float NaN") {
      assertTrue(ToJs[Float].toJs(Float.NaN) == "NaN")
    },
    test("Float positive Infinity") {
      assertTrue(ToJs[Float].toJs(Float.PositiveInfinity) == "Infinity")
    },
    test("Float negative Infinity") {
      assertTrue(ToJs[Float].toJs(Float.NegativeInfinity) == "-Infinity")
    },
    test("List[String] with escaping") {
      assertTrue(ToJs[List[String]].toJs(List("""a"b""", "c")) == """["a\"b","c"]""")
    },
    test("Map[String, String] with key escaping") {
      val result = ToJs[Map[String, String]].toJs(Map("k" -> "v"))
      assertTrue(result == """{"k":"v"}""")
    },
    test("Map empty") {
      assertTrue(ToJs[Map[String, Int]].toJs(Map.empty) == "{}")
    },
    test("Map[String, Int] with multiple entries uses comma separator") {
      val result = ToJs[Map[String, Int]].toJs(scala.collection.immutable.ListMap("a" -> 1, "b" -> 2))
      assertTrue(result == """{"a":1,"b":2}""")
    },
    test("ToJs.fromSchema derives from Schema for case class") {
      import zio.blocks.schema.Schema
      case class Point(x: Int, y: Int)
      object Point {
        implicit val schema: Schema[Point] = Schema.derived
      }
      val point  = Point(1, 2)
      val result = ToJs[Point].toJs(point)
      assertTrue(result == """{"x":1,"y":2}""")
    },
    test("ToJs.fromSchema escapes angle brackets in JSON output") {
      import zio.blocks.schema.Schema
      case class Wrap(v: String)
      object Wrap {
        implicit val schema: Schema[Wrap] = Schema.derived
      }
      val result = ToJs[Wrap].toJs(Wrap("<b>"))
      assertTrue(result == "{\"v\":\"\\u003cb\\u003e\"}")
    }
  )
}
