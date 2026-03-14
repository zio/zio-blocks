package zio.blocks.template

import zio.test._

object OptimizationEquivalenceSpec extends ZIOSpecDefault {
  def spec = suite("Optimization Equivalence")(
    suite("macro-optimized Tag.apply produces correct output")(
      test("div with single string child") {
        val result = div("hello").render
        assertTrue(result == "<div>hello</div>")
      },
      test("div with single attribute") {
        val result = div(id := "main").render
        assertTrue(result == """<div id="main"></div>""")
      },
      test("div with multiple attributes and text") {
        val result = div(id := "main", className := "box", "text").render
        assertTrue(
          result.contains("id=\"main\""),
          result.contains("class=\"box\""),
          result.contains("text"),
          result.startsWith("<div"),
          result.endsWith("</div>")
        )
      },
      test("nested elements") {
        val inner  = p("inner")
        val result = div(inner).render
        assertTrue(result == "<div><p>inner</p></div>")
      },
      test("multiple children in ul/li") {
        val li1    = li("one")
        val li2    = li("two")
        val result = ul(li1, li2).render
        assertTrue(result == "<ul><li>one</li><li>two</li></ul>")
      },
      test("void element br with className") {
        val result = br(className := "spacer").render
        assertTrue(result == """<br class="spacer"/>""")
      },
      test("void element hr with id") {
        val result = hr(id := "separator").render
        assertTrue(result == """<hr id="separator"/>""")
      },
      test("img with src attribute") {
        val result = img(src := "x.png").render
        assertTrue(result == """<img src="x.png"/>""")
      },
      test("img with multiple attributes") {
        val result = img(src := "photo.jpg", alt := "A photo").render
        assertTrue(
          result.contains("src=\"photo.jpg\""),
          result.contains("alt=\"A photo\""),
          result.startsWith("<img"),
          result.endsWith("/>")
        )
      },
      test("span with string child") {
        val result = span("inline text").render
        assertTrue(result == "<span>inline text</span>")
      },
      test("h1 with string child") {
        val result = h1("Title").render
        assertTrue(result == "<h1>Title</h1>")
      },
      test("a with href and text") {
        val result = a(href := "https://example.com", "link").render
        assertTrue(result == """<a href="https://example.com">link</a>""")
      },
      test("deeply nested elements") {
        val innerLi = li("item")
        val innerUl = ul(innerLi)
        val result  = div(innerUl).render
        assertTrue(result == "<div><ul><li>item</li></ul></div>")
      },
      test("input with type attribute") {
        val result = input(`type` := "text").render
        assertTrue(result == """<input type="text"/>""")
      },
      test("table structure") {
        val cell   = td("data")
        val row    = tr(cell)
        val result = table(row).render
        assertTrue(result == "<table><tr><td>data</td></tr></table>")
      },
      test("form with action attribute and children") {
        val btn    = button("Submit")
        val result = form(action := "/submit", btn).render
        assertTrue(
          result.contains("action=\"/submit\""),
          result.contains("<button>Submit</button>")
        )
      },
      test("element with boolean attribute") {
        val result = input(disabled).render
        assertTrue(result == "<input disabled/>")
      },
      test("multiple string children") {
        val result = p("hello ", "world").render
        assertTrue(result == "<p>hello world</p>")
      }
    ),
    suite("constant-folded interpolators")(
      test("css literal matches Css.Raw") {
        val interpolated = css"margin: 10px".render
        val direct       = Css.Raw("margin: 10px").render
        assertTrue(interpolated == direct)
      },
      test("css multi-property literal matches Css.Raw") {
        val interpolated = css"color: red; font-size: 14px".render
        val direct       = Css.Raw("color: red; font-size: 14px").render
        assertTrue(interpolated == direct)
      },
      test("js literal matches Js constructor") {
        val interpolated = js"console.log('hi')".value
        val direct       = Js("console.log('hi')").value
        assertTrue(interpolated == direct)
      },
      test("js multi-statement literal matches Js constructor") {
        val interpolated = js"var x = 1; var y = 2".value
        val direct       = Js("var x = 1; var y = 2").value
        assertTrue(interpolated == direct)
      },
      test("selector literal matches CssSelector.Raw") {
        val interpolated = selector".my-class".render
        val direct       = CssSelector.Raw(".my-class").render
        assertTrue(interpolated == direct)
      },
      test("selector element literal matches CssSelector.Raw") {
        val interpolated = selector"div.active".render
        val direct       = CssSelector.Raw("div.active").render
        assertTrue(interpolated == direct)
      },
      test("selector id literal matches CssSelector.Raw") {
        val interpolated = selector"#main-content".render
        val direct       = CssSelector.Raw("#main-content").render
        assertTrue(interpolated == direct)
      }
    )
  )
}
