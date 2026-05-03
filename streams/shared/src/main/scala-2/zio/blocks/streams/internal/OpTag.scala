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

  final val READ_I = 35
  final val READ_L = 36
  final val READ_F = 37
  final val READ_D = 38
  final val READ_R = 39

  def mapTag(inLane: Lane, outLane: Lane): OpTag = inLane * 5 + outLane
  def filterTag(inLane: Lane): OpTag             = 25 + inLane
  def pushTag(inLane: Lane): OpTag               = 30 + inLane
  def readTag(inLane: Lane): OpTag               = 35 + inLane

  def isMap(tag: OpTag): Boolean    = tag >= 0 && tag <= 24
  def isFilter(tag: OpTag): Boolean = tag >= 25 && tag <= 29
  def isPush(tag: OpTag): Boolean   = tag >= 30 && tag <= 34
  def isRead(tag: OpTag): Boolean   = tag >= 35 && tag <= 39

  def storageLaneOfMapTag(tag: OpTag): Lane = tag % 5
}
