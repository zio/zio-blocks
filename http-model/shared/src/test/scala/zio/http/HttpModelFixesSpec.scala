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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream
import zio.test._

object HttpModelFixesSpec extends HttpModelBaseSpec {

  private object IntHeader extends Header.Codec[Int] {
    def name: String                              = "x-n"
    def parse(value: String): Either[String, Int] =
      value.toIntOption.toRight(s"not an int: $value")
    def render(value: Int): String = value.toString
  }

  def spec: Spec[TestEnvironment, Any] = suite("HttpModelFixes")(
    suite("HeadersStrict")(
      test("lenient get reads wrong-typed header as absent") {
        val h = Headers("x-n" -> "abc")
        assertTrue(h.get(IntHeader) == None)
      },
      test("strict get reports unparseable header as Left") {
        val h = Headers("x-n" -> "abc")
        assertTrue(h.getStrict(IntHeader) == Left("not an int: abc"))
      },
      test("strict get reports absent header as Right(None)") {
        assertTrue(Headers.empty.getStrict(IntHeader) == Right(None))
      },
      test("strict get keeps scanning past bad entries for a good one") {
        val h = Headers("x-n" -> "abc", "x-n" -> "42")
        assertTrue(
          h.get(IntHeader) == Some(42),
          h.getStrict(IntHeader) == Right(Some(42))
        )
      },
      test("getAll skips bad entries while getAllStrict fails fast") {
        val h = Headers("x-n" -> "abc")
        assertTrue(
          h.getAll(IntHeader) == Chunk.empty,
          h.getAllStrict(IntHeader) == Left("not an int: abc")
        )
      },
      test("getAllStrict collects all good entries") {
        val h = Headers("x-n" -> "1", "x-n" -> "2")
        assertTrue(h.getAllStrict(IntHeader) == Right(Chunk(1, 2)))
      }
    ),
    suite("HeadersBuilderEquivalence")(
      test("builder matches chained add") {
        val pairs   = List("a" -> "1", "b" -> "2", "a" -> "3", "c" -> "4", "b" -> "5")
        val viaAdd  = pairs.foldLeft(Headers.empty) { case (h, (k, v)) => h.add(k, v) }
        val builder = HeadersBuilder.make()
        pairs.foreach { case (k, v) => builder.add(k, v) }
        assertTrue(builder.build().toList == viaAdd.toList)
      }
    ),
    suite("QueryParamsBuilderIndex")(
      test("builder matches chained add past the index threshold") {
        val pairs   = (1 to 30).map(i => ("key" + i, "val" + i)).toList ++ List("dup" -> "1", "dup" -> "2")
        val viaAdd  = pairs.foldLeft(QueryParams.empty) { case (q, (k, v)) => q.add(k, v) }
        val builder = QueryParamsBuilder.make()
        pairs.foreach { case (k, v) => builder.add(k, v) }
        val built = builder.build()
        assertTrue(
          built.toList == viaAdd.toList,
          built.encode == viaAdd.encode
        )
      },
      test("duplicate keys merge through the indexed path") {
        val builder = QueryParamsBuilder.make()
        (1 to 20).foreach(i => builder.add("k" + i, "v" + i))
        builder.add("k5", "extra")
        builder.add("dup", "1")
        builder.add("dup", "2")
        val qp = builder.build()
        assertTrue(
          qp.get("k5") == Some(Chunk("v5", "extra")),
          qp.get("dup") == Some(Chunk("1", "2")),
          qp.get("k20") == Some(Chunk("v20"))
        )
      },
      test("addAll avoids intermediate lists and matches ++") {
        val a       = QueryParams("x" -> "1", "y" -> "2")
        val c       = QueryParams("y" -> "3", "z" -> "4")
        val builder = QueryParamsBuilder.make()
        builder.addAll(a)
        builder.addAll(c)
        assertTrue(builder.build().toList == (a ++ c).toList)
      },
      test("reset clears the index") {
        val builder = QueryParamsBuilder.make()
        (1 to 10).foreach(i => builder.add("k" + i, "v" + i))
        builder.reset()
        builder.add("after", "reset")
        val qp = builder.build()
        assertTrue(
          qp.toList == List("after" -> "reset"),
          qp.get("k1") == None
        )
      }
    ),
    suite("CookieEdgeCases")(
      test("bad max-age is ignored") {
        val result = Cookie.parseResponse("a=b; Max-Age=notanumber")
        assertTrue(result.map(_.maxAge) == Right(None))
      },
      test("unknown samesite is ignored") {
        val result = Cookie.parseResponse("a=b; SameSite=Sometimes")
        assertTrue(result.map(_.sameSite) == Right(None))
      },
      test("unknown priority is ignored") {
        val result = Cookie.parseResponse("a=b; Priority=Urgent")
        assertTrue(result.map(_.priority) == Right(None))
      },
      test("unknown attributes are ignored") {
        val result = Cookie.parseResponse("a=b; Future-Attr=zzz")
        assertTrue(result.map(c => (c.name, c.value)) == Right(("a", "b")))
      },
      test("name-less request parts are dropped") {
        assertTrue(
          Cookie.parseRequest("a=1; nopart; b=2") ==
            Chunk(RequestCookie("a", "1"), RequestCookie("b", "2"))
        )
      }
    ),
    suite("PathDecodeContract")(
      test("apply stores escapes raw") {
        assertTrue(Path("/a%20b").segments == Chunk("a%20b"))
      },
      test("fromEncoded decodes escapes") {
        assertTrue(Path.fromEncoded("/a%20b").segments == Chunk("a b"))
      },
      test("case-class apply stores segments verbatim") {
        assertTrue(Path(Chunk("a b"), hasLeadingSlash = true, trailingSlash = false).render == "/a b")
      },
      test("fromEncoded inverts encode") {
        val decoded = Path("/a b/c")
        assertTrue(Path.fromEncoded(decoded.encode) == decoded)
      }
    ),
    suite("BodySemantics")(
      test("identical streaming bodies are not equal") {
        val a = Body.fromStream(Stream.fromIterable(List[Byte](1, 2, 3)))
        val b = Body.fromStream(Stream.fromIterable(List[Byte](1, 2, 3)))
        assertTrue(a != b)
      },
      test("streaming body still materializes") {
        val a = Body.fromStream(Stream.fromIterable(List[Byte](1, 2, 3)))
        assertTrue(a.toChunk == Chunk.fromIterable(List[Byte](1, 2, 3)))
      },
      test("fromArray aliases the input array") {
        val arr  = Array[Byte](1, 2, 3)
        val body = Body.fromArray(arr)
        arr(0) = 9
        assertTrue(body.toArray.toList == List[Byte](9, 2, 3))
      },
      test("toArray returns a fresh copy") {
        val body  = Body.fromString("hello")
        val first = body.toArray
        first(0) = 'X'.toByte
        assertTrue(
          !body.toArray.eq(body.toArray),
          body.asString() == "hello"
        )
      }
    ),
    suite("ResponseJson")(
      test("json renders content type through ContentType") {
        val r = Response.json("""{"a":1}""")
        assertTrue(
          r.headers.rawGet("content-type") == Some(ContentType.`application/json`.render),
          r.headers.rawGet("content-type") == Some("application/json"),
          r.body.contentType == ContentType.`application/json`
        )
      },
      test("json body bytes are UTF-8") {
        val r = Response.json("""{"a":1}""")
        assertTrue(r.body.toArray.toList == """{"a":1}""".getBytes(Charset.UTF8.name).toList)
      }
    ),
    suite("HeaderSplitAliases")(
      test("Header.Authorization is the moved top-level model") {
        val basic                = Header.Authorization.Basic("user", "pass")
        val rendered             = Header.Authorization.render(basic)
        val asTop: Authorization = basic
        assertTrue(
          Header.Authorization.parse(rendered) == Right(basic),
          asTop.headerName == "authorization"
        )
      },
      test("digest params round-trip through the shared helper") {
        val digest   = Header.Authorization.Digest(Map("username" -> "alice", "realm" -> "example"))
        val reparsed = Header.Authorization.parse(Header.Authorization.render(digest))
        assertTrue(reparsed == Right(digest))
      },
      test("proxy authorization stays reachable through Header") {
        val bearer = Header.ProxyAuthorization.Bearer("tok")
        assertTrue(
          Header.ProxyAuthorization.render(bearer) == "Bearer tok",
          Header.ProxyAuthorization.parse("Bearer tok") == Right(bearer)
        )
      },
      test("challenge headers stay reachable through Header") {
        assertTrue(
          Header.WWWAuthenticate.parse("""Basic realm="x"""") ==
            Right(Header.WWWAuthenticate("Basic", Map("realm" -> "x"))),
          Header.ProxyAuthenticate.parse("Basic") ==
            Right(Header.ProxyAuthenticate("Basic", Map.empty))
        )
      }
    )
  )
}
