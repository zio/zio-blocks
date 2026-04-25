/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
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

package zio.blocks.html

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
      },
      test("cssString escapes control characters") {
        val result = Escape.cssString("a\u0001b")
        assertTrue(result == "a\\1 b")
      }
    ),
    suite("htmlTo")(
      test("writes escaped HTML to buffer") {
        val sb = new java.lang.StringBuilder
        Escape.htmlTo("<b>&\"'", sb)
        assertTrue(sb.toString == "&lt;b&gt;&amp;&quot;&#x27;")
      },
      test("writes safe string to buffer unchanged") {
        val sb = new java.lang.StringBuilder
        Escape.htmlTo("safe text", sb)
        assertTrue(sb.toString == "safe text")
      },
      test("empty string is no-op") {
        val sb = new java.lang.StringBuilder
        Escape.htmlTo("", sb)
        assertTrue(sb.toString == "")
      }
    ),
    suite("jsStringTo")(
      test("writes escaped JS to buffer") {
        val sb = new java.lang.StringBuilder
        Escape.jsStringTo("a\"b\\c\n", sb)
        assertTrue(sb.toString == "a\\\"b\\\\c\\n")
      },
      test("writes safe string to buffer unchanged") {
        val sb = new java.lang.StringBuilder
        Escape.jsStringTo("safe", sb)
        assertTrue(sb.toString == "safe")
      },
      test("empty string is no-op") {
        val sb = new java.lang.StringBuilder
        Escape.jsStringTo("", sb)
        assertTrue(sb.toString == "")
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
      },
      test("blocks VBScript: with mixed case") {
        assertTrue(Escape.sanitizeUrl("VBScript:Run") == "unsafe:VBScript:Run")
      },
      test("blocks Data:Text/Html with mixed case") {
        assertTrue(Escape.sanitizeUrl("Data:Text/Html,<b>x</b>") == "unsafe:Data:Text/Html,<b>x</b>")
      },
      test("allows mailto: scheme") {
        assertTrue(Escape.sanitizeUrl("mailto:user@example.com") == "mailto:user@example.com")
      },
      test("allows fragment-only URLs") {
        assertTrue(Escape.sanitizeUrl("#section") == "#section")
      }
    )
  )
}
