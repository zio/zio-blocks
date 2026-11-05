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

package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.json.{Json, JsonCodec}
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * Converts values of type `A` to and from XML: to the [[Xml]] tree
 * (`encodeValue`/`decodeValue`), to a `String` (`encodeToString`/`decode`) and
 * to UTF-8 bytes (`encode`/`decode`).
 *
 * Get one by deriving it from a schema with `Schema#derive(XmlFormat)`. A
 * record becomes an element named after its type, with one child element per
 * field; see [[XmlCodecDeriver]] for the full mapping. The `decode` methods
 * return a `Left` for malformed XML or a tree that does not match the type,
 * with the path to the failing value in the error message.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Reading(sensor: String, celsius: Double)
 * object Reading {
 *   implicit val schema: Schema[Reading] = Schema.derived
 * }
 *
 * val codec: XmlCodec[Reading] = Schema[Reading].derive(XmlFormat)
 * val xml                      = codec.encodeToString(Reading("t-1", 21.5))
 * // <Reading><sensor>t-1</sensor><celsius>21.5</celsius></Reading>
 *
 * codec.decode(xml) // Right(Reading(t-1,21.5))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/xml/ XML]]
 */
abstract class XmlCodec[A] extends BinaryCodec[A] {

  /**
   * Decodes a value from an XML tree. Unlike `decode`, it throws on a mismatch,
   * usually an [[XmlCodecError]].
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Order(id: String, qty: Int)
   * object Order {
   *   implicit val schema: Schema[Order] = Schema.derived
   * }
   *
   * val codec = Schema[Order].derive(XmlFormat)
   * codec.decodeValue(XmlReader.read("<Order><id>B-2</id><qty>5</qty></Order>")) // Order(B-2,5)
   * }}}
   */
  def decodeValue(in: Xml): A

  /**
   * Encodes a value as an XML tree, for further navigation or patching before
   * printing.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Order(id: String, qty: Int)
   * object Order {
   *   implicit val schema: Schema[Order] = Schema.derived
   * }
   *
   * val xml: Xml = Schema[Order].derive(XmlFormat).encodeValue(Order("A-17", 2))
   * xml.select.get("qty").text // Right(2)
   * }}}
   */
  def encodeValue(x: A): Xml

