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

object CoverageSpec extends ZIOSpecDefault {

  def spec = suite("CoverageSpec")(
    isEmptySpec,
    renderConfigSpec,
    parseErrorSpec,
    documentMethodsSpec,
    blockMethodsSpec,
    inlineMethodsSpec,
    toMarkdownSpec
  )

  val isEmptySpec = suite("isEmpty Methods")(
    test("Inline.Text.isEmpty true for empty") {
      assertTrue(Inline.Text("").isEmpty)
    },
    test("Inline.Text.isEmpty false for non-empty") {
      assertTrue(!Inline.Text("x").isEmpty)
    },
    test("Inline.Code.isEmpty true for empty") {
      assertTrue(Inline.Code("").isEmpty)
    },
    test("Inline.Code.isEmpty false for non-empty") {
      assertTrue(!Inline.Code("x").isEmpty)
    },
    test("Inline.Emphasis.isEmpty true for empty children") {
      assertTrue(Inline.Emphasis(Chunk.empty).isEmpty)
    },
    test("Inline.Emphasis.isEmpty false for non-empty") {
      assertTrue(!Inline.Emphasis(Chunk(Inline.Text("x"))).isEmpty)
    },
    test("Inline.Strong.isEmpty true for empty children") {
      assertTrue(Inline.Strong(Chunk.empty).isEmpty)
    },
    test("Inline.Strong.isEmpty false for non-empty") {
      assertTrue(!Inline.Strong(Chunk(Inline.Text("x"))).isEmpty)
    },
    test("Inline.Strikethrough.isEmpty true for empty children") {
      assertTrue(Inline.Strikethrough(Chunk.empty).isEmpty)
    },
    test("Inline.Strikethrough.isEmpty false for non-empty") {
      assertTrue(!Inline.Strikethrough(Chunk(Inline.Text("x"))).isEmpty)
    },
    test("Inline.Link.isEmpty true for empty children") {
      assertTrue(Inline.Link(Chunk.empty, "url", None).isEmpty)
    },
    test("Inline.Link.isEmpty false for non-empty") {
      assertTrue(!Inline.Link(Chunk(Inline.Text("x")), "url", None).isEmpty)
    },
    test("Inline.Image.isEmpty true for empty alt and url") {
      assertTrue(Inline.Image("", "", None).isEmpty)
    },
    test("Inline.Image.isEmpty false for non-empty alt") {
      assertTrue(!Inline.Image("alt", "url", None).isEmpty)
    },
    test("Inline.HtmlInline.isEmpty true for empty") {
      assertTrue(Inline.HtmlInline("").isEmpty)
    },
    test("Inline.HtmlInline.isEmpty false for non-empty") {
      assertTrue(!Inline.HtmlInline("<br>").isEmpty)
    },
    test("Inline.SoftBreak.isEmpty is false") {
      assertTrue(!Inline.SoftBreak.isEmpty)
    },
    test("Inline.HardBreak.isEmpty is false") {
      assertTrue(!Inline.HardBreak.isEmpty)
    },
    test("Inline.Autolink.isEmpty true for empty URL") {
      assertTrue(Inline.Autolink("", false).isEmpty)
    },
    test("Inline.Autolink.isEmpty false for non-empty URL") {
      assertTrue(!Inline.Autolink("https://x.com", false).isEmpty)
    },
    test("Block.Paragraph.isEmpty true for empty children") {
      assertTrue(Block.Paragraph(Chunk.empty).isEmpty)
    },
    test("Block.Paragraph.isEmpty false for non-empty children") {
      assertTrue(!Block.Paragraph(Chunk(Inline.Text("x"))).isEmpty)
    },
    test("Block.Heading.isEmpty true for empty children") {
      assertTrue(Block.Heading(HeadingLevel.H1, Chunk.empty).isEmpty)
    },
    test("Block.Heading.isEmpty false for non-empty children") {
      assertTrue(!Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("x"))).isEmpty)
    },
    test("Block.CodeBlock.isEmpty true for empty code") {
      assertTrue(Block.CodeBlock("", None).isEmpty)
    },
    test("Block.CodeBlock.isEmpty false for non-empty code") {
      assertTrue(!Block.CodeBlock("x", None).isEmpty)
    },
    test("Block.ThematicBreak.isEmpty is false") {
      assertTrue(!Block.ThematicBreak.isEmpty)
    },
    test("Block.BlockQuote.isEmpty true for empty children") {
      assertTrue(Block.BlockQuote(Chunk.empty).isEmpty)
    },
    test("Block.BlockQuote.isEmpty false for non-empty children") {
      assertTrue(!Block.BlockQuote(Chunk(Block.Paragraph(Chunk(Inline.Text("x"))))).isEmpty)
    },
    test("Block.BulletList.isEmpty true for empty items") {
      assertTrue(Block.BulletList(Chunk.empty, true).isEmpty)
    },
    test("Block.BulletList.isEmpty false for non-empty items") {
      assertTrue(!Block.BulletList(Chunk(Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("x")))), None)), true).isEmpty)
    },
    test("Block.OrderedList.isEmpty true for empty items") {
      assertTrue(Block.OrderedList(Chunk.empty, 1, true).isEmpty)
    },
    test("Block.OrderedList.isEmpty false for non-empty items") {
      assertTrue(!Block.OrderedList(Chunk(Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("x")))), None)), 1, true).isEmpty)
    },
    test("Block.ListItem.isEmpty true for empty children") {
      assertTrue(Block.ListItem(Chunk.empty, None).isEmpty)
    },
    test("Block.ListItem.isEmpty false for non-empty children") {
      assertTrue(!Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("x")))), None).isEmpty)
    },
    test("Block.HtmlBlock.isEmpty true for empty value") {
      assertTrue(Block.HtmlBlock("").isEmpty)
    },
    test("Block.HtmlBlock.isEmpty false for non-empty value") {
      assertTrue(!Block.HtmlBlock("<div>").isEmpty)
    },
    test("Block.Table.isEmpty true for empty header") {
      assertTrue(Block.Table(Chunk.empty, Chunk.empty, Chunk.empty[Chunk[Block.TableCell]]).isEmpty)
    },
    test("Block.Table.isEmpty false for non-empty header") {
      assertTrue(!Block.Table(Chunk(Block.TableCell(Chunk.empty)), Chunk.empty, Chunk.empty[Chunk[Block.TableCell]]).isEmpty)
    },
    test("Block.TableCell.isEmpty true for empty children") {
      assertTrue(Block.TableCell(Chunk.empty).isEmpty)
    },
    test("Block.TableCell.isEmpty false for non-empty children") {
      assertTrue(!Block.TableCell(Chunk(Inline.Text("x"))).isEmpty)
    },
    test("Document.isEmpty true for empty blocks") {
      assertTrue(Document(Chunk.empty).isEmpty)
    },
    test("Document.isEmpty false for non-empty blocks") {
      assertTrue(!Document(Chunk(Block.Paragraph(Chunk(Inline.Text("x"))))).isEmpty)
    }
  )

  val renderConfigSpec = suite("RenderConfig Methods")(
    test("withBulletChar") {
      val config = RenderConfig.default.withBulletChar('*')
      assertTrue(config.bulletChar == '*')
    },
    test("withEmphasisChar") {
      val config = RenderConfig.default.withEmphasisChar('_')
      assertTrue(config.emphasisChar == '_')
    },
    test("withStrongChar") {
      val config = RenderConfig.default.withStrongChar('_')
      assertTrue(config.strongChar == '_')
    },
    test("withThematicBreakChar") {
      val config = RenderConfig.default.withThematicBreakChar('*')
      assertTrue(config.thematicBreakChar == '*')
    },
    test("withCodeBlockChar") {
      val config = RenderConfig.default.withCodeBlockChar('~')
      assertTrue(config.codeBlockChar == '~')
    },
    test("withSoftBreak") {
      val config = RenderConfig.default.withSoftBreak(" ")
      assertTrue(config.softBreak == " ")
    },
    test("render with custom bullet char") {
      val doc    = Document(Block.BulletList(Chunk(Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("item")))), None)), true))
      val config = RenderConfig.default.withBulletChar('*')
      val result = Renderer.render(doc, config)
      assertTrue(result.contains("* item"))
    },
    test("render with custom emphasis char") {
      val doc    = Document(Block.Paragraph(Chunk(Inline.Emphasis(Chunk(Inline.Text("text"))))))
      val config = RenderConfig.default.withEmphasisChar('_')
      val result = Renderer.render(doc, config)
      assertTrue(result.contains("_text_"))
    },
    test("render with custom strong char") {
      val doc    = Document(Block.Paragraph(Chunk(Inline.Strong(Chunk(Inline.Text("text"))))))
      val config = RenderConfig.default.withStrongChar('_')
      val result = Renderer.render(doc, config)
      assertTrue(result.contains("__text__"))
    },
    test("render with custom thematic break char") {
      val doc    = Document(Block.ThematicBreak)
      val config = RenderConfig.default.withThematicBreakChar('*')
      val result = Renderer.render(doc, config)
      assertTrue(result.contains("***"))
    },
    test("render with custom code block char") {
      val doc    = Document(Block.CodeBlock("code", None))
      val config = RenderConfig.default.withCodeBlockChar('~')
      val result = Renderer.render(doc, config)
      assertTrue(result.contains("~~~"))
    }
  )

  val parseErrorSpec = suite("ParseError")(
    test("has correct line") {
      val error = ParseError("message", 5, 3, Some("bad"))
      assertTrue(error.line == 5)
    },
    test("has correct column") {
      val error = ParseError("message", 5, 3, Some("bad"))
      assertTrue(error.column == 3)
    },
    test("has correct offendingInput") {
      val error = ParseError("message", 5, 3, Some("bad"))
      assertTrue(error.offendingInput == Some("bad"))
    },
    test("getMessage contains message") {
      val error = ParseError("test message", 5, 3, Some("bad"))
      assertTrue(error.getMessage.contains("test message"))
    },
    test("getMessage contains line") {
      val error = ParseError("message", 5, 3, Some("bad"))
      assertTrue(error.getMessage.contains("5"))
    },
    test("getMessage contains column") {
      val error = ParseError("message", 5, 3, Some("bad"))
      assertTrue(error.getMessage.contains("3"))
    },
    test("formattedMessage equals getMessage") {
      val error = ParseError("message", 5, 3, Some("bad"))
      assertTrue(error.formattedMessage == error.getMessage)
    },
    test("withMessage creates new error") {
      val error1 = ParseError("message", 5, 3, Some("bad"))
      val error2 = error1.withMessage("new message")
      assertTrue(error2.message == "new message" && error1.message == "message")
    },
    test("withOffendingInput creates new error") {
      val error1 = ParseError("message", 5, 3, None)
      val error2 = error1.withOffendingInput("input")
      assertTrue(error2.offendingInput == Some("input") && error1.offendingInput.isEmpty)
    },
    test("apply with two args creates error at 1,1") {
      val error = ParseError("message")
      assertTrue(error.line == 1 && error.column == 1)
    },
    test("apply with three args creates error at position") {
      val error = ParseError("message", 5, 3)
      assertTrue(error.line == 5 && error.column == 3 && error.offendingInput.isEmpty)
    },
    test("truncates long offending input in message") {
      val longInput = "a" * 100
      val error     = ParseError("message", 1, 1, Some(longInput))
      assertTrue(error.getMessage.contains("..."))
    }
  )

  val documentMethodsSpec = suite("Document Methods")(
    test("+: prepends block") {
      val doc = Document.text("world")
      val newDoc = Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("hello"))) +: doc
      assertTrue(newDoc.blocks.length == 2)
    },
    test(":+ appends block") {
      val doc = Document.text("hello")
      val newDoc = doc :+ Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("world")))
      assertTrue(newDoc.blocks.length == 2)
    },
    test("++ concatenates documents") {
      val doc1 = Document.text("hello")
      val doc2 = Document.text("world")
      val combined = doc1 ++ doc2
      assertTrue(combined.blocks.length == 2)
    },
    test("plainText returns concatenated text") {
      val doc = Document(
        Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("Title"))),
        Block.Paragraph(Chunk(Inline.Text("Body")))
      )
      assertTrue(doc.plainText.contains("Title") && doc.plainText.contains("Body"))
    },
    test("render returns markdown string") {
      val doc = Document.text("hello")
      val rendered = doc.render
      assertTrue(rendered.contains("hello"))
    },
    test("render with config") {
      val doc = Document(Block.BulletList(Chunk(Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("item")))), None)), true))
      val config = RenderConfig.default.withBulletChar('*')
      val rendered = doc.render(config)
      assertTrue(rendered.contains("*"))
    },
    test("Document.empty is empty") {
      assertTrue(Document.empty.isEmpty)
    },
    test("Document.text creates paragraph") {
      val doc = Document.text("hello")
      assertTrue(doc.blocks.head.isInstanceOf[Block.Paragraph])
    },
    test("Document.heading creates heading") {
      val doc = Document.heading(HeadingLevel.H1, "Title")
      assertTrue(doc.blocks.head.isInstanceOf[Block.Heading])
    },
    test("Document.apply with varargs") {
      val doc = Document(
        Block.Paragraph(Chunk(Inline.Text("a"))),
        Block.Paragraph(Chunk(Inline.Text("b")))
      )
      assertTrue(doc.blocks.length == 2)
    }
  )

  val blockMethodsSpec = suite("Block Methods")(
    test("Block.paragraph creates paragraph") {
      val block = Block.paragraph(Inline.Text("hello"))
      assertTrue(block.isInstanceOf[Block.Paragraph])
    },
    test("Block.heading creates heading") {
      val block = Block.heading(HeadingLevel.H1, Inline.Text("title"))
      assertTrue(block.isInstanceOf[Block.Heading])
    },
    test("Block.h1 creates H1") {
      val block = Block.h1(Inline.Text("title"))
      assertTrue(block.asInstanceOf[Block.Heading].level == HeadingLevel.H1)
    },
    test("Block.h2 creates H2") {
      val block = Block.h2(Inline.Text("title"))
      assertTrue(block.asInstanceOf[Block.Heading].level == HeadingLevel.H2)
    },
    test("Block.h3 creates H3") {
      val block = Block.h3(Inline.Text("title"))
      assertTrue(block.asInstanceOf[Block.Heading].level == HeadingLevel.H3)
    },
    test("Block.h4 creates H4") {
      val block = Block.h4(Inline.Text("title"))
      assertTrue(block.asInstanceOf[Block.Heading].level == HeadingLevel.H4)
    },
    test("Block.h5 creates H5") {
      val block = Block.h5(Inline.Text("title"))
      assertTrue(block.asInstanceOf[Block.Heading].level == HeadingLevel.H5)
    },
    test("Block.h6 creates H6") {
      val block = Block.h6(Inline.Text("title"))
      assertTrue(block.asInstanceOf[Block.Heading].level == HeadingLevel.H6)
    },
    test("Block.codeBlock without language") {
      val block = Block.codeBlock("code")
      assertTrue(block.asInstanceOf[Block.CodeBlock].info.isEmpty)
    },
    test("Block.codeBlock with language") {
      val block = Block.codeBlock("code", "scala")
      assertTrue(block.asInstanceOf[Block.CodeBlock].info == Some("scala"))
    },
    test("Block.thematicBreak returns ThematicBreak") {
      val block = Block.thematicBreak
      assertTrue(block == Block.ThematicBreak)
    },
    test("Block.blockQuote creates BlockQuote") {
      val block = Block.blockQuote(Block.Paragraph(Chunk(Inline.Text("quoted"))))
      assertTrue(block.isInstanceOf[Block.BlockQuote])
    },
    test("Block.bulletList creates BulletList") {
      val block = Block.bulletList(Block.ListItem(Chunk.empty, None))
      assertTrue(block.isInstanceOf[Block.BulletList])
    },
    test("Block.orderedList creates OrderedList") {
      val block = Block.orderedList(Block.ListItem(Chunk.empty, None))
      assertTrue(block.isInstanceOf[Block.OrderedList])
    },
    test("Block.listItem creates ListItem") {
      val item = Block.listItem(Block.Paragraph(Chunk(Inline.Text("item"))))
      assertTrue(item.isInstanceOf[Block.ListItem])
    },
    test("Block.html creates HtmlBlock") {
      val block = Block.html("<div>")
      assertTrue(block.isInstanceOf[Block.HtmlBlock])
    },
    test("Heading.h1 convenience") {
      val block = Block.Heading.h1(Inline.Text("title"))
      assertTrue(block.level == HeadingLevel.H1)
    },
    test("Heading.h2 convenience") {
      val block = Block.Heading.h2(Inline.Text("title"))
      assertTrue(block.level == HeadingLevel.H2)
    },
    test("Heading.h3 convenience") {
      val block = Block.Heading.h3(Inline.Text("title"))
      assertTrue(block.level == HeadingLevel.H3)
    },
    test("Heading.h4 convenience") {
      val block = Block.Heading.h4(Inline.Text("title"))
      assertTrue(block.level == HeadingLevel.H4)
    },
    test("Heading.h5 convenience") {
      val block = Block.Heading.h5(Inline.Text("title"))
      assertTrue(block.level == HeadingLevel.H5)
    },
    test("Heading.h6 convenience") {
      val block = Block.Heading.h6(Inline.Text("title"))
      assertTrue(block.level == HeadingLevel.H6)
    },
    test("ListItem with checked true") {
      val item = Block.ListItem(Chunk.empty, Some(true))
      assertTrue(item.checked == Some(true))
    },
    test("ListItem with checked false") {
      val item = Block.ListItem(Chunk.empty, Some(false))
      assertTrue(item.checked == Some(false))
    },
    test("ListItem without checked") {
      val item = Block.ListItem(Chunk.empty, None)
      assertTrue(item.checked.isEmpty)
    },
    test("TableCell.text convenience") {
      val cell = Block.TableCell.text("content")
      assertTrue(cell.children.head.asInstanceOf[Inline.Text].value == "content")
    }
  )

  val inlineMethodsSpec = suite("Inline Methods")(
    test("Inline.text creates Text") {
      val elem = Inline.text("hello")
      assertTrue(elem == Inline.Text("hello"))
    },
    test("Inline.code creates Code") {
      val elem = Inline.code("x")
      assertTrue(elem == Inline.Code("x"))
    },
    test("Inline.emphasis creates Emphasis") {
      val elem = Inline.emphasis(Inline.Text("em"))
      assertTrue(elem.isInstanceOf[Inline.Emphasis])
    },
    test("Inline.strong creates Strong") {
      val elem = Inline.strong(Inline.Text("bold"))
      assertTrue(elem.isInstanceOf[Inline.Strong])
    },
    test("Inline.strikethrough creates Strikethrough") {
      val elem = Inline.strikethrough(Inline.Text("del"))
      assertTrue(elem.isInstanceOf[Inline.Strikethrough])
    },
    test("Inline.link creates Link") {
      val elem = Inline.link("text", "url")
      assertTrue(elem.isInstanceOf[Inline.Link])
    },
    test("Inline.link with title") {
      val elem = Inline.link("text", "url", "title")
      assertTrue(elem.asInstanceOf[Inline.Link].title == Some("title"))
    },
    test("Inline.link with Chunk children") {
      val elem = Inline.link(Chunk(Inline.Text("text")), "url")
      assertTrue(elem.isInstanceOf[Inline.Link])
    },
    test("Inline.link with Chunk children and title") {
      val elem = Inline.link(Chunk(Inline.Text("text")), "url", "title")
      assertTrue(elem.asInstanceOf[Inline.Link].title == Some("title"))
    },
    test("Inline.image creates Image") {
      val elem = Inline.image("alt", "url")
      assertTrue(elem.isInstanceOf[Inline.Image])
    },
    test("Inline.image with title") {
      val elem = Inline.image("alt", "url", "title")
      assertTrue(elem.asInstanceOf[Inline.Image].title == Some("title"))
    },
    test("Inline.autolink URL") {
      val elem = Inline.autolink("https://example.com")
      assertTrue(elem.asInstanceOf[Inline.Autolink].isEmail == false)
    },
    test("Inline.emailAutolink") {
      val elem = Inline.emailAutolink("test@example.com")
      assertTrue(elem.asInstanceOf[Inline.Autolink].isEmail == true)
    },
    test("Inline.softBreak") {
      assertTrue(Inline.softBreak == Inline.SoftBreak)
    },
    test("Inline.hardBreak") {
      assertTrue(Inline.hardBreak == Inline.HardBreak)
    },
    test("Inline.html creates HtmlInline") {
      val elem = Inline.html("<br>")
      assertTrue(elem == Inline.HtmlInline("<br>"))
    }
  )

  val toMarkdownSpec = suite("ToMarkdown Extended")(
    test("String to markdown") {
      val result = ToMarkdown[String].toMarkdown("hello")
      assertTrue(result == Inline.Text("hello"))
    },
    test("Int to markdown") {
      val result = ToMarkdown[Int].toMarkdown(42)
      assertTrue(result == Inline.Text("42"))
    },
    test("Long to markdown") {
      val result = ToMarkdown[Long].toMarkdown(123L)
      assertTrue(result == Inline.Text("123"))
    },
    test("Double to markdown") {
      val result = ToMarkdown[Double].toMarkdown(3.14)
      assertTrue(result == Inline.Text("3.14"))
    },
    test("Float to markdown") {
      val result = ToMarkdown[Float].toMarkdown(2.5f)
      assertTrue(result == Inline.Text("2.5"))
    },
    test("Boolean true to markdown") {
      val result = ToMarkdown[Boolean].toMarkdown(true)
      assertTrue(result == Inline.Text("true"))
    },
    test("Boolean false to markdown") {
      val result = ToMarkdown[Boolean].toMarkdown(false)
      assertTrue(result == Inline.Text("false"))
    },
    test("Char to markdown") {
      val result = ToMarkdown[Char].toMarkdown('A')
      assertTrue(result == Inline.Text("A"))
    },
    test("BigInt to markdown") {
      val result = ToMarkdown[BigInt].toMarkdown(BigInt(123))
      assertTrue(result == Inline.Text("123"))
    },
    test("BigDecimal to markdown") {
      val result = ToMarkdown[BigDecimal].toMarkdown(BigDecimal("1.23"))
      assertTrue(result == Inline.Text("1.23"))
    },
    test("Inline passes through") {
      val inline = Inline.Strong(Chunk(Inline.Text("bold")))
      val result = ToMarkdown[Inline].toMarkdown(inline)
      assertTrue(result == inline)
    }
  )
}
