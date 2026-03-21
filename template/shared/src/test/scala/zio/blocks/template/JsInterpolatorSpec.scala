package zio.blocks.template

import zio.test._

object JsInterpolatorSpec extends ZIOSpecDefault {
  def spec = suite("js interpolator")(
    test("static JS string") {
      val result = js"var x = 1"
      assertTrue(result == Js("var x = 1"))
    },
    test("JS with interpolated Int") {
      val result = js"var x = ${42}"
      assertTrue(result == Js("var x = 42"))
    },
    test("JS with interpolated String is quoted and escaped") {
      val result = js"var s = ${"hello"}"
      assertTrue(result == Js("""var s = "hello""""))
    },
    test("JS with interpolated Boolean") {
      val result = js"var b = ${true}"
      assertTrue(result == Js("var b = true"))
    }
  )
}
