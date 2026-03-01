package zio.blocks.template

import zio.test._
import CssLength.CssLengthIntOps

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
      test("Css passthrough") {
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
      }
    ),
    suite("ToElements")(
      test("String to text element") {
        assertTrue(ToElements[String].toElements("hello") == Vector(Dom.Text("hello")))
      },
      test("Int to text element") {
        assertTrue(ToElements[Int].toElements(42) == Vector(Dom.Text("42")))
      },
      test("Dom passthrough") {
        assertTrue(ToElements[Dom].toElements(Dom.Empty) == Vector(Dom.Empty))
      },
      test("Option[String] Some") {
        assertTrue(ToElements[Option[String]].toElements(Some("x")) == Vector(Dom.Text("x")))
      },
      test("Option[String] None") {
        assertTrue(ToElements[Option[String]].toElements(None) == Vector.empty)
      },
      test("List[String]") {
        assertTrue(
          ToElements[List[String]].toElements(List("a", "b")) == Vector(Dom.Text("a"), Dom.Text("b"))
        )
      },
      test("Vector[Dom]") {
        val elems = Vector(Dom.Text("x"), Dom.Empty)
        assertTrue(ToElements[Vector[Dom]].toElements(elems) == Vector(Dom.Text("x"), Dom.Empty))
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
      test("Js passthrough (no escaping)") {
        assertTrue(ToAttrValue[Js].toAttrValue(Js("fn()")) == "fn()")
      },
      test("Css is HTML-escaped") {
        assertTrue(ToAttrValue[Css].toAttrValue(Css("color: red")) == "color: red")
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
      }
    ),
    suite("CssColor")(
      test("Hex renders with hash") {
        assertTrue(CssColor.Hex("ff0000").render == "#ff0000")
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
      test("Named renders name directly") {
        assertTrue(CssColor.Named("red").render == "red")
      }
    )
  )
}
