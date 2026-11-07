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

/**
 * The operation tags of the synchronous and asynchronous interpreters. In a tag
 * name, `I` is the `Int` lane (which also carries `Boolean`, `Byte`, `Char` and
 * `Short`), `L` is `Long`, `F` is `Float`, `D` is `Double` and `R` is the
 * reference lane; a two-letter suffix names the input lane, then the output
 * lane.
 */
object OpTag {

  /** A `map` from the `Int` lane to the `Int` lane. */
  final val MAP_II = 0

  /** A `map` from the `Int` lane to the `Long` lane. */
  final val MAP_IL = 1

  /** A `map` from the `Int` lane to the `Float` lane. */
  final val MAP_IF = 2

  /** A `map` from the `Int` lane to the `Double` lane. */
  final val MAP_ID = 3

  /** A `map` from the `Int` lane to the reference lane. */
  final val MAP_IR = 4

  /** A `map` from the `Long` lane to the `Int` lane. */
  final val MAP_LI = 5

  /** A `map` from the `Long` lane to the `Long` lane. */
  final val MAP_LL = 6

  /** A `map` from the `Long` lane to the `Float` lane. */
  final val MAP_LF = 7

  /** A `map` from the `Long` lane to the `Double` lane. */
  final val MAP_LD = 8

  /** A `map` from the `Long` lane to the reference lane. */
  final val MAP_LR = 9

  /** A `map` from the `Float` lane to the `Int` lane. */
  final val MAP_FI = 10

  /** A `map` from the `Float` lane to the `Long` lane. */
  final val MAP_FL = 11

  /** A `map` from the `Float` lane to the `Float` lane. */
  final val MAP_FF = 12

  /** A `map` from the `Float` lane to the `Double` lane. */
  final val MAP_FD = 13

  /** A `map` from the `Float` lane to the reference lane. */
  final val MAP_FR = 14

  /** A `map` from the `Double` lane to the `Int` lane. */
  final val MAP_DI = 15

  /** A `map` from the `Double` lane to the `Long` lane. */
  final val MAP_DL = 16

  /** A `map` from the `Double` lane to the `Float` lane. */
  final val MAP_DF = 17

  /** A `map` from the `Double` lane to the `Double` lane. */
  final val MAP_DD = 18

  /** A `map` from the `Double` lane to the reference lane. */
  final val MAP_DR = 19

  /** A `map` from the reference lane to the `Int` lane. */
  final val MAP_RI = 20

  /** A `map` from the reference lane to the `Long` lane. */
  final val MAP_RL = 21

  /** A `map` from the reference lane to the `Float` lane. */
  final val MAP_RF = 22

  /** A `map` from the reference lane to the `Double` lane. */
  final val MAP_RD = 23

  /** A `map` from the reference lane to the reference lane. */
  final val MAP_RR = 24

  /** A `filter` on the `Int` lane. */
  final val FILTER_I = 25

  /** A `filter` on the `Long` lane. */
  final val FILTER_L = 26

  /** A `filter` on the `Float` lane. */
  final val FILTER_F = 27

  /** A `filter` on the `Double` lane. */
  final val FILTER_D = 28

  /** A `filter` on the reference lane. */
  final val FILTER_R = 29

  /**
   * A `flatMap` step on the `Int` lane: its function returns the inner stream
   * to read next.
   */
  final val PUSH_I = 30

  /**
   * A `flatMap` step on the `Long` lane: its function returns the inner stream
   * to read next.
   */
  final val PUSH_L = 31

  /**
   * A `flatMap` step on the `Float` lane: its function returns the inner stream
   * to read next.
   */
  final val PUSH_F = 32

  /**
   * A `flatMap` step on the `Double` lane: its function returns the inner
   * stream to read next.
   */
  final val PUSH_D = 33

  /**
   * A `flatMap` step on the reference lane: its function returns the inner
   * stream to read next.
   */
  final val PUSH_R = 34

  // A read tag identifies the physical Reader method. It is deliberately
  // independent of the five interpreter storage lanes: the five small
  // integral types share LANE_I only after their exact pull has completed.
  /** A pull from a source on the `Int` lane, through `readInt`. */
  final val READ_I = 35

  /**
   * A pull from a source on the `Long` lane, through a one-element `readLongs`,
   * whose count marks the end.
   */
  final val READ_L = 36

  /** A pull from a source on the `Float` lane, through `readFloat`. */
  final val READ_F = 37

  /**
   * A pull from a source on the `Double` lane, through a one-element
   * `readDoubles`, whose count marks the end.
   */
  final val READ_D = 38

  /** A pull from a source on the reference lane, through `read`. */
  final val READ_R = 39

  /** A pull from a source on the `Boolean` lane, through `readBoolean`. */
  final val READ_B = 40

  /** A pull from a source on the `Byte` lane, through `readByte`. */
  final val READ_BY = 41

  /** A pull from a source on the `Char` lane, through `readChar`. */
  final val READ_C = 42

