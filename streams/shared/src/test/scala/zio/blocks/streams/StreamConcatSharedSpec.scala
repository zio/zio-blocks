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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
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
      runAsync(result.runCollectAsync).map { result =>
        val normalized = result.map(_.map(separateStringInt))
        assert(normalized)(equalTo(Right(Chunk(Left("hello"), Right(42)))))
      }
    },
    test("same element type stays the same") {
      val result = Stream.succeed("hello") ++ Stream.succeed("world")
      runAsync(result.runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk("hello", "world")))))
    },
    test("subtype element type widens to supertype") {
      val result = (Stream.succeed(Dog("fido")): Stream[Nothing, Dog]) ++ Stream.succeed[Animal](Cat("milo"))
      runAsync(result.runCollectAsync).map { result =>
        assert(result)(equalTo(Right(Chunk[Animal](Dog("fido"), Cat("milo")))))
      }
    },
    test("empty left stream") {
      val result: Stream[Nothing, String | Int] = (Stream.empty: Stream[Nothing, String]) ++ Stream.succeed(42)
      runAsync(result.runCollectAsync).map { result =>
        val normalized = result.map(_.map(separateStringInt))
        assert(normalized)(equalTo(Right(Chunk(Right(42)))))
      }
    },
    test("empty right stream") {
      val result: Stream[Nothing, String | Int] = Stream.succeed("hello") ++ (Stream.empty: Stream[Nothing, Int])
      runAsync(result.runCollectAsync).map { result =>
        val normalized = result.map(_.map(separateStringInt))
        assert(normalized)(equalTo(Right(Chunk(Left("hello")))))
      }
    },
    test("left stream error propagation") {
      val result = (Stream.fail("boom"): Stream[String, String]) ++ Stream.succeed(42)
      runAsync(result.runCollectAsync).map(result => assert(result)(equalTo(Left("boom"))))
    },
    test("unrelated error types widen to common supertype") {
      sealed trait AppError
      final case class LeftErr(msg: String) extends AppError
      final case class RightErr(code: Int)  extends AppError

      val left: Stream[LeftErr, String] = Stream.fail(LeftErr("oops"))
      val right: Stream[RightErr, Int]  = Stream.succeed(42)
      val result                        = left ++ right
      runAsync(result.runCollectAsync).map { result =>
        val actual = result.left.map(err => err: AppError)
        assert(actual)(equalTo(Left(LeftErr("oops"): AppError)))
      }
    },
    test("right stream error propagation") {
      sealed trait AppError
      final case class LeftErr(msg: String) extends AppError
      final case class RightErr(code: Int)  extends AppError

      val left: Stream[LeftErr, String] = Stream.succeed("ok")
      val right: Stream[RightErr, Int]  = Stream.fail(RightErr(404))
      val result                        = left ++ right
      runAsync(result.runCollectAsync).map { result =>
        val actual = result.left.map(err => err: AppError)
        assert(actual)(equalTo(Left(RightErr(404): AppError)))
      }
    },
    test("two-stream primitive union type ascription compiles") {
      val _: Stream[Nothing, String | Int] = Stream.succeed("a") ++ Stream.succeed(1)
      assertTrue(true)
    },
    test("multiple elements per side") {
      val result: Stream[Nothing, String | Int] = Stream("a", "b") ++ Stream(1, 2)
      runAsync(result.runCollectAsync).map { result =>
        val normalized = result.map(_.map(separateStringInt))
        assert(normalized)(equalTo(Right(Chunk(Left("a"), Left("b"), Right(1), Right(2)))))
      }
    },
    test("three-stream primitive union type ascription compiles") {
      val _: Stream[Nothing, String | Int | Boolean] =
        Stream.succeed("hello") ++ Stream.succeed(42) ++ Stream.succeed(true)
      assertTrue(true)
    },
    test("three-stream concatenation") {
      val result: Stream[Nothing, Word | Count | Flag] =
        Stream.succeed(Word("hello")) ++ Stream.succeed(Count(42)) ++ Stream.succeed(Flag(true))

      runAsync(result.runCollectAsync).map { result =>
        val tagged = result.map(_.map(tagWordCountFlag))
        assert(tagged)(equalTo(Right(Chunk(TaggedString("hello"), TaggedInt(42), TaggedBoolean(true)))))
      }
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

      runAsync(result.runCollectAsync).map { result =>
        val tagged = result.map(_.map(tagWordCountFlagRatio))
        assert(tagged)(
          equalTo(Right(Chunk(TaggedString("hello"), TaggedInt(42), TaggedBoolean(true), TaggedDouble(3.14))))
        )
      }
    },
    test("reader concat preserves the exhausted segment close failure") {
      val closeFailure = new RuntimeException("head-close")
      var tailCreated  = false
      var closes       = 0
      val head         = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def read[A1 >: Int](sentinel: A1): A1                                = sentinel
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = sentinel
        def close(): Unit                                                    = { closes += 1; throw closeFailure }
      }
      val reader = head.concatWithJvmType(
        { () =>
          tailCreated = true
          Reader.fromChunk(Chunk(1))
        },
        JvmType.Int
      )

      val caught = try { reader.readInt(Long.MinValue); null }
      catch { case t: Throwable => t }
      val replay = try { reader.readInt(Long.MinValue); null }
      catch { case t: Throwable => t }
      val closeReplay = scala.util.Try(reader.close()).failed.toOption.orNull

      assertTrue(caught eq closeFailure, replay eq closeFailure, closeReplay eq closeFailure, closes == 1, !tailCreated)
    },
    test("reader concat bulk reads skip arbitrary consecutive empty segments") {
      def ints = Reader
        .fromChunk(Chunk.empty[Int])
        .concatWithJvmType(() => Reader.fromChunk(Chunk.empty[Int]), JvmType.Int)
        .concatWithJvmType(() => Reader.fromChunk(Chunk(1, 2)), JvmType.Int)
      def longs = Reader
        .fromChunk(Chunk.empty[Long])
        .concatWithJvmType(() => Reader.fromChunk(Chunk.empty[Long]), JvmType.Long)
        .concatWithJvmType(() => Reader.fromChunk(Chunk(3L)), JvmType.Long)
      def floats = Reader
        .fromChunk(Chunk.empty[Float])
        .concatWithJvmType(() => Reader.fromChunk(Chunk.empty[Float]), JvmType.Float)
        .concatWithJvmType(() => Reader.fromChunk(Chunk(4.0f)), JvmType.Float)
      def doubles = Reader
        .fromChunk(Chunk.empty[Double])
        .concatWithJvmType(() => Reader.fromChunk(Chunk.empty[Double]), JvmType.Double)
        .concatWithJvmType(() => Reader.fromChunk(Chunk(5.0)), JvmType.Double)
      val ib   = new Array[Int](2); val lb           = new Array[Long](1); val fb = new Array[Float](1);
      val db   = new Array[Double](1)
      val in   = ints.readInts(ib, 0, 2); val ln     = longs.readLongs(lb, 0, 1)
      val fn   = floats.readFloats(fb, 0, 1); val dn = doubles.readDoubles(db, 0, 1)
      val refs = Reader
        .fromChunk(Chunk.empty[String])
        .concat(() => Reader.fromChunk(Chunk.empty[String]))
        .concat(() => Reader.fromChunk(Chunk("later")))

      assertTrue(
        in == 2,
        ib.toList == List(1, 2),
        ln == 1,
        lb(0) == 3L,
        fn == 1,
        fb(0) == 4.0f,
        dn == 1,
        db(0) == 5.0,
        refs.readUpToN[String](1) == Chunk("later")
      )
    },
    test("reader concat preserves active final segment close failure") {
      val closeFailure = new RuntimeException("tail-close")
      var closes       = 0
      val tail         = new Reader.SyncReader[Int] {
        private var emitted                                                  = false
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = emitted
        def read[A1 >: Int](sentinel: A1): A1                                = readInt(Long.MinValue).asInstanceOf[A1]
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (emitted) sentinel
          else { emitted = true; 2L }
        def close(): Unit = {
          closes += 1
          throw closeFailure
        }
      }
      val reader = Reader.fromChunk(Chunk(1)).concatWithJvmType(() => tail, JvmType.Int)

      val first      = reader.readInt(Long.MinValue)
      val second     = reader.readInt(Long.MinValue)
      val firstClose = scala.util.Try(reader.close()).failed.toOption.orNull
      val nextClose  = scala.util.Try(reader.close()).failed.toOption.orNull

      assertTrue(
        first == 1L,
        second == 2L,
        firstClose eq closeFailure,
        nextClose eq closeFailure,
        closes == 1
      )
    },
    test("mapError reader reset remains unsupported") {
      val reader = Stream.fail("boom").mapError(identity).compile(0).expectedSync
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
