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
import zio.blocks.docs.Doc
import zio.blocks.schema.binding.{Binding, HasBinding, Register, Registers}
import zio.blocks.schema.codec.Codec
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.schema.{DynamicOptic, Lazy, Modifier, PrimitiveType, Reflect, SchemaError, Term}
import zio.blocks.typeid.TypeId

import scala.util.control.NonFatal

/**
 * Shared derivation core for the query and header codecs.
 *
 * `QueryCodecDeriver` and `HeaderCodecDeriver` differ only in transport details
 * (how field keys map to wire names, how raw values are read and written, and
 * which message renders malformed values), so every `derive*` method lives here
 * exactly once. Subclasses supply the transport operations plus a way to build
 * their concrete codec type.
 *
 * Record decoding accumulates failures across fields (combining them with
 * `SchemaError.++`, mirroring the `Into.sequence` accumulation idiom) instead
 * of failing fast on the first bad field, and every conversion failure carries
 * the field name in its path. See [[SchemaError.atField]] for how paths
 * compose.
 */
private[schema] abstract class ParamCodecDeriver[In, Out, TC[A] <: Codec[In, Out, A]](
  codecName: String,
  malformedMessage: (String, String, String) => String
) extends Deriver[TC] {

  /**
   * Shared top-level key for whole-value codecs, lowercase on both transports.
   */
  protected final val topLevelKey: String = "value"

  private object ErrorFactory extends ParamCodecSupport.DecodeErrorFactory {
    def malformed(name: String, raw: String, cause: String): String =
      malformedMessage(name, raw, cause)
  }

  /**
   * Maps a schema field name to its wire name (identity for queries,
   * UPPER-KEBAB for headers).
   */
  protected def fieldKey(fieldName: String): String

  protected def readField(input: In, key: String): Option[Chunk[String]]

  protected def writeField(output: Out, key: String, value: String): Unit

  protected def makeCodec[A](
    encodeFn: (A, Out) => Unit,
    decodeFn: In => Either[SchemaError, A]
  ): TC[A]

  protected def unsupportedCodec[A](message: String): TC[A] =
    makeCodec[A](
      (_, _) => throw new UnsupportedOperationException(message),
      _ => throw new UnsupportedOperationException(message)
    )

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[TC[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      Lazy {
        buildTopLevelCodec(ParamCodecSupport.SinglePrimitive(primitiveType))
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]] =
    Lazy {
      val fieldCodecs   = new Array[ParamCodecSupport.FieldCodec](fields.length)
      val fieldKeys     = new Array[String](fields.length)
      val fieldReflects = new Array[Reflect[F, Any]](fields.length)
      var index         = 0
      while (index < fields.length) {
        val field = fields(index)
        D.instance(field.value.metadata).force
        fieldKeys(index) = fieldKey(field.name)
        fieldReflects(index) = field.value.asInstanceOf[Reflect[F, Any]]
        fieldCodecs(index) = ParamCodecSupport.buildFieldCodec(field.value, field.name) match {
          case Right(codec) => codec
          case Left(error)  => throw new UnsupportedOperationException(error)
        }
        index += 1
      }
      val fieldRegs =
        Reflect.Record.registers(fieldReflects.asInstanceOf[Array[Reflect[F, ?]]]).asInstanceOf[Array[Register[Any]]]
      val constructor   = binding.constructor
      val deconstructor = binding.deconstructor
      val factory       = ErrorFactory
      makeCodec[A](
        (value, output) => {
          val registers = Registers(deconstructor.usedRegisters)
          deconstructor.deconstruct(registers, 0L, value)
          var idx = 0
          while (idx < fieldRegs.length) {
            val encodedValues = fieldCodecs(idx).encodeAny(fieldRegs(idx).get(registers, 0L))
            var valueIndex    = 0
            while (valueIndex < encodedValues.length) {
              writeField(output, fieldKeys(idx), encodedValues(valueIndex))
              valueIndex += 1
            }
            idx += 1
          }
        },
        input => {
          val registers             = Registers(constructor.usedRegisters)
          var idx                   = 0
          var failures: SchemaError = null
          while (idx < fieldRegs.length) {
            val rawValues = readField(input, fieldKeys(idx))
            fieldCodecs(idx).decodeAny(fieldKeys(idx), rawValues, factory) match {
              case Right(decoded) => fieldRegs(idx).set(registers, 0L, decoded)
              case Left(error)    =>
                failures = if (failures eq null) error else failures ++ error
            }
            idx += 1
          }
          if (failures eq null) Right(constructor.construct(registers, 0L))
          else Left(failures)
        }
      )
    }

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]] =
    Lazy {
      if (typeId.isOption && cases.length == 2)
        ParamCodecSupport.buildOptionalCodec(cases(1).value, topLevelKey) match {
          case Right(fieldCodec) => buildTopLevelCodec(fieldCodec)
          case Left(_)           => unsupportedCodec[A](s"$codecName does not support variant schema ${typeId.fullName}")
        }
      else
        unsupportedCodec[A](s"$codecName does not support variant schema ${typeId.fullName}")
    }

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[C[A]]] =
    Lazy {
      ParamCodecSupport.buildSequenceCodec(element, binding.constructor, topLevelKey) match {
        case Right(fieldCodec) => buildTopLevelCodec(fieldCodec).asInstanceOf[TC[C[A]]]
        case Left(_)           =>
          unsupportedCodec[C[A]](s"$codecName does not support sequence schema ${typeId.fullName}")
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[M[K, V]]] =
    Lazy(unsupportedCodec[M[K, V]](s"$codecName does not support map schema ${typeId.fullName}"))

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[zio.blocks.schema.DynamicValue],
    examples: Seq[zio.blocks.schema.DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[zio.blocks.schema.DynamicValue]] =
    Lazy(unsupportedCodec[zio.blocks.schema.DynamicValue](s"$codecName does not support dynamic values"))

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      D.instance(wrapped.metadata).map { wrappedCodec =>
        makeCodec[A](
          (value, output) => wrappedCodec.encode(binding.unwrap(value), output),
          input =>
            wrappedCodec.decode(input).flatMap { value =>
              try Right(binding.wrap(value))
              catch {
                case NonFatal(error) =>
                  Left(
                    SchemaError.conversionFailed(List(DynamicOptic.Node.Field(topLevelKey)), error.getMessage)
                  )
              }
            }
        )
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance

  private def buildTopLevelCodec[A](fieldCodec: ParamCodecSupport.FieldCodec): TC[A] =
    makeCodec[A](
      (value, output) => {
        val encodedValues = fieldCodec.encodeAny(value)
        var index         = 0
        while (index < encodedValues.length) {
          writeField(output, topLevelKey, encodedValues(index))
          index += 1
        }
      },
      input =>
        fieldCodec
          .decodeAny(topLevelKey, readField(input, topLevelKey), ErrorFactory)
          .asInstanceOf[Either[SchemaError, A]]
    )
}
