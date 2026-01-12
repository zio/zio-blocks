package zio.blocks.schema.toon

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding, Registers, RegisterOffset}
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver}

import java.nio.charset.StandardCharsets.UTF_8

/**
 * TOON format for ZIO Schema 2.
 *
 * TOON (Token-Oriented Object Notation) is a compact serialization format
 * optimized for LLM token efficiency, achieving 30-60% reduction vs JSON.
 */
object ToonFormat
    extends BinaryFormat(
      "application/toon",
      new Deriver[ToonBinaryCodec] {

        // Type aliases for codec derivation
        type TC[A] = ToonBinaryCodec[A]

        override def derivePrimitive[F[_, _], A](
          primitiveType: PrimitiveType[A],
          typeName: TypeName[A],
          binding: Binding[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        ): Lazy[ToonBinaryCodec[A]] = Lazy {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            primitiveType match {
              case _: PrimitiveType.Unit.type  => ToonBinaryCodec.unitCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.Boolean    => ToonBinaryCodec.booleanCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.Byte       => ToonBinaryCodec.byteCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.Short      => ToonBinaryCodec.shortCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.Int        => ToonBinaryCodec.intCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.Long       => ToonBinaryCodec.longCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.Float      => ToonBinaryCodec.floatCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.Double     => ToonBinaryCodec.doubleCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.Char       => ToonBinaryCodec.charCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.String     => ToonBinaryCodec.stringCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.BigInt     => ToonBinaryCodec.bigIntCodec.asInstanceOf[TC[A]]
              case _: PrimitiveType.BigDecimal => ToonBinaryCodec.bigDecimalCodec.asInstanceOf[TC[A]]
              // java.time types - encode as strings for MVP
              case _: PrimitiveType.Instant =>
                stringWrapper[java.time.Instant](_.toString, java.time.Instant.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.LocalDate =>
                stringWrapper[java.time.LocalDate](_.toString, java.time.LocalDate.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.LocalTime =>
                stringWrapper[java.time.LocalTime](_.toString, java.time.LocalTime.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.LocalDateTime =>
                stringWrapper[java.time.LocalDateTime](_.toString, java.time.LocalDateTime.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.OffsetDateTime =>
                stringWrapper[java.time.OffsetDateTime](_.toString, java.time.OffsetDateTime.parse(_))
                  .asInstanceOf[TC[A]]
              case _: PrimitiveType.ZonedDateTime =>
                stringWrapper[java.time.ZonedDateTime](_.toString, java.time.ZonedDateTime.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.Duration =>
                stringWrapper[java.time.Duration](_.toString, java.time.Duration.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.Period =>
                stringWrapper[java.time.Period](_.toString, java.time.Period.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.Year =>
                intWrapper[java.time.Year](_.getValue, java.time.Year.of(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.YearMonth =>
                stringWrapper[java.time.YearMonth](_.toString, java.time.YearMonth.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.MonthDay =>
                stringWrapper[java.time.MonthDay](_.toString, java.time.MonthDay.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.Month =>
                stringWrapper[java.time.Month](_.toString, java.time.Month.valueOf(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.DayOfWeek =>
                stringWrapper[java.time.DayOfWeek](_.toString, java.time.DayOfWeek.valueOf(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.ZoneId =>
                stringWrapper[java.time.ZoneId](_.toString, java.time.ZoneId.of(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.ZoneOffset =>
                stringWrapper[java.time.ZoneOffset](_.toString, java.time.ZoneOffset.of(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.OffsetTime =>
                stringWrapper[java.time.OffsetTime](_.toString, java.time.OffsetTime.parse(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.UUID =>
                stringWrapper[java.util.UUID](_.toString, java.util.UUID.fromString(_)).asInstanceOf[TC[A]]
              case _: PrimitiveType.Currency =>
                stringWrapper[java.util.Currency](_.getCurrencyCode, java.util.Currency.getInstance(_))
                  .asInstanceOf[TC[A]]
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
        }

        override def deriveRecord[F[_, _], A](
          fields: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val b      = binding.asInstanceOf[Binding.Record[A]]
            val len    = fields.length
            val codecs = new Array[ToonBinaryCodec[?]](len)
            val names  = new Array[String](len)
            var offset = 0L
            var idx    = 0
            while (idx < len) {
              val field = fields(idx)
              val codec = deriveCodec(field.value)
              codecs(idx) = codec
              names(idx) = field.name
              offset = RegisterOffset.add(codec.valueOffset, offset)
              idx += 1
            }

            new ToonBinaryCodec[A] {
              private[this] val deconstructor = b.deconstructor
              private[this] val fieldCodecs   = codecs
              private[this] val fieldNames    = names
              private[this] val usedRegisters = offset

              override def isNested: Boolean = true

              override def encodeValue(x: A, out: ToonWriter): Unit = {
                val regs = Registers(usedRegisters)
                deconstructor.deconstruct(regs, 0L, x)
                var regOffset = 0L
                var idx       = 0
                while (idx < fieldCodecs.length) {
                  if (idx > 0) out.newLine()
                  out.writeKey(fieldNames(idx))
                  val codec = fieldCodecs(idx)
                  // If the field codec is a nested type, use nested formatting
                  if (codec.isNested) {
                    out.startNestedObject()
                    codec.asInstanceOf[ToonBinaryCodec[AnyRef]].encodeValue(regs.getObject(regOffset), out)
                    out.endNestedObject()
                  } else {
                    codec.valueType match {
                      case ToonBinaryCodec.objectType =>
                        codec.asInstanceOf[ToonBinaryCodec[AnyRef]].encodeValue(regs.getObject(regOffset), out)
                      case ToonBinaryCodec.booleanType =>
                        codec.asInstanceOf[ToonBinaryCodec[Boolean]].encodeValue(regs.getBoolean(regOffset), out)
                      case ToonBinaryCodec.byteType =>
                        codec.asInstanceOf[ToonBinaryCodec[Byte]].encodeValue(regs.getByte(regOffset), out)
                      case ToonBinaryCodec.charType =>
                        codec.asInstanceOf[ToonBinaryCodec[Char]].encodeValue(regs.getChar(regOffset), out)
                      case ToonBinaryCodec.shortType =>
                        codec.asInstanceOf[ToonBinaryCodec[Short]].encodeValue(regs.getShort(regOffset), out)
                      case ToonBinaryCodec.floatType =>
                        codec.asInstanceOf[ToonBinaryCodec[Float]].encodeValue(regs.getFloat(regOffset), out)
                      case ToonBinaryCodec.intType =>
                        codec.asInstanceOf[ToonBinaryCodec[Int]].encodeValue(regs.getInt(regOffset), out)
                      case ToonBinaryCodec.doubleType =>
                        codec.asInstanceOf[ToonBinaryCodec[Double]].encodeValue(regs.getDouble(regOffset), out)
                      case ToonBinaryCodec.longType =>
                        codec.asInstanceOf[ToonBinaryCodec[Long]].encodeValue(regs.getLong(regOffset), out)
                      case _ =>
                        codec.asInstanceOf[ToonBinaryCodec[Unit]].encodeValue((), out)
                    }
                  }
                  regOffset += codec.valueOffset
                  idx += 1
                }
              }

              override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
                Left(SchemaError.expectationMismatch(Nil, "TOON record decoding not yet implemented"))
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
        }

        override def deriveVariant[F[_, _], A](
          cases: IndexedSeq[Term[F, A, ?]],
          typeName: TypeName[A],
          binding: Binding[BindingType.Variant, A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val b      = binding.asInstanceOf[Binding.Variant[A]]
            val len    = cases.length
            val codecs = new Array[ToonBinaryCodec[?]](len)
            val names  = new Array[String](len)
            var idx    = 0
            while (idx < len) {
              val c = cases(idx)
              codecs(idx) = deriveCodec(c.value)
              names(idx) = c.name
              idx += 1
            }

            new ToonBinaryCodec[A] {
              private[this] val discriminator = b.discriminator
              private[this] val caseCodecs    = codecs
              private[this] val caseNames     = names

              override def encodeValue(x: A, out: ToonWriter): Unit = {
                val idx   = discriminator.discriminate(x)
                val name  = caseNames(idx)
                val codec = caseCodecs(idx).asInstanceOf[ToonBinaryCodec[A]]
                // Key discriminator style: TypeName: followed by nested content
                out.writeDiscriminator(name)
                codec.encodeValue(x, out)
                out.endDiscriminator()
              }

              override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
                Left(SchemaError.expectationMismatch(Nil, "TOON variant decoding not yet implemented"))
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
        }

        override def deriveSequence[F[_, _], C[_], A](
          element: Reflect[F, A],
          typeName: TypeName[C[A]],
          binding: Binding[BindingType.Seq[C], C[A]],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[C[A]]] = Lazy {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val b            = binding.asInstanceOf[Binding.Seq[C, A]]
            val elementCodec = deriveCodec(element)

            new ToonBinaryCodec[C[A]] {
              private[this] val deconstructor = b.deconstructor

              override def encodeValue(x: C[A], out: ToonWriter): Unit = {
                val it   = deconstructor.deconstruct(x)
                val size = deconstructor.size(x)
                // Inline format: [N]: a,b,c
                out.writeArrayHeader(size)
                var first = true
                while (it.hasNext) {
                  if (!first) out.writeArraySeparator()
                  elementCodec.encodeValue(it.next(), out)
                  first = false
                }
              }

              override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, C[A]] =
                Left(SchemaError.expectationMismatch(Nil, "TOON sequence decoding not yet implemented"))
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, C[A]]].instance.force
        }

        override def deriveMap[F[_, _], M[_, _], K, V](
          key: Reflect[F, K],
          value: Reflect[F, V],
          typeName: TypeName[M[K, V]],
          binding: Binding[BindingType.Map[M], M[K, V]],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[M[K, V]]] = Lazy {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val b        = binding.asInstanceOf[Binding.Map[M, K, V]]
            val keyCodec = deriveCodec(key)
            val valCodec = deriveCodec(value)

            new ToonBinaryCodec[M[K, V]] {
              private[this] val deconstructor = b.deconstructor

              override def encodeValue(x: M[K, V], out: ToonWriter): Unit = {
                val it    = deconstructor.deconstruct(x)
                var first = true
                while (it.hasNext) {
                  val kv = it.next()
                  if (!first) out.newLine()
                  // Maps are encoded as objects: key: value
                  out.writeKey(keyCodec.encodeToString(deconstructor.getKey(kv)))
                  valCodec.encodeValue(deconstructor.getValue(kv), out)
                  first = false
                }
              }

              override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, M[K, V]] =
                Left(SchemaError.expectationMismatch(Nil, "TOON map decoding not yet implemented"))
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, M[K, V]]].instance.force
        }

        override def deriveDynamic[F[_, _]](
          binding: Binding[BindingType.Dynamic, DynamicValue],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[DynamicValue]] = Lazy {
          new ToonBinaryCodec[DynamicValue] {
            override def encodeValue(x: DynamicValue, out: ToonWriter): Unit =
              out.writeString(x.toString) // Fallback for MVP

            override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, DynamicValue] =
              Left(SchemaError.expectationMismatch(Nil, "TOON dynamic decoding not yet implemented"))
          }
        }

        override def deriveWrapper[F[_, _], A, B](
          wrapped: Reflect[F, B],
          typeName: TypeName[A],
          wrapperPrimitiveType: Option[PrimitiveType[A]],
          binding: Binding[BindingType.Wrapper[A, B], A],
          doc: Doc,
          modifiers: Seq[Modifier.Reflect]
        )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
          if (binding.isInstanceOf[Binding[?, ?]]) {
            val b            = binding.asInstanceOf[Binding.Wrapper[A, B]]
            val wrappedCodec = deriveCodec(wrapped)

            new ToonBinaryCodec[A] {
              private[this] val unwrap = b.unwrap

              override def encodeValue(x: A, out: ToonWriter): Unit =
                wrappedCodec.encodeValue(unwrap(x), out)

              override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
                Left(SchemaError.expectationMismatch(Nil, "TOON wrapper decoding not yet implemented"))
            }
          } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
        }

        // Type aliases for internal use
        type Elem
        type Key
        type Value
        type Wrapped
        type Col[_]
        type MapType[_, _]

        // Helper to derive codec from Reflect (following AvroFormat pattern)
        private def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] = {
          if (reflect.isPrimitive) {
            val primitive = reflect.asPrimitive.get
            if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
              primitive.primitiveType match {
                case _: PrimitiveType.Unit.type  => ToonBinaryCodec.unitCodec
                case _: PrimitiveType.Boolean    => ToonBinaryCodec.booleanCodec
                case _: PrimitiveType.Byte       => ToonBinaryCodec.byteCodec
                case _: PrimitiveType.Short      => ToonBinaryCodec.shortCodec
                case _: PrimitiveType.Int        => ToonBinaryCodec.intCodec
                case _: PrimitiveType.Long       => ToonBinaryCodec.longCodec
                case _: PrimitiveType.Float      => ToonBinaryCodec.floatCodec
                case _: PrimitiveType.Double     => ToonBinaryCodec.doubleCodec
                case _: PrimitiveType.Char       => ToonBinaryCodec.charCodec
                case _: PrimitiveType.String     => ToonBinaryCodec.stringCodec
                case _: PrimitiveType.BigInt     => ToonBinaryCodec.bigIntCodec
                case _: PrimitiveType.BigDecimal => ToonBinaryCodec.bigDecimalCodec
                // java.time types - encode as strings for MVP
                case _: PrimitiveType.Instant =>
                  stringWrapper[java.time.Instant](_.toString, java.time.Instant.parse(_))
                case _: PrimitiveType.LocalDate =>
                  stringWrapper[java.time.LocalDate](_.toString, java.time.LocalDate.parse(_))
                case _: PrimitiveType.LocalTime =>
                  stringWrapper[java.time.LocalTime](_.toString, java.time.LocalTime.parse(_))
                case _: PrimitiveType.LocalDateTime =>
                  stringWrapper[java.time.LocalDateTime](_.toString, java.time.LocalDateTime.parse(_))
                case _: PrimitiveType.OffsetDateTime =>
                  stringWrapper[java.time.OffsetDateTime](_.toString, java.time.OffsetDateTime.parse(_))
                case _: PrimitiveType.ZonedDateTime =>
                  stringWrapper[java.time.ZonedDateTime](_.toString, java.time.ZonedDateTime.parse(_))
                case _: PrimitiveType.Duration =>
                  stringWrapper[java.time.Duration](_.toString, java.time.Duration.parse(_))
                case _: PrimitiveType.Period    => stringWrapper[java.time.Period](_.toString, java.time.Period.parse(_))
                case _: PrimitiveType.Year      => intWrapper[java.time.Year](_.getValue, java.time.Year.of(_))
                case _: PrimitiveType.YearMonth =>
                  stringWrapper[java.time.YearMonth](_.toString, java.time.YearMonth.parse(_))
                case _: PrimitiveType.MonthDay =>
                  stringWrapper[java.time.MonthDay](_.toString, java.time.MonthDay.parse(_))
                case _: PrimitiveType.Month     => stringWrapper[java.time.Month](_.toString, java.time.Month.valueOf(_))
                case _: PrimitiveType.DayOfWeek =>
                  stringWrapper[java.time.DayOfWeek](_.toString, java.time.DayOfWeek.valueOf(_))
                case _: PrimitiveType.ZoneId     => stringWrapper[java.time.ZoneId](_.toString, java.time.ZoneId.of(_))
                case _: PrimitiveType.ZoneOffset =>
                  stringWrapper[java.time.ZoneOffset](_.toString, java.time.ZoneOffset.of(_))
                case _: PrimitiveType.OffsetTime =>
                  stringWrapper[java.time.OffsetTime](_.toString, java.time.OffsetTime.parse(_))
                case _: PrimitiveType.UUID     => stringWrapper[java.util.UUID](_.toString, java.util.UUID.fromString(_))
                case _: PrimitiveType.Currency =>
                  stringWrapper[java.util.Currency](_.getCurrencyCode, java.util.Currency.getInstance(_))
              }
            } else primitive.primitiveBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isRecord) {
            val record = reflect.asRecord.get
            if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]
              val fields  = record.fields
              val len     = fields.length
              val codecs  = new Array[ToonBinaryCodec[?]](len)
              val names   = new Array[String](len)
              var offset  = 0L
              var idx     = 0
              while (idx < len) {
                val field = fields(idx)
                val codec = deriveCodec(field.value)
                codecs(idx) = codec
                names(idx) = field.name
                offset = RegisterOffset.add(codec.valueOffset, offset)
                idx += 1
              }
              new ToonBinaryCodec[A] {
                private[this] val deconstructor = binding.deconstructor
                private[this] val fieldCodecs   = codecs
                private[this] val fieldNames    = names
                private[this] val usedRegisters = offset

                override def isNested: Boolean = true

                override def encodeValue(x: A, out: ToonWriter): Unit = {
                  val regs = Registers(usedRegisters)
                  deconstructor.deconstruct(regs, 0L, x)
                  var regOffset = 0L
                  var idx       = 0
                  while (idx < fieldCodecs.length) {
                    if (idx > 0) out.newLine()
                    out.writeKey(fieldNames(idx))
                    val codec = fieldCodecs(idx)
                    // If the field codec is a nested type, use nested formatting
                    if (codec.isNested) {
                      out.startNestedObject()
                      codec.asInstanceOf[ToonBinaryCodec[AnyRef]].encodeValue(regs.getObject(regOffset), out)
                      out.endNestedObject()
                    } else {
                      codec.valueType match {
                        case ToonBinaryCodec.objectType =>
                          codec.asInstanceOf[ToonBinaryCodec[AnyRef]].encodeValue(regs.getObject(regOffset), out)
                        case ToonBinaryCodec.booleanType =>
                          codec.asInstanceOf[ToonBinaryCodec[Boolean]].encodeValue(regs.getBoolean(regOffset), out)
                        case ToonBinaryCodec.byteType =>
                          codec.asInstanceOf[ToonBinaryCodec[Byte]].encodeValue(regs.getByte(regOffset), out)
                        case ToonBinaryCodec.charType =>
                          codec.asInstanceOf[ToonBinaryCodec[Char]].encodeValue(regs.getChar(regOffset), out)
                        case ToonBinaryCodec.shortType =>
                          codec.asInstanceOf[ToonBinaryCodec[Short]].encodeValue(regs.getShort(regOffset), out)
                        case ToonBinaryCodec.floatType =>
                          codec.asInstanceOf[ToonBinaryCodec[Float]].encodeValue(regs.getFloat(regOffset), out)
                        case ToonBinaryCodec.intType =>
                          codec.asInstanceOf[ToonBinaryCodec[Int]].encodeValue(regs.getInt(regOffset), out)
                        case ToonBinaryCodec.doubleType =>
                          codec.asInstanceOf[ToonBinaryCodec[Double]].encodeValue(regs.getDouble(regOffset), out)
                        case ToonBinaryCodec.longType =>
                          codec.asInstanceOf[ToonBinaryCodec[Long]].encodeValue(regs.getLong(regOffset), out)
                        case _ =>
                          codec.asInstanceOf[ToonBinaryCodec[Unit]].encodeValue((), out)
                      }
                    }
                    regOffset += codec.valueOffset
                    idx += 1
                  }
                }

                override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
                  Left(SchemaError.expectationMismatch(Nil, "TOON record decoding not yet implemented"))
              }
            } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isVariant) {
            val variant = reflect.asVariant.get
            if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
              val binding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
              val cases   = variant.cases
              val len     = cases.length
              val codecs  = new Array[ToonBinaryCodec[?]](len)
              val names   = new Array[String](len)
              var idx     = 0
              while (idx < len) {
                codecs(idx) = deriveCodec(cases(idx).value)
                names(idx) = cases(idx).name
                idx += 1
              }
              new ToonBinaryCodec[A] {
                private[this] val discriminator = binding.discriminator
                private[this] val caseCodecs    = codecs
                private[this] val caseNames     = names

                override def encodeValue(x: A, out: ToonWriter): Unit = {
                  val idx   = discriminator.discriminate(x)
                  val name  = caseNames(idx)
                  val codec = caseCodecs(idx).asInstanceOf[ToonBinaryCodec[A]]
                  // Key discriminator style: TypeName: followed by nested content
                  out.writeDiscriminator(name)
                  codec.encodeValue(x, out)
                  out.endDiscriminator()
                }

                override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
                  Left(SchemaError.expectationMismatch(Nil, "TOON variant decoding not yet implemented"))
              }
            } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isSequence) {
            val sequence = reflect.asSequenceUnknown.get.sequence
            if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
              val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
              val elementCodec = deriveCodec(sequence.element).asInstanceOf[ToonBinaryCodec[Elem]]
              new ToonBinaryCodec[Col[Elem]] {
                private[this] val deconstructor = binding.deconstructor

                override def encodeValue(x: Col[Elem], out: ToonWriter): Unit = {
                  val it   = deconstructor.deconstruct(x)
                  val size = deconstructor.size(x)
                  // Inline format: [N]: a,b,c
                  out.writeArrayHeader(size)
                  var first = true
                  while (it.hasNext) {
                    if (!first) out.writeArraySeparator()
                    elementCodec.encodeValue(it.next(), out)
                    first = false
                  }
                }

                override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Col[Elem]] =
                  Left(SchemaError.expectationMismatch(Nil, "TOON sequence decoding not yet implemented"))
              }
            } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isMap) {
            val map = reflect.asMapUnknown.get.map
            if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
              val binding  = map.mapBinding.asInstanceOf[Binding.Map[MapType, Key, Value]]
              val keyCodec = deriveCodec(map.key).asInstanceOf[ToonBinaryCodec[Key]]
              val valCodec = deriveCodec(map.value).asInstanceOf[ToonBinaryCodec[Value]]
              new ToonBinaryCodec[MapType[Key, Value]] {
                private[this] val deconstructor = binding.deconstructor

                override def encodeValue(x: MapType[Key, Value], out: ToonWriter): Unit = {
                  val it    = deconstructor.deconstruct(x)
                  var first = true
                  while (it.hasNext) {
                    val kv = it.next()
                    if (!first) out.newLine()
                    // Maps are encoded as objects: key: value
                    out.writeKey(keyCodec.encodeToString(deconstructor.getKey(kv)))
                    valCodec.encodeValue(deconstructor.getValue(kv), out)
                    first = false
                  }
                }

                override def decodeBytes(
                  bytes: Array[Byte],
                  offset: Int,
                  length: Int
                ): Either[SchemaError, MapType[Key, Value]] =
                  Left(SchemaError.expectationMismatch(Nil, "TOON map decoding not yet implemented"))
              }
            } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isDynamic) {
            val dynamic = reflect.asDynamic.get
            if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) {
              new ToonBinaryCodec[DynamicValue] {
                override def encodeValue(x: DynamicValue, out: ToonWriter): Unit =
                  out.writeString(x.toString) // Fallback for MVP

                override def decodeBytes(
                  bytes: Array[Byte],
                  offset: Int,
                  length: Int
                ): Either[SchemaError, DynamicValue] =
                  Left(SchemaError.expectationMismatch(Nil, "TOON dynamic decoding not yet implemented"))
              }
            } else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isWrapper) {
            val wrapper = reflect.asWrapperUnknown.get.wrapper
            if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
              val binding      = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
              val wrappedCodec = deriveCodec(wrapper.wrapped).asInstanceOf[ToonBinaryCodec[Wrapped]]
              new ToonBinaryCodec[A] {
                private[this] val unwrap = binding.unwrap

                override def encodeValue(x: A, out: ToonWriter): Unit =
                  wrappedCodec.encodeValue(unwrap(x), out)

                override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
                  Left(SchemaError.expectationMismatch(Nil, "TOON wrapper decoding not yet implemented"))
              }
            } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
          } else if (reflect.isInstanceOf[Reflect.Deferred[F, A]]) {
            deriveCodec(reflect.asInstanceOf[Reflect.Deferred[F, A]].value)
          } else {
            throw new IllegalArgumentException(s"Unsupported reflect type: $reflect")
          }
        }.asInstanceOf[ToonBinaryCodec[A]]

        // Helper for string-based types
        private def stringWrapper[A](enc: A => String, dec: String => A): ToonBinaryCodec[A] =
          new ToonBinaryCodec[A] {
            override def encodeValue(x: A, out: ToonWriter): Unit                                          = out.writeString(enc(x))
            override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
              try Right(dec(new String(bytes, offset, length, UTF_8).trim))
              catch { case e: Exception => Left(SchemaError.expectationMismatch(Nil, e.getMessage)) }
          }

        // Helper for int-based types
        private def intWrapper[A](enc: A => Int, dec: Int => A): ToonBinaryCodec[A] =
          new ToonBinaryCodec[A](ToonBinaryCodec.intType) {
            override def encodeValue(x: A, out: ToonWriter): Unit                                          = out.writeInt(enc(x))
            override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
              try Right(dec(new String(bytes, offset, length, UTF_8).trim.toInt))
              catch { case e: Exception => Left(SchemaError.expectationMismatch(Nil, e.getMessage)) }
          }
      }
    ) {

  /** Convenience method to derive a ToonBinaryCodec from a Schema. */
  def codec[A](implicit schema: Schema[A]): ToonBinaryCodec[A] =
    schema.derive(deriver)
}