  /** A pull from a source on the `Short` lane, through `readShort`. */
  final val READ_S = 43

  // White-box-only asynchronous MAP tags. Keep these distinct from synchronous
  // MAP so the interpreter can suspend with its instruction pointer intact.
  /**
   * The first `ASYNC_MAP_*` tag (`Int` lane to `Int` lane), for a map whose
   * function returns an `Async`; the 25 tags up to `ASYNC_MAP_RR` cover every
   * input and output lane.
   */
  final val ASYNC_MAP_II = 44

  /** The last `ASYNC_MAP_*` tag (reference lane to reference lane). */
  final val ASYNC_MAP_RR = 68

  // White-box-only asynchronous FILTER tags. The callback result is always a
  // Boolean, while the accepted value remains in its original physical lane.
  /**
   * The first `ASYNC_FILTER_*` tag (`Int` lane), for a filter whose predicate
   * returns an `Async[Boolean]`; the 5 tags up to `ASYNC_FILTER_R` cover every
   * lane.
   */
  final val ASYNC_FILTER_I = 69

  /** The last `ASYNC_FILTER_*` tag (reference lane). */
  final val ASYNC_FILTER_R = 73

  // White-box-only asynchronous COLLECT tags. Unlike ASYNC MAP, the raw
  // callback result is Option[B], which is decoded only after the callback
  // leaf itself completes.
  /**
   * The first `ASYNC_COLLECT_*` tag (`Int` lane to `Int` lane), for a collect
   * whose function returns an `Async` of an `Option`; the 25 tags up to
   * `ASYNC_COLLECT_RR` cover every input and output lane.
   */
  final val ASYNC_COLLECT_II = 74

  /** The last `ASYNC_COLLECT_*` tag (reference lane to reference lane). */
  final val ASYNC_COLLECT_RR = 98

  // White-box-only asynchronous TAP tags. The callback's Unit payload is
  // discarded and the input remains in its original physical lane.
  /**
   * The first `ASYNC_TAP_*` tag (`Int` lane), for a tap whose function returns
   * an `Async[Unit]`; the element passes on unchanged; the 5 tags up to
   * `ASYNC_TAP_R` cover every lane.
   */
  final val ASYNC_TAP_I = 99

  /** The last `ASYNC_TAP_*` tag (reference lane). */
  final val ASYNC_TAP_R = 103

  // White-box-only asynchronous DISTINCT_KEY tags. The callback result is a
  // comparison key; the accepted value remains in its original lane.
  /**
   * The first `ASYNC_DISTINCT_KEY_*` tag (`Int` lane), for a distinct-by-key
   * step whose key function returns an `Async`; the 5 tags up to
   * `ASYNC_DISTINCT_KEY_R` cover every lane.
   */
  final val ASYNC_DISTINCT_KEY_I = 104

  /** The last `ASYNC_DISTINCT_KEY_*` tag (reference lane). */
  final val ASYNC_DISTINCT_KEY_R = 108

  // White-box-only asynchronous MAP_ACCUM tags. Both input and output lanes
  // are encoded; accumulator state is owned by the instruction object.
  /**
   * The first `ASYNC_MAP_ACCUM_*` tag (`Int` lane to `Int` lane), for a
   * stateful map (such as a scan) whose function returns an `Async`; the 25
   * tags up to `ASYNC_MAP_ACCUM_RR` cover every input and output lane.
   */
  final val ASYNC_MAP_ACCUM_II = 109

  /** The last `ASYNC_MAP_ACCUM_*` tag (reference lane to reference lane). */
  final val ASYNC_MAP_ACCUM_RR = 133

  // White-box-only asynchronous TAKE_WHILE tags. A false decision seals the
  // interpreter at EOF rather than re-driving the source like FILTER.
  /**
   * The first `ASYNC_TAKE_WHILE_*` tag (`Int` lane), for a take-while whose
   * predicate returns an `Async[Boolean]`; a `false` ends the stream; the 5
   * tags up to `ASYNC_TAKE_WHILE_R` cover every lane.
   */
  final val ASYNC_TAKE_WHILE_I = 134

  /** The last `ASYNC_TAKE_WHILE_*` tag (reference lane). */
  final val ASYNC_TAKE_WHILE_R = 138

  /** The `MAP_*` tag for the given input and output lanes. */
  def mapTag(inLane: Lane, outLane: Lane): OpTag = inLane * 5 + outLane

  /** The `FILTER_*` tag for the given lane. */
  def filterTag(inLane: Lane): OpTag = 25 + inLane

  /** The `PUSH_*` tag for the given lane. */
  def pushTag(inLane: Lane): OpTag = 30 + inLane

