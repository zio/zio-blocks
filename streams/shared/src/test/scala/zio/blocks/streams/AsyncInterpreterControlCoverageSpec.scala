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
import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.streams.internal.AsyncInterpreter
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncInterpreterControlCoverageSpec extends StreamsBaseSpec {
  private val noop = new Runnable { def run(): Unit = () }

  private final class Gate[A](replacement: Pollable[A] = null, wakeBeforeReturn: Boolean = false)
      extends Async.Operation[A] {
    val polls                                = new AtomicInteger
    val cancellations                        = new AtomicInteger
    var callback: Runnable                   = null
    def poll(onComplete: Runnable): Async[A] = {
      callback = onComplete
      polls.incrementAndGet()
      if (wakeBeforeReturn) { onComplete.run(); onComplete.run() }
      if (replacement eq null) this else replacement
    }
    protected def cancelOperation(): Async[Unit] = {
      cancellations.incrementAndGet()
      Async.succeed(())
    }
  }

  private final class Ready[A](value: A) extends Pollable[A] {
    val polls                                = new AtomicInteger
    def poll(onComplete: Runnable): Async[A] = { polls.incrementAndGet(); Async.succeed(value) }
  }

  private class Source(var next: Async[Any]) extends Reader.AsyncReader[Any] {
    var closed                                     = false
    val closes, reads, controls, resets            = new AtomicInteger
    def close(): Async[Unit]                       = { closes.incrementAndGet(); closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]                   = Async.succeed(closed)
    def readable(): Async[Boolean]                 = Async.succeed(!closed)
    def read[A >: Any](sentinel: A): Async[A]      = { reads.incrementAndGet(); next.asInstanceOf[Async[A]] }
    override def setLimit(n: Long): Async[Boolean] = { controls.incrementAndGet(); Async.succeed(n == 7) }
    override def setRepeat(): Async[Boolean]       = { controls.incrementAndGet(); Async.succeed(true) }
    override def setSkip(n: Long): Async[Boolean]  = { controls.incrementAndGet(); Async.succeed(n == 2) }
    override def reset(): Async[Unit]              = { resets.incrementAndGet(); closed = false; Async.succeed(()) }
  }

  private final class LaneSource(lane: JvmType, result: Async[Any], eofRef: Boolean = false) extends Source(result) {
    override def jvmType: JvmType                      = lane
    override def read[A >: Any](sentinel: A): Async[A] =
      if (eofRef) Async.succeed(sentinel)
      else result.asInstanceOf[Async[A]]
    override def readBoolean(s: Int)(implicit ev: Any <:< Boolean): Async[Int]                                 = result.asInstanceOf[Async[Int]]
    override def readByte(): Async[Int]                                                                        = result.asInstanceOf[Async[Int]]
    override def readChar(s: Int)(implicit ev: Any <:< Char): Async[Int]                                       = result.asInstanceOf[Async[Int]]
    override def readShort(s: Int)(implicit ev: Any <:< Short): Async[Int]                                     = result.asInstanceOf[Async[Int]]
    override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long]                                       = result.asInstanceOf[Async[Long]]
    override def readLong(s: Long)(implicit ev: Any <:< Long): Async[Long]                                     = result.asInstanceOf[Async[Long]]
    override def readFloat(s: Double)(implicit ev: Any <:< Float): Async[Double]                               = result.asInstanceOf[Async[Double]]
    override def readDouble(s: Double)(implicit ev: Any <:< Double): Async[Double]                             = result.asInstanceOf[Async[Double]]
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      result.map { value =>
        if (eofRef) -1
        else { dest(offset) = value.asInstanceOf[Long]; 1 }
      }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      result.map { value =>
        if (eofRef) -1
        else { dest(offset) = value.asInstanceOf[Double]; 1 }
      }
  }

  private val lanes = List[(JvmType, Any, Any)](
    (JvmType.Boolean, 1, 1),
    (JvmType.Byte, 7, 7),
    (JvmType.Char, 65, 65),
    (JvmType.Short, 8, 8),
    (JvmType.Int, 9L, 9L),
    (JvmType.Long, 10L, 10L),
    (JvmType.Float, 1.25d, 1.25d),
    (JvmType.Double, 2.5d, 2.5d),
    (JvmType.AnyRef, "x", "x")
  )

  private def pull(i: AsyncInterpreter, lane: JvmType): Async[Any] = (lane match {
    case JvmType.Boolean => i.readBoolean(-1)
    case JvmType.Byte    => i.readByte()
    case JvmType.Char    => i.readChar(-1)
    case JvmType.Short   => i.readShort(-1)
    case JvmType.Int     => i.readInt(-1)
    case JvmType.Long    => i.readLong(-1)
    case JvmType.Float   => i.readFloat(-1)
    case JvmType.Double  => i.readDouble(-1)
    case JvmType.AnyRef  => i.read[Any]("eof")
  }).asInstanceOf[Async[Any]]

  def spec = suite("AsyncInterpreter control and read drive coverage")(
    test("ready, trusted failure, EOF, closed, and sticky terminal paths cover every physical lane") {
      for {
        results <- zio.ZIO.foreach(lanes) { case (lane, raw, expected) =>
                     val ready       = new AsyncInterpreter(new LaneSource(lane, Async.succeed(raw)))
                     val boom        = new RuntimeException(lane.toString)
                     val failed      = new AsyncInterpreter(new LaneSource(lane, Async.failTrusted(boom)))
                     val eofRaw: Any = lane match {
                       case JvmType.Boolean                => -1
                       case JvmType.Byte                   => -1
                       case JvmType.Char | JvmType.Short   => Int.MinValue
                       case JvmType.Int                    => Long.MinValue
                       case JvmType.Long                   => Long.MaxValue
                       case JvmType.Float | JvmType.Double => Double.MaxValue
                       case JvmType.AnyRef                 => null
                     }
                     val collisionSafe = lane == JvmType.Long || lane == JvmType.Double
                     val eof           = new AsyncInterpreter(
                       new LaneSource(lane, Async.succeed(eofRaw), collisionSafe || lane == JvmType.AnyRef)
                     )
                     for {
                       value   <- runAsync(pull(ready, lane))
                       failure <- runAsync(pull(failed, lane).either)
                       sticky  <- runAsync(pull(failed, lane).either)
                       end     <- runAsync(pull(eof, lane))
                       _       <- runAsync(eof.closeForTest())
                       closed  <- runAsync(pull(eof, lane))
                     } yield value == expected && failure == Left(boom) && sticky == Left(boom) && end == closed
                   }
      } yield assertTrue(results.forall(identity))
    },
    test("self suspension, replacement, callback-before-return, and duplicate callback redrive") {
      val terminal          = new Ready[Any](42)
      val replacement       = new Gate[Any](terminal, wakeBeforeReturn = true)
      val interpreter       = new AsyncInterpreter(new Source(replacement))
      val operation         = interpreter.read[Any]("eof")
      val first             = operation.asInstanceOf[Pollable[Any]].poll(noop)
      val self              = new Gate[Any]()
      val secondInterpreter = new AsyncInterpreter(new Source(self))
      val second            = secondInterpreter.read[Any]("eof")
      val suspended         = second.asInstanceOf[Pollable[Any]].poll(noop)
      self.callback.run(); self.callback.run()
      assertTrue(
        first.block == 42,
        replacement.polls.get == 1,
        terminal.polls.get == 1,
        suspended.asInstanceOf[AnyRef] eq second.asInstanceOf[AnyRef]
      )
    },
    test("cancellation before poll is idempotent and allows a following operation") {
      val source       = new Source(Async.succeed(3))
      val interpreter  = new AsyncInterpreter(source)
      val op           = interpreter.read[Any]("eof")
      val cancellation = Async.cancelWithCleanup(op.asInstanceOf[Pollable[Any]])
      assertTrue(
        cancellation.block == (),
        Async.cancelWithCleanup(op.asInstanceOf[Pollable[Any]]).block == (),
        interpreter.read[Any]("eof").block == 3
      )
    },
    test("cancellation during poll claims a distinct replacement and joins both cleanups") {
      val child                 = new Gate[Any]()
      var operation: Async[Any] = null
      var joined: Async[Unit]   = null
      val racing                = new Async.Operation[Any] {
        def poll(onComplete: Runnable): Async[Any] = {
          joined = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Any]]); child
        }
        protected def cancelOperation(): Async[Unit] = Async.succeed(())
      }
      operation = new AsyncInterpreter(new Source(racing)).read[Any]("eof")
      operation.asInstanceOf[Pollable[Any]].poll(noop)
      assertTrue(joined.block == (), child.cancellations.get == 1)
    },
    test("lifecycle controls commit, reject overlap, observe closure, and reset") {
      val gate        = new Gate[Boolean]()
      val source      = new Source(Async.succeed(1)) { override def setLimit(n: Long): Async[Boolean] = gate }
      val interpreter = new AsyncInterpreter(source)
      val active      = interpreter.setLimit(7)
      active.asInstanceOf[Pollable[Boolean]].poll(noop)
      val overlap = interpreter.readable().either.block
      Async.cancelWithCleanup(active.asInstanceOf[Pollable[Boolean]]).block
      val ordinary = new AsyncInterpreter(new Source(Async.succeed(1)))
      assertTrue(
        overlap.left.exists(_.isInstanceOf[IllegalStateException]),
        ordinary.setLimit(7).block,
        ordinary.setRepeat().block,
        ordinary.setSkip(2).block,
        ordinary.readable().block,
        !ordinary.isClosed.block,
        ordinary.reset().block == ()
      )
    },
    test("cancelling a pending reset restores the sealed state and closes its root") {
      val resetGate = new Gate[Unit]()
      val source    = new Source(Async.succeed(1)) {
        override def reset(): Async[Unit] = { resets.incrementAndGet(); resetGate }
      }
      val interpreter = new AsyncInterpreter(source)
      val reset       = interpreter.reset()
      reset.asInstanceOf[Pollable[Unit]].poll(noop)
      val cleanup = Async.cancelWithCleanup(reset.asInstanceOf[Pollable[Unit]])
      assertTrue(cleanup.block == (), source.resets.get == 1, source.closes.get == 1, interpreter.isClosed.block)
    },
    test("non-map lifecycle mutation is refused and closed queries are short-circuited") {
      val transformed = AsyncInterpreter.fromStream(Stream(1, 2).filter(_ => true))
      val source      = new Source(Async.succeed(1))
      val closed      = new AsyncInterpreter(source)
      closed.closeForTest().block
      assertTrue(
        !transformed.setRepeat().block,
        closed.isClosed.block,
        !closed.readable().block,
        closed.setLimit(1).either.block.left.exists(_.isInstanceOf[IOException])
      )
    },
    test("reentrant close abandons a constructing control operation without deadlock") {
      var interpreter: AsyncInterpreter = null
      val source                        = new Source(Async.succeed(1)) {
        override def setLimit(n: Long): Async[Boolean] = { interpreter.closeForTest().block; Async.succeed(true) }
      }
      interpreter = new AsyncInterpreter(source)
      val result = interpreter.setLimit(1).either.block
      assertTrue(result.left.exists(_.isInstanceOf[IOException]), source.closes.get == 1, interpreter.isClosed.block)
    },
    test("reentrant close with a null close failure still settles the constructing control") {
      var interpreter: AsyncInterpreter = null
      val source                        = new Source(Async.succeed(1)) {
        override def close(): Async[Unit] = {
          closes.incrementAndGet()
          closed = true
          Async.fail(null)
        }
        override def setLimit(n: Long): Async[Boolean] = { interpreter.closeForTest().block; Async.succeed(true) }
      }
      interpreter = new AsyncInterpreter(source)
      val result = interpreter.setLimit(1).either.block
      assertTrue(result.left.exists(_.isInstanceOf[IOException]), source.closes.get == 1, interpreter.isClosed.block)
    },
    test("overlapping pulls and control-vs-read collisions leave the owner usable after cancellation") {
      val gate        = new Gate[Any]()
      val interpreter = new AsyncInterpreter(new Source(gate))
      val first       = interpreter.read[Any]("first")
      first.asInstanceOf[Pollable[Any]].poll(noop)
      val second  = interpreter.read[Any]("second").either.block
      val control = interpreter.isClosed.either.block
      Async.cancelWithCleanup(first.asInstanceOf[Pollable[Any]]).block
      assertTrue(
        second.left.exists(_.isInstanceOf[IllegalStateException]),
        control.left.exists(_.isInstanceOf[IllegalStateException]),
        !interpreter.isClosed.block
      )
    }
  )
}
