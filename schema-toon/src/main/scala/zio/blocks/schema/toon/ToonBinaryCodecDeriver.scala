package zio.blocks.schema.toon

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.derive.{Deriver, InstanceOverride, ModifierOverride}
import zio.blocks.schema.json.{NameMapper, DiscriminatorKind}

/**
 * Default instance of ToonBinaryCodecDeriver with sensible defaults for TOON
 * format.
 */
object ToonBinaryCodecDeriver
    extends ToonBinaryCodecDeriver(
      fieldNameMapper = NameMapper.Identity,
      caseNameMapper = NameMapper.Identity,
      discriminatorKind = DiscriminatorKind.Key,
      rejectExtraFields = false,
      enumValuesAsStrings = true,
      transientNone = true,
      requireOptionFields = false,
      transientEmptyCollection = true,
      requireCollectionFields = false,
      transientDefaultValue = true,
      requireDefaultValueFields = false,
      arrayFormat = ArrayFormat.Auto
    )

/**
 * Deriver for TOON binary codecs with configuration options.
 *
 * @param fieldNameMapper
 *   Strategy for transforming field names during encoding/decoding
 * @param caseNameMapper
 *   Strategy for transforming case names in variants
 * @param discriminatorKind
 *   How to handle type discriminators in variants
 * @param rejectExtraFields
 *   Whether to reject unknown fields during decoding
 * @param enumValuesAsStrings
 *   Whether to serialize enumerations as strings
 * @param transientNone
 *   Whether to omit None values during encoding
 * @param requireOptionFields
 *   Whether optional fields must be present during decoding
 * @param transientEmptyCollection
 *   Whether to omit empty collections during encoding
 * @param requireCollectionFields
 *   Whether collection fields must be present during decoding
 * @param transientDefaultValue
 *   Whether to omit fields with default values during encoding
 * @param requireDefaultValueFields
 *   Whether fields with default values must be present during decoding
 * @param arrayFormat
 *   The format to use for encoding arrays
 */
