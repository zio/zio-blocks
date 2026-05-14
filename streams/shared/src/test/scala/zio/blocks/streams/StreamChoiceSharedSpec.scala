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

import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Choices
import zio.test._
import zio.test.Assertion._

/**
 * Cross-version tests for `Stream.choice`. Uses `Choices.separate` to normalize
 * union elements to `Either[L, R]`, ensuring identical behavior on Scala 2
 * (where `|` = `Either`) and Scala 3 (where `|` = native union).
 */
object StreamChoiceSharedSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Stream.choice (shared)")(
    test("disjoint concatenation") {
      val result = Stream.succeed("hello").choice(Stream.succeed(42)).runCollect
      assert(result.map(_.map(elem => Choices.separate[String, Int](elem))))(
        equalTo(Right(Chunk(Left("hello"), Right(42))))
      )
    },
    test("empty left stream") {
      val result = (Stream.empty: Stream[Nothing, String]).choice(Stream.succeed(42)).runCollect
      assert(result.map(_.map(elem => Choices.separate[String, Int](elem))))(
        equalTo(Right(Chunk(Right(42))))
      )
    },
    test("empty right stream") {
      val result = Stream.succeed("hello").choice(Stream.empty: Stream[Nothing, Int]).runCollect
      assert(result.map(_.map(elem => Choices.separate[String, Int](elem))))(
        equalTo(Right(Chunk(Left("hello"))))
      )
    },
    test("error propagation") {
      val result = (Stream.fail("boom"): Stream[String, String]).choice(Stream.succeed(42)).runCollect
      assert(result)(equalTo(Left("boom")))
    },
    test("error channel widens to common supertype") {
      sealed trait AppError
      case class LeftErr(msg: String) extends AppError
      case class RightErr(code: Int)  extends AppError

      val left: Stream[LeftErr, String] = Stream.fail(LeftErr("oops"))
      val right: Stream[RightErr, Int]  = Stream.succeed(42)
      val result                        = left.choice(right).runCollect
      assert(result)(equalTo(Left(LeftErr("oops"))))
    },
    test("right stream error propagation") {
      sealed trait AppError
      case class LeftErr(msg: String) extends AppError
      case class RightErr(code: Int)  extends AppError

      val left: Stream[LeftErr, String] = Stream.succeed("ok")
      val right: Stream[RightErr, Int]  = Stream.fail(RightErr(404))
      val result                        = left.choice(right).runCollect
      assert(result)(equalTo(Left(RightErr(404))))
    },
    test("type ascription compiles") {
      val _ = Stream.succeed("a").choice(Stream.succeed(1))
      assertTrue(true)
    },
    test("multiple elements per side") {
      val result = Stream("a", "b").choice(Stream(1, 2)).runCollect
      assert(result.map(_.map(elem => Choices.separate[String, Int](elem))))(
        equalTo(Right(Chunk(Left("a"), Left("b"), Right(1), Right(2))))
      )
    }
  )
}
