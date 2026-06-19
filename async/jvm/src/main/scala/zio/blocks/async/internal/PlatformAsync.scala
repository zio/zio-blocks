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
 * and, unlike a `ReentrantLock` `Condition`, `LockSupport` allocates no
 * AQS wait-queue node per park, which is the bulk of the per-async-boundary
 * garbage on the genuinely-suspended (off-thread completion) path.
 */
private[async] object PlatformAsync {

  def newParker(): Parker = new JvmParker

  // One object that IS the parker AND its own waker (`onComplete = this`). The
  // waker just flips a flag and unparks the owner; no lock, no condition, no
  // per-park allocation.
  private final class JvmParker extends Parker with Runnable {
    // The awaiting thread. `newParker()` is called from inside `awaitSuspended`
    // on the very thread that will `park()`, so capturing it here is correct.
    private val owner           = Thread.currentThread()
    @volatile private var ready = false

    def onComplete: Runnable = this

    def run(): Unit = {
      ready = true
      LockSupport.unpark(owner)
    }

    def reset(): Unit = ready = false

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
