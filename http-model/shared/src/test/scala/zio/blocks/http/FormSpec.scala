package zio.blocks.http

import zio.test._
import zio.blocks.chunk.Chunk

object FormSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Form")(
    suite("empty")(
      test("is empty") {
        assertTrue(Form.empty.isEmpty)
      },
      test("is not nonEmpty") {
        assertTrue(!Form.empty.nonEmpty)
      }
    ),
    suite("apply")(
      test("creates from varargs pairs") {
        val form = Form("a" -> "1", "b" -> "2")
        assertTrue(
          form.entries.length == 2,
          form.get("a") == Some("1"),
          form.get("b") == Some("2")
        )
      },
      test("preserves duplicate keys") {
        val form = Form("a" -> "1", "a" -> "2")
        assertTrue(
          form.entries.length == 2,
          form.getAll("a") == Chunk("1", "2")
        )
      }
    ),
    suite("fromString")(
      test("parses simple form data") {
        val form = Form.fromString("key1=val1&key2=val2")
        assertTrue(
          form.get("key1") == Some("val1"),
          form.get("key2") == Some("val2")
        )
      },
      test("parses multi-value keys") {
        val form = Form.fromString("a=1&a=2&a=3")
        assertTrue(form.getAll("a") == Chunk("1", "2", "3"))
      },
      test("handles entries without values") {
        val form = Form.fromString("keyonly")
        assertTrue(form.get("keyonly") == Some(""))
      },
      test("handles entries without values mixed with normal entries") {
        val form = Form.fromString("a=1&keyonly&b=2")
        assertTrue(
          form.get("a") == Some("1"),
          form.get("keyonly") == Some(""),
          form.get("b") == Some("2")
        )
      },
      test("decodes percent-encoded keys and values") {
        val form = Form.fromString("key%20name=value%26special")
        assertTrue(form.get("key name") == Some("value&special"))
      },
      test("handles empty string") {
        val form = Form.fromString("")
        assertTrue(form.isEmpty)
      },
      test("handles value containing equals sign") {
        val form = Form.fromString("expr=a=b=c")
        assertTrue(form.get("expr") == Some("a=b=c"))
      }
    ),
    suite("get")(
      test("returns first value for key") {
        val form = Form("a" -> "1", "a" -> "2")
        assertTrue(form.get("a") == Some("1"))
      },
      test("returns None for missing key") {
        val form = Form("a" -> "1")
        assertTrue(form.get("b") == None)
      }
    ),
    suite("getAll")(
      test("returns all values for key") {
        val form = Form("a" -> "1", "b" -> "2", "a" -> "3")
        assertTrue(form.getAll("a") == Chunk("1", "3"))
      },
      test("returns empty Chunk for missing key") {
        val form = Form("a" -> "1")
        assertTrue(form.getAll("b") == Chunk.empty[String])
      }
    ),
    suite("add")(
      test("appends new entry") {
        val form = Form("a" -> "1").add("b", "2")
        assertTrue(
          form.get("a") == Some("1"),
          form.get("b") == Some("2"),
          form.entries.length == 2
        )
      },
      test("appends duplicate key entry") {
        val form = Form("a" -> "1").add("a", "2")
        assertTrue(
          form.getAll("a") == Chunk("1", "2"),
          form.entries.length == 2
        )
      }
    ),
    suite("isEmpty / nonEmpty")(
      test("empty form is empty") {
        assertTrue(Form.empty.isEmpty, !Form.empty.nonEmpty)
      },
      test("non-empty form is nonEmpty") {
        val form = Form("a" -> "1")
        assertTrue(form.nonEmpty, !form.isEmpty)
      }
    ),
    suite("encode")(
      test("renders to URL-encoded string") {
        val form = Form("a" -> "1", "b" -> "2")
        assertTrue(form.encode == "a=1&b=2")
      },
      test("empty form encodes to empty string") {
        assertTrue(Form.empty.encode == "")
      },
      test("encodes special characters in keys and values") {
        val form    = Form("key name" -> "value&special")
        val encoded = form.encode
        assertTrue(
          encoded.contains("key%20name"),
          encoded.contains("value%26special")
        )
      },
      test("encodes equals sign in values") {
        val form    = Form("expr" -> "a=b")
        val encoded = form.encode
        assertTrue(encoded == "expr=a%3Db" || encoded == "expr=a=b")
      },
      test("encodes spaces in values") {
        val form = Form("msg" -> "hello world")
        assertTrue(form.encode.contains("hello%20world"))
      }
    ),
    suite("round-trip")(
      test("fromString(encode) preserves data") {
        val original     = Form("a" -> "1", "b" -> "2")
        val roundTripped = Form.fromString(original.encode)
        assertTrue(roundTripped == original)
      },
      test("round-trip with special characters") {
        val original     = Form("key name" -> "value&special", "x" -> "y")
        val roundTripped = Form.fromString(original.encode)
        assertTrue(roundTripped == original)
      },
      test("round-trip with multi-value keys") {
        val original     = Form("a" -> "1", "a" -> "2", "b" -> "3")
        val roundTripped = Form.fromString(original.encode)
        assertTrue(roundTripped == original)
      }
    ),
    suite("special characters")(
      test("handles spaces in keys and values") {
        val form    = Form("first name" -> "John Doe")
        val encoded = form.encode
        val decoded = Form.fromString(encoded)
        assertTrue(decoded.get("first name") == Some("John Doe"))
      },
      test("handles ampersands in values") {
        val form    = Form("query" -> "a&b")
        val encoded = form.encode
        val decoded = Form.fromString(encoded)
        assertTrue(decoded.get("query") == Some("a&b"))
      },
      test("handles equals signs in values") {
        val form    = Form("expr" -> "x=1")
        val encoded = form.encode
        val decoded = Form.fromString(encoded)
        assertTrue(decoded.get("expr") == Some("x=1"))
      }
    )
  )
}
