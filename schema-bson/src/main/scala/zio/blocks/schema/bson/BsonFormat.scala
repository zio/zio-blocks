package zio.blocks.schema.bson

import org.bson.{BsonBinary, BsonBinarySubType, BsonReader, BsonWriter}
import org.bson.types.Decimal128
import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import java.nio.ByteBuffer

/**
 * BSON format for ZIO Schema serialization/deserialization.
 */
object BsonFormat extends BinaryFormat("application/bson", BsonBinaryCodecDeriver)

/**
 * Deriver for BSON binary codecs.
 */
object BsonBinaryCodecDeriver extends Deriver[BsonCodec] {

  // --- Type aliases for existential types ---
  type Elem
  type Key
  type Value
  type Wrapped
  type Col[_]
  type Map[_, _]
  type TC[_]

  // --- Primitive codecs ---
  private[this] val intCodec: BsonCodec[Int] = new BsonCodec[Int](BsonCodec.intType) {
    def decodeUnsafe(decoder: BsonReader): Int = decoder.readInt32()
    def encode(value: Int, encoder: BsonWriter): Unit = encoder.writeInt32(value)
  }

  private[this] val longCodec: BsonCodec[Long] = new BsonCodec[Long](BsonCodec.longType) {
    def decodeUnsafe(decoder: BsonReader): Long = decoder.readInt64()
    def encode(value: Long, encoder: BsonWriter): Unit = encoder.writeInt64(value)
  }

  private[this] val stringCodec: BsonCodec[String] = new BsonCodec[String](BsonCodec.stringType) {
    def decodeUnsafe(decoder: BsonReader): String = decoder.readString()
    def encode(value: String, encoder: BsonWriter): Unit = encoder.writeString(value)
  }

  private[this] val booleanCodec: BsonCodec[Boolean] = new BsonCodec[Boolean](BsonCodec.booleanType) {
    def decodeUnsafe(decoder: BsonReader): Boolean = decoder.readBoolean()
    def encode(value: Boolean, encoder: BsonWriter): Unit = encoder.writeBoolean(value)
  }

  private[this] val doubleCodec: BsonCodec[Double] = new BsonCodec[Double](BsonCodec.doubleType) {
    def decodeUnsafe(decoder: BsonReader): Double = decoder.readDouble()
    def encode(value: Double, encoder: BsonWriter): Unit = encoder.writeDouble(value)
  }

  private[this] val unitCodec: BsonCodec[Unit] = new BsonCodec[Unit](BsonCodec.unitType) {
    def decodeUnsafe(decoder: BsonReader): Unit = decoder.readNull()
    def encode(value: Unit, encoder: BsonWriter): Unit = encoder.writeNull()
  }

  private[this] val byteCodec: BsonCodec[Byte] = new BsonCodec[Byte](BsonCodec.byteType) {
    def decodeUnsafe(decoder: BsonReader): Byte = decoder.readInt32().toByte
    def encode(value: Byte, encoder: BsonWriter): Unit = encoder.writeInt32(value.toInt)
  }

  private[this] val shortCodec: BsonCodec[Short] = new BsonCodec[Short](BsonCodec.shortType) {
    def decodeUnsafe(decoder: BsonReader): Short = decoder.readInt32().toShort
    def encode(value: Short, encoder: BsonWriter): Unit = encoder.writeInt32(value.toInt)
  }

  private[this] val floatCodec: BsonCodec[Float] = new BsonCodec[Float](BsonCodec.floatType) {
    def decodeUnsafe(decoder: BsonReader): Float = decoder.readDouble().toFloat
    def encode(value: Float, encoder: BsonWriter): Unit = encoder.writeDouble(value.toDouble)
  }

  private[this] val charCodec: BsonCodec[Char] = new BsonCodec[Char](BsonCodec.charType) {
    def decodeUnsafe(decoder: BsonReader): Char = decoder.readString().head
    def encode(value: Char, encoder: BsonWriter): Unit = encoder.writeString(value.toString)
  }

