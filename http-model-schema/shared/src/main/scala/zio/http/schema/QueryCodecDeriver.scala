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

import zio.blocks.schema.binding.{Binding, HasBinding, Register, Registers}
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.schema.{Lazy, Modifier, PrimitiveType, Reflect, SchemaError, Term}
import zio.blocks.docs.Doc
import zio.blocks.typeid.TypeId
import zio.http.{QueryParams, QueryParamsBuilder}

import scala.util.control.NonFatal

object QueryCodecDeriver extends Deriver[QueryCodec] {
  private object ErrorFactory extends ParamCodecSupport.DecodeErrorFactory {
    def malformed(name: String, raw: String, cause: String): String =
      QueryParamError.Malformed(name, raw, cause).message
  }

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[QueryCodec[A]] =
    Lazy {
      buildTopLevelCodec(ParamCodecSupport.SinglePrimitive(primitiveType))
    }

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[QueryCodec[A]] =
    Lazy {
      val fieldCodecs   = new Array[ParamCodecSupport.FieldCodec](fields.length)
      val fieldNames    = new Array[String](fields.length)
      val fieldReflects = new Array[Reflect[F, Any]](fields.length)
      var index         = 0
      while (index < fields.length) {
        val field = fields(index)
        D.instance(field.value.metadata).force
        fieldNames(index) = field.name
        fieldReflects(index) = field.value.asInstanceOf[Reflect[F, Any]]
        fieldCodecs(index) = ParamCodecSupport.buildFieldCodec(field.value, field.name) match {
          case Right(codec) => codec
          case Left(error)  => throw new UnsupportedOperationException(error)
        }
        index += 1
      }
      val fieldRegs =
        Reflect.Record.registers(fieldReflects.asInstanceOf[Array[Reflect[F, ?]]]).asInstanceOf[Array[Register[Any]]]

      new QueryCodec[A] {
        private[this] val constructor   = binding.constructor
        private[this] val deconstructor = binding.deconstructor

        def encode(value: A, output: QueryParamsBuilder): Unit = {
          val registers = Registers(deconstructor.usedRegisters)
          deconstructor.deconstruct(registers, 0L, value)
          var idx = 0
          while (idx < fieldRegs.length) {
            val encodedValues = fieldCodecs(idx).encodeAny(fieldRegs(idx).get(registers, 0L))
            var valueIndex    = 0
            while (valueIndex < encodedValues.length) {
              output.add(fieldNames(idx), encodedValues(valueIndex))
              valueIndex += 1
            }
            idx += 1
          }
        }

        def decode(input: QueryParams): Either[SchemaError, A] = {
          val registers = Registers(constructor.usedRegisters)
          var idx       = 0
          while (idx < fieldRegs.length) {
            val rawValues = input.get(fieldNames(idx))
            fieldCodecs(idx).decodeAny(fieldNames(idx), rawValues, ErrorFactory) match {
              case Right(decoded) => fieldRegs(idx).set(registers, 0L, decoded)
              case Left(error)    => return Left(error)
            }
            idx += 1
          }
          Right(constructor.construct(registers, 0L))
        }
      }
    }

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[QueryCodec[A]] =
    Lazy {
      if (typeId.isOption && cases.length == 2)
        ParamCodecSupport.buildOptionalCodec(cases(1).value, "value") match {
          case Right(fieldCodec) => buildTopLevelCodec(fieldCodec)
          case Left(_)           => unsupportedTopLevelCodec[A](s"QueryCodec does not support variant schema ${typeId.fullName}")
        }
      else
        unsupportedTopLevelCodec[A](s"QueryCodec does not support variant schema ${typeId.fullName}")
    }

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[QueryCodec[C[A]]] =
    Lazy {
      ParamCodecSupport.buildSequenceCodec(element, binding.constructor, "value") match {
        case Right(fieldCodec) => buildTopLevelCodec(fieldCodec).asInstanceOf[QueryCodec[C[A]]]
        case Left(_)           =>
          unsupportedTopLevelCodec[C[A]](s"QueryCodec does not support sequence schema ${typeId.fullName}")
      }
    }

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[QueryCodec[M[K, V]]] =
    Lazy(unsupportedTopLevelCodec[M[K, V]](s"QueryCodec does not support map schema ${typeId.fullName}"))

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[zio.blocks.schema.DynamicValue],
    examples: Seq[zio.blocks.schema.DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[QueryCodec[zio.blocks.schema.DynamicValue]] =
    Lazy(unsupportedTopLevelCodec[zio.blocks.schema.DynamicValue]("QueryCodec does not support dynamic values"))

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[QueryCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      D.instance(wrapped.metadata).map { wrappedCodec =>
        new QueryCodec[A] {
          def encode(value: A, output: QueryParamsBuilder): Unit = wrappedCodec.encode(binding.unwrap(value), output)

          def decode(input: QueryParams): Either[SchemaError, A] =
            wrappedCodec.decode(input).flatMap { value =>
              try Right(binding.wrap(value))
              catch {
                case NonFatal(error) => Left(SchemaError.conversionFailed(Nil, error.getMessage))
              }
            }
        }
      }
    } else binding.asInstanceOf[BindingInstance[QueryCodec, ?, A]].instance

  private def buildTopLevelCodec[A](fieldCodec: ParamCodecSupport.FieldCodec): QueryCodec[A] =
    new QueryCodec[A] {
      def encode(value: A, output: QueryParamsBuilder): Unit = {
        val encodedValues = fieldCodec.encodeAny(value)
        var index         = 0
        while (index < encodedValues.length) {
          output.add("value", encodedValues(index))
          index += 1
        }
      }

      def decode(input: QueryParams): Either[SchemaError, A] =
        fieldCodec.decodeAny("value", input.get("value"), ErrorFactory).asInstanceOf[Either[SchemaError, A]]
    }

  private def unsupportedTopLevelCodec[A](message: String): QueryCodec[A] =
    new QueryCodec[A] {
      def encode(value: A, output: QueryParamsBuilder): Unit =
        throw new UnsupportedOperationException(message)

      def decode(input: QueryParams): Either[SchemaError, A] =
        throw new UnsupportedOperationException(message)
    }
}
