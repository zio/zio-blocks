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

  /**
   * Driver-specific value that cannot be expressed as one of the standard
   * [[DbValue]] variants.
   *
   * `Raw` stays a bound parameter: the JDBC writer binds it with
   * `PreparedStatement.setObject(index, value, Types.OTHER)`, so the value is
   * never inlined into the SQL text. The [[sqlType]] name is metadata, but
   * [[SqlDialect.typeName]] returns it verbatim and DDL generation splices it
   * into the column type position, so it must be a constant type name and never
   * derived from untrusted input. JDBC drivers that understand `Types.OTHER`
   * infer the concrete type from `value` (for example a PostgreSQL `PGobject`
   * such as a pgvector value).
   *
   * '''Trust boundary.''' The value is passed to the JDBC driver as-is. It must
   * be a driver-understood object (or a `String` in the driver's text format),
   * never raw SQL. Use `Raw` only for values that have no standard `DbValue`
   * representation, and prefer the standard variants everywhere else. In
   * particular, do not replace normal `DbParam` binding with `Raw` and do not
   * use `SqlLiteral` to inline such values into the SQL text.
   *
   * {{{
   * // e.g. a org.postgresql.util.PGobject with type "vector"
   * val vec: Any = "[1.0,2.0,3.0]"
   * sql"INSERT INTO items (embedding) VALUES (${DbValue.Raw("vector", vec)})"
   * }}}
   *
   * @param sqlType
   *   the dialect-specific SQL type name, e.g. `"vector"` or `"jsonb"`
   * @param value
   *   the driver-specific value passed to `PreparedStatement.setObject`
   */
  final case class Raw(sqlType: String, value: Any) extends DbValue
}
