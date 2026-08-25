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

package zio.blocks.schema.bson

import org.bson.{BsonReader, BsonWriter, BsonValue}
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding, Register, RegisterOffset, Registers}
import zio.blocks.schema.derive.Deriver
import zio.blocks.schema.json.Json
import zio.blocks.typeid.TypeId

object BsonCodecDeriver extends BsonCodecDeriver(BsonSchemaCodec.Config)

class BsonCodecDeriver private[bson] (val config: BsonSchemaCodec.Config) extends Deriver[BsonCodec] {
  import BsonSchemaCodec.SumTypeHandling

  def withSumTypeHandling(value: BsonSchemaCodec.SumTypeHandling): BsonCodecDeriver =
    new BsonCodecDeriver(config.withSumTypeHandling(value))
  def withClassNameMapping(value: BsonSchemaCodec.TermMapping): BsonCodecDeriver =
    new BsonCodecDeriver(config.withClassNameMapping(value))
  def withIgnoreExtraFields(value: Boolean): BsonCodecDeriver =
    new BsonCodecDeriver(config.withIgnoreExtraFields(value))
  def withNativeObjectId(value: Boolean): BsonCodecDeriver =
    new BsonCodecDeriver(config.withNativeObjectId(value))

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[BsonCodec[A]] = Lazy(primitiveCodec(primitiveType))

  private[bson] def primitiveCodec[A](primitiveType: PrimitiveType[A]): BsonCodec[A] =
    (primitiveType match {
      case PrimitiveType.Unit              => BsonCodec.unit
      case PrimitiveType.Boolean(_)        => BsonCodec.boolean
      case PrimitiveType.Byte(_)           => BsonCodec.byte
      case PrimitiveType.Short(_)          => BsonCodec.short
      case PrimitiveType.Int(_)            => BsonCodec.int
      case PrimitiveType.Long(_)           => BsonCodec.long
      case PrimitiveType.Float(_)          => BsonCodec.float
      case PrimitiveType.Double(_)         => BsonCodec.double
      case PrimitiveType.Char(_)           => BsonCodec.char
      case PrimitiveType.String(_)         => BsonCodec.string
      case PrimitiveType.BigInt(_)         => BsonCodec.scalaBigInt
      case PrimitiveType.BigDecimal(_)     => BsonCodec.scalaBigDecimal
      case PrimitiveType.DayOfWeek(_)      => BsonCodec.dayOfWeek
      case PrimitiveType.Duration(_)       => BsonCodec.duration
      case PrimitiveType.Instant(_)        => BsonCodec.instant
      case PrimitiveType.LocalDate(_)      => BsonCodec.localDate
      case PrimitiveType.LocalDateTime(_)  => BsonCodec.localDateTime
      case PrimitiveType.LocalTime(_)      => BsonCodec.localTime
      case PrimitiveType.Month(_)          => BsonCodec.month
      case PrimitiveType.MonthDay(_)       => BsonCodec.monthDay
      case PrimitiveType.OffsetDateTime(_) => BsonCodec.offsetDateTime
      case PrimitiveType.OffsetTime(_)     => BsonCodec.offsetTime
      case PrimitiveType.Period(_)         => BsonCodec.period
      case PrimitiveType.Year(_)           => BsonCodec.year
      case PrimitiveType.YearMonth(_)      => BsonCodec.yearMonth
      case PrimitiveType.ZonedDateTime(_)  => BsonCodec.zonedDateTime
      case PrimitiveType.ZoneId(_)         => BsonCodec.zoneId
      case PrimitiveType.ZoneOffset(_)     => BsonCodec.zoneOffset
      case PrimitiveType.Currency(_)       => BsonCodec.currency
      case PrimitiveType.UUID(_)           => BsonCodec.uuid
    }).asInstanceOf[BsonCodec[A]]

  private def childCodec[F[_, _], A](reflect: Reflect[F, A])(implicit D: HasInstance[F]): BsonCodec[A] = {
    val lazyCodec = D.instance(reflect.metadata)
    if (reflect.isInstanceOf[Reflect.Deferred[F, A]])
      BsonCodec(
        new BsonEncoder[A] {
          def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit =
            lazyCodec.force.encoder.encode(writer, value, ctx)
          def toBsonValue(value: A): BsonValue = lazyCodec.force.encoder.toBsonValue(value)
        },
        new BsonDecoder[A] {
          def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A =
            lazyCodec.force.decoder.decodeUnsafe(reader, trace, ctx)
          def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A =
            lazyCodec.force.decoder.fromBsonValueUnsafe(value, trace, ctx)
        }
      )
    else lazyCodec.force
  }

