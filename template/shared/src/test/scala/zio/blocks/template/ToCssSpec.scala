package zio.blocks.template

import zio.test._

object ToCssSpec extends ZIOSpecDefault {
  def spec = suite("ToCss")(
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
  )
}
