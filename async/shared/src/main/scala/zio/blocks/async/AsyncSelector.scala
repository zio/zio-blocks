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

package zio.blocks.async

import scala.collection.mutable.ListBuffer

/**
 * A repeatable, round-robin selector for independently pending computations.
 *
 * Eligibility means that a slot is armed (initially, or by [[replace]]) and its
 * computation has completed. Among eligible slots selection scans cyclically
 * from the slot after the previous winner. Consequently a continuously eligible
 * slot wins within at most `armedCount` successful selections.
 *
 * The selector registers one stable waker per armed slot, rather than a new
 * loser waker per call to [[select]]. A winning slot is disarmed atomically and
 * cannot win twice. [[shutdown]] is idempotent and does not finish until
 * cleanup for every still-armed loser has finished.
 */
final class AsyncSelector[A] private (slotCount: Int, initial: IndexedSeq[(Int, Async[A])]) extends Cancelable {
  private final class Slot(val index: Int) {
    val wake: Runnable = new Runnable {
      def run(): Unit = {
        AsyncSelector.this.synchronized(wakeEpoch += 1L)
        scheduleWakeSelectors()
      }
    }
    var wakeEpoch: Long                                 = 0L
    var running: Pollable[A]                            = null
    var reservedBy: Selection[?]                        = null
    var claiming                                        = false
    var ready: Async[A]                                 = null
    var readySet                                        = false
    var shutdownHandoff: Completer[Option[Pollable[A]]] = null
  }

  private val slots                    = Array.tabulate(slotCount)(new Slot(_))
  private var cursor                   = 0
  private var closed                   = false
  private var waiters                  = List.empty[Runnable]
  private var closeEffect: Async[Unit] = null

  initial.foreach { case (index, value) => install(index, value) }

  /** Number of slots, including currently disarmed slots. */
  def size: Int = slots.length

  /** Arm a disarmed slot with its next computation. */
  def replace(index: Int, value: => Async[A]): Unit = {
    checkIndex(index)
    val wake = synchronized {
      if (closed) throw new IllegalStateException("selector is closed")
      else if (slots(index).running ne null)
        throw new IllegalStateException(s"selector slot $index is already armed")
      else {
        slots(index).running = start(() => value)
        waiters.nonEmpty
      }
    }
    if (wake) scheduleWakeSelectors()
  }

  /** Arm a disarmed slot with an already constructed internal computation. */
  private[blocks] def replaceKnown(index: Int, value: Async[A]): Unit = {
    checkIndex(index)
    val wake = synchronized {
      if (closed) throw new IllegalStateException("selector is closed")
      else if (slots(index).running ne null)
        throw new IllegalStateException(s"selector slot $index is already armed")
      else {
        slots(index).running = startKnown(value)
        waiters.nonEmpty
      }
    }
    if (wake) scheduleWakeSelectors()
  }

  /** Wait for and remove the next winner, returning its slot and value. */
  def select: Async[(Int, A)] = new Selection[(Int, A)](() => true, null, 1)

  /**
   * Internal transactional selection. A ready winner is removed only when
   * `claim` atomically accepts its handoff.
   */
  private[blocks] def selectClaim(claim: () => Boolean): Async[(Int, A)] =
    new Selection[(Int, A)](claim, null, 1)

  /**
   * Transactional selection that also performs one eager, non-registering
   * probe. Internal consumers that are already running can avoid suspending
   * when a slot is immediately ready without making public [[select]] eager.
   */
  private[blocks] def selectClaimReady(claim: () => Boolean): Async[(Int, A)] = {
    val selection = new Selection[(Int, A)](claim, null, 1)
    selection.pollInitial()
  }

  /**
   * Consume up to `maxReady` immediately-ready winners through one selector
   * reservation object. The handler must rearm any slot that should remain
   * eligible; pending slots stay owned by the selector and are never detached.
   */
  private[blocks] def selectClaimReadyRepeat(
    claim: () => Boolean,
    maxReady: Int,
    handler: AsyncSelector.ReadyHandler[A]
  ): Async[Unit] = {
    require(maxReady > 0, "maxReady must be positive")
    val selection = new Selection[Unit](claim, handler, maxReady)
    selection.pollInitial()
  }

