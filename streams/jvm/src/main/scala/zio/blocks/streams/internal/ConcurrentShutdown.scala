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

package zio.blocks.streams.internal

import zio.blocks.streams.io.Reader

import java.util.concurrent.atomic.AtomicBoolean

private[internal] object ConcurrentShutdown {
  final class CloseOnce(reader: Reader.SyncReader[?]) {
    private val closed = new AtomicBoolean(false)

    def close(recordFailure: Throwable => Unit): Unit =
      if (closed.compareAndSet(false, true))
        try reader.close()
        catch { case cause: Throwable => recordFailure(cause) }
  }

  /**
   * Joins to actual termination. The old five-second timeout remains the
   * polling interval, but neither timeout nor caller interruption abandons
   * cleanup. Returns the first interruption so the caller's interrupt status
   * can be restored after shutdown.
   */
  def join(thread: Thread, interrupted: InterruptedException): InterruptedException = {
    var result = interrupted
    if (Thread.interrupted() && (result eq null)) result = new InterruptedException
    if ((thread ne null) && (thread ne Thread.currentThread())) {
      while (thread.isAlive) {
        try thread.join(5000L)
        catch {
          case cause: InterruptedException =>
            if (result eq null) result = cause
        }
      }
    }
    result
  }

  def replay(error: Throwable, interrupted: InterruptedException): Unit = {
    if (interrupted ne null) Thread.currentThread().interrupt()
    if (error ne null) {
      if ((interrupted ne null) && (error ne interrupted)) error.addSuppressed(interrupted)
      throw error
    }
  }
}
