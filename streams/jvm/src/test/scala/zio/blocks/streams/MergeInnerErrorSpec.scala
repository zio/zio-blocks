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

object MergeInnerErrorSpec extends StreamsBaseSpec {

  private val errorMsg = "inner-boom"

  private def failingIntStream(failAt: Int): Stream[String, Int] =
    Stream.range(0, failAt) ++ Stream.fail(errorMsg)

  private def failingLongStream(failAt: Int): Stream[String, Long] =
    Stream.range(0, failAt).map(_.toLong) ++ Stream.fail(errorMsg)

  private def failingDoubleStream(failAt: Int): Stream[String, Double] =
    Stream.range(0, failAt).map(_.toDouble) ++ Stream.fail(errorMsg)

  private def failingFloatStream(failAt: Int): Stream[String, Float] =
    Stream.range(0, failAt).map(_.toFloat) ++ Stream.fail(errorMsg)

  def spec: Spec[TestEnvironment, Any] = suite("MergeInnerErrorSpec")(
    suite("error in inner stream does not hang (InnerDone-in-finally regression)")(
      test("generic mergeAll: error in inner terminates without hang") {
        ZIO.attemptBlocking {
          val streams: Stream[String, Stream[String, String]] = Stream.fromIterable(
            Seq(
              Stream.fromIterable(Seq("a", "b", "c")),
              Stream.fromIterable(Seq("d")) ++ Stream.fail(errorMsg),
              Stream.fromIterable(Seq("e", "f"))
            )
          )
          val result = Stream.mergeAll(3)(streams).runCollect
          assertTrue(result.isLeft)
        }
      } @@ TestAspect.timeout(10.seconds),

      test("Int mergeAll: error in inner terminates without hang") {
        ZIO.attemptBlocking {
          val streams = Stream.fromIterable(
            Seq(Stream.range(0, 100), failingIntStream(50), Stream.range(200, 300))
          )
          val result = Stream.mergeAll(3)(streams).runCollect
          assertTrue(result.isLeft)
        }
      } @@ TestAspect.timeout(10.seconds),

      test("Long mergeAll: error in inner terminates without hang") {
        ZIO.attemptBlocking {
          val streams = Stream.fromIterable(
            Seq(
              Stream.range(0, 100).map(_.toLong),
              failingLongStream(50),
              Stream.range(200, 300).map(_.toLong)
            )
          )
          val result = Stream.mergeAll(3)(streams).runCollect
          assertTrue(result.isLeft)
        }
      } @@ TestAspect.timeout(10.seconds),

      test("Double mergeAll: error in inner terminates without hang") {
        ZIO.attemptBlocking {
          val streams = Stream.fromIterable(
            Seq(
              Stream.range(0, 100).map(_.toDouble),
              failingDoubleStream(50),
              Stream.range(200, 300).map(_.toDouble)
            )
          )
          val result = Stream.mergeAll(3)(streams).runCollect
          assertTrue(result.isLeft)
        }
      } @@ TestAspect.timeout(10.seconds),

      test("Float mergeAll: error in inner terminates without hang") {
        ZIO.attemptBlocking {
          val streams = Stream.fromIterable(
            Seq(
              Stream.range(0, 100).map(_.toFloat),
              failingFloatStream(50),
              Stream.range(200, 300).map(_.toFloat)
            )
          )
          val result = Stream.mergeAll(3)(streams).runCollect
          assertTrue(result.isLeft)
        }
      } @@ TestAspect.timeout(10.seconds)
    ),

    suite("multiple concurrent inner errors do not hang")(
      test("Int: 8 streams, half error, does not hang") {
        ZIO.attemptBlocking {
          var i = 0
          while (i < 10) {
            val streams = Stream.fromIterable(
              (0 until 8).map(j =>
                if (j % 2 == 0) failingIntStream(100)
                else Stream.range(j * 1000, j * 1000 + 200)
              )
            )
            val result = Stream.mergeAll(8)(streams).runCollect
            require(result.isLeft, s"iteration $i expected Left")
            i += 1
          }
          assertTrue(true)
        }
      } @@ TestAspect.timeout(30.seconds),

      test("Long: 8 streams, half error, does not hang") {
        ZIO.attemptBlocking {
          var i = 0
          while (i < 10) {
            val streams = Stream.fromIterable(
              (0 until 8).map(j =>
                if (j % 2 == 0) failingLongStream(100)
                else Stream.range(j * 1000, j * 1000 + 200).map(_.toLong)
              )
            )
            val result = Stream.mergeAll(8)(streams).runCollect
            require(result.isLeft, s"iteration $i expected Left")
            i += 1
          }
          assertTrue(true)
        }
      } @@ TestAspect.timeout(30.seconds)
    )
  )
}
