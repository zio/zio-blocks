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
 * Operation tags for the [[Interpreter]]. Each tag is an 8-bit value encoding
 * the operation type and (for map/filter/push/read) the input/output lane.
 *
 * Layout (40 tags total):
 *   - 0–24: Map ops (5 input lanes × 5 output lanes)
 *   - 25–29: Filter ops (5 input lanes)
 *   - 30–34: Push ops / flatMap (5 input lanes)
 *   - 35–39: Read ops / source (5 input lanes)
 *
 * All vals have singleton literal types so Scala treats them as compile-time
 * constants for `@switch` pattern matching.
 */
object OpTag {

  // ---- Map ops: inLane * 5 + outLane (0–24) ----
  inline val MAP_II: 0  = 0; inline val MAP_IL: 1   = 1; inline val MAP_IF: 2   = 2; inline val MAP_ID: 3   = 3;
  inline val MAP_IR: 4  = 4
  inline val MAP_LI: 5  = 5; inline val MAP_LL: 6   = 6; inline val MAP_LF: 7   = 7; inline val MAP_LD: 8   = 8;
  inline val MAP_LR: 9  = 9
  inline val MAP_FI: 10 = 10; inline val MAP_FL: 11 = 11; inline val MAP_FF: 12 = 12; inline val MAP_FD: 13 = 13;
  inline val MAP_FR: 14 = 14
  inline val MAP_DI: 15 = 15; inline val MAP_DL: 16 = 16; inline val MAP_DF: 17 = 17; inline val MAP_DD: 18 = 18;
  inline val MAP_DR: 19 = 19
  inline val MAP_RI: 20 = 20; inline val MAP_RL: 21 = 21; inline val MAP_RF: 22 = 22; inline val MAP_RD: 23 = 23;
  inline val MAP_RR: 24 = 24

  // ---- Filter ops (25–29) ----
  inline val FILTER_I: 25 = 25; inline val FILTER_L: 26 = 26; inline val FILTER_F: 27 = 27
  inline val FILTER_D: 28 = 28; inline val FILTER_R: 29 = 29

  // ---- Push ops / flatMap (30–34) ----
  inline val PUSH_I: 30 = 30; inline val PUSH_L: 31 = 31; inline val PUSH_F: 32 = 32
  inline val PUSH_D: 33 = 33; inline val PUSH_R: 34 = 34

  // ---- Read ops / source (35–39) ----
  inline val READ_I: 35 = 35; inline val READ_L: 36 = 36; inline val READ_F: 37 = 37
  inline val READ_D: 38 = 38; inline val READ_R: 39 = 39

  // ---- Derived helpers ----
  inline def mapTag(inLane: Lane, outLane: Lane): OpTag = inLane * 5 + outLane
  inline def filterTag(inLane: Lane): OpTag             = 25 + inLane
  inline def pushTag(inLane: Lane): OpTag               = 30 + inLane
  inline def readTag(inLane: Lane): OpTag               = 35 + inLane

  inline def isMap(tag: OpTag): Boolean    = tag >= 0 && tag <= 24
  inline def isFilter(tag: OpTag): Boolean = tag >= 25 && tag <= 29
  inline def isPush(tag: OpTag): Boolean   = tag >= 30 && tag <= 34
  inline def isRead(tag: OpTag): Boolean   = tag >= 35 && tag <= 39

  inline def storageLaneOfMapTag(tag: OpTag): Lane = tag % 5
}
