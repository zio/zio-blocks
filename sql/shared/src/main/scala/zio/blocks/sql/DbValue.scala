package zio.blocks.sql

import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

sealed trait DbValue

object DbValue {
  case object DbNull                                     extends DbValue
  final case class DbInt(value: Int)                     extends DbValue
  final case class DbLong(value: Long)                   extends DbValue
  final case class DbDouble(value: Double)               extends DbValue
  final case class DbFloat(value: Float)                 extends DbValue
  final case class DbBoolean(value: Boolean)             extends DbValue
  final case class DbString(value: String)               extends DbValue
  final case class DbBigDecimal(value: scala.BigDecimal) extends DbValue
  final case class DbBytes(value: Array[Byte])           extends DbValue
  final case class DbShort(value: Short)                 extends DbValue
  final case class DbByte(value: Byte)                   extends DbValue
  final case class DbChar(value: Char)                   extends DbValue
  final case class DbLocalDate(value: LocalDate)         extends DbValue
  final case class DbLocalDateTime(value: LocalDateTime) extends DbValue
  final case class DbLocalTime(value: LocalTime)         extends DbValue
  final case class DbInstant(value: Instant)             extends DbValue
  final case class DbDuration(value: Duration)           extends DbValue
  final case class DbUUID(value: UUID)                   extends DbValue
}
