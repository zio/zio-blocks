package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._
import zio.test.Assertion._

object MdInterpolatorSpec extends MarkdownBaseSpec {
  def spec = suite("md interpolator")(
    suite("Headings")(
      test("parses H1") {
        val doc = md"# Hello"
        assertTrue(doc == Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello"))))))
      },
      test("parses H2") {
        val doc = md"## World"
        assertTrue(doc == Doc(Chunk(Heading(HeadingLevel.H2, Chunk(Text("World"))))))
      },
      test("parses H3") {
        val doc = md"### Level 3"
        assertTrue(doc == Doc(Chunk(Heading(HeadingLevel.H3, Chunk(Text("Level 3"))))))
      },
      test("parses H4") {
        val doc = md"#### Level 4"
        assertTrue(doc == Doc(Chunk(Heading(HeadingLevel.H4, Chunk(Text("Level 4"))))))
      },
      test("parses H5") {
        val doc = md"##### Level 5"
        assertTrue(doc == Doc(Chunk(Heading(HeadingLevel.H5, Chunk(Text("Level 5"))))))
      },
      test("parses H6") {
        val doc = md"###### Deep"
        assertTrue(doc == Doc(Chunk(Heading(HeadingLevel.H6, Chunk(Text("Deep"))))))
      },
      test("parses heading with emphasis") {
        val doc = md"# Hello *world*"
        assertTrue(
          doc == Doc(
            Chunk(
              Heading(HeadingLevel.H1, Chunk(Text("Hello "), Emphasis(Chunk(Text("world")))))
            )
          )
        )
      },
      test("parses heading with strong") {
        val doc = md"## Title with **bold**"
        assertTrue(
          doc == Doc(
            Chunk(
              Heading(HeadingLevel.H2, Chunk(Text("Title with "), Strong(Chunk(Text("bold")))))
            )
          )
        )
      }
    ),
    suite("Paragraphs")(
      test("simple paragraph") {
        val doc = md"Hello world"
        assertTrue(doc == Doc(Chunk(Paragraph(Chunk(Text("Hello world"))))))
      },
      test("paragraph with emphasis") {
        val doc = md"Text with *emphasis* here"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(Chunk(Text("Text with "), Emphasis(Chunk(Text("emphasis"))), Text(" here")))
            )
          )
        )
      },
      test("paragraph with strong") {
        val doc = md"Text with **strong** here"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(Chunk(Text("Text with "), Strong(Chunk(Text("strong"))), Text(" here")))
            )
          )
        )
      },
      test("paragraph with strikethrough") {
        val doc = md"Text with ~~deleted~~ here"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(Chunk(Text("Text with "), Strikethrough(Chunk(Text("deleted"))), Text(" here")))
            )
          )
        )
      },
      test("paragraph with inline code") {
        val doc = md"Use `code` here"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(Chunk(Text("Use "), Code("code"), Text(" here")))
            )
          )
        )
      },
      test("paragraph with link") {
        val doc = md"Visit [example](https://example.com) now"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(
                Chunk(
                  Text("Visit "),
                  Link(Chunk(Text("example")), "https://example.com", None),
                  Text(" now")
                )
              )
            )
          )
        )
      },
      test("paragraph with image") {
        val doc = md"See ![alt text](url) here"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(Chunk(Text("See "), Image("alt text", "url", None), Text(" here")))
            )
          )
        )
      },
      test("paragraph with mixed inline formatting") {
        val doc = md"Mix *emphasis* and **strong** with `code`"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(
                Chunk(
                  Text("Mix "),
                  Emphasis(Chunk(Text("emphasis"))),
                  Text(" and "),
                  Strong(Chunk(Text("strong"))),
                  Text(" with "),
                  Code("code")
                )
              )
            )
          )
        )
      }
    ),
    suite("String interpolation")(
      test("interpolates String into paragraph") {
        val name = "World"
        val doc  = md"Hello $name"
        assertTrue(doc == Doc(Chunk(Paragraph(Chunk(Text("Hello World"))))))
      },
      test("interpolates String with surrounding text") {
        val user = "Alice"
        val doc  = md"Welcome, $user!"
        assertTrue(doc == Doc(Chunk(Paragraph(Chunk(Text("Welcome, Alice!"))))))
      },
      test("interpolates multiple Strings") {
        val first = "John"
        val last  = "Doe"
        val doc   = md"Name: $first $last"
        assertTrue(doc == Doc(Chunk(Paragraph(Chunk(Text("Name: John Doe"))))))
      }
    ),
    suite("Numeric interpolation")(
      test("interpolates Int") {
        val n   = 42
        val doc = md"Value: $n"
        assertTrue(doc == Doc(Chunk(Paragraph(Chunk(Text("Value: 42"))))))
      },
      test("interpolates Long") {
        val n   = 1234567890L
        val doc = md"Large: $n"
        assertTrue(doc == Doc(Chunk(Paragraph(Chunk(Text("Large: 1234567890"))))))
      },
      test("interpolates Double") {
        val n   = 3.14
        val doc = md"Pi: $n"
        assertTrue(doc == Doc(Chunk(Paragraph(Chunk(Text("Pi: 3.14"))))))
      },
      test("interpolates Boolean") {
        val flag = true
        val doc  = md"Flag: $flag"
        assertTrue(doc == Doc(Chunk(Paragraph(Chunk(Text("Flag: true"))))))
      }
    ),
    suite("Inline interpolation")(
      test("interpolates Inline value - Strong") {
        val bold: Inline = Strong(Chunk(Text("bold")))
        val doc          = md"This is $bold text"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(Chunk(Text("This is "), Strong(Chunk(Text("bold"))), Text(" text")))
            )
          )
        )
      },
      test("interpolates Inline value - Emphasis") {
        val em: Inline = Emphasis(Chunk(Text("italic")))
        val doc        = md"This is $em text"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(Chunk(Text("This is "), Emphasis(Chunk(Text("italic"))), Text(" text")))
            )
          )
        )
      },
      test("interpolates Inline value - Code") {
        val code: Inline = Code("x + y")
        val doc          = md"Formula: $code"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(Chunk(Text("Formula: "), Code("x + y")))
            )
          )
        )
      },
      test("interpolates multiple Inline values") {
        val bold: Inline   = Strong(Chunk(Text("bold")))
        val italic: Inline = Emphasis(Chunk(Text("italic")))
        val doc            = md"Mix $bold and $italic here"
        assertTrue(
          doc == Doc(
            Chunk(
              Paragraph(
                Chunk(
                  Text("Mix "),
                  Strong(Chunk(Text("bold"))),
                  Text(" and "),
                  Emphasis(Chunk(Text("italic"))),
                  Text(" here")
                )
              )
            )
          )
        )
      }
    ),
    suite("Code blocks")(
      test("fenced code block with language") {
        val doc = md"""```scala
val x = 1
```"""
        assertTrue(doc == Doc(Chunk(CodeBlock(Some("scala"), "val x = 1"))))
      },
      test("fenced code block without language") {
        val doc = md"""```
code here
```"""
        assertTrue(doc == Doc(Chunk(CodeBlock(None, "code here"))))
      },
      test("fenced code block with multiple lines") {
        val doc = md"""```
line 1
line 2
```"""
        assertTrue(doc == Doc(Chunk(CodeBlock(None, "line 1\nline 2"))))
      }
    ),
    suite("Lists")(
      test("bullet list") {
        val doc = md"""- item 1
- item 2"""
        assertTrue(
          doc == Doc(
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
      },
      test("ordered list") {
        val doc = md"""1. first
2. second"""
        assertTrue(
          doc == Doc(
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
      },
      test("task list") {
        val doc = md"""- [ ] unchecked
- [x] checked"""
        assertTrue(
          doc == Doc(
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
      }
    ),
    suite("Tables")(
      test("simple table") {
        val doc = md"""| a | b |
|---|---|
| 1 | 2 |"""
        assertTrue(
          doc == Doc(
            Chunk(
              Table(
                TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")))),
                Chunk(Alignment.None, Alignment.None),
                Chunk(TableRow(Chunk(Chunk(Text("1")), Chunk(Text("2")))))
              )
            )
          )
        )
      },
      test("table with alignment") {
        val doc = md"""| left | center | right |
|:---|:---:|---:|
| a | b | c |"""
        assertTrue(
          doc == Doc(
            Chunk(
              Table(
                TableRow(Chunk(Chunk(Text("left")), Chunk(Text("center")), Chunk(Text("right")))),
                Chunk(Alignment.Left, Alignment.Center, Alignment.Right),
                Chunk(TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")), Chunk(Text("c")))))
              )
            )
          )
        )
      }
    ),
    suite("Block quotes")(
      test("simple block quote") {
        val doc = md"""> quoted text"""
        assertTrue(
          doc == Doc(
            Chunk(
              BlockQuote(Chunk(Paragraph(Chunk(Text("quoted text")))))
            )
          )
        )
      },
      test("multi-line block quote") {
        val doc = md"""> line 1
> line 2"""
        assertTrue(
          doc == Doc(
            Chunk(
              BlockQuote(
                Chunk(
                  Paragraph(Chunk(Text("line 1"), SoftBreak, Text("line 2")))
                )
              )
            )
          )
        )
      }
    ),
    suite("Multiline and mixed content")(
      test("heading followed by paragraph") {
        val doc = md"""# Title

Some text here."""
        assertTrue(
          doc == Doc(
            Chunk(
              Heading(HeadingLevel.H1, Chunk(Text("Title"))),
              Paragraph(Chunk(Text("Some text here.")))
            )
          )
        )
      },
      test("complex document structure") {
        val doc = md"""# Main Title

Introduction paragraph.

## Section

```scala
val x = 1
```

- item 1
- item 2"""
        assertTrue(
          doc == Doc(
            Chunk(
              Heading(HeadingLevel.H1, Chunk(Text("Main Title"))),
              Paragraph(Chunk(Text("Introduction paragraph."))),
              Heading(HeadingLevel.H2, Chunk(Text("Section"))),
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
      },
      test("mixed inline and block interpolation") {
        val count        = 5
        val bold: Inline = Strong(Chunk(Text("important")))
        val doc          = md"""# Count: $count

This is $bold information."""
        assertTrue(
          doc == Doc(
            Chunk(
              Heading(HeadingLevel.H1, Chunk(Text("Count: 5"))),
              Paragraph(Chunk(Text("This is "), Strong(Chunk(Text("important"))), Text(" information.")))
            )
          )
        )
      }
    ),
    suite("Thematic breaks")(
      test("thematic break with dashes") {
        val doc = md"---"
        assertTrue(doc == Doc(Chunk(ThematicBreak)))
      },
      test("thematic break with asterisks") {
        val doc = md"***"
        assertTrue(doc == Doc(Chunk(ThematicBreak)))
      }
    ),
    suite("Compile-time validation")(
      test("rejects unclosed code fence") {
        typeCheck {
          """
          import zio.blocks.docs._
          md"```scala\nval x = 1"
          """
        }.map(assert(_)(isLeft(containsString("Invalid markdown"))))
      },
      test("rejects type without ToMarkdown instance") {
        typeCheck {
          """
          import zio.blocks.docs._
          case class Custom(value: Int)
          val c = Custom(1)
          md"Value: $c"
          """
        }.map(assert(_)(isLeft(containsString("No ToMarkdown instance"))))
      },
      test("rejects class without ToMarkdown instance") {
        typeCheck {
          """
          import zio.blocks.docs._
          class MyClass
          val obj = new MyClass
          md"Object: $obj"
          """
        }.map(assert(_)(isLeft(containsString("No ToMarkdown instance"))))
      },
      test("accepts List with ToMarkdown instance") {
        typeCheck {
          """
          import zio.blocks.docs._
          val list: List[Int] = List(1, 2, 3)
          md"Items: $list"
          """
        }.map(assert(_)(isRight))
      },
      test("rejects Map without ToMarkdown instance") {
        typeCheck {
          """
          import zio.blocks.docs._
          val map: Map[String, Int] = Map("a" -> 1)
          md"Mapping: $map"
          """
        }.map(assert(_)(isLeft(containsString("No ToMarkdown instance"))))
      }
    )
  )
}
