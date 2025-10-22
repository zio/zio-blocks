package zio.blocks.avro

import org.apache.avro.io.{BinaryEncoder, BinaryDecoder, DecoderFactory, EncoderFactory}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema._
import zio.blocks.schema.codec.{BinaryCodec, BinaryFormat}
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import java.io.OutputStream
import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import scala.collection.mutable
import scala.util.control.NonFatal

object AvroFormat
    extends BinaryFormat(
      "application/avro",
      new Deriver[BinaryCodec] {
        override def derivePrimitive[F[_, _], A](
          primitiveType: PrimitiveType[A],
          typeName: TypeName[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        ): Lazy[BinaryCodec[A]] = Lazy {
          deriveCodec(
            new Schema(
              new Reflect.Primitive(
                primitiveType = primitiveType,
                typeName = typeName,
                primitiveBinding = binding,
                doc = doc,
                modifiers = modifiers
              )
            )
          )
        }

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[A]] = Lazy {
          deriveCodec(
            new Schema(
              new Reflect.Record(
                fields = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
                typeName = typeName,
                recordBinding = binding,
                doc = doc,
                modifiers = modifiers
              )
            )
          )
        }

        override def deriveVariant[F[_, _], A](
          cases: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Variant, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[A]] = Lazy {
          deriveCodec(
            new Schema(
              new Reflect.Variant(
                cases = cases.asInstanceOf[IndexedSeq[Term[Binding, A, ? <: A]]],
                typeName = typeName,
                variantBinding = binding,
                doc = doc,
                modifiers = modifiers
              )
            )
          )
        }

        override def deriveSequence[F[_, _], C[_], A](
          element: Reflect[F, A],
          typeName: TypeName[C[A]],
          binding: Binding[BindingType.Seq[C], C[A]],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[C[A]]] = Lazy {
          deriveCodec(
            new Schema(
              Reflect.Sequence(
                element = element.asInstanceOf[Reflect[Binding, A]],
                typeName = typeName,
                seqBinding = binding,
                doc = doc,
                modifiers = modifiers
              )
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
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[M[K, V]]] = Lazy {
          deriveCodec(
            new Schema(
              new Reflect.Map(
                key = key.asInstanceOf[Reflect[Binding, K]],
                value = value.asInstanceOf[Reflect[Binding, V]],
                typeName = typeName,
                mapBinding = binding,
                doc = doc,
                modifiers = modifiers
              )
            )
          )
        }

        override def deriveDynamic[F[_, _]](
          binding: Binding[BindingType.Dynamic, DynamicValue],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit
          F: HasBinding[F],
          D: HasInstance[F]
        ): Lazy[BinaryCodec[DynamicValue]] = Lazy {
          deriveCodec(
            new Schema(
              new Reflect.Dynamic(
                dynamicBinding = binding,
                typeName = TypeName.dynamicValue,
                doc = doc,
                modifiers = modifiers
              )
            )
          )
        }

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeName: TypeName[A],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[A]] = Lazy {
          deriveCodec(
            new Schema(
              new Reflect.Wrapper(
                wrapped = wrapped.asInstanceOf[Reflect[Binding, B]],
                typeName = typeName,
                wrapperBinding = binding,
                doc = doc,
                modifiers = modifiers
              )
            )
          )
        }

        type Elem
        type Key
        type Value
        type Wrapped
        type Col[_]
        type Map[_, _]
        type TC[_]

        private[this] def deriveCodec[A](
          schema: Schema[A],
          cache: mutable.HashMap[TypeName[?], Array[AvroBinaryCodec[?]]] = new mutable.HashMap
        ): AvroBinaryCodec[A] = {
          val reflect = schema.reflect
          if (reflect.isPrimitive) {
            val primitiveType = reflect.asPrimitive.get.primitiveType
            primitiveType match {
              case _: PrimitiveType.Unit.type =>
                new AvroBinaryCodec[Unit](AvroBinaryCodec.unitType) {
                  def decode(d: BinaryDecoder): Unit = d.readNull()

                  def encode(x: Unit, e: BinaryEncoder): Unit = e.writeNull()
                }
              case _: PrimitiveType.Byte =>
                new AvroBinaryCodec[Byte](AvroBinaryCodec.byteType) {
                  def decode(d: BinaryDecoder): Byte = {
                    val x = d.readInt()
                    if (x <= Byte.MaxValue && x >= Byte.MinValue) x.toByte
                    else sys.error("Expected Byte")
                  }

                  def encode(x: Byte, e: BinaryEncoder): Unit = e.writeInt(x)
                }
              case _: PrimitiveType.Boolean =>
                new AvroBinaryCodec[Boolean](AvroBinaryCodec.booleanType) {
                  def decode(d: BinaryDecoder): Boolean = d.readBoolean()

                  def encode(x: Boolean, e: BinaryEncoder): Unit = e.writeBoolean(x)
                }
              case _: PrimitiveType.Short =>
                new AvroBinaryCodec[Short](AvroBinaryCodec.shortType) {
                  def decode(d: BinaryDecoder): Short = {
                    val x = d.readInt()
                    if (x <= Short.MaxValue && x >= Short.MinValue) x.toShort
                    else sys.error("Expected Short")
                  }

                  def encode(x: Short, e: BinaryEncoder): Unit = e.writeInt(x)
                }
              case _: PrimitiveType.Char =>
                new AvroBinaryCodec[Char](AvroBinaryCodec.charType) {
                  def decode(d: BinaryDecoder): Char = {
                    val x = d.readInt()
                    if (x <= Char.MaxValue && x >= Char.MinValue) x.toChar
                    else sys.error("Expected Char")
                  }

                  def encode(x: Char, e: BinaryEncoder): Unit = e.writeInt(x)
                }
              case _: PrimitiveType.Int =>
                new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
                  def decode(d: BinaryDecoder): Int = d.readInt()

                  def encode(x: Int, e: BinaryEncoder): Unit = e.writeInt(x)
                }
              case _: PrimitiveType.Float =>
                new AvroBinaryCodec[Float](AvroBinaryCodec.floatType) {
                  def decode(d: BinaryDecoder): Float = d.readFloat()

                  def encode(x: Float, e: BinaryEncoder): Unit = e.writeFloat(x)
                }
              case _: PrimitiveType.Long =>
                new AvroBinaryCodec[Long](AvroBinaryCodec.longType) {
                  def decode(d: BinaryDecoder): Long = d.readLong()

                  def encode(x: Long, e: BinaryEncoder): Unit = e.writeLong(x)
                }
              case _: PrimitiveType.Double =>
                new AvroBinaryCodec[Double](AvroBinaryCodec.doubleType) {
                  def decode(d: BinaryDecoder): Double = d.readDouble()

                  def encode(x: Double, e: BinaryEncoder): Unit = e.writeDouble(x)
                }
              case _: PrimitiveType.String =>
                new AvroBinaryCodec[String]() {
                  def decode(d: BinaryDecoder): String = d.readString()

                  def encode(x: String, e: BinaryEncoder): Unit = e.writeString(x)
                }
              case _: PrimitiveType.BigInt =>
                new AvroBinaryCodec[BigInt]() {
                  def decode(d: BinaryDecoder): BigInt = BigInt(d.readBytes(null).array())

                  def encode(x: BigInt, e: BinaryEncoder): Unit = e.writeBytes(x.toByteArray)
                }
              case _: PrimitiveType.BigDecimal =>
                new AvroBinaryCodec[BigDecimal]() {
                  def decode(d: BinaryDecoder): BigDecimal = {
                    val mantissa     = d.readBytes(null).array()
                    val scale        = d.readInt()
                    val precision    = d.readInt()
                    val roundingMode = java.math.RoundingMode.valueOf(d.readInt())
                    val mc           = new MathContext(precision, roundingMode)
                    new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
                  }

                  def encode(x: BigDecimal, e: BinaryEncoder): Unit = {
                    val bd = x.underlying
                    val mc = x.mc
                    e.writeBytes(ByteBuffer.wrap(bd.unscaledValue.toByteArray))
                    e.writeInt(bd.scale)
                    e.writeInt(mc.getPrecision)
                    e.writeInt(mc.getRoundingMode.ordinal)
                  }
                }
              case _: PrimitiveType.DayOfWeek =>
                new AvroBinaryCodec[java.time.DayOfWeek]() {
                  def decode(d: BinaryDecoder): java.time.DayOfWeek = java.time.DayOfWeek.of(d.readInt())

                  def encode(x: java.time.DayOfWeek, e: BinaryEncoder): Unit = e.writeInt(x.getValue)
                }
              case _: PrimitiveType.Duration =>
                new AvroBinaryCodec[java.time.Duration]() {
                  def decode(d: BinaryDecoder): java.time.Duration =
                    java.time.Duration.ofSeconds(d.readLong(), d.readInt())

                  def encode(x: java.time.Duration, e: BinaryEncoder): Unit = {
                    e.writeLong(x.getSeconds)
                    e.writeInt(x.getNano)
                  }
                }
              case _: PrimitiveType.Instant =>
                new AvroBinaryCodec[java.time.Instant]() {
                  def decode(d: BinaryDecoder): java.time.Instant =
                    java.time.Instant.ofEpochSecond(d.readLong(), d.readInt())

                  def encode(x: java.time.Instant, e: BinaryEncoder): Unit = {
                    e.writeLong(x.getEpochSecond)
                    e.writeInt(x.getNano)
                  }
                }
              case _: PrimitiveType.LocalDate =>
                new AvroBinaryCodec[java.time.LocalDate]() {
                  def decode(d: BinaryDecoder): java.time.LocalDate =
                    java.time.LocalDate.of(d.readInt(), d.readInt(), d.readInt())

                  def encode(x: java.time.LocalDate, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                  }
                }
              case _: PrimitiveType.LocalDateTime =>
                new AvroBinaryCodec[java.time.LocalDateTime]() {
                  def decode(d: BinaryDecoder): java.time.LocalDateTime =
                    java.time.LocalDateTime
                      .of(d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt())

                  def encode(x: java.time.LocalDateTime, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                  }
                }
              case _: PrimitiveType.LocalTime =>
                new AvroBinaryCodec[java.time.LocalTime]() {
                  def decode(d: BinaryDecoder): java.time.LocalTime =
                    java.time.LocalTime.of(d.readInt(), d.readInt(), d.readInt(), d.readInt())

                  def encode(x: java.time.LocalTime, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                  }
                }
              case _: PrimitiveType.Month =>
                new AvroBinaryCodec[java.time.Month]() {
                  def decode(d: BinaryDecoder): java.time.Month = java.time.Month.of(d.readInt())

                  def encode(x: java.time.Month, e: BinaryEncoder): Unit = e.writeInt(x.getValue)
                }
              case _: PrimitiveType.MonthDay =>
                new AvroBinaryCodec[java.time.MonthDay]() {
                  def decode(d: BinaryDecoder): java.time.MonthDay = java.time.MonthDay.of(d.readInt(), d.readInt())

                  def encode(x: java.time.MonthDay, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                  }
                }
              case _: PrimitiveType.OffsetDateTime =>
                new AvroBinaryCodec[java.time.OffsetDateTime]() {
                  def decode(d: BinaryDecoder): java.time.OffsetDateTime =
                    java.time.OffsetDateTime.of(
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      java.time.ZoneOffset.ofTotalSeconds(d.readInt())
                    )

                  def encode(x: java.time.OffsetDateTime, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                    e.writeInt(x.getOffset.getTotalSeconds)
                  }
                }
              case _: PrimitiveType.OffsetTime =>
                new AvroBinaryCodec[java.time.OffsetTime]() {
                  def decode(d: BinaryDecoder): java.time.OffsetTime =
                    java.time.OffsetTime.of(
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      java.time.ZoneOffset.ofTotalSeconds(d.readInt())
                    )

                  def encode(x: java.time.OffsetTime, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                    e.writeInt(x.getOffset.getTotalSeconds)
                  }
                }
              case _: PrimitiveType.Period =>
                new AvroBinaryCodec[java.time.Period]() {
                  def decode(d: BinaryDecoder): java.time.Period =
                    java.time.Period.of(d.readInt(), d.readInt(), d.readInt())

                  def encode(x: java.time.Period, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getYears)
                    e.writeInt(x.getMonths)
                    e.writeInt(x.getDays)
                  }
                }
              case _: PrimitiveType.Year =>
                new AvroBinaryCodec[java.time.Year]() {
                  def decode(d: BinaryDecoder): java.time.Year = java.time.Year.of(d.readInt())

                  def encode(x: java.time.Year, e: BinaryEncoder): Unit = e.writeInt(x.getValue)
                }
              case _: PrimitiveType.YearMonth =>
                new AvroBinaryCodec[java.time.YearMonth]() {
                  def decode(d: BinaryDecoder): java.time.YearMonth = java.time.YearMonth.of(d.readInt(), d.readInt())

                  def encode(x: java.time.YearMonth, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                  }
                }
              case _: PrimitiveType.ZoneId =>
                new AvroBinaryCodec[java.time.ZoneId]() {
                  def decode(d: BinaryDecoder): java.time.ZoneId = java.time.ZoneId.of(d.readString())

                  def encode(x: java.time.ZoneId, e: BinaryEncoder): Unit = e.writeString(x.toString)
                }
              case _: PrimitiveType.ZoneOffset =>
                new AvroBinaryCodec[java.time.ZoneOffset]() {
                  def decode(d: BinaryDecoder): java.time.ZoneOffset = java.time.ZoneOffset.ofTotalSeconds(d.readInt())

                  def encode(x: java.time.ZoneOffset, e: BinaryEncoder): Unit = e.writeInt(x.getTotalSeconds)
                }
              case _: PrimitiveType.ZonedDateTime =>
                new AvroBinaryCodec[java.time.ZonedDateTime]() {
                  def decode(d: BinaryDecoder): java.time.ZonedDateTime = java.time.ZonedDateTime.ofInstant(
                    java.time.LocalDateTime.of(
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt()
                    ),
                    java.time.ZoneOffset.ofTotalSeconds(d.readInt()),
                    java.time.ZoneId.of(d.readString())
                  )

                  def encode(x: java.time.ZonedDateTime, e: BinaryEncoder): Unit = {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                    e.writeInt(x.getOffset.getTotalSeconds)
                    e.writeString(x.getZone.toString)
                  }
                }
              case _: PrimitiveType.Currency =>
                new AvroBinaryCodec[java.util.Currency]() {
                  def decode(d: BinaryDecoder): java.util.Currency = {
                    val bs = new Array[Byte](3)
                    d.readFixed(bs, 0, 3)
                    java.util.Currency.getInstance(new String(bs))
                  }

                  def encode(x: java.util.Currency, e: BinaryEncoder): Unit = {
                    val s = x.toString
                    e.writeFixed(Array(s.charAt(0).toByte, s.charAt(1).toByte, s.charAt(2).toByte))
                  }
                }
              case _: PrimitiveType.UUID =>
                new AvroBinaryCodec[java.util.UUID]() {
                  def decode(d: BinaryDecoder): java.util.UUID = {
                    val bs = new Array[Byte](16)
                    d.readFixed(bs)
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

                  def encode(x: java.util.UUID, e: BinaryEncoder): Unit = {
                    val hi = x.getMostSignificantBits
                    val lo = x.getLeastSignificantBits
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
                    e.writeFixed(bs)
                  }
                }
            }
          } else if (reflect.isVariant) {
            val variant        = reflect.asVariant.get
            val variantBinding = {
              if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) variant.variantBinding
              else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding
            }.asInstanceOf[Binding.Variant[A]]
            new AvroBinaryCodec[A]() {
              private[this] val caseCodecs = cache.get(variant.typeName) match {
                case Some(x) => x
                case _       =>
                  val cases  = variant.cases
                  val codecs = new Array[AvroBinaryCodec[?]](cases.length)
                  cache.put(variant.typeName, codecs)
                  val len = cases.length
                  var idx = 0
                  while (idx < len) {
                    codecs(idx) = deriveCodec(new Schema(cases(idx).value), cache)
                    idx += 1
                  }
                  codecs
              }
              private[this] val discriminator = variantBinding.discriminator

              def decode(d: BinaryDecoder): A = {
                val idx = d.readInt()
                if (idx >= 0 && idx < caseCodecs.length) caseCodecs(idx).asInstanceOf[AvroBinaryCodec[A]].decode(d)
                else sys.error(s"Expected enum index from 0 to ${caseCodecs.length - 1}, got: $idx")
              }

              def encode(x: A, e: BinaryEncoder): Unit = {
                val idx = discriminator.discriminate(x)
                e.writeInt(idx)
                caseCodecs(idx).asInstanceOf[AvroBinaryCodec[A]].encode(x, e)
              }
            }
          } else if (reflect.isSequence) {
            val sequence   = reflect.asSequenceUnknown.get.sequence
            val seqBinding = {
              if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) sequence.seqBinding
              else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, Elem]].binding
            }.asInstanceOf[Binding.Seq[Col, Elem]]
            new AvroBinaryCodec[Col[Elem]]() {
              private[this] val elementCodec =
                deriveCodec(new Schema(sequence.element), cache).asInstanceOf[AvroBinaryCodec[Elem]]
              private[this] val deconstructor = seqBinding.deconstructor
              private[this] val constructor   = seqBinding.constructor

              def decode(d: BinaryDecoder): Col[Elem] = {
                val builder = constructor.newObjectBuilder[Elem](8)
                var size    = d.readLong()
                while (size > 0) {
                  while (size > 0) {
                    constructor.addObject(builder, elementCodec.decode(d))
                    size -= 1
                  }
                  size = d.readLong()
                }
                constructor.resultObject[Elem](builder)
              }

              def encode(x: Col[Elem], e: BinaryEncoder): Unit = {
                val size = deconstructor.size(x)
                if (size > 0) {
                  e.writeInt(size)
                  val it = deconstructor.deconstruct(x)
                  while (it.hasNext) elementCodec.encode(it.next(), e)
                }
                e.writeInt(0)
              }
            }
          } else if (reflect.isMap) {
            val map        = reflect.asMapUnknown.get.map
            val mapBinding = {
              if (map.mapBinding.isInstanceOf[Binding[?, ?]]) map.mapBinding
              else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, Value]].binding
            }.asInstanceOf[Binding.Map[Map, Key, Value]]
            new AvroBinaryCodec[Map[Key, Value]]() {
              private[this] val keyCodec   = deriveCodec(new Schema(map.key), cache).asInstanceOf[AvroBinaryCodec[Key]]
              private[this] val valueCodec =
                deriveCodec(new Schema(map.value), cache).asInstanceOf[AvroBinaryCodec[Value]]
              private[this] val deconstructor = mapBinding.deconstructor
              private[this] val constructor   = mapBinding.constructor

              def decode(d: BinaryDecoder): Map[Key, Value] = {
                val builder = constructor.newObjectBuilder[Key, Value](8)
                var size    = d.readLong()
                while (size > 0) {
                  while (size > 0) {
                    constructor.addObject(builder, keyCodec.decode(d), valueCodec.decode(d))
                    size -= 1
                  }
                  size = d.readLong()
                }
                constructor.resultObject[Key, Value](builder)
              }

              def encode(x: Map[Key, Value], e: BinaryEncoder): Unit = {
                val size = deconstructor.size(x)
                if (size > 0) {
                  e.writeInt(size)
                  val it = deconstructor.deconstruct(x)
                  while (it.hasNext) {
                    val kv = it.next()
                    keyCodec.encode(deconstructor.getKey(kv), e)
                    valueCodec.encode(deconstructor.getValue(kv), e)
                  }
                }
                e.writeInt(0)
              }
            }
          } else if (reflect.isRecord) {
            val record        = reflect.asRecord.get
            val recordBinding = {
              if (record.recordBinding.isInstanceOf[Binding[?, ?]]) record.recordBinding
              else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding
            }.asInstanceOf[Binding.Record[A]]
            new AvroBinaryCodec[A]() {
              private[this] val fieldCodecs = cache.get(record.typeName) match {
                case Some(x) => x
                case _       =>
                  val fields = record.fields
                  val len    = fields.length
                  val codecs = new Array[AvroBinaryCodec[?]](len)
                  cache.put(record.typeName, codecs)
                  var idx = 0
                  while (idx < len) {
                    codecs(idx) = deriveCodec(new Schema(fields(idx).value), cache)
                    idx += 1
                  }
                  codecs
              }
              private[this] val deconstructor = recordBinding.deconstructor
              private[this] val constructor   = recordBinding.constructor

              def decode(d: BinaryDecoder): A = {
                val registers = Registers(record.usedRegisters)
                var offset    = RegisterOffset.Zero
                val len       = fieldCodecs.length
                var idx       = 0
                while (idx < len) {
                  val codec = fieldCodecs(idx)
                  codec.valueType match {
                    case AvroBinaryCodec.objectType =>
                      registers.setObject(offset, 0, codec.asInstanceOf[AvroBinaryCodec[AnyRef]].decode(d))
                    case AvroBinaryCodec.intType     => registers.setInt(offset, 0, d.readInt())
                    case AvroBinaryCodec.longType    => registers.setLong(offset, 0, d.readLong())
                    case AvroBinaryCodec.floatType   => registers.setFloat(offset, 0, d.readFloat())
                    case AvroBinaryCodec.doubleType  => registers.setDouble(offset, 0, d.readDouble())
                    case AvroBinaryCodec.booleanType => registers.setBoolean(offset, 0, d.readBoolean())
                    case AvroBinaryCodec.byteType    =>
                      val x = d.readInt()
                      if (x <= Byte.MaxValue && x >= Byte.MinValue) registers.setByte(offset, 0, x.toByte)
                      else sys.error("Expected Byte")
                    case AvroBinaryCodec.charType =>
                      val x = d.readInt()
                      if (x <= Char.MaxValue && x >= Byte.MinValue) registers.setChar(offset, 0, x.toChar)
                      else sys.error("Expected Char")
                    case AvroBinaryCodec.shortType =>
                      val x = d.readInt()
                      if (x <= Short.MaxValue && x >= Short.MinValue) registers.setShort(offset, 0, x.toShort)
                      else sys.error("Expected Char")
                    case _ => ()
                  }
                  offset = RegisterOffset.add(offset, codec.valueOffset)
                  idx += 1
                }
                constructor.construct(registers, RegisterOffset.Zero)
              }

              def encode(x: A, e: BinaryEncoder): Unit = {
                val registers = Registers(record.usedRegisters)
                var offset    = RegisterOffset.Zero
                deconstructor.deconstruct(registers, offset, x)
                val len = fieldCodecs.length
                var idx = 0
                while (idx < len) {
                  val codec = fieldCodecs(idx)
                  codec.valueType match {
                    case AvroBinaryCodec.objectType =>
                      codec.asInstanceOf[AvroBinaryCodec[AnyRef]].encode(registers.getObject(offset, 0), e)
                    case AvroBinaryCodec.intType     => e.writeInt(registers.getInt(offset, 0))
                    case AvroBinaryCodec.longType    => e.writeLong(registers.getLong(offset, 0))
                    case AvroBinaryCodec.floatType   => e.writeFloat(registers.getFloat(offset, 0))
                    case AvroBinaryCodec.doubleType  => e.writeDouble(registers.getDouble(offset, 0))
                    case AvroBinaryCodec.booleanType => e.writeBoolean(registers.getBoolean(offset, 0))
                    case AvroBinaryCodec.byteType    => e.writeInt(registers.getByte(offset, 0))
                    case AvroBinaryCodec.charType    => e.writeInt(registers.getChar(offset, 0))
                    case AvroBinaryCodec.shortType   => e.writeInt(registers.getShort(offset, 0))
                    case _                           => ()
                  }
                  offset = RegisterOffset.add(offset, codec.valueOffset)
                  idx += 1
                }
              }
            }
          } else if (reflect.isWrapper) {
            val wrapper        = reflect.asWrapperUnknown.get.wrapper
            val wrapperBinding = {
              if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) wrapper.wrapperBinding
              else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding
            }.asInstanceOf[Binding.Wrapper[A, Wrapped]]
            new AvroBinaryCodec[A]() {
              private[this] val codec =
                deriveCodec(new Schema(wrapper.wrapped), cache).asInstanceOf[AvroBinaryCodec[Wrapped]]
              private[this] val unwrap = wrapperBinding.unwrap
              private[this] val wrap   = wrapperBinding.wrap

              def decode(d: BinaryDecoder): A = wrap(codec.decode(d)) match {
                case Right(x)  => x
                case Left(err) => sys.error(err)
              }

              def encode(x: A, e: BinaryEncoder): Unit = codec.encode(unwrap(x), e)
            }
          } else dynamicValueCodec
        }.asInstanceOf[AvroBinaryCodec[A]]

        private[this] val dynamicValueCodec = new AvroBinaryCodec[DynamicValue]() {
          private[this] val primitiveDynamicValueCodec = deriveCodec(Schema.derived[DynamicValue.Primitive])

          def decode(d: BinaryDecoder): DynamicValue = d.readInt() match {
            case 0 => primitiveDynamicValueCodec.decode(d)
            case 1 =>
              val builder = Vector.newBuilder[(String, DynamicValue)]
              var size    = d.readLong()
              while (size > 0) {
                while (size > 0) {
                  builder.addOne((d.readString(), decode(d)))
                  size -= 1
                }
                size = d.readLong()
              }
              new DynamicValue.Record(builder.result())
            case 2 => new DynamicValue.Variant(d.readString(), decode(d))
            case 3 =>
              val builder = Vector.newBuilder[DynamicValue]
              var size    = d.readLong()
              while (size > 0) {
                while (size > 0) {
                  builder.addOne(decode(d))
                  size -= 1
                }
                size = d.readLong()
              }
              new DynamicValue.Sequence(builder.result())
            case 4 =>
              val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
              var size    = d.readLong()
              while (size > 0) {
                while (size > 0) {
                  builder.addOne((decode(d), decode(d)))
                  size -= 1
                }
                size = d.readLong()
              }
              new DynamicValue.Map(builder.result())
            case _ => sys.error("Expected enum index from 0 to 4")
          }

          def encode(x: DynamicValue, e: BinaryEncoder): Unit = x match {
            case primitive: DynamicValue.Primitive =>
              e.writeInt(0)
              primitiveDynamicValueCodec.encode(primitive, e)
            case record: DynamicValue.Record =>
              e.writeInt(1)
              val fields = record.fields
              val size   = fields.length
              if (size > 0) {
                e.writeInt(size)
                val it = fields.iterator
                while (it.hasNext) {
                  val kv = it.next()
                  e.writeString(kv._1)
                  encode(kv._2, e)
                }
              }
              e.writeInt(0)
            case variant: DynamicValue.Variant =>
              e.writeInt(2)
              e.writeString(variant.caseName)
              encode(variant.value, e)
            case sequence: DynamicValue.Sequence =>
              e.writeInt(3)
              val elements = sequence.elements
              val size     = elements.length
              if (size > 0) {
                e.writeInt(size)
                val it = elements.iterator
                while (it.hasNext) {
                  encode(it.next(), e)
                }
              }
              e.writeInt(0)
            case map: DynamicValue.Map =>
              e.writeInt(4)
              val entries = map.entries
              val size    = entries.length
              if (size > 0) {
                e.writeInt(size)
                val it = entries.iterator
                while (it.hasNext) {
                  val kv = it.next()
                  encode(kv._1, e)
                  encode(kv._2, e)
                }
              }
              e.writeInt(0)
          }
        }
      }
    )

