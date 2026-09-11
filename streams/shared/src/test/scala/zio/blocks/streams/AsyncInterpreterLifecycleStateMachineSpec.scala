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

import java.io.IOException
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.AsyncInterpreter
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Observable lifecycle state-machine checks for the asynchronous interpreter.
 */
object AsyncInterpreterLifecycleStateMachineSpec extends StreamsBaseSpec {
  private val noop = new Runnable { def run(): Unit = () }

  private final class Gate[A] extends Async.Operation[A] {
    private var result: Async[A]             = null
    private var wake: Runnable               = null
    val polls                                = new AtomicInteger
    val cancellations                        = new AtomicInteger
    def poll(onComplete: Runnable): Async[A] = synchronized {
      polls.incrementAndGet()
      if (result == null) { wake = onComplete; this }
      else result
    }
    protected def cancelOperation(): Async[Unit] = { cancellations.incrementAndGet(); Async.succeed(()) }
    def succeed(value: A): Unit                  = complete(Async.succeed(value))
    def fail(cause: Throwable): Unit             = complete(Async.fail(cause))
    private def complete(value: Async[A]): Unit  = {
      val callback = synchronized { result = value; wake }
      if (callback ne null) callback.run()
    }
  }

  private final class IntSource(
    values: List[Int],
    readableEffect: () => Async[Boolean] = null,
    closeEffect: () => Async[Unit] = () => Async.succeed(())
  ) extends Reader.AsyncReader[Int] {
    private var remaining = values
    val reads             = new AtomicInteger
    val closes            = new AtomicInteger

    override def jvmType: JvmType  = JvmType.Int
    def isClosed: Async[Boolean]   = Async.succeed(closes.get != 0)
    def readable(): Async[Boolean] =
      if (readableEffect eq null) Async.succeed(remaining.nonEmpty) else readableEffect()
    def close(): Async[Unit]                  = { closes.incrementAndGet(); closeEffect() }
    def read[A >: Int](sentinel: A): Async[A] =
      readInt(Long.MinValue).map(value => if (value == Long.MinValue) sentinel else value.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = synchronized {
      reads.incrementAndGet()
      remaining match {
        case head :: tail => remaining = tail; Async.succeed(head.toLong)
        case Nil          => Async.succeed(sentinel)
      }
    }
  }

  private def pollable[A](effect: Async[A]): Pollable[A] = effect match {
    case value: Pollable[A @unchecked] => value
    case _                             => throw new AssertionError("expected a pending operation")
  }

  def spec = suite("AsyncInterpreter lifecycle state machine")(
    test("reentrant close from a lifecycle callback abandons the control owner without deadlock") {
      val interpreterRef = new AtomicReference[AsyncInterpreter]
      val nestedClose    = new AtomicReference[Async[Unit]]
      val source         = new IntSource(
        Nil,
        () => {
          val close = interpreterRef.get().closeForTest()
          nestedClose.set(close)
          pollable(close).poll(noop)
          Async.succeed(true)
        }
      )
      val interpreter = new AsyncInterpreter(source)
      interpreterRef.set(interpreter)
      for {
        result <- runAsync(interpreter.readable().either)
        _      <- runAsync(nestedClose.get())
        closed <- runAsync(interpreter.isClosed)
      } yield assertTrue(
        result.left.toOption.exists(_.isInstanceOf[IOException]),
        source.closes.get == 1,
        closed
      )
    },
    test("cancelling an active lifecycle query cancels its child exactly once and permits a later query") {
      val gate   = new Gate[Boolean]
      val source = new IntSource(Nil, () => gate)
      val reader = new AsyncInterpreter(source)
      val first  = pollable(reader.readable())
      first.poll(noop)
      for {
        _      <- runAsync(Async.cancelWithCleanup(first))
        _      <- runAsync(Async.cancelWithCleanup(first))
        second  = pollable(reader.readable())
        _       = second.poll(noop)
        _       = gate.succeed(true)
        result <- runAsync(second)
        _      <- runAsync(reader.closeForTest())
      } yield assertTrue(result, gate.polls.get >= 1, gate.cancellations.get == 1, source.closes.get == 1)
    },
    test("bulk reads cross cooperative-yield boundaries without loss or duplication") {
      val values = (0 until 600).toList
      val source = new IntSource(values)
      val reader = new AsyncInterpreter(source)
      for {
        chunk <- runAsync(reader.readN[Int](values.length))
        eof   <- runAsync(reader.readInt(-1L))
        _     <- runAsync(reader.closeForTest())
      } yield assertTrue(chunk == Chunk.fromIterable(values), eof == -1L, source.reads.get == values.length + 1)
    },
    test("cancelling a pull before nested materialization prevents the late owner from being installed") {
      val materialization = new Gate[Stream[Nothing, Int]]
      val inner           = new IntSource(List(99))
      val outer           = new IntSource(List(1))
      val stream          = Stream.fromReader[Nothing, Int](outer).flatMap(_ => Stream.unwrap(materialization))
      for {
        reader <- runAsync(stream.startAsync)
        pull    = pollable(reader.readInt(-1L))
        _       = pull.poll(noop)
        cancel  = Async.cancelWithCleanup(pull).start
        _       = materialization.succeed(Stream.fromReader[Nothing, Int](inner))
        _      <- runAsync(cancel)
        value  <- runAsync(pull)
        _      <- runAsync(reader.close())
      } yield assertTrue(value == -1L, inner.closes.get == 0, outer.closes.get == 1)
    },
    test("nested close failure remains primary when cancellation races its completion") {
      val closeGate = new Gate[Unit]
      val inner     = new IntSource(Nil, closeEffect = () => closeGate)
      val outer     = new IntSource(List(7))
      val reader    = new AsyncInterpreter(outer)
      val failure   = new IOException("inner close failed")
      reader.installNestedFrameForTest(inner, JvmType.Int)
      val pull = pollable(reader.readInt(-1L))
      pull.poll(noop)
      val cancellation = Async.cancelWithCleanup(pull).start
      closeGate.fail(failure)
      for {
        cancelled <- runAsync(cancellation.either)
        result    <- runAsync(pull.either)
        replay    <- runAsync(reader.readInt(-2L).either)
        _         <- runAsync(reader.closeForTest().either)
      } yield assertTrue(
        cancelled == Left(failure),
        result == Left(failure),
        replay == Left(failure),
        inner.closes.get == 1
      )
    }
  ) @@ TestAspect.sequential
}
