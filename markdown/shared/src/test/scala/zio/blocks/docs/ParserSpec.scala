package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._

object ParserSpec extends MarkdownBaseSpec {
  def spec = suite("Parser")(
    suite("Headings")(
      test("parses H1") {
        val result = Parser.parse("# Hello")
        assertTrue(result == Right(Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))))
      },
      test("parses H2") {
        val result = Parser.parse("## World")
        assertTrue(result == Right(Doc(Chunk(Heading(HeadingLevel.H2, Chunk(Text("World")))))))
      },
      test("parses H3") {
        val result = Parser.parse("### Level 3")
        assertTrue(result == Right(Doc(Chunk(Heading(HeadingLevel.H3, Chunk(Text("Level 3")))))))
      },
      test("parses H4") {
        val result = Parser.parse("#### Level 4")
        assertTrue(result == Right(Doc(Chunk(Heading(HeadingLevel.H4, Chunk(Text("Level 4")))))))
      },
      test("parses H5") {
        val result = Parser.parse("##### Level 5")
        assertTrue(result == Right(Doc(Chunk(Heading(HeadingLevel.H5, Chunk(Text("Level 5")))))))
      },
      test("parses H6") {
        val result = Parser.parse("###### Deep")
        assertTrue(result == Right(Doc(Chunk(Heading(HeadingLevel.H6, Chunk(Text("Deep")))))))
      },
      test("parses heading with inline formatting") {
        val result = Parser.parse("# Hello *world*")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Heading(HeadingLevel.H1, Chunk(Text("Hello "), Emphasis(Chunk(Text("world")))))
              )
            )
          )
        )
      },
      test("parses heading with trailing hashes") {
        val result = Parser.parse("## Heading ##")
        assertTrue(result == Right(Doc(Chunk(Heading(HeadingLevel.H2, Chunk(Text("Heading")))))))
      }
    ),
    suite("Paragraphs")(
      test("parses simple paragraph") {
        val result = Parser.parse("Hello world")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("Hello world")))))))
      },
      test("parses multiple paragraphs") {
        val result = Parser.parse("Para 1\n\nPara 2")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Text("Para 1"))),
                Paragraph(Chunk(Text("Para 2")))
              )
            )
          )
        )
      },
      test("parses paragraph with soft break") {
        val result = Parser.parse("Line 1\nLine 2")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Text("Line 1"), SoftBreak, Text("Line 2")))
              )
            )
          )
        )
      },
      test("parses paragraph with hard break (two spaces)") {
        val result = Parser.parse("Line 1  \nLine 2")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Text("Line 1"), HardBreak, Text("Line 2")))
              )
            )
          )
        )
      },
      test("parses paragraph with hard break (backslash)") {
        val result = Parser.parse("Line 1\\\nLine 2")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Text("Line 1"), HardBreak, Text("Line 2")))
              )
            )
          )
        )
      }
    ),
    suite("Code blocks")(
      test("parses fenced code block with backticks") {
        val input  = "```scala\nval x = 1\n```"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(CodeBlock(Some("scala"), "val x = 1")))))
      },
      test("parses fenced code block with tildes") {
        val input  = "~~~python\nprint('hello')\n~~~"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(CodeBlock(Some("python"), "print('hello')")))))
      },
      test("parses fenced code block without info string") {
        val input  = "```\ncode here\n```"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(CodeBlock(None, "code here")))))
      },
      test("parses fenced code block with multiple lines") {
        val input  = "```\nline 1\nline 2\nline 3\n```"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(CodeBlock(None, "line 1\nline 2\nline 3")))))
      }
    ),
    suite("Thematic breaks")(
      test("parses thematic break with dashes") {
        val result = Parser.parse("---")
        assertTrue(result == Right(Doc(Chunk(ThematicBreak))))
      },
      test("parses thematic break with asterisks") {
        val result = Parser.parse("***")
        assertTrue(result == Right(Doc(Chunk(ThematicBreak))))
      },
      test("parses thematic break with underscores") {
        val result = Parser.parse("___")
        assertTrue(result == Right(Doc(Chunk(ThematicBreak))))
      },
      test("parses thematic break with more than 3 chars") {
        val result = Parser.parse("-----")
        assertTrue(result == Right(Doc(Chunk(ThematicBreak))))
      },
      test("parses thematic break with spaces") {
        val result = Parser.parse("- - -")
        assertTrue(result == Right(Doc(Chunk(ThematicBreak))))
      }
    ),
    suite("Block quotes")(
      test("parses simple block quote") {
        val result = Parser.parse("> quoted text")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BlockQuote(Chunk(Paragraph(Chunk(Text("quoted text")))))
              )
            )
          )
        )
      },
      test("parses multi-line block quote") {
        val result = Parser.parse("> line 1\n> line 2")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BlockQuote(
                  Chunk(
                    Paragraph(Chunk(Text("line 1"), SoftBreak, Text("line 2")))
                  )
                )
              )
            )
          )
        )
      },
      test("parses nested block quote") {
        val result = Parser.parse("> > nested")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BlockQuote(
                  Chunk(
                    BlockQuote(Chunk(Paragraph(Chunk(Text("nested")))))
                  )
                )
              )
            )
          )
        )
      }
    ),
    suite("Bullet lists")(
      test("parses bullet list with dash") {
        val input  = "- item 1\n- item 2"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BulletList(
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("item 1")))), None),
                    ListItem(Chunk(Paragraph(Chunk(Text("item 2")))), None)
                  ),
                  tight = true
                )
              )
            )
          )
        )
      },
      test("parses bullet list with asterisk") {
        val input  = "* item 1\n* item 2"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BulletList(
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("item 1")))), None),
                    ListItem(Chunk(Paragraph(Chunk(Text("item 2")))), None)
                  ),
                  tight = true
                )
              )
            )
          )
        )
      },
      test("parses bullet list with plus") {
        val input  = "+ item 1\n+ item 2"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BulletList(
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("item 1")))), None),
                    ListItem(Chunk(Paragraph(Chunk(Text("item 2")))), None)
                  ),
                  tight = true
                )
              )
            )
          )
        )
      },
      test("parses task list unchecked") {
        val input  = "- [ ] unchecked"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BulletList(
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("unchecked")))), Some(false))
                  ),
                  tight = true
                )
              )
            )
          )
        )
      },
      test("parses task list checked") {
        val input  = "- [x] checked"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BulletList(
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("checked")))), Some(true))
                  ),
                  tight = true
                )
              )
            )
          )
        )
      },
      test("parses mixed task list") {
        val input  = "- [ ] unchecked\n- [x] checked"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BulletList(
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("unchecked")))), Some(false)),
                    ListItem(Chunk(Paragraph(Chunk(Text("checked")))), Some(true))
                  ),
                  tight = true
                )
              )
            )
          )
        )
      }
    ),
    suite("Ordered lists")(
      test("parses ordered list") {
        val input  = "1. first\n2. second"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                OrderedList(
                  1,
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("first")))), None),
                    ListItem(Chunk(Paragraph(Chunk(Text("second")))), None)
                  ),
                  tight = true
                )
              )
            )
          )
        )
      },
      test("parses ordered list starting at different number") {
        val input  = "5. fifth\n6. sixth"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                OrderedList(
                  5,
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("fifth")))), None),
                    ListItem(Chunk(Paragraph(Chunk(Text("sixth")))), None)
                  ),
                  tight = true
                )
              )
            )
          )
        )
      }
    ),
    suite("Tables")(
      test("parses simple table") {
        val input  = "| a | b |\n|---|---|\n| 1 | 2 |"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Table(
                  TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")))),
                  Chunk(Alignment.None, Alignment.None),
                  Chunk(TableRow(Chunk(Chunk(Text("1")), Chunk(Text("2")))))
                )
              )
            )
          )
        )
      },
      test("parses table with alignment") {
        val input  = "| left | center | right |\n|:---|:---:|---:|\n| a | b | c |"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Table(
                  TableRow(Chunk(Chunk(Text("left")), Chunk(Text("center")), Chunk(Text("right")))),
                  Chunk(Alignment.Left, Alignment.Center, Alignment.Right),
                  Chunk(TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")), Chunk(Text("c")))))
                )
              )
            )
          )
        )
      },
      test("parses table with multiple rows") {
        val input  = "| h1 | h2 |\n|---|---|\n| r1c1 | r1c2 |\n| r2c1 | r2c2 |"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Table(
                  TableRow(Chunk(Chunk(Text("h1")), Chunk(Text("h2")))),
                  Chunk(Alignment.None, Alignment.None),
                  Chunk(
                    TableRow(Chunk(Chunk(Text("r1c1")), Chunk(Text("r1c2")))),
                    TableRow(Chunk(Chunk(Text("r2c1")), Chunk(Text("r2c2"))))
                  )
                )
              )
            )
          )
        )
      }
    ),
    suite("HTML blocks")(
      test("parses HTML block") {
        val input  = "<div>\n  content\n</div>"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(HtmlBlock("<div>\n  content\n</div>")))))
      },
      test("parses self-closing HTML") {
        val input  = "<br/>"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(HtmlBlock("<br/>")))))
      }
    ),
    suite("Inline formatting")(
      test("parses emphasis with asterisk") {
        val result = Parser.parse("*hello*")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val em     = para.content.head.asInstanceOf[Emphasis]
        assertTrue(em.content == Chunk(Text("hello")))
      },
      test("parses emphasis with underscore") {
        val result = Parser.parse("_hello_")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Emphasis(Chunk(Text("hello")))))
              )
            )
          )
        )
      },
      test("parses strong with double asterisk") {
        val result = Parser.parse("**bold**")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val strong = para.content.head.asInstanceOf[Strong]
        assertTrue(strong.content == Chunk(Text("bold")))
      },
      test("parses strong with double underscore") {
        val result = Parser.parse("__bold__")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Strong(Chunk(Text("bold")))))
              )
            )
          )
        )
      },
      test("parses strikethrough") {
        val result = Parser.parse("~~deleted~~")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val strike = para.content.head.asInstanceOf[Strikethrough]
        assertTrue(strike.content == Chunk(Text("deleted")))
      },
      test("parses inline code") {
        val result = Parser.parse("`code`")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val code   = para.content.head.asInstanceOf[Code]
        assertTrue(code.value == "code")
      },
      test("parses inline code with double backticks") {
        val result = Parser.parse("`` `code` ``")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val code   = para.content.head.asInstanceOf[Code]
        assertTrue(code.value == "`code`")
      },
      test("parses link") {
        val result = Parser.parse("[text](url)")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val link   = para.content.head.asInstanceOf[Link]
        assertTrue(
          link.text == Chunk(Text("text")),
          link.url == "url",
          link.title.isEmpty
        )
      },
      test("parses link with title") {
        val result = Parser.parse("""[text](url "title")""")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val link   = para.content.head.asInstanceOf[Link]
        assertTrue(
          link.url == "url",
          link.title == Some("title")
        )
      },
      test("parses image") {
        val result = Parser.parse("![alt](url)")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val img    = para.content.head.asInstanceOf[Image]
        assertTrue(
          img.alt == "alt",
          img.url == "url",
          img.title.isEmpty
        )
      },
      test("parses image with title") {
        val result = Parser.parse("""![alt](url "title")""")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val img    = para.content.head.asInstanceOf[Image]
        assertTrue(
          img.url == "url",
          img.title == Some("title")
        )
      },
      test("parses autolink in angle brackets") {
        val result = Parser.parse("<https://example.com>")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val auto   = para.content.head.asInstanceOf[Autolink]
        assertTrue(
          auto.url == "https://example.com",
          auto.isEmail == false
        )
      },
      test("parses email autolink") {
        val result = Parser.parse("<user@example.com>")
        val para   = result.toOption.get.blocks.head.asInstanceOf[Paragraph]
        val auto   = para.content.head.asInstanceOf[Autolink]
        assertTrue(
          auto.url == "user@example.com",
          auto.isEmail == true
        )
      },
      test("parses bare URL") {
        val result = Parser.parse("Visit https://example.com today")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(
                  Chunk(
                    Text("Visit "),
                    Autolink("https://example.com", false),
                    Text(" today")
                  )
                )
              )
            )
          )
        )
      },
      test("parses nested emphasis in strong") {
        val result = Parser.parse("**bold *and italic***")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(
                  Chunk(
                    Strong(
                      Chunk(
                        Text("bold "),
                        Emphasis(Chunk(Text("and italic")))
                      )
                    )
                  )
                )
              )
            )
          )
        )
      }
    ),
    suite("Mixed content")(
      test("parses heading followed by paragraph") {
        val input  = "# Title\n\nSome text"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Heading(HeadingLevel.H1, Chunk(Text("Title"))),
                Paragraph(Chunk(Text("Some text")))
              )
            )
          )
        )
      },
      test("parses complex document") {
        val input = """# Title
                      |
                      |A paragraph with *emphasis* and **strong**.
                      |
                      |## Subtitle
                      |
                      |```scala
                      |val x = 1
                      |```
                      |
                      |- item 1
                      |- item 2
                      |""".stripMargin
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Heading(HeadingLevel.H1, Chunk(Text("Title"))),
                Paragraph(
                  Chunk(
                    Text("A paragraph with "),
                    Emphasis(Chunk(Text("emphasis"))),
                    Text(" and "),
                    Strong(Chunk(Text("strong"))),
                    Text(".")
                  )
                ),
                Heading(HeadingLevel.H2, Chunk(Text("Subtitle"))),
                CodeBlock(Some("scala"), "val x = 1"),
                BulletList(
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("item 1")))), None),
                    ListItem(Chunk(Paragraph(Chunk(Text("item 2")))), None)
                  ),
                  tight = true
                )
              )
            )
          )
        )
      }
    ),
    suite("Empty and whitespace")(
      test("parses empty input") {
        val result = Parser.parse("")
        assertTrue(result == Right(Doc(Chunk.empty)))
      },
      test("parses whitespace only") {
        val result = Parser.parse("   \n\n   ")
        assertTrue(result == Right(Doc(Chunk.empty)))
      }
    ),
    suite("Edge cases")(
      test("parses heading without space after hash") {
        val result = Parser.parse("#NoSpace")
        assertTrue(result == Right(Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("#NoSpace")))))))
      },
      test("parses thematic break with less than 3 chars as paragraph") {
        val result = Parser.parse("--")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("--")))))))
      },
      test("parses thematic break with mixed chars as paragraph") {
        val result = Parser.parse("-*-")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("-*-")))))))
      },
      test("parses multi-line HTML block") {
        val input  = "<div>\n  <p>content</p>\n</div>"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(HtmlBlock("<div>\n  <p>content</p>\n</div>")))))
      },
      test("parses HTML block followed by blank line") {
        val input  = "<div>content</div>\n\nParagraph"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                HtmlBlock("<div>content</div>"),
                Paragraph(Chunk(Text("Paragraph")))
              )
            )
          )
        )
      },
      test("parses unclosed angle bracket as text") {
        val result = Parser.parse("a < b")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("a < b")))))))
      },
      test("parses unclosed emphasis as text") {
        val result = Parser.parse("*unclosed")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("*unclosed")))))))
      },
      test("parses unclosed strong as text") {
        val result = Parser.parse("**unclosed")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("**unclosed")))))))
      },
      test("parses unclosed strikethrough as text") {
        val result = Parser.parse("~~unclosed")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("~~unclosed")))))))
      },
      test("parses unclosed inline code as text") {
        val result = Parser.parse("`unclosed")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("`unclosed")))))))
      },
      test("parses unclosed link as text") {
        val result = Parser.parse("[unclosed")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("[unclosed")))))))
      },
      test("parses unclosed image as text") {
        val result = Parser.parse("![unclosed")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("![unclosed")))))))
      },
      test("parses link without closing paren as text") {
        val result = Parser.parse("[text](url")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("[text](url")))))))
      },
      test("parses table without delimiter row as paragraph") {
        val result = Parser.parse("| a | b |")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("| a | b |")))))))
      },
      test("parses table with invalid delimiter as paragraph") {
        val input  = "| a | b |\n| x | y |"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Text("| a | b |"), SoftBreak, Text("| x | y |")))
              )
            )
          )
        )
      },
      test("parses triple asterisk emphasis") {
        val result = Parser.parse("***bold and italic***")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(
                  Chunk(
                    Strong(
                      Chunk(
                        Emphasis(Chunk(Text("bold and italic")))
                      )
                    )
                  )
                )
              )
            )
          )
        )
      },
      test("parses triple underscore emphasis") {
        val result = Parser.parse("___bold and italic___")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(
                  Chunk(
                    Strong(
                      Chunk(
                        Emphasis(Chunk(Text("bold and italic")))
                      )
                    )
                  )
                )
              )
            )
          )
        )
      },
      test("parses unclosed triple asterisk") {
        val result = Parser.parse("***unclosed")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("***unclosed")))))))
      },
      test("parses double backtick code without closing") {
        val result = Parser.parse("``unclosed")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("``unclosed")))))))
      },
      test("parses HTML inline tag") {
        val result = Parser.parse("text <span>inline</span> more")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(
                  Chunk(
                    Text("text "),
                    HtmlInline("<span>"),
                    Text("inline"),
                    HtmlInline("</span>"),
                    Text(" more")
                  )
                )
              )
            )
          )
        )
      },
      test("parses bare http URL") {
        val result = Parser.parse("Visit http://example.com today")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(
                  Chunk(
                    Text("Visit "),
                    Autolink("http://example.com", false),
                    Text(" today")
                  )
                )
              )
            )
          )
        )
      },
      test("parses link with title using single quotes") {
        val result = Parser.parse("[text](url)")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Link(Chunk(Text("text")), "url", None)))
              )
            )
          )
        )
      },
      test("parses nested list item content") {
        val input  = "- item 1\n  continuation"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BulletList(
                  Chunk(
                    ListItem(
                      Chunk(
                        Paragraph(Chunk(Text("item 1"), SoftBreak, Text("continuation")))
                      ),
                      None
                    )
                  ),
                  tight = true
                )
              )
            )
          )
        )
      },
      test("parses ordered list with continuation") {
        val input  = "1. item 1\n   continuation"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                OrderedList(
                  1,
                  Chunk(
                    ListItem(
                      Chunk(
                        Paragraph(Chunk(Text("item 1"), SoftBreak, Text("continuation")))
                      ),
                      None
                    )
                  ),
                  tight = true
                )
              )
            )
          )
        )
      },
      test("handles empty paragraph lines") {
        val result = Parser.parse("text\n\n")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("text")))))))
      },
      test("parses paragraph ending with block element") {
        val input  = "text\n# Heading"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Text("text"))),
                Heading(HeadingLevel.H1, Chunk(Text("Heading")))
              )
            )
          )
        )
      },
      test("parses block quote without space after marker") {
        val result = Parser.parse(">text")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BlockQuote(Chunk(Paragraph(Chunk(Text("text")))))
              )
            )
          )
        )
      },
      test("parses thematic break with only spaces between chars") {
        val result = Parser.parse("- - - - -")
        assertTrue(result == Right(Doc(Chunk(ThematicBreak))))
      },
      test("parses image without closing paren") {
        val result = Parser.parse("![alt](url")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("![alt](url")))))))
      },
      test("parses image with incomplete bracket") {
        val result = Parser.parse("![alt")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("![alt")))))))
      },
      test("parses URL at end of text") {
        val result = Parser.parse("Visit https://example.com")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(
                  Chunk(
                    Text("Visit "),
                    Autolink("https://example.com", false)
                  )
                )
              )
            )
          )
        )
      },
      test("parses text with special char at start") {
        val result = Parser.parse("*")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("*")))))))
      },
      test("parses HTML block without closing tag") {
        val input  = "<div>\ncontent"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(HtmlBlock("<div>\ncontent")))))
      },
      test("parses HTML block with unclosed angle bracket") {
        val input  = "<div"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(HtmlBlock("<div")))))
      },
      test("parses table without leading pipe") {
        val input  = "a | b\n---|---\n1 | 2"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Table(
                  TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")))),
                  Chunk(Alignment.None, Alignment.None),
                  Chunk(TableRow(Chunk(Chunk(Text("1")), Chunk(Text("2")))))
                )
              )
            )
          )
        )
      },
      test("parses table without trailing pipe") {
        val input  = "| a | b\n|---|---\n| 1 | 2"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Table(
                  TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")))),
                  Chunk(Alignment.None, Alignment.None),
                  Chunk(TableRow(Chunk(Chunk(Text("1")), Chunk(Text("2")))))
                )
              )
            )
          )
        )
      },
      test("parses link with malformed title") {
        val result = Parser.parse("""[text](url "title)""")
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                Paragraph(Chunk(Link(Chunk(Text("text")), "url \"title", None)))
              )
            )
          )
        )
      },
      test("parses triple asterisk without closing as text") {
        val result = Parser.parse("***no close")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("***no close")))))))
      },
      test("parses empty inline content") {
        val result = Parser.parse("")
        assertTrue(result == Right(Doc(Chunk.empty)))
      },
      test("parses text only paragraph") {
        val result = Parser.parse("just plain text")
        assertTrue(result == Right(Doc(Chunk(Paragraph(Chunk(Text("just plain text")))))))
      },
      test("parses HTML comment block") {
        val input  = "<!-- comment -->"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(HtmlBlock("<!-- comment -->")))))
      },
      test("parses closing HTML tag block") {
        val input  = "</div>"
        val result = Parser.parse(input)
        assertTrue(result == Right(Doc(Chunk(HtmlBlock("</div>")))))
      },
      test("parses task list with uppercase X") {
        val input  = "- [X] checked"
        val result = Parser.parse(input)
        assertTrue(
          result == Right(
            Doc(
              Chunk(
                BulletList(
                  Chunk(
                    ListItem(Chunk(Paragraph(Chunk(Text("checked")))), Some(true))
                  ),
                  tight = true
                )
              )
            )
          )
        )
      }
    )
  )
}
