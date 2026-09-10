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

import zio.blocks.streams.JvmType

object OpTag {
  final val MAP_II = 0
  final val MAP_IL = 1
  final val MAP_IF = 2
  final val MAP_ID = 3
  final val MAP_IR = 4
  final val MAP_LI = 5
  final val MAP_LL = 6
  final val MAP_LF = 7
  final val MAP_LD = 8
  final val MAP_LR = 9
  final val MAP_FI = 10
  final val MAP_FL = 11
  final val MAP_FF = 12
  final val MAP_FD = 13
  final val MAP_FR = 14
  final val MAP_DI = 15
  final val MAP_DL = 16
  final val MAP_DF = 17
  final val MAP_DD = 18
  final val MAP_DR = 19
  final val MAP_RI = 20
  final val MAP_RL = 21
  final val MAP_RF = 22
  final val MAP_RD = 23
  final val MAP_RR = 24

  final val FILTER_I = 25
  final val FILTER_L = 26
  final val FILTER_F = 27
  final val FILTER_D = 28
  final val FILTER_R = 29

  final val PUSH_I = 30
  final val PUSH_L = 31
  final val PUSH_F = 32
  final val PUSH_D = 33
  final val PUSH_R = 34

  // A read tag identifies the physical Reader method. It is deliberately
  // independent of the five interpreter storage lanes: the five small
  // integral types share LANE_I only after their exact pull has completed.
  final val READ_I  = 35
  final val READ_L  = 36
  final val READ_F  = 37
  final val READ_D  = 38
  final val READ_R  = 39
  final val READ_B  = 40
  final val READ_BY = 41
  final val READ_C  = 42
  final val READ_S  = 43

  // White-box-only asynchronous MAP tags. Keep these distinct from synchronous
  // MAP so the interpreter can suspend with its instruction pointer intact.
  final val ASYNC_MAP_II = 44
  final val ASYNC_MAP_RR = 68

  // White-box-only asynchronous FILTER tags. The callback result is always a
  // Boolean, while the accepted value remains in its original physical lane.
  final val ASYNC_FILTER_I = 69
  final val ASYNC_FILTER_R = 73

  // White-box-only asynchronous COLLECT tags. Unlike ASYNC MAP, the raw
  // callback result is Option[B], which is decoded only after the callback
  // leaf itself completes.
  final val ASYNC_COLLECT_II = 74
  final val ASYNC_COLLECT_RR = 98

  // White-box-only asynchronous TAP tags. The callback's Unit payload is
  // discarded and the input remains in its original physical lane.
  final val ASYNC_TAP_I = 99
  final val ASYNC_TAP_R = 103

  // White-box-only asynchronous DISTINCT_KEY tags. The callback result is a
  // comparison key; the accepted value remains in its original lane.
  final val ASYNC_DISTINCT_KEY_I = 104
  final val ASYNC_DISTINCT_KEY_R = 108

  // White-box-only asynchronous MAP_ACCUM tags. Both input and output lanes
  // are encoded; accumulator state is owned by the instruction object.
  final val ASYNC_MAP_ACCUM_II = 109
  final val ASYNC_MAP_ACCUM_RR = 133

  // White-box-only asynchronous TAKE_WHILE tags. A false decision seals the
  // interpreter at EOF rather than re-driving the source like FILTER.
  final val ASYNC_TAKE_WHILE_I = 134
  final val ASYNC_TAKE_WHILE_R = 138

  def mapTag(inLane: Lane, outLane: Lane): OpTag = inLane * 5 + outLane
  def filterTag(inLane: Lane): OpTag             = 25 + inLane
  def pushTag(inLane: Lane): OpTag               = 30 + inLane
  def readTag(jvmType: JvmType): OpTag           = jvmType match {
    case JvmType.Boolean => READ_B
    case JvmType.Byte    => READ_BY
    case JvmType.Char    => READ_C
    case JvmType.Short   => READ_S
    case JvmType.Int     => READ_I
    case JvmType.Long    => READ_L
    case JvmType.Float   => READ_F
    case JvmType.Double  => READ_D
    case _               => READ_R
  }
  def asyncMapTag(inLane: Lane, outLane: Lane): OpTag      = ASYNC_MAP_II + inLane * 5 + outLane
  def asyncFilterTag(inLane: Lane): OpTag                  = ASYNC_FILTER_I + inLane
  def asyncCollectTag(inLane: Lane, outLane: Lane): OpTag  = ASYNC_COLLECT_II + inLane * 5 + outLane
  def asyncTapTag(inLane: Lane): OpTag                     = ASYNC_TAP_I + inLane
  def asyncDistinctKeyTag(inLane: Lane): OpTag             = ASYNC_DISTINCT_KEY_I + inLane
  def asyncMapAccumTag(inLane: Lane, outLane: Lane): OpTag = ASYNC_MAP_ACCUM_II + inLane * 5 + outLane
  def asyncTakeWhileTag(inLane: Lane): OpTag               = ASYNC_TAKE_WHILE_I + inLane

  def isMap(tag: OpTag): Boolean              = tag >= 0 && tag <= 24
  def isFilter(tag: OpTag): Boolean           = tag >= 25 && tag <= 29
  def isPush(tag: OpTag): Boolean             = tag >= 30 && tag <= 34
  def isRead(tag: OpTag): Boolean             = (tag >= READ_I && tag <= READ_R) || (tag >= READ_B && tag <= READ_S)
  def isAsyncMap(tag: OpTag): Boolean         = tag >= ASYNC_MAP_II && tag <= ASYNC_MAP_RR
  def isAsyncFilter(tag: OpTag): Boolean      = tag >= ASYNC_FILTER_I && tag <= ASYNC_FILTER_R
  def isAsyncCollect(tag: OpTag): Boolean     = tag >= ASYNC_COLLECT_II && tag <= ASYNC_COLLECT_RR
  def isAsyncTap(tag: OpTag): Boolean         = tag >= ASYNC_TAP_I && tag <= ASYNC_TAP_R
  def isAsyncDistinctKey(tag: OpTag): Boolean =
    tag >= ASYNC_DISTINCT_KEY_I && tag <= ASYNC_DISTINCT_KEY_R
  def isAsyncMapAccum(tag: OpTag): Boolean  = tag >= ASYNC_MAP_ACCUM_II && tag <= ASYNC_MAP_ACCUM_RR
  def isAsyncTakeWhile(tag: OpTag): Boolean = tag >= ASYNC_TAKE_WHILE_I && tag <= ASYNC_TAKE_WHILE_R

  def storageLaneOfMapTag(tag: OpTag): Lane           = tag                        % 5
  def storageLaneOfAsyncMapTag(tag: OpTag): Lane      = (tag - ASYNC_MAP_II)       % 5
  def storageLaneOfAsyncCollectTag(tag: OpTag): Lane  = (tag - ASYNC_COLLECT_II)   % 5
  def storageLaneOfAsyncMapAccumTag(tag: OpTag): Lane = (tag - ASYNC_MAP_ACCUM_II) % 5
  def storageLaneOfReadTag(tag: OpTag): Lane          = tag match {
    case READ_B | READ_BY | READ_C | READ_S | READ_I => 0
    case READ_L                                      => 1
    case READ_F                                      => 2
    case READ_D                                      => 3
    case READ_R                                      => 4
  }
}
