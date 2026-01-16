package zio.blocks.schema.thrift

import org.apache.thrift.protocol.{TField, TList, TMap, TProtocol, TProtocolUtil, TStruct, TType}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import scala.util.control.NonFatal

object ThriftFormat
    extends BinaryFormat(
      "application/x-thrift",
      new Deriver[ThriftBinaryCodec] {
        override def derivePrimitive[F[_, _], A](
          primitiveType: PrimitiveType[A],
          typeName: TypeName[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        ): Lazy[ThriftBinaryCodec[A]] =
          Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[C[A]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[M[K, V]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[DynamicValue]] =
          Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeName: TypeName[A],
          wrapperPrimitiveType: Option[PrimitiveType[A]],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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
          new ThreadLocal[java.util.HashMap[TypeName[?], Array[ThriftBinaryCodec[?]]]] {
            override def initialValue: java.util.HashMap[TypeName[?], Array[ThriftBinaryCodec[?]]] =
              new java.util.HashMap
          }
        private[this] val recordCounters =
          new ThreadLocal[java.util.HashMap[(String, String), Int]] {
            override def initialValue: java.util.HashMap[(String, String), Int] = new java.util.HashMap
          }

        private[this] val unitCodec = new ThriftBinaryCodec[Unit](ThriftBinaryCodec.unitType) {
          def decodeUnsafe(protocol: TProtocol): Unit        = ()
          def encode(value: Unit, protocol: TProtocol): Unit = ()
        }

        private[this] val booleanCodec = new ThriftBinaryCodec[Boolean](ThriftBinaryCodec.booleanType) {
          def decodeUnsafe(protocol: TProtocol): Boolean        = protocol.readBool()
          def encode(value: Boolean, protocol: TProtocol): Unit = protocol.writeBool(value)
        }

        private[this] val byteCodec = new ThriftBinaryCodec[Byte](ThriftBinaryCodec.byteType) {
          def decodeUnsafe(protocol: TProtocol): Byte        = protocol.readByte()
          def encode(value: Byte, protocol: TProtocol): Unit = protocol.writeByte(value)
        }

        private[this] val shortCodec = new ThriftBinaryCodec[Short](ThriftBinaryCodec.shortType) {
          def decodeUnsafe(protocol: TProtocol): Short        = protocol.readI16()
          def encode(value: Short, protocol: TProtocol): Unit = protocol.writeI16(value)
        }

        private[this] val intCodec = new ThriftBinaryCodec[Int](ThriftBinaryCodec.intType) {
          def decodeUnsafe(protocol: TProtocol): Int        = protocol.readI32()
          def encode(value: Int, protocol: TProtocol): Unit = protocol.writeI32(value)
        }

        private[this] val longCodec = new ThriftBinaryCodec[Long](ThriftBinaryCodec.longType) {
          def decodeUnsafe(protocol: TProtocol): Long        = protocol.readI64()
          def encode(value: Long, protocol: TProtocol): Unit = protocol.writeI64(value)
        }

        private[this] val floatCodec = new ThriftBinaryCodec[Float](ThriftBinaryCodec.floatType) {
          def decodeUnsafe(protocol: TProtocol): Float        = java.lang.Float.intBitsToFloat(protocol.readI32())
          def encode(value: Float, protocol: TProtocol): Unit = protocol.writeI32(java.lang.Float.floatToIntBits(value))
        }

        private[this] val doubleCodec = new ThriftBinaryCodec[Double](ThriftBinaryCodec.doubleType) {
          def decodeUnsafe(protocol: TProtocol): Double        = protocol.readDouble()
          def encode(value: Double, protocol: TProtocol): Unit = protocol.writeDouble(value)
        }

        private[this] val charCodec = new ThriftBinaryCodec[Char](ThriftBinaryCodec.charType) {
          def decodeUnsafe(protocol: TProtocol): Char = {
            val x = protocol.readI32()
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError("Expected Char")
          }
          def encode(value: Char, protocol: TProtocol): Unit = protocol.writeI32(value)
        }

        private[this] val stringCodec = new ThriftBinaryCodec[String]() {
          def decodeUnsafe(protocol: TProtocol): String        = protocol.readString()
          def encode(value: String, protocol: TProtocol): Unit = protocol.writeString(value)
        }

        private[this] val bigIntCodec = new ThriftBinaryCodec[BigInt]() {
          def decodeUnsafe(protocol: TProtocol): BigInt        = BigInt(protocol.readBinary().array())
          def encode(value: BigInt, protocol: TProtocol): Unit =
            protocol.writeBinary(ByteBuffer.wrap(value.toByteArray))
        }

        private[this] val bigDecimalCodec = new ThriftBinaryCodec[BigDecimal]() {
          def decodeUnsafe(protocol: TProtocol): BigDecimal = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.STRING)
            val mantissa = protocol.readBinary().array()
            readField(protocol, 2, TType.I32)
            val scale = protocol.readI32()
            readField(protocol, 3, TType.I32)
            val precision = protocol.readI32()
            readField(protocol, 4, TType.I32)
            val roundingMode = java.math.RoundingMode.valueOf(protocol.readI32())
            readFieldStop(protocol)
            protocol.readStructEnd()
            val mc = new MathContext(precision, roundingMode)
            new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
          }

          def encode(value: BigDecimal, protocol: TProtocol): Unit = {
            val bd = value.underlying
            val mc = value.mc
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("mantissa", TType.STRING, 1))
            protocol.writeBinary(ByteBuffer.wrap(bd.unscaledValue.toByteArray))
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("scale", TType.I32, 2))
            protocol.writeI32(bd.scale)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("precision", TType.I32, 3))
            protocol.writeI32(mc.getPrecision)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("roundingMode", TType.I32, 4))
            protocol.writeI32(mc.getRoundingMode.ordinal)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val dayOfWeekCodec = new ThriftBinaryCodec[java.time.DayOfWeek]() {
          def decodeUnsafe(protocol: TProtocol): java.time.DayOfWeek        = java.time.DayOfWeek.of(protocol.readI32())
          def encode(value: java.time.DayOfWeek, protocol: TProtocol): Unit = protocol.writeI32(value.getValue)
        }

        private[this] val durationCodec = new ThriftBinaryCodec[java.time.Duration]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Duration = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I64)
            val seconds = protocol.readI64()
            readField(protocol, 2, TType.I32)
            val nanos = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.Duration.ofSeconds(seconds, nanos)
          }

          def encode(value: java.time.Duration, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("seconds", TType.I64, 1))
            protocol.writeI64(value.getSeconds)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("nanos", TType.I32, 2))
            protocol.writeI32(value.getNano)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val instantCodec = new ThriftBinaryCodec[java.time.Instant]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Instant = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I64)
            val epochSecond = protocol.readI64()
            readField(protocol, 2, TType.I32)
            val nano = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.Instant.ofEpochSecond(epochSecond, nano)
          }

          def encode(value: java.time.Instant, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("epochSecond", TType.I64, 1))
            protocol.writeI64(value.getEpochSecond)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("nano", TType.I32, 2))
            protocol.writeI32(value.getNano)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val localDateCodec = new ThriftBinaryCodec[java.time.LocalDate]() {
          def decodeUnsafe(protocol: TProtocol): java.time.LocalDate = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val year = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val month = protocol.readI32()
            readField(protocol, 3, TType.I32)
            val day = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.LocalDate.of(year, month, day)
          }

          def encode(value: java.time.LocalDate, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("year", TType.I32, 1))
            protocol.writeI32(value.getYear)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("month", TType.I32, 2))
            protocol.writeI32(value.getMonthValue)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("day", TType.I32, 3))
            protocol.writeI32(value.getDayOfMonth)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val localDateTimeCodec = new ThriftBinaryCodec[java.time.LocalDateTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.LocalDateTime = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val year = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val month = protocol.readI32()
            readField(protocol, 3, TType.I32)
            val day = protocol.readI32()
            readField(protocol, 4, TType.I32)
            val hour = protocol.readI32()
            readField(protocol, 5, TType.I32)
            val minute = protocol.readI32()
            readField(protocol, 6, TType.I32)
            val second = protocol.readI32()
            readField(protocol, 7, TType.I32)
            val nano = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano)
          }

          def encode(value: java.time.LocalDateTime, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("year", TType.I32, 1))
            protocol.writeI32(value.getYear)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("month", TType.I32, 2))
            protocol.writeI32(value.getMonthValue)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("day", TType.I32, 3))
            protocol.writeI32(value.getDayOfMonth)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("hour", TType.I32, 4))
            protocol.writeI32(value.getHour)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("minute", TType.I32, 5))
            protocol.writeI32(value.getMinute)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("second", TType.I32, 6))
            protocol.writeI32(value.getSecond)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("nano", TType.I32, 7))
            protocol.writeI32(value.getNano)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val localTimeCodec = new ThriftBinaryCodec[java.time.LocalTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.LocalTime = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val hour = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val minute = protocol.readI32()
            readField(protocol, 3, TType.I32)
            val second = protocol.readI32()
            readField(protocol, 4, TType.I32)
            val nano = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.LocalTime.of(hour, minute, second, nano)
          }

          def encode(value: java.time.LocalTime, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("hour", TType.I32, 1))
            protocol.writeI32(value.getHour)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("minute", TType.I32, 2))
            protocol.writeI32(value.getMinute)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("second", TType.I32, 3))
            protocol.writeI32(value.getSecond)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("nano", TType.I32, 4))
            protocol.writeI32(value.getNano)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val monthCodec = new ThriftBinaryCodec[java.time.Month]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Month        = java.time.Month.of(protocol.readI32())
          def encode(value: java.time.Month, protocol: TProtocol): Unit = protocol.writeI32(value.getValue)
        }

        private[this] val monthDayCodec = new ThriftBinaryCodec[java.time.MonthDay]() {
          def decodeUnsafe(protocol: TProtocol): java.time.MonthDay = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val month = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val day = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.MonthDay.of(month, day)
          }

          def encode(value: java.time.MonthDay, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("month", TType.I32, 1))
            protocol.writeI32(value.getMonthValue)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("day", TType.I32, 2))
            protocol.writeI32(value.getDayOfMonth)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val offsetDateTimeCodec = new ThriftBinaryCodec[java.time.OffsetDateTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.OffsetDateTime = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val year = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val month = protocol.readI32()
            readField(protocol, 3, TType.I32)
            val day = protocol.readI32()
            readField(protocol, 4, TType.I32)
            val hour = protocol.readI32()
            readField(protocol, 5, TType.I32)
            val minute = protocol.readI32()
            readField(protocol, 6, TType.I32)
            val second = protocol.readI32()
            readField(protocol, 7, TType.I32)
            val nano = protocol.readI32()
            readField(protocol, 8, TType.I32)
            val offsetSecond = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.OffsetDateTime
              .of(year, month, day, hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }

          def encode(value: java.time.OffsetDateTime, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("year", TType.I32, 1))
            protocol.writeI32(value.getYear)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("month", TType.I32, 2))
            protocol.writeI32(value.getMonthValue)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("day", TType.I32, 3))
            protocol.writeI32(value.getDayOfMonth)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("hour", TType.I32, 4))
            protocol.writeI32(value.getHour)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("minute", TType.I32, 5))
            protocol.writeI32(value.getMinute)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("second", TType.I32, 6))
            protocol.writeI32(value.getSecond)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("nano", TType.I32, 7))
            protocol.writeI32(value.getNano)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("offsetSecond", TType.I32, 8))
            protocol.writeI32(value.getOffset.getTotalSeconds)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val offsetTimeCodec = new ThriftBinaryCodec[java.time.OffsetTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.OffsetTime = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val hour = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val minute = protocol.readI32()
            readField(protocol, 3, TType.I32)
            val second = protocol.readI32()
            readField(protocol, 4, TType.I32)
            val nano = protocol.readI32()
            readField(protocol, 5, TType.I32)
            val offsetSecond = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.OffsetTime.of(hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }

          def encode(value: java.time.OffsetTime, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("hour", TType.I32, 1))
            protocol.writeI32(value.getHour)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("minute", TType.I32, 2))
            protocol.writeI32(value.getMinute)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("second", TType.I32, 3))
            protocol.writeI32(value.getSecond)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("nano", TType.I32, 4))
            protocol.writeI32(value.getNano)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("offsetSecond", TType.I32, 5))
            protocol.writeI32(value.getOffset.getTotalSeconds)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val periodCodec = new ThriftBinaryCodec[java.time.Period]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Period = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val years = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val months = protocol.readI32()
            readField(protocol, 3, TType.I32)
            val days = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.Period.of(years, months, days)
          }

          def encode(value: java.time.Period, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("years", TType.I32, 1))
            protocol.writeI32(value.getYears)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("months", TType.I32, 2))
            protocol.writeI32(value.getMonths)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("days", TType.I32, 3))
            protocol.writeI32(value.getDays)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val yearCodec = new ThriftBinaryCodec[java.time.Year]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Year        = java.time.Year.of(protocol.readI32())
          def encode(value: java.time.Year, protocol: TProtocol): Unit = protocol.writeI32(value.getValue)
        }

        private[this] val yearMonthCodec = new ThriftBinaryCodec[java.time.YearMonth]() {
          def decodeUnsafe(protocol: TProtocol): java.time.YearMonth = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val year = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val month = protocol.readI32()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.YearMonth.of(year, month)
          }

          def encode(value: java.time.YearMonth, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("year", TType.I32, 1))
            protocol.writeI32(value.getYear)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("month", TType.I32, 2))
            protocol.writeI32(value.getMonthValue)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val zoneIdCodec = new ThriftBinaryCodec[java.time.ZoneId]() {
          def decodeUnsafe(protocol: TProtocol): java.time.ZoneId        = java.time.ZoneId.of(protocol.readString())
          def encode(value: java.time.ZoneId, protocol: TProtocol): Unit = protocol.writeString(value.toString)
        }

        private[this] val zoneOffsetCodec = new ThriftBinaryCodec[java.time.ZoneOffset]() {
          def decodeUnsafe(protocol: TProtocol): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(protocol.readI32())
          def encode(value: java.time.ZoneOffset, protocol: TProtocol): Unit = protocol.writeI32(value.getTotalSeconds)
        }

        private[this] val zonedDateTimeCodec = new ThriftBinaryCodec[java.time.ZonedDateTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.ZonedDateTime = {
            protocol.readStructBegin()
            readField(protocol, 1, TType.I32)
            val year = protocol.readI32()
            readField(protocol, 2, TType.I32)
            val month = protocol.readI32()
            readField(protocol, 3, TType.I32)
            val day = protocol.readI32()
            readField(protocol, 4, TType.I32)
            val hour = protocol.readI32()
            readField(protocol, 5, TType.I32)
            val minute = protocol.readI32()
            readField(protocol, 6, TType.I32)
            val second = protocol.readI32()
            readField(protocol, 7, TType.I32)
            val nano = protocol.readI32()
            readField(protocol, 8, TType.I32)
            val offsetSecond = protocol.readI32()
            readField(protocol, 9, TType.STRING)
            val zoneId = protocol.readString()
            readFieldStop(protocol)
            protocol.readStructEnd()
            java.time.ZonedDateTime.ofInstant(
              java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano),
              java.time.ZoneOffset.ofTotalSeconds(offsetSecond),
              java.time.ZoneId.of(zoneId)
            )
          }

          def encode(value: java.time.ZonedDateTime, protocol: TProtocol): Unit = {
            protocol.writeStructBegin(emptyStruct)
            protocol.writeFieldBegin(new TField("year", TType.I32, 1))
            protocol.writeI32(value.getYear)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("month", TType.I32, 2))
            protocol.writeI32(value.getMonthValue)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("day", TType.I32, 3))
            protocol.writeI32(value.getDayOfMonth)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("hour", TType.I32, 4))
            protocol.writeI32(value.getHour)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("minute", TType.I32, 5))
            protocol.writeI32(value.getMinute)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("second", TType.I32, 6))
            protocol.writeI32(value.getSecond)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("nano", TType.I32, 7))
            protocol.writeI32(value.getNano)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("offsetSecond", TType.I32, 8))
            protocol.writeI32(value.getOffset.getTotalSeconds)
            protocol.writeFieldEnd()
            protocol.writeFieldBegin(new TField("zoneId", TType.STRING, 9))
            protocol.writeString(value.getZone.toString)
            protocol.writeFieldEnd()
            protocol.writeFieldStop()
            protocol.writeStructEnd()
          }
        }

        private[this] val currencyCodec = new ThriftBinaryCodec[java.util.Currency]() {
          def decodeUnsafe(protocol: TProtocol): java.util.Currency =
            java.util.Currency.getInstance(protocol.readString())
          def encode(value: java.util.Currency, protocol: TProtocol): Unit = protocol.writeString(value.getCurrencyCode)
        }

        private[this] val uuidCodec = new ThriftBinaryCodec[java.util.UUID]() {
          def decodeUnsafe(protocol: TProtocol): java.util.UUID = {
            val hi = protocol.readI64()
            val lo = protocol.readI64()
            new java.util.UUID(hi, lo)
          }

          def encode(value: java.util.UUID, protocol: TProtocol): Unit = {
            protocol.writeI64(value.getMostSignificantBits)
            protocol.writeI64(value.getLeastSignificantBits)
          }
        }

        private[this] val emptyStruct = new TStruct("")

        private[this] def readField(protocol: TProtocol, expectedId: Short, expectedType: Byte): Unit = {
          val field = protocol.readFieldBegin()
          if (field.id != expectedId || field.`type` != expectedType) {
            throw new ThriftBinaryCodecError(Nil, s"Expected field $expectedId of type $expectedType")
          }
        }

        private[this] def readFieldStop(protocol: TProtocol): Unit = {
          val field = protocol.readFieldBegin()
          if (field.`type` != TType.STOP) {
            throw new ThriftBinaryCodecError(Nil, "Expected field stop")
          }
        }

        private[this] def getThriftType(codec: ThriftBinaryCodec[?]): Byte = codec.valueType match {
          case ThriftBinaryCodec.booleanType => TType.BOOL
          case ThriftBinaryCodec.byteType    => TType.BYTE
          case ThriftBinaryCodec.shortType   => TType.I16
          case ThriftBinaryCodec.intType     => TType.I32
          case ThriftBinaryCodec.longType    => TType.I64
          case ThriftBinaryCodec.floatType   => TType.I32
          case ThriftBinaryCodec.doubleType  => TType.DOUBLE
          case ThriftBinaryCodec.charType    => TType.I32
          case ThriftBinaryCodec.unitType    => TType.STRUCT
          case _                             => TType.STRUCT
        }

        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ThriftBinaryCodec[A] = {
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
              val codecs  = new Array[ThriftBinaryCodec[?]](len)
              val types   = new Array[Byte](len)
              var idx     = 0
              while (idx < len) {
                val codec = deriveCodec(cases(idx).value)
                codecs(idx) = codec
                types(idx) = getThriftType(codec)
                idx += 1
              }
              new ThriftBinaryCodec[A]() {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs
                private[this] val caseTypes     = types

                def decodeUnsafe(protocol: TProtocol): A = {
                  protocol.readStructBegin()
                  val field = protocol.readFieldBegin()
                  val idx   = field.id.toInt - 1
                  if (idx >= 0 && idx < caseCodecs.length) {
                    val result =
                      try caseCodecs(idx).asInstanceOf[ThriftBinaryCodec[A]].decodeUnsafe(protocol)
                      catch {
                        case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
                      }
                    protocol.readFieldEnd()
                    readFieldStop(protocol)
                    protocol.readStructEnd()
                    result
                  } else decodeError(s"Expected union field from 1 to ${caseCodecs.length}, got ${field.id}")
                }

                def encode(value: A, protocol: TProtocol): Unit = {
                  val idx = discriminator.discriminate(value)
                  protocol.writeStructBegin(emptyStruct)
                  protocol.writeFieldBegin(new TField(cases(idx).name, caseTypes(idx), (idx + 1).toShort))
                  caseCodecs(idx).asInstanceOf[ThriftBinaryCodec[A]].encode(value, protocol)
                  protocol.writeFieldEnd()
                  protocol.writeFieldStop()
                  protocol.writeStructEnd()
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding  = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec    = deriveCodec(sequence.element).asInstanceOf[ThriftBinaryCodec[Elem]]
              val elemType = getThriftType(codec)
              new ThriftBinaryCodec[Col[Elem]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val elementCodec  = codec
                private[this] val elementType   = elemType

                def decodeUnsafe(protocol: TProtocol): Col[Elem] = {
                  val list = protocol.readListBegin()
                  val size = list.size
                  if (size > ThriftBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${ThriftBinaryCodec.maxCollectionSize}, got $size"
                    )
                  }
                  val builder = constructor.newObjectBuilder[Elem](size)
                  var idx     = 0
                  try {
                    while (idx < size) {
                      constructor.addObject(builder, elementCodec.decodeUnsafe(protocol))
                      idx += 1
                    }
                  } catch {
                    case error if NonFatal(error) =>
                      decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                  }
                  protocol.readListEnd()
                  constructor.resultObject[Elem](builder)
                }

                def encode(value: Col[Elem], protocol: TProtocol): Unit = {
                  val size = deconstructor.size(value)
                  protocol.writeListBegin(new TList(elementType, size))
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) elementCodec.encode(it.next(), protocol)
                  protocol.writeListEnd()
                }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding  = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val keyCodec = deriveCodec(map.key).asInstanceOf[ThriftBinaryCodec[Key]]
              val valCodec = deriveCodec(map.value).asInstanceOf[ThriftBinaryCodec[Value]]
              val keyType  = getThriftType(keyCodec)
              val valType  = getThriftType(valCodec)
              new ThriftBinaryCodec[Map[Key, Value]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val kCodec        = keyCodec
                private[this] val vCodec        = valCodec
                private[this] val kType         = keyType
                private[this] val vType         = valType
                private[this] val keyReflect    = map.key.asInstanceOf[Reflect.Bound[Key]]

                def decodeUnsafe(protocol: TProtocol): Map[Key, Value] = {
                  val thriftMap = protocol.readMapBegin()
                  val size      = thriftMap.size
                  if (size > ThriftBinaryCodec.maxCollectionSize) {
                    decodeError(s"Expected map size not greater than ${ThriftBinaryCodec.maxCollectionSize}, got $size")
                  }
                  val builder = constructor.newObjectBuilder[Key, Value](size)
                  var idx     = 0
                  while (idx < size) {
                    val k =
                      try kCodec.decodeUnsafe(protocol)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                    val v =
                      try vCodec.decodeUnsafe(protocol)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), error)
                      }
                    constructor.addObject(builder, k, v)
                    idx += 1
                  }
                  protocol.readMapEnd()
                  constructor.resultObject[Key, Value](builder)
                }

                def encode(value: Map[Key, Value], protocol: TProtocol): Unit = {
                  val size = deconstructor.size(value)
                  protocol.writeMapBegin(new TMap(kType, vType, size))
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) {
                    val kv = it.next()
                    kCodec.encode(deconstructor.getKey(kv), protocol)
                    vCodec.encode(deconstructor.getValue(kv), protocol)
                  }
                  protocol.writeMapEnd()
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
                codecs = new Array[ThriftBinaryCodec[?]](len)
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
              new ThriftBinaryCodec[A]() {
                private[this] val deconstructor                = binding.deconstructor
                private[this] val constructor                  = binding.constructor
                private[this] val usedRegisters                = offset
                private[this] val fieldCodecs                  = codecs
                @volatile private[this] var types: Array[Byte] = null
                @volatile private[this] var offsets: Array[Long] = null

                private[this] def getTypes: Array[Byte] = {
                  var t = types
                  if (t eq null) {
                    t = new Array[Byte](fieldCodecs.length)
                    var i = 0
                    while (i < fieldCodecs.length) {
                      t(i) = getThriftType(fieldCodecs(i))
                      i += 1
                    }
                    types = t
                  }
                  t
                }

                private[this] def getOffsets: Array[Long] = {
                  var o = offsets
                  if (o eq null) {
                    o = new Array[Long](fieldCodecs.length)
                    var i   = 0
                    var off = 0L
                    while (i < fieldCodecs.length) {
                      o(i) = off
                      off += fieldCodecs(i).valueOffset
                      i += 1
                    }
                    offsets = o
                  }
                  o
                }

                def decodeUnsafe(protocol: TProtocol): A = {
                  val regs      = Registers(usedRegisters)
                  protocol.readStructBegin()
                  val len     = fieldCodecs.length
                  val seen    = new Array[Boolean](len)
                  val offsets = getOffsets

                  var field = protocol.readFieldBegin()
                  while (field.`type` != TType.STOP) {
                    val idx = field.id.toInt - 1
                    if (idx >= 0 && idx < len) {
                      val codec     = fieldCodecs(idx)
                      val regOffset = offsets(idx)
                      try {
                        codec.valueType match {
                          case ThriftBinaryCodec.objectType =>
                            regs.setObject(regOffset, codec.asInstanceOf[ThriftBinaryCodec[AnyRef]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.intType =>
                            regs.setInt(regOffset, codec.asInstanceOf[ThriftBinaryCodec[Int]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.longType =>
                            regs.setLong(regOffset, codec.asInstanceOf[ThriftBinaryCodec[Long]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.floatType =>
                            regs.setFloat(regOffset, codec.asInstanceOf[ThriftBinaryCodec[Float]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.doubleType =>
                            regs.setDouble(regOffset, codec.asInstanceOf[ThriftBinaryCodec[Double]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.booleanType =>
                            regs.setBoolean(
                              regOffset,
                              codec.asInstanceOf[ThriftBinaryCodec[Boolean]].decodeUnsafe(protocol)
                            )
                          case ThriftBinaryCodec.byteType =>
                            regs.setByte(regOffset, codec.asInstanceOf[ThriftBinaryCodec[Byte]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.charType =>
                            regs.setChar(regOffset, codec.asInstanceOf[ThriftBinaryCodec[Char]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.shortType =>
                            regs.setShort(regOffset, codec.asInstanceOf[ThriftBinaryCodec[Short]].decodeUnsafe(protocol))
                          case _ =>
                            codec.asInstanceOf[ThriftBinaryCodec[Unit]].decodeUnsafe(protocol)
                        }
                      } catch {
                        case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                      }
                      seen(idx) = true
                    } else {
                      // Allow forward-compatible readers: ignore unknown fields (as long as protocol can skip them).
                      TProtocolUtil.skip(protocol, field.`type`)
                    }
                    protocol.readFieldEnd()
                    field = protocol.readFieldBegin()
                  }
                  protocol.readStructEnd()

                  var missing = 0
                  while (missing < len) {
                    if (!seen(missing)) {
                      decodeError(s"Missing field ${fields(missing).name} (${missing + 1})")
                    }
                    missing += 1
                  }

                  constructor.construct(regs, 0)
                }

                def encode(value: A, protocol: TProtocol): Unit = {
                  val regs      = Registers(usedRegisters)
                  var regOffset = 0L
                  deconstructor.deconstruct(regs, regOffset, value)
                  protocol.writeStructBegin(emptyStruct)
                  val len        = fieldCodecs.length
                  val fieldTypes = getTypes
                  var idx        = 0
                  while (idx < len) {
                    val codec = fieldCodecs(idx)
                    protocol.writeFieldBegin(new TField(fields(idx).name, fieldTypes(idx), (idx + 1).toShort))
                    codec.valueType match {
                      case ThriftBinaryCodec.objectType =>
                        codec.asInstanceOf[ThriftBinaryCodec[AnyRef]].encode(regs.getObject(regOffset), protocol)
                      case ThriftBinaryCodec.intType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Int]].encode(regs.getInt(regOffset), protocol)
                      case ThriftBinaryCodec.longType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Long]].encode(regs.getLong(regOffset), protocol)
                      case ThriftBinaryCodec.floatType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Float]].encode(regs.getFloat(regOffset), protocol)
                      case ThriftBinaryCodec.doubleType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Double]].encode(regs.getDouble(regOffset), protocol)
                      case ThriftBinaryCodec.booleanType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Boolean]].encode(regs.getBoolean(regOffset), protocol)
                      case ThriftBinaryCodec.byteType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Byte]].encode(regs.getByte(regOffset), protocol)
                      case ThriftBinaryCodec.charType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Char]].encode(regs.getChar(regOffset), protocol)
                      case ThriftBinaryCodec.shortType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Short]].encode(regs.getShort(regOffset), protocol)
                      case _ => codec.asInstanceOf[ThriftBinaryCodec[Unit]].encode((), protocol)
                    }
                    protocol.writeFieldEnd()
                    regOffset += codec.valueOffset
                    idx += 1
                  }
                  protocol.writeFieldStop()
                  protocol.writeStructEnd()
                }
              }
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val codec   = deriveCodec(wrapper.wrapped).asInstanceOf[ThriftBinaryCodec[Wrapped]]
              new ThriftBinaryCodec[A](wrapper.wrapperPrimitiveType.fold(ThriftBinaryCodec.objectType) {
                case _: PrimitiveType.Boolean   => ThriftBinaryCodec.booleanType
                case _: PrimitiveType.Byte      => ThriftBinaryCodec.byteType
                case _: PrimitiveType.Char      => ThriftBinaryCodec.charType
                case _: PrimitiveType.Short     => ThriftBinaryCodec.shortType
                case _: PrimitiveType.Float     => ThriftBinaryCodec.floatType
                case _: PrimitiveType.Int       => ThriftBinaryCodec.intType
                case _: PrimitiveType.Double    => ThriftBinaryCodec.doubleType
                case _: PrimitiveType.Long      => ThriftBinaryCodec.longType
                case _: PrimitiveType.Unit.type => ThriftBinaryCodec.unitType
                case _                          => ThriftBinaryCodec.objectType
              }) {
                private[this] val unwrap       = binding.unwrap
                private[this] val wrap         = binding.wrap
                private[this] val wrappedCodec = codec

                def decodeUnsafe(protocol: TProtocol): A = {
                  val wrapped =
                    try wrappedCodec.decodeUnsafe(protocol)
                    catch {
                      case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                    }
                  wrap(wrapped) match {
                    case Right(x)  => x
                    case Left(err) => decodeError(err)
                  }
                }

                def encode(value: A, protocol: TProtocol): Unit = wrappedCodec.encode(unwrap(value), protocol)
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
            else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          }
        }.asInstanceOf[ThriftBinaryCodec[A]]

        private[this] val dynamicValueCodec = new ThriftBinaryCodec[DynamicValue]() {
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

          def decodeUnsafe(protocol: TProtocol): DynamicValue = {
            protocol.readStructBegin()
            val field  = protocol.readFieldBegin()
            val result = (field.id: @scala.annotation.switch) match {
              case 1 =>
                try {
                  protocol.readStructBegin()
                  val primitiveField = protocol.readFieldBegin()
                  val idx            = primitiveField.id.toInt - 1
                  if (idx < 0 || idx > 29)
                    decodeError(s"Expected primitive index from 1 to 30, got ${primitiveField.id}")
                  val value =
                    try {
                      new DynamicValue.Primitive((idx: @scala.annotation.switch) match {
                        case 0  => PrimitiveValue.Unit
                        case 1  => new PrimitiveValue.Boolean(booleanCodec.decodeUnsafe(protocol))
                        case 2  => new PrimitiveValue.Byte(byteCodec.decodeUnsafe(protocol))
                        case 3  => new PrimitiveValue.Short(shortCodec.decodeUnsafe(protocol))
                        case 4  => new PrimitiveValue.Int(intCodec.decodeUnsafe(protocol))
                        case 5  => new PrimitiveValue.Long(longCodec.decodeUnsafe(protocol))
                        case 6  => new PrimitiveValue.Float(floatCodec.decodeUnsafe(protocol))
                        case 7  => new PrimitiveValue.Double(doubleCodec.decodeUnsafe(protocol))
                        case 8  => new PrimitiveValue.Char(charCodec.decodeUnsafe(protocol))
                        case 9  => new PrimitiveValue.String(stringCodec.decodeUnsafe(protocol))
                        case 10 => new PrimitiveValue.BigInt(bigIntCodec.decodeUnsafe(protocol))
                        case 11 => new PrimitiveValue.BigDecimal(bigDecimalCodec.decodeUnsafe(protocol))
                        case 12 => new PrimitiveValue.DayOfWeek(dayOfWeekCodec.decodeUnsafe(protocol))
                        case 13 => new PrimitiveValue.Duration(durationCodec.decodeUnsafe(protocol))
                        case 14 => new PrimitiveValue.Instant(instantCodec.decodeUnsafe(protocol))
                        case 15 => new PrimitiveValue.LocalDate(localDateCodec.decodeUnsafe(protocol))
                        case 16 => new PrimitiveValue.LocalDateTime(localDateTimeCodec.decodeUnsafe(protocol))
                        case 17 => new PrimitiveValue.LocalTime(localTimeCodec.decodeUnsafe(protocol))
                        case 18 => new PrimitiveValue.Month(monthCodec.decodeUnsafe(protocol))
                        case 19 => new PrimitiveValue.MonthDay(monthDayCodec.decodeUnsafe(protocol))
                        case 20 => new PrimitiveValue.OffsetDateTime(offsetDateTimeCodec.decodeUnsafe(protocol))
                        case 21 => new PrimitiveValue.OffsetTime(offsetTimeCodec.decodeUnsafe(protocol))
                        case 22 => new PrimitiveValue.Period(periodCodec.decodeUnsafe(protocol))
                        case 23 => new PrimitiveValue.Year(yearCodec.decodeUnsafe(protocol))
                        case 24 => new PrimitiveValue.YearMonth(yearMonthCodec.decodeUnsafe(protocol))
                        case 25 => new PrimitiveValue.ZoneId(zoneIdCodec.decodeUnsafe(protocol))
                        case 26 => new PrimitiveValue.ZoneOffset(zoneOffsetCodec.decodeUnsafe(protocol))
                        case 27 => new PrimitiveValue.ZonedDateTime(zonedDateTimeCodec.decodeUnsafe(protocol))
                        case 28 => new PrimitiveValue.Currency(currencyCodec.decodeUnsafe(protocol))
                        case _  => new PrimitiveValue.UUID(uuidCodec.decodeUnsafe(protocol))
                      })
                    } catch {
                      case error if NonFatal(error) => decodeError(spanValue, error)
                    }
                  protocol.readFieldEnd()
                  readFieldStop(protocol)
                  protocol.readStructEnd()
                  value
                } catch {
                  case error if NonFatal(error) => decodeError(spanPrimitive, error)
                }
              case 2 =>
                try {
                  val list = protocol.readListBegin()
                  val size = list.size
                  if (size > ThriftBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${ThriftBinaryCodec.maxCollectionSize}, got $size"
                    )
                  }
                  val builder = Vector.newBuilder[(String, DynamicValue)]
                  var idx     = 0
                  while (idx < size) {
                    protocol.readStructBegin()
                    readField(protocol, 1, TType.STRING)
                    val k =
                      try protocol.readString()
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), span_1, error)
                      }
                    protocol.readFieldEnd()
                    readField(protocol, 2, TType.STRUCT)
                    val v =
                      try decodeUnsafe(protocol)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), span_2, error)
                      }
                    protocol.readFieldEnd()
                    readFieldStop(protocol)
                    protocol.readStructEnd()
                    builder.addOne((k, v))
                    idx += 1
                  }
                  protocol.readListEnd()
                  new DynamicValue.Record(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanRecord, spanFields, error)
                }
              case 3 =>
                protocol.readStructBegin()
                readField(protocol, 1, TType.STRING)
                val caseName =
                  try protocol.readString()
                  catch {
                    case error if NonFatal(error) => decodeError(spanVariant, spanCaseName, error)
                  }
                protocol.readFieldEnd()
                readField(protocol, 2, TType.STRUCT)
                val value =
                  try decodeUnsafe(protocol)
                  catch {
                    case error if NonFatal(error) => decodeError(spanVariant, spanValue, error)
                  }
                protocol.readFieldEnd()
                readFieldStop(protocol)
                protocol.readStructEnd()
                new DynamicValue.Variant(caseName, value)
              case 4 =>
                try {
                  val list = protocol.readListBegin()
                  val size = list.size
                  if (size > ThriftBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${ThriftBinaryCodec.maxCollectionSize}, got $size"
                    )
                  }
                  val builder = Vector.newBuilder[DynamicValue]
                  var idx     = 0
                  try {
                    while (idx < size) {
                      builder.addOne(decodeUnsafe(protocol))
                      idx += 1
                    }
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                  }
                  protocol.readListEnd()
                  new DynamicValue.Sequence(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanSequence, spanElements, error)
                }
              case 5 =>
                try {
                  val list = protocol.readListBegin()
                  val size = list.size
                  if (size > ThriftBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${ThriftBinaryCodec.maxCollectionSize}, got $size"
                    )
                  }
                  val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
                  var idx     = 0
                  while (idx < size) {
                    protocol.readStructBegin()
                    readField(protocol, 1, TType.STRUCT)
                    val k =
                      try decodeUnsafe(protocol)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), span_1, error)
                      }
                    protocol.readFieldEnd()
                    readField(protocol, 2, TType.STRUCT)
                    val v =
                      try decodeUnsafe(protocol)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), span_2, error)
                      }
                    protocol.readFieldEnd()
                    readFieldStop(protocol)
                    protocol.readStructEnd()
                    builder.addOne((k, v))
                    idx += 1
                  }
                  protocol.readListEnd()
                  new DynamicValue.Map(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanMap, spanEntries, error)
                }
              case idx => decodeError(s"Expected DynamicValue field from 1 to 5, got $idx")
            }
            protocol.readFieldEnd()
            readFieldStop(protocol)
            protocol.readStructEnd()
            result
          }

          def encode(value: DynamicValue, protocol: TProtocol): Unit = value match {
            case primitive: DynamicValue.Primitive =>
              protocol.writeStructBegin(emptyStruct)
              protocol.writeFieldBegin(new TField("Primitive", TType.STRUCT, 1))
              protocol.writeStructBegin(emptyStruct)
              primitive.value match {
                case _: PrimitiveValue.Unit.type =>
                  protocol.writeFieldBegin(new TField("Unit", TType.STRUCT, 1))
                  protocol.writeStructBegin(emptyStruct)
                  protocol.writeFieldStop()
                  protocol.writeStructEnd()
                case v: PrimitiveValue.Boolean =>
                  protocol.writeFieldBegin(new TField("Boolean", TType.BOOL, 2))
                  booleanCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Byte =>
                  protocol.writeFieldBegin(new TField("Byte", TType.BYTE, 3))
                  byteCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Short =>
                  protocol.writeFieldBegin(new TField("Short", TType.I16, 4))
                  shortCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Int =>
                  protocol.writeFieldBegin(new TField("Int", TType.I32, 5))
                  intCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Long =>
                  protocol.writeFieldBegin(new TField("Long", TType.I64, 6))
                  longCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Float =>
                  protocol.writeFieldBegin(new TField("Float", TType.I32, 7))
                  floatCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Double =>
                  protocol.writeFieldBegin(new TField("Double", TType.DOUBLE, 8))
                  doubleCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Char =>
                  protocol.writeFieldBegin(new TField("Char", TType.I32, 9))
                  charCodec.encode(v.value, protocol)
                case v: PrimitiveValue.String =>
                  protocol.writeFieldBegin(new TField("String", TType.STRING, 10))
                  stringCodec.encode(v.value, protocol)
                case v: PrimitiveValue.BigInt =>
                  protocol.writeFieldBegin(new TField("BigInt", TType.STRING, 11))
                  bigIntCodec.encode(v.value, protocol)
                case v: PrimitiveValue.BigDecimal =>
                  protocol.writeFieldBegin(new TField("BigDecimal", TType.STRUCT, 12))
                  bigDecimalCodec.encode(v.value, protocol)
                case v: PrimitiveValue.DayOfWeek =>
                  protocol.writeFieldBegin(new TField("DayOfWeek", TType.I32, 13))
                  dayOfWeekCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Duration =>
                  protocol.writeFieldBegin(new TField("Duration", TType.STRUCT, 14))
                  durationCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Instant =>
                  protocol.writeFieldBegin(new TField("Instant", TType.STRUCT, 15))
                  instantCodec.encode(v.value, protocol)
                case v: PrimitiveValue.LocalDate =>
                  protocol.writeFieldBegin(new TField("LocalDate", TType.STRUCT, 16))
                  localDateCodec.encode(v.value, protocol)
                case v: PrimitiveValue.LocalDateTime =>
                  protocol.writeFieldBegin(new TField("LocalDateTime", TType.STRUCT, 17))
                  localDateTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.LocalTime =>
                  protocol.writeFieldBegin(new TField("LocalTime", TType.STRUCT, 18))
                  localTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Month =>
                  protocol.writeFieldBegin(new TField("Month", TType.I32, 19))
                  monthCodec.encode(v.value, protocol)
                case v: PrimitiveValue.MonthDay =>
                  protocol.writeFieldBegin(new TField("MonthDay", TType.STRUCT, 20))
                  monthDayCodec.encode(v.value, protocol)
                case v: PrimitiveValue.OffsetDateTime =>
                  protocol.writeFieldBegin(new TField("OffsetDateTime", TType.STRUCT, 21))
                  offsetDateTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.OffsetTime =>
                  protocol.writeFieldBegin(new TField("OffsetTime", TType.STRUCT, 22))
                  offsetTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Period =>
                  protocol.writeFieldBegin(new TField("Period", TType.STRUCT, 23))
                  periodCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Year =>
                  protocol.writeFieldBegin(new TField("Year", TType.I32, 24))
                  yearCodec.encode(v.value, protocol)
                case v: PrimitiveValue.YearMonth =>
                  protocol.writeFieldBegin(new TField("YearMonth", TType.STRUCT, 25))
                  yearMonthCodec.encode(v.value, protocol)
                case v: PrimitiveValue.ZoneId =>
                  protocol.writeFieldBegin(new TField("ZoneId", TType.STRING, 26))
                  zoneIdCodec.encode(v.value, protocol)
                case v: PrimitiveValue.ZoneOffset =>
                  protocol.writeFieldBegin(new TField("ZoneOffset", TType.I32, 27))
                  zoneOffsetCodec.encode(v.value, protocol)
                case v: PrimitiveValue.ZonedDateTime =>
                  protocol.writeFieldBegin(new TField("ZonedDateTime", TType.STRUCT, 28))
                  zonedDateTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Currency =>
                  protocol.writeFieldBegin(new TField("Currency", TType.STRING, 29))
                  currencyCodec.encode(v.value, protocol)
                case v: PrimitiveValue.UUID =>
                  protocol.writeFieldBegin(new TField("UUID", TType.STRUCT, 30))
                  uuidCodec.encode(v.value, protocol)
              }
              protocol.writeFieldEnd()
              protocol.writeFieldStop()
              protocol.writeStructEnd()
              protocol.writeFieldEnd()
              protocol.writeFieldStop()
              protocol.writeStructEnd()
            case record: DynamicValue.Record =>
              protocol.writeStructBegin(emptyStruct)
              protocol.writeFieldBegin(new TField("Record", TType.LIST, 2))
              val fields = record.fields
              val size   = fields.length
              protocol.writeListBegin(new TList(TType.STRUCT, size))
              val it = fields.iterator
              while (it.hasNext) {
                val kv = it.next()
                protocol.writeStructBegin(emptyStruct)
                protocol.writeFieldBegin(new TField("name", TType.STRING, 1))
                protocol.writeString(kv._1)
                protocol.writeFieldEnd()
                protocol.writeFieldBegin(new TField("value", TType.STRUCT, 2))
                encode(kv._2, protocol)
                protocol.writeFieldEnd()
                protocol.writeFieldStop()
                protocol.writeStructEnd()
              }
              protocol.writeListEnd()
              protocol.writeFieldEnd()
              protocol.writeFieldStop()
              protocol.writeStructEnd()
            case variant: DynamicValue.Variant =>
              protocol.writeStructBegin(emptyStruct)
              protocol.writeFieldBegin(new TField("Variant", TType.STRUCT, 3))
              protocol.writeStructBegin(emptyStruct)
              protocol.writeFieldBegin(new TField("caseName", TType.STRING, 1))
              protocol.writeString(variant.caseName)
              protocol.writeFieldEnd()
              protocol.writeFieldBegin(new TField("value", TType.STRUCT, 2))
              encode(variant.value, protocol)
              protocol.writeFieldEnd()
              protocol.writeFieldStop()
              protocol.writeStructEnd()
              protocol.writeFieldEnd()
              protocol.writeFieldStop()
              protocol.writeStructEnd()
            case sequence: DynamicValue.Sequence =>
              protocol.writeStructBegin(emptyStruct)
              protocol.writeFieldBegin(new TField("Sequence", TType.LIST, 4))
              val elements = sequence.elements
              val size     = elements.length
              protocol.writeListBegin(new TList(TType.STRUCT, size))
              val it = elements.iterator
              while (it.hasNext) encode(it.next(), protocol)
              protocol.writeListEnd()
              protocol.writeFieldEnd()
              protocol.writeFieldStop()
              protocol.writeStructEnd()
            case map: DynamicValue.Map =>
              protocol.writeStructBegin(emptyStruct)
              protocol.writeFieldBegin(new TField("Map", TType.LIST, 5))
              val entries = map.entries
              val size    = entries.length
              protocol.writeListBegin(new TList(TType.STRUCT, size))
              val it = entries.iterator
              while (it.hasNext) {
                val kv = it.next()
                protocol.writeStructBegin(emptyStruct)
                protocol.writeFieldBegin(new TField("key", TType.STRUCT, 1))
                encode(kv._1, protocol)
                protocol.writeFieldEnd()
                protocol.writeFieldBegin(new TField("value", TType.STRUCT, 2))
                encode(kv._2, protocol)
                protocol.writeFieldEnd()
                protocol.writeFieldStop()
                protocol.writeStructEnd()
              }
              protocol.writeListEnd()
              protocol.writeFieldEnd()
              protocol.writeFieldStop()
              protocol.writeStructEnd()
          }
        }
      }
    )
