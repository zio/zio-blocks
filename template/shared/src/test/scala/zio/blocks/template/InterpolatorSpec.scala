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
      test("static HTML string produces typed DOM") {
        val result = html"<div>hello</div>"
        assertTrue(
          result == div("hello"),
          result.render == "<div>hello</div>"
        )
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
    ),
    suite("html interpolator produces typed DOM structure")(
      test("simple div matches DSL") {
        val fromHtml = html"<div>hello</div>"
        val fromDsl  = div("hello")
        assertTrue(fromHtml == fromDsl)
      },
      test("div with attribute matches DSL") {
        val fromHtml = html"""<div class="container">text</div>"""
        val fromDsl  = div(`class` := "container")("text")
        assertTrue(fromHtml == fromDsl)
      },
      test("nested elements match DSL") {
        val fromHtml = html"<div><p>nested</p></div>"
        val fromDsl  = div(p("nested"))
        assertTrue(fromHtml == fromDsl)
      },
      test("void element matches DSL") {
        val fromHtml = html"<br/>"
        val fromDsl  = br()
        assertTrue(fromHtml == fromDsl)
      },
      test("void element without slash matches DSL") {
        val fromHtml = html"<br>"
        val fromDsl  = br()
        assertTrue(fromHtml == fromDsl)
      },
      test("input with attributes matches DSL") {
        val fromHtml = html"""<input type="text"/>"""
        val fromDsl  = input(`type` := "text")
        assertTrue(fromHtml == fromDsl)
      },
      test("script element matches DSL") {
        val fromHtml = html"<script>alert('hi')</script>"
        val fromDsl  = script("alert('hi')")
        assertTrue(fromHtml == fromDsl)
      },
      test("style element matches DSL") {
        val fromHtml = html"<style>body{color:red}</style>"
        val fromDsl  = style("body{color:red}")
        assertTrue(fromHtml == fromDsl)
      },
      test("script with src attribute matches DSL") {
        val fromHtml = html"""<script src="app.js"></script>"""
        val fromDsl  = script(src := "app.js")
        assertTrue(fromHtml == fromDsl)
      },
      test("multiple children match DSL") {
        val fromHtml = html"<ul><li>a</li><li>b</li></ul>"
        val fromDsl  = ul(li("a"), li("b"))
        assertTrue(fromHtml == fromDsl)
      },
      test("element with boolean attribute matches DSL") {
        val fromHtml = html"<input disabled/>"
        val fromDsl  = input(disabled)
        assertTrue(fromHtml == fromDsl)
      },
      test("mixed text and elements match DSL") {
        val fromHtml = html"<p>hello <strong>world</strong></p>"
        val fromDsl  = p("hello ", strong("world"))
        assertTrue(fromHtml == fromDsl)
      },
      test("meta void element matches DSL") {
        val fromHtml = html"""<meta charset="utf-8">"""
        val fromDsl  = meta(charset := "utf-8")
        assertTrue(fromHtml == fromDsl)
      },
      test("img void element matches DSL") {
        val fromHtml = html"""<img src="photo.jpg" alt="Photo">"""
        val fromDsl  = img(src := "photo.jpg", alt := "Photo")
        assertTrue(fromHtml == fromDsl)
      },
      test("deeply nested matches DSL") {
        val fromHtml = html"<div><ul><li>item</li></ul></div>"
        val fromDsl  = div(ul(li("item")))
        assertTrue(fromHtml == fromDsl)
      },
      test("script does not parse inner HTML") {
        val fromHtml = html"<script>if (a < b) { alert('<b>hi</b>') }</script>"
        val fromDsl  = script("if (a < b) { alert('<b>hi</b>') }")
        assertTrue(fromHtml == fromDsl)
      },
      test("multiple top-level elements") {
        val fromHtml = html"<p>one</p><p>two</p>"
        val fromDsl  = Dom.Fragment(Vector(p("one"), p("two")))
        assertTrue(fromHtml == fromDsl)
      },
      test("interpolated value inside element") {
        val name     = "World"
        val fromHtml = html"<p>Hello ${name}</p>"
        assertTrue(fromHtml.render == "<p>Hello World</p>")
      }
    )
  )
}
