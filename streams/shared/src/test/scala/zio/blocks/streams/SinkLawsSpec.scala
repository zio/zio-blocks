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

import zio.ZIO
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._
import StreamsGen._

/**
 * Laws and unit tests for [[Sink]].
 *
 * Laws tested:
 *   - Functor: `map(id) ≡ id`, `map(f).map(g) ≡ map(f andThen g)`
 *   - Contravariant: `contramap(id) ≡ id`,
 *     `contramap(f).contramap(g) ≡ contramap(g andThen f)`
 *   - mapError identity
 *
 * Constructors tested: `drain`, `count`, `collectAll`, `foldLeft`, `foreach`,
 * `head`, `last`, `take`, `sumInt`, `sumLong`, `createAsync`, `createBoth`.
 */
object SinkLawsSpec extends StreamsBaseSpec {

  private def collect[A](s: Stream[Nothing, A]): ZIO[Any, Throwable, Chunk[A]] =
    runAsync(s.runCollectAsync).map(_.fold(e => throw new RuntimeException(s"unexpected error: $e"), identity))

  private def runSink[A, Z](s: Stream[Nothing, A], sink: Sink[Nothing, A, Z]): ZIO[Any, Throwable, Z] =
    runAsync(s.runAsync(sink)).map(_.fold(e => throw new RuntimeException(s"unexpected error: $e"), identity))

