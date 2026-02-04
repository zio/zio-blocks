package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._

object TerminalRendererSpec extends MarkdownBaseSpec {

  private val Reset       = "\u001b[0m"
  private val Bold        = "\u001b[1m"
  private val Italic      = "\u001b[3m"
  private val Underline   = "\u001b[4m"
  private val StrikeStyle = "\u001b[9m"
  private val Red         = "\u001b[31m"
  private val Green       = "\u001b[32m"
  private val Yellow      = "\u001b[33m"
  private val Blue        = "\u001b[34m"
  private val Magenta     = "\u001b[35m"
  private val Cyan        = "\u001b[36m"
  private val GrayBg      = "\u001b[48;5;236m"

  override def spec = suite("TerminalRendererSpec")(
    suite("render - Full document")(
      test("empty document") {
        val doc    = Doc(Chunk.empty)
        val result = TerminalRenderer.render(doc)
        assertTrue(result == "")
      },
      test("document with multiple blocks") {
        val doc = Doc(
          Chunk(
            Heading(HeadingLevel.H1, Chunk(Text("Title"))),
            Paragraph(Chunk(Text("Content")))
          )
        )
        val result   = TerminalRenderer.render(doc)
        val expected = s"${Bold}${Red}Title${Reset}\n\nContent\n\n"
        assertTrue(result == expected)
      }
    ),
    suite("Headings")(
      test("H1 renders with bold and red") {
        val block  = Heading(HeadingLevel.H1, Chunk(Text("Heading 1")))
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == s"${Bold}${Red}Heading 1${Reset}\n\n")
      },
      test("H2 renders with bold and yellow") {
        val block  = Heading(HeadingLevel.H2, Chunk(Text("Heading 2")))
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == s"${Bold}${Yellow}Heading 2${Reset}\n\n")
      },
      test("H3 renders with bold and green") {
        val block  = Heading(HeadingLevel.H3, Chunk(Text("Heading 3")))
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == s"${Bold}${Green}Heading 3${Reset}\n\n")
      },
      test("H4 renders with bold and cyan") {
        val block  = Heading(HeadingLevel.H4, Chunk(Text("Heading 4")))
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == s"${Bold}${Cyan}Heading 4${Reset}\n\n")
      },
      test("H5 renders with bold and blue") {
        val block  = Heading(HeadingLevel.H5, Chunk(Text("Heading 5")))
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == s"${Bold}${Blue}Heading 5${Reset}\n\n")
      },
      test("H6 renders with bold and magenta") {
        val block  = Heading(HeadingLevel.H6, Chunk(Text("Heading 6")))
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == s"${Bold}${Magenta}Heading 6${Reset}\n\n")
      }
    ),
    suite("Inline styles")(
      test("Strong renders as bold") {
        val inline = Strong(Chunk(Text("bold")))
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Bold}bold${Reset}")
      },
      test("Emphasis renders as italic") {
        val inline = Emphasis(Chunk(Text("italic")))
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Italic}italic${Reset}")
      },
      test("Strikethrough renders with strikethrough") {
        val inline = Strikethrough(Chunk(Text("strike")))
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${StrikeStyle}strike${Reset}")
      },
      test("Code renders with gray background") {
        val inline = Code("code")
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${GrayBg}code${Reset}")
      }
    ),
    suite("Links")(
      test("Link renders with blue underline and URL") {
        val inline = Link(Chunk(Text("click here")), "http://example.com", None)
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Blue}${Underline}click here${Reset} (http://example.com)")
      },
      test("Autolink renders with blue underline") {
        val inline = Autolink("http://example.com", false)
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Blue}${Underline}http://example.com${Reset}")
      },
      test("Email autolink renders with blue underline") {
        val inline = Autolink("user@example.com", true)
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Blue}${Underline}user@example.com${Reset}")
      }
    ),
    suite("Blocks")(
      test("Paragraph") {
        val block  = Paragraph(Chunk(Text("hello")))
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == "hello\n\n")
      },
      test("CodeBlock renders with gray background") {
        val block  = CodeBlock(Some("scala"), "val x = 1")
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == s"${GrayBg}val x = 1${Reset}\n\n")
      },
      test("CodeBlock without language") {
        val block  = CodeBlock(None, "plain")
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == s"${GrayBg}plain${Reset}\n\n")
      },
      test("ThematicBreak renders as horizontal line") {
        val result = TerminalRenderer.renderBlock(ThematicBreak)
        assertTrue(result == s"${"─" * 40}\n\n")
      },
      test("BlockQuote") {
        val block  = BlockQuote(Chunk(Paragraph(Chunk(Text("quoted")))))
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == "│ quoted\n")
      },
      test("BulletList renders with bullets") {
        val block = BulletList(
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("item 1")))), None),
            ListItem(Chunk(Paragraph(Chunk(Text("item 2")))), None)
          ),
          tight = true
        )
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == "• item 1\n• item 2\n")
      },
      test("OrderedList") {
        val block = OrderedList(
          1,
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("first")))), None),
            ListItem(Chunk(Paragraph(Chunk(Text("second")))), None)
          ),
          tight = true
        )
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == "1. first\n2. second\n")
      },
      test("OrderedList with start") {
        val block = OrderedList(
          5,
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("item")))), None)
          ),
          tight = true
        )
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == "5. item\n")
      },
      test("Task list checked item") {
        val block = BulletList(
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("done")))), Some(true))
          ),
          tight = true
        )
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == "[x] done\n")
      },
      test("Task list unchecked item") {
        val block = BulletList(
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("todo")))), Some(false))
          ),
          tight = true
        )
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == "[ ] todo\n")
      },
      test("HtmlBlock passthrough") {
        val block  = HtmlBlock("<div>raw</div>")
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result == "<div>raw</div>\n\n")
      },
      test("Table") {
        val block = Table(
          TableRow(Chunk(Chunk(Text("Name")), Chunk(Text("Age")))),
          Chunk(Alignment.Left, Alignment.Right),
          Chunk(
            TableRow(Chunk(Chunk(Text("Alice")), Chunk(Text("30"))))
          )
        )
        val result = TerminalRenderer.renderBlock(block)
        assertTrue(result.contains("│ Name │ Age │"))
        assertTrue(result.contains("├──────┼───────┤"))
        assertTrue(result.contains("│ Alice │ 30 │"))
      }
    ),
    suite("Edge cases")(
      test("Empty document") {
        val doc    = Doc(Chunk.empty)
        val result = TerminalRenderer.render(doc)
        assertTrue(result == "")
      },
      test("Nested styles") {
        val inline = Strong(Chunk(Emphasis(Chunk(Text("bold italic")))))
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Bold}${Italic}bold italic${Reset}${Reset}")
      },
      test("Image") {
        val inline = Image("alt text", "http://example.com/img.png", None)
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "[Image: alt text] (http://example.com/img.png)")
      },
      test("Image with title") {
        val inline = Image("alt", "http://img.jpg", Some("Title"))
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "[Image: alt] (http://img.jpg)")
      },
      test("HtmlInline passthrough") {
        val inline = HtmlInline("<br/>")
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "<br/>")
      },
      test("SoftBreak") {
        val inline = SoftBreak
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == " ")
      },
      test("HardBreak") {
        val inline = HardBreak
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "\n")
      },
      test("Text") {
        val inline = Text("plain text")
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "plain text")
      },
      test("Inline.Text variant") {
        val inline = Inline.Text("variant text")
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "variant text")
      },
      test("Inline.Code variant") {
        val inline = Inline.Code("variant code")
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${GrayBg}variant code${Reset}")
      },
      test("Inline.Emphasis variant") {
        val inline = Inline.Emphasis(Chunk(Text("variant italic")))
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Italic}variant italic${Reset}")
      },
      test("Inline.Strong variant") {
        val inline = Inline.Strong(Chunk(Text("variant bold")))
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Bold}variant bold${Reset}")
      },
      test("Inline.Strikethrough variant") {
        val inline = Inline.Strikethrough(Chunk(Text("variant strike")))
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${StrikeStyle}variant strike${Reset}")
      },
      test("Inline.Link variant") {
        val inline = Inline.Link(Chunk(Text("link")), "http://url.com", None)
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Blue}${Underline}link${Reset} (http://url.com)")
      },
      test("Inline.Image variant") {
        val inline = Inline.Image("img", "http://img.png", None)
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "[Image: img] (http://img.png)")
      },
      test("Inline.HtmlInline variant") {
        val inline = Inline.HtmlInline("<span>html</span>")
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "<span>html</span>")
      },
      test("Inline.SoftBreak variant") {
        val inline = Inline.SoftBreak
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == " ")
      },
      test("Inline.HardBreak variant") {
        val inline = Inline.HardBreak
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == "\n")
      },
      test("Inline.Autolink variant") {
        val inline = Inline.Autolink("http://auto.com", false)
        val result = TerminalRenderer.renderInline(inline)
        assertTrue(result == s"${Blue}${Underline}http://auto.com${Reset}")
      }
    )
  )
}
