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
import zio.blocks.schema.binding.{Binding, HasBinding, Register, SeqConstructor}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.codec.Codec
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.schema.{DynamicOptic, Lazy, Modifier, PrimitiveType, Reflect, SchemaError, Term}
import zio.blocks.typeid.TypeId

import scala.annotation.switch
import scala.collection.immutable.::
import scala.reflect.ClassTag
import scala.util.control.NonFatal

private[schema] object ParamCodecDeriver {

  /**
   * Marks codecs built by this deriver family. `Customized` additionally flags
   * codecs that transitively incorporate user-supplied behavior; an explicitly
   * installed codec carrying it is bridged whole rather than rebuilt from
   * shape, so its actual behavior is preserved. Records are never flagged:
   * multi-key record codecs cannot compose as single fields and keep the
   * existing derivation-time unsupported error.
   */
  trait Derived

  /**
   * Flags a [[Derived]] codec that incorporates user-supplied behavior, either
   * directly or through a customized child.
   */
  trait Customized

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

  private[schema] def decodePrimitive[A](
    fieldName: String,
    raw: String,
    primitiveType: PrimitiveType[A],
    errorFactory: DecodeErrorFactory
  ): Either[SchemaError, A] =
    ParamPrimitiveCodec
      .decodePrimitiveString(raw, primitiveType)
      .left
      .map(cause =>
        SchemaError.conversionFailed(
          List(DynamicOptic.Node.Field(fieldName)),
          errorFactory.malformed(fieldName, raw, cause)
        )
      )

  /**
   * Scalar fast-path layout for one record field: the JSON tag mapping (0 =
   * object, 1 = int, 2 = long, 3 = float, 4 = double, 5 = boolean, 6 = byte, 7 =
   * char, 8 = short) with the field's register offset and, for object-tagged
   * fields, its `PrimitiveType` for specialized render/parse. Returns `None`
   * when the field must keep the boxed `FieldCodec` path (non-scalar shapes,
   * custom bridges, exotic primitives, or a register kind that does not agree
   * with the primitive type).
   */
  private def scalarLayout(
    fieldCodec: FieldCodec,
    register: Register[Any]
  ): Option[(Int, RegisterOffset, PrimitiveType[Any])] =
    fieldCodec match {
      case SinglePrimitive(primitiveType) =>
        if (register.isInstanceOf[Register.Int] && primitiveType.isInstanceOf[PrimitiveType.Int])
          Some((1, register.asInstanceOf[Register.Int].offset, null))
        else if (register.isInstanceOf[Register.Long] && primitiveType.isInstanceOf[PrimitiveType.Long])
          Some((2, register.asInstanceOf[Register.Long].offset, null))
        else if (register.isInstanceOf[Register.Float] && primitiveType.isInstanceOf[PrimitiveType.Float])
          Some((3, register.asInstanceOf[Register.Float].offset, null))
        else if (register.isInstanceOf[Register.Double] && primitiveType.isInstanceOf[PrimitiveType.Double])
          Some((4, register.asInstanceOf[Register.Double].offset, null))
        else if (register.isInstanceOf[Register.Boolean] && primitiveType.isInstanceOf[PrimitiveType.Boolean])
          Some((5, register.asInstanceOf[Register.Boolean].offset, null))
        else if (register.isInstanceOf[Register.Byte] && primitiveType.isInstanceOf[PrimitiveType.Byte])
          Some((6, register.asInstanceOf[Register.Byte].offset, null))
        else if (register.isInstanceOf[Register.Char] && primitiveType.isInstanceOf[PrimitiveType.Char])
          Some((7, register.asInstanceOf[Register.Char].offset, null))
        else if (register.isInstanceOf[Register.Short] && primitiveType.isInstanceOf[PrimitiveType.Short])
          Some((8, register.asInstanceOf[Register.Short].offset, null))
        else if (register.isInstanceOf[Register.Object[?]] && isScalarObjectPrimitive(primitiveType))
          Some(
            (0, register.asInstanceOf[Register.Object[AnyRef]].offset, primitiveType.asInstanceOf[PrimitiveType[Any]])
          )
        else None
      case _ => None
    }

  /**
   * Object-tagged primitives the fast path renders and parses without boxing:
   * every `String`-wire primitive except the unboxed eight above. Any other
   * primitive (timestamps, `Unit`, ...) keeps the boxed path with its current
   * throw-on-encode / fail-on-decode behavior.
   */
  private def isScalarObjectPrimitive(primitiveType: PrimitiveType[?]): Boolean =
    primitiveType.isInstanceOf[PrimitiveType.String] ||
      primitiveType.isInstanceOf[PrimitiveType.UUID] ||
      primitiveType.isInstanceOf[PrimitiveType.BigInt] ||
      primitiveType.isInstanceOf[PrimitiveType.BigDecimal]

  /**
   * Combines accumulated record failures in one linear pass, with the same
   * content and order as left-nested `++`: every error's singles concatenated
   * in field order.
   */
  private def combineFailures(failures: Array[SchemaError], count: Int): SchemaError = {
    val builder = List.newBuilder[SchemaError.Single]
    var index   = 0
    while (index < count) {
      builder ++= failures(index).errors
      index += 1
    }
    builder.result() match {
      case head :: tail => new SchemaError(new ::(head, tail))
      case Nil          => throw new IllegalStateException("combineFailures requires at least one failure")
    }
  }
}