  private[this] val bigIntCodec: BsonCodec[BigInt] = new BsonCodec[BigInt](BsonCodec.stringType) {
    def decodeUnsafe(decoder: BsonReader): BigInt = BigInt(decoder.readString())
    def encode(value: BigInt, encoder: BsonWriter): Unit = encoder.writeString(value.toString)
  }

  private[this] val bigDecimalCodec: BsonCodec[BigDecimal] = new BsonCodec[BigDecimal](BsonCodec.decimalType) {
    def decodeUnsafe(decoder: BsonReader): BigDecimal = BigDecimal(decoder.readDecimal128().bigDecimalValue())
    def encode(value: BigDecimal, encoder: BsonWriter): Unit = encoder.writeDecimal128(new Decimal128(value.underlying))
  }

  private[this] val instantCodec: BsonCodec[java.time.Instant] = new BsonCodec[java.time.Instant](BsonCodec.dateTimeType) {
    def decodeUnsafe(decoder: BsonReader): java.time.Instant = java.time.Instant.ofEpochMilli(decoder.readDateTime())
    def encode(value: java.time.Instant, encoder: BsonWriter): Unit = encoder.writeDateTime(value.toEpochMilli)
  }

  private[this] val uuidCodec: BsonCodec[java.util.UUID] = new BsonCodec[java.util.UUID](BsonCodec.binaryType) {
    def decodeUnsafe(decoder: BsonReader): java.util.UUID = {
      val bin = decoder.readBinaryData()
      val bb = ByteBuffer.wrap(bin.getData)
      new java.util.UUID(bb.getLong, bb.getLong)
    }
    def encode(value: java.util.UUID, encoder: BsonWriter): Unit = {
      val bb = ByteBuffer.allocate(16)
      bb.putLong(value.getMostSignificantBits)
      bb.putLong(value.getLeastSignificantBits)
      encoder.writeBinaryData(new BsonBinary(BsonBinarySubType.UUID_STANDARD, bb.array()))
    }
  }

  private[this] def stringWrapperCodec[A](parse: String => A, toStr: A => String): BsonCodec[A] =
    new BsonCodec[A](BsonCodec.stringType) {
      def decodeUnsafe(decoder: BsonReader): A = parse(decoder.readString())
      def encode(value: A, encoder: BsonWriter): Unit = encoder.writeString(toStr(value))
    }

  // --- Deriver method implementations ---

