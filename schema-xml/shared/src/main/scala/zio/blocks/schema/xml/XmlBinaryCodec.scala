package zio.blocks.schema.xml

import zio.blocks.schema.SchemaError
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.switch
import scala.util.control.NonFatal

abstract class XmlBinaryCodec[A](val valueType: Int = XmlBinaryCodec.objectType) extends BinaryCodec[A] {

  val valueOffset: RegisterOffset.RegisterOffset = (valueType: @switch) match {
    case 0 => RegisterOffset(objects = 1)
    case 1 => RegisterOffset(ints = 1)
    case 2 => RegisterOffset(longs = 1)
    case 3 => RegisterOffset(floats = 1)
    case 4 => RegisterOffset(doubles = 1)
    case 5 => RegisterOffset(booleans = 1)
    case 6 => RegisterOffset(bytes = 1)
    case 7 => RegisterOffset(chars = 1)
    case 8 => RegisterOffset(shorts = 1)
    case _ => RegisterOffset.Zero
  }

  def decodeValue(in: Xml): Either[XmlError, A]

  def encodeValue(x: A): Xml

  def nullValue: A = null.asInstanceOf[A]

  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  override def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig.default)

  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] = {
    val bytes = new Array[Byte](input.remaining())
    input.get(bytes)
    decode(bytes, config)
  }

  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = {
    val bytes = encode(value, config)
    output.put(bytes)
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  def encode(value: A): Array[Byte] = encode(value, WriterConfig.default)

  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try {
      // Force preserveWhitespace=true for codec decoding to ensure text content is preserved correctly
      val readerConfig = config.copy(preserveWhitespace = true)
      XmlReader.readFromBytes(input, readerConfig) match {
        case Left(xmlError) => Left(toSchemaError(xmlError))
        case Right(xml)     =>
          decodeValue(xml) match {
            case Left(xmlError) => Left(toSchemaError(xmlError))
            case Right(value)   => Right(value)
          }
      }
    } catch {
      case error if NonFatal(error) => Left(toSchemaError(error))
    }

  def encode(value: A, config: WriterConfig): Array[Byte] = {
    val xml = encodeValue(value)
    XmlWriter.writeToBytes(xml, config)
  }

  def decode(input: String): Either[SchemaError, A] = decode(input, ReaderConfig.default)

  def encodeToString(value: A): String = encodeToString(value, WriterConfig.default)

  def decode(input: String, config: ReaderConfig): Either[SchemaError, A] =
    try {
      val buf = input.getBytes(UTF_8)
      decode(buf, config)
    } catch {
      case error if NonFatal(error) => Left(toSchemaError(error))
    }

  def encodeToString(value: A, config: WriterConfig): String = {
    val xml = encodeValue(value)
    XmlWriter.write(xml, config)
  }

  private def toSchemaError(error: Throwable): SchemaError = error match {
    case xmlError: XmlError => SchemaError(xmlError.getMessage)
    case other              => SchemaError(Option(other.getMessage).getOrElse(s"${other.getClass.getName}: (no message)"))
  }
}

object XmlBinaryCodec {
  val objectType  = 0
  val intType     = 1
  val longType    = 2
  val floatType   = 3
  val doubleType  = 4
  val booleanType = 5
  val byteType    = 6
  val charType    = 7
  val shortType   = 8
  val unitType    = 9

  val unitCodec: XmlBinaryCodec[Unit] = new XmlBinaryCodec[Unit](XmlBinaryCodec.unitType) {
    def decodeValue(xml: Xml): Either[XmlError, Unit] = xml match {
      case Xml.Element(name, _, _) if name.localName == "unit" => Right(())
      case _                                                   => Left(XmlError.parseError("Expected <unit> element", 0, 0))
    }

    def encodeValue(x: Unit): Xml = Xml.Element("unit")
  }

