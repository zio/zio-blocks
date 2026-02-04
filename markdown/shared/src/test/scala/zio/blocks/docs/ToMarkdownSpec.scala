package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._

object ToMarkdownSpec extends MarkdownBaseSpec {
  def spec = suite("ToMarkdown")(
    suite("Primitives")(
      test("String converts to Text") {
        val result = ToMarkdown[String].toMarkdown("hello")
        assertTrue(result == Text("hello"))
      },
      test("Int converts to Text") {
        val result = ToMarkdown[Int].toMarkdown(42)
        assertTrue(result == Text("42"))
      },
      test("Long converts to Text") {
        val result = ToMarkdown[Long].toMarkdown(123L)
        assertTrue(result == Text("123"))
      },
      test("Double converts to Text") {
        val result = ToMarkdown[Double].toMarkdown(3.14)
        assertTrue(result == Text("3.14"))
      },
      test("Boolean converts to Text") {
        val result = ToMarkdown[Boolean].toMarkdown(true)
        assertTrue(result == Text("true"))
      },
      test("Inline passes through unchanged") {
        val inline = Strong(Chunk(Text("bold")))
        val result = ToMarkdown[Inline].toMarkdown(inline)
        assertTrue(result == inline)
      }
    ),
    suite("Collections")(
      test("List[String] converts to comma-separated text") {
        val result = ToMarkdown[List[String]].toMarkdown(List("a", "b", "c"))
        assertTrue(result == Text("a, b, c"))
      },
      test("List[Int] converts to comma-separated text") {
        val result = ToMarkdown[List[Int]].toMarkdown(List(1, 2, 3))
        assertTrue(result == Text("1, 2, 3"))
      },
      test("Empty List converts to empty text") {
        val result = ToMarkdown[List[String]].toMarkdown(List.empty)
        assertTrue(result == Text(""))
      },
      test("Chunk[String] converts to comma-separated text") {
        val result = ToMarkdown[Chunk[String]].toMarkdown(Chunk("x", "y"))
        assertTrue(result == Text("x, y"))
      },
      test("Vector[String] converts to comma-separated text") {
        val result = ToMarkdown[Vector[String]].toMarkdown(Vector("p", "q", "r"))
        assertTrue(result == Text("p, q, r"))
      },
      test("Seq[String] converts to comma-separated text") {
        val result = ToMarkdown[Seq[String]].toMarkdown(Seq("m", "n"))
        assertTrue(result == Text("m, n"))
      },
      test("List of Inline elements renders correctly") {
        val inlines = List(Code("a"), Code("b"))
        val result  = ToMarkdown[List[Inline]].toMarkdown(inlines)
        assertTrue(result == Text("`a`, `b`"))
      }
    ),
    suite("Blocks")(
      test("Paragraph converts to rendered markdown") {
        val block  = Paragraph(Chunk(Text("hello")))
        val result = ToMarkdown[Block].toMarkdown(block)
        assertTrue(result == Text("hello"))
      },
      test("Heading converts to rendered markdown") {
        val block  = Heading(HeadingLevel.H1, Chunk(Text("Title")))
        val result = ToMarkdown[Block].toMarkdown(block)
        assertTrue(result == Text("# Title"))
      },
      test("CodeBlock converts to rendered markdown") {
        val block  = CodeBlock(Some("scala"), "val x = 1")
        val result = ToMarkdown[Block].toMarkdown(block)
        assertTrue(result == Text("```scala\nval x = 1\n```"))
      },
      test("ThematicBreak converts to rendered markdown") {
        val result = ToMarkdown[Block].toMarkdown(ThematicBreak)
        assertTrue(result == Text("---"))
      },
      test("BlockQuote converts to rendered markdown") {
        val block  = BlockQuote(Chunk(Paragraph(Chunk(Text("quoted")))))
        val result = ToMarkdown[Block].toMarkdown(block)
        assertTrue(result == Text("> quoted\n>"))
      }
    )
  )
}
