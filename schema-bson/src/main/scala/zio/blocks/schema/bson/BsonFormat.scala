package zio.blocks.schema.bson

import org.bson.{BsonReader, BsonWriter, BsonType, BsonDocument, BsonArray, BsonNull, BsonString, BsonInt32, BsonInt64, BsonDouble, BsonBoolean, BsonBinary, BsonDateTime}
import org.bson.types.{ObjectId, Decimal128}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema.binding.SeqDeconstructor._
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride, Lazy}
import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import scala.util.control.NonFatal

/**
 * BSON (Binary JSON) format implementation for ZIO Blocks.
 * 
 * BSON is a binary serialization format used by MongoDB and other systems.
 * This implementation provides full support for encoding and decoding Scala types
 * to/from BSON format.
 */
object BsonFormat
    extends BinaryFormat[BsonBinaryCodec](
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

        // Primitive type codecs
        private[this] val unitCodec = new BsonBinaryCodec[Unit](BsonBinaryCodec.unitType) {
          def decodeUnsafe(reader: BsonReader): Unit = {
            reader.readNull()
            ()
          }

          def encode(value: Unit, writer: BsonWriter): Unit = writer.writeNull()
        }

        private[this] val booleanCodec = new BsonBinaryCodec[Boolean](BsonBinaryCodec.booleanType) {
          def decodeUnsafe(reader: BsonReader): Boolean = reader.readBoolean()

          def encode(value: Boolean, writer: BsonWriter): Unit = writer.writeBoolean(value)
        }

        private[this] val byteCodec = new BsonBinaryCodec[Byte](BsonBinaryCodec.byteType) {
          def decodeUnsafe(reader: BsonReader): Byte = {
            val x = reader.readInt32()
            if (x >= Byte.MinValue && x <= Byte.MaxValue) x.toByte
            else decodeError("Expected Byte")
          }

          def encode(value: Byte, writer: BsonWriter): Unit = writer.writeInt32(value.toInt)
        }

        private[this] val shortCodec = new BsonBinaryCodec[Short](BsonBinaryCodec.shortType) {
          def decodeUnsafe(reader: BsonReader): Short = {
            val x = reader.readInt32()
            if (x >= Short.MinValue && x <= Short.MaxValue) x.toShort
            else decodeError("Expected Short")
          }

          def encode(value: Short, writer: BsonWriter): Unit = writer.writeInt32(value.toInt)
        }

        private[this] val intCodec = new BsonBinaryCodec[Int](BsonBinaryCodec.intType) {
          def decodeUnsafe(reader: BsonReader): Int = reader.readInt32()

          def encode(value: Int, writer: BsonWriter): Unit = writer.writeInt32(value)
        }

        private[this] val longCodec = new BsonBinaryCodec[Long](BsonBinaryCodec.longType) {
          def decodeUnsafe(reader: BsonReader): Long = reader.readInt64()

          def encode(value: Long, writer: BsonWriter): Unit = writer.writeInt64(value)
        }

        private[this] val floatCodec = new BsonBinaryCodec[Float](BsonBinaryCodec.floatType) {
          def decodeUnsafe(reader: BsonReader): Float = reader.readDouble().toFloat

          def encode(value: Float, writer: BsonWriter): Unit = writer.writeDouble(value.toDouble)
        }

        private[this] val doubleCodec = new BsonBinaryCodec[Double](BsonBinaryCodec.doubleType) {
          def decodeUnsafe(reader: BsonReader): Double = reader.readDouble()

          def encode(value: Double, writer: BsonWriter): Unit = writer.writeDouble(value)
        }

        private[this] val charCodec = new BsonBinaryCodec[Char](BsonBinaryCodec.charType) {
          def decodeUnsafe(reader: BsonReader): Char = {
            val x = reader.readInt32()
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError("Expected Char")
          }

          def encode(value: Char, writer: BsonWriter): Unit = writer.writeInt32(value.toInt)
        }

        private[this] val stringCodec = new BsonBinaryCodec[String]() {
          def decodeUnsafe(reader: BsonReader): String = reader.readString()

          def encode(value: String, writer: BsonWriter): Unit = writer.writeString(value)
        }

        private[this] val bigIntCodec = new BsonBinaryCodec[BigInt]() {
          def decodeUnsafe(reader: BsonReader): BigInt = {
            val binary = reader.readBinaryData()
            BigInt(binary.getData)
          }

          def encode(value: BigInt, writer: BsonWriter): Unit = {
            writer.writeBinaryData(new org.bson.BsonBinary(value.toByteArray))
          }
        }

        private[this] val bigDecimalCodec = new BsonBinaryCodec[BigDecimal]() {
          def decodeUnsafe(reader: BsonReader): BigDecimal = {
            val decimal128 = reader.readDecimal128()
            BigDecimal(decimal128.bigDecimalValue())
          }

          def encode(value: BigDecimal, writer: BsonWriter): Unit = {
            writer.writeDecimal128(new Decimal128(value.bigDecimal))
          }
        }

        // Java time types
        private[this] val dayOfWeekCodec = new BsonBinaryCodec[java.time.DayOfWeek]() {
          def decodeUnsafe(reader: BsonReader): java.time.DayOfWeek = 
            java.time.DayOfWeek.of(reader.readInt32())

          def encode(value: java.time.DayOfWeek, writer: BsonWriter): Unit = 
            writer.writeInt32(value.getValue)
        }

        private[this] val durationCodec = new BsonBinaryCodec[java.time.Duration]() {
          def decodeUnsafe(reader: BsonReader): java.time.Duration = {
            reader.readStartDocument()
            reader.readName("seconds")
            val seconds = reader.readInt64()
            reader.readName("nanos")
            val nanos = reader.readInt32()
            reader.readEndDocument()
            java.time.Duration.ofSeconds(seconds, nanos.toLong)
          }

          def encode(value: java.time.Duration, writer: BsonWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("seconds")
            writer.writeInt64(value.getSeconds)
            writer.writeName("nanos")
            writer.writeInt32(value.getNano)
            writer.writeEndDocument()
          }
        }

        private[this] val instantCodec = new BsonBinaryCodec[java.time.Instant]() {
          def decodeUnsafe(reader: BsonReader): java.time.Instant = {
            val millis = reader.readDateTime()
            java.time.Instant.ofEpochMilli(millis)
          }

          def encode(value: java.time.Instant, writer: BsonWriter): Unit = {
            writer.writeDateTime(value.toEpochMilli)
          }
        }

        private[this] val localDateCodec = new BsonBinaryCodec[java.time.LocalDate]() {
          def decodeUnsafe(reader: BsonReader): java.time.LocalDate = {
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

          def encode(value: java.time.LocalDate, writer: BsonWriter): Unit = {
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
          def decodeUnsafe(reader: BsonReader): java.time.LocalDateTime = {
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

          def encode(value: java.time.LocalDateTime, writer: BsonWriter): Unit = {
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
          def decodeUnsafe(reader: BsonReader): java.time.LocalTime = {
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

          def encode(value: java.time.LocalTime, writer: BsonWriter): Unit = {
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
          def decodeUnsafe(reader: BsonReader): java.time.Month = 
            java.time.Month.of(reader.readInt32())

          def encode(value: java.time.Month, writer: BsonWriter): Unit = 
            writer.writeInt32(value.getValue)
        }

        private[this] val monthDayCodec = new BsonBinaryCodec[java.time.MonthDay]() {
          def decodeUnsafe(reader: BsonReader): java.time.MonthDay = {
            reader.readStartDocument()
            reader.readName("month")
            val month = reader.readInt32()
            reader.readName("day")
            val day = reader.readInt32()
            reader.readEndDocument()
            java.time.MonthDay.of(month, day)
          }

          def encode(value: java.time.MonthDay, writer: BsonWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("month")
            writer.writeInt32(value.getMonthValue)
            writer.writeName("day")
            writer.writeInt32(value.getDayOfMonth)
            writer.writeEndDocument()
          }
        }

        private[this] val offsetDateTimeCodec = new BsonBinaryCodec[java.time.OffsetDateTime]() {
          def decodeUnsafe(reader: BsonReader): java.time.OffsetDateTime = {
            reader.readStartDocument()
            reader.readName("dateTime")
            val dateTime = localDateTimeCodec.decodeUnsafe(reader)
            reader.readName("offset")
            val offsetSeconds = reader.readInt32()
            reader.readEndDocument()
            java.time.OffsetDateTime.of(dateTime, java.time.ZoneOffset.ofTotalSeconds(offsetSeconds))
          }

          def encode(value: java.time.OffsetDateTime, writer: BsonWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("dateTime")
            localDateTimeCodec.encode(value.toLocalDateTime, writer)
            writer.writeName("offset")
            writer.writeInt32(value.getOffset.getTotalSeconds)
            writer.writeEndDocument()
          }
        }

        private[this] val offsetTimeCodec = new BsonBinaryCodec[java.time.OffsetTime]() {
          def decodeUnsafe(reader: BsonReader): java.time.OffsetTime = {
            reader.readStartDocument()
            reader.readName("time")
            val time = localTimeCodec.decodeUnsafe(reader)
            reader.readName("offset")
            val offsetSeconds = reader.readInt32()
            reader.readEndDocument()
            java.time.OffsetTime.of(time, java.time.ZoneOffset.ofTotalSeconds(offsetSeconds))
          }

          def encode(value: java.time.OffsetTime, writer: BsonWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("time")
            localTimeCodec.encode(value.toLocalTime, writer)
            writer.writeName("offset")
            writer.writeInt32(value.getOffset.getTotalSeconds)
            writer.writeEndDocument()
          }
        }

        private[this] val periodCodec = new BsonBinaryCodec[java.time.Period]() {
          def decodeUnsafe(reader: BsonReader): java.time.Period = {
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

          def encode(value: java.time.Period, writer: BsonWriter): Unit = {
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
          def decodeUnsafe(reader: BsonReader): java.time.Year = 
            java.time.Year.of(reader.readInt32())

          def encode(value: java.time.Year, writer: BsonWriter): Unit = 
            writer.writeInt32(value.getValue)
        }

        private[this] val yearMonthCodec = new BsonBinaryCodec[java.time.YearMonth]() {
          def decodeUnsafe(reader: BsonReader): java.time.YearMonth = {
            reader.readStartDocument()
            reader.readName("year")
            val year = reader.readInt32()
            reader.readName("month")
            val month = reader.readInt32()
            reader.readEndDocument()
            java.time.YearMonth.of(year, month)
          }

          def encode(value: java.time.YearMonth, writer: BsonWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("year")
            writer.writeInt32(value.getYear)
            writer.writeName("month")
            writer.writeInt32(value.getMonthValue)
            writer.writeEndDocument()
          }
        }

        private[this] val zonedDateTimeCodec = new BsonBinaryCodec[java.time.ZonedDateTime]() {
          def decodeUnsafe(reader: BsonReader): java.time.ZonedDateTime = {
            reader.readStartDocument()
            reader.readName("dateTime")
            val dateTime = localDateTimeCodec.decodeUnsafe(reader)
            reader.readName("zone")
            val zoneId = reader.readString()
            reader.readEndDocument()
            java.time.ZonedDateTime.of(dateTime, java.time.ZoneId.of(zoneId))
          }

          def encode(value: java.time.ZonedDateTime, writer: BsonWriter): Unit = {
            writer.writeStartDocument()
            writer.writeName("dateTime")
            localDateTimeCodec.encode(value.toLocalDateTime, writer)
            writer.writeName("zone")
            writer.writeString(value.getZone.getId)
            writer.writeEndDocument()
          }
        }

        private[this] val zoneIdCodec = new BsonBinaryCodec[java.time.ZoneId]() {
          def decodeUnsafe(reader: BsonReader): java.time.ZoneId = 
            java.time.ZoneId.of(reader.readString())

          def encode(value: java.time.ZoneId, writer: BsonWriter): Unit = 
            writer.writeString(value.getId)
        }

        private[this] val zoneOffsetCodec = new BsonBinaryCodec[java.time.ZoneOffset]() {
          def decodeUnsafe(reader: BsonReader): java.time.ZoneOffset = 
            java.time.ZoneOffset.ofTotalSeconds(reader.readInt32())

          def encode(value: java.time.ZoneOffset, writer: BsonWriter): Unit = 
            writer.writeInt32(value.getTotalSeconds)
        }

        private[this] val uuidCodec = new BsonBinaryCodec[java.util.UUID]() {
          def decodeUnsafe(reader: BsonReader): java.util.UUID = {
            val binary = reader.readBinaryData()
            val bb = ByteBuffer.wrap(binary.getData)
            new java.util.UUID(bb.getLong(), bb.getLong())
          }

          def encode(value: java.util.UUID, writer: BsonWriter): Unit = {
            val bb = ByteBuffer.allocate(16)
            bb.putLong(value.getMostSignificantBits)
            bb.putLong(value.getLeastSignificantBits)
            writer.writeBinaryData(new org.bson.BsonBinary(bb.array()))
          }
        }

        private[this] val currencyCodec = new BsonBinaryCodec[java.util.Currency]() {
          def decodeUnsafe(reader: BsonReader): java.util.Currency = 
            java.util.Currency.getInstance(reader.readString())

          def encode(value: java.util.Currency, writer: BsonWriter): Unit = 
            writer.writeString(value.getCurrencyCode)
        }

        // ObjectId codec for MongoDB
        private[this] val objectIdCodec = new BsonBinaryCodec[ObjectId]() {
          def decodeUnsafe(reader: BsonReader): ObjectId = reader.readObjectId()

          def encode(value: ObjectId, writer: BsonWriter): Unit = writer.writeObjectId(value)
        }

        // Derive codec implementation
        private[this] def deriveCodec[A](reflect: Reflect[Binding, A]): BsonBinaryCodec[A] = {
          reflect match {
            case r: Reflect.Primitive[Binding, A] => derivePrimitiveCodec(r)
            case r: Reflect.Record[Binding, A]    => deriveRecordCodec(r)
            case r: Reflect.Variant[Binding, A]   => deriveVariantCodec(r)
            case r: Reflect.Sequence[Binding, c, a] =>
              deriveSequenceCodec(r).asInstanceOf[BsonBinaryCodec[A]]
            case r: Reflect.Map[Binding, m, k, v] =>
              deriveMapCodec(r).asInstanceOf[BsonBinaryCodec[A]]
            case r: Reflect.Wrapper[Binding, A, b] =>
              deriveWrapperCodec(r)
            case r: Reflect.Dynamic[Binding] =>
              deriveDynamicCodec(r).asInstanceOf[BsonBinaryCodec[A]]
          }
        }

        private[this] def derivePrimitiveCodec[A](reflect: Reflect.Primitive[Binding, A]): BsonBinaryCodec[A] = {
          import PrimitiveType._
          (reflect.primitiveType: @unchecked) match {
            case UnitType       => unitCodec.asInstanceOf[BsonBinaryCodec[A]]
            case BoolType       => booleanCodec.asInstanceOf[BsonBinaryCodec[A]]
            case ByteType       => byteCodec.asInstanceOf[BsonBinaryCodec[A]]
            case ShortType      => shortCodec.asInstanceOf[BsonBinaryCodec[A]]
            case IntType        => intCodec.asInstanceOf[BsonBinaryCodec[A]]
            case LongType       => longCodec.asInstanceOf[BsonBinaryCodec[A]]
            case FloatType      => floatCodec.asInstanceOf[BsonBinaryCodec[A]]
            case DoubleType     => doubleCodec.asInstanceOf[BsonBinaryCodec[A]]
            case CharType       => charCodec.asInstanceOf[BsonBinaryCodec[A]]
            case StringType     => stringCodec.asInstanceOf[BsonBinaryCodec[A]]
            case BigIntType     => bigIntCodec.asInstanceOf[BsonBinaryCodec[A]]
            case BigDecimalType => bigDecimalCodec.asInstanceOf[BsonBinaryCodec[A]]
            case DayOfWeekType  => dayOfWeekCodec.asInstanceOf[BsonBinaryCodec[A]]
            case DurationType   => durationCodec.asInstanceOf[BsonBinaryCodec[A]]
            case InstantType    => instantCodec.asInstanceOf[BsonBinaryCodec[A]]
            case LocalDateType  => localDateCodec.asInstanceOf[BsonBinaryCodec[A]]
            case LocalDateTimeType => localDateTimeCodec.asInstanceOf[BsonBinaryCodec[A]]
            case LocalTimeType  => localTimeCodec.asInstanceOf[BsonBinaryCodec[A]]
            case MonthType      => monthCodec.asInstanceOf[BsonBinaryCodec[A]]
            case MonthDayType   => monthDayCodec.asInstanceOf[BsonBinaryCodec[A]]
            case OffsetDateTimeType => offsetDateTimeCodec.asInstanceOf[BsonBinaryCodec[A]]
            case OffsetTimeType => offsetTimeCodec.asInstanceOf[BsonBinaryCodec[A]]
            case PeriodType     => periodCodec.asInstanceOf[BsonBinaryCodec[A]]
            case YearType       => yearCodec.asInstanceOf[BsonBinaryCodec[A]]
            case YearMonthType  => yearMonthCodec.asInstanceOf[BsonBinaryCodec[A]]
            case ZonedDateTimeType => zonedDateTimeCodec.asInstanceOf[BsonBinaryCodec[A]]
            case ZoneIdType     => zoneIdCodec.asInstanceOf[BsonBinaryCodec[A]]
            case ZoneOffsetType => zoneOffsetCodec.asInstanceOf[BsonBinaryCodec[A]]
            case UUIDType       => uuidCodec.asInstanceOf[BsonBinaryCodec[A]]
            case CurrencyType   => currencyCodec.asInstanceOf[BsonBinaryCodec[A]]
          }
        }

        private[this] def deriveRecordCodec[A](reflect: Reflect.Record[Binding, A]): BsonBinaryCodec[A] = {
          val fields = reflect.fields
          val binding = reflect.binding
          val fieldCodecs = fields.map(field => deriveCodec(field.reflect))

          new BsonBinaryCodec[A]() {
            def decodeUnsafe(reader: BsonReader): A = {
              reader.readStartDocument()
              val registers = Registers.allocate(binding.offset)
              
              var i = 0
              while (i < fields.length) {
                val field = fields(i)
                val codec = fieldCodecs(i)
                reader.readName(field.name)
                val value = codec.decodeUnsafe(reader)
                registers.set(field.offset, value)
                i += 1
              }
              
              reader.readEndDocument()
              binding.construct(registers)
            }

            def encode(value: A, writer: BsonWriter): Unit = {
              writer.writeStartDocument()
              val registers = Registers.allocate(binding.offset)
              binding.deconstruct(value, registers)
              
              var i = 0
              while (i < fields.length) {
                val field = fields(i)
                val codec = fieldCodecs(i)
                writer.writeName(field.name)
                val fieldValue = registers.get[Any](field.offset)
                codec.asInstanceOf[BsonBinaryCodec[Any]].encode(fieldValue, writer)
                i += 1
              }
              
              writer.writeEndDocument()
            }
          }
        }

        private[this] def deriveVariantCodec[A](reflect: Reflect.Variant[Binding, A]): BsonBinaryCodec[A] = {
          val cases = reflect.cases
          val binding = reflect.binding
          val caseCodecs = cases.map(c => deriveCodec(c.reflect))

          new BsonBinaryCodec[A]() {
            def decodeUnsafe(reader: BsonReader): A = {
              reader.readStartDocument()
              reader.readName("$type")
              val typeName = reader.readString()
              
              val caseIndex = cases.indexWhere(_.name == typeName)
              if (caseIndex < 0) decodeError(s"Unknown variant case: $typeName")
              
              reader.readName("$value")
              val value = caseCodecs(caseIndex).decodeUnsafe(reader)
              reader.readEndDocument()
              
              val registers = Registers.allocate(binding.offset)
              registers.set(cases(caseIndex).offset, value)
              binding.construct(caseIndex, registers)
            }

            def encode(value: A, writer: BsonWriter): Unit = {
              val caseIndex = binding.ordinal(value)
              val caseReflect = cases(caseIndex)
              val codec = caseCodecs(caseIndex)
              
              writer.writeStartDocument()
              writer.writeName("$type")
              writer.writeString(caseReflect.name)
              writer.writeName("$value")
              
              val registers = Registers.allocate(binding.offset)
              binding.deconstruct(value, registers)
              val caseValue = registers.get[Any](caseReflect.offset)
              codec.asInstanceOf[BsonBinaryCodec[Any]].encode(caseValue, writer)
              
              writer.writeEndDocument()
            }
          }
        }

        private[this] def deriveSequenceCodec[C[_], A](
          reflect: Reflect.Sequence[Binding, C, A]
        ): BsonBinaryCodec[C[A]] = {
          val elementCodec = deriveCodec(reflect.element)
          val binding = reflect.binding

          new BsonBinaryCodec[C[A]]() {
            def decodeUnsafe(reader: BsonReader): C[A] = {
              reader.readStartArray()
              val builder = binding.newBuilder
              
              while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                val elem = elementCodec.decodeUnsafe(reader)
                binding.addOne(builder, elem)
              }
              
              reader.readEndArray()
              binding.result(builder)
            }

            def encode(value: C[A], writer: BsonWriter): Unit = {
              writer.writeStartArray()
              val iterator = binding.iterator(value)
              
              while (iterator.hasNext) {
                elementCodec.encode(iterator.next(), writer)
              }
              
              writer.writeEndArray()
            }
          }
        }

        private[this] def deriveMapCodec[M[_, _], K, V](
          reflect: Reflect.Map[Binding, M, K, V]
        ): BsonBinaryCodec[M[K, V]] = {
          val keyCodec = deriveCodec(reflect.key)
          val valueCodec = deriveCodec(reflect.value)
          val binding = reflect.binding

          new BsonBinaryCodec[M[K, V]]() {
            def decodeUnsafe(reader: BsonReader): M[K, V] = {
              reader.readStartDocument()
              val builder = binding.newBuilder
              
              while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                val keyStr = reader.readName()
                // For string keys, use directly; for other types, decode from the key string
                val key = if (keyCodec == stringCodec) keyStr.asInstanceOf[K]
                         else stringCodec.decode(keyStr.getBytes("UTF-8")).getOrElse(
                           throw new RuntimeException(s"Failed to decode map key: $keyStr")
                         ).asInstanceOf[K]
                val value = valueCodec.decodeUnsafe(reader)
                binding.addOne(builder, (key, value))
              }
              
              reader.readEndDocument()
              binding.result(builder)
            }

            def encode(value: M[K, V], writer: BsonWriter): Unit = {
              writer.writeStartDocument()
              val iterator = binding.iterator(value)
              
              while (iterator.hasNext) {
                val (k, v) = iterator.next()
                // For string keys, use directly; for other types, encode to string
                val keyStr = if (keyCodec == stringCodec) k.asInstanceOf[String]
                            else new String(keyCodec.encode(k), "UTF-8")
                writer.writeName(keyStr)
                valueCodec.encode(v, writer)
              }
              
              writer.writeEndDocument()
            }
          }
        }

        private[this] def deriveWrapperCodec[A, B](
          reflect: Reflect.Wrapper[Binding, A, B]
        ): BsonBinaryCodec[A] = {
          val wrappedCodec = deriveCodec(reflect.wrapped)
          val binding = reflect.binding

          new BsonBinaryCodec[A]() {
            def decodeUnsafe(reader: BsonReader): A = {
              val wrapped = wrappedCodec.decodeUnsafe(reader)
              binding.wrap(wrapped)
            }

            def encode(value: A, writer: BsonWriter): Unit = {
              val wrapped = binding.unwrap(value)
              wrappedCodec.encode(wrapped, writer)
            }
          }
        }

        private[this] def deriveDynamicCodec(reflect: Reflect.Dynamic[Binding]): BsonBinaryCodec[DynamicValue] = {
          new BsonBinaryCodec[DynamicValue]() {
            def decodeUnsafe(reader: BsonReader): DynamicValue = {
              val bsonType = reader.getCurrentBsonType
              bsonType match {
                case BsonType.NULL => 
                  reader.readNull()
                  DynamicValue.NoneValue
                case BsonType.BOOLEAN => 
                  DynamicValue.Primitive(reader.readBoolean(), PrimitiveType.BoolType)
                case BsonType.INT32 => 
                  DynamicValue.Primitive(reader.readInt32(), PrimitiveType.IntType)
                case BsonType.INT64 => 
                  DynamicValue.Primitive(reader.readInt64(), PrimitiveType.LongType)
                case BsonType.DOUBLE => 
                  DynamicValue.Primitive(reader.readDouble(), PrimitiveType.DoubleType)
                case BsonType.STRING => 
                  DynamicValue.Primitive(reader.readString(), PrimitiveType.StringType)
                case BsonType.DOCUMENT =>
                  reader.readStartDocument()
                  var fields = Map.empty[String, DynamicValue]
                  while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                    val name = reader.readName()
                    val value = decodeUnsafe(reader)
                    fields = fields + (name -> value)
                  }
                  reader.readEndDocument()
                  DynamicValue.Record(TypeName.nominal("", "", "Document"), fields)
                case BsonType.ARRAY =>
                  reader.readStartArray()
                  var elements = Vector.empty[DynamicValue]
                  while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                    elements = elements :+ decodeUnsafe(reader)
                  }
                  reader.readEndArray()
                  DynamicValue.Sequence(elements)
                case _ =>
                  decodeError(s"Unsupported BSON type: $bsonType")
              }
            }

            def encode(value: DynamicValue, writer: BsonWriter): Unit = value match {
              case DynamicValue.NoneValue => writer.writeNull()
              case DynamicValue.Primitive(v, primitiveType) =>
                primitiveType match {
                  case PrimitiveType.BoolType => writer.writeBoolean(v.asInstanceOf[Boolean])
                  case PrimitiveType.ByteType => writer.writeInt32(v.asInstanceOf[Byte].toInt)
                  case PrimitiveType.ShortType => writer.writeInt32(v.asInstanceOf[Short].toInt)
                  case PrimitiveType.IntType => writer.writeInt32(v.asInstanceOf[Int])
                  case PrimitiveType.LongType => writer.writeInt64(v.asInstanceOf[Long])
                  case PrimitiveType.FloatType => writer.writeDouble(v.asInstanceOf[Float].toDouble)
                  case PrimitiveType.DoubleType => writer.writeDouble(v.asInstanceOf[Double])
                  case PrimitiveType.CharType => writer.writeInt32(v.asInstanceOf[Char].toInt)
                  case PrimitiveType.StringType => writer.writeString(v.asInstanceOf[String])
                  case _ => throw new RuntimeException(s"Unsupported primitive type: $primitiveType")
                }
              case DynamicValue.Record(_, fields) =>
                writer.writeStartDocument()
                fields.foreach { case (name, value) =>
                  writer.writeName(name)
                  encode(value, writer)
                }
                writer.writeEndDocument()
              case DynamicValue.Sequence(elements) =>
                writer.writeStartArray()
                elements.foreach(encode(_, writer))
                writer.writeEndArray()
              case _ =>
                throw new RuntimeException(s"Unsupported dynamic value: $value")
            }
          }
        }
      }
    )
