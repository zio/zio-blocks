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
      val (meta, _) = Parser.stripFrontmatter("---\ntitle: test")
      val result    = Parser.parseWithFrontmatter("---\ntitle: test")
      assertTrue(meta.isEmpty, result.isRight)
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
      val (meta, rest) = Parser.stripFrontmatter("---\n---\n# Hello")
      assertTrue(meta.isEmpty, rest == "---\n---\n# Hello")
    },
    test("strict parse still rejects frontmatter") {
      val result = Parser.parse("---\ntitle: test\n---\n# Hello")
      assertTrue(result.isLeft)
    },
    test("stripFrontmatter splits pairs from the body") {
      val (meta, rest) = Parser.stripFrontmatter("---\ntitle: test\n---\n# Hello")
      assertTrue(meta == Map("title" -> "test"), rest == "# Hello")
    },
    test("stripFrontmatter keeps inputs without fences unchanged") {
      val (meta, rest) = Parser.stripFrontmatter("# Hello")
      assertTrue(meta.isEmpty, rest == "# Hello")
    }
  )
}
