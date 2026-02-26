package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder, ChunkMap, NonEmptyChunk}
import zio.blocks.docs.Doc
import zio.blocks.schema.json._
import zio.blocks.schema.json.JsonBinaryCodec._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, RegisterOffset, Registers}
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
 * MIME type and utilizing the `JsonBinaryCodecDeriver` for deriving the
 * necessary codecs.
 */
object JsonFormat extends BinaryFormat("application/json", JsonBinaryCodecDeriver)

/**
 * Provides a default implementation of `JsonBinaryCodecDeriver` with
 * customizable settings for JSON and binary codec derivation. This object
 * allows the derivation of codecs for various data types, including primitives,
 * records, variants, sequences, maps, and dynamic values.
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
 *   - `transientNone`: Excludes fields with a value of `None` during
 *     serialization.
 *   - `requireOptionFields`: Enforces the inclusion of optional fields in
 *     deserialization.
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
object JsonBinaryCodecDeriver
    extends JsonBinaryCodecDeriver(
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

class JsonBinaryCodecDeriver private[json] (
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
) extends Deriver[JsonBinaryCodec] {

  /**
   * Updates the `JsonBinaryCodecDeriver` instance with the specified field name
   * mapper. The field name mapper defines how field names should be transformed
   * during encoding and decoding.
   *
   * @param fieldNameMapper
   *   The `NameMapper` to apply for transforming field names.
   * @return
   *   A new instance of `JsonBinaryCodecDeriver` with the updated field name
   *   mapper.
   */
  def withFieldNameMapper(fieldNameMapper: NameMapper): JsonBinaryCodecDeriver = copy(fieldNameMapper = fieldNameMapper)

  /**
   * Updates the `JsonBinaryCodecDeriver` instance with the specified case name
   * mapper. The case name mapper defines how case names should be transformed
   * during encoding and decoding.
   *
   * @param caseNameMapper
   *   The `NameMapper` to apply for transforming case names.
   * @return
   *   A new instance of `JsonBinaryCodecDeriver` with the updated case name
   *   mapper.
   */
  def withCaseNameMapper(caseNameMapper: NameMapper): JsonBinaryCodecDeriver = copy(caseNameMapper = caseNameMapper)

  /**
   * Updates the `JsonBinaryCodecDeriver` instance with the specified
   * discriminator kind. The discriminator kind defines how the subtype
   * discriminator is represented in the serialized JSON.
   *
   * @param discriminatorKind
   *   The `DiscriminatorKind` to apply for specifying how the discriminator is
   *   handled in the JSON schema for sealed hierarchies.
   * @return
   *   A new instance of `JsonBinaryCodecDeriver` with the updated discriminator
   *   kind.
   */
  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): JsonBinaryCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  /**
   * Updates the `JsonBinaryCodecDeriver` instance to specify whether additional
   * fields in the JSON input that are not part of the schema should be rejected
   * during decoding.
   *
   * @param rejectExtraFields
   *   A boolean flag indicating whether to reject extra fields that are not
   *   defined in the schema. If `true`, decoding will fail when extra fields
   *   are encountered; if `false`, extra fields will be ignored.
   * @return
   *   A new instance of `JsonBinaryCodecDeriver` with the updated
   *   `rejectExtraFields` setting.
   */
  def withRejectExtraFields(rejectExtraFields: Boolean): JsonBinaryCodecDeriver =
    copy(rejectExtraFields = rejectExtraFields)

  /**
   * Updates the `JsonBinaryCodecDeriver` instance to specify whether
   * enumeration values should be serialized and deserialized as strings.
   *
   * @param enumValuesAsStrings
   *   A boolean flag indicating whether to treat enumeration values as strings.
   *   If `true`, enumeration values are serialized and deserialized as their
   *   string representations; if `false`, they are encoded with default
   *   `DiscriminatorKind.Key` encoding.
   * @return
   *   A new instance of `JsonBinaryCodecDeriver` with the updated
   *   `enumValuesAsStrings` setting.
   */
  def withEnumValuesAsStrings(enumValuesAsStrings: Boolean): JsonBinaryCodecDeriver =
    copy(enumValuesAsStrings = enumValuesAsStrings)

  /**
   * Updates the `JsonBinaryCodecDeriver` instance to specify whether fields of
   * type `Option` with a value of `None` should be excluded during encoding.
   *
   * @param transientNone
   *   A boolean flag indicating whether to exclude fields of type `Option` with
   *   a value of `None` during encoding. If `true`, such fields are omitted; if
   *   `false`, they are included.
   * @return
   *   A new instance of `JsonBinaryCodecDeriver` with the updated
   *   `transientNone` setting.
   */
  def withTransientNone(transientNone: Boolean): JsonBinaryCodecDeriver = copy(transientNone = transientNone)

  /**
   * Sets the requirement for optional fields.
   *
   * @param requireOptionFields
   *   A boolean flag indicating whether optional fields are required. If true,
   *   optional fields must be present and will not be treated as optional
   *   during codec derivation.
   * @return
   *   A new instance of JsonBinaryCodecDeriver with the updated setting for
   *   requiring optional fields.
   */
  def withRequireOptionFields(requireOptionFields: Boolean): JsonBinaryCodecDeriver =
    copy(requireOptionFields = requireOptionFields)

  /**
   * Configures whether the derived codec should handle empty collections as
   * transient.
   *
   * @param transientEmptyCollection
   *   Indicates if empty collections should be treated as transient.
   * @return
   *   A new instance of JsonBinaryCodecDeriver with the updated
   *   transientEmptyCollection setting.
   */
  def withTransientEmptyCollection(transientEmptyCollection: Boolean): JsonBinaryCodecDeriver =
    copy(transientEmptyCollection = transientEmptyCollection)

  /**
   * Sets the flag indicating whether collection fields are required.
   *
   * @param requireCollectionFields
   *   A boolean value specifying if collection fields should be required.
   * @return
   *   A new instance of JsonBinaryCodecDeriver with the updated configuration.
   */
  def withRequireCollectionFields(requireCollectionFields: Boolean): JsonBinaryCodecDeriver =
    copy(requireCollectionFields = requireCollectionFields)

  /**
   * Sets the transient behavior for fields with defined default values.
   *
   * @param transientDefaultValue
   *   A boolean indicating whether the transient behavior for fields with
   *   defined default values should be applied or not.
   * @return
   *   A new instance of JsonBinaryCodecDeriver with the specified transient
   *   default value setting.
   */
  def withTransientDefaultValue(transientDefaultValue: Boolean): JsonBinaryCodecDeriver =
    copy(transientDefaultValue = transientDefaultValue)

  /**
   * Sets the flag indicating whether fields with default values are required.
   *
   * @param requireDefaultValueFields
   *   A boolean flag indicating whether fields with default values should be
   *   required.
   * @return
   *   A new instance of `JsonBinaryCodecDeriver` with the updated
   *   configuration.
   */
  def withRequireDefaultValueFields(requireDefaultValueFields: Boolean): JsonBinaryCodecDeriver =
    copy(requireDefaultValueFields = requireDefaultValueFields)

  private def copy(
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
    new JsonBinaryCodecDeriver(
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
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[JsonBinaryCodec[A]] = {
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
  }.asInstanceOf[Lazy[JsonBinaryCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val len           = fields.length
      if (typeId.isTuple) Lazy {
        var offset = 0L
        val codecs = new Array[JsonBinaryCodec[?]](len)
        var idx    = 0
        while (idx < len) {
          val codec = D.instance(fields(idx).value.metadata).force
          codecs(idx) = codec
          offset = RegisterOffset.add(codec.valueOffset, offset)
          idx += 1
        }
        new JsonBinaryCodec[A]() {
          private[this] val deconstructor = recordBinding.deconstructor
          private[this] val constructor   = recordBinding.constructor
          private[this] val fieldCodecs   = codecs
          private[this] val usedRegisters = offset

          override def decodeValue(in: JsonReader, default: A): A =
            if (in.isNextToken('[')) {
              val top = in.push(usedRegisters)
              try {
                val regs = in.registers
                if (!in.isNextToken(']')) {
                  in.rollbackToken()
                  var offset = top
                  val len    = fieldCodecs.length
                  var idx    = 0
                  while ({
                    val codec = fieldCodecs(idx)
                    try {
                      (codec.valueType: @switch) match {
                        case 0 =>
                          regs.setObject(
                            offset,
                            codec
                              .asInstanceOf[JsonBinaryCodec[AnyRef]]
                              .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[AnyRef]].nullValue)
                          )
                        case 1 =>
                          val value =
                            if (codec eq intCodec) in.readInt()
                            else {
                              codec
                                .asInstanceOf[JsonBinaryCodec[Int]]
                                .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Int]].nullValue)
                            }
                          regs.setInt(offset, value)
                        case 2 =>
                          val value =
                            if (codec eq longCodec) in.readLong()
                            else {
                              codec
                                .asInstanceOf[JsonBinaryCodec[Long]]
                                .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Long]].nullValue)
                            }
                          regs.setLong(offset, value)
                        case 3 =>
                          val value =
                            if (codec eq floatCodec) in.readFloat()
                            else {
                              codec
                                .asInstanceOf[JsonBinaryCodec[Float]]
                                .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Float]].nullValue)
                            }
                          regs.setFloat(offset, value)
                        case 4 =>
                          val value =
                            if (codec eq doubleCodec) in.readDouble()
                            else {
                              codec
                                .asInstanceOf[JsonBinaryCodec[Double]]
                                .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Double]].nullValue)
                            }
                          regs.setDouble(offset, value)
                        case 5 =>
                          val value =
                            if (codec eq booleanCodec) in.readBoolean()
                            else {
                              codec
                                .asInstanceOf[JsonBinaryCodec[Boolean]]
                                .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Boolean]].nullValue)
                            }
                          regs.setBoolean(offset, value)
                        case 6 =>
                          val value =
                            if (codec eq byteCodec) in.readByte()
                            else {
                              codec
                                .asInstanceOf[JsonBinaryCodec[Byte]]
                                .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Byte]].nullValue)
                            }
                          regs.setByte(offset, value)
                        case 7 =>
                          val value =
                            if (codec eq charCodec) in.readChar()
                            else {
                              codec
                                .asInstanceOf[JsonBinaryCodec[Char]]
                                .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Char]].nullValue)
                            }
                          regs.setChar(offset, value)
                        case 8 =>
                          val value =
                            if (codec eq shortCodec) in.readShort()
                            else {
                              codec
                                .asInstanceOf[JsonBinaryCodec[Short]]
                                .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Short]].nullValue)
                            }
                          regs.setShort(offset, value)
                        case _ =>
                          codec
                            .asInstanceOf[JsonBinaryCodec[Unit]]
                            .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Unit]].nullValue)
                      }
                    } catch {
                      case error if NonFatal(error) =>
                        in.decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                    }
                    offset += codec.valueOffset
                    idx += 1
                    idx < len && (in.isNextToken(',') || in.commaError())
                  }) ()
                  if (!in.isNextToken(']')) in.arrayEndError()
                }
                constructor.construct(regs, top)
              } finally in.pop(usedRegisters)
            } else {
              in.rollbackToken()
              in.readNullOrTokenError(default, '[')
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
                (codec.valueType: @switch) match {
                  case 0 =>
                    codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(regs.getObject(offset), out)
                  case 1 =>
                    val value = regs.getInt(offset)
                    if (codec eq intCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Int]].encodeValue(value, out)
                  case 2 =>
                    val value = regs.getLong(offset)
                    if (codec eq longCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Long]].encodeValue(value, out)
                  case 3 =>
                    val value = regs.getFloat(offset)
                    if (codec eq floatCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Float]].encodeValue(value, out)
                  case 4 =>
                    val value = regs.getDouble(offset)
                    if (codec eq doubleCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Double]].encodeValue(value, out)
                  case 5 =>
                    val value = regs.getBoolean(offset)
                    if (codec eq booleanCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Boolean]].encodeValue(value, out)
                  case 6 =>
                    val value = regs.getByte(offset)
                    if (codec eq byteCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Byte]].encodeValue(value, out)
                  case 7 =>
                    val value = regs.getChar(offset)
                    if (codec eq charCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Char]].encodeValue(value, out)
                  case 8 =>
                    val value = regs.getShort(offset)
                    if (codec eq shortCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Short]].encodeValue(value, out)
                  case _ => codec.asInstanceOf[JsonBinaryCodec[Unit]].encodeValue((), out)
                }
                offset += codec.valueOffset
                idx += 1
              }
            } finally out.pop(usedRegisters)
            out.writeArrayEnd()
          }

          override lazy val toJsonSchema: JsonSchema =
            NonEmptyChunk.fromChunk(Chunk.from(fieldCodecs.map(_.toJsonSchema))) match {
              case Some(schemas) =>
                JsonSchema.array(
                  prefixItems = new Some(schemas),
                  items = new Some(JsonSchema.False)
                )
              case _ => JsonSchema.False
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
            var idx = 0
            while (idx < len) {
              val field        = fields(idx)
              val fieldReflect = field.value
              infos(idx) = new FieldInfo(
                new DynamicOptic.Node.Field(field.name),
                getDefaultValue(fieldReflect),
                idx,
                isOptional(fieldReflect),
                isCollection(fieldReflect)
              )
              idx += 1
            }
            if (isRecursive) recursiveRecordCache.get.put(typeId, infos)
            discriminatorFields.set(null :: discriminatorFields.get)
          }
          var offset = 0L
          val map    = new StringMap[FieldInfo](len)
          var idx    = 0
          while (idx < len) {
            val field     = fields(idx)
            val fieldInfo = infos(idx)
            if (deriveCodecs) {
              val codec = D.instance(field.value.metadata).force
              fieldInfo.setCodec(codec)
              fieldInfo.setOffset(offset)
              offset = RegisterOffset.add(codec.valueOffset, offset)
            }
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
          val typeName = typeId.name
          new JsonBinaryCodec[A]() {
            private[this] val deconstructor       = recordBinding.deconstructor
            private[this] val constructor         = recordBinding.constructor
            private[this] val fieldInfos          = infos
            private[this] val fieldIndexMap       = map
            private[this] val discriminatorField  = discriminatorFields.get.headOption.orNull
            private[this] var usedRegisters       = offset
            private[this] val skipNone            = transientNone
            private[this] val skipEmptyCollection = transientEmptyCollection
            private[this] val skipDefaultValue    = transientDefaultValue
            private[this] val doReject            = rejectExtraFields

            require(fieldInfos.length <= 128, "expected up to 128 fields")

            override def decodeValue(in: JsonReader, default: A): A =
              if (in.isNextToken('{')) {
                val len = fieldInfos.length
                if (len > 0 && usedRegisters == 0) {
                  usedRegisters = fieldInfos(len - 1).usedRegisters // delayed initialization for recursive records
                }
                val top = in.push(usedRegisters)
                try {
                  val regs                 = in.registers
                  var fieldInfo: FieldInfo = null
                  var missing1, missing2   = -1L
                  var idx, keyLen          = -1
                  if (!in.isNextToken('}')) {
                    in.rollbackToken()
                    while (keyLen < 0 || in.isNextToken(',')) {
                      keyLen = in.readKeyAsCharBuf()
                      if (
                        len > 0 && {
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
                        try fieldInfo.readValue(in, regs, top)
                        catch {
                          case error if NonFatal(error) => in.decodeError(fieldInfo.span, error)
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
                    fieldInfos(idx).setMissingValueOrError(in, regs, top)
                    missing1 &= missing1 - 1L
                  }
                  if (len > 64) {
                    while ({
                      idx = java.lang.Long.numberOfTrailingZeros(missing2) + 64
                      idx < len
                    }) {
                      fieldInfos(idx).setMissingValueOrError(in, regs, top)
                      missing2 &= missing2 - 1L
                    }
                  }
                  constructor.construct(regs, top)
                } finally in.pop(usedRegisters)
              } else {
                in.rollbackToken()
                in.readNullOrTokenError(default, '{')
              }

            override def encodeValue(x: A, out: JsonWriter): Unit = {
              out.writeObjectStart()
              if (discriminatorField ne null) discriminatorField.writeKeyAndValue(out)
              val top = out.push(usedRegisters)
              try {
                val regs = out.registers
                deconstructor.deconstruct(regs, top, x)
                val len = fieldInfos.length
                var idx = 0
                while (idx < len) {
                  val fieldInfo = fieldInfos(idx)
                  if (fieldInfo.nonTransient) {
                    if (skipDefaultValue && fieldInfo.hasDefault) fieldInfo.writeDefaultValue(out, regs, top)
                    else if (skipNone && fieldInfo.isOptional) fieldInfo.writeOptional(out, regs, top)
                    else if (skipEmptyCollection && fieldInfo.isCollection) fieldInfo.writeCollection(out, regs, top)
                    else fieldInfo.writeRequired(out, regs, top)
                  }
                  idx += 1
                }
              } finally out.pop(usedRegisters)
              out.writeObjectEnd()
            }

            private[this] def skipOrReject(in: JsonReader, keyLen: Int): Unit =
              if (doReject && ((discriminatorField eq null) || !discriminatorField.nameMatch(in, keyLen))) {
                in.unexpectedKeyError(keyLen)
              } else in.skip()

            override lazy val toJsonSchema: JsonSchema = {
              val reqs    = Set.newBuilder[String]
              val len     = fieldInfos.length
              val names   = ChunkBuilder.make[String](len)
              val schemas = ChunkBuilder.make[JsonSchema](len)
              var idx     = 0
              while (idx < len) {
                val fieldInfo = fieldInfos(idx)
                if (
                  fieldInfo.nonTransient && {
                    names.addOne(fieldInfo.getName)
                    schemas.addOne(fieldInfo.getCodec.toJsonSchema)
                    !(fieldInfo.hasDefault || fieldInfo.isOptional || fieldInfo.isCollection)
                  }
                ) reqs.addOne(fieldInfo.getName)
                idx += 1
              }
              val required = reqs.result()
              JsonSchema.obj(
                properties = new Some(ChunkMap.fromChunks(names.result(), schemas.result())),
                required = if (required.nonEmpty) new Some(required) else None,
                additionalProperties = if (doReject) new Some(JsonSchema.False) else None,
                title = new Some(typeName)
              )
            }
          }
        }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[JsonBinaryCodec[A]]]

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      if (typeId.isOption) {
        D.instance(cases(1).value.asRecord.get.fields(0).value.metadata).map { codec =>
          new JsonBinaryCodec[Option[Any]]() {
            private[this] val valueCodec = codec.asInstanceOf[JsonBinaryCodec[Any]]

            override def decodeValue(in: JsonReader, default: Option[Any]): Option[Any] = {
              val isNull = in.isNextToken('n')
              in.rollbackToken()
              try {
                if (isNull) in.readNullOrError(default, "expected null")
                else new Some(valueCodec.decodeValue(in, valueCodec.nullValue))
              } catch {
                case error if NonFatal(error) => decodeError(in, error, isNull)
              }
            }

            override def encodeValue(x: Option[Any], out: JsonWriter): Unit =
              if (x eq None) out.writeNull()
              else valueCodec.encodeValue(x.get, out)

            override def nullValue: Option[String] = None

            private[this] def decodeError(in: JsonReader, error: Throwable, isNull: Boolean): Nothing =
              if (isNull) in.decodeError(new DynamicOptic.Node.Case("None"), error)
              else in.decodeError(new DynamicOptic.Node.Case("Some"), new DynamicOptic.Node.Field("value"), error)

            override lazy val toJsonSchema: JsonSchema = valueCodec.toJsonSchema.withNullable
          }
        }
      } else {
        val discr           = binding.asInstanceOf[Binding.Variant[A]].discriminator
        val variantTypeName = typeId.name
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

          new JsonBinaryCodec[A]() {
            private[this] val root           = new EnumNodeInfo(discr, enumInfos)
            private[this] val constructorMap = map

            def decodeValue(in: JsonReader, default: A): A = {
              val valueLen    = in.readStringAsCharBuf()
              val constructor = constructorMap.get(in, valueLen)
              if (constructor ne null) constructor.construct(null, 0).asInstanceOf[A]
              else in.enumValueError(valueLen)
            }

            def encodeValue(x: A, out: JsonWriter): Unit = root.discriminate(x).writeVal(out)

            override lazy val toJsonSchema: JsonSchema =
              NonEmptyChunk.fromChunk(collectEnumNames(enumInfos, ChunkBuilder.make()).result()) match {
                case Some(enumNames) =>
                  new JsonSchema.Object(`enum` = new Some(enumNames), title = new Some(variantTypeName))
                case _ => JsonSchema.False
              }

            private[this] def collectEnumNames(
              infos: Array[EnumInfo],
              acc: ChunkBuilder[Json.String]
            ): ChunkBuilder[Json.String] = {
              val len = infos.length
              var idx = 0
              while (idx < len) {
                infos(idx) match {
                  case leaf: EnumLeafInfo    => acc.addOne(new Json.String(leaf.enumName))
                  case node: EnumNodeInfo[?] => collectEnumNames(node.enumInfos, acc)
                }
                idx += 1
              }
              acc
            }
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
                      var name: String = null
                      case_.modifiers.foreach {
                        case m: Modifier.rename => if (name eq null) name = m.name
                        case m: Modifier.alias  => map.put(m.name, caseLeafInfo)
                        case _                  =>
                      }
                      if (name eq null) name = caseNameMapper(case_.name)
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

                new JsonBinaryCodec[A]() {
                  private[this] val root                   = new CaseNodeInfo(discr, getInfos(cases, Nil))
                  private[this] val caseMap                = map
                  private[this] val discriminatorFieldName = fieldName

                  def decodeValue(in: JsonReader, default: A): A = {
                    in.setMark()
                    if (in.isNextToken('{')) {
                      if (in.skipToKey(discriminatorFieldName)) {
                        val caseInfo = caseMap.get(in, in.readStringAsCharBuf())
                        if (caseInfo ne null) {
                          in.rollbackToMark()
                          val codec = caseInfo.codec.asInstanceOf[JsonBinaryCodec[A]]
                          try codec.decodeValue(in, codec.nullValue)
                          catch {
                            case error if NonFatal(error) => in.decodeError(caseInfo.spans, error)
                          }
                        } else in.discriminatorValueError(discriminatorFieldName)
                      } else in.requiredFieldError(discriminatorFieldName)
                    } else {
                      in.resetMark()
                      in.readNullOrTokenError(default, '{')
                    }
                  }

                  def encodeValue(x: A, out: JsonWriter): Unit =
                    root.discriminate(x).codec.asInstanceOf[JsonBinaryCodec[A]].encodeValue(x, out)

                  override lazy val toJsonSchema: JsonSchema = {
                    val chunk = collectCaseSchemas(root.caseInfos, ChunkBuilder.make[JsonSchema]()).result()
                    NonEmptyChunk.fromChunk(chunk) match {
                      case Some(caseSchemas) =>
                        new JsonSchema.Object(oneOf = new Some(caseSchemas), title = new Some(variantTypeName))
                      case _ => JsonSchema.False
                    }
                  }

                  private[this] def collectCaseSchemas(
                    infos: Array[CaseInfo],
                    acc: ChunkBuilder[JsonSchema]
                  ): ChunkBuilder[JsonSchema] = {
                    val len = infos.length
                    var idx = 0
                    while (idx < len) {
                      infos(idx) match {
                        case leaf: CaseLeafInfo    => acc.addOne(leaf.codec.toJsonSchema)
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
                val codecs = mutable.ArrayBuilder.make[JsonBinaryCodec[?]]

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

                new JsonBinaryCodec[A]() {
                  private[this] val root           = new CaseNodeInfo(discr, getInfos(cases))
                  private[this] val caseLeafCodecs = codecs.result()

                  def decodeValue(in: JsonReader, default: A): A = {
                    var idx = 0
                    while (idx < caseLeafCodecs.length) {
                      in.setMark()
                      val codec = caseLeafCodecs(idx).asInstanceOf[JsonBinaryCodec[A]]
                      try {
                        val x = codec.decodeValue(in, codec.nullValue)
                        in.resetMark()
                        return x
                      } catch {
                        case error if NonFatal(error) => in.rollbackToMark()
                      }
                      idx += 1
                    }
                    in.decodeError("expected a variant value")
                  }

                  def encodeValue(x: A, out: JsonWriter): Unit =
                    root.discriminate(x).codec.asInstanceOf[JsonBinaryCodec[A]].encodeValue(x, out)

                  override lazy val toJsonSchema: JsonSchema =
                    NonEmptyChunk.fromChunk(Chunk.fromArray(caseLeafCodecs.map(_.toJsonSchema))) match {
                      case Some(caseSchemas) =>
                        new JsonSchema.Object(oneOf = new Some(caseSchemas), title = new Some(variantTypeName))
                      case _ => JsonSchema.False
                    }
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
                    val span        = new DynamicOptic.Node.Case(case_.name)
                    infos(idx) = if (caseReflect.isVariant) {
                      val caseVariant = caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]
                      new CaseNodeInfo(discriminator(caseReflect), getInfos(caseVariant.cases, span :: spans))
                    } else {
                      val caseLeafInfo = new CaseLeafInfo(D.instance(caseReflect.metadata).force, span :: spans)
                      var name: String = null
                      case_.modifiers.foreach {
                        case m: Modifier.rename => if (name eq null) name = m.name
                        case m: Modifier.alias  => map.put(m.name, caseLeafInfo)
                        case _                  =>
                      }
                      if (name eq null) name = caseNameMapper(case_.name)
                      map.put(name, caseLeafInfo)
                      caseLeafInfo.setName(name)
                      caseLeafInfo
                    }
                    idx += 1
                  }
                  infos
                }

                new JsonBinaryCodec[A]() {
                  private[this] val root    = new CaseNodeInfo(discr, getInfos(cases, Nil))
                  private[this] val caseMap = map

                  def decodeValue(in: JsonReader, default: A): A =
                    if (in.isNextToken('{')) {
                      if (!in.isNextToken('}')) {
                        in.rollbackToken()
                        val caseInfo = caseMap.get(in, in.readKeyAsCharBuf())
                        if (caseInfo ne null) {
                          val codec = caseInfo.codec.asInstanceOf[JsonBinaryCodec[A]]
                          val x     =
                            try codec.decodeValue(in, codec.nullValue)
                            catch {
                              case error if NonFatal(error) => in.decodeError(caseInfo.spans, error)
                            }
                          if (!in.isNextToken('}')) in.objectEndOrCommaError()
                          return x
                        }
                      }
                      in.discriminatorError()
                    } else {
                      in.rollbackToken()
                      in.readNullOrTokenError(default, '{')
                    }

                  def encodeValue(x: A, out: JsonWriter): Unit = {
                    out.writeObjectStart()
                    val caseInfo = root.discriminate(x)
                    caseInfo.writeKey(out)
                    caseInfo.codec.asInstanceOf[JsonBinaryCodec[A]].encodeValue(x, out)
                    out.writeObjectEnd()
                  }

                  override lazy val toJsonSchema: JsonSchema = {
                    val chunk = collectCaseSchemas(root.caseInfos, ChunkBuilder.make[JsonSchema]()).result()
                    NonEmptyChunk.fromChunk(chunk) match {
                      case Some(caseSchemas) =>
                        new JsonSchema.Object(oneOf = new Some(caseSchemas), title = new Some(variantTypeName))
                      case _ => JsonSchema.False
                    }
                  }

                  private[this] def collectCaseSchemas(
                    infos: Array[CaseInfo],
                    acc: ChunkBuilder[JsonSchema]
                  ): ChunkBuilder[JsonSchema] = {
                    val len = infos.length
                    var idx = 0
                    while (idx < len) {
                      infos(idx) match {
                        case leaf: CaseLeafInfo =>
                          var innerSchema = leaf.codec.toJsonSchema
                          val name        = leaf.getName
                          if (name ne null) {
                            innerSchema = JsonSchema.obj(
                              properties = new Some(ChunkMap((name, innerSchema))),
                              required = new Some(Set(name)),
                              additionalProperties = new Some(JsonSchema.False)
                            )
                          }
                          acc.addOne(innerSchema)
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
  }.asInstanceOf[Lazy[JsonBinaryCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { codec =>
        codec.valueType match {
          case JsonBinaryCodec.intType if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq intCodec) {
              new JsonBinaryCodec[Col[Int]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader, default: Col[Int]): Col[Int] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
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
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Int] = constructor.empty[Int]
              }
            } else {
              new JsonBinaryCodec[Col[Int]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Int]]

                def decodeValue(in: JsonReader, default: Col[Int]): Col[Int] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Int]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Int] = constructor.empty[Int]
              }
            }
          case JsonBinaryCodec.longType if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq longCodec) {
              new JsonBinaryCodec[Col[Long]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader, default: Col[Long]): Col[Long] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
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
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Long] = constructor.empty[Long]
              }
            } else {
              new JsonBinaryCodec[Col[Long]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Long]]

                def decodeValue(in: JsonReader, default: Col[Long]): Col[Long] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Long]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Long] = constructor.empty[Long]
              }
            }
          case JsonBinaryCodec.floatType if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq floatCodec) {
              new JsonBinaryCodec[Col[Float]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader, default: Col[Float]): Col[Float] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
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
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Float] = constructor.empty[Float]
              }
            } else {
              new JsonBinaryCodec[Col[Float]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Float]]

                def decodeValue(in: JsonReader, default: Col[Float]): Col[Float] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Float]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Float] = constructor.empty[Float]
              }
            }
          case JsonBinaryCodec.doubleType if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq doubleCodec) {
              new JsonBinaryCodec[Col[Double]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader, default: Col[Double]): Col[Double] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
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
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Double] = constructor.empty[Double]
              }
            } else {
              new JsonBinaryCodec[Col[Double]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Double]]

                def decodeValue(in: JsonReader, default: Col[Double]): Col[Double] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Double]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Double] = constructor.empty[Double]
              }
            }
          case JsonBinaryCodec.booleanType if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq booleanCodec) {
              new JsonBinaryCodec[Col[Boolean]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader, default: Col[Boolean]): Col[Boolean] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
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
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Boolean] = constructor.empty[Boolean]
              }
            } else {
              new JsonBinaryCodec[Col[Boolean]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Boolean]]

                def decodeValue(in: JsonReader, default: Col[Boolean]): Col[Boolean] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Boolean]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Boolean] = constructor.empty[Boolean]
              }
            }
          case JsonBinaryCodec.byteType if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq byteCodec) {
              new JsonBinaryCodec[Col[Byte]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader, default: Col[Byte]): Col[Byte] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
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
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Byte] = constructor.empty[Byte]
              }
            } else {
              new JsonBinaryCodec[Col[Byte]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Byte]]

                def decodeValue(in: JsonReader, default: Col[Byte]): Col[Byte] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Byte]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Byte] = constructor.empty[Byte]
              }
            }
          case JsonBinaryCodec.charType if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq charCodec) {
              new JsonBinaryCodec[Col[Char]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader, default: Col[Char]): Col[Char] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
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
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Char] = constructor.empty[Char]
              }
            } else {
              new JsonBinaryCodec[Col[Char]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Char]]

                def decodeValue(in: JsonReader, default: Col[Char]): Col[Char] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Char]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Char] = constructor.empty[Char]
              }
            }
          case JsonBinaryCodec.shortType if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            if (codec eq shortCodec) {
              new JsonBinaryCodec[Col[Short]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor

                def decodeValue(in: JsonReader, default: Col[Short]): Col[Short] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
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
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Short] = constructor.empty[Short]
              }
            } else {
              new JsonBinaryCodec[Col[Short]]() {
                private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                private[this] val constructor   = seqBinding.constructor
                private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Short]]

                def decodeValue(in: JsonReader, default: Col[Short]): Col[Short] =
                  if (in.isNextToken('[')) {
                    if (in.isNextToken(']')) default
                    else {
                      in.rollbackToken()
                      val builder = constructor.newBuilder[Short]()
                      var idx     = -1
                      try {
                        while ({
                          idx += 1
                          constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      } catch {
                        case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                      if (in.isCurrentToken(']')) constructor.result(builder)
                      else in.arrayEndOrCommaError()
                    }
                  } else {
                    in.rollbackToken()
                    in.readNullOrTokenError(default, '[')
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

                override def nullValue: Col[Short] = constructor.empty[Short]
              }
            }
          case _ =>
            new JsonBinaryCodec[Col[Elem]]() {
              private[this] val deconstructor = seqBinding.deconstructor
              private[this] val constructor   = seqBinding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Elem]]
              private[this] val elemClassTag  = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

              def decodeValue(in: JsonReader, default: Col[Elem]): Col[Elem] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newBuilder[Elem](8)(elemClassTag)
                    var idx     = -1
                    try {
                      while ({
                        idx += 1
                        constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                        in.isNextToken(',')
                      }) ()
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.result[Elem](builder)
                    else in.arrayEndOrCommaError()
                  }
                } else {
                  in.rollbackToken()
                  in.readNullOrTokenError(default, '[')
                }

              def encodeValue(x: Col[Elem], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val it = deconstructor.deconstruct(x)
                while (it.hasNext) elementCodec.encodeValue(it.next(), out)
                out.writeArrayEnd()
              }

              override def nullValue: Col[Elem] = constructor.empty[Elem](elemClassTag)

              override lazy val toJsonSchema: JsonSchema = JsonSchema.array(items = new Some(elementCodec.toJsonSchema))
            }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[JsonBinaryCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
        new JsonBinaryCodec[Map[Key, Value]]() {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val keyCodec      = codec1.asInstanceOf[JsonBinaryCodec[Key]]
          private[this] val valueCodec    = codec2.asInstanceOf[JsonBinaryCodec[Value]]
          private[this] val keyReflect    = key.asInstanceOf[Reflect.Bound[Key]]

          def decodeValue(in: JsonReader, default: Map[Key, Value]): Map[Key, Value] =
            if (in.isNextToken('{')) {
              if (in.isNextToken('}')) default
              else {
                in.rollbackToken()
                val builder = constructor.newObjectBuilder[Key, Value](8)
                var idx     = -1
                while ({
                  idx += 1
                  val k =
                    try keyCodec.decodeKey(in)
                    catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                  val v =
                    try valueCodec.decodeValue(in, valueCodec.nullValue)
                    catch {
                      case error if NonFatal(error) =>
                        in.decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), error)
                    }
                  constructor.addObject(builder, k, v)
                  in.isNextToken(',')
                }) ()
                if (in.isCurrentToken('}')) constructor.resultObject[Key, Value](builder)
                else in.objectEndOrCommaError()
              }
            } else {
              in.rollbackToken()
              in.readNullOrTokenError(default, '{')
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

          override def nullValue: Map[Key, Value] = constructor.emptyObject[Key, Value]

          override lazy val toJsonSchema: JsonSchema =
            JsonSchema.obj(additionalProperties = new Some(valueCodec.toJsonSchema))
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[JsonBinaryCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[DynamicValue]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[JsonBinaryCodec[DynamicValue]]]

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
      D.instance(wrapped.metadata).map { codec =>
        new JsonBinaryCodec[A](PrimitiveType.fromTypeId(typeId).fold(JsonBinaryCodec.objectType) {
          case _: PrimitiveType.Boolean   => JsonBinaryCodec.booleanType
          case _: PrimitiveType.Byte      => JsonBinaryCodec.byteType
          case _: PrimitiveType.Char      => JsonBinaryCodec.charType
          case _: PrimitiveType.Short     => JsonBinaryCodec.shortType
          case _: PrimitiveType.Float     => JsonBinaryCodec.floatType
          case _: PrimitiveType.Int       => JsonBinaryCodec.intType
          case _: PrimitiveType.Double    => JsonBinaryCodec.doubleType
          case _: PrimitiveType.Long      => JsonBinaryCodec.longType
          case _: PrimitiveType.Unit.type => JsonBinaryCodec.unitType
          case _                          => JsonBinaryCodec.objectType
        }) {
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val wrappedCodec = codec.asInstanceOf[JsonBinaryCodec[Wrapped]]

          override def decodeValue(in: JsonReader, default: A): A =
            try {
              wrap(
                wrappedCodec.decodeValue(
                  in, {
                    if (default == null) null
                    else unwrap(default)
                  }.asInstanceOf[Wrapped]
                )
              )
            } catch {
              case error if NonFatal(error) => in.decodeError(DynamicOptic.Node.Wrapped, error)
            }

          override def encodeValue(x: A, out: JsonWriter): Unit =
            try wrappedCodec.encodeValue(unwrap(x), out)
            catch {
              case error if NonFatal(error) => out.encodeError(error.getMessage)
            }

          override def decodeKey(in: JsonReader): A =
            try wrap(wrappedCodec.decodeKey(in))
            catch {
              case error if NonFatal(error) => in.decodeError(DynamicOptic.Node.Wrapped, error)
            }

          override def encodeKey(x: A, out: JsonWriter): Unit =
            try wrappedCodec.encodeKey(unwrap(x), out)
            catch {
              case error if NonFatal(error) => out.encodeError(error.getMessage)
            }

          override def toJsonSchema: JsonSchema = wrappedCodec.toJsonSchema
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[JsonBinaryCodec[A]]]

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
      override def initialValue: java.util.HashMap[TypeId[?], Array[FieldInfo]] =
        new java.util.HashMap
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

  private[this] def isOptional[F[_, _], A](reflect: Reflect[F, A]): Boolean = !requireOptionFields && reflect.isOption

  private[this] def isCollection[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    !requireCollectionFields && reflect.isCollection

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
  val idx: Int,
  val isOptional: Boolean,
  val isCollection: Boolean
) {
  private[this] var codec: JsonBinaryCodec[?]      = null
  private[this] var name: String                   = null
  private[this] var offset: RegisterOffset         = 0
  var nonTransient: Boolean                        = true
  private[this] var isPredefinedCodec: Boolean     = false
  private[this] var isNonEscapedAsciiName: Boolean = false

  def setName(name: String): Unit = {
    isNonEscapedAsciiName = JsonWriter.isNonEscapedAscii(name)
    this.name = name
  }

  def setCodec(codec: JsonBinaryCodec[?]): Unit = {
    isPredefinedCodec =
      (codec eq intCodec) || (codec eq longCodec) || (codec eq floatCodec) || (codec eq doubleCodec) ||
        (codec eq booleanCodec) || (codec eq byteCodec) || (codec eq charCodec) || (codec eq shortCodec)
    this.codec = codec
  }

  def setOffset(offset: RegisterOffset): Unit = this.offset = offset

  def getName: String = name

  def getCodec: JsonBinaryCodec[?] = codec

  @inline
  def hasDefault: Boolean = defaultValue ne None

  @inline
  def nameMatch(in: JsonReader, keyLen: Int): Boolean = in.isCharBufEqualsTo(keyLen, name)

  def usedRegisters: RegisterOffset = codec.valueOffset + offset

  def readValue(in: JsonReader, regs: Registers, top: RegisterOffset): Unit = {
    val offset = this.offset + top
    (codec.valueType: @switch) match {
      case 0 =>
        regs.setObject(
          offset,
          codec
            .asInstanceOf[JsonBinaryCodec[AnyRef]]
            .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[AnyRef]].nullValue)
        )
      case 1 =>
        regs.setInt(
          offset,
          if (isPredefinedCodec) in.readInt()
          else {
            codec.asInstanceOf[JsonBinaryCodec[Int]].decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Int]].nullValue)
          }
        )
      case 2 =>
        regs.setLong(
          offset,
          if (isPredefinedCodec) in.readLong()
          else {
            codec
              .asInstanceOf[JsonBinaryCodec[Long]]
              .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Long]].nullValue)
          }
        )
      case 3 =>
        regs.setFloat(
          offset,
          if (isPredefinedCodec) in.readFloat()
          else {
            codec
              .asInstanceOf[JsonBinaryCodec[Float]]
              .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Float]].nullValue)
          }
        )
      case 4 =>
        regs.setDouble(
          offset,
          if (isPredefinedCodec) in.readDouble()
          else {
            codec
              .asInstanceOf[JsonBinaryCodec[Double]]
              .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Double]].nullValue)
          }
        )
      case 5 =>
        regs.setBoolean(
          offset,
          if (isPredefinedCodec) in.readBoolean()
          else {
            codec
              .asInstanceOf[JsonBinaryCodec[Boolean]]
              .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Boolean]].nullValue)
          }
        )
      case 6 =>
        regs.setByte(
          offset,
          if (isPredefinedCodec) in.readByte()
          else {
            codec
              .asInstanceOf[JsonBinaryCodec[Byte]]
              .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Byte]].nullValue)
          }
        )
      case 7 =>
        regs.setChar(
          offset,
          if (isPredefinedCodec) in.readChar()
          else {
            codec
              .asInstanceOf[JsonBinaryCodec[Char]]
              .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Char]].nullValue)
          }
        )
      case 8 =>
        regs.setShort(
          offset,
          if (isPredefinedCodec) in.readShort()
          else {
            codec
              .asInstanceOf[JsonBinaryCodec[Short]]
              .decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Short]].nullValue)
          }
        )
      case _ =>
        codec.asInstanceOf[JsonBinaryCodec[Unit]].decodeValue(in, codec.asInstanceOf[JsonBinaryCodec[Unit]].nullValue)
    }
  }

  def setMissingValueOrError(in: JsonReader, regs: Registers, top: RegisterOffset): Unit = {
    val offset = this.offset + top
    if (defaultValue ne None) {
      val dv = defaultValue.get
      (codec.valueType: @switch) match {
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
    } else if (isOptional) regs.setObject(offset, None)
    else if (isCollection) regs.setObject(offset, codec.nullValue.asInstanceOf[AnyRef])
    else in.requiredFieldError(name)
  }

  def writeDefaultValue(out: JsonWriter, regs: Registers, top: RegisterOffset): Unit = {
    val offset = this.offset + top
    val dv     = defaultValue.get
    (codec.valueType: @switch) match {
      case 0 =>
        val value = regs.getObject(offset)
        if (dv != value) {
          writeKey(out)
          codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(value, out)
        }
      case 1 =>
        val value = regs.getInt(offset)
        if (dv.asInstanceOf[Int] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonBinaryCodec[Int]].encodeValue(value, out)
        }
      case 2 =>
        val value = regs.getLong(offset)
        if (dv.asInstanceOf[Long] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonBinaryCodec[Long]].encodeValue(value, out)
        }
      case 3 =>
        val value = regs.getFloat(offset)
        if (dv.asInstanceOf[Float] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonBinaryCodec[Float]].encodeValue(value, out)
        }
      case 4 =>
        val value = regs.getDouble(offset)
        if (dv.asInstanceOf[Double] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonBinaryCodec[Double]].encodeValue(value, out)
        }
      case 5 =>
        val value = regs.getBoolean(offset)
        if (dv.asInstanceOf[Boolean] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonBinaryCodec[Boolean]].encodeValue(value, out)
        }
      case 6 =>
        val value = regs.getByte(offset)
        if (dv.asInstanceOf[Byte] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonBinaryCodec[Byte]].encodeValue(value, out)
        }
      case 7 =>
        val value = regs.getChar(offset)
        if (dv.asInstanceOf[Char] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonBinaryCodec[Char]].encodeValue(value, out)
        }
      case 8 =>
        val value = regs.getShort(offset)
        if (dv.asInstanceOf[Short] != value) {
          writeKey(out)
          if (isPredefinedCodec) out.writeVal(value)
          else codec.asInstanceOf[JsonBinaryCodec[Short]].encodeValue(value, out)
        }
      case _ =>
    }
  }

  def writeOptional(out: JsonWriter, regs: Registers, top: RegisterOffset): Unit = {
    val value = regs.getObject(offset + top)
    if (value ne None) {
      writeKey(out)
      codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(value, out)
    }
  }

  def writeCollection(out: JsonWriter, regs: Registers, top: RegisterOffset): Unit =
    regs.getObject(offset + top) match {
      case value: Iterable[?] =>
        if (value.nonEmpty) {
          writeKey(out)
          codec.asInstanceOf[JsonBinaryCodec[Iterable[?]]].encodeValue(value, out)
        }
      case value: Array[?] =>
        if (value.length > 0) {
          writeKey(out)
          codec.asInstanceOf[JsonBinaryCodec[Array[?]]].encodeValue(value, out)
        }
    }

  def writeRequired(out: JsonWriter, regs: Registers, top: RegisterOffset): Unit = {
    writeKey(out)
    val offset = this.offset + top
    (codec.valueType: @switch) match {
      case 0 =>
        codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(regs.getObject(offset), out)
      case 1 =>
        val value = regs.getInt(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonBinaryCodec[Int]].encodeValue(value, out)
      case 2 =>
        val value = regs.getLong(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonBinaryCodec[Long]].encodeValue(value, out)
      case 3 =>
        val value = regs.getFloat(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonBinaryCodec[Float]].encodeValue(value, out)
      case 4 =>
        val value = regs.getDouble(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonBinaryCodec[Double]].encodeValue(value, out)
      case 5 =>
        val value = regs.getBoolean(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonBinaryCodec[Boolean]].encodeValue(value, out)
      case 6 =>
        val value = regs.getByte(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonBinaryCodec[Byte]].encodeValue(value, out)
      case 7 =>
        val value = regs.getChar(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonBinaryCodec[Char]].encodeValue(value, out)
      case 8 =>
        val value = regs.getShort(offset)
        if (isPredefinedCodec) out.writeVal(value)
        else codec.asInstanceOf[JsonBinaryCodec[Short]].encodeValue(value, out)
      case _ => codec.asInstanceOf[JsonBinaryCodec[Unit]].encodeValue((), out)
    }
  }

  @inline
  private[this] def writeKey(out: JsonWriter): Unit =
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
    else out.writeKey(name)
}

