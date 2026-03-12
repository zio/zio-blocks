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

package zio.blocks.schema.bson

import org.bson.{
  BsonArray,
  BsonBoolean,
  BsonDocument,
  BsonDouble,
  BsonInt32,
  BsonInt64,
  BsonReader,
  BsonString,
  BsonValue,
  BsonWriter
}
import org.bson.types.{Decimal128, ObjectId}
import zio.blocks.schema.json.Json
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NoStackTrace
import scala.jdk.CollectionConverters._

/**
 * Trace element for BSON decoding errors, indicating the path where an error
 * occurred.
 */
sealed trait BsonTrace

object BsonTrace {
  def render(trace: List[BsonTrace]): String =
    trace.reverse.map {
      case Field(name) => s".$name"
      case Array(idx)  => s"[$idx]"
    }.mkString

  case class Field(name: String) extends BsonTrace
  case class Array(idx: Int)     extends BsonTrace
}

/**
 * Encoder for BSON values.
 */
trait BsonEncoder[A] { self =>

  final def contramap[B](f: B => A): BsonEncoder[B] = new BsonEncoder[B] {
    override def isAbsent(value: B): Boolean = self.isAbsent(f(value))

    def encode(writer: BsonWriter, value: B, ctx: BsonEncoder.EncoderContext): Unit =
      self.encode(writer, f(value), ctx)

    def toBsonValue(value: B): BsonValue = self.toBsonValue(f(value))
  }

  /**
   * @return
   *   true if encoder can skip this value.
   */
  def isAbsent(value: A): Boolean = false

  def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit

  def toBsonValue(value: A): BsonValue
}

object BsonEncoder {
  case class EncoderContext(inlineNextObject: Boolean = false)

  object EncoderContext {
    val default: EncoderContext = EncoderContext()
  }

  val bigInteger: BsonEncoder[JBigInteger] = new BsonEncoder[JBigInteger] {
    def encode(writer: BsonWriter, value: JBigInteger, ctx: EncoderContext): Unit =
      writer.writeString(value.toString)

    def toBsonValue(value: JBigInteger): BsonValue =
      new org.bson.BsonString(value.toString)
  }

  val bsonValueEncoder: BsonEncoder[BsonValue] = new BsonEncoder[BsonValue] {
    def encode(writer: BsonWriter, value: BsonValue, ctx: EncoderContext): Unit = {
      val codec = new org.bson.codecs.BsonValueCodec()
      codec.encode(writer, value, org.bson.codecs.EncoderContext.builder().build())
    }

    def toBsonValue(value: BsonValue): BsonValue = value
  }
}

/**
 * Decoder for BSON values.
 */
trait BsonDecoder[A] { self =>

  def decode(reader: BsonReader): Either[BsonDecoder.Error, A] =
    try Right(decodeUnsafe(reader, Nil, BsonDecoder.BsonDecoderContext.default))
    catch {
      case e: BsonDecoder.Error => Left(e)
    }

  def fromBsonValue(value: BsonValue): Either[BsonDecoder.Error, A] =
    try Right(fromBsonValueUnsafe(value, Nil, BsonDecoder.BsonDecoderContext.default))
    catch {
      case e: BsonDecoder.Error => Left(e)
    }

  def decodeMissingUnsafe(trace: List[BsonTrace]): A =
    throw BsonDecoder.Error(trace, "missing")

  def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A

  def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A

  def map[B](f: A => B): BsonDecoder[B] = new BsonDecoder[B] {
    override def decodeMissingUnsafe(trace: List[BsonTrace]): B = f(self.decodeMissingUnsafe(trace))

    def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): B = f(
      self.decodeUnsafe(reader, trace, ctx)
    )

    def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): B = f(
      self.fromBsonValueUnsafe(value, trace, ctx)
    )
  }

  def mapOrFail[B](f: A => Either[String, B]): BsonDecoder[B] = new BsonDecoder[B] {
    override def decodeMissingUnsafe(trace: List[BsonTrace]): B = f(self.decodeMissingUnsafe(trace)) match {
      case Left(err)    => throw BsonDecoder.Error(trace, err)
      case Right(value) => value
    }

    def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): B =
      f(self.decodeUnsafe(reader, trace, ctx)) match {
        case Left(msg)    => throw BsonDecoder.Error(trace, msg)
        case Right(value) => value
      }

    def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): B =
      f(self.fromBsonValueUnsafe(value, trace, ctx)) match {
        case Left(msg)    => throw BsonDecoder.Error(trace, msg)
        case Right(value) => value
      }
  }
}

object BsonDecoder {
  case class BsonDecoderContext(ignoreExtraField: Option[String] = None)

  object BsonDecoderContext {
    val default: BsonDecoderContext = BsonDecoderContext()
  }

  case class Error(trace: List[BsonTrace], message: String)
      extends RuntimeException(
        s"Path: ${BsonTrace.render(trace)}, error: $message"
      )
      with NoStackTrace {
    def render: String = s"${BsonTrace.render(trace)}: $message"
  }

