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

object ParserEdgeCasesSpec extends ZIOSpecDefault {

  def spec = suite("ParserEdgeCasesSpec")(
    htmlBlockSpec,
    linkEdgeCasesSpec,
    imageEdgeCasesSpec,
    listEdgeCasesSpec,
    codeBlockEdgeCasesSpec,
    emphasisEdgeCasesSpec,
    blockQuoteEdgeCasesSpec,
    tableEdgeCasesSpec,
    escapeSpec,
    whitespaceSpec
  )

  val htmlBlockSpec = suite("HTML Blocks")(
    test("parses HTML block type 1 - script") {
      val input  = "<script>\nalert('hi');\n</script>"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses HTML block type 2 - comment") {
      val input  = "<!-- comment -->"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses HTML block type 3 - processing instruction") {
      val input  = "<?xml version=\"1.0\"?>"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses HTML block type 4 - declaration") {
      val input  = "<!DOCTYPE html>"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses HTML block type 5 - CDATA") {
      val input  = "<![CDATA[data]]>"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses HTML block type 6 - standard tags") {
      val input  = "<div>\ncontent\n</div>"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses HTML block with attributes") {
      val input  = "<div class=\"test\" id=\"main\">\ncontent\n</div>"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses self-closing HTML tag") {
      val input  = "<br/>"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses inline HTML") {
      val input  = "text <span>inline</span> more"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses nested HTML tags") {
      val input  = "<div><span>nested</span></div>"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    }
  )

