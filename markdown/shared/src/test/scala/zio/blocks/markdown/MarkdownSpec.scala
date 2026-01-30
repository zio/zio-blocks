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

object MarkdownSpec extends ZIOSpecDefault {

  def spec = suite("MarkdownSpec")(
    headingLevelSpec,
    alignmentSpec,
    inlineSpec,
    blockSpec,
    documentSpec
  )

  val headingLevelSpec = suite("HeadingLevel")(
    test("fromInt returns correct levels") {
      assertTrue(
        HeadingLevel.fromInt(1) == Some(HeadingLevel.H1),
        HeadingLevel.fromInt(2) == Some(HeadingLevel.H2),
        HeadingLevel.fromInt(3) == Some(HeadingLevel.H3),
        HeadingLevel.fromInt(4) == Some(HeadingLevel.H4),
        HeadingLevel.fromInt(5) == Some(HeadingLevel.H5),
        HeadingLevel.fromInt(6) == Some(HeadingLevel.H6),
        HeadingLevel.fromInt(0) == None,
        HeadingLevel.fromInt(7) == None
      )
    },
    test("toInt returns correct values") {
      assertTrue(
        HeadingLevel.H1.toInt == 1,
        HeadingLevel.H2.toInt == 2,
        HeadingLevel.H3.toInt == 3,
        HeadingLevel.H4.toInt == 4,
        HeadingLevel.H5.toInt == 5,
        HeadingLevel.H6.toInt == 6
      )
    },
    test("unsafeFromInt throws for invalid levels") {
      assertTrue(
        try {
          HeadingLevel.unsafeFromInt(0)
          false
        } catch {
          case _: IllegalArgumentException => true
        }
      )
    },
    test("values contains all levels") {
      assertTrue(HeadingLevel.values.length == 6)
    }
  )

  val alignmentSpec = suite("Alignment")(
    test("has all alignment types") {
      assertTrue(
        Alignment.None.isInstanceOf[Alignment],
        Alignment.Left.isInstanceOf[Alignment],
        Alignment.Right.isInstanceOf[Alignment],
        Alignment.Center.isInstanceOf[Alignment]
      )
    }
  )

  val inlineSpec = suite("Inline")(
    test("Text plainText returns value") {
      assertTrue(Inline.Text("hello").plainText == "hello")
    },
    test("Text isEmpty for empty string") {
      assertTrue(
        Inline.Text("").isEmpty,
        !Inline.Text("hello").isEmpty
      )
    },
    test("Code plainText returns value") {
      assertTrue(Inline.Code("println").plainText == "println")
    },
    test("Emphasis plainText concatenates children") {
      val emphasis = Inline.Emphasis(Chunk(Inline.Text("hello"), Inline.Text(" world")))
      assertTrue(emphasis.plainText == "hello world")
    },
    test("Strong plainText concatenates children") {
      val strong = Inline.Strong(Chunk(Inline.Text("important")))
      assertTrue(strong.plainText == "important")
    },
    test("Strikethrough plainText concatenates children") {
      val strike = Inline.Strikethrough(Chunk(Inline.Text("removed")))
      assertTrue(strike.plainText == "removed")
    },
    test("Link plainText returns link text") {
      val link = Inline.Link(Chunk(Inline.Text("click here")), "https://example.com", None)
      assertTrue(link.plainText == "click here")
    },
    test("Image plainText returns alt text") {
      val image = Inline.Image("Alt text", "image.png", None)
      assertTrue(image.plainText == "Alt text")
    },
    test("HtmlInline plainText returns empty") {
      assertTrue(Inline.HtmlInline("<br>").plainText == "")
    },
    test("SoftBreak plainText returns space") {
      assertTrue(Inline.SoftBreak.plainText == " ")
    },
    test("HardBreak plainText returns newline") {
      assertTrue(Inline.HardBreak.plainText == "\n")
    },
    test("Autolink plainText returns url") {
      assertTrue(Inline.Autolink("https://example.com", isEmail = false).plainText == "https://example.com")
    },
    test("convenience constructors work") {
      assertTrue(
        Inline.text("hello") == Inline.Text("hello"),
        Inline.code("code") == Inline.Code("code"),
        Inline.softBreak == Inline.SoftBreak,
        Inline.hardBreak == Inline.HardBreak
      )
    }
  )

