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

package zio.blocks.schema.msgpack

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.binding.{Registers, RegisterOffset}

import java.nio.charset.StandardCharsets
import scala.annotation.switch

private[msgpack] final class MessagePackFieldInfo(val span: DynamicOptic.Node.Field, val idx: Int) {
  private[this] var codec: MessagePackBinaryCodec[?]      = null
  private[this] var _name: String                         = null
  private[this] var offset: RegisterOffset.RegisterOffset = 0
  private[this] var encodedName: Array[Byte]              = null

  def name: String = _name

  def setName(name: String): Unit = {
    this._name = name
    val utf8 = name.getBytes(StandardCharsets.UTF_8)
    val len  = utf8.length
    encodedName = if (len <= 31) {
      val arr = new Array[Byte](1 + len)
      arr(0) = (0xa0 | len).toByte // fixstr header
      System.arraycopy(utf8, 0, arr, 1, len)
      arr
    } else if (len <= 0xff) {
      val arr = new Array[Byte](2 + len)
      arr(0) = 0xd9.toByte // str8
      arr(1) = len.toByte
      System.arraycopy(utf8, 0, arr, 2, len)
      arr
    } else if (len <= 0xffff) {
      val arr = new Array[Byte](3 + len)
      arr(0) = 0xda.toByte // str16
      arr(1) = (len >> 8).toByte
      arr(2) = len.toByte
      System.arraycopy(utf8, 0, arr, 3, len)
      arr
    } else {
      val arr = new Array[Byte](5 + len)
      arr(0) = 0xdb.toByte // str32
      arr(1) = (len >> 24).toByte
      arr(2) = (len >> 16).toByte
      arr(3) = (len >> 8).toByte
      arr(4) = len.toByte
      System.arraycopy(utf8, 0, arr, 5, len)
      arr
    }
  }

  def writeEncodedName(out: MessagePackWriter): Unit = out.writeRaw(encodedName)

  def setCodec(codec: MessagePackBinaryCodec[?]): Unit = this.codec = codec

  def setOffset(offset: RegisterOffset.RegisterOffset): Unit = this.offset = offset

  def usedRegisters: RegisterOffset.RegisterOffset = RegisterOffset.add(codec.valueOffset, offset)

  def readValue(in: MessagePackReader, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val offset =
      if (top == 0L) this.offset
      else RegisterOffset.add(this.offset, top)
    (codec.valueType: @switch) match {
      case 0 => regs.setObject(offset, codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].decodeValue(in))
      case 1 => regs.setInt(offset, codec.asInstanceOf[MessagePackBinaryCodec[Int]].decodeValue(in))
      case 2 => regs.setLong(offset, codec.asInstanceOf[MessagePackBinaryCodec[Long]].decodeValue(in))
      case 3 => regs.setFloat(offset, codec.asInstanceOf[MessagePackBinaryCodec[Float]].decodeValue(in))
      case 4 => regs.setDouble(offset, codec.asInstanceOf[MessagePackBinaryCodec[Double]].decodeValue(in))
      case 5 => regs.setBoolean(offset, codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].decodeValue(in))
      case 6 => regs.setByte(offset, codec.asInstanceOf[MessagePackBinaryCodec[Byte]].decodeValue(in))
      case 7 => regs.setChar(offset, codec.asInstanceOf[MessagePackBinaryCodec[Char]].decodeValue(in))
      case 8 => regs.setShort(offset, codec.asInstanceOf[MessagePackBinaryCodec[Short]].decodeValue(in))
      case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].decodeValue(in)
    }
  }

  def writeValue(out: MessagePackWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val offset =
      if (top == 0L) this.offset
      else RegisterOffset.add(this.offset, top)
    (codec.valueType: @switch) match {
      case 0 => codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].encodeValue(regs.getObject(offset), out)
      case 1 => codec.asInstanceOf[MessagePackBinaryCodec[Int]].encodeValue(regs.getInt(offset), out)
      case 2 => codec.asInstanceOf[MessagePackBinaryCodec[Long]].encodeValue(regs.getLong(offset), out)
      case 3 => codec.asInstanceOf[MessagePackBinaryCodec[Float]].encodeValue(regs.getFloat(offset), out)
      case 4 => codec.asInstanceOf[MessagePackBinaryCodec[Double]].encodeValue(regs.getDouble(offset), out)
      case 5 => codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].encodeValue(regs.getBoolean(offset), out)
      case 6 => codec.asInstanceOf[MessagePackBinaryCodec[Byte]].encodeValue(regs.getByte(offset), out)
      case 7 => codec.asInstanceOf[MessagePackBinaryCodec[Char]].encodeValue(regs.getChar(offset), out)
      case 8 => codec.asInstanceOf[MessagePackBinaryCodec[Short]].encodeValue(regs.getShort(offset), out)
      case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].encodeValue((), out)
    }
  }
}
