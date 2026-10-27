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
 * Packs a `SyncInterpreter` stage position into one `Long`: four 15-bit indices
 * (`stageStart`, `incomingLen`, `stageEnd` and `outgoingLen`, from the high
 * bits down) and a 4-bit output lane in the low bits.
 */
object StreamState {
  private final val IDX_BITS  = 15
  private final val IDX_MASK  = (1 << IDX_BITS) - 1
  private final val LANE_BITS = 4
  private final val LANE_MASK = (1 << LANE_BITS) - 1

  /** Packs the four indices and the output lane. */
  def apply(
    stageStart: Int,
    incomingLen: Int,
    stageEnd: Int,
    outgoingLen: Int,
    outputLane: Int = 0
  ): StreamState =
    (stageStart.toLong << (3 * IDX_BITS + LANE_BITS)) |
      (incomingLen.toLong << (2 * IDX_BITS + LANE_BITS)) |
      (stageEnd.toLong << (1 * IDX_BITS + LANE_BITS)) |
      (outgoingLen.toLong << LANE_BITS) |
      outputLane.toLong

  /** Unpacks the `stageStart` index. */
  def stageStart(s: StreamState): Int = ((s >>> (3 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt

  /** Unpacks the `incomingLen` index. */
  def incomingLen(s: StreamState): Int = ((s >>> (2 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt

  /** Unpacks the `stageEnd` index. */
  def stageEnd(s: StreamState): Int = ((s >>> (1 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt

  /** Unpacks the `outgoingLen` index. */
  def outgoingLen(s: StreamState): Int = ((s >>> LANE_BITS) & IDX_MASK).toInt

  /** Unpacks the output lane. */
  def outputLane(s: StreamState): Int = (s & LANE_MASK).toInt

  /** Returns `s` with the `stageStart` index replaced by `v`. */
  def withStageStart(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (3 * IDX_BITS + LANE_BITS))) | (v.toLong << (3 * IDX_BITS + LANE_BITS))

  /** Returns `s` with the `incomingLen` index replaced by `v`. */
  def withIncomingLen(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (2 * IDX_BITS + LANE_BITS))) | (v.toLong << (2 * IDX_BITS + LANE_BITS))

  /** Returns `s` with the `stageEnd` index replaced by `v`. */
  def withStageEnd(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (1 * IDX_BITS + LANE_BITS))) | (v.toLong << (1 * IDX_BITS + LANE_BITS))

  /** Returns `s` with the `outgoingLen` index replaced by `v`. */
  def withOutgoingLen(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << LANE_BITS)) | (v.toLong << LANE_BITS)

  /** Returns `s` with the output lane replaced by `v`. */
  def withOutputLane(s: StreamState, v: Int): StreamState =
    (s & ~LANE_MASK.toLong) | v.toLong

  /** The state with every field 0. */
  final val empty: Long = 0L

  /** The largest index a 15-bit field holds. */
  final val MaxIndex = 32767
}
