package zio.blocks.template

import zio.test._

object ContextAwareHtmlSpec extends ZIOSpecDefault {
  def spec = suite("Context-aware html interpolator and boolean attr split")(
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
      test("attr value + content in one template") {
        val cls     = "main"
        val content = "Hello"
        val result  = html"""<section class="$cls">$content</section>"""
        assertTrue(
          result.render == """<section class="main">Hello</section>"""
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
    suite("boolean attributes have ToModifier instances")(
      test("disabled is usable via ToModifier (BooleanAttribute)") {
        val result = div(disabled)
        assertTrue(result.render == "<div disabled></div>")
      },
      test("checked is usable via ToModifier (BooleanAttribute)") {
        val result = input(checked)
        assertTrue(result.render == "<input checked/>")
      },
      test("required is usable via ToModifier (BooleanAttribute)") {
        val result = input(required)
        assertTrue(result.render == "<input required/>")
      },
      test("hidden is usable via ToModifier (BooleanAttribute)") {
        val result = div(hidden)
        assertTrue(result.render == "<div hidden></div>")
      },
      test("readonly is usable via ToModifier (BooleanAttribute)") {
        val result = input(readonly)
        assertTrue(result.render == "<input readonly/>")
      },
      test("selected is usable via ToModifier (BooleanAttribute)") {
        val result = option(selected)
        assertTrue(result.render == "<option selected></option>")
      },
      test("multiple is usable via ToModifier (BooleanAttribute)") {
        val result = select(multiple)
        assertTrue(result.render == "<select multiple></select>")
      },
      test("autofocus is usable via ToModifier (BooleanAttribute)") {
        val result = input(autofocus)
        assertTrue(result.render == "<input autofocus/>")
      },
      test("open is usable via ToModifier (BooleanAttribute)") {
        val result = details(open)
        assertTrue(result.render == "<details open></details>")
      },
      test("reversed is usable via ToModifier (BooleanAttribute)") {
        val result = ol(reversed)
        assertTrue(result.render == "<ol reversed></ol>")
      },
      test("defer is usable via ToModifier (BooleanAttribute)") {
        val result = script(defer)
        assertTrue(result.render == "<script defer></script>")
      },
      test("async is usable via ToModifier (BooleanAttribute)") {
        val result = script(async)
        assertTrue(result.render == "<script async></script>")
      }
    ),
    suite("key-value attributes via :=")(
      test("id := produces Attribute via ToModifier") {
        val result = div(id := "main")
        assertTrue(result.render == """<div id="main"></div>""")
      },
      test("className := produces Attribute via ToModifier") {
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
