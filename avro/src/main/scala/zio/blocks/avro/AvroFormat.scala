package zio.blocks.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import scala.util.control.NonFatal

object AvroFormat
    extends BinaryFormat(
      "application/avro",
      new Deriver[AvroBinaryCodec] {
        override def derivePrimitive[F[_, _], A](
          primitiveType: PrimitiveType[A],
          typeName: TypeName[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        ): Lazy[AvroBinaryCodec[A]] =
          Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[C[A]]] = Lazy {
          deriveCodec(
            new Reflect.Sequence(
              element = element.asInstanceOf[Reflect[Binding, A]],
              typeName = typeName,
              seqBinding = binding,
              doc = doc,
              modifiers = modifiers
            )
          )
        }

        override def deriveMap[F[_, _], M[_, _], K, V](
          key: Reflect[F, K],
          value: Reflect[F, V],
          typeName: TypeName[M[K, V]],
          binding: Binding[BindingType.Map[M], M[K, V]],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[M[K, V]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[DynamicValue]] =
          Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeName: TypeName[A],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
          deriveCodec(
            new Reflect.Wrapper(
              wrapped = wrapped.asInstanceOf[Reflect[Binding, B]],
              typeName = typeName,
              wrapperBinding = binding,
              doc = doc,
              modifiers = modifiers
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
          new ThreadLocal[java.util.HashMap[TypeName[?], (Array[AvroBinaryCodec[?]], Array[DynamicOptic.Node.Field])]] {
            override def initialValue
              : java.util.HashMap[TypeName[?], (Array[AvroBinaryCodec[?]], Array[DynamicOptic.Node.Field])] =
              new java.util.HashMap
          }
        private[this] val unitCodec = new AvroBinaryCodec[Unit](AvroBinaryCodec.unitType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Unit = ()

          def encode(x: Unit, e: BinaryEncoder): Unit = ()
        }
        private[this] val byteCodec = new AvroBinaryCodec[Byte](AvroBinaryCodec.byteType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Byte = {
            val x =
              try {
                d.readInt()
              } catch {
                case error if NonFatal(error) => decodeError(t, error)
              }
            if (x >= Byte.MinValue && x <= Byte.MaxValue) x.toByte
            else decodeError(t, "Expected Byte")
          }

          def encode(x: Byte, e: BinaryEncoder): Unit = e.writeInt(x)
        }
        private[this] val booleanCodec = new AvroBinaryCodec[Boolean](AvroBinaryCodec.booleanType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Boolean = try {
            d.readBoolean()
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: Boolean, e: BinaryEncoder): Unit = e.writeBoolean(x)
        }
        private[this] val shortCodec = new AvroBinaryCodec[Short](AvroBinaryCodec.shortType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Short = {
            val x =
              try {
                d.readInt()
              } catch {
                case error if NonFatal(error) => decodeError(t, error)
              }
            if (x >= Short.MinValue && x <= Short.MaxValue) x.toShort
            else decodeError(t, "Expected Short")
          }

          def encode(x: Short, e: BinaryEncoder): Unit = e.writeInt(x)
        }
        private[this] val charCodec = new AvroBinaryCodec[Char](AvroBinaryCodec.charType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Char = {
            val x =
              try {
                d.readInt()
              } catch {
                case error if NonFatal(error) => decodeError(t, error)
              }
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError(t, "Expected Char")
          }

          def encode(x: Char, e: BinaryEncoder): Unit = e.writeInt(x)
        }
        private[this] val intCodec = new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Int = try {
            d.readInt()
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: Int, e: BinaryEncoder): Unit = e.writeInt(x)
        }
        private[this] val floatCodec = new AvroBinaryCodec[Float](AvroBinaryCodec.floatType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Float = try {
            d.readFloat()
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: Float, e: BinaryEncoder): Unit = e.writeFloat(x)
        }
        private[this] val longCodec = new AvroBinaryCodec[Long](AvroBinaryCodec.longType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Long = try {
            d.readLong()
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: Long, e: BinaryEncoder): Unit = e.writeLong(x)
        }
        private[this] val doubleCodec = new AvroBinaryCodec[Double](AvroBinaryCodec.doubleType) {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Double = try {
            d.readDouble()
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: Double, e: BinaryEncoder): Unit = e.writeDouble(x)
        }
        private[this] val stringCodec = new AvroBinaryCodec[String]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): String = try {
            d.readString()
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: String, e: BinaryEncoder): Unit = e.writeString(x)
        }
        private[this] val bigIntCodec = new AvroBinaryCodec[BigInt]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): BigInt = try {
            BigInt(d.readBytes(null).array())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: BigInt, e: BinaryEncoder): Unit = e.writeBytes(x.toByteArray)
        }
        private[this] val bigDecimalCodec = new AvroBinaryCodec[BigDecimal]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): BigDecimal = try {
            val mantissa     = d.readBytes(null).array()
            val scale        = d.readInt()
            val precision    = d.readInt()
            val roundingMode = java.math.RoundingMode.valueOf(d.readInt())
            val mc           = new MathContext(precision, roundingMode)
            new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: BigDecimal, e: BinaryEncoder): Unit = {
            val bd = x.underlying
            val mc = x.mc
            e.writeBytes(ByteBuffer.wrap(bd.unscaledValue.toByteArray))
            e.writeInt(bd.scale)
            e.writeInt(mc.getPrecision)
            e.writeInt(mc.getRoundingMode.ordinal)
          }
        }
        private[this] val dayOfWeekCodec = new AvroBinaryCodec[java.time.DayOfWeek]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.DayOfWeek = try {
            java.time.DayOfWeek.of(d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.DayOfWeek, e: BinaryEncoder): Unit = e.writeInt(x.getValue)
        }
        private[this] val durationCodec = new AvroBinaryCodec[java.time.Duration]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.Duration = try {
            java.time.Duration.ofSeconds(d.readLong(), d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.Duration, e: BinaryEncoder): Unit = {
            e.writeLong(x.getSeconds)
            e.writeInt(x.getNano)
          }
        }
        private[this] val instantCodec = new AvroBinaryCodec[java.time.Instant]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.Instant = try {
            java.time.Instant.ofEpochSecond(d.readLong(), d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.Instant, e: BinaryEncoder): Unit = {
            e.writeLong(x.getEpochSecond)
            e.writeInt(x.getNano)
          }
        }
        private[this] val localDateCodec = new AvroBinaryCodec[java.time.LocalDate]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.LocalDate = try {
            java.time.LocalDate.of(d.readInt(), d.readInt(), d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.LocalDate, e: BinaryEncoder): Unit = {
            e.writeInt(x.getYear)
            e.writeInt(x.getMonthValue)
            e.writeInt(x.getDayOfMonth)
          }
        }
        private[this] val localDateTimeCodec = new AvroBinaryCodec[java.time.LocalDateTime]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.LocalDateTime = try {
            java.time.LocalDateTime
              .of(d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.LocalDateTime, e: BinaryEncoder): Unit = {
            e.writeInt(x.getYear)
            e.writeInt(x.getMonthValue)
            e.writeInt(x.getDayOfMonth)
            e.writeInt(x.getHour)
            e.writeInt(x.getMinute)
            e.writeInt(x.getSecond)
            e.writeInt(x.getNano)
          }
        }
        private[this] val localTimeCodec = new AvroBinaryCodec[java.time.LocalTime]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.LocalTime = try {
            java.time.LocalTime.of(d.readInt(), d.readInt(), d.readInt(), d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.LocalTime, e: BinaryEncoder): Unit = {
            e.writeInt(x.getHour)
            e.writeInt(x.getMinute)
            e.writeInt(x.getSecond)
            e.writeInt(x.getNano)
          }
        }
        private[this] val monthCodec = new AvroBinaryCodec[java.time.Month]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.Month = try {
            java.time.Month.of(d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.Month, e: BinaryEncoder): Unit = e.writeInt(x.getValue)
        }
        private[this] val monthDayCodec = new AvroBinaryCodec[java.time.MonthDay]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.MonthDay = try {
            java.time.MonthDay.of(d.readInt(), d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.MonthDay, e: BinaryEncoder): Unit = {
            e.writeInt(x.getMonthValue)
            e.writeInt(x.getDayOfMonth)
          }
        }
        private[this] val offsetDateTimeCodec = new AvroBinaryCodec[java.time.OffsetDateTime]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.OffsetDateTime = try {
            java.time.OffsetDateTime.of(
              d.readInt(),
              d.readInt(),
              d.readInt(),
              d.readInt(),
              d.readInt(),
              d.readInt(),
              d.readInt(),
              java.time.ZoneOffset.ofTotalSeconds(d.readInt())
            )
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.OffsetDateTime, e: BinaryEncoder): Unit = {
            e.writeInt(x.getYear)
            e.writeInt(x.getMonthValue)
            e.writeInt(x.getDayOfMonth)
            e.writeInt(x.getHour)
            e.writeInt(x.getMinute)
            e.writeInt(x.getSecond)
            e.writeInt(x.getNano)
            e.writeInt(x.getOffset.getTotalSeconds)
          }
        }
        private[this] val offsetTimeCodec = new AvroBinaryCodec[java.time.OffsetTime]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.OffsetTime = try {
            java.time.OffsetTime.of(
              d.readInt(),
              d.readInt(),
              d.readInt(),
              d.readInt(),
              java.time.ZoneOffset.ofTotalSeconds(d.readInt())
            )
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.OffsetTime, e: BinaryEncoder): Unit = {
            e.writeInt(x.getHour)
            e.writeInt(x.getMinute)
            e.writeInt(x.getSecond)
            e.writeInt(x.getNano)
            e.writeInt(x.getOffset.getTotalSeconds)
          }
        }
        private[this] val periodCodec = new AvroBinaryCodec[java.time.Period]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.Period = try {
            java.time.Period.of(d.readInt(), d.readInt(), d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.Period, e: BinaryEncoder): Unit = {
            e.writeInt(x.getYears)
            e.writeInt(x.getMonths)
            e.writeInt(x.getDays)
          }
        }
        private[this] val yearCodec = new AvroBinaryCodec[java.time.Year]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.Year = try {
            java.time.Year.of(d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.Year, e: BinaryEncoder): Unit = e.writeInt(x.getValue)
        }
        private[this] val yearMonthCodec = new AvroBinaryCodec[java.time.YearMonth]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.YearMonth = try {
            java.time.YearMonth.of(d.readInt(), d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.YearMonth, e: BinaryEncoder): Unit = {
            e.writeInt(x.getYear)
            e.writeInt(x.getMonthValue)
          }
        }
        private[this] val zoneIdCodec = new AvroBinaryCodec[java.time.ZoneId]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.ZoneId = try {
            java.time.ZoneId.of(d.readString())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.ZoneId, e: BinaryEncoder): Unit = e.writeString(x.toString)
        }
        private[this] val zoneOffsetCodec = new AvroBinaryCodec[java.time.ZoneOffset]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.ZoneOffset = try {
            java.time.ZoneOffset.ofTotalSeconds(d.readInt())
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.ZoneOffset, e: BinaryEncoder): Unit = e.writeInt(x.getTotalSeconds)
        }
        private[this] val zonedDateTimeCodec = new AvroBinaryCodec[java.time.ZonedDateTime]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.time.ZonedDateTime = try {
            java.time.ZonedDateTime.ofInstant(
              java.time.LocalDateTime.of(
                d.readInt(),
                d.readInt(),
                d.readInt(),
                d.readInt(),
                d.readInt(),
                d.readInt(),
                d.readInt()
              ),
              java.time.ZoneOffset.ofTotalSeconds(d.readInt()),
              java.time.ZoneId.of(d.readString())
            )
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.time.ZonedDateTime, e: BinaryEncoder): Unit = {
            e.writeInt(x.getYear)
            e.writeInt(x.getMonthValue)
            e.writeInt(x.getDayOfMonth)
            e.writeInt(x.getHour)
            e.writeInt(x.getMinute)
            e.writeInt(x.getSecond)
            e.writeInt(x.getNano)
            e.writeInt(x.getOffset.getTotalSeconds)
            e.writeString(x.getZone.toString)
          }
        }
        private[this] val currencyCodec = new AvroBinaryCodec[java.util.Currency]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.util.Currency = try {
            val bs = new Array[Byte](3)
            d.readFixed(bs, 0, 3)
            java.util.Currency.getInstance(new String(bs))
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.util.Currency, e: BinaryEncoder): Unit = {
            val s = x.toString
            e.writeFixed(Array(s.charAt(0).toByte, s.charAt(1).toByte, s.charAt(2).toByte))
          }
        }

        private[this] val uuidCodec = new AvroBinaryCodec[java.util.UUID]() {
          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): java.util.UUID = try {
            val bs = new Array[Byte](16)
            d.readFixed(bs)
            val hi =
              (bs(0) & 0xff).toLong << 56 |
                (bs(1) & 0xff).toLong << 48 |
                (bs(2) & 0xff).toLong << 40 |
                (bs(3) & 0xff).toLong << 32 |
                (bs(4) & 0xff).toLong << 24 |
                (bs(5) & 0xff) << 16 |
                (bs(6) & 0xff) << 8 |
                (bs(7) & 0xff)
            val lo =
              (bs(8) & 0xff).toLong << 56 |
                (bs(9) & 0xff).toLong << 48 |
                (bs(10) & 0xff).toLong << 40 |
                (bs(11) & 0xff).toLong << 32 |
                (bs(12) & 0xff).toLong << 24 |
                (bs(13) & 0xff) << 16 |
                (bs(14) & 0xff) << 8 |
                (bs(15) & 0xff)
            new java.util.UUID(hi, lo)
          } catch {
            case error if NonFatal(error) => decodeError(t, error)
          }

          def encode(x: java.util.UUID, e: BinaryEncoder): Unit = {
            val hi = x.getMostSignificantBits
            val lo = x.getLeastSignificantBits
            val bs = Array(
              (hi >> 56).toByte,
              (hi >> 48).toByte,
              (hi >> 40).toByte,
              (hi >> 32).toByte,
              (hi >> 24).toByte,
              (hi >> 16).toByte,
              (hi >> 8).toByte,
              hi.toByte,
              (lo >> 56).toByte,
              (lo >> 48).toByte,
              (lo >> 40).toByte,
              (lo >> 32).toByte,
              (lo >> 24).toByte,
              (lo >> 16).toByte,
              (lo >> 8).toByte,
              lo.toByte
            )
            e.writeFixed(bs)
          }
        }

        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): AvroBinaryCodec[A] = {
          if (reflect.isPrimitive) {
            val primitive = reflect.asPrimitive.get
            if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
              primitive.primitiveType match {
                case _: PrimitiveType.Unit.type      => unitCodec
                case _: PrimitiveType.Byte           => byteCodec
                case _: PrimitiveType.Boolean        => booleanCodec
                case _: PrimitiveType.Short          => shortCodec
                case _: PrimitiveType.Char           => charCodec
                case _: PrimitiveType.Int            => intCodec
                case _: PrimitiveType.Float          => floatCodec
                case _: PrimitiveType.Long           => longCodec
                case _: PrimitiveType.Double         => doubleCodec
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
              val variantBinding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
              new AvroBinaryCodec[A]() {
                private[this] val (caseCodecs, caseSpans) = {
                  val cases  = variant.cases
                  val codecs = new Array[AvroBinaryCodec[?]](cases.length)
                  val spans  = new Array[DynamicOptic.Node.Case](cases.length)
                  val len    = cases.length
                  var idx    = 0
                  while (idx < len) {
                    codecs(idx) = deriveCodec(cases(idx).value)
                    spans(idx) = new DynamicOptic.Node.Case(cases(idx).name)
                    idx += 1
                  }
                  (codecs, spans)
                }
                private[this] val discriminator = variantBinding.discriminator

                def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): A = {
                  val idx =
                    try {
                      d.readInt()
                    } catch {
                      case error if NonFatal(error) => decodeError(t, error)
                    }
                  if (idx >= 0 && idx < caseCodecs.length) {
                    caseCodecs(idx).asInstanceOf[AvroBinaryCodec[A]].decode(caseSpans(idx) :: t, d)
                  } else decodeError(t, s"Expected enum index from 0 to ${caseCodecs.length - 1}, got $idx")
                }

                def encode(x: A, e: BinaryEncoder): Unit = {
                  val idx = discriminator.discriminate(x)
                  e.writeInt(idx)
                  caseCodecs(idx).asInstanceOf[AvroBinaryCodec[A]].encode(x, e)
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val seqBinding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              new AvroBinaryCodec[Col[Elem]]() {
                private[this] val elementCodec  = deriveCodec(sequence.element).asInstanceOf[AvroBinaryCodec[Elem]]
                private[this] val deconstructor = seqBinding.deconstructor
                private[this] val constructor   = seqBinding.constructor

                def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Col[Elem] = {
                  val builder = constructor.newObjectBuilder[Elem](8)
                  var size    = 0
                  var count   = 0L
                  while ({
                    size =
                      try {
                        d.readInt()
                      } catch {
                        case error if NonFatal(error) => decodeError(t, error)
                      }
                    size > 0
                  }) {
                    if (count + size > AvroBinaryCodec.maxCollectionSize) {
                      decodeError(
                        t,
                        s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                      )
                    }
                    while (size > 0) {
                      constructor
                        .addObject(builder, elementCodec.decode(new DynamicOptic.Node.AtIndex(count.toInt) :: t, d))
                      count += 1
                      size -= 1
                    }
                  }
                  if (size < 0) decodeError(t, s"Expected positive collection part size, got $size")
                  constructor.resultObject[Elem](builder)
                }

                def encode(x: Col[Elem], e: BinaryEncoder): Unit = {
                  val size = deconstructor.size(x)
                  if (size > 0) {
                    e.writeInt(size)
                    val it = deconstructor.deconstruct(x)
                    while (it.hasNext) elementCodec.encode(it.next(), e)
                  }
                  e.writeInt(0)
                }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val mapBinding = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              new AvroBinaryCodec[Map[Key, Value]]() {
                private[this] val keyCodec      = deriveCodec(map.key).asInstanceOf[AvroBinaryCodec[Key]]
                private[this] val valueCodec    = deriveCodec(map.value).asInstanceOf[AvroBinaryCodec[Value]]
                private[this] val deconstructor = mapBinding.deconstructor
                private[this] val constructor   = mapBinding.constructor

                def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): Map[Key, Value] = {
                  val builder = constructor.newObjectBuilder[Key, Value](8)
                  var size    = 0
                  var count   = 0L
                  while ({
                    size =
                      try {
                        d.readInt()
                      } catch {
                        case error if NonFatal(error) => decodeError(t, error)
                      }
                    size > 0
                  }) {
                    if (count + size > AvroBinaryCodec.maxCollectionSize) {
                      decodeError(
                        t,
                        s"Expected map size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                      )
                    }
                    while (size > 0) {
                      val k = keyCodec.decode(new DynamicOptic.Node.AtIndex(count.toInt) :: t, d)
                      val v = valueCodec.decode(new DynamicOptic.Node.AtMapKey(k) :: t, d)
                      constructor.addObject(builder, k, v)
                      count += 1
                      size -= 1
                    }
                  }
                  if (size < 0) decodeError(t, s"Expected positive map part size, got $size")
                  constructor.resultObject[Key, Value](builder)
                }

                def encode(x: Map[Key, Value], e: BinaryEncoder): Unit = {
                  val size = deconstructor.size(x)
                  if (size > 0) {
                    e.writeInt(size)
                    val it = deconstructor.deconstruct(x)
                    while (it.hasNext) {
                      val kv = it.next()
                      keyCodec.encode(deconstructor.getKey(kv), e)
                      valueCodec.encode(deconstructor.getValue(kv), e)
                    }
                  }
                  e.writeInt(0)
                }
              }
            } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val recordBinding = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields        = record.fields
              val isRecursive   = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
              new AvroBinaryCodec[A]() {
                private[this] val (fieldCodecs, fieldSpans) = {
                  if (isRecursive) recursiveRecordCache.get.get(record.typeName)
                  else null
                } match {
                  case null =>
                    val len             = fields.length
                    val codecs          = new Array[AvroBinaryCodec[?]](len)
                    val spans           = new Array[DynamicOptic.Node.Field](len)
                    val codecsWithSpans = (codecs, spans)
                    if (isRecursive) recursiveRecordCache.get.put(record.typeName, codecsWithSpans)
                    var idx = 0
                    while (idx < len) {
                      codecs(idx) = deriveCodec(fields(idx).value)
                      spans(idx) = new DynamicOptic.Node.Field(fields(idx).name)
                      idx += 1
                    }
                    codecsWithSpans
                  case codecsWithSpans => codecsWithSpans
                }
                private[this] val deconstructor = recordBinding.deconstructor
                private[this] val constructor   = recordBinding.constructor

                def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): A = {
                  val registers = Registers(record.usedRegisters)
                  var offset    = RegisterOffset.Zero
                  val len       = fieldCodecs.length
                  var idx       = 0
                  while (idx < len) {
                    val codec = fieldCodecs(idx)
                    val t_    = fieldSpans(idx) :: t
                    codec.valueType match {
                      case AvroBinaryCodec.objectType =>
                        registers.setObject(offset, 0, codec.asInstanceOf[AvroBinaryCodec[AnyRef]].decode(t_, d))
                      case AvroBinaryCodec.intType =>
                        registers.setInt(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Int]].decode(t_, d))
                      case AvroBinaryCodec.longType =>
                        registers.setLong(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Long]].decode(t_, d))
                      case AvroBinaryCodec.floatType =>
                        registers.setFloat(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Float]].decode(t_, d))
                      case AvroBinaryCodec.doubleType =>
                        registers.setDouble(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Double]].decode(t_, d))
                      case AvroBinaryCodec.booleanType =>
                        registers.setBoolean(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Boolean]].decode(t_, d))
                      case AvroBinaryCodec.byteType =>
                        registers.setByte(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Byte]].decode(t_, d))
                      case AvroBinaryCodec.charType =>
                        registers.setChar(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Char]].decode(t_, d))
                      case AvroBinaryCodec.shortType =>
                        registers.setShort(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Short]].decode(t_, d))
                      case _ => codec.asInstanceOf[AvroBinaryCodec[Unit]].decode(t_, d)
                    }
                    offset = RegisterOffset.add(offset, codec.valueOffset)
                    idx += 1
                  }
                  constructor.construct(registers, RegisterOffset.Zero)
                }

                def encode(x: A, e: BinaryEncoder): Unit = {
                  val registers = Registers(record.usedRegisters)
                  var offset    = RegisterOffset.Zero
                  deconstructor.deconstruct(registers, offset, x)
                  val len = fieldCodecs.length
                  var idx = 0
                  while (idx < len) {
                    val codec = fieldCodecs(idx)
                    codec.valueType match {
                      case AvroBinaryCodec.objectType =>
                        codec.asInstanceOf[AvroBinaryCodec[AnyRef]].encode(registers.getObject(offset, 0), e)
                      case AvroBinaryCodec.intType =>
                        codec.asInstanceOf[AvroBinaryCodec[Int]].encode(registers.getInt(offset, 0), e)
                      case AvroBinaryCodec.longType =>
                        codec.asInstanceOf[AvroBinaryCodec[Long]].encode(registers.getLong(offset, 0), e)
                      case AvroBinaryCodec.floatType =>
                        codec.asInstanceOf[AvroBinaryCodec[Float]].encode(registers.getFloat(offset, 0), e)
                      case AvroBinaryCodec.doubleType =>
                        codec.asInstanceOf[AvroBinaryCodec[Double]].encode(registers.getDouble(offset, 0), e)
                      case AvroBinaryCodec.booleanType =>
                        codec.asInstanceOf[AvroBinaryCodec[Boolean]].encode(registers.getBoolean(offset, 0), e)
                      case AvroBinaryCodec.byteType =>
                        codec.asInstanceOf[AvroBinaryCodec[Byte]].encode(registers.getByte(offset, 0), e)
                      case AvroBinaryCodec.charType =>
                        codec.asInstanceOf[AvroBinaryCodec[Char]].encode(registers.getChar(offset, 0), e)
                      case AvroBinaryCodec.shortType =>
                        codec.asInstanceOf[AvroBinaryCodec[Short]].encode(registers.getShort(offset, 0), e)
                      case _ => codec.asInstanceOf[AvroBinaryCodec[Unit]].encode((), e)
                    }
                    offset = RegisterOffset.add(offset, codec.valueOffset)
                    idx += 1
                  }
                }
              }
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val wrapperBinding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              new AvroBinaryCodec[A]() {
                private[this] val codec  = deriveCodec(wrapper.wrapped).asInstanceOf[AvroBinaryCodec[Wrapped]]
                private[this] val unwrap = wrapperBinding.unwrap
                private[this] val wrap   = wrapperBinding.wrap

                def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): A =
                  wrap(codec.decode(DynamicOptic.Node.Wrapped :: t, d)) match {
                    case Right(x)  => x
                    case Left(err) => decodeError(t, err)
                  }

                def encode(x: A, e: BinaryEncoder): Unit = codec.encode(unwrap(x), e)
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
            else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          }
        }.asInstanceOf[AvroBinaryCodec[A]]

        private[this] lazy val dynamicValueCodec = new AvroBinaryCodec[DynamicValue]() {
          private[this] val primitiveDynamicValueCodec = deriveCodec(Schema.derived[DynamicValue.Primitive].reflect)
          private[this] val spanPrimitive              = new DynamicOptic.Node.Case("Primitive")
          private[this] val spanRecord                 = new DynamicOptic.Node.Case("Record")
          private[this] val spanVariant                = new DynamicOptic.Node.Case("Variant")
          private[this] val spanSequence               = new DynamicOptic.Node.Case("Sequence")
          private[this] val spanMap                    = new DynamicOptic.Node.Case("Map")
          private[this] val spanFields                 = new DynamicOptic.Node.Field("fields")
          private[this] val spanCaseName               = new DynamicOptic.Node.Field("caseName")
          private[this] val spanValue                  = new DynamicOptic.Node.Field("value")
          private[this] val spanElements               = new DynamicOptic.Node.Field("elements")
          private[this] val spanEntries                = new DynamicOptic.Node.Field("entries")
          private[this] val span_1                     = new DynamicOptic.Node.Field("_1")
          private[this] val span_2                     = new DynamicOptic.Node.Field("_2")

          def decode(t: List[DynamicOptic.Node], d: BinaryDecoder): DynamicValue = {
            val idx =
              try {
                d.readInt()
              } catch {
                case error if NonFatal(error) => decodeError(t, error)
              }
            idx match {
              case 0 =>
                primitiveDynamicValueCodec.decode(spanPrimitive :: t, d)
              case 1 =>
                val t_      = spanFields :: spanRecord :: t
                val builder = Vector.newBuilder[(String, DynamicValue)]
                var size    = 0
                var count   = 0L
                while ({
                  size =
                    try {
                      d.readInt()
                    } catch {
                      case error if NonFatal(error) => decodeError(t_, error)
                    }
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      t_,
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    val t__ = new DynamicOptic.Node.AtIndex(count.toInt) :: t_
                    val k   =
                      try {
                        d.readString()
                      } catch {
                        case error if NonFatal(error) => decodeError(span_1 :: t__, error)
                      }
                    builder.addOne((k, decode(span_2 :: t__, d)))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) decodeError(t_, s"Expected positive collection part size, got $size")
                new DynamicValue.Record(builder.result())
              case 2 =>
                val t_       = spanVariant :: t
                val caseName =
                  try {
                    d.readString()
                  } catch {
                    case error if NonFatal(error) => decodeError(spanCaseName :: t_, error)
                  }
                val value = decode(spanValue :: t_, d)
                new DynamicValue.Variant(caseName, value)
              case 3 =>
                val t_      = spanElements :: spanSequence :: t
                val builder = Vector.newBuilder[DynamicValue]
                var size    = 0
                var count   = 0L
                while ({
                  size =
                    try {
                      d.readInt()
                    } catch {
                      case error if NonFatal(error) => decodeError(t_, error)
                    }
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      t_,
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    builder.addOne(decode(new DynamicOptic.Node.AtIndex(count.toInt) :: t_, d))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) decodeError(t_, s"Expected positive collection part size, got $size")
                new DynamicValue.Sequence(builder.result())
              case 4 =>
                val t_      = spanEntries :: spanMap :: t
                val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
                var size    = 0
                var count   = 0L
                while ({
                  size =
                    try {
                      d.readInt()
                    } catch {
                      case error if NonFatal(error) => decodeError(t_, error)
                    }
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      t_,
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    val t__ = new DynamicOptic.Node.AtIndex(count.toInt) :: t_
                    val k   = decode(span_1 :: t__, d)
                    val v   = decode(span_2 :: t__, d)
                    builder.addOne((k, v))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) decodeError(t_, s"Expected positive collection part size, got $size")
                new DynamicValue.Map(builder.result())
              case idx => decodeError(t, s"Expected enum index from 0 to 4, got $idx")
            }
          }

          def encode(x: DynamicValue, e: BinaryEncoder): Unit = x match {
            case primitive: DynamicValue.Primitive =>
              e.writeInt(0)
              primitiveDynamicValueCodec.encode(primitive, e)
            case record: DynamicValue.Record =>
              e.writeInt(1)
              val fields = record.fields
              val size   = fields.length
              if (size > 0) {
                e.writeInt(size)
                val it = fields.iterator
                while (it.hasNext) {
                  val kv = it.next()
                  e.writeString(kv._1)
                  encode(kv._2, e)
                }
              }
              e.writeInt(0)
            case variant: DynamicValue.Variant =>
              e.writeInt(2)
              e.writeString(variant.caseName)
              encode(variant.value, e)
            case sequence: DynamicValue.Sequence =>
              e.writeInt(3)
              val elements = sequence.elements
              val size     = elements.length
              if (size > 0) {
                e.writeInt(size)
                val it = elements.iterator
                while (it.hasNext) {
                  encode(it.next(), e)
                }
              }
              e.writeInt(0)
            case map: DynamicValue.Map =>
              e.writeInt(4)
              val entries = map.entries
              val size    = entries.length
              if (size > 0) {
                e.writeInt(size)
                val it = entries.iterator
                while (it.hasNext) {
                  val kv = it.next()
                  encode(kv._1, e)
                  encode(kv._2, e)
                }
              }
              e.writeInt(0)
          }
        }
      }
    )
