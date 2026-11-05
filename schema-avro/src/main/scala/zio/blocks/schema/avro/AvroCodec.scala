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

package zio.blocks.schema.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder, DecoderFactory, DirectBinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.codec.BinaryCodec
import java.io.OutputStream
import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * Encodes values of type `A` as Avro binary data and decodes them back, in the
 * layout that its Avro schema, [[avroSchema]], describes. Get one with
 * `Schema#derive(AvroFormat)`. Use it to write records for Kafka topics, data
 * files or other Avro readers, and print [[avroSchema]] to give those readers
 * the matching `.avsc` schema.
 *
 * The bytes carry no field names, type name or schema, so the reader needs the
 * writer's schema. Decoding returns `Left` with a `SchemaError` whose message
 * ends with the path of the part that failed, as in
 * `Unexpected end of input at: .celsius`. Two known bugs: `decode` returns the
 * first value and ignores any bytes after it, and bytes written for one type
 * can decode without an error as a different type whose fields have a similar
 * layout.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class SensorReading(sensorId: String, celsius: Double, takenAt: Long)
 * object SensorReading {
 *   implicit val schema: Schema[SensorReading] = Schema.derived
 * }
 *
 * val codec: AvroCodec[SensorReading] = SensorReading.schema.derive(AvroFormat)
 * val bytes   = codec.encode(SensorReading("t-01", 21.5, 1762329600L)) // 18 bytes
 * val decoded = codec.decode(bytes) // Right(SensorReading(t-01,21.5,1762329600))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/avro/ Avro Codec Module]]
 *
 * @tparam A
 *   The type of values this codec encodes and decodes.
 */
abstract class AvroCodec[A] extends BinaryCodec[A] {

  /**
   * The Avro schema that this codec writes and reads. Its `toString` gives the
   * schema as JSON, the `.avsc` form that other Avro libraries parse.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * val json = Schema[Map[String, Double]].derive(AvroFormat).avroSchema.toString
   * // {"type":"map","values":"double"}
   * }}}
   */
  def avroSchema: AvroSchema

  /**
   * Reads one value from `decoder`, and throws when the input is malformed;
   * `decode` turns the exception into a `Left`. Implement it, together with
   * [[encodeValue]] and [[avroSchema]], to write a codec by hand, and call
   * `error(expectation)` to reject a value:
   *
   * {{{
   * import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
   *
   * val batteryLevel: AvroCodec[Int] = new AvroCodec[Int] {
   *   val avroSchema: org.apache.avro.Schema = AvroCodec.intCodec.avroSchema
   *
   *   def decodeValue(decoder: BinaryDecoder): Int = {
   *     val level = decoder.readInt()
   *     if (level >= 0 && level <= 100) level else error("Expected a level from 0 to 100")
   *   }
   *
   *   def encodeValue(value: Int, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
   * }
   *
   * val rejected = batteryLevel.decode(AvroCodec.intCodec.encode(140)).left.map(_.getMessage)
   * // Left(Expected a level from 0 to 100 at: .)
   * }}}
   */
  def decodeValue(decoder: BinaryDecoder): A

  /**
   * Writes `value` to `encoder` in the layout that [[avroSchema]] describes.
   */
  def encodeValue(value: A, encoder: BinaryEncoder): Unit

  /**
   * Throws a decode error with the message `expectation`. `decode` returns it
   * as a `Left` whose message is `expectation`, then ` at: ` and the path of
   * the failing part, such as `Expected Byte at: .`.
   */
  def error(expectation: String): Nothing = throw new AvroCodecError(Nil, expectation)

