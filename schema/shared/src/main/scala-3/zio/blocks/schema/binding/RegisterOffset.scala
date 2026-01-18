/*
 * Copyright 2023 ZIO Blocks Maintainers
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

object RegisterOffset {
  type RegisterOffset = Long

  val Zero: RegisterOffset = 0L

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

  inline def add(left: RegisterOffset, right: RegisterOffset): RegisterOffset = {
    val result = left + right
    if (((left.toInt + right.toInt).toLong | result) < 0) throw new IllegalArgumentException("offset overflow")
    result
  }

  inline def getObjects(offset: RegisterOffset): Int = offset.toInt

  inline def getBytes(offset: RegisterOffset): Int = (offset >>> 32).toInt

  inline def incrementObjects(offset: RegisterOffset): RegisterOffset = offset + 1L

  inline def incrementBooleansAndBytes(offset: RegisterOffset): RegisterOffset = offset + 0x100000000L

  inline def incrementCharsAndShorts(offset: RegisterOffset): RegisterOffset = offset + 0x200000000L

  inline def incrementFloatsAndInts(offset: RegisterOffset): RegisterOffset = offset + 0x400000000L

  inline def incrementDoublesAndLongs(offset: RegisterOffset): RegisterOffset = offset + 0x800000000L
}
