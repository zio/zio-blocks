package zio.blocks.schema.msgpack

import org.msgpack.core.{MessagePacker, MessageUnpacker}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers}

import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.math.{BigInteger, MathContext}

import scala.util.control.NonFatal

/**
 * MessagePack format for ZIO Blocks Schema.
 *
 * Key features:
 * - Forward-compatible record decoding (unknown fields are skipped)
 * - High performance with minimal allocations
 * - Support for all primitive types
 */
object MessagePackFormat
    extends BinaryFormat(
      "application/x-msgpack",
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

        // Primitive codecs
        private[this] val unitCodec = new MessagePackBinaryCodec[Unit](MessagePackBinaryCodec.unitType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Unit = unpacker.unpackNil()
          def encode(value: Unit, packer: MessagePacker): Unit = packer.packNil()
        }

        private[this] val booleanCodec = new MessagePackBinaryCodec[Boolean](MessagePackBinaryCodec.booleanType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Boolean = unpacker.unpackBoolean()
          def encode(value: Boolean, packer: MessagePacker): Unit = packer.packBoolean(value)
        }

        private[this] val byteCodec = new MessagePackBinaryCodec[Byte](MessagePackBinaryCodec.byteType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Byte = unpacker.unpackByte()
          def encode(value: Byte, packer: MessagePacker): Unit = packer.packByte(value)
        }

        private[this] val shortCodec = new MessagePackBinaryCodec[Short](MessagePackBinaryCodec.shortType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Short = unpacker.unpackShort()
          def encode(value: Short, packer: MessagePacker): Unit = packer.packShort(value)
        }

        private[this] val intCodec = new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Int = unpacker.unpackInt()
          def encode(value: Int, packer: MessagePacker): Unit = packer.packInt(value)
        }

        private[this] val longCodec = new MessagePackBinaryCodec[Long](MessagePackBinaryCodec.longType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Long = unpacker.unpackLong()
          def encode(value: Long, packer: MessagePacker): Unit = packer.packLong(value)
        }

        private[this] val floatCodec = new MessagePackBinaryCodec[Float](MessagePackBinaryCodec.floatType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Float = unpacker.unpackFloat()
          def encode(value: Float, packer: MessagePacker): Unit = packer.packFloat(value)
        }

        private[this] val doubleCodec = new MessagePackBinaryCodec[Double](MessagePackBinaryCodec.doubleType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Double = unpacker.unpackDouble()
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
          def decodeUnsafe(unpacker: MessageUnpacker): String = unpacker.unpackString()
          def encode(value: String, packer: MessagePacker): Unit = packer.packString(value)
        }

        private[this] val bigIntCodec = new MessagePackBinaryCodec[BigInt]() {
          def decodeUnsafe(unpacker: MessageUnpacker): BigInt = {
            val len = unpacker.unpackBinaryHeader()
            val bs = new Array[Byte](len)
            unpacker.readPayload(bs)
            BigInt(bs)
          }
          def encode(value: BigInt, packer: MessagePacker): Unit = {
            val bs = value.toByteArray
            packer.packBinaryHeader(bs.length)
            packer.writePayload(bs)
          }
        }

        private[this] val bigDecimalCodec = new MessagePackBinaryCodec[BigDecimal]() {
          def decodeUnsafe(unpacker: MessageUnpacker): BigDecimal = {
            unpacker.unpackArrayHeader()
            val mLen = unpacker.unpackBinaryHeader()
            val mantissa = new Array[Byte](mLen)
            unpacker.readPayload(mantissa)
            val scale = unpacker.unpackInt()
            val precision = unpacker.unpackInt()
            val roundingMode = java.math.RoundingMode.valueOf(unpacker.unpackInt())
            val mc = new MathContext(precision, roundingMode)
            new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
          }
          def encode(value: BigDecimal, packer: MessagePacker): Unit = {
            val bd = value.underlying
            val mc = value.mc
            packer.packArrayHeader(4)
            val bs = bd.unscaledValue.toByteArray
            packer.packBinaryHeader(bs.length)
            packer.writePayload(bs)
            packer.packInt(bd.scale)
            packer.packInt(mc.getPrecision)
            packer.packInt(mc.getRoundingMode.ordinal)
          }
        }

        // Time types
        private[this] val dayOfWeekCodec = new MessagePackBinaryCodec[java.time.DayOfWeek]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.DayOfWeek = java.time.DayOfWeek.of(unpacker.unpackInt())
          def encode(value: java.time.DayOfWeek, packer: MessagePacker): Unit = packer.packInt(value.getValue)
        }

        private[this] val durationCodec = new MessagePackBinaryCodec[java.time.Duration]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Duration = {
            unpacker.unpackArrayHeader()
            val seconds = unpacker.unpackLong()
            val nanos = unpacker.unpackInt()
            java.time.Duration.ofSeconds(seconds, nanos)
          }
          def encode(value: java.time.Duration, packer: MessagePacker): Unit = {
            packer.packArrayHeader(2)
            packer.packLong(value.getSeconds)
            packer.packInt(value.getNano)
          }
        }

        private[this] val instantCodec = new MessagePackBinaryCodec[java.time.Instant]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Instant = {
            unpacker.unpackArrayHeader()
            val epochSecond = unpacker.unpackLong()
            val nano = unpacker.unpackInt()
            java.time.Instant.ofEpochSecond(epochSecond, nano)
          }
          def encode(value: java.time.Instant, packer: MessagePacker): Unit = {
            packer.packArrayHeader(2)
            packer.packLong(value.getEpochSecond)
            packer.packInt(value.getNano)
          }
        }

        private[this] val localDateCodec = new MessagePackBinaryCodec[java.time.LocalDate]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDate = {
            unpacker.unpackArrayHeader()
            val year = unpacker.unpackInt()
            val month = unpacker.unpackInt()
            val day = unpacker.unpackInt()
            java.time.LocalDate.of(year, month, day)
          }
          def encode(value: java.time.LocalDate, packer: MessagePacker): Unit = {
            packer.packArrayHeader(3)
            packer.packInt(value.getYear)
            packer.packInt(value.getMonthValue)
            packer.packInt(value.getDayOfMonth)
          }
        }

        private[this] val localDateTimeCodec = new MessagePackBinaryCodec[java.time.LocalDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDateTime = {
            unpacker.unpackArrayHeader()
            java.time.LocalDateTime.of(
              unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(),
              unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt()
            )
          }
          def encode(value: java.time.LocalDateTime, packer: MessagePacker): Unit = {
            packer.packArrayHeader(7)
            packer.packInt(value.getYear)
            packer.packInt(value.getMonthValue)
            packer.packInt(value.getDayOfMonth)
            packer.packInt(value.getHour)
            packer.packInt(value.getMinute)
            packer.packInt(value.getSecond)
            packer.packInt(value.getNano)
          }
        }

        private[this] val localTimeCodec = new MessagePackBinaryCodec[java.time.LocalTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalTime = {
            unpacker.unpackArrayHeader()
            java.time.LocalTime.of(unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt())
          }
          def encode(value: java.time.LocalTime, packer: MessagePacker): Unit = {
            packer.packArrayHeader(4)
            packer.packInt(value.getHour)
            packer.packInt(value.getMinute)
            packer.packInt(value.getSecond)
            packer.packInt(value.getNano)
          }
        }

        private[this] val monthCodec = new MessagePackBinaryCodec[java.time.Month]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Month = java.time.Month.of(unpacker.unpackInt())
          def encode(value: java.time.Month, packer: MessagePacker): Unit = packer.packInt(value.getValue)
        }

        private[this] val monthDayCodec = new MessagePackBinaryCodec[java.time.MonthDay]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.MonthDay = {
            unpacker.unpackArrayHeader()
            java.time.MonthDay.of(unpacker.unpackInt(), unpacker.unpackInt())
          }
          def encode(value: java.time.MonthDay, packer: MessagePacker): Unit = {
            packer.packArrayHeader(2)
            packer.packInt(value.getMonthValue)
            packer.packInt(value.getDayOfMonth)
          }
        }

        private[this] val yearCodec = new MessagePackBinaryCodec[java.time.Year]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Year = java.time.Year.of(unpacker.unpackInt())
          def encode(value: java.time.Year, packer: MessagePacker): Unit = packer.packInt(value.getValue)
        }

        private[this] val yearMonthCodec = new MessagePackBinaryCodec[java.time.YearMonth]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.YearMonth = {
            unpacker.unpackArrayHeader()
            java.time.YearMonth.of(unpacker.unpackInt(), unpacker.unpackInt())
          }
          def encode(value: java.time.YearMonth, packer: MessagePacker): Unit = {
            packer.packArrayHeader(2)
            packer.packInt(value.getYear)
            packer.packInt(value.getMonthValue)
          }
        }

        private[this] val zoneIdCodec = new MessagePackBinaryCodec[java.time.ZoneId]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneId = java.time.ZoneId.of(unpacker.unpackString())
          def encode(value: java.time.ZoneId, packer: MessagePacker): Unit = packer.packString(value.toString)
        }

        private[this] val zoneOffsetCodec = new MessagePackBinaryCodec[java.time.ZoneOffset]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt())
          def encode(value: java.time.ZoneOffset, packer: MessagePacker): Unit =
            packer.packInt(value.getTotalSeconds)
        }

        private[this] val offsetDateTimeCodec = new MessagePackBinaryCodec[java.time.OffsetDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetDateTime = {
            unpacker.unpackArrayHeader()
            java.time.OffsetDateTime.of(
              unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(),
              unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(),
              java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt())
            )
          }
          def encode(value: java.time.OffsetDateTime, packer: MessagePacker): Unit = {
            packer.packArrayHeader(8)
            packer.packInt(value.getYear)
            packer.packInt(value.getMonthValue)
            packer.packInt(value.getDayOfMonth)
            packer.packInt(value.getHour)
            packer.packInt(value.getMinute)
            packer.packInt(value.getSecond)
            packer.packInt(value.getNano)
            packer.packInt(value.getOffset.getTotalSeconds)
          }
        }

        private[this] val offsetTimeCodec = new MessagePackBinaryCodec[java.time.OffsetTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetTime = {
            unpacker.unpackArrayHeader()
            java.time.OffsetTime.of(
              unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(),
              java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt())
            )
          }
          def encode(value: java.time.OffsetTime, packer: MessagePacker): Unit = {
            packer.packArrayHeader(5)
            packer.packInt(value.getHour)
            packer.packInt(value.getMinute)
            packer.packInt(value.getSecond)
            packer.packInt(value.getNano)
            packer.packInt(value.getOffset.getTotalSeconds)
          }
        }

        private[this] val periodCodec = new MessagePackBinaryCodec[java.time.Period]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Period = {
            unpacker.unpackArrayHeader()
            java.time.Period.of(unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt())
          }
          def encode(value: java.time.Period, packer: MessagePacker): Unit = {
            packer.packArrayHeader(3)
            packer.packInt(value.getYears)
            packer.packInt(value.getMonths)
            packer.packInt(value.getDays)
          }
        }

        private[this] val zonedDateTimeCodec = new MessagePackBinaryCodec[java.time.ZonedDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZonedDateTime = {
            unpacker.unpackArrayHeader()
            java.time.ZonedDateTime.ofInstant(
              java.time.LocalDateTime.of(
                unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(),
                unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt()
              ),
              java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt()),
              java.time.ZoneId.of(unpacker.unpackString())
            )
          }
          def encode(value: java.time.ZonedDateTime, packer: MessagePacker): Unit = {
            packer.packArrayHeader(9)
            packer.packInt(value.getYear)
            packer.packInt(value.getMonthValue)
            packer.packInt(value.getDayOfMonth)
            packer.packInt(value.getHour)
            packer.packInt(value.getMinute)
            packer.packInt(value.getSecond)
            packer.packInt(value.getNano)
            packer.packInt(value.getOffset.getTotalSeconds)
            packer.packString(value.getZone.toString)
          }
        }

        private[this] val currencyCodec = new MessagePackBinaryCodec[java.util.Currency]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.util.Currency =
            java.util.Currency.getInstance(unpacker.unpackString())
          def encode(value: java.util.Currency, packer: MessagePacker): Unit =
            packer.packString(value.getCurrencyCode)
        }

        private[this] val uuidCodec = new MessagePackBinaryCodec[java.util.UUID]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.util.UUID = {
            val len = unpacker.unpackBinaryHeader()
            if (len != 16) decodeError(s"Expected 16 bytes for UUID, got $len")
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
          def encode(value: java.util.UUID, packer: MessagePacker): Unit = {
            val hi = value.getMostSignificantBits
            val lo = value.getLeastSignificantBits
            val bs = Array(
              (hi >> 56).toByte, (hi >> 48).toByte, (hi >> 40).toByte, (hi >> 32).toByte,
              (hi >> 24).toByte, (hi >> 16).toByte, (hi >> 8).toByte, hi.toByte,
              (lo >> 56).toByte, (lo >> 48).toByte, (lo >> 40).toByte, (lo >> 32).toByte,
              (lo >> 24).toByte, (lo >> 16).toByte, (lo >> 8).toByte, lo.toByte
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

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  unpacker.unpackArrayHeader()
                  val caseIdx = unpacker.unpackInt()
                  if (caseIdx >= 0 && caseIdx < caseCodecs.length) {
                    try caseCodecs(caseIdx).asInstanceOf[MessagePackBinaryCodec[A]].decodeUnsafe(unpacker)
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(cases(caseIdx).name), error)
                    }
                  } else decodeError(s"Expected variant index from 0 to ${caseCodecs.length - 1}, got $caseIdx")
                }

                def encode(value: A, packer: MessagePacker): Unit = {
                  val idx = discriminator.discriminate(value)
                  packer.packArrayHeader(2)
                  packer.packInt(idx)
                  caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].encode(value, packer)
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields  = record.fields
              val len     = fields.length
              val codecs  = new Array[MessagePackBinaryCodec[?]](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(fields(idx).value)
                idx += 1
              }
              new MessagePackBinaryCodec[A]() {
                private[this] val constructor = binding.constructor
                private[this] val deconstructor = binding.deconstructor
                private[this] val fieldCodecs = codecs
                private[this] val registers = record.registers

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val size = unpacker.unpackArrayHeader()
                  if (size != len) {
                    decodeError(s"Expected $len fields, got $size")
                  }
                  val regs = Registers(constructor.usedRegisters)
                  var fieldIdx = 0
                  while (fieldIdx < len) {
                    try {
                      val value = fieldCodecs(fieldIdx).asInstanceOf[MessagePackBinaryCodec[Any]].decodeUnsafe(unpacker)
                      registers(fieldIdx).set(regs, 0, value)
                    } catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.Field(fields(fieldIdx).name), error)
                    }
                    fieldIdx += 1
                  }
                  constructor.construct(regs, 0)
                }

                def encode(value: A, packer: MessagePacker): Unit = {
                  packer.packArrayHeader(len)
                  val regs = Registers(deconstructor.usedRegisters)
                  deconstructor.deconstruct(regs, 0, value)
                  var fieldIdx = 0
                  while (fieldIdx < len) {
                    val fieldValue = registers(fieldIdx).get(regs, 0)
                    fieldCodecs(fieldIdx).asInstanceOf[MessagePackBinaryCodec[Any]].encode(fieldValue, packer)
                    fieldIdx += 1
                  }
                }
              }
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val unknown = reflect.asSequenceUnknown.get
            val sequence = unknown.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec   = deriveCodec(sequence.element).asInstanceOf[MessagePackBinaryCodec[Elem]]
              new MessagePackBinaryCodec[Col[Elem]]() {
                private[this] val seqDeconstructor = binding.deconstructor
                private[this] val seqConstructor   = binding.constructor
                private[this] val elementCodec     = codec

                def decodeUnsafe(unpacker: MessageUnpacker): Col[Elem] = {
                  val size = unpacker.unpackArrayHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize) {
                    decodeError(s"Collection too large: $size > ${MessagePackBinaryCodec.maxCollectionSize}")
                  }
                  val builder = seqConstructor.newObjectBuilder[Elem](size)
                  var i = 0
                  while (i < size) {
                    try {
                      seqConstructor.addObject(builder, elementCodec.decodeUnsafe(unpacker))
                    } catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(i), error)
                    }
                    i += 1
                  }
                  seqConstructor.resultObject(builder)
                }

                def encode(value: Col[Elem], packer: MessagePacker): Unit = {
                  val size = seqDeconstructor.size(value)
                  packer.packArrayHeader(size)
                  val it = seqDeconstructor.deconstruct(value)
                  while (it.hasNext) {
                    elementCodec.encode(it.next(), packer)
                  }
                }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, Col[Elem]]].instance.force
          } else if (reflect.isMap) {
            val unknown = reflect.asMapUnknown.get
            val map = unknown.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val keyCodec = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[Key]]
              val valueCodec = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[Value]]
              new MessagePackBinaryCodec[Map[Key, Value]]() {
                private[this] val mapDeconstructor = binding.deconstructor
                private[this] val mapConstructor   = binding.constructor

                def decodeUnsafe(unpacker: MessageUnpacker): Map[Key, Value] = {
                  val size = unpacker.unpackMapHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize) {
                    decodeError(s"Map too large: $size > ${MessagePackBinaryCodec.maxCollectionSize}")
                  }
                  val builder = mapConstructor.newObjectBuilder[Key, Value](size)
                  var i = 0
                  while (i < size) {
                    val k = keyCodec.decodeUnsafe(unpacker)
                    val v = valueCodec.decodeUnsafe(unpacker)
                    mapConstructor.addObject(builder, k, v)
                    i += 1
                  }
                  mapConstructor.resultObject(builder)
                }

                def encode(value: Map[Key, Value], packer: MessagePacker): Unit = {
                  val size = mapDeconstructor.size(value)
                  packer.packMapHeader(size)
                  val it = mapDeconstructor.deconstruct(value)
                  while (it.hasNext) {
                    val kv = it.next()
                    keyCodec.encode(mapDeconstructor.getKey(kv), packer)
                    valueCodec.encode(mapDeconstructor.getValue(kv), packer)
                  }
                }
              }
            } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, Map[Key, Value]]].instance.force
          } else if (reflect.isWrapper) {
            val unknown = reflect.asWrapperUnknown.get
            val wrapper = unknown.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val innerCodec = deriveCodec(wrapper.wrapped).asInstanceOf[MessagePackBinaryCodec[Wrapped]]
              new MessagePackBinaryCodec[A]() {
                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val inner = innerCodec.decodeUnsafe(unpacker)
                  binding.wrap(inner) match {
                    case Right(a)    => a
                    case Left(error) => decodeError(error.toString)
                  }
                }
                def encode(value: A, packer: MessagePacker): Unit = {
                  innerCodec.encode(binding.unwrap(value), packer)
                }
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isDynamic) {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) {
              dynamicValueCodec
            } else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, DynamicValue]].instance.force
          } else {
            throw new IllegalArgumentException(s"Unknown reflect type: $reflect")
          }
        }.asInstanceOf[MessagePackBinaryCodec[A]]

        private[this] val dynamicValueCodec: MessagePackBinaryCodec[DynamicValue] = new MessagePackBinaryCodec[DynamicValue]() {
          def decodeUnsafe(unpacker: MessageUnpacker): DynamicValue = {
            val format = unpacker.getNextFormat
            import org.msgpack.value.ValueType
            format.getValueType match {
              case ValueType.NIL =>
                unpacker.unpackNil()
                DynamicValue.Primitive(PrimitiveValue.Unit)
              case ValueType.BOOLEAN =>
                DynamicValue.Primitive(PrimitiveValue.Boolean(unpacker.unpackBoolean()))
              case ValueType.INTEGER =>
                DynamicValue.Primitive(PrimitiveValue.Long(unpacker.unpackLong()))
              case ValueType.FLOAT =>
                DynamicValue.Primitive(PrimitiveValue.Double(unpacker.unpackDouble()))
              case ValueType.STRING =>
                DynamicValue.Primitive(PrimitiveValue.String(unpacker.unpackString()))
              case ValueType.BINARY =>
                val len = unpacker.unpackBinaryHeader()
                val bs = new Array[Byte](len)
                unpacker.readPayload(bs)
                // Encode binary as base64 string since PrimitiveValue doesn't have Binary
                val base64 = java.util.Base64.getEncoder.encodeToString(bs)
                DynamicValue.Primitive(PrimitiveValue.String(base64))
              case ValueType.ARRAY =>
                val size = unpacker.unpackArrayHeader()
                val builder = Vector.newBuilder[DynamicValue]
                var i = 0
                while (i < size) {
                  builder += decodeUnsafe(unpacker)
                  i += 1
                }
                new DynamicValue.Sequence(builder.result())
              case ValueType.MAP =>
                val size = unpacker.unpackMapHeader()
                val builder = Vector.newBuilder[(String, DynamicValue)]
                var i = 0
                while (i < size) {
                  val key = unpacker.unpackString()
                  val value = decodeUnsafe(unpacker)
                  builder += ((key, value))
                  i += 1
                }
                new DynamicValue.Record(builder.result())
              case _ =>
                unpacker.skipValue()
                DynamicValue.Primitive(PrimitiveValue.Unit)
            }
          }

          def encode(value: DynamicValue, packer: MessagePacker): Unit = value match {
            case DynamicValue.Primitive(pv) =>
              pv match {
                case PrimitiveValue.Unit              => packer.packNil()
                case PrimitiveValue.Boolean(v)        => packer.packBoolean(v)
                case PrimitiveValue.Byte(v)           => packer.packByte(v)
                case PrimitiveValue.Short(v)          => packer.packShort(v)
                case PrimitiveValue.Int(v)            => packer.packInt(v)
                case PrimitiveValue.Long(v)           => packer.packLong(v)
                case PrimitiveValue.Float(v)          => packer.packFloat(v)
                case PrimitiveValue.Double(v)         => packer.packDouble(v)
                case PrimitiveValue.Char(v)           => packer.packInt(v)
                case PrimitiveValue.String(v)         => packer.packString(v)
                case PrimitiveValue.BigInt(v)         =>
                  val bs = v.toByteArray
                  packer.packBinaryHeader(bs.length)
                  packer.writePayload(bs)
                case PrimitiveValue.BigDecimal(v)     => packer.packString(v.toString)
                case _                                => packer.packString(pv.toString)
              }
            case DynamicValue.Sequence(values) =>
              packer.packArrayHeader(values.size)
              values.foreach(v => encode(v, packer))
            case DynamicValue.Record(fields) =>
              packer.packMapHeader(fields.size)
              fields.foreach { case (k, v) =>
                packer.packString(k)
                encode(v, packer)
              }
            case DynamicValue.Variant(caseName, innerValue) =>
              packer.packMapHeader(1)
              packer.packString(caseName)
              encode(innerValue, packer)
            case _ => packer.packNil()
          }
        }
      }
    )