  val booleanCodec: XmlBinaryCodec[Boolean] = new XmlBinaryCodec[Boolean](XmlBinaryCodec.booleanType) {
    def decodeValue(xml: Xml): Either[XmlError, Boolean] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            text.trim match {
              case "true"  => Right(true)
              case "false" => Right(false)
              case _       => Left(XmlError.parseError(s"Invalid boolean value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Boolean): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val byteCodec: XmlBinaryCodec[Byte] = new XmlBinaryCodec[Byte](XmlBinaryCodec.byteType) {
    def decodeValue(xml: Xml): Either[XmlError, Byte] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            try Right(text.trim.toByte)
            catch {
              case _: NumberFormatException => Left(XmlError.parseError(s"Invalid byte value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Byte): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val shortCodec: XmlBinaryCodec[Short] = new XmlBinaryCodec[Short](XmlBinaryCodec.shortType) {
    def decodeValue(xml: Xml): Either[XmlError, Short] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            try Right(text.trim.toShort)
            catch {
              case _: NumberFormatException => Left(XmlError.parseError(s"Invalid short value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Short): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val intCodec: XmlBinaryCodec[Int] = new XmlBinaryCodec[Int](XmlBinaryCodec.intType) {
    def decodeValue(xml: Xml): Either[XmlError, Int] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            try Right(text.trim.toInt)
            catch {
              case _: NumberFormatException => Left(XmlError.parseError(s"Invalid int value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Int): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val longCodec: XmlBinaryCodec[Long] = new XmlBinaryCodec[Long](XmlBinaryCodec.longType) {
    def decodeValue(xml: Xml): Either[XmlError, Long] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            try Right(text.trim.toLong)
            catch {
              case _: NumberFormatException => Left(XmlError.parseError(s"Invalid long value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Long): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val floatCodec: XmlBinaryCodec[Float] = new XmlBinaryCodec[Float](XmlBinaryCodec.floatType) {
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

    def encodeValue(x: Float): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val doubleCodec: XmlBinaryCodec[Double] = new XmlBinaryCodec[Double](XmlBinaryCodec.doubleType) {
    def decodeValue(xml: Xml): Either[XmlError, Double] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            try Right(text.trim.toDouble)
            catch {
              case _: NumberFormatException => Left(XmlError.parseError(s"Invalid double value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Double): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val charCodec: XmlBinaryCodec[Char] = new XmlBinaryCodec[Char](XmlBinaryCodec.charType) {
    def decodeValue(xml: Xml): Either[XmlError, Char] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) if text.length == 1 => Right(text.charAt(0))
          case Some(Xml.Text(text))                     => Left(XmlError.parseError(s"Expected single character, got: $text", 0, 0))
          case _                                        => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: Char): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val stringCodec: XmlBinaryCodec[String] = new XmlBinaryCodec[String]() {
    def decodeValue(xml: Xml): Either[XmlError, String] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) => Right(text)
          case None                 => Right("")
          case _                    => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: String): Xml =
      if (x.isEmpty) Xml.Element("value")
      else Xml.Element("value", Xml.Text(x))
  }

  val bigIntCodec: XmlBinaryCodec[BigInt] = new XmlBinaryCodec[BigInt]() {
    def decodeValue(xml: Xml): Either[XmlError, BigInt] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            try Right(BigInt(text.trim))
            catch {
              case _: NumberFormatException => Left(XmlError.parseError(s"Invalid BigInt value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: BigInt): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }

  val bigDecimalCodec: XmlBinaryCodec[BigDecimal] = new XmlBinaryCodec[BigDecimal]() {
    def decodeValue(xml: Xml): Either[XmlError, BigDecimal] = xml match {
      case Xml.Element(name, _, children) if name.localName == "value" =>
        children.headOption match {
          case Some(Xml.Text(text)) =>
            try Right(BigDecimal(text.trim))
            catch {
              case _: NumberFormatException => Left(XmlError.parseError(s"Invalid BigDecimal value: $text", 0, 0))
            }
          case _ => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        }
      case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
    }

    def encodeValue(x: BigDecimal): Xml =
      Xml.Element("value", Xml.Text(x.toString))
  }
}
