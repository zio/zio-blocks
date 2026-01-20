package zio.blocks.schema.bson

import java.time.Instant

import scala.collection.immutable.HashMap
import scala.jdk.CollectionConverters._

import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonElement, BsonNull, BsonReader, BsonType, BsonValue, BsonWriter}

import zio.bson.BsonBuilder._
import zio.bson.DecoderUtils._
import zio.bson.{BsonCodec, BsonDecoder, BsonEncoder, BsonEncoderOps, BsonFieldDecoder, BsonFieldEncoder, BsonTrace}
import zio.blocks.schema.annotation._
import zio.blocks.schema.binding.{Binding, Register, Registers, RegisterOffset}
import zio.blocks.schema.{DynamicValue, Modifier, PrimitiveType, PrimitiveValue, Reflect, Schema, Term, TypeName}
import zio.Chunk

object BsonSchemaCodec {

  type TermMapping = String => String

  /**
   * Strategy for encoding and decoding sum types (sealed traits / enums) in BSON.
   */
  sealed trait SumTypeHandling

  object SumTypeHandling {
    /**
     * Encode each case as a wrapper document keyed by the mapped class name.
     */
    case object WrapperWithClassNameField extends SumTypeHandling

    /**
     * Encode each case as a document containing a discriminator field with the mapped class name.
     */
    final case class DiscriminatorField(name: String) extends SumTypeHandling
  }

  /**
   * Configuration for the BSON schema codec.
   * @param sumTypeHandling The handling of sum types.
   * @param classNameMapping The mapping of class names.
   */
  class Config private (
    val sumTypeHandling: SumTypeHandling,
    val classNameMapping: TermMapping
  ) {

    def withSumTypeHandling(sumTypeHandling: SumTypeHandling): Config =
      copy(sumTypeHandling = sumTypeHandling)

    def withClassNameMapping(classNameMapping: TermMapping): Config =
      copy(classNameMapping = classNameMapping)

    private[this] def copy(
      sumTypeHandling: SumTypeHandling = sumTypeHandling,
      classNameMapping: TermMapping = classNameMapping
    ): Config =
      new Config(sumTypeHandling, classNameMapping)
  }

  object Config
      extends Config(
        sumTypeHandling = SumTypeHandling.WrapperWithClassNameField,
        classNameMapping = identity
      )

  /**
   * Derives a BSON encoder for the provided schema using the given configuration.
   */
  def bsonEncoder[A](schema: Schema[A], config: Config): BsonEncoder[A] =
    BsonSchemaEncoder.schemaEncoder(config)(schema.reflect)

  /**
   * Derives a BSON encoder for the provided schema using the default configuration.
   */
  def bsonEncoder[A](schema: Schema[A]): BsonEncoder[A] =
    bsonEncoder(schema, Config)

  /**
   * Derives a BSON decoder for the provided schema using the given configuration.
   */
  def bsonDecoder[A](schema: Schema[A], config: Config): BsonDecoder[A] =
    BsonSchemaDecoder.schemaDecoder(config)(schema.reflect)

  /**
   * Derives a BSON decoder for the provided schema using the default configuration.
   */
  def bsonDecoder[A](schema: Schema[A]): BsonDecoder[A] =
    bsonDecoder(schema, Config)

  /**
   * Derives a BSON codec for the provided schema using the given configuration.
   */
  def bsonCodec[A](schema: Schema[A], config: Config): BsonCodec[A] =
    BsonCodec(bsonEncoder(schema, config), bsonDecoder(schema, config))

  /**
   * Derives a BSON codec for the provided schema using the default configuration.
   */
  def bsonCodec[A](schema: Schema[A]): BsonCodec[A] =
    bsonCodec(schema, Config)

  object Codecs {
    private[bson] val unitEncoder: BsonEncoder[Unit] = new BsonEncoder[Unit] {
      override def encode(writer: BsonWriter, value: Unit, ctx: BsonEncoder.EncoderContext): Unit =
        if (!ctx.inlineNextObject) {
          writer.writeStartDocument()
          writer.writeEndDocument()
        }

      override def toBsonValue(value: Unit): BsonValue = doc()
    }

    private[bson] val unitDecoder: BsonDecoder[Unit] =
      new BsonDecoder[Unit] {
        private val noExtra = true

        override def decodeUnsafe(
          reader: BsonReader,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): Unit = unsafeCall(trace) {
          reader.readStartDocument()

          while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            val name = reader.readName()

            if (noExtra && !ctx.ignoreExtraField.contains(name)) {
              throw BsonDecoder.Error(BsonTrace.Field(name) :: trace, "Invalid extra field.")
            } else reader.skipValue()
          }

          reader.readEndDocument()

          ()
        }

        override def fromBsonValueUnsafe(
          value: BsonValue,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): Unit =
          assumeType(trace)(BsonType.DOCUMENT, value) { value =>
            if (noExtra) {
              value.asDocument().asScala.keys.foreach { name =>
                if (!ctx.ignoreExtraField.contains(name))
                  throw BsonDecoder.Error(BsonTrace.Field(name) :: trace, "Invalid extra field.")
              }
            }

            ()
          }
      }

    private[bson] val unitCodec: BsonCodec[Unit] = BsonCodec(unitEncoder, unitDecoder)

    private[bson] def tuple2Encoder[A: BsonEncoder, B: BsonEncoder]: BsonEncoder[(A, B)] =
      new BsonEncoder[(A, B)] {
        override def encode(writer: BsonWriter, value: (A, B), ctx: BsonEncoder.EncoderContext): Unit = {
          val nextCtx = BsonEncoder.EncoderContext.default

          if (!ctx.inlineNextObject) writer.writeStartDocument()

          writer.writeName("_1")
          BsonEncoder[A].encode(writer, value._1, nextCtx)

          writer.writeName("_2")
          BsonEncoder[B].encode(writer, value._2, nextCtx)

          if (!ctx.inlineNextObject) writer.writeEndDocument()
        }

        override def toBsonValue(value: (A, B)): BsonValue =
          doc(
            "_1" -> value._1.toBsonValue,
            "_2" -> value._2.toBsonValue
          )
      }

    private[bson] def tuple2Decoder[A: BsonDecoder, B: BsonDecoder]: BsonDecoder[(A, B)] =
      new BsonDecoder[(A, B)] {
        private val noExtra = true

        override def decodeUnsafe(
          reader: BsonReader,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): (A, B) = unsafeCall(trace) {
          val nextCtx = BsonDecoder.BsonDecoderContext.default
          var _1: A   = null.asInstanceOf[A]
          var has_1   = false
          var _2: B   = null.asInstanceOf[B]
          var has_2   = false

          reader.readStartDocument()

          while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            val name = reader.readName()

            def fieldTrace = BsonTrace.Field(name) :: trace

            if (name == "_1") {
              _1 = BsonDecoder[A].decodeUnsafe(reader, fieldTrace, nextCtx)
              has_1 = true
            } else if (name == "_2") {
              _2 = BsonDecoder[B].decodeUnsafe(reader, fieldTrace, nextCtx)
              has_2 = true
            } else if (noExtra && !ctx.ignoreExtraField.contains(name)) {
              throw BsonDecoder.Error(fieldTrace, "Invalid extra field.")
            } else reader.skipValue()
          }

          reader.readEndDocument()

          if (!has_1) _1 = BsonDecoder[A].decodeMissingUnsafe(trace)
          if (!has_2) _2 = BsonDecoder[B].decodeMissingUnsafe(trace)

          (_1, _2)
        }

