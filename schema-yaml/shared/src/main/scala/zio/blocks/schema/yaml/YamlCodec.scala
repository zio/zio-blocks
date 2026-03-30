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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.json.{Json, JsonCodec}
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * Abstract base class for YAML binary codecs that serialize/deserialize values
 * of type `A` to and from YAML representations.
 *
 * @tparam A
 *   the type this codec handles
 */
abstract class YamlCodec[A] extends BinaryCodec[A] {
  def decodeValue(in: Yaml): A

  def encodeValue(x: A): Yaml

  /**
   * Attempts to decode a value of type `A` from the string representation.
   */
  def decodeKey(s: String): A = decodeUnsafe(s)

  /**
   * Converts a value to its string representation for use as a YAML key.
   */
  def encodeKey(x: A): String = encodeToString(x)

  /**
   * Throws a [[YamlCodecError]] wrapping the given error and adding a span.
   *
   * @param span
   *   the span to add to the error
   * @param error
   *   the error to wrap
   * @throws YamlCodecError
   *   always
   */
  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: YamlCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ => throw new YamlCodecError(new ::(span, Nil), error.getMessage)
  }

  /**
   * Throws a [[YamlCodecError]] wrapping the given error and adding two spans.
   *
   * @param span1
   *   the first span to add to the error
   * @param span2
   *   the second span to add to the error
   * @param error
   *   the error to wrap
   * @throws YamlCodecError
   *   always
   */
  def error(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: YamlCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ => throw new YamlCodecError(new ::(span1, new ::(span2, Nil)), error.getMessage)
  }

  /**
   * Throws a [[YamlCodecError]] with the given error message.
   *
   * @param message
   *   the error message
   * @throws YamlCodecError
   *   always
   */
  def error(message: String): Nothing = throw new YamlCodecError(Nil, message)

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val bytes = new Array[Byte](input.remaining())
    input.get(bytes)
    decode(bytes)
  }

  override def encode(value: A, output: ByteBuffer): Unit = output.put(encode(value))

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try new Right(decodeValue(YamlReader.readFromBytes(input)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A): Array[Byte] = YamlWriter.writeToBytes(encodeValue(value))

  def decode(input: String): Either[SchemaError, A] =
    try new Right(decodeValue(YamlReader.read(input)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encodeToString(value: A): String = YamlWriter.write(encodeValue(value))

  private[yaml] def decodeUnsafe(input: String): A = decodeValue(YamlReader.read(input))

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      new ExpectationMismatch(
        error match {
          case e: YamlCodecError =>
            var list  = e.spans
            val array = new Array[DynamicOptic.Node](list.size)
            var idx   = 0
            while (list ne Nil) {
              array(idx) = list.head
              idx += 1
              list = list.tail
            }
            new DynamicOptic(Chunk.fromArray(array))
          case _ => DynamicOptic.root
        }, {
          var msg = error.getMessage
          if (msg eq null) msg = s"${error.getClass.getName}: (no message)"
          msg
        }
      ),
      Nil
    )
  )
}

/**
 * Companion object providing primitive [[YamlCodec]] instances and type
 * constants.
 */
