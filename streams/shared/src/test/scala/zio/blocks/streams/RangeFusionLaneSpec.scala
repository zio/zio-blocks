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

object RangeFusionLaneSpec extends StreamsBaseSpec {
  private val range = Stream.range(2, 12)

  def spec: Spec[TestEnvironment, Any] = suite("range fusion lanes")(
    test("one traversal program preserves map, filter, and operation order") {
      val mapped       = range.map(_ * 3)
      val filtered     = range.filter(_ % 3 == 0)
      val mapFiltered  = range.map(_ + 1).filter(_ % 2 == 0)
      val filterMapped = range.filter(_ % 2 == 0).map(_ + 1)
      assertTrue(
        mapped.runFoldLongBlocking(0L, _ + _) == Right((2 until 12).map(_ * 3L).sum),
        filtered.runFoldLongBlocking(0L, _ + _) == Right((2 until 12).filter(_ % 3 == 0).map(_.toLong).sum),
        mapFiltered.runFoldLongBlocking(0L, _ + _) == Right(
          (2 until 12).map(_ + 1).filter(_ % 2 == 0).map(_.toLong).sum
        ),
        filterMapped.runFoldLongBlocking(0L, _ + _) == Right((2 until 12).filter(_ % 2 == 0).map(_ + 1L).sum)
      )
    },
    test("slices and short-circuiting share the range traversal") {
      assertTrue(
        range.take(4).runFoldLongBlocking(0L, _ + _) == Right(14L),
        range.drop(3).take(4).runFoldLongBlocking(0L, _ + _) == Right(26L),
        range.takeWhile(_ < 7).runFoldLongBlocking(0L, _ + _) == Right(20L)
      )
    },
    test("alternate accumulator lanes retain identical semantics") {
      val transformed = range.map(_ + 1).filter(_ % 2 == 0)
      assertTrue(
        transformed.runFoldIntBlocking(0, _ + _) == Right(40),
        transformed.runFoldDoubleBlocking(0d, _ + _) == Right(40d),
        transformed.runFoldLongBlocking(0L, _ + _) == Right(40L)
      )
    }
  )
}
