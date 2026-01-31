package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._

object AdtSpec extends MarkdownBaseSpec {
  def spec = suite("ADT")(
    suite("Block")(
      test("Paragraph can hold Chunk[Inline]") {
        val p = Paragraph(Chunk(Text("hello")))
        assertTrue(p.content.size == 1)
      },
      test("Paragraph with multiple inlines") {
        val p = Paragraph(Chunk(Text("hello"), Text(" "), Text("world")))
        assertTrue(p.content.size == 3)
      },
      test("Heading uses HeadingLevel") {
        val h = Heading(HeadingLevel.H1, Chunk(Text("Title")))
        assertTrue(h.level.value == 1, h.content.size == 1)
      },
      test("Heading with multiple levels") {
        val h1 = Heading(HeadingLevel.H1, Chunk(Text("H1")))
        val h6 = Heading(HeadingLevel.H6, Chunk(Text("H6")))
        assertTrue(h1.level.value == 1, h6.level.value == 6)
      },
      test("CodeBlock with info string") {
        val cb = CodeBlock(Some("scala"), "val x = 42")
        assertTrue(cb.info.contains("scala"), cb.code == "val x = 42")
      },
      test("CodeBlock without info string") {
        val cb = CodeBlock(None, "code")
        assertTrue(cb.info.isEmpty, cb.code == "code")
      },
      test("ThematicBreak is singleton") {
        val tb1 = ThematicBreak
        val tb2 = ThematicBreak
        assertTrue(tb1 == tb2)
      },
      test("BlockQuote contains Chunk[Block]") {
        val bq = BlockQuote(Chunk(Paragraph(Chunk(Text("quoted")))))
        assertTrue(bq.content.size == 1)
      },
      test("BulletList with tight flag") {
        val item = ListItem(Chunk(Paragraph(Chunk(Text("item")))), None)
        val bl   = BulletList(Chunk(item), tight = true)
        assertTrue(bl.items.size == 1, bl.tight == true)
      },
      test("BulletList with loose flag") {
        val item = ListItem(Chunk(Paragraph(Chunk(Text("item")))), None)
        val bl   = BulletList(Chunk(item), tight = false)
        assertTrue(bl.tight == false)
      },
      test("OrderedList with start number") {
        val item = ListItem(Chunk(Paragraph(Chunk(Text("item")))), None)
        val ol   = OrderedList(1, Chunk(item), tight = true)
        assertTrue(ol.start == 1, ol.items.size == 1)
      },
      test("ListItem with checked state") {
        val li1 = ListItem(Chunk(Paragraph(Chunk(Text("task")))), Some(true))
        val li2 = ListItem(Chunk(Paragraph(Chunk(Text("todo")))), Some(false))
        val li3 = ListItem(Chunk(Paragraph(Chunk(Text("para")))), None)
        assertTrue(li1.checked == Some(true), li2.checked == Some(false), li3.checked == None)
      },
      test("HtmlBlock with content") {
        val hb = HtmlBlock("<div>raw html</div>")
        assertTrue(hb.content == "<div>raw html</div>")
      },
      test("Table with header and rows") {
        val header = TableRow(Chunk(Chunk(Text("Name")), Chunk(Text("Age"))))
        val row    = TableRow(Chunk(Chunk(Text("Alice")), Chunk(Text("30"))))
        val table  = Table(header, Chunk(Alignment.Left, Alignment.Right), Chunk(row))
        assertTrue(
          table.header.cells.size == 2,
          table.alignments.size == 2,
          table.rows.size == 1
        )
      }
    ),
    suite("Inline")(
      test("Text holds string value") {
        val t = Text("hello")
        assertTrue(t.value == "hello")
      },
      test("Code holds inline code") {
        val c = Code("variable")
        assertTrue(c.value == "variable")
      },
      test("Emphasis contains Chunk[Inline]") {
        val e = Emphasis(Chunk(Text("emphasized")))
        assertTrue(e.content.size == 1)
      },
      test("Strong contains Chunk[Inline]") {
        val s = Strong(Chunk(Text("bold")))
        assertTrue(s.content.size == 1)
      },
      test("Strikethrough contains Chunk[Inline]") {
        val st = Strikethrough(Chunk(Text("deleted")))
        assertTrue(st.content.size == 1)
      },
      test("Link with url and no title") {
        val l = Link(Chunk(Text("click")), "https://example.com", None)
        assertTrue(l.url == "https://example.com", l.title.isEmpty)
      },
      test("Link with url and title") {
        val l = Link(Chunk(Text("click")), "https://example.com", Some("Example"))
        assertTrue(l.title == Some("Example"))
      },
      test("Image with alt and url") {
        val img = Image("alt text", "https://example.com/img.png", None)
        assertTrue(img.alt == "alt text", img.url == "https://example.com/img.png")
      },
      test("Image with title") {
        val img = Image("alt", "img.png", Some("Image Title"))
        assertTrue(img.title == Some("Image Title"))
      },
      test("HtmlInline with content") {
        val hi = HtmlInline("<span>inline html</span>")
        assertTrue(hi.content == "<span>inline html</span>")
      },
      test("SoftBreak is singleton") {
        val sb1 = SoftBreak
        val sb2 = SoftBreak
        assertTrue(sb1 == sb2)
      },
      test("HardBreak is singleton") {
        val hb1 = HardBreak
        val hb2 = HardBreak
        assertTrue(hb1 == hb2)
      },
      test("Autolink with url") {
        val al = Autolink("https://example.com", isEmail = false)
        assertTrue(al.url == "https://example.com", al.isEmail == false)
      },
      test("Autolink with email") {
        val al = Autolink("user@example.com", isEmail = true)
        assertTrue(al.isEmail == true)
      }
    ),
    suite("HeadingLevel")(
      test("All heading levels have correct values") {
        assertTrue(
          HeadingLevel.H1.value == 1,
          HeadingLevel.H2.value == 2,
          HeadingLevel.H3.value == 3,
          HeadingLevel.H4.value == 4,
          HeadingLevel.H5.value == 5,
          HeadingLevel.H6.value == 6
        )
      },
      test("fromInt returns Some for valid levels 1-6") {
        assertTrue(
          HeadingLevel.fromInt(1) == Some(HeadingLevel.H1),
          HeadingLevel.fromInt(2) == Some(HeadingLevel.H2),
          HeadingLevel.fromInt(3) == Some(HeadingLevel.H3),
          HeadingLevel.fromInt(4) == Some(HeadingLevel.H4),
          HeadingLevel.fromInt(5) == Some(HeadingLevel.H5),
          HeadingLevel.fromInt(6) == Some(HeadingLevel.H6)
        )
      },
      test("fromInt returns None for invalid levels") {
        assertTrue(
          HeadingLevel.fromInt(0) == None,
          HeadingLevel.fromInt(7) == None,
          HeadingLevel.fromInt(-1) == None,
          HeadingLevel.fromInt(100) == None
        )
      },
      test("unsafeFromInt returns value for valid levels") {
        assertTrue(
          HeadingLevel.unsafeFromInt(1) == HeadingLevel.H1,
          HeadingLevel.unsafeFromInt(6) == HeadingLevel.H6
        )
      },
      test("unsafeFromInt throws for invalid levels") {
        assertTrue(
          try { HeadingLevel.unsafeFromInt(0); false }
          catch { case _: IllegalArgumentException => true }
        ) &&
        assertTrue(
          try { HeadingLevel.unsafeFromInt(7); false }
          catch { case _: IllegalArgumentException => true }
        )
      }
    ),
    suite("Alignment")(
      test("Left alignment") {
        val a = Alignment.Left
        assertTrue(a == Alignment.Left)
      },
      test("Right alignment") {
        val a = Alignment.Right
        assertTrue(a == Alignment.Right)
      },
      test("Center alignment") {
        val a = Alignment.Center
        assertTrue(a == Alignment.Center)
      },
      test("None alignment") {
        val a = Alignment.None
        assertTrue(a == Alignment.None)
      }
    ),
    suite("TableRow")(
      test("TableRow with single cell") {
        val tr = TableRow(Chunk(Chunk(Text("cell"))))
        assertTrue(tr.cells.size == 1)
      },
      test("TableRow with multiple cells") {
        val tr = TableRow(Chunk(Chunk(Text("A")), Chunk(Text("B")), Chunk(Text("C"))))
        assertTrue(tr.cells.size == 3)
      },
      test("TableRow with cells containing multiple inlines") {
        val cell = Chunk(Text("Bold: "), Strong(Chunk(Text("text"))))
        val tr   = TableRow(Chunk(cell))
        assertTrue(tr.cells.size == 1, tr.cells(0).size == 2)
      }
    ),
    suite("Doc")(
      test("Doc wraps Chunk[Block]") {
        val doc = Doc(Chunk(Paragraph(Chunk(Text("hello")))))
        assertTrue(doc.blocks.size == 1)
      },
      test("Doc with empty blocks") {
        val doc = Doc(Chunk.empty)
        assertTrue(doc.blocks.size == 0)
      },
      test("Doc with multiple blocks") {
        val doc = Doc(
          Chunk(
            Heading(HeadingLevel.H1, Chunk(Text("Title"))),
            Paragraph(Chunk(Text("Content")))
          )
        )
        assertTrue(doc.blocks.size == 2)
      },
      test("Doc with default metadata") {
        val doc = Doc(Chunk(Paragraph(Chunk(Text("hi")))))
        assertTrue(doc.metadata.isEmpty)
      },
      test("Doc with custom metadata") {
        val meta = Map("author" -> "John", "date" -> "2024-01-30")
        val doc  = Doc(Chunk(Paragraph(Chunk(Text("hi")))), meta)
        assertTrue(doc.metadata == meta)
      },
      suite("++ concatenation")(
        test("concatenates blocks") {
          val doc1     = Doc(Chunk(Paragraph(Chunk(Text("a")))))
          val doc2     = Doc(Chunk(Paragraph(Chunk(Text("b")))))
          val combined = doc1 ++ doc2
          assertTrue(combined.blocks.size == 2)
        },
        test("merges metadata") {
          val doc1 = Doc(Chunk.empty, Map("k1" -> "v1"))
          val doc2 = Doc(Chunk.empty, Map("k2" -> "v2"))
          assertTrue((doc1 ++ doc2).metadata == Map("k1" -> "v1", "k2" -> "v2"))
        },
        test("right metadata wins on conflict") {
          val doc1 = Doc(Chunk.empty, Map("k" -> "left"))
          val doc2 = Doc(Chunk.empty, Map("k" -> "right"))
          assertTrue((doc1 ++ doc2).metadata == Map("k" -> "right"))
        }
      ),
      suite("toString")(
        test("renders as markdown") {
          val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hi")))))
          assertTrue(doc.toString == "# Hi\n")
        }
      ),
      suite("normalize")(
        test("merges adjacent Text nodes") {
          val doc        = Doc(Chunk(Paragraph(Chunk(Text("a"), Text("b"), Text("c")))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(Paragraph(Chunk(Text("abc")))))
        },
        test("removes empty Text nodes") {
          val doc        = Doc(Chunk(Paragraph(Chunk(Text(""), Text("x"), Text("")))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(Paragraph(Chunk(Text("x")))))
        },
        test("removes empty Paragraphs") {
          val doc        = Doc(Chunk(Paragraph(Chunk.empty), Paragraph(Chunk(Text("keep")))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(Paragraph(Chunk(Text("keep")))))
        },
        test("normalizes nested structures") {
          val doc        = Doc(Chunk(Paragraph(Chunk(Strong(Chunk(Text("a"), Text("b")))))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(Paragraph(Chunk(Strong(Chunk(Text("ab")))))))
        },
        test("normalizes BlockQuote content") {
          val doc        = Doc(Chunk(BlockQuote(Chunk(Paragraph(Chunk(Text("a"), Text("b")))))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(BlockQuote(Chunk(Paragraph(Chunk(Text("ab")))))))
        },
        test("normalizes Inline.Emphasis variant") {
          val doc        = Doc(Chunk(Paragraph(Chunk(Inline.Emphasis(Chunk(Text("a"), Text("b")))))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(Paragraph(Chunk(Emphasis(Chunk(Text("ab")))))))
        },
        test("normalizes Inline.Strong variant") {
          val doc        = Doc(Chunk(Paragraph(Chunk(Inline.Strong(Chunk(Text("a"), Text("b")))))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(Paragraph(Chunk(Strong(Chunk(Text("ab")))))))
        },
        test("normalizes Inline.Strikethrough variant") {
          val doc        = Doc(Chunk(Paragraph(Chunk(Inline.Strikethrough(Chunk(Text("a"), Text("b")))))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(Paragraph(Chunk(Strikethrough(Chunk(Text("ab")))))))
        },
        test("normalizes Inline.Link variant") {
          val doc        = Doc(Chunk(Paragraph(Chunk(Inline.Link(Chunk(Text("a"), Text("b")), "url", None)))))
          val normalized = doc.normalize
          assertTrue(normalized.blocks == Chunk(Paragraph(Chunk(Link(Chunk(Text("ab")), "url", None)))))
        }
      ),
      suite("equality via normalization")(
        test("structurally different but semantically equal docs are equal") {
          val doc1 = Doc(Chunk(Paragraph(Chunk(Text("a"), Text("b")))))
          val doc2 = Doc(Chunk(Paragraph(Chunk(Text("ab")))))
          assertTrue(doc1 == doc2)
        },
        test("hashCode is consistent with equals") {
          val doc1 = Doc(Chunk(Paragraph(Chunk(Text("a"), Text("b")))))
          val doc2 = Doc(Chunk(Paragraph(Chunk(Text("ab")))))
          assertTrue(doc1.hashCode == doc2.hashCode)
        },
        test("different content docs are not equal") {
          val doc1 = Doc(Chunk(Paragraph(Chunk(Text("hello")))))
          val doc2 = Doc(Chunk(Paragraph(Chunk(Text("world")))))
          assertTrue(doc1 != doc2)
        },
        test("equals returns false for non-Doc objects") {
          val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
          assertTrue(
            !doc.equals("a string"),
            !doc.equals(42),
            !doc.equals(null)
          )
        }
      ),
      suite("instance methods")(
        test("toHtml returns HTML via Doc instance method") {
          val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
          assertTrue(doc.toHtml.contains("<p>Hello</p>"))
        },
        test("toHtmlFragment returns fragment via Doc instance method") {
          val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
          assertTrue(
            doc.toHtmlFragment.contains("<p>Hello</p>"),
            !doc.toHtmlFragment.contains("<!DOCTYPE")
          )
        },
        test("toTerminal returns ANSI output via Doc instance method") {
          val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
          assertTrue(doc.toTerminal.contains("Hello"))
        }
      ),
      suite("normalize edge cases")(
        test("normalizes ListItem blocks in BulletList") {
          val doc = Doc(
            Chunk(
              BulletList(
                Chunk(ListItem(Chunk(Paragraph(Chunk(Text("a"), Text("b")))), None)),
                tight = true
              )
            )
          )
          val normalized = doc.normalize
          val list       = normalized.blocks.head.asInstanceOf[BulletList]
          val item       = list.items.head
          val para       = item.content.head.asInstanceOf[Paragraph]
          assertTrue(para.content.size == 1, para.content.head == Text("ab"))
        },
        test("normalizes ListItem blocks in OrderedList") {
          val doc = Doc(
            Chunk(
              OrderedList(
                1,
                Chunk(ListItem(Chunk(Paragraph(Chunk(Text("x"), Text("y")))), Some(true))),
                tight = true
              )
            )
          )
          val normalized = doc.normalize
          val list       = normalized.blocks.head.asInstanceOf[OrderedList]
          val item       = list.items.head
          val para       = item.content.head.asInstanceOf[Paragraph]
          assertTrue(para.content.size == 1, para.content.head == Text("xy"))
        },
        test("normalizeInlines merges adjacent Inline.Text variants") {
          val inlines    = Chunk[Inline](Inline.Text("a"), Inline.Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("normalizeInlines merges Text with Inline.Text") {
          val inlines    = Chunk[Inline](Text("a"), Inline.Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("normalizeInlines merges Inline.Text with Text") {
          val inlines    = Chunk[Inline](Inline.Text("a"), Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("normalizeInlines filters empty Inline.Text") {
          val inlines    = Chunk[Inline](Inline.Text("a"), Inline.Text(""), Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("isEmpty returns true for paragraph with only empty Inline.Text") {
          val para = Paragraph(Chunk(Inline.Text("")))
          assertTrue(Doc.isEmpty(para))
        },
        test("normalizes Table row content") {
          val header     = TableRow(Chunk(Chunk(Text("a"), Text("b"))))
          val row        = TableRow(Chunk(Chunk(Text("x"), Text("y"))))
          val doc        = Doc(Chunk(Table(header, Chunk(Alignment.Left), Chunk(row))))
          val normalized = doc.normalize
          val table      = normalized.blocks.head.asInstanceOf[Table]
          assertTrue(
            table.header.cells.head.head == Text("ab"),
            table.rows.head.cells.head.head == Text("xy")
          )
        },
        test("normalizeBlock handles standalone ListItem") {
          val item           = ListItem(Chunk(Paragraph(Chunk(Text("a"), Text("b")))), Some(true))
          val normalized     = Doc.normalizeBlock(item)
          val normalizedItem = normalized.asInstanceOf[ListItem]
          val para           = normalizedItem.content.head.asInstanceOf[Paragraph]
          assertTrue(para.content.size == 1, para.content.head == Text("ab"))
        },
        test("normalizeInlines filters empty Text") {
          val inlines    = Chunk[Inline](Text("a"), Text(""), Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("isEmpty returns true for paragraph with only empty Text") {
          val para = Paragraph(Chunk(Text("")))
          assertTrue(Doc.isEmpty(para))
        }
      )
    )
  )
}
