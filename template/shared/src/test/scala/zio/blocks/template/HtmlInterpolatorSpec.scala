package zio.blocks.template

import zio.blocks.chunk.Chunk
import zio.test._

object HtmlInterpolatorSpec extends ZIOSpecDefault {
  def spec = suite("HtmlInterpolatorSpec")(
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
        val fromDsl  = br
        assertTrue(fromHtml == fromDsl)
      },
      test("void element without slash matches DSL") {
        val fromHtml = html"<br>"
        val fromDsl  = br
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
      test("multiple top-level elements throw error") {
        val result = scala.util.Try(html"<p>one</p><p>two</p>")
        assertTrue(
          result.isFailure,
          result.failed.get.isInstanceOf[IllegalArgumentException],
          result.failed.get.getMessage.contains("single root element")
        )
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
        assertTrue(result == Chunk(Dom.Text("just text")))
      },
      test("malformed closing tag without >") {
        val result = InterpolatorRuntime.parseHtml("</div")
        assertTrue(result == Chunk(Dom.Text("</div")))
      },
      test("DOCTYPE is skipped") {
        val result = InterpolatorRuntime.parseHtml("<!DOCTYPE html><p>ok</p>")
        assertTrue(result == Chunk(Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("ok")))))
      },
      test("HTML comment is skipped") {
        val result = InterpolatorRuntime.parseHtml("<!-- comment --><div>x</div>")
        assertTrue(result == Chunk(Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("x")))))
      },
      test("malformed DOCTYPE without >") {
        val result = InterpolatorRuntime.parseHtml("<!DOCTYPE html")
        assertTrue(result == Chunk(Dom.Text("<!DOCTYPE html")))
      },
      test("processing instruction is skipped") {
        val result = InterpolatorRuntime.parseHtml("<?xml version='1.0'?><p>ok</p>")
        assertTrue(result == Chunk(Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("ok")))))
      },
      test("invalid tag name starting with number") {
        val result = InterpolatorRuntime.parseHtml("<3tag>text</3tag>")
        assertTrue(result.head == Dom.Text("<"))
      },
      test("script without closing tag") {
        val result = InterpolatorRuntime.parseHtml("<script>var x = 1")
        assertTrue(result == Chunk(Dom.Element.Script(Chunk.empty, Chunk(Dom.Text("var x = 1")))))
      },
      test("style without closing tag") {
        val result = InterpolatorRuntime.parseHtml("<style>.cls{color:red}")
        assertTrue(result == Chunk(Dom.Element.Style(Chunk.empty, Chunk(Dom.Text(".cls{color:red}")))))
      },
      test("script with empty content") {
        val result = InterpolatorRuntime.parseHtml("<script></script>")
        assertTrue(result == Chunk(Dom.Element.Script(Chunk.empty, Chunk.empty)))
      },
      test("unclosed element at end of input") {
        val result = InterpolatorRuntime.parseHtml("<div>content")
        assertTrue(result == Chunk(Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("content")))))
      },
      test("multiple unclosed elements at end") {
        val result = InterpolatorRuntime.parseHtml("<div><span>text")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk.empty,
              Chunk(Dom.Element.Generic("span", Chunk.empty, Chunk(Dom.Text("text"))))
            )
          )
        )
      },
      test("stray closing tag is ignored") {
        val result = InterpolatorRuntime.parseHtml("</nonexistent><p>ok</p>")
        assertTrue(result == Chunk(Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("ok")))))
      },
      test("mismatched closing tag closes inner elements") {
        val result = InterpolatorRuntime.parseHtml("<div><span><b>x</b></div>")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk.empty,
              Chunk(
                Dom.Element.Generic(
                  "span",
                  Chunk.empty,
                  Chunk(Dom.Element.Generic("b", Chunk.empty, Chunk(Dom.Text("x"))))
                )
              )
            )
          )
        )
      },
      test("single-quoted attribute value") {
        val result = InterpolatorRuntime.parseHtml("<div class='foo'>bar</div>")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("foo"))),
              Chunk(Dom.Text("bar"))
            )
          )
        )
      },
      test("unquoted attribute value") {
        val result = InterpolatorRuntime.parseHtml("<div id=main>text</div>")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))),
              Chunk(Dom.Text("text"))
            )
          )
        )
      },
      test("attribute with whitespace around equals") {
        val result = InterpolatorRuntime.parseHtml("<div id = \"main\">text</div>")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))),
              Chunk(Dom.Text("text"))
            )
          )
        )
      },
      test("tag at end of input without >") {
        val result = InterpolatorRuntime.parseHtml("<div")
        assertTrue(result == Chunk(Dom.Element.Generic("div", Chunk.empty, Chunk.empty)))
      },
      test("tag with attr at end of input without >") {
        val result = InterpolatorRuntime.parseHtml("<div class")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk(Dom.Attribute.BooleanAttribute("class")),
              Chunk.empty
            )
          )
        )
      },
      test("tag with attr value at end of input") {
        val result = InterpolatorRuntime.parseHtml("<div class=\"x\"")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("x"))),
              Chunk.empty
            )
          )
        )
      },
      test("attribute equals at end of input") {
        val result = InterpolatorRuntime.parseHtml("<div class=")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk(Dom.Attribute.BooleanAttribute("class")),
              Chunk.empty
            )
          )
        )
      },
      test("second attribute at end of input without >") {
        val result = InterpolatorRuntime.parseHtml("<div id=\"x\" class")
        assertTrue(
          result == Chunk(
            Dom.Element.Generic(
              "div",
              Chunk(
                Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x")),
                Dom.Attribute.BooleanAttribute("class")
              ),
              Chunk.empty
            )
          )
        )
      },
      test("self-closing with space before />") {
        val result = InterpolatorRuntime.parseHtml("<br />")
        assertTrue(result == Chunk(Dom.Element.Generic("br", Chunk.empty, Chunk.empty)))
      },
      test("bare < treated as text") {
        val result = InterpolatorRuntime.parseHtml("a < b")
        assertTrue(result.nonEmpty, result.head.isInstanceOf[Dom.Text])
      },
      test("whitespace-only input") {
        val result = InterpolatorRuntime.parseHtml("   ")
        assertTrue(result == Chunk(Dom.Text("   ")))
      },
      test("HTML comment at end of input") {
        val result = InterpolatorRuntime.parseHtml("text<!-- comment -->")
        assertTrue(result == Chunk(Dom.Text("text")))
      },
      test("HTML comment without closing") {
        val result = InterpolatorRuntime.parseHtml("<!-- unclosed")
        assertTrue(result == Chunk(Dom.Text("<!-- unclosed")))
      },
      test("processing instruction without closing") {
        val result = InterpolatorRuntime.parseHtml("<?xml version='1.0'")
        assertTrue(result == Chunk(Dom.Text("<?xml version='1.0'")))
      },
      test("nested script element with inner tags") {
        val result = InterpolatorRuntime.parseHtml("<script>var x = '<b>bold</b>';</script>")
        assertTrue(result == Chunk(Dom.Element.Script(Chunk.empty, Chunk(Dom.Text("var x = '<b>bold</b>';")))))
      },
      test("nested style element with inner selectors") {
        val result = InterpolatorRuntime.parseHtml("<style>div > p { color: red; }</style>")
        assertTrue(result == Chunk(Dom.Element.Style(Chunk.empty, Chunk(Dom.Text("div > p { color: red; }")))))
      },
      test("multiple attributes on element") {
        val result = InterpolatorRuntime.parseHtml("""<div id="main" class="container">x</div>""")
        assertTrue(
          result.length == 1,
          result.head.isInstanceOf[Dom.Element],
          result.head.asInstanceOf[Dom.Element].attributes.length == 2
        )
      },
      test("self-closing tag with attributes") {
        val result = InterpolatorRuntime.parseHtml("""<img src="a.png" alt="photo"/>""")
        assertTrue(
          result.length == 1,
          result.head.isInstanceOf[Dom.Element],
          result.head.asInstanceOf[Dom.Element].tag == "img",
          result.head.asInstanceOf[Dom.Element].attributes.length == 2
        )
      },
      test("tag with tab and newline whitespace in attributes") {
        val result = InterpolatorRuntime.parseHtml("<div\tid=\"x\"\nclass=\"y\">z</div>")
        assertTrue(
          result.head.asInstanceOf[Dom.Element].attributes.length == 2,
          result.head.asInstanceOf[Dom.Element].children.length == 1
        )
      },
      test("multiple sibling elements at top level") {
        val result = InterpolatorRuntime.parseHtml("<p>a</p><p>b</p><p>c</p>")
        assertTrue(result.length == 3)
      },
      test("empty element pairs") {
        val result = InterpolatorRuntime.parseHtml("<div></div>")
        assertTrue(result == Chunk(Dom.Element.Generic("div", Chunk.empty, Chunk.empty)))
      },
      test("adjacent text and elements") {
        val result = InterpolatorRuntime.parseHtml("before<span>mid</span>after")
        assertTrue(result.length == 3)
      },
      test("script with attributes and content") {
        val result = InterpolatorRuntime.parseHtml("""<script type="module">import x;</script>""")
        val s      = result.head.asInstanceOf[Dom.Element.Script]
        assertTrue(
          s.attributes.length == 1,
          s.children == Chunk(Dom.Text("import x;"))
        )
      },
      test("style with no content has empty children") {
        val result = InterpolatorRuntime.parseHtml("<style></style>")
        assertTrue(result == Chunk(Dom.Element.Style(Chunk.empty, Chunk.empty)))
      },
      test("closing tag for mismatched element is handled gracefully") {
        val result = InterpolatorRuntime.parseHtml("<p>text</div>")
        assertTrue(result.nonEmpty)
      },
      test("attribute with empty value") {
        val result = InterpolatorRuntime.parseHtml("""<div id="">x</div>""")
        val el     = result.head.asInstanceOf[Dom.Element]
        assertTrue(
          el.attributes.length == 1,
          el.attributes(0) == Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue(""))
        )
      },
      test("slash in attribute area without prior tag close") {
        val result = InterpolatorRuntime.parseHtml("<br/>")
        assertTrue(result == Chunk(Dom.Element.Generic("br", Chunk.empty, Chunk.empty)))
      },
      test("multiple boolean attributes") {
        val result = InterpolatorRuntime.parseHtml("<input disabled required/>")
        val el     = result.head.asInstanceOf[Dom.Element]
        assertTrue(el.attributes.length == 2)
      },
      test("case-insensitive closing tag") {
        val result = InterpolatorRuntime.parseHtml("<SCRIPT>code</SCRIPT>")
        assertTrue(
          result.head.isInstanceOf[Dom.Element.Script],
          result.head.asInstanceOf[Dom.Element.Script].children == Chunk(Dom.Text("code"))
        )
      }
    ),
    suite("buildHtml edge cases")(
      test("buildHtml with Left(attrValue) argument") {
        val sc     = new StringContext("<div id=\"", "\">text</div>")
        val args   = Seq(Left("main"): Either[String, Chunk[Dom]])
        val result = InterpolatorRuntime.buildHtml(sc, args)
        assertTrue(result.render == "<div id=\"main\">text</div>")
      },
      test("buildHtml returning Empty when input is empty") {
        val sc     = new StringContext("")
        val result = InterpolatorRuntime.buildHtml(sc, Seq.empty)
        assertTrue(result == Dom.Empty)
      },
      test("buildHtml with multiple top-level elements throws error") {
        val sc     = new StringContext("<p>a</p><p>b</p>")
        val result = scala.util.Try(InterpolatorRuntime.buildHtml(sc, Seq.empty))
        assertTrue(
          result.isFailure,
          result.failed.get.isInstanceOf[IllegalArgumentException],
          result.failed.get.getMessage.contains("single root element")
        )
      },
      test("buildHtml with Right(chunk) content substitution") {
        val sc     = new StringContext("<div>", "</div>")
        val child  = Chunk[Dom](Dom.Text("injected"))
        val args   = Seq(Right(child): Either[String, Chunk[Dom]])
        val result = InterpolatorRuntime.buildHtml(sc, args)
        assertTrue(result.render == "<div>injected</div>")
      }
    ),
    suite("parseHtml additional edge cases")(
      test("sentinel without closing sentinel treats rest as text") {
        val input  = "\u0000\u0001text"
        val result = InterpolatorRuntime.parseHtml(input)
        assertTrue(result.nonEmpty)
      },
      test("tag name only at end of input") {
        val result = InterpolatorRuntime.parseHtml("<abc")
        assertTrue(result == Chunk(Dom.Element.Generic("abc", Chunk.empty, Chunk.empty)))
      },
      test("tag starting with < followed by non-alpha") {
        val result = InterpolatorRuntime.parseHtml("< text>")
        // Parser treats '<' as text when followed by space
        assertTrue(result.nonEmpty)
      },
      test("multiple attributes including unquoted") {
        val result = InterpolatorRuntime.parseHtml("<div id=main class=\"box\" hidden>x</div>")
        val el     = result.head.asInstanceOf[Dom.Element]
        assertTrue(el.attributes.length == 3)
      },
      test("attribute name runs to end of input without = or >") {
        val result = InterpolatorRuntime.parseHtml("<div data")
        val el     = result.head.asInstanceOf[Dom.Element]
        assertTrue(el.attributes.length == 1)
      },
      test("second attr name runs to end of input") {
        val result = InterpolatorRuntime.parseHtml("<div id=\"x\" data")
        val el     = result.head.asInstanceOf[Dom.Element]
        assertTrue(el.attributes.length == 2)
      }
    )
  )
}
