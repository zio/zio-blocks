package zio.http.schema

import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

private[schema] object StringDecoder {

  def decode[T](raw: String, schema: Schema[T]): Either[String, T] =
    schema.reflect match {
      case p: Reflect.Primitive[Binding, T] @unchecked =>
        decodePrimitive(raw, p.primitiveType)
      case _ =>
        Left("Unsupported schema type for string decoding")
    }

  private def decodePrimitive[T](raw: String, pt: PrimitiveType[T]): Either[String, T] =
    try
      pt match {
        case _: PrimitiveType.String     => Right(raw.asInstanceOf[T])
        case _: PrimitiveType.Int        => Right(raw.toInt.asInstanceOf[T])
        case _: PrimitiveType.Long       => Right(raw.toLong.asInstanceOf[T])
        case _: PrimitiveType.Boolean    => Right(raw.toBoolean.asInstanceOf[T])
        case _: PrimitiveType.Double     => Right(raw.toDouble.asInstanceOf[T])
        case _: PrimitiveType.Float      => Right(raw.toFloat.asInstanceOf[T])
        case _: PrimitiveType.Short      => Right(raw.toShort.asInstanceOf[T])
        case _: PrimitiveType.Byte       => Right(raw.toByte.asInstanceOf[T])
        case _: PrimitiveType.BigInt     => Right(scala.BigInt(raw).asInstanceOf[T])
        case _: PrimitiveType.BigDecimal => Right(scala.BigDecimal(raw).asInstanceOf[T])
        case _: PrimitiveType.UUID       => Right(java.util.UUID.fromString(raw).asInstanceOf[T])
        case _: PrimitiveType.Char       =>
          if (raw.length == 1) Right(raw.charAt(0).asInstanceOf[T])
          else Left(s"Expected single character but got '$raw'")
        case _ => Left("Unsupported primitive type for string decoding")
      }
    catch {
      case _: NumberFormatException    => Left(s"Cannot parse '$raw' as ${typeName(pt)}")
      case _: IllegalArgumentException => Left(s"Cannot parse '$raw' as ${typeName(pt)}")
    }

  private def typeName[T](pt: PrimitiveType[T]): String = pt match {
    case _: PrimitiveType.String     => "String"
    case _: PrimitiveType.Int        => "Int"
    case _: PrimitiveType.Long       => "Long"
    case _: PrimitiveType.Boolean    => "Boolean"
    case _: PrimitiveType.Double     => "Double"
    case _: PrimitiveType.Float      => "Float"
    case _: PrimitiveType.Short      => "Short"
    case _: PrimitiveType.Byte       => "Byte"
    case _: PrimitiveType.BigInt     => "BigInt"
    case _: PrimitiveType.BigDecimal => "BigDecimal"
    case _: PrimitiveType.UUID       => "UUID"
    case _: PrimitiveType.Char       => "Char"
    case _                           => "Unknown"
  }
}
