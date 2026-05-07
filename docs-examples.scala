import zio.blocks.docs._
import zio.blocks.chunk.Chunk

object DocsExamples {
  def main(args: Array[String]): Unit = {
    println("=" * 80)
    println("1. DOCUMENT COMPOSITION & RENDERING")
    println("=" * 80)

    val doc = Doc(
      Chunk(
        Heading(HeadingLevel.H1, Chunk(Text("Hello World"))),
        Paragraph(Chunk(Text("This is a paragraph with "), Strong(Chunk(Text("bold"))), Text(" text.")))
      )
    )

    println("\n[Markdown Output]")
    println(doc.toString())

    println("\n[HTML Fragment]")
    println(doc.toHtmlFragment())

    println("\n" + "=" * 80)
    println("2. PARSING MARKDOWN")
    println("=" * 80)

    val markdown = """# Getting Started

This is a guide with:
- List item 1
- List item 2

Here's some `code` inline.
"""

    val parsed = Parser.parse(markdown)
    parsed match {
      case Right(doc) =>
        println(s"\n[Successfully Parsed]")
        println(s"Blocks: ${doc.blocks.size}")
        doc.blocks.zipWithIndex.foreach { case (block, idx) =>
          println(s"  $idx: ${block.getClass.getSimpleName}")
        }
      case Left(err) =>
        println(s"\n[Parse Error at line ${err.line}:${err.column}]")
        println(err.message)
    }

    println("\n" + "=" * 80)
    println("3. ROUND-TRIP SEMANTICS")
    println("=" * 80)

    val input = "# Hello\n\nWorld"
    println(s"\n[Original Input]")
    println(input)

    val parsed1  = Parser.parse(input).toOption.get
    val rendered = Renderer.render(parsed1)
    println(s"\n[Rendered]")
    println(rendered)

    val parsed2 = Parser.parse(rendered).toOption.get
    println(s"[After Re-parsing]")
    println(s"Original == ReparsedNormalized: ${parsed1 == parsed2}")

    println("\n" + "=" * 80)
    println("4. INLINE FORMATTING")
    println("=" * 80)

    val inlineDoc = Doc(
      Chunk(
        Paragraph(
          Chunk(
            Text("Text with "),
            Emphasis(Chunk(Text("italic"))),
            Text(", "),
            Strong(Chunk(Text("bold"))),
            Text(", "),
            Strikethrough(Chunk(Text("strikethrough"))),
            Text(", and "),
            Code("inline code"),
            Text(".")
          )
        )
      )
    )

    println("\n[Markdown]")
    println(Renderer.render(inlineDoc))

    println("\n" + "=" * 80)
    println("5. LINKS")
    println("=" * 80)

    val linkDoc = Doc(
      Chunk(
        Paragraph(
          Chunk(
            Link(Chunk(Text("ZIO Blocks")), "https://github.com/zio/zio-blocks", None),
            Text(" is awesome.")
          )
        )
      )
    )

    println("\n[Markdown]")
    println(Renderer.render(linkDoc))

    println("\n[HTML Fragment]")
    println(linkDoc.toHtmlFragment())

    println("\n" + "=" * 80)
    println("6. LISTS")
    println("=" * 80)

    val listDoc = Doc(
      Chunk(
        BulletList(
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("First item")))), None),
            ListItem(Chunk(Paragraph(Chunk(Text("Second item")))), None),
            ListItem(Chunk(Paragraph(Chunk(Text("Third item")))), None)
          ),
          tight = true
        )
      )
    )

    println("\n[Markdown]")
    println(Renderer.render(listDoc))

    println("\n" + "=" * 80)
    println("7. CODE BLOCKS")
    println("=" * 80)

    val codeDoc = Doc(
      Chunk(
        CodeBlock(Some("scala"), "val x = 42\nprintln(x)")
      )
    )

    println("\n[Markdown]")
    println(Renderer.render(codeDoc))

    println("\n[HTML Fragment]")
    println(codeDoc.toHtmlFragment())

    println("\n" + "=" * 80)
    println("8. TABLES")
    println("=" * 80)

    val tableDoc = Doc(
      Chunk(
        Table(
          TableRow(
            Chunk(
              Chunk(Text("Name")),
              Chunk(Text("Age")),
              Chunk(Text("Role"))
            )
          ),
          Chunk(Alignment.None, Alignment.None, Alignment.None),
          Chunk(
            TableRow(
              Chunk(
                Chunk(Text("Alice")),
                Chunk(Text("30")),
                Chunk(Text("Engineer"))
              )
            ),
            TableRow(
              Chunk(
                Chunk(Text("Bob")),
                Chunk(Text("28")),
                Chunk(Text("Designer"))
              )
            )
          )
        )
      )
    )

    println("\n[Markdown]")
    println(Renderer.render(tableDoc))

    println("\n" + "=" * 80)
    println("9. TASK LISTS")
    println("=" * 80)

    val taskDoc = Doc(
      Chunk(
        BulletList(
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("Write documentation")))), Some(true)),
            ListItem(Chunk(Paragraph(Chunk(Text("Fix bugs")))), Some(false)),
            ListItem(Chunk(Paragraph(Chunk(Text("Add tests")))), None)
          ),
          tight = true
        )
      )
    )

    println("\n[Markdown]")
    println(Renderer.render(taskDoc))

    println("\n[HTML Fragment]")
    println(taskDoc.toHtmlFragment())

    println("\n" + "=" * 80)
    println("10. BLOCK QUOTES")
    println("=" * 80)

    val quoteDoc = Doc(
      Chunk(
        BlockQuote(
          Chunk(
            Paragraph(Chunk(Text("This is a quoted statement.")))
          )
        )
      )
    )

    println("\n[Markdown]")
    println(Renderer.render(quoteDoc))

    println("\n" + "=" * 80)
  }
}
