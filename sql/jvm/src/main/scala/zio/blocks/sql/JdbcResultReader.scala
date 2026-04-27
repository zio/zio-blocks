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

class JdbcResultReader(val underlying: ResultSet) extends DbResultReader {

  def getInt(index: Int): Int = underlying.getInt(index)

  def getLong(index: Int): Long = underlying.getLong(index)

  def getDouble(index: Int): Double = underlying.getDouble(index)

  def getFloat(index: Int): Float = underlying.getFloat(index)

  def getBoolean(index: Int): Boolean = underlying.getBoolean(index)

  def getString(index: Int): String = underlying.getString(index)

  def getBigDecimal(index: Int): java.math.BigDecimal = underlying.getBigDecimal(index)

  def getBytes(index: Int): Array[Byte] = underlying.getBytes(index)

  def getShort(index: Int): Short = underlying.getShort(index)

  def getByte(index: Int): Byte = underlying.getByte(index)

  def getLocalDate(index: Int): java.time.LocalDate = underlying.getObject(index, classOf[java.time.LocalDate])

  def getLocalDateTime(index: Int): java.time.LocalDateTime =
    underlying.getObject(index, classOf[java.time.LocalDateTime])

  def getLocalTime(index: Int): java.time.LocalTime = underlying.getObject(index, classOf[java.time.LocalTime])

  def getInstant(index: Int): java.time.Instant = {
    val ts = underlying.getTimestamp(index)
    if (ts == null) null else ts.toInstant
  }

  def getDuration(index: Int): java.time.Duration = {
    val s = underlying.getString(index)
    if (s == null) null else java.time.Duration.parse(s)
  }

  def getUUID(index: Int): UUID = {
    val s = underlying.getString(index)
    if (s == null) null else UUID.fromString(s)
  }

  def wasNull: Boolean = underlying.wasNull()
}
