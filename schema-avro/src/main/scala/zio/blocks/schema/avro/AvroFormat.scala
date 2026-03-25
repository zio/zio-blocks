/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
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

package zio.blocks.schema.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.docs.Doc
import zio.blocks.schema.binding.{Binding, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema.binding.SeqDeconstructor._
import zio.blocks.schema._
import zio.blocks.chunk.ChunkBuilder
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.TypeId
import scala.annotation.switch
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object AvroFormat
    extends BinaryFormat(
      "application/avro",
      new Deriver[AvroCodec] {
        override def derivePrimitive[A](
          primitiveType: PrimitiveType[A],
          typeId: TypeId[A],
          binding: Binding.Primitive[A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        ): Lazy[AvroCodec[A]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            Lazy(primitiveType match {
              case _: PrimitiveType.Unit.type      => AvroCodec.unitCodec
              case _: PrimitiveType.Boolean        => AvroCodec.booleanCodec
              case _: PrimitiveType.Byte           => AvroCodec.byteCodec
              case _: PrimitiveType.Short          => AvroCodec.shortCodec
              case _: PrimitiveType.Int            => AvroCodec.intCodec
              case _: PrimitiveType.Long           => AvroCodec.longCodec
              case _: PrimitiveType.Float          => AvroCodec.floatCodec
              case _: PrimitiveType.Double         => AvroCodec.doubleCodec
              case _: PrimitiveType.Char           => AvroCodec.charCodec
              case _: PrimitiveType.String         => AvroCodec.stringCodec
              case _: PrimitiveType.BigInt         => AvroCodec.bigIntCodec
              case _: PrimitiveType.BigDecimal     => AvroCodec.bigDecimalCodec
              case _: PrimitiveType.DayOfWeek      => AvroCodec.dayOfWeekCodec
              case _: PrimitiveType.Duration       => AvroCodec.durationCodec
              case _: PrimitiveType.Instant        => AvroCodec.instantCodec
              case _: PrimitiveType.LocalDate      => AvroCodec.localDateCodec
              case _: PrimitiveType.LocalDateTime  => AvroCodec.localDateTimeCodec
              case _: PrimitiveType.LocalTime      => AvroCodec.localTimeCodec
              case _: PrimitiveType.Month          => AvroCodec.monthCodec
              case _: PrimitiveType.MonthDay       => AvroCodec.monthDayCodec
              case _: PrimitiveType.OffsetDateTime => AvroCodec.offsetDateTimeCodec
              case _: PrimitiveType.OffsetTime     => AvroCodec.offsetTimeCodec
              case _: PrimitiveType.Period         => AvroCodec.periodCodec
              case _: PrimitiveType.Year           => AvroCodec.yearCodec
              case _: PrimitiveType.YearMonth      => AvroCodec.yearMonthCodec
              case _: PrimitiveType.ZoneId         => AvroCodec.zoneIdCodec
              case _: PrimitiveType.ZoneOffset     => AvroCodec.zoneOffsetCodec
              case _: PrimitiveType.ZonedDateTime  => AvroCodec.zonedDateTimeCodec
              case _: PrimitiveType.Currency       => AvroCodec.currencyCodec
              case _: PrimitiveType.UUID           => AvroCodec.uuidCodec
            })
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[AvroCodec[A]]]

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeId: TypeId[A],
          binding: Binding.Record[A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroCodec[A]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val recordBinding            = binding.asInstanceOf[Binding.Record[A]]
            val isRecursive              = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
            var fieldInfosWithAvroSchema =
              if (isRecursive) recursiveRecordCache.get.get(typeId)
              else null
            if (fieldInfosWithAvroSchema eq null) {
              var offset     = 0L
              val namespace  = typeId.owner.asString
              val avroSchema = createAvroRecord(namespace, typeId.name)
              val len        = fields.length
              val fieldInfos = new Array[FieldInfo](len)
              fieldInfosWithAvroSchema = (fieldInfos, avroSchema)
              if (isRecursive) recursiveRecordCache.get.put(typeId, fieldInfosWithAvroSchema)
              val avroSchemaFields = new java.util.ArrayList[AvroSchema.Field](len)
              var idx              = 0
              while (idx < len) {
                val field        = fields(idx)
                val fieldReflect = field.value
                val codec        = D.instance(fieldReflect.metadata).force.asInstanceOf[AvroCodec[?]]
                fieldInfos(idx) = new FieldInfo(codec, Reflect.typeTag(fieldReflect), offset)
                avroSchemaFields.add(new AvroSchema.Field(field.name, codec.avroSchema))
                offset = RegisterOffset.add(Reflect.registerOffset(fieldReflect), offset)
                idx += 1
              }
              avroSchema.setFields(avroSchemaFields)
            }
            Lazy(new AvroCodec[A]() {
              private[this] val deconstructor = recordBinding.deconstructor
              private[this] val constructor   = recordBinding.constructor
              private[this] val usedRegisters = constructor.usedRegisters
              private[this] val fieldInfos    = fieldInfosWithAvroSchema._1

              val avroSchema: AvroSchema = fieldInfosWithAvroSchema._2

              def decodeValue(decoder: BinaryDecoder): A = {
                val regs = Registers(usedRegisters)
                val len  = fieldInfos.length
                var idx  = 0
                try {
                  while (idx < len) {
                    val fieldInfo = fieldInfos(idx)
                    val codec     = fieldInfo.codec
                    val offset    = fieldInfo.offset
                    (fieldInfo.typeTag: @switch) match {
                      case 0 => regs.setObject(offset, codec.asInstanceOf[AvroCodec[AnyRef]].decodeValue(decoder))
                      case 1 => regs.setInt(offset, codec.asInstanceOf[AvroCodec[Int]].decodeValue(decoder))
                      case 2 => regs.setLong(offset, codec.asInstanceOf[AvroCodec[Long]].decodeValue(decoder))
                      case 3 => regs.setFloat(offset, codec.asInstanceOf[AvroCodec[Float]].decodeValue(decoder))
                      case 4 => regs.setDouble(offset, codec.asInstanceOf[AvroCodec[Double]].decodeValue(decoder))
                      case 5 => regs.setBoolean(offset, codec.asInstanceOf[AvroCodec[Boolean]].decodeValue(decoder))
                      case 6 => regs.setByte(offset, codec.asInstanceOf[AvroCodec[Byte]].decodeValue(decoder))
                      case 7 => regs.setChar(offset, codec.asInstanceOf[AvroCodec[Char]].decodeValue(decoder))
                      case 8 => regs.setShort(offset, codec.asInstanceOf[AvroCodec[Short]].decodeValue(decoder))
                      case _ => codec.asInstanceOf[AvroCodec[Unit]].decodeValue(decoder)
                    }
                    idx += 1
                  }
                  constructor.construct(regs, 0L)
                } catch {
                  case err if NonFatal(err) => error(new DynamicOptic.Node.Field(fields(idx).name), err)
                }
              }

              def encodeValue(value: A, encoder: BinaryEncoder): Unit = {
                val regs = Registers(usedRegisters)
                deconstructor.deconstruct(regs, 0L, value)
                val len = fieldInfos.length
                var idx = 0
                while (idx < len) {
                  val fieldInfo = fieldInfos(idx)
                  val codec     = fieldInfo.codec
                  val offset    = fieldInfo.offset
                  (fieldInfo.typeTag: @switch) match {
                    case 0 => codec.asInstanceOf[AvroCodec[AnyRef]].encodeValue(regs.getObject(offset), encoder)
                    case 1 => codec.asInstanceOf[AvroCodec[Int]].encodeValue(regs.getInt(offset), encoder)
                    case 2 => codec.asInstanceOf[AvroCodec[Long]].encodeValue(regs.getLong(offset), encoder)
                    case 3 => codec.asInstanceOf[AvroCodec[Float]].encodeValue(regs.getFloat(offset), encoder)
                    case 4 => codec.asInstanceOf[AvroCodec[Double]].encodeValue(regs.getDouble(offset), encoder)
                    case 5 => codec.asInstanceOf[AvroCodec[Boolean]].encodeValue(regs.getBoolean(offset), encoder)
                    case 6 => codec.asInstanceOf[AvroCodec[Byte]].encodeValue(regs.getByte(offset), encoder)
                    case 7 => codec.asInstanceOf[AvroCodec[Char]].encodeValue(regs.getChar(offset), encoder)
                    case 8 => codec.asInstanceOf[AvroCodec[Short]].encodeValue(regs.getShort(offset), encoder)
                    case _ => codec.asInstanceOf[AvroCodec[Unit]].encodeValue((), encoder)
                  }
                  idx += 1
                }
              }
            })
          } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
        }.asInstanceOf[Lazy[AvroCodec[A]]]

        override def deriveVariant[F[_, _], A](
          cases: IndexedSeq[Term[F, A, ?]],
          typeId: TypeId[A],
          binding: Binding.Variant[A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroCodec[A]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
            val len            = cases.length
            val codecs         = new Array[AvroCodec[?]](len)
            var idx            = 0
            while (idx < len) {
              codecs(idx) = D.instance(cases(idx).value.metadata).force.asInstanceOf[AvroCodec[A]]
              idx += 1
            }
            Lazy(new AvroCodec[A]() {
              private[this] val discriminator = variantBinding.discriminator
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

              def decodeValue(decoder: BinaryDecoder): A = {
                val idx = decoder.readInt()
                if (idx >= 0 && idx < caseCodecs.length) {
                  try caseCodecs(idx).asInstanceOf[AvroCodec[A]].decodeValue(decoder)
                  catch {
                    case err if NonFatal(err) => error(new DynamicOptic.Node.Case(cases(idx).name), err)
                  }
                } else error(s"Expected enum index from 0 to ${caseCodecs.length - 1}, got $idx")
              }

              def encodeValue(value: A, encoder: BinaryEncoder): Unit = {
                val idx = discriminator.discriminate(value)
                encoder.writeInt(idx)
                caseCodecs(idx).asInstanceOf[AvroCodec[A]].encodeValue(value, encoder)
              }
            })
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[AvroCodec[A]]]

        override def deriveSequence[F[_, _], C[_], A](
          element: Reflect[F, A],
          typeId: TypeId[C[A]],
          binding: Binding.Seq[C, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[C[A]],
          examples: Seq[C[A]]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroCodec[C[A]]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
            D.instance(element.metadata).map { codec =>
              Reflect.typeTag(element) match {
                case 5 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroCodec[Col[Boolean]]() {
                    private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Boolean]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Boolean] = {
                      val builder = constructor.newBuilder[Boolean](8)(ClassTag.Boolean)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addBoolean(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Boolean], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case 6 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroCodec[Col[Byte]]() {
                    private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Byte]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Byte] = {
                      val builder = constructor.newBuilder[Byte](8)(ClassTag.Byte)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addByte(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Byte], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case 7 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroCodec[Col[Char]]() {
                    private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Char]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Char] = {
                      val builder = constructor.newBuilder[Char](8)(ClassTag.Char)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addChar(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Char], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case 8 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroCodec[Col[Short]]() {
                    private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Short]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Short] = {
                      val builder = constructor.newBuilder[Short](8)(ClassTag.Short)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addShort(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Short], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case 3 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroCodec[Col[Float]]() {
                    private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Float]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Float] = {
                      val builder = constructor.newBuilder[Float](8)(ClassTag.Float)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addFloat(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Float], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case 1 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroCodec[Col[Int]]() {
                    private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Int]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Int] = {
                      val builder = constructor.newBuilder[Int](8)(ClassTag.Int)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addInt(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Int], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case 4 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroCodec[Col[Double]]() {
                    private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Double]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Double] = {
                      val builder = constructor.newBuilder[Double](8)(ClassTag.Double)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addDouble(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Double], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case 2 if seqBinding.deconstructor.isInstanceOf[SpecializedIndexed[Col]] =>
                  new AvroCodec[Col[Long]]() {
                    private[this] val deconstructor = seqBinding.deconstructor.asInstanceOf[SpecializedIndexed[Col]]
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Long]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Long] = {
                      val builder = constructor.newBuilder[Long](8)(ClassTag.Long)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.addLong(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Long], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
                case _ =>
                  new AvroCodec[Col[Elem]]() {
                    private[this] val deconstructor = seqBinding.deconstructor
                    private[this] val constructor   = seqBinding.constructor
                    private[this] val elementCodec  = codec.asInstanceOf[AvroCodec[Elem]]
                    private[this] val elemClassTag  = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

                    val avroSchema: AvroSchema = AvroSchema.createArray(elementCodec.avroSchema)

                    def decodeValue(decoder: BinaryDecoder): Col[Elem] = {
                      val builder = constructor.newBuilder[Elem](8)(elemClassTag)
                      var count   = 0L
                      var size    = 0
                      while ({
                        size = decoder.readInt()
                        size > 0
                      }) {
                        if (count + size > AvroCodec.maxCollectionSize) {
                          error(
                            s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                          )
                        }
                        try {
                          while (size > 0) {
                            constructor.add(builder, elementCodec.decodeValue(decoder))
                            count += 1
                            size -= 1
                          }
                        } catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      }
                      if (size < 0) error(s"Expected positive collection part size, got $size")
                      constructor.result(builder)
                    }

                    def encodeValue(value: Col[Elem], encoder: BinaryEncoder): Unit = {
                      val size = deconstructor.size(value)
                      if (size > 0) {
                        encoder.writeInt(size)
                        val it = deconstructor.deconstruct(value)
                        while (it.hasNext) elementCodec.encodeValue(it.next(), encoder)
                      }
                      encoder.writeInt(0)
                    }
                  }
              }
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[AvroCodec[C[A]]]]

        override def deriveMap[F[_, _], M[_, _], K, V](
          key: Reflect[F, K],
          value: Reflect[F, V],
          typeId: TypeId[M[K, V]],
          binding: Binding.Map[M, K, V],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[M[K, V]],
          examples: Seq[M[K, V]]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroCodec[M[K, V]]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
            D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
              new AvroCodec[Map[Key, Value]]() {
                private[this] val deconstructor = mapBinding.deconstructor
                private[this] val constructor   = mapBinding.constructor
                private[this] val keyCodec      = codec1.asInstanceOf[AvroCodec[Key]]
                private[this] val valueCodec    = codec2.asInstanceOf[AvroCodec[Value]]
                private[this] val keyReflect    = key.asInstanceOf[Reflect.Bound[Key]]

                val avroSchema: AvroSchema = key.asPrimitive match {
                  case Some(primitiveKey) if primitiveKey.primitiveType.isInstanceOf[PrimitiveType.String] =>
                    AvroSchema.createMap(valueCodec.avroSchema)
                  case _ =>
                    val fields = new java.util.ArrayList[AvroSchema.Field](2)
                    fields.add(new AvroSchema.Field("_1", keyCodec.avroSchema))
                    fields.add(new AvroSchema.Field("_2", valueCodec.avroSchema))
                    AvroSchema.createArray(createAvroRecord("scala", "Tuple2", fields))
                }

                def decodeValue(decoder: BinaryDecoder): Map[Key, Value] = {
                  val builder = constructor.newObjectBuilder[Key, Value](8)
                  var count   = 0L
                  var size    = 0
                  while ({
                    size = decoder.readInt()
                    size > 0
                  }) {
                    if (count + size > AvroCodec.maxCollectionSize) {
                      error(s"Expected map size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}")
                    }
                    while (size > 0) {
                      val k =
                        try keyCodec.decodeValue(decoder)
                        catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                        }
                      val v =
                        try valueCodec.decodeValue(decoder)
                        catch {
                          case err if NonFatal(err) =>
                            error(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), err)
                        }
                      constructor.addObject(builder, k, v)
                      count += 1
                      size -= 1
                    }
                  }
                  if (size < 0) error(s"Expected positive map part size, got $size")
                  constructor.resultObject[Key, Value](builder)
                }

                def encodeValue(value: Map[Key, Value], encoder: BinaryEncoder): Unit = {
                  val size = deconstructor.size(value)
                  if (size > 0) {
                    encoder.writeInt(size)
                    val it = deconstructor.deconstruct(value)
                    while (it.hasNext) {
                      val kv = it.next()
                      keyCodec.encodeValue(deconstructor.getKey(kv), encoder)
                      valueCodec.encodeValue(deconstructor.getValue(kv), encoder)
                    }
                  }
                  encoder.writeInt(0)
                }
              }
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
        }.asInstanceOf[Lazy[AvroCodec[M[K, V]]]]

        override def deriveDynamic[F[_, _]](
          binding: Binding.Dynamic,
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[DynamicValue],
          examples: Seq[DynamicValue]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroCodec[DynamicValue]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
          else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
        }.asInstanceOf[Lazy[AvroCodec[DynamicValue]]]

        def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeId: TypeId[A],
          binding: Binding.Wrapper[A, B],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[AvroCodec[A]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
            D.instance(wrapped.metadata).map { codec =>
              new AvroCodec[A] {
                private[this] val wrap         = wrapperBinding.wrap
                private[this] val unwrap       = wrapperBinding.unwrap
                private[this] val wrappedCodec = codec.asInstanceOf[AvroCodec[Wrapped]]

                val avroSchema: AvroSchema = wrappedCodec.avroSchema

                def decodeValue(decoder: BinaryDecoder): A =
                  try wrap(wrappedCodec.decodeValue(decoder))
                  catch {
                    case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
                  }

                def encodeValue(value: A, encoder: BinaryEncoder): Unit =
                  wrappedCodec.encodeValue(unwrap(value), encoder)
              }
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[AvroCodec[A]]]

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
          new ThreadLocal[java.util.HashMap[TypeId[?], (Array[FieldInfo], AvroSchema)]] {
            override def initialValue: java.util.HashMap[TypeId[?], (Array[FieldInfo], AvroSchema)] =
              new java.util.HashMap
          }
        private[this] val recordCounters =
          new ThreadLocal[java.util.HashMap[(String, String), Int]] {
            override def initialValue: java.util.HashMap[(String, String), Int] = new java.util.HashMap
          }

        private[this] val dynamicValueCodec = new AvroCodec[DynamicValue]() {
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
                          createPrimitiveValueAvroRecord("Boolean", AvroCodec.booleanCodec),
                          createPrimitiveValueAvroRecord("Byte", AvroCodec.byteCodec),
                          createPrimitiveValueAvroRecord("Short", AvroCodec.shortCodec),
                          createPrimitiveValueAvroRecord("Int", AvroCodec.intCodec),
                          createPrimitiveValueAvroRecord("Long", AvroCodec.longCodec),
                          createPrimitiveValueAvroRecord("Float", AvroCodec.floatCodec),
                          createPrimitiveValueAvroRecord("Double", AvroCodec.doubleCodec),
                          createPrimitiveValueAvroRecord("Char", AvroCodec.charCodec),
                          createPrimitiveValueAvroRecord("String", AvroCodec.stringCodec),
                          createPrimitiveValueAvroRecord("BigInt", AvroCodec.bigIntCodec),
                          createPrimitiveValueAvroRecord("BigDecimal", AvroCodec.bigDecimalCodec),
                          createPrimitiveValueAvroRecord("DayOfWeek", AvroCodec.dayOfWeekCodec),
                          createPrimitiveValueAvroRecord("Duration", AvroCodec.durationCodec),
                          createPrimitiveValueAvroRecord("Instant", AvroCodec.instantCodec),
                          createPrimitiveValueAvroRecord("LocalDate", AvroCodec.localDateCodec),
                          createPrimitiveValueAvroRecord("LocalDateTime", AvroCodec.localDateTimeCodec),
                          createPrimitiveValueAvroRecord("LocalTime", AvroCodec.localTimeCodec),
                          createPrimitiveValueAvroRecord("Month", AvroCodec.monthCodec),
                          createPrimitiveValueAvroRecord("MonthDay", AvroCodec.monthDayCodec),
                          createPrimitiveValueAvroRecord("OffsetDateTime", AvroCodec.offsetDateTimeCodec),
                          createPrimitiveValueAvroRecord("OffsetTime", AvroCodec.offsetTimeCodec),
                          createPrimitiveValueAvroRecord("Period", AvroCodec.periodCodec),
                          createPrimitiveValueAvroRecord("Year", AvroCodec.yearCodec),
                          createPrimitiveValueAvroRecord("YearMonth", AvroCodec.yearMonthCodec),
                          createPrimitiveValueAvroRecord("ZoneId", AvroCodec.zoneIdCodec),
                          createPrimitiveValueAvroRecord("ZoneOffset", AvroCodec.zoneOffsetCodec),
                          createPrimitiveValueAvroRecord("ZonedDateTime", AvroCodec.zonedDateTimeCodec),
                          createPrimitiveValueAvroRecord("Currency", AvroCodec.currencyCodec),
                          createPrimitiveValueAvroRecord("UUID", AvroCodec.uuidCodec)
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
                          fieldFields.add(new AvroSchema.Field("name", AvroCodec.stringCodec.avroSchema))
                          fieldFields.add(new AvroSchema.Field("value", dynamicValue))
                          createAvroRecord("zio.blocks.schema.internal", "Field", fieldFields)
                        }
                      )
                    )
                    createAvroRecord("zio.blocks.schema.DynamicValue", "Record", recordFields)
                  }, {
                    val variantFields = new java.util.ArrayList[AvroSchema.Field](2)
                    variantFields.add(new AvroSchema.Field("caseName", AvroCodec.stringCodec.avroSchema))
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

          def decodeValue(decoder: BinaryDecoder): DynamicValue = decoder.readInt() match {
            case 0 =>
              try {
                val idx = decoder.readInt()
                if (idx < 0 || idx > 29) error(s"Expected enum index from 0 to 29, got $idx")
                try {
                  new DynamicValue.Primitive((idx: @scala.annotation.switch) match {
                    case 0  => PrimitiveValue.Unit
                    case 1  => new PrimitiveValue.Boolean(AvroCodec.booleanCodec.decodeValue(decoder))
                    case 2  => new PrimitiveValue.Byte(AvroCodec.byteCodec.decodeValue(decoder))
                    case 3  => new PrimitiveValue.Short(AvroCodec.shortCodec.decodeValue(decoder))
                    case 4  => new PrimitiveValue.Int(AvroCodec.intCodec.decodeValue(decoder))
                    case 5  => new PrimitiveValue.Long(AvroCodec.longCodec.decodeValue(decoder))
                    case 6  => new PrimitiveValue.Float(AvroCodec.floatCodec.decodeValue(decoder))
                    case 7  => new PrimitiveValue.Double(AvroCodec.doubleCodec.decodeValue(decoder))
                    case 8  => new PrimitiveValue.Char(AvroCodec.charCodec.decodeValue(decoder))
                    case 9  => new PrimitiveValue.String(AvroCodec.stringCodec.decodeValue(decoder))
                    case 10 => new PrimitiveValue.BigInt(AvroCodec.bigIntCodec.decodeValue(decoder))
                    case 11 => new PrimitiveValue.BigDecimal(AvroCodec.bigDecimalCodec.decodeValue(decoder))
                    case 12 => new PrimitiveValue.DayOfWeek(AvroCodec.dayOfWeekCodec.decodeValue(decoder))
                    case 13 => new PrimitiveValue.Duration(AvroCodec.durationCodec.decodeValue(decoder))
                    case 14 => new PrimitiveValue.Instant(AvroCodec.instantCodec.decodeValue(decoder))
                    case 15 => new PrimitiveValue.LocalDate(AvroCodec.localDateCodec.decodeValue(decoder))
                    case 16 =>
                      new PrimitiveValue.LocalDateTime(AvroCodec.localDateTimeCodec.decodeValue(decoder))
                    case 17 => new PrimitiveValue.LocalTime(AvroCodec.localTimeCodec.decodeValue(decoder))
                    case 18 => new PrimitiveValue.Month(AvroCodec.monthCodec.decodeValue(decoder))
                    case 19 => new PrimitiveValue.MonthDay(AvroCodec.monthDayCodec.decodeValue(decoder))
                    case 20 =>
                      new PrimitiveValue.OffsetDateTime(AvroCodec.offsetDateTimeCodec.decodeValue(decoder))
                    case 21 => new PrimitiveValue.OffsetTime(AvroCodec.offsetTimeCodec.decodeValue(decoder))
                    case 22 => new PrimitiveValue.Period(AvroCodec.periodCodec.decodeValue(decoder))
                    case 23 => new PrimitiveValue.Year(AvroCodec.yearCodec.decodeValue(decoder))
                    case 24 => new PrimitiveValue.YearMonth(AvroCodec.yearMonthCodec.decodeValue(decoder))
                    case 25 => new PrimitiveValue.ZoneId(AvroCodec.zoneIdCodec.decodeValue(decoder))
                    case 26 => new PrimitiveValue.ZoneOffset(AvroCodec.zoneOffsetCodec.decodeValue(decoder))
                    case 27 =>
                      new PrimitiveValue.ZonedDateTime(AvroCodec.zonedDateTimeCodec.decodeValue(decoder))
                    case 28 => new PrimitiveValue.Currency(AvroCodec.currencyCodec.decodeValue(decoder))
                    case _  => new PrimitiveValue.UUID(AvroCodec.uuidCodec.decodeValue(decoder))
                  })
                } catch {
                  case err if NonFatal(err) => error(spanValue, err)
                }
              } catch {
                case err if NonFatal(err) => error(spanPrimitive, err)
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
                  if (count + size > AvroCodec.maxCollectionSize) {
                    error(
                      s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    val k =
                      try decoder.readString()
                      catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), span_1, err)
                      }
                    val v =
                      try decodeValue(decoder)
                      catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), span_2, err)
                      }
                    builder.addOne((k, v))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) error(s"Expected positive collection part size, got $size")
                new DynamicValue.Record(builder.result())
              } catch {
                case err if NonFatal(err) => error(spanRecord, spanFields, err)
              }
            case 2 =>
              val caseName =
                try decoder.readString()
                catch {
                  case err if NonFatal(err) => error(spanVariant, spanCaseName, err)
                }
              val value =
                try decodeValue(decoder)
                catch {
                  case err if NonFatal(err) => error(spanVariant, spanValue, err)
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
                  if (count + size > AvroCodec.maxCollectionSize) {
                    error(
                      s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  try {
                    while (size > 0) {
                      builder.addOne(decodeValue(decoder))
                      count += 1
                      size -= 1
                    }
                  } catch {
                    case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), err)
                  }
                }
                if (size < 0) error(s"Expected positive collection part size, got $size")
                new DynamicValue.Sequence(builder.result())
              } catch {
                case err if NonFatal(err) => error(spanSequence, spanElements, err)
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
                  if (count + size > AvroCodec.maxCollectionSize) {
                    error(
                      s"Expected collection size not greater than ${AvroCodec.maxCollectionSize}, got ${count + size}"
                    )
                  }
                  while (size > 0) {
                    val k =
                      try decodeValue(decoder)
                      catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), span_1, err)
                      }
                    val v =
                      try decodeValue(decoder)
                      catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(count.toInt), span_2, err)
                      }
                    builder.addOne((k, v))
                    count += 1
                    size -= 1
                  }
                }
                if (size < 0) error(s"Expected positive collection part size, got $size")
                new DynamicValue.Map(builder.result())
              } catch {
                case err if NonFatal(err) => error(spanMap, spanEntries, err)
              }
            case 5 =>
              DynamicValue.Null
            case idx => error(s"Expected enum index from 0 to 5, got $idx")
          }

          def encodeValue(value: DynamicValue, encoder: BinaryEncoder): Unit = value match {
            case primitive: DynamicValue.Primitive =>
              encoder.writeInt(0)
              primitive.value match {
                case _: PrimitiveValue.Unit.type =>
                  encoder.writeInt(0)
                case v: PrimitiveValue.Boolean =>
                  encoder.writeInt(1)
                  AvroCodec.booleanCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Byte =>
                  encoder.writeInt(2)
                  AvroCodec.byteCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Short =>
                  encoder.writeInt(3)
                  AvroCodec.shortCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Int =>
                  encoder.writeInt(4)
                  AvroCodec.intCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Long =>
                  encoder.writeInt(5)
                  AvroCodec.longCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Float =>
                  encoder.writeInt(6)
                  AvroCodec.floatCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Double =>
                  encoder.writeInt(7)
                  AvroCodec.doubleCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Char =>
                  encoder.writeInt(8)
                  AvroCodec.charCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.String =>
                  encoder.writeInt(9)
                  AvroCodec.stringCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.BigInt =>
                  encoder.writeInt(10)
                  AvroCodec.bigIntCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.BigDecimal =>
                  encoder.writeInt(11)
                  AvroCodec.bigDecimalCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.DayOfWeek =>
                  encoder.writeInt(12)
                  AvroCodec.dayOfWeekCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Duration =>
                  encoder.writeInt(13)
                  AvroCodec.durationCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Instant =>
                  encoder.writeInt(14)
                  AvroCodec.instantCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.LocalDate =>
                  encoder.writeInt(15)
                  AvroCodec.localDateCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.LocalDateTime =>
                  encoder.writeInt(16)
                  AvroCodec.localDateTimeCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.LocalTime =>
                  encoder.writeInt(17)
                  AvroCodec.localTimeCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Month =>
                  encoder.writeInt(18)
                  AvroCodec.monthCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.MonthDay =>
                  encoder.writeInt(19)
                  AvroCodec.monthDayCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.OffsetDateTime =>
                  encoder.writeInt(20)
                  AvroCodec.offsetDateTimeCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.OffsetTime =>
                  encoder.writeInt(21)
                  AvroCodec.offsetTimeCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Period =>
                  encoder.writeInt(22)
                  AvroCodec.periodCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Year =>
                  encoder.writeInt(23)
                  AvroCodec.yearCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.YearMonth =>
                  encoder.writeInt(24)
                  AvroCodec.yearMonthCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.ZoneId =>
                  encoder.writeInt(25)
                  AvroCodec.zoneIdCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.ZoneOffset =>
                  encoder.writeInt(26)
                  AvroCodec.zoneOffsetCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.ZonedDateTime =>
                  encoder.writeInt(27)
                  AvroCodec.zonedDateTimeCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.Currency =>
                  encoder.writeInt(28)
                  AvroCodec.currencyCodec.encodeValue(v.value, encoder)
                case v: PrimitiveValue.UUID =>
                  encoder.writeInt(29)
                  AvroCodec.uuidCodec.encodeValue(v.value, encoder)
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
                  encodeValue(kv._2, encoder)
                }
              }
              encoder.writeInt(0)
            case variant: DynamicValue.Variant =>
              encoder.writeInt(2)
              encoder.writeString(variant.caseNameValue)
              encodeValue(variant.value, encoder)
            case sequence: DynamicValue.Sequence =>
              encoder.writeInt(3)
              val elements = sequence.elements
              val size     = elements.length
              if (size > 0) {
                encoder.writeInt(size)
                val it = elements.iterator
                while (it.hasNext) {
                  encodeValue(it.next(), encoder)
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
                  encodeValue(kv._1, encoder)
                  encodeValue(kv._2, encoder)
                }
              }
              encoder.writeInt(0)
            case DynamicValue.Null =>
              encoder.writeInt(5)
          }

          private[this] def createPrimitiveValueAvroRecord(name: String, codec: AvroCodec[?]): AvroSchema = {
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

private[avro] case class FieldInfo(
  codec: AvroCodec[?],
  typeTag: Int,
  offset: RegisterOffset.RegisterOffset = 0L
)
