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

package zio.blocks.telemetry

import java.util.concurrent.atomic.AtomicLongArray

private[telemetry] object LogRateLimit {
  private val SIZE       = 4096
  private val MASK       = SIZE - 1
  private val counters   = new AtomicLongArray(SIZE)
  private val timestamps = new AtomicLongArray(SIZE)

  def shouldLogEvery(siteId: Int, every: Int): Boolean = {
    if (every <= 0) return false
    val idx = siteId & MASK
    counters.incrementAndGet(idx) % every == 0
  }

  def shouldLogAtMost(siteId: Int, intervalMillis: Long): Boolean = {
    val idx  = siteId & MASK
    val now  = System.currentTimeMillis()
    val last = timestamps.get(idx)
    if (now - last >= intervalMillis) {
      timestamps.compareAndSet(idx, last, now)
      true
    } else false
  }
}
