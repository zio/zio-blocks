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

package zio.blocks.streams.io

import java.util.concurrent.CountDownLatch
import zio.blocks.async._
import zio.blocks.streams.JvmType

private[streams] final class AsyncToSyncReader[A](val source: Reader.AsyncReader[A])
    extends Reader.SyncReader[A]
    with Reader.AsyncToSyncView[A]
    with Reader.AsyncToSyncTestView {
  private val lock                                  = new AnyRef
  private val lifecycleOwner                        = new AnyRef
  @volatile private var closed                      = false
  private var busy                                  = false
  private var active: Async.Running[_]              = null
  private var consumer: Thread                      = null
  private var activeOperation: Operation            = null
  private var closeCompletion: Completer[Unit]      = null
  private var closeLeader: Thread                   = null
  private var closeRunning: Async.Running[_]        = null
  private var interruptOperation: Operation         = null
  private var interruptPending                      = false
  private var afterCloseCapture: () => Unit         = null
  private var beforeAwaitCloseInterrupt: () => Unit = null
  override def jvmType: JvmType                     = source.jvmType

  private[streams] def afterCloseCaptureForTest(hook: () => Unit): Unit = lock.synchronized {
    afterCloseCapture = hook
  }

  private[streams] def beforeAwaitCloseInterruptForTest(hook: () => Unit): Unit = lock.synchronized {
    beforeAwaitCloseInterrupt = hook
  }

  private final class Operation {
    val completion                   = new Completer[Unit]
    val cancellation                 = new Completer[Unit]
    var cancellationClaimed: Boolean = false
  }

  private def cancellation(operation: Operation, running: Async.Running[_]): Pollable[Unit] = {
    val leader = lock.synchronized {
      if (operation.cancellationClaimed) false
      else { operation.cancellationClaimed = true; true }
    }
    if (leader) {
      val cleanup = Async.cancelWithCleanup(running)
      val publish = cleanup.foldCause { cause =>
        operation.cancellation.fail(cause); ()
      } { _ => operation.cancellation.succeed(()); () }
      Async.startRegisteredOwned(publish, lifecycleOwner)(_ => ())
    }
    operation.cancellation
  }

  private def awaitCancellation(operation: Operation, running: Async.Running[_]): Throwable = {
    val cleanup     = cancellation(operation, running)
    val done        = new CountDownLatch(1)
    var interrupted = false
    cleanup.poll(new Runnable { def run(): Unit = done.countDown() }) match {
      case _: Failure     => ()
      case _: Pollable[_] =>
        var waiting = true
        while (waiting)
          try {
            done.await()
            waiting = false
          } catch { case _: InterruptedException => interrupted = true }
      case _ => ()
    }
    val failure = try { cleanup.block; null }
    catch {
      case cleanupFailure: Throwable => cleanupFailure
    }
    if (interrupted) Thread.currentThread().interrupt()
    failure
  }

  private def awaitClose(completion: Completer[Unit]): Unit = {
    val done                              = new CountDownLatch(1)
    var interrupted: InterruptedException =
      if (Thread.interrupted()) new InterruptedException else null
    var failure: Throwable = null
    completion.poll(new Runnable { def run(): Unit = done.countDown() }) match {
      case failed: Failure =>
        try Async.fail(failed.cause).block
        catch { case cause: Throwable => failure = cause }
      case _: Pollable[_] =>
        var waiting = true
        while (waiting)
          try {
            done.await()
            waiting = false
          } catch {
            case cause: InterruptedException => if (interrupted eq null) interrupted = cause
          }
        try completion.block
        catch { case cause: Throwable => failure = cause }
      case _ => ()
    }
    if (interrupted ne null) {
      if ((failure ne null) && (failure ne interrupted)) interrupted.addSuppressed(failure)
      Thread.currentThread().interrupt()
      throw interrupted
    }
    if (failure ne null) throw failure
  }

  private def awaitCloseInterrupt(operation: Operation): Unit = {
    val targeted = lock.synchronized {
      if (interruptOperation ne operation) false
      else {
        if (interruptPending && (beforeAwaitCloseInterrupt ne null)) beforeAwaitCloseInterrupt()
        while (interruptPending)
          try lock.wait()
          catch { case _: InterruptedException => () }
        interruptOperation = null
        true
      }
    }
    if (targeted) { Thread.interrupted(); () }
  }

  private def drive[B](closedValue: => B)(effect: => Async[B]): B = {
    val operation = lock.synchronized {
      while (busy && !closed) {
        try lock.wait()
        catch {
          case interrupted: InterruptedException =>
            Thread.currentThread().interrupt()
            throw interrupted
        }
      }
      if (closed) return closedValue
      busy = true
      consumer = Thread.currentThread()
      activeOperation = new Operation
      activeOperation
    }
    var running: Async.Running[B]   = null
    var settled                     = false
    var operationFailure: Throwable = null
    try {
      running = Async.startRegistered(effect)(started => lock.synchronized { active = started })
      val closeWon = lock.synchronized {
        if (closed) true else false
      }
      if (closeWon) {
        operationFailure = awaitCancellation(operation, running)
        closedValue
      } else {
        val value         = running.block
        val closeWonAfter = lock.synchronized {
          val result = closed
          active = null
          activeOperation = null
          consumer = null
          busy = false
          settled = true
          lock.notifyAll()
          result
        }
        if (closeWonAfter) closedValue else value
      }
    } catch {
      case interrupted: InterruptedException =>
        if (running ne null) {
          val cleanupFailure = awaitCancellation(operation, running)
          if (cleanupFailure ne null) {
            operationFailure = cleanupFailure
            if (!closed && (cleanupFailure ne interrupted)) interrupted.addSuppressed(cleanupFailure)
          }
        }
        if (closed) closedValue
        else {
          Thread.currentThread().interrupt()
          throw interrupted
        }
      case cause: Throwable =>
        if (lock.synchronized(closed)) closedValue else throw cause
    } finally {
      awaitCloseInterrupt(operation)
      if (!settled) lock.synchronized {
        active = null
        activeOperation = null
        consumer = null
        busy = false
        lock.notifyAll()
      }
      if (operationFailure eq null) operation.completion.succeed(()) else operation.completion.fail(operationFailure)
    }
  }
  def close(): Unit = {
    val (running, thread, operation, completion, leader, deliverInterrupt, closeReentrant) = lock.synchronized {
      if (closeCompletion eq null) {
        closeCompletion = new Completer[Unit]
        closeLeader = Thread.currentThread()
        closed = true
        val deliver = (consumer ne null) && (consumer ne Thread.currentThread())
        if (deliver) {
          interruptOperation = activeOperation
          interruptPending = true
        }
        lock.notifyAll()
        (active, consumer, activeOperation, closeCompletion, true, deliver, false)
      } else {
        val nested = (closeLeader eq Thread.currentThread()) ||
          ((closeRunning ne null) && closeRunning.isDriverThread) || Async.isExecutionOwner(lifecycleOwner)
        (active, consumer, activeOperation, closeCompletion, false, false, nested)
      }
    }
    if (leader && (afterCloseCapture ne null)) afterCloseCapture()
    if (running ne null) { cancellation(operation, running); () }
    if (deliverInterrupt)
      try thread.interrupt()
      finally
        lock.synchronized {
          interruptPending = false
          lock.notifyAll()
        }
    val reentrant =
      ((thread eq Thread.currentThread()) || ((running ne null) && running.isDriverThread) ||
        Async.isExecutionOwner(lifecycleOwner)) && (operation ne null)
    if (leader) {
      val sourceClose =
        try source.close()
        catch { case cause: Throwable => Async.fail(cause) }
        finally lock.synchronized { closeLeader = null }
      val sourceCloseRunning = Async.startRegisteredOwned(sourceClose.either, lifecycleOwner)(_ => ())
      val operationClose     = if (operation eq null) Async.succeed(()) else operation.completion.peek
      val closeEffect        = operationClose.either.flatMap { operationResult =>
        sourceCloseRunning.flatMap { sourceResult =>
          operationResult match {
            case Right(_)               => sourceResult.fold(Async.fail, _ => Async.succeed(()))
            case Left(operationFailure) =>
              sourceResult match {
                case Left(sourceFailure) =>
                  if ((sourceFailure ne operationFailure) && (sourceFailure ne null))
                    sourceFailure.addSuppressed(operationFailure)
                  Async.fail(sourceFailure)
                case Right(_) => Async.fail(operationFailure)
              }
          }
        }
      }
      val publish = closeEffect.foldCause { cause =>
        completion.fail(cause); ()
      } { _ => completion.succeed(()); () }
      Async.startRegisteredOwned(publish, lifecycleOwner)(started => lock.synchronized { closeRunning = started })
    }
    if (!reentrant && !closeReentrant) awaitClose(completion)
  }
  def isClosed: Boolean                                     = if (closed) true else drive(true)(source.isClosed)
  override def readAll[B >: A](): zio.blocks.chunk.Chunk[B] =
    drive(zio.blocks.chunk.Chunk.empty[B])(source.readAll[B]())
  override def readN[B >: A](n: Int): zio.blocks.chunk.Chunk[B] =
    drive(zio.blocks.chunk.Chunk.empty[B])(source.readN[B](n))
  override def readUpToN[B >: A](n: Int): zio.blocks.chunk.Chunk[B] =
    drive(zio.blocks.chunk.Chunk.empty[B])(source.readUpToN[B](n))
  override def readable(): Boolean                                         = drive(false)(source.readable())
  def read[B >: A](sentinel: B): B                                         = drive(sentinel)(source.read(sentinel))
  override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int =
    drive(sentinel)(source.readBooleanPhysical(sentinel))
  override def readByte(): Int                                       = drive(-1)(source.readBytePhysical())
  override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int =
    drive(sentinel)(source.readCharPhysical(sentinel))
  override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int =
    drive(sentinel)(source.readShortPhysical(sentinel))
  override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long   = drive(sentinel)(source.readIntPhysical(sentinel))
  override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
    drive(sentinel)(source.readLongPhysical(sentinel))
  override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
    drive(sentinel)(source.readFloatPhysical(sentinel))
  override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
    drive(sentinel)(source.readDoublePhysical(sentinel))
  override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Int = {
    Reader.validateArrayRange(dest, offset, length)
    drive(if (length == 0) 0 else -1)(source.readBytesPhysical(dest, offset, length))
  }
  override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Int = {
    Reader.validateArrayRange(dest, offset, length)
    drive(if (length == 0) 0 else -1)(source.readIntsPhysical(dest, offset, length))
  }
  private[streams] def readIntsFullyOutcome(dest: Array[Int], offset: Int, length: Int)(implicit
    ev: A <:< Int
  ): (Either[Throwable, Int], Int) = {
    var filled                      = 0
    def loop(used: Int): Async[Int] = {
      filled = used
      if (used >= length) Async.succeed(used)
      else
        source.readIntsPhysical(dest, offset + used, length - used).flatMap { n =>
          if (n > 0) loop(used + n)
          else Async.succeed(if (used == 0) n else used)
        }
    }
    (drive[Either[Throwable, Int]](Right(if (length == 0) 0 else -1))(loop(0).either), filled)
  }
  override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int = {
    Reader.validateArrayRange(dest, offset, length)
    drive(if (length == 0) 0 else -1)(source.readLongsPhysical(dest, offset, length))
  }
  override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Int = {
    Reader.validateArrayRange(dest, offset, length)
    drive(if (length == 0) 0 else -1)(source.readFloatsPhysical(dest, offset, length))
  }
  override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int = {
    Reader.validateArrayRange(dest, offset, length)
    drive(if (length == 0) 0 else -1)(source.readDoublesPhysical(dest, offset, length))
  }
  private def closedControl[B]: B         = throw new java.io.IOException("Reader is closed")
  override def reset(): Unit              = drive(closedControl)(source.reset())
  override def setLimit(n: Long): Boolean = drive(closedControl)(source.setLimit(n))
  override def setRepeat(): Boolean       = drive(closedControl)(source.setRepeat())
  override def setSkip(n: Long): Boolean  = drive(closedControl)(source.setSkip(n))
  override def skip(n: Long): Unit        = drive(closedControl)(source.skip(n))
}
