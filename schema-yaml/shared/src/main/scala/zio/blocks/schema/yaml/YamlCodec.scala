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

import zio.blocks.schema.SchemaError
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal

/**
 * Abstract base class for YAML binary codecs that serialize/deserialize values
 * of type `A` to and from YAML representations.
 *
 * @tparam A
 *   the type this codec handles
 */
abstract class YamlCodec[A] extends BinaryCodec[A] {
  def decodeValue(in: Yaml): Either[YamlError, A]

  def encodeValue(x: A): Yaml

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

/**
 * Companion object providing primitive [[YamlCodec]] instances and type
 * constants.
 */
object YamlCodec {
  val unitCodec: YamlCodec[Unit] = new YamlCodec[Unit] {
    def decodeValue(yaml: Yaml): Either[YamlError, Unit] = yaml match {
      case Yaml.NullValue                                            => Right(())
      case Yaml.Scalar(v, _) if v == "null" || v == "~" || v.isEmpty => Right(())
      case _                                                         => Left(YamlError.parseError("Expected null value for Unit", 0, 0))
    }
    def encodeValue(x: Unit): Yaml = Yaml.NullValue
  }

  val booleanCodec: YamlCodec[Boolean] = new YamlCodec[Boolean] {
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

  val byteCodec: YamlCodec[Byte] = new YamlCodec[Byte] {
    def decodeValue(yaml: Yaml): Either[YamlError, Byte] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toByte)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid byte value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for byte", 0, 0))
    }
    def encodeValue(x: Byte): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  val shortCodec: YamlCodec[Short] = new YamlCodec[Short] {
    def decodeValue(yaml: Yaml): Either[YamlError, Short] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toShort)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid short value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for short", 0, 0))
    }
    def encodeValue(x: Short): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  val intCodec: YamlCodec[Int] = new YamlCodec[Int] {
    def decodeValue(yaml: Yaml): Either[YamlError, Int] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toInt)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid int value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for int", 0, 0))
    }
    def encodeValue(x: Int): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  val longCodec: YamlCodec[Long] = new YamlCodec[Long] {
    def decodeValue(yaml: Yaml): Either[YamlError, Long] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toLong)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid long value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for long", 0, 0))
    }
    def encodeValue(x: Long): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  val floatCodec: YamlCodec[Float] = new YamlCodec[Float] {
    def decodeValue(yaml: Yaml): Either[YamlError, Float] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toFloat)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid float value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for float", 0, 0))
    }
    def encodeValue(x: Float): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Float))
  }

  val doubleCodec: YamlCodec[Double] = new YamlCodec[Double] {
    def decodeValue(yaml: Yaml): Either[YamlError, Double] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(v.trim.toDouble)
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid double value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for double", 0, 0))
    }
    def encodeValue(x: Double): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Float))
  }

  val charCodec: YamlCodec[Char] = new YamlCodec[Char] {
    def decodeValue(yaml: Yaml): Either[YamlError, Char] = yaml match {
      case Yaml.Scalar(v, _) if v.length == 1 => Right(v.charAt(0))
      case Yaml.Scalar(v, _)                  => Left(YamlError.parseError(s"Expected single character, got: $v", 0, 0))
      case _                                  => Left(YamlError.parseError("Expected scalar for char", 0, 0))
    }
    def encodeValue(x: Char): Yaml = Yaml.Scalar(x.toString)
  }

  val stringCodec: YamlCodec[String] = new YamlCodec[String] {
    def decodeValue(yaml: Yaml): Either[YamlError, String] = yaml match {
      case Yaml.Scalar(v, _) => Right(v)
      case Yaml.NullValue    => Right("")
      case _                 => Left(YamlError.parseError("Expected scalar for string", 0, 0))
    }
    def encodeValue(x: String): Yaml = Yaml.Scalar(x)
  }

  val bigIntCodec: YamlCodec[BigInt] = new YamlCodec[BigInt] {
    def decodeValue(yaml: Yaml): Either[YamlError, BigInt] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(BigInt(v.trim))
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid BigInt value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for BigInt", 0, 0))
    }
    def encodeValue(x: BigInt): Yaml = Yaml.Scalar(x.toString)
  }

  val bigDecimalCodec: YamlCodec[BigDecimal] = new YamlCodec[BigDecimal] {
    def decodeValue(yaml: Yaml): Either[YamlError, BigDecimal] = yaml match {
      case Yaml.Scalar(v, _) =>
        try Right(BigDecimal(v.trim))
        catch { case _: NumberFormatException => Left(YamlError.parseError(s"Invalid BigDecimal value: $v", 0, 0)) }
      case _ => Left(YamlError.parseError("Expected scalar for BigDecimal", 0, 0))
    }
    def encodeValue(x: BigDecimal): Yaml = Yaml.Scalar(x.toString)
  }
}
