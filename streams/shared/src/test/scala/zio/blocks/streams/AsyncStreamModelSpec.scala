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

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import scala.concurrent.ExecutionContext

import zio._
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncStreamModelSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private def updateMaximum(maximum: AtomicInteger, value: Int): Unit =
    maximum.updateAndGet((old: Int) => Math.max(old, value))

  private final class Gated[A](onCancel: () => Unit = () => ()) extends Async.Operation[A] {
    private val result                           = new Completer[A]
    val polled                                   = new Completer[Unit]
    val cancellations                            = new AtomicInteger
    def poll(onComplete: Runnable): Async[A]     = { polled.succeed(()); result.poll(onComplete) }
    protected def cancelOperation(): Async[Unit] = {
      cancellations.incrementAndGet()
      onCancel()
      Async.succeed(())
    }
    def succeed(value: A): Unit      = result.succeed(value)
    def fail(cause: Throwable): Unit = result.fail(cause)
  }

  private class GatedReader[A](gate: Gated[A], closed: AtomicInteger) extends Reader.AsyncReader[A] {
    private var done                           = false
    def close(): Async[Unit]                   = { closed.incrementAndGet(); done = true; Async.succeed(()) }
    def isClosed: Async[Boolean]               = Async.succeed(done)
    def readable(): Async[Boolean]             = Async.succeed(!done)
    def read[A1 >: A](sentinel: A1): Async[A1] =
      if (done) Async.succeed(sentinel) else { done = true; gate.asInstanceOf[Async[A1]] }
  }

  private sealed trait SourceKind
  private case object SyncSource  extends SourceKind
  private case object AsyncSource extends SourceKind

  private sealed trait CallbackKind
  private case object SyncCallback  extends CallbackKind
  private case object ReadyCallback extends CallbackKind

  /**
   * A deliberately tiny, serializable language whose model does not call
   * Stream.
   */
  private sealed trait Expr                                                  extends Serializable
  private final case class Source(values: List[Int])                         extends Expr
  private final case class MapBy(in: Expr, delta: Int)                       extends Expr
  private final case class FilterMod(in: Expr, modulus: Int, remainder: Int) extends Expr
  private final case class CollectEven(in: Expr, scale: Int)                 extends Expr
  private final case class Take(in: Expr, n: Int)                            extends Expr
  private final case class Drop(in: Expr, n: Int)                            extends Expr
  private final case class ScanSum(in: Expr, initial: Int)                   extends Expr
  private final case class Accumulate(in: Expr, initial: Int)                extends Expr

  private def model(expr: Expr): List[Int] = expr match {
    case Source(values)                    => values
    case MapBy(in, delta)                  => model(in).map(_ + delta)
    case FilterMod(in, modulus, remainder) => model(in).filter(i => Math.floorMod(i, modulus) == remainder)
    case CollectEven(in, scale)            => model(in).collect { case i if (i & 1) == 0 => i * scale }
    case Take(in, n)                       => model(in).take(n)
    case Drop(in, n)                       => model(in).drop(n)
    case ScanSum(in, initial)              => model(in).scanLeft(initial)(_ + _)
    case Accumulate(in, initial)           => model(in).scanLeft(initial)(_ + _).tail
  }

  private def source(values: List[Int], kind: SourceKind, partitionSize: Int): Stream[Nothing, Int] = {
    val parts = values.grouped(partitionSize).toList
    parts.foldLeft(Stream.empty: Stream[Nothing, Int]) { (stream, part) =>
      val next = kind match {
        case SyncSource  => Stream.fromReader[Nothing, Int](Reader.fromIterable(part))
        case AsyncSource => Stream.fromReader[Nothing, Int](Reader.fromIterable(part).toAsync)
      }
      stream ++ next
    }
  }

  private def compile(
    expr: Expr,
    sourceKind: SourceKind,
    callbackKind: CallbackKind,
    partitionSize: Int
  ): Stream[Nothing, Int] = expr match {
    case Source(values)   => source(values, sourceKind, partitionSize)
    case MapBy(in, delta) =>
      val stream = compile(in, sourceKind, callbackKind, partitionSize)
      callbackKind match {
        case SyncCallback  => stream.map(_ + delta)
        case ReadyCallback => stream.mapAsync(i => Async.succeed(i + delta))
      }
    case FilterMod(in, modulus, remainder) =>
      val stream = compile(in, sourceKind, callbackKind, partitionSize)
      callbackKind match {
        case SyncCallback  => stream.filter(i => Math.floorMod(i, modulus) == remainder)
        case ReadyCallback => stream.filterAsync(i => Async.succeed(Math.floorMod(i, modulus) == remainder))
      }
    case CollectEven(in, scale) =>
      val stream = compile(in, sourceKind, callbackKind, partitionSize)
      callbackKind match {
        case SyncCallback  => stream.collect { case i if (i & 1) == 0 => i * scale }
        case ReadyCallback => stream.collectAsync(i => Async.succeed(if ((i & 1) == 0) Some(i * scale) else None))
      }
    case Take(in, n)          => compile(in, sourceKind, callbackKind, partitionSize).take(n.toLong)
    case Drop(in, n)          => compile(in, sourceKind, callbackKind, partitionSize).drop(n.toLong)
    case ScanSum(in, initial) =>
      val stream = compile(in, sourceKind, callbackKind, partitionSize)
      callbackKind match {
        case SyncCallback  => stream.scan(initial)(_ + _)
        case ReadyCallback => stream.scanAsync(initial)((sum, i) => Async.succeed(sum + i))
      }
    case Accumulate(in, initial) =>
      val stream = compile(in, sourceKind, callbackKind, partitionSize)
      callbackKind match {
        case SyncCallback  => stream.mapAccum(initial)((sum, i) => (sum + i, sum + i))
        case ReadyCallback => stream.mapAccumAsync(initial)((sum, i) => Async.succeed((sum + i, sum + i)))
      }
  }

  private val programs: List[Expr] = {
    val inputs = List(Nil, List(0), List(-3, -2, -1, 0, 1, 2, 3), List(2, 2, 5, 8))
    inputs.flatMap { values =>
      val root = Source(values)
      List(
        root,
        MapBy(FilterMod(root, 3, 1), 7),
        Take(Drop(MapBy(root, -2), 1), 3),
        CollectEven(MapBy(root, 1), -3),
        ScanSum(FilterMod(root, 2, 0), 10),
        Accumulate(CollectEven(root, 2), -4)
      )
    }
  }

  private val generatedSource: Gen[Any, Expr] =
    Gen.listOfBounded(0, 12)(Gen.int(-16, 16)).map(Source(_))

  private def generatedExpr(depth: Int): Gen[Any, Expr] =
    if (depth <= 0) generatedSource
    else {
      val child = generatedExpr(depth - 1)
      Gen.oneOf(
        generatedSource,
        child.zip(Gen.int(-8, 8)).map { case (in, delta) => MapBy(in, delta) },
        child.flatMap(in =>
          Gen.int(1, 5).flatMap(modulus => Gen.int(0, 4).map(remainder => FilterMod(in, modulus, remainder % modulus)))
        ),
        child.zip(Gen.int(-4, 4)).map { case (in, scale) => CollectEven(in, scale) },
        child.zip(Gen.int(0, 12)).map { case (in, n) => Take(in, n) },
        child.zip(Gen.int(0, 12)).map { case (in, n) => Drop(in, n) },
        child.zip(Gen.int(-8, 8)).map { case (in, initial) => ScanSum(in, initial) },
        child.zip(Gen.int(-8, 8)).map { case (in, initial) => Accumulate(in, initial) }
      )
    }

  private def collect(stream: Stream[Nothing, Int]): ZIO[Any, Throwable, List[Int]] =
    run(stream.runCollectAsync).map(_.fold(nothing => nothing, _.toList))

  def spec = suite("Async Stream independent model")(
    test("native concurrent boundaries can be repeated") {
      for {
        mapped    <- collect(Stream(1, 2).mapParAsync(1)(Async.succeed).repeated.take(4))
        flattened <- collect(
                       Stream(1, 2)
                         .flatMapPar(1)(value => Stream.unwrap(Async.succeed(Stream(value))))
                         .repeated
                         .take(4)
                     )
        merged <- collect(
                    Stream.mergeAll(1)(Stream(Stream(1), Stream(2))).repeated.take(4)
                  )
      } yield assertTrue(
        mapped == List(1, 2, 1, 2),
        flattened == List(1, 2, 1, 2),
        merged == List(1, 2, 1, 2)
      )
    },
    test("generated shrinkable programs agree with the independent model") {
      check(generatedExpr(4), Gen.int(1, 8)) { (program, partitionSize) =>
        val expected = model(program)
        for {
          sync       <- collect(compile(program, SyncSource, SyncCallback, partitionSize))
          lifted     <- collect(compile(program, AsyncSource, SyncCallback, partitionSize))
          readyAsync <- collect(compile(program, AsyncSource, ReadyCallback, partitionSize))
        } yield assertTrue(sync == expected, lifted == expected, readyAsync == expected)
      }
    },
    test("finite operator programs agree with the model across source, callback, and partition boundaries") {
      ZIO
        .foreach(programs) { program =>
          ZIO
            .foreach(List(1, 2, 8)) { partitionSize =>
              for {
                sync       <- collect(compile(program, SyncSource, SyncCallback, partitionSize))
                lifted     <- collect(compile(program, AsyncSource, SyncCallback, partitionSize))
                readyAsync <- collect(compile(program, AsyncSource, ReadyCallback, partitionSize))
              } yield (sync, lifted, readyAsync)
            }
            .map(results => (model(program), results))
        }
        .map(results =>
          assertTrue(results.forall { case (expected, variants) =>
            variants.forall { case (sync, lifted, readyAsync) =>
              sync == expected && lifted == expected && readyAsync == expected
            }
          })
        )
    },
    test("mixed synchronous and asynchronous dynamic inners compose through flatMap, concat, recovery, and mergeAll") {
      def inner(i: Int): Stream[Nothing, Int] =
        if ((i & 1) == 0) Stream(i, i + 10)
        else Stream.fromReader[Nothing, Int](Reader.fromIterable(List(i, i + 10)).toAsync)

      val flat      = source(List(1, 2, 3), AsyncSource, 2).flatMap(inner)
      val concat    = inner(1) ++ inner(2)
      val recovered = Stream.fail[String]("boom").catchAll(_ => inner(3))
      val merged    = Stream.mergeAll(2)(Stream.fromIterable(List(inner(1), inner(2), inner(3))))
      for {
        a <- collect(flat)
        b <- collect(concat)
        c <- run(recovered.runCollectAsync)
        d <- collect(merged)
      } yield assertTrue(
        a == List(1, 11, 2, 12, 3, 13),
        b == List(1, 11, 2, 12),
        c == Right(Chunk(3, 13)),
        d.sorted == List(1, 2, 3, 11, 12, 13)
      )
    },
    test("asynchronous terminals have platform-independent values and short-circuiting") {
      val seen   = new AtomicInteger
      val stream = source(List(1, 2, 3, 4), AsyncSource, 1)
      for {
        collected <- run(stream.runCollectAsync)
        count     <- run(stream.countAsync)
        head      <- run(stream.headAsync)
        last      <- run(stream.lastAsync)
        exists    <- run(stream.existsAsync { i => seen.incrementAndGet(); Async.succeed(i == 2) })
      } yield assertTrue(
        collected == Right(Chunk(1, 2, 3, 4)),
        count == Right(4L),
        head == Right(Some(1)),
        last == Right(Some(4)),
        exists == Right(true),
        seen.get == 2
      )
    },
    test("mapParAsync respects its bound and fail-fast cancels an in-flight sibling") {
      val active        = new AtomicInteger
      val maximum       = new AtomicInteger
      val cancellations = new AtomicInteger
      val failure       = new RuntimeException("model-fail-fast")
      val stalled       = new Async.Operation[Int] {
        def poll(onComplete: Runnable): Async[Int]   = this
        protected def cancelOperation(): Async[Unit] = {
          active.decrementAndGet()
          cancellations.incrementAndGet()
          Async.succeed(())
        }
      }
      val reader = Stream(1, 2, 3)
        .mapParAsync(2) { i =>
          val now = active.incrementAndGet()
          maximum.updateAndGet((old: Int) => Math.max(old, now))
          if (i == 1) stalled
          else if (i == 2) { active.decrementAndGet(); Async.fail(failure).asInstanceOf[Async[Int]] }
          else { active.decrementAndGet(); Async.succeed(i) }
        }
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        failed <- run(reader.read(-1).either)
        _      <- run(reader.close())
      } yield assertTrue(
        failed.left.exists(_ eq failure),
        maximum.get == 2,
        maximum.get <= 2,
        cancellations.get == 1,
        active.get == 0
      )
    },
    test("native merge compiles asynchronously and bounds synchronously materialized mixed inners") {
      val open    = new AtomicInteger
      val maximum = new AtomicInteger
      val closes  = List.fill(4)(new AtomicInteger)
      val gates   = List.fill(4)(new Gated[Int])
      val inners  = gates.zip(closes).zipWithIndex.map { case ((gate, closed), index) =>
        Stream.fromReader[Nothing, Int] {
          val now = open.incrementAndGet()
          updateMaximum(maximum, now)
          val reader = new GatedReader[Int](gate, closed) {
            override def close(): Async[Unit] = {
              if (closed.get == 0) open.decrementAndGet()
              super.close()
            }
          }
          if ((index & 1) == 0) reader else reader
        }
      }
      val reader = Stream.mergeAll(2)(Stream.fromIterable(inners)).compile(0, Stream.DefaultBufferSize)
      for {
        fiber <- run(reader.asInstanceOf[Reader.AsyncReader[Int]].read(-1)).fork
        _     <- run(gates(0).polled)
        _     <- run(gates(1).polled)
        _     <- run(reader.asInstanceOf[Reader.AsyncReader[Int]].close())
        _     <- fiber.await
      } yield assertTrue(
        reader.isInstanceOf[Reader.AsyncReader[?]],
        maximum.get == 2,
        open.get == 0,
        closes.map(_.get).sum == 2
      )
    },
    test("flatMapPar with unwrap counts pending callbacks and installed inners against one bound") {
      val pending                  = new AtomicInteger
      val open                     = new AtomicInteger
      val maximum                  = new AtomicInteger
      val settled                  = List.fill(3)(new AtomicBoolean)
      def finish(index: Int): Unit =
        if (settled(index).compareAndSet(false, true)) pending.decrementAndGet()
      val gates  = List.tabulate(3)(index => new Gated[Stream[Nothing, Int]](() => finish(index)))
      val closes = List.fill(3)(new AtomicInteger)
      val reader = Stream(1, 2, 3)
        .flatMapPar(2) { value =>
          Stream.unwrap {
            val now = pending.incrementAndGet()
            updateMaximum(maximum, now + open.get)
            gates(value - 1).map { _ =>
              Stream.fromReader[Nothing, Int] {
                val opened = open.incrementAndGet()
                updateMaximum(maximum, pending.get + opened)
                val output = new Gated[Int]
                new GatedReader[Int](output, closes(value - 1)) {
                  override def close(): Async[Unit] = {
                    if (closes(value - 1).get == 0) open.decrementAndGet()
                    super.close()
                  }
                }
              }
            }
          }
        }
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val operation = reader.read(-1)
      val cancelled = operation.asInstanceOf[Pollable[Int]].poll(new Runnable { def run(): Unit = () })
      val failure   = new RuntimeException("materialization")
      for {
        _      <- run(gates(0).polled)
        _      <- run(gates(1).polled)
        _       = finish(0)
        _       = gates(0).succeed(Stream.fromReader[Nothing, Int](new GatedReader[Int](new Gated[Int], closes(0))))
        _       = finish(1)
        _       = gates(1).fail(failure)
        failed <- run(cancelled.either)
        _      <- run(reader.close())
      } yield assertTrue(
        failed.left.exists(_ eq failure),
        maximum.get == 2,
        pending.get == 0,
        open.get == 0,
        gates(2).cancellations.get == 0
      )
    },
    test("flatMapPar with unwrap rejects a late owner exactly once without contaminating later pulls") {
      val acquired = new Gated[Reader[Int]]
      val closes   = new AtomicInteger
      val inner    = Reader.fromIterable(List(99)).withRelease(() => closes.incrementAndGet()).toAsync
      val reader   = Stream(1)
        .flatMapPar(1)(_ => Stream.unwrap(Async.succeed(Stream.fromReaderAsync[Nothing, Int](acquired))))
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        pull   <- run(reader.read(-1)).fork
        _      <- run(acquired.polled)
        closing = reader.close().asInstanceOf[Pollable[Unit]].poll(new Runnable { def run(): Unit = () })
        _       = acquired.succeed(inner)
        value  <- pull.join
        _      <- run(closing)
        later  <- run(reader.read(-2))
        _       = acquired.fail(new RuntimeException("ignored-late-failure"))
      } yield assertTrue(
        value == -1,
        later == -2,
        closes.get == 1
      )
    },
    test("flatMapPar with unwrap close joins and ignores a late materialization failure") {
      val acquired = new Gated[Reader[Int]]
      val failure  = new RuntimeException("late-materialization")
      val reader   = Stream(1)
        .flatMapPar(1)(_ => Stream.unwrap(Async.succeed(Stream.fromReaderAsync[Nothing, Int](acquired))))
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        pull        <- run(reader.read(-1)).fork
        _           <- run(acquired.polled)
        closing      = reader.close().asInstanceOf[Pollable[Unit]].poll(new Runnable { def run(): Unit = () })
        stillPending = closing match {
                         case pending: Pollable[Unit] @unchecked =>
                           pending.poll(new Runnable { def run(): Unit = () }).isInstanceOf[Pollable[?]]
                         case _ => false
                       }
        _      = acquired.fail(failure)
        value <- pull.join
        _     <- run(closing)
        later <- run(reader.read(-2).either)
      } yield assertTrue(
        stillPending,
        value == -1,
        later == Right(-2)
      )
    },
    test("mergeAll close rejects a late acquired inner and closes it exactly once") {
      final class LateAcquire(reader: Reader[Int]) extends Pollable[Reader[Int]] {
        val polled                                         = new Completer[Unit]
        private var observer: Runnable                     = null
        private var value: Async[Reader[Int]]              = this
        def poll(onComplete: Runnable): Async[Reader[Int]] = synchronized {
          polled.succeed(())
          if (value.asInstanceOf[AnyRef] eq this) observer = onComplete
          value
        }
        def succeed(reader: Reader[Int]): Unit = {
          val notify = synchronized {
            value = Async.succeed(reader)
            observer
          }
          if (notify ne null) notify.run()
        }
        override def cancel(): Unit = succeed(reader)
      }
      val closes   = new AtomicInteger
      val inner    = Reader.fromIterable(List(42)).withRelease(() => closes.incrementAndGet()).toAsync
      val acquired = new LateAcquire(inner)
      val stream   = Stream.fromReaderAsync[Nothing, Int](acquired)
      val reader   = Stream
        .mergeAll(1)(Stream(stream))
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        pull  <- run(reader.read(-1)).fork
        _     <- run(acquired.polled)
        _     <- run(reader.close())
        value <- pull.join
        later <- run(reader.read(-2))
      } yield assertTrue(
        value == -1,
        later == -2,
        closes.get == 1
      )
    },
    test("mergeAll close rejects directly acquired mapped inners and closes them exactly once") {
      def exercise(transform: Stream[Nothing, Int] => Stream[Nothing, Int]) = {
        final class LateAcquire(reader: Reader[Int]) extends Pollable[Reader[Int]] {
          val polled                                         = new Completer[Unit]
          private var observer: Runnable                     = null
          private var value: Async[Reader[Int]]              = this
          def poll(onComplete: Runnable): Async[Reader[Int]] = synchronized {
            polled.succeed(())
            if (value.asInstanceOf[AnyRef] eq this) observer = onComplete
            value
          }
          def succeed(reader: Reader[Int]): Unit = {
            val notify = synchronized {
              value = Async.succeed(reader)
              val current = observer
              observer = null
              current
            }
            if (notify ne null) notify.run()
          }
          override def cancel(): Unit = succeed(reader)
        }
        val closes  = new AtomicInteger
        val inner   = Reader.fromIterable(List(42)).withRelease(() => closes.incrementAndGet()).toAsync
        val acquire = new LateAcquire(inner)
        val stream  = transform(Stream.fromReaderAsync[Nothing, Int](acquire))
        val reader  = Stream
          .mergeAll(1)(Stream(stream))
          .compile(0, Stream.DefaultBufferSize)
          .asInstanceOf[Reader.AsyncReader[Int]]
        for {
          pull  <- run(reader.read(-1)).fork
          _     <- run(acquire.polled)
          _     <- run(reader.close())
          value <- pull.join
          later <- run(reader.read(-2))
        } yield (value, later, closes.get)
      }
      for {
        mapped      <- exercise(_.map(_ + 1))
        asyncMapped <- exercise(_.mapAsync(value => Async.succeed(value + 1)))
      } yield assertTrue(
        mapped == ((-1, -2, 1)),
        asyncMapped == ((-1, -2, 1))
      )
    },
    test("flatMapPar with unwrap accounts for callback, materialization, and installed-inner phases") {
      val callbacks        = new AtomicInteger
      val materializing    = new AtomicInteger
      val installed        = new AtomicInteger
      val maximum          = new AtomicInteger
      val callbackGates    = List.fill(3)(new Gated[Stream[Nothing, Int]])
      val materializeGates = List.fill(3)(new Gated[Reader[Int]](() => materializing.decrementAndGet()))
      val closes           = List.fill(3)(new AtomicInteger)
      def account(): Unit  = updateMaximum(maximum, callbacks.get + materializing.get + installed.get)
      val reader           = Stream(1, 2, 3)
        .flatMapPar(2) { value =>
          Stream.unwrap {
            callbacks.incrementAndGet()
            account()
            callbackGates(value - 1).map { stream =>
              callbacks.decrementAndGet()
              stream
            }
          }
        }
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val streams = materializeGates.zipWithIndex.map { case (gate, index) =>
        Stream.fromReaderAsync[Nothing, Int] {
          materializing.incrementAndGet()
          account()
          gate.map { owned =>
            materializing.decrementAndGet()
            installed.incrementAndGet()
            account()
            owned
          }
        }
      }
      for {
        pull <- run(reader.read(-1)).fork
        _    <- run(callbackGates(0).polled)
        _    <- run(callbackGates(1).polled)
        _     = callbackGates(0).succeed(streams(0))
        _    <- run(materializeGates(0).polled)
        _     = callbackGates(1).succeed(streams(1))
        _    <- run(materializeGates(1).polled)
        _    <- run(reader.close())
        _    <- pull.await
      } yield assertTrue(
        maximum.get == 2,
        callbacks.get + materializing.get + installed.get <= 2,
        callbackGates(2).polled.poll(new Runnable { def run(): Unit = () }).isInstanceOf[Pollable[?]],
        callbackGates(2).cancellations.get == 0,
        closes.forall(_.get == 0)
      )
    },
    test("mergeAll cleanup keeps the first close failure primary and later failures suppressed in ownership order") {
      val firstFailure  = new RuntimeException("first-inner-close")
      val secondFailure = new RuntimeException("second-inner-close")
      val outerFailure  = new RuntimeException("outer-close")
      val gates         = List(new Gated[Int], new Gated[Int])
      val closes        = List.fill(2)(new AtomicInteger)
      val failures      = List(firstFailure, secondFailure)
      val inners        = gates.zip(closes).zip(failures).map { case ((gate, count), closeFailure) =>
        Stream.fromReader[Nothing, Int](new Reader.AsyncReader[Int] {
          def close(): Async[Unit] = {
            count.incrementAndGet()
            Async.fail(closeFailure)
          }
          def isClosed: Async[Boolean]              = Async.succeed(count.get > 0)
          def readable(): Async[Boolean]            = Async.succeed(count.get == 0)
          def read[A >: Int](sentinel: A): Async[A] = gate.asInstanceOf[Async[A]]
        })
      }
      val outer  = Reader.fromIterable(inners).withRelease(() => throw outerFailure).toAsync
      val reader = Stream
        .mergeAll(2)(Stream.fromReader[Nothing, Stream[Nothing, Int]](outer))
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      for {
        pull   <- run(reader.read(-1)).fork
        _      <- run(gates(0).polled)
        _      <- run(gates(1).polled)
        result <- run(reader.close().either)
        _      <- pull.await
      } yield assertTrue(
        result.left.exists(_ eq firstFailure),
        firstFailure.getSuppressed.toList == List(secondFailure, outerFailure),
        closes.forall(_.get == 1)
      )
    }
  )
}
