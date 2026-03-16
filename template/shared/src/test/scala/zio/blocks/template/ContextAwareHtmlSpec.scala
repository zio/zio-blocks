package zio.blocks.template

import zio.test._

object ContextAwareHtmlSpec extends ZIOSpecDefault {
  def spec = suite("Context-aware html interpolator and boolean attr split")(
    suite("html interpolator - tag name position")(
      test("SafeTagName in tag position renders element") {
        val tag    = SafeTagName("div").get
        val result = html"<$tag>content</$tag>"
        assertTrue(result.render == "<div>content</div>")
      },
      test("SafeTagName section in tag position") {
        val tag    = SafeTagName("section").get
        val result = html"<$tag>hello</$tag>"
        assertTrue(result.render == "<section>hello</section>")
      },
      test("SafeTagName in tag position with static attribute") {
        val tag    = SafeTagName("div").get
        val result = html"""<$tag class="main">text</$tag>"""
        assertTrue(
          result.render.contains("<div"),
          result.render.contains("""class="main""""),
          result.render.contains("text"),
          result.render.contains("</div>")
        )
      }
    ),
    suite("html interpolator - attr name position")(
      test("SafeAttrName in attr name position") {
        val attrName = SafeAttrName("class").get
        val result   = html"""<div $attrName="test">content</div>"""
        assertTrue(
          result.render.contains("""class="test""""),
          result.render.contains("content")
        )
      },
      test("SafeAttrName id in attr name position") {
        val attrName = SafeAttrName("id").get
        val result   = html"""<span $attrName="main">text</span>"""
        assertTrue(
          result.render.contains("""id="main""""),
          result.render.contains("text")
        )
      },
      test("EventAttrName in attr name position") {
        val attrName = EventAttrName("onclick").get
        val result   = html"""<button $attrName="handler()">Click</button>"""
        assertTrue(
          result.render.contains("""onclick="handler()""""),
          result.render.contains("Click")
        )
      }
    ),
    suite("html interpolator - attr value position")(
      test("string in attr value position is substituted") {
        val cls    = "my-class"
        val result = html"""<div class="$cls">content</div>"""
        assertTrue(result.render.contains("my-class"))
      },
      test("int in attr value position is substituted") {
        val width  = 100
        val result = html"""<div tabindex="$width">content</div>"""
        assertTrue(result.render.contains("100"))
      },
      test("boolean in attr value position") {
        val flag   = true
        val result = html"""<div hidden="$flag">content</div>"""
        assertTrue(result.render.contains("true"))
      }
    ),
    suite("html interpolator - content position")(
      test("string in content position is escaped") {
        val text   = "Hello <world>"
        val result = html"<p>$text</p>"
        assertTrue(result.render.contains("Hello &lt;world&gt;"))
      },
      test("Dom element in content position is embedded") {
        val child  = div("inner")
        val result = html"<section>$child</section>"
        assertTrue(result.render.contains("<div>inner</div>"))
      },
      test("int in content position is rendered as text") {
        val num    = 42
        val result = html"<span>$num</span>"
        assertTrue(result.render.contains("42"))
      }
    ),
    suite("html interpolator - multiple positions")(
      test("tag + attr value + content in one template") {
        val tag     = SafeTagName("section").get
        val cls     = "main"
        val content = "Hello"
        val result  = html"""<$tag class="$cls">$content</$tag>"""
        assertTrue(
          result.render.contains("<section"),
          result.render.contains("main"),
          result.render.contains("Hello"),
          result.render.contains("</section>")
        )
      },
      test("attr name + attr value in one template") {
        val attrName = SafeAttrName("id").get
        val attrVal  = "container"
        val result   = html"""<div $attrName="$attrVal">text</div>"""
        assertTrue(
          result.render.contains("""id="container""""),
          result.render.contains("text")
        )
      },
      test("multiple content interpolations") {
        val a      = "first"
        val b      = "second"
        val result = html"<div>$a and $b</div>"
        assertTrue(
          result.render.contains("first"),
          result.render.contains("second")
        )
      }
    ),
    suite("boolean attributes are Modifiers")(
      test("disabled is a Modifier (BooleanAttribute)") {
        val result = div(disabled)
        assertTrue(result.render.contains("disabled"))
      },
      test("checked is a Modifier (BooleanAttribute)") {
        val result = input(checked)
        assertTrue(result.render.contains("checked"))
      },
      test("required is a Modifier (BooleanAttribute)") {
        val result = input(required)
        assertTrue(result.render.contains("required"))
      },
      test("hidden is a Modifier (BooleanAttribute)") {
        val result = div(hidden)
        assertTrue(result.render.contains("hidden"))
      },
      test("readonly is a Modifier (BooleanAttribute)") {
        val result = input(readonly)
        assertTrue(result.render.contains("readonly"))
      },
      test("selected is a Modifier (BooleanAttribute)") {
        val result = option(selected)
        assertTrue(result.render.contains("selected"))
      },
      test("multiple is a Modifier (BooleanAttribute)") {
        val result = select(multiple)
        assertTrue(result.render.contains("multiple"))
      },
      test("autofocus is a Modifier (BooleanAttribute)") {
        val result = input(autofocus)
        assertTrue(result.render.contains("autofocus"))
      },
      test("open is a Modifier (BooleanAttribute)") {
        val result = details(open)
        assertTrue(result.render.contains("open"))
      },
      test("reversed is a Modifier (BooleanAttribute)") {
        val result = ol(reversed)
        assertTrue(result.render.contains("reversed"))
      },
      test("defer is a Modifier (BooleanAttribute)") {
        val result = script(defer)
        assertTrue(result.render.contains("defer"))
      },
      test("async is a Modifier (BooleanAttribute)") {
        val result = script(async)
        assertTrue(result.render.contains("async"))
      }
    ),
    suite("key-value attributes via :=")(
      test("id := produces Modifier via implicit") {
        val result = div(id := "main")
        assertTrue(result.render.contains("""id="main""""))
      },
      test("className := produces Modifier via implicit") {
        val result = div(className := "container")
        assertTrue(result.render.contains("""class="container""""))
      },
      test("type := on input") {
        val result = input(`type` := "text")
        assertTrue(result.render.contains("""type="text""""))
      }
    ),
    suite("mixing boolean and key-value attributes")(
      test("boolean + key-value attrs on same element") {
        val result   = button(disabled, id := "save", `type` := "submit")
        val rendered = result.render
        assertTrue(
          rendered.contains("disabled"),
          rendered.contains("""id="save""""),
          rendered.contains("""type="submit""")
        )
      },
      test("multiple boolean attrs on same element") {
        val result   = input(disabled, required, readonly)
        val rendered = result.render
        assertTrue(
          rendered.contains("disabled"),
          rendered.contains("required"),
          rendered.contains("readonly")
        )
      },
      test("boolean attrs + content on same element") {
        val result   = button(disabled, "Click me")
        val rendered = result.render
        assertTrue(
          rendered.contains("disabled"),
          rendered.contains("Click me")
        )
      }
    )
  )
}
