/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
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

package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._

object ParserFrontmatterSpec extends MarkdownBaseSpec {
  def spec = suite("ParserFrontmatter")(
    test("parseWithFrontmatter reads pairs and the document") {
      val input  = "---\ntitle: test\nauthor: me\n---\n# Hello"
      val result = Parser.parseWithFrontmatter(input)
      assertTrue(
        result.isRight,
        result.toOption.get._1 == Map("title" -> "test", "author" -> "me"),
        result.toOption.get._2 == Doc(
          Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))),
          Map("title" -> "test", "author" -> "me")
        )
      )
    },
    test("parseWithFrontmatter without frontmatter behaves like parse") {
      val input = "# Hello"
      assertTrue(
        Parser.parseWithFrontmatter(input) == Parser.parse(input).map(doc => (Map.empty[String, String], doc))
      )
    },
    test("parseWithFrontmatter leaves unclosed fences to strict parsing") {
      val result = Parser.parseWithFrontmatter("---\ntitle: test")
      assertTrue(result.isRight, result.toOption.get._1.isEmpty)
    },
    test("parseWithFrontmatter reports body errors after frontmatter") {
      val result = Parser.parseWithFrontmatter("---\ntitle: test\n---\n####### Too deep")
      assertTrue(result.isLeft)
    },
    test("parseWithFrontmatter skips non-pair lines inside fences") {
      val input  = "---\n# a comment\ntitle: test\n---\n# Hello"
      val result = Parser.parseWithFrontmatter(input)
      assertTrue(
        result.isRight,
        result.toOption.get._1 == Map("title" -> "test"),
        result.toOption.get._2.blocks.length == 1
      )
    },
    test("empty fences are not frontmatter") {
      val result = Parser.parseWithFrontmatter("---\n---\n# Hello")
      assertTrue(result.isRight, result.toOption.get._1.isEmpty)
    },
    test("strict parse still rejects frontmatter") {
      val result = Parser.parse("---\ntitle: test\n---\n# Hello")
      assertTrue(result.isLeft)
    },
    test("parseWithFrontmatter returns pairs with the document") {
      val result = Parser.parseWithFrontmatter("---\ntitle: test\n---\n# Hello")
      assertTrue(
        result.isRight,
        result.toOption.get._1 == Map("title" -> "test"),
        result.toOption.get._2.blocks == Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello"))))
      )
    },
    test("parseWithFrontmatter keeps inputs without fences unchanged") {
      val result = Parser.parseWithFrontmatter("# Hello")
      assertTrue(
        result.isRight,
        result.toOption.get._1.isEmpty,
        result.toOption.get._2.blocks.length == 1
      )
    },
    test("parseWithFrontmatter accepts CRLF fences") {
      val input  = "---\r\ntitle: test\r\nauthor: me\r\n---\r\n# Hello"
      val result = Parser.parseWithFrontmatter(input)
      assertTrue(
        result.isRight,
        result.toOption.get._1 == Map("title" -> "test", "author" -> "me"),
        result.toOption.get._2.blocks.length == 1
      )
    },
    test("strict parse rejects CRLF frontmatter") {
      val result = Parser.parse("---\r\ntitle: test\r\n---\r\n# Hello")
      assertTrue(result.isLeft)
    },
    test("parseWithFrontmatter leaves CRLF unclosed fences to strict parsing") {
      val result = Parser.parseWithFrontmatter("---\r\ntitle: test")
      assertTrue(result.isRight, result.toOption.get._1.isEmpty)
    }
  )
}
