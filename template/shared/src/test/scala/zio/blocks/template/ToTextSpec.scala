package zio.blocks.template

import zio.test._

object ToTextSpec extends ZIOSpecDefault {
  def spec = suite("ToText")(
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
  )
}