  val bigInteger: BsonDecoder[JBigInteger] = new BsonDecoder[JBigInteger] {
    def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): JBigInteger =
      new JBigInteger(reader.readString())

    def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoderContext): JBigInteger =
      if (value.isString) new JBigInteger(value.asString().getValue)
      else throw Error(trace, s"Expected STRING but got ${value.getBsonType}")
  }
}

/**
 * Combined encoder and decoder for BSON values.
 */
final case class BsonCodec[A](encoder: BsonEncoder[A], decoder: BsonDecoder[A]) {
  def transform[B](to: A => B)(from: B => A): BsonCodec[B] =
    BsonCodec(encoder.contramap(from), decoder.map(to))

  def transformOrFail[B](to: A => Either[String, B])(from: B => A): BsonCodec[B] =
    BsonCodec(encoder.contramap(from), decoder.mapOrFail(to))
}

object BsonCodec {
  private def primitive[A](
    writeValue: (BsonWriter, A) => Unit,
    toBson: A => BsonValue,
    readValue: BsonReader => A,
    fromBson: (BsonValue, List[BsonTrace]) => A
  ): BsonCodec[A] = BsonCodec(
    new BsonEncoder[A] {
      def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit =
        writeValue(writer, value)
      def toBsonValue(value: A): BsonValue = toBson(value)
    },
    new BsonDecoder[A] {
      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A =
        readValue(reader)
      def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A =
        fromBson(value, trace)
    }
  )

  private def stringBased[A](
    toString: A => String,
    fromString: String => A
  ): BsonCodec[A] = primitive[A](
    (w, v) => w.writeString(toString(v)),
    v => new org.bson.BsonString(toString(v)),
    r => fromString(r.readString()),
    (v, trace) =>
      if (v.isString) fromString(v.asString().getValue)
      else throw BsonDecoder.Error(trace, s"Expected STRING but got ${v.getBsonType}")
  )

  val boolean: BsonCodec[Boolean] = primitive[Boolean](
    (w, v) => w.writeBoolean(v),
    v => new org.bson.BsonBoolean(v),
    r => r.readBoolean(),
    (v, trace) =>
      if (v.isBoolean) v.asBoolean().getValue
      else throw BsonDecoder.Error(trace, s"Expected BOOLEAN but got ${v.getBsonType}")
  )

  val byte: BsonCodec[Byte] = primitive[Byte](
    (w, v) => w.writeInt32(v.toInt),
    v => new org.bson.BsonInt32(v.toInt),
    r => r.readInt32().toByte,
    (v, trace) =>
      if (v.isInt32) v.asInt32().getValue.toByte
      else throw BsonDecoder.Error(trace, s"Expected INT32 but got ${v.getBsonType}")
  )

  val short: BsonCodec[Short] = primitive[Short](
    (w, v) => w.writeInt32(v.toInt),
    v => new org.bson.BsonInt32(v.toInt),
    r => r.readInt32().toShort,
    (v, trace) =>
      if (v.isInt32) v.asInt32().getValue.toShort
      else throw BsonDecoder.Error(trace, s"Expected INT32 but got ${v.getBsonType}")
  )

  val int: BsonCodec[Int] = primitive[Int](
    (w, v) => w.writeInt32(v),
    v => new org.bson.BsonInt32(v),
    r => r.readInt32(),
    (v, trace) =>
      if (v.isInt32) v.asInt32().getValue
      else throw BsonDecoder.Error(trace, s"Expected INT32 but got ${v.getBsonType}")
  )

  val long: BsonCodec[Long] = primitive[Long](
    (w, v) => w.writeInt64(v),
    v => new org.bson.BsonInt64(v),
    r => r.readInt64(),
    (v, trace) =>
      if (v.isInt64) v.asInt64().getValue
      else throw BsonDecoder.Error(trace, s"Expected INT64 but got ${v.getBsonType}")
  )

  val float: BsonCodec[Float] = primitive[Float](
    (w, v) => w.writeDouble(v.toDouble),
    v => new org.bson.BsonDouble(v.toDouble),
    r => r.readDouble().toFloat,
    (v, trace) =>
      if (v.isDouble) v.asDouble().getValue.toFloat
      else throw BsonDecoder.Error(trace, s"Expected DOUBLE but got ${v.getBsonType}")
  )

  val double: BsonCodec[Double] = primitive[Double](
    (w, v) => w.writeDouble(v),
    v => new org.bson.BsonDouble(v),
    r => r.readDouble(),
    (v, trace) =>
      if (v.isDouble) v.asDouble().getValue
      else throw BsonDecoder.Error(trace, s"Expected DOUBLE but got ${v.getBsonType}")
  )

  val char: BsonCodec[Char] = primitive[Char](
    (w, v) => w.writeString(v.toString),
    v => new org.bson.BsonString(v.toString),
    r => {
      val s = r.readString()
      if (s.length == 1) s.charAt(0)
      else throw new RuntimeException(s"Expected single character string but got: $s")
    },
    (v, trace) =>
      if (v.isString) {
        val s = v.asString().getValue
        if (s.length == 1) s.charAt(0)
        else throw BsonDecoder.Error(trace, s"Expected single character string but got: $s")
      } else throw BsonDecoder.Error(trace, s"Expected STRING but got ${v.getBsonType}")
  )