class ToonBinaryCodecDeriver private[toon] (
  fieldNameMapper: NameMapper,
  caseNameMapper: NameMapper,
  discriminatorKind: DiscriminatorKind,
  rejectExtraFields: Boolean,
  enumValuesAsStrings: Boolean,
  transientNone: Boolean,
  requireOptionFields: Boolean,
  transientEmptyCollection: Boolean,
  requireCollectionFields: Boolean,
  transientDefaultValue: Boolean,
  requireDefaultValueFields: Boolean,
  arrayFormat: ArrayFormat
) extends Deriver[ToonBinaryCodec] {

  /**
   * Updates the field name mapper.
   */
  def withFieldNameMapper(fieldNameMapper: NameMapper): ToonBinaryCodecDeriver =
    copy(fieldNameMapper = fieldNameMapper)

  /**
   * Updates the case name mapper.
   */
  def withCaseNameMapper(caseNameMapper: NameMapper): ToonBinaryCodecDeriver =
    copy(caseNameMapper = caseNameMapper)

  /**
   * Updates the discriminator kind.
   */
  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): ToonBinaryCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  /**
   * Updates whether to reject extra fields.
   */
  def withRejectExtraFields(rejectExtraFields: Boolean): ToonBinaryCodecDeriver =
    copy(rejectExtraFields = rejectExtraFields)

  /**
   * Updates whether to serialize enum values as strings.
   */
  def withEnumValuesAsStrings(enumValuesAsStrings: Boolean): ToonBinaryCodecDeriver =
    copy(enumValuesAsStrings = enumValuesAsStrings)

  /**
   * Updates whether to omit None values.
   */
  def withTransientNone(transientNone: Boolean): ToonBinaryCodecDeriver =
    copy(transientNone = transientNone)

  /**
   * Updates whether optional fields are required.
   */
  def withRequireOptionFields(requireOptionFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireOptionFields = requireOptionFields)

  /**
   * Updates whether to omit empty collections.
   */
  def withTransientEmptyCollection(transientEmptyCollection: Boolean): ToonBinaryCodecDeriver =
    copy(transientEmptyCollection = transientEmptyCollection)

  /**
   * Updates whether collection fields are required.
   */
  def withRequireCollectionFields(requireCollectionFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireCollectionFields = requireCollectionFields)

  /**
   * Updates whether to omit fields with default values.
   */
  def withTransientDefaultValue(transientDefaultValue: Boolean): ToonBinaryCodecDeriver =
    copy(transientDefaultValue = transientDefaultValue)

  /**
   * Updates whether fields with default values are required.
   */
  def withRequireDefaultValueFields(requireDefaultValueFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireDefaultValueFields = requireDefaultValueFields)

  /**
   * Updates the array format.
   */
  def withArrayFormat(arrayFormat: ArrayFormat): ToonBinaryCodecDeriver =
    copy(arrayFormat = arrayFormat)

  private def copy(
    fieldNameMapper: NameMapper = fieldNameMapper,
    caseNameMapper: NameMapper = caseNameMapper,
    discriminatorKind: DiscriminatorKind = discriminatorKind,
    rejectExtraFields: Boolean = rejectExtraFields,
    enumValuesAsStrings: Boolean = enumValuesAsStrings,
    transientNone: Boolean = transientNone,
    requireOptionFields: Boolean = requireOptionFields,
    transientEmptyCollection: Boolean = transientEmptyCollection,
    requireCollectionFields: Boolean = requireCollectionFields,
    transientDefaultValue: Boolean = transientDefaultValue,
    requireDefaultValueFields: Boolean = requireDefaultValueFields,
    arrayFormat: ArrayFormat = arrayFormat
  ): ToonBinaryCodecDeriver =
    new ToonBinaryCodecDeriver(
      fieldNameMapper,
      caseNameMapper,
      discriminatorKind,
      rejectExtraFields,
      enumValuesAsStrings,
      transientNone,
      requireOptionFields,
      transientEmptyCollection,
      requireCollectionFields,
      transientDefaultValue,
      requireDefaultValueFields,
      arrayFormat
    )

  /**
   * Derives a codec for primitive types.
   *
   * Maps each primitive type to its corresponding TOON codec implementation.
   *
   * {{{
   * // Automatically called during schema derivation
   * val intCodec = derivePrimitive(PrimitiveType.Int, ...)
   * }}}
   *
   * @param primitiveType
   *   the primitive type to derive a codec for
   * @param typeName
   *   the type name
   * @param binding
   *   the binding for this primitive
   * @param doc
   *   documentation for this type
   * @param modifiers
   *   reflection modifiers
   * @return
   *   a lazy codec for the primitive type
   */
  override def derivePrimitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  ): Lazy[ToonBinaryCodec[A]] = Lazy {
    primitiveType match {
      case _: PrimitiveType.Unit.type      => ToonBinaryCodec.unitCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Boolean        => ToonBinaryCodec.booleanCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Byte           => ToonBinaryCodec.byteCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Short          => ToonBinaryCodec.shortCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Int            => ToonBinaryCodec.intCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Long           => ToonBinaryCodec.longCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Float          => ToonBinaryCodec.floatCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Double         => ToonBinaryCodec.doubleCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Char           => ToonBinaryCodec.charCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.String         => ToonBinaryCodec.stringCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.BigInt         => ToonBinaryCodec.bigIntCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.BigDecimal     => ToonBinaryCodec.bigDecimalCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Duration       => ToonBinaryCodec.durationCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Instant        => ToonBinaryCodec.instantCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.LocalDate      => ToonBinaryCodec.localDateCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.LocalDateTime  => ToonBinaryCodec.localDateTimeCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.LocalTime      => ToonBinaryCodec.localTimeCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.OffsetDateTime => ToonBinaryCodec.offsetDateTimeCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.ZonedDateTime  => ToonBinaryCodec.zonedDateTimeCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Period         => ToonBinaryCodec.periodCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.ZoneId         => ToonBinaryCodec.zoneIdCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.ZoneOffset     => ToonBinaryCodec.zoneOffsetCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.UUID           => ToonBinaryCodec.uuidCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Currency       => ToonBinaryCodec.currencyCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.DayOfWeek      => ToonBinaryCodec.dayOfWeekCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Month          => ToonBinaryCodec.monthCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.Year           => ToonBinaryCodec.yearCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.YearMonth      => ToonBinaryCodec.yearMonthCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.MonthDay       => ToonBinaryCodec.monthDayCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _: PrimitiveType.OffsetTime     => ToonBinaryCodec.offsetTimeCodec.asInstanceOf[ToonBinaryCodec[A]]
      case _                               => throw new UnsupportedOperationException(s"Unsupported primitive type: $primitiveType")
    }
  }

  // Helper method to derive codec from Reflect - delegates to the internal value for Deferred
  private def deriveFromReflect[G[_, _], B](
    reflect: Reflect[G, B]
  )(implicit F: HasBinding[G], D: HasInstance[G]): ToonBinaryCodec[B] =
    reflect match {
      case p: Reflect.Primitive[G, B] =>
        derivePrimitive(
          p.primitiveType,
          p.typeName,
          F.binding(p.primitiveBinding).asInstanceOf[Binding[BindingType.Primitive, B]],
          p.doc,
          p.modifiers
        ).force

      case r: Reflect.Record[G, B] =>
        deriveRecord(
          r.fields,
          r.typeName,
          F.binding(r.recordBinding).asInstanceOf[Binding[BindingType.Record, B]],
          r.doc,
          r.modifiers
        )(F, D).force

      case v: Reflect.Variant[G, B] =>
        deriveVariant(
          v.cases.asInstanceOf[IndexedSeq[Term[G, B, ?]]],
          v.typeName,
          F.binding(v.variantBinding).asInstanceOf[Binding[BindingType.Variant, B]],
          v.doc,
          v.modifiers
        )(F, D).force

      case s: Reflect.Sequence[G, _, _] =>
        // Use the Unknown pattern to extract the types properly
        s.asSequenceUnknown match {
          case Some(unknown) =>
            val seq = unknown.sequence
            deriveSequenceUnsafe(seq.element, seq.typeName, seq.seqBinding, seq.doc, seq.modifiers)(F, D)
              .asInstanceOf[ToonBinaryCodec[B]]
          case None =>
            throw new UnsupportedOperationException("Failed to extract Sequence type info")
        }

      case m: Reflect.Map[G, _, _, _] =>
        m.asMapUnknown match {
          case Some(unknown) =>
            val map = unknown.map
            deriveMapUnsafe(map.key, map.value, map.typeName, map.mapBinding, map.doc, map.modifiers)(F, D)
              .asInstanceOf[ToonBinaryCodec[B]]
          case None =>
            throw new UnsupportedOperationException("Failed to extract Map type info")
        }

      case w: Reflect.Wrapper[G, B, _] =>
        w.asWrapperUnknown match {
          case Some(unknown) =>
            val wrapper = unknown.wrapper
            deriveWrapperUnsafe(
              wrapper.wrapped,
              wrapper.typeName,
              wrapper.wrapperPrimitiveType,
              wrapper.wrapperBinding,
              wrapper.doc,
              wrapper.modifiers
            )(F, D).asInstanceOf[ToonBinaryCodec[B]]
          case None =>
            throw new UnsupportedOperationException("Failed to extract Wrapper type info")
        }

      case d: Reflect.Deferred[G, B] =>
        // Force the deferred and derive from the actual reflect
        deriveFromReflect(d.value)(F, D)

      case dyn: Reflect.Dynamic[G] =>
        deriveDynamic(
          F.binding(dyn.dynamicBinding).asInstanceOf[Binding[BindingType.Dynamic, DynamicValue]],
          dyn.doc,
          dyn.modifiers
        )(F, D).force.asInstanceOf[ToonBinaryCodec[B]]

      case _ =>
        throw new UnsupportedOperationException(s"Derivation for ${reflect.getClass.getSimpleName} not yet implemented")
    }

  // Unsafe helper methods that bypass type checks for complex generic scenarios
  private def deriveSequenceUnsafe[G[_, _], A, C[_]](
    element: Reflect[G, A],
    typeName: TypeName[C[A]],
    seqBinding: G[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[G], D: HasInstance[G]): ToonBinaryCodec[C[A]] =
    deriveSequence(
      element,
      typeName,
      F.binding(seqBinding).asInstanceOf[Binding[BindingType.Seq[C], C[A]]],
      doc,
      modifiers
    )(F, D).force

  private def deriveMapUnsafe[G[_, _], K, V, M[_, _]](
    key: Reflect[G, K],
    value: Reflect[G, V],
    typeName: TypeName[M[K, V]],
    mapBinding: G[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[G], D: HasInstance[G]): ToonBinaryCodec[M[K, V]] =
    deriveMap(
      key,
      value,
      typeName,
      F.binding(mapBinding).asInstanceOf[Binding[BindingType.Map[M], M[K, V]]],
      doc,
      modifiers
    )(F, D).force

  private def deriveWrapperUnsafe[G[_, _], A, B](
    wrapped: Reflect[G, B],
    typeName: TypeName[A],
    wrapperPrimitiveType: Option[PrimitiveType[A]],
    wrapperBinding: G[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[G], D: HasInstance[G]): ToonBinaryCodec[A] =
    deriveWrapper(
      wrapped,
      typeName,
      wrapperPrimitiveType,
      F.binding(wrapperBinding).asInstanceOf[Binding[BindingType.Wrapper[A, B], A]],
      doc,
      modifiers
    )(F, D).force

  /**
   * Derives a codec for record types (case classes, products).
   *
   * Encodes records as key-value pairs with field names and values.
   *
   * {{{
   * // For case class Person(name: String, age: Int)
   * // Encodes as:
   * // name: Alice
   * // age: 30
   * }}}
   *
   * @param fields
   *   the fields of the record
   * @param typeName
   *   the type name
   * @param binding
   *   the binding for this record
   * @param doc
   *   documentation for this type
   * @param modifiers
   *   reflection modifiers
   * @return
   *   a lazy codec for the record type
   */
  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {

    // Value type constants matching JSON codec pattern
    val objectType  = 0
    val intType     = 1
    val longType    = 2
    val floatType   = 3
    val doubleType  = 4
    val booleanType = 5
    val byteType    = 6
    val charType    = 7
    val shortType   = 8
    val unitType    = 9

    // Collect field info - derive codec for each field
    case class FieldInfo(
      name: String,
      codec: ToonBinaryCodec[Any],
      index: Int,
      term: Term[F, A, ?],
      defaultValue: Option[Any],
      valueType: Int,
      valueOffset: RegisterOffset.RegisterOffset
    )

    // Compute value type from Reflect
    def getValueType(reflect: Reflect[F, ?]): Int = reflect match {
      case p: Reflect.Primitive[F, _] =>
        p.primitiveType match {
          case _: PrimitiveType.Int       => intType
          case _: PrimitiveType.Long      => longType
          case _: PrimitiveType.Float     => floatType
          case _: PrimitiveType.Double    => doubleType
          case _: PrimitiveType.Boolean   => booleanType
          case _: PrimitiveType.Byte      => byteType
          case _: PrimitiveType.Char      => charType
          case _: PrimitiveType.Short     => shortType
          case _: PrimitiveType.Unit.type => unitType
          case _                          => objectType
        }
      case _ => objectType
    }

    // Compute offset based on type
    def getValueOffset(vt: Int): RegisterOffset.RegisterOffset = vt match {
      case 1 => RegisterOffset(ints = 1)
      case 2 => RegisterOffset(longs = 1)
      case 3 => RegisterOffset(floats = 1)
      case 4 => RegisterOffset(doubles = 1)
      case 5 => RegisterOffset(booleans = 1)
      case 6 => RegisterOffset(bytes = 1)
      case 7 => RegisterOffset(chars = 1)
      case 8 => RegisterOffset(shorts = 1)
      case _ => RegisterOffset(objects = 1)
    }

    val fieldInfos = fields.indices.map { idx =>
      val field     = fields(idx)
      val fieldName = fieldNameMapper(field.name)
      val codec     = deriveFromReflect(field.value)(F, D).asInstanceOf[ToonBinaryCodec[Any]]
      val vt        = getValueType(field.value)
      val vo        = getValueOffset(vt)

      // Extract default value if available
      val default = field.value match {
        case _: Reflect.Record[F, _] => None
        case _                       => None // Default value extraction not yet supported
      }

      FieldInfo(fieldName, codec, idx, field, default, vt, vo)
    }.toArray

    // Compute total register size and per-field offsets
    val recordBinding = binding.asInstanceOf[Binding.Record[A]]
    val constructor   = recordBinding.constructor
    val totalOffset   = constructor.usedRegisters

    // Compute cumulative offsets for each field
    val fieldOffsets                                    = new Array[RegisterOffset.RegisterOffset](fieldInfos.length)
    var cumulativeOffset: RegisterOffset.RegisterOffset = 0L
    var i                                               = 0
    while (i < fieldInfos.length) {
      fieldOffsets(i) = cumulativeOffset
      cumulativeOffset = cumulativeOffset + fieldInfos(i).valueOffset
      i += 1
    }

    new ToonBinaryCodec[A] {
      private[this] val infos   = fieldInfos
      private[this] val offsets = fieldOffsets

      override def decodeValue(in: ToonReader, default: A): A = {
        val values = new Array[Any](infos.length)
        val found  = new Array[Boolean](infos.length)

        // Build field name lookup map (String -> Integer to handle null properly)
        val fieldMap = new java.util.HashMap[String, Integer](infos.length)
        var i        = 0
        while (i < infos.length) {
          fieldMap.put(infos(i).name, Integer.valueOf(i))
          i += 1
        }

        // Track expected indentation for this record's fields
        val baseIndent = in.getCurrentIndentLevel

        // Parse fields
        var done = false
        while (!done && !in.isEof) {
          // Check if we're still at a valid field position
          val c = in.peek()
          if (c == '\n') {
            in.skipNewline()
            in.skipIndentation()
            if (in.getCurrentIndentLevel < baseIndent || in.isEof) {
              done = true
            }
          } else if (c == '-' || c == 0.toChar) {
            // List item marker or EOF - not a field
            done = true
          } else {
            // Read field
            val key = in.readKey()
            if (key.isEmpty) {
              done = true
            } else {
              in.expectColon()

              val fieldIdxBox = fieldMap.get(key)
              if (fieldIdxBox ne null) {
                val fieldIdx = fieldIdxBox.intValue()
                val info     = infos(fieldIdx)
                // Check if value is on same line or next line
                in.peekNextNonWhitespace() // Skip whitespace
                values(fieldIdx) = info.codec.decodeValue(in, null.asInstanceOf[Any])
                found(fieldIdx) = true
              } else {
                // Unknown field
                if (rejectExtraFields) {
                  throw ToonCodecError.atLine(in.getLine, s"Unexpected field: $key")
                } else {
                  in.skipValue()
                }
              }

              // Move to next line if present
              if (!in.isEof && in.peek() == '\n') {
                in.skipNewline()
                in.skipIndentation()
                if (in.getCurrentIndentLevel < baseIndent) {
                  done = true
                }
              } else if (in.isEof) {
                done = true
              }
            }
          }
        }

        // Set defaults for missing optional fields
        i = 0
        while (i < infos.length) {
          if (!found(i)) {
            infos(i).defaultValue match {
              case Some(defVal) => values(i) = defVal
              case None         => // Leave as null, will fail in construct if required
            }
          }
          i += 1
        }

        // Use registers to construct record with proper type handling
        val registers = Registers(totalOffset)

        i = 0
        while (i < infos.length) {
          val info   = infos(i)
          val offset = offsets(i)
          val value  = values(i)

          info.valueType match {
            case 1 => registers.setInt(offset, value.asInstanceOf[Int])
            case 2 => registers.setLong(offset, value.asInstanceOf[Long])
            case 3 => registers.setFloat(offset, value.asInstanceOf[Float])
            case 4 => registers.setDouble(offset, value.asInstanceOf[Double])
            case 5 => registers.setBoolean(offset, value.asInstanceOf[Boolean])
            case 6 => registers.setByte(offset, value.asInstanceOf[Byte])
            case 7 => registers.setChar(offset, value.asInstanceOf[Char])
            case 8 => registers.setShort(offset, value.asInstanceOf[Short])
            case _ => registers.setObject(offset, value.asInstanceOf[AnyRef])
          }
          i += 1
        }

        constructor.construct(registers, 0L)
      }

      override def encodeValue(x: A, out: ToonWriter): Unit = {
        // Use Product interface for case classes
        val product = x.asInstanceOf[Product]
        var first   = true
        var i       = 0
        while (i < infos.length) {
          val info       = infos(i)
          val fieldValue = product.productElement(info.index)

          // Check if field should be skipped (transient handling)
          var shouldSkip = false

          // Check for None values
          if (transientNone && fieldValue == None) {
            shouldSkip = true
          }

          // Check for empty collections
          if (transientEmptyCollection && !shouldSkip) {
            fieldValue match {
              case seq: Seq[_] if seq.isEmpty    => shouldSkip = true
              case map: Map[_, _] if map.isEmpty => shouldSkip = true
              case set: Set[_] if set.isEmpty    => shouldSkip = true
              case _                             => // Not a collection or not empty
            }
          }

          // Check for default values
          if (transientDefaultValue && !shouldSkip) {
            info.defaultValue match {
              case Some(default) if fieldValue == default => shouldSkip = true
              case _                                      => // No default or value differs
            }
          }

          if (!shouldSkip) {
            if (!first) out.newLine()
            first = false

            // Write field name
            out.writeRaw(info.name)
            out.writeRaw(": ")

            // Write field value
            info.codec.encodeValue(fieldValue, out)
          }

          i += 1
        }
      }
    }
  }

  /**
   * Derives a codec for variant types (sealed traits, ADTs).
   *
   * Encodes variants based on the configured discriminator kind.
   *
   * {{{
   * // For sealed trait Shape with case Circle and Rectangle
   * // With DiscriminatorKind.Key, encodes as:
   * // Circle:
   * //   radius: 5.0
   * }}}
   *
   * @param cases
   *   the cases of the variant
   * @param typeName
   *   the type name
   * @param binding
   *   the binding for this variant
   * @param doc
   *   documentation for this type
   * @param modifiers
   *   reflection modifiers
   * @return
   *   a lazy codec for the variant type
   */
  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
    val variantBinding = binding.asInstanceOf[Binding.Variant[A]]

    // Collect case info
    case class CaseInfo(name: String, codec: ToonBinaryCodec[Any], matcher: Matcher[Any], index: Int)

    val caseInfos = cases.indices.map { idx =>
      val caseItem = cases(idx)
      val caseName = caseNameMapper(caseItem.name)
      val codec    = deriveFromReflect(caseItem.value)(F, D).asInstanceOf[ToonBinaryCodec[Any]]
      val matcher  = variantBinding.matchers(idx).asInstanceOf[Matcher[Any]]
      CaseInfo(caseName, codec, matcher, idx)
    }.toArray

    new ToonBinaryCodec[A] {
      private[this] val infos         = caseInfos
      private[this] val discriminator = variantBinding.discriminator

      override def decodeValue(in: ToonReader, default: A): A =
        discriminatorKind match {
          case DiscriminatorKind.Key =>
            // Format: CaseName:\n  fields...
            val caseName = in.readKey()
            in.expectColon()

            // Find case by name
            var idx = -1
            var i   = 0
            while (i < infos.length && idx == -1) {
              if (infos(i).name == caseName) idx = i
              i += 1
            }
            if (idx == -1) throw ToonCodecError.atLine(in.getLine, s"Unknown variant case: $caseName")

            // Move to next line and decode
            in.skipNewline()
            in.skipIndentation()
            infos(idx).codec.decodeValue(in, null).asInstanceOf[A]

          case DiscriminatorKind.Field(fieldName) =>
            // Format: type: CaseName\nfields...
            // First read discriminator field
            val key = in.readKey()
            if (key != fieldName) {
              throw ToonCodecError.atLine(in.getLine, s"Expected discriminator field '$fieldName' but got '$key'")
            }
            in.expectColon()
            val caseName = in.readString()

            var idx = -1
            var i   = 0
            while (i < infos.length && idx == -1) {
              if (infos(i).name == caseName) idx = i
              i += 1
            }
            if (idx == -1) throw ToonCodecError.atLine(in.getLine, s"Unknown variant case: $caseName")

            in.skipNewline()
            in.skipIndentation()
            infos(idx).codec.decodeValue(in, null).asInstanceOf[A]

          case DiscriminatorKind.None =>
            // Try each case until one succeeds (not ideal for performance)
            throw new UnsupportedOperationException("DiscriminatorKind.None decoding not yet implemented")
        }

      override def encodeValue(x: A, out: ToonWriter): Unit = {
        val caseIdx    = discriminator.discriminate(x)
        val info       = infos(caseIdx)
        val downcasted = info.matcher.downcastOrNull(x)

        discriminatorKind match {
          case DiscriminatorKind.Key =>
            // CaseName:
            //   field: value
            out.writeRaw(info.name)
            out.writeRaw(":")
            out.newLine()
            out.indent()
            out.writeIndent()
            info.codec.encodeValue(downcasted, out)
            out.unindent()

          case DiscriminatorKind.Field(fieldName) =>
            // type: CaseName
            // field: value
            out.writeRaw(fieldName)
            out.writeRaw(": ")
            out.writeRaw(info.name)
            out.newLine()
            info.codec.encodeValue(downcasted, out)

          case DiscriminatorKind.None =>
            info.codec.encodeValue(downcasted, out)
        }
      }
    }
  }

  /**
   * Derives a codec for sequence types (List, Vector, etc.).
   *
   * Encodes sequences based on the configured array format.
   *
   * {{{
   * // For List(1, 2, 3) with ArrayFormat.Inline:
   * // [3]: 1,2,3
   * //
   * // With ArrayFormat.List:
   * // [3]:
   * //   - 1
   * //   - 2
   * //   - 3
   * }}}
   *
   * @param element
   *   the element type reflect
   * @param typeName
   *   the type name
   * @param binding
   *   the binding for this sequence
   * @param doc
   *   documentation for this type
   * @param modifiers
   *   reflection modifiers
   * @return
   *   a lazy codec for the sequence type
   */
  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeName: TypeName[C[A]],
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[C[A]]] = Lazy {
    val seqBinding         = binding.asInstanceOf[Binding.Seq[C, A]]
    val elementCodec       = deriveFromReflect(element)(F, D)
    val isPrimitiveElement = element.isInstanceOf[Reflect.Primitive[F, A]]

    new ToonBinaryCodec[C[A]] {
      private[this] val codec         = elementCodec
      private[this] val deconstructor = seqBinding.deconstructor

      override def decodeValue(in: ToonReader, default: C[A]): C[A] = {
        // Expect [N]: format
        if (!in.consume('[')) {
          throw ToonCodecError.atLine(in.getLine, "Expected '[' for array")
        }
        val length = in.readInt()
        if (!in.consume(']')) {
          throw ToonCodecError.atLine(in.getLine, "Expected ']' after array length")
        }
        in.expectColon()

        val constructor = seqBinding.constructor

        if (length == 0) {
          // Empty array
          constructor.emptyObject[A]
        } else {
          // Check if inline or list format
          val isInline = in.peek() != '\n'

          val builder = constructor.newObjectBuilder[A](length)

          if (isInline) {
            // Inline format: [N]: val1,val2,val3
            var i = 0
            while (i < length) {
              if (i > 0) in.consume(',')
              constructor.addObject(builder, codec.decodeValue(in, null.asInstanceOf[A]))
              i += 1
            }
          } else {
            // List format: [N]:\n  - val1\n  - val2
            in.skipNewline()
            var i = 0
            while (i < length) {
              in.skipIndentation()
              in.skipListMarker()
              constructor.addObject(builder, codec.decodeValue(in, null.asInstanceOf[A]))
              if (i < length - 1) {
                in.skipNewline()
              }
              i += 1
            }
          }
          constructor.resultObject(builder)
        }
      }

      override def encodeValue(x: C[A], out: ToonWriter): Unit = {
        val it    = deconstructor.deconstruct(x)
        val items = it.toList
        val size  = items.size

        arrayFormat match {
          case ArrayFormat.Inline =>
            // Inline format: [N]: val1,val2,val3
            out.writeRaw(s"[$size]: ")
            var first = true
            items.foreach { item =>
              if (!first) out.writeRaw(",")
              first = false
              codec.encodeValue(item, out)
            }

          case ArrayFormat.Auto if isPrimitiveElement =>
            // Inline format for primitives: [N]: val1,val2,val3
            out.writeRaw(s"[$size]: ")
            var first = true
            items.foreach { item =>
              if (!first) out.writeRaw(",")
              first = false
              codec.encodeValue(item, out)
            }

          case ArrayFormat.List | ArrayFormat.Auto =>
            // List format: [N]:\n  - item1\n  - item2
            out.writeRaw(s"[$size]:")
            items.foreach { item =>
              out.newLine()
              out.writeIndent()
              out.writeRaw("- ")
              codec.encodeValue(item, out)
            }

          case ArrayFormat.Tabular =>
            // Tabular format for records - simplified for now
            out.writeRaw(s"[$size]:")
            items.foreach { item =>
              out.newLine()
              out.writeIndent()
              codec.encodeValue(item, out)
            }
        }
      }
    }
  }

  /**
   * Derives a codec for map types.
   *
   * String-keyed maps are encoded as object-like structures. Other key types
   * use bracket notation.
   *
   * {{{
   * // For Map("a" -> 1, "b" -> 2):
   * // a: 1
   * // b: 2
   * //
   * // For Map(1 -> "a", 2 -> "b"):
   * // [1]: a
   * // [2]: b
   * }}}
   *
   * @param key
   *   the key type reflect
   * @param value
   *   the value type reflect
   * @param typeName
   *   the type name
   * @param binding
   *   the binding for this map
   * @param doc
   *   documentation for this type
   * @param modifiers
   *   reflection modifiers
   * @return
   *   a lazy codec for the map type
   */
  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeName: TypeName[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[M[K, V]]] = Lazy {
    val mapBinding  = binding.asInstanceOf[Binding.Map[M, K, V]]
    val keyCodec    = deriveFromReflect(key)(F, D)
    val valueCodec  = deriveFromReflect(value)(F, D)
    val isStringKey = key.isInstanceOf[Reflect.Primitive[F, K]] &&
      key.asInstanceOf[Reflect.Primitive[F, K]].primitiveType.isInstanceOf[PrimitiveType.String]

    new ToonBinaryCodec[M[K, V]] {
      private[this] val kCodec        = keyCodec
      private[this] val vCodec        = valueCodec
      private[this] val deconstructor = mapBinding.deconstructor

      override def decodeValue(in: ToonReader, default: M[K, V]): M[K, V] = {
        val constructor = mapBinding.constructor
        val builder     = constructor.newObjectBuilder[K, V]()
        val baseIndent  = in.getCurrentIndentLevel

        var done = false
        while (!done && !in.isEof) {
          val c = in.peek()
          if (c == '\n') {
            in.skipNewline()
            in.skipIndentation()
            if (in.getCurrentIndentLevel < baseIndent || in.isEof) {
              done = true
            }
          } else if (c == 0.toChar || c == '-') {
            done = true
          } else {
            val mapKey = if (isStringKey) {
              // String key: read as identifier
              kCodec.decodeValue(in, null.asInstanceOf[K])
            } else {
              // Non-string key: [key]: value
              if (!in.consume('[')) {
                throw ToonCodecError.atLine(in.getLine, "Expected '[' for map key")
              }
              val k = kCodec.decodeValue(in, null.asInstanceOf[K])
              if (!in.consume(']')) {
                throw ToonCodecError.atLine(in.getLine, "Expected ']' after map key")
              }
              k
            }

            in.expectColon()
            val mapValue = vCodec.decodeValue(in, null.asInstanceOf[V])
            constructor.addObject(builder, mapKey, mapValue)

            // Move to next line if present
            if (!in.isEof && in.peek() == '\n') {
              in.skipNewline()
              in.skipIndentation()
              if (in.getCurrentIndentLevel < baseIndent) {
                done = true
              }
            } else if (in.isEof) {
              done = true
            }
          }
        }

        constructor.resultObject(builder)
      }

      override def encodeValue(x: M[K, V], out: ToonWriter): Unit = {
        val it    = deconstructor.deconstruct(x)
        var first = true

        while (it.hasNext) {
          val entry = it.next()
          val k     = deconstructor.getKey(entry)
          val v     = deconstructor.getValue(entry)

          if (!first) out.newLine()
          first = false

          if (isStringKey) {
            // String-keyed map: encode as object-like structure
            out.writeRaw(k.toString)
            out.writeRaw(": ")
            vCodec.encodeValue(v, out)
          } else {
            // Other key types: encode as key-value pair
            out.writeRaw("[")
            kCodec.encodeValue(k, out)
            out.writeRaw("]: ")
            vCodec.encodeValue(v, out)
          }
        }
      }
    }
  }

  /**
   * Derives a codec for dynamic values.
   *
   * Handles runtime-typed values that can be primitives, records, variants,
   * sequences, or maps.
   *
   * @param binding
   *   the binding for dynamic values
   * @param doc
   *   documentation for this type
   * @param modifiers
   *   reflection modifiers
   * @return
   *   a lazy codec for dynamic values
   */
  override def deriveDynamic[F[_, _]](
    binding: Binding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[DynamicValue]] = Lazy {
    new ToonBinaryCodec[DynamicValue] {
      override def decodeValue(in: ToonReader, default: DynamicValue): DynamicValue =
        throw new UnsupportedOperationException("Dynamic decoding not yet implemented")

      override def encodeValue(x: DynamicValue, out: ToonWriter): Unit =
        x match {
          case DynamicValue.Primitive(value) =>
            out.writeRaw(value.toString)
          case DynamicValue.Record(fields) =>
            var first = true
            fields.foreach { case (name, value) =>
              if (!first) out.newLine()
              first = false
              out.writeRaw(name)
              out.writeRaw(": ")
              encodeValue(value, out)
            }
          case DynamicValue.Variant(caseName, value) =>
            out.writeRaw(caseName)
            out.writeRaw(":")
            out.newLine()
            out.writeIndent()
            encodeValue(value, out)
          case DynamicValue.Sequence(elements) =>
            out.writeRaw(s"[${elements.size}]:")
            elements.foreach { elem =>
              out.newLine()
              out.writeIndent()
              out.writeRaw("- ")
              encodeValue(elem, out)
            }
          case DynamicValue.Map(entries) =>
            var first = true
            entries.foreach { case (k, v) =>
              if (!first) out.newLine()
              first = false
              encodeValue(k, out)
              out.writeRaw(": ")
              encodeValue(v, out)
            }
        }
    }
  }

  /**
   * Derives a codec for wrapper types (newtypes).
   *
   * Transparently encodes/decodes the wrapped value.
   *
   * {{{
   * // For case class UserId(value: Int)
   * // Encodes as: 42 (just the wrapped Int)
   * }}}
   *
   * @param wrapped
   *   the wrapped type reflect
   * @param typeName
   *   the type name
   * @param wrapperPrimitiveType
   *   optional primitive type if wrapper is primitive
   * @param binding
   *   the binding for this wrapper
   * @param doc
   *   documentation for this type
   * @param modifiers
   *   reflection modifiers
   * @return
   *   a lazy codec for the wrapper type
   */
  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeName: TypeName[A],
    wrapperPrimitiveType: Option[PrimitiveType[A]],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
    val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
    val wrappedCodec   = deriveFromReflect(wrapped)(F, D)

    new ToonBinaryCodec[A] {
      private[this] val codec  = wrappedCodec
      private[this] val unwrap = wrapperBinding.unwrap
      private[this] val wrap   = wrapperBinding.wrap

      override def decodeValue(in: ToonReader, default: A): A = {
        val b = codec.decodeValue(in, null.asInstanceOf[B])
        wrap(b) match {
          case Right(a)  => a
          case Left(err) => throw ToonCodecError.atLine(in.getLine, s"Wrapper validation failed: $err")
        }
      }

      override def encodeValue(x: A, out: ToonWriter): Unit =
        codec.encodeValue(unwrap(x), out)
    }
  }

  override def instanceOverrides: IndexedSeq[InstanceOverride] = IndexedSeq.empty

  override def modifierOverrides: IndexedSeq[ModifierOverride] = IndexedSeq.empty
}