private abstract class AvroBinaryCodec[A](val valueType: Int = AvroBinaryCodec.objectType) extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case AvroBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case AvroBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case AvroBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case AvroBinaryCodec.charType    => RegisterOffset(chars = 1)
    case AvroBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case AvroBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case AvroBinaryCodec.intType     => RegisterOffset(ints = 1)
    case AvroBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case AvroBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                           => RegisterOffset.Zero
  }

  def decode(d: BinaryDecoder): A

  def encode(x: A, e: BinaryEncoder): Unit

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    var pos             = input.position
    val len             = input.limit - pos
    var bs: Array[Byte] = null
    if (input.hasArray) bs = input.array()
    else {
      pos = 0
      bs = new Array[Byte](len)
      input.get(bs)
    }
    val avroDecoder = DecoderFactory.get().binaryDecoder(bs, pos, len, null)
    try {
      new Right(decode(avroDecoder))
    } catch {
      case error if NonFatal(error) =>
        new Left(new SchemaError(new ::(new SchemaError.InvalidType(DynamicOptic.root, error.getMessage), Nil)))
    }
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val avroEncoder = EncoderFactory
      .get()
      .directBinaryEncoder(
        new OutputStream {
          override def write(b: Int): Unit = output.put(b.toByte)

          override def write(bs: Array[Byte]): Unit = output.put(bs)

          override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
        },
        null
      )
    encode(value, avroEncoder)
  }
}

private object AvroBinaryCodec {
  val objectType  = 0
  val booleanType = 1
  val byteType    = 2
  val charType    = 3
  val shortType   = 4
  val floatType   = 5
  val intType     = 6
  val doubleType  = 7
  val longType    = 8
  val unitType    = 9
}
