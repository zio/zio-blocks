package zio.blocks.avro

import org.apache.avro.generic.GenericData.Fixed
import org.apache.avro.generic.{
  GenericData,
  GenericDatumReader,
  GenericDatumWriter,
  GenericRecordBuilder,
  IndexedRecord
}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.util.Utf8
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema._
import zio.blocks.schema.codec.{BinaryCodec, BinaryFormat}
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import java.io.OutputStream
import java.math.{BigInteger, MathContext, RoundingMode}
import java.nio.ByteBuffer
import scala.jdk.CollectionConverters._
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
        type Value
        type Wrapped
        type Col[_]
        type Map[_, _]
        type TC[_]

        private def deriveCodec[A, B](
          schema: Schema[A],
          cache: mutable.HashMap[TypeName[?], Array[AvroBinaryCodec[?, ?]]] = new mutable.HashMap
        ): AvroBinaryCodec[A, B] = {
          val avroSchema = AvroSchemaCodec.toAvroSchema(schema)
          val reflect    = schema.reflect
          if (reflect.isPrimitive) {
            val primitiveType = reflect.asPrimitive.get.primitiveType
            primitiveType match {
              case _: PrimitiveType.Unit.type =>
                toAvroBinaryCodec(
                  avroSchema,
                  (_: Unit) => null,
                  (_: Null) => ()
                )
              case _: PrimitiveType.Byte =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: Byte) => x.toInt,
                  (x: Int) => if (x <= Byte.MaxValue && x >= Byte.MinValue) x.toByte else sys.error("Expected Byte")
                )
              case _: PrimitiveType.Char =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: Char) => x.toInt,
                  (x: Int) => if (x <= Char.MaxValue && x >= Char.MinValue) x.toChar else sys.error("Expected Char")
                )
              case _: PrimitiveType.Short =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: Short) => x.toInt,
                  (x: Int) => if (x <= Short.MaxValue && x >= Short.MinValue) x.toShort else sys.error("Expected Short")
                )
              case _: PrimitiveType.String =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: String) => new Utf8(x),
                  (x: Utf8) => x.toString
                )
              case _: PrimitiveType.BigInt =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: BigInt) => ByteBuffer.wrap(x.toByteArray),
                  (x: ByteBuffer) => BigInt(x.array)
                )
              case _: PrimitiveType.BigDecimal =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: BigDecimal) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = {
                        val bd = x.underlying
                        val mc = x.mc
                        i match {
                          case 0 => ByteBuffer.wrap(bd.unscaledValue.toByteArray)
                          case 1 => bd.scale
                          case 2 => mc.getPrecision
                          case _ => mc.getRoundingMode.ordinal
                        }
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) => {
                    val mantissa     = x.get(0).asInstanceOf[ByteBuffer].array
                    val scale        = x.get(1).asInstanceOf[Int]
                    val precision    = x.get(2).asInstanceOf[Int]
                    val roundingMode = java.math.RoundingMode.valueOf(x.get(3).asInstanceOf[Int])
                    val mc           = if (roundingMode == RoundingMode.HALF_UP) {
                      if (precision == 0) MathContext.UNLIMITED
                      else new MathContext(precision, roundingMode)
                    } else {
                      precision match {
                        case 7  => MathContext.DECIMAL32
                        case 16 => MathContext.DECIMAL64
                        case 34 => MathContext.DECIMAL128
                        case _  => new MathContext(precision, roundingMode)
                      }
                    }
                    new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
                  }
                )
              case _: PrimitiveType.DayOfWeek =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.DayOfWeek) => x.getValue,
                  (x: Int) => java.time.DayOfWeek.of(x)
                )
              case _: PrimitiveType.Duration =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.Duration) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getSeconds
                        case _ => x.getNano
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.Duration.ofSeconds(x.get(0).asInstanceOf[Long], x.get(1).asInstanceOf[Int])
                )
              case _: PrimitiveType.Instant =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.Instant) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getEpochSecond
                        case _ => x.getNano
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.Instant.ofEpochSecond(x.get(0).asInstanceOf[Long], x.get(1).asInstanceOf[Int])
                )
              case _: PrimitiveType.LocalDate =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.LocalDate) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getYear
                        case 1 => x.getMonth.getValue
                        case _ => x.getDayOfMonth
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.LocalDate
                      .of(x.get(0).asInstanceOf[Int], x.get(1).asInstanceOf[Int], x.get(2).asInstanceOf[Int])
                )
              case _: PrimitiveType.LocalDateTime =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.LocalDateTime) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getYear
                        case 1 => x.getMonthValue
                        case 2 => x.getDayOfMonth
                        case 3 => x.getHour
                        case 4 => x.getMinute
                        case 5 => x.getSecond
                        case _ => x.getNano
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.LocalDateTime.of(
                      x.get(0).asInstanceOf[Int],
                      x.get(1).asInstanceOf[Int],
                      x.get(2).asInstanceOf[Int],
                      x.get(3).asInstanceOf[Int],
                      x.get(4).asInstanceOf[Int],
                      x.get(5).asInstanceOf[Int],
                      x.get(6).asInstanceOf[Int]
                    )
                )
              case _: PrimitiveType.LocalTime =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.LocalTime) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getHour
                        case 1 => x.getMinute
                        case 2 => x.getSecond
                        case _ => x.getNano
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.LocalTime.of(
                      x.get(0).asInstanceOf[Int],
                      x.get(1).asInstanceOf[Int],
                      x.get(2).asInstanceOf[Int],
                      x.get(3).asInstanceOf[Int]
                    )
                )
              case _: PrimitiveType.Month =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.Month) => x.getValue,
                  (x: Int) => java.time.Month.of(x)
                )
              case _: PrimitiveType.MonthDay =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.MonthDay) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getMonthValue
                        case _ => x.getDayOfMonth
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.MonthDay.of(
                      x.get(0).asInstanceOf[Int],
                      x.get(1).asInstanceOf[Int]
                    )
                )
              case _: PrimitiveType.OffsetDateTime =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.OffsetDateTime) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getYear
                        case 1 => x.getMonthValue
                        case 2 => x.getDayOfMonth
                        case 3 => x.getHour
                        case 4 => x.getMinute
                        case 5 => x.getSecond
                        case 6 => x.getNano
                        case _ => x.getOffset.getTotalSeconds
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.OffsetDateTime.of(
                      x.get(0).asInstanceOf[Int],
                      x.get(1).asInstanceOf[Int],
                      x.get(2).asInstanceOf[Int],
                      x.get(3).asInstanceOf[Int],
                      x.get(4).asInstanceOf[Int],
                      x.get(5).asInstanceOf[Int],
                      x.get(6).asInstanceOf[Int],
                      java.time.ZoneOffset.ofTotalSeconds(x.get(7).asInstanceOf[Int])
                    )
                )
              case _: PrimitiveType.OffsetTime =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.OffsetTime) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getHour
                        case 1 => x.getMinute
                        case 2 => x.getSecond
                        case 3 => x.getNano
                        case _ => x.getOffset.getTotalSeconds
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.OffsetTime.of(
                      x.get(0).asInstanceOf[Int],
                      x.get(1).asInstanceOf[Int],
                      x.get(2).asInstanceOf[Int],
                      x.get(3).asInstanceOf[Int],
                      java.time.ZoneOffset.ofTotalSeconds(x.get(4).asInstanceOf[Int])
                    )
                )
              case _: PrimitiveType.Period =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.Period) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getYears
                        case 1 => x.getMonths
                        case _ => x.getDays
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.Period
                      .of(x.get(0).asInstanceOf[Int], x.get(1).asInstanceOf[Int], x.get(2).asInstanceOf[Int])
                )
              case _: PrimitiveType.Year =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.Year) => x.getValue,
                  (x: Int) => java.time.Year.of(x)
                )
              case _: PrimitiveType.YearMonth =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.YearMonth) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getYear
                        case _ => x.getMonthValue
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.YearMonth.of(
                      x.get(0).asInstanceOf[Int],
                      x.get(1).asInstanceOf[Int]
                    )
                )
              case _: PrimitiveType.ZoneId =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.ZoneId) => new Utf8(x.toString),
                  (x: Utf8) => java.time.ZoneId.of(x.toString)
                )
              case _: PrimitiveType.ZoneOffset =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.ZoneOffset) => x.getTotalSeconds,
                  (x: Int) => java.time.ZoneOffset.ofTotalSeconds(x)
                )
              case _: PrimitiveType.ZonedDateTime =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.time.ZonedDateTime) =>
                    new IndexedRecord {
                      override def put(i: Int, v: Any): Unit = ()

                      override def get(i: Int): Any = i match {
                        case 0 => x.getYear
                        case 1 => x.getMonthValue
                        case 2 => x.getDayOfMonth
                        case 3 => x.getHour
                        case 4 => x.getMinute
                        case 5 => x.getSecond
                        case 6 => x.getNano
                        case 7 => x.getOffset.getTotalSeconds
                        case _ => new Utf8(x.getZone.toString)
                      }

                      override def getSchema: AvroSchema = avroSchema
                    },
                  (x: IndexedRecord) =>
                    java.time.ZonedDateTime.ofInstant(
                      java.time.LocalDateTime.of(
                        x.get(0).asInstanceOf[Int],
                        x.get(1).asInstanceOf[Int],
                        x.get(2).asInstanceOf[Int],
                        x.get(3).asInstanceOf[Int],
                        x.get(4).asInstanceOf[Int],
                        x.get(5).asInstanceOf[Int],
                        x.get(6).asInstanceOf[Int]
                      ),
                      java.time.ZoneOffset.ofTotalSeconds(x.get(7).asInstanceOf[Int]),
                      java.time.ZoneId.of(x.get(8).asInstanceOf[Utf8].toString)
                    )
                )
              case _: PrimitiveType.Currency =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.util.Currency) => {
                    val s = x.toString
                    new Fixed(avroSchema, Array(s.charAt(0).toByte, s.charAt(1).toByte, s.charAt(2).toByte))
                  },
                  (x: Fixed) => java.util.Currency.getInstance(new String(x.bytes))
                )
              case _: PrimitiveType.UUID =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: java.util.UUID) => {
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
                    new Fixed(avroSchema, bs)
                  },
                  (x: Fixed) => {
                    val bs = x.bytes
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
              case _ =>
                toAvroBinaryCodec(
                  avroSchema,
                  (x: A) => x.asInstanceOf[B],
                  (x: B) => x.asInstanceOf[A]
                )
            }
          } else if (reflect.isVariant) {
            val variant        = reflect.asVariant.get
            val variantBinding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
            val cases          = variant.cases
            val discriminator  = variantBinding.discriminator
            val caseCodecs     = cache.get(variant.typeName) match {
              case Some(x) => x
              case _       =>
                val codecs = new Array[AvroBinaryCodec[?, ?]](cases.length)
                cache.put(variant.typeName, codecs)
                val len = cases.length
                var idx = 0
                while (idx < len) {
                  val reflect = cases(idx).value
                  codecs(idx) = deriveCodec(new Schema(reflect), cache)
                  idx += 1
                }
                codecs
            }
            val valueReaders = avroSchema.getTypes.asScala.map(as => new GenericDatumReader[Any](as))
            val valueWriters = avroSchema.getTypes.asScala.map(as => new GenericDatumWriter[Any](as))
            new AvroBinaryCodec[A, Any](null, null, null, null) {
              override def encode(value: A, output: ByteBuffer): Unit = {
                val idx         = discriminator.discriminate(value)
                val avroEncoder =
                  EncoderFactory
                    .get()
                    .directBinaryEncoder(
                      new OutputStream {
                        override def write(b: Int): Unit = output.put(b.toByte)

                        override def write(bs: Array[Byte]): Unit = output.put(bs)

                        override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
                      },
                      null
                    )
                avroEncoder.writeIndex(idx)
                valueWriters(idx).write(
                  caseCodecs(idx).encoder.asInstanceOf[A => Any](value),
                  avroEncoder
                )
              }

              override def decode(input: ByteBuffer): Either[SchemaError, A] = {
                val bs = new Array[Byte](input.limit - input.position)
                input.get(bs)
                val avroDecoder = DecoderFactory.get().binaryDecoder(bs, null)
                val idx         = avroDecoder.readIndex()
                if (idx >= 0 && idx <= caseCodecs.length) {
                  try {
                    val datum = valueReaders(idx).read(null.asInstanceOf[Any], avroDecoder)
                    new Right(caseCodecs(idx).decoder.asInstanceOf[Any => A](datum))
                  } catch {
                    case error if NonFatal(error) =>
                      new Left(
                        new SchemaError(new ::(new SchemaError.InvalidType(DynamicOptic.root, error.getMessage), Nil))
                      )
                  }
                } else
                  new Left(
                    new SchemaError(
                      new ::(
                        new SchemaError.InvalidType(
                          DynamicOptic.root,
                          s"Expected discriminator from 0 to ${caseCodecs.length - 1}"
                        ),
                        Nil
                      )
                    )
                  )
              }
            }
          } else if (reflect.isSequence) {
            val sequence   = reflect.asSequenceUnknown.get.sequence
            val seqBinding =
              try {
                sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              } catch {
                case _: Exception =>
                  sequence.seqBinding
                    .asInstanceOf[BindingInstance[TC, ?, Elem]]
                    .binding
                    .asInstanceOf[Binding.Seq[Col, Elem]]
              }
            val constructor   = seqBinding.constructor
            val deconstructor = seqBinding.deconstructor
            val element       = sequence.element
            val elementCodec  = deriveCodec(new Schema(element), cache)
            val encoder       = elementCodec.encoder.asInstanceOf[Elem => Any]
            val decoder       = elementCodec.decoder.asInstanceOf[Any => Elem]
            toAvroBinaryCodec[Col[Elem], Any](
              avroSchema,
              (x: Col[Elem]) => {
                val res = new java.util.ArrayList[Any]
                val it  = deconstructor.deconstruct(x)
                while (it.hasNext) res.add(encoder(it.next()))
                res
              },
              (x: Any) => {
                val array   = x.asInstanceOf[GenericData.AbstractArray[Elem]]
                val builder = constructor.newObjectBuilder[Elem](8)
                val it      = array.iterator()
                while (it.hasNext) {
                  constructor.addObject(builder, decoder(it.next()))
                }
                constructor.resultObject[Elem](builder)
              }
            )
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            map.key.asPrimitive match {
              case Some(primitiveKey) if primitiveKey.primitiveType.isInstanceOf[PrimitiveType.String] =>
                val mapBinding =
                  try {
                    map.mapBinding.asInstanceOf[Binding.Map[Map, String, Value]]
                  } catch {
                    case _: Exception =>
                      map.mapBinding
                        .asInstanceOf[BindingInstance[TC, ?, Value]]
                        .binding
                        .asInstanceOf[Binding.Map[Map, String, Value]]
                  }
                val constructor   = mapBinding.constructor
                val deconstructor = mapBinding.deconstructor
                val keyCodec      = deriveCodec(new Schema(primitiveKey), cache)
                val keyEncoder    = keyCodec.encoder.asInstanceOf[String => Any]
                val keyDecoder    = keyCodec.decoder.asInstanceOf[Any => String]
                val value         = map.value
                val valueCodec    = deriveCodec(new Schema(value), cache)
                val valueEncoder  = valueCodec.encoder.asInstanceOf[Value => Any]
                val valueDecoder  = valueCodec.decoder.asInstanceOf[Any => Value]
                toAvroBinaryCodec[Map[String, Value], Any](
                  avroSchema,
                  (x: Map[String, Value]) => {
                    val res = new java.util.HashMap[Any, Any]
                    val it  = deconstructor.deconstruct(x)
                    while (it.hasNext) {
                      val kv    = it.next()
                      val key   = deconstructor.getKey(kv)
                      val value = deconstructor.getValue(kv)
                      res.put(keyEncoder(key), valueEncoder(value))
                    }
                    res
                  },
                  (x: Any) => {
                    val map     = x.asInstanceOf[java.util.Map[Any, Any]].asScala
                    val builder = constructor.newObjectBuilder[String, Value](8)
                    val it      = map.iterator
                    while (it.hasNext) {
                      val kv = it.next()

                      constructor.addObject(builder, keyDecoder(kv._1), valueDecoder(kv._2))
                    }
                    constructor.resultObject[String, Value](builder)
                  }
                )
              case _ => sys.error(s"Expected string keys only")
            }
          } else if (reflect.isRecord) {
            val record        = reflect.asRecord.get
            val recordBinding =
              try {
                record.recordBinding.asInstanceOf[Binding.Record[A]]
              } catch {
                case _: Exception =>
                  record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding.asInstanceOf[Binding.Record[A]]
              }
            val constructor   = recordBinding.constructor
            val deconstructor = recordBinding.deconstructor
            val fields        = record.fields
            val fieldCodecs   = cache.get(record.typeName) match {
              case Some(x) => x
              case _       =>
                val codecs = new Array[AvroBinaryCodec[?, ?]](fields.length)
                cache.put(record.typeName, codecs)
                val len = fields.length
                var idx = 0
                while (idx < len) {
                  val reflect = fields(idx).value
                  codecs(idx) = deriveCodec(new Schema(reflect), cache)
                  idx += 1
                }
                codecs
            }
            toAvroBinaryCodec[A, IndexedRecord](
              avroSchema,
              (a: A) => {
                val builder   = new GenericRecordBuilder(avroSchema)
                val registers = Registers(record.usedRegisters)
                deconstructor.deconstruct(registers, RegisterOffset.Zero, a)
                var offset = RegisterOffset.Zero
                var idx    = -1
                fields.foreach { field =>
                  idx += 1
                  val encoder   = fieldCodecs(idx).encoder
                  val fieldName = field.name
                  val reflect   = field.value
                  if (reflect.isPrimitive) {
                    val primitiveType = reflect.asPrimitive.get.primitiveType
                    primitiveType match {
                      case _: PrimitiveType.Unit.type => ()
                      case _: PrimitiveType.Boolean   =>
                        builder.set(fieldName, encoder.asInstanceOf[Boolean => AnyRef](registers.getBoolean(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(booleans = 1))
                      case _: PrimitiveType.Byte =>
                        builder.set(fieldName, encoder.asInstanceOf[Byte => AnyRef](registers.getByte(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(bytes = 1))
                      case _: PrimitiveType.Char =>
                        builder.set(fieldName, encoder.asInstanceOf[Char => AnyRef](registers.getChar(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(chars = 1))
                      case _: PrimitiveType.Short =>
                        builder.set(fieldName, encoder.asInstanceOf[Short => AnyRef](registers.getShort(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(shorts = 1))
                      case _: PrimitiveType.Float =>
                        builder.set(fieldName, encoder.asInstanceOf[Float => AnyRef](registers.getFloat(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(floats = 1))
                      case _: PrimitiveType.Int =>
                        builder.set(fieldName, encoder.asInstanceOf[Int => AnyRef](registers.getInt(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(ints = 1))
                      case _: PrimitiveType.Double =>
                        builder.set(fieldName, encoder.asInstanceOf[Double => AnyRef](registers.getDouble(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(doubles = 1))
                      case _: PrimitiveType.Long =>
                        builder.set(fieldName, encoder.asInstanceOf[Long => AnyRef](registers.getLong(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(longs = 1))
                      case _ =>
                        builder.set(fieldName, encoder.asInstanceOf[AnyRef => AnyRef](registers.getObject(offset, 0)))
                        offset = RegisterOffset.add(offset, RegisterOffset(objects = 1))
                    }
                  } else {
                    builder.set(fieldName, encoder.asInstanceOf[AnyRef => AnyRef](registers.getObject(offset, 0)))
                    offset = RegisterOffset.add(offset, RegisterOffset(objects = 1))
                  }
                }
                builder.build()
              },
              (indexedRecord: IndexedRecord) => {
                val registers = Registers(record.usedRegisters)
                var offset    = RegisterOffset.Zero
                var idx       = -1
                fields.foreach { field =>
                  idx += 1
                  val decoder = fieldCodecs(idx).decoder
                  val reflect = field.value
                  if (reflect.isPrimitive) {
                    val primitiveType = reflect.asPrimitive.get.primitiveType
                    primitiveType match {
                      case _: PrimitiveType.Unit.type => ()
                      case _: PrimitiveType.Boolean   =>
                        registers
                          .setBoolean(offset, 0, decoder.asInstanceOf[AnyRef => Boolean](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(booleans = 1))
                      case _: PrimitiveType.Byte =>
                        registers.setByte(offset, 0, decoder.asInstanceOf[AnyRef => Byte](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(bytes = 1))
                      case _: PrimitiveType.Char =>
                        registers.setChar(offset, 0, decoder.asInstanceOf[AnyRef => Char](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(chars = 1))
                      case _: PrimitiveType.Short =>
                        registers.setShort(offset, 0, decoder.asInstanceOf[AnyRef => Short](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(shorts = 1))
                      case _: PrimitiveType.Float =>
                        registers.setFloat(offset, 0, decoder.asInstanceOf[AnyRef => Float](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(floats = 1))
                      case _: PrimitiveType.Int =>
                        registers.setInt(offset, 0, decoder.asInstanceOf[AnyRef => Int](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(ints = 1))
                      case _: PrimitiveType.Double =>
                        registers
                          .setDouble(offset, 0, decoder.asInstanceOf[AnyRef => Double](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(doubles = 1))
                      case _: PrimitiveType.Long =>
                        registers.setLong(offset, 0, decoder.asInstanceOf[AnyRef => Long](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(longs = 1))
                      case _ =>
                        registers.setObject(offset, 0, decoder.asInstanceOf[AnyRef => AnyRef](indexedRecord.get(idx)))
                        offset = RegisterOffset.add(offset, RegisterOffset(objects = 1))
                    }
                  } else {
                    registers.setObject(offset, 0, decoder.asInstanceOf[AnyRef => AnyRef](indexedRecord.get(idx)))
                    offset = RegisterOffset.add(offset, RegisterOffset(objects = 1))
                  }
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
                case _: Exception =>
                  wrapper.wrapperBinding
                    .asInstanceOf[BindingInstance[TC, ?, A]]
                    .binding
                    .asInstanceOf[Binding.Wrapper[A, Wrapped]]
              }
            val wrap   = wrapperBinding.wrap
            val unwrap = wrapperBinding.unwrap
            val codec  = deriveCodec(new Schema(wrapper.wrapped), cache)
            toAvroBinaryCodec[A, Any](
              avroSchema,
              (a: A) => codec.encoder.asInstanceOf[Wrapped => Any](unwrap(a)),
              (a: Any) =>
                wrap(codec.decoder.asInstanceOf[Any => Wrapped](a)) match {
                  case Right(x)  => x
                  case Left(err) => sys.error(err)
                }
            )
          } else if (reflect.isDynamic) {
            ???
          } else ???
        }.asInstanceOf[AvroBinaryCodec[A, B]]

        private def toAvroBinaryCodec[A, B](
          schemaAvro: AvroSchema,
          encoder: A => B,
          decoder: B => A
        ): AvroBinaryCodec[A, B] = {
          val reader = new GenericDatumReader[B](schemaAvro)
          val writer = new GenericDatumWriter[B](schemaAvro)
          new AvroBinaryCodec(reader, writer, encoder, decoder)
        }
      }
    )

private case class AvroBinaryCodec[A, B](
  reader: GenericDatumReader[B],
  writer: GenericDatumWriter[B],
  encoder: A => B,
  decoder: B => A
) extends BinaryCodec[A] {

  override def encode(value: A, output: ByteBuffer): Unit =
    writer.write(
      encoder(value),
      EncoderFactory
        .get()
        .directBinaryEncoder(
          new OutputStream {
            override def write(b: Int): Unit = output.put(b.toByte)

            override def write(bs: Array[Byte]): Unit = output.put(bs)

            override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
          },
          null
        )
    )

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val bs = new Array[Byte](input.limit - input.position)
    input.get(bs)
    val datum = reader.read(null.asInstanceOf[B], DecoderFactory.get().binaryDecoder(bs, null))
    try new Right(decoder(datum))
    catch {
      case error if NonFatal(error) =>
        new Left(new SchemaError(new ::(new SchemaError.InvalidType(DynamicOptic.root, error.getMessage), Nil)))
    }
  }
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
