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

object AsyncTakenIntLongUseSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

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
  private def taken[E](reader: IntReader, n: Long)(fold: (Long, Int) => Async[Long]) =
    source[E](reader).take(n).runFoldAsync(0L)(fold)
  private def takenWhile[E](reader: IntReader, pred: Int => Boolean)(fold: (Long, Int) => Async[Long]) =
    source[E](reader).takeWhile(pred).runFoldAsync(0L)(fold)
  private val sum: (Long, Int) => Async[Long] = (acc, value) => Async.succeed(acc + value)

  private def callbackFailure(result: Either[Throwable, _], value: String): Boolean = result.left.exists {
    case error: StreamError => error.value == value && !error.isTrusted
    case _                  => false
  }

  def spec = suite("AsyncTakenIntLongUse and AsyncTakenWhileIntLongUse")(
    test("use exact physical Int reads for limits and predicate stopping") {
      val zero    = ready(1); val boundary  = ready(1, 2, 3); val over = ready(1, 2)
      val stopped = ready(1, 2, 3); val eof = ready(4, 5)
      for {
        z <- run(taken[Nothing](zero, 0)(sum)); b             <- run(taken[Nothing](boundary, 2)(sum));
        o <- run(taken[Nothing](over, 9)(sum))
        s <- run(takenWhile[Nothing](stopped, _ < 3)(sum)); e <- run(takenWhile[Nothing](eof, _ => true)(sum))
      } yield assertTrue(
        z == Right(0L),
        b == Right(3L),
        o == Right(3L),
        s == Right(3L),
        e == Right(9L),
        zero.readCount.get == 0,
        boundary.readCount.get == 2,
        over.readCount.get == 3,
        stopped.readCount.get == 3,
        eof.readCount.get == 3,
        List(zero, boundary, over, stopped, eof).forall(_.closeCount.get == 1)
      )
    },
    test("resume genuinely pending read values and EOF") {
      val takeValueEntered  = new Completer[Unit]; val takeValue                     = new Completer[Long]
      val takeEofEntered    = new Completer[Unit]; val takeEof                       = new Completer[Long]
      val whileValueEntered = new Completer[Unit]; val whileValue                    = new Completer[Long]
      val whileEofEntered   = new Completer[Unit]; val whileEof                      = new Completer[Long]
      val tv                = new IntReader(Vector { () => takeValueEntered.succeed(()); takeValue })
      val te                = new IntReader(Vector { () => takeEofEntered.succeed(()); takeEof })
      val wv                = new IntReader(Vector { () => whileValueEntered.succeed(()); whileValue })
      val we                = new IntReader(Vector { () => whileEofEntered.succeed(()); whileEof })
      val tvr               = taken[Nothing](tv, 1)(sum).start; val ter              = taken[Nothing](te, 1)(sum).start
      val wvr               = takenWhile[Nothing](wv, _ => true)(sum).start; val wer = takenWhile[Nothing](we, _ => true)(sum).start
      for {
        _ <- run(takeValueEntered); _  = takeValue.succeed(7L); _  <- run(takeEofEntered);
        _  = takeEof.succeed(Long.MinValue)
        _ <- run(whileValueEntered); _ = whileValue.succeed(8L); _ <- run(whileEofEntered);
        _  = whileEof.succeed(Long.MinValue)
        a <-
          run(tvr);
        b <-
          run(ter);
        c <-
          run(wvr);
        d <- run(wer)
      } yield assertTrue(
        a == Right(7L),
        b == Right(0L),
        c == Right(8L),
        d == Right(0L),
        List(tv, te, wv, we).forall(_.closeCount.get == 1)
      )
    },
    test("resume genuinely pending folds") {
      val takeEntered  = new Completer[Unit]; val takeGate  = new Completer[Long]
      val whileEntered = new Completer[Unit]; val whileGate = new Completer[Long]
      val tr           = ready(2, 3); val wr                = ready(4, 5)
      val t            = taken[Nothing](tr, 2) { (a, v) =>
        if (v == 2) { takeEntered.succeed(()); takeGate }
        else Async.succeed(a + v)
      }.start
      val w = takenWhile[Nothing](wr, _ => true) { (a, v) =>
        if (v == 4) { whileEntered.succeed(()); whileGate }
        else Async.succeed(a + v)
      }.start
      for {
        _ <- run(takeEntered); _ = takeGate.succeed(2L); _ <- run(whileEntered); _ = whileGate.succeed(4L)
        a <-
          run(t);
        b <- run(w)
      } yield assertTrue(
        a == Right(5L),
        b == Right(9L),
        tr.readCount.get == 2,
        wr.readCount.get == 3,
        tr.closeCount.get == 1,
        wr.closeCount.get == 1
      )
    },
    test("preserve ordinary and trusted semantics for thrown and failed reads") {
      val takeOrdinaryFail   = new RuntimeException("take-ordinary-fail")
      val takeOrdinaryThrow  = new RuntimeException("take-ordinary-throw")
      val takeTrustedFail    = StreamError.source("take-trusted-fail")
      val takeTrustedThrow   = StreamError.source("take-trusted-throw")
      val whileOrdinaryFail  = new RuntimeException("while-ordinary-fail")
      val whileOrdinaryThrow = new RuntimeException("while-ordinary-throw")
      val whileTrustedFail   = StreamError.source("while-trusted-fail")
      val whileTrustedThrow  = StreamError.source("while-trusted-throw")
      val readers            = Vector(
        new IntReader(Vector(() => Async.fail(takeOrdinaryFail))),
        new IntReader(Vector(() => throw takeOrdinaryThrow)),
        new IntReader(Vector(() => Async.failTrusted(takeTrustedFail))),
        new IntReader(Vector(() => throw takeTrustedThrow)),
        new IntReader(Vector(() => Async.fail(whileOrdinaryFail))),
        new IntReader(Vector(() => throw whileOrdinaryThrow)),
        new IntReader(Vector(() => Async.failTrusted(whileTrustedFail))),
        new IntReader(Vector(() => throw whileTrustedThrow))
      )
      for {
        a <- run(taken[Nothing](readers(0), 1)(sum).either)
        b <- run(taken[Nothing](readers(1), 1)(sum).either)
        c <- run(taken[String](readers(2), 1)(sum))
        d <- run(taken[String](readers(3), 1)(sum))
        e <- run(takenWhile[Nothing](readers(4), _ => true)(sum).either)
        f <- run(takenWhile[Nothing](readers(5), _ => true)(sum).either)
        g <- run(takenWhile[String](readers(6), _ => true)(sum))
        h <- run(takenWhile[String](readers(7), _ => true)(sum))
      } yield assertTrue(
        a == Left(takeOrdinaryFail),
        b == Left(takeOrdinaryThrow),
        c == Left("take-trusted-fail"),
        d == Left("take-trusted-throw"),
        e == Left(whileOrdinaryFail),
        f == Left(whileOrdinaryThrow),
        g == Left("while-trusted-fail"),
        h == Left("while-trusted-throw"),
        readers.forall(_.closeCount.get == 1)
      )
    },
    test("protect callback throws and failed callbacks") {
      val takeThrow = new StreamError("take-throw"); val takeFail   = new StreamError("take-fail")
      val predThrow = new StreamError("pred-throw"); val whileThrow = new StreamError("while-throw");
      val whileFail = new StreamError("while-fail")
      for {
        a <- run(taken[Nothing](ready(1), 1)((_, _) => throw takeThrow).either)
        b <- run(taken[Nothing](ready(1), 1)((_, _) => Async.failTrusted(takeFail)).either)
        c <- run(takenWhile[Nothing](ready(1), _ => throw predThrow)(sum).either)
        d <- run(takenWhile[Nothing](ready(1), _ => true)((_, _) => throw whileThrow).either)
        e <- run(takenWhile[Nothing](ready(1), _ => true)((_, _) => Async.failTrusted(whileFail)).either)
      } yield assertTrue(
        callbackFailure(a, "take-throw"),
        callbackFailure(b, "take-fail"),
        callbackFailure(c, "pred-throw"),
        callbackFailure(d, "while-throw"),
        callbackFailure(e, "while-fail")
      )
    },
    test("report close-only failures and suppress close failures onto use failures") {
      val tc         = new RuntimeException("take-close"); val tp  = new RuntimeException("take-primary");
      val ts         = new RuntimeException("take-suppressed")
      val wc         = new RuntimeException("while-close"); val wp = new RuntimeException("while-primary");
      val ws         = new RuntimeException("while-suppressed")
      val takeClose  = new IntReader(Vector.empty, () => Async.fail(tc));
      val takeUse    = new IntReader(Vector(() => Async.fail(tp)), () => Async.fail(ts))
      val whileClose = new IntReader(Vector.empty, () => Async.fail(wc));
      val whileUse   = new IntReader(Vector(() => Async.fail(wp)), () => Async.fail(ws))
      for {
        a <- run(taken[Nothing](takeClose, 1)(sum).either); b <- run(taken[Nothing](takeUse, 1)(sum).either)
        c <- run(takenWhile[Nothing](whileClose, _ => true)(sum).either);
        d <- run(takenWhile[Nothing](whileUse, _ => true)(sum).either)
      } yield assertTrue(
        a == Left(tc),
        c == Left(wc),
        b.left.exists(x => (x eq tp) && x.getSuppressed.toList == List(ts)),
        d.left.exists(x => (x eq wp) && x.getSuppressed.toList == List(ws)),
        List(takeClose, takeUse, whileClose, whileUse).forall(_.closeCount.get == 1)
      )
    },
    test("protect thrown and failed acquisition for both owners") {
      val tt = new StreamError("take-acquire-throw"); val tf  = new StreamError("take-acquire-fail")
      val wt = new StreamError("while-acquire-throw"); val wf = new StreamError("while-acquire-fail")
      for {
        a <- run(Stream.fromReaderAsync[Nothing, Int](throw tt).take(1).runFoldAsync(0L)(sum).either)
        b <- run(Stream.fromReaderAsync[Nothing, Int](Async.fail(tf)).take(1).runFoldAsync(0L)(sum).either)
        c <- run(Stream.fromReaderAsync[Nothing, Int](throw wt).takeWhile(_ => true).runFoldAsync(0L)(sum).either)
        d <- run(Stream.fromReaderAsync[Nothing, Int](Async.fail(wf)).takeWhile(_ => true).runFoldAsync(0L)(sum).either)
      } yield assertTrue(
        callbackFailure(a, "take-acquire-throw"),
        callbackFailure(b, "take-acquire-fail"),
        callbackFailure(c, "while-acquire-throw"),
        callbackFailure(d, "while-acquire-fail")
      )
    },
    test("close when acquisition wins cancellation for both owners") {
      val tr              = ready(1); val wr                    = ready(1)
      val ta              = new CancelOnPoll[Reader[Int]](Async.succeed(tr: Reader[Int]));
      val wa              = new CancelOnPoll[Reader[Int]](Async.succeed(wr: Reader[Int]))
      val t               = Stream.fromReaderAsync[Nothing, Int](ta).take(1).runFoldAsync(0L)(sum).start
      val w               = Stream.fromReaderAsync[Nothing, Int](wa).takeWhile(_ => true).runFoldAsync(0L)(sum).start
      var tc: Async[Unit] = null; var wc: Async[Unit]           = null
      val tCancelled      = new Completer[Unit]; val wCancelled = new Completer[Unit]
      for {
        _ <- run(ta.entered);
        _  = ta.arm { () => tc = Async.cancelWithCleanup(t.asInstanceOf[Pollable[Any]]); tCancelled.succeed(()) }
        _ <- run(tCancelled); _ <- run(tc)
        _ <- run(wa.entered);
        _  = wa.arm { () => wc = Async.cancelWithCleanup(w.asInstanceOf[Pollable[Any]]); wCancelled.succeed(()) }
        _ <- run(wCancelled); _ <- run(wc)
      } yield assertTrue(tr.closeCount.get == 1, wr.closeCount.get == 1, tr.readCount.get == 0, wr.readCount.get == 0)
    },
    test("yield after exactly 1024 ready folds and resume for both owners") {
      def check(effect: Async[Either[Nothing, Long]]): Step[Either[Nothing, Long]] =
        step(effect) match {
          case Pending(operation) => step(operation.poll(() => ()))
          case other              => other
        }

      val takeGateEntered  = new Completer[Unit]; val takeGate  = new Completer[Long]
      val whileGateEntered = new Completer[Unit]; val whileGate = new Completer[Long]
      val readyReads       = (1 to 1024).map(value => () => Async.succeed(value.toLong)).toVector
      val tr               = new IntReader(readyReads :+ (() => { takeGateEntered.succeed(()); takeGate }))
      val wr               = new IntReader(readyReads :+ (() => { whileGateEntered.succeed(()); whileGate }))
      val tc               = new AtomicInteger; val wc          = new AtomicInteger
      val te               = taken[Nothing](tr, 1025) { (a, v) => tc.incrementAndGet(); Async.succeed(a + v) }
      val we               = takenWhile[Nothing](wr, _ => true) { (a, v) => wc.incrementAndGet(); Async.succeed(a + v) }
      val t                = check(te); val w                   = check(we)
      for {
        _               <- run(takeGateEntered); _ <- run(whileGateEntered)
        callbacksAtYield = (tc.get, wc.get)
        _                = takeGate.succeed(1025L)
        _                = whileGate.succeed(1025L)
        a               <- run(te); b              <- run(we)
      } yield assertTrue(
        t.isInstanceOf[Pending[_]],
        w.isInstanceOf[Pending[_]],
        callbacksAtYield == ((1024, 1024)),
        a == Right(525825L),
        b == Right(525825L),
        tc.get == 1025,
        wc.get == 1025,
        tr.readCount.get == 1025,
        wr.readCount.get == 1026,
        tr.closeCount.get == 1,
        wr.closeCount.get == 1
      )
    }
  )
}