  val blockSpec = suite("Block")(
    test("Paragraph plainText concatenates inlines") {
      val para = Block.Paragraph(Chunk(Inline.Text("Hello "), Inline.Text("world")))
      assertTrue(para.plainText == "Hello world")
    },
    test("Heading plainText concatenates inlines") {
      val heading = Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("Title")))
      assertTrue(heading.plainText == "Title")
    },
    test("CodeBlock plainText returns code") {
      val code = Block.CodeBlock("val x = 1", Some("scala"))
      assertTrue(code.plainText == "val x = 1")
    },
    test("ThematicBreak plainText returns empty") {
      assertTrue(Block.ThematicBreak.plainText == "")
    },
    test("BlockQuote plainText concatenates children") {
      val quote = Block.BlockQuote(Chunk(Block.Paragraph(Chunk(Inline.Text("quoted")))))
      assertTrue(quote.plainText == "quoted")
    },
    test("BulletList plainText concatenates items") {
      val list = Block.BulletList(
        Chunk(
          Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("item 1")))), None),
          Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("item 2")))), None)
        ),
        tight = true
      )
      assertTrue(list.plainText == "item 1\nitem 2")
    },
    test("OrderedList plainText concatenates items") {
      val list = Block.OrderedList(
        Chunk(
          Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("first")))), None),
          Block.ListItem(Chunk(Block.Paragraph(Chunk(Inline.Text("second")))), None)
        ),
        start = 1,
        tight = true
      )
      assertTrue(list.plainText == "first\nsecond")
    },
    test("ListItem task item markers") {
      val checked   = Block.ListItem.checked(Block.Paragraph(Chunk(Inline.Text("done"))))
      val unchecked = Block.ListItem.unchecked(Block.Paragraph(Chunk(Inline.Text("todo"))))
      val normal    = Block.ListItem(Block.Paragraph(Chunk(Inline.Text("item"))))
      assertTrue(
        checked.isTaskItem,
        checked.checked == Some(true),
        unchecked.isTaskItem,
        unchecked.checked == Some(false),
        !normal.isTaskItem,
        normal.checked == None
      )
    },
    test("Table plainText formats cells") {
      val table = Block.Table(
        Chunk(Block.TableCell(Chunk(Inline.Text("A"))), Block.TableCell(Chunk(Inline.Text("B")))),
        Chunk(Alignment.Left, Alignment.Right),
        Chunk(
          Chunk(Block.TableCell(Chunk(Inline.Text("1"))), Block.TableCell(Chunk(Inline.Text("2"))))
        )
      )
      assertTrue(table.plainText.contains("A | B"))
    },
    test("convenience constructors work") {
      assertTrue(
        Block.h1(Inline.Text("Title")).isInstanceOf[Block.Heading],
        Block.h2(Inline.Text("Sub")).isInstanceOf[Block.Heading],
        Block.codeBlock("code").isInstanceOf[Block.CodeBlock],
        Block.thematicBreak == Block.ThematicBreak
      )
    }
  )

  val documentSpec = suite("Document")(
    test("empty document") {
      assertTrue(
        Document.empty.isEmpty,
        Document.empty.blocks.isEmpty
      )
    },
    test("plainText concatenates blocks") {
      val doc = Document(
        Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("Title"))),
        Block.Paragraph(Chunk(Inline.Text("Content")))
      )
      assertTrue(doc.plainText == "Title\n\nContent")
    },
    test("++ concatenates documents") {
      val doc1     = Document(Block.Paragraph(Chunk(Inline.Text("First"))))
      val doc2     = Document(Block.Paragraph(Chunk(Inline.Text("Second"))))
      val combined = doc1 ++ doc2
      assertTrue(combined.blocks.length == 2)
    },
    test(":+ appends block") {
      val doc = Document.empty :+ Block.Paragraph(Chunk(Inline.Text("Added")))
      assertTrue(doc.blocks.length == 1)
    },
    test("+: prepends block") {
      val doc = Block.Paragraph(Chunk(Inline.Text("First"))) +: Document(Block.Paragraph(Chunk(Inline.Text("Second"))))
      assertTrue(
        doc.blocks.length == 2,
        doc.blocks.head.plainText == "First"
      )
    },
    test("text creates paragraph document") {
      val doc = Document.text("Hello")
      assertTrue(
        doc.blocks.length == 1,
        doc.blocks.head.isInstanceOf[Block.Paragraph]
      )
    },
    test("heading creates heading document") {
      val doc = Document.heading(HeadingLevel.H2, "Subtitle")
      assertTrue(
        doc.blocks.length == 1,
        doc.blocks.head.isInstanceOf[Block.Heading]
      )
    }
  )
}
