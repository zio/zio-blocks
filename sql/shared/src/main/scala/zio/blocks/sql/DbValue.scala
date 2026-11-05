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

/**
 * A value bound to a statement parameter, as a [[DbCodec]] produces it with
 * `DbCodec#toDbValues`. A [[Frag]] carries one per `?` placeholder, and
 * `SqlDialect#typeName` maps a `DbValue` to a column type for DDL.
 *
 * {{{
 * case class Product(sku: String, priceCents: Long, discontinued: Boolean) derives DbCodec
 *
 * val values = DbCodec[Product].toDbValues(Product("MUG-1", 1200L, false))
 * // Vector(DbString(MUG-1), DbLong(1200), DbBoolean(false))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-value/ DbValue]]
 */
sealed trait DbValue

/** The cases of [[DbValue]], one per supported SQL value type. */
object DbValue {

  /** SQL `NULL`. */
  case object DbNull extends DbValue

  /** An `INTEGER` value. */
  final case class DbInt(value: Int) extends DbValue

  /** A `BIGINT` value. */
  final case class DbLong(value: Long) extends DbValue

  /** A double-precision floating-point value. */
  final case class DbDouble(value: Double) extends DbValue

  /** A single-precision floating-point value. */
  final case class DbFloat(value: Float) extends DbValue

  /** A boolean value. */
  final case class DbBoolean(value: Boolean) extends DbValue

  /** A text value; JSON columns are stored as `DbString` too. */
  final case class DbString(value: String) extends DbValue

  /** An exact decimal value. */
  final case class DbBigDecimal(value: scala.BigDecimal) extends DbValue

  /** A binary value. */
  final case class DbBytes(value: Array[Byte]) extends DbValue

  /** A `SMALLINT` value. */
  final case class DbShort(value: Short) extends DbValue

  /** A byte, stored as a `SMALLINT` on PostgreSQL. */
  final case class DbByte(value: Byte) extends DbValue

  /** A single character, bound as a one-character string. */
  final case class DbChar(value: Char) extends DbValue

  /** A date without a time zone. */
  final case class DbLocalDate(value: LocalDate) extends DbValue

  /** A date and time without a time zone. */
  final case class DbLocalDateTime(value: LocalDateTime) extends DbValue

  /** A time of day without a time zone. */
  final case class DbLocalTime(value: LocalTime) extends DbValue

  /** An instant, bound as a UTC timestamp. */
  final case class DbInstant(value: Instant) extends DbValue

  /** A duration, bound as its ISO-8601 text, such as `PT1H30M`. */
  final case class DbDuration(value: Duration) extends DbValue

  /** A UUID. */
  final case class DbUUID(value: UUID) extends DbValue

  /**
   * An array whose elements have the SQL type named by `elementType`, such as
   * `varchar`; the JDBC backend binds it with `Connection#createArrayOf`.
   */
  final case class DbArray(elementType: String, elements: IndexedSeq[Any]) extends DbValue
}
