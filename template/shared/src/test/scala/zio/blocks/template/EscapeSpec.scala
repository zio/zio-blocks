package zio.blocks.template

import zio.test._

object EscapeSpec extends ZIOSpecDefault {
  def spec = suite("Escape")(
    suite("html")(
      test("empty string passthrough") {
        assertTrue(Escape.html("") == "")
      },
      test("safe string passthrough") {
        assertTrue(Escape.html("safe text 123") == "safe text 123")
      },
      test("escapes ampersand") {
        assertTrue(Escape.html("a&b") == "a&amp;b")
      },
      test("escapes less than") {
        assertTrue(Escape.html("<script>") == "&lt;script&gt;")
      },
      test("escapes greater than") {
        assertTrue(Escape.html("a>b") == "a&gt;b")
      },
      test("escapes double quotes") {
        assertTrue(Escape.html(""""quoted"""") == "&quot;quoted&quot;")
      },
      test("escapes single quotes") {
        assertTrue(Escape.html("'single'") == "&#x27;single&#x27;")
      },
      test("escapes all special chars together") {
        assertTrue(Escape.html("""<b>&"'""") == "&lt;b&gt;&amp;&quot;&#x27;")
      }
    ),
    suite("jsString")(
      test("empty string passthrough") {
        assertTrue(Escape.jsString("") == "")
      },
      test("safe string produces output") {
        val result = Escape.jsString("hello")
        assertTrue(result == "hello")
      },
      test("escapes double quotes") {
        assertTrue(Escape.jsString("""a"b""") == """a\"b""")
      },
      test("escapes backslash") {
        assertTrue(Escape.jsString("a\\b") == "a\\\\b")
      },
      test("escapes newline") {
        assertTrue(Escape.jsString("line\nbreak") == "line\\nbreak")
      },
      test("escapes carriage return") {
        assertTrue(Escape.jsString("line\rbreak") == "line\\rbreak")
      },
      test("escapes tab") {
        assertTrue(Escape.jsString("a\tb") == "a\\tb")
      },
      test("escapes angle brackets for script safety") {
        val result = Escape.jsString("</script>")
        assertTrue(result == "\\u003c/script\\u003e")
      },
      test("escapes single quotes") {
        assertTrue(Escape.jsString("a'b") == "a\\'b")
      },
      test("escapes ampersand in JS") {
        assertTrue(Escape.jsString("a&b") == "a\\u0026b")
      },
      test("escapes control characters") {
        val result = Escape.jsString("a\u0001b")
        assertTrue(result == "a\\u0001b")
      },
      test("escapes greater-than sign") {
        assertTrue(Escape.jsString("a>b") == "a\\u003eb")
      },
      test("jsString escapes Unicode line separators") {
        assertTrue(
          Escape.jsString("a\u2028b\u2029c") == "a\\u2028b\\u2029c"
        )
      }
    ),
    suite("cssString")(
      test("empty string passthrough") {
        assertTrue(Escape.cssString("") == "")
      },
      test("safe string passthrough") {
        assertTrue(Escape.cssString("safe") == "safe")
      },
      test("escapes backslash") {
        assertTrue(Escape.cssString("a\\b") == "a\\\\b")
      },
      test("escapes double quotes") {
        assertTrue(Escape.cssString("""a"b""") == """a\"b""")
      },
      test("escapes single quotes") {
        assertTrue(Escape.cssString("a'b") == "a\\'b")
      },
      test("escapes angle brackets") {
        val result = Escape.cssString("<div>")
        assertTrue(result == "\\3c div\\3e ")
      },
      test("escapes ampersand") {
        assertTrue(Escape.cssString("a&b") == "a\\26 b")
      },
      test("cssString with all escape types at once") {
        val result = Escape.cssString("""\"'<>&""")
        assertTrue(result == """\\\"\'\3c \3e \26 """)
      }
    ),
    suite("sanitizeUrl")(
      test("blocks javascript: scheme") {
        assertTrue(Escape.sanitizeUrl("javascript:alert(1)") == "unsafe:javascript:alert(1)")
      },
      test("blocks JavaScript: with mixed case") {
        assertTrue(Escape.sanitizeUrl("JavaScript:alert(1)") == "unsafe:JavaScript:alert(1)")
      },
      test("blocks vbscript: scheme") {
        assertTrue(Escape.sanitizeUrl("vbscript:MsgBox") == "unsafe:vbscript:MsgBox")
      },
      test("blocks data:text/html scheme") {
        assertTrue(Escape.sanitizeUrl("data:text/html,<h1>hi</h1>") == "unsafe:data:text/html,<h1>hi</h1>")
      },
      test("blocks javascript: with leading whitespace") {
        assertTrue(Escape.sanitizeUrl("  javascript:alert(1)") == "unsafe:  javascript:alert(1)")
      },
      test("allows https: scheme") {
        assertTrue(Escape.sanitizeUrl("https://example.com") == "https://example.com")
      },
      test("allows relative URLs") {
        assertTrue(Escape.sanitizeUrl("/path/to/page") == "/path/to/page")
      },
      test("allows data:image URLs") {
        assertTrue(Escape.sanitizeUrl("data:image/png;base64,abc") == "data:image/png;base64,abc")
      }
    )
  )
}
