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

package zio.blocks.async.internal

import java.util.concurrent.atomic.AtomicInteger
import zio.blocks.async._

/**
 * Joins cleanup discovered after cancellation raced an in-flight poll. The
 * cleanup protocol is driven once independently of its observers, all of which
 * join and replay the same completion.
 */
private[async] final class CancellationCleanup {
  private final class Supplied(val value: Async[Unit])

  private val result                   = new Completer[Unit]
  private var handoff: Supplied        = null
  private var observed                 = false
  private var primaryCleanup: Supplied = null
  private var started                  = false

  val effect: Async[Unit] = new Pollable[Unit] {
    def poll(onComplete: Runnable): Async[Unit] = {
      val cleanups = CancellationCleanup.this.synchronized {
        observed = true
        claimCleanups()
      }
      if (cleanups ne null) driveCleanups(cleanups._1, cleanups._2)
      val observedResult = result.poll(onComplete)
      if (AsyncEncoding.isSuspended(observedResult)) this else observedResult
    }

    override private[async] def cancelWithCleanup(): Async[Unit] = this
  }

  def primary(value: Async[Unit]): Unit = {
    val cleanups = synchronized {
      if (primaryCleanup eq null) primaryCleanup = new Supplied(value)
      claimCleanups()
    }
    if (cleanups ne null) driveCleanups(cleanups._1, cleanups._2)
  }

  /**
   * Obtains the replacement cleanup synchronously, which preserves signalling.
   */
  def replacement(value: Pollable[?]): Unit =
    supplyHandoff(
      try value.cancelWithCleanup()
      catch { case t: Throwable => Async.fail(t) }
    )

  /**
   * Joins cleanup which has already been claimed and therefore must not be
   * cancellation-signalled.
   */
  def claimedReplacement(value: Async[Unit]): Unit = supplyHandoff(value)

  def noReplacement(): Unit = supplyHandoff(Async.succeed(()))

  private def claimCleanups(): (Async[Unit], Async[Unit]) =
    if (observed && !started && (primaryCleanup ne null) && (handoff ne null)) {
      started = true
      (primaryCleanup.value, handoff.value)
    } else null

  private def driveCleanups(primary: Async[Unit], next: Async[Unit]): Unit =
    driveUntilSuspended(primary) { first =>
      driveUntilSuspended(next) { second =>
        (first, second) match {
          case (Right(_), Right(_))         => result.succeed(())
          case (Left(cause), Right(_))      => result.fail(cause)
          case (Right(_), Left(cause))      => result.fail(cause)
          case (Left(primary), Left(cause)) =>
            if ((primary ne null) && (cause ne null) && (primary ne cause)) primary.addSuppressed(cause)
            result.fail(primary)
        }
      }
    }

  private def driveUntilSuspended(start: Async[Unit])(complete: Either[Throwable, Unit] => Unit): Unit = {
    var current  = start
    var continue = true
    while (continue) {
      Async.foldStep(current)(new Async.StepFold[Unit, Unit] {
        def success(value: Unit): Unit = {
          continue = false
          complete(Right(value))
        }
        def failure(cause: Throwable): Unit = {
          continue = false
          complete(Left(cause))
        }
        def pending(value: Pollable[Unit]): Unit = {
          // 0 = poll in progress, 1 = same pending value armed, 2 = signalled.
          // A synchronous signal is consumed by this loop; an asynchronous
          // signal launches the internal driver exactly once. This avoids both
          // re-polling a one-shot value before its callback and routing the
          // callback through another continuation/completer chain.
          val state             = new AtomicInteger(0)
          val executionOwner    = Async.currentExecutionOwner
          val cancellationBatch = Async.currentCancellationBatch
          val wake              = new Runnable {
            def run(): Unit = {
              val previous = state.getAndSet(2)
              if (previous == 1)
                Async.withExecutionOwner(executionOwner) {
                  Async.withCancellationBatch(cancellationBatch) {
                    driveUntilSuspended(value)(complete)
                  }
                }
            }
          }
          val next =
            try value.poll(wake)
            catch { case cause: Throwable => Async.fail(cause) }
          if (next.asInstanceOf[AnyRef] eq value.asInstanceOf[AnyRef]) {
            if (state.compareAndSet(0, 1)) continue = false
            else current = value
          } else current = next
        }
      })
    }
  }

  private def supplyHandoff(value: Async[Unit]): Unit = {
    val cleanups = synchronized {
      if (handoff eq null) handoff = new Supplied(value)
      claimCleanups()
    }
    if (cleanups ne null) driveCleanups(cleanups._1, cleanups._2)
  }
}
