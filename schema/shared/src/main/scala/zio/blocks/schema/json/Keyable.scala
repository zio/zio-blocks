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

package zio.blocks.schema.json

import java.time._
import java.util.{Currency, UUID}

/**
 * A type class for types that can be used as JSON object keys or interpolated
 * inside JSON string literals.
 *
 * Types with a `Keyable` instance correspond exactly to the types defined in
 * `PrimitiveType`:
 *   - Scala primitives: Unit, Boolean, Byte, Short, Int, Long, Float, Double,
 *     Char, String
 *   - Arbitrary precision: BigInt, BigDecimal
 *   - Java Time: DayOfWeek, Duration, Instant, LocalDate, LocalDateTime,
 *     LocalTime, Month, MonthDay, OffsetDateTime, OffsetTime, Period, Year,
 *     YearMonth, ZoneId, ZoneOffset, ZonedDateTime
 *   - Java Util: UUID, Currency
 */
trait Keyable[A] {

  /**
   * Converts a value to its string representation for use as a JSON key.
   */
  def asKey(a: A): String
}

object Keyable {

  /**
   * Summons a Keyable instance for type A.
   */
  def apply[A](implicit keyable: Keyable[A]): Keyable[A] = keyable

  /**
   * Creates a Keyable instance from a function.
   */
  def instance[A](f: A => String): Keyable[A] = new Keyable[A] {
    def asKey(a: A): String = f(a)
  }

  // Scala Primitives

  implicit val unitKeyable: Keyable[Unit] = instance(_ => "{}")

  implicit val booleanKeyable: Keyable[Boolean] = instance(_.toString)

  implicit val byteKeyable: Keyable[Byte] = instance(_.toString)

  implicit val shortKeyable: Keyable[Short] = instance(_.toString)

  implicit val intKeyable: Keyable[Int] = instance(_.toString)

  implicit val longKeyable: Keyable[Long] = instance(_.toString)

  implicit val floatKeyable: Keyable[Float] = instance(JsonBinaryCodec.floatCodec.encodeToString)

  implicit val doubleKeyable: Keyable[Double] = instance(JsonBinaryCodec.doubleCodec.encodeToString)

  implicit val charKeyable: Keyable[Char] = instance(_.toString)

  implicit val stringKeyable: Keyable[String] = instance(identity)

  // Arbitrary Precision Numbers

  implicit val bigIntKeyable: Keyable[BigInt] = instance(JsonBinaryCodec.bigIntCodec.encodeToString)

  implicit val bigDecimalKeyable: Keyable[BigDecimal] = instance(JsonBinaryCodec.bigDecimalCodec.encodeToString)

  // Java Time Types

  implicit val dayOfWeekKeyable: Keyable[DayOfWeek] = instance(_.toString)

  implicit val durationKeyable: Keyable[Duration] = instance(Json.durationRawCodec.encodeToString)

  implicit val instantKeyable: Keyable[Instant] = instance(Json.instantRawCodec.encodeToString)

  implicit val localDateKeyable: Keyable[LocalDate] = instance(Json.localDateRawCodec.encodeToString)

  implicit val localDateTimeKeyable: Keyable[LocalDateTime] = instance(Json.localDateTimeRawCodec.encodeToString)

  implicit val localTimeKeyable: Keyable[LocalTime] = instance(Json.localTimeRawCodec.encodeToString)

  implicit val monthKeyable: Keyable[Month] = instance(_.toString)

  implicit val monthDayKeyable: Keyable[MonthDay] = instance(Json.monthDayRawCodec.encodeToString)

  implicit val offsetDateTimeKeyable: Keyable[OffsetDateTime] = instance(Json.offsetDateTimeRawCodec.encodeToString)

  implicit val offsetTimeKeyable: Keyable[OffsetTime] = instance(Json.offsetTimeRawCodec.encodeToString)

  implicit val periodKeyable: Keyable[Period] = instance(Json.periodRawCodec.encodeToString)

  implicit val yearKeyable: Keyable[Year] = instance(_.toString)

  implicit val yearMonthKeyable: Keyable[YearMonth] = instance(_.toString)

  implicit val zoneIdKeyable: Keyable[ZoneId] = instance(_.getId)

  implicit val zoneOffsetKeyable: Keyable[ZoneOffset] = instance(_.getId)

  implicit val zonedDateTimeKeyable: Keyable[ZonedDateTime] = instance(Json.zonedDateTimeRawCodec.encodeToString)

  // Java Util Types

  implicit val uuidKeyable: Keyable[UUID] = instance(_.toString)
  // In Currency use getCurrencyCode
  implicit val currencyKeyable: Keyable[Currency] = instance(_.getCurrencyCode)
}
