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
import zio.test._
import zio.test.Assertion._

/**
 * Cross-version tests for `Stream.++` / `Stream.concat`.
 *
 * Disjoint concat results are normalized in the assertions so the shared tests
 * stay identical on Scala 2 (`|` = `Either`) and Scala 3 (native unions).
 */
object StreamConcatSharedSpec extends StreamsBaseSpec {

  final case class Word(value: String)
  final case class Count(value: Int)
  final case class Flag(value: Boolean)
  final case class Ratio(value: Double)

  sealed trait TaggedValue
  final case class TaggedString(value: String)   extends TaggedValue
  final case class TaggedInt(value: Int)         extends TaggedValue
  final case class TaggedBoolean(value: Boolean) extends TaggedValue
  final case class TaggedDouble(value: Double)   extends TaggedValue

  sealed trait Animal
  final case class Dog(name: String) extends Animal
  final case class Cat(name: String) extends Animal

  private def tagStringIntBoolean(
    value: Either[Either[String, Int], Boolean]
  ): TaggedValue =
    value match {
      case Left(Left(value))  => TaggedString(value)
      case Left(Right(value)) => TaggedInt(value)
      case Right(value)       => TaggedBoolean(value)
    }

  private def tagStringIntBooleanDouble(
    value: Either[Either[Either[String, Int], Boolean], Double]
  ): TaggedValue =
    value match {
      case Left(left)   => tagStringIntBoolean(left)
      case Right(value) => TaggedDouble(value)
    }

  private def tagWordCountFlag(value: Any): TaggedValue =
    value match {
      case Word(value)               => TaggedString(value)
      case Count(value)              => TaggedInt(value)
      case Flag(value)               => TaggedBoolean(value)
      case Left(Left(Word(value)))   => TaggedString(value)
      case Left(Right(Count(value))) => TaggedInt(value)
      case Right(Flag(value))        => TaggedBoolean(value)
    }

  private def tagWordCountFlagRatio(
    value: Any
  ): TaggedValue =
    value match {
      case Word(value)         => TaggedString(value)
      case Count(value)        => TaggedInt(value)
      case Flag(value)         => TaggedBoolean(value)
      case Ratio(value)        => TaggedDouble(value)
      case Left(left)          => tagWordCountFlag(left)
      case Right(Ratio(value)) => TaggedDouble(value)
    }

  def spec: Spec[TestEnvironment, Any] = suite("Stream.++ / concat (shared)")(
    test("disjoint concatenation") {
      val result: Stream[Nothing, String | Int] = Stream.succeed("hello") ++ Stream.succeed(42)
      val normalized                            = result.runCollect.map(_.map(separateStringInt))
      assert(normalized)(
        equalTo(Right(Chunk(Left("hello"), Right(42))))
      )
    },
    test("same element type stays the same") {
      val result = Stream.succeed("hello") ++ Stream.succeed("world")
      assert(result.runCollect)(equalTo(Right(Chunk("hello", "world"))))
    },
    test("subtype element type widens to supertype") {
      val result = (Stream.succeed(Dog("fido")): Stream[Nothing, Dog]) ++ Stream.succeed[Animal](Cat("milo"))
      assert(result.runCollect)(equalTo(Right(Chunk[Animal](Dog("fido"), Cat("milo")))))
    },
    test("empty left stream") {
      val result: Stream[Nothing, String | Int] = (Stream.empty: Stream[Nothing, String]) ++ Stream.succeed(42)
      val normalized = result.runCollect.map(_.map(separateStringInt))
      assert(normalized)(
        equalTo(Right(Chunk(Right(42))))
      )
    },
    test("empty right stream") {
      val result: Stream[Nothing, String | Int] = Stream.succeed("hello") ++ (Stream.empty: Stream[Nothing, Int])
      val normalized = result.runCollect.map(_.map(separateStringInt))
      assert(normalized)(
        equalTo(Right(Chunk(Left("hello"))))
      )
    },
    test("left stream error propagation") {
      val result = (Stream.fail("boom"): Stream[String, String]) ++ Stream.succeed(42)
      assert(result.runCollect)(equalTo(Left("boom")))
    },
    test("unrelated error types widen to common supertype") {
      sealed trait AppError
      final case class LeftErr(msg: String) extends AppError
      final case class RightErr(code: Int)  extends AppError

      val left: Stream[LeftErr, String] = Stream.fail(LeftErr("oops"))
      val right: Stream[RightErr, Int]  = Stream.succeed(42)
      val result                        = left ++ right
      val actual                        =
        result.runCollect.left.map(err => err: AppError)
      assert(actual)(equalTo(Left(LeftErr("oops"): AppError)))
    },
    test("right stream error propagation") {
      sealed trait AppError
      final case class LeftErr(msg: String) extends AppError
      final case class RightErr(code: Int)  extends AppError

      val left: Stream[LeftErr, String] = Stream.succeed("ok")
      val right: Stream[RightErr, Int]  = Stream.fail(RightErr(404))
      val result                        = left ++ right
      val actual                        =
        result.runCollect.left.map(err => err: AppError)
      assert(actual)(equalTo(Left(RightErr(404): AppError)))
    },
    test("two-stream primitive union type ascription compiles") {
      val _: Stream[Nothing, String | Int] = Stream.succeed("a") ++ Stream.succeed(1)
      assertTrue(true)
    },
    test("multiple elements per side") {
      val result: Stream[Nothing, String | Int] = Stream("a", "b") ++ Stream(1, 2)
      val normalized = result.runCollect.map(_.map(separateStringInt))
      assert(normalized)(
        equalTo(Right(Chunk(Left("a"), Left("b"), Right(1), Right(2))))
      )
    },
    test("three-stream primitive union type ascription compiles") {
      val _: Stream[Nothing, String | Int | Boolean] =
        Stream.succeed("hello") ++ Stream.succeed(42) ++ Stream.succeed(true)
      assertTrue(true)
    },
    test("three-stream concatenation") {
      val result: Stream[Nothing, Word | Count | Flag] =
        Stream.succeed(Word("hello")) ++ Stream.succeed(Count(42)) ++ Stream.succeed(Flag(true))

      val tagged = result.runCollect.map(
        _.map(tagWordCountFlag)
      )

      assert(tagged)(
        equalTo(Right(Chunk(TaggedString("hello"), TaggedInt(42), TaggedBoolean(true))))
      )
    },
    test("four-stream primitive union type ascription compiles") {
      val _: Stream[Nothing, String | Int | Boolean | Double] =
        Stream.succeed("hello") ++ Stream.succeed(42) ++ Stream.succeed(true) ++ Stream.succeed(3.14)
      assertTrue(true)
    },
    test("four-stream concatenation") {
      val result: Stream[Nothing, Word | Count | Flag | Ratio] =
        Stream.succeed(Word("hello")) ++ Stream.succeed(Count(42)) ++ Stream.succeed(Flag(true)) ++ Stream.succeed(
          Ratio(3.14)
        )

      val tagged = result.runCollect.map(
        _.map(tagWordCountFlagRatio)
      )

      assert(tagged)(
        equalTo(Right(Chunk(TaggedString("hello"), TaggedInt(42), TaggedBoolean(true), TaggedDouble(3.14))))
      )
    },
    test("mapError reader reset remains unsupported") {
      val reader = Stream.fail("boom").mapError(identity).compile(0)
      val result =
        try {
          reader.reset()
          false
        } catch {
          case e: UnsupportedOperationException => e.getMessage == "ErrorMapped does not support reset"
        }

      assertTrue(result)
    }
  )
}
