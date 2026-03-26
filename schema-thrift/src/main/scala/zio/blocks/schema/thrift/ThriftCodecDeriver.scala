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

package zio.blocks.schema.thrift

import org.apache.thrift.protocol._
import scala.reflect.ClassTag
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.TypeId
import scala.util.control.NonFatal

object ThriftCodecDeriver extends Deriver[ThriftCodec] {
  // Existential type aliases for internal use
  type Elem
  type Key
  type Value
  type Wrapped
  type Col[_]
  type Map[_, _]
  type TC[_]

  // Deriver implementation
  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[ThriftCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      Lazy(primitiveType match {
        case _: PrimitiveType.Unit.type      => ThriftCodec.unitCodec
        case _: PrimitiveType.Boolean        => ThriftCodec.booleanCodec
        case _: PrimitiveType.Byte           => ThriftCodec.byteCodec
        case _: PrimitiveType.Short          => ThriftCodec.shortCodec
        case _: PrimitiveType.Int            => ThriftCodec.intCodec
        case _: PrimitiveType.Long           => ThriftCodec.longCodec
        case _: PrimitiveType.Float          => ThriftCodec.floatCodec
        case _: PrimitiveType.Double         => ThriftCodec.doubleCodec
        case _: PrimitiveType.Char           => ThriftCodec.charCodec
        case _: PrimitiveType.String         => ThriftCodec.stringCodec
        case _: PrimitiveType.BigInt         => ThriftCodec.bigIntCodec
        case _: PrimitiveType.BigDecimal     => ThriftCodec.bigDecimalCodec
        case _: PrimitiveType.DayOfWeek      => ThriftCodec.dayOfWeekCodec
        case _: PrimitiveType.Month          => ThriftCodec.monthCodec
        case _: PrimitiveType.Year           => ThriftCodec.yearCodec
        case _: PrimitiveType.MonthDay       => ThriftCodec.monthDayCodec
        case _: PrimitiveType.YearMonth      => ThriftCodec.yearMonthCodec
        case _: PrimitiveType.Period         => ThriftCodec.periodCodec
        case _: PrimitiveType.Duration       => ThriftCodec.durationCodec
        case _: PrimitiveType.Instant        => ThriftCodec.instantCodec
        case _: PrimitiveType.LocalDate      => ThriftCodec.localDateCodec
        case _: PrimitiveType.LocalTime      => ThriftCodec.localTimeCodec
        case _: PrimitiveType.LocalDateTime  => ThriftCodec.localDateTimeCodec
        case _: PrimitiveType.OffsetTime     => ThriftCodec.offsetTimeCodec
        case _: PrimitiveType.OffsetDateTime => ThriftCodec.offsetDateTimeCodec
        case _: PrimitiveType.ZonedDateTime  => ThriftCodec.zonedDateTimeCodec
        case _: PrimitiveType.ZoneId         => ThriftCodec.zoneIdCodec
        case _: PrimitiveType.ZoneOffset     => ThriftCodec.zoneOffsetCodec
        case _: PrimitiveType.Currency       => ThriftCodec.currencyCodec
        case _: PrimitiveType.UUID           => ThriftCodec.uuidCodec
      })
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ThriftCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val isRecursive   = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
      var fieldInfos    =
        if (isRecursive) recursiveRecordCache.get.get(typeId)
        else null
      if (fieldInfos eq null) {
        val len = fields.length
        fieldInfos = new Array[ThriftFieldInfo](len)
        if (isRecursive) recursiveRecordCache.get.put(typeId, fieldInfos)
        var idx    = 0
        var offset = 0L
        while (idx < len) {
          val field        = fields(idx)
          val fieldReflect = field.value
          fieldInfos(idx) = new ThriftFieldInfo(
            field.name,
            D.instance(fieldReflect.metadata).force,
            offset,
            Reflect.typeTag(fieldReflect),
            getThriftType(fieldReflect)
          )
          offset = RegisterOffset.add(Reflect.registerOffset(fieldReflect), offset)
          idx += 1
        }
      }
      new ThriftCodec[A]() {
        private[this] val fields        = fieldInfos
        private[this] val constructor   = recordBinding.constructor
        private[this] val deconstruct   = recordBinding.deconstructor
        private[this] val usedRegisters = constructor.usedRegisters

        def decodeValue(protocol: TProtocol): A = {
          val regs  = Registers(usedRegisters)
          var field = protocol.readFieldBegin()
          while (field.`type` != TType.STOP) {
            val fieldIdx = field.id - 1
            val len      = fields.length
            if (fieldIdx >= 0 && fieldIdx < len) {
              val fieldInfo = fields(fieldIdx)
              try {
                val codec  = fieldInfo.codec
                val offset = fieldInfo.offset
                fieldInfo.typeTag match {
                  case 0 => regs.setObject(offset, codec.asInstanceOf[ThriftCodec[AnyRef]].decodeValue(protocol))
                  case 1 => regs.setInt(offset, codec.asInstanceOf[ThriftCodec[Int]].decodeValue(protocol))
                  case 2 => regs.setLong(offset, codec.asInstanceOf[ThriftCodec[Long]].decodeValue(protocol))
                  case 3 => regs.setFloat(offset, codec.asInstanceOf[ThriftCodec[Float]].decodeValue(protocol))
                  case 4 => regs.setDouble(offset, codec.asInstanceOf[ThriftCodec[Double]].decodeValue(protocol))
                  case 5 => regs.setBoolean(offset, codec.asInstanceOf[ThriftCodec[Boolean]].decodeValue(protocol))
                  case 6 => regs.setByte(offset, codec.asInstanceOf[ThriftCodec[Byte]].decodeValue(protocol))
                  case 7 => regs.setChar(offset, codec.asInstanceOf[ThriftCodec[Char]].decodeValue(protocol))
                  case 8 => regs.setShort(offset, codec.asInstanceOf[ThriftCodec[Short]].decodeValue(protocol))
                  case _ => codec.asInstanceOf[ThriftCodec[Unit]].decodeValue(protocol)
                }
              } catch {
                case err if NonFatal(err) => error(new DynamicOptic.Node.Field(fieldInfo.name), err)
              }
            } else { // Unknown field - skip it to support schema evolution
              TProtocolUtil.skip(protocol, field.`type`)
            }
            protocol.readFieldEnd()
            field = protocol.readFieldBegin()
          }
          constructor.construct(regs, 0)
        }

        def encodeValue(value: A, protocol: TProtocol): Unit = {
          val regs = Registers(usedRegisters)
          deconstruct.deconstruct(regs, 0, value)
          val len = fields.length
          var idx = 0
          while (idx < len) {
            val field = fields(idx)
            protocol.writeFieldBegin(new TField(field.name, field.tType, (idx + 1).toShort))
            val codec  = field.codec
            val offset = field.offset
            field.typeTag match {
              case 0 => codec.asInstanceOf[ThriftCodec[AnyRef]].encodeValue(regs.getObject(offset), protocol)
              case 1 => codec.asInstanceOf[ThriftCodec[Int]].encodeValue(regs.getInt(offset), protocol)
              case 2 => codec.asInstanceOf[ThriftCodec[Long]].encodeValue(regs.getLong(offset), protocol)
              case 3 => codec.asInstanceOf[ThriftCodec[Float]].encodeValue(regs.getFloat(offset), protocol)
              case 4 => codec.asInstanceOf[ThriftCodec[Double]].encodeValue(regs.getDouble(offset), protocol)
              case 5 => codec.asInstanceOf[ThriftCodec[Boolean]].encodeValue(regs.getBoolean(offset), protocol)
              case 6 => codec.asInstanceOf[ThriftCodec[Byte]].encodeValue(regs.getByte(offset), protocol)
              case 7 => codec.asInstanceOf[ThriftCodec[Char]].encodeValue(regs.getChar(offset), protocol)
              case 8 => codec.asInstanceOf[ThriftCodec[Short]].encodeValue(regs.getShort(offset), protocol)
              case _ => codec.asInstanceOf[ThriftCodec[Unit]].encodeValue((), protocol)
            }
            idx += 1
          }
          protocol.writeFieldStop()
        }
      }
    }
    else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ThriftCodec[A]]]

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
      val len            = cases.length
      val codecs         = new Array[ThriftCodec[?]](len)
      var idx            = 0
      while (idx < len) {
        codecs(idx) = D.instance(cases(idx).value.metadata).force
        idx += 1
      }
      new ThriftCodec[A]() {
        private[this] val discriminator = variantBinding.discriminator
        private[this] val caseCodecs    = codecs
        private[this] val tTypes        = cases.map(c => getThriftType(c.value)).toArray

        def decodeValue(protocol: TProtocol): A = {
          val field   = protocol.readFieldBegin()
          val caseIdx = field.id - 1
          if (caseIdx >= 0 && caseIdx < caseCodecs.length) {
            try {
              val result = caseCodecs(caseIdx).asInstanceOf[ThriftCodec[A]].decodeValue(protocol)
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
              case err if NonFatal(err) => error(new DynamicOptic.Node.Case(cases(caseIdx).name), err)
            }
          } else {
            // Unknown case - skip the field
            TProtocolUtil.skip(protocol, field.`type`)
            protocol.readFieldEnd()
            error(s"Unknown variant case index: ${caseIdx + 1}")
          }
        }

        def encodeValue(value: A, protocol: TProtocol): Unit = {
          val idx = discriminator.discriminate(value)
          protocol.writeFieldBegin(new TField("", tTypes(idx), (idx + 1).toShort))
          caseCodecs(idx).asInstanceOf[ThriftCodec[A]].encodeValue(value, protocol)
          protocol.writeFieldStop()
        }
      }
    }
    else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ThriftCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      D.instance(element.metadata).map { codec =>
        val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
        val tType      = getThriftType(element)
        new ThriftCodec[Col[Elem]]() {
          private[this] val constructor     = seqBinding.constructor
          private[this] val deconstructor   = seqBinding.deconstructor
          private[this] val elementCodec    = codec.asInstanceOf[ThriftCodec[Elem]]
          private[this] val elementTType    = tType
          private[this] val elementClassTag = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

          def decodeValue(protocol: TProtocol): Col[Elem] = {
            val size    = protocol.readListBegin().size
            val builder = constructor.newBuilder[Elem](size)(elementClassTag)
            var idx     = 0
            try {
              while (idx < size) {
                constructor.add(builder, elementCodec.decodeValue(protocol))
                idx += 1
              }
              constructor.result(builder)
            } catch {
              case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
            }
          }

          def encodeValue(value: Col[Elem], protocol: TProtocol): Unit = {
            val size = deconstructor.size(value)
            protocol.writeListBegin(new TList(elementTType, size))
            val it = deconstructor.deconstruct(value)
            while (it.hasNext) {
              elementCodec.encodeValue(it.next(), protocol)
            }
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ThriftCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
        val mapBinding   = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
        val keyReflect   = key.asInstanceOf[Reflect[Binding, Key]]
        val valueReflect = value.asInstanceOf[Reflect[Binding, Value]]
        val keyTType     = getThriftType(keyReflect)
        val valueTType   = getThriftType(valueReflect)
        new ThriftCodec[Map[Key, Value]]() {
          private[this] val constructor   = mapBinding.constructor
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val keyCodec      = codec1.asInstanceOf[ThriftCodec[Key]]
          private[this] val valueCodec    = codec2.asInstanceOf[ThriftCodec[Value]]

          def decodeValue(protocol: TProtocol): Map[Key, Value] = {
            val size    = protocol.readMapBegin().size
            val builder = constructor.newObjectBuilder[Key, Value](size)
            var idx     = 0
            var k: Key  = null.asInstanceOf[Key]
            try {
              while (idx < size) {
                k = keyCodec.decodeValue(protocol)
                val v = valueCodec.decodeValue(protocol)
                constructor.addObject(builder, k, v)
                k = null.asInstanceOf[Key]
                idx += 1
              }
              constructor.resultObject(builder)
            } catch {
              case err if NonFatal(err) =>
                error(
                  if (k != null) new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k))
                  else new DynamicOptic.Node.AtIndex(idx),
                  err
                )
            }
          }

          def encodeValue(value: Map[Key, Value], protocol: TProtocol): Unit = {
            val size = deconstructor.size(value)
            protocol.writeMapBegin(new TMap(keyTType, valueTType, size))
            val it = deconstructor.deconstruct(value)
            while (it.hasNext) {
              val kv = it.next()
              keyCodec.encodeValue(deconstructor.getKey(kv), protocol)
              valueCodec.encodeValue(deconstructor.getValue(kv), protocol)
            }
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[ThriftCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftCodec[DynamicValue]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[ThriftCodec[DynamicValue]]]

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      D.instance(wrapped.metadata).map { codec =>
        val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
        new ThriftCodec[A] {
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrappedCodec = codec.asInstanceOf[ThriftCodec[Wrapped]]

          def decodeValue(protocol: TProtocol): A =
            try wrap(wrappedCodec.decodeValue(protocol))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          def encodeValue(value: A, protocol: TProtocol): Unit = wrappedCodec.encodeValue(unwrap(value), protocol)
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ThriftCodec[A]]]

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
        case _: PrimitiveType.Char       => TType.I16
        case _: PrimitiveType.BigDecimal => TType.STRUCT
        case _: PrimitiveType.DayOfWeek  => TType.BYTE
        case _: PrimitiveType.Month      => TType.BYTE
        case _: PrimitiveType.Year       => TType.I32
        case _: PrimitiveType.ZoneOffset => TType.I32
        case _                           => TType.STRING
      }
    } else if (reflect.isSequence) TType.LIST
    else if (reflect.isMap) TType.MAP
    else TType.STRUCT

  // Thread-local cache for recursive type handling
  private[this] val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[TypeId[?], Array[ThriftFieldInfo]]] {
      override def initialValue: java.util.HashMap[TypeId[?], Array[ThriftFieldInfo]] = new java.util.HashMap
    }
  private[this] val dynamicValueCodec = new ThriftCodec[DynamicValue]() {
    def decodeValue(protocol: TProtocol): DynamicValue = error("Cannot decode DynamicValue without schema")

    def encodeValue(value: DynamicValue, protocol: TProtocol): Unit = value match {
      case pv: DynamicValue.Primitive =>
        pv.value match {
          case v: PrimitiveValue.String         => ThriftCodec.stringCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Int            => ThriftCodec.intCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Long           => ThriftCodec.longCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Boolean        => ThriftCodec.booleanCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Double         => ThriftCodec.doubleCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Float          => ThriftCodec.floatCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Short          => ThriftCodec.shortCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Byte           => ThriftCodec.byteCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Char           => ThriftCodec.charCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.BigInt         => ThriftCodec.bigIntCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.BigDecimal     => ThriftCodec.bigDecimalCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.UUID           => ThriftCodec.uuidCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Instant        => ThriftCodec.instantCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.LocalDate      => ThriftCodec.localDateCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.LocalTime      => ThriftCodec.localTimeCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.LocalDateTime  => ThriftCodec.localDateTimeCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.OffsetTime     => ThriftCodec.offsetTimeCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.OffsetDateTime => ThriftCodec.offsetDateTimeCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.ZonedDateTime  => ThriftCodec.zonedDateTimeCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.ZoneId         => ThriftCodec.zoneIdCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.ZoneOffset     => ThriftCodec.zoneOffsetCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.DayOfWeek      => ThriftCodec.dayOfWeekCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Month          => ThriftCodec.monthCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.MonthDay       => ThriftCodec.monthDayCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Year           => ThriftCodec.yearCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.YearMonth      => ThriftCodec.yearMonthCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Period         => ThriftCodec.periodCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Duration       => ThriftCodec.durationCodec.encodeValue(v.value, protocol)
          case v: PrimitiveValue.Currency       => ThriftCodec.currencyCodec.encodeValue(v.value, protocol)
          case _: PrimitiveValue.Unit.type      => ThriftCodec.unitCodec.encodeValue((), protocol)
        }
      case r: DynamicValue.Record =>
        val fields = r.fields
        var idx    = 0
        fields.foreach { case (name, dv) =>
          protocol.writeFieldBegin(new TField(name, TType.STRUCT, (idx + 1).toShort))
          encodeValue(dv, protocol)
          idx += 1
        }
        protocol.writeFieldStop()
      case v: DynamicValue.Variant =>
        protocol.writeFieldBegin(new TField(v.caseNameValue, TType.STRUCT, 1))
        encodeValue(v.value, protocol)
        protocol.writeFieldStop()
      case s: DynamicValue.Sequence =>
        val elements = s.elements
        protocol.writeListBegin(new TList(TType.STRUCT, elements.size))
        elements.foreach(encodeValue(_, protocol))
      case m: DynamicValue.Map =>
        val entries = m.entries
        protocol.writeMapBegin(new TMap(TType.STRUCT, TType.STRUCT, entries.size))
        entries.foreach { case (k, v) =>
          encodeValue(k, protocol)
          encodeValue(v, protocol)
        }
      case _: DynamicValue.Null.type => ThriftCodec.unitCodec.encodeValue((), protocol)
    }
  }
}

private case class ThriftFieldInfo(
  name: String,
  codec: ThriftCodec[?],
  offset: RegisterOffset.RegisterOffset,
  typeTag: Int,
  tType: Byte
)
