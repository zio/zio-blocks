package zio.blocks.template

import zio.test._

object ToAttrValueSpec extends ZIOSpecDefault {
  def spec = suite("ToAttrValue")(
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
  )
}
