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

import java.util.concurrent.locks.ReentrantLock

/**
 * JVM platform helpers for the async runtime.
 *
 * The parker uses [[ReentrantLock]] + `Condition` rather than
 * `Object.synchronized` / `Object.wait` so that virtual threads (Project Loom,
 * JDK 21+) properly unmount their carrier while parked instead of pinning it.
 */
private[async] object PlatformAsync {

  def newParker(): Parker = new JvmParker

  // Extends `ReentrantLock` and IS its own waker (`onComplete = this`), so a
  // single `JvmParker` object carries the lock, condition, parker, and Runnable
  // — one allocation per `await` instead of three (the standalone lock and the
  // anonymous Runnable are gone). `JvmParker` is `private final` inside a
  // `private[async]` object, so inheriting `ReentrantLock`'s public methods
  // leaks nothing user-facing.
  private final class JvmParker extends ReentrantLock with Parker with Runnable {
    private val cond = newCondition()
    // `@volatile` so `reset` and the `park` fast path can read/write it without
    // the lock. The lock is still taken for the genuine wait (`cond.await`) and
    // its matching wake (`signalAll`), which is what makes the wait
    // interruptible (cancellation) and lost-wakeup-free.
    @volatile private var ready = false

    def onComplete: Runnable = this

    def run(): Unit = {
      lock()
      try {
        ready = true
        cond.signalAll()
      } finally unlock()
    }

    // Lock-free by design — do NOT re-add the lock. The driver calls
    // `reset(); poll(onComplete); if (pending) park()` each round, so `reset`
    // is program-ordered before this round's waker is even registered (inside
    // `poll`); it can never race a live wakeup for the current round. A stale
    // wakeup from a prior round only causes a spurious `park` return, which the
    // driver's re-poll absorbs.
    def reset(): Unit = ready = false

    def park(): Unit = {
      // Fast path: on the common synchronous-wakeup path `onComplete` fired
      // inside `poll`, so we never actually block — skip the lock entirely. The
      // value handoff happens through the leaf's own synchronization on re-poll,
      // not through `ready`, so a plain volatile read is sufficient here.
      if (ready) return
      lock()
      try while (!ready) cond.await()
      finally unlock()
    }
  }
}
