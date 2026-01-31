/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue}

/**
 * Primitive type conversions for schema migration.
 *
 * Supports safe conversions between primitive types.
 */
object PrimitiveConversions {

  /**
   * Convert a DynamicValue from one primitive type to another.
   */
  def convert(value: DynamicValue, @annotation.unused fromType: String, toType: String): Either[String, DynamicValue] =
    value match {
      case DynamicValue.Primitive(pv) =>
        convertPrimitive(pv, toType).map(DynamicValue.Primitive(_))
      case DynamicValue.Null =>
        Right(DynamicValue.Null)
      case _ =>
        Left(s"Expected primitive value, got ${value.getClass.getSimpleName}")
    }

  /**
   * Convert a primitive value to a target type.
   */
  def convertPrimitive(pv: PrimitiveValue, toType: String): Either[String, PrimitiveValue] =
    toType.toLowerCase match {
      case "string"     => Right(PrimitiveValue.String(primitiveToString(pv)))
      case "int"        => primitiveToInt(pv).map(PrimitiveValue.Int(_))
      case "long"       => primitiveToLong(pv).map(PrimitiveValue.Long(_))
      case "double"     => primitiveToDouble(pv).map(PrimitiveValue.Double(_))
      case "float"      => primitiveToFloat(pv).map(PrimitiveValue.Float(_))
      case "short"      => primitiveToShort(pv).map(PrimitiveValue.Short(_))
      case "byte"       => primitiveToByte(pv).map(PrimitiveValue.Byte(_))
      case "boolean"    => primitiveToBoolean(pv).map(PrimitiveValue.Boolean(_))
      case "char"       => primitiveToChar(pv).map(PrimitiveValue.Char(_))
      case "bigint"     => primitiveToBigInt(pv).map(PrimitiveValue.BigInt(_))
      case "bigdecimal" => primitiveToBigDecimal(pv).map(PrimitiveValue.BigDecimal(_))
      case other        => Left(s"Unknown target type: $other")
    }

  private def primitiveToString(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.String(s)     => s
    case PrimitiveValue.Int(i)        => i.toString
    case PrimitiveValue.Long(l)       => l.toString
    case PrimitiveValue.Double(d)     => d.toString
    case PrimitiveValue.Float(f)      => f.toString
    case PrimitiveValue.Short(s)      => s.toString
    case PrimitiveValue.Byte(b)       => b.toString
    case PrimitiveValue.Boolean(b)    => b.toString
    case PrimitiveValue.Char(c)       => c.toString
    case PrimitiveValue.BigInt(b) => b.toString
    case PrimitiveValue.BigDecimal(b) => b.toString
    case other                        => other.toString
  }

  private def primitiveToInt(pv: PrimitiveValue): Either[String, Int] = pv match {
    case PrimitiveValue.Int(i)     => Right(i)
    case PrimitiveValue.Long(l)    => Right(l.toInt)
    case PrimitiveValue.Double(d)  => Right(d.toInt)
    case PrimitiveValue.Float(f)   => Right(f.toInt)
    case PrimitiveValue.Short(s)   => Right(s.toInt)
    case PrimitiveValue.Byte(b)    => Right(b.toInt)
    case PrimitiveValue.String(s)  => parseIntSafe(s)
    case PrimitiveValue.Boolean(b) => Right(if (b) 1 else 0)
    case PrimitiveValue.Char(c)    => Right(c.toInt)
    case other                     => Left(s"Cannot convert ${other.getClass.getSimpleName} to Int")
  }

  private def primitiveToLong(pv: PrimitiveValue): Either[String, Long] = pv match {
    case PrimitiveValue.Long(l)    => Right(l)
    case PrimitiveValue.Int(i)     => Right(i.toLong)
    case PrimitiveValue.Double(d)  => Right(d.toLong)
    case PrimitiveValue.Float(f)   => Right(f.toLong)
    case PrimitiveValue.Short(s)   => Right(s.toLong)
    case PrimitiveValue.Byte(b)    => Right(b.toLong)
    case PrimitiveValue.String(s)  => parseLongSafe(s)
    case PrimitiveValue.Boolean(b) => Right(if (b) 1L else 0L)
    case PrimitiveValue.Char(c)    => Right(c.toLong)
    case other                     => Left(s"Cannot convert ${other.getClass.getSimpleName} to Long")
  }

  private def primitiveToDouble(pv: PrimitiveValue): Either[String, Double] = pv match {
    case PrimitiveValue.Double(d)  => Right(d)
    case PrimitiveValue.Float(f)   => Right(f.toDouble)
    case PrimitiveValue.Long(l)    => Right(l.toDouble)
    case PrimitiveValue.Int(i)     => Right(i.toDouble)
    case PrimitiveValue.Short(s)   => Right(s.toDouble)
    case PrimitiveValue.Byte(b)    => Right(b.toDouble)
    case PrimitiveValue.String(s)  => parseDoubleSafe(s)
    case PrimitiveValue.Boolean(b) => Right(if (b) 1.0 else 0.0)
    case other                     => Left(s"Cannot convert ${other.getClass.getSimpleName} to Double")
  }

