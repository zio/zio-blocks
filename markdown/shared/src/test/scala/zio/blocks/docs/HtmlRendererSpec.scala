package zio.blocks.docs

import zio.blocks.chunk.Chunk
import zio.test._

object HtmlRendererSpec extends MarkdownBaseSpec {

  def spec: Spec[Any, Throwable] = suite("HtmlRenderer")(
    suite("renderFragment - Blocks")(
      test("Paragraph") {
        val block  = Paragraph(Chunk(Text("hello world")))
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<p>hello world</p>")
      },
      test("Heading H1") {
        val block  = Heading(HeadingLevel.H1, Chunk(Text("Title")))
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<h1>Title</h1>")
      },
      test("Heading H2") {
        val block  = Heading(HeadingLevel.H2, Chunk(Text("Title")))
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<h2>Title</h2>")
      },
      test("Heading H3") {
        val block  = Heading(HeadingLevel.H3, Chunk(Text("Title")))
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<h3>Title</h3>")
      },
      test("Heading H4") {
        val block  = Heading(HeadingLevel.H4, Chunk(Text("Title")))
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<h4>Title</h4>")
      },
      test("Heading H5") {
        val block  = Heading(HeadingLevel.H5, Chunk(Text("Title")))
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<h5>Title</h5>")
      },
      test("Heading H6") {
        val block  = Heading(HeadingLevel.H6, Chunk(Text("Title")))
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<h6>Title</h6>")
      },
      test("CodeBlock without language") {
        val block  = CodeBlock(None, "val x = 1")
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<pre><code>val x = 1</code></pre>")
      },
      test("CodeBlock with language") {
        val block  = CodeBlock(Some("scala"), "val x = 1")
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == """<pre><code class="language-scala">val x = 1</code></pre>""")
      },
      test("CodeBlock with HTML entities") {
        val block  = CodeBlock(None, "<script>alert('xss')</script>")
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<pre><code>&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;</code></pre>")
      },
      test("ThematicBreak") {
        val result = HtmlRenderer.renderBlock(ThematicBreak)
        assertTrue(result == "<hr>")
      },
      test("BlockQuote") {
        val block  = BlockQuote(Chunk(Paragraph(Chunk(Text("quoted text")))))
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == "<blockquote><p>quoted text</p></blockquote>")
      },
      test("BulletList") {
        val list = BulletList(
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("item 1")))), None),
            ListItem(Chunk(Paragraph(Chunk(Text("item 2")))), None)
          ),
          tight = false
        )
        val result = HtmlRenderer.renderBlock(list)
        assertTrue(result == "<ul><li><p>item 1</p></li><li><p>item 2</p></li></ul>")
      },
      test("OrderedList starting at 1") {
        val list = OrderedList(
          1,
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("first")))), None),
            ListItem(Chunk(Paragraph(Chunk(Text("second")))), None)
          ),
          tight = false
        )
        val result = HtmlRenderer.renderBlock(list)
        assertTrue(result == "<ol><li><p>first</p></li><li><p>second</p></li></ol>")
      },
      test("OrderedList with custom start") {
        val list = OrderedList(
          5,
          Chunk(
            ListItem(Chunk(Paragraph(Chunk(Text("fifth")))), None),
            ListItem(Chunk(Paragraph(Chunk(Text("sixth")))), None)
          ),
          tight = false
        )
        val result = HtmlRenderer.renderBlock(list)
        assertTrue(result == """<ol start="5"><li><p>fifth</p></li><li><p>sixth</p></li></ol>""")
      },
      test("Task list items") {
        val checked   = ListItem(Chunk(Paragraph(Chunk(Text("done")))), Some(true))
        val unchecked = ListItem(Chunk(Paragraph(Chunk(Text("todo")))), Some(false))
        val normal    = ListItem(Chunk(Paragraph(Chunk(Text("normal")))), None)

        assertTrue(
          HtmlRenderer.renderBlock(checked) == """<li><input type="checkbox" checked disabled><p>done</p></li>""",
          HtmlRenderer.renderBlock(unchecked) == """<li><input type="checkbox" disabled><p>todo</p></li>""",
          HtmlRenderer.renderBlock(normal) == "<li><p>normal</p></li>"
        )
      },
      test("HtmlBlock passthrough") {
        val block  = HtmlBlock("<div class=\"custom\">raw html</div>")
        val result = HtmlRenderer.renderBlock(block)
        assertTrue(result == """<div class="custom">raw html</div>""")
      },
      test("Table") {
        val table = Table(
          header = TableRow(Chunk(Chunk(Text("Name")), Chunk(Text("Age")))),
          alignments = Chunk(Alignment.Left, Alignment.Right),
          rows = Chunk(
            TableRow(Chunk(Chunk(Text("Alice")), Chunk(Text("30")))),
            TableRow(Chunk(Chunk(Text("Bob")), Chunk(Text("25"))))
          )
        )
        val result = HtmlRenderer.renderBlock(table)
        assertTrue(
          result == """<table><thead><tr><th style="text-align:left">Name</th><th style="text-align:right">Age</th></tr></thead><tbody><tr><td style="text-align:left">Alice</td><td style="text-align:right">30</td></tr><tr><td style="text-align:left">Bob</td><td style="text-align:right">25</td></tr></tbody></table>"""
        )
      },
      test("Table with all alignment types") {
        val table = Table(
          header = TableRow(Chunk(Chunk(Text("L")), Chunk(Text("R")), Chunk(Text("C")), Chunk(Text("N")))),
          alignments = Chunk(Alignment.Left, Alignment.Right, Alignment.Center, Alignment.None),
          rows = Chunk(TableRow(Chunk(Chunk(Text("a")), Chunk(Text("b")), Chunk(Text("c")), Chunk(Text("d")))))
        )
        val result = HtmlRenderer.renderBlock(table)
        assertTrue(
          result.contains("""<th style="text-align:left">L</th>"""),
          result.contains("""<th style="text-align:right">R</th>"""),
          result.contains("""<th style="text-align:center">C</th>"""),
          result.contains("<th>N</th>")
        )
      }
    ),
    suite("renderFragment - Inlines")(
      test("Text") {
        val inline = Text("hello")
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "hello")
      },
      test("Text with HTML entities") {
        val inline = Text("""<script>&"'</script>""")
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "&lt;script&gt;&amp;&quot;&#39;&lt;/script&gt;")
      },
      test("Text - both variants") {
        val inline1 = Text("test")
        val inline2 = Inline.Text("test")
        assertTrue(
          HtmlRenderer.renderInline(inline1) == "test",
          HtmlRenderer.renderInline(inline2) == "test"
        )
      },
      test("Code") {
        val inline = Code("val x = 1")
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "<code>val x = 1</code>")
      },
      test("Code with HTML entities") {
        val inline = Code("<script>")
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "<code>&lt;script&gt;</code>")
      },
      test("Code - both variants") {
        val inline1 = Code("test")
        val inline2 = Inline.Code("test")
        assertTrue(
          HtmlRenderer.renderInline(inline1) == "<code>test</code>",
          HtmlRenderer.renderInline(inline2) == "<code>test</code>"
        )
      },
      test("Emphasis") {
        val inline = Emphasis(Chunk(Text("italic")))
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "<em>italic</em>")
      },
      test("Emphasis - both variants") {
        val inline1 = Emphasis(Chunk(Text("test")))
        val inline2 = Inline.Emphasis(Chunk(Text("test")))
        assertTrue(
          HtmlRenderer.renderInline(inline1) == "<em>test</em>",
          HtmlRenderer.renderInline(inline2) == "<em>test</em>"
        )
      },
      test("Strong") {
        val inline = Strong(Chunk(Text("bold")))
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "<strong>bold</strong>")
      },
      test("Strong - both variants") {
        val inline1 = Strong(Chunk(Text("test")))
        val inline2 = Inline.Strong(Chunk(Text("test")))
        assertTrue(
          HtmlRenderer.renderInline(inline1) == "<strong>test</strong>",
          HtmlRenderer.renderInline(inline2) == "<strong>test</strong>"
        )
      },
      test("Strikethrough") {
        val inline = Strikethrough(Chunk(Text("deleted")))
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "<del>deleted</del>")
      },
      test("Strikethrough - both variants") {
        val inline1 = Strikethrough(Chunk(Text("test")))
        val inline2 = Inline.Strikethrough(Chunk(Text("test")))
        assertTrue(
          HtmlRenderer.renderInline(inline1) == "<del>test</del>",
          HtmlRenderer.renderInline(inline2) == "<del>test</del>"
        )
      },
      test("Link without title") {
        val inline = Link(Chunk(Text("click here")), "https://example.com", None)
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<a href="https://example.com">click here</a>""")
      },
      test("Link with title") {
        val inline = Link(Chunk(Text("click")), "https://example.com", Some("tooltip"))
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<a href="https://example.com" title="tooltip">click</a>""")
      },
      test("Link with HTML entities in URL and title") {
        val inline = Link(Chunk(Text("link")), "https://example.com?a=1&b=2", Some("A & B"))
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<a href="https://example.com?a=1&amp;b=2" title="A &amp; B">link</a>""")
      },
      test("Link - both variants") {
        val inline1 = Link(Chunk(Text("test")), "url", None)
        val inline2 = Inline.Link(Chunk(Text("test")), "url", None)
        assertTrue(
          HtmlRenderer.renderInline(inline1) == """<a href="url">test</a>""",
          HtmlRenderer.renderInline(inline2) == """<a href="url">test</a>"""
        )
      },
      test("Image without title") {
        val inline = Image("alt text", "https://example.com/img.png", None)
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<img src="https://example.com/img.png" alt="alt text">""")
      },
      test("Image with title") {
        val inline = Image("alt", "https://example.com/img.png", Some("tooltip"))
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<img src="https://example.com/img.png" alt="alt" title="tooltip">""")
      },
      test("Image with HTML entities") {
        val inline = Image("<script>", "url?a=1&b=2", Some("A & B"))
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<img src="url?a=1&amp;b=2" alt="&lt;script&gt;" title="A &amp; B">""")
      },
      test("Image - both variants") {
        val inline1 = Image("alt", "url", None)
        val inline2 = Inline.Image("alt", "url", None)
        assertTrue(
          HtmlRenderer.renderInline(inline1) == """<img src="url" alt="alt">""",
          HtmlRenderer.renderInline(inline2) == """<img src="url" alt="alt">"""
        )
      },
      test("HtmlInline passthrough") {
        val inline = HtmlInline("<span class=\"custom\">raw</span>")
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<span class="custom">raw</span>""")
      },
      test("HtmlInline - both variants") {
        val inline1 = HtmlInline("<br/>")
        val inline2 = Inline.HtmlInline("<br/>")
        assertTrue(
          HtmlRenderer.renderInline(inline1) == "<br/>",
          HtmlRenderer.renderInline(inline2) == "<br/>"
        )
      },
      test("SoftBreak") {
        val inline = SoftBreak
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == " ")
      },
      test("SoftBreak - both variants") {
        assertTrue(
          HtmlRenderer.renderInline(SoftBreak) == " ",
          HtmlRenderer.renderInline(Inline.SoftBreak) == " "
        )
      },
      test("HardBreak") {
        val inline = HardBreak
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "<br>")
      },
      test("HardBreak - both variants") {
        assertTrue(
          HtmlRenderer.renderInline(HardBreak) == "<br>",
          HtmlRenderer.renderInline(Inline.HardBreak) == "<br>"
        )
      },
      test("Autolink URL") {
        val inline = Autolink("https://example.com", isEmail = false)
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<a href="https://example.com">https://example.com</a>""")
      },
      test("Autolink email") {
        val inline = Autolink("user@example.com", isEmail = true)
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == """<a href="mailto:user@example.com">user@example.com</a>""")
      },
      test("Autolink - both variants") {
        val inline1 = Autolink("url", isEmail = false)
        val inline2 = Inline.Autolink("url", isEmail = false)
        assertTrue(
          HtmlRenderer.renderInline(inline1) == """<a href="url">url</a>""",
          HtmlRenderer.renderInline(inline2) == """<a href="url">url</a>"""
        )
      },
      test("Nested inlines") {
        val inline = Strong(Chunk(Emphasis(Chunk(Text("bold italic")))))
        val result = HtmlRenderer.renderInline(inline)
        assertTrue(result == "<strong><em>bold italic</em></strong>")
      }
    ),
    suite("render - Full document")(
      test("empty document") {
        val doc    = Doc(Chunk.empty)
        val result = HtmlRenderer.render(doc)
        assertTrue(result == "<!DOCTYPE html><html><head></head><body></body></html>")
      },
      test("document with single paragraph") {
        val doc    = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
        val result = HtmlRenderer.render(doc)
        assertTrue(result == "<!DOCTYPE html><html><head></head><body><p>Hello</p></body></html>")
      },
      test("document with multiple blocks") {
        val doc = Doc(
          Chunk(
            Heading(HeadingLevel.H1, Chunk(Text("Title"))),
            Paragraph(Chunk(Text("content")))
          )
        )
        val result = HtmlRenderer.render(doc)
        assertTrue(result == "<!DOCTYPE html><html><head></head><body><h1>Title</h1><p>content</p></body></html>")
      }
    ),
    suite("escape")(
      test("escapes ampersand") {
        assertTrue(HtmlRenderer.escape("a&b") == "a&amp;b")
      },
      test("escapes less-than") {
        assertTrue(HtmlRenderer.escape("a<b") == "a&lt;b")
      },
      test("escapes greater-than") {
        assertTrue(HtmlRenderer.escape("a>b") == "a&gt;b")
      },
      test("escapes double quote") {
        assertTrue(HtmlRenderer.escape("""a"b""") == "a&quot;b")
      },
      test("escapes single quote") {
        assertTrue(HtmlRenderer.escape("a'b") == "a&#39;b")
      },
      test("escapes all entities") {
        assertTrue(HtmlRenderer.escape("""<>&"'""") == "&lt;&gt;&amp;&quot;&#39;")
      },
      test("order matters - ampersand first") {
        assertTrue(HtmlRenderer.escape("&lt;") == "&amp;lt;")
      }
    )
  )
}
