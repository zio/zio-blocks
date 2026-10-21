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

package zio.blocks.schema.binding

/**
 * A `RegisterOffset` is a position in [[Registers]], or an amount of space in
 * them, packed into one `Long`. The high 32 bits count bytes of the byte area,
 * which holds primitive values, and the low 32 bits count slots of the object
 * area, which holds references. Offsets add up: the offset of a record field is
 * the space taken by the fields before it, and `offset + base` places a record
 * inside a larger `Registers`. Plain `+` is correct as long as neither total
 * reaches 2^31; `add` checks that.
 *
 * {{{
 * import zio.blocks.schema.binding._
 *
 * val offset = RegisterOffset(objects = 3, bytes = 4, ints = 2)
 * val bytes  = RegisterOffset.getBytes(offset)   // 12
 * val slots  = RegisterOffset.getObjects(offset) // 3
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/registers/ Register System]]
 */
object RegisterOffset {

  /**
   * A packed byte count and object-slot count; see the `RegisterOffset` object.
   */
  type RegisterOffset = Long

  /** The first byte and the first object slot; also no space at all. */
  val Zero: RegisterOffset = 0L

  /**
   * Returns the space that the given numbers of values take:
   * `booleans + bytes + 2 * (chars + shorts) + 4 * (floats + ints) + 8 * (doubles + longs)`
   * bytes and `objects` object slots. Used as an offset, it points just past
   * those values.
   *
   * @throws java.lang.IllegalArgumentException
   *   `offset overflow`, when the byte total is negative or 2^31 or more, or
   *   `objects` is negative
   */
  def apply(
    booleans: Int = 0,
    bytes: Int = 0,
    shorts: Int = 0,
    ints: Int = 0,
    longs: Int = 0,
    floats: Int = 0,
    doubles: Int = 0,
    chars: Int = 0,
    objects: Int = 0
  ): RegisterOffset = {
    val primitives =
      booleans.toLong + bytes.toLong + ((chars.toLong + shorts.toLong) << 1) + ((floats.toLong + ints.toLong) << 2) + ((doubles.toLong + longs.toLong) << 3)
    if (((primitives | objects.toLong) >> 31) != 0) throw new IllegalArgumentException("offset overflow")
    primitives << 32 | objects.toLong
  }

  /**
   * Returns `left + right`, adding the byte counts and the object counts.
   *
   * @throws java.lang.IllegalArgumentException
   *   `offset overflow`, when either total is 2^31 or more
   */
  @inline
  def add(left: RegisterOffset, right: RegisterOffset): RegisterOffset = {
    val result = left + right
    if (((left.toInt + right.toInt).toLong | result) < 0) throw new IllegalArgumentException("offset overflow")
    result
  }

  /**
   * Returns the object part of `offset`: an object-slot index, or a number of
   * slots.
   */
  @inline
  def getObjects(offset: RegisterOffset): Int = offset.toInt

  /**
   * Returns the byte part of `offset`: a byte index in the byte area, or a
   * number of bytes.
   */
  @inline
  def getBytes(offset: RegisterOffset): Int = (offset >>> 32).toInt

  /** Returns `offset` moved past one object slot. */
  @inline
  def incrementObjects(offset: RegisterOffset): RegisterOffset = offset + 1L

  /**
   * Returns `offset` moved past 1 byte, the size of a `Boolean` or a `Byte`.
   */
  @inline
  def incrementBooleansAndBytes(offset: RegisterOffset): RegisterOffset = offset + 0x100000000L

  /** Returns `offset` moved past 2 bytes, the size of a `Char` or a `Short`. */
  @inline
  def incrementCharsAndShorts(offset: RegisterOffset): RegisterOffset = offset + 0x200000000L

  /** Returns `offset` moved past 4 bytes, the size of a `Float` or an `Int`. */
  @inline
  def incrementFloatsAndInts(offset: RegisterOffset): RegisterOffset = offset + 0x400000000L

  /**
   * Returns `offset` moved past 8 bytes, the size of a `Double` or a `Long`.
   */
  @inline
  def incrementDoublesAndLongs(offset: RegisterOffset): RegisterOffset = offset + 0x800000000L
}
