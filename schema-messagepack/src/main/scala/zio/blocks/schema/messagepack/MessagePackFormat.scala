package zio.blocks.schema.messagepack

import org.msgpack.core.{MessagePacker, MessageUnpacker}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema.binding.SeqDeconstructor._
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.math.{BigInteger, MathContext}
import scala.util.control.NonFatal

object MessagePackFormat
    extends BinaryFormat(
      "application/msgpack",
      new Deriver[MessagePackBinaryCodec] {
        override def derivePrimitive[F[_, _], A](
          primitiveType: PrimitiveType[A],
          typeName: TypeName[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        ): Lazy[MessagePackBinaryCodec[A]] =
          Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[C[A]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[M[K, V]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[DynamicValue]] =
          Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeName: TypeName[A],
          wrapperPrimitiveType: Option[PrimitiveType[A]],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] = Lazy {
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

        private[this] val recursiveRecordCache =
          new ThreadLocal[java.util.HashMap[TypeName[?], Array[MessagePackBinaryCodec[?]]]] {
            override def initialValue: java.util.HashMap[TypeName[?], Array[MessagePackBinaryCodec[?]]] =
              new java.util.HashMap
          }

        private[this] val unitCodec = new MessagePackBinaryCodec[Unit](MessagePackBinaryCodec.unitType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Unit = unpacker.unpackNil()

          def encodeUnsafe(packer: MessagePacker, value: Unit): Unit = packer.packNil()
        }

        private[this] val booleanCodec = new MessagePackBinaryCodec[Boolean](MessagePackBinaryCodec.booleanType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Boolean = unpacker.unpackBoolean()

          def encodeUnsafe(packer: MessagePacker, value: Boolean): Unit = packer.packBoolean(value)
        }

        private[this] val byteCodec = new MessagePackBinaryCodec[Byte](MessagePackBinaryCodec.byteType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Byte = unpacker.unpackByte()

          def encodeUnsafe(packer: MessagePacker, value: Byte): Unit = packer.packByte(value)
        }

        private[this] val shortCodec = new MessagePackBinaryCodec[Short](MessagePackBinaryCodec.shortType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Short = unpacker.unpackShort()

          def encodeUnsafe(packer: MessagePacker, value: Short): Unit = packer.packShort(value)
        }

        private[this] val intCodec = new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Int = unpacker.unpackInt()

          def encodeUnsafe(packer: MessagePacker, value: Int): Unit = packer.packInt(value)
        }

        private[this] val longCodec = new MessagePackBinaryCodec[Long](MessagePackBinaryCodec.longType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Long = unpacker.unpackLong()

          def encodeUnsafe(packer: MessagePacker, value: Long): Unit = packer.packLong(value)
        }

        private[this] val floatCodec = new MessagePackBinaryCodec[Float](MessagePackBinaryCodec.floatType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Float = unpacker.unpackFloat()

          def encodeUnsafe(packer: MessagePacker, value: Float): Unit = packer.packFloat(value)
        }

        private[this] val doubleCodec = new MessagePackBinaryCodec[Double](MessagePackBinaryCodec.doubleType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Double = unpacker.unpackDouble()

          def encodeUnsafe(packer: MessagePacker, value: Double): Unit = packer.packDouble(value)
        }

        private[this] val charCodec = new MessagePackBinaryCodec[Char](MessagePackBinaryCodec.charType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Char = {
            val x = unpacker.unpackInt()
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError("Expected Char")
          }

          def encodeUnsafe(packer: MessagePacker, value: Char): Unit = packer.packInt(value.toInt)
        }

        private[this] val stringCodec = new MessagePackBinaryCodec[String]() {
          def decodeUnsafe(unpacker: MessageUnpacker): String = unpacker.unpackString()

          def encodeUnsafe(packer: MessagePacker, value: String): Unit = packer.packString(value)
        }

        private[this] val bigIntCodec = new MessagePackBinaryCodec[BigInt]() {
          def decodeUnsafe(unpacker: MessageUnpacker): BigInt = {
            val len = unpacker.unpackBinaryHeader()
            val bs  = new Array[Byte](len)
            unpacker.readPayload(bs)
            BigInt(bs)
          }

          def encodeUnsafe(packer: MessagePacker, value: BigInt): Unit = {
            val bs = value.toByteArray
            packer.packBinaryHeader(bs.length)
            packer.writePayload(bs)
          }
        }

        private[this] val bigDecimalCodec = new MessagePackBinaryCodec[BigDecimal]() {
          def decodeUnsafe(unpacker: MessageUnpacker): BigDecimal = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 4) decodeError("Expected BigDecimal map with 4 fields")
            var mantissa: Array[Byte]                = null
            var scale: Int                           = 0
            var precision: Int                       = 0
            var roundingMode: java.math.RoundingMode = null
            var idx                                  = 0
            while (idx < 4) {
              val key = unpacker.unpackString()
              key match {
                case "mantissa" =>
                  val len = unpacker.unpackBinaryHeader()
                  mantissa = new Array[Byte](len)
                  unpacker.readPayload(mantissa)
                case "scale"        => scale = unpacker.unpackInt()
                case "precision"    => precision = unpacker.unpackInt()
                case "roundingMode" => roundingMode = java.math.RoundingMode.valueOf(unpacker.unpackInt())
                case _              => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            val mc = new MathContext(precision, roundingMode)
            new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
          }

          def encodeUnsafe(packer: MessagePacker, value: BigDecimal): Unit = {
            val bd = value.underlying
            val mc = value.mc
            packer.packMapHeader(4)
            packer.packString("mantissa")
            val mantissa = bd.unscaledValue.toByteArray
            packer.packBinaryHeader(mantissa.length)
            packer.writePayload(mantissa)
            packer.packString("scale")
            packer.packInt(bd.scale)
            packer.packString("precision")
            packer.packInt(mc.getPrecision)
            packer.packString("roundingMode")
            packer.packInt(mc.getRoundingMode.ordinal)
          }
        }

        private[this] val dayOfWeekCodec = new MessagePackBinaryCodec[java.time.DayOfWeek]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.DayOfWeek =
            java.time.DayOfWeek.of(unpacker.unpackInt())

          def encodeUnsafe(packer: MessagePacker, value: java.time.DayOfWeek): Unit =
            packer.packInt(value.getValue)
        }

        private[this] val durationCodec = new MessagePackBinaryCodec[java.time.Duration]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Duration = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 2) decodeError("Expected Duration map with 2 fields")
            var seconds: Long = 0
            var nanos: Int    = 0
            var idx           = 0
            while (idx < 2) {
              val key = unpacker.unpackString()
              key match {
                case "seconds" => seconds = unpacker.unpackLong()
                case "nanos"   => nanos = unpacker.unpackInt()
                case _         => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.Duration.ofSeconds(seconds, nanos)
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.Duration): Unit = {
            packer.packMapHeader(2)
            packer.packString("seconds")
            packer.packLong(value.getSeconds)
            packer.packString("nanos")
            packer.packInt(value.getNano)
          }
        }

        private[this] val instantCodec = new MessagePackBinaryCodec[java.time.Instant]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Instant = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 2) decodeError("Expected Instant map with 2 fields")
            var epochSecond: Long = 0
            var nano: Int         = 0
            var idx               = 0
            while (idx < 2) {
              val key = unpacker.unpackString()
              key match {
                case "epochSecond" => epochSecond = unpacker.unpackLong()
                case "nano"        => nano = unpacker.unpackInt()
                case _             => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.Instant.ofEpochSecond(epochSecond, nano)
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.Instant): Unit = {
            packer.packMapHeader(2)
            packer.packString("epochSecond")
            packer.packLong(value.getEpochSecond)
            packer.packString("nano")
            packer.packInt(value.getNano)
          }
        }

        private[this] val localDateCodec = new MessagePackBinaryCodec[java.time.LocalDate]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDate = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 3) decodeError("Expected LocalDate map with 3 fields")
            var year: Int  = 0
            var month: Int = 0
            var day: Int   = 0
            var idx        = 0
            while (idx < 3) {
              val key = unpacker.unpackString()
              key match {
                case "year"  => year = unpacker.unpackInt()
                case "month" => month = unpacker.unpackInt()
                case "day"   => day = unpacker.unpackInt()
                case _       => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.LocalDate.of(year, month, day)
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.LocalDate): Unit = {
            packer.packMapHeader(3)
            packer.packString("year")
            packer.packInt(value.getYear)
            packer.packString("month")
            packer.packInt(value.getMonthValue)
            packer.packString("day")
            packer.packInt(value.getDayOfMonth)
          }
        }

        private[this] val localDateTimeCodec = new MessagePackBinaryCodec[java.time.LocalDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDateTime = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 7) decodeError("Expected LocalDateTime map with 7 fields")
            var year: Int   = 0
            var month: Int  = 0
            var day: Int    = 0
            var hour: Int   = 0
            var minute: Int = 0
            var second: Int = 0
            var nano: Int   = 0
            var idx         = 0
            while (idx < 7) {
              val key = unpacker.unpackString()
              key match {
                case "year"   => year = unpacker.unpackInt()
                case "month"  => month = unpacker.unpackInt()
                case "day"    => day = unpacker.unpackInt()
                case "hour"   => hour = unpacker.unpackInt()
                case "minute" => minute = unpacker.unpackInt()
                case "second" => second = unpacker.unpackInt()
                case "nano"   => nano = unpacker.unpackInt()
                case _        => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano)
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.LocalDateTime): Unit = {
            packer.packMapHeader(7)
            packer.packString("year")
            packer.packInt(value.getYear)
            packer.packString("month")
            packer.packInt(value.getMonthValue)
            packer.packString("day")
            packer.packInt(value.getDayOfMonth)
            packer.packString("hour")
            packer.packInt(value.getHour)
            packer.packString("minute")
            packer.packInt(value.getMinute)
            packer.packString("second")
            packer.packInt(value.getSecond)
            packer.packString("nano")
            packer.packInt(value.getNano)
          }
        }

        private[this] val localTimeCodec = new MessagePackBinaryCodec[java.time.LocalTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalTime = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 4) decodeError("Expected LocalTime map with 4 fields")
            var hour: Int   = 0
            var minute: Int = 0
            var second: Int = 0
            var nano: Int   = 0
            var idx         = 0
            while (idx < 4) {
              val key = unpacker.unpackString()
              key match {
                case "hour"   => hour = unpacker.unpackInt()
                case "minute" => minute = unpacker.unpackInt()
                case "second" => second = unpacker.unpackInt()
                case "nano"   => nano = unpacker.unpackInt()
                case _        => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.LocalTime.of(hour, minute, second, nano)
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.LocalTime): Unit = {
            packer.packMapHeader(4)
            packer.packString("hour")
            packer.packInt(value.getHour)
            packer.packString("minute")
            packer.packInt(value.getMinute)
            packer.packString("second")
            packer.packInt(value.getSecond)
            packer.packString("nano")
            packer.packInt(value.getNano)
          }
        }

        private[this] val monthCodec = new MessagePackBinaryCodec[java.time.Month]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Month =
            java.time.Month.of(unpacker.unpackInt())

          def encodeUnsafe(packer: MessagePacker, value: java.time.Month): Unit =
            packer.packInt(value.getValue)
        }

        private[this] val monthDayCodec = new MessagePackBinaryCodec[java.time.MonthDay]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.MonthDay = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 2) decodeError("Expected MonthDay map with 2 fields")
            var month: Int = 0
            var day: Int   = 0
            var idx        = 0
            while (idx < 2) {
              val key = unpacker.unpackString()
              key match {
                case "month" => month = unpacker.unpackInt()
                case "day"   => day = unpacker.unpackInt()
                case _       => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.MonthDay.of(month, day)
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.MonthDay): Unit = {
            packer.packMapHeader(2)
            packer.packString("month")
            packer.packInt(value.getMonthValue)
            packer.packString("day")
            packer.packInt(value.getDayOfMonth)
          }
        }

        private[this] val offsetDateTimeCodec = new MessagePackBinaryCodec[java.time.OffsetDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetDateTime = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 8) decodeError("Expected OffsetDateTime map with 8 fields")
            var year: Int         = 0
            var month: Int        = 0
            var day: Int          = 0
            var hour: Int         = 0
            var minute: Int       = 0
            var second: Int       = 0
            var nano: Int         = 0
            var offsetSecond: Int = 0
            var idx               = 0
            while (idx < 8) {
              val key = unpacker.unpackString()
              key match {
                case "year"         => year = unpacker.unpackInt()
                case "month"        => month = unpacker.unpackInt()
                case "day"          => day = unpacker.unpackInt()
                case "hour"         => hour = unpacker.unpackInt()
                case "minute"       => minute = unpacker.unpackInt()
                case "second"       => second = unpacker.unpackInt()
                case "nano"         => nano = unpacker.unpackInt()
                case "offsetSecond" => offsetSecond = unpacker.unpackInt()
                case _              => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.OffsetDateTime
              .of(year, month, day, hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.OffsetDateTime): Unit = {
            packer.packMapHeader(8)
            packer.packString("year")
            packer.packInt(value.getYear)
            packer.packString("month")
            packer.packInt(value.getMonthValue)
            packer.packString("day")
            packer.packInt(value.getDayOfMonth)
            packer.packString("hour")
            packer.packInt(value.getHour)
            packer.packString("minute")
            packer.packInt(value.getMinute)
            packer.packString("second")
            packer.packInt(value.getSecond)
            packer.packString("nano")
            packer.packInt(value.getNano)
            packer.packString("offsetSecond")
            packer.packInt(value.getOffset.getTotalSeconds)
          }
        }

        private[this] val offsetTimeCodec = new MessagePackBinaryCodec[java.time.OffsetTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetTime = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 5) decodeError("Expected OffsetTime map with 5 fields")
            var hour: Int         = 0
            var minute: Int       = 0
            var second: Int       = 0
            var nano: Int         = 0
            var offsetSecond: Int = 0
            var idx               = 0
            while (idx < 5) {
              val key = unpacker.unpackString()
              key match {
                case "hour"         => hour = unpacker.unpackInt()
                case "minute"       => minute = unpacker.unpackInt()
                case "second"       => second = unpacker.unpackInt()
                case "nano"         => nano = unpacker.unpackInt()
                case "offsetSecond" => offsetSecond = unpacker.unpackInt()
                case _              => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.OffsetTime.of(hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.OffsetTime): Unit = {
            packer.packMapHeader(5)
            packer.packString("hour")
            packer.packInt(value.getHour)
            packer.packString("minute")
            packer.packInt(value.getMinute)
            packer.packString("second")
            packer.packInt(value.getSecond)
            packer.packString("nano")
            packer.packInt(value.getNano)
            packer.packString("offsetSecond")
            packer.packInt(value.getOffset.getTotalSeconds)
          }
        }

        private[this] val periodCodec = new MessagePackBinaryCodec[java.time.Period]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Period = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 3) decodeError("Expected Period map with 3 fields")
            var years: Int  = 0
            var months: Int = 0
            var days: Int   = 0
            var idx         = 0
            while (idx < 3) {
              val key = unpacker.unpackString()
              key match {
                case "years"  => years = unpacker.unpackInt()
                case "months" => months = unpacker.unpackInt()
                case "days"   => days = unpacker.unpackInt()
                case _        => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.Period.of(years, months, days)
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.Period): Unit = {
            packer.packMapHeader(3)
            packer.packString("years")
            packer.packInt(value.getYears)
            packer.packString("months")
            packer.packInt(value.getMonths)
            packer.packString("days")
            packer.packInt(value.getDays)
          }
        }

        private[this] val yearCodec = new MessagePackBinaryCodec[java.time.Year]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Year =
            java.time.Year.of(unpacker.unpackInt())

          def encodeUnsafe(packer: MessagePacker, value: java.time.Year): Unit =
            packer.packInt(value.getValue)
        }

        private[this] val yearMonthCodec = new MessagePackBinaryCodec[java.time.YearMonth]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.YearMonth = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 2) decodeError("Expected YearMonth map with 2 fields")
            var year: Int  = 0
            var month: Int = 0
            var idx        = 0
            while (idx < 2) {
              val key = unpacker.unpackString()
              key match {
                case "year"  => year = unpacker.unpackInt()
                case "month" => month = unpacker.unpackInt()
                case _       => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.YearMonth.of(year, month)
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.YearMonth): Unit = {
            packer.packMapHeader(2)
            packer.packString("year")
            packer.packInt(value.getYear)
            packer.packString("month")
            packer.packInt(value.getMonthValue)
          }
        }

        private[this] val zoneIdCodec = new MessagePackBinaryCodec[java.time.ZoneId]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneId =
            java.time.ZoneId.of(unpacker.unpackString())

          def encodeUnsafe(packer: MessagePacker, value: java.time.ZoneId): Unit =
            packer.packString(value.toString)
        }

        private[this] val zoneOffsetCodec = new MessagePackBinaryCodec[java.time.ZoneOffset]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt())

          def encodeUnsafe(packer: MessagePacker, value: java.time.ZoneOffset): Unit =
            packer.packInt(value.getTotalSeconds)
        }

        private[this] val zonedDateTimeCodec = new MessagePackBinaryCodec[java.time.ZonedDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZonedDateTime = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 9) decodeError("Expected ZonedDateTime map with 9 fields")
            var year: Int         = 0
            var month: Int        = 0
            var day: Int          = 0
            var hour: Int         = 0
            var minute: Int       = 0
            var second: Int       = 0
            var nano: Int         = 0
            var offsetSecond: Int = 0
            var zoneId: String    = null
            var idx               = 0
            while (idx < 9) {
              val key = unpacker.unpackString()
              key match {
                case "year"         => year = unpacker.unpackInt()
                case "month"        => month = unpacker.unpackInt()
                case "day"          => day = unpacker.unpackInt()
                case "hour"         => hour = unpacker.unpackInt()
                case "minute"       => minute = unpacker.unpackInt()
                case "second"       => second = unpacker.unpackInt()
                case "nano"         => nano = unpacker.unpackInt()
                case "offsetSecond" => offsetSecond = unpacker.unpackInt()
                case "zoneId"       => zoneId = unpacker.unpackString()
                case _              => decodeError(s"Unexpected field: $key")
              }
              idx += 1
            }
            java.time.ZonedDateTime.ofInstant(
              java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano),
              java.time.ZoneOffset.ofTotalSeconds(offsetSecond),
              java.time.ZoneId.of(zoneId)
            )
          }

          def encodeUnsafe(packer: MessagePacker, value: java.time.ZonedDateTime): Unit = {
            packer.packMapHeader(9)
            packer.packString("year")
            packer.packInt(value.getYear)
            packer.packString("month")
            packer.packInt(value.getMonthValue)
            packer.packString("day")
            packer.packInt(value.getDayOfMonth)
            packer.packString("hour")
            packer.packInt(value.getHour)
            packer.packString("minute")
            packer.packInt(value.getMinute)
            packer.packString("second")
            packer.packInt(value.getSecond)
            packer.packString("nano")
            packer.packInt(value.getNano)
            packer.packString("offsetSecond")
            packer.packInt(value.getOffset.getTotalSeconds)
            packer.packString("zoneId")
            packer.packString(value.getZone.toString)
          }
        }

        private[this] val currencyCodec = new MessagePackBinaryCodec[java.util.Currency]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.util.Currency =
            java.util.Currency.getInstance(unpacker.unpackString())

          def encodeUnsafe(packer: MessagePacker, value: java.util.Currency): Unit =
            packer.packString(value.getCurrencyCode)
        }

        private[this] val uuidCodec = new MessagePackBinaryCodec[java.util.UUID]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.util.UUID = {
            val len = unpacker.unpackBinaryHeader()
            if (len != 16) decodeError(s"Expected UUID binary with 16 bytes, got $len")
            val bs = new Array[Byte](16)
            unpacker.readPayload(bs)
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

          def encodeUnsafe(packer: MessagePacker, value: java.util.UUID): Unit = {
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
            packer.packBinaryHeader(16)
            packer.writePayload(bs)
          }
        }

        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): MessagePackBinaryCodec[A] = {
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
              val codecs  = new Array[MessagePackBinaryCodec[?]](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                idx += 1
              }
              new MessagePackBinaryCodec[A]() {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs
                private[this] val caseNames     = cases.map(_.name)

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val mapSize = unpacker.unpackMapHeader()
                  if (mapSize != 1) decodeError(s"Expected variant map with 1 field, got $mapSize")
                  val caseName = unpacker.unpackString()
                  var idx      = 0
                  while (idx < caseNames.length && caseNames(idx) != caseName) idx += 1
                  if (idx >= caseNames.length) decodeError(s"Unknown variant case: $caseName")
                  try caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].decodeUnsafe(unpacker)
                  catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(caseName), error)
                  }
                }

                def encodeUnsafe(packer: MessagePacker, value: A): Unit = {
                  val idx = discriminator.discriminate(value)
                  packer.packMapHeader(1)
                  packer.packString(caseNames(idx))
                  caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].encodeUnsafe(packer, value)
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec   = deriveCodec(sequence.element).asInstanceOf[MessagePackBinaryCodec[Elem]]
              codec.valueType match {
                case MessagePackBinaryCodec.booleanType
                    if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new MessagePackBinaryCodec[Col[Boolean]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[MessagePackBinaryCodec[Boolean]]

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Boolean] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newBooleanBuilder()
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addBoolean(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultBoolean(builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Boolean]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
                case MessagePackBinaryCodec.byteType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new MessagePackBinaryCodec[Col[Byte]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[MessagePackBinaryCodec[Byte]]

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Byte] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newByteBuilder()
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addByte(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultByte(builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Byte]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
                case MessagePackBinaryCodec.charType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new MessagePackBinaryCodec[Col[Char]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[MessagePackBinaryCodec[Char]]

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Char] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newCharBuilder()
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addChar(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultChar(builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Char]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
                case MessagePackBinaryCodec.shortType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new MessagePackBinaryCodec[Col[Short]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[MessagePackBinaryCodec[Short]]

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Short] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newShortBuilder()
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addShort(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultShort(builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Short]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
                case MessagePackBinaryCodec.floatType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new MessagePackBinaryCodec[Col[Float]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[MessagePackBinaryCodec[Float]]

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Float] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newFloatBuilder()
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addFloat(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultFloat(builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Float]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
                case MessagePackBinaryCodec.intType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new MessagePackBinaryCodec[Col[Int]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[MessagePackBinaryCodec[Int]]

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Int] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newIntBuilder()
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addInt(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultInt(builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Int]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
                case MessagePackBinaryCodec.doubleType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new MessagePackBinaryCodec[Col[Double]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[MessagePackBinaryCodec[Double]]

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Double] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newDoubleBuilder()
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addDouble(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultDouble(builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Double]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
                case MessagePackBinaryCodec.longType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new MessagePackBinaryCodec[Col[Long]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[MessagePackBinaryCodec[Long]]

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Long] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newLongBuilder()
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addLong(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultLong(builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Long]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
                case _ =>
                  new MessagePackBinaryCodec[Col[Elem]]() {
                    private[this] val deconstructor = binding.deconstructor
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec

                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Elem] = {
                      val size = unpacker.unpackArrayHeader()
                      if (size > MessagePackBinaryCodec.maxCollectionSize)
                        decodeError(
                          s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                        )
                      val builder = constructor.newObjectBuilder[Elem](size)
                      var idx     = 0
                      while (idx < size) {
                        try constructor.addObject(builder, elementCodec.decodeUnsafe(unpacker))
                        catch {
                          case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                        }
                        idx += 1
                      }
                      constructor.resultObject[Elem](builder)
                    }

                    def encodeUnsafe(packer: MessagePacker, value: Col[Elem]): Unit = {
                      val size = deconstructor.size(value)
                      packer.packArrayHeader(size)
                      val it = deconstructor.deconstruct(value)
                      while (it.hasNext) elementCodec.encodeUnsafe(packer, it.next())
                    }
                  }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val codec1  = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[Key]]
              val codec2  = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[Value]]
              new MessagePackBinaryCodec[Map[Key, Value]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val keyCodec      = codec1
                private[this] val valueCodec    = codec2
                private[this] val keyReflect    = map.key.asInstanceOf[Reflect.Bound[Key]]

                def decodeUnsafe(unpacker: MessageUnpacker): Map[Key, Value] = {
                  val size = unpacker.unpackMapHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize)
                    decodeError(
                      s"Expected map size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                    )
                  val builder = constructor.newObjectBuilder[Key, Value](size)
                  var idx     = 0
                  while (idx < size) {
                    val k =
                      try keyCodec.decodeUnsafe(unpacker)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                      }
                    val v =
                      try valueCodec.decodeUnsafe(unpacker)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), error)
                      }
                    constructor.addObject(builder, k, v)
                    idx += 1
                  }
                  constructor.resultObject[Key, Value](builder)
                }

                def encodeUnsafe(packer: MessagePacker, value: Map[Key, Value]): Unit = {
                  val size = deconstructor.size(value)
                  packer.packMapHeader(size)
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) {
                    val kv = it.next()
                    keyCodec.encodeUnsafe(packer, deconstructor.getKey(kv))
                    valueCodec.encodeUnsafe(packer, deconstructor.getValue(kv))
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
                codecs = new Array[MessagePackBinaryCodec[?]](len)
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
              new MessagePackBinaryCodec[A]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val usedRegisters = offset
                private[this] val fieldCodecs   = codecs
                private[this] val fieldNames    = fields.map(_.name)

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val mapSize = unpacker.unpackMapHeader()
                  if (mapSize != fieldCodecs.length)
                    decodeError(s"Expected record map with ${fieldCodecs.length} fields, got $mapSize")
                  val regs = Registers(usedRegisters)
                  val len  = fieldCodecs.length
                  var idx  = 0
                  try {
                    while (idx < len) {
                      val fieldName = unpacker.unpackString()
                      var fieldIdx  = 0
                      while (fieldIdx < fieldNames.length && fieldNames(fieldIdx) != fieldName) fieldIdx += 1
                      if (fieldIdx >= fieldNames.length) decodeError(s"Unknown field: $fieldName")

                      var fieldOffset = 0L
                      var i           = 0
                      while (i < fieldIdx) {
                        fieldOffset += fieldCodecs(i).valueOffset
                        i += 1
                      }

                      val codec = fieldCodecs(fieldIdx)
                      codec.valueType match {
                        case MessagePackBinaryCodec.objectType =>
                          regs.setObject(
                            fieldOffset,
                            codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.intType =>
                          regs
                            .setInt(fieldOffset, codec.asInstanceOf[MessagePackBinaryCodec[Int]].decodeUnsafe(unpacker))
                        case MessagePackBinaryCodec.longType =>
                          regs.setLong(
                            fieldOffset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Long]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.floatType =>
                          regs.setFloat(
                            fieldOffset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Float]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.doubleType =>
                          regs.setDouble(
                            fieldOffset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Double]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.booleanType =>
                          regs.setBoolean(
                            fieldOffset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.byteType =>
                          regs.setByte(
                            fieldOffset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Byte]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.charType =>
                          regs.setChar(
                            fieldOffset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Char]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.shortType =>
                          regs.setShort(
                            fieldOffset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Short]].decodeUnsafe(unpacker)
                          )
                        case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].decodeUnsafe(unpacker)
                      }
                      idx += 1
                    }
                    constructor.construct(regs, 0)
                  } catch {
                    case error if NonFatal(error) =>
                      val fieldName = if (idx < fieldNames.length) fieldNames(idx) else s"field_$idx"
                      decodeError(new DynamicOptic.Node.Field(fieldName), error)
                  }
                }

                def encodeUnsafe(packer: MessagePacker, value: A): Unit = {
                  val regs      = Registers(usedRegisters)
                  var regOffset = 0L
                  deconstructor.deconstruct(regs, regOffset, value)
                  val len = fieldCodecs.length
                  packer.packMapHeader(len)
                  var idx = 0
                  while (idx < len) {
                    packer.packString(fieldNames(idx))
                    val codec = fieldCodecs(idx)
                    codec.valueType match {
                      case MessagePackBinaryCodec.objectType =>
                        codec
                          .asInstanceOf[MessagePackBinaryCodec[AnyRef]]
                          .encodeUnsafe(packer, regs.getObject(regOffset))
                      case MessagePackBinaryCodec.intType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Int]].encodeUnsafe(packer, regs.getInt(regOffset))
                      case MessagePackBinaryCodec.longType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Long]].encodeUnsafe(packer, regs.getLong(regOffset))
                      case MessagePackBinaryCodec.floatType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Float]].encodeUnsafe(packer, regs.getFloat(regOffset))
                      case MessagePackBinaryCodec.doubleType =>
                        codec
                          .asInstanceOf[MessagePackBinaryCodec[Double]]
                          .encodeUnsafe(packer, regs.getDouble(regOffset))
                      case MessagePackBinaryCodec.booleanType =>
                        codec
                          .asInstanceOf[MessagePackBinaryCodec[Boolean]]
                          .encodeUnsafe(packer, regs.getBoolean(regOffset))
                      case MessagePackBinaryCodec.byteType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Byte]].encodeUnsafe(packer, regs.getByte(regOffset))
                      case MessagePackBinaryCodec.charType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Char]].encodeUnsafe(packer, regs.getChar(regOffset))
                      case MessagePackBinaryCodec.shortType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Short]].encodeUnsafe(packer, regs.getShort(regOffset))
                      case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].encodeUnsafe(packer, ())
                    }
                    regOffset += codec.valueOffset
                    idx += 1
                  }
                }
              }
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val codec   = deriveCodec(wrapper.wrapped).asInstanceOf[MessagePackBinaryCodec[Wrapped]]
              new MessagePackBinaryCodec[A](wrapper.wrapperPrimitiveType.fold(MessagePackBinaryCodec.objectType) {
                case _: PrimitiveType.Boolean   => MessagePackBinaryCodec.booleanType
                case _: PrimitiveType.Byte      => MessagePackBinaryCodec.byteType
                case _: PrimitiveType.Char      => MessagePackBinaryCodec.charType
                case _: PrimitiveType.Short     => MessagePackBinaryCodec.shortType
                case _: PrimitiveType.Float     => MessagePackBinaryCodec.floatType
                case _: PrimitiveType.Int       => MessagePackBinaryCodec.intType
                case _: PrimitiveType.Double    => MessagePackBinaryCodec.doubleType
                case _: PrimitiveType.Long      => MessagePackBinaryCodec.longType
                case _: PrimitiveType.Unit.type => MessagePackBinaryCodec.unitType
                case _                          => MessagePackBinaryCodec.objectType
              }) {
                private[this] val unwrap       = binding.unwrap
                private[this] val wrap         = binding.wrap
                private[this] val wrappedCodec = codec

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val wrapped =
                    try wrappedCodec.decodeUnsafe(unpacker)
                    catch {
                      case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                    }
                  wrap(wrapped) match {
                    case Right(x)  => x
                    case Left(err) => decodeError(err)
                  }
                }

                def encodeUnsafe(packer: MessagePacker, value: A): Unit =
                  wrappedCodec.encodeUnsafe(packer, unwrap(value))
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
            else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          }
        }.asInstanceOf[MessagePackBinaryCodec[A]]

        private[this] val dynamicValueCodec = new MessagePackBinaryCodec[DynamicValue]() {
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

          def decodeUnsafe(unpacker: MessageUnpacker): DynamicValue = {
            val mapSize = unpacker.unpackMapHeader()
            if (mapSize != 1) decodeError(s"Expected DynamicValue variant map with 1 field, got $mapSize")
            val caseName = unpacker.unpackString()
            caseName match {
              case "Primitive" =>
                try {
                  val innerMapSize = unpacker.unpackMapHeader()
                  if (innerMapSize != 1) decodeError("Expected Primitive map with 1 field")
                  val valueKey = unpacker.unpackString()
                  if (valueKey != "value") decodeError(s"Expected 'value' field, got $valueKey")
                  val typeMapSize = unpacker.unpackMapHeader()
                  if (typeMapSize != 1) decodeError("Expected primitive type map with 1 field")
                  val typeName = unpacker.unpackString()
                  try {
                    new DynamicValue.Primitive(typeName match {
                      case "Unit" =>
                        unpacker.unpackNil()
                        PrimitiveValue.Unit
                      case "Boolean"       => new PrimitiveValue.Boolean(booleanCodec.decodeUnsafe(unpacker))
                      case "Byte"          => new PrimitiveValue.Byte(byteCodec.decodeUnsafe(unpacker))
                      case "Short"         => new PrimitiveValue.Short(shortCodec.decodeUnsafe(unpacker))
                      case "Int"           => new PrimitiveValue.Int(intCodec.decodeUnsafe(unpacker))
                      case "Long"          => new PrimitiveValue.Long(longCodec.decodeUnsafe(unpacker))
                      case "Float"         => new PrimitiveValue.Float(floatCodec.decodeUnsafe(unpacker))
                      case "Double"        => new PrimitiveValue.Double(doubleCodec.decodeUnsafe(unpacker))
                      case "Char"          => new PrimitiveValue.Char(charCodec.decodeUnsafe(unpacker))
                      case "String"        => new PrimitiveValue.String(stringCodec.decodeUnsafe(unpacker))
                      case "BigInt"        => new PrimitiveValue.BigInt(bigIntCodec.decodeUnsafe(unpacker))
                      case "BigDecimal"    => new PrimitiveValue.BigDecimal(bigDecimalCodec.decodeUnsafe(unpacker))
                      case "DayOfWeek"     => new PrimitiveValue.DayOfWeek(dayOfWeekCodec.decodeUnsafe(unpacker))
                      case "Duration"      => new PrimitiveValue.Duration(durationCodec.decodeUnsafe(unpacker))
                      case "Instant"       => new PrimitiveValue.Instant(instantCodec.decodeUnsafe(unpacker))
                      case "LocalDate"     => new PrimitiveValue.LocalDate(localDateCodec.decodeUnsafe(unpacker))
                      case "LocalDateTime" =>
                        new PrimitiveValue.LocalDateTime(localDateTimeCodec.decodeUnsafe(unpacker))
                      case "LocalTime"      => new PrimitiveValue.LocalTime(localTimeCodec.decodeUnsafe(unpacker))
                      case "Month"          => new PrimitiveValue.Month(monthCodec.decodeUnsafe(unpacker))
                      case "MonthDay"       => new PrimitiveValue.MonthDay(monthDayCodec.decodeUnsafe(unpacker))
                      case "OffsetDateTime" =>
                        new PrimitiveValue.OffsetDateTime(offsetDateTimeCodec.decodeUnsafe(unpacker))
                      case "OffsetTime"    => new PrimitiveValue.OffsetTime(offsetTimeCodec.decodeUnsafe(unpacker))
                      case "Period"        => new PrimitiveValue.Period(periodCodec.decodeUnsafe(unpacker))
                      case "Year"          => new PrimitiveValue.Year(yearCodec.decodeUnsafe(unpacker))
                      case "YearMonth"     => new PrimitiveValue.YearMonth(yearMonthCodec.decodeUnsafe(unpacker))
                      case "ZoneId"        => new PrimitiveValue.ZoneId(zoneIdCodec.decodeUnsafe(unpacker))
                      case "ZoneOffset"    => new PrimitiveValue.ZoneOffset(zoneOffsetCodec.decodeUnsafe(unpacker))
                      case "ZonedDateTime" =>
                        new PrimitiveValue.ZonedDateTime(zonedDateTimeCodec.decodeUnsafe(unpacker))
                      case "Currency" => new PrimitiveValue.Currency(currencyCodec.decodeUnsafe(unpacker))
                      case "UUID"     => new PrimitiveValue.UUID(uuidCodec.decodeUnsafe(unpacker))
                      case _          => decodeError(s"Unknown primitive type: $typeName")
                    })
                  } catch {
                    case error if NonFatal(error) => decodeError(spanValue, error)
                  }
                } catch {
                  case error if NonFatal(error) => decodeError(spanPrimitive, error)
                }
              case "Record" =>
                try {
                  val innerMapSize = unpacker.unpackMapHeader()
                  if (innerMapSize != 1) decodeError("Expected Record map with 1 field")
                  val fieldsKey = unpacker.unpackString()
                  if (fieldsKey != "fields") decodeError(s"Expected 'fields' key, got $fieldsKey")
                  val size = unpacker.unpackArrayHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize)
                    decodeError(
                      s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                    )
                  val builder = Vector.newBuilder[(String, DynamicValue)]
                  var idx     = 0
                  while (idx < size) {
                    val entryMapSize = unpacker.unpackMapHeader()
                    if (entryMapSize != 2) decodeError(s"Expected field entry with 2 fields, got $entryMapSize")
                    val k =
                      try {
                        val nameKey = unpacker.unpackString()
                        if (nameKey != "name") decodeError(s"Expected 'name' key, got $nameKey")
                        unpacker.unpackString()
                      } catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), span_1, error)
                      }
                    val v =
                      try {
                        val valueKey = unpacker.unpackString()
                        if (valueKey != "value") decodeError(s"Expected 'value' key, got $valueKey")
                        decodeUnsafe(unpacker)
                      } catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), span_2, error)
                      }
                    builder.addOne((k, v))
                    idx += 1
                  }
                  new DynamicValue.Record(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanRecord, spanFields, error)
                }
              case "Variant" =>
                val innerMapSize = unpacker.unpackMapHeader()
                if (innerMapSize != 2) decodeError("Expected Variant map with 2 fields")
                var variantCaseName: String    = null
                var variantValue: DynamicValue = null
                var idx                        = 0
                while (idx < 2) {
                  val key = unpacker.unpackString()
                  key match {
                    case "caseName" =>
                      try variantCaseName = unpacker.unpackString()
                      catch {
                        case error if NonFatal(error) => decodeError(spanVariant, spanCaseName, error)
                      }
                    case "value" =>
                      try variantValue = decodeUnsafe(unpacker)
                      catch {
                        case error if NonFatal(error) => decodeError(spanVariant, spanValue, error)
                      }
                    case _ => decodeError(s"Unexpected field: $key")
                  }
                  idx += 1
                }
                new DynamicValue.Variant(variantCaseName, variantValue)
              case "Sequence" =>
                try {
                  val innerMapSize = unpacker.unpackMapHeader()
                  if (innerMapSize != 1) decodeError("Expected Sequence map with 1 field")
                  val elementsKey = unpacker.unpackString()
                  if (elementsKey != "elements") decodeError(s"Expected 'elements' key, got $elementsKey")
                  val size = unpacker.unpackArrayHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize)
                    decodeError(
                      s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                    )
                  val builder = Vector.newBuilder[DynamicValue]
                  var idx     = 0
                  while (idx < size) {
                    try builder.addOne(decodeUnsafe(unpacker))
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    idx += 1
                  }
                  new DynamicValue.Sequence(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanSequence, spanElements, error)
                }
              case "Map" =>
                try {
                  val innerMapSize = unpacker.unpackMapHeader()
                  if (innerMapSize != 1) decodeError("Expected Map map with 1 field")
                  val entriesKey = unpacker.unpackString()
                  if (entriesKey != "entries") decodeError(s"Expected 'entries' key, got $entriesKey")
                  val size = unpacker.unpackArrayHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize)
                    decodeError(
                      s"Expected collection size not greater than ${MessagePackBinaryCodec.maxCollectionSize}, got $size"
                    )
                  val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
                  var idx     = 0
                  while (idx < size) {
                    val entryMapSize = unpacker.unpackMapHeader()
                    if (entryMapSize != 2) decodeError(s"Expected entry with 2 fields, got $entryMapSize")
                    val k =
                      try {
                        val keyKey = unpacker.unpackString()
                        if (keyKey != "key") decodeError(s"Expected 'key' key, got $keyKey")
                        decodeUnsafe(unpacker)
                      } catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), span_1, error)
                      }
                    val v =
                      try {
                        val valueKey = unpacker.unpackString()
                        if (valueKey != "value") decodeError(s"Expected 'value' key, got $valueKey")
                        decodeUnsafe(unpacker)
                      } catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(idx), span_2, error)
                      }
                    builder.addOne((k, v))
                    idx += 1
                  }
                  new DynamicValue.Map(builder.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanMap, spanEntries, error)
                }
              case _ => decodeError(s"Unknown DynamicValue case: $caseName")
            }
          }

          def encodeUnsafe(packer: MessagePacker, value: DynamicValue): Unit = value match {
            case primitive: DynamicValue.Primitive =>
              packer.packMapHeader(1)
              packer.packString("Primitive")
              packer.packMapHeader(1)
              packer.packString("value")
              packer.packMapHeader(1)
              primitive.value match {
                case _: PrimitiveValue.Unit.type =>
                  packer.packString("Unit")
                  packer.packNil()
                case v: PrimitiveValue.Boolean =>
                  packer.packString("Boolean")
                  booleanCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Byte =>
                  packer.packString("Byte")
                  byteCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Short =>
                  packer.packString("Short")
                  shortCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Int =>
                  packer.packString("Int")
                  intCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Long =>
                  packer.packString("Long")
                  longCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Float =>
                  packer.packString("Float")
                  floatCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Double =>
                  packer.packString("Double")
                  doubleCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Char =>
                  packer.packString("Char")
                  charCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.String =>
                  packer.packString("String")
                  stringCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.BigInt =>
                  packer.packString("BigInt")
                  bigIntCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.BigDecimal =>
                  packer.packString("BigDecimal")
                  bigDecimalCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.DayOfWeek =>
                  packer.packString("DayOfWeek")
                  dayOfWeekCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Duration =>
                  packer.packString("Duration")
                  durationCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Instant =>
                  packer.packString("Instant")
                  instantCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.LocalDate =>
                  packer.packString("LocalDate")
                  localDateCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.LocalDateTime =>
                  packer.packString("LocalDateTime")
                  localDateTimeCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.LocalTime =>
                  packer.packString("LocalTime")
                  localTimeCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Month =>
                  packer.packString("Month")
                  monthCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.MonthDay =>
                  packer.packString("MonthDay")
                  monthDayCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.OffsetDateTime =>
                  packer.packString("OffsetDateTime")
                  offsetDateTimeCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.OffsetTime =>
                  packer.packString("OffsetTime")
                  offsetTimeCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Period =>
                  packer.packString("Period")
                  periodCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Year =>
                  packer.packString("Year")
                  yearCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.YearMonth =>
                  packer.packString("YearMonth")
                  yearMonthCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.ZoneId =>
                  packer.packString("ZoneId")
                  zoneIdCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.ZoneOffset =>
                  packer.packString("ZoneOffset")
                  zoneOffsetCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.ZonedDateTime =>
                  packer.packString("ZonedDateTime")
                  zonedDateTimeCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.Currency =>
                  packer.packString("Currency")
                  currencyCodec.encodeUnsafe(packer, v.value)
                case v: PrimitiveValue.UUID =>
                  packer.packString("UUID")
                  uuidCodec.encodeUnsafe(packer, v.value)
              }
            case record: DynamicValue.Record =>
              packer.packMapHeader(1)
              packer.packString("Record")
              packer.packMapHeader(1)
              packer.packString("fields")
              val fields = record.fields
              packer.packArrayHeader(fields.length)
              val it = fields.iterator
              while (it.hasNext) {
                val kv = it.next()
                packer.packMapHeader(2)
                packer.packString("name")
                packer.packString(kv._1)
                packer.packString("value")
                encodeUnsafe(packer, kv._2)
              }
            case variant: DynamicValue.Variant =>
              packer.packMapHeader(1)
              packer.packString("Variant")
              packer.packMapHeader(2)
              packer.packString("caseName")
              packer.packString(variant.caseName)
              packer.packString("value")
              encodeUnsafe(packer, variant.value)
            case sequence: DynamicValue.Sequence =>
              packer.packMapHeader(1)
              packer.packString("Sequence")
              packer.packMapHeader(1)
              packer.packString("elements")
              val elements = sequence.elements
              packer.packArrayHeader(elements.length)
              val it = elements.iterator
              while (it.hasNext) encodeUnsafe(packer, it.next())
            case map: DynamicValue.Map =>
              packer.packMapHeader(1)
              packer.packString("Map")
              packer.packMapHeader(1)
              packer.packString("entries")
              val entries = map.entries
              packer.packArrayHeader(entries.length)
              val it = entries.iterator
              while (it.hasNext) {
                val kv = it.next()
                packer.packMapHeader(2)
                packer.packString("key")
                encodeUnsafe(packer, kv._1)
                packer.packString("value")
                encodeUnsafe(packer, kv._2)
              }
          }
        }
      }
    )
