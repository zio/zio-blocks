package zio.blocks.schema.json

import zio.blocks.schema.json._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers}
import zio.blocks.schema._
import zio.blocks.schema.binding.{Constructor, Discriminator}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.SeqDeconstructor.SpecializedIndexed
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import scala.collection.immutable.VectorBuilder
import scala.util.control.NonFatal

object JsonFormat extends BinaryFormat("application/json", JsonBinaryCodecDeriver)

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
  def withFieldNameMapper(fieldNameMapper: NameMapper): JsonBinaryCodecDeriver = copy(fieldNameMapper = fieldNameMapper)

  def withCaseNameMapper(caseNameMapper: NameMapper): JsonBinaryCodecDeriver = copy(caseNameMapper = caseNameMapper)

  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): JsonBinaryCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  def withRejectExtraFields(rejectExtraFields: Boolean): JsonBinaryCodecDeriver =
    copy(rejectExtraFields = rejectExtraFields)

  def withEnumValuesAsStrings(enumValuesAsStrings: Boolean): JsonBinaryCodecDeriver =
    copy(enumValuesAsStrings = enumValuesAsStrings)

  def withTransientNone(transientNone: Boolean): JsonBinaryCodecDeriver = copy(transientNone = transientNone)

  def withRequireOptionFields(requireOptionFields: Boolean): JsonBinaryCodecDeriver =
    copy(requireOptionFields = requireOptionFields)

  def withTransientEmptyCollection(transientEmptyCollection: Boolean): JsonBinaryCodecDeriver =
    copy(transientEmptyCollection = transientEmptyCollection)

  def withRequireCollectionFields(requireCollectionFields: Boolean): JsonBinaryCodecDeriver =
    copy(requireCollectionFields = requireCollectionFields)

  def withTransientDefaultValue(transientDefaultValue: Boolean): JsonBinaryCodecDeriver =
    copy(transientDefaultValue = transientDefaultValue)

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

  override def derivePrimitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  ): Lazy[JsonBinaryCodec[A]] =
    Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[A]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[A]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[C[A]]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[M[K, V]]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[DynamicValue]] =
    Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeName: TypeName[A],
    wrapperPrimitiveType: Option[PrimitiveType[A]],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonBinaryCodec[A]] = Lazy {
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
  private[this] val discriminatorFields = new ThreadLocal[List[(String, String)]] {
    override def initialValue: List[(String, String)] = Nil
  }
  private[this] val unitCodec = new JsonBinaryCodec[Unit](JsonBinaryCodec.unitType) {
    def decodeValue(in: JsonReader, default: Unit): Unit =
      if (in.isNextToken('n')) in.readNullOrError((), "expected null")
      else in.decodeError("expected null")

    def encodeValue(x: Unit, out: JsonWriter): Unit = out.writeNull()
  }
  private[this] val booleanCodec = new JsonBinaryCodec[Boolean](JsonBinaryCodec.booleanType) {
    def decodeValue(in: JsonReader, default: Boolean): Boolean = in.readBoolean()

    def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Boolean = in.readKeyAsBoolean()

    override def encodeKey(x: Boolean, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val byteCodec = new JsonBinaryCodec[Byte](JsonBinaryCodec.byteType) {
    def decodeValue(in: JsonReader, default: Byte): Byte = in.readByte()

    def encodeValue(x: Byte, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Byte = in.readKeyAsByte()

    override def encodeKey(x: Byte, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val shortCodec = new JsonBinaryCodec[Short](JsonBinaryCodec.shortType) {
    def decodeValue(in: JsonReader, default: Short): Short = in.readShort()

    def encodeValue(x: Short, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Short = in.readKeyAsShort()

    override def encodeKey(x: Short, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val intCodec = new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
    def decodeValue(in: JsonReader, default: Int): Int = in.readInt()

    def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Int = in.readKeyAsInt()

    override def encodeKey(x: Int, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val longCodec = new JsonBinaryCodec[Long](JsonBinaryCodec.longType) {
    def decodeValue(in: JsonReader, default: Long): Long = in.readLong()

    def encodeValue(x: Long, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Long = in.readKeyAsLong()

    override def encodeKey(x: Long, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val floatCodec = new JsonBinaryCodec[Float](JsonBinaryCodec.floatType) {
    def decodeValue(in: JsonReader, default: Float): Float = in.readFloat()

    def encodeValue(x: Float, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Float = in.readKeyAsFloat()

    override def encodeKey(x: Float, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val doubleCodec = new JsonBinaryCodec[Double](JsonBinaryCodec.doubleType) {
    def decodeValue(in: JsonReader, default: Double): Double = in.readDouble()

    def encodeValue(x: Double, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Double = in.readKeyAsDouble()

    override def encodeKey(x: Double, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val charCodec = new JsonBinaryCodec[Char](JsonBinaryCodec.charType) {
    def decodeValue(in: JsonReader, default: Char): Char = in.readChar()

    def encodeValue(x: Char, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Char = in.readKeyAsChar()

    override def encodeKey(x: Char, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val stringCodec = new JsonBinaryCodec[String]() {
    def decodeValue(in: JsonReader, default: String): String = in.readString(default)

    def encodeValue(x: String, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): String = in.readKeyAsString()

    override def encodeKey(x: String, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val bigIntCodec = new JsonBinaryCodec[BigInt]() {
    def decodeValue(in: JsonReader, default: BigInt): BigInt = in.readBigInt(default)

    def encodeValue(x: BigInt, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): BigInt = in.readKeyAsBigInt()

    override def encodeKey(x: BigInt, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val bigDecimalCodec = new JsonBinaryCodec[BigDecimal]() {
    def decodeValue(in: JsonReader, default: BigDecimal): BigDecimal = in.readBigDecimal(default)

    def encodeValue(x: BigDecimal, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): BigDecimal = in.readKeyAsBigDecimal()

    override def encodeKey(x: BigDecimal, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val dayOfWeekCodec = new JsonBinaryCodec[java.time.DayOfWeek]() {
    def decodeValue(in: JsonReader, default: java.time.DayOfWeek): java.time.DayOfWeek = {
      val code = in.readString(if (default eq null) null else default.toString)
      try java.time.DayOfWeek.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal day of week value")
      }
    }

    def encodeValue(x: java.time.DayOfWeek, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeKey(in: JsonReader): java.time.DayOfWeek = {
      val code = in.readKeyAsString()
      try java.time.DayOfWeek.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal day of week value")
      }
    }

    override def encodeKey(x: java.time.DayOfWeek, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)
  }
  private[this] val durationCodec = new JsonBinaryCodec[java.time.Duration]() {
    def decodeValue(in: JsonReader, default: java.time.Duration): java.time.Duration = in.readDuration(default)

    def encodeValue(x: java.time.Duration, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.Duration = in.readKeyAsDuration()

    override def encodeKey(x: java.time.Duration, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val instantCodec = new JsonBinaryCodec[java.time.Instant]() {
    def decodeValue(in: JsonReader, default: java.time.Instant): java.time.Instant = in.readInstant(default)

    def encodeValue(x: java.time.Instant, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.Instant = in.readKeyAsInstant()

    override def encodeKey(x: java.time.Instant, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val localDateCodec = new JsonBinaryCodec[java.time.LocalDate]() {
    def decodeValue(in: JsonReader, default: java.time.LocalDate): java.time.LocalDate = in.readLocalDate(default)

    def encodeValue(x: java.time.LocalDate, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.LocalDate = in.readKeyAsLocalDate()

    override def encodeKey(x: java.time.LocalDate, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val localDateTimeCodec = new JsonBinaryCodec[java.time.LocalDateTime]() {
    def decodeValue(in: JsonReader, default: java.time.LocalDateTime): java.time.LocalDateTime =
      in.readLocalDateTime(default)

    def encodeValue(x: java.time.LocalDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.LocalDateTime = in.readKeyAsLocalDateTime()

    override def encodeKey(x: java.time.LocalDateTime, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val localTimeCodec = new JsonBinaryCodec[java.time.LocalTime]() {
    def decodeValue(in: JsonReader, default: java.time.LocalTime): java.time.LocalTime = in.readLocalTime(default)

    def encodeValue(x: java.time.LocalTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.LocalTime = in.readKeyAsLocalTime()

    override def encodeKey(x: java.time.LocalTime, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val monthCodec = new JsonBinaryCodec[java.time.Month]() {
    def decodeValue(in: JsonReader, default: java.time.Month): java.time.Month = {
      val code = in.readString(if (default eq null) null else default.toString)
      try java.time.Month.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal month value")
      }
    }

    def encodeValue(x: java.time.Month, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeKey(in: JsonReader): java.time.Month = {
      val code = in.readKeyAsString()
      try java.time.Month.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal month value")
      }
    }

    override def encodeKey(x: java.time.Month, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)
  }
  private[this] val monthDayCodec = new JsonBinaryCodec[java.time.MonthDay]() {
    def decodeValue(in: JsonReader, default: java.time.MonthDay): java.time.MonthDay = in.readMonthDay(default)

    def encodeValue(x: java.time.MonthDay, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.MonthDay = in.readKeyAsMonthDay()

    override def encodeKey(x: java.time.MonthDay, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val offsetDateTimeCodec = new JsonBinaryCodec[java.time.OffsetDateTime]() {
    def decodeValue(in: JsonReader, default: java.time.OffsetDateTime): java.time.OffsetDateTime =
      in.readOffsetDateTime(default)

    def encodeValue(x: java.time.OffsetDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.OffsetDateTime = in.readKeyAsOffsetDateTime()

    override def encodeKey(x: java.time.OffsetDateTime, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val offsetTimeCodec = new JsonBinaryCodec[java.time.OffsetTime]() {
    def decodeValue(in: JsonReader, default: java.time.OffsetTime): java.time.OffsetTime = in.readOffsetTime(default)

    def encodeValue(x: java.time.OffsetTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.OffsetTime = in.readKeyAsOffsetTime()

    override def encodeKey(x: java.time.OffsetTime, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val periodCodec = new JsonBinaryCodec[java.time.Period]() {
    def decodeValue(in: JsonReader, default: java.time.Period): java.time.Period = in.readPeriod(default)

    def encodeValue(x: java.time.Period, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.Period = in.readKeyAsPeriod()

    override def encodeKey(x: java.time.Period, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val yearCodec = new JsonBinaryCodec[java.time.Year]() {
    def decodeValue(in: JsonReader, default: java.time.Year): java.time.Year = in.readYear(default)

    def encodeValue(x: java.time.Year, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.Year = in.readKeyAsYear()

    override def encodeKey(x: java.time.Year, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val yearMonthCodec = new JsonBinaryCodec[java.time.YearMonth]() {
    def decodeValue(in: JsonReader, default: java.time.YearMonth): java.time.YearMonth = in.readYearMonth(default)

    def encodeValue(x: java.time.YearMonth, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.YearMonth = in.readKeyAsYearMonth()

    override def encodeKey(x: java.time.YearMonth, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val zoneIdCodec = new JsonBinaryCodec[java.time.ZoneId]() {
    def decodeValue(in: JsonReader, default: java.time.ZoneId): java.time.ZoneId = in.readZoneId(default)

    def encodeValue(x: java.time.ZoneId, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.ZoneId = in.readKeyAsZoneId()

    override def encodeKey(x: java.time.ZoneId, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val zoneOffsetCodec = new JsonBinaryCodec[java.time.ZoneOffset]() {
    def decodeValue(in: JsonReader, default: java.time.ZoneOffset): java.time.ZoneOffset = in.readZoneOffset(default)

    def encodeValue(x: java.time.ZoneOffset, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.ZoneOffset = in.readKeyAsZoneOffset()

    override def encodeKey(x: java.time.ZoneOffset, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val zonedDateTimeCodec = new JsonBinaryCodec[java.time.ZonedDateTime]() {
    def decodeValue(in: JsonReader, default: java.time.ZonedDateTime): java.time.ZonedDateTime =
      in.readZonedDateTime(default)

    def encodeValue(x: java.time.ZonedDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.ZonedDateTime = in.readKeyAsZonedDateTime()

    override def encodeKey(x: java.time.ZonedDateTime, out: JsonWriter): Unit = out.writeKey(x)
  }
  private[this] val currencyCodec = new JsonBinaryCodec[java.util.Currency]() {
    def decodeValue(in: JsonReader, default: java.util.Currency): java.util.Currency = {
      val code = in.readString(if (default eq null) null else default.toString)
      try java.util.Currency.getInstance(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal currency value")
      }
    }

    def encodeValue(x: java.util.Currency, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeKey(in: JsonReader): java.util.Currency = {
      val code = in.readKeyAsString()
      try java.util.Currency.getInstance(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal currency value")
      }
    }

    override def encodeKey(x: java.util.Currency, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)
  }
  private[this] val uuidCodec = new JsonBinaryCodec[java.util.UUID]() {
    def decodeValue(in: JsonReader, default: java.util.UUID): java.util.UUID = in.readUUID(default)

    def encodeValue(x: java.util.UUID, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.util.UUID = in.readKeyAsUUID()

    override def encodeKey(x: java.util.UUID, out: JsonWriter): Unit = out.writeKey(x)
  }

  private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): JsonBinaryCodec[A] = {
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
      } else primitive.primitiveBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isVariant) {
      val variant = reflect.asVariant.get
      if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
        option(variant) match {
          case Some(value) =>
            val valueCodec = deriveCodec(value).asInstanceOf[JsonBinaryCodec[Any]]
            new JsonBinaryCodec[Option[Any]]() {
              private[this] val codec = valueCodec

              override def decodeValue(in: JsonReader, default: Option[Any]): Option[Any] =
                if (in.nextToken() == 'n') {
                  try in.readNullOrError(default, "expected null")
                  catch {
                    case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.Case("None"), error)
                  }
                } else {
                  in.rollbackToken()
                  try new Some(codec.decodeValue(in, codec.nullValue))
                  catch {
                    case error if NonFatal(error) =>
                      in.decodeError(new DynamicOptic.Node.Case("Some"), new DynamicOptic.Node.Field("value"), error)
                  }
                }

              override def encodeValue(x: Option[Any], out: JsonWriter): Unit =
                if (x eq None) out.writeNull()
                else codec.encodeValue(x.get, out)

              override def nullValue: Option[String] = None
            }
          case _ =>
            val discr = variant.variantBinding.asInstanceOf[Binding.Variant[A]].discriminator
            if (isEnumeration(variant)) {
              val allConstructors = Array.newBuilder[Constructor[?]]
              val map             = new StringToIntMap(variant.cases.length)

              def getInfos(variant: Reflect.Variant[F, A]): Array[EnumInfo] = {
                val cases = variant.cases
                val len   = cases.length
                val infos = new Array[EnumInfo](len)
                var idx   = 0
                while (idx < len) {
                  val case_       = cases(idx)
                  val caseReflect = case_.value
                  infos(idx) = if (caseReflect.isVariant) {
                    val discr = discriminator(caseReflect)
                    new EnumNodeInfo(discr, getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]))
                  } else {
                    val constructorIdx = allConstructors.length
                    var name: String   = null
                    case_.modifiers.foreach {
                      case m: Modifier.rename => if (name eq null) name = m.name
                      case m: Modifier.alias  => map.put(m.name, constructorIdx)
                      case _                  =>
                    }
                    if (name eq null) name = caseNameMapper(case_.name)
                    map.put(name, constructorIdx)
                    val constructor = caseReflect.asRecord.get.recordBinding
                      .asInstanceOf[BindingInstance[TC, ?, ?]]
                      .binding
                      .asInstanceOf[Binding.Record[?]]
                      .constructor
                    allConstructors.addOne(constructor)
                    new EnumLeafInfo(name, constructor)
                  }
                  idx += 1
                }
                infos
              }

              new JsonBinaryCodec[A]() {
                private[this] val root           = new EnumNodeInfo(discr, getInfos(variant))
                private[this] val constructors   = allConstructors.result()
                private[this] val constructorMap = map

                def decodeValue(in: JsonReader, default: A): A = {
                  val valueLen = in.readStringAsCharBuf()
                  val idx      = constructorMap.get(in, valueLen)
                  if (idx >= 0) constructors(idx).construct(null, 0).asInstanceOf[A]
                  else in.enumValueError(valueLen)
                }

                def encodeValue(x: A, out: JsonWriter): Unit = root.discriminate(x).writeVal(out)
              }
            } else {
              val allCaseLeafInfos = Array.newBuilder[CaseLeafInfo]
              val map              = new StringToIntMap(variant.cases.length)
              discriminatorKind match {
                case DiscriminatorKind.Field(fieldName) if hasOnlyRecordAndVariantCases(variant) =>
                  def getInfos(variant: Reflect.Variant[F, A]): Array[CaseInfo] = {
                    val cases = variant.cases
                    val len   = cases.length
                    val infos = new Array[CaseInfo](len)
                    var idx   = 0
                    while (idx < len) {
                      val case_       = cases(idx)
                      val caseReflect = case_.value
                      infos(idx) = if (caseReflect.isVariant) {
                        val discr = discriminator(caseReflect)
                        new CaseNodeInfo(discr, getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]))
                      } else {
                        val infosIdx     = allCaseLeafInfos.length
                        var name: String = null
                        case_.modifiers.foreach {
                          case m: Modifier.rename => if (name eq null) name = m.name
                          case m: Modifier.alias  => map.put(m.name, infosIdx)
                          case _                  =>
                        }
                        if (name eq null) name = caseNameMapper(case_.name)
                        map.put(name, infosIdx)
                        val span = new DynamicOptic.Node.Case(case_.name)
                        discriminatorFields.set((fieldName, name) :: discriminatorFields.get)
                        val caseLeafInfo = new CaseLeafInfo(name, deriveCodec(caseReflect), span)
                        discriminatorFields.set(discriminatorFields.get.tail)
                        allCaseLeafInfos.addOne(caseLeafInfo)
                        caseLeafInfo
                      }
                      idx += 1
                    }
                    infos
                  }

                  new JsonBinaryCodec[A]() {
                    private[this] val root                   = new CaseNodeInfo(discr, getInfos(variant))
                    private[this] val caseLeafInfos          = allCaseLeafInfos.result()
                    private[this] val caseMap                = map
                    private[this] val discriminatorFieldName = fieldName

                    def decodeValue(in: JsonReader, default: A): A = {
                      in.setMark()
                      if (in.isNextToken('{')) {
                        if (in.skipToKey(discriminatorFieldName)) {
                          val idx = caseMap.get(in, in.readStringAsCharBuf())
                          if (idx >= 0) {
                            in.rollbackToMark()
                            val caseInfo = caseLeafInfos(idx)
                            val codec    = caseInfo.codec.asInstanceOf[JsonBinaryCodec[A]]
                            try codec.decodeValue(in, codec.nullValue)
                            catch {
                              case error if NonFatal(error) => in.decodeError(caseInfo.span, error)
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
                  }
                case DiscriminatorKind.None =>
                  def getInfos(variant: Reflect.Variant[F, A]): Array[CaseInfo] = {
                    val cases = variant.cases
                    val len   = cases.length
                    val infos = new Array[CaseInfo](len)
                    var idx   = 0
                    while (idx < len) {
                      val case_       = cases(idx)
                      val caseReflect = case_.value
                      infos(idx) = if (caseReflect.isVariant) {
                        val discr = discriminator(caseReflect)
                        new CaseNodeInfo(discr, getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]))
                      } else {
                        val caseLeafInfo = new CaseLeafInfo("", deriveCodec(caseReflect), null)
                        allCaseLeafInfos.addOne(caseLeafInfo)
                        caseLeafInfo
                      }
                      idx += 1
                    }
                    infos
                  }

                  new JsonBinaryCodec[A]() {
                    private[this] val root          = new CaseNodeInfo(discr, getInfos(variant))
                    private[this] val caseLeafInfos = allCaseLeafInfos.result()

                    def decodeValue(in: JsonReader, default: A): A = {
                      var idx = 0
                      while (idx < caseLeafInfos.length) {
                        val caseInfo = caseLeafInfos(idx)
                        in.setMark()
                        val codec = caseInfo.codec.asInstanceOf[JsonBinaryCodec[A]]
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
                  }
                case _ =>
                  def getInfos(variant: Reflect.Variant[F, A]): Array[CaseInfo] = {
                    val cases = variant.cases
                    val len   = cases.length
                    val infos = new Array[CaseInfo](len)
                    var idx   = 0
                    while (idx < len) {
                      val case_       = cases(idx)
                      val caseReflect = case_.value
                      infos(idx) = if (caseReflect.isVariant) {
                        val discr = discriminator(caseReflect)
                        new CaseNodeInfo(discr, getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]))
                      } else {
                        val infosIdx     = allCaseLeafInfos.length
                        var name: String = null
                        case_.modifiers.foreach {
                          case m: Modifier.rename => if (name eq null) name = m.name
                          case m: Modifier.alias  => map.put(m.name, infosIdx)
                          case _                  =>
                        }
                        if (name eq null) name = caseNameMapper(case_.name)
                        map.put(name, infosIdx)
                        val span         = new DynamicOptic.Node.Case(case_.name)
                        val caseLeafInfo = new CaseLeafInfo(name, deriveCodec(caseReflect), span)
                        allCaseLeafInfos.addOne(caseLeafInfo)
                        caseLeafInfo
                      }
                      idx += 1
                    }
                    infos
                  }

                  new JsonBinaryCodec[A]() {
                    private[this] val root          = new CaseNodeInfo(discr, getInfos(variant))
                    private[this] val caseLeafInfos = allCaseLeafInfos.result()
                    private[this] val caseMap       = map

                    def decodeValue(in: JsonReader, default: A): A =
                      if (in.isNextToken('{')) {
                        if (!in.isNextToken('}')) {
                          in.rollbackToken()
                          val idx = caseMap.get(in, in.readKeyAsCharBuf())
                          if (idx >= 0) {
                            val caseInfo = caseLeafInfos(idx)
                            val codec    = caseInfo.codec.asInstanceOf[JsonBinaryCodec[A]]
                            val x        =
                              try codec.decodeValue(in, codec.nullValue)
                              catch {
                                case error if NonFatal(error) => in.decodeError(caseInfo.span, error)
                              }
                            if (!in.isNextToken('}')) in.objectEndOrCommaError()
                            return x
                          }
                        }
                        in.discriminatorError()
                      } else in.readNullOrTokenError(default, '{')

                    def encodeValue(x: A, out: JsonWriter): Unit = {
                      out.writeObjectStart()
                      val caseInfo = root.discriminate(x)
                      caseInfo.writeKey(out)
                      caseInfo.codec.asInstanceOf[JsonBinaryCodec[A]].encodeValue(x, out)
                      out.writeObjectEnd()
                    }
                  }
              }
            }
        }
      } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isSequence) {
      val sequence = reflect.asSequenceUnknown.get.sequence
      if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
        val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
        val codec   = deriveCodec(sequence.element).asInstanceOf[JsonBinaryCodec[Elem]]
        codec.valueType match {
          case JsonBinaryCodec.intType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            new JsonBinaryCodec[Col[Int]]() {
              private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Int]]
              private[this] val optimize      = elementCodec eq intCodec

              def decodeValue(in: JsonReader, default: Col[Int]): Col[Int] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newIntBuilder()
                    var idx     = -1
                    try {
                      if (optimize) {
                        while ({
                          idx += 1
                          constructor.addInt(builder, in.readInt())
                          in.isNextToken(',')
                        }) ()
                      } else {
                        while ({
                          idx += 1
                          constructor.addInt(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      }
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultInt(builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Int], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val len = deconstructor.size(x)
                var idx = 0
                if (optimize) {
                  while (idx < len) {
                    out.writeVal(deconstructor.intAt(x, idx))
                    idx += 1
                  }
                } else {
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.intAt(x, idx), out)
                    idx += 1
                  }
                }
                out.writeArrayEnd()
              }

              override def nullValue: Col[Int] = constructor.resultInt(constructor.newIntBuilder(0))
            }
          case JsonBinaryCodec.longType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            new JsonBinaryCodec[Col[Long]]() {
              private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Long]]
              private[this] val optimize      = elementCodec eq longCodec

              def decodeValue(in: JsonReader, default: Col[Long]): Col[Long] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newLongBuilder()
                    var idx     = -1
                    try {
                      if (optimize) {
                        while ({
                          idx += 1
                          constructor.addLong(builder, in.readLong())
                          in.isNextToken(',')
                        }) ()
                      } else {
                        while ({
                          idx += 1
                          constructor.addLong(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      }
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultLong(builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Long], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val len = deconstructor.size(x)
                var idx = 0
                if (optimize) {
                  while (idx < len) {
                    out.writeVal(deconstructor.longAt(x, idx))
                    idx += 1
                  }
                } else {
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.longAt(x, idx), out)
                    idx += 1
                  }
                }
                out.writeArrayEnd()
              }

              override def nullValue: Col[Long] = constructor.resultLong(constructor.newLongBuilder(0))
            }
          case JsonBinaryCodec.floatType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            new JsonBinaryCodec[Col[Float]]() {
              private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Float]]
              private[this] val optimize      = elementCodec eq floatCodec

              def decodeValue(in: JsonReader, default: Col[Float]): Col[Float] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newFloatBuilder()
                    var idx     = -1
                    try {
                      if (optimize) {
                        while ({
                          idx += 1
                          constructor.addFloat(builder, in.readFloat())
                          in.isNextToken(',')
                        }) ()
                      } else {
                        while ({
                          idx += 1
                          constructor.addFloat(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      }
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultFloat(builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Float], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val len = deconstructor.size(x)
                var idx = 0
                if (optimize) {
                  while (idx < len) {
                    out.writeVal(deconstructor.floatAt(x, idx))
                    idx += 1
                  }
                } else {
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.floatAt(x, idx), out)
                    idx += 1
                  }
                }
                out.writeArrayEnd()
              }

              override def nullValue: Col[Float] = constructor.resultFloat(constructor.newFloatBuilder(0))
            }
          case JsonBinaryCodec.doubleType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            new JsonBinaryCodec[Col[Double]]() {
              private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Double]]
              private[this] val optimize      = elementCodec eq doubleCodec

              def decodeValue(in: JsonReader, default: Col[Double]): Col[Double] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newDoubleBuilder()
                    var idx     = -1
                    try {
                      if (optimize) {
                        while ({
                          idx += 1
                          constructor.addDouble(builder, in.readDouble())
                          in.isNextToken(',')
                        }) ()
                      } else {
                        while ({
                          idx += 1
                          constructor.addDouble(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      }
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultDouble(builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Double], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val len = deconstructor.size(x)
                var idx = 0
                if (optimize) {
                  while (idx < len) {
                    out.writeVal(deconstructor.doubleAt(x, idx))
                    idx += 1
                  }
                } else {
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.doubleAt(x, idx), out)
                    idx += 1
                  }
                }
                out.writeArrayEnd()
              }

              override def nullValue: Col[Double] = constructor.resultDouble(constructor.newDoubleBuilder(0))
            }
          case JsonBinaryCodec.booleanType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            new JsonBinaryCodec[Col[Boolean]]() {
              private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Boolean]]
              private[this] val optimize      = elementCodec eq booleanCodec

              def decodeValue(in: JsonReader, default: Col[Boolean]): Col[Boolean] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newBooleanBuilder()
                    var idx     = -1
                    try {
                      if (optimize) {
                        while ({
                          idx += 1
                          constructor.addBoolean(builder, in.readBoolean())
                          in.isNextToken(',')
                        }) ()
                      } else {
                        while ({
                          idx += 1
                          constructor.addBoolean(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      }
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultBoolean(builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Boolean], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val len = deconstructor.size(x)
                var idx = 0
                if (optimize) {
                  while (idx < len) {
                    out.writeVal(deconstructor.booleanAt(x, idx))
                    idx += 1
                  }
                } else {
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.booleanAt(x, idx), out)
                    idx += 1
                  }
                }
                out.writeArrayEnd()
              }

              override def nullValue: Col[Boolean] = constructor.resultBoolean(constructor.newBooleanBuilder(0))
            }
          case JsonBinaryCodec.byteType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            new JsonBinaryCodec[Col[Byte]]() {
              private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Byte]]
              private[this] val optimize      = elementCodec eq byteCodec

              def decodeValue(in: JsonReader, default: Col[Byte]): Col[Byte] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newByteBuilder()
                    var idx     = -1
                    try {
                      if (optimize) {
                        while ({
                          idx += 1
                          constructor.addByte(builder, in.readByte())
                          in.isNextToken(',')
                        }) ()
                      } else {
                        while ({
                          idx += 1
                          constructor.addByte(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      }
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultByte(builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Byte], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val len = deconstructor.size(x)
                var idx = 0
                if (optimize) {
                  while (idx < len) {
                    out.writeVal(deconstructor.byteAt(x, idx))
                    idx += 1
                  }
                } else {
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.byteAt(x, idx), out)
                    idx += 1
                  }
                }
                out.writeArrayEnd()
              }

              override def nullValue: Col[Byte] = constructor.resultByte(constructor.newByteBuilder(0))
            }
          case JsonBinaryCodec.charType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            new JsonBinaryCodec[Col[Char]]() {
              private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Char]]
              private[this] val optimize      = elementCodec eq charCodec

              def decodeValue(in: JsonReader, default: Col[Char]): Col[Char] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newCharBuilder()
                    var idx     = -1
                    try {
                      if (optimize) {
                        while ({
                          idx += 1
                          constructor.addChar(builder, in.readChar())
                          in.isNextToken(',')
                        }) ()
                      } else {
                        while ({
                          idx += 1
                          constructor.addChar(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      }
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultChar(builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Char], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val len = deconstructor.size(x)
                var idx = 0
                if (optimize) {
                  while (idx < len) {
                    out.writeVal(deconstructor.charAt(x, idx))
                    idx += 1
                  }
                } else {
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.charAt(x, idx), out)
                    idx += 1
                  }
                }
                out.writeArrayEnd()
              }

              override def nullValue: Col[Char] = constructor.resultChar(constructor.newCharBuilder(0))
            }
          case JsonBinaryCodec.shortType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
            new JsonBinaryCodec[Col[Short]]() {
              private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec.asInstanceOf[JsonBinaryCodec[Short]]
              private[this] val optimize      = elementCodec eq shortCodec

              def decodeValue(in: JsonReader, default: Col[Short]): Col[Short] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newShortBuilder()
                    var idx     = -1
                    try {
                      if (optimize) {
                        while ({
                          idx += 1
                          constructor.addShort(builder, in.readShort())
                          in.isNextToken(',')
                        }) ()
                      } else {
                        while ({
                          idx += 1
                          constructor.addShort(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                          in.isNextToken(',')
                        }) ()
                      }
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultShort(builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Short], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val len = deconstructor.size(x)
                var idx = 0
                if (optimize) {
                  while (idx < len) {
                    out.writeVal(deconstructor.shortAt(x, idx))
                    idx += 1
                  }
                } else {
                  while (idx < len) {
                    elementCodec.encodeValue(deconstructor.shortAt(x, idx), out)
                    idx += 1
                  }
                }
                out.writeArrayEnd()
              }

              override def nullValue: Col[Short] = constructor.resultShort(constructor.newShortBuilder(0))
            }
          case _ =>
            new JsonBinaryCodec[Col[Elem]]() {
              private[this] val deconstructor = binding.deconstructor
              private[this] val constructor   = binding.constructor
              private[this] val elementCodec  = codec

              def decodeValue(in: JsonReader, default: Col[Elem]): Col[Elem] =
                if (in.isNextToken('[')) {
                  if (in.isNextToken(']')) default
                  else {
                    in.rollbackToken()
                    val builder = constructor.newObjectBuilder[Elem](8)
                    var idx     = -1
                    try {
                      while ({
                        idx += 1
                        constructor.addObject(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                        in.isNextToken(',')
                      }) ()
                    } catch {
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    if (in.isCurrentToken(']')) constructor.resultObject[Elem](builder)
                    else in.arrayEndOrCommaError()
                  }
                } else in.readNullOrTokenError(default, '[')

              def encodeValue(x: Col[Elem], out: JsonWriter): Unit = {
                out.writeArrayStart()
                val it = deconstructor.deconstruct(x)
                while (it.hasNext) elementCodec.encodeValue(it.next(), out)
                out.writeArrayEnd()
              }

              override def nullValue: Col[Elem] = constructor.resultObject(constructor.newObjectBuilder(0))
            }
        }
      } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isMap) {
      val map = reflect.asMapUnknown.get.map
      if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
        val binding = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
        val codec1  = deriveCodec(map.key).asInstanceOf[JsonBinaryCodec[Key]]
        val codec2  = deriveCodec(map.value).asInstanceOf[JsonBinaryCodec[Value]]
        new JsonBinaryCodec[Map[Key, Value]]() {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor
          private[this] val keyCodec      = codec1
          private[this] val valueCodec    = codec2

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
                      case error if NonFatal(error) => in.decodeError(new DynamicOptic.Node.AtMapKey(k), error)
                    }
                  constructor.addObject(builder, k, v)
                  in.isNextToken(',')
                }) ()
                if (in.isCurrentToken('}')) constructor.resultObject[Key, Value](builder)
                else in.objectEndOrCommaError()
              }
            } else in.readNullOrTokenError(default, '[')

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

          override def nullValue: Map[Key, Value] = constructor.resultObject(constructor.newObjectBuilder(0))
        }
      } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isRecord) {
      val record = reflect.asRecord.get
      if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
        val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]
        val fields  = record.fields
        val len     = fields.length
        if (isTuple(reflect)) {
          val codecs      = new Array[JsonBinaryCodec[?]](len)
          var offset, idx = 0
          while (idx < len) {
            val codec = deriveCodec(fields(idx).value)
            codecs(idx) = codec
            offset += codec.valueOffset
            idx += 1
          }
          new JsonBinaryCodec[A]() {
            private[this] val deconstructor = binding.deconstructor
            private[this] val constructor   = binding.constructor
            private[this] val fieldCodecs   = codecs
            private[this] val usedRegisters = offset

            override def decodeValue(in: JsonReader, default: A): A =
              if (in.isNextToken('[')) {
                val regs = Registers(usedRegisters)
                if (!in.isNextToken(']')) {
                  in.rollbackToken()
                  val len         = fieldCodecs.length
                  var offset, idx = 0
                  while (idx < len && (idx == 0 || in.isNextToken(',') || in.commaError())) {
                    val codec = fieldCodecs(idx)
                    try {
                      codec.valueType match {
                        case JsonBinaryCodec.objectType =>
                          val objCodec = codec.asInstanceOf[JsonBinaryCodec[AnyRef]]
                          regs.setObject(offset, 0, objCodec.decodeValue(in, objCodec.nullValue))
                        case JsonBinaryCodec.intType =>
                          val value =
                            if (codec eq intCodec) in.readInt()
                            else {
                              val intCodec = codec.asInstanceOf[JsonBinaryCodec[Int]]
                              intCodec.decodeValue(in, intCodec.nullValue)
                            }
                          regs.setInt(offset, 0, value)
                        case JsonBinaryCodec.longType =>
                          val value =
                            if (codec eq longCodec) in.readLong()
                            else {
                              val longCodec = codec.asInstanceOf[JsonBinaryCodec[Long]]
                              longCodec.decodeValue(in, longCodec.nullValue)
                            }
                          regs.setLong(offset, 0, value)
                        case JsonBinaryCodec.floatType =>
                          val value =
                            if (codec eq floatCodec) in.readFloat()
                            else {
                              val floatCodec = codec.asInstanceOf[JsonBinaryCodec[Float]]
                              floatCodec.decodeValue(in, floatCodec.nullValue)
                            }
                          regs.setFloat(offset, 0, value)
                        case JsonBinaryCodec.doubleType =>
                          val value =
                            if (codec eq doubleCodec) in.readDouble()
                            else {
                              val doubleCodec = codec.asInstanceOf[JsonBinaryCodec[Double]]
                              doubleCodec.decodeValue(in, doubleCodec.nullValue)
                            }
                          regs.setDouble(offset, 0, value)
                        case JsonBinaryCodec.booleanType =>
                          val value =
                            if (codec eq booleanCodec) in.readBoolean()
                            else {
                              val booleanCodec = codec.asInstanceOf[JsonBinaryCodec[Boolean]]
                              booleanCodec.decodeValue(in, booleanCodec.nullValue)
                            }
                          regs.setBoolean(offset, 0, value)
                        case JsonBinaryCodec.byteType =>
                          val value =
                            if (codec eq byteCodec) in.readByte()
                            else {
                              val byteCodec = codec.asInstanceOf[JsonBinaryCodec[Byte]]
                              byteCodec.decodeValue(in, byteCodec.nullValue)
                            }
                          regs.setByte(offset, 0, value)
                        case JsonBinaryCodec.charType =>
                          val value =
                            if (codec eq charCodec) in.readChar()
                            else {
                              val charCodec = codec.asInstanceOf[JsonBinaryCodec[Char]]
                              charCodec.decodeValue(in, charCodec.nullValue)
                            }
                          regs.setChar(offset, 0, value)
                        case JsonBinaryCodec.shortType =>
                          val value =
                            if (codec eq shortCodec) in.readShort()
                            else {
                              val shortCodec = codec.asInstanceOf[JsonBinaryCodec[Short]]
                              shortCodec.decodeValue(in, shortCodec.nullValue)
                            }
                          regs.setShort(offset, 0, value)
                        case _ =>
                          val unitCodec = codec.asInstanceOf[JsonBinaryCodec[Unit]]
                          unitCodec.decodeValue(in, unitCodec.nullValue)
                      }
                    } catch {
                      case error if NonFatal(error) =>
                        in.decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                    }
                    offset += codec.valueOffset
                    idx += 1
                  }
                  if (!in.isNextToken(']')) in.arrayEndError()
                }
                constructor.construct(regs, 0)
              } else in.readNullOrTokenError(default, '[')

            override def encodeValue(x: A, out: JsonWriter): Unit = {
              out.writeArrayStart()
              val regs = Registers(usedRegisters)
              deconstructor.deconstruct(regs, 0, x)
              val len         = fieldCodecs.length
              var offset, idx = 0
              while (idx < len) {
                val codec = fieldCodecs(idx)
                codec.valueType match {
                  case JsonBinaryCodec.objectType =>
                    val value = regs.getObject(offset, 0)
                    codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(value, out)
                  case JsonBinaryCodec.intType =>
                    val value = regs.getInt(offset, 0)
                    if (codec eq intCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Int]].encodeValue(value, out)
                  case JsonBinaryCodec.longType =>
                    val value = regs.getLong(offset, 0)
                    if (codec eq longCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Long]].encodeValue(value, out)
                  case JsonBinaryCodec.floatType =>
                    val value = regs.getFloat(offset, 0)
                    if (codec eq floatCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Float]].encodeValue(value, out)
                  case JsonBinaryCodec.doubleType =>
                    val value = regs.getDouble(offset, 0)
                    if (codec eq doubleCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Double]].encodeValue(value, out)
                  case JsonBinaryCodec.booleanType =>
                    val value = regs.getBoolean(offset, 0)
                    if (codec eq booleanCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Boolean]].encodeValue(value, out)
                  case JsonBinaryCodec.byteType =>
                    val value = regs.getByte(offset, 0)
                    if (codec eq byteCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Byte]].encodeValue(value, out)
                  case JsonBinaryCodec.charType =>
                    val value = regs.getChar(offset, 0)
                    if (codec eq charCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Char]].encodeValue(value, out)
                  case JsonBinaryCodec.shortType =>
                    val value = regs.getShort(offset, 0)
                    if (codec eq shortCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Short]].encodeValue(value, out)
                  case _ => codec.asInstanceOf[JsonBinaryCodec[Unit]].encodeValue((), out)
                }
                offset += codec.valueOffset
                idx += 1
              }
              out.writeArrayEnd()
            }
          }
        } else {
          val isRecursive = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
          val typeName    = record.typeName
          var infos       =
            if (isRecursive) recursiveRecordCache.get.get(typeName)
            else null
          val deriveCodecs = infos eq null
          val map          = new StringToIntMap(len)
          var offset       = 0
          if (deriveCodecs) {
            infos = new Array[FieldInfo](len)
            if (isRecursive) recursiveRecordCache.get.put(typeName, infos)
            discriminatorFields.set(null :: discriminatorFields.get)
          }
          var idx = 0
          while (idx < len) {
            val field        = fields(idx)
            var name: String = null
            field.modifiers.foreach {
              case m: Modifier.rename => if (name eq null) name = m.name
              case m: Modifier.alias  => map.put(m.name, idx)
              case _                  =>
            }
            if (name eq null) name = fieldNameMapper(field.name)
            map.put(name, idx)
            val fieldReflect = field.value
            val isOpt        = isOptional(fieldReflect)
            val isColl       = isCollection(fieldReflect)
            val defVal       = defaultValue(fieldReflect)
            if (deriveCodecs) {
              val nonTransient = !field.modifiers.exists(_.isInstanceOf[Modifier.transient])
              val codec        = deriveCodec(fieldReflect)
              val span         = new DynamicOptic.Node.Field(field.name)
              infos(idx) = new FieldInfo(name, codec, offset, isOpt, isColl, nonTransient, defVal, span)
              offset += codec.valueOffset
            }
            idx += 1
          }
          if (deriveCodecs) discriminatorFields.set(discriminatorFields.get.tail)
          new JsonBinaryCodec[A]() {
            private[this] val deconstructor       = binding.deconstructor
            private[this] val constructor         = binding.constructor
            private[this] val fieldInfos          = infos
            private[this] val fieldIndexMap       = map
            private[this] val discriminatorField  = discriminatorFields.get.headOption.orNull
            private[this] val usedRegisters       = offset
            private[this] val skipNone            = transientNone
            private[this] val skipEmptyCollection = transientEmptyCollection
            private[this] val skipDefaultValue    = transientDefaultValue
            private[this] val doReject            = rejectExtraFields

            require(fieldInfos.length <= 128, "expected up to 128 fields")

            override def decodeValue(in: JsonReader, default: A): A =
              if (in.isNextToken('{')) {
                val regs             = Registers(usedRegisters)
                var field: FieldInfo = null
                val len              = fieldInfos.length
                var seen1, seen2     = 0L
                var idx, keyLen      = -1
                if (!in.isNextToken('}')) {
                  in.rollbackToken()
                  while (keyLen < 0 || in.isNextToken(',')) {
                    keyLen = in.readKeyAsCharBuf()
                    if (
                      len > 0 && {
                        idx += 1
                        if (idx == len) idx = 0
                        field = fieldInfos(idx)
                        (in.isCharBufEqualsTo(keyLen, field.name) || {
                          val keyIdx = fieldIndexMap.get(in, keyLen)
                          (keyIdx >= 0) && {
                            field = fieldInfos(keyIdx)
                            idx = keyIdx
                            true
                          }
                        }) && field.nonTransient
                      }
                    ) {
                      if (idx < 64) {
                        val mask = 1L << idx
                        if ((seen1 & mask) != 0L) in.duplicatedKeyError(keyLen)
                        seen1 ^= mask
                      } else {
                        val mask = 1L << (idx - 64)
                        if ((seen2 & mask) != 0L) in.duplicatedKeyError(keyLen)
                        seen2 ^= mask
                      }
                      try {
                        val codec  = field.codec
                        val offset = field.offset
                        field.valueType match {
                          case JsonBinaryCodec.objectType =>
                            val objCodec = codec.asInstanceOf[JsonBinaryCodec[AnyRef]]
                            regs.setObject(offset, 0, objCodec.decodeValue(in, objCodec.nullValue))
                          case JsonBinaryCodec.intType =>
                            val value =
                              if (codec eq intCodec) in.readInt()
                              else {
                                val intCodec = codec.asInstanceOf[JsonBinaryCodec[Int]]
                                intCodec.decodeValue(in, intCodec.nullValue)
                              }
                            regs.setInt(offset, 0, value)
                          case JsonBinaryCodec.longType =>
                            val value =
                              if (codec eq longCodec) in.readLong()
                              else {
                                val longCodec = codec.asInstanceOf[JsonBinaryCodec[Long]]
                                longCodec.decodeValue(in, longCodec.nullValue)
                              }
                            regs.setLong(offset, 0, value)
                          case JsonBinaryCodec.floatType =>
                            val value =
                              if (codec eq floatCodec) in.readFloat()
                              else {
                                val floatCodec = codec.asInstanceOf[JsonBinaryCodec[Float]]
                                floatCodec.decodeValue(in, floatCodec.nullValue)
                              }
                            regs.setFloat(offset, 0, value)
                          case JsonBinaryCodec.doubleType =>
                            val value =
                              if (codec eq doubleCodec) in.readDouble()
                              else {
                                val doubleCodec = codec.asInstanceOf[JsonBinaryCodec[Double]]
                                doubleCodec.decodeValue(in, doubleCodec.nullValue)
                              }
                            regs.setDouble(offset, 0, value)
                          case JsonBinaryCodec.booleanType =>
                            val value =
                              if (codec eq booleanCodec) in.readBoolean()
                              else {
                                val booleanCodec = codec.asInstanceOf[JsonBinaryCodec[Boolean]]
                                booleanCodec.decodeValue(in, booleanCodec.nullValue)
                              }
                            regs.setBoolean(offset, 0, value)
                          case JsonBinaryCodec.byteType =>
                            val value =
                              if (codec eq byteCodec) in.readByte()
                              else {
                                val byteCodec = codec.asInstanceOf[JsonBinaryCodec[Byte]]
                                byteCodec.decodeValue(in, byteCodec.nullValue)
                              }
                            regs.setByte(offset, 0, value)
                          case JsonBinaryCodec.charType =>
                            val value =
                              if (codec eq charCodec) in.readChar()
                              else {
                                val charCodec = codec.asInstanceOf[JsonBinaryCodec[Char]]
                                charCodec.decodeValue(in, charCodec.nullValue)
                              }
                            regs.setChar(offset, 0, value)
                          case JsonBinaryCodec.shortType =>
                            val value =
                              if (codec eq shortCodec) in.readShort()
                              else {
                                val shortCodec = codec.asInstanceOf[JsonBinaryCodec[Short]]
                                shortCodec.decodeValue(in, shortCodec.nullValue)
                              }
                            regs.setShort(offset, 0, value)
                          case _ =>
                            val unitCodec = codec.asInstanceOf[JsonBinaryCodec[Unit]]
                            unitCodec.decodeValue(in, unitCodec.nullValue)
                        }
                      } catch {
                        case error if NonFatal(error) => in.decodeError(field.span, error)
                      }
                    } else skipOrReject(in, keyLen)
                  }
                  if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
                }
                idx = 0
                while (idx < len) {
                  if (
                    {
                      if (idx < 64) seen1 & (1L << idx)
                      else seen2 & (1L << (idx - 64))
                    } == 0L
                  ) {
                    val field = fieldInfos(idx)
                    if (field.hasDefault) {
                      val value  = field.defaultValueConstructor.get.apply()
                      val offset = field.offset
                      field.valueType match {
                        case JsonBinaryCodec.objectType  => regs.setObject(offset, 0, value.asInstanceOf[AnyRef])
                        case JsonBinaryCodec.intType     => regs.setInt(offset, 0, value.asInstanceOf[Int])
                        case JsonBinaryCodec.longType    => regs.setLong(offset, 0, value.asInstanceOf[Long])
                        case JsonBinaryCodec.floatType   => regs.setFloat(offset, 0, value.asInstanceOf[Float])
                        case JsonBinaryCodec.doubleType  => regs.setDouble(offset, 0, value.asInstanceOf[Double])
                        case JsonBinaryCodec.booleanType => regs.setBoolean(offset, 0, value.asInstanceOf[Boolean])
                        case JsonBinaryCodec.byteType    => regs.setByte(offset, 0, value.asInstanceOf[Byte])
                        case JsonBinaryCodec.charType    => regs.setChar(offset, 0, value.asInstanceOf[Char])
                        case JsonBinaryCodec.shortType   => regs.setShort(offset, 0, value.asInstanceOf[Short])
                        case _                           =>
                      }
                    } else if (field.isOptional) regs.setObject(field.offset, 0, None)
                    else if (field.isCollection) {
                      regs.setObject(field.offset, 0, field.codec.nullValue.asInstanceOf[AnyRef])
                    } else in.requiredFieldError(fieldInfos(idx).name)
                  }
                  idx += 1
                }
                constructor.construct(regs, 0)
              } else in.readNullOrTokenError(default, '{')

            override def encodeValue(x: A, out: JsonWriter): Unit = {
              out.writeObjectStart()
              if (discriminatorField ne null) {
                out.writeKey(discriminatorField._1)
                out.writeVal(discriminatorField._2)
              }
              val regs = Registers(usedRegisters)
              deconstructor.deconstruct(regs, 0, x)
              val len = fieldInfos.length
              var idx = 0
              while (idx < len) {
                val field = fieldInfos(idx)
                if (field.nonTransient) {
                  if (skipDefaultValue && field.hasDefault) writeDefaultValue(out, field, regs)
                  else if (skipNone && field.isOptional) writeOptional(out, field, regs)
                  else if (skipEmptyCollection && field.isCollection) writeCollection(out, field, regs)
                  else writeRequired(out, field, regs)
                }
                idx += 1
              }
              out.writeObjectEnd()
            }

            private[this] def writeDefaultValue(out: JsonWriter, field: FieldInfo, regs: Registers): Unit = {
              val default = field.defaultValueConstructor.get.apply()
              val offset  = field.offset
              val codec   = field.codec
              field.valueType match {
                case JsonBinaryCodec.objectType =>
                  val value = regs.getObject(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(value, out)
                  }
                case JsonBinaryCodec.intType =>
                  val value = regs.getInt(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    if (codec eq intCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Int]].encodeValue(value, out)
                  }
                case JsonBinaryCodec.longType =>
                  val value = regs.getLong(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    if (codec eq longCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Long]].encodeValue(value, out)
                  }
                case JsonBinaryCodec.floatType =>
                  val value = regs.getFloat(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    if (codec eq floatCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Float]].encodeValue(value, out)
                  }
                case JsonBinaryCodec.doubleType =>
                  val value = regs.getDouble(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    if (codec eq doubleCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Double]].encodeValue(value, out)
                  }
                case JsonBinaryCodec.booleanType =>
                  val value = regs.getBoolean(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    if (codec eq booleanCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Boolean]].encodeValue(value, out)
                  }
                case JsonBinaryCodec.byteType =>
                  val value = regs.getByte(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    if (codec eq byteCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Byte]].encodeValue(value, out)
                  }
                case JsonBinaryCodec.charType =>
                  val value = regs.getChar(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    if (codec eq charCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Char]].encodeValue(value, out)
                  }
                case JsonBinaryCodec.shortType =>
                  val value = regs.getShort(offset, 0)
                  if (value != default) {
                    field.writeKey(out)
                    if (codec eq shortCodec) out.writeVal(value)
                    else codec.asInstanceOf[JsonBinaryCodec[Short]].encodeValue(value, out)
                  }
                case _ =>
              }
            }

            private[this] def writeOptional(out: JsonWriter, field: FieldInfo, regs: Registers): Unit = {
              val value = regs.getObject(field.offset, 0)
              if (value ne None) {
                field.writeKey(out)
                field.codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(value, out)
              }
            }

            private[this] def writeCollection(out: JsonWriter, field: FieldInfo, regs: Registers): Unit = {
              val value = regs.getObject(field.offset, 0)
              if (value.asInstanceOf[Iterable[?]].nonEmpty) {
                field.writeKey(out)
                field.codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(value, out)
              }
            }

            private[this] def writeRequired(out: JsonWriter, field: FieldInfo, regs: Registers): Unit = {
              field.writeKey(out)
              val offset = field.offset
              val codec  = field.codec
              field.valueType match {
                case JsonBinaryCodec.objectType =>
                  val value = regs.getObject(offset, 0)
                  codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(value, out)
                case JsonBinaryCodec.intType =>
                  val value = regs.getInt(offset, 0)
                  if (codec eq intCodec) out.writeVal(value)
                  else codec.asInstanceOf[JsonBinaryCodec[Int]].encodeValue(value, out)
                case JsonBinaryCodec.longType =>
                  val value = regs.getLong(offset, 0)
                  if (codec eq longCodec) out.writeVal(value)
                  else codec.asInstanceOf[JsonBinaryCodec[Long]].encodeValue(value, out)
                case JsonBinaryCodec.floatType =>
                  val value = regs.getFloat(offset, 0)
                  if (codec eq floatCodec) out.writeVal(value)
                  else codec.asInstanceOf[JsonBinaryCodec[Float]].encodeValue(value, out)
                case JsonBinaryCodec.doubleType =>
                  val value = regs.getDouble(offset, 0)
                  if (codec eq doubleCodec) out.writeVal(value)
                  else codec.asInstanceOf[JsonBinaryCodec[Double]].encodeValue(value, out)
                case JsonBinaryCodec.booleanType =>
                  val value = regs.getBoolean(offset, 0)
                  if (codec eq booleanCodec) out.writeVal(value)
                  else codec.asInstanceOf[JsonBinaryCodec[Boolean]].encodeValue(value, out)
                case JsonBinaryCodec.byteType =>
                  val value = regs.getByte(offset, 0)
                  if (codec eq byteCodec) out.writeVal(value)
                  else codec.asInstanceOf[JsonBinaryCodec[Byte]].encodeValue(value, out)
                case JsonBinaryCodec.charType =>
                  val value = regs.getChar(offset, 0)
                  if (codec eq charCodec) out.writeVal(value)
                  else codec.asInstanceOf[JsonBinaryCodec[Char]].encodeValue(value, out)
                case JsonBinaryCodec.shortType =>
                  val value = regs.getShort(offset, 0)
                  if (codec eq shortCodec) out.writeVal(value)
                  else codec.asInstanceOf[JsonBinaryCodec[Short]].encodeValue(value, out)
                case _ => codec.asInstanceOf[JsonBinaryCodec[Unit]].encodeValue((), out)
              }
            }

            private[this] def skipOrReject(in: JsonReader, keyLen: Int): Unit =
              if (doReject && ((discriminatorField eq null) || !in.isCharBufEqualsTo(keyLen, discriminatorField._1))) {
                in.unexpectedKeyError(keyLen)
              } else in.skip()
          }
        }
      } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isWrapper) {
      val wrapper = reflect.asWrapperUnknown.get.wrapper
      if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
        val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
        val codec   = deriveCodec(wrapper.wrapped).asInstanceOf[JsonBinaryCodec[Wrapped]]
        new JsonBinaryCodec[A]() {
          private[this] val unwrap       = binding.unwrap
          private[this] val wrap         = binding.wrap
          private[this] val wrappedCodec = codec

          override def decodeValue(in: JsonReader, default: A): A =
            wrap(
              try {
                wrappedCodec.decodeValue(
                  in, {
                    if (default == null) null
                    else unwrap(default)
                  }.asInstanceOf[Wrapped]
                )
              } catch {
                case error if NonFatal(error) => in.decodeError(DynamicOptic.Node.Wrapped, error)
              }
            ) match {
              case Right(x)    => x
              case Left(error) => in.decodeError(error)
            }

          override def encodeValue(x: A, out: JsonWriter): Unit = wrappedCodec.encodeValue(unwrap(x), out)

          override def decodeKey(in: JsonReader): A =
            wrap(
              try wrappedCodec.decodeKey(in)
              catch {
                case error if NonFatal(error) => in.decodeError(DynamicOptic.Node.Wrapped, error)
              }
            ) match {
              case Right(x)    => x
              case Left(error) => in.decodeError(error)
            }

          override def encodeKey(x: A, out: JsonWriter): Unit = wrappedCodec.encodeKey(unwrap(x), out)
        }
      } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else {
      val dynamic = reflect.asDynamic.get
      if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
      else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    }
  }.asInstanceOf[JsonBinaryCodec[A]]

  private[this] def isEnumeration[F[_, _], A](variant: Reflect.Variant[F, A]): Boolean =
    enumValuesAsStrings && variant.cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.asRecord.exists(_.fields.isEmpty) ||
      caseReflect.isVariant && caseReflect.asVariant.forall(isEnumeration)
    }

  private[this] def hasOnlyRecordAndVariantCases[F[_, _], A](variant: Reflect.Variant[F, A]): Boolean =
    variant.cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.isRecord || caseReflect.isVariant && caseReflect.asVariant.forall(hasOnlyRecordAndVariantCases)
    }

  private[this] def option[F[_, _], A](variant: Reflect.Variant[F, A]): Option[Reflect[F, ?]] = {
    val typeName = variant.typeName
    val cases    = variant.cases
    if (
      typeName.namespace == Namespace.scala && typeName.name == "Option" &&
      cases.length == 2 && cases(1).name == "Some"
    ) cases(1).value.asRecord.map(_.fields(0).value)
    else None
  }

  private[this] def isOptional[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    !requireOptionFields && reflect.isVariant && {
      val variant  = reflect.asVariant.get
      val typeName = reflect.typeName
      val cases    = variant.cases
      typeName.namespace == Namespace.scala && typeName.name == "Option" &&
      cases.length == 2 && cases(1).name == "Some"
    }

  private[this] def isCollection[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    !requireCollectionFields && (reflect.isSequence || reflect.isMap)

  private[this] def defaultValue[F[_, _], A](fieldReflect: Reflect[F, A]): Option[() => ?] =
    if (requireDefaultValueFields) None
    else
      {
        if (fieldReflect.isPrimitive) fieldReflect.asPrimitive.get.primitiveBinding
        else if (fieldReflect.isRecord) fieldReflect.asRecord.get.recordBinding
        else if (fieldReflect.isVariant) fieldReflect.asVariant.get.variantBinding
        else if (fieldReflect.isSequence) fieldReflect.asSequenceUnknown.get.sequence.seqBinding
        else if (fieldReflect.isMap) fieldReflect.asMapUnknown.get.map.mapBinding
        else if (fieldReflect.isWrapper) fieldReflect.asWrapperUnknown.get.wrapper.wrapperBinding
        else fieldReflect.asDynamic.get.dynamicBinding
      }.asInstanceOf[BindingInstance[TC, ?, A]].binding.defaultValue

  private[this] def discriminator[F[_, _], A](caseReflect: Reflect[F, A]): Discriminator[?] =
    caseReflect.asVariant.get.variantBinding
      .asInstanceOf[BindingInstance[TC, ?, ?]]
      .binding
      .asInstanceOf[Binding.Variant[A]]
      .discriminator

  private[this] def isTuple[F[_, _], A](reflect: Reflect[F, A]): Boolean = reflect.isRecord && {
    val typeName = reflect.typeName
    typeName.namespace == Namespace.scala && typeName.name.startsWith("Tuple")
  }

  private[this] val dynamicValueCodec = new JsonBinaryCodec[DynamicValue]() {
    private[this] val falseValue       = new DynamicValue.Primitive(new PrimitiveValue.Boolean(false))
    private[this] val trueValue        = new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
    private[this] val emptyArrayValue  = new DynamicValue.Sequence(Vector.empty)
    private[this] val emptyObjectValue = new DynamicValue.Map(Vector.empty)
    private[this] val unitValue        = new DynamicValue.Primitive(PrimitiveValue.Unit)

    def decodeValue(in: JsonReader, default: DynamicValue): DynamicValue = {
      val b = in.nextToken()
      if (b == '"') {
        in.rollbackToken()
        new DynamicValue.Primitive(new PrimitiveValue.String(in.readString(null)))
      } else if (b == 'f' || b == 't') {
        in.rollbackToken()
        if (in.readBoolean()) trueValue
        else falseValue
      } else if (b >= '0' && b <= '9' || b == '-') {
        in.rollbackToken()
        val n = in.readBigDecimal(null)
        new DynamicValue.Primitive({
          val longValue = n.bigDecimal.longValue
          if (n == BigDecimal(longValue)) {
            val intValue = longValue.toInt
            if (longValue == intValue) new PrimitiveValue.Int(intValue)
            else new PrimitiveValue.Long(longValue)
          } else new PrimitiveValue.BigDecimal(n)
        })
      } else if (b == '[') {
        if (in.isNextToken(']')) emptyArrayValue
        else {
          in.rollbackToken()
          val builder = new VectorBuilder[DynamicValue]
          while ({
            builder.addOne(decodeValue(in, default))
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken(']')) new DynamicValue.Sequence(builder.result())
          else in.arrayEndOrCommaError()
        }
      } else if (b == '{') {
        if (in.isNextToken('}')) emptyObjectValue
        else {
          in.rollbackToken()
          val builder = new VectorBuilder[(String, DynamicValue)]
          while ({
            builder.addOne((in.readKeyAsString(), decodeValue(in, default)))
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken('}')) new DynamicValue.Record(builder.result())
          else in.objectEndOrCommaError()
        }
      } else in.readNullOrError(unitValue, "expected JSON value")
    }

    def encodeValue(x: DynamicValue, out: JsonWriter): Unit = x match {
      case primitive: DynamicValue.Primitive =>
        primitive.value match {
          case _: PrimitiveValue.Unit.type      => out.writeNull()
          case v: PrimitiveValue.Boolean        => out.writeVal(v.value)
          case v: PrimitiveValue.Byte           => out.writeVal(v.value)
          case v: PrimitiveValue.Short          => out.writeVal(v.value)
          case v: PrimitiveValue.Int            => out.writeVal(v.value)
          case v: PrimitiveValue.Long           => out.writeVal(v.value)
          case v: PrimitiveValue.Float          => out.writeVal(v.value)
          case v: PrimitiveValue.Double         => out.writeVal(v.value)
          case v: PrimitiveValue.Char           => out.writeVal(v.value)
          case v: PrimitiveValue.String         => out.writeVal(v.value)
          case v: PrimitiveValue.BigInt         => out.writeVal(v.value)
          case v: PrimitiveValue.BigDecimal     => out.writeVal(v.value)
          case v: PrimitiveValue.DayOfWeek      => out.writeNonEscapedAsciiVal(v.value.toString)
          case v: PrimitiveValue.Duration       => out.writeVal(v.value)
          case v: PrimitiveValue.Instant        => out.writeVal(v.value)
          case v: PrimitiveValue.LocalDate      => out.writeVal(v.value)
          case v: PrimitiveValue.LocalDateTime  => out.writeVal(v.value)
          case v: PrimitiveValue.LocalTime      => out.writeVal(v.value)
          case v: PrimitiveValue.Month          => out.writeNonEscapedAsciiVal(v.value.toString)
          case v: PrimitiveValue.MonthDay       => out.writeVal(v.value)
          case v: PrimitiveValue.OffsetDateTime => out.writeVal(v.value)
          case v: PrimitiveValue.OffsetTime     => out.writeVal(v.value)
          case v: PrimitiveValue.Period         => out.writeVal(v.value)
          case v: PrimitiveValue.Year           => out.writeVal(v.value)
          case v: PrimitiveValue.YearMonth      => out.writeVal(v.value)
          case v: PrimitiveValue.ZoneId         => out.writeVal(v.value)
          case v: PrimitiveValue.ZoneOffset     => out.writeVal(v.value)
          case v: PrimitiveValue.ZonedDateTime  => out.writeVal(v.value)
          case v: PrimitiveValue.Currency       => out.writeNonEscapedAsciiVal(v.value.toString)
          case v: PrimitiveValue.UUID           => out.writeVal(v.value)
        }
      case record: DynamicValue.Record =>
        out.writeObjectStart()
        val fields = record.fields
        val it     = fields.iterator
        while (it.hasNext) {
          val kv = it.next()
          out.writeKey(kv._1)
          encodeValue(kv._2, out)
        }
        out.writeObjectEnd()
      case variant: DynamicValue.Variant =>
        out.writeObjectStart()
        out.writeKey(variant.caseName)
        encodeValue(variant.value, out)
        out.writeObjectEnd()
      case sequence: DynamicValue.Sequence =>
        out.writeArrayStart()
        val elements = sequence.elements
        val it       = elements.iterator
        while (it.hasNext) {
          encodeValue(it.next(), out)
        }
        out.writeArrayEnd()
      case map: DynamicValue.Map =>
        out.writeObjectStart()
        val entries = map.entries
        val it      = entries.iterator
        while (it.hasNext) {
          val kv = it.next()
          encodeKey(kv._1, out)
          encodeValue(kv._2, out)
        }
        out.writeObjectEnd()
    }

    override def encodeKey(x: DynamicValue, out: JsonWriter): Unit = x match {
      case primitive: DynamicValue.Primitive =>
        primitive.value match {
          case _: PrimitiveValue.Unit.type      => out.encodeError("encoding as JSON key is not supported")
          case v: PrimitiveValue.Boolean        => out.writeKey(v.value)
          case v: PrimitiveValue.Byte           => out.writeKey(v.value)
          case v: PrimitiveValue.Short          => out.writeKey(v.value)
          case v: PrimitiveValue.Int            => out.writeKey(v.value)
          case v: PrimitiveValue.Long           => out.writeKey(v.value)
          case v: PrimitiveValue.Float          => out.writeKey(v.value)
          case v: PrimitiveValue.Double         => out.writeKey(v.value)
          case v: PrimitiveValue.Char           => out.writeKey(v.value)
          case v: PrimitiveValue.String         => out.writeKey(v.value)
          case v: PrimitiveValue.BigInt         => out.writeKey(v.value)
          case v: PrimitiveValue.BigDecimal     => out.writeKey(v.value)
          case v: PrimitiveValue.DayOfWeek      => out.writeNonEscapedAsciiKey(v.value.toString)
          case v: PrimitiveValue.Duration       => out.writeKey(v.value)
          case v: PrimitiveValue.Instant        => out.writeKey(v.value)
          case v: PrimitiveValue.LocalDate      => out.writeKey(v.value)
          case v: PrimitiveValue.LocalDateTime  => out.writeKey(v.value)
          case v: PrimitiveValue.LocalTime      => out.writeKey(v.value)
          case v: PrimitiveValue.Month          => out.writeNonEscapedAsciiKey(v.value.toString)
          case v: PrimitiveValue.MonthDay       => out.writeKey(v.value)
          case v: PrimitiveValue.OffsetDateTime => out.writeKey(v.value)
          case v: PrimitiveValue.OffsetTime     => out.writeKey(v.value)
          case v: PrimitiveValue.Period         => out.writeKey(v.value)
          case v: PrimitiveValue.Year           => out.writeKey(v.value)
          case v: PrimitiveValue.YearMonth      => out.writeKey(v.value)
          case v: PrimitiveValue.ZoneId         => out.writeKey(v.value)
          case v: PrimitiveValue.ZoneOffset     => out.writeKey(v.value)
          case v: PrimitiveValue.ZonedDateTime  => out.writeKey(v.value)
          case v: PrimitiveValue.Currency       => out.writeNonEscapedAsciiKey(v.value.toString)
          case v: PrimitiveValue.UUID           => out.writeKey(v.value)
        }
      case _ => out.encodeError("encoding as JSON key is not supported")
    }
  }
}

trait Info {
  def isNonEscapedAscii(name: String): Boolean = {
    val len = name.length
    var idx = 0
    while (idx < len && JsonWriter.isNonEscapedAscii(name.charAt(idx))) idx += 1
    idx == len
  }
}

private case class FieldInfo(
  name: String,
  codec: JsonBinaryCodec[?],
  offset: RegisterOffset,
  isOptional: Boolean,
  isCollection: Boolean,
  nonTransient: Boolean,
  defaultValueConstructor: Option[() => ?],
  span: DynamicOptic.Node.Field
) extends Info {
  val valueType: Int                      = codec.valueType
  val hasDefault: Boolean                 = defaultValueConstructor ne None
  private[this] val isNonEscapedAsciiName = isNonEscapedAscii(name)

  def writeKey(out: JsonWriter): Unit =
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
    else out.writeKey(name)
}

trait CaseInfo extends Info

private case class CaseLeafInfo(name: String, codec: JsonBinaryCodec[?], span: DynamicOptic.Node.Case)
    extends CaseInfo {
  private[this] val isNonEscapedAsciiName = isNonEscapedAscii(name)

  def writeKey(out: JsonWriter): Unit =
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
    else out.writeKey(name)
}

private case class CaseNodeInfo[A](discriminator: Discriminator[A], caseInfos: Array[CaseInfo]) extends CaseInfo {
  def discriminate(x: A): CaseLeafInfo = caseInfos(discriminator.discriminate(x)) match {
    case eli: CaseLeafInfo => eli
    case eni               => eni.asInstanceOf[CaseNodeInfo[A]].discriminate(x)
  }
}

trait EnumInfo extends Info

private case class EnumLeafInfo(name: String, constructor: Constructor[?]) extends EnumInfo {
  private[this] val isNonEscapedAsciiName = isNonEscapedAscii(name)

  def writeVal(out: JsonWriter): Unit =
    if (isNonEscapedAsciiName) out.writeNonEscapedAsciiVal(name)
    else out.writeVal(name)
}

private case class EnumNodeInfo[A](discriminator: Discriminator[A], enumInfos: Array[EnumInfo]) extends EnumInfo {
  def discriminate(x: A): EnumLeafInfo = enumInfos(discriminator.discriminate(x)) match {
    case eli: EnumLeafInfo => eli
    case eni               => eni.asInstanceOf[EnumNodeInfo[A]].discriminate(x)
  }
}

private class StringToIntMap(initCapacity: Int) {
  private[this] var size   = 0
  private[this] var mask   = (Integer.highestOneBit(initCapacity | 1) << 2) - 1
  private[this] var keys   = new Array[String](mask + 1)
  private[this] var values = new Array[Int](mask + 1)

  def put(key: String, value: Int): Unit = {
    val len       = key.length
    var hash, idx = 0
    while (idx < len) {
      hash = (hash << 5) + (key.charAt(idx) - hash)
      idx += 1
    }
    idx = hash & mask
    var currKey: String = null
    while ({
      currKey = keys(idx)
      (currKey ne null) && !currKey.equals(key)
    }) idx = (idx + 1) & mask
    if (currKey ne null) sys.error(s"Cannot derive codec - duplicated name detected: '$key'")
    keys(idx) = key
    values(idx) = value
    size += 1
    if (size << 1 > mask) grow()
  }

  def get(in: JsonReader, len: Int): Int = {
    var idx             = in.charBufToHashCode(len) & mask
    var currKey: String = null
    while ({
      currKey = keys(idx)
      (currKey ne null) && !in.isCharBufEqualsTo(len, currKey)
    }) idx = (idx + 1) & mask
    if (currKey eq null) -1
    else values(idx)
  }

  private[this] def grow(): Unit = {
    val mask    = (Integer.highestOneBit(size | 1) << 2) - 1
    val keys    = new Array[String](mask + 1)
    val values  = new Array[Int](mask + 1)
    val keysLen = this.keys.length
    var keysIdx = 0
    while (keysIdx < keysLen) {
      val key = this.keys(keysIdx)
      if (key ne null) {
        val len       = key.length
        var hash, idx = 0
        while (idx < len) {
          hash = (hash << 5) + (key.charAt(idx) - hash)
          idx += 1
        }
        idx = hash & mask
        var currKey: String = null
        while ({
          currKey = keys(idx)
          (currKey ne null) && !currKey.equals(key)
        }) idx = (idx + 1) & mask
        keys(idx) = key
        values(idx) = this.values(keysIdx)
      }
      keysIdx += 1
    }
    this.mask = mask
    this.keys = keys
    this.values = values
  }
}
