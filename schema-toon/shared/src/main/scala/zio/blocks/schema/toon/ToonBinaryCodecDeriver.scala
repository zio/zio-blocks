package zio.blocks.schema.toon

import zio.blocks.schema.toon._
import zio.blocks.schema.toon.ToonBinaryCodec._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema._
import zio.blocks.schema.binding.Discriminator
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.SeqDeconstructor.SpecializedIndexed
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}

/**
 * Provides a default implementation of `ToonBinaryCodecDeriver` with
 * customizable settings for TOON codec derivation. This object allows the
 * derivation of codecs for various data types, including primitives, records,
 * variants, sequences, maps, and dynamic values.
 *
 * The derivation process can be customized through pre-configured parameters:
 *   - `fieldNameMapper`: Controls how field names are transformed
 *   - `caseNameMapper`: Controls how case names in variants are transformed
 *   - `discriminatorKind`: Determines the strategy for type discriminators
 *   - `arrayFormat`: Controls how arrays are formatted (Auto, Inline, Tabular,
 *     List)
 *   - `rejectExtraFields`: Specifies if unrecognized fields cause errors
 *   - `enumValuesAsStrings`: Whether enum values are represented as strings
 *   - `transientNone`: Excludes None fields during serialization
 *   - `transientEmptyCollection`: Excludes empty collections during
 *     serialization
 *   - `transientDefaultValue`: Excludes default values during serialization
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 */
object ToonBinaryCodecDeriver
    extends ToonBinaryCodecDeriver(
      fieldNameMapper = NameMapper.Identity,
      caseNameMapper = NameMapper.Identity,
      discriminatorKind = DiscriminatorKind.Key,
      arrayFormat = ArrayFormat.Auto,
      rejectExtraFields = false,
      enumValuesAsStrings = true,
      transientNone = true,
      requireOptionFields = false,
      transientEmptyCollection = true,
      requireCollectionFields = false,
      transientDefaultValue = true,
      requireDefaultValueFields = false
    )

/**
 * Deriver for creating `ToonBinaryCodec` instances from ZIO Schema types.
 *
 * This class provides methods for deriving codecs for all supported types
 * including primitives, records, variants (ADTs), sequences, maps, and dynamic
 * values.
 */
