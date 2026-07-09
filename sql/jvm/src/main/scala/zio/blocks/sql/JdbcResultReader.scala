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

import java.sql.ResultSet
import java.util.UUID

private[sql] class JdbcResultReader(val underlying: ResultSet) extends DbResultReader {

  private lazy val availableColumns: Set[String] = {
    val meta    = underlying.getMetaData
    val builder = Set.newBuilder[String]
    var index   = 1
    while (index <= meta.getColumnCount) {
      builder += meta.getColumnLabel(index)
      index += 1
    }
    builder.result()
  }

  def getInt(index: Int): Int = underlying.getInt(index)

  def getInt(label: String): Int = underlying.getInt(label)

  def getLong(index: Int): Long = underlying.getLong(index)

  def getLong(label: String): Long = underlying.getLong(label)

  def getDouble(index: Int): Double = underlying.getDouble(index)

  def getDouble(label: String): Double = underlying.getDouble(label)

  def getFloat(index: Int): Float = underlying.getFloat(index)

  def getFloat(label: String): Float = underlying.getFloat(label)

  def getBoolean(index: Int): Boolean = underlying.getBoolean(index)

  def getBoolean(label: String): Boolean = underlying.getBoolean(label)

  def getString(index: Int): String = underlying.getString(index)

  def getString(label: String): String = underlying.getString(label)

  def getBigDecimal(index: Int): java.math.BigDecimal = underlying.getBigDecimal(index)

  def getBigDecimal(label: String): java.math.BigDecimal = underlying.getBigDecimal(label)

  def getBytes(index: Int): Array[Byte] = underlying.getBytes(index)

  def getBytes(label: String): Array[Byte] = underlying.getBytes(label)

  def getShort(index: Int): Short = underlying.getShort(index)

  def getShort(label: String): Short = underlying.getShort(label)

  def getByte(index: Int): Byte = underlying.getByte(index)

  def getByte(label: String): Byte = underlying.getByte(label)

  def getLocalDate(index: Int): java.time.LocalDate = underlying.getObject(index, classOf[java.time.LocalDate])

  def getLocalDate(label: String): java.time.LocalDate = underlying.getObject(label, classOf[java.time.LocalDate])

  def getLocalDateTime(index: Int): java.time.LocalDateTime =
    underlying.getObject(index, classOf[java.time.LocalDateTime])

  def getLocalDateTime(label: String): java.time.LocalDateTime =
    underlying.getObject(label, classOf[java.time.LocalDateTime])

  def getLocalTime(index: Int): java.time.LocalTime = underlying.getObject(index, classOf[java.time.LocalTime])

  def getLocalTime(label: String): java.time.LocalTime = underlying.getObject(label, classOf[java.time.LocalTime])

  def getInstant(index: Int): java.time.Instant = {
    val ldt = underlying.getObject(index, classOf[java.time.LocalDateTime])
    if (ldt == null) null else ldt.toInstant(java.time.ZoneOffset.UTC)
  }

  def getInstant(label: String): java.time.Instant = {
    val ldt = underlying.getObject(label, classOf[java.time.LocalDateTime])
    if (ldt == null) null else ldt.toInstant(java.time.ZoneOffset.UTC)
  }

  def getDuration(index: Int): java.time.Duration = {
    val s = underlying.getString(index)
    if (s == null) null else java.time.Duration.parse(s)
  }

  def getDuration(label: String): java.time.Duration = {
    val s = underlying.getString(label)
    if (s == null) null else java.time.Duration.parse(s)
  }

  def getUUID(index: Int): UUID = {
    val s = underlying.getString(index)
    if (s == null) null else UUID.fromString(s)
  }

  def getUUID(label: String): UUID = {
    val s = underlying.getString(label)
    if (s == null) null else UUID.fromString(s)
  }

  override def getArray(index: Int): java.sql.Array = underlying.getArray(index)

  override def getArray(label: String): java.sql.Array = underlying.getArray(label)

  def columnLabel(index: Int): String = underlying.getMetaData.getColumnLabel(index)

  def hasColumn(label: String): Boolean = availableColumns.contains(label)

  def wasNull: Boolean = underlying.wasNull()
}
