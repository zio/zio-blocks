package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.docs._
import zio.test._

object MarkdownSchemaSpec extends SchemaBaseSpec {

  def spec = suite("MarkdownSchemaSpec")(
    suite("Doc Schema")(
      test("Schema[Doc] should exist and round-trip") {
        val schema = implicitly[Schema[Doc]]
        val doc    = Doc(Chunk(Paragraph(Chunk(Inline.Text("Hello")))), Map("key" -> "value"))
        val result = schema.fromDynamicValue(schema.toDynamicValue(doc))
        assertTrue(result == Right(doc))
      }
    ),
    suite("Block Schema")(
      test("Schema[Block] should exist and round-trip") {
        val schema = implicitly[Schema[Block]]
        val block  = Paragraph(Chunk(Inline.Text("Test paragraph")))
        val result = schema.fromDynamicValue(schema.toDynamicValue(block))
        assertTrue(result == Right(block))
      },
      test("Schema[Paragraph] should exist and round-trip") {
        val schema    = implicitly[Schema[Paragraph]]
        val paragraph = Paragraph(Chunk(Inline.Text("Hello"), Inline.Text(" world")))
        val result    = schema.fromDynamicValue(schema.toDynamicValue(paragraph))
        assertTrue(result == Right(paragraph))
      },
      test("Schema[Heading] should exist and round-trip") {
        val schema  = implicitly[Schema[Heading]]
        val heading = Heading(HeadingLevel.H1, Chunk(Inline.Text("Title")))
        val result  = schema.fromDynamicValue(schema.toDynamicValue(heading))
        assertTrue(result == Right(heading))
      },
      test("Schema[CodeBlock] should exist and round-trip") {
        val schema    = implicitly[Schema[CodeBlock]]
        val codeBlock = CodeBlock(Some("scala"), "val x = 42")
        val result    = schema.fromDynamicValue(schema.toDynamicValue(codeBlock))
        assertTrue(result == Right(codeBlock))
      },
      test("Schema[ThematicBreak.type] should exist and round-trip") {
        val schema         = implicitly[Schema[ThematicBreak.type]]
        val thematicBreak  = ThematicBreak
        val result         = schema.fromDynamicValue(schema.toDynamicValue(thematicBreak))
        assertTrue(result == Right(thematicBreak))
      },
      test("Schema[BlockQuote] should exist and round-trip") {
        val schema     = implicitly[Schema[BlockQuote]]
        val blockQuote = BlockQuote(Chunk(Paragraph(Chunk(Inline.Text("Quote")))))
        val result     = schema.fromDynamicValue(schema.toDynamicValue(blockQuote))
        assertTrue(result == Right(blockQuote))
      },
      test("Schema[BulletList] should exist and round-trip") {
        val schema     = implicitly[Schema[BulletList]]
        val bulletList = BulletList(
          Chunk(ListItem(Chunk(Paragraph(Chunk(Inline.Text("Item 1")))), None)),
          tight = true
        )
        val result = schema.fromDynamicValue(schema.toDynamicValue(bulletList))
        assertTrue(result == Right(bulletList))
      },
      test("Schema[OrderedList] should exist and round-trip") {
        val schema      = implicitly[Schema[OrderedList]]
        val orderedList = OrderedList(
          1,
          Chunk(ListItem(Chunk(Paragraph(Chunk(Inline.Text("Item 1")))), None)),
          tight = false
        )
        val result = schema.fromDynamicValue(schema.toDynamicValue(orderedList))
        assertTrue(result == Right(orderedList))
      },
      test("Schema[ListItem] should exist and round-trip") {
        val schema   = implicitly[Schema[ListItem]]
        val listItem = ListItem(Chunk(Paragraph(Chunk(Inline.Text("Item")))), Some(true))
        val result   = schema.fromDynamicValue(schema.toDynamicValue(listItem))
        assertTrue(result == Right(listItem))
      },
      test("Schema[HtmlBlock] should exist and round-trip") {
        val schema    = implicitly[Schema[HtmlBlock]]
        val htmlBlock = HtmlBlock("<div>HTML</div>")
        val result    = schema.fromDynamicValue(schema.toDynamicValue(htmlBlock))
        assertTrue(result == Right(htmlBlock))
      },
      test("Schema[Table] should exist and round-trip") {
        val schema = implicitly[Schema[Table]]
        val table  = Table(
          TableRow(Chunk(Chunk(Inline.Text("Header")))),
          Chunk(Alignment.Left),
          Chunk(TableRow(Chunk(Chunk(Inline.Text("Data")))))
        )
        val result = schema.fromDynamicValue(schema.toDynamicValue(table))
        assertTrue(result == Right(table))
      }
    ),
    suite("Inline Schema - Companion Object Variants")(
      test("Schema[Inline] should exist and round-trip") {
        val schema = implicitly[Schema[Inline]]
        val inline = Inline.Text("Hello")
        val result = schema.fromDynamicValue(schema.toDynamicValue(inline))
        assertTrue(result == Right(inline))
      },
      test("Schema[Inline.Text] should exist and round-trip") {
        val schema = implicitly[Schema[Inline.Text]]
        val text   = Inline.Text("Plain text")
        val result = schema.fromDynamicValue(schema.toDynamicValue(text))
        assertTrue(result == Right(text))
      },
      test("Schema[Inline.Code] should exist and round-trip") {
        val schema = implicitly[Schema[Inline.Code]]
        val code   = Inline.Code("val x = 42")
        val result = schema.fromDynamicValue(schema.toDynamicValue(code))
        assertTrue(result == Right(code))
      },
      test("Schema[Inline.Emphasis] should exist and round-trip") {
        val schema   = implicitly[Schema[Inline.Emphasis]]
        val emphasis = Inline.Emphasis(Chunk(Inline.Text("emphasized")))
        val result   = schema.fromDynamicValue(schema.toDynamicValue(emphasis))
        assertTrue(result == Right(emphasis))
      },
      test("Schema[Inline.Strong] should exist and round-trip") {
        val schema = implicitly[Schema[Inline.Strong]]
        val strong = Inline.Strong(Chunk(Inline.Text("bold")))
        val result = schema.fromDynamicValue(schema.toDynamicValue(strong))
        assertTrue(result == Right(strong))
      },
      test("Schema[Inline.Strikethrough] should exist and round-trip") {
        val schema        = implicitly[Schema[Inline.Strikethrough]]
        val strikethrough = Inline.Strikethrough(Chunk(Inline.Text("struck")))
        val result        = schema.fromDynamicValue(schema.toDynamicValue(strikethrough))
        assertTrue(result == Right(strikethrough))
      },
      test("Schema[Inline.Link] should exist and round-trip") {
        val schema = implicitly[Schema[Inline.Link]]
        val link   = Inline.Link(Chunk(Inline.Text("Example")), "https://example.com", Some("Title"))
        val result = schema.fromDynamicValue(schema.toDynamicValue(link))
        assertTrue(result == Right(link))
      },
      test("Schema[Inline.Image] should exist and round-trip") {
        val schema = implicitly[Schema[Inline.Image]]
        val image  = Inline.Image("Alt text", "https://example.com/image.png", None)
        val result = schema.fromDynamicValue(schema.toDynamicValue(image))
        assertTrue(result == Right(image))
      },
      test("Schema[Inline.HtmlInline] should exist and round-trip") {
        val schema     = implicitly[Schema[Inline.HtmlInline]]
        val htmlInline = Inline.HtmlInline("<span>HTML</span>")
        val result     = schema.fromDynamicValue(schema.toDynamicValue(htmlInline))
        assertTrue(result == Right(htmlInline))
      },
      test("Schema[Inline.SoftBreak.type] should exist and round-trip") {
        val schema    = implicitly[Schema[Inline.SoftBreak.type]]
        val softBreak = Inline.SoftBreak
        val result    = schema.fromDynamicValue(schema.toDynamicValue(softBreak))
        assertTrue(result == Right(softBreak))
      },
      test("Schema[Inline.HardBreak.type] should exist and round-trip") {
        val schema    = implicitly[Schema[Inline.HardBreak.type]]
        val hardBreak = Inline.HardBreak
        val result    = schema.fromDynamicValue(schema.toDynamicValue(hardBreak))
        assertTrue(result == Right(hardBreak))
      },
      test("Schema[Inline.Autolink] should exist and round-trip") {
        val schema   = implicitly[Schema[Inline.Autolink]]
        val autolink = Inline.Autolink("https://example.com", isEmail = false)
        val result   = schema.fromDynamicValue(schema.toDynamicValue(autolink))
        assertTrue(result == Right(autolink))
      }
    ),
    suite("HeadingLevel Schema")(
      test("Schema[HeadingLevel] should exist and round-trip") {
        val schema = implicitly[Schema[HeadingLevel]]
        val level  = HeadingLevel.H1
        val result = schema.fromDynamicValue(schema.toDynamicValue(level))
        assertTrue(result == Right(level))
      }
    ),
    suite("Alignment Schema")(
      test("Schema[Alignment] should exist and round-trip") {
        val schema    = implicitly[Schema[Alignment]]
        val alignment = Alignment.Left
        val result    = schema.fromDynamicValue(schema.toDynamicValue(alignment))
        assertTrue(result == Right(alignment))
      }
    ),
    suite("TableRow Schema")(
      test("Schema[TableRow] should exist and round-trip") {
        val schema   = implicitly[Schema[TableRow]]
        val tableRow = TableRow(Chunk(Chunk(Inline.Text("Cell 1")), Chunk(Inline.Text("Cell 2"))))
        val result   = schema.fromDynamicValue(schema.toDynamicValue(tableRow))
        assertTrue(result == Right(tableRow))
      }
    )
  )
}
