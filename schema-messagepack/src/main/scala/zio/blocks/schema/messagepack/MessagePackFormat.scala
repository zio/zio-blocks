package zio.blocks.schema.messagepack

import org.msgpack.core.{MessagePacker, MessageUnpacker}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema._
import zio.blocks.schema.PrimitiveValue
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
            override def initialValue(): java.util.HashMap[TypeName[?], Array[MessagePackBinaryCodec[?]]] =
              new java.util.HashMap
          }

        // Primitive codecs definitions
        private[this] val unitCodec = new MessagePackBinaryCodec[Unit](MessagePackBinaryCodec.unitType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Unit    = unpacker.unpackNil()
          def encode(value: Unit, packer: MessagePacker): Unit = packer.packNil()
        }

        private[this] val booleanCodec = new MessagePackBinaryCodec[Boolean](MessagePackBinaryCodec.booleanType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Boolean    = unpacker.unpackBoolean()
          def encode(value: Boolean, packer: MessagePacker): Unit = packer.packBoolean(value)
        }

        private[this] val byteCodec = new MessagePackBinaryCodec[Byte](MessagePackBinaryCodec.byteType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Byte    = unpacker.unpackByte()
          def encode(value: Byte, packer: MessagePacker): Unit = packer.packByte(value)
        }

        private[this] val shortCodec = new MessagePackBinaryCodec[Short](MessagePackBinaryCodec.shortType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Short    = unpacker.unpackShort()
          def encode(value: Short, packer: MessagePacker): Unit = packer.packShort(value)
        }

        private[this] val intCodec = new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Int    = unpacker.unpackInt()
          def encode(value: Int, packer: MessagePacker): Unit = packer.packInt(value)
        }

        private[this] val longCodec = new MessagePackBinaryCodec[Long](MessagePackBinaryCodec.longType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Long    = unpacker.unpackLong()
          def encode(value: Long, packer: MessagePacker): Unit = packer.packLong(value)
        }

        private[this] val floatCodec = new MessagePackBinaryCodec[Float](MessagePackBinaryCodec.floatType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Float    = unpacker.unpackFloat()
          def encode(value: Float, packer: MessagePacker): Unit = packer.packFloat(value)
        }

        private[this] val doubleCodec = new MessagePackBinaryCodec[Double](MessagePackBinaryCodec.doubleType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Double    = unpacker.unpackDouble()
          def encode(value: Double, packer: MessagePacker): Unit = packer.packDouble(value)
        }

        private[this] val charCodec = new MessagePackBinaryCodec[Char](MessagePackBinaryCodec.charType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Char = {
            val x = unpacker.unpackInt()
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError("Expected Char")
          }
          def encode(value: Char, packer: MessagePacker): Unit = packer.packInt(value)
        }

        private[this] val stringCodec = new MessagePackBinaryCodec[String]() {
          def decodeUnsafe(unpacker: MessageUnpacker): String    = unpacker.unpackString()
          def encode(value: String, packer: MessagePacker): Unit = packer.packString(value)
        }

        private[this] val bigIntCodec = new MessagePackBinaryCodec[BigInt]() {
          def decodeUnsafe(unpacker: MessageUnpacker): BigInt = {
            val len = unpacker.unpackBinaryHeader()
            if (len > MessagePackBinaryCodec.maxBinarySize) {
              decodeError(s"Binary size $len exceeds maximum allowed ${MessagePackBinaryCodec.maxBinarySize}")
            }
            val bytes = new Array[Byte](len)
            unpacker.readPayload(bytes)
            BigInt(new BigInteger(bytes))
          }
          def encode(value: BigInt, packer: MessagePacker): Unit = {
            val bytes = value.toByteArray
            packer.packBinaryHeader(bytes.length)
            packer.writePayload(bytes)
          }
        }

        private[this] val bigDecimalCodec = new MessagePackBinaryCodec[BigDecimal]() {
          def decodeUnsafe(unpacker: MessageUnpacker): BigDecimal = {
            val size = unpacker.unpackMapHeader()
            if (size != 4) decodeError(s"Expected BigDecimal map with 4 fields, got $size")

            var mantissa: Array[Byte] = null
            var scale                 = 0
            var precision             = 0
            var roundingMode          = 0

            var i = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "mantissa" =>
                  val len = unpacker.unpackBinaryHeader()
                  if (len > MessagePackBinaryCodec.maxBinarySize) {
                    decodeError(s"Binary size $len exceeds maximum allowed ${MessagePackBinaryCodec.maxBinarySize}")
                  }
                  mantissa = new Array[Byte](len)
                  unpacker.readPayload(mantissa)
                case "scale"        => scale = unpacker.unpackInt()
                case "precision"    => precision = unpacker.unpackInt()
                case "roundingMode" => roundingMode = unpacker.unpackInt()
                case _              => unpacker.skipValue()
              }
              i += 1
            }
            val mc = new MathContext(precision, java.math.RoundingMode.valueOf(roundingMode))
            new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
          }
          def encode(value: BigDecimal, packer: MessagePacker): Unit = {
            val bd       = value.underlying
            val mc       = value.mc
            val mantissa = bd.unscaledValue.toByteArray
            packer.packMapHeader(4)
            packer.packString("mantissa")
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

        // Date/Time codecs
        private[this] val dayOfWeekCodec = new MessagePackBinaryCodec[java.time.DayOfWeek]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.DayOfWeek =
            java.time.DayOfWeek.of(unpacker.unpackInt())
          def encode(value: java.time.DayOfWeek, packer: MessagePacker): Unit =
            packer.packInt(value.getValue)
        }

        private[this] val durationCodec = new MessagePackBinaryCodec[java.time.Duration]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Duration = {
            val size    = unpacker.unpackMapHeader()
            var seconds = 0L
            var nanos   = 0
            var i       = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "seconds" => seconds = unpacker.unpackLong()
                case "nanos"   => nanos = unpacker.unpackInt()
                case _         => unpacker.skipValue()
              }
              i += 1
            }
            java.time.Duration.ofSeconds(seconds, nanos)
          }
          def encode(value: java.time.Duration, packer: MessagePacker): Unit = {
            packer.packMapHeader(2)
            packer.packString("seconds")
            packer.packLong(value.getSeconds)
            packer.packString("nanos")
            packer.packInt(value.getNano)
          }
        }

        private[this] val instantCodec = new MessagePackBinaryCodec[java.time.Instant]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Instant = {
            val size        = unpacker.unpackMapHeader()
            var epochSecond = 0L
            var nano        = 0
            var i           = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "epochSecond" => epochSecond = unpacker.unpackLong()
                case "nano"        => nano = unpacker.unpackInt()
                case _             => unpacker.skipValue()
              }
              i += 1
            }
            java.time.Instant.ofEpochSecond(epochSecond, nano)
          }
          def encode(value: java.time.Instant, packer: MessagePacker): Unit = {
            packer.packMapHeader(2)
            packer.packString("epochSecond")
            packer.packLong(value.getEpochSecond)
            packer.packString("nano")
            packer.packInt(value.getNano)
          }
        }

        private[this] val localDateCodec = new MessagePackBinaryCodec[java.time.LocalDate]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDate = {
            val size  = unpacker.unpackMapHeader()
            var year  = 0
            var month = 0
            var day   = 0
            var i     = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "year"  => year = unpacker.unpackInt()
                case "month" => month = unpacker.unpackInt()
                case "day"   => day = unpacker.unpackInt()
                case _       => unpacker.skipValue()
              }
              i += 1
            }
            java.time.LocalDate.of(year, month, day)
          }
          def encode(value: java.time.LocalDate, packer: MessagePacker): Unit = {
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
            val size                                         = unpacker.unpackMapHeader()
            var year, month, day, hour, minute, second, nano = 0
            var i                                            = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "year"   => year = unpacker.unpackInt()
                case "month"  => month = unpacker.unpackInt()
                case "day"    => day = unpacker.unpackInt()
                case "hour"   => hour = unpacker.unpackInt()
                case "minute" => minute = unpacker.unpackInt()
                case "second" => second = unpacker.unpackInt()
                case "nano"   => nano = unpacker.unpackInt()
                case _        => unpacker.skipValue()
              }
              i += 1
            }
            java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano)
          }
          def encode(value: java.time.LocalDateTime, packer: MessagePacker): Unit = {
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
            val size                       = unpacker.unpackMapHeader()
            var hour, minute, second, nano = 0
            var i                          = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "hour"   => hour = unpacker.unpackInt()
                case "minute" => minute = unpacker.unpackInt()
                case "second" => second = unpacker.unpackInt()
                case "nano"   => nano = unpacker.unpackInt()
                case _        => unpacker.skipValue()
              }
              i += 1
            }
            java.time.LocalTime.of(hour, minute, second, nano)
          }
          def encode(value: java.time.LocalTime, packer: MessagePacker): Unit = {
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
          def encode(value: java.time.Month, packer: MessagePacker): Unit =
            packer.packInt(value.getValue)
        }

        private[this] val monthDayCodec = new MessagePackBinaryCodec[java.time.MonthDay]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.MonthDay = {
            val size       = unpacker.unpackMapHeader()
            var month, day = 0
            var i          = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "month" => month = unpacker.unpackInt()
                case "day"   => day = unpacker.unpackInt()
                case _       => unpacker.skipValue()
              }
              i += 1
            }
            java.time.MonthDay.of(month, day)
          }
          def encode(value: java.time.MonthDay, packer: MessagePacker): Unit = {
            packer.packMapHeader(2)
            packer.packString("month")
            packer.packInt(value.getMonthValue)
            packer.packString("day")
            packer.packInt(value.getDayOfMonth)
          }
        }

        private[this] val offsetDateTimeCodec = new MessagePackBinaryCodec[java.time.OffsetDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetDateTime = {
            val size                                                       = unpacker.unpackMapHeader()
            var year, month, day, hour, minute, second, nano, offsetSecond = 0
            var i                                                          = 0
            while (i < size) {
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
                case _              => unpacker.skipValue()
              }
              i += 1
            }
            java.time.OffsetDateTime
              .of(year, month, day, hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }
          def encode(value: java.time.OffsetDateTime, packer: MessagePacker): Unit = {
            packer.packMapHeader(8)
            packer.packString("year"); packer.packInt(value.getYear)
            packer.packString("month"); packer.packInt(value.getMonthValue)
            packer.packString("day"); packer.packInt(value.getDayOfMonth)
            packer.packString("hour"); packer.packInt(value.getHour)
            packer.packString("minute"); packer.packInt(value.getMinute)
            packer.packString("second"); packer.packInt(value.getSecond)
            packer.packString("nano"); packer.packInt(value.getNano)
            packer.packString("offsetSecond"); packer.packInt(value.getOffset.getTotalSeconds)
          }
        }

        private[this] val offsetTimeCodec = new MessagePackBinaryCodec[java.time.OffsetTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetTime = {
            val size                                     = unpacker.unpackMapHeader()
            var hour, minute, second, nano, offsetSecond = 0
            var i                                        = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "hour"         => hour = unpacker.unpackInt()
                case "minute"       => minute = unpacker.unpackInt()
                case "second"       => second = unpacker.unpackInt()
                case "nano"         => nano = unpacker.unpackInt()
                case "offsetSecond" => offsetSecond = unpacker.unpackInt()
                case _              => unpacker.skipValue()
              }
              i += 1
            }
            java.time.OffsetTime.of(hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }
          def encode(value: java.time.OffsetTime, packer: MessagePacker): Unit = {
            packer.packMapHeader(5)
            packer.packString("hour"); packer.packInt(value.getHour)
            packer.packString("minute"); packer.packInt(value.getMinute)
            packer.packString("second"); packer.packInt(value.getSecond)
            packer.packString("nano"); packer.packInt(value.getNano)
            packer.packString("offsetSecond"); packer.packInt(value.getOffset.getTotalSeconds)
          }
        }

        private[this] val periodCodec = new MessagePackBinaryCodec[java.time.Period]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Period = {
            val size                = unpacker.unpackMapHeader()
            var years, months, days = 0
            var i                   = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "years"  => years = unpacker.unpackInt()
                case "months" => months = unpacker.unpackInt()
                case "days"   => days = unpacker.unpackInt()
                case _        => unpacker.skipValue()
              }
              i += 1
            }
            java.time.Period.of(years, months, days)
          }
          def encode(value: java.time.Period, packer: MessagePacker): Unit = {
            packer.packMapHeader(3)
            packer.packString("years"); packer.packInt(value.getYears)
            packer.packString("months"); packer.packInt(value.getMonths)
            packer.packString("days"); packer.packInt(value.getDays)
          }
        }

        private[this] val yearCodec = new MessagePackBinaryCodec[java.time.Year]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Year    = java.time.Year.of(unpacker.unpackInt())
          def encode(value: java.time.Year, packer: MessagePacker): Unit = packer.packInt(value.getValue)
        }

        private[this] val yearMonthCodec = new MessagePackBinaryCodec[java.time.YearMonth]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.YearMonth = {
            val size        = unpacker.unpackMapHeader()
            var year, month = 0
            var i           = 0
            while (i < size) {
              val key = unpacker.unpackString()
              key match {
                case "year"  => year = unpacker.unpackInt()
                case "month" => month = unpacker.unpackInt()
                case _       => unpacker.skipValue()
              }
              i += 1
            }
            java.time.YearMonth.of(year, month)
          }
          def encode(value: java.time.YearMonth, packer: MessagePacker): Unit = {
            packer.packMapHeader(2)
            packer.packString("year"); packer.packInt(value.getYear)
            packer.packString("month"); packer.packInt(value.getMonthValue)
          }
        }

        private[this] val zoneIdCodec = new MessagePackBinaryCodec[java.time.ZoneId]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneId    = java.time.ZoneId.of(unpacker.unpackString())
          def encode(value: java.time.ZoneId, packer: MessagePacker): Unit = packer.packString(value.toString)
        }

        private[this] val zoneOffsetCodec = new MessagePackBinaryCodec[java.time.ZoneOffset]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt())
          def encode(value: java.time.ZoneOffset, packer: MessagePacker): Unit = packer.packInt(value.getTotalSeconds)
        }

        private[this] val zonedDateTimeCodec = new MessagePackBinaryCodec[java.time.ZonedDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZonedDateTime = {
            val size                                                       = unpacker.unpackMapHeader()
            var year, month, day, hour, minute, second, nano, offsetSecond = 0
            var zoneId: String                                             = null
            var i                                                          = 0
            while (i < size) {
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
                case _              => unpacker.skipValue()
              }
              i += 1
            }
            java.time.ZonedDateTime.ofInstant(
              java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano),
              java.time.ZoneOffset.ofTotalSeconds(offsetSecond),
              java.time.ZoneId.of(zoneId)
            )
          }
          def encode(value: java.time.ZonedDateTime, packer: MessagePacker): Unit = {
            packer.packMapHeader(9)
            packer.packString("year"); packer.packInt(value.getYear)
            packer.packString("month"); packer.packInt(value.getMonthValue)
            packer.packString("day"); packer.packInt(value.getDayOfMonth)
            packer.packString("hour"); packer.packInt(value.getHour)
            packer.packString("minute"); packer.packInt(value.getMinute)
            packer.packString("second"); packer.packInt(value.getSecond)
            packer.packString("nano"); packer.packInt(value.getNano)
            packer.packString("offsetSecond"); packer.packInt(value.getOffset.getTotalSeconds)
            packer.packString("zoneId"); packer.packString(value.getZone.toString)
          }
        }

        private[this] val currencyCodec = new MessagePackBinaryCodec[java.util.Currency]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.util.Currency =
            java.util.Currency.getInstance(unpacker.unpackString())
          def encode(value: java.util.Currency, packer: MessagePacker): Unit = packer.packString(value.getCurrencyCode)
        }

        private[this] val uuidCodec = new MessagePackBinaryCodec[java.util.UUID]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.util.UUID = {
            val len = unpacker.unpackBinaryHeader()
            if (len != 16) decodeError(s"Expected UUID with 16 bytes, got $len")
            val bytes = new Array[Byte](16)
            unpacker.readPayload(bytes)
            val hi = (bytes(0) & 0xff).toLong << 56 | (bytes(1) & 0xff).toLong << 48 |
              (bytes(2) & 0xff).toLong << 40 | (bytes(3) & 0xff).toLong << 32 |
              (bytes(4) & 0xff).toLong << 24 | (bytes(5) & 0xff) << 16 |
              (bytes(6) & 0xff) << 8 | (bytes(7) & 0xff)
            val lo = (bytes(8) & 0xff).toLong << 56 | (bytes(9) & 0xff).toLong << 48 |
              (bytes(10) & 0xff).toLong << 40 | (bytes(11) & 0xff).toLong << 32 |
              (bytes(12) & 0xff).toLong << 24 | (bytes(13) & 0xff) << 16 |
              (bytes(14) & 0xff) << 8 | (bytes(15) & 0xff)
            new java.util.UUID(hi, lo)
          }
          def encode(value: java.util.UUID, packer: MessagePacker): Unit = {
            val hi    = value.getMostSignificantBits
            val lo    = value.getLeastSignificantBits
            val bytes = Array(
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
            packer.writePayload(bytes)
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

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val idx = unpacker.unpackInt()
                  if (idx >= 0 && idx < caseCodecs.length) {
                    try caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].decodeUnsafe(unpacker)
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
                    }
                  } else decodeError(s"Expected enum index from 0 to ${caseCodecs.length - 1}, got $idx")
                }

                def encode(value: A, packer: MessagePacker): Unit = {
                  val idx = discriminator.discriminate(value)
                  packer.packInt(idx)
                  caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].encode(value, packer)
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec   = deriveCodec(sequence.element).asInstanceOf[MessagePackBinaryCodec[Elem]]
              new MessagePackBinaryCodec[Col[Elem]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val elementCodec  = codec

                def decodeUnsafe(unpacker: MessageUnpacker): Col[Elem] = {
                  val size = unpacker.unpackArrayHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Collection size $size exceeds maximum allowed ${MessagePackBinaryCodec.maxCollectionSize}"
                    )
                  }
                  val builder = constructor.newObjectBuilder[Elem](size)
                  var i       = 0
                  while (i < size) {
                    try constructor.addObject[Elem](builder, elementCodec.decodeUnsafe(unpacker))
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(i), error)
                    }
                    i += 1
                  }
                  constructor.resultObject(builder)
                }

                def encode(value: Col[Elem], packer: MessagePacker): Unit = {
                  val it     = deconstructor.deconstruct(value)
                  var size   = 0
                  val tempIt = deconstructor.deconstruct(value)
                  while (tempIt.hasNext) { tempIt.next(); size += 1 }
                  packer.packArrayHeader(size)
                  while (it.hasNext) elementCodec.encode(it.next(), packer)
                }
              }.asInstanceOf[MessagePackBinaryCodec[A]]
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding    = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val keyCodec   = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[Key]]
              val valueCodec = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[Value]]

              val isStringKey = map.key.isPrimitive &&
                map.key.asPrimitive.exists(_.primitiveType.isInstanceOf[PrimitiveType.String])

              if (isStringKey) {
                new MessagePackBinaryCodec[Map[Key, Value]]() {
                  private[this] val deconstructor = binding.deconstructor
                  private[this] val constructor   = binding.constructor
                  private[this] val vCodec        = valueCodec

                  def decodeUnsafe(unpacker: MessageUnpacker): Map[Key, Value] = {
                    val size = unpacker.unpackMapHeader()
                    if (size > MessagePackBinaryCodec.maxCollectionSize) {
                      decodeError(s"Map size $size exceeds maximum allowed ${MessagePackBinaryCodec.maxCollectionSize}")
                    }
                    val builder = constructor.newObjectBuilder[Key, Value](size)
                    var i       = 0
                    while (i < size) {
                      try {
                        val key   = unpacker.unpackString().asInstanceOf[Key]
                        val value = vCodec.decodeUnsafe(unpacker)
                        constructor.addObject[Key, Value](builder, key, value)
                      } catch {
                        case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(i), error)
                      }
                      i += 1
                    }
                    constructor.resultObject(builder)
                  }

                  def encode(value: Map[Key, Value], packer: MessagePacker): Unit = {
                    val it     = deconstructor.deconstruct(value)
                    var size   = 0
                    val tempIt = deconstructor.deconstruct(value)
                    while (tempIt.hasNext) { tempIt.next(); size += 1 }
                    packer.packMapHeader(size)
                    while (it.hasNext) {
                      val entry = it.next()
                      packer.packString(deconstructor.getKey(entry).asInstanceOf[String])
                      vCodec.encode(deconstructor.getValue(entry), packer)
                    }
                  }
                }.asInstanceOf[MessagePackBinaryCodec[A]]
              } else {
                new MessagePackBinaryCodec[Map[Key, Value]]() {
                  private[this] val deconstructor = binding.deconstructor
                  private[this] val constructor   = binding.constructor
                  private[this] val kCodec        = keyCodec
                  private[this] val vCodec        = valueCodec

                  def decodeUnsafe(unpacker: MessageUnpacker): Map[Key, Value] = {
                    val size = unpacker.unpackArrayHeader()
                    if (size > MessagePackBinaryCodec.maxCollectionSize) {
                      decodeError(s"Map size $size exceeds maximum allowed ${MessagePackBinaryCodec.maxCollectionSize}")
                    }
                    val builder = constructor.newObjectBuilder[Key, Value](size)
                    var i       = 0
                    while (i < size) {
                      try {
                        unpacker.unpackMapHeader()
                        var key: Key     = null.asInstanceOf[Key]
                        var value: Value = null.asInstanceOf[Value]
                        unpacker.unpackString()
                        key = kCodec.decodeUnsafe(unpacker)
                        unpacker.unpackString()
                        value = vCodec.decodeUnsafe(unpacker)
                        constructor.addObject[Key, Value](builder, key, value)
                      } catch {
                        case error if NonFatal(error) =>
                          decodeError(
                            new DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("<key>"))),
                            error
                          )
                      }
                      i += 1
                    }
                    constructor.resultObject(builder)
                  }

                  def encode(value: Map[Key, Value], packer: MessagePacker): Unit = {
                    val it     = deconstructor.deconstruct(value)
                    var size   = 0
                    val tempIt = deconstructor.deconstruct(value)
                    while (tempIt.hasNext) { tempIt.next(); size += 1 }
                    packer.packArrayHeader(size)
                    while (it.hasNext) {
                      val entry = it.next()
                      packer.packMapHeader(2)
                      packer.packString("_1")
                      kCodec.encode(deconstructor.getKey(entry), packer)
                      packer.packString("_2")
                      vCodec.encode(deconstructor.getValue(entry), packer)
                    }
                  }
                }.asInstanceOf[MessagePackBinaryCodec[A]]
              }
            } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val binding  = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields   = record.fields
              val len      = fields.length
              val typeName = record.typeName

              val cache  = recursiveRecordCache.get()
              val cached = cache.get(typeName)
              if (cached != null) {
                return new MessagePackBinaryCodec[A]() {
                  private[this] val codecsRef                            = cached
                  private[this] var realCodec: MessagePackBinaryCodec[A] = _

                  private def getCodec: MessagePackBinaryCodec[A] = {
                    if (realCodec == null) {
                      realCodec = codecsRef(0).asInstanceOf[MessagePackBinaryCodec[A]]
                    }
                    realCodec
                  }

                  def decodeUnsafe(unpacker: MessageUnpacker): A    = getCodec.decodeUnsafe(unpacker)
                  def encode(value: A, packer: MessagePacker): Unit = getCodec.encode(value, packer)
                }
              }

              val codecsHolder = new Array[MessagePackBinaryCodec[?]](1)
              cache.put(typeName, codecsHolder)

              val codecs       = new Array[MessagePackBinaryCodec[?]](len)
              val fieldNames   = new Array[String](len)
              val fieldMap     = new java.util.HashMap[String, java.lang.Integer](len)
              val fieldOffsets = new Array[Long](len)
              var idx          = 0
              var offset       = 0L
              while (idx < len) {
                val field = fields(idx)
                val codec = deriveCodec(field.value)
                codecs(idx) = codec
                fieldNames(idx) = field.name
                fieldMap.put(field.name, idx)
                fieldOffsets(idx) = offset
                offset = RegisterOffset.add(codec.valueOffset, offset)
                idx += 1
              }

              val codec = new MessagePackBinaryCodec[A]() {
                private[this] val fieldCodecs   = codecs
                private[this] val names         = fieldNames
                private[this] val nameToIndex   = fieldMap
                private[this] val offsets       = fieldOffsets
                private[this] val constructor   = binding.constructor
                private[this] val deconstructor = binding.deconstructor
                private[this] val usedRegisters = offset

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val size      = unpacker.unpackMapHeader()
                  val registers = Registers(usedRegisters)

                  var i = 0
                  while (i < size) {
                    val fieldName = unpacker.unpackString()
                    val fieldIdx  = nameToIndex.get(fieldName)
                    if (fieldIdx != null) {
                      val idx         = fieldIdx.intValue()
                      val fieldOffset = offsets(idx)
                      val fieldCodec  = fieldCodecs(idx)

                      try {
                        fieldCodec.valueType match {
                          case MessagePackBinaryCodec.objectType =>
                            registers.setObject(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].decodeUnsafe(unpacker)
                            )
                          case MessagePackBinaryCodec.booleanType =>
                            registers.setBoolean(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[Boolean]].decodeUnsafe(unpacker)
                            )
                          case MessagePackBinaryCodec.byteType =>
                            registers.setByte(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[Byte]].decodeUnsafe(unpacker)
                            )
                          case MessagePackBinaryCodec.charType =>
                            registers.setChar(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[Char]].decodeUnsafe(unpacker)
                            )
                          case MessagePackBinaryCodec.shortType =>
                            registers.setShort(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[Short]].decodeUnsafe(unpacker)
                            )
                          case MessagePackBinaryCodec.intType =>
                            registers.setInt(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[Int]].decodeUnsafe(unpacker)
                            )
                          case MessagePackBinaryCodec.longType =>
                            registers.setLong(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[Long]].decodeUnsafe(unpacker)
                            )
                          case MessagePackBinaryCodec.floatType =>
                            registers.setFloat(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[Float]].decodeUnsafe(unpacker)
                            )
                          case MessagePackBinaryCodec.doubleType =>
                            registers.setDouble(
                              fieldOffset,
                              fieldCodec.asInstanceOf[MessagePackBinaryCodec[Double]].decodeUnsafe(unpacker)
                            )
                          case _ =>
                            fieldCodec.asInstanceOf[MessagePackBinaryCodec[Unit]].decodeUnsafe(unpacker)
                        }
                      } catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.Field(fieldName), error)
                      }
                    } else {
                      unpacker.skipValue()
                    }
                    i += 1
                  }

                  constructor.construct(registers, 0)
                }

                def encode(value: A, packer: MessagePacker): Unit = {
                  val registers = Registers(usedRegisters)
                  deconstructor.deconstruct(registers, 0, value)

                  packer.packMapHeader(names.length)
                  var i = 0
                  while (i < names.length) {
                    packer.packString(names(i))
                    val fieldCodec  = fieldCodecs(i)
                    val fieldOffset = offsets(i)

                    try {
                      fieldCodec.valueType match {
                        case MessagePackBinaryCodec.objectType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[AnyRef]]
                            .encode(registers.getObject(fieldOffset), packer)
                        case MessagePackBinaryCodec.booleanType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[Boolean]]
                            .encode(registers.getBoolean(fieldOffset), packer)
                        case MessagePackBinaryCodec.byteType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[Byte]]
                            .encode(registers.getByte(fieldOffset), packer)
                        case MessagePackBinaryCodec.charType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[Char]]
                            .encode(registers.getChar(fieldOffset), packer)
                        case MessagePackBinaryCodec.shortType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[Short]]
                            .encode(registers.getShort(fieldOffset), packer)
                        case MessagePackBinaryCodec.intType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[Int]]
                            .encode(registers.getInt(fieldOffset), packer)
                        case MessagePackBinaryCodec.longType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[Long]]
                            .encode(registers.getLong(fieldOffset), packer)
                        case MessagePackBinaryCodec.floatType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[Float]]
                            .encode(registers.getFloat(fieldOffset), packer)
                        case MessagePackBinaryCodec.doubleType =>
                          fieldCodec
                            .asInstanceOf[MessagePackBinaryCodec[Double]]
                            .encode(registers.getDouble(fieldOffset), packer)
                        case _ =>
                          fieldCodec.asInstanceOf[MessagePackBinaryCodec[Unit]].encode((), packer)
                      }
                    } catch {
                      case error if NonFatal(error) => throw error
                    }
                    i += 1
                  }
                }
              }

              codecsHolder(0) = codec
              cache.remove(typeName)
              codec
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val codec   = deriveCodec(wrapper.wrapped).asInstanceOf[MessagePackBinaryCodec[Wrapped]]
              new MessagePackBinaryCodec[A](codec.valueType) {
                private[this] val wrappedCodec = codec
                private[this] val wrap         = binding.wrap
                private[this] val unwrap       = binding.unwrap

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val wrapped = wrappedCodec.decodeUnsafe(unpacker)
                  wrap(wrapped) match {
                    case Right(a) => a
                    case Left(_)  =>
                      try decodeError(s"Expected ${wrapper.typeName.name}")
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.Field("wrapped"), error)
                      }
                  }
                }

                def encode(value: A, packer: MessagePacker): Unit = wrappedCodec.encode(unwrap(value), packer)
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isDynamic) {
            deriveDynamicCodec()
          } else {
            throw new IllegalArgumentException(s"Unsupported reflect type: ${reflect.getClass}")
          }
        }.asInstanceOf[MessagePackBinaryCodec[A]]

        private[this] def deriveDynamicCodec(): MessagePackBinaryCodec[DynamicValue] = {
          lazy val codec: MessagePackBinaryCodec[DynamicValue] = new MessagePackBinaryCodec[DynamicValue]() {
            def decodeUnsafe(unpacker: MessageUnpacker): DynamicValue = {
              val variant = unpacker.unpackInt()
              variant match {
                case 0 => // Primitive
                  val primitiveVariant = unpacker.unpackInt()
                  DynamicValue.Primitive(decodePrimitiveValue(unpacker, primitiveVariant))
                case 1 => // Record
                  val size = unpacker.unpackArrayHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Collection size $size exceeds maximum allowed ${MessagePackBinaryCodec.maxCollectionSize}"
                    )
                  }
                  val fields = Vector.newBuilder[(String, DynamicValue)]
                  var i      = 0
                  while (i < size) {
                    val name  = unpacker.unpackString()
                    val value = codec.decodeUnsafe(unpacker)
                    fields += ((name, value))
                    i += 1
                  }
                  DynamicValue.Record(fields.result())
                case 2 => // Variant
                  val caseName = unpacker.unpackString()
                  val value    = codec.decodeUnsafe(unpacker)
                  DynamicValue.Variant(caseName, value)
                case 3 => // Sequence
                  val size = unpacker.unpackArrayHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Collection size $size exceeds maximum allowed ${MessagePackBinaryCodec.maxCollectionSize}"
                    )
                  }
                  val elements = Vector.newBuilder[DynamicValue]
                  var i        = 0
                  while (i < size) {
                    elements += codec.decodeUnsafe(unpacker)
                    i += 1
                  }
                  DynamicValue.Sequence(elements.result())
                case 4 => // Map
                  val size = unpacker.unpackArrayHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Collection size $size exceeds maximum allowed ${MessagePackBinaryCodec.maxCollectionSize}"
                    )
                  }
                  val entries = Vector.newBuilder[(DynamicValue, DynamicValue)]
                  var i       = 0
                  while (i < size) {
                    val key   = codec.decodeUnsafe(unpacker)
                    val value = codec.decodeUnsafe(unpacker)
                    entries += ((key, value))
                    i += 1
                  }
                  DynamicValue.Map(entries.result())
                case _ =>
                  decodeError(s"Expected enum index from 0 to 4, got $variant")
              }
            }

            def encode(value: DynamicValue, packer: MessagePacker): Unit = value match {
              case DynamicValue.Primitive(pv) =>
                packer.packInt(0)
                encodePrimitiveValue(pv, packer)
              case DynamicValue.Record(fields) =>
                packer.packInt(1)
                packer.packArrayHeader(fields.size)
                fields.foreach { case (name, v) =>
                  packer.packString(name)
                  codec.encode(v, packer)
                }
              case DynamicValue.Variant(caseName, v) =>
                packer.packInt(2)
                packer.packString(caseName)
                codec.encode(v, packer)
              case DynamicValue.Sequence(elements) =>
                packer.packInt(3)
                packer.packArrayHeader(elements.size)
                elements.foreach(codec.encode(_, packer))
              case DynamicValue.Map(entries) =>
                packer.packInt(4)
                packer.packArrayHeader(entries.size)
                entries.foreach { case (k, v) =>
                  codec.encode(k, packer)
                  codec.encode(v, packer)
                }
            }

            private def decodePrimitiveValue(unpacker: MessageUnpacker, variant: Int): PrimitiveValue = variant match {
              case 0  => PrimitiveValue.Unit
              case 1  => PrimitiveValue.Boolean(unpacker.unpackBoolean())
              case 2  => PrimitiveValue.Byte(unpacker.unpackByte())
              case 3  => PrimitiveValue.Short(unpacker.unpackShort())
              case 4  => PrimitiveValue.Int(unpacker.unpackInt())
              case 5  => PrimitiveValue.Long(unpacker.unpackLong())
              case 6  => PrimitiveValue.Float(unpacker.unpackFloat())
              case 7  => PrimitiveValue.Double(unpacker.unpackDouble())
              case 8  => PrimitiveValue.Char(unpacker.unpackInt().toChar)
              case 9  => PrimitiveValue.String(unpacker.unpackString())
              case 10 =>
                val len   = unpacker.unpackBinaryHeader()
                val bytes = new Array[Byte](len)
                unpacker.readPayload(bytes)
                PrimitiveValue.BigInt(scala.math.BigInt(new java.math.BigInteger(bytes)))
              case 11 => PrimitiveValue.BigDecimal(bigDecimalCodec.decodeUnsafe(unpacker))
              case 12 => PrimitiveValue.DayOfWeek(dayOfWeekCodec.decodeUnsafe(unpacker))
              case 13 => PrimitiveValue.Duration(durationCodec.decodeUnsafe(unpacker))
              case 14 => PrimitiveValue.Instant(instantCodec.decodeUnsafe(unpacker))
              case 15 => PrimitiveValue.LocalDate(localDateCodec.decodeUnsafe(unpacker))
              case 16 => PrimitiveValue.LocalDateTime(localDateTimeCodec.decodeUnsafe(unpacker))
              case 17 => PrimitiveValue.LocalTime(localTimeCodec.decodeUnsafe(unpacker))
              case 18 => PrimitiveValue.Month(monthCodec.decodeUnsafe(unpacker))
              case 19 => PrimitiveValue.MonthDay(monthDayCodec.decodeUnsafe(unpacker))
              case 20 => PrimitiveValue.OffsetDateTime(offsetDateTimeCodec.decodeUnsafe(unpacker))
              case 21 => PrimitiveValue.OffsetTime(offsetTimeCodec.decodeUnsafe(unpacker))
              case 22 => PrimitiveValue.Period(periodCodec.decodeUnsafe(unpacker))
              case 23 => PrimitiveValue.Year(yearCodec.decodeUnsafe(unpacker))
              case 24 => PrimitiveValue.YearMonth(yearMonthCodec.decodeUnsafe(unpacker))
              case 25 => PrimitiveValue.ZoneId(zoneIdCodec.decodeUnsafe(unpacker))
              case 26 => PrimitiveValue.ZoneOffset(zoneOffsetCodec.decodeUnsafe(unpacker))
              case 27 => PrimitiveValue.ZonedDateTime(zonedDateTimeCodec.decodeUnsafe(unpacker))
              case 28 => PrimitiveValue.Currency(currencyCodec.decodeUnsafe(unpacker))
              case 29 => PrimitiveValue.UUID(uuidCodec.decodeUnsafe(unpacker))
              case _  => decodeError(s"Expected enum index from 0 to 29, got $variant")
            }

            private def encodePrimitiveValue(pv: PrimitiveValue, packer: MessagePacker): Unit = pv match {
              case PrimitiveValue.Unit       => packer.packInt(0)
              case PrimitiveValue.Boolean(v) => packer.packInt(1); packer.packBoolean(v)
              case PrimitiveValue.Byte(v)    => packer.packInt(2); packer.packByte(v)
              case PrimitiveValue.Short(v)   => packer.packInt(3); packer.packShort(v)
              case PrimitiveValue.Int(v)     => packer.packInt(4); packer.packInt(v)
              case PrimitiveValue.Long(v)    => packer.packInt(5); packer.packLong(v)
              case PrimitiveValue.Float(v)   => packer.packInt(6); packer.packFloat(v)
              case PrimitiveValue.Double(v)  => packer.packInt(7); packer.packDouble(v)
              case PrimitiveValue.Char(v)    => packer.packInt(8); packer.packInt(v)
              case PrimitiveValue.String(v)  => packer.packInt(9); packer.packString(v)
              case PrimitiveValue.BigInt(v)  =>
                packer.packInt(10)
                val bytes = v.toByteArray
                packer.packBinaryHeader(bytes.length)
                packer.writePayload(bytes)
              case PrimitiveValue.BigDecimal(v)     => packer.packInt(11); bigDecimalCodec.encode(v, packer)
              case PrimitiveValue.DayOfWeek(v)      => packer.packInt(12); dayOfWeekCodec.encode(v, packer)
              case PrimitiveValue.Duration(v)       => packer.packInt(13); durationCodec.encode(v, packer)
              case PrimitiveValue.Instant(v)        => packer.packInt(14); instantCodec.encode(v, packer)
              case PrimitiveValue.LocalDate(v)      => packer.packInt(15); localDateCodec.encode(v, packer)
              case PrimitiveValue.LocalDateTime(v)  => packer.packInt(16); localDateTimeCodec.encode(v, packer)
              case PrimitiveValue.LocalTime(v)      => packer.packInt(17); localTimeCodec.encode(v, packer)
              case PrimitiveValue.Month(v)          => packer.packInt(18); monthCodec.encode(v, packer)
              case PrimitiveValue.MonthDay(v)       => packer.packInt(19); monthDayCodec.encode(v, packer)
              case PrimitiveValue.OffsetDateTime(v) => packer.packInt(20); offsetDateTimeCodec.encode(v, packer)
              case PrimitiveValue.OffsetTime(v)     => packer.packInt(21); offsetTimeCodec.encode(v, packer)
              case PrimitiveValue.Period(v)         => packer.packInt(22); periodCodec.encode(v, packer)
              case PrimitiveValue.Year(v)           => packer.packInt(23); yearCodec.encode(v, packer)
              case PrimitiveValue.YearMonth(v)      => packer.packInt(24); yearMonthCodec.encode(v, packer)
              case PrimitiveValue.ZoneId(v)         => packer.packInt(25); zoneIdCodec.encode(v, packer)
              case PrimitiveValue.ZoneOffset(v)     => packer.packInt(26); zoneOffsetCodec.encode(v, packer)
              case PrimitiveValue.ZonedDateTime(v)  => packer.packInt(27); zonedDateTimeCodec.encode(v, packer)
              case PrimitiveValue.Currency(v)       => packer.packInt(28); currencyCodec.encode(v, packer)
              case PrimitiveValue.UUID(v)           => packer.packInt(29); uuidCodec.encode(v, packer)
            }
          }
          codec
        }
      }
    )