  val linkEdgeCasesSpec = suite("Link Edge Cases")(
    test("parses link with empty text") {
      val input  = "[](https://example.com)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link with spaces in URL") {
      val input  = "[text](https://example.com/path%20with%20spaces)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link with parentheses in URL") {
      val input  = "[text](https://example.com/path_(with)_parens)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link with angle brackets") {
      val input  = "[text](<https://example.com>)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link with title in single quotes") {
      val input  = "[text](url 'title')"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link with title in double quotes") {
      val input  = "[text](url \"title\")"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link with title in parentheses") {
      val input  = "[text](url (title))"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link with formatted text") {
      val input  = "[**bold** text](url)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link with code in text") {
      val input  = "[`code` text](url)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses nested links - outer wins") {
      val input  = "[[inner](url1)](url2)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses link followed by text") {
      val input  = "[link](url) and more text"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses multiple links") {
      val input  = "[link1](url1) and [link2](url2)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    }
  )

  val imageEdgeCasesSpec = suite("Image Edge Cases")(
    test("parses image with empty alt") {
      val input  = "![](image.png)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses image with title") {
      val input  = "![alt](image.png \"title\")"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses image with spaces in alt") {
      val input  = "![alt text with spaces](image.png)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses image in link") {
      val input  = "[![alt](image.png)](url)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses multiple images") {
      val input  = "![img1](a.png) ![img2](b.png)"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    }
  )

  val listEdgeCasesSpec = suite("List Edge Cases")(
    test("parses bullet list with + marker") {
      val input  = "+ item 1\n+ item 2"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.BulletList]
      )
    },
    test("parses ordered list starting at 0") {
      val input  = "0. item"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.OrderedList(_, start, _) => start == 0
          case _                              => false
        }
      )
    },
    test("parses ordered list with large start number") {
      val input  = "999999999. item"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses list with blank lines between items") {
      val input  = "- item 1\n\n- item 2"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.BulletList]
      )
    },
    test("parses tight list") {
      val input  = "- item 1\n- item 2"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.BulletList(_, tight) => tight
          case _                          => false
        }
      )
    },
    test("parses list item with multiple paragraphs") {
      val input  = "- para 1\n\n  para 2"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses list item with code block") {
      val input  = "- item\n\n      code"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses deeply nested list") {
      val input  = "- level 1\n  - level 2\n    - level 3"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses task list with uppercase X") {
      val input  = "- [X] done"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.BulletList(items, _) => items.head.checked == Some(true)
          case _                          => false
        }
      )
    },
    test("parses mixed task list") {
      val input  = "- [x] done\n- [ ] todo\n- regular"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses ordered list with ) delimiter") {
      val input  = "1) item"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    }
  )

  val codeBlockEdgeCasesSpec = suite("Code Block Edge Cases")(
    test("parses code block with 4+ backticks") {
      val input  = "````\ncode with ``` inside\n````"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.CodeBlock(code, _) => code.contains("```")
          case _                        => false
        }
      )
    },
    test("parses code block with info string containing spaces") {
      val input  = "```scala sbt\ncode\n```"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses empty code block") {
      val input  = "```\n```"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.CodeBlock(code, _) => code.isEmpty
          case _                        => false
        }
      )
    },
    test("parses code block with trailing spaces in fence") {
      val input  = "```   \ncode\n```"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses indented code block") {
      val input  = "    code line 1\n    code line 2"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("preserves blank lines in code block") {
      val input  = "```\nline 1\n\nline 3\n```"
      val result = Parser.parse(input)
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head match {
          case Block.CodeBlock(code, _) => code.contains("\n\n")
          case _                        => false
        }
      )
    }
  )

  val emphasisEdgeCasesSpec = suite("Emphasis Edge Cases")(
    test("parses emphasis at start of line") {
      val result = Parser.parse("*emphasis* text")
      assertTrue(result.isRight)
    },
    test("parses emphasis at end of line") {
      val result = Parser.parse("text *emphasis*")
      assertTrue(result.isRight)
    },
    test("parses emphasis with punctuation") {
      val result = Parser.parse("*emphasis*.")
      assertTrue(result.isRight)
    },
    test("parses nested emphasis") {
      val result = Parser.parse("***bold and italic***")
      assertTrue(result.isRight)
    },
    test("parses emphasis with underscores inside") {
      val result = Parser.parse("*foo_bar_baz*")
      assertTrue(result.isRight)
    },
    test("parses emphasis correctly") {
      val result = Parser.parse("*emphasis*")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head
          .asInstanceOf[Block.Paragraph]
          .children
          .exists(_.isInstanceOf[Inline.Emphasis])
      )
    },
    test("parses strong emphasis") {
      val result = Parser.parse("**strong**")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head
          .asInstanceOf[Block.Paragraph]
          .children
          .exists(_.isInstanceOf[Inline.Strong])
      )
    },
    test("parses emphasis spanning multiple words") {
      val result = Parser.parse("*multiple words here*")
      assertTrue(result.isRight)
    }
  )

  val blockQuoteEdgeCasesSpec = suite("BlockQuote Edge Cases")(
    test("parses block quote without space after >") {
      val result = Parser.parse(">quoted")
      assertTrue(
        result.isRight,
        result.toOption.get.blocks.head.isInstanceOf[Block.BlockQuote]
      )
    },
    test("parses deeply nested block quotes") {
      val result = Parser.parse("> > > deeply nested")
      assertTrue(result.isRight)
    },
    test("parses block quote with heading") {
      val result = Parser.parse("> # Heading")
      assertTrue(result.isRight)
    },
    test("parses block quote with code block") {
      val result = Parser.parse("> ```\n> code\n> ```")
      assertTrue(result.isRight)
    },
    test("parses block quote with list") {
      val result = Parser.parse("> - item 1\n> - item 2")
      assertTrue(result.isRight)
    },
    test("parses empty block quote") {
      val result = Parser.parse(">")
      assertTrue(result.isRight)
    },
    test("parses block quote with blank lines") {
      val result = Parser.parse("> para 1\n>\n> para 2")
      assertTrue(result.isRight)
    }
  )

  val tableEdgeCasesSpec = suite("Table Edge Cases")(
    test("parses table with empty cells") {
      val input  = "| A | B |\n|---|---|\n|   |   |"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses table with single column") {
      val input  = "| A |\n|---|\n| 1 |"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses table with many columns") {
      val input  = "| A | B | C | D | E |\n|---|---|---|---|---|\n| 1 | 2 | 3 | 4 | 5 |"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses table with escaped pipes") {
      val input  = "| A \\| B | C |\n|---|---|\n| 1 \\| 2 | 3 |"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses table with formatted content") {
      val input  = "| **Bold** | *Italic* |\n|---|---|\n| `code` | [link](url) |"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses table with no rows") {
      val input  = "| A | B |\n|---|---|"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    },
    test("parses table with extra spaces") {
      val input  = "|  A  |  B  |\n|-----|-----|\n|  1  |  2  |"
      val result = Parser.parse(input)
      assertTrue(result.isRight)
    }
  )

  val escapeSpec = suite("Escape Sequences")(
    test("escapes backslash") {
      val result = Parser.parse("\\\\")
      assertTrue(result.isRight)
    },
    test("escapes backtick") {
      val result = Parser.parse("\\`")
      assertTrue(result.isRight)
    },
    test("escapes asterisk") {
      val result = Parser.parse("\\*")
      assertTrue(result.isRight)
    },
    test("escapes underscore") {
      val result = Parser.parse("\\_")
      assertTrue(result.isRight)
    },
    test("escapes curly braces") {
      val result = Parser.parse("\\{\\}")
      assertTrue(result.isRight)
    },
    test("escapes square brackets") {
      val result = Parser.parse("\\[\\]")
      assertTrue(result.isRight)
    },
    test("escapes parentheses") {
      val result = Parser.parse("\\(\\)")
      assertTrue(result.isRight)
    },
    test("escapes hash") {
      val result = Parser.parse("\\#")
      assertTrue(result.isRight)
    },
    test("escapes plus") {
      val result = Parser.parse("\\+")
      assertTrue(result.isRight)
    },
    test("escapes minus") {
      val result = Parser.parse("\\-")
      assertTrue(result.isRight)
    },
    test("escapes dot") {
      val result = Parser.parse("\\.")
      assertTrue(result.isRight)
    },
    test("escapes exclamation") {
      val result = Parser.parse("\\!")
      assertTrue(result.isRight)
    },
    test("escapes pipe") {
      val result = Parser.parse("\\|")
      assertTrue(result.isRight)
    }
  )

  val whitespaceSpec = suite("Whitespace Handling")(
    test("handles CRLF line endings") {
      val result = Parser.parse("line 1\r\nline 2")
      assertTrue(result.isRight)
    },
    test("handles CR line endings") {
      val result = Parser.parse("line 1\rline 2")
      assertTrue(result.isRight)
    },
    test("handles tabs") {
      val result = Parser.parse("\tindented")
      assertTrue(result.isRight)
    },
    test("handles multiple blank lines") {
      val result = Parser.parse("para 1\n\n\n\npara 2")
      assertTrue(result.isRight)
    },
    test("handles trailing whitespace") {
      val result = Parser.parse("text   \n")
      assertTrue(result.isRight)
    },
    test("handles leading whitespace") {
      val result = Parser.parse("   text")
      assertTrue(result.isRight)
    },
    test("handles Unicode whitespace") {
      val result = Parser.parse("text\u00A0text") // non-breaking space
      assertTrue(result.isRight)
    }
  )
}
