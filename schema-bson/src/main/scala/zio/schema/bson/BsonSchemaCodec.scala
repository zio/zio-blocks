package zio.schema.bson

import org.bson.{BsonReader, BsonWriter, BsonType, BsonDocument, BsonDocumentWriter, BsonDocumentReader}
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.{Binding, Discriminator}
import zio.bson._
import zio.bson.BsonEncoder.EncoderContext
import zio.bson.BsonDecoder.{BsonDecoderContext, Error => BsonError}
import org.bson.codecs.{BsonDocumentCodec, DecoderContext => BsonLibDecoderContext}
import java.time._

object BsonSchemaCodec {

  sealed trait SumTypeHandling
  object SumTypeHandling {
    case object WrapperWithClassNameField       extends SumTypeHandling
    case class DiscriminatorField(name: String) extends SumTypeHandling
  }

  case class Config(
    sumTypeHandling: SumTypeHandling = SumTypeHandling.WrapperWithClassNameField,
    classNameMapping: String => String = identity,
    allowExtraFields: Boolean = true
  )

  object Config {
    val default: Config = Config()

    def withClassNameMapping(classNameMapping: String => String): Config =
      default.copy(classNameMapping = classNameMapping)

    def withSumTypeHandling(sumTypeHandling: SumTypeHandling): Config =
      default.copy(sumTypeHandling = sumTypeHandling)
  }

  def bsonCodec[A](schema: Schema[A], config: Config): BsonCodec[A] =
    new BsonCodec(encoder(schema, config), decoder(schema, config))

  def bsonCodec[A](schema: Schema[A]): BsonCodec[A] = bsonCodec(schema, Config.default)

  type AnyK[X]      = Any
  type AnyMap[K, V] = Any

