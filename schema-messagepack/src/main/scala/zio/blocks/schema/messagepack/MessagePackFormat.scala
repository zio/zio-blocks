package zio.blocks.schema.messagepack

import org.msgpack.core.{MessageFormat, MessagePacker, MessageUnpacker}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.math.{BigInteger, MathContext}
import scala.util.control.NonFatal

/**
 * MessagePack binary format implementation for ZIO Blocks Schema.
 *
 * This format provides efficient binary serialization using MessagePack, with
 * support for:
 *   - All primitive types (including java.time.* types)
 *   - Records (case classes) with forward-compatible decoding (unknown fields
 *     are skipped)
 *   - Variants (sealed traits/enums)
 *   - Sequences (Lists, Vectors, Arrays, etc.)
 *   - Maps
 *   - Optional values
 *   - Dynamic values
 *
 * Key features:
 *   - Forward-compatible record decoding: unknown fields are skipped, field
 *     order is flexible
 *   - Efficient primitive encoding using MessagePack's compact binary format
 *   - Support for recursive types
 *   - Type-specialized collection handling for primitives
 */
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

        private[this] def isOptional[F[_, _], A](reflect: Reflect[F, A]): Boolean =
          reflect.isVariant && {
            val variant  = reflect.asVariant.get
            val typeName = reflect.typeName
            val cases    = variant.cases
            typeName.namespace == Namespace.scala && typeName.name == "Option" &&
            cases.length == 2 && cases(1).name == "Some"
          }

        private[this] def bindingDefaultValue(binding: Any): Option[() => Any] = binding match {
          case b: Binding[?, ?]             => b.asInstanceOf[Binding[Any, Any]].defaultValue.map(_.asInstanceOf[() => Any])
          case bi: BindingInstance[?, ?, ?] =>
            bi.binding.asInstanceOf[Binding[Any, Any]].defaultValue.map(_.asInstanceOf[() => Any])
          case _ => None
        }

        private[this] def defaultValue[F[_, _], A](fieldReflect: Reflect[F, A]): Option[() => Any] = {
          val binding: Any =
            if (fieldReflect.isPrimitive) fieldReflect.asPrimitive.get.primitiveBinding
            else if (fieldReflect.isRecord) fieldReflect.asRecord.get.recordBinding
            else if (fieldReflect.isVariant) fieldReflect.asVariant.get.variantBinding
            else if (fieldReflect.isSequence) fieldReflect.asSequenceUnknown.get.sequence.seqBinding
            else if (fieldReflect.isMap) fieldReflect.asMapUnknown.get.map.mapBinding
            else if (fieldReflect.isWrapper) fieldReflect.asWrapperUnknown.get.wrapper.wrapperBinding
            else fieldReflect.asDynamic.get.dynamicBinding

          bindingDefaultValue(binding)
        }

        private[this] def emptyCollectionValue[F[_, _], A](fieldReflect: Reflect[F, A]): Option[() => AnyRef] = {
          def fromSeqBinding(binding: Any): Option[() => AnyRef] = binding match {
            case b: Binding.Seq[Col, Elem] @scala.unchecked =>
              val constructor = b.constructor
              new Some(() => {
                val builder = constructor.newObjectBuilder[Elem](0)
                constructor.resultObject[Elem](builder).asInstanceOf[AnyRef]
              })
            case bi: BindingInstance[?, ?, ?] => fromSeqBinding(bi.binding)
            case _                            => None
          }

          def fromMapBinding(binding: Any): Option[() => AnyRef] = binding match {
            case b: Binding.Map[Map, Key, Value] @scala.unchecked =>
              val constructor = b.constructor
              new Some(() => {
                val builder = constructor.newObjectBuilder[Key, Value](0)
                constructor.resultObject[Key, Value](builder).asInstanceOf[AnyRef]
              })
            case bi: BindingInstance[?, ?, ?] => fromMapBinding(bi.binding)
            case _                            => None
          }

          if (fieldReflect.isSequence) fromSeqBinding(fieldReflect.asSequenceUnknown.get.sequence.seqBinding)
          else if (fieldReflect.isMap) fromMapBinding(fieldReflect.asMapUnknown.get.map.mapBinding)
          else None
        }

        private[this] val recursiveRecordCache =
          new ThreadLocal[java.util.HashMap[TypeName[?], MessagePackBinaryCodec[?]]] {
            override def initialValue: java.util.HashMap[TypeName[?], MessagePackBinaryCodec[?]] =
              new java.util.HashMap
          }

        // ============================================================
        // Primitive Codecs
        // ============================================================

        private[this] val unitCodec = new MessagePackBinaryCodec[Unit](MessagePackBinaryCodec.unitType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Unit = {
            unpacker.unpackNil()
            ()
          }

          def encodeUnsafe(value: Unit, packer: MessagePacker): Unit = packer.packNil()
        }

        private[this] val booleanCodec = new MessagePackBinaryCodec[Boolean](MessagePackBinaryCodec.booleanType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Boolean = unpacker.unpackBoolean()

          def encodeUnsafe(value: Boolean, packer: MessagePacker): Unit = packer.packBoolean(value)
        }

        private[this] val byteCodec = new MessagePackBinaryCodec[Byte](MessagePackBinaryCodec.byteType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Byte = unpacker.unpackByte()

          def encodeUnsafe(value: Byte, packer: MessagePacker): Unit = packer.packByte(value)
        }

        private[this] val shortCodec = new MessagePackBinaryCodec[Short](MessagePackBinaryCodec.shortType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Short = unpacker.unpackShort()

          def encodeUnsafe(value: Short, packer: MessagePacker): Unit = packer.packShort(value)
        }

        private[this] val intCodec = new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Int = unpacker.unpackInt()

          def encodeUnsafe(value: Int, packer: MessagePacker): Unit = packer.packInt(value)
        }

        private[this] val longCodec = new MessagePackBinaryCodec[Long](MessagePackBinaryCodec.longType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Long = unpacker.unpackLong()

          def encodeUnsafe(value: Long, packer: MessagePacker): Unit = packer.packLong(value)
        }

        private[this] val floatCodec = new MessagePackBinaryCodec[Float](MessagePackBinaryCodec.floatType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Float = unpacker.unpackFloat()

          def encodeUnsafe(value: Float, packer: MessagePacker): Unit = packer.packFloat(value)
        }

        private[this] val doubleCodec = new MessagePackBinaryCodec[Double](MessagePackBinaryCodec.doubleType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Double = unpacker.unpackDouble()

          def encodeUnsafe(value: Double, packer: MessagePacker): Unit = packer.packDouble(value)
        }

        private[this] val charCodec = new MessagePackBinaryCodec[Char](MessagePackBinaryCodec.charType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Char = {
            val x = unpacker.unpackInt()
            if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
            else decodeError("Expected Char")
          }

          def encodeUnsafe(value: Char, packer: MessagePacker): Unit = packer.packInt(value.toInt)
        }

        private[this] val stringCodec = new MessagePackBinaryCodec[String]() {
          def decodeUnsafe(unpacker: MessageUnpacker): String = unpacker.unpackString()

          def encodeUnsafe(value: String, packer: MessagePacker): Unit = packer.packString(value)
        }

        private[this] val bigIntCodec = new MessagePackBinaryCodec[BigInt]() {
          def decodeUnsafe(unpacker: MessageUnpacker): BigInt = {
            val len = unpacker.unpackBinaryHeader()
            val bs  = new Array[Byte](len)
            unpacker.readPayload(bs)
            BigInt(new BigInteger(bs))
          }

          def encodeUnsafe(value: BigInt, packer: MessagePacker): Unit = {
            val bs = value.bigInteger.toByteArray
            packer.packBinaryHeader(bs.length)
            packer.writePayload(bs)
          }
        }

        private[this] val bigDecimalCodec = new MessagePackBinaryCodec[BigDecimal]() {
          def decodeUnsafe(unpacker: MessageUnpacker): BigDecimal = {
            unpacker.unpackMapHeader() // Read map header (4 fields)
            // Read mantissa
            unpacker.unpackString() // field name "mantissa"
            val mantissaLen   = unpacker.unpackBinaryHeader()
            val mantissaBytes = new Array[Byte](mantissaLen)
            unpacker.readPayload(mantissaBytes)
            // Read scale
            unpacker.unpackString() // field name "scale"
            val scale = unpacker.unpackInt()
            // Read precision
            unpacker.unpackString() // field name "precision"
            val precision = unpacker.unpackInt()
            // Read roundingMode
            unpacker.unpackString() // field name "roundingMode"
            val roundingMode = java.math.RoundingMode.valueOf(unpacker.unpackInt())
            val mc           = new MathContext(precision, roundingMode)
            new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissaBytes), scale), mc)
          }

          def encodeUnsafe(value: BigDecimal, packer: MessagePacker): Unit = {
            val bd = value.underlying
            val mc = value.mc
            packer.packMapHeader(4)
            packer.packString("mantissa")
            val mantissaBytes = bd.unscaledValue.toByteArray
            packer.packBinaryHeader(mantissaBytes.length)
            packer.writePayload(mantissaBytes)
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

          def encodeUnsafe(value: java.time.DayOfWeek, packer: MessagePacker): Unit =
            packer.packInt(value.getValue)
        }

        private[this] val durationCodec = new MessagePackBinaryCodec[java.time.Duration]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Duration = {
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "seconds"
            val seconds = unpacker.unpackLong()
            unpacker.unpackString() // "nanos"
            val nanos = unpacker.unpackInt()
            java.time.Duration.ofSeconds(seconds, nanos)
          }

          def encodeUnsafe(value: java.time.Duration, packer: MessagePacker): Unit = {
            packer.packMapHeader(2)
            packer.packString("seconds")
            packer.packLong(value.getSeconds)
            packer.packString("nanos")
            packer.packInt(value.getNano)
          }
        }

        private[this] val instantCodec = new MessagePackBinaryCodec[java.time.Instant]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Instant = {
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "epochSecond"
            val epochSecond = unpacker.unpackLong()
            unpacker.unpackString() // "nano"
            val nano = unpacker.unpackInt()
            java.time.Instant.ofEpochSecond(epochSecond, nano)
          }

          def encodeUnsafe(value: java.time.Instant, packer: MessagePacker): Unit = {
            packer.packMapHeader(2)
            packer.packString("epochSecond")
            packer.packLong(value.getEpochSecond)
            packer.packString("nano")
            packer.packInt(value.getNano)
          }
        }

        private[this] val localDateCodec = new MessagePackBinaryCodec[java.time.LocalDate]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDate = {
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "year"
            val year = unpacker.unpackInt()
            unpacker.unpackString() // "month"
            val month = unpacker.unpackInt()
            unpacker.unpackString() // "day"
            val day = unpacker.unpackInt()
            java.time.LocalDate.of(year, month, day)
          }

          def encodeUnsafe(value: java.time.LocalDate, packer: MessagePacker): Unit = {
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
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "year"
            val year = unpacker.unpackInt()
            unpacker.unpackString() // "month"
            val month = unpacker.unpackInt()
            unpacker.unpackString() // "day"
            val day = unpacker.unpackInt()
            unpacker.unpackString() // "hour"
            val hour = unpacker.unpackInt()
            unpacker.unpackString() // "minute"
            val minute = unpacker.unpackInt()
            unpacker.unpackString() // "second"
            val second = unpacker.unpackInt()
            unpacker.unpackString() // "nano"
            val nano = unpacker.unpackInt()
            java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano)
          }

          def encodeUnsafe(value: java.time.LocalDateTime, packer: MessagePacker): Unit = {
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
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "hour"
            val hour = unpacker.unpackInt()
            unpacker.unpackString() // "minute"
            val minute = unpacker.unpackInt()
            unpacker.unpackString() // "second"
            val second = unpacker.unpackInt()
            unpacker.unpackString() // "nano"
            val nano = unpacker.unpackInt()
            java.time.LocalTime.of(hour, minute, second, nano)
          }

          def encodeUnsafe(value: java.time.LocalTime, packer: MessagePacker): Unit = {
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

          def encodeUnsafe(value: java.time.Month, packer: MessagePacker): Unit =
            packer.packInt(value.getValue)
        }

        private[this] val monthDayCodec = new MessagePackBinaryCodec[java.time.MonthDay]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.MonthDay = {
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "month"
            val month = unpacker.unpackInt()
            unpacker.unpackString() // "day"
            val day = unpacker.unpackInt()
            java.time.MonthDay.of(month, day)
          }

          def encodeUnsafe(value: java.time.MonthDay, packer: MessagePacker): Unit = {
            packer.packMapHeader(2)
            packer.packString("month")
            packer.packInt(value.getMonthValue)
            packer.packString("day")
            packer.packInt(value.getDayOfMonth)
          }
        }

        private[this] val offsetDateTimeCodec = new MessagePackBinaryCodec[java.time.OffsetDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.OffsetDateTime = {
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "year"
            val year = unpacker.unpackInt()
            unpacker.unpackString() // "month"
            val month = unpacker.unpackInt()
            unpacker.unpackString() // "day"
            val day = unpacker.unpackInt()
            unpacker.unpackString() // "hour"
            val hour = unpacker.unpackInt()
            unpacker.unpackString() // "minute"
            val minute = unpacker.unpackInt()
            unpacker.unpackString() // "second"
            val second = unpacker.unpackInt()
            unpacker.unpackString() // "nano"
            val nano = unpacker.unpackInt()
            unpacker.unpackString() // "offsetSecond"
            val offsetSecond = unpacker.unpackInt()
            java.time.OffsetDateTime
              .of(year, month, day, hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }

          def encodeUnsafe(value: java.time.OffsetDateTime, packer: MessagePacker): Unit = {
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
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "hour"
            val hour = unpacker.unpackInt()
            unpacker.unpackString() // "minute"
            val minute = unpacker.unpackInt()
            unpacker.unpackString() // "second"
            val second = unpacker.unpackInt()
            unpacker.unpackString() // "nano"
            val nano = unpacker.unpackInt()
            unpacker.unpackString() // "offsetSecond"
            val offsetSecond = unpacker.unpackInt()
            java.time.OffsetTime.of(hour, minute, second, nano, java.time.ZoneOffset.ofTotalSeconds(offsetSecond))
          }

          def encodeUnsafe(value: java.time.OffsetTime, packer: MessagePacker): Unit = {
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
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "years"
            val years = unpacker.unpackInt()
            unpacker.unpackString() // "months"
            val months = unpacker.unpackInt()
            unpacker.unpackString() // "days"
            val days = unpacker.unpackInt()
            java.time.Period.of(years, months, days)
          }

          def encodeUnsafe(value: java.time.Period, packer: MessagePacker): Unit = {
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

          def encodeUnsafe(value: java.time.Year, packer: MessagePacker): Unit =
            packer.packInt(value.getValue)
        }

        private[this] val yearMonthCodec = new MessagePackBinaryCodec[java.time.YearMonth]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.YearMonth = {
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "year"
            val year = unpacker.unpackInt()
            unpacker.unpackString() // "month"
            val month = unpacker.unpackInt()
            java.time.YearMonth.of(year, month)
          }

          def encodeUnsafe(value: java.time.YearMonth, packer: MessagePacker): Unit = {
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

          def encodeUnsafe(value: java.time.ZoneId, packer: MessagePacker): Unit =
            packer.packString(value.toString)
        }

        private[this] val zoneOffsetCodec = new MessagePackBinaryCodec[java.time.ZoneOffset]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(unpacker.unpackInt())

          def encodeUnsafe(value: java.time.ZoneOffset, packer: MessagePacker): Unit =
            packer.packInt(value.getTotalSeconds)
        }

        private[this] val zonedDateTimeCodec = new MessagePackBinaryCodec[java.time.ZonedDateTime]() {
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZonedDateTime = {
            unpacker.unpackMapHeader()
            unpacker.unpackString() // "year"
            val year = unpacker.unpackInt()
            unpacker.unpackString() // "month"
            val month = unpacker.unpackInt()
            unpacker.unpackString() // "day"
            val day = unpacker.unpackInt()
            unpacker.unpackString() // "hour"
            val hour = unpacker.unpackInt()
            unpacker.unpackString() // "minute"
            val minute = unpacker.unpackInt()
            unpacker.unpackString() // "second"
            val second = unpacker.unpackInt()
            unpacker.unpackString() // "nano"
            val nano = unpacker.unpackInt()
            unpacker.unpackString() // "offsetSecond"
            val offsetSecond = unpacker.unpackInt()
            unpacker.unpackString() // "zoneId"
            val zoneId = unpacker.unpackString()
            java.time.ZonedDateTime.ofInstant(
              java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano),
              java.time.ZoneOffset.ofTotalSeconds(offsetSecond),
              java.time.ZoneId.of(zoneId)
            )
          }

          def encodeUnsafe(value: java.time.ZonedDateTime, packer: MessagePacker): Unit = {
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

          def encodeUnsafe(value: java.util.Currency, packer: MessagePacker): Unit =
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

          def encodeUnsafe(value: java.util.UUID, packer: MessagePacker): Unit = {
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

        // ============================================================
        // Main deriveCodec method
        // ============================================================

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
              val binding         = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
              val cases           = variant.cases
              val len             = cases.length
              val codecs          = new Array[MessagePackBinaryCodec[?]](len)
              val caseNameToIndex = new java.util.HashMap[String, Int](len)
              var idx             = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                caseNameToIndex.put(cases(idx).name, idx)
                idx += 1
              }
              new MessagePackBinaryCodec[A]() {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs
                private[this] val nameToIndex   = caseNameToIndex
                private[this] val variantCases  = cases

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  // Variants are encoded as a map with one entry: {"caseName": caseValue}
                  val mapSize = unpacker.unpackMapHeader()
                  if (mapSize != 1) decodeError(s"Expected variant map with 1 entry, got $mapSize")
                  val caseName = unpacker.unpackString()
                  val caseIdx  = nameToIndex.get(caseName)
                  if (caseIdx eq null) {
                    unpacker.skipValue() // Skip the value
                    decodeError(s"Unknown variant case: $caseName")
                  }
                  try caseCodecs(caseIdx).asInstanceOf[MessagePackBinaryCodec[A]].decodeUnsafe(unpacker)
                  catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(caseName), error)
                  }
                }

                def encodeUnsafe(value: A, packer: MessagePacker): Unit = {
                  val idx = discriminator.discriminate(value)
                  packer.packMapHeader(1)
                  packer.packString(variantCases(idx).name)
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
                  while (it.hasNext) {
                    elementCodec.encodeUnsafe(it.next(), packer)
                  }
                }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding    = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val codec1     = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[Key]]
              val codec2     = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[Value]]
              val keyReflect = map.key.asInstanceOf[Reflect.Bound[Key]]

              new MessagePackBinaryCodec[Map[Key, Value]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val keyCodec      = codec1
                private[this] val valueCodec    = codec2

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
              val binding                                       = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields                                        = record.fields
              val isRecursive                                   = fields.exists(_.value.isInstanceOf[Reflect.Deferred[?, ?]])
              val typeName                                      = record.typeName
              def buildRecordCodec(): MessagePackBinaryCodec[A] = {
                val len                = fields.length
                val codecs             = new Array[MessagePackBinaryCodec[?]](len)
                val defaults           = new Array[() => Any](len)
                val optionalFields     = new Array[Boolean](len)
                val collectionDefaults = new Array[() => AnyRef](len)
                var offset             = 0L

                var idx = 0
                while (idx < len) {
                  val field = fields(idx)
                  val codec = deriveCodec(field.value)
                  codecs(idx) = codec
                  offset = RegisterOffset.add(codec.valueOffset, offset)

                  defaults(idx) = defaultValue(field.value).orNull
                  optionalFields(idx) = isOptional(field.value)
                  collectionDefaults(idx) = emptyCollectionValue(field.value).orNull
                  idx += 1
                }

                // Build field name to index map for forward-compatible decoding
                val fieldNameToIndex = new java.util.HashMap[String, Int](len)
                var i                = 0
                while (i < len) {
                  fieldNameToIndex.put(fields(i).name, i)
                  i += 1
                }

                // Pre-compute register offsets per field (used by both encode/decode)
                val fieldOffsets  = new Array[Long](len)
                var runningOffset = 0L
                var oi            = 0
                while (oi < len) {
                  fieldOffsets(oi) = runningOffset
                  runningOffset = RegisterOffset.add(codecs(oi).valueOffset, runningOffset)
                  oi += 1
                }

                new MessagePackBinaryCodec[A]() {
                  private[this] val deconstructor    = binding.deconstructor
                  private[this] val constructor      = binding.constructor
                  private[this] val usedRegisters    = offset
                  private[this] val fieldCodecs      = codecs
                  private[this] val fieldNames       = fields.map(_.name).toArray
                  private[this] val nameToIndex      = fieldNameToIndex
                  private[this] val numFields        = len
                  private[this] val offsets          = fieldOffsets
                  private[this] val defaultValues    = defaults
                  private[this] val optional         = optionalFields
                  private[this] val emptyCollections = collectionDefaults

                  def decodeUnsafe(unpacker: MessageUnpacker): A = {
                    val mapSize = unpacker.unpackMapHeader()
                    val regs    = Registers(usedRegisters)
                    val decoded = new Array[Boolean](numFields)
                    var count   = 0

                    // Read fields from the map - supports out-of-order and unknown fields
                    while (count < mapSize) {
                      val fieldName = unpacker.unpackString()
                      val fieldIdx  = nameToIndex.get(fieldName)
                      if (fieldIdx ne null) {
                        val idx         = fieldIdx.intValue
                        val codec       = fieldCodecs(idx)
                        val fieldOffset = offsets(idx)
                        try {
                          codec.valueType match {
                            case MessagePackBinaryCodec.objectType =>
                              regs.setObject(
                                fieldOffset,
                                codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].decodeUnsafe(unpacker)
                              )
                            case MessagePackBinaryCodec.intType =>
                              regs.setInt(
                                fieldOffset,
                                codec.asInstanceOf[MessagePackBinaryCodec[Int]].decodeUnsafe(unpacker)
                              )
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
                            case _ =>
                              codec.asInstanceOf[MessagePackBinaryCodec[Unit]].decodeUnsafe(unpacker)
                          }
                          decoded(idx) = true
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.Field(fieldName), error)
                        }
                      } else {
                        // Unknown field - skip it (forward compatibility)
                        unpacker.skipValue()
                      }
                      count += 1
                    }

                    // Check that all required fields were decoded
                    var missing = 0
                    while (missing < numFields) {
                      if (!decoded(missing)) {
                        val fieldOffset = offsets(missing)
                        if (optional(missing)) {
                          regs.setObject(fieldOffset, None)
                        } else {
                          val emptyCollection = emptyCollections(missing)
                          if (emptyCollection ne null) {
                            regs.setObject(fieldOffset, emptyCollection())
                          } else {
                            val defaultValue = defaultValues(missing)
                            if (defaultValue ne null) {
                              val codec = fieldCodecs(missing)
                              val v     = defaultValue()
                              codec.valueType match {
                                case MessagePackBinaryCodec.objectType =>
                                  regs.setObject(fieldOffset, v.asInstanceOf[AnyRef])
                                case MessagePackBinaryCodec.intType =>
                                  regs.setInt(fieldOffset, v.asInstanceOf[Int])
                                case MessagePackBinaryCodec.longType =>
                                  regs.setLong(fieldOffset, v.asInstanceOf[Long])
                                case MessagePackBinaryCodec.floatType =>
                                  regs.setFloat(fieldOffset, v.asInstanceOf[Float])
                                case MessagePackBinaryCodec.doubleType =>
                                  regs.setDouble(fieldOffset, v.asInstanceOf[Double])
                                case MessagePackBinaryCodec.booleanType =>
                                  regs.setBoolean(fieldOffset, v.asInstanceOf[Boolean])
                                case MessagePackBinaryCodec.byteType =>
                                  regs.setByte(fieldOffset, v.asInstanceOf[Byte])
                                case MessagePackBinaryCodec.charType =>
                                  regs.setChar(fieldOffset, v.asInstanceOf[Char])
                                case MessagePackBinaryCodec.shortType =>
                                  regs.setShort(fieldOffset, v.asInstanceOf[Short])
                                case _ =>
                                  ()
                              }
                            } else {
                              decodeError(s"Missing required field: ${fieldNames(missing)}")
                            }
                          }
                        }
                      }
                      missing += 1
                    }

                    constructor.construct(regs, 0)
                  }

                  def encodeUnsafe(value: A, packer: MessagePacker): Unit = {
                    val regs = Registers(usedRegisters)
                    deconstructor.deconstruct(regs, 0L, value)
                    packer.packMapHeader(numFields)
                    var idx = 0
                    while (idx < numFields) {
                      val codec       = fieldCodecs(idx)
                      val fieldOffset = offsets(idx)
                      packer.packString(fieldNames(idx))
                      codec.valueType match {
                        case MessagePackBinaryCodec.objectType =>
                          codec
                            .asInstanceOf[MessagePackBinaryCodec[AnyRef]]
                            .encodeUnsafe(regs.getObject(fieldOffset), packer)
                        case MessagePackBinaryCodec.intType =>
                          codec.asInstanceOf[MessagePackBinaryCodec[Int]].encodeUnsafe(regs.getInt(fieldOffset), packer)
                        case MessagePackBinaryCodec.longType =>
                          codec
                            .asInstanceOf[MessagePackBinaryCodec[Long]]
                            .encodeUnsafe(regs.getLong(fieldOffset), packer)
                        case MessagePackBinaryCodec.floatType =>
                          codec
                            .asInstanceOf[MessagePackBinaryCodec[Float]]
                            .encodeUnsafe(regs.getFloat(fieldOffset), packer)
                        case MessagePackBinaryCodec.doubleType =>
                          codec
                            .asInstanceOf[MessagePackBinaryCodec[Double]]
                            .encodeUnsafe(regs.getDouble(fieldOffset), packer)
                        case MessagePackBinaryCodec.booleanType =>
                          codec
                            .asInstanceOf[MessagePackBinaryCodec[Boolean]]
                            .encodeUnsafe(regs.getBoolean(fieldOffset), packer)
                        case MessagePackBinaryCodec.byteType =>
                          codec
                            .asInstanceOf[MessagePackBinaryCodec[Byte]]
                            .encodeUnsafe(regs.getByte(fieldOffset), packer)
                        case MessagePackBinaryCodec.charType =>
                          codec
                            .asInstanceOf[MessagePackBinaryCodec[Char]]
                            .encodeUnsafe(regs.getChar(fieldOffset), packer)
                        case MessagePackBinaryCodec.shortType =>
                          codec
                            .asInstanceOf[MessagePackBinaryCodec[Short]]
                            .encodeUnsafe(regs.getShort(fieldOffset), packer)
                        case _ =>
                          codec.asInstanceOf[MessagePackBinaryCodec[Unit]].encodeUnsafe((), packer)
                      }
                      idx += 1
                    }
                  }
                }
              }

              val resultCodec: MessagePackBinaryCodec[A] =
                if (!isRecursive) buildRecordCodec()
                else {
                  val cached = recursiveRecordCache.get.get(typeName).asInstanceOf[MessagePackBinaryCodec[A]]
                  if (cached ne null) cached
                  else {
                    final class RecursiveRecordCodec extends MessagePackBinaryCodec[A]() {
                      @volatile private[this] var underlying: MessagePackBinaryCodec[A] = null

                      def initialize(codec: MessagePackBinaryCodec[A]): Unit = underlying = codec

                      def decodeUnsafe(unpacker: MessageUnpacker): A = {
                        val c = underlying
                        if (c eq null) decodeError(s"Recursive codec not initialized for: $typeName")
                        c.decodeUnsafe(unpacker)
                      }

                      def encodeUnsafe(value: A, packer: MessagePacker): Unit = {
                        val c = underlying
                        if (c eq null)
                          throw new IllegalStateException(s"Recursive codec not initialized for: $typeName")
                        c.encodeUnsafe(value, packer)
                      }
                    }

                    val placeholder = new RecursiveRecordCodec
                    recursiveRecordCache.get.put(typeName, placeholder)
                    val actual = buildRecordCodec()
                    placeholder.initialize(actual)
                    placeholder
                  }
                }

              resultCodec
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding          = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val codec            = deriveCodec(wrapper.wrapped).asInstanceOf[MessagePackBinaryCodec[Wrapped]]
              val wrapperValueType = wrapper.wrapperPrimitiveType.fold(MessagePackBinaryCodec.objectType) {
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
              }
              new MessagePackBinaryCodec[A](wrapperValueType) {
                private[this] val wrap       = binding.wrap
                private[this] val unwrap     = binding.unwrap
                private[this] val innerCodec = codec

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val wrapped =
                    try innerCodec.decodeUnsafe(unpacker)
                    catch {
                      case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                    }
                  wrap(wrapped) match {
                    case Right(x)  => x
                    case Left(err) => decodeError(err)
                  }
                }

                def encodeUnsafe(value: A, packer: MessagePacker): Unit =
                  innerCodec.encodeUnsafe(unwrap(value), packer)
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isDynamic) {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) {
              new MessagePackBinaryCodec[DynamicValue]() {
                def decodeUnsafe(unpacker: MessageUnpacker): DynamicValue =
                  decodeDynamicValue(unpacker)

                def encodeUnsafe(value: DynamicValue, packer: MessagePacker): Unit =
                  encodeDynamicValue(value, packer)
              }
            } else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else {
            throw new IllegalArgumentException(s"Unsupported reflect type: $reflect")
          }
        }.asInstanceOf[MessagePackBinaryCodec[A]]

        private[this] def decodeDynamicValue(unpacker: MessageUnpacker): DynamicValue = {
          val format = unpacker.getNextFormat
          format.getValueType match {
            case org.msgpack.value.ValueType.NIL =>
              unpacker.unpackNil()
              DynamicValue.Primitive(PrimitiveValue.Unit)
            case org.msgpack.value.ValueType.BOOLEAN =>
              DynamicValue.Primitive(PrimitiveValue.Boolean(unpacker.unpackBoolean()))
            case org.msgpack.value.ValueType.INTEGER =>
              val mf = format
              if (mf == MessageFormat.UINT64 || mf == MessageFormat.INT64) {
                DynamicValue.Primitive(PrimitiveValue.Long(unpacker.unpackLong()))
              } else {
                DynamicValue.Primitive(PrimitiveValue.Int(unpacker.unpackInt()))
              }
            case org.msgpack.value.ValueType.FLOAT =>
              DynamicValue.Primitive(PrimitiveValue.Double(unpacker.unpackDouble()))
            case org.msgpack.value.ValueType.STRING =>
              DynamicValue.Primitive(PrimitiveValue.String(unpacker.unpackString()))
            case org.msgpack.value.ValueType.BINARY =>
              // Convert binary to BigInt for representation in DynamicValue
              val len = unpacker.unpackBinaryHeader()
              val bs  = new Array[Byte](len)
              unpacker.readPayload(bs)
              DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(new java.math.BigInteger(1, bs))))
            case org.msgpack.value.ValueType.ARRAY =>
              val size    = unpacker.unpackArrayHeader()
              val builder = Vector.newBuilder[DynamicValue]
              builder.sizeHint(size)
              var idx = 0
              while (idx < size) {
                builder += decodeDynamicValue(unpacker)
                idx += 1
              }
              DynamicValue.Sequence(builder.result())
            case org.msgpack.value.ValueType.MAP =>
              val size    = unpacker.unpackMapHeader()
              val builder = Vector.newBuilder[(String, DynamicValue)]
              builder.sizeHint(size)
              var idx = 0
              while (idx < size) {
                val key   = unpacker.unpackString()
                val value = decodeDynamicValue(unpacker)
                builder += ((key, value))
                idx += 1
              }
              DynamicValue.Record(builder.result())
            case org.msgpack.value.ValueType.EXTENSION =>
              unpacker.skipValue()
              DynamicValue.Primitive(PrimitiveValue.Unit)
          }
        }

        private[this] def encodeDynamicValue(value: DynamicValue, packer: MessagePacker): Unit = {
          value match {
            case DynamicValue.Primitive(pv) =>
              pv match {
                case PrimitiveValue.Unit       => packer.packNil()
                case PrimitiveValue.Boolean(v) => packer.packBoolean(v)
                case PrimitiveValue.Byte(v)    => packer.packByte(v)
                case PrimitiveValue.Short(v)   => packer.packShort(v)
                case PrimitiveValue.Int(v)     => packer.packInt(v)
                case PrimitiveValue.Long(v)    => packer.packLong(v)
                case PrimitiveValue.Float(v)   => packer.packFloat(v)
                case PrimitiveValue.Double(v)  => packer.packDouble(v)
                case PrimitiveValue.Char(v)    => packer.packInt(v.toInt)
                case PrimitiveValue.String(v)  => packer.packString(v)
                case PrimitiveValue.BigInt(v)  =>
                  val bs = v.toByteArray
                  packer.packBinaryHeader(bs.length)
                  packer.writePayload(bs)
                case PrimitiveValue.BigDecimal(v) =>
                  packer.packString(v.toString)
                case PrimitiveValue.DayOfWeek(v) => packer.packInt(v.getValue)
                case PrimitiveValue.Duration(v)  =>
                  packer.packMapHeader(2)
                  packer.packString("seconds")
                  packer.packLong(v.getSeconds)
                  packer.packString("nanos")
                  packer.packInt(v.getNano)
                case PrimitiveValue.Instant(v) =>
                  packer.packMapHeader(2)
                  packer.packString("epochSecond")
                  packer.packLong(v.getEpochSecond)
                  packer.packString("nano")
                  packer.packInt(v.getNano)
                case PrimitiveValue.LocalDate(v) =>
                  packer.packMapHeader(3)
                  packer.packString("year")
                  packer.packInt(v.getYear)
                  packer.packString("month")
                  packer.packInt(v.getMonthValue)
                  packer.packString("day")
                  packer.packInt(v.getDayOfMonth)
                case PrimitiveValue.LocalDateTime(v) =>
                  packer.packMapHeader(7)
                  packer.packString("year")
                  packer.packInt(v.getYear)
                  packer.packString("month")
                  packer.packInt(v.getMonthValue)
                  packer.packString("day")
                  packer.packInt(v.getDayOfMonth)
                  packer.packString("hour")
                  packer.packInt(v.getHour)
                  packer.packString("minute")
                  packer.packInt(v.getMinute)
                  packer.packString("second")
                  packer.packInt(v.getSecond)
                  packer.packString("nano")
                  packer.packInt(v.getNano)
                case PrimitiveValue.LocalTime(v) =>
                  packer.packMapHeader(4)
                  packer.packString("hour")
                  packer.packInt(v.getHour)
                  packer.packString("minute")
                  packer.packInt(v.getMinute)
                  packer.packString("second")
                  packer.packInt(v.getSecond)
                  packer.packString("nano")
                  packer.packInt(v.getNano)
                case PrimitiveValue.Month(v)    => packer.packInt(v.getValue)
                case PrimitiveValue.MonthDay(v) =>
                  packer.packMapHeader(2)
                  packer.packString("month")
                  packer.packInt(v.getMonthValue)
                  packer.packString("day")
                  packer.packInt(v.getDayOfMonth)
                case PrimitiveValue.OffsetDateTime(v) =>
                  packer.packMapHeader(8)
                  packer.packString("year")
                  packer.packInt(v.getYear)
                  packer.packString("month")
                  packer.packInt(v.getMonthValue)
                  packer.packString("day")
                  packer.packInt(v.getDayOfMonth)
                  packer.packString("hour")
                  packer.packInt(v.getHour)
                  packer.packString("minute")
                  packer.packInt(v.getMinute)
                  packer.packString("second")
                  packer.packInt(v.getSecond)
                  packer.packString("nano")
                  packer.packInt(v.getNano)
                  packer.packString("offsetSecond")
                  packer.packInt(v.getOffset.getTotalSeconds)
                case PrimitiveValue.OffsetTime(v) =>
                  packer.packMapHeader(5)
                  packer.packString("hour")
                  packer.packInt(v.getHour)
                  packer.packString("minute")
                  packer.packInt(v.getMinute)
                  packer.packString("second")
                  packer.packInt(v.getSecond)
                  packer.packString("nano")
                  packer.packInt(v.getNano)
                  packer.packString("offsetSecond")
                  packer.packInt(v.getOffset.getTotalSeconds)
                case PrimitiveValue.Period(v) =>
                  packer.packMapHeader(3)
                  packer.packString("years")
                  packer.packInt(v.getYears)
                  packer.packString("months")
                  packer.packInt(v.getMonths)
                  packer.packString("days")
                  packer.packInt(v.getDays)
                case PrimitiveValue.Year(v)      => packer.packInt(v.getValue)
                case PrimitiveValue.YearMonth(v) =>
                  packer.packMapHeader(2)
                  packer.packString("year")
                  packer.packInt(v.getYear)
                  packer.packString("month")
                  packer.packInt(v.getMonthValue)
                case PrimitiveValue.ZoneId(v)        => packer.packString(v.toString)
                case PrimitiveValue.ZoneOffset(v)    => packer.packInt(v.getTotalSeconds)
                case PrimitiveValue.ZonedDateTime(v) =>
                  packer.packMapHeader(9)
                  packer.packString("year")
                  packer.packInt(v.getYear)
                  packer.packString("month")
                  packer.packInt(v.getMonthValue)
                  packer.packString("day")
                  packer.packInt(v.getDayOfMonth)
                  packer.packString("hour")
                  packer.packInt(v.getHour)
                  packer.packString("minute")
                  packer.packInt(v.getMinute)
                  packer.packString("second")
                  packer.packInt(v.getSecond)
                  packer.packString("nano")
                  packer.packInt(v.getNano)
                  packer.packString("offsetSecond")
                  packer.packInt(v.getOffset.getTotalSeconds)
                  packer.packString("zoneId")
                  packer.packString(v.getZone.toString)
                case PrimitiveValue.Currency(v) => packer.packString(v.getCurrencyCode)
                case PrimitiveValue.UUID(v)     =>
                  val hi = v.getMostSignificantBits
                  val lo = v.getLeastSignificantBits
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
            case DynamicValue.Sequence(elements) =>
              packer.packArrayHeader(elements.size)
              elements.foreach(v => encodeDynamicValue(v, packer))
            case DynamicValue.Record(fields) =>
              packer.packMapHeader(fields.size)
              fields.foreach { case (name, v) =>
                packer.packString(name)
                encodeDynamicValue(v, packer)
              }
            case DynamicValue.Variant(caseName, value) =>
              packer.packMapHeader(1)
              packer.packString(caseName)
              encodeDynamicValue(value, packer)
            case DynamicValue.Map(entries) =>
              packer.packMapHeader(entries.size)
              entries.foreach { case (k, v) =>
                encodeDynamicValue(k, packer)
                encodeDynamicValue(v, packer)
              }
          }
        }
      }
    )
