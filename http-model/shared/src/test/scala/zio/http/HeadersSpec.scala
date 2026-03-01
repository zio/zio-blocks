package zio.http

import zio.test._

object HeadersSpec extends HttpModelBaseSpec {
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
        assertTrue(h.rawGet("content-type") == Some("text/html"))
      },
      test("multiple entries with same name") {
        val h = Headers("Set-Cookie" -> "a=1", "Set-Cookie" -> "b=2")
        assertTrue(h.size == 2)
      }
    ),
    suite("rawGet")(
      test("returns Some for existing header") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(h.rawGet("content-type") == Some("text/html"))
      },
      test("returns None for missing header") {
        val h = Headers("content-type" -> "text/html")
        assertTrue(h.rawGet("accept") == None)
      },
      test("returns first matching value") {
        val h = Headers("set-cookie" -> "a=1", "set-cookie" -> "b=2")
        assertTrue(h.rawGet("set-cookie") == Some("a=1"))
      },
      test("case-insensitive matching via lowercased storage") {
        val h = Headers("Content-Type" -> "text/html")
        assertTrue(h.rawGet("content-type") == Some("text/html"))
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
        val result = h.get(headers.ContentType)
        assertTrue(result.isDefined)
      },
      test("parses ContentLength header") {
        val h      = Headers("Content-Length" -> "42")
        val result = h.get(headers.ContentLength)
        assertTrue(result == Some(headers.ContentLength(42L)))
      },
      test("parses Host header") {
        val h      = Headers("Host" -> "example.com:8080")
        val result = h.get(headers.Host)
        assertTrue(result == Some(headers.Host("example.com", Some(8080))))
      },
      test("returns None for missing header type") {
        val h = Headers("accept" -> "text/html")
        assertTrue(h.get(headers.ContentLength) == None)
      },
      test("returns None when parse fails") {
        val h = Headers("content-length" -> "not-a-number")
        assertTrue(h.get(headers.ContentLength) == None)
      },
      test("caches parsed result on second call") {
        val h      = Headers("Content-Length" -> "42")
        val first  = h.get(headers.ContentLength)
        val second = h.get(headers.ContentLength)
        assertTrue(
          first == Some(headers.ContentLength(42L)),
          second == Some(headers.ContentLength(42L))
        )
      }
    ),
    suite("getAll")(
      test("returns all matching entries") {
        val h       = Headers("Set-Cookie" -> "a=1", "Host" -> "example.com", "Set-Cookie" -> "b=2")
        val cookies = h.getAll(headers.SetCookieHeader)
        assertTrue(
          cookies.length == 2,
          cookies(0) == headers.SetCookieHeader("a=1"),
          cookies(1) == headers.SetCookieHeader("b=2")
        )
      },
      test("returns empty Chunk when no matches") {
        val h       = Headers("content-type" -> "text/html")
        val cookies = h.getAll(headers.SetCookieHeader)
        assertTrue(cookies.isEmpty)
      },
      test("returns single-element Chunk for one match") {
        val h     = Headers("Host" -> "example.com")
        val hosts = h.getAll(headers.Host)
        assertTrue(
          hosts.length == 1,
          hosts(0) == headers.Host("example.com", None)
        )
      }
    ),
    suite("add")(
      test("appends new entry") {
        val h = Headers("content-type" -> "text/html").add("accept", "application/json")
        assertTrue(
          h.size == 2,
          h.rawGet("accept") == Some("application/json")
        )
      },
      test("appends duplicate name") {
        val h = Headers("set-cookie" -> "a=1").add("set-cookie", "b=2")
        assertTrue(
          h.size == 2,
          h.rawGet("set-cookie") == Some("a=1")
        )
      },
      test("stores name lowercased") {
        val h = Headers.empty.add("Content-Type", "text/html")
        assertTrue(h.rawGet("content-type") == Some("text/html"))
      },
      test("does not carry parsed cache") {
        val h  = Headers("Content-Length" -> "42")
        val _  = h.get(headers.ContentLength) // trigger parse
        val h2 = h.add("accept", "text/html")
        // h2 is a fresh Headers, its parsed cache is all-null
        // but get should still work after re-parsing
        assertTrue(h2.get(headers.ContentLength) == Some(headers.ContentLength(42L)))
      }
    ),
    suite("set")(
      test("replaces all entries with same name") {
        val h = Headers("set-cookie" -> "a=1", "host" -> "example.com", "set-cookie" -> "b=2")
          .set("set-cookie", "c=3")
        assertTrue(
          h.rawGet("set-cookie") == Some("c=3"),
          h.rawGet("host") == Some("example.com")
        )
      },
      test("adds entry if name not present") {
        val h = Headers("content-type" -> "text/html").set("accept", "application/json")
        assertTrue(
          h.size == 2,
          h.rawGet("accept") == Some("application/json")
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
          h.rawGet("content-type") == Some("application/json"),
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
    suite("HeadersBuilder")(
      test("builds from scratch") {
        val builder = HeadersBuilder.make()
        builder.add("content-type", "text/html")
        builder.add("accept", "application/json")
        val h = builder.build()
        assertTrue(
          h.size == 2,
          h.rawGet("content-type") == Some("text/html"),
          h.rawGet("accept") == Some("application/json")
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
        assertTrue(h.rawGet("content-type") == Some("text/html"))
      }
    )
  )
}
