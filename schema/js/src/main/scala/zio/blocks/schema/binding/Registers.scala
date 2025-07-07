package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

import java.util

/**
 * Temporary storage to be used during encoding and decoding for schema-based
 * data structures. These are mutable and should be cached with thread locals,
 * fiber locals, or pools, to ensure zero-allocation during encoding / decoding.
 */
class Registers private (userRegister: RegisterOffset) {
  import RegisterOffset.RegisterOffset

  private[this] var bytes: Array[Byte]     = new Array[Byte](RegisterOffset.getBytes(userRegister))
  private[this] var objects: Array[AnyRef] = new Array[AnyRef](RegisterOffset.getObjects(userRegister))

  def getBoolean(baseOffset: RegisterOffset, relativeIndex: Int): Boolean =
    bytes(RegisterOffset.getBytes(baseOffset) + relativeIndex) != 0

  def getByte(baseOffset: RegisterOffset, relativeIndex: Int): Byte =
    bytes(RegisterOffset.getBytes(baseOffset) + relativeIndex)

  def getShort(baseOffset: RegisterOffset, relativeIndex: Int): Short = {
    val idx = RegisterOffset.getBytes(baseOffset) + relativeIndex
    (bytes(idx) & 0xff |
      (bytes(idx + 1) & 0xff) << 8).toShort
  }

  def getInt(baseOffset: RegisterOffset, relativeIndex: Int): Int = {
    val idx = RegisterOffset.getBytes(baseOffset) + relativeIndex
    bytes(idx) & 0xff |
      (bytes(idx + 1) & 0xff) << 8 |
      (bytes(idx + 2) & 0xff) << 16 |
      (bytes(idx + 3) << 24)
  }

  def getLong(baseOffset: RegisterOffset, relativeIndex: Int): Long = {
    val idx = RegisterOffset.getBytes(baseOffset) + relativeIndex
    (bytes(idx) & 0xff |
      (bytes(idx + 1) & 0xff) << 8 |
      (bytes(idx + 2) & 0xff) << 16).toLong |
      (bytes(idx + 3) & 0xff).toLong << 24 |
      (bytes(idx + 4) & 0xff).toLong << 32 |
      (bytes(idx + 5) & 0xff).toLong << 40 |
      (bytes(idx + 6) & 0xff).toLong << 48 |
      (bytes(idx + 7) & 0xff).toLong << 56
  }

  def getFloat(baseOffset: RegisterOffset, relativeIndex: Int): Float = {
    val idx = RegisterOffset.getBytes(baseOffset) + relativeIndex
    java.lang.Float.intBitsToFloat(
      bytes(idx) & 0xff |
        (bytes(idx + 1) & 0xff) << 8 |
        (bytes(idx + 2) & 0xff) << 16 |
        (bytes(idx + 3) << 24)
    )
  }

  def getDouble(baseOffset: RegisterOffset, relativeIndex: Int): Double = {
    val idx = RegisterOffset.getBytes(baseOffset) + relativeIndex
    java.lang.Double.longBitsToDouble(
      (bytes(idx) & 0xff |
        (bytes(idx + 1) & 0xff) << 8 |
        (bytes(idx + 2) & 0xff) << 16).toLong |
        (bytes(idx + 3) & 0xff).toLong << 24 |
        (bytes(idx + 4) & 0xff).toLong << 32 |
        (bytes(idx + 5) & 0xff).toLong << 40 |
        (bytes(idx + 6) & 0xff).toLong << 48 |
        (bytes(idx + 7) & 0xff).toLong << 56
    )
  }

  def getChar(baseOffset: RegisterOffset, relativeIndex: Int): Char = {
    val idx = RegisterOffset.getBytes(baseOffset) + relativeIndex
    (bytes(idx) & 0xff |
      (bytes(idx + 1) & 0xff) << 8).toChar
  }

  def getObject(baseOffset: RegisterOffset, relativeIndex: Int): AnyRef =
    objects(RegisterOffset.getObjects(baseOffset) + relativeIndex)

