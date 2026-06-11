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
import zio.blocks.streams.internal.EndOfStream
import zio.blocks.streams.internal.SinkError
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.internal.StreamState
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._
import StreamsGen._
import scala.util.control.{ControlThrowable, NonFatal}
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try

object StreamSpec extends StreamsBaseSpec {

  private def collect[A](s: Stream[Nothing, A]): Chunk[A] =
    s.runCollect.fold(_ => Chunk.empty, identity)

  private def collectE[E, A](s: Stream[E, A]): Either[E, Chunk[A]] =
    s.runCollect

  private def guarded[A](thunk: => A): Either[String, A] =
    try Right(thunk)
    catch { case _: OutOfMemoryError => Left("over-allocated Array(n)") }

  final case class Word(value: String)
  final case class Count(value: Int)
  final case class Flag(value: Boolean)
  final case class Ratio(value: Double)

  sealed trait TaggedValue
  final case class TaggedString(value: String)   extends TaggedValue
  final case class TaggedInt(value: Int)         extends TaggedValue
  final case class TaggedBoolean(value: Boolean) extends TaggedValue
  final case class TaggedDouble(value: Double)   extends TaggedValue

  sealed trait Animal
  final case class Dog(name: String) extends Animal
  final case class Cat(name: String) extends Animal

  private def tagStringIntBoolean(
    value: Either[Either[String, Int], Boolean]
  ): TaggedValue =
    value match {
      case Left(Left(value))  => TaggedString(value)
      case Left(Right(value)) => TaggedInt(value)
      case Right(value)       => TaggedBoolean(value)
    }

  private def tagStringIntBooleanDouble(
    value: Either[Either[Either[String, Int], Boolean], Double]
  ): TaggedValue =
    value match {
      case Left(left)   => tagStringIntBoolean(left)
      case Right(value) => TaggedDouble(value)
    }

  private def tagWordCountFlag(value: Any): TaggedValue =
    value match {
      case Word(value)               => TaggedString(value)
      case Count(value)              => TaggedInt(value)
      case Flag(value)               => TaggedBoolean(value)
      case Left(Left(Word(value)))   => TaggedString(value)
      case Left(Right(Count(value))) => TaggedInt(value)
      case Right(Flag(value))        => TaggedBoolean(value)
    }

  private def tagWordCountFlagRatio(
    value: Any
  ): TaggedValue =
    value match {
      case Word(value)         => TaggedString(value)
      case Count(value)        => TaggedInt(value)
      case Flag(value)         => TaggedBoolean(value)
      case Ratio(value)        => TaggedDouble(value)
      case Left(left)          => tagWordCountFlag(left)
      case Right(Ratio(value)) => TaggedDouble(value)
    }

  /**
   * A sink that reads every element into a `Chunk`, declaring error type `Int`.
   */
  private def readAllIntErr[A]: Sink[Int, A, Chunk[A]] =
    Sink.create[Int, A, Chunk[A]](r => r.readAll[A]())

  /**
   * An otherwise-empty source that raises a typed `StreamError` from `close()`.
   */
  private def streamErrorOnClose(value: Any): Reader[Int] =
    new Reader[Int] {
      private var _closed                   = false
      override def jvmType: JvmType         = JvmType.AnyRef
      def isClosed: Boolean                 = _closed
      def read[A1 >: Int](sentinel: A1): A1 = sentinel // empty stream
      def close(): Unit                     = { _closed = true; throw new StreamError(value) }
    }

  private val chunk3: Chunk[Int] = Chunk(1, 2, 3)
  private val chunk5: Chunk[Int] = Chunk(1, 2, 3, 4, 5)
  private val chunk2: Chunk[Int] = Chunk(10, 20)

  /** Captures the throwable a side-effecting thunk raises, or `null`. */
  private def caught(thunk: => Any): Throwable =
    try { thunk; null }
    catch { case t: Throwable => t }

  /** A stream that emits 1, 2, then fails with a typed error. */
  private val failing: Stream[String, Int] =
    (Stream(1, 2): Stream[Nothing, Int]) ++ (Stream.fail("boom"): Stream[String, Int])

  /**
   * Drains via a read loop, recovering only from throwables matched by
   * `recover`.
   */
  private def sinkCatching(recover: PartialFunction[Throwable, String]): Sink[Nothing, Int, String] =
    Sink.create[Nothing, Int, String] { r =>
      try {
        var v = r.read[Any](EndOfStream)
        while (v.asInstanceOf[AnyRef] ne EndOfStream) v = r.read[Any](EndOfStream)
        "consumed"
      } catch recover
    }

  private val boom = new RuntimeException("boom-sentinel")

  private def threwBoom(thunk: => Any): Boolean =
    try { thunk; false }
    catch { case t: Throwable => t.getMessage == "boom-sentinel" || hasBoomCause(t) }

  private def hasBoomCause(t: Throwable): Boolean =
    // Recursively search the cause chain AND suppressed exceptions (at any depth)
    // for the boom sentinel, matching the strongest original probe semantics.
    t != null && (t.getMessage == "boom-sentinel" || hasBoomCause(t.getCause) || t.getSuppressed.exists(hasBoomCause))

  private def threwIAE(thunk: => Any): Boolean =
    try { thunk; false }
    catch { case _: IllegalArgumentException => true }

  // Force the interpreter (deep) compilation path: > DepthCutoff (100) ops.
  private def deepIdentity(s: Stream[Nothing, Int], n: Int): Stream[Nothing, Int] = {
    var acc = s
    var i   = 0
    while (i < n) { acc = acc.map(x => x); i += 1 }
    acc
  }

  // Force the deep (Interpreter) compilation path: DepthCutoff is 100.
  private def deep(s: Stream[Nothing, Int]): Stream[Nothing, Int] =
    (0 until 130).foldLeft(s)((acc, _) => acc.map(x => x))

  private def managed(opens: AtomicInteger, closes: AtomicInteger, elems: Int*): Stream[Nothing, Int] =
    Stream.fromAcquireRelease({ opens.incrementAndGet(); () }, (_: Unit) => { closes.incrementAndGet(); () })(_ =>
      Stream(elems: _*)
    )

  // Invariant: when knownLength is defined, it must equal the actual element count.
  private def checkLen(s: Stream[Nothing, Int]): Boolean = s.knownLength match {
    case Some(n) => s.runCollect.map(_.length.toLong) == Right(n)
    case None    => true
  }

  private def deepSum(n: Int): Either[Nothing, Chunk[Int]] = {
    var s: Stream[Nothing, Int] = Stream.succeed(0)
    var i                       = 0
    while (i < n) {
      val k = i
      s = s.flatMap((x: Int) => Stream.succeed(x + k))
      i += 1
    }
    s.runCollect
  }

