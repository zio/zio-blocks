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

import java.util.Locale

import zio.test._
import zio.blocks.chunk.Chunk

object HeadersRepresentationsSpec extends HttpModelBaseSpec {

  private def eager(pairs: (String, String)*): Headers =
    Headers(pairs*)

  private def overlay(pairs: (String, String)*): Headers =
    pairs.toList match {
      case Nil             => Headers.empty
      case (name, v) :: tl =>
        tl.foldLeft[Headers](new Overlay(Headers.empty, name.toLowerCase(Locale.ROOT), v)) {
          case (headers, (nextName, nextValue)) => headers.add(nextName, nextValue)
        }
    }

  private def slicedEither(pairs: (String, String)*): Either[String, Headers] = {
    val size         = pairs.length
    val nameOffsets  = new Array[Int](size)
    val nameLengths  = new Array[Int](size)
    val valueOffsets = new Array[Int](size)
    val valueLengths = new Array[Int](size)
    val buffer       = scala.collection.mutable.ArrayBuffer.empty[Byte]

    var i = 0
    pairs.foreach { case (name, value) =>
      val nameBytes  = name.getBytes("UTF-8")
      val valueBytes = value.getBytes("UTF-8")
      nameOffsets(i) = buffer.length
      nameLengths(i) = nameBytes.length
      buffer ++= nameBytes
      valueOffsets(i) = buffer.length
      valueLengths(i) = valueBytes.length
      buffer ++= valueBytes
      i += 1
    }

    Headers.fromSlices(buffer.toArray, nameOffsets, nameLengths, valueOffsets, valueLengths)
  }

  private def sliced(pairs: (String, String)*): Headers =
    slicedEither(pairs*) match {
      case Right(headers) => headers
      case Left(error)    => throw new AssertionError(s"fromSlices failed: $error")
    }

  private def slicedEitherOf(name: String, value: String): Either[String, Headers] = {
    val nameBytes  = name.getBytes("UTF-8")
    val valueBytes = value.getBytes("UTF-8")
    Headers.fromSlices(
      nameBytes ++ valueBytes,
      Array(0),
      Array(nameBytes.length),
      Array(nameBytes.length),
      Array(valueBytes.length)
    )
  }

  private def assertSamePairs(left: Headers, right: Headers): TestResult =
    assertTrue(
      left.size == right.size,
      left.toList == right.toList,
      left.toChunk == right.toChunk,
      left == right,
      right == left,
      left.hashCode == right.hashCode
    )