object YamlCodec {
  val unitCodec: YamlCodec[Unit] = new YamlCodec[Unit] {
    def decodeValue(yaml: Yaml): Unit = yaml match {
      case Yaml.NullValue                                            => ()
      case Yaml.Scalar(v, _) if v == "null" || v == "~" || v.isEmpty => ()
      case _                                                         => error("Expected null value for Unit")
    }
    def encodeValue(x: Unit): Yaml = Yaml.NullValue
  }
  val booleanCodec: YamlCodec[Boolean] = new YamlCodec[Boolean] {
    def decodeValue(yaml: Yaml): Boolean = yaml match {
      case Yaml.Scalar(v, _) =>
        v.trim match {
          case "true"  => true
          case "false" => false
          case _       => error(s"Invalid boolean value: $v")
        }
      case _ => error("Expected scalar for boolean")
    }
    def encodeValue(x: Boolean): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Bool))
  }
  val byteCodec: YamlCodec[Byte] = new YamlCodec[Byte] {
    def decodeValue(yaml: Yaml): Byte = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toByte
        catch {
          case _: NumberFormatException => error(s"Invalid byte value: $v")
        }
      case _ => error("Expected scalar for byte")
    }
    def encodeValue(x: Byte): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }
  val shortCodec: YamlCodec[Short] = new YamlCodec[Short] {
    def decodeValue(yaml: Yaml): Short = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toShort
        catch {
          case _: NumberFormatException => error(s"Invalid short value: $v")
        }
      case _ => error("Expected scalar for short")
    }
    def encodeValue(x: Short): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }
  val intCodec: YamlCodec[Int] = new YamlCodec[Int] {
    def decodeValue(yaml: Yaml): Int = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toInt
        catch {
          case _: NumberFormatException => error(s"Invalid int value: $v")
        }
      case _ => error("Expected scalar for int")
    }
    def encodeValue(x: Int): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }
  val longCodec: YamlCodec[Long] = new YamlCodec[Long] {
    def decodeValue(yaml: Yaml): Long = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toLong
        catch {
          case _: NumberFormatException => error(s"Invalid long value: $v")
        }
      case _ => error("Expected scalar for long")
    }
    def encodeValue(x: Long): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }
  val floatCodec: YamlCodec[Float] = new YamlCodec[Float] {
    def decodeValue(yaml: Yaml): Float = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toFloat
        catch {
          case _: NumberFormatException => error(s"Invalid float value: $v")
        }
      case _ => error("Expected scalar for float")
    }
    def encodeValue(x: Float): Yaml = Yaml.Scalar(JsonCodec.floatCodec.encodeToString(x), tag = new Some(YamlTag.Float))
  }
  val doubleCodec: YamlCodec[Double] = new YamlCodec[Double] {
    def decodeValue(yaml: Yaml): Double = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toDouble
        catch {
          case _: NumberFormatException => error(s"Invalid double value: $v")
        }
      case _ => error("Expected scalar for double")
    }
    def encodeValue(x: Double): Yaml =
      Yaml.Scalar(JsonCodec.doubleCodec.encodeToString(x), tag = new Some(YamlTag.Float))
  }
  val charCodec: YamlCodec[Char] = new YamlCodec[Char] {
    def decodeValue(yaml: Yaml): Char = yaml match {
      case Yaml.Scalar(v, _) =>
        if (v.length == 1) v.charAt(0)
        else error(s"Expected single character, got: $v")
      case _ => error("Expected scalar for char")
    }
    def encodeValue(x: Char): Yaml = Yaml.Scalar(x.toString)
  }
  val stringCodec: YamlCodec[String] = new YamlCodec[String] {
    def decodeValue(yaml: Yaml): String = yaml match {
      case Yaml.Scalar(v, _) => v
      case Yaml.NullValue    => ""
      case _                 => error("Expected scalar for string")
    }
    def encodeValue(x: String): Yaml = Yaml.Scalar(x)
  }
  val bigIntCodec: YamlCodec[BigInt] = new YamlCodec[BigInt] {
    def decodeValue(yaml: Yaml): BigInt = yaml match {
      case Yaml.Scalar(v, _) =>
        try BigInt(v.trim)
        catch {
          case _: NumberFormatException => error(s"Invalid BigInt value: $v")
        }
      case _ => error("Expected scalar for BigInt")
    }
    def encodeValue(x: BigInt): Yaml = Yaml.Scalar(JsonCodec.bigIntCodec.encodeToString(x))
  }
  val bigDecimalCodec: YamlCodec[BigDecimal] = new YamlCodec[BigDecimal] {
    def decodeValue(yaml: Yaml): BigDecimal = yaml match {
      case Yaml.Scalar(v, _) =>
        try BigDecimal(v.trim)
        catch {
          case _: NumberFormatException => error(s"Invalid BigDecimal value: $v")
        }
      case _ => error("Expected scalar for BigDecimal")
    }
    def encodeValue(x: BigDecimal): Yaml = Yaml.Scalar(JsonCodec.bigDecimalCodec.encodeToString(x))
  }
  val dayOfWeekCodec: YamlCodec[DayOfWeek] = new YamlCodec[DayOfWeek] {
    def decodeValue(yaml: Yaml): DayOfWeek = yaml match {
      case Yaml.Scalar(v, _) => DayOfWeek.valueOf(v.trim.toUpperCase)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: DayOfWeek): Yaml = Yaml.Scalar(x.toString)
  }

  val durationCodec: YamlCodec[Duration] = new YamlCodec[Duration] {
    def decodeValue(yaml: Yaml): Duration = yaml match {
      case Yaml.Scalar(v, _) => Json.durationRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Duration): Yaml = Yaml.Scalar(Json.durationRawCodec.encodeToString(x))
  }
  val instantCodec: YamlCodec[Instant] = new YamlCodec[Instant] {
    def decodeValue(yaml: Yaml): Instant = yaml match {
      case Yaml.Scalar(v, _) => Json.instantRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Instant): Yaml = Yaml.Scalar(Json.instantRawCodec.encodeToString(x))
  }
  val localDateCodec: YamlCodec[LocalDate] = new YamlCodec[LocalDate] {
    def decodeValue(yaml: Yaml): LocalDate = yaml match {
      case Yaml.Scalar(v, _) => Json.localDateRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: LocalDate): Yaml = Yaml.Scalar(Json.localDateRawCodec.encodeToString(x))
  }
  val localDateTimeCodec: YamlCodec[LocalDateTime] = new YamlCodec[LocalDateTime] {
    def decodeValue(yaml: Yaml): LocalDateTime = yaml match {
      case Yaml.Scalar(v, _) => Json.localDateTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: LocalDateTime): Yaml = Yaml.Scalar(Json.localDateTimeRawCodec.encodeToString(x))
  }
  val localTimeCodec: YamlCodec[LocalTime] = new YamlCodec[LocalTime] {
    def decodeValue(yaml: Yaml): LocalTime = yaml match {
      case Yaml.Scalar(v, _) => Json.localTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: LocalTime): Yaml = Yaml.Scalar(Json.localTimeRawCodec.encodeToString(x))
  }
  val monthCodec: YamlCodec[Month] = new YamlCodec[Month] {
    def decodeValue(yaml: Yaml): Month = yaml match {
      case Yaml.Scalar(v, _) => Month.valueOf(v.trim.toUpperCase)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Month): Yaml = Yaml.Scalar(x.toString)
  }
  val monthDayCodec: YamlCodec[MonthDay] = new YamlCodec[MonthDay] {
    def decodeValue(yaml: Yaml): MonthDay = yaml match {
      case Yaml.Scalar(v, _) => Json.monthDayRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: MonthDay): Yaml = Yaml.Scalar(Json.monthDayRawCodec.encodeToString(x))
  }
  val offsetDateTimeCodec: YamlCodec[OffsetDateTime] = new YamlCodec[OffsetDateTime] {
    def decodeValue(yaml: Yaml): OffsetDateTime = yaml match {
      case Yaml.Scalar(v, _) => Json.offsetDateTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: OffsetDateTime): Yaml = Yaml.Scalar(Json.offsetDateTimeRawCodec.encodeToString(x))
  }
  val offsetTimeCodec: YamlCodec[OffsetTime] = new YamlCodec[OffsetTime] {
    def decodeValue(yaml: Yaml): OffsetTime = yaml match {
      case Yaml.Scalar(v, _) => Json.offsetTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: OffsetTime): Yaml = Yaml.Scalar(Json.offsetTimeRawCodec.encodeToString(x))
  }
  val periodCodec: YamlCodec[Period] = new YamlCodec[Period] {
    def decodeValue(yaml: Yaml): Period = yaml match {
      case Yaml.Scalar(v, _) => Json.periodRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Period): Yaml = Yaml.Scalar(Json.periodRawCodec.encodeToString(x))
  }
  val yearCodec: YamlCodec[Year] = new YamlCodec[Year] {
    def decodeValue(yaml: Yaml): Year = yaml match {
      case Yaml.Scalar(v, _) => Year.parse(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Year): Yaml = Yaml.Scalar(x.toString)
  }
  val yearMonthCodec: YamlCodec[YearMonth] = new YamlCodec[YearMonth] {
    def decodeValue(yaml: Yaml): YearMonth = yaml match {
      case Yaml.Scalar(v, _) => YearMonth.parse(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: YearMonth): Yaml = Yaml.Scalar(x.toString)
  }
  val zoneIdCodec: YamlCodec[ZoneId] = new YamlCodec[ZoneId] {
    def decodeValue(yaml: Yaml): ZoneId = yaml match {
      case Yaml.Scalar(v, _) => ZoneId.of(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: ZoneId): Yaml = Yaml.Scalar(x.toString)
  }
  val zoneOffsetCodec: YamlCodec[ZoneOffset] = new YamlCodec[ZoneOffset] {
    def decodeValue(yaml: Yaml): ZoneOffset = yaml match {
      case Yaml.Scalar(v, _) => ZoneOffset.of(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: ZoneOffset): Yaml = Yaml.Scalar(x.toString)
  }
  val zonedDateTimeCodec: YamlCodec[ZonedDateTime] = new YamlCodec[ZonedDateTime] {
    def decodeValue(yaml: Yaml): ZonedDateTime = yaml match {
      case Yaml.Scalar(v, _) => Json.zonedDateTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: ZonedDateTime): Yaml = Yaml.Scalar(Json.zonedDateTimeRawCodec.encodeToString(x))
  }
  val currencyCodec: YamlCodec[Currency] = new YamlCodec[Currency] {
    def decodeValue(yaml: Yaml): Currency = yaml match {
      case Yaml.Scalar(v, _) => Currency.getInstance(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Currency): Yaml = Yaml.Scalar(x.getCurrencyCode)
  }
  val uuidCodec: YamlCodec[UUID] = new YamlCodec[UUID] {
    def decodeValue(yaml: Yaml): UUID = yaml match {
      case Yaml.Scalar(v, _) => UUID.fromString(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: UUID): Yaml = Yaml.Scalar(x.toString)
  }
}
