package zio.blocks.template

import zio.test._
import CssLength.CssLengthIntOps

object InterpolatorSpec extends ZIOSpecDefault {
  def spec = suite("Interpolators")(
    suite("css interpolator")(
      test("static CSS string") {
        val result = css"color: red"
        assertTrue(result == Css("color: red"))
      },
      test("CSS with interpolated CssLength") {
        val result = css"margin: ${10.px}"
        assertTrue(result == Css("margin: 10px"))
      },
      test("CSS with interpolated Int") {
        val result = css"width: ${100}"
        assertTrue(result == Css("width: 100"))
      }
    ),
    suite("js interpolator")(
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
        assertTrue(result == Js("var s = \"hello\""))
      },
      test("JS with interpolated Boolean") {
        val result = js"var b = ${true}"
        assertTrue(result == Js("var b = true"))
      }
    ),
    suite("html interpolator")(
      test("static HTML string") {
        val result = html"<div>hello</div>"
        assertTrue(result.render == "<div>hello</div>")
      },
      test("HTML with interpolated string creates Text node") {
        val result = html"<p>${"content"}</p>"
        assertTrue(result.render == "<p>content</p>")
      },
      test("HTML with interpolated Dom element") {
        val inner  = div("inner")
        val result = html"${inner}"
        assertTrue(result.render == "<div>inner</div>")
      },
      test("HTML escapes interpolated strings") {
        val result = html"${"<script>alert('xss')</script>"}"
        assertTrue(result.render == "&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;")
      },
      test("HTML with multiple interpolations") {
        val result = html"<div>${"a"}${"b"}</div>"
        assertTrue(result.render == "<div>ab</div>")
      }
    )
  )
}
