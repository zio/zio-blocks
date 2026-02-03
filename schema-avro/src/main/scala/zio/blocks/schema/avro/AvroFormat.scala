package zio.blocks.schema.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema.binding.SeqDeconstructor._
import zio.blocks.schema._
import zio.blocks.chunk.ChunkBuilder
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.TypeId
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object AvroFormat
    extends BinaryFormat(
      "application/avro",
      new Deriver[AvroBinaryCodec] {
        override def derivePrimitive[A](
          primitiveType: PrimitiveType[A],
          typeId: TypeId[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        ): Lazy[AvroBinaryCodec[A]] =
          Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeId, binding, doc, modifiers)))

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeId: TypeId[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
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
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
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
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[C[A]],
          examples: Seq[C[A]]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[C[A]]] = Lazy {
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
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[M[K, V]],
          examples: Seq[M[K, V]]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[M[K, V]]] = Lazy {
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
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[DynamicValue],
          examples: Seq[DynamicValue]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[DynamicValue]] =
          Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeId.of[DynamicValue], doc, modifiers)))

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeId: TypeId[A],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroBinaryCodec[A]] = Lazy {
          deriveCodec(
            new Reflect.Wrapper(
              wrapped.asInstanceOf[Reflect[Binding, B]],
              typeId,
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
          new ThreadLocal[java.util.HashMap[TypeId[?], (Array[AvroBinaryCodec[?]], AvroSchema)]] {
            override def initialValue: java.util.HashMap[TypeId[?], (Array[AvroBinaryCodec[?]], AvroSchema)] =
              new java.util.HashMap
          }
        private[this] val recordCounters =
          new ThreadLocal[java.util.HashMap[(String, String), Int]] {
            override def initialValue: java.util.HashMap[(String, String), Int] = new java.util.HashMap
          }

        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): AvroBinaryCodec[A] = {
          if (reflect.isPrimitive) {
            val primitive = reflect.asPrimitive.get
            if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
              primitive.primitiveType match {
                case _: PrimitiveType.Unit.type      => AvroBinaryCodec.unitCodec
                case _: PrimitiveType.Boolean        => AvroBinaryCodec.booleanCodec
                case _: PrimitiveType.Byte           => AvroBinaryCodec.byteCodec
                case _: PrimitiveType.Short          => AvroBinaryCodec.shortCodec
                case _: PrimitiveType.Int            => AvroBinaryCodec.intCodec
                case _: PrimitiveType.Long           => AvroBinaryCodec.longCodec
                case _: PrimitiveType.Float          => AvroBinaryCodec.floatCodec
                case _: PrimitiveType.Double         => AvroBinaryCodec.doubleCodec
                case _: PrimitiveType.Char           => AvroBinaryCodec.charCodec
                case _: PrimitiveType.String         => AvroBinaryCodec.stringCodec
                case _: PrimitiveType.BigInt         => AvroBinaryCodec.bigIntCodec
                case _: PrimitiveType.BigDecimal     => AvroBinaryCodec.bigDecimalCodec
                case _: PrimitiveType.DayOfWeek      => AvroBinaryCodec.dayOfWeekCodec
                case _: PrimitiveType.Duration       => AvroBinaryCodec.durationCodec
                case _: PrimitiveType.Instant        => AvroBinaryCodec.instantCodec
                case _: PrimitiveType.LocalDate      => AvroBinaryCodec.localDateCodec
                case _: PrimitiveType.LocalDateTime  => AvroBinaryCodec.localDateTimeCodec
                case _: PrimitiveType.LocalTime      => AvroBinaryCodec.localTimeCodec
                case _: PrimitiveType.Month          => AvroBinaryCodec.monthCodec
                case _: PrimitiveType.MonthDay       => AvroBinaryCodec.monthDayCodec
                case _: PrimitiveType.OffsetDateTime => AvroBinaryCodec.offsetDateTimeCodec
                case _: PrimitiveType.OffsetTime     => AvroBinaryCodec.offsetTimeCodec
                case _: PrimitiveType.Period         => AvroBinaryCodec.periodCodec
                case _: PrimitiveType.Year           => AvroBinaryCodec.yearCodec
                case _: PrimitiveType.YearMonth      => AvroBinaryCodec.yearMonthCodec
                case _: PrimitiveType.ZoneId         => AvroBinaryCodec.zoneIdCodec
                case _: PrimitiveType.ZoneOffset     => AvroBinaryCodec.zoneOffsetCodec
                case _: PrimitiveType.ZonedDateTime  => AvroBinaryCodec.zonedDateTimeCodec
                case _: PrimitiveType.Currency       => AvroBinaryCodec.currencyCodec
                case _: PrimitiveType.UUID           => AvroBinaryCodec.uuidCodec
              }
            } else primitive.primitiveBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isVariant) {
            val variant = reflect.asVariant.get
            if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
              val cases   = variant.cases
              val len     = cases.length
              val codecs  = new Array[AvroBinaryCodec[?]](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                idx += 1
              }
              new AvroBinaryCodec[A]() {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs

                val avroSchema: AvroSchema = {
                  val len             = codecs.length
                  val caseAvroSchemas = new java.util.ArrayList[AvroSchema](len)
                  var idx             = 0
                  while (idx < len) {
                    caseAvroSchemas.add(codecs(idx).avroSchema)
                    idx += 1
                  }
                  AvroSchema.createUnion(caseAvroSchemas)
                }

                def decodeUnsafe(decoder: BinaryDecoder): A = {
                  val idx = decoder.readInt()
                  if (idx >= 0 && idx < caseCodecs.length) {
                    try caseCodecs(idx).asInstanceOf[AvroBinaryCodec[A]].decodeUnsafe(decoder)
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
                    }
                  } else decodeError(s"Expected enum index from 0 to ${caseCodecs.length - 1}, got $idx")
                }

                def encode(value: A, encoder: BinaryEncoder): Unit = {
                  val idx = discriminator.discriminate(value)
                  encoder.writeInt(idx)
                  caseCodecs(idx).asInstanceOf[AvroBinaryCodec[A]].encode(value, encoder)
                }
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec   = deriveCodec(sequence.element).asInstanceOf[AvroBinaryCodec[Elem]]
              codec.valueType match {
                case AvroBinaryCodec.booleanType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Boolean]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Boolean]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Boolean] = {
                      val builder = constructor.newBuilder[Boolean](8)(ClassTag.Boolean)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addBoolean(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Boolean], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.byteType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Byte]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Byte]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Byte] = {
                      val builder = constructor.newBuilder[Byte](8)(ClassTag.Byte)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addByte(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Byte], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.charType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Char]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Char]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Char] = {
                      val builder = constructor.newBuilder[Char](8)(ClassTag.Char)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addChar(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Char], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.shortType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Short]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Short]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Short] = {
                      val builder = constructor.newBuilder[Short](8)(ClassTag.Short)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addShort(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Short], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.floatType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Float]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Float]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Float] = {
                      val builder = constructor.newBuilder[Float](8)(ClassTag.Float)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addFloat(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Float], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.intType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Int]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Int]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Int] = {
                      val builder = constructor.newBuilder[Int](8)(ClassTag.Int)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addInt(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Int], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.doubleType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Double]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Double]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Double] = {
                      val builder = constructor.newBuilder[Double](8)(ClassTag.Double)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addDouble(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Double], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case AvroBinaryCodec.longType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroBinaryCodec[Col[Long]]() {
                    private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroBinaryCodec[Long]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Long] = {
                      val builder = constructor.newBuilder[Long](8)(ClassTag.Long)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addLong(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Long], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case _ =>
                  new AvroBinaryCodec[Col[Elem]]() {
                    private[this] val deconstructor = binding.deconstructor
                    private[this] val constructor   = binding.constructor
                    private[this] val elementCodec  = codec
                    private[this] val elemClassTag  = sequence.elemClassTag.asInstanceOf[ClassTag[Elem]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeUnsafe(decoder: BinaryDecoder): Col[Elem] = {
                      val builder = constructor.newBuilder[Elem](8)(elemClassTag)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroBinaryCodec.maxCollectionSize) {
                          decodeError(
                            s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.add(builder, elementCodec.decodeUnsafe(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      }
                      if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encode(value: Col[Elem], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encode(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val codec1  = deriveCodec(map.key).asInstanceOf[AvroBinaryCodec[Key]]
              val codec2  = deriveCodec(map.value).asInstanceOf[AvroBinaryCodec[Value]]
              new AvroBinaryCodec[Map[Key, Value]]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val keyCodec      = codec1
                private[this] val valueCodec    = codec2
                private[this] val keyReflect    = map.key.asInstanceOf[Reflect.Bound[Key]]

                val avroSchema: AvroSchema = map.key.asPrimitive match {
                  case Some(primitiveKey) if primitiveKey.primitiveType.isInstanceOf[PrimitiveType.String] =>
                    AvroSchema.createMap(valueCodec.avroSchema)
                  case _ =>
                    val fields = new java.util.ArrayList[AvroSchema.Field](2)
                    fields.add(new AvroSchema.Field("_1", keyCodec.avroSchema))
                    fields.add(new AvroSchema.Field("_2", valueCodec.avroSchema))
                    AvroSchema.createArray(createAvroRecord("scala", "Tuple2", fields))
                }

                def decodeUnsafe(decoder: BinaryDecoder): Map[Key, Value] = {
                  val builder = constructor.newObjectBuilder[Key, Value](8)
                  var count   = 0L
                  var size    = 0
                  while ({
                    size = decoder.readInt()
                    size > 0
                  }) {
                    if (count + size > AvroBinaryCodec.maxCollectionSize) {
                      decodeError(
                        s"Expected map size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                      )
                    }
                    while (size > 0) {
                      val k =
                        try keyCodec.decodeUnsafe(decoder)
                        catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                        }
                      val v =
                        try valueCodec.decodeUnsafe(decoder)
                        catch {
                          case error if NonFatal(error) =>
                            decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), error)
                        }
                      constructor.addObject(builder, k, v)
                      count += 1
                      size -= 1
                    }
                  }
                  if (size < 0) decodeError(s"Expected positive map part size, got $size")
                  constructor.resultObject[Key, Value](builder)
                }

                def encode(value: Map[Key, Value], encoder: BinaryEncoder): Unit = {
                  val size = deconstructor.size(value)
                  if (size > 0) {
                    encoder.writeInt(size)
                    val it = deconstructor.deconstruct(value)
                    while (it.hasNext) {
                      val kv = it.next()
                      keyCodec.encode(deconstructor.getKey(kv), encoder)
                      valueCodec.encode(deconstructor.getValue(kv), encoder)
                    }
                  }
                  encoder.writeInt(0)
                }
              }
            } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val binding              = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields               = record.fields
              val isRecursive          = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
              val typeId               = record.typeId
              var codecsWithAvroSchema =
                if (isRecursive) recursiveRecordCache.get.get(typeId)
                else null
              var offset = 0L
              if (codecsWithAvroSchema eq null) {
                val namespace  = typeId.owner.asString
                val avroSchema = createAvroRecord(namespace, typeId.name)
                val len        = fields.length
                val codecs     = new Array[AvroBinaryCodec[?]](len)
                codecsWithAvroSchema = (codecs, avroSchema)
                if (isRecursive) recursiveRecordCache.get.put(typeId, codecsWithAvroSchema)
                val avroSchemaFields = new java.util.ArrayList[AvroSchema.Field](len)
                var idx              = 0
                while (idx < len) {
                  val field = fields(idx)
                  val codec = deriveCodec(field.value)
                  codecs(idx) = codec
                  avroSchemaFields.add(new AvroSchema.Field(field.name, codec.avroSchema))
                  offset = RegisterOffset.add(codec.valueOffset, offset)
                  idx += 1
                }
                avroSchema.setFields(avroSchemaFields)
              }
              new AvroBinaryCodec[A]() {
                private[this] val deconstructor = binding.deconstructor
                private[this] val constructor   = binding.constructor
                private[this] val usedRegisters = offset
                private[this] val fieldCodecs   = codecsWithAvroSchema._1

                val avroSchema: AvroSchema = codecsWithAvroSchema._2

                def decodeUnsafe(decoder: BinaryDecoder): A = {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  val len    = fieldCodecs.length
                  var idx    = 0
                  try {
                    while (idx < len) {
                      val codec = fieldCodecs(idx)
                      codec.valueType match {
                        case AvroBinaryCodec.objectType =>
                          regs.setObject(offset, codec.asInstanceOf[AvroBinaryCodec[AnyRef]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.intType =>
                          regs.setInt(offset, codec.asInstanceOf[AvroBinaryCodec[Int]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.longType =>
                          regs.setLong(offset, codec.asInstanceOf[AvroBinaryCodec[Long]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.floatType =>
                          regs.setFloat(offset, codec.asInstanceOf[AvroBinaryCodec[Float]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.doubleType =>
                          regs.setDouble(offset, codec.asInstanceOf[AvroBinaryCodec[Double]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.booleanType =>
                          regs.setBoolean(offset, codec.asInstanceOf[AvroBinaryCodec[Boolean]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.byteType =>
                          regs.setByte(offset, codec.asInstanceOf[AvroBinaryCodec[Byte]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.charType =>
                          regs.setChar(offset, codec.asInstanceOf[AvroBinaryCodec[Char]].decodeUnsafe(decoder))
                        case AvroBinaryCodec.shortType =>
                          regs.setShort(offset, codec.asInstanceOf[AvroBinaryCodec[Short]].decodeUnsafe(decoder))
                        case _ => codec.asInstanceOf[AvroBinaryCodec[Unit]].decodeUnsafe(decoder)
                      }
                      offset += codec.valueOffset
                      idx += 1
                    }
                    constructor.construct(regs, 0)
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Field(fields(idx).name), error)
                  }
                }

                def encode(value: A, encoder: BinaryEncoder): Unit = {
                  val regs   = Registers(usedRegisters)
                  var offset = 0L
                  deconstructor.deconstruct(regs, offset, value)
                  val len = fieldCodecs.length
                  var idx = 0
                  while (idx < len) {
                    val codec = fieldCodecs(idx)
                    codec.valueType match {
                      case AvroBinaryCodec.objectType =>
                        codec.asInstanceOf[AvroBinaryCodec[AnyRef]].encode(regs.getObject(offset), encoder)
                      case AvroBinaryCodec.intType =>
                        codec.asInstanceOf[AvroBinaryCodec[Int]].encode(regs.getInt(offset), encoder)
                      case AvroBinaryCodec.longType =>
                        codec.asInstanceOf[AvroBinaryCodec[Long]].encode(regs.getLong(offset), encoder)
                      case AvroBinaryCodec.floatType =>
                        codec.asInstanceOf[AvroBinaryCodec[Float]].encode(regs.getFloat(offset), encoder)
                      case AvroBinaryCodec.doubleType =>
                        codec.asInstanceOf[AvroBinaryCodec[Double]].encode(regs.getDouble(offset), encoder)
                      case AvroBinaryCodec.booleanType =>
                        codec.asInstanceOf[AvroBinaryCodec[Boolean]].encode(regs.getBoolean(offset), encoder)
                      case AvroBinaryCodec.byteType =>
                        codec.asInstanceOf[AvroBinaryCodec[Byte]].encode(regs.getByte(offset), encoder)
                      case AvroBinaryCodec.charType =>
                        codec.asInstanceOf[AvroBinaryCodec[Char]].encode(regs.getChar(offset), encoder)
                      case AvroBinaryCodec.shortType =>
                        codec.asInstanceOf[AvroBinaryCodec[Short]].encode(regs.getShort(offset), encoder)
                      case _ => codec.asInstanceOf[AvroBinaryCodec[Unit]].encode((), encoder)
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
              val codec   = deriveCodec(wrapper.wrapped).asInstanceOf[AvroBinaryCodec[Wrapped]]
              new AvroBinaryCodec[A](wrapper.underlyingPrimitiveType.fold(AvroBinaryCodec.objectType) {
                case _: PrimitiveType.Boolean   => AvroBinaryCodec.booleanType
                case _: PrimitiveType.Byte      => AvroBinaryCodec.byteType
                case _: PrimitiveType.Char      => AvroBinaryCodec.charType
                case _: PrimitiveType.Short     => AvroBinaryCodec.shortType
                case _: PrimitiveType.Float     => AvroBinaryCodec.floatType
                case _: PrimitiveType.Int       => AvroBinaryCodec.intType
                case _: PrimitiveType.Double    => AvroBinaryCodec.doubleType
                case _: PrimitiveType.Long      => AvroBinaryCodec.longType
                case _: PrimitiveType.Unit.type => AvroBinaryCodec.unitType
                case _                          => AvroBinaryCodec.objectType
              }) {
                private[this] val wrap         = binding.wrap
                private[this] val unwrap       = binding.unwrap
                private[this] val wrappedCodec = codec

                val avroSchema: AvroSchema = wrappedCodec.avroSchema

                def decodeUnsafe(decoder: BinaryDecoder): A =
                  try wrap(wrappedCodec.decodeUnsafe(decoder))
                  catch {
                    case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                  }

                def encode(value: A, encoder: BinaryEncoder): Unit =
                  wrappedCodec.encode(unwrap(value), encoder)
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
            else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          }
        }.asInstanceOf[AvroBinaryCodec[A]]

        private[this] val dynamicValueCodec = new AvroBinaryCodec[DynamicValue]() {
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

          val avroSchema: AvroSchema = {
            val dynamicValue       = createAvroRecord("zio.blocks.schema", "DynamicValue")
            val dynamicValueFields = new java.util.ArrayList[AvroSchema.Field](1)
            dynamicValueFields.add(
              new AvroSchema.Field(
                "value",
                AvroSchema.createUnion(
                  {
                    val primitiveFields = new java.util.ArrayList[AvroSchema.Field](1)
                    primitiveFields.add(
                      new AvroSchema.Field(
                        "value",
                        AvroSchema.createUnion(
                          createAvroRecord(
                            "zio.blocks.schema.PrimitiveValue",
                            "Unit",
                            new java.util.ArrayList[AvroSchema.Field](0)
                          ),
                          createPrimitiveValueAvroRecord("Boolean", AvroBinaryCodec.booleanCodec),
                          createPrimitiveValueAvroRecord("Byte", AvroBinaryCodec.byteCodec),
                          createPrimitiveValueAvroRecord("Short", AvroBinaryCodec.shortCodec),
                          createPrimitiveValueAvroRecord("Int", AvroBinaryCodec.intCodec),
                          createPrimitiveValueAvroRecord("Long", AvroBinaryCodec.longCodec),
                          createPrimitiveValueAvroRecord("Float", AvroBinaryCodec.floatCodec),
                          createPrimitiveValueAvroRecord("Double", AvroBinaryCodec.doubleCodec),
                          createPrimitiveValueAvroRecord("Char", AvroBinaryCodec.charCodec),
                          createPrimitiveValueAvroRecord("String", AvroBinaryCodec.stringCodec),
                          createPrimitiveValueAvroRecord("BigInt", AvroBinaryCodec.bigIntCodec),
                          createPrimitiveValueAvroRecord("BigDecimal", AvroBinaryCodec.bigDecimalCodec),
                          createPrimitiveValueAvroRecord("DayOfWeek", AvroBinaryCodec.dayOfWeekCodec),
                          createPrimitiveValueAvroRecord("Duration", AvroBinaryCodec.durationCodec),
                          createPrimitiveValueAvroRecord("Instant", AvroBinaryCodec.instantCodec),
                          createPrimitiveValueAvroRecord("LocalDate", AvroBinaryCodec.localDateCodec),
                          createPrimitiveValueAvroRecord("LocalDateTime", AvroBinaryCodec.localDateTimeCodec),
                          createPrimitiveValueAvroRecord("LocalTime", AvroBinaryCodec.localTimeCodec),
                          createPrimitiveValueAvroRecord("Month", AvroBinaryCodec.monthCodec),
                          createPrimitiveValueAvroRecord("MonthDay", AvroBinaryCodec.monthDayCodec),
                          createPrimitiveValueAvroRecord("OffsetDateTime", AvroBinaryCodec.offsetDateTimeCodec),
                          createPrimitiveValueAvroRecord("OffsetTime", AvroBinaryCodec.offsetTimeCodec),
                          createPrimitiveValueAvroRecord("Period", AvroBinaryCodec.periodCodec),
                          createPrimitiveValueAvroRecord("Year", AvroBinaryCodec.yearCodec),
                          createPrimitiveValueAvroRecord("YearMonth", AvroBinaryCodec.yearMonthCodec),
                          createPrimitiveValueAvroRecord("ZoneId", AvroBinaryCodec.zoneIdCodec),
                          createPrimitiveValueAvroRecord("ZoneOffset", AvroBinaryCodec.zoneOffsetCodec),
                          createPrimitiveValueAvroRecord("ZonedDateTime", AvroBinaryCodec.zonedDateTimeCodec),
                          createPrimitiveValueAvroRecord("Currency", AvroBinaryCodec.currencyCodec),
                          createPrimitiveValueAvroRecord("UUID", AvroBinaryCodec.uuidCodec)
                        )
                      )
                    )
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Primitive", primitiveFields)
                  }, {
                    val recordFields = new java.util.ArrayList[AvroSchema.Field](1)
                    recordFields.add(
                      new AvroSchema.Field(
                        "fields",
                        AvroSchema.createArray {
                          val fieldFields = new java.util.ArrayList[AvroSchema.Field](2)
                          fieldFields.add(new AvroSchema.Field("name", AvroBinaryCodec.stringCodec.avroSchema))
                          fieldFields.add(new AvroSchema.Field("value", dynamicValue))
                          createAvroRecord("zio.blocks.schema.internal", "Field", fieldFields)
                        }
                      )
                    )
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Record", recordFields)
                  }, {
                    val variantFields = new java.util.ArrayList[AvroSchema.Field](2)
                    variantFields.add(new AvroSchema.Field("caseName", AvroBinaryCodec.stringCodec.avroSchema))
                    variantFields.add(new AvroSchema.Field("value", dynamicValue))
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Variant", variantFields)
                  }, {
                    val sequenceFields = new java.util.ArrayList[AvroSchema.Field](1)
                    sequenceFields.add(new AvroSchema.Field("elements", AvroSchema.createArray(dynamicValue)))
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Sequence", sequenceFields)
                  }, {
                    val mapFields = new java.util.ArrayList[AvroSchema.Field](1)
                    mapFields.add(
                      new AvroSchema.Field(
                        "entries",
                        AvroSchema.createArray {
                          val entryFields = new java.util.ArrayList[AvroSchema.Field](2)
                          entryFields.add(new AvroSchema.Field("key", dynamicValue))
                          entryFields.add(new AvroSchema.Field("value", dynamicValue))
                          createAvroRecord("zio.blocks.schema.internal", "Entry", entryFields)
                        }
                      )
                    )
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Map", mapFields)
                  },
                  createAvroRecord(
                    "zio.blocks.schema.DynamicValue",
                    "Null",
                    new java.util.ArrayList[AvroSchema.Field](0)
                  )
                )
              )
            )
            dynamicValue.setFields(dynamicValueFields)
            dynamicValue
          }

          def decodeUnsafe(decoder: BinaryDecoder): DynamicValue = decoder.readInt() match {
            case 0 =>
              try {
                val idx = decoder.readInt()
                if (idx < 0 || idx > 29) decodeError(s"Expected enum index from 0 to 29, got $idx")
                try {
                  new DynamicValue.Primitive((idx: @scala.annotation.switch) match {
                    case 0  => PrimitiveValue.Unit
                    case 1  => new PrimitiveValue.Boolean(AvroBinaryCodec.booleanCodec.decodeUnsafe(decoder))
                    case 2  => new PrimitiveValue.Byte(AvroBinaryCodec.byteCodec.decodeUnsafe(decoder))
                    case 3  => new PrimitiveValue.Short(AvroBinaryCodec.shortCodec.decodeUnsafe(decoder))
                    case 4  => new PrimitiveValue.Int(AvroBinaryCodec.intCodec.decodeUnsafe(decoder))
                    case 5  => new PrimitiveValue.Long(AvroBinaryCodec.longCodec.decodeUnsafe(decoder))
                    case 6  => new PrimitiveValue.Float(AvroBinaryCodec.floatCodec.decodeUnsafe(decoder))
                    case 7  => new PrimitiveValue.Double(AvroBinaryCodec.doubleCodec.decodeUnsafe(decoder))
                    case 8  => new PrimitiveValue.Char(AvroBinaryCodec.charCodec.decodeUnsafe(decoder))
                    case 9  => new PrimitiveValue.String(AvroBinaryCodec.stringCodec.decodeUnsafe(decoder))
                    case 10 => new PrimitiveValue.BigInt(AvroBinaryCodec.bigIntCodec.decodeUnsafe(decoder))
                    case 11 => new PrimitiveValue.BigDecimal(AvroBinaryCodec.bigDecimalCodec.decodeUnsafe(decoder))
                    case 12 => new PrimitiveValue.DayOfWeek(AvroBinaryCodec.dayOfWeekCodec.decodeUnsafe(decoder))
                    case 13 => new PrimitiveValue.Duration(AvroBinaryCodec.durationCodec.decodeUnsafe(decoder))
                    case 14 => new PrimitiveValue.Instant(AvroBinaryCodec.instantCodec.decodeUnsafe(decoder))
                    case 15 => new PrimitiveValue.LocalDate(AvroBinaryCodec.localDateCodec.decodeUnsafe(decoder))
                    case 16 =>
                      new PrimitiveValue.LocalDateTime(AvroBinaryCodec.localDateTimeCodec.decodeUnsafe(decoder))
                    case 17 => new PrimitiveValue.LocalTime(AvroBinaryCodec.localTimeCodec.decodeUnsafe(decoder))
                    case 18 => new PrimitiveValue.Month(AvroBinaryCodec.monthCodec.decodeUnsafe(decoder))
                    case 19 => new PrimitiveValue.MonthDay(AvroBinaryCodec.monthDayCodec.decodeUnsafe(decoder))
                    case 20 =>
                      new PrimitiveValue.OffsetDateTime(AvroBinaryCodec.offsetDateTimeCodec.decodeUnsafe(decoder))
                    case 21 => new PrimitiveValue.OffsetTime(AvroBinaryCodec.offsetTimeCodec.decodeUnsafe(decoder))
                    case 22 => new PrimitiveValue.Period(AvroBinaryCodec.periodCodec.decodeUnsafe(decoder))
                    case 23 => new PrimitiveValue.Year(AvroBinaryCodec.yearCodec.decodeUnsafe(decoder))
                    case 24 => new PrimitiveValue.YearMonth(AvroBinaryCodec.yearMonthCodec.decodeUnsafe(decoder))
                    case 25 => new PrimitiveValue.ZoneId(AvroBinaryCodec.zoneIdCodec.decodeUnsafe(decoder))
                    case 26 => new PrimitiveValue.ZoneOffset(AvroBinaryCodec.zoneOffsetCodec.decodeUnsafe(decoder))
                    case 27 =>
                      new PrimitiveValue.ZonedDateTime(AvroBinaryCodec.zonedDateTimeCodec.decodeUnsafe(decoder))
                    case 28 => new PrimitiveValue.Currency(AvroBinaryCodec.currencyCodec.decodeUnsafe(decoder))
                    case _  => new PrimitiveValue.UUID(AvroBinaryCodec.uuidCodec.decodeUnsafe(decoder))
                  })
                } catch {
                  case error if NonFatal(error) => decodeError(spanValue, error)
                }
              } catch {
                case error if NonFatal(error) => decodeError(spanPrimitive, error)
              }
            case 1 =>
              try {
                val builder = ChunkBuilder.make[(String, DynamicValue)]()
                var count   = 0L
                var size    = 0
                while ({
                  size = decoder.readInt()
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    val k =
                      try decoder.readString()
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count.toInt), span_1, error)
                      }
                    val v =
                      try decodeUnsafe(decoder)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count.toInt), span_2, error)
                      }
                    builder.addOne((k, v))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                new DynamicValue.Record(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(spanRecord, spanFields, error)
              }
            case 2 =>
              val caseName =
                try decoder.readString()
                catch {
                  case error if NonFatal(error) => decodeError(spanVariant, spanCaseName, error)
                }
              val value =
                try decodeUnsafe(decoder)
                catch {
                  case error if NonFatal(error) => decodeError(spanVariant, spanValue, error)
                }
              new DynamicValue.Variant(caseName, value)
            case 3 =>
              try {
                val builder = ChunkBuilder.make[DynamicValue]()
                var count   = 0L
                var size    = 0
                while ({
                  size = decoder.readInt()
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  try {
                    while (size > 0) {
                      builder.addOne(decodeUnsafe(decoder))
                      count += 1
                      size -= 1
                    }
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(count.toInt), error)
                  }
                }
                if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                new DynamicValue.Sequence(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(spanSequence, spanElements, error)
              }
            case 4 =>
              try {
                val builder = ChunkBuilder.make[(DynamicValue, DynamicValue)]()
                var count   = 0L
                var size    = 0
                while ({
                  size = decoder.readInt()
                  size > 0
                }) {
                  if (count + size > AvroBinaryCodec.maxCollectionSize) {
                    decodeError(
                      s"Expected collection size not greater than ${AvroBinaryCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    val k =
                      try decodeUnsafe(decoder)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count.toInt), span_1, error)
                      }
                    val v =
                      try decodeUnsafe(decoder)
                      catch {
                        case error if NonFatal(error) =>
                          decodeError(new DynamicOptic.Node.AtIndex(count.toInt), span_2, error)
                      }
                    builder.addOne((k, v))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) decodeError(s"Expected positive collection part size, got $size")
                new DynamicValue.Map(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(spanMap, spanEntries, error)
              }
            case 5 =>
              DynamicValue.Null
            case idx => decodeError(s"Expected enum index from 0 to 5, got $idx")
          }

          def encode(value: DynamicValue, encoder: BinaryEncoder): Unit = value match {
            case primitive: DynamicValue.Primitive =>
              encoder.writeInt(0)
              primitive.value match {
                case _: PrimitiveValue.Unit.type =>
                  encoder.writeInt(0)
                case v: PrimitiveValue.Boolean =>
                  encoder.writeInt(1)
                  AvroBinaryCodec.booleanCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Byte =>
                  encoder.writeInt(2)
                  AvroBinaryCodec.byteCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Short =>
                  encoder.writeInt(3)
                  AvroBinaryCodec.shortCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Int =>
                  encoder.writeInt(4)
                  AvroBinaryCodec.intCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Long =>
                  encoder.writeInt(5)
                  AvroBinaryCodec.longCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Float =>
                  encoder.writeInt(6)
                  AvroBinaryCodec.floatCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Double =>
                  encoder.writeInt(7)
                  AvroBinaryCodec.doubleCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Char =>
                  encoder.writeInt(8)
                  AvroBinaryCodec.charCodec.encode(v.value, encoder)
                case v: PrimitiveValue.String =>
                  encoder.writeInt(9)
                  AvroBinaryCodec.stringCodec.encode(v.value, encoder)
                case v: PrimitiveValue.BigInt =>
                  encoder.writeInt(10)
                  AvroBinaryCodec.bigIntCodec.encode(v.value, encoder)
                case v: PrimitiveValue.BigDecimal =>
                  encoder.writeInt(11)
                  AvroBinaryCodec.bigDecimalCodec.encode(v.value, encoder)
                case v: PrimitiveValue.DayOfWeek =>
                  encoder.writeInt(12)
                  AvroBinaryCodec.dayOfWeekCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Duration =>
                  encoder.writeInt(13)
                  AvroBinaryCodec.durationCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Instant =>
                  encoder.writeInt(14)
                  AvroBinaryCodec.instantCodec.encode(v.value, encoder)
                case v: PrimitiveValue.LocalDate =>
                  encoder.writeInt(15)
                  AvroBinaryCodec.localDateCodec.encode(v.value, encoder)
                case v: PrimitiveValue.LocalDateTime =>
                  encoder.writeInt(16)
                  AvroBinaryCodec.localDateTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.LocalTime =>
                  encoder.writeInt(17)
                  AvroBinaryCodec.localTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Month =>
                  encoder.writeInt(18)
                  AvroBinaryCodec.monthCodec.encode(v.value, encoder)
                case v: PrimitiveValue.MonthDay =>
                  encoder.writeInt(19)
                  AvroBinaryCodec.monthDayCodec.encode(v.value, encoder)
                case v: PrimitiveValue.OffsetDateTime =>
                  encoder.writeInt(20)
                  AvroBinaryCodec.offsetDateTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.OffsetTime =>
                  encoder.writeInt(21)
                  AvroBinaryCodec.offsetTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Period =>
                  encoder.writeInt(22)
                  AvroBinaryCodec.periodCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Year =>
                  encoder.writeInt(23)
                  AvroBinaryCodec.yearCodec.encode(v.value, encoder)
                case v: PrimitiveValue.YearMonth =>
                  encoder.writeInt(24)
                  AvroBinaryCodec.yearMonthCodec.encode(v.value, encoder)
                case v: PrimitiveValue.ZoneId =>
                  encoder.writeInt(25)
                  AvroBinaryCodec.zoneIdCodec.encode(v.value, encoder)
                case v: PrimitiveValue.ZoneOffset =>
                  encoder.writeInt(26)
                  AvroBinaryCodec.zoneOffsetCodec.encode(v.value, encoder)
                case v: PrimitiveValue.ZonedDateTime =>
                  encoder.writeInt(27)
                  AvroBinaryCodec.zonedDateTimeCodec.encode(v.value, encoder)
                case v: PrimitiveValue.Currency =>
                  encoder.writeInt(28)
                  AvroBinaryCodec.currencyCodec.encode(v.value, encoder)
                case v: PrimitiveValue.UUID =>
                  encoder.writeInt(29)
                  AvroBinaryCodec.uuidCodec.encode(v.value, encoder)
              }
            case record: DynamicValue.Record =>
              encoder.writeInt(1)
              val fields = record.fields
              val size   = fields.length
              if (size > 0) {
                encoder.writeInt(size)
                val it = fields.iterator
                while (it.hasNext) {
                  val kv = it.next()
                  encoder.writeString(kv._1)
                  encode(kv._2, encoder)
                }
              }
              encoder.writeInt(0)
            case variant: DynamicValue.Variant =>
              encoder.writeInt(2)
              encoder.writeString(variant.caseNameValue)
              encode(variant.value, encoder)
            case sequence: DynamicValue.Sequence =>
              encoder.writeInt(3)
              val elements = sequence.elements
              val size     = elements.length
              if (size > 0) {
                encoder.writeInt(size)
                val it = elements.iterator
                while (it.hasNext) {
                  encode(it.next(), encoder)
                }
              }
              encoder.writeInt(0)
            case map: DynamicValue.Map =>
              encoder.writeInt(4)
              val entries = map.entries
              val size    = entries.length
              if (size > 0) {
                encoder.writeInt(size)
                val it = entries.iterator
                while (it.hasNext) {
                  val kv = it.next()
                  encode(kv._1, encoder)
                  encode(kv._2, encoder)
                }
              }
              encoder.writeInt(0)
            case DynamicValue.Null =>
              encoder.writeInt(5)
          }

          private[this] def createPrimitiveValueAvroRecord(name: String, codec: AvroBinaryCodec[?]): AvroSchema = {
            val avroSchema = codec.avroSchema
            val fields     = new java.util.ArrayList[AvroSchema.Field](1)
            fields.add(new AvroSchema.Field("value", avroSchema))
            createAvroRecord("zio.blocks.schema.PrimitiveValue", name, fields)
          }
        }

        private[this] def createAvroRecord(
          namespace: String,
          name: String,
          fields: java.util.ArrayList[AvroSchema.Field] = null
        ): AvroSchema = {
          val number     = recordCounters.get().compute((namespace, name), (_: (String, String), n: Int) => n + 1) - 1
          val recordName =
            if (number > 0) (new java.lang.StringBuilder).append(name).append('_').append(number).toString
            else name
          if (fields eq null) AvroSchema.createRecord(recordName, null, namespace, false)
          else AvroSchema.createRecord(recordName, null, namespace, false, fields)
        }
      }
    )
