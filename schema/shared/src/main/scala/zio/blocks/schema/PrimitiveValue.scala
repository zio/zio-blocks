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

import zio.blocks.chunk.Chunk
import zio.blocks.typeid.TypeId

/**
 * A `PrimitiveValue` is a scalar value inside a [[DynamicValue]], which
 * `DynamicValue.Primitive` wraps. There is one case per [[PrimitiveType]], and
 * each case keeps the exact Scala or Java type of its value: `Int(2021)` and
 * `Long(2021L)` are different values.
 *
 * Primitive values are ordered: two values of the same case by their value, and
 * values of different cases by the order of the cases, from `Unit` to `UUID`
 * (see `typeIndex`). `PrimitiveValue.ordering` and `DynamicValue#compare` use
 * this order. `Int` values are compared by subtraction, which overflows, so two
 * `Int` values that are more than `Int.MaxValue` apart compare the wrong way
 * round.
 *
 * {{{
 * val title = PrimitiveValue.String("Zionomicon")
 * val year  = PrimitiveValue.Int(2021)
 *
 * val kind   = year.primitiveType                       // Int(None)
 * val before = year < PrimitiveValue.Int(2024)          // true
 * val sorted = List[PrimitiveValue](title, year).sorted // List(Int(2021), String(Zionomicon))
 * val value  = DynamicValue.Primitive(title)            // "Zionomicon"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/dynamic-value/ DynamicValue]]
 */
sealed trait PrimitiveValue {

  /** The Scala or Java type of the value. */
  type Type

  /** Returns the [[PrimitiveType]] of the value, without validation. */
  def primitiveType: PrimitiveType[Type]

  /**
   * Returns the position of this case in the list of cases, from 0 for `Unit`
   * to 29 for `UUID`. Values of different cases are ordered by it.
   */
  def typeIndex: scala.Int

  /**
   * Returns a negative number, zero or a positive number when this value is
   * less than, equal to or greater than `that`. Values of the same case are
   * compared by their value, except as noted on `Int` and `Period`; values of
   * different cases by `typeIndex`.
   */
  def compare(that: PrimitiveValue): scala.Int

  /** Returns `true` when this value is greater than `that` by `compare`. */
  final def >(that: PrimitiveValue): Boolean = compare(that) > 0

  /**
   * Returns `true` when this value is greater than or equal to `that` by
   * `compare`.
   */
  final def >=(that: PrimitiveValue): Boolean = compare(that) >= 0

  /** Returns `true` when this value is less than `that` by `compare`. */
  final def <(that: PrimitiveValue): Boolean = compare(that) < 0

  /**
   * Returns `true` when this value is less than or equal to `that` by
   * `compare`.
   */
  final def <=(that: PrimitiveValue): Boolean = compare(that) <= 0
}

/** The cases of [[PrimitiveValue]], their ordering and their schemas. */
object PrimitiveValue {
  import zio.blocks.schema.binding._
  import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

