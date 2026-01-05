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

  def getBoolean(offset: RegisterOffset): Boolean = bytes(RegisterOffset.getBytes(offset)) != 0

  def getByte(offset: RegisterOffset): Byte = bytes(RegisterOffset.getBytes(offset))

  def getShort(offset: RegisterOffset): Short = ByteArrayAccess.getShort(bytes, RegisterOffset.getBytes(offset))

  def getInt(offset: RegisterOffset): Int = ByteArrayAccess.getInt(bytes, RegisterOffset.getBytes(offset))

  def getLong(offset: RegisterOffset): Long = ByteArrayAccess.getLong(bytes, RegisterOffset.getBytes(offset))

  def getFloat(offset: RegisterOffset): Float = ByteArrayAccess.getFloat(bytes, RegisterOffset.getBytes(offset))

  def getDouble(offset: RegisterOffset): Double = ByteArrayAccess.getDouble(bytes, RegisterOffset.getBytes(offset))

  def getChar(offset: RegisterOffset): Char = ByteArrayAccess.getChar(bytes, RegisterOffset.getBytes(offset))

  def getObject(offset: RegisterOffset): AnyRef = objects(RegisterOffset.getObjects(offset))

  def setBoolean(offset: RegisterOffset, value: Boolean): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx >= bytes.length) growBytes(idx)
    bytes(idx) = if (value) (1: Byte) else (0: Byte)
  }

  def setByte(offset: RegisterOffset, value: Byte): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx >= bytes.length) growBytes(idx)
    bytes(idx) = value
  }

  def setShort(offset: RegisterOffset, value: Short): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 1 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setShort(bytes, idx, value)
  }

  def setInt(offset: RegisterOffset, value: Int): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 3 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setInt(bytes, idx, value)
  }

  def setLong(offset: RegisterOffset, value: Long): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 7 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setLong(bytes, idx, value)
  }

  def setFloat(offset: RegisterOffset, value: Float): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 3 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setFloat(bytes, idx, value)
  }

  def setDouble(offset: RegisterOffset, value: Double): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 7 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setDouble(bytes, idx, value)
  }

  def setChar(offset: RegisterOffset, value: Char): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 1 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setChar(bytes, idx, value)
  }

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

  def clearObjects(offset: RegisterOffset): Unit =
    java.util.Arrays.fill(objects, 0, Math.min(RegisterOffset.getObjects(offset), objects.length), null)

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
