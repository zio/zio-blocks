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

package zio.http.schema

import zio.blocks.schema.binding.Binding
import zio.blocks.schema.{PrimitiveType, Reflect, Schema}

import java.util.UUID

/**
 * Primitive `<->` string parsing shared by the query/header transports. Strings
 * are only the wire; the domain here is primitives.
 */
private[schema] object ParamPrimitiveCodec {

  /**
   * Parses `raw` as the given primitive type, with the rules and messages of
   * the `query` and `header` extension methods. Decodes `raw` as a `T`, or
   * returns the reason it cannot, when `schema` is one of the supported
   * primitives.
   *
   * This is the one shared string-to-primitive entry point for the
   * `QueryParamsSchemaOps` / `HeadersSchemaOps` extension methods; the derivers
   * reach the same parser through
   * [[ParamPrimitiveCodec.decodePrimitiveString]]. It supports primitive
   * schemas of 12 types, each parsed with the Scala or JDK parser of that type:
   * `String` (the value as is), `Boolean` (`true` or `false`, any letter case),
   * `Char` (exactly one character), `Byte`, `Short`, `Int`, `Long`, `Float`,
   * `Double`, `BigInt`, `BigDecimal` and `java.util.UUID`. A failure is a
   * `Left` with the reason, `Cannot parse 'abc' as Int` or, for `Char`,
   * `Expected single character but got 'abc'`; the callers wrap it in
   * `Malformed`.
   *
   * Every other schema fails on every value: a primitive type such as
   * `java.time.Instant` or `Unit` gives "Unsupported primitive type for string
   * decoding", and an `Option`, a collection, a wrapper or a record gives
   * "Unsupported schema type for string decoding". The schema's `Validation` is
   * never checked.
   *
   * {{{
   * import zio.http.QueryParams
   * import zio.http.schema._
   *
   * val params = QueryParams(
   *   "order"   -> "550e8400-e29b-41d4-a716-446655440000",
   *   "express" -> "TRUE",
   *   "qty"     -> "",
   *   "grade"   -> "AB"
   * )
   *
   * params.query[java.util.UUID]("order") // Right(550e8400-e29b-41d4-a716-446655440000)
   * params.query[Boolean]("express")      // Right(true)
   * params.query[Int]("qty")              // Left(Malformed(qty,,Cannot parse '' as Int))
   * params.query[Char]("grade")           // Left(Malformed(grade,AB,Expected single character but got 'AB'))
   * params.query[Option[Int]]("qty")      // Left(Malformed(qty,,Unsupported schema type for string decoding))
   * }}}
   */
  def decode[T](raw: String, schema: Schema[T]): Either[String, T] =
    schema.reflect match {
      case p: Reflect.Primitive[Binding, T] @unchecked =>
        decodePrimitiveString(raw, p.primitiveType)
      case _ =>
        Left("Unsupported schema type for string decoding")
    }

  private[schema] def decodePrimitiveString[A](raw: String, primitiveType: PrimitiveType[A]): Either[String, A] =
    try {
      primitiveType match {
        case _: PrimitiveType.String     => Right(raw.asInstanceOf[A])
        case _: PrimitiveType.Int        => Right(raw.toInt.asInstanceOf[A])
        case _: PrimitiveType.Long       => Right(raw.toLong.asInstanceOf[A])
        case _: PrimitiveType.Boolean    => Right(raw.toBoolean.asInstanceOf[A])
        case _: PrimitiveType.Double     => Right(raw.toDouble.asInstanceOf[A])
        case _: PrimitiveType.Float      => Right(raw.toFloat.asInstanceOf[A])
        case _: PrimitiveType.Short      => Right(raw.toShort.asInstanceOf[A])
        case _: PrimitiveType.Byte       => Right(raw.toByte.asInstanceOf[A])
        case _: PrimitiveType.BigInt     => Right(scala.BigInt(raw).asInstanceOf[A])
        case _: PrimitiveType.BigDecimal => Right(scala.BigDecimal(raw).asInstanceOf[A])
        case _: PrimitiveType.UUID       => Right(UUID.fromString(raw).asInstanceOf[A])
        case _: PrimitiveType.Char       =>
          if (raw.length == 1) Right(raw.charAt(0).asInstanceOf[A])
          else Left(s"Expected single character but got '$raw'")
        case _ => Left("Unsupported primitive type for string decoding")
      }
    } catch {
      case _: NumberFormatException =>
        Left(s"Cannot parse '$raw' as ${typeName(primitiveType)}")
      case _: IllegalArgumentException =>
        Left(s"Cannot parse '$raw' as ${typeName(primitiveType)}")
    }

  private[schema] def renderPrimitive[A](value: A, primitiveType: PrimitiveType[A]): String = primitiveType match {
    case _: PrimitiveType.String     => value.asInstanceOf[String]
    case _: PrimitiveType.Int        => value.toString
    case _: PrimitiveType.Long       => value.toString
    case _: PrimitiveType.Boolean    => value.toString
    case _: PrimitiveType.Double     => value.toString
    case _: PrimitiveType.Float      => value.toString
    case _: PrimitiveType.Short      => value.toString
    case _: PrimitiveType.Byte       => value.toString
    case _: PrimitiveType.BigInt     => value.toString
    case _: PrimitiveType.BigDecimal => value.toString
    case _: PrimitiveType.UUID       => value.asInstanceOf[UUID].toString
    case _: PrimitiveType.Char       => value.toString
    case _                           => throw new UnsupportedOperationException(s"Unsupported primitive type: $primitiveType")
  }

  /**
   * Unboxed renderers for the record fast path: same output as
   * [[renderPrimitive]] for the matching type, but the value stays unboxed. The
   * record deriver calls these only for fields whose type it verified at
   * derivation time, so the caller guarantees the type matches.
   */
  private[schema] def renderInt(value: Int): String         = value.toString
  private[schema] def renderLong(value: Long): String       = value.toString
  private[schema] def renderFloat(value: Float): String     = value.toString
  private[schema] def renderDouble(value: Double): String   = value.toString
  private[schema] def renderBoolean(value: Boolean): String = value.toString
  private[schema] def renderByte(value: Byte): String       = value.toString
  private[schema] def renderChar(value: Char): String       = value.toString
  private[schema] def renderShort(value: Short): String     = value.toString

  /**
   * Renders an object-tagged primitive (`String`, `UUID`, `BigInt`,
   * `BigDecimal`) that the record fast path read as a reference. Same output as
   * [[renderPrimitive]] for those types.
   */
  private[schema] def renderObject[A](value: AnyRef, primitiveType: PrimitiveType[A]): String =
    primitiveType match {
      case _: PrimitiveType.String     => value.asInstanceOf[String]
      case _: PrimitiveType.UUID       => value.asInstanceOf[UUID].toString
      case _: PrimitiveType.BigInt     => value.asInstanceOf[scala.BigInt].toString
      case _: PrimitiveType.BigDecimal => value.asInstanceOf[scala.BigDecimal].toString
      case _                           => throw new UnsupportedOperationException(s"Unsupported primitive type: $primitiveType")
    }

  /**
   * Unboxed parsers for the record fast path: same values and failure reasons
   * as [[decodePrimitiveString]] for the matching type, but failures throw
   * `IllegalArgumentException` carrying the reason instead of allocating an
   * `Either`, and successes never box. The record deriver catches the exception
   * and wraps it exactly like the boxed path does.
   */
  private[schema] def parseInt(raw: String): Int =
    try raw.toInt
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "Int") }

  private[schema] def parseLong(raw: String): Long =
    try raw.toLong
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "Long") }

  private[schema] def parseFloat(raw: String): Float =
    try raw.toFloat
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "Float") }

  private[schema] def parseDouble(raw: String): Double =
    try raw.toDouble
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "Double") }

  private[schema] def parseBoolean(raw: String): Boolean =
    try raw.toBoolean
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "Boolean") }

  private[schema] def parseByte(raw: String): Byte =
    try raw.toByte
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "Byte") }

  private[schema] def parseChar(raw: String): Char =
    if (raw.length == 1) raw.charAt(0)
    else throw new IllegalArgumentException(s"Expected single character but got '$raw'")

  private[schema] def parseShort(raw: String): Short =
    try raw.toShort
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "Short") }

  private[schema] def parseString(raw: String): String = raw

  private[schema] def parseUUID(raw: String): UUID =
    try UUID.fromString(raw)
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "UUID") }

  private[schema] def parseBigInt(raw: String): scala.BigInt =
    try scala.BigInt(raw)
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "BigInt") }

  private[schema] def parseBigDecimal(raw: String): scala.BigDecimal =
    try scala.BigDecimal(raw)
    catch { case _: IllegalArgumentException => throw cannotParse(raw, "BigDecimal") }

  /**
   * Parses an object-tagged primitive (`String`, `UUID`, `BigInt`,
   * `BigDecimal`) into a reference. Same values and failure reasons as
   * [[decodePrimitiveString]] for those types.
   */
  private[schema] def parseObject[A](raw: String, primitiveType: PrimitiveType[A]): AnyRef =
    primitiveType match {
      case _: PrimitiveType.String     => raw
      case _: PrimitiveType.UUID       => parseUUID(raw)
      case _: PrimitiveType.BigInt     => parseBigInt(raw)
      case _: PrimitiveType.BigDecimal => parseBigDecimal(raw)
      case _                           => throw new UnsupportedOperationException(s"Unsupported primitive type: $primitiveType")
    }

  private def cannotParse(raw: String, typeName: String): IllegalArgumentException =
    new IllegalArgumentException(s"Cannot parse '$raw' as $typeName")

  private def typeName[A](primitiveType: PrimitiveType[A]): String = primitiveType match {
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
