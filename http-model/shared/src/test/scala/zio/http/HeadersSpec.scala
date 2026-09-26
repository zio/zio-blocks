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

package zio.http

import _root_.zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.maybe.Maybe

object HeadersSpec extends HttpModelBaseSpec {
  private object TraceIdHeader extends Header.Codec[String] {
    def name: String                                 = "x-trace-id"
    def parse(value: String): Either[String, String] =
      if (value.startsWith("trace-")) Right(value) else Left("trace id must start with trace-")
    def render(value: String): String = value
  }

  private object TraceIdLengthHeader extends Header.Codec[Int] {
    def name: String                              = "x-trace-id"
    def parse(value: String): Either[String, Int] =
      if (value.startsWith("trace-")) Right(value.length) else Left("trace id must start with trace-")
    def render(value: Int): String = "trace-" + value.toString
  }

  private object IntHeader extends Header.Codec[Int] {
    def name: String                              = "x-n"
    def parse(value: String): Either[String, Int] =
      value.toIntOption.toRight(s"not an int: $value")
    def render(value: Int): String = value.toString
  }

  def spec: Spec[TestEnvironment, Any] = suite("Headers")(
    suite("empty")(
      test("is empty") {
        assertTrue(Headers.empty.isEmpty)
      },
      test("has size 0") {
        assertTrue(Headers.empty.size == 0)
      },
      test("is not nonEmpty") {
        assertTrue(!Headers.empty.nonEmpty)
      },
      test("toList returns empty list") {
        assertTrue(Headers.empty.toList == List.empty[(String, String)])
      }
    ),
    suite("apply")(
      test("creates from pairs") {
        val h = Headers("Content-Type" -> "text/html", "Accept" -> "application/json")
        assertTrue(
          h.size == 2,
          h.nonEmpty
        )
      },
      test("stores names lowercased") {
        val h = Headers("Content-Type" -> "text/html")
        assertTrue(h.rawGet("content-type") == Maybe.present("text/html"))
      },
      test("multiple entries with same name") {
        val h = Headers("Set-Cookie" -> "a=1", "Set-Cookie" -> "b=2")
        assertTrue(h.size == 2)
      }
    ),
    suite("rawGet")(
      test("returns Some for existing header") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(h.rawGet("content-type") == Maybe.present("text/html"))
      },
      test("returns None for missing header") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(h.rawGet("accept") == Maybe.absent)
      },
      test("returns first matching value") {
        val h = Headers("set-cookie" -> "a=1", "set-cookie" -> "b=2")
        assertTrue(h.rawGet("set-cookie") == Maybe.present("a=1"))
      },
      test("case-insensitive matching via lowercased storage") {
        val h = Headers("Content-Type" -> "text/html")
        assertTrue(h.rawGet("content-type") == Maybe.present("text/html"))
      }
    ),
    suite("rawGetAll")(
      test("returns empty Chunk when header not present") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(h.rawGetAll("accept").isEmpty)
      },
      test("returns single value when one header present") {
        val h      = Headers("content-type" -> "text/html")
        val result = h.rawGetAll("content-type")
        assertTrue(
          result.length == 1,
          result(0) == "text/html"
        )
      },
      test("returns all values when multiple headers with same name exist") {
        val h      = Headers("Set-Cookie" -> "a=1", "Host" -> "example.com", "Set-Cookie" -> "b=2")
        val result = h.rawGetAll("set-cookie")
        assertTrue(
          result.length == 2,
          result(0) == "a=1",
          result(1) == "b=2"
        )
      },
      test("is case-insensitive") {
        val h = Headers("Content-Type" -> "text/html")
        assertTrue(
          h.rawGetAll("CONTENT-TYPE").length == 1,
          h.rawGetAll("content-type").length == 1,
          h.rawGetAll("Content-Type").length == 1
        )
      }
    ),
    suite("get (typed)")(
      test("parses ContentType header") {
        val h      = Headers("Content-Type" -> "text/html")
        val result = h.get(Header.ContentType)
        assertTrue(result.isDefined)
      },
      test("parses ContentLength header") {
        val h      = Headers("Content-Length" -> "42")
        val result = h.get(Header.ContentLength)
        assertTrue(result == Maybe.present(Header.ContentLength(42L)))
      },
      test("parses Host header") {
        val h      = Headers("Host" -> "example.com:8080")
        val result = h.get(Header.Host)
        assertTrue(result == Maybe.present(Header.Host("example.com", Some(8080))))
      },
      test("returns None for missing header type") {
        val h = Headers("accept" -> "text/html")
        assertTrue(h.get(Header.ContentLength) == Maybe.absent)
      },
      test("returns None when parse fails") {
        val h = Headers("content-length" -> "not-a-number")
        assertTrue(h.get(Header.ContentLength) == Maybe.absent)
      },
      test("caches parsed result on second call") {
        val h      = Headers("Content-Length" -> "42")
        val first  = h.get(Header.ContentLength)
        val second = h.get(Header.ContentLength)
        assertTrue(
          first == Maybe.present(Header.ContentLength(42L)),
          second == Maybe.present(Header.ContentLength(42L))
        )
      },
      test("parses custom header codec without Header subtype") {
        val h      = Headers("X-Trace-Id" -> "trace-123")
        val result = h.get(TraceIdHeader)
        assertTrue(result == Maybe.present("trace-123"))
      },
      test("keeps built-in convenience header values working through Header.Codec") {
        val h = Headers(
          "Access-Control-Allow-Headers"     -> "*",
          "Access-Control-Allow-Credentials" -> "true"
        )
        assertTrue(
          h.get(Header.AccessControlAllowHeaders) == Maybe.present(Header.AccessControlAllowHeaders.All),
          h.get(Header.AccessControlAllowCredentials) == Maybe.present(Header.AccessControlAllowCredentials.Allow)
        )
      },
      test("does not reuse cached value across codecs with the same header name") {
        val h      = Headers("X-Trace-Id" -> "trace-123")
        val first  = h.get(TraceIdHeader)
        val second = h.get(TraceIdLengthHeader)
        val third  = h.get(TraceIdHeader)
        assertTrue(
          first == Maybe.present("trace-123"),
          second == Maybe.present(9),
          third == Maybe.present("trace-123")
        )
      }
    ),
    suite("getAll")(
      test("returns all matching entries") {
        val h       = Headers("Set-Cookie" -> "a=1", "Host" -> "example.com", "Set-Cookie" -> "b=2")
        val cookies = h.getAll(Header.SetCookieHeader)
        assertTrue(
          cookies.length == 2,
          cookies(0) == Header.SetCookieHeader("a=1"),
          cookies(1) == Header.SetCookieHeader("b=2")
        )
      },
      test("returns empty Chunk when no matches") {
        val h       = Headers("content-type" -> "text/html")
        val cookies = h.getAll(Header.SetCookieHeader)
        assertTrue(cookies.isEmpty)
      },
      test("returns single-element Chunk for one match") {
        val h     = Headers("Host" -> "example.com")
        val hosts = h.getAll(Header.Host)
        assertTrue(
          hosts.length == 1,
          hosts(0) == Header.Host("example.com", None)
        )
      },
      test("returns all matching custom header codec values") {
        val h      = Headers("X-Trace-Id" -> "trace-1", "X-Trace-Id" -> "trace-2")
        val traces = h.getAll(TraceIdHeader)
        assertTrue(traces == Chunk("trace-1", "trace-2"))
      },
      test("does not reuse cached values across getAll codecs with the same header name") {
        val h       = Headers("X-Trace-Id" -> "trace-1", "X-Trace-Id" -> "trace-22")
        val strings = h.getAll(TraceIdHeader)
        val lengths = h.getAll(TraceIdLengthHeader)
        val again   = h.getAll(TraceIdHeader)
        assertTrue(
          strings == Chunk("trace-1", "trace-22"),
          lengths == Chunk(7, 8),
          again == Chunk("trace-1", "trace-22")
        )
      },
      test("reuses cached values on a repeated read with the same codec") {
        val h      = Headers("X-Trace-Id" -> "trace-1", "X-Trace-Id" -> "trace-2")
        val first  = h.getAll(TraceIdHeader)
        val second = h.getAll(TraceIdHeader)
        assertTrue(
          first == Chunk("trace-1", "trace-2"),
          second == Chunk("trace-1", "trace-2")
        )
      }
    ),
    suite("getStrict")(
      test("reports an absent header as Right(absent)") {
        assertTrue(Headers.empty.getStrict(IntHeader) == Right(Maybe.absent))
      },
      test("parses on a cold cache without a prior lenient read") {
        val h = Headers("x-n" -> "7")
        assertTrue(h.getStrict(IntHeader) == Right(Maybe.present(7)))
      },
      test("reports a present-but-unparseable header as Left where get reads None") {
        val h = Headers("x-n" -> "abc")
        assertTrue(
          h.get(IntHeader) == Maybe.absent,
          h.getStrict(IntHeader) == Left("not an int: abc")
        )
      },
      test("keeps scanning past bad entries for a later good one") {
        val h = Headers("x-n" -> "abc", "x-n" -> "42")
        assertTrue(
          h.get(IntHeader) == Maybe.present(42),
          h.getStrict(IntHeader) == Right(Maybe.present(42))
        )
      },
      test("reports the first error when no entry parses") {
        val h = Headers("x-n" -> "abc", "x-n" -> "def")
        assertTrue(h.getStrict(IntHeader) == Left("not an int: abc"))
      },
      test("reuses values cached by the same codec") {
        val h     = Headers("x-n" -> "7")
        val first = h.get(IntHeader)
        assertTrue(
          first == Maybe.present(7),
          h.getStrict(IntHeader) == Right(Maybe.present(7))
        )
      }
    ),
    suite("getAllStrict")(
      test("collects every matching entry") {
        val h = Headers("x-n" -> "1", "x-n" -> "2")
        assertTrue(h.getAllStrict(IntHeader) == Right(Chunk(1, 2)))
      },
      test("returns Right(empty) when no header matches") {
        assertTrue(Headers.empty.getAllStrict(IntHeader) == Right(Chunk.empty))
      },
      test("fails fast on the first bad entry where getAll skips it") {
        val h = Headers("x-n" -> "1", "x-n" -> "abc", "x-n" -> "3")
        assertTrue(
          h.getAll(IntHeader) == Chunk(1, 3),
          h.getAllStrict(IntHeader) == Left("not an int: abc")
        )
      },
      test("ignores entries with other names") {
        val h = Headers("x-n" -> "1", "other" -> "z", "x-n" -> "2")
        assertTrue(h.getAllStrict(IntHeader) == Right(Chunk(1, 2)))
      }
    ),
    suite("getLast")(
      test("returns the last matching typed header") {
        val h      = Headers("Set-Cookie" -> "a=1", "Set-Cookie" -> "b=2")
        val cookie = h.getLast(Header.SetCookieHeader)
        assertTrue(cookie == Maybe.present(Header.SetCookieHeader("b=2")))
      },
      test("returns absent when no matching typed header exists") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(h.getLast(Header.SetCookieHeader).isAbsent)
      },
      test("skips unparseable entries scanning backwards") {
        val h = Headers("content-length" -> "42", "content-length" -> "abc")
        assertTrue(h.getLast(Header.ContentLength) == Maybe.present(Header.ContentLength(42L)))
      },
      test("reuses the cached value on a repeated read") {
        val h      = Headers("content-length" -> "7")
        val first  = h.getLast(Header.ContentLength)
        val second = h.getLast(Header.ContentLength)
        assertTrue(
          first == Maybe.present(Header.ContentLength(7L)),
          second == Maybe.present(Header.ContentLength(7L))
        )
      }
    ),
    suite("rawGetLast")(
      test("returns the last raw header value") {
        val h = Headers("Set-Cookie" -> "a=1", "Set-Cookie" -> "b=2")
        assertTrue(h.rawGetLast("set-cookie") == Maybe.present("b=2"))
      },
      test("returns None when header is missing") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(h.rawGetLast("set-cookie").isEmpty)
      }
    ),
    suite("add")(
      test("appends new entry") {
        val h = Headers("content-type" -> "text/html").add("accept", "application/json")
        assertTrue(
          h.size == 2,
          h.rawGet("accept") == Maybe.present("application/json")
        )
      },
      test("appends duplicate name") {
        val h = Headers("set-cookie" -> "a=1").add("set-cookie", "b=2")
        assertTrue(
          h.size == 2,
          h.rawGet("set-cookie") == Maybe.present("a=1")
        )
      },
      test("stores name lowercased") {
        val h = Headers.empty.add("Content-Type", "text/html")
        assertTrue(h.rawGet("content-type") == Maybe.present("text/html"))
      },
      test("does not carry parsed cache") {
        val h  = Headers("Content-Length" -> "42")
        val _  = h.get(Header.ContentLength) // trigger parse
        val h2 = h.add("accept", "text/html")
        // h2 is a fresh Headers, its parsed cache is all-null
        // but get should still work after re-parsing
        assertTrue(h2.get(Header.ContentLength) == Maybe.present(Header.ContentLength(42L)))
      }
    ),
    suite("typed add/set")(
      test("add accepts a typed header") {
        val h = Headers.empty.add(Header.Host("example.com", Some(8080)))
        assertTrue(h.get(Header.Host) == Maybe.present(Header.Host("example.com", Some(8080))))
      },
      test("set accepts a typed header") {
        val h = Headers("host" -> "old.example.com").set(Header.Host("example.com", Some(8080)))
        assertTrue(
          h.get(Header.Host) == Maybe.present(Header.Host("example.com", Some(8080))),
          h.size == 1
        )
      }
    ),
    suite("set")(
      test("replaces all entries with same name") {
        val h = Headers("set-cookie" -> "a=1", "host" -> "example.com", "set-cookie" -> "b=2")
          .set("set-cookie", "c=3")
        assertTrue(
          h.rawGet("set-cookie") == Maybe.present("c=3"),
          h.rawGet("host") == Maybe.present("example.com")
        )
      },
      test("adds entry if name not present") {
        val h = Headers("content-type" -> "text/html").set("accept", "application/json")
        assertTrue(
          h.size == 2,
          h.rawGet("accept") == Maybe.present("application/json")
        )
      },
      test("resulting Headers has exactly one entry for the set name") {
        val h        = Headers("a" -> "1", "a" -> "2", "a" -> "3").set("a", "4")
        val aEntries = h.toList.filter(_._1 == "a")
        assertTrue(aEntries == List(("a", "4")))
      },
      test("case-insensitive replacement") {
        val h = Headers("Content-Type" -> "text/html").set("CONTENT-TYPE", "application/json")
        assertTrue(
          h.rawGet("content-type") == Maybe.present("application/json"),
          h.size == 1
        )
      }
    ),
    suite("remove")(
      test("removes all entries with matching name") {
        val h = Headers("set-cookie" -> "a=1", "host" -> "example.com", "set-cookie" -> "b=2")
          .remove("set-cookie")
        assertTrue(
          !h.has("set-cookie"),
          h.has("host"),
          h.size == 1
        )
      },
      test("no-op for missing name") {
        val h = Headers("content-type" -> "text/html").remove("accept")
        assertTrue(h.size == 1)
      },
      test("case-insensitive removal") {
        val h = Headers("Content-Type" -> "text/html").remove("CONTENT-TYPE")
        assertTrue(h.isEmpty)
      }
    ),
    suite("has")(
      test("returns true for existing header") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(h.has("content-type"))
      },
      test("returns false for missing header") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(!h.has("accept"))
      },
      test("case-insensitive") {
        val h = Headers("Content-Type" -> "text/html")
        assertTrue(h.has("content-type"))
      }
    ),
    suite("isEmpty / nonEmpty")(
      test("empty Headers is empty") {
        assertTrue(Headers.empty.isEmpty, !Headers.empty.nonEmpty)
      },
      test("non-empty Headers is nonEmpty") {
        val h = Headers("a" -> "b")
        assertTrue(!h.isEmpty, h.nonEmpty)
      }
    ),
    suite("toList")(
      test("returns all pairs in order") {
        val h = Headers("content-type" -> "text/html", "accept" -> "application/json", "host" -> "example.com")
        assertTrue(
          h.toList == List(
            ("content-type", "text/html"),
            ("accept", "application/json"),
            ("host", "example.com")
          )
        )
      },
      test("includes duplicate names") {
        val h = Headers("set-cookie" -> "a=1", "set-cookie" -> "b=2")
        assertTrue(
          h.toList == List(("set-cookie", "a=1"), ("set-cookie", "b=2"))
        )
      }
    ),
    suite("equality")(
      test("equal headers are equal") {
        val h1 = Headers("a" -> "1", "b" -> "2")
        val h2 = Headers("a" -> "1", "b" -> "2")
        assertTrue(h1 == h2)
      },
      test("different headers are not equal") {
        val h1 = Headers("a" -> "1")
        val h2 = Headers("a" -> "2")
        assertTrue(h1 != h2)
      },
      test("headers of different sizes are not equal") {
        val h1 = Headers("a" -> "1")
        val h2 = Headers("a" -> "1", "b" -> "2")
        assertTrue(h1 != h2)
      },
      test("order matters") {
        val h1 = Headers("a" -> "1", "b" -> "2")
        val h2 = Headers("b" -> "2", "a" -> "1")
        assertTrue(h1 != h2)
      },
      test("hashCode is consistent with equals") {
        val h1 = Headers("a" -> "1", "b" -> "2")
        val h2 = Headers("a" -> "1", "b" -> "2")
        assertTrue(h1.hashCode == h2.hashCode)
      }
    ),
    suite("toString")(
      test("has readable format") {
        val h = Headers("content-type" -> "text/html", "host" -> "example.com")
        val s = h.toString
        assertTrue(
          s.contains("Headers"),
          s.contains("content-type"),
          s.contains("text/html")
        )
      }
    ),
    suite("validateName / validateValue")(
      test("accepts valid names and values") {
        assertTrue(
          Headers.validateName("content-type") == Right(()),
          Headers.validateName("X-TRACE-ID") == Right(()),
          Headers.validateValue("text/html") == Right(()),
          Headers.validateValue("") == Right(())
        )
      },
      test("rejects empty and non-token names") {
        assertTrue(
          Headers.validateName("") == Left("Header name cannot be empty"),
          Headers.validateName("bad name").isLeft,
          Headers.validateName("bad@name").isLeft
        )
      },
      test("rejects values containing CR or LF") {
        assertTrue(
          Headers.validateValue("a\rb").isLeft,
          Headers.validateValue("a\nb").isLeft
        )
      }
    ),
    suite("HeadersBuilder")(
      test("builds from scratch") {
        val builder = HeadersBuilder.make()
        builder.add("content-type", "text/html")
        builder.add("accept", "application/json")
        val h = builder.build()
        assertTrue(
          h.size == 2,
          h.rawGet("content-type") == Maybe.present("text/html"),
          h.rawGet("accept") == Maybe.present("application/json")
        )
      },
      test("allows duplicate names") {
        val builder = HeadersBuilder.make()
        builder.add("set-cookie", "a=1")
        builder.add("set-cookie", "b=2")
        val h = builder.build()
        assertTrue(
          h.size == 2,
          h.toList == List(("set-cookie", "a=1"), ("set-cookie", "b=2"))
        )
      },
      test("grows capacity automatically") {
        val builder = HeadersBuilder.make(2)
        builder.add("a", "1")
        builder.add("b", "2")
        builder.add("c", "3")
        builder.add("d", "4")
        val h = builder.build()
        assertTrue(h.size == 4)
      },
      test("lowercases names") {
        val builder = HeadersBuilder.make()
        builder.add("Content-Type", "text/html")
        val h = builder.build()
        assertTrue(h.rawGet("content-type") == Maybe.present("text/html"))
      },
      test("rejects invalid header names") {
        assertTrue(
          scala.util.Try(Headers("" -> "value")).isFailure,
          scala.util.Try(Headers("bad@name" -> "value")).isFailure,
          scala.util.Try(Headers("bad header" -> "value")).isFailure,
          scala.util.Try(Headers.empty.add("bad\r\nname", "value")).isFailure,
          scala.util.Try(Headers("ok" -> "value").rawGet("bad name")).isFailure,
          scala.util.Try(Headers("ok" -> "value").rawGetAll("bad name")).isFailure,
          scala.util.Try(Headers("ok" -> "value").remove("bad name")).isFailure,
          scala.util.Try(Headers("ok" -> "value").has("bad name")).isFailure
        )
      },
      test("rejects CRLF in header values") {
        assertTrue(
          scala.util.Try(Headers("x-test" -> "ok\r\nInjected: yes")).isFailure,
          scala.util.Try(Headers.empty.add("x-test", "ok\rInjected: yes")).isFailure,
          scala.util.Try(Headers.empty.set("x-test", "ok\nInjected: yes")).isFailure,
          scala.util.Try(Headers.empty.set("x-test", "ok\rInjected: yes")).isFailure
        )
      }
    ),
    suite("HeadersBuilder capacity growth")(
      test("grows past initial capacity of 4 (minimum)") {
        val builder = HeadersBuilder.make(1)
        builder.add("a", "1")
        builder.add("b", "2")
        builder.add("c", "3")
        builder.add("d", "4")
        builder.add("e", "5")
        builder.add("f", "6")
        builder.add("g", "7")
        builder.add("h", "8")
        builder.add("i", "9")
        val h = builder.build()
        assertTrue(
          h.size == 9,
          h.rawGet("a") == Maybe.present("1"),
          h.rawGet("i") == Maybe.present("9")
        )
      },
      test("builds empty headers from builder") {
        val builder = HeadersBuilder.make()
        val h       = builder.build()
        assertTrue(h.isEmpty, h.size == 0)
      },
      test("default initial capacity is at least 4") {
        val builder = HeadersBuilder.make(0)
        builder.add("a", "1")
        builder.add("b", "2")
        builder.add("c", "3")
        builder.add("d", "4")
        builder.add("e", "5")
        val h = builder.build()
        assertTrue(h.size == 5)
      }
    ),
    suite("Headers equality edge cases")(
      test("Headers does not equal non-Headers") {
        val h = Headers("a" -> "1")
        assertTrue(!h.equals("not headers"))
      },
      test("empty headers toString") {
        assertTrue(Headers.empty.toString == "Headers()")
      }
    ),
    suite("++ operator")(
      test("combines two headers") {
        val h1       = Headers("a" -> "1")
        val h2       = Headers("b" -> "2")
        val combined = h1 ++ h2
        assertTrue(
          combined.size == 2,
          combined.rawGet("a") == Maybe.present("1"),
          combined.rawGet("b") == Maybe.present("2")
        )
      },
      test("combining with empty returns same entries") {
        val h        = Headers("a" -> "1")
        val combined = h ++ Headers.empty
        assertTrue(combined.size == 1, combined.rawGet("a") == Maybe.present("1"))
      },
      test("preserves order") {
        val h1       = Headers("a" -> "1")
        val h2       = Headers("b" -> "2")
        val combined = h1 ++ h2
        assertTrue(combined.toList == List(("a", "1"), ("b", "2")))
      }
    ),
    suite("contains")(
      test("returns true for existing header") {
        val h = Headers("Content-Type" -> "text/html")
        assertTrue(h.contains("content-type"))
      },
      test("returns false for missing header") {
        val h = Headers("Content-Type" -> "text/html")
        assertTrue(!h.contains("accept"))
      },
      test("is case-insensitive") {
        val h = Headers("Content-Type" -> "text/html")
        assertTrue(h.contains("CONTENT-TYPE"))
      }
    ),
    suite("toChunk")(
      test("returns correct pairs") {
        val h     = Headers("a" -> "1", "b" -> "2")
        val chunk = h.toChunk
        assertTrue(
          chunk.length == 2,
          chunk == Chunk(("a", "1"), ("b", "2"))
        )
      }
    )
  )
}
