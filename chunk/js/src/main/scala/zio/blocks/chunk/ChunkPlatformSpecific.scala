/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.chunk

import scala.reflect.{ClassTag, classTag}

private[chunk] trait ChunkPlatformSpecific {

  private[chunk] object Tags {
    def fromValue[A](a: A): ClassTag[A] = {
      val _ = a
      classTag[AnyRef].asInstanceOf[ClassTag[A]]
    }
  }

  private[chunk] def findFirst(array: Array[Byte], offset: Int, length: Int, target: Byte): Int = {
    var i = 0
    while (i < length) {
      if (array(offset + i) == target) return i
      i += 1
    }
    -1
  }

  private[chunk] def findFirstNot(array: Array[Byte], offset: Int, length: Int, target: Byte): Int = {
    var i = 0
    while (i < length) {
      if (array(offset + i) != target) return i
      i += 1
    }
    -1
  }

  private[chunk] def matchAny(array: Array[Byte], offset: Int, length: Int, candidates: Array[Byte]): Boolean = {
    var i = 0
    while (i < length) {
      val b = array(offset + i)
      var j = 0
      while (j < candidates.length) {
        if (b == candidates(j)) return true
        j += 1
      }
      i += 1
    }
    false
  }

  private[chunk] def byteChecksum(array: Array[Byte], offset: Int, length: Int): Long = {
    var sum = 0L
    var i   = 0
    while (i < length) {
      sum += array(offset + i) & 0xff
      i += 1
    }
    sum
  }

  private[chunk] def bitwiseAnd(
    left: Array[Byte],
    leftOffset: Int,
    right: Array[Byte],
    rightOffset: Int,
    target: Array[Byte],
    targetOffset: Int,
    bytes: Int
  ): Unit = {
    var i = 0
    while (i < bytes) {
      target(targetOffset + i) = (left(leftOffset + i) & right(rightOffset + i)).toByte
      i += 1
    }
  }

  private[chunk] def bitwiseOr(
    left: Array[Byte],
    leftOffset: Int,
    right: Array[Byte],
    rightOffset: Int,
    target: Array[Byte],
    targetOffset: Int,
    bytes: Int
  ): Unit = {
    var i = 0
    while (i < bytes) {
      target(targetOffset + i) = (left(leftOffset + i) | right(rightOffset + i)).toByte
      i += 1
    }
  }

  private[chunk] def bitwiseXor(
    left: Array[Byte],
    leftOffset: Int,
    right: Array[Byte],
    rightOffset: Int,
    target: Array[Byte],
    targetOffset: Int,
    bytes: Int
  ): Unit = {
    var i = 0
    while (i < bytes) {
      target(targetOffset + i) = (left(leftOffset + i) ^ right(rightOffset + i)).toByte
      i += 1
    }
  }

  private[chunk] def bitwiseNot(
    data: Array[Byte],
    dataOffset: Int,
    target: Array[Byte],
    targetOffset: Int,
    bytes: Int
  ): Unit = {
    var i = 0
    while (i < bytes) {
      target(targetOffset + i) = (~data(dataOffset + i)).toByte
      i += 1
    }
  }
}