  /** Cancel all armed inputs and join all cleanup. Idempotent. */
  def shutdown: Async[Unit] = {
    val claimed: (Completer[Unit], List[Async[Unit]], List[Runnable]) = synchronized {
      if (closeEffect.asInstanceOf[AnyRef] ne null) return closeEffect
      closed = true
      val completion = new Completer[Unit]
      closeEffect = completion
      val active = slots.iterator.flatMap { slot =>
        val run = slot.running
        if (slot.claiming) {
          val handoff = new Completer[Option[Pollable[A]]]
          slot.shutdownHandoff = handoff
          Some(handoff.flatMap {
            case Some(child) => cancelCleanup(child)
            case None        => Async.succeed(())
          })
        } else if (slot.reservedBy ne null) {
          val handoff = new Completer[Option[Pollable[A]]]
          slot.shutdownHandoff = handoff
          Some(
            joinCleanup(
              List(
                cancelCleanup(run),
                handoff.flatMap {
                  case Some(child) => cancelCleanup(child)
                  case None        => Async.succeed(())
                }
              )
            )
          )
        } else {
          slot.running = null
          slot.reservedBy = null
          slot.ready = null
          slot.readySet = false
          Option(run).map(cancelCleanup)
        }
      }.toList
      val pending = waiters
      waiters = Nil
      (completion, active, pending)
    }
    wake(claimed._3)
    // The selector publishes this driver's result into a globally shared close
    // completion. It therefore must observe the cleanup batch from outside that
    // batch: a driver inheriting the same batch would treat its BatchJoin as a
    // self-cycle and could publish close completion before late cleanup finished.
    Async.withCancellationBatch(null) {
      Async.startRegistered(
        joinCleanup(claimed._2).foldCause { (cause: Throwable) =>
          claimed._1.fail(cause); ()
        } { (_: Unit) =>
          claimed._1.succeed(()); ()
        }
      )(_ => ())
    }
    claimed._1
  }

  override def cancel(): Unit = { shutdown.start; () }

  private final class DeferredInput(thunk: () => Async[A]) extends Pollable[A] {
    def poll(onComplete: Runnable): Async[A] = {
      val _      = onComplete
      val result =
        try {
          val value = thunk()
          if (value.asInstanceOf[AnyRef] eq null)
            Async.fail(new NullPointerException("selector input returned null Async"))
          else value
        } catch { case cause: Throwable => Async.fail(cause) }
      wakeSelectors()
      result
    }
  }

  private def install(index: Int, value: Async[A]): Unit =
    synchronized {
      slots(index).running = startKnown(value)
    }

  private def start(value: () => Async[A]): Pollable[A] =
    new DeferredInput(value)

  private def startKnown(value: Async[A]): Pollable[A] =
    if (value.isInstanceOf[Pollable[?]]) {
      value.asInstanceOf[Pollable[A]]
    } else {
      new Pollable[A] {
        def poll(onComplete: Runnable): Async[A] = value
      }
    }

