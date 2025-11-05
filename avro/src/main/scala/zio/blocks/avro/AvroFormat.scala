package zio.blocks.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
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
          new ThreadLocal[java.util.HashMap[TypeName[?], (Array[AvroBinaryCodec[?]], AvroSchema)]] {
            override def initialValue: java.util.HashMap[TypeName[?], (Array[AvroBinaryCodec[?]], AvroSchema)] =
              new java.util.HashMap
          }
        private[this] val unitCodec = new AvroBinaryCodec[Unit](AvroBinaryCodec.unitType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.NULL)

          def decodeUnsafe(decoder: BinaryDecoder): Unit = ()

          def encode(value: Unit, encoder: BinaryEncoder): Unit = ()
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
        private[this] val booleanCodec = new AvroBinaryCodec[Boolean](AvroBinaryCodec.booleanType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BOOLEAN)

          def decodeUnsafe(decoder: BinaryDecoder): Boolean = decoder.readBoolean()

          def encode(value: Boolean, encoder: BinaryEncoder): Unit = encoder.writeBoolean(value)
        }
        private[this] val shortCodec = new AvroBinaryCodec[Short](AvroBinaryCodec.shortType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

          def decodeUnsafe(decoder: BinaryDecoder): Short = {
            val x = decoder.readInt()
            if (x >= Short.MinValue && x <= Short.MaxValue) x.toShort
            else decodeError("Expected Short")
          }

          def encode(value: Short, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
        }
        private[this] val charCodec = new AvroBinaryCodec[Char](AvroBinaryCodec.charType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

          def decodeUnsafe(decoder: BinaryDecoder): Char = {
            val x = decoder.readInt()
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError("Expected Char")
          }

          def encode(value: Char, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
        }
        private[this] val intCodec = new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

          def decodeUnsafe(decoder: BinaryDecoder): Int = decoder.readInt()

          def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
        }
        private[this] val floatCodec = new AvroBinaryCodec[Float](AvroBinaryCodec.floatType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.FLOAT)

          def decodeUnsafe(decoder: BinaryDecoder): Float = decoder.readFloat()

          def encode(value: Float, encoder: BinaryEncoder): Unit = encoder.writeFloat(value)
        }
        private[this] val longCodec = new AvroBinaryCodec[Long](AvroBinaryCodec.longType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.LONG)

          def decodeUnsafe(decoder: BinaryDecoder): Long = decoder.readLong()

          def encode(value: Long, encoder: BinaryEncoder): Unit = encoder.writeLong(value)
        }
        private[this] val doubleCodec = new AvroBinaryCodec[Double](AvroBinaryCodec.doubleType) {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.DOUBLE)

          def decodeUnsafe(decoder: BinaryDecoder): Double = decoder.readDouble()

          def encode(value: Double, encoder: BinaryEncoder): Unit = encoder.writeDouble(value)
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
            val avroSchema = AvroSchema.createRecord("BigDecimal", null, "scala", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("mantissa", AvroSchema.create(AvroSchema.Type.BYTES)))
            fields.add(new AvroSchema.Field("scale", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("precision", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("roundingMode", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

          def decodeUnsafe(decoder: BinaryDecoder): java.time.DayOfWeek = java.time.DayOfWeek.of(decoder.readInt())

          def encode(value: java.time.DayOfWeek, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
        }
        private[this] val durationCodec = new AvroBinaryCodec[java.time.Duration]() {
          val avroSchema: AvroSchema = {
            val avroSchema = AvroSchema.createRecord("Duration", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("seconds", AvroSchema.create(AvroSchema.Type.LONG)))
            fields.add(new AvroSchema.Field("nanos", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
            val avroSchema = AvroSchema.createRecord("Instant", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("epochSecond", AvroSchema.create(AvroSchema.Type.LONG)))
            fields.add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
            val avroSchema = AvroSchema.createRecord("LocalDate", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
            val avroSchema = AvroSchema.createRecord("LocalDateTime", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
            val avroSchema = AvroSchema.createRecord("LocalTime", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

          def decodeUnsafe(decoder: BinaryDecoder): java.time.Month = java.time.Month.of(decoder.readInt())

          def encode(value: java.time.Month, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
        }
        private[this] val monthDayCodec = new AvroBinaryCodec[java.time.MonthDay]() {
          val avroSchema: AvroSchema = {
            val avroSchema = AvroSchema.createRecord("MonthDay", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
            val avroSchema = AvroSchema.createRecord("OffsetDateTime", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("offsetSecond", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
            val avroSchema = AvroSchema.createRecord("OffsetTime", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("offsetSecond", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
            val avroSchema = AvroSchema.createRecord("Period", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("years", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("days", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
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
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

          def decodeUnsafe(decoder: BinaryDecoder): java.time.Year = java.time.Year.of(decoder.readInt())

          def encode(value: java.time.Year, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
        }
        private[this] val yearMonthCodec = new AvroBinaryCodec[java.time.YearMonth]() {
          val avroSchema: AvroSchema = {
            val avroSchema = AvroSchema.createRecord("YearMonth", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            avroSchema.setFields(fields)
            avroSchema
          }

          def decodeUnsafe(decoder: BinaryDecoder): java.time.YearMonth =
            java.time.YearMonth.of(decoder.readInt(), decoder.readInt())

          def encode(value: java.time.YearMonth, encoder: BinaryEncoder): Unit = {
            encoder.writeInt(value.getYear)
            encoder.writeInt(value.getMonthValue)
          }
        }
        private[this] val zoneIdCodec = new AvroBinaryCodec[java.time.ZoneId]() {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

          def decodeUnsafe(decoder: BinaryDecoder): java.time.ZoneId = java.time.ZoneId.of(decoder.readString())

          def encode(value: java.time.ZoneId, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
        }
        private[this] val zoneOffsetCodec = new AvroBinaryCodec[java.time.ZoneOffset]() {
          val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

          def decodeUnsafe(decoder: BinaryDecoder): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(decoder.readInt())

          def encode(value: java.time.ZoneOffset, encoder: BinaryEncoder): Unit =
            encoder.writeInt(value.getTotalSeconds)
        }
        private[this] val zonedDateTimeCodec = new AvroBinaryCodec[java.time.ZonedDateTime]() {
          val avroSchema: AvroSchema = {
            val avroSchema = AvroSchema.createRecord("ZonedDateTime", null, "java.time", false)
            val fields     = new java.util.ArrayList[AvroSchema.Field]
            fields.add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("offsetSecond", AvroSchema.create(AvroSchema.Type.INT)))
            fields.add(new AvroSchema.Field("zoneId", AvroSchema.create(AvroSchema.Type.STRING)))
            avroSchema.setFields(fields)
            avroSchema
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
              val cases          = variant.cases
              val len            = cases.length
              val codecs         = new Array[AvroBinaryCodec[?]](len)
              var idx            = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                idx += 1
              }
              new AvroBinaryCodec[A]() {
                private[this] val discriminator = variantBinding.discriminator
                private[this] val caseCodecs    = codecs

                val avroSchema: AvroSchema = {
                  val caseAvroSchemas = new java.util.ArrayList[AvroSchema]
                  caseCodecs.foreach(codec => caseAvroSchemas.add(codec.avroSchema))
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
              val seqBinding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec      = deriveCodec(sequence.element).asInstanceOf[AvroBinaryCodec[Elem]]
              new AvroBinaryCodec[Col[Elem]]() {
                private[this] val deconstructor = seqBinding.deconstructor
                private[this] val constructor   = seqBinding.constructor
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
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
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
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val mapBinding = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val codec1     = deriveCodec(map.key).asInstanceOf[AvroBinaryCodec[Key]]
              val codec2     = deriveCodec(map.value).asInstanceOf[AvroBinaryCodec[Value]]
              new AvroBinaryCodec[Map[Key, Value]]() {
                private[this] val deconstructor = mapBinding.deconstructor
                private[this] val constructor   = mapBinding.constructor
                private[this] val keyCodec      = codec1
                private[this] val valueCodec    = codec2

                val avroSchema: AvroSchema = map.key.asPrimitive match {
                  case Some(primitiveKey) if primitiveKey.primitiveType.isInstanceOf[PrimitiveType.String] =>
                    AvroSchema.createMap(valueCodec.avroSchema)
                  case _ =>
                    val name             = s"Tuple2_${map.typeName.params.hashCode.toHexString}"
                    val tuple2AvroSchema = AvroSchema.createRecord(name, null, "scala", false)
                    val fields           = new java.util.ArrayList[AvroSchema.Field]
                    fields.add(new AvroSchema.Field("_1", keyCodec.avroSchema))
                    fields.add(new AvroSchema.Field("_2", valueCodec.avroSchema))
                    tuple2AvroSchema.setFields(fields)
                    AvroSchema.createArray(tuple2AvroSchema)
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
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtMapKey(k), error)
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
              val recordBinding        = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields               = record.fields
              val isRecursive          = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
              var codecsWithAvroSchema =
                if (isRecursive) recursiveRecordCache.get.get(record.typeName)
                else null
              if (codecsWithAvroSchema eq null) {
                val len        = fields.length
                val codecs     = new Array[AvroBinaryCodec[?]](len)
                val typeName   = record.typeName
                val avroSchema = AvroSchema.createRecord(getName(typeName), null, getNamespace(typeName), false)
                codecsWithAvroSchema = (codecs, avroSchema)
                if (isRecursive) recursiveRecordCache.get.put(record.typeName, codecsWithAvroSchema)
                if (avroSchema.hasFields) {
                  var idx = 0
                  while (idx < len) {
                    codecs(idx) = deriveCodec(fields(idx).value)
                    idx += 1
                  }
                } else {
                  val avroSchemaFields = new java.util.ArrayList[AvroSchema.Field]
                  var idx              = 0
                  while (idx < len) {
                    val field = fields(idx)
                    val codec = deriveCodec(field.value)
                    codecs(idx) = codec
                    avroSchemaFields.add(new AvroSchema.Field(field.name, codec.avroSchema))
                    idx += 1
                  }
                  avroSchema.setFields(avroSchemaFields)
                }
              }
              new AvroBinaryCodec[A]() {
                private[this] val deconstructor = recordBinding.deconstructor
                private[this] val constructor   = recordBinding.constructor
                private[this] val usedRegisters = record.usedRegisters
                private[this] val fieldCodecs   = codecsWithAvroSchema._1

                val avroSchema: AvroSchema = codecsWithAvroSchema._2

                def decodeUnsafe(decoder: BinaryDecoder): A = {
                  val registers = Registers(usedRegisters)
                  var offset    = RegisterOffset.Zero
                  val len       = fieldCodecs.length
                  var idx       = 0
                  try {
                    while (idx < len) {
                      val codec = fieldCodecs(idx)
                      codec.valueType match {
                        case AvroBinaryCodec.objectType =>
                          registers
                            .setObject(offset, 0, codec.asInstanceOf[AvroBinaryCodec[AnyRef]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.intType =>
                          registers.setInt(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Int]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.longType =>
                          registers.setLong(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Long]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.floatType =>
                          registers
                            .setFloat(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Float]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.doubleType =>
                          registers
                            .setDouble(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Double]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.booleanType =>
                          registers
                            .setBoolean(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Boolean]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.byteType =>
                          registers.setByte(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Byte]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.charType =>
                          registers.setChar(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Char]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.shortType =>
                          registers
                            .setShort(offset, 0, codec.asInstanceOf[AvroBinaryCodec[Short]].decodeUnsafe(decoder))
                        case _ => codec.asInstanceOf[AvroBinaryCodec[Unit]].decodeUnsafe(decoder)
                      }
                      offset = RegisterOffset.add(offset, codec.valueOffset)
                      idx += 1
                    }
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                  }
                  constructor.construct(registers, RegisterOffset.Zero)
                }

                def encode(value: A, encoder: BinaryEncoder): Unit = {
                  val registers = Registers(usedRegisters)
                  var offset    = RegisterOffset.Zero
                  deconstructor.deconstruct(registers, offset, value)
                  val len = fieldCodecs.length
                  var idx = 0
                  while (idx < len) {
                    val codec = fieldCodecs(idx)
                    codec.valueType match {
                      case AvroBinaryCodec.objectType =>
                        codec.asInstanceOf[AvroBinaryCodec[AnyRef]].encode(registers.getObject(offset, 0), encoder)
                      case AvroBinaryCodec.intType =>
                        codec.asInstanceOf[AvroBinaryCodec[Int]].encode(registers.getInt(offset, 0), encoder)
                      case AvroBinaryCodec.longType =>
                        codec.asInstanceOf[AvroBinaryCodec[Long]].encode(registers.getLong(offset, 0), encoder)
                      case AvroBinaryCodec.floatType =>
                        codec.asInstanceOf[AvroBinaryCodec[Float]].encode(registers.getFloat(offset, 0), encoder)
                      case AvroBinaryCodec.doubleType =>
                        codec.asInstanceOf[AvroBinaryCodec[Double]].encode(registers.getDouble(offset, 0), encoder)
                      case AvroBinaryCodec.booleanType =>
                        codec.asInstanceOf[AvroBinaryCodec[Boolean]].encode(registers.getBoolean(offset, 0), encoder)
                      case AvroBinaryCodec.byteType =>
                        codec.asInstanceOf[AvroBinaryCodec[Byte]].encode(registers.getByte(offset, 0), encoder)
                      case AvroBinaryCodec.charType =>
                        codec.asInstanceOf[AvroBinaryCodec[Char]].encode(registers.getChar(offset, 0), encoder)
                      case AvroBinaryCodec.shortType =>
                        codec.asInstanceOf[AvroBinaryCodec[Short]].encode(registers.getShort(offset, 0), encoder)
                      case _ => codec.asInstanceOf[AvroBinaryCodec[Unit]].encode((), encoder)
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
              val codec          = deriveCodec(wrapper.wrapped).asInstanceOf[AvroBinaryCodec[Wrapped]]
              new AvroBinaryCodec[A]() {
                private[this] val unwrap       = wrapperBinding.unwrap
                private[this] val wrap         = wrapperBinding.wrap
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

        private[this] def getName(typeName: TypeName[?]): String = {
          val name = typeName.name
          if (typeName.params.isEmpty) name
          else s"${name}_${typeName.params.hashCode.toHexString}"
        }

        private[this] def getNamespace(typeName: TypeName[?]): String = typeName.namespace.elements.mkString(".")

        private[this] val dynamicValueCodec = new AvroBinaryCodec[DynamicValue]() {
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

          val avroSchema: AvroSchema = {
            val dynamicValue       = AvroSchema.createRecord("DynamicValue", null, "zio.blocks.schema", false)
            val dynamicValueFields = new java.util.ArrayList[AvroSchema.Field]
            dynamicValueFields.add(
              new AvroSchema.Field(
                "value",
                AvroSchema.createUnion(
                  primitiveDynamicValueCodec.avroSchema, {
                    val record       = AvroSchema.createRecord("Record", null, "zio.blocks.schema.DynamicValue", false)
                    val recordFields = new java.util.ArrayList[AvroSchema.Field]
                    recordFields.add(
                      new AvroSchema.Field(
                        "fields",
                        AvroSchema.createArray {
                          val field       = AvroSchema.createRecord("Field", null, "zio.blocks.schema.internal", false)
                          val fieldFields = new java.util.ArrayList[AvroSchema.Field]
                          fieldFields.add(new AvroSchema.Field("name", AvroSchema.create(AvroSchema.Type.STRING)))
                          fieldFields.add(new AvroSchema.Field("value", dynamicValue))
                          field.setFields(fieldFields)
                          field
                        }
                      )
                    )
                    record.setFields(recordFields)
                    record
                  }, {
                    val variant       = AvroSchema.createRecord("Variant", null, "zio.blocks.schema.DynamicValue", false)
                    val variantFields = new java.util.ArrayList[AvroSchema.Field]
                    variantFields.add(new AvroSchema.Field("caseName", AvroSchema.create(AvroSchema.Type.STRING)))
                    variantFields.add(new AvroSchema.Field("value", dynamicValue))
                    variant.setFields(variantFields)
                    variant
                  }, {
                    val sequence       = AvroSchema.createRecord("Sequence", null, "zio.blocks.schema.DynamicValue", false)
                    val sequenceFields = new java.util.ArrayList[AvroSchema.Field]
                    sequenceFields.add(new AvroSchema.Field("elements", AvroSchema.createArray(dynamicValue)))
                    sequence.setFields(sequenceFields)
                    sequence
                  }, {
                    val map       = AvroSchema.createRecord("Map", null, "zio.blocks.schema.DynamicValue", false)
                    val mapFields = new java.util.ArrayList[AvroSchema.Field]
                    mapFields.add(
                      new AvroSchema.Field(
                        "entries",
                        AvroSchema.createArray {
                          val entry       = AvroSchema.createRecord("Entry", null, "zio.blocks.schema.internal", false)
                          val entryFields = new java.util.ArrayList[AvroSchema.Field]
                          entryFields.add(new AvroSchema.Field("key", dynamicValue))
                          entryFields.add(new AvroSchema.Field("value", dynamicValue))
                          entry.setFields(entryFields)
                          entry
                        }
                      )
                    )
                    map.setFields(mapFields)
                    map
                  }
                )
              )
            )
            dynamicValue.setFields(dynamicValueFields)
            dynamicValue
          }

          def decodeUnsafe(decoder: BinaryDecoder): DynamicValue = {
            val idx = decoder.readInt()
            idx match {
              case 0 =>
                try primitiveDynamicValueCodec.decodeUnsafe(decoder)
                catch {
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
                try {
                  val caseName =
                    try decoder.readString()
                    catch {
                      case error if NonFatal(error) => decodeError(spanCaseName, error)
                    }
                  val value =
                    try decodeUnsafe(decoder)
                    catch {
                      case error if NonFatal(error) => decodeError(spanValue, error)
                    }
                  new DynamicValue.Variant(caseName, value)
                } catch {
                  case error if NonFatal(error) => decodeError(spanVariant, error)
                }
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
          }

          def encode(value: DynamicValue, encoder: BinaryEncoder): Unit = value match {
            case primitive: DynamicValue.Primitive =>
              encoder.writeInt(0)
              primitiveDynamicValueCodec.encode(primitive, encoder)
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
        }
      }
    )