private class DiscriminatorFieldInfo(name: String, value: String) {
  private[this] val isNonEscapedAsciiName  = JsonWriter.isNonEscapedAscii(name)
  private[this] val isNonEscapedAsciiValue = JsonWriter.isNonEscapedAscii(value)

  @inline
  def nameMatch(in: JsonReader, keyLen: Int): Boolean = in.isCharBufEqualsTo(keyLen, name)

  @inline
  def writeKeyAndValue(out: JsonWriter): Unit = {
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
    else out.writeKey(name)
    if (isNonEscapedAsciiValue) out.writeNonEscapedAsciiVal(value)
    else out.writeVal(value)
  }
}

trait CaseInfo

private class CaseLeafInfo(
  var codec: JsonBinaryCodec[?],
  val spans: List[DynamicOptic.Node.Case]
) extends CaseInfo {
  private[this] var name: String                   = null
  private[this] var isNonEscapedAsciiName: Boolean = false

  def setName(name: String): Unit = {
    isNonEscapedAsciiName = JsonWriter.isNonEscapedAscii(name)
    this.name = name
  }

  def getName: String = name

  @inline
  def writeKey(out: JsonWriter): Unit =
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
    else out.writeKey(name)
}

private class CaseNodeInfo[A](
  private[this] val discriminator: Discriminator[A],
  private[this] val caseInfosArray: Array[CaseInfo]
) extends CaseInfo {
  def caseInfos: Array[CaseInfo] = caseInfosArray

  @tailrec
  final def discriminate(x: A): CaseLeafInfo = caseInfosArray(discriminator.discriminate(x)) match {
    case eli: CaseLeafInfo => eli
    case eni               => eni.asInstanceOf[CaseNodeInfo[A]].discriminate(x)
  }
}

trait EnumInfo

private class EnumLeafInfo(name: String, val constructor: Constructor[?]) extends EnumInfo {
  private[this] val isNonEscapedAsciiName = JsonWriter.isNonEscapedAscii(name)

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
