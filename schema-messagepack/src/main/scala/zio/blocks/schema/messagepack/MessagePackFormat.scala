package zio.blocks.schema.messagepack

import org.msgpack.core.{MessagePacker, MessageUnpacker}
import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver}

import java.nio.charset.StandardCharsets
import java.time._
import java.util.{Currency, UUID}
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

        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): MessagePackBinaryCodec[A] = {
          if (reflect.isPrimitive) derivePrimitiveCodec(reflect.asPrimitive.get)
          else if (reflect.isVariant) deriveVariantCodec(reflect.asVariant.get)
          else if (reflect.isSequence) deriveSequenceCodec(reflect.asSequenceUnknown.get.sequence)
          else if (reflect.isMap) deriveMapCodec(reflect.asMapUnknown.get.map)
          else if (reflect.isRecord) deriveRecordCodec(reflect.asRecord.get)
          else if (reflect.isWrapper) deriveWrapperCodec(reflect.asWrapperUnknown.get.wrapper)
          else deriveDynamicCodec(reflect.asDynamic.get)
        }.asInstanceOf[MessagePackBinaryCodec[A]]

        private[this] def derivePrimitiveCodec[F[_, _], A](
          primitive: Reflect.Primitive[F, A]
        ): MessagePackBinaryCodec[A] = {
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
          } else primitive.primitiveBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance.force
        }.asInstanceOf[MessagePackBinaryCodec[A]]

        private[this] def deriveRecordCodec[F[_, _], A](record: Reflect.Record[F, A]): MessagePackBinaryCodec[A] =
          if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
            val binding    = record.recordBinding.asInstanceOf[Binding.Record[A]]
            val fields     = record.fields
            val fieldCount = fields.size
            val fieldCodecs  = new Array[MessagePackBinaryCodec[Any]](fieldCount)
            val fieldNames   = new Array[String](fieldCount)
            val fieldOffsets = new Array[Long](fieldCount)
            // O(1) field lookup map for efficient decoding
            val fieldIndexMap = new java.util.HashMap[String, Integer](fieldCount)
            var offset        = 0L
            var i             = 0
            while (i < fieldCount) {
              val field = fields(i)
              val codec = deriveCodec(field.value).asInstanceOf[MessagePackBinaryCodec[Any]]
              fieldCodecs(i) = codec
              fieldNames(i) = field.name
              fieldOffsets(i) = offset
              fieldIndexMap.put(field.name, i)
              offset = RegisterOffset.add(codec.valueOffset, offset)
              i += 1
            }
            val usedRegisters = binding.constructor.usedRegisters

            new MessagePackBinaryCodec[A]() {
              override def decodeValue(unpacker: MessageUnpacker): A = {
                val mapSize = unpacker.unpackMapHeader()
                val regs    = Registers(usedRegisters)
                var j       = 0
                while (j < mapSize) {
                  val key = unpacker.unpackString()
                  val idx = fieldIndexMap.get(key)
                  if (idx ne null) {
                    try {
                      val codec = fieldCodecs(idx)
                      val off   = fieldOffsets(idx)
                      (codec.valueType: @scala.annotation.switch) match {
                        case MessagePackBinaryCodec.objectType =>
                          regs.setObject(off, codec.decodeValue(unpacker).asInstanceOf[AnyRef])
                        case MessagePackBinaryCodec.booleanType =>
                          regs.setBoolean(off, codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].decodeValue(unpacker))
                        case MessagePackBinaryCodec.byteType =>
                          regs.setByte(off, codec.asInstanceOf[MessagePackBinaryCodec[Byte]].decodeValue(unpacker))
                        case MessagePackBinaryCodec.charType =>
                          regs.setChar(off, codec.asInstanceOf[MessagePackBinaryCodec[Char]].decodeValue(unpacker))
                        case MessagePackBinaryCodec.shortType =>
                          regs.setShort(off, codec.asInstanceOf[MessagePackBinaryCodec[Short]].decodeValue(unpacker))
                        case MessagePackBinaryCodec.floatType =>
                          regs.setFloat(off, codec.asInstanceOf[MessagePackBinaryCodec[Float]].decodeValue(unpacker))
                        case MessagePackBinaryCodec.intType =>
                          regs.setInt(off, codec.asInstanceOf[MessagePackBinaryCodec[Int]].decodeValue(unpacker))
                        case MessagePackBinaryCodec.doubleType =>
                          regs.setDouble(off, codec.asInstanceOf[MessagePackBinaryCodec[Double]].decodeValue(unpacker))
                        case MessagePackBinaryCodec.longType =>
                          regs.setLong(off, codec.asInstanceOf[MessagePackBinaryCodec[Long]].decodeValue(unpacker))
                        case _ =>
                          codec.asInstanceOf[MessagePackBinaryCodec[Unit]].decodeValue(unpacker)
                      }
                    } catch {
                      case err if NonFatal(err) =>
                        decodeError(DynamicOptic.Node.Field(key), err)
                    }
                  } else {
                    // Forward compatibility: skip unknown fields
                    unpacker.skipValue()
                  }
                  j += 1
                }
                binding.constructor.construct(regs, 0)
              }

              override def encodeValue(value: A, packer: MessagePacker): Unit = {
                packer.packMapHeader(fieldCount)
                val regs = Registers(usedRegisters)
                binding.deconstructor.deconstruct(regs, 0, value)
                var offset = 0L
                var i = 0
                while (i < fieldCount) {
                  packer.packString(fieldNames(i))
                  val codec = fieldCodecs(i)
                  (codec.valueType: @scala.annotation.switch) match {
                    case MessagePackBinaryCodec.objectType =>
                      codec.encodeValue(regs.getObject(offset), packer)
                    case MessagePackBinaryCodec.booleanType =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].encodeValue(regs.getBoolean(offset), packer)
                    case MessagePackBinaryCodec.byteType =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Byte]].encodeValue(regs.getByte(offset), packer)
                    case MessagePackBinaryCodec.charType =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Char]].encodeValue(regs.getChar(offset), packer)
                    case MessagePackBinaryCodec.shortType =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Short]].encodeValue(regs.getShort(offset), packer)
                    case MessagePackBinaryCodec.floatType =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Float]].encodeValue(regs.getFloat(offset), packer)
                    case MessagePackBinaryCodec.intType =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Int]].encodeValue(regs.getInt(offset), packer)
                    case MessagePackBinaryCodec.doubleType =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Double]].encodeValue(regs.getDouble(offset), packer)
                    case MessagePackBinaryCodec.longType =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Long]].encodeValue(regs.getLong(offset), packer)
                    case _ =>
                      codec.asInstanceOf[MessagePackBinaryCodec[Unit]].encodeValue((), packer)
                  }
                  offset = RegisterOffset.add(codec.valueOffset, offset)
                  i += 1
                }
              }
            }
          } else {
            record.recordBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance.force
          }

        private[this] def deriveVariantCodec[F[_, _], A](variant: Reflect.Variant[F, A]): MessagePackBinaryCodec[A] =
          if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
            val binding    = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
            val cases      = variant.cases
            val caseCount  = cases.size
            val caseCodecs = new Array[MessagePackBinaryCodec[Any]](caseCount)
            val caseNames  = new Array[String](caseCount)
            var i          = 0
            while (i < caseCount) {
              val c = cases(i)
              caseCodecs(i) = deriveCodec(c.value).asInstanceOf[MessagePackBinaryCodec[Any]]
              caseNames(i) = c.name
              i += 1
            }

            new MessagePackBinaryCodec[A]() {
              override def decodeValue(unpacker: MessageUnpacker): A = {
                val mapSize = unpacker.unpackMapHeader()
                if (mapSize < 1) decodeError("Expected at least one field for variant")
                val typeKey = unpacker.unpackString()
                if (typeKey != "_type") decodeError(s"Expected '_type' field, got '$typeKey'")
                val typeName  = unpacker.unpackString()
                var idx       = 0
                var found     = false
                var result: A = null.asInstanceOf[A]
                while (idx < caseCount && !found) {
                  if (caseNames(idx) == typeName) {
                    found = true
                    if (mapSize > 1) {
                      val valueKey = unpacker.unpackString()
                      if (valueKey == "value") {
                        try result = caseCodecs(idx).decodeValue(unpacker).asInstanceOf[A]
                        catch {
                          case err if NonFatal(err) =>
                            decodeError(DynamicOptic.Node.Case(typeName), err)
                        }
                      } else {
                        decodeError(s"Expected 'value' field, got '$valueKey'")
                      }
                    } else {
                      // Unit-type case with only _type field (no value field)
                      // Use nullValue which returns () for Unit types
                      result = caseCodecs(idx).nullValue.asInstanceOf[A]
                    }
                  }
                  idx += 1
                }
                if (!found) decodeError(s"Unknown variant case: $typeName")
                result
              }

              override def encodeValue(value: A, packer: MessagePacker): Unit = {
                val idx = binding.discriminator.discriminate(value)
                packer.packMapHeader(2)
                packer.packString("_type")
                packer.packString(caseNames(idx))
                packer.packString("value")
                caseCodecs(idx).encodeValue(value.asInstanceOf[Any], packer)
              }
            }
          } else {
            variant.variantBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance.force
          }

        private[this] def deriveSequenceCodec[F[_, _], A, C[_]](
          sequence: Reflect.Sequence[F, A, C]
        ): MessagePackBinaryCodec[C[A]] =
          if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
            val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[C, A]]
            val elementCodec = deriveCodec(sequence.element).asInstanceOf[MessagePackBinaryCodec[A]]

            new MessagePackBinaryCodec[C[A]]() {
              override def decodeValue(unpacker: MessageUnpacker): C[A] = {
                val size    = unpacker.unpackArrayHeader()
                val builder = binding.constructor.newObjectBuilder[A](size)
                var i       = 0
                while (i < size) {
                  try binding.constructor.addObject(builder, elementCodec.decodeValue(unpacker))
                  catch {
                    case err if NonFatal(err) =>
                      decodeError(DynamicOptic.Node.AtIndex(i), err)
                  }
                  i += 1
                }
                binding.constructor.resultObject(builder)
              }

              override def encodeValue(value: C[A], packer: MessagePacker): Unit = {
                val size = binding.deconstructor.size(value)
                packer.packArrayHeader(size)
                val iterator = binding.deconstructor.deconstruct(value)
                while (iterator.hasNext) {
                  elementCodec.encodeValue(iterator.next(), packer)
                }
              }
            }
          } else {
            sequence.seqBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, C[A]]].instance.force
          }

        private[this] def deriveMapCodec[F[_, _], K, V, M[_, _]](
          map: Reflect.Map[F, K, V, M]
        ): MessagePackBinaryCodec[M[K, V]] =
          if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
            val binding    = map.mapBinding.asInstanceOf[Binding.Map[M, K, V]]
            val keyCodec   = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[K]]
            val valueCodec = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[V]]

            new MessagePackBinaryCodec[M[K, V]]() {
              override def decodeValue(unpacker: MessageUnpacker): M[K, V] = {
                val size    = unpacker.unpackMapHeader()
                val builder = binding.constructor.newObjectBuilder[K, V](size)
                var i       = 0
                while (i < size) {
                  val k =
                    try keyCodec.decodeValue(unpacker)
                    catch {
                      case err if NonFatal(err) =>
                        decodeError(DynamicOptic.Node.MapKeys, err)
                    }
                  val v =
                    try valueCodec.decodeValue(unpacker)
                    catch {
                      case err if NonFatal(err) =>
                        decodeError(DynamicOptic.Node.MapValues, err)
                    }
                  binding.constructor.addObject(builder, k, v)
                  i += 1
                }
                binding.constructor.resultObject(builder)
              }

              override def encodeValue(value: M[K, V], packer: MessagePacker): Unit = {
                val size = binding.deconstructor.size(value)
                packer.packMapHeader(size)
                val iterator = binding.deconstructor.deconstruct(value)
                while (iterator.hasNext) {
                  val kv = iterator.next()
                  keyCodec.encodeValue(binding.deconstructor.getKey(kv), packer)
                  valueCodec.encodeValue(binding.deconstructor.getValue(kv), packer)
                }
              }
            }
          } else {
            map.mapBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, M[K, V]]].instance.force
          }

        private[this] def deriveWrapperCodec[F[_, _], A, B](
          wrapper: Reflect.Wrapper[F, A, B]
        ): MessagePackBinaryCodec[A] =
          if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
            val binding      = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, B]]
            val wrappedCodec = deriveCodec(wrapper.wrapped).asInstanceOf[MessagePackBinaryCodec[B]]

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
              override def decodeValue(unpacker: MessageUnpacker): A = {
                val wrapped =
                  try wrappedCodec.decodeValue(unpacker)
                  catch {
                    case err if NonFatal(err) =>
                      decodeError(DynamicOptic.Node.Wrapped, err)
                  }
                binding.wrap(wrapped) match {
                  case Right(a)    => a
                  case Left(error) => decodeError(error)
                }
              }

              override def encodeValue(value: A, packer: MessagePacker): Unit =
                wrappedCodec.encodeValue(binding.unwrap(value), packer)
            }
          } else {
            wrapper.wrapperBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance.force
          }

        private[this] def deriveDynamicCodec[F[_, _]](
          dynamic: Reflect.Dynamic[F]
        ): MessagePackBinaryCodec[DynamicValue] =
          if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
          else
            dynamic.dynamicBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, DynamicValue]].instance.force

        // Primitive codecs
        private val unitCodec: MessagePackBinaryCodec[Unit] =
          new MessagePackBinaryCodec[Unit](MessagePackBinaryCodec.unitType) {
            override def decodeValue(unpacker: MessageUnpacker): Unit = {
              unpacker.unpackNil()
              ()
            }
            override def encodeValue(value: Unit, packer: MessagePacker): Unit =
              packer.packNil()
          }

        private val booleanCodec: MessagePackBinaryCodec[Boolean] =
          new MessagePackBinaryCodec[Boolean](MessagePackBinaryCodec.booleanType) {
            override def decodeValue(unpacker: MessageUnpacker): Boolean =
              unpacker.unpackBoolean()
            override def encodeValue(value: Boolean, packer: MessagePacker): Unit =
              packer.packBoolean(value)
          }

        private val byteCodec: MessagePackBinaryCodec[Byte] =
          new MessagePackBinaryCodec[Byte](MessagePackBinaryCodec.byteType) {
            override def decodeValue(unpacker: MessageUnpacker): Byte =
              unpacker.unpackByte()
            override def encodeValue(value: Byte, packer: MessagePacker): Unit =
              packer.packByte(value)
          }

        private val shortCodec: MessagePackBinaryCodec[Short] =
          new MessagePackBinaryCodec[Short](MessagePackBinaryCodec.shortType) {
            override def decodeValue(unpacker: MessageUnpacker): Short =
              unpacker.unpackShort()
            override def encodeValue(value: Short, packer: MessagePacker): Unit =
              packer.packShort(value)
          }

        private val intCodec: MessagePackBinaryCodec[Int] =
          new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
            override def decodeValue(unpacker: MessageUnpacker): Int =
              unpacker.unpackInt()
            override def encodeValue(value: Int, packer: MessagePacker): Unit =
              packer.packInt(value)
          }

        private val longCodec: MessagePackBinaryCodec[Long] =
          new MessagePackBinaryCodec[Long](MessagePackBinaryCodec.longType) {
            override def decodeValue(unpacker: MessageUnpacker): Long =
              unpacker.unpackLong()
            override def encodeValue(value: Long, packer: MessagePacker): Unit =
              packer.packLong(value)
          }

        private val floatCodec: MessagePackBinaryCodec[Float] =
          new MessagePackBinaryCodec[Float](MessagePackBinaryCodec.floatType) {
            override def decodeValue(unpacker: MessageUnpacker): Float =
              unpacker.unpackFloat()
            override def encodeValue(value: Float, packer: MessagePacker): Unit =
              packer.packFloat(value)
          }

        private val doubleCodec: MessagePackBinaryCodec[Double] =
          new MessagePackBinaryCodec[Double](MessagePackBinaryCodec.doubleType) {
            override def decodeValue(unpacker: MessageUnpacker): Double =
              unpacker.unpackDouble()
            override def encodeValue(value: Double, packer: MessagePacker): Unit =
              packer.packDouble(value)
          }

        private val charCodec: MessagePackBinaryCodec[Char] =
          new MessagePackBinaryCodec[Char](MessagePackBinaryCodec.charType) {
            override def decodeValue(unpacker: MessageUnpacker): Char = {
              val s = unpacker.unpackString()
              if (s.length == 1) s.charAt(0)
              else throw new MessagePackBinaryCodecError(Nil, s"Expected single char, got: $s")
            }
            override def encodeValue(value: Char, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val stringCodec: MessagePackBinaryCodec[String] =
          new MessagePackBinaryCodec[String]() {
            override def decodeValue(unpacker: MessageUnpacker): String =
              unpacker.unpackString()
            override def encodeValue(value: String, packer: MessagePacker): Unit =
              packer.packString(value)
          }

        private val bigIntCodec: MessagePackBinaryCodec[BigInt] =
          new MessagePackBinaryCodec[BigInt]() {
            override def decodeValue(unpacker: MessageUnpacker): BigInt =
              BigInt(unpacker.unpackString())
            override def encodeValue(value: BigInt, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val bigDecimalCodec: MessagePackBinaryCodec[BigDecimal] =
          new MessagePackBinaryCodec[BigDecimal]() {
            override def decodeValue(unpacker: MessageUnpacker): BigDecimal =
              BigDecimal(unpacker.unpackString())
            override def encodeValue(value: BigDecimal, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val dayOfWeekCodec: MessagePackBinaryCodec[DayOfWeek] =
          new MessagePackBinaryCodec[DayOfWeek]() {
            override def decodeValue(unpacker: MessageUnpacker): DayOfWeek =
              DayOfWeek.valueOf(unpacker.unpackString())
            override def encodeValue(value: DayOfWeek, packer: MessagePacker): Unit =
              packer.packString(value.name)
          }

        private val durationCodec: MessagePackBinaryCodec[Duration] =
          new MessagePackBinaryCodec[Duration]() {
            override def decodeValue(unpacker: MessageUnpacker): Duration =
              Duration.parse(unpacker.unpackString())
            override def encodeValue(value: Duration, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val instantCodec: MessagePackBinaryCodec[Instant] =
          new MessagePackBinaryCodec[Instant]() {
            override def decodeValue(unpacker: MessageUnpacker): Instant =
              Instant.parse(unpacker.unpackString())
            override def encodeValue(value: Instant, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val localDateCodec: MessagePackBinaryCodec[LocalDate] =
          new MessagePackBinaryCodec[LocalDate]() {
            override def decodeValue(unpacker: MessageUnpacker): LocalDate =
              LocalDate.parse(unpacker.unpackString())
            override def encodeValue(value: LocalDate, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val localDateTimeCodec: MessagePackBinaryCodec[LocalDateTime] =
          new MessagePackBinaryCodec[LocalDateTime]() {
            override def decodeValue(unpacker: MessageUnpacker): LocalDateTime =
              LocalDateTime.parse(unpacker.unpackString())
            override def encodeValue(value: LocalDateTime, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val localTimeCodec: MessagePackBinaryCodec[LocalTime] =
          new MessagePackBinaryCodec[LocalTime]() {
            override def decodeValue(unpacker: MessageUnpacker): LocalTime =
              LocalTime.parse(unpacker.unpackString())
            override def encodeValue(value: LocalTime, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val monthCodec: MessagePackBinaryCodec[Month] =
          new MessagePackBinaryCodec[Month]() {
            override def decodeValue(unpacker: MessageUnpacker): Month =
              Month.valueOf(unpacker.unpackString())
            override def encodeValue(value: Month, packer: MessagePacker): Unit =
              packer.packString(value.name)
          }

        private val monthDayCodec: MessagePackBinaryCodec[MonthDay] =
          new MessagePackBinaryCodec[MonthDay]() {
            override def decodeValue(unpacker: MessageUnpacker): MonthDay =
              MonthDay.parse(unpacker.unpackString())
            override def encodeValue(value: MonthDay, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val offsetDateTimeCodec: MessagePackBinaryCodec[OffsetDateTime] =
          new MessagePackBinaryCodec[OffsetDateTime]() {
            override def decodeValue(unpacker: MessageUnpacker): OffsetDateTime =
              OffsetDateTime.parse(unpacker.unpackString())
            override def encodeValue(value: OffsetDateTime, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val offsetTimeCodec: MessagePackBinaryCodec[OffsetTime] =
          new MessagePackBinaryCodec[OffsetTime]() {
            override def decodeValue(unpacker: MessageUnpacker): OffsetTime =
              OffsetTime.parse(unpacker.unpackString())
            override def encodeValue(value: OffsetTime, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val periodCodec: MessagePackBinaryCodec[Period] =
          new MessagePackBinaryCodec[Period]() {
            override def decodeValue(unpacker: MessageUnpacker): Period =
              Period.parse(unpacker.unpackString())
            override def encodeValue(value: Period, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val yearCodec: MessagePackBinaryCodec[Year] =
          new MessagePackBinaryCodec[Year]() {
            override def decodeValue(unpacker: MessageUnpacker): Year =
              Year.of(unpacker.unpackInt())
            override def encodeValue(value: Year, packer: MessagePacker): Unit =
              packer.packInt(value.getValue)
          }

        private val yearMonthCodec: MessagePackBinaryCodec[YearMonth] =
          new MessagePackBinaryCodec[YearMonth]() {
            override def decodeValue(unpacker: MessageUnpacker): YearMonth =
              YearMonth.parse(unpacker.unpackString())
            override def encodeValue(value: YearMonth, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val zoneIdCodec: MessagePackBinaryCodec[ZoneId] =
          new MessagePackBinaryCodec[ZoneId]() {
            override def decodeValue(unpacker: MessageUnpacker): ZoneId =
              ZoneId.of(unpacker.unpackString())
            override def encodeValue(value: ZoneId, packer: MessagePacker): Unit =
              packer.packString(value.getId)
          }

        private val zoneOffsetCodec: MessagePackBinaryCodec[ZoneOffset] =
          new MessagePackBinaryCodec[ZoneOffset]() {
            override def decodeValue(unpacker: MessageUnpacker): ZoneOffset =
              ZoneOffset.of(unpacker.unpackString())
            override def encodeValue(value: ZoneOffset, packer: MessagePacker): Unit =
              packer.packString(value.getId)
          }

        private val zonedDateTimeCodec: MessagePackBinaryCodec[ZonedDateTime] =
          new MessagePackBinaryCodec[ZonedDateTime]() {
            override def decodeValue(unpacker: MessageUnpacker): ZonedDateTime =
              ZonedDateTime.parse(unpacker.unpackString())
            override def encodeValue(value: ZonedDateTime, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val currencyCodec: MessagePackBinaryCodec[Currency] =
          new MessagePackBinaryCodec[Currency]() {
            override def decodeValue(unpacker: MessageUnpacker): Currency =
              Currency.getInstance(unpacker.unpackString())
            override def encodeValue(value: Currency, packer: MessagePacker): Unit =
              packer.packString(value.getCurrencyCode)
          }

        private val uuidCodec: MessagePackBinaryCodec[UUID] =
          new MessagePackBinaryCodec[UUID]() {
            override def decodeValue(unpacker: MessageUnpacker): UUID =
              UUID.fromString(unpacker.unpackString())
            override def encodeValue(value: UUID, packer: MessagePacker): Unit =
              packer.packString(value.toString)
          }

        private val dynamicValueCodec: MessagePackBinaryCodec[DynamicValue] =
          new MessagePackBinaryCodec[DynamicValue]() {
            override def decodeValue(unpacker: MessageUnpacker): DynamicValue = {
              val format = unpacker.getNextFormat
              format.getValueType match {
                case org.msgpack.value.ValueType.NIL =>
                  unpacker.unpackNil()
                  DynamicValue.Primitive(PrimitiveValue.Unit)
                case org.msgpack.value.ValueType.BOOLEAN =>
                  DynamicValue.Primitive(PrimitiveValue.Boolean(unpacker.unpackBoolean()))
                case org.msgpack.value.ValueType.INTEGER =>
                  val l = unpacker.unpackLong()
                  if (l >= Int.MinValue && l <= Int.MaxValue)
                    DynamicValue.Primitive(PrimitiveValue.Int(l.toInt))
                  else
                    DynamicValue.Primitive(PrimitiveValue.Long(l))
                case org.msgpack.value.ValueType.FLOAT =>
                  DynamicValue.Primitive(PrimitiveValue.Double(unpacker.unpackDouble()))
                case org.msgpack.value.ValueType.STRING =>
                  DynamicValue.Primitive(PrimitiveValue.String(unpacker.unpackString()))
                case org.msgpack.value.ValueType.BINARY =>
                  val len   = unpacker.unpackBinaryHeader()
                  val bytes = new Array[Byte](len)
                  unpacker.readPayload(bytes)
                  DynamicValue.Primitive(PrimitiveValue.String(new String(bytes, StandardCharsets.UTF_8)))
                case org.msgpack.value.ValueType.ARRAY =>
                  val size    = unpacker.unpackArrayHeader()
                  val builder = Vector.newBuilder[DynamicValue]
                  var i       = 0
                  while (i < size) {
                    builder += decodeValue(unpacker)
                    i += 1
                  }
                  DynamicValue.Sequence(builder.result())
                case org.msgpack.value.ValueType.MAP =>
                  val size    = unpacker.unpackMapHeader()
                  val builder = Vector.newBuilder[(String, DynamicValue)]
                  var i       = 0
                  while (i < size) {
                    val key   = unpacker.unpackString()
                    val value = decodeValue(unpacker)
                    builder += ((key, value))
                    i += 1
                  }
                  DynamicValue.Record(builder.result())
                case org.msgpack.value.ValueType.EXTENSION =>
                  val header = unpacker.unpackExtensionTypeHeader()
                  val bytes  = new Array[Byte](header.getLength)
                  unpacker.readPayload(bytes)
                  DynamicValue.Primitive(PrimitiveValue.String(new String(bytes, StandardCharsets.UTF_8)))
              }
            }

            override def encodeValue(value: DynamicValue, packer: MessagePacker): Unit = value match {
              case DynamicValue.Primitive(p)   => encodePrimitive(p, packer)
              case DynamicValue.Record(fields) =>
                packer.packMapHeader(fields.size)
                fields.foreach { case (k, v) =>
                  packer.packString(k)
                  encodeValue(v, packer)
                }
              case DynamicValue.Variant(caseName, innerValue) =>
                packer.packMapHeader(2)
                packer.packString("_type")
                packer.packString(caseName)
                packer.packString("value")
                encodeValue(innerValue, packer)
              case DynamicValue.Sequence(elements) =>
                packer.packArrayHeader(elements.size)
                elements.foreach(encodeValue(_, packer))
              case DynamicValue.Map(entries) =>
                packer.packMapHeader(entries.size)
                entries.foreach { case (k, v) =>
                  encodeValue(k, packer)
                  encodeValue(v, packer)
                }
            }

            private def encodePrimitive(p: PrimitiveValue, packer: MessagePacker): Unit = p match {
              case _: PrimitiveValue.Unit.type      => packer.packNil()
              case v: PrimitiveValue.Boolean        => packer.packBoolean(v.value)
              case v: PrimitiveValue.Byte           => packer.packByte(v.value)
              case v: PrimitiveValue.Short          => packer.packShort(v.value)
              case v: PrimitiveValue.Int            => packer.packInt(v.value)
              case v: PrimitiveValue.Long           => packer.packLong(v.value)
              case v: PrimitiveValue.Float          => packer.packFloat(v.value)
              case v: PrimitiveValue.Double         => packer.packDouble(v.value)
              case v: PrimitiveValue.Char           => packer.packString(v.value.toString)
              case v: PrimitiveValue.String         => packer.packString(v.value)
              case v: PrimitiveValue.BigInt         => packer.packString(v.value.toString)
              case v: PrimitiveValue.BigDecimal     => packer.packString(v.value.toString)
              case v: PrimitiveValue.DayOfWeek      => packer.packString(v.value.name)
              case v: PrimitiveValue.Duration       => packer.packString(v.value.toString)
              case v: PrimitiveValue.Instant        => packer.packString(v.value.toString)
              case v: PrimitiveValue.LocalDate      => packer.packString(v.value.toString)
              case v: PrimitiveValue.LocalDateTime  => packer.packString(v.value.toString)
              case v: PrimitiveValue.LocalTime      => packer.packString(v.value.toString)
              case v: PrimitiveValue.Month          => packer.packString(v.value.name)
              case v: PrimitiveValue.MonthDay       => packer.packString(v.value.toString)
              case v: PrimitiveValue.OffsetDateTime => packer.packString(v.value.toString)
              case v: PrimitiveValue.OffsetTime     => packer.packString(v.value.toString)
              case v: PrimitiveValue.Period         => packer.packString(v.value.toString)
              case v: PrimitiveValue.Year           => packer.packInt(v.value.getValue)
              case v: PrimitiveValue.YearMonth      => packer.packString(v.value.toString)
              case v: PrimitiveValue.ZoneId         => packer.packString(v.value.getId)
              case v: PrimitiveValue.ZoneOffset     => packer.packString(v.value.getId)
              case v: PrimitiveValue.ZonedDateTime  => packer.packString(v.value.toString)
              case v: PrimitiveValue.Currency       => packer.packString(v.value.getCurrencyCode)
              case v: PrimitiveValue.UUID           => packer.packString(v.value.toString)
            }
          }
      }
    )
