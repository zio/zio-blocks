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
