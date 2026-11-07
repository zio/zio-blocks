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

import zio.blocks.async.Async
import zio.test._

object StreamRepresentationSpec extends StreamsBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Stream element representation")(
    test("known primitive survives covariant widening and preserving operators") {
      val widened: Stream[Nothing, AnyVal] = Stream.succeed(1)
      val distinct                         = Stream.succeed(1).distinct
      val distinctBy                       = Stream.succeed(1).distinctBy(identity)
      val accumulated                      = Stream.succeed(1).mapAccum(0)((state, value) => (state + value, value))
      val parallel                         = Stream.succeed(1).mapParAsync(1)(Async.succeed)
      assertTrue(
        widened.elementRepresentation == ElementRepresentation.Known(JvmType.Int),
        widened.filter(_ => true).elementRepresentation == ElementRepresentation.Known(JvmType.Int),
        distinct.elementRepresentation == ElementRepresentation.Known(JvmType.Int),
        distinct.repeated.compile(0).jvmType == JvmType.Int,
        distinctBy.elementRepresentation == ElementRepresentation.Known(JvmType.Int),
        distinctBy.repeated.compile(0).jvmType == JvmType.Int,
        accumulated.elementRepresentation == ElementRepresentation.Known(JvmType.Int),
        accumulated.repeated.compile(0).jvmType == JvmType.Int,
        parallel.elementRepresentation == ElementRepresentation.Known(JvmType.Int),
        parallel.repeated.compile(0).jvmType == JvmType.Int
      )
    },
    test("single-worker parallel maps degrade to their sequential operators") {
      val source = Stream.succeed(1)
      assertTrue(
        source.mapPar(1)(_ + 1).render == source.map(_ + 1).render,
        source.mapParAsync(1)(value => Async.succeed(value + 1)).render ==
          source.mapAsync(value => Async.succeed(value + 1)).render
      )
    },
    test("late-bound buffering adopts the materialized physical lane") {
      val source = Stream.fromReader[Nothing, Int](zio.blocks.streams.io.Reader.singleInt(1))
      assertTrue(
        source.elementRepresentation == ElementRepresentation.LateBound,
        source.buffer(1).compile(0).jvmType == JvmType.Int,
        Stream.bufferSize(1)(source).compile(0).jvmType == JvmType.Int
      )
    },
    test("fixed and effect-deferred results have stable representations") {
      assertTrue(
        Stream.attempt(1).elementRepresentation == ElementRepresentation.Known(JvmType.Int),
        Stream.attemptAsync(Async.succeed(1)).elementRepresentation == ElementRepresentation.Known(JvmType.Int),
        Stream.succeed("a").elementRepresentation == ElementRepresentation.Boxed
      )
    },
    test("synchronous async reader factories remain late-bound") {
      val stream = Stream.fromReader[Nothing, Int](zio.blocks.streams.io.Reader.singleInt(1).toAsync)
      assertTrue(stream.elementRepresentation == ElementRepresentation.LateBound)
    },
    test("widening APIs infer their exact output types") {
      val bytes: Stream[Nothing, Byte] = Stream.succeed(1.toByte)
      val recovered                    = Stream.succeed(1).catchAll(_ => Stream.succeed(2.0))
      val separated                    = Stream.succeed(1).intersperse(2.0)
      assertTrue(
        bytes.elementRepresentation == ElementRepresentation.Known(JvmType.Byte),
        recovered.elementRepresentation == ElementRepresentation.Boxed,
        separated.elementRepresentation == ElementRepresentation.Boxed
      )
    }
  )
}
