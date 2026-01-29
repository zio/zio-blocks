package zio.blocks.schema.thrift

import org.apache.thrift.protocol._
import zio.blocks.schema._
import zio.blocks.schema.thrift.ThriftBinaryCodec
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
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
          new ThreadLocal[java.util.HashMap[TypeName[?], Array[ThriftBinaryCodec[?]]]] {
            override def initialValue: java.util.HashMap[TypeName[?], Array[ThriftBinaryCodec[?]]] =
              new java.util.HashMap
          }

        // Deriver implementation
        override def derivePrimitive[A](
          primitiveType: PrimitiveType[A],
          typeName: TypeName[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        ): Lazy[ThriftBinaryCodec[A]] =
          Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
          deriveCodec(
            new Reflect.Record(fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]], typeName, binding, doc, modifiers)
          )
        }

        override def deriveVariant[F[_, _], A](
          cases: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Variant, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[C[A]],
          examples: Seq[C[A]]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[C[A]]] = Lazy {
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
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[M[K, V]],
          examples: Seq[M[K, V]]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[M[K, V]]] = Lazy {
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
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[DynamicValue],
          examples: Seq[DynamicValue]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[DynamicValue]] =
          Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

        override def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeName: TypeName[A],
          wrapperPrimitiveType: Option[PrimitiveType[A]],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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

        // Main codec derivation logic - all inlined to avoid type parameter issues
        private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ThriftBinaryCodec[A] = {
          if (reflect.isPrimitive) {
            val primitive = reflect.asPrimitive.get
            if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
              (primitive.primitiveType match {
                case _: PrimitiveType.Unit.type      => ThriftBinaryCodec.unitCodec
                case _: PrimitiveType.Boolean        => ThriftBinaryCodec.booleanCodec
                case _: PrimitiveType.Byte           => ThriftBinaryCodec.byteCodec
                case _: PrimitiveType.Short          => ThriftBinaryCodec.shortCodec
                case _: PrimitiveType.Int            => ThriftBinaryCodec.intCodec
                case _: PrimitiveType.Long           => ThriftBinaryCodec.longCodec
                case _: PrimitiveType.Float          => ThriftBinaryCodec.floatCodec
                case _: PrimitiveType.Double         => ThriftBinaryCodec.doubleCodec
                case _: PrimitiveType.Char           => ThriftBinaryCodec.charCodec
                case _: PrimitiveType.String         => ThriftBinaryCodec.stringCodec
                case _: PrimitiveType.BigInt         => ThriftBinaryCodec.bigIntCodec
                case _: PrimitiveType.BigDecimal     => ThriftBinaryCodec.bigDecimalCodec
                case _: PrimitiveType.DayOfWeek      => ThriftBinaryCodec.dayOfWeekCodec
                case _: PrimitiveType.Month          => ThriftBinaryCodec.monthCodec
                case _: PrimitiveType.Year           => ThriftBinaryCodec.yearCodec
                case _: PrimitiveType.MonthDay       => ThriftBinaryCodec.monthDayCodec
                case _: PrimitiveType.YearMonth      => ThriftBinaryCodec.yearMonthCodec
                case _: PrimitiveType.Period         => ThriftBinaryCodec.periodCodec
                case _: PrimitiveType.Duration       => ThriftBinaryCodec.durationCodec
                case _: PrimitiveType.Instant        => ThriftBinaryCodec.instantCodec
                case _: PrimitiveType.LocalDate      => ThriftBinaryCodec.localDateCodec
                case _: PrimitiveType.LocalTime      => ThriftBinaryCodec.localTimeCodec
                case _: PrimitiveType.LocalDateTime  => ThriftBinaryCodec.localDateTimeCodec
                case _: PrimitiveType.OffsetTime     => ThriftBinaryCodec.offsetTimeCodec
                case _: PrimitiveType.OffsetDateTime => ThriftBinaryCodec.offsetDateTimeCodec
                case _: PrimitiveType.ZonedDateTime  => ThriftBinaryCodec.zonedDateTimeCodec
                case _: PrimitiveType.ZoneId         => ThriftBinaryCodec.zoneIdCodec
                case _: PrimitiveType.ZoneOffset     => ThriftBinaryCodec.zoneOffsetCodec
                case _: PrimitiveType.Currency       => ThriftBinaryCodec.currencyCodec
                case _: PrimitiveType.UUID           => ThriftBinaryCodec.uuidCodec
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
                  val size    = protocol.readListBegin().size
                  val builder = constructor.newObjectBuilder[Elem](size)
                  var idx     = 0
                  try {
                    while (idx < size) {
                      constructor.addObject(builder, elementCodec.decodeUnsafe(protocol))
                      idx += 1
                    }
                    constructor.resultObject(builder)
                  } catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                  }
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
              val binding      = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val keyReflect   = map.key.asInstanceOf[Reflect[Binding, Key]]
              val valueReflect = map.value.asInstanceOf[Reflect[Binding, Value]]
              val keyCodec     = deriveCodec(keyReflect)
              val valueCodec   = deriveCodec(valueReflect)
              val keyTType     = getThriftType(keyReflect)
              val valueTType   = getThriftType(valueReflect)
              new ThriftBinaryCodec[Map[Key, Value]]() {
                private[this] val constructor   = binding.constructor
                private[this] val deconstructor = binding.deconstructor

                def decodeUnsafe(protocol: TProtocol): Map[Key, Value] = {
                  val size    = protocol.readMapBegin().size
                  val builder = constructor.newObjectBuilder[Key, Value](size)
                  var idx     = 0
                  var k: Key  = null.asInstanceOf[Key]
                  var hasKey  = false
                  try {
                    while (idx < size) {
                      k = keyCodec.decodeUnsafe(protocol)
                      hasKey = true
                      val v = valueCodec.decodeUnsafe(protocol)
                      constructor.addObject(builder, k, v)
                      hasKey = false
                      idx += 1
                    }
                    constructor.resultObject(builder)
                  } catch {
                    case error if NonFatal(error) =>
                      decodeError(
                        if (hasKey) new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k))
                        else new DynamicOptic.Node.AtIndex(idx),
                        error
                      )
                  }
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
              val typeName   = record.typeName
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
                    case Left(err) => decodeError(err.message)
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
          def decodeUnsafe(protocol: TProtocol): DynamicValue = decodeError("Cannot decode DynamicValue without schema")

          def encode(value: DynamicValue, protocol: TProtocol): Unit =
            value match {
              case DynamicValue.Primitive(pv) =>
                pv match {
                  case PrimitiveValue.String(v)         => ThriftBinaryCodec.stringCodec.encode(v, protocol)
                  case PrimitiveValue.Int(v)            => ThriftBinaryCodec.intCodec.encode(v, protocol)
                  case PrimitiveValue.Long(v)           => ThriftBinaryCodec.longCodec.encode(v, protocol)
                  case PrimitiveValue.Boolean(v)        => ThriftBinaryCodec.booleanCodec.encode(v, protocol)
                  case PrimitiveValue.Double(v)         => ThriftBinaryCodec.doubleCodec.encode(v, protocol)
                  case PrimitiveValue.Float(v)          => ThriftBinaryCodec.floatCodec.encode(v, protocol)
                  case PrimitiveValue.Short(v)          => ThriftBinaryCodec.shortCodec.encode(v, protocol)
                  case PrimitiveValue.Byte(v)           => ThriftBinaryCodec.byteCodec.encode(v, protocol)
                  case PrimitiveValue.Char(v)           => ThriftBinaryCodec.charCodec.encode(v, protocol)
                  case PrimitiveValue.BigInt(v)         => ThriftBinaryCodec.bigIntCodec.encode(v, protocol)
                  case PrimitiveValue.BigDecimal(v)     => ThriftBinaryCodec.bigDecimalCodec.encode(v, protocol)
                  case PrimitiveValue.Unit              => ThriftBinaryCodec.unitCodec.encode((), protocol)
                  case PrimitiveValue.UUID(v)           => ThriftBinaryCodec.uuidCodec.encode(v, protocol)
                  case PrimitiveValue.Instant(v)        => ThriftBinaryCodec.instantCodec.encode(v, protocol)
                  case PrimitiveValue.LocalDate(v)      => ThriftBinaryCodec.localDateCodec.encode(v, protocol)
                  case PrimitiveValue.LocalTime(v)      => ThriftBinaryCodec.localTimeCodec.encode(v, protocol)
                  case PrimitiveValue.LocalDateTime(v)  => ThriftBinaryCodec.localDateTimeCodec.encode(v, protocol)
                  case PrimitiveValue.OffsetTime(v)     => ThriftBinaryCodec.offsetTimeCodec.encode(v, protocol)
                  case PrimitiveValue.OffsetDateTime(v) => ThriftBinaryCodec.offsetDateTimeCodec.encode(v, protocol)
                  case PrimitiveValue.ZonedDateTime(v)  => ThriftBinaryCodec.zonedDateTimeCodec.encode(v, protocol)
                  case PrimitiveValue.ZoneId(v)         => ThriftBinaryCodec.zoneIdCodec.encode(v, protocol)
                  case PrimitiveValue.ZoneOffset(v)     => ThriftBinaryCodec.zoneOffsetCodec.encode(v, protocol)
                  case PrimitiveValue.DayOfWeek(v)      => ThriftBinaryCodec.dayOfWeekCodec.encode(v, protocol)
                  case PrimitiveValue.Month(v)          => ThriftBinaryCodec.monthCodec.encode(v, protocol)
                  case PrimitiveValue.MonthDay(v)       => ThriftBinaryCodec.monthDayCodec.encode(v, protocol)
                  case PrimitiveValue.Year(v)           => ThriftBinaryCodec.yearCodec.encode(v, protocol)
                  case PrimitiveValue.YearMonth(v)      => ThriftBinaryCodec.yearMonthCodec.encode(v, protocol)
                  case PrimitiveValue.Period(v)         => ThriftBinaryCodec.periodCodec.encode(v, protocol)
                  case PrimitiveValue.Duration(v)       => ThriftBinaryCodec.durationCodec.encode(v, protocol)
                  case PrimitiveValue.Currency(v)       => ThriftBinaryCodec.currencyCodec.encode(v, protocol)
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
              case DynamicValue.Null =>
                ThriftBinaryCodec.unitCodec.encode((), protocol)
            }
        }
      }
    )
