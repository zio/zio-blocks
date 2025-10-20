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
        ): Lazy[BinaryCodec[A]] =
          Lazy(
            deriveCodec(
              new Schema(
                Reflect.Primitive(
                  primitiveType = primitiveType,
                  typeName = typeName,
                  primitiveBinding = binding,
                  doc = doc,
                  modifiers = modifiers
                )
              )
            )
          )

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[A]] =
          Lazy(
            deriveCodec(
              new Schema(
                Reflect.Record(
                  fields = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
                  typeName = typeName,
                  recordBinding = binding,
                  doc = doc,
                  modifiers = modifiers
                )
              )
            )
          )

        override def deriveVariant[F[_, _], A](
          cases: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Variant, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[A]] =
          Lazy(
            deriveCodec(
              new Schema(
                Reflect.Variant(
                  cases = cases.asInstanceOf[IndexedSeq[Term[Binding, A, ? <: A]]],
                  typeName = typeName,
                  variantBinding = binding,
                  doc = doc,
                  modifiers = modifiers
                )
              )
            )
          )

        override def deriveSequence[F[_, _], C[_], A](
          element: Reflect[F, A],
          typeName: TypeName[C[A]],
          binding: Binding[BindingType.Seq[C], C[A]],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[C[A]]] =
          Lazy(
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
          )

        override def deriveMap[F[_, _], M[_, _], K, V](
          key: Reflect[F, K],
          value: Reflect[F, V],
          typeName: TypeName[M[K, V]],
          binding: Binding[BindingType.Map[M], M[K, V]],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[M[K, V]]] =
          Lazy(
            deriveCodec(
              new Schema(
                Reflect.Map(
                  key = key.asInstanceOf[Reflect[Binding, K]],
                  value = value.asInstanceOf[Reflect[Binding, V]],
                  typeName = typeName,
                  mapBinding = binding,
                  doc = doc,
                  modifiers = modifiers
                )
              )
            )
          )

        override def deriveDynamic[F[_, _]](
          binding: Binding[BindingType.Dynamic, DynamicValue],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit
          F: HasBinding[F],
          D: HasInstance[F]
        ): Lazy[BinaryCodec[DynamicValue]] =
          Lazy(new BinaryCodec[DynamicValue] {
            override def encode(value: DynamicValue, output: ByteBuffer): Unit = ???

            override def decode(input: ByteBuffer): Either[SchemaError, DynamicValue] = ???
          })

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeName: TypeName[A],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BinaryCodec[A]] =
          Lazy(
            deriveCodec(
              new Schema(
                Reflect.Wrapper(
                  wrapped = wrapped.asInstanceOf[Reflect[Binding, B]],
                  typeName = typeName,
                  wrapperBinding = binding,
                  doc = doc,
                  modifiers = modifiers
                )
              )
            )
          )

        type Elem
        type Key
        type Value
        type Wrapped
        type Col[_]
        type Map[_, _]
        type TC[_]

        private def deriveCodec[A](
          schema: Schema[A],
          cache: mutable.HashMap[TypeName[?], Array[AvroBinaryCodec[?]]] = new mutable.HashMap
        ): AvroBinaryCodec[A] = {
          val reflect = schema.reflect
          if (reflect.isPrimitive) {
            val primitiveType = reflect.asPrimitive.get.primitiveType
            primitiveType match {
              case _: PrimitiveType.Unit.type =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.unitType,
                  (_: Unit, e: BinaryEncoder) => e.writeNull(),
                  (d: BinaryDecoder) => d.readNull()
                )
              case _: PrimitiveType.Byte =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.byteType,
                  (x: Byte, e: BinaryEncoder) => e.writeInt(x),
                  (d: BinaryDecoder) => {
                    val x = d.readInt()
                    if (x <= Byte.MaxValue && x >= Byte.MinValue) x.toByte else sys.error("Expected Byte")
                  }
                )
              case _: PrimitiveType.Boolean =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.booleanType,
                  (x: Boolean, e: BinaryEncoder) => e.writeBoolean(x),
                  (d: BinaryDecoder) => d.readBoolean()
                )
              case _: PrimitiveType.Short =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.shortType,
                  (x: Short, e: BinaryEncoder) => e.writeInt(x),
                  (d: BinaryDecoder) => {
                    val x = d.readInt()
                    if (x <= Short.MaxValue && x >= Short.MinValue) x.toShort else sys.error("Expected Short")
                  }
                )
              case _: PrimitiveType.Char =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.charType,
                  (x: Char, e: BinaryEncoder) => e.writeInt(x),
                  (d: BinaryDecoder) => {
                    val x = d.readInt()
                    if (x <= Char.MaxValue && x >= Char.MinValue) x.toChar else sys.error("Expected Char")
                  }
                )
              case _: PrimitiveType.Int =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.intType,
                  (x: Int, e: BinaryEncoder) => e.writeInt(x),
                  (d: BinaryDecoder) => d.readInt()
                )
              case _: PrimitiveType.Float =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.floatType,
                  (x: Float, e: BinaryEncoder) => e.writeFloat(x),
                  (d: BinaryDecoder) => d.readFloat()
                )
              case _: PrimitiveType.Long =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.longType,
                  (x: Long, e: BinaryEncoder) => e.writeLong(x),
                  (d: BinaryDecoder) => d.readLong()
                )
              case _: PrimitiveType.Double =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.doubleType,
                  (x: Double, e: BinaryEncoder) => e.writeDouble(x),
                  (d: BinaryDecoder) => d.readDouble()
                )
              case _: PrimitiveType.String =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: String, e: BinaryEncoder) => e.writeString(x),
                  (d: BinaryDecoder) => d.readString()
                )
              case _: PrimitiveType.BigInt =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: BigInt, e: BinaryEncoder) => e.writeBytes(x.toByteArray),
                  (d: BinaryDecoder) => BigInt(d.readBytes(null).array())
                )
              case _: PrimitiveType.BigDecimal =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: BigDecimal, e: BinaryEncoder) => {
                    val bd = x.underlying
                    val mc = x.mc
                    e.writeBytes(ByteBuffer.wrap(bd.unscaledValue.toByteArray))
                    e.writeInt(bd.scale)
                    e.writeInt(mc.getPrecision)
                    e.writeInt(mc.getRoundingMode.ordinal)
                  },
                  (d: BinaryDecoder) => {
                    val mantissa     = d.readBytes(null).array()
                    val scale        = d.readInt()
                    val precision    = d.readInt()
                    val roundingMode = java.math.RoundingMode.valueOf(d.readInt())
                    val mc           = new MathContext(precision, roundingMode)
                    new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
                  }
                )
              case _: PrimitiveType.DayOfWeek =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.DayOfWeek, e: BinaryEncoder) => e.writeInt(x.getValue),
                  (d: BinaryDecoder) => java.time.DayOfWeek.of(d.readInt())
                )
              case _: PrimitiveType.Duration =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.Duration, e: BinaryEncoder) => {
                    e.writeLong(x.getSeconds)
                    e.writeInt(x.getNano)
                  },
                  (d: BinaryDecoder) => java.time.Duration.ofSeconds(d.readLong(), d.readInt())
                )
              case _: PrimitiveType.Instant =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.Instant, e: BinaryEncoder) => {
                    e.writeLong(x.getEpochSecond)
                    e.writeInt(x.getNano)
                  },
                  (d: BinaryDecoder) => java.time.Instant.ofEpochSecond(d.readLong(), d.readInt())
                )
              case _: PrimitiveType.LocalDate =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.LocalDate, e: BinaryEncoder) => {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                  },
                  (d: BinaryDecoder) => java.time.LocalDate.of(d.readInt(), d.readInt(), d.readInt())
                )
              case _: PrimitiveType.LocalDateTime =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.LocalDateTime, e: BinaryEncoder) => {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                  },
                  (d: BinaryDecoder) =>
                    java.time.LocalDateTime
                      .of(d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt())
                )
              case _: PrimitiveType.LocalTime =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.LocalTime, e: BinaryEncoder) => {
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                  },
                  (d: BinaryDecoder) => java.time.LocalTime.of(d.readInt(), d.readInt(), d.readInt(), d.readInt())
                )
              case _: PrimitiveType.Month =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.Month, e: BinaryEncoder) => e.writeInt(x.getValue),
                  (d: BinaryDecoder) => java.time.Month.of(d.readInt())
                )
              case _: PrimitiveType.MonthDay =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.MonthDay, e: BinaryEncoder) => {
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                  },
                  (d: BinaryDecoder) => java.time.MonthDay.of(d.readInt(), d.readInt())
                )
              case _: PrimitiveType.OffsetDateTime =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.OffsetDateTime, e: BinaryEncoder) => {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                    e.writeInt(x.getOffset.getTotalSeconds)
                  },
                  (d: BinaryDecoder) =>
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
                )
              case _: PrimitiveType.OffsetTime =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.OffsetTime, e: BinaryEncoder) => {
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                    e.writeInt(x.getOffset.getTotalSeconds)
                  },
                  (d: BinaryDecoder) =>
                    java.time.OffsetTime.of(
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      d.readInt(),
                      java.time.ZoneOffset.ofTotalSeconds(d.readInt())
                    )
                )
              case _: PrimitiveType.Period =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.Period, e: BinaryEncoder) => {
                    e.writeInt(x.getYears)
                    e.writeInt(x.getMonths)
                    e.writeInt(x.getDays)
                  },
                  (d: BinaryDecoder) => java.time.Period.of(d.readInt(), d.readInt(), d.readInt())
                )
              case _: PrimitiveType.Year =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.Year, e: BinaryEncoder) => e.writeInt(x.getValue),
                  (d: BinaryDecoder) => java.time.Year.of(d.readInt())
                )
              case _: PrimitiveType.YearMonth =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.YearMonth, e: BinaryEncoder) => {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                  },
                  (d: BinaryDecoder) => java.time.YearMonth.of(d.readInt(), d.readInt())
                )
              case _: PrimitiveType.ZoneId =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.ZoneId, e: BinaryEncoder) => e.writeString(x.toString),
                  (d: BinaryDecoder) => java.time.ZoneId.of(d.readString())
                )
              case _: PrimitiveType.ZoneOffset =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.ZoneOffset, e: BinaryEncoder) => e.writeInt(x.getTotalSeconds),
                  (d: BinaryDecoder) => java.time.ZoneOffset.ofTotalSeconds(d.readInt())
                )
              case _: PrimitiveType.ZonedDateTime =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.time.ZonedDateTime, e: BinaryEncoder) => {
                    e.writeInt(x.getYear)
                    e.writeInt(x.getMonthValue)
                    e.writeInt(x.getDayOfMonth)
                    e.writeInt(x.getHour)
                    e.writeInt(x.getMinute)
                    e.writeInt(x.getSecond)
                    e.writeInt(x.getNano)
                    e.writeInt(x.getOffset.getTotalSeconds)
                    e.writeString(x.getZone.toString)
                  },
                  (d: BinaryDecoder) =>
                    java.time.ZonedDateTime.ofInstant(
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
                )
              case _: PrimitiveType.Currency =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.util.Currency, e: BinaryEncoder) => {
                    val s = x.toString
                    e.writeFixed(Array(s.charAt(0).toByte, s.charAt(1).toByte, s.charAt(2).toByte))
                  },
                  (d: BinaryDecoder) => {
                    val bs = new Array[Byte](3)
                    d.readFixed(bs, 0, 3)
                    java.util.Currency.getInstance(new String(bs))
                  }
                )
              case _: PrimitiveType.UUID =>
                toAvroBinaryCodec(
                  AvroBinaryCodec.objectType,
                  (x: java.util.UUID, e: BinaryEncoder) => {
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
                  },
                  (d: BinaryDecoder) => {
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
                )
            }
          } else if (reflect.isVariant) {
            val variant        = reflect.asVariant.get
            val variantBinding =
              try {
                variant.variantBinding.asInstanceOf[Binding.Variant[A]]
              } catch {
                case error if NonFatal(error) =>
                  variant.variantBinding
                    .asInstanceOf[BindingInstance[TC, ?, A]]
                    .binding
                    .asInstanceOf[Binding.Variant[A]]
              }
            val cases         = variant.cases
            val discriminator = variantBinding.discriminator
            val caseCodecs    = cache.get(variant.typeName) match {
              case Some(x) => x
              case _       =>
                val codecs = new Array[AvroBinaryCodec[?]](cases.length)
                cache.put(variant.typeName, codecs)
                val len = cases.length
                var idx = 0
                while (idx < len) {
                  val caseReflect = cases(idx).value
                  codecs(idx) = deriveCodec(new Schema(caseReflect), cache)
                  idx += 1
                }
                codecs
            }
            toAvroBinaryCodec[A](
              AvroBinaryCodec.objectType,
              (x: A, e: BinaryEncoder) => {
                val idx = discriminator.discriminate(x)
                e.writeInt(idx)
                caseCodecs(idx).encoder.asInstanceOf[(A, BinaryEncoder) => Unit](x, e)
              },
              (d: BinaryDecoder) => {
                val idx = d.readInt()
                if (idx < 0 || idx >= caseCodecs.length) {
                  sys.error(s"Expected enum index from 0 to ${caseCodecs.length - 1}, got: $idx")
                }
                caseCodecs(idx).decoder.asInstanceOf[BinaryDecoder => A](d)
              }
            )
          } else if (reflect.isSequence) {
            val sequence   = reflect.asSequenceUnknown.get.sequence
            val seqBinding =
              try {
                sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              } catch {
                case error if NonFatal(error) =>
                  sequence.seqBinding
                    .asInstanceOf[BindingInstance[TC, ?, Elem]]
                    .binding
                    .asInstanceOf[Binding.Seq[Col, Elem]]
              }
            val constructor   = seqBinding.constructor
            val deconstructor = seqBinding.deconstructor
            val element       = sequence.element
            val elementCodec  = deriveCodec(new Schema(element), cache)
            val encoder       = elementCodec.encoder.asInstanceOf[(Elem, BinaryEncoder) => Unit]
            val decoder       = elementCodec.decoder.asInstanceOf[BinaryDecoder => Elem]
            toAvroBinaryCodec[Col[Elem]](
              AvroBinaryCodec.objectType,
              (x: Col[Elem], e: BinaryEncoder) => {
                val size = deconstructor.size(x)
                if (size > 0) {
                  e.writeInt(size)
                  val it = deconstructor.deconstruct(x)
                  while (it.hasNext) encoder.apply(it.next(), e)
                }
                e.writeInt(0)
              },
              (d: BinaryDecoder) => {
                val builder = constructor.newObjectBuilder[Elem](8)
                var size    = d.readLong()
                while (size > 0) {
                  while (size > 0) {
                    constructor.addObject(builder, decoder.apply(d))
                    size -= 1
                  }
                  size = d.readLong()
                }
                constructor.resultObject[Elem](builder)
              }
            )
          } else if (reflect.isMap) {
            val map        = reflect.asMapUnknown.get.map
            val mapBinding =
              try {
                map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              } catch {
                case error if NonFatal(error) =>
                  map.mapBinding
                    .asInstanceOf[BindingInstance[TC, ?, Value]]
                    .binding
                    .asInstanceOf[Binding.Map[Map, Key, Value]]
              }
            val constructor   = mapBinding.constructor
            val deconstructor = mapBinding.deconstructor
            val keyCodec      = deriveCodec(new Schema(map.key), cache)
            val keyEncoder    = keyCodec.encoder.asInstanceOf[(Key, BinaryEncoder) => Unit]
            val keyDecoder    = keyCodec.decoder.asInstanceOf[BinaryDecoder => Key]
            val valueCodec    = deriveCodec(new Schema(map.value), cache)
            val valueEncoder  = valueCodec.encoder.asInstanceOf[(Value, BinaryEncoder) => Unit]
            val valueDecoder  = valueCodec.decoder.asInstanceOf[BinaryDecoder => Value]
            toAvroBinaryCodec[Map[Key, Value]](
              AvroBinaryCodec.objectType,
              (x: Map[Key, Value], e: BinaryEncoder) => {
                val size = deconstructor.size(x)
                if (size > 0) {
                  e.writeInt(size)
                  val it = deconstructor.deconstruct(x)
                  while (it.hasNext) {
                    val kv    = it.next()
                    val key   = deconstructor.getKey(kv)
                    val value = deconstructor.getValue(kv)
                    keyEncoder(key, e)
                    valueEncoder(value, e)
                  }
                }
                e.writeInt(0)
              },
              (d: BinaryDecoder) => {
                val builder = constructor.newObjectBuilder[Key, Value](8)
                var size    = d.readLong()
                while (size > 0) {
                  while (size > 0) {
                    constructor.addObject(builder, keyDecoder(d), valueDecoder(d))
                    size -= 1
                  }
                  size = d.readLong()
                }
                constructor.resultObject[Key, Value](builder)
              }
            )
          } else if (reflect.isRecord) {
            val record        = reflect.asRecord.get
            val recordBinding =
              try {
                record.recordBinding.asInstanceOf[Binding.Record[A]]
              } catch {
                case error if NonFatal(error) =>
                  record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding.asInstanceOf[Binding.Record[A]]
              }
            val constructor   = recordBinding.constructor
            val deconstructor = recordBinding.deconstructor
            val fieldTerms    = record.fields
            val fieldCodecs   = cache.get(record.typeName) match {
              case Some(x) => x
              case _       =>
                val len    = fieldTerms.length
                val codecs = new Array[AvroBinaryCodec[?]](len)
                cache.put(record.typeName, codecs)
                var idx = 0
                while (idx < len) {
                  val fieldReflect = fieldTerms(idx).value
                  codecs(idx) = deriveCodec(new Schema(fieldReflect), cache)
                  idx += 1
                }
                codecs
            }
            toAvroBinaryCodec[A](
              AvroBinaryCodec.objectType,
              (a: A, e: BinaryEncoder) => {
                val registers = Registers(record.usedRegisters)
                var offset    = RegisterOffset.Zero
                deconstructor.deconstruct(registers, offset, a)
                val len = fieldCodecs.length
                var idx = 0
                while (idx < len) {
                  val codec   = fieldCodecs(idx)
                  val encoder = codec.encoder
                  codec.valueType match {
                    case AvroBinaryCodec.objectType =>
                      encoder.asInstanceOf[(AnyRef, BinaryEncoder) => Unit](registers.getObject(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(objects = 1))
                    case AvroBinaryCodec.booleanType =>
                      encoder.asInstanceOf[(Boolean, BinaryEncoder) => Unit](registers.getBoolean(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(booleans = 1))
                    case AvroBinaryCodec.byteType =>
                      encoder.asInstanceOf[(Byte, BinaryEncoder) => Unit](registers.getByte(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(bytes = 1))
                    case AvroBinaryCodec.charType =>
                      encoder.asInstanceOf[(Char, BinaryEncoder) => Unit](registers.getChar(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(chars = 1))
                    case AvroBinaryCodec.shortType =>
                      encoder.asInstanceOf[(Short, BinaryEncoder) => Unit](registers.getShort(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(shorts = 1))
                    case AvroBinaryCodec.floatType =>
                      encoder.asInstanceOf[(Float, BinaryEncoder) => Unit](registers.getFloat(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(floats = 1))
                    case AvroBinaryCodec.intType =>
                      encoder.asInstanceOf[(Int, BinaryEncoder) => Unit](registers.getInt(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(ints = 1))
                    case AvroBinaryCodec.doubleType =>
                      encoder.asInstanceOf[(Double, BinaryEncoder) => Unit](registers.getDouble(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(doubles = 1))
                    case AvroBinaryCodec.longType =>
                      encoder.asInstanceOf[(Long, BinaryEncoder) => Unit](registers.getLong(offset, 0), e)
                      offset = RegisterOffset.add(offset, RegisterOffset(longs = 1))
                    case _ => ()
                  }
                  idx += 1
                }
              },
              (d: BinaryDecoder) => {
                val registers = Registers(record.usedRegisters)
                var offset    = RegisterOffset.Zero
                val len       = fieldCodecs.length
                var idx       = 0
                while (idx < len) {
                  val codec   = fieldCodecs(idx)
                  val decoder = codec.decoder
                  codec.valueType match {
                    case AvroBinaryCodec.`objectType` =>
                      registers.setObject(offset, 0, decoder.asInstanceOf[BinaryDecoder => AnyRef](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(objects = 1))
                    case AvroBinaryCodec.booleanType =>
                      registers.setBoolean(offset, 0, decoder.asInstanceOf[BinaryDecoder => Boolean](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(booleans = 1))
                    case AvroBinaryCodec.byteType =>
                      registers.setByte(offset, 0, decoder.asInstanceOf[BinaryDecoder => Byte](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(bytes = 1))
                    case AvroBinaryCodec.charType =>
                      registers.setChar(offset, 0, decoder.asInstanceOf[BinaryDecoder => Char](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(chars = 1))
                    case AvroBinaryCodec.shortType =>
                      registers.setShort(offset, 0, decoder.asInstanceOf[BinaryDecoder => Short](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(shorts = 1))
                    case AvroBinaryCodec.floatType =>
                      registers.setFloat(offset, 0, decoder.asInstanceOf[BinaryDecoder => Float](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(floats = 1))
                    case AvroBinaryCodec.intType =>
                      registers.setInt(offset, 0, decoder.asInstanceOf[BinaryDecoder => Int](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(ints = 1))
                    case AvroBinaryCodec.doubleType =>
                      registers.setDouble(offset, 0, decoder.asInstanceOf[BinaryDecoder => Double](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(doubles = 1))
                    case AvroBinaryCodec.longType =>
                      registers.setLong(offset, 0, decoder.asInstanceOf[BinaryDecoder => Long](d))
                      offset = RegisterOffset.add(offset, RegisterOffset(longs = 1))
                    case _ => ()
                  }
                  idx += 1
                }
                constructor.construct(registers, RegisterOffset.Zero)
              }
            )
          } else if (reflect.isWrapper) {
            val wrapper        = reflect.asWrapperUnknown.get.wrapper
            val wrapperBinding =
              try {
                wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              } catch {
                case error if NonFatal(error) =>
                  wrapper.wrapperBinding
                    .asInstanceOf[BindingInstance[TC, ?, A]]
                    .binding
                    .asInstanceOf[Binding.Wrapper[A, Wrapped]]
              }
            val wrap    = wrapperBinding.wrap
            val unwrap  = wrapperBinding.unwrap
            val codec   = deriveCodec(new Schema(wrapper.wrapped), cache)
            val encoder = codec.encoder.asInstanceOf[(Wrapped, BinaryEncoder) => Unit]
            val decoder = codec.decoder.asInstanceOf[BinaryDecoder => Wrapped]
            toAvroBinaryCodec[A](
              AvroBinaryCodec.objectType,
              (x: A, e: BinaryEncoder) => encoder(unwrap(x), e),
              (d: BinaryDecoder) =>
                wrap(decoder(d)) match {
                  case Right(x)  => x
                  case Left(err) => sys.error(err)
                }
            )
          } else if (reflect.isDynamic) {
            ???
          } else ???
        }.asInstanceOf[AvroBinaryCodec[A]]

        private def toAvroBinaryCodec[A](
          valueType: Int,
          encoder: (A, BinaryEncoder) => Unit,
          decoder: BinaryDecoder => A
        ): AvroBinaryCodec[A] = new AvroBinaryCodec(valueType, encoder, decoder)
      }
    )

private case class AvroBinaryCodec[A](
  valueType: Int,
  encoder: (A, BinaryEncoder) => Unit,
  decoder: BinaryDecoder => A
) extends BinaryCodec[A] {

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
    encoder(value, avroEncoder)
  }

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
      new Right(decoder(avroDecoder))
    } catch {
      case error if NonFatal(error) =>
        new Left(new SchemaError(new ::(new SchemaError.InvalidType(DynamicOptic.root, error.getMessage), Nil)))
    }
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

/*
Provide schema-based codec API with best-in-class:
   1) safety:
      - default derivation/runtime configuration that prevents DoS by limiting CPU/memory usage
      - no error accumulation
   2) correctness:
      - using codecs instead decoders/encoders to ensure that data can be parsed after serilazation
      - using 3-rd party tests/serializers in CI
      - providing golden test utilities
   3) usability:
      - no extra dependencies in core modules except standard Java/Scala libraries
      - functional interface on the top-level
      - exception thowing only for fatal errors: thread interruption, out of memory, etc.
      - full-featured support of strict typed values: primitive and data-time values, product and sum types, collections, etc.
      - full-featured support of `zio.blocks.schema.DynamicValue`
      - derivation configuration on par with 3-rd party solutions (avoid annotations where possible)
      - configurable in runtime error reporting (concise by default with optional stack traces and hex dumps for debugging)
      - site with user documentation: API docs, tutorial and how-to guides
      - documentation generation tools for Avro Schema, JSON Schema, OpenAPI, .proto files, etc.
      - code generation tools for data structures from JSON Schema, OpenAPI, Avro Schema, .proto files, etc.
   4) performance:
      - imperative interface with stack-less exceptions on low-levels
      - using internal byte array buffering for faster byte access, IO batching and error reporting
      - direct parsing/serialization for strict types without allocations and heavy lifting to ADTs
      - use fast paths with SWAR-techiques for buffered input/output without escaping sequences (\n, \uFFFF, etc.)
      - use slow paths for error throwing, buffer flushing/prefetching, or escaped sequence parsing (\n, \uFFFF, etc.)
      - move as possible configuration to compile-time codec generation
      - minimize initialization for fast start-up and low-latency runtime
      - benchmarks for synthetic and real-world message samples
   5) integrations:
      - newtype/subtype/refined-type libraries
      - HTTP-service frameworks
      - non-blocking parsing/serialization of value sequences (async, virtual threads)
      - 3-party dynamic values (Avro GenericRecord, JSON ADTs)

While usability is preferred over performance, we should provide following abilities and configurable trade offs:

1. Parsing from:
   1) byte buffers (full parsing or within specified starting position and limit)
   2) byte arrays (full parsing or within specified starting position and limit)
   3) Java's input streams (full parsing or callbacks/iterators for sequences of values)
   4) strings ??? (for non UTF-8 decoding and testing/debugging of JSON)

3. Serialization to:
   1) byte buffers, including preallocated with specified starting position and limit
   2) byte arrays, including preallocated with specified starting position and limit
   3) Java's output streams
   4) strings ??? (for non UTF-8 encoding and testing/debugging of JSON/XML)

4. Two kinds of codecs:
   1) for values
   2) for keys with stringified representation (Avro maps/JSON objects)

5. Passing some parsing or serialization context in codec runtime for:
   1) error positioning (schema path/absolute position)
   2) indentation (JSON/XML)
   3) commas (JSON)

6. Use internal in/out buffers for:
   1) faster byte access using SWAR techniques with direct byte buffers and Java's streams
   2) faster batched IO with Java's streams
   3) printing hex dumps of parsing buffers for errors (helpful for syntax and binary encoding errors)

 */
