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

import java.util.concurrent.locks.LockSupport

/**
 * JVM platform helpers for the async runtime.
 *
 * The parker uses [[LockSupport]] `park`/`unpark` rather than
 * `Object.synchronized` / `Object.wait` so that virtual threads (Project Loom,
 * JDK 21+) properly unmount their carrier while parked instead of pinning it —
 * and, unlike a `ReentrantLock` `Condition`, `LockSupport` allocates no AQS
 * wait-queue node per park, which is the bulk of the per-async-boundary garbage
 * on the genuinely-suspended (off-thread completion) path.
 */
private[async] object PlatformAsync {

  // One parker is cached per awaiting thread and reused across `await` calls,
  // so a steady stream of (non-nested) `.block` calls on the same thread
  // allocates no parker at all — only the very first await on a thread does.
  // The slot holds a parker that is either idle (reusable) or `null`.
  private val pooled = new ThreadLocal[JvmParker]

  // Acquire a parker for the current thread:
  //   - reuse the thread-local one if it is idle (the common case: a `.block`
  //     always parks on its own thread, one at a time);
  //   - otherwise (first await on this thread, OR a NESTED await whose outer
  //     frame still owns the pooled parker — e.g. a `flatMap` continuation that
  //     itself calls `.block`) allocate a fresh, un-pooled parker.
  // `owner` is re-captured here because a pooled parker created on this thread
  // is only ever handed back out on this same thread.
  def newParker(): Parker = {
    val p = pooled.get()
    if ((p ne null) && !p.busy) {
      p.busy = true
      p
    } else {
      val fresh = new JvmParker
      fresh.busy = true
      if (p eq null) pooled.set(fresh) // pool only the FIRST parker per thread
      fresh
    }
  }

  // One object that IS the parker AND its own waker (`onComplete = this`). The
  // waker just flips a flag and unparks the owner; no lock, no condition, no
  // per-park allocation.
  private final class JvmParker extends Parker with Runnable {
    // The awaiting thread. A parker (pooled or fresh) is always created AND
    // parked on the same thread, so capturing the current thread here is
    // correct for the life of this parker.
    private val owner           = Thread.currentThread()
    @volatile private var ready = false

    // True while this parker is in use by an `await` frame. Guards the
    // thread-local slot against re-entrant (nested) awaits reusing it mid-park.
    var busy = false

    def onComplete: Runnable = this

    def run(): Unit = {
      ready = true
      LockSupport.unpark(owner)
    }

    def reset(): Unit = ready = false

    // Return to the pool: only the thread's first (pooled) parker is retained;
    // fresh parkers allocated for nested awaits are simply dropped.
    override def release(): Unit = busy = false

    def park(): Unit =
      // `while (!ready)` + unpark-permit semantics are lost-wakeup-free: a
      // synchronous wakeup sets `ready` so we never block (the old fast path); a
      // leftover permit from a prior round causes at most one spurious return the
      // loop absorbs.
      while (!ready) {
        LockSupport.park()
        // A worker cancel interrupts the parked thread (AsyncRunner.cancel ->
        // worker.interrupt()); `LockSupport.park` returns with the interrupt flag
        // set, so re-throw `InterruptedException` exactly as the old `cond.await`
        // did, for `drive` to unwind into a suppressed/cancelled outcome. Checked
        // before `ready` so a cancel racing a completion still surfaces as
        // interruption.
        if (Thread.interrupted()) throw new InterruptedException()
      }
  }
}
