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

  private val utcCalendar: java.util.Calendar = {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.setLenient(false)
    cal
  }

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

  /**
   * Per-row column null bitmap. JDBC's `wasNull()` only reflects the last
   * column read, so every getter records its `wasNull` result here keyed by
   * column (index-based reads into [[nullBitmap]], label-based reads into
   * [[nullLabels]]). `isNull` then answers from the recorded bitmap without
   * touching the driver again. [[beginRow]] resets the bitmap when the result
   * set advances to a new row.
   */
  private val nullBitmap: java.util.BitSet          = new java.util.BitSet
  private val nullLabels: java.util.HashSet[String] = new java.util.HashSet[String]

  /**
   * Resets the per-row null bitmap. Called by `JdbcResultSet.next()` when the
   * result set advances to a new row.
   */
  private[sql] def beginRow(): Unit = {
    nullBitmap.clear()
    nullLabels.clear()
  }

  @inline private def recordWasNull(index: Int): Unit =
    if (underlying.wasNull()) nullBitmap.set(index)

  @inline private def recordLabelWasNull(label: String): Unit =
    if (underlying.wasNull()) nullLabels.add(label)

  def getInt(index: Int): Int = {
    val v = underlying.getInt(index)
    recordWasNull(index)
    v
  }

  def getInt(label: String): Int = {
    val v = underlying.getInt(label)
    recordLabelWasNull(label)
    v
  }

  def getLong(index: Int): Long = {
    val v = underlying.getLong(index)
    recordWasNull(index)
    v
  }

  def getLong(label: String): Long = {
    val v = underlying.getLong(label)
    recordLabelWasNull(label)
    v
  }

  def getDouble(index: Int): Double = {
    val v = underlying.getDouble(index)
    recordWasNull(index)
    v
  }

  def getDouble(label: String): Double = {
    val v = underlying.getDouble(label)
    recordLabelWasNull(label)
    v
  }

  def getFloat(index: Int): Float = {
    val v = underlying.getFloat(index)
    recordWasNull(index)
    v
  }

  def getFloat(label: String): Float = {
    val v = underlying.getFloat(label)
    recordLabelWasNull(label)
    v
  }

  def getBoolean(index: Int): Boolean = {
    val v = underlying.getBoolean(index)
    recordWasNull(index)
    v
  }

  def getBoolean(label: String): Boolean = {
    val v = underlying.getBoolean(label)
    recordLabelWasNull(label)
    v
  }

  def getString(index: Int): String = {
    val v = underlying.getString(index)
    recordWasNull(index)
    v
  }

  def getString(label: String): String = {
    val v = underlying.getString(label)
    recordLabelWasNull(label)
    v
  }

  def getBigDecimal(index: Int): java.math.BigDecimal = {
    val v = underlying.getBigDecimal(index)
    recordWasNull(index)
    v
  }

  def getBigDecimal(label: String): java.math.BigDecimal = {
    val v = underlying.getBigDecimal(label)
    recordLabelWasNull(label)
    v
  }

  def getBytes(index: Int): Array[Byte] = {
    val v = underlying.getBytes(index)
    recordWasNull(index)
    v
  }

  def getBytes(label: String): Array[Byte] = {
    val v = underlying.getBytes(label)
    recordLabelWasNull(label)
    v
  }

  def getShort(index: Int): Short = {
    val v = underlying.getShort(index)
    recordWasNull(index)
    v
  }

  def getShort(label: String): Short = {
    val v = underlying.getShort(label)
    recordLabelWasNull(label)
    v
  }

  def getByte(index: Int): Byte = {
    val v = underlying.getByte(index)
    recordWasNull(index)
    v
  }

  def getByte(label: String): Byte = {
    val v = underlying.getByte(label)
    recordLabelWasNull(label)
    v
  }

  def getLocalDate(index: Int): java.time.LocalDate = {
    val v = underlying.getObject(index, classOf[java.time.LocalDate])
    recordWasNull(index)
    v
  }

  def getLocalDate(label: String): java.time.LocalDate = {
    val v = underlying.getObject(label, classOf[java.time.LocalDate])
    recordLabelWasNull(label)
    v
  }

  def getLocalDateTime(index: Int): java.time.LocalDateTime = {
    val v = underlying.getObject(index, classOf[java.time.LocalDateTime])
    recordWasNull(index)
    v
  }

  def getLocalDateTime(label: String): java.time.LocalDateTime = {
    val v = underlying.getObject(label, classOf[java.time.LocalDateTime])
    recordLabelWasNull(label)
    v
  }

  def getLocalTime(index: Int): java.time.LocalTime = {
    val v = underlying.getObject(index, classOf[java.time.LocalTime])
    recordWasNull(index)
    v
  }

  def getLocalTime(label: String): java.time.LocalTime = {
    val v = underlying.getObject(label, classOf[java.time.LocalTime])
    recordLabelWasNull(label)
    v
  }

  def getInstant(index: Int): java.time.Instant = {
    val ts = underlying.getTimestamp(index, utcCalendar)
    recordWasNull(index)
    if (ts == null) null else ts.toInstant
  }

  def getInstant(label: String): java.time.Instant = {
    val ts = underlying.getTimestamp(label, utcCalendar)
    recordLabelWasNull(label)
    if (ts == null) null else ts.toInstant
  }

  def getDuration(index: Int): java.time.Duration = {
    val s = underlying.getString(index)
    recordWasNull(index)
    if (s == null) null else java.time.Duration.parse(s)
  }

  def getDuration(label: String): java.time.Duration = {
    val s = underlying.getString(label)
    recordLabelWasNull(label)
    if (s == null) null else java.time.Duration.parse(s)
  }

  def getUUID(index: Int): UUID = {
    val s = underlying.getString(index)
    recordWasNull(index)
    if (s == null) null else UUID.fromString(s)
  }

  def getUUID(label: String): UUID = {
    val s = underlying.getString(label)
    recordLabelWasNull(label)
    if (s == null) null else UUID.fromString(s)
  }

  override def getArray(index: Int): Array[String] = {
    val sqlArray = underlying.getArray(index)
    recordWasNull(index)
    if (sqlArray == null) null
    else
      try sqlArray.getArray().asInstanceOf[Array[String]]
      finally sqlArray.free()
  }

  override def getArray(label: String): Array[String] = {
    val sqlArray = underlying.getArray(label)
    recordLabelWasNull(label)
    if (sqlArray == null) null
    else
      try sqlArray.getArray().asInstanceOf[Array[String]]
      finally sqlArray.free()
  }

  def columnLabel(index: Int): String = underlying.getMetaData.getColumnLabel(index)

  def hasColumn(label: String): Boolean = availableColumns.contains(label)

  def wasNull: Boolean = underlying.wasNull()

  override def isNull(index: Int): Boolean = nullBitmap.get(index)

  override def isNull(label: String): Boolean = nullLabels.contains(label)
}
