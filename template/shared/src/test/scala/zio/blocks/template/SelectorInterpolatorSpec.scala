package zio.blocks.template

import zio.test._

object SelectorInterpolatorSpec extends ZIOSpecDefault {
  def spec = suite("selector interpolator")(
    test("static selector string") {
      val result = selector"div.active"
      assertTrue(result == CssSelector.Raw("div.active"))
    },
    test("selector with interpolated string") {
      val cls    = "highlight"
      val result = selector".${cls}"
      assertTrue(result == CssSelector.Raw(".highlight"))
    }
  )
}
