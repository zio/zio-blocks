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

package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder, ChunkMap, NonEmptyChunk}
import zio.blocks.docs.Doc
import zio.blocks.schema.json._
import zio.blocks.schema.json.JsonCodec._
import zio.blocks.schema.binding.{Binding, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema._
import zio.blocks.schema.binding.{Constructor, Discriminator}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.SeqDeconstructor.SpecializedIndexed
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.TypeId
import scala.annotation.{switch, tailrec}
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
 * An object that represents the JSON format used for serialization and
 * deserialization.
 *
 * It extends the `BinaryFormat` class, specifying "application/json" as the
 * MIME type and utilizing the `JsonCodecDeriver` for deriving the necessary
 * codecs.
 */
object JsonFormat extends BinaryFormat("application/json", JsonCodecDeriver)

/**
 * Provides a default implementation of `JsonCodecDeriver` with customizable
 * settings for JSON and binary codec derivation. This object allows the
 * derivation of codecs for various data types, including primitives, records,
 * variants, sequences, maps, and dynamic values.
 *
 * The derivation process can be customized through pre-configured parameters,
 * including:
 *   - `fieldNameMapper`: Controls how field names are transformed during
 *     serialization and deserialization.
 *   - `caseNameMapper`: Controls how case names in variants are transformed.
 *   - `discriminatorKind`: Determines the strategy for handling type
 *     discriminators in variants.
 *   - `rejectExtraFields`: Specifies if unrecognized fields should cause
 *     validation errors.
 *   - `enumValuesAsStrings`: Specifies whether enumeration values are
 *     represented as strings.
 *   - `transientNone`: Excludes fields with a value of `None` (for `Option`) or
 *     absent (for `Maybe`) during serialization.
 *   - `requireOptionFields`: Enforces the inclusion of optional fields
 *     (`Option` and `Maybe`) in deserialization.
 *   - `transientEmptyCollection`: Excludes empty collections during
 *     serialization.
 *   - `requireCollectionFields`: Enforces the inclusion of collection fields in
 *     deserialization.
 *   - `transientDefaultValue`: Excludes fields with default values during
 *     serialization.
 *   - `requireDefaultValueFields`: Enforces the inclusion of fields with
 *     default values in deserialization.
 *
 * This predefined object uses the `NameMapper.Identity` strategy, which applies
 * no transformation to field or case names, and `DiscriminatorKind.Key`, which
 * embeds type information as a key in serialized data.
 */
object JsonCodecDeriver
    extends JsonCodecDeriver(
      fieldNameMapper = NameMapper.Identity,
      caseNameMapper = NameMapper.Identity,
      discriminatorKind = DiscriminatorKind.Key,
      rejectExtraFields = false,
      enumValuesAsStrings = true,
      transientNone = true,
      requireOptionFields = false,
      transientEmptyCollection = true,
      requireCollectionFields = false,
      transientDefaultValue = true,
      requireDefaultValueFields = false
    )

class JsonCodecDeriver private[json] (
  fieldNameMapper: NameMapper,
  caseNameMapper: NameMapper,
  discriminatorKind: DiscriminatorKind,
  rejectExtraFields: Boolean,
  enumValuesAsStrings: Boolean,
  transientNone: Boolean,
  requireOptionFields: Boolean,
  transientEmptyCollection: Boolean,
  requireCollectionFields: Boolean,
  transientDefaultValue: Boolean,
  requireDefaultValueFields: Boolean
) extends Deriver[JsonCodec] {

  /**
   * Updates the `JsonCodecDeriver` instance with the specified field name
   * mapper. The field name mapper defines how field names should be transformed
   * during encoding and decoding.
   *
   * @param fieldNameMapper
   *   The `NameMapper` to apply for transforming field names.
   * @return
   *   A new instance of `JsonCodecDeriver` with the updated field name mapper.
   */
  def withFieldNameMapper(fieldNameMapper: NameMapper): JsonCodecDeriver = copy(fieldNameMapper = fieldNameMapper)

  /**
   * Updates the `JsonCodecDeriver` instance with the specified case name
   * mapper. The case name mapper defines how case names should be transformed
   * during encoding and decoding.
   *
   * @param caseNameMapper
   *   The `NameMapper` to apply for transforming case names.
   * @return
   *   A new instance of `JsonCodecDeriver` with the updated case name mapper.
   */
  def withCaseNameMapper(caseNameMapper: NameMapper): JsonCodecDeriver = copy(caseNameMapper = caseNameMapper)

  /**
   * Updates the `JsonCodecDeriver` instance with the specified discriminator
   * kind. The discriminator kind defines how the subtype discriminator is
   * represented in the serialized JSON.
   *
   * @param discriminatorKind
   *   The `DiscriminatorKind` to apply for specifying how the discriminator is
   *   handled in the JSON schema for sealed hierarchies.
   * @return
   *   A new instance of `JsonCodecDeriver` with the updated discriminator kind.
   */
  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): JsonCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  /**
   * Updates the `JsonCodecDeriver` instance to specify whether additional
   * fields in the JSON input that are not part of the schema should be rejected
   * during decoding.
   *
   * @param rejectExtraFields
   *   A boolean flag indicating whether to reject extra fields that are not
   *   defined in the schema. If `true`, decoding will fail when extra fields
   *   are encountered; if `false`, extra fields will be ignored.
   * @return
   *   A new instance of `JsonCodecDeriver` with the updated `rejectExtraFields`
   *   setting.
   */
  def withRejectExtraFields(rejectExtraFields: Boolean): JsonCodecDeriver = copy(rejectExtraFields = rejectExtraFields)

  /**
   * Updates the `JsonCodecDeriver` instance to specify whether enumeration
   * values should be serialized and deserialized as strings.
   *
   * @param enumValuesAsStrings
   *   A boolean flag indicating whether to treat enumeration values as strings.
   *   If `true`, enumeration values are serialized and deserialized as their
   *   string representations; if `false`, they are encoded with default
   *   `DiscriminatorKind.Key` encoding.
   * @return
   *   A new instance of `JsonCodecDeriver` with the updated
   *   `enumValuesAsStrings` setting.
   */
  def withEnumValuesAsStrings(enumValuesAsStrings: Boolean): JsonCodecDeriver =
    copy(enumValuesAsStrings = enumValuesAsStrings)

  /**
   * Updates the `JsonCodecDeriver` instance to specify whether fields of type
   * `Option` with a value of `None` or `Maybe` with an absent value should be
   * excluded during encoding.
   *
   * @param transientNone
   *   A boolean flag indicating whether to exclude fields of type `Option` with
   *   a value of `None` (or `Maybe` with an absent value) during encoding. If
   *   `true`, such fields are omitted; if `false`, they are included.
   * @return
   *   A new instance of `JsonCodecDeriver` with the updated `transientNone`
   *   setting.
   */
  def withTransientNone(transientNone: Boolean): JsonCodecDeriver = copy(transientNone = transientNone)

  /**
   * Sets the requirement for optional fields (`Option` and `Maybe`).
   *
   * @param requireOptionFields
   *   A boolean flag indicating whether optional fields (`Option` and `Maybe`)
   *   are required. If true, these fields must be present in the JSON input and
   *   will not be treated as optional during codec derivation.
   * @return
   *   A new instance of JsonCodecDeriver with the updated setting for requiring
   *   optional fields.
   */
  def withRequireOptionFields(requireOptionFields: Boolean): JsonCodecDeriver =
    copy(requireOptionFields = requireOptionFields)

  /**
   * Configures whether the derived codec should handle empty collections as
   * transient.
   *
   * @param transientEmptyCollection
   *   Indicates if empty collections should be treated as transient.
   * @return
   *   A new instance of JsonCodecDeriver with the updated
   *   transientEmptyCollection setting.
   */
  def withTransientEmptyCollection(transientEmptyCollection: Boolean): JsonCodecDeriver =
    copy(transientEmptyCollection = transientEmptyCollection)

  /**
   * Sets the flag indicating whether collection fields are required.
   *
   * @param requireCollectionFields
   *   A boolean value specifying if collection fields should be required.
   * @return
   *   A new instance of JsonCodecDeriver with the updated configuration.
   */
  def withRequireCollectionFields(requireCollectionFields: Boolean): JsonCodecDeriver =
    copy(requireCollectionFields = requireCollectionFields)

  /**
   * Sets the transient behavior for fields with defined default values.
   *
   * @param transientDefaultValue
   *   A boolean indicating whether the transient behavior for fields with
   *   defined default values should be applied or not.
   * @return
   *   A new instance of JsonCodecDeriver with the specified transient default
   *   value setting.
   */
  def withTransientDefaultValue(transientDefaultValue: Boolean): JsonCodecDeriver =
    copy(transientDefaultValue = transientDefaultValue)

  /**
   * Sets the flag indicating whether fields with default values are required.
   *
   * @param requireDefaultValueFields
   *   A boolean flag indicating whether fields with default values should be
   *   required.
   * @return
   *   A new instance of `JsonCodecDeriver` with the updated configuration.
   */
  def withRequireDefaultValueFields(requireDefaultValueFields: Boolean): JsonCodecDeriver =
    copy(requireDefaultValueFields = requireDefaultValueFields)

  private[this] def copy(
    fieldNameMapper: NameMapper = fieldNameMapper,
    caseNameMapper: NameMapper = caseNameMapper,
    discriminatorKind: DiscriminatorKind = discriminatorKind,
    rejectExtraFields: Boolean = rejectExtraFields,
    enumValuesAsStrings: Boolean = enumValuesAsStrings,
    transientNone: Boolean = transientNone,
    requireOptionFields: Boolean = requireOptionFields,
    transientEmptyCollection: Boolean = transientEmptyCollection,
    requireCollectionFields: Boolean = requireCollectionFields,
    transientDefaultValue: Boolean = transientDefaultValue,
    requireDefaultValueFields: Boolean = requireDefaultValueFields
  ) =
    new JsonCodecDeriver(
      fieldNameMapper,
      caseNameMapper,
      discriminatorKind,
      rejectExtraFields,
      enumValuesAsStrings,
      transientNone,
      requireOptionFields,
      transientEmptyCollection,
      requireCollectionFields,
      transientDefaultValue,
      requireDefaultValueFields
    )

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[JsonCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      Lazy(primitiveType match {
        case _: PrimitiveType.Unit.type      => unitCodec
        case _: PrimitiveType.Boolean        => booleanCodec
        case _: PrimitiveType.Byte           => byteCodec
        case _: PrimitiveType.Short          => shortCodec
        case _: PrimitiveType.Int            => intCodec
        case _: PrimitiveType.Long           => longCodec
        case _: PrimitiveType.Float          => floatCodec
        case _: PrimitiveType.Double         => doubleCodec
        case _: PrimitiveType.Char           => charCodec
        case _: PrimitiveType.String         => stringCodec
        case _: PrimitiveType.BigInt         => bigIntCodec
        case _: PrimitiveType.BigDecimal     => bigDecimalCodec
        case _: PrimitiveType.DayOfWeek      => dayOfWeekCodec
        case _: PrimitiveType.Duration       => durationCodec
        case _: PrimitiveType.Instant        => instantCodec
        case _: PrimitiveType.LocalDate      => localDateCodec
        case _: PrimitiveType.LocalDateTime  => localDateTimeCodec
        case _: PrimitiveType.LocalTime      => localTimeCodec
        case _: PrimitiveType.Month          => monthCodec
        case _: PrimitiveType.MonthDay       => monthDayCodec
        case _: PrimitiveType.OffsetDateTime => offsetDateTimeCodec
        case _: PrimitiveType.OffsetTime     => offsetTimeCodec
        case _: PrimitiveType.Period         => periodCodec
        case _: PrimitiveType.Year           => yearCodec
        case _: PrimitiveType.YearMonth      => yearMonthCodec
        case _: PrimitiveType.ZoneId         => zoneIdCodec
        case _: PrimitiveType.ZoneOffset     => zoneOffsetCodec
        case _: PrimitiveType.ZonedDateTime  => zonedDateTimeCodec
        case _: PrimitiveType.Currency       => currencyCodec
        case _: PrimitiveType.UUID           => uuidCodec
      })
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[JsonCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val len           = fields.length
      if (typeId.isTuple) Lazy {
        val fieldRegisterOffsets = new Array[RegisterOffset](len)
        val fieldTypeTags        = new Array[Int](len)
        val codecs               = new Array[JsonCodec[?]](len)
        var idx                  = 0
        while (idx < len) {
          val fieldReflect = fields(idx).value
          fieldRegisterOffsets(idx) = Reflect.registerOffset(fieldReflect)
          fieldTypeTags(idx) = Reflect.typeTag(fieldReflect)
          codecs(idx) = D.instance(fieldReflect.metadata).force
          idx += 1
        }
        new JsonCodec[A]() {
          private[this] val deconstructor   = recordBinding.deconstructor
          private[this] val constructor     = recordBinding.constructor
          private[this] val fieldCodecs     = codecs
          private[this] val usedRegisters   = constructor.usedRegisters
          private[this] val registerOffsets = fieldRegisterOffsets
          private[this] val typeTags        = fieldTypeTags

          override def decodeValue(in: JsonReader): A = {
            in.nextTokenOrError('[')
            val baseOffset = in.push(usedRegisters)
            try {
              val regs = in.registers
              if (!in.isNextToken(']')) {
                in.rollbackToken()
                var offset = baseOffset
                val len    = fieldCodecs.length
                var idx    = 0
                while ({
                  val codec = fieldCodecs(idx)
                  try {
                    (typeTags(idx): @switch) match {
                      case 0 =>
                        regs.setObject(offset, codec.asInstanceOf[JsonCodec[AnyRef]].decodeValue(in))
                      case 1 =>
                        val value =
                          if (codec eq intCodec) in.readInt()
                          else codec.asInstanceOf[JsonCodec[Int]].decodeValue(in)
                        regs.setInt(offset, value)
                      case 2 =>
                        val value =
                          if (codec eq longCodec) in.readLong()
                          else codec.asInstanceOf[JsonCodec[Long]].decodeValue(in)
                        regs.setLong(offset, value)
                      case 3 =>
                        val value =
                          if (codec eq floatCodec) in.readFloat()
                          else codec.asInstanceOf[JsonCodec[Float]].decodeValue(in)
                        regs.setFloat(offset, value)
                      case 4 =>
                        val value =
                          if (codec eq doubleCodec) in.readDouble()
                          else codec.asInstanceOf[JsonCodec[Double]].decodeValue(in)
                        regs.setDouble(offset, value)
                      case 5 =>
                        val value =
                          if (codec eq booleanCodec) in.readBoolean()
                          else codec.asInstanceOf[JsonCodec[Boolean]].decodeValue(in)
                        regs.setBoolean(offset, value)
                      case 6 =>
                        val value =
                          if (codec eq byteCodec) in.readByte()
                          else codec.asInstanceOf[JsonCodec[Byte]].decodeValue(in)
                        regs.setByte(offset, value)
                      case 7 =>
                        val value =
                          if (codec eq charCodec) in.readChar()
                          else codec.asInstanceOf[JsonCodec[Char]].decodeValue(in)
                        regs.setChar(offset, value)
                      case 8 =>
                        val value =
                          if (codec eq shortCodec) in.readShort()
                          else codec.asInstanceOf[JsonCodec[Short]].decodeValue(in)
                        regs.setShort(offset, value)
                      case _ => codec.asInstanceOf[JsonCodec[Unit]].decodeValue(in)
                    }
                  } catch {
                    case err if NonFatal(err) => error(new DynamicOptic.Node.Field(fields(idx).name), err)
                  }
                  offset += registerOffsets(idx)
                  idx += 1
                  idx < len && (in.isNextToken(',') || error("expected ','"))
                }) ()
                if (!in.isNextToken(']')) error("expected ']'")
              }
              constructor.construct(regs, baseOffset)
            } finally in.pop(usedRegisters)
          }

          override def encodeValue(x: A, out: JsonWriter): Unit = {
            out.writeArrayStart()
            var offset = out.push(usedRegisters)
            try {
              val regs = out.registers
              deconstructor.deconstruct(regs, offset, x)
              val len = fieldCodecs.length
              var idx = 0
              while (idx < len) {
                val codec = fieldCodecs(idx)
                (typeTags(idx): @switch) match {
                  case 0 =>
                    codec.asInstanceOf[JsonCodec[AnyRef]].encodeValue(regs.getObject(offset), out)
                  case 1 =>
                    val value = regs.getInt(offset)
                    if (codec eq intCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonCodec[Int]].encodeValue(value, out)
                  case 2 =>
                    val value = regs.getLong(offset)
                    if (codec eq longCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonCodec[Long]].encodeValue(value, out)
                  case 3 =>
                    val value = regs.getFloat(offset)
                    if (codec eq floatCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonCodec[Float]].encodeValue(value, out)
                  case 4 =>
                    val value = regs.getDouble(offset)
                    if (codec eq doubleCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonCodec[Double]].encodeValue(value, out)
                  case 5 =>
                    val value = regs.getBoolean(offset)
                    if (codec eq booleanCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonCodec[Boolean]].encodeValue(value, out)
                  case 6 =>
                    val value = regs.getByte(offset)
                    if (codec eq byteCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonCodec[Byte]].encodeValue(value, out)
                  case 7 =>
                    val value = regs.getChar(offset)
                    if (codec eq charCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonCodec[Char]].encodeValue(value, out)
                  case 8 =>
                    val value = regs.getShort(offset)
                    if (codec eq shortCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonCodec[Short]].encodeValue(value, out)
                  case _ => codec.asInstanceOf[JsonCodec[Unit]].encodeValue((), out)
                }
                offset += registerOffsets(idx)
                idx += 1
              }
            } finally out.pop(usedRegisters)
            out.writeArrayEnd()
          }

          override def decodeValue(json: Json): A = {
            json match {
              case a: Json.Array =>
                val len    = fieldCodecs.length
                val values = a.value
                if (values.length == len) {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  var idx    = 0
                  while ({
                    val codec = fieldCodecs(idx)
                    val value = values(idx)
                    try {
                      (typeTags(idx): @switch) match {
                        case 0 => regs.setObject(offset, codec.asInstanceOf[JsonCodec[AnyRef]].decodeValue(value))
                        case 1 => regs.setInt(offset, codec.asInstanceOf[JsonCodec[Int]].decodeValue(value))
                        case 2 => regs.setLong(offset, codec.asInstanceOf[JsonCodec[Long]].decodeValue(value))
                        case 3 => regs.setFloat(offset, codec.asInstanceOf[JsonCodec[Float]].decodeValue(value))
                        case 4 => regs.setDouble(offset, codec.asInstanceOf[JsonCodec[Double]].decodeValue(value))
                        case 5 => regs.setBoolean(offset, codec.asInstanceOf[JsonCodec[Boolean]].decodeValue(value))
                        case 6 => regs.setByte(offset, codec.asInstanceOf[JsonCodec[Byte]].decodeValue(value))
                        case 7 => regs.setChar(offset, codec.asInstanceOf[JsonCodec[Char]].decodeValue(value))
                        case 8 => regs.setShort(offset, codec.asInstanceOf[JsonCodec[Short]].decodeValue(value))
                        case _ => codec.asInstanceOf[JsonCodec[Unit]].decodeValue(value)
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.Field(fields(idx).name), err)
                    }
                    offset += registerOffsets(idx)
                    idx += 1
                    idx < len
                  }) ()
                  return constructor.construct(regs, 0L)
                }
              case _ =>
            }
            error("expected Json.Array")
          }

          override def encodeValue(x: A): Json = {
            val len    = fieldCodecs.length
            val elems  = new Array[Json](len)
            val regs   = Registers(usedRegisters)
            var offset = 0L
            deconstructor.deconstruct(regs, offset, x)
            var idx = 0
            while (idx < len) {
              val codec = fieldCodecs(idx)
              elems(idx) = (typeTags(idx): @switch) match {
                case 0 => codec.asInstanceOf[JsonCodec[AnyRef]].encodeValue(regs.getObject(offset))
                case 1 => codec.asInstanceOf[JsonCodec[Int]].encodeValue(regs.getInt(offset))
                case 2 => codec.asInstanceOf[JsonCodec[Long]].encodeValue(regs.getLong(offset))
                case 3 => codec.asInstanceOf[JsonCodec[Float]].encodeValue(regs.getFloat(offset))
                case 4 => codec.asInstanceOf[JsonCodec[Double]].encodeValue(regs.getDouble(offset))
                case 5 => codec.asInstanceOf[JsonCodec[Boolean]].encodeValue(regs.getBoolean(offset))
                case 6 => codec.asInstanceOf[JsonCodec[Byte]].encodeValue(regs.getByte(offset))
                case 7 => codec.asInstanceOf[JsonCodec[Char]].encodeValue(regs.getChar(offset))
                case 8 => codec.asInstanceOf[JsonCodec[Short]].encodeValue(regs.getShort(offset))
                case _ => codec.asInstanceOf[JsonCodec[Unit]].encodeValue(())
              }
              offset += registerOffsets(idx)
              idx += 1
            }
            new Json.Array(Chunk.fromArray(elems))
          }

          override lazy val toJsonSchema: JsonSchema = {
            val items = NonNegativeInt(fieldCodecs.length)
            new JsonSchema.Object(
              title = new Some(typeId.toString),
              `type` = new Some(new SchemaType.Single(JsonSchemaType.Array)),
              prefixItems = NonEmptyChunk.fromChunk(Chunk.from(fieldCodecs.map(_.toJsonSchema))),
              items = new Some(JsonSchema.False),
              minItems = items,
              maxItems = items
            )
          }
        }
      }
      else
        Lazy {
          val isRecursive = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
          var infos       =
            if (isRecursive) recursiveRecordCache.get.get(typeId)
            else null
          val deriveCodecs = infos eq null
          if (deriveCodecs) {
            infos = new Array[FieldInfo](len)
            var offset = 0L
            var idx    = 0
            while (idx < len) {
              val field                      = fields(idx)
              val fieldReflect               = field.value
              val defaultValue               = getDefaultValue(fieldReflect)
              val emptyCollectionConstructor =
                (if (requireCollectionFields) null
                 else if (fieldReflect.isSequence) {
                   val seq                         = fieldReflect.asSequenceUnknown.get.sequence
                   implicit val ct: ClassTag[Elem] = seq.elemClassTag.asInstanceOf[ClassTag[Elem]]
                   val constructor                 =
                     (if (seq.seqBinding.isInstanceOf[Binding[?, ?]]) seq.seqBinding
                      else seq.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding)
                       .asInstanceOf[Binding.Seq[Col, Elem]]
                       .constructor
                   () => constructor.empty
                 } else if (fieldReflect.isMap) {
                   val map         = fieldReflect.asMapUnknown.get.map
                   val constructor =
                     (if (map.mapBinding.isInstanceOf[Binding[?, ?]]) map.mapBinding
                      else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding)
                       .asInstanceOf[Binding.Map[Map, Key, Value]]
                       .constructor
                   () => constructor.emptyObject
                 } else null).asInstanceOf[() => AnyRef]
              infos(idx) = new FieldInfo(
                span = new DynamicOptic.Node.Field(field.name),
                defaultValue = defaultValue,
                emptyCollectionConstructor = emptyCollectionConstructor,
                offset = offset,
                typeTag = Reflect.typeTag(fieldReflect),
                idx = idx,
                isOptional = !requireOptionFields && (fieldReflect.isOption || fieldReflect.isMaybe),
                usesNullSentinel = fieldReflect.isMaybe
              )
              offset = RegisterOffset.add(Reflect.registerOffset(fieldReflect), offset)
              idx += 1
            }
            if (isRecursive) recursiveRecordCache.get.put(typeId, infos)
            discriminatorFields.set(null :: discriminatorFields.get)
          }
          val map = new StringMap[FieldInfo](len)
          var idx = 0
          while (idx < len) {
            val field     = fields(idx)
            val fieldInfo = infos(idx)
            if (deriveCodecs) fieldInfo.setCodec(D.instance(field.value.metadata).force)
            var name: String = null
            field.modifiers.foreach {
              case m: Modifier.rename    => if (name eq null) name = m.name
              case m: Modifier.alias     => map.put(m.name, fieldInfo)
              case _: Modifier.transient => fieldInfo.nonTransient = false
              case _                     =>
            }
            if (name eq null) name = fieldNameMapper(field.name)
            map.put(name, fieldInfo)
            fieldInfo.setName(name)
            idx += 1
          }
          if (deriveCodecs) discriminatorFields.set(discriminatorFields.get.tail)
          new JsonCodec[A]() {
            private[this] val deconstructor       = recordBinding.deconstructor
            private[this] val constructor         = recordBinding.constructor
            private[this] val fieldInfos          = infos
            private[this] val fieldIndexMap       = map
            private[this] val discriminatorField  = discriminatorFields.get.headOption.orNull
            private[this] val usedRegisters       = constructor.usedRegisters
            private[this] val skipNone            = transientNone
            private[this] val skipEmptyCollection = transientEmptyCollection
            private[this] val skipDefaultValue    = transientDefaultValue
            private[this] val doReject            = rejectExtraFields

            require(fieldInfos.length <= 128, "expected up to 128 fields")

            override def decodeValue(in: JsonReader): A = {
              in.nextTokenOrError('{')
              val baseOffset = in.push(usedRegisters)
              try {
                val len                  = fieldInfos.length
                var fieldInfo: FieldInfo = null
                var missing1, missing2   = -1L
                var idx, keyLen          = -1
                if (!in.isNextToken('}')) {
                  in.rollbackToken()
                  while (keyLen < 0 || in.isNextToken(',')) {
                    keyLen = in.readKeyAsCharBuf()
                    if (
                      len != 0 && {
                        idx += 1
                        if (idx == len) idx = 0
                        fieldInfo = fieldInfos(idx)
                        (fieldInfo.nameMatch(in, keyLen) || {
                          fieldInfo = fieldIndexMap.get(in, keyLen)
                          (fieldInfo ne null) && {
                            idx = fieldInfo.idx
                            true
                          }
                        }) && fieldInfo.nonTransient
                      }
                    ) {
                      var mask = 1L << idx
                      if (idx < 64) {
                        mask &= missing1
                        missing1 ^= mask
                      } else {
                        mask &= missing2
                        missing2 ^= mask
                      }
                      if (mask == 0L) in.duplicatedKeyError(keyLen)
                      try fieldInfo.readValue(in, baseOffset)
                      catch {
                        case err if NonFatal(err) => error(fieldInfo.span, err)
                      }
                    } else skipOrReject(in, keyLen)
                  }
                  if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
                }
                val len64 = Math.min(len, 64)
                while ({
                  idx = java.lang.Long.numberOfTrailingZeros(missing1)
                  idx < len64
                }) {
                  fieldInfos(idx).setMissingValueOrError(in, baseOffset)
                  missing1 &= missing1 - 1L
                }
                if (len > 64) {
                  while ({
                    idx = java.lang.Long.numberOfTrailingZeros(missing2) + 64
                    idx < len
                  }) {
                    fieldInfos(idx).setMissingValueOrError(in, baseOffset)
                    missing2 &= missing2 - 1L
                  }
                }
                constructor.construct(in.registers, baseOffset)
              } finally in.pop(usedRegisters)
            }

            override def encodeValue(x: A, out: JsonWriter): Unit = {
              out.writeObjectStart()
              if (discriminatorField ne null) discriminatorField.writeKeyAndValue(out)
              val baseOffset = out.push(usedRegisters)
              try {
                deconstructor.deconstruct(out.registers, baseOffset, x)
                val len = fieldInfos.length
                var idx = 0
                while (idx < len) {
                  val fieldInfo = fieldInfos(idx)
                  if (fieldInfo.nonTransient) {
                    if (skipDefaultValue && fieldInfo.hasDefault) fieldInfo.writeDefaultValue(out, baseOffset)
                    else if (skipNone && fieldInfo.isOptional) fieldInfo.writeOptional(out, baseOffset)
                    else if (skipEmptyCollection && fieldInfo.isCollection) fieldInfo.writeCollection(out, baseOffset)
                    else fieldInfo.writeRequired(out, baseOffset)
                  }
                  idx += 1
                }
              } finally out.pop(usedRegisters)
              out.writeObjectEnd()
            }

            override def decodeValue(json: Json): A = json match {
              case o: Json.Object =>
                val it                   = o.value.iterator
                val regs                 = Registers(usedRegisters)
                val len                  = fieldInfos.length
                var fieldInfo: FieldInfo = null
                var missing1, missing2   = -1L
                var idx                  = -1
                while (it.hasNext) {
                  val kv  = it.next()
                  val key = kv._1
                  if (
                    len != 0 && {
                      idx += 1
                      if (idx == len) idx = 0
                      fieldInfo = fieldInfos(idx)
                      (fieldInfo.getName == key || {
                        fieldInfo = fieldIndexMap.get(key)
                        (fieldInfo ne null) && {
                          idx = fieldInfo.idx
                          true
                        }
                      }) && fieldInfo.nonTransient
                    }
                  ) {
                    var mask = 1L << idx
                    if (idx < 64) {
                      mask &= missing1
                      missing1 ^= mask
                    } else {
                      mask &= missing2
                      missing2 ^= mask
                    }
                    if (mask == 0L) error(s"duplicated field \"$key\"")
                    try fieldInfo.readValue(regs, kv._2)
                    catch {
                      case err if NonFatal(err) => error(fieldInfo.span, err)
                    }
                  } else skipOrReject(key)
                }
                val len64 = Math.min(len, 64)
                while ({
                  idx = java.lang.Long.numberOfTrailingZeros(missing1)
                  idx < len64
                }) {
                  fieldInfos(idx).setMissingValueOrError(regs)
                  missing1 &= missing1 - 1L
                }
                if (len > 64) {
                  while ({
                    idx = java.lang.Long.numberOfTrailingZeros(missing2) + 64
                    idx < len
                  }) {
                    fieldInfos(idx).setMissingValueOrError(regs)
                    missing2 &= missing2 - 1L
                  }
                }
                constructor.construct(regs, 0)
              case _ => error("expected Json.Object")
            }

            override def encodeValue(x: A): Json = {
              val len     = fieldInfos.length
              val builder = ChunkBuilder.make[(String, Json)](len + {
                if (discriminatorField ne null) 1
                else 0
              })
              if (discriminatorField ne null) discriminatorField.writeKeyAndValue(builder)
              val regs = Registers(usedRegisters)
              deconstructor.deconstruct(regs, 0, x)
              var idx = 0
              while (idx < len) {
                val fieldInfo = fieldInfos(idx)
                if (fieldInfo.nonTransient) {
                  if (skipDefaultValue && fieldInfo.hasDefault) fieldInfo.writeDefaultValue(regs, builder)
                  else if (skipNone && fieldInfo.isOptional) fieldInfo.writeOptional(regs, builder)
                  else if (skipEmptyCollection && fieldInfo.isCollection) fieldInfo.writeCollection(regs, builder)
                  else fieldInfo.writeRequired(regs, builder)
                }
                idx += 1
              }
              new Json.Object(builder.result())
            }

            private[this] def skipOrReject(in: JsonReader, keyLen: Int): Unit =
              if (doReject && ((discriminatorField eq null) || !discriminatorField.nameMatch(in, keyLen))) {
                in.unexpectedKeyError(keyLen)
              } else in.skip()

            private[this] def skipOrReject(key: String): Unit =
              if (doReject && ((discriminatorField eq null) || discriminatorField.getName != key)) {
                error(s"unexpected field \"$key\"")
              }

            override lazy val toJsonSchema: JsonSchema = {
              val len              = fieldInfos.length
              val properties       = new ChunkMap.ChunkMapBuilder[String, JsonSchema](len)
              val dependentSchemas = new ChunkMap.ChunkMapBuilder[String, JsonSchema]
              val allOf            = ChunkBuilder.make[JsonSchema]()
              val reqs             = Set.newBuilder[String]
              var idx              = 0
              while (idx < len) {
                val fieldInfo = fieldInfos(idx)
                val field     = fields(idx)
                if (
                  fieldInfo.nonTransient && {
                    val schema = fieldInfo.getCodec.toJsonSchema
                    val name   = fieldInfo.getName
                    properties.add(name, schema)
                    val isRequired =
                      !(fieldInfo.hasDefault || fieldInfo.isOptional || fieldInfo.isCollection)
                    var nameWithAliases = Chunk.empty[String]
                    field.modifiers.foreach {
                      case m: Modifier.alias =>
                        val alias = m.name
                        properties.add(alias, schema)
                        nameWithAliases = nameWithAliases :+ alias
                      case _ =>
                    }
                    if (nameWithAliases.nonEmpty) {
                      nameWithAliases = name +: nameWithAliases
                      if (isRequired) {
                        allOf.addOne(
                          new JsonSchema.Object(
                            oneOf = NonEmptyChunk.fromChunk(nameWithAliases.map { nameOrAlias =>
                              toObjectNot(nameWithAliases, nameOrAlias, true)
                            })
                          )
                        )
                      } else {
                        nameWithAliases.foreach { nameOrAlias =>
                          dependentSchemas.add(nameOrAlias, toObjectNot(nameWithAliases, nameOrAlias, false))
                        }
                      }
                      false
                    } else isRequired
                  }
                ) reqs.addOne(fieldInfo.getName)
                idx += 1
              }
              val required = reqs.result()
              JsonSchema.obj(
                title = new Some(typeId.toString),
                properties = new Some(properties.result()),
                required = if (required.nonEmpty) new Some(required) else None,
                additionalProperties = if (doReject) new Some(JsonSchema.False) else None,
                allOf = NonEmptyChunk.fromChunk(allOf.result()),
                dependentSchemas = if (dependentSchemas.knownSize > 0) Some(dependentSchemas.result()) else None
              )
            }

            private[this] def toObjectNot(
              nameWithAliases: Chunk[String],
              nameOrAlias: String,
              isRequired: Boolean
            ): JsonSchema.Object = new JsonSchema.Object(
              required = if (isRequired) new Some(Set(nameOrAlias)) else None,
              not = new Some({
                val required = nameWithAliases.filter(_ != nameOrAlias)
                if (required.length == 1) new JsonSchema.Object(required = new Some(required.toSet))
                else {
                  new JsonSchema.Object(anyOf = NonEmptyChunk.fromChunk(required.map { n =>
                    new JsonSchema.Object(required = new Some(Set(n)))
                  }))
                }
              })
            )
          }
        }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[JsonCodec[A]]]

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val isOption = typeId.isOption
      if (isOption || typeId.isMaybe) {
        D.instance(cases(1).value.asRecord.get.fields(0).value.metadata).map { codec =>
          if (isOption) {
            new JsonCodec[Option[Any]]() {
              private[this] val valueCodec = codec.asInstanceOf[JsonCodec[Any]]

              override def decodeValue(in: JsonReader): Option[Any] = {
                val isNull = in.isNextToken('n')
                in.rollbackToken()
                try {
                  if (isNull) in.readNullOrError(None, "expected null")
                  else new Some(valueCodec.decodeValue(in))
                } catch {
                  case err if NonFatal(err) => decodeError(err, isNull)
                }
              }

              override def encodeValue(x: Option[Any], out: JsonWriter): Unit =
                if (x eq None) out.writeNull()
                else valueCodec.encodeValue(x.get, out)

              override def decodeValue(json: Json): Option[Any] =
                if (json eq Json.Null) None
                else {
                  try new Some(valueCodec.decodeValue(json))
                  catch {
                    case err if NonFatal(err) => decodeError(err, false)
                  }
                }

              override def encodeValue(x: Option[Any]): Json =
                if (x eq None) Json.Null
                else valueCodec.encodeValue(x.get)

              private[this] def decodeError(err: Throwable, isNull: Boolean): Nothing =
                if (isNull) error(new DynamicOptic.Node.Case("None"), err)
                else error(new DynamicOptic.Node.Case("Some"), new DynamicOptic.Node.Field("value"), err)

              override lazy val toJsonSchema: JsonSchema = valueCodec.toJsonSchema.withNullable
            }
          } else {
            new JsonCodec[AnyRef]() {
              private[this] val valueCodec  = codec.asInstanceOf[JsonCodec[AnyRef]]
              private[this] val nullDefault = new AnyRef

              override def decodeValue(in: JsonReader): AnyRef = {
                val isNull = in.isNextToken('n')
                in.rollbackToken()
                try {
                  if (isNull) {
                    in.readNullOrError(nullDefault, "expected null")
                    null
                  } else valueCodec.decodeValue(in)
                } catch {
                  case err if NonFatal(err) => decodeError(err, isNull)
                }
              }

              override def encodeValue(x: AnyRef, out: JsonWriter): Unit =
                if (x eq null) out.writeNull()
                else valueCodec.encodeValue(x, out)

              override def decodeValue(json: Json): AnyRef =
                if (json eq Json.Null) null
                else {
                  try valueCodec.decodeValue(json)
                  catch {
                    case err if NonFatal(err) => decodeError(err, false)
                  }
                }

              override def encodeValue(x: AnyRef): Json =
                if (x eq null) Json.Null
                else valueCodec.encodeValue(x)

              private[this] def decodeError(err: Throwable, isNull: Boolean): Nothing =
                if (isNull) error(new DynamicOptic.Node.Case("Absent"), err)
                else error(new DynamicOptic.Node.Case("Present"), new DynamicOptic.Node.Field("value"), err)

              override lazy val toJsonSchema: JsonSchema = valueCodec.toJsonSchema.withNullable
            }
          }
        }
      } else {
        val discr = binding.asInstanceOf[Binding.Variant[A]].discriminator
        if (isEnumeration(cases)) Lazy {
          val map = new StringMap[Constructor[?]](cases.length)

          def getInfos(cases: IndexedSeq[Term[F, A, ?]]): Array[EnumInfo] = {
            val len   = cases.length
            val infos = new Array[EnumInfo](len)
            var idx   = 0
            while (idx < len) {
              val case_       = cases(idx)
              val caseReflect = case_.value
              infos(idx) = if (caseReflect.isVariant) {
                new EnumNodeInfo(
                  discriminator(caseReflect),
                  getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]].cases)
                )
              } else {
                val constructor = caseReflect.asRecord.get.recordBinding
                  .asInstanceOf[BindingInstance[TC, ?, ?]]
                  .binding
                  .asInstanceOf[Binding.Record[?]]
                  .constructor
                var name: String = null
                case_.modifiers.foreach {
                  case m: Modifier.rename => if (name eq null) name = m.name
                  case m: Modifier.alias  => map.put(m.name, constructor)
                  case _                  =>
                }
                if (name eq null) name = caseNameMapper(case_.name)
                map.put(name, constructor)
                new EnumLeafInfo(name, constructor)
              }
              idx += 1
            }
            infos
          }

          val enumInfos = getInfos(cases)

          new JsonCodec[A]() {
            private[this] val root           = new EnumNodeInfo(discr, enumInfos)
            private[this] val constructorMap = map

            def decodeValue(in: JsonReader): A = {
              val valueLen    = in.readStringAsCharBuf()
              val constructor = constructorMap.get(in, valueLen)
              if (constructor ne null) constructor.construct(null, 0).asInstanceOf[A]
              else in.enumValueError(valueLen)
            }

            def encodeValue(x: A, out: JsonWriter): Unit = root.discriminate(x).writeVal(out)

            override def decodeValue(json: Json): A = json match {
              case s: Json.String =>
                val enumName    = s.value
                val constructor = constructorMap.get(enumName)
                if (constructor ne null) constructor.construct(null, 0).asInstanceOf[A]
                else error(s"illegal enum value \"$enumName\"")
              case _ => error("expected Json.String")
            }

            override def encodeValue(x: A): Json = new Json.String(root.discriminate(x).enumName)

            override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
              title = new Some(typeId.toString),
              `enum` = NonEmptyChunk.fromChunk(map.keys.map(new Json.String(_)))
            )
          }
        }
        else {
          discriminatorKind match {
            case DiscriminatorKind.Field(fieldName) if hasOnlyRecordAndVariantCases(cases) =>
              Lazy {
                val map = new StringMap[CaseLeafInfo](cases.length)

                def getInfos(
                  cases: IndexedSeq[Term[F, A, ?]],
                  spans: List[DynamicOptic.Node.Case]
                ): Array[CaseInfo] = {
                  val len   = cases.length
                  val infos = new Array[CaseInfo](len)
                  var idx   = 0
                  while (idx < len) {
                    val case_       = cases(idx)
                    val caseReflect = case_.value
                    val span        = new DynamicOptic.Node.Case(case_.name)
                    infos(idx) = if (caseReflect.isVariant) {
                      val caseVariant = caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]
                      new CaseNodeInfo(discriminator(caseReflect), getInfos(caseVariant.cases, span :: spans))
                    } else {
                      val caseLeafInfo = new CaseLeafInfo(null, span :: spans)
                      val aliases      = mutable.ArrayBuilder.make[String]
                      var name: String = null
                      case_.modifiers.foreach {
                        case m: Modifier.rename => if (name eq null) name = m.name
                        case m: Modifier.alias  =>
                          val alias = m.name
                          map.put(alias, caseLeafInfo)
                          aliases.addOne(alias)
                        case _ =>
                      }
                      if (name eq null) name = caseNameMapper(case_.name)
                      caseLeafInfo.setName(name)
                      caseLeafInfo.setAliases(aliases.result())
                      map.put(name, caseLeafInfo)
                      discriminatorFields.set(new DiscriminatorFieldInfo(fieldName, name) :: discriminatorFields.get)
                      caseLeafInfo.codec = D.instance(caseReflect.metadata).force
                      discriminatorFields.set(discriminatorFields.get.tail)
                      caseLeafInfo
                    }
                    idx += 1
                  }
                  infos
                }

                new JsonCodec[A]() {
                  private[this] val root                   = new CaseNodeInfo(discr, getInfos(cases, Nil))
                  private[this] val caseMap                = map
                  private[this] val discriminatorFieldName = fieldName

                  def decodeValue(in: JsonReader): A = {
                    in.setMark()
                    in.nextTokenOrError('{')
                    if (in.skipToKey(discriminatorFieldName)) {
                      val caseInfo = caseMap.get(in, in.readStringAsCharBuf())
                      if (caseInfo ne null) {
                        in.rollbackToMark()
                        try caseInfo.codec.asInstanceOf[JsonCodec[A]].decodeValue(in)
                        catch {
                          case err if NonFatal(err) => error(caseInfo.spans, err)
                        }
                      } else in.discriminatorValueError(discriminatorFieldName)
                    } else in.requiredFieldError(discriminatorFieldName)
                  }

                  def encodeValue(x: A, out: JsonWriter): Unit =
                    root.discriminate(x).codec.asInstanceOf[JsonCodec[A]].encodeValue(x, out)

                  override def decodeValue(json: Json): A = json match {
                    case o: Json.Object =>
                      val kvs = o.value
                      val len = kvs.length
                      var idx = 0
                      while (idx < len) {
                        val kv = kvs(idx)
                        if (kv._1 == discriminatorFieldName) {
                          kv._2 match {
                            case s: Json.String =>
                              val caseInfo = caseMap.get(s.value)
                              if (caseInfo ne null) {
                                try return caseInfo.codec.asInstanceOf[JsonCodec[A]].decodeValue(json)
                                catch {
                                  case err if NonFatal(err) => error(caseInfo.spans, err)
                                }
                              }
                            case _ =>
                          }
                          error(s"illegal value of discriminator field \"$discriminatorFieldName\"")
                        }
                        idx += 1
                      }
                      error(s"missing required field \"$discriminatorFieldName\"")
                    case _ => error("expected Json.Object")
                  }

                  override def encodeValue(x: A): Json =
                    root.discriminate(x).codec.asInstanceOf[JsonCodec[A]].encodeValue(x)

                  override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                    title = new Some(typeId.toString),
                    oneOf = NonEmptyChunk.fromChunk(
                      collectCaseSchemas(root.caseInfos, ChunkBuilder.make[JsonSchema]()).result()
                    )
                  )

                  private[this] def collectCaseSchemas(
                    infos: Array[CaseInfo],
                    acc: ChunkBuilder[JsonSchema]
                  ): ChunkBuilder[JsonSchema] = {
                    val len = infos.length
                    var idx = 0
                    while (idx < len) {
                      infos(idx) match {
                        case leaf: CaseLeafInfo =>
                          val schema = leaf.codec.toJsonSchema
                          (leaf.getName +: leaf.getAliases).foreach { nameOrAlias =>
                            acc.addOne(schema.withDiscriminatorField(discriminatorFieldName, nameOrAlias))
                          }
                        case node: CaseNodeInfo[?] => collectCaseSchemas(node.caseInfos, acc)
                      }
                      idx += 1
                    }
                    acc
                  }
                }
              }
            case DiscriminatorKind.None =>
              Lazy {
                val codecs = mutable.ArrayBuilder.make[JsonCodec[?]]

                def getInfos(cases: IndexedSeq[Term[F, A, ?]]): Array[CaseInfo] = {
                  val len   = cases.length
                  val infos = new Array[CaseInfo](len)
                  var idx   = 0
                  while (idx < len) {
                    val caseReflect = cases(idx).value
                    infos(idx) = if (caseReflect.isVariant) {
                      val caseVariant = caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]
                      new CaseNodeInfo(discriminator(caseReflect), getInfos(caseVariant.cases))
                    } else {
                      val codec = D.instance(caseReflect.metadata).force
                      codecs.addOne(codec)
                      new CaseLeafInfo(codec, Nil)
                    }
                    idx += 1
                  }
                  infos
                }

                new JsonCodec[A]() {
                  private[this] val root           = new CaseNodeInfo(discr, getInfos(cases))
                  private[this] val caseLeafCodecs = codecs.result()

                  def decodeValue(in: JsonReader): A = {
                    var idx = 0
                    while (idx < caseLeafCodecs.length) {
                      in.setMark()
                      try {
                        val x = caseLeafCodecs(idx).asInstanceOf[JsonCodec[A]].decodeValue(in)
                        in.resetMark()
                        return x
                      } catch {
                        case err if NonFatal(err) => in.rollbackToMark()
                      }
                      idx += 1
                    }
                    error("expected a variant value")
                  }

                  def encodeValue(x: A, out: JsonWriter): Unit =
                    root.discriminate(x).codec.asInstanceOf[JsonCodec[A]].encodeValue(x, out)

                  override def decodeValue(json: Json): A = {
                    var idx = 0
                    while (idx < caseLeafCodecs.length) {
                      try return caseLeafCodecs(idx).asInstanceOf[JsonCodec[A]].decodeValue(json)
                      catch {
                        case err if NonFatal(err) =>
                      }
                      idx += 1
                    }
                    error("expected a variant value")
                  }

                  override def encodeValue(x: A): Json =
                    root.discriminate(x).codec.asInstanceOf[JsonCodec[A]].encodeValue(x)

                  override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                    title = new Some(typeId.toString),
                    oneOf = NonEmptyChunk.fromIterableOption(caseLeafCodecs.map(_.toJsonSchema))
                  )
                }
              }
            case _ =>
              Lazy {
                val map = new StringMap[CaseLeafInfo](cases.length)

                def getInfos(
                  cases: IndexedSeq[Term[F, A, ?]],
                  spans: List[DynamicOptic.Node.Case]
                ): Array[CaseInfo] = {
                  val len   = cases.length
                  val infos = new Array[CaseInfo](len)
                  var idx   = 0
                  while (idx < len) {
                    val case_       = cases(idx)
                    val caseReflect = case_.value
                    val span_       = new DynamicOptic.Node.Case(case_.name) :: spans
                    infos(idx) = if (caseReflect.isVariant) {
                      val caseVariant = caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]
                      new CaseNodeInfo(discriminator(caseReflect), getInfos(caseVariant.cases, span_))
                    } else {
                      val caseLeafInfo = new CaseLeafInfo(D.instance(caseReflect.metadata).force, span_)
                      val aliases      = mutable.ArrayBuilder.make[String]
                      var name: String = null
                      case_.modifiers.foreach {
                        case m: Modifier.rename => if (name eq null) name = m.name
                        case m: Modifier.alias  =>
                          val alias = m.name
                          map.put(alias, caseLeafInfo)
                          aliases.addOne(alias)
                        case _ =>
                      }
                      if (name eq null) name = caseNameMapper(case_.name)
                      map.put(name, caseLeafInfo)
                      caseLeafInfo.setName(name)
                      caseLeafInfo.setAliases(aliases.result())
                      caseLeafInfo
                    }
                    idx += 1
                  }
                  infos
                }

                new JsonCodec[A]() {
                  private[this] val root    = new CaseNodeInfo(discr, getInfos(cases, Nil))
                  private[this] val caseMap = map

                  def decodeValue(in: JsonReader): A = {
                    in.nextTokenOrError('{')
                    if (!in.isNextToken('}')) {
                      in.rollbackToken()
                      val caseInfo = caseMap.get(in, in.readKeyAsCharBuf())
                      if (caseInfo ne null) {
                        val x =
                          try caseInfo.codec.asInstanceOf[JsonCodec[A]].decodeValue(in)
                          catch {
                            case err if NonFatal(err) => error(caseInfo.spans, err)
                          }
                        if (!in.isNextToken('}')) in.objectEndOrCommaError()
                        return x
                      }
                    }
                    in.discriminatorError()
                  }

                  def encodeValue(x: A, out: JsonWriter): Unit = {
                    out.writeObjectStart()
                    val caseInfo = root.discriminate(x)
                    caseInfo.writeKey(out)
                    caseInfo.codec.asInstanceOf[JsonCodec[A]].encodeValue(x, out)
                    out.writeObjectEnd()
                  }

                  override def decodeValue(json: Json): A = json match {
                    case o: Json.Object =>
                      val kvs = o.value
                      if (kvs.nonEmpty) {
                        val kv       = kvs(0)
                        val caseInfo = caseMap.get(kv._1)
                        if (caseInfo ne null) {
                          try return caseInfo.codec.asInstanceOf[JsonCodec[A]].decodeValue(kv._2)
                          catch {
                            case err if NonFatal(err) => error(caseInfo.spans, err)
                          }
                        }
                      }
                      error("illegal discriminator")
                    case _ => error("expected Json.Object")
                  }

                  override def encodeValue(x: A): Json = {
                    val caseInfo = root.discriminate(x)
                    new Json.Object(
                      Chunk.single((caseInfo.getName, caseInfo.codec.asInstanceOf[JsonCodec[A]].encodeValue(x)))
                    )
                  }

                  override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                    title = new Some(typeId.toString),
                    oneOf = NonEmptyChunk.fromChunk(
                      collectCaseSchemas(root.caseInfos, ChunkBuilder.make[JsonSchema]()).result()
                    )
                  )

                  private[this] def collectCaseSchemas(
                    infos: Array[CaseInfo],
                    acc: ChunkBuilder[JsonSchema]
                  ): ChunkBuilder[JsonSchema] = {
                    val len = infos.length
                    var idx = 0
                    while (idx < len) {
                      infos(idx) match {
                        case leaf: CaseLeafInfo =>
                          val name   = leaf.getName
                          val schema = leaf.codec.toJsonSchema
                          (name +: leaf.getAliases).foreach { nameOrAlias =>
                            acc.addOne(
                              JsonSchema.obj(
                                properties = new Some(ChunkMap((nameOrAlias, schema))),
                                required = new Some(Set(nameOrAlias)),
                                additionalProperties = new Some(JsonSchema.False)
                              )
                            )
                          }
                        case node: CaseNodeInfo[?] => collectCaseSchemas(node.caseInfos, acc)
                      }
                      idx += 1
                    }
                    acc
                  }
                }
              }
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[JsonCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { codec =>
        Reflect.typeTag(element) match {
          case 1 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq intCodec) {
              new JsonCodec[Col[Int]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader): Col[Int] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Int]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.addInt(builder, in.readInt())
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Int], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    out.writeVal(deconstructor.intAt(x, idx))
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Int] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Int](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, intCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Int](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Int]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Int]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = Json.Number(deconstructor.intAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(intCodec.toJsonSchema)
                )
              }
            } else {
              new JsonCodec[Col[Int]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Int]]

                def decodeValue(in: JsonReader): Col[Int] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Int]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Int], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.intAt(x, idx), out)
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Int] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Int](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Int](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Int]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Int]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = elementCodec.encodeValue(deconstructor.intAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(elementCodec.toJsonSchema)
                )
              }
            }
          case 2 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq longCodec) {
              new JsonCodec[Col[Long]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader): Col[Long] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Long]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.addLong(builder, in.readLong())
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Long], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    out.writeVal(deconstructor.longAt(x, idx))
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Long] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Long](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, longCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Long](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Long]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Long]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = Json.Number(deconstructor.longAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(longCodec.toJsonSchema)
                )
              }
            } else {
              new JsonCodec[Col[Long]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Long]]

                def decodeValue(in: JsonReader): Col[Long] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Long]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Long], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.longAt(x, idx), out)
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Long] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Long](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Long](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Long]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Long]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = elementCodec.encodeValue(deconstructor.longAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(elementCodec.toJsonSchema)
                )
              }
            }
          case 3 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq floatCodec) {
              new JsonCodec[Col[Float]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader): Col[Float] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Float]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.addFloat(builder, in.readFloat())
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Float], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    out.writeVal(deconstructor.floatAt(x, idx))
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Float] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Float](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, floatCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Float](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Float]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Float]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = Json.Number(deconstructor.floatAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(floatCodec.toJsonSchema)
                )
              }
            } else {
              new JsonCodec[Col[Float]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Float]]

                def decodeValue(in: JsonReader): Col[Float] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Float]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Float], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.floatAt(x, idx), out)
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Float] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Float](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Float](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Float]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Float]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = elementCodec.encodeValue(deconstructor.floatAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(elementCodec.toJsonSchema)
                )
              }
            }
          case 4 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq doubleCodec) {
              new JsonCodec[Col[Double]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader): Col[Double] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Double]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.addDouble(builder, in.readDouble())
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Double], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    out.writeVal(deconstructor.doubleAt(x, idx))
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Double] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Double](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, doubleCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Double](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Double]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Double]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = Json.Number(deconstructor.doubleAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(doubleCodec.toJsonSchema)
                )
              }
            } else {
              new JsonCodec[Col[Double]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Double]]

                def decodeValue(in: JsonReader): Col[Double] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Double]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Double], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.doubleAt(x, idx), out)
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Double] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Double](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Double](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Double]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Double]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = elementCodec.encodeValue(deconstructor.doubleAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(elementCodec.toJsonSchema)
                )
              }
            }
          case 5 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq booleanCodec) {
              new JsonCodec[Col[Boolean]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader): Col[Boolean] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Boolean]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.addBoolean(builder, in.readBoolean())
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Boolean], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    out.writeVal(deconstructor.booleanAt(x, idx))
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Boolean] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Boolean](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, booleanCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Boolean](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Boolean]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Boolean]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = Json.Boolean(deconstructor.booleanAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(booleanCodec.toJsonSchema)
                )
              }
            } else {
              new JsonCodec[Col[Boolean]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Boolean]]

                def decodeValue(in: JsonReader): Col[Boolean] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Boolean]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Boolean], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.booleanAt(x, idx), out)
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Boolean] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Boolean](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Boolean](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Boolean]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Boolean]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = elementCodec.encodeValue(deconstructor.booleanAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(elementCodec.toJsonSchema)
                )
              }
            }
          case 6 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq byteCodec) {
              new JsonCodec[Col[Byte]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader): Col[Byte] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Byte]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, in.readByte())
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Byte], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    out.writeVal(deconstructor.byteAt(x, idx))
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Byte] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Byte](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, byteCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Byte](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Byte]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Byte]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = Json.Number(deconstructor.byteAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(byteCodec.toJsonSchema)
                )
              }
            } else {
              new JsonCodec[Col[Byte]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Byte]]

                def decodeValue(in: JsonReader): Col[Byte] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Byte]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Byte], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.byteAt(x, idx), out)
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Byte] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Byte](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Byte](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Byte]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Byte]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = elementCodec.encodeValue(deconstructor.byteAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(elementCodec.toJsonSchema)
                )
              }
            }
          case 7 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq charCodec) {
              new JsonCodec[Col[Char]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader): Col[Char] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Char]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, in.readChar())
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Char], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    out.writeVal(deconstructor.charAt(x, idx))
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Char] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Char](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, charCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Char](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Char]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Char]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = new Json.String(deconstructor.charAt(x, idx).toString)
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(charCodec.toJsonSchema)
                )
              }
            } else {
              new JsonCodec[Col[Char]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Char]]

                def decodeValue(in: JsonReader): Col[Char] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Char]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Char], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.charAt(x, idx), out)
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Char] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Char](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Char](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Char]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Char]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = elementCodec.encodeValue(deconstructor.charAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(elementCodec.toJsonSchema)
                )
              }
            }
          case 8 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq shortCodec) {
              new JsonCodec[Col[Short]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader): Col[Short] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Short]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, in.readShort())
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Short], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    out.writeVal(deconstructor.shortAt(x, idx))
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Short] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Short](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, shortCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Short](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Short]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Short]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = Json.Number(deconstructor.shortAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(shortCodec.toJsonSchema)
                )
              }
            } else {
              new JsonCodec[Col[Short]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Short]]

                def decodeValue(in: JsonReader): Col[Short] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) constructor.empty
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Short]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(constructor.empty, '[')
                  }

                def encodeValue(x: Col[Short], out: JsonWriter): Unit = {
                  out.writeArrayStart()
                  val len = deconstructor.size(x)
                  var idx = 0
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.shortAt(x, idx), out)
                    idx += 1
                  }
                  out.writeArrayEnd()
                }

                override def decodeValue(json: Json): Col[Short] = json match {
                  case a: Json.Array =>
                    val elems   = a.value
                    val len     = elems.length
                    val builder = constructor.newBuilder[Short](len)
                    var idx     = 0
                    try {
                      while (idx < len) {
                        constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                        idx += 1
                      }
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    constructor.result[Short](builder)
                  case _ =>
                    if (json eq Json.Null) constructor.empty[Short]
                    else error("expected Json.Array")
                }

                override def encodeValue(x: Col[Short]): Json = {
                  val len   = deconstructor.size(x)
                  val elems = new Array[Json](len)
                  var idx   = 0
                  while (idx < len) {
                    elems(idx) = elementCodec.encodeValue(deconstructor.shortAt(x, idx))
                    idx += 1
                  }
                  new Json.Array(Chunk.fromArray(elems))
                }

                override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                  title = new Some(typeId.toString),
                  `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                  items = new Some(elementCodec.toJsonSchema)
                )
              }
            }
          case _ =>
            new JsonCodec[Col[Elem]]() {
              private[this] val deconstructor = seqBinding.deconstructor
              private[this] val constructor   = seqBinding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonCodec[Elem]]
              private[this] val elemClassTag  = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

              def decodeValue(in: JsonReader): Col[Elem] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) constructor.empty(elemClassTag)
                  else {
                    in.rollbackToken()
                    val builder = constructor.newBuilder[Elem]()(elemClassTag)
                    var idx     = -1
                    try {
                      while ({
                        idx += 1
                        constructor.add(builder, elementCodec.decodeValue(in))
                        in.isNextToken(',')
                      }) ()
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                    if (in.isCurrentToken(']')) constructor.result[Elem](builder)
                    else in.arrayEndOrCommaError()
                  }
                } else {
                  in.rollbackToken()
                  in.readNullOrTokenError(constructor.empty(elemClassTag), '[')
                }

              def encodeValue(x: Col[Elem], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val it = deconstructor.deconstruct(x)
                while (it.hasNext) elementCodec.encodeValue(it.next(), out)
                out.writeArrayEnd()
              }

              override def decodeValue(json: Json): Col[Elem] = json match {
                case a: Json.Array =>
                  val elems   = a.value
                  val len     = elems.length
                  val builder = constructor.newBuilder[Elem](len)(elemClassTag)
                  var idx     = 0
                  try {
                    while (idx < len) {
                      constructor.add(builder, elementCodec.decodeValue(elems(idx)))
                      idx += 1
                    }
                  } catch {
                    case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                  }
                  constructor.result[Elem](builder)
                case _ =>
                  if (json eq Json.Null) constructor.empty[Elem](elemClassTag)
                  else error("expected Json.Array")
              }

              override def encodeValue(x: Col[Elem]): Json = {
                val len   = deconstructor.size(x)
                val elems = new Array[Json](len)
                val it    = deconstructor.deconstruct(x)
                var idx   = 0
                while (idx < len) {
                  elems(idx) = elementCodec.encodeValue(it.next())
                  idx += 1
                }
                new Json.Array(Chunk.fromArray(elems))
              }

              override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
                title = new Some(typeId.toString),
                `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Array, JsonSchemaType.Null))),
                items = new Some(elementCodec.toJsonSchema)
              )
            }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[JsonCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
        new JsonCodec[Map[Key, Value]]() {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val keyCodec      = codec1.asInstanceOf[JsonCodec[Key]]
          private[this] val valueCodec    = codec2.asInstanceOf[JsonCodec[Value]]
          private[this] val keyRefl       = key.asInstanceOf[Reflect.Bound[Key]]

          def decodeValue(in: JsonReader): Map[Key, Value] =
            if (in.isNextToken('{')) {
              if (in.isNextToken('}')) constructor.emptyObject
              else {
                in.rollbackToken()
                val builder = constructor.newObjectBuilder[Key, Value](8)
                var idx     = -1
                while ({
                  idx += 1
                  val k =
                    try keyCodec.decodeKey(in)
                    catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                  val v =
                    try valueCodec.decodeValue(in)
                    catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtMapKey(keyRefl.toDynamicValue(k)), err)
                    }
                  constructor.addObject(builder, k, v)
                  in.isNextToken(',')
                }) ()
                if (in.isCurrentToken('}')) constructor.resultObject[Key, Value](builder)
                else in.objectEndOrCommaError()
              }
            } else {
              in.rollbackToken()
              in.readNullOrTokenError(constructor.emptyObject, '{')
            }

          def encodeValue(x: Map[Key, Value], out: JsonWriter): Unit = {
            out.writeObjectStart()
            val it = deconstructor.deconstruct(x)
            while (it.hasNext) {
              val kv = it.next()
              keyCodec.encodeKey(deconstructor.getKey(kv), out)
              valueCodec.encodeValue(deconstructor.getValue(kv), out)
            }
            out.writeObjectEnd()
          }

          override def decodeValue(json: Json): Map[Key, Value] = json match {
            case o: Json.Object =>
              val builder = constructor.newObjectBuilder[Key, Value](o.value.length)
              o.value.foreach {
                var idx = 0
                kv =>
                  val k =
                    try keyCodec.decodeKey(kv._1)
                    catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                    }
                  val v =
                    try valueCodec.decodeValue(kv._2)
                    catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.AtMapKey(keyRefl.toDynamicValue(k)), err)
                    }
                  constructor.addObject(builder, k, v)
                  idx += 1
              }
              constructor.resultObject[Key, Value](builder)
            case _ =>
              if (json eq Json.Null) constructor.emptyObject[Key, Value]
              else error("expected Json.Object")
          }

          override def encodeValue(x: Map[Key, Value]): Json = {
            val len = deconstructor.size(x)
            val kvs = new Array[(String, Json)](len)
            val it  = deconstructor.deconstruct(x)
            var idx = 0
            while (idx < len) {
              val kv = it.next()
              kvs(idx) =
                (keyCodec.encodeKey(deconstructor.getKey(kv)), valueCodec.encodeValue(deconstructor.getValue(kv)))
              idx += 1
            }
            new Json.Object(Chunk.fromArray(kvs))
          }

          override lazy val toJsonSchema: JsonSchema = new JsonSchema.Object(
            title = new Some(typeId.toString),
            `type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Object, JsonSchemaType.Null))),
            additionalProperties = new Some(valueCodec.toJsonSchema)
          )
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[JsonCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonCodec[DynamicValue]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[JsonCodec[DynamicValue]]]

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
      D.instance(wrapped.metadata).map { codec =>
        new JsonCodec[A] {
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val wrappedCodec = codec.asInstanceOf[JsonCodec[Wrapped]]

          override def decodeValue(in: JsonReader): A =
            try wrap(wrappedCodec.decodeValue(in))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeValue(x: A, out: JsonWriter): Unit =
            try wrappedCodec.encodeValue(unwrap(x), out)
            catch {
              case err if NonFatal(err) => error(err.getMessage)
            }

          override def decodeValue(json: Json): A =
            try wrap(wrappedCodec.decodeValue(json))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeValue(x: A): Json =
            try wrappedCodec.encodeValue(unwrap(x))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def decodeKey(in: JsonReader): A =
            try wrap(wrappedCodec.decodeKey(in))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeKey(x: A, out: JsonWriter): Unit =
            try wrappedCodec.encodeKey(unwrap(x), out)
            catch {
              case err if NonFatal(err) => error(err.getMessage)
            }

          override def decodeKey(s: String): A =
            try wrap(wrappedCodec.decodeKey(s))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeKey(x: A): String =
            try wrappedCodec.encodeKey(unwrap(x))
            catch {
              case err if NonFatal(err) => error(err.getMessage)
            }

          override def toJsonSchema: JsonSchema = wrappedCodec.toJsonSchema
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[JsonCodec[A]]]

  override def instanceOverrides: IndexedSeq[InstanceOverride] = {
    recursiveRecordCache.remove()
    super.instanceOverrides
  }

  type Elem
  type Key
  type Value
  type Wrapped
  type Col[_]
  type Map[_, _]
  type TC[_]

  private[this] val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[TypeId[?], Array[FieldInfo]]] {
      override def initialValue: java.util.HashMap[TypeId[?], Array[FieldInfo]] = new java.util.HashMap
    }
  private[this] val discriminatorFields = new ThreadLocal[List[DiscriminatorFieldInfo]] {
    override def initialValue: List[DiscriminatorFieldInfo] = Nil
  }

  private[this] def isEnumeration[F[_, _], A](cases: IndexedSeq[Term[F, A, ?]]): Boolean =
    enumValuesAsStrings && cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.asRecord.exists(_.fields.isEmpty) ||
      caseReflect.isVariant && caseReflect.asVariant.map(_.cases).forall(isEnumeration)
    }

  private[this] def hasOnlyRecordAndVariantCases[F[_, _], A](cases: IndexedSeq[Term[F, A, ?]]): Boolean =
    cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.isRecord ||
      caseReflect.isVariant && caseReflect.asVariant.map(_.cases).forall(hasOnlyRecordAndVariantCases)
    }

  private[this] def getDefaultValue[F[_, _], A](fieldReflect: Reflect[F, A]): Option[?] =
    if (requireDefaultValueFields) None
    else fieldReflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue

  private[this] def discriminator[F[_, _], A](caseReflect: Reflect[F, A]): Discriminator[?] =
    caseReflect.asVariant.get.variantBinding
      .asInstanceOf[BindingInstance[TC, ?, ?]]
      .binding
      .asInstanceOf[Binding.Variant[A]]
      .discriminator
}

private class FieldInfo(
  val span: DynamicOptic.Node.Field,
  defaultValue: Option[?],
  emptyCollectionConstructor: () => AnyRef,
  offset: RegisterOffset = 0L,
  typeTag: Int,
  val idx: Int,
  val isOptional: Boolean,
  val usesNullSentinel: Boolean = false
) {
  var nonTransient: Boolean                        = true
  private[this] var isPredefinedCodec: Boolean     = false
  private[this] var isNonEscapedAsciiName: Boolean = false
  private[this] var name: String                   = null
  private[this] var codec: JsonCodec[?]            = null

  def setName(name: String): Unit = {
    isNonEscapedAsciiName = JsonWriter.isNonEscapedAscii(name)
    this.name = name
  }

  def setCodec(codec: JsonCodec[?]): Unit = {
    isPredefinedCodec =
      (codec eq intCodec) || (codec eq longCodec) || (codec eq floatCodec) || (codec eq doubleCodec) ||
        (codec eq booleanCodec) || (codec eq byteCodec) || (codec eq charCodec) || (codec eq shortCodec)
    this.codec = codec
  }

  @inline
  def getName: String = name

  @inline
  def getCodec: JsonCodec[?] = codec

  @inline
  def hasDefault: Boolean = defaultValue ne None

  @inline
  def isCollection: Boolean = emptyCollectionConstructor ne null

  @inline
  def nameMatch(in: JsonReader, keyLen: Int): Boolean = in.isCharBufEqualsTo(keyLen, name)

  def readValue(in: JsonReader, baseOffset: RegisterOffset): Unit = {
    val offset = this.offset + baseOffset
    val regs   = in.registers
    (typeTag: @switch) match {
      case 0 =>
        regs.setObject(offset, codec.asInstanceOf[JsonCodec[AnyRef]].decodeValue(in))
      case 1 =>
        regs.setInt(
          offset,
          if (isPredefinedCodec) in.readInt()
          else codec.asInstanceOf[JsonCodec[Int]].decodeValue(in)
        )
      case 2 =>
        regs.setLong(
          offset,
          if (isPredefinedCodec) in.readLong()
          else codec.asInstanceOf[JsonCodec[Long]].decodeValue(in)
        )
      case 3 =>
        regs.setFloat(
          offset,
          if (isPredefinedCodec) in.readFloat()
          else codec.asInstanceOf[JsonCodec[Float]].decodeValue(in)
        )
      case 4 =>
        regs.setDouble(
          offset,
          if (isPredefinedCodec) in.readDouble()
          else codec.asInstanceOf[JsonCodec[Double]].decodeValue(in)
        )
      case 5 =>
        regs.setBoolean(
          offset,
          if (isPredefinedCodec) in.readBoolean()
          else codec.asInstanceOf[JsonCodec[Boolean]].decodeValue(in)
        )
      case 6 =>
        regs.setByte(
          offset,
          if (isPredefinedCodec) in.readByte()
          else codec.asInstanceOf[JsonCodec[Byte]].decodeValue(in)
        )
      case 7 =>
        regs.setChar(
          offset,
          if (isPredefinedCodec) in.readChar()
          else codec.asInstanceOf[JsonCodec[Char]].decodeValue(in)
        )
      case 8 =>
        regs.setShort(
          offset,
          if (isPredefinedCodec) in.readShort()
          else codec.asInstanceOf[JsonCodec[Short]].decodeValue(in)
        )
      case _ =>
        codec.asInstanceOf[JsonCodec[Unit]].decodeValue(in)
    }
  }

  def readValue(regs: Registers, json: Json): Unit =
    (typeTag: @switch) match {
      case 0 => regs.setObject(offset, codec.asInstanceOf[JsonCodec[AnyRef]].decodeValue(json))
      case 1 => regs.setInt(offset, codec.asInstanceOf[JsonCodec[Int]].decodeValue(json))
      case 2 => regs.setLong(offset, codec.asInstanceOf[JsonCodec[Long]].decodeValue(json))
      case 3 => regs.setFloat(offset, codec.asInstanceOf[JsonCodec[Float]].decodeValue(json))
      case 4 => regs.setDouble(offset, codec.asInstanceOf[JsonCodec[Double]].decodeValue(json))
      case 5 => regs.setBoolean(offset, codec.asInstanceOf[JsonCodec[Boolean]].decodeValue(json))
      case 6 => regs.setByte(offset, codec.asInstanceOf[JsonCodec[Byte]].decodeValue(json))
      case 7 => regs.setChar(offset, codec.asInstanceOf[JsonCodec[Char]].decodeValue(json))
      case 8 => regs.setShort(offset, codec.asInstanceOf[JsonCodec[Short]].decodeValue(json))
      case _ => codec.asInstanceOf[JsonCodec[Unit]].decodeValue(json)
    }

  def setMissingValueOrError(in: JsonReader, baseOffset: RegisterOffset): Unit = {
    val offset = this.offset + baseOffset
    val regs   = in.registers
    if (defaultValue ne None) {
      val dv = defaultValue.get
      (typeTag: @switch) match {
        case 0 => regs.setObject(offset, dv.asInstanceOf[AnyRef])
        case 1 => regs.setInt(offset, dv.asInstanceOf[Int])
        case 2 => regs.setLong(offset, dv.asInstanceOf[Long])
        case 3 => regs.setFloat(offset, dv.asInstanceOf[Float])
        case 4 => regs.setDouble(offset, dv.asInstanceOf[Double])
        case 5 => regs.setBoolean(offset, dv.asInstanceOf[Boolean])
        case 6 => regs.setByte(offset, dv.asInstanceOf[Byte])
        case 7 => regs.setChar(offset, dv.asInstanceOf[Char])
        case 8 => regs.setShort(offset, dv.asInstanceOf[Short])
        case _ =>
      }
    } else if (isOptional) regs.setObject(offset, if (usesNullSentinel) null else None)
    else if (emptyCollectionConstructor ne null) regs.setObject(offset, emptyCollectionConstructor())
    else in.requiredFieldError(name)
  }

  def setMissingValueOrError(regs: Registers): Unit =
    if (defaultValue ne None) {
      val dv = defaultValue.get
      (typeTag: @switch) match {
        case 0 => regs.setObject(offset, dv.asInstanceOf[AnyRef])
        case 1 => regs.setInt(offset, dv.asInstanceOf[Int])
        case 2 => regs.setLong(offset, dv.asInstanceOf[Long])
        case 3 => regs.setFloat(offset, dv.asInstanceOf[Float])
        case 4 => regs.setDouble(offset, dv.asInstanceOf[Double])
        case 5 => regs.setBoolean(offset, dv.asInstanceOf[Boolean])
        case 6 => regs.setByte(offset, dv.asInstanceOf[Byte])
        case 7 => regs.setChar(offset, dv.asInstanceOf[Char])
        case 8 => regs.setShort(offset, dv.asInstanceOf[Short])
        case _ =>
      }
    } else if (isOptional) regs.setObject(offset, if (usesNullSentinel) null else None)
    else if (emptyCollectionConstructor ne null) regs.setObject(offset, emptyCollectionConstructor())
    else throw new JsonCodecError(Nil, s"missing required field \"$name\"")

  def writeDefaultValue(out: JsonWriter, baseOffset: RegisterOffset): Unit = {
    val offset = this.offset + baseOffset
    val regs   = out.registers
    val dv     = defaultValue.get
    (typeTag: @switch) match {
      case 0 =>
        val value = regs.getObject(offset)
        if (dv != value) {
          writeKey(out)
          codec.asInstanceOf[JsonCodec[AnyRef]].encodeValue(value, out)
        }
      case 1 =>
        val value = regs.getInt(offset)
        if (dv.asInstanceOf[Int] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonCodec[Int]].encodeValue(value, out)
        }
      case 2 =>
        val value = regs.getLong(offset)
        if (dv.asInstanceOf[Long] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonCodec[Long]].encodeValue(value, out)
        }
      case 3 =>
        val value = regs.getFloat(offset)
        if (dv.asInstanceOf[Float] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonCodec[Float]].encodeValue(value, out)
        }
      case 4 =>
        val value = regs.getDouble(offset)
        if (dv.asInstanceOf[Double] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonCodec[Double]].encodeValue(value, out)
        }
      case 5 =>
        val value = regs.getBoolean(offset)
        if (dv.asInstanceOf[Boolean] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonCodec[Boolean]].encodeValue(value, out)
        }
      case 6 =>
        val value = regs.getByte(offset)
        if (dv.asInstanceOf[Byte] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonCodec[Byte]].encodeValue(value, out)
        }
      case 7 =>
        val value = regs.getChar(offset)
        if (dv.asInstanceOf[Char] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonCodec[Char]].encodeValue(value, out)
        }
      case 8 =>
        val value = regs.getShort(offset)
        if (dv.asInstanceOf[Short] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonCodec[Short]].encodeValue(value, out)
        }
      case _ =>
    }
  }

  def writeDefaultValue(regs: Registers, builder: ChunkBuilder[(String, Json)]): Unit = {
    val dv = defaultValue.get
    (typeTag: @switch) match {
      case 0 =>
        val value = regs.getObject(offset)
        if (dv != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[AnyRef]].encodeValue(value)))
        }
      case 1 =>
        val value = regs.getInt(offset)
        if (dv.asInstanceOf[Int] != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Int]].encodeValue(value)))
        }
      case 2 =>
        val value = regs.getLong(offset)
        if (dv.asInstanceOf[Long] != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Long]].encodeValue(value)))
        }
      case 3 =>
        val value = regs.getFloat(offset)
        if (dv.asInstanceOf[Float] != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Float]].encodeValue(value)))
        }
      case 4 =>
        val value = regs.getDouble(offset)
        if (dv.asInstanceOf[Double] != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Double]].encodeValue(value)))
        }
      case 5 =>
        val value = regs.getBoolean(offset)
        if (dv.asInstanceOf[Boolean] != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Boolean]].encodeValue(value)))
        }
      case 6 =>
        val value = regs.getByte(offset)
        if (dv.asInstanceOf[Byte] != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Byte]].encodeValue(value)))
        }
      case 7 =>
        val value = regs.getChar(offset)
        if (dv.asInstanceOf[Char] != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Char]].encodeValue(value)))
        }
      case 8 =>
        val value = regs.getShort(offset)
        if (dv.asInstanceOf[Short] != value) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Short]].encodeValue(value)))
        }
      case _ =>
    }
  }

  def writeOptional(out: JsonWriter, baseOffset: RegisterOffset): Unit = {
    val value    = out.registers.getObject(offset + baseOffset)
    val isAbsent = if (usesNullSentinel) value eq null else value eq None
    if (!isAbsent) {
      writeKey(out)
      codec.asInstanceOf[JsonCodec[AnyRef]].encodeValue(value, out)
    }
  }

  def writeOptional(regs: Registers, builder: ChunkBuilder[(String, Json)]): Unit = {
    val value    = regs.getObject(offset)
    val isAbsent = if (usesNullSentinel) value eq null else value eq None
    if (!isAbsent) {
      builder.addOne((name, codec.asInstanceOf[JsonCodec[AnyRef]].encodeValue(value)))
    }
  }

  def writeCollection(out: JsonWriter, baseOffset: RegisterOffset): Unit =
    out.registers.getObject(offset + baseOffset) match {
      case value: Iterable[?] =>
        if (value.nonEmpty) {
          writeKey(out)
          codec.asInstanceOf[JsonCodec[Iterable[?]]].encodeValue(value, out)
        }
      case value: Array[?] =>
        if (value.length > 0) {
          writeKey(out)
          codec.asInstanceOf[JsonCodec[Array[?]]].encodeValue(value, out)
        }
    }

  def writeCollection(regs: Registers, builder: ChunkBuilder[(String, Json)]): Unit =
    regs.getObject(offset) match {
      case value: Iterable[?] =>
        if (value.nonEmpty) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Iterable[?]]].encodeValue(value)))
        }
      case value: Array[?] =>
        if (value.length > 0) {
          builder.addOne((name, codec.asInstanceOf[JsonCodec[Array[?]]].encodeValue(value)))
        }
    }

  def writeRequired(out: JsonWriter, baseOffset: RegisterOffset): Unit = {
    writeKey(out)
    val offset = this.offset + baseOffset
    val regs   = out.registers
    (typeTag: @switch) match {
      case 0 =>
        codec.asInstanceOf[JsonCodec[AnyRef]].encodeValue(regs.getObject(offset), out)
      case 1 =>
        val value = regs.getInt(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonCodec[Int]].encodeValue(value, out)
      case 2 =>
        val value = regs.getLong(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonCodec[Long]].encodeValue(value, out)
      case 3 =>
        val value = regs.getFloat(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonCodec[Float]].encodeValue(value, out)
      case 4 =>
        val value = regs.getDouble(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonCodec[Double]].encodeValue(value, out)
      case 5 =>
        val value = regs.getBoolean(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonCodec[Boolean]].encodeValue(value, out)
      case 6 =>
        val value = regs.getByte(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonCodec[Byte]].encodeValue(value, out)
      case 7 =>
        val value = regs.getChar(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonCodec[Char]].encodeValue(value, out)
      case 8 =>
        val value = regs.getShort(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonCodec[Short]].encodeValue(value, out)
      case _ => codec.asInstanceOf[JsonCodec[Unit]].encodeValue((), out)
    }
  }

  def writeRequired(regs: Registers, builder: ChunkBuilder[(String, Json)]): Unit = builder.addOne(
    (
      name, {
        (typeTag: @switch) match {
          case 0 => codec.asInstanceOf[JsonCodec[AnyRef]].encodeValue(regs.getObject(offset))
          case 1 => codec.asInstanceOf[JsonCodec[Int]].encodeValue(regs.getInt(offset))
          case 2 => codec.asInstanceOf[JsonCodec[Long]].encodeValue(regs.getLong(offset))
          case 3 => codec.asInstanceOf[JsonCodec[Float]].encodeValue(regs.getFloat(offset))
          case 4 => codec.asInstanceOf[JsonCodec[Double]].encodeValue(regs.getDouble(offset))
          case 5 => codec.asInstanceOf[JsonCodec[Boolean]].encodeValue(regs.getBoolean(offset))
          case 6 => codec.asInstanceOf[JsonCodec[Byte]].encodeValue(regs.getByte(offset))
          case 7 => codec.asInstanceOf[JsonCodec[Char]].encodeValue(regs.getChar(offset))
          case 8 => codec.asInstanceOf[JsonCodec[Short]].encodeValue(regs.getShort(offset))
          case _ => codec.asInstanceOf[JsonCodec[Unit]].encodeValue(())
        }
      }
    )
  )

  @inline
  private[this] def writeKey(out: JsonWriter): Unit =
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
    else out.writeKey(name)
}

private class DiscriminatorFieldInfo(name: String, value: String) {
  private[this] val keyAndValueJson        = (name, new Json.String(value))
  private[this] val isNonEscapedAsciiName  = JsonWriter.isNonEscapedAscii(name)
  private[this] val isNonEscapedAsciiValue = JsonWriter.isNonEscapedAscii(value)

  @inline
  def nameMatch(in: JsonReader, keyLen: Int): Boolean = in.isCharBufEqualsTo(keyLen, name)

  @inline
  def getName: String = name

  @inline
  def writeKeyAndValue(out: JsonWriter): Unit = {
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
    else out.writeKey(name)
    if (isNonEscapedAsciiValue) out.writeNonEscapedAsciiVal(value)
    else out.writeVal(value)
  }

  @inline
  def writeKeyAndValue(builder: ChunkBuilder[(String, Json)]): Unit = builder.addOne(keyAndValueJson)
}

private sealed trait CaseInfo

private class CaseLeafInfo(
  var codec: JsonCodec[?],
  val spans: List[DynamicOptic.Node.Case]
) extends CaseInfo {
  private[this] var name: String                   = null
  private[this] var isNonEscapedAsciiName: Boolean = false
  private[this] var aliases: Array[String]         = null

  def setName(name: String): Unit = {
    isNonEscapedAsciiName = JsonWriter.isNonEscapedAscii(name)
    this.name = name
  }

  @inline
  def getName: String = name

  def setAliases(aliases: Array[String]): Unit =
    this.aliases = aliases

  def getAliases: Array[String] = aliases

  @inline
  def writeKey(out: JsonWriter): Unit =
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
    else out.writeKey(name)
}

private class CaseNodeInfo[A](
  private[this] val discriminator: Discriminator[A],
  private[this] val caseInfosArray: Array[CaseInfo]
) extends CaseInfo {
  @inline
  def caseInfos: Array[CaseInfo] = caseInfosArray

  @tailrec
  final def discriminate(x: A): CaseLeafInfo = caseInfosArray(discriminator.discriminate(x)) match {
    case eli: CaseLeafInfo => eli
    case eni               => eni.asInstanceOf[CaseNodeInfo[A]].discriminate(x)
  }
}

private sealed trait EnumInfo

private class EnumLeafInfo(name: String, val constructor: Constructor[?]) extends EnumInfo {
  private[this] val isNonEscapedAsciiName = JsonWriter.isNonEscapedAscii(name)

  @inline
  def enumName: String = name

  @inline
  def writeVal(out: JsonWriter): Unit =
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiVal(name)
    else out.writeVal(name)
}

private class EnumNodeInfo[A](
  private[this] val discriminator: Discriminator[A],
  private[this] val enumInfosArray: Array[EnumInfo]
) extends EnumInfo {
  @inline
  def enumInfos: Array[EnumInfo] = enumInfosArray

  @tailrec
  final def discriminate(x: A): EnumLeafInfo = enumInfosArray(discriminator.discriminate(x)) match {
    case eli: EnumLeafInfo => eli
    case eni               => eni.asInstanceOf[EnumNodeInfo[A]].discriminate(x)
  }
}

private class StringMap[A <: AnyRef](initCapacity: Int) {
  private[this] var size = 0
  private[this] var mask = (Integer.highestOneBit(initCapacity | 1) << 3) - 2
  private[this] var kvs  = new Array[AnyRef](mask + 2)

  def put(key: String, value: A): Unit = {
    val keyLen    = key.length
    var hash, idx = 0
    while (idx < keyLen) {
      hash = (hash << 5) + (key.charAt(idx) - hash)
      idx += 1
    }
    idx = hash & mask
    var currKey: AnyRef = null
    while ({
      currKey = kvs(idx)
      (currKey ne null) && !currKey.equals(key)
    }) idx = (idx + 2) & mask
    if (currKey ne null) sys.error(s"Cannot derive codec - duplicated name detected: '$key'")
    kvs(idx) = key
    kvs(idx + 1) = value
    size += 1
    if (size << 2 > mask) grow()
  }

  def get(in: JsonReader, keyLen: Int): A = {
    var idx = in.charBufToHashCode(keyLen) & mask
    while (true) {
      val currKey = kvs(idx)
      if (currKey eq null) return null.asInstanceOf[A]
      if (in.isCharBufEqualsTo(keyLen, currKey.asInstanceOf[String])) return kvs(idx + 1).asInstanceOf[A]
      idx = (idx + 2) & mask
    }
    null.asInstanceOf[A] // unreachable
  }

  def get(key: String): A = {
    var idx = key.hashCode & mask
    while (true) {
      val currKey = kvs(idx)
      if (currKey eq null) return null.asInstanceOf[A]
      if (key == currKey) return kvs(idx + 1).asInstanceOf[A]
      idx = (idx + 2) & mask
    }
    null.asInstanceOf[A] // unreachable
  }

  def keys: Chunk[String] = {
    val builder = ChunkBuilder.make[String](size)
    var idx     = 0
    while (idx <= mask) {
      val k = kvs(idx)
      if (k != null) builder.addOne(k.asInstanceOf[String])
      idx += 2
    }
    builder.result()
  }

  private[this] def grow(): Unit = {
    val mask = (Integer.highestOneBit(size | 1) << 3) - 2
    val kvs  = new Array[AnyRef](mask + 2)
    val len  = this.kvs.length
    var idx  = 0
    while (idx < len) {
      val key = this.kvs(idx).asInstanceOf[String]
      if (key ne null) {
        val keyLen       = key.length
        var hash, keyIdx = 0
        while (keyIdx < keyLen) {
          hash = (hash << 5) + (key.charAt(keyIdx) - hash)
          keyIdx += 1
        }
        keyIdx = hash & mask
        var currKey: AnyRef = null
        while ({
          currKey = kvs(keyIdx)
          (currKey ne null) && !currKey.equals(key)
        }) keyIdx = (keyIdx + 2) & mask
        kvs(keyIdx) = key
        kvs(keyIdx + 1) = this.kvs(idx + 1)
      }
      idx += 2
    }
    this.mask = mask
    this.kvs = kvs
  }
}