  /**
   * Throws an [[XmlCodecError]] with the given span prepended to its path. If
   * `error` is already an [[XmlCodecError]], it is rethrown with the span
   * added; otherwise a new one is thrown with the same message (without the
   * original exception as its cause).
   *
   * @param span
   *   the span to add to the error
   * @param error
   *   the error to wrap
   * @throws zio.blocks.schema.xml.XmlCodecError
   *   always
   */
  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: XmlCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ => throw new XmlCodecError(new ::(span, Nil), error.getMessage)
  }

  /**
   * Throws an [[XmlCodecError]] with `span1` and `span2` prepended to its path,
   * in that order. If `error` is already an [[XmlCodecError]], it is rethrown
   * with the spans added; otherwise a new one is thrown with the same message.
   *
   * @param span1
   *   the first span to add to the error
   * @param span2
   *   the second span to add to the error
   * @param error
   *   the error to wrap
   * @throws zio.blocks.schema.xml.XmlCodecError
   *   always
   */
  def error(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: XmlCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ => throw new XmlCodecError(new ::(span1, new ::(span2, Nil)), error.getMessage)
  }

  /**
   * Throws an [[XmlCodecError]] with the given error message and an empty path.
   *
   * @param message
   *   the error message
   * @throws zio.blocks.schema.xml.XmlCodecError
   *   always
   */
  def error(message: String): Nothing = throw new XmlCodecError(Nil, message)

  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  override def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig.default)

  /**
   * Decodes a value from the remaining bytes of `input`, read as UTF-8, and
   * consumes them.
   */
  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] = {
    val bytes = new Array[Byte](input.remaining())
    input.get(bytes)
    decode(bytes, config)
  }

  /**
   * Encodes a value and puts the bytes into `output`, in the charset named by
   * `config.encoding`.
   *
   * @throws java.nio.BufferOverflowException
   *   if `output` has less room than the encoded bytes
   * @throws java.nio.charset.UnsupportedCharsetException
   *   if `config.encoding` names a charset the platform does not support
   * @throws java.nio.charset.IllegalCharsetNameException
   *   if `config.encoding` is not a legal charset name
   */
  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = output.put(encode(value, config))

  /** Decodes a value from UTF-8 bytes with the default [[ReaderConfig]]. */
  def decode(input: Array[Byte]): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  /** Encodes a value as compact UTF-8 bytes without an XML declaration. */
  def encode(value: A): Array[Byte] = encode(value, WriterConfig.default)

  /**
   * Decodes a value from bytes, read as UTF-8 whatever the XML declaration
   * says. The limits of `config` apply; its `preserveWhitespace` is ignored
   * (text is always kept as written). Returns a `Left` for malformed XML and
   * for a tree that does not match the type.
   */
  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try {
      // Force preserveWhitespace=true for codec decoding to ensure text content is preserved correctly
      new Right(decodeValue(XmlReader.readFromBytes(input, config.copy(preserveWhitespace = true))))
    } catch {
      case err if NonFatal(err) => new Left(toError(err))
    }

  /**
   * Encodes a value as bytes in the charset named by `config.encoding`.
   *
   * @throws java.nio.charset.UnsupportedCharsetException
   *   if `config.encoding` names a charset the platform does not support
   * @throws java.nio.charset.IllegalCharsetNameException
   *   if `config.encoding` is not a legal charset name
   */
  def encode(value: A, config: WriterConfig): Array[Byte] = XmlWriter.writeToBytes(encodeValue(value), config)

  /**
   * Decodes a value from an XML string with the default [[ReaderConfig]].
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Order(id: String, qty: Int)
   * object Order {
   *   implicit val schema: Schema[Order] = Schema.derived
   * }
   *
   * val codec = Schema[Order].derive(XmlFormat)
   * codec.decode("<Order><id>A-17</id><qty>two</qty></Order>").left.map(_.getMessage)
   * // Left(Invalid int value: two at: .qty)
   * }}}
   */
  def decode(input: String): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  /** Encodes a value as a compact XML string without an XML declaration. */
  def encodeToString(value: A): String = encodeToString(value, WriterConfig.default)

  /**
   * Decodes a value from an XML string. The limits of `config` apply; its
   * `preserveWhitespace` is ignored (text is always kept as written). Returns a
   * `Left` for malformed XML and for a tree that does not match the type.
   */
  def decode(input: String, config: ReaderConfig): Either[SchemaError, A] =
    try {
      // Force preserveWhitespace=true for codec decoding to ensure text content is preserved correctly
      new Right(decodeValue(XmlReader.read(input, config.copy(preserveWhitespace = true))))
    } catch {
      case err if NonFatal(err) => new Left(toError(err))
    }

  /**
   * Encodes a value as an XML string formatted by `config`.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Order(id: String, qty: Int)
   * object Order {
   *   implicit val schema: Schema[Order] = Schema.derived
   * }
   *
   * Schema[Order].derive(XmlFormat).encodeToString(Order("A-17", 2), WriterConfig.pretty)
   * // <Order>
   * //   <id>A-17</id>
   * //   <qty>2</qty>
   * // </Order>
   * }}}
   */
  def encodeToString(value: A, config: WriterConfig): String = XmlWriter.write(encodeValue(value), config)

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      new ExpectationMismatch(
        error match {
          case e: XmlCodecError =>
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
 * The codecs for primitive types that [[XmlCodecDeriver]] uses. Each one
 * encodes its value as text inside a `<value>` element (`unitCodec` writes
 * `<unit/>`), and decodes the first child of the element, trimmed for the types
 * other than `String` and `Char`.
 */
object XmlCodec {

  /** The codec for `Unit`, written as `<unit/>`. */
  val unitCodec: XmlCodec[Unit] = new XmlCodec[Unit] {
    def decodeValue(xml: Xml): Unit = xml match {
      case e: Xml.Element if e.name.localName == "unit" => ()
      case _                                            => error("Expected <unit> element")
    }

    def encodeValue(x: Unit): Xml = Xml.Element("unit")
  }

  /** The codec for `Boolean`. */
  val booleanCodec: XmlCodec[Boolean] = new XmlCodec[Boolean] {
    def decodeValue(xml: Xml): Boolean = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            t.value.trim match {
              case "true"  => true
              case "false" => false
              case _       => error(s"Invalid boolean value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Boolean): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `Byte`. */
  val byteCodec: XmlCodec[Byte] = new XmlCodec[Byte] {
    def decodeValue(xml: Xml): Byte = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try t.value.trim.toByte
            catch {
              case _: NumberFormatException => error(s"Invalid byte value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Byte): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `Short`. */
  val shortCodec: XmlCodec[Short] = new XmlCodec[Short] {
    def decodeValue(xml: Xml): Short = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try t.value.trim.toShort
            catch {
              case _: NumberFormatException => error(s"Invalid short value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Short): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `Int`. */
  val intCodec: XmlCodec[Int] = new XmlCodec[Int] {
    def decodeValue(xml: Xml): Int = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try t.value.trim.toInt
            catch {
              case _: NumberFormatException => error(s"Invalid int value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Int): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `Long`. */
  val longCodec: XmlCodec[Long] = new XmlCodec[Long] {
    def decodeValue(xml: Xml): Long = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try t.value.trim.toLong
            catch {
              case _: NumberFormatException => error(s"Invalid long value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Long): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `Float`. */
  val floatCodec: XmlCodec[Float] = new XmlCodec[Float] {
    def decodeValue(xml: Xml): Float = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try t.value.trim.toFloat
            catch {
              case _: NumberFormatException => error(s"Invalid float value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Float): Xml = Xml.Element("value", new Xml.Text(JsonCodec.floatCodec.encodeToString(x)))
  }

  /** The codec for `Double`. */
  val doubleCodec: XmlCodec[Double] = new XmlCodec[Double] {
    def decodeValue(xml: Xml): Double = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try t.value.trim.toDouble
            catch {
              case _: NumberFormatException => error(s"Invalid double value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Double): Xml = Xml.Element("value", new Xml.Text(JsonCodec.doubleCodec.encodeToString(x)))
  }

  /** The codec for `Char`; decoding requires exactly one character. */
  val charCodec: XmlCodec[Char] = new XmlCodec[Char] {
    def decodeValue(xml: Xml): Char = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            val text = t.value
            if (text.length == 1) text.charAt(0)
            else error(s"Expected single character, got: $text")
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Char): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `String`; the empty string is written as `<value/>`. */
  val stringCodec: XmlCodec[String] = new XmlCodec[String] {
    def decodeValue(xml: Xml): String = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => t.value
          case None              => ""
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: String): Xml =
      if (x.isEmpty) Xml.Element("value")
      else Xml.Element("value", new Xml.Text(x))
  }

  /** The codec for `BigInt`. */
  val bigIntCodec: XmlCodec[BigInt] = new XmlCodec[BigInt] {
    def decodeValue(xml: Xml): BigInt = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try BigInt(t.value.trim)
            catch {
              case _: NumberFormatException => error(s"Invalid BigInt value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: BigInt): Xml = Xml.Element("value", new Xml.Text(JsonCodec.bigIntCodec.encodeToString(x)))
  }

  /** The codec for `BigDecimal`. */
  val bigDecimalCodec: XmlCodec[BigDecimal] = new XmlCodec[BigDecimal] {
    def decodeValue(xml: Xml): BigDecimal = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try BigDecimal(t.value.trim)
            catch {
              case _: NumberFormatException => error(s"Invalid BigDecimal value: ${t.value}")
            }
          case _ => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: BigDecimal): Xml =
      Xml.Element("value", new Xml.Text(JsonCodec.bigDecimalCodec.encodeToString(x)))
  }

  /** The codec for `java.time.DayOfWeek`; decoding ignores case. */
  val dayOfWeekCodec: XmlCodec[DayOfWeek] = new XmlCodec[DayOfWeek]() {
    def decodeValue(xml: Xml): DayOfWeek = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => DayOfWeek.valueOf(t.value.trim.toUpperCase)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: DayOfWeek): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `java.time.Duration`, in ISO-8601 form such as `PT1H30M`. */
  val durationCodec: XmlCodec[Duration] = new XmlCodec[Duration]() {
    def decodeValue(xml: Xml): Duration = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.durationRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Duration): Xml = Xml.Element("value", new Xml.Text(Json.durationRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.Instant`. */
  val instantCodec: XmlCodec[Instant] = new XmlCodec[Instant]() {
    def decodeValue(xml: Xml): Instant = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.instantRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Instant): Xml = Xml.Element("value", new Xml.Text(Json.instantRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.LocalDate`. */
  val localDateCodec: XmlCodec[LocalDate] = new XmlCodec[LocalDate]() {
    def decodeValue(xml: Xml): LocalDate = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.localDateRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: LocalDate): Xml = Xml.Element("value", new Xml.Text(Json.localDateRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.LocalDateTime`. */
  val localDateTimeCodec: XmlCodec[LocalDateTime] = new XmlCodec[LocalDateTime]() {
    def decodeValue(xml: Xml): LocalDateTime = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.localDateTimeRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: LocalDateTime): Xml =
      Xml.Element("value", new Xml.Text(Json.localDateTimeRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.LocalTime`. */
  val localTimeCodec: XmlCodec[LocalTime] = new XmlCodec[LocalTime]() {
    def decodeValue(xml: Xml): LocalTime = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.localTimeRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: LocalTime): Xml = Xml.Element("value", new Xml.Text(Json.localTimeRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.Month`; decoding ignores case. */
  val monthCodec: XmlCodec[Month] = new XmlCodec[Month]() {
    def decodeValue(xml: Xml): Month = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Month.valueOf(t.value.trim.toUpperCase)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Month): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `java.time.MonthDay`. */
  val monthDayCodec: XmlCodec[MonthDay] = new XmlCodec[MonthDay]() {
    def decodeValue(xml: Xml): MonthDay = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.monthDayRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: MonthDay): Xml = Xml.Element("value", new Xml.Text(Json.monthDayRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.OffsetDateTime`. */
  val offsetDateTimeCodec: XmlCodec[OffsetDateTime] = new XmlCodec[OffsetDateTime]() {
    def decodeValue(xml: Xml): OffsetDateTime = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.offsetDateTimeRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: OffsetDateTime): Xml =
      Xml.Element("value", new Xml.Text(Json.offsetDateTimeRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.OffsetTime`. */
  val offsetTimeCodec: XmlCodec[OffsetTime] = new XmlCodec[OffsetTime]() {
    def decodeValue(xml: Xml): OffsetTime = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.offsetTimeRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: OffsetTime): Xml = Xml.Element("value", new Xml.Text(Json.offsetTimeRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.Period`. */
  val periodCodec: XmlCodec[Period] = new XmlCodec[Period]() {
    def decodeValue(xml: Xml): Period = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.periodRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Period): Xml = Xml.Element("value", new Xml.Text(Json.periodRawCodec.encodeToString(x)))
  }

  /** The codec for `java.time.Year`. */
  val yearCodec: XmlCodec[Year] = new XmlCodec[Year]() {
    def decodeValue(xml: Xml): Year = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Year.parse(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Year): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `java.time.YearMonth`. */
  val yearMonthCodec: XmlCodec[YearMonth] = new XmlCodec[YearMonth]() {
    def decodeValue(xml: Xml): YearMonth = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => YearMonth.parse(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: YearMonth): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  /** The codec for `java.time.ZoneId`. */
  val zoneIdCodec: XmlCodec[ZoneId] = new XmlCodec[ZoneId]() {
    def decodeValue(xml: Xml): ZoneId = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => ZoneId.of(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: ZoneId): Xml = Xml.Element("value", new Xml.Text(x.getId))
  }

  /** The codec for `java.time.ZoneOffset`. */
  val zoneOffsetCodec: XmlCodec[ZoneOffset] = new XmlCodec[ZoneOffset]() {
    def decodeValue(xml: Xml): ZoneOffset = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => ZoneOffset.of(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: ZoneOffset): Xml = Xml.Element("value", new Xml.Text(x.getId))
  }

  /** The codec for `java.time.ZonedDateTime`. */
  val zonedDateTimeCodec: XmlCodec[ZonedDateTime] = new XmlCodec[ZonedDateTime]() {
    def decodeValue(xml: Xml): ZonedDateTime = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Json.zonedDateTimeRawCodec.decodeUnsafe(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: ZonedDateTime): Xml =
      Xml.Element("value", new Xml.Text(Json.zonedDateTimeRawCodec.encodeToString(x)))
  }

  /** The codec for `java.util.Currency`, as its ISO 4217 code. */
  val currencyCodec: XmlCodec[Currency] = new XmlCodec[Currency]() {
    def decodeValue(xml: Xml): Currency = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => Currency.getInstance(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: Currency): Xml = Xml.Element("value", new Xml.Text(x.getCurrencyCode))
  }

  /** The codec for `java.util.UUID`. */
  val uuidCodec: XmlCodec[UUID] = new XmlCodec[UUID]() {
    def decodeValue(xml: Xml): UUID = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => UUID.fromString(t.value.trim)
          case _                 => error("Expected text content in <value> element")
        }
      case _ => error("Expected <value> element")
    }

    def encodeValue(x: UUID): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }
}