private[schema] trait DecodeErrorFactory {
  def malformed(name: String, raw: String, cause: String): String
}

// Non-sealed so transports can bridge user-supplied whole-value codecs as
// record fields; the fast-path cases below stay closed by construction.
private[schema] trait FieldCodec {
  def encodeAny(value: Any): Chunk[String]
  def decodeAny(
    fieldName: String,
    rawValues: Option[Chunk[String]],
    errorFactory: DecodeErrorFactory
  ): Either[SchemaError, Any]
}

private[schema] final case class SinglePrimitive[A](primitiveType: PrimitiveType[A]) extends FieldCodec {
  def encodeAny(value: Any): Chunk[String] =
    Chunk.single(ParamPrimitiveCodec.renderPrimitive(value.asInstanceOf[A], primitiveType))

  def decodeAny(
    fieldName: String,
    rawValues: Option[Chunk[String]],
    errorFactory: DecodeErrorFactory
  ): Either[SchemaError, Any] =
    rawValues match {
      case Some(values) if values.nonEmpty =>
        ParamCodecDeriver
          .decodePrimitive(fieldName, values(0), primitiveType, errorFactory)
          .asInstanceOf[Either[SchemaError, Any]]
      case _ => Left(SchemaError.missingField(Nil, fieldName))
    }
}