  private final class Selection[B](
    claim: () => Boolean,
    handler: AsyncSelector.ReadyHandler[A],
    private var remaining: Int
  ) extends Pollable[B] {
    private final class RegisteredWaiter(val target: Runnable) extends Runnable {
      def run(): Unit = {
        val active = AsyncSelector.this.synchronized {
          !done && registered.exists(_ eq this)
        }
        if (active) target.run()
      }
    }

    private var done       = false
    private var registered = List.empty[RegisteredWaiter]

    private def register(onComplete: Runnable): Unit =
      if (!registered.exists(_.target eq onComplete)) {
        val waiter = new RegisteredWaiter(onComplete)
        registered = waiter :: registered
        waiters = waiter :: waiters
      }

    def poll(onComplete: Runnable): Async[B] = pollFrom(onComplete, 0)

    def pollInitial(): Async[B] = pollFrom(null, 0)

    private def pollFrom(onComplete: Runnable, startOffset: Int): Async[B] = {
      val reservation = AsyncSelector.this.synchronized {
        if (done) Left(new IllegalStateException("selection was already consumed"))
        else if (closed) Left(new IllegalStateException("selector is closed"))
        else {
          var offset = startOffset
          var winner = -1
          while (offset < slots.length && winner < 0) {
            val i    = (cursor + offset) % slots.length
            val slot = slots(i)
            if ((slot.running ne null) && (slot.reservedBy eq null)) {
              slot.reservedBy = this
              winner = i
            }
            offset += 1
          }
          if (winner >= 0) {
            val slot = slots(winner)
            Right((winner, slot.running, slot.ready, slot.readySet, slot.wakeEpoch, offset))
          } else {
            if (onComplete ne null) register(onComplete)
            return this
          }
        }
      }
      reservation match {
        case Left(cause)                                                   => Async.fail(cause)
        case Right((index, run, cached, cachedSet, wakeEpoch, nextOffset)) =>
          val observed =
            if (cachedSet) cached
            else
              try run.poll(slots(index).wake)
              catch { case cause: Throwable => Async.fail(cause) }
          val terminal = observed.isInstanceOf[Failure] || !observed.isInstanceOf[Pollable[?]]
          if (!terminal) {
            val replacement = observed.asInstanceOf[Pollable[A]]
            val replayWake  = AsyncSelector.this.synchronized {
              val slot = slots(index)
              if ((slot.reservedBy eq this) && (slot.running eq run)) {
                slot.reservedBy = null
                if (closed) {
                  slot.running = null
                  slot.ready = null
                  slot.readySet = false
                  val handoff = slot.shutdownHandoff
                  if (handoff ne null)
                    handoff.succeed(
                      if (Async.replacementNeedsCancellation(run, replacement)) Some(replacement) else None
                    )
                } else {
                  slot.running = replacement
                  if (!done && (onComplete ne null)) register(onComplete)
                }
              } else if (!closed && !done && (onComplete ne null)) register(onComplete)
              !closed && !done && slot.wakeEpoch != wakeEpoch
            }
            // A real slot wake may run before this selection has registered
            // its outer waiter. Replay that observed wake after registration;
            // otherwise a same-identity suspension can lose its only signal.
            if (replayWake) scheduleWakeSelectors()
            if (closed) Async.fail(new IllegalStateException("selector is closed"))
            else if (replacement ne run) pollFrom(onComplete, 0)
            else if (nextOffset < slots.length) pollFrom(onComplete, nextOffset)
            else this
          } else {
            val mayClaim = AsyncSelector.this.synchronized {
              val slot = slots(index)
              if (done) {
                if ((slot.reservedBy eq this) && (slot.running eq run)) {
                  slot.ready = observed
                  slot.readySet = true
                  slot.reservedBy = null
                }
                false
              } else if (closed || !(slot.reservedBy eq this) || (slot.running ne run)) {
                if (closed && (slot.reservedBy eq this) && (slot.running eq run)) {
                  slot.running = null
                  slot.ready = null
                  slot.readySet = false
                  slot.reservedBy = null
                  val handoff = slot.shutdownHandoff
                  if (handoff ne null) handoff.succeed(None)
                }
                false
              } else {
                slot.ready = observed
                slot.readySet = true
                slot.claiming = true
                true
              }
            }
            if (!mayClaim) Async.fail(new IllegalStateException("selector is closed"))
            else {
              val accepted =
                try Right(claim())
                catch { case cause: Throwable => Left(cause) }
              val result = AsyncSelector.this.synchronized {
                val slot = slots(index)
                slot.claiming = false
                val handoff = slot.shutdownHandoff
                if (accepted == Right(true)) {
                  slot.running = null
                  slot.ready = null
                  slot.readySet = false
                  slot.reservedBy = null
                  // A winner index can only come from the slot scan above, so
                  // the array is necessarily non-empty here.
                  cursor = (index + 1) % slots.length
                  if (handler eq null) {
                    done = true
                    unregister()
                  }
                  if (handoff ne null) handoff.succeed(None)
                  Right(observed)
                } else {
                  slot.reservedBy = null
                  if (closed) {
                    slot.running = null
                    slot.ready = null
                    slot.readySet = false
                    if (handoff ne null) handoff.succeed(Some(run))
                  } else
                    accepted match {
                      case Left(_) =>
                        done = true
                        unregister()
                      case _ if onComplete ne null => register(onComplete)
                      case _                       => ()
                    }
                  accepted match {
                    case Left(cause) => Left(cause)
                    case _ if closed => Left(new IllegalStateException("selector is closed"))
                    case _           => return this
                  }
                }
              }
              result match {
                case Left(cause)  => Async.fail(cause)
                case Right(value) =>
                  if (handler eq null) value.map(a => (index, a)).asInstanceOf[Async[B]]
                  else if (Async.stepKind(value) != 0) {
                    finishSelection()
                    value.asInstanceOf[Async[B]]
                  } else {
                    val continue =
                      try Right(handler(index, Async.stepValue(value)))
                      catch { case cause: Throwable => Left(cause) }
                    continue match {
                      case Left(cause) =>
                        finishSelection()
                        Async.fail(cause)
                      case Right(true) if remaining > 1 =>
                        remaining -= 1
                        pollFrom(onComplete, 0)
                      case _ =>
                        finishSelection()
                        Async.succeed(()).asInstanceOf[Async[B]]
                    }
                  }
              }
            }
          }
      }
    }

    private def finishSelection(): Unit = AsyncSelector.this.synchronized {
      done = true
      unregister()
    }

    private def unregister(): Unit =
      if (registered.nonEmpty) {
        waiters = waiters.filterNot(waiter => registered.exists(_ eq waiter))
        registered = Nil
      }

    override def cancel(): Unit = AsyncSelector.this.synchronized {
      if (!done) {
        done = true
        unregister()
      }
    }
  }

