package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import java.util

/**
 * Temporary storage to be used during encoding and decoding for schema-based
 * data structures. These are mutable and should be cached with thread locals,
 * fiber locals, or pools, to ensure zero-allocation during encoding / decoding.
 */
class Registers private (userRegister: RegisterOffset) {
  private[this] var bytes: Array[Byte] = {
    val bytes = RegisterOffset.getBytes(userRegister)
    if (bytes == 0) Array.emptyByteArray else new Array[Byte](bytes)
  }
  private[this] var objects: Array[AnyRef] = {
    val objects = RegisterOffset.getObjects(userRegister)
    if (objects == 0) Array.emptyObjectArray else new Array[AnyRef](objects)
  }

  @inline
  def getBoolean(offset: RegisterOffset): Boolean = bytes(RegisterOffset.getBytes(offset)) != 0

  @inline
  def getByte(offset: RegisterOffset): Byte = bytes(RegisterOffset.getBytes(offset))

  @inline
  def getShort(offset: RegisterOffset): Short = {
    val idx = RegisterOffset.getBytes(offset)
    (bytes(idx) & 0xff | (bytes(idx + 1) & 0xff) << 8).toShort
  }

  @inline
  def getInt(offset: RegisterOffset): Int = {
    val idx   = RegisterOffset.getBytes(offset)
    val bytes = this.bytes
    bytes(idx) & 0xff | (bytes(idx + 1) & 0xff) << 8 | (bytes(idx + 2) & 0xff) << 16 | (bytes(idx + 3) << 24)
  }

  @inline
  def getLong(offset: RegisterOffset): Long = {
    val idx   = RegisterOffset.getBytes(offset)
    val bytes = this.bytes
    (bytes(idx) & 0xff |
      (bytes(idx + 1) & 0xff) << 8 |
      (bytes(idx + 2) & 0xff) << 16 |
      bytes(idx + 3) << 24) & 0xffffffffL |
      (bytes(idx + 4) & 0xff |
        (bytes(idx + 5) & 0xff) << 8 |
        (bytes(idx + 6) & 0xff) << 16 |
        bytes(idx + 7) << 24).toLong << 32
  }

  @inline
  def getFloat(offset: RegisterOffset): Float = {
    val idx   = RegisterOffset.getBytes(offset)
    val bytes = this.bytes
    java.lang.Float.intBitsToFloat(
      bytes(idx) & 0xff | (bytes(idx + 1) & 0xff) << 8 | (bytes(idx + 2) & 0xff) << 16 | (bytes(idx + 3) << 24)
    )
  }

  @inline
  def getDouble(offset: RegisterOffset): Double = {
    val idx   = RegisterOffset.getBytes(offset)
    val bytes = this.bytes
    java.lang.Double.longBitsToDouble(
      (bytes(idx) & 0xff |
        (bytes(idx + 1) & 0xff) << 8 |
        (bytes(idx + 2) & 0xff) << 16 |
        bytes(idx + 3) << 24) & 0xffffffffL |
        (bytes(idx + 4) & 0xff |
          (bytes(idx + 5) & 0xff) << 8 |
          (bytes(idx + 6) & 0xff) << 16 |
          bytes(idx + 7) << 24).toLong << 32
    )
  }

  @inline
  def getChar(offset: RegisterOffset): Char = {
    val idx = RegisterOffset.getBytes(offset)
    (bytes(idx) & 0xff | (bytes(idx + 1) & 0xff) << 8).toChar
  }

  @inline
  def getObject(offset: RegisterOffset): AnyRef = objects(RegisterOffset.getObjects(offset))

