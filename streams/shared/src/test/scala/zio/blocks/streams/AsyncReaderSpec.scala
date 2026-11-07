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

import scala.concurrent.ExecutionContext
import scala.util.Try
import zio._
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncReaderSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }
  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private def jvm(name: String)(body: => TestResult) =
    test(name)(if (TestPlatform.isJVM) body else assertTrue(true))

  private abstract class IntSyncReader extends Reader.SyncReader[Int] {
    final override def jvmType: JvmType                                  = JvmType.Int
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
      read[AnyVal](sentinel).asInstanceOf[Number].longValue()
  }

  private abstract class IntAsyncReader extends Reader.AsyncReader[Int] {
    final override def jvmType: JvmType                                         = JvmType.Int
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
      read[AnyVal](sentinel).map(_.asInstanceOf[Number].longValue())
  }

  def spec = suite("AsyncReader")(
    test("production hierarchy preserves static result kinds and RHS laziness") {
      val sync                        = Reader.singleInt(1)
      val async                       = Reader.singleInt(2).toAsync
      var forced                      = 0
      val ss: Reader.SyncReader[Int]  = sync ++ Reader.singleInt(2)
      val sa: Reader.AsyncReader[Int] = Reader.singleInt(1) ++ async
      val as: Reader.AsyncReader[Int] = async ++ Reader.singleInt(3)
      val aa: Reader.AsyncReader[Int] = async ++ Reader.singleInt(4).toAsync
      val lazyReader                  = Reader.singleInt(1) ++ { forced += 1; Reader.singleInt(2).toAsync }
      assertTrue(ss.isInstanceOf[Reader.SyncReader[_]]) &&
      assertTrue(sa.isInstanceOf[Reader.AsyncReader[_]]) &&
      assertTrue(as.isInstanceOf[Reader.AsyncReader[_]]) &&
      assertTrue(aa.isInstanceOf[Reader.AsyncReader[_]]) &&
      assertTrue(lazyReader.isInstanceOf[Reader.AsyncReader[_]], forced == 0)
    },
    test("all sync and async concat combinations preserve values") {
      def values(reader: Reader.AsyncReader[Int]): Chunk[Int] = reader.readAll[Int]().block
      val ss                                                  = (Reader.singleInt(1) ++ Reader.singleInt(2)).toAsync
      val sa                                                  = Reader.singleInt(1) ++ Reader.singleInt(2).toAsync
      val as                                                  = Reader.singleInt(1).toAsync ++ Reader.singleInt(2)
      val aa                                                  = Reader.singleInt(1).toAsync ++ Reader.singleInt(2).toAsync
      assertTrue(
        values(ss) == Chunk(1, 2),
        values(sa) == Chunk(1, 2),
        values(as) == Chunk(1, 2),
        values(aa) == Chunk(1, 2)
      )
    },
    test("widened concat preserves values across primitive lanes") {
      val expected = Chunk[AnyVal](1, 4294967297L)
      val sync     = Reader.singleInt(1).concat[AnyVal](() => Reader.singleLong(4294967297L))
      val mixed    = Reader.singleInt(1).toAsync.concat[AnyVal](() => Reader.singleLong(4294967297L))
      assertTrue(
        sync.readAll[AnyVal]() == expected,
        mixed.readAll[AnyVal]().block == expected,
        sync.jvmType == JvmType.AnyRef,
        mixed.jvmType == JvmType.AnyRef
      )
    },
    test("toAsync isClosed does not discard buffered output") {
      val source = new IntSyncReader {
        private var pending                = true
        def close(): Unit                  = ()
        def isClosed: Boolean              = true
        def read[A >: Int](sentinel: A): A =
          if (pending) { pending = false; 1.asInstanceOf[A] }
          else sentinel
      }
      val reader = source.toAsync
      assertTrue(reader.isClosed.block, reader.readInt(-1L).block == 1L, reader.readInt(-1L).block == -1L)
    },
    test("concat closes its final segment and latches final EOF") {
      var closes = 0
      val tail   = new IntSyncReader {
        private var emitted                = false
        def close(): Unit                  = closes += 1
        def isClosed: Boolean              = emitted
        def read[A >: Int](sentinel: A): A =
          if (emitted) sentinel else { emitted = true; 2.asInstanceOf[A] }
      }
      val reader = Reader.singleInt(1).toAsync ++ tail
      val values = reader.readAll[Int]().block
      val again  = reader.read[Int](-1).block
      assertTrue(values == Chunk(1, 2), again == -1L, reader.isClosed.block, closes == 1)
    },
    test("concat closes an empty final segment and surfaces its close failure") {
      val failure = new RuntimeException("final close")
      var closes  = 0
      val tail    = new IntAsyncReader {
        def close(): Async[Unit]                  = { closes += 1; Async.fail(failure) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val reader = Reader.closed.toAsync ++ tail
      val result = Try(reader.read[Int](-1).block).failed.toOption
      val again  = Try(reader.read[Int](-1).block).failed.toOption
      assertTrue(result.contains(failure), again.contains(failure), closes == 1)
    },
    test("concat tail failure is sticky") {
      val failure = new RuntimeException("tail")
      val reader  = Reader.closed.toAsync.concatAsync[Int](() => Async.fail(failure))
      val first   = Try(reader.read[Int](Int.MinValue).block).failed.toOption
      val second  = Try(reader.read[Int](Int.MinValue).block).failed.toOption
      assertTrue(first.contains(failure), second.contains(failure))
    },
    test("concat null tail failure is sticky") {
      val reader       = Reader.closed.toAsync.concatAsync[Int](() => Async.fail(null))
      val firstEffect  = reader.read[Int](Int.MinValue)
      val first        = firstEffect.either.block
      val secondEffect = reader.read[Int](Int.MinValue)
      val second       = secondEffect.either.block
      assertTrue(first == Left(null), second == Left(null))
    },
    jvm("concat close rejects an in-flight segment value") {
      val value = new Completer[Long]
      val head  = new IntAsyncReader {
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = value.map(_.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = value
      }
      val reader  = head.concatReaderWithJvmType(() => Reader.singleInt(2), JvmType.Int)
      val running = reader.readInt(-1L).start
      reader.close().block
      value.succeed(1L)
      assertTrue(running.block == -1L)
    },
    test("concat lifecycle queries do not force a lazy tail") {
      var forced = 0
      val reader = Reader.closed.toAsync ++ { forced += 1; Reader.singleInt(1) }
      val closed = reader.isClosed.block
      val ready  = reader.readable().block
      assertTrue(!closed, !ready, forced == 0)
    },
    jvm("readable false does not advance concat") {
      var tail = 0
      val head = new IntSyncReader {
        private var done                   = false
        def close(): Unit                  = done = true
        def isClosed: Boolean              = done
        override def readable(): Boolean   = false
        def read[A >: Int](sentinel: A): A =
          if (done) sentinel else { done = true; 1.asInstanceOf[A] }
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (done) sentinel else { done = true; 1L }
      }
      val reader = head.toAsync.concatReaderWithJvmType(() => { tail += 1; Reader.singleInt(2) }, JvmType.Int)
      val before = reader.readable().block
      val value  = reader.readInt(Long.MinValue).block
      assertTrue(!before, tail == 0, value == 1L)
    },
    jvm("sync adapter is lazy, delegates high-level pulls, and has sticky EOF") {
      var pulls  = 0
      val source = new IntSyncReader {
        private var values                 = List(1, 2, 3)
        def close(): Unit                  = values = Nil
        def isClosed: Boolean              = values.isEmpty
        def read[A >: Int](sentinel: A): A = values match {
          case h :: t => pulls += 1; values = t; h.asInstanceOf[A]
          case Nil    => sentinel
        }
        override def readN[A >: Int](n: Int): Chunk[A] = { pulls += 10; super.readN(n) }
      }
      val async      = source.toAsync
      val effect     = async.readN[Int](2)
      val lazyBefore = pulls == 0
      val result     = effect.block
      async.close().block
      assertTrue(
        lazyBefore,
        pulls >= 12,
        result == Chunk(1, 2),
        async.readInt(-9L).block == -9L,
        !async.readable().block,
        async.isClosed.block
      )
    },
    test("toAsync latches observed EOF independently of explicit close") {
      var pulls   = 0
      val hostile = new IntSyncReader {
        def close(): Unit                  = ()
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = {
          pulls += 1
          if (pulls == 1) sentinel else 42.asInstanceOf[A]
        }
      }
      val async  = hostile.toAsync
      val first  = async.readInt(-1L).block
      val second = async.readInt(-1L).block
      assertTrue(first == -1L, second == -1L, pulls == 1)
    },
    test("toAsync preserves every primitive lane in readUpToN") {
      val booleans = Reader.fromChunk(Chunk(true, false, true)).toAsync.readUpToN[Boolean](3).block
      val bytes    = Reader.fromChunk(Chunk[Byte](1, 2, 3)).toAsync.readUpToN[Byte](3).block
      val chars    = Reader.fromChunk(Chunk('a', 'b', 'c')).toAsync.readUpToN[Char](3).block
      val shorts   = Reader.fromChunk(Chunk[Short](1, 2, 3)).toAsync.readUpToN[Short](3).block
      val ints     = Reader.fromChunk(Chunk(1, 2, 3)).toAsync.readUpToN[Int](3).block
      val longs    = Reader.fromChunk(Chunk(1L, 2L, 3L)).toAsync.readUpToN[Long](3).block
      val floats   = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f)).toAsync.readUpToN[Float](3).block
      val doubles  = Reader.fromChunk(Chunk(1.0, 2.0, 3.0)).toAsync.readUpToN[Double](3).block
      val refs     = Reader.fromChunk(Chunk("a", "b", "c")).toAsync.readUpToN[String](3).block
      assertTrue(
        booleans == Chunk(true, false, true),
        bytes == Chunk[Byte](1, 2, 3),
        chars == Chunk('a', 'b', 'c'),
        shorts == Chunk[Short](1, 2, 3),
        ints == Chunk(1, 2, 3),
        longs == Chunk(1L, 2L, 3L),
        floats == Chunk(1.0f, 2.0f, 3.0f),
        doubles == Chunk(1.0, 2.0, 3.0),
        refs == Chunk("a", "b", "c")
      )
    },
    test("toAsync readUpToN zero does not latch EOF") {
      val reader = Reader.singleInt(1).toAsync
      val empty  = reader.readUpToN[Int](0).block
      val value  = reader.readInt(-1L).block
      assertTrue(empty == Chunk.empty, value == 1L)
    },
    test("closed async bulk pulls preserve the zero-length contract") {
      val reader = Reader.closed.toAsync
      reader.close().block
      assertTrue(
        reader.readBytes(Array.emptyByteArray, 0, 0).block == 0,
        reader.readInts(Array.emptyIntArray, 0, 0).block == 0,
        reader.readLongs(Array.emptyLongArray, 0, 0).block == 0,
        reader.readFloats(Array.emptyFloatArray, 0, 0).block == 0,
        reader.readDoubles(Array.emptyDoubleArray, 0, 0).block == 0
      )
    },
    test("async readByte uses the exact Byte physical lane") {
      val reader = new Reader.AsyncReader[Byte] {
        private var value                          = 0x80.toByte
        def close(): Async[Unit]                   = Async.succeed(())
        def isClosed: Async[Boolean]               = Async.succeed(false)
        def readable(): Async[Boolean]             = Async.succeed(true)
        override def jvmType: JvmType              = JvmType.Byte
        def read[A >: Byte](sentinel: A): Async[A] = {
          val result = value
          value = 0
          Async.succeed(result.asInstanceOf[A])
        }
        override def readByte(): Async[Int] = {
          val result = value
          value = 0
          Async.succeed(result & 0xff)
        }
      }
      assertTrue(reader.readByte().block == 128)
    },
    test("chunk-backed Char remains on the Char physical lane") {
      val sync  = Reader.fromChunk(Chunk('\u0141'))
      val async = Reader.fromChunk(Chunk('\u0141')).toAsync
      assertTrue(sync.readChar(-1) == 0x141, async.readChar(-1).block == 0x141)
    },
    test("release and repeated wrappers preserve trusted source failure provenance") {
      def failing = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = failSource("typed")
      }
      def trusted(result: Either[Throwable, Int]): Boolean = result match {
        case Left(error: zio.blocks.streams.internal.StreamError) => error.isTrusted && error.value == "typed"
        case _                                                    => false
      }
      for {
        released <- run(failing.withReleaseAsync(() => Async.succeed(())).read(-1).either)
        repeated <- run(Reader.repeated(failing).read(-1).either)
      } yield assertTrue(trusted(released), trusted(repeated))
    },
    jvm("release is lazy and memoized") {
      var released = 0
      val reader   =
        Reader.singleInt(1).toAsync.withReleaseAsync(() => Async.deferCancelable(() => { released += 1; () }, () => ()))
      val close1 = reader.close()
      val close2 = reader.close()
      val before = released
      close1.block
      close2.block
      assertTrue(before == 0, released == 1, reader.isClosed.block)
    },
    test("async release permits reentrant close") {
      var releases                        = 0
      var reader: Reader.AsyncReader[Int] = null
      reader = Reader.singleInt(1).toAsync.withReleaseAsync { () =>
        releases += 1
        reader.close()
      }
      reader.close().block
      reader.close().block
      assertTrue(releases == 1, reader.isClosed.block)
    },
    test("repeated close wins an EOF reset race and closes the child") {
      val resetStarted = new Completer[Unit]
      val resetGate    = new Completer[Unit]
      var resetDone    = false
      var closes       = 0
      val inner        = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed { closes += 1; () }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def reset(): Async[Unit]         =
          Async
            .deferCancelable(() => { resetStarted.succeed(()); resetGate }, () => ())
            .flatten
            .map { _ => resetDone = true }
      }
      val reader                   = Reader.repeated(inner)
      val pull                     = reader.readInt(-1L).start
      def awaitClosed: Async[Unit] =
        reader.isClosed.flatMap(if (_) Async.succeed(()) else Async.reschedule(() => awaitClosed))
      for {
        _      <- run(resetStarted)
        close   = reader.close().start
        _      <- run(awaitClosed)
        _       = resetGate.succeed(())
        _      <- run(close)
        value  <- run(pull)
        closed <- run(reader.isClosed)
      } yield assertTrue(value == -1L, closed, closes == 1, resetDone)
    },
    test("repeated close waits for an explicit reset before physically closing the reopened child") {
      val resetStarted = new Completer[Unit]
      val resetGate    = new Completer[Unit]
      var open         = true
      var closes       = 0
      val inner        = new IntAsyncReader {
        def close(): Async[Unit] = Async.succeed {
          closes += 1
          open = false
          ()
        }
        def isClosed: Async[Boolean]              = Async.succeed(!open)
        def readable(): Async[Boolean]            = Async.succeed(open)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def reset(): Async[Unit]         =
          Async
            .deferCancelable(() => { resetStarted.succeed(()); resetGate }, () => ())
            .flatten
            .map { _ => open = true }
      }
      val reader = Reader.repeated(inner)
      val reset  = reader.reset().either.start
      for {
        _      <- run(resetStarted)
        closing = reader.close().start
        _      <- run(reader.isClosed)
        pending =
          closing.poll(new Runnable { def run(): Unit = () }).asInstanceOf[AnyRef] eq closing.asInstanceOf[AnyRef]
        _       = resetGate.succeed(())
        result <- run(reset)
        _      <- run(closing)
        closed <- run(reader.isClosed)
      } yield assertTrue(
        pending,
        result.left.exists(_.isInstanceOf[java.io.IOException]),
        closes == 2,
        !open,
        closed
      )
    },
    test("repeated close cancels and joins an admitted readable query") {
      val started     = new Completer[Unit]
      val readyGate   = new Completer[Boolean]
      val cleanupGate = new Completer[Unit]
      var cleaned     = false
      var closeDone   = false
      val inner       = new IntAsyncReader {
        def close(): Async[Unit]       = Async.succeed(())
        def isClosed: Async[Boolean]   = Async.succeed(false)
        def readable(): Async[Boolean] =
          Async.bracketAsync(
            () => Async.succeed(()),
            (_: Unit) => { started.succeed(()); readyGate },
            (_: Unit) => cleanupGate.map { _ => cleaned = true; () }
          )
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val reader = Reader.repeated(inner)
      val query  = reader.readable().start
      for {
        _      <- run(started)
        closing = reader.close().map(_ => closeDone = true).start
        _      <- ZIO.yieldNow
        pending = !closeDone && !cleaned
        _       = cleanupGate.succeed(())
        _      <- run(closing)
        result <- run(query)
      } yield assertTrue(pending, cleaned, closeDone, !result)
    },
    test("repeated close cannot return before an admitted read finishes cancellation cleanup") {
      val started     = new Completer[Unit]
      val cleanupGate = new Completer[Unit]
      val valueGate   = new Completer[Int]
      var cleaned     = false
      val inner       = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.bracketAsync(
            () => Async.succeed(()),
            (_: Unit) => { started.succeed(()); valueGate.map(_.asInstanceOf[A]) },
            (_: Unit) => cleanupGate.map { _ => cleaned = true; () }
          )
      }
      val reader = Reader.repeated(inner)
      val pull   = reader.readInt(-1L).start
      val noop   = new Runnable { def run(): Unit = () }
      for {
        _      <- run(started)
        closing = reader.close().start
        pending = closing.poll(noop).isInstanceOf[Pollable[?]]
        _       = cleanupGate.succeed(())
        _      <- run(closing)
        value  <- run(pull)
      } yield assertTrue(pending, cleaned, value == -1L)
    },
    jvm("release wrapper close rejects an in-flight inner value") {
      val value = new Completer[Long]
      val inner = new IntAsyncReader {
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = value.map(_.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = value
      }
      val reader  = inner.withReleaseAsync(() => Async.succeed(()))
      val running = reader.readInt(-1L).start
      reader.close().block
      value.succeed(1L)
      assertTrue(running.block == -1L)
    },
    jvm("stateful wrapper permits a source read to close it reentrantly") {
      var wrapper: Reader.AsyncReader[Int] = null
      val source                           = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.attempt {
          wrapper.close().block
          1.asInstanceOf[A]
        }
      }
      wrapper = Stream
        .fromReader[Nothing, Int](source)
        .buffer(2)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val value = wrapper.readInt(-1L).block
      wrapper.close().block
      assertTrue(value == -1L, wrapper.isClosed.block)
    },
    test("stateful wrapper close cancels and joins an admitted source read before closing ownership") {
      val started              = new Completer[Unit]
      val valueGate            = new Completer[Int]
      val cleanupDone          = new Completer[Unit]
      var cleaned              = false
      var closes               = 0
      var closeObservedCleanup = false
      val source               = new IntAsyncReader {
        def close(): Async[Unit] = {
          closeObservedCleanup = cleaned
          closes += 1
          Async.succeed(())
        }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.bracketAsync(
            () => Async.succeed(()),
            (_: Unit) => { started.succeed(()); valueGate.map(_.asInstanceOf[A]) },
            (_: Unit) => Async.succeed { cleaned = true; cleanupDone.succeed(()); () }
          )
      }
      val reader = Stream
        .fromReader[Nothing, Int](source)
        .buffer(2)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val pull = reader.readInt(-1L).start
      for {
        _      <- run(started)
        closing = reader.close().start
        _      <- run(cleanupDone)
        _       = valueGate.succeed(1)
        _      <- run(closing)
        value  <- run(pull)
      } yield assertTrue(cleaned, closeObservedCleanup, closes == 1, value == -1L, reader.isClosed.block)
    },
    test("stateful async wrappers reset upstream and restore all local state") {
      def async[A](stream: Stream[Nothing, A]): Reader.AsyncReader[A] =
        stream.compile(0, Stream.DefaultBufferSize).asInstanceOf[Reader.AsyncReader[A]]
      val buffered     = async(Stream.fromReader[Nothing, Int](Reader.fromRange(0 until 4).toAsync).buffer(3))
      val chunked      = async(Stream.fromReader[Nothing, Int](Reader.fromRange(0 until 4).toAsync).chunked(2))
      val interspersed = async(
        Stream.fromReader[Nothing, String](Reader.fromIterable(List("a", "b", "c")).toAsync).intersperse("-")
      )
      val sliding = async(Stream.fromReader[Nothing, Int](Reader.fromRange(0 until 4).toAsync).sliding(2, 1))
      for {
        bufferedFirst   <- run(buffered.readInt(-1L))
        _               <- run(buffered.reset())
        bufferedAll     <- run(buffered.readAll[Int]())
        chunkedFirst    <- run(chunked.read[Any](null)).map(_.asInstanceOf[Chunk[Int]])
        _               <- run(chunked.reset())
        chunkedAll      <- run(chunked.readAll[Chunk[Int]]())
        firstValue      <- run(interspersed.read[Any](null))
        separator       <- run(interspersed.read[Any](null))
        _               <- run(interspersed.reset())
        interspersedAll <- run(interspersed.readAll[String]())
        slidingFirst    <- run(sliding.read[Any](null)).map(_.asInstanceOf[Chunk[Int]])
        _               <- run(sliding.reset())
        slidingAll      <- run(sliding.readAll[Chunk[Int]]())
      } yield assertTrue(
        bufferedFirst == 0L,
        bufferedAll == Chunk(0, 1, 2, 3),
        chunkedFirst == Chunk(0, 1),
        chunkedAll == Chunk(Chunk(0, 1), Chunk(2, 3)),
        firstValue == "a",
        separator == "-",
        interspersedAll == Chunk("a", "-", "b", "-", "c"),
        slidingFirst == Chunk(0, 1),
        slidingAll == Chunk(Chunk(0, 1), Chunk(1, 2), Chunk(2, 3))
      )
    },
    test("stateful wrapper cancellation preserves every accepted prefix") {
      final class PrefixSource extends Reader.AsyncReader[String] {
        val pendingStarted                                             = new Completer[Unit]
        val pendingValue                                               = new Completer[String]
        var calls                                                      = 0
        def close(): Async[Unit]                                       = Async.succeed(())
        def isClosed: Async[Boolean]                                   = Async.succeed(false)
        def readable(): Async[Boolean]                                 = Async.succeed(true)
        override private[streams] def tryReadable: Reader.Availability = Reader.Available
        def read[A >: String](sentinel: A): Async[A]                   = {
          calls += 1
          calls match {
            case 1 => Async.succeed("a")
            case 2 => pendingStarted.succeed(()); pendingValue.map(_.asInstanceOf[A])
            case 3 => Async.succeed("b")
            case _ => Async.succeed(sentinel)
          }
        }
      }
      def cancelAfterPrefix[A](reader: Reader.AsyncReader[A], source: PrefixSource): ZIO[Any, Throwable, Unit] = {
        val running = reader.read[Any](null).start
        for {
          _ <- run(source.pendingStarted)
          _ <- run(Async.cancelWithCleanup(running))
        } yield ()
      }
      val bufferedSource = new PrefixSource
      val buffered       = Stream
        .fromReader[Nothing, String](bufferedSource)
        .buffer(2)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[String]]
      val chunkedSource = new PrefixSource
      val chunked       = Stream
        .fromReader[Nothing, String](chunkedSource)
        .chunked(2)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Chunk[String]]]
      val slidingSource = new PrefixSource
      val sliding       = Stream
        .fromReader[Nothing, String](slidingSource)
        .sliding(2, 1)
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Chunk[String]]]
      for {
        _             <- cancelAfterPrefix(buffered, bufferedSource)
        bufferedValue <- run(buffered.read[Any](null))
        _             <- cancelAfterPrefix(chunked, chunkedSource)
        chunkedValue  <- run(chunked.read[Any](null))
        _             <- cancelAfterPrefix(sliding, slidingSource)
        slidingValue  <- run(sliding.read[Any](null))
      } yield assertTrue(
        bufferedValue == "a",
        chunkedValue == Chunk("a", "b"),
        slidingValue == Chunk("a", "b")
      )
    },
    jvm("concurrent close callers join one pending concat segment close") {
      val completion = new Completer[Unit]
      var closes     = 0
      val head       = new IntAsyncReader {
        def close(): Async[Unit]                  = { closes += 1; completion }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val reader = head ++ Reader.singleInt(1)
      val close1 = reader.close().start
      val close2 = reader.close().start
      completion.succeed(())
      close1.block
      close2.block
      assertTrue(closes == 1, reader.isClosed.block)
    },
    test("release failure is primary only when inner close succeeds") {
      def reader(closeEffect: Async[Unit]) = new IntAsyncReader {
        def close(): Async[Unit]                  = closeEffect
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val closeFailure   = new RuntimeException("close")
      val releaseFailure = new RuntimeException("release")
      val releaseOnly    = reader(Async.succeed(())).withReleaseAsync(() => Async.fail(releaseFailure))
      val both           = reader(Async.fail(closeFailure)).withReleaseAsync(() => Async.fail(releaseFailure))
      val releaseResult  = Try(releaseOnly.close().block).failed.toOption
      val bothResult     = Try(both.close().block).failed.toOption
      assertTrue(
        releaseResult.contains(releaseFailure),
        bothResult.contains(closeFailure),
        closeFailure.getSuppressed.toList == List(releaseFailure)
      )
    },
    test("failed Async from concat and release callbacks cannot forge typed failures") {
      val concat = Reader
        .singleInt(1)
        .toAsync
        .concatAsyncWithJvmType(
          () => Async.fail(new zio.blocks.streams.internal.StreamError("concat")),
          JvmType.Int
        )
      concat.readInt(-1L).block
      val concatFailure = Try(concat.readInt(-1L).block).failed.toOption.orNull
      val release       = Reader
        .singleInt(1)
        .toAsync
        .withReleaseAsync(() => Async.fail(new zio.blocks.streams.internal.StreamError("release")))
      val releaseFailure = Try(release.close().block).failed.toOption.orNull
      assertTrue(
        concatFailure.isInstanceOf[zio.blocks.streams.internal.StreamError],
        !concatFailure.asInstanceOf[zio.blocks.streams.internal.StreamError].isTrusted,
        releaseFailure.isInstanceOf[zio.blocks.streams.internal.StreamError],
        !releaseFailure.asInstanceOf[zio.blocks.streams.internal.StreamError].isTrusted
      )
    },
    test("sync release is exactly once and replays combined close failure") {
      val closeFailure   = new RuntimeException("sync close")
      val releaseFailure = new RuntimeException("sync release")
      var closes         = 0
      var releases       = 0
      val source         = new IntSyncReader {
        def close(): Unit                  = { closes += 1; throw closeFailure }
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = sentinel
      }
      val reader = source.withRelease { () => releases += 1; throw releaseFailure }
      val first  = Try(reader.close()).failed.toOption
      val second = Try(reader.close()).failed.toOption
      assertTrue(
        first.contains(closeFailure),
        second.contains(closeFailure),
        closes == 1,
        releases == 1,
        closeFailure.getSuppressed.toList == List(releaseFailure)
      )
    },
    jvm("sync release permits same-thread reentrant close") {
      var releases                            = 0
      lazy val reader: Reader.SyncReader[Int] = Reader.singleInt(1).withRelease { () =>
        releases += 1
        reader.close()
      }
      reader.close()
      reader.close()
      assertTrue(releases == 1, reader.isClosed)
    },
    test("async adapters and concat preserve reset and cross-segment skip semantics") {
      val adapted = Reader.fromChunk(Chunk(1, 2)).toAsync
      val first   = adapted.readAll[Int]().block
      adapted.reset().block
      val replayed = adapted.readAll[Int]().block

      val concat = Reader.singleInt(1).toAsync.concatReaderWithJvmType(() => Reader.fromChunk(Chunk(2, 3)), JvmType.Int)
      concat.skip(2).block
      val afterSkip = concat.readInt(-1L).block
      concat.reset().block
      val afterReset = concat.readAll[Int]().block
      assertTrue(
        first == Chunk(1, 2),
        replayed == Chunk(1, 2),
        afterSkip == 3L,
        afterReset == Chunk(1, 2, 3),
        !concat.setLimit(1).block,
        !concat.setSkip(1).block,
        !concat.setRepeat().block
      )
    },
    test("deep asynchronous concat chains drain without recursive reader nesting") {
      val depth                           = 20000
      var reader: Reader.AsyncReader[Int] = Reader.singleInt(0).toAsync
      var i                               = 1
      while (i < depth) {
        val value = i
        reader = reader ++ Reader.singleInt(value)
        i += 1
      }
      run(reader.readAll[Int]()).map(values =>
        assertTrue(values.length == depth, values.head == 0, values.last == depth - 1)
      )
    },
    test("reset from open state preserves one close owner") {
      var closes = 0
      val source = new IntSyncReader {
        def close(): Unit                  = closes += 1
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = sentinel
        override def reset(): Unit         = ()
      }
      val reader     = source.toAsync
      val staleClose = reader.close()
      reader.reset().block
      staleClose.block
      reader.close().block
      assertTrue(closes == 1)
    },
    test("failed reset after close physically re-closes the source and preserves the completed close owner") {
      var closes  = 0
      val failure = new RuntimeException("reset")
      val source  = new IntSyncReader {
        def close(): Unit                  = closes += 1
        def isClosed: Boolean              = closes != 0
        def read[A >: Int](sentinel: A): A = sentinel
        override def reset(): Unit         = {
          closes = 0
          throw failure
        }
      }
      val reader = source.toAsync
      reader.close().block
      val resetFailure = Try(reader.reset().block).failed.toOption
      reader.close().block
      assertTrue(resetFailure.contains(failure), closes == 1, reader.isClosed.block)
    }
  )
}
