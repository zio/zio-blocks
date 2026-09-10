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

import zio._
import zio.test._

object BufferSizeSpec extends StreamsBaseSpec {
  def spec = suite("Stream.bufferSize")(
    test("mapPar with custom buffer size produces correct results") {
      for {
        result <- ZIO.fromEither(Stream.bufferSize(128)(Stream.range(0, 1000).mapPar(4)(identity)).runCollect)
      } yield assertTrue(result.length == 1000)
    },
    test("flatMapPar with custom buffer size produces correct results") {
      for {
        result <- ZIO.fromEither(
                    Stream.bufferSize(32)(Stream.range(0, 10).flatMapPar(4)(i => Stream.range(0, i))).runCollect
                  )
      } yield assertTrue(result.length == 45)
    },
    test("nested bufferSize: inner wins") {
      for {
        result <- ZIO.fromEither(
                    Stream.bufferSize(128)(Stream.bufferSize(32)(Stream.range(0, 100).mapPar(2)(identity))).runCollect
                  )
      } yield assertTrue(result.length == 100)
    },
    test("invalid bufferSize throws IllegalArgumentException") {
      assertTrue(scala.util.Try(Stream.bufferSize(3)(Stream.empty)).isFailure) &&
      assertTrue(scala.util.Try(Stream.bufferSize(0)(Stream.empty)).isFailure)
    }
  )
}
