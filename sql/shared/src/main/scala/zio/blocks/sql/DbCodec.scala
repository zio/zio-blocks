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
 * parameter in positional `readValue` / `writeValue` is 1-based (JDBC
 * convention): column 1 corresponds to `columns(0)`. Query decoding prefers the
 * label-based overload so result column order can differ from codec order as
 * long as column labels still match.
 */
trait DbCodec[A] {
  def columns: IndexedSeq[String]
  def readValue(reader: DbResultReader, startIndex: Int): A =
    readValue(reader, IndexedSeq.tabulate(columnCount)(offset => reader.columnLabel(startIndex + offset)))
  def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): A
  def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit
  def toDbValues(value: A): IndexedSeq[DbValue]
  def columnCount: Int = columns.size
}

object DbCodec {
  def apply[A](implicit codec: DbCodec[A]): DbCodec[A] = codec

  given intCodec: DbCodec[Int] = new DbCodec[Int] {
    val columns: IndexedSeq[String]                                              = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Int = reader.getInt(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Int): Unit     = writer.setInt(startIndex, value)
    def toDbValues(value: Int): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbInt(value))
  }

  given longCodec: DbCodec[Long] = new DbCodec[Long] {
    val columns: IndexedSeq[String]                                               = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Long = reader.getLong(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Long): Unit     = writer.setLong(startIndex, value)
    def toDbValues(value: Long): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbLong(value))
  }

  given stringCodec: DbCodec[String] = new DbCodec[String] {
    val columns: IndexedSeq[String]                                                 = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): String =
      reader.getString(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: String): Unit = writer.setString(startIndex, value)
    def toDbValues(value: String): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbString(value))
  }

  given booleanCodec: DbCodec[Boolean] = new DbCodec[Boolean] {
    val columns: IndexedSeq[String]                                                  = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Boolean =
      reader.getBoolean(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Boolean): Unit = writer.setBoolean(startIndex, value)
    def toDbValues(value: Boolean): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbBoolean(value))
  }

  given doubleCodec: DbCodec[Double] = new DbCodec[Double] {
    val columns: IndexedSeq[String]                                                 = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Double =
      reader.getDouble(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Double): Unit = writer.setDouble(startIndex, value)
    def toDbValues(value: Double): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbDouble(value))
  }

  given floatCodec: DbCodec[Float] = new DbCodec[Float] {
    val columns: IndexedSeq[String]                                                = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Float = reader.getFloat(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Float): Unit     = writer.setFloat(startIndex, value)
    def toDbValues(value: Float): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbFloat(value))
  }

  given shortCodec: DbCodec[Short] = new DbCodec[Short] {
    val columns: IndexedSeq[String]                                                = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Short = reader.getShort(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Short): Unit     = writer.setShort(startIndex, value)
    def toDbValues(value: Short): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbShort(value))
  }

  given byteCodec: DbCodec[Byte] = new DbCodec[Byte] {
    val columns: IndexedSeq[String]                                               = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Byte = reader.getByte(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Byte): Unit     = writer.setByte(startIndex, value)
    def toDbValues(value: Byte): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbByte(value))
  }

  given bigDecimalCodec: DbCodec[BigDecimal] = new DbCodec[BigDecimal] {
    val columns: IndexedSeq[String]                                                     = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): BigDecimal = {
      val jbd = reader.getBigDecimal(columnLabels.head)
      if (jbd != null) scala.BigDecimal(jbd) else null.asInstanceOf[BigDecimal]
    }
    def writeValue(writer: DbParamWriter, startIndex: Int, value: BigDecimal): Unit =
      writer.setBigDecimal(startIndex, value.bigDecimal)
    def toDbValues(value: BigDecimal): IndexedSeq[DbValue] = IndexedSeq(DbValue.DbBigDecimal(value))
  }
}

/**
 * Reads column values from a database result set.
 *
 * All `index` parameters are 1-based (JDBC convention). Label-based access is
 * available for order-independent decoding. After any `get*` call, use
 * `wasNull` to check whether the value was SQL NULL.
 */
trait DbResultReader {
  def getInt(index: Int): Int
  def getInt(label: String): Int
  def getLong(index: Int): Long
  def getLong(label: String): Long
  def getDouble(index: Int): Double
  def getDouble(label: String): Double
  def getFloat(index: Int): Float
  def getFloat(label: String): Float
  def getBoolean(index: Int): Boolean
  def getBoolean(label: String): Boolean
  def getString(index: Int): String
  def getString(label: String): String
  def getBigDecimal(index: Int): java.math.BigDecimal
  def getBigDecimal(label: String): java.math.BigDecimal
  def getBytes(index: Int): Array[Byte]
  def getBytes(label: String): Array[Byte]
  def getShort(index: Int): Short
  def getShort(label: String): Short
  def getByte(index: Int): Byte
  def getByte(label: String): Byte
  def getLocalDate(index: Int): java.time.LocalDate
  def getLocalDate(label: String): java.time.LocalDate
  def getLocalDateTime(index: Int): java.time.LocalDateTime
  def getLocalDateTime(label: String): java.time.LocalDateTime
  def getLocalTime(index: Int): java.time.LocalTime
  def getLocalTime(label: String): java.time.LocalTime
  def getInstant(index: Int): java.time.Instant
  def getInstant(label: String): java.time.Instant
  def getDuration(index: Int): java.time.Duration
  def getDuration(label: String): java.time.Duration
  def getUUID(index: Int): java.util.UUID
  def getUUID(label: String): java.util.UUID
  def columnLabel(index: Int): String
  def hasColumn(label: String): Boolean
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
