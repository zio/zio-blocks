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

/**
 * A bounded single-producer/single-consumer queue for raw 64-bit values.
 *
 * Empty and done are represented out of band, so every `Long` value and every
 * raw `Double` bit pattern can be transported without allocation or collision.
 */
private[streams] final class FullDomainSpscRingBuffer(capacity: Int) {
  import FullDomainSpscRingBuffer._

  require(capacity > 0, s"FullDomainSpscRingBuffer requires capacity > 0, got $capacity")

  private val data = new Array[Long](capacity)

  @volatile private var consumerIndex = 0L
  @volatile private var producerDone  = false
  @volatile private var producerIndex = 0L
  private var doneSeen                = false

  def offer(value: Long): Boolean = {
    val write = producerIndex
    if (write - consumerIndex >= capacity.toLong) false
    else {
      data((write % capacity).toInt) = value
      producerIndex = write + 1L
      true
    }
  }

  def offerDone(): Boolean = {
    producerDone = true
    true
  }

  def poll(dest: Array[Long], offset: Int): Int = {
    val read = consumerIndex
    // Read the completion flag before the producer index. When completion is
    // observed, its volatile read acquires every preceding producer-index
    // update, so DONE can never overtake the producer's final data offer.
    val done  = producerDone
    val write = producerIndex
    if (read < write) {
      dest(offset) = data((read % capacity).toInt)
      consumerIndex = read + 1L
      Data
    } else if (done && !doneSeen) {
      doneSeen = true
      Done
    } else Empty
  }
}

private[internal] object FullDomainSpscRingBuffer {
  final val Empty: Int = 0
  final val Data: Int  = 1
  final val Done: Int  = 2
}
