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

/**
 * The parking primitive behind `Async.slowPath.awaitSuspended`, which `block`
 * uses. Each `awaitSuspended` call obtains one parker and reuses it for every
 * poll: it calls [[reset]], polls with [[onComplete]] and, when the pollable
 * stays pending, calls [[park]].
 *
 * On the JVM the parker is its own waker and blocks with
 * `java.util.concurrent.locks.LockSupport`, and each thread keeps one parker
 * for reuse. On Scala.js [[park]] cannot wait, so it throws unless the wake
 * already happened.
 */
private[async] trait Parker {

  /** The waker handed to every poll; running it releases [[park]]. */
  def onComplete: Runnable

  /** Clears an earlier wake before the next poll. */
  def reset(): Unit

  /**
   * Waits until [[onComplete]] has run since the last [[reset]]. On the JVM it
   * blocks the thread and throws `InterruptedException` when the thread is
   * interrupted; on Scala.js it returns only when the wake already happened and
   * otherwise throws `IllegalStateException`.
   */
  def park(): Unit

  /**
   * Returns this parker for reuse. `awaitSuspended` calls it once, from its
   * `finally`, after the last `park` or poll. The default does nothing (the
   * parker is discarded); the JVM parker marks itself idle, so the parker kept
   * in a thread's slot serves that thread's next non-nested `block` without an
   * allocation.
   */
  def release(): Unit = ()
}
