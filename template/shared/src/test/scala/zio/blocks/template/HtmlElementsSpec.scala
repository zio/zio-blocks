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
      test("void elements self-close") {
        assertTrue(
          br().render == "<br/>",
          hr().render == "<hr/>"
        )
      },
      test("input as void element") {
        assertTrue(input().render == "<input/>")
      },
      test("img as void element with attributes") {
        val result = img(src := "pic.png", alt := "photo").render
        assertTrue(
          result.startsWith("<img"),
          result.contains("src=\"pic.png\""),
          result.contains("alt=\"photo\""),
          result.endsWith("/>")
        )
      }
    ),
    suite("boolean attributes")(
      test("required as boolean attribute via PartialAttribute") {
        assertTrue(input(required).render == "<input required/>")
      },
      test("disabled as boolean attribute") {
        assertTrue(input(disabled).render == "<input disabled/>")
      },
      test("required with := true") {
        assertTrue(input(required := true).render == "<input required/>")
      },
      test("required with := false omits attribute") {
        assertTrue(input(required := false).render == "<input/>")
      }
    ),
    suite("attribute helpers")(
      test("class attribute via className") {
        assertTrue(div(className := "container").render == "<div class=\"container\"></div>")
      },
      test("href attribute") {
        assertTrue(
          a(href := "https://example.com", "link").render == "<a href=\"https://example.com\">link</a>"
        )
      },
      test("multi-value attribute via Vector") {
        val result = div(className := Vector("a", "b", "c")).render
        assertTrue(result == "<div class=\"a b c\"></div>")
      },
      test("multi-value attribute via varargs tuple") {
        val result = div(className.:=("a", "b", "c")).render
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
      },
      test("Iterable[Modifier] applies all") {
        val mods: Iterable[Modifier] = List(
          Modifier.stringToModifier("a"),
          Modifier.stringToModifier("b")
        )
        assertTrue(div(mods).render == "<div>ab</div>")
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
    ),
    suite("script and style elements")(
      test("script() returns Script type") {
        val s: Dom.Element.Script = script()
        assertTrue(s.tag == "script")
      },
      test("style() returns Style type") {
        val s: Dom.Element.Style = style()
        assertTrue(s.tag == "style")
      },
      test("script renders JS without escaping") {
        assertTrue(script("var x = 1 < 2;").render == "<script>var x = 1 < 2;</script>")
      },
      test("style renders CSS without escaping") {
        assertTrue(style("div > p { color: red; }").render == "<style>div > p { color: red; }</style>")
      },
      test("script with attributes") {
        assertTrue(script(src := "app.js").render == """<script src="app.js"></script>""")
      },
      test("script inlineJs convenience") {
        val s = script().inlineJs("alert('hello')")
        assertTrue(s.render == "<script>alert('hello')</script>")
      },
      test("script externalJs convenience") {
        val s = script().externalJs("app.js")
        assertTrue(s.render == """<script src="app.js"></script>""")
      },
      test("style inlineCss convenience") {
        val s = style().inlineCss("body { margin: 0; }")
        assertTrue(s.render == "<style>body { margin: 0; }</style>")
      },
      test("script inlineJs with Js type") {
        val s = script().inlineJs(Js("console.log(1)"))
        assertTrue(s.render == "<script>console.log(1)</script>")
      },
      test("style inlineCss with Css type") {
        val s = style().inlineCss(Css("a > b { color: blue; }"))
        assertTrue(s.render == "<style>a > b { color: blue; }</style>")
      }
    ),
    suite("raw, fragment, empty helpers")(
      test("raw renders unescaped HTML") {
        assertTrue(raw("<b>bold</b>").render == "<b>bold</b>")
      },
      test("fragment combines children") {
        assertTrue(fragment(Dom.Text("a"), Dom.Text("b")).render == "ab")
      },
      test("empty renders nothing") {
        assertTrue(empty.render == "")
      }
    ),
    suite("ARIA multi-value attributes")(
      test("ariaDescribedby with multiple values") {
        val result = div(ariaDescribedby.:=("desc1", "desc2")).render
        assertTrue(result == "<div aria-describedby=\"desc1 desc2\"></div>")
      },
      test("ariaLabelledby with multiple values") {
        val result = div(ariaLabelledby.:=("label1", "label2")).render
        assertTrue(result == "<div aria-labelledby=\"label1 label2\"></div>")
      },
      test("ariaDescribedby with single value") {
        val result = div(ariaDescribedby := "desc1").render
        assertTrue(result == "<div aria-describedby=\"desc1\"></div>")
      }
    ),
    suite("multiAttr helpers")(
      test("multiAttr creates multi-value attribute") {
        val cls    = multiAttr("class")
        val result = div(cls("container", "fluid")).render
        assertTrue(result == "<div class=\"container fluid\"></div>")
      },
      test("multiAttr with custom separator") {
        val styles = multiAttr("style", Dom.AttributeSeparator.Semicolon)
        val result = div(styles("color: red", "font-size: 14px")).render
        assertTrue(result == "<div style=\"color: red;font-size: 14px\"></div>")
      }
    ),
    suite("Element apply for curried modifiers")(
      test("form with apply for additional modifiers") {
        val f      = form(action := "/submit")
        val result = f(Modifier.domToModifier(div("content")), Modifier.domToModifier(button("Submit")))
        assertTrue(
          result.render.contains("action=\"/submit\""),
          result.render.contains("<div>content</div>"),
          result.render.contains("<button>Submit</button>")
        )
      }
    )
  )
}
