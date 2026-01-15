package zio.blocks.schema.thrift

import org.apache.thrift.protocol.{TField, TList, TMap, TProtocol, TStruct, TType}
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{Deriver, BindingInstance}
import zio.blocks.schema.DynamicValue

object ThriftFormat
    extends BinaryFormat[ThriftBinaryCodec](
      "application/x-thrift",
      ThriftDeriver
    )

object ThriftDeriver extends Deriver[ThriftBinaryCodec] {

  override def derivePrimitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  ): Lazy[ThriftBinaryCodec[A]] =
    Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[A]] = Lazy {
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
    modifiers: Seq[Modifier.Reflect]
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
    modifiers: Seq[Modifier.Reflect]
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
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ThriftBinaryCodec[DynamicValue]] =
    Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeName: TypeName[A],
    wrapperPrimitiveType: Option[PrimitiveType[A]],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
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

  private val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[TypeName[?], Array[ThriftBinaryCodec[?]]]] {
      override def initialValue: java.util.HashMap[TypeName[?], Array[ThriftBinaryCodec[?]]] =
        new java.util.HashMap
    }

  private def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ThriftBinaryCodec[A] = {
    if (reflect.isPrimitive) {
      val primitive = reflect.asPrimitive.get
      derivePrimitiveCodec(primitive.primitiveType.asInstanceOf[PrimitiveType[A]])
    } else if (reflect.isRecord) {
      val record = reflect.asRecord.get
      if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
        val binding  = record.recordBinding.asInstanceOf[Binding.Record[A]]
        val fields   = record.fields
        val typeName = record.typeName

        var codecs: Array[ThriftBinaryCodec[?]] = recursiveRecordCache.get.get(typeName)

        if (codecs eq null) {
          var offset = 0L
          val len    = fields.length
          codecs = new Array[ThriftBinaryCodec[?]](len)
          recursiveRecordCache.get.put(typeName, codecs)

          var idx = 0
          while (idx < len) {
            val field = fields(idx)
            val codec = deriveCodec(field.value)
            codecs(idx) = codec
            offset = RegisterOffset.add(codec.valueOffset, offset)
            idx += 1
          }

          new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
            private[this] val deconstructor = binding.deconstructor
            private[this] val constructor   = binding.constructor
            private[this] val usedRegisters = offset
            private[this] val fieldCodecs   = codecs

            override def encode(value: A, p: TProtocol): Unit = {
              p.writeStructBegin(new TStruct(typeName.name))
              val regs = Registers(usedRegisters)
              deconstructor.deconstruct(regs, 0, value)
              val len         = fieldCodecs.length
              var idx         = 0
              var fieldOffset = 0L
              while (idx < len) {
                val field = fields(idx)
                val codec = fieldCodecs(idx)
                p.writeFieldBegin(new TField(field.name, getTType(field.value), (idx + 1).toShort))

                codec.valueType match {
                  case ThriftBinaryCodec.objectType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Any]].encode(regs.getObject(fieldOffset), p)
                  case ThriftBinaryCodec.intType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Int]].encode(regs.getInt(fieldOffset), p)
                  case ThriftBinaryCodec.longType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Long]].encode(regs.getLong(fieldOffset), p)
                  case ThriftBinaryCodec.booleanType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Boolean]].encode(regs.getBoolean(fieldOffset), p)
                  case ThriftBinaryCodec.doubleType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Double]].encode(regs.getDouble(fieldOffset), p)
                  case ThriftBinaryCodec.byteType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Byte]].encode(regs.getByte(fieldOffset), p)
                  case ThriftBinaryCodec.shortType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Short]].encode(regs.getShort(fieldOffset), p)
                  case ThriftBinaryCodec.floatType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Float]].encode(regs.getFloat(fieldOffset), p)
                  case ThriftBinaryCodec.charType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Char]].encode(regs.getChar(fieldOffset), p)
                  case _ =>
                    codec.asInstanceOf[ThriftBinaryCodec[Unit]].encode((), p)
                }

                p.writeFieldEnd()
                fieldOffset = RegisterOffset.add(codec.valueOffset, fieldOffset)
                idx += 1
              }
              p.writeFieldStop()
              p.writeStructEnd()
            }

            def decodeUnsafe(p: TProtocol): A = {
              p.readStructBegin()
              val regs      = Registers(usedRegisters)
              var done      = false
              val len       = fieldCodecs.length
              val offsets   = new Array[Long](len)
              var tmpOffset = 0L
              var k         = 0
              while (k < len) {
                offsets(k) = tmpOffset
                tmpOffset = RegisterOffset.add(fieldCodecs(k).valueOffset, tmpOffset)
                k += 1
              }

              while (!done) {
                val field = p.readFieldBegin()
                if (field.`type` == TType.STOP) {
                  done = true
                } else {
                  val id = field.id - 1
                  if (id >= 0 && id < len) {
                    val codec = fieldCodecs(id)
                    val off   = offsets(id)
                    codec.valueType match {
                      case ThriftBinaryCodec.objectType =>
                        regs.setObject(
                          off,
                          codec.asInstanceOf[ThriftBinaryCodec[Any]].decodeUnsafe(p).asInstanceOf[AnyRef]
                        )
                      case ThriftBinaryCodec.intType =>
                        regs.setInt(off, codec.asInstanceOf[ThriftBinaryCodec[Int]].decodeUnsafe(p))
                      case ThriftBinaryCodec.longType =>
                        regs.setLong(off, codec.asInstanceOf[ThriftBinaryCodec[Long]].decodeUnsafe(p))
                      case ThriftBinaryCodec.booleanType =>
                        regs.setBoolean(off, codec.asInstanceOf[ThriftBinaryCodec[Boolean]].decodeUnsafe(p))
                      case ThriftBinaryCodec.doubleType =>
                        regs.setDouble(off, codec.asInstanceOf[ThriftBinaryCodec[Double]].decodeUnsafe(p))
                      case ThriftBinaryCodec.byteType =>
                        regs.setByte(off, codec.asInstanceOf[ThriftBinaryCodec[Byte]].decodeUnsafe(p))
                      case ThriftBinaryCodec.shortType =>
                        regs.setShort(off, codec.asInstanceOf[ThriftBinaryCodec[Short]].decodeUnsafe(p))
                      case ThriftBinaryCodec.floatType =>
                        regs.setFloat(off, codec.asInstanceOf[ThriftBinaryCodec[Float]].decodeUnsafe(p))
                      case ThriftBinaryCodec.charType =>
                        regs.setChar(off, codec.asInstanceOf[ThriftBinaryCodec[Char]].decodeUnsafe(p))
                      case _ =>
                        codec.asInstanceOf[ThriftBinaryCodec[Unit]].decodeUnsafe(p)
                    }
                  } else {
                    org.apache.thrift.protocol.TProtocolUtil.skip(p, field.`type`)
                  }
                  p.readFieldEnd()
                }
              }
              p.readStructEnd()
              constructor.construct(regs, 0)
            }
          }
        } else {
          new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
            private[this] val deconstructor = binding.deconstructor
            private[this] val constructor   = binding.constructor
            private[this] val fieldCodecs   = codecs

            private[this] lazy val usedRegisters: Long = {
              var off = 0L
              var idx = 0
              while (idx < fieldCodecs.length) {
                off = RegisterOffset.add(fieldCodecs(idx).valueOffset, off)
                idx += 1
              }
              off
            }

            override def encode(value: A, p: TProtocol): Unit = {
              p.writeStructBegin(new TStruct(typeName.name))
              val regs = Registers(usedRegisters)
              deconstructor.deconstruct(regs, 0, value)
              val len         = fieldCodecs.length
              var idx         = 0
              var fieldOffset = 0L
              while (idx < len) {
                val field = fields(idx)
                val codec = fieldCodecs(idx)
                p.writeFieldBegin(new TField(field.name, getTType(field.value), (idx + 1).toShort))

                codec.valueType match {
                  case ThriftBinaryCodec.objectType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Any]].encode(regs.getObject(fieldOffset), p)
                  case ThriftBinaryCodec.intType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Int]].encode(regs.getInt(fieldOffset), p)
                  case ThriftBinaryCodec.longType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Long]].encode(regs.getLong(fieldOffset), p)
                  case ThriftBinaryCodec.booleanType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Boolean]].encode(regs.getBoolean(fieldOffset), p)
                  case ThriftBinaryCodec.doubleType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Double]].encode(regs.getDouble(fieldOffset), p)
                  case ThriftBinaryCodec.byteType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Byte]].encode(regs.getByte(fieldOffset), p)
                  case ThriftBinaryCodec.shortType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Short]].encode(regs.getShort(fieldOffset), p)
                  case ThriftBinaryCodec.floatType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Float]].encode(regs.getFloat(fieldOffset), p)
                  case ThriftBinaryCodec.charType =>
                    codec.asInstanceOf[ThriftBinaryCodec[Char]].encode(regs.getChar(fieldOffset), p)
                  case _ =>
                    codec.asInstanceOf[ThriftBinaryCodec[Unit]].encode((), p)
                }

                p.writeFieldEnd()
                fieldOffset = RegisterOffset.add(codec.valueOffset, fieldOffset)
                idx += 1
              }
              p.writeFieldStop()
              p.writeStructEnd()
            }

            def decodeUnsafe(p: TProtocol): A = {
              p.readStructBegin()
              val regs      = Registers(usedRegisters)
              var done      = false
              val len       = fieldCodecs.length
              val offsets   = new Array[Long](len)
              var tmpOffset = 0L
              var k         = 0
              while (k < len) {
                offsets(k) = tmpOffset
                tmpOffset = RegisterOffset.add(fieldCodecs(k).valueOffset, tmpOffset)
                k += 1
              }

              while (!done) {
                val field = p.readFieldBegin()
                if (field.`type` == TType.STOP) {
                  done = true
                } else {
                  val id = field.id - 1
                  if (id >= 0 && id < len) {
                    val codec = fieldCodecs(id)
                    val off   = offsets(id)
                    codec.valueType match {
                      case ThriftBinaryCodec.objectType =>
                        regs.setObject(
                          off,
                          codec.asInstanceOf[ThriftBinaryCodec[Any]].decodeUnsafe(p).asInstanceOf[AnyRef]
                        )
                      case ThriftBinaryCodec.intType =>
                        regs.setInt(off, codec.asInstanceOf[ThriftBinaryCodec[Int]].decodeUnsafe(p))
                      case ThriftBinaryCodec.longType =>
                        regs.setLong(off, codec.asInstanceOf[ThriftBinaryCodec[Long]].decodeUnsafe(p))
                      case ThriftBinaryCodec.booleanType =>
                        regs.setBoolean(off, codec.asInstanceOf[ThriftBinaryCodec[Boolean]].decodeUnsafe(p))
                      case ThriftBinaryCodec.doubleType =>
                        regs.setDouble(off, codec.asInstanceOf[ThriftBinaryCodec[Double]].decodeUnsafe(p))
                      case ThriftBinaryCodec.byteType =>
                        regs.setByte(off, codec.asInstanceOf[ThriftBinaryCodec[Byte]].decodeUnsafe(p))
                      case ThriftBinaryCodec.shortType =>
                        regs.setShort(off, codec.asInstanceOf[ThriftBinaryCodec[Short]].decodeUnsafe(p))
                      case ThriftBinaryCodec.floatType =>
                        regs.setFloat(off, codec.asInstanceOf[ThriftBinaryCodec[Float]].decodeUnsafe(p))
                      case ThriftBinaryCodec.charType =>
                        regs.setChar(off, codec.asInstanceOf[ThriftBinaryCodec[Char]].decodeUnsafe(p))
                      case _ =>
                        codec.asInstanceOf[ThriftBinaryCodec[Unit]].decodeUnsafe(p)
                    }
                  } else {
                    org.apache.thrift.protocol.TProtocolUtil.skip(p, field.`type`)
                  }
                  p.readFieldEnd()
                }
              }
              p.readStructEnd()
              constructor.construct(regs, 0)
            }
          }
        }
      } else {
        record.recordBinding.asInstanceOf[BindingInstance[ThriftBinaryCodec, ?, A]].instance.force
      }

    } else if (reflect.isSequence) {
      val sequence = reflect.asSequenceUnknown.get.sequence
      if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
        type Col[x] = Any
        type Elem   = Any

        val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
        val elementCodec = deriveCodec(sequence.element).asInstanceOf[ThriftBinaryCodec[Elem]]

        new ThriftBinaryCodec[Col[Elem]](ThriftBinaryCodec.objectType) {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor

          def encode(value: Col[Elem], p: TProtocol): Unit = {
            val size = deconstructor.size(value)
            p.writeListBegin(new TList(getTType(sequence.element), size))
            val it = deconstructor.deconstruct(value)
            while (it.hasNext) {
              elementCodec.encode(it.next(), p)
            }
            p.writeListEnd()
          }

          def decodeUnsafe(p: TProtocol): Col[Elem] = {
            val list    = p.readListBegin()
            val builder = constructor.newObjectBuilder[Elem](list.size)
            var i       = 0
            while (i < list.size) {
              val elem = elementCodec.decodeUnsafe(p)
              constructor.addObject(builder, elem)
              i += 1
            }
            p.readListEnd()
            constructor.resultObject(builder)
          }
        }.asInstanceOf[ThriftBinaryCodec[A]]
      } else sequence.seqBinding.asInstanceOf[BindingInstance[ThriftBinaryCodec, ?, A]].instance.force

    } else if (reflect.isMap) {
      val map = reflect.asMapUnknown.get.map
      if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
        type MapStr[k, v] = Any
        type Key          = Any
        type Value        = Any

        val binding    = map.mapBinding.asInstanceOf[Binding.Map[MapStr, Key, Value]]
        val keyCodec   = deriveCodec(map.key).asInstanceOf[ThriftBinaryCodec[Key]]
        val valueCodec = deriveCodec(map.value).asInstanceOf[ThriftBinaryCodec[Value]]

        new ThriftBinaryCodec[MapStr[Key, Value]](ThriftBinaryCodec.objectType) {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor

          def encode(value: MapStr[Key, Value], p: TProtocol): Unit = {
            val size = deconstructor.size(value)
            p.writeMapBegin(new TMap(getTType(map.key), getTType(map.value), size))
            val it = deconstructor.deconstruct(value)
            while (it.hasNext) {
              val entry = it.next()
              keyCodec.encode(deconstructor.getKey(entry), p)
              valueCodec.encode(deconstructor.getValue(entry), p)
            }
            p.writeMapEnd()
          }

          def decodeUnsafe(p: TProtocol): MapStr[Key, Value] = {
            val tmap    = p.readMapBegin()
            val builder = constructor.newObjectBuilder[Key, Value](tmap.size)
            var i       = 0
            while (i < tmap.size) {
              val key   = keyCodec.decodeUnsafe(p)
              val value = valueCodec.decodeUnsafe(p)
              constructor.addObject(builder, key, value)
              i += 1
            }
            p.readMapEnd()
            constructor.resultObject(builder)
          }
        }.asInstanceOf[ThriftBinaryCodec[A]]
      } else map.mapBinding.asInstanceOf[BindingInstance[ThriftBinaryCodec, ?, A]].instance.force

    } else if (reflect.isVariant) {
      val variant = reflect.asVariant.get
      if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
        val binding  = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
        val cases    = variant.cases
        val typeName = variant.typeName

        var codecs: Array[ThriftBinaryCodec[?]] = recursiveRecordCache.get.get(typeName)

        if (codecs eq null) {
          val len = cases.length
          codecs = new Array[ThriftBinaryCodec[?]](len)
          recursiveRecordCache.get.put(typeName, codecs)

          var idx = 0
          while (idx < len) {
            codecs(idx) = deriveCodec(cases(idx).value)
            idx += 1
          }

          new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
            private[this] val discriminator = binding.discriminator
            private[this] val caseCodecs    = codecs

            def encode(value: A, p: TProtocol): Unit = {
              val idx = discriminator.discriminate(value)
              p.writeStructBegin(new TStruct(variant.typeName.name))
              val caseName = cases(idx).name
              p.writeFieldBegin(new TField(caseName, getTType(cases(idx).value), (idx + 1).toShort))
              caseCodecs(idx).asInstanceOf[ThriftBinaryCodec[A]].encode(value, p)
              p.writeFieldEnd()
              p.writeFieldStop()
              p.writeStructEnd()
            }

            def decodeUnsafe(p: TProtocol): A = {
              p.readStructBegin()
              var result: Option[A] = None
              var done              = false
              val len               = caseCodecs.length
              while (!done) {
                val field = p.readFieldBegin()
                if (field.`type` == TType.STOP) done = true
                else {
                  val id = field.id - 1
                  if (id >= 0 && id < len) {
                    result = Some(caseCodecs(id).asInstanceOf[ThriftBinaryCodec[A]].decodeUnsafe(p))
                  } else {
                    org.apache.thrift.protocol.TProtocolUtil.skip(p, field.`type`)
                  }
                  p.readFieldEnd()
                }
              }
              p.readStructEnd()
              result.getOrElse(throw new RuntimeException("Variant not set"))
            }
          }
        } else {
          new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
            private[this] val discriminator = binding.discriminator
            private[this] val caseCodecs    = codecs

            def encode(value: A, p: TProtocol): Unit = {
              val idx = discriminator.discriminate(value)
              p.writeStructBegin(new TStruct(variant.typeName.name))
              val caseName = cases(idx).name
              p.writeFieldBegin(new TField(caseName, getTType(cases(idx).value), (idx + 1).toShort))
              caseCodecs(idx).asInstanceOf[ThriftBinaryCodec[A]].encode(value, p)
              p.writeFieldEnd()
              p.writeFieldStop()
              p.writeStructEnd()
            }

            def decodeUnsafe(p: TProtocol): A = {
              p.readStructBegin()
              var result: Option[A] = None
              var done              = false
              val len               = caseCodecs.length
              while (!done) {
                val field = p.readFieldBegin()
                if (field.`type` == TType.STOP) done = true
                else {
                  val id = field.id - 1
                  if (id >= 0 && id < len) {
                    result = Some(caseCodecs(id).asInstanceOf[ThriftBinaryCodec[A]].decodeUnsafe(p))
                  } else {
                    org.apache.thrift.protocol.TProtocolUtil.skip(p, field.`type`)
                  }
                  p.readFieldEnd()
                }
              }
              p.readStructEnd()
              result.getOrElse(throw new RuntimeException("Variant not set"))
            }
          }
        }
      } else variant.variantBinding.asInstanceOf[BindingInstance[ThriftBinaryCodec, ?, A]].instance.force

    } else if (reflect.isDynamic) {
      deriveCodec(dynamicValueSchema.reflect).asInstanceOf[ThriftBinaryCodec[A]]
    } else {
      throw new UnsupportedOperationException(s"Unsupported reflect type: $reflect")
    }
  }

  private lazy val dynamicValueSchema: Schema[DynamicValue] = Schema.derived[DynamicValue]

  private def derivePrimitiveCodec[A](primitiveType: PrimitiveType[A]): ThriftBinaryCodec[A] =
    primitiveType match {
      case PrimitiveType.Unit =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.unitType) {
          def encode(value: A, p: TProtocol): Unit = ()
          def decodeUnsafe(p: TProtocol): A        = ().asInstanceOf[A]
        }
      case PrimitiveType.Boolean(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.booleanType) {
          def encode(value: A, p: TProtocol): Unit = p.writeBool(value.asInstanceOf[Boolean])
          def decodeUnsafe(p: TProtocol): A        = p.readBool().asInstanceOf[A]
        }
      case PrimitiveType.Byte(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.byteType) {
          def encode(value: A, p: TProtocol): Unit = p.writeByte(value.asInstanceOf[Byte])
          def decodeUnsafe(p: TProtocol): A        = p.readByte().asInstanceOf[A]
        }
      case PrimitiveType.Short(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.shortType) {
          def encode(value: A, p: TProtocol): Unit = p.writeI16(value.asInstanceOf[Short])
          def decodeUnsafe(p: TProtocol): A        = p.readI16().asInstanceOf[A]
        }
      case PrimitiveType.Int(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.intType) {
          def encode(value: A, p: TProtocol): Unit = p.writeI32(value.asInstanceOf[Int])
          def decodeUnsafe(p: TProtocol): A        = p.readI32().asInstanceOf[A]
        }
      case PrimitiveType.Long(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.longType) {
          def encode(value: A, p: TProtocol): Unit = p.writeI64(value.asInstanceOf[Long])
          def decodeUnsafe(p: TProtocol): A        = p.readI64().asInstanceOf[A]
        }
      case PrimitiveType.Double(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.doubleType) {
          def encode(value: A, p: TProtocol): Unit = p.writeDouble(value.asInstanceOf[Double])
          def decodeUnsafe(p: TProtocol): A        = p.readDouble().asInstanceOf[A]
        }
      case PrimitiveType.Float(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.floatType) {
          def encode(value: A, p: TProtocol): Unit = p.writeDouble(value.asInstanceOf[Float].toDouble)
          def decodeUnsafe(p: TProtocol): A        = p.readDouble().toFloat.asInstanceOf[A]
        }
      case PrimitiveType.String(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.asInstanceOf[String])
          def decodeUnsafe(p: TProtocol): A        = p.readString().asInstanceOf[A]
        }
      case PrimitiveType.BigInt(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = BigInt(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.BigDecimal(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = BigDecimal(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.UUID(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.util.UUID.fromString(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.Currency(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.util.Currency.getInstance(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.DayOfWeek(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.intType) {
          def encode(value: A, p: TProtocol): Unit = p.writeI32(value.asInstanceOf[java.time.DayOfWeek].getValue)
          def decodeUnsafe(p: TProtocol): A        = java.time.DayOfWeek.of(p.readI32()).asInstanceOf[A]
        }
      case PrimitiveType.Month(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.intType) {
          def encode(value: A, p: TProtocol): Unit = p.writeI32(value.asInstanceOf[java.time.Month].getValue)
          def decodeUnsafe(p: TProtocol): A        = java.time.Month.of(p.readI32()).asInstanceOf[A]
        }
      case PrimitiveType.Year(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.intType) {
          def encode(value: A, p: TProtocol): Unit = p.writeI32(value.asInstanceOf[java.time.Year].getValue)
          def decodeUnsafe(p: TProtocol): A        = java.time.Year.of(p.readI32()).asInstanceOf[A]
        }
      case PrimitiveType.Duration(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.Duration.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.Period(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.Period.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.Instant(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.Instant.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.LocalDate(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.LocalDate.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.LocalTime(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.LocalTime.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.LocalDateTime(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.LocalDateTime.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.OffsetTime(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.OffsetTime.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.OffsetDateTime(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.OffsetDateTime.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.ZonedDateTime(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.ZonedDateTime.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.ZoneId(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.ZoneId.of(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.ZoneOffset(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.ZoneOffset.of(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.MonthDay(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.MonthDay.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.YearMonth(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = java.time.YearMonth.parse(p.readString()).asInstanceOf[A]
        }
      case PrimitiveType.Char(_) =>
        new ThriftBinaryCodec[A](ThriftBinaryCodec.objectType) {
          def encode(value: A, p: TProtocol): Unit = p.writeString(value.toString)
          def decodeUnsafe(p: TProtocol): A        = p.readString().head.asInstanceOf[A]
        }
      case _ => throw new UnsupportedOperationException(s"Unsupported primitive: $primitiveType")
    }

  private def getTType[F[_, _]](reflect: Reflect[F, ?]): Byte =
    if (reflect.isPrimitive) {
      val primitive = reflect.asPrimitive.get
      primitive.primitiveType match {
        case PrimitiveType.Boolean(_) => TType.BOOL
        case PrimitiveType.Byte(_)    => TType.BYTE
        case PrimitiveType.Short(_)   => TType.I16
        case PrimitiveType.Int(_)     => TType.I32
        case PrimitiveType.Long(_)    => TType.I64
        case PrimitiveType.Double(_)  => TType.DOUBLE
        case PrimitiveType.Float(_)   => TType.DOUBLE
        case PrimitiveType.String(_)  => TType.STRING
        case PrimitiveType.Unit       => TType.VOID
        // Maps new primitives to most appropriate Thrift type
        case PrimitiveType.BigInt(_) | PrimitiveType.BigDecimal(_) | PrimitiveType.UUID(_) |
            PrimitiveType.Currency(_) =>
          TType.STRING
        case PrimitiveType.DayOfWeek(_) | PrimitiveType.Month(_) | PrimitiveType.Year(_) => TType.I32
        case PrimitiveType.Duration(_) | PrimitiveType.Period(_)                         => TType.STRING
        case PrimitiveType.Instant(_) | PrimitiveType.LocalDate(_) | PrimitiveType.LocalTime(_) |
            PrimitiveType.LocalDateTime(_) | PrimitiveType.OffsetTime(_) | PrimitiveType.OffsetDateTime(_) |
            PrimitiveType.ZonedDateTime(_) | PrimitiveType.ZoneId(_) | PrimitiveType.ZoneOffset(_) |
            PrimitiveType.MonthDay(_) | PrimitiveType.YearMonth(_) =>
          TType.STRING
        case PrimitiveType.Char(_) => TType.STRING
        case _                     => TType.STRING
      }
    } else if (reflect.isRecord) {
      TType.STRUCT
    } else if (reflect.isSequence) {
      TType.LIST
    } else if (reflect.isMap) {
      TType.MAP
    } else if (reflect.isVariant) {
      TType.STRUCT
    } else if (reflect.isDynamic) {
      TType.STRUCT
    } else {
      throw new UnsupportedOperationException(s"Unsupported reflect type: $reflect")
    }

}
