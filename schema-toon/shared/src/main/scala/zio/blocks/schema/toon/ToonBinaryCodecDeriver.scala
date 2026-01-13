package zio.blocks.schema.toon

import zio.blocks.schema.toon._
import zio.blocks.schema.toon.ToonBinaryCodec._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema._
import zio.blocks.schema.binding.{Constructor, Discriminator}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.SeqDeconstructor.SpecializedIndexed
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}

import scala.annotation.{switch, tailrec}
import scala.util.control.NonFatal

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

  private[this] val recursiveRecordCache = new ThreadLocal[java.util.HashMap[TypeName[?], Array[FieldInfo]]] {
    override def initialValue: java.util.HashMap[TypeName[?], Array[FieldInfo]] = new java.util.HashMap
  }
  private[this] val discriminatorFields = new ThreadLocal[List[DiscriminatorFieldInfo]] {
    override def initialValue: List[DiscriminatorFieldInfo] = Nil
  }

  private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] = {
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
        val overrideInstance = primitive.primitiveBinding.asInstanceOf[BindingInstance[A]]
        overrideInstance.instance.asInstanceOf[ToonBinaryCodec[A]]
      }
    } else if (reflect.isRecord) {
      deriveRecordCodec(reflect.asRecord.get.asInstanceOf[Reflect.Record[Binding, A]])
    } else if (reflect.isVariant) {
      deriveVariantCodec(reflect.asVariant.get.asInstanceOf[Reflect.Variant[Binding, A]])
    } else if (reflect.isSequence) {
      deriveSequenceCodec(reflect.asSequence.get.asInstanceOf[Reflect.Sequence[Binding, Col, Elem]])
        .asInstanceOf[ToonBinaryCodec[A]]
    } else if (reflect.isMap) {
      deriveMapCodec(reflect.asMap.get.asInstanceOf[Reflect.Map[Binding, Map, Key, Value]])
        .asInstanceOf[ToonBinaryCodec[A]]
    } else if (reflect.isDynamic) {
      deriveDynamicCodec().asInstanceOf[ToonBinaryCodec[A]]
    } else if (reflect.isWrapper) {
      val wrapper = reflect.asWrapper.get.asInstanceOf[Reflect.Wrapper[Binding, A, Wrapped]]
      deriveWrapperCodec(wrapper)
    } else {
      throw new UnsupportedOperationException(s"Cannot derive TOON codec for: $reflect")
    }
  }

  private[this] def deriveRecordCodec[A](record: Reflect.Record[Binding, A]): ToonBinaryCodec[A] = {
    val fields       = record.fields
    val fieldCount   = fields.length
    val fieldInfos   = new Array[FieldInfo](fieldCount)
    val fieldNames   = new Array[String](fieldCount)
    val fieldCodecs  = new Array[ToonBinaryCodec[Any]](fieldCount)
    val constructor  = record.recordBinding.constructor.asInstanceOf[Constructor[A]]
    val registers    = constructor.usedRegisters

    // Check for recursive reference
    val cache = recursiveRecordCache.get
    val cachedFieldInfos = cache.get(record.typeName)
    if (cachedFieldInfos != null) {
      // Return a lazy codec that references the cached field infos
      return new ToonBinaryCodec[A]() {
        def decodeValue(in: ToonReader, default: A): A = {
          // Use cached field infos when available
          decodeRecordWithInfos(in, cachedFieldInfos, constructor, registers)
        }
        def encodeValue(x: A, out: ToonWriter): Unit = {
          encodeRecordWithInfos(x, out, cachedFieldInfos)
        }
      }
    }

    // Store field infos for recursive reference
    cache.put(record.typeName, fieldInfos)

    var idx = 0
    while (idx < fieldCount) {
      val field       = fields(idx)
      val fieldName   = fieldNameMapper.map(field.name)
      val fieldReflect = field.reflectField.asInstanceOf[Reflect[Binding, Any]]
      val fieldCodec  = deriveCodec(fieldReflect)
      val fieldOffset = field.binding.offset

      fieldNames(idx) = fieldName
      fieldCodecs(idx) = fieldCodec
      fieldInfos(idx) = FieldInfo(
        name = fieldName,
        codec = fieldCodec,
        offset = fieldOffset,
        isOptional = fieldReflect.isWrapper && fieldReflect.asWrapper.get.wrapped.isSequence,
        defaultValue = field.binding.defaultValue,
        isTransientNone = transientNone && fieldReflect.isWrapper,
        isTransientEmpty = transientEmptyCollection && fieldReflect.isSequence,
        isTransientDefault = transientDefaultValue && field.binding.defaultValue.isDefined
      )
      idx += 1
    }

    new ToonBinaryCodec[A]() {
      def decodeValue(in: ToonReader, default: A): A =
        decodeRecordWithInfos(in, fieldInfos, constructor, registers)

      def encodeValue(x: A, out: ToonWriter): Unit =
        encodeRecordWithInfos(x, out, fieldInfos)
    }
  }

  private[this] def decodeRecordWithInfos[A](
    in: ToonReader,
    fieldInfos: Array[FieldInfo],
    constructor: Constructor[A],
    registers: RegisterOffset
  ): A = {
    val regs = Registers.pooled(registers)
    try {
      in.readObjectStart()
      var continue = true
      while (continue) {
        val key = in.readKeyOrEnd()
        if (key == null) {
          continue = false
        } else {
          var found = false
          var idx = 0
          while (idx < fieldInfos.length && !found) {
            val info = fieldInfos(idx)
            if (info.name == key) {
              found = true
              val value = info.codec.decodeValue(in, info.codec.nullValue)
              info.offset.write(regs, value)
            }
            idx += 1
          }
          if (!found) {
            if (rejectExtraFields) {
              in.decodeError(s"unexpected field: $key")
            } else {
              in.skipValue()
            }
          }
        }
      }
      // Apply defaults for missing fields
      var idx = 0
      while (idx < fieldInfos.length) {
        val info = fieldInfos(idx)
        if (info.defaultValue.isDefined && !info.offset.isSet(regs)) {
          info.offset.write(regs, info.defaultValue.get)
        }
        idx += 1
      }
      constructor.construct(regs)
    } finally {
      Registers.free(regs)
    }
  }

  private[this] def encodeRecordWithInfos[A](x: A, out: ToonWriter, fieldInfos: Array[FieldInfo]): Unit = {
    out.writeObjectStart()
    var idx = 0
    var first = true
    while (idx < fieldInfos.length) {
      val info = fieldInfos(idx)
      val value = info.offset.read(x)
      val shouldSkip =
        (info.isTransientNone && value == None) ||
        (info.isTransientEmpty && isEmptyCollection(value)) ||
        (info.isTransientDefault && info.defaultValue.contains(value))

      if (!shouldSkip) {
        if (!first) out.writeFieldSeparator()
        out.writeKey(info.name)
        info.codec.encodeValue(value.asInstanceOf[Any], out)
        first = false
      }
      idx += 1
    }
    out.writeObjectEnd()
  }

  private[this] def isEmptyCollection(value: Any): Boolean = value match {
    case seq: scala.collection.Seq[?] => seq.isEmpty
    case set: scala.collection.Set[?] => set.isEmpty
    case map: scala.collection.Map[?, ?] => map.isEmpty
    case arr: Array[?] => arr.isEmpty
    case _ => false
  }

  private[this] def deriveVariantCodec[A](variant: Reflect.Variant[Binding, A]): ToonBinaryCodec[A] = {
    val cases = variant.cases
    val caseCount = cases.length
    val caseInfos = new Array[CaseInfo](caseCount)

    var idx = 0
    while (idx < caseCount) {
      val c = cases(idx)
      val caseName = caseNameMapper.map(c.name)
      val caseReflect = c.reflectField.asInstanceOf[Reflect[Binding, A]]
      val caseCodec = deriveCodec(caseReflect)
      val discriminator = c.binding.discriminator.asInstanceOf[Discriminator[A, A]]

      caseInfos(idx) = CaseInfo(
        name = caseName,
        codec = caseCodec,
        discriminator = discriminator,
        isEnum = caseReflect.isRecord && caseReflect.asRecord.get.fields.isEmpty
      )
      idx += 1
    }

    new ToonBinaryCodec[A]() {
      def decodeValue(in: ToonReader, default: A): A = {
        discriminatorKind match {
          case DiscriminatorKind.Key =>
            in.readObjectStart()
            val key = in.readKey()
            var result: A = default
            var found = false
            var idx = 0
            while (idx < caseInfos.length && !found) {
              val info = caseInfos(idx)
              if (info.name == key) {
                found = true
                if (info.isEnum && enumValuesAsStrings) {
                  result = info.discriminator.inject(info.codec.nullValue)
                } else {
                  result = info.codec.decodeValue(in, info.codec.nullValue)
                }
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
            var found = false
            var idx = 0
            while (idx < caseInfos.length && !found) {
              val info = caseInfos(idx)
              if (info.name == discValue) {
                found = true
                result = info.codec.decodeValue(in, info.codec.nullValue)
              }
              idx += 1
            }
            if (!found) in.decodeError(s"unknown variant case: $discValue")
            result
        }
      }

      def encodeValue(x: A, out: ToonWriter): Unit = {
        var idx = 0
        var done = false
        while (idx < caseInfos.length && !done) {
          val info = caseInfos(idx)
          if (info.discriminator.extract(x).isDefined) {
            done = true
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
                // Encode with discriminator field embedded
                out.writeObjectStart()
                out.writeKey(fieldName)
                out.writeVal(info.name)
                out.writeFieldSeparator()
                info.codec.encodeValue(x, out)
                out.writeObjectEnd()
            }
          }
          idx += 1
        }
      }
    }
  }

  private[this] def deriveSequenceCodec[C[_], E](seq: Reflect.Sequence[Binding, C, E]): ToonBinaryCodec[C[E]] = {
    val elementCodec = deriveCodec(seq.element)
    val seqBinding = seq.seqBinding
    val constructor = seqBinding.constructor
    val deconstructor = seqBinding.deconstructor

    new ToonBinaryCodec[C[E]]() {
      def decodeValue(in: ToonReader, default: C[E]): C[E] = {
        val builder = constructor.newBuilder
        in.readArrayStart()
        while (!in.isArrayEnd) {
          val elem = elementCodec.decodeValue(in, elementCodec.nullValue)
          builder += elem
        }
        in.readArrayEnd()
        builder.result()
      }

      def encodeValue(x: C[E], out: ToonWriter): Unit = {
        val iter = deconstructor.deconstruct(x)
        val size = deconstructor match {
          case indexed: SpecializedIndexed[C, E] @unchecked => indexed.length(x)
          case _ => iter.size
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

  private[this] def deriveMapCodec[M[_, _], K, V](
    mapReflect: Reflect.Map[Binding, M, K, V]
  ): ToonBinaryCodec[M[K, V]] = {
    val keyCodec = deriveCodec(mapReflect.key)
    val valueCodec = deriveCodec(mapReflect.value)
    val mapBinding = mapReflect.mapBinding
    val constructor = mapBinding.constructor
    val deconstructor = mapBinding.deconstructor

    new ToonBinaryCodec[M[K, V]]() {
      def decodeValue(in: ToonReader, default: M[K, V]): M[K, V] = {
        val builder = constructor.newBuilder
        in.readObjectStart()
        while (!in.isObjectEnd) {
          val key = keyCodec.decodeKey(in)
          val value = valueCodec.decodeValue(in, valueCodec.nullValue)
          builder += ((key, value))
        }
        in.readObjectEnd()
        builder.result()
      }

      def encodeValue(x: M[K, V], out: ToonWriter): Unit = {
        val iter = deconstructor.deconstruct(x)
        out.writeObjectStart()
        var first = true
        while (iter.hasNext) {
          val (k, v) = iter.next()
          if (!first) out.writeFieldSeparator()
          keyCodec.encodeKey(k, out)
          valueCodec.encodeValue(v, out)
          first = false
        }
        out.writeObjectEnd()
      }
    }
  }

  private[this] def deriveDynamicCodec(): ToonBinaryCodec[DynamicValue] = {
    new ToonBinaryCodec[DynamicValue]() {
      def decodeValue(in: ToonReader, default: DynamicValue): DynamicValue = {
        in.readDynamicValue()
      }

      def encodeValue(x: DynamicValue, out: ToonWriter): Unit = {
        out.writeDynamicValue(x)
      }
    }
  }

  private[this] def deriveWrapperCodec[A, W](wrapper: Reflect.Wrapper[Binding, A, W]): ToonBinaryCodec[A] = {
    val wrappedCodec = deriveCodec(wrapper.wrapped)
    val wrapperBinding = wrapper.wrapperBinding

    new ToonBinaryCodec[A]() {
      def decodeValue(in: ToonReader, default: A): A = {
        val wrapped = wrappedCodec.decodeValue(in, wrappedCodec.nullValue)
        wrapperBinding.wrap(wrapped)
      }

      def encodeValue(x: A, out: ToonWriter): Unit = {
        val wrapped = wrapperBinding.unwrap(x)
        wrappedCodec.encodeValue(wrapped, out)
      }

      override def decodeKey(in: ToonReader): A = {
        val wrapped = wrappedCodec.decodeKey(in)
        wrapperBinding.wrap(wrapped)
      }

      override def encodeKey(x: A, out: ToonWriter): Unit = {
        val wrapped = wrapperBinding.unwrap(x)
        wrappedCodec.encodeKey(wrapped, out)
      }
    }
  }

  private case class FieldInfo(
    name: String,
    codec: ToonBinaryCodec[Any],
    offset: RegisterOffset,
    isOptional: Boolean,
    defaultValue: Option[Any],
    isTransientNone: Boolean,
    isTransientEmpty: Boolean,
    isTransientDefault: Boolean
  )

  private case class CaseInfo(
    name: String,
    codec: ToonBinaryCodec[Any],
    discriminator: Discriminator[Any, Any],
    isEnum: Boolean
  )

  private case class DiscriminatorFieldInfo(
    name: String,
    value: String
  )
}
