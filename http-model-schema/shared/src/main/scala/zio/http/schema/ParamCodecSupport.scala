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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding.{HasBinding, SeqConstructor}
import zio.blocks.schema.{PrimitiveType, Reflect, SchemaError}

import java.util.UUID
import scala.reflect.ClassTag
import scala.util.control.NonFatal

private[schema] object ParamCodecSupport {

  trait DecodeErrorFactory {
    def malformed(name: String, raw: String, cause: String): String
  }

  sealed trait FieldCodec {
    def encodeAny(value: Any): Chunk[String]
    def decodeAny(
      fieldName: String,
      rawValues: Option[Chunk[String]],
      errorFactory: DecodeErrorFactory
    ): Either[SchemaError, Any]
  }

  final case class SinglePrimitive[A](primitiveType: PrimitiveType[A]) extends FieldCodec {
    def encodeAny(value: Any): Chunk[String] = Chunk.single(renderPrimitive(value.asInstanceOf[A], primitiveType))

    def decodeAny(
      fieldName: String,
      rawValues: Option[Chunk[String]],
      errorFactory: DecodeErrorFactory
    ): Either[SchemaError, Any] =
      rawValues match {
        case Some(values) if values.nonEmpty =>
          decodePrimitive(fieldName, values(0), primitiveType, errorFactory).asInstanceOf[Either[SchemaError, Any]]
        case _ => Left(SchemaError.missingField(Nil, fieldName))
      }
  }

  final case class OptionalValue(inner: FieldCodec) extends FieldCodec {
    def encodeAny(value: Any): Chunk[String] =
      value match {
        case None             => Chunk.empty
        case Some(innerValue) => inner.encodeAny(innerValue)
      }

    def decodeAny(
      fieldName: String,
      rawValues: Option[Chunk[String]],
      errorFactory: DecodeErrorFactory
    ): Either[SchemaError, Any] =
      rawValues match {
        case None                           => Right(None)
        case Some(values) if values.isEmpty => Right(None)
        case Some(values)                   => inner.decodeAny(fieldName, Some(Chunk.single(values(0))), errorFactory).map(Some(_))
      }
  }

  final case class SequenceValue[C[_]](constructor: SeqConstructor[C], elementCodec: FieldCodec) extends FieldCodec {
    def encodeAny(value: Any): Chunk[String] = {
      val builder  = Chunk.newBuilder[String]
      val iterator = value.asInstanceOf[Iterable[Any]].iterator
      while (iterator.hasNext) {
        val encodedValues = elementCodec.encodeAny(iterator.next())
        var index         = 0
        while (index < encodedValues.length) {
          builder += encodedValues(index)
          index += 1
        }
      }
      builder.result()
    }

    def decodeAny(
      fieldName: String,
      rawValues: Option[Chunk[String]],
      errorFactory: DecodeErrorFactory
    ): Either[SchemaError, Any] = {
      val values                           = rawValues.getOrElse(Chunk.empty)
      implicit val classTag: ClassTag[Any] = ClassTag.Any
      val builder                          = constructor.newBuilder[Any](values.length)
      var index                            = 0
      while (index < values.length) {
        elementCodec.decodeAny(fieldName, Some(Chunk.single(values(index))), errorFactory) match {
          case Right(decoded) => constructor.add(builder, decoded)
          case Left(error)    => return Left(error)
        }
        index += 1
      }
      Right(constructor.result(builder))
    }
  }

  final case class WrappedValue[A, B](wrap: B => A, unwrap: A => B, inner: FieldCodec) extends FieldCodec {
    def encodeAny(value: Any): Chunk[String] = inner.encodeAny(unwrap(value.asInstanceOf[A]))

    def decodeAny(
      fieldName: String,
      rawValues: Option[Chunk[String]],
      errorFactory: DecodeErrorFactory
    ): Either[SchemaError, Any] =
      inner.decodeAny(fieldName, rawValues, errorFactory).flatMap { value =>
        try Right(wrap(value.asInstanceOf[B]))
        catch {
          case NonFatal(error) => Left(SchemaError.conversionFailed(Nil, error.getMessage))
        }
      }
  }

  def buildFieldCodec[F[_, _], A](reflect: Reflect[F, A], fieldName: String)(implicit
    F: HasBinding[F]
  ): Either[String, FieldCodec] =
    primitiveFieldCodec(reflect)
      .orElse(optionalFieldCodec(reflect, fieldName))
      .orElse(sequenceFieldCodec(reflect, fieldName))
      .orElse(wrapperFieldCodec(reflect, fieldName))
      .toRight {
        if (reflect.isRecord) s"Nested records are not supported for field '$fieldName'"
        else s"Unsupported schema type for field '$fieldName'"
      }

  def buildTopLevelCodec[F[_, _], A](reflect: Reflect[F, A])(implicit F: HasBinding[F]): Either[String, FieldCodec] =
    primitiveFieldCodec(reflect)
      .orElse(optionalFieldCodec(reflect, "value"))
      .orElse(sequenceFieldCodec(reflect, "value"))
      .orElse(wrapperFieldCodec(reflect, "value"))
      .toRight("Only primitive, optional, sequence, and wrapped primitive schemas are supported at the top level")

  def buildOptionalCodec[F[_, _], A](inner: Reflect[F, A], fieldName: String)(implicit
    F: HasBinding[F]
  ): Either[String, FieldCodec] =
    buildFieldCodec(inner, fieldName).map(OptionalValue.apply)

  def buildSequenceCodec[F[_, _], C[_], A](
    element: Reflect[F, A],
    constructor: SeqConstructor[C],
    fieldName: String
  )(implicit F: HasBinding[F]): Either[String, FieldCodec] =
    buildFieldCodec(element, fieldName).map(SequenceValue(constructor, _))

  private def primitiveFieldCodec[F[_, _], A](reflect: Reflect[F, A]): Option[FieldCodec] =
    reflect.asPrimitive.map { primitive =>
      SinglePrimitive(primitive.primitiveType)
    }

  private def optionalFieldCodec[F[_, _], A](reflect: Reflect[F, A], fieldName: String)(implicit
    F: HasBinding[F]
  ): Option[FieldCodec] =
    if (reflect.isOption) {
      reflect.optionInnerType
        .map(_.asInstanceOf[Reflect[F, Any]])
        .flatMap(inner => buildOptionalCodec(inner, fieldName).toOption)
    } else None

  private def sequenceFieldCodec[F[_, _], A](reflect: Reflect[F, A], fieldName: String)(implicit
    F: HasBinding[F]
  ): Option[FieldCodec] =
    reflect.asSequenceUnknown.flatMap { unknown =>
      val sequence = unknown.sequence
      val element  = sequence.element.asInstanceOf[Reflect[F, Any]]
      buildSequenceCodec(
        element,
        sequence.seqConstructor,
        fieldName
      ).toOption
    }

  private def wrapperFieldCodec[F[_, _], A](reflect: Reflect[F, A], fieldName: String)(implicit
    F: HasBinding[F]
  ): Option[FieldCodec] =
    reflect.asWrapperUnknown.flatMap { unknown =>
      val wrapper = unknown.wrapper
      buildFieldCodec(wrapper.wrapped.asInstanceOf[Reflect[F, unknown.Wrapped]], fieldName).toOption.map { inner =>
        WrappedValue(
          wrapper.binding.wrap.asInstanceOf[unknown.Wrapped => unknown.Wrapping],
          wrapper.binding.unwrap.asInstanceOf[unknown.Wrapping => unknown.Wrapped],
          inner
        )
      }
    }

  private def decodePrimitive[A](
    fieldName: String,
    raw: String,
    primitiveType: PrimitiveType[A],
    errorFactory: DecodeErrorFactory
  ): Either[SchemaError, A] =
    decodePrimitiveString(raw, primitiveType).left.map(cause =>
      SchemaError.conversionFailed(Nil, errorFactory.malformed(fieldName, raw, cause))
    )

  def decodePrimitiveString[A](raw: String, primitiveType: PrimitiveType[A]): Either[String, A] =
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
      case _: NumberFormatException    => Left(s"Cannot parse '$raw' as ${typeName(primitiveType)}")
      case _: IllegalArgumentException => Left(s"Cannot parse '$raw' as ${typeName(primitiveType)}")
    }

  private def renderPrimitive[A](value: A, primitiveType: PrimitiveType[A]): String = primitiveType match {
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
