package zio.blocks.schema.thrift

import org.apache.thrift.protocol.{TField, TList, TMap, TProtocol, TProtocolUtil, TType}
import zio.blocks.schema._
import zio.blocks.typeid.TypeId
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}

import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import scala.util.control.NonFatal

/**
 * Apache Thrift binary codec for ZIO Schema 2.
 *
 * Key features:
 *   - '''Field ID-based encoding/decoding''' (NOT position-based)
 *   - '''Forward-compatible schema evolution''' (unknown fields are skipped via
 *     TProtocolUtil.skip)
 *   - '''Out-of-order field decoding''' (fields can arrive in any order on the
 *     wire)
 *   - Compatible with Apache Thrift 0.22.0 wire format
 *
 * Example usage:
 * {{{
 * import zio.blocks.schema._
 * import zio.blocks.schema.thrift.ThriftFormat
 * import java.nio.ByteBuffer
 *
 * case class Person(name: String, age: Int)
 * object Person {
 *   implicit val schema: Schema[Person] = Schema.derived
 * }
 *
 * // Encode
 * val buffer = ByteBuffer.allocate(1024)
 * Schema[Person].encode(ThriftFormat)(buffer)(Person("Alice", 30))
 * buffer.flip()
 *
 * // Decode
 * val result: Either[SchemaError, Person] = Schema[Person].decode(ThriftFormat)(buffer)
 * }}}
 *
 * @note
 *   This implementation uses Thrift field IDs (1-based) that correspond to
 *   field positions in the case class definition.
 */
