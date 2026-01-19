package zio.blocks.schema.messagepack

import org.msgpack.core.{MessageBufferPacker, MessageUnpacker}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema.binding.SeqDeconstructor._
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.math.BigInteger
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

        private[this] val recursiveRecordCache =
          new ThreadLocal[java.util.HashMap[TypeName[?], (Array[MessagePackBinaryCodec[?]], MessagePackBinaryCodec[?])]] {
            override def initialValue: java.util.HashMap[TypeName[?], (Array[MessagePackBinaryCodec[?]], MessagePackBinaryCodec[?])] =
              new java.util.HashMap
          }

        private[this] val unitCodec = new MessagePackBinaryCodec[Unit](MessagePackBinaryCodec.unitType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Unit = unpacker.unpackNil()
          def encode(value: Unit, packer: MessageBufferPacker): Unit = packer.packNil()
        }
        
        private[this] val booleanCodec = new MessagePackBinaryCodec[Boolean](MessagePackBinaryCodec.booleanType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Boolean = unpacker.unpackBoolean()
          def encode(value: Boolean, packer: MessageBufferPacker): Unit = packer.packBoolean(value)
        }
        
        private[this] val byteCodec = new MessagePackBinaryCodec[Byte](MessagePackBinaryCodec.byteType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Byte = unpacker.unpackByte()
          def encode(value: Byte, packer: MessageBufferPacker): Unit = packer.packByte(value)
        }

        private[this] val shortCodec = new MessagePackBinaryCodec[Short](MessagePackBinaryCodec.shortType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Short = unpacker.unpackShort()
          def encode(value: Short, packer: MessageBufferPacker): Unit = packer.packShort(value)
        }

        private[this] val intCodec = new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Int = unpacker.unpackInt()
          def encode(value: Int, packer: MessageBufferPacker): Unit = packer.packInt(value)
        }

        private[this] val longCodec = new MessagePackBinaryCodec[Long](MessagePackBinaryCodec.longType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Long = unpacker.unpackLong()
          def encode(value: Long, packer: MessageBufferPacker): Unit = packer.packLong(value)
        }

        private[this] val floatCodec = new MessagePackBinaryCodec[Float](MessagePackBinaryCodec.floatType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Float = unpacker.unpackFloat()
          def encode(value: Float, packer: MessageBufferPacker): Unit = packer.packFloat(value)
        }

        private[this] val doubleCodec = new MessagePackBinaryCodec[Double](MessagePackBinaryCodec.doubleType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Double = unpacker.unpackDouble()
          def encode(value: Double, packer: MessageBufferPacker): Unit = packer.packDouble(value)
        }
        
        private[this] val charCodec = new MessagePackBinaryCodec[Char](MessagePackBinaryCodec.charType) {
             def decodeUnsafe(unpacker: MessageUnpacker): Char = {
                 val i = unpacker.unpackInt()
                 i.toChar
             }
             def encode(value: Char, packer: MessageBufferPacker): Unit = packer.packInt(value.toInt)
        }

        private[this] val stringCodec = new MessagePackBinaryCodec[String]() {
          def decodeUnsafe(unpacker: MessageUnpacker): String = unpacker.unpackString()
          def encode(value: String, packer: MessageBufferPacker): Unit = packer.packString(value)
        }

        private[this] val bigIntCodec = new MessagePackBinaryCodec[BigInt]() {
            def decodeUnsafe(unpacker: MessageUnpacker): BigInt = {
                val len = unpacker.unpackBinaryHeader()
                val bytes = new Array[Byte](len)
                unpacker.readPayload(bytes)
                BigInt(bytes)
            }
            def encode(value: BigInt, packer: MessageBufferPacker): Unit = {
                val bytes = value.toByteArray
                packer.packBinaryHeader(bytes.length)
                packer.writePayload(bytes)
            }
        }
        
        private[this] val bigDecimalCodec = new MessagePackBinaryCodec[BigDecimal]() {
           def decodeUnsafe(unpacker: MessageUnpacker): BigDecimal = {
               val size = unpacker.unpackArrayHeader()
               if(size != 4) decodeError(s"Expected array of size 4 for BigDecimal, got $size")
               val mantissaLen = unpacker.unpackBinaryHeader()
               val mantissaBytes = new Array[Byte](mantissaLen)
               unpacker.readPayload(mantissaBytes)
               val scale = unpacker.unpackInt()
               val precision = unpacker.unpackInt()
               val roundingModeIdx = unpacker.unpackInt()
               
               val mc = new java.math.MathContext(precision, java.math.RoundingMode.valueOf(roundingModeIdx))
               new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissaBytes), scale), mc)
           }
           
           def encode(value: BigDecimal, packer: MessageBufferPacker): Unit = {
                packer.packArrayHeader(4)
                val bd = value.underlying
                val bytes = bd.unscaledValue.toByteArray
                packer.packBinaryHeader(bytes.length)
                packer.writePayload(bytes)
                packer.packInt(bd.scale)
                packer.packInt(value.mc.getPrecision)
                packer.packInt(value.mc.getRoundingMode.ordinal)
           }
        }
        
        // Helper to codec java.time types using components array
        private def timeCodec[A](read: MessageUnpacker => A, write: (A, MessageBufferPacker) => Unit): MessagePackBinaryCodec[A] = new MessagePackBinaryCodec[A](){
             def decodeUnsafe(unpacker: MessageUnpacker): A = read(unpacker)
             def encode(value: A, packer: MessageBufferPacker): Unit = write(value, packer)
        }

        private[this] val dayOfWeekCodec = timeCodec[java.time.DayOfWeek](
            u => java.time.DayOfWeek.of(u.unpackInt()),
            (v, p) => p.packInt(v.getValue)
        )
        
        private[this] val durationCodec = timeCodec[java.time.Duration](
            u => {
                u.unpackArrayHeader() // consume array header
                val s = u.unpackLong()
                val n = u.unpackInt()
                java.time.Duration.ofSeconds(s, n.toLong)
            },
            (v, p) => {
                p.packArrayHeader(2)
                p.packLong(v.getSeconds)
                p.packInt(v.getNano)
            }
        )

        private[this] val instantCodec = timeCodec[java.time.Instant](
             u => {
                u.unpackArrayHeader()
                val s = u.unpackLong()
                val n = u.unpackInt()
                java.time.Instant.ofEpochSecond(s, n.toLong)
             },
             (v, p) => {
                 p.packArrayHeader(2)
                 p.packLong(v.getEpochSecond)
                 p.packInt(v.getNano)
             }
        )
        
        private[this] val localDateCodec = timeCodec[java.time.LocalDate](
             u => {
                 u.unpackArrayHeader()
                 val y = u.unpackInt()
                 val m = u.unpackInt()
                 val d = u.unpackInt()
                 java.time.LocalDate.of(y, m, d)
             },
             (v, p) => {
                 p.packArrayHeader(3)
                 p.packInt(v.getYear)
                 p.packInt(v.getMonthValue)
                 p.packInt(v.getDayOfMonth)
             }
        )
        
        private[this] val localDateTimeCodec = timeCodec[java.time.LocalDateTime](
            u => {
                u.unpackArrayHeader()
                java.time.LocalDateTime.of(
                    u.unpackInt(), u.unpackInt(), u.unpackInt(),
                    u.unpackInt(), u.unpackInt(), u.unpackInt(), u.unpackInt()
                )
            },
            (v, p) => {
                p.packArrayHeader(7)
                p.packInt(v.getYear).packInt(v.getMonthValue).packInt(v.getDayOfMonth)
                p.packInt(v.getHour).packInt(v.getMinute).packInt(v.getSecond).packInt(v.getNano)
            }
        )
        
        private[this] val localTimeCodec = timeCodec[java.time.LocalTime](
            u => {
                u.unpackArrayHeader()
                java.time.LocalTime.of(u.unpackInt(), u.unpackInt(), u.unpackInt(), u.unpackInt())
            },
            (v, p) => {
                p.packArrayHeader(4)
                p.packInt(v.getHour).packInt(v.getMinute).packInt(v.getSecond).packInt(v.getNano)
            }
        )

        private[this] val monthCodec = timeCodec[java.time.Month](
             u => java.time.Month.of(u.unpackInt()),
             (v, p) => p.packInt(v.getValue)
        )

        private[this] val monthDayCodec = timeCodec[java.time.MonthDay](
             u => {
                 u.unpackArrayHeader()
                 java.time.MonthDay.of(u.unpackInt(), u.unpackInt())
             },
             (v, p) => {
                 p.packArrayHeader(2)
                 p.packInt(v.getMonthValue).packInt(v.getDayOfMonth)
             }
        )

         private[this] val offsetDateTimeCodec = timeCodec[java.time.OffsetDateTime](
            u => {
                 u.unpackArrayHeader()
                 val y = u.unpackInt(); val mo = u.unpackInt(); val d = u.unpackInt()
                 val h = u.unpackInt(); val mi = u.unpackInt(); val s = u.unpackInt(); val n = u.unpackInt()
                 val off = u.unpackInt()
                 java.time.OffsetDateTime.of(y, mo, d, h, mi, s, n, java.time.ZoneOffset.ofTotalSeconds(off))
            },
            (v, p) => {
                p.packArrayHeader(8)
                p.packInt(v.getYear).packInt(v.getMonthValue).packInt(v.getDayOfMonth)
                p.packInt(v.getHour).packInt(v.getMinute).packInt(v.getSecond).packInt(v.getNano)
                p.packInt(v.getOffset.getTotalSeconds)
            }
         )
         
         private[this] val offsetTimeCodec = timeCodec[java.time.OffsetTime](
             u => {
                 u.unpackArrayHeader()
                 val h = u.unpackInt(); val m = u.unpackInt(); val s = u.unpackInt(); val n = u.unpackInt()
                 val off = u.unpackInt()
                 java.time.OffsetTime.of(h, m, s, n, java.time.ZoneOffset.ofTotalSeconds(off))
             },
             (v, p) => {
                 p.packArrayHeader(5)
                  p.packInt(v.getHour).packInt(v.getMinute).packInt(v.getSecond).packInt(v.getNano)
                  p.packInt(v.getOffset.getTotalSeconds)
             }
         )
         
         private[this] val periodCodec = timeCodec[java.time.Period](
              u => {
                  u.unpackArrayHeader()
                  java.time.Period.of(u.unpackInt(), u.unpackInt(), u.unpackInt())
              },
              (v, p) => {
                  p.packArrayHeader(3)
                  p.packInt(v.getYears).packInt(v.getMonths).packInt(v.getDays)
              }
         )
         
         private[this] val yearCodec = timeCodec[java.time.Year](
              u => java.time.Year.of(u.unpackInt()),
              (v, p) => p.packInt(v.getValue)
         )
         
         private[this] val yearMonthCodec = timeCodec[java.time.YearMonth](
              u => {
                  u.unpackArrayHeader()
                  java.time.YearMonth.of(u.unpackInt(), u.unpackInt())
              },
              (v, p) => {
                  p.packArrayHeader(2)
                  p.packInt(v.getYear).packInt(v.getMonthValue)
              }
         )
         
         private[this] val zoneIdCodec = timeCodec[java.time.ZoneId](
             u => java.time.ZoneId.of(u.unpackString()),
             (v, p) => p.packString(v.toString)
         )

         private[this] val zoneOffsetCodec = timeCodec[java.time.ZoneOffset](
              u => java.time.ZoneOffset.ofTotalSeconds(u.unpackInt()),
              (v, p) => p.packInt(v.getTotalSeconds)
         )
         
         private[this] val zonedDateTimeCodec = timeCodec[java.time.ZonedDateTime](
             u => {
                 u.unpackArrayHeader()
                 val y = u.unpackInt(); val mo = u.unpackInt(); val d = u.unpackInt()
                 val h = u.unpackInt(); val mi = u.unpackInt(); val s = u.unpackInt(); val n = u.unpackInt()
                 val off = u.unpackInt()
                 val zoneId = u.unpackString()
                 java.time.ZonedDateTime.ofInstant(
                     java.time.LocalDateTime.of(y, mo, d, h, mi, s, n),
                     java.time.ZoneOffset.ofTotalSeconds(off),
                     java.time.ZoneId.of(zoneId)
                 )
             },
             (v, p) => {
                 p.packArrayHeader(9)
                 p.packInt(v.getYear).packInt(v.getMonthValue).packInt(v.getDayOfMonth)
                 p.packInt(v.getHour).packInt(v.getMinute).packInt(v.getSecond).packInt(v.getNano)
                 p.packInt(v.getOffset.getTotalSeconds)
                 p.packString(v.getZone.toString)
             }
         )
         
         private[this] val currencyCodec = timeCodec[java.util.Currency](
              u => java.util.Currency.getInstance(u.unpackString()),
              (v, p) => p.packString(v.toString)
         )
         
         private[this] val uuidCodec = timeCodec[java.util.UUID](
              u => java.util.UUID.fromString(u.unpackString()),
              (v, p) => p.packString(v.toString)
         )
    
        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): MessagePackBinaryCodec[A] = {
           type TC[A] = MessagePackBinaryCodec[A]
           if(reflect.isPrimitive) {
               val primitive = reflect.asPrimitive.get
               if(primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
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
           } else if(reflect.isVariant) {
               val variant = reflect.asVariant.get
               if(variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
                   val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
                   val cases = variant.cases
                   val len = cases.length
                   val codecs = new Array[MessagePackBinaryCodec[?]](len)
                   var idx = 0
                   while(idx < len) {
                       codecs(idx) = deriveCodec(cases(idx).value)
                       idx += 1
                   }
                   new MessagePackBinaryCodec[A](){
                       def decodeUnsafe(unpacker: MessageUnpacker): A = {
                           val idx = unpacker.unpackInt()
                           if(idx >= 0 && idx < codecs.length) {
                               try codecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].decodeUnsafe(unpacker)
                               catch {
                                   case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
                               }
                           } else decodeError(s"Expected enum index from 0 to ${codecs.length - 1}, got $idx")
                       }
                       
                       def encode(value: A, packer: MessageBufferPacker): Unit = {
                           val idx = binding.discriminator.discriminate(value)
                           packer.packInt(idx)
                           codecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].encode(value, packer)
                       }
                   }
               } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
           } else if(reflect.isRecord) {
                val record = reflect.asRecord.get
                if(record.recordBinding.isInstanceOf[Binding[?, ?]]) {
                     val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]
                     val fields = record.fields
                     val typeName             = record.typeName
                     var codecsWithRecordCodec = recursiveRecordCache.get.get(typeName)
                     var offset = 0L
                     if (codecsWithRecordCodec eq null) {
                       val len = fields.length
                       val codecs = new Array[MessagePackBinaryCodec[?]](len)
                       codecsWithRecordCodec = (codecs, null) // Temporary placeholder for recursive codec
                       if (fields.exists(_.value.isInstanceOf[Reflect.Deferred[?, ?]])) {
                           recursiveRecordCache.get.put(typeName, codecsWithRecordCodec)
                       }
                       var idx = 0
                       while (idx < len) {
                         val codec = deriveCodec(fields(idx).value)
                         codecs(idx) = codec
                         offset = RegisterOffset.add(codec.valueOffset, offset)
                         idx += 1
                       }
                     } else {
                       offset = codecsWithRecordCodec._1.foldLeft(0L)((acc, c) => RegisterOffset.add(c.valueOffset, acc))
                     }
                     
                     val finalFieldCodecs = codecsWithRecordCodec._1
                     new MessagePackBinaryCodec[A](){
                        private[this] val deconstructor = binding.deconstructor
                        private[this] val constructor   = binding.constructor
                        private[this] val usedRegisters = offset
                        private[this] val fieldCodecs   = finalFieldCodecs

                        def decodeUnsafe(unpacker: MessageUnpacker): A = {
                            val size = unpacker.unpackArrayHeader()
                            if(size != fields.length) decodeError(s"Expected array size ${fields.length} for record, got $size")
                            
                            val regs = Registers(usedRegisters)
                            var offset = 0L
                            var i = 0
                            try {
                                while(i < fields.length) {
                                    val codec = fieldCodecs(i)
                                    codec.valueType match {
                                       case MessagePackBinaryCodec.objectType =>
                                            regs.setObject(offset, codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].decodeUnsafe(unpacker))
                                       case MessagePackBinaryCodec.intType =>
                                            regs.setInt(offset, codec.asInstanceOf[MessagePackBinaryCodec[Int]].decodeUnsafe(unpacker))
                                       case MessagePackBinaryCodec.longType =>
                                            regs.setLong(offset, codec.asInstanceOf[MessagePackBinaryCodec[Long]].decodeUnsafe(unpacker))
                                       case MessagePackBinaryCodec.floatType =>
                                            regs.setFloat(offset, codec.asInstanceOf[MessagePackBinaryCodec[Float]].decodeUnsafe(unpacker))
                                       case MessagePackBinaryCodec.doubleType =>
                                            regs.setDouble(offset, codec.asInstanceOf[MessagePackBinaryCodec[Double]].decodeUnsafe(unpacker))
                                       case MessagePackBinaryCodec.booleanType =>
                                            regs.setBoolean(offset, codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].decodeUnsafe(unpacker))
                                       case MessagePackBinaryCodec.byteType =>
                                            regs.setByte(offset, codec.asInstanceOf[MessagePackBinaryCodec[Byte]].decodeUnsafe(unpacker))
                                       case MessagePackBinaryCodec.charType =>
                                            regs.setChar(offset, codec.asInstanceOf[MessagePackBinaryCodec[Char]].decodeUnsafe(unpacker))
                                       case MessagePackBinaryCodec.shortType =>
                                            regs.setShort(offset, codec.asInstanceOf[MessagePackBinaryCodec[Short]].decodeUnsafe(unpacker))
                                       case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].decodeUnsafe(unpacker)
                                    }
                                    offset += codec.valueOffset
                                    i += 1 
                                }
                                constructor.construct(regs, 0)
                            } catch {
                                case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Field(fields(i).name), error)
                            }
                        }
                        
                        def encode(value: A, packer: MessageBufferPacker): Unit = {
                            packer.packArrayHeader(fields.length)
                            val regs = Registers(usedRegisters)
                            var offset = 0L
                            deconstructor.deconstruct(regs, offset, value)
                            
                            var i = 0
                            while(i < fields.length) {
                                val codec = fieldCodecs(i)
                                codec.valueType match {
                                     case MessagePackBinaryCodec.objectType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].encode(regs.getObject(offset), packer)
                                     case MessagePackBinaryCodec.intType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[Int]].encode(regs.getInt(offset), packer)
                                     case MessagePackBinaryCodec.longType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[Long]].encode(regs.getLong(offset), packer)
                                     case MessagePackBinaryCodec.floatType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[Float]].encode(regs.getFloat(offset), packer)
                                     case MessagePackBinaryCodec.doubleType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[Double]].encode(regs.getDouble(offset), packer)
                                     case MessagePackBinaryCodec.booleanType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].encode(regs.getBoolean(offset), packer)
                                     case MessagePackBinaryCodec.byteType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[Byte]].encode(regs.getByte(offset), packer)
                                     case MessagePackBinaryCodec.charType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[Char]].encode(regs.getChar(offset), packer)
                                     case MessagePackBinaryCodec.shortType =>
                                          codec.asInstanceOf[MessagePackBinaryCodec[Short]].encode(regs.getShort(offset), packer)
                                     case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].encode((), packer)
                                }
                                offset += codec.valueOffset
                                i += 1
                            }
                        }
                    }
                } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
            } else if(reflect.isSequence) {
                 val sequence = reflect.asSequenceUnknown.get.sequence
                 type Col[x]
                 type Elem
                 if(sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
                      val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
                      val codec = deriveCodec(sequence.element).asInstanceOf[MessagePackBinaryCodec[Elem]]
                      
                      codec.valueType match {
                          case MessagePackBinaryCodec.booleanType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                              new MessagePackBinaryCodec[Col[Boolean]]() {
                                  private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                                  private[this] val constructor = binding.constructor
                                  private[this] val elementCodec = codec.asInstanceOf[MessagePackBinaryCodec[Boolean]]
                                  def decodeUnsafe(unpacker: MessageUnpacker): Col[Boolean] = {
                                      val size = unpacker.unpackArrayHeader()
                                      val builder = constructor.newBooleanBuilder()
                                      var i = 0
                                      while(i < size) {
                                          constructor.addBoolean(builder, elementCodec.decodeUnsafe(unpacker))
                                          i += 1
                                      }
                                      constructor.resultBoolean(builder)
                                  }
                                  def encode(value: Col[Boolean], packer: MessageBufferPacker): Unit = {
                                      val size = deconstructor.size(value)
                                      packer.packArrayHeader(size)
                                      val it = deconstructor.deconstruct(value)
                                      while(it.hasNext) elementCodec.encode(it.next(), packer)
                                  }
                              }.asInstanceOf[MessagePackBinaryCodec[Col[Elem]]]
                          case MessagePackBinaryCodec.byteType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                              new MessagePackBinaryCodec[Col[Byte]]() {
                                  private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                                  private[this] val constructor = binding.constructor
                                  private[this] val elementCodec = codec.asInstanceOf[MessagePackBinaryCodec[Byte]]
                                  def decodeUnsafe(unpacker: MessageUnpacker): Col[Byte] = {
                                      val size = unpacker.unpackArrayHeader()
                                      val builder = constructor.newByteBuilder()
                                      var i = 0
                                      while(i < size) {
                                          constructor.addByte(builder, elementCodec.decodeUnsafe(unpacker))
                                          i += 1
                                      }
                                      constructor.resultByte(builder)
                                  }
                                  def encode(value: Col[Byte], packer: MessageBufferPacker): Unit = {
                                      val size = deconstructor.size(value)
                                      packer.packArrayHeader(size)
                                      val it = deconstructor.deconstruct(value)
                                      while(it.hasNext) elementCodec.encode(it.next(), packer)
                                  }
                              }.asInstanceOf[MessagePackBinaryCodec[Col[Elem]]]
                          case MessagePackBinaryCodec.shortType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                              new MessagePackBinaryCodec[Col[Short]]() {
                                  private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                                  private[this] val constructor = binding.constructor
                                  private[this] val elementCodec = codec.asInstanceOf[MessagePackBinaryCodec[Short]]
                                  def decodeUnsafe(unpacker: MessageUnpacker): Col[Short] = {
                                      val size = unpacker.unpackArrayHeader()
                                      val builder = constructor.newShortBuilder()
                                      var i = 0
                                      while(i < size) {
                                          constructor.addShort(builder, elementCodec.decodeUnsafe(unpacker))
                                          i += 1
                                      }
                                      constructor.resultShort(builder)
                                  }
                                  def encode(value: Col[Short], packer: MessageBufferPacker): Unit = {
                                      val size = deconstructor.size(value)
                                      packer.packArrayHeader(size)
                                      val it = deconstructor.deconstruct(value)
                                      while(it.hasNext) elementCodec.encode(it.next(), packer)
                                  }
                              }.asInstanceOf[MessagePackBinaryCodec[Col[Elem]]]
                          case MessagePackBinaryCodec.charType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                              new MessagePackBinaryCodec[Col[Char]]() {
                                  private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                                  private[this] val constructor = binding.constructor
                                  private[this] val elementCodec = codec.asInstanceOf[MessagePackBinaryCodec[Char]]
                                  def decodeUnsafe(unpacker: MessageUnpacker): Col[Char] = {
                                      val size = unpacker.unpackArrayHeader()
                                      val builder = constructor.newCharBuilder()
                                      var i = 0
                                      while(i < size) {
                                          constructor.addChar(builder, elementCodec.decodeUnsafe(unpacker))
                                          i += 1
                                      }
                                      constructor.resultChar(builder)
                                  }
                                  def encode(value: Col[Char], packer: MessageBufferPacker): Unit = {
                                      val size = deconstructor.size(value)
                                      packer.packArrayHeader(size)
                                      val it = deconstructor.deconstruct(value)
                                      while(it.hasNext) elementCodec.encode(it.next(), packer)
                                  }
                              }.asInstanceOf[MessagePackBinaryCodec[Col[Elem]]]
                          case MessagePackBinaryCodec.intType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                              new MessagePackBinaryCodec[Col[Int]]() {
                                  private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                                  private[this] val constructor = binding.constructor
                                  private[this] val elementCodec = codec.asInstanceOf[MessagePackBinaryCodec[Int]]
                                  def decodeUnsafe(unpacker: MessageUnpacker): Col[Int] = {
                                      val size = unpacker.unpackArrayHeader()
                                      val builder = constructor.newIntBuilder()
                                      var i = 0
                                      while(i < size) {
                                          constructor.addInt(builder, elementCodec.decodeUnsafe(unpacker))
                                          i += 1
                                      }
                                      constructor.resultInt(builder)
                                  }
                                  def encode(value: Col[Int], packer: MessageBufferPacker): Unit = {
                                      val size = deconstructor.size(value)
                                      packer.packArrayHeader(size)
                                      val it = deconstructor.deconstruct(value)
                                      while(it.hasNext) elementCodec.encode(it.next(), packer)
                                  }
                              }.asInstanceOf[MessagePackBinaryCodec[Col[Elem]]]
                          case MessagePackBinaryCodec.longType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                              new MessagePackBinaryCodec[Col[Long]]() {
                                  private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                                  private[this] val constructor = binding.constructor
                                  private[this] val elementCodec = codec.asInstanceOf[MessagePackBinaryCodec[Long]]
                                  def decodeUnsafe(unpacker: MessageUnpacker): Col[Long] = {
                                      val size = unpacker.unpackArrayHeader()
                                      val builder = constructor.newLongBuilder()
                                      var i = 0
                                      while(i < size) {
                                          constructor.addLong(builder, elementCodec.decodeUnsafe(unpacker))
                                          i += 1
                                      }
                                      constructor.resultLong(builder)
                                  }
                                  def encode(value: Col[Long], packer: MessageBufferPacker): Unit = {
                                      val size = deconstructor.size(value)
                                      packer.packArrayHeader(size)
                                      val it = deconstructor.deconstruct(value)
                                      while(it.hasNext) elementCodec.encode(it.next(), packer)
                                  }
                              }.asInstanceOf[MessagePackBinaryCodec[Col[Elem]]]
                          case MessagePackBinaryCodec.floatType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                              new MessagePackBinaryCodec[Col[Float]]() {
                                  private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                                  private[this] val constructor = binding.constructor
                                  private[this] val elementCodec = codec.asInstanceOf[MessagePackBinaryCodec[Float]]
                                  def decodeUnsafe(unpacker: MessageUnpacker): Col[Float] = {
                                      val size = unpacker.unpackArrayHeader()
                                      val builder = constructor.newFloatBuilder()
                                      var i = 0
                                      while(i < size) {
                                          constructor.addFloat(builder, elementCodec.decodeUnsafe(unpacker))
                                          i += 1
                                      }
                                      constructor.resultFloat(builder)
                                  }
                                  def encode(value: Col[Float], packer: MessageBufferPacker): Unit = {
                                      val size = deconstructor.size(value)
                                      packer.packArrayHeader(size)
                                      val it = deconstructor.deconstruct(value)
                                      while(it.hasNext) elementCodec.encode(it.next(), packer)
                                  }
                              }.asInstanceOf[MessagePackBinaryCodec[Col[Elem]]]
                          case MessagePackBinaryCodec.doubleType if binding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                              new MessagePackBinaryCodec[Col[Double]]() {
                                  private[this] val deconstructor = binding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                                  private[this] val constructor = binding.constructor
                                  private[this] val elementCodec = codec.asInstanceOf[MessagePackBinaryCodec[Double]]
                                  def decodeUnsafe(unpacker: MessageUnpacker): Col[Double] = {
                                      val size = unpacker.unpackArrayHeader()
                                      val builder = constructor.newDoubleBuilder()
                                      var i = 0
                                      while(i < size) {
                                          constructor.addDouble(builder, elementCodec.decodeUnsafe(unpacker))
                                          i += 1
                                      }
                                      constructor.resultDouble(builder)
                                  }
                                  def encode(value: Col[Double], packer: MessageBufferPacker): Unit = {
                                      val size = deconstructor.size(value)
                                      packer.packArrayHeader(size)
                                      val it = deconstructor.deconstruct(value)
                                      while(it.hasNext) elementCodec.encode(it.next(), packer)
                                  }
                              }.asInstanceOf[MessagePackBinaryCodec[Col[Elem]]]
                          // TODO: specialized for other types...
                          case _ => 
                               new MessagePackBinaryCodec[Col[Elem]]() {
                                    def decodeUnsafe(unpacker: MessageUnpacker): Col[Elem] = {
                                        val size = unpacker.unpackArrayHeader()
                                        val builder = binding.constructor.newObjectBuilder[Elem]()
                                        var i = 0
                                        try {
                                            while(i < size) {
                                                binding.constructor.addObject(builder, codec.decodeUnsafe(unpacker))
                                                i += 1
                                            }
                                        } catch {
                                            case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(i), error)
                                        }
                                        binding.constructor.resultObject(builder)
                                    }
                                    
                                    def encode(value: Col[Elem], packer: MessageBufferPacker): Unit = {
                                        val size = binding.deconstructor.size(value)
                                        packer.packArrayHeader(size)
                                        val it = binding.deconstructor.deconstruct(value)
                                        while(it.hasNext) {
                                            codec.encode(it.next(), packer)
                                        }
                                    }
                               }
                      }
                 } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, Col[Elem]]].instance.force
            } else if (reflect.isMap) {
                 val map = reflect.asMapUnknown.get.map
                 type Key
                 type Value
                 type Map[k, v]
                 if(map.mapBinding.isInstanceOf[Binding[?, ?]]) {
                     val binding = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
                     val keyCodec = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[Key]]
                     val valueCodec = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[Value]]
                     
                     new MessagePackBinaryCodec[Map[Key, Value]]() {
                          def decodeUnsafe(unpacker: MessageUnpacker): Map[Key, Value] = {
                              val size = unpacker.unpackMapHeader()
                              val builder = binding.constructor.newObjectBuilder[Key, Value]()
                              var i = 0
                              try {
                                  while(i < size) {
                                      val key = keyCodec.decodeUnsafe(unpacker)
                                      val value = valueCodec.decodeUnsafe(unpacker)
                                      binding.constructor.addObject(builder, key, value)
                                      i += 1
                                  }
                              } catch {
                                  case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(i), error)
                              }
                              binding.constructor.resultObject(builder)
                          }
                          
                          def encode(value: Map[Key, Value], packer: MessageBufferPacker): Unit = {
                              val size = binding.deconstructor.size(value)
                              packer.packMapHeader(size)
                              val it = binding.deconstructor.deconstruct(value)
                              while(it.hasNext) {
                                 val kv = it.next()
                                 val k = binding.deconstructor.getKey(kv)
                                 val v = binding.deconstructor.getValue(kv)
                                 keyCodec.encode(k, packer)
                                 valueCodec.encode(v, packer)
                              }
                          }
                     }
                 } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, Map[Key, Value]]].instance.force
             } else if (reflect.isWrapper) {
                  val wrapper = reflect.asWrapperUnknown.get.wrapper
                  type Wrapped
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

                      def encode(value: A, packer: MessageBufferPacker): Unit = wrappedCodec.encode(unwrap(value), packer)
                    }
                  } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
            } else {
                val dynamic = reflect.asDynamic.get
                if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
                else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
            }
        }.asInstanceOf[MessagePackBinaryCodec[A]]

        private[this] lazy val dynamicValueCodec: MessagePackBinaryCodec[DynamicValue] = new MessagePackBinaryCodec[DynamicValue]() {
          def decodeUnsafe(unpacker: MessageUnpacker): DynamicValue = unpacker.unpackInt() match {
            case 0 =>
              try {
                val idx = unpacker.unpackInt()
                if (idx < 0 || idx > 29) decodeError(s"Expected enum index from 0 to 29, got $idx")
                try {
                  new DynamicValue.Primitive((idx: @scala.annotation.switch) match {
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
                    case _  => new PrimitiveValue.UUID(uuidCodec.decodeUnsafe(unpacker))
                  })
                } catch {
                  case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Field("value"), error)
                }
              } catch {
                case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Primitive"), error)
              }
            case 1 =>
              try {
                val builder = Vector.newBuilder[(String, DynamicValue)]
                var size    = unpacker.unpackArrayHeader()
                if(size % 2 != 0) decodeError(s"Expected even number of elements for Record fields (key-value pairs), got $size")
                size = size / 2
                var i = 0
                while (i < size) {
                  val k =
                    try unpacker.unpackString()
                    catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(i), new DynamicOptic.Node.Field("_1"), error)
                    }
                  val v =
                    try decodeUnsafe(unpacker)
                    catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(i), new DynamicOptic.Node.Field("_2"), error)
                    }
                  builder.addOne((k, v))
                  i += 1
                }
                new DynamicValue.Record(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Record"), new DynamicOptic.Node.Field("fields"), error)
              }
            case 2 =>
              val caseName =
                try unpacker.unpackString()
                catch {
                  case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Variant"), new DynamicOptic.Node.Field("caseName"), error)
                }
              val value =
                try decodeUnsafe(unpacker)
                catch {
                  case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Variant"), new DynamicOptic.Node.Field("value"), error)
                }
              new DynamicValue.Variant(caseName, value)
            case 3 =>
              try {
                val builder = Vector.newBuilder[DynamicValue]
                val size    = unpacker.unpackArrayHeader()
                var i = 0
                while (i < size) {
                  try {
                    builder.addOne(decodeUnsafe(unpacker))
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(i), error)
                  }
                  i += 1
                }
                new DynamicValue.Sequence(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Sequence"), new DynamicOptic.Node.Field("elements"), error)
              }
            case 4 =>
              try {
                val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
                var size    = unpacker.unpackArrayHeader()
                if(size % 2 != 0) decodeError(s"Expected even number of elements for Map entries (key-value pairs), got $size")
                size = size / 2
                var i = 0
                while (i < size) {
                  val k =
                    try decodeUnsafe(unpacker)
                    catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(i), new DynamicOptic.Node.Field("_1"), error)
                    }
                  val v =
                    try decodeUnsafe(unpacker)
                    catch {
                      case error if NonFatal(error) =>
                        decodeError(new DynamicOptic.Node.AtIndex(i), new DynamicOptic.Node.Field("_2"), error)
                    }
                  builder.addOne((k, v))
                  i += 1
                }
                new DynamicValue.Map(builder.result())
              } catch {
                case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Map"), new DynamicOptic.Node.Field("entries"), error)
              }
            case idx => decodeError(s"Expected enum index from 0 to 4, got $idx")
          }

          def encode(value: DynamicValue, packer: MessageBufferPacker): Unit = value match {
            case primitive: DynamicValue.Primitive =>
              packer.packInt(0)
              primitive.value match {
                case _: PrimitiveValue.Unit.type =>
                  packer.packInt(0)
                case v: PrimitiveValue.Boolean =>
                  packer.packInt(1)
                  booleanCodec.encode(v.value, packer)
                case v: PrimitiveValue.Byte =>
                  packer.packInt(2)
                  byteCodec.encode(v.value, packer)
                case v: PrimitiveValue.Short =>
                  packer.packInt(3)
                  shortCodec.encode(v.value, packer)
                case v: PrimitiveValue.Int =>
                  packer.packInt(4)
                  intCodec.encode(v.value, packer)
                case v: PrimitiveValue.Long =>
                  packer.packInt(5)
                  longCodec.encode(v.value, packer)
                case v: PrimitiveValue.Float =>
                  packer.packInt(6)
                  floatCodec.encode(v.value, packer)
                case v: PrimitiveValue.Double =>
                  packer.packInt(7)
                  doubleCodec.encode(v.value, packer)
                case v: PrimitiveValue.Char =>
                  packer.packInt(8)
                  charCodec.encode(v.value, packer)
                case v: PrimitiveValue.String =>
                  packer.packInt(9)
                  stringCodec.encode(v.value, packer)
                case v: PrimitiveValue.BigInt =>
                  packer.packInt(10)
                  bigIntCodec.encode(v.value, packer)
                case v: PrimitiveValue.BigDecimal =>
                  packer.packInt(11)
                  bigDecimalCodec.encode(v.value, packer)
                case v: PrimitiveValue.DayOfWeek =>
                  packer.packInt(12)
                  dayOfWeekCodec.encode(v.value, packer)
                case v: PrimitiveValue.Duration =>
                  packer.packInt(13)
                  durationCodec.encode(v.value, packer)
                case v: PrimitiveValue.Instant =>
                  packer.packInt(14)
                  instantCodec.encode(v.value, packer)
                case v: PrimitiveValue.LocalDate =>
                  packer.packInt(15)
                  localDateCodec.encode(v.value, packer)
                case v: PrimitiveValue.LocalDateTime =>
                  packer.packInt(16)
                  localDateTimeCodec.encode(v.value, packer)
                case v: PrimitiveValue.LocalTime =>
                  packer.packInt(17)
                  localTimeCodec.encode(v.value, packer)
                case v: PrimitiveValue.Month =>
                  packer.packInt(18)
                  monthCodec.encode(v.value, packer)
                case v: PrimitiveValue.MonthDay =>
                  packer.packInt(19)
                  monthDayCodec.encode(v.value, packer)
                case v: PrimitiveValue.OffsetDateTime =>
                  packer.packInt(20)
                  offsetDateTimeCodec.encode(v.value, packer)
                case v: PrimitiveValue.OffsetTime =>
                  packer.packInt(21)
                  offsetTimeCodec.encode(v.value, packer)
                case v: PrimitiveValue.Period =>
                  packer.packInt(22)
                  periodCodec.encode(v.value, packer)
                case v: PrimitiveValue.Year =>
                  packer.packInt(23)
                  yearCodec.encode(v.value, packer)
                case v: PrimitiveValue.YearMonth =>
                  packer.packInt(24)
                  yearMonthCodec.encode(v.value, packer)
                case v: PrimitiveValue.ZoneId =>
                  packer.packInt(25)
                  zoneIdCodec.encode(v.value, packer)
                case v: PrimitiveValue.ZoneOffset =>
                  packer.packInt(26)
                  zoneOffsetCodec.encode(v.value, packer)
                case v: PrimitiveValue.ZonedDateTime =>
                  packer.packInt(27)
                  zonedDateTimeCodec.encode(v.value, packer)
                case v: PrimitiveValue.Currency =>
                  packer.packInt(28)
                  currencyCodec.encode(v.value, packer)
                case v: PrimitiveValue.UUID =>
                  packer.packInt(29)
                  uuidCodec.encode(v.value, packer)
              }
            case record: DynamicValue.Record =>
              packer.packInt(1)
              val fields = record.fields
              packer.packArrayHeader(fields.length * 2) 
              val it = fields.iterator
              while (it.hasNext) {
                  val kv = it.next()
                  packer.packString(kv._1)
                  encode(kv._2, packer)
              }
            case variant: DynamicValue.Variant =>
              packer.packInt(2)
              packer.packString(variant.caseName)
              encode(variant.value, packer)
            case sequence: DynamicValue.Sequence =>
              packer.packInt(3)
              val elements = sequence.elements
              packer.packArrayHeader(elements.length)
              val it = elements.iterator
              while (it.hasNext) {
                  encode(it.next(), packer)
              }
            case map: DynamicValue.Map =>
              packer.packInt(4)
              val entries = map.entries
              packer.packArrayHeader(entries.length * 2)
              val it = entries.iterator
              while(it.hasNext) {
                  val kv = it.next()
                  encode(kv._1, packer)
                  encode(kv._2, packer)
              }
          }
        }



    }
)
