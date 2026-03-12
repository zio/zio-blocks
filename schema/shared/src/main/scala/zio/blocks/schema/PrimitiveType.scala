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

import zio.blocks.schema.binding.{Binding, BindingType}
import zio.blocks.schema.json.JsonWriter
import zio.blocks.typeid.{TypeId, TypeRepr}

sealed trait PrimitiveType[A] {
  def binding: Binding[BindingType.Primitive, A] = new Binding.Primitive[A]

  def fromDynamicValue(value: DynamicValue): Either[SchemaError, A] = fromDynamicValue(value, Nil)

  private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node]): Either[SchemaError, A]

  def toDynamicValue(value: A): DynamicValue

  def typeId: TypeId[A]

  def validation: Validation[A]
}

object PrimitiveType {
  case object Unit extends PrimitiveType[scala.Unit] {
    def validation: Validation[scala.Unit] = Validation.None

    def toDynamicValue(value: scala.Unit): DynamicValue = new DynamicValue.Primitive(PrimitiveValue.Unit)

    def typeId: TypeId[scala.Unit] = TypeId.unit

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Unit] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Unit) => new Right(())
        case _                                           => new Left(SchemaError.expectationMismatch(trace, "Expected Unit"))
      }
  }

  case class Boolean(validation: Validation[scala.Boolean]) extends PrimitiveType[scala.Boolean] {
    def toDynamicValue(value: scala.Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))

    def typeId: TypeId[scala.Boolean] = TypeId.boolean

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Boolean] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => new Right(b)
        case _                                                 => new Left(SchemaError.expectationMismatch(trace, "Expected Boolean"))
      }
  }

  case class Byte(validation: Validation[scala.Byte]) extends PrimitiveType[scala.Byte] {
    def toDynamicValue(value: scala.Byte): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Byte(value))

    def typeId: TypeId[scala.Byte] = TypeId.byte

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Byte] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.Byte(b)    => return new Right(b)
            case PrimitiveValue.Short(s)   => if (s.toByte.toShort == s) return new Right(s.toByte)
            case PrimitiveValue.Char(ch)   => if (ch.toByte.toChar == ch) return new Right(ch.toByte)
            case PrimitiveValue.Int(i)     => if (i.toByte.toInt == i) return new Right(i.toByte)
            case PrimitiveValue.Float(f)   => if (f.toByte.toFloat == f) return new Right(f.toByte)
            case PrimitiveValue.Long(l)    => if (l.toByte.toLong == l) return new Right(l.toByte)
            case PrimitiveValue.Double(d)  => if (d.toByte.toDouble == d) return new Right(d.toByte)
            case PrimitiveValue.BigInt(bi) =>
              val b = bi.toByte
              if (scala.BigInt(b) == bi) return new Right(b)
            case PrimitiveValue.BigDecimal(bd) =>
              val b = bd.toByte
              if (scala.BigDecimal(b) == bd) return new Right(b)
            case _ =>
          }
        case _ =>
      }
      new Left(SchemaError.expectationMismatch(trace, "Expected Byte"))
    }
  }

  case class Short(validation: Validation[scala.Short]) extends PrimitiveType[scala.Short] {
    def toDynamicValue(value: scala.Short): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Short(value))

    def typeId: TypeId[scala.Short] = TypeId.short

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Short] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.Short(s)   => return new Right(s)
            case PrimitiveValue.Byte(b)    => return new Right(b.toShort)
            case PrimitiveValue.Char(ch)   => if (ch.toShort.toChar == ch) return new Right(ch.toShort)
            case PrimitiveValue.Int(i)     => if (i.toShort.toInt == i) return new Right(i.toShort)
            case PrimitiveValue.Float(f)   => if (f.toShort.toFloat == f) return new Right(f.toShort)
            case PrimitiveValue.Long(l)    => if (l.toShort.toLong == l) return new Right(l.toShort)
            case PrimitiveValue.Double(d)  => if (d.toShort.toDouble == d) return new Right(d.toShort)
            case PrimitiveValue.BigInt(bi) =>
              val sh = bi.toShort
              if (scala.BigInt(sh) == bi) return new Right(sh)
            case PrimitiveValue.BigDecimal(bd) =>
              val sh = bd.toShort
              if (scala.BigDecimal(sh) == bd) return new Right(sh)
            case _ =>
          }
        case _ =>
      }
      new Left(SchemaError.expectationMismatch(trace, "Expected Short"))
    }
  }

  case class Int(validation: Validation[scala.Int]) extends PrimitiveType[scala.Int] {
    def toDynamicValue(value: scala.Int): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Int(value))

    def typeId: TypeId[scala.Int] = TypeId.int

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Int] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.Int(i)     => return new Right(i)
            case PrimitiveValue.Byte(b)    => return new Right(b.toInt)
            case PrimitiveValue.Short(s)   => return new Right(s.toInt)
            case PrimitiveValue.Char(ch)   => return new Right(ch.toInt)
            case PrimitiveValue.Float(f)   => if (f.toInt.toFloat == f) return new Right(f.toInt)
            case PrimitiveValue.Long(l)    => if (l.toInt.toLong == l) return new Right(l.toInt)
            case PrimitiveValue.Double(d)  => if (d.toInt.toDouble == d) return new Right(d.toInt)
            case PrimitiveValue.BigInt(bi) =>
              val i = bi.toInt
              if (scala.BigInt(i) == bi) return new Right(i)
            case PrimitiveValue.BigDecimal(bd) =>
              val i = bd.toInt
              if (scala.BigDecimal(i) == bd) return new Right(i)
            case _ =>
          }
        case _ =>
      }
      new Left(SchemaError.expectationMismatch(trace, "Expected Int"))
    }
  }

  case class Long(validation: Validation[scala.Long]) extends PrimitiveType[scala.Long] {
    def toDynamicValue(value: scala.Long): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Long(value))

    def typeId: TypeId[scala.Long] = TypeId.long

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Long] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.Long(l)    => return new Right(l)
            case PrimitiveValue.Byte(b)    => return new Right(b.toLong)
            case PrimitiveValue.Short(s)   => return new Right(s.toLong)
            case PrimitiveValue.Char(ch)   => return new Right(ch.toLong)
            case PrimitiveValue.Int(i)     => return new Right(i.toLong)
            case PrimitiveValue.Float(f)   => if (f.toLong.toFloat == f) return new Right(f.toLong)
            case PrimitiveValue.Double(d)  => if (d.toLong.toDouble == d) return new Right(d.toLong)
            case PrimitiveValue.BigInt(bi) =>
              val l = bi.toLong
              if (scala.BigInt(l) == bi) return new Right(l)
            case PrimitiveValue.BigDecimal(bd) =>
              val l = bd.toLong
              if (scala.BigDecimal(l) == bd) return new Right(l)
            case _ =>
          }
        case _ =>
      }
      new Left(SchemaError.expectationMismatch(trace, "Expected Long"))
    }
  }

  case class Float(validation: Validation[scala.Float]) extends PrimitiveType[scala.Float] {
    def toDynamicValue(value: scala.Float): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Float(value))

    def typeId: TypeId[scala.Float] = TypeId.float

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Float] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.Float(f)   => return new Right(f)
            case PrimitiveValue.Byte(b)    => return new Right(b.toFloat)
            case PrimitiveValue.Short(s)   => return new Right(s.toFloat)
            case PrimitiveValue.Char(ch)   => return new Right(ch.toFloat)
            case PrimitiveValue.Int(i)     => if (i.toFloat.toInt == i) return new Right(i.toFloat)
            case PrimitiveValue.Double(d)  => if (d.toFloat.toDouble == d) return new Right(d.toFloat)
            case PrimitiveValue.Long(l)    => if (l.toFloat.toLong == l) return new Right(l.toFloat)
            case PrimitiveValue.BigInt(bi) =>
              val f = scala.BigDecimal(bi).toFloat
              if (JsonWriter.toBigDecimal(f).toBigInt == bi) return new Right(f)
            case PrimitiveValue.BigDecimal(bd) =>
              val f = bd.toFloat
              if (scala.BigDecimal(f) == bd) return new Right(f)
            case _ =>
          }
        case _ =>
      }
      Left(SchemaError.expectationMismatch(trace, "Expected Float"))
    }
  }

  case class Double(validation: Validation[scala.Double]) extends PrimitiveType[scala.Double] {
    def toDynamicValue(value: scala.Double): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Double(value))

    def typeId: TypeId[scala.Double] = TypeId.double

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Double] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.Double(d)  => return new Right(d)
            case PrimitiveValue.Byte(b)    => return new Right(b.toDouble)
            case PrimitiveValue.Short(s)   => return new Right(s.toDouble)
            case PrimitiveValue.Char(ch)   => return new Right(ch.toDouble)
            case PrimitiveValue.Int(i)     => return new Right(i.toDouble)
            case PrimitiveValue.Float(f)   => return new Right(f.toDouble)
            case PrimitiveValue.Long(l)    => if (l.toDouble.toLong == l) return new Right(l.toDouble)
            case PrimitiveValue.BigInt(bi) =>
              val d = scala.BigDecimal(bi).toDouble
              if (JsonWriter.toBigDecimal(d).toBigInt == bi) return new Right(d)
            case PrimitiveValue.BigDecimal(bd) =>
              val d = bd.toDouble
              if (scala.BigDecimal(d) == bd) return new Right(d)
            case _ =>
          }
        case _ =>
      }
      new Left(SchemaError.expectationMismatch(trace, "Expected Double"))
    }
  }

  case class Char(validation: Validation[scala.Char]) extends PrimitiveType[scala.Char] {
    def toDynamicValue(value: scala.Char): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Char(value))

    def typeId: TypeId[scala.Char] = TypeId.char

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Char] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.Char(ch)   => return new Right(ch)
            case PrimitiveValue.Byte(b)    => if (b.toChar.toByte == b) return new Right(b.toChar)
            case PrimitiveValue.Short(s)   => if (s.toChar.toShort == s) return new Right(s.toChar)
            case PrimitiveValue.Int(i)     => if (i.toChar.toInt == i) return new Right(i.toChar)
            case PrimitiveValue.Long(l)    => if (l.toChar.toLong == l) return new Right(l.toChar)
            case PrimitiveValue.Float(f)   => if (f.toChar.toFloat == f) return new Right(f.toChar)
            case PrimitiveValue.Double(d)  => if (d.toChar.toDouble == d) return new Right(d.toChar)
            case PrimitiveValue.BigInt(bi) =>
              val ch = bi.toChar
              if (scala.BigInt(ch) == bi) return new Right(ch)
            case PrimitiveValue.BigDecimal(bd) =>
              val ch = bd.toInt.toChar
              if (scala.BigDecimal(ch) == bd) return new Right(ch)
            case _ =>
          }
        case _ =>
      }
      new Left(SchemaError.expectationMismatch(trace, "Expected Char"))
    }
  }

  case class String(validation: Validation[Predef.String]) extends PrimitiveType[Predef.String] {
    def toDynamicValue(value: Predef.String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))

    def typeId: TypeId[Predef.String] = TypeId.string

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, Predef.String] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) => new Right(s)
        case _                                                => new Left(SchemaError.expectationMismatch(trace, "Expected String"))
      }
  }

  case class BigInt(validation: Validation[scala.BigInt]) extends PrimitiveType[scala.BigInt] {
    def toDynamicValue(value: scala.BigInt): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.BigInt(value))

    def typeId: TypeId[scala.BigInt] = TypeId.bigInt

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.BigInt] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.BigInt(bi) => return new Right(bi)
            case PrimitiveValue.Byte(b)    => return new Right(scala.BigInt(b))
            case PrimitiveValue.Short(s)   => return new Right(scala.BigInt(s))
            case PrimitiveValue.Char(ch)   => return new Right(scala.BigInt(ch))
            case PrimitiveValue.Int(i)     => return new Right(scala.BigInt(i))
            case PrimitiveValue.Long(l)    => return new Right(scala.BigInt(l))
            case PrimitiveValue.Float(f)   =>
              if (java.lang.Float.isFinite(f)) {
                val bi = JsonWriter.toBigDecimal(f).toBigInt
                if (bi.toFloat == f) return new Right(bi)
              }
            case PrimitiveValue.Double(d) =>
              if (java.lang.Double.isFinite(d)) {
                val bi = JsonWriter.toBigDecimal(d).toBigInt
                if (bi.toDouble == d) return new Right(bi)
              }
            case PrimitiveValue.BigDecimal(bd) =>
              val bi = bd.toBigInt
              if (scala.BigDecimal(bi) == bd) return new Right(bi)
            case _ =>
          }
        case _ =>
      }
      new Left(SchemaError.expectationMismatch(trace, "Expected BigInt"))
    }
  }

  case class BigDecimal(validation: Validation[scala.BigDecimal]) extends PrimitiveType[scala.BigDecimal] {
    def toDynamicValue(value: scala.BigDecimal): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(value))

    def typeId: TypeId[scala.BigDecimal] = TypeId.bigDecimal

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.BigDecimal] = {
      value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.BigDecimal(bd) => return new Right(bd)
            case PrimitiveValue.Byte(b)        => return new Right(scala.BigDecimal(b))
            case PrimitiveValue.Char(ch)       => return new Right(scala.BigDecimal(ch))
            case PrimitiveValue.Short(s)       => return new Right(scala.BigDecimal(s))
            case PrimitiveValue.Int(i)         => return new Right(scala.BigDecimal(i))
            case PrimitiveValue.Float(f)       =>
              if (java.lang.Float.isFinite(f)) return new Right(JsonWriter.toBigDecimal(f))
            case PrimitiveValue.Long(l)   => return new Right(scala.BigDecimal(l))
            case PrimitiveValue.Double(d) =>
              if (java.lang.Double.isFinite(d)) return new Right(JsonWriter.toBigDecimal(d))
            case PrimitiveValue.BigInt(bi) => return new Right(scala.BigDecimal(bi))
            case _                         =>
          }
        case _ =>
      }
      new Left(SchemaError.expectationMismatch(trace, "Expected BigDecimal"))
    }
  }

  case class DayOfWeek(validation: Validation[java.time.DayOfWeek]) extends PrimitiveType[java.time.DayOfWeek] {
    def toDynamicValue(value: java.time.DayOfWeek): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.DayOfWeek(value))

    def typeId: TypeId[java.time.DayOfWeek] = TypeId.dayOfWeek

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.DayOfWeek] = value match {
      case DynamicValue.Primitive(PrimitiveValue.DayOfWeek(d)) => new Right(d)
      case _                                                   => new Left(SchemaError.expectationMismatch(trace, "Expected DayOfWeek"))
    }
  }

  case class Duration(validation: Validation[java.time.Duration]) extends PrimitiveType[java.time.Duration] {
    def toDynamicValue(value: java.time.Duration): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Duration(value))

    def typeId: TypeId[java.time.Duration] = TypeId.duration

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Duration] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Duration(d)) => new Right(d)
        case _                                                  => new Left(SchemaError.expectationMismatch(trace, "Expected Duration"))
      }
  }

  case class Instant(validation: Validation[java.time.Instant]) extends PrimitiveType[java.time.Instant] {
    def toDynamicValue(value: java.time.Instant): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Instant(value))

    def typeId: TypeId[java.time.Instant] = TypeId.instant

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Instant] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Instant(i)) => new Right(i)
        case _                                                 => new Left(SchemaError.expectationMismatch(trace, "Expected Instant"))
      }
  }

  case class LocalDate(validation: Validation[java.time.LocalDate]) extends PrimitiveType[java.time.LocalDate] {
    def toDynamicValue(value: java.time.LocalDate): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalDate(value))

    def typeId: TypeId[java.time.LocalDate] = TypeId.localDate

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.LocalDate] = value match {
      case DynamicValue.Primitive(PrimitiveValue.LocalDate(d)) => new Right(d)
      case _                                                   => new Left(SchemaError.expectationMismatch(trace, "Expected LocalDate"))
    }
  }

  case class LocalDateTime(validation: Validation[java.time.LocalDateTime])
      extends PrimitiveType[java.time.LocalDateTime] {
    def toDynamicValue(value: java.time.LocalDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalDateTime(value))

    def typeId: TypeId[java.time.LocalDateTime] = TypeId.localDateTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.LocalDateTime] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.LocalDateTime(d)) => new Right(d)
        case _                                                       => new Left(SchemaError.expectationMismatch(trace, "Expected LocalDateTime"))
      }
  }

  case class LocalTime(validation: Validation[java.time.LocalTime]) extends PrimitiveType[java.time.LocalTime] {
    def toDynamicValue(value: java.time.LocalTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalTime(value))

    def typeId: TypeId[java.time.LocalTime] = TypeId.localTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.LocalTime] = value match {
      case DynamicValue.Primitive(PrimitiveValue.LocalTime(t)) => new Right(t)
      case _                                                   => new Left(SchemaError.expectationMismatch(trace, "Expected LocalTime"))
    }
  }

  case class Month(validation: Validation[java.time.Month]) extends PrimitiveType[java.time.Month] {
    def toDynamicValue(value: java.time.Month): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Month(value))

    def typeId: TypeId[java.time.Month] = TypeId.month

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Month] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Month(m)) => new Right(m)
        case _                                               => new Left(SchemaError.expectationMismatch(trace, "Expected Month"))
      }
  }

  case class MonthDay(validation: Validation[java.time.MonthDay]) extends PrimitiveType[java.time.MonthDay] {
    def toDynamicValue(value: java.time.MonthDay): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.MonthDay(value))

    def typeId: TypeId[java.time.MonthDay] = TypeId.monthDay

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.MonthDay] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.MonthDay(m)) => new Right(m)
        case _                                                  => new Left(SchemaError.expectationMismatch(trace, "Expected MonthDay"))
      }
  }

  case class OffsetDateTime(validation: Validation[java.time.OffsetDateTime])
      extends PrimitiveType[java.time.OffsetDateTime] {
    def toDynamicValue(value: java.time.OffsetDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.OffsetDateTime(value))

    def typeId: TypeId[java.time.OffsetDateTime] = TypeId.offsetDateTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.OffsetDateTime] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(d)) => new Right(d)
        case _                                                        => new Left(SchemaError.expectationMismatch(trace, "Expected OffsetDateTime"))
      }
  }

  case class OffsetTime(validation: Validation[java.time.OffsetTime]) extends PrimitiveType[java.time.OffsetTime] {
    def toDynamicValue(value: java.time.OffsetTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.OffsetTime(value))

    def typeId: TypeId[java.time.OffsetTime] = TypeId.offsetTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.OffsetTime] = value match {
      case DynamicValue.Primitive(PrimitiveValue.OffsetTime(t)) => new Right(t)
      case _                                                    => new Left(SchemaError.expectationMismatch(trace, "Expected OffsetTime"))
    }
  }

  case class Period(validation: Validation[java.time.Period]) extends PrimitiveType[java.time.Period] {
    def toDynamicValue(value: java.time.Period): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Period(value))

    def typeId: TypeId[java.time.Period] = TypeId.period

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Period] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Period(p)) => new Right(p)
        case _                                                => new Left(SchemaError.expectationMismatch(trace, "Expected Period"))
      }
  }

  case class Year(validation: Validation[java.time.Year]) extends PrimitiveType[java.time.Year] {
    def toDynamicValue(value: java.time.Year): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Year(value))

    def typeId: TypeId[java.time.Year] = TypeId.year

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Year] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Year(y)) => new Right(y)
        case _                                              => new Left(SchemaError.expectationMismatch(trace, "Expected Year"))
      }
  }

  case class YearMonth(validation: Validation[java.time.YearMonth]) extends PrimitiveType[java.time.YearMonth] {
    def toDynamicValue(value: java.time.YearMonth): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.YearMonth(value))

    def typeId: TypeId[java.time.YearMonth] = TypeId.yearMonth

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.YearMonth] = value match {
      case DynamicValue.Primitive(PrimitiveValue.YearMonth(y)) => new Right(y)
      case _                                                   => new Left(SchemaError.expectationMismatch(trace, "Expected YearMonth"))
    }
  }

  case class ZoneId(validation: Validation[java.time.ZoneId]) extends PrimitiveType[java.time.ZoneId] {
    def toDynamicValue(value: java.time.ZoneId): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZoneId(value))

    def typeId: TypeId[java.time.ZoneId] = TypeId.zoneId

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.ZoneId] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.ZoneId(z)) => new Right(z)
        case _                                                => new Left(SchemaError.expectationMismatch(trace, "Expected ZoneId"))
      }
  }

  case class ZoneOffset(validation: Validation[java.time.ZoneOffset]) extends PrimitiveType[java.time.ZoneOffset] {
    def toDynamicValue(value: java.time.ZoneOffset): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZoneOffset(value))

    def typeId: TypeId[java.time.ZoneOffset] = TypeId.zoneOffset

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.ZoneOffset] = value match {
      case DynamicValue.Primitive(PrimitiveValue.ZoneOffset(z)) => new Right(z)
      case _                                                    => new Left(SchemaError.expectationMismatch(trace, "Expected ZoneOffset"))
    }
  }

  case class ZonedDateTime(validation: Validation[java.time.ZonedDateTime])
      extends PrimitiveType[java.time.ZonedDateTime] {
    def toDynamicValue(value: java.time.ZonedDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZonedDateTime(value))

    def typeId: TypeId[java.time.ZonedDateTime] = TypeId.zonedDateTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.ZonedDateTime] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(z)) => new Right(z)
        case _                                                       => new Left(SchemaError.expectationMismatch(trace, "Expected ZonedDateTime"))
      }
  }

  case class UUID(validation: Validation[java.util.UUID]) extends PrimitiveType[java.util.UUID] {
    def toDynamicValue(value: java.util.UUID): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.UUID(value))

    def typeId: TypeId[java.util.UUID] = TypeId.uuid

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.util.UUID] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.UUID(u)) => new Right(u)
        case _                                              => new Left(SchemaError.expectationMismatch(trace, "Expected UUID"))
      }
  }

  case class Currency(validation: Validation[java.util.Currency]) extends PrimitiveType[java.util.Currency] {
    def toDynamicValue(value: java.util.Currency): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Currency(value))

    def typeId: TypeId[java.util.Currency] = TypeId.currency

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.util.Currency] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Currency(c)) => new Right(c)
        case _                                                  => new Left(SchemaError.expectationMismatch(trace, "Expected Currency"))
      }
  }

  /**
   * Attempts to derive a PrimitiveType from a TypeId's underlying
   * representation.
   *
   * This is used for opaque types and type aliases that wrap primitive types.
   * Returns Some if the TypeId's representation refers to a primitive type,
   * None otherwise.
   */
  def fromTypeId[A](typeId: TypeId[A]): Option[PrimitiveType[A]] = {
    if (typeId eq null) return None
    val underlyingRepr: Option[TypeRepr] =
      if (typeId.isOpaque) typeId.representation
      else if (typeId.isAlias) typeId.aliasedTo
      else None

    underlyingRepr.flatMap { repr =>
      typeReprToPrimitiveType(repr).asInstanceOf[Option[PrimitiveType[A]]]
    }
  }

  private[this] def typeReprToPrimitiveType(repr: TypeRepr): Option[PrimitiveType[?]] = repr match {
    case TypeRepr.Ref(id) =>
      val fullName = id.fullName
      if (fullName == "scala.Unit") Some(PrimitiveType.Unit)
      else if (fullName == "scala.Boolean") Some(PrimitiveType.Boolean(Validation.None))
      else if (fullName == "scala.Byte") Some(PrimitiveType.Byte(Validation.None))
      else if (fullName == "scala.Short") Some(PrimitiveType.Short(Validation.None))
      else if (fullName == "scala.Int") Some(PrimitiveType.Int(Validation.None))
      else if (fullName == "scala.Long") Some(PrimitiveType.Long(Validation.None))
      else if (fullName == "scala.Float") Some(PrimitiveType.Float(Validation.None))
      else if (fullName == "scala.Double") Some(PrimitiveType.Double(Validation.None))
      else if (fullName == "scala.Char") Some(PrimitiveType.Char(Validation.None))
      else if (fullName == "java.lang.String") Some(PrimitiveType.String(Validation.None))
      else if (fullName == "scala.BigInt") Some(PrimitiveType.BigInt(Validation.None))
      else if (fullName == "scala.BigDecimal") Some(PrimitiveType.BigDecimal(Validation.None))
      else if (fullName == "java.util.UUID") Some(PrimitiveType.UUID(Validation.None))
      else if (fullName == "java.util.Currency") Some(PrimitiveType.Currency(Validation.None))
      else if (fullName == "java.time.DayOfWeek") Some(PrimitiveType.DayOfWeek(Validation.None))
      else if (fullName == "java.time.Duration") Some(PrimitiveType.Duration(Validation.None))
      else if (fullName == "java.time.Instant") Some(PrimitiveType.Instant(Validation.None))
      else if (fullName == "java.time.LocalDate") Some(PrimitiveType.LocalDate(Validation.None))
      else if (fullName == "java.time.LocalDateTime") Some(PrimitiveType.LocalDateTime(Validation.None))
      else if (fullName == "java.time.LocalTime") Some(PrimitiveType.LocalTime(Validation.None))
      else if (fullName == "java.time.Month") Some(PrimitiveType.Month(Validation.None))
      else if (fullName == "java.time.MonthDay") Some(PrimitiveType.MonthDay(Validation.None))
      else if (fullName == "java.time.OffsetDateTime") Some(PrimitiveType.OffsetDateTime(Validation.None))
      else if (fullName == "java.time.OffsetTime") Some(PrimitiveType.OffsetTime(Validation.None))
      else if (fullName == "java.time.Period") Some(PrimitiveType.Period(Validation.None))
      else if (fullName == "java.time.Year") Some(PrimitiveType.Year(Validation.None))
      else if (fullName == "java.time.YearMonth") Some(PrimitiveType.YearMonth(Validation.None))
      else if (fullName == "java.time.ZoneId") Some(PrimitiveType.ZoneId(Validation.None))
      else if (fullName == "java.time.ZoneOffset") Some(PrimitiveType.ZoneOffset(Validation.None))
      else if (fullName == "java.time.ZonedDateTime") Some(PrimitiveType.ZonedDateTime(Validation.None))
      else None
    case _ => None
  }
}
