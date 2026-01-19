package zio.blocks.schema.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema.binding.SeqDeconstructor._
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.{Owner, TypeId}
import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import scala.util.control.NonFatal

object AvroFormat
    extends BinaryFormat(
      "application/avro",
      new Deriver[AvroBinaryCodec] {
        override def derivePrimitive[F[_, _], A](
          primitiveType: PrimitiveType[A],
          typeId: TypeId[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        ): Lazy[AvroBinaryCodec[A]] =
          Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeId, binding, doc, modifiers)))

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeId: TypeId[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
          deriveCodec(
            new Reflect.Record(
              fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
              typeId,
              binding,
              doc,
              modifiers
            )
          )
        }

        override def deriveVariant[F[_, _], A](
          cases: IndexedSeq[Term[F, A, ?]],
          typeId: TypeId[A],
          binding: Binding[BindingType.Variant, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
          deriveCodec(
            new Reflect.Variant(
              cases.asInstanceOf[IndexedSeq[Term[Binding, A, ? <: A]]],
              typeId,
              binding,
              doc,
              modifiers
            )
          )
        }

        override def deriveSequence[F[_, _], C[_], A](
          element: Reflect[F, A],
          typeId: TypeId[C[A]],
          binding: Binding[BindingType.Seq[C], C[A]],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[C[A]]] = Lazy {
          deriveCodec(
            new Reflect.Sequence(element.asInstanceOf[Reflect[Binding, A]], typeId, binding, doc, modifiers)
          )
        }

        override def deriveMap[F[_, _], M[_, _], K, V](
          key: Reflect[F, K],
          value: Reflect[F, V],
          typeId: TypeId[M[K, V]],
          binding: Binding[BindingType.Map[M], M[K, V]],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[M[K, V]]] = Lazy {
          deriveCodec(
            new Reflect.Map(
              key.asInstanceOf[Reflect[Binding, K]],
              value.asInstanceOf[Reflect[Binding, V]],
              typeId,
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[DynamicValue]] =
          Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeId.nominal[DynamicValue]("DynamicValue", Owner(List(Owner.Package("zio"), Owner.Package("blocks"), Owner.Package("schema"))), Nil), doc, modifiers)))

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeId: TypeId[A],
          wrapperPrimitiveType: Option[PrimitiveType[A]],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
          deriveCodec(
            new Reflect.Wrapper(
              wrapped.asInstanceOf[Reflect[Binding, B]],
              typeId,
              wrapperPrimitiveType,
              binding,
              doc,
              modifiers
            )
          )
        }

        override def instanceOverrides: IndexedSeq[InstanceOverride] = {
          recursiveRecordCache.remove()
          recordCounters.remove()
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
          new ThreadLocal[java.util.HashMap[TypeId[?], (Array[AvroBinaryCodec[?]], AvroSchema)]] {
            override def initialValue: java.util.HashMap[TypeId[?], (Array[AvroBinaryCodec[?]], AvroSchema)] =
              new java.util.HashMap
          }
        private[this] val recordCounters =
          new ThreadLocal[java.util.HashMap[(String, String), Int]] {
            override def initialValue: java.util.HashMap[(String, String), Int] = new java.util.HashMap
          }
        private[this] val unitCodec = new AvroBinaryCodec[Unit](AvroBinaryCodec.unitType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.NULL)

          def decodeUnsafe(decoder: BinaryDecoder): Unit = ()

          def encode(value: Unit, encoder: BinaryEncoder): Unit = ()
        }
        private[this] val booleanCodec = new AvroBinaryCodec[Boolean](AvroBinaryCodec.booleanType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BOOLEAN)

          def decodeUnsafe(decoder: BinaryDecoder): Boolean = decoder.readBoolean()

          def encode(value: Boolean, encoder: BinaryEncoder): Unit = encoder.writeBoolean(value)
        }
        private[this] val byteCodec = new AvroBinaryCodec[Byte](AvroBinaryCodec.byteType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

          def decodeUnsafe(decoder: BinaryDecoder): Byte = {
            val x = decoder.readInt()
            if (x >= Byte.MinValue && x <= Byte.MaxValue) x.toByte
            else decodeError("Expected Byte")
          }

          def encode(value: Byte, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
        }
        private[this] val shortCodec = new AvroBinaryCodec[Short](AvroBinaryCodec.shortType) {
          val avroSchema: AvroSchema = byteCodec.avroSchema

          def decodeUnsafe(decoder: BinaryDecoder): Short = {
            val x = decoder.readInt()
            if (x >= Short.MinValue && x <= Short.MaxValue) x.toShort
            else decodeError("Expected Short")
          }

          def encode(value: Short, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
        }
        private[this] val intCodec = new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
          val avroSchema: AvroSchema = shortCodec.avroSchema

          def decodeUnsafe(decoder: BinaryDecoder): Int = decoder.readInt()

          def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
        }
        private[this] val longCodec = new AvroBinaryCodec[Long](AvroBinaryCodec.longType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.LONG)

          def decodeUnsafe(decoder: BinaryDecoder): Long = decoder.readLong()

          def encode(value: Long, encoder: BinaryEncoder): Unit = encoder.writeLong(value)
        }
        private[this] val floatCodec = new AvroBinaryCodec[Float](AvroBinaryCodec.floatType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.FLOAT)

          def decodeUnsafe(decoder: BinaryDecoder): Float = decoder.readFloat()

          def encode(value: Float, encoder: BinaryEncoder): Unit = encoder.writeFloat(value)
        }
        private[this] val doubleCodec = new AvroBinaryCodec[Double](AvroBinaryCodec.doubleType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.DOUBLE)

          def decodeUnsafe(decoder: BinaryDecoder): Double = decoder.readDouble()

          def encode(value: Double, encoder: BinaryEncoder): Unit = encoder.writeDouble(value)
        }
        private[this] val charCodec = new AvroBinaryCodec[Char](AvroBinaryCodec.charType) {
          val avroSchema: AvroSchema = intCodec.avroSchema

          def decodeUnsafe(decoder: BinaryDecoder): Char = {
            val x = decoder.readInt()
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError("Expected Char")
          }

          def encode(value: Char, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
        }
        private[this] val stringCodec = new AvroBinaryCodec[String]() {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

          def decodeUnsafe(decoder: BinaryDecoder): String = decoder.readString()

          def encode(value: String, encoder: BinaryEncoder): Unit = encoder.writeString(value)
        }
        private[this] val bigIntCodec = new AvroBinaryCodec[BigInt]() {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BYTES)

          def decodeUnsafe(decoder: BinaryDecoder): BigInt = BigInt(decoder.readBytes(null).array())

          def encode(value: BigInt, encoder: BinaryEncoder): Unit = encoder.writeBytes(value.toByteArray)
        }
        private[this] val bigDecimalCodec = new AvroBinaryCodec[BigDecimal]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](4)
            fields.add(new AvroSchema.Field("mantissa", bigIntCodec.avroSchema))
            fields.add(new AvroSchema.Field("scale", intAvroSchema))
            fields.add(new AvroSchema.Field("precision", intAvroSchema))
            fields.add(new AvroSchema.Field("roundingMode", intAvroSchema))
            createAvroRecord("scala", "BigDecimal", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): BigDecimal = {
            val mantissa     = decoder.readBytes(null).array()
            val scale        = decoder.readInt()
            val precision    = decoder.readInt()
            val roundingMode = java.math.RoundingMode.valueOf(decoder.readInt())
            val mc           = new MathContext(precision, roundingMode)
            new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
          }

          def encode(value: BigDecimal, encoder: BinaryEncoder): Unit = {
            val bd = value.underlying
            val mc = value.mc
            encoder.writeBytes(ByteBuffer.wrap(bd.unscaledValue.toByteArray))
            encoder.writeInt(bd.scale)
            encoder.writeInt(mc.getPrecision)
            encoder.writeInt(mc.getRoundingMode.ordinal)
          }
        }
        private[this] val dayOfWeekCodec = new AvroBinaryCodec[java.time.DayOfWeek]() {
          val avroSchema: AvroSchema = intCodec.avroSchema

          def decodeUnsafe(decoder: BinaryDecoder): java.time.DayOfWeek = java.time.DayOfWeek.of(decoder.readInt())

          def encode(value: java.time.DayOfWeek, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
        }
        private[this] val durationCodec = new AvroBinaryCodec[java.time.Duration]() {
          val avroSchema: AvroSchema = {
            val fields = new java.util.ArrayList[AvroSchema.Field](2)
            fields.add(new AvroSchema.Field("seconds", longCodec.avroSchema))
            fields.add(new AvroSchema.Field("nanos", intCodec.avroSchema))
            createAvroRecord("java.time", "Duration", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.Duration =
            java.time.Duration.ofSeconds(decoder.readLong(), decoder.readInt())

          def encode(value: java.time.Duration, encoder: BinaryEncoder): Unit = {
            encoder.writeLong(value.getSeconds)
            encoder.writeInt(value.getNano)
          }
        }
        private[this] val instantCodec = new AvroBinaryCodec[java.time.Instant]() {
          val avroSchema: AvroSchema = {
            val fields = new java.util.ArrayList[AvroSchema.Field](2)
            fields.add(new AvroSchema.Field("epochSecond", longCodec.avroSchema))
            fields.add(new AvroSchema.Field("nano", intCodec.avroSchema))
            createAvroRecord("java.time", "Instant", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.Instant =
            java.time.Instant.ofEpochSecond(decoder.readLong(), decoder.readInt())

          def encode(value: java.time.Instant, encoder: BinaryEncoder): Unit = {
            encoder.writeLong(value.getEpochSecond)
            encoder.writeInt(value.getNano)
          }
        }
        private[this] val localDateCodec = new AvroBinaryCodec[java.time.LocalDate]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](3)
            fields.add(new AvroSchema.Field("year", intAvroSchema))
            fields.add(new AvroSchema.Field("month", intAvroSchema))
            fields.add(new AvroSchema.Field("day", intAvroSchema))
            createAvroRecord("java.time", "LocalDate", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.LocalDate =
            java.time.LocalDate.of(decoder.readInt(), decoder.readInt(), decoder.readInt())

          def encode(value: java.time.LocalDate, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getYear)
            encoder.writeInt(value.getMonthValue)
            encoder.writeInt(value.getDayOfMonth)
          }
        }
        private[this] val localDateTimeCodec = new AvroBinaryCodec[java.time.LocalDateTime]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](7)
            fields.add(new AvroSchema.Field("year", intAvroSchema))
            fields.add(new AvroSchema.Field("month", intAvroSchema))
            fields.add(new AvroSchema.Field("day", intAvroSchema))
            fields.add(new AvroSchema.Field("hour", intAvroSchema))
            fields.add(new AvroSchema.Field("minute", intAvroSchema))
            fields.add(new AvroSchema.Field("second", intAvroSchema))
            fields.add(new AvroSchema.Field("nano", intAvroSchema))
            createAvroRecord("java.time", "LocalDateTime", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.LocalDateTime =
            java.time.LocalDateTime
              .of(
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt()
              )

          def encode(value: java.time.LocalDateTime, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getYear)
            encoder.writeInt(value.getMonthValue)
            encoder.writeInt(value.getDayOfMonth)
            encoder.writeInt(value.getHour)
            encoder.writeInt(value.getMinute)
            encoder.writeInt(value.getSecond)
            encoder.writeInt(value.getNano)
          }
        }
        private[this] val localTimeCodec = new AvroBinaryCodec[java.time.LocalTime]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](4)
            fields.add(new AvroSchema.Field("hour", intAvroSchema))
            fields.add(new AvroSchema.Field("minute", intAvroSchema))
            fields.add(new AvroSchema.Field("second", intAvroSchema))
            fields.add(new AvroSchema.Field("nano", intAvroSchema))
            createAvroRecord("java.time", "LocalTime", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.LocalTime =
            java.time.LocalTime.of(decoder.readInt(), decoder.readInt(), decoder.readInt(), decoder.readInt())

          def encode(value: java.time.LocalTime, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getHour)
            encoder.writeInt(value.getMinute)
            encoder.writeInt(value.getSecond)
            encoder.writeInt(value.getNano)
          }
        }
        private[this] val monthCodec = new AvroBinaryCodec[java.time.Month]() {
          val avroSchema: AvroSchema = intCodec.avroSchema

          def decodeUnsafe(decoder: BinaryDecoder): java.time.Month = java.time.Month.of(decoder.readInt())

          def encode(value: java.time.Month, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
        }
        private[this] val monthDayCodec = new AvroBinaryCodec[java.time.MonthDay]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](2)
            fields.add(new AvroSchema.Field("month", intAvroSchema))
            fields.add(new AvroSchema.Field("day", intAvroSchema))
            createAvroRecord("java.time", "MonthDay", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.MonthDay =
            java.time.MonthDay.of(decoder.readInt(), decoder.readInt())

          def encode(value: java.time.MonthDay, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getMonthValue)
            encoder.writeInt(value.getDayOfMonth)
          }
        }
        private[this] val offsetDateTimeCodec = new AvroBinaryCodec[java.time.OffsetDateTime]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](8)
            fields.add(new AvroSchema.Field("year", intAvroSchema))
            fields.add(new AvroSchema.Field("month", intAvroSchema))
            fields.add(new AvroSchema.Field("day", intAvroSchema))
            fields.add(new AvroSchema.Field("hour", intAvroSchema))
            fields.add(new AvroSchema.Field("minute", intAvroSchema))
            fields.add(new AvroSchema.Field("second", intAvroSchema))
            fields.add(new AvroSchema.Field("nano", intAvroSchema))
            fields.add(new AvroSchema.Field("offsetSecond", intAvroSchema))
            createAvroRecord("java.time", "OffsetDateTime", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.OffsetDateTime =
            java.time.OffsetDateTime.of(
              decoder.readInt(),
              decoder.readInt(),
              decoder.readInt(),
              decoder.readInt(),
              decoder.readInt(),
              decoder.readInt(),
              decoder.readInt(),
              java.time.ZoneOffset.ofTotalSeconds(decoder.readInt())
            )

          def encode(value: java.time.OffsetDateTime, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getYear)
            encoder.writeInt(value.getMonthValue)
            encoder.writeInt(value.getDayOfMonth)
            encoder.writeInt(value.getHour)
            encoder.writeInt(value.getMinute)
            encoder.writeInt(value.getSecond)
            encoder.writeInt(value.getNano)
            encoder.writeInt(value.getOffset.getTotalSeconds)
          }
        }
        private[this] val offsetTimeCodec = new AvroBinaryCodec[java.time.OffsetTime]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](5)
            fields.add(new AvroSchema.Field("hour", intAvroSchema))
            fields.add(new AvroSchema.Field("minute", intAvroSchema))
            fields.add(new AvroSchema.Field("second", intAvroSchema))
            fields.add(new AvroSchema.Field("nano", intAvroSchema))
            fields.add(new AvroSchema.Field("offsetSecond", intAvroSchema))
            createAvroRecord("java.time", "OffsetTime", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.OffsetTime =
            java.time.OffsetTime.of(
              decoder.readInt(),
              decoder.readInt(),
              decoder.readInt(),
              decoder.readInt(),
              java.time.ZoneOffset.ofTotalSeconds(decoder.readInt())
            )

          def encode(value: java.time.OffsetTime, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getHour)
            encoder.writeInt(value.getMinute)
            encoder.writeInt(value.getSecond)
            encoder.writeInt(value.getNano)
            encoder.writeInt(value.getOffset.getTotalSeconds)
          }
        }
        private[this] val periodCodec = new AvroBinaryCodec[java.time.Period]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](3)
            fields.add(new AvroSchema.Field("years", intAvroSchema))
            fields.add(new AvroSchema.Field("month", intAvroSchema))
            fields.add(new AvroSchema.Field("days", intAvroSchema))
            createAvroRecord("java.time", "Period", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.Period =
            java.time.Period.of(decoder.readInt(), decoder.readInt(), decoder.readInt())

          def encode(value: java.time.Period, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getYears)
            encoder.writeInt(value.getMonths)
            encoder.writeInt(value.getDays)
          }
        }
        private[this] val yearCodec = new AvroBinaryCodec[java.time.Year]() {
          val avroSchema: AvroSchema = intCodec.avroSchema

          def decodeUnsafe(decoder: BinaryDecoder): java.time.Year = java.time.Year.of(decoder.readInt())

          def encode(value: java.time.Year, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
        }
        private[this] val yearMonthCodec = new AvroBinaryCodec[java.time.YearMonth]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](2)
            fields.add(new AvroSchema.Field("year", intAvroSchema))
            fields.add(new AvroSchema.Field("month", intAvroSchema))
            createAvroRecord("java.time", "YearMonth", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.YearMonth =
            java.time.YearMonth.of(decoder.readInt(), decoder.readInt())

          def encode(value: java.time.YearMonth, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getYear)
            encoder.writeInt(value.getMonthValue)
          }
        }
        private[this] val zoneIdCodec = new AvroBinaryCodec[java.time.ZoneId]() {
          val avroSchema: AvroSchema = stringCodec.avroSchema

          def decodeUnsafe(decoder: BinaryDecoder): java.time.ZoneId = java.time.ZoneId.of(decoder.readString())

          def encode(value: java.time.ZoneId, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
        }
        private[this] val zoneOffsetCodec = new AvroBinaryCodec[java.time.ZoneOffset]() {
          val avroSchema: AvroSchema = intCodec.avroSchema

          def decodeUnsafe(decoder: BinaryDecoder): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(decoder.readInt())

          def encode(value: java.time.ZoneOffset, encoder: BinaryEncoder): Unit =
            encoder.writeInt(value.getTotalSeconds)
        }
        private[this] val zonedDateTimeCodec = new AvroBinaryCodec[java.time.ZonedDateTime]() {
          val avroSchema: AvroSchema = {
            val intAvroSchema = intCodec.avroSchema
            val fields        = new java.util.ArrayList[AvroSchema.Field](9)
            fields.add(new AvroSchema.Field("year", intAvroSchema))
            fields.add(new AvroSchema.Field("month", intAvroSchema))
            fields.add(new AvroSchema.Field("day", intAvroSchema))
            fields.add(new AvroSchema.Field("hour", intAvroSchema))
            fields.add(new AvroSchema.Field("minute", intAvroSchema))
            fields.add(new AvroSchema.Field("second", intAvroSchema))
            fields.add(new AvroSchema.Field("nano", intAvroSchema))
            fields.add(new AvroSchema.Field("offsetSecond", intAvroSchema))
            fields.add(new AvroSchema.Field("zoneId", stringCodec.avroSchema))
            createAvroRecord("java.time", "ZonedDateTime", fields)
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.ZonedDateTime =
            java.time.ZonedDateTime.ofInstant(
              java.time.LocalDateTime.of(
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt(),
                decoder.readInt()
              ),
              java.time.ZoneOffset.ofTotalSeconds(decoder.readInt()),
              java.time.ZoneId.of(decoder.readString())
            )

          def encode(value: java.time.ZonedDateTime, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getYear)
            encoder.writeInt(value.getMonthValue)
            encoder.writeInt(value.getDayOfMonth)
            encoder.writeInt(value.getHour)
            encoder.writeInt(value.getMinute)
            encoder.writeInt(value.getSecond)
            encoder.writeInt(value.getNano)
            encoder.writeInt(value.getOffset.getTotalSeconds)
            encoder.writeString(value.getZone.toString)
          }
        }
        private[this] val currencyCodec = new AvroBinaryCodec[java.util.Currency]() {
          val avroSchema: AvroSchema = AvroSchema.createFixed("Currency", null, "java.util", 3)

          def decodeUnsafe(decoder: BinaryDecoder): java.util.Currency = {
            val bs = new Array[Byte](3)
            decoder.readFixed(bs, 0, 3)
            java.util.Currency.getInstance(new String(bs))
          }

          def encode(value: java.util.Currency, encoder: BinaryEncoder): Unit = {
            val s = value.toString
            encoder.writeFixed(Array(s.charAt(0).toByte, s.charAt(1).toByte, s.charAt(2).toByte))
          }
        }
        private[this] val uuidCodec = new AvroBinaryCodec[java.util.UUID]() {
          val avroSchema: AvroSchema = AvroSchema.createFixed("UUID", null, "java.util", 16)

          def decodeUnsafe(decoder: BinaryDecoder): java.util.UUID = {
            val bs = new Array[Byte](16)
            decoder.readFixed(bs)
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
          }

          def encode(value: java.util.UUID, encoder: BinaryEncoder): Unit = {
            val hi = value.getMostSignificantBits
            val lo = value.getLeastSignificantBits
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
            encoder.writeFixed(bs)
          }
        }

        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): AvroBinaryCodec[A] = {
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
              val codecs  = new Array[AvroBinaryCodec[?]](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                idx += 1
              }
              new AvroBinaryCodec[A]() {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs

                val avroSchema: AvroSchema = {
                  val len             = codecs.length
                  val caseAvroSchemas = new java.util.ArrayList[AvroSchema](len)
                  var idx             = 0
                  while (idx < len) {
                    caseAvroSchemas.add(codecs(idx).avroSchema)
                    idx += 1
                  }
                  AvroSchema.createUnion(caseAvroSchemas)
                }

                def decodeUnsafe(decoder: BinaryDecoder): A = {
                  val idx = decoder.readInt()
                  if (idx >= 0 && idx < caseCodecs.length) {
                    try caseCodecs(idx).asInstanceOf[AvroBinaryCodec[A]].decodeUnsafe(decoder)
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
                    }
                  } else decodeError(s"Expected enum index from 0 to ${caseCodecs.length - 1}, got $idx")
                }

                def encode(value: A, encoder: BinaryEncoder): Unit = {
                  val idx = discriminator.discriminate(value)
                  encoder.writeInt(idx)
                  caseCodecs(idx).asInstanceOf[AvroBinaryCodec[A]].encode(value, encoder)
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec   = deriveCodec(sequence.element).asInstanceOf[AvroBinaryCodec[Elem]]
              codec.valueType match {
                case AvroBinaryCodec.booleanType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Boolean]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Boolean]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Boolean] = {
                      val builder = constructor.newBooleanBuilder()
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addBoolean(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultBoolean(builder)
                    }

                    def encode(value: Col[Boolean], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.byteType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Byte]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Byte]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Byte] = {
                      val builder = constructor.newByteBuilder()
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addByte(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultByte(builder)
                    }

                    def encode(value: Col[Byte], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.charType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Char]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Char]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Char] = {
                      val builder = constructor.newCharBuilder()
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addChar(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultChar(builder)
                    }

                    def encode(value: Col[Char], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.shortType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Short]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Short]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Short] = {
                      val builder = constructor.newShortBuilder()
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addShort(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultShort(builder)
                    }

                    def encode(value: Col[Short], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.floatType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Float]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Float]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Float] = {
                      val builder = constructor.newFloatBuilder()
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addFloat(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultFloat(builder)
                    }

                    def encode(value: Col[Float], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.intType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Int]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Int]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Int] = {
                      val builder = constructor.newIntBuilder()
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addInt(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultInt(builder)
                    }

                    def encode(value: Col[Int], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.doubleType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Double]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Double]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Double] = {
                      val builder = constructor.newDoubleBuilder()
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addDouble(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultDouble(builder)
                    }

                    def encode(value: Col[Double], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.longType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Long]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Long]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Long] = {
                      val builder = constructor.newLongBuilder()
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addLong(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultLong(builder)
                    }

                    def encode(value: Col[Long], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case _ =>
                  new AvroBinaryCodec[Col[Elem]]() {
                    private[this] val deconstructor = binding.deconstructor
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Elem] = {
                      val builder = constructor.newObjectBuilder[Elem](8)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addObject(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.resultObject[Elem](builder)
                    }

                    def encode(value: Col[Elem], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val codec1  = deriveCodec(map.key).asInstanceOf[AvroBinaryCodec[Key]]
              val codec2  = deriveCodec(map.value).asInstanceOf[AvroBinaryCodec[Value]]
              new AvroBinaryCodec[Map[Key, Value]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val keyCodec      = codec1
                private[this] val valueCodec    = codec2
                private[this] val keyReflect    = map.key.asInstanceOf[Reflect.Bound[Key]]

                val avroSchema: AvroSchema = map.key.asPrimitive match {
                  case Some(primitiveKey) if primitiveKey.primitiveType.isInstanceOf[PrimitiveType.String] =>
                    AvroSchema.createMap(valueCodec.avroSchema)
                  case _ =>
                    val fields = new java.util.ArrayList[AvroSchema.Field](2)
                    fields.add(new AvroSchema.Field("_1", keyCodec.avroSchema))
                    fields.add(new AvroSchema.Field("_2", valueCodec.avroSchema))
                    AvroSchema.createArray(createAvroRecord("scala", "Tuple2", fields))
                }

                def decodeUnsafe(decoder: BinaryDecoder): Map[Key, Value] = {
                  val builder = constructor.newObjectBuilder[Key, Value](8)
                  var count   = 0L
                  var size    = 0
                  while ({
                    size = decoder.readInt()
                    size > 0
                  }) {
                    if (count + size > AvroBinaryCodec.maxCollectionSize) {
                      decodeError(
                        s"Expected map size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                      )
                    }
                    while (size > 0) {
                      val k =
                        try keyCodec.decodeUnsafe(decoder)
                        catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      val v =
                        try valueCodec.decodeUnsafe(decoder)
                        catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), error)
                        }
                      constructor.addObject(builder, k, v)
                      count += 1
                      size -= 1
                    }
                  }
                  if (size < 0) decodeError(s"Expected positive map part size, got $size")
                  constructor.resultObject[Key, Value](builder)
                }

                def encode(value: Map[Key, Value], encoder: BinaryEncoder): Unit = {
                  val size = deconstructor.size(value)
                  if (size > 0) {
                    encoder.writeInt(size)
                    val it = deconstructor.deconstruct(value)
                    while (it.hasNext) {
                      val kv = it.next()
                      keyCodec.encode(deconstructor.getKey(kv), encoder)
                      valueCodec.encode(deconstructor.getValue(kv), encoder)
                    }
                  }
                  encoder.writeInt(0)
                }
              }
            } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val binding              = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields               = record.fields
              val isRecursive          = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
              val typeId               = record.typeId
              var codecsWithAvroSchema =
                if (isRecursive) recursiveRecordCache.get.get(typeId)
                else null
              var offset = 0L
              if (codecsWithAvroSchema eq null) {
                val namespaceBuilder = new java.lang.StringBuilder()
                typeId.owner.segments.foreach { segment =>
                  if (namespaceBuilder.length > 0) namespaceBuilder.append('.')
                  namespaceBuilder.append(segment.name)
                }
                val avroSchema = createAvroRecord(namespaceBuilder.toString, typeId.name)
                val len        = fields.length
                val codecs     = new Array[AvroBinaryCodec[?]](len)
                codecsWithAvroSchema = (codecs, avroSchema)
                if (isRecursive) recursiveRecordCache.get.put(typeId, codecsWithAvroSchema)
                val avroSchemaFields = new java.util.ArrayList[AvroSchema.Field](len)
                var idx              = 0
                while (idx < len) {
                  val field = fields(idx)
                  val codec = deriveCodec(field.value)
                  codecs(idx) = codec
                  avroSchemaFields.add(new AvroSchema.Field(field.name, codec.avroSchema))
                  offset = RegisterOffset.add(codec.valueOffset, offset)
                  idx += 1
                }
                avroSchema.setFields(avroSchemaFields)
              }
              new AvroBinaryCodec[A]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val usedRegisters = offset
                private[this] val fieldCodecs   = codecsWithAvroSchema._1

                val avroSchema: AvroSchema = codecsWithAvroSchema._2

                def decodeUnsafe(decoder: BinaryDecoder): A = {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  val len    = fieldCodecs.length
                  var idx    = 0
                  try {
                    while (idx < len) {
                      val codec = fieldCodecs(idx)
                      codec.valueType match {
                        case AvroBinaryCodec.objectType =>
                          regs.setObject(offset, codec.asInstanceOf[AvroBinaryCodec[AnyRef]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.intType =>
                          regs.setInt(offset, codec.asInstanceOf[AvroBinaryCodec[Int]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.longType =>
                          regs.setLong(offset, codec.asInstanceOf[AvroBinaryCodec[Long]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.floatType =>
                          regs.setFloat(offset, codec.asInstanceOf[AvroBinaryCodec[Float]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.doubleType =>
                          regs.setDouble(offset, codec.asInstanceOf[AvroBinaryCodec[Double]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.booleanType =>
                          regs.setBoolean(offset, codec.asInstanceOf[AvroBinaryCodec[Boolean]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.byteType =>
                          regs.setByte(offset, codec.asInstanceOf[AvroBinaryCodec[Byte]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.charType =>
                          regs.setChar(offset, codec.asInstanceOf[AvroBinaryCodec[Char]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.shortType =>
                          regs.setShort(offset, codec.asInstanceOf[AvroBinaryCodec[Short]].decodeUnsafe(decoder))
                        case _ => codec.asInstanceOf[AvroBinaryCodec[Unit]].decodeUnsafe(decoder)
                      }
                      offset += codec.valueOffset
                      idx += 1
                    }
                    constructor.construct(regs, 0)
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                  }
                }

                def encode(value: A, encoder: BinaryEncoder): Unit = {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  deconstructor.deconstruct(regs, offset, value)
                  val len = fieldCodecs.length
                  var idx = 0
                  while (idx < len) {
                    val codec = fieldCodecs(idx)
                    codec.valueType match {
                      case AvroBinaryCodec.objectType =>
                        codec.asInstanceOf[AvroBinaryCodec[AnyRef]].encode(regs.getObject(offset), encoder)
                      case AvroBinaryCodec.intType =>
                        codec.asInstanceOf[AvroBinaryCodec[Int]].encode(regs.getInt(offset), encoder)
                      case AvroBinaryCodec.longType =>
                        codec.asInstanceOf[AvroBinaryCodec[Long]].encode(regs.getLong(offset), encoder)
                      case AvroBinaryCodec.floatType =>
                        codec.asInstanceOf[AvroBinaryCodec[Float]].encode(regs.getFloat(offset), encoder)
                      case AvroBinaryCodec.doubleType =>
                        codec.asInstanceOf[AvroBinaryCodec[Double]].encode(regs.getDouble(offset), encoder)
                      case AvroBinaryCodec.booleanType =>
                        codec.asInstanceOf[AvroBinaryCodec[Boolean]].encode(regs.getBoolean(offset), encoder)
                      case AvroBinaryCodec.byteType =>
                        codec.asInstanceOf[AvroBinaryCodec[Byte]].encode(regs.getByte(offset), encoder)
                      case AvroBinaryCodec.charType =>
                        codec.asInstanceOf[AvroBinaryCodec[Char]].encode(regs.getChar(offset), encoder)
                      case AvroBinaryCodec.shortType =>
                        codec.asInstanceOf[AvroBinaryCodec[Short]].encode(regs.getShort(offset), encoder)
                      case _ => codec.asInstanceOf[AvroBinaryCodec[Unit]].encode((), encoder)
                    }
                    offset += codec.valueOffset
                    idx += 1
                  }
                }
              }
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val codec   = deriveCodec(wrapper.wrapped).asInstanceOf[AvroBinaryCodec[Wrapped]]
              new AvroBinaryCodec[A](wrapper.wrapperPrimitiveType.fold(AvroBinaryCodec.objectType) {
                case _: PrimitiveType.Boolean   => AvroBinaryCodec.booleanType
                case _: PrimitiveType.Byte      => AvroBinaryCodec.byteType
                case _: PrimitiveType.Char      => AvroBinaryCodec.charType
                case _: PrimitiveType.Short     => AvroBinaryCodec.shortType
                case _: PrimitiveType.Float     => AvroBinaryCodec.floatType
                case _: PrimitiveType.Int       => AvroBinaryCodec.intType
                case _: PrimitiveType.Double    => AvroBinaryCodec.doubleType
                case _: PrimitiveType.Long      => AvroBinaryCodec.longType
                case _: PrimitiveType.Unit.type => AvroBinaryCodec.unitType
                case _                          => AvroBinaryCodec.objectType
              }) {
                private[this] val unwrap       = binding.unwrap
                private[this] val wrap         = binding.wrap
                private[this] val wrappedCodec = codec

                val avroSchema: AvroSchema = wrappedCodec.avroSchema

                def decodeUnsafe(decoder: BinaryDecoder): A = {
                  val wrapped =
                    try wrappedCodec.decodeUnsafe(decoder)
                    catch {
                      case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                    }
                  wrap(wrapped) match {
                    case Right(x)  => x
                    case Left(err) => decodeError(err)
                  }
                }

                def encode(value: A, encoder: BinaryEncoder): Unit = wrappedCodec.encode(unwrap(value), encoder)
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
            else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          }
        }.asInstanceOf[AvroBinaryCodec[A]]

        private[this] val dynamicValueCodec = new AvroBinaryCodec[DynamicValue]() {
          private[this] val spanPrimitive = new DynamicOptic.Node.Case("Primitive")
          private[this] val spanRecord    = new DynamicOptic.Node.Case("Record")
          private[this] val spanVariant   = new DynamicOptic.Node.Case("Variant")
          private[this] val spanSequence  = new DynamicOptic.Node.Case("Sequence")
          private[this] val spanMap       = new DynamicOptic.Node.Case("Map")
          private[this] val spanFields    = new DynamicOptic.Node.Field("fields")
          private[this] val spanCaseName  = new DynamicOptic.Node.Field("caseName")
          private[this] val spanValue     = new DynamicOptic.Node.Field("value")
          private[this] val spanElements  = new DynamicOptic.Node.Field("elements")
          private[this] val spanEntries   = new DynamicOptic.Node.Field("entries")
          private[this] val span_1        = new DynamicOptic.Node.Field("_1")
          private[this] val span_2        = new DynamicOptic.Node.Field("_2")

          val avroSchema: AvroSchema = {
            val dynamicValue       = createAvroRecord("zio.blocks.schema", "DynamicValue")
            val dynamicValueFields = new java.util.ArrayList[AvroSchema.Field](1)
            dynamicValueFields.add(
              new AvroSchema.Field(
                "value",
                AvroSchema.createUnion(
                  {
                    val primitiveFields = new java.util.ArrayList[AvroSchema.Field](1)
                    primitiveFields.add(
                      new AvroSchema.Field(
                        "value",
                        AvroSchema.createUnion(
                          createAvroRecord(
                            "zio.blocks.schema.PrimitiveValue",
                            "Unit",
                            new java.util.ArrayList[AvroSchema.Field](0)
                          ),
                          createPrimitiveValueAvroRecord("Boolean", booleanCodec),
                          createPrimitiveValueAvroRecord("Byte", byteCodec),
                          createPrimitiveValueAvroRecord("Short", shortCodec),
                          createPrimitiveValueAvroRecord("Int", intCodec),
                          createPrimitiveValueAvroRecord("Long", longCodec),
                          createPrimitiveValueAvroRecord("Float", floatCodec),
                          createPrimitiveValueAvroRecord("Double", doubleCodec),
                          createPrimitiveValueAvroRecord("Char", charCodec),
                          createPrimitiveValueAvroRecord("String", stringCodec),
                          createPrimitiveValueAvroRecord("BigInt", bigIntCodec),
                          createPrimitiveValueAvroRecord("BigDecimal", bigDecimalCodec),
                          createPrimitiveValueAvroRecord("DayOfWeek", dayOfWeekCodec),
                          createPrimitiveValueAvroRecord("Duration", durationCodec),
                          createPrimitiveValueAvroRecord("Instant", instantCodec),
                          createPrimitiveValueAvroRecord("LocalDate", localDateCodec),
                          createPrimitiveValueAvroRecord("LocalDateTime", localDateTimeCodec),
                          createPrimitiveValueAvroRecord("LocalTime", localTimeCodec),
                          createPrimitiveValueAvroRecord("Month", monthCodec),
                          createPrimitiveValueAvroRecord("MonthDay", monthDayCodec),
                          createPrimitiveValueAvroRecord("OffsetDateTime", offsetDateTimeCodec),
                          createPrimitiveValueAvroRecord("OffsetTime", offsetTimeCodec),
                          createPrimitiveValueAvroRecord("Period", periodCodec),
                          createPrimitiveValueAvroRecord("Year", yearCodec),
                          createPrimitiveValueAvroRecord("YearMonth", yearMonthCodec),
                          createPrimitiveValueAvroRecord("ZoneId", zoneIdCodec),
                          createPrimitiveValueAvroRecord("ZoneOffset", zoneOffsetCodec),
                          createPrimitiveValueAvroRecord("ZonedDateTime", zonedDateTimeCodec),
                          createPrimitiveValueAvroRecord("Currency", currencyCodec),
                          createPrimitiveValueAvroRecord("UUID", uuidCodec)
                        )
                      )
                    )
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Primitive", primitiveFields)
                  }, {
                    val recordFields = new java.util.ArrayList[AvroSchema.Field](1)
                    recordFields.add(
                      new AvroSchema.Field(
                        "fields",
                        AvroSchema.createArray {
                          val fieldFields = new java.util.ArrayList[AvroSchema.Field](2)
                          fieldFields.add(new AvroSchema.Field("name", stringCodec.avroSchema))
                          fieldFields.add(new AvroSchema.Field("value", dynamicValue))
                          createAvroRecord("zio.blocks.schema.internal", "Field", fieldFields)
                        }
                      )
                    )
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Record", recordFields)
                  }, {
                    val variantFields = new java.util.ArrayList[AvroSchema.Field](2)
                    variantFields.add(new AvroSchema.Field("caseName", stringCodec.avroSchema))
                    variantFields.add(new AvroSchema.Field("value", dynamicValue))
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Variant", variantFields)
                  }, {
                    val sequenceFields = new java.util.ArrayList[AvroSchema.Field](1)
                    sequenceFields.add(new AvroSchema.Field("elements", AvroSchema.createArray(dynamicValue)))
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Sequence", sequenceFields)
                  }, {
                    val mapFields = new java.util.ArrayList[AvroSchema.Field](1)
                    mapFields.add(
                      new AvroSchema.Field(
                        "entries",
                        AvroSchema.createArray {
                          val entryFields = new java.util.ArrayList[AvroSchema.Field](2)
                          entryFields.add(new AvroSchema.Field("key", dynamicValue))
                          entryFields.add(new AvroSchema.Field("value", dynamicValue))
                          createAvroRecord("zio.blocks.schema.internal", "Entry", entryFields)
                        }
                      )
                    )
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Map", mapFields)
                  }
                )
              )
            )
            dynamicValue.setFields(dynamicValueFields)
            dynamicValue
          }

          def decodeUnsafe(decoder: BinaryDecoder): DynamicValue = decoder.readInt() match {
            case 0 =>
              try {
                val idx = decoder.readInt()
                if (idx < 0 || idx > 29) decodeError(s"Expected enum index from 0 to 29, got $idx")
                try {
                  new DynamicValue.Primitive((idx: @scala.annotation.switch) match {
                    case 0  => PrimitiveValue.Unit
                    case 1  => new PrimitiveValue.Boolean(booleanCodec.decodeUnsafe(decoder))
                    case 2  => new PrimitiveValue.Byte(byteCodec.decodeUnsafe(decoder))
                    case 3  => new PrimitiveValue.Short(shortCodec.decodeUnsafe(decoder))
                    case 4  => new PrimitiveValue.Int(intCodec.decodeUnsafe(decoder))
                    case 5  => new PrimitiveValue.Long(longCodec.decodeUnsafe(decoder))
                    case 6  => new PrimitiveValue.Float(floatCodec.decodeUnsafe(decoder))
                    case 7  => new PrimitiveValue.Double(doubleCodec.decodeUnsafe(decoder))
                    case 8  => new PrimitiveValue.Char(charCodec.decodeUnsafe(decoder))
                    case 9  => new PrimitiveValue.String(stringCodec.decodeUnsafe(decoder))
                    case 10 => new PrimitiveValue.BigInt(bigIntCodec.decodeUnsafe(decoder))
                    case 11 => new PrimitiveValue.BigDecimal(bigDecimalCodec.decodeUnsafe(decoder))
                    case 12 => new PrimitiveValue.DayOfWeek(dayOfWeekCodec.decodeUnsafe(decoder))
                    case 13 => new PrimitiveValue.Duration(durationCodec.decodeUnsafe(decoder))
                    case 14 => new PrimitiveValue.Instant(instantCodec.decodeUnsafe(decoder))
                    case 15 => new PrimitiveValue.LocalDate(localDateCodec.decodeUnsafe(decoder))
                    case 16 => new PrimitiveValue.LocalDateTime(localDateTimeCodec.decodeUnsafe(decoder))
                    case 17 => new PrimitiveValue.LocalTime(localTimeCodec.decodeUnsafe(decoder))
                    case 18 => new PrimitiveValue.Month(monthCodec.decodeUnsafe(decoder))
                    case 19 => new PrimitiveValue.MonthDay(monthDayCodec.decodeUnsafe(decoder))
                    case 20 => new PrimitiveValue.OffsetDateTime(offsetDateTimeCodec.decodeUnsafe(decoder))
                    case 21 => new PrimitiveValue.OffsetTime(offsetTimeCodec.decodeUnsafe(decoder))
                    case 22 => new PrimitiveValue.Period(periodCodec.decodeUnsafe(decoder))
                    case 23 => new PrimitiveValue.Year(yearCodec.decodeUnsafe(decoder))
                    case 24 => new PrimitiveValue.YearMonth(yearMonthCodec.decodeUnsafe(decoder))
                    case 25 => new PrimitiveValue.ZoneId(zoneIdCodec.decodeUnsafe(decoder))
                    case 26 => new PrimitiveValue.ZoneOffset(zoneOffsetCodec.decodeUnsafe(decoder))
                    case 27 => new PrimitiveValue.ZonedDateTime(zonedDateTimeCodec.decodeUnsafe(decoder))
                    case 28 => new PrimitiveValue.Currency(currencyCodec.decodeUnsafe(decoder))
                    case _  => new PrimitiveValue.UUID(uuidCodec.decodeUnsafe(decoder))
                  })
                } catch {
                  case error if NonFatal(error) => decodeError(spanValue, error)
                }
              } catch {
                case error if NonFatal(error) => decodeError(spanPrimitive, error)
              }
            case 1 =>
              try {
                val builder = Vector.newBuilder[(String, DynamicValue)]
                var count   = 0L
                var size    = 0
                while ({
                  size = decoder.readInt()
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    val k =
                      try decoder.readString()
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count.toInt), span_1, error)
                      }
                    val v =
                      try decodeUnsafe(decoder)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count.toInt), span_2, error)
                      }
                    builder.addOne((k, v))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                new DynamicValue.Record(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(spanRecord, spanFields, error)
              }
            case 2 =>
              val caseName =
                try decoder.readString()
                catch {
                  case error if NonFatal(error) => decodeError(spanVariant, spanCaseName, error)
                }
              val value =
                try decodeUnsafe(decoder)
                catch {
                  case error if NonFatal(error) => decodeError(spanVariant, spanValue, error)
                }
              new DynamicValue.Variant(caseName, value)
            case 3 =>
              try {
                val builder = Vector.newBuilder[DynamicValue]
                var count   = 0L
                var size    = 0
                while ({
                  size = decoder.readInt()
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  try {
                    while (size > 0) {
                      builder.addOne(decodeUnsafe(decoder))
                      count += 1
                      size -= 1
                    }
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                  }
                }
                if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                new DynamicValue.Sequence(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(spanSequence, spanElements, error)
              }
            case 4 =>
              try {
                val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
                var count   = 0L
                var size    = 0
                while ({
                  size = decoder.readInt()
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    val k =
                      try decodeUnsafe(decoder)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count.toInt), span_1, error)
                      }
                    val v =
                      try decodeUnsafe(decoder)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count.toInt), span_2, error)
                      }
                    builder.addOne((k, v))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                new DynamicValue.Map(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(spanMap, spanEntries, error)
              }
            case idx => decodeError(s"Expected enum index from 0 to 4, got $idx")
          }

          def encode(value: DynamicValue, encoder: BinaryEncoder): Unit = value match {
            case primitive: DynamicValue.Primitive =>
              encoder.writeInt(0)
              primitive.value match {
                case _: PrimitiveValue.Unit.type =>
                  encoder.writeInt(0)
                case v: PrimitiveValue.Boolean =>
                  encoder.writeInt(1)
                  booleanCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Byte =>
                  encoder.writeInt(2)
                  byteCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Short =>
                  encoder.writeInt(3)
                  shortCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Int =>
                  encoder.writeInt(4)
                  intCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Long =>
                  encoder.writeInt(5)
                  longCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Float =>
                  encoder.writeInt(6)
                  floatCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Double =>
                  encoder.writeInt(7)
                  doubleCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Char =>
                  encoder.writeInt(8)
                  charCodec.encode(v.value, encoder)
                case v: PrimitiveValue.String =>
                  encoder.writeInt(9)
                  stringCodec.encode(v.value, encoder)
                case v: PrimitiveValue.BigInt =>
                  encoder.writeInt(10)
                  bigIntCodec.encode(v.value, encoder)
                case v: PrimitiveValue.BigDecimal =>
                  encoder.writeInt(11)
                  bigDecimalCodec.encode(v.value, encoder)
                case v: PrimitiveValue.DayOfWeek =>
                  encoder.writeInt(12)
                  dayOfWeekCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Duration =>
                  encoder.writeInt(13)
                  durationCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Instant =>
                  encoder.writeInt(14)
                  instantCodec.encode(v.value, encoder)
                case v: PrimitiveValue.LocalDate =>
                  encoder.writeInt(15)
                  localDateCodec.encode(v.value, encoder)
                case v: PrimitiveValue.LocalDateTime =>
                  encoder.writeInt(16)
                  localDateTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.LocalTime =>
                  encoder.writeInt(17)
                  localTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Month =>
                  encoder.writeInt(18)
                  monthCodec.encode(v.value, encoder)
                case v: PrimitiveValue.MonthDay =>
                  encoder.writeInt(19)
                  monthDayCodec.encode(v.value, encoder)
                case v: PrimitiveValue.OffsetDateTime =>
                  encoder.writeInt(20)
                  offsetDateTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.OffsetTime =>
                  encoder.writeInt(21)
                  offsetTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Period =>
                  encoder.writeInt(22)
                  periodCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Year =>
                  encoder.writeInt(23)
                  yearCodec.encode(v.value, encoder)
                case v: PrimitiveValue.YearMonth =>
                  encoder.writeInt(24)
                  yearMonthCodec.encode(v.value, encoder)
                case v: PrimitiveValue.ZoneId =>
                  encoder.writeInt(25)
                  zoneIdCodec.encode(v.value, encoder)
                case v: PrimitiveValue.ZoneOffset =>
                  encoder.writeInt(26)
                  zoneOffsetCodec.encode(v.value, encoder)
                case v: PrimitiveValue.ZonedDateTime =>
                  encoder.writeInt(27)
                  zonedDateTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Currency =>
                  encoder.writeInt(28)
                  currencyCodec.encode(v.value, encoder)
                case v: PrimitiveValue.UUID =>
                  encoder.writeInt(29)
                  uuidCodec.encode(v.value, encoder)
              }
            case record: DynamicValue.Record =>
              encoder.writeInt(1)
              val fields = record.fields
              val size   = fields.length
              if (size > 0) {
                encoder.writeInt(size)
                val it = fields.iterator
                while (it.hasNext) {
                  val kv = it.next()
                  encoder.writeString(kv._1)
                  encode(kv._2, encoder)
                }
              }
              encoder.writeInt(0)
            case variant: DynamicValue.Variant =>
              encoder.writeInt(2)
              encoder.writeString(variant.caseName)
              encode(variant.value, encoder)
            case sequence: DynamicValue.Sequence =>
              encoder.writeInt(3)
              val elements = sequence.elements
              val size     = elements.length
              if (size > 0) {
                encoder.writeInt(size)
                val it = elements.iterator
                while (it.hasNext) {
                  encode(it.next(), encoder)
                }
              }
              encoder.writeInt(0)
            case map: DynamicValue.Map =>
              encoder.writeInt(4)
              val entries = map.entries
              val size    = entries.length
              if (size > 0) {
                encoder.writeInt(size)
                val it = entries.iterator
                while (it.hasNext) {
                  val kv = it.next()
                  encode(kv._1, encoder)
                  encode(kv._2, encoder)
                }
              }
              encoder.writeInt(0)
          }

          private[this] def createPrimitiveValueAvroRecord(name: String, codec: AvroBinaryCodec[?]): AvroSchema = {
            val avroSchema = codec.avroSchema
            val fields     = new java.util.ArrayList[AvroSchema.Field](1)
            fields.add(new AvroSchema.Field("value", avroSchema))
            createAvroRecord("zio.blocks.schema.PrimitiveValue", name, fields)
          }
        }

        private[this] def createAvroRecord(
          namespace: String,
          name: String,
          fields: java.util.ArrayList[AvroSchema.Field] = null
        ): AvroSchema = {
          val number     = recordCounters.get().compute((namespace, name), (_: (String, String), n: Int) => n + 1) - 1
          val recordName =
            if (number > 0) (new java.lang.StringBuilder).append(name).append('_').append(number).toString
            else name
          if (fields eq null) AvroSchema.createRecord(recordName, null, namespace, false)
          else AvroSchema.createRecord(recordName, null, namespace, false, fields)
        }
      }
    )
