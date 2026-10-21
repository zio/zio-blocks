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

package zio.blocks.schema.binding

import zio.blocks.schema.ByteArrayAccess
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import java.util

/**
 * The mutable storage that [[Constructor]]s and [[Deconstructor]]s read and
 * write: a byte area for primitive values and an object area for references. A
 * value's position is a [[RegisterOffset]]: the getters and setters for
 * primitives use its byte part as the index into the byte area, and `getObject`
 * and `setObject` use its object part as the index into the object area.
 * Primitives are stored as raw little-endian bytes, without boxing.
 *
 * `Registers(usedRegisters)` allocates both areas at the sizes that
 * `usedRegisters` gives. The setters grow an area when a value does not fit.
 * The getters never grow an area: a position must be inside an area before it
 * is read, and on the JVM reading beyond an area throws an
 * `IndexOutOfBoundsException`. A position that was allocated but never written
 * reads as `0`, `false` or `null`.
 *
 * A `Registers` is not thread-safe. The JSON reader and writer each keep one as
 * a stack of record frames, so the JSON codec does not allocate registers for
 * each record.
 *
 * {{{
 * import zio.blocks.schema.binding._
 *
 * val registers = Registers(RegisterOffset(objects = 1, ints = 1))
 * registers.setObject(RegisterOffset.Zero, "Zionomicon")
 * registers.setInt(RegisterOffset(objects = 1), 2021)
 * val title = registers.getObject(RegisterOffset.Zero)     // "Zionomicon"
 * val year  = registers.getInt(RegisterOffset(objects = 1)) // 2021
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/registers/ Register System]]
 */
class Registers private (userRegister: RegisterOffset) {
  private[this] var inUse: Boolean     = false
  private[this] var bytes: Array[Byte] = {
    val bytes = RegisterOffset.getBytes(userRegister)
    if (bytes == 0) Array.emptyByteArray else new Array[Byte](bytes)
  }
  private[this] var objects: Array[AnyRef] = {
    val objects = RegisterOffset.getObjects(userRegister)
    if (objects == 0) Array.emptyObjectArray else new Array[AnyRef](objects)
  }

  /**
   * Reads the `Boolean` at the byte position of `offset`: `true` for any byte
   * other than 0.
   */
  @inline
  def getBoolean(offset: RegisterOffset): Boolean = bytes(RegisterOffset.getBytes(offset)) != 0

  /** Reads the `Byte` at the byte position of `offset`. */
  @inline
  def getByte(offset: RegisterOffset): Byte = bytes(RegisterOffset.getBytes(offset))

  /** Reads the `Short` at the byte position of `offset`. */
  @inline
  def getShort(offset: RegisterOffset): Short = ByteArrayAccess.getShort(bytes, RegisterOffset.getBytes(offset))

  /** Reads the `Int` at the byte position of `offset`. */
  @inline
  def getInt(offset: RegisterOffset): Int = ByteArrayAccess.getInt(bytes, RegisterOffset.getBytes(offset))

  /** Reads the `Long` at the byte position of `offset`. */
  @inline
  def getLong(offset: RegisterOffset): Long = ByteArrayAccess.getLong(bytes, RegisterOffset.getBytes(offset))

  /** Reads the `Float` at the byte position of `offset`. */
  @inline
  def getFloat(offset: RegisterOffset): Float = ByteArrayAccess.getFloat(bytes, RegisterOffset.getBytes(offset))

  /** Reads the `Double` at the byte position of `offset`. */
  @inline
  def getDouble(offset: RegisterOffset): Double = ByteArrayAccess.getDouble(bytes, RegisterOffset.getBytes(offset))

  /** Reads the `Char` at the byte position of `offset`. */
  @inline
  def getChar(offset: RegisterOffset): Char = ByteArrayAccess.getChar(bytes, RegisterOffset.getBytes(offset))

  /** Reads the reference in the object slot of `offset`. */
  @inline
  def getObject(offset: RegisterOffset): AnyRef = objects(RegisterOffset.getObjects(offset))

