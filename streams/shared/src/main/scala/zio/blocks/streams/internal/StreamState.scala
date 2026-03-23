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
 * Packed pipeline state: four 13-bit unsigned index fields plus a 4-bit output
 * lane, all in a single Long (56 bits used).
 *
 * Layout:
 * {{{
 *   bits [55:43] = stageStart   (13 bits, max 8191)
 *   bits [42:30] = incomingLen  (13 bits, max 8191)
 *   bits [29:17] = stageEnd     (13 bits, max 8191)
 *   bits [16: 4] = outgoingLen  (13 bits, max 8191)
 *   bits [ 3: 0] = outputLane   (4 bits, max 15)
 * }}}
 *
 * When stored in `Interpreter`'s incoming/outgoing Long arrays, the StreamState
 * occupies the upper 56 bits (shifted left by 8), with the lower 8 bits
 * reserved for the [[OpTag]].
 */
object StreamState {
  private inline val IDX_BITS  = 13
  private inline val IDX_MASK  = (1 << IDX_BITS) - 1  // 0x1FFF = 8191
  private inline val LANE_BITS = 4
  private inline val LANE_MASK = (1 << LANE_BITS) - 1 // 0xF = 15

  inline def apply(
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

  inline def stageStart(s: StreamState): Int  = ((s >>> (3 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt
  inline def incomingLen(s: StreamState): Int = ((s >>> (2 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt
  inline def stageEnd(s: StreamState): Int    = ((s >>> (1 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt
  inline def outgoingLen(s: StreamState): Int = ((s >>> LANE_BITS) & IDX_MASK).toInt
  inline def outputLane(s: StreamState): Int  = (s & LANE_MASK).toInt

  inline def withStageStart(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (3 * IDX_BITS + LANE_BITS))) | (v.toLong << (3 * IDX_BITS + LANE_BITS))

  inline def withIncomingLen(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (2 * IDX_BITS + LANE_BITS))) | (v.toLong << (2 * IDX_BITS + LANE_BITS))

  inline def withStageEnd(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (1 * IDX_BITS + LANE_BITS))) | (v.toLong << (1 * IDX_BITS + LANE_BITS))

  inline def withOutgoingLen(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << LANE_BITS)) | (v.toLong << LANE_BITS)

  inline def withOutputLane(s: StreamState, v: Int): StreamState =
    (s & ~LANE_MASK.toLong) | v.toLong

  inline val empty: 0L = 0L

  /** Maximum value for any index field (13 bits unsigned). */
  inline val MaxIndex: 8191 = 8191
}
