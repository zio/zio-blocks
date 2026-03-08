package zio.blocks.schema.yaml

import zio.blocks.schema.SchemaError
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.switch
import scala.util.control.NonFatal

abstract class YamlBinaryCodec[A](val valueType: Int = YamlBinaryCodec.objectType) extends BinaryCodec[A] {

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

  def decodeValue(in: Yaml): Either[YamlError, A]

  def encodeValue(x: A): Yaml

  def nullValue: A = null.asInstanceOf[A]

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val bytes = new Array[Byte](input.remaining())
    input.get(bytes)
    decode(bytes)
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val bytes = encode(value)
    output.put(bytes)
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try {
      YamlReader.readFromBytes(input) match {
        case Left(yamlError) => Left(toSchemaError(yamlError))
        case Right(yaml)     =>
          decodeValue(yaml) match {
            case Left(yamlError) => Left(toSchemaError(yamlError))
            case Right(value)    => Right(value)
          }
      }
    } catch {
      case error if NonFatal(error) => Left(toSchemaError(error))
    }

  def encode(value: A): Array[Byte] = {
    val yaml = encodeValue(value)
    YamlWriter.writeToBytes(yaml)
  }

  def decode(input: String): Either[SchemaError, A] =
    try {
      val buf = input.getBytes(UTF_8)
      decode(buf)
    } catch {
      case error if NonFatal(error) => Left(toSchemaError(error))
    }

  def encodeToString(value: A): String = {
    val yaml = encodeValue(value)
    YamlWriter.write(yaml)
  }

  private def toSchemaError(error: Throwable): SchemaError = error match {
    case yamlError: YamlError => SchemaError(yamlError.getMessage)
    case other                => SchemaError(Option(other.getMessage).getOrElse(s"${other.getClass.getName}: (no message)"))
  }
}

object YamlBinaryCodec {
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

  val unitCodec: YamlBinaryCodec[Unit] = new YamlBinaryCodec[Unit](YamlBinaryCodec.unitType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Unit] = yaml match {
      case Yaml.NullValue                                            => Right(())
      case Yaml.Scalar(v, _) if v == "null" || v == "~" || v.isEmpty => Right(())
      case _                                                         => Left(YamlError.parseError("Expected null value for Unit", 0, 0))
    }
    def encodeValue(x: Unit): Yaml = Yaml.NullValue
  }

  val booleanCodec: YamlBinaryCodec[Boolean] = new YamlBinaryCodec[Boolean](YamlBinaryCodec.booleanType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Boolean] = yaml match {
      case Yaml.Scalar(v, _) =>
        v.trim match {
          case "true"  => Right(true)
          case "false" => Right(false)
          case _       => Left(YamlError.parseError(s"Invalid boolean value: $v", 0, 0))
        }
      case _ => Left(YamlError.parseError("Expected scalar for boolean", 0, 0))
    }
    def encodeValue(x: Boolean): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Bool))
  }

  val byteCodec: YamlBinaryCodec[Byte] = new YamlBinaryCodec[Byte](YamlBinaryCodec.byteType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Byte] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toByte)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid byte value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for byte", 0, 0))
    }
    def encodeValue(x: Byte): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  val shortCodec: YamlBinaryCodec[Short] = new YamlBinaryCodec[Short](YamlBinaryCodec.shortType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Short] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toShort)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid short value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for short", 0, 0))
    }
    def encodeValue(x: Short): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  val intCodec: YamlBinaryCodec[Int] = new YamlBinaryCodec[Int](YamlBinaryCodec.intType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Int] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toInt)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid int value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for int", 0, 0))
    }
    def encodeValue(x: Int): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  val longCodec: YamlBinaryCodec[Long] = new YamlBinaryCodec[Long](YamlBinaryCodec.longType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Long] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toLong)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid long value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for long", 0, 0))
    }
    def encodeValue(x: Long): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  val floatCodec: YamlBinaryCodec[Float] = new YamlBinaryCodec[Float](YamlBinaryCodec.floatType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Float] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toFloat)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid float value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for float", 0, 0))
    }
    def encodeValue(x: Float): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Float))
  }

  val doubleCodec: YamlBinaryCodec[Double] = new YamlBinaryCodec[Double](YamlBinaryCodec.doubleType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Double] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toDouble)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid double value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for double", 0, 0))
    }
    def encodeValue(x: Double): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Float))
  }

  val charCodec: YamlBinaryCodec[Char] = new YamlBinaryCodec[Char](YamlBinaryCodec.charType) {
    def decodeValue(yaml: Yaml): Either[YamlError, Char] = yaml match {
      case Yaml.Scalar(v, _) if v.length == 1 => Right(v.charAt(0))
      case Yaml.Scalar(v, _)                  => Left(YamlError.parseError(s"Expected single character, got: $v", 0, 0))
      case _                                  => Left(YamlError.parseError("Expected scalar for char", 0, 0))
    }
    def encodeValue(x: Char): Yaml = Yaml.Scalar(x.toString)
  }

  val stringCodec: YamlBinaryCodec[String] = new YamlBinaryCodec[String]() {
    def decodeValue(yaml: Yaml): Either[YamlError, String] = yaml match {
      case Yaml.Scalar(v, _) => Right(v)
      case Yaml.NullValue    => Right("")
      case _                 => Left(YamlError.parseError("Expected scalar for string", 0, 0))
    }
    def encodeValue(x: String): Yaml = Yaml.Scalar(x)
  }

  val bigIntCodec: YamlBinaryCodec[BigInt] = new YamlBinaryCodec[BigInt]() {
    def decodeValue(yaml: Yaml): Either[YamlError, BigInt] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(BigInt(v.trim))
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid BigInt value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for BigInt", 0, 0))
    }
    def encodeValue(x: BigInt): Yaml = Yaml.Scalar(x.toString)
  }

  val bigDecimalCodec: YamlBinaryCodec[BigDecimal] = new YamlBinaryCodec[BigDecimal]() {
    def decodeValue(yaml: Yaml): Either[YamlError, BigDecimal] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(BigDecimal(v.trim))
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid BigDecimal value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for BigDecimal", 0, 0))
    }
    def encodeValue(x: BigDecimal): Yaml = Yaml.Scalar(x.toString)
  }
}