  private def wakeSelectors(): Unit = {
    // Keep each waiter registered until its Selection either consumes a winner
    // or is cancelled. A wake is only a hint to poll again: another Selection
    // may claim the completed slot first, in which case removing the losing
    // waiter here would strand it permanently with all remaining slots pending.
    val pending = synchronized(waiters)
    wake(pending)
  }

  private def scheduleWakeSelectors(): Unit =
    Async.schedule(new Runnable { def run(): Unit = wakeSelectors() }, forceMacrotask = false)

  private def wake(pending: List[Runnable]): Unit = pending.foreach { waiter =>
    try waiter.run()
    catch { case _: Throwable => () }
  }

  private def checkIndex(index: Int): Unit =
    if (index < 0 || index >= slots.length) throw new IndexOutOfBoundsException(index.toString)

  private def cancelCleanup(child: Pollable[A]): Async[Unit] =
    // The selector owns and joins each armed child itself. Giving a child the
    // caller's ambient cancellation batch would let that child depend on the
    // selector shutdown which is waiting for it, creating a self-cycle whose
    // BatchJoin is allowed to return context-relative success too early.
    Async.withCancellationBatch(null) {
      try Async.cancelWithCleanup(child)
      catch { case cause: Throwable => Async.fail(cause) }
    }

  private def joinCleanup(active: List[Async[Unit]]): Async[Unit] = {
    val running = new ListBuffer[Async.Running[Unit]]
    active.foreach(cleanup => running += cleanup.start)
    def loop(rest: List[Async.Running[Unit]], failed: Boolean, failure: Throwable): Async[Unit] = rest match {
      case Nil          => if (failed) Async.fail(failure) else Async.succeed(())
      case head :: tail =>
        head.either.flatMap {
          case Right(_)    => loop(tail, failed, failure)
          case Left(cause) =>
            if (failed && (failure ne null) && (cause ne null) && (failure ne cause)) failure.addSuppressed(cause)
            loop(tail, failed = true, if (failed) failure else cause)
        }
    }
    loop(running.toList, failed = false, null)
  }
}

object AsyncSelector {
  private[blocks] trait ReadyHandler[-A] {
    def apply(index: Int, value: A): Boolean
  }

  private[async] def apply[A](inputs: IndexedSeq[Async[A]]): AsyncSelector[A] =
    new AsyncSelector(inputs.length, inputs.indices.map(index => (index, inputs(index))))

  private[async] def sparse[A](size: Int, inputs: IndexedSeq[(Int, Async[A])]): AsyncSelector[A] = {
    require(size > 0, "selector size must be positive")
    inputs.foreach { case (index, _) =>
      if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index.toString)
    }
    if (inputs.map(_._1).distinct.length != inputs.length)
      throw new IllegalArgumentException("selector initial slot indices must be distinct")
    new AsyncSelector(size, inputs)
  }
}
