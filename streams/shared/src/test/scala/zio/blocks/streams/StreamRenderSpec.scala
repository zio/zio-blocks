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

import zio.test._
import zio.test.Assertion._

object StreamRenderSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("StreamRenderSpec")(
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
  )
}