  /** The `READ_*` tag for a source on the lane of `jvmType`. */
  def readTag(jvmType: JvmType): OpTag = jvmType match {
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

  /** The `ASYNC_MAP_*` tag for the given input and output lanes. */
  def asyncMapTag(inLane: Lane, outLane: Lane): OpTag = ASYNC_MAP_II + inLane * 5 + outLane

  /** The `ASYNC_FILTER_*` tag for the given lane. */
  def asyncFilterTag(inLane: Lane): OpTag = ASYNC_FILTER_I + inLane

  /** The `ASYNC_COLLECT_*` tag for the given input and output lanes. */
  def asyncCollectTag(inLane: Lane, outLane: Lane): OpTag = ASYNC_COLLECT_II + inLane * 5 + outLane

  /** The `ASYNC_TAP_*` tag for the given lane. */
  def asyncTapTag(inLane: Lane): OpTag = ASYNC_TAP_I + inLane

  /** The `ASYNC_DISTINCT_KEY_*` tag for the given lane. */
  def asyncDistinctKeyTag(inLane: Lane): OpTag = ASYNC_DISTINCT_KEY_I + inLane

  /** The `ASYNC_MAP_ACCUM_*` tag for the given input and output lanes. */
  def asyncMapAccumTag(inLane: Lane, outLane: Lane): OpTag = ASYNC_MAP_ACCUM_II + inLane * 5 + outLane

  /** The `ASYNC_TAKE_WHILE_*` tag for the given lane. */
  def asyncTakeWhileTag(inLane: Lane): OpTag = ASYNC_TAKE_WHILE_I + inLane

  /** Returns `true` for a `MAP_*` tag. */
  def isMap(tag: OpTag): Boolean = tag >= 0 && tag <= 24

  /** Returns `true` for a `FILTER_*` tag. */
  def isFilter(tag: OpTag): Boolean = tag >= 25 && tag <= 29

  /** Returns `true` for a `PUSH_*` tag. */
  def isPush(tag: OpTag): Boolean = tag >= 30 && tag <= 34

  /** Returns `true` for a `READ_*` tag. */
  def isRead(tag: OpTag): Boolean = (tag >= READ_I && tag <= READ_R) || (tag >= READ_B && tag <= READ_S)

  /** Returns `true` for an `ASYNC_MAP_*` tag. */
  def isAsyncMap(tag: OpTag): Boolean = tag >= ASYNC_MAP_II && tag <= ASYNC_MAP_RR

  /** Returns `true` for an `ASYNC_FILTER_*` tag. */
  def isAsyncFilter(tag: OpTag): Boolean = tag >= ASYNC_FILTER_I && tag <= ASYNC_FILTER_R

  /** Returns `true` for an `ASYNC_COLLECT_*` tag. */
  def isAsyncCollect(tag: OpTag): Boolean = tag >= ASYNC_COLLECT_II && tag <= ASYNC_COLLECT_RR

  /** Returns `true` for an `ASYNC_TAP_*` tag. */
  def isAsyncTap(tag: OpTag): Boolean = tag >= ASYNC_TAP_I && tag <= ASYNC_TAP_R

  /** Returns `true` for an `ASYNC_DISTINCT_KEY_*` tag. */
  def isAsyncDistinctKey(tag: OpTag): Boolean =
    tag >= ASYNC_DISTINCT_KEY_I && tag <= ASYNC_DISTINCT_KEY_R

  /** Returns `true` for an `ASYNC_MAP_ACCUM_*` tag. */
  def isAsyncMapAccum(tag: OpTag): Boolean = tag >= ASYNC_MAP_ACCUM_II && tag <= ASYNC_MAP_ACCUM_RR

  /** Returns `true` for an `ASYNC_TAKE_WHILE_*` tag. */
  def isAsyncTakeWhile(tag: OpTag): Boolean = tag >= ASYNC_TAKE_WHILE_I && tag <= ASYNC_TAKE_WHILE_R

  /** The output lane of a `MAP_*` tag. */
  def storageLaneOfMapTag(tag: OpTag): Lane = tag % 5

  /** The output lane of an `ASYNC_MAP_*` tag. */
  def storageLaneOfAsyncMapTag(tag: OpTag): Lane = (tag - ASYNC_MAP_II) % 5

  /** The output lane of an `ASYNC_COLLECT_*` tag. */
  def storageLaneOfAsyncCollectTag(tag: OpTag): Lane = (tag - ASYNC_COLLECT_II) % 5

  /** The output lane of an `ASYNC_MAP_ACCUM_*` tag. */
  def storageLaneOfAsyncMapAccumTag(tag: OpTag): Lane = (tag - ASYNC_MAP_ACCUM_II) % 5

  /**
   * The lane a `READ_*` tag stores its value in: `Boolean`, `Byte`, `Char` and
   * `Short` share the `Int` lane.
   */
  def storageLaneOfReadTag(tag: OpTag): Lane = tag match {
    case READ_B | READ_BY | READ_C | READ_S | READ_I => 0
    case READ_L                                      => 1
    case READ_F                                      => 2
    case READ_D                                      => 3
    case READ_R                                      => 4
  }
}