  // Record (case class) codec derivation
  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[A]] = Lazy {
    val constructor                          = binding.constructor
    val deconstructor                        = binding.deconstructor
    val bindingUsedRegs                      = deconstructor.usedRegisters
    val registers: IndexedSeq[Register[Any]] =
      if (
        RegisterOffset.getObjects(bindingUsedRegs) == fields.length &&
        RegisterOffset.getBytes(bindingUsedRegs) == 0
      ) {
        var offset = 0L
        fields.indices.map { _ =>
          val reg = new Register.Object[AnyRef](offset).asInstanceOf[Register[Any]]
          offset = RegisterOffset.incrementObjects(offset)
          reg
        }
      } else {
        Reflect.Record.registers(fields.map(_.value).toArray).toIndexedSeq
      }

    // Obtain codecs derived by DerivationBuilder. Deferred children remain lazy to break recursive cycles.
    val fieldCodecs: Array[BsonCodec[Any]] = fields.map { field =>
      childCodec(field.value.asInstanceOf[Reflect[F, Any]]).asInstanceOf[BsonCodec[Any]]
    }.toArray

    val fieldNameMapper = modifiers.collectFirst { case m: Modifier.fieldNaming =>
      NameMapper.fromString(m.strategy)
    }.getOrElse(NameMapper.Identity)
    val rejectExtraFields = !config.ignoreExtraFields || modifiers.exists(_.isInstanceOf[Modifier.noExtraFields])

    // Get field names (respecting @rename modifier)
    val fieldNames: Array[String] = fields.map { field =>
      field.modifiers.collectFirst { case m: Modifier.rename =>
        m.name
      }.getOrElse(fieldNameMapper(field.name))
    }.toArray

    // Check for transient fields
    val transientFields: Array[Boolean] = fields.map { field =>
      field.modifiers.exists(_.isInstanceOf[Modifier.transient])
    }.toArray

    val encoder = new BsonEncoder[A] {
      def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit = {
        writer.writeStartDocument()

        // Deconstruct the value into registers
        val regs = Registers(deconstructor.usedRegisters)
        deconstructor.deconstruct(regs, 0, value)

        // Encode each field
        var idx = 0
        while (idx < fields.length) {
          if (!transientFields(idx)) {
            val fieldValue = registers(idx).get(regs, 0)
            writer.writeName(fieldNames(idx))
            fieldCodecs(idx).encoder.encode(writer, fieldValue, BsonEncoder.EncoderContext.default)
          }
          idx += 1
        }

        writer.writeEndDocument()
      }

      def toBsonValue(value: A): BsonValue = {
        val doc = new org.bson.BsonDocument()

        // Deconstruct the value into registers
        val regs = Registers(deconstructor.usedRegisters)
        deconstructor.deconstruct(regs, 0, value)

        // Encode each field
        var idx = 0
        while (idx < fields.length) {
          if (!transientFields(idx)) {
            val fieldValue = registers(idx).get(regs, 0)
            doc.put(fieldNames(idx), fieldCodecs(idx).encoder.toBsonValue(fieldValue))
          }
          idx += 1
        }

        doc
      }
    }

    val decoder = new BsonDecoder[A] {
      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
        reader.readStartDocument()

        val regs                     = Registers(constructor.usedRegisters)
        val fieldValues: Array[Any]  = Array.ofDim(fields.length)
        val fieldSet: Array[Boolean] = Array.ofDim(fields.length)

        // Create field name to index map
        val fieldIndexMap = scala.collection.mutable.HashMap[String, Int]()
        var i             = 0
        while (i < fieldNames.length) {
          fieldIndexMap(fieldNames(i)) = i
          i += 1
        }

        // Read all fields from BSON document
        while (reader.readBsonType() != org.bson.BsonType.END_OF_DOCUMENT) {
          val name = reader.readName()
          fieldIndexMap.get(name) match {
            case Some(idx) =>
              val fieldTrace = BsonTrace.Field(name) :: trace
              fieldValues(idx) =
                fieldCodecs(idx).decoder.decodeUnsafe(reader, fieldTrace, BsonDecoder.BsonDecoderContext.default)
              fieldSet(idx) = true
            case None =>
              // Check if we should reject extra fields
              // We also allow fields explicitly ignored by the context (e.g. discriminator fields)
              val isIgnored = ctx.ignoreExtraField.contains(name)
              if (rejectExtraFields && !isIgnored) {
                throw BsonDecoder.Error(BsonTrace.Field(name) :: trace, "Invalid extra field.")
              }
              // Skip unknown fields
              reader.skipValue()
          }
        }

        reader.readEndDocument()

        // Set field values in registers
        i = 0
        while (i < fields.length) {
          if (fieldSet(i)) {
            registers(i).set(regs, 0, fieldValues(i))
          } else {
            // Field is missing - check if it's transient or has a default value
            if (transientFields(i)) {
              // Transient field - use default value if available
              fields(i).value.getDefaultValue match {
                case Some(defaultValue) =>
                  registers(i).set(regs, 0, defaultValue)
                case None =>
                  throw BsonDecoder.Error(trace, s"Missing required transient field: ${fieldNames(i)}")
              }
            } else {
              // Regular field - use default value if available
              fields(i).value.getDefaultValue match {
                case Some(defaultValue) =>
                  registers(i).set(regs, 0, defaultValue)
                case None =>
                  throw BsonDecoder.Error(trace, s"Missing required field: ${fieldNames(i)}")
              }
            }
          }
          i += 1
        }

        constructor.construct(regs, 0)
      }

      def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
        if (value.getBsonType() != org.bson.BsonType.DOCUMENT) {
          throw BsonDecoder.Error(trace, s"Expected DOCUMENT but got ${value.getBsonType()}")
        }

        val doc                      = value.asDocument()
        val regs                     = Registers(constructor.usedRegisters)
        val fieldValues: Array[Any]  = Array.ofDim(fields.length)
        val fieldSet: Array[Boolean] = Array.ofDim(fields.length)

        // Create field name to index map
        val fieldIndexMap = scala.collection.mutable.HashMap[String, Int]()
        var i             = 0
        while (i < fieldNames.length) {
          fieldIndexMap(fieldNames(i)) = i
          i += 1
        }

        // Read all fields from BSON document
        val iter = doc.entrySet().iterator()
        while (iter.hasNext()) {
          val entry = iter.next()
          val name  = entry.getKey()
          fieldIndexMap.get(name) match {
            case Some(idx) =>
              val fieldTrace = BsonTrace.Field(name) :: trace
              fieldValues(idx) = fieldCodecs(idx).decoder.fromBsonValueUnsafe(
                entry.getValue(),
                fieldTrace,
                BsonDecoder.BsonDecoderContext.default
              )
              fieldSet(idx) = true
            case None =>
              // Check if we should reject extra fields
              // We also allow fields explicitly ignored by the context (e.g. discriminator fields)
              val isIgnored = ctx.ignoreExtraField.contains(name)
              if (rejectExtraFields && !isIgnored) {
                throw BsonDecoder.Error(BsonTrace.Field(name) :: trace, "Invalid extra field.")
              }
            // Skip unknown fields
          }
        }

        // Set field values in registers
        i = 0
        while (i < fields.length) {
          if (fieldSet(i)) {
            registers(i).set(regs, 0, fieldValues(i))
          } else {
            // Field is missing - check if it's transient or has a default value
            if (transientFields(i)) {
              // Transient field - use default value if available
              fields(i).value.getDefaultValue match {
                case Some(defaultValue) =>
                  registers(i).set(regs, 0, defaultValue)
                case None =>
                  throw BsonDecoder.Error(trace, s"Missing required transient field: ${fieldNames(i)}")
              }
            } else {
              // Regular field - use default value if available
              fields(i).value.getDefaultValue match {
                case Some(defaultValue) =>
                  registers(i).set(regs, 0, defaultValue)
                case None =>
                  throw BsonDecoder.Error(trace, s"Missing required field: ${fieldNames(i)}")
              }
            }
          }
          i += 1
        }

        constructor.construct(regs, 0)
      }
    }

    BsonCodec(encoder, decoder)
  }

  // Sequence (List, Vector, Set, etc.) codec derivation
  override def deriveSequence[F[_, _], C[_], E](
    element: Reflect[F, E],
    typeId: TypeId[C[E]],
    binding: Binding.Seq[C, E],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[E]],
    examples: Seq[C[E]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[C[E]]] = Lazy {
    val constructor                                      = binding.constructor
    val deconstructor                                    = binding.deconstructor
    implicit val elemClassTag: scala.reflect.ClassTag[E] =
      element.typeId.classTag.asInstanceOf[scala.reflect.ClassTag[E]]

    val elementCodec = childCodec(element)

    val encoder = new BsonEncoder[C[E]] {
      def encode(writer: BsonWriter, value: C[E], ctx: BsonEncoder.EncoderContext): Unit = {
        writer.writeStartArray()

        val iter = deconstructor.deconstruct(value)
        while (iter.hasNext) {
          val elem = iter.next()
          elementCodec.encoder.encode(writer, elem, BsonEncoder.EncoderContext.default)
        }

        writer.writeEndArray()
      }

      def toBsonValue(value: C[E]): BsonValue = {
        val array = new org.bson.BsonArray()

        val iter = deconstructor.deconstruct(value)
        while (iter.hasNext) {
          val elem = iter.next()
          array.add(elementCodec.encoder.toBsonValue(elem))
        }

        array
      }
    }

    val decoder = new BsonDecoder[C[E]] {
      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): C[E] = {
        if (reader.getCurrentBsonType() != org.bson.BsonType.ARRAY) {
          reader.readBsonType()
        }

        if (reader.getCurrentBsonType() == org.bson.BsonType.ARRAY) {
          reader.readStartArray()

          val builder = constructor.newBuilder[E](16)
          var idx     = 0

          while (reader.readBsonType() != org.bson.BsonType.END_OF_DOCUMENT) {
            val elemTrace = BsonTrace.Array(idx) :: trace
            val elem      = elementCodec.decoder.decodeUnsafe(reader, elemTrace, BsonDecoder.BsonDecoderContext.default)
            constructor.add(builder, elem)
            idx += 1
          }

          reader.readEndArray()
          constructor.result(builder)
        } else {
          throw BsonDecoder.Error(trace, s"Expected ARRAY but got ${reader.getCurrentBsonType()}")
        }
      }

      def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): C[E] = {
        if (value.getBsonType() != org.bson.BsonType.ARRAY) {
          throw BsonDecoder.Error(trace, s"Expected ARRAY but got ${value.getBsonType()}")
        }

        val array   = value.asArray()
        val builder = constructor.newBuilder[E](array.size())
        var idx     = 0

        val iter = array.iterator()
        while (iter.hasNext()) {
          val elem      = iter.next()
          val elemTrace = BsonTrace.Array(idx) :: trace
          val decoded   =
            elementCodec.decoder.fromBsonValueUnsafe(elem, elemTrace, BsonDecoder.BsonDecoderContext.default)
          constructor.add(builder, decoded)
          idx += 1
        }

        constructor.result(builder)
      }
    }

    BsonCodec(encoder, decoder)
  }

  // Map codec derivation
  override def deriveMap[F[_, _], M[_, _], K, V](
    keyReflect: Reflect[F, K],
    valueReflect: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[M[K, V]]] = Lazy {
    val constructor   = binding.constructor
    val deconstructor = binding.deconstructor

    // Check if keys are strings (can be encoded as BSON document)
    val isStringKey = keyReflect.isPrimitive && {
      keyReflect.asPrimitive.get.primitiveType match {
        case _: PrimitiveType.String => true
        case _                       => false
      }
    }

    // Derive codec for value type
    val valueCodec = childCodec(valueReflect)

    if (isStringKey) {
      // String keys: encode as BSON document
      val encoder = new BsonEncoder[M[K, V]] {
        def encode(writer: BsonWriter, value: M[K, V], ctx: BsonEncoder.EncoderContext): Unit = {
          writer.writeStartDocument()

          val iter = deconstructor.deconstruct(value)
          while (iter.hasNext) {
            val kv  = iter.next()
            val key = deconstructor.getKey(kv).asInstanceOf[String]
            val v   = deconstructor.getValue(kv)
            writer.writeName(key)
            valueCodec.encoder.encode(writer, v, BsonEncoder.EncoderContext.default)
          }

          writer.writeEndDocument()
        }

        def toBsonValue(value: M[K, V]): BsonValue = {
          val doc = new org.bson.BsonDocument()

          val iter = deconstructor.deconstruct(value)
          while (iter.hasNext) {
            val kv  = iter.next()
            val key = deconstructor.getKey(kv).asInstanceOf[String]
            val v   = deconstructor.getValue(kv)
            doc.put(key, valueCodec.encoder.toBsonValue(v))
          }

          doc
        }
      }

      val decoder = new BsonDecoder[M[K, V]] {
        def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): M[K, V] = {
          if (reader.getCurrentBsonType() != org.bson.BsonType.DOCUMENT) {
            reader.readBsonType()
          }

          if (reader.getCurrentBsonType() == org.bson.BsonType.DOCUMENT) {
            reader.readStartDocument()

            val builder = constructor.newObjectBuilder[K, V](16)

            while (reader.readBsonType() != org.bson.BsonType.END_OF_DOCUMENT) {
              val keyStr     = reader.readName()
              val key        = keyStr.asInstanceOf[K]
              val fieldTrace = BsonTrace.Field(keyStr) :: trace
              val v          = valueCodec.decoder.decodeUnsafe(reader, fieldTrace, BsonDecoder.BsonDecoderContext.default)
              constructor.addObject(builder, key, v)
            }

            reader.readEndDocument()
            constructor.resultObject[K, V](builder)
          } else {
            throw BsonDecoder.Error(trace, s"Expected DOCUMENT but got ${reader.getCurrentBsonType()}")
          }
        }

        def fromBsonValueUnsafe(
          value: BsonValue,
          trace: List[BsonTrace],
          ctx: BsonDecoder.BsonDecoderContext
        ): M[K, V] = {
          if (value.getBsonType() != org.bson.BsonType.DOCUMENT) {
            throw BsonDecoder.Error(trace, s"Expected DOCUMENT but got ${value.getBsonType()}")
          }

          val doc     = value.asDocument()
          val builder = constructor.newObjectBuilder[K, V](doc.size())

          val iter = doc.entrySet().iterator()
          while (iter.hasNext()) {
            val entry      = iter.next()
            val keyStr     = entry.getKey()
            val key        = keyStr.asInstanceOf[K]
            val fieldTrace = BsonTrace.Field(keyStr) :: trace
            val v          = valueCodec.decoder.fromBsonValueUnsafe(
              entry.getValue(),
              fieldTrace,
              BsonDecoder.BsonDecoderContext.default
            )
            constructor.addObject(builder, key, v)
          }

          constructor.resultObject[K, V](builder)
        }
      }

      BsonCodec(encoder, decoder)
    } else {
      // Non-string keys: encode as array of [key, value] pairs
      throw new UnsupportedOperationException(s"Map with non-string keys not yet supported for ${typeId.fullName}")
    }
  }

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[A]] =
    if (typeId == TypeId.of[Json]) Lazy(BsonCodec.json.asInstanceOf[BsonCodec[A]])
    else
      Lazy {
        val discriminator  = binding.discriminator
        val caseNameMapper = modifiers.collectFirst { case m: Modifier.caseNaming =>
          NameMapper.fromString(m.strategy)
        }.getOrElse(config.classNameMapping)
        val sumTypeHandling = modifiers.collectFirst { case m: Modifier.discriminator =>
          SumTypeHandling.DiscriminatorField(m.name)
        }.getOrElse(config.sumTypeHandling)

        // Obtain case codecs derived by DerivationBuilder.
        val caseCodecs: Array[BsonCodec[Any]] = cases.map { case_ =>
          childCodec(case_.value.asInstanceOf[Reflect[F, Any]]).asInstanceOf[BsonCodec[Any]]
        }.toArray

        // Get case names (respecting @rename modifier if present)
        val caseNames: Array[String] = cases.map { case_ =>
          case_.modifiers.collectFirst { case m: Modifier.rename =>
            m.name
          }.getOrElse(caseNameMapper(case_.name))
        }.toArray

        // Get case aliases (respecting @alias modifier)
        val caseAliases: Array[Seq[String]] = cases.map { case_ =>
          case_.modifiers.collect { case m: Modifier.alias => m.name }
        }.toArray

        // Check for transient cases
        val transientCases: Array[Boolean] = cases.map { case_ =>
          case_.modifiers.exists(_.isInstanceOf[Modifier.transient])
        }.toArray

        // Check if each case is a case object (record with zero fields)
        val isCaseObject: Array[Boolean] = cases.map { case_ =>
          case_.value.isRecord && case_.value.asRecord.get.fields.isEmpty
        }.toArray

        // Build case name to index map for decoding (including aliases)
        val caseNameToIndex = scala.collection.mutable.HashMap[String, Int]()
        var i               = 0
        while (i < caseNames.length) {
          if (!transientCases(i)) {
            caseNameToIndex(caseNames(i)) = i
            caseAliases(i).foreach { alias =>
              caseNameToIndex(alias) = i
            }
          }
          i += 1
        }

        sumTypeHandling match {
          case SumTypeHandling.WrapperWithClassNameField =>
            // WrapperWithClassNameField mode: { "CaseName": <case value> }
            val encoder = new BsonEncoder[A] {
              def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit = {
                val caseIdx = discriminator.discriminate(value)

                if (transientCases(caseIdx)) {
                  writer.writeStartDocument()
                  writer.writeEndDocument()
                } else {
                  val caseName  = caseNames(caseIdx)
                  val caseCodec = caseCodecs(caseIdx)

                  writer.writeStartDocument()
                  writer.writeName(caseName)
                  caseCodec.encoder.encode(writer, value, BsonEncoder.EncoderContext.default)
                  writer.writeEndDocument()
                }
              }

              def toBsonValue(value: A): BsonValue = {
                val caseIdx = discriminator.discriminate(value)

                if (transientCases(caseIdx)) {
                  new org.bson.BsonDocument()
                } else {
                  val caseName  = caseNames(caseIdx)
                  val caseCodec = caseCodecs(caseIdx)

                  val doc = new org.bson.BsonDocument()
                  doc.put(caseName, caseCodec.encoder.toBsonValue(value))
                  doc
                }
              }
            }

            val decoder = new BsonDecoder[A] {
              def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
                reader.readStartDocument()

                if (reader.readBsonType() == org.bson.BsonType.END_OF_DOCUMENT) {
                  reader.readEndDocument()
                  throw BsonDecoder.Error(trace, "Expected a case wrapper but got empty document")
                }

                val caseName   = reader.readName()
                val fieldTrace = BsonTrace.Field(caseName) :: trace

                caseNameToIndex.get(caseName) match {
                  case Some(idx) =>
                    val caseCodec = caseCodecs(idx)
                    val decoded   =
                      caseCodec.decoder.decodeUnsafe(reader, fieldTrace, BsonDecoder.BsonDecoderContext.default)

                    // Skip any extra fields
                    while (reader.readBsonType() != org.bson.BsonType.END_OF_DOCUMENT) {
                      reader.readName()
                      reader.skipValue()
                    }

                    reader.readEndDocument()
                    decoded.asInstanceOf[A]

                  case None =>
                    throw BsonDecoder.Error(fieldTrace, s"Unknown case name: $caseName")
                }
              }

              def fromBsonValueUnsafe(
                value: BsonValue,
                trace: List[BsonTrace],
                ctx: BsonDecoder.BsonDecoderContext
              ): A = {
                if (value.getBsonType() != org.bson.BsonType.DOCUMENT) {
                  throw BsonDecoder.Error(trace, s"Expected DOCUMENT but got ${value.getBsonType()}")
                }

                val doc    = value.asDocument()
                val fields = doc.entrySet().iterator()

                if (!fields.hasNext()) {
                  throw BsonDecoder.Error(trace, "Expected a case wrapper but got empty document")
                }

                val entry      = fields.next()
                val caseName   = entry.getKey()
                val fieldTrace = BsonTrace.Field(caseName) :: trace

                caseNameToIndex.get(caseName) match {
                  case Some(idx) =>
                    val caseCodec = caseCodecs(idx)
                    caseCodec.decoder
                      .fromBsonValueUnsafe(entry.getValue(), fieldTrace, BsonDecoder.BsonDecoderContext.default)
                      .asInstanceOf[A]

                  case None =>
                    throw BsonDecoder.Error(fieldTrace, s"Unknown case name: $caseName")
                }
              }
            }

            BsonCodec(encoder, decoder)

          case SumTypeHandling.DiscriminatorField(discriminatorFieldName) =>
            // DiscriminatorField mode: { "type": "CaseName", ...case fields... }
            val encoder = new BsonEncoder[A] {
              def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit = {
                val caseIdx = discriminator.discriminate(value)

                if (transientCases(caseIdx)) {
                  writer.writeStartDocument()
                  writer.writeEndDocument()
                } else {
                  val caseName  = caseNames(caseIdx)
                  val caseCodec = caseCodecs(caseIdx)

                  writer.writeStartDocument()

                  // Write discriminator field first
                  writer.writeName(discriminatorFieldName)
                  writer.writeString(caseName)

                  // Write case value inline (assuming it's a record that will write its fields)
                  // We need to encode the case value's fields directly into the current document
                  val caseValue = caseCodec.encoder.toBsonValue(value)
                  if (caseValue.isDocument()) {
                    val caseDoc = caseValue.asDocument()
                    val iter    = caseDoc.entrySet().iterator()
                    while (iter.hasNext()) {
                      val entry = iter.next()
                      writer.writeName(entry.getKey())
                      // Write the BSON value directly
                      entry.getValue().getBsonType() match {
                        case org.bson.BsonType.STRING   => writer.writeString(entry.getValue().asString().getValue())
                        case org.bson.BsonType.INT32    => writer.writeInt32(entry.getValue().asInt32().getValue())
                        case org.bson.BsonType.INT64    => writer.writeInt64(entry.getValue().asInt64().getValue())
                        case org.bson.BsonType.DOUBLE   => writer.writeDouble(entry.getValue().asDouble().getValue())
                        case org.bson.BsonType.BOOLEAN  => writer.writeBoolean(entry.getValue().asBoolean().getValue())
                        case org.bson.BsonType.NULL     => writer.writeNull()
                        case org.bson.BsonType.DOCUMENT =>
                          BsonEncoder.bsonValueEncoder
                            .encode(writer, entry.getValue(), BsonEncoder.EncoderContext.default)
                        case org.bson.BsonType.ARRAY =>
                          BsonEncoder.bsonValueEncoder
                            .encode(writer, entry.getValue(), BsonEncoder.EncoderContext.default)
                        case _ =>
                          BsonEncoder.bsonValueEncoder
                            .encode(writer, entry.getValue(), BsonEncoder.EncoderContext.default)
                      }
                    }
                  } else {
                    throw new RuntimeException(s"Cannot use DiscriminatorField mode for non-record case: $caseName")
                  }

                  writer.writeEndDocument()
                }
              }

              def toBsonValue(value: A): BsonValue = {
                val caseIdx = discriminator.discriminate(value)

                if (transientCases(caseIdx)) {
                  new org.bson.BsonDocument()
                } else {
                  val caseName  = caseNames(caseIdx)
                  val caseCodec = caseCodecs(caseIdx)

                  val caseValue = caseCodec.encoder.toBsonValue(value)
                  if (caseValue.isDocument()) {
                    val doc = caseValue.asDocument()
                    // Add discriminator field
                    doc.put(discriminatorFieldName, new org.bson.BsonString(caseName))
                    doc
                  } else {
                    // If it's not a document, wrap it
                    val doc = new org.bson.BsonDocument()
                    doc.put(discriminatorFieldName, new org.bson.BsonString(caseName))
                    doc.put("value", caseValue)
                    doc
                  }
                }
              }
            }

            val decoder = new BsonDecoder[A] {
              def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
                // We need to read the document to find the discriminator field first
                val mark = reader.getMark()

                reader.readStartDocument()

                var discriminatorValue: String = null
                var bsonType                   = reader.readBsonType()

                // Scan for discriminator field
                while (discriminatorValue == null && bsonType != org.bson.BsonType.END_OF_DOCUMENT) {
                  val name = reader.readName()
                  if (name == discriminatorFieldName && bsonType == org.bson.BsonType.STRING) {
                    discriminatorValue = reader.readString()
                  } else {
                    reader.skipValue()
                  }
                  bsonType = reader.readBsonType()
                }

                reader.readEndDocument()

                if (discriminatorValue == null) {
                  throw BsonDecoder.Error(trace, s"Missing discriminator field: $discriminatorFieldName")
                }

                caseNameToIndex.get(discriminatorValue) match {
                  case Some(idx) =>
                    // Reset and decode the whole document as the case type
                    mark.reset()
                    val caseCodec = caseCodecs(idx)
                    // We pass a context that tells the decoder to ignore the discriminator field
                    val nextCtx = ctx.copy(ignoreExtraField = Some(discriminatorFieldName))
                    caseCodec.decoder.decodeUnsafe(reader, trace, nextCtx).asInstanceOf[A]

                  case None =>
                    throw BsonDecoder.Error(
                      BsonTrace.Field(discriminatorFieldName) :: trace,
                      s"Unknown case: $discriminatorValue"
                    )
                }
              }

              def fromBsonValueUnsafe(
                value: BsonValue,
                trace: List[BsonTrace],
                ctx: BsonDecoder.BsonDecoderContext
              ): A = {
                if (value.getBsonType() != org.bson.BsonType.DOCUMENT) {
                  throw BsonDecoder.Error(trace, s"Expected DOCUMENT but got ${value.getBsonType()}")
                }

                val doc                = value.asDocument()
                val discriminatorField = doc.get(discriminatorFieldName)

                if (discriminatorField == null || discriminatorField.getBsonType() != org.bson.BsonType.STRING) {
                  throw BsonDecoder.Error(trace, s"Missing or invalid discriminator field: $discriminatorFieldName")
                }

                val discriminatorValue = discriminatorField.asString().getValue()

                caseNameToIndex.get(discriminatorValue) match {
                  case Some(idx) =>
                    val caseCodec = caseCodecs(idx)
                    // Decode using the same document (the case decoder will read its fields)
                    // We pass a context that tells the decoder to ignore the discriminator field
                    val nextCtx = ctx.copy(ignoreExtraField = Some(discriminatorFieldName))
                    caseCodec.decoder.fromBsonValueUnsafe(value, trace, nextCtx).asInstanceOf[A]

                  case None =>
                    throw BsonDecoder.Error(
                      BsonTrace.Field(discriminatorFieldName) :: trace,
                      s"Unknown case: $discriminatorValue"
                    )
                }
              }
            }

            BsonCodec(encoder, decoder)

          case SumTypeHandling.NoDiscriminator =>
            // NoDiscriminator mode: encode variant value directly without wrapper or discriminator
            // Case objects (zero fields) are encoded as strings
            val encoder = new BsonEncoder[A] {
              def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit = {
                val caseIdx = discriminator.discriminate(value)
                if (transientCases(caseIdx)) {
                  writer.writeStartDocument()
                  writer.writeEndDocument()
                } else if (isCaseObject(caseIdx)) {
                  // Case object: encode as string
                  writer.writeString(caseNames(caseIdx))
                } else {
                  // Regular case: encode value directly
                  val caseCodec = caseCodecs(caseIdx)
                  caseCodec.encoder.encode(writer, value, ctx)
                }
              }

              def toBsonValue(value: A): BsonValue = {
                val caseIdx = discriminator.discriminate(value)
                if (transientCases(caseIdx)) {
                  new org.bson.BsonDocument()
                } else if (isCaseObject(caseIdx)) {
                  // Case object: encode as string
                  new org.bson.BsonString(caseNames(caseIdx))
                } else {
                  // Regular case: encode value directly
                  val caseCodec = caseCodecs(caseIdx)
                  caseCodec.encoder.toBsonValue(value)
                }
              }
            }

            val decoder = new BsonDecoder[A] {
              def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
                // Check if it's a string (case object)
                val currentType = reader.getCurrentBsonType()
                val bsonType    = if (currentType == null) reader.readBsonType() else currentType

                if (bsonType == org.bson.BsonType.STRING) {
                  // String value - match to case object by name
                  val stringValue = reader.readString()
                  caseNameToIndex.get(stringValue) match {
                    case Some(idx) if isCaseObject(idx) =>
                      // Decode the case object using its codec
                      caseCodecs(idx).decoder
                        .fromBsonValueUnsafe(new org.bson.BsonDocument(), trace, ctx)
                        .asInstanceOf[A]
                    case _ =>
                      throw BsonDecoder.Error(trace, s"Unknown case object name: $stringValue")
                  }
                } else {
                  // Try each case codec until one succeeds
                  var idx                                  = 0
                  var result: Option[A]                    = None
                  var lastError: Option[BsonDecoder.Error] = None

                  while (idx < caseCodecs.length && result.isEmpty) {
                    if (!transientCases(idx) && !isCaseObject(idx)) {
                      val mark = reader.getMark()
                      try {
                        val decoded = caseCodecs(idx).decoder.decodeUnsafe(reader, trace, ctx)
                        result = Some(decoded.asInstanceOf[A])
                      } catch {
                        case e: BsonDecoder.Error =>
                          lastError = Some(e)
                          mark.reset()
                      }
                    }
                    idx += 1
                  }

                  result.getOrElse {
                    throw lastError.getOrElse(
                      BsonDecoder.Error(trace, "Could not decode variant - no matching case found")
                    )
                  }
                }
              }

              def fromBsonValueUnsafe(
                value: BsonValue,
                trace: List[BsonTrace],
                ctx: BsonDecoder.BsonDecoderContext
              ): A =
                // Check if it's a string (case object)
                if (value.getBsonType() == org.bson.BsonType.STRING) {
                  val stringValue = value.asString().getValue()
                  caseNameToIndex.get(stringValue) match {
                    case Some(idx) if isCaseObject(idx) =>
                      // Decode the case object using its codec
                      caseCodecs(idx).decoder
                        .fromBsonValueUnsafe(new org.bson.BsonDocument(), trace, ctx)
                        .asInstanceOf[A]
                    case _ =>
                      throw BsonDecoder.Error(trace, s"Unknown case object name: $stringValue")
                  }
                } else {
                  // Try each case codec until one succeeds
                  var idx                                  = 0
                  var result: Option[A]                    = None
                  var lastError: Option[BsonDecoder.Error] = None

                  while (idx < caseCodecs.length && result.isEmpty) {
                    if (!transientCases(idx) && !isCaseObject(idx)) {
                      try {
                        val decoded = caseCodecs(idx).decoder.fromBsonValueUnsafe(value, trace, ctx)
                        result = Some(decoded.asInstanceOf[A])
                      } catch {
                        case e: BsonDecoder.Error =>
                          lastError = Some(e)
                      }
                    }
                    idx += 1
                  }

                  result.getOrElse {
                    throw lastError.getOrElse(
                      BsonDecoder.Error(trace, "Could not decode variant - no matching case found")
                    )
                  }
                }
            }

            BsonCodec(encoder, decoder)
        }
      }

  // Wrapper (newtype) codec derivation
  override def deriveWrapper[F[_, _], A, B](
    wrappedReflect: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[A]] = Lazy {
    // ObjectIdSupport uses this exact TypeId to request BSON's native ObjectId representation.
    val isObjectId = typeId.name == "ObjectId" && typeId.owner.asString == "org.bson.types"

    // Use native ObjectId codec if:
    // 1. It's detected as ObjectId by typename (from ObjectIdSupport), OR
    // 2. Config explicitly enables native ObjectId support AND it's actually an ObjectId type
    if (isObjectId || (config.useNativeObjectId && isObjectId)) {
      // Import ObjectId type and use zio-bson's native codec
      BsonCodec.objectId.asInstanceOf[BsonCodec[A]]
    } else {
      // Normal wrapper handling
      val wrappedCodec = childCodec(wrappedReflect)

      val encoder = new BsonEncoder[A] {
        def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit =
          wrappedCodec.encoder.encode(writer, binding.unwrap(value), ctx)

        def toBsonValue(value: A): BsonValue =
          wrappedCodec.encoder.toBsonValue(binding.unwrap(value))
      }

      val decoder = new BsonDecoder[A] {
        def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
          val unwrapped = wrappedCodec.decoder.decodeUnsafe(reader, trace, ctx)
          try binding.wrap(unwrapped)
          catch {
            case error: SchemaError => throw BsonDecoder.Error(trace, s"Failed to wrap value: ${error.message}")
          }
        }

        def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
          val unwrapped = wrappedCodec.decoder.fromBsonValueUnsafe(value, trace, ctx)
          try binding.wrap(unwrapped)
          catch {
            case error: SchemaError => throw BsonDecoder.Error(trace, s"Failed to wrap value: ${error.message}")
          }
        }
      }

      BsonCodec(encoder, decoder)
    }
  }

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[BsonCodec[DynamicValue]] =
    Lazy.fail(
      new UnsupportedOperationException(
        s"BSON codec for ${TypeId.of[DynamicValue].fullName} (type: ${Reflect.Type.Dynamic}) is not yet implemented."
      )
    )
}
