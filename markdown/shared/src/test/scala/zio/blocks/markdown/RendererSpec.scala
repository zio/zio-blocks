/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.markdown

import zio.test._
import zio.blocks.chunk.Chunk

object RendererSpec extends ZIOSpecDefault {

  def spec = suite("RendererSpec")(
    headingRenderSpec,
    paragraphRenderSpec,
    codeBlockRenderSpec,
    listRenderSpec,
    blockQuoteRenderSpec,
    tableRenderSpec,
    inlineRenderSpec,
    roundTripSpec,
    configSpec
  )

  val headingRenderSpec = suite("Heading Rendering")(
    test("renders H1") {
      val doc      = Document(Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("Title"))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered == "# Title\n")
    },
    test("renders H2-H6") {
      val results = (2 to 6).map { level =>
        val doc = Document(Block.Heading(HeadingLevel.unsafeFromInt(level), Chunk(Inline.Text("Heading"))))
        Renderer.render(doc)
      }
      assertTrue(
        results(0) == "## Heading\n",
        results(1) == "### Heading\n",
        results(2) == "#### Heading\n",
        results(3) == "##### Heading\n",
        results(4) == "###### Heading\n"
      )
    },
    test("renders heading with inline formatting") {
      val doc = Document(
        Block.Heading(
          HeadingLevel.H1,
          Chunk(Inline.Text("Hello "), Inline.Strong(Chunk(Inline.Text("world"))))
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(rendered == "# Hello **world**\n")
    }
  )

  val paragraphRenderSpec = suite("Paragraph Rendering")(
    test("renders simple paragraph") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Text("Hello world"))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered == "Hello world\n")
    },
    test("renders multiple paragraphs") {
      val doc = Document(
        Block.Paragraph(Chunk(Inline.Text("First"))),
        Block.Paragraph(Chunk(Inline.Text("Second")))
      )
      val rendered = Renderer.render(doc)
      assertTrue(rendered == "First\n\nSecond\n")
    }
  )

  val codeBlockRenderSpec = suite("Code Block Rendering")(
    test("renders code block without language") {
      val doc      = Document(Block.CodeBlock("val x = 1", None))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("```") && rendered.contains("val x = 1"))
    },
    test("renders code block with language") {
      val doc      = Document(Block.CodeBlock("val x = 1", Some("scala")))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("```scala") && rendered.contains("val x = 1"))
    },
    test("renders multiline code block") {
      val doc      = Document(Block.CodeBlock("line 1\nline 2\nline 3", None))
      val rendered = Renderer.render(doc)
      assertTrue(
        rendered.contains("line 1"),
        rendered.contains("line 2"),
        rendered.contains("line 3")
      )
    }
  )

  val listRenderSpec = suite("List Rendering")(
    test("renders bullet list") {
      val doc = Document(
        Block.BulletList(
          Chunk(
            Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("item 1")))), None),
            Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("item 2")))), None)
          ),
          tight = true
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("- item 1") && rendered.contains("- item 2"))
    },
    test("renders ordered list") {
      val doc = Document(
        Block.OrderedList(
          Chunk(
            Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("first")))), None),
            Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("second")))), None)
          ),
          start = 1,
          tight = true
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("1. first") && rendered.contains("2. second"))
    },
    test("renders ordered list with custom start") {
      val doc = Document(
        Block.OrderedList(
          Chunk(
            Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("fifth")))), None)
          ),
          start = 5,
          tight = true
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("5. fifth"))
    },
    test("renders task list") {
      val doc = Document(
        Block.BulletList(
          Chunk(
            Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("done")))), Some(true)),
            Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("todo")))), Some(false))
          ),
          tight = true
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("[x] done") && rendered.contains("[ ] todo"))
    }
  )

  val blockQuoteRenderSpec = suite("Block Quote Rendering")(
    test("renders simple block quote") {
      val doc = Document(
        Block.BlockQuote(
          Chunk(
            Block.Paragraph(Chunk(Inline.Text("quoted text")))
          )
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("> quoted text"))
    },
    test("renders multi-paragraph block quote") {
      val doc = Document(
        Block.BlockQuote(
          Chunk(
            Block.Paragraph(Chunk(Inline.Text("para 1"))),
            Block.Paragraph(Chunk(Inline.Text("para 2")))
          )
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("> para 1") && rendered.contains("> para 2"))
    }
  )

  val tableRenderSpec = suite("Table Rendering")(
    test("renders simple table") {
      val doc = Document(
        Block.Table(
          Chunk(Block.TableCell(Chunk(Inline.Text("A"))), Block.TableCell(Chunk(Inline.Text("B")))),
          Chunk(Alignment.None, Alignment.None),
          Chunk(
            Chunk(Block.TableCell(Chunk(Inline.Text("1"))), Block.TableCell(Chunk(Inline.Text("2"))))
          )
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(
        rendered.contains("| A | B |"),
        rendered.contains("| 1 | 2 |"),
        rendered.contains("---")
      )
    },
    test("renders table with alignments") {
      val doc = Document(
        Block.Table(
          Chunk(
            Block.TableCell(Chunk(Inline.Text("L"))),
            Block.TableCell(Chunk(Inline.Text("C"))),
            Block.TableCell(Chunk(Inline.Text("R")))
          ),
          Chunk(Alignment.Left, Alignment.Center, Alignment.Right),
          Chunk.empty[Chunk[Block.TableCell]]
        )
      )
      val rendered = Renderer.render(doc)
      assertTrue(
        rendered.contains(":---"),
        rendered.contains(":---:"),
        rendered.contains("---:")
      )
    }
  )

  val inlineRenderSpec = suite("Inline Rendering")(
    test("renders emphasis") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Emphasis(Chunk(Inline.Text("italic"))))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("*italic*"))
    },
    test("renders strong") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Strong(Chunk(Inline.Text("bold"))))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("**bold**"))
    },
    test("renders strikethrough") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Strikethrough(Chunk(Inline.Text("deleted"))))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("~~deleted~~"))
    },
    test("renders inline code") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Code("code"))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("`code`"))
    },
    test("renders inline code with backticks") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Code("`code`"))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("`` `code` ``"))
    },
    test("renders link") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Link(Chunk(Inline.Text("text")), "https://example.com", None))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("[text](https://example.com)"))
    },
    test("renders link with title") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Link(Chunk(Inline.Text("text")), "url", Some("Title")))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("""[text](url "Title")"""))
    },
    test("renders image") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Image("alt", "image.png", None))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("![alt](image.png)"))
    },
    test("renders autolink") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Autolink("https://example.com", isEmail = false))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("<https://example.com>"))
    },
    test("renders hard break") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Text("line 1"), Inline.HardBreak, Inline.Text("line 2"))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("  \n"))
    },
    test("renders soft break") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Text("line 1"), Inline.SoftBreak, Inline.Text("line 2"))))
      val rendered = Renderer.render(doc)
      assertTrue(rendered.contains("\n"))
    }
  )

  val roundTripSpec = suite("Round-Trip")(
    test("heading round-trip") {
      roundTrip("# Hello World")
    },
    test("paragraph round-trip") {
      roundTrip("Hello world")
    },
    test("emphasis round-trip") {
      roundTrip("*emphasized*")
    },
    test("strong round-trip") {
      roundTrip("**bold**")
    },
    test("code span round-trip") {
      roundTrip("`code`")
    },
    test("link round-trip") {
      roundTrip("[text](https://example.com)")
    },
    test("bullet list round-trip") {
      val input    = "- item 1\n- item 2"
      val doc1     = Parser.parseUnsafe(input)
      val rendered = Renderer.render(doc1)
      val doc2     = Parser.parseUnsafe(rendered)
      assertTrue(doc1.blocks.length == doc2.blocks.length)
    },
    test("ordered list round-trip") {
      val input    = "1. first\n2. second"
      val doc1     = Parser.parseUnsafe(input)
      val rendered = Renderer.render(doc1)
      val doc2     = Parser.parseUnsafe(rendered)
      assertTrue(doc1.blocks.length == doc2.blocks.length)
    },
    test("code block round-trip") {
      val input    = "```scala\nval x = 1\n```"
      val doc1     = Parser.parseUnsafe(input)
      val rendered = Renderer.render(doc1)
      val doc2     = Parser.parseUnsafe(rendered)
      (doc1.blocks.head, doc2.blocks.head) match {
        case (Block.CodeBlock(c1, i1), Block.CodeBlock(c2, i2)) =>
          assertTrue(c1 == c2 && i1 == i2)
        case _ => assertTrue(false)
      }
    },
    test("thematic break round-trip") {
      roundTrip("---")
    },
    test("block quote round-trip") {
      val input    = "> quoted text"
      val doc1     = Parser.parseUnsafe(input)
      val rendered = Renderer.render(doc1)
      val doc2     = Parser.parseUnsafe(rendered)
      assertTrue(
        doc1.blocks.head.isInstanceOf[Block.BlockQuote],
        doc2.blocks.head.isInstanceOf[Block.BlockQuote]
      )
    }
  )

  private def roundTrip(input: String): TestResult = {
    val doc1     = Parser.parseUnsafe(input)
    val rendered = Renderer.render(doc1)
    val doc2     = Parser.parseUnsafe(rendered)
    assertTrue(doc1.plainText == doc2.plainText)
  }

  val configSpec = suite("Render Config")(
    test("uses custom bullet char") {
      val doc = Document(
        Block.BulletList(
          Chunk(Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("item")))), None)),
          tight = true
        )
      )
      val config   = RenderConfig.default.withBulletChar('*')
      val rendered = Renderer.render(doc, config)
      assertTrue(rendered.contains("* item"))
    },
    test("uses custom emphasis char") {
      val doc      = Document(Block.Paragraph(Chunk(Inline.Emphasis(Chunk(Inline.Text("text"))))))
      val config   = RenderConfig.default.withEmphasisChar('_')
      val rendered = Renderer.render(doc, config)
      assertTrue(rendered.contains("_text_"))
    },
    test("uses custom thematic break char") {
      val doc      = Document(Block.ThematicBreak)
      val config   = RenderConfig.default.withThematicBreakChar('*')
      val rendered = Renderer.render(doc, config)
      assertTrue(rendered.contains("***"))
    }
  )
}
