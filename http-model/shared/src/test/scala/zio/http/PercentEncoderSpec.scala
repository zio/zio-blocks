package zio.http

import zio.test._

object PercentEncoderSpec extends HttpModelBaseSpec {
  import PercentEncoder._
  import ComponentType._

  def spec: Spec[TestEnvironment, Any] = suite("PercentEncoder")(
    suite("encode")(
      test("empty string returns empty string") {
        assertTrue(
          encode("", PathSegment) == "",
          encode("", QueryKey) == "",
          encode("", QueryValue) == "",
          encode("", Fragment) == "",
          encode("", UserInfo) == ""
        )
      },
      test("unreserved chars pass through unchanged") {
        val unreserved = "hello-world_v1.0~test"
        assertTrue(
          encode(unreserved, PathSegment) == unreserved,
          encode(unreserved, QueryKey) == unreserved,
          encode(unreserved, QueryValue) == unreserved,
          encode(unreserved, Fragment) == unreserved,
          encode(unreserved, UserInfo) == unreserved
        )
      },
      test("space is percent-encoded") {
        assertTrue(encode("hello world", PathSegment) == "hello%20world")
      },
      test("path segment allows sub-delims and colon/at") {
        assertTrue(encode("key=value", PathSegment) == "key=value")
      },
      test("userinfo does not allow slash") {
        assertTrue(encode("a/b", UserInfo) == "a%2Fb")
      },
      test("query key does not allow = or &") {
        assertTrue(
          encode("key=value", QueryKey) == "key%3Dvalue",
          encode("a&b", QueryKey) == "a%26b"
        )
      },
      test("query value does not allow &") {
        assertTrue(
          encode("a&b", QueryValue) == "a%26b",
          encode("key=value", QueryValue) == "key=value"
        )
      },
      test("fragment allows most reserved chars") {
        assertTrue(encode("a/b?c=d&e", Fragment) == "a/b?c=d&e")
      },
      test("UTF-8 multi-byte encoding") {
        assertTrue(encode("café", PathSegment) == "caf%C3%A9")
      },
      test("non-ASCII characters are percent-encoded as UTF-8 bytes") {
        assertTrue(encode("日本語", PathSegment) == "%E6%97%A5%E6%9C%AC%E8%AA%9E")
      }
    ),
    suite("decode")(
      test("decodes percent-encoded space") {
        assertTrue(decode("hello%20world") == "hello world")
      },
      test("decodes multi-byte UTF-8") {
        assertTrue(decode("caf%C3%A9") == "café")
      },
      test("decodes non-ASCII UTF-8 sequences") {
        assertTrue(decode("%E6%97%A5%E6%9C%AC%E8%AA%9E") == "日本語")
      },
      test("invalid hex sequence passes through") {
        assertTrue(decode("%ZZ") == "%ZZ")
      },
      test("trailing percent passes through") {
        assertTrue(decode("%") == "%")
      },
      test("trailing percent with one hex digit passes through") {
        assertTrue(decode("%2") == "%2")
      },
      test("already decoded string is unchanged") {
        assertTrue(decode("hello") == "hello")
      },
      test("plus is NOT decoded as space (RFC 3986)") {
        assertTrue(decode("+") == "+")
      }
    ),
    suite("round-trip")(
      test("encode then decode preserves simple strings") {
        assertTrue(decode(encode("hello world", PathSegment)) == "hello world")
      },
      test("encode then decode preserves UTF-8") {
        assertTrue(decode(encode("café", PathSegment)) == "café")
      },
      test("encode then decode preserves non-ASCII") {
        assertTrue(decode(encode("日本語", PathSegment)) == "日本語")
      }
    ),
    suite("decode edge cases")(
      test("empty string returns empty") {
        assertTrue(decode("") == "")
      },
      test("percent at end of string") {
        assertTrue(decode("abc%") == "abc%")
      },
      test("percent with one char at end") {
        assertTrue(decode("abc%2") == "abc%2")
      },
      test("invalid hex after valid percent-encoded byte") {
        assertTrue(decode("%20%ZZ") == " %ZZ")
      },
      test("mixed encoded and plain text with buffer flushing") {
        assertTrue(decode("a%20b%20c") == "a b c")
      },
      test("consecutive percent-encoded bytes") {
        assertTrue(decode("%41%42%43") == "ABC")
      },
      test("percent-encoded byte followed by plain char") {
        assertTrue(decode("%41x%42") == "AxB")
      },
      test("lowercase hex digits decoded") {
        assertTrue(decode("%2f%2F") == "//")
      }
    ),
    suite("encode edge cases")(
      test("UserInfo encodes most characters") {
        assertTrue(
          encode("user:pass", UserInfo) == "user:pass",
          encode("user@host", UserInfo) == "user%40host",
          encode("a/b", UserInfo) == "a%2Fb"
        )
      },
      test("PathSegment allows sub-delims") {
        assertTrue(
          encode("a;b", PathSegment) == "a;b",
          encode("a@b", PathSegment) == "a@b",
          encode("a:b", PathSegment) == "a:b"
        )
      },
      test("PathSegment encodes slash and question mark") {
        assertTrue(
          encode("a/b", PathSegment) == "a%2Fb",
          encode("a?b", PathSegment) == "a%3Fb"
        )
      },
      test("Fragment allows slash and question mark") {
        assertTrue(
          encode("a/b?c", Fragment) == "a/b?c"
        )
      },
      test("QueryKey allows slash and question mark but not equals") {
        assertTrue(
          encode("a/b", QueryKey) == "a/b",
          encode("a=b", QueryKey) == "a%3Db"
        )
      },
      test("QueryValue allows equals but not ampersand") {
        assertTrue(
          encode("a=b", QueryValue) == "a=b",
          encode("a&b", QueryValue) == "a%26b"
        )
      }
    )
  )
}
