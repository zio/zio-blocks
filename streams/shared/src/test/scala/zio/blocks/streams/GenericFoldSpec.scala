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

// Cross-platform semantic coverage.

import zio._
import zio.blocks.async.Async
import zio.test._

/**
 * Tests the generic `Sink.foldLeft[A, Z]` dispatch paths, which specialize on
 * both element JvmType and accumulator JvmType to call Function2 specialized
 * apply methods. Each (Z, A) combination must produce correct results.
 */
object GenericFoldSpec extends StreamsBaseSpec {

  private val n = 100

  // Helpers that force the GENERIC runFold[Z] path (not the specialized overloads)
  private def foldInts[Z: JvmType.Infer](z: Z)(f: (Z, Int) => Z): Async[Either[Nothing, Z]] =
    Stream.range(0, n).runFoldAsync[Z](z)((a, b) => Async.succeed(f(a, b)))
  private def foldLongs[Z: JvmType.Infer](z: Z)(f: (Z, Long) => Z): Async[Either[Nothing, Z]] =
    Stream.range(0, n).map(_.toLong).runFoldAsync[Z](z)((a, b) => Async.succeed(f(a, b)))
  private def foldDoubles[Z: JvmType.Infer](z: Z)(f: (Z, Double) => Z): Async[Either[Nothing, Z]] =
    Stream.range(0, n).map(_.toDouble).runFoldAsync[Z](z)((a, b) => Async.succeed(f(a, b)))
  private def foldFloats[Z: JvmType.Infer](z: Z)(f: (Z, Float) => Z): Async[Either[Nothing, Z]] =
    Stream.range(0, n).map(_.toFloat).runFoldAsync[Z](z)((a, b) => Async.succeed(f(a, b)))

  private def assertFold[Z](fold: Async[Either[Nothing, Z]])(assertion: Either[Nothing, Z] => TestResult) =
    runAsync(fold).map(assertion)

  private val intSum    = n * (n - 1) / 2
  private val longSum   = intSum.toLong
  private val doubleSum = intSum.toDouble
  private val floatSum  = intSum.toFloat

  private def equalTo[Z](expected: Z): Either[Nothing, Z] => TestResult = result =>
    assertTrue(result == Right(expected))

  def spec: Spec[TestEnvironment, Any] = suite("GenericFoldSpec")(
    suite("Int elements")(
      test("Z=Int, A=Int")(assertFold(foldInts(0)(_ + _))(equalTo(intSum))),
      test("Z=Long, A=Int")(assertFold(foldInts(0L)(_ + _))(equalTo(longSum))),
      test("Z=Double, A=Int")(assertFold(foldInts(0.0)(_ + _))(equalTo(doubleSum))),
      test("Z=Float, A=Int")(assertFold(foldInts(0.0f)(_ + _))(equalTo(floatSum))),
      test("Z=String, A=Int")(
        assertFold(foldInts("")((a, b) => a + b.toString))(r => assertTrue(r.map(_.length) == Right(n + 90)))
      )
    ),
    suite("Long elements")(
      test("Z=Int, A=Long")(assertFold(foldLongs(0)((a, b) => a + b.toInt))(equalTo(intSum))),
      test("Z=Long, A=Long")(assertFold(foldLongs(0L)(_ + _))(equalTo(longSum))),
      test("Z=Double, A=Long")(assertFold(foldLongs(0.0)(_ + _))(equalTo(doubleSum))),
      test("Z=Float, A=Long")(assertFold(foldLongs(0.0f)(_ + _))(equalTo(floatSum))),
      test("Z=String, A=Long")(
        assertFold(foldLongs("")((a, b) => a + b.toString))(r => assertTrue(r.map(_.length) == Right(n + 90)))
      )
    ),
    suite("Double elements")(
      test("Z=Int, A=Double")(assertFold(foldDoubles(0)((a, b) => a + b.toInt))(equalTo(intSum))),
      test("Z=Long, A=Double")(assertFold(foldDoubles(0L)((a, b) => a + b.toLong))(equalTo(longSum))),
      test("Z=Double, A=Double")(assertFold(foldDoubles(0.0)(_ + _))(equalTo(doubleSum))),
      test("Z=Float, A=Double")(assertFold(foldDoubles(0.0f)((a, b) => a + b.toFloat))(equalTo(floatSum))),
      test("Z=String, A=Double")(
        assertFold(foldDoubles("")((a, b) => a + b.toInt.toString))(r => assertTrue(r.map(_.length) == Right(n + 90)))
      )
    ),
    suite("Float elements")(
      test("Z=Int, A=Float")(assertFold(foldFloats(0)((a, b) => a + b.toInt))(equalTo(intSum))),
      test("Z=Long, A=Float")(assertFold(foldFloats(0L)((a, b) => a + b.toLong))(equalTo(longSum))),
      test("Z=Double, A=Float")(assertFold(foldFloats(0.0)(_ + _))(equalTo(doubleSum))),
      test("Z=Float, A=Float")(assertFold(foldFloats(0.0f)(_ + _))(equalTo(floatSum))),
      test("Z=String, A=Float")(
        assertFold(foldFloats("")((a, b) => a + b.toInt.toString))(r => assertTrue(r.map(_.length) == Right(n + 90)))
      )
    ),
    suite("AnyRef elements")(
      test("Z=Long, A=String")(
        assertFold(
          Stream.fromIterable((0 until n).map(_.toString)).runFoldAsync(0L)((a, b) => Async.succeed(a + b.toLong))
        )(equalTo(longSum))
      ),
      test("Z=String, A=String")(
        assertFold(Stream.fromIterable(Seq("a", "b", "c")).runFoldAsync("")((a, b) => Async.succeed(a + b)))(
          equalTo("abc")
        )
      )
    )
  )
}