  def setBoolean(baseOffset: RegisterOffset, relativeIndex: Int, value: Boolean): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = if (value) (1: Byte) else (0: Byte)
  }

  def setByte(baseOffset: RegisterOffset, relativeIndex: Int, value: Byte): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value
  }

  def setShort(baseOffset: RegisterOffset, relativeIndex: Int, value: Short): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value.toByte
    bytes(absoluteIndex + 1) = (value >> 8).toByte
  }

  def setInt(baseOffset: RegisterOffset, relativeIndex: Int, value: Int): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value.toByte
    bytes(absoluteIndex + 1) = (value >> 8).toByte
    bytes(absoluteIndex + 2) = (value >> 16).toByte
    bytes(absoluteIndex + 3) = (value >> 24).toByte
  }

  def setLong(baseOffset: RegisterOffset, relativeIndex: Int, value: Long): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value.toByte
    bytes(absoluteIndex + 1) = (value >> 8).toByte
    bytes(absoluteIndex + 2) = (value >> 16).toByte
    bytes(absoluteIndex + 3) = (value >> 24).toByte
    bytes(absoluteIndex + 4) = (value >> 32).toByte
    bytes(absoluteIndex + 5) = (value >> 40).toByte
    bytes(absoluteIndex + 6) = (value >> 48).toByte
    bytes(absoluteIndex + 7) = (value >> 56).toByte
  }

  def setFloat(baseOffset: RegisterOffset, relativeIndex: Int, value: Float): Unit = {
    val bits          = java.lang.Float.floatToIntBits(value)
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 3 >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = bits.toByte
    bytes(absoluteIndex + 1) = (bits >> 8).toByte
    bytes(absoluteIndex + 2) = (bits >> 16).toByte
    bytes(absoluteIndex + 3) = (bits >> 24).toByte
  }

  def setDouble(baseOffset: RegisterOffset, relativeIndex: Int, value: Double): Unit = {
    val bits          = java.lang.Double.doubleToLongBits(value)
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 7 >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = bits.toByte
    bytes(absoluteIndex + 1) = (bits >> 8).toByte
    bytes(absoluteIndex + 2) = (bits >> 16).toByte
    bytes(absoluteIndex + 3) = (bits >> 24).toByte
    bytes(absoluteIndex + 4) = (bits >> 32).toByte
    bytes(absoluteIndex + 5) = (bits >> 40).toByte
    bytes(absoluteIndex + 6) = (bits >> 48).toByte
    bytes(absoluteIndex + 7) = (bits >> 56).toByte
  }

  def setChar(baseOffset: RegisterOffset, relativeIndex: Int, value: Char): Unit = {
    val absoluteIndex = RegisterOffset.getBytes(baseOffset) + relativeIndex
    if (absoluteIndex + 1 >= bytes.length) growBytes(absoluteIndex)
    bytes(absoluteIndex) = value.toByte
    bytes(absoluteIndex + 1) = (value >> 8).toByte
  }

  def setObject(baseOffset: RegisterOffset, relativeIndex: Int, value: AnyRef): Unit = {
    val absoluteIndex = RegisterOffset.getObjects(baseOffset) + relativeIndex
    if (absoluteIndex >= objects.length) growObjects(absoluteIndex)
    objects(absoluteIndex) = value
  }

  def setRegisters(baseOffset: RegisterOffset, registers: Registers): Unit = {
    val bytes       = registers.getBytes
    val bytesLength = bytes.length
    val byteIndex   = RegisterOffset.getObjects(baseOffset)
    if (bytesLength + byteIndex >= this.bytes.length) growBytes(bytesLength + byteIndex)
    System.arraycopy(bytes, 0, this.bytes, byteIndex, bytesLength)
    val objects       = registers.getObjects
    val objectsLength = objects.length
    val objectIndex   = RegisterOffset.getObjects(baseOffset)
    if (objectsLength + objectIndex >= this.objects.length) growObjects(objectsLength + objectIndex)
    System.arraycopy(objects, 0, this.objects, objectIndex, objectsLength)
  }

  private def getBytes: Array[Byte] = bytes

  private def getObjects: Array[AnyRef] = objects

  private[this] def growBytes(absoluteIndex: RegisterOffset): Unit =
    bytes = util.Arrays.copyOf(bytes, Math.max(bytes.length << 1, absoluteIndex + 8))

  private[this] def growObjects(absoluteIndex: RegisterOffset): Unit =
    objects = util.Arrays.copyOf(objects, Math.max(objects.length << 1, absoluteIndex + 1))
}

object Registers {
  def apply(usedRegisters: RegisterOffset): Registers = new Registers(usedRegisters)
}
