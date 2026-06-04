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
import zio.blocks.async.Waker

/**
 * JVM platform helpers for the async runtime.
 *
 * The parker uses [[ReentrantLock]] + `Condition` rather than
 * `Object.synchronized` / `Object.wait` so that virtual threads (Project Loom,
 * JDK 21+) properly unmount their carrier while parked instead of pinning it.
 */
private[async] object PlatformAsync {

  def newParker(): Parker = new JvmParker

  private final class JvmParker extends Parker {
    private val lock  = new ReentrantLock()
    private val cond  = lock.newCondition()
    private var ready = false
    val waker: Waker  = new Waker {
      def wake(): Unit = {
        lock.lock()
        try {
          ready = true
          cond.signalAll()
        } finally lock.unlock()
      }
    }

    def reset(): Unit = {
      lock.lock()
      try ready = false
      finally lock.unlock()
    }

    def park(): Unit = {
      lock.lock()
      try while (!ready) cond.await()
      finally lock.unlock()
    }
  }
}
