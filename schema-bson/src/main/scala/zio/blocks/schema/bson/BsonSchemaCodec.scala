package zio.blocks.schema.bson

import org.bson.{BsonReader, BsonWriter, BsonValue}
import zio.bson._
import zio.blocks.schema._
import zio.blocks.schema.binding.Registers

object BsonSchemaCodec {

  type TermMapping = String => String

  sealed trait SumTypeHandling

  object SumTypeHandling {

    /**
     * Wrapper with class name as field:
     * {{{
     *   {
     *     mySum: {
     *       SomeBranch: {
     *         a: 123
     *       }
     *     }
     *   }
     * }}}
     */
    case object WrapperWithClassNameField extends SumTypeHandling

    /**
     * Discriminator field approach:
     * {{{
     *   {
     *     mySum: {
     *       type: "SomeBranch"
     *       a: 123
     *     }
     *   }
     * }}}
     */
    final case class DiscriminatorField(name: String) extends SumTypeHandling

    /**
     * No discriminator - encodes variant directly without wrapper or
     * discriminator field:
     * {{{
     *   A("str") => {a: "str"}
     *   B("str") => {b: "str"}
     * }}}
     * This only works when each case has distinct field names.
     */
    case object NoDiscriminator extends SumTypeHandling
  }

  /**
   * Configuration for the BSON schema codec.
   * @param sumTypeHandling
   *   The handling of sum types.
   * @param classNameMapping
   *   The mapping of class names.
   * @param ignoreExtraFields
   *   If true (default), extra fields in BSON documents are silently ignored
   *   during decoding. If false, decoding will fail with an error when
   *   encountering unknown fields.
   */
  class Config private (
    val sumTypeHandling: SumTypeHandling,
    val classNameMapping: TermMapping,
    val ignoreExtraFields: Boolean
  ) {

    def withSumTypeHandling(sumTypeHandling: SumTypeHandling): Config =
      copy(sumTypeHandling = sumTypeHandling)

    def withClassNameMapping(classNameMapping: TermMapping): Config =
      copy(classNameMapping = classNameMapping)

    def withIgnoreExtraFields(ignoreExtraFields: Boolean): Config =
      copy(ignoreExtraFields = ignoreExtraFields)

    private[this] def copy(
      sumTypeHandling: SumTypeHandling = sumTypeHandling,
      classNameMapping: TermMapping = classNameMapping,
      ignoreExtraFields: Boolean = ignoreExtraFields
    ): Config =
      new Config(sumTypeHandling, classNameMapping, ignoreExtraFields)
  }

  object Config
      extends Config(
        sumTypeHandling = SumTypeHandling.WrapperWithClassNameField,
        classNameMapping = identity,
        ignoreExtraFields = true
      )

  // Cache for deferred codecs to handle recursion
  private val codecCache = new java.util.concurrent.ConcurrentHashMap[Any, BsonCodec[Any]]()

