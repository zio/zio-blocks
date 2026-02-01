package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._

object RendererSpec extends MarkdownBaseSpec {
  def spec = suite("Renderer")(
    suite("Blocks")(
      test("renders heading H1") {
        val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))
        assertTrue(Renderer.render(doc) == "# Hello\n")
      },
      test("renders heading H2") {
        val doc = Doc(Chunk(Heading(HeadingLevel.H2, Chunk(Text("Title")))))
        assertTrue(Renderer.render(doc) == "## Title\n")
      },
      test("renders heading H6") {
        val doc = Doc(Chunk(Heading(HeadingLevel.H6, Chunk(Text("Small")))))
        assertTrue(Renderer.render(doc) == "###### Small\n")
      },
      test("renders heading with multiple inlines") {
        val doc =
          Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello "), Strong(Chunk(Text("world")))))))
        assertTrue(Renderer.render(doc) == "# Hello **world**\n")
      },
      test("renders paragraph") {
        val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello world")))))
        assertTrue(Renderer.render(doc) == "Hello world\n\n")
      },
      test("renders multiple paragraphs") {
        val doc = Doc(
          Chunk(
            Paragraph(Chunk(Text("First"))),
            Paragraph(Chunk(Text("Second")))
          )
        )
        assertTrue(Renderer.render(doc) == "First\n\nSecond\n\n")
      },
      test("renders code block without info") {
        val doc = Doc(Chunk(CodeBlock(None, "val x = 1")))
        assertTrue(Renderer.render(doc) == "```\nval x = 1\n```\n")
      },
      test("renders code block with info") {
        val doc = Doc(Chunk(CodeBlock(Some("scala"), "val x = 1")))
        assertTrue(Renderer.render(doc) == "```scala\nval x = 1\n```\n")
      },
      test("renders code block with multiple lines") {
        val doc = Doc(Chunk(CodeBlock(Some("scala"), "val x = 1\nval y = 2")))
        assertTrue(Renderer.render(doc) == "```scala\nval x = 1\nval y = 2\n```\n")
      },
      test("renders thematic break") {
        val doc = Doc(Chunk(ThematicBreak))
        assertTrue(Renderer.render(doc) == "---\n")
      },
      test("renders block quote single line") {
        val doc = Doc(Chunk(BlockQuote(Chunk(Paragraph(Chunk(Text("quote")))))))
        assertTrue(Renderer.render(doc) == "> quote\n>\n")
      },
      test("renders block quote multiple lines") {
        val doc = Doc(
          Chunk(
            BlockQuote(
              Chunk(
                Paragraph(Chunk(Text("line 1"))),
                Paragraph(Chunk(Text("line 2")))
              )
            )
          )
        )
        assertTrue(Renderer.render(doc) == "> line 1\n>\n> line 2\n>\n")
      },
      test("renders bullet list tight") {
        val doc = Doc(
          Chunk(
            BulletList(
              Chunk(
                ListItem(Chunk(Paragraph(Chunk(Text("item 1")))), None),
                ListItem(Chunk(Paragraph(Chunk(Text("item 2")))), None)
              ),
              tight = true
            )
          )
        )
        assertTrue(Renderer.render(doc) == "- item 1\n- item 2\n")
      },
      test("renders bullet list loose") {
        val doc = Doc(
          Chunk(
            BulletList(
              Chunk(
                ListItem(Chunk(Paragraph(Chunk(Text("item 1")))), None),
                ListItem(Chunk(Paragraph(Chunk(Text("item 2")))), None)
              ),
              tight = false
            )
          )
        )
        assertTrue(Renderer.render(doc) == "- item 1\n\n- item 2\n\n")
      },
      test("renders ordered list") {
        val doc = Doc(
          Chunk(
            OrderedList(
              1,
              Chunk(
                ListItem(Chunk(Paragraph(Chunk(Text("first")))), None),
                ListItem(Chunk(Paragraph(Chunk(Text("second")))), None)
              ),
              tight = true
            )
          )
        )
        assertTrue(Renderer.render(doc) == "1. first\n2. second\n")
      },
      test("renders ordered list with start number") {
        val doc = Doc(
          Chunk(
            OrderedList(
              5,
              Chunk(
                ListItem(Chunk(Paragraph(Chunk(Text("fifth")))), None),
                ListItem(Chunk(Paragraph(Chunk(Text("sixth")))), None)
              ),
              tight = true
            )
          )
        )
        assertTrue(Renderer.render(doc) == "5. fifth\n6. sixth\n")
      },
      test("renders task list unchecked") {
        val doc = Doc(
          Chunk(
            BulletList(
              Chunk(
                ListItem(Chunk(Paragraph(Chunk(Text("unchecked")))), Some(false)),
                ListItem(Chunk(Paragraph(Chunk(Text("checked")))), Some(true))
              ),
              tight = true
            )
          )
        )
        assertTrue(Renderer.render(doc) == "- [ ] unchecked\n- [x] checked\n")
      },
      test("renders task list checked") {
        val doc = Doc(
          Chunk(
            BulletList(
              Chunk(
                ListItem(Chunk(Paragraph(Chunk(Text("done")))), Some(true))
              ),
              tight = true
            )
          )
        )
        assertTrue(Renderer.render(doc) == "- [x] done\n")
      },
      test("renders html block") {
        val doc = Doc(Chunk(HtmlBlock("<div>test</div>")))
        assertTrue(Renderer.render(doc) == "<div>test</div>\n")
      },
      test("renders table simple") {
        val doc = Doc(
          Chunk(
            Table(
              TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")))),
              Chunk(Alignment.Left, Alignment.Right),
              Chunk(TableRow(Chunk(Chunk(Text("1")), Chunk(Text("2")))))
            )
          )
        )
        assertTrue(Renderer.render(doc) == "| a | b |\n|:---|---:|\n| 1 | 2 |\n")
      },
      test("renders table with center alignment") {
        val doc = Doc(
          Chunk(
            Table(
              TableRow(Chunk(Chunk(Text("x")), Chunk(Text("y")))),
              Chunk(Alignment.Center, Alignment.None),
              Chunk(TableRow(Chunk(Chunk(Text("1")), Chunk(Text("2")))))
            )
          )
        )
        assertTrue(Renderer.render(doc) == "| x | y |\n|:--:|---|\n| 1 | 2 |\n")
      },
      test("renders table multiple rows") {
        val doc = Doc(
          Chunk(
            Table(
              TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")))),
              Chunk(Alignment.Left, Alignment.Right),
              Chunk(
                TableRow(Chunk(Chunk(Text("1")), Chunk(Text("2")))),
                TableRow(Chunk(Chunk(Text("3")), Chunk(Text("4"))))
              )
            )
          )
        )
        assertTrue(
          Renderer.render(doc) == "| a | b |\n|:---|---:|\n| 1 | 2 |\n| 3 | 4 |\n"
        )
      }
    ),
    suite("Inlines")(
      test("renders text") {
        val doc = Doc(Chunk(Paragraph(Chunk(Text("hello")))))
        assertTrue(Renderer.render(doc) == "hello\n\n")
      },
      test("renders emphasis") {
        val doc = Doc(Chunk(Paragraph(Chunk(Emphasis(Chunk(Text("em")))))))
        assertTrue(Renderer.render(doc) == "*em*\n\n")
      },
      test("renders strong") {
        val doc = Doc(Chunk(Paragraph(Chunk(Strong(Chunk(Text("bold")))))))
        assertTrue(Renderer.render(doc) == "**bold**\n\n")
      },
      test("renders strikethrough") {
        val doc = Doc(Chunk(Paragraph(Chunk(Strikethrough(Chunk(Text("strike")))))))
        assertTrue(Renderer.render(doc) == "~~strike~~\n\n")
      },
      test("renders code") {
        val doc = Doc(Chunk(Paragraph(Chunk(Code("x + y")))))
        assertTrue(Renderer.render(doc) == "`x + y`\n\n")
      },
      test("renders link without title") {
        val doc = Doc(Chunk(Paragraph(Chunk(Link(Chunk(Text("text")), "url", None)))))
        assertTrue(Renderer.render(doc) == "[text](url)\n\n")
      },
      test("renders link with title") {
        val doc = Doc(Chunk(Paragraph(Chunk(Link(Chunk(Text("text")), "url", Some("title"))))))
        assertTrue(Renderer.render(doc) == "[text](url \"title\")\n\n")
      },
      test("renders image without title") {
        val doc = Doc(Chunk(Paragraph(Chunk(Image("alt", "url", None)))))
        assertTrue(Renderer.render(doc) == "![alt](url)\n\n")
      },
      test("renders image with title") {
        val doc = Doc(Chunk(Paragraph(Chunk(Image("alt", "url", Some("title"))))))
        assertTrue(Renderer.render(doc) == "![alt](url \"title\")\n\n")
      },
      test("renders html inline") {
        val doc = Doc(Chunk(Paragraph(Chunk(HtmlInline("<br>")))))
        assertTrue(Renderer.render(doc) == "<br>\n\n")
      },
      test("renders soft break") {
        val doc = Doc(Chunk(Paragraph(Chunk(Text("line1"), SoftBreak, Text("line2")))))
        assertTrue(Renderer.render(doc) == "line1\nline2\n\n")
      },
      test("renders hard break") {
        val doc = Doc(Chunk(Paragraph(Chunk(Text("line1"), HardBreak, Text("line2")))))
        assertTrue(Renderer.render(doc) == "line1  \nline2\n\n")
      },
      test("renders autolink url") {
        val doc = Doc(Chunk(Paragraph(Chunk(Autolink("http://example.com", false)))))
        assertTrue(Renderer.render(doc) == "<http://example.com>\n\n")
      },
      test("renders autolink email") {
        val doc = Doc(Chunk(Paragraph(Chunk(Autolink("test@example.com", true)))))
        assertTrue(Renderer.render(doc) == "<test@example.com>\n\n")
      },
      test("renders mixed inlines") {
        val doc = Doc(
          Chunk(
            Paragraph(
              Chunk(
                Text("Hello "),
                Strong(Chunk(Text("world"))),
                Text(" with "),
                Code("code"),
                Text(".")
              )
            )
          )
        )
        assertTrue(Renderer.render(doc) == "Hello **world** with `code`.\n\n")
      },
      test("renders nested emphasis") {
        val doc = Doc(
          Chunk(
            Paragraph(
              Chunk(
                Emphasis(Chunk(Text("outer "), Strong(Chunk(Text("inner")))))
              )
            )
          )
        )
        assertTrue(Renderer.render(doc) == "*outer **inner***\n\n")
      }
    ),
    suite("Inlines - Inline.* versions")(
      test("renders Inline.Text") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Text("hello")))))
        assertTrue(Renderer.render(doc) == "hello\n\n")
      },
      test("renders Inline.Code") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Code("x + y")))))
        assertTrue(Renderer.render(doc) == "`x + y`\n\n")
      },
      test("renders Inline.Emphasis") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Emphasis(Chunk(Inline.Text("em")))))))
        assertTrue(Renderer.render(doc) == "*em*\n\n")
      },
      test("renders Inline.Strong") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Strong(Chunk(Inline.Text("bold")))))))
        assertTrue(Renderer.render(doc) == "**bold**\n\n")
      },
      test("renders Inline.Strikethrough") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Strikethrough(Chunk(Inline.Text("strike")))))))
        assertTrue(Renderer.render(doc) == "~~strike~~\n\n")
      },
      test("renders Inline.Link without title") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Link(Chunk(Inline.Text("text")), "url", None)))))
        assertTrue(Renderer.render(doc) == "[text](url)\n\n")
      },
      test("renders Inline.Link with title") {
        val doc = Doc(
          Chunk(Paragraph(Chunk(Inline.Link(Chunk(Inline.Text("text")), "url", Some("title")))))
        )
        assertTrue(Renderer.render(doc) == "[text](url \"title\")\n\n")
      },
      test("renders Inline.Image without title") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Image("alt", "url", None)))))
        assertTrue(Renderer.render(doc) == "![alt](url)\n\n")
      },
      test("renders Inline.Image with title") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Image("alt", "url", Some("title"))))))
        assertTrue(Renderer.render(doc) == "![alt](url \"title\")\n\n")
      },
      test("renders Inline.HtmlInline") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.HtmlInline("<br>")))))
        assertTrue(Renderer.render(doc) == "<br>\n\n")
      },
      test("renders Inline.SoftBreak") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Text("line1"), Inline.SoftBreak, Inline.Text("line2")))))
        assertTrue(Renderer.render(doc) == "line1\nline2\n\n")
      },
      test("renders Inline.HardBreak") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Text("line1"), Inline.HardBreak, Inline.Text("line2")))))
        assertTrue(Renderer.render(doc) == "line1  \nline2\n\n")
      },
      test("renders Inline.Autolink url") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Autolink("http://example.com", false)))))
        assertTrue(Renderer.render(doc) == "<http://example.com>\n\n")
      },
      test("renders Inline.Autolink email") {
        val doc = Doc(Chunk(Paragraph(Chunk(Inline.Autolink("test@example.com", true)))))
        assertTrue(Renderer.render(doc) == "<test@example.com>\n\n")
      }
    ),
    suite("Complex documents")(
      test("renders document with multiple block types") {
        val doc = Doc(
          Chunk(
            Heading(HeadingLevel.H1, Chunk(Text("Title"))),
            Paragraph(Chunk(Text("Introduction"))),
            CodeBlock(Some("scala"), "val x = 1"),
            BulletList(
              Chunk(
                ListItem(Chunk(Paragraph(Chunk(Text("item")))), None)
              ),
              tight = true
            )
          )
        )
        assertTrue(
          Renderer.render(doc) == "# Title\nIntroduction\n\n```scala\nval x = 1\n```\n- item\n"
        )
      }
    )
  )
}