  override def derivePrimitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  ): Lazy[BsonCodec[A]] =
    Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[A]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[A]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[C[A]]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[M[K, V]]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[DynamicValue]] =
    Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeName: TypeName[A],
    wrapperPrimitiveType: Option[PrimitiveType[A]],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[A]] = Lazy {
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

  override def instanceOverrides: IndexedSeq[InstanceOverride] = IndexedSeq.empty

  // --- Core derivation logic ---

  private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): BsonCodec[A] = {
    if (reflect.isPrimitive) {
      val primitive = reflect.asPrimitive.get
      if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
        primitive.primitiveType match {
          case _: PrimitiveType.Unit.type      => unitCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Boolean        => booleanCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Byte           => byteCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Short          => shortCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Int            => intCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Long           => longCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Float          => floatCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Double         => doubleCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Char           => charCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.String         => stringCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.BigInt         => bigIntCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.BigDecimal     => bigDecimalCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Instant        => instantCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.UUID           => uuidCodec.asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.LocalDate      => stringWrapperCodec(java.time.LocalDate.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.LocalTime      => stringWrapperCodec(java.time.LocalTime.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.LocalDateTime  => stringWrapperCodec(java.time.LocalDateTime.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.OffsetDateTime => stringWrapperCodec(java.time.OffsetDateTime.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.OffsetTime     => stringWrapperCodec(java.time.OffsetTime.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.ZonedDateTime  => stringWrapperCodec(java.time.ZonedDateTime.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.ZoneId         => stringWrapperCodec(java.time.ZoneId.of, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.ZoneOffset     => stringWrapperCodec(java.time.ZoneOffset.of, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Duration       => stringWrapperCodec(java.time.Duration.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Period         => stringWrapperCodec(java.time.Period.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Year           => stringWrapperCodec(s => java.time.Year.of(s.toInt), _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.YearMonth      => stringWrapperCodec(java.time.YearMonth.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.MonthDay       => stringWrapperCodec(java.time.MonthDay.parse, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Month          => stringWrapperCodec(java.time.Month.valueOf, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.DayOfWeek      => stringWrapperCodec(java.time.DayOfWeek.valueOf, _.toString).asInstanceOf[BsonCodec[A]]
          case _: PrimitiveType.Currency       => stringWrapperCodec(java.util.Currency.getInstance, _.toString).asInstanceOf[BsonCodec[A]]
        }
      } else {
        primitive.primitiveBinding.asInstanceOf[BindingInstance[BsonCodec, ?, A]].instance.force
      }
    } else if (reflect.isRecord) {
      val record = reflect.asRecord.get
      if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
        val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]
        val fields = record.fields
        val len = fields.length
        
        // Pre-compute field codecs and offsets
        val fieldCodecs = new Array[BsonCodec[Any]](len)
        val fieldNames = new Array[String](len)
        val fieldOffsets = new Array[Long](len)
        var totalOffset = 0L
        var idx = 0
        while (idx < len) {
          val field = fields(idx)
          
          var name = field.name
          field.modifiers.foreach {
            case Modifier.rename(n) => name = n
            case _ =>
          }
          fieldNames(idx) = name
          
          val codec = deriveCodec(field.value).asInstanceOf[BsonCodec[Any]]
          fieldCodecs(idx) = codec
          fieldOffsets(idx) = totalOffset
          totalOffset = RegisterOffset.add(totalOffset, codec.valueOffset)
          idx += 1
        }
        
        new BsonCodec[A](BsonCodec.objectType) {
          private[this] val theConstructor = binding.constructor
          private[this] val theDeconstructor = binding.deconstructor
          private[this] val names = fieldNames
          private[this] val codecs = fieldCodecs
          private[this] val offsets = fieldOffsets
          private[this] val numFields = len
          private[this] val usedRegisters = totalOffset
          
          def decodeUnsafe(decoder: BsonReader): A = {
            import org.bson.BsonType
            val regs = Registers(usedRegisters)
            decoder.readStartDocument()
            while (decoder.readBsonType() != BsonType.END_OF_DOCUMENT) {
              val fieldName = decoder.readName()
              var matched = false
              var i = 0
              while (i < numFields && !matched) {
                if (names(i) == fieldName) {
                  val codec = codecs(i)
                  val offset = offsets(i)
                  (codec.valueType: @scala.annotation.switch) match {
                    case 0 | 10 | 11 | 12 | 13 | 9 => regs.setObject(offset, codec.asInstanceOf[BsonCodec[AnyRef]].decodeUnsafe(decoder)) // Objects, Strings, Dates, Decimals, Binary, Unit
                    case 1 => regs.setBoolean(offset, codec.asInstanceOf[BsonCodec[Boolean]].decodeUnsafe(decoder))
                    case 2 => regs.setByte(offset, codec.asInstanceOf[BsonCodec[Byte]].decodeUnsafe(decoder))
                    case 3 => regs.setChar(offset, codec.asInstanceOf[BsonCodec[Char]].decodeUnsafe(decoder))
                    case 4 => regs.setShort(offset, codec.asInstanceOf[BsonCodec[Short]].decodeUnsafe(decoder))
                    case 5 => regs.setFloat(offset, codec.asInstanceOf[BsonCodec[Float]].decodeUnsafe(decoder))
                    case 6 => regs.setInt(offset, codec.asInstanceOf[BsonCodec[Int]].decodeUnsafe(decoder))
                    case 7 => regs.setDouble(offset, codec.asInstanceOf[BsonCodec[Double]].decodeUnsafe(decoder))
                    case 8 => regs.setLong(offset, codec.asInstanceOf[BsonCodec[Long]].decodeUnsafe(decoder))
                    case _ => codec.decodeUnsafe(decoder)
                  }
                  matched = true
                }
                i += 1
              }
              if (!matched) decoder.skipValue()
            }
            decoder.readEndDocument()
            theConstructor.construct(regs, 0L)
          }
          
          def encode(value: A, encoder: BsonWriter): Unit = {
            val regs = Registers(usedRegisters)
            theDeconstructor.deconstruct(regs, 0L, value)
            
            encoder.writeStartDocument()
            var i = 0
            while (i < numFields) {
              val codec = codecs(i)
              val offset = offsets(i)
              encoder.writeName(names(i))
              (codec.valueType: @scala.annotation.switch) match {
                case 0 | 10 | 11 | 12 | 13 | 9 => codec.asInstanceOf[BsonCodec[AnyRef]].encode(regs.getObject(offset), encoder)
                case 1 => codec.asInstanceOf[BsonCodec[Boolean]].encode(regs.getBoolean(offset), encoder)
                case 2 => codec.asInstanceOf[BsonCodec[Byte]].encode(regs.getByte(offset), encoder)
                case 3 => codec.asInstanceOf[BsonCodec[Char]].encode(regs.getChar(offset), encoder)
                case 4 => codec.asInstanceOf[BsonCodec[Short]].encode(regs.getShort(offset), encoder)
                case 5 => codec.asInstanceOf[BsonCodec[Float]].encode(regs.getFloat(offset), encoder)
                case 6 => codec.asInstanceOf[BsonCodec[Int]].encode(regs.getInt(offset), encoder)
                case 7 => codec.asInstanceOf[BsonCodec[Double]].encode(regs.getDouble(offset), encoder)
                case 8 => codec.asInstanceOf[BsonCodec[Long]].encode(regs.getLong(offset), encoder)
                case _ => codec.encode(null.asInstanceOf[Any], encoder)
              }
              i += 1
            }
            encoder.writeEndDocument()
          }
        }.asInstanceOf[BsonCodec[A]]
      } else {
        new LazyBsonCodec(record.recordBinding.asInstanceOf[BindingInstance[BsonCodec, ?, A]].instance, BsonCodec.objectType)
      }
    } else if (reflect.isSequence) {
      val sequence = reflect.asSequenceUnknown.get.sequence
      if (sequence.seqBinding.isInstanceOf[Binding.Seq[?, ?]]) {
        val binding = sequence.seqBinding.asInstanceOf[Binding.Seq[[_] =>> Any, Any]]
        val elementCodec = deriveCodec(sequence.element).asInstanceOf[BsonCodec[Any]]
        
        new BsonCodec[A](BsonCodec.unitType) { // Use unitType to force wrapping in { "v": ... } so it's a valid root Document
          private[this] val constructor = binding.constructor
          private[this] val deconstructor = binding.deconstructor
          
          def decodeUnsafe(decoder: BsonReader): A = {
            decoder.readStartArray()
            val builder = constructor.newObjectBuilder[Any](10)
            while (decoder.readBsonType() != org.bson.BsonType.END_OF_DOCUMENT) {
              val item = elementCodec.decodeUnsafe(decoder)
              constructor.addObject[Any](builder, item)
            }
            decoder.readEndArray()
            constructor.resultObject[Any](builder).asInstanceOf[A]
          }
          
          def encode(value: A, encoder: BsonWriter): Unit = {
            encoder.writeStartArray()
            val it = deconstructor.deconstruct(value)
            while (it.hasNext) {
              elementCodec.encode(it.next(), encoder)
            }
            encoder.writeEndArray()
          }
        }
      } else {
        new LazyBsonCodec(sequence.seqBinding.asInstanceOf[BindingInstance[BsonCodec, ?, A]].instance, BsonCodec.unitType)
      }
    } else if (reflect.isMap) {
      val map = reflect.asMapUnknown.get.map
      if (map.mapBinding.isInstanceOf[Binding.Map[?, ?, ?]]) {
        val binding = map.mapBinding.asInstanceOf[Binding.Map[[_,_] =>> Any, Any, Any]]
        val valueCodec = deriveCodec(map.value).asInstanceOf[BsonCodec[Any]]

        new BsonCodec[A](BsonCodec.objectType) {
          private[this] val constructor = binding.constructor
          private[this] val deconstructor = binding.deconstructor

          def decodeUnsafe(decoder: BsonReader): A = {
            decoder.readStartDocument()
            val builder = constructor.newObjectBuilder[Any, Any](10)
            while (decoder.readBsonType() != org.bson.BsonType.END_OF_DOCUMENT) {
              val keyStr = decoder.readName()
              // Simplification: Assume String keys.
              val key = keyStr.asInstanceOf[Any]
              val value = valueCodec.decodeUnsafe(decoder)
              constructor.addObject[Any, Any](builder, key, value)
            }
            decoder.readEndDocument()
            constructor.resultObject[Any, Any](builder).asInstanceOf[A]
          }

          def encode(value: A, encoder: BsonWriter): Unit = {
            encoder.writeStartDocument()
            val it = deconstructor.deconstruct(value)
            while (it.hasNext) {
              val kv = it.next().asInstanceOf[deconstructor.KeyValue[Any, Any]]
              val key = deconstructor.getKey(kv)
              val v = deconstructor.getValue(kv)
              
              // Simplification: Use toString for keys to ensure valid BSON
              encoder.writeName(key.toString)
              valueCodec.encode(v, encoder)
            }
            encoder.writeEndDocument()
          }
        }
      } else {
        new LazyBsonCodec(map.mapBinding.asInstanceOf[BindingInstance[BsonCodec, ?, A]].instance, BsonCodec.objectType)
      }
    } else if (reflect.isVariant) {
      val variant = reflect.asVariant.get
      val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
      
      // Pre-calculate codecs and names for cases
      val cases = variant.cases
      val caseCodecs = new Array[BsonCodec[Any]](cases.length)
      val caseNames = new Array[String](cases.length)
      val nameToIndex = new java.util.HashMap[String, Int]()
      
      var i = 0
      while (i < cases.length) {
        val c = cases(i)
        caseCodecs(i) = deriveCodec(c.value).asInstanceOf[BsonCodec[Any]]
        
        var name = c.name
        c.modifiers.foreach {
          case Modifier.rename(n) => name = n
          case _ =>
        }
        caseNames(i) = name
        
        nameToIndex.put(name, i)
        i += 1
      }
      
      new BsonCodec[A](BsonCodec.objectType) {
        def decodeUnsafe(decoder: BsonReader): A = {
          decoder.readStartDocument()
          val name = decoder.readName()
          if (!nameToIndex.containsKey(name)) {
             // Fallback or error
             throw new IllegalArgumentException(s"Unknown variant case: $name") 
          }
          val idx = nameToIndex.get(name)
          val codec = caseCodecs(idx)
          val result = codec.decodeUnsafe(decoder)
          decoder.readEndDocument()
          result.asInstanceOf[A]
        }

        def encode(value: A, encoder: BsonWriter): Unit = {
          val idx = binding.discriminator.discriminate(value)
          if (idx < 0 || idx >= caseNames.length) {
             throw new IllegalArgumentException(s"Invalid variant index: $idx")
          }
          val name = caseNames(idx)
          val codec = caseCodecs(idx)
          
          encoder.writeStartDocument()
          encoder.writeName(name)
          codec.encode(value, encoder)
          encoder.writeEndDocument()
        }
      }
    } else if (reflect.typeName.name == "DynamicValue") {
      dynamicValueCodec.asInstanceOf[BsonCodec[A]]
    } else {
      // For other complex types, throw an error for now
      throw new UnsupportedOperationException(s"Complex type derivation not yet implemented: ${reflect.getClass.getName}")
    }
  }

  private lazy val dynamicValueCodec: BsonCodec[DynamicValue] = new BsonCodec[DynamicValue](BsonCodec.objectType) {
    import org.bson.BsonType
    import zio.blocks.schema.PrimitiveValue

    def encode(value: DynamicValue, encoder: BsonWriter): Unit = {
      encoder.writeStartDocument()
      value match {
        case DynamicValue.Primitive(p) =>
          encoder.writeString("type", "Primitive")
          encoder.writeName("value")
          encodePrimitive(p, encoder)
        case DynamicValue.Record(fields) =>
          encoder.writeString("type", "Record")
          encoder.writeName("value")
          encoder.writeStartDocument()
          fields.foreach { case (k, v) =>
            encoder.writeName(k)
            encode(v, encoder)
          }
          encoder.writeEndDocument()
        case DynamicValue.Sequence(elems) =>
          encoder.writeString("type", "Sequence")
          encoder.writeStartArray("value")
          elems.foreach(encode(_, encoder))
          encoder.writeEndArray()
        case DynamicValue.Variant(name, v) =>
          encoder.writeString("type", "Variant")
          encoder.writeString("caseName", name)
          encoder.writeName("value")
          encode(v, encoder)
        case _ =>
          throw new UnsupportedOperationException(s"DynamicValue type not supported: $value")
      }
      encoder.writeEndDocument()
    }

    private def encodePrimitive(p: PrimitiveValue, encoder: BsonWriter): Unit = p match {
      case PrimitiveValue.String(s) => encoder.writeString(s)
      case PrimitiveValue.Int(i) => encoder.writeInt32(i)
      case PrimitiveValue.Long(l) => encoder.writeInt64(l)
      case PrimitiveValue.Float(f) => encoder.writeDouble(f.toDouble)
      case PrimitiveValue.Double(d) => encoder.writeDouble(d)
      case PrimitiveValue.Boolean(b) => encoder.writeBoolean(b)
      case PrimitiveValue.Unit => encoder.writeNull()
      case _ => throw new UnsupportedOperationException(s"Primitive not supported: $p")
    }

    def decodeUnsafe(decoder: BsonReader): DynamicValue = {
      decoder.readStartDocument()
      val name = decoder.readName()
      if (name != "type") throw new IllegalArgumentException(s"Expected 'type' field, got: $name")
      val typeName = decoder.readString()
      
      val result = typeName match {
        case "Primitive" =>
          decoder.readName() // "value"
          val p = decodePrimitive(decoder)
          DynamicValue.Primitive(p)
        case "Record" =>
          decoder.readName() // "value"
          decoder.readStartDocument()
          val fields = scala.collection.mutable.ArrayBuffer[(String, DynamicValue)]()
          while (decoder.readBsonType() != BsonType.END_OF_DOCUMENT) {
            val key = decoder.readName()
            val value = decodeUnsafe(decoder)
            fields += (key -> value)
          }
          decoder.readEndDocument()
          DynamicValue.Record(fields.toVector)
        case "Sequence" =>
          decoder.readName() // "value"
          decoder.readStartArray()
          val elems = scala.collection.mutable.ArrayBuffer[DynamicValue]()
          while (decoder.readBsonType() != BsonType.END_OF_DOCUMENT) {
            elems += decodeUnsafe(decoder)
          }
          decoder.readEndArray()
          DynamicValue.Sequence(elems.toVector)
        case "Variant" =>
          decoder.readName() // "caseName"
          val caseName = decoder.readString()
          decoder.readName() // "value"
          val value = decodeUnsafe(decoder)
          DynamicValue.Variant(caseName, value)
        case _ => throw new IllegalArgumentException(s"Unknown DynamicValue type: $typeName")
      }
      decoder.readEndDocument()
      result
    }

    private def decodePrimitive(decoder: BsonReader): PrimitiveValue = {
      val tpe = decoder.getCurrentBsonType
      tpe match {
        case BsonType.STRING => PrimitiveValue.String(decoder.readString())
        case BsonType.INT32 => PrimitiveValue.Int(decoder.readInt32())
        case BsonType.INT64 => PrimitiveValue.Long(decoder.readInt64())
        case BsonType.DOUBLE => PrimitiveValue.Double(decoder.readDouble())
        case BsonType.BOOLEAN => PrimitiveValue.Boolean(decoder.readBoolean())
        case BsonType.NULL => decoder.readNull(); PrimitiveValue.Unit
        case _ => throw new IllegalArgumentException(s"Unsupported BSON type for DynamicValue: $tpe")
      }
    }
  }

  
  private class LazyBsonCodec[A](lazyCodec: Lazy[BsonCodec[A]], tpe: Int) extends BsonCodec[A](tpe) {
    def decodeUnsafe(decoder: BsonReader): A = lazyCodec.force.decodeUnsafe(decoder)
    
    def encode(value: A, encoder: BsonWriter): Unit = lazyCodec.force.encode(value, encoder)
  }
}