private[schema] final case class OptionalValue(inner: FieldCodec) extends FieldCodec {
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

private[schema] final case class SequenceValue[C[_]](constructor: SeqConstructor[C], elementCodec: FieldCodec)
    extends FieldCodec {
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

private[schema] final case class WrappedValue[A, B](wrap: B => A, unwrap: A => B, inner: FieldCodec)
    extends FieldCodec {
  def encodeAny(value: Any): Chunk[String] = inner.encodeAny(unwrap(value.asInstanceOf[A]))

  def decodeAny(
    fieldName: String,
    rawValues: Option[Chunk[String]],
    errorFactory: DecodeErrorFactory
  ): Either[SchemaError, Any] =
    inner.decodeAny(fieldName, rawValues, errorFactory).flatMap { value =>
      try Right(wrap(value.asInstanceOf[B]))
      catch {
        case NonFatal(error) =>
          Left(SchemaError.conversionFailed(List(DynamicOptic.Node.Field(fieldName)), error.getMessage))
      }
    }
}

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

  private object ErrorFactory extends DecodeErrorFactory {
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

  /**
   * Builds a codec flagged [[ParamCodecDeriver.Customized]] for results that
   * incorporate user-supplied behavior.
   */
  protected def makeCustomizedCodec[A](
    encodeFn: (A, Out) => Unit,
    decodeFn: In => Either[SchemaError, A]
  ): TC[A]

  /**
   * Fresh, exclusively-owned output builder used to isolate one custom field
   * value. A new builder per call keeps derivation reentrant and safe for
   * concurrent use; custom codecs must never observe shared mutable state.
   */
  protected def freshOutput(): Out

  /**
   * Drains a temp-built custom value to its entries for re-keying under the
   * record field key. The bridge enforces the canonical [[topLevelKey]] key on
   * these entries (see [[isTopLevelKey]]).
   */
  protected def finishEntries(output: Out): Chunk[(String, String)]

  /**
   * Builds an isolated input carrying `rawValues` under [[topLevelKey]] so a
   * whole-value custom codec decodes exactly the owning field's raw values.
   */
  protected def syntheticInput(rawValues: Option[Chunk[String]]): In

  /**
   * Checks a temp-built entry key against the canonical whole-value key. Query
   * keys compare exactly; header names compare case-insensitively, matching
   * header semantics.
   */
  protected def isTopLevelKey(key: String): Boolean

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
        buildTopLevelCodec(SinglePrimitive(primitiveType), customized = false)
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
      val fieldCodecs   = new Array[FieldCodec](fields.length)
      val fieldKeys     = new Array[String](fields.length)
      val fieldReflects = new Array[Reflect[F, Any]](fields.length)
      var index         = 0
      while (index < fields.length) {
        val field   = fields(index)
        val wireKey = fieldKey(field.name)
        val top     = D.instance(field.value.metadata).force
        fieldKeys(index) = wireKey
        fieldReflects(index) = field.value.asInstanceOf[Reflect[F, Any]]
        fieldCodecs(index) = ParamCodecDeriver.buildFieldCodec(field.value, field.name) match {
          case Right(fast) =>
            if (isPure(top)) fast else customFieldCodec(top.asInstanceOf[TC[Any]], wireKey)
          case Left(error) =>
            if (isPure(top)) throw new UnsupportedOperationException(error)
            else customFieldCodec(top.asInstanceOf[TC[Any]], wireKey)
        }
        index += 1
      }
      val fieldRegs =
        Reflect.Record.registers(fieldReflects.asInstanceOf[Array[Reflect[F, ?]]]).asInstanceOf[Array[Register[Any]]]
      val constructor   = binding.constructor
      val deconstructor = binding.deconstructor
      val factory       = ErrorFactory
      // Scalar fast-path layout per field, mirroring JsonCodecDeriver's
      // type-tag + unboxed-register design: `scalarTags` holds the JSON tag
      // mapping (0 = object, 1 = int, 2 = long, 3 = float, 4 = double,
      // 5 = boolean, 6 = byte, 7 = char, 8 = short) or -1 for fields that keep
      // the boxed `FieldCodec` fallback (Option, Sequence, Wrapper, custom,
      // and exotic primitives). `scalarOffsets` holds the field's register
      // offset for unboxed `Registers` access, and `scalarPrimitives` the
      // `PrimitiveType` for object-tagged fields (String, UUID, BigInt,
      // BigDecimal), used for specialized render/parse.
      val scalarTags       = new Array[Int](fields.length)
      val scalarOffsets    = new Array[RegisterOffset](fields.length)
      val scalarPrimitives = new Array[PrimitiveType[Any]](fields.length)
      var layoutIdx        = 0
      while (layoutIdx < fields.length) {
        ParamCodecDeriver.scalarLayout(fieldCodecs(layoutIdx), fieldRegs(layoutIdx)) match {
          case None =>
            scalarTags(layoutIdx) = -1
          case Some((tag, offset, primitive)) =>
            scalarTags(layoutIdx) = tag
            scalarOffsets(layoutIdx) = offset
            scalarPrimitives(layoutIdx) = primitive
        }
        layoutIdx += 1
      }
      val pool = new RegisterPool(constructor.usedRegisters)
      makeCodec[A](
        (value, output) => {
          val registers = pool.borrow()
          try {
            deconstructor.deconstruct(registers, 0L, value)
            var idx = 0
            while (idx < fieldRegs.length) {
              val tag = scalarTags(idx)
              if (tag < 0) {
                val encodedValues = fieldCodecs(idx).encodeAny(fieldRegs(idx).get(registers, 0L))
                var valueIndex    = 0
                while (valueIndex < encodedValues.length) {
                  writeField(output, fieldKeys(idx), encodedValues(valueIndex))
                  valueIndex += 1
                }
              } else {
                val offset   = scalarOffsets(idx)
                val rendered = (tag: @switch) match {
                  case 1 => ParamPrimitiveCodec.renderInt(registers.getInt(offset))
                  case 2 => ParamPrimitiveCodec.renderLong(registers.getLong(offset))
                  case 3 => ParamPrimitiveCodec.renderFloat(registers.getFloat(offset))
                  case 4 => ParamPrimitiveCodec.renderDouble(registers.getDouble(offset))
                  case 5 => ParamPrimitiveCodec.renderBoolean(registers.getBoolean(offset))
                  case 6 => ParamPrimitiveCodec.renderByte(registers.getByte(offset))
                  case 7 => ParamPrimitiveCodec.renderChar(registers.getChar(offset))
                  case 8 => ParamPrimitiveCodec.renderShort(registers.getShort(offset))
                  case _ => ParamPrimitiveCodec.renderObject(registers.getObject(offset), scalarPrimitives(idx))
                }
                writeField(output, fieldKeys(idx), rendered)
              }
              idx += 1
            }
          } finally pool.release(registers)
        },
        input => {
          val registers = pool.borrow()
          try {
            var failures: Array[SchemaError] = null
            var failureCount                 = 0
            var idx                          = 0
            while (idx < fieldRegs.length) {
              val tag = scalarTags(idx)
              val key = fieldKeys(idx)
              if (tag < 0) {
                val rawValues = readField(input, key)
                fieldCodecs(idx).decodeAny(key, rawValues, factory) match {
                  case Right(decoded) => fieldRegs(idx).set(registers, 0L, decoded)
                  case Left(error)    =>
                    if (failures eq null) failures = new Array[SchemaError](fieldRegs.length)
                    failures(failureCount) = error
                    failureCount += 1
                }
              } else {
                readField(input, key) match {
                  case Some(values) if values.nonEmpty =>
                    val raw = values(0)
                    try {
                      val offset = scalarOffsets(idx)
                      (tag: @switch) match {
                        case 1 => registers.setInt(offset, ParamPrimitiveCodec.parseInt(raw))
                        case 2 => registers.setLong(offset, ParamPrimitiveCodec.parseLong(raw))
                        case 3 => registers.setFloat(offset, ParamPrimitiveCodec.parseFloat(raw))
                        case 4 => registers.setDouble(offset, ParamPrimitiveCodec.parseDouble(raw))
                        case 5 => registers.setBoolean(offset, ParamPrimitiveCodec.parseBoolean(raw))
                        case 6 => registers.setByte(offset, ParamPrimitiveCodec.parseByte(raw))
                        case 7 => registers.setChar(offset, ParamPrimitiveCodec.parseChar(raw))
                        case 8 => registers.setShort(offset, ParamPrimitiveCodec.parseShort(raw))
                        case _ =>
                          registers.setObject(offset, ParamPrimitiveCodec.parseObject(raw, scalarPrimitives(idx)))
                      }
                    } catch {
                      case error: IllegalArgumentException =>
                        if (failures eq null) failures = new Array[SchemaError](fieldRegs.length)
                        failures(failureCount) = SchemaError.conversionFailed(
                          List(DynamicOptic.Node.Field(key)),
                          factory.malformed(key, raw, error.getMessage)
                        )
                        failureCount += 1
                    }
                  case _ =>
                    if (failures eq null) failures = new Array[SchemaError](fieldRegs.length)
                    failures(failureCount) = SchemaError.missingField(Nil, key)
                    failureCount += 1
                }
              }
              idx += 1
            }
            if (failures eq null) Right(constructor.construct(registers, 0L))
            else if (failureCount == 1) Left(failures(0))
            else Left(ParamCodecDeriver.combineFailures(failures, failureCount))
          } finally pool.release(registers)
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
      if (typeId.isOption && cases.length == 2) {
        // The Some payload is a single-field record; the decodable value is its
        // first field (mirrors Reflect.optionInnerType).
        val inner = cases(1).value.asRecord.flatMap(record => record.fields.headOption.map(_.value))
        inner match {
          case Some(payload) =>
            ParamCodecDeriver.buildOptionalCodec(payload, topLevelKey) match {
              case Right(fast) =>
                val innerTop = D.instance(payload.metadata).force
                if (isPure(innerTop)) buildTopLevelCodec(fast, customized = false)
                else
                  buildTopLevelCodec(
                    OptionalValue(customFieldCodec(innerTop.asInstanceOf[TC[Any]], topLevelKey)),
                    customized = true
                  )
              case Left(_) => unsupportedCodec[A](s"$codecName does not support variant schema ${typeId.fullName}")
            }
          case None => unsupportedCodec[A](s"$codecName does not support variant schema ${typeId.fullName}")
        }
      } else
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
      ParamCodecDeriver.buildSequenceCodec(element, binding.constructor, topLevelKey) match {
        case Right(fast) =>
          val elementTop = D.instance(element.metadata).force
          if (isPure(elementTop)) buildTopLevelCodec(fast, customized = false).asInstanceOf[TC[C[A]]]
          else
            buildTopLevelCodec(
              SequenceValue(
                binding.constructor,
                customFieldCodec(elementTop.asInstanceOf[TC[Any]], topLevelKey)
              ),
              customized = true
            ).asInstanceOf[TC[C[A]]]
        case Left(_) =>
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
        def encode(value: A, output: Out): Unit =
          wrappedCodec.encode(binding.unwrap(value), output)

        def decode(input: In): Either[SchemaError, A] =
          wrappedCodec.decode(input).flatMap { value =>
            try Right(binding.wrap(value))
            catch {
              case NonFatal(error) =>
                Left(
                  SchemaError.conversionFailed(List(DynamicOptic.Node.Field(topLevelKey)), error.getMessage)
                )
            }
          }

        if (isPure(wrappedCodec)) makeCodec[A](encode, decode)
        else makeCustomizedCodec[A](encode, decode)
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance

  /**
   * Bridges a user-supplied whole-value codec to one record field. Whole-value
   * codecs must use the canonical [[topLevelKey]] key: encoding runs the custom
   * codec against an isolated builder and re-emits its values under the field's
   * wire key, rejecting any other key so a codec can always decode its own
   * output; decoding feeds the field's raw values back through the custom codec
   * via an isolated input keyed by [[topLevelKey]]. Custom failures keep their
   * content with the root key re-anchored to the field key, and the record loop
   * accumulates them across fields.
   */
  private def customFieldCodec[A](custom: TC[A], wireKey: String): FieldCodec =
    new FieldCodec {
      def encodeAny(value: Any): Chunk[String] = {
        val tmp = freshOutput()
        custom.asInstanceOf[TC[Any]].encode(value, tmp)
        val entries     = finishEntries(tmp)
        var index       = 0
        var bad: String = null
        while (index < entries.length) {
          if (!isTopLevelKey(entries(index)._1)) bad = entries(index)._1
          index += 1
        }
        if (bad ne null)
          throw new UnsupportedOperationException(
            s"$codecName requires custom field codecs to use the canonical '$topLevelKey' key " +
              s"as record field '$wireKey', but found key '$bad'"
          )
        val builder = Chunk.newBuilder[String]
        index = 0
        while (index < entries.length) {
          builder += entries(index)._2
          index += 1
        }
        builder.result()
      }

      def decodeAny(
        fieldName: String,
        rawValues: Option[Chunk[String]],
        errorFactory: DecodeErrorFactory
      ): Either[SchemaError, Any] =
        custom
          .decode(syntheticInput(rawValues))
          .left
          .map(error => remapError(error, fieldName))
          .asInstanceOf[Either[SchemaError, Any]]
    }

  /**
   * Reports whether a child codec was built purely from shape by this deriver
   * family, with no user-supplied behavior anywhere inside. Pure children keep
   * the direct fast path; anything else is bridged whole so explicitly
   * installed behavior is preserved.
   */
  private def isPure(codec: Any): Boolean =
    codec.isInstanceOf[ParamCodecDeriver.Derived] && !codec.isInstanceOf[ParamCodecDeriver.Customized]

  private def remapError(error: SchemaError, fieldKey: String): SchemaError =
    if (fieldKey == topLevelKey) error
    else {
      def remapRoot(optic: DynamicOptic): DynamicOptic =
        optic.nodes.headOption match {
          case Some(DynamicOptic.Node.Field(name)) if isTopLevelKey(name) =>
            new DynamicOptic(optic.nodes.updated(0, DynamicOptic.Node.Field(fieldKey)))
          case _ => optic
        }

      def remapSingle(single: SchemaError.Single): SchemaError.Single = single match {
        case e: SchemaError.ConversionFailed =>
          e.copy(source = remapRoot(e.source), cause = e.cause.map(remapError(_, fieldKey)))
        case e: SchemaError.MissingField =>
          e.copy(
            source = remapRoot(e.source),
            fieldName = if (isTopLevelKey(e.fieldName)) fieldKey else e.fieldName
          )
        case e: SchemaError.DuplicatedField =>
          e.copy(
            source = remapRoot(e.source),
            fieldName = if (isTopLevelKey(e.fieldName)) fieldKey else e.fieldName
          )
        case e: SchemaError.ExpectationMismatch => e.copy(source = remapRoot(e.source))
        case e: SchemaError.UnknownCase         => e.copy(source = remapRoot(e.source))
        case e: SchemaError.Message             => e.copy(source = remapRoot(e.source))
        case e: SchemaError.MigrationError      => e.copy(source = remapRoot(e.source))
      }

      val remapped = error.errors.toList.map(remapSingle)
      new SchemaError(new ::(remapped.head, remapped.tail))
    }

  private def buildTopLevelCodec[A](fieldCodec: FieldCodec, customized: Boolean): TC[A] = {
    def encode(value: A, output: Out): Unit = {
      val encodedValues = fieldCodec.encodeAny(value)
      var index         = 0
      while (index < encodedValues.length) {
        writeField(output, topLevelKey, encodedValues(index))
        index += 1
      }
    }

    def decode(input: In): Either[SchemaError, A] =
      fieldCodec
        .decodeAny(topLevelKey, readField(input, topLevelKey), ErrorFactory)
        .asInstanceOf[Either[SchemaError, A]]

    if (customized) makeCustomizedCodec[A](encode, decode)
    else makeCodec[A](encode, decode)
  }
}
