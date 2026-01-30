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

object InterpolatorSpec extends ZIOSpecDefault {

  def spec = suite("InterpolatorSpec")(
    basicInterpolationSpec,
    typeInterpolationSpec,
    inlineInterpolationSpec,
    complexInterpolationSpec,
    toMarkdownSpec
  )

  val basicInterpolationSpec = suite("Basic Interpolation")(
    test("creates simple document") {
      val doc = md"Hello world"
      assertTrue(
        doc.blocks.length == 1,
        doc.plainText == "Hello world"
      )
    },
    test("creates heading") {
      val doc = md"# Title"
      assertTrue(
        doc.blocks.head.isInstanceOf[Block.Heading],
        doc.blocks.head match {
          case Block.Heading(HeadingLevel.H1, _) => true
          case _                                 => false
        }
      )
    },
    test("creates paragraph with formatting") {
      val doc = md"Hello *world*"
      assertTrue(doc.blocks.head.isInstanceOf[Block.Paragraph])
    }
  )

  val typeInterpolationSpec = suite("Type Interpolation")(
    test("interpolates String") {
      val name = "World"
      val doc  = md"Hello $name"
      assertTrue(doc.plainText == "Hello World")
    },
    test("interpolates Int") {
      val count = 42
      val doc   = md"Count: $count"
      assertTrue(doc.plainText == "Count: 42")
    },
    test("interpolates Long") {
      val bigNum = 9999999999L
      val doc    = md"Number: $bigNum"
      assertTrue(doc.plainText.contains("9999999999"))
    },
    test("interpolates Double") {
      val pi  = 3.14159
      val doc = md"Pi: $pi"
      assertTrue(doc.plainText.contains("3.14159"))
    },
    test("interpolates Float") {
      val f   = 2.5f
      val doc = md"Float: $f"
      assertTrue(doc.plainText.contains("2.5"))
    },
    test("interpolates Boolean") {
      val flag = true
      val doc  = md"Flag: $flag"
      assertTrue(doc.plainText == "Flag: true")
    },
    test("interpolates Char") {
      val c   = 'X'
      val doc = md"Char: $c"
      assertTrue(doc.plainText == "Char: X")
    },
    test("interpolates BigInt") {
      val big = BigInt("123456789012345678901234567890")
      val doc = md"Big: $big"
      assertTrue(doc.plainText.contains("123456789012345678901234567890"))
    },
    test("interpolates BigDecimal") {
      val bd  = BigDecimal("123.456789")
      val doc = md"Decimal: $bd"
      assertTrue(doc.plainText.contains("123.456789"))
    }
  )

  val inlineInterpolationSpec = suite("Inline Interpolation")(
    test("interpolates Inline.Text") {
      val text = Inline.Text("interpolated")
      val doc  = md"Hello $text"
      assertTrue(doc.plainText == "Hello interpolated")
    },
    test("interpolates Inline.Strong") {
      val bold = Inline.Strong(Chunk(Inline.Text("bold")))
      val doc  = md"This is $bold text"
      assertTrue(doc.plainText == "This is bold text")
    },
    test("interpolates Inline.Emphasis") {
      val italic = Inline.Emphasis(Chunk(Inline.Text("italic")))
      val doc    = md"This is $italic text"
      assertTrue(doc.plainText == "This is italic text")
    },
    test("interpolates Inline.Code") {
      val code = Inline.Code("x = 1")
      val doc  = md"Code: $code"
      assertTrue(doc.plainText.contains("x = 1"))
    },
    test("interpolates Inline.Link") {
      val link = Inline.Link(Chunk(Inline.Text("click")), "https://example.com", None)
      val doc  = md"Please $link here"
      assertTrue(doc.plainText.contains("click"))
    }
  )

  val complexInterpolationSpec = suite("Complex Interpolation")(
    test("multiple interpolations") {
      val name = "Alice"
      val age  = 30
      val doc  = md"$name is $age years old"
      assertTrue(doc.plainText == "Alice is 30 years old")
    },
    test("interpolation in heading") {
      val title = "Introduction"
      val doc   = md"# $title"
      assertTrue(
        doc.blocks.head.isInstanceOf[Block.Heading],
        doc.plainText == "Introduction"
      )
    },
    test("interpolation with emphasis") {
      val word = "important"
      val doc  = md"This is *$word*"
      assertTrue(doc.plainText == "This is important")
    },
    test("interpolation in code span") {
      val varName = "x"
      val doc     = md"Variable `$varName` is defined"
      assertTrue(doc.plainText.contains("x"))
    },
    test("interpolation in link text") {
      val linkText = "example"
      val doc      = md"Visit [$linkText](https://example.com)"
      assertTrue(doc.plainText.contains("example"))
    }
  )

  val toMarkdownSpec = suite("ToMarkdown Typeclass")(
    test("String instance") {
      val result = ToMarkdown[String].toMarkdown("hello")
      assertTrue(result == Inline.Text("hello"))
    },
    test("Int instance") {
      val result = ToMarkdown[Int].toMarkdown(42)
      assertTrue(result == Inline.Text("42"))
    },
    test("Long instance") {
      val result = ToMarkdown[Long].toMarkdown(123L)
      assertTrue(result == Inline.Text("123"))
    },
    test("Double instance") {
      val result = ToMarkdown[Double].toMarkdown(3.14)
      assertTrue(result == Inline.Text("3.14"))
    },
    test("Float instance") {
      val result = ToMarkdown[Float].toMarkdown(2.5f)
      assertTrue(result == Inline.Text("2.5"))
    },
    test("Boolean instance") {
      val result = ToMarkdown[Boolean].toMarkdown(true)
      assertTrue(result == Inline.Text("true"))
    },
    test("Inline instance") {
      val inline = Inline.Strong(Chunk(Inline.Text("text")))
      val result = ToMarkdown[Inline].toMarkdown(inline)
      assertTrue(result == inline)
    },
    test("Char instance") {
      val result = ToMarkdown[Char].toMarkdown('A')
      assertTrue(result == Inline.Text("A"))
    },
    test("BigInt instance") {
      val result = ToMarkdown[BigInt].toMarkdown(BigInt(123))
      assertTrue(result == Inline.Text("123"))
    },
    test("BigDecimal instance") {
      val result = ToMarkdown[BigDecimal].toMarkdown(BigDecimal("1.23"))
      assertTrue(result == Inline.Text("1.23"))
    }
  )
}
