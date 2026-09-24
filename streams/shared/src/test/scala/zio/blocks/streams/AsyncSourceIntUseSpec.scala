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

import scala.concurrent.ExecutionContext

import zio.ZIO
import zio.blocks.async._
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncSourceIntUseSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

  private final case class Total(value: Int)

  private final class IntReader(
    reads: Vector[() => Async[Long]],
    closeEffect: () => Async[Unit] = () => Async.succeed(())
  ) extends Reader.AsyncReader[Int] {
    private var index                                                           = 0
    val readCount                                                               = new AtomicInteger
    val closeCount                                                              = new AtomicInteger
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = { closeCount.incrementAndGet(); closeEffect() }
    def isClosed: Async[Boolean]                                                = Async.succeed(index >= reads.length)
    def readable(): Async[Boolean]                                              = Async.succeed(index < reads.length)
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      readCount.incrementAndGet()
      if (index >= reads.length) Async.succeed(sentinel)
      else {
        val read = reads(index)
        index += 1
        read()
      }
    }
  }

  private final class CancelOnPoll[A](result: Async[A]) extends Pollable[A] {
    private var armed                    = false
    private var cancelAction: () => Unit = null
    private var wake: Runnable           = null
    val entered                          = new Completer[Unit]

    def arm(action: () => Unit): Unit = {
      val notify = synchronized { armed = true; cancelAction = action; wake }
      if (notify ne null) notify.run()
    }
    def poll(onComplete: Runnable): Async[A] = {
      val action = synchronized {
        if (armed) cancelAction
        else { wake = onComplete; entered.succeed(()); null }
      }
      if (action eq null) this else { action(); result }
    }
  }

  private sealed trait Step[+A]
  private final case class Ready[A](value: A)             extends Step[A]
  private final case class Failed(cause: Throwable)       extends Step[Nothing]
  private final case class Pending[A](value: Pollable[A]) extends Step[A]

  private def step[A](effect: Async[A]): Step[A] =
    Async.foldStep(effect)(new Async.StepFold[A, Step[A]] {
      def success(value: A): Step[A]                         = Ready(value)
      def failure(cause: Throwable): Step[A]                 = Failed(cause)
      override def trustedFailure(cause: Throwable): Step[A] = Failed(cause)
      def pending(value: Pollable[A]): Step[A]               = Pending(value)
    })

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)
  private def ready(values: Int*): IntReader                   =
    new IntReader(values.map(value => () => Async.succeed(value.toLong)).toVector)
  private def source[E](reader: IntReader): Stream[E, Int] =
    Stream.fromReaderAsync[E, Int](Async.succeed(reader: Reader[Int]))
  private def fold[E](reader: IntReader)                                      = source[E](reader).take(Long.MaxValue).runFoldAsync(Total(0))(sum)
  private def foldWith[E](reader: IntReader)(f: (Total, Int) => Async[Total]) =
    source[E](reader).take(Long.MaxValue).runFoldAsync(Total(0))(f)
  private def drain[E](reader: IntReader)                          = source[E](reader).runDrainAsync
  private def foreach[E](reader: IntReader)(f: Int => Async[Unit]) = source[E](reader).runForeachAsync(f)
  private val sum: (Total, Int) => Async[Total]                    = (acc, value) => Async.succeed(Total(acc.value + value))

  private def callbackFailure(result: Either[Throwable, _], value: String): Boolean = result.left.exists {
    case error: StreamError => error.value == value && !error.isTrusted
    case _                  => false
  }

  def spec = suite("AsyncSourceIntFoldUse and AsyncSourceIntDrainUse")(
    test("use exact Int physical reads for ready values and EOF") {
      val fr = ready(1, 2, 3); val dr = ready(4, 5)
      for {
        f <- run(fold[Nothing](fr)); d <- run(drain[Nothing](dr))
      } yield assertTrue(
        f == Right(Total(6)),
        d == Right(()),
        fr.readCount.get == 4,
        dr.readCount.get == 3,
        fr.closeCount.get == 1,
        dr.closeCount.get == 1
      )
    },
    test("resume genuinely pending Int values and EOF") {
      val fvEntered = new Completer[Unit]; val fvGate   = new Completer[Long]
      val feEntered = new Completer[Unit]; val feGate   = new Completer[Long]
      val dvEntered = new Completer[Unit]; val dvGate   = new Completer[Long]
      val deEntered = new Completer[Unit]; val deGate   = new Completer[Long]
      val fv        = new IntReader(Vector { () => fvEntered.succeed(()); fvGate })
      val fe        = new IntReader(Vector { () => feEntered.succeed(()); feGate })
      val dv        = new IntReader(Vector { () => dvEntered.succeed(()); dvGate })
      val de        = new IntReader(Vector { () => deEntered.succeed(()); deGate })
      val fvr       = fold[Nothing](fv).start; val fer  = fold[Nothing](fe).start
      val dvr       = drain[Nothing](dv).start; val der = drain[Nothing](de).start
      for {
        _ <- run(fvEntered); _ = fvGate.succeed(7L); _ <- run(feEntered); _ = feGate.succeed(Long.MinValue)
        _ <- run(dvEntered); _ = dvGate.succeed(8L); _ <- run(deEntered); _ = deGate.succeed(Long.MinValue)
        a <- run(fvr); b      <- run(fer); c           <- run(dvr); d      <- run(der)
      } yield assertTrue(
        a == Right(Total(7)),
        b == Right(Total(0)),
        c == Right(()),
        d == Right(()),
        fv.readCount.get == 2,
        fe.readCount.get == 1,
        dv.readCount.get == 2,
        de.readCount.get == 1,
        List(fv, fe, dv, de).forall(_.closeCount.get == 1)
      )
    },
    test("resume a genuinely pending reference fold") {
      val entered = new Completer[Unit]; val gate = new Completer[Total]; val reader = ready(2, 3)
      val result  = foldWith[Nothing](reader) { (acc, value) =>
        if (value == 2) { entered.succeed(()); gate }
        else Async.succeed(Total(acc.value + value))
      }.start
      for {
        _ <- run(entered); _ = gate.succeed(Total(2)); value <- run(result)
      } yield assertTrue(value == Right(Total(5)), reader.readCount.get == 3, reader.closeCount.get == 1)
    },
    test("preserve ordinary and trusted thrown and failed reads for both owners") {
      val failures = Vector[Throwable](
        new RuntimeException("fold-fail"),
        new RuntimeException("fold-throw"),
        StreamError.source("fold-trusted-fail"),
        StreamError.source("fold-trusted-throw"),
        new RuntimeException("drain-fail"),
        new RuntimeException("drain-throw"),
        StreamError.source("drain-trusted-fail"),
        StreamError.source("drain-trusted-throw")
      )
      val readers = Vector(
        new IntReader(Vector(() => Async.fail(failures(0)))),
        new IntReader(Vector(() => throw failures(1))),
        new IntReader(Vector(() => Async.failTrusted(failures(2)))),
        new IntReader(Vector(() => throw failures(3))),
        new IntReader(Vector(() => Async.fail(failures(4)))),
        new IntReader(Vector(() => throw failures(5))),
        new IntReader(Vector(() => Async.failTrusted(failures(6)))),
        new IntReader(Vector(() => throw failures(7)))
      )
      for {
        a <- run(fold[Nothing](readers(0)).either); b  <- run(fold[Nothing](readers(1)).either)
        c <- run(fold[String](readers(2))); d          <- run(fold[String](readers(3)))
        e <- run(drain[Nothing](readers(4)).either); f <- run(drain[Nothing](readers(5)).either)
        g <- run(drain[String](readers(6))); h         <- run(drain[String](readers(7)))
      } yield assertTrue(
        a == Left(failures(0)),
        b == Left(failures(1)),
        c == Left("fold-trusted-fail"),
        d == Left("fold-trusted-throw"),
        e == Left(failures(4)),
        f == Left(failures(5)),
        g == Left("drain-trusted-fail"),
        h == Left("drain-trusted-throw"),
        readers.forall(r => r.readCount.get == 1 && r.closeCount.get == 1)
      )
    },
    test("normalize thrown and failed fold callback trust") {
      val thrown = new StreamError("fold-callback-throw"); val failed = new StreamError("fold-callback-fail")
      val tr     = ready(1); val fr                                   = ready(1)
      for {
        a <- run(foldWith[Nothing](tr)((_, _) => throw thrown).either)
        b <- run(foldWith[Nothing](fr)((_, _) => Async.failTrusted(failed)).either)
      } yield assertTrue(
        callbackFailure(a, "fold-callback-throw"),
        callbackFailure(b, "fold-callback-fail"),
        tr.readCount.get == 1,
        fr.readCount.get == 1,
        tr.closeCount.get == 1,
        fr.closeCount.get == 1
      )
    },
    test("classify foreach read and callback failures and resume a pending callback") {
      val ordinaryRead   = new RuntimeException("foreach-read")
      val trustedRead    = StreamError.source("foreach-typed-read")
      val callbackThrow  = new StreamError("foreach-callback-throw")
      val callbackFail   = new StreamError("foreach-callback-fail")
      val readThrown     = new IntReader(Vector(() => throw ordinaryRead))
      val readTrusted    = new IntReader(Vector(() => throw trustedRead))
      val callbackThrown = ready(1)
      val callbackFailed = ready(1)
      val pendingReader  = ready(1, 2)
      val entered        = new Completer[Unit]
      val gate           = new Completer[Unit]
      val pending        = foreach[Nothing](pendingReader) { value =>
        if (value == 1) { entered.succeed(()); gate }
        else Async.succeed(())
      }.start
      for {
        thrownResult         <- run(foreach[Nothing](readThrown)(_ => Async.succeed(())).either)
        trustedResult        <- run(foreach[String](readTrusted)(_ => Async.succeed(())))
        callbackThrownResult <- run(foreach[Nothing](callbackThrown)(_ => throw callbackThrow).either)
        callbackFailedResult <- run(foreach[Nothing](callbackFailed)(_ => Async.failTrusted(callbackFail)).either)
        _                    <- run(entered)
        _                     = gate.succeed(())
        pendingResult        <- run(pending)
      } yield assertTrue(
        thrownResult == Left(ordinaryRead),
        trustedResult == Left("foreach-typed-read"),
        callbackFailure(callbackThrownResult, "foreach-callback-throw"),
        callbackFailure(callbackFailedResult, "foreach-callback-fail"),
        pendingResult == Right(()),
        List(readThrown, readTrusted, callbackThrown, callbackFailed, pendingReader).forall(_.closeCount.get == 1)
      )
    },
    test("report close-only failures and suppress close failures onto use failures") {
      val fc         = new RuntimeException("fold-close"); val fp  = new RuntimeException("fold-primary");
      val fs         = new RuntimeException("fold-suppressed")
      val dc         = new RuntimeException("drain-close"); val dp = new RuntimeException("drain-primary");
      val ds         = new RuntimeException("drain-suppressed")
      val foldClose  = new IntReader(Vector.empty, () => Async.fail(fc))
      val foldUse    = new IntReader(Vector(() => Async.fail(fp)), () => Async.fail(fs))
      val drainClose = new IntReader(Vector.empty, () => Async.fail(dc))
      val drainUse   = new IntReader(Vector(() => Async.fail(dp)), () => Async.fail(ds))
      for {
        a <- run(fold[Nothing](foldClose).either); b   <- run(fold[Nothing](foldUse).either)
        c <- run(drain[Nothing](drainClose).either); d <- run(drain[Nothing](drainUse).either)
      } yield assertTrue(
        a == Left(fc),
        c == Left(dc),
        b.left.exists(x => (x eq fp) && x.getSuppressed.toList == List(fs)),
        d.left.exists(x => (x eq dp) && x.getSuppressed.toList == List(ds)),
        List(foldClose, foldUse, drainClose, drainUse).forall(_.closeCount.get == 1)
      )
    },
    test("protect thrown and failed acquisition for both owners") {
      val ft = new StreamError("fold-acquire-throw"); val ff  = new StreamError("fold-acquire-fail")
      val dt = new StreamError("drain-acquire-throw"); val df = new StreamError("drain-acquire-fail")
      for {
        a <- run(Stream.fromReaderAsync[Nothing, Int](throw ft).take(Long.MaxValue).runFoldAsync(Total(0))(sum).either)
        b <-
          run(
            Stream.fromReaderAsync[Nothing, Int](Async.fail(ff)).take(Long.MaxValue).runFoldAsync(Total(0))(sum).either
          )
        c <- run(Stream.fromReaderAsync[Nothing, Int](throw dt).runDrainAsync.either)
        d <- run(Stream.fromReaderAsync[Nothing, Int](Async.fail(df)).runDrainAsync.either)
      } yield assertTrue(
        callbackFailure(a, "fold-acquire-throw"),
        callbackFailure(b, "fold-acquire-fail"),
        callbackFailure(c, "drain-acquire-throw"),
        callbackFailure(d, "drain-acquire-fail")
      )
    },
    test("close when acquisition wins cancellation for both owners") {
      val fr              = ready(1); val dr                    = ready(1)
      val fa              = new CancelOnPoll[Reader[Int]](Async.succeed(fr: Reader[Int]))
      val da              = new CancelOnPoll[Reader[Int]](Async.succeed(dr: Reader[Int]))
      val f               = Stream.fromReaderAsync[Nothing, Int](fa).take(Long.MaxValue).runFoldAsync(Total(0))(sum).start
      val d               = Stream.fromReaderAsync[Nothing, Int](da).runDrainAsync.start
      var fc: Async[Unit] = null; var dc: Async[Unit]           = null
      val fCancelled      = new Completer[Unit]; val dCancelled = new Completer[Unit]
      for {
        _ <- run(fa.entered); _  = fa.arm { () => fc = Async.cancelWithCleanup(f); fCancelled.succeed(()) }
        _ <- run(fCancelled); _ <- run(fc)
        _ <- run(da.entered); _  = da.arm { () => dc = Async.cancelWithCleanup(d); dCancelled.succeed(()) }
        _ <-
          run(dCancelled);
        _ <- run(dc)
      } yield assertTrue(
        fr.readCount.get == 0,
        dr.readCount.get == 0,
        fr.closeCount.get == 1,
        dr.closeCount.get == 1
      )
    },
    test("yield after exactly 1024 ready reads and resume both owners") {
      def pendingAfterFirstPoll[A](effect: Async[A], onComplete: Runnable): Boolean = step(effect) match {
        case Pending(operation) => step(operation.poll(onComplete)).isInstanceOf[Pending[_]]
        case _                  => false
      }
      val fr               = ready((1 to 1025): _*); val dr = ready((1 to 1025): _*)
      val callbacks        = new AtomicInteger
      val callbacksAtYield = new AtomicInteger(-1)
      val yieldPermit      = new Completer[Unit]
      val f                = foldWith[Nothing](fr) { (acc, value) =>
        callbacks.incrementAndGet(); Async.succeed(Total(acc.value + value))
      }
      val d  = drain[Nothing](dr)
      val fp = pendingAfterFirstPoll(f, () => { callbacksAtYield.set(callbacks.get); yieldPermit.succeed(()) })
      val dp = pendingAfterFirstPoll(d, () => ())
      for {
        _ <- run(yieldPermit); fv <- run(f); dv <- run(d)
      } yield assertTrue(
        fp,
        dp,
        callbacksAtYield.get == 1024,
        fv == Right(Total(525825)),
        dv == Right(()),
        callbacks.get == 1025,
        fr.readCount.get == 1026,
        dr.readCount.get == 1026,
        fr.closeCount.get == 1,
        dr.closeCount.get == 1
      )
    }
  )
}
