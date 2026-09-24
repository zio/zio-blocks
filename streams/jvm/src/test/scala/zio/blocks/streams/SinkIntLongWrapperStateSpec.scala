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
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

/** JVM-only state-machine coverage that waits for scheduler-thread polling. */
object SinkIntLongWrapperStateSpec extends StreamsBaseSpec {
  private sealed trait Step[+A]
  private final case class Done[A](value: A)                          extends Step[A]
  private final case class Failed(error: Throwable, trusted: Boolean) extends Step[Nothing]
  private final case class Waiting[A](pollable: Pollable[A])          extends Step[A]

  private def step[A](async: Async[A]): Step[A] =
    Async.foldStep(async)(new Async.StepFold[A, Step[A]] {
      def success(value: A): Step[A]                         = Done(value)
      def failure(error: Throwable): Step[A]                 = Failed(error, false)
      override def trustedFailure(error: Throwable): Step[A] = Failed(error, true)
      def pending(pollable: Pollable[A]): Step[A]            = Waiting(pollable)
    })

  private def pollOnce[A](async: Async[A]): Step[A] = step(async) match {
    case Waiting(p) => step(p.poll(new Runnable { def run(): Unit = () }))
    case other      => other
  }

  private final class Gate[A] extends Async.Operation[A] {
    val polls                            = new AtomicInteger
    val cancellations                    = new AtomicInteger
    val cleanup                          = new Completer[Unit]
    private var result: Option[Async[A]] = None
    private var wake: Runnable           = null

    def poll(onComplete: Runnable): Async[A] = synchronized {
      polls.incrementAndGet()
      result match {
        case Some(value) => value
        case None        => wake = onComplete; this
      }
    }
    def succeed(value: A): Unit                        = finish(Async.succeed(value))
    def fail(error: Throwable, trusted: Boolean): Unit =
      finish(if (trusted) Async.failTrusted(error) else Async.fail(error))
    private def finish(value: Async[A]): Unit = synchronized {
      result = Some(value)
      val callback = wake
      wake = null
      if (callback ne null) callback.run()
    }
    protected def cancelOperation(): Async[Unit] = { cancellations.incrementAndGet(); cleanup }
  }

  private def awaitPoll(gate: Gate[_]): Boolean = {
    var n = 0
    while (gate.polls.get == 0 && n < 2000) { Thread.sleep(1L); n += 1 }
    gate.polls.get > 0
  }

