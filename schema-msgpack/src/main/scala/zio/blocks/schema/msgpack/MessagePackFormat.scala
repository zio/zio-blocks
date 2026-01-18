/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
 *





 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *





 *     http://www.apache.org/licenses/LICENSE-2.0
 *





 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.msgpack

import org.msgpack.core.{MessagePacker, MessageUnpacker}
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding}
import zio.blocks.schema._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import org.msgpack.value.ValueType
import java.nio.ByteBuffer
import scala.collection.mutable

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

        type Elem
        type Key
        type Value
        type Wrapped
        type Col[_]
        type MapType[_, _]

        private[this] val unitCodec = new MessagePackBinaryCodec[Unit] {
          def encode(packer: MessagePacker, a: Unit): Unit  = packer.packNil()
          def decodeUnsafe(unpacker: MessageUnpacker): Unit = { unpacker.unpackNil(); () }
        }

        private[this] val booleanCodec = new MessagePackBinaryCodec[Boolean] {
          def encode(packer: MessagePacker, a: Boolean): Unit  = packer.packBoolean(a)
          def decodeUnsafe(unpacker: MessageUnpacker): Boolean = unpacker.unpackBoolean()
        }

        private[this] val byteCodec = new MessagePackBinaryCodec[Byte] {
          def encode(packer: MessagePacker, a: Byte): Unit  = packer.packByte(a)
          def decodeUnsafe(unpacker: MessageUnpacker): Byte = unpacker.unpackByte()
        }

        private[this] val shortCodec = new MessagePackBinaryCodec[Short] {
          def encode(packer: MessagePacker, a: Short): Unit  = packer.packShort(a)
          def decodeUnsafe(unpacker: MessageUnpacker): Short = unpacker.unpackShort()
        }

        private[this] val intCodec = new MessagePackBinaryCodec[Int] {
          def encode(packer: MessagePacker, a: Int): Unit  = packer.packInt(a)
          def decodeUnsafe(unpacker: MessageUnpacker): Int = unpacker.unpackInt()
        }

        private[this] val longCodec = new MessagePackBinaryCodec[Long] {
          def encode(packer: MessagePacker, a: Long): Unit  = packer.packLong(a)
          def decodeUnsafe(unpacker: MessageUnpacker): Long = unpacker.unpackLong()
        }

        private[this] val floatCodec = new MessagePackBinaryCodec[Float] {
          def encode(packer: MessagePacker, a: Float): Unit  = packer.packFloat(a)
          def decodeUnsafe(unpacker: MessageUnpacker): Float = unpacker.unpackFloat()
        }

        private[this] val doubleCodec = new MessagePackBinaryCodec[Double] {
          def encode(packer: MessagePacker, a: Double): Unit  = packer.packDouble(a)
          def decodeUnsafe(unpacker: MessageUnpacker): Double = unpacker.unpackDouble()
        }

        private[this] val charCodec = new MessagePackBinaryCodec[Char] {
          def encode(packer: MessagePacker, a: Char): Unit  = packer.packString(a.toString)
          def decodeUnsafe(unpacker: MessageUnpacker): Char = {
            val s = unpacker.unpackString()
            if (s.isEmpty) '\u0000' else s.charAt(0)
          }
        }

        private[this] val stringCodec = new MessagePackBinaryCodec[String] {
          def encode(packer: MessagePacker, a: String): Unit  = packer.packString(a)
          def decodeUnsafe(unpacker: MessageUnpacker): String = unpacker.unpackString()
        }

        private[this] val bigIntCodec = new MessagePackBinaryCodec[BigInt] {
          def encode(packer: MessagePacker, a: BigInt): Unit  = packer.packString(a.toString)
          def decodeUnsafe(unpacker: MessageUnpacker): BigInt = BigInt(unpacker.unpackString())
        }

        private[this] val bigDecimalCodec = new MessagePackBinaryCodec[BigDecimal] {
          def encode(packer: MessagePacker, a: BigDecimal): Unit  = packer.packString(a.toString)
          def decodeUnsafe(unpacker: MessageUnpacker): BigDecimal = BigDecimal(unpacker.unpackString())
        }

        private[this] val instantCodec = new MessagePackBinaryCodec[java.time.Instant] {
          def encode(packer: MessagePacker, a: java.time.Instant): Unit = {
            packer.packArrayHeader(2)
            packer.packLong(a.getEpochSecond)
            packer.packInt(a.getNano)
          }
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.Instant = {
            unpacker.unpackArrayHeader()
            java.time.Instant.ofEpochSecond(unpacker.unpackLong(), unpacker.unpackInt().toLong)
          }
        }

        private[this] val localDateCodec = new MessagePackBinaryCodec[java.time.LocalDate] {
          def encode(packer: MessagePacker, a: java.time.LocalDate): Unit = {
            packer.packArrayHeader(3)
            packer.packInt(a.getYear)
            packer.packInt(a.getMonthValue)
            packer.packInt(a.getDayOfMonth)
          }
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalDate = {
            unpacker.unpackArrayHeader()
            java.time.LocalDate.of(unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt())
          }
        }

        private[this] val localTimeCodec = new MessagePackBinaryCodec[java.time.LocalTime] {
          def encode(packer: MessagePacker, a: java.time.LocalTime): Unit = {
            packer.packArrayHeader(4)
            packer.packInt(a.getHour)
            packer.packInt(a.getMinute)
            packer.packInt(a.getSecond)
            packer.packInt(a.getNano)
          }
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.LocalTime = {
            unpacker.unpackArrayHeader()
            java.time.LocalTime
              .of(unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt(), unpacker.unpackInt())
          }
        }

        private[this] val localDateTimeCodec = new MessagePackBinaryCodec[java.time.LocalDateTime] {
          def encode(packer: MessagePacker, a: java.time.LocalDateTime): Unit = {
            packer.packArrayHeader(7)
            packer.packInt(a.getYear)
            packer.packInt(a.getMonthValue)
            packer.packInt(a.getDayOfMonth)
            packer.packInt(a.getHour)
            packer.packInt(a.getMinute)
            packer.packInt(a.getSecond)
            packer.packInt(a.getNano)
          }
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
        }

        private[this] val offsetDateTimeCodec = new MessagePackBinaryCodec[java.time.OffsetDateTime] {
          def encode(packer: MessagePacker, a: java.time.OffsetDateTime): Unit = {
            packer.packArrayHeader(8)
            packer.packInt(a.getYear)
            packer.packInt(a.getMonthValue)
            packer.packInt(a.getDayOfMonth)
            packer.packInt(a.getHour)
            packer.packInt(a.getMinute)
            packer.packInt(a.getSecond)
            packer.packInt(a.getNano)
            packer.packInt(a.getOffset.getTotalSeconds)
          }
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
        }

        private[this] val zonedDateTimeCodec = new MessagePackBinaryCodec[java.time.ZonedDateTime] {
          def encode(packer: MessagePacker, a: java.time.ZonedDateTime): Unit = {
            packer.packArrayHeader(8)
            packer.packInt(a.getYear)
            packer.packInt(a.getMonthValue)
            packer.packInt(a.getDayOfMonth)
            packer.packInt(a.getHour)
            packer.packInt(a.getMinute)
            packer.packInt(a.getSecond)
            packer.packInt(a.getNano)
            packer.packString(a.getZone.getId)
          }
          def decodeUnsafe(unpacker: MessageUnpacker): java.time.ZonedDateTime = {
            unpacker.unpackArrayHeader()
            java.time.ZonedDateTime.of(
              unpacker.unpackInt(),
              unpacker.unpackInt(),
              unpacker.unpackInt(),
              unpacker.unpackInt(),
              unpacker.unpackInt(),
              unpacker.unpackInt(),
              unpacker.unpackInt(),
              java.time.ZoneId.of(unpacker.unpackString())
            )
          }
        }

        private[this] val uuidCodec = new MessagePackBinaryCodec[java.util.UUID] {
          def encode(packer: MessagePacker, a: java.util.UUID): Unit = {
            packer.packExtensionTypeHeader(1.toByte, 16)
            val buf = ByteBuffer.allocate(16)
            buf.putLong(a.getMostSignificantBits)
            buf.putLong(a.getLeastSignificantBits)
            packer.writePayload(buf.array())
          }
          def decodeUnsafe(unpacker: MessageUnpacker): java.util.UUID = {
            val header = unpacker.unpackExtensionTypeHeader()
            val data   = new Array[Byte](header.getLength)
            unpacker.readPayload(data)
            val buf = ByteBuffer.wrap(data)
            new java.util.UUID(buf.getLong, buf.getLong)
          }
        }

        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): MessagePackBinaryCodec[A] = {
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
                case _: PrimitiveType.Instant        => instantCodec
                case _: PrimitiveType.LocalDate      => localDateCodec
                case _: PrimitiveType.LocalTime      => localTimeCodec
                case _: PrimitiveType.LocalDateTime  => localDateTimeCodec
                case _: PrimitiveType.OffsetDateTime => offsetDateTimeCodec
                case _: PrimitiveType.ZonedDateTime  => zonedDateTimeCodec
                case _: PrimitiveType.UUID           => uuidCodec
                case _                               => stringCodec // Fallback for other types
              }).asInstanceOf[MessagePackBinaryCodec[A]]
            } else
              primitive.primitiveBinding
                .asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]]
                .instance
                .force
                .asInstanceOf[MessagePackBinaryCodec[A]]
          } else if (reflect.isVariant) {
            val variant = reflect.asVariant.get
            if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
              val cases   = variant.cases
              val len     = cases.length
              val codecs  = new Array[MessagePackBinaryCodec[?]](len)
              val labels  = new Array[String](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                labels(idx) = cases(idx).name
                idx += 1
              }
              new MessagePackBinaryCodec[A] {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs
                private[this] val caseLabels    = labels

                def encode(packer: MessagePacker, value: A): Unit = {
                  val caseIdx = discriminator.discriminate(value)
                  packer.packMapHeader(1)
                  packer.packString(caseLabels(caseIdx))
                  caseCodecs(caseIdx).asInstanceOf[MessagePackBinaryCodec[A]].encode(packer, value)
                }

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  unpacker.unpackMapHeader()
                  val caseName = unpacker.unpackString()
                  var caseIdx  = -1
                  var i        = 0
                  while (i < caseLabels.length && caseIdx < 0) {
                    if (caseLabels(i) == caseName) caseIdx = i
                    i += 1
                  }
                  if (caseIdx < 0) throw new RuntimeException(s"Unknown case: $caseName")
                  caseCodecs(caseIdx).asInstanceOf[MessagePackBinaryCodec[A]].decodeUnsafe(unpacker)
                }
              }
            } else
              variant.variantBinding
                .asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]]
                .instance
                .force
                .asInstanceOf[MessagePackBinaryCodec[A]]
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val codec   = deriveCodec(sequence.element).asInstanceOf[MessagePackBinaryCodec[Elem]]
              new MessagePackBinaryCodec[Col[Elem]] {
                private[this] val constructor   = binding.constructor
                private[this] val deconstructor = binding.deconstructor
                private[this] val elementCodec  = codec

                def encode(packer: MessagePacker, value: Col[Elem]): Unit = {
                  val size = deconstructor.size(value)
                  packer.packArrayHeader(size)
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) elementCodec.encode(packer, it.next())
                }

                def decodeUnsafe(unpacker: MessageUnpacker): Col[Elem] = {
                  val len     = unpacker.unpackArrayHeader()
                  val builder = constructor.newObjectBuilder[Elem](len)
                  var i       = 0
                  while (i < len) {
                    constructor.addObject(builder, elementCodec.decodeUnsafe(unpacker))
                    i += 1
                  }
                  constructor.resultObject(builder)
                }
              }.asInstanceOf[MessagePackBinaryCodec[A]]
            } else
              sequence.seqBinding
                .asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]]
                .instance
                .force
                .asInstanceOf[MessagePackBinaryCodec[A]]
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding    = map.mapBinding.asInstanceOf[Binding.Map[MapType, Key, Value]]
              val keyCodec   = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[Key]]
              val valueCodec = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[Value]]
              new MessagePackBinaryCodec[MapType[Key, Value]] {
                private[this] val constructor   = binding.constructor
                private[this] val deconstructor = binding.deconstructor

                def encode(packer: MessagePacker, value: MapType[Key, Value]): Unit = {
                  val size = deconstructor.size(value)
                  packer.packMapHeader(size)
                  val it = deconstructor.deconstruct(value)
                  while (it.hasNext) {
                    val kv = it.next()
                    keyCodec.encode(packer, deconstructor.getKey(kv))
                    valueCodec.encode(packer, deconstructor.getValue(kv))
                  }
                }

                def decodeUnsafe(unpacker: MessageUnpacker): MapType[Key, Value] = {
                  val len     = unpacker.unpackMapHeader()
                  val builder = constructor.newObjectBuilder[Key, Value](len)
                  var i       = 0
                  while (i < len) {
                    val key   = keyCodec.decodeUnsafe(unpacker)
                    val value = valueCodec.decodeUnsafe(unpacker)
                    constructor.addObject(builder, key, value)
                    i += 1
                  }
                  constructor.resultObject(builder)
                }
              }.asInstanceOf[MessagePackBinaryCodec[A]]
            } else
              map.mapBinding
                .asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]]
                .instance
                .force
                .asInstanceOf[MessagePackBinaryCodec[A]]
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val binding   = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields    = record.fields
              val len       = fields.length
              val codecs    = new Array[MessagePackBinaryCodec[?]](len)
              val labels    = new Array[String](len)
              val registers = record.registers
              var idx       = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(fields(idx).value)
                labels(idx) = fields(idx).name
                idx += 1
              }
              new MessagePackBinaryCodec[A] {
                private[this] val constructor   = binding.constructor
                private[this] val deconstructor = binding.deconstructor
                private[this] val fieldCodecs   = codecs
                private[this] val fieldLabels   = labels
                private[this] val fieldRegs     = registers

                def encode(packer: MessagePacker, value: A): Unit = {
                  val regs = zio.blocks.schema.binding.Registers(deconstructor.usedRegisters)
                  deconstructor.deconstruct(regs, 0, value)
                  packer.packMapHeader(fieldLabels.length)
                  var i = 0
                  while (i < fieldLabels.length) {
                    packer.packString(fieldLabels(i))
                    val fieldValue = fieldRegs(i).get(regs, 0)
                    fieldCodecs(i).asInstanceOf[MessagePackBinaryCodec[Any]].encode(packer, fieldValue)
                    i += 1
                  }
                }

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val mapLen = unpacker.unpackMapHeader()
                  val regs   = zio.blocks.schema.binding.Registers(constructor.usedRegisters)
                  var read   = 0
                  while (read < mapLen) {
                    val fieldName = unpacker.unpackString()
                    var fieldIdx  = -1
                    var i         = 0
                    while (i < fieldLabels.length && fieldIdx < 0) {
                      if (fieldLabels(i) == fieldName) fieldIdx = i
                      i += 1
                    }
                    if (fieldIdx >= 0) {
                      val value = fieldCodecs(fieldIdx).asInstanceOf[MessagePackBinaryCodec[Any]].decodeUnsafe(unpacker)
                      fieldRegs(fieldIdx).asInstanceOf[zio.blocks.schema.binding.Register[Any]].set(regs, 0, value)
                    } else {
                      unpacker.skipValue() // Skip unknown fields
                    }
                    read += 1
                  }
                  constructor.construct(regs, 0)
                }
              }
            } else
              record.recordBinding
                .asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]]
                .instance
                .force
                .asInstanceOf[MessagePackBinaryCodec[A]]
          } else if (reflect.isDynamic) {
            dynamicCodec.asInstanceOf[MessagePackBinaryCodec[A]]
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding      = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val wrappedCodec = deriveCodec(wrapper.wrapped).asInstanceOf[MessagePackBinaryCodec[Wrapped]]
              new MessagePackBinaryCodec[A] {
                def encode(packer: MessagePacker, a: A): Unit =
                  wrappedCodec.encode(packer, binding.unwrap(a))

                def decodeUnsafe(unpacker: MessageUnpacker): A = {
                  val wrapped = wrappedCodec.decodeUnsafe(unpacker)
                  binding.wrap(wrapped) match {
                    case Right(a)  => a
                    case Left(err) => throw new RuntimeException(s"Failed to wrap value: $err")
                  }
                }
              }
            } else
              wrapper.wrapperBinding
                .asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]]
                .instance
                .force
                .asInstanceOf[MessagePackBinaryCodec[A]]
          } else {
            throw new UnsupportedOperationException(s"Cannot derive MessagePackBinaryCodec for ${reflect.typeName}")
          }
        }

        private[this] val dynamicCodec = new MessagePackBinaryCodec[DynamicValue] {
          def encode(packer: MessagePacker, dv: DynamicValue): Unit = dv match {
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
                case PrimitiveValue.Char(v)    => packer.packString(v.toString)
                case PrimitiveValue.String(v)  => packer.packString(v)
                case _                         => packer.packNil()
              }
            case DynamicValue.Record(fields) =>
              packer.packMapHeader(fields.size)
              fields.foreach { case (k, v) =>
                packer.packString(k)
                encode(packer, v)
              }
            case DynamicValue.Variant(caseLabel, value) =>
              packer.packMapHeader(1)
              packer.packString(caseLabel)
              encode(packer, value)
            case DynamicValue.Sequence(elements) =>
              packer.packArrayHeader(elements.length)
              elements.foreach(encode(packer, _))
            case _ => packer.packNil()
          }

          def decodeUnsafe(unpacker: MessageUnpacker): DynamicValue = {
            val format = unpacker.getNextFormat
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
              case ValueType.ARRAY =>
                val len      = unpacker.unpackArrayHeader()
                val elements = mutable.ArrayBuffer[DynamicValue]()
                var i        = 0
                while (i < len) {
                  elements += decodeUnsafe(unpacker)
                  i += 1
                }
                DynamicValue.Sequence(elements.toVector)
              case ValueType.MAP =>
                val len    = unpacker.unpackMapHeader()
                val fields = mutable.ListBuffer[(String, DynamicValue)]()
                var i      = 0
                while (i < len) {
                  val key   = unpacker.unpackString()
                  val value = decodeUnsafe(unpacker)
                  fields += ((key, value))
                  i += 1
                }
                DynamicValue.Record(fields.toVector)
              case _ =>
                unpacker.skipValue()
                DynamicValue.Primitive(PrimitiveValue.Unit)
            }
          }
        }
      }
    )