object ThriftFormat
    extends BinaryFormat(
      "application/thrift",
      new Deriver[ThriftBinaryCodec] {

        // Existential type aliases for internal use
        type Elem
        type Key
        type Value
        type Wrapped
        type Col[_]
        type Map[_, _]
        type TC[_]

        // Thread-local cache for recursive type handling
        private[this] val recursiveRecordCache =
          new ThreadLocal[java.util.HashMap[TypeId[?], Array[ThriftBinaryCodec[?]]]] {
            override def initialValue: java.util.HashMap[TypeId[?], Array[ThriftBinaryCodec[?]]] =
              new java.util.HashMap
          }

        // Primitive codec instances (cached for performance)
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
          def decodeUnsafe(protocol: TProtocol): Float        = protocol.readDouble().toFloat
          def encode(value: Float, protocol: TProtocol): Unit = protocol.writeDouble(value.toDouble)
        }

        private[this] val doubleCodec = new ThriftBinaryCodec[Double](ThriftBinaryCodec.doubleType) {
          def decodeUnsafe(protocol: TProtocol): Double        = protocol.readDouble()
          def encode(value: Double, protocol: TProtocol): Unit = protocol.writeDouble(value)
        }

        private[this] val charCodec = new ThriftBinaryCodec[Char](ThriftBinaryCodec.charType) {
          def decodeUnsafe(protocol: TProtocol): Char = {
            val s = protocol.readString()
            if (s.length == 1) s.charAt(0)
            else decodeError(s"Expected single character, got string of length ${s.length}")
          }
          def encode(value: Char, protocol: TProtocol): Unit = protocol.writeString(value.toString)
        }

        private[this] val stringCodec = new ThriftBinaryCodec[String]() {
          def decodeUnsafe(protocol: TProtocol): String        = protocol.readString()
          def encode(value: String, protocol: TProtocol): Unit = protocol.writeString(value)
        }

        private[this] val bigIntCodec = new ThriftBinaryCodec[BigInt]() {
          def decodeUnsafe(protocol: TProtocol): BigInt = {
            val buf = protocol.readBinary()
            val arr = new Array[Byte](buf.remaining())
            buf.get(arr)
            BigInt(new BigInteger(arr))
          }
          def encode(value: BigInt, protocol: TProtocol): Unit =
            protocol.writeBinary(ByteBuffer.wrap(value.toByteArray))
        }

        private[this] val bigDecimalCodec = new ThriftBinaryCodec[BigDecimal]() {
          def decodeUnsafe(protocol: TProtocol): BigDecimal = {
            protocol.readFieldBegin()
            val unscaledBuf = protocol.readBinary()
            val unscaledArr = new Array[Byte](unscaledBuf.remaining())
            unscaledBuf.get(unscaledArr)
            val unscaled = new BigInteger(unscaledArr)

            protocol.readFieldBegin()
            val precision = protocol.readI32()

            protocol.readFieldBegin()
            val scale = protocol.readI32()

            protocol.readFieldBegin() // read STOP

            new BigDecimal(new java.math.BigDecimal(unscaled, scale, new MathContext(precision)))
          }

          def encode(value: BigDecimal, protocol: TProtocol): Unit = {
            val bd = value.underlying()
            protocol.writeFieldBegin(new TField("unscaled", TType.STRING, 1))
            protocol.writeBinary(ByteBuffer.wrap(bd.unscaledValue().toByteArray))
            protocol.writeFieldBegin(new TField("precision", TType.I32, 2))
            protocol.writeI32(bd.precision())
            protocol.writeFieldBegin(new TField("scale", TType.I32, 3))
            protocol.writeI32(bd.scale())
            protocol.writeFieldStop()
          }
        }

        // Date/time codecs - using ISO strings for simplicity
        private[this] val dayOfWeekCodec = new ThriftBinaryCodec[java.time.DayOfWeek]() {
          def decodeUnsafe(protocol: TProtocol): java.time.DayOfWeek =
            java.time.DayOfWeek.of(protocol.readByte().toInt)
          def encode(value: java.time.DayOfWeek, protocol: TProtocol): Unit =
            protocol.writeByte(value.getValue.toByte)
        }

        private[this] val monthCodec = new ThriftBinaryCodec[java.time.Month]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Month =
            java.time.Month.of(protocol.readByte().toInt)
          def encode(value: java.time.Month, protocol: TProtocol): Unit =
            protocol.writeByte(value.getValue.toByte)
        }

        private[this] val yearCodec = new ThriftBinaryCodec[java.time.Year]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Year =
            java.time.Year.of(protocol.readI32())
          def encode(value: java.time.Year, protocol: TProtocol): Unit =
            protocol.writeI32(value.getValue)
        }

        private[this] val monthDayCodec = new ThriftBinaryCodec[java.time.MonthDay]() {
          def decodeUnsafe(protocol: TProtocol): java.time.MonthDay =
            java.time.MonthDay.parse(protocol.readString())
          def encode(value: java.time.MonthDay, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val yearMonthCodec = new ThriftBinaryCodec[java.time.YearMonth]() {
          def decodeUnsafe(protocol: TProtocol): java.time.YearMonth =
            java.time.YearMonth.parse(protocol.readString())
          def encode(value: java.time.YearMonth, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val periodCodec = new ThriftBinaryCodec[java.time.Period]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Period =
            java.time.Period.parse(protocol.readString())
          def encode(value: java.time.Period, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val durationCodec = new ThriftBinaryCodec[java.time.Duration]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Duration =
            java.time.Duration.parse(protocol.readString())
          def encode(value: java.time.Duration, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val instantCodec = new ThriftBinaryCodec[java.time.Instant]() {
          def decodeUnsafe(protocol: TProtocol): java.time.Instant =
            java.time.Instant.parse(protocol.readString())
          def encode(value: java.time.Instant, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val localDateCodec = new ThriftBinaryCodec[java.time.LocalDate]() {
          def decodeUnsafe(protocol: TProtocol): java.time.LocalDate =
            java.time.LocalDate.parse(protocol.readString())
          def encode(value: java.time.LocalDate, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val localTimeCodec = new ThriftBinaryCodec[java.time.LocalTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.LocalTime =
            java.time.LocalTime.parse(protocol.readString())
          def encode(value: java.time.LocalTime, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val localDateTimeCodec = new ThriftBinaryCodec[java.time.LocalDateTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.LocalDateTime =
            java.time.LocalDateTime.parse(protocol.readString())
          def encode(value: java.time.LocalDateTime, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val offsetTimeCodec = new ThriftBinaryCodec[java.time.OffsetTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.OffsetTime =
            java.time.OffsetTime.parse(protocol.readString())
          def encode(value: java.time.OffsetTime, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val offsetDateTimeCodec = new ThriftBinaryCodec[java.time.OffsetDateTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.OffsetDateTime =
            java.time.OffsetDateTime.parse(protocol.readString())
          def encode(value: java.time.OffsetDateTime, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val zonedDateTimeCodec = new ThriftBinaryCodec[java.time.ZonedDateTime]() {
          def decodeUnsafe(protocol: TProtocol): java.time.ZonedDateTime =
            java.time.ZonedDateTime.parse(protocol.readString())
          def encode(value: java.time.ZonedDateTime, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        private[this] val zoneIdCodec = new ThriftBinaryCodec[java.time.ZoneId]() {
          def decodeUnsafe(protocol: TProtocol): java.time.ZoneId =
            java.time.ZoneId.of(protocol.readString())
          def encode(value: java.time.ZoneId, protocol: TProtocol): Unit =
            protocol.writeString(value.getId)
        }

        private[this] val zoneOffsetCodec = new ThriftBinaryCodec[java.time.ZoneOffset]() {
          def decodeUnsafe(protocol: TProtocol): java.time.ZoneOffset =
            java.time.ZoneOffset.ofTotalSeconds(protocol.readI32())
          def encode(value: java.time.ZoneOffset, protocol: TProtocol): Unit =
            protocol.writeI32(value.getTotalSeconds)
        }

        private[this] val currencyCodec = new ThriftBinaryCodec[java.util.Currency]() {
          def decodeUnsafe(protocol: TProtocol): java.util.Currency =
            java.util.Currency.getInstance(protocol.readString())
          def encode(value: java.util.Currency, protocol: TProtocol): Unit =
            protocol.writeString(value.getCurrencyCode)
        }

        private[this] val uuidCodec = new ThriftBinaryCodec[java.util.UUID]() {
          def decodeUnsafe(protocol: TProtocol): java.util.UUID =
            java.util.UUID.fromString(protocol.readString())
          def encode(value: java.util.UUID, protocol: TProtocol): Unit =
            protocol.writeString(value.toString)
        }

        // Deriver implementation
        override def derivePrimitive[F[_, _], A](
          primitiveType: PrimitiveType[A],
          typeId: TypeId[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        ): Lazy[ThriftBinaryCodec[A]] =
          Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeId, binding, doc, modifiers)))

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeId: TypeId[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[C[A]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[M[K, V]]] = Lazy {
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[DynamicValue]] =
          Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeId.of[DynamicValue], doc, modifiers)))

        override def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeId: TypeId[A],
          wrapperPrimitiveType: Option[PrimitiveType[A]],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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
          super.instanceOverrides
        }

        private[this] def getThriftType[F[_, _]](reflect: Reflect[F, ?]): Byte =
          if (reflect.isPrimitive) {
            val primitive = reflect.asPrimitive.get
            primitive.primitiveType match {
              case _: PrimitiveType.Unit.type  => TType.VOID
              case _: PrimitiveType.Boolean    => TType.BOOL
              case _: PrimitiveType.Byte       => TType.BYTE
              case _: PrimitiveType.Short      => TType.I16
              case _: PrimitiveType.Int        => TType.I32
              case _: PrimitiveType.Long       => TType.I64
              case _: PrimitiveType.Float      => TType.DOUBLE
              case _: PrimitiveType.Double     => TType.DOUBLE
              case _: PrimitiveType.Char       => TType.STRING
              case _: PrimitiveType.String     => TType.STRING
              case _: PrimitiveType.BigInt     => TType.STRING
              case _: PrimitiveType.BigDecimal => TType.STRUCT
              case _: PrimitiveType.DayOfWeek  => TType.BYTE
              case _: PrimitiveType.Month      => TType.BYTE
              case _: PrimitiveType.Year       => TType.I32
              case _: PrimitiveType.ZoneOffset => TType.I32
              case _                           => TType.STRING
            }
          } else if (reflect.isSequence) {
            TType.LIST
          } else if (reflect.isMap) {
            TType.MAP
          } else {
            TType.STRUCT
          }

        // Main codec derivation logic - all inlined to avoid type parameter issues
        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ThriftBinaryCodec[A] = {
          if (reflect.isPrimitive) {
            val primitive = reflect.asPrimitive.get
            if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
              (primitive.primitiveType match {
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
                case _: PrimitiveType.Month          => monthCodec
                case _: PrimitiveType.Year           => yearCodec
                case _: PrimitiveType.MonthDay       => monthDayCodec
                case _: PrimitiveType.YearMonth      => yearMonthCodec
                case _: PrimitiveType.Period         => periodCodec
                case _: PrimitiveType.Duration       => durationCodec
                case _: PrimitiveType.Instant        => instantCodec
                case _: PrimitiveType.LocalDate      => localDateCodec
                case _: PrimitiveType.LocalTime      => localTimeCodec
                case _: PrimitiveType.LocalDateTime  => localDateTimeCodec
                case _: PrimitiveType.OffsetTime     => offsetTimeCodec
                case _: PrimitiveType.OffsetDateTime => offsetDateTimeCodec
                case _: PrimitiveType.ZonedDateTime  => zonedDateTimeCodec
                case _: PrimitiveType.ZoneId         => zoneIdCodec
                case _: PrimitiveType.ZoneOffset     => zoneOffsetCodec
                case _: PrimitiveType.Currency       => currencyCodec
                case _: PrimitiveType.UUID           => uuidCodec
              })
            } else primitive.primitiveBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isVariant) {
            val variant = reflect.asVariant.get
            if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
              val cases   = variant.cases
              val len     = cases.length
              val codecs  = new Array[ThriftBinaryCodec[?]](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                idx += 1
              }
              new ThriftBinaryCodec[A]() {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs

                def decodeUnsafe(protocol: TProtocol): A = {
                  val field   = protocol.readFieldBegin()
                  val caseIdx = field.id - 1
                  if (caseIdx >= 0 && caseIdx < caseCodecs.length) {
                    try {
                      val result = caseCodecs(caseIdx).asInstanceOf[ThriftBinaryCodec[A]].decodeUnsafe(protocol)
                      protocol.readFieldEnd()
                      // Read the STOP field
                      val stopField = protocol.readFieldBegin()
                      if (stopField.`type` != TType.STOP) {
                        // Skip any extra fields for forward compatibility
                        TProtocolUtil.skip(protocol, stopField.`type`)
                        protocol.readFieldEnd()
                        protocol.readFieldBegin() // Read actual STOP
                      }
                      result
                    } catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.Case(cases(caseIdx).name), error)
                    }
                  } else {
                    // Unknown case - skip the field
                    TProtocolUtil.skip(protocol, field.`type`)
                    protocol.readFieldEnd()
                    decodeError(s"Unknown variant case index: ${caseIdx + 1}")
                  }
                }

                def encode(value: A, protocol: TProtocol): Unit = {
                  val caseIdx = discriminator.discriminate(value)
                  val ttype   = getThriftType(cases(caseIdx).value)
                  protocol.writeFieldBegin(new TField("", ttype, (caseIdx + 1).toShort))
                  caseCodecs(caseIdx).asInstanceOf[ThriftBinaryCodec[A]].encode(value, protocol)
                  protocol.writeFieldStop()
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val elementCodec = deriveCodec(sequence.element).asInstanceOf[ThriftBinaryCodec[Elem]]
              val elementTType = getThriftType(sequence.element)
              new ThriftBinaryCodec[Col[Elem]]() {
                private[this] val constructor   = binding.constructor
                private[this] val deconstructor = binding.deconstructor

                def decodeUnsafe(protocol: TProtocol): Col[Elem] = {
                  val list    = protocol.readListBegin()
                  val size    = list.size
                  val builder = constructor.newObjectBuilder[Elem](size)
                  var idx     = 0
                  while (idx < size) {
                    try {
                      constructor.addObject(builder, elementCodec.decodeUnsafe(protocol))
                    } catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    idx += 1
                  }
                  constructor.resultObject(builder)
                }

                def encode(value: Col[Elem], protocol: TProtocol): Unit = {
                  val size = deconstructor.size(value)
                  protocol.writeListBegin(new TList(elementTType, size))
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) {
                    elementCodec.encode(it.next(), protocol)
                  }
                }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding    = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val keyCodec   = deriveCodec(map.key).asInstanceOf[ThriftBinaryCodec[Key]]
              val valueCodec = deriveCodec(map.value).asInstanceOf[ThriftBinaryCodec[Value]]
              val keyTType   = getThriftType(map.key)
              val valueTType = getThriftType(map.value)
              new ThriftBinaryCodec[Map[Key, Value]]() {
                private[this] val constructor   = binding.constructor
                private[this] val deconstructor = binding.deconstructor

                def decodeUnsafe(protocol: TProtocol): Map[Key, Value] = {
                  val mapBegin = protocol.readMapBegin()
                  val size     = mapBegin.size
                  val builder  = constructor.newObjectBuilder[Key, Value](size)
                  var idx      = 0
                  while (idx < size) {
                    try {
                      val k = keyCodec.decodeUnsafe(protocol)
                      val v = valueCodec.decodeUnsafe(protocol)
                      constructor.addObject(builder, k, v)
                    } catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                    idx += 1
                  }
                  constructor.resultObject(builder)
                }

                def encode(value: Map[Key, Value], protocol: TProtocol): Unit = {
                  val size = deconstructor.size(value)
                  protocol.writeMapBegin(new TMap(keyTType, valueTType, size))
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) {
                    val kv = it.next()
                    keyCodec.encode(deconstructor.getKey(kv), protocol)
                    valueCodec.encode(deconstructor.getValue(kv), protocol)
                  }
                }
              }
            } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val binding    = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields     = record.fields
              val fieldCount = fields.length
              val typeName   = record.typeId

              // Check for cached recursive codec
              val cache  = recursiveRecordCache.get()
              val cached = cache.get(typeName)
              if (cached ne null) {
                return new ThriftBinaryCodec[A]() {
                  def decodeUnsafe(protocol: TProtocol): A = {
                    val actualCodecs = cache.get(typeName)
                    if (actualCodecs != null && actualCodecs.length > 0)
                      actualCodecs(0).asInstanceOf[ThriftBinaryCodec[A]].decodeUnsafe(protocol)
                    else decodeError("Recursive type not yet initialized")
                  }
                  def encode(value: A, protocol: TProtocol): Unit = {
                    val actualCodecs = cache.get(typeName)
                    if (actualCodecs != null && actualCodecs.length > 0)
                      actualCodecs(0).asInstanceOf[ThriftBinaryCodec[A]].encode(value, protocol)
                    else throw new IllegalStateException("Recursive type not yet initialized")
                  }
                }
              }

              val isRecursive = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
              val placeholder = new Array[ThriftBinaryCodec[?]](1)
              if (isRecursive) cache.put(typeName, placeholder)

              val fieldCodecs = new Array[ThriftBinaryCodec[?]](fieldCount)
              var offset      = 0L
              var idx         = 0
              while (idx < fieldCount) {
                val codec = deriveCodec(fields(idx).value)
                fieldCodecs(idx) = codec
                offset = RegisterOffset.add(codec.valueOffset, offset)
                idx += 1
              }

              val codec = new ThriftBinaryCodec[A]() {
                private[this] val fieldNames    = fields.map(_.name)
                private[this] val constructor   = binding.constructor
                private[this] val deconstruct   = binding.deconstructor
                private[this] val usedRegisters = offset

                def decodeUnsafe(protocol: TProtocol): A = {
                  val regs  = Registers(usedRegisters)
                  var field = protocol.readFieldBegin()

                  while (field.`type` != TType.STOP) {
                    val fieldIdx = field.id - 1
                    if (fieldIdx >= 0 && fieldIdx < fieldCount) {
                      try {
                        val codec       = fieldCodecs(fieldIdx)
                        var fieldOffset = 0L
                        var i           = 0
                        while (i < fieldIdx) {
                          fieldOffset = RegisterOffset.add(fieldCodecs(i).valueOffset, fieldOffset)
                          i += 1
                        }
                        codec.valueType match {
                          case ThriftBinaryCodec.objectType =>
                            regs.setObject(
                              fieldOffset,
                              codec.asInstanceOf[ThriftBinaryCodec[AnyRef]].decodeUnsafe(protocol)
                            )
                          case ThriftBinaryCodec.intType =>
                            regs.setInt(fieldOffset, codec.asInstanceOf[ThriftBinaryCodec[Int]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.longType =>
                            regs
                              .setLong(fieldOffset, codec.asInstanceOf[ThriftBinaryCodec[Long]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.floatType =>
                            regs.setFloat(
                              fieldOffset,
                              codec.asInstanceOf[ThriftBinaryCodec[Float]].decodeUnsafe(protocol)
                            )
                          case ThriftBinaryCodec.doubleType =>
                            regs.setDouble(
                              fieldOffset,
                              codec.asInstanceOf[ThriftBinaryCodec[Double]].decodeUnsafe(protocol)
                            )
                          case ThriftBinaryCodec.booleanType =>
                            regs.setBoolean(
                              fieldOffset,
                              codec.asInstanceOf[ThriftBinaryCodec[Boolean]].decodeUnsafe(protocol)
                            )
                          case ThriftBinaryCodec.byteType =>
                            regs
                              .setByte(fieldOffset, codec.asInstanceOf[ThriftBinaryCodec[Byte]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.charType =>
                            regs
                              .setChar(fieldOffset, codec.asInstanceOf[ThriftBinaryCodec[Char]].decodeUnsafe(protocol))
                          case ThriftBinaryCodec.shortType =>
                            regs.setShort(
                              fieldOffset,
                              codec.asInstanceOf[ThriftBinaryCodec[Short]].decodeUnsafe(protocol)
                            )
                          case _ =>
                            codec.asInstanceOf[ThriftBinaryCodec[Unit]].decodeUnsafe(protocol)
                        }
                      } catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.Field(fieldNames(fieldIdx)), error)
                      }
                    } else {
                      // Unknown field - skip it to support schema evolution
                      TProtocolUtil.skip(protocol, field.`type`)
                    }
                    protocol.readFieldEnd()
                    field = protocol.readFieldBegin()
                  }

                  constructor.construct(regs, 0)
                }

                def encode(value: A, protocol: TProtocol): Unit = {
                  val regs = Registers(usedRegisters)
                  deconstruct.deconstruct(regs, 0, value)
                  var idx = 0
                  while (idx < fieldCount) {
                    val codec       = fieldCodecs(idx)
                    var fieldOffset = 0L
                    var i           = 0
                    while (i < idx) {
                      fieldOffset = RegisterOffset.add(fieldCodecs(i).valueOffset, fieldOffset)
                      i += 1
                    }

                    val ttype = getThriftType(fields(idx).value)
                    protocol.writeFieldBegin(new TField(fieldNames(idx), ttype, (idx + 1).toShort))
                    codec.valueType match {
                      case ThriftBinaryCodec.objectType =>
                        codec.asInstanceOf[ThriftBinaryCodec[AnyRef]].encode(regs.getObject(fieldOffset), protocol)
                      case ThriftBinaryCodec.intType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Int]].encode(regs.getInt(fieldOffset), protocol)
                      case ThriftBinaryCodec.longType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Long]].encode(regs.getLong(fieldOffset), protocol)
                      case ThriftBinaryCodec.floatType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Float]].encode(regs.getFloat(fieldOffset), protocol)
                      case ThriftBinaryCodec.doubleType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Double]].encode(regs.getDouble(fieldOffset), protocol)
                      case ThriftBinaryCodec.booleanType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Boolean]].encode(regs.getBoolean(fieldOffset), protocol)
                      case ThriftBinaryCodec.byteType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Byte]].encode(regs.getByte(fieldOffset), protocol)
                      case ThriftBinaryCodec.charType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Char]].encode(regs.getChar(fieldOffset), protocol)
                      case ThriftBinaryCodec.shortType =>
                        codec.asInstanceOf[ThriftBinaryCodec[Short]].encode(regs.getShort(fieldOffset), protocol)
                      case _ =>
                        codec.asInstanceOf[ThriftBinaryCodec[Unit]].encode((), protocol)
                    }
                    idx += 1
                  }
                  protocol.writeFieldStop()
                }
              }

              placeholder(0) = codec
              codec
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
          def decodeUnsafe(protocol: TProtocol): DynamicValue =
            decodeError("Cannot decode DynamicValue without schema")

          def encode(value: DynamicValue, protocol: TProtocol): Unit =
            value match {
              case DynamicValue.Primitive(pv) =>
                // Encode primitive value based on its specific type
                pv match {
                  case PrimitiveValue.String(v)         => stringCodec.encode(v, protocol)
                  case PrimitiveValue.Int(v)            => intCodec.encode(v, protocol)
                  case PrimitiveValue.Long(v)           => longCodec.encode(v, protocol)
                  case PrimitiveValue.Boolean(v)        => booleanCodec.encode(v, protocol)
                  case PrimitiveValue.Double(v)         => doubleCodec.encode(v, protocol)
                  case PrimitiveValue.Float(v)          => floatCodec.encode(v, protocol)
                  case PrimitiveValue.Short(v)          => shortCodec.encode(v, protocol)
                  case PrimitiveValue.Byte(v)           => byteCodec.encode(v, protocol)
                  case PrimitiveValue.Char(v)           => charCodec.encode(v, protocol)
                  case PrimitiveValue.BigInt(v)         => bigIntCodec.encode(v, protocol)
                  case PrimitiveValue.BigDecimal(v)     => bigDecimalCodec.encode(v, protocol)
                  case PrimitiveValue.Unit              => unitCodec.encode((), protocol)
                  case PrimitiveValue.UUID(v)           => uuidCodec.encode(v, protocol)
                  case PrimitiveValue.Instant(v)        => instantCodec.encode(v, protocol)
                  case PrimitiveValue.LocalDate(v)      => localDateCodec.encode(v, protocol)
                  case PrimitiveValue.LocalTime(v)      => localTimeCodec.encode(v, protocol)
                  case PrimitiveValue.LocalDateTime(v)  => localDateTimeCodec.encode(v, protocol)
                  case PrimitiveValue.OffsetTime(v)     => offsetTimeCodec.encode(v, protocol)
                  case PrimitiveValue.OffsetDateTime(v) => offsetDateTimeCodec.encode(v, protocol)
                  case PrimitiveValue.ZonedDateTime(v)  => zonedDateTimeCodec.encode(v, protocol)
                  case PrimitiveValue.ZoneId(v)         => zoneIdCodec.encode(v, protocol)
                  case PrimitiveValue.ZoneOffset(v)     => zoneOffsetCodec.encode(v, protocol)
                  case PrimitiveValue.DayOfWeek(v)      => dayOfWeekCodec.encode(v, protocol)
                  case PrimitiveValue.Month(v)          => monthCodec.encode(v, protocol)
                  case PrimitiveValue.MonthDay(v)       => monthDayCodec.encode(v, protocol)
                  case PrimitiveValue.Year(v)           => yearCodec.encode(v, protocol)
                  case PrimitiveValue.YearMonth(v)      => yearMonthCodec.encode(v, protocol)
                  case PrimitiveValue.Period(v)         => periodCodec.encode(v, protocol)
                  case PrimitiveValue.Duration(v)       => durationCodec.encode(v, protocol)
                  case PrimitiveValue.Currency(v)       => currencyCodec.encode(v, protocol)
                }
              case DynamicValue.Record(fields) =>
                var idx = 0
                fields.foreach { case (name, dv) =>
                  protocol.writeFieldBegin(new TField(name, TType.STRUCT, (idx + 1).toShort))
                  encode(dv, protocol)
                  idx += 1
                }
                protocol.writeFieldStop()
              case DynamicValue.Variant(caseName, dv) =>
                protocol.writeFieldBegin(new TField(caseName, TType.STRUCT, 1))
                encode(dv, protocol)
                protocol.writeFieldStop()
              case DynamicValue.Sequence(elements) =>
                protocol.writeListBegin(new TList(TType.STRUCT, elements.size))
                elements.foreach(encode(_, protocol))
              case DynamicValue.Map(entries) =>
                protocol.writeMapBegin(new TMap(TType.STRUCT, TType.STRUCT, entries.size))
                entries.foreach { case (k, v) =>
                  encode(k, protocol)
                  encode(v, protocol)
                }
            }
        }
      }
    )
