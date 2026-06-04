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
 * Tests the generic `Sink.foldLeft[A, Z]` dispatch paths, which specialize on
 * both element JvmType and accumulator JvmType to call Function2 specialized
 * apply methods. Each (Z, A) combination must produce correct results.
 */
object GenericFoldSpec extends StreamsBaseSpec {

  private val n = 100

  // Helpers that force the GENERIC runFold[Z] path (not the specialized overloads)
  private def foldInts[Z](z: Z)(f: (Z, Int) => Z): Either[Nothing, Z]   = Stream.range(0, n).runFold[Z](z)(f)
  private def foldLongs[Z](z: Z)(f: (Z, Long) => Z): Either[Nothing, Z] =
    Stream.range(0, n).map(_.toLong).runFold[Z](z)(f)
  private def foldDoubles[Z](z: Z)(f: (Z, Double) => Z): Either[Nothing, Z] =
    Stream.range(0, n).map(_.toDouble).runFold[Z](z)(f)
  private def foldFloats[Z](z: Z)(f: (Z, Float) => Z): Either[Nothing, Z] =
    Stream.range(0, n).map(_.toFloat).runFold[Z](z)(f)

  private val intSum    = n * (n - 1) / 2
  private val longSum   = intSum.toLong
  private val doubleSum = intSum.toDouble
  private val floatSum  = intSum.toFloat

  def spec: Spec[TestEnvironment, Any] = suite("GenericFoldSpec")(
    suite("Int elements")(
      test("Z=Int, A=Int")(assertTrue(foldInts[Int](0)((a, b) => a + b) == Right(intSum))),
      test("Z=Long, A=Int")(assertTrue(foldInts[Long](0L)((a, b) => a + b) == Right(longSum))),
      test("Z=Double, A=Int")(assertTrue(foldInts[Double](0.0)((a, b) => a + b) == Right(doubleSum))),
      test("Z=Float, A=Int")(assertTrue(foldInts[Float](0.0f)((a, b) => a + b) == Right(floatSum))),
      test("Z=String, A=Int")(assertTrue(foldInts[String]("")((a, b) => a + b.toString).map(_.length) == Right(n + 90)))
    ),

    suite("Long elements")(
      test("Z=Int, A=Long")(assertTrue(foldLongs[Int](0)((a, b) => a + b.toInt) == Right(intSum))),
      test("Z=Long, A=Long")(assertTrue(foldLongs[Long](0L)((a, b) => a + b) == Right(longSum))),
      test("Z=Double, A=Long")(assertTrue(foldLongs[Double](0.0)((a, b) => a + b) == Right(doubleSum))),
      test("Z=Float, A=Long")(assertTrue(foldLongs[Float](0.0f)((a, b) => a + b) == Right(floatSum))),
      test("Z=String, A=Long") {
        assertTrue(foldLongs[String]("")((a, b) => a + b.toString).map(_.length) == Right(n + 90))
      }
    ),

    suite("Double elements")(
      test("Z=Int, A=Double")(assertTrue(foldDoubles[Int](0)((a, b) => a + b.toInt) == Right(intSum))),
      test("Z=Long, A=Double")(assertTrue(foldDoubles[Long](0L)((a, b) => a + b.toLong) == Right(longSum))),
      test("Z=Double, A=Double")(assertTrue(foldDoubles[Double](0.0)((a, b) => a + b) == Right(doubleSum))),
      test("Z=Float, A=Double")(assertTrue(foldDoubles[Float](0.0f)((a, b) => a + b.toFloat) == Right(floatSum))),
      test("Z=String, A=Double") {
        assertTrue(foldDoubles[String]("")((a, b) => a + b.toInt.toString).map(_.length) == Right(n + 90))
      }
    ),

    suite("Float elements")(
      test("Z=Int, A=Float")(assertTrue(foldFloats[Int](0)((a, b) => a + b.toInt) == Right(intSum))),
      test("Z=Long, A=Float")(assertTrue(foldFloats[Long](0L)((a, b) => a + b.toLong) == Right(longSum))),
      test("Z=Double, A=Float")(assertTrue(foldFloats[Double](0.0)((a, b) => a + b) == Right(doubleSum))),
      test("Z=Float, A=Float")(assertTrue(foldFloats[Float](0.0f)((a, b) => a + b) == Right(floatSum))),
      test("Z=String, A=Float") {
        assertTrue(foldFloats[String]("")((a, b) => a + b.toInt.toString).map(_.length) == Right(n + 90))
      }
    ),

    suite("AnyRef elements")(
      test("Z=Long, A=String") {
        val result = Stream.fromIterable((0 until n).map(_.toString)).runFold[Long](0L)((a, b) => a + b.toLong)
        assertTrue(result == Right(longSum))
      },
      test("Z=String, A=String") {
        val result = Stream.fromIterable(Seq("a", "b", "c")).runFold[String]("")(_ + _)
        assertTrue(result == Right("abc"))
      }
    )
  )
}
