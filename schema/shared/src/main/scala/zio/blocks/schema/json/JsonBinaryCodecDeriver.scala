package zio.blocks.schema.json

import zio.blocks.schema.json._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers}
import zio.blocks.schema._
import zio.blocks.schema.binding.Constructor
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import scala.collection.immutable.VectorBuilder
import scala.util.control.NonFatal

object JsonFormat extends BinaryFormat("application/json", JsonBinaryCodecDeriver)

object JsonBinaryCodecDeriver
    extends JsonBinaryCodecDeriver(
      fieldNameMapper = NameMapper.Identity,
      caseNameMapper = NameMapper.Identity,
      rejectExtraFields = false,
      discriminatorKind = DiscriminatorKind.Key
    )

class JsonBinaryCodecDeriver private[json] (
  fieldNameMapper: NameMapper,
  caseNameMapper: NameMapper,
  rejectExtraFields: Boolean,
  discriminatorKind: DiscriminatorKind
) extends Deriver[JsonBinaryCodec] {
  def withFieldNameMapper(fieldNameMapper: NameMapper): JsonBinaryCodecDeriver = copy(fieldNameMapper = fieldNameMapper)

  def withCaseNameMapper(caseNameMapper: NameMapper): JsonBinaryCodecDeriver = copy(caseNameMapper = caseNameMapper)

  def withRejectExtraFields(rejectExtraFields: Boolean): JsonBinaryCodecDeriver =
    copy(rejectExtraFields = rejectExtraFields)

  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): JsonBinaryCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  private def copy(
    fieldNameMapper: NameMapper = fieldNameMapper,
    caseNameMapper: NameMapper = caseNameMapper,
    rejectExtraFields: Boolean = rejectExtraFields,
    discriminatorKind: DiscriminatorKind = discriminatorKind
  ) =
    new JsonBinaryCodecDeriver(
      fieldNameMapper,
      caseNameMapper,
      rejectExtraFields,
      discriminatorKind
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
        fields = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
        typeName = typeName,
        recordBinding = binding,
        doc = doc,
        modifiers = modifiers
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
        cases = cases.asInstanceOf[IndexedSeq[Term[Binding, A, ? <: A]]],
        typeName = typeName,
        variantBinding = binding,
        doc = doc,
        modifiers = modifiers
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
        key = key.asInstanceOf[Reflect[Binding, K]],
        value = value.asInstanceOf[Reflect[Binding, V]],
        typeName = typeName,
        mapBinding = binding,
        doc = doc,
        modifiers = modifiers
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
        wrapped = wrapped.asInstanceOf[Reflect[Binding, B]],
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

  private[this] val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[TypeName[?], Array[FieldInfo]]] {
      override def initialValue: java.util.HashMap[TypeName[?], Array[FieldInfo]] = new java.util.HashMap
    }
  private[this] val discriminatorFields =
    new ThreadLocal[List[(String, String)]] {
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

    override def encodeKey(x: java.time.DayOfWeek, out: JsonWriter): Unit =
      out.writeNonEscapedAsciiKey(x.toString)
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
                      in.decodeError(
                        new DynamicOptic.Node.Case("Some"),
                        new DynamicOptic.Node.Field("value"),
                        error
                      )
                  }
                }

              override def encodeValue(x: Option[Any], out: JsonWriter): Unit =
                if (x eq None) out.writeNull()
                else codec.encodeValue(x.get, out)

              override def nullValue: Option[String] = None
            }
          case _ =>
            val discr = variant.variantBinding.asInstanceOf[Binding.Variant[A]].discriminator
            val cases = variant.cases
            val len   = cases.length
            enumeration(variant) match {
              case Some(constructors) =>
                val infos = new Array[EnumValueInfo](len)
                var idx   = 0
                while (idx < len) {
                  val case_ = cases(idx)
                  val name  = getName(case_.modifiers, caseNameMapper(case_.name))
                  infos(idx) = new EnumValueInfo(name, constructors(idx))
                  idx += 1
                }
                if (len <= 8) { // faster decoding for small enumerations
                  new JsonBinaryCodec[A]() {
                    private[this] val discriminator  = discr
                    private[this] val enumValueInfos = infos

                    def decodeValue(in: JsonReader, default: A): A = {
                      val valueLen = in.readStringAsCharBuf()
                      val len      = enumValueInfos.length
                      var idx      = 0
                      while (idx < len) {
                        val enumValueInfo = enumValueInfos(idx)
                        if (in.isCharBufEqualsTo(valueLen, enumValueInfo.name)) {
                          return enumValueInfo.constructor.construct(null, 0).asInstanceOf[A]
                        }
                        idx += 1
                      }
                      in.enumValueError(valueLen)
                    }

                    def encodeValue(x: A, out: JsonWriter): Unit = {
                      val enumValueInfo = enumValueInfos(discriminator.discriminate(x))
                      val name          = enumValueInfo.name
                      if (enumValueInfo.isNonEscapedAsciiName) out.writeNonEscapedAsciiVal(name)
                      else out.writeVal(name)
                    }
                  }
                } else {
                  val map = new StringToIntMap(len)
                  var idx = 0
                  while (idx < len) {
                    map.put(infos(idx).name, idx)
                    idx += 1
                  }
                  new JsonBinaryCodec[A]() {
                    private[this] val discriminator  = discr
                    private[this] val enumValueInfos = infos
                    private[this] val caseMap        = map

                    def decodeValue(in: JsonReader, default: A): A = {
                      val valueLen = in.readStringAsCharBuf()
                      val idx      = caseMap.get(in, valueLen)
                      if (idx >= 0) enumValueInfos(idx).constructor.construct(null, 0).asInstanceOf[A]
                      else in.enumValueError(valueLen)
                    }

                    def encodeValue(x: A, out: JsonWriter): Unit = {
                      val enumValueInfo = enumValueInfos(discriminator.discriminate(x))
                      val name          = enumValueInfo.name
                      if (enumValueInfo.isNonEscapedAsciiName) out.writeNonEscapedAsciiVal(name)
                      else out.writeVal(name)
                    }
                  }
                }
              case _ =>
                val map   = new StringToIntMap(len)
                val infos = new Array[CaseInfo](len)
                discriminatorKind match {
                  case DiscriminatorKind.Field(fieldName) if hasOnlyRecordAndVariantCases(variant) =>
                    var idx = 0
                    while (idx < len) {
                      val case_ = cases(idx)
                      val name  = getName(case_.modifiers, caseNameMapper(case_.name))
                      map.put(name, idx)
                      discriminatorFields.set((fieldName, name) :: discriminatorFields.get)
                      infos(idx) = new CaseInfo(name, deriveCodec(case_.value))
                      discriminatorFields.set(discriminatorFields.get.tail)
                      idx += 1
                    }
                    new JsonBinaryCodec[A]() {
                      private[this] val discriminator          = discr
                      private[this] val caseInfos              = infos
                      private[this] val caseMap                = map
                      private[this] val discriminatorFieldName = fieldName

                      def decodeValue(in: JsonReader, default: A): A = {
                        in.setMark()
                        if (in.isNextToken('{')) {
                          if (in.skipToKey(discriminatorFieldName)) {
                            val idx = caseMap.get(in, in.readStringAsCharBuf())
                            if (idx >= 0) {
                              in.rollbackToMark()
                              val codec = caseInfos(idx).codec.asInstanceOf[JsonBinaryCodec[A]]
                              try codec.decodeValue(in, codec.nullValue)
                              catch {
                                case error if NonFatal(error) =>
                                  in.decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
                              }
                            } else in.discriminatorValueError(discriminatorFieldName)
                          } else in.requiredFieldError(discriminatorFieldName)
                        } else {
                          in.resetMark()
                          in.readNullOrTokenError(default, '{')
                        }
                      }

                      def encodeValue(x: A, out: JsonWriter): Unit =
                        caseInfos(discriminator.discriminate(x)).codec
                          .asInstanceOf[JsonBinaryCodec[A]]
                          .encodeValue(x, out)
                    }
                  case _ =>
                    var idx = 0
                    while (idx < len) {
                      val case_ = cases(idx)
                      val name  = getName(case_.modifiers, caseNameMapper(case_.name))
                      map.put(name, idx)
                      infos(idx) = new CaseInfo(name, deriveCodec(case_.value))
                      idx += 1
                    }
                    new JsonBinaryCodec[A]() {
                      private[this] val discriminator = discr
                      private[this] val caseMap       = map
                      private[this] val caseInfos     = infos

                      def decodeValue(in: JsonReader, default: A): A =
                        if (in.isNextToken('{')) {
                          if (!in.isNextToken('}')) {
                            in.rollbackToken()
                            val idx = caseMap.get(in, in.readKeyAsCharBuf())
                            if (idx >= 0) {
                              val codec = caseInfos(idx).codec.asInstanceOf[JsonBinaryCodec[A]]
                              val x     =
                                try codec.decodeValue(in, codec.nullValue)
                                catch {
                                  case error if NonFatal(error) =>
                                    in.decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
                                }
                              if (!in.isNextToken('}')) in.objectEndOrCommaError()
                              return x
                            }
                          }
                          in.discriminatorError()
                        } else in.readNullOrTokenError(default, '{')

                      def encodeValue(x: A, out: JsonWriter): Unit = {
                        out.writeObjectStart()
                        val caseInfo = caseInfos(discriminator.discriminate(x))
                        val name     = caseInfo.name
                        if (caseInfo.isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
                        else out.writeKey(name)
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

          override val nullValue: Col[Elem] = constructor.resultObject[Elem](constructor.newObjectBuilder[Elem]())
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

          override val nullValue: Map[Key, Value] =
            constructor.resultObject[Key, Value](constructor.newObjectBuilder[Key, Value]())
        }
      } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isRecord) {
      val record = reflect.asRecord.get
      if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
        val binding     = record.recordBinding.asInstanceOf[Binding.Record[A]]
        val fields      = record.fields
        val isRecursive = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
        val typeName    = record.typeName
        var infos       =
          if (isRecursive) recursiveRecordCache.get.get(typeName)
          else null
        var offset = 0
        val len    = fields.length
        if (infos eq null) {
          infos = new Array[FieldInfo](len)
          if (isRecursive) recursiveRecordCache.get.put(typeName, infos)
          discriminatorFields.set(null :: discriminatorFields.get)
          var idx = 0
          while (idx < len) {
            val field        = fields(idx)
            val fieldReflect = field.value
            val codec        = deriveCodec(fieldReflect)
            val name         = getName(field.modifiers, fieldNameMapper(field.name))
            infos(idx) = new FieldInfo(name, offset, codec, isOptional(fieldReflect))
            offset += codec.valueOffset
            idx += 1
          }
          discriminatorFields.set(discriminatorFields.get.tail)
        }
        if (isTuple(reflect)) {
          new JsonBinaryCodec[A]() {
            private[this] val deconstructor = binding.deconstructor
            private[this] val constructor   = binding.constructor
            private[this] val fieldInfos    = infos
            private[this] val usedRegisters = offset

            override def decodeValue(in: JsonReader, default: A): A =
              if (in.isNextToken('[')) {
                val regs = Registers(usedRegisters)
                if (!in.isNextToken(']')) {
                  in.rollbackToken()
                  val len = fieldInfos.length
                  var idx = 0
                  while (idx < len && (idx == 0 || in.isNextToken(',') || in.commaError())) {
                    val field = fieldInfos(idx)
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
                      case error if NonFatal(error) =>
                        in.decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                    }
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
              val len = fieldInfos.length
              var idx = 0
              while (idx < len) {
                val field  = fieldInfos(idx)
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
                idx += 1
              }
              out.writeArrayEnd()
            }
          }
        } else {
          new JsonBinaryCodec[A]() {
            private[this] val deconstructor                    = binding.deconstructor
            private[this] val constructor                      = binding.constructor
            private[this] val fieldInfos                       = infos
            private[this] val map                              = new StringToIntMap(fieldInfos.length)
            private[this] var optionalFieldOffsets: Array[Int] = null
            private[this] var reqInit                          = 0L
            private[this] val usedRegisters                    = offset
            private[this] val doReject                         = rejectExtraFields
            private[this] val discriminatorField               = discriminatorFields.get.headOption.orNull

            private[this] def init(): Unit = {
              val len = fieldInfos.length
              var req = 0L
              if (len != 0) req = -1L >>> (64 - len)
              var optionalIdx, idx = 0
              while (idx < len) {
                val field = fieldInfos(idx)
                map.put(field.name, idx)
                if (field.isOptional) {
                  req ^= 1L << idx
                  optionalIdx += 1
                }
                idx += 1
              }
              val optionalFieldOffsets = new Array[Int](optionalIdx)
              optionalIdx = 0
              idx = 0
              while (idx < len) {
                val field = fieldInfos(idx)
                if (field.isOptional) {
                  optionalFieldOffsets(optionalIdx) = field.offset
                  optionalIdx += 1
                }
                idx += 1
              }
              this.optionalFieldOffsets = optionalFieldOffsets
              this.reqInit = req
            }

            override def decodeValue(in: JsonReader, default: A): A =
              if (in.isNextToken('{')) {
                val regs = Registers(usedRegisters)
                if (!in.isNextToken('}')) {
                  in.rollbackToken()
                  if (optionalFieldOffsets eq null) init()
                  var len = optionalFieldOffsets.length
                  var idx = 0
                  while (idx < len) {
                    regs.setObject(optionalFieldOffsets(idx), 0, None)
                    idx += 1
                  }
                  len = fieldInfos.length
                  var req             = reqInit
                  var currIdx, keyLen = -1
                  while (keyLen < 0 || in.isNextToken(',')) {
                    keyLen = in.readKeyAsCharBuf()
                    var field: FieldInfo = null
                    if (
                      len > 0 && {
                        currIdx += 1
                        if (currIdx == len) currIdx = 0
                        field = fieldInfos(currIdx)
                        if (!in.isCharBufEqualsTo(keyLen, field.name)) {
                          idx = map.get(in, keyLen)
                          if (idx >= 0) {
                            field = fieldInfos(idx)
                            currIdx = idx
                          } else field = null
                        }
                        field ne null
                      }
                    ) {
                      if (!field.isOptional) {
                        val mask = 1L << currIdx
                        if ((req & mask) == 0) in.duplicatedKeyError(keyLen)
                        req ^= mask
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
                        case error if NonFatal(error) =>
                          in.decodeError(new DynamicOptic.Node.Field(fields(currIdx).name), error)
                      }
                    } else skipOrReject(in, keyLen)
                  }
                  if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
                  if (req != 0) missingRequiredFieldsError(in, req)
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
                val field  = fieldInfos(idx)
                val name   = field.name
                val offset = field.offset
                val codec  = field.codec
                if (field.isOptional) {
                  val value = regs.getObject(offset, 0)
                  if (value ne None) {
                    if (field.isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
                    else out.writeKey(name)
                    codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(value, out)
                  }
                } else {
                  if (field.isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
                  else out.writeKey(name)
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
                idx += 1
              }
              out.writeObjectEnd()
            }

            private[this] def missingRequiredFieldsError(in: JsonReader, req: Long): Nothing =
              in.requiredFieldError(fieldInfos(java.lang.Long.numberOfTrailingZeros(req)).name)

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

  private[this] def enumeration[F[_, _], A](variant: Reflect.Variant[F, A]): Option[Array[Constructor[?]]] =
    if (variant.cases.forall(case_ => case_.value.asRecord.exists(_.fields.isEmpty) || case_.value.isVariant)) {
      new Some(variant.cases.map { case_ =>
        if (case_.value.isRecord) {
          val recordBinding = case_.value.asRecord.get.recordBinding
          val binding       = recordBinding match {
            case value: Binding.Record[?] => value
            case _                        => recordBinding.asInstanceOf[BindingInstance[TC, ?, ?]].binding.asInstanceOf[Binding.Record[?]]
          }
          binding.constructor
        } else null
      }.toArray)
    } else None

  private[this] def hasOnlyRecordAndVariantCases[F[_, _], A](variant: Reflect.Variant[F, A]): Boolean =
    variant.cases.forall(case_ => case_.value.isRecord || case_.value.isVariant)

  private[this] def option[F[_, _], A](variant: Reflect.Variant[F, A]): Option[Reflect[F, ?]] = {
    val typeName = variant.typeName
    val cases    = variant.cases
    if (
      typeName.namespace == Namespace.scala && typeName.name == "Option" &&
      cases.length == 2 && cases(1).name == "Some"
    ) cases(1).value.asRecord.map(_.fields(0).value)
    else None
  }

  private[this] def isOptional[F[_, _], A](reflect: Reflect[F, A]): Boolean = reflect.isVariant && {
    val variant  = reflect.asVariant.get
    val typeName = reflect.typeName
    val cases    = variant.cases
    typeName.namespace == Namespace.scala && typeName.name == "Option" &&
    cases.length == 2 && cases(1).name == "Some"
  }

  private[this] def isTuple[F[_, _], A](reflect: Reflect[F, A]): Boolean = reflect.isRecord && {
    val typeName = reflect.typeName
    typeName.namespace == Namespace.scala && typeName.name.startsWith("Tuple")
  }

  private[this] def getName(modifiers: Seq[Modifier.Term], name: String): String = modifiers.collectFirst {
    case m: Modifier.config if m.key == Modifiers.renameKey => m.value
  }.getOrElse(name)

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
            builder.addOne(
              (
                in.readKeyAsString(),
                decodeValue(in, default)
              )
            )
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
          case _: PrimitiveValue.Unit.type      => out.encodeError("encoding as a JSON key is not supported")
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
      case _ => out.encodeError("encoding as a JSON key is not supported")
    }
  }
}

abstract class Info(name: String) {
  val isNonEscapedAsciiName: Boolean = {
    val len = name.length
    var idx = 0
    while (idx < len && JsonWriter.isNonEscapedAscii(name.charAt(idx))) idx += 1
    idx == len
  }
}

private case class FieldInfo(name: String, offset: RegisterOffset, codec: JsonBinaryCodec[?], isOptional: Boolean)
    extends Info(name) {
  val valueType: Int = codec.valueType
}

private case class CaseInfo(name: String, codec: JsonBinaryCodec[?]) extends Info(name)

private case class EnumValueInfo(name: String, constructor: Constructor[?]) extends Info(name)

private class StringToIntMap(size: Int) {
  private[this] val mask   = (Integer.highestOneBit(size | 1) << 2) - 1
  private[this] val keys   = new Array[String](mask + 1)
  private[this] val values = new Array[Int](mask + 1)

  def put(key: String, value: Int): Unit = {
    val len       = key.length
    var hash, idx = 0
    while (idx < len) {
      hash = (hash << 5) + (key.charAt(idx) - hash)
      idx += 1
    }
    idx = hash & mask
    while ({
      val currKey = keys(idx)
      (currKey ne null) && !currKey.equals(key)
    }) idx = (idx + 1) & mask
    keys(idx) = key
    values(idx) = value
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
}
