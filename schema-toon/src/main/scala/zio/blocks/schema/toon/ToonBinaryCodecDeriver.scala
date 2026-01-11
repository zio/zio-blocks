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
      case _                               => ??? // TODO: Add remaining primitive types as needed
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

    // Collect field info - derive codec for each field
    case class FieldInfo(name: String, codec: ToonBinaryCodec[Any], index: Int)

    val fieldInfos = fields.indices.map { idx =>
      val field     = fields(idx)
      val fieldName = fieldNameMapper(field.name)
      val codec     = deriveFromReflect(field.value)(F, D).asInstanceOf[ToonBinaryCodec[Any]]
      FieldInfo(fieldName, codec, idx)
    }.toArray

    new ToonBinaryCodec[A] {
      private[this] val infos = fieldInfos

      override def decodeValue(in: ToonReader, default: A): A =
        // TODO: Implement full TOON record parsing
        throw new UnsupportedOperationException("Record decoding not yet implemented")

      override def encodeValue(x: A, out: ToonWriter): Unit = {
        // Use Product interface for case classes
        val product = x.asInstanceOf[Product]
        var first   = true
        var i       = 0
        while (i < infos.length) {
          val info       = infos(i)
          val fieldValue = product.productElement(info.index)

          if (!first) out.newLine()
          first = false

          // Write field name
          out.writeRaw(info.name)
          out.writeRaw(": ")

          // Write field value
          info.codec.encodeValue(fieldValue, out)

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
        throw new UnsupportedOperationException("Variant decoding not yet implemented")

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

      override def decodeValue(in: ToonReader, default: C[A]): C[A] =
        throw new UnsupportedOperationException("Sequence decoding not yet implemented")

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

      override def decodeValue(in: ToonReader, default: M[K, V]): M[K, V] =
        throw new UnsupportedOperationException("Map decoding not yet implemented")

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
          case Left(err) => throw new RuntimeException(s"Wrapper validation failed: $err")
        }
      }

      override def encodeValue(x: A, out: ToonWriter): Unit =
        codec.encodeValue(unwrap(x), out)
    }
  }

  override def instanceOverrides: IndexedSeq[InstanceOverride] = IndexedSeq.empty

  override def modifierOverrides: IndexedSeq[ModifierOverride] = IndexedSeq.empty
}
