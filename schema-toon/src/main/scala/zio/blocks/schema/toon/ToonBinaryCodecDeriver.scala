package zio.blocks.schema.toon

import zio.blocks.schema.toon.ToonBinaryCodec._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding}
import zio.blocks.schema._
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.schema.toon.codec._

import java.util
import scala.util.control.NonFatal

object ToonBinaryCodecDeriver
    extends ToonBinaryCodecDeriver(
      fieldNameMapper = NameMapper.Identity,
      caseNameMapper = NameMapper.Identity,
      discriminatorKind = DiscriminatorKind.Key,
      arrayFormat = ArrayFormat.Auto,
      delimiter = Delimiter.Comma,
      rejectExtraFields = false,
      enumValuesAsStrings = true,
      transientNone = true,
      requireOptionFields = false,
      transientEmptyCollection = true,
      requireCollectionFields = false,
      transientDefaultValue = true,
      requireDefaultValueFields = false
    )

class ToonBinaryCodecDeriver private[toon] (
  fieldNameMapper: NameMapper,
  caseNameMapper: NameMapper,
  discriminatorKind: DiscriminatorKind,
  arrayFormat: ArrayFormat,
  delimiter: Delimiter,
  rejectExtraFields: Boolean,
  enumValuesAsStrings: Boolean,
  transientNone: Boolean,
  requireOptionFields: Boolean,
  transientEmptyCollection: Boolean,
  requireCollectionFields: Boolean,
  transientDefaultValue: Boolean,
  requireDefaultValueFields: Boolean
) extends Deriver[ToonBinaryCodec] {

  def withFieldNameMapper(fieldNameMapper: NameMapper): ToonBinaryCodecDeriver =
    copy(fieldNameMapper = fieldNameMapper)

  def withCaseNameMapper(caseNameMapper: NameMapper): ToonBinaryCodecDeriver =
    copy(caseNameMapper = caseNameMapper)

  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): ToonBinaryCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  def withArrayFormat(arrayFormat: ArrayFormat): ToonBinaryCodecDeriver =
    copy(arrayFormat = arrayFormat)

  def withDelimiter(delimiter: Delimiter): ToonBinaryCodecDeriver =
    copy(delimiter = delimiter)

  def withRejectExtraFields(rejectExtraFields: Boolean): ToonBinaryCodecDeriver =
    copy(rejectExtraFields = rejectExtraFields)

  def withEnumValuesAsStrings(enumValuesAsStrings: Boolean): ToonBinaryCodecDeriver =
    copy(enumValuesAsStrings = enumValuesAsStrings)

  def withTransientNone(transientNone: Boolean): ToonBinaryCodecDeriver =
    copy(transientNone = transientNone)

  def withRequireOptionFields(requireOptionFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireOptionFields = requireOptionFields)

  def withTransientEmptyCollection(transientEmptyCollection: Boolean): ToonBinaryCodecDeriver =
    copy(transientEmptyCollection = transientEmptyCollection)

  def withRequireCollectionFields(requireCollectionFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireCollectionFields = requireCollectionFields)

  def withTransientDefaultValue(transientDefaultValue: Boolean): ToonBinaryCodecDeriver =
    copy(transientDefaultValue = transientDefaultValue)

  def withRequireDefaultValueFields(requireDefaultValueFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireDefaultValueFields = requireDefaultValueFields)

  private def copy(
    fieldNameMapper: NameMapper = fieldNameMapper,
    caseNameMapper: NameMapper = caseNameMapper,
    discriminatorKind: DiscriminatorKind = discriminatorKind,
    arrayFormat: ArrayFormat = arrayFormat,
    delimiter: Delimiter = delimiter,
    rejectExtraFields: Boolean = rejectExtraFields,
    enumValuesAsStrings: Boolean = enumValuesAsStrings,
    transientNone: Boolean = transientNone,
    requireOptionFields: Boolean = requireOptionFields,
    transientEmptyCollection: Boolean = transientEmptyCollection,
    requireCollectionFields: Boolean = requireCollectionFields,
    transientDefaultValue: Boolean = transientDefaultValue,
    requireDefaultValueFields: Boolean = requireDefaultValueFields
  ) = new ToonBinaryCodecDeriver(
    fieldNameMapper,
    caseNameMapper,
    discriminatorKind,
    arrayFormat,
    delimiter,
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
    typeName: TypeName[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[ToonBinaryCodec[A]] =
    Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
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
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
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
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
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
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
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
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[DynamicValue]] =
    Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeName: TypeName[A],
    wrapperPrimitiveType: Option[PrimitiveType[A]],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
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

  private[this] val recursiveRecordCache: ThreadLocal[util.HashMap[TypeName[?], Array[ToonFieldInfo]]] =
    new ThreadLocal[java.util.HashMap[TypeName[?], Array[ToonFieldInfo]]] {
      override def initialValue: java.util.HashMap[TypeName[?], Array[ToonFieldInfo]] = new java.util.HashMap
    }

  private[this] val discriminatorFields: ThreadLocal[List[ToonDiscriminatorFieldInfo]] =
    new ThreadLocal[List[ToonDiscriminatorFieldInfo]] {
      override def initialValue: List[ToonDiscriminatorFieldInfo] = Nil
    }

  private[this] lazy val codecDeriver: CodecDeriver = new CodecDeriver {
    def derive[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] = deriveCodec(reflect)
  }

  private[this] lazy val variantBuilder = new VariantCodecBuilder(
    discriminatorKind,
    caseNameMapper,
    enumValuesAsStrings,
    discriminatorFields,
    codecDeriver
  )

  private[this] lazy val sequenceBuilder = new SequenceCodecBuilder(
    arrayFormat,
    delimiter,
    codecDeriver
  )

  private[this] lazy val mapBuilder = new MapCodecBuilder(codecDeriver)

  private[this] lazy val recordBuilder = new RecordCodecBuilder(
    fieldNameMapper,
    rejectExtraFields,
    transientNone,
    transientEmptyCollection,
    transientDefaultValue,
    requireOptionFields,
    requireCollectionFields,
    requireDefaultValueFields,
    recursiveRecordCache,
    discriminatorFields,
    codecDeriver
  )

  private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] = {
    if (reflect.isPrimitive) derivePrimitiveCodec(reflect.asPrimitive.get)
    else if (reflect.isVariant) deriveVariantCodec(reflect.asVariant.get)
    else if (reflect.isSequence) deriveSequenceCodec(reflect.asSequenceUnknown.get.sequence)
    else if (reflect.isMap) deriveMapCodec(reflect.asMapUnknown.get.map)
    else if (reflect.isRecord) deriveRecordCodec(reflect.asRecord.get)
    else if (reflect.isWrapper) deriveWrapperCodec(reflect.asWrapperUnknown.get.wrapper)
    else deriveDynamicCodec(reflect.asDynamic.get)
  }.asInstanceOf[ToonBinaryCodec[A]]

  private[this] def derivePrimitiveCodec[F[_, _], A](primitive: Reflect.Primitive[F, A]): ToonBinaryCodec[A] = {
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
    } else primitive.primitiveBinding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance.force
  }.asInstanceOf[ToonBinaryCodec[A]]

  private[this] def deriveVariantCodec[F[_, _], A](variant: Reflect.Variant[F, A]): ToonBinaryCodec[A] =
    if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
      variantBuilder.build(variant, variant.variantBinding.asInstanceOf[Binding.Variant[A]].discriminator)
    } else {
      variant.variantBinding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance.force
    }

  private[this] def deriveSequenceCodec[F[_, _], A, C[_]](sequence: Reflect.Sequence[F, A, C]): ToonBinaryCodec[C[A]] =
    if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
      sequenceBuilder.build(sequence, sequence.seqBinding.asInstanceOf[Binding.Seq[C, A]])
    } else {
      sequence.seqBinding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, C[A]]].instance.force
    }

  private[this] def deriveMapCodec[F[_, _], K, V, M[_, _]](map: Reflect.Map[F, K, V, M]): ToonBinaryCodec[M[K, V]] =
    if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
      mapBuilder.build(map, map.mapBinding.asInstanceOf[Binding.Map[M, K, V]])
    } else {
      map.mapBinding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, M[K, V]]].instance.force
    }

  private[this] def deriveRecordCodec[F[_, _], A](record: Reflect.Record[F, A]): ToonBinaryCodec[A] =
    if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
      recordBuilder.build(record, record.recordBinding.asInstanceOf[Binding.Record[A]])
    } else {
      record.recordBinding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance.force
    }

  private[this] def deriveWrapperCodec[F[_, _], A, B](wrapper: Reflect.Wrapper[F, A, B]): ToonBinaryCodec[A] =
    if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
      val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, B]]
      val codec   = deriveCodec(wrapper.wrapped)
      new ToonBinaryCodec[A](wrapper.wrapperPrimitiveType.fold(ToonBinaryCodec.objectType) {
        case _: PrimitiveType.Boolean   => ToonBinaryCodec.booleanType
        case _: PrimitiveType.Byte      => ToonBinaryCodec.byteType
        case _: PrimitiveType.Char      => ToonBinaryCodec.charType
        case _: PrimitiveType.Short     => ToonBinaryCodec.shortType
        case _: PrimitiveType.Float     => ToonBinaryCodec.floatType
        case _: PrimitiveType.Int       => ToonBinaryCodec.intType
        case _: PrimitiveType.Double    => ToonBinaryCodec.doubleType
        case _: PrimitiveType.Long      => ToonBinaryCodec.longType
        case _: PrimitiveType.Unit.type => ToonBinaryCodec.unitType
        case _                          => ToonBinaryCodec.objectType
      }) {
        private[this] val unwrap       = binding.unwrap
        private[this] val wrap         = binding.wrap
        private[this] val wrappedCodec = codec

        override def decodeValue(in: ToonReader, default: A): A =
          wrap(
            try {
              wrappedCodec.decodeValue(
                in, {
                  if (default == null) null
                  else unwrap(default)
                }.asInstanceOf[B]
              )
            } catch {
              case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.Wrapped, err)
            }
          ) match {
            case Right(x)    => x
            case Left(error) => in.decodeError(error.message)
          }

        override def encodeValue(x: A, out: ToonWriter): Unit = wrappedCodec.encodeValue(unwrap(x), out)

        override def decodeKey(in: ToonReader): A =
          wrap(
            try wrappedCodec.decodeKey(in)
            catch {
              case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.Wrapped, err)
            }
          ) match {
            case Right(x)    => x
            case Left(error) => in.decodeError(error.message)
          }

        override def encodeKey(x: A, out: ToonWriter): Unit = wrappedCodec.encodeKey(unwrap(x), out)
      }
    } else {
      wrapper.wrapperBinding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance.force
    }

  private[this] def deriveDynamicCodec[F[_, _]](dynamic: Reflect.Dynamic[F]): ToonBinaryCodec[DynamicValue] =
    if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
    else dynamic.dynamicBinding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, DynamicValue]].instance.force
}
