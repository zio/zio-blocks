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
  def getBoolean(baseOffset: RegisterOffset, relativeIndex: Int): Boolean =
    bytes(RegisterOffset.getBytes(baseOffset) + relativeIndex) != 0

  @inline
  def getByte(baseOffset: RegisterOffset, relativeIndex: Int): Byte =
    bytes(RegisterOffset.getBytes(baseOffset) + relativeIndex)

  @inline
  def getShort(baseOffset: RegisterOffset, relativeIndex: Int): Short = {
    val idx = RegisterOffset.getBytes(baseOffset) + relativeIndex
    (bytes(idx) & 0xff | (bytes(idx + 1) & 0xff) << 8).toShort
  }

  @inline
  def getInt(baseOffset: RegisterOffset, relativeIndex: Int): Int = {
    val idx   = RegisterOffset.getBytes(baseOffset) + relativeIndex
    val bytes = this.bytes
    bytes(idx) & 0xff | (bytes(idx + 1) & 0xff) << 8 | (bytes(idx + 2) & 0xff) << 16 | (bytes(idx + 3) << 24)
  }

  @inline
  def getLong(baseOffset: RegisterOffset, relativeIndex: Int): Long = {
    val idx   = RegisterOffset.getBytes(baseOffset) + relativeIndex
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
  def getFloat(baseOffset: RegisterOffset, relativeIndex: Int): Float = {
    val idx   = RegisterOffset.getBytes(baseOffset) + relativeIndex
    val bytes = this.bytes
    java.lang.Float.intBitsToFloat(
      bytes(idx) & 0xff | (bytes(idx + 1) & 0xff) << 8 | (bytes(idx + 2) & 0xff) << 16 | (bytes(idx + 3) << 24)
    )
  }

  @inline
  def getDouble(baseOffset: RegisterOffset, relativeIndex: Int): Double = {
    val idx   = RegisterOffset.getBytes(baseOffset) + relativeIndex
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
  def getChar(baseOffset: RegisterOffset, relativeIndex: Int): Char = {
    val idx = RegisterOffset.getBytes(baseOffset) + relativeIndex
    (bytes(idx) & 0xff | (bytes(idx + 1) & 0xff) << 8).toChar
  }

  @inline
  def getObject(baseOffset: RegisterOffset, relativeIndex: Int): AnyRef =
    objects(RegisterOffset.getObjects(baseOffset) + relativeIndex)

  @inline
  def setBoolean(baseOffset: RegisterOffset, relativeIndex: Int, value: Boolean): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = if (value) (1: Byte) else (0: Byte)
  }

  @inline
  def setByte(baseOffset: RegisterOffset, relativeIndex: Int, value: Byte): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value
  }

  @inline
  def setShort(baseOffset: RegisterOffset, relativeIndex: Int, value: Short): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value.toByte
    bytes(absoluteIndex + 1) = (value >> 8).toByte
  }

  @inline
  def setInt(baseOffset: RegisterOffset, relativeIndex: Int, value: Int): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= this.bytes.length) growBytes(absoluteIndex)
    val bytes = this.bytes
    bytes(absoluteIndex) = value.toByte
    bytes(absoluteIndex + 1) = (value >> 8).toByte
    bytes(absoluteIndex + 2) = (value >> 16).toByte
    bytes(absoluteIndex + 3) = (value >> 24).toByte
  }

  @inline
  def setLong(baseOffset: RegisterOffset, relativeIndex: Int, value: Long): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= this.bytes.length) growBytes(absoluteIndex)
    val bytes = this.bytes
    bytes(absoluteIndex) = value.toByte
    bytes(absoluteIndex + 1) = (value >> 8).toByte
    bytes(absoluteIndex + 2) = (value >> 16).toByte
    bytes(absoluteIndex + 3) = (value >> 24).toByte
    bytes(absoluteIndex + 4) = (value >> 32).toByte
    bytes(absoluteIndex + 5) = (value >> 40).toByte
    bytes(absoluteIndex + 6) = (value >> 48).toByte
    bytes(absoluteIndex + 7) = (value >> 56).toByte
  }

  @inline
  def setFloat(baseOffset: RegisterOffset, relativeIndex: Int, value: Float): Unit = {
    val bits          = java.lang.Float.floatToIntBits(value)
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= this.bytes.length) growBytes(absoluteIndex)
    val bytes = this.bytes
    bytes(absoluteIndex) = bits.toByte
    bytes(absoluteIndex + 1) = (bits >> 8).toByte
    bytes(absoluteIndex + 2) = (bits >> 16).toByte
    bytes(absoluteIndex + 3) = (bits >> 24).toByte
  }

  @inline
  def setDouble(baseOffset: RegisterOffset, relativeIndex: Int, value: Double): Unit = {
    val bits          = java.lang.Double.doubleToLongBits(value)
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= this.bytes.length) growBytes(absoluteIndex)
    val bytes = this.bytes
    bytes(absoluteIndex) = bits.toByte
    bytes(absoluteIndex + 1) = (bits >> 8).toByte
    bytes(absoluteIndex + 2) = (bits >> 16).toByte
    bytes(absoluteIndex + 3) = (bits >> 24).toByte
    bytes(absoluteIndex + 4) = (bits >> 32).toByte
    bytes(absoluteIndex + 5) = (bits >> 40).toByte
    bytes(absoluteIndex + 6) = (bits >> 48).toByte
    bytes(absoluteIndex + 7) = (bits >> 56).toByte
  }

  @inline
  def setChar(baseOffset: RegisterOffset, relativeIndex: Int, value: Char): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value.toByte
    bytes(absoluteIndex + 1) = (value >> 8).toByte
  }

  @inline
  def setObject(baseOffset: RegisterOffset, relativeIndex: Int, value: AnyRef): Unit = {
    val absoluteIndex = RegisterOffset.getObjects(baseOffset) + relativeIndex
    if (absoluteIndex >= objects.length) growObjects(absoluteIndex)
    objects(absoluteIndex) = value
  }

  def setRegisters(baseOffset: RegisterOffset, registers: Registers): Unit = {
    val bytes       = registers.getBytes
    val bytesLength = bytes.length
    val byteIndex   = RegisterOffset.getBytes(baseOffset)
    if (bytesLength + byteIndex >= this.bytes.length) growBytes(bytesLength + byteIndex)
    System.arraycopy(bytes, 0, this.bytes, byteIndex, bytesLength)
    val objects       = registers.getObjects
    val objectsLength = objects.length
    val objectIndex   = RegisterOffset.getObjects(baseOffset)
    if (objectsLength + objectIndex >= this.objects.length) growObjects(objectsLength + objectIndex)
    System.arraycopy(objects, 0, this.objects, objectIndex, objectsLength)
  }

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
