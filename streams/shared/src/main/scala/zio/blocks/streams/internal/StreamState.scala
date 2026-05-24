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

object StreamState {
  private final val IDX_BITS  = 13
  private final val IDX_MASK  = (1 << IDX_BITS) - 1
  private final val LANE_BITS = 4
  private final val LANE_MASK = (1 << LANE_BITS) - 1

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

  def stageStart(s: StreamState): Int  = ((s >>> (3 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt
  def incomingLen(s: StreamState): Int = ((s >>> (2 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt
  def stageEnd(s: StreamState): Int    = ((s >>> (1 * IDX_BITS + LANE_BITS)) & IDX_MASK).toInt
  def outgoingLen(s: StreamState): Int = ((s >>> LANE_BITS) & IDX_MASK).toInt
  def outputLane(s: StreamState): Int  = (s & LANE_MASK).toInt

  def withStageStart(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (3 * IDX_BITS + LANE_BITS))) | (v.toLong << (3 * IDX_BITS + LANE_BITS))

  def withIncomingLen(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (2 * IDX_BITS + LANE_BITS))) | (v.toLong << (2 * IDX_BITS + LANE_BITS))

  def withStageEnd(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << (1 * IDX_BITS + LANE_BITS))) | (v.toLong << (1 * IDX_BITS + LANE_BITS))

  def withOutgoingLen(s: StreamState, v: Int): StreamState =
    (s & ~(IDX_MASK.toLong << LANE_BITS)) | (v.toLong << LANE_BITS)

  def withOutputLane(s: StreamState, v: Int): StreamState =
    (s & ~LANE_MASK.toLong) | v.toLong

  final val empty: Long = 0L
  final val MaxIndex    = 8191
}