  private def primitiveToFloat(pv: PrimitiveValue): Either[String, Float] = pv match {
    case PrimitiveValue.Float(f)   => Right(f)
    case PrimitiveValue.Double(d)  => Right(d.toFloat)
    case PrimitiveValue.Long(l)    => Right(l.toFloat)
    case PrimitiveValue.Int(i)     => Right(i.toFloat)
    case PrimitiveValue.Short(s)   => Right(s.toFloat)
    case PrimitiveValue.Byte(b)    => Right(b.toFloat)
    case PrimitiveValue.String(s)  => parseFloatSafe(s)
    case PrimitiveValue.Boolean(b) => Right(if (b) 1.0f else 0.0f)
    case other                     => Left(s"Cannot convert ${other.getClass.getSimpleName} to Float")
  }

  private def primitiveToShort(pv: PrimitiveValue): Either[String, Short] = pv match {
    case PrimitiveValue.Short(s)   => Right(s)
    case PrimitiveValue.Int(i)     => Right(i.toShort)
    case PrimitiveValue.Long(l)    => Right(l.toShort)
    case PrimitiveValue.Byte(b)    => Right(b.toShort)
    case PrimitiveValue.String(s)  => parseShortSafe(s)
    case PrimitiveValue.Boolean(b) => Right(if (b) 1.toShort else 0.toShort)
    case other                     => Left(s"Cannot convert ${other.getClass.getSimpleName} to Short")
  }

  private def primitiveToByte(pv: PrimitiveValue): Either[String, Byte] = pv match {
    case PrimitiveValue.Byte(b)    => Right(b)
    case PrimitiveValue.Int(i)     => Right(i.toByte)
    case PrimitiveValue.Short(s)   => Right(s.toByte)
    case PrimitiveValue.Long(l)    => Right(l.toByte)
    case PrimitiveValue.String(s)  => parseByteSafe(s)
    case PrimitiveValue.Boolean(b) => Right(if (b) 1.toByte else 0.toByte)
    case other                     => Left(s"Cannot convert ${other.getClass.getSimpleName} to Byte")
  }

  private def primitiveToBoolean(pv: PrimitiveValue): Either[String, Boolean] = pv match {
    case PrimitiveValue.Boolean(b) => Right(b)
    case PrimitiveValue.Int(i)     => Right(i != 0)
    case PrimitiveValue.Long(l)    => Right(l != 0L)
    case PrimitiveValue.String(s) =>
      s.toLowerCase match {
        case "true" | "1" | "yes" | "on"   => Right(true)
        case "false" | "0" | "no" | "off"  => Right(false)
        case _                             => Left(s"Cannot parse '$s' as Boolean")
      }
    case other => Left(s"Cannot convert ${other.getClass.getSimpleName} to Boolean")
  }

  private def primitiveToChar(pv: PrimitiveValue): Either[String, Char] = pv match {
    case PrimitiveValue.Char(c)   => Right(c)
    case PrimitiveValue.Int(i)    => Right(i.toChar)
    case PrimitiveValue.String(s) =>
      if (s.length == 1) Right(s.charAt(0))
      else Left(s"Cannot convert string of length ${s.length} to Char")
    case other => Left(s"Cannot convert ${other.getClass.getSimpleName} to Char")
  }

  private def primitiveToBigInt(pv: PrimitiveValue): Either[String, BigInt] = pv match {
    case PrimitiveValue.BigInt(b) => Right(b)
    case PrimitiveValue.Long(l)       => Right(BigInt(l))
    case PrimitiveValue.Int(i)        => Right(BigInt(i))
    case PrimitiveValue.String(s)     => parseBigIntSafe(s)
    case other                        => Left(s"Cannot convert ${other.getClass.getSimpleName} to BigInt")
  }

  private def primitiveToBigDecimal(pv: PrimitiveValue): Either[String, BigDecimal] = pv match {
    case PrimitiveValue.BigDecimal(b)  => Right(b)
    case PrimitiveValue.BigInt(b)  => Right(BigDecimal(b))
    case PrimitiveValue.Double(d)      => Right(BigDecimal(d))
    case PrimitiveValue.Float(f)       => Right(BigDecimal(f.toDouble))
    case PrimitiveValue.Long(l)        => Right(BigDecimal(l))
    case PrimitiveValue.Int(i)         => Right(BigDecimal(i))
    case PrimitiveValue.String(s)      => parseBigDecimalSafe(s)
    case other                         => Left(s"Cannot convert ${other.getClass.getSimpleName} to BigDecimal")
  }

  // Safe parsing helpers
  private def parseIntSafe(s: String): Either[String, Int] =
    try Right(s.toInt)
    catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as Int") }

  private def parseLongSafe(s: String): Either[String, Long] =
    try Right(s.toLong)
    catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as Long") }

  private def parseDoubleSafe(s: String): Either[String, Double] =
    try Right(s.toDouble)
    catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as Double") }

  private def parseFloatSafe(s: String): Either[String, Float] =
    try Right(s.toFloat)
    catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as Float") }

  private def parseShortSafe(s: String): Either[String, Short] =
    try Right(s.toShort)
    catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as Short") }

  private def parseByteSafe(s: String): Either[String, Byte] =
    try Right(s.toByte)
    catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as Byte") }

  private def parseBigIntSafe(s: String): Either[String, BigInt] =
    try Right(BigInt(s))
    catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as BigInt") }

  private def parseBigDecimalSafe(s: String): Either[String, BigDecimal] =
    try Right(BigDecimal(s))
    catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as BigDecimal") }
}
