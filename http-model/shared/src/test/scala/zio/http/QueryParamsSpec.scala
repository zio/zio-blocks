package zio.http

import zio.test._
import zio.blocks.chunk.Chunk

object QueryParamsSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("QueryParams")(
    suite("empty")(
      test("is empty") {
        assertTrue(QueryParams.empty.isEmpty)
      },
      test("has size 0") {
        assertTrue(QueryParams.empty.size == 0)
      }
    ),
    suite("apply")(
      test("creates from pairs") {
        val qp = QueryParams("a" -> "1", "b" -> "2")
        assertTrue(qp.size == 2)
      },
      test("multi-value for same key") {
        val qp = QueryParams("a" -> "1", "a" -> "2")
        assertTrue(
          qp.size == 1,
          qp.get("a") == Some(Chunk("1", "2"))
        )
      }
    ),
    suite("get")(
      test("returns Some for existing key") {
        val qp = QueryParams("a" -> "1", "b" -> "2")
        assertTrue(qp.get("a") == Some(Chunk("1")))
      },
      test("returns None for missing key") {
        val qp = QueryParams("a" -> "1")
        assertTrue(qp.get("c") == None)
      }
    ),
    suite("getFirst")(
      test("returns first value for key") {
        val qp = QueryParams("a" -> "1", "a" -> "2")
        assertTrue(qp.getFirst("a") == Some("1"))
      },
      test("returns None for missing key") {
        val qp = QueryParams("a" -> "1")
        assertTrue(qp.getFirst("b") == None)
      }
    ),
    suite("has")(
      test("returns true for existing key") {
        val qp = QueryParams("a" -> "1", "b" -> "2")
        assertTrue(qp.has("a"))
      },
      test("returns false for missing key") {
        val qp = QueryParams("a" -> "1")
        assertTrue(!qp.has("c"))
      }
    ),
    suite("add")(
      test("adds to existing key (multi-value)") {
        val qp = QueryParams("a" -> "1").add("a", "2")
        assertTrue(qp.get("a") == Some(Chunk("1", "2")))
      },
      test("adds new key") {
        val qp = QueryParams("a" -> "1").add("b", "2")
        assertTrue(
          qp.size == 2,
          qp.getFirst("b") == Some("2")
        )
      }
    ),
    suite("set")(
      test("replaces all values for key") {
        val qp = QueryParams("a" -> "1", "a" -> "2").set("a", "3")
        assertTrue(qp.get("a") == Some(Chunk("3")))
      },
      test("preserves other keys") {
        val qp = QueryParams("a" -> "1", "b" -> "2").set("a", "3")
        assertTrue(
          qp.getFirst("a") == Some("3"),
          qp.getFirst("b") == Some("2")
        )
      }
    ),
    suite("remove")(
      test("removes key") {
        val qp = QueryParams("a" -> "1", "b" -> "2").remove("a")
        assertTrue(
          !qp.has("a"),
          qp.has("b"),
          qp.size == 1
        )
      },
      test("no-op for missing key") {
        val qp = QueryParams("a" -> "1").remove("b")
        assertTrue(qp.size == 1)
      }
    ),
    suite("encode")(
      test("produces wire format") {
        val qp = QueryParams("a" -> "1", "b" -> "2")
        assertTrue(qp.encode == "a=1&b=2")
      },
      test("empty produces empty string") {
        assertTrue(QueryParams.empty.encode == "")
      },
      test("multi-value expands") {
        val qp = QueryParams("a" -> "1", "a" -> "2")
        assertTrue(qp.encode == "a=1&a=2")
      },
      test("special characters are encoded") {
        val qp = QueryParams("key with space" -> "value&special")
        assertTrue(qp.encode.contains("%20") || qp.encode.contains("+"))
        assertTrue(qp.encode.contains("%26"))
      }
    ),
    suite("fromEncoded")(
      test("parses simple pairs") {
        val qp = QueryParams.fromEncoded("a=1&b=2")
        assertTrue(
          qp.getFirst("a") == Some("1"),
          qp.getFirst("b") == Some("2")
        )
      },
      test("handles empty string") {
        assertTrue(QueryParams.fromEncoded("").isEmpty)
      },
      test("multi-value key") {
        val qp = QueryParams.fromEncoded("a=1&a=2")
        assertTrue(qp.get("a") == Some(Chunk("1", "2")))
      },
      test("key without value") {
        val qp = QueryParams.fromEncoded("key")
        assertTrue(qp.getFirst("key") == Some(""))
      },
      test("decodes special characters") {
        val qp = QueryParams.fromEncoded("key%20name=value%26special")
        assertTrue(
          qp.getFirst("key name") == Some("value&special")
        )
      }
    ),
    suite("round-trip")(
      test("fromEncoded(encode) equals original") {
        val original     = QueryParams("a" -> "1", "b" -> "2")
        val roundTripped = QueryParams.fromEncoded(original.encode)
        assertTrue(roundTripped == original)
      },
      test("multi-value round-trip") {
        val original     = QueryParams("a" -> "1", "a" -> "2", "b" -> "3")
        val roundTripped = QueryParams.fromEncoded(original.encode)
        assertTrue(roundTripped == original)
      }
    ),
    suite("toList")(
      test("returns all pairs expanded") {
        val qp = QueryParams("a" -> "1", "a" -> "2", "b" -> "3")
        assertTrue(qp.toList == List(("a", "1"), ("a", "2"), ("b", "3")))
      }
    ),
    suite("nonEmpty")(
      test("returns true when non-empty") {
        assertTrue(QueryParams("a" -> "1").nonEmpty)
      },
      test("returns false when empty") {
        assertTrue(!QueryParams.empty.nonEmpty)
      }
    ),
    suite("equality")(
      test("equal params are equal") {
        val qp1 = QueryParams("a" -> "1", "b" -> "2")
        val qp2 = QueryParams("a" -> "1", "b" -> "2")
        assertTrue(qp1 == qp2)
      },
      test("different params are not equal") {
        val qp1 = QueryParams("a" -> "1")
        val qp2 = QueryParams("a" -> "2")
        assertTrue(qp1 != qp2)
      }
    ),
    suite("++(")(
      test("combines two QueryParams") {
        val qp1      = QueryParams("a" -> "1", "b" -> "2")
        val qp2      = QueryParams("b" -> "3", "c" -> "4")
        val combined = qp1 ++ qp2
        assertTrue(
          combined.get("a") == Some(Chunk("1")),
          combined.get("b") == Some(Chunk("2", "3")),
          combined.get("c") == Some(Chunk("4"))
        )
      },
      test("empty ++ qp returns qp") {
        val qp = QueryParams("a" -> "1")
        assertTrue(QueryParams.empty ++ qp == qp)
      },
      test("qp ++ empty returns qp") {
        val qp = QueryParams("a" -> "1")
        assertTrue(qp ++ QueryParams.empty == qp)
      }
    ),
    suite("addAll")(
      test("merges all entries") {
        val qp1    = QueryParams("a" -> "1")
        val qp2    = QueryParams("a" -> "2", "b" -> "3")
        val merged = qp1.addAll(qp2)
        assertTrue(
          merged.get("a") == Some(Chunk("1", "2")),
          merged.get("b") == Some(Chunk("3"))
        )
      },
      test("empty.addAll(qp) returns qp") {
        val qp = QueryParams("x" -> "1")
        assertTrue(QueryParams.empty.addAll(qp) == qp)
      }
    ),
    suite("toMap")(
      test("converts to Map[String, Chunk[String]]") {
        val qp  = QueryParams("a" -> "1", "a" -> "2", "b" -> "3")
        val map = qp.toMap
        assertTrue(
          map.get("a") == Some(Chunk("1", "2")),
          map.get("b") == Some(Chunk("3")),
          map.size == 2
        )
      },
      test("empty.toMap is empty") {
        assertTrue(QueryParams.empty.toMap.isEmpty)
      }
    ),
    suite("filter")(
      test("keeps matching entries") {
        val qp       = QueryParams("a" -> "1", "b" -> "2", "c" -> "3")
        val filtered = qp.filter((k, _) => k != "b")
        assertTrue(
          filtered.has("a"),
          !filtered.has("b"),
          filtered.has("c")
        )
      },
      test("filter with multi-value predicate") {
        val qp       = QueryParams("a" -> "1", "a" -> "2", "b" -> "3")
        val filtered = qp.filter((_, vs) => vs.length > 1)
        assertTrue(
          filtered.has("a"),
          !filtered.has("b")
        )
      },
      test("filter all returns empty") {
        val qp = QueryParams("a" -> "1")
        assertTrue(qp.filter((_, _) => false).isEmpty)
      }
    ),
    suite("QueryParamsBuilder")(
      test("builds from scratch") {
        val builder = QueryParamsBuilder.make()
        builder.add("x", "1")
        builder.add("y", "2")
        val qp = builder.build()
        assertTrue(
          qp.size == 2,
          qp.getFirst("x") == Some("1"),
          qp.getFirst("y") == Some("2")
        )
      },
      test("merges values for same key") {
        val builder = QueryParamsBuilder.make()
        builder.add("x", "1")
        builder.add("x", "2")
        val qp = builder.build()
        assertTrue(qp.get("x") == Some(Chunk("1", "2")))
      }
    )
  )
}
