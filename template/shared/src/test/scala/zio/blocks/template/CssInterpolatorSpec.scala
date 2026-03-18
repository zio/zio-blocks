package zio.blocks.template

import zio.test._
import CssLength.CssLengthIntOps

object CssInterpolatorSpec extends ZIOSpecDefault {
  def spec = suite("css interpolator")(
    test("static CSS string") {
      val result = css"color: red"
      assertTrue(result == Css.Raw("color: red"))
    },
    test("CSS with interpolated CssLength") {
      val result = css"margin: ${10.px}"
      assertTrue(result == Css.Raw("margin: 10px"))
    },
    test("CSS with interpolated Int") {
      val result = css"width: ${100}"
      assertTrue(result == Css.Raw("width: 100"))
    }
  )
}