class ToonBinaryCodecDeriver private[toon] (
  fieldNameMapper: NameMapper,
  caseNameMapper: NameMapper,
  discriminatorKind: DiscriminatorKind,
  arrayFormat: ArrayFormat,
  rejectExtraFields: Boolean,
  enumValuesAsStrings: Boolean,
  transientNone: Boolean,
  requireOptionFields: Boolean,
  transientEmptyCollection: Boolean,
  requireCollectionFields: Boolean,
  transientDefaultValue: Boolean,
  requireDefaultValueFields: Boolean
) extends Deriver[ToonBinaryCodec] {

  /**
   * Updates the deriver with a new field name mapper.
   */
  def withFieldNameMapper(fieldNameMapper: NameMapper): ToonBinaryCodecDeriver =
    copy(fieldNameMapper = fieldNameMapper)

  /**
   * Updates the deriver with a new case name mapper.
   */
  def withCaseNameMapper(caseNameMapper: NameMapper): ToonBinaryCodecDeriver =
    copy(caseNameMapper = caseNameMapper)

  /**
   * Updates the deriver with a new discriminator kind.
   */
  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): ToonBinaryCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  /**
   * Updates the deriver with a specific array format.
   */
  def withArrayFormat(arrayFormat: ArrayFormat): ToonBinaryCodecDeriver =
    copy(arrayFormat = arrayFormat)

  /**
   * Sets whether to reject extra fields during decoding.
   */
  def withRejectExtraFields(rejectExtraFields: Boolean): ToonBinaryCodecDeriver =
    copy(rejectExtraFields = rejectExtraFields)

  /**
   * Sets whether enum values are serialized as strings.
   */
  def withEnumValuesAsStrings(enumValuesAsStrings: Boolean): ToonBinaryCodecDeriver =
    copy(enumValuesAsStrings = enumValuesAsStrings)

  /**
   * Sets whether None values are excluded during encoding.
   */
  def withTransientNone(transientNone: Boolean): ToonBinaryCodecDeriver =
    copy(transientNone = transientNone)

  /**
   * Sets whether Option fields are required during decoding.
   */
  def withRequireOptionFields(requireOptionFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireOptionFields = requireOptionFields)

  /**
   * Sets whether empty collections are excluded during encoding.
   */
  def withTransientEmptyCollection(transientEmptyCollection: Boolean): ToonBinaryCodecDeriver =
    copy(transientEmptyCollection = transientEmptyCollection)

  /**
   * Sets whether collection fields are required during decoding.
   */
  def withRequireCollectionFields(requireCollectionFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireCollectionFields = requireCollectionFields)

  /**
   * Sets whether fields with default values are excluded during encoding.
   */
  def withTransientDefaultValue(transientDefaultValue: Boolean): ToonBinaryCodecDeriver =
    copy(transientDefaultValue = transientDefaultValue)

  /**
   * Sets whether fields with default values are required during decoding.
   */
  def withRequireDefaultValueFields(requireDefaultValueFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireDefaultValueFields = requireDefaultValueFields)

  private def copy(
    fieldNameMapper: NameMapper = fieldNameMapper,
    caseNameMapper: NameMapper = caseNameMapper,
    discriminatorKind: DiscriminatorKind = discriminatorKind,
    arrayFormat: ArrayFormat = arrayFormat,
    rejectExtraFields: Boolean = rejectExtraFields,
    enumValuesAsStrings: Boolean = enumValuesAsStrings,
    transientNone: Boolean = transientNone,
    requireOptionFields: Boolean = requireOptionFields,
    transientEmptyCollection: Boolean = transientEmptyCollection,
    requireCollectionFields: Boolean = requireCollectionFields,
    transientDefaultValue: Boolean = transientDefaultValue,
    requireDefaultValueFields: Boolean = requireDefaultValueFields
  ) =
    new ToonBinaryCodecDeriver(
      fieldNameMapper,
      caseNameMapper,
      discriminatorKind,
      arrayFormat,
      rejectExtraFields,
      enumValuesAsStrings,
      transientNone,
      requireOptionFields,
      transientEmptyCollection,
      requireCollectionFields,
      transientDefaultValue,
      requireDefaultValueFields
    )

  override def derivePrimitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  ): Lazy[ToonBinaryCodec[A]] =
    Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
    deriveCodec(
      new Reflect.Record(
        fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
        typeName,
        binding,
        doc,
        modifiers
      )
    )
  }

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
    deriveCodec(
      new Reflect.Variant(
        cases.asInstanceOf[IndexedSeq[Term[Binding, A, ? <: A]]],
        typeName,
        binding,
        doc,
        modifiers
      )
    )
  }

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeName: TypeName[C[A]],
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[C[A]]] = Lazy {
    deriveCodec(
      new Reflect.Sequence(element.asInstanceOf[Reflect[Binding, A]], typeName, binding, doc, modifiers)
    )
  }

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeName: TypeName[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[M[K, V]]] = Lazy {
    deriveCodec(
      new Reflect.Map(
        key.asInstanceOf[Reflect[Binding, K]],
        value.asInstanceOf[Reflect[Binding, V]],
        typeName,
        binding,
        doc,
        modifiers
      )
    )
  }

  override def deriveDynamic[F[_, _]](
    binding: Binding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[DynamicValue]] =
    Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeName: TypeName[A],
    wrapperPrimitiveType: Option[PrimitiveType[A]],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
    deriveCodec(
      new Reflect.Wrapper(
        wrapped.asInstanceOf[Reflect[Binding, B]],
        typeName,
        wrapperPrimitiveType,
        binding,
        doc,
        modifiers
      )
    )
  }

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

  private[this] val recursiveRecordCache = new ThreadLocal[java.util.HashMap[TypeName[?], FieldInfoArray]] {
    override def initialValue: java.util.HashMap[TypeName[?], FieldInfoArray] = new java.util.HashMap
  }

  private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] =
    if (reflect.isPrimitive) {
      val primitive = reflect.asPrimitive.get
      if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
        primitive.primitiveType match {
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
        }
      }.asInstanceOf[ToonBinaryCodec[A]]
      else {
        // Custom primitive with binding instance
        primitive.primitiveBinding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance.force
      }
    } else if (reflect.isRecord) {
      deriveRecordCodec(reflect.asRecord.get.asInstanceOf[Reflect.Record[Binding, A]])
    } else if (reflect.isVariant) {
      deriveVariantCodec(reflect.asVariant.get.asInstanceOf[Reflect.Variant[Binding, A]])
    } else if (reflect.isSequence) {
      val sequence = reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Elem, Col]]
      deriveSequenceCodec(sequence).asInstanceOf[ToonBinaryCodec[A]]
    } else if (reflect.isMap) {
      val map = reflect.asMapUnknown.get.map.asInstanceOf[Reflect.Map[Binding, Key, Value, Map]]
      deriveMapCodec(map).asInstanceOf[ToonBinaryCodec[A]]
    } else if (reflect.isDynamic) {
      deriveDynamicCodec().asInstanceOf[ToonBinaryCodec[A]]
    } else if (reflect.isWrapper) {
      val wrapper = reflect.asWrapperUnknown.get.wrapper.asInstanceOf[Reflect.Wrapper[Binding, ?, ?]]
      deriveWrapperCodec(wrapper).asInstanceOf[ToonBinaryCodec[A]]
    } else {
      throw new UnsupportedOperationException(s"Cannot derive TOON codec for: $reflect")
    }

  private[this] def deriveRecordCodec[A](record: Reflect.Record[Binding, A]): ToonBinaryCodec[A] = {
    val fields        = record.fields
    val fieldCount    = fields.length
    val binding       = record.recordBinding.asInstanceOf[Binding.Record[A]]
    val constructor   = binding.constructor
    val deconstructor = binding.deconstructor
    val usedRegisters = constructor.usedRegisters
    val fieldNames    = new Array[String](fieldCount)
    val fieldCodecs   = new Array[ToonBinaryCodec[Any]](fieldCount)
    val fieldOffsets  = new Array[RegisterOffset](fieldCount)
    val fieldDefaults = new Array[Option[Any]](fieldCount)
    val isOptionals   = new Array[Boolean](fieldCount)
    val isSequences   = new Array[Boolean](fieldCount)
    val hasDefaults   = new Array[Boolean](fieldCount)

    // Check for recursive reference
    val cache            = recursiveRecordCache.get
    val cachedFieldInfos = cache.get(record.typeName)
    if (cachedFieldInfos != null) {
      return new ToonBinaryCodec[A]() {
        def decodeValue(in: ToonReader, default: A): A = {
          val top = in.push(usedRegisters)
          try {
            val regs = in.registers
            decodeRecordIntoRegisters(in, cachedFieldInfos, regs, top)
            constructor.construct(regs, top)
          } finally in.pop(usedRegisters)
        }
        def encodeValue(x: A, out: ToonWriter): Unit = {
          val top = out.push(usedRegisters)
          try {
            val regs = out.registers
            deconstructor.deconstruct(regs, top, x)
            encodeRecordFromRegisters(out, cachedFieldInfos, regs, top)
          } finally out.pop(usedRegisters)
        }
      }
    }

    // Build field info
    var offset: RegisterOffset = 0L
    var idx                    = 0
    while (idx < fieldCount) {
      val field        = fields(idx)
      val fieldName    = fieldNameMapper.map(field.name)
      val fieldReflect = field.value.asInstanceOf[Reflect[Binding, Any]]
      val fieldCodec   = deriveCodec(fieldReflect)
      val fieldDefault = getDefaultValue(fieldReflect)

      fieldNames(idx) = fieldName
      fieldCodecs(idx) = fieldCodec
      fieldOffsets(idx) = offset
      fieldDefaults(idx) = fieldDefault
      isOptionals(idx) = transientNone && fieldReflect.isWrapper
      isSequences(idx) = transientEmptyCollection && fieldReflect.isSequence
      hasDefaults(idx) = transientDefaultValue && fieldDefault.isDefined

      offset = RegisterOffset.add(fieldCodec.valueOffset, offset)
      idx += 1
    }

    val fieldInfos = FieldInfoArray(
      fieldNames,
      fieldCodecs,
      fieldOffsets,
      fieldDefaults,
      isOptionals,
      isSequences,
      hasDefaults,
      fieldCount
    )
    cache.put(record.typeName, fieldInfos)

    new ToonBinaryCodec[A]() {
      def decodeValue(in: ToonReader, default: A): A = {
        val top = in.push(usedRegisters)
        try {
          val regs = in.registers
          decodeRecordIntoRegisters(in, fieldInfos, regs, top)
          constructor.construct(regs, top)
        } finally in.pop(usedRegisters)
      }

      def encodeValue(x: A, out: ToonWriter): Unit = {
        val top = out.push(usedRegisters)
        try {
          val regs = out.registers
          deconstructor.deconstruct(regs, top, x)
          encodeRecordFromRegisters(out, fieldInfos, regs, top)
        } finally out.pop(usedRegisters)
      }
    }
  }

  private[this] def getDefaultValue[F[_, _], A](reflect: Reflect[F, A]): Option[Any] =
    if (reflect.isPrimitive) reflect.asPrimitive.get.primitiveBinding match {
      case b: Binding[?, ?] => b.asInstanceOf[Binding[?, A]].defaultValue.map(f => f())
      case _                => None
    }
    else if (reflect.isRecord) reflect.asRecord.get.recordBinding match {
      case b: Binding[?, ?] => b.asInstanceOf[Binding[?, A]].defaultValue.map(f => f())
      case _                => None
    }
    else None

  private[this] def decodeRecordIntoRegisters(
    in: ToonReader,
    infos: FieldInfoArray,
    regs: Registers,
    top: RegisterOffset
  ): Unit = {
    val fieldSet = new Array[Boolean](infos.count)
    in.readObjectStart()
    var continue = true
    while (continue) {
      val key = in.readKeyOrEnd()
      if (key == null) {
        continue = false
      } else {
        var found = false
        var idx   = 0
        while (idx < infos.count && !found) {
          if (infos.names(idx) == key) {
            found = true
            fieldSet(idx) = true
            val codec       = infos.codecs(idx)
            val fieldOffset = top + infos.offsets(idx)

            // Set value based on codec's value type
            (codec.valueType: @scala.annotation.switch) match {
              case 0 =>
                val value = codec.decodeValue(in, codec.nullValue)
                regs.setObject(fieldOffset, value.asInstanceOf[AnyRef])
              case 1 =>
                val value = codec.asInstanceOf[ToonBinaryCodec[Int]].decodeValue(in, 0)
                regs.setInt(fieldOffset, value)
              case 2 =>
                val value = codec.asInstanceOf[ToonBinaryCodec[Long]].decodeValue(in, 0L)
                regs.setLong(fieldOffset, value)
              case 3 =>
                val value = codec.asInstanceOf[ToonBinaryCodec[Float]].decodeValue(in, 0.0f)
                regs.setFloat(fieldOffset, value)
              case 4 =>
                val value = codec.asInstanceOf[ToonBinaryCodec[Double]].decodeValue(in, 0.0)
                regs.setDouble(fieldOffset, value)
              case 5 =>
                val value = codec.asInstanceOf[ToonBinaryCodec[Boolean]].decodeValue(in, false)
                regs.setBoolean(fieldOffset, value)
              case 6 =>
                val value = codec.asInstanceOf[ToonBinaryCodec[Byte]].decodeValue(in, 0.toByte)
                regs.setByte(fieldOffset, value)
              case 7 =>
                val value = codec.asInstanceOf[ToonBinaryCodec[Char]].decodeValue(in, '\u0000')
                regs.setChar(fieldOffset, value)
              case 8 =>
                val value = codec.asInstanceOf[ToonBinaryCodec[Short]].decodeValue(in, 0.toShort)
                regs.setShort(fieldOffset, value)
              case _ =>
                // Unit - just decode and ignore
                codec.decodeValue(in, codec.nullValue)
            }
          }
          idx += 1
        }
        if (!found) {
          if (rejectExtraFields) in.decodeError(s"unexpected field: $key")
          else in.skipValue()
        }
      }
    }
    // Apply defaults for missing fields
    var idx = 0
    while (idx < infos.count) {
      if (!fieldSet(idx) && infos.defaults(idx).isDefined) {
        val codec       = infos.codecs(idx)
        val fieldOffset = top + infos.offsets(idx)
        val defaultVal  = infos.defaults(idx).get

        // Set default value based on codec's value type
        (codec.valueType: @scala.annotation.switch) match {
          case 0 => regs.setObject(fieldOffset, defaultVal.asInstanceOf[AnyRef])
          case 1 => regs.setInt(fieldOffset, defaultVal.asInstanceOf[Int])
          case 2 => regs.setLong(fieldOffset, defaultVal.asInstanceOf[Long])
          case 3 => regs.setFloat(fieldOffset, defaultVal.asInstanceOf[Float])
          case 4 => regs.setDouble(fieldOffset, defaultVal.asInstanceOf[Double])
          case 5 => regs.setBoolean(fieldOffset, defaultVal.asInstanceOf[Boolean])
          case 6 => regs.setByte(fieldOffset, defaultVal.asInstanceOf[Byte])
          case 7 => regs.setChar(fieldOffset, defaultVal.asInstanceOf[Char])
          case 8 => regs.setShort(fieldOffset, defaultVal.asInstanceOf[Short])
          case _ => // Unit - nothing to set
        }
      }
      idx += 1
    }
  }

  private[this] def encodeRecordFromRegisters(
    out: ToonWriter,
    infos: FieldInfoArray,
    regs: Registers,
    top: RegisterOffset
  ): Unit = {
    out.writeObjectStart()
    var idx    = 0
    var first  = true
    var offset = top
    while (idx < infos.count) {
      val codec = infos.codecs(idx)

      // Get value based on codec's value type
      val value: Any = (codec.valueType: @scala.annotation.switch) match {
        case 0 => regs.getObject(offset)  // object
        case 1 => regs.getInt(offset)     // int
        case 2 => regs.getLong(offset)    // long
        case 3 => regs.getFloat(offset)   // float
        case 4 => regs.getDouble(offset)  // double
        case 5 => regs.getBoolean(offset) // boolean
        case 6 => regs.getByte(offset)    // byte
        case 7 => regs.getChar(offset)    // char
        case 8 => regs.getShort(offset)   // short
        case _ => ()                      // Unit
      }

      val shouldSkip =
        (infos.isOptionals(idx) && value == None) ||
          (infos.isSequences(idx) && isEmptyCollection(value)) ||
          (infos.hasDefaults(idx) && infos.defaults(idx).contains(value))

      if (!shouldSkip) {
        if (!first) out.writeFieldSeparator()
        out.writeKey(infos.names(idx))
        codec.encodeValue(value.asInstanceOf[Any], out)
        first = false
      }
      offset += codec.valueOffset
      idx += 1
    }
    out.writeObjectEnd()
  }

  private[this] def isEmptyCollection(value: Any): Boolean = value match {
    case seq: scala.collection.Seq[?]    => seq.isEmpty
    case set: scala.collection.Set[?]    => set.isEmpty
    case map: scala.collection.Map[?, ?] => map.isEmpty
    case arr: Array[?]                   => arr.isEmpty
    case _                               => false
  }

  private[this] def deriveVariantCodec[A](variant: Reflect.Variant[Binding, A]): ToonBinaryCodec[A] = {
    val variantBinding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
    val discriminator  = variantBinding.discriminator
    val cases          = variant.cases
    val caseCount      = cases.length
    val caseInfos      = new Array[CaseInfo](caseCount)

    var idx = 0
    while (idx < caseCount) {
      val c           = cases(idx)
      val caseName    = caseNameMapper.map(c.name)
      val caseReflect = c.value.asInstanceOf[Reflect[Binding, A]]
      val caseCodec   = deriveCodec(caseReflect).asInstanceOf[ToonBinaryCodec[Any]]

      caseInfos(idx) = CaseInfo(
        name = caseName,
        codec = caseCodec,
        discriminator = discriminator.asInstanceOf[Discriminator[Any]],
        isEnum = caseReflect.isRecord && caseReflect.asRecord.get.fields.isEmpty
      )
      idx += 1
    }

    new ToonBinaryCodec[A]() {
      def decodeValue(in: ToonReader, default: A): A =
        discriminatorKind match {
          case DiscriminatorKind.Key =>
            in.readObjectStart()
            val key       = in.readKey()
            var result: A = default
            var found     = false
            var idx       = 0
            while (idx < caseInfos.length && !found) {
              val info = caseInfos(idx)
              if (info.name == key) {
                found = true
                result = info.codec.decodeValue(in, info.codec.nullValue).asInstanceOf[A]
              }
              idx += 1
            }
            if (!found) in.decodeError(s"unknown variant case: $key")
            in.readObjectEnd()
            result

          case DiscriminatorKind.Field(fieldName) =>
            in.readObjectStart()
            val discValue = in.peekDiscriminatorField(fieldName)
            var result: A = default
            var found     = false
            var idx       = 0
            while (idx < caseInfos.length && !found) {
              val info = caseInfos(idx)
              if (info.name == discValue) {
                found = true
                result = info.codec.decodeValue(in, info.codec.nullValue).asInstanceOf[A]
              }
              idx += 1
            }
            if (!found) in.decodeError(s"unknown variant case: $discValue")
            result

          case DiscriminatorKind.None =>
            // DiscriminatorKind.None requires mark/reset functionality in ToonReader
            // which is not yet implemented. Use Key or Field discriminator modes instead.
            throw new UnsupportedOperationException(
              "DiscriminatorKind.None is not yet supported for TOON format. " +
                "Please use DiscriminatorKind.Key (default) or DiscriminatorKind.Field."
            )
        }

      def encodeValue(x: A, out: ToonWriter): Unit = {
        val caseIdx = discriminator.discriminate(x)
        if (caseIdx >= 0 && caseIdx < caseInfos.length) {
          val info = caseInfos(caseIdx)
          discriminatorKind match {
            case DiscriminatorKind.Key =>
              out.writeObjectStart()
              out.writeKey(info.name)
              if (info.isEnum && enumValuesAsStrings) {
                out.writeNull()
              } else {
                info.codec.encodeValue(x, out)
              }
              out.writeObjectEnd()

            case DiscriminatorKind.Field(fieldName) =>
              out.writeObjectStart()
              out.writeKey(fieldName)
              out.writeVal(info.name)
              out.writeFieldSeparator()
              info.codec.encodeValue(x, out)
              out.writeObjectEnd()

            case DiscriminatorKind.None =>
              // Encode without discriminator - just the value directly
              info.codec.encodeValue(x, out)
          }
        }
      }
    }
  }

  private[this] def deriveSequenceCodec[A, C[_]](seq: Reflect.Sequence[Binding, A, C]): ToonBinaryCodec[C[A]] = {
    val elementCodec  = deriveCodec(seq.element)
    val seqBinding    = seq.seqBinding.asInstanceOf[Binding.Seq[C, A]]
    val constructor   = seqBinding.constructor
    val deconstructor = seqBinding.deconstructor

    new ToonBinaryCodec[C[A]]() {
      def decodeValue(in: ToonReader, default: C[A]): C[A] = {
        val builder = constructor.newObjectBuilder[A](8)
        in.readArrayStart()
        while (!in.isArrayEnd) {
          val elem = elementCodec.decodeValue(in, elementCodec.nullValue)
          constructor.addObject(builder, elem)
        }
        in.readArrayEnd()
        constructor.resultObject[A](builder)
      }

      def encodeValue(x: C[A], out: ToonWriter): Unit = {
        val iter = deconstructor.deconstruct(x)
        val size = deconstructor match {
          case indexed: SpecializedIndexed[C] @unchecked => indexed.size(x)
          case _                                         => iter.size
        }
        out.writeArrayStart(size)
        var first = true
        while (iter.hasNext) {
          if (!first) out.writeElementSeparator()
          elementCodec.encodeValue(iter.next(), out)
          first = false
        }
        out.writeArrayEnd()
      }
    }
  }

  private[this] def deriveMapCodec[K, V, M[_, _]](
    mapReflect: Reflect.Map[Binding, K, V, M]
  ): ToonBinaryCodec[M[K, V]] = {
    val keyCodec      = deriveCodec(mapReflect.key)
    val valueCodec    = deriveCodec(mapReflect.value)
    val mapBinding    = mapReflect.mapBinding.asInstanceOf[Binding.Map[M, K, V]]
    val constructor   = mapBinding.constructor
    val deconstructor = mapBinding.deconstructor

    new ToonBinaryCodec[M[K, V]]() {
      def decodeValue(in: ToonReader, default: M[K, V]): M[K, V] = {
        val builder = constructor.newObjectBuilder[K, V](8)
        in.readObjectStart()
        while (!in.isObjectEnd) {
          val key   = keyCodec.decodeKey(in)
          val value = valueCodec.decodeValue(in, valueCodec.nullValue)
          constructor.addObject(builder, key, value)
        }
        in.readObjectEnd()
        constructor.resultObject[K, V](builder)
      }

      def encodeValue(x: M[K, V], out: ToonWriter): Unit = {
        val iter = deconstructor.deconstruct(x)
        out.writeObjectStart()
        var first = true
        while (iter.hasNext) {
          val kv = iter.next()
          if (!first) out.writeFieldSeparator()
          keyCodec.encodeKey(deconstructor.getKey(kv), out)
          valueCodec.encodeValue(deconstructor.getValue(kv), out)
          first = false
        }
        out.writeObjectEnd()
      }
    }
  }

  private[this] def deriveDynamicCodec(): ToonBinaryCodec[DynamicValue] =
    new ToonBinaryCodec[DynamicValue]() {
      def decodeValue(in: ToonReader, default: DynamicValue): DynamicValue =
        in.readDynamicValue()

      def encodeValue(x: DynamicValue, out: ToonWriter): Unit =
        out.writeDynamicValue(x)
    }

  private[this] def deriveWrapperCodec[A](wrapper: Reflect.Wrapper[Binding, ?, ?]): ToonBinaryCodec[A] = {
    val binding      = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
    val wrappedCodec = deriveCodec(wrapper.wrapped).asInstanceOf[ToonBinaryCodec[Wrapped]]
    val wrap         = binding.wrap
    val unwrap       = binding.unwrap

    new ToonBinaryCodec[A]() {
      def decodeValue(in: ToonReader, default: A): A = {
        val wrapped = wrappedCodec.decodeValue(in, wrappedCodec.nullValue)
        wrap(wrapped) match {
          case Right(x)    => x
          case Left(error) => in.decodeError(error)
        }
      }

      def encodeValue(x: A, out: ToonWriter): Unit = {
        val wrapped = unwrap(x)
        wrappedCodec.encodeValue(wrapped, out)
      }

      override def decodeKey(in: ToonReader): A = {
        val wrapped = wrappedCodec.decodeKey(in)
        wrap(wrapped) match {
          case Right(x)    => x
          case Left(error) => in.decodeError(error)
        }
      }

      override def encodeKey(x: A, out: ToonWriter): Unit = {
        val wrapped = unwrap(x)
        wrappedCodec.encodeKey(wrapped, out)
      }
    }
  }

  private case class FieldInfoArray(
    names: Array[String],
    codecs: Array[ToonBinaryCodec[Any]],
    offsets: Array[RegisterOffset],
    defaults: Array[Option[Any]],
    isOptionals: Array[Boolean],
    isSequences: Array[Boolean],
    hasDefaults: Array[Boolean],
    count: Int
  )

  private case class CaseInfo(
    name: String,
    codec: ToonBinaryCodec[Any],
    discriminator: Discriminator[Any],
    isEnum: Boolean
  )

}
