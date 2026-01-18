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

package zio.blocks.schema.json

import scala.scalanative.annotation.alwaysinline
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.runtime.LLVMIntrinsics._
import scala.scalanative.runtime.RawPtr
import scala.scalanative.meta.LinktimeInfo.isMultithreadingEnabled

private[json] object ByteArrayAccess {
  // Borrowed from https://github.com/scala-native/scala-native/blob/2bb9cc6f032a5b00083d0a6bbc96aba2632f61d4/nativelib/src/main/scala/scala/scalanative/runtime/MemoryLayout.scala
  @alwaysinline
  private[this] def PtrSize: Int = castRawSizeToInt(sizeOf[RawPtr])

  @alwaysinline
  private[this] def ValuesOffset: Int =
    (if (isMultithreadingEnabled) PtrSize
     else 0) + PtrSize + 8

  @alwaysinline
  private[this] def toPtr(buf: Array[Byte], pos: Int): RawPtr = elemRawPtr(castObjectToRawPtr(buf), pos + ValuesOffset)

  @alwaysinline
  def setLong(buf: Array[Byte], pos: Int, value: Long): Unit = storeLong(toPtr(buf, pos), value)

  @alwaysinline
  def getLong(buf: Array[Byte], pos: Int): Long = loadLong(toPtr(buf, pos))

  @alwaysinline
  def setInt(buf: Array[Byte], pos: Int, value: Int): Unit = storeInt(toPtr(buf, pos), value)

  @alwaysinline
  def getInt(buf: Array[Byte], pos: Int): Int = loadInt(toPtr(buf, pos))

  @alwaysinline
  def setShort(buf: Array[Byte], pos: Int, value: Short): Unit = storeShort(toPtr(buf, pos), value)

  @alwaysinline
  def setLongReversed(buf: Array[Byte], pos: Int, value: Long): Unit =
    storeLong(toPtr(buf, pos), `llvm.bswap.i64`(value))
}
