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
