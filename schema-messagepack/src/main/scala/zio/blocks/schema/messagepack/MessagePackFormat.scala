package zio.blocks.schema.messagepack

import org.msgpack.core.{MessagePacker, MessageUnpacker}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}

import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.math.{BigInteger, MathContext}
import scala.util.control.NonFatal

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

        // =====================================================
        // Primitive Codecs
        // =====================================================

        private[this] val unitCodec = new MessagePackBinaryCodec[Unit](MessagePackBinaryCodec.unitType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Unit          = unpacker.unpackNil()
          def encodeUnsafe(value: Unit, packer: MessagePacker): Unit = packer.packNil()
        }

        private[this] val booleanCodec = new MessagePackBinaryCodec[Boolean](MessagePackBinaryCodec.booleanType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Boolean          = unpacker.unpackBoolean()
          def encodeUnsafe(value: Boolean, packer: MessagePacker): Unit = packer.packBoolean(value)
        }

        private[this] val byteCodec = new MessagePackBinaryCodec[Byte](MessagePackBinaryCodec.byteType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Byte          = unpacker.unpackByte()
          def encodeUnsafe(value: Byte, packer: MessagePacker): Unit = packer.packByte(value)
        }

        private[this] val shortCodec = new MessagePackBinaryCodec[Short](MessagePackBinaryCodec.shortType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Short          = unpacker.unpackShort()
          def encodeUnsafe(value: Short, packer: MessagePacker): Unit = packer.packShort(value)
        }

        private[this] val intCodec = new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Int          = unpacker.unpackInt()
          def encodeUnsafe(value: Int, packer: MessagePacker): Unit = packer.packInt(value)
        }

        private[this] val longCodec = new MessagePackBinaryCodec[Long](MessagePackBinaryCodec.longType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Long          = unpacker.unpackLong()
          def encodeUnsafe(value: Long, packer: MessagePacker): Unit = packer.packLong(value)
        }

        private[this] val floatCodec = new MessagePackBinaryCodec[Float](MessagePackBinaryCodec.floatType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Float          = unpacker.unpackFloat()
          def encodeUnsafe(value: Float, packer: MessagePacker): Unit = packer.packFloat(value)
        }

        private[this] val doubleCodec = new MessagePackBinaryCodec[Double](MessagePackBinaryCodec.doubleType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Double          = unpacker.unpackDouble()
          def encodeUnsafe(value: Double, packer: MessagePacker): Unit = packer.packDouble(value)
        }

        private[this] val charCodec = new MessagePackBinaryCodec[Char](MessagePackBinaryCodec.charType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Char = {
            val s = unpacker.unpackString()
            if (s.length == 1) s.charAt(0)
            else decodeError(s"Expected Char (string of length 1), got length ${s.length}")
          }
          def encodeUnsafe(value: Char, packer: MessagePacker): Unit = packer.packString(value.toString)
        }

        private[this] val stringCodec = new MessagePackBinaryCodec[String](MessagePackBinaryCodec.objectType) {
          def decodeUnsafe(unpacker: MessageUnpacker): String          = unpacker.unpackString()
          def encodeUnsafe(value: String, packer: MessagePacker): Unit = packer.packString(value)
        }

        private[this] val bigIntCodec = new MessagePackBinaryCodec[BigInt](MessagePackBinaryCodec.objectType) {
          def decodeUnsafe(unpacker: MessageUnpacker): BigInt = {
            val len   = unpacker.unpackBinaryHeader()
            val bytes = unpacker.readPayload(len)
            BigInt(bytes)
          }
          def encodeUnsafe(value: BigInt, packer: MessagePacker): Unit = {
            val bytes = value.toByteArray
            packer.packBinaryHeader(bytes.length)
            packer.writePayload(bytes)
          }
        }

        private[this] val bigDecimalCodec = new MessagePackBinaryCodec[BigDecimal](MessagePackBinaryCodec.objectType) {
          def decodeUnsafe(unpacker: MessageUnpacker): BigDecimal = {
            unpacker.unpackMapHeader() // Expect 4 fields
            unpacker.unpackString()    // "unscaled" key
            val unscaledLen   = unpacker.unpackBinaryHeader()
            val unscaledBytes = unpacker.readPayload(unscaledLen)
            unpacker.unpackString() // "scale" key
            val scale = unpacker.unpackInt()
            unpacker.unpackString() // "precision" key
            val precision = unpacker.unpackInt()
            unpacker.unpackString() // "roundingMode" key
            val roundingMode = java.math.RoundingMode.valueOf(unpacker.unpackInt())
            val mc           = new MathContext(precision, roundingMode)
            new BigDecimal(new java.math.BigDecimal(new BigInteger(unscaledBytes), scale), mc)
          }
          def encodeUnsafe(value: BigDecimal, packer: MessagePacker): Unit = {
            val bd            = value.underlying
            val mc            = value.mc
            val unscaledBytes = bd.unscaledValue.toByteArray
            packer.packMapHeader(4)
            packer.packString("unscaled")
            packer.packBinaryHeader(unscaledBytes.length)
            packer.writePayload(unscaledBytes)
            packer.packString("scale")
            packer.packInt(bd.scale)
            packer.packString("precision")
            packer.packInt(mc.getPrecision)
            packer.packString("roundingMode")
            packer.packInt(mc.getRoundingMode.ordinal)
          }
        }

        private[this] val dayOfWeekCodec =
          new MessagePackBinaryCodec[java.time.DayOfWeek](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.DayOfWeek = {
              val value = unpacker.unpackInt()
              if (value >= 1 && value <= 7) java.time.DayOfWeek.of(value)
              else decodeError(s"Invalid DayOfWeek value: $value, expected 1-7")
            }
            def encodeUnsafe(value: java.time.DayOfWeek, packer: MessagePacker): Unit = packer.packInt(value.getValue)
          }

        private[this] val monthCodec = new MessagePackBinaryCodec[java.time.Month](MessagePackBinaryCodec.objectType) {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Month = {
            val value = unpacker.unpackInt()
            if (value >= 1 && value <= 12) java.time.Month.of(value)
            else decodeError(s"Invalid Month value: $value, expected 1-12")
          }
          def encodeUnsafe(value: java.time.Month, packer: MessagePacker): Unit = packer.packInt(value.getValue)
        }

        private[this] val durationCodec =
          new MessagePackBinaryCodec[java.time.Duration](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.Duration = {
              unpacker.unpackArrayHeader() // Expect 2 elements
              val seconds = unpacker.unpackLong()
              val nanos   = unpacker.unpackInt()
              java.time.Duration.ofSeconds(seconds, nanos)
            }
            def encodeUnsafe(value: java.time.Duration, packer: MessagePacker): Unit = {
              packer.packArrayHeader(2)
              packer.packLong(value.getSeconds)
              packer.packInt(value.getNano)
            }
          }

        private[this] val instantCodec =
          new MessagePackBinaryCodec[java.time.Instant](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.Instant = {
              unpacker.unpackArrayHeader()
              val epochSecond = unpacker.unpackLong()
              val nano        = unpacker.unpackInt()
              java.time.Instant.ofEpochSecond(epochSecond, nano)
            }
            def encodeUnsafe(value: java.time.Instant, packer: MessagePacker): Unit = {
              packer.packArrayHeader(2)
              packer.packLong(value.getEpochSecond)
              packer.packInt(value.getNano)
            }
          }

        private[this] val localDateCodec =
          new MessagePackBinaryCodec[java.time.LocalDate](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDate = {
              unpacker.unpackArrayHeader()
              java.time.LocalDate.of(unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt())
            }
            def encodeUnsafe(value: java.time.LocalDate, packer: MessagePacker): Unit = {
              packer.packArrayHeader(3)
              packer.packInt(value.getYear)
              packer.packInt(value.getMonthValue)
              packer.packInt(value.getDayOfMonth)
            }
          }

        private[this] val localDateTimeCodec =
          new MessagePackBinaryCodec[java.time.LocalDateTime](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDateTime = {
              unpacker.unpackArrayHeader()
              java.time.LocalDateTime.of(
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt()
              )
            }
            def encodeUnsafe(value: java.time.LocalDateTime, packer: MessagePacker): Unit = {
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

        private[this] val localTimeCodec =
          new MessagePackBinaryCodec[java.time.LocalTime](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalTime = {
              unpacker.unpackArrayHeader()
              java.time.LocalTime
                .of(unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt())
            }
            def encodeUnsafe(value: java.time.LocalTime, packer: MessagePacker): Unit = {
              packer.packArrayHeader(4)
              packer.packInt(value.getHour)
              packer.packInt(value.getMinute)
              packer.packInt(value.getSecond)
              packer.packInt(value.getNano)
            }
          }

        private[this] val monthDayCodec =
          new MessagePackBinaryCodec[java.time.MonthDay](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.MonthDay = {
              unpacker.unpackArrayHeader()
              java.time.MonthDay.of(unpacker.unpackInt(), unpacker.unpackInt())
            }
            def encodeUnsafe(value: java.time.MonthDay, packer: MessagePacker): Unit = {
              packer.packArrayHeader(2)
              packer.packInt(value.getMonthValue)
              packer.packInt(value.getDayOfMonth)
            }
          }

        private[this] val offsetDateTimeCodec =
          new MessagePackBinaryCodec[java.time.OffsetDateTime](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetDateTime = {
              unpacker.unpackArrayHeader()
              java.time.OffsetDateTime.of(
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt())
              )
            }
            def encodeUnsafe(value: java.time.OffsetDateTime, packer: MessagePacker): Unit = {
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

        private[this] val offsetTimeCodec =
          new MessagePackBinaryCodec[java.time.OffsetTime](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetTime = {
              unpacker.unpackArrayHeader()
              java.time.OffsetTime.of(
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                unpacker.unpackInt(),
                java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt())
              )
            }
            def encodeUnsafe(value: java.time.OffsetTime, packer: MessagePacker): Unit = {
              packer.packArrayHeader(5)
              packer.packInt(value.getHour)
              packer.packInt(value.getMinute)
              packer.packInt(value.getSecond)
              packer.packInt(value.getNano)
              packer.packInt(value.getOffset.getTotalSeconds)
            }
          }

        private[this] val periodCodec =
          new MessagePackBinaryCodec[java.time.Period](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.Period = {
              unpacker.unpackArrayHeader()
              java.time.Period.of(unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt())
            }
            def encodeUnsafe(value: java.time.Period, packer: MessagePacker): Unit = {
              packer.packArrayHeader(3)
              packer.packInt(value.getYears)
              packer.packInt(value.getMonths)
              packer.packInt(value.getDays)
            }
          }

        private[this] val yearCodec = new MessagePackBinaryCodec[java.time.Year](MessagePackBinaryCodec.objectType) {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Year = {
            val value = unpacker.unpackInt()
            try java.time.Year.of(value)
            catch {
              case e: java.time.DateTimeException => decodeError(s"Invalid Year value: $value - ${e.getMessage}")
            }
          }
          def encodeUnsafe(value: java.time.Year, packer: MessagePacker): Unit = packer.packInt(value.getValue)
        }

        private[this] val yearMonthCodec =
          new MessagePackBinaryCodec[java.time.YearMonth](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.YearMonth = {
              unpacker.unpackArrayHeader()
              java.time.YearMonth.of(unpacker.unpackInt(), unpacker.unpackInt())
            }
            def encodeUnsafe(value: java.time.YearMonth, packer: MessagePacker): Unit = {
              packer.packArrayHeader(2)
              packer.packInt(value.getYear)
              packer.packInt(value.getMonthValue)
            }
          }

        private[this] val zoneIdCodec =
          new MessagePackBinaryCodec[java.time.ZoneId](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneId = {
              val str = unpacker.unpackString()
              try java.time.ZoneId.of(str)
              catch { case e: Exception => decodeError(s"Invalid ZoneId: $str - ${e.getMessage}") }
            }
            def encodeUnsafe(value: java.time.ZoneId, packer: MessagePacker): Unit = packer.packString(value.toString)
          }

        private[this] val zoneOffsetCodec =
          new MessagePackBinaryCodec[java.time.ZoneOffset](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneOffset = {
              val seconds = unpacker.unpackInt()
              try java.time.ZoneOffset.ofTotalSeconds(seconds)
              catch {
                case e: java.time.DateTimeException =>
                  decodeError(s"Invalid ZoneOffset seconds: $seconds - ${e.getMessage}")
              }
            }
            def encodeUnsafe(value: java.time.ZoneOffset, packer: MessagePacker): Unit =
              packer.packInt(value.getTotalSeconds)
          }

        private[this] val zonedDateTimeCodec =
          new MessagePackBinaryCodec[java.time.ZonedDateTime](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZonedDateTime = {
              unpacker.unpackArrayHeader()
              val year          = unpacker.unpackInt()
              val month         = unpacker.unpackInt()
              val day           = unpacker.unpackInt()
              val hour          = unpacker.unpackInt()
              val minute        = unpacker.unpackInt()
              val second        = unpacker.unpackInt()
              val nano          = unpacker.unpackInt()
              val offsetSeconds = unpacker.unpackInt()
              val zoneId        = unpacker.unpackString()
              java.time.ZonedDateTime.ofInstant(
                java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano),
                java.time.ZoneOffset.ofTotalSeconds(offsetSeconds),
                java.time.ZoneId.of(zoneId)
              )
            }
            def encodeUnsafe(value: java.time.ZonedDateTime, packer: MessagePacker): Unit = {
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

        private[this] val currencyCodec =
          new MessagePackBinaryCodec[java.util.Currency](MessagePackBinaryCodec.objectType) {
            def decodeUnsafe(unpacker: MessageUnpacker): java.util.Currency = {
              val str = unpacker.unpackString()
              try java.util.Currency.getInstance(str)
              catch { case e: Exception => decodeError(s"Invalid Currency: $str - ${e.getMessage}") }
            }
            def encodeUnsafe(value: java.util.Currency, packer: MessagePacker): Unit =
              packer.packString(value.getCurrencyCode)
          }

        private[this] val uuidCodec = new MessagePackBinaryCodec[java.util.UUID](MessagePackBinaryCodec.objectType) {
          def decodeUnsafe(unpacker: MessageUnpacker): java.util.UUID = {
            val str = unpacker.unpackString()
            try java.util.UUID.fromString(str)
            catch { case e: Exception => decodeError(s"Invalid UUID: $str - ${e.getMessage}") }
          }
          def encodeUnsafe(value: java.util.UUID, packer: MessagePacker): Unit = packer.packString(value.toString)
        }

        // =====================================================
        // Main deriveCodec implementation
        // =====================================================

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
                  val caseIndex = unpacker.unpackInt()
                  // Fixed BUG-001 & BUG-003: Validate index bounds
                  if (caseIndex < 0 || caseIndex >= caseCodecs.length) {
                    decodeError(s"Invalid enum case index: $caseIndex, valid range: 0-${caseCodecs.length - 1}")
                  }
                  try caseCodecs(caseIndex).asInstanceOf[MessagePackBinaryCodec[A]].decodeUnsafe(unpacker)
                  catch {
                    case error if NonFatal(error) =>
                      decodeError(new DynamicOptic.Node.Case(cases(caseIndex).name), error)
                  }
                }

                def encodeUnsafe(value: A, packer: MessagePacker): Unit = {
                  val idx = discriminator.discriminate(value)
                  packer.packInt(idx)
                  caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].encodeUnsafe(value, packer)
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
                    decodeError(s"Collection size $size exceeds maximum ${MessagePackBinaryCodec.maxCollectionSize}")
                  }
                  val builder = constructor.newObjectBuilder[Elem](size)
                  var idx     = 0
                  while (idx < size) {
                    try {
                      constructor.addObject(builder, elementCodec.decodeUnsafe(unpacker))
                    } catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    idx += 1
                  }
                  constructor.resultObject[Elem](builder)
                }

                def encodeUnsafe(value: Col[Elem], packer: MessagePacker): Unit = {
                  val size = deconstructor.size(value)
                  packer.packArrayHeader(size)
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) elementCodec.encodeUnsafe(it.next(), packer)
                }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding       = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val keyCodecVal   = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[Key]]
              val valueCodecVal = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[Value]]
              new MessagePackBinaryCodec[Map[Key, Value]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val keyCodec      = keyCodecVal
                private[this] val valueCodec    = valueCodecVal
                private[this] val keyReflect    = map.key.asInstanceOf[Reflect.Bound[Key]]

                def decodeUnsafe(unpacker: MessageUnpacker): Map[Key, Value] = {
                  val size = unpacker.unpackMapHeader()
                  if (size > MessagePackBinaryCodec.maxCollectionSize) {
                    decodeError(s"Map size $size exceeds maximum ${MessagePackBinaryCodec.maxCollectionSize}")
                  }
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

                def encodeUnsafe(value: Map[Key, Value], packer: MessagePacker): Unit = {
                  val size = deconstructor.size(value)
                  packer.packMapHeader(size)
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) {
                    val kv = it.next()
                    keyCodec.encodeUnsafe(deconstructor.getKey(kv), packer)
                    valueCodec.encodeUnsafe(deconstructor.getValue(kv), packer)
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

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  val len    = fieldCodecs.length
                  // Records encoded as map with field names as keys
                  val mapSize = unpacker.unpackMapHeader()
                  if (mapSize != len) {
                    decodeError(s"Expected $len fields, got $mapSize")
                  }
                  var idx = 0
                  try {
                    while (idx < len) {
                      unpacker.unpackString() // Field name - skip for now, assume order matches
                      val codec = fieldCodecs(idx)
                      codec.valueType match {
                        case MessagePackBinaryCodec.objectType =>
                          regs.setObject(
                            offset,
                            codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.intType =>
                          regs.setInt(offset, codec.asInstanceOf[MessagePackBinaryCodec[Int]].decodeUnsafe(unpacker))
                        case MessagePackBinaryCodec.longType =>
                          regs.setLong(offset, codec.asInstanceOf[MessagePackBinaryCodec[Long]].decodeUnsafe(unpacker))
                        case MessagePackBinaryCodec.floatType =>
                          regs
                            .setFloat(offset, codec.asInstanceOf[MessagePackBinaryCodec[Float]].decodeUnsafe(unpacker))
                        case MessagePackBinaryCodec.doubleType =>
                          regs.setDouble(
                            offset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Double]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.booleanType =>
                          regs.setBoolean(
                            offset,
                            codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].decodeUnsafe(unpacker)
                          )
                        case MessagePackBinaryCodec.byteType =>
                          regs.setByte(offset, codec.asInstanceOf[MessagePackBinaryCodec[Byte]].decodeUnsafe(unpacker))
                        case MessagePackBinaryCodec.charType =>
                          regs.setChar(offset, codec.asInstanceOf[MessagePackBinaryCodec[Char]].decodeUnsafe(unpacker))
                        case MessagePackBinaryCodec.shortType =>
                          regs
                            .setShort(offset, codec.asInstanceOf[MessagePackBinaryCodec[Short]].decodeUnsafe(unpacker))
                        case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].decodeUnsafe(unpacker)
                      }
                      offset += codec.valueOffset
                      idx += 1
                    }
                    constructor.construct(regs, 0)
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                  }
                }

                def encodeUnsafe(value: A, packer: MessagePacker): Unit = {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  deconstructor.deconstruct(regs, offset, value)
                  val len = fieldCodecs.length
                  packer.packMapHeader(len)
                  var idx = 0
                  while (idx < len) {
                    packer.packString(fields(idx).name)
                    val codec = fieldCodecs(idx)
                    codec.valueType match {
                      case MessagePackBinaryCodec.objectType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].encodeUnsafe(regs.getObject(offset), packer)
                      case MessagePackBinaryCodec.intType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Int]].encodeUnsafe(regs.getInt(offset), packer)
                      case MessagePackBinaryCodec.longType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Long]].encodeUnsafe(regs.getLong(offset), packer)
                      case MessagePackBinaryCodec.floatType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Float]].encodeUnsafe(regs.getFloat(offset), packer)
                      case MessagePackBinaryCodec.doubleType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Double]].encodeUnsafe(regs.getDouble(offset), packer)
                      case MessagePackBinaryCodec.booleanType =>
                        codec
                          .asInstanceOf[MessagePackBinaryCodec[Boolean]]
                          .encodeUnsafe(regs.getBoolean(offset), packer)
                      case MessagePackBinaryCodec.byteType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Byte]].encodeUnsafe(regs.getByte(offset), packer)
                      case MessagePackBinaryCodec.charType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Char]].encodeUnsafe(regs.getChar(offset), packer)
                      case MessagePackBinaryCodec.shortType =>
                        codec.asInstanceOf[MessagePackBinaryCodec[Short]].encodeUnsafe(regs.getShort(offset), packer)
                      case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].encodeUnsafe((), packer)
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

                def encodeUnsafe(value: A, packer: MessagePacker): Unit =
                  wrappedCodec.encodeUnsafe(unwrap(value), packer)
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

          def decodeUnsafe(unpacker: MessageUnpacker): DynamicValue = {
            val tag = unpacker.unpackInt()
            (tag: @scala.annotation.switch) match {
              case 0 => // Primitive
                try {
                  val primitiveTag = unpacker.unpackInt()
                  new DynamicValue.Primitive((primitiveTag: @scala.annotation.switch) match {
                    case 0  => PrimitiveValue.Unit
                    case 1  => new PrimitiveValue.Boolean(booleanCodec.decodeUnsafe(unpacker))
                    case 2  => new PrimitiveValue.Byte(byteCodec.decodeUnsafe(unpacker))
                    case 3  => new PrimitiveValue.Short(shortCodec.decodeUnsafe(unpacker))
                    case 4  => new PrimitiveValue.Int(intCodec.decodeUnsafe(unpacker))
                    case 5  => new PrimitiveValue.Long(longCodec.decodeUnsafe(unpacker))
                    case 6  => new PrimitiveValue.Float(floatCodec.decodeUnsafe(unpacker))
                    case 7  => new PrimitiveValue.Double(doubleCodec.decodeUnsafe(unpacker))
                    case 8  => new PrimitiveValue.Char(charCodec.decodeUnsafe(unpacker))
                    case 9  => new PrimitiveValue.String(stringCodec.decodeUnsafe(unpacker))
                    case 10 => new PrimitiveValue.BigInt(bigIntCodec.decodeUnsafe(unpacker))
                    case 11 => new PrimitiveValue.BigDecimal(bigDecimalCodec.decodeUnsafe(unpacker))
                    case 12 => new PrimitiveValue.DayOfWeek(dayOfWeekCodec.decodeUnsafe(unpacker))
                    case 13 => new PrimitiveValue.Duration(durationCodec.decodeUnsafe(unpacker))
                    case 14 => new PrimitiveValue.Instant(instantCodec.decodeUnsafe(unpacker))
                    case 15 => new PrimitiveValue.LocalDate(localDateCodec.decodeUnsafe(unpacker))
                    case 16 => new PrimitiveValue.LocalDateTime(localDateTimeCodec.decodeUnsafe(unpacker))
                    case 17 => new PrimitiveValue.LocalTime(localTimeCodec.decodeUnsafe(unpacker))
                    case 18 => new PrimitiveValue.Month(monthCodec.decodeUnsafe(unpacker))
                    case 19 => new PrimitiveValue.MonthDay(monthDayCodec.decodeUnsafe(unpacker))
                    case 20 => new PrimitiveValue.OffsetDateTime(offsetDateTimeCodec.decodeUnsafe(unpacker))
                    case 21 => new PrimitiveValue.OffsetTime(offsetTimeCodec.decodeUnsafe(unpacker))
                    case 22 => new PrimitiveValue.Period(periodCodec.decodeUnsafe(unpacker))
                    case 23 => new PrimitiveValue.Year(yearCodec.decodeUnsafe(unpacker))
                    case 24 => new PrimitiveValue.YearMonth(yearMonthCodec.decodeUnsafe(unpacker))
                    case 25 => new PrimitiveValue.ZoneId(zoneIdCodec.decodeUnsafe(unpacker))
                    case 26 => new PrimitiveValue.ZoneOffset(zoneOffsetCodec.decodeUnsafe(unpacker))
                    case 27 => new PrimitiveValue.ZonedDateTime(zonedDateTimeCodec.decodeUnsafe(unpacker))
                    case 28 => new PrimitiveValue.Currency(currencyCodec.decodeUnsafe(unpacker))
                    case 29 => new PrimitiveValue.UUID(uuidCodec.decodeUnsafe(unpacker))
                    case _  => decodeError(s"Unknown primitive type index: $primitiveTag")
                  })
                } catch {
                  case error if NonFatal(error) => decodeError(spanPrimitive, error)
                }
              case 1 => // Record
                try {
                  val size   = unpacker.unpackArrayHeader()
                  val fields = Vector.newBuilder[(scala.Predef.String, DynamicValue)]
                  var i      = 0
                  while (i < size) {
                    val name  = unpacker.unpackString()
                    val value = decodeUnsafe(unpacker)
                    fields += ((name, value))
                    i += 1
                  }
                  new DynamicValue.Record(fields.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanRecord, error)
                }
              case 2 => // Variant
                try {
                  val caseName = unpacker.unpackString()
                  val value    = decodeUnsafe(unpacker)
                  new DynamicValue.Variant(caseName, value)
                } catch {
                  case error if NonFatal(error) => decodeError(spanVariant, error)
                }
              case 3 => // Sequence
                try {
                  val size     = unpacker.unpackArrayHeader()
                  val elements = Vector.newBuilder[DynamicValue]
                  var i        = 0
                  while (i < size) {
                    elements += decodeUnsafe(unpacker)
                    i += 1
                  }
                  new DynamicValue.Sequence(elements.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanSequence, error)
                }
              case 4 => // Map
                try {
                  val size    = unpacker.unpackArrayHeader()
                  val entries = Vector.newBuilder[(DynamicValue, DynamicValue)]
                  var i       = 0
                  while (i < size) {
                    val key   = decodeUnsafe(unpacker)
                    val value = decodeUnsafe(unpacker)
                    entries += ((key, value))
                    i += 1
                  }
                  new DynamicValue.Map(entries.result())
                } catch {
                  case error if NonFatal(error) => decodeError(spanMap, error)
                }
              case _ => decodeError(s"Unknown DynamicValue type index: $tag")
            }
          }

          def encodeUnsafe(value: DynamicValue, packer: MessagePacker): Unit = value match {
            case p: DynamicValue.Primitive =>
              packer.packInt(0) // Primitive tag
              p.value match {
                case PrimitiveValue.Unit             => packer.packInt(0)
                case v: PrimitiveValue.Boolean       => packer.packInt(1); booleanCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Byte          => packer.packInt(2); byteCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Short         => packer.packInt(3); shortCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Int           => packer.packInt(4); intCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Long          => packer.packInt(5); longCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Float         => packer.packInt(6); floatCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Double        => packer.packInt(7); doubleCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Char          => packer.packInt(8); charCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.String        => packer.packInt(9); stringCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.BigInt        => packer.packInt(10); bigIntCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.BigDecimal    => packer.packInt(11); bigDecimalCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.DayOfWeek     => packer.packInt(12); dayOfWeekCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Duration      => packer.packInt(13); durationCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Instant       => packer.packInt(14); instantCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.LocalDate     => packer.packInt(15); localDateCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.LocalDateTime =>
                  packer.packInt(16); localDateTimeCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.LocalTime      => packer.packInt(17); localTimeCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Month          => packer.packInt(18); monthCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.MonthDay       => packer.packInt(19); monthDayCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.OffsetDateTime =>
                  packer.packInt(20); offsetDateTimeCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.OffsetTime    => packer.packInt(21); offsetTimeCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Period        => packer.packInt(22); periodCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Year          => packer.packInt(23); yearCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.YearMonth     => packer.packInt(24); yearMonthCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.ZoneId        => packer.packInt(25); zoneIdCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.ZoneOffset    => packer.packInt(26); zoneOffsetCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.ZonedDateTime =>
                  packer.packInt(27); zonedDateTimeCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.Currency => packer.packInt(28); currencyCodec.encodeUnsafe(v.value, packer)
                case v: PrimitiveValue.UUID     => packer.packInt(29); uuidCodec.encodeUnsafe(v.value, packer)
              }
            case r: DynamicValue.Record =>
              packer.packInt(1) // Record tag
              val fields = r.fields
              packer.packArrayHeader(fields.size)
              fields.foreach { case (name, value) =>
                packer.packString(name)
                encodeUnsafe(value, packer)
              }
            case v: DynamicValue.Variant =>
              packer.packInt(2) // Variant tag
              packer.packString(v.caseName)
              encodeUnsafe(v.value, packer)
            case s: DynamicValue.Sequence =>
              packer.packInt(3) // Sequence tag
              packer.packArrayHeader(s.elements.size)
              s.elements.foreach(encodeUnsafe(_, packer))
            case m: DynamicValue.Map =>
              packer.packInt(4) // Map tag
              packer.packArrayHeader(m.entries.size)
              m.entries.foreach { case (k, v) =>
                encodeUnsafe(k, packer)
                encodeUnsafe(v, packer)
              }
          }
        }
      }
    )
