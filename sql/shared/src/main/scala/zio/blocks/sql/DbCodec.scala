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

package zio.blocks.sql

/**
 * Bidirectional codec between a Scala value `A` and one or more database
 * columns.
 *
 * Column ordering matches the `columns` [[IndexedSeq]]. The `startIndex`
 * parameter in `readValue` / `writeValue` is 1-based (JDBC convention): column
 * 1 corresponds to `columns(0)`.
 */
trait DbCodec[A] {
  def columns: IndexedSeq[String]
  def readValue(reader: DbResultReader, startIndex: Int): A
  def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit
  def toDbValues(value: A): IndexedSeq[DbValue]
  def columnCount: Int = columns.size
}

object DbCodec {
  def apply[A](implicit codec: DbCodec[A]): DbCodec[A] = codec
}

/**
 * Reads column values from a database result set.
 *
 * All `index` parameters are 1-based (JDBC convention). After any `get*` call,
 * use `wasNull` to check whether the value was SQL NULL.
 */
trait DbResultReader {
  def getInt(index: Int): Int
  def getLong(index: Int): Long
  def getDouble(index: Int): Double
  def getFloat(index: Int): Float
  def getBoolean(index: Int): Boolean
  def getString(index: Int): String
  def getBigDecimal(index: Int): java.math.BigDecimal
  def getBytes(index: Int): Array[Byte]
  def getShort(index: Int): Short
  def getByte(index: Int): Byte
  def getLocalDate(index: Int): java.time.LocalDate
  def getLocalDateTime(index: Int): java.time.LocalDateTime
  def getLocalTime(index: Int): java.time.LocalTime
  def getInstant(index: Int): java.time.Instant
  def getDuration(index: Int): java.time.Duration
  def getUUID(index: Int): java.util.UUID
  def wasNull: Boolean
}

/**
 * Writes parameter values to a prepared statement.
 *
 * All `index` parameters are 1-based (JDBC convention). Use `setNull` to write
 * SQL NULL for a given parameter index.
 */
trait DbParamWriter {
  def setInt(index: Int, value: Int): Unit
  def setLong(index: Int, value: Long): Unit
  def setDouble(index: Int, value: Double): Unit
  def setFloat(index: Int, value: Float): Unit
  def setBoolean(index: Int, value: Boolean): Unit
  def setString(index: Int, value: String): Unit
  def setBigDecimal(index: Int, value: java.math.BigDecimal): Unit
  def setBytes(index: Int, value: Array[Byte]): Unit
  def setShort(index: Int, value: Short): Unit
  def setByte(index: Int, value: Byte): Unit
  def setLocalDate(index: Int, value: java.time.LocalDate): Unit
  def setLocalDateTime(index: Int, value: java.time.LocalDateTime): Unit
  def setLocalTime(index: Int, value: java.time.LocalTime): Unit
  def setInstant(index: Int, value: java.time.Instant): Unit
  def setDuration(index: Int, value: java.time.Duration): Unit
  def setUUID(index: Int, value: java.util.UUID): Unit
  def setNull(index: Int, sqlType: Int): Unit
}