  /**
   * Rethrows `error` as a decode error with `span` added to the front of its
   * path. Derived codecs call it when the codec of a field, element, map entry
   * or case fails, so the final message names where decoding stopped. Another
   * exception becomes a decode error with that exception's message, or
   * `Unexpected end of input` for a `java.io.EOFException`.
   */
  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: AvroCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new AvroCodecError(new ::(span, Nil), getMessage(error))
  }

  /**
   * Rethrows `error` as a decode error with `span1` and then `span2` added to
   * the front of its path, as `error(span, error)` does for one node.
   */
  def error(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: AvroCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new AvroCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  /**
   * Decodes one value from the bytes between the buffer's position and limit. A
   * heap buffer is read in place and its position does not move; a direct
   * buffer is copied first, which moves its position to its limit. A known bug:
   * for a heap buffer whose content does not start at index 0 of its backing
   * array, such as one made by `slice()`, it reads from the wrong offset. Bytes
   * after the first value are ignored, a known bug shared with the byte-array
   * `decode`.
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    var pos             = input.position
    val len             = input.limit - pos
    var bs: Array[Byte] = null
    if (input.hasArray) bs = input.array()
    else {
      pos = 0
      bs = new Array[Byte](len)
      input.get(bs)
    }
    try new Right(decodeValue(DecoderFactory.get().binaryDecoder(bs, pos, len, null)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }
  }

  /**
   * Writes `value` at the buffer's position and moves the position past it.
   * Throws `java.nio.BufferOverflowException` when the buffer has too little
   * room, after writing the bytes that fit.
   */
  override def encode(value: A, output: ByteBuffer): Unit = encode(
    value,
    new OutputStream {
      override def write(b: Int): Unit = output.put(b.toByte)

      override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
    }
  )

  /**
   * Decodes one value from `input`. Returns `Left` when the bytes run out, with
   * the message `Unexpected end of input` and the path, or when they hold a
   * value that the type does not accept. A known bug: bytes after the first
   * value are ignored, so input that is too long still decodes to `Right`.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class WindSample(stationId: String, speed: Double)
   * object WindSample {
   *   implicit val schema: Schema[WindSample] = Schema.derived
   * }
   *
   * val codec     = WindSample.schema.derive(AvroFormat)
   * val bytes     = codec.encode(WindSample("w-7", 12.5))
   * val decoded   = codec.decode(bytes) // Right(WindSample(w-7,12.5))
   * val truncated = codec.decode(bytes.take(4)).left.map(_.getMessage)
   * // Left(Unexpected end of input at: .speed)
   * }}}
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try new Right(decodeValue(DecoderFactory.get().binaryDecoder(input, 0, input.length, null)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes `value` into a new byte array.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * val bytes = Schema[List[Int]].derive(AvroFormat).encode(List(1, 2, 3))
   * // Array(6, 2, 4, 6, 0): a block of 3 items as zig-zag ints, then 0 to end the array
   * }}}
   */
  def encode(value: A): Array[Byte] = {
    val output = new ByteArrayOutputStream
    encode(value, output)
    output.toByteArray
  }

  /**
   * Reads one value from `input` and leaves the stream after its last byte, so
   * values written one after another come back one per call. Returns `Left`
   * with the message `Unexpected end of input` and the path when the stream
   * ends first. It does not close the stream.
   *
   * {{{
   * import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
   * import zio.blocks.schema._
   *
   * case class HumiditySample(sensorId: String, percent: Int)
   * object HumiditySample {
   *   implicit val schema: Schema[HumiditySample] = Schema.derived
   * }
   *
   * val codec = HumiditySample.schema.derive(AvroFormat)
   * val out   = new ByteArrayOutputStream()
   * codec.encode(HumiditySample("h-1", 48), out)
   * codec.encode(HumiditySample("h-2", 61), out)
   *
   * val in     = new ByteArrayInputStream(out.toByteArray)
   * val first  = codec.decode(in) // Right(HumiditySample(h-1,48))
   * val second = codec.decode(in) // Right(HumiditySample(h-2,61))
   * val third  = codec.decode(in).left.map(_.getMessage)
   * // Left(Unexpected end of input at: .sensorId)
   * }}}
   */
  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    try new Right(decodeValue(DecoderFactory.get().directBinaryDecoder(input, null)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Writes `value` to `output` with no buffering of its own. It neither flushes
   * nor closes the stream.
   */
  def encode(value: A, output: java.io.OutputStream): Unit = encodeValue(value, new DirectBinaryEncoder(output) {})

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: AvroCodecError =>
          var list  = e.spans
          val array = new Array[DynamicOptic.Node](list.size)
          var idx   = 0
          while (list ne Nil) {
            array(idx) = list.head
            idx += 1
            list = list.tail
          }
          new ExpectationMismatch(new DynamicOptic(Chunk.fromArray(array)), e.getMessage)
        case _ => new ExpectationMismatch(DynamicOptic.root, getMessage(error))
      },
      Nil
    )
  )

  private[this] def getMessage(error: Throwable): String = error match {
    case _: java.io.EOFException => "Unexpected end of input"
    case _                       =>
      var msg = error.getMessage
      if (msg eq null) msg = s"${error.getClass.getName}: (no message)"
      msg
  }
}