  /**
   * Writes `value` at the byte position of `offset`, as 1 for `true` and 0 for
   * `false`, and grows the byte area first when it is too small.
   */
  @inline
  def setBoolean(offset: RegisterOffset, value: Boolean): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx >= bytes.length) growBytes(idx)
    bytes(idx) = if (value) (1: Byte) else (0: Byte)
  }

  /**
   * Writes `value` as a `Byte` at the byte position of `offset`, taking 1 byte,
   * and grows the byte area first when it is too small.
   */
  @inline
  def setByte(offset: RegisterOffset, value: Byte): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx >= bytes.length) growBytes(idx)
    bytes(idx) = value
  }

  /**
   * Writes `value` as a `Short` at the byte position of `offset`, taking 2
   * bytes, and grows the byte area first when it is too small.
   */
  @inline
  def setShort(offset: RegisterOffset, value: Short): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 1 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setShort(bytes, idx, value)
  }

  /**
   * Writes `value` as an `Int` at the byte position of `offset`, taking 4
   * bytes, and grows the byte area first when it is too small.
   */
  @inline
  def setInt(offset: RegisterOffset, value: Int): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 3 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setInt(bytes, idx, value)
  }

  /**
   * Writes `value` as a `Long` at the byte position of `offset`, taking 8
   * bytes, and grows the byte area first when it is too small.
   */
  @inline
  def setLong(offset: RegisterOffset, value: Long): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 7 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setLong(bytes, idx, value)
  }

  /**
   * Writes `value` as a `Float` at the byte position of `offset`, taking 4
   * bytes, and grows the byte area first when it is too small.
   */
  @inline
  def setFloat(offset: RegisterOffset, value: Float): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 3 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setFloat(bytes, idx, value)
  }

  /**
   * Writes `value` as a `Double` at the byte position of `offset`, taking 8
   * bytes, and grows the byte area first when it is too small.
   */
  @inline
  def setDouble(offset: RegisterOffset, value: Double): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 7 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setDouble(bytes, idx, value)
  }

  /**
   * Writes `value` as a `Char` at the byte position of `offset`, taking 2
   * bytes, and grows the byte area first when it is too small.
   */
  @inline
  def setChar(offset: RegisterOffset, value: Char): Unit = {
    val idx = RegisterOffset.getBytes(offset)
    if (idx + 1 >= bytes.length) growBytes(idx)
    ByteArrayAccess.setChar(bytes, idx, value)
  }

  /**
   * Writes `value` in the object slot of `offset`, and grows the object area
   * first when it is too small.
   */
  @inline
  def setObject(offset: RegisterOffset, value: AnyRef): Unit = {
    val idx = RegisterOffset.getObjects(offset)
    if (idx >= objects.length) growObjects(idx)
    objects(idx) = value
  }

  /**
   * Copies all of `registers` into this instance at `offset`: its whole byte
   * area to the byte position of `offset` and its whole object area to the
   * object slot of `offset`, growing the areas here as needed. The whole areas
   * are copied, including any space they grew to, not only the part that was
   * written.
   */
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

  /**
   * Sets the object slots from 0 up to, but not including, the object slot of
   * `offset` to `null`, so this instance no longer holds those references. The
   * byte area is not changed.
   */
  @inline
  def clearObjects(offset: RegisterOffset): Unit =
    java.util.Arrays.fill(objects, 0, Math.min(RegisterOffset.getObjects(offset), objects.length), null)

  @inline
  private def getBytes: Array[Byte] = bytes

  @inline
  private def getObjects: Array[AnyRef] = objects

  @noinline
  private[this] def growBytes(idx: Int): Unit =
    bytes = util.Arrays.copyOf(bytes, Math.max(bytes.length << 1, idx + 8))

  @noinline
  private[this] def growObjects(idx: Int): Unit =
    objects = util.Arrays.copyOf(objects, Math.max(objects.length << 1, idx + 1))

  /**
   * Indicates whether this instance is currently borrowed from a pool and must
   * not be reused concurrently. Callers should allocate a fresh instance
   * instead when this is true.
   */
  private[blocks] def isInUse: Boolean = inUse

  /**
   * Marks this instance as in use so it can be safely reused for the next
   * encoding or decoding operation.
   */
  private[blocks] def startUse(): Unit =
    inUse = true

  /**
   * Marks this instance as no longer in use and releases all stored values so
   * that a pooled instance does not retain the previous operation's objects.
   */
  private[blocks] def endUse(): Unit = {
    clear()
    inUse = false
  }

  private[this] def clear(): Unit = {
    java.util.Arrays.fill(bytes, 0, bytes.length, (0: Byte))
    java.util.Arrays.fill(objects, 0, objects.length, null)
  }
}

/** Creates [[Registers]]. */
object Registers {

  /**
   * Returns new registers whose byte area has the byte count of `usedRegisters`
   * and whose object area has its object count. Either can be 0; the setters
   * grow the areas as needed.
   */
  @inline
  def apply(usedRegisters: RegisterOffset): Registers = new Registers(usedRegisters)
}
