/*
 * Copyright 2023 ZIO Blocks Maintainers
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

sealed trait PrimitiveValue {
  type Type

  def primitiveType: PrimitiveType[Type]

  def typeIndex: scala.Int

  def compare(that: PrimitiveValue): scala.Int

  final def >(that: PrimitiveValue): Boolean = compare(that) > 0

  final def >=(that: PrimitiveValue): Boolean = compare(that) >= 0

  final def <(that: PrimitiveValue): Boolean = compare(that) < 0

  final def <=(that: PrimitiveValue): Boolean = compare(that) <= 0
}

object PrimitiveValue {
  case object Unit extends PrimitiveValue {
    type Type = scala.Unit

    def primitiveType: PrimitiveType[scala.Unit] = PrimitiveType.Unit

    def typeIndex: scala.Int = 0

    def compare(that: PrimitiveValue): scala.Int = that match {
      case _: Unit.type => 0
      case _            => -that.typeIndex
    }
  }

  case class Boolean(value: scala.Boolean) extends PrimitiveValue {
    type Type = scala.Boolean

    def primitiveType: PrimitiveType[scala.Boolean] = PrimitiveType.Boolean(Validation.None)

    def typeIndex: scala.Int = 1

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Boolean => value.compare(x.value)
      case _          => 1 - that.typeIndex
    }
  }

  case class Byte(value: scala.Byte) extends PrimitiveValue {
    type Type = scala.Byte

    def primitiveType: PrimitiveType[scala.Byte] = PrimitiveType.Byte(Validation.None)

    def typeIndex: scala.Int = 2

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Byte => value - x.value
      case _       => 2 - that.typeIndex
    }
  }

  case class Short(value: scala.Short) extends PrimitiveValue {
    type Type = scala.Short

    def primitiveType: PrimitiveType[scala.Short] = PrimitiveType.Short(Validation.None)

    def typeIndex: scala.Int = 3

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Short => value - x.value
      case _        => 3 - that.typeIndex
    }
  }

  case class Int(value: scala.Int) extends PrimitiveValue {
    type Type = scala.Int

    def primitiveType: PrimitiveType[scala.Int] = PrimitiveType.Int(Validation.None)

    def typeIndex: scala.Int = 4

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Int => value - x.value
      case _      => 4 - that.typeIndex
    }
  }

  case class Long(value: scala.Long) extends PrimitiveValue {
    type Type = scala.Long

    def primitiveType: PrimitiveType[scala.Long] = PrimitiveType.Long(Validation.None)

    def typeIndex: scala.Int = 5

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Long => value.compare(x.value)
      case _       => 5 - that.typeIndex
    }
  }

  case class Float(value: scala.Float) extends PrimitiveValue {
    type Type = scala.Float

    def primitiveType: PrimitiveType[scala.Float] = PrimitiveType.Float(Validation.None)

    def typeIndex: scala.Int = 6

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Float => value.compare(x.value)
      case _        => 6 - that.typeIndex
    }
  }

  case class Double(value: scala.Double) extends PrimitiveValue {
    type Type = scala.Double

    def primitiveType: PrimitiveType[scala.Double] = PrimitiveType.Double(Validation.None)

    def typeIndex: scala.Int = 7

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Double => value.compare(x.value)
      case _         => 7 - that.typeIndex
    }
  }

  case class Char(value: scala.Char) extends PrimitiveValue {
    type Type = scala.Char

    def primitiveType: PrimitiveType[scala.Char] = PrimitiveType.Char(Validation.None)

    def typeIndex: scala.Int = 8

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Char => value.compare(x.value)
      case _       => 8 - that.typeIndex
    }
  }

  case class String(value: Predef.String) extends PrimitiveValue {
    type Type = Predef.String

    def primitiveType: PrimitiveType[Predef.String] = PrimitiveType.String(Validation.None)

    def typeIndex: scala.Int = 9

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: String => value.compareTo(x.value)
      case _         => 9 - that.typeIndex
    }
  }

  case class BigInt(value: scala.BigInt) extends PrimitiveValue {
    type Type = scala.BigInt

    def primitiveType: PrimitiveType[scala.BigInt] = PrimitiveType.BigInt(Validation.None)

    def typeIndex: scala.Int = 10

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: BigInt => value.compareTo(x.value)
      case _         => 10 - that.typeIndex
    }
  }

  case class BigDecimal(value: scala.BigDecimal) extends PrimitiveValue {
    type Type = scala.BigDecimal

    def primitiveType: PrimitiveType[scala.BigDecimal] = PrimitiveType.BigDecimal(Validation.None)

    def typeIndex: scala.Int = 11

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: BigDecimal => value.compareTo(x.value)
      case _             => 11 - that.typeIndex
    }
  }

  case class DayOfWeek(value: java.time.DayOfWeek) extends PrimitiveValue {
    type Type = java.time.DayOfWeek

    def primitiveType: PrimitiveType[java.time.DayOfWeek] = PrimitiveType.DayOfWeek(Validation.None)

    def typeIndex: scala.Int = 12

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: DayOfWeek => value.compareTo(x.value)
      case _            => 12 - that.typeIndex
    }
  }

  case class Duration(value: java.time.Duration) extends PrimitiveValue {
    type Type = java.time.Duration

    def primitiveType: PrimitiveType[java.time.Duration] = PrimitiveType.Duration(Validation.None)

    def typeIndex: scala.Int = 13

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Duration => value.compareTo(x.value)
      case _           => 13 - that.typeIndex
    }
  }

  case class Instant(value: java.time.Instant) extends PrimitiveValue {
    type Type = java.time.Instant

    def primitiveType: PrimitiveType[java.time.Instant] = PrimitiveType.Instant(Validation.None)

    def typeIndex: scala.Int = 14

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Instant => value.compareTo(x.value)
      case _          => 14 - that.typeIndex
    }
  }

  case class LocalDate(value: java.time.LocalDate) extends PrimitiveValue {
    type Type = java.time.LocalDate

    def primitiveType: PrimitiveType[java.time.LocalDate] = PrimitiveType.LocalDate(Validation.None)

    def typeIndex: scala.Int = 15

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalDate => value.compareTo(x.value)
      case _            => 15 - that.typeIndex
    }
  }

  case class LocalDateTime(value: java.time.LocalDateTime) extends PrimitiveValue {
    type Type = java.time.LocalDateTime

    def primitiveType: PrimitiveType[java.time.LocalDateTime] = PrimitiveType.LocalDateTime(Validation.None)

    def typeIndex: scala.Int = 16

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalDateTime => value.compareTo(x.value)
      case _                => 16 - that.typeIndex
    }
  }

  case class LocalTime(value: java.time.LocalTime) extends PrimitiveValue {
    type Type = java.time.LocalTime

    def primitiveType: PrimitiveType[java.time.LocalTime] = PrimitiveType.LocalTime(Validation.None)

    def typeIndex: scala.Int = 17

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalTime => value.compareTo(x.value)
      case _            => 17 - that.typeIndex
    }
  }

  case class Month(value: java.time.Month) extends PrimitiveValue {
    type Type = java.time.Month

    def primitiveType: PrimitiveType[java.time.Month] = PrimitiveType.Month(Validation.None)

    def typeIndex: scala.Int = 18

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Month => value.compareTo(x.value)
      case _        => 18 - that.typeIndex
    }
  }

  case class MonthDay(value: java.time.MonthDay) extends PrimitiveValue {
    type Type = java.time.MonthDay

    def primitiveType: PrimitiveType[java.time.MonthDay] = PrimitiveType.MonthDay(Validation.None)

    def typeIndex: scala.Int = 19

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: MonthDay => value.compareTo(x.value)
      case _           => 19 - that.typeIndex
    }
  }

  case class OffsetDateTime(value: java.time.OffsetDateTime) extends PrimitiveValue {
    type Type = java.time.OffsetDateTime

    def primitiveType: PrimitiveType[java.time.OffsetDateTime] = PrimitiveType.OffsetDateTime(Validation.None)

    def typeIndex: scala.Int = 20

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: OffsetDateTime => value.compareTo(x.value)
      case _                 => 20 - that.typeIndex
    }
  }

  case class OffsetTime(value: java.time.OffsetTime) extends PrimitiveValue {
    type Type = java.time.OffsetTime

    def primitiveType: PrimitiveType[java.time.OffsetTime] = PrimitiveType.OffsetTime(Validation.None)

    def typeIndex: scala.Int = 21

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: OffsetTime => value.compareTo(x.value)
      case _             => 21 - that.typeIndex
    }
  }

  case class Period(value: java.time.Period) extends PrimitiveValue {
    type Type = java.time.Period

    def primitiveType: PrimitiveType[java.time.Period] = PrimitiveType.Period(Validation.None)

    def typeIndex: scala.Int = 22

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Period =>
        val xv = x.value
        (value.toTotalMonths * 30.436875 + value.getDays).compareTo(xv.toTotalMonths * 30.436875 + xv.getDays)
      case _ => 22 - that.typeIndex
    }
  }

  case class Year(value: java.time.Year) extends PrimitiveValue {
    type Type = java.time.Year

    def primitiveType: PrimitiveType[java.time.Year] = PrimitiveType.Year(Validation.None)

    def typeIndex: scala.Int = 23

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Year => value.compareTo(x.value)
      case _       => 23 - that.typeIndex
    }
  }

  case class YearMonth(value: java.time.YearMonth) extends PrimitiveValue {
    type Type = java.time.YearMonth

    def primitiveType: PrimitiveType[java.time.YearMonth] = PrimitiveType.YearMonth(Validation.None)

    def typeIndex: scala.Int = 24

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: YearMonth => value.compareTo(x.value)
      case _            => 24 - that.typeIndex
    }
  }

  case class ZoneId(value: java.time.ZoneId) extends PrimitiveValue {
    type Type = java.time.ZoneId

    def primitiveType: PrimitiveType[java.time.ZoneId] = PrimitiveType.ZoneId(Validation.None)

    def typeIndex: scala.Int = 25

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZoneId => value.getId.compareTo(x.value.getId)
      case _         => 25 - that.typeIndex
    }
  }

  case class ZoneOffset(value: java.time.ZoneOffset) extends PrimitiveValue {
    type Type = java.time.ZoneOffset

    def primitiveType: PrimitiveType[java.time.ZoneOffset] = PrimitiveType.ZoneOffset(Validation.None)

    def typeIndex: scala.Int = 26

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZoneOffset => value.compareTo(x.value)
      case _             => 26 - that.typeIndex
    }
  }

  case class ZonedDateTime(value: java.time.ZonedDateTime) extends PrimitiveValue {
    type Type = java.time.ZonedDateTime

    def primitiveType: PrimitiveType[java.time.ZonedDateTime] = PrimitiveType.ZonedDateTime(Validation.None)

    def typeIndex: scala.Int = 27

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZonedDateTime => value.compareTo(x.value)
      case _                => 27 - that.typeIndex
    }
  }

  case class Currency(value: java.util.Currency) extends PrimitiveValue {
    type Type = java.util.Currency

    def primitiveType: PrimitiveType[java.util.Currency] = PrimitiveType.Currency(Validation.None)

    def typeIndex: scala.Int = 28

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Currency => value.getCurrencyCode.compareTo(x.value.getCurrencyCode)
      case _           => 28 - that.typeIndex
    }
  }

  case class UUID(value: java.util.UUID) extends PrimitiveValue {
    type Type = java.util.UUID

    def primitiveType: PrimitiveType[java.util.UUID] = PrimitiveType.UUID(Validation.None)

    def typeIndex: scala.Int = 29

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: UUID => value.compareTo(x.value)
      case _       => 29 - that.typeIndex
    }
  }

  implicit val ordering: Ordering[PrimitiveValue] = new Ordering[PrimitiveValue] {
    def compare(x: PrimitiveValue, y: PrimitiveValue): scala.Int = x.compare(y)
  }
}
