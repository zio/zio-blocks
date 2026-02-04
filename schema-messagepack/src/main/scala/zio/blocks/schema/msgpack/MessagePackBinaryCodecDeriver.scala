package zio.blocks.schema.msgpack

import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object MessagePackBinaryCodecDeriver extends Deriver[MessagePackBinaryCodec] {
  import MessagePackBinaryCodec._

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: zio.blocks.typeid.TypeId[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[MessagePackBinaryCodec[A]] =
    Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeId, binding, doc, modifiers)))

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: zio.blocks.typeid.TypeId[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] = Lazy {
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
    typeId: zio.blocks.typeid.TypeId[A],
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] = Lazy {
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
    typeId: zio.blocks.typeid.TypeId[C[A]],
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[C[A]]] = Lazy {
    deriveCodec(
      new Reflect.Sequence(element.asInstanceOf[Reflect[Binding, A]], typeId, binding, doc, modifiers)
    )
  }

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: zio.blocks.typeid.TypeId[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[M[K, V]]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[DynamicValue]] =
    Lazy(deriveCodec(new Reflect.Dynamic(binding, zio.blocks.typeid.TypeId.of[DynamicValue], doc, modifiers)))

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: zio.blocks.typeid.TypeId[A],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[MessagePackBinaryCodec[A]] = Lazy {
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
    super.instanceOverrides
  }

  type Elem
  type Key
  type Value
  type Col[_]
  type Map[_, _]

  private[this] val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[zio.blocks.typeid.TypeId[?], Array[MessagePackFieldInfo]]] {
      override def initialValue: java.util.HashMap[zio.blocks.typeid.TypeId[?], Array[MessagePackFieldInfo]] =
        new java.util.HashMap
    }

  private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): MessagePackBinaryCodec[A] = {
    if (reflect.isPrimitive) {
      derivePrimitiveCodec(reflect.asPrimitive.get)
    } else if (reflect.isVariant) {
      deriveVariantCodec(reflect.asVariant.get)
    } else if (reflect.isSequence) {
      deriveSequenceCodec(reflect.asSequenceUnknown.get.sequence)
    } else if (reflect.isMap) {
      deriveMapCodec(reflect.asMapUnknown.get.map)
    } else if (reflect.isRecord) {
      deriveRecordCodec(reflect.asRecord.get)
    } else if (reflect.isWrapper) {
      deriveWrapperCodec(reflect.asWrapperUnknown.get.wrapper)
    } else {
      deriveDynamicCodec(reflect.asDynamic.get)
    }
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

  private[this] def deriveVariantCodec[F[_, _], A](
    variant: Reflect.Variant[F, A]
  ): MessagePackBinaryCodec[A] =
    if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
      val typeId = variant.typeId
      val cases  = variant.cases
      if (typeId.isEither && cases.length == 2) {
        val leftInner  = cases(0).value.asRecord.flatMap(r => r.fields.headOption.map(_.value))
        val rightInner = cases(1).value.asRecord.flatMap(r => r.fields.headOption.map(_.value))
        if (leftInner.isDefined && rightInner.isDefined) {
          deriveEitherCodec(variant, leftInner.get, rightInner.get)
        } else {
          deriveGenericVariantCodec(variant)
        }
      } else if (variant.optionInnerType.isDefined) {
        deriveOptionCodec(variant)
      } else {
        deriveGenericVariantCodec(variant)
      }
    } else {
      variant.variantBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance.force
    }

  private[this] def deriveGenericVariantCodec[F[_, _], A](
    variant: Reflect.Variant[F, A]
  ): MessagePackBinaryCodec[A] = {
    val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
    val cases   = variant.cases
    val len     = cases.length
    val codecs  = new Array[MessagePackBinaryCodec[?]](len)
    var idx     = 0
    while (idx < len) {
      codecs(idx) = deriveCodec(cases(idx).value)
      idx += 1
    }
    new MessagePackBinaryCodec[A]() {
      private[this] val discriminator = binding.discriminator
      private[this] val caseCodecs    = codecs

      def decodeValue(in: MessagePackReader): A = {
        val idx = in.readIntValue()
        if (idx >= 0 && idx < caseCodecs.length) {
          try caseCodecs(idx).asInstanceOf[MessagePackBinaryCodec[A]].decodeValue(in)
          catch {
            case error if NonFatal(error) =>
              decodeError(new DynamicOptic.Node.Case(cases(idx).name), error)
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

  private[this] def deriveEitherCodec[F[_, _], A](
    variant: Reflect.Variant[F, A],
    leftInner: Reflect[F, ?],
    rightInner: Reflect[F, ?]
  ): MessagePackBinaryCodec[A] = {
    val binding    = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
    val leftCodec  = deriveCodec(leftInner)
    val rightCodec = deriveCodec(rightInner)
    new MessagePackBinaryCodec[A]() {
      private[this] val discriminator = binding.discriminator
      private[this] val lCodec        = leftCodec
      private[this] val rCodec        = rightCodec

      def decodeValue(in: MessagePackReader): A = {
        val mapSize = in.readMapHeader()
        if (mapSize != 1) in.decodeError(s"Expected Either map of 1, got: $mapSize")
        val key = in.readString()
        if (key == "left") {
          try {
            val innerValue = lCodec.decodeValue(in)
            Left(innerValue).asInstanceOf[A]
          } catch {
            case error if NonFatal(error) =>
              decodeError(new DynamicOptic.Node.Case("Left"), error)
          }
        } else if (key == "right") {
          try {
            val innerValue = rCodec.decodeValue(in)
            Right(innerValue).asInstanceOf[A]
          } catch {
            case error if NonFatal(error) =>
              decodeError(new DynamicOptic.Node.Case("Right"), error)
          }
        } else in.decodeError(s"Expected 'left' or 'right', got: $key")
      }

      def encodeValue(value: A, out: MessagePackWriter): Unit = {
        val idx = discriminator.discriminate(value)
        out.writeMapHeader(1)
        if (idx == 0) {
          out.writeString("left")
          val left = value.asInstanceOf[Left[?, ?]]
          lCodec.asInstanceOf[MessagePackBinaryCodec[Any]].encodeValue(left.value, out)
        } else {
          out.writeString("right")
          val right = value.asInstanceOf[Right[?, ?]]
          rCodec.asInstanceOf[MessagePackBinaryCodec[Any]].encodeValue(right.value, out)
        }
      }
    }
  }

  private[this] def deriveOptionCodec[F[_, _], A](
    variant: Reflect.Variant[F, A]
  ): MessagePackBinaryCodec[A] = {
    val binding     = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
    val someRecord  = variant.cases(1).value.asRecord.get
    val someBinding = someRecord.recordBinding match {
      case b: Binding.Record[?]         => b.asInstanceOf[Binding.Record[A]]
      case bi: BindingInstance[?, ?, ?] => bi.binding.asInstanceOf[Binding.Record[A]]
    }
    val innerReflect = variant.optionInnerType.get
    val innerCodec   = deriveCodec(innerReflect)
    new MessagePackBinaryCodec[A]() {
      private[this] val discriminator = binding.discriminator
      private[this] val someCons      = someBinding.constructor
      private[this] val someDecons    = someBinding.deconstructor
      private[this] val elementCodec  = innerCodec
      private[this] val usedRegs      = someBinding.constructor.usedRegisters

      def decodeValue(in: MessagePackReader): A = {
        val arrLen = in.readArrayHeader()
        if (arrLen == 0) {
          None.asInstanceOf[A]
        } else if (arrLen == 1) {
          val regs = Registers(usedRegs)
          try {
            val innerValue = elementCodec.decodeValue(in)
            elementCodec.valueType match {
              case 0 => regs.setObject(0, innerValue.asInstanceOf[AnyRef])
              case 1 => regs.setInt(0, innerValue.asInstanceOf[Int])
              case 2 => regs.setLong(0, innerValue.asInstanceOf[Long])
              case 3 => regs.setFloat(0, innerValue.asInstanceOf[Float])
              case 4 => regs.setDouble(0, innerValue.asInstanceOf[Double])
              case 5 => regs.setBoolean(0, innerValue.asInstanceOf[Boolean])
              case 6 => regs.setByte(0, innerValue.asInstanceOf[Byte])
              case 7 => regs.setChar(0, innerValue.asInstanceOf[Char])
              case 8 => regs.setShort(0, innerValue.asInstanceOf[Short])
              case _ => regs.setObject(0, innerValue.asInstanceOf[AnyRef])
            }
            someCons.construct(regs, 0).asInstanceOf[A]
          } catch {
            case error if NonFatal(error) =>
              decodeError(new DynamicOptic.Node.Case("Some"), error)
          }
        } else in.decodeError(s"Expected Option array of 0 or 1, got: $arrLen")
      }

      def encodeValue(value: A, out: MessagePackWriter): Unit = {
        val idx = discriminator.discriminate(value)
        if (idx == 0) {
          out.writeArrayHeader(0)
        } else {
          out.writeArrayHeader(1)
          val regs = Registers(usedRegs)
          someDecons.deconstruct(regs, 0, value)
          elementCodec.valueType match {
            case 0 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Any]].encodeValue(regs.getObject(0), out)
            case 1 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Int]].encodeValue(regs.getInt(0), out)
            case 2 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Long]].encodeValue(regs.getLong(0), out)
            case 3 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Float]].encodeValue(regs.getFloat(0), out)
            case 4 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Double]].encodeValue(regs.getDouble(0), out)
            case 5 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Boolean]].encodeValue(regs.getBoolean(0), out)
            case 6 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Byte]].encodeValue(regs.getByte(0), out)
            case 7 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Char]].encodeValue(regs.getChar(0), out)
            case 8 => elementCodec.asInstanceOf[MessagePackBinaryCodec[Short]].encodeValue(regs.getShort(0), out)
            case _ => elementCodec.asInstanceOf[MessagePackBinaryCodec[Any]].encodeValue(regs.getObject(0), out)
          }
        }
      }
    }
  }

  private[this] def deriveSequenceCodec[F[_, _], A, C[_]](
    sequence: Reflect.Sequence[F, A, C]
  ): MessagePackBinaryCodec[C[A]] = {
    if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
      val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
      val codec        = deriveCodec(sequence.element).asInstanceOf[MessagePackBinaryCodec[Elem]]
      val elemClassTag = sequence.elemClassTag.asInstanceOf[ClassTag[Elem]]
      new MessagePackBinaryCodec[Col[Elem]]() {
        private[this] val deconstructor                     = binding.deconstructor
        private[this] val constructor                       = binding.constructor
        private[this] val elementCodec                      = codec
        private[this] implicit val classTag: ClassTag[Elem] = elemClassTag

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
    } else sequence.seqBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, C[A]]].instance.force
  }.asInstanceOf[MessagePackBinaryCodec[C[A]]]

  private[this] def deriveMapCodec[F[_, _], K, V, M[_, _]](
    map: Reflect.Map[F, K, V, M]
  ): MessagePackBinaryCodec[M[K, V]] = {
    if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
      val binding    = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
      val keyCodec   = deriveCodec(map.key).asInstanceOf[MessagePackBinaryCodec[Key]]
      val valueCodec = deriveCodec(map.value).asInstanceOf[MessagePackBinaryCodec[Value]]
      new MessagePackBinaryCodec[Map[Key, Value]]() {
        private[this] val deconstructor = binding.deconstructor
        private[this] val constructor   = binding.constructor
        private[this] val kCodec        = keyCodec
        private[this] val vCodec        = valueCodec
        private[this] val keyReflect    = map.key.asInstanceOf[Reflect.Bound[Key]]

        def decodeValue(in: MessagePackReader): Map[Key, Value] = {
          val size    = in.readMapHeader()
          val builder = constructor.newObjectBuilder[Key, Value](Math.min(size, 16))
          var idx     = 0
          while (idx < size) {
            val k =
              try kCodec.decodeValue(in)
              catch {
                case error if NonFatal(error) =>
                  decodeError(new DynamicOptic.Node.AtIndex(idx), error)
              }
            val v =
              try vCodec.decodeValue(in)
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
            val k  = deconstructor.getKey(kv)
            val v  = deconstructor.getValue(kv)
            kCodec.encodeValue(k, out)
            vCodec.encodeValue(v, out)
          }
        }
      }
    } else map.mapBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, M[K, V]]].instance.force
  }.asInstanceOf[MessagePackBinaryCodec[M[K, V]]]

  private[this] def deriveRecordCodec[F[_, _], A](
    record: Reflect.Record[F, A]
  ): MessagePackBinaryCodec[A] =
    if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
      val binding      = record.recordBinding.asInstanceOf[Binding.Record[A]]
      val fields       = record.fields
      val len          = fields.length
      val typeId       = record.typeId
      val isRecursive  = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
      var infos        = if (isRecursive) recursiveRecordCache.get.get(typeId) else null
      val deriveCodecs = infos eq null

      if (deriveCodecs) {
        infos = new Array[MessagePackFieldInfo](len)
        var idx = 0
        while (idx < len) {
          val field = fields(idx)
          infos(idx) = new MessagePackFieldInfo(
            DynamicOptic.Node.Field(field.name),
            idx
          )
          infos(idx).setName(field.name)
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
          val codec = deriveCodec(field.value)
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
        private[this] val deconstructor = binding.deconstructor
        private[this] val constructor   = binding.constructor
        private[this] val fieldInfos    = infos
        private[this] val fieldLookup   = fieldNameMap
        private[this] var usedRegisters = offset

        def decodeValue(in: MessagePackReader): A = {
          val fieldLen = fieldInfos.length
          if (fieldLen > 0 && usedRegisters == 0) {
            usedRegisters = fieldInfos(fieldLen - 1).usedRegisters
          }
          val regs    = Registers(usedRegisters)
          val mapSize = in.readMapHeader()
          if (mapSize != fieldLen)
            in.decodeError(s"Expected $fieldLen fields, got: $mapSize")
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
              case error if NonFatal(error) =>
                decodeError(fieldInfo.span, error)
            }
            idx += 1
          }
          constructor.construct(regs, 0)
        }

        def encodeValue(value: A, out: MessagePackWriter): Unit = {
          val fieldLen = fieldInfos.length
          if (fieldLen > 0 && usedRegisters == 0) {
            usedRegisters = fieldInfos(fieldLen - 1).usedRegisters
          }
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
    } else record.recordBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance.force

  private[this] def deriveWrapperCodec[F[_, _], A, B](
    wrapper: Reflect.Wrapper[F, A, B]
  ): MessagePackBinaryCodec[A] =
    if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
      val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, B]]
      val codec   = deriveCodec(wrapper.wrapped)
      new MessagePackBinaryCodec[A](wrapper.underlyingPrimitiveType.fold(objectType) {
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
        private[this] val wrap         = binding.wrap
        private[this] val unwrap       = binding.unwrap
        private[this] val wrappedCodec = codec

        def decodeValue(in: MessagePackReader): A =
          try wrap(wrappedCodec.decodeValue(in))
          catch {
            case error if NonFatal(error) => decodeError(DynamicOptic.Node.Wrapped, error)
          }

        def encodeValue(value: A, out: MessagePackWriter): Unit =
          wrappedCodec.encodeValue(unwrap(value), out)
      }
    } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, A]].instance.force

  private[this] def deriveDynamicCodec[F[_, _]](
    dynamic: Reflect.Dynamic[F]
  ): MessagePackBinaryCodec[DynamicValue] =
    if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
    else dynamic.dynamicBinding.asInstanceOf[BindingInstance[MessagePackBinaryCodec, ?, DynamicValue]].instance.force

  private[this] val dynamicValueCodec: MessagePackBinaryCodec[DynamicValue] =
    new MessagePackBinaryCodec[DynamicValue]() {
      def decodeValue(in: MessagePackReader): DynamicValue = decodeDynamic(in)

      def encodeValue(value: DynamicValue, out: MessagePackWriter): Unit = encodeDynamic(value, out)

      private def decodeDynamic(in: MessagePackReader): DynamicValue = {
        val b = in.peekType & 0xff
        if ((b & 0x80) == 0 || (b & 0xe0) == 0xe0) {
          DynamicValue.Primitive(PrimitiveValue.Long(in.readLongValue()))
        } else if ((b & 0xe0) == 0xa0 || b == 0xd9 || b == 0xda || b == 0xdb) {
          DynamicValue.Primitive(PrimitiveValue.String(in.readString()))
        } else if ((b & 0xf0) == 0x90 || b == 0xdc || b == 0xdd) {
          val len = in.readArrayHeader()
          if (len < 0) in.decodeError("Array length exceeds maximum (2GB)")
          val builder = zio.blocks.chunk.ChunkBuilder.make[DynamicValue]()
          var idx     = 0
          while (idx < len) {
            builder.addOne(decodeDynamic(in))
            idx += 1
          }
          DynamicValue.Sequence(builder.result())
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
            DynamicValue.Record(Chunk.from(fields))
          } else {
            DynamicValue.Map(Chunk.from(entries))
          }
        } else
          b match {
            case 0xc0                                                  => in.readNil(); DynamicValue.Null
            case 0xc2                                                  => DynamicValue.Primitive(PrimitiveValue.Boolean(in.readBoolean()))
            case 0xc3                                                  => DynamicValue.Primitive(PrimitiveValue.Boolean(in.readBoolean()))
            case 0xca                                                  => DynamicValue.Primitive(PrimitiveValue.Float(in.readFloatValue()))
            case 0xcb                                                  => DynamicValue.Primitive(PrimitiveValue.Double(in.readDoubleValue()))
            case 0xcc | 0xcd | 0xce | 0xcf | 0xd0 | 0xd1 | 0xd2 | 0xd3 =>
              DynamicValue.Primitive(PrimitiveValue.Long(in.readLongValue()))
            case 0xc4 | 0xc5 | 0xc6 =>
              val bytes = in.readBinary()
              DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(bytes)))
            case _ => in.decodeError(s"Unsupported MessagePack type for DynamicValue: $b")
          }
      }

      private def encodeDynamic(value: DynamicValue, out: MessagePackWriter): Unit = value match {
        case DynamicValue.Primitive(p)   => encodePrimitive(p, out)
        case DynamicValue.Record(fields) =>
          out.writeMapHeader(fields.size)
          var i = 0
          while (i < fields.size) {
            val (k, v) = fields(i)
            out.writeString(k)
            encodeDynamic(v, out)
            i += 1
          }
        case DynamicValue.Variant(caseName, innerValue) =>
          out.writeMapHeader(2)
          out.writeString("_case")
          out.writeString(caseName)
          out.writeString("_value")
          encodeDynamic(innerValue, out)
        case DynamicValue.Sequence(elements) =>
          out.writeArrayHeader(elements.size)
          var i = 0
          while (i < elements.size) {
            encodeDynamic(elements(i), out)
            i += 1
          }
        case DynamicValue.Map(entries) =>
          out.writeMapHeader(entries.size)
          var i = 0
          while (i < entries.size) {
            val (k, v) = entries(i)
            encodeDynamic(k, out)
            encodeDynamic(v, out)
            i += 1
          }
        case DynamicValue.Null =>
          out.writeNil()
      }

      private def encodePrimitive(p: PrimitiveValue, out: MessagePackWriter): Unit = p match {
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
