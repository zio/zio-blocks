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

package zio.blocks.schema

import zio.test.Gen
import java.time._
import scala.util.Try
import scala.jdk.CollectionConverters._

object JavaTimeGen {
  val genDayOfWeek: Gen[Any, DayOfWeek] = Gen.int(1, 7).map(DayOfWeek.of)
  val genDuration: Gen[Any, Duration]   = Gen.oneOf(
    Gen.long(Long.MinValue / 86400, Long.MaxValue / 86400).map(Duration.ofDays),
    Gen.long(Long.MinValue / 3600, Long.MaxValue / 3600).map(Duration.ofHours),
    Gen.long(Long.MinValue / 60, Long.MaxValue / 60).map(Duration.ofMinutes),
    Gen.long(Long.MinValue, Long.MaxValue).map(Duration.ofSeconds),
    for {
      seconds        <- Gen.long(Long.MinValue, Long.MaxValue)
      nanoAdjustment <- Gen.long(0L, Long.MaxValue)
    } yield Duration.ofSeconds(seconds, nanoAdjustment)
  )
  val genYear: Gen[Any, Year]       = Gen.oneOf(Gen.int(-9999, 9999), Gen.int(-999999999, 999999999)).map(Year.of)
  val genInstant: Gen[Any, Instant] = for {
    epochSecond     <- Gen.long(Instant.MIN.getEpochSecond, Instant.MAX.getEpochSecond)
    nanoAdjustment  <- Gen.long(Long.MinValue, Long.MaxValue)
    fallbackInstant <- Gen.elements(Instant.MIN, Instant.EPOCH, Instant.MAX)
  } yield Try(Instant.ofEpochSecond(epochSecond, nanoAdjustment)).getOrElse(fallbackInstant)
  val genLocalDate: Gen[Any, LocalDate] = for {
    year  <- genYear
    month <- Gen.int(1, 12)
    day   <- Gen.int(1, Month.of(month).length(year.isLeap))
  } yield LocalDate.of(year.getValue, month, day)
  val genLocalTime: Gen[Any, LocalTime] = for {
    hour   <- Gen.int(0, 23)
    minute <- Gen.int(0, 59)
    second <- Gen.int(0, 59)
    nano   <- Gen.oneOf(
              Gen.int(0, 0),
              Gen.int(1, 999).map(_ * 1000000),
              Gen.int(1, 999999).map(_ * 1000),
              Gen.int(1, 999999999)
            )
  } yield LocalTime.of(hour, minute, second, nano)
  val genLocalDateTime: Gen[Any, LocalDateTime] = for {
    localDate <- genLocalDate
    localTime <- genLocalTime
  } yield LocalDateTime.of(localDate, localTime)
  val genMonth: Gen[Any, Month] = for {
    month <- Gen.int(1, 12)
  } yield Month.of(month)
  val genMonthDay: Gen[Any, MonthDay] = for {
    month <- Gen.int(1, 12)
    day   <- Gen.int(1, 29)
  } yield MonthDay.of(month, day)
  val genZoneOffset: Gen[Any, ZoneOffset] = Gen.oneOf(
    Gen.int(-18, 18).map(ZoneOffset.ofHours),
    Gen.int(-18 * 60, 18 * 60).map(x => ZoneOffset.ofHoursMinutes(x / 60, x % 60)),
    Gen.int(-18 * 60 * 60, 18 * 60 * 60).map(ZoneOffset.ofTotalSeconds)
  )
  val genOffsetDateTime: Gen[Any, OffsetDateTime] = for {
    localDateTime <- genLocalDateTime
    zoneOffset    <- genZoneOffset
  } yield OffsetDateTime.of(localDateTime, zoneOffset)
  val genOffsetTime: Gen[Any, OffsetTime] = for {
    localTime  <- genLocalTime
    zoneOffset <- genZoneOffset
  } yield OffsetTime.of(localTime, zoneOffset)
  val genPeriod: Gen[Any, Period] = for {
    year  <- Gen.int
    month <- Gen.int
    day   <- Gen.int
  } yield Period.of(year, month, day)
  // Note: genYear produces years outside the 4-digit range,
  // YearMonth.parse() only handles 4-digit years. Using constrained year range here.
  val genYearMonth: Gen[Any, YearMonth] = for {
    year  <- Gen.int(-9999, 9999)
    month <- Gen.int(1, 12)
  } yield YearMonth.of(year, month)
  val genZoneId: Gen[Any, ZoneId] = Gen.oneOf(
    genZoneOffset,
    genZoneOffset.map(zo => ZoneId.of(zo.toString.replace(":", ""))),
    genZoneOffset.map(zo => ZoneId.ofOffset("UT", zo)),
    genZoneOffset.map(zo => ZoneId.ofOffset("UTC", zo)),
    genZoneOffset.map(zo => ZoneId.ofOffset("GMT", zo)),
    Gen.elements(ZoneId.getAvailableZoneIds.asScala.toSeq: _*).map(ZoneId.of),
    Gen.elements(ZoneId.SHORT_IDS.values().asScala.toSeq: _*).map(ZoneId.of)
  )
  val genZonedDateTime: Gen[Any, ZonedDateTime] = for {
    localDateTime <- genLocalDateTime
    zoneId        <- genZoneId
  } yield ZonedDateTime.of(localDateTime, zoneId)
}