  def spec: Spec[TestEnvironment, Any] = suite("Stream")(
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
      },
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
      )
    ),
    suite("chunked")(
      test("exact multiple") {
        val s      = Stream.fromIterable(List(1, 2, 3, 4, 5, 6))
        val result = collect(s.chunked(3))
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
        val result = collect(s.chunked(3))
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
        assert(collect(s.chunked(3)))(equalTo(Chunk.empty))
      },
      test("n = 1") {
        val s      = Stream.fromIterable(List(1, 2, 3))
        val result = collect(s.chunked(1))
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
        val result = collect(s.chunked(5))
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
        val result = collect(s.chunked(10))
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
      suite("specialization")(
        test("chunked specialization with Int stream") {
          val s      = Stream.range(0, 6).chunked(3)
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
      )
    ),
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
      },
      // BUG-R7-02: `Reader.readable()` contract — "Returns `true` if the next
      // `read()` ... would return a value (not closed/sentinel). ... Subclasses
      // with buffered state should override for accuracy." The intersperse
      // reader caches the upcoming element when it emits a separator, but does
      // not override `readable()`; once the source is structurally exhausted the
      // default `!isClosed` reports `false` while the cached element is still
      // pending, so a documented readable()-gated consumer loop drops the final
      // element.
      test("intersperse_readable_remainsTrueWhileCachedElementPending [AdversarialReadableAccuracySpec]") {
        val out = Stream(1, 2)
          .intersperse(0)
          .run(Sink.create[Nothing, Int, (Int, Int, Boolean, Int)] { r =>
            val first        = r.read(-1) // 1
            val sep          = r.read(-1) // 0 (caches 2)
            val stillPending = r.readable()
            val last         = r.read(-1) // 2 — a value, so readable() had to be true
            (first, sep, stillPending, last)
          })
        assertTrue(out == Right((1, 0, true, 2)))
      }
    ),
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
      },
      // BUG-R7-02 (sibling manifestation): the scan reader has a pending `init`
      // element before the first read, but `readable()` is the default
      // `!isClosed` = `!source.isClosed`; over an already-exhausted (empty)
      // source it reports `false` even though the next read returns `init`.
      test("scan_readable_trueBeforePendingInitOnEmptySource [AdversarialReadableAccuracySpec]") {
        val out = (Stream.empty: Stream[Nothing, Int])
          .scan(42)(_ + _)
          .run(Sink.create[Nothing, Int, (Boolean, Int)] { r =>
            val beforeInit = r.readable()
            val init       = r.read(-1) // 42 — a value, so readable() had to be true
            (beforeInit, init)
          })
        assertTrue(out == Right((true, 42)))
      }
    ),
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
      test("step = n (same as chunked)") {
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
      },
      suite("step > n")(
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
      suite("specialization")(
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
      )
    ),
    suite("validation")(
      test("chunked(0) throws") {
        assert(try {
          Stream.fromIterable(List(1)).chunked(0); "ok"
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
    suite("render labels")(
      test("chunked render includes combinator name") {
        val s = Stream.range(0, 10).chunked(3)
        assert(s.render.contains("chunked"))(isTrue)
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
      test(
        "take(2).take(5) keeps the narrower window like List.take(2).take(5) across sources (convergence evidence)"
      ) {
        // setLimit contract (Reader scaladoc): successive window ops compose
        // in invocation order over the CURRENT live window, so a later, larger
        // take must not re-expand an already-narrowed window. Locked in here
        // for the native-pushdown (chunk/range), wrapper (iterable →
        // SkipLimitReader), and deep interpreter-compiled paths; the ByteBuffer
        // siblings violate it (see NioSpec [AdversarialWindowComposeSpec]).
        val oracle      = Chunk.fromIterable(List(0, 1, 2, 3, 4, 5, 6, 7).take(2).take(5))
        val viaIterable = collect(Stream.fromIterable(List(0, 1, 2, 3, 4, 5, 6, 7)).take(2).take(5))
        val viaChunk    = collect(Stream.fromChunk(Chunk(0, 1, 2, 3, 4, 5, 6, 7)).take(2).take(5))
        val viaRange    = collect(Stream.range(0, 8).take(2).take(5))
        val deep        = (1 to 150).foldLeft(Stream.range(0, 8))((s, _) => s.map(identity[Int]))
        val viaDeep     = collect(deep.take(2).take(5))
        assertTrue(viaIterable == oracle, viaChunk == oracle, viaRange == oracle, viaDeep == oracle)
      },
      test("chunked(1) wraps each element like List.grouped(1)") {
        val s = Stream.fromIterable(List(1, 2, 3))
        assert(collect(s.chunked(1)))(
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
    suite("Managed I/O")(
      test("fromInputStream closes the stream on completion") {
        var closed = false
        val is     = new java.io.InputStream {
          override def read(): Int   = -1
          override def close(): Unit = closed = true
        }
        val stream: Stream[java.io.IOException, Byte] = Stream.fromInputStream(is)
        stream.runDrain
        assert(closed)(isTrue)
      },
      test("fromInputStreamUnmanaged does not close the stream") {
        var closed = false
        val is     = new java.io.InputStream {
          override def read(): Int   = -1
          override def close(): Unit = closed = true
        }
        val stream: Stream[java.io.IOException, Byte] = Stream.fromInputStreamUnmanaged(is)
        stream.runDrain
        assert(closed)(isFalse)
      }
    ),
    suite("concat (++)")(
      test("disjoint concatenation") {
        val result: Stream[Nothing, String | Int] = Stream.succeed("hello") ++ Stream.succeed(42)
        val normalized                            = result.runCollect.map(_.map(separateStringInt))
        assert(normalized)(
          equalTo(Right(Chunk(Left("hello"), Right(42))))
        )
      },
      test("same element type stays the same") {
        val result = Stream.succeed("hello") ++ Stream.succeed("world")
        assert(result.runCollect)(equalTo(Right(Chunk("hello", "world"))))
      },
      test("subtype element type widens to supertype") {
        val result = (Stream.succeed(Dog("fido")): Stream[Nothing, Dog]) ++ Stream.succeed[Animal](Cat("milo"))
        assert(result.runCollect)(equalTo(Right(Chunk[Animal](Dog("fido"), Cat("milo")))))
      },
      test("empty left stream") {
        val result: Stream[Nothing, String | Int] = (Stream.empty: Stream[Nothing, String]) ++ Stream.succeed(42)
        val normalized                            = result.runCollect.map(_.map(separateStringInt))
        assert(normalized)(
          equalTo(Right(Chunk(Right(42))))
        )
      },
      test("empty right stream") {
        val result: Stream[Nothing, String | Int] = Stream.succeed("hello") ++ (Stream.empty: Stream[Nothing, Int])
        val normalized                            = result.runCollect.map(_.map(separateStringInt))
        assert(normalized)(
          equalTo(Right(Chunk(Left("hello"))))
        )
      },
      test("left stream error propagation") {
        val result = (Stream.fail("boom"): Stream[String, String]) ++ Stream.succeed(42)
        assert(result.runCollect)(equalTo(Left("boom")))
      },
      test("unrelated error types widen to common supertype") {
        sealed trait AppError
        final case class LeftErr(msg: String) extends AppError
        final case class RightErr(code: Int)  extends AppError

        val left: Stream[LeftErr, String] = Stream.fail(LeftErr("oops"))
        val right: Stream[RightErr, Int]  = Stream.succeed(42)
        val result                        = left ++ right
        val actual                        =
          result.runCollect.left.map(err => err: AppError)
        assert(actual)(equalTo(Left(LeftErr("oops"): AppError)))
      },
      test("right stream error propagation") {
        sealed trait AppError
        final case class LeftErr(msg: String) extends AppError
        final case class RightErr(code: Int)  extends AppError

        val left: Stream[LeftErr, String] = Stream.succeed("ok")
        val right: Stream[RightErr, Int]  = Stream.fail(RightErr(404))
        val result                        = left ++ right
        val actual                        =
          result.runCollect.left.map(err => err: AppError)
        assert(actual)(equalTo(Left(RightErr(404): AppError)))
      },
      test("two-stream primitive union type ascription compiles") {
        val _: Stream[Nothing, String | Int] = Stream.succeed("a") ++ Stream.succeed(1)
        assertTrue(true)
      },
      test("multiple elements per side") {
        val result: Stream[Nothing, String | Int] = Stream("a", "b") ++ Stream(1, 2)
        val normalized                            = result.runCollect.map(_.map(separateStringInt))
        assert(normalized)(
          equalTo(Right(Chunk(Left("a"), Left("b"), Right(1), Right(2))))
        )
      },
      test("three-stream primitive union type ascription compiles") {
        val _: Stream[Nothing, String | Int | Boolean] =
          Stream.succeed("hello") ++ Stream.succeed(42) ++ Stream.succeed(true)
        assertTrue(true)
      },
      test("three-stream concatenation") {
        val result: Stream[Nothing, Word | Count | Flag] =
          Stream.succeed(Word("hello")) ++ Stream.succeed(Count(42)) ++ Stream.succeed(Flag(true))

        val tagged = result.runCollect.map(
          _.map(tagWordCountFlag)
        )

        assert(tagged)(
          equalTo(Right(Chunk(TaggedString("hello"), TaggedInt(42), TaggedBoolean(true))))
        )
      },
      test("four-stream primitive union type ascription compiles") {
        val _: Stream[Nothing, String | Int | Boolean | Double] =
          Stream.succeed("hello") ++ Stream.succeed(42) ++ Stream.succeed(true) ++ Stream.succeed(3.14)
        assertTrue(true)
      },
      test("four-stream concatenation") {
        val result: Stream[Nothing, Word | Count | Flag | Ratio] =
          Stream.succeed(Word("hello")) ++ Stream.succeed(Count(42)) ++ Stream.succeed(Flag(true)) ++ Stream.succeed(
            Ratio(3.14)
          )

        val tagged = result.runCollect.map(
          _.map(tagWordCountFlagRatio)
        )

        assert(tagged)(
          equalTo(Right(Chunk(TaggedString("hello"), TaggedInt(42), TaggedBoolean(true), TaggedDouble(3.14))))
        )
      },
      test("mapError reader reset delegates to upstream (stateless restart, ITER-4)") {
        // mapError is a stateless pass-through, so reset must rewind the source
        // (delegating to upstream.reset()) rather than throwing — this is what
        // lets `xs.mapError(...).repeated` restart, like every other stateless
        // transform. `repeated` drives reset() internally; previously this threw
        // UnsupportedOperationException("ErrorMapped does not support reset").
        val result = Stream(1, 2, 3).mapError(identity).repeated.take(7).runCollect
        assertTrue(result == Right(Chunk(1, 2, 3, 1, 2, 3, 1)))
      },
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
      suite("ConcatReader performance")(
        test("large left-associative concat is O(n) not O(n^2)") {
          var stream: Stream[Nothing, Int] = Stream.succeed(1)
          var i                            = 0
          while (i < 999) { stream = stream ++ Stream.succeed(1); i += 1 }
          val result = stream.runFold(0L)(_ + _)
          assert(result)(isRight(equalTo(1000L)))
        }
      )
    ),
    suite("zip (&&)")(
      test("zips two streams of equal length") {
        val result = (Stream(1, 2, 3) && Stream("a", "b", "c")).runCollect
        assertTrue(result == Right(Chunk((1, "a"), (2, "b"), (3, "c"))))
      },
      test("shorter stream determines length (left shorter)") {
        val result = (Stream(1, 2) && Stream("a", "b", "c")).runCollect
        assertTrue(result == Right(Chunk((1, "a"), (2, "b"))))
      },
      test("shorter stream determines length (right shorter)") {
        val result = (Stream(1, 2, 3) && Stream("a")).runCollect
        assertTrue(result == Right(Chunk((1, "a"))))
      },
      test("empty stream yields empty") {
        val result = (Stream[Int]() && Stream(1, 2, 3)).runCollect
        assertTrue(result == Right(Chunk.empty))
      },
      test("triple zip flattens: a && b && c gives (A, B, C)") {
        val result = (Stream(1, 2) && Stream("a", "b") && Stream(true, false)).runCollect
        assertTrue(result == Right(Chunk((1, "a", true), (2, "b", false))))
      },
      // R6 convergence probe: the zip reader pulls both sides through the boxed
      // `read` lane, so a real Long.MaxValue element (the Long-lane EOF
      // sentinel) on either side must pair up, not truncate the zip.
      test("zip_longMaxValueElements_preserved [AdversarialConvergenceSweepSpec]") {
        val r = (Stream.fromChunk(Chunk(1L, Long.MaxValue)) && Stream.fromChunk(Chunk(Long.MaxValue, 2L))).runCollect
        assertTrue(r == Right(Chunk((1L, Long.MaxValue), (Long.MaxValue, 2L))))
      },
      test("error in left stream propagates") {
        val result = ((Stream.fail("boom"): Stream[String, Int]) && Stream(1, 2)).runCollect
        assertTrue(result == Left("boom"))
      },
      test("error in right stream propagates") {
        val result = (Stream(1, 2) && (Stream.fail("boom"): Stream[String, Int])).runCollect
        assertTrue(result == Left("boom"))
      },
      test("resources are cleaned up on both sides") {
        val leftClosed  = new java.util.concurrent.atomic.AtomicBoolean(false)
        val rightClosed = new java.util.concurrent.atomic.AtomicBoolean(false)
        val left        = Stream.range(0, 100).ensuring(leftClosed.set(true))
        val right       = Stream.range(0, 3).ensuring(rightClosed.set(true))
        val result      = (left && right).runCollect
        assertTrue(result == Right(Chunk((0, 0), (1, 1), (2, 2)))) &&
        assertTrue(leftClosed.get()) &&
        assertTrue(rightClosed.get())
      },
      test("&& closes left if right compilation fails") {
        var leftClosed = false
        val left       = Stream.fromAcquireRelease("left", (_: String) => leftClosed = true)(_ => Stream(1, 2, 3))
        val right      = Stream.fromReader[Nothing, Int](throw new RuntimeException("compile fail"))
        val result     = try {
          (left && right).runCollect
          "no exception"
        } catch {
          case _: RuntimeException => "threw"
        }
        assertTrue(result == "threw") && assertTrue(leftClosed)
      },
      test("&& close accumulates exceptions from both sides") {
        var leftClosed        = false
        var rightClosed       = false
        val left              = Stream(1, 2, 3).ensuring { leftClosed = true; throw new RuntimeException("left close") }
        val right             = Stream(4, 5, 6).ensuring { rightClosed = true; throw new RuntimeException("right close") }
        val (msg, suppressed) = try {
          (left && right).runCollect
          ("no exception", -1)
        } catch {
          case e: RuntimeException =>
            (e.getMessage, e.getSuppressed.length)
        }
        // Both sides are closed and both close failures surface. When the equal-
        // length zip completes, the final read closes the right side inline (the
        // left side reports end-of-stream first), so "right close" is the first
        // throwable in flight and becomes the primary. The subsequent zip-reader
        // close failure ("left close") is suppressed onto it rather than
        // discarded (Principle 4). Previously "left close" appeared to win only
        // because a `finally` block silently dropped the drain-time "right close".
        assertTrue(leftClosed) && assertTrue(rightClosed) &&
        assertTrue(msg == "right close") && assertTrue(suppressed == 1)
      }
    ),
    suite("union composition")(
      suite("catchAll")(
        test("recovery element type widens to union (disjoint)") {
          val result: Stream[Nothing, String | Int] =
            (Stream.fail("e"): Stream[String, String]).catchAll(_ => Stream.succeed(42))
          val normalized = result.runCollect.map(_.map(separateStringInt))
          assert(normalized)(equalTo(Right(Chunk(Right(42)))))
        },
        test("original element is kept when no error (disjoint)") {
          val result: Stream[Nothing, String | Int] =
            (Stream.succeed("hello"): Stream[String, String]).catchAll(_ => Stream.succeed(42))
          val normalized = result.runCollect.map(_.map(separateStringInt))
          assert(normalized)(equalTo(Right(Chunk(Left("hello")))))
        },
        test("related element types widen to common supertype (LUB)") {
          val result: Stream[Nothing, Animal] =
            (Stream.fail("e"): Stream[String, Dog]).catchAll(_ => Stream.succeed[Animal](Cat("milo")))
          assert(result.runCollect)(equalTo(Right(Chunk[Animal](Cat("milo")))))
        },
        test("original error is handled and does not appear in result type") {
          val result = (Stream.fail("boom"): Stream[String, Int]).catchAll(_ => Stream.succeed(1))
          assert(result.runCollect)(equalTo(Right(Chunk(1))))
        },
        test("recovers from an error encountered while dropping/skipping") {
          val result: Stream[Nothing, String | Int] =
            (Stream.fail("e"): Stream[String, String]).catchAll(_ => Stream.succeed(1)).drop(1)
          assert(result.runCollect)(equalTo(Right(Chunk.empty[String | Int])))
        }
      ),
      suite("orElse / ||")(
        test("element type widens to union, error is the fallback's (disjoint)") {
          val result: Stream[Nothing, String | Int] =
            (Stream.fail("boom"): Stream[String, String]) || Stream.succeed(42)
          val normalized = result.runCollect.map(_.map(separateStringInt))
          assert(normalized)(equalTo(Right(Chunk(Right(42)))))
        },
        test("result error type is the fallback's, not a union with the original") {
          val result: Stream[Int, Int] =
            (Stream.fail("a"): Stream[String, Int]).orElse(Stream.fail(99): Stream[Int, Int])
          assert(result.runCollect)(equalTo(Left(99)))
        },
        test("original elements pass through when no error") {
          val result: Stream[Nothing, String | Int] =
            (Stream.succeed("ok"): Stream[String, String]) || Stream.succeed(42)
          val normalized = result.runCollect.map(_.map(separateStringInt))
          assert(normalized)(equalTo(Right(Chunk(Left("ok")))))
        }
      ),
      suite("intersperse")(
        test("separator type widens to union (disjoint)") {
          val result: Stream[Nothing, String | Int] = Stream("a", "b", "c").intersperse(0)
          val normalized                            = result.runCollect.map(_.map(separateStringInt))
          assert(normalized)(
            equalTo(Right(Chunk(Left("a"), Right(0), Left("b"), Right(0), Left("c"))))
          )
        },
        test("same separator type keeps the element type") {
          val result = Stream(1, 2, 3).intersperse(0)
          assert(result.runCollect)(equalTo(Right(Chunk(1, 0, 2, 0, 3))))
        },
        test("single element has no separator") {
          val result: Stream[Nothing, String | Int] = Stream("only").intersperse(0)
          val normalized                            = result.runCollect.map(_.map(separateStringInt))
          assert(normalized)(equalTo(Right(Chunk(Left("only")))))
        }
      ),
      suite("concat error channel")(
        test("disjoint error types widen to union") {
          val result: Stream[String | Int, Int] =
            (Stream.fail("boom"): Stream[String, Int]) ++ (Stream.succeed(1): Stream[Int, Int])
          assert(result.runCollect.left.map(separateStringInt))(equalTo(Left(Left("boom"))))
        },
        test("right side disjoint error widens to union") {
          val result: Stream[String | Int, Int] =
            (Stream.succeed(1): Stream[String, Int]) ++ (Stream.fail(99): Stream[Int, Int])
          assert(result.runCollect.left.map(separateStringInt))(equalTo(Left(Right(99))))
        }
      ),
      suite("flatMap error channel")(
        test("inner error widens to union (disjoint)") {
          val result: Stream[String | Int, String] =
            (Stream.succeed(1): Stream[String, Int]).flatMap(_ => (Stream.fail(99): Stream[Int, String]))
          assert(result.runCollect.left.map(separateStringInt))(equalTo(Left(Right(99))))
        },
        test("outer error widens to union (disjoint)") {
          val result: Stream[String | Int, String] =
            (Stream.fail("outer"): Stream[String, Int]).flatMap(_ => (Stream.succeed("x"): Stream[Int, String]))
          assert(result.runCollect.left.map(separateStringInt))(equalTo(Left(Left("outer"))))
        },
        test("success path is unaffected") {
          val result = Stream(1, 2).flatMap(x => Stream(x, x * 10))
          assert(result.runCollect)(equalTo(Right(Chunk(1, 10, 2, 20))))
        }
      ),
      suite("&& error channel")(
        test("left disjoint error widens to union") {
          val result: Stream[String | Int, (Int, String)] =
            (Stream.fail("boom"): Stream[String, Int]) && (Stream.succeed("a"): Stream[Int, String])
          assert(result.runCollect.left.map(separateStringInt))(equalTo(Left(Left("boom"))))
        },
        test("right disjoint error widens to union") {
          val result: Stream[String | Int, (Int, String)] =
            (Stream.succeed(1): Stream[String, Int]) && (Stream.fail(99): Stream[Int, String])
          assert(result.runCollect.left.map(separateStringInt))(equalTo(Left(Right(99))))
        },
        test("element pairing is unaffected") {
          val result = Stream(1, 2, 3) && Stream("a", "b", "c")
          assert(result.runCollect)(equalTo(Right(Chunk((1, "a"), (2, "b"), (3, "c")))))
        }
      ),
      suite("catchDefect")(
        test("recovery element widens to union after a defect (disjoint)") {
          val dies: Stream[Nothing, String]         = Stream.succeed("a") ++ Stream.die(new RuntimeException("x"))
          val result: Stream[Nothing, String | Int] =
            dies.catchDefect { case _: RuntimeException => Stream.succeed(1) }
          val normalized = result.runCollect.map(_.map(separateStringInt))
          assert(normalized)(equalTo(Right(Chunk(Left("a"), Right(1)))))
        },
        test("typed error passes through and widens to union (disjoint)") {
          val result: Stream[String | Int, Int] =
            (Stream.fail("typed"): Stream[String, Int]).catchDefect { case _ => Stream.succeed(1): Stream[Int, Int] }
          assert(result.runCollect.left.map(separateStringInt))(equalTo(Left(Left("typed"))))
        },
        test("recovers from a defect encountered while dropping/skipping") {
          val dies: Stream[Nothing, String]         = Stream.die(new RuntimeException("x"))
          val result: Stream[Nothing, String | Int] =
            dies.catchDefect { case _: RuntimeException => Stream.succeed(1) }.drop(1)
          assert(result.runCollect)(equalTo(Right(Chunk.empty[String | Int])))
        }
      ),
      suite("run error channel")(
        test("sink error widens to union (disjoint)") {
          val result =
            (Stream.succeed(1): Stream[String, Int]).run(Sink.fail(42))
          assert(result.left.map(separateStringInt))(equalTo(Left(Right(42))))
        },
        test("stream error widens to union, distinct from sink error (disjoint)") {
          val result =
            (Stream.fail("boom"): Stream[String, Int]).run(readAllIntErr[Int])
          assert(result.left.map(separateStringInt))(equalTo(Left(Left("boom"))))
        },
        test("infallible sink keeps the stream error type") {
          val result = (Stream.fail("boom"): Stream[String, Int]).run(Sink.collectAll[Int])
          assert(result)(equalTo(Left("boom")))
        },
        // Regression: a typed StreamError raised while *closing* the source must
        // be returned as a (stream-side) Left, not escape as a thrown exception.
        // Previously the disjoint (Scala 2 Either) path closed in a `finally`
        // outside the classifying `catch`, so the error escaped uncaught.
        test("disjoint path: a StreamError from source close() is returned as a stream-side Left") {
          val stream = Stream.fromReader[String, Int](streamErrorOnClose("closeBoom"))
          val result = stream.run(readAllIntErr[Int])
          assert(result.left.map(separateStringInt))(equalTo(Left(Left("closeBoom"))))
        },
        test("identity path: a StreamError from source close() is returned as Left") {
          val stream = Stream.fromReader[String, Int](streamErrorOnClose("closeBoom"))
          val result = stream.run(Sink.collectAll[Int])
          assert(result)(equalTo(Left("closeBoom")))
        }
      ),
      suite("flattenAll error channel")(
        test("inner error widens to union with the outer error (disjoint)") {
          val outer: Stream[String, Stream[Int, String]] =
            Stream.succeed(Stream.fail(99): Stream[Int, String])
          val result: Stream[String | Int, String] = Stream.flattenAll(outer)
          assert(result.runCollect.left.map(separateStringInt))(equalTo(Left(Right(99))))
        },
        test("success path flattens") {
          val nested: Stream[Nothing, Stream[Nothing, Int]] =
            Stream(Stream(1, 2), Stream(3, 4))
          assert(Stream.flattenAll(nested).runCollect)(equalTo(Right(Chunk(1, 2, 3, 4))))
        }
      ),
      suite("Reader.concat / ++")(
        test("disjoint element types widen to union") {
          val reader: Reader[String | Int] =
            Reader.fromChunk[String](Chunk("a", "b")) ++ Reader.fromChunk[Int](Chunk(1, 2))
          assert(reader.readAll().map(separateStringInt))(
            equalTo(Chunk(Left("a"), Left("b"), Right(1), Right(2)))
          )
        },
        test("same element type is preserved (and stays specialized)") {
          val reader = Reader.fromChunk[Int](Chunk(1, 2)) ++ Reader.fromChunk[Int](Chunk(3, 4))
          assert(reader.jvmType)(equalTo(JvmType.Int)) &&
          assert(reader.readAll())(equalTo(Chunk(1, 2, 3, 4)))
        },
        test("related element types widen to common supertype (LUB)") {
          val reader: Reader[Animal] =
            (Reader.fromChunk[Dog](Chunk(Dog("fido"))): Reader[Dog]) ++ Reader.fromIterable[Animal](List(Cat("milo")))
          assert(reader.readAll())(equalTo(Chunk[Animal](Dog("fido"), Cat("milo"))))
        },
        test("widened reader reports AnyRef jvm lane") {
          val reader: Reader[Int | String] =
            Reader.fromChunk[Int](Chunk(1)) ++ Reader.fromChunk[String](Chunk("x"))
          assert(reader.jvmType)(equalTo(JvmType.AnyRef))
        }
      )
    ),
    suite("metadata")(
      suite("knownChunk")(
        test("fromChunk returns Some(chunk)") {
          val chunk = Chunk(1, 2, 3)
          assertTrue(Stream.fromChunk(chunk).knownChunk == Some(chunk))
        },
        test("fromChunk with empty chunk returns Some(empty)") {
          assertTrue(Stream.fromChunk(Chunk.empty[Int]).knownChunk == Some(Chunk.empty[Int]))
        },
        test("fromChunk with Byte chunk returns Some(chunk)") {
          val chunk = Chunk[Byte](1, 2)
          assertTrue(Stream.fromChunk(chunk).knownChunk == Some(chunk))
        },
        test("fromArray returns Some(chunk)") {
          val arr = Array(1, 2, 3)
          assertTrue(Stream.fromArray(arr).knownChunk == Some(Chunk.fromArray(arr)))
        },
        test("fromIterable returns None") {
          assertTrue(Stream.fromIterable(List(1, 2, 3)).knownChunk == None)
        },
        test("fromIterator returns None") {
          assertTrue(Stream.fromIterator(Iterator(1, 2, 3)).knownChunk == None)
        },
        test("fromRange returns None") {
          assertTrue(Stream.fromRange(0 until 10).knownChunk == None)
        },
        test("empty returns Some(Chunk.empty)") {
          assertTrue(Stream.empty.knownChunk == Some(Chunk.empty))
        },
        test("succeed returns None") {
          assertTrue(Stream.succeed(42).knownChunk == None)
        },
        test("map invalidates knownChunk") {
          assertTrue(Stream.fromChunk(chunk3).map(_ + 1).knownChunk == None)
        },
        test("filter invalidates knownChunk") {
          assertTrue(Stream.fromChunk(chunk3).filter(_ > 1).knownChunk == None)
        },
        test("take invalidates knownChunk") {
          assertTrue(Stream.fromChunk(chunk3).take(2).knownChunk == None)
        },
        test("drop invalidates knownChunk") {
          assertTrue(Stream.fromChunk(chunk3).drop(1).knownChunk == None)
        },
        test("concat invalidates knownChunk") {
          assertTrue((Stream.fromChunk(chunk3) ++ Stream.fromChunk(chunk2)).knownChunk == None)
        }
      ),
      suite("knownLength")(
        test("fromChunk returns Some(length)") {
          assertTrue(Stream.fromChunk(Chunk(1, 2, 3)).knownLength == Some(3L))
        },
        test("fromChunk with empty chunk returns Some(0)") {
          assertTrue(Stream.fromChunk(Chunk.empty[Int]).knownLength == Some(0L))
        },
        test("fromArray returns Some(length)") {
          assertTrue(Stream.fromArray(Array(1, 2, 3)).knownLength == Some(3L))
        },
        test("fromIterable with known size returns Some(length)") {
          assertTrue(Stream.fromIterable(Vector(1, 2, 3)).knownLength == Some(3L))
        },
        test("fromRange returns Some(length)") {
          assertTrue(Stream.fromRange(0 until 10).knownLength == Some(10L))
        },
        test("fromIterator returns None") {
          assertTrue(Stream.fromIterator(Iterator(1)).knownLength == None)
        },
        test("empty returns Some(0)") {
          assertTrue(Stream.empty.knownLength == Some(0L))
        },
        test("succeed returns Some(1)") {
          assertTrue(Stream.succeed(42).knownLength == Some(1L))
        }
      ),
      suite("knownLength propagation")(
        test("map preserves knownLength") {
          assertTrue(Stream.fromChunk(chunk3).map(_ + 1).knownLength == Some(3L))
        },
        test("filter invalidates knownLength") {
          assertTrue(Stream.fromChunk(chunk3).filter(_ > 1).knownLength == None)
        },
        test("take clamps to min of n and knownLength") {
          assertTrue(Stream.fromChunk(chunk5).take(2).knownLength == Some(2L))
        },
        test("take with n > length clamps to length") {
          assertTrue(Stream.fromChunk(chunk5).take(100).knownLength == Some(5L))
        },
        test("drop subtracts from knownLength") {
          assertTrue(Stream.fromChunk(chunk5).drop(2).knownLength == Some(3L))
        },
        test("drop more than length clamps to 0") {
          assertTrue(Stream.fromChunk(chunk5).drop(100).knownLength == Some(0L))
        },
        test("take with negative n clamps knownLength to 0") {
          assertTrue(Stream.fromChunk(chunk5).take(-1).knownLength == Some(0L))
        },
        test("take with negative n produces empty stream") {
          assertTrue(Stream.fromChunk(chunk5).take(-1).runCollect == Right(Chunk.empty[Int]))
        },
        test("drop with negative n preserves knownLength (no-op)") {
          assertTrue(Stream.fromChunk(chunk5).drop(-1).knownLength == Some(5L))
        },
        test("drop with negative n produces all elements") {
          assertTrue(Stream.fromChunk(chunk5).drop(-1).runCollect == Right(chunk5))
        },
        test("take on unknown-length stream returns None") {
          assertTrue(Stream.fromIterator(Iterator(1, 2, 3)).take(2).knownLength == None)
        },
        test("concat sums both known lengths") {
          assertTrue((Stream.fromChunk(chunk3) ++ Stream.fromChunk(chunk2)).knownLength == Some(5L))
        },
        test("concat with one unknown returns None") {
          assertTrue((Stream.fromChunk(chunk3) ++ Stream.fromIterator(Iterator(1))).knownLength == None)
        },
        test("repeated returns None") {
          assertTrue(Stream.fromChunk(chunk3).repeated.knownLength == None)
        },
        test("flatMap invalidates knownLength") {
          assertTrue(Stream.fromChunk(chunk3).flatMap(i => Stream.succeed(i)).knownLength == None)
        },
        test("map then take chains correctly") {
          assertTrue(Stream.fromChunk(chunk3).map(_ + 1).take(2).knownLength == Some(2L))
        },
        test("drop then map chains correctly") {
          assertTrue(Stream.fromChunk(chunk3).drop(1).map(_ + 1).knownLength == Some(2L))
        }
      ),
      suite("fromArray")(
        test("knownChunk returns Some(chunk)") {
          val arr = Array(1, 2, 3)
          assertTrue(Stream.fromArray(arr).knownChunk == Some(Chunk.fromArray(arr)))
        },
        test("knownLength returns Some(length)") {
          assertTrue(Stream.fromArray(Array(1, 2, 3)).knownLength == Some(3L))
        },
        test("empty array has knownLength Some(0)") {
          assertTrue(Stream.fromArray(Array.empty[Int]).knownLength == Some(0L))
        },
        test("Byte array specialization works") {
          val arr = Array[Byte](1, 2, 3)
          assertTrue(Stream.fromArray(arr).knownChunk == Some(Chunk.fromArray(arr)))
        },
        test("roundtrip collects correctly") {
          val arr = Array[Byte](1, 2, 3)
          assertTrue(Stream.fromArray(arr).runCollect == Right(Chunk.fromArray(arr)))
        }
      ),
      suite("fromIterable metadata")(
        test("Vector has knownLength") {
          assertTrue(Stream.fromIterable(Vector(1, 2, 3)).knownLength == Some(3L))
        },
        test("ArraySeq has knownLength") {
          assertTrue(Stream.fromIterable(scala.collection.immutable.ArraySeq(1, 2)).knownLength == Some(2L))
        },
        test("List has no knownLength") {
          assertTrue(Stream.fromIterable(List(1, 2, 3)).knownLength == None)
        },
        test("knownChunk is None") {
          assertTrue(Stream.fromIterable(Vector(1, 2, 3)).knownChunk == None)
        },
        test("LazyList has no knownLength") {
          assertTrue(Stream.fromIterable(LazyList(1, 2, 3)).knownLength == None)
        },
        test("roundtrip collects correctly") {
          assertTrue(Stream.fromIterable(List(1, 2, 3)).runCollect == Right(Chunk(1, 2, 3)))
        }
      ),
      suite("fromRange metadata")(
        test("exclusive range has knownLength") {
          assertTrue(Stream.fromRange(0 until 10).knownLength == Some(10L))
        },
        test("inclusive range has knownLength") {
          assertTrue(Stream.fromRange(1 to 5).knownLength == Some(5L))
        },
        test("empty range has knownLength Some(0)") {
          assertTrue(Stream.fromRange(Range(0, 0)).knownLength == Some(0L))
        },
        test("knownChunk is None") {
          assertTrue(Stream.fromRange(0 until 10).knownChunk == None)
        },
        test("roundtrip collects correctly") {
          assertTrue(Stream.fromRange(1 to 3).runCollect == Right(Chunk(1, 2, 3)))
        }
      )
    ),
    suite("render")(
      // ---- Source rendering ---------------------------------------------------

      suite("sources")(
        test("Stream.empty renders as Stream.empty") {
          assert(Stream.empty.render)(equalTo("Stream.empty"))
        },
        test("Stream.fail renders as Stream.fail(...)") {
          assert(Stream.fail("oops").render)(equalTo("Stream.fail(...)"))
        },
        test("Stream.succeed renders as Stream.succeed(...)") {
          assert(Stream.succeed(42).render)(equalTo("Stream.succeed(...)"))
        },
        test("Stream.range renders with arguments") {
          assert(Stream.range(0, 10).render)(equalTo("Stream.range(0, 10)"))
        },
        test("Stream.apply with small list renders elements") {
          assert(Stream(1, 2, 3).render)(equalTo("Stream(1, 2, 3)"))
        },
        test("Stream.apply with empty list renders Stream()") {
          assert(Stream[Int]().render)(equalTo("Stream()"))
        },
        test("Stream.apply with many elements truncates") {
          assert(Stream(1, 2, 3, 4, 5, 6, 7).render)(equalTo("Stream(1, 2, 3, 4, 5, ...)"))
        },
        test("Stream.fromIterable renders as Stream.fromIterable(...)") {
          assert(Stream.fromIterable(List(1, 2, 3)).render)(equalTo("Stream.fromIterable(...)"))
        },
        test("Stream.fromChunk renders as Stream.fromChunk(...)") {
          assert(Stream.fromChunk(zio.blocks.chunk.Chunk(1, 2)).render)(equalTo("Stream.fromChunk(...)"))
        },
        test("Stream.repeat renders as Stream.repeat(...)") {
          assert(Stream.repeat(1).render)(equalTo("Stream.repeat(...)"))
        },
        test("Stream.unfold renders as Stream.unfold(...)") {
          assert(Stream.unfold(0)(n => if (n < 5) Some((n, n + 1)) else None).render)(equalTo("Stream.unfold(...)"))
        },
        test("Stream.fromReader renders as Stream.fromReader(...)") {
          assert(Stream.fromReader(zio.blocks.streams.io.Reader.closed).render)(equalTo("Stream.fromReader(...)"))
        },
        test("Stream.fromIterator renders as Stream.fromIterator(...)") {
          assert(Stream.fromIterator(Iterator(1, 2)).render)(equalTo("Stream.fromIterator(...)"))
        },
        test("Stream.fromRange renders as Stream.fromRange(...)") {
          assert(Stream.fromRange(0 until 10).render)(equalTo("Stream.fromRange(...)"))
        }
      ),

      // ---- Transform rendering ------------------------------------------------

      suite("transforms")(
        test("map renders as .map(...)") {
          assert(Stream.range(0, 10).map(_ + 1).render)(equalTo("Stream.range(0, 10).map(...)"))
        },
        test("filter renders as .filter(...)") {
          assert(Stream.range(0, 10).filter(_ > 5).render)(equalTo("Stream.range(0, 10).filter(...)"))
        },
        test("flatMap renders as .flatMap(...)") {
          assert(Stream.range(0, 3).flatMap(n => Stream(n)).render)(equalTo("Stream.range(0, 3).flatMap(...)"))
        },
        test("take renders with count") {
          assert(Stream.range(0, 10).take(3).render)(equalTo("Stream.range(0, 10).take(3)"))
        },
        test("drop renders with count") {
          assert(Stream.range(0, 10).drop(5).render)(equalTo("Stream.range(0, 10).drop(5)"))
        },
        test("takeWhile renders as .takeWhile(...)") {
          assert(Stream.range(0, 10).takeWhile(_ < 5).render)(equalTo("Stream.range(0, 10).takeWhile(...)"))
        },
        test("repeated renders as .repeated") {
          assert(Stream(1, 2, 3).repeated.render)(equalTo("Stream(1, 2, 3).repeated"))
        }
      ),

      // ---- Error ops rendering ------------------------------------------------

      suite("error ops")(
        test("mapError renders as .mapError(...)") {
          assert(Stream.fail("err").mapError(_.length).render)(equalTo("Stream.fail(...).mapError(...)"))
        },
        test("catchAll renders as .catchAll(...)") {
          assert(Stream.fail("err").catchAll(_ => Stream.empty).render)(equalTo("Stream.fail(...).catchAll(...)"))
        },
        test("catchDefect renders as .catchDefect(...)") {
          assert(Stream.empty.catchDefect { case _ => Stream.empty }.render)(
            equalTo("Stream.empty.catchDefect(...)")
          )
        }
      ),

      // ---- Composition rendering ----------------------------------------------

      suite("composition")(
        test("concat renders with ++") {
          assert((Stream(1, 2) ++ Stream(3, 4)).render)(equalTo("Stream(1, 2) ++ Stream(3, 4)"))
        },
        test("ensuring renders as .ensuring(...)") {
          assert(Stream(1, 2).ensuring(()).render)(equalTo("Stream(1, 2).ensuring(...)"))
        },
        test("suspend renders as Stream.suspend(...)") {
          assert(Stream.suspend(Stream(1)).render)(equalTo("Stream.suspend(...)"))
        }
      ),

      // ---- Chained pipelines --------------------------------------------------

      suite("chained pipelines")(
        test("chained map/filter/take renders correctly") {
          val s = Stream.range(0, 10).map(_ + 1).filter(_ > 5).take(3)
          assert(s.render)(equalTo("Stream.range(0, 10).map(...).filter(...).take(3)"))
        },
        test("complex chain renders correctly") {
          val s = Stream.fromIterable(List(1, 2, 3)).map(_ * 2).drop(1).take(5)
          assert(s.render)(equalTo("Stream.fromIterable(...).map(...).drop(1).take(5)"))
        }
      ),

      // ---- toString delegates to render ---------------------------------------

      suite("toString")(
        test("toString returns same as render") {
          val s = Stream.range(0, 10).map(_ + 1)
          assert(s.toString)(equalTo(s.render))
        }
      )
    ),
    suite("state")(
      test("empty is all zeros") {
        val s = StreamState.empty
        assertTrue(
          StreamState.stageStart(s) == 0,
          StreamState.incomingLen(s) == 0,
          StreamState.stageEnd(s) == 0,
          StreamState.outgoingLen(s) == 0,
          StreamState.outputLane(s) == 0
        )
      },
      test("apply and getters round-trip") {
        val s = StreamState(10, 20, 30, 40, 3)
        assertTrue(
          StreamState.stageStart(s) == 10,
          StreamState.incomingLen(s) == 20,
          StreamState.stageEnd(s) == 30,
          StreamState.outgoingLen(s) == 40,
          StreamState.outputLane(s) == 3
        )
      },
      test("max index values (13-bit = 8191)") {
        val s = StreamState(8191, 8191, 8191, 8191, 4)
        assertTrue(
          StreamState.stageStart(s) == 8191,
          StreamState.incomingLen(s) == 8191,
          StreamState.stageEnd(s) == 8191,
          StreamState.outgoingLen(s) == 8191,
          StreamState.outputLane(s) == 4
        )
      },
      test("individual component independence") {
        assertTrue(StreamState.stageStart(StreamState(1, 0, 0, 0)) == 1) &&
        assertTrue(StreamState.incomingLen(StreamState(1, 0, 0, 0)) == 0) &&
        assertTrue(StreamState.incomingLen(StreamState(0, 1, 0, 0)) == 1) &&
        assertTrue(StreamState.stageEnd(StreamState(0, 0, 1, 0)) == 1) &&
        assertTrue(StreamState.outgoingLen(StreamState(0, 0, 0, 1)) == 1) &&
        assertTrue(StreamState.outputLane(StreamState(0, 0, 0, 0, 4)) == 4)
      },
      test("withStageStart preserves other fields") {
        val s  = StreamState(1, 2, 3, 4, 2)
        val s2 = StreamState.withStageStart(s, 100)
        assertTrue(
          StreamState.stageStart(s2) == 100,
          StreamState.incomingLen(s2) == 2,
          StreamState.stageEnd(s2) == 3,
          StreamState.outgoingLen(s2) == 4,
          StreamState.outputLane(s2) == 2
        )
      },
      test("withIncomingLen preserves other fields") {
        val s  = StreamState(1, 2, 3, 4, 2)
        val s2 = StreamState.withIncomingLen(s, 200)
        assertTrue(
          StreamState.stageStart(s2) == 1,
          StreamState.incomingLen(s2) == 200,
          StreamState.stageEnd(s2) == 3,
          StreamState.outgoingLen(s2) == 4,
          StreamState.outputLane(s2) == 2
        )
      },
      test("withStageEnd preserves other fields") {
        val s  = StreamState(1, 2, 3, 4, 2)
        val s2 = StreamState.withStageEnd(s, 300)
        assertTrue(
          StreamState.stageStart(s2) == 1,
          StreamState.incomingLen(s2) == 2,
          StreamState.stageEnd(s2) == 300,
          StreamState.outgoingLen(s2) == 4,
          StreamState.outputLane(s2) == 2
        )
      },
      test("withOutgoingLen preserves other fields") {
        val s  = StreamState(1, 2, 3, 4, 2)
        val s2 = StreamState.withOutgoingLen(s, 400)
        assertTrue(
          StreamState.stageStart(s2) == 1,
          StreamState.incomingLen(s2) == 2,
          StreamState.stageEnd(s2) == 3,
          StreamState.outgoingLen(s2) == 400,
          StreamState.outputLane(s2) == 2
        )
      },
      test("withOutputLane preserves other fields") {
        val s  = StreamState(1, 2, 3, 4, 2)
        val s2 = StreamState.withOutputLane(s, 4)
        assertTrue(
          StreamState.stageStart(s2) == 1,
          StreamState.incomingLen(s2) == 2,
          StreamState.stageEnd(s2) == 3,
          StreamState.outgoingLen(s2) == 4,
          StreamState.outputLane(s2) == 4
        )
      },
      test("all lanes round-trip") {
        (0 to 4).foldLeft(assertTrue(true)) { (acc, lane) =>
          val s = StreamState(0, 0, 0, 0, lane)
          acc && assertTrue(StreamState.outputLane(s) == lane)
        }
      },
      test("boundary: power-of-two values") {
        val s = StreamState(4096, 2048, 1024, 512, 4)
        assertTrue(
          StreamState.stageStart(s) == 4096,
          StreamState.incomingLen(s) == 2048,
          StreamState.stageEnd(s) == 1024,
          StreamState.outgoingLen(s) == 512,
          StreamState.outputLane(s) == 4
        )
      }
    ),
    suite("repeated")(
      // ---- Basic repetition ---------------------------------------------------

      suite("basic repetition")(
        test("single element stream repeats that element") {
          val result = Stream.succeed(42).repeated.take(5).runCollect
          assert(result)(equalTo(Right(Chunk(42, 42, 42, 42, 42))))
        },
        test("multi-element stream repeats the full sequence") {
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.take(9).runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 3, 1, 2, 3, 1, 2, 3))))
        },
        test("take fewer than one full cycle") {
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.take(2).runCollect
          assert(result)(equalTo(Right(Chunk(1, 2))))
        },
        test("take exactly one full cycle") {
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.take(3).runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 3))))
        },
        test("take across cycle boundary") {
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.take(7).runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 3, 1, 2, 3, 1))))
        },
        test("range stream repeats correctly") {
          val result = Stream.range(0, 3).repeated.take(9).runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2, 0, 1, 2, 0, 1, 2))))
        },
        test("fromChunk stream repeats correctly") {
          val result = Stream.fromChunk(Chunk(10, 20)).repeated.take(6).runCollect
          assert(result)(equalTo(Right(Chunk(10, 20, 10, 20, 10, 20))))
        },
        test("unfold stream repeats correctly") {
          val nats3  = Stream.unfold(0)(n => if (n < 3) Some((n, n + 1)) else None)
          val result = nats3.repeated.take(9).runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2, 0, 1, 2, 0, 1, 2))))
        }
      ),

      // ---- Empty stream -------------------------------------------------------

      suite("empty stream")(
        test("repeated empty stream with take(0) yields empty") {
          val result = Stream.empty.repeated.take(0).runCollect
          assert(result)(equalTo(Right(Chunk.empty)))
        }
      ),

      // ---- Composed pipelines -------------------------------------------------

      suite("composed pipelines")(
        test("map then repeated") {
          val result = Stream.fromIterable(List(1, 2, 3)).map(_ * 10).repeated.take(6).runCollect
          assert(result)(equalTo(Right(Chunk(10, 20, 30, 10, 20, 30))))
        },
        test("repeated then map") {
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.map(_ * 10).take(6).runCollect
          assert(result)(equalTo(Right(Chunk(10, 20, 30, 10, 20, 30))))
        },
        test("filter then repeated") {
          // filter evens from [1,2,3,4] = [2,4], repeated
          val result = Stream.fromIterable(List(1, 2, 3, 4)).filter(_ % 2 == 0).repeated.take(6).runCollect
          assert(result)(equalTo(Right(Chunk(2, 4, 2, 4, 2, 4))))
        },
        test("repeated then filter") {
          // repeat [1,2,3], then filter evens → [2, 2, 2, ...]
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.filter(_ % 2 == 0).take(4).runCollect
          assert(result)(equalTo(Right(Chunk(2, 2, 2, 2))))
        },
        test("concat then repeated replays all segments on each cycle") {
          // ConcatReader resets all segments: each cycle replays 1,2,3
          val s      = Stream.fromIterable(List(1, 2)) ++ Stream.fromIterable(List(3))
          val result = s.repeated.take(9).runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 3, 1, 2, 3, 1, 2, 3))))
        },
        test("concat + map + filter with repeated replays all segments") {
          // (1,2) ++ (3,4) → map *2 → (2,4,6,8) → filter even → all pass
          // On repeat, all segments reset → full cycle: 2,4,6,8
          val s = (Stream.fromIterable(List(1, 2)) ++ Stream.fromIterable(List(3, 4)))
            .map(_ * 2)
            .filter(_ % 2 == 0)
            .repeated
            .take(8)
          val result = s.runCollect
          assert(result)(equalTo(Right(Chunk(2, 4, 6, 8, 2, 4, 6, 8))))
        },
        test("repeated with drop") {
          // [1,2,3].repeated.drop(2) skips 1,2 → 3,1,2,3,1,2,3,...
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.drop(2).take(7).runCollect
          assert(result)(equalTo(Right(Chunk(3, 1, 2, 3, 1, 2, 3))))
        },
        test("repeated with drop larger than one cycle") {
          // [1,2,3].repeated.drop(5) skips 1,2,3,1,2 → 3,1,2,3,...
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.drop(5).take(4).runCollect
          assert(result)(equalTo(Right(Chunk(3, 1, 2, 3))))
        },
        test("repeated with takeWhile") {
          // [1,2,3].repeated → takeWhile(_ < 3) → stops at first 3: [1,2]
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.takeWhile(_ < 3).runCollect
          assert(result)(equalTo(Right(Chunk(1, 2))))
        }
      ),

      // ---- Nested repeats -----------------------------------------------------

      suite("nested repeats")(
        test("repeated.repeated behaves like repeated (take terminates)") {
          val single = collect(Stream.fromIterable(List(1, 2)).repeated.take(8))
          val nested = collect(Stream.fromIterable(List(1, 2)).repeated.repeated.take(8))
          assert(nested)(equalTo(single))
        },
        test("nested repeat element order is preserved") {
          val result = Stream.fromIterable(List(1, 2, 3)).repeated.repeated.take(9).runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 3, 1, 2, 3, 1, 2, 3))))
        }
      ),

      // ---- Repeat after flatMap -----------------------------------------------

      suite("repeat after flatMap")(
        test("flatMap then repeated replays the flat-mapped stream on each cycle") {
          // flatMap produces a finite inner stream per outer element; `repeated`
          // restarts the outer source and recompiles the inner streams, so the
          // full flat-mapped sequence replays on every cycle.
          val result = Stream
            .fromIterable(List(1, 2))
            .flatMap(i => Stream.succeed(i * 10))
            .repeated
            .take(6)
            .runCollect
          assert(result)(equalTo(Right(Chunk(10, 20, 10, 20, 10, 20))))
        },
        test("flatMap with multi-element inner streams replays on repeat") {
          val result = Stream
            .fromIterable(List(1, 2))
            .flatMap(i => Stream.fromIterable(List(i, i * 10)))
            .repeated
            .take(8)
            .runCollect
          assert(result)(equalTo(Right(Chunk(1, 10, 2, 20, 1, 10, 2, 20))))
        }
      ),

      // ---- Resource management ------------------------------------------------

      suite("resource management")(
        test("ensuring finalizer runs when repeated stream is closed via take") {
          var count  = 0
          val result = Stream
            .fromIterable(List(1, 2))
            .ensuring(count += 1)
            .repeated
            .take(6)
            .runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 1, 2, 1, 2)))) &&
          assertTrue(count == 1)
        },
        test("ensuring finalizer runs when repeated stream is closed early via take") {
          var finalized = false
          val result    = Stream
            .fromIterable(List(1, 2, 3))
            .ensuring { finalized = true }
            .repeated
            .take(2)
            .runCollect
          assert(result)(equalTo(Right(Chunk(1, 2)))) &&
          assertTrue(finalized)
        },
        test("ensuring works on non-repeated stream (sanity check)") {
          var finalized = false
          val result    = Stream
            .fromIterable(List(1, 2, 3))
            .ensuring { finalized = true }
            .runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 3)))) &&
          assertTrue(finalized)
        }
      ),

      // ---- Error handling -----------------------------------------------------

      suite("error handling")(
        test("repeated stream that fails propagates error") {
          // fail(e).repeated should propagate the error (not silently restart)
          val result = Stream.fail("boom").repeated.take(3).runCollect
          assert(result)(equalTo(Left("boom")))
        },
        test("repeated stream where error occurs mid-stream propagates error") {
          val s      = (Stream.fromIterable(List(1, 2)) ++ Stream.fail("err")).repeated.take(10)
          val result = s.runCollect
          assert(result)(equalTo(Left("err")))
        },
        test("catchAll after repeated catches repeated stream error") {
          val result = (Stream.fromIterable(List(1, 2)) ++ Stream.fail("err")).repeated
            .take(10)
            .catchAll((_: String) => Stream.succeed(99))
            .runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 99))))
        },
        test("catchDefect after repeated catches defect") {
          val defectStream = Stream.fromReader[Nothing, Int](new Reader[Int] {
            private var emitted                   = 0
            def isClosed: Boolean                 = false
            def read[A1 >: Int](sentinel: A1): A1 = {
              emitted += 1
              if (emitted <= 2) Int.box(emitted).asInstanceOf[A1]
              else throw new RuntimeException("defect")
            }
            def close(): Unit = ()
          })
          val result = defectStream.repeated
            .take(10)
            .catchDefect { case _: RuntimeException => Stream.succeed(99) }
            .runCollect
          assert(result)(equalTo(Right(Chunk(1, 2, 99))))
        }
      ),

      // ---- Element order preservation -----------------------------------------

      suite("element order preservation")(
        test("elements repeat in original order across cycles") {
          val data     = List(10, 20, 30, 40, 50)
          val result   = collect(Stream.fromIterable(data).repeated.take(15))
          val expected = Chunk.fromIterable(
            List.fill(3)(data).flatten
          )
          assert(result)(equalTo(expected))
        },
        test("string elements repeat in order") {
          val data   = List("a", "b", "c")
          val result = collect(Stream.fromIterable(data).repeated.take(9))
          assert(result)(equalTo(Chunk.fromIterable(List("a", "b", "c", "a", "b", "c", "a", "b", "c"))))
        }
      ),

      // ---- Property: s.repeated.take(N) == (s ++ s ++ ...).take(N) -----------

      suite("property: repeated ≡ concat repetitions")(
        test("first N elements of s.repeated == (s ++ s ++ ... ++ s).take(N)") {
          check(Gen.chunkOfBounded(1, 10)(genInt), Gen.int(1, 30)) { (zChunk, n) =>
            val chunk          = Chunk.fromIterable(zChunk)
            val repeatedResult = collect(Stream.fromChunk(chunk).repeated.take(n))
            // Build the expected by manual concatenation
            val copies       = (n / math.max(chunk.length, 1)) + 1
            val concatStream = (0 until copies).foldLeft(Stream.empty: Stream[Nothing, Int]) { (acc, _) =>
              acc ++ Stream.fromChunk(chunk)
            }
            val concatResult = collect(concatStream.take(n))
            assert(repeatedResult)(equalTo(concatResult))
          }
        },
        test("property with varying chunk sizes") {
          check(Gen.chunkOfBounded(1, 5)(genInt), Gen.int(1, 20)) { (zChunk, n) =>
            val chunk          = Chunk.fromIterable(zChunk)
            val repeatedResult = collect(Stream.fromChunk(chunk).repeated.take(n))
            // verify length
            val expectedLen = math.min(n, Int.MaxValue).toInt
            assertTrue(repeatedResult.length == expectedLen) &&
            // verify cycling: element at index i == chunk(i % chunk.length)
            assertTrue(repeatedResult.toList.zipWithIndex.forall { case (elem, i) =>
              elem == chunk(i % chunk.length)
            })
          }
        }
      ),

      // ---- Stream.repeat(a) (infinite single value) --------------------------

      suite("Stream.repeat companion")(
        test("Stream.repeat(a) emits infinite a's (take first 5)") {
          assert(Stream.repeat(7).take(5).runCollect)(equalTo(Right(Chunk(7, 7, 7, 7, 7))))
        },
        test("Stream.repeat(a).map(f).take(n) works") {
          assert(Stream.repeat(3).map(_ * 2).take(4).runCollect)(equalTo(Right(Chunk(6, 6, 6, 6))))
        },
        test("Stream.repeat(a).filter(pred).take(n) works") {
          // filter always true — should emit the value
          assert(Stream.repeat(5).filter(_ > 0).take(3).runCollect)(equalTo(Right(Chunk(5, 5, 5))))
        },
        test("Stream.repeat(a).drop(n).take(m) works") {
          assert(Stream.repeat(1).drop(100).take(3).runCollect)(equalTo(Right(Chunk(1, 1, 1))))
        }
      ),

      // ---- Edge cases ---------------------------------------------------------

      suite("edge cases")(
        test("take(0) on repeated yields empty") {
          assert(Stream.fromIterable(List(1, 2, 3)).repeated.take(0).runCollect)(
            equalTo(Right(Chunk.empty))
          )
        },
        test("repeated with take(1) yields first element") {
          assert(Stream.fromIterable(List(10, 20, 30)).repeated.take(1).runCollect)(
            equalTo(Right(Chunk(10)))
          )
        },
        test("single-element chunk repeated many cycles") {
          val result = Stream.fromChunk(Chunk(42)).repeated.take(100).runCollect
          assert(result)(equalTo(Right(Chunk.fromIterable(List.fill(100)(42)))))
        },
        test("large chunk repeated a few cycles") {
          val data     = Chunk.fromIterable(1 to 50)
          val result   = collect(Stream.fromChunk(data).repeated.take(150))
          val expected = Chunk.fromIterable(List.fill(3)((1 to 50).toList).flatten)
          assert(result)(equalTo(expected))
        }
      )
    ),
    suite("error handling")(
      // ---- Suite 1: Error construction ----

      suite("Error construction")(
        test("Stream.fail(e).runCollect returns Left(e)") {
          assert(Stream.fail("boom").runCollect)(equalTo(Left("boom")))
        },
        test("Stream.fail(e).runDrain returns Left(e)") {
          assert(Stream.fail(42).runDrain)(equalTo(Left(42)))
        },
        test("Stream.die(t).runCollect throws t") {
          val t      = new RuntimeException("die")
          val result = try {
            Stream.die(t).runCollect
            "no exception"
          } catch {
            case e: RuntimeException => e.getMessage
          }
          assert(result)(equalTo("die"))
        },
        test("Stream.attempt { 42 }.runCollect returns Right(Chunk(42))") {
          assert(Stream.attempt(42).runCollect)(equalTo(Right(Chunk(42))))
        },
        test("Stream.attempt { throw RuntimeException }.runCollect returns Left") {
          val result = Stream.attempt(throw new RuntimeException("boom")).runCollect
          assert(result.isLeft)(isTrue) &&
          assert(result.left.exists(_.isInstanceOf[RuntimeException]))(isTrue)
        },
        test("Stream.suspend produces correct elements") {
          val result = Stream.suspend(Stream.range(0, 3)).runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2))))
        },
        test("Stream.eval executes side-effect and emits nothing") {
          var executed = false
          val result   = Stream.eval { executed = true }.runCollect
          assertTrue(executed) && assert(result)(equalTo(Right(Chunk.empty)))
        },
        test("Stream.eval defect propagates as defect") {
          val result = scala.util.Try(Stream.eval(throw new RuntimeException("boom")).runCollect)
          assertTrue(result.isFailure) &&
          assertTrue(result.failed.get.isInstanceOf[RuntimeException])
        },
        test("Stream.attemptEval captures throwable as typed error") {
          val result = Stream.attemptEval(throw new RuntimeException("oops")).runCollect
          assertTrue(result.isLeft) &&
          assertTrue(result.left.exists(_.getMessage == "oops"))
        },
        test("Stream.attemptEval succeeds with no elements on success") {
          var executed = false
          val result   = Stream.attemptEval { executed = true }.runCollect
          assertTrue(executed) && assert(result)(equalTo(Right(Chunk.empty)))
        },
        test("Stream.defer registers release action on close") {
          var released = false
          val result   = Stream.defer { released = true }.runCollect
          assert(result)(equalTo(Right(Chunk.empty))) && assertTrue(released)
        },
        test("Stream.defer release runs after preceding elements") {
          var released = false
          val result   = (Stream.range(0, 3) ++ Stream.defer { released = true }).runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2)))) && assertTrue(released)
        },
        test("nested defers run in LIFO order") {
          val order  = scala.collection.mutable.ListBuffer.empty[Int]
          val result = (
            Stream.defer(order += 1) ++
              Stream.range(0, 2) ++
              Stream.defer(order += 2)
          ).runCollect
          assert(result)(equalTo(Right(Chunk(0, 1)))) &&
          assert(order.toList)(equalTo(List(1, 2)))
        },
        test("concat of defers have distinct lifetimes") {
          val order  = scala.collection.mutable.ListBuffer.empty[String]
          val stream =
            Stream.defer(order += "a-release") ++
              Stream.succeed(1) ++
              Stream.defer(order += "b-release") ++
              Stream.succeed(2)
          val result = stream.runCollect
          assert(result)(equalTo(Right(Chunk(1, 2)))) &&
          assert(order.toList)(equalTo(List("a-release", "b-release")))
        }
      ),

      // ---- Suite 2: catchAll ----

      suite("catchAll")(
        test("catches error and switches to fallback stream") {
          val result = Stream.fail("e").catchAll(_ => Stream.succeed(1)).runCollect
          assert(result)(equalTo(Right(Chunk(1))))
        },
        test("catches error after some elements") {
          val result = (Stream.range(0, 5) ++ Stream.fail("e")).catchAll(_ => Stream.succeed(99)).runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2, 3, 4, 99))))
        },
        test("fallback can also fail") {
          val result = Stream.fail("e").catchAll((_: String) => Stream.fail("e2")).runCollect
          assert(result)(equalTo(Left("e2")))
        },
        test("does not catch when no error") {
          val result = Stream.succeed(1).catchAll((_: Nothing) => Stream.succeed(2)).runCollect
          assert(result)(equalTo(Right(Chunk(1))))
        },
        test("passes the error value to the handler") {
          val result = Stream.fail("hello").catchAll((e: String) => Stream.succeed(e.length)).runCollect
          assert(result)(equalTo(Right(Chunk(5))))
        }
      ),

      // ---- Suite 3: catchDefect ----

      suite("catchDefect")(
        test("catches RuntimeException from die") {
          val result = Stream
            .die(new RuntimeException("boom"))
            .catchDefect { case _: RuntimeException => Stream.succeed(42) }
            .runCollect
          assert(result)(equalTo(Right(Chunk(42))))
        },
        test("does NOT catch typed StreamError") {
          val result = Stream.fail("typed").catchDefect { case _ => Stream.empty }.runCollect
          assert(result)(equalTo(Left("typed")))
        },
        test("non-matching defect is rethrown") {
          // Use an Exception subclass that doesn't match the PartialFunction
          val t      = new java.io.IOException("io error")
          val result = try {
            Stream.die(t).catchDefect { case _: RuntimeException => Stream.succeed(42) }.runCollect
            "no exception"
          } catch {
            case e: java.io.IOException => e.getMessage
          }
          assert(result)(equalTo("io error"))
        }
      ),

      // ---- Suite 4: orElse / || ----

      suite("orElse / ||")(
        test("fail || succeed produces the succeed") {
          val result = (Stream.fail("e") || Stream.succeed(1)).runCollect
          assert(result)(equalTo(Right(Chunk(1))))
        },
        test("succeed || succeed produces the first") {
          val result = (Stream.succeed(1) || Stream.succeed(2)).runCollect
          assert(result)(equalTo(Right(Chunk(1))))
        },
        test("orElse is the same as ||") {
          val result = Stream.fail("e").orElse(Stream.succeed(99)).runCollect
          assert(result)(equalTo(Right(Chunk(99))))
        }
      ),

      // ---- Suite 5: Error + combinators ----

      suite("Error + combinators")(
        test("error in map, caught by catchAll") {
          val result = Stream
            .range(0, 3)
            .map { (_: Int) => throw new StreamError("e"); 0 }
            .catchAll((_: String) => Stream.succeed(99))
            .runCollect
          assert(result)(equalTo(Right(Chunk(99))))
        },
        test("error in flatMap inner stream, caught by catchAll") {
          val result = Stream
            .range(0, 10)
            .flatMap((i: Int) => if (i == 5) Stream.fail("e") else Stream.succeed(i))
            .catchAll((_: String) => Stream.empty)
            .runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2, 3, 4))))
        },
        test("error in concat, caught by catchAll") {
          val result = (Stream.range(0, 3) ++ Stream.fail("e"))
            .catchAll((_: String) => Stream.range(10, 13))
            .runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2, 10, 11, 12))))
        }
      ),

      // ---- Suite 6: Resource cleanup ----

      suite("Resource cleanup")(
        test("catchAll closes upstream reader on switch") {
          var closed   = false
          val upstream = Stream.fromReader[String, Int](new Reader[Int] {
            private var done                      = false
            def isClosed: Boolean                 = done
            def read[A1 >: Int](sentinel: A1): A1 = { done = true; throw new StreamError("err") }
            def close(): Unit                     = closed = true
          })
          val result = upstream.catchAll((_: String) => Stream.succeed(42)).runCollect
          assert(result)(equalTo(Right(Chunk(42)))) &&
          assert(closed)(isTrue)
        },
        test("ConcatWith closes first reader on switch") {
          var closed = false
          val first  = Stream.fromReader[Nothing, Int](new Reader[Int] {
            private var emitted                   = false
            def isClosed: Boolean                 = emitted
            def read[A1 >: Int](sentinel: A1): A1 = if (!emitted) { emitted = true; Int.box(1).asInstanceOf[A1] }
            else sentinel
            def close(): Unit = closed = true
          })
          val result = (first ++ Stream.succeed(2)).runCollect
          assert(result)(equalTo(Right(Chunk(1, 2)))) &&
          assert(closed)(isTrue)
        },
        test("ensuring calls finalizer on normal completion") {
          var finalized = false
          val result    = Stream.range(0, 3).ensuring { finalized = true }.runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2)))) &&
          assert(finalized)(isTrue)
        },
        test("ensuring calls finalizer on error") {
          var finalized = false
          val result    = Stream.fail("err").ensuring { finalized = true }.runCollect
          assert(result)(equalTo(Left("err"))) &&
          assert(finalized)(isTrue)
        },
        test("fromAcquireRelease calls release on error during use compile") {
          var released = false
          val result   = try {
            Stream
              .fromAcquireRelease("resource", (_: String) => { released = true }) { _ =>
                throw new RuntimeException("compile failed")
                Stream.empty
              }
              .runCollect
            "no exception"
          } catch {
            case _: RuntimeException => "caught"
          }
          assert(result)(equalTo("caught")) &&
          assert(released)(isTrue)
        },
        test("fromAcquireRelease calls release on normal close") {
          var released = false
          val result   = Stream
            .fromAcquireRelease("res", (_: String) => { released = true }) { r =>
              Stream.succeed(r)
            }
            .runCollect
          assert(result)(equalTo(Right(Chunk("res")))) &&
          assert(released)(isTrue)
        }
      ),

      // ---- Suite 7: mapAccum ----

      suite("mapAccum")(
        test("cumulative sum") {
          val result = Stream
            .range(0, 5)
            .mapAccum(0) { (sum, a) =>
              val s = sum + a; (s, s)
            }
            .runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 3, 6, 10))))
        },
        test("running index") {
          val result = Stream
            .fromIterable(List("a", "b", "c"))
            .mapAccum(0)((idx, s) => (idx + 1, s"$idx:$s"))
            .runCollect
          assert(result)(equalTo(Right(Chunk("0:a", "1:b", "2:c"))))
        }
      ),

      // ---- Suite 8: Sink errors ----

      suite("Sink errors")(
        test("Sink.fail(e) produces Left(e)") {
          val result = Stream.succeed(1).run(Sink.fail("e"))
          assert(result)(equalTo(Left("e")))
        },
        test("Sink.mapError transforms the error value") {
          val result = Stream.fail(42).mapError(_.toString).run(Sink.drain)
          assert(result)(equalTo(Left("42")))
        }
      ),

      // ---- Suite 9: fromReader ----

      suite("fromReader")(
        test("fromReader wraps a reader as a stream") {
          val result = Stream.fromReader[Nothing, Int](Reader.fromRange(0 until 3)).runCollect
          assert(result)(equalTo(Right(Chunk(0, 1, 2))))
        }
      ),

      // ---- Error-handling principles ----

      suite("error principles")(
        suite("Sink.mapError carrier split (ClassCastException regression)")(
          test("a stream-origin error is never rewritten by Sink.mapError (no CCE)") {
            // Before the StreamError/SinkError split, the sink's `ErrorMapped`
            // caught the *stream's* `StreamError("stream-err")` and applied its own
            // `Boolean => Int` mapping to a `String`, throwing a ClassCastException.
            // Now the stream error flows past the sink untouched and `run` projects
            // it through the left side of the error `Concat`.
            val sink: Sink[Int, Int, Chunk[Int]] =
              Sink.create[Boolean, Int, Chunk[Int]](r => r.readAll[Int]()).mapError((b: Boolean) => if (b) 1 else 0)
            val stream: Stream[String, Int] = Stream.fail("stream-err")
            val result                      = stream.run(sink).left.map(separateStringInt)
            assert(result)(equalTo(Left(Left("stream-err"))))
          },
          test("a sink-origin error is still transformed by Sink.mapError (disjoint)") {
            val sink: Sink[Int, Int, Nothing] =
              Sink.fail[Boolean](true).mapError((b: Boolean) => if (b) 7 else 0)
            val stream: Stream[String, Int] = Stream(1, 2, 3)
            val result                      = stream.run(sink).left.map(separateStringInt)
            assert(result)(equalTo(Left(Right(7))))
          },
          test("a stream error wins over a sink that would also fail") {
            val sink: Sink[Int, Int, Chunk[Int]] =
              Sink
                .create[Boolean, Int, Chunk[Int]] { r =>
                  r.readAll[Int]() // raises the stream's StreamError before failing itself
                }
                .mapError((b: Boolean) => if (b) 1 else 0)
            val stream: Stream[String, Int] = Stream.fail("stream-wins")
            val result                      = stream.run(sink).left.map(separateStringInt)
            assert(result)(equalTo(Left(Left("stream-wins"))))
          }
        ),
        suite("isCatchable recovery vs control signals")(
          test("catchDefect does not recover from a typed stream error") {
            val stream =
              (Stream.fail("boom"): Stream[String, Int]).catchDefect { case _: Throwable => Stream.succeed(99) }
            assert(stream.runCollect)(equalTo(Left("boom")))
          },
          test("catchDefect recovers from a genuine defect") {
            val stream =
              Stream.die(new RuntimeException("kaboom")).catchDefect { case _: RuntimeException => Stream.succeed(7) }
            assert(stream.runCollect)(equalTo(Right(Chunk(7))))
          },
          test("catchAll recovers from a typed stream error") {
            val stream = (Stream.fail("boom"): Stream[String, Int]).catchAll(_ => Stream.succeed(1))
            assert(stream.runCollect)(equalTo(Right(Chunk(1))))
          }
        ),
        suite("cleanup suppression (no swallowed throwables)")(
          test("a finalizer failure is suppressed onto the primary drain defect") {
            val primary = new RuntimeException("primary-defect")
            val cleanup = new RuntimeException("cleanup-failure")
            val stream  = Stream.die(primary).ensuring(throw cleanup)
            val t       = caught(stream.runDrain)
            assert(t)(equalTo(primary)) &&
            assert(t.getSuppressed.toList)(equalTo(List[Throwable](cleanup)))
          },
          test("a finalizer failure with no primary becomes the propagated error") {
            val cleanup = new RuntimeException("cleanup-only")
            val stream  = (Stream(1, 2, 3): Stream[Nothing, Int]).ensuring(throw cleanup)
            val t       = caught(stream.runDrain)
            assert(t)(equalTo(cleanup)) &&
            assert(t.getSuppressed.toList)(equalTo(Nil))
          },
          test("a clean finalizer runs without error") {
            var ran    = false
            val stream = (Stream(1, 2, 3): Stream[Nothing, Int]).ensuring { ran = true }
            assert(stream.runDrain)(equalTo(Right(()))) &&
            assert(ran)(equalTo(true))
          }
        )
      ),

      // ---- StreamError control semantics ----

      suite("error control")(
        test("StreamError is a control throwable, not a NonFatal Exception") {
          val e: Throwable = new StreamError("boom")
          assert(e.isInstanceOf[ControlThrowable])(isTrue) &&
          assert(e.isInstanceOf[Exception])(isFalse) &&
          assert(NonFatal(e))(isFalse)
        },
        test("scala.util.Try does not swallow a stream error during a read") {
          val attempt = scala.util.Try {
            val sink = Sink.create[Nothing, Int, String] { r =>
              var v = r.read[Any](EndOfStream)
              while (v.asInstanceOf[AnyRef] ne EndOfStream) v = r.read[Any](EndOfStream)
              "consumed"
            }
            failing.run(sink)
          }
          // The stream error is not caught by Try; run turns it into a Left instead.
          assert(attempt.isSuccess)(isTrue) &&
          assert(attempt.get)(equalTo(Left("boom")))
        },
        test("a sink that catches NonFatal does not swallow stream errors") {
          val result = failing.run(sinkCatching { case NonFatal(_) => "fallback" })
          assert(result)(equalTo(Left("boom")))
        },
        test("a sink that catches Exception does not swallow stream errors") {
          val result = failing.run(sinkCatching { case _: Exception => "fallback" })
          assert(result)(equalTo(Left("boom")))
        },
        test("a sink's own NonFatal defect is still recoverable") {
          // A defect raised by the sink's own logic (not a stream error) is NonFatal
          // and may legitimately be recovered by the sink.
          val sink = Sink.create[Nothing, Int, String] { _ =>
            try throw new RuntimeException("sink-defect")
            catch { case NonFatal(_) => "recovered" }
          }
          val result = (Stream(1, 2, 3): Stream[Nothing, Int]).run(sink)
          assert(result)(equalTo(Right("recovered")))
        },
        test("Sink.mapError still transforms a sink-originated error") {
          val sink = Sink.fail[String]("sink").mapError((s: String) => s + "!")
          assert((Stream(1): Stream[Nothing, Int]).run(sink))(equalTo(Left("sink!")))
        },
        // SinkError (carrying sink-origin typed errors) and StreamError (carrying
        // stream-origin typed errors) are both control throwables but are deliberately
        // unrelated sibling types, so that Sink.mapError — which catches SinkError —
        // never rewrites a stream-origin error.
        test("SinkError and StreamError are unrelated control throwables") {
          val se: Throwable = new SinkError("x")
          val st: Throwable = new StreamError("y")
          assert(se.isInstanceOf[ControlThrowable])(isTrue) &&
          assert(se.isInstanceOf[StreamError])(isFalse) &&
          assert(st.isInstanceOf[SinkError])(isFalse) &&
          assert(NonFatal(se))(isFalse) &&
          assert(NonFatal(st))(isFalse)
        },
        // Documents the one remaining boundary: a sink that catches every Throwable
        // (a contract violation) still intercepts the control signal. This is by
        // design — the framework cannot stop a `catch _: Throwable` without paying a
        // per-element cost on the common path; the contract is documented on
        // `Sink.create`.
        test("a sink that catches all Throwable (contract violation) still swallows — documents the boundary") {
          val result = failing.run(sinkCatching { case _: Throwable => "fallback" })
          assert(result)(equalTo(Right("fallback")))
        }
      )
    ),
    suite("regressions")(
      suite("AdversarialConvergenceSweepSpec")(
        // ---- Specialization hazard in pass-through wrappers besides takeWhile ----
        // The BUG-T1 family: any wrapper that applies a user fn on the Byte lane.
        // All Byte-lane combinators route through the boxed/ref path or a Byte-aware
        // cast, so none CCE.
        test("scan_byteLane_collect_noCCE [AdversarialConvergenceSweepSpec]") {
          val s = Stream(1.toByte, 2.toByte, 3.toByte).scan(0.toByte)((a, x) => (a + x).toByte)
          assertTrue(s.runCollect == Right(Chunk[Byte](0, 1, 3, 6)))
        },
        // ---- Long-lane scan accumulator may itself equal the EOF sentinel ----
        // The ACCUMULATOR (not just a source element) hits Long.MaxValue
        // mid-stream; the scan reader's own lastReadWasEOF flag must keep the
        // Long-lane sink from mistaking it for end-of-stream (BUG-004 family,
        // accumulator variant) — convergence evidence.
        test("scan_longLane_accumulatorEqualsSentinel_notTruncated [AdversarialConvergenceSweepSpec]") {
          val s = Stream.fromChunk(Chunk(Long.MaxValue, 0L, 1L)).scan(0L)(_ + _)
          assertTrue(s.runCollect == Right(Chunk(0L, Long.MaxValue, Long.MaxValue, Long.MinValue)))
        },
        test("scan_byteLane_count_noCCE [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream(1.toByte, 2.toByte).scan(0.toByte)((a, x) => (a + x).toByte).count == Right(3L))
        },
        test("mapAccum_byteLane_noCCE [AdversarialConvergenceSweepSpec]") {
          val s =
            Stream(1.toByte, 2.toByte, 3.toByte).mapAccum(0.toByte)((acc, x) => ((acc + x).toByte, (acc + x).toByte))
          assertTrue(s.runCollect == Right(Chunk[Byte](1, 3, 6)))
        },
        test("intersperse_byteLane_noCCE [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream(1.toByte, 2.toByte, 3.toByte).intersperse(0.toByte).runCollect == Right(Chunk[Byte](1, 0, 2, 0, 3))
          )
        },
        test("distinct_byteLane_noCCE [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream(1.toByte, 1.toByte, 2.toByte).distinct.runCollect == Right(Chunk[Byte](1, 2)))
        },
        test("filter_byteLane_count_noCCE [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream(1.toByte, 2.toByte, 3.toByte, 4.toByte).filter(_ % 2 == 0).count == Right(2L))
        },
        test("takeWhile_charLane_noCCE [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream('a', 'b', 'c').takeWhile(_ < 'c').runCollect == Right(Chunk('a', 'b')))
        },
        test("filter_shortLane_noCCE [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream(1.toShort, 2.toShort, 3.toShort).filter(_ > 1.toShort).runCollect == Right(Chunk[Short](2, 3))
          )
        },

        // R6 convergence probe: `chunked` pulls via `readN` on its source; over a
        // recovery wrapper the base generic `readN` must pull element-wise through
        // the recovery-aware `read`, so a mid-chunk upstream failure switches to
        // the recovery stream WITHOUT losing the elements already accumulated in
        // the chunk being built (sibling of BUG-A02, bulk-path-vs-recovery).
        test("chunked_overCatchAllRecovery_groupsAcrossSwitch [AdversarialConvergenceSweepSpec]") {
          val s = ((Stream(1, 2, 3) ++ Stream.fail("boom")).catchAll(_ => Stream(9))).chunked(2)
          assertTrue(s.runCollect == Right(Chunk(Chunk(1, 2), Chunk(3, 9))))
        },

        // ---- Sentinel collision (Long.MaxValue / Double.MaxValue) on combinators
        // NOT covered by AdversarialSentinelSpec ----
        test("filter_longMaxValue_keepsAll [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).filter(_ => true).runCollect == Right(
              Chunk(1L, Long.MaxValue, 3L)
            )
          )
        },
        test("distinct_longMaxValue_preserved [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1L, Long.MaxValue, Long.MaxValue, 3L)).distinct.runCollect == Right(
              Chunk(1L, Long.MaxValue, 3L)
            )
          )
        },
        test("takeWhile_longMaxValue_preserved [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).takeWhile(_ => true).runCollect == Right(
              Chunk(1L, Long.MaxValue, 3L)
            )
          )
        },
        test("drop_longMaxValue_preserved [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).drop(1).runCollect == Right(Chunk(Long.MaxValue, 3L))
          )
        },
        test("scan_longMaxValueState_preserved [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(0L, 0L)).scan(Long.MaxValue)((a, _) => a).runCollect == Right(
              Chunk(Long.MaxValue, Long.MaxValue, Long.MaxValue)
            )
          )
        },
        test("sliding_longMaxValue_preserved [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).sliding(2, 1).runCollect == Right(
              Chunk(Chunk(1L, Long.MaxValue), Chunk(Long.MaxValue, 3L))
            )
          )
        },
        test("sliding_doubleMaxValue_preserved [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1.0, Double.MaxValue, 3.0)).sliding(2, 1).runCollect == Right(
              Chunk(Chunk(1.0, Double.MaxValue), Chunk(Double.MaxValue, 3.0))
            )
          )
        },

        // ---- Cross-lane scan (srcType != outType) ----
        test("scan_intToLong [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream(1, 2, 3).scan(0L)((acc, x) => acc + x).runCollect == Right(Chunk(0L, 1L, 3L, 6L)))
        },
        test("scan_intToLong_thenMapLong [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream(1, 2, 3).scan(0L)((acc, x) => acc + x).map(_ * 2L).runCollect == Right(Chunk(0L, 2L, 6L, 12L))
          )
        },
        test("scan_byteToInt [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream(1.toByte, 2.toByte, 3.toByte).scan(0)((acc, x) => acc + x).runCollect == Right(Chunk(0, 1, 3, 6))
          )
        },

        // ---- Error integrity: a tagged user-fn fault must propagate (defect
        // channel), never be swallowed, on EVERY specialized lane (differential) ----
        test("map_throws_propagates_intLane [AdversarialConvergenceSweepSpec]")(
          assertTrue(threwBoom(Stream(1, 2, 3).map[Int](_ => throw boom).runCollect))
        ),
        test("map_throws_propagates_byteLane [AdversarialConvergenceSweepSpec]") {
          assertTrue(threwBoom(Stream(1.toByte, 2.toByte).map[Byte](_ => throw boom).runCollect))
        },
        test("map_throws_propagates_longLane [AdversarialConvergenceSweepSpec]")(
          assertTrue(threwBoom(Stream(1L, 2L).map[Long](_ => throw boom).runCollect))
        ),
        test("map_throws_propagates_doubleLane [AdversarialConvergenceSweepSpec]") {
          assertTrue(threwBoom(Stream(1.0, 2.0).map[Double](_ => throw boom).runCollect))
        },
        test("filter_throws_propagates [AdversarialConvergenceSweepSpec]")(
          assertTrue(threwBoom(Stream(1, 2, 3).filter(_ => throw boom).runCollect))
        ),
        test("takeWhile_throws_propagates_byteLane [AdversarialConvergenceSweepSpec]") {
          assertTrue(threwBoom(Stream(1.toByte, 2.toByte).takeWhile(_ => throw boom).runCollect))
        },
        test("scan_throws_propagates [AdversarialConvergenceSweepSpec]")(
          assertTrue(threwBoom(Stream(1, 2, 3).scan(0)((_, _) => throw boom).runCollect))
        ),
        test("mapAccum_throws_propagates [AdversarialConvergenceSweepSpec]") {
          assertTrue(threwBoom(Stream(1, 2, 3).mapAccum(0)((_, _) => throw boom).runCollect))
        },
        test("distinctBy_throws_propagates [AdversarialConvergenceSweepSpec]")(
          assertTrue(threwBoom(Stream(1, 2, 3).distinctBy(_ => throw boom).runCollect))
        ),
        test("collect_throws_propagates [AdversarialConvergenceSweepSpec]") {
          assertTrue(threwBoom(Stream(1, 2, 3).collect { case _ => (throw boom): Int }.runCollect))
        },

        // ---- repeated/reset over EVERY stateful combinator: reset must clear state
        // (sibling-uniformity oracle vs map/filter) ----
        test("repeated_scan_resetsState [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream(1, 2).scan(0)(_ + _).repeated.take(6).runCollect == Right(Chunk(0, 1, 3, 0, 1, 3)))
        },
        test("repeated_mapAccum_resetsState [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream(1, 2).mapAccum(0)((acc, x) => (acc + x, acc + x)).repeated.take(4).runCollect == Right(
              Chunk(1, 3, 1, 3)
            )
          )
        },
        test("repeated_intersperse_resetsState [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream(1, 2).intersperse(0).repeated.take(6).runCollect == Right(Chunk(1, 0, 2, 1, 0, 2)))
        },
        test("repeated_distinct_resetsSeen [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream(1, 1, 2).distinct.repeated.take(4).runCollect == Right(Chunk(1, 2, 1, 2)))
        },
        test("repeated_grouped_resets [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream(1, 2, 3).grouped(2).repeated.take(4).runCollect == Right(
              Chunk(Chunk(1, 2), Chunk(3), Chunk(1, 2), Chunk(3))
            )
          )
        },
        test("repeated_sliding_resets [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream(1, 2, 3).sliding(2, 1).repeated.take(4).runCollect == Right(
              Chunk(Chunk(1, 2), Chunk(2, 3), Chunk(1, 2), Chunk(2, 3))
            )
          )
        },

        // ---- skip/take/drop/reset composition ----
        test("dropThenTake_repeated [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream.range(0, 5).drop(1).take(2).repeated.take(5).runCollect == Right(Chunk(1, 2, 1, 2, 1)))
        },
        test("takeThenDrop_repeated [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream.range(0, 5).take(4).drop(1).repeated.take(6).runCollect == Right(Chunk(1, 2, 3, 1, 2, 3)))
        },

        // ---- Deep (> DepthCutoff) segmented-interpreter path per lane ----
        test("deep_byteLane_noCorruption [AdversarialConvergenceSweepSpec]") {
          val s = (0 until 150).foldLeft(Stream(1.toByte, 2.toByte, 3.toByte))((acc, _) => acc.map(x => x))
          assertTrue(s.runCollect == Right(Chunk[Byte](1, 2, 3)))
        },
        test("deep_longLane [AdversarialConvergenceSweepSpec]") {
          val s = (0 until 150).foldLeft(Stream(1L, 2L, 3L))((acc, _) => acc.map(_ + 1L))
          assertTrue(s.runCollect == Right(Chunk(151L, 152L, 153L)))
        },
        test("deep_takeWhile_byteLane [AdversarialConvergenceSweepSpec]") {
          val s = (0 until 150).foldLeft(Stream(1.toByte, 2.toByte, 3.toByte, 4.toByte).takeWhile(_ < 4))((acc, _) =>
            acc.map(x => x)
          )
          assertTrue(s.runCollect == Right(Chunk[Byte](1, 2, 3)))
        },

        // ---- Algebraic laws ----
        test("flatMap_leftIdentity [AdversarialConvergenceSweepSpec]") {
          val f: Int => Stream[Nothing, Int] = x => Stream(x, x * 10)
          assertTrue(Stream(5).flatMap(f).runCollect == f(5).runCollect)
        },
        test("flatMap_associativity_mixedLanes [AdversarialConvergenceSweepSpec]") {
          val l = Stream(1, 2).flatMap(x => Stream(x, x + 1).flatMap(y => Stream(y.toByte))).runCollect
          val r = Stream(1, 2).flatMap(x => Stream(x, x + 1)).flatMap(y => Stream(y.toByte)).runCollect
          assertTrue(l == r)
        },
        test("flatMap_emptyInnersInterleaved [AdversarialConvergenceSweepSpec]") {
          val s = Stream(1, 2, 3, 4).flatMap(x => if (x % 2 == 0) Stream(x) else (Stream.empty: Stream[Nothing, Int]))
          assertTrue(s.runCollect == Right(Chunk(2, 4)))
        },
        test("concat_associativity [AdversarialConvergenceSweepSpec]") {
          val l = ((Stream(1, 2) ++ Stream(3, 4)) ++ Stream(5)).runCollect
          val r = (Stream(1, 2) ++ (Stream(3, 4) ++ Stream(5))).runCollect
          assertTrue(l == r && l == Right(Chunk(1, 2, 3, 4, 5)))
        },
        test("map_composition [AdversarialConvergenceSweepSpec]") {
          val l = Stream(1, 2, 3).map(_ + 1).map(_ * 2).runCollect
          val r = Stream(1, 2, 3).map(x => (x + 1) * 2).runCollect
          assertTrue(l == r)
        },
        test("distinct_idempotent [AdversarialConvergenceSweepSpec]") {
          assertTrue(
            Stream(1, 1, 2, 3, 3, 2).distinct.distinct.runCollect == Stream(1, 1, 2, 3, 3, 2).distinct.runCollect
          )
        },
        test("distinct_NaN_matchesScalaListSemantics [AdversarialConvergenceSweepSpec]") {
          // Differential oracle: distinct uses Scala number equality (NaN != NaN),
          // so two NaNs are NOT collapsed — exactly like List(NaN,NaN,1.0).distinct.
          val viaStream = Stream.fromChunk(Chunk(Double.NaN, Double.NaN, 1.0)).distinct.runCollect.toOption.get.length
          val viaList   = List(Double.NaN, Double.NaN, 1.0).distinct.length
          assertTrue(viaStream == viaList)
        },
        test("zip_unequalLengths_shorterWins [AdversarialConvergenceSweepSpec]") {
          assertTrue((Stream(1, 2, 3) && Stream(10, 20)).runCollect == Right(Chunk((1, 10), (2, 20))))
        },

        // ---- Boundary requires / degenerate args ----
        test("grouped_zero_throwsIAE [AdversarialConvergenceSweepSpec]")(assertTrue(threwIAE(Stream(1, 2).grouped(0)))),
        test("sliding_zeroN_throwsIAE [AdversarialConvergenceSweepSpec]")(
          assertTrue(threwIAE(Stream(1, 2).sliding(0, 1)))
        ),
        test("sliding_zeroStep_throwsIAE [AdversarialConvergenceSweepSpec]")(
          assertTrue(threwIAE(Stream(1, 2).sliding(1, 0)))
        ),
        test("take_negative_isEmpty [AdversarialConvergenceSweepSpec]")(
          assertTrue(Stream(1, 2, 3).take(-1).runCollect == Right(Chunk.empty[Int]))
        ),
        test("drop_negative_isIdentity [AdversarialConvergenceSweepSpec]")(
          assertTrue(Stream(1, 2, 3).drop(-1).runCollect == Right(Chunk(1, 2, 3)))
        ),
        test("sliding_stepGreaterThanN [AdversarialConvergenceSweepSpec]") {
          assertTrue(Stream.range(0, 7).sliding(2, 3).runCollect == Right(Chunk(Chunk(0, 1), Chunk(3, 4), Chunk(6))))
        },

        // ---- Pipeline composition ----
        test("pipeline_map_andThen_filter [AdversarialConvergenceSweepSpec]") {
          val pipe = Pipeline.map[Int, Int](_ + 1).andThen(Pipeline.filter[Int](_ % 2 == 0))
          assertTrue(Stream.range(0, 6).via(pipe).runCollect == Right(Chunk(2, 4, 6)))
        },
        test("pipeline_drop_andThen_take [AdversarialConvergenceSweepSpec]") {
          val pipe = Pipeline.drop[Int](1).andThen(Pipeline.take[Int](2))
          assertTrue(Stream.range(0, 10).via(pipe).runCollect == Right(Chunk(1, 2)))
        },

        // ---- Writer: concat, contramap, limited(0), double-close ----
        test("writer_limited_concat_switchesAtBoundary [AdversarialConvergenceSweepSpec]") {
          val sb                             = new StringBuilder
          def w(tag: String): io.Writer[Int] = new io.Writer[Int] {
            private var closed = false
            def isClosed       = closed
            def write(a: Int)  = if (closed) false else { sb.append(s"$tag$a;"); true }
            def close()        = closed = true
          }
          val c = io.Writer.limited(w("A"), 2).concat(w("B"))
          c.write(1); c.write(2); c.write(3); c.write(4); c.close()
          assertTrue(sb.toString == "A1;A2;B3;B4;")
        },
        test("writer_limited_zero_isClosed [AdversarialConvergenceSweepSpec]") {
          val w0 = io.Writer.limited(io.Writer.single[Int], 0)
          assertTrue(w0.isClosed && !w0.write(1))
        },
        test("writer_contramap_transformsInput [AdversarialConvergenceSweepSpec]") {
          val sb                = new StringBuilder
          val w: io.Writer[Int] = new io.Writer[Int] {
            private var closed = false
            def isClosed       = closed
            def write(a: Int)  = { sb.append(a); sb.append(';'); true }
            def close()        = closed = true
          }
          val cw = w.contramap[String](_.length)
          cw.write("ab"); cw.write("abcd")
          assertTrue(sb.toString == "2;4;")
        }
      ),
      suite("AdversarialIter9Spec")(
        // ---- Pipeline applied to Sink (runViaSink) over flatMapped source ------
        test("pipelineTake_andThenSink_overFlatMap_matchesViaStream [AdversarialIter9Spec]") {
          val data = Stream.range(0, 6).flatMap(n => Stream(n, n * 10))
          // [0,0,1,10,2,20,3,30,4,40,5,50]
          val pipe = Pipeline.take[Int](5)
          val r1   = data.run(pipe andThenSink Sink.collectAll[Int])
          val r2   = data.via(pipe).run(Sink.collectAll[Int])
          assertTrue(r1 == r2) && assertTrue(r1 == Right(Chunk(0, 0, 1, 10, 2)))
        },
        test("pipelineDrop_andThenSink_overFlatMap_matchesViaStream [AdversarialIter9Spec]") {
          val data = Stream.range(0, 6).flatMap(n => Stream(n, n * 10))
          val pipe = Pipeline.drop[Int](3)
          val r1   = data.run(pipe andThenSink Sink.collectAll[Int])
          val r2   = data.via(pipe).run(Sink.collectAll[Int])
          assertTrue(r1 == r2) && assertTrue(r1 == Right(Chunk(10, 2, 20, 3, 30, 4, 40, 5, 50)))
        },
        test("pipelineCollect_andThenSink_overZip_matchesViaStream [AdversarialIter9Spec]") {
          val data = Stream.range(0, 6) && Stream.range(100, 110) // [(0,100)..(5,105)]
          val pipe = Pipeline.collect[(Int, Int), Int] { case (a, b) if a % 2 == 0 => a + b }
          val r1   = data.run(pipe andThenSink Sink.collectAll[Int])
          val r2   = data.via(pipe).run(Sink.collectAll[Int])
          assertTrue(r1 == r2) && assertTrue(r1 == Right(Chunk(100, 104, 108)))
        },
        test("composedPipeline_mapThenTake_andThenSink_matchesViaStream [AdversarialIter9Spec]") {
          val data = Stream.range(0, 10).flatMap(n => Stream(n, n))
          val pipe = Pipeline.map[Int, Int](_ + 1) andThen Pipeline.take[Int](4)
          val r1   = data.run(pipe andThenSink Sink.collectAll[Int])
          val r2   = data.via(pipe).run(Sink.collectAll[Int])
          assertTrue(r1 == r2) && assertTrue(r1 == Right(Chunk(1, 1, 2, 2)))
        },
        test("composedPipeline_takeThenDrop_andThenSink_orderPreserved [AdversarialIter9Spec]") {
          val data = Stream.range(0, 20)
          // take(10) then drop(3) keeps [3,10)
          val pipe = Pipeline.take[Int](10) andThen Pipeline.drop[Int](3)
          val r1   = data.run(pipe andThenSink Sink.collectAll[Int])
          val r2   = data.via(pipe).run(Sink.collectAll[Int])
          assertTrue(r1 == r2) && assertTrue(r1 == Right(Chunk(3, 4, 5, 6, 7, 8, 9)))
        },
        test("pipelineTake_andThenSink_partialSink_head [AdversarialIter9Spec]") {
          val data = Stream.range(0, 100).flatMap(n => Stream(n, -n))
          val pipe = Pipeline.take[Int](5)
          val r    = data.run(pipe andThenSink Sink.head[Int])
          assertTrue(r == Right(Some(0)))
        },

        // ---- Sink contramap over a deep (interpreter) stream ------------------
        test("sinkContramap_overDeepInterpreterStream_matchesShallow [AdversarialIter9Spec]") {
          val deep    = deepIdentity(Stream.range(0, 8), 150) // forces interpreter
          val shallow = Stream.range(0, 8)
          val sink    = Sink.collectAll[Int].contramap[Int](_ * 2)
          assertTrue(deep.run(sink) == shallow.run(sink)) &&
          assertTrue(deep.run(sink) == Right(Chunk(0, 2, 4, 6, 8, 10, 12, 14)))
        },
        test("sinkContramapContramap_overDeepInterpreterStream [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(0, 5), 150)
          // consume A2=Int; g2: +1 ; g1: *10 ; element e -> g1(g2(e)) = (e+1)*10
          val sink = Sink.collectAll[Int].contramap[Int](_ * 10).contramap[Int](_ + 1)
          assertTrue(deep.run(sink) == Right(Chunk(10, 20, 30, 40, 50)))
        },
        test("sinkContramap_toPrimitiveSink_overDeepInterpreter [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(1, 5), 150) // [1,2,3,4]
          val sink = Sink.sumInt.contramap[Int](_ * 2)     // sum of [2,4,6,8] = 20
          assertTrue(deep.run(sink) == Right(20L))
        },

        // ---- flatMap where the FUNCTION throws --------------------------------
        test("flatMap_functionThrows_propagatesAndClosesSource [AdversarialIter9Spec]") {
          val closes                    = new AtomicInteger(0)
          val boom                      = new RuntimeException("fn-boom")
          val src: Stream[Nothing, Int] =
            Stream.fromAcquireRelease((), (_: Unit) => { closes.incrementAndGet(); () })(_ => Stream.range(0, 5))
          val s = src.flatMap[Nothing, Nothing, Int] { n =>
            if (n == 2) throw boom else Stream(n)
          }
          val res = Try(s.runCollect)
          assertTrue(res == scala.util.Failure(boom)) && assertTrue(closes.get() == 1)
        },
        test("flatMap_functionThrows_deepInterpreter_closesSource [AdversarialIter9Spec]") {
          val closes                     = new AtomicInteger(0)
          val boom                       = new RuntimeException("fn-boom-deep")
          val base: Stream[Nothing, Int] =
            Stream.fromAcquireRelease((), (_: Unit) => { closes.incrementAndGet(); () })(_ => Stream.range(0, 5))
          val deep = deepIdentity(base, 150)
          val s    = deep.flatMap[Nothing, Nothing, Int](n => if (n == 3) throw boom else Stream(n))
          val res  = Try(s.runCollect)
          assertTrue(res == scala.util.Failure(boom)) && assertTrue(closes.get() == 1)
        },

        // ---- zero / negative count args across paths --------------------------
        test("take0_overFlatMap [AdversarialIter9Spec]") {
          val s = Stream.range(0, 5).flatMap(n => Stream(n, n)).take(0)
          assertTrue(s.runCollect == Right(Chunk.empty[Int]))
        },
        test("takeNeg_overFlatMap [AdversarialIter9Spec]") {
          val s = Stream.range(0, 5).flatMap(n => Stream(n, n)).take(-1)
          assertTrue(s.runCollect == Right(Chunk.empty[Int]))
        },
        test("dropNeg_keepsAll [AdversarialIter9Spec]") {
          val s = Stream.range(0, 5).drop(-3)
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 3, 4)))
        },
        test("take0_deepInterpreter [AdversarialIter9Spec]") {
          val s = deepIdentity(Stream.range(0, 5), 150).take(0)
          assertTrue(s.runCollect == Right(Chunk.empty[Int]))
        },
        test("takeNeg_deepInterpreter [AdversarialIter9Spec]") {
          val s = deepIdentity(Stream.range(0, 5), 150).take(-5)
          assertTrue(s.runCollect == Right(Chunk.empty[Int]))
        },
        test("dropNeg_deepInterpreter [AdversarialIter9Spec]") {
          val s = deepIdentity(Stream.range(0, 5), 150).drop(-2)
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 3, 4)))
        },

        // ---- knownLength exact invariant under more compositions --------------
        test("knownLength_exactValue_extendedCompositions [AdversarialIter9Spec]") {
          def check(s: Stream[Nothing, Int]): Boolean =
            s.knownLength match {
              case Some(n) => s.runCollect.map(_.length.toLong) == Right(n)
              case None    => true
            }
          val cases = List(
            Stream.range(0, 10).take(Long.MaxValue),
            Stream.range(0, 10).drop(Long.MaxValue),
            Stream.range(0, 10).take(-1),
            Stream.range(0, 10).drop(-1),
            Stream.range(0, 10).take(0),
            (Stream.range(0, 5) ++ Stream.range(0, 5)).take(Long.MaxValue),
            Stream.range(0, 10).map(_ + 1).map(_ * 2).drop(2).take(3),
            deepIdentity(Stream.range(0, 10), 150).take(4),
            deepIdentity(Stream.range(0, 10), 150).drop(7)
          )
          assertTrue(cases.forall(check))
        },

        // ---- reentrancy: map fn runs another stream ---------------------------
        test("map_reentrant_innerRun_isSafe [AdversarialIter9Spec]") {
          val s = Stream.range(0, 4).map { n =>
            val inner = Stream.range(0, n).runFold(0)(_ + _).fold(_ => -1, identity)
            n * 100 + inner
          }
          // inner = sum(0..n-1): n=0->0, n=1->0, n=2->1, n=3->3
          assertTrue(s.runCollect == Right(Chunk(0, 100, 201, 303)))
        },

        // ---- flatMap inner stream EMPTY vs source via Pipeline-sink -----------
        test("pipelineTake_overFlatMapAllEmpty_isEmpty [AdversarialIter9Spec]") {
          val data = Stream.range(0, 100).flatMap(_ => Stream.empty: Stream[Nothing, Int])
          val pipe = Pipeline.take[Int](5)
          val r    = data.run(pipe andThenSink Sink.collectAll[Int])
          assertTrue(r == Right(Chunk.empty[Int]))
        },

        // ---- deep interpreter path: filter + take fallback (allOpsAreMaps=false)
        test("deepInterpreter_filterThenTake_fallbackWindow [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(0, 50), 150).filter(_ % 3 == 0).take(4)
          // multiples of 3 in [0,50): 0,3,6,9,... take 4 -> 0,3,6,9
          assertTrue(deep.runCollect == Right(Chunk(0, 3, 6, 9)))
        },
        test("deepInterpreter_dropThenTake_orderPreserved [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(0, 50), 150).drop(5).take(4)
          assertTrue(deep.runCollect == Right(Chunk(5, 6, 7, 8)))
        },
        test("deepInterpreter_takeThenDrop_orderPreserved [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(0, 50), 150).take(10).drop(7)
          assertTrue(deep.runCollect == Right(Chunk(7, 8, 9)))
        },

        // ---- type-changing flatMap (Int -> String) then map over interpreter --
        test("typeChangingFlatMap_deepThenMap [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(0, 3), 150)
          val s    = deep.flatMap(n => Stream(s"a$n", s"b$n")).map(_.toUpperCase)
          assertTrue(s.runCollect == Right(Chunk("A0", "B0", "A1", "B1", "A2", "B2")))
        },

        // ---- zip where one side is deep interpreter, lanes differ -------------
        test("zip_deepIntLeft_longRight [AdversarialIter9Spec]") {
          val left  = deepIdentity(Stream.range(0, 5), 150)
          val right = Stream(10L, 20L, 30L)
          assertTrue((left && right).runCollect == Right(Chunk((0, 10L), (1, 20L), (2, 30L))))
        },
        test("zip_deepBothSides_take [AdversarialIter9Spec]") {
          val left  = deepIdentity(Stream.range(0, 10), 120)
          val right = deepIdentity(Stream.range(100, 104), 120)
          assertTrue((left && right).runCollect == Right(Chunk((0, 100), (1, 101), (2, 102), (3, 103))))
        },

        // ---- concat where segments are deep interpreters ----------------------
        test("concat_deepSegments_repeatedTake [AdversarialIter9Spec]") {
          val a = deepIdentity(Stream.range(0, 3), 120)
          val b = deepIdentity(Stream.range(10, 12), 120)
          val s = (a ++ b).repeated.take(8)
          // [0,1,2,10,11] repeated; take 8 -> 0,1,2,10,11,0,1,2
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 10, 11, 0, 1, 2)))
        },

        // ---- scan / distinct over deep interpreter ----------------------------
        test("scan_overDeepInterpreter [AdversarialIter9Spec]") {
          val s = deepIdentity(Stream.range(1, 5), 150).scan(0)(_ + _)
          // scan 0 over [1,2,3,4] = [0,1,3,6,10]
          assertTrue(s.runCollect == Right(Chunk(0, 1, 3, 6, 10)))
        },
        test("distinct_overDeepInterpreter_repeated [AdversarialIter9Spec]") {
          val s = deepIdentity(Stream(1, 1, 2, 3, 3), 150).distinct.repeated.take(7)
          assertTrue(s.runCollect == Right(Chunk(1, 2, 3, 1, 2, 3, 1)))
        },

        // ---- flatMap with a DEEP inner stream ---------------------------------
        test("flatMap_deepInnerStream [AdversarialIter9Spec]") {
          val s = Stream.range(0, 3).flatMap(n => deepIdentity(Stream(n, n * 10), 150))
          assertTrue(s.runCollect == Right(Chunk(0, 0, 1, 10, 2, 20)))
        },

        // ---- Sink.take(0) and take over interpreter primitive lane ------------
        test("sinkTake0_overDeepInterpreter [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(0, 5), 150)
          assertTrue(deep.run(Sink.take[Int](0)) == Right(Chunk.empty[Int]))
        },
        test("sinkTakeNeg_overDeepInterpreter [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(0, 5), 150)
          assertTrue(deep.run(Sink.take[Int](-3)) == Right(Chunk.empty[Int]))
        },

        // ---- grouped over deep interpreter (source.jvmType from interpreter) --
        test("grouped_overDeepInterpreter [AdversarialIter9Spec]") {
          val s = deepIdentity(Stream.range(0, 7), 150).grouped(3)
          assertTrue(s.runCollect == Right(Chunk(Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6))))
        },
        test("sliding_overDeepInterpreter [AdversarialIter9Spec]") {
          val s = deepIdentity(Stream.range(0, 5), 150).sliding(2, 1)
          assertTrue(s.runCollect == Right(Chunk(Chunk(0, 1), Chunk(1, 2), Chunk(2, 3), Chunk(3, 4))))
        },

        // ---- flatMap to Boolean (laneOf=I but boxed type Boolean) -------------
        // map uses outLaneOf (Boolean->R) but flatMap's addPush uses laneOf
        // (Boolean->I). Deep (interpreter) flatMap producing Boolean must still
        // yield Boolean elements, not Integer 0/1.
        test("flatMapToBoolean_deepInterpreter_matchesShallow [AdversarialIter9Spec]") {
          val shallow  = Stream.range(0, 3).flatMap(_ => Stream(true, false))
          val deep     = deepIdentity(Stream.range(0, 3), 150).flatMap(_ => Stream(true, false))
          val expected = Right(Chunk(true, false, true, false, true, false))
          assertTrue(shallow.runCollect == expected) &&
          assertTrue(deep.runCollect == expected) &&
          assertTrue(deep.runCollect == shallow.runCollect)
        },
        test("flatMapToBoolean_deepInterpreter_elementsAreBooleans [AdversarialIter9Spec]") {
          val deep = deepIdentity(Stream.range(0, 2), 150).flatMap(_ => Stream(true, false))
          val got  = deep.runCollect.fold(_ => Chunk.empty[Any], identity)
          // every emitted element must be a java.lang.Boolean, never an Integer
          assertTrue(got.forall(_.isInstanceOf[Boolean])) &&
          assertTrue(got == Chunk(true, false, true, false))
        },

        // ---- deep map-chain THROUGH Boolean lane ------------------------------
        // map uses outLaneOf(Boolean)=R for storage but laneOf(Boolean)=I for the
        // next op's input lane; a deep chain that passes a Boolean from one map to
        // the next must round-trip the value, not read a stale lane.
        test("deepMapChain_throughBoolean_matchesShallow [AdversarialIter9Spec]") {
          def boolChain(s: Stream[Nothing, Int]): Stream[Nothing, Boolean] = {
            var acc: Stream[Nothing, Boolean] = s.map(_ % 2 == 0)
            var i                             = 0
            while (i < 150) { acc = acc.map(b => !b); i += 1 } // even count of negations => identity
            acc
          }
          val shallow = {
            var acc: Stream[Nothing, Boolean] = Stream.range(0, 4).map(_ % 2 == 0)
            acc = acc.map(b => !b).map(b => !b)
            acc
          }
          val expected = Right(Chunk(true, false, true, false)) // 0,2 even
          assertTrue(shallow.runCollect == expected) &&
          assertTrue(boolChain(Stream.range(0, 4)).runCollect == expected)
        },
        test("deepMapChain_intToBooleanToInt_matchesShallow [AdversarialIter9Spec]") {
          // Int -> Boolean -> Int round trip through a deep interpreter chain.
          var acc: Stream[Nothing, Boolean] = Stream.range(0, 4).map(_ % 2 == 0)
          var i                             = 0
          while (i < 150) { acc = acc.map(identity[Boolean]); i += 1 }
          val s = acc.map(b => if (b) 1 else 0)
          assertTrue(s.runCollect == Right(Chunk(1, 0, 1, 0)))
        }
      ),
      suite("AdversarialProbeBatterySpec")(
        // ---- Short output (shallow) ------------------------------------------
        test("short_map_collect [AdversarialProbeBatterySpec]") {
          assertTrue(Stream.range(0, 5).map(_.toShort).runCollect == Right(Chunk[Short](0, 1, 2, 3, 4)))
        },
        test("short_map_flatMapInner_collect [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 3).flatMap(i => Stream.range(0, 2).map(j => (i * 2 + j).toShort))
          assertTrue(s.runCollect == Right(Chunk[Short](0, 1, 2, 3, 4, 5)))
        },
        test("short_map_concat_collect [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 3).map(_.toShort) ++ Stream.range(3, 6).map(_.toShort)
          assertTrue(s.runCollect == Right(Chunk[Short](0, 1, 2, 3, 4, 5)))
        },
        test("short_map_take_collect [AdversarialProbeBatterySpec]") {
          assertTrue(Stream.range(0, 9).map(_.toShort).take(4).runCollect == Right(Chunk[Short](0, 1, 2, 3)))
        },
        test("short_map_repeated_take_collect [AdversarialProbeBatterySpec]") {
          assertTrue(
            Stream.range(0, 3).map(_.toShort).repeated.take(7).runCollect == Right(Chunk[Short](0, 1, 2, 0, 1, 2, 0))
          )
        },
        test("short_map_count [AdversarialProbeBatterySpec]") {
          assertTrue(Stream.range(0, 5).map(_.toShort).count == Right(5L))
        },
        test("short_map_fold [AdversarialProbeBatterySpec]") {
          assertTrue(Stream.range(0, 5).map(_.toShort).runFold(0)((a, s) => a + s.toInt) == Right(10))
        },
        // ---- Short output (deep / interpreter) -------------------------------
        test("short_map_deep_collect [AdversarialProbeBatterySpec]") {
          assertTrue(deep(Stream.range(0, 5)).map(_.toShort).runCollect == Right(Chunk[Short](0, 1, 2, 3, 4)))
        },
        test("short_map_deep_flatMapInner_collect [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 3).flatMap(i => deep(Stream.range(0, 2)).map(j => (i * 2 + j).toShort))
          assertTrue(s.runCollect == Right(Chunk[Short](0, 1, 2, 3, 4, 5)))
        },
        test("short_map_deep_concat_collect [AdversarialProbeBatterySpec]") {
          val s = deep(Stream.range(0, 3)).map(_.toShort) ++ deep(Stream.range(3, 6)).map(_.toShort)
          assertTrue(s.runCollect == Right(Chunk[Short](0, 1, 2, 3, 4, 5)))
        },
        // ---- Char output -----------------------------------------------------
        test("char_map_collect [AdversarialProbeBatterySpec]") {
          assertTrue(Stream.range(0, 3).map(i => ('a' + i).toChar).runCollect == Right(Chunk('a', 'b', 'c')))
        },
        test("char_map_flatMapInner_collect [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 2).flatMap(i => Stream.range(0, 2).map(j => ('a' + i * 2 + j).toChar))
          assertTrue(s.runCollect == Right(Chunk('a', 'b', 'c', 'd')))
        },
        test("char_map_concat_collect [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 2).map(i => ('a' + i).toChar) ++ Stream.range(2, 4).map(i => ('a' + i).toChar)
          assertTrue(s.runCollect == Right(Chunk('a', 'b', 'c', 'd')))
        },
        test("char_map_deep_collect [AdversarialProbeBatterySpec]") {
          assertTrue(deep(Stream.range(0, 3)).map(i => ('a' + i).toChar).runCollect == Right(Chunk('a', 'b', 'c')))
        },
        // ---- Long/Float/Double flatMap-inner over deep (register) child ------
        test("long_deep_flatMapInner_collect [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 3).flatMap(i => deep(Stream.range(0, 2)).map(j => (i * 2 + j).toLong))
          assertTrue(s.runCollect == Right(Chunk(0L, 1L, 2L, 3L, 4L, 5L)))
        },
        test("float_deep_flatMapInner_collect [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 3).flatMap(i => deep(Stream.range(0, 2)).map(j => (i * 2 + j).toFloat))
          assertTrue(s.runCollect == Right(Chunk(0f, 1f, 2f, 3f, 4f, 5f)))
        },
        test("double_deep_flatMapInner_collect [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 3).flatMap(i => deep(Stream.range(0, 2)).map(j => (i * 2 + j).toDouble))
          assertTrue(s.runCollect == Right(Chunk(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)))
        },
        test("long_deep_flatMapInner_sum [AdversarialProbeBatterySpec]") {
          val s = Stream.range(0, 3).flatMap(i => deep(Stream.range(0, 2)).map(j => (i * 2 + j).toLong))
          assertTrue(s.run(Sink.sumLong) == Right(15L))
        },
        // ---- shallow-vs-deep differential for stateful combinators -----------
        test("scan_deep_eq_shallow [AdversarialProbeBatterySpec]") {
          val shallow = Stream.range(1, 6).scan(0)(_ + _).runCollect
          val deepR   = deep(Stream.range(1, 6)).scan(0)(_ + _).runCollect
          assertTrue(shallow == Right(Chunk(0, 1, 3, 6, 10, 15))) && assertTrue(deepR == shallow)
        },
        test("mapAccum_deep_eq_shallow [AdversarialProbeBatterySpec]") {
          val f       = (s: Int, x: Int) => (s + x, s + x)
          val shallow = Stream.range(1, 6).mapAccum(0)(f).runCollect
          val deepR   = deep(Stream.range(1, 6)).mapAccum(0)(f).runCollect
          assertTrue(shallow == Right(Chunk(1, 3, 6, 10, 15))) && assertTrue(deepR == shallow)
        },
        test("intersperse_deep_eq_shallow [AdversarialProbeBatterySpec]") {
          val shallow = Stream.range(1, 4).intersperse(0).runCollect
          val deepR   = deep(Stream.range(1, 4)).intersperse(0).runCollect
          assertTrue(shallow == Right(Chunk(1, 0, 2, 0, 3))) && assertTrue(deepR == shallow)
        },
        test("grouped_deep_eq_shallow [AdversarialProbeBatterySpec]") {
          val shallow = Stream.range(0, 7).grouped(3).runCollect
          val deepR   = deep(Stream.range(0, 7)).grouped(3).runCollect
          assertTrue(shallow == Right(Chunk(Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6)))) && assertTrue(deepR == shallow)
        },
        test("sliding_deep_eq_shallow [AdversarialProbeBatterySpec]") {
          val shallow = Stream.range(0, 5).sliding(3, 2).runCollect
          val deepR   = deep(Stream.range(0, 5)).sliding(3, 2).runCollect
          assertTrue(shallow == Right(Chunk(Chunk(0, 1, 2), Chunk(2, 3, 4)))) && assertTrue(deepR == shallow)
        },
        // ---- Pipeline applyToSink surfaces -----------------------------------
        test("pipeline_map_filter_applyToSink_int [AdversarialProbeBatterySpec]") {
          val pipe = Pipeline.map[Int, Int](_ * 2).andThen(Pipeline.filter[Int](_ % 3 == 0))
          val sink = pipe.andThenSink(Sink.collectAll[Int])
          assertTrue(Stream.range(0, 6).run(sink) == Right(Chunk(0, 6)))
        },
        test("pipeline_take_drop_applyToSink [AdversarialProbeBatterySpec]") {
          val pipe = Pipeline.drop[Int](2).andThen(Pipeline.take[Int](2))
          val sink = pipe.andThenSink(Sink.collectAll[Int])
          assertTrue(Stream.range(0, 10).run(sink) == Right(Chunk(2, 3)))
        },
        test("pipeline_map_applyToSink_deep [AdversarialProbeBatterySpec]") {
          val pipe = Pipeline.map[Int, Long](_.toLong + 1L)
          val sink = pipe.andThenSink(Sink.sumLong)
          assertTrue(deep(Stream.range(0, 4)).run(sink) == Right(0L + 1 + 2 + 3 + 4))
        },
        // ---- zip unequal lengths value correctness ---------------------------
        test("zip_unequalLeftLonger [AdversarialProbeBatterySpec]") {
          val z = Stream.range(0, 5) && Stream.range(10, 12)
          assertTrue(z.runCollect == Right(Chunk((0, 10), (1, 11))))
        },
        test("zip_unequalRightLonger [AdversarialProbeBatterySpec]") {
          val z = Stream.range(0, 2) && Stream.range(10, 15)
          assertTrue(z.runCollect == Right(Chunk((0, 10), (1, 11))))
        }
      ),
      suite("AdversarialCombinatorLawConvergenceSpec")(
        test("sliding_overlap_trailingPartial_int [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 6).sliding(3, 2).runCollect.map(_.map(_.toList).toList)
          val exp = (1 to 5).sliding(3, 2).toList.map(_.toList)
          assertTrue(got == Right(exp))
        },
        test("sliding_overlap_trailingPartial_int2 [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 8).sliding(3, 2).runCollect.map(_.map(_.toList).toList)
          val exp = (1 to 7).sliding(3, 2).toList.map(_.toList)
          assertTrue(got == Right(exp))
        },
        test("sliding_overlap_n4_step2 [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 6).sliding(4, 2).runCollect.map(_.map(_.toList).toList)
          val exp = (1 to 5).sliding(4, 2).toList.map(_.toList)
          assertTrue(got == Right(exp))
        },
        test("sliding_generic_trailingPartial [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream("a", "b", "c", "d", "e").sliding(3, 2).runCollect.map(_.map(_.toList).toList)
          val exp = List("a", "b", "c", "d", "e").sliding(3, 2).toList.map(_.toList)
          assertTrue(got == Right(exp))
        },
        test("chunked_vs_grouped [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(0, 10).chunked(3).runCollect.map(_.map(_.toList).toList)
          val exp = (0 until 10).grouped(3).toList.map(_.toList)
          assertTrue(got == Right(exp))
        },
        test("scan_int_vs_scanLeft [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 5).scan(0)(_ + _).runCollect.map(_.toList)
          val exp = (1 until 5).scanLeft(0)(_ + _).toList
          assertTrue(got == Right(exp))
        },
        test("scan_long_maxvalue [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(Long.MaxValue - 1L, 1L).scan(0L)(_ + _).runCollect
          assertTrue(got == Right(Chunk(0L, Long.MaxValue - 1L, Long.MaxValue)))
        },
        test("mapAccum_vs_manual [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 5).mapAccum(0)((s, a) => (s + a, s + a)).runCollect.map(_.toList)
          assertTrue(got == Right(List(1, 3, 6, 10)))
        },
        test("distinct_order [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(3, 1, 3, 2, 1).distinct.runCollect
          assertTrue(got == Right(Chunk(3, 1, 2)))
        },
        test("distinctBy [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1, 2, 3, 4).distinctBy(_ % 2).runCollect
          assertTrue(got == Right(Chunk(1, 2)))
        },
        test("intersperse_long [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1L, 2L, 3L).intersperse(0L).runCollect
          assertTrue(got == Right(Chunk(1L, 0L, 2L, 0L, 3L)))
        },
        test("intersperse_double_maxvalue [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1.0, Double.MaxValue).intersperse(0.0).runCollect
          assertTrue(got == Right(Chunk(1.0, 0.0, Double.MaxValue)))
        },
        test("flatMap_law [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1, 2, 3).flatMap(x => Stream(x, x * 10)).runCollect
          assertTrue(got == Right(Chunk(1, 10, 2, 20, 3, 30)))
        },
        test("flatMap_empty_inner [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1, 2, 3).flatMap(_ => (Stream.empty: Stream[Nothing, Int])).runCollect
          assertTrue(got == Right(Chunk.empty[Int]))
        },
        test("take_then_drop_native [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(0, 10).take(5).drop(3).runCollect
          assertTrue(got == Right(Chunk(3, 4)))
        },
        test("drop_then_take_native [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(0, 10).drop(3).take(5).runCollect
          assertTrue(got == Right(Chunk(3, 4, 5, 6, 7)))
        },
        test("take_then_drop_fallback [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.fromIterable((0 until 10).toList).take(5).drop(3).runCollect
          assertTrue(got == Right(Chunk(3, 4)))
        },
        test("drop_then_take_fallback [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.fromIterable((0 until 10).toList).drop(3).take(5).runCollect
          assertTrue(got == Right(Chunk(3, 4, 5, 6, 7)))
        },
        test("zip_min_length [AdversarialCombinatorLawConvergenceSpec]") {
          val got = (Stream.range(0, 5) && Stream.range(10, 13)).runCollect
          assertTrue(got == Right(Chunk((0, 10), (1, 11), (2, 12))))
        },
        test("buffer_transparency_long_maxvalue [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1L, Long.MaxValue, 3L).buffer(2).runCollect
          assertTrue(got == Right(Chunk(1L, Long.MaxValue, 3L)))
        },
        test("buffer_null_elements [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream[String]("a", null, "b").buffer(2).runCollect
          assertTrue(got == Right(Chunk("a", null, "b")))
        },
        test("scan_repeated_resets_state [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 3).scan(0)(_ + _).repeated.take(6).runCollect
          assertTrue(got == Right(Chunk(0, 1, 3, 0, 1, 3)))
        },
        test("mapAccum_repeated_resets_state [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 3).mapAccum(0)((s, a) => (s + a, s + a)).repeated.take(4).runCollect
          assertTrue(got == Right(Chunk(1, 3, 1, 3)))
        },
        test("sliding_repeated [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 4).sliding(2, 1).repeated.take(4).runCollect.map(_.map(_.toList).toList)
          assertTrue(got == Right(List(List(1, 2), List(2, 3), List(1, 2), List(2, 3))))
        },
        test("chunked_repeated [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(0, 4).chunked(2).repeated.take(4).runCollect.map(_.map(_.toList).toList)
          assertTrue(got == Right(List(List(0, 1), List(2, 3), List(0, 1), List(2, 3))))
        },
        test("intersperse_repeated [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1, 2).intersperse(0).repeated.take(6).runCollect
          assertTrue(got == Right(Chunk(1, 0, 2, 1, 0, 2)))
        },
        test("distinct_repeated [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1, 2, 2, 3).distinct.repeated.take(6).runCollect
          assertTrue(got == Right(Chunk(1, 2, 3, 1, 2, 3)))
        },
        test("scan_byte [AdversarialCombinatorLawConvergenceSpec]") {
          val arr = Array[Byte](1, 2, 3)
          val got = Stream.fromArray(arr).scan(0.toByte)((a, b) => (a + b).toByte).runCollect
          assertTrue(got == Right(Chunk[Byte](0, 1, 3, 6)))
        },
        test("scan_float [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream(1.0f, 2.0f, 3.0f).scan(0.0f)(_ + _).runCollect
          assertTrue(got == Right(Chunk(0.0f, 1.0f, 3.0f, 6.0f)))
        },
        test("takeWhile_then_repeated [AdversarialCombinatorLawConvergenceSpec]") {
          val got = Stream.range(1, 6).takeWhile(_ < 3).repeated.take(4).runCollect
          assertTrue(got == Right(Chunk(1, 2, 1, 2)))
        }
      ),
      suite("AdversarialCompositionHardeningSpec")(
        // ----- Stacked stateful combinators under repeated/take/drop ------------
        test("scan_dropTakeRepeated_stacked [AdversarialCompositionHardeningSpec]") {
          // scan(0)(_+_) over [0,1,2,3,4] = [0,0,1,3,6,10]; drop(1) = [0,1,3,6,10];
          // take(3) = [0,1,3]; repeated -> [0,1,3,0,1,3,...]; take(7)
          val s = Stream.range(0, 5).scan(0)(_ + _).drop(1).take(3).repeated.take(7)
          assertTrue(s.runCollect == Right(Chunk(0, 1, 3, 0, 1, 3, 0)))
        },
        test("sliding_stepGtN_repeatedTake [AdversarialCompositionHardeningSpec]") {
          // [0,1,2,3,4,5] sliding(2,3) = [[0,1],[3,4]]; repeated; take 5
          val s = Stream.range(0, 6).sliding(2, 3).repeated.take(5)
          assertTrue(
            s.runCollect == Right(Chunk(Chunk(0, 1), Chunk(3, 4), Chunk(0, 1), Chunk(3, 4), Chunk(0, 1)))
          )
        },
        test("intersperse_dropTakeRepeated [AdversarialCompositionHardeningSpec]") {
          // [1,2,3] intersperse 0 = [1,0,2,0,3]; drop(1) = [0,2,0,3]; take(3) = [0,2,0]
          // repeated -> [0,2,0,0,2,0,...]; take(7)
          val s = Stream(1, 2, 3).intersperse(0).drop(1).take(3).repeated.take(7)
          assertTrue(s.runCollect == Right(Chunk(0, 2, 0, 0, 2, 0, 0)))
        },
        test("mapAccum_repeatedTake_restartsAccumulator [AdversarialCompositionHardeningSpec]") {
          // running sum via mapAccum over [1,2,3] = [1,3,6]; repeated restarts state
          val s = Stream(1, 2, 3).mapAccum(0)((acc, a) => (acc + a, acc + a)).repeated.take(7)
          assertTrue(s.runCollect == Right(Chunk(1, 3, 6, 1, 3, 6, 1)))
        },

        // ----- Metamorphic: grouped -> flatMap re-flatten == identity ----------
        test("grouped_flatMap_reflatten_isIdentity [AdversarialCompositionHardeningSpec]") {
          val ref = (0 until 17).toList
          val s   = Stream.range(0, 17).grouped(3).flatMap(c => Stream.fromChunk(c))
          assertTrue(s.runCollect == Right(Chunk.fromIterable(ref)))
        },
        test("sliding_stepEqN_equals_grouped [AdversarialCompositionHardeningSpec]") {
          // sliding(n, n) must equal grouped(n) including the trailing partial window
          val viaSliding = Stream.range(0, 7).sliding(3, 3).runCollect
          val viaGrouped = Stream.range(0, 7).grouped(3).runCollect
          assertTrue(viaSliding == viaGrouped) &&
          assertTrue(viaGrouped == Right(Chunk(Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6))))
        },

        // ----- zip && across primitive lanes -----------------------------------
        test("zip_intLongLanes_take [AdversarialCompositionHardeningSpec]") {
          val left  = Stream.range(0, 5)    // Int lane
          val right = Stream(10L, 20L, 30L) // Long lane
          val z     = (left && right).take(10)
          assertTrue(z.runCollect == Right(Chunk((0, 10L), (1, 20L), (2, 30L))))
        },
        test("zip_withSentinelLongValue [AdversarialCompositionHardeningSpec]") {
          // right side contains Long.MaxValue (the Long-lane EOF sentinel) as a real
          // element; zip must not truncate.
          val left  = Stream(1, 2, 3)
          val right = Stream(Long.MaxValue, 0L, Long.MinValue)
          val z     = left && right
          assertTrue(z.runCollect == Right(Chunk((1, Long.MaxValue), (2, 0L), (3, Long.MinValue))))
        },
        test("zip_repeated_take [AdversarialCompositionHardeningSpec]") {
          val z = (Stream(1, 2) && Stream(9, 8, 7)).repeated.take(5)
          // zip of [1,2] and [9,8,7] = [(1,9),(2,8)]; repeated
          assertTrue(z.runCollect == Right(Chunk((1, 9), (2, 8), (1, 9), (2, 8), (1, 9))))
        },

        // ----- flatMap mixed empty/singleton/infinite inners -------------------
        test("flatMap_mixedEmptySingletonInfinite_take [AdversarialCompositionHardeningSpec]") {
          // for each n in [0,1,2,3]: empty if even, repeat(n) (infinite) if odd
          val s = Stream.range(0, 4).flatMap { n =>
            if (n % 2 == 0) Stream.empty else Stream.repeat(n).take(2)
          }
          // n=0 -> []; n=1 -> [1,1]; n=2 -> []; n=3 -> [3,3]
          assertTrue(s.runCollect == Right(Chunk(1, 1, 3, 3)))
        },
        test("flatMap_infiniteInner_outerTake [AdversarialCompositionHardeningSpec]") {
          // first inner is infinite; outer take must stop without consuming forever
          val s = Stream.range(0, 100).flatMap(n => Stream.repeat(n)).take(5)
          assertTrue(s.runCollect == Right(Chunk(0, 0, 0, 0, 0)))
        },

        // ----- Char / Short / Boolean specialized lanes ------------------------
        test("char_grouped_sliding_intersperse [AdversarialCompositionHardeningSpec]") {
          val g = Stream('a', 'b', 'c', 'd', 'e').grouped(2).runCollect
          val l = Stream('a', 'b', 'c', 'd').sliding(2, 1).runCollect
          val i = Stream('a', 'b', 'c').intersperse('-').runCollect
          assertTrue(g == Right(Chunk(Chunk('a', 'b'), Chunk('c', 'd'), Chunk('e')))) &&
          assertTrue(l == Right(Chunk(Chunk('a', 'b'), Chunk('b', 'c'), Chunk('c', 'd')))) &&
          assertTrue(i == Right(Chunk('a', '-', 'b', '-', 'c')))
        },
        test("boolean_scan_distinct_repeated [AdversarialCompositionHardeningSpec]") {
          val sc = Stream(true, false, true).scan(false)(_ ^ _).runCollect
          // scan init false: [false, false^true=true, true^false=true, true^true=false]
          val di = Stream(true, true, false, false, true).distinct.runCollect
          val rp = Stream(true, false).repeated.take(5).runCollect
          assertTrue(sc == Right(Chunk(false, true, true, false))) &&
          assertTrue(di == Right(Chunk(true, false))) &&
          assertTrue(rp == Right(Chunk(true, false, true, false, true)))
        },
        test("short_mapAccum_grouped [AdversarialCompositionHardeningSpec]") {
          val s: Stream[Nothing, Short] = Stream(1.toShort, 2.toShort, 3.toShort, 4.toShort)
          val g                         = s.grouped(3).runCollect
          assertTrue(g == Right(Chunk(Chunk(1.toShort, 2.toShort, 3.toShort), Chunk(4.toShort))))
        },

        // ----- distinct / distinctBy under repeated ----------------------------
        test("distinct_repeated_resetsSeen [AdversarialCompositionHardeningSpec]") {
          val s = Stream(1, 1, 2, 3, 3).distinct.repeated.take(7)
          // distinct = [1,2,3]; repeated -> [1,2,3,1,2,3,1]
          assertTrue(s.runCollect == Right(Chunk(1, 2, 3, 1, 2, 3, 1)))
        },
        test("distinctBy_repeated_resetsSeen [AdversarialCompositionHardeningSpec]") {
          val s = Stream("aa", "ab", "bc", "bd").distinctBy(_.charAt(0)).repeated.take(5)
          // distinctBy first char = ["aa","bc"]; repeated
          assertTrue(s.runCollect == Right(Chunk("aa", "bc", "aa", "bc", "aa")))
        },

        // ----- knownLength internal invariant ----------------------------------
        test("knownLength_matchesActual_acrossCompositions [AdversarialCompositionHardeningSpec]") {
          def check(s: Stream[Nothing, Int]): Boolean =
            s.knownLength match {
              case Some(n) => s.runCollect.map(_.length.toLong) == Right(n)
              case None    => true
            }
          val cases = List(
            Stream.range(0, 10).drop(3).take(2),
            Stream.range(0, 10).take(2).drop(3),
            Stream.range(0, 10).drop(20),
            Stream.range(0, 10).take(Long.MaxValue),
            Stream(1, 2, 3) ++ Stream(4, 5),
            (Stream.range(0, 5) ++ Stream.range(0, 3)).drop(2).take(4),
            Stream.range(0, 10).map(_ * 2).drop(3),
            Stream.range(0, 10).map(_ + 1).take(4).drop(1)
          )
          assertTrue(cases.forall(check))
        },

        // ----- nested recovery + repeated + resource accounting ----------------
        test("catchAll_repeated_closesRecoveryOncePerCycle [AdversarialCompositionHardeningSpec]") {
          val closes                         = new java.util.concurrent.atomic.AtomicInteger(0)
          val opens                          = new java.util.concurrent.atomic.AtomicInteger(0)
          val failing: Stream[String, Int]   = Stream.fail("boom")
          val recovery: Stream[Nothing, Int] =
            Stream
              .fromAcquireRelease({ opens.incrementAndGet(); () }, (_: Unit) => { closes.incrementAndGet(); () })(_ =>
                Stream(7, 8)
              )
          val s      = failing.catchAll(_ => recovery).repeated.take(6)
          val result = s.runCollect
          // recovery runs each cycle: [7,8] repeated -> [7,8,7,8,7,8]
          assertTrue(result == Right(Chunk(7, 8, 7, 8, 7, 8))) &&
          assertTrue(opens.get() == closes.get())
        },
        test("nestedCatchDefect_repeated_restarts [AdversarialCompositionHardeningSpec]") {
          val s = Stream
            .die(new RuntimeException("x"))
            .catchDefect { case _: RuntimeException => Stream(1, 2) }
            .repeated
            .take(5)
          assertTrue(Try(s.runCollect) == scala.util.Success(Right(Chunk(1, 2, 1, 2, 1))))
        },

        // ----- scan over Long lane containing the sentinel value ---------------
        test("scan_longLane_reachesMaxValue_noTruncation [AdversarialCompositionHardeningSpec]") {
          val s = Stream(1L).scan(Long.MaxValue - 1L)(_ + _) // [Max-1, Max]
          assertTrue(s.runCollect == Right(Chunk(Long.MaxValue - 1L, Long.MaxValue)))
        },
        test("grouped_longLane_containsMaxValue [AdversarialCompositionHardeningSpec]") {
          val s = Stream(Long.MaxValue, 1L, 2L).grouped(2)
          assertTrue(s.runCollect == Right(Chunk(Chunk(Long.MaxValue, 1L), Chunk(2L))))
        },

        // ----- Cat M: zip eager-close of a recovery-with-finalizer under repeated
        test("repeated_zipRecoveryFinalizer_shorterRight_closesOncePerCycle [AdversarialCompositionHardeningSpec]") {
          val opens  = new java.util.concurrent.atomic.AtomicInteger(0)
          val closes = new java.util.concurrent.atomic.AtomicInteger(0)
          // left: fails, then recovers into an acquire/release-backed [7,8,9] (len 3)
          val left: Stream[Nothing, Int] =
            Stream
              .fail("x")
              .catchAll(_ =>
                Stream.fromAcquireRelease(
                  { opens.incrementAndGet(); () },
                  (_: Unit) => { closes.incrementAndGet(); () }
                )(_ => Stream(7, 8, 9))
              )
          val right: Stream[Nothing, Int] = Stream(1, 2) // shorter; ends first
          val zipped                      = (left && right).repeated.take(4)
          val result                      = zipped.runCollect
          // each cycle: zip of [7,8,9] and [1,2] = [(7,1),(8,2)]; take 4 -> 2 cycles
          assertTrue(result == Right(Chunk((7, 1), (8, 2), (7, 1), (8, 2)))) &&
          assertTrue(opens.get() == closes.get()) &&
          assertTrue(opens.get() == 2)
        },

        // ----- Cat M: zip eager-close where LEFT recovery side is shorter --------
        test("repeated_zipRecoveryFinalizer_shorterLeft_closesOncePerCycle [AdversarialCompositionHardeningSpec]") {
          val opens                      = new java.util.concurrent.atomic.AtomicInteger(0)
          val closes                     = new java.util.concurrent.atomic.AtomicInteger(0)
          val left: Stream[Nothing, Int] =
            Stream
              .fail("x")
              .catchAll(_ =>
                Stream.fromAcquireRelease(
                  { opens.incrementAndGet(); () },
                  (_: Unit) => { closes.incrementAndGet(); () }
                )(_ => Stream(7, 8))
              )
          val right: Stream[Nothing, Int] = Stream(1, 2, 3, 4) // longer
          val zipped                      = (left && right).repeated.take(5)
          val result                      = zipped.runCollect
          // each cycle: zip of [7,8] and [1,2,3,4] = [(7,1),(8,2)]
          assertTrue(result == Right(Chunk((7, 1), (8, 2), (7, 1), (8, 2), (7, 1)))) &&
          assertTrue(opens.get() == closes.get())
        }
      ),
      suite("AdversarialSentinelTerminalsSpec")(
        // ---- Sentinel collision on terminal ops NOT in AdversarialSentinelSpec ----
        test("head_longFirstIsMaxValue [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(Long.MaxValue, 2L)).head == Right(Some(Long.MaxValue)))
        },
        test("last_longLastIsMaxValue [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1L, 2L, Long.MaxValue)).last == Right(Some(Long.MaxValue)))
        },
        test("last_doubleLastIsMaxValue [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1.0, Double.MaxValue)).last == Right(Some(Double.MaxValue)))
        },
        test("find_longMaxValue [AdversarialSentinelTerminalsSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).find(_ == Long.MaxValue) == Right(Some(Long.MaxValue))
          )
        },
        test("exists_longMaxValueIsLast [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1L, 2L, Long.MaxValue)).exists(_ == 5L) == Right(false))
        },
        test("forall_longMaxValuePresent [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(Long.MaxValue, Long.MaxValue)).forall(_ == Long.MaxValue) == Right(true))
        },
        test("foldLeft_longMaxValue_countsAll [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).runFold(0L)((acc, _) => acc + 1L) == Right(3L))
        },
        test("foldLeft_doubleMaxValue_countsAll [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1.0, Double.MaxValue, 3.0)).runFold(0)((acc, _) => acc + 1) == Right(3))
        },
        test("foreach_longMaxValue_seesAll [AdversarialSentinelTerminalsSpec]") {
          val buf = scala.collection.mutable.ListBuffer.empty[Long]
          val r   = Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).runForeach(buf += _)
          assertTrue(r == Right(()) && buf.toList == List(1L, Long.MaxValue, 3L))
        },
        test("sumLong_withMaxValueDataMidstream_seesAll [AdversarialSentinelTerminalsSpec]") {
          // 1 + (-2) + 3 with a MaxValue in the middle would be different; use a
          // distinguishable check: count via fold already done. Here verify drain.
          assertTrue(Stream.fromChunk(Chunk(0L, Long.MaxValue, 0L)).count == Right(3L))
        },

        // ---- Sentinel through wrappers + head/last ----
        test("head_filtered_longMaxValueFirst [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(Long.MaxValue, 2L)).filter(_ => true).head == Right(Some(Long.MaxValue)))
        },
        test("last_dropped_longMaxValueLast [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1L, 2L, Long.MaxValue)).drop(1).last == Right(Some(Long.MaxValue)))
        },
        test("last_scan_longMaxValueState [AdversarialSentinelTerminalsSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(0L, 0L)).scan(Long.MaxValue)((a, _) => a).last == Right(Some(Long.MaxValue))
          )
        },
        test("find_takeWhile_longMaxValue [AdversarialSentinelTerminalsSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).takeWhile(_ => true).find(_ == Long.MaxValue) == Right(
              Some(Long.MaxValue)
            )
          )
        },
        test("foldLeft_concat_longMaxValue [AdversarialSentinelTerminalsSpec]") {
          val s = Stream.fromChunk(Chunk(1L, Long.MaxValue)) ++ Stream.fromChunk(Chunk(Long.MaxValue, 4L))
          assertTrue(s.runFold(0L)((acc, _) => acc + 1L) == Right(4L))
        },

        // ---- Char / Boolean / Short lanes through combinators + deep ----
        test("char_filter_map_collect [AdversarialSentinelTerminalsSpec]") {
          assertTrue(
            Stream('a', 'b', 'c', 'd').filter(_ != 'b').map(_.toUpper).runCollect == Right(Chunk('A', 'C', 'D'))
          )
        },
        test("boolean_filter_collect [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream(true, false, true).filter(identity).runCollect == Right(Chunk(true, true)))
        },
        test("boolean_map_collect [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream(true, false).map(!_).runCollect == Right(Chunk(false, true)))
        },
        test("short_scan_collect [AdversarialSentinelTerminalsSpec]") {
          assertTrue(
            Stream(1.toShort, 2.toShort, 3.toShort).scan(0.toShort)((a, x) => (a + x).toShort).runCollect == Right(
              Chunk[Short](0, 1, 3, 6)
            )
          )
        },
        test("deep_charLane [AdversarialSentinelTerminalsSpec]") {
          val s = (0 until 150).foldLeft(Stream('x', 'y', 'z'))((acc, _) => acc.map(c => c))
          assertTrue(s.runCollect == Right(Chunk('x', 'y', 'z')))
        },
        test("deep_booleanLane [AdversarialSentinelTerminalsSpec]") {
          val s = (0 until 150).foldLeft(Stream(true, false, true))((acc, _) => acc.map(b => b))
          assertTrue(s.runCollect == Right(Chunk(true, false, true)))
        },
        test("count_charLane [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream('a', 'b', 'c').count == Right(3L))
        },
        test("foldLeft_booleanLane [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream(true, false, true).runFold(0)((acc, b) => acc + (if (b) 1 else 0)) == Right(2))
        },

        // ---- Monad laws at depth with mixed lanes ----
        test("flatMap_rightIdentity_byteInner [AdversarialSentinelTerminalsSpec]") {
          val l = Stream(1.toByte, 2.toByte).flatMap(b => Stream(b)).runCollect
          val r = Stream(1.toByte, 2.toByte).runCollect
          assertTrue(l == r)
        },
        test("flatMap_associativity_intLongByte [AdversarialSentinelTerminalsSpec]") {
          val l =
            Stream(1, 2).flatMap(x => Stream(x.toLong).flatMap(y => Stream((y + 1L).toByte))).runCollect
          val r =
            Stream(1, 2).flatMap(x => Stream(x.toLong)).flatMap(y => Stream((y + 1L).toByte)).runCollect
          assertTrue(l == r && l == Right(Chunk[Byte](2, 3)))
        },
        test("flatMap_deep_byteOuter [AdversarialSentinelTerminalsSpec]") {
          val s = (0 until 60).foldLeft(Stream(1.toByte, 2.toByte))((acc, _) => acc.flatMap(b => Stream(b)))
          assertTrue(s.runCollect == Right(Chunk[Byte](1, 2)))
        },

        // ---- grouped/sliding boundaries ----
        test("grouped_one [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream(1, 2, 3).grouped(1).runCollect == Right(Chunk(Chunk(1), Chunk(2), Chunk(3))))
        },
        test("sliding_stepEqualsN_int [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.range(0, 6).sliding(2, 2).runCollect == Right(Chunk(Chunk(0, 1), Chunk(2, 3), Chunk(4, 5))))
        },
        test("sliding_stepEqualsN_uneven [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream.range(0, 5).sliding(2, 2).runCollect == Right(Chunk(Chunk(0, 1), Chunk(2, 3), Chunk(4))))
        },
        test("sliding_one_one [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream(1, 2, 3).sliding(1, 1).runCollect == Right(Chunk(Chunk(1), Chunk(2), Chunk(3))))
        },
        test("sliding_n_equals_length [AdversarialSentinelTerminalsSpec]") {
          assertTrue(Stream(1, 2, 3).sliding(3, 1).runCollect == Right(Chunk(Chunk(1, 2, 3))))
        },
        test("sliding_byteLane_stepGreaterThanN [AdversarialSentinelTerminalsSpec]") {
          assertTrue(
            Stream(0.toByte, 1.toByte, 2.toByte, 3.toByte, 4.toByte, 5.toByte, 6.toByte)
              .sliding(2, 3)
              .runCollect == Right(Chunk(Chunk[Byte](0, 1), Chunk[Byte](3, 4), Chunk[Byte](6)))
          )
        },

        // ---- distinctBy / distinct sentinel ----
        test("distinctBy_longMaxValue [AdversarialSentinelTerminalsSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1L, Long.MaxValue, Long.MaxValue, 3L)).distinctBy(identity).runCollect == Right(
              Chunk(1L, Long.MaxValue, 3L)
            )
          )
        },

        // ---- zip with sentinel values ----
        test("zip_longMaxValueElements [AdversarialSentinelTerminalsSpec]") {
          assertTrue(
            (Stream.fromChunk(Chunk(1L, Long.MaxValue)) && Stream.fromChunk(
              Chunk(Long.MaxValue, 2L)
            )).runCollect == Right(
              Chunk((1L, Long.MaxValue), (Long.MaxValue, 2L))
            )
          )
        }
      ),
      suite("AdversarialSentinelThroughCombinatorSpec")(
        // ---- chunked over Long.MaxValue (Long-lane EOF sentinel) ----
        test("chunked_long_maxValue_not_truncated [AdversarialSentinelThroughCombinatorSpec]") {
          val xs = Chunk(1L, Long.MaxValue, Long.MaxValue, 2L, 3L)
          val r  = Stream.fromChunk(xs).chunked(2).runCollect
          assertTrue(r == Right(Chunk(Chunk(1L, Long.MaxValue), Chunk(Long.MaxValue, 2L), Chunk(3L))))
        },
        test("chunked_long_all_maxValue [AdversarialSentinelThroughCombinatorSpec]") {
          val xs = Chunk(Long.MaxValue, Long.MaxValue, Long.MaxValue)
          val r  = Stream.fromChunk(xs).chunked(2).runCollect
          assertTrue(r == Right(Chunk(Chunk(Long.MaxValue, Long.MaxValue), Chunk(Long.MaxValue))))
        },
        // ---- chunked over Double.MaxValue / NaN ----
        test("chunked_double_maxValue_not_truncated [AdversarialSentinelThroughCombinatorSpec]") {
          val xs = Chunk(1.0, Double.MaxValue, 2.0)
          val r  = Stream.fromChunk(xs).chunked(3).runCollect
          assertTrue(r == Right(Chunk(Chunk(1.0, Double.MaxValue, 2.0))))
        },
        test("chunked_double_nan_preserved [AdversarialSentinelThroughCombinatorSpec]") {
          val r = Stream.fromChunk(Chunk(Double.NaN, 1.0, Double.NaN)).chunked(2).runCollect
          assertTrue(
            r.fold(
              _ => false,
              cs =>
                cs.length == 2 &&
                  cs(0).length == 2 && cs(0)(0).isNaN && cs(0)(1) == 1.0 &&
                  cs(1).length == 1 && cs(1)(0).isNaN
            )
          )
        },
        // ---- chunked over signed bytes ----
        test("chunked_byte_signed_roundtrip [AdversarialSentinelThroughCombinatorSpec]") {
          val xs: Chunk[Byte] = Chunk[Byte](-128, -1, 0, 1, 127)
          val r               = Stream.fromChunk(xs).chunked(2).runCollect
          assertTrue(r == Right(Chunk(Chunk[Byte](-128, -1), Chunk[Byte](0, 1), Chunk[Byte](127))))
        },
        test("grouped_byte_negative_one_not_eof [AdversarialSentinelThroughCombinatorSpec]") {
          // -1 byte (0xFF) must not be confused with the -1 readByte EOF marker.
          val xs: Chunk[Byte] = Chunk[Byte](-1, -1, -1)
          val r               = Stream.fromChunk(xs).grouped(2).runCollect
          assertTrue(r == Right(Chunk(Chunk[Byte](-1, -1), Chunk[Byte](-1))))
        },
        // ---- intersperse over sentinel values ----
        test("intersperse_long_maxValue_preserved [AdversarialSentinelThroughCombinatorSpec]") {
          val xs = Chunk(Long.MaxValue, Long.MaxValue)
          val r  = Stream.fromChunk(xs).intersperse(0L).runCollect
          assertTrue(r == Right(Chunk(Long.MaxValue, 0L, Long.MaxValue)))
        },
        test("intersperse_byte_negativeOne [AdversarialSentinelThroughCombinatorSpec]") {
          val xs: Chunk[Byte] = Chunk[Byte](-1, -1)
          val sep: Byte       = 5
          val r               = Stream.fromChunk(xs).intersperse(sep).runCollect
          assertTrue(r == Right(Chunk[Byte](-1, 5, -1)))
        },
        // ---- scan / mapAccum over sentinel values ----
        test("scan_long_maxValue_state [AdversarialSentinelThroughCombinatorSpec]") {
          // scan emitting the running max; ensure Long.MaxValue elements flow.
          val xs = Chunk(1L, Long.MaxValue, 2L)
          val r  = Stream.fromChunk(xs).scan(0L)((acc, x) => math.max(acc, x)).runCollect
          assertTrue(r == Right(Chunk(0L, 1L, Long.MaxValue, Long.MaxValue)))
        },
        test("mapAccum_long_maxValue_passthrough [AdversarialSentinelThroughCombinatorSpec]") {
          val xs = Chunk(Long.MaxValue, 1L, Long.MaxValue)
          val r  = Stream.fromChunk(xs).mapAccum(0)((c, x) => (c + 1, x)).runCollect
          assertTrue(r == Right(Chunk(Long.MaxValue, 1L, Long.MaxValue)))
        },
        // ---- collectAll via Sink over chunked output (nested chunks) ----
        test("chunked_then_flatten_collect_equals_input [AdversarialSentinelThroughCombinatorSpec]") {
          val xs = Chunk(1, 2, 3, 4, 5, 6, 7)
          val r  = Stream.fromChunk(xs).chunked(3).runCollect
          assertTrue(r.map(_.foldLeft(Chunk.empty[Int])(_ ++ _)) == Right(xs))
        }
      ),
      suite("AdversarialDeepErrorIntegritySpec")(
        // ---- Deep (> DepthCutoff) interpreter value integrity for collect/scan/mapAccum ----
        test("deep_collect_intLane [AdversarialDeepErrorIntegritySpec]") {
          val s = (0 until 130).foldLeft(Stream.range(0, 6))((acc, _) => acc.collect { case x => x })
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 3, 4, 5)))
        },
        test("deep_collect_byteLane [AdversarialDeepErrorIntegritySpec]") {
          val s = (0 until 130).foldLeft(Stream(1.toByte, 2.toByte, 3.toByte))((acc, _) => acc.collect { case x => x })
          assertTrue(s.runCollect == Right(Chunk[Byte](1, 2, 3)))
        },
        test("deep_scan_intLane [AdversarialDeepErrorIntegritySpec]") {
          val base = Stream(1, 2, 3).scan(0)(_ + _)
          val s    = (0 until 130).foldLeft(base)((acc, _) => acc.map(identity))
          assertTrue(s.runCollect == Right(Chunk(0, 1, 3, 6)))
        },
        test("deep_filter_collect_combined [AdversarialDeepErrorIntegritySpec]") {
          val s = (0 until 130).foldLeft(Stream.range(0, 10))((acc, i) =>
            if (i % 2 == 0) acc.filter(_ => true) else acc.map(identity)
          )
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)))
        },

        // ---- Hostile callback / single evaluation ----
        test("collect_pf_evaluatedExactlyOncePerElement [AdversarialDeepErrorIntegritySpec]") {
          var calls                         = 0
          val pf: PartialFunction[Int, Int] = { case x => calls += 1; x * 2 }
          val out                           = Stream(1, 2, 3, 4).collect(pf).runCollect
          assertTrue(out == Right(Chunk(2, 4, 6, 8)) && calls == 4)
        },
        test("filter_pred_evaluatedExactlyOncePerElement [AdversarialDeepErrorIntegritySpec]") {
          var calls = 0
          val out   = Stream(1, 2, 3, 4).filter { x => calls += 1; x % 2 == 0 }.runCollect
          assertTrue(out == Right(Chunk(2, 4)) && calls == 4)
        },
        test("map_fn_evaluatedExactlyOncePerElement [AdversarialDeepErrorIntegritySpec]") {
          var calls = 0
          val out   = Stream(1, 2, 3).map { x => calls += 1; x }.runCollect
          assertTrue(out == Right(Chunk(1, 2, 3)) && calls == 3)
        },

        // ---- Error integrity: partial side effects then propagate (no swallow) ----
        test("runForeach_mapThrowsMidstream_sideEffectsBeforeFaultOnly [AdversarialDeepErrorIntegritySpec]") {
          val seen = scala.collection.mutable.ListBuffer.empty[Int]
          val res  = threwBoom(
            Stream(1, 2, 3, 4, 5)
              .map(x => if (x == 3) throw boom else x)
              .runForeach(seen += _)
          )
          assertTrue(res && seen.toList == List(1, 2))
        },
        test("scan_throwsMidstream_propagates_byteLane [AdversarialDeepErrorIntegritySpec]") {
          assertTrue(
            threwBoom(
              Stream(1.toByte, 2.toByte, 3.toByte)
                .scan(0.toByte) { (a, x) =>
                  if (x == 2.toByte) throw boom else (a + x).toByte
                }
                .runCollect
            )
          )
        },
        test("grouped_sourceErrors_propagates [AdversarialDeepErrorIntegritySpec]") {
          val e = new RuntimeException("boom-sentinel")
          val s = (Stream(1, 2, 3) ++ Stream.fail(e) ++ Stream(4)).grouped(2)
          assertTrue(s.runCollect == Left(e))
        },
        test("sliding_sourceErrors_propagates [AdversarialDeepErrorIntegritySpec]") {
          val e = new RuntimeException("boom-sentinel")
          val s = (Stream(1, 2, 3) ++ Stream.fail(e) ++ Stream(4)).sliding(2, 1)
          assertTrue(s.runCollect == Left(e))
        },
        test("scan_sourceErrors_propagates [AdversarialDeepErrorIntegritySpec]") {
          val e = new RuntimeException("boom-sentinel")
          val s = (Stream(1, 2) ++ Stream.fail(e)).scan(0)(_ + _)
          assertTrue(s.runCollect == Left(e))
        },
        test("intersperse_sourceErrors_propagates [AdversarialDeepErrorIntegritySpec]") {
          val e = new RuntimeException("boom-sentinel")
          val s = (Stream(1, 2) ++ Stream.fail(e)).intersperse(0)
          assertTrue(s.runCollect == Left(e))
        },

        // ---- Typed-error channel + catchAll ordering ----
        test("flatMap_innerTypedFail_caughtByCatchAll_orderPreserved [AdversarialDeepErrorIntegritySpec]") {
          val s: Stream[String, Int] =
            Stream(1, 2, 3).flatMap[String, String, Int](x => if (x == 2) Stream.fail("err2") else Stream(x, x * 10))
          val recovered = s.catchAll(e => Stream(-1))
          // emits 1,10 then hits fail at x=2 -> recovers with -1; flatMap stops at failure.
          assertTrue(recovered.runCollect == Right(Chunk(1, 10, -1)))
        },
        test("fail_typedError_isLeft [AdversarialDeepErrorIntegritySpec]") {
          assertTrue(Stream.fail("nope").runCollect == Left("nope"))
        },
        test("mapError_transformsTypedError [AdversarialDeepErrorIntegritySpec]") {
          assertTrue(Stream.fail("e").mapError(_ + "!").runCollect == Left("e!"))
        },

        // ---- ensuring exactly-once ----
        test("ensuring_runsOnceOnSuccess [AdversarialDeepErrorIntegritySpec]") {
          var fin = 0
          val r   = Stream(1, 2, 3).ensuring(fin += 1).runCollect
          assertTrue(r == Right(Chunk(1, 2, 3)) && fin == 1)
        },
        test("ensuring_runsOnceOnFailure [AdversarialDeepErrorIntegritySpec]") {
          var fin = 0
          val r   = threwBoom(
            Stream(1, 2, 3).map(x => if (x == 2) throw boom else x).ensuring(fin += 1).runCollect
          )
          assertTrue(r && fin == 1)
        },

        // ---- distinct with null / boundary ----
        test("distinct_withNullElements [AdversarialDeepErrorIntegritySpec]") {
          val s: Stream[Nothing, String] = Stream.fromChunk(Chunk[String]("a", null, "a", null, "b"))
          assertTrue(s.distinct.runCollect == Right(Chunk[String]("a", null, "b")))
        },

        // ---- repeated over collect resets cleanly ----
        test("repeated_collect_resets [AdversarialDeepErrorIntegritySpec]") {
          assertTrue(
            Stream(1, 2, 3).collect { case x if x % 2 == 1 => x }.repeated.take(4).runCollect == Right(
              Chunk(1, 3, 1, 3)
            )
          )
        }
      ),
      suite("AdversarialDeepChainNullConvergenceSpec")(
        test("deep_map_chain [AdversarialDeepChainNullConvergenceSpec]") {
          val s = (0 until 500).foldLeft(Stream.range(0, 5))((acc, _) => acc.map(_ + 1))
          assertTrue(s.runCollect == Right(Chunk(500, 501, 502, 503, 504)))
        },
        test("deep_filter_chain [AdversarialDeepChainNullConvergenceSpec]") {
          val s = (0 until 300).foldLeft(Stream.range(0, 20))((acc, _) => acc.filter(_ => true))
          assertTrue(s.runCollect == Right(Chunk.fromIterable(0 until 20)))
        },
        test("deep_concat_chain [AdversarialDeepChainNullConvergenceSpec]") {
          val s = (0 until 300).foldLeft(Stream(0))((acc, i) => acc ++ Stream(i + 1))
          assertTrue(s.runCollect.map(_.length) == Right(301))
        },
        test("deep_map_then_count [AdversarialDeepChainNullConvergenceSpec]") {
          val s = (0 until 400).foldLeft(Stream.range(0, 1000))((acc, _) => acc.map(identity))
          assertTrue(s.count == Right(1000L))
        },
        test("deep_chain_long_lane [AdversarialDeepChainNullConvergenceSpec]") {
          val s = (0 until 300).foldLeft(Stream(Long.MaxValue, 1L, 2L))((acc, _) => acc.map(x => x))
          assertTrue(s.runCollect == Right(Chunk(Long.MaxValue, 1L, 2L)))
        },
        test("null_map [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String]("a", null, "b").map(x => if (x == null) "N" else x)
          assertTrue(s.runCollect == Right(Chunk("a", "N", "b")))
        },
        test("null_filter [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String]("a", null, "b").filter(_ != null)
          assertTrue(s.runCollect == Right(Chunk("a", "b")))
        },
        test("null_distinct [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String]("a", null, "a", null, "b").distinct
          assertTrue(s.runCollect == Right(Chunk("a", null, "b")))
        },
        test("null_intersperse [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String]("a", "b").intersperse(null.asInstanceOf[String])
          assertTrue(s.runCollect == Right(Chunk("a", null, "b")))
        },
        test("null_chunked [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String]("a", null, "b", null).chunked(2)
          assertTrue(s.runCollect.map(_.map(_.toList).toList) == Right(List(List("a", null), List("b", null))))
        },
        test("null_scan [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String]("a", "b").scan("")((acc, x) => acc + (if (x == null) "_" else x))
          assertTrue(s.runCollect == Right(Chunk("", "a", "ab")))
        },
        test("null_last [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String]("a", null)
          assertTrue(s.last == Right(Some(null)))
        },
        test("null_head [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String](null, "a")
          assertTrue(s.head == Right(Some(null)))
        },
        test("null_take_drop [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream[String]("a", null, "b", null, "c").drop(1).take(3)
          assertTrue(s.runCollect == Right(Chunk(null, "b", null)))
        },
        test("fromIterator_basic [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream.fromIterator(List(1, 2, 3).iterator)
          assertTrue(s.runCollect == Right(Chunk(1, 2, 3)))
        },
        test("unfold_basic [AdversarialDeepChainNullConvergenceSpec]") {
          val s = Stream.unfold(0)(s => if (s < 5) Some((s, s + 1)) else None)
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 3, 4)))
        }
      ),
      suite("AdversarialErrorLifecycleProbeSpec")(
        test("zip_rightFails_returnsLeftError [AdversarialErrorLifecycleProbeSpec]") {
          val z = Stream.range(0, 5) && (Stream.fail("boom"): Stream[String, Int])
          assertTrue(z.runCollect == Left("boom"))
        },
        test("zip_leftFails_returnsLeftError [AdversarialErrorLifecycleProbeSpec]") {
          val z = (Stream.fail("boom"): Stream[String, Int]) && Stream.range(0, 5)
          assertTrue(z.runCollect == Left("boom"))
        },
        test("zip_rightFails_closesManagedLeftOnce [AdversarialErrorLifecycleProbeSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val z      = managed(opens, closes, 1, 2, 3) && (Stream.fail("boom"): Stream[String, Int])
          val r      = z.runCollect
          assertTrue(r == Left("boom")) && assertTrue(opens.get() == 1) && assertTrue(closes.get() == 1)
        },
        test("zip_leftFails_closesManagedRightOnce [AdversarialErrorLifecycleProbeSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val z      = (Stream.fail("boom"): Stream[String, Int]) && managed(opens, closes, 1, 2, 3)
          val r      = z.runCollect
          assertTrue(r == Left("boom")) && assertTrue(opens.get() == 1) && assertTrue(closes.get() == 1)
        },
        test("catchAll_recovers_value [AdversarialErrorLifecycleProbeSpec]") {
          val s: Stream[Nothing, Int] = (Stream.range(0, 3) ++ Stream.fail("x")).catchAll(_ => Stream.range(10, 12))
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 10, 11)))
        },
        test("catchAll_recoveryAlsoFails_propagatesSecondError [AdversarialErrorLifecycleProbeSpec]") {
          val s = (Stream.range(0, 2) ++ Stream.fail("first")).catchAll(_ => Stream.fail("second"))
          assertTrue(s.runCollect == Left("second"))
        },
        test("flatMap_innerFails_returnsLeft_closesManagedOuterOnce [AdversarialErrorLifecycleProbeSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val s      = managed(opens, closes, 1, 2, 3).flatMap { i =>
            if (i == 2) Stream.fail("boom") else Stream.succeed(i)
          }
          val r = s.runCollect
          assertTrue(r == Left("boom")) && assertTrue(opens.get() == 1) && assertTrue(closes.get() == 1)
        },
        test("pipeline_toSink_sinkFails_propagates [AdversarialErrorLifecycleProbeSpec]") {
          val pipe = Pipeline.map[Int, Int](_ * 2)
          val sink = pipe.andThenSink(Sink.fail[String]("nope"))
          assertTrue(Stream.range(0, 3).run(sink) == Left("nope"))
        },
        test("ensuring_runsOnce_onError [AdversarialErrorLifecycleProbeSpec]") {
          val count = new AtomicInteger(0)
          val s     = (Stream.range(0, 2) ++ Stream.fail("e")).ensuring(count.incrementAndGet())
          val r     = s.runCollect
          assertTrue(r == Left("e")) && assertTrue(count.get() == 1)
        },
        test("ensuring_runsOnce_onSuccess [AdversarialErrorLifecycleProbeSpec]") {
          val count = new AtomicInteger(0)
          val s     = Stream.range(0, 3).ensuring(count.incrementAndGet())
          val r     = s.runCollect
          assertTrue(r == Right(Chunk(0, 1, 2))) && assertTrue(count.get() == 1)
        },
        test("flatMap_managedInner_eachInnerReleasedOnce [AdversarialErrorLifecycleProbeSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val s      = Stream.range(0, 3).flatMap(i => managed(opens, closes, i, i + 10))
          val r      = s.runCollect
          assertTrue(r == Right(Chunk(0, 10, 1, 11, 2, 12))) &&
          assertTrue(opens.get() == 3) && assertTrue(closes.get() == 3)
        }
      ),
      suite("AdversarialKnownLengthConvergenceSpec")(
        test("range_len [AdversarialKnownLengthConvergenceSpec]")(
          assertTrue(checkLen(Stream.range(0, 10)) && Stream.range(0, 10).knownLength == Some(10L))
        ),
        test("map_len [AdversarialKnownLengthConvergenceSpec]")(assertTrue(checkLen(Stream.range(0, 10).map(_ + 1)))),
        test("take_capped [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 10).take(20)) && Stream.range(0, 10).take(20).knownLength == Some(10L))
        },
        test("take_smaller [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 10).take(4)) && Stream.range(0, 10).take(4).knownLength == Some(4L))
        },
        test("drop_partial [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 10).drop(3)) && Stream.range(0, 10).drop(3).knownLength == Some(7L))
        },
        test("drop_beyond [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 10).drop(20)) && Stream.range(0, 10).drop(20).knownLength == Some(0L))
        },
        test("take_drop_compose [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 10).take(5).drop(2)))
        },
        test("drop_take_compose [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 10).drop(2).take(5)))
        },
        test("concat_sum [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 4) ++ Stream.range(0, 6)))
        },
        test("ensuring_preserves [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 7).ensuring(())))
        },
        test("take_negative [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(checkLen(Stream.range(0, 10).take(-1)) && Stream.range(0, 10).take(-1).knownLength == Some(0L))
        },
        test("drop_negative [AdversarialKnownLengthConvergenceSpec]") {
          // drop(-1) is identity; knownLength must not exceed actual length.
          assertTrue(checkLen(Stream.range(0, 10).drop(-1)))
        },
        test("intRange_fullwidth_len [AdversarialKnownLengthConvergenceSpec]") {
          assertTrue(Stream.range(Int.MinValue, 0).knownLength == Some(2147483648L))
        },
        test("fromChunk_knownChunk_matches [AdversarialKnownLengthConvergenceSpec]") {
          val s = Stream.fromChunk(zio.blocks.chunk.Chunk(1, 2, 3))
          assertTrue(s.knownChunk.map(_.toList) == Some(List(1, 2, 3)) && checkLen(s))
        }
      ),
      suite("AdversarialMetadataSpec")(
        // ---- controls: the sibling constructors DO populate metadata ----
        test("control: fromRange knownLength is Some [AdversarialMetadataSpec]") {
          assertTrue(Stream.fromRange(0 until 10).knownLength == Some(10L))
        },
        test("control: fromChunk knownLength is Some [AdversarialMetadataSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1, 2, 3)).knownLength == Some(3L))
        },
        test("control: fromChunk knownChunk is Some [AdversarialMetadataSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1, 2, 3)).knownChunk == Some(Chunk(1, 2, 3)))
        },

        // ---- BUG-M1: Stream.range drops the O(1)-knowable length ----
        test("range_knownLength_matchesEquivalentFromRange [AdversarialMetadataSpec]") {
          assertTrue(Stream.range(0, 10).knownLength == Some(10L))
        },
        test("range_knownLength_propagatesThroughMap [AdversarialMetadataSpec]") {
          assertTrue(Stream.range(0, 10).map(_ + 1).knownLength == Some(10L))
        },

        // ---- BUG-M2: Stream.apply(as*) drops the O(1)-knowable length/chunk ----
        test("apply_knownLength_isKnown [AdversarialMetadataSpec]") {
          assertTrue(Stream(1, 2, 3).knownLength == Some(3L))
        },
        test("apply_knownChunk_isKnown [AdversarialMetadataSpec]") {
          assertTrue(Stream(1, 2, 3).knownChunk == Some(Chunk(1, 2, 3)))
        }
      ),
      suite("AdversarialDeepFlatMapSpec")(
        test("deep flatMap chain with distinct functions is not corrupted (n below cap) [AdversarialDeepFlatMapSpec]") {
          assertTrue(deepSum(8000) == Right(Chunk(8000 * 7999 / 2)))
        },
        test("deep flatMap chain at the 13-bit boundary (n = 8192) [AdversarialDeepFlatMapSpec]") {
          assertTrue(deepSum(8192) == Right(Chunk(8192 * 8191 / 2)))
        },
        test("deep flatMap chain just past the boundary (n = 8300) [AdversarialDeepFlatMapSpec]") {
          assertTrue(deepSum(8300) == Right(Chunk(8300 * 8299 / 2)))
        },
        test("deep flatMap chain well past the boundary (n = 10000) [AdversarialDeepFlatMapSpec]") {
          assertTrue(deepSum(10000) == Right(Chunk(10000L.toInt * 9999 / 2)))
        },
        test("deep flatMap chain very deep (n = 50000) [AdversarialDeepFlatMapSpec]") {
          val n        = 50000
          val expected = n.toLong * (n - 1) / 2
          assertTrue(deepSum(n) == Right(Chunk(expected.toInt)))
        },
        test("terminal type-changing flatMap in fused path emits the new element type [AdversarialDeepFlatMapSpec]") {
          // Force the fused Interpreter path with a long map prefix, then a flatMap
          // that changes the element type Int -> String (lane Int -> lane Ref).
          var s: Stream[Nothing, Int] = Stream.succeed(0)
          var i                       = 0
          while (i < 150) { s = s.map((x: Int) => x + 1); i += 1 }
          val out: Stream[Nothing, String] = s.flatMap((_: Int) => Stream.succeed("x"))
          assertTrue(out.runCollect == Right(Chunk("x")))
        },
        test("type-changing flatMap with downstream continuation (mixed lanes, fused) [AdversarialDeepFlatMapSpec]") {
          var s: Stream[Nothing, Int] = Stream.succeed(1)
          var i                       = 0
          while (i < 150) { s = s.map((x: Int) => x); i += 1 }
          val out: Stream[Nothing, Int] =
            s.flatMap((_: Int) => Stream.succeed("abc")).map((str: String) => str.length)
          assertTrue(out.runCollect == Right(Chunk(3)))
        },
        test("type-changing flatMap chain Int -> String -> Int (fused) [AdversarialDeepFlatMapSpec]") {
          var s: Stream[Nothing, Int] = Stream.succeed(5)
          var i                       = 0
          while (i < 150) { s = s.map((x: Int) => x); i += 1 }
          val out: Stream[Nothing, Int] =
            s.flatMap((n: Int) => Stream.succeed("ab" * n)).flatMap((str: String) => Stream.succeed(str.length))
          assertTrue(out.runCollect == Right(Chunk(10)))
        }
      ),
      suite("AdversarialCompileStackSafetySpec")(
        test(
          "deep linear map chain (20k) compiles & runs without StackOverflowError [AdversarialCompileStackSafetySpec]"
        ) {
          val outcome =
            try {
              var s = Stream(1)
              var i = 0
              while (i < 20000) { s = s.map(_ + 1); i += 1 }
              s.runCollect.toString
            } catch { case t: Throwable => t.getClass.getName }
          assertTrue(outcome == "Right(Chunk(20001))")
        },
        test(
          "deep linear filter chain (20k) compiles & runs without StackOverflowError [AdversarialCompileStackSafetySpec]"
        ) {
          val outcome =
            try {
              var s: Stream[Nothing, Int] = Stream(1)
              var i                       = 0
              while (i < 20000) { s = s.filter(_ => true); i += 1 }
              s.runCollect == Right(Chunk(1))
            } catch { case t: Throwable => t.getClass.getName }
          assertTrue(outcome == true)
        }
      ),
      suite("AdversarialCombinatorDifferentialSpec")(
        test(
          "sliding(n, step) matches List.sliding across all lanes and a length/param matrix [AdversarialCombinatorDifferentialSpec]"
        ) {
          // Accumulate mismatches into a plain list and assert once: chaining
          // thousands of `&&`-ed assertions builds a deep BoolAlgebra that
          // stack-overflows the JS runtime (a test-harness limit, not a defect).
          val bad = scala.collection.mutable.ListBuffer.empty[String]
          for (len <- 0 to 9; n <- 1 to 6; step <- 1 to 6) {
            val ints                                                             = (1 to len).toList
            def check[A](xs: List[A], lane: String)(s: Stream[Nothing, A]): Unit = {
              val exp = xs.sliding(n, step).map(Chunk.fromIterable(_)).toList
              if (s.sliding(n, step).runCollect != Right(Chunk.fromIterable(exp)))
                bad += s"$lane len=$len n=$n step=$step"
            }
            check(ints, "Int")(Stream.fromIterable(ints))
            check(ints.map(_.toLong), "Long")(Stream.fromIterable(ints.map(_.toLong)))
            check(ints.map(_.toDouble), "Double")(Stream.fromIterable(ints.map(_.toDouble)))
            check(ints.map(_.toFloat), "Float")(Stream.fromIterable(ints.map(_.toFloat)))
            check(ints.map(_.toByte), "Byte")(Stream.fromIterable(ints.map(_.toByte)))
            check(ints.map(_.toString), "Ref")(Stream.fromIterable(ints.map(_.toString)))
          }
          assertTrue(bad.toList == Nil)
        },
        test(
          "chunked(n) matches List.grouped across all lanes and a length/param matrix [AdversarialCombinatorDifferentialSpec]"
        ) {
          val bad = scala.collection.mutable.ListBuffer.empty[String]
          for (len <- 0 to 9; n <- 1 to 6) {
            val ints                                                             = (1 to len).toList
            def check[A](xs: List[A], lane: String)(s: Stream[Nothing, A]): Unit = {
              val exp = xs.grouped(n).map(Chunk.fromIterable(_)).toList
              if (s.chunked(n).runCollect != Right(Chunk.fromIterable(exp))) bad += s"$lane len=$len n=$n"
            }
            check(ints, "Int")(Stream.fromIterable(ints))
            check(ints.map(_.toLong), "Long")(Stream.fromIterable(ints.map(_.toLong)))
            check(ints.map(_.toDouble), "Double")(Stream.fromIterable(ints.map(_.toDouble)))
            check(ints.map(_.toFloat), "Float")(Stream.fromIterable(ints.map(_.toFloat)))
            check(ints.map(_.toByte), "Byte")(Stream.fromIterable(ints.map(_.toByte)))
            check(ints.map(_.toString), "Ref")(Stream.fromIterable(ints.map(_.toString)))
          }
          assertTrue(bad.toList == Nil)
        },
        test("scan(z)(f) matches List.scanLeft across specialized lanes [AdversarialCombinatorDifferentialSpec]") {
          val bad = scala.collection.mutable.ListBuffer.empty[String]
          for (len <- 0 to 6) {
            val ints = (1 to len).toList
            if (
              Stream.fromIterable(ints).scan(0)(_ + _).runCollect != Right(Chunk.fromIterable(ints.scanLeft(0)(_ + _)))
            )
              bad += s"Int len=$len"
            val ls = ints.map(_.toLong)
            if (Stream.fromIterable(ls).scan(0L)(_ + _).runCollect != Right(Chunk.fromIterable(ls.scanLeft(0L)(_ + _))))
              bad += s"Long len=$len"
            val ds = ints.map(_.toDouble)
            if (
              Stream.fromIterable(ds).scan(0.0)(_ + _).runCollect != Right(Chunk.fromIterable(ds.scanLeft(0.0)(_ + _)))
            )
              bad += s"Double len=$len"
            val fs = ints.map(_.toFloat)
            if (
              Stream.fromIterable(fs).scan(0.0f)(_ + _).runCollect != Right(
                Chunk.fromIterable(fs.scanLeft(0.0f)(_ + _))
              )
            )
              bad += s"Float len=$len"
            val bs = ints.map(_.toByte)
            if (
              Stream
                .fromIterable(bs)
                .scan(0.toByte)((a, b) => (a + b).toByte)
                .runCollect != Right(Chunk.fromIterable(bs.scanLeft(0.toByte)((a, b) => (a + b).toByte)))
            ) bad += s"Byte len=$len"
          }
          assertTrue(bad.toList == Nil)
        },
        test(
          "distinct matches List.distinct; intersperse and mapAccum match references [AdversarialCombinatorDifferentialSpec]"
        ) {
          val in             = List(1, 1, 2, 3, 2, 4, 4, 5, 1)
          val intersperseExp = List(1, 2, 3, 4).flatMap(x => List(x, 0)).dropRight(1)
          assertTrue(Stream.fromIterable(in).distinct.runCollect == Right(Chunk.fromIterable(in.distinct))) &&
          assertTrue(
            Stream.fromIterable(List(1, 2, 3, 4)).intersperse(0).runCollect ==
              Right(Chunk.fromIterable(intersperseExp))
          ) &&
          assertTrue(
            Stream
              .fromIterable(List(1, 2, 3, 4))
              .mapAccum(0) { (s, a) =>
                val s2 = s + a; (s2, s2)
              }
              .runCollect == Right(Chunk(1, 3, 6, 10))
          )
        },
        test(
          "&& zips positionally, shorter side wins, across mismatched lengths [AdversarialCombinatorDifferentialSpec]"
        ) {
          val bad = scala.collection.mutable.ListBuffer.empty[String]
          for (la <- 0 to 5; lb <- 0 to 5) {
            val a   = (1 to la).toList
            val b   = (1 to lb).map(i => ('a' + i - 1).toChar.toString).toList
            val got = (Stream.fromIterable(a) && Stream.fromIterable(b)).runCollect
            if (got != Right(Chunk.fromIterable(a.zip(b)))) bad += s"la=$la lb=$lb"
          }
          assertTrue(bad.toList == Nil)
        },
        test(
          "take/drop boundaries (negative, zero, exact, overshoot) match List [AdversarialCombinatorDifferentialSpec]"
        ) {
          val in  = (0 to 5).toList
          val bad = scala.collection.mutable.ListBuffer.empty[String]
          for (k <- -1 to 7) {
            if (Stream.fromIterable(in).take(k.toLong).runCollect != Right(Chunk.fromIterable(in.take(k))))
              bad += s"take $k"
            if (Stream.fromIterable(in).drop(k.toLong).runCollect != Right(Chunk.fromIterable(in.drop(k))))
              bad += s"drop $k"
          }
          assertTrue(bad.toList == Nil) &&
          assertTrue(
            Stream.fromIterable(List(1, 2, 3, 4, 1, 2)).takeWhile(_ < 4).runCollect == Right(Chunk(1, 2, 3))
          )
        },
        test("range numeric boundaries (overflow-adjacent, empty, negative) [AdversarialCombinatorDifferentialSpec]") {
          assertTrue(
            Stream.range(Int.MaxValue - 2, Int.MaxValue).runCollect == Right(Chunk(Int.MaxValue - 2, Int.MaxValue - 1))
          ) &&
          assertTrue(Stream.range(0, Int.MinValue).runCollect == Right(Chunk.empty[Int])) &&
          assertTrue(Stream.range(5, 5).runCollect == Right(Chunk.empty[Int])) &&
          assertTrue(Stream.range(-3, 2).runCollect == Right(Chunk(-3, -2, -1, 0, 1)))
        }
      ),
      suite("AdversarialRepeatedEmptySpec")(
        test("empty.repeated is empty, not a livelock [AdversarialRepeatedEmptySpec]") {
          assertTrue(Stream.empty.repeated.take(5).runCollect == Right(Chunk.empty))
        },
        test("empty Int chunk repeated is empty (Int lane) [AdversarialRepeatedEmptySpec]") {
          assertTrue(Stream.fromChunk(Chunk.empty[Int]).repeated.take(5).runCollect == Right(Chunk.empty[Int]))
        },
        test("empty Long chunk repeated is empty (Long lane) [AdversarialRepeatedEmptySpec]") {
          assertTrue(Stream.fromChunk(Chunk.empty[Long]).repeated.take(5).runCollect == Right(Chunk.empty[Long]))
        },
        test("empty Double chunk repeated is empty (Double lane) [AdversarialRepeatedEmptySpec]") {
          assertTrue(Stream.fromChunk(Chunk.empty[Double]).repeated.take(5).runCollect == Right(Chunk.empty[Double]))
        },
        test("filtered-to-empty repeated is empty (consumed work, emitted nothing) [AdversarialRepeatedEmptySpec]") {
          assertTrue(
            Stream.fromChunk(Chunk(1, 2, 3)).filter(_ => false).repeated.take(5).runCollect == Right(Chunk.empty[Int])
          )
        },
        test("range-to-empty repeated is empty [AdversarialRepeatedEmptySpec]") {
          assertTrue(Stream.range(0, 0).repeated.take(5).runCollect == Right(Chunk.empty[Int]))
        },
        test("non-empty repeated still cycles (no regression) [AdversarialRepeatedEmptySpec]") {
          assertTrue(Stream.fromChunk(Chunk(1, 2)).repeated.take(5).runCollect == Right(Chunk(1, 2, 1, 2, 1)))
        },
        test("single-element repeated still cycles [AdversarialRepeatedEmptySpec]") {
          assertTrue(Stream.succeed(7).repeated.take(3).runCollect == Right(Chunk(7, 7, 7)))
        }
      ),
      suite("AdversarialRepeatedErrorHandlerSpec")(
        // ---- CONTROL: stateless transform restarts correctly via reset().
        test("repeated_overMap_control_restarts [AdversarialRepeatedErrorHandlerSpec]") {
          val result = Stream(1, 2, 3).map(_ + 0).repeated.take(5).runCollect
          assertTrue(result == Right(Chunk(1, 2, 3, 1, 2)))
        },
        // ---- BUG: catchAll handles the error inside the cycle; repeated must restart.
        test("repeated_overCatchAll_restartsRecoveredCycle [AdversarialRepeatedErrorHandlerSpec]") {
          val result = Stream.fail("boom").catchAll(_ => Stream(1, 2, 3)).repeated.take(7).runCollect
          assertTrue(result == Right(Chunk(1, 2, 3, 1, 2, 3, 1)))
        },
        // ---- BUG: orElse is catchAll's alias; same contract violation.
        test("repeated_overOrElse_restartsFallbackCycle [AdversarialRepeatedErrorHandlerSpec]") {
          val result = Stream.fail("boom").orElse(Stream(1, 2, 3)).repeated.take(7).runCollect
          assertTrue(result == Right(Chunk(1, 2, 3, 1, 2, 3, 1)))
        },
        // ---- BUG: catchDefect recovers a defect inside the cycle; repeated must restart.
        test("repeated_overCatchDefect_restartsRecoveredCycle [AdversarialRepeatedErrorHandlerSpec]") {
          val result = Stream(1)
            .map[Int](_ => throw new RuntimeException("defect"))
            .catchDefect { case _: RuntimeException => Stream(7, 8) }
            .repeated
            .take(5)
            .runCollect
          assertTrue(result == Right(Chunk(7, 8, 7, 8, 7)))
        },
        // ---- BUG (mapError): no error in flight at all, yet reset throws.
        test("repeated_overMapError_restarts [AdversarialRepeatedErrorHandlerSpec]") {
          val result = Stream(1, 2, 3).mapError(identity).repeated.take(5).runCollect
          assertTrue(result == Right(Chunk(1, 2, 3, 1, 2)))
        },
        // ---- BUG (zip restart manifestation): `&&` restart also calls reset(); a
        // mapError on one side makes the repeated zip throw instead of restarting.
        test("repeated_overZipWithMapErrorSide_restarts [AdversarialRepeatedErrorHandlerSpec]") {
          val result =
            (Stream(1, 2, 3).mapError(identity) && Stream(4, 5, 6)).repeated.take(5).runCollect
          assertTrue(result == Right(Chunk((1, 4), (2, 5), (3, 6), (1, 4), (2, 5))))
        }
      ),
      suite("AdversarialRepeatStateSpec")(
        // BUG-002a: scan(...).repeated crashes instead of restarting.
        // scan(0)(_+_) over [0,1,2] = [0,0,1,3]; repeated twice, take 8.
        test("repeated_afterScanInMemory_restartsCleanly [AdversarialRepeatStateSpec]") {
          val s      = Stream.range(0, 3).scan(0)(_ + _)
          val result = Try(s.repeated.take(8).runCollect)
          assertTrue(result == scala.util.Success(Right(Chunk(0, 0, 1, 3, 0, 0, 1, 3))))
        },

        // BUG-002b: grouped(...).repeated crashes instead of restarting.
        test("repeated_afterGroupedInMemory_restartsCleanly [AdversarialRepeatStateSpec]") {
          val s      = Stream.range(0, 4).grouped(2)
          val result = Try(s.repeated.take(4).runCollect)
          assertTrue(
            result == scala.util.Success(Right(Chunk(Chunk(0, 1), Chunk(2, 3), Chunk(0, 1), Chunk(2, 3))))
          )
        },

        // BUG-002c: intersperse(...).repeated crashes instead of restarting.
        test("repeated_afterIntersperseInMemory_restartsCleanly [AdversarialRepeatStateSpec]") {
          val s      = Stream.fromChunk(Chunk(1, 2)).intersperse(0)
          val result = Try(s.repeated.take(6).runCollect)
          assertTrue(result == scala.util.Success(Right(Chunk(1, 0, 2, 1, 0, 2))))
        },

        // BUG-003: mapAccum's accumulator is not reset on repeat, so the second
        // cycle continues accumulating instead of restarting from `init`.
        // mapAccum(0) running-sum over [1,2] = [1,3]; law says repeat == [1,3,1,3].
        test("repeated_afterMapAccum_resetsAccumulator [AdversarialRepeatStateSpec]") {
          val s      = Stream.range(1, 3).mapAccum(0)((acc, a) => (acc + a, acc + a))
          val result = s.repeated.take(4).runCollect
          assertTrue(result == Right(Chunk(1, 3, 1, 3)))
        },

        // BUG-005: distinct's `seen` set is not reset on repeat. Before the fix, the
        // second cycle saw every element as a duplicate and emitted nothing, so
        // `repeated` (which loops until a non-EOF element appears) LIVELOCKED. The
        // `take(6)` bounds the output, but producing element #4 would spin forever
        // without the fix; with the fix the second cycle replays cleanly.
        // distinct over [1,1,2,3,3] = [1,2,3]; repeated == [1,2,3,1,2,3].
        test("repeated_afterDistinctInMemory_restartsWithoutLivelock [AdversarialRepeatStateSpec]") {
          val s      = Stream.fromChunk(Chunk(1, 1, 2, 3, 3)).distinct
          val result = Try(s.repeated.take(6).runCollect)
          assertTrue(result == scala.util.Success(Right(Chunk(1, 2, 3, 1, 2, 3))))
        },

        // BUG-005 (distinctBy variant): key set must also reset across cycles.
        // distinctBy(_.length) over ["a","bb","cc","ddd"] = ["a","bb","ddd"].
        test("repeated_afterDistinctByInMemory_restartsWithoutLivelock [AdversarialRepeatStateSpec]") {
          val s      = Stream.fromChunk(Chunk("a", "bb", "cc", "ddd")).distinctBy(_.length)
          val result = Try(s.repeated.take(6).runCollect)
          assertTrue(result == scala.util.Success(Right(Chunk("a", "bb", "ddd", "a", "bb", "ddd"))))
        }
      ),
      suite("AdversarialRangeOverflowSpec")(
        test("range_overIntMaxElements_take3_lazy [AdversarialRangeOverflowSpec]") {
          // [-1, Int.MaxValue) has Int.MaxValue + 1 elements.
          assertTrue(Stream.range(-1, Int.MaxValue).take(3).runCollect == Right(Chunk(-1, 0, 1)))
        },
        test("range_fullNegativeHalf_take3_lazy [AdversarialRangeOverflowSpec]") {
          // [Int.MinValue, 0) has 2^31 elements (> Int.MaxValue).
          assertTrue(
            Stream.range(Int.MinValue, 0).take(3).runCollect == Right(
              Chunk(Int.MinValue, Int.MinValue + 1, Int.MinValue + 2)
            )
          )
        },
        // Control: exactly Int.MaxValue elements is the largest legal scala.Range and
        // already works — proves the boundary is > Int.MaxValue, not >= .
        test("range_exactlyIntMaxElements_take2_works [AdversarialRangeOverflowSpec]") {
          assertTrue(Stream.range(0, Int.MaxValue).take(2).runCollect == Right(Chunk(0, 1)))
        }
      ),
      suite("AdversarialTakeDropOrderSpec")(
        test("take(4).drop(2) keeps positions [2,4) — fromChunk [AdversarialTakeDropOrderSpec]") {
          val s = Stream(0, 1, 2, 3, 4, 5)
          assertTrue(s.take(4).drop(2).runCollect == Right(Chunk(2, 3)))
        },
        test("take(4).drop(2) keeps positions [2,4) — range [AdversarialTakeDropOrderSpec]") {
          val s = Stream.range(0, 6)
          assertTrue(s.take(4).drop(2).runCollect == Right(Chunk(2, 3)))
        },
        test("take(2).drop(4) is empty (drop exceeds limited length) [AdversarialTakeDropOrderSpec]") {
          // take(2) = [0,1]; dropping 4 of a 2-element stream yields empty.
          val s = Stream(0, 1, 2, 3, 4, 5)
          assertTrue(s.take(2).drop(4).runCollect == Right(Chunk.empty[Int]))
        },
        test("take(3).drop(1) keeps positions [1,3) — fromIterable [AdversarialTakeDropOrderSpec]") {
          val s = Stream.fromIterable(List(10, 20, 30, 40, 50))
          assertTrue(s.take(3).drop(1).runCollect == Right(Chunk(20, 30)))
        },
        // Control: drop(m).take(n) (the order that is natively supported) is correct.
        test("control: drop(2).take(2) keeps positions [2,4) [AdversarialTakeDropOrderSpec]") {
          val s = Stream(0, 1, 2, 3, 4, 5)
          assertTrue(s.drop(2).take(2).runCollect == Right(Chunk(2, 3)))
        }
      ),
      suite("AdversarialTakePreallocSpec")(
        test("take_hugeN_shortRefStream_doesNotOverAllocate [AdversarialTakePreallocSpec]") {
          val s   = Stream.fromIterable(List("a", "b", "c"))
          val got = guarded(s.run(Sink.take[String](Int.MaxValue)))
          assertTrue(got == Right(Right(Chunk("a", "b", "c"))))
        },
        test("grouped_hugeN_shortRefStream_doesNotOverAllocate [AdversarialTakePreallocSpec]") {
          val s   = Stream.fromIterable(List("a", "b", "c"))
          val got = guarded(s.grouped(Int.MaxValue).runCollect)
          assertTrue(got == Right(Right(Chunk(Chunk("a", "b", "c")))))
        }
      ),
      // BUG: `CatchAllReader.readUpToN` / `CatchDefectReader.readUpToN` delegate
      // the WHOLE bulk read to the upstream reader and only then recover. When
      // the upstream's `readUpToN` buffers some elements and the error fires
      // mid-chunk (any reader whose bulk read is a read loop, e.g. MappedInt),
      // the buffered prefix is discarded and recovery replaces it — the same
      // stream observed element-by-element (`read`/`runCollect`) keeps the
      // prefix. Silent data loss + representation dependence: two public
      // consumption paths over one stream must agree on the elements.
      suite("AdversarialReadUpToNRecoverySpec")(
        test(
          "catchAll: bulk readUpToN keeps elements buffered before the typed error [AdversarialReadUpToNRecoverySpec]"
        ) {
          def make: Stream[Nothing, Int] =
            (Stream(1, 2) ++ Stream.fail("boom")).map(_ * 10).catchAll(_ => Stream(99))
          val viaRead = make.runCollect
          val viaBulk = make.run(Sink.create[Nothing, Int, Chunk[Int]] { r =>
            var acc      = Chunk.empty[Int]
            var continue = true
            while (continue) {
              val c = r.readUpToN[Int](8)
              if (c.isEmpty) continue = false else acc = acc ++ c
            }
            acc
          })
          assertTrue(
            viaRead == Right(Chunk(10, 20, 99)),
            viaBulk == viaRead
          )
        },
        test(
          "catchDefect: bulk readUpToN keeps elements buffered before the defect [AdversarialReadUpToNRecoverySpec]"
        ) {
          def make: Stream[Nothing, Int] =
            Stream(1, 2, 3)
              .map(x => if (x == 3) throw new RuntimeException("defect") else x)
              .catchDefect { case e if e.getMessage == "defect" => Stream(99) }
          val viaRead = make.runCollect
          val viaBulk = make.run(Sink.create[Nothing, Int, Chunk[Int]] { r =>
            var acc      = Chunk.empty[Int]
            var continue = true
            while (continue) {
              val c = r.readUpToN[Int](8)
              if (c.isEmpty) continue = false else acc = acc ++ c
            }
            acc
          })
          assertTrue(
            viaRead == Right(Chunk(1, 2, 99)),
            viaBulk == viaRead
          )
        },
        // Control: when the error lands exactly on a chunk boundary (segment
        // transition), no prefix is in flight and both paths already agree.
        test("control: error at a segment boundary agrees across read paths [AdversarialReadUpToNRecoverySpec]") {
          def make: Stream[Nothing, Int] =
            (Stream(1, 2) ++ Stream.fail("boom")).catchAll(_ => Stream(99))
          val viaRead = make.runCollect
          val viaBulk = make.run(Sink.create[Nothing, Int, Chunk[Int]] { r =>
            var acc      = Chunk.empty[Int]
            var continue = true
            while (continue) {
              val c = r.readUpToN[Int](8)
              if (c.isEmpty) continue = false else acc = acc ++ c
            }
            acc
          })
          assertTrue(
            viaRead == Right(Chunk(1, 2, 99)),
            viaBulk == viaRead
          )
        }
      )
    ),
    run7ConvergenceSuite,
    run10ConvergenceSuite,
    run11ConvergenceSuite
  )

  // ---- Run #10 convergence probes ------------------------------------------
  // Passing adversarial probes from the tenth hardening round: randomized
  // differential pipelines vs the List oracle, deep-vs-shallow compilation
  // parity, in-band sentinel (Long.MaxValue/Double.MaxValue) compositions,
  // hostile callbacks (functions throwing on the Nth call) with exactly-once
  // finalization, repeated/fixpoint restarts of stateful operators, lifecycle
  // leak-tracking in zip/catchAll/flatMap, singleton lanes, null elements, and
  // fromJavaReader buffering edges. Committed as convergence evidence.
  private case class Run10Boom(n: Int) extends RuntimeException(s"boom-$n")

  private def run10Deep[A](s: Stream[Nothing, A])(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    (0 until 101).foldLeft(s)((acc, _) => acc.map((a: A) => a))

  private def run10Collect[A](s: Stream[Nothing, A]): Chunk[A] =
    s.runCollect.fold(e => throw new AssertionError(s"unexpected Left($e)"), identity)

  private def run10Tracked(elems: Int*): (AtomicInteger, Stream[Nothing, Int]) = {
    val closes = new AtomicInteger(0)
    val s      = Stream.fromReader[Nothing, Int] {
      val inner = Reader.fromChunk(Chunk.fromIterable(elems.toList))
      new Reader.DelegatingReader[Int](inner) {
        override def close(): Unit = { closes.incrementAndGet(); inner.close() }
      }
    }
    (closes, s)
  }

  /**
   * Stream/List dual interpretation of a small op algebra for differential
   * testing.
   */
  sealed private trait Run10Op
  private object Run10Op {
    final case class MapAdd(k: Int)      extends Run10Op
    final case class FilterMod(m: Int)   extends Run10Op
    final case class Take(n: Int)        extends Run10Op
    final case class Drop(n: Int)        extends Run10Op
    final case class TakeWhileLt(b: Int) extends Run10Op
    final case class ScanSum()           extends Run10Op

    val gen: Gen[Any, Run10Op] = Gen.oneOf(
      Gen.int(-3, 3).map(MapAdd(_)),
      Gen.int(2, 4).map(FilterMod(_)),
      Gen.int(0, 12).map(Take(_)),
      Gen.int(0, 6).map(Drop(_)),
      Gen.int(-5, 40).map(TakeWhileLt(_)),
      Gen.const(ScanSum())
    )

    def applyStream(s: Stream[Nothing, Int], op: Run10Op): Stream[Nothing, Int] = op match {
      case MapAdd(k)      => s.map(_ + k)
      case FilterMod(m)   => s.filter(x => math.abs(x % m) != 1)
      case Take(n)        => s.take(n.toLong)
      case Drop(n)        => s.drop(n.toLong)
      case TakeWhileLt(b) => s.takeWhile(_ < b)
      case ScanSum()      => s.scan(0)(_ + _)
    }

    def applyList(xs: List[Int], op: Run10Op): List[Int] = op match {
      case MapAdd(k)      => xs.map(_ + k)
      case FilterMod(m)   => xs.filter(x => math.abs(x % m) != 1)
      case Take(n)        => xs.take(n)
      case Drop(n)        => xs.drop(n)
      case TakeWhileLt(b) => xs.takeWhile(_ < b)
      case ScanSum()      => xs.scanLeft(0)(_ + _)
    }
  }

  private def run10ConvergenceSuite = suite("run10 convergence")(
    suite("randomized differential vs List oracle")(
      test("randomOpPipelines_overChunkAndRange_matchListOracle") {
        check(StreamsGen.genChunk(Gen.int(-50, 50)), Gen.listOfBounded(1, 5)(Run10Op.gen)) { (chunk, ops) =>
          val xs         = chunk.toList
          val fromChunkS = ops.foldLeft(Stream.fromChunk(chunk): Stream[Nothing, Int])(Run10Op.applyStream)
          val oracle     = ops.foldLeft(xs)(Run10Op.applyList)
          assertTrue(run10Collect(fromChunkS).toList == oracle)
        }
      },
      test("randomOpPipelines_overNativePushdownRange_matchListOracle") {
        check(Gen.int(0, 30), Gen.listOfBounded(1, 5)(Run10Op.gen)) { (n, ops) =>
          val streamed = ops.foldLeft(Stream.range(0, n): Stream[Nothing, Int])(Run10Op.applyStream)
          val oracle   = ops.foldLeft((0 until n).toList)(Run10Op.applyList)
          assertTrue(run10Collect(streamed).toList == oracle)
        }
      },
      test("randomOpPipelines_consumedViaChunked_matchGroupedListOracle") {
        check(Gen.int(0, 25), Gen.listOfBounded(1, 4)(Run10Op.gen)) { (n, ops) =>
          val streamed = ops.foldLeft(Stream.range(0, n): Stream[Nothing, Int])(Run10Op.applyStream).chunked(3)
          val oracle   = ops.foldLeft((0 until n).toList)(Run10Op.applyList).grouped(3).toList
          assertTrue(run10Collect(streamed).map(_.toList).toList == oracle)
        }
      }
    ),
    suite("deep-vs-shallow compilation parity")(
      test("mapFilterTakeDropMap_deepEqualsShallowEqualsOracle") {
        val xs                            = (0 until 50).toList
        def pipe(s: Stream[Nothing, Int]) = s.map(_ * 3).filter(_ % 2 == 0).take(12).drop(4).map(_ + 1)
        val oracle                        = xs.map(_ * 3).filter(_ % 2 == 0).take(12).drop(4).map(_ + 1)
        assertTrue(
          run10Collect(pipe(Stream.fromIterable(xs))).toList == oracle,
          run10Collect(pipe(Stream.range(0, 50))).toList == oracle,
          run10Collect(run10Deep(pipe(Stream.range(0, 50)))).toList == oracle
        )
      },
      test("dropTakeChains_composeInInvocationOrder_acrossSources") {
        val xs                            = (0 until 30).toList
        def pipe(s: Stream[Nothing, Int]) = s.drop(3).take(20).drop(2).take(5).drop(1)
        val oracle                        = xs.drop(3).take(20).drop(2).take(5).drop(1)
        assertTrue(
          run10Collect(pipe(Stream.fromIterable(xs))).toList == oracle,
          run10Collect(pipe(Stream.range(0, 30))).toList == oracle,
          run10Collect(pipe(Stream.fromChunk(Chunk.fromIterable(xs)))).toList == oracle,
          run10Collect(run10Deep(pipe(Stream.range(0, 30)))).toList == oracle
        )
      },
      test("scanDropTake_deepEqualsShallowEqualsOracle") {
        val s      = Stream.range(0, 10).scan(0)(_ + _).drop(2).take(4)
        val oracle = (0 until 10).toList.scanLeft(0)(_ + _).drop(2).take(4)
        assertTrue(run10Collect(s).toList == oracle, run10Collect(run10Deep(s)).toList == oracle)
      },
      test("charLane_throughDeepInterpreter_roundTrips") {
        val chars = List('a', 'b', 'c')
        val s     = Stream.fromIterable(chars).map(_.toInt).map(i => (i + 1).toChar)
        assertTrue(
          run10Collect(s).toList == chars.map(c => (c.toInt + 1).toChar),
          run10Collect(run10Deep(s)).toList == chars.map(c => (c.toInt + 1).toChar)
        )
      }
    ),
    suite("in-band sentinel compositions")(
      test("longSentinel_filterTakeWhileMap_shallowAndDeep") {
        val xs                             = List(1L, Long.MaxValue, 2L, Long.MaxValue, 3L)
        def pipe(s: Stream[Nothing, Long]) = s.filter(_ != 2L).takeWhile(_ >= 1L).map(identity)
        val oracle                         = xs.filter(_ != 2L).takeWhile(_ >= 1L)
        assertTrue(
          run10Collect(pipe(Stream.fromChunk(Chunk.fromIterable(xs)))).toList == oracle,
          run10Collect(run10Deep(pipe(Stream.fromChunk(Chunk.fromIterable(xs))))).toList == oracle
        )
      },
      test("longSentinel_scanAccumulatesThroughMaxValue") {
        val out    = run10Collect(Stream(Long.MaxValue - 1L, 1L, 5L).scan(0L)(_ + _))
        val oracle = List(Long.MaxValue - 1L, 1L, 5L).scanLeft(0L)(_ + _)
        assertTrue(out.toList == oracle)
      },
      test("longSentinel_chunkedIntersperseZip_lossless") {
        val xs      = List(Long.MaxValue, 1L, Long.MaxValue)
        val chunked = run10Collect(Stream.fromChunk(Chunk.fromIterable(xs)).chunked(2)).map(_.toList).toList
        val inter   = run10Collect(Stream.fromChunk(Chunk.fromIterable(xs)).intersperse(0L)).toList
        val zipped  = (Stream.fromChunk(Chunk.fromIterable(xs)) && Stream.range(0, 5)).runCollect
        assertTrue(
          chunked == List(List(Long.MaxValue, 1L), List(Long.MaxValue)),
          inter == List(Long.MaxValue, 0L, 1L, 0L, Long.MaxValue),
          zipped.map(_.toList) == Right(List((Long.MaxValue, 0), (1L, 1), (Long.MaxValue, 2)))
        )
      },
      test("longSentinel_sinkMatrix_headLastTakeFindExistsCount") {
        def s = Stream(Long.MaxValue)
        assertTrue(
          s.head == Right(Some(Long.MaxValue)),
          s.last == Right(Some(Long.MaxValue)),
          s.run(Sink.take[Long](5)).map(_.toList) == Right(List(Long.MaxValue)),
          s.find(_ == Long.MaxValue) == Right(Some(Long.MaxValue)),
          s.exists(_ == Long.MaxValue) == Right(true),
          s.count == Right(1L)
        )
      },
      test("doubleSentinel_slidingWindowsOverMaxValue") {
        val xs  = List(1.0, Double.MaxValue, 2.0)
        val out = run10Collect(Stream.fromChunk(Chunk.fromIterable(xs)).sliding(2)).map(_.toList)
        assertTrue(out.toList == xs.sliding(2).toList)
      },
      test("longSentinel_singletonSucceedMaxValue_throughMapRepeatedConcat") {
        assertTrue(
          run10Collect(Stream.succeed(Long.MaxValue).map(identity)).toList == List(Long.MaxValue),
          run10Collect(Stream.succeed(Long.MaxValue).repeated.take(3L)).toList ==
            List(Long.MaxValue, Long.MaxValue, Long.MaxValue),
          run10Collect((Stream.succeed(Long.MaxValue) ++ Stream.succeed(1L)).take(2L)).toList ==
            List(Long.MaxValue, 1L)
        )
      }
    ),
    suite("hostile callbacks: exactly-once finalization")(
      test("filterPredThrowsOnNthCall_ensuringFiresOnce_defectPropagates") {
        val closes = new AtomicInteger(0)
        val calls  = new AtomicInteger(0)
        val s      = Stream
          .range(0, 10)
          .ensuring(closes.incrementAndGet())
          .filter(_ => if (calls.incrementAndGet() == 3) throw Run10Boom(1) else true)
        val r =
          try { s.runCollect; "no-throw" }
          catch { case Run10Boom(1) => "boom" }
        assertTrue(r == "boom", closes.get() == 1)
      },
      test("takeWhilePredThrows_underCatchDefect_prefixPreservedRecoveryAppended") {
        val s = Stream
          .range(0, 10)
          .takeWhile(i => if (i == 3) throw Run10Boom(2) else true)
          .catchDefect { case Run10Boom(2) => Stream(100, 101) }
        assertTrue(run10Collect(s).toList == List(0, 1, 2, 100, 101))
      },
      test("mapAccumAndDistinctByThrow_ensuringFiresOnce") {
        val closes1 = new AtomicInteger(0)
        val s1      = Stream
          .range(0, 5)
          .ensuring(closes1.incrementAndGet())
          .mapAccum(0)((acc, a) => if (a == 2) throw Run10Boom(3) else (acc + a, acc + a))
        val r1 =
          try { s1.runCollect; "no-throw" }
          catch { case Run10Boom(3) => "boom" }
        val closes2 = new AtomicInteger(0)
        val s2      = Stream
          .range(0, 5)
          .ensuring(closes2.incrementAndGet())
          .distinctBy(i => if (i == 2) throw Run10Boom(4) else i)
        val r2 =
          try { s2.runCollect; "no-throw" }
          catch { case Run10Boom(4) => "boom" }
        assertTrue(r1 == "boom", closes1.get() == 1, r2 == "boom", closes2.get() == 1)
      },
      test("scanFThrows_defectPropagatesThroughDeepPathToo") {
        def s(src: Stream[Nothing, Int]) =
          src.scan(0)((acc, a) => if (a == 2) throw Run10Boom(7) else acc + a)
        def outcome(st: Stream[Nothing, Int]): String =
          try { st.runCollect; "no-throw" }
          catch { case Run10Boom(7) => "boom" }
        assertTrue(
          outcome(s(Stream.range(0, 5))) == "boom",
          outcome(run10Deep(s(Stream.range(0, 5)))) == "boom"
        )
      },
      test("fromIteratorHasNextThrows_isRecoverableDefect") {
        val it = new Iterator[Int] {
          private var n        = 0
          def hasNext: Boolean = if (n >= 2) throw Run10Boom(32) else true
          def next(): Int      = { n += 1; n }
        }
        val s = Stream.fromIterator(it).catchDefect { case Run10Boom(32) => Stream(99) }
        assertTrue(run10Collect(s).toList == List(1, 2, 99))
      }
    ),
    suite("repeated/fixpoint restarts of stateful operators")(
      test("emptyMapAccumRepeated_terminatesAsEmpty") {
        assertTrue(run10Collect(Stream.empty.mapAccum(0)((s, a: Int) => (s, a)).repeated.take(5)).isEmpty)
      },
      test("scanOverEmpty_emitsInit_andRepeatedReEmitsInitPerCycle") {
        assertTrue(
          run10Collect(Stream.empty.scan(7)((s, _: Int) => s)).toList == List(7),
          run10Collect(Stream.empty.scan(7)((s, _: Int) => s).repeated.take(3)).toList == List(7, 7, 7)
        )
      },
      test("statefulOperators_restartCleanlyUnderRepeated") {
        val mapAccum = Stream.range(1, 4).mapAccum(0)((acc, a) => (acc + a, acc + a)).repeated.take(8)
        val inter    = Stream(1, 2).intersperse(0).repeated.take(7)
        val chunked  = Stream(1, 2, 3).chunked(2).repeated.take(4)
        val sliding  = Stream(1, 2, 3).sliding(2).repeated.take(5)
        assertTrue(
          run10Collect(mapAccum).toList == List(1, 3, 6, 1, 3, 6, 1, 3),
          run10Collect(inter).toList == List(1, 0, 2, 1, 0, 2, 1),
          run10Collect(chunked).map(_.toList).toList == List(List(1, 2), List(3), List(1, 2), List(3)),
          run10Collect(sliding).map(_.toList).toList ==
            List(List(1, 2), List(2, 3), List(1, 2), List(2, 3), List(1, 2))
        )
      },
      test("zipRepeated_restartsBothSides") {
        val out = (Stream(1, 2) && Stream(10, 20)).repeated.take(5).runCollect.map(_.toList)
        assertTrue(out == Right(List((1, 10), (2, 20), (1, 10), (2, 20), (1, 10))))
      },
      test("takeAndDropInsideRepeated_reapplyEachCycle") {
        assertTrue(
          run10Collect(Stream(1, 2, 3).take(2).repeated.take(5)).toList == List(1, 2, 1, 2, 1),
          run10Collect(Stream(1, 2, 3).drop(1).repeated.take(5)).toList == List(2, 3, 2, 3, 2)
        )
      },
      test("mapErrorUnderRepeated_errorFromSecondCycleStillRouted") {
        var cycle = 0
        val src   = Stream.fromIterator[Int] {
          cycle += 1
          if (cycle < 2) Iterator(1) else throw Run10Boom(20)
        }
        val s = src.repeated.take(5)
        val r =
          try { s.runCollect; "no-throw" }
          catch { case Run10Boom(20) => "boom" }
        assertTrue(r == "boom")
      }
    ),
    suite("lifecycle leak-tracking in composition")(
      test("zip_rightSideCompileFailure_closesLeftExactlyOnce") {
        val (closes, left) = run10Tracked(1, 2, 3)
        val right          = Stream.fromReader[Nothing, Int](throw Run10Boom(30))
        val s              = left && right
        val r              =
          try { s.runCollect; "no-throw" }
          catch { case Run10Boom(30) => "boom" }
        assertTrue(r == "boom", closes.get() == 1)
      },
      test("zip_shorterRightEagerlyClosesLeftExactlyOnceOverall") {
        val (lCloses, left)  = run10Tracked(1, 2, 3)
        val (rCloses, right) = run10Tracked(10)
        val res              = (left && right).runCollect
        assertTrue(res.map(_.toList) == Right(List((1, 10))), lCloses.get() == 1, rCloses.get() == 1)
      },
      test("catchAll_failingRecoveryConstruction_attachesHandledErrorAsSuppressed") {
        val (closes, src) = run10Tracked(1, 2)
        val failing       = src ++ Stream.fail("orig")
        val s             = failing.catchAll((_: String) => throw Run10Boom(31))
        val r             =
          try { s.runCollect; null }
          catch { case b: Run10Boom => b }
        assertTrue(
          r == Run10Boom(31),
          r.getSuppressed.exists(_.isInstanceOf[StreamError]),
          closes.get() == 1
        )
      },
      test("catchAll_recoveryReaderClosedExactlyOnceOnCleanRun") {
        val (rcCloses, recovery) = run10Tracked(100, 101)
        val s                    = (Stream(1) ++ Stream.fail("e")).catchAll((_: String) => recovery)
        val res                  = s.runCollect
        assertTrue(res.map(_.toList) == Right(List(1, 100, 101)), rcCloses.get() == 1)
      },
      test("flatMap_everyInnerReaderClosedExactlyOnce_inclEarlyTake") {
        val closes = new AtomicInteger(0)
        val s      = Stream
          .range(0, 5)
          .flatMap { i =>
            Stream.fromReader[Nothing, Int] {
              val inner = Reader.fromChunk(Chunk.fromIterable(List(i, i * 10)))
              new Reader.DelegatingReader[Int](inner) {
                override def close(): Unit = { closes.incrementAndGet(); inner.close() }
              }
            }
          }
          .take(3L)
        val res = s.runCollect
        assertTrue(res.map(_.toList) == Right(List(0, 0, 1)), closes.get() == 2)
      },
      test("takeZero_stillReleasesAcquiredResourceExactlyOnce") {
        val released = new AtomicInteger(0)
        val s        = Stream.fromAcquireRelease((), (_: Unit) => released.incrementAndGet())(_ => Stream.range(0, 10))
        val res      = s.take(0L).runCollect
        assertTrue(res.map(_.toList) == Right(Nil), released.get() == 1)
      },
      test("ensuringFinalizerDefect_winsOverInFlightTypedError_carrierSuppressed") {
        val s = (Stream(1) ++ Stream.fail("typed")).ensuring(throw Run10Boom(50))
        val r =
          try { s.runCollect; null }
          catch { case b: Run10Boom => b }
        assertTrue(r == Run10Boom(50), r.getSuppressed.exists(_.isInstanceOf[StreamError]))
      },
      test("nestedEnsuring_outerStillFiresWhenInnerFinalizerThrows") {
        val outer = new AtomicInteger(0)
        val s     = Stream(1).ensuring(throw Run10Boom(52)).ensuring(outer.incrementAndGet())
        val r     =
          try { s.runCollect; "no-throw" }
          catch { case Run10Boom(52) => "boom" }
        assertTrue(r == "boom", outer.get() == 1)
      }
    ),
    suite("singleton lanes / null elements / lane bridges")(
      test("repeatAndSucceed_overEveryPrimitiveLane") {
        assertTrue(
          run10Collect(Stream.repeat(7).take(3L)).toList == List(7, 7, 7),
          run10Collect(Stream.repeat(7L).take(3L)).toList == List(7L, 7L, 7L),
          run10Collect(Stream.repeat(1.5).take(2L)).toList == List(1.5, 1.5),
          run10Collect(Stream.repeat(1.5f).take(2L)).toList == List(1.5f, 1.5f),
          run10Collect(Stream.repeat(true).take(2L)).toList == List(true, true),
          run10Collect(Stream.repeat('z').take(2L)).toList == List('z', 'z'),
          run10Collect(Stream.repeat(3.toShort).take(2L)).toList == List(3.toShort, 3.toShort),
          run10Collect(Stream.repeat("s").take(2L)).toList == List("s", "s"),
          run10Collect(Stream.succeed(false)).toList == List(false),
          run10Collect(Stream.succeed('q')).toList == List('q'),
          run10Collect(Stream.succeed(4.toShort)).toList == List(4.toShort),
          run10Collect(Stream.succeed(9).repeated.take(4L)).toList == List(9, 9, 9, 9)
        )
      },
      test("nullElements_surviveMapFilterTakeIntersperseDistinctAndSinks") {
        val xs = List("a", null, "b")
        val s  = Stream.fromIterable(List[String](null, "z"))
        assertTrue(
          run10Collect(Stream.fromIterable(xs)).toList == xs,
          run10Collect(Stream.fromIterable(xs).filter(_ ne null)).toList == List("a", "b"),
          run10Collect(Stream.fromIterable(xs).take(2L)).toList == List("a", null),
          run10Collect(Stream.fromIterable(List("a", null, null, "b")).distinct).toList == List("a", null, "b"),
          run10Collect(Stream[String]("x", "y").intersperse(null.asInstanceOf[String])).toList ==
            List("x", null, "y"),
          s.head == Right(Some(null)),
          s.last == Right(Some("z")),
          s.find(_ eq null) == Right(Some(null))
        )
      },
      test("charBooleanShortLanes_filterMapTake_matchListOracle") {
        val chars  = List('a', 'b', 'c', 'd')
        val bools  = List(true, false, true, true)
        val shorts = List[Short](1, 2, 3, 4)
        assertTrue(
          run10Collect(Stream.fromIterable(chars).filter(_ != 'b').map(_.toUpper).take(2)).toList ==
            chars.filter(_ != 'b').map(_.toUpper).take(2),
          run10Collect(Stream.fromIterable(bools).filter(identity).take(2)).toList ==
            bools.filter(identity).take(2),
          run10Collect(Stream.fromIterable(shorts).map(s => (s + 1).toShort).filter(_ > 2).take(2)).toList ==
            shorts.map(s => (s + 1).toShort).filter(_ > 2).take(2)
        )
      },
      test("threeWayZip_flattensTuplesAndMatchesOracle") {
        val res = (Stream(1, 2, 3) && Stream("a", "b") && Stream(9.5, 8.5, 7.5)).runCollect
        assertTrue(res.map(_.toList) == Right(List((1, "a", 9.5), (2, "b", 8.5))))
      }
    ),
    suite("window extremes / fromJavaReader buffering edges")(
      test("takeDropExtremes_negativeAndLongMaxValue") {
        val xs = (0 until 5).toList
        assertTrue(
          run10Collect(Stream.fromIterable(xs).take(-1L)).isEmpty,
          run10Collect(Stream.fromIterable(xs).drop(-1L)).toList == xs,
          run10Collect(Stream.fromIterable(xs).take(Long.MaxValue)).toList == xs,
          run10Collect(Stream.fromIterable(xs).drop(Long.MaxValue)).isEmpty,
          run10Collect(Stream.fromChunk(Chunk.fromIterable(xs)).take(-1L)).isEmpty,
          run10Collect(Stream.fromChunk(Chunk.fromIterable(xs)).drop(-1L)).toList == xs,
          run10Collect(Stream.range(0, 5).take(-1L)).isEmpty,
          run10Collect(Stream.range(0, 5).drop(-1L)).toList == xs
        )
      },
      test("hugeLazyRange_dropTakeWindowsStayLazyAndExact") {
        val s = Stream.range(Int.MinValue, Int.MaxValue).drop(5L).take(3L)
        assertTrue(run10Collect(s).toList == List(Int.MinValue + 5, Int.MinValue + 6, Int.MinValue + 7))
      },
      test("fromJavaReaderUnmanagedWithBuffer_prefixThenTypedErrorSurfaces") {
        val good    = new java.io.StringReader("abc")
        val out1    = Stream.fromJavaReaderUnmanaged(good).buffer(2).runCollect.map(_.toList)
        val failing = new java.io.Reader {
          private var i                                        = 0
          def read(cbuf: Array[Char], off: Int, len: Int): Int = throw new java.io.IOException("nope")
          override def read(): Int                             =
            if (i < 2) { i += 1; 'x'.toInt }
            else throw new java.io.IOException("mid-fail")
          def close(): Unit = ()
        }
        val out2 = Stream.fromJavaReaderUnmanaged(failing).buffer(4).runCollect
        assertTrue(
          out1 == Right(List('a', 'b', 'c')),
          out2.left.toOption.map(_.getMessage) == Some("mid-fail")
        )
      },
      test("fromJavaReaderManaged_closesUnderlyingExactlyOnceOnError") {
        val closes  = new AtomicInteger(0)
        val failing = new java.io.Reader {
          def read(cbuf: Array[Char], off: Int, len: Int): Int = throw new java.io.IOException("r")
          override def read(): Int                             = throw new java.io.IOException("r")
          def close(): Unit                                    = { closes.incrementAndGet(); () }
        }
        val res = Stream.fromJavaReader(failing).runCollect
        assertTrue(res.left.toOption.map(_.getMessage) == Some("r"), closes.get() == 1)
      },
      test("charReaderWithFalseReady_readableGatedConsumptionDropsNothing") {
        val sneaky = new java.io.Reader {
          private val data                                     = "xyz".toCharArray
          private var i                                        = 0
          override def ready(): Boolean                        = false
          def read(cbuf: Array[Char], off: Int, len: Int): Int =
            if (i >= data.length) -1
            else { cbuf(off) = data(i); i += 1; 1 }
          override def read(): Int = if (i >= data.length) -1 else { val c = data(i); i += 1; c.toInt }
          def close(): Unit        = ()
        }
        val out = Stream.fromJavaReaderUnmanaged(sneaky).runCollect.map(_.toList)
        assertTrue(out == Right(List('x', 'y', 'z')))
      }
    )
  )

  // ---- Run #7 convergence probes -------------------------------------------
  // Passing adversarial probes locking in behavior examined during the seventh
  // hardening round (knownLength propagation, readN-on-wrapper semantics,
  // side-effecting constructors). Committed as convergence evidence.
  private def run7ConvergenceSuite = suite("run7 convergence")(
    test("knownLength_combinatorMatrix_matchesActualCount [AdversarialRun7ConvergenceSpec]") {
      assertTrue(
        checkLen(Stream(1, 2, 3).take(2)),
        checkLen(Stream(1, 2, 3).take(0)),
        checkLen(Stream(1, 2, 3).take(-1)),
        checkLen(Stream(1, 2, 3).drop(2)),
        checkLen(Stream(1, 2, 3).drop(5)),
        checkLen(Stream(1, 2, 3).drop(-1)),
        checkLen(Stream(1, 2) ++ Stream(3)),
        checkLen(Stream(1, 2, 3).map(_ + 1)),
        checkLen(Stream(1, 2, 3).ensuring(())),
        checkLen(Stream.range(0, 5).take(3).drop(1)),
        checkLen(Stream.fromIterable(List(1, 2, 3)).drop(1))
      )
    },
    test("chunked_overZip_buildsChunksAcrossPulls [AdversarialRun7ConvergenceSpec]") {
      val r = (Stream(1, 2, 3) && Stream(4, 5, 6, 7)).chunked(2).runCollect
      assertTrue(r == Right(Chunk(Chunk((1, 4), (2, 5)), Chunk((3, 6)))))
    },
    test("chunked_overStatefulOperators_matchesElementWiseSemantics [AdversarialRun7ConvergenceSpec]") {
      val viaIntersperse = Stream(1, 2, 3).intersperse(0).chunked(2).runCollect
      val viaScan        = Stream(1, 2, 3).scan(0)(_ + _).chunked(2).runCollect
      val viaDistinct    = Stream(1, 1, 2, 3, 3).distinct.chunked(2).runCollect
      val viaMapAccum    = Stream(1, 2, 3).mapAccum(0)((s, a) => (s + a, s + a)).chunked(2).runCollect
      assertTrue(
        viaIntersperse == Right(Chunk(Chunk(1, 0), Chunk(2, 0), Chunk(3))),
        viaScan == Right(Chunk(Chunk(0, 1), Chunk(3, 6))),
        viaDistinct == Right(Chunk(Chunk(1, 2), Chunk(3))),
        viaMapAccum == Right(Chunk(Chunk(1, 3), Chunk(6)))
      )
    },
    test("defer_finalizerRunsOnce_evenWhenNoElementPulled [AdversarialRun7ConvergenceSpec]") {
      val count = new AtomicInteger(0)
      val r     = Stream.defer(count.incrementAndGet()).take(0).runCollect
      assertTrue(r == Right(Chunk.empty), count.get() == 1)
    },
    test("attemptEval_capturesThrowableAsTypedError [AdversarialRun7ConvergenceSpec]") {
      val boom = new RuntimeException("attempt-eval-boom")
      val r    = Stream.attemptEval(throw boom).runCollect
      assertTrue(r == Left(boom))
    }
  )

  // ---- Run #11 convergence probes ------------------------------------------
  // Eleventh (convergence-verification) round, attacked with angles fresh
  // relative to run 10: an EXTENDED randomized differential op algebra
  // (distinct/distinctBy/intersperse/mapAccum/collect/flatMap/concat mixed into
  // the run-10 core), zip-composed differential pipelines, randomized Long-lane
  // pipelines whose ELEMENTS include the in-band Long.MaxValue EOF sentinel,
  // user-supplied minimal-but-lawful Reader SPI implementations driven through
  // library combinators (parametricity: the library must hold its contracts for
  // any lawful implementation, not just its own), and interpreter
  // segment-boundary pipelines sized at SegmentBudget ± 1 (7999/8000/8001 ops,
  // incl. repeated/reset cascading across the chained-segment boundary).
  // Committed as convergence evidence.

  /**
   * Minimal-but-lawful boxed Reader SPI implementation (default AnyRef lane).
   */
  private final class Run11BoxedReader[A](elems: Vector[A], supportsReset: Boolean = false) extends Reader[A] {
    private var idx                     = 0
    private var closed                  = false
    def isClosed: Boolean               = closed || idx >= elems.length
    def read[A1 >: A](sentinel: A1): A1 =
      if (closed || idx >= elems.length) sentinel
      else { val v: A1 = elems(idx); idx += 1; v }
    def close(): Unit          = closed = true
    override def reset(): Unit = if (supportsReset) { idx = 0; closed = false }
    else super.reset()
  }

  /**
   * Minimal-but-lawful SPI reader that ADVERTISES the Long lane but implements
   * only the boxed `read`. The base-class specialized defaults must keep it
   * correct — including `lastReadWasEOF` disambiguation when a real element
   * equals the in-band `Long.MaxValue` EOF sentinel.
   */
  private final class Run11LongTaggedReader(elems: Vector[Long]) extends Reader[Long] {
    private var idx                        = 0
    private var closed                     = false
    override def jvmType: JvmType          = JvmType.Long
    def isClosed: Boolean                  = closed || idx >= elems.length
    def read[A1 >: Long](sentinel: A1): A1 =
      if (closed || idx >= elems.length) sentinel
      else { val v: A1 = elems(idx); idx += 1; v }
    def close(): Unit = closed = true
  }

  private def run11Collect[A](s: Stream[Nothing, A]): Chunk[A] =
    s.runCollect.fold(e => throw new AssertionError(s"unexpected Left($e)"), identity)

  private def run11Deep[A](s: Stream[Nothing, A])(implicit jt: JvmType.Infer[A]): Stream[Nothing, A] =
    (0 until 101).foldLeft(s)((acc, _) => acc.map((a: A) => a))

  private def run11Intersperse[A](xs: List[A], sep: A): List[A] =
    if (xs.isEmpty) xs else xs.flatMap(x => List(sep, x)).tail

  /** Extended Stream/List dual op algebra (supersets the run-10 algebra). */
  sealed private trait Run11Op
  private object Run11Op {
    final case class MapAdd(k: Int)        extends Run11Op
    final case class FilterMod(m: Int)     extends Run11Op
    final case class Take(n: Int)          extends Run11Op
    final case class Drop(n: Int)          extends Run11Op
    final case class TakeWhileLt(b: Int)   extends Run11Op
    final case class ScanSum()             extends Run11Op
    final case class MapAccumMax()         extends Run11Op
    final case class Distinct()            extends Run11Op
    final case class DistinctByMod(m: Int) extends Run11Op
    final case class Intersperse(sep: Int) extends Run11Op
    final case class CollectEvenHalf()     extends Run11Op
    final case class FlatMapDup()          extends Run11Op
    final case class ConcatTail(t: Int)    extends Run11Op

    val gen: Gen[Any, Run11Op] = Gen.oneOf(
      Gen.int(-3, 3).map(MapAdd(_)),
      Gen.int(2, 4).map(FilterMod(_)),
      Gen.int(0, 12).map(Take(_)),
      Gen.int(0, 6).map(Drop(_)),
      Gen.int(-5, 40).map(TakeWhileLt(_)),
      Gen.const(ScanSum()),
      Gen.const(MapAccumMax()),
      Gen.const(Distinct()),
      Gen.int(2, 5).map(DistinctByMod(_)),
      Gen.int(-1, 1).map(Intersperse(_)),
      Gen.const(CollectEvenHalf()),
      Gen.const(FlatMapDup()),
      Gen.int(90, 92).map(ConcatTail(_))
    )

    def applyStream(s: Stream[Nothing, Int], op: Run11Op): Stream[Nothing, Int] = op match {
      case MapAdd(k)      => s.map(_ + k)
      case FilterMod(m)   => s.filter(x => math.abs(x % m) != 1)
      case Take(n)        => s.take(n.toLong)
      case Drop(n)        => s.drop(n.toLong)
      case TakeWhileLt(b) => s.takeWhile(_ < b)
      case ScanSum()      => s.scan(0)(_ + _)
      case MapAccumMax()  =>
        s.mapAccum(Int.MinValue) { (mx, a) =>
          val m = math.max(mx, a); (m, m)
        }
      case Distinct()        => s.distinct
      case DistinctByMod(m)  => s.distinctBy(x => math.abs(x % m))
      case Intersperse(sep)  => s.intersperse(sep)
      case CollectEvenHalf() => s.collect { case x if x % 2 == 0 => x / 2 }
      case FlatMapDup()      => s.flatMap(x => Stream(x, x + 1))
      case ConcatTail(t)     => s ++ Stream(t, t + 1)
    }

    def applyList(xs: List[Int], op: Run11Op): List[Int] = op match {
      case MapAdd(k)      => xs.map(_ + k)
      case FilterMod(m)   => xs.filter(x => math.abs(x % m) != 1)
      case Take(n)        => xs.take(n)
      case Drop(n)        => xs.drop(n)
      case TakeWhileLt(b) => xs.takeWhile(_ < b)
      case ScanSum()      => xs.scanLeft(0)(_ + _)
      case MapAccumMax()  =>
        xs.foldLeft((Int.MinValue, List.empty[Int])) { case ((mx, acc), a) =>
          val m = math.max(mx, a); (m, m :: acc)
        }._2
          .reverse
      case Distinct()        => xs.distinct
      case DistinctByMod(m)  => xs.distinctBy(x => math.abs(x % m))
      case Intersperse(sep)  => run11Intersperse(xs, sep)
      case CollectEvenHalf() => xs.collect { case x if x % 2 == 0 => x / 2 }
      case FlatMapDup()      => xs.flatMap(x => List(x, x + 1))
      case ConcatTail(t)     => xs ++ List(t, t + 1)
    }
  }

  /**
   * Long-lane op algebra whose generated ELEMENTS include the in-band
   * `Long.MaxValue` EOF sentinel (and separators equal to it), composing the
   * lossless-disambiguation contract through randomized pipelines.
   */
  sealed private trait Run11LongOp
  private object Run11LongOp {
    final case class MapAddL(k: Long)        extends Run11LongOp
    final case class FilterNotEq(v: Long)    extends Run11LongOp
    final case class TakeL(n: Int)           extends Run11LongOp
    final case class DropL(n: Int)           extends Run11LongOp
    final case class TakeWhileNotEq(v: Long) extends Run11LongOp
    final case class DistinctL()             extends Run11LongOp
    final case class IntersperseL(sep: Long) extends Run11LongOp
    final case class ScanXor()               extends Run11LongOp

    val gen: Gen[Any, Run11LongOp] = Gen.oneOf(
      Gen.oneOf(Gen.const(-1L), Gen.const(0L), Gen.const(1L)).map(MapAddL(_)),
      Gen.oneOf(Gen.const(7L), Gen.const(Long.MaxValue)).map(FilterNotEq(_)),
      Gen.int(0, 10).map(TakeL(_)),
      Gen.int(0, 4).map(DropL(_)),
      Gen.oneOf(Gen.const(7L), Gen.const(Long.MaxValue)).map(TakeWhileNotEq(_)),
      Gen.const(DistinctL()),
      Gen.oneOf(Gen.const(0L), Gen.const(Long.MaxValue)).map(IntersperseL(_)),
      Gen.const(ScanXor())
    )

    def applyStream(s: Stream[Nothing, Long], op: Run11LongOp): Stream[Nothing, Long] = op match {
      case MapAddL(k)        => s.map(_ + k)
      case FilterNotEq(v)    => s.filter(_ != v)
      case TakeL(n)          => s.take(n.toLong)
      case DropL(n)          => s.drop(n.toLong)
      case TakeWhileNotEq(v) => s.takeWhile(_ != v)
      case DistinctL()       => s.distinct
      case IntersperseL(sep) => s.intersperse(sep)
      case ScanXor()         => s.scan(0L)(_ ^ _)
    }

    def applyList(xs: List[Long], op: Run11LongOp): List[Long] = op match {
      case MapAddL(k)        => xs.map(_ + k)
      case FilterNotEq(v)    => xs.filter(_ != v)
      case TakeL(n)          => xs.take(n)
      case DropL(n)          => xs.drop(n)
      case TakeWhileNotEq(v) => xs.takeWhile(_ != v)
      case DistinctL()       => xs.distinct
      case IntersperseL(sep) => run11Intersperse(xs, sep)
      case ScanXor()         => xs.scanLeft(0L)(_ ^ _)
    }
  }

  private val run11LongElem: Gen[Any, Long] = Gen.oneOf(
    Gen.const(Long.MaxValue),
    Gen.const(Long.MaxValue - 1),
    Gen.const(Long.MinValue + 2),
    Gen.const(0L),
    Gen.const(7L),
    Gen.long(-5L, 5L)
  )

  private def run11ConvergenceSuite = suite("run11 convergence")(
    suite("extended randomized differential vs List oracle")(
      test("extendedOpAlgebra_overChunk_matchesListOracle") {
        check(StreamsGen.genChunk(Gen.int(-50, 50)), Gen.listOfBounded(1, 5)(Run11Op.gen)) { (chunk, ops) =>
          val xs     = chunk.toList
          val s      = ops.foldLeft(Stream.fromChunk(chunk): Stream[Nothing, Int])(Run11Op.applyStream)
          val oracle = ops.foldLeft(xs)(Run11Op.applyList)
          assertTrue(run11Collect(s).toList == oracle)
        }
      },
      test("extendedOpAlgebra_overRange_deepEqualsShallowEqualsOracle") {
        check(Gen.int(0, 16), Gen.listOfBounded(1, 4)(Run11Op.gen)) { (n, ops) =>
          val s      = ops.foldLeft(Stream.range(0, n): Stream[Nothing, Int])(Run11Op.applyStream)
          val oracle = ops.foldLeft((0 until n).toList)(Run11Op.applyList)
          assertTrue(run11Collect(s).toList == oracle, run11Collect(run11Deep(s)).toList == oracle)
        }
      },
      test("extendedOpAlgebra_overFromIterator_matchesListOracle") {
        check(Gen.listOfBounded(0, 10)(Gen.int(-9, 9)), Gen.listOfBounded(1, 4)(Run11Op.gen)) { (xs, ops) =>
          val s      = ops.foldLeft(Stream.fromIterator(xs.iterator): Stream[Nothing, Int])(Run11Op.applyStream)
          val oracle = ops.foldLeft(xs)(Run11Op.applyList)
          assertTrue(run11Collect(s).toList == oracle)
        }
      }
    ),
    suite("zip-composed differential")(
      test("zip_filteredLeft_mappedDroppedRight_matchesListOracle") {
        check(StreamsGen.genChunk(Gen.int(-20, 20)), Gen.int(0, 5), Gen.int(2, 4)) { (chunk, d, m) =>
          val xs = chunk.toList
          val s  = (Stream.fromChunk(chunk).filter(x => math.abs(x % m) != 1) &&
            Stream.fromChunk(chunk).map(_ * 2).drop(d.toLong)).map(t => t._1 + t._2)
          val oracle = xs.filter(x => math.abs(x % m) != 1).zip(xs.map(_ * 2).drop(d)).map(t => t._1 + t._2)
          assertTrue(run11Collect(s).toList == oracle)
        }
      },
      test("zip_distinctLeft_scanRight_thenIntersperseTakeWhile_matchesListOracle") {
        check(StreamsGen.genChunk(Gen.int(-9, 9)), Gen.int(-3, 3)) { (chunk, sep) =>
          val xs = chunk.toList
          val zs = (Stream.fromChunk(chunk).distinct && Stream.fromChunk(chunk).scan(0)(_ + _))
            .map(t => t._1 * t._2)
          val s      = zs.intersperse(sep).takeWhile(_ < 50)
          val zl     = xs.distinct.zip(xs.scanLeft(0)(_ + _)).map(t => t._1 * t._2)
          val oracle = run11Intersperse(zl, sep).takeWhile(_ < 50)
          assertTrue(run11Collect(s).toList == oracle)
        }
      },
      test("flatMap_withInnerZip_matchesListOracle") {
        check(Gen.int(0, 8)) { n =>
          val s = Stream
            .range(0, n)
            .flatMap(i => (Stream.range(0, i) && Stream.range(0, i).map(_ * 10)).map(t => t._1 + t._2 + i))
          val oracle = (0 until n).toList
            .flatMap(i => (0 until i).toList.zip((0 until i).map(_ * 10)).map(t => t._1 + t._2 + i))
          assertTrue(run11Collect(s).toList == oracle)
        }
      }
    ),
    suite("Long-lane randomized sentinel-element differential")(
      test("longOpAlgebra_withMaxValueElements_matchesListOracle") {
        check(Gen.listOfBounded(0, 10)(run11LongElem), Gen.listOfBounded(1, 4)(Run11LongOp.gen)) { (xs, ops) =>
          val s =
            ops.foldLeft(Stream.fromChunk(Chunk.fromIterable(xs)): Stream[Nothing, Long])(Run11LongOp.applyStream)
          val oracle = ops.foldLeft(xs)(Run11LongOp.applyList)
          assertTrue(run11Collect(s).toList == oracle)
        }
      },
      test("doubleLane_maxValueElements_throughDistinctIntersperseScan") {
        val xs = List(Double.MaxValue, 1.5, Double.MaxValue, 2.5)
        assertTrue(
          run11Collect(Stream.fromChunk(Chunk.fromIterable(xs)).distinct).toList == xs.distinct,
          run11Collect(Stream.fromChunk(Chunk.fromIterable(xs)).intersperse(Double.MaxValue)).toList ==
            run11Intersperse(xs, Double.MaxValue),
          run11Collect(Stream.fromChunk(Chunk.fromIterable(xs)).scan(0.0)(_ + _)).toList == xs.scanLeft(0.0)(_ + _),
          run11Collect(Stream.fromChunk(Chunk.fromIterable(xs)).takeWhile(_ => true)).toList == xs
        )
      }
    ),
    suite("manual Reader SPI through library combinators")(
      test("minimalBoxedReader_throughMapFilterDropTakeScan_matchesListOracle") {
        val xs = (0 until 20).toList
        val s  = Stream
          .fromReader[Nothing, Int](new Run11BoxedReader[Int](xs.toVector))
          .map(_ * 2)
          .filter(_ % 3 != 0)
          .drop(1L)
          .take(7L)
          .scan(0)(_ + _)
        val oracle = xs.map(_ * 2).filter(_ % 3 != 0).drop(1).take(7).scanLeft(0)(_ + _)
        assertTrue(run11Collect(s).toList == oracle)
      },
      test("minimalBoxedReader_deepInterpreterPath_matchesListOracle") {
        val xs = (0 until 12).toList
        val s  = run11Deep(Stream.fromReader[Nothing, Int](new Run11BoxedReader[Int](xs.toVector)).map(_ + 1))
        assertTrue(run11Collect(s).toList == xs.map(_ + 1))
      },
      test("minimalBoxedReader_zipAndConcatWithLibraryStreams") {
        val zipped = (Stream.fromReader[Nothing, Int](new Run11BoxedReader[Int](Vector(1, 2, 3))) &&
          Stream(10, 20, 30, 40)).map(t => t._1 + t._2)
        val concatenated =
          Stream.fromReader[Nothing, Int](new Run11BoxedReader[Int](Vector(1, 2, 3))) ++ Stream(7, 8)
        assertTrue(
          run11Collect(zipped).toList == List(11, 22, 33),
          run11Collect(concatenated).toList == List(1, 2, 3, 7, 8)
        )
      },
      test("minimalBoxedReader_chunkedSlidingDistinctIntersperse_matchListSemantics") {
        def mk = Stream.fromReader[Nothing, Int](new Run11BoxedReader[Int](Vector(1, 1, 2, 3, 3, 4)))
        assertTrue(
          run11Collect(mk.chunked(2)).map(_.toList).toList == List(List(1, 1), List(2, 3), List(3, 4)),
          run11Collect(mk.distinct).toList == List(1, 2, 3, 4),
          run11Collect(mk.intersperse(0)).toList == List(1, 0, 1, 0, 2, 0, 3, 0, 3, 0, 4),
          run11Collect(mk.sliding(3, 2)).map(_.toList).toList ==
            List(1, 1, 2, 3, 3, 4).sliding(3, 2).map(_.toList).toList
        )
      },
      test("minimalBoxedReader_nullElements_passThroughCombinators") {
        val s = Stream.fromReader[Nothing, String](new Run11BoxedReader[String](Vector("a", null, "b")))
        assertTrue(run11Collect(s.filter(_ ne null)).toList == List("a", "b"))
      },
      test("minimalResettableReader_underRepeated_cycles") {
        val s = Stream.fromReader[Nothing, Int](new Run11BoxedReader[Int](Vector(1, 2), supportsReset = true))
        assertTrue(run11Collect(s.repeated.take(5L)).toList == List(1, 2, 1, 2, 1))
      },
      test("minimalNonResettableReader_underRepeated_surfacesDocumentedUOE") {
        val s = Stream.fromReader[Nothing, Int](new Run11BoxedReader[Int](Vector(1, 2)))
        val t = caught(s.repeated.take(5L).runCollect)
        assertTrue(t != null, t.isInstanceOf[UnsupportedOperationException])
      },
      test("longTaggedMinimalReader_maxValueElements_losslessThroughCombinators") {
        val xs = Vector(Long.MaxValue, 5L, Long.MaxValue, 6L)
        def mk = Stream.fromReader[Nothing, Long](new Run11LongTaggedReader(xs))
        assertTrue(
          run11Collect(mk).toList == xs.toList,
          run11Collect(mk.map(_ - 1L)).toList == xs.toList.map(_ - 1L),
          run11Collect(mk.take(3L)).toList == xs.toList.take(3),
          run11Collect(mk.filter(_ != 5L)).toList == xs.toList.filter(_ != 5L),
          mk.count == Right(4L),
          mk.runFold(0L)(_ + _) == Right(xs.sum)
        )
      }
    ),
    suite("interpreter segment boundaries (SegmentBudget ± 1)")(
      test("mapChain_at7999_8000_8001_ops_valuesCorrect") {
        def chain(n: Int): Stream[Nothing, Int] =
          (0 until n).foldLeft(Stream(0, 1, 2): Stream[Nothing, Int])((s, _) => s.map(_ + 1))
        assertTrue(
          run11Collect(chain(7999)).toList == List(7999, 8000, 8001),
          run11Collect(chain(8000)).toList == List(8000, 8001, 8002),
          run11Collect(chain(8001)).toList == List(8001, 8002, 8003)
        )
      },
      test("takeAtSegmentSealPoint_7999and8000and8001_windowCorrect") {
        def out(n: Int) = run11Collect(
          (0 until n)
            .foldLeft(Stream.range(0, 6): Stream[Nothing, Int])((acc, _) => acc.map(_ + 1))
            .take(3L)
            .map(_ + 1)
        ).toList
        def oracle(n: Int) = (0 until 6).map(_ + n).take(3).map(_ + 1).toList
        assertTrue(out(7999) == oracle(7999), out(8000) == oracle(8000), out(8001) == oracle(8001))
      },
      test("repeated_overMultiSegmentPipeline_resetCascadesAcrossSegments") {
        val s = (0 until 8001).foldLeft(Stream(1, 2): Stream[Nothing, Int])((acc, _) => acc.map(_ + 1))
        assertTrue(run11Collect(s.repeated.take(5L)).toList == List(8002, 8003, 8002, 8003, 8002))
      },
      test("typedError_crossesSegmentBoundary_ensuringFiresOnce") {
        val fin                      = new AtomicInteger(0)
        val src: Stream[String, Int] =
          ((Stream(1, 2): Stream[Nothing, Int]) ++ (Stream.fail("seg-boom"): Stream[String, Nothing])).ensuring {
            fin.incrementAndGet(); ()
          }
        val s = (0 until 8001).foldLeft(src)((acc, _) => acc.map(_ + 1))
        assertTrue(s.runCollect == Left("seg-boom"), fin.get == 1)
      }
    )
  )
}
