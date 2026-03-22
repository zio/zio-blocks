package zio.blocks.template

import zio.test._
import CssLength.{CssLengthIntOps, CssLengthDoubleOps}

object CssLengthSpec extends ZIOSpecDefault {
  def spec = suite("CssLength")(
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
  )
}
