package zio.blocks.docs

import zio.test._

object RoundTripSpec extends MarkdownBaseSpec {
  def spec = suite("Round-trip")(
    test("heading round-trips") {
      val input    = "# Hello\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("heading H2 round-trips") {
      val input    = "## Title\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("paragraph round-trips") {
      val input    = "Hello world\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("code block round-trips") {
      val input    = "```scala\nval x = 1\n```\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("code block without info round-trips") {
      val input    = "```\nval x = 1\n```\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("thematic break round-trips") {
      val input    = "---\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("bullet list round-trips") {
      val input = """- item 1
                    |- item 2
                    |""".stripMargin
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("ordered list round-trips") {
      val input = """1. first
                    |2. second
                    |""".stripMargin
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("bold text round-trips") {
      val input    = "**bold**\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("italic text round-trips") {
      val input    = "*italic*\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("strikethrough text round-trips") {
      val input    = "~~strikethrough~~\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("inline code round-trips") {
      val input    = "`code`\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("link round-trips") {
      val input    = "[text](url)\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("link with title round-trips") {
      val input    = "[text](url \"title\")\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("image round-trips") {
      val input    = "![alt](url)\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("image with title round-trips") {
      val input    = "![alt](url \"title\")\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("autolink round-trips") {
      val input    = "<http://example.com>\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("autolink email round-trips") {
      val input    = "<test@example.com>\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("hard break round-trips") {
      val input    = "line1  \nline2\n\n"
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("complex document round-trips") {
      val input    = """# Title

Some **bold** and *italic* text.

```scala
val x = 1
```

- item 1
- item 2
"""
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("block quote round-trips") {
      val input = """> quote
                    |
                    |""".stripMargin
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    },
    test("table round-trips") {
      val input = """| a | b |
                     ||:---|---:|
                     || 1 | 2 |
                     |""".stripMargin
      val parsed   = Parser.parse(input)
      val rendered = parsed.map(Renderer.render)
      val reparsed = rendered.flatMap(Parser.parse)
      assertTrue(parsed == reparsed)
    }
  )
}
