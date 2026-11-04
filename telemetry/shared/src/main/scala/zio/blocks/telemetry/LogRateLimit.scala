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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private[telemetry] object LogRateLimit {
  private val counters   = new ConcurrentHashMap[Int, AtomicLong]()
  private val timestamps = new ConcurrentHashMap[Int, AtomicLong]()

  private def getOrCreate(map: ConcurrentHashMap[Int, AtomicLong], siteId: Int): AtomicLong = {
    var ref = map.get(siteId)
    if (ref == null) {
      ref = new AtomicLong(0L)
      val existing = map.putIfAbsent(siteId, ref)
      if (existing != null) ref = existing
    }
    ref
  }

  def shouldLogEvery(siteId: Int, every: Int): Boolean = {
    if (every <= 0) return false
    getOrCreate(counters, siteId).incrementAndGet() % every == 0
  }

  def shouldLogAtMost(siteId: Int, intervalMillis: Long): Boolean = {
    val ts   = getOrCreate(timestamps, siteId)
    val now  = System.currentTimeMillis()
    val last = ts.get()
    if (now - last >= intervalMillis) {
      ts.compareAndSet(last, now)
    } else false
  }
}
