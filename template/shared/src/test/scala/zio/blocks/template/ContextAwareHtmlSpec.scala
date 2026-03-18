package zio.blocks.template

import zio.test._

object ContextAwareHtmlSpec extends ZIOSpecDefault {
  def spec = suite("Context-aware html interpolator and boolean attr split")(
    suite("html interpolator - tag name position")(
      test("element in tag position renders element") {
        val result = html"<${div()}>content</${div()}>"
        assertTrue(result.render == "<div>content</div>")
      },
      test("dynamic heading level") {
        val level   = 2
        val heading = if (level == 1) h1() else h2()
        val result  = html"<$heading>Title</$heading>"
        assertTrue(result.render == "<h2>Title</h2>")
      },
      test("section element in tag position") {
        val result = html"<${section()}>content</${section()}>"
        assertTrue(result.render == "<section>content</section>")
      },
      test("SafeTagName in tag position still works") {
        val tag    = SafeTagName("div").get
        val result = html"<$tag>content</$tag>"
        assertTrue(result.render == "<div>content</div>")
      }
    ),
    suite("html interpolator - attr name position")(
      test("SafeAttrName in attr name position") {
        val attrName = SafeAttrName("class").get
        val result   = html"""<div $attrName="test">content</div>"""
        assertTrue(
          result.render == """<div class="test">content</div>"""
        )
      },
      test("SafeAttrName id in attr name position") {
        val attrName = SafeAttrName("id").get
        val result   = html"""<span $attrName="main">text</span>"""
        assertTrue(
          result.render == """<span id="main">text</span>"""
        )
      },
      test("EventAttrName in attr name position") {
        val attrName = EventAttrName("onclick").get
        val result   = html"""<button $attrName="handler()">Click</button>"""
        assertTrue(
          result.render == """<button onclick="handler()">Click</button>"""
        )
      }
    ),
    suite("html interpolator - attr value position")(
      test("string in attr value position is substituted") {
        val cls    = "my-class"
        val result = html"""<div class="$cls">content</div>"""
        assertTrue(result.render == """<div class="my-class">content</div>""")
      },
      test("int in attr value position is substituted") {
        val width  = 100
        val result = html"""<div tabindex="$width">content</div>"""
        assertTrue(result.render == """<div tabindex="100">content</div>""")
      },
      test("boolean in attr value position") {
        val flag   = true
        val result = html"""<div hidden="$flag">content</div>"""
        assertTrue(result.render == """<div hidden="true">content</div>""")
      }
    ),
    suite("html interpolator - content position")(
      test("string in content position is escaped") {
        val text   = "Hello <world>"
        val result = html"<p>$text</p>"
        assertTrue(result.render == "<p>Hello &lt;world&gt;</p>")
      },
      test("Dom element in content position is embedded") {
        val child  = div("inner")
        val result = html"<section>$child</section>"
        assertTrue(result.render == "<section><div>inner</div></section>")
      },
      test("int in content position is rendered as text") {
        val num    = 42
        val result = html"<span>$num</span>"
        assertTrue(result.render == "<span>42</span>")
      }
    ),
    suite("html interpolator - multiple positions")(
      test("tag + attr value + content in one template") {
        val tag     = section()
        val cls     = "main"
        val content = "Hello"
        val result  = html"""<$tag class="$cls">$content</$tag>"""
        assertTrue(
          result.render == """<section class="main">Hello</section>"""
        )
      },
      test("attr name + attr value in one template") {
        val attrName = SafeAttrName("id").get
        val attrVal  = "container"
        val result   = html"""<div $attrName="$attrVal">text</div>"""
        assertTrue(
          result.render == """<div id="container">text</div>"""
        )
      },
      test("multiple content interpolations") {
        val a      = "first"
        val b      = "second"
        val result = html"<div>$a and $b</div>"
        assertTrue(
          result.render == "<div>first and second</div>"
        )
      }
    ),
    suite("boolean attributes are Modifiers")(
      test("disabled is a Modifier (BooleanAttribute)") {
        val result = div(disabled)
        assertTrue(result.render == "<div disabled></div>")
      },
      test("checked is a Modifier (BooleanAttribute)") {
        val result = input(checked)
        assertTrue(result.render == "<input checked/>")
      },
      test("required is a Modifier (BooleanAttribute)") {
        val result = input(required)
        assertTrue(result.render == "<input required/>")
      },
      test("hidden is a Modifier (BooleanAttribute)") {
        val result = div(hidden)
        assertTrue(result.render == "<div hidden></div>")
      },
      test("readonly is a Modifier (BooleanAttribute)") {
        val result = input(readonly)
        assertTrue(result.render == "<input readonly/>")
      },
      test("selected is a Modifier (BooleanAttribute)") {
        val result = option(selected)
        assertTrue(result.render == "<option selected></option>")
      },
      test("multiple is a Modifier (BooleanAttribute)") {
        val result = select(multiple)
        assertTrue(result.render == "<select multiple></select>")
      },
      test("autofocus is a Modifier (BooleanAttribute)") {
        val result = input(autofocus)
        assertTrue(result.render == "<input autofocus/>")
      },
      test("open is a Modifier (BooleanAttribute)") {
        val result = details(open)
        assertTrue(result.render == "<details open></details>")
      },
      test("reversed is a Modifier (BooleanAttribute)") {
        val result = ol(reversed)
        assertTrue(result.render == "<ol reversed></ol>")
      },
      test("defer is a Modifier (BooleanAttribute)") {
        val result = script(defer)
        assertTrue(result.render == "<script defer></script>")
      },
      test("async is a Modifier (BooleanAttribute)") {
        val result = script(async)
        assertTrue(result.render == "<script async></script>")
      }
    ),
    suite("key-value attributes via :=")(
      test("id := produces Modifier via implicit") {
        val result = div(id := "main")
        assertTrue(result.render == """<div id="main"></div>""")
      },
      test("className := produces Modifier via implicit") {
        val result = div(className := "container")
        assertTrue(result.render == """<div class="container"></div>""")
      },
      test("type := on input") {
        val result = input(`type` := "text")
        assertTrue(result.render == """<input type="text"/>""")
      }
    ),
    suite("mixing boolean and key-value attributes")(
      test("boolean + key-value attrs on same element") {
        val result   = button(disabled, id := "save", `type` := "submit")
        val rendered = result.render
        assertTrue(
          rendered == """<button disabled id="save" type="submit"></button>"""
        )
      },
      test("multiple boolean attrs on same element") {
        val result   = input(disabled, required, readonly)
        val rendered = result.render
        assertTrue(
          rendered == "<input disabled required readonly/>"
        )
      },
      test("boolean attrs + content on same element") {
        val result   = button(disabled, "Click me")
        val rendered = result.render
        assertTrue(
          rendered == "<button disabled>Click me</button>"
        )
      }
    )
  )
}
