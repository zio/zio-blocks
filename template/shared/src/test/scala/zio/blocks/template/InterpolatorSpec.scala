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
    ),
    suite("parseHtml edge cases")(
      test("empty input") {
        val result = InterpolatorRuntime.parseHtml("")
        assertTrue(result.isEmpty)
      },
      test("text only input") {
        val result = InterpolatorRuntime.parseHtml("just text")
        assertTrue(result == Vector(Dom.Text("just text")))
      },
      test("malformed closing tag without >") {
        val result = InterpolatorRuntime.parseHtml("</div")
        assertTrue(result == Vector(Dom.Text("</div")))
      },
      test("DOCTYPE is skipped") {
        val result = InterpolatorRuntime.parseHtml("<!DOCTYPE html><p>ok</p>")
        assertTrue(result == Vector(Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("ok")))))
      },
      test("HTML comment is skipped") {
        val result = InterpolatorRuntime.parseHtml("<!-- comment --><div>x</div>")
        assertTrue(result == Vector(Dom.Element.Generic("div", Vector.empty, Vector(Dom.Text("x")))))
      },
      test("malformed DOCTYPE without >") {
        val result = InterpolatorRuntime.parseHtml("<!DOCTYPE html")
        assertTrue(result == Vector(Dom.Text("<!DOCTYPE html")))
      },
      test("processing instruction is skipped") {
        val result = InterpolatorRuntime.parseHtml("<?xml version='1.0'?><p>ok</p>")
        assertTrue(result == Vector(Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("ok")))))
      },
      test("invalid tag name starting with number") {
        val result = InterpolatorRuntime.parseHtml("<3tag>text</3tag>")
        assertTrue(result.head == Dom.Text("<"))
      },
      test("script without closing tag") {
        val result = InterpolatorRuntime.parseHtml("<script>var x = 1")
        assertTrue(result == Vector(Dom.Element.Script(Vector.empty, Vector(Dom.Text("var x = 1")))))
      },
      test("style without closing tag") {
        val result = InterpolatorRuntime.parseHtml("<style>.cls{color:red}")
        assertTrue(result == Vector(Dom.Element.Style(Vector.empty, Vector(Dom.Text(".cls{color:red}")))))
      },
      test("script with empty content") {
        val result = InterpolatorRuntime.parseHtml("<script></script>")
        assertTrue(result == Vector(Dom.Element.Script(Vector.empty, Vector.empty)))
      },
      test("unclosed element at end of input") {
        val result = InterpolatorRuntime.parseHtml("<div>content")
        assertTrue(result == Vector(Dom.Element.Generic("div", Vector.empty, Vector(Dom.Text("content")))))
      },
      test("multiple unclosed elements at end") {
        val result = InterpolatorRuntime.parseHtml("<div><span>text")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector.empty,
              Vector(Dom.Element.Generic("span", Vector.empty, Vector(Dom.Text("text"))))
            )
          )
        )
      },
      test("stray closing tag is ignored") {
        val result = InterpolatorRuntime.parseHtml("</nonexistent><p>ok</p>")
        assertTrue(result == Vector(Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("ok")))))
      },
      test("mismatched closing tag closes inner elements") {
        val result = InterpolatorRuntime.parseHtml("<div><span><b>x</b></div>")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector.empty,
              Vector(
                Dom.Element.Generic(
                  "span",
                  Vector.empty,
                  Vector(Dom.Element.Generic("b", Vector.empty, Vector(Dom.Text("x"))))
                )
              )
            )
          )
        )
      },
      test("single-quoted attribute value") {
        val result = InterpolatorRuntime.parseHtml("<div class='foo'>bar</div>")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("foo"))),
              Vector(Dom.Text("bar"))
            )
          )
        )
      },
      test("unquoted attribute value") {
        val result = InterpolatorRuntime.parseHtml("<div id=main>text</div>")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))),
              Vector(Dom.Text("text"))
            )
          )
        )
      },
      test("attribute with whitespace around equals") {
        val result = InterpolatorRuntime.parseHtml("<div id = \"main\">text</div>")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))),
              Vector(Dom.Text("text"))
            )
          )
        )
      },
      test("tag at end of input without >") {
        val result = InterpolatorRuntime.parseHtml("<div")
        assertTrue(result == Vector(Dom.Element.Generic("div", Vector.empty, Vector.empty)))
      },
      test("tag with attr at end of input without >") {
        val result = InterpolatorRuntime.parseHtml("<div class")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector(Dom.Attribute.BooleanAttribute("class")),
              Vector.empty
            )
          )
        )
      },
      test("tag with attr value at end of input") {
        val result = InterpolatorRuntime.parseHtml("<div class=\"x\"")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("x"))),
              Vector.empty
            )
          )
        )
      },
      test("attribute equals at end of input") {
        val result = InterpolatorRuntime.parseHtml("<div class=")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector(Dom.Attribute.BooleanAttribute("class")),
              Vector.empty
            )
          )
        )
      },
      test("second attribute at end of input without >") {
        val result = InterpolatorRuntime.parseHtml("<div id=\"x\" class")
        assertTrue(
          result == Vector(
            Dom.Element.Generic(
              "div",
              Vector(
                Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x")),
                Dom.Attribute.BooleanAttribute("class")
              ),
              Vector.empty
            )
          )
        )
      },
      test("self-closing with space before />") {
        val result = InterpolatorRuntime.parseHtml("<br />")
        assertTrue(result == Vector(Dom.Element.Generic("br", Vector.empty, Vector.empty)))
      },
      test("bare < treated as text") {
        val result = InterpolatorRuntime.parseHtml("a < b")
        assertTrue(result.nonEmpty, result.head.isInstanceOf[Dom.Text])
      }
    )
  )
}
