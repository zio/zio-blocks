package zio.blocks.schema.thrift

import org.apache.thrift.protocol._
import scala.reflect.ClassTag
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.TypeId
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

        // Deriver implementation
        override def derivePrimitive[A](
          primitiveType: PrimitiveType[A],
          typeId: TypeId[A],
          binding: Binding.Primitive[A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        ): Lazy[ThriftBinaryCodec[A]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            Lazy(primitiveType match {
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
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[ThriftBinaryCodec[A]]]

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeId: TypeId[A],
          binding: Binding.Record[A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val recordBinding = binding.asInstanceOf[Binding.Record[A]]
            val fieldCount    = fields.length
            // Check for cached recursive codec
            val cache  = recursiveRecordCache.get()
            val cached = cache.get(typeId)
            if (cached ne null) Lazy {
              new ThriftBinaryCodec[A]() {
                def decodeUnsafe(protocol: TProtocol): A = {
                  val actualCodecs = cache.get(typeId)
                  if (actualCodecs != null && actualCodecs.length > 0)
                    actualCodecs(0).asInstanceOf[ThriftBinaryCodec[A]].decodeUnsafe(protocol)
                  else decodeError("Recursive type not yet initialized")
                }

                def encode(value: A, protocol: TProtocol): Unit = {
                  val actualCodecs = cache.get(typeId)
                  if (actualCodecs != null && actualCodecs.length > 0)
                    actualCodecs(0).asInstanceOf[ThriftBinaryCodec[A]].encode(value, protocol)
                  else throw new IllegalStateException("Recursive type not yet initialized")
                }
              }
            }
            else
              Lazy {
                val isRecursive = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
                val placeholder = new Array[ThriftBinaryCodec[?]](1)
                if (isRecursive) cache.put(typeId, placeholder)
                val fieldCodecs = new Array[ThriftBinaryCodec[?]](fieldCount)
                var offset      = 0L
                var idx         = 0
                while (idx < fieldCount) {
                  val codec = D.instance(fields(idx).value.metadata).force
                  fieldCodecs(idx) = codec
                  offset = RegisterOffset.add(codec.valueOffset, offset)
                  idx += 1
                }
                val codec = new ThriftBinaryCodec[A]() {
                  private[this] val fieldNames    = fields.map(_.name)
                  private[this] val constructor   = recordBinding.constructor
                  private[this] val deconstruct   = recordBinding.deconstructor
                  private[this] val usedRegisters = offset

                  def decodeUnsafe(protocol: TProtocol): A = {
                    val regs  = Registers(usedRegisters)
                    var field = protocol.readFieldBegin()
                    while (field.`type` != TType.STOP) {
                      val fieldIdx = field.id - 1
                      if (fieldIdx >= 0 && fieldIdx < fieldCount) {
                        try {
                          val codec  = fieldCodecs(fieldIdx)
                          var offset = 0L
                          var i      = 0
                          while (i < fieldIdx) {
                            offset = RegisterOffset.add(fieldCodecs(i).valueOffset, offset)
                            i += 1
                          }
                          codec.valueType match {
                            case ThriftBinaryCodec.objectType =>
                              regs
                                .setObject(offset, codec.asInstanceOf[ThriftBinaryCodec[AnyRef]].decodeUnsafe(protocol))
                            case ThriftBinaryCodec.intType =>
                              regs.setInt(offset, codec.asInstanceOf[ThriftBinaryCodec[Int]].decodeUnsafe(protocol))
                            case ThriftBinaryCodec.longType =>
                              regs.setLong(offset, codec.asInstanceOf[ThriftBinaryCodec[Long]].decodeUnsafe(protocol))
                            case ThriftBinaryCodec.floatType =>
                              regs.setFloat(offset, codec.asInstanceOf[ThriftBinaryCodec[Float]].decodeUnsafe(protocol))
                            case ThriftBinaryCodec.doubleType =>
                              regs
                                .setDouble(offset, codec.asInstanceOf[ThriftBinaryCodec[Double]].decodeUnsafe(protocol))
                            case ThriftBinaryCodec.booleanType =>
                              regs
                                .setBoolean(
                                  offset,
                                  codec.asInstanceOf[ThriftBinaryCodec[Boolean]].decodeUnsafe(protocol)
                                )
                            case ThriftBinaryCodec.byteType =>
                              regs.setByte(offset, codec.asInstanceOf[ThriftBinaryCodec[Byte]].decodeUnsafe(protocol))
                            case ThriftBinaryCodec.charType =>
                              regs.setChar(offset, codec.asInstanceOf[ThriftBinaryCodec[Char]].decodeUnsafe(protocol))
                            case ThriftBinaryCodec.shortType =>
                              regs.setShort(offset, codec.asInstanceOf[ThriftBinaryCodec[Short]].decodeUnsafe(protocol))
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
              }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[ThriftBinaryCodec[A]]]

        override def deriveVariant[F[_, _], A](
          cases: IndexedSeq[Term[F, A, ?]],
          typeId: TypeId[A],
          binding: Binding.Variant[A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
            val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
            val len            = cases.length
            val codecs         = new Array[ThriftBinaryCodec[?]](len)
            var idx            = 0
            while (idx < len) {
              codecs(idx) = D.instance(cases(idx).value.metadata).force
              idx += 1
            }
            new ThriftBinaryCodec[A]() {
              private[this] val discriminator = variantBinding.discriminator
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
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(cases(caseIdx).name), error)
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
          }
          else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[ThriftBinaryCodec[A]]]

        override def deriveSequence[F[_, _], C[_], A](
          element: Reflect[F, A],
          typeId: TypeId[C[A]],
          binding: Binding.Seq[C, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[C[A]],
          examples: Seq[C[A]]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[C[A]]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            D.instance(element.metadata).map { codec =>
              val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
              val tType      = getThriftType(element)
              new ThriftBinaryCodec[Col[Elem]]() {
                private[this] val constructor     = seqBinding.constructor
                private[this] val deconstructor   = seqBinding.deconstructor
                private[this] val elementCodec    = codec.asInstanceOf[ThriftBinaryCodec[Elem]]
                private[this] val elementTType    = tType
                private[this] val elementClassTag = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

                def decodeUnsafe(protocol: TProtocol): Col[Elem] = {
                  val size    = protocol.readListBegin().size
                  val builder = constructor.newBuilder[Elem](size)(elementClassTag)
                  var idx     = 0
                  try {
                    while (idx < size) {
                      constructor.add(builder, elementCodec.decodeUnsafe(protocol))
                      idx += 1
                    }
                    constructor.result(builder)
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
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[ThriftBinaryCodec[C[A]]]]

        override def deriveMap[F[_, _], M[_, _], K, V](
          key: Reflect[F, K],
          value: Reflect[F, V],
          typeId: TypeId[M[K, V]],
          binding: Binding.Map[M, K, V],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[M[K, V]],
          examples: Seq[M[K, V]]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[M[K, V]]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
              val mapBinding   = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
              val keyReflect   = key.asInstanceOf[Reflect[Binding, Key]]
              val valueReflect = value.asInstanceOf[Reflect[Binding, Value]]
              val keyTType     = getThriftType(keyReflect)
              val valueTType   = getThriftType(valueReflect)
              new ThriftBinaryCodec[Map[Key, Value]]() {
                private[this] val constructor   = mapBinding.constructor
                private[this] val deconstructor = mapBinding.deconstructor
                private[this] val keyCodec      = codec1.asInstanceOf[ThriftBinaryCodec[Key]]
                private[this] val valueCodec    = codec2.asInstanceOf[ThriftBinaryCodec[Value]]

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
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
        }.asInstanceOf[Lazy[ThriftBinaryCodec[M[K, V]]]]

        override def deriveDynamic[F[_, _]](
          binding: Binding.Dynamic,
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[DynamicValue],
          examples: Seq[DynamicValue]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[DynamicValue]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
          else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
        }.asInstanceOf[Lazy[ThriftBinaryCodec[DynamicValue]]]

        override def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeId: TypeId[A],
          binding: Binding.Wrapper[A, B],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect],
          defaultValue: Option[A],
          examples: Seq[A]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            D.instance(wrapped.metadata).map { codec =>
              val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              new ThriftBinaryCodec[A](PrimitiveType.fromTypeId(typeId).fold(ThriftBinaryCodec.objectType) {
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
                private[this] val wrap         = wrapperBinding.wrap
                private[this] val unwrap       = wrapperBinding.unwrap
                private[this] val wrappedCodec = codec.asInstanceOf[ThriftBinaryCodec[Wrapped]]

                def decodeUnsafe(protocol: TProtocol): A =
                  try wrap(wrappedCodec.decodeUnsafe(protocol))
                  catch {
                    case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
                  }

                def encode(value: A, protocol: TProtocol): Unit = wrappedCodec.encode(unwrap(value), protocol)
              }
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
        }.asInstanceOf[Lazy[ThriftBinaryCodec[A]]]

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
          new ThreadLocal[java.util.HashMap[TypeId[?], Array[ThriftBinaryCodec[?]]]] {
            override def initialValue: java.util.HashMap[TypeId[?], Array[ThriftBinaryCodec[?]]] =
              new java.util.HashMap
          }
        private[this] val dynamicValueCodec = new ThriftBinaryCodec[DynamicValue]() {
          def decodeUnsafe(protocol: TProtocol): DynamicValue = decodeError("Cannot decode DynamicValue without schema")

          def encode(value: DynamicValue, protocol: TProtocol): Unit = value match {
            case pv: DynamicValue.Primitive =>
              pv.value match {
                case v: PrimitiveValue.String         => ThriftBinaryCodec.stringCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Int            => ThriftBinaryCodec.intCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Long           => ThriftBinaryCodec.longCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Boolean        => ThriftBinaryCodec.booleanCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Double         => ThriftBinaryCodec.doubleCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Float          => ThriftBinaryCodec.floatCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Short          => ThriftBinaryCodec.shortCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Byte           => ThriftBinaryCodec.byteCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Char           => ThriftBinaryCodec.charCodec.encode(v.value, protocol)
                case v: PrimitiveValue.BigInt         => ThriftBinaryCodec.bigIntCodec.encode(v.value, protocol)
                case v: PrimitiveValue.BigDecimal     => ThriftBinaryCodec.bigDecimalCodec.encode(v.value, protocol)
                case v: PrimitiveValue.UUID           => ThriftBinaryCodec.uuidCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Instant        => ThriftBinaryCodec.instantCodec.encode(v.value, protocol)
                case v: PrimitiveValue.LocalDate      => ThriftBinaryCodec.localDateCodec.encode(v.value, protocol)
                case v: PrimitiveValue.LocalTime      => ThriftBinaryCodec.localTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.LocalDateTime  => ThriftBinaryCodec.localDateTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.OffsetTime     => ThriftBinaryCodec.offsetTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.OffsetDateTime => ThriftBinaryCodec.offsetDateTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.ZonedDateTime  => ThriftBinaryCodec.zonedDateTimeCodec.encode(v.value, protocol)
                case v: PrimitiveValue.ZoneId         => ThriftBinaryCodec.zoneIdCodec.encode(v.value, protocol)
                case v: PrimitiveValue.ZoneOffset     => ThriftBinaryCodec.zoneOffsetCodec.encode(v.value, protocol)
                case v: PrimitiveValue.DayOfWeek      => ThriftBinaryCodec.dayOfWeekCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Month          => ThriftBinaryCodec.monthCodec.encode(v.value, protocol)
                case v: PrimitiveValue.MonthDay       => ThriftBinaryCodec.monthDayCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Year           => ThriftBinaryCodec.yearCodec.encode(v.value, protocol)
                case v: PrimitiveValue.YearMonth      => ThriftBinaryCodec.yearMonthCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Period         => ThriftBinaryCodec.periodCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Duration       => ThriftBinaryCodec.durationCodec.encode(v.value, protocol)
                case v: PrimitiveValue.Currency       => ThriftBinaryCodec.currencyCodec.encode(v.value, protocol)
                case _: PrimitiveValue.Unit.type      => ThriftBinaryCodec.unitCodec.encode((), protocol)
              }
            case r: DynamicValue.Record =>
              val fields = r.fields
              var idx    = 0
              fields.foreach { case (name, dv) =>
                protocol.writeFieldBegin(new TField(name, TType.STRUCT, (idx + 1).toShort))
                encode(dv, protocol)
                idx += 1
              }
              protocol.writeFieldStop()
            case v: DynamicValue.Variant =>
              protocol.writeFieldBegin(new TField(v.caseNameValue, TType.STRUCT, 1))
              encode(v.value, protocol)
              protocol.writeFieldStop()
            case s: DynamicValue.Sequence =>
              val elements = s.elements
              protocol.writeListBegin(new TList(TType.STRUCT, elements.size))
              elements.foreach(encode(_, protocol))
            case m: DynamicValue.Map =>
              val entries = m.entries
              protocol.writeMapBegin(new TMap(TType.STRUCT, TType.STRUCT, entries.size))
              entries.foreach { case (k, v) =>
                encode(k, protocol)
                encode(v, protocol)
              }
            case _: DynamicValue.Null.type => ThriftBinaryCodec.unitCodec.encode((), protocol)
          }
        }
      }
    )
