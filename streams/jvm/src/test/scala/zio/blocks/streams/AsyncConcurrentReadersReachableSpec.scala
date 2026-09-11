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

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.{AsyncConcurrentReaders, StreamError}
import zio.blocks.streams.io.Reader
import zio.test._

/** Deterministic witnesses for reachable AsyncConcurrentReaders transitions. */
object AsyncConcurrentReadersReachableSpec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private def genericFold(reader: Reader.AsyncReader[Int], fold: (Long, Int) => Async[Long]): Async[Long] =
    Sink.foldNativeAsyncIntLong(reader, 0L, fold)

  private final class IntEffectSource(effect: Async[Long]) extends Reader.AsyncReader[Int] {
    private val reads                                              = new AtomicInteger
    override val jvmType                                           = JvmType.Int
    def close()                                                    = Async.succeed(())
    def isClosed                                                   = Async.succeed(false)
    def readable()                                                 = Async.succeed(true)
    def read[A >: Int](sentinel: A)                                = readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int) =
      if (reads.getAndIncrement() == 0) effect.map(v => if (v == Long.MinValue) sentinel else v)
      else Async.succeed(sentinel)
  }

  private final class OneEffectSource[A](effect: Async[A], override val jvmType: JvmType)
      extends Reader.AsyncReader[A] {
    private val reads                       = new AtomicInteger
    def close()                             = Async.succeed(())
    def isClosed                            = Async.succeed(false)
    def readable()                          = Async.succeed(true)
    def read[B >: A](sentinel: B): Async[B] =
      if (reads.getAndIncrement() == 0) effect else Async.succeed(sentinel)
  }

  private final class IntSequenceSource(effects: Vector[Async[Long]]) extends Reader.AsyncReader[Int] {
    private val index                                              = new AtomicInteger
    override val jvmType                                           = JvmType.Int
    def close()                                                    = Async.succeed(())
    def isClosed                                                   = Async.succeed(false)
    def readable()                                                 = Async.succeed(true)
    def read[A >: Int](sentinel: A)                                = readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = {
      val i = index.getAndIncrement()
      if (i < effects.length) effects(i).map(v => if (v == Long.MinValue) sentinel else v)
      else Async.succeed(sentinel)
    }
  }

  private final class ImmediateIntSource(values: Vector[Int], terminal: Option[Async[Long]] = None)
      extends Reader.AsyncReader[Int] {
    private var index               = 0
    override val jvmType            = JvmType.Int
    def close()                     = Async.succeed(())
    def isClosed                    = Async.succeed(false)
    def readable()                  = Async.succeed(true)
    def read[A >: Int](sentinel: A) =
      if (index < values.length) { val value = values(index); index += 1; Async.succeed(value.asInstanceOf[A]) }
      else
        terminal.fold(Async.succeed(sentinel))(
          _.map(value => if (value == Long.MinValue) sentinel else value.toInt.asInstanceOf[A])
        )
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
      if (index < values.length) { val value = values(index); index += 1; Async.succeed(value.toLong) }
      else terminal.getOrElse(Async.succeed(sentinel))
  }

  private final class FailingCloseReader(failure: Throwable) extends Reader.AsyncReader[Int] {
    override val jvmType                                           = JvmType.Int
    def close()                                                    = Async.fail(failure)
    def isClosed                                                   = Async.succeed(false)
    def readable()                                                 = Async.succeed(false)
    def read[A >: Int](sentinel: A)                                = Async.succeed(sentinel)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = Async.succeed(sentinel)
  }

  private final class CountingAsyncReader(closes: AtomicInteger, failure: Throwable = null)
      extends Reader.AsyncReader[Int] {
    override val jvmType = JvmType.Int
    def close()          = {
      closes.incrementAndGet()
      if (failure eq null) Async.succeed(()) else Async.fail(failure)
    }
    def isClosed                                                   = Async.succeed(closes.get != 0)
    def readable()                                                 = Async.succeed(true)
    def read[A >: Int](sentinel: A)                                = Async.succeed(sentinel)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = Async.succeed(sentinel)
  }

  private final class CountingSyncReader(closes: AtomicInteger) extends Reader.SyncReader[Int] {
    override val jvmType                                                 = JvmType.Int
    def close(): Unit                                                    = { closes.incrementAndGet(); () }
    def isClosed                                                         = closes.get != 0
    override def readable()                                              = true
    def read[A >: Int](sentinel: A): A                                   = sentinel
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = sentinel
  }

  def spec = suite("reachable AsyncConcurrentReaders transitions")(
    test("generic concurrent long fold handles ready and pending reads and callback results") {
      val readGate = new Completer[Long]
      val foldGate = new Completer[Long]
      val pending  =
        AsyncConcurrentReaders.mapPar[Int, Int](new IntEffectSource(readGate), 1, Async.succeed, JvmType.AnyRef)
      val folded = genericFold(pending, (_, value) => if (value == 7) foldGate else Async.succeed(value.toLong)).start
      val ready  = AsyncConcurrentReaders.mapPar[Int, Int](source(1, 2, 3), 1, Async.succeed, JvmType.AnyRef)
      for {
        _      <- runAsync(Async.reschedule(() => Async.succeed(())))
        _       = readGate.succeed(7L)
        _       = foldGate.succeed(11L)
        result <- runAsync(folded)
        sum    <- runAsync(genericFold(ready, (acc, value) => Async.succeed(acc + value)))
        _      <- runAsync(pending.close())
        _      <- runAsync(ready.close())
      } yield assertTrue(result == 11L, sum == 6L)
    },
    test("generic concurrent long fold preserves ordinary and trusted callback failures") {
      val ordinary = new RuntimeException("ordinary")
      val trusted  = new RuntimeException("trusted")
      val r1       = AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, Async.succeed, JvmType.AnyRef)
      val r2       = AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, Async.succeed, JvmType.AnyRef)
      for {
        a <- runAsync(genericFold(r1, (_, _) => Async.fail(ordinary)).either)
        b <- runAsync(genericFold(r2, (_, _) => Async.failTrusted(trusted)).either)
      } yield assertTrue(a == Left(ordinary), b == Left(trusted))
    },
    test("generic concurrent long fold yields after its 1024-element budget") {
      val reader = AsyncConcurrentReaders.mapPar[Int, Int](source((1 to 1025): _*), 1, Async.succeed, JvmType.AnyRef)
      runAsync(genericFold(reader, (acc, value) => Async.succeed(acc + value))).map(sum =>
        assertTrue(sum == 1025L * 1026L / 2L)
      )
    },
    test("physical Int source adapter preserves pending, ordinary, and trusted failures") {
      val gate                        = new Completer[Long]
      val ordinary                    = new RuntimeException("ordinary-source")
      val trusted                     = new RuntimeException("trusted-source")
      def mapped(effect: Async[Long]) =
        AsyncConcurrentReaders.mapPar[Int, Int](new IntEffectSource(effect), 1, Async.succeed, JvmType.Int)
      val pending = mapped(gate)
      val running = pending.readInt(-1L).start
      for {
        _ <- runAsync(Async.reschedule(() => Async.succeed(())))
        _  = gate.succeed(9L)
        v <- runAsync(running)
        o <- runAsync(mapped(Async.fail(ordinary)).readInt(-1L).either)
        t <- runAsync(mapped(Async.failTrusted(trusted)).readInt(-1L).either)
      } yield assertTrue(v == 9L, o == Left(ordinary), t == Left(trusted))
    },
    test("native mapPar fold executes immediate success, EOF, and direct fairness yield") {
      val values  = (1 to 1025).toVector
      val yielded = new Completer[Unit]
      val reader  =
        AsyncConcurrentReaders.mapPar[Int, Int](new ImmediateIntSource(values), 1, Async.succeed, JvmType.Int)
      AsyncConcurrentReaders.afterYieldForTest(reader)(() => yielded.succeed(()))
      val running = genericFold(reader, (acc, value) => Async.succeed(acc + value)).start
      for {
        _   <- runAsync(yielded)
        sum <- runAsync(running)
      } yield assertTrue(sum == 1025L * 1026L / 2L)
    },
    test("native mapPar fold preserves immediate ordinary and trusted source failures") {
      val ordinary                       = new RuntimeException("source-ordinary")
      val trusted                        = new RuntimeException("source-trusted")
      def runSource(effect: Async[Long]) = {
        val reader = AsyncConcurrentReaders
          .mapPar[Int, Int](new ImmediateIntSource(Vector.empty, Some(effect)), 1, Async.succeed, JvmType.Int)
        runAsync(genericFold(reader, (acc, value) => Async.succeed(acc + value)).either)
      }
      for {
        a <- runSource(Async.fail(ordinary))
        b <- runSource(Async.failTrusted(trusted))
      } yield assertTrue(a == Left(ordinary), b == Left(trusted))
    },
    test("native mapPar fold preserves immediate map throws and ordinary and trusted failures") {
      val thrown                         = new RuntimeException("map-thrown")
      val ordinary                       = new RuntimeException("map-ordinary")
      val trusted                        = new RuntimeException("map-trusted")
      def runMap(map: Int => Async[Int]) = {
        val reader = AsyncConcurrentReaders.mapPar[Int, Int](new ImmediateIntSource(Vector(1)), 1, map, JvmType.Int)
        runAsync(genericFold(reader, (acc, value) => Async.succeed(acc + value)).either)
      }
      for {
        a <- runMap(_ => throw thrown)
        b <- runMap(_ => Async.fail(ordinary))
        c <- runMap(_ => Async.failTrusted(trusted))
      } yield assertTrue(a == Left(thrown), b == Left(ordinary), c == Left(trusted))
    },
    test("native mapPar fold treats trusted StreamErrors from map and fold callbacks as untrusted") {
      val directMap                                                     = StreamError.source("direct-map")
      val directFold                                                    = StreamError.source("direct-fold")
      val resumedMap                                                    = StreamError.source("resumed-map")
      val resumedFold                                                   = StreamError.source("resumed-fold")
      def run(map: Int => Async[Int], fold: (Long, Int) => Async[Long]) = {
        val reader = AsyncConcurrentReaders.mapPar[Int, Int](new ImmediateIntSource(Vector(1)), 1, map, JvmType.Int)
        runAsync(genericFold(reader, fold).either)
      }
      def runResumed(map: Int => Async[Int], fold: (Long, Int) => Async[Long]) = {
        val gate    = new Completer[Long]
        val reader  = AsyncConcurrentReaders.mapPar[Int, Int](new IntEffectSource(gate), 1, map, JvmType.Int)
        val running = genericFold(reader, fold).start
        for {
          _ <- runAsync(Async.reschedule(() => Async.succeed(())))
          _  = gate.succeed(1L)
          r <- runAsync(running.either)
        } yield r
      }
      for {
        a <- run(_ => Async.failTrusted(directMap), (acc, value) => Async.succeed(acc + value))
        b <- run(Async.succeed, (_, _) => Async.failTrusted(directFold))
        c <- runResumed(_ => Async.failTrusted(resumedMap), (acc, value) => Async.succeed(acc + value))
        d <- runResumed(Async.succeed, (_, _) => Async.failTrusted(resumedFold))
      } yield {
        val errors = Vector(a, b, c, d).map(_.swap.toOption.get.asInstanceOf[StreamError])
        assertTrue(
          errors.map(_.value) == Vector("direct-map", "direct-fold", "resumed-map", "resumed-fold"),
          errors.forall(error => !error.isTrusted),
          errors(0) ne directMap,
          errors(1) ne directFold,
          errors(2) ne resumedMap,
          errors(3) ne resumedFold
        )
      }
    },
    test("native mapPar fold preserves immediate fold throws failures and pending continuation") {
      val thrown   = new RuntimeException("fold-thrown")
      val ordinary = new RuntimeException("fold-ordinary")
      val trusted  = new RuntimeException("fold-trusted")
      def reader   =
        AsyncConcurrentReaders.mapPar[Int, Int](new ImmediateIntSource(Vector(1, 2)), 1, Async.succeed, JvmType.Int)
      val gate    = new Completer[Long]
      val entered = new Completer[Unit]
      val pending = genericFold(
        reader,
        (acc, value) =>
          if (value == 1) { entered.succeed(()); gate }
          else Async.succeed(acc + value)
      ).start
      for {
        a <- runAsync(genericFold(reader, (_, _) => throw thrown).either)
        b <- runAsync(genericFold(reader, (_, _) => Async.fail(ordinary)).either)
        c <- runAsync(genericFold(reader, (_, _) => Async.failTrusted(trusted)).either)
        _ <- runAsync(entered)
        _  = gate.succeed(1L)
        d <- runAsync(pending)
      } yield assertTrue(a == Left(thrown), b == Left(ordinary), c == Left(trusted), d == 3L)
    },
    test("native mapPar fold handles a genuinely pending map followed by pending and failing folds") {
      val mapGate     = new Completer[Int]
      val foldGate    = new Completer[Long]
      val foldEntered = new Completer[Unit]
      val reader      = AsyncConcurrentReaders.mapPar[Int, Int](
        new ImmediateIntSource(Vector(1)),
        1,
        _ => mapGate,
        JvmType.Int
      )
      val running = genericFold(reader, (_, value) => { foldEntered.succeed(()); foldGate.map(_ + value) }).start
      for {
        _     <- runAsync(Async.reschedule(() => Async.succeed(())))
        _      = mapGate.succeed(2)
        _     <- runAsync(foldEntered)
        _      = foldGate.succeed(3L)
        value <- runAsync(running)
      } yield assertTrue(value == 5L)
    },
    test("native mapPar fold catches throwing callbacks after a pending source read") {
      val mapFailure                                                           = new RuntimeException("resumed-map")
      val foldFailure                                                          = new RuntimeException("resumed-fold")
      def runPending(map: Int => Async[Int], fold: (Long, Int) => Async[Long]) = {
        val gate    = new Completer[Long]
        val reader  = AsyncConcurrentReaders.mapPar[Int, Int](new IntEffectSource(gate), 1, map, JvmType.Int)
        val running = genericFold(reader, fold).start
        gate.succeed(1L)
        runAsync(running.either)
      }
      for {
        a <- runPending(_ => throw mapFailure, (acc, value) => Async.succeed(acc + value))
        b <- runPending(Async.succeed, (_, _) => throw foldFailure)
      } yield assertTrue(a == Left(mapFailure), b == Left(foldFailure))
    },
    test("native mapPar fold observes immediate selector failures while a worker is pending") {
      val ordinary                          = new RuntimeException("selector-ordinary")
      val trusted                           = new RuntimeException("selector-trusted")
      def runFailure(terminal: Async[Long]) = {
        val worker = new Completer[Int]
        val source = new ImmediateIntSource(Vector(1), Some(terminal))
        val reader = AsyncConcurrentReaders.mapPar[Int, Int](source, 2, _ => worker, JvmType.Int)
        runAsync(genericFold(reader, (acc, value) => Async.succeed(acc + value)).either)
      }
      for {
        a <- runFailure(Async.fail(ordinary))
        b <- runFailure(Async.failTrusted(trusted))
      } yield assertTrue(a == Left(ordinary), b == Left(trusted))
    },
    test("native mapPar fold observes ready selector failures after a pending worker fold") {
      val ordinary                          = new RuntimeException("ready-selector-ordinary")
      val trusted                           = new RuntimeException("ready-selector-trusted")
      def runFailure(terminal: Async[Long]) = {
        val mapped      = new Completer[Int]
        val folded      = new Completer[Long]
        val foldEntered = new Completer[Unit]
        val source      = new ImmediateIntSource(Vector(1), Some(terminal))
        val reader      = AsyncConcurrentReaders.mapPar[Int, Int](source, 1, _ => mapped, JvmType.Int)
        val running     = genericFold(reader, (_, value) => { foldEntered.succeed(()); folded.map(_ + value) }).start
        for {
          _      <- runAsync(Async.reschedule(() => Async.succeed(())))
          _       = mapped.succeed(2)
          _      <- runAsync(foldEntered)
          _       = folded.succeed(3L)
          result <- runAsync(running.either)
        } yield result
      }
      for {
        a <- runFailure(Async.fail(ordinary))
        b <- runFailure(Async.failTrusted(trusted))
      } yield assertTrue(a == Left(ordinary), b == Left(trusted))
    },
    test("native mapPar fold observes an immediate fold failure after an asynchronously mapped value") {
      val failure = new RuntimeException("async-map-fold")
      val mapped  = new Completer[Int]
      val reader  =
        AsyncConcurrentReaders.mapPar[Int, Int](new ImmediateIntSource(Vector(1)), 1, _ => mapped, JvmType.Int)
      val running = genericFold(reader, (_, _) => Async.fail(failure)).start
      for {
        _      <- runAsync(Async.reschedule(() => Async.succeed(())))
        _       = mapped.succeed(2)
        result <- runAsync(running.either)
      } yield assertTrue(result == Left(failure))
    },
    test("native mapPar fold observes a ready worker fold failure after resuming an earlier fold") {
      val failure     = new RuntimeException("ready-worker-fold")
      val firstMapped = new Completer[Int]
      val firstFolded = new Completer[Long]
      var maps        = 0
      var folds       = 0
      val reader      = AsyncConcurrentReaders.mapPar[Int, Int](
        new ImmediateIntSource(Vector(1, 2)),
        2,
        value => {
          maps += 1
          if (maps == 1) firstMapped else Async.succeed(value)
        },
        JvmType.Int
      )
      val running = genericFold(
        reader,
        (acc, value) => {
          folds += 1
          if (folds == 1) firstFolded.map(_ + value) else Async.fail(failure)
        }
      ).start
      for {
        _      <- runAsync(Async.reschedule(() => Async.succeed(())))
        _       = firstMapped.succeed(1)
        _      <- runAsync(Async.reschedule(() => Async.succeed(())))
        _       = firstFolded.succeed(0L)
        result <- runAsync(running.either)
      } yield assertTrue(result == Left(failure), maps == 2, folds == 2)
    },
    test("native mapPar selector resumes a pending worker into a pending fold") {
      val mapped      = new Completer[Int]
      val folded      = new Completer[Long]
      val foldEntered = new Completer[Unit]
      val reader      =
        AsyncConcurrentReaders.mapPar[Int, Int](new ImmediateIntSource(Vector(1)), 1, _ => mapped, JvmType.Int)
      val running = genericFold(reader, (_, value) => { foldEntered.succeed(()); folded.map(_ + value) }).start
      for {
        _     <- runAsync(Async.reschedule(() => Async.succeed(())))
        _      = mapped.succeed(2)
        _     <- runAsync(foldEntered)
        _      = folded.succeed(3L)
        value <- runAsync(running)
      } yield assertTrue(value == 5L)
    },
    test("native mapPar selector yields after four completed batches") {
      val yielded  = new Completer[Unit]
      val firstMap = new Completer[Int]
      var first    = true
      val reader   = AsyncConcurrentReaders.mapPar[Int, Int](
        new ImmediateIntSource((1 to 8).toVector),
        1,
        value =>
          if (first) { first = false; firstMap }
          else Async.succeed(value),
        JvmType.Int
      )
      AsyncConcurrentReaders.afterYieldForTest(reader)(() => yielded.succeed(()))
      val running = genericFold(reader, (acc, value) => Async.succeed(acc + value)).start
      for {
        _   <- runAsync(Async.reschedule(() => Async.succeed(())))
        _    = firstMap.succeed(1)
        _   <- runAsync(yielded)
        sum <- runAsync(running)
      } yield assertTrue(sum == 36L)
    },
    test("native mapPar selector completes after asynchronously resumed EOF") {
      val mapped  = new Completer[Int]
      val eof     = new Completer[Long]
      val source  = new ImmediateIntSource(Vector(1), Some(eof))
      val reader  = AsyncConcurrentReaders.mapPar[Int, Int](source, 2, _ => mapped, JvmType.Int)
      val running = genericFold(reader, (acc, value) => Async.succeed(acc + value)).start
      for {
        _   <- runAsync(Async.reschedule(() => Async.succeed(())))
        _    = mapped.succeed(2)
        _   <- runAsync(Async.reschedule(() => Async.succeed(())))
        _    = eof.succeed(Long.MinValue)
        sum <- runAsync(running)
      } yield assertTrue(sum == 2L)
    },
    test("native mapPar fold rejects a worker value after close invalidates its claimed handoff") {
      val mapped = new Completer[Int]
      val reader =
        AsyncConcurrentReaders.mapPar[Int, Int](new ImmediateIntSource(Vector(1)), 1, _ => mapped, JvmType.Int)
      val running                    = genericFold(reader, (acc, value) => Async.succeed(acc + value)).start
      var close: Async.Running[Unit] = null
      var observedClosed             = false
      AsyncConcurrentReaders.afterBeginHandoffForTest(reader) { () =>
        close = reader.close().start
        observedClosed = reader.isClosed.block
      }
      for {
        _   <- runAsync(Async.reschedule(() => Async.succeed(())))
        _    = mapped.succeed(2)
        sum <- runAsync(running)
        _   <- runAsync(close)
      } yield assertTrue(sum == 0L, observedClosed)
    },
    test("native mapPar fold terminates after a scalar pull drains the source and sole worker") {
      val mapped = new Completer[Int]
      val reader =
        AsyncConcurrentReaders.mapPar[Int, Int](new ImmediateIntSource(Vector(1)), 2, _ => mapped, JvmType.Int)
      val scalar = reader.readInt(-1L).start
      for {
        _     <- runAsync(Async.reschedule(() => Async.succeed(())))
        _     <- runAsync(Async.reschedule(() => Async.succeed(())))
        _      = mapped.succeed(2)
        value <- runAsync(scalar)
        sum   <- runAsync(genericFold(reader, (acc, n) => Async.succeed(acc + n)))
      } yield assertTrue(value == 2L, sum == 0L)
    },
    test("native mapPar fold cancellation during a pending fold completes the handoff") {
      val foldGate                    = new Completer[Long]
      val reader                      = AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, Async.succeed, JvmType.Int)
      val pull                        = genericFold(reader, (_, _) => foldGate).asInstanceOf[Pollable[Long]]
      var cancel: Async.Running[Unit] = null
      AsyncConcurrentReaders.afterBeginHandoffForTest(reader) { () =>
        cancel = Async.cancelWithCleanup(pull).start
      }
      val running = pull.start
      for {
        _     <- runAsync(cancel)
        _      = foldGate.succeed(1L)
        value <- runAsync(running)
        _     <- runAsync(reader.close())
      } yield assertTrue(value == 1L)
    },
    test("native merge fold drains multiple inners, releases their slots, and observes outer EOF") {
      val outer  = source(Stream(1, 2), Stream.empty, Stream(3, 4, 5))
      val reader = AsyncConcurrentReaders.merge[Int](outer, 2, 1, JvmType.Int)
      for {
        sum    <- runAsync(genericFold(reader, (acc, value) => Async.succeed(acc + value)))
        closed <- runAsync(reader.isClosed)
      } yield assertTrue(sum == 15L, closed)
    },
    test("native merge fold resumes pending outer, inner, and fold effects") {
      val outerGate = new Completer[Stream[Nothing, Int]]
      val innerGate = new Completer[Long]
      val foldGate  = new Completer[Long]
      val outer     = new OneEffectSource[Stream[Nothing, Int]](outerGate, JvmType.AnyRef)
      val reader    = AsyncConcurrentReaders.merge[Int](outer, 1, 1, JvmType.Int)
      val running   = genericFold(reader, (_, value) => if (value == 7) foldGate else Async.succeed(value.toLong)).start
      for {
        _ <- runAsync(Async.reschedule(() => Async.succeed(())))
        _  = outerGate.succeed(Stream.fromReader[Nothing, Int](new IntEffectSource(innerGate)))
        _ <- runAsync(Async.reschedule(() => Async.succeed(())))
        _  = innerGate.succeed(7L)
        _ <- runAsync(Async.reschedule(() => Async.succeed(())))
        _  = foldGate.succeed(11L)
        v <- runAsync(running)
      } yield assertTrue(v == 11L)
    },
    test("native merge fold accepts boxed MapValue results from a legal AnyRef inner lane") {
      val boxed  = new OneEffectSource[Int](Async.succeed(7), JvmType.AnyRef)
      val reader = AsyncConcurrentReaders.merge[Int](source(Stream.fromReader[Nothing, Int](boxed)), 1, 1, JvmType.Int)
      runAsync(genericFold(reader, (acc, value) => Async.succeed(acc + value))).map(sum => assertTrue(sum == 7L))
    },
    test("native merge fold preserves ordinary and trusted outer and inner failures") {
      val outerOrdinary                              = new RuntimeException("outer-ordinary")
      val outerTrusted                               = new RuntimeException("outer-trusted")
      val innerOrdinary                              = new RuntimeException("inner-ordinary")
      val innerTrusted                               = new RuntimeException("inner-trusted")
      def outer(effect: Async[Stream[Nothing, Int]]) =
        AsyncConcurrentReaders.merge[Int](new OneEffectSource(effect, JvmType.AnyRef), 1, 1, JvmType.Int)
      def inner(effect: Async[Long]) =
        AsyncConcurrentReaders
          .merge[Int](source(Stream.fromReader[Nothing, Int](new IntEffectSource(effect))), 1, 1, JvmType.Int)
      for {
        oo <- runAsync(genericFold(outer(Async.fail(outerOrdinary)), (_, _) => Async.succeed(0L)).either)
        ot <- runAsync(genericFold(outer(Async.failTrusted(outerTrusted)), (_, _) => Async.succeed(0L)).either)
        io <- runAsync(genericFold(inner(Async.fail(innerOrdinary)), (_, _) => Async.succeed(0L)).either)
        it <- runAsync(genericFold(inner(Async.failTrusted(innerTrusted)), (_, _) => Async.succeed(0L)).either)
      } yield assertTrue(
        oo == Left(outerOrdinary),
        ot == Left(outerTrusted),
        io == Left(innerOrdinary),
        it == Left(innerTrusted)
      )
    },
    test("native merge fold preserves ordinary and trusted callback failures") {
      val ordinary = new RuntimeException("fold-ordinary")
      val trusted  = new RuntimeException("fold-trusted")
      val r1       = AsyncConcurrentReaders.merge[Int](source(Stream(1)), 1, 1, JvmType.Int)
      val r2       = AsyncConcurrentReaders.merge[Int](source(Stream(1)), 1, 1, JvmType.Int)
      for {
        a <- runAsync(genericFold(r1, (_, _) => Async.fail(ordinary)).either)
        b <- runAsync(genericFold(r2, (_, _) => Async.failTrusted(trusted)).either)
      } yield assertTrue(a == Left(ordinary), b == Left(trusted))
    },
    test("native merge fold observes immediate ordinary and trusted selector failures after a pending fold") {
      val ordinary                         = new RuntimeException("selector-ordinary")
      val trusted                          = new RuntimeException("selector-trusted")
      def runFailure(failure: Async[Long]) = {
        val gate    = new Completer[Long]
        val entered = new Completer[Unit]
        val inner   = new IntSequenceSource(Vector(Async.succeed(1L), failure))
        val reader  =
          AsyncConcurrentReaders.merge[Int](source(Stream.fromReader[Nothing, Int](inner)), 1, 1, JvmType.Int)
        val running = genericFold(reader, (_, _) => { entered.succeed(()); gate }).start
        for {
          _      <- runAsync(entered)
          _       = gate.succeed(1L)
          result <- runAsync(running.either)
        } yield result
      }
      for {
        a <- runFailure(Async.fail(ordinary))
        b <- runFailure(Async.failTrusted(trusted))
      } yield assertTrue(a == Left(ordinary), b == Left(trusted))
    },
    test("native merge fold observes an immediate callback failure after resuming a pending fold") {
      val failure = new RuntimeException("second-fold")
      val gate    = new Completer[Long]
      val entered = new Completer[Unit]
      val inner   = new IntSequenceSource(Vector(Async.succeed(1L), Async.succeed(2L)))
      val reader  = AsyncConcurrentReaders.merge[Int](source(Stream.fromReader[Nothing, Int](inner)), 1, 1, JvmType.Int)
      val running = genericFold(
        reader,
        (_, value) =>
          if (value == 1) { entered.succeed(()); gate }
          else Async.fail(failure)
      ).start
      for {
        _      <- runAsync(entered)
        _       = gate.succeed(1L)
        result <- runAsync(running.either)
      } yield assertTrue(result == Left(failure))
    },
    test("native merge fold cancellation during a claimed handoff completes the fold") {
      val foldGate                    = new Completer[Long]
      val reader                      = AsyncConcurrentReaders.merge[Int](source(Stream(1)), 1, 1, JvmType.Int)
      val fold                        = genericFold(reader, (_, _) => foldGate).asInstanceOf[Pollable[Long]]
      var cancel: Async.Running[Unit] = null
      var claimed                     = false
      AsyncConcurrentReaders.afterBeginHandoffForTest(reader) { () =>
        cancel = Async.cancelWithCleanup(fold).start
        claimed = AsyncConcurrentReaders.handoffCancellationClaimedForTest(reader)
      }
      val running = fold.start
      foldGate.succeed(1L)
      for {
        _     <- runAsync(cancel)
        value <- runAsync(running)
        _     <- runAsync(reader.close())
      } yield assertTrue(value == 1L, claimed)
    },
    test("native merge fold yields after a full 1024-result selector batch") {
      val values  = 1 to 1026
      val gate    = new Completer[Long]
      val entered = new Completer[Unit]
      val yielded = new Completer[Unit]
      val reader  = AsyncConcurrentReaders.merge[Int](source(Stream.fromIterable(values)), 1, 1, JvmType.Int)
      AsyncConcurrentReaders.afterYieldForTest(reader)(() => yielded.succeed(()))
      val running = genericFold(
        reader,
        (acc, value) =>
          if (value == 1) { entered.succeed(()); gate }
          else Async.succeed(acc + value)
      ).start
      for {
        _   <- runAsync(entered)
        _    = gate.succeed(1L)
        _   <- runAsync(yielded)
        sum <- runAsync(running)
      } yield assertTrue(sum == 1026L * 1027L / 2L)
    },
    test("native merge fold returns its sentinel when close invalidates a claimed selector handoff") {
      val reader                     = AsyncConcurrentReaders.merge[Int](source(Stream(1)), 1, 1, JvmType.Int)
      var close: Async.Running[Unit] = null
      var observedClosed             = false
      AsyncConcurrentReaders.afterBeginHandoffForTest(reader) { () =>
        close = reader.close().start
        observedClosed = reader.isClosed.block
      }
      val running = genericFold(reader, (acc, value) => Async.succeed(acc + value)).start
      for {
        sum <- runAsync(running)
        _   <- runAsync(close)
      } yield assertTrue(sum == 0L, observedClosed)
    },
    test("merge close after asynchronous acquisition and before installation closes exactly once") {
      val acquisition                = new Completer[Reader[Int]]
      val closes                     = new AtomicInteger
      val acquired                   = new CountingAsyncReader(closes)
      val outer                      = source(Stream.fromReaderAsync[Nothing, Int](acquisition))
      val reader                     = AsyncConcurrentReaders.merge[Int](outer, 1, 1, JvmType.Int)
      var close: Async.Running[Unit] = null
      AsyncConcurrentReaders.afterAcquisitionForTest(reader) { () => close = reader.close().start }
      val pull = reader.readInt(-7L).start
      acquisition.succeed(acquired)
      for {
        _ <- runAsync(close)
        v <- runAsync(pull)
      } yield assertTrue(v == -7L, closes.get == 1)
    },
    test("merge close after synchronous acquisition and before installation closes exactly once") {
      val closes                     = new AtomicInteger
      val acquired                   = new CountingSyncReader(closes)
      val outer                      = source(Stream.fromReaderAsync[Nothing, Int](Async.succeed(acquired)))
      val reader                     = AsyncConcurrentReaders.merge[Int](outer, 1, 1, JvmType.Int)
      var close: Async.Running[Unit] = null
      AsyncConcurrentReaders.afterAcquisitionForTest(reader) { () => close = reader.close().start }
      val pull = reader.readInt(-7L).start
      for {
        _ <- runAsync(close)
        v <- runAsync(pull)
      } yield assertTrue(v == -7L, closes.get == 1)
    },
    test("merge pull reports acquired-reader cleanup failure when close wins before installation") {
      val failure                    = new RuntimeException("acquired-close")
      val closes                     = new AtomicInteger
      val acquired                   = new CountingAsyncReader(closes, failure)
      val outer                      = source(Stream.fromReaderAsync[Nothing, Int](Async.succeed(acquired)))
      val reader                     = AsyncConcurrentReaders.merge[Int](outer, 1, 1, JvmType.Int)
      var close: Async.Running[Unit] = null
      AsyncConcurrentReaders.afterAcquisitionForTest(reader) { () => close = reader.close().start }
      val pull = reader.readInt(-7L).start
      for {
        closed <- runAsync(close.either)
        value  <- runAsync(pull.either)
      } yield assertTrue(closes.get == 1, closed.isRight, value == Left(failure))
    },
    test("merge propagates a failing inner close") {
      val failure = new RuntimeException("inner-close")
      val outer   = source(Stream.fromReader[Nothing, Int](new FailingCloseReader(failure)))
      val reader  = AsyncConcurrentReaders.merge[Int](outer, 1, 1, JvmType.Int)
      runAsync(reader.readInt(-1L).either).map(result => assertTrue(result == Left(failure)))
    }
  ) @@ TestAspect.sequential
}
