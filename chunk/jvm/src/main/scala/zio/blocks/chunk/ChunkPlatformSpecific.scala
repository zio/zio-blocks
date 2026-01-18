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

  private[chunk] def byteChecksum(array: Array[Byte], offset: Int, length: Int): Int = {
    var i     = offset
    var sum   = 0
    val limit = offset + length - 7
    while (i < limit) {
      sum += (array(i) & 0xff) +
        (array(i + 1) & 0xff) +
        (array(i + 2) & 0xff) +
        (array(i + 3) & 0xff) +
        (array(i + 4) & 0xff) +
        (array(i + 5) & 0xff) +
        (array(i + 6) & 0xff) +
        (array(i + 7) & 0xff)
      i += 8
    }
    val end = offset + length
    while (i < end) {
      sum += array(i) & 0xff
      i += 1
    }
    sum
  }

  private[chunk] def findFirst(array: Array[Byte], length: Int, element: Byte, fromIndex: Int): Int = {
    var i = fromIndex
    while (i < length) {
      if (array(i) == element) return i
      i += 1
    }
    -1
  }

  private[chunk] def findFirstNot(array: Array[Byte], length: Int, element: Byte, fromIndex: Int): Int = {
    var i = fromIndex
    while (i < length) {
      if (array(i) != element) return i
      i += 1
    }
    -1
  }

  private[chunk] def matchAny(array: Array[Byte], length: Int, candidates: Array[Byte]): Boolean = {
    var i    = 0
    val clen = candidates.length
    while (i < length) {
      val b = array(i)
      var j = 0
      while (j < clen) {
        if (b == candidates(j)) return true
        j += 1
      }
      i += 1
    }
    false
  }

  // Bitwise operations - basic implementation (JIT handles simple loops well)
  private[chunk] def bitwiseAnd(
    l: Array[Byte],
    lOffset: Int,
    r: Array[Byte],
    rOffset: Int,
    length: Int
  ): Array[Byte] = {
    val res = new Array[Byte](length)
    var i   = 0
    while (i < length) {
      res(i) = (l(i + lOffset) & r(i + rOffset)).toByte
      i += 1
    }
    res
  }

  private[chunk] def bitwiseOr(l: Array[Byte], lOffset: Int, r: Array[Byte], rOffset: Int, length: Int): Array[Byte] = {
    val res = new Array[Byte](length)
    var i   = 0
    while (i < length) {
      res(i) = (l(i + lOffset) | r(i + rOffset)).toByte
      i += 1
    }
    res
  }

  private[chunk] def bitwiseXor(
    l: Array[Byte],
    lOffset: Int,
    r: Array[Byte],
    rOffset: Int,
    length: Int
  ): Array[Byte] = {
    val res = new Array[Byte](length)
    var i   = 0
    while (i < length) {
      res(i) = (l(i + lOffset) ^ r(i + rOffset)).toByte
      i += 1
    }
    res
  }

  private[chunk] def bitwiseNot(array: Array[Byte], offset: Int, length: Int): Array[Byte] = {
    val res = new Array[Byte](length)
    var i   = 0
    while (i < length) {
      res(i) = (~array(i + offset)).toByte
      i += 1
    }
    res
  }
}
