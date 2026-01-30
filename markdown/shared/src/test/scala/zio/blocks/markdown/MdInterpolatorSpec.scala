package zio.blocks.markdown

import zio.blocks.chunk.Chunk
import zio.test._

object MdInterpolatorSpec extends MarkdownBaseSpec {
  def spec = suite("md interpolator")(
    test("simple heading") {
      val doc = md"# Hello"
      assertTrue(doc.blocks.size == 1 && doc.blocks.head.isInstanceOf[Heading])
    },
    test("paragraph with text") {
      val doc = md"Hello world"
      assertTrue(doc.blocks.size == 1 && doc.blocks.head.isInstanceOf[Paragraph])
    },
    test("interpolates String") {
      val name = "World"
      val doc  = md"Hello $name"
      val para = doc.blocks.head.asInstanceOf[Paragraph]
      assertTrue(para.content.exists {
        case Text(v) => v.contains("World")
        case _       => false
      })
    },
    test("interpolates Int") {
      val n    = 42
      val doc  = md"Value: $n"
      val para = doc.blocks.head.asInstanceOf[Paragraph]
      assertTrue(para.content.exists {
        case Text(v) => v.contains("42")
        case _       => false
      })
    },
    test("interpolates Inline") {
      val bold: Inline = Strong(Chunk(Text("bold")))
      val doc          = md"This is $bold text"
      assertTrue(doc.blocks.size == 1)
    },
    test("multiline markdown") {
      val doc = md"""# Title

Some text here."""
      assertTrue(doc.blocks.size == 2)
    }
  )
}