  def spec: Spec[TestEnvironment, Any] = suite("HeadersRepresentations")(
    suite("overlay vs eager")(
      test("size, toList, equality and hashCode agree") {
        val pairs = List("Set-Cookie" -> "a=1", "Host" -> "example.com", "set-cookie" -> "b=2")
        assertSamePairs(eager(pairs*), overlay(pairs*))
      },
      test("rawGet returns the first match and rawGetLast the last") {
        val pairs = List("Set-Cookie" -> "a=1", "Host" -> "example.com", "set-cookie" -> "b=2")
        val e     = eager(pairs*)
        val o     = overlay(pairs*)
        assertTrue(
          e.rawGet("SET-COOKIE") == Some("a=1"),
          o.rawGet("SET-COOKIE") == Some("a=1"),
          e.rawGetLast("set-cookie") == Some("b=2"),
          o.rawGetLast("set-cookie") == Some("b=2"),
          e.rawGet("missing") == None,
          o.rawGet("missing") == None,
          e.rawGetLast("missing") == None,
          o.rawGetLast("missing") == None
        )
      },
      test("rawGetAll returns every match in insertion order") {
        val pairs = List("set-cookie" -> "a=1", "host" -> "example.com", "SET-COOKIE" -> "b=2")
        val e     = eager(pairs*)
        val o     = overlay(pairs*)
        assertTrue(
          e.rawGetAll("set-cookie") == Chunk("a=1", "b=2"),
          o.rawGetAll("set-cookie") == Chunk("a=1", "b=2"),
          e.rawGetAll("missing").isEmpty,
          o.rawGetAll("missing").isEmpty
        )
      },
      test("has and contains are case-insensitive and agree with eager") {
        val e = eager("Content-Type" -> "text/html")
        val o = overlay("Content-Type" -> "text/html")
        assertTrue(
          e.has("CONTENT-TYPE"),
          o.has("CONTENT-TYPE"),
          e.contains("content-type"),
          o.contains("content-type"),
          !e.has("accept"),
          !o.has("accept")
        )
      },
      test("typed get returns the first parseable match") {
        val pairs = List("content-length" -> "100", "content-length" -> "oops", "content-length" -> "300")
        val e     = eager(pairs*)
        val o     = overlay(pairs*)
        assertTrue(
          e.get(Header.ContentLength) == Some(Header.ContentLength(100L)),
          o.get(Header.ContentLength) == Some(Header.ContentLength(100L)),
          o.getLast(Header.ContentLength) == Some(Header.ContentLength(300L))
        )
      },
      test("typed getAll returns all parseable matches") {
        val pairs = List("content-length" -> "100", "content-length" -> "oops", "content-length" -> "300")
        val o     = overlay(pairs*)
        assertTrue(
          o.getAll(Header.ContentLength) == Chunk(Header.ContentLength(100L), Header.ContentLength(300L))
        )
      },
      test("set replaces matches and appends the new value last") {
        val o = overlay("a" -> "1", "b" -> "2", "A" -> "3")
        assertSamePairs(o.set("a", "9"), eager("a" -> "1", "b" -> "2", "A" -> "3").set("a", "9"))
        assertTrue(o.set("a", "9").toList == List("b" -> "2", "a" -> "9"))
      },
      test("remove drops every match and preserves order") {
        val pairs = List("a" -> "1", "b" -> "2", "A" -> "3")
        assertSamePairs(overlay(pairs*).remove("a"), eager(pairs*).remove("a"))
        assertTrue(overlay(pairs*).remove("a").toList == List("b" -> "2"))
      },
      test("++ concatenates both sides") {
        val pairs = List("a" -> "1", "b" -> "2", "A" -> "3")
        val o     = overlay(pairs*)
        assertSamePairs(o ++ eager("c" -> "4"), eager(pairs*) ++ eager("c" -> "4"))
        assertSamePairs(eager("c" -> "4") ++ o, eager("c" -> "4") ++ eager(pairs*))
      },
      test("toString materialises the same rendering as eager") {
        val e = eager("content-type" -> "text/html", "host" -> "example.com")
        val o = overlay("content-type" -> "text/html", "host" -> "example.com")
        assertTrue(o.toString == e.toString)
      }
    ),
    suite("slices")(
      test("matches eager for 0 headers") {
        assertSamePairs(sliced(), eager())
      },
      test("matches eager for 1 header") {
        assertSamePairs(sliced("X-Trace" -> "abc"), eager("X-Trace" -> "abc"))
      },
      test("matches eager for 8 headers with mixed name case") {
        val pairs =
          (1 to 8).toList.map(i => (if (i % 3 == 0) s"X-Header-$i" else s"x-header-$i") -> s"value-$i")
        assertSamePairs(sliced(pairs*), eager(pairs*))
      },
      test("matches eager for 32 headers") {
        val pairs = (1 to 32).toList.map(i => s"x-header-$i" -> s"value-$i")
        assertSamePairs(sliced(pairs*), eager(pairs*))
      },
      test("lowecase, mixed and uppercase lookups agree") {
        val s = sliced("Content-Type" -> "text/html", "X-Trace" -> "abc", "HOST" -> "example.com")
        assertTrue(
          s.rawGet("content-type") == Some("text/html"),
          s.rawGet("CONTENT-TYPE") == Some("text/html"),
          s.rawGet("Content-Type") == Some("text/html"),
          s.rawGet("x-trace") == Some("abc"),
          s.rawGet("X-TRACE") == Some("abc"),
          s.rawGet("host") == Some("example.com"),
          s.has("HOST"),
          s.has("host")
        )
      },
      test("decodes UTF-8 values") {
        val value = "héllo-世界-🚀"
        val s     = sliced("x-utf8" -> value)
        assertTrue(
          s.rawGet("x-utf8") == Some(value),
          s.toList == List("x-utf8" -> value)
        )
      },
      test("supports empty values") {
        val s = sliced("x-empty" -> "")
        assertTrue(
          s.rawGet("x-empty") == Some(""),
          s.toList == List("x-empty" -> "")
        )
      },
      test("supports one-byte names") {
        val s = sliced("a" -> "1", "b" -> "2")
        assertTrue(
          s.size == 2,
          s.rawGet("a") == Some("1"),
          s.rawGet("b") == Some("2")
        )
      },
      test("handles duplicate names with first-match, last and all") {
        val s = sliced("set-cookie" -> "a=1", "set-cookie" -> "b=2")
        assertTrue(
          s.rawGet("set-cookie") == Some("a=1"),
          s.rawGetLast("set-cookie") == Some("b=2"),
          s.rawGetAll("set-cookie") == Chunk("a=1", "b=2")
        )
      },
      test("typed get parses and caches the matched value") {
        val s = sliced("Content-Length" -> "42")
        assertTrue(
          s.get(Header.ContentLength) == Some(Header.ContentLength(42L)),
          s.get(Header.ContentLength) == Some(Header.ContentLength(42L))
        )
      },
      test("typed getAll returns every parseable match") {
        val s = sliced("content-length" -> "100", "content-length" -> "oops", "content-length" -> "300")
        assertTrue(
          s.getAll(Header.ContentLength) == Chunk(Header.ContentLength(100L), Header.ContentLength(300L))
        )
      },
      test("add returns an overlay that still reads slices") {
        val s = sliced("a" -> "1").add("b", "2")
        assertTrue(
          s.size == 2,
          s.rawGet("a") == Some("1"),
          s.rawGet("b") == Some("2"),
          s.toList == List("a" -> "1", "b" -> "2")
        )
      }
    ),
    suite("fromSlices validation")(
      test("rejects an invalid name") {
        assertTrue(
          slicedEitherOf("bad name", "v").isLeft,
          slicedEitherOf("bad@name", "v").isLeft,
          slicedEitherOf("bad\rname", "v").isLeft
        )
      },
      test("rejects an empty name") {
        assertTrue(slicedEitherOf("", "v") == Left("Header name cannot be empty"))
      },
      test("rejects CR and LF in values") {
        assertTrue(
          slicedEitherOf("x-test", "ok\rInjected: yes") == Left("Header value cannot contain CR or LF"),
          slicedEitherOf("x-test", "ok\nInjected: yes") == Left("Header value cannot contain CR or LF")
        )
      },
      test("rejects mismatched offset arrays") {
        val bytes = "a".getBytes("UTF-8")
        assertTrue(
          Headers.fromSlices(bytes, Array(0), Array.empty, Array(0), Array(0)).isLeft,
          Headers.fromSlices(bytes, Array(0), Array(1), Array.empty, Array(0)).isLeft,
          Headers.fromSlices(bytes, Array(0), Array(1), Array(0), Array.empty).isLeft
        )
      },
      test("rejects out-of-bounds slices") {
        val bytes = "ab".getBytes("UTF-8")
        assertTrue(
          Headers.fromSlices(bytes, Array(-1), Array(1), Array(1), Array(0)).isLeft,
          Headers.fromSlices(bytes, Array(0), Array(3), Array(1), Array(0)).isLeft,
          Headers.fromSlices(bytes, Array(0), Array(1), Array(2), Array(4)).isLeft
        )
      }
    ),
    suite("property")(
      test("eager-built and overlay-built headers from the same adds are equal") {
        val nameGen  = Gen.fromIterable(List("a", "b", "content-type", "x-trace-id", "set-cookie"))
        val valueGen = Gen.fromIterable(List("", "1", "text/html", "a=b", "price=10 USD"))
        check(Gen.listOf(nameGen.zip(valueGen))) { pairs =>
          val eagerHeaders   = Headers(pairs*)
          val overlayHeaders = pairs match {
            case Nil             => Headers.empty
            case (name, v) :: tl =>
              tl.foldLeft[Headers](new Overlay(Headers.empty, name.toLowerCase(Locale.ROOT), v)) {
                case (headers, (nextName, nextValue)) => headers.add(nextName, nextValue)
              }
          }
          assertSamePairs(eagerHeaders, overlayHeaders)
        }
      }
    )
  )
}
