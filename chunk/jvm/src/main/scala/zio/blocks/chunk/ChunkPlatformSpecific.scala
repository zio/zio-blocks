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
    def fromValue[A](a: A): ClassTag[A] =
      if (a == null) classTag[AnyRef].asInstanceOf[ClassTag[A]]
      else {
        val c            = a.getClass
        val unboxedClass =
          if (isBoolean(c)) BooleanClass.asInstanceOf[Class[A]]
          else if (isByte(c)) ByteClass.asInstanceOf[Class[A]]
          else if (isShort(c)) ShortClass.asInstanceOf[Class[A]]
          else if (isInt(c)) IntClass.asInstanceOf[Class[A]]
          else if (isLong(c)) LongClass.asInstanceOf[Class[A]]
          else if (isFloat(c)) FloatClass.asInstanceOf[Class[A]]
          else if (isDouble(c)) DoubleClass.asInstanceOf[Class[A]]
          else if (isChar(c)) CharClass.asInstanceOf[Class[A]]
          else null

        if (unboxedClass eq null) classTag[AnyRef].asInstanceOf[ClassTag[A]]
        else ClassTag(unboxedClass).asInstanceOf[ClassTag[A]]
      }

    private def isBoolean(c: Class[_]): Boolean = c == BooleanClass || c == BooleanClassBox
    private def isByte(c: Class[_]): Boolean    = c == ByteClass || c == ByteClassBox
    private def isShort(c: Class[_]): Boolean   = c == ShortClass || c == ShortClassBox
    private def isInt(c: Class[_]): Boolean     = c == IntClass || c == IntClassBox
    private def isLong(c: Class[_]): Boolean    = c == LongClass || c == LongClassBox
    private def isFloat(c: Class[_]): Boolean   = c == FloatClass || c == FloatClassBox
    private def isDouble(c: Class[_]): Boolean  = c == DoubleClass || c == DoubleClassBox
    private def isChar(c: Class[_]): Boolean    = c == CharClass || c == CharClassBox

    private val BooleanClass    = classOf[Boolean]
    private val BooleanClassBox = classOf[java.lang.Boolean]
    private val ByteClass       = classOf[Byte]
    private val ByteClassBox    = classOf[java.lang.Byte]
    private val ShortClass      = classOf[Short]
    private val ShortClassBox   = classOf[java.lang.Short]
    private val IntClass        = classOf[Int]
    private val IntClassBox     = classOf[java.lang.Integer]
    private val LongClass       = classOf[Long]
    private val LongClassBox    = classOf[java.lang.Long]
    private val FloatClass      = classOf[Float]
    private val FloatClassBox   = classOf[java.lang.Float]
    private val DoubleClass     = classOf[Double]
    private val DoubleClassBox  = classOf[java.lang.Double]
    private val CharClass       = classOf[Char]
    private val CharClassBox    = classOf[java.lang.Character]
  }

  private[chunk] def findFirst(array: Array[Byte], offset: Int, length: Int, target: Byte): Int =
    SIMDUtils.findFirstSIMD(array, offset, length, target)

  private[chunk] def findFirstNot(array: Array[Byte], offset: Int, length: Int, target: Byte): Int =
    SIMDUtils.findFirstNotSIMD(array, offset, length, target)

  private[chunk] def matchAny(array: Array[Byte], offset: Int, length: Int, candidates: Array[Byte]): Boolean =
    SIMDUtils.matchAnySIMD(array, offset, length, candidates)

  private[chunk] def byteChecksum(array: Array[Byte], offset: Int, length: Int): Long =
    SIMDUtils.checksumSIMD(array, offset, length)

  private[chunk] def bitwiseAnd(
    left: Array[Byte],
    leftOffset: Int,
    right: Array[Byte],
    rightOffset: Int,
    target: Array[Byte],
    targetOffset: Int,
    bytes: Int
  ): Unit =
    SIMDUtils.andSIMD(left, leftOffset, right, rightOffset, target, targetOffset, bytes)

  private[chunk] def bitwiseOr(
    left: Array[Byte],
    leftOffset: Int,
    right: Array[Byte],
    rightOffset: Int,
    target: Array[Byte],
    targetOffset: Int,
    bytes: Int
  ): Unit =
    SIMDUtils.orSIMD(left, leftOffset, right, rightOffset, target, targetOffset, bytes)

  private[chunk] def bitwiseXor(
    left: Array[Byte],
    leftOffset: Int,
    right: Array[Byte],
    rightOffset: Int,
    target: Array[Byte],
    targetOffset: Int,
    bytes: Int
  ): Unit =
    SIMDUtils.xorSIMD(left, leftOffset, right, rightOffset, target, targetOffset, bytes)

  private[chunk] def bitwiseNot(
    data: Array[Byte],
    dataOffset: Int,
    target: Array[Byte],
    targetOffset: Int,
    bytes: Int
  ): Unit =
    SIMDUtils.notSIMD(data, dataOffset, target, targetOffset, bytes)
}