  // Helper to recursively derive codec from Reflect structure
  private def deriveCodec[A](reflect: Reflect.Bound[A], config: Config): BsonCodec[A] =
    // Check if this is a Deferred type (recursive)
    if (reflect.isInstanceOf[Reflect.Deferred[binding.Binding, A]]) {
      val deferred = reflect.asInstanceOf[Reflect.Deferred[binding.Binding, A]]
      // Use a lazy codec to break recursion
      val cacheKey = (deferred, config)
      codecCache.get(cacheKey) match {
        case null =>
          // Create a lazy codec placeholder
          var lazyCodec: BsonCodec[A] = null

          val encoder = new BsonEncoder[A] {
            def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit = {
              if (lazyCodec == null) {
                lazyCodec = deriveCodec(deferred.value.asInstanceOf[Reflect.Bound[A]], config)
              }
              lazyCodec.encoder.encode(writer, value, ctx)
            }

            def toBsonValue(value: A): BsonValue = {
              if (lazyCodec == null) {
                lazyCodec = deriveCodec(deferred.value.asInstanceOf[Reflect.Bound[A]], config)
              }
              lazyCodec.encoder.toBsonValue(value)
            }
          }

          val decoder = new BsonDecoder[A] {
            def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
              if (lazyCodec == null) {
                lazyCodec = deriveCodec(deferred.value.asInstanceOf[Reflect.Bound[A]], config)
              }
              lazyCodec.decoder.decodeUnsafe(reader, trace, ctx)
            }

            def fromBsonValueUnsafe(
              value: BsonValue,
              trace: List[BsonTrace],
              ctx: BsonDecoder.BsonDecoderContext
            ): A = {
              if (lazyCodec == null) {
                lazyCodec = deriveCodec(deferred.value.asInstanceOf[Reflect.Bound[A]], config)
              }
              lazyCodec.decoder.fromBsonValueUnsafe(value, trace, ctx)
            }
          }

          val codec = BsonCodec(encoder, decoder)
          codecCache.put(cacheKey, codec.asInstanceOf[BsonCodec[Any]])
          codec

        case cached =>
          cached.asInstanceOf[BsonCodec[A]]
      }
    } else {
      // Non-deferred types
      reflect.asPrimitive match {
        case Some(primitive) =>
          Codecs.primitiveCodec(primitive.primitiveType)
        case None =>
          if (reflect.isRecord) {
            deriveRecordCodec(reflect.asRecord.get, config)
          } else if (reflect.isSequence) {
            val seq = reflect.asSequenceUnknown.get
            deriveSequenceCodec(seq.sequence, config).asInstanceOf[BsonCodec[A]]
          } else if (reflect.isMap) {
            val m = reflect.asMapUnknown.get
            deriveMapCodec(m.map, config).asInstanceOf[BsonCodec[A]]
          } else if (reflect.isVariant) {
            deriveVariantCodec(reflect.asVariant.get, config)
          } else if (reflect.isWrapper) {
            val w = reflect.asWrapperUnknown.get
            deriveWrapperCodec(w.wrapper, config).asInstanceOf[BsonCodec[A]]
          } else {
            throw new UnsupportedOperationException(
              s"BSON codec for ${reflect.typeName} (type: ${reflect.nodeType}) is not yet implemented."
            )
          }
      }
    }

  // Record (case class) codec derivation
  private def deriveRecordCodec[A](record: Reflect.Record.Bound[A], config: Config): BsonCodec[A] = {
    val fields        = record.fields
    val binding       = record.binding.asInstanceOf[zio.blocks.schema.binding.Binding.Record[A]]
    val constructor   = binding.constructor
    val deconstructor = binding.deconstructor
    val registers     = record.registers

    // Derive codecs for each field
    val fieldCodecs: Array[BsonCodec[Any]] = fields.map { field =>
      deriveCodec(field.value.asInstanceOf[Reflect.Bound[Any]], config).asInstanceOf[BsonCodec[Any]]
    }.toArray

    // Get field names (respecting @rename modifier)
    val fieldNames: Array[String] = fields.map { field =>
      field.modifiers.collectFirst { case m: Modifier.rename =>
        m.name
      }.getOrElse(field.name)
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
              if (!config.ignoreExtraFields && !isIgnored) {
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
              if (!config.ignoreExtraFields && !isIgnored) {
                throw BsonDecoder.Error(BsonTrace.Field(name) :: trace, "Invalid extra field.")
              }
            // Skip unknown fields (implicit continue)
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
  private def deriveSequenceCodec[C[_], E](sequence: Reflect.Sequence.Bound[E, C], config: Config): BsonCodec[C[E]] = {
    val elementReflect = sequence.element
    val binding        = sequence.binding.asInstanceOf[zio.blocks.schema.binding.Binding.Seq[C, E]]
    val constructor    = binding.constructor
    val deconstructor  = binding.deconstructor

    // Derive codec for the element type
    val elementCodec = deriveCodec(elementReflect.asInstanceOf[Reflect.Bound[E]], config)

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

          val builder = constructor.newObjectBuilder[E](16)
          var idx     = 0

          while (reader.readBsonType() != org.bson.BsonType.END_OF_DOCUMENT) {
            val elemTrace = BsonTrace.Array(idx) :: trace
            val elem      = elementCodec.decoder.decodeUnsafe(reader, elemTrace, BsonDecoder.BsonDecoderContext.default)
            constructor.addObject(builder, elem)
            idx += 1
          }

          reader.readEndArray()
          constructor.resultObject[E](builder)
        } else {
          throw BsonDecoder.Error(trace, s"Expected ARRAY but got ${reader.getCurrentBsonType()}")
        }
      }

      def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): C[E] = {
        if (value.getBsonType() != org.bson.BsonType.ARRAY) {
          throw BsonDecoder.Error(trace, s"Expected ARRAY but got ${value.getBsonType()}")
        }

        val array   = value.asArray()
        val builder = constructor.newObjectBuilder[E](array.size())
        var idx     = 0

        val iter = array.iterator()
        while (iter.hasNext()) {
          val elem      = iter.next()
          val elemTrace = BsonTrace.Array(idx) :: trace
          val decoded   =
            elementCodec.decoder.fromBsonValueUnsafe(elem, elemTrace, BsonDecoder.BsonDecoderContext.default)
          constructor.addObject(builder, decoded)
          idx += 1
        }

        constructor.resultObject[E](builder)
      }
    }

    BsonCodec(encoder, decoder)
  }

  // Map codec derivation
  private def deriveMapCodec[M[_, _], K, V](map: Reflect.Map.Bound[K, V, M], config: Config): BsonCodec[M[K, V]] = {
    val keyReflect    = map.key
    val valueReflect  = map.value
    val binding       = map.binding.asInstanceOf[zio.blocks.schema.binding.Binding.Map[M, K, V]]
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
    val valueCodec = deriveCodec(valueReflect.asInstanceOf[Reflect.Bound[V]], config)

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
      throw new UnsupportedOperationException(s"Map with non-string keys not yet supported for ${map.typeName}")
    }
  }

  private def deriveVariantCodec[A](variant: Reflect.Variant.Bound[A], config: Config): BsonCodec[A] = {
    val cases         = variant.cases
    val binding       = variant.binding.asInstanceOf[zio.blocks.schema.binding.Binding.Variant[A]]
    val discriminator = binding.discriminator

    // Derive codecs for each case
    val caseCodecs: Array[BsonCodec[Any]] = cases.map { case_ =>
      deriveCodec(case_.value.asInstanceOf[Reflect.Bound[Any]], config).asInstanceOf[BsonCodec[Any]]
    }.toArray

    // Get case names (respecting @rename modifier if present)
    val caseNames: Array[String] = cases.map { case_ =>
      case_.modifiers.collectFirst { case m: Modifier.rename =>
        m.name
      }.getOrElse(config.classNameMapping(case_.name))
    }.toArray

    // Get case aliases (respecting @alias modifier)
    val caseAliases: Array[Seq[String]] = cases.map { case_ =>
      case_.modifiers.collect { case m: Modifier.alias => m.name }
    }.toArray

    // Check for transient cases
    val transientCases: Array[Boolean] = cases.map { case_ =>
      case_.modifiers.exists(_.isInstanceOf[Modifier.transient])
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

    config.sumTypeHandling match {
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
                val decoded   = caseCodec.decoder.decodeUnsafe(reader, fieldTrace, BsonDecoder.BsonDecoderContext.default)

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

          def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
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
                      // For nested documents, we can write them directly
                      zio.bson.BsonEncoder.bsonValueEncoder
                        .encode(writer, entry.getValue(), BsonEncoder.EncoderContext.default)
                    case org.bson.BsonType.ARRAY =>
                      zio.bson.BsonEncoder.bsonValueEncoder
                        .encode(writer, entry.getValue(), BsonEncoder.EncoderContext.default)
                    case _ =>
                      // For other types, use the generic BsonValue encoder
                      zio.bson.BsonEncoder.bsonValueEncoder
                        .encode(writer, entry.getValue(), BsonEncoder.EncoderContext.default)
                  }
                }
              } else {
                // If it's not a document, we have a problem - can't inline it
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

          def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
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
        // This only works when each case has distinct field structure
        val encoder = new BsonEncoder[A] {
          def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit = {
            val caseIdx = discriminator.discriminate(value)
            if (transientCases(caseIdx)) {
              writer.writeStartDocument()
              writer.writeEndDocument()
            } else {
              val caseCodec = caseCodecs(caseIdx)
              caseCodec.encoder.encode(writer, value, ctx)
            }
          }

          def toBsonValue(value: A): BsonValue = {
            val caseIdx = discriminator.discriminate(value)
            if (transientCases(caseIdx)) {
              new org.bson.BsonDocument()
            } else {
              val caseCodec = caseCodecs(caseIdx)
              caseCodec.encoder.toBsonValue(value)
            }
          }
        }

        val decoder = new BsonDecoder[A] {
          def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
            // Try each case codec until one succeeds
            // This is inefficient but works when cases have distinct structures
            var idx                                  = 0
            var result: Option[A]                    = None
            var lastError: Option[BsonDecoder.Error] = None

            while (idx < caseCodecs.length && result.isEmpty) {
              if (!transientCases(idx)) {
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

          def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
            // Try each case codec until one succeeds
            var idx                                  = 0
            var result: Option[A]                    = None
            var lastError: Option[BsonDecoder.Error] = None

            while (idx < caseCodecs.length && result.isEmpty) {
              if (!transientCases(idx)) {
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
  private def deriveWrapperCodec[A, B](wrapper: Reflect.Wrapper.Bound[A, B], config: Config): BsonCodec[A] =
    // Special case for ObjectId: use zio-bson's built-in codec
    // ObjectId has typename "ObjectId" from namespace "org.bson.types"
    if (wrapper.typeName.name == "ObjectId" && wrapper.typeName.namespace.packages == Seq("org", "bson", "types")) {
      // Import ObjectId type and use zio-bson's native codec
      BsonCodec.objectId.asInstanceOf[BsonCodec[A]]
    } else {
      // Normal wrapper handling
      val wrappedReflect = wrapper.wrapped
      val binding        = wrapper.binding.asInstanceOf[zio.blocks.schema.binding.Binding.Wrapper[A, B]]

      // Derive codec for the wrapped type
      val wrappedCodec = deriveCodec(wrappedReflect.asInstanceOf[Reflect.Bound[B]], config)

      val encoder = new BsonEncoder[A] {
        def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit = {
          val unwrapped = binding.unwrap(value)
          wrappedCodec.encoder.encode(writer, unwrapped, ctx)
        }

        def toBsonValue(value: A): BsonValue = {
          val unwrapped = binding.unwrap(value)
          wrappedCodec.encoder.toBsonValue(unwrapped)
        }
      }

      val decoder = new BsonDecoder[A] {
        def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
          val unwrapped = wrappedCodec.decoder.decodeUnsafe(reader, trace, ctx)
          binding.wrap(unwrapped) match {
            case Right(wrapped) => wrapped
            case Left(error)    => throw BsonDecoder.Error(trace, s"Failed to wrap value: $error")
          }
        }

        def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
          val unwrapped = wrappedCodec.decoder.fromBsonValueUnsafe(value, trace, ctx)
          binding.wrap(unwrapped) match {
            case Right(wrapped) => wrapped
            case Left(error)    => throw BsonDecoder.Error(trace, s"Failed to wrap value: $error")
          }
        }
      }

      BsonCodec(encoder, decoder)
    }

  def bsonEncoder[A](schema: Schema[A], config: Config): BsonEncoder[A] =
    deriveCodec(schema.reflect, config).encoder

  def bsonEncoder[A](schema: Schema[A]): BsonEncoder[A] =
    bsonEncoder(schema, Config)

  def bsonDecoder[A](schema: Schema[A], config: Config): BsonDecoder[A] =
    deriveCodec(schema.reflect, config).decoder

  def bsonDecoder[A](schema: Schema[A]): BsonDecoder[A] =
    bsonDecoder(schema, Config)

  def bsonCodec[A](schema: Schema[A], config: Config): BsonCodec[A] =
    deriveCodec(schema.reflect, config)

  def bsonCodec[A](schema: Schema[A]): BsonCodec[A] =
    bsonCodec(schema, Config)

  object Codecs {
    import org.bson.{BsonDocument, BsonType, BsonValue}

    /**
     * Unit codec - encodes as empty BSON document
     */
    val unitCodec: BsonCodec[Unit] = BsonCodec(
      new BsonEncoder[Unit] {
        def encode(writer: BsonWriter, value: Unit, ctx: BsonEncoder.EncoderContext): Unit = {
          writer.writeStartDocument()
          writer.writeEndDocument()
        }
        def toBsonValue(value: Unit): BsonValue = new BsonDocument()
      },
      new BsonDecoder[Unit] {
        def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Unit = {
          reader.readStartDocument()
          while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            reader.readName()
            reader.skipValue()
          }
          reader.readEndDocument()
          ()
        }
        def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Unit =
          ()
      }
    )

    /**
     * Scala BigInt codec - wraps Java BigInteger codec
     */
    val bigIntCodec: BsonCodec[BigInt] = BsonCodec(
      new BsonEncoder[BigInt] {
        def encode(writer: BsonWriter, value: BigInt, ctx: BsonEncoder.EncoderContext): Unit =
          BsonEncoder.bigInteger.encode(writer, value.bigInteger, ctx)
        def toBsonValue(value: BigInt): BsonValue =
          BsonEncoder.bigInteger.toBsonValue(value.bigInteger)
      },
      new BsonDecoder[BigInt] {
        def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): BigInt =
          BigInt(BsonDecoder.bigInteger.decodeUnsafe(reader, trace, ctx))
        def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): BigInt =
          BigInt(BsonDecoder.bigInteger.fromBsonValueUnsafe(value, trace, ctx))
      }
    )

    /**
     * Maps a zio-blocks PrimitiveType to the corresponding zio-bson BsonCodec.
     */
    def primitiveCodec[A](primitiveType: PrimitiveType[A]): BsonCodec[A] =
      (primitiveType match {
        case PrimitiveType.Unit              => unitCodec
        case PrimitiveType.Boolean(_)        => BsonCodec.boolean
        case PrimitiveType.Byte(_)           => BsonCodec.byte
        case PrimitiveType.Short(_)          => BsonCodec.short
        case PrimitiveType.Int(_)            => BsonCodec.int
        case PrimitiveType.Long(_)           => BsonCodec.long
        case PrimitiveType.Float(_)          => BsonCodec.float
        case PrimitiveType.Double(_)         => BsonCodec.double
        case PrimitiveType.Char(_)           => BsonCodec.char
        case PrimitiveType.String(_)         => BsonCodec.string
        case PrimitiveType.BigInt(_)         => bigIntCodec
        case PrimitiveType.BigDecimal(_)     => BsonCodec.bigDecimal
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
  }
}