  private final class IntAsyncReader(
    values: Vector[() => Async[Long]],
    control: Long => Async[Boolean] = _ => Async.succeed(false)
  ) extends Reader.AsyncReader[Int] {
    val reads                                                                   = new AtomicInteger
    val skips                                                                   = new AtomicInteger
    private var index                                                           = 0
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = Async.succeed(())
    def isClosed: Async[Boolean]                                                = Async.succeed(false)
    def readable(): Async[Boolean]                                              = Async.succeed(true)
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read used"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      reads.incrementAndGet()
      if (index >= values.length) Async.succeed(sentinel)
      else { val next = values(index); index += 1; next() }
    }
    override def setSkip(n: Long): Async[Boolean] = { skips.incrementAndGet(); control(n) }
  }

  private final class IntSyncReader(values: Vector[Long], failure: Throwable = null) extends Reader.SyncReader[Int] {
    val reads                                                            = new AtomicInteger
    private var index                                                    = 0
    override def jvmType: JvmType                                        = JvmType.Int
    def close(): Unit                                                    = ()
    def isClosed: Boolean                                                = false
    override def readable(): Boolean                                     = true
    def read[A >: Int](sentinel: A): A                                   = throw new AssertionError("generic read used")
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = {
      reads.incrementAndGet()
      if (failure ne null) throw failure
      if (index >= values.length) sentinel else { val value = values(index); index += 1; value }
    }
  }

  private trait Family {
    def name: String
    def expected: Long
    def apply(reader: Reader.AsyncReader[Int], callback: (Long, Int) => Async[Long]): Async[Long]
  }

  private val families = List[Family](
    new Family {
      val name                                                             = "take-drop"; val expected = 5L
      def apply(r: Reader.AsyncReader[Int], f: (Long, Int) => Async[Long]) =
        Sink.foldTakeDropNativeAsyncIntLong(r, 1L, 2L, 0L, f)
    },
    new Family {
      val name                                                             = "take-while"; val expected = 3L
      def apply(r: Reader.AsyncReader[Int], f: (Long, Int) => Async[Long]) =
        Sink.foldTakenWhileNativeAsyncIntLong(r, n => Async.succeed(n < 3), 0L, f)
    },
    new Family {
      val name                                                             = "filter"; val expected = 2L
      def apply(r: Reader.AsyncReader[Int], f: (Long, Int) => Async[Long]) =
        Sink.foldFilteredNativeAsyncIntLong(r, n => Async.succeed((n & 1) == 0), 0L, f)
    },
    new Family {
      val name                                                             = "map"; val expected = 9L
      def apply(r: Reader.AsyncReader[Int], f: (Long, Int) => Async[Long]) =
        Sink.foldMappedNativeAsyncIntLong(r, n => Async.succeed(n + 1), 0L, f)
    }
  )

  private def ready(values: Int*): IntAsyncReader =
    new IntAsyncReader(values.map(n => () => Async.succeed(n.toLong)).toVector)

  private def familyTests(family: Family): Spec[Any, Nothing] = suite(family.name)(
    test("uses the exact Int lane and obeys its boundary through EOF") {
      val reader = ready(1, 2, 3)
      assertTrue(family(reader, (a, n) => Async.succeed(a + n)).block == family.expected, reader.reads.get >= 3)
    },
    test("resumes genuine pending read value and EOF") {
      val value   = new Gate[Long]
      val vr      = new IntAsyncReader(Vector(() => value, () => Async.succeed(Long.MinValue)))
      val running = family(vr, (a, n) => Async.succeed(a + n)).start
      val pending = awaitPoll(value)
      value.succeed(2L)
      val eof        = new Gate[Long]
      val er         = new IntAsyncReader(Vector(() => eof))
      val eofRunning = family(er, (a, n) => Async.succeed(a + n)).start
      val eofPending = awaitPoll(eof)
      eof.succeed(Long.MinValue)
      assertTrue(pending, eofPending, running.block >= 0L, eofRunning.block == 0L)
    },
    test("resumes a genuine pending fold callback and drives replacement pollables") {
      val gate    = new Gate[Long]
      val running = family(ready(1, 2, 3), (a, n) => if (gate.polls.get == 0) gate else Async.succeed(a + n)).start
      val pending = awaitPoll(gate)
      gate.succeed(1L)
      val replacement = new Pollable[Long] {
        def poll(onComplete: Runnable): Async[Long] = { val _ = onComplete; Async.succeed(7L) }
      }
      val replaced = family(ready(1, 2, 3), (_, _) => replacement).block
      assertTrue(pending, running.block >= 1L, replaced >= 0L)
    },
    test("preserves read failures and normalizes callback failures") {
      val read                                = new RuntimeException("read")
      val trustedRead                         = StreamError.source("trusted-read")
      val callback                            = StreamError.source("callback")
      def readOutcome(effect: => Async[Long]) =
        pollOnce(family(new IntAsyncReader(Vector(() => effect)), (a, _) => Async.succeed(a)))
      def callbackOutcome(effect: => Async[Long]) =
        try pollOnce(family(ready(1, 2, 3), (_, _) => effect))
        catch { case error: Throwable => Failed(error, false) }
      val outcomes = List(
        readOutcome(Async.fail(read)),
        readOutcome(throw read),
        readOutcome(Async.failTrusted(trustedRead)),
        readOutcome(throw trustedRead)
      )
      val callbacks = List(callbackOutcome(throw callback), callbackOutcome(Async.failTrusted(callback)))
      assertTrue(
        outcomes.take(2).forall(_ == Failed(read, false)),
        outcomes.drop(2).forall(_ == Failed(trustedRead, true)),
        callbacks.forall {
          case Failed(error: StreamError, false) => error.value == "callback" && !error.isTrusted
          case _                                 => false
        }
      )
    },
    test("cancellation reaches an owned pending read and joins cleanup") {
      val gate    = new Gate[Long]
      val running = family(new IntAsyncReader(Vector(() => gate)), (a, _) => Async.succeed(a)).start
      val pending = awaitPoll(gate)
      val cleanup = Async.cancelWithCleanup(running)
      val joining = step(cleanup).isInstanceOf[Waiting[_]]
      gate.cleanup.succeed(())
      cleanup.block
      assertTrue(pending, joining, gate.cancellations.get == 1)
    },
    test("yields at its cooperative fairness boundary") {
      val reader = ready(Vector.fill(1025)(1): _*)
      val async  =
        if (family.name == "take-drop")
          Sink.foldTakeDropNativeAsyncIntLong(reader, 0L, 1025L, 0L, (a, n) => Async.succeed(a + n))
        else family(reader, (a, n) => Async.succeed(a + n))
      val yielded = step(async).isInstanceOf[Waiting[_]]
      assertTrue(yielded, async.block >= 0L)
    }
  )

  def spec = suite("Int-to-Long wrapper state machines")(
    (families.map(familyTests) ++ List(
      test("take-drop handles accepted, rejected, pending, thrown, and failed skip controls") {
        def run(control: Long => Async[Boolean]) = {
          val reader = new IntAsyncReader(Vector(() => Async.succeed(1L), () => Async.succeed(2L)), control)
          (Sink.foldTakeDropNativeAsyncIntLong(reader, 1L, 1L, 0L, (a, n) => Async.succeed(a + n)), reader)
        }
        val accepted   = run(_ => Async.succeed(true)); val rejected       = run(_ => Async.succeed(false))
        val gate       = new Gate[Boolean]; val pending                    = run(_ => gate); val started = pending._1.start
        val wasPending = awaitPoll(gate); gate.succeed(true)
        val thrown     = new RuntimeException("control-throw"); val failed = StreamError.source("control-fail")
        assertTrue(
          accepted._1.block == 1L,
          rejected._1.block == 2L,
          wasPending,
          started.block == 1L,
          pollOnce(run(_ => throw thrown)._1) == Failed(thrown, false),
          pollOnce(run(_ => throw failed)._1) == Failed(failed, true),
          pollOnce(run(_ => Async.failTrusted(failed))._1) == Failed(failed, true),
          accepted._2.skips.get == 1,
          rejected._2.skips.get == 1
        )
      },
      test("pending filter and take predicates resume on accept, reject, failure, and throw") {
        def check(factory: (Reader.AsyncReader[Int], Int => Async[Boolean]) => Async[Long]): Boolean = {
          val accept = new Gate[Boolean]
          val ar     = factory(ready(2), _ => accept).start
          val ap     = awaitPoll(accept); accept.succeed(true)
          val reject = new Gate[Boolean]
          val rr     = factory(ready(2), _ => reject).start
          val rp     = awaitPoll(reject); reject.succeed(false)
          val error  = StreamError.source("predicate")
          ap && rp && ar.block == 2L && rr.block == 0L &&
          pollOnce(factory(ready(2), _ => throw error)).isInstanceOf[Failed] &&
          (pollOnce(factory(ready(2), _ => Async.failTrusted(error))) match {
            case Failed(e: StreamError, false) => e.value == "predicate"
            case _                             => false
          })
        }
        val filtered = check((r, p) => Sink.foldFilteredNativeAsyncIntLong(r, p, 0L, (a, n) => Async.succeed(a + n)))
        val taken    = check((r, p) => Sink.foldTakenWhileNativeAsyncIntLong(r, p, 0L, (a, n) => Async.succeed(a + n)))
        assertTrue(filtered, taken)
      },
      test("native map suspends, resumes, and normalizes thrown and failed mapping") {
        val gate                           = new Gate[Int]
        val running                        = Sink.foldMappedNativeAsyncIntLong(ready(2), _ => gate, 0L, (a, n) => Async.succeed(a + n)).start
        val pending                        = awaitPoll(gate); gate.succeed(9)
        val error                          = StreamError.source("map")
        def outcome(effect: => Async[Int]) =
          try pollOnce(Sink.foldMappedNativeAsyncIntLong(ready(1), _ => effect, 0L, (a, n) => Async.succeed(a + n)))
          catch { case e: Throwable => Failed(e, false) }
        assertTrue(
          pending,
          running.block == 9L,
          outcome(throw error).isInstanceOf[Failed],
          outcome(Async.failTrusted(error)) match {
            case Failed(e: StreamError, false) => e.value == "map"
            case _                             => false
          }
        )
      },
      test("sync-reader map uses exact Int reads and suspends map and fold callbacks") {
        val mapGate     = new Gate[Int]
        val mapReader   = new IntSyncReader(Vector(2L))
        val mapped      = Sink.foldMappedSyncAsyncIntLong(mapReader, _ => mapGate, 0L, (a, n) => Async.succeed(a + n)).start
        val mapPending  = awaitPoll(mapGate); mapGate.succeed(4)
        val foldGate    = new Gate[Long]
        val foldReader  = new IntSyncReader(Vector(3L))
        val folded      = Sink.foldMappedSyncAsyncIntLong(foldReader, n => Async.succeed(n), 0L, (_, _) => foldGate).start
        val foldPending = awaitPoll(foldGate); foldGate.succeed(8L)
        assertTrue(
          mapPending,
          foldPending,
          mapped.block == 4L,
          folded.block == 8L,
          mapReader.reads.get == 2,
          foldReader.reads.get == 2
        )
      },
      test("sync-reader map preserves read failure and normalizes map/fold failures") {
        val ordinaryRead       = new RuntimeException("sync-read-ordinary")
        val read               = StreamError.source("sync-read"); val callback = StreamError.source("sync-callback")
        val ordinaryReadResult =
          pollOnce(
            Sink.foldMappedSyncAsyncIntLong(
              new IntSyncReader(Vector.empty, ordinaryRead),
              Async.succeed(_),
              0L,
              (a, _) => Async.succeed(a)
            )
          )
        val readResult = pollOnce(
          Sink.foldMappedSyncAsyncIntLong(
            new IntSyncReader(Vector.empty, read),
            Async.succeed(_),
            0L,
            (a, _) => Async.succeed(a)
          )
        )
        def result(map: Int => Async[Int], fold: (Long, Int) => Async[Long]) =
          try pollOnce(Sink.foldMappedSyncAsyncIntLong(new IntSyncReader(Vector(1L)), map, 0L, fold))
          catch { case error: Throwable => Failed(error, false) }
        assertTrue(
          ordinaryReadResult == Failed(ordinaryRead, false),
          readResult == Failed(read, true),
          result(_ => throw callback, (a, _) => Async.succeed(a)).isInstanceOf[Failed],
          result(Async.succeed(_), (_, _) => Async.failTrusted(callback)) match {
            case Failed(e: StreamError, false) => e.value == "sync-callback"
            case _                             => false
          }
        )
      },
      test("sync-reader map yields after 1024 ready elements") {
        val reader = new IntSyncReader(Vector.fill(1025)(1L))
        val async  = Sink.foldMappedSyncAsyncIntLong(reader, Async.succeed(_), 0L, (a, n) => Async.succeed(a + n))
        assertTrue(step(async).isInstanceOf[Waiting[_]], async.block == 1025L, reader.reads.get == 1026)
      }
    )): _*
  )
}