  def spec: Spec[TestEnvironment, Any] = suite("Sink laws")(
    test("async sink reader facade delegates every generic, control, scalar, and bulk operation") {
      def refs    = Reader.fromChunk(Chunk[AnyRef]("a", "b")).toAsync
      def ints    = Reader.fromChunk(Chunk(1, 2)).toAsync
      def longs   = Reader.fromChunk(Chunk(1L, 2L)).toAsync
      def floats  = Reader.fromChunk(Chunk(1.0f, 2.0f)).toAsync
      def doubles = Reader.fromChunk(Chunk(1.0, 2.0)).toAsync
      def bytes   = Reader.fromChunk(Chunk(1.toByte, 2.toByte)).toAsync
      def bools   = Reader.fromChunk(Chunk(true, false)).toAsync
      def chars   = Reader.fromChunk(Chunk('a', 'b')).toAsync
      def shorts  = Reader.fromChunk(Chunk(1.toShort, 2.toShort)).toAsync
      for {
        jvmType    <- runAsync(Sink.readerCallbackAsync(refs)(r => Async.succeed(r.jvmType)))
        closed     <- runAsync(Sink.readerCallbackAsync(refs)(_.isClosed))
        readable   <- runAsync(Sink.readerCallbackAsync(refs)(_.readable()))
        one        <- runAsync(Sink.readerCallbackAsync(refs)(_.read[AnyRef]("eof")))
        all        <- runAsync(Sink.readerCallbackAsync(refs)(_.readAll[AnyRef]()))
        n          <- runAsync(Sink.readerCallbackAsync(refs)(_.readN[AnyRef](1)))
        upTo       <- runAsync(Sink.readerCallbackAsync(refs)(_.readUpToN[AnyRef](1)))
        skipped    <- runAsync(Sink.readerCallbackAsync(refs)(_.skip(1)))
        limit      <- runAsync(Sink.readerCallbackAsync(refs)(_.setLimit(1)))
        repeat     <- runAsync(Sink.readerCallbackAsync(refs)(_.setRepeat()))
        skip       <- runAsync(Sink.readerCallbackAsync(refs)(_.setSkip(1)))
        reset      <- runAsync(Sink.readerCallbackAsync(refs)(_.reset()))
        close      <- runAsync(Sink.readerCallbackAsync(refs)(_.close()))
        boolean    <- runAsync(Sink.readerCallbackAsync(bools)(_.readBoolean(-1)))
        byte       <- runAsync(Sink.readerCallbackAsync(bytes)(_.readByte()))
        char       <- runAsync(Sink.readerCallbackAsync(chars)(_.readChar(-1)))
        short      <- runAsync(Sink.readerCallbackAsync(shorts)(_.readShort(Int.MinValue)))
        int        <- runAsync(Sink.readerCallbackAsync(ints)(_.readInt(Long.MinValue)))
        long       <- runAsync(Sink.readerCallbackAsync(longs)(_.readLong(Long.MinValue)))
        float      <- runAsync(Sink.readerCallbackAsync(floats)(_.readFloat(Double.NaN)))
        double     <- runAsync(Sink.readerCallbackAsync(doubles)(_.readDouble(Double.NaN)))
        byteArray   = new Array[Byte](2)
        intArray    = new Array[Int](2)
        longArray   = new Array[Long](2)
        floatArray  = new Array[Float](2)
        doubleArray = new Array[Double](2)
        byteN      <- runAsync(Sink.readerCallbackAsync(bytes)(_.readBytes(byteArray, 0, 2)))
        intN       <- runAsync(Sink.readerCallbackAsync(ints)(_.readInts(intArray, 0, 2)))
        longN      <- runAsync(Sink.readerCallbackAsync(longs)(_.readLongs(longArray, 0, 2)))
        floatN     <- runAsync(Sink.readerCallbackAsync(floats)(_.readFloats(floatArray, 0, 2)))
        doubleN    <- runAsync(Sink.readerCallbackAsync(doubles)(_.readDoubles(doubleArray, 0, 2)))
      } yield assertTrue(
        jvmType == JvmType.AnyRef,
        !closed,
        readable,
        one == "a",
        all == Chunk[AnyRef]("a", "b"),
        n == Chunk[AnyRef]("a"),
        upTo == Chunk[AnyRef]("a"),
        skipped == (),
        limit,
        !repeat,
        skip,
        reset == (),
        close == (),
        boolean == 1,
        byte == 1,
        char == 'a'.toInt,
        short == 1,
        int == 1L,
        long == 1L,
        float == 1.0,
        double == 1.0,
        byteN == 2 && byteArray.toSeq == Seq[Byte](1, 2),
        intN == 2 && intArray.toSeq == Seq(1, 2),
        longN == 2 && longArray.toSeq == Seq(1L, 2L),
        floatN == 2 && floatArray.toSeq == Seq(1.0f, 2.0f),
        doubleN == 2 && doubleArray.toSeq == Seq(1.0, 2.0)
      )
    },
    test("async sink transforms execute success and typed failure paths") {
      val transformed = Sink
        .collectAll[Int]
        .contramapAsync[Int, String](value => Async.succeed(value.toInt))
        .mapAsync(values => Async.succeed(values.foldLeft(0)(_ + _)))
      for {
        sum     <- runAsync(Stream("1", "2", "3").runAsync(transformed))
        failure <-
          runAsync(Stream.empty.runAsync(Sink.fail("boom").mapErrorAsync(error => Async.succeed(error + "-async"))))
      } yield assertTrue(sum == Right(6), failure == Left("boom-async"))
    },
    test("synchronous numeric folds preserve Boolean, Char, and Short primitive lanes") {
      val booleans = new Sink.FoldLeftInt[Boolean](0, (n, value) => n + (if (value) 1 else 0))
      val chars    = new Sink.FoldLeftLong[Char](0L, (n, value) => n + value.toLong)
      val shorts   = new Sink.FoldLeftDouble[Short](0.0, (n, value) => n + value.toDouble)
      assertTrue(
        booleans.drain(Reader.fromChunk(Chunk(true, false, true))) == 2,
        chars.drain(Reader.fromChunk(Chunk('A', 'B'))) == 131L,
        shorts.drain(Reader.fromChunk(Chunk(1.toShort, 2.toShort, 3.toShort))) == 6.0
      )
    },
    // ---- Functor ------------------------------------------------------------

    suite("Functor")(
      test("map(identity) ≡ identity") {
        check(genIntStream) { s =>
          for {
            data  <- collect(s)
            left  <- runSink(Stream.fromChunk(data), Sink.collectAll[Int].map(identity))
            right <- runSink(Stream.fromChunk(data), Sink.collectAll[Int])
          } yield assert(left)(equalTo(right))
        }
      },
      test("map(f).map(g) ≡ map(f andThen g)") {
        check(genIntStream, Gen.function[Any, Long, Long](Gen.long), Gen.function[Any, Long, Long](Gen.long)) {
          (s, f, g) =>
            for {
              data <- collect(s)
              r1   <- runSink(Stream.fromChunk(data), Sink.sumInt.map(f).map(g))
              r2   <- runSink(Stream.fromChunk(data), Sink.sumInt.map(f andThen g))
            } yield assert(r1)(equalTo(r2))
        }
      }
    ),

    // ---- Contravariant functor ----------------------------------------------

    suite("Contravariant")(
      test("contramap(identity) ≡ identity") {
        check(genIntStream) { s =>
          for {
            data  <- collect(s)
            left  <- runSink(Stream.fromChunk(data), Sink.collectAll[Int].contramap[Int, Int](identity))
            right <- runSink(Stream.fromChunk(data), Sink.collectAll[Int])
          } yield assert(left)(equalTo(right))
        }
      },
      test("contramap(f).contramap(g) ≡ contramap(g andThen f)") {
        check(genIntStream, Gen.function[Any, Int, Int](genInt), Gen.function[Any, Int, Int](genInt)) { (s, f, g) =>
          for {
            data <- collect(s)
            r1   <- runSink(Stream.fromChunk(data), Sink.sumInt.contramap[Int, Int](f).contramap(g))
            r2   <- runSink(Stream.fromChunk(data), Sink.sumInt.contramap[Int, Int](g andThen f))
          } yield assert(r1)(equalTo(r2))
        }
      }
    ),

    // ---- mapError -----------------------------------------------------------

    suite("mapError")(
      test("mapError identity ≡ identity on success") {
        check(genIntStream) { s =>
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.drain.mapError[Nothing](identity)))
              .map(result => assert(result)(equalTo(Right(()))))
          )
        }
      },
      test("mapError transforms the error") {
        val sink: Sink[String, Int, Unit] = Sink.drain.mapError[String](identity)
        runAsync(Stream.fail("oops").runAsync(sink)).map(result => assert(result)(equalTo(Left("oops"))))
      }
    ),

    // ---- Constructors -------------------------------------------------------

    suite("Constructors")(
      test("drain discards all elements") {
        check(genIntStream) { s =>
          runAsync(s.runAsync(Sink.drain)).map(result => assert(result)(equalTo(Right(()))))
        }
      },
      test("drain preserves observable iteration for arbitrary Iterable sources") {
        var pulls  = 0
        val values = new Iterable[Int] {
          def iterator: Iterator[Int] = Iterator(1, 2, 3).map { value => pulls += 1; value }
        }
        val reader = Reader.fromIterable(values)
        Sink.drain.drain(reader)
        assertTrue(pulls == 3, reader.isClosed)
      },
      test("Vector Int readers push down skip and limit and reset to the configured slice") {
        val reader  = Reader.fromIterable(Vector(1, 2, 3, 4, 5))
        val skipped = reader.setSkip(2)
        val limited = reader.setLimit(2)
        val first   = reader.readInt(Long.MinValue)
        reader.skip(1)
        val end = reader.readInt(Long.MinValue)
        reader.reset()
        val reset = reader.readInt(Long.MinValue)
        assertTrue(skipped, limited, first == 3L, end == Long.MinValue, reset == 3L)
      },
      test("Vector Int streams directly fold take, drop/take, and takeWhile slices") {
        val stream    = Stream.fromIterable(Vector(1, 2, 3, 4, 5))
        val take      = stream.take(3).runFoldLongBlocking(0L, _ + _)
        val takeDrop  = stream.drop(2).take(2).runFoldLongBlocking(0L, _ + _)
        val takeWhile = stream.takeWhile(_ < 4).runFoldLongBlocking(0L, _ + _)
        assertTrue(take == Right(6L), takeDrop == Right(7L), takeWhile == Right(6L))
      },
      test("count counts all elements") {
        check(genIntStream) { s =>
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.count))
              .map(result => assert(result)(equalTo(Right(data.length.toLong))))
          )
        }
      },
      test("collectAll collects all elements") {
        check(genIntStream) { s =>
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.collectAll[Int]))
              .map(result => assert(result)(equalTo(Right(data))))
          )
        }
      },
      test("foldLeft agrees with Chunk.foldLeft") {
        check(genIntStream, genInt, Gen.function2(genInt)) { (s, z, f) =>
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.foldLeft(z)(f)))
              .map(result => assert(result)(equalTo(Right(data.foldLeft(z)(f)))))
          )
        }
      },
      test("foreach calls f for each element") {
        check(genIntStream) { s =>
          val buf = new scala.collection.mutable.ArrayBuffer[Int]()
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.foreach[Int](a => buf += a)))
              .map(_ => assert(buf.toList)(equalTo(data.toList)))
          )
        }
      },
      test("head returns first element") {
        check(genIntStream) { s =>
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.head[Int]))
              .map(result => assert(result)(equalTo(Right(data.headOption))))
          )
        }
      },
      test("last returns last element") {
        check(genIntStream) { s =>
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.last[Int]))
              .map(result => assert(result)(equalTo(Right(data.lastOption))))
          )
        }
      },
      test("take(n) collects first n elements") {
        check(genIntStream, Gen.int(0, 20)) { (s, n) =>
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.take[Int](n)))
              .map(result => assert(result)(equalTo(Right(data.take(n)))))
          )
        }
      },
      test("sumInt sums all ints") {
        check(genIntStream) { s =>
          collect(s).flatMap(data =>
            runAsync(Stream.fromChunk(data).runAsync(Sink.sumInt))
              .map(result => assert(result)(equalTo(Right(data.foldLeft(0L)(_ + _.toLong)))))
          )
        }
      },
      test("sumLong sums all longs") {
        check(Gen.chunkOfBounded(0, 20)(Gen.long(-1000, 1000)).map(Chunk.fromIterable(_))) { data =>
          runAsync(Stream.fromChunk(data).runAsync(Sink.sumLong))
            .map(result => assert(result)(equalTo(Right(data.foldLeft(0L)(_ + _)))))
        }
      },
      test("sumFloat sums all floats into Double") {
        check(Gen.chunkOfBounded(0, 20)(Gen.int(-1000, 1000).map(_.toFloat)).map(Chunk.fromIterable(_))) { data =>
          runAsync(Stream.fromChunk(data).runAsync(Sink.sumFloat))
            .map(result => assert(result)(equalTo(Right(data.foldLeft(0.0)(_ + _.toDouble)))))
        }
      },
      test("sumDouble sums all doubles") {
        check(Gen.chunkOfBounded(0, 20)(Gen.double(-1000.0, 1000.0)).map(Chunk.fromIterable(_))) { data =>
          runAsync(Stream.fromChunk(data).runAsync(Sink.sumDouble))
            .map(result => assert(result)(equalTo(Right(data.foldLeft(0.0)(_ + _)))))
        }
      },
      test("createAsync and createBoth receive the dequeue and return its elements") {
        val asyncSink = Sink.createAsync[Nothing, Int, Chunk[Int]](_.readAll[Int]())
        val bothSink  = Sink.createBoth[Nothing, Int, Chunk[Int]](
          _.readAll[Int](),
          _.readAll[Int]()
        )
        check(genIntStream) { s =>
          for {
            data        <- collect(s)
            asyncResult <- runAsync(Stream.fromChunk(data).runAsync(asyncSink))
            bothResult  <- runAsync(Stream.fromChunk(data).runAsync(bothSink))
          } yield assertTrue(asyncResult == Right(data), bothResult == Right(data))
        }
      },
      test("Long and Double extrema remain data through operators and sinks") {
        val longs        = Stream(Long.MaxValue, 1L)
        val doubles      = Stream(Double.MaxValue, Double.NaN, 1.0)
        val seenLongs    = scala.collection.mutable.ListBuffer.empty[Long]
        val seenDoubles  = scala.collection.mutable.ListBuffer.empty[Double]
        val mappedLongs  = Stream(0, 1).map(i => if (i == 0) Long.MaxValue else 1L)
        val mappedDouble = Stream(0, 1).map(i => if (i == 0) Double.MaxValue else 1.0)
        for {
          longCount       <- runAsync(longs.countAsync)
          longValues      <- runAsync(longs.runCollectAsync)
          longHead        <- runAsync(longs.headAsync)
          longLast        <- runAsync(longs.lastAsync)
          longExists      <- runAsync(longs.existsAsync(v => Async.succeed(v == Long.MaxValue)))
          longForall      <- runAsync(longs.forallAsync(v => Async.succeed(v > 0L)))
          longFind        <- runAsync(longs.findAsync(v => Async.succeed(v == Long.MaxValue)))
          longTake        <- runAsync(longs.runAsync(Sink.take[Long](2)))
          longForeach     <- runAsync(longs.runForeachAsync(v => Async.succeed { seenLongs += v; () }))
          doubleCount     <- runAsync(doubles.countAsync)
          doubleValues    <- runAsync(doubles.runCollectAsync)
          doubleHead      <- runAsync(doubles.headAsync)
          doubleLast      <- runAsync(doubles.lastAsync)
          doubleExists    <- runAsync(doubles.existsAsync(v => Async.succeed(v.isNaN)))
          doubleFind      <- runAsync(doubles.findAsync(v => Async.succeed(v.isNaN)))
          doubleForeach   <- runAsync(doubles.runForeachAsync(v => Async.succeed { seenDoubles += v; () }))
          filteredLongs   <- runAsync(mappedLongs.filter(_ > 0L).takeWhile(_ > 0L).runCollectAsync)
          scannedLongs    <- runAsync(mappedLongs.scan(0L)(_ ^ _).runCollectAsync)
          slidingLongs    <- runAsync(mappedLongs.sliding(2, 1).runCollectAsync)
          filteredDoubles <- runAsync(mappedDouble.filter(_ > 0.0).takeWhile(_ > 0.0).runCollectAsync)
          scannedDoubles  <- runAsync(mappedDouble.scan(0.0)(_ + _).runCollectAsync)
        } yield assertTrue(
          longCount == Right(2L),
          longValues == Right(Chunk(Long.MaxValue, 1L)),
          longHead == Right(Some(Long.MaxValue)),
          longLast == Right(Some(1L)),
          longExists == Right(true),
          longForall == Right(true),
          longFind == Right(Some(Long.MaxValue)),
          longTake == Right(Chunk(Long.MaxValue, 1L)),
          longForeach == Right(()),
          seenLongs.toList == List(Long.MaxValue, 1L),
          doubleCount == Right(3L),
          doubleValues.exists(chunk =>
            chunk.length == 3 && chunk(0) == Double.MaxValue && chunk(1).isNaN && chunk(2) == 1.0
          ),
          doubleHead == Right(Some(Double.MaxValue)),
          doubleLast == Right(Some(1.0)),
          doubleExists == Right(true),
          doubleFind.exists(_.exists(_.isNaN)),
          doubleForeach == Right(()),
          seenDoubles.length == 3 && seenDoubles.head == Double.MaxValue && seenDoubles(1).isNaN,
          filteredLongs == Right(Chunk(Long.MaxValue, 1L)),
          scannedLongs == Right(Chunk(0L, Long.MaxValue, Long.MaxValue - 1L)),
          slidingLongs == Right(Chunk(Chunk(Long.MaxValue, 1L))),
          filteredDoubles == Right(Chunk(Double.MaxValue, 1.0)),
          scannedDoubles == Right(Chunk(0.0, Double.MaxValue, Double.MaxValue))
        )
      },
      test("drain propagates upstream error") {
        runAsync(Stream.fail("err").runAsync(Sink.drain)).map(result => assert(result)(equalTo(Left("err"))))
      },
      test("collectAll propagates upstream error") {
        runAsync(Stream.fail("err").runAsync(Sink.collectAll[Int]))
          .map(result => assert(result)(equalTo(Left("err"))))
      }
    )
  )
}
