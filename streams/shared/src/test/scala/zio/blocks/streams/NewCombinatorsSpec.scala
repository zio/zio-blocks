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

package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.test._
import zio.test.Assertion._

object NewCombinatorsSpec extends StreamsBaseSpec {

  private def collect[A](s: Stream[Nothing, A]): Chunk[A] =
    s.runCollect.fold(_ => Chunk.empty, identity)

  private def collectE[E, A](s: Stream[E, A]): Either[E, Chunk[A]] =
    s.runCollect

  def spec: Spec[TestEnvironment, Any] = suite("NewCombinatorsSpec")(
    // ---- distinct -----------------------------------------------------------

    suite("distinct")(
      test("removes duplicates") {
        val s = Stream.fromIterable(List(1, 2, 3, 2, 1, 4, 3))
        assert(collect(s.distinct))(equalTo(Chunk.fromIterable(List(1, 2, 3, 4))))
      },
      test("empty stream") {
        val s = Stream.fromIterable(List.empty[Int])
        assert(collect(s.distinct))(equalTo(Chunk.empty[Int]))
      },
      test("all same elements") {
        val s = Stream.fromIterable(List(5, 5, 5, 5))
        assert(collect(s.distinct))(equalTo(Chunk.fromIterable(List(5))))
      },
      test("all different elements") {
        val s = Stream.fromIterable(List(1, 2, 3, 4, 5))
        assert(collect(s.distinct))(equalTo(Chunk.fromIterable(List(1, 2, 3, 4, 5))))
      },
      test("preserves order of first occurrence") {
        val s = Stream.fromIterable(List("b", "a", "c", "a", "b", "d"))
        assert(collect(s.distinct))(equalTo(Chunk.fromIterable(List("b", "a", "c", "d"))))
      }
    ),

    // ---- distinctBy ---------------------------------------------------------

    suite("distinctBy")(
      test("by key function") {
        val s = Stream.fromIterable(List("apple", "avocado", "banana", "blueberry", "cherry"))
        assert(collect(s.distinctBy(_.head)))(
          equalTo(Chunk.fromIterable(List("apple", "banana", "cherry")))
        )
      },
      test("by modulus") {
        val s = Stream.fromIterable(List(1, 2, 3, 4, 5, 6))
        assert(collect(s.distinctBy(_ % 3)))(
          equalTo(Chunk.fromIterable(List(1, 2, 3)))
        )
      },
      test("empty stream") {
        val s: Stream[Nothing, String] = Stream.empty
        assert(collect(s.distinctBy(_.length)))(equalTo(Chunk.empty))
      }
    ),

    // ---- grouped ------------------------------------------------------------

    suite("grouped")(
      test("exact multiple") {
        val s      = Stream.fromIterable(List(1, 2, 3, 4, 5, 6))
        val result = collect(s.grouped(3))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2, 3)),
                Chunk.fromIterable(List(4, 5, 6))
              )
            )
          )
        )
      },
      test("partial last group") {
        val s      = Stream.fromIterable(List(1, 2, 3, 4, 5))
        val result = collect(s.grouped(3))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2, 3)),
                Chunk.fromIterable(List(4, 5))
              )
            )
          )
        )
      },
      test("empty stream") {
        val s: Stream[Nothing, Int] = Stream.empty
        assert(collect(s.grouped(3)))(equalTo(Chunk.empty))
      },
      test("n = 1") {
        val s      = Stream.fromIterable(List(1, 2, 3))
        val result = collect(s.grouped(1))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1)),
                Chunk.fromIterable(List(2)),
                Chunk.fromIterable(List(3))
              )
            )
          )
        )
      },
      test("single element") {
        val s      = Stream.fromIterable(List(42))
        val result = collect(s.grouped(5))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(42))
              )
            )
          )
        )
      },
      test("group size larger than stream") {
        val s      = Stream.fromIterable(List(1, 2))
        val result = collect(s.grouped(10))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2))
              )
            )
          )
        )
      }
    ),

    // ---- intersperse --------------------------------------------------------

    suite("intersperse")(
      test("basic intersperse") {
        val s = Stream.fromIterable(List(1, 2, 3))
        assert(collect(s.intersperse(0)))(
          equalTo(Chunk.fromIterable(List(1, 0, 2, 0, 3)))
        )
      },
      test("empty stream") {
        val s: Stream[Nothing, Int] = Stream.empty
        assert(collect(s.intersperse(0)))(equalTo(Chunk.empty))
      },
      test("single element") {
        val s = Stream.fromIterable(List(42))
        assert(collect(s.intersperse(0)))(equalTo(Chunk.fromIterable(List(42))))
      },
      test("two elements") {
        val s = Stream.fromIterable(List(1, 2))
        assert(collect(s.intersperse(0)))(equalTo(Chunk.fromIterable(List(1, 0, 2))))
      },
      test("string separator") {
        val s = Stream.fromIterable(List("a", "b", "c"))
        assert(collect(s.intersperse(",")))(
          equalTo(Chunk.fromIterable(List("a", ",", "b", ",", "c")))
        )
      }
    ),

    // ---- scan ---------------------------------------------------------------

    suite("scan")(
      test("basic accumulation (running sum)") {
        val s = Stream.fromIterable(List(1, 2, 3, 4))
        assert(collect(s.scan(0)(_ + _)))(
          equalTo(Chunk.fromIterable(List(0, 1, 3, 6, 10)))
        )
      },
      test("empty stream emits only init") {
        val s: Stream[Nothing, Int] = Stream.empty
        assert(collect(s.scan(42)(_ + _)))(equalTo(Chunk.fromIterable(List(42))))
      },
      test("single element") {
        val s = Stream.fromIterable(List(5))
        assert(collect(s.scan(10)(_ + _)))(equalTo(Chunk.fromIterable(List(10, 15))))
      },
      test("running product") {
        val s = Stream.fromIterable(List(1, 2, 3, 4))
        assert(collect(s.scan(1)(_ * _)))(
          equalTo(Chunk.fromIterable(List(1, 1, 2, 6, 24)))
        )
      }
    ),

    // ---- sliding ------------------------------------------------------------

    suite("sliding")(
      test("basic sliding window") {
        val s      = Stream.fromIterable(List(1, 2, 3, 4, 5))
        val result = collect(s.sliding(3))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2, 3)),
                Chunk.fromIterable(List(2, 3, 4)),
                Chunk.fromIterable(List(3, 4, 5))
              )
            )
          )
        )
      },
      test("step > 1") {
        val s      = Stream.fromIterable(List(1, 2, 3, 4, 5, 6))
        val result = collect(s.sliding(3, 2))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2, 3)),
                Chunk.fromIterable(List(3, 4, 5)),
                Chunk.fromIterable(List(5, 6))
              )
            )
          )
        )
      },
      test("step = n (same as grouped)") {
        val s      = Stream.fromIterable(List(1, 2, 3, 4, 5, 6))
        val result = collect(s.sliding(3, 3))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2, 3)),
                Chunk.fromIterable(List(4, 5, 6))
              )
            )
          )
        )
      },
      test("empty stream") {
        val s: Stream[Nothing, Int] = Stream.empty
        assert(collect(s.sliding(3)))(equalTo(Chunk.empty))
      },
      test("window larger than stream") {
        val s      = Stream.fromIterable(List(1, 2))
        val result = collect(s.sliding(5))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2))
              )
            )
          )
        )
      },
      test("single element window") {
        val s      = Stream.fromIterable(List(1, 2, 3))
        val result = collect(s.sliding(1))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1)),
                Chunk.fromIterable(List(2)),
                Chunk.fromIterable(List(3))
              )
            )
          )
        )
      }
    ),

    // ---- sliding step > n ---------------------------------------------------

    suite("sliding step > n")(
      test("step > n skips elements between windows") {
        val s      = Stream.fromIterable(List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        val result = collect(s.sliding(2, 5))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2)),
                Chunk.fromIterable(List(6, 7))
              )
            )
          )
        )
      },
      test("step much larger than n") {
        val s      = Stream.fromIterable(List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12))
        val result = collect(s.sliding(2, 10))
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2)),
                Chunk.fromIterable(List(11, 12))
              )
            )
          )
        )
      }
    ),

    // ---- validation ---------------------------------------------------------

    suite("validation")(
      test("grouped(0) throws") {
        assert(try {
          Stream.fromIterable(List(1)).grouped(0); "ok"
        } catch { case _: IllegalArgumentException => "threw" })(equalTo("threw"))
      },
      test("sliding(0, 1) throws") {
        assert(try {
          Stream.fromIterable(List(1)).sliding(0); "ok"
        } catch { case _: IllegalArgumentException => "threw" })(equalTo("threw"))
      },
      test("sliding(1, 0) throws") {
        assert(try {
          Stream.fromIterable(List(1)).sliding(1, 0); "ok"
        } catch { case _: IllegalArgumentException => "threw" })(equalTo("threw"))
      }
    ),

    // ---- render labels ------------------------------------------------------

    suite("render labels")(
      test("grouped render includes combinator name") {
        val s = Stream.range(0, 10).grouped(3)
        assert(s.render.contains("grouped"))(isTrue)
      },
      test("sliding render includes combinator name") {
        val s = Stream.range(0, 10).sliding(3)
        assert(s.render.contains("sliding"))(isTrue)
      },
      test("scan render includes combinator name") {
        val s = Stream.range(0, 10).scan(0)(_ + _)
        assert(s.render.contains("scan"))(isTrue)
      },
      test("intersperse render includes combinator name") {
        val s = Stream.fromIterable(List(1, 2, 3)).intersperse(0)
        assert(s.render.contains("intersperse"))(isTrue)
      }
    ),

    // ---- fromIterator -------------------------------------------------------

    suite("fromIterator")(
      test("basic iterator") {
        val s = Stream.fromIterator(List(1, 2, 3).iterator)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(1, 2, 3))))
      },
      test("empty iterator") {
        val s = Stream.fromIterator(Iterator.empty[Int])
        assert(collect(s))(equalTo(Chunk.empty))
      },
      test("lazy evaluation") {
        var created = false
        val s       = Stream.fromIterator { created = true; List(1, 2).iterator }
        assert(created)(isFalse) &&
        assert(collect(s))(equalTo(Chunk.fromIterable(List(1, 2)))) &&
        assert(created)(isTrue)
      },
      test("single element iterator") {
        val s = Stream.fromIterator(Iterator.single(42))
        assert(collect(s))(equalTo(Chunk.fromIterable(List(42))))
      },
      test("large iterator") {
        val s = Stream.fromIterator((1 to 1000).iterator)
        assert(collect(s).length)(equalTo(1000))
      }
    ),

    // ---- specialization tests for grouped/sliding on primitive streams ------

    suite("grouped specialization")(
      test("grouped specialization with Int stream") {
        val s      = Stream.range(0, 6).grouped(3)
        val result = s.runCollect
        assert(result)(
          isRight(
            equalTo(
              Chunk.fromIterable(
                List(
                  Chunk.fromIterable(List(0, 1, 2)),
                  Chunk.fromIterable(List(3, 4, 5))
                )
              )
            )
          )
        )
      }
    ),

    suite("sliding specialization")(
      test("sliding specialization with Int stream") {
        val s      = Stream.range(1, 6).sliding(3)
        val result = s.runCollect.getOrElse(Chunk.empty)
        assert(result)(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1, 2, 3)),
                Chunk.fromIterable(List(2, 3, 4)),
                Chunk.fromIterable(List(3, 4, 5))
              )
            )
          )
        )
      }
    ),

    // ---- Scala stdlib conformance ------------------------------------------

    suite("Scala stdlib conformance")(
      test("take(-1) returns empty like List.take(-1)") {
        val s = Stream.fromIterable(List(1, 2, 3))
        assert(collect(s.take(-1)))(equalTo(Chunk.empty[Int]))
      },
      test("take(0) returns empty like List.take(0)") {
        val s = Stream.fromIterable(List(1, 2, 3))
        assert(collect(s.take(0)))(equalTo(Chunk.empty[Int]))
      },
      test("take(100) on short stream returns all like List.take(100)") {
        val s = Stream.fromIterable(List(1, 2))
        assert(collect(s.take(100)))(equalTo(Chunk.fromIterable(List(1, 2))))
      },
      test("grouped(1) wraps each element like List.grouped(1)") {
        val s = Stream.fromIterable(List(1, 2, 3))
        assert(collect(s.grouped(1)))(
          equalTo(
            Chunk.fromIterable(
              List(
                Chunk.fromIterable(List(1)),
                Chunk.fromIterable(List(2)),
                Chunk.fromIterable(List(3))
              )
            )
          )
        )
      },
      test("sliding(2, 3) matches List.sliding(2, 3)") {
        val s        = Stream.fromIterable(List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        val expected = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).sliding(2, 3).map(_.toList).toList
        val result   = collect(s.sliding(2, 3)).map(c => c.toList).toList
        assert(result)(equalTo(expected))
      },
      test("sliding(2, 5) matches List.sliding(2, 5)") {
        val s        = Stream.fromIterable((1 to 10).toList)
        val expected = (1 to 10).toList.sliding(2, 5).map(_.toList).toList
        val result   = collect(s.sliding(2, 5)).map(c => c.toList).toList
        assert(result)(equalTo(expected))
      },
      test("sliding(3, 2) matches List.sliding(3, 2)") {
        val input    = (1 to 8).toList
        val s        = Stream.fromIterable(input)
        val expected = input.sliding(3, 2).map(_.toList).toList
        val result   = collect(s.sliding(3, 2)).map(c => c.toList).toList
        assert(result)(equalTo(expected))
      }
    ),

    // ---- Managed I/O tests -------------------------------------------------

    suite("Managed I/O")(
      test("fromInputStream closes the stream on completion") {
        var closed = false
        val is     = new java.io.InputStream {
          override def read(): Int   = -1
          override def close(): Unit = closed = true
        }
        Stream.fromInputStream(is).runDrain
        assert(closed)(isTrue)
      },
      test("fromInputStreamUnmanaged does not close the stream") {
        var closed = false
        val is     = new java.io.InputStream {
          override def read(): Int   = -1
          override def close(): Unit = closed = true
        }
        Stream.fromInputStreamUnmanaged(is).runDrain
        assert(closed)(isFalse)
      }
    ),

    // ---- ConcatReader: reset cycling ----------------------------------------

    suite("ConcatReader reset")(
      test("simple repeated concat cycles all segments") {
        val s = (Stream(1, 2) ++ Stream(3, 4)).repeated.take(10)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(1, 2, 3, 4, 1, 2, 3, 4, 1, 2))))
      },
      test("three-segment concat repeated") {
        val s = (Stream(1) ++ Stream(2) ++ Stream(3)).repeated.take(7)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(1, 2, 3, 1, 2, 3, 1))))
      },
      test("repeated concat with inner take (SkipLimitReader reset)") {
        val s = (Stream.repeat(5).take(2) ++ Stream.repeat(1).take(3)).repeated.take(12)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(5, 5, 1, 1, 1, 5, 5, 1, 1, 1, 5, 5))))
      },
      test("repeated concat with inner map") {
        val s = (Stream(1, 2).map(_ * 10) ++ Stream(99)).repeated.take(7)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(10, 20, 99, 10, 20, 99, 10))))
      },
      test("empty head skips to tail on each cycle") {
        val s = (Stream.empty ++ Stream(1, 2)).repeated.take(4)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(1, 2, 1, 2))))
      },
      test("empty middle segment skipped") {
        val s = (Stream(1) ++ Stream.empty ++ Stream(2)).repeated.take(4)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(1, 2, 1, 2))))
      },
      test("Int-specialized concat resets correctly") {
        val s = (Stream.range(0, 2) ++ Stream.range(10, 12)).repeated.take(8)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(0, 1, 10, 11, 0, 1, 10, 11))))
      },
      test("nested repeated inside concat inside repeated") {
        val inner = (Stream(1) ++ Stream(2)).repeated.take(3)
        val s     = (inner ++ Stream(99)).repeated.take(10)
        assert(collect(s))(equalTo(Chunk.fromIterable(List(1, 2, 1, 99, 1, 2, 1, 99, 1, 2))))
      },
      test("large concat chain with repeated") {
        var stream: Stream[Nothing, Int] = Stream.succeed(1)
        var i                            = 0
        while (i < 99) { stream = stream ++ Stream.succeed(1); i += 1 }
        val result = stream.repeated.take(300).runFold(0L)(_ + _)
        assert(result)(isRight(equalTo(300L)))
      }
    ),

    // ---- ConcatReader: close-on-transition ----------------------------------

    suite("ConcatReader close-on-transition")(
      test("each segment is closed when exhausted before moving to next") {
        val closed = new java.util.ArrayList[String]()
        val s      = Stream(1).ensuring(closed.add("a")) ++
          Stream(2).ensuring(closed.add("b")) ++
          Stream(3).ensuring(closed.add("c"))
        s.runCollect
        assert(closed.toArray.toList)(equalTo(List("a", "b", "c")))
      },
      test("segment closes before next segment is evaluated (laziness preserved)") {
        val events = new java.util.ArrayList[String]()
        val s      = Stream(1).ensuring(events.add("close-a")) ++
          Stream.suspend { events.add("open-b"); Stream(2).ensuring(events.add("close-b")) } ++
          Stream.suspend { events.add("open-c"); Stream(3).ensuring(events.add("close-c")) }
        s.runCollect
        assert(events.toArray.toList)(equalTo(List("close-a", "open-b", "close-b", "open-c", "close-c")))
      },
      test("early termination closes only the active segment") {
        var aClosed    = false
        var bEvaluated = false
        var bClosed    = false
        val s          = Stream(1, 2, 3).ensuring { aClosed = true } ++
          Stream.suspend { bEvaluated = true; Stream(4, 5).ensuring { bClosed = true } }
        s.take(1).runDrain
        assert(aClosed)(isTrue) &&
        assert(bEvaluated)(isFalse) &&
        assert(bClosed)(isFalse)
      },
      test("early termination mid-second-segment closes first and second") {
        val closed = new java.util.ArrayList[String]()
        val s      = Stream(1).ensuring(closed.add("a")) ++
          Stream(2, 3, 4).ensuring(closed.add("b")) ++
          Stream.suspend { closed.add("open-c"); Stream(5).ensuring(closed.add("c")) }
        s.take(3).runDrain
        assert(closed.toArray.toList)(equalTo(List("a", "b")))
      },
      test("close on transition fires during repeated cycles") {
        val closes = new java.util.ArrayList[String]()
        val s      = (Stream(1).ensuring(closes.add("a")) ++ Stream(2).ensuring(closes.add("b"))).repeated.take(4)
        s.runDrain
        assert(closes.toArray.toList)(equalTo(List("a", "b", "a", "b")))
      },
      test("error in segment prevents opening next segment") {
        var bEvaluated = false
        val s          = (Stream(1) ++ Stream.fail("boom")) ++
          Stream.suspend { bEvaluated = true; Stream(2) }
        val result = s.runCollect
        assert(result.isLeft)(isTrue) &&
        assert(bEvaluated)(isFalse)
      },
      test("finalizers fire on each reset cycle") {
        var closeCount = 0
        val s          = (Stream(1).ensuring(closeCount += 1) ++ Stream(2).ensuring(closeCount += 1)).repeated.take(6)
        s.runDrain
        assert(closeCount)(isGreaterThanEqualTo(4))
      }
    ),

    // ---- ConcatReader: O(n) performance ------------------------------------

    suite("ConcatReader performance")(
      test("large left-associative concat is O(n) not O(n^2)") {
        var stream: Stream[Nothing, Int] = Stream.succeed(1)
        var i                            = 0
        while (i < 999) { stream = stream ++ Stream.succeed(1); i += 1 }
        val result = stream.runFold(0L)(_ + _)
        assert(result)(isRight(equalTo(1000L)))
      }
    )
  )
}
