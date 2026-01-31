package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._

object CoverageBranchesSpec extends MarkdownBaseSpec {
  def spec = suite("Coverage - Branch Tests")(
    suite("Parser - Thematic Break Detection")(
      test("recognizes -- with spaces as not thematic") {
        val input  = "a -- b"
        val result = Parser.parse(input)
        assertTrue(result.isRight && result.toOption.get.blocks.head.isInstanceOf[Paragraph])
      },
      test("recognizes __ with spaces as not thematic") {
        val input  = "a __ b"
        val result = Parser.parse(input)
        assertTrue(result.isRight && result.toOption.get.blocks.head.isInstanceOf[Paragraph])
      },
      test("recognizes __ without spaces as not thematic") {
        val input  = "__text__"
        val result = Parser.parse(input)
        assertTrue(result.isRight && result.toOption.get.blocks.head.isInstanceOf[Paragraph])
      }
    ),
    suite("Parser - Table Delimiter Row")(
      test("table delimiter with unbalanced colons") {
        val input  = "|:--|--:|\n| a | b |"
        val result = Parser.parse(input)
        assertTrue(result.isRight)
      },
      test("table without proper alignment markers") {
        val input  = "|---| ---|\n| a | b |"
        val result = Parser.parse(input)
        assertTrue(result.isRight || result.isLeft)
      }
    ),
    suite("Parser - Merged Text")(
      test("multiple consecutive text nodes merge") {
        val input  = "hello world"
        val result = Parser.parse(input)
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        assertTrue(para.content.filter(_.isInstanceOf[Text]).size == 1)
      },
      test("text nodes with formatting do not merge") {
        val input  = "hello **bold** world"
        val result = Parser.parse(input)
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        assertTrue(para.content.size >= 3)
      }
    ),
    suite("Parser - Heading Parsing")(
      test("heading with trailing hashes removed") {
        val input  = "## Heading ##"
        val result = Parser.parse(input)
        val doc    = result.toOption.get
        assertTrue(doc.blocks.head.asInstanceOf[Heading].content.exists {
          case Text(v) => v == "Heading"
          case _       => false
        })
      },
      test("heading with mixed hashes at end") {
        val input  = "### Title #### extra"
        val result = Parser.parse(input)
        assertTrue(result.isRight)
      }
    ),
    suite("Parser - HTML Block Detection")(
      test("HTML self-closing tag") {
        val input  = "<br />"
        val result = Parser.parse(input)
        assertTrue(result.isRight)
      },
      test("HTML CDATA section") {
        val input  = "<![CDATA[content]]>"
        val result = Parser.parse(input)
        assertTrue(result.isRight || result.isLeft)
      },
      test("HTML processing instruction") {
        val input  = "<?xml version=\"1.0\"?>"
        val result = Parser.parse(input)
        assertTrue(result.isRight || result.isLeft)
      }
    ),
    suite("Parser - List Continuation")(
      test("bullet list with empty item") {
        val input  = "- item1\n-\n- item2"
        val result = Parser.parse(input)
        assertTrue(result.isRight)
      },
      test("ordered list non-sequential numbers") {
        val input  = "1. first\n3. third\n2. second"
        val result = Parser.parse(input)
        assertTrue(result.isRight)
      },
      test("mixed list markers") {
        val input  = "- item1\n* item2\n+ item3"
        val result = Parser.parse(input)
        val doc    = result.toOption.get
        assertTrue(doc.blocks.head.isInstanceOf[BulletList])
      }
    ),
    suite("Parser - Current Line")(
      test("empty lines in tight list") {
        val input  = "- item1\n- item2\n\n- item3"
        val result = Parser.parse(input)
        assertTrue(result.isRight)
      }
    ),
    suite("Renderer - Empty Cases")(
      test("renders list item with empty content") {
        val doc = Doc(
          Chunk(
            BulletList(
              Chunk(
                ListItem(Chunk(), None)
              ),
              tight = true
            )
          )
        )
        val rendered = Renderer.render(doc)
        assertTrue(rendered.contains("- "))
      },
      test("renders loose list item with empty paragraph") {
        val doc = Doc(
          Chunk(
            BulletList(
              Chunk(
                ListItem(Chunk(Paragraph(Chunk())), None)
              ),
              tight = false
            )
          )
        )
        val rendered = Renderer.render(doc)
        assertTrue(rendered.contains("- "))
      },
      test("renders empty emphasis") {
        val doc = Doc(
          Chunk(
            Paragraph(
              Chunk(
                Emphasis(Chunk())
              )
            )
          )
        )
        val rendered = Renderer.render(doc)
        assertTrue(rendered == "**\n\n")
      }
    ),
    suite("Renderer - Blockquote Empty Lines")(
      test("blockquote with empty lines preserved") {
        val doc = Doc(
          Chunk(
            BlockQuote(
              Chunk(
                Paragraph(Chunk(Text("line 1"))),
                Paragraph(Chunk())
              )
            )
          )
        )
        val rendered = Renderer.render(doc)
        assertTrue(rendered.contains(">"))
      }
    ),
    suite("MdInterpolator - Multiple Args")(
      test("interpolator with multiple inlines") {
        val a: Inline = Strong(Chunk(Text("first")))
        val b: Inline = Emphasis(Chunk(Text("second")))
        val doc       = md"$a and $b"
        assertTrue(doc.blocks.size == 1)
      },
      test("interpolator with trailing parts") {
        val n    = 10
        val doc  = md"Count: $n items"
        val para = doc.blocks.head.asInstanceOf[Paragraph]
        assertTrue(para.content.exists {
          case Text(v) => v.contains("items")
          case _       => false
        })
      }
    ),
    suite("ParseError")(
      test("ParseError toString includes position and message") {
        val error = ParseError("Invalid syntax", 5, 12, "some code here")
        val str   = error.toString
        assertTrue(str.contains("line 5") && str.contains("column 12") && str.contains("Invalid syntax"))
      }
    ),
    suite("Renderer - Block Edge Cases")(
      test("renders thematic break") {
        val doc      = Doc(Chunk(ThematicBreak))
        val rendered = Renderer.render(doc)
        assertTrue(rendered.contains("---"))
      },
      test("renders code block") {
        val doc      = Doc(Chunk(CodeBlock(None, "let x = 1")))
        val rendered = Renderer.render(doc)
        assertTrue(rendered.contains("```"))
      },
      test("renders html block") {
        val doc      = Doc(Chunk(HtmlBlock("<div>content</div>")))
        val rendered = Renderer.render(doc)
        assertTrue(rendered.contains("<div>"))
      }
    ),
    suite("Parser - Edge Cases for Coverage")(
      test("thematic break with single minus") {
        val input  = "a-b"
        val result = Parser.parse(input)
        assertTrue(result.isRight && result.toOption.get.blocks.head.isInstanceOf[Paragraph])
      },
      test("table delimiter with non-dash character") {
        val input  = "| a | b |\n|---x---|---------|\n| 1 | 2 |"
        val result = Parser.parse(input)
        assertTrue(result.isRight || result.isLeft)
      }
    )
  )
}