        override def fromBsonValueUnsafe(
          value: BsonValue,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): (A, B) =
          assumeType(trace)(BsonType.DOCUMENT, value) { value =>
            val nextCtx = BsonDecoder.BsonDecoderContext.default
            var _1: A   = null.asInstanceOf[A]
            var has_1   = false
            var _2: B   = null.asInstanceOf[B]
            var has_2   = false

            value.asDocument().asScala.foreachEntry { (name, value) =>
              def fieldTrace = BsonTrace.Field(name) :: trace

              if (name == "_1") {
                _1 = BsonDecoder[A].fromBsonValueUnsafe(value, fieldTrace, nextCtx)
                has_1 = true
              } else if (name == "_2") {
                _2 = BsonDecoder[B].fromBsonValueUnsafe(value, fieldTrace, nextCtx)
                has_2 = true
              } else if (noExtra && !ctx.ignoreExtraField.contains(name)) {
                throw BsonDecoder.Error(fieldTrace, "Invalid extra field.")
              }
            }

            if (!has_1) _1 = BsonDecoder[A].decodeMissingUnsafe(trace)
            if (!has_2) _2 = BsonDecoder[B].decodeMissingUnsafe(trace)

            (_1, _2)
          }
      }

    private[bson] def tuple2Codec[A: BsonEncoder: BsonDecoder, B: BsonEncoder: BsonDecoder]: BsonCodec[(A, B)] =
      BsonCodec(tuple2Encoder, tuple2Decoder)

    private[bson] def eitherEncoder[A: BsonEncoder, B: BsonEncoder]: BsonEncoder[Either[A, B]] =
      new BsonEncoder[Either[A, B]] {
        override def encode(writer: BsonWriter, value: Either[A, B], ctx: BsonEncoder.EncoderContext): Unit = {
          val nextCtx = BsonEncoder.EncoderContext.default

          if (!ctx.inlineNextObject) writer.writeStartDocument()

          value match {
            case Left(value) =>
              writer.writeName("left")
              BsonEncoder[A].encode(writer, value, nextCtx)
            case Right(value) =>
              writer.writeName("right")
              BsonEncoder[B].encode(writer, value, nextCtx)
          }

          if (!ctx.inlineNextObject) writer.writeEndDocument()
        }

        override def toBsonValue(value: Either[A, B]): BsonValue = value match {
          case Left(value)  => doc("left"  -> value.toBsonValue)
          case Right(value) => doc("right" -> value.toBsonValue)
        }
      }

    private[bson] def eitherDecoder[A: BsonDecoder, B: BsonDecoder]: BsonDecoder[Either[A, B]] =
      new BsonDecoder[Either[A, B]] {
        private val noExtra = true

        override def decodeUnsafe(
          reader: BsonReader,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): Either[A, B] = unsafeCall(trace) {
          val nextCtx  = BsonDecoder.BsonDecoderContext.default
          var left: A  = null.asInstanceOf[A]
          var hasLeft  = false
          var right: B = null.asInstanceOf[B]
          var hasRight = false

          reader.readStartDocument()

          while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            val name = reader.readName()

            def fieldTrace = BsonTrace.Field(name) :: trace

            if (name == "left") {
              left = BsonDecoder[A].decodeUnsafe(reader, fieldTrace, nextCtx)
              hasLeft = true
            } else if (name == "right") {
              right = BsonDecoder[B].decodeUnsafe(reader, fieldTrace, nextCtx)
              hasRight = true
            } else if (noExtra && !ctx.ignoreExtraField.contains(name)) {
              throw BsonDecoder.Error(fieldTrace, "Invalid extra field.")
            } else reader.skipValue()
          }

          reader.readEndDocument()

          if (hasLeft && hasRight) throw BsonDecoder.Error(trace, "Both `left` and `right` cases found.")

          if (hasLeft) Left(left)
          else if (hasRight) Right(right)
          else throw BsonDecoder.Error(trace, "Both `left` and `right` cases missing.")
        }

        override def fromBsonValueUnsafe(
          value: BsonValue,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): Either[A, B] =
          assumeType(trace)(BsonType.DOCUMENT, value) { value =>
            val nextCtx  = BsonDecoder.BsonDecoderContext.default
            var left: A  = null.asInstanceOf[A]
            var hasLeft  = false
            var right: B = null.asInstanceOf[B]
            var hasRight = false

            value.asDocument().asScala.foreachEntry { (name, value) =>
              def fieldTrace = BsonTrace.Field(name) :: trace

              if (name == "left") {
                left = BsonDecoder[A].fromBsonValueUnsafe(value, fieldTrace, nextCtx)
                hasLeft = true
              } else if (name == "right") {
                right = BsonDecoder[B].fromBsonValueUnsafe(value, fieldTrace, nextCtx)
                hasRight = true
              } else if (noExtra && !ctx.ignoreExtraField.contains(name)) {
                throw BsonDecoder.Error(fieldTrace, "Invalid extra field.")
              }
            }

            if (hasLeft && hasRight) throw BsonDecoder.Error(trace, "Both `left` and `right` cases found.")

            if (hasLeft) Left(left)
            else if (hasRight) Right(right)
            else throw BsonDecoder.Error(trace, "Both `left` and `right` cases missing.")
          }
      }

    private[bson] def failDecoder[A](message: String): BsonDecoder[A] =
      new BsonDecoder[A] {
        override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A =
          throw BsonDecoder.Error(trace, message)

        override def fromBsonValueUnsafe(
          value: BsonValue,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): A =
          throw BsonDecoder.Error(trace, message)
      }

    private[bson] def primitiveCodec[A](primitiveType: PrimitiveType[A]): BsonCodec[A] =
      primitiveType match {
        case PrimitiveType.Unit        => unitCodec.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.String   => BsonCodec.string.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Boolean  => BsonCodec.boolean.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Byte     => BsonCodec.byte.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Short    => BsonCodec.short.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Int      => BsonCodec.int.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Long     => BsonCodec.long.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Float    => BsonCodec.float.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Double   => BsonCodec.double.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Char     => BsonCodec.char.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.BigInt   => BsonCodec.bigInt.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.BigDecimal => BsonCodec.bigDecimal.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.DayOfWeek => BsonCodec.dayOfWeek.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Duration  => BsonCodec.duration.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Instant   => BsonCodec.instant.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.LocalDate => BsonCodec.localDate.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.LocalDateTime => BsonCodec.localDateTime.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.LocalTime => BsonCodec.localTime.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Month => BsonCodec.month.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.MonthDay => BsonCodec.monthDay.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.OffsetDateTime => BsonCodec.offsetDateTime.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.OffsetTime => BsonCodec.offsetTime.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Period => BsonCodec.period.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Year => BsonCodec.year.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.YearMonth => BsonCodec.yearMonth.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.ZoneId => BsonCodec.zoneId.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.ZoneOffset => BsonCodec.zoneOffset.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.ZonedDateTime => BsonCodec.zonedDateTime.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.UUID => BsonCodec.uuid.asInstanceOf[BsonCodec[A]]
        case _: PrimitiveType.Currency => BsonCodec.currency.asInstanceOf[BsonCodec[A]]
      }
  }

  object BsonSchemaEncoder {

    import Codecs._

    private[bson] def schemaEncoder[A](config: Config)(schema: Reflect[Binding, A]): BsonEncoder[A] =
      schema.asPrimitive match {
        case Some(primitive) => primitiveCodec(primitive.primitiveType).encoder
        case _ =>
          schema match {
            case deferred: Reflect.Deferred[Binding, A] =>
              lazy val inner = schemaEncoder(config)(deferred.value)
              new BsonEncoder[A] {
                override def isAbsent(value: A): Boolean =
                  inner.isAbsent(value)

                def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit =
                  inner.encode(writer, value, ctx)

                def toBsonValue(value: A): BsonValue = inner.toBsonValue(value)
              }
            case _ if schema.isOption =>
              val inner = schema.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
              BsonEncoder.option(schemaEncoder(config)(inner)).asInstanceOf[BsonEncoder[A]]
            case _ if schema.isVariant =>
              enumEncoder(config)(schema.asVariant.get.asInstanceOf[Reflect.Variant[Binding, A]])
            case _ if schema.isRecord =>
              caseClassEncoder(config)(schema.asRecord.get.asInstanceOf[Reflect.Record[Binding, A]])
            case _ if schema.isSequence =>
              sequenceEncoder(config)(schema.asSequenceUnknown.get.sequence).asInstanceOf[BsonEncoder[A]]
            case _ if schema.isMap =>
              mapEncoder(config)(schema.asMapUnknown.get.map).asInstanceOf[BsonEncoder[A]]
            case _ if schema.isWrapper =>
              wrapperEncoder(config)(schema.asWrapperUnknown.get.wrapper.asInstanceOf[Reflect.Wrapper[Binding, A, Any]])
            case _ if schema.isDynamic =>
              dynamicEncoder(config)(schema.asDynamic.get).asInstanceOf[BsonEncoder[A]]
            case _ =>
              throw new IllegalArgumentException(s"Missing a handler for encoding of schema $schema.")
          }
      }

    private def wrapperEncoder[A, B](config: Config)(schema: Reflect.Wrapper[Binding, A, B]): BsonEncoder[A] = {
      if (isObjectId(schema.modifiers)) {
        BsonEncoder.objectId.contramap[A] { value =>
          new ObjectId(schema.binding.unwrap(value).toString)
        }
      } else {
        val inner = schemaEncoder(config)(schema.wrapped)
        inner.contramap[A](schema.binding.unwrap)
      }
    }

    private[bson] def bsonFieldEncoder[A](schema: Reflect[Binding, A]): Option[BsonFieldEncoder[A]] =
      schema.asPrimitive match {
        case Some(primitive) =>
          primitive.primitiveType match {
            case _: PrimitiveType.String => Some(BsonFieldEncoder.string.asInstanceOf[BsonFieldEncoder[A]])
            case _: PrimitiveType.Long   => Some(BsonFieldEncoder.long.asInstanceOf[BsonFieldEncoder[A]])
            case _: PrimitiveType.Int    => Some(BsonFieldEncoder.int.asInstanceOf[BsonFieldEncoder[A]])
            case _                       => None
          }
        case _ => None
      }

    private[bson] def mapEncoder[K, V, M[_, _]](
      config: Config
    )(schema: Reflect.Map[Binding, K, V, M]): BsonEncoder[M[K, V]] = {
      val valueEncoder = schemaEncoder(config)(schema.value)
      bsonFieldEncoder(schema.key) match {
        case Some(bsonFieldEncoder) =>
          new BsonEncoder[M[K, V]] {
            override def encode(writer: BsonWriter, value: M[K, V], ctx: BsonEncoder.EncoderContext): Unit = {
              val deconstructor = schema.mapDeconstructor
              val it            = deconstructor.deconstruct(value)

              writer.writeStartDocument()

              while (it.hasNext) {
                val next = it.next()
                val key  = deconstructor.getKey(next)
                val v    = deconstructor.getValue(next)
                if (!valueEncoder.isAbsent(v)) {
                  writer.writeName(bsonFieldEncoder.unsafeEncodeField(key))
                  valueEncoder.encode(writer, v, ctx)
                }
              }

              writer.writeEndDocument()
            }

            override def toBsonValue(value: M[K, V]): BsonValue = {
              val deconstructor = schema.mapDeconstructor
              val it            = deconstructor.deconstruct(value)

              val elements = Vector.newBuilder[BsonElement]
              while (it.hasNext) {
                val next = it.next()
                val key  = deconstructor.getKey(next)
                val v    = deconstructor.getValue(next)
                if (!valueEncoder.isAbsent(v)) {
                  elements += element(bsonFieldEncoder.unsafeEncodeField(key), valueEncoder.toBsonValue(v))
                }
              }

              new BsonDocument(elements.result().asJava)
            }
          }
        case None =>
          val tupleEncoder = tuple2Encoder(schemaEncoder(config)(schema.key), valueEncoder)
          new BsonEncoder[M[K, V]] {
            override def encode(writer: BsonWriter, value: M[K, V], ctx: BsonEncoder.EncoderContext): Unit = {
              val deconstructor = schema.mapDeconstructor
              val it            = deconstructor.deconstruct(value)

              writer.writeStartArray()

              while (it.hasNext) {
                val next = it.next()
                tupleEncoder.encode(writer, (deconstructor.getKey(next), deconstructor.getValue(next)), ctx)
              }

              writer.writeEndArray()
            }

            override def toBsonValue(value: M[K, V]): BsonValue = {
              val deconstructor = schema.mapDeconstructor
              val it            = deconstructor.deconstruct(value)
              val values        = Vector.newBuilder[BsonValue]

              while (it.hasNext) {
                val next = it.next()
                values += tupleEncoder.toBsonValue((deconstructor.getKey(next), deconstructor.getValue(next)))
              }

              array(values.result(): _*)
            }
          }
      }
    }

    private def dynamicEncoder(config: Config)(schema: Reflect.Dynamic[Binding]): BsonEncoder[DynamicValue] =
      new BsonEncoder[DynamicValue] {
        override def encode(writer: BsonWriter, value: DynamicValue, ctx: BsonEncoder.EncoderContext): Unit =
          schemaEncoder(config)(schema).encode(writer, value, ctx)

        override def toBsonValue(value: DynamicValue): BsonValue =
          dynamicValueToBson(value)
      }

    private def enumEncoder[Z](
      config: Config
    )(schema: Reflect.Variant[Binding, Z]): BsonEncoder[Z] = {
      val cases = schema.cases
      if (schema.isEnumeration) {
        val caseMap: Map[Z, String] = cases
          .filterNot(isTransient)
          .map { case_ =>
            val name = caseName(config)(case_)
            case_.value.asRecord
              .flatMap(_.getDefaultValue)
              .getOrElse(case_.value.asInstanceOf[Reflect.Record[Binding, Z]].constructor.construct(Registers(0L), 0)) ->
              name
          }
          .toMap
        BsonEncoder.string.contramap(caseMap(_))
      } else if (schema.isOption) {
        val inner = schema.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
        BsonEncoder.option(schemaEncoder(config)(inner)).asInstanceOf[BsonEncoder[Z]]
      } else if (isEither(schema)) {
        val left  = cases(0).value.asInstanceOf[Reflect[Binding, Any]]
        val right = cases(1).value.asInstanceOf[Reflect[Binding, Any]]
        eitherEncoder(schemaEncoder(config)(left), schemaEncoder(config)(right)).asInstanceOf[BsonEncoder[Z]]
      } else {
        val bsonDiscriminator   = schema.modifiers.collectFirst { case d: bsonDiscriminator => d.name }
        val schemaDiscriminator = schema.modifiers.collectFirst { case d: discriminatorName => d.tag }
        val configDiscriminator = config.sumTypeHandling match {
          case SumTypeHandling.WrapperWithClassNameField => None
          case SumTypeHandling.DiscriminatorField(name)  => Some(name)
        }
        val discriminator = bsonDiscriminator.orElse(schemaDiscriminator).orElse(configDiscriminator)

        val noDiscriminators = schema.modifiers.exists { case _: noDiscriminator => true; case _ => false }

        if (noDiscriminators) {
          new BsonEncoder[Z] {
            override def encode(writer: BsonWriter, value: Z, ctx: BsonEncoder.EncoderContext): Unit =
              nonTransientCase(schema, value) match {
                case Some(case_) =>
                  val encoder = schemaEncoder(config)(case_.value).asInstanceOf[BsonEncoder[Z]]
                  encoder.encode(writer, value, BsonEncoder.EncoderContext.default)
                case None =>
                  writer.writeStartDocument()
                  writer.writeEndDocument()
              }

            override def toBsonValue(value: Z): BsonValue =
              nonTransientCase(schema, value) match {
                case Some(case_) =>
                  val encoder = schemaEncoder(config)(case_.value).asInstanceOf[BsonEncoder[Z]]
                  encoder.toBsonValue(value)
                case None => doc()
              }
          }
        } else {
          discriminator match {
            case None =>
              new BsonEncoder[Z] {
                def encode(writer: BsonWriter, value: Z, ctx: BsonEncoder.EncoderContext): Unit = {
                  writer.writeStartDocument()
                  nonTransientCase(schema, value) match {
                    case Some(case_) =>
                      val encoder = schemaEncoder(config)(case_.value).asInstanceOf[BsonEncoder[Z]]

                      val name = caseName(config)(case_)
                      writer.writeName(name)
                      encoder.encode(writer, value, BsonEncoder.EncoderContext.default)
                    case None =>
                  }
                  writer.writeEndDocument()
                }

                def toBsonValue(value: Z): BsonValue =
                  nonTransientCase(schema, value) match {
                    case Some(case_) =>
                      val encoder = schemaEncoder(config)(case_.value).asInstanceOf[BsonEncoder[Z]]

                      val name = caseName(config)(case_)
                      doc(name -> encoder.toBsonValue(value))

                    case None => doc()
                  }
              }
            case Some(discriminator) =>
              new BsonEncoder[Z] {
                def encode(writer: BsonWriter, value: Z, ctx: BsonEncoder.EncoderContext): Unit = {
                  val nextCtx = ctx.copy(inlineNextObject = true)

                  writer.writeStartDocument()

                  nonTransientCase(schema, value) match {
                    case Some(case_) =>
                      val encoder = schemaEncoder(config)(case_.value).asInstanceOf[BsonEncoder[Z]]

                      val name = caseName(config)(case_)
                      writer.writeName(discriminator)
                      writer.writeString(name)
                      encoder.encode(writer, value, nextCtx)

                    case None =>
                  }

                  writer.writeEndDocument()
                }

                def toBsonValue(value: Z): BsonValue =
                  nonTransientCase(schema, value) match {
                    case Some(case_) =>
                      val encoder  = schemaEncoder(config)(case_.value).asInstanceOf[BsonEncoder[Z]]
                      val caseBson = encoder.toBsonValue(value)

                      if (!caseBson.isDocument) throw new RuntimeException("Subtype is not encoded as an object")

                      val doc  = caseBson.asDocument()
                      val name = caseName(config)(case_)
                      doc.put(discriminator, str(name))
                      doc
                    case None => doc()
                  }
              }
          }
        }
      }
    }

    private def caseClassEncoder[Z](config: Config)(
      schema: Reflect.Record[Binding, Z]
    ): BsonEncoder[Z] = new BsonEncoder[Z] {
      private val keepNulls = false

      private val fields = schema.fields

      private val fieldIndices = fields.indices.filterNot(idx => isTransient(fields(idx))).toArray
      private val nonTransientFields = fieldIndices.map(fields(_))
      private val allRegisters = recordRegisters(schema)

      private val names: Array[String] =
        nonTransientFields.map(encodedFieldName)

      private lazy val encoders: Array[BsonEncoder[Any]] =
        nonTransientFields.map(s => schemaEncoder(config)(s.value).asInstanceOf[BsonEncoder[Any]])

      private val registers = fieldIndices.map(allRegisters).asInstanceOf[Array[Register[Any]]]
      private val len       = nonTransientFields.length
      private val usedRegisters = Reflect.Record.usedRegisters(allRegisters)

      def encode(writer: BsonWriter, value: Z, ctx: BsonEncoder.EncoderContext): Unit =
        if (names.length == 1 && names(0) == ObjectIdTag) {
          val fieldValue  = fieldValueAt(value, 0, registers(0))
          val id          = new ObjectId(fieldValue.toString)
          writer.writeObjectId(id)
        } else {
          if (!ctx.inlineNextObject) writer.writeStartDocument()

          var i = 0

          while (i < len) {
            val tc         = encoders(i)
            val fieldValue = fieldValueAt(value, i, registers(i))

            if (keepNulls || !tc.isAbsent(fieldValue)) {
              writer.writeName(names(i))
              tc.encode(writer, fieldValue, BsonEncoder.EncoderContext.default)
            }

            i += 1
          }

          if (!ctx.inlineNextObject) writer.writeEndDocument()
        }

      def toBsonValue(value: Z): BsonValue =
        if (names.length == 1 && names(0) == ObjectIdTag) {
          val fieldValue = fieldValueAt(value, 0, registers(0))
          val id         = new ObjectId(fieldValue.toString)
          id.toBsonValue
        } else {
          val elements = nonTransientFields.indices.view.flatMap { idx =>
            val fieldValue = fieldValueAt(value, idx, registers(idx))
            val tc         = encoders(idx)

            if (keepNulls || !tc.isAbsent(fieldValue)) Some(element(names(idx), tc.toBsonValue(fieldValue)))
            else None
          }.to(Chunk)

          new BsonDocument(elements.asJava)
        }

      private def fieldValueAt(value: Z, idx: Int, register: Register[Any]): Any =
        value match {
          case product: Product if product.productArity == fields.length =>
            product.productElement(fieldIndices(idx))
          case _ =>
            val deconstructor = schema.deconstructor
            val registers     = Registers(usedRegisters)
            deconstructor.deconstruct(registers, 0, value)
            register.get(registers, 0)
        }
    }

    private def sequenceEncoder[A, C[_]](config: Config)(schema: Reflect.Sequence[Binding, A, C]): BsonEncoder[C[A]] = {
      val elementEncoder = schemaEncoder(config)(schema.element)
      new BsonEncoder[C[A]] {
        override def encode(writer: BsonWriter, value: C[A], ctx: BsonEncoder.EncoderContext): Unit = {
          val deconstructor = schema.seqDeconstructor
          val iterator      = deconstructor.deconstruct(value)
          writer.writeStartArray()
          while (iterator.hasNext) {
            elementEncoder.encode(writer, iterator.next(), ctx)
          }
          writer.writeEndArray()
        }

        override def toBsonValue(value: C[A]): BsonValue = {
          val deconstructor = schema.seqDeconstructor
          val iterator      = deconstructor.deconstruct(value)
          val values        = Vector.newBuilder[BsonValue]
          while (iterator.hasNext) {
            values += elementEncoder.toBsonValue(iterator.next())
          }
          array(values.result(): _*)
        }
      }
    }
  }

  object BsonSchemaDecoder {

    import Codecs._

    private[bson] def schemaDecoder[A](config: Config)(schema: Reflect[Binding, A]): BsonDecoder[A] =
      schema.asPrimitive match {
        case Some(primitive) => primitiveCodec(primitive.primitiveType).decoder
        case None =>
          schema match {
            case deferred: Reflect.Deferred[Binding, A] =>
              lazy val inner = schemaDecoder(config)(deferred.value)
              new BsonDecoder[A] {
                override def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A =
                  inner.decodeUnsafe(reader, trace, ctx)

                override def decodeMissingUnsafe(trace: List[BsonTrace]): A =
                  inner.decodeMissingUnsafe(trace)

                override def fromBsonValueUnsafe(
                  value: BsonValue,
                  trace: List[BsonTrace],
                  ctx: BsonDecoder.BsonDecoderContext
                ): A =
                  inner.fromBsonValueUnsafe(value, trace, ctx)
              }
            case _ if schema.isOption =>
              val inner = schema.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
              BsonDecoder.option(schemaDecoder(config)(inner)).asInstanceOf[BsonDecoder[A]]
            case _ if schema.isVariant =>
              enumDecoder(config)(schema.asVariant.get.asInstanceOf[Reflect.Variant[Binding, A]])
            case _ if schema.isRecord =>
              caseClassDecoder(config)(schema.asRecord.get.asInstanceOf[Reflect.Record[Binding, A]])
            case _ if schema.isSequence =>
              sequenceDecoder(config)(schema.asSequenceUnknown.get.sequence).asInstanceOf[BsonDecoder[A]]
            case _ if schema.isMap =>
              mapDecoder(config)(schema.asMapUnknown.get.map).asInstanceOf[BsonDecoder[A]]
            case _ if schema.isWrapper =>
              wrapperDecoder(config)(schema.asWrapperUnknown.get.wrapper.asInstanceOf[Reflect.Wrapper[Binding, A, Any]])
            case _ if schema.isDynamic =>
              dynamicDecoder.asInstanceOf[BsonDecoder[A]]
            case _ =>
              throw new IllegalArgumentException(s"Missing a handler for decoding of schema $schema.")
          }
      }

    private def wrapperDecoder[A, B](config: Config)(schema: Reflect.Wrapper[Binding, A, B]): BsonDecoder[A] = {
      if (isObjectId(schema.modifiers)) {
        BsonDecoder.objectId.mapOrFail { oid =>
          schema.binding.wrap(oid.toHexString.asInstanceOf[B]).left.map(identity)
        }
      } else {
        schemaDecoder(config)(schema.wrapped).mapOrFail(schema.binding.wrap)
      }
    }

    private[bson] def mapDecoder[K, V, M[_, _]](
      config: Config
    )(schema: Reflect.Map[Binding, K, V, M]): BsonDecoder[M[K, V]] = {
      val valueDecoder = schemaDecoder(config)(schema.value)
      bsonFieldDecoder(schema.key) match {
        case Some(bsonFieldDecoder) =>
          new BsonDecoder[M[K, V]] {
            override def decodeUnsafe(
              reader: BsonReader,
              trace: List[BsonTrace],
              ctx: BsonDecoder.BsonDecoderContext
            ): M[K, V] = {
              val constructor = schema.mapConstructor
              val builder     = constructor.newObjectBuilder[K, V]()

              unsafeCall(trace) {
                reader.readStartDocument()

                while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                  val fieldName = reader.readName()
                  val docTrace  = BsonTrace.Field(fieldName) :: trace
                  val value     = valueDecoder.decodeUnsafe(reader, docTrace, ctx)
                  constructor.addObject(builder, bsonFieldDecoder.unsafeDecodeField(docTrace, fieldName), value)
                }

                reader.readEndDocument()
              }

              constructor.resultObject(builder)
            }

            override def fromBsonValueUnsafe(
              value: BsonValue,
              trace: List[BsonTrace],
              ctx: BsonDecoder.BsonDecoderContext
            ): M[K, V] =
              assumeType(trace)(BsonType.DOCUMENT, value) { value =>
                val constructor = schema.mapConstructor
                val builder     = constructor.newObjectBuilder[K, V]()
                value.asDocument().forEach { (fieldName: String, element: BsonValue) =>
                  val docTrace = BsonTrace.Field(fieldName) :: trace
                  val value    = valueDecoder.fromBsonValueUnsafe(element, docTrace, ctx)
                  constructor.addObject(builder, bsonFieldDecoder.unsafeDecodeField(docTrace, fieldName), value)
                }
                constructor.resultObject(builder)
              }
          }
        case None =>
          val tupleDecoder = tuple2Decoder(schemaDecoder(config)(schema.key), valueDecoder)
          new BsonDecoder[M[K, V]] {
            override def decodeUnsafe(
              reader: BsonReader,
              trace: List[BsonTrace],
              ctx: BsonDecoder.BsonDecoderContext
            ): M[K, V] = {
              val constructor = schema.mapConstructor
              val builder     = constructor.newObjectBuilder[K, V]()

              unsafeCall(trace)(reader.readStartArray())
              var idx = 0
              while (unsafeCall(trace)(reader.readBsonType()) != BsonType.END_OF_DOCUMENT) {
                val arrayTrace = BsonTrace.Array(idx) :: trace
                val entry      = tupleDecoder.decodeUnsafe(reader, arrayTrace, ctx)
                constructor.addObject(builder, entry._1, entry._2)
                idx += 1
              }
              unsafeCall(trace)(reader.readEndArray())

              constructor.resultObject(builder)
            }

            override def fromBsonValueUnsafe(
              value: BsonValue,
              trace: List[BsonTrace],
              ctx: BsonDecoder.BsonDecoderContext
            ): M[K, V] =
              assumeType(trace)(BsonType.ARRAY, value) { value =>
                val constructor = schema.mapConstructor
                val builder     = constructor.newObjectBuilder[K, V]()
                var idx         = 0
                value.asArray().forEach { element =>
                  val arrayTrace = BsonTrace.Array(idx) :: trace
                  val entry      = tupleDecoder.fromBsonValueUnsafe(element, arrayTrace, ctx)
                  constructor.addObject(builder, entry._1, entry._2)
                  idx += 1
                }
                constructor.resultObject(builder)
              }
          }
      }
    }

    private[bson] def bsonFieldDecoder[A](schema: Reflect[Binding, A]): Option[BsonFieldDecoder[A]] =
      schema.asPrimitive match {
        case Some(primitive) =>
          primitive.primitiveType match {
            case _: PrimitiveType.String => Some(BsonFieldDecoder.string.asInstanceOf[BsonFieldDecoder[A]])
            case _: PrimitiveType.Long   => Some(BsonFieldDecoder.long.asInstanceOf[BsonFieldDecoder[A]])
            case _: PrimitiveType.Int    => Some(BsonFieldDecoder.int.asInstanceOf[BsonFieldDecoder[A]])
            case _                       => None
          }
        case _ => None
      }

    private def dynamicDecoder: BsonDecoder[DynamicValue] =
      BsonDecoder.bsonValueDecoder[BsonValue].map(bsonToDynamicValue)

    private def enumDecoder[Z](config: Config)(schema: Reflect.Variant[Binding, Z]): BsonDecoder[Z] = {
      val cases           = schema.cases
      val caseNameAliases = aliasesByCaseName(config)(cases)

      if (schema.isEnumeration) {
        val caseMap: Map[String, Z] =
          cases.map { case_ =>
            caseName(config)(case_) ->
              case_.value.asInstanceOf[Reflect.Record[Binding, Z]].constructor.construct(Registers(0L), 0)
          }.toMap
        BsonDecoder.string.mapOrFail(
          s =>
            caseMap.get(caseNameAliases.getOrElse(s, s)) match {
              case Some(z) => Right(z)
              case None    => Left("unrecognized string")
            }
        )
      } else if (schema.isOption) {
        val inner = schema.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
        BsonDecoder.option(schemaDecoder(config)(inner)).asInstanceOf[BsonDecoder[Z]]
      } else if (isEither(schema)) {
        val left  = cases(0).value.asInstanceOf[Reflect[Binding, Any]]
        val right = cases(1).value.asInstanceOf[Reflect[Binding, Any]]
        eitherDecoder(schemaDecoder(config)(left), schemaDecoder(config)(right)).asInstanceOf[BsonDecoder[Z]]
      } else {

        val noDiscriminators = schema.modifiers.exists { case _: noDiscriminator => true; case _ => false }

        if (noDiscriminators) {
          new BsonDecoder[Z] {
            override def decodeUnsafe(
              reader: BsonReader,
              trace: List[BsonTrace],
              ctx: BsonDecoder.BsonDecoderContext
            ): Z =
              unsafeCall(trace) {
                val mark              = reader.getMark
                val it                = cases.iterator
                var result: Option[Z] = None

                while (result.isEmpty && it.hasNext) {
                  val c = it.next()
                  try {
                    val decoded = schemaDecoder(config)(c.value).decodeUnsafe(reader, trace, ctx).asInstanceOf[Z]
                    result = Some(decoded)
                  } catch {
                    case _: Exception => mark.reset()
                  }
                }

                result match {
                  case Some(value) => value
                  case None        => throw BsonDecoder.Error(trace, "none of the subtypes could decode the data")
                }
              }

            override def fromBsonValueUnsafe(
              value: BsonValue,
              trace: List[BsonTrace],
              ctx: BsonDecoder.BsonDecoderContext
            ): Z = unsafeCall(trace) {
              val it                = cases.iterator
              var result: Option[Z] = None

              while (result.isEmpty && it.hasNext) {
                val c = it.next()
                try {
                  val decoded = schemaDecoder(config)(c.value).fromBsonValueUnsafe(value, trace, ctx).asInstanceOf[Z]
                  result = Some(decoded)
                } catch {
                  case _: Exception =>
                }
              }

              result match {
                case Some(value) => value
                case None        => throw BsonDecoder.Error(trace, "none of the subtypes could decode the data")
              }
            }
          }
        } else {
          val discriminators = schema.modifiers.collect {
            case d: bsonDiscriminator => d.name
            case d: discriminatorName => d.tag
          }.toSet ++ (config.sumTypeHandling match {
            case SumTypeHandling.WrapperWithClassNameField => Set.empty[String]
            case SumTypeHandling.DiscriminatorField(name)  => Set(name)
          })

          val casesIndex = Map(cases.map(c => caseName(config)(c) -> c): _*)

          def getCase(name: String) = casesIndex.get(caseNameAliases.getOrElse(name, name))

          if (discriminators.isEmpty) {
            new BsonDecoder[Z] {
              def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Z =
                unsafeCall(trace) {
                  reader.readStartDocument()

                  val name      = reader.readName()
                  val nextTrace = BsonTrace.Field(name) :: trace
                  val nextCtx   = BsonDecoder.BsonDecoderContext.default

                  val result =
                    getCase(name) match {
                      case None    => throw BsonDecoder.Error(nextTrace, s"Invalid disambiguator $name.")
                      case Some(c) => schemaDecoder(config)(c.value).decodeUnsafe(reader, nextTrace, nextCtx)
                    }

                  reader.readEndDocument()

                  result.asInstanceOf[Z]
                }

              def fromBsonValueUnsafe(
                value: BsonValue,
                trace: List[BsonTrace],
                ctx: BsonDecoder.BsonDecoderContext
              ): Z =
                assumeType(trace)(BsonType.DOCUMENT, value) { value =>
                  val fields = value.asDocument().asScala

                  if (fields.size != 1) throw BsonDecoder.Error(trace, "Expected exactly 1 disambiguator.")

                  val (name, element) = fields.head
                  val nextTrace       = BsonTrace.Field(name) :: trace
                  val nextCtx         = BsonDecoder.BsonDecoderContext.default

                  getCase(name) match {
                    case None => throw BsonDecoder.Error(nextTrace, s"Invalid disambiguator $name.")
                    case Some(c) =>
                      schemaDecoder(config)(c.value).fromBsonValueUnsafe(element, nextTrace, nextCtx).asInstanceOf[Z]
                  }
                }
            }
          } else {
            new BsonDecoder[Z] {
              def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Z =
                unsafeCall(trace) {
                  val mark = reader.getMark

                  var hint: String          = null
                  var discriminator: String = null

                  reader.readStartDocument()

                  var bsonType = reader.readBsonType()
                  while (hint == null && bsonType != BsonType.END_OF_DOCUMENT) {
                    val name = reader.readName()
                    if (discriminators.contains(name) && bsonType == BsonType.STRING) {
                      hint = unsafeCall(BsonTrace.Field(name) :: trace)(reader.readString())
                      discriminator = name
                    } else reader.skipValue()

                    bsonType = reader.readBsonType()
                  }

                  if (hint == null)
                    throw BsonDecoder.Error(
                      trace,
                      s"Missing disambiguator. Expected any of: ${discriminators.mkString(", ")}."
                    )

                  getCase(hint) match {
                    case None =>
                      throw BsonDecoder.Error(BsonTrace.Field(discriminator) :: trace, s"Invalid disambiguator $hint.")
                    case Some(c) =>
                      mark.reset()
                      val nextCtx = ctx.copy(ignoreExtraField = Some(discriminator))
                      schemaDecoder(config)(c.value).decodeUnsafe(reader, trace, nextCtx).asInstanceOf[Z]
                  }
                }

              def fromBsonValueUnsafe(
                value: BsonValue,
                trace: List[BsonTrace],
                ctx: BsonDecoder.BsonDecoderContext
              ): Z =
                assumeType(trace)(BsonType.DOCUMENT, value) { value =>
                  val fields = value.asDocument().asScala

                  val discriminatorHint = discriminators.collectFirst {
                    case discriminator if fields.contains(discriminator) => discriminator -> fields(discriminator)
                  }
                  discriminatorHint match {
                    case None =>
                      throw BsonDecoder.Error(
                        trace,
                        s"Missing disambiguator. Expected any of: ${discriminators.mkString(", ")}."
                      )
                    case Some((discriminator, hint)) =>
                      assumeType(BsonTrace.Field(discriminator) :: trace)(BsonType.STRING, hint) { hint =>
                        getCase(hint.asString().getValue) match {
                          case None =>
                            throw BsonDecoder.Error(trace, s"Invalid disambiguator ${hint.asString().getValue}.")
                          case Some(c) =>
                            val nextCtx = ctx.copy(ignoreExtraField = Some(discriminator))
                            schemaDecoder(config)(c.value).fromBsonValueUnsafe(value, trace, nextCtx).asInstanceOf[Z]
                        }
                      }
                  }
                }
            }

          }
        }
      }
    }

    private def caseClassDecoder[Z](config: Config)(schema: Reflect.Record[Binding, Z]): BsonDecoder[Z] = {
      val fields     = schema.fields
      val len: Int   = fields.length
      val fieldNames = fields.map(encodedFieldName).toArray
      val spans: Array[BsonTrace] = fieldNames.map(f => BsonTrace.Field(f))
      val decoders: Array[BsonDecoder[Any]] =
        fields.map(s => schemaDecoder(config)(s.value).asInstanceOf[BsonDecoder[Any]]).toArray
      val fieldAliases = fields.flatMap {
        case field =>
          val aliases = fieldAliasesFrom(field)
          aliases.map(_ -> fieldNames.indexOf(encodedFieldName(field))) :+ (field.name -> fieldNames.indexOf(
            encodedFieldName(field)
          ))
      }.toMap
      val indexes = HashMap((fieldAliases ++ fieldNames.zipWithIndex).toSeq: _*)
      val noExtra = schema.modifiers.exists {
        case _: rejectExtraFields => true
        case _: bsonNoExtraFields => true
        case _                    => false
      }
      val registers = recordRegisters(schema)
      val defaults  = fields.map(_.value.getDefaultValue.map(_.asInstanceOf[Any])).toArray
      val optionals = fields.map(field => isOptional(field.value)).toArray
      val transients = fields.map(isTransient).toArray
      val constructor = schema.constructor

      new BsonDecoder[Z] {
        def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Z =
          if (fieldNames.length == 1 && fieldNames(0) == ObjectIdTag) {
            val id = reader.readObjectId().toHexString
            val regs = Registers(constructor.usedRegisters)
            registers(0).set(regs, 0, id)
            constructor.construct(regs, 0)
          } else {
            unsafeCall(trace) {
              reader.readStartDocument()

              val nextCtx    = BsonDecoder.BsonDecoderContext.default
              val regs       = Registers(constructor.usedRegisters)
              val seen       = Array.fill(len)(false)

              while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                val name = reader.readName()
                val idx  = indexes.getOrElse(name, -1)

                if (idx >= 0) {
                  val nextTrace = spans(idx) :: trace
                  val tc        = decoders(idx)
                  if (seen(idx)) throw BsonDecoder.Error(nextTrace, "duplicate")
                  val value = tc.decodeUnsafe(reader, nextTrace, nextCtx)
                  registers(idx).set(regs, 0, value)
                  seen(idx) = true
                } else if (noExtra && !ctx.ignoreExtraField.contains(name)) {
                  throw BsonDecoder.Error(BsonTrace.Field(name) :: trace, "Invalid extra field.")
                } else reader.skipValue()
              }

              var i = 0
              while (i < len) {
                if (!seen(i)) {
                  defaults(i) match {
                    case Some(defaultValue) => registers(i).set(regs, 0, defaultValue)
                    case None if optionals(i) || transients(i) =>
                      registers(i).set(regs, 0, None)
                    case None =>
                      registers(i).set(regs, 0, decoders(i).decodeMissingUnsafe(spans(i) :: trace))
                  }
                }
                i += 1
              }

              reader.readEndDocument()

              constructor.construct(regs, 0)
            }
          }

        def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Z =
          if (value.getBsonType == BsonType.OBJECT_ID) {
            val regs = Registers(constructor.usedRegisters)
            registers(0).set(regs, 0, value.asObjectId().getValue.toHexString)
            constructor.construct(regs, 0)
          } else {
            assumeType(trace)(BsonType.DOCUMENT, value) { value =>
              val nextCtx = BsonDecoder.BsonDecoderContext.default
              val regs    = Registers(constructor.usedRegisters)
              val seen    = Array.fill(len)(false)

              value.asDocument().asScala.foreachEntry { (name, value) =>
                val idx = indexes.getOrElse(name, -1)

                if (idx >= 0) {
                  val nextTrace = spans(idx) :: trace
                  val tc        = decoders(idx)
                  if (seen(idx)) throw BsonDecoder.Error(nextTrace, "duplicate")
                  val decoded = tc.fromBsonValueUnsafe(value, nextTrace, nextCtx)
                  registers(idx).set(regs, 0, decoded)
                  seen(idx) = true
                } else if (noExtra && !ctx.ignoreExtraField.contains(name))
                  throw BsonDecoder.Error(BsonTrace.Field(name) :: trace, "Invalid extra field.")
              }

              var i = 0
              while (i < len) {
                if (!seen(i)) {
                  defaults(i) match {
                    case Some(defaultValue) => registers(i).set(regs, 0, defaultValue)
                    case None if optionals(i) || transients(i) => registers(i).set(regs, 0, None)
                    case None =>
                      registers(i).set(regs, 0, decoders(i).decodeMissingUnsafe(spans(i) :: trace))
                  }
                }
                i += 1
              }

              constructor.construct(regs, 0)
            }
          }
      }
    }

    private def sequenceDecoder[A, C[_]](config: Config)(schema: Reflect.Sequence[Binding, A, C]): BsonDecoder[C[A]] = {
      val elementDecoder = schemaDecoder(config)(schema.element)
      new BsonDecoder[C[A]] {
        override def decodeUnsafe(
          reader: BsonReader,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): C[A] = {
          unsafeCall(trace)(reader.readStartArray())

          val constructor = schema.seqConstructor
          val builder     = constructor.newObjectBuilder[A]()
          var idx         = 0
          while (unsafeCall(trace)(reader.readBsonType()) != BsonType.END_OF_DOCUMENT) {
            val arrayTrace = BsonTrace.Array(idx) :: trace
            constructor.addObject(builder, elementDecoder.decodeUnsafe(reader, arrayTrace, ctx))
            idx += 1
          }

          unsafeCall(trace)(reader.readEndArray())

          constructor.resultObject(builder)
        }

        override def fromBsonValueUnsafe(
          value: BsonValue,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): C[A] =
          assumeType(trace)(BsonType.ARRAY, value) { value =>
            val constructor = schema.seqConstructor
            val builder     = constructor.newObjectBuilder[A]()
            var idx         = 0
            value.asArray().forEach { (element: BsonValue) =>
              val arrayTrace = BsonTrace.Array(idx) :: trace
              constructor.addObject(builder, elementDecoder.fromBsonValueUnsafe(element, arrayTrace, ctx))
              idx += 1
            }
            constructor.resultObject(builder)
          }
      }
    }

  }

  private[bson] def bsonToDynamicValue(bsonValue: BsonValue): DynamicValue =
    bsonValue.getBsonType match {
      case BsonType.END_OF_DOCUMENT => DynamicValue.Primitive(PrimitiveValue.Unit)
      case BsonType.DOUBLE          => DynamicValue.Primitive(new PrimitiveValue.Double(bsonValue.asDouble().getValue))
      case BsonType.STRING          => DynamicValue.Primitive(new PrimitiveValue.String(bsonValue.asString().getValue))
      case BsonType.DOCUMENT =>
        val values = bsonValue
          .asDocument()
          .asScala
          .toSeq
          .map {
            case (k, v) => k -> bsonToDynamicValue(v)
          }

        DynamicValue.Record(values.toVector)
      case BsonType.ARRAY =>
        DynamicValue.Sequence(bsonValue.asArray().getValues.asScala.map(bsonToDynamicValue).toVector)
      case BsonType.BINARY =>
        val bytes = bsonValue.asBinary().getData
        DynamicValue.Sequence(bytes.toVector.map(byte => DynamicValue.Primitive(new PrimitiveValue.Byte(byte))))
      case BsonType.UNDEFINED => DynamicValue.Primitive(PrimitiveValue.Unit)
      case BsonType.OBJECT_ID =>
        DynamicValue.Record(
          Vector(
            ObjectIdTag -> DynamicValue.Primitive(
              new PrimitiveValue.String(bsonValue.asObjectId().getValue.toHexString)
            )
          )
        )
      case BsonType.BOOLEAN => DynamicValue.Primitive(new PrimitiveValue.Boolean(bsonValue.asBoolean().getValue))
      case BsonType.DATE_TIME =>
        DynamicValue.Primitive(new PrimitiveValue.Instant(Instant.ofEpochMilli(bsonValue.asDateTime().getValue)))
      case BsonType.NULL                  => DynamicValue.Primitive(PrimitiveValue.Unit)
      case BsonType.REGULAR_EXPRESSION    => DynamicValue.Primitive(PrimitiveValue.Unit)
      case BsonType.DB_POINTER            => DynamicValue.Primitive(PrimitiveValue.Unit)
      case BsonType.JAVASCRIPT            => DynamicValue.Primitive(PrimitiveValue.Unit)
      case BsonType.SYMBOL                => DynamicValue.Primitive(new PrimitiveValue.String(bsonValue.asSymbol().getSymbol))
      case BsonType.JAVASCRIPT_WITH_SCOPE => DynamicValue.Primitive(PrimitiveValue.Unit)
      case BsonType.INT32 => DynamicValue.Primitive(new PrimitiveValue.Int(bsonValue.asInt32().getValue))
      case BsonType.TIMESTAMP =>
        DynamicValue.Primitive(new PrimitiveValue.Instant(Instant.ofEpochMilli(bsonValue.asTimestamp().getValue)))
      case BsonType.INT64 => DynamicValue.Primitive(new PrimitiveValue.Long(bsonValue.asInt64().getValue))
      case BsonType.DECIMAL128 =>
        DynamicValue.Primitive(
          new PrimitiveValue.BigDecimal(bsonValue.asDecimal128().getValue.bigDecimalValue())
        )
      case BsonType.MIN_KEY => DynamicValue.Primitive(PrimitiveValue.Unit)
      case BsonType.MAX_KEY => DynamicValue.Primitive(PrimitiveValue.Unit)
    }

  private[bson] def dynamicValueToBson(value: DynamicValue): BsonValue =
    value match {
      case DynamicValue.Primitive(primitive) =>
        primitive match {
          case PrimitiveValue.Unit             => BsonNull.VALUE
          case PrimitiveValue.Boolean(value)   => BsonCodec.boolean.encoder.toBsonValue(value)
          case PrimitiveValue.Byte(value)      => BsonCodec.byte.encoder.toBsonValue(value)
          case PrimitiveValue.Short(value)     => BsonCodec.short.encoder.toBsonValue(value)
          case PrimitiveValue.Int(value)       => BsonCodec.int.encoder.toBsonValue(value)
          case PrimitiveValue.Long(value)      => BsonCodec.long.encoder.toBsonValue(value)
          case PrimitiveValue.Float(value)     => BsonCodec.float.encoder.toBsonValue(value)
          case PrimitiveValue.Double(value)    => BsonCodec.double.encoder.toBsonValue(value)
          case PrimitiveValue.Char(value)      => BsonCodec.char.encoder.toBsonValue(value)
          case PrimitiveValue.String(value)    => BsonCodec.string.encoder.toBsonValue(value)
          case PrimitiveValue.BigInt(value)    => BsonCodec.bigInt.encoder.toBsonValue(value)
          case PrimitiveValue.BigDecimal(value) => BsonCodec.bigDecimal.encoder.toBsonValue(value)
          case PrimitiveValue.DayOfWeek(value) => BsonCodec.dayOfWeek.encoder.toBsonValue(value)
          case PrimitiveValue.Duration(value)  => BsonCodec.duration.encoder.toBsonValue(value)
          case PrimitiveValue.Instant(value)   => BsonCodec.instant.encoder.toBsonValue(value)
          case PrimitiveValue.LocalDate(value) => BsonCodec.localDate.encoder.toBsonValue(value)
          case PrimitiveValue.LocalDateTime(value) => BsonCodec.localDateTime.encoder.toBsonValue(value)
          case PrimitiveValue.LocalTime(value) => BsonCodec.localTime.encoder.toBsonValue(value)
          case PrimitiveValue.Month(value)     => BsonCodec.month.encoder.toBsonValue(value)
          case PrimitiveValue.MonthDay(value)  => BsonCodec.monthDay.encoder.toBsonValue(value)
          case PrimitiveValue.OffsetDateTime(value) => BsonCodec.offsetDateTime.encoder.toBsonValue(value)
          case PrimitiveValue.OffsetTime(value) => BsonCodec.offsetTime.encoder.toBsonValue(value)
          case PrimitiveValue.Period(value)    => BsonCodec.period.encoder.toBsonValue(value)
          case PrimitiveValue.Year(value)      => BsonCodec.year.encoder.toBsonValue(value)
          case PrimitiveValue.YearMonth(value) => BsonCodec.yearMonth.encoder.toBsonValue(value)
          case PrimitiveValue.ZoneId(value)    => BsonCodec.zoneId.encoder.toBsonValue(value)
          case PrimitiveValue.ZoneOffset(value) => BsonCodec.zoneOffset.encoder.toBsonValue(value)
          case PrimitiveValue.ZonedDateTime(value) => BsonCodec.zonedDateTime.encoder.toBsonValue(value)
          case PrimitiveValue.UUID(value)      => BsonCodec.uuid.encoder.toBsonValue(value)
          case PrimitiveValue.Currency(value)  => BsonCodec.currency.encoder.toBsonValue(value)
        }
      case DynamicValue.Record(fields) =>
        new BsonDocument(fields.view.map { case (key, value) => element(key, dynamicValueToBson(value)) }.to(Chunk).asJava)
      case DynamicValue.Sequence(values) =>
        array(values.map(dynamicValueToBson): _*)
      case DynamicValue.Variant(caseName, value) =>
        doc(caseName -> dynamicValueToBson(value))
      case DynamicValue.Map(entries) =>
        array(entries.map { case (key, value) => doc("_1" -> dynamicValueToBson(key), "_2" -> dynamicValueToBson(value)) }: _*)
    }

  private def isObjectId(modifiers: Seq[Modifier.Reflect]): Boolean =
    modifiers.collectFirst { case Modifier.config(ObjectIdConfigKey, "true") => () }.isDefined

  private def isTransient(term: Term[Binding, ?, ?]): Boolean =
    term.modifiers.exists {
      case _: Modifier.transient => true
      case _: transientField     => true
      case _: transientCase      => true
      case _: bsonExclude        => true
      case _                     => false
    }

  private def encodedFieldName(term: Term[Binding, ?, ?]): String =
    term.modifiers.collectFirst { case m: bsonField => m.name }
      .orElse(term.modifiers.collectFirst { case m: fieldName => m.name })
      .orElse(term.modifiers.collectFirst { case m: Modifier.rename => m.name })
      .getOrElse(term.name)

  private def fieldAliasesFrom(term: Term[Binding, ?, ?]): Seq[String] =
    term.modifiers.collect {
      case m: fieldNameAliases => m.aliases
      case m: Modifier.alias   => Seq(m.name)
    }.flatten

  private def aliasesByCaseName[A](config: Config)(
    cases: IndexedSeq[Term[Binding, A, _]]
  ): Map[String, String] =
    cases.flatMap { case_ =>
      val aliases = case_.modifiers.collect {
        case a: caseNameAliases => a.aliases.toList
        case a: caseName        => List(a.name)
        case a: bsonHint        => List(a.name)
        case a: Modifier.rename => List(a.name)
        case a: Modifier.alias  => List(a.name)
      }.flatten
      val mappedName = caseName(config)(case_)
      aliases.map(_ -> mappedName)
    }.toMap

  private def caseName[A](config: Config)(case_ : Term[Binding, A, _]): String = {
    case_.modifiers.collectFirst { case m: bsonHint => m.name }
      .orElse(case_.modifiers.collectFirst { case m: caseName => m.name })
      .orElse(case_.modifiers.collectFirst { case m: Modifier.rename => m.name })
      .getOrElse(config.classNameMapping(case_.name))
  }

  private def nonTransientCase[A](schema: Reflect.Variant[Binding, A], value: A): Option[Term[Binding, A, _]] = {
    val idx  = schema.discriminator.discriminate(value)
    val term = schema.cases(idx)
    if (isTransient(term)) None else Some(term)
  }

  private def isOptional[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    reflect.isOption

  private def isEither[A](variant: Reflect.Variant[Binding, A]): Boolean =
    variant.typeName.namespace == TypeName.option(TypeName.string).namespace &&
      variant.typeName.name == "Either" && variant.cases.length == 2

  private def recordRegisters(schema: Reflect.Record[Binding, ?]): Array[Register[Any]] = {
    if (!schema.fields.exists(_.value.isInstanceOf[Reflect.Deferred[Binding, ?]])) {
      schema.registers.toArray.asInstanceOf[Array[Register[Any]]]
    } else {
      var offset    = 0L
      val registers = new Array[Register[?]](schema.fields.length)
      schema.fields.zipWithIndex.foreach { case (field, idx) =>
        val primitive = field.value match {
          case _: Reflect.Deferred[Binding, ?] => None
          case _ => Reflect.unwrapToPrimitiveTypeOption(field.value)
        }
        primitive match {
          case Some(primitiveType) =>
            primitiveType match {
              case PrimitiveType.Unit =>
                registers(idx) = Register.Unit
              case _: PrimitiveType.Boolean =>
                registers(idx) = new Register.Boolean(offset)
                offset = RegisterOffset.incrementBooleansAndBytes(offset)
              case _: PrimitiveType.Byte =>
                registers(idx) = new Register.Byte(offset)
                offset = RegisterOffset.incrementBooleansAndBytes(offset)
              case _: PrimitiveType.Char =>
                registers(idx) = new Register.Char(offset)
                offset = RegisterOffset.incrementCharsAndShorts(offset)
              case _: PrimitiveType.Short =>
                registers(idx) = new Register.Short(offset)
                offset = RegisterOffset.incrementCharsAndShorts(offset)
              case _: PrimitiveType.Float =>
                registers(idx) = new Register.Float(offset)
                offset = RegisterOffset.incrementFloatsAndInts(offset)
              case _: PrimitiveType.Int =>
                registers(idx) = new Register.Int(offset)
                offset = RegisterOffset.incrementFloatsAndInts(offset)
              case _: PrimitiveType.Double =>
                registers(idx) = new Register.Double(offset)
                offset = RegisterOffset.incrementDoublesAndLongs(offset)
              case _: PrimitiveType.Long =>
                registers(idx) = new Register.Long(offset)
                offset = RegisterOffset.incrementDoublesAndLongs(offset)
              case _ =>
                registers(idx) = new Register.Object(offset)
                offset = RegisterOffset.incrementObjects(offset)
            }
          case None =>
            registers(idx) = new Register.Object(offset)
            offset = RegisterOffset.incrementObjects(offset)
        }
      }
      registers.asInstanceOf[Array[Register[Any]]]
    }
  }
}
