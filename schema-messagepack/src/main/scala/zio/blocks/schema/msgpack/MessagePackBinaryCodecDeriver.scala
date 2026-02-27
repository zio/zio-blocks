package zio.blocks.schema.msgpack

import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema._
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.TypeId
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object MessagePackBinaryCodecDeriver extends Deriver[MessagePackBinaryCodec] {
  import MessagePackBinaryCodec._

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[MessagePackBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      Lazy(primitiveType match {
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
      })
    } else binding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance
  }.asInstanceOf[Lazy[MessagePackBinaryCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val len           = fields.length
      val isRecursive   = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
      var infos         =
        if (isRecursive) recursiveRecordCache.get.get(typeId)
        else null
      val deriveCodecs = infos eq null
      if (deriveCodecs) {
        infos = new Array[MessagePackFieldInfo](len)
        var idx = 0
        while (idx < len) {
          val fieldName = fields(idx).name
          val info      = new MessagePackFieldInfo(DynamicOptic.Node.Field(fieldName), idx)
          info.setName(fieldName)
          infos(idx) = info
          idx += 1
        }
        if (isRecursive) recursiveRecordCache.get.put(typeId, infos)
      }
      var offset: RegisterOffset.RegisterOffset = 0L
      var idx                                   = 0
      while (idx < len) {
        val field     = fields(idx)
        val fieldInfo = infos(idx)
        if (deriveCodecs) {
          val codec = D.instance(field.value.metadata).force
          fieldInfo.setCodec(codec)
          fieldInfo.setOffset(offset)
          offset = RegisterOffset.add(codec.valueOffset, offset)
        }
        idx += 1
      }
      val fieldNameMap = if (len > 4) {
        val map    = new java.util.HashMap[String, java.lang.Integer](len)
        var mapIdx = 0
        while (mapIdx < len) {
          map.put(infos(mapIdx).name, mapIdx)
          mapIdx += 1
        }
        map
      } else null
      new MessagePackBinaryCodec[A]() {
        private[this] val deconstructor = recordBinding.deconstructor
        private[this] val constructor   = recordBinding.constructor
        private[this] val fieldInfos    = infos
        private[this] val fieldLookup   = fieldNameMap
        private[this] var usedRegisters = offset

        def decodeValue(in: MessagePackReader): A = {
          val fieldLen = fieldInfos.length
          if (fieldLen > 0 && usedRegisters == 0) usedRegisters = fieldInfos(fieldLen - 1).usedRegisters
          val regs    = Registers(usedRegisters)
          val mapSize = in.readMapHeader()
          if (mapSize != fieldLen) in.decodeError(s"Expected $fieldLen fields, got: $mapSize")
          var idx = 0
          while (idx < mapSize) {
            val fieldName = in.readString()
            val fieldIdx  = if (fieldLookup != null) {
              val mapIdx = fieldLookup.get(fieldName)
              if (mapIdx == null) in.decodeError(s"Unknown field: $fieldName")
              mapIdx.intValue()
            } else {
              var i     = 0
              var found = -1
              while (i < fieldLen && found < 0) {
                if (fieldInfos(i).name == fieldName) found = i
                i += 1
              }
              if (found < 0) in.decodeError(s"Unknown field: $fieldName")
              found
            }
            val fieldInfo = fieldInfos(fieldIdx)
            try fieldInfo.readValue(in, regs, 0)
            catch {
              case error if NonFatal(error) => decodeError(fieldInfo.span, error)
            }
            idx += 1
          }
          constructor.construct(regs, 0)
        }

        def encodeValue(value: A, out: MessagePackWriter): Unit = {
          val fieldLen = fieldInfos.length
          if (fieldLen > 0 && usedRegisters == 0) usedRegisters = fieldInfos(fieldLen - 1).usedRegisters
          val regs = Registers(usedRegisters)
          deconstructor.deconstruct(regs, 0, value)
          out.writeMapHeader(fieldLen)
          var idx = 0
          while (idx < fieldLen) {
            val fieldInfo = fieldInfos(idx)
            fieldInfo.writeEncodedName(out)
            fieldInfo.writeValue(out, regs, 0)
            idx += 1
          }
        }
      }
    }
    else binding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
      if (typeId.isOption) {
        val someRecord  = cases(1).value.asRecord.get
        val someBinding = someRecord.recordBinding
          .asInstanceOf[BindingInstance[TC, ?, ?]]
          .binding
          .asInstanceOf[Binding.Record[A]]
        val valueReflect = someRecord.fields(0).value
        D.instance(valueReflect.metadata).map { valueCodec =>
          new MessagePackBinaryCodec[A]() {
            private[this] val discriminator = variantBinding.discriminator
            private[this] val constructor   = someBinding.constructor
            private[this] val deconstructor = someBinding.deconstructor
            private[this] val codec         = valueCodec
            private[this] val usedRegisters = someBinding.constructor.usedRegisters

            def decodeValue(in: MessagePackReader): A = {
              val arrLen = in.readArrayHeader()
              if (arrLen == 0) None
              else if (arrLen == 1) {
                val regs = Registers(usedRegisters)
                try {
                  val innerValue = codec.decodeValue(in)
                  codec.valueType match {
                    case 0 => regs.setObject(0, innerValue.asInstanceOf[AnyRef])
                    case 1 => regs.setInt(0, innerValue.asInstanceOf[Int])
                    case 2 => regs.setLong(0, innerValue.asInstanceOf[Long])
                    case 3 => regs.setFloat(0, innerValue.asInstanceOf[Float])
                    case 4 => regs.setDouble(0, innerValue.asInstanceOf[Double])
                    case 5 => regs.setBoolean(0, innerValue.asInstanceOf[Boolean])
                    case 6 => regs.setByte(0, innerValue.asInstanceOf[Byte])
                    case 7 => regs.setChar(0, innerValue.asInstanceOf[Char])
                    case 8 => regs.setShort(0, innerValue.asInstanceOf[Short])
                    case _ =>
                  }
                  constructor.construct(regs, 0)
                } catch {
                  case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Some"), error)
                }
              } else in.decodeError(s"Expected Option array of 0 or 1, got: $arrLen")
            }.asInstanceOf[A]

            def encodeValue(value: A, out: MessagePackWriter): Unit = {
              val idx = discriminator.discriminate(value)
              if (idx == 0) out.writeArrayHeader(0)
              else {
                out.writeArrayHeader(1)
                val regs = Registers(usedRegisters)
                deconstructor.deconstruct(regs, 0, value)
                codec.valueType match {
                  case 0 => codec.asInstanceOf[MessagePackBinaryCodec[AnyRef]].encodeValue(regs.getObject(0), out)
                  case 1 => codec.asInstanceOf[MessagePackBinaryCodec[Int]].encodeValue(regs.getInt(0), out)
                  case 2 => codec.asInstanceOf[MessagePackBinaryCodec[Long]].encodeValue(regs.getLong(0), out)
                  case 3 => codec.asInstanceOf[MessagePackBinaryCodec[Float]].encodeValue(regs.getFloat(0), out)
                  case 4 => codec.asInstanceOf[MessagePackBinaryCodec[Double]].encodeValue(regs.getDouble(0), out)
                  case 5 => codec.asInstanceOf[MessagePackBinaryCodec[Boolean]].encodeValue(regs.getBoolean(0), out)
                  case 6 => codec.asInstanceOf[MessagePackBinaryCodec[Byte]].encodeValue(regs.getByte(0), out)
                  case 7 => codec.asInstanceOf[MessagePackBinaryCodec[Char]].encodeValue(regs.getChar(0), out)
                  case 8 => codec.asInstanceOf[MessagePackBinaryCodec[Short]].encodeValue(regs.getShort(0), out)
                  case _ => codec.asInstanceOf[MessagePackBinaryCodec[Unit]].encodeValue((), out)
                }
              }
            }
          }
        }
      } else if (typeId.isEither) {
        val leftValueReflect  = cases(0).value.asRecord.get.fields(0).value
        val rightValueReflect = cases(1).value.asRecord.get.fields(0).value
        D.instance(leftValueReflect.metadata).zip(D.instance(rightValueReflect.metadata)).map {
          case (leftValueCodec, rightValueCodec) =>
            new MessagePackBinaryCodec[A]() {
              private[this] val discriminator = variantBinding.discriminator
              private[this] val leftCodec     = leftValueCodec.asInstanceOf[MessagePackBinaryCodec[Any]]
              private[this] val rightCodec    = rightValueCodec.asInstanceOf[MessagePackBinaryCodec[Any]]

              def decodeValue(in: MessagePackReader): A = {
                val mapSize = in.readMapHeader()
                if (mapSize != 1) in.decodeError(s"Expected Either map of 1, got: $mapSize")
                val key = in.readString()
                if (key == "left") {
                  try new Left(leftCodec.decodeValue(in)).asInstanceOf[A]
                  catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Left"), error)
                  }
                } else if (key == "right") {
                  try new Right(rightCodec.decodeValue(in)).asInstanceOf[A]
                  catch {
                    case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case("Right"), error)
                  }
                } else in.decodeError(s"Expected 'left' or 'right', got: $key")
              }

              def encodeValue(value: A, out: MessagePackWriter): Unit = {
                val idx = discriminator.discriminate(value)
                out.writeMapHeader(1)
                if (idx == 0) {
                  out.writeString("left")
                  leftCodec.encodeValue(value.asInstanceOf[Left[?, ?]].value, out)
                } else {
                  out.writeString("right")
                  rightCodec.encodeValue(value.asInstanceOf[Right[?, ?]].value, out)
                }
              }
            }
        }
      } else
        Lazy {
          val len    = cases.length
          val codecs = new Array[MessagePackBinaryCodec[?]](len)
          var idx    = 0
          while (idx < len) {
            codecs(idx) = D.instance(cases(idx).value.metadata).force
            idx += 1
          }
          new MessagePackBinaryCodec[A]() {
            private[this] val discriminator = variantBinding.discriminator
            private[this] val caseCodecs    = codecs

            def decodeValue(in: MessagePackReader): A = {
              val idx = in.readIntValue()
              if (idx >= 0 && idx < caseCodecs.length) {
                try caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].decodeValue(in)
                catch {
                  case error if NonFatal(error) => decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
                }
              } else in.decodeError(s"Expected variant index from 0 to ${caseCodecs.length - 1}, got $idx")
            }

            def encodeValue(value: A, out: MessagePackWriter): Unit = {
              val idx = discriminator.discriminate(value)
              out.writeInt(idx)
              caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].encodeValue(value, out)
            }
          }
        }
    } else binding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance
  }.asInstanceOf[Lazy[MessagePackBinaryCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { codec =>
        new MessagePackBinaryCodec[Col[Elem]]() {
          private[this] val deconstructor                     = seqBinding.deconstructor
          private[this] val constructor                       = seqBinding.constructor
          private[this] val elementCodec                      = codec.asInstanceOf[MessagePackBinaryCodec[Elem]]
          private[this] implicit val classTag: ClassTag[Elem] = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

          def decodeValue(in: MessagePackReader): Col[Elem] = {
            val size    = in.readArrayHeader()
            val builder = constructor.newBuilder[Elem](Math.min(size, 16))
            var idx     = 0
            while (idx < size) {
              try constructor.add(builder, elementCodec.decodeValue(in))
              catch {
                case error if NonFatal(error) =>
                  decodeError(new DynamicOptic.Node.AtIndex(idx), error)
              }
              idx += 1
            }
            constructor.result[Elem](builder)
          }

          def encodeValue(value: Col[Elem], out: MessagePackWriter): Unit = {
            val size = deconstructor.size(value)
            out.writeArrayHeader(size)
            val it = deconstructor.deconstruct(value)
            while (it.hasNext) elementCodec.encodeValue(it.next(), out)
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, C[A]]].instance
  }.asInstanceOf[Lazy[MessagePackBinaryCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
        new MessagePackBinaryCodec[Map[Key, Value]]() {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val keyCodec      = codec1.asInstanceOf[MessagePackBinaryCodec[Key]]
          private[this] val valueCodec    = codec2.asInstanceOf[MessagePackBinaryCodec[Value]]
          private[this] val keyReflect    = key.asInstanceOf[Reflect.Bound[Key]]

          def decodeValue(in: MessagePackReader): Map[Key, Value] = {
            val size    = in.readMapHeader()
            val builder = constructor.newObjectBuilder[Key, Value](Math.min(size, 4))
            var idx     = 0
            while (idx < size) {
              val k =
                try keyCodec.decodeValue(in)
                catch {
                  case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                }
              val v =
                try valueCodec.decodeValue(in)
                catch {
                  case error if NonFatal(error) =>
                    decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), error)
                }
              constructor.addObject(builder, k, v)
              idx += 1
            }
            constructor.resultObject[Key, Value](builder)
          }

          def encodeValue(value: Map[Key, Value], out: MessagePackWriter): Unit = {
            val size = deconstructor.size(value)
            out.writeMapHeader(size)
            val it = deconstructor.deconstruct(value)
            while (it.hasNext) {
              val kv = it.next()
              keyCodec.encodeValue(deconstructor.getKey(kv), out)
              valueCodec.encodeValue(deconstructor.getValue(kv), out)
            }
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, M[K, V]]].instance
  }.asInstanceOf[Lazy[MessagePackBinaryCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[DynamicValue]] =
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, DynamicValue]].instance

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
      D.instance(wrapped.metadata).map { codec =>
        new MessagePackBinaryCodec[A](PrimitiveType.fromTypeId(typeId).fold(objectType) {
          case _: PrimitiveType.Boolean   => booleanType
          case _: PrimitiveType.Byte      => byteType
          case _: PrimitiveType.Char      => charType
          case _: PrimitiveType.Short     => shortType
          case _: PrimitiveType.Float     => floatType
          case _: PrimitiveType.Int       => intType
          case _: PrimitiveType.Double    => doubleType
          case _: PrimitiveType.Long      => longType
          case _: PrimitiveType.Unit.type => unitType
          case _                          => objectType
        }) {
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrappedCodec = codec

          def decodeValue(in: MessagePackReader): A =
            try wrap(wrappedCodec.decodeValue(in))
            catch {
              case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
            }

          def encodeValue(value: A, out: MessagePackWriter): Unit = wrappedCodec.encodeValue(unwrap(value), out)
        }
      }
    } else binding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance

  override def instanceOverrides: IndexedSeq[InstanceOverride] = {
    recursiveRecordCache.remove()
    super.instanceOverrides
  }

  type Elem
  type Key
  type Value
  type Col[_]
  type Map[_, _]
  type TC[_]

  private[this] val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[TypeId[?], Array[MessagePackFieldInfo]]] {
      override def initialValue: java.util.HashMap[TypeId[?], Array[MessagePackFieldInfo]] =
        new java.util.HashMap
    }

  private[this] val dynamicValueCodec: MessagePackBinaryCodec[DynamicValue] =
    new MessagePackBinaryCodec[DynamicValue]() {
      def decodeValue(in: MessagePackReader): DynamicValue = decodeDynamic(in)

      def encodeValue(value: DynamicValue, out: MessagePackWriter): Unit = encodeDynamic(value, out)

      private[this] def decodeDynamic(in: MessagePackReader): DynamicValue = {
        val b = in.peekType & 0xff
        if ((b & 0x80) == 0 || (b & 0xe0) == 0xe0) {
          new DynamicValue.Primitive(PrimitiveValue.Long(in.readLongValue()))
        } else if ((b & 0xe0) == 0xa0 || b == 0xd9 || b == 0xda || b == 0xdb) {
          new DynamicValue.Primitive(PrimitiveValue.String(in.readString()))
        } else if ((b & 0xf0) == 0x90 || b == 0xdc || b == 0xdd) {
          val len = in.readArrayHeader()
          if (len < 0) in.decodeError("Array length exceeds maximum (2GB)")
          val builder = zio.blocks.chunk.ChunkBuilder.make[DynamicValue](Math.min(len, 1000000))
          var idx     = 0
          while (idx < len) {
            builder.addOne(decodeDynamic(in))
            idx += 1
          }
          new DynamicValue.Sequence(builder.result())
        } else if ((b & 0xf0) == 0x80 || b == 0xde || b == 0xdf) {
          val len = in.readMapHeader()
          if (len < 0) in.decodeError("Map length exceeds maximum (2GB)")
          val entries       = new Array[(DynamicValue, DynamicValue)](len)
          var idx           = 0
          var allStringKeys = true
          while (idx < len) {
            val key   = decodeDynamic(in)
            val value = decodeDynamic(in)
            entries(idx) = (key, value)
            if (allStringKeys) {
              key match {
                case DynamicValue.Primitive(_: PrimitiveValue.String) =>
                case _                                                => allStringKeys = false
              }
            }
            idx += 1
          }
          if (allStringKeys) {
            val fields = new Array[(String, DynamicValue)](len)
            idx = 0
            while (idx < len) {
              val (k, v) = entries(idx)
              fields(idx) = (k.asInstanceOf[DynamicValue.Primitive].value.asInstanceOf[PrimitiveValue.String].value, v)
              idx += 1
            }
            new DynamicValue.Record(Chunk.from(fields))
          } else new DynamicValue.Map(Chunk.from(entries))
        } else {
          b match {
            case 0xc0 =>
              in.readNil()
              DynamicValue.Null
            case 0xc2 =>
              new DynamicValue.Primitive(new PrimitiveValue.Boolean(in.readBoolean()))
            case 0xc3 =>
              new DynamicValue.Primitive(new PrimitiveValue.Boolean(in.readBoolean()))
            case 0xca =>
              new DynamicValue.Primitive(new PrimitiveValue.Float(in.readFloatValue()))
            case 0xcb =>
              new DynamicValue.Primitive(new PrimitiveValue.Double(in.readDoubleValue()))
            case 0xcc | 0xcd | 0xce | 0xcf | 0xd0 | 0xd1 | 0xd2 | 0xd3 =>
              new DynamicValue.Primitive(new PrimitiveValue.Long(in.readLongValue()))
            case 0xc4 | 0xc5 | 0xc6 =>
              new DynamicValue.Primitive(new PrimitiveValue.BigInt(in.readBigInt()))
            case _ => in.decodeError(s"Unsupported MessagePack type for DynamicValue: $b")
          }
        }
      }

      private[this] def encodeDynamic(value: DynamicValue, out: MessagePackWriter): Unit = value match {
        case p: DynamicValue.Primitive => encodePrimitive(p.value, out)
        case r: DynamicValue.Record    =>
          val fields = r.fields
          val len    = fields.length
          out.writeMapHeader(len)
          var i = 0
          while (i < len) {
            val (k, v) = fields(i)
            out.writeString(k)
            encodeDynamic(v, out)
            i += 1
          }
        case v: DynamicValue.Variant =>
          out.writeMapHeader(2)
          out.writeString("_case")
          out.writeString(v.caseNameValue)
          out.writeString("_value")
          encodeDynamic(v.value, out)
        case s: DynamicValue.Sequence =>
          val elements = s.elements
          val len      = elements.length
          out.writeArrayHeader(len)
          var i = 0
          while (i < len) {
            encodeDynamic(elements(i), out)
            i += 1
          }
        case m: DynamicValue.Map =>
          val entries = m.entries
          val len     = entries.length
          out.writeMapHeader(len)
          var i = 0
          while (i < len) {
            val kv = entries(i)
            encodeDynamic(kv._1, out)
            encodeDynamic(kv._2, out)
            i += 1
          }
        case _ => out.writeNil()
      }

      private[this] def encodePrimitive(p: PrimitiveValue, out: MessagePackWriter): Unit = p match {
        case _: PrimitiveValue.Unit.type      => out.writeMapHeader(0)
        case v: PrimitiveValue.Boolean        => booleanCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Byte           => byteCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Short          => shortCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Int            => intCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Long           => longCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Float          => floatCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Double         => doubleCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Char           => charCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.String         => stringCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.BigInt         => bigIntCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.BigDecimal     => bigDecimalCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.DayOfWeek      => dayOfWeekCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Duration       => durationCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Instant        => instantCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.LocalDate      => localDateCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.LocalDateTime  => localDateTimeCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.LocalTime      => localTimeCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Month          => monthCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.MonthDay       => monthDayCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.OffsetDateTime => offsetDateTimeCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.OffsetTime     => offsetTimeCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Period         => periodCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Year           => yearCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.YearMonth      => yearMonthCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.ZoneId         => zoneIdCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.ZoneOffset     => zoneOffsetCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.ZonedDateTime  => zonedDateTimeCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.Currency       => currencyCodec.encodeValue(v.value, out)
        case v: PrimitiveValue.UUID           => uuidCodec.encodeValue(v.value, out)
      }
    }
}