  implicit class BsonCodecOps[A](val self: BsonCodec[A]) extends AnyVal {
    def xmap[B](f: A => B, g: B => A): BsonCodec[B] = new BsonCodec(
      new BsonEncoder[B] {
        override def encode(writer: BsonWriter, value: B, ctx: EncoderContext): Unit =
          self.encoder.encode(writer, g(value), ctx)
        override def toBsonValue(value: B): org.bson.BsonValue =
          self.encoder.toBsonValue(g(value))
      },
      new BsonDecoder[B] {
        override def decode(reader: BsonReader): Either[BsonError, B] =
          self.decoder.decode(reader).map(f)
        override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): B =
          f(self.decoder.decodeUnsafe(reader, trace, ctx))
        override def fromBsonValueUnsafe(
          value: org.bson.BsonValue,
          trace: List[BsonTrace],
          ctx: BsonDecoderContext
        ): B =
          f(self.decoder.fromBsonValueUnsafe(value, trace, ctx))
      }
    )
  }

  private def primitiveCodec[A](primitive: PrimitiveType[A]): BsonCodec[A] = primitive match {
    case PrimitiveType.Unit       => unitCodec.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Boolean(_) => BsonCodec.boolean.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Byte(_)    =>
      BsonCodec.int.xmap[Byte]((i: Int) => i.toByte, (b: Byte) => b.toInt).asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Short(_) =>
      BsonCodec.int.xmap[Short]((i: Int) => i.toShort, (s: Short) => s.toInt).asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Int(_)   => BsonCodec.int.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Long(_)  => BsonCodec.long.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Float(_) =>
      BsonCodec.double.xmap[Float]((d: Double) => d.toFloat, (f: Float) => f.toDouble).asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Double(_) => BsonCodec.double.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Char(_)   =>
      BsonCodec.string.xmap[Char]((s: String) => s.head, (c: Char) => c.toString).asInstanceOf[BsonCodec[A]]
    case PrimitiveType.String(_) => BsonCodec.string.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.BigInt(_) =>
      BsonCodec.string
        .xmap[scala.BigInt]((s: String) => scala.BigInt(s), (b: scala.BigInt) => b.toString)
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.BigDecimal(_) => BsonCodec.bigDecimal.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.DayOfWeek(_)  =>
      BsonCodec.string
        .xmap[DayOfWeek]((s: String) => DayOfWeek.valueOf(s), (d: DayOfWeek) => d.name)
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Duration(_) =>
      BsonCodec.string
        .xmap[Duration]((s: String) => Duration.parse(s), (d: Duration) => d.toString)
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Instant(_)       => BsonCodec.instant.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.LocalDate(_)     => BsonCodec.localDate.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.LocalDateTime(_) => BsonCodec.localDateTime.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.LocalTime(_)     => BsonCodec.localTime.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Month(_)         =>
      BsonCodec.string.xmap[Month]((s: String) => Month.valueOf(s), (m: Month) => m.name).asInstanceOf[BsonCodec[A]]
    case PrimitiveType.MonthDay(_) =>
      BsonCodec.string
        .xmap[MonthDay]((s: String) => MonthDay.parse(s), (m: MonthDay) => m.toString)
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.OffsetDateTime(_) => BsonCodec.offsetDateTime.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.OffsetTime(_)     =>
      BsonCodec.string
        .xmap[OffsetTime]((s: String) => OffsetTime.parse(s), (o: OffsetTime) => o.toString)
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Period(_) =>
      BsonCodec.string
        .xmap[Period]((s: String) => Period.parse(s), (p: Period) => p.toString)
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Year(_) =>
      BsonCodec.int.xmap[Year]((i: Int) => Year.of(i), (y: Year) => y.getValue).asInstanceOf[BsonCodec[A]]
    case PrimitiveType.YearMonth(_) =>
      BsonCodec.string
        .xmap[YearMonth]((s: String) => YearMonth.parse(s), (ym: YearMonth) => ym.toString)
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.ZoneId(_) =>
      BsonCodec.string.xmap[ZoneId]((s: String) => ZoneId.of(s), (z: ZoneId) => z.toString).asInstanceOf[BsonCodec[A]]
    case PrimitiveType.ZoneOffset(_) =>
      BsonCodec.string
        .xmap[ZoneOffset]((s: String) => ZoneOffset.of(s), (z: ZoneOffset) => z.toString)
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.ZonedDateTime(_) => BsonCodec.zonedDateTime.asInstanceOf[BsonCodec[A]]
    case PrimitiveType.Currency(_)      =>
      BsonCodec.string
        .xmap[java.util.Currency](
          (s: String) => java.util.Currency.getInstance(s),
          (c: java.util.Currency) => c.getCurrencyCode
        )
        .asInstanceOf[BsonCodec[A]]
    case PrimitiveType.UUID(_) => BsonCodec.uuid.asInstanceOf[BsonCodec[A]]
  }

  lazy val unitCodec: BsonCodec[Unit] = new BsonCodec(
    new BsonEncoder[Unit] {
      override def encode(writer: BsonWriter, value: Unit, ctx: EncoderContext): Unit = ()
      override def toBsonValue(value: Unit): org.bson.BsonValue                       = new org.bson.BsonNull()
    },
    new BsonDecoder[Unit] {
      override def decode(reader: BsonReader): Either[BsonError, Unit]                                     = Right(())
      override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): Unit =
        // Unit type might not have a corresponding BSON value, or it might be Null/Undefined.
        // For now, we just consume whatever is there if it's not END_OF_DOCUMENT.
        // If it's a field, it might be a BsonNull or BsonUndefined.
        // If it's a top-level value, it might be nothing.
        // For simplicity, we assume it's either not present or a null.
        // If the reader is positioned at a value, we should consume it.
        if (reader.getCurrentBsonType != BsonType.END_OF_DOCUMENT) {
          reader.skipValue()
        }
      override def fromBsonValueUnsafe(
        value: org.bson.BsonValue,
        trace: List[BsonTrace],
        ctx: BsonDecoderContext
      ): Unit = ()
    }
  )

  def encoder[A](schema: Schema[A], config: Config = Config.default): BsonEncoder[A] = encoder0(schema.reflect, config)
  def decoder[A](schema: Schema[A], config: Config = Config.default): BsonDecoder[A] = decoder0(schema.reflect, config)

  private def encoder0[A](reflect: Reflect.Bound[A], config: Config): BsonEncoder[A] = reflect match {
    case Reflect.Primitive(p, _, _, _, _) => primitiveCodec(p).encoder
    case r: Reflect.Record[_, A]          => recordEncoder(r, config)
    case r: Reflect.Sequence[_, _, _]     =>
      sequenceEncoder(r.asInstanceOf[Reflect.Sequence[Binding, Any, AnyK]], config).asInstanceOf[BsonEncoder[A]]
    case r: Reflect.Map[_, _, _, _] =>
      mapEncoder(r.asInstanceOf[Reflect.Map[Binding, Any, Any, AnyMap]], config).asInstanceOf[BsonEncoder[A]]
    case r: Reflect.Wrapper[_, _, _] =>
      wrapperEncoder(r.asInstanceOf[Reflect.Wrapper[Binding, A, Any]], config).asInstanceOf[BsonEncoder[A]]
    case r: Reflect.Variant[_, _] =>
      variantEncoder(r.asInstanceOf[Reflect.Variant[Binding, A]], config).asInstanceOf[BsonEncoder[A]]
    case r: Reflect.Deferred[_, _] => encoder0(r.value.asInstanceOf[Reflect.Bound[A]], config)
    case _                         => ???
  }

  private def decoder0[A](reflect: Reflect.Bound[A], config: Config): BsonDecoder[A] = reflect match {
    case Reflect.Primitive(p, _, _, _, _) => primitiveCodec(p).decoder
    case r: Reflect.Record[_, A]          => recordDecoder(r, config)
    case r: Reflect.Sequence[_, _, _]     =>
      sequenceDecoder(r.asInstanceOf[Reflect.Sequence[Binding, A, AnyK]], config).asInstanceOf[BsonDecoder[A]]
    case r: Reflect.Map[_, _, _, _] =>
      mapDecoder(r.asInstanceOf[Reflect.Map[Binding, Any, Any, AnyMap]], config).asInstanceOf[BsonDecoder[A]]
    case r: Reflect.Wrapper[_, _, _] =>
      wrapperDecoder(r.asInstanceOf[Reflect.Wrapper[Binding, A, Any]], config).asInstanceOf[BsonDecoder[A]]
    case r: Reflect.Variant[_, _] =>
      variantDecoder(r.asInstanceOf[Reflect.Variant[Binding, A]], config).asInstanceOf[BsonDecoder[A]]
    case r: Reflect.Deferred[_, _] => decoder0(r.value.asInstanceOf[Reflect.Bound[A]], config)
    case _                         => ???
  }

  private def toBsonValueImpl[A](value: A, encoder: (BsonWriter, A, EncoderContext) => Unit): org.bson.BsonValue = {
    val doc    = new BsonDocument()
    val writer = new BsonDocumentWriter(doc)
    writer.writeStartDocument()
    writer.writeName("v")
    encoder(writer, value, EncoderContext.default)
    writer.writeEndDocument()
    doc.get("v")
  }

  private def fromBsonValueUnsafeImpl[A](
    value: org.bson.BsonValue,
    decoder: (BsonReader, List[BsonTrace], BsonDecoderContext) => A,
    trace: List[BsonTrace],
    ctx: BsonDecoderContext
  ): A = {
    val doc = new BsonDocument()
    doc.append("v", value)
    val reader = new BsonDocumentReader(doc)
    reader.readStartDocument()
    reader.readName("v")
    val res = decoder(reader, trace, ctx)
    reader.readEndDocument()
    res
  }

  private def sequenceEncoder[A, C[_]](r: Reflect.Sequence[Binding, A, C], config: Config): BsonEncoder[C[A]] =
    new BsonEncoder[C[A]] {
      override def encode(writer: BsonWriter, value: C[A], ctx: EncoderContext): Unit = {
        val binding     = r.metadata.asInstanceOf[Binding.Seq[C, A]]
        val chunk       = binding.deconstructor.deconstruct(value)
        val elemEncoder = encoder0(r.element, config).asInstanceOf[BsonEncoder[A]]

        writer.writeStartArray()
        chunk.foreach { a =>
          elemEncoder.encode(writer, a, ctx)
        }
        writer.writeEndArray()
      }
      override def toBsonValue(value: C[A]): org.bson.BsonValue =
        toBsonValueImpl(value, encode)
    }

  private def sequenceDecoder[A, C[_]](r: Reflect.Sequence[Binding, A, C], config: Config): BsonDecoder[C[A]] =
    new BsonDecoder[C[A]] {
      override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): C[A] = {
        val binding     = r.metadata.asInstanceOf[Binding.Seq[C, A]]
        val elemDecoder = decoder0(r.element, config).asInstanceOf[BsonDecoder[A]]

        val builder = binding.constructor.newObjectBuilder[A]()
        reader.readStartArray()

        try {
          while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            val item = elemDecoder.decodeUnsafe(reader, trace, ctx)
            binding.constructor.addObject(builder, item)
          }
          reader.readEndArray()
          binding.constructor.resultObject(builder)
        } catch {
          case e: Exception => throw BsonError(trace, e.getMessage)
        }
      }

      override def fromBsonValueUnsafe(
        value: org.bson.BsonValue,
        trace: List[BsonTrace],
        ctx: BsonDecoderContext
      ): C[A] =
        fromBsonValueUnsafeImpl(value, decodeUnsafe, trace, ctx)
    }

  private def mapEncoder[K, V, M[_, _]](r: Reflect.Map[Binding, K, V, M], config: Config): BsonEncoder[M[K, V]] =
    new BsonEncoder[M[K, V]] {
      override def encode(writer: BsonWriter, value: M[K, V], ctx: EncoderContext): Unit = {
        val binding = r.metadata.asInstanceOf[Binding.Map[M, K, V]]
        val chunk   = binding.deconstructor.deconstruct(value)

        val valEncoder = encoder0(r.value, config).asInstanceOf[BsonEncoder[V]]

        writer.writeStartDocument()
        chunk.foreach { case (k, v) =>
          writer.writeName(k.toString)
          valEncoder.encode(writer, v.asInstanceOf[V], ctx)
        }
        writer.writeEndDocument()
      }
      override def toBsonValue(value: M[K, V]): org.bson.BsonValue =
        toBsonValueImpl(value, encode)
    }

  private def mapDecoder[K, V, M[_, _]](r: Reflect.Map[Binding, K, V, M], config: Config): BsonDecoder[M[K, V]] =
    new BsonDecoder[M[K, V]] {
      override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): M[K, V] = {
        val binding = r.metadata.asInstanceOf[Binding.Map[M, K, V]]

        val valDecoder = decoder0(r.value, config).asInstanceOf[BsonDecoder[V]]

        reader.readStartDocument()
        val builder = binding.constructor.newObjectBuilder[K, V]()

        try {
          while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            val name = reader.readName()
            val k    = name.asInstanceOf[K]
            val v    = valDecoder.decodeUnsafe(reader, trace, ctx)
            binding.constructor.addObject(builder, k, v)
          }
          reader.readEndDocument()
          binding.constructor.resultObject(builder)
        } catch {
          case e: Exception => throw BsonError(trace, e.getMessage)
        }
      }

      override def fromBsonValueUnsafe(
        value: org.bson.BsonValue,
        trace: List[BsonTrace],
        ctx: BsonDecoderContext
      ): M[K, V] =
        fromBsonValueUnsafeImpl(value, decodeUnsafe, trace, ctx)
    }

  private def wrapperEncoder[A, B](r: Reflect.Wrapper[Binding, A, B], config: Config): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def encode(writer: BsonWriter, value: A, ctx: EncoderContext): Unit = {
        val binding = r.wrapperBinding.asInstanceOf[Binding.Wrapper[A, B]]
        encoder0(r.wrapped, config).asInstanceOf[BsonEncoder[B]].encode(writer, binding.unwrap(value), ctx)
      }
      override def toBsonValue(value: A): org.bson.BsonValue =
        toBsonValueImpl(value, encode)
    }

  private def wrapperDecoder[A, B](r: Reflect.Wrapper[Binding, A, B], config: Config): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): A = {
        val binding = r.wrapperBinding.asInstanceOf[Binding.Wrapper[A, B]]
        val b       = decoder0(r.wrapped, config).asInstanceOf[BsonDecoder[B]].decodeUnsafe(reader, trace, ctx)
        binding.wrap(b) match {
          case Right(a)  => a
          case Left(err) => throw BsonError(trace, err)
        }
      }
      override def fromBsonValueUnsafe(value: org.bson.BsonValue, trace: List[BsonTrace], ctx: BsonDecoderContext): A =
        fromBsonValueUnsafeImpl(value, decodeUnsafe, trace, ctx)
    }

  private def variantEncoder[A](r: Reflect.Variant[Binding, A], config: Config): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def encode(writer: BsonWriter, value: A, ctx: EncoderContext): Unit = {
        val binding       = r.metadata.asInstanceOf[Binding.Variant[A]]
        val discriminator = binding.discriminator.asInstanceOf[Discriminator[A]]
        val idx           = discriminator.discriminate(value)
        val caseTerm      = r.cases(idx)
        val isTransient   = caseTerm.modifiers.exists { case Modifier.transient() => true; case _ => false }

        if (!isTransient) {
          val rawName       = caseTerm.modifiers.collectFirst { case Modifier.rename(n) => n }.getOrElse(caseTerm.name)
          val effectiveName = config.classNameMapping(rawName)
          val schemaForCase = caseTerm.value.asInstanceOf[Reflect.Bound[Any]]

          val handling = r.modifiers.collectFirst {
            case Modifier.config("bson.discriminator", value) =>
              SumTypeHandling.DiscriminatorField(value)
            case Modifier.config("bson.noDiscriminator", "true") =>
              SumTypeHandling.WrapperWithClassNameField
          }.getOrElse(config.sumTypeHandling)

          if (r.isEnumeration) {
            writer.writeString(effectiveName)
          } else {
            handling match {
              case SumTypeHandling.WrapperWithClassNameField =>
                writer.writeStartDocument()
                writer.writeName(effectiveName)
                encoder0(schemaForCase, config).encode(writer, value, ctx)
                writer.writeEndDocument()

              case SumTypeHandling.DiscriminatorField(discriminatorName) =>
                schemaForCase match {
                  case rec: Reflect.Record[_, _] =>
                    writer.writeStartDocument()
                    writer.writeName(discriminatorName)
                    writer.writeString(effectiveName)
                    encodeRecordFields(rec.asInstanceOf[Reflect.Record[Binding, Any]], value, writer, config, ctx)
                    writer.writeEndDocument()
                  case _ =>
                    writer.writeStartDocument()
                    writer.writeName(discriminatorName)
                    writer.writeString(effectiveName)
                    writer.writeName("value")
                    encoder0(schemaForCase, config).encode(writer, value, ctx)
                    writer.writeEndDocument()
                }
            }
          }
        } else {
          writer.writeStartDocument()
          writer.writeEndDocument()
        }
      }
      override def toBsonValue(value: A): org.bson.BsonValue =
        toBsonValueImpl(value, encode)
    }

  private def variantDecoder[A](r: Reflect.Variant[Binding, A], config: Config): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): A = {
        val isEnumeration = r.isEnumeration

        val cases = r.cases.map { caseTerm =>
          val caseName      = caseTerm.modifiers.collectFirst { case Modifier.rename(name) => name }.getOrElse(caseTerm.name)
          val effectiveName = config.classNameMapping(caseName)
          val caseReflect   = caseTerm.value.asInstanceOf[Reflect.Bound[Any]]
          val aliases       = caseTerm.modifiers.collect { case Modifier.alias(name) => name }
          val names         = effectiveName +: aliases

          (caseTerm.name, (names, caseReflect))
        }.toMap

        if (isEnumeration) {
          val name = reader.readString()
          cases.find { case (_, (names, _)) => names.contains(name) } match {
            case Some((_, (_, reflex))) =>
              reflex match {
                case rec: Reflect.Record[_, _] =>
                  val binding = rec.metadata.asInstanceOf[Binding.Record[Any]]
                  binding.constructor
                    .construct(Registers(binding.constructor.usedRegisters), RegisterOffset.Zero)
                    .asInstanceOf[A]
                case _ =>
                  throw BsonError(trace, s"Enum object $name is not a record")
              }
            case None =>
              throw BsonError(trace, s"Unknown enum variant: $name")
          }
        } else {
          val sumTypeHandling = r.modifiers.collectFirst {
            case Modifier.config("bson.discriminator", value) =>
              SumTypeHandling.DiscriminatorField(value)
            case Modifier.config("bson.noDiscriminator", "true") =>
              SumTypeHandling.WrapperWithClassNameField
          }.getOrElse(config.sumTypeHandling)

          sumTypeHandling match {
            case SumTypeHandling.WrapperWithClassNameField =>
              reader.readStartDocument()
              val name = reader.readName()

              cases.find { case (_, (names, _)) => names.contains(name) } match {
                case Some((_, (_, reflex))) =>
                  val decoder = decoder0(reflex, config)
                  val res     = decoder.decodeUnsafe(reader, trace, ctx).asInstanceOf[A]
                  reader.readEndDocument()
                  res
                case None =>
                  throw BsonError(trace, s"Unknown variant case (wrapper): $name")
              }

            case SumTypeHandling.DiscriminatorField(discriminatorName) =>
              val codec = new BsonDocumentCodec()
              val doc   = codec.decode(reader, BsonLibDecoderContext.builder().build())

              val typeName = if (doc.containsKey(discriminatorName)) {
                doc.getString(discriminatorName).getValue
              } else {
                throw BsonError(trace, s"Missing discriminator field: $discriminatorName")
              }

              cases.find { case (_, (names, _)) => names.contains(typeName) } match {
                case Some((_, (_, reflex))) =>
                  val decoder   = decoder0(reflex, config)
                  val docReader = new BsonDocumentReader(doc)
                  decoder.decodeUnsafe(docReader, trace, ctx).asInstanceOf[A]
                case None =>
                  throw BsonError(trace, s"Unknown variant case: $typeName")
              }
          }
        }
      }
      override def fromBsonValueUnsafe(value: org.bson.BsonValue, trace: List[BsonTrace], ctx: BsonDecoderContext): A =
        fromBsonValueUnsafeImpl(value, decodeUnsafe, trace, ctx)
    }

  private def encodeRecordFields[A](
    r: Reflect.Record[Binding, A],
    value: A,
    writer: BsonWriter,
    config: Config,
    ctx: EncoderContext
  ): Unit = {
    val binding   = r.recordBinding.asInstanceOf[Binding.Record[A]]
    val registers = Registers(binding.deconstructor.usedRegisters)
    binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)

    var offset = RegisterOffset.Zero
    r.fields.foreach { term =>
      val usage       = registerUsage(term.value)
      val isTransient = term.modifiers.exists { case Modifier.transient() => true; case _ => false }

      if (!isTransient && usage != RegisterOffset.Zero) {
        val name = term.modifiers.collectFirst { case Modifier.rename(n) => n }.getOrElse(term.name)
        writer.writeName(name)
        val elemEncoder = encoder0(term.value, config).asInstanceOf[BsonEncoder[Any]]
        val valFromReg  = retrieve(registers, offset, term.value)
        elemEncoder.encode(writer, valFromReg, ctx)
      }
      offset = RegisterOffset.add(offset, usage)
    }
  }

  private def decodeRecordFields[A](
    r: Reflect.Record[Binding, A],
    reader: BsonReader,
    trace: List[BsonTrace],
    ctx: BsonDecoderContext,
    config: Config
  ): A = {
    val binding   = r.recordBinding.asInstanceOf[Binding.Record[A]]
    val registers = Registers(binding.constructor.usedRegisters)

    // Fill defaults
    var initOffset = RegisterOffset.Zero
    r.fields.foreach { term =>
      val usage = registerUsage(term.value)
      term.value.binding.defaultValue.foreach { d =>
        store(registers, initOffset, d(), term.value)
      }
      initOffset = RegisterOffset.add(initOffset, usage)
    }

    var offset   = RegisterOffset.Zero
    val fieldMap = r.fields.zipWithIndex.flatMap { case (term, _) =>
      val usage         = registerUsage(term.value)
      val currentOffset = offset
      offset = RegisterOffset.add(offset, usage)
      // val isTransient = term.modifiers.exists { case Modifier.transient() => true; case _ => false }

      val name    = term.modifiers.collectFirst { case Modifier.rename(n) => n }.getOrElse(term.name)
      val aliases = term.modifiers.collect { case Modifier.alias(n) => n }
      val dec     = decoder0(term.value, config).asInstanceOf[BsonDecoder[Any]]
      val tuple   = (currentOffset, dec, term.value)
      (name :: aliases.toList).map(_ -> tuple)
    }.toMap

    try {
      while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
        val name = reader.readName()
        fieldMap.get(name) match {
          case Some((off, dec, refl)) =>
            val v = dec.decodeUnsafe(reader, trace, ctx)
            store(registers, off, v, refl)
          case None =>
            if (config.allowExtraFields) reader.skipValue()
            else throw BsonError(trace, s"Unexpected field: $name")
        }
      }
      reader.readEndDocument()
      binding.constructor.construct(registers, RegisterOffset.Zero)
    } catch {
      case e: BsonError => throw e
      case e: Exception => throw BsonError(trace, e.getMessage)
    }
  }

  private def recordEncoder[A](r0: Reflect.Record[?, A], config: Config): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def encode(writer: BsonWriter, value: A, ctx: EncoderContext): Unit = {
        val r = r0.asInstanceOf[Reflect.Record[Binding, A]]
        writer.writeStartDocument()
        encodeRecordFields(r, value, writer, config, ctx)
        writer.writeEndDocument()
      }
      override def toBsonValue(value: A): org.bson.BsonValue =
        toBsonValueImpl(value, encode)
    }

  private def recordDecoder[A](r0: Reflect.Record[?, A], config: Config): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def decode(reader: BsonReader): Either[BsonError, A] =
        try {
          Right(decodeUnsafe(reader, List.empty, BsonDecoderContext.default))
        } catch {
          case e: BsonError => Left(e)
          case e: Exception => Left(BsonError(List.empty, e.getMessage))
        }

      override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): A = {
        val r = r0.asInstanceOf[Reflect.Record[Binding, A]]
        reader.readStartDocument()
        decodeRecordFields(r, reader, trace, ctx, config)
      }
      override def fromBsonValueUnsafe(value: org.bson.BsonValue, trace: List[BsonTrace], ctx: BsonDecoderContext): A =
        fromBsonValueUnsafeImpl(value, decodeUnsafe, trace, ctx)
    }

  private def registerUsage[F[_, _], A](reflect: Reflect[F, A]): RegisterOffset = reflect match {
    case Reflect.Primitive(p, _, _, _, _) =>
      p match {
        case PrimitiveType.Boolean(_) => RegisterOffset(booleans = 1)
        case PrimitiveType.Byte(_)    => RegisterOffset(bytes = 1)
        case PrimitiveType.Short(_)   => RegisterOffset(shorts = 1)
        case PrimitiveType.Char(_)    => RegisterOffset(chars = 1)
        case PrimitiveType.Int(_)     => RegisterOffset(ints = 1)
        case PrimitiveType.Float(_)   => RegisterOffset(floats = 1)
        case PrimitiveType.Long(_)    => RegisterOffset(longs = 1)
        case PrimitiveType.Double(_)  => RegisterOffset(doubles = 1)
        case PrimitiveType.Unit       => RegisterOffset.Zero
        case _                        => RegisterOffset(objects = 1)
      }
    case _ => RegisterOffset(objects = 1)
  }

  private def retrieve[F[_, _], A](registers: Registers, offset: RegisterOffset, reflect: Reflect[F, A]): Any =
    reflect match {
      case Reflect.Primitive(p, _, _, _, _) =>
        p match {
          case PrimitiveType.Boolean(_) => registers.getBoolean(offset)
          case PrimitiveType.Byte(_)    => registers.getByte(offset)
          case PrimitiveType.Short(_)   => registers.getShort(offset)
          case PrimitiveType.Char(_)    => registers.getChar(offset)
          case PrimitiveType.Int(_)     => registers.getInt(offset)
          case PrimitiveType.Float(_)   => registers.getFloat(offset)
          case PrimitiveType.Long(_)    => registers.getLong(offset)
          case PrimitiveType.Double(_)  => registers.getDouble(offset)
          case PrimitiveType.Unit       => ()
          case _                        => registers.getObject(offset)
        }
      case _ => registers.getObject(offset)
    }

  private def store[F[_, _], A](
    registers: Registers,
    offset: RegisterOffset,
    value: Any,
    reflect: Reflect[F, A]
  ): Unit = reflect match {
    case Reflect.Primitive(p, _, _, _, _) =>
      p match {
        case PrimitiveType.Boolean(_) => registers.setBoolean(offset, value.asInstanceOf[Boolean])
        case PrimitiveType.Byte(_)    => registers.setByte(offset, value.asInstanceOf[Byte])
        case PrimitiveType.Short(_)   => registers.setShort(offset, value.asInstanceOf[Short])
        case PrimitiveType.Char(_)    => registers.setChar(offset, value.asInstanceOf[Char])
        case PrimitiveType.Int(_)     => registers.setInt(offset, value.asInstanceOf[Int])
        case PrimitiveType.Float(_)   => registers.setFloat(offset, value.asInstanceOf[Float])
        case PrimitiveType.Long(_)    => registers.setLong(offset, value.asInstanceOf[Long])
        case PrimitiveType.Double(_)  => registers.setDouble(offset, value.asInstanceOf[Double])
        case PrimitiveType.Unit       => ()
        case _                        => registers.setObject(offset, value.asInstanceOf[AnyRef])
      }
    case _ => registers.setObject(offset, value.asInstanceOf[AnyRef])
  }
}
