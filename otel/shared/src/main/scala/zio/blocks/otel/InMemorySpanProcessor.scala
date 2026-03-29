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

package zio.blocks.otel

import java.util.concurrent.atomic.AtomicInteger

private[otel] class InMemorySpanProcessor(capacity: Int = 1024) extends SpanProcessor {
  private val buffer: Array[SpanData]   = new Array[SpanData](capacity)
  private val writeIndex: AtomicInteger = new AtomicInteger(0)
  private val count: AtomicInteger      = new AtomicInteger(0)

  override def onStart(span: Span): Unit = ()

  override def onEnd(spanData: SpanData): Unit = {
    val idx = math.abs(writeIndex.getAndIncrement() % capacity)
    buffer(idx) = spanData
    var c = count.get()
    while (c < capacity) {
      if (count.compareAndSet(c, c + 1)) {
        c = capacity
      } else {
        c = count.get()
      }
    }
  }

  override def shutdown(): Unit = ()

  override def forceFlush(): Unit = ()

  def collectedSpans: List[SpanData] = {
    val n      = math.min(count.get(), capacity)
    val w      = writeIndex.get()
    val result = new Array[SpanData](n)
    var i      = 0
    while (i < n) {
      val idx = ((w - n + i) % capacity + capacity) % capacity
      result(i) = buffer(idx)
      i += 1
    }
    result.toList.filter(_ != null)
  }

  def clear(): Unit = {
    var i = 0
    while (i < capacity) { buffer(i) = null; i += 1 }
    count.set(0)
    writeIndex.set(0)
  }
}