/**
 * The built-in codecs for the primitive types, which [[AvroCodecDeriver]] uses
 * for primitive fields and values: `Schema[Int].derive(AvroFormat)` returns
 * [[intCodec]] itself. Each one names the Avro type that it writes. The
 * `java.time` records and the `fixed` types for `UUID` and `Currency` are not
 * the Avro logical types (`date`, `timestamp-millis`, `uuid` and the others).
 */
object AvroCodec {

  /**
   * The largest number of entries, 2147483639 (`Int.MaxValue - 8`), that a
   * derived sequence or map codec decodes. Larger input returns `Left` with
   * `Expected collection size not greater than 2147483639, got <n>`, or
   * `Expected map size ...` for a map.
   */
  val maxCollectionSize: Int = Integer.MAX_VALUE - 8

  /** Writes `Unit` as Avro `null`, which takes no bytes. */
  val unitCodec: AvroCodec[Unit] = new AvroCodec[Unit] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.NULL)

    def decodeValue(decoder: BinaryDecoder): Unit = ()

    def encodeValue(value: Unit, encoder: BinaryEncoder): Unit = ()
  }

  /** Writes a `Boolean` as Avro `boolean`, one byte. */
  val booleanCodec: AvroCodec[Boolean] = new AvroCodec[Boolean] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BOOLEAN)

    def decodeValue(decoder: BinaryDecoder): Boolean = decoder.readBoolean()

    def encodeValue(value: Boolean, encoder: BinaryEncoder): Unit = encoder.writeBoolean(value)
  }

  /**
   * Writes a `Byte` as Avro `int`. Decoding an `int` outside the `Byte` range
   * fails with `Expected Byte`.
   */
  val byteCodec: AvroCodec[Byte] = new AvroCodec[Byte] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

    def decodeValue(decoder: BinaryDecoder): Byte = {
      val x = decoder.readInt()
      if (x >= Byte.MinValue && x <= Byte.MaxValue) x.toByte
      else error("Expected Byte")
    }

    def encodeValue(value: Byte, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  /**
   * Writes a `Short` as Avro `int`. Decoding an `int` outside the `Short` range
   * fails with `Expected Short`.
   */
  val shortCodec: AvroCodec[Short] = new AvroCodec[Short] {
    val avroSchema: AvroSchema = byteCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Short = {
      val x = decoder.readInt()
      if (x >= Short.MinValue && x <= Short.MaxValue) x.toShort
      else error("Expected Short")
    }

    def encodeValue(value: Short, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  /**
   * Writes an `Int` as Avro `int`, a zig-zag variable-length number of one to
   * five bytes.
   */
  val intCodec: AvroCodec[Int] = new AvroCodec[Int] {
    val avroSchema: AvroSchema = shortCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Int = decoder.readInt()

    def encodeValue(value: Int, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  /**
   * Writes a `Long` as Avro `long`, a zig-zag variable-length number of one to
   * ten bytes.
   */
  val longCodec: AvroCodec[Long] = new AvroCodec[Long] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.LONG)

    def decodeValue(decoder: BinaryDecoder): Long = decoder.readLong()

    def encodeValue(value: Long, encoder: BinaryEncoder): Unit = encoder.writeLong(value)
  }

  /** Writes a `Float` as Avro `float`, four bytes. */
  val floatCodec: AvroCodec[Float] = new AvroCodec[Float] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.FLOAT)

    def decodeValue(decoder: BinaryDecoder): Float = decoder.readFloat()

    def encodeValue(value: Float, encoder: BinaryEncoder): Unit = encoder.writeFloat(value)
  }

  /** Writes a `Double` as Avro `double`, eight bytes. */
  val doubleCodec: AvroCodec[Double] = new AvroCodec[Double] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.DOUBLE)

    def decodeValue(decoder: BinaryDecoder): Double = decoder.readDouble()

    def encodeValue(value: Double, encoder: BinaryEncoder): Unit = encoder.writeDouble(value)
  }

  /**
   * Writes a `Char` as Avro `int`, its UTF-16 code unit, not as a one-letter
   * string. Decoding an `int` outside the `Char` range fails with
   * `Expected Char`.
   */
  val charCodec: AvroCodec[Char] = new AvroCodec[Char] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Char = {
      val x = decoder.readInt()
      if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
      else error("Expected Char")
    }

    def encodeValue(value: Char, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  /**
   * Writes a `String` as Avro `string`: its UTF-8 length, then the UTF-8 bytes.
   */
  val stringCodec: AvroCodec[String] = new AvroCodec[String] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

    def decodeValue(decoder: BinaryDecoder): String = decoder.readString()

    def encodeValue(value: String, encoder: BinaryEncoder): Unit = encoder.writeString(value)
  }

  /**
   * Writes a `BigInt` as Avro `bytes`, its two's-complement big-endian bytes.
   */
  val bigIntCodec: AvroCodec[BigInt] = new AvroCodec[BigInt] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BYTES)

    def decodeValue(decoder: BinaryDecoder): BigInt = BigInt(decoder.readBytes(null).array())

    def encodeValue(value: BigInt, encoder: BinaryEncoder): Unit = encoder.writeBytes(value.toByteArray)
  }

  /**
   * Writes a `BigDecimal` as the Avro record `scala.BigDecimal`: `mantissa`
   * (`bytes`, the unscaled value), `scale`, `precision` and `roundingMode`
   * (each `int`; the last two come from its `MathContext`). It is not the Avro
   * `decimal` logical type.
   */
  val bigDecimalCodec: AvroCodec[BigDecimal] = new AvroCodec[BigDecimal] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](4)
      fields.add(new AvroSchema.Field("mantissa", bigIntCodec.avroSchema))
      fields.add(new AvroSchema.Field("scale", intAvroSchema))
      fields.add(new AvroSchema.Field("precision", intAvroSchema))
      fields.add(new AvroSchema.Field("roundingMode", intAvroSchema))
      createAvroRecord("scala", "BigDecimal", fields)
    }

    def decodeValue(decoder: BinaryDecoder): BigDecimal = {
      val mantissa     = decoder.readBytes(null).array()
      val scale        = decoder.readInt()
      val precision    = decoder.readInt()
      val roundingMode = java.math.RoundingMode.valueOf(decoder.readInt())
      val mc           = new MathContext(precision, roundingMode)
      new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
    }

    def encodeValue(value: BigDecimal, encoder: BinaryEncoder): Unit = {
      val bd = value.underlying
      val mc = value.mc
      encoder.writeBytes(ByteBuffer.wrap(bd.unscaledValue.toByteArray))
      encoder.writeInt(bd.scale)
      encoder.writeInt(mc.getPrecision)
      encoder.writeInt(mc.getRoundingMode.ordinal)
    }
  }

  /** Writes a `DayOfWeek` as Avro `int`, from 1 (Monday) to 7 (Sunday). */
  val dayOfWeekCodec: AvroCodec[DayOfWeek] = new AvroCodec[DayOfWeek] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): DayOfWeek = DayOfWeek.of(decoder.readInt())

    def encodeValue(value: DayOfWeek, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  /**
   * Writes a `Duration` as the Avro record `java.time.Duration`, with `seconds`
   * (`long`) and `nano` (`int`).
   */
  val durationCodec: AvroCodec[Duration] = new AvroCodec[Duration] {
    val avroSchema: AvroSchema = {
      val fields = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("seconds", longCodec.avroSchema))
      fields.add(new AvroSchema.Field("nano", intCodec.avroSchema))
      createAvroRecord("java.time", "Duration", fields)
    }

    def decodeValue(decoder: BinaryDecoder): Duration = Duration.ofSeconds(decoder.readLong(), decoder.readInt())

    def encodeValue(value: Duration, encoder: BinaryEncoder): Unit = {
      encoder.writeLong(value.getSeconds)
      encoder.writeInt(value.getNano)
    }
  }

  /**
   * Writes an `Instant` as the Avro record `java.time.Instant`, with
   * `epochSecond` (`long`) and `nano` (`int`).
   */
  val instantCodec: AvroCodec[Instant] = new AvroCodec[Instant] {
    val avroSchema: AvroSchema = {
      val fields = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("epochSecond", longCodec.avroSchema))
      fields.add(new AvroSchema.Field("nano", intCodec.avroSchema))
      createAvroRecord("java.time", "Instant", fields)
    }

    def decodeValue(decoder: BinaryDecoder): Instant = Instant.ofEpochSecond(decoder.readLong(), decoder.readInt())

    def encodeValue(value: Instant, encoder: BinaryEncoder): Unit = {
      encoder.writeLong(value.getEpochSecond)
      encoder.writeInt(value.getNano)
    }
  }

  /**
   * Writes a `LocalDate` as the Avro record `java.time.LocalDate`, with the
   * `int` fields `year`, `month` and `day`.
   */
  val localDateCodec: AvroCodec[LocalDate] = new AvroCodec[LocalDate] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](3)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      createAvroRecord("java.time", "LocalDate", fields)
    }

    def decodeValue(decoder: BinaryDecoder): LocalDate =
      LocalDate.of(decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encodeValue(value: LocalDate, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
    }
  }

  /**
   * Writes a `LocalDateTime` as the Avro record `java.time.LocalDateTime`, with
   * the `int` fields `year`, `month`, `day`, `hour`, `minute`, `second` and
   * `nano`.
   */
  val localDateTimeCodec: AvroCodec[LocalDateTime] = new AvroCodec[LocalDateTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](7)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      createAvroRecord("java.time", "LocalDateTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): LocalDateTime =
      LocalDateTime
        .of(
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt()
        )

    def encodeValue(value: LocalDateTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
    }
  }

  /**
   * Writes a `LocalTime` as the Avro record `java.time.LocalTime`, with the
   * `int` fields `hour`, `minute`, `second` and `nano`.
   */
  val localTimeCodec: AvroCodec[LocalTime] = new AvroCodec[LocalTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](4)
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      createAvroRecord("java.time", "LocalTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): LocalTime =
      LocalTime.of(decoder.readInt(), decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encodeValue(value: LocalTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
    }
  }

  /** Writes a `Month` as Avro `int`, from 1 (January) to 12 (December). */
  val monthCodec: AvroCodec[Month] = new AvroCodec[Month] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Month = Month.of(decoder.readInt())

    def encodeValue(value: Month, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  /**
   * Writes a `MonthDay` as the Avro record `java.time.MonthDay`, with the `int`
   * fields `month` and `day`.
   */
  val monthDayCodec: AvroCodec[MonthDay] = new AvroCodec[MonthDay] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      createAvroRecord("java.time", "MonthDay", fields)
    }

    def decodeValue(decoder: BinaryDecoder): MonthDay = MonthDay.of(decoder.readInt(), decoder.readInt())

    def encodeValue(value: MonthDay, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
    }
  }

  /**
   * Writes an `OffsetDateTime` as the Avro record `java.time.OffsetDateTime`:
   * the `int` fields of `localDateTimeCodec`, then `offset`, the offset in
   * seconds.
   */
  val offsetDateTimeCodec: AvroCodec[OffsetDateTime] = new AvroCodec[OffsetDateTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](8)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      fields.add(new AvroSchema.Field("offset", intAvroSchema))
      createAvroRecord("java.time", "OffsetDateTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): OffsetDateTime =
      OffsetDateTime.of(
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        ZoneOffset.ofTotalSeconds(decoder.readInt())
      )

    def encodeValue(value: OffsetDateTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
      encoder.writeInt(value.getOffset.getTotalSeconds)
    }
  }

  /**
   * Writes an `OffsetTime` as the Avro record `java.time.OffsetTime`: the `int`
   * fields of `localTimeCodec`, then `offset`, the offset in seconds.
   */
  val offsetTimeCodec: AvroCodec[OffsetTime] = new AvroCodec[OffsetTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](5)
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      fields.add(new AvroSchema.Field("offset", intAvroSchema))
      createAvroRecord("java.time", "OffsetTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): OffsetTime =
      OffsetTime.of(
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        ZoneOffset.ofTotalSeconds(decoder.readInt())
      )

    def encodeValue(value: OffsetTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
      encoder.writeInt(value.getOffset.getTotalSeconds)
    }
  }

  /**
   * Writes a `Period` as the Avro record `java.time.Period`, with the `int`
   * fields `years`, `months` and `days`.
   */
  val periodCodec: AvroCodec[Period] = new AvroCodec[Period] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](3)
      fields.add(new AvroSchema.Field("years", intAvroSchema))
      fields.add(new AvroSchema.Field("months", intAvroSchema))
      fields.add(new AvroSchema.Field("days", intAvroSchema))
      createAvroRecord("java.time", "Period", fields)
    }

    def decodeValue(decoder: BinaryDecoder): Period = Period.of(decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encodeValue(value: Period, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYears)
      encoder.writeInt(value.getMonths)
      encoder.writeInt(value.getDays)
    }
  }

  /** Writes a `Year` as Avro `int`. */
  val yearCodec: AvroCodec[Year] = new AvroCodec[Year] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Year = Year.of(decoder.readInt())

    def encodeValue(value: Year, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  /**
   * Writes a `YearMonth` as the Avro record `java.time.YearMonth`, with the
   * `int` fields `year` and `month`.
   */
  val yearMonthCodec: AvroCodec[YearMonth] = new AvroCodec[YearMonth] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      createAvroRecord("java.time", "YearMonth", fields)
    }

    def decodeValue(decoder: BinaryDecoder): YearMonth = YearMonth.of(decoder.readInt(), decoder.readInt())

    def encodeValue(value: YearMonth, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
    }
  }

  /** Writes a `ZoneId` as Avro `string`, its ID such as `Asia/Tehran`. */
  val zoneIdCodec: AvroCodec[ZoneId] = new AvroCodec[ZoneId] {
    val avroSchema: AvroSchema = stringCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): ZoneId = ZoneId.of(decoder.readString())

    def encodeValue(value: ZoneId, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
  }

  /** Writes a `ZoneOffset` as Avro `int`, the offset in seconds. */
  val zoneOffsetCodec: AvroCodec[ZoneOffset] = new AvroCodec[ZoneOffset] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): ZoneOffset = ZoneOffset.ofTotalSeconds(decoder.readInt())

    def encodeValue(value: ZoneOffset, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getTotalSeconds)
  }

  /**
   * Writes a `ZonedDateTime` as the Avro record `java.time.ZonedDateTime`: the
   * `int` fields of `localDateTimeCodec`, `offset` (seconds) and `zone`
   * (`string`, the zone ID).
   */
  val zonedDateTimeCodec: AvroCodec[ZonedDateTime] = new AvroCodec[ZonedDateTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](9)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      fields.add(new AvroSchema.Field("offset", intAvroSchema))
      fields.add(new AvroSchema.Field("zone", stringCodec.avroSchema))
      createAvroRecord("java.time", "ZonedDateTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): ZonedDateTime =
      ZonedDateTime.ofInstant(
        LocalDateTime.of(
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt()
        ),
        ZoneOffset.ofTotalSeconds(decoder.readInt()),
        ZoneId.of(decoder.readString())
      )

    def encodeValue(value: ZonedDateTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
      encoder.writeInt(value.getOffset.getTotalSeconds)
      encoder.writeString(value.getZone.toString)
    }
  }

  /**
   * Writes a `Currency` as the Avro `fixed` type `java.util.Currency` of 3
   * bytes, its ISO 4217 code such as `EUR`.
   */
  val currencyCodec: AvroCodec[Currency] = new AvroCodec[java.util.Currency] {
    val avroSchema: AvroSchema = AvroSchema.createFixed("Currency", null, "java.util", 3)

    def decodeValue(decoder: BinaryDecoder): java.util.Currency = {
      val bs = new Array[Byte](3)
      decoder.readFixed(bs, 0, 3)
      java.util.Currency.getInstance(new String(bs))
    }

    def encodeValue(value: java.util.Currency, encoder: BinaryEncoder): Unit = {
      val s = value.toString
      encoder.writeFixed(Array(s.charAt(0).toByte, s.charAt(1).toByte, s.charAt(2).toByte))
    }
  }

  /**
   * Writes a `UUID` as the Avro `fixed` type `java.util.UUID` of 16 bytes: the
   * most significant 64 bits, then the least significant, both big-endian. It
   * is not the Avro `uuid` logical type, which is a string.
   */
  val uuidCodec: AvroCodec[UUID] = new AvroCodec[java.util.UUID] {
    val avroSchema: AvroSchema = AvroSchema.createFixed("UUID", null, "java.util", 16)

    def decodeValue(decoder: BinaryDecoder): java.util.UUID = {
      val bs = new Array[Byte](16)
      decoder.readFixed(bs)
      val hi =
        (bs(0) & 0xff).toLong << 56 |
          (bs(1) & 0xff).toLong << 48 |
          (bs(2) & 0xff).toLong << 40 |
          (bs(3) & 0xff).toLong << 32 |
          (bs(4) & 0xff).toLong << 24 |
          (bs(5) & 0xff) << 16 |
          (bs(6) & 0xff) << 8 |
          (bs(7) & 0xff)
      val lo =
        (bs(8) & 0xff).toLong << 56 |
          (bs(9) & 0xff).toLong << 48 |
          (bs(10) & 0xff).toLong << 40 |
          (bs(11) & 0xff).toLong << 32 |
          (bs(12) & 0xff).toLong << 24 |
          (bs(13) & 0xff) << 16 |
          (bs(14) & 0xff) << 8 |
          (bs(15) & 0xff)
      new java.util.UUID(hi, lo)
    }

    def encodeValue(value: java.util.UUID, encoder: BinaryEncoder): Unit = {
      val hi = value.getMostSignificantBits
      val lo = value.getLeastSignificantBits
      val bs = Array(
        (hi >> 56).toByte,
        (hi >> 48).toByte,
        (hi >> 40).toByte,
        (hi >> 32).toByte,
        (hi >> 24).toByte,
        (hi >> 16).toByte,
        (hi >> 8).toByte,
        hi.toByte,
        (lo >> 56).toByte,
        (lo >> 48).toByte,
        (lo >> 40).toByte,
        (lo >> 32).toByte,
        (lo >> 24).toByte,
        (lo >> 16).toByte,
        (lo >> 8).toByte,
        lo.toByte
      )
      encoder.writeFixed(bs)
    }
  }

  private[this] def createAvroRecord(
    namespace: String,
    name: String,
    fields: java.util.ArrayList[AvroSchema.Field]
  ): AvroSchema = AvroSchema.createRecord(name, null, namespace, false, fields)
}

/** A decode error with the path of the failing part; it has no stack trace. */
private class AvroCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false)

/**
 * An unsynchronized output stream that collects bytes in an array, which
 * `AvroCodec#encode(value)` uses instead of `java.io.ByteArrayOutputStream`.
 * The array starts at 64 bytes and, when full, doubles or grows to the needed
 * length, whichever is larger.
 */
private class ByteArrayOutputStream extends java.io.OutputStream {
  private[this] var buf   = new Array[Byte](64)
  private[this] var count = 0

  override def write(b: Int): Unit = {
    if (count >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
    buf(count) = b.toByte
    count += 1
  }

  override def write(bs: Array[Byte], off: Int, len: Int): Unit = {
    val newLen = count + len
    if (newLen > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, newLen))
    System.arraycopy(bs, off, buf, count, len)
    count = newLen
  }

  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)
}
