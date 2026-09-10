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

/**
 * JVM-only because synchronous terminal execution is deliberately unavailable
 * on Scala.js.
 */
object NarrowFoldSpec extends StreamsBaseSpec {
  private def ints: Stream[Nothing, Int] = Stream.range(0, 100)

  def spec = suite("narrow primitive synchronous folds")(
    test("Byte, Short, Char, and Boolean values reach specialized Int accumulators with their logical types") {
      assertTrue(
        ints.map(_.toByte).runFold(0)(_ + _) == Right(4950),
        ints.map(_.toShort).runFold(0)(_ + _) == Right(4950),
        ints.map(_.toChar).runFold(0)((sum, value) => sum + value.toInt) == Right(4950),
        ints.map(_ % 2 == 0).runFold(0)((sum, value) => sum + (if (value) 1 else 0)) == Right(50)
      )
    },
    test("Byte, Short, Char, and Boolean values reach specialized Long accumulators with their logical types") {
      assertTrue(
        ints.map(_.toByte).runFold(0L)(_ + _) == Right(4950L),
        ints.map(_.toShort).runFold(0L)(_ + _) == Right(4950L),
        ints.map(_.toChar).runFold(0L)((sum, value) => sum + value.toLong) == Right(4950L),
        ints.map(_ % 2 == 0).runFold(0L)((sum, value) => sum + (if (value) 1L else 0L)) == Right(50L)
      )
    },
    test("Byte, Short, Char, and Boolean values reach specialized Double accumulators with their logical types") {
      assertTrue(
        ints.map(_.toByte).runFold(0d)(_ + _) == Right(4950d),
        ints.map(_.toShort).runFold(0d)(_ + _) == Right(4950d),
        ints.map(_.toChar).runFold(0d)((sum, value) => sum + value.toDouble) == Right(4950d),
        ints.map(_ % 2 == 0).runFold(0d)((sum, value) => sum + (if (value) 1d else 0d)) == Right(50d)
      )
    }
  )
}
