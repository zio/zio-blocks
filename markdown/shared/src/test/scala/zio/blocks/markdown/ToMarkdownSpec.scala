package zio.blocks.markdown

import zio.blocks.chunk.Chunk
import zio.test._

object ToMarkdownSpec extends MarkdownBaseSpec {
  def spec = suite("ToMarkdown")(
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
  )
}
