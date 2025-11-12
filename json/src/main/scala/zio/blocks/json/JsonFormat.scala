package zio.blocks.json

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers}
import zio.blocks.schema._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import scala.util.control.NonFatal

object JsonFormat
    extends BinaryFormat(
      "application/json",
      new Deriver[JsonBinaryCodec] {
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
          def decodeValue(in: JsonReader, default: java.time.DayOfWeek): java.time.DayOfWeek =
            java.time.DayOfWeek.of(in.readInt())

          def encodeValue(x: java.time.DayOfWeek, out: JsonWriter): Unit = out.writeVal(x.getValue)

          override def decodeKey(in: JsonReader): java.time.DayOfWeek =
            java.time.DayOfWeek.of(in.readKeyAsInt())

          override def encodeKey(x: java.time.DayOfWeek, out: JsonWriter): Unit = out.writeKey(x.getValue)
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
          def decodeValue(in: JsonReader, default: java.time.Month): java.time.Month =
            java.time.Month.of(in.readInt())

          def encodeValue(x: java.time.Month, out: JsonWriter): Unit = out.writeVal(x.getValue)

          override def decodeKey(in: JsonReader): java.time.Month =
            java.time.Month.of(in.readKeyAsInt())

          override def encodeKey(x: java.time.Month, out: JsonWriter): Unit = out.writeKey(x.getValue)
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
          def decodeValue(in: JsonReader, default: java.time.OffsetTime): java.time.OffsetTime =
            in.readOffsetTime(default)

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
          def decodeValue(in: JsonReader, default: java.time.ZoneOffset): java.time.ZoneOffset =
            in.readZoneOffset(default)

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
          def decodeValue(in: JsonReader, default: java.util.Currency): java.util.Currency =
            java.util.Currency.getInstance(in.readString(if (default eq null) null else default.toString))

          def encodeValue(x: java.util.Currency, out: JsonWriter): Unit = out.writeVal(x.toString)

          override def decodeKey(in: JsonReader): java.util.Currency =
            java.util.Currency.getInstance(in.readKeyAsString())

          override def encodeKey(x: java.util.Currency, out: JsonWriter): Unit = out.writeKey(x.toString)
        }
        private[this] val uuidCodec = new JsonBinaryCodec[java.util.UUID]() {
          def decodeValue(in: JsonReader, default: java.util.UUID): java.util.UUID = in.readUUID(default)

          def encodeValue(x: java.util.UUID, out: JsonWriter): Unit = out.writeVal(x.toString)

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
              val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
              val cases   = variant.cases
              val len     = cases.length
              val codecs  = new Array[JsonBinaryCodec[?]](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                idx += 1
              }
              new JsonBinaryCodec[A]() {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs
                private[this] val variantCases  = cases

                def decodeValue(in: JsonReader, default: A): A =
                  if (in.isNextToken('{')) {
                    if (!in.isNextToken('}')) {
                      in.rollbackToken()
                      val l   = in.readKeyAsCharBuf()
                      val len = variantCases.length
                      var idx = 0
                      while (idx < len) {
                        val name = variantCases(idx).name
                        if (in.isCharBufEqualsTo(l, name)) {
                          val codec = caseCodecs(idx).asInstanceOf[JsonBinaryCodec[A]]
                          val x     =
                            try codec.decodeValue(in, codec.nullValue)
                            catch {
                              case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(name), error)
                            }
                          if (!in.isNextToken('}')) in.objectEndOrCommaError()
                          return x
                        }
                        idx += 1
                      }
                    }
                    in.decodeError("unexpected discriminator key")
                  } else in.readNullOrTokenError(default, '{')

                def encodeValue(x: A, out: JsonWriter): Unit = {
                  out.writeObjectStart()
                  val idx = discriminator.discriminate(x)
                  out.writeKey(variantCases(idx).name)
                  caseCodecs(idx).asInstanceOf[JsonBinaryCodec[A]].encodeValue(x, out)
                  out.writeObjectEnd()
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
                        case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
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
                            case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                          }
                        val v =
                          try valueCodec.decodeValue(in, valueCodec.nullValue)
                          catch {
                            case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtMapKey(k), error)
                          }
                        constructor.addObject(builder, k, v)
                        in.isNextToken(',')
                      }) ()
                      if (in.isCurrentToken('}')) constructor.resultObject[Key, Value](builder)
                      else in.arrayEndOrCommaError()
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
              var fieldInfos  =
                if (isRecursive) recursiveRecordCache.get.get(typeName)
                else null
              var offset = 0
              if (fieldInfos eq null) {
                val len = fields.length
                fieldInfos = new Array[FieldInfo](len)
                if (isRecursive) recursiveRecordCache.get.put(typeName, fieldInfos)
                var idx = 0
                while (idx < len) {
                  val field = fields(idx)
                  val codec = deriveCodec(field.value)
                  fieldInfos(idx) = new FieldInfo(field.name, offset, codec)
                  offset += codec.valueOffset
                  idx += 1
                }
              }
              new JsonBinaryCodec[A]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val fields        = fieldInfos
                private[this] val usedRegisters = offset

                override def decodeValue(in: JsonReader, default: A): A =
                  if (in.isNextToken('{')) {
                    val regs = Registers(usedRegisters)
                    if (!in.isNextToken('}')) {
                      in.rollbackToken()
                      val len = fields.length
                      var req = 0L
                      if (len != 0) req = -1L >>> (64 - len)
                      var currIdx, keyLen = -1
                      while (keyLen < 0 || in.isNextToken(',')) {
                        keyLen = in.readKeyAsCharBuf()
                        var field: FieldInfo = null
                        var idx              = 0
                        while (
                          idx < len && {
                            currIdx += 1
                            if (currIdx == len) currIdx = 0
                            field = fields(currIdx)
                            !in.isCharBufEqualsTo(keyLen, field.name)
                          }
                        ) idx += 1
                        if (idx < len) {
                          val mask = 1L << currIdx
                          if ((req & mask) == 0) in.duplicatedKeyError(keyLen)
                          req ^= mask
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
                              decodeError(new DynamicOptic.Node.Field(field.name), error)
                          }
                        } else in.skip()
                      }
                      if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
                      if (req != 0) missingRequiredFieldsError(in, req)
                    }
                    constructor.construct(regs, 0)
                  } else in.readNullOrTokenError(default, '{')

                override def encodeValue(x: A, out: JsonWriter): Unit = {
                  out.writeObjectStart()
                  val regs = Registers(usedRegisters)
                  deconstructor.deconstruct(regs, 0, x)
                  val len = fields.length
                  var idx = 0
                  while (idx < len) {
                    val field  = fields(idx)
                    val name   = field.name
                    val codec  = field.codec
                    val offset = field.offset
                    if (field.isNonEscapedAsciiName) out.writeNonEscapedAsciiKey(name)
                    else out.writeKey(name)
                    field.valueType match {
                      case JsonBinaryCodec.objectType =>
                        codec.asInstanceOf[JsonBinaryCodec[AnyRef]].encodeValue(regs.getObject(offset, 0), out)
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
                  out.writeObjectEnd()
                }

                private[this] def missingRequiredFieldsError(in: JsonReader, req: Long): Nothing = {
                  val name = fields(java.lang.Long.numberOfTrailingZeros(req)).name
                  in.decodeError(s"missing required field \"$name\"")
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

                override def decodeValue(in: JsonReader, default: A): A = {
                  val wrapped =
                    try {
                      wrappedCodec.decodeValue(
                        in, {
                          if (default == null) null
                          else unwrap(default)
                        }.asInstanceOf[Wrapped]
                      )
                    } catch {
                      case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                    }
                  wrap(wrapped) match {
                    case Right(x)  => x
                    case Left(err) => decodeError(err)
                  }
                }

                override def encodeValue(x: A, out: JsonWriter): Unit = wrappedCodec.encodeValue(unwrap(x), out)

                override def decodeKey(in: JsonReader): A = {
                  val wrapped =
                    try wrappedCodec.decodeKey(in)
                    catch {
                      case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                    }
                  wrap(wrapped) match {
                    case Right(x)  => x
                    case Left(err) => decodeError(err)
                  }
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

        private[this] val dynamicValueCodec = new JsonBinaryCodec[DynamicValue]() {
          def decodeValue(in: JsonReader, default: DynamicValue): DynamicValue = ???

          def encodeValue(x: DynamicValue, out: JsonWriter): Unit = ???
        }
      }
    )

private case class FieldInfo(name: String, offset: RegisterOffset, codec: JsonBinaryCodec[?]) {
  val valueType: Int                 = codec.valueType
  val isNonEscapedAsciiName: Boolean = {
    val s   = name
    val len = s.length
    var idx = 0
    while (idx < len && JsonWriter.isNonEscapedAscii(s.charAt(idx))) idx += 1
    idx == len
  }
}
