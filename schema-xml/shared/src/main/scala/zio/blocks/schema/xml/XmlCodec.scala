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

import zio.blocks.schema.SchemaError
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.json.JsonCodec
import java.nio.ByteBuffer
import scala.util.control.NonFatal

abstract class XmlCodec[A] extends BinaryCodec[A] {
  def decodeValue(in: Xml): Either[XmlError, A]

  def encodeValue(x: A): Xml

  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  override def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig.default)

  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] = {
    val bytes = new Array[Byte](input.remaining())
    input.get(bytes)
    decode(bytes, config)
  }

  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = output.put(encode(value, config))

  def decode(input: Array[Byte]): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  def encode(value: A): Array[Byte] = encode(value, WriterConfig.default)

  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try {
      // Force preserveWhitespace=true for codec decoding to ensure text content is preserved correctly
      val readerConfig = config.copy(preserveWhitespace = true)
      XmlReader.readFromBytes(input, readerConfig) match {
        case Right(xml) =>
          decodeValue(xml) match {
            case Left(xmlError) => new Left(toSchemaError(xmlError))
            case r              => r.asInstanceOf[Either[SchemaError, A]]
          }
        case Left(xmlError) => new Left(toSchemaError(xmlError))
      }
    } catch {
      case error if NonFatal(error) => new Left(toSchemaError(error))
    }

  def encode(value: A, config: WriterConfig): Array[Byte] = XmlWriter.writeToBytes(encodeValue(value), config)

  def decode(input: String): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  def encodeToString(value: A): String = encodeToString(value, WriterConfig.default)

  def decode(input: String, config: ReaderConfig): Either[SchemaError, A] =
    try {
      // Force preserveWhitespace=true for codec decoding to ensure text content is preserved correctly
      val readerConfig = config.copy(preserveWhitespace = true)
      XmlReader.read(input, readerConfig) match {
        case Right(xml) =>
          decodeValue(xml) match {
            case Left(xmlError) => new Left(toSchemaError(xmlError))
            case r              => r.asInstanceOf[Either[SchemaError, A]]
          }
        case Left(xmlError) => new Left(toSchemaError(xmlError))
      }
    } catch {
      case error if NonFatal(error) => new Left(toSchemaError(error))
    }

  def encodeToString(value: A, config: WriterConfig): String = XmlWriter.write(encodeValue(value), config)

  private[this] def toSchemaError(error: Throwable): SchemaError = SchemaError(error match {
    case xmlError: XmlError => xmlError.getMessage
    case other              =>
      val msg = other.getMessage
      if (msg ne null) msg
      else s"${other.getClass.getName}: (no message)"
  })
}

object XmlCodec {
  val unitCodec: XmlCodec[Unit] = new XmlCodec[Unit] {
    def decodeValue(xml: Xml): Either[XmlError, Unit] = xml match {
      case e: Xml.Element if e.name.localName == "unit" => new Right(())
      case _                                            => new Left(XmlError.parseError("Expected <unit> element", 0, 0))
    }

    def encodeValue(x: Unit): Xml = Xml.Element("unit")
  }

  val booleanCodec: XmlCodec[Boolean] = new XmlCodec[Boolean] {
    def decodeValue(xml: Xml): Either[XmlError, Boolean] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            t.value.trim match {
              case "true"  => new Right(true)
              case "false" => new Right(false)
              case _       => new Left(XmlError.parseError(s"Invalid boolean value: ${t.value}", 0, 0))
            }
          case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Boolean): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  val byteCodec: XmlCodec[Byte] = new XmlCodec[Byte] {
    def decodeValue(xml: Xml): Either[XmlError, Byte] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try new Right(t.value.trim.toByte)
            catch {
              case _: NumberFormatException => new Left(XmlError.parseError(s"Invalid byte value: ${t.value}", 0, 0))
            }
          case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Byte): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  val shortCodec: XmlCodec[Short] = new XmlCodec[Short] {
    def decodeValue(xml: Xml): Either[XmlError, Short] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try new Right(t.value.trim.toShort)
            catch {
              case _: NumberFormatException => new Left(XmlError.parseError(s"Invalid short value: ${t.value}", 0, 0))
            }
          case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Short): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  val intCodec: XmlCodec[Int] = new XmlCodec[Int] {
    def decodeValue(xml: Xml): Either[XmlError, Int] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try new Right(t.value.trim.toInt)
            catch {
              case _: NumberFormatException => new Left(XmlError.parseError(s"Invalid int value: ${t.value}", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Int): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  val longCodec: XmlCodec[Long] = new XmlCodec[Long] {
    def decodeValue(xml: Xml): Either[XmlError, Long] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try new Right(t.value.trim.toLong)
            catch {
              case _: NumberFormatException => new Left(XmlError.parseError(s"Invalid long value: ${t.value}", 0, 0))
            }
          case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Long): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  val floatCodec: XmlCodec[Float] = new XmlCodec[Float] {
    def decodeValue(xml: Xml): Either[XmlError, Float] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            try Right(text.trim.toFloat)
            catch {
              case _: NumberFormatException => Left(XmlError.parseError(s"Invalid float value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Float): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  val doubleCodec: XmlCodec[Double] = new XmlCodec[Double] {
    def decodeValue(xml: Xml): Either[XmlError, Double] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try new Right(t.value.trim.toDouble)
            catch {
              case _: NumberFormatException => new Left(XmlError.parseError(s"Invalid double value: ${t.value}", 0, 0))
            }
          case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Double): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  val charCodec: XmlCodec[Char] = new XmlCodec[Char] {
    def decodeValue(xml: Xml): Either[XmlError, Char] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            val text = t.value
            if (text.length == 1) new Right(text.charAt(0))
            else new Left(XmlError.parseError(s"Expected single character, got: $text", 0, 0))
          case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Char): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  val stringCodec: XmlCodec[String] = new XmlCodec[String] {
    def decodeValue(xml: Xml): Either[XmlError, String] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) => new Right(t.value)
          case None              => new Right("")
          case _                 => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: String): Xml =
      if (x.isEmpty) Xml.Element("value")
      else Xml.Element("value", new Xml.Text(x))
  }

  val bigIntCodec: XmlCodec[BigInt] = new XmlCodec[BigInt] {
    def decodeValue(xml: Xml): Either[XmlError, BigInt] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try new Right(BigInt(t.value.trim))
            catch {
              case _: NumberFormatException => new Left(XmlError.parseError(s"Invalid BigInt value: ${t.value}", 0, 0))
            }
          case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: BigInt): Xml = Xml.Element("value", new Xml.Text(JsonCodec.bigIntCodec.encodeToString(x)))
  }

  val bigDecimalCodec: XmlCodec[BigDecimal] = new XmlCodec[BigDecimal] {
    def decodeValue(xml: Xml): Either[XmlError, BigDecimal] = xml match {
      case e: Xml.Element if e.name.localName == "value" =>
        e.children.headOption match {
          case Some(t: Xml.Text) =>
            try new Right(BigDecimal(t.value.trim))
            catch {
              case _: NumberFormatException =>
                new Left(XmlError.parseError(s"Invalid BigDecimal value: ${t.value}", 0, 0))
            }
          case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: BigDecimal): Xml =
      Xml.Element("value", new Xml.Text(JsonCodec.bigDecimalCodec.encodeToString(x)))
  }
}
