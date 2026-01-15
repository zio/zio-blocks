package zio.blocks.schema.bson

import org.bson._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.math.{BigInteger, MathContext}
import scala.util.control.NonFatal

object BsonFormat
    extends BinaryFormat(
      "application/bson",
      new Deriver[BsonBinaryCodec] {
        override def derivePrimitive[F[_, _], A](
          primitiveType: PrimitiveType[A],
          typeName: TypeName[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        ): Lazy[BsonBinaryCodec[A]] =
          Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonBinaryCodec[C[A]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonBinaryCodec[M[K, V]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonBinaryCodec[DynamicValue]] =
          Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeName: TypeName[A],
          wrapperPrimitiveType: Option[PrimitiveType[A]],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonBinaryCodec[A]] = Lazy {
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
          new ThreadLocal[java.util.HashMap[TypeName[?], Array[BsonBinaryCodec[?]]]] {
            override def initialValue: java.util.HashMap[TypeName[?], Array[BsonBinaryCodec[?]]] =
              new java.util.HashMap
          }
        private[this] val recordCounters =
          new ThreadLocal[java.util.HashMap[(String, String), Int]] {
            override def initialValue: java.util.HashMap[(String, String), Int] = new java.util.HashMap
          }

        private[this] val unitCodec = new BsonBinaryCodec[Unit](BsonBinaryCodec.unitType) {
          def decodeUnsafe(reader: BsonBinaryReader): Unit =
            reader.readNull()

          def encodeUnsafe(value: Unit, writer: BsonBinaryWriter): Unit =
            writer.writeNull()
        }

        private[this] val booleanCodec = new BsonBinaryCodec[Boolean](BsonBinaryCodec.booleanType) {
          def decodeUnsafe(reader: BsonBinaryReader): Boolean = reader.readBoolean()

          def encodeUnsafe(value: Boolean, writer: BsonBinaryWriter): Unit = writer.writeBoolean(value)
        }

        private[this] val byteCodec = new BsonBinaryCodec[Byte](BsonBinaryCodec.byteType) {
          def decodeUnsafe(reader: BsonBinaryReader): Byte = {
            val x = reader.readInt32()
            if (x >= Byte.MinValue && x <= Byte.MaxValue) x.toByte
            else decodeError("Expected Byte")
          }

          def encodeUnsafe(value: Byte, writer: BsonBinaryWriter): Unit = writer.writeInt32(value.toInt)
        }

        private[this] val shortCodec = new BsonBinaryCodec[Short](BsonBinaryCodec.shortType) {
          def decodeUnsafe(reader: BsonBinaryReader): Short = {
            val x = reader.readInt32()
            if (x >= Short.MinValue && x <= Short.MaxValue) x.toShort
            else decodeError("Expected Short")
          }

          def encodeUnsafe(value: Short, writer: BsonBinaryWriter): Unit = writer.writeInt32(value.toInt)
        }

        private[this] val intCodec = new BsonBinaryCodec[Int](BsonBinaryCodec.intType) {
          def decodeUnsafe(reader: BsonBinaryReader): Int = reader.readInt32()

          def encodeUnsafe(value: Int, writer: BsonBinaryWriter): Unit = writer.writeInt32(value)
        }

        private[this] val longCodec = new BsonBinaryCodec[Long](BsonBinaryCodec.longType) {
          def decodeUnsafe(reader: BsonBinaryReader): Long = reader.readInt64()

          def encodeUnsafe(value: Long, writer: BsonBinaryWriter): Unit = writer.writeInt64(value)
        }

        private[this] val floatCodec = new BsonBinaryCodec[Float](BsonBinaryCodec.floatType) {
          def decodeUnsafe(reader: BsonBinaryReader): Float = reader.readDouble().toFloat

          def encodeUnsafe(value: Float, writer: BsonBinaryWriter): Unit = writer.writeDouble(value.toDouble)
        }

        private[this] val doubleCodec = new BsonBinaryCodec[Double](BsonBinaryCodec.doubleType) {
          def decodeUnsafe(reader: BsonBinaryReader): Double = reader.readDouble()

          def encodeUnsafe(value: Double, writer: BsonBinaryWriter): Unit = writer.writeDouble(value)
        }

        private[this] val charCodec = new BsonBinaryCodec[Char](BsonBinaryCodec.charType) {
          def decodeUnsafe(reader: BsonBinaryReader): Char = {
            val x = reader.readInt32()
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError("Expected Char")
          }

          def encodeUnsafe(value: Char, writer: BsonBinaryWriter): Unit = writer.writeInt32(value.toInt)
        }

        private[this] val stringCodec = new BsonBinaryCodec[String]() {
          def decodeUnsafe(reader: BsonBinaryReader): String = reader.readString()

          def encodeUnsafe(value: String, writer: BsonBinaryWriter): Unit = writer.writeString(value)
        }

        private[this] val bigIntCodec = new BsonBinaryCodec[BigInt]() {
          def decodeUnsafe(reader: BsonBinaryReader): BigInt = {
            val binary = reader.readBinaryData()
            BigInt(binary.getData)
          }

          def encodeUnsafe(value: BigInt, writer: BsonBinaryWriter): Unit =
            writer.writeBinaryData(new BsonBinary(value.toByteArray))
        }

        private[this] val bigDecimalCodec = new BsonBinaryCodec[BigDecimal]() {
          def decodeUnsafe(reader: BsonBinaryReader): BigDecimal = {
            reader.readStartDocument()
            reader.readName("mantissa")
            val mantissa = reader.readBinaryData().getData
            reader.readName("scale")
            val scale = reader.readInt32()
            reader.readName("precision")
            val precision = reader.readInt32()
            reader.readName("roundingMode")
            val roundingMode = java.math.RoundingMode.valueOf(reader.readInt32())
            reader.readEndDocument()
            val mc = new MathContext(precision, roundingMode)
            new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
          }

          def encodeUnsafe(value: BigDecimal, writer: BsonBinaryWriter): Unit = {
            val bd = value.underlying
            val mc = value.mc
            writer.writeStartDocument()
            writer.writeName("mantissa")
            writer.writeBinaryData(new BsonBinary(bd.unscaledValue.toByteArray))
            writer.writeName("scale")
            writer.writeInt32(bd.scale)
            writer.writeName("precision")
            writer.writeInt32(mc.getPrecision)
            writer.writeName("roundingMode")
            writer.writeInt32(mc.getRoundingMode.ordinal)
            writer.writeEndDocument()
          }
        }

        private[this] val dayOfWeekCodec = new BsonBinaryCodec[java.time.DayOfWeek]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.DayOfWeek = java.time.DayOfWeek.of(reader.readInt32())

          def encodeUnsafe(value: java.time.DayOfWeek, writer: BsonBinaryWriter): Unit =
            writer.writeInt32(value.getValue)
        }

        private[this] val durationCodec = new BsonBinaryCodec[java.time.Duration]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.Duration = {
            reader.readStartDocument()
            reader.readName("seconds")
            val seconds = reader.readInt64()
            reader.readName("nanos")
            val nanos = reader.readInt32()
            reader.readEndDocument()
            java.time.Duration.ofSeconds(seconds, nanos)
          }

          def encodeUnsafe(value: java.time.Duration, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("seconds")
            writer.writeInt64(value.getSeconds)
            writer.writeName("nanos")
            writer.writeInt32(value.getNano)
            writer.writeEndDocument()
          }
        }

        private[this] val instantCodec = new BsonBinaryCodec[java.time.Instant]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.Instant = {
            reader.readStartDocument()
            reader.readName("epochSecond")
            val epochSecond = reader.readInt64()
            reader.readName("nano")
            val nano = reader.readInt32()
            reader.readEndDocument()
            java.time.Instant.ofEpochSecond(epochSecond, nano)
          }

          def encodeUnsafe(value: java.time.Instant, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("epochSecond")
            writer.writeInt64(value.getEpochSecond)
            writer.writeName("nano")
            writer.writeInt32(value.getNano)
            writer.writeEndDocument()
          }
        }

        private[this] val localDateCodec = new BsonBinaryCodec[java.time.LocalDate]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.LocalDate = {
            reader.readStartDocument()
            reader.readName("year")
            val year = reader.readInt32()
            reader.readName("month")
            val month = reader.readInt32()
            reader.readName("day")
            val day = reader.readInt32()
            reader.readEndDocument()
            java.time.LocalDate.of(year, month, day)
          }

          def encodeUnsafe(value: java.time.LocalDate, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("year")
            writer.writeInt32(value.getYear)
            writer.writeName("month")
            writer.writeInt32(value.getMonthValue)
            writer.writeName("day")
            writer.writeInt32(value.getDayOfMonth)
            writer.writeEndDocument()
          }
        }

        private[this] val localDateTimeCodec = new BsonBinaryCodec[java.time.LocalDateTime]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.LocalDateTime = {
            reader.readStartDocument()
            reader.readName("year")
            val year = reader.readInt32()
            reader.readName("month")
            val month = reader.readInt32()
            reader.readName("day")
            val day = reader.readInt32()
            reader.readName("hour")
            val hour = reader.readInt32()
            reader.readName("minute")
            val minute = reader.readInt32()
            reader.readName("second")
            val second = reader.readInt32()
            reader.readName("nano")
            val nano = reader.readInt32()
            reader.readEndDocument()
            java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano)
          }

          def encodeUnsafe(value: java.time.LocalDateTime, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("year")
            writer.writeInt32(value.getYear)
            writer.writeName("month")
            writer.writeInt32(value.getMonthValue)
            writer.writeName("day")
            writer.writeInt32(value.getDayOfMonth)
            writer.writeName("hour")
            writer.writeInt32(value.getHour)
            writer.writeName("minute")
            writer.writeInt32(value.getMinute)
            writer.writeName("second")
            writer.writeInt32(value.getSecond)
            writer.writeName("nano")
            writer.writeInt32(value.getNano)
            writer.writeEndDocument()
          }
        }

        private[this] val localTimeCodec = new BsonBinaryCodec[java.time.LocalTime]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.LocalTime = {
            reader.readStartDocument()
            reader.readName("hour")
            val hour = reader.readInt32()
            reader.readName("minute")
            val minute = reader.readInt32()
            reader.readName("second")
            val second = reader.readInt32()
            reader.readName("nano")
            val nano = reader.readInt32()
            reader.readEndDocument()
            java.time.LocalTime.of(hour, minute, second, nano)
          }

          def encodeUnsafe(value: java.time.LocalTime, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("hour")
            writer.writeInt32(value.getHour)
            writer.writeName("minute")
            writer.writeInt32(value.getMinute)
            writer.writeName("second")
            writer.writeInt32(value.getSecond)
            writer.writeName("nano")
            writer.writeInt32(value.getNano)
            writer.writeEndDocument()
          }
        }

        private[this] val monthCodec = new BsonBinaryCodec[java.time.Month]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.Month = java.time.Month.of(reader.readInt32())

          def encodeUnsafe(value: java.time.Month, writer: BsonBinaryWriter): Unit = writer.writeInt32(value.getValue)
        }

        private[this] val monthDayCodec = new BsonBinaryCodec[java.time.MonthDay]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.MonthDay = {
            reader.readStartDocument()
            reader.readName("month")
            val month = reader.readInt32()
            reader.readName("day")
            val day = reader.readInt32()
            reader.readEndDocument()
            java.time.MonthDay.of(month, day)
          }

          def encodeUnsafe(value: java.time.MonthDay, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("month")
            writer.writeInt32(value.getMonthValue)
            writer.writeName("day")
            writer.writeInt32(value.getDayOfMonth)
            writer.writeEndDocument()
          }
        }

        private[this] val offsetDateTimeCodec = new BsonBinaryCodec[java.time.OffsetDateTime]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.OffsetDateTime = {
            reader.readStartDocument()
            reader.readName("year")
            val year = reader.readInt32()
            reader.readName("month")
            val month = reader.readInt32()
            reader.readName("day")
            val day = reader.readInt32()
            reader.readName("hour")
            val hour = reader.readInt32()
            reader.readName("minute")
            val minute = reader.readInt32()
            reader.readName("second")
            val second = reader.readInt32()
            reader.readName("nano")
            val nano = reader.readInt32()
            reader.readName("offsetSecond")
            val offsetSecond = reader.readInt32()
            reader.readEndDocument()
            java.time.OffsetDateTime
              .of(year, month, day, hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }

          def encodeUnsafe(value: java.time.OffsetDateTime, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("year")
            writer.writeInt32(value.getYear)
            writer.writeName("month")
            writer.writeInt32(value.getMonthValue)
            writer.writeName("day")
            writer.writeInt32(value.getDayOfMonth)
            writer.writeName("hour")
            writer.writeInt32(value.getHour)
            writer.writeName("minute")
            writer.writeInt32(value.getMinute)
            writer.writeName("second")
            writer.writeInt32(value.getSecond)
            writer.writeName("nano")
            writer.writeInt32(value.getNano)
            writer.writeName("offsetSecond")
            writer.writeInt32(value.getOffset.getTotalSeconds)
            writer.writeEndDocument()
          }
        }

        private[this] val offsetTimeCodec = new BsonBinaryCodec[java.time.OffsetTime]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.OffsetTime = {
            reader.readStartDocument()
            reader.readName("hour")
            val hour = reader.readInt32()
            reader.readName("minute")
            val minute = reader.readInt32()
            reader.readName("second")
            val second = reader.readInt32()
            reader.readName("nano")
            val nano = reader.readInt32()
            reader.readName("offsetSecond")
            val offsetSecond = reader.readInt32()
            reader.readEndDocument()
            java.time.OffsetTime.of(hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }

          def encodeUnsafe(value: java.time.OffsetTime, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("hour")
            writer.writeInt32(value.getHour)
            writer.writeName("minute")
            writer.writeInt32(value.getMinute)
            writer.writeName("second")
            writer.writeInt32(value.getSecond)
            writer.writeName("nano")
            writer.writeInt32(value.getNano)
            writer.writeName("offsetSecond")
            writer.writeInt32(value.getOffset.getTotalSeconds)
            writer.writeEndDocument()
          }
        }

        private[this] val periodCodec = new BsonBinaryCodec[java.time.Period]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.Period = {
            reader.readStartDocument()
            reader.readName("years")
            val years = reader.readInt32()
            reader.readName("months")
            val months = reader.readInt32()
            reader.readName("days")
            val days = reader.readInt32()
            reader.readEndDocument()
            java.time.Period.of(years, months, days)
          }

          def encodeUnsafe(value: java.time.Period, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("years")
            writer.writeInt32(value.getYears)
            writer.writeName("months")
            writer.writeInt32(value.getMonths)
            writer.writeName("days")
            writer.writeInt32(value.getDays)
            writer.writeEndDocument()
          }
        }

        private[this] val yearCodec = new BsonBinaryCodec[java.time.Year]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.Year = java.time.Year.of(reader.readInt32())

          def encodeUnsafe(value: java.time.Year, writer: BsonBinaryWriter): Unit = writer.writeInt32(value.getValue)
        }

        private[this] val yearMonthCodec = new BsonBinaryCodec[java.time.YearMonth]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.YearMonth = {
            reader.readStartDocument()
            reader.readName("year")
            val year = reader.readInt32()
            reader.readName("month")
            val month = reader.readInt32()
            reader.readEndDocument()
            java.time.YearMonth.of(year, month)
          }

          def encodeUnsafe(value: java.time.YearMonth, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("year")
            writer.writeInt32(value.getYear)
            writer.writeName("month")
            writer.writeInt32(value.getMonthValue)
            writer.writeEndDocument()
          }
        }

        private[this] val zoneIdCodec = new BsonBinaryCodec[java.time.ZoneId]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.ZoneId = java.time.ZoneId.of(reader.readString())

          def encodeUnsafe(value: java.time.ZoneId, writer: BsonBinaryWriter): Unit = writer.writeString(value.toString)
        }

        private[this] val zoneOffsetCodec = new BsonBinaryCodec[java.time.ZoneOffset]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(reader.readInt32())

          def encodeUnsafe(value: java.time.ZoneOffset, writer: BsonBinaryWriter): Unit =
            writer.writeInt32(value.getTotalSeconds)
        }

        private[this] val zonedDateTimeCodec = new BsonBinaryCodec[java.time.ZonedDateTime]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.time.ZonedDateTime = {
            reader.readStartDocument()
            reader.readName("year")
            val year = reader.readInt32()
            reader.readName("month")
            val month = reader.readInt32()
            reader.readName("day")
            val day = reader.readInt32()
            reader.readName("hour")
            val hour = reader.readInt32()
            reader.readName("minute")
            val minute = reader.readInt32()
            reader.readName("second")
            val second = reader.readInt32()
            reader.readName("nano")
            val nano = reader.readInt32()
            reader.readName("offsetSecond")
            val offsetSecond = reader.readInt32()
            reader.readName("zoneId")
            val zoneId = reader.readString()
            reader.readEndDocument()
            java.time.ZonedDateTime.ofInstant(
              java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano),
              java.time.ZoneOffset.ofTotalSeconds(offsetSecond),
              java.time.ZoneId.of(zoneId)
            )
          }

          def encodeUnsafe(value: java.time.ZonedDateTime, writer: BsonBinaryWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("year")
            writer.writeInt32(value.getYear)
            writer.writeName("month")
            writer.writeInt32(value.getMonthValue)
            writer.writeName("day")
            writer.writeInt32(value.getDayOfMonth)
            writer.writeName("hour")
            writer.writeInt32(value.getHour)
            writer.writeName("minute")
            writer.writeInt32(value.getMinute)
            writer.writeName("second")
            writer.writeInt32(value.getSecond)
            writer.writeName("nano")
            writer.writeInt32(value.getNano)
            writer.writeName("offsetSecond")
            writer.writeInt32(value.getOffset.getTotalSeconds)
            writer.writeName("zoneId")
            writer.writeString(value.getZone.toString)
            writer.writeEndDocument()
          }
        }

        private[this] val currencyCodec = new BsonBinaryCodec[java.util.Currency]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.util.Currency =
            java.util.Currency.getInstance(reader.readString())

          def encodeUnsafe(value: java.util.Currency, writer: BsonBinaryWriter): Unit =
            writer.writeString(value.getCurrencyCode)
        }

        private[this] val uuidCodec = new BsonBinaryCodec[java.util.UUID]() {
          def decodeUnsafe(reader: BsonBinaryReader): java.util.UUID = {
            val binary = reader.readBinaryData()
            val bs     = binary.getData
            val hi     =
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

          def encodeUnsafe(value: java.util.UUID, writer: BsonBinaryWriter): Unit = {
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
            writer.writeBinaryData(new BsonBinary(BsonBinarySubType.UUID_STANDARD, bs))
          }
        }

        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): BsonBinaryCodec[A] = {
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
              val codecs  = new Array[BsonBinaryCodec[?]](len)
              val names   = new Array[String](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                names(idx) = cases(idx).name
                idx += 1
              }
              new BsonBinaryCodec[A]() {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs
                private[this] val caseNames     = names

                def decodeUnsafe(reader: BsonBinaryReader): A = {
                  reader.readStartDocument()
                  reader.readName("_type")
                  val typeName = reader.readString()
                  var idx      = 0
                  while (idx < caseNames.length && caseNames(idx) != typeName) idx += 1
                  if (idx >= caseNames.length) decodeError(s"Unknown variant type: $typeName")
                  reader.readName("value")
                  val result =
                    try caseCodecs(idx).asInstanceOf[BsonBinaryCodec[A]].decodeUnsafe(reader)
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(caseNames(idx)), error)
                    }
                  reader.readEndDocument()
                  result
                }

                def encodeUnsafe(value: A, writer: BsonBinaryWriter): Unit = {
                  val idx = discriminator.discriminate(value)
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString(caseNames(idx))
                  writer.writeName("value")
                  caseCodecs(idx).asInstanceOf[BsonBinaryCodec[A]].encodeUnsafe(value, writer)
                  writer.writeEndDocument()
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec   = deriveCodec(sequence.element).asInstanceOf[BsonBinaryCodec[Elem]]
              new BsonBinaryCodec[Col[Elem]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val elementCodec  = codec

                def decodeUnsafe(reader: BsonBinaryReader): Col[Elem] = {
                  val builder = constructor.newObjectBuilder[Elem](8)
                  reader.readStartArray()
                  var count = 0
                  while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                    try {
                      constructor.addObject(builder, elementCodec.decodeUnsafe(reader))
                      count += 1
                    } catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(count), error)
                    }
                  }
                  reader.readEndArray()
                  constructor.resultObject[Elem](builder)
                }

                def encodeUnsafe(value: Col[Elem], writer: BsonBinaryWriter): Unit = {
                  writer.writeStartArray()
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) elementCodec.encodeUnsafe(it.next(), writer)
                  writer.writeEndArray()
                }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding     = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val codec1      = deriveCodec(map.key).asInstanceOf[BsonBinaryCodec[Key]]
              val codec2      = deriveCodec(map.value).asInstanceOf[BsonBinaryCodec[Value]]
              val isStringKey = map.key.asPrimitive match {
                case Some(primitiveKey) => primitiveKey.primitiveType.isInstanceOf[PrimitiveType.String]
                case _                  => false
              }
              if (isStringKey) {
                new BsonBinaryCodec[Map[Key, Value]]() {
                  private[this] val deconstructor = binding.deconstructor
                  private[this] val constructor   = binding.constructor
                  private[this] val valueCodec    = codec2
                  private[this] val keyReflect    = map.key.asInstanceOf[Reflect.Bound[Key]]

                  def decodeUnsafe(reader: BsonBinaryReader): Map[Key, Value] = {
                    val builder = constructor.newObjectBuilder[Key, Value](8)
                    reader.readStartDocument()
                    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                      val k = reader.readName().asInstanceOf[Key]
                      val v =
                        try valueCodec.decodeUnsafe(reader)
                        catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), error)
                        }
                      constructor.addObject(builder, k, v)
                    }
                    reader.readEndDocument()
                    constructor.resultObject[Key, Value](builder)
                  }

                  def encodeUnsafe(value: Map[Key, Value], writer: BsonBinaryWriter): Unit = {
                    writer.writeStartDocument()
                    val it = deconstructor.deconstruct(value)
                    while (it.hasNext) {
                      val kv = it.next()
                      writer.writeName(deconstructor.getKey(kv).asInstanceOf[String])
                      valueCodec.encodeUnsafe(deconstructor.getValue(kv), writer)
                    }
                    writer.writeEndDocument()
                  }
                }
              } else {
                new BsonBinaryCodec[Map[Key, Value]]() {
                  private[this] val deconstructor = binding.deconstructor
                  private[this] val constructor   = binding.constructor
                  private[this] val keyCodec      = codec1
                  private[this] val valueCodec    = codec2
                  private[this] val keyReflect    = map.key.asInstanceOf[Reflect.Bound[Key]]

                  def decodeUnsafe(reader: BsonBinaryReader): Map[Key, Value] = {
                    val builder = constructor.newObjectBuilder[Key, Value](8)
                    reader.readStartArray()
                    var count = 0
                    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                      reader.readStartDocument()
                      reader.readName("_1")
                      val k =
                        try keyCodec.decodeUnsafe(reader)
                        catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count), error)
                        }
                      reader.readName("_2")
                      val v =
                        try valueCodec.decodeUnsafe(reader)
                        catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), error)
                        }
                      reader.readEndDocument()
                      constructor.addObject(builder, k, v)
                      count += 1
                    }
                    reader.readEndArray()
                    constructor.resultObject[Key, Value](builder)
                  }

                  def encodeUnsafe(value: Map[Key, Value], writer: BsonBinaryWriter): Unit = {
                    writer.writeStartArray()
                    val it = deconstructor.deconstruct(value)
                    while (it.hasNext) {
                      val kv = it.next()
                      writer.writeStartDocument()
                      writer.writeName("_1")
                      keyCodec.encodeUnsafe(deconstructor.getKey(kv), writer)
                      writer.writeName("_2")
                      valueCodec.encodeUnsafe(deconstructor.getValue(kv), writer)
                      writer.writeEndDocument()
                    }
                    writer.writeEndArray()
                  }
                }
              }
            } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val binding     = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields      = record.fields
              val isRecursive = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
              val typeName    = record.typeName
              var codecs      = if (isRecursive) recursiveRecordCache.get.get(typeName) else null
              var offset      = 0L
              if (codecs eq null) {
                val len = fields.length
                codecs = new Array[BsonBinaryCodec[?]](len)
                if (isRecursive) recursiveRecordCache.get.put(typeName, codecs)
                var idx = 0
                while (idx < len) {
                  val field = fields(idx)
                  val codec = deriveCodec(field.value)
                  codecs(idx) = codec
                  offset = RegisterOffset.add(codec.valueOffset, offset)
                  idx += 1
                }
              }
              new BsonBinaryCodec[A]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val usedRegisters = offset
                private[this] val fieldCodecs   = codecs

                def decodeUnsafe(reader: BsonBinaryReader): A = {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  val len    = fieldCodecs.length
                  var idx    = 0
                  reader.readStartDocument()
                  try {
                    while (idx < len) {
                      val codec     = fieldCodecs(idx)
                      val fieldName = fields(idx).name
                      reader.readName(fieldName)
                      codec.valueType match {
                        case BsonBinaryCodec.objectType =>
                          regs.setObject(offset, codec.asInstanceOf[BsonBinaryCodec[AnyRef]].decodeUnsafe(reader))
                        case BsonBinaryCodec.intType =>
                          regs.setInt(offset, codec.asInstanceOf[BsonBinaryCodec[Int]].decodeUnsafe(reader))
                        case BsonBinaryCodec.longType =>
                          regs.setLong(offset, codec.asInstanceOf[BsonBinaryCodec[Long]].decodeUnsafe(reader))
                        case BsonBinaryCodec.floatType =>
                          regs.setFloat(offset, codec.asInstanceOf[BsonBinaryCodec[Float]].decodeUnsafe(reader))
                        case BsonBinaryCodec.doubleType =>
                          regs.setDouble(offset, codec.asInstanceOf[BsonBinaryCodec[Double]].decodeUnsafe(reader))
                        case BsonBinaryCodec.booleanType =>
                          regs.setBoolean(offset, codec.asInstanceOf[BsonBinaryCodec[Boolean]].decodeUnsafe(reader))
                        case BsonBinaryCodec.byteType =>
                          regs.setByte(offset, codec.asInstanceOf[BsonBinaryCodec[Byte]].decodeUnsafe(reader))
                        case BsonBinaryCodec.charType =>
                          regs.setChar(offset, codec.asInstanceOf[BsonBinaryCodec[Char]].decodeUnsafe(reader))
                        case BsonBinaryCodec.shortType =>
                          regs.setShort(offset, codec.asInstanceOf[BsonBinaryCodec[Short]].decodeUnsafe(reader))
                        case _ => codec.asInstanceOf[BsonBinaryCodec[Unit]].decodeUnsafe(reader)
                      }
                      offset += codec.valueOffset
                      idx += 1
                    }
                    reader.readEndDocument()
                    constructor.construct(regs, 0)
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                  }
                }

                def encodeUnsafe(value: A, writer: BsonBinaryWriter): Unit = {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  deconstructor.deconstruct(regs, offset, value)
                  val len = fieldCodecs.length
                  var idx = 0
                  writer.writeStartDocument()
                  while (idx < len) {
                    val codec     = fieldCodecs(idx)
                    val fieldName = fields(idx).name
                    writer.writeName(fieldName)
                    codec.valueType match {
                      case BsonBinaryCodec.objectType =>
                        codec.asInstanceOf[BsonBinaryCodec[AnyRef]].encodeUnsafe(regs.getObject(offset), writer)
                      case BsonBinaryCodec.intType =>
                        codec.asInstanceOf[BsonBinaryCodec[Int]].encodeUnsafe(regs.getInt(offset), writer)
                      case BsonBinaryCodec.longType =>
                        codec.asInstanceOf[BsonBinaryCodec[Long]].encodeUnsafe(regs.getLong(offset), writer)
                      case BsonBinaryCodec.floatType =>
                        codec.asInstanceOf[BsonBinaryCodec[Float]].encodeUnsafe(regs.getFloat(offset), writer)
                      case BsonBinaryCodec.doubleType =>
                        codec.asInstanceOf[BsonBinaryCodec[Double]].encodeUnsafe(regs.getDouble(offset), writer)
                      case BsonBinaryCodec.booleanType =>
                        codec.asInstanceOf[BsonBinaryCodec[Boolean]].encodeUnsafe(regs.getBoolean(offset), writer)
                      case BsonBinaryCodec.byteType =>
                        codec.asInstanceOf[BsonBinaryCodec[Byte]].encodeUnsafe(regs.getByte(offset), writer)
                      case BsonBinaryCodec.charType =>
                        codec.asInstanceOf[BsonBinaryCodec[Char]].encodeUnsafe(regs.getChar(offset), writer)
                      case BsonBinaryCodec.shortType =>
                        codec.asInstanceOf[BsonBinaryCodec[Short]].encodeUnsafe(regs.getShort(offset), writer)
                      case _ => codec.asInstanceOf[BsonBinaryCodec[Unit]].encodeUnsafe((), writer)
                    }
                    offset += codec.valueOffset
                    idx += 1
                  }
                  writer.writeEndDocument()
                }
              }
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val codec   = deriveCodec(wrapper.wrapped).asInstanceOf[BsonBinaryCodec[Wrapped]]
              new BsonBinaryCodec[A](wrapper.wrapperPrimitiveType.fold(BsonBinaryCodec.objectType) {
                case _: PrimitiveType.Boolean   => BsonBinaryCodec.booleanType
                case _: PrimitiveType.Byte      => BsonBinaryCodec.byteType
                case _: PrimitiveType.Char      => BsonBinaryCodec.charType
                case _: PrimitiveType.Short     => BsonBinaryCodec.shortType
                case _: PrimitiveType.Float     => BsonBinaryCodec.floatType
                case _: PrimitiveType.Int       => BsonBinaryCodec.intType
                case _: PrimitiveType.Double    => BsonBinaryCodec.doubleType
                case _: PrimitiveType.Long      => BsonBinaryCodec.longType
                case _: PrimitiveType.Unit.type => BsonBinaryCodec.unitType
                case _                          => BsonBinaryCodec.objectType
              }) {
                private[this] val unwrap       = binding.unwrap
                private[this] val wrap         = binding.wrap
                private[this] val wrappedCodec = codec

                def decodeUnsafe(reader: BsonBinaryReader): A = {
                  val wrapped =
                    try wrappedCodec.decodeUnsafe(reader)
                    catch {
                      case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                    }
                  wrap(wrapped) match {
                    case Right(x)  => x
                    case Left(err) => decodeError(err)
                  }
                }

                def encodeUnsafe(value: A, writer: BsonBinaryWriter): Unit =
                  wrappedCodec.encodeUnsafe(unwrap(value), writer)
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
            else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          }
        }.asInstanceOf[BsonBinaryCodec[A]]

        private[this] val dynamicValueCodec = new BsonBinaryCodec[DynamicValue]() {
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

          def decodeUnsafe(reader: BsonBinaryReader): DynamicValue = {
            reader.readStartDocument()
            reader.readName("_type")
            val typeName = reader.readString()
            reader.readName("value")
            val result = typeName match {
              case "Primitive" =>
                try {
                  reader.readStartDocument()
                  reader.readName("_type")
                  val primitiveType  = reader.readString()
                  val primitiveValue = primitiveType match {
                    case "Unit" =>
                      reader.readEndDocument()
                      PrimitiveValue.Unit
                    case "Boolean" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Boolean(booleanCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Byte" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Byte(byteCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Short" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Short(shortCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Int" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Int(intCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Long" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Long(longCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Float" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Float(floatCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Double" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Double(doubleCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Char" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Char(charCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "String" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.String(stringCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "BigInt" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.BigInt(bigIntCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "BigDecimal" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.BigDecimal(bigDecimalCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "DayOfWeek" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.DayOfWeek(dayOfWeekCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Duration" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Duration(durationCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Instant" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Instant(instantCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "LocalDate" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.LocalDate(localDateCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "LocalDateTime" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.LocalDateTime(localDateTimeCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "LocalTime" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.LocalTime(localTimeCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Month" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Month(monthCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "MonthDay" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.MonthDay(monthDayCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "OffsetDateTime" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.OffsetDateTime(offsetDateTimeCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "OffsetTime" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.OffsetTime(offsetTimeCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Period" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Period(periodCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Year" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Year(yearCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "YearMonth" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.YearMonth(yearMonthCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "ZoneId" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.ZoneId(zoneIdCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "ZoneOffset" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.ZoneOffset(zoneOffsetCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "ZonedDateTime" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.ZonedDateTime(zonedDateTimeCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "Currency" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.Currency(currencyCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case "UUID" =>
                      reader.readName("value")
                      val v = new PrimitiveValue.UUID(uuidCodec.decodeUnsafe(reader))
                      reader.readEndDocument()
                      v
                    case other =>
                      decodeError(s"Unknown primitive type: $other")
                  }
                  new DynamicValue.Primitive(primitiveValue)
                } catch {
                  case error if NonFatal(error) => decodeError(spanPrimitive, error)
                }
              case "Record" =>
                try {
                  val builder = Vector.newBuilder[(String, DynamicValue)]
                  reader.readStartArray()
                  var count = 0
                  while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                    reader.readStartDocument()
                    reader.readName("name")
                    val k =
                      try reader.readString()
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count), span_1, error)
                      }
                    reader.readName("value")
                    val v =
                      try decodeUnsafe(reader)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count), span_2, error)
                      }
                    reader.readEndDocument()
                    builder.addOne((k, v))
                    count += 1
                  }
                  reader.readEndArray()
                  new DynamicValue.Record(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanRecord, spanFields, error)
                }
              case "Variant" =>
                reader.readStartDocument()
                reader.readName("caseName")
                val caseName =
                  try reader.readString()
                  catch {
                    case error if NonFatal(error) => decodeError(spanVariant, spanCaseName, error)
                  }
                reader.readName("value")
                val value =
                  try decodeUnsafe(reader)
                  catch {
                    case error if NonFatal(error) => decodeError(spanVariant, spanValue, error)
                  }
                reader.readEndDocument()
                new DynamicValue.Variant(caseName, value)
              case "Sequence" =>
                try {
                  val builder = Vector.newBuilder[DynamicValue]
                  reader.readStartArray()
                  var count = 0
                  while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                    try {
                      builder.addOne(decodeUnsafe(reader))
                      count += 1
                    } catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(count), error)
                    }
                  }
                  reader.readEndArray()
                  new DynamicValue.Sequence(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanSequence, spanElements, error)
                }
              case "Map" =>
                try {
                  val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
                  reader.readStartArray()
                  var count = 0
                  while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                    reader.readStartDocument()
                    reader.readName("key")
                    val k =
                      try decodeUnsafe(reader)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count), span_1, error)
                      }
                    reader.readName("value")
                    val v =
                      try decodeUnsafe(reader)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count), span_2, error)
                      }
                    reader.readEndDocument()
                    builder.addOne((k, v))
                    count += 1
                  }
                  reader.readEndArray()
                  new DynamicValue.Map(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanMap, spanEntries, error)
                }
              case other => decodeError(s"Unknown DynamicValue type: $other")
            }
            reader.readEndDocument()
            result
          }

          def encodeUnsafe(value: DynamicValue, writer: BsonBinaryWriter): Unit = value match {
            case primitive: DynamicValue.Primitive =>
              writer.writeStartDocument()
              writer.writeName("_type")
              writer.writeString("Primitive")
              writer.writeName("value")
              primitive.value match {
                case _: PrimitiveValue.Unit.type =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Unit")
                  writer.writeEndDocument()
                case v: PrimitiveValue.Boolean =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Boolean")
                  writer.writeName("value")
                  booleanCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Byte =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Byte")
                  writer.writeName("value")
                  byteCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Short =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Short")
                  writer.writeName("value")
                  shortCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Int =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Int")
                  writer.writeName("value")
                  intCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Long =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Long")
                  writer.writeName("value")
                  longCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Float =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Float")
                  writer.writeName("value")
                  floatCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Double =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Double")
                  writer.writeName("value")
                  doubleCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Char =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Char")
                  writer.writeName("value")
                  charCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.String =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("String")
                  writer.writeName("value")
                  stringCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.BigInt =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("BigInt")
                  writer.writeName("value")
                  bigIntCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.BigDecimal =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("BigDecimal")
                  writer.writeName("value")
                  bigDecimalCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.DayOfWeek =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("DayOfWeek")
                  writer.writeName("value")
                  dayOfWeekCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Duration =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Duration")
                  writer.writeName("value")
                  durationCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Instant =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Instant")
                  writer.writeName("value")
                  instantCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.LocalDate =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("LocalDate")
                  writer.writeName("value")
                  localDateCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.LocalDateTime =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("LocalDateTime")
                  writer.writeName("value")
                  localDateTimeCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.LocalTime =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("LocalTime")
                  writer.writeName("value")
                  localTimeCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Month =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Month")
                  writer.writeName("value")
                  monthCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.MonthDay =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("MonthDay")
                  writer.writeName("value")
                  monthDayCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.OffsetDateTime =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("OffsetDateTime")
                  writer.writeName("value")
                  offsetDateTimeCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.OffsetTime =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("OffsetTime")
                  writer.writeName("value")
                  offsetTimeCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Period =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Period")
                  writer.writeName("value")
                  periodCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Year =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Year")
                  writer.writeName("value")
                  yearCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.YearMonth =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("YearMonth")
                  writer.writeName("value")
                  yearMonthCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.ZoneId =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("ZoneId")
                  writer.writeName("value")
                  zoneIdCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.ZoneOffset =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("ZoneOffset")
                  writer.writeName("value")
                  zoneOffsetCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.ZonedDateTime =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("ZonedDateTime")
                  writer.writeName("value")
                  zonedDateTimeCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.Currency =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("Currency")
                  writer.writeName("value")
                  currencyCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
                case v: PrimitiveValue.UUID =>
                  writer.writeStartDocument()
                  writer.writeName("_type")
                  writer.writeString("UUID")
                  writer.writeName("value")
                  uuidCodec.encodeUnsafe(v.value, writer)
                  writer.writeEndDocument()
              }
              writer.writeEndDocument()
            case record: DynamicValue.Record =>
              writer.writeStartDocument()
              writer.writeName("_type")
              writer.writeString("Record")
              writer.writeName("value")
              writer.writeStartArray()
              val fields = record.fields
              val it     = fields.iterator
              while (it.hasNext) {
                val kv = it.next()
                writer.writeStartDocument()
                writer.writeName("name")
                writer.writeString(kv._1)
                writer.writeName("value")
                encodeUnsafe(kv._2, writer)
                writer.writeEndDocument()
              }
              writer.writeEndArray()
              writer.writeEndDocument()
            case variant: DynamicValue.Variant =>
              writer.writeStartDocument()
              writer.writeName("_type")
              writer.writeString("Variant")
              writer.writeName("value")
              writer.writeStartDocument()
              writer.writeName("caseName")
              writer.writeString(variant.caseName)
              writer.writeName("value")
              encodeUnsafe(variant.value, writer)
              writer.writeEndDocument()
              writer.writeEndDocument()
            case sequence: DynamicValue.Sequence =>
              writer.writeStartDocument()
              writer.writeName("_type")
              writer.writeString("Sequence")
              writer.writeName("value")
              writer.writeStartArray()
              val elements = sequence.elements
              val it       = elements.iterator
              while (it.hasNext) {
                encodeUnsafe(it.next(), writer)
              }
              writer.writeEndArray()
              writer.writeEndDocument()
            case map: DynamicValue.Map =>
              writer.writeStartDocument()
              writer.writeName("_type")
              writer.writeString("Map")
              writer.writeName("value")
              writer.writeStartArray()
              val entries = map.entries
              val it      = entries.iterator
              while (it.hasNext) {
                val kv = it.next()
                writer.writeStartDocument()
                writer.writeName("key")
                encodeUnsafe(kv._1, writer)
                writer.writeName("value")
                encodeUnsafe(kv._2, writer)
                writer.writeEndDocument()
              }
              writer.writeEndArray()
              writer.writeEndDocument()
          }
        }
      }
    )