  /** The `Unit` value. It is the smallest primitive value. */
  case object Unit extends PrimitiveValue {

    /** `scala.Unit`: this case holds `()`. */
    type Type = scala.Unit

    /** Returns `PrimitiveType.Unit`. */
    def primitiveType: PrimitiveType[scala.Unit] = PrimitiveType.Unit

    /** Returns 0, the lowest index: `Unit` sorts before every other case. */
    def typeIndex: scala.Int = 0

    /**
     * Returns 0 for `Unit` and `-that.typeIndex`, a negative number, for any
     * other case.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case _: Unit.type => 0
      case _            => -that.typeIndex
    }
  }

  /** A `Boolean` value; `false` is less than `true`. */
  case class Boolean(value: scala.Boolean) extends PrimitiveValue {

    /** `scala.Boolean`, the type of `value`. */
    type Type = scala.Boolean

    /** Returns `PrimitiveType.Boolean(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.Boolean] = PrimitiveType.Boolean(Validation.None)

    /** Returns 1: this case sorts after `Unit` and before `Byte`. */
    def typeIndex: scala.Int = 1

    /**
     * Compares two `Boolean` values with `Boolean#compare`, so `false` is less
     * than `true`. A value of another case gives `1 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Boolean => value.compare(x.value)
      case _          => 1 - that.typeIndex
    }
  }

  /** A `Byte` value. */
  case class Byte(value: scala.Byte) extends PrimitiveValue {

    /** `scala.Byte`, the type of `value`. */
    type Type = scala.Byte

    /** Returns `PrimitiveType.Byte(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.Byte] = PrimitiveType.Byte(Validation.None)

    /** Returns 2: this case sorts after `Boolean` and before `Short`. */
    def typeIndex: scala.Int = 2

    /**
     * Returns the difference of the two `Byte` values, this one minus the
     * other, which cannot overflow. A value of another case gives
     * `2 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Byte => value - x.value
      case _       => 2 - that.typeIndex
    }
  }

  /** A `Short` value. */
  case class Short(value: scala.Short) extends PrimitiveValue {

    /** `scala.Short`, the type of `value`. */
    type Type = scala.Short

    /** Returns `PrimitiveType.Short(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.Short] = PrimitiveType.Short(Validation.None)

    /** Returns 3: this case sorts after `Byte` and before `Int`. */
    def typeIndex: scala.Int = 3

    /**
     * Returns the difference of the two `Short` values, this one minus the
     * other, which cannot overflow. A value of another case gives
     * `3 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Short => value - x.value
      case _        => 3 - that.typeIndex
    }
  }

  /**
   * An `Int` value. It is compared by subtracting the two values, which
   * overflows: `Int(Int.MaxValue) > Int(-1)` is `false`.
   */
  case class Int(value: scala.Int) extends PrimitiveValue {

    /** `scala.Int`, the type of `value`. */
    type Type = scala.Int

    /** Returns `PrimitiveType.Int(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.Int] = PrimitiveType.Int(Validation.None)

    /** Returns 4: this case sorts after `Short` and before `Long`. */
    def typeIndex: scala.Int = 4

    /**
     * Compares by subtracting the two values, which overflows:
     * `Int(Int.MaxValue)` compared with `Int(-1)` is negative. A value of
     * another case is compared by `typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Int => value - x.value
      case _      => 4 - that.typeIndex
    }
  }

  /** A `Long` value. */
  case class Long(value: scala.Long) extends PrimitiveValue {

    /** `scala.Long`, the type of `value`. */
    type Type = scala.Long

    /** Returns `PrimitiveType.Long(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.Long] = PrimitiveType.Long(Validation.None)

    /** Returns 5: this case sorts after `Int` and before `Float`. */
    def typeIndex: scala.Int = 5

    /**
     * Compares two `Long` values with `java.lang.Long.compare`, which does not
     * overflow. A value of another case gives `5 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Long => value.compare(x.value)
      case _       => 5 - that.typeIndex
    }
  }

  /**
   * A `Float` value, compared as `java.lang.Float.compare` does: `NaN` is the
   * largest value, and `-0.0f` is less than `0.0f`.
   */
  case class Float(value: scala.Float) extends PrimitiveValue {

    /** `scala.Float`, the type of `value`. */
    type Type = scala.Float

    /** Returns `PrimitiveType.Float(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.Float] = PrimitiveType.Float(Validation.None)

    /** Returns 6: this case sorts after `Long` and before `Double`. */
    def typeIndex: scala.Int = 6

    /**
     * Compares two `Float` values with `java.lang.Float.compare`: `NaN` is
     * greater than `Float.PositiveInfinity`, and `-0.0f` is less than `0.0f`. A
     * value of another case gives `6 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Float => value.compare(x.value)
      case _        => 6 - that.typeIndex
    }
  }

  /**
   * A `Double` value, compared as `java.lang.Double.compare` does: `NaN` is the
   * largest value, and `-0.0` is less than `0.0`.
   */
  case class Double(value: scala.Double) extends PrimitiveValue {

    /** `scala.Double`, the type of `value`. */
    type Type = scala.Double

    /** Returns `PrimitiveType.Double(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.Double] = PrimitiveType.Double(Validation.None)

    /** Returns 7: this case sorts after `Float` and before `Char`. */
    def typeIndex: scala.Int = 7

    /**
     * Compares two `Double` values with `java.lang.Double.compare`: `NaN` is
     * greater than `Double.PositiveInfinity`, and `-0.0` is less than `0.0`. A
     * value of another case gives `7 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Double => value.compare(x.value)
      case _         => 7 - that.typeIndex
    }
  }

  /** A `Char` value. */
  case class Char(value: scala.Char) extends PrimitiveValue {

    /** `scala.Char`, the type of `value`. */
    type Type = scala.Char

    /** Returns `PrimitiveType.Char(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.Char] = PrimitiveType.Char(Validation.None)

    /** Returns 8: this case sorts after `Double` and before `String`. */
    def typeIndex: scala.Int = 8

    /**
     * Returns the difference of the two characters' UTF-16 code units. A value
     * of another case gives `8 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Char => value.compare(x.value)
      case _       => 8 - that.typeIndex
    }
  }

  /** A `String` value, compared with `String#compareTo`. */
  case class String(value: Predef.String) extends PrimitiveValue {

    /** `Predef.String`, the type of `value`. */
    type Type = Predef.String

    /** Returns `PrimitiveType.String(Validation.None)`. */
    def primitiveType: PrimitiveType[Predef.String] = PrimitiveType.String(Validation.None)

    /** Returns 9: this case sorts after `Char` and before `BigInt`. */
    def typeIndex: scala.Int = 9

    /**
     * Compares two strings with `String#compareTo`, by UTF-16 code unit, so
     * `"Z"` is less than `"a"`. A value of another case gives
     * `9 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: String => value.compareTo(x.value)
      case _         => 9 - that.typeIndex
    }
  }

  /** A `scala.BigInt` value. */
  case class BigInt(value: scala.BigInt) extends PrimitiveValue {

    /** `scala.BigInt`, the type of `value`. */
    type Type = scala.BigInt

    /** Returns `PrimitiveType.BigInt(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.BigInt] = PrimitiveType.BigInt(Validation.None)

    /** Returns 10: this case sorts after `String` and before `BigDecimal`. */
    def typeIndex: scala.Int = 10

    /**
     * Compares two `BigInt` values numerically. A value of another case gives
     * `10 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: BigInt => value.compareTo(x.value)
      case _         => 10 - that.typeIndex
    }
  }

  /**
   * A `scala.BigDecimal` value, compared by numeric value, so `2.0` and `2.00`
   * compare as equal.
   */
  case class BigDecimal(value: scala.BigDecimal) extends PrimitiveValue {

    /** `scala.BigDecimal`, the type of `value`. */
    type Type = scala.BigDecimal

    /** Returns `PrimitiveType.BigDecimal(Validation.None)`. */
    def primitiveType: PrimitiveType[scala.BigDecimal] = PrimitiveType.BigDecimal(Validation.None)

    /** Returns 11: this case sorts after `BigInt` and before `DayOfWeek`. */
    def typeIndex: scala.Int = 11

    /**
     * Compares two `BigDecimal` values numerically, so `2.0` and `2.00` compare
     * as equal. A value of another case gives `11 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: BigDecimal => value.compareTo(x.value)
      case _             => 11 - that.typeIndex
    }
  }

  /** A `java.time.DayOfWeek` value, from `MONDAY` to `SUNDAY`. */
  case class DayOfWeek(value: java.time.DayOfWeek) extends PrimitiveValue {

    /** `java.time.DayOfWeek`, the type of `value`. */
    type Type = java.time.DayOfWeek

    /** Returns `PrimitiveType.DayOfWeek(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.DayOfWeek] = PrimitiveType.DayOfWeek(Validation.None)

    /** Returns 12: this case sorts after `BigDecimal` and before `Duration`. */
    def typeIndex: scala.Int = 12

    /**
     * Compares two days with `DayOfWeek#compareTo`, from `MONDAY` to `SUNDAY`.
     * A value of another case gives `12 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: DayOfWeek => value.compareTo(x.value)
      case _            => 12 - that.typeIndex
    }
  }

  /** A `java.time.Duration` value. */
  case class Duration(value: java.time.Duration) extends PrimitiveValue {

    /** `java.time.Duration`, the type of `value`. */
    type Type = java.time.Duration

    /** Returns `PrimitiveType.Duration(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.Duration] = PrimitiveType.Duration(Validation.None)

    /** Returns 13: this case sorts after `DayOfWeek` and before `Instant`. */
    def typeIndex: scala.Int = 13

    /**
     * Compares two durations by length with `Duration#compareTo`. A value of
     * another case gives `13 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Duration => value.compareTo(x.value)
      case _           => 13 - that.typeIndex
    }
  }

  /** A `java.time.Instant` value. */
  case class Instant(value: java.time.Instant) extends PrimitiveValue {

    /** `java.time.Instant`, the type of `value`. */
    type Type = java.time.Instant

    /** Returns `PrimitiveType.Instant(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.Instant] = PrimitiveType.Instant(Validation.None)

    /** Returns 14: this case sorts after `Duration` and before `LocalDate`. */
    def typeIndex: scala.Int = 14

    /**
     * Compares two instants with `Instant#compareTo`, earlier instants first. A
     * value of another case gives `14 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Instant => value.compareTo(x.value)
      case _          => 14 - that.typeIndex
    }
  }

  /** A `java.time.LocalDate` value. */
  case class LocalDate(value: java.time.LocalDate) extends PrimitiveValue {

    /** `java.time.LocalDate`, the type of `value`. */
    type Type = java.time.LocalDate

    /** Returns `PrimitiveType.LocalDate(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.LocalDate] = PrimitiveType.LocalDate(Validation.None)

    /**
     * Returns 15: this case sorts after `Instant` and before `LocalDateTime`.
     */
    def typeIndex: scala.Int = 15

    /**
     * Compares two dates with `LocalDate#compareTo`, earlier dates first. A
     * value of another case gives `15 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalDate => value.compareTo(x.value)
      case _            => 15 - that.typeIndex
    }
  }

  /** A `java.time.LocalDateTime` value. */
  case class LocalDateTime(value: java.time.LocalDateTime) extends PrimitiveValue {

    /** `java.time.LocalDateTime`, the type of `value`. */
    type Type = java.time.LocalDateTime

    /** Returns `PrimitiveType.LocalDateTime(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.LocalDateTime] = PrimitiveType.LocalDateTime(Validation.None)

    /** Returns 16: this case sorts after `LocalDate` and before `LocalTime`. */
    def typeIndex: scala.Int = 16

    /**
     * Compares two date-times with `LocalDateTime#compareTo`, earlier
     * date-times first. A value of another case gives `16 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalDateTime => value.compareTo(x.value)
      case _                => 16 - that.typeIndex
    }
  }

  /** A `java.time.LocalTime` value. */
  case class LocalTime(value: java.time.LocalTime) extends PrimitiveValue {

    /** `java.time.LocalTime`, the type of `value`. */
    type Type = java.time.LocalTime

    /** Returns `PrimitiveType.LocalTime(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.LocalTime] = PrimitiveType.LocalTime(Validation.None)

    /** Returns 17: this case sorts after `LocalDateTime` and before `Month`. */
    def typeIndex: scala.Int = 17

    /**
     * Compares two times of day with `LocalTime#compareTo`, earlier times
     * first. A value of another case gives `17 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalTime => value.compareTo(x.value)
      case _            => 17 - that.typeIndex
    }
  }

  /** A `java.time.Month` value, from `JANUARY` to `DECEMBER`. */
  case class Month(value: java.time.Month) extends PrimitiveValue {

    /** `java.time.Month`, the type of `value`. */
    type Type = java.time.Month

    /** Returns `PrimitiveType.Month(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.Month] = PrimitiveType.Month(Validation.None)

    /** Returns 18: this case sorts after `LocalTime` and before `MonthDay`. */
    def typeIndex: scala.Int = 18

    /**
     * Compares two months with `Month#compareTo`, from `JANUARY` to `DECEMBER`.
     * A value of another case gives `18 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Month => value.compareTo(x.value)
      case _        => 18 - that.typeIndex
    }
  }

  /** A `java.time.MonthDay` value. */
  case class MonthDay(value: java.time.MonthDay) extends PrimitiveValue {

    /** `java.time.MonthDay`, the type of `value`. */
    type Type = java.time.MonthDay

    /** Returns `PrimitiveType.MonthDay(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.MonthDay] = PrimitiveType.MonthDay(Validation.None)

    /**
     * Returns 19: this case sorts after `Month` and before `OffsetDateTime`.
     */
    def typeIndex: scala.Int = 19

    /**
     * Compares with `MonthDay#compareTo`: by month, then by day of month. A
     * value of another case gives `19 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: MonthDay => value.compareTo(x.value)
      case _           => 19 - that.typeIndex
    }
  }

  /**
   * A `java.time.OffsetDateTime` value, compared with
   * `OffsetDateTime#compareTo`: by instant, then by local date-time.
   */
  case class OffsetDateTime(value: java.time.OffsetDateTime) extends PrimitiveValue {

    /** `java.time.OffsetDateTime`, the type of `value`. */
    type Type = java.time.OffsetDateTime

    /** Returns `PrimitiveType.OffsetDateTime(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.OffsetDateTime] = PrimitiveType.OffsetDateTime(Validation.None)

    /** Returns 20: this case sorts after `MonthDay` and before `OffsetTime`. */
    def typeIndex: scala.Int = 20

    /**
     * Compares with `OffsetDateTime#compareTo`: by instant, then by local
     * date-time, so `12:00+01:00` is greater than `11:00Z` on the same day,
     * although both are the same instant. A value of another case gives
     * `20 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: OffsetDateTime => value.compareTo(x.value)
      case _                 => 20 - that.typeIndex
    }
  }

  /** A `java.time.OffsetTime` value, compared with `OffsetTime#compareTo`. */
  case class OffsetTime(value: java.time.OffsetTime) extends PrimitiveValue {

    /** `java.time.OffsetTime`, the type of `value`. */
    type Type = java.time.OffsetTime

    /** Returns `PrimitiveType.OffsetTime(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.OffsetTime] = PrimitiveType.OffsetTime(Validation.None)

    /**
     * Returns 21: this case sorts after `OffsetDateTime` and before `Period`.
     */
    def typeIndex: scala.Int = 21

    /**
     * Compares with `OffsetTime#compareTo`: by the time in UTC, then by local
     * time, so `12:00+01:00` is greater than `11:00Z`, although both are the
     * same time in UTC. A value of another case gives `21 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: OffsetTime => value.compareTo(x.value)
      case _             => 21 - that.typeIndex
    }
  }

  /**
   * A `java.time.Period` value. Periods are compared by their approximate
   * length in days, counting a month as 30.436875 days, so `P1Y` and `P12M`
   * compare as equal although they are not equal values.
   */
  case class Period(value: java.time.Period) extends PrimitiveValue {

    /** `java.time.Period`, the type of `value`. */
    type Type = java.time.Period

    /** Returns `PrimitiveType.Period(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.Period] = PrimitiveType.Period(Validation.None)

    /** Returns 22: this case sorts after `OffsetTime` and before `Year`. */
    def typeIndex: scala.Int = 22

    /**
     * Compares by approximate length in days, counting a month as 30.436875
     * days, so `P1Y` and `P12M` compare as equal. A value of another case is
     * compared by `typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Period =>
        val xv = x.value
        (value.toTotalMonths * 30.436875 + value.getDays).compareTo(xv.toTotalMonths * 30.436875 + xv.getDays)
      case _ => 22 - that.typeIndex
    }
  }

  /** A `java.time.Year` value. */
  case class Year(value: java.time.Year) extends PrimitiveValue {

    /** `java.time.Year`, the type of `value`. */
    type Type = java.time.Year

    /** Returns `PrimitiveType.Year(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.Year] = PrimitiveType.Year(Validation.None)

    /** Returns 23: this case sorts after `Period` and before `YearMonth`. */
    def typeIndex: scala.Int = 23

    /**
     * Compares two years with `Year#compareTo`, earlier years first. A value of
     * another case gives `23 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Year => value.compareTo(x.value)
      case _       => 23 - that.typeIndex
    }
  }

  /** A `java.time.YearMonth` value. */
  case class YearMonth(value: java.time.YearMonth) extends PrimitiveValue {

    /** `java.time.YearMonth`, the type of `value`. */
    type Type = java.time.YearMonth

    /** Returns `PrimitiveType.YearMonth(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.YearMonth] = PrimitiveType.YearMonth(Validation.None)

    /** Returns 24: this case sorts after `Year` and before `ZoneId`. */
    def typeIndex: scala.Int = 24

    /**
     * Compares with `YearMonth#compareTo`: by year, then by month. A value of
     * another case gives `24 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: YearMonth => value.compareTo(x.value)
      case _            => 24 - that.typeIndex
    }
  }

  /**
   * A `java.time.ZoneId` value, compared by its ID string, such as
   * `Europe/Paris`.
   */
  case class ZoneId(value: java.time.ZoneId) extends PrimitiveValue {

    /** `java.time.ZoneId`, the type of `value`. */
    type Type = java.time.ZoneId

    /** Returns `PrimitiveType.ZoneId(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.ZoneId] = PrimitiveType.ZoneId(Validation.None)

    /**
     * Returns 25: this case sorts after `YearMonth` and before `ZoneOffset`.
     */
    def typeIndex: scala.Int = 25

    /**
     * Compares the two zone ID strings with `String#compareTo`, so
     * `America/New_York` is less than `Europe/Paris`. A value of another case
     * gives `25 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZoneId => value.getId.compareTo(x.value.getId)
      case _         => 25 - that.typeIndex
    }
  }

  /**
   * A `java.time.ZoneOffset` value, compared with `ZoneOffset#compareTo`, which
   * puts larger offsets first.
   */
  case class ZoneOffset(value: java.time.ZoneOffset) extends PrimitiveValue {

    /** `java.time.ZoneOffset`, the type of `value`. */
    type Type = java.time.ZoneOffset

    /** Returns `PrimitiveType.ZoneOffset(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.ZoneOffset] = PrimitiveType.ZoneOffset(Validation.None)

    /**
     * Returns 26: this case sorts after `ZoneId` and before `ZonedDateTime`.
     */
    def typeIndex: scala.Int = 26

    /**
     * Compares with `ZoneOffset#compareTo`, which puts larger offsets first:
     * `+01:00` is greater than `+02:00`. A value of another case gives
     * `26 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZoneOffset => value.compareTo(x.value)
      case _             => 26 - that.typeIndex
    }
  }

  /**
   * A `java.time.ZonedDateTime` value, compared with `ZonedDateTime#compareTo`:
   * by instant, then by local date-time, then by zone ID.
   */
  case class ZonedDateTime(value: java.time.ZonedDateTime) extends PrimitiveValue {

    /** `java.time.ZonedDateTime`, the type of `value`. */
    type Type = java.time.ZonedDateTime

    /** Returns `PrimitiveType.ZonedDateTime(Validation.None)`. */
    def primitiveType: PrimitiveType[java.time.ZonedDateTime] = PrimitiveType.ZonedDateTime(Validation.None)

    /** Returns 27: this case sorts after `ZoneOffset` and before `Currency`. */
    def typeIndex: scala.Int = 27

    /**
     * Compares with `ZonedDateTime#compareTo`: by instant, then by local
     * date-time, then by zone ID string. A value of another case gives
     * `27 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZonedDateTime => value.compareTo(x.value)
      case _                => 27 - that.typeIndex
    }
  }

  /** A `java.util.Currency` value, compared by its ISO 4217 currency code. */
  case class Currency(value: java.util.Currency) extends PrimitiveValue {

    /** `java.util.Currency`, the type of `value`. */
    type Type = java.util.Currency

    /** Returns `PrimitiveType.Currency(Validation.None)`. */
    def primitiveType: PrimitiveType[java.util.Currency] = PrimitiveType.Currency(Validation.None)

    /** Returns 28: this case sorts after `ZonedDateTime` and before `UUID`. */
    def typeIndex: scala.Int = 28

    /**
     * Compares the two ISO 4217 currency codes with `String#compareTo`, so
     * `EUR` is less than `USD`. A value of another case gives
     * `28 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Currency => value.getCurrencyCode.compareTo(x.value.getCurrencyCode)
      case _           => 28 - that.typeIndex
    }
  }

  /** A `java.util.UUID` value, compared with `UUID#compareTo`. */
  case class UUID(value: java.util.UUID) extends PrimitiveValue {

    /** `java.util.UUID`, the type of `value`. */
    type Type = java.util.UUID

    /** Returns `PrimitiveType.UUID(Validation.None)`. */
    def primitiveType: PrimitiveType[java.util.UUID] = PrimitiveType.UUID(Validation.None)

    /** Returns 29, the highest index: `UUID` sorts after every other case. */
    def typeIndex: scala.Int = 29

    /**
     * Compares with `UUID#compareTo`, which compares the most significant 64
     * bits and then the least significant 64 bits, each as a signed `Long`. A
     * value of another case gives `29 - that.typeIndex`.
     */
    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: UUID => value.compareTo(x.value)
      case _       => 29 - that.typeIndex
    }
  }

  /** Orders primitive values with `compare`. */
  implicit val ordering: Ordering[PrimitiveValue] = new Ordering[PrimitiveValue] {
    def compare(x: PrimitiveValue, y: PrimitiveValue): scala.Int = x.compare(y)
  }

  /** The schema of `PrimitiveValue.Unit`: a record without fields. */
  implicit lazy val unitSchema: Schema[Unit.type] = new Schema(
    reflect = new Reflect.Record[Binding, Unit.type](
      fields = Chunk.empty,
      typeId = TypeId.of[Unit.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Unit.type](Unit),
        deconstructor = new ConstantDeconstructor[Unit.type]
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Boolean`: a record with the single field
   * `value`.
   */
  implicit lazy val booleanSchema: Schema[Boolean] = new Schema(
    reflect = new Reflect.Record[Binding, Boolean](
      fields = Chunk.single(Schema[scala.Boolean].reflect.asTerm("value")),
      typeId = TypeId.of[Boolean],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Boolean] {
          def usedRegisters: RegisterOffset                             = RegisterOffset(booleans = 1)
          def construct(in: Registers, offset: RegisterOffset): Boolean = new Boolean(in.getBoolean(offset))
        },
        deconstructor = new Deconstructor[Boolean] {
          def usedRegisters: RegisterOffset                                                = RegisterOffset(booleans = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Boolean): scala.Unit =
            out.setBoolean(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Byte`: a record with the single field
   * `value`.
   */
  implicit lazy val byteSchema: Schema[Byte] = new Schema(
    reflect = new Reflect.Record[Binding, Byte](
      fields = Chunk.single(Schema[scala.Byte].reflect.asTerm("value")),
      typeId = TypeId.of[Byte],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Byte] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(bytes = 1)
          def construct(in: Registers, offset: RegisterOffset): Byte = new Byte(in.getByte(offset))
        },
        deconstructor = new Deconstructor[Byte] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(bytes = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Byte): scala.Unit =
            out.setByte(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Short`: a record with the single field
   * `value`.
   */
  implicit lazy val shortSchema: Schema[Short] = new Schema(
    reflect = new Reflect.Record[Binding, Short](
      fields = Chunk.single(Schema[scala.Short].reflect.asTerm("value")),
      typeId = TypeId.of[Short],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Short] {
          def usedRegisters: RegisterOffset                           = RegisterOffset(shorts = 1)
          def construct(in: Registers, offset: RegisterOffset): Short = new Short(in.getShort(offset))
        },
        deconstructor = new Deconstructor[Short] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(shorts = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Short): scala.Unit =
            out.setShort(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Int`: a record with the single field `value`.
   */
  implicit lazy val intSchema: Schema[Int] = new Schema(
    reflect = new Reflect.Record[Binding, Int](
      fields = Chunk.single(Schema[scala.Int].reflect.asTerm("value")),
      typeId = TypeId.of[Int],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Int] {
          def usedRegisters: RegisterOffset                         = RegisterOffset(ints = 1)
          def construct(in: Registers, offset: RegisterOffset): Int = new Int(in.getInt(offset))
        },
        deconstructor = new Deconstructor[Int] {
          def usedRegisters: RegisterOffset                                            = RegisterOffset(ints = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Int): scala.Unit =
            out.setInt(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Long`: a record with the single field
   * `value`.
   */
  implicit lazy val longSchema: Schema[Long] = new Schema(
    reflect = new Reflect.Record[Binding, Long](
      fields = Chunk.single(Schema[scala.Long].reflect.asTerm("value")),
      typeId = TypeId.of[Long],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Long] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(longs = 1)
          def construct(in: Registers, offset: RegisterOffset): Long = new Long(in.getLong(offset))
        },
        deconstructor = new Deconstructor[Long] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(longs = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Long): scala.Unit =
            out.setLong(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Float`: a record with the single field
   * `value`.
   */
  implicit lazy val floatSchema: Schema[Float] = new Schema(
    reflect = new Reflect.Record[Binding, Float](
      fields = Chunk.single(Schema[scala.Float].reflect.asTerm("value")),
      typeId = TypeId.of[Float],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Float] {
          def usedRegisters: RegisterOffset                           = RegisterOffset(floats = 1)
          def construct(in: Registers, offset: RegisterOffset): Float = new Float(in.getFloat(offset))
        },
        deconstructor = new Deconstructor[Float] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(floats = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Float): scala.Unit =
            out.setFloat(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Double`: a record with the single field
   * `value`.
   */
  implicit lazy val doubleSchema: Schema[Double] = new Schema(
    reflect = new Reflect.Record[Binding, Double](
      fields = Chunk.single(Schema[scala.Double].reflect.asTerm("value")),
      typeId = TypeId.of[Double],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Double] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(doubles = 1)
          def construct(in: Registers, offset: RegisterOffset): Double = new Double(in.getDouble(offset))
        },
        deconstructor = new Deconstructor[Double] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(doubles = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Double): scala.Unit =
            out.setDouble(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Char`: a record with the single field
   * `value`.
   */
  implicit lazy val charSchema: Schema[Char] = new Schema(
    reflect = new Reflect.Record[Binding, Char](
      fields = Chunk.single(Schema[scala.Char].reflect.asTerm("value")),
      typeId = TypeId.of[Char],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Char] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(chars = 1)
          def construct(in: Registers, offset: RegisterOffset): Char = new Char(in.getChar(offset))
        },
        deconstructor = new Deconstructor[Char] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(chars = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Char): scala.Unit =
            out.setChar(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.String`: a record with the single field
   * `value`.
   */
  implicit lazy val stringSchema: Schema[String] = new Schema(
    reflect = new Reflect.Record[Binding, String](
      fields = Chunk.single(Schema[java.lang.String].reflect.asTerm("value")),
      typeId = TypeId.of[String],
      recordBinding = new Binding.Record(
        constructor = new Constructor[String] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): String =
            new String(in.getObject(offset).asInstanceOf[java.lang.String])
        },
        deconstructor = new Deconstructor[String] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: String): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.BigInt`: a record with the single field
   * `value`.
   */
  implicit lazy val bigIntSchema: Schema[BigInt] = new Schema(
    reflect = new Reflect.Record[Binding, BigInt](
      fields = Chunk.single(Schema[scala.BigInt].reflect.asTerm("value")),
      typeId = TypeId.of[BigInt],
      recordBinding = new Binding.Record(
        constructor = new Constructor[BigInt] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): BigInt =
            new BigInt(in.getObject(offset).asInstanceOf[scala.BigInt])
        },
        deconstructor = new Deconstructor[BigInt] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: BigInt): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.BigDecimal`: a record with the single field
   * `value`.
   */
  implicit lazy val bigDecimalSchema: Schema[BigDecimal] = new Schema(
    reflect = new Reflect.Record[Binding, BigDecimal](
      fields = Chunk.single(Schema[scala.BigDecimal].reflect.asTerm("value")),
      typeId = TypeId.of[BigDecimal],
      recordBinding = new Binding.Record(
        constructor = new Constructor[BigDecimal] {
          def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): BigDecimal =
            new BigDecimal(in.getObject(offset).asInstanceOf[scala.BigDecimal])
        },
        deconstructor = new Deconstructor[BigDecimal] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: BigDecimal): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.DayOfWeek`: a record with the single field
   * `value`.
   */
  implicit lazy val dayOfWeekSchema: Schema[DayOfWeek] = new Schema(
    reflect = new Reflect.Record[Binding, DayOfWeek](
      fields = Chunk.single(Schema[java.time.DayOfWeek].reflect.asTerm("value")),
      typeId = TypeId.of[DayOfWeek],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DayOfWeek] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): DayOfWeek =
            new DayOfWeek(in.getObject(offset).asInstanceOf[java.time.DayOfWeek])
        },
        deconstructor = new Deconstructor[DayOfWeek] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: DayOfWeek): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Duration`: a record with the single field
   * `value`.
   */
  implicit lazy val durationSchema: Schema[Duration] = new Schema(
    reflect = new Reflect.Record[Binding, Duration](
      fields = Chunk.single(Schema[java.time.Duration].reflect.asTerm("value")),
      typeId = TypeId.of[Duration],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Duration] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Duration =
            new Duration(in.getObject(offset).asInstanceOf[java.time.Duration])
        },
        deconstructor = new Deconstructor[Duration] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Duration): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Instant`: a record with the single field
   * `value`.
   */
  implicit lazy val instantSchema: Schema[Instant] = new Schema(
    reflect = new Reflect.Record[Binding, Instant](
      fields = Chunk.single(Schema[java.time.Instant].reflect.asTerm("value")),
      typeId = TypeId.of[Instant],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Instant] {
          def usedRegisters: RegisterOffset                             = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Instant =
            new Instant(in.getObject(offset).asInstanceOf[java.time.Instant])
        },
        deconstructor = new Deconstructor[Instant] {
          def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Instant): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.LocalDate`: a record with the single field
   * `value`.
   */
  implicit lazy val localDateSchema: Schema[LocalDate] = new Schema(
    reflect = new Reflect.Record[Binding, LocalDate](
      fields = Chunk.single(Schema[java.time.LocalDate].reflect.asTerm("value")),
      typeId = TypeId.of[LocalDate],
      recordBinding = new Binding.Record(
        constructor = new Constructor[LocalDate] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): LocalDate =
            new LocalDate(in.getObject(offset).asInstanceOf[java.time.LocalDate])
        },
        deconstructor = new Deconstructor[LocalDate] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: LocalDate): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.LocalDateTime`: a record with the single
   * field `value`.
   */
  implicit lazy val localDateTimeSchema: Schema[LocalDateTime] = new Schema(
    reflect = new Reflect.Record[Binding, LocalDateTime](
      fields = Chunk.single(Schema[java.time.LocalDateTime].reflect.asTerm("value")),
      typeId = TypeId.of[LocalDateTime],
      recordBinding = new Binding.Record(
        constructor = new Constructor[LocalDateTime] {
          def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): LocalDateTime =
            new LocalDateTime(in.getObject(offset).asInstanceOf[java.time.LocalDateTime])
        },
        deconstructor = new Deconstructor[LocalDateTime] {
          def usedRegisters: RegisterOffset                                                      = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: LocalDateTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.LocalTime`: a record with the single field
   * `value`.
   */
  implicit lazy val localTimeSchema: Schema[LocalTime] = new Schema(
    reflect = new Reflect.Record[Binding, LocalTime](
      fields = Chunk.single(Schema[java.time.LocalTime].reflect.asTerm("value")),
      typeId = TypeId.of[LocalTime],
      recordBinding = new Binding.Record(
        constructor = new Constructor[LocalTime] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): LocalTime =
            new LocalTime(in.getObject(offset).asInstanceOf[java.time.LocalTime])
        },
        deconstructor = new Deconstructor[LocalTime] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: LocalTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Month`: a record with the single field
   * `value`.
   */
  implicit lazy val monthSchema: Schema[Month] = new Schema(
    reflect = new Reflect.Record[Binding, Month](
      fields = Chunk.single(Schema[java.time.Month].reflect.asTerm("value")),
      typeId = TypeId.of[Month],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Month] {
          def usedRegisters: RegisterOffset                           = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Month =
            new Month(in.getObject(offset).asInstanceOf[java.time.Month])
        },
        deconstructor = new Deconstructor[Month] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Month): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.MonthDay`: a record with the single field
   * `value`.
   */
  implicit lazy val monthDaySchema: Schema[MonthDay] = new Schema(
    reflect = new Reflect.Record[Binding, MonthDay](
      fields = Chunk.single(Schema[java.time.MonthDay].reflect.asTerm("value")),
      typeId = TypeId.of[MonthDay],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MonthDay] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): MonthDay =
            new MonthDay(in.getObject(offset).asInstanceOf[java.time.MonthDay])
        },
        deconstructor = new Deconstructor[MonthDay] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: MonthDay): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.OffsetDateTime`: a record with the single
   * field `value`.
   */
  implicit lazy val offsetDateTimeSchema: Schema[OffsetDateTime] = new Schema(
    reflect = new Reflect.Record[Binding, OffsetDateTime](
      fields = Chunk.single(Schema[java.time.OffsetDateTime].reflect.asTerm("value")),
      typeId = TypeId.of[OffsetDateTime],
      recordBinding = new Binding.Record(
        constructor = new Constructor[OffsetDateTime] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): OffsetDateTime =
            new OffsetDateTime(in.getObject(offset).asInstanceOf[java.time.OffsetDateTime])
        },
        deconstructor = new Deconstructor[OffsetDateTime] {
          def usedRegisters: RegisterOffset                                                       = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: OffsetDateTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.OffsetTime`: a record with the single field
   * `value`.
   */
  implicit lazy val offsetTimeSchema: Schema[OffsetTime] = new Schema(
    reflect = new Reflect.Record[Binding, OffsetTime](
      fields = Chunk.single(Schema[java.time.OffsetTime].reflect.asTerm("value")),
      typeId = TypeId.of[OffsetTime],
      recordBinding = new Binding.Record(
        constructor = new Constructor[OffsetTime] {
          def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): OffsetTime =
            new OffsetTime(in.getObject(offset).asInstanceOf[java.time.OffsetTime])
        },
        deconstructor = new Deconstructor[OffsetTime] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: OffsetTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Period`: a record with the single field
   * `value`.
   */
  implicit lazy val periodSchema: Schema[Period] = new Schema(
    reflect = new Reflect.Record[Binding, Period](
      fields = Chunk.single(Schema[java.time.Period].reflect.asTerm("value")),
      typeId = TypeId.of[Period],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Period] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Period =
            new Period(in.getObject(offset).asInstanceOf[java.time.Period])
        },
        deconstructor = new Deconstructor[Period] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Period): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Year`: a record with the single field
   * `value`.
   */
  implicit lazy val yearSchema: Schema[Year] = new Schema(
    reflect = new Reflect.Record[Binding, Year](
      fields = Chunk.single(Schema[java.time.Year].reflect.asTerm("value")),
      typeId = TypeId.of[Year],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Year] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Year =
            new Year(in.getObject(offset).asInstanceOf[java.time.Year])
        },
        deconstructor = new Deconstructor[Year] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Year): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.YearMonth`: a record with the single field
   * `value`.
   */
  implicit lazy val yearMonthSchema: Schema[YearMonth] = new Schema(
    reflect = new Reflect.Record[Binding, YearMonth](
      fields = Chunk.single(Schema[java.time.YearMonth].reflect.asTerm("value")),
      typeId = TypeId.of[YearMonth],
      recordBinding = new Binding.Record(
        constructor = new Constructor[YearMonth] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): YearMonth =
            new YearMonth(in.getObject(offset).asInstanceOf[java.time.YearMonth])
        },
        deconstructor = new Deconstructor[YearMonth] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: YearMonth): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.ZoneId`: a record with the single field
   * `value`.
   */
  implicit lazy val zoneIdSchema: Schema[ZoneId] = new Schema(
    reflect = new Reflect.Record[Binding, ZoneId](
      fields = Chunk.single(Schema[java.time.ZoneId].reflect.asTerm("value")),
      typeId = TypeId.of[ZoneId],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ZoneId] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ZoneId =
            new ZoneId(in.getObject(offset).asInstanceOf[java.time.ZoneId])
        },
        deconstructor = new Deconstructor[ZoneId] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ZoneId): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.ZoneOffset`: a record with the single field
   * `value`.
   */
  implicit lazy val zoneOffsetSchema: Schema[ZoneOffset] = new Schema(
    reflect = new Reflect.Record[Binding, ZoneOffset](
      fields = Chunk.single(Schema[java.time.ZoneOffset].reflect.asTerm("value")),
      typeId = TypeId.of[ZoneOffset],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ZoneOffset] {
          def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ZoneOffset =
            new ZoneOffset(in.getObject(offset).asInstanceOf[java.time.ZoneOffset])
        },
        deconstructor = new Deconstructor[ZoneOffset] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ZoneOffset): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.ZonedDateTime`: a record with the single
   * field `value`.
   */
  implicit lazy val zonedDateTimeSchema: Schema[ZonedDateTime] = new Schema(
    reflect = new Reflect.Record[Binding, ZonedDateTime](
      fields = Chunk.single(Schema[java.time.ZonedDateTime].reflect.asTerm("value")),
      typeId = TypeId.of[ZonedDateTime],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ZonedDateTime] {
          def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ZonedDateTime =
            new ZonedDateTime(in.getObject(offset).asInstanceOf[java.time.ZonedDateTime])
        },
        deconstructor = new Deconstructor[ZonedDateTime] {
          def usedRegisters: RegisterOffset                                                      = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ZonedDateTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.Currency`: a record with the single field
   * `value`.
   */
  implicit lazy val currencySchema: Schema[Currency] = new Schema(
    reflect = new Reflect.Record[Binding, Currency](
      fields = Chunk.single(Schema[java.util.Currency].reflect.asTerm("value")),
      typeId = TypeId.of[Currency],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Currency] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Currency =
            new Currency(in.getObject(offset).asInstanceOf[java.util.Currency])
        },
        deconstructor = new Deconstructor[Currency] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Currency): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue.UUID`: a record with the single field
   * `value`.
   */
  implicit lazy val uuidSchema: Schema[UUID] = new Schema(
    reflect = new Reflect.Record[Binding, UUID](
      fields = Chunk.single(Schema[java.util.UUID].reflect.asTerm("value")),
      typeId = TypeId.of[UUID],
      recordBinding = new Binding.Record(
        constructor = new Constructor[UUID] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): UUID =
            new UUID(in.getObject(offset).asInstanceOf[java.util.UUID])
        },
        deconstructor = new Deconstructor[UUID] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: UUID): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * The schema of `PrimitiveValue`: a variant with one case per
   * [[PrimitiveValue]] case, named after it and in `typeIndex` order, whose
   * schemas are the ones above.
   */
  implicit lazy val schema: Schema[PrimitiveValue] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveValue](
      cases = Chunk(
        unitSchema.reflect.asTerm("Unit"),
        booleanSchema.reflect.asTerm("Boolean"),
        byteSchema.reflect.asTerm("Byte"),
        shortSchema.reflect.asTerm("Short"),
        intSchema.reflect.asTerm("Int"),
        longSchema.reflect.asTerm("Long"),
        floatSchema.reflect.asTerm("Float"),
        doubleSchema.reflect.asTerm("Double"),
        charSchema.reflect.asTerm("Char"),
        stringSchema.reflect.asTerm("String"),
        bigIntSchema.reflect.asTerm("BigInt"),
        bigDecimalSchema.reflect.asTerm("BigDecimal"),
        dayOfWeekSchema.reflect.asTerm("DayOfWeek"),
        durationSchema.reflect.asTerm("Duration"),
        instantSchema.reflect.asTerm("Instant"),
        localDateSchema.reflect.asTerm("LocalDate"),
        localDateTimeSchema.reflect.asTerm("LocalDateTime"),
        localTimeSchema.reflect.asTerm("LocalTime"),
        monthSchema.reflect.asTerm("Month"),
        monthDaySchema.reflect.asTerm("MonthDay"),
        offsetDateTimeSchema.reflect.asTerm("OffsetDateTime"),
        offsetTimeSchema.reflect.asTerm("OffsetTime"),
        periodSchema.reflect.asTerm("Period"),
        yearSchema.reflect.asTerm("Year"),
        yearMonthSchema.reflect.asTerm("YearMonth"),
        zoneIdSchema.reflect.asTerm("ZoneId"),
        zoneOffsetSchema.reflect.asTerm("ZoneOffset"),
        zonedDateTimeSchema.reflect.asTerm("ZonedDateTime"),
        currencySchema.reflect.asTerm("Currency"),
        uuidSchema.reflect.asTerm("UUID")
      ),
      typeId = TypeId.of[PrimitiveValue],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveValue] {
          def discriminate(a: PrimitiveValue): scala.Int = a.typeIndex
        },
        matchers = Matchers(
          new Matcher[Unit.type] {
            def downcastOrNull(a: Any): Unit.type = a match {
              case x: Unit.type => x
              case _            => null.asInstanceOf[Unit.type]
            }
          },
          new Matcher[Boolean] {
            def downcastOrNull(a: Any): Boolean = a match {
              case x: Boolean => x
              case _          => null.asInstanceOf[Boolean]
            }
          },
          new Matcher[Byte] {
            def downcastOrNull(a: Any): Byte = a match {
              case x: Byte => x
              case _       => null.asInstanceOf[Byte]
            }
          },
          new Matcher[Short] {
            def downcastOrNull(a: Any): Short = a match {
              case x: Short => x
              case _        => null.asInstanceOf[Short]
            }
          },
          new Matcher[Int] {
            def downcastOrNull(a: Any): Int = a match {
              case x: Int => x
              case _      => null.asInstanceOf[Int]
            }
          },
          new Matcher[Long] {
            def downcastOrNull(a: Any): Long = a match {
              case x: Long => x
              case _       => null.asInstanceOf[Long]
            }
          },
          new Matcher[Float] {
            def downcastOrNull(a: Any): Float = a match {
              case x: Float => x
              case _        => null.asInstanceOf[Float]
            }
          },
          new Matcher[Double] {
            def downcastOrNull(a: Any): Double = a match {
              case x: Double => x
              case _         => null.asInstanceOf[Double]
            }
          },
          new Matcher[Char] {
            def downcastOrNull(a: Any): Char = a match {
              case x: Char => x
              case _       => null.asInstanceOf[Char]
            }
          },
          new Matcher[String] {
            def downcastOrNull(a: Any): String = a match {
              case x: String => x
              case _         => null.asInstanceOf[String]
            }
          },
          new Matcher[BigInt] {
            def downcastOrNull(a: Any): BigInt = a match {
              case x: BigInt => x
              case _         => null.asInstanceOf[BigInt]
            }
          },
          new Matcher[BigDecimal] {
            def downcastOrNull(a: Any): BigDecimal = a match {
              case x: BigDecimal => x
              case _             => null.asInstanceOf[BigDecimal]
            }
          },
          new Matcher[DayOfWeek] {
            def downcastOrNull(a: Any): DayOfWeek = a match {
              case x: DayOfWeek => x
              case _            => null.asInstanceOf[DayOfWeek]
            }
          },
          new Matcher[Duration] {
            def downcastOrNull(a: Any): Duration = a match {
              case x: Duration => x
              case _           => null.asInstanceOf[Duration]
            }
          },
          new Matcher[Instant] {
            def downcastOrNull(a: Any): Instant = a match {
              case x: Instant => x
              case _          => null.asInstanceOf[Instant]
            }
          },
          new Matcher[LocalDate] {
            def downcastOrNull(a: Any): LocalDate = a match {
              case x: LocalDate => x
              case _            => null.asInstanceOf[LocalDate]
            }
          },
          new Matcher[LocalDateTime] {
            def downcastOrNull(a: Any): LocalDateTime = a match {
              case x: LocalDateTime => x
              case _                => null.asInstanceOf[LocalDateTime]
            }
          },
          new Matcher[LocalTime] {
            def downcastOrNull(a: Any): LocalTime = a match {
              case x: LocalTime => x
              case _            => null.asInstanceOf[LocalTime]
            }
          },
          new Matcher[Month] {
            def downcastOrNull(a: Any): Month = a match {
              case x: Month => x
              case _        => null.asInstanceOf[Month]
            }
          },
          new Matcher[MonthDay] {
            def downcastOrNull(a: Any): MonthDay = a match {
              case x: MonthDay => x
              case _           => null.asInstanceOf[MonthDay]
            }
          },
          new Matcher[OffsetDateTime] {
            def downcastOrNull(a: Any): OffsetDateTime = a match {
              case x: OffsetDateTime => x
              case _                 => null.asInstanceOf[OffsetDateTime]
            }
          },
          new Matcher[OffsetTime] {
            def downcastOrNull(a: Any): OffsetTime = a match {
              case x: OffsetTime => x
              case _             => null.asInstanceOf[OffsetTime]
            }
          },
          new Matcher[Period] {
            def downcastOrNull(a: Any): Period = a match {
              case x: Period => x
              case _         => null.asInstanceOf[Period]
            }
          },
          new Matcher[Year] {
            def downcastOrNull(a: Any): Year = a match {
              case x: Year => x
              case _       => null.asInstanceOf[Year]
            }
          },
          new Matcher[YearMonth] {
            def downcastOrNull(a: Any): YearMonth = a match {
              case x: YearMonth => x
              case _            => null.asInstanceOf[YearMonth]
            }
          },
          new Matcher[ZoneId] {
            def downcastOrNull(a: Any): ZoneId = a match {
              case x: ZoneId => x
              case _         => null.asInstanceOf[ZoneId]
            }
          },
          new Matcher[ZoneOffset] {
            def downcastOrNull(a: Any): ZoneOffset = a match {
              case x: ZoneOffset => x
              case _             => null.asInstanceOf[ZoneOffset]
            }
          },
          new Matcher[ZonedDateTime] {
            def downcastOrNull(a: Any): ZonedDateTime = a match {
              case x: ZonedDateTime => x
              case _                => null.asInstanceOf[ZonedDateTime]
            }
          },
          new Matcher[Currency] {
            def downcastOrNull(a: Any): Currency = a match {
              case x: Currency => x
              case _           => null.asInstanceOf[Currency]
            }
          },
          new Matcher[UUID] {
            def downcastOrNull(a: Any): UUID = a match {
              case x: UUID => x
              case _       => null.asInstanceOf[UUID]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
