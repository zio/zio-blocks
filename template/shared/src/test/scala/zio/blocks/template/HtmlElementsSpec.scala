package zio.blocks.template

import zio.test._

object HtmlElementsSpec extends ZIOSpecDefault {
  def spec = suite("HtmlElements")(
    suite("element constructors")(
      test("div renders empty div") {
        assertTrue(div().render == "<div></div>")
      },
      test("div with string modifier") {
        assertTrue(div("hello").render == "<div>hello</div>")
      },
      test("div with attribute") {
        assertTrue(div(id := "main").render == "<div id=\"main\"></div>")
      },
      test("div with multiple attributes and content") {
        val result = div(id := "main", className := "box", "content").render
        assertTrue(
          result.contains("id=\"main\""),
          result.contains("class=\"box\""),
          result.contains("content"),
          result.startsWith("<div"),
          result.endsWith("</div>")
        )
      },
      test("nested elements") {
        assertTrue(ul(li("one"), li("two")).render == "<ul><li>one</li><li>two</li></ul>")
      },
      test("void elements render without closing tag") {
        assertTrue(
          br().render == "<br>",
          hr().render == "<hr>"
        )
      },
      test("input as void element") {
        assertTrue(input().render == "<input>")
      },
      test("img as void element with attributes") {
        val result = img(src := "pic.png", alt := "photo").render
        assertTrue(
          result.startsWith("<img"),
          result.contains("src=\"pic.png\""),
          result.contains("alt=\"photo\""),
          !result.contains("</img>")
        )
      }
    ),
    suite("boolean attributes")(
      test("required as boolean attribute via PartialAttribute") {
        assertTrue(input(required).render == "<input required>")
      },
      test("disabled as boolean attribute") {
        assertTrue(input(disabled).render == "<input disabled>")
      },
      test("required with := true") {
        assertTrue(input(required := true).render == "<input required>")
      },
      test("required with := false omits attribute") {
        assertTrue(input(required := false).render == "<input>")
      }
    ),
    suite("attribute helpers")(
      test("class attribute via className") {
        assertTrue(div(className := "container").render == "<div class=\"container\"></div>")
      },
      test("href attribute") {
        assertTrue(a(href := "https://example.com", "link").render == "<a href=\"https://example.com\">link</a>")
      },
      test("multi-value attribute via Vector") {
        val result = div(className := Vector("a", "b", "c")).render
        assertTrue(result == "<div class=\"a b c\"></div>")
      }
    ),
    suite("modifiers")(
      test("Dom as modifier adds child") {
        val child  = Dom.Text("child")
        val result = div(Modifier.domToModifier(child)).render
        assertTrue(result == "<div>child</div>")
      },
      test("Option[Modifier] None is no-op") {
        val none: Option[Modifier] = None
        assertTrue(div(none).render == "<div></div>")
      },
      test("Option[Modifier] Some applies modifier") {
        val some: Option[Modifier] = Some(Modifier.stringToModifier("text"))
        assertTrue(div(some).render == "<div>text</div>")
      }
    ),
    suite("various elements")(
      test("h1 through h6") {
        assertTrue(
          h1("Title").render == "<h1>Title</h1>",
          h2("Sub").render == "<h2>Sub</h2>",
          h3("Sub2").render == "<h3>Sub2</h3>"
        )
      },
      test("p element") {
        assertTrue(p("paragraph").render == "<p>paragraph</p>")
      },
      test("span element") {
        assertTrue(span("inline").render == "<span>inline</span>")
      },
      test("table structure") {
        val result = table(tr(td("cell"))).render
        assertTrue(result == "<table><tr><td>cell</td></tr></table>")
      }
    )
  )
}
