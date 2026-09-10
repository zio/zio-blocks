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

import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._

object StreamStartSpec extends StreamsBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("start")(
    test("start returns a scoped reader that produces all elements") {
      import zio.blocks.scope._
      val result = Scope.global.scoped { scope =>
        implicit val implicitScope = scope
        val reader                 = Stream.range(0, 5).start
        val buf                    = scala.collection.mutable.ListBuffer.empty[Int]
        val r                      = implicitScope.leak(reader)
        var v                      = r.read[Any](null)
        while (v != null) { buf += v.asInstanceOf[Int]; v = r.read[Any](null) }
        buf.toList
      }
      assert(result)(equalTo(List(0, 1, 2, 3, 4)))
    },
    test("start reader is closed when scope closes") {
      import zio.blocks.scope._
      var readerClosed = false
      Scope.global.scoped { scope =>
        implicit val implicitScope = scope
        val s                      = Stream.range(0, 5).ensuring { readerClosed = true }
        val _reader                = s.start
      }
      assertTrue(readerClosed)
    },
    test("start with mapped stream") {
      import zio.blocks.scope._
      val result = Scope.global.scoped { scope =>
        implicit val implicitScope = scope
        val reader                 = Stream.range(0, 3).map(_ * 10).start
        val buf                    = scala.collection.mutable.ListBuffer.empty[Int]
        val r                      = implicitScope.leak(reader)
        var v                      = r.read[Any](null)
        while (v != null) { buf += v.asInstanceOf[Int]; v = r.read[Any](null) }
        buf.toList
      }
      assert(result)(equalTo(List(0, 10, 20)))
    },
    test("blocking drain keeps the read failure primary and closes exactly once") {
      val primary = new RuntimeException("read")
      val cleanup = new RuntimeException("close")
      var closes  = 0
      val stream  = Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = throw primary
        def close(): Unit                  = { closes += 1; throw cleanup }
      })
      val failure = scala.util.Try(stream.runDrain).failed.toOption.orNull
      assertTrue(failure eq primary, primary.getSuppressed.toList == List(cleanup), closes == 1)
    },
    test("blocking Long fold keeps the callback failure primary and closes exactly once") {
      val primary = new RuntimeException("fold")
      val cleanup = new RuntimeException("close")
      var closes  = 0
      val source  = new Reader.SyncReader[Int] {
        private var emitted                = false
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A =
          if (emitted) sentinel else { emitted = true; Int.box(1).asInstanceOf[A] }
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (emitted) sentinel else { emitted = true; 1L }
        def close(): Unit = { closes += 1; throw cleanup }
      }
      val stream  = Stream.fromReader[Nothing, Int](source)
      val failure = scala.util.Try(stream.runFold(0L)((_, _) => throw primary)).failed.toOption.orNull
      assertTrue(failure eq primary, primary.getSuppressed.toList == List(cleanup), closes == 1)
    },
    test("blocking flatMap readers latch source callback and inner-compilation failures") {
      def replay[A](stream: Stream[Nothing, A]): (Throwable, Throwable) = {
        val reader = stream.compileBlocking(Stream.DefaultBufferSize)
        val first  = scala.util.Try(reader.read[Any](null)).failed.toOption.orNull
        val second = scala.util.Try(reader.read[Any](null)).failed.toOption.orNull
        scala.util.Try(reader.close())
        (first, second)
      }

      val sourceFailure = new RuntimeException("source")
      var sourceReads   = 0
      val brokenSource  = Stream.fromReader[Nothing, String](new Reader.SyncReader[String] {
        def isClosed: Boolean                 = false
        def read[A >: String](sentinel: A): A = { sourceReads += 1; throw sourceFailure }
        def close(): Unit                     = ()
      })
      val sourceReplay = replay(brokenSource.flatMap(_ => Stream.succeed("unused")))

      val callbackFailure = new RuntimeException("callback")
      var callbackCalls   = 0
      val callbackReplay  = replay(
        Stream.succeed(true).flatMap[Nothing, Nothing, Int] { _ => callbackCalls += 1; throw callbackFailure }
      )

      val innerFailure = new RuntimeException("inner")
      var innerCalls   = 0
      val innerReplay  = replay(
        Stream.succeed(1).flatMap { _ =>
          innerCalls += 1
          Stream.fromReader[Nothing, Int](throw innerFailure)
        }
      )

      assertTrue(
        (sourceReplay._1 eq sourceFailure) && (sourceReplay._2 eq sourceFailure),
        sourceReads == 1,
        (callbackReplay._1 eq callbackFailure) && (callbackReplay._2 eq callbackFailure),
        callbackCalls == 1,
        (innerReplay._1 eq innerFailure) && (innerReplay._2 eq innerFailure),
        innerCalls == 1
      )
    }
  )
}
