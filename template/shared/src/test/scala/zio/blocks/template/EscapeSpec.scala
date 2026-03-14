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
        assertTrue(Escape.html("\"quoted\"") == "&quot;quoted&quot;")
      },
      test("escapes single quotes") {
        assertTrue(Escape.html("'single'") == "&#x27;single&#x27;")
      },
      test("escapes all special chars together") {
        assertTrue(Escape.html("<b>&\"'") == "&lt;b&gt;&amp;&quot;&#x27;")
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
        assertTrue(Escape.jsString("a\"b") == "a\\\"b")
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
        assertTrue(result.contains("\\u003c"))
      },
      test("escapes single quotes") {
        assertTrue(Escape.jsString("a'b") == "a\\'b")
      },
      test("escapes ampersand in JS") {
        assertTrue(Escape.jsString("a&b") == "a\\u0026b")
      },
      test("escapes control characters") {
        val result = Escape.jsString("a\u0001b")
        assertTrue(result.contains("\\u0001"))
      },
      test("escapes greater-than sign") {
        assertTrue(Escape.jsString("a>b").contains("\\u003e"))
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
        assertTrue(Escape.cssString("a\"b") == "a\\\"b")
      },
      test("escapes single quotes") {
        assertTrue(Escape.cssString("a'b") == "a\\'b")
      },
      test("escapes angle brackets") {
        val result = Escape.cssString("<div>")
        assertTrue(result.contains("\\3c ") && result.contains("\\3e "))
      },
      test("escapes ampersand") {
        assertTrue(Escape.cssString("a&b") == "a\\26 b")
      },
      test("cssString with all escape types at once") {
        val result = Escape.cssString("\\\"'<>&")
        assertTrue(
          result.contains("\\\\"),
          result.contains("\\\"")
        )
      }
    )
  )
}
