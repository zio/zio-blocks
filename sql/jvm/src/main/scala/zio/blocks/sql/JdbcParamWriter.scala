package zio.blocks.sql

import java.sql.PreparedStatement

class JdbcParamWriter(val underlying: PreparedStatement) extends DbParamWriter {

  def setInt(index: Int, value: Int): Unit = underlying.setInt(index, value)

  def setLong(index: Int, value: Long): Unit = underlying.setLong(index, value)

  def setDouble(index: Int, value: Double): Unit = underlying.setDouble(index, value)

  def setFloat(index: Int, value: Float): Unit = underlying.setFloat(index, value)

  def setBoolean(index: Int, value: Boolean): Unit = underlying.setBoolean(index, value)

  def setString(index: Int, value: String): Unit = underlying.setString(index, value)

  def setBigDecimal(index: Int, value: java.math.BigDecimal): Unit = underlying.setBigDecimal(index, value)

  def setBytes(index: Int, value: Array[Byte]): Unit = underlying.setBytes(index, value)

  def setShort(index: Int, value: Short): Unit = underlying.setShort(index, value)

  def setByte(index: Int, value: Byte): Unit = underlying.setByte(index, value)

  def setLocalDate(index: Int, value: java.time.LocalDate): Unit = underlying.setObject(index, value)

  def setLocalDateTime(index: Int, value: java.time.LocalDateTime): Unit = underlying.setObject(index, value)

  def setLocalTime(index: Int, value: java.time.LocalTime): Unit = underlying.setObject(index, value)

  def setInstant(index: Int, value: java.time.Instant): Unit =
    underlying.setTimestamp(index, java.sql.Timestamp.from(value))

  def setDuration(index: Int, value: java.time.Duration): Unit = underlying.setString(index, value.toString)

  def setUUID(index: Int, value: java.util.UUID): Unit = underlying.setObject(index, value)

  def setNull(index: Int, sqlType: Int): Unit = underlying.setNull(index, sqlType)
}
