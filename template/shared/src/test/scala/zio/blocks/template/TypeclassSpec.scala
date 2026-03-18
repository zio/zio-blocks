package zio.blocks.template

import zio.blocks.chunk.Chunk
import zio.test._
import CssLength.{CssLengthIntOps, CssLengthDoubleOps}

object TypeclassSpec extends ZIOSpecDefault {
  def spec = suite("Typeclasses")(
    suite("ToJs")(
      test("String is quoted and escaped") {
        assertTrue(ToJs[String].toJs("hello") == "\"hello\"")
      },
      test("String with special chars") {
        assertTrue(ToJs[String].toJs("a\"b") == "\"a\\\"b\"")
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
        assertTrue(result == "{\"a\":1}")
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
        assertTrue(ToJs[List[String]].toJs(List("a\"b", "c")) == "[\"a\\\"b\",\"c\"]")
      },
      test("Map[String, String] with key escaping") {
        val result = ToJs[Map[String, String]].toJs(Map("k" -> "v"))
        assertTrue(result == "{\"k\":\"v\"}")
      },
      test("Map empty") {
        assertTrue(ToJs[Map[String, Int]].toJs(Map.empty) == "{}")
      },
      test("Map[String, Int] with multiple entries uses comma separator") {
        val result = ToJs[Map[String, Int]].toJs(scala.collection.immutable.ListMap("a" -> 1, "b" -> 2))
        assertTrue(result == "{\"a\":1,\"b\":2}")
      },
      test("ToJs.fromSchema derives from Schema for case class") {
        import zio.blocks.schema.Schema
        case class Point(x: Int, y: Int)
        object Point {
          implicit val schema: Schema[Point] = Schema.derived
        }
        val point  = Point(1, 2)
        val result = ToJs[Point].toJs(point)
        assertTrue(result == "{\"x\":1,\"y\":2}")
      }
    ),
    suite("ToCss")(
      test("String is CSS-escaped") {
        assertTrue(ToCss[String].toCss("red") == "red")
      },
      test("String with special chars") {
        assertTrue(ToCss[String].toCss("a\\b") == "a\\\\b")
      },
      test("Int renders as string") {
        assertTrue(ToCss[Int].toCss(10) == "10")
      },
      test("Double renders as string") {
        assertTrue(ToCss[Double].toCss(1.5) == "1.5")
      },
      test("Css passthrough renders") {
        assertTrue(ToCss[Css].toCss(Css("color: red")) == "color: red")
      },
      test("Option[String] Some") {
        assertTrue(ToCss[Option[String]].toCss(Some("blue")) == "blue")
      },
      test("Option[String] None") {
        assertTrue(ToCss[Option[String]].toCss(None) == "")
      },
      test("CssLength") {
        assertTrue(ToCss[CssLength].toCss(CssLength(10, "px")) == "10px")
      },
      test("CssColor Rgb") {
        assertTrue(ToCss[CssColor].toCss(CssColor.Rgb(255, 0, 0)) == "rgb(255,0,0)")
      },
      test("Long ToCss") {
        assertTrue(ToCss[Long].toCss(100L) == "100")
      },
      test("Float ToCss") {
        assertTrue(ToCss[Float].toCss(1.5f) == "1.5")
      },
      test("Option[CssLength] Some") {
        assertTrue(ToCss[Option[CssLength]].toCss(Some(CssLength(10, "px"))) == "10px")
      },
      test("Option[CssLength] None") {
        assertTrue(ToCss[Option[CssLength]].toCss(None) == "")
      }
    ),
    suite("ToText")(
      test("String passthrough") {
        assertTrue(ToText[String].toText("hello") == "hello")
      },
      test("Int") {
        assertTrue(ToText[Int].toText(42) == "42")
      },
      test("Boolean") {
        assertTrue(ToText[Boolean].toText(true) == "true")
      },
      test("Char") {
        assertTrue(ToText[Char].toText('x') == "x")
      },
      test("BigInt") {
        assertTrue(ToText[BigInt].toText(BigInt(999)) == "999")
      },
      test("BigDecimal") {
        assertTrue(ToText[BigDecimal].toText(BigDecimal("3.14")) == "3.14")
      },
      test("Long") {
        assertTrue(ToText[Long].toText(100L) == "100")
      },
      test("Double") {
        assertTrue(ToText[Double].toText(3.14) == "3.14")
      },
      test("Float") {
        assertTrue(ToText[Float].toText(1.5f) == "1.5")
      },
      test("Byte") {
        assertTrue(ToText[Byte].toText(42.toByte) == "42")
      },
      test("Short") {
        assertTrue(ToText[Short].toText(100.toShort) == "100")
      }
    ),
    suite("ToElements")(
      test("String to text element") {
        assertTrue(ToElements[String].toElements("hello") == Chunk(Dom.Text("hello")))
      },
      test("Int to text element") {
        assertTrue(ToElements[Int].toElements(42) == Chunk(Dom.Text("42")))
      },
      test("Dom passthrough") {
        assertTrue(ToElements[Dom].toElements(Dom.Empty) == Chunk(Dom.Empty))
      },
      test("Option[String] Some") {
        assertTrue(ToElements[Option[String]].toElements(Some("x")) == Chunk(Dom.Text("x")))
      },
      test("Option[String] None") {
        assertTrue(ToElements[Option[String]].toElements(None) == Chunk.empty)
      },
      test("List[String]") {
        assertTrue(
          ToElements[List[String]].toElements(List("a", "b")) == Chunk(Dom.Text("a"), Dom.Text("b"))
        )
      },
      test("Chunk[Dom]") {
        val elems = Chunk(Dom.Text("x"), Dom.Empty)
        assertTrue(ToElements[Chunk[Dom]].toElements(elems) == Chunk(Dom.Text("x"), Dom.Empty))
      },
      test("Long to text element") {
        assertTrue(ToElements[Long].toElements(100L) == Chunk(Dom.Text("100")))
      },
      test("Double to text element") {
        assertTrue(ToElements[Double].toElements(3.14) == Chunk(Dom.Text("3.14")))
      },
      test("Boolean to text element") {
        assertTrue(ToElements[Boolean].toElements(true) == Chunk(Dom.Text("true")))
      },
      test("Char to text element") {
        assertTrue(ToElements[Char].toElements('x') == Chunk(Dom.Text("x")))
      },
      test("Iterable[Dom] to elements") {
        val elems: Iterable[Dom] = List(Dom.Text("a"), Dom.Text("b"))
        assertTrue(ToElements[Iterable[Dom]].toElements(elems) == Chunk(Dom.Text("a"), Dom.Text("b")))
      }
    ),
    suite("ToTagName")(
      test("SafeTagName") {
        assertTrue(ToTagName[SafeTagName].toTagName(SafeTagName.unsafe("div")) == "div")
      }
    ),
    suite("ToAttrName")(
      test("SafeAttrName") {
        assertTrue(ToAttrName[SafeAttrName].toAttrName(SafeAttrName.unsafe("id")) == "id")
      },
      test("EventAttrName") {
        assertTrue(ToAttrName[EventAttrName].toAttrName(EventAttrName.unsafe("onclick")) == "onclick")
      }
    ),
    suite("ToAttrValue")(
      test("String is HTML-escaped") {
        assertTrue(ToAttrValue[String].toAttrValue("a&b") == "a&amp;b")
      },
      test("Int renders as string") {
        assertTrue(ToAttrValue[Int].toAttrValue(42) == "42")
      },
      test("Boolean renders as string") {
        assertTrue(ToAttrValue[Boolean].toAttrValue(true) == "true")
      },
      test("Js is HTML-escaped") {
        assertTrue(ToAttrValue[Js].toAttrValue(Js("fn()")) == "fn()")
      },
      test("Js with HTML special chars is escaped") {
        assertTrue(ToAttrValue[Js].toAttrValue(Js("alert(\"xss\")")) == "alert(&quot;xss&quot;)")
      },
      test("Css is HTML-escaped") {
        assertTrue(ToAttrValue[Css].toAttrValue(Css("color: red")) == "color: red")
      },
      test("Long renders as string") {
        assertTrue(ToAttrValue[Long].toAttrValue(100L) == "100")
      },
      test("Double renders as string") {
        assertTrue(ToAttrValue[Double].toAttrValue(3.14) == "3.14")
      },
      test("Char is HTML-escaped") {
        assertTrue(ToAttrValue[Char].toAttrValue('<') == "&lt;")
      },
      test("Css with special chars is HTML-escaped") {
        assertTrue(ToAttrValue[Css].toAttrValue(Css("a&b")) == "a&amp;b")
      },
      test("Option[String] Some") {
        assertTrue(ToAttrValue[Option[String]].toAttrValue(Some("x")) == "x")
      },
      test("Option[String] None") {
        assertTrue(ToAttrValue[Option[String]].toAttrValue(None) == "")
      },
      test("Iterable[String]") {
        assertTrue(ToAttrValue[Iterable[String]].toAttrValue(List("a", "b")) == "a b")
      },
      test("Iterable[String] with escaping") {
        assertTrue(ToAttrValue[Iterable[String]].toAttrValue(List("a&b", "c<d")) == "a&amp;b c&lt;d")
      }
    ),
    suite("CssLength")(
      test("integer value renders without decimal") {
        assertTrue(CssLength(10, "px").render == "10px")
      },
      test("fractional value renders with decimal") {
        assertTrue(CssLength(1.5, "em").render == "1.5em")
      },
      test("Int extension px") {
        assertTrue(10.px == CssLength(10, "px"))
      },
      test("Int extension em") {
        assertTrue(2.em == CssLength(2, "em"))
      },
      test("Int extension rem") {
        assertTrue(1.rem == CssLength(1, "rem"))
      },
      test("Int extension pct") {
        assertTrue(50.pct == CssLength(50, "%"))
      },
      test("Int extension vh") {
        assertTrue(100.vh == CssLength(100, "vh"))
      },
      test("Int extension vw") {
        assertTrue(100.vw == CssLength(100, "vw"))
      },
      test("Double extension px") {
        assertTrue(1.5.px == CssLength(1.5, "px"))
      },
      test("Double extension em") {
        assertTrue(2.5.em == CssLength(2.5, "em"))
      },
      test("Double extension rem") {
        assertTrue(1.5.rem == CssLength(1.5, "rem"))
      },
      test("Double extension pct") {
        assertTrue(50.5.pct == CssLength(50.5, "%"))
      },
      test("Double extension vh") {
        assertTrue(33.3.vh == CssLength(33.3, "vh"))
      },
      test("Double extension vw") {
        assertTrue(66.6.vw == CssLength(66.6, "vw"))
      }
    ),
    suite("CssColor")(
      test("Hex renders with hash") {
        assertTrue(CssColor.Hex("ff0000").map(_.render) == Some("#ff0000"))
      },
      test("Hex validates format") {
        assertTrue(CssColor.Hex("invalid").isEmpty)
      },
      test("Rgb renders") {
        assertTrue(CssColor.Rgb(255, 0, 0).render == "rgb(255,0,0)")
      },
      test("Rgba renders") {
        assertTrue(CssColor.Rgba(255, 0, 0, 0.5).render == "rgba(255,0,0,0.5)")
      },
      test("Hsl renders with percent") {
        assertTrue(CssColor.Hsl(120, 50, 50).render == "hsl(120,50%,50%)")
      },
      test("Named validates against whitelist") {
        assertTrue(CssColor.Named("red").map(_.render) == Some("red"))
      },
      test("Named rejects invalid names") {
        assertTrue(CssColor.Named("notacolor").isEmpty)
      },
      test("Named unsafe allows any string") {
        assertTrue(CssColor.Named.unsafe("anything").render == "anything")
      }
    )
  )
}
