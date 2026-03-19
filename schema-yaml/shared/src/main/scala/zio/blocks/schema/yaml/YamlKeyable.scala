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

package zio.blocks.schema.yaml

import zio.blocks.schema.json._
import java.time._
import java.util.{Currency, UUID}

/**
 * A type class for types that can be used as Yaml keys.
 *
 * Types with a `YamlKeyable` instance correspond exactly to the types defined
 * in `PrimitiveType`:
 *   - Scala primitives: Unit, Boolean, Byte, Short, Int, Long, Float, Double,
 *     Char, String
 *   - Arbitrary precision: BigInt, BigDecimal
 *   - Java Time: DayOfWeek, Duration, Instant, LocalDate, LocalDateTime,
 *     LocalTime, Month, MonthDay, OffsetDateTime, OffsetTime, Period, Year,
 *     YearMonth, ZoneId, ZoneOffset, ZonedDateTime
 *   - Java Util: UUID, Currency
 */
trait YamlKeyable[A] {

  /**
   * Converts a value to its string representation for use as a Yaml key.
   */
  def asKey(a: A): String
}

object YamlKeyable {

  /**
   * Summons a Keyable instance for type A.
   */
  def apply[A](implicit keyable: YamlKeyable[A]): YamlKeyable[A] = keyable

  /**
   * Creates a Keyable instance from a function.
   */
  def instance[A](f: A => String): YamlKeyable[A] = new YamlKeyable[A] {
    def asKey(a: A): String = f(a)
  }

  // Scala Primitives

  implicit val unitKeyable: YamlKeyable[Unit] = instance(_ => "{}")

  implicit val booleanKeyable: YamlKeyable[Boolean] = instance(_.toString)

  implicit val byteKeyable: YamlKeyable[Byte] = instance(_.toString)

  implicit val shortKeyable: YamlKeyable[Short] = instance(_.toString)

  implicit val intKeyable: YamlKeyable[Int] = instance(_.toString)

  implicit val longKeyable: YamlKeyable[Long] = instance(_.toString)

  implicit val floatKeyable: YamlKeyable[Float] = instance(JsonBinaryCodec.floatCodec.encodeToString)

  implicit val doubleKeyable: YamlKeyable[Double] = instance(JsonBinaryCodec.doubleCodec.encodeToString)

  implicit val charKeyable: YamlKeyable[Char] = instance(_.toString)

  implicit val stringKeyable: YamlKeyable[String] = instance(identity)

  // Arbitrary Precision Numbers

  implicit val bigIntKeyable: YamlKeyable[BigInt] = instance(JsonBinaryCodec.bigIntCodec.encodeToString)

  implicit val bigDecimalKeyable: YamlKeyable[BigDecimal] = instance(JsonBinaryCodec.bigDecimalCodec.encodeToString)

  // Java Time Types

  implicit val dayOfWeekKeyable: YamlKeyable[DayOfWeek] = instance(_.toString)

  implicit val durationKeyable: YamlKeyable[Duration] = instance(Json.durationRawCodec.encodeToString)

  implicit val instantKeyable: YamlKeyable[Instant] = instance(Json.instantRawCodec.encodeToString)

  implicit val localDateKeyable: YamlKeyable[LocalDate] = instance(Json.localDateRawCodec.encodeToString)

  implicit val localDateTimeKeyable: YamlKeyable[LocalDateTime] = instance(Json.localDateTimeRawCodec.encodeToString)

  implicit val localTimeKeyable: YamlKeyable[LocalTime] = instance(Json.localTimeRawCodec.encodeToString)

  implicit val monthKeyable: YamlKeyable[Month] = instance(_.toString)

  implicit val monthDayKeyable: YamlKeyable[MonthDay] = instance(Json.monthDayRawCodec.encodeToString)

  implicit val offsetDateTimeKeyable: YamlKeyable[OffsetDateTime] = instance(Json.offsetDateTimeRawCodec.encodeToString)

  implicit val offsetTimeKeyable: YamlKeyable[OffsetTime] = instance(Json.offsetTimeRawCodec.encodeToString)

  implicit val periodKeyable: YamlKeyable[Period] = instance(Json.periodRawCodec.encodeToString)

  implicit val yearKeyable: YamlKeyable[Year] = instance(_.toString)

  implicit val yearMonthKeyable: YamlKeyable[YearMonth] = instance(_.toString)

  implicit val zoneIdKeyable: YamlKeyable[ZoneId] = instance(_.getId)

  implicit val zoneOffsetKeyable: YamlKeyable[ZoneOffset] = instance(_.getId)

  implicit val zonedDateTimeKeyable: YamlKeyable[ZonedDateTime] = instance(Json.zonedDateTimeRawCodec.encodeToString)

  // Java Util Types

  implicit val uuidKeyable: YamlKeyable[UUID] = instance(_.toString)

  implicit val currencyKeyable: YamlKeyable[Currency] = instance(_.getCurrencyCode)
}
