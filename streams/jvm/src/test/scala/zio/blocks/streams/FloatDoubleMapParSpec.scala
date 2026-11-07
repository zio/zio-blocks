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

/**
 * Coverage for the Float/Double specialized
 * [[zio.blocks.streams.internal.FloatConcurrentMapParReader]] and
 * [[zio.blocks.streams.internal.DoubleConcurrentMapParReader]]. These are
 * picked by [[zio.blocks.streams.PlatformSpecific.createMapParReader]] when
 * `inType` is `JvmType.Float` / `JvmType.Double`, which happens whenever an
 * `Int`/`Long` stream is `map`ped to `Float`/`Double` and then `mapPar`'d.
 */
object FloatDoubleMapParSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("FloatDoubleMapParSpec")(
    suite("Float mapPar")(
      test("Float => Float identity preserves all elements") {
        ZIO.attemptBlocking {
          val n        = 1_000
          val result   = Stream.range(0, n).map(_.toFloat).mapPar(4)(identity).runFold(0.0)(_ + _)
          val expected = (0 until n).map(_.toDouble).sum
          assertTrue(result.exists(v => math.abs(v - expected) < 1e-3))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Float => Float doubles each value") {
        ZIO.attemptBlocking {
          val n      = 500
          val result = Stream.range(0, n).map(_.toFloat).mapPar(3)(_ * 2.0f).runCollect
          assertTrue(result.map(_.toSet) == Right((0 until n).map(_.toFloat * 2.0f).toSet))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Float => Long widens correctly") {
        ZIO.attemptBlocking {
          val n        = 500
          val result   = Stream.range(0, n).map(_.toFloat).mapPar(2)(_.toLong).runFold(0L)(_ + _)
          val expected = n.toLong * (n - 1) / 2
          assertTrue(result == Right(expected))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Float => String boxes through generic path") {
        ZIO.attemptBlocking {
          val n      = 200
          val result = Stream.range(0, n).map(_.toFloat).mapPar(2)(_.toString).runCollect
          assertTrue(result.map(_.size) == Right(n))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Float mapPar propagates worker error") {
        ZIO.attemptBlocking {
          val attempted = scala.util.Try {
            Stream
              .range(0, 100)
              .map(_.toFloat)
              .mapPar(2) { f =>
                if (f >= 50.0f) throw new RuntimeException("boom") else f
              }
              .runDrain
          }
          assertTrue(attempted.isFailure)
        }
      } @@ TestAspect.timeout(15.seconds)
    ),
    suite("Double mapPar")(
      test("Double => Double identity preserves all elements") {
        ZIO.attemptBlocking {
          val n        = 1_000
          val result   = Stream.range(0, n).map(_.toDouble).mapPar(4)(identity).runFold(0.0)(_ + _)
          val expected = (0 until n).map(_.toDouble).sum
          assertTrue(result == Right(expected))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Double => Double scales each value") {
        ZIO.attemptBlocking {
          val n      = 500
          val result = Stream.range(0, n).map(_.toDouble).mapPar(3)(_ * 0.5).runCollect
          assertTrue(result.map(_.toSet) == Right((0 until n).map(_.toDouble * 0.5).toSet))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Double => Int narrows correctly") {
        ZIO.attemptBlocking {
          val n        = 500
          val result   = Stream.range(0, n).map(_.toDouble).mapPar(2)(_.toInt).runFold(0L)(_ + _.toLong)
          val expected = n.toLong * (n - 1) / 2
          assertTrue(result == Right(expected))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Double => String boxes through generic path") {
        ZIO.attemptBlocking {
          val n      = 200
          val result = Stream.range(0, n).map(_.toDouble).mapPar(2)(_.toString).runCollect
          assertTrue(result.map(_.size) == Right(n))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Double mapPar(1) preserves all elements as set") {
        ZIO.attemptBlocking {
          val n        = 300
          val result   = Stream.range(0, n).map(_.toDouble).mapPar(1)(_ + 1.0).runCollect
          val expected = (0 until n).map(_.toDouble + 1.0).toSet
          assertTrue(result.map(_.toSet) == Right(expected))
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Double mapPar propagates worker error") {
        ZIO.attemptBlocking {
          val attempted = scala.util.Try {
            Stream
              .range(0, 100)
              .map(_.toDouble)
              .mapPar(2) { d =>
                if (d >= 50.0) throw new RuntimeException("boom") else d
              }
              .runDrain
          }
          assertTrue(attempted.isFailure)
        }
      } @@ TestAspect.timeout(15.seconds),
      test("Double mapPar empty stream completes") {
        ZIO.attemptBlocking {
          val result = Stream.range(0, 0).map(_.toDouble).mapPar(2)(identity).runCollect
          assertTrue(result.map(_.toList) == Right(Nil))
        }
      } @@ TestAspect.timeout(15.seconds)
    )
  )
}
