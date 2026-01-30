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

object ParserSpec extends ZIOSpecDefault {

  def spec = suite("ParserSpec")(
    headingSpec,
    paragraphSpec,
    codeBlockSpec,
    thematicBreakSpec,
    listSpec,
    blockQuoteSpec,
    tableSpec,
    inlineSpec,
    errorSpec
  )

  val headingSpec = suite("Headings")(
    test("parses ATX headings H1-H6") {
      val results = (1 to 6).map { level =>
        val input = "#" * level + " Heading " + level
        Parser.parse(input).map(_.blocks.head)
      }
      assertTrue(
        results.forall(_.isRight),
        results.zipWithIndex.forall { case (r, i) =>
          r.toOption.get match {
            case Block.Heading(level, _) => level.toInt == i + 1
            case _                       => false
          }
        }
      )
    },
    test("parses heading without trailing space") {
      val result = Parser.parse("# Title")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Heading(HeadingLevel.H1, children) =>
            children.head match {
              case Inline.Text(t) => t == "Title"
              case _              => false
            }
          case _ => false
        }
      )
    },
    test("parses heading with trailing hashes") {
      val result = Parser.parse("## Title ##")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Heading(HeadingLevel.H2, children) =>
            children.head match {
              case Inline.Text(t) => t == "Title"
              case _              => false
            }
          case _ => false
        }
      )
    },
    test("parses heading with inline formatting") {
      val result = Parser.parse("# Hello *world*")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Heading(HeadingLevel.H1, children) => children.length >= 2
          case _                                        => false
        }
      )
    },
    test("treats 7+ hashes as paragraph") {
      val result = Parser.parse("####### Not a heading")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.Paragraph]
      )
    }
  )

  val paragraphSpec = suite("Paragraphs")(
    test("parses simple paragraph") {
      val result = Parser.parse("Hello world")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.head match {
              case Inline.Text(t) => t == "Hello world"
              case _              => false
            }
          case _ => false
        }
      )
    },
    test("parses multiline paragraph") {
      val result = Parser.parse("Line 1\nLine 2")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.length == 1
      )
    },
    test("separates paragraphs by blank line") {
      val result = Parser.parse("Para 1\n\nPara 2")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.length == 2
      )
    }
  )

  val codeBlockSpec = suite("Code Blocks")(
    test("parses fenced code block with backticks") {
      val input  = "```\ncode here\n```"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.CodeBlock(code, info) =>
            code == "code here" && info.isEmpty
          case _ => false
        }
      )
    },
    test("parses fenced code block with tildes") {
      val input  = "~~~\ncode here\n~~~"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.CodeBlock]
      )
    },
    test("parses code block with language info") {
      val input  = "```scala\nval x = 1\n```"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.CodeBlock(code, info) =>
            code == "val x = 1" && info == Some("scala")
          case _ => false
        }
      )
    },
    test("parses code block with multiple lines") {
      val input  = "```\nline 1\nline 2\nline 3\n```"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.CodeBlock(code, _) => code.contains("line 1") && code.contains("line 3")
          case _                        => false
        }
      )
    },
    test("handles unclosed code block") {
      val input  = "```\ncode without closing"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.CodeBlock]
      )
    }
  )

  val thematicBreakSpec = suite("Thematic Breaks")(
    test("parses thematic break with dashes") {
      val result = Parser.parse("---")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head == Block.ThematicBreak
      )
    },
    test("parses thematic break with asterisks") {
      val result = Parser.parse("***")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head == Block.ThematicBreak
      )
    },
    test("parses thematic break with underscores") {
      val result = Parser.parse("___")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head == Block.ThematicBreak
      )
    },
    test("parses thematic break with spaces") {
      val result = Parser.parse("- - -")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head == Block.ThematicBreak
      )
    },
    test("parses long thematic break") {
      val result = Parser.parse("------------------------------")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head == Block.ThematicBreak
      )
    }
  )

  val listSpec = suite("Lists")(
    test("parses bullet list with dashes") {
      val input  = "- item 1\n- item 2"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.BulletList(items, _) => items.length == 2
          case _                          => false
        }
      )
    },
    test("parses bullet list with asterisks") {
      val input  = "* item 1\n* item 2"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.BulletList]
      )
    },
    test("parses ordered list") {
      val input  = "1. first\n2. second"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.OrderedList(items, start, _) =>
            items.length == 2 && start == 1
          case _ => false
        }
      )
    },
    test("parses ordered list with custom start") {
      val input  = "5. fifth\n6. sixth"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.OrderedList(_, start, _) => start == 5
          case _                              => false
        }
      )
    },
    test("parses task list with unchecked item") {
      val input  = "- [ ] todo item"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.BulletList(items, _) =>
            items.head.checked == Some(false)
          case _ => false
        }
      )
    },
    test("parses task list with checked item") {
      val input  = "- [x] done item"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.BulletList(items, _) =>
            items.head.checked == Some(true)
          case _ => false
        }
      )
    },
    test("parses nested list") {
      val input  = "- item 1\n  - nested"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    }
  )

  val blockQuoteSpec = suite("Block Quotes")(
    test("parses simple block quote") {
      val input  = "> quoted text"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.BlockQuote]
      )
    },
    test("parses multiline block quote") {
      val input  = "> line 1\n> line 2"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.BlockQuote]
      )
    },
    test("parses lazy continuation") {
      val input  = "> line 1\nline 2"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses nested block quote") {
      val input  = "> > nested quote"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.BlockQuote]
      )
    }
  )

  val tableSpec = suite("Tables")(
    test("parses simple table") {
      val input  = "| A | B |\n|---|---|\n| 1 | 2 |"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Table(header, _, rows) =>
            header.length == 2 && rows.length == 1
          case _ => false
        }
      )
    },
    test("parses table with alignments") {
      val input  = "| Left | Center | Right |\n|:---|:---:|---:|\n| l | c | r |"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Table(_, alignments, _) =>
            alignments == Chunk(Alignment.Left, Alignment.Center, Alignment.Right)
          case _ => false
        }
      )
    },
    test("parses table with multiple rows") {
      val input  = "| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Table(_, _, rows) => rows.length == 2
          case _                       => false
        }
      )
    }
  )

  val inlineSpec = suite("Inline Parsing")(
    test("parses emphasis with asterisks") {
      val result = Parser.parse("*emphasized*")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists(_.isInstanceOf[Inline.Emphasis])
          case _ => false
        }
      )
    },
    test("parses emphasis with underscores") {
      val result = Parser.parse("_emphasized_")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists(_.isInstanceOf[Inline.Emphasis])
          case _ => false
        }
      )
    },
    test("parses strong with asterisks") {
      val result = Parser.parse("**strong**")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists(_.isInstanceOf[Inline.Strong])
          case _ => false
        }
      )
    },
    test("parses strikethrough") {
      val result = Parser.parse("~~deleted~~")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists(_.isInstanceOf[Inline.Strikethrough])
          case _ => false
        }
      )
    },
    test("parses inline code") {
      val result = Parser.parse("`code`")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists {
              case Inline.Code(c) => c == "code"
              case _              => false
            }
          case _ => false
        }
      )
    },
    test("parses inline code with double backticks") {
      val result = Parser.parse("`` `code` ``")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists(_.isInstanceOf[Inline.Code])
          case _ => false
        }
      )
    },
    test("parses link") {
      val result = Parser.parse("[text](https://example.com)")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists {
              case Inline.Link(_, url, _) => url == "https://example.com"
              case _                      => false
            }
          case _ => false
        }
      )
    },
    test("parses link with title") {
      val result = Parser.parse("""[text](https://example.com "Title")""")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists {
              case Inline.Link(_, _, title) => title == Some("Title")
              case _                        => false
            }
          case _ => false
        }
      )
    },
    test("parses image") {
      val result = Parser.parse("![alt](image.png)")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists {
              case Inline.Image(alt, url, _) => alt == "alt" && url == "image.png"
              case _                         => false
            }
          case _ => false
        }
      )
    },
    test("parses autolink") {
      val result = Parser.parse("<https://example.com>")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists {
              case Inline.Autolink(url, false) => url == "https://example.com"
              case _                           => false
            }
          case _ => false
        }
      )
    },
    test("parses email autolink") {
      val result = Parser.parse("<test@example.com>")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists {
              case Inline.Autolink(_, true) => true
              case _                        => false
            }
          case _ => false
        }
      )
    },
    test("parses hard break with trailing spaces") {
      val result = Parser.parse("line 1  \nline 2")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            children.exists(_ == Inline.HardBreak)
          case _ => false
        }
      )
    },
    test("parses escape sequences") {
      val result = Parser.parse("\\*not emphasis\\*")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.Paragraph(children) =>
            !children.exists(_.isInstanceOf[Inline.Emphasis])
          case _ => false
        }
      )
    }
  )

  val errorSpec = suite("Error Handling")(
    test("empty input returns empty document") {
      val result = Parser.parse("")
      assertTrue(
        result.isRight,
        result.toOption.get.isEmpty
      )
    },
    test("whitespace only returns empty document") {
      val result = Parser.parse("   \n   \n   ")
      assertTrue(
        result.isRight,
        result.toOption.get.isEmpty
      )
    }
  )
}