  @inline
  def setBoolean(offset: RegisterOffset, value: Boolean): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx >= bytes.length) growBytes(idx)
    bytes(idx) = if (value) (1: Byte) else (0: Byte)
  }

  @inline
  def setByte(offset: RegisterOffset, value: Byte): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx >= bytes.length) growBytes(idx)
    bytes(idx) = value
  }

  @inline
  def setShort(offset: RegisterOffset, value: Short): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 1 >= bytes.length) growBytes(idx)
    bytes(idx) = value.toByte
    bytes(idx + 1) = (value >> 8).toByte
  }

  @inline
  def setInt(offset: RegisterOffset, value: Int): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 3 >= this.bytes.length) growBytes(idx)
    val bytes = this.bytes
    bytes(idx) = value.toByte
    bytes(idx + 1) = (value >> 8).toByte
    bytes(idx + 2) = (value >> 16).toByte
    bytes(idx + 3) = (value >> 24).toByte
  }

  @inline
  def setLong(offset: RegisterOffset, value: Long): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 7 >= this.bytes.length) growBytes(idx)
    val bytes = this.bytes
    bytes(idx) = value.toByte
    bytes(idx + 1) = (value >> 8).toByte
    bytes(idx + 2) = (value >> 16).toByte
    bytes(idx + 3) = (value >> 24).toByte
    bytes(idx + 4) = (value >> 32).toByte
    bytes(idx + 5) = (value >> 40).toByte
    bytes(idx + 6) = (value >> 48).toByte
    bytes(idx + 7) = (value >> 56).toByte
  }

  @inline
  def setFloat(offset: RegisterOffset, value: Float): Unit = {
    val bits = java.lang.Float.floatToIntBits(value)
    val idx  = RegisterOffset.getBytes(offset)
    if (idx + 3 >= this.bytes.length) growBytes(idx)
    val bytes = this.bytes
    bytes(idx) = bits.toByte
    bytes(idx + 1) = (bits >> 8).toByte
    bytes(idx + 2) = (bits >> 16).toByte
    bytes(idx + 3) = (bits >> 24).toByte
  }

  @inline
  def setDouble(offset: RegisterOffset, value: Double): Unit = {
    val bits = java.lang.Double.doubleToLongBits(value)
    val idx  = RegisterOffset.getBytes(offset)
    if (idx + 7 >= this.bytes.length) growBytes(idx)
    val bytes = this.bytes
    bytes(idx) = bits.toByte
    bytes(idx + 1) = (bits >> 8).toByte
    bytes(idx + 2) = (bits >> 16).toByte
    bytes(idx + 3) = (bits >> 24).toByte
    bytes(idx + 4) = (bits >> 32).toByte
    bytes(idx + 5) = (bits >> 40).toByte
    bytes(idx + 6) = (bits >> 48).toByte
    bytes(idx + 7) = (bits >> 56).toByte
  }

  @inline
  def setChar(offset: RegisterOffset, value: Char): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 1 >= bytes.length) growBytes(idx)
    bytes(idx) = value.toByte
    bytes(idx + 1) = (value >> 8).toByte
  }

  @inline
  def setObject(offset: RegisterOffset, value: AnyRef): Unit = {
    val idx = RegisterOffset.getObjects(offset)
    if (idx >= objects.length) growObjects(idx)
    objects(idx) = value
  }

  def setRegisters(offset: RegisterOffset, registers: Registers): Unit = {
    val bytes    = registers.getBytes
    val bytesLen = bytes.length
    val byteIdx  = RegisterOffset.getBytes(offset)
    if (bytesLen + byteIdx >= this.bytes.length) growBytes(bytesLen + byteIdx)
    System.arraycopy(bytes, 0, this.bytes, byteIdx, bytesLen)
    val objects    = registers.getObjects
    val objectsLen = objects.length
    val objectIdx  = RegisterOffset.getObjects(offset)
    if (objectsLen + objectIdx >= this.objects.length) growObjects(objectsLen + objectIdx)
    System.arraycopy(objects, 0, this.objects, objectIdx, objectsLen)
  }

  @inline
  def clearObjects(offset: RegisterOffset): Unit =
    java.util.Arrays.fill(objects, 0, Math.min(RegisterOffset.getObjects(offset), objects.length), null)

  @inline
  private def getBytes: Array[Byte] = bytes

  @inline
  private def getObjects: Array[AnyRef] = objects

  @noinline
  private[this] def growBytes(absoluteIndex: RegisterOffset): Unit =
    bytes = util.Arrays.copyOf(bytes, Math.max(bytes.length << 1, absoluteIndex + 8))

  @noinline
  private[this] def growObjects(absoluteIndex: RegisterOffset): Unit =
    objects = util.Arrays.copyOf(objects, Math.max(objects.length << 1, absoluteIndex + 1))
}

object Registers {
  @inline
  def apply(usedRegisters: RegisterOffset): Registers = new Registers(usedRegisters)
}