  val string: BsonCodec[String] = primitive[String](
    (w, v) => w.writeString(v),
    v => new org.bson.BsonString(v),
    r => r.readString(),
    (v, trace) =>
      if (v.isString) v.asString().getValue
      else throw BsonDecoder.Error(trace, s"Expected STRING but got ${v.getBsonType}")
  )

  val bigDecimal: BsonCodec[JBigDecimal] = primitive[JBigDecimal](
    (w, v) => w.writeDecimal128(new Decimal128(v)),
    v => new org.bson.BsonDecimal128(new Decimal128(v)),
    r => r.readDecimal128().bigDecimalValue(),
    (v, trace) =>
      if (v.isDecimal128) v.asDecimal128().getValue.bigDecimalValue()
      else throw BsonDecoder.Error(trace, s"Expected DECIMAL128 but got ${v.getBsonType}")
  )

  val uuid: BsonCodec[UUID] = stringBased[UUID](
    _.toString,
    UUID.fromString
  )

  val objectId: BsonCodec[ObjectId] = primitive[ObjectId](
    (w, v) => w.writeObjectId(v),
    v => new org.bson.BsonObjectId(v),
    r => r.readObjectId(),
    (v, trace) =>
      if (v.isObjectId) v.asObjectId().getValue
      else throw BsonDecoder.Error(trace, s"Expected OBJECT_ID but got ${v.getBsonType}")
  )

  val dayOfWeek: BsonCodec[DayOfWeek] = stringBased[DayOfWeek](
    _.toString,
    DayOfWeek.valueOf
  )

  val duration: BsonCodec[Duration] = stringBased[Duration](
    Json.durationRawCodec.encodeToString,
    x =>
      Json.durationRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val instant: BsonCodec[Instant] = stringBased[Instant](
    Json.instantRawCodec.encodeToString,
    x =>
      Json.instantRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val localDate: BsonCodec[LocalDate] = stringBased[LocalDate](
    Json.localDateRawCodec.encodeToString,
    x =>
      Json.localDateRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val localDateTime: BsonCodec[LocalDateTime] = stringBased[LocalDateTime](
    Json.localDateTimeRawCodec.encodeToString,
    x =>
      Json.localDateTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val localTime: BsonCodec[LocalTime] = stringBased[LocalTime](
    Json.localTimeRawCodec.encodeToString,
    x =>
      Json.localTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val month: BsonCodec[Month] = stringBased[Month](
    _.toString,
    Month.valueOf
  )

  val monthDay: BsonCodec[MonthDay] = stringBased[MonthDay](
    Json.monthDayRawCodec.encodeToString,
    x =>
      Json.monthDayRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val offsetDateTime: BsonCodec[OffsetDateTime] = stringBased[OffsetDateTime](
    Json.offsetDateTimeRawCodec.encodeToString,
    x =>
      Json.offsetDateTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val offsetTime: BsonCodec[OffsetTime] = stringBased[OffsetTime](
    Json.offsetTimeRawCodec.encodeToString,
    x =>
      Json.offsetTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val period: BsonCodec[Period] = stringBased[Period](
    Json.periodRawCodec.encodeToString,
    x =>
      Json.periodRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val year: BsonCodec[Year] = stringBased[Year](
    _.toString,
    Year.parse
  )

  val yearMonth: BsonCodec[YearMonth] = stringBased[YearMonth](
    _.toString,
    YearMonth.parse
  )

  val zoneId: BsonCodec[ZoneId] = stringBased[ZoneId](
    _.toString,
    ZoneId.of
  )

  val zoneOffset: BsonCodec[ZoneOffset] = stringBased[ZoneOffset](
    _.toString,
    ZoneOffset.of
  )

  val zonedDateTime: BsonCodec[ZonedDateTime] = stringBased[ZonedDateTime](
    Json.zonedDateTimeRawCodec.encodeToString,
    x =>
      Json.zonedDateTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  val currency: BsonCodec[Currency] = stringBased[Currency](
    _.getCurrencyCode,
    Currency.getInstance
  )
}

/**
 * Helper functions for constructing BSON values in tests.
 */
object BsonBuilder {
  def doc(fields: (String, BsonValue)*): BsonDocument = {
    val document = new BsonDocument()
    fields.foreach { case (key, value) => document.append(key, value) }
    document
  }

  def str(value: String): BsonString = new BsonString(value)

  def int32(value: Int): BsonInt32 = new BsonInt32(value)

  def int64(value: Long): BsonInt64 = new BsonInt64(value)

  def double(value: Double): BsonDouble = new BsonDouble(value)

  def boolean(value: Boolean): BsonBoolean = new BsonBoolean(value)

  def array(values: BsonValue*): BsonArray = new BsonArray(values.asJava)
}
