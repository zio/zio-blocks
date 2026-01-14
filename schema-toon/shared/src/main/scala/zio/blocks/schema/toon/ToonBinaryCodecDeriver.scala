package zio.blocks.schema.toon

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.derive._
import zio.blocks.schema.json.{DiscriminatorKind, NameMapper}

import scala.util.control.NonFatal

/**
 * Default TOON deriver with standard settings.
 */
object ToonBinaryCodecDeriver
    extends ToonBinaryCodecDeriver(
      fieldNameMapper = NameMapper.Identity,
      caseNameMapper = NameMapper.Identity,
      discriminatorKind = DiscriminatorKind.Key,
      arrayFormat = ArrayFormat.Auto,
      delimiter = ',',
      rejectExtraFields = false,
      enumValuesAsStrings = true,
      transientNone = true,
      requireOptionFields = false,
      transientEmptyCollection = true,
      requireCollectionFields = false,
      transientDefaultValue = true,
      requireDefaultValueFields = false,
      enableKeyFolding = false
    )

class ToonBinaryCodecDeriver private[toon] (
  fieldNameMapper: NameMapper,
  caseNameMapper: NameMapper,
  discriminatorKind: DiscriminatorKind,
  arrayFormat: ArrayFormat,
  delimiter: Char,
  rejectExtraFields: Boolean,
  enumValuesAsStrings: Boolean,
  transientNone: Boolean,
  requireOptionFields: Boolean,
  transientEmptyCollection: Boolean,
  requireCollectionFields: Boolean,
  transientDefaultValue: Boolean,
  requireDefaultValueFields: Boolean,
  enableKeyFolding: Boolean
) extends Deriver[ToonBinaryCodec] {

  // Builder methods

  /**
   * Updates the field name mapper for transforming field names during
   * encoding/decoding.
   */
  def withFieldNameMapper(mapper: NameMapper): ToonBinaryCodecDeriver =
    copy(fieldNameMapper = mapper)

  /**
   * Updates the case name mapper for transforming variant case names during
   * encoding/decoding.
   */
  def withCaseNameMapper(mapper: NameMapper): ToonBinaryCodecDeriver =
    copy(caseNameMapper = mapper)

  /**
   * Updates the discriminator kind for encoding sealed trait/enum
   * hierarchies.
   */
  def withDiscriminatorKind(kind: DiscriminatorKind): ToonBinaryCodecDeriver =
    copy(discriminatorKind = kind)

  /**
   * Updates the array format preference (Auto, Tabular, Inline, or List).
   */
  def withArrayFormat(format: ArrayFormat): ToonBinaryCodecDeriver =
    copy(arrayFormat = format)

  /**
   * Updates the delimiter character used in tabular and inline array formats.
   */
  def withDelimiter(delim: Char): ToonBinaryCodecDeriver =
    copy(delimiter = delim)

  /**
   * Configures whether to reject extra fields during decoding.
   */
  def withRejectExtraFields(reject: Boolean): ToonBinaryCodecDeriver =
    copy(rejectExtraFields = reject)

  /**
   * Configures whether to encode case object enums as strings.
   */
  def withEnumValuesAsStrings(asStrings: Boolean): ToonBinaryCodecDeriver =
    copy(enumValuesAsStrings = asStrings)

  /**
   * Configures whether to omit Option fields with None values during
   * encoding.
   */
  def withTransientNone(transient: Boolean): ToonBinaryCodecDeriver =
    copy(transientNone = transient)

  /**
   * Configures whether Option fields are required to be present during
   * decoding.
   */
  def withRequireOptionFields(require: Boolean): ToonBinaryCodecDeriver =
    copy(requireOptionFields = require)

  /**
   * Configures whether to omit empty collection fields during encoding.
   */
  def withTransientEmptyCollection(transient: Boolean): ToonBinaryCodecDeriver =
    copy(transientEmptyCollection = transient)

  /**
   * Configures whether collection fields are required to be present during
   * decoding.
   */
  def withRequireCollectionFields(require: Boolean): ToonBinaryCodecDeriver =
    copy(requireCollectionFields = require)

  /**
   * Configures whether to omit fields with default values during encoding.
   */
  def withTransientDefaultValue(transient: Boolean): ToonBinaryCodecDeriver =
    copy(transientDefaultValue = transient)

  /**
   * Configures whether fields with defaults are required to be present during
   * decoding.
   */
  def withRequireDefaultValueFields(require: Boolean): ToonBinaryCodecDeriver =
    copy(requireDefaultValueFields = require)

  /**
   * Configures whether to enable key folding (dotted key expansion).
   */
  def withKeyFolding(enabled: Boolean): ToonBinaryCodecDeriver =
    copy(enableKeyFolding = enabled)

  private def copy(
    fieldNameMapper: NameMapper = fieldNameMapper,
    caseNameMapper: NameMapper = caseNameMapper,
    discriminatorKind: DiscriminatorKind = discriminatorKind,
    arrayFormat: ArrayFormat = arrayFormat,
    delimiter: Char = delimiter,
    rejectExtraFields: Boolean = rejectExtraFields,
    enumValuesAsStrings: Boolean = enumValuesAsStrings,
    transientNone: Boolean = transientNone,
    requireOptionFields: Boolean = requireOptionFields,
    transientEmptyCollection: Boolean = transientEmptyCollection,
    requireCollectionFields: Boolean = requireCollectionFields,
    transientDefaultValue: Boolean = transientDefaultValue,
    requireDefaultValueFields: Boolean = requireDefaultValueFields,
    enableKeyFolding: Boolean = enableKeyFolding
  ): ToonBinaryCodecDeriver = new ToonBinaryCodecDeriver(
    fieldNameMapper,
    caseNameMapper,
    discriminatorKind,
    arrayFormat,
    delimiter,
    rejectExtraFields,
    enumValuesAsStrings,
    transientNone,
    requireOptionFields,
    transientEmptyCollection,
    requireCollectionFields,
    transientDefaultValue,
    requireDefaultValueFields,
    enableKeyFolding
  )

  // Deriver implementation - delegate to internal deriveCodec via Reflect objects
  override def derivePrimitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  ): Lazy[ToonBinaryCodec[A]] = Lazy {
    deriveCodec(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers))
  }

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[C[A]]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[M[K, V]]] = Lazy {
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[DynamicValue]] = Lazy {
    new ToonBinaryCodec[DynamicValue]() {
      def decodeValue(in: ToonReader, default: DynamicValue): DynamicValue = {
        in.skipWhitespace()
        val c = in.peek()
        if (c == '[') {
          // Sequence
          in.ptr += 1 // Skip [
          val start = in.ptr
          while (in.ptr < in.limit && in.buf(in.ptr) != ']') in.ptr += 1
          val sizeStr = new String(in.buf, start, in.ptr - start, java.nio.charset.StandardCharsets.UTF_8)
          val size    =
            try sizeStr.toInt
            catch { case _: NumberFormatException => 0 }
          if (in.ptr < in.limit) in.ptr += 1 // Skip ]

          in.skipWhitespace()
          if (in.ptr < in.limit && in.buf(in.ptr) == '{') {
            // Tabular {keys} - skip it
            while (in.ptr < in.limit && in.buf(in.ptr) != '}') in.ptr += 1
            if (in.ptr < in.limit) in.ptr += 1
          }

          if (in.ptr < in.limit && in.buf(in.ptr) == ':') in.ptr += 1

          val builder = Vector.newBuilder[DynamicValue]
          var i       = 0
          while (i < size) {
            in.skipWhitespace()
            if (in.ptr + 1 < in.limit && in.buf(in.ptr) == '-' && Character.isWhitespace(in.buf(in.ptr + 1).toChar)) {
              in.ptr += 1
              in.skipWhitespace()
            } else if (i > 0 && in.ptr < in.limit && in.buf(in.ptr) == ',') {
              in.ptr += 1
            }

            builder += decodeValue(in, null)
            i += 1
          }
          DynamicValue.Sequence(builder.result())
        } else {
          val startPtr        = in.ptr
          val potentialString =
            try in.readUnquotedString()
            catch { case _: Exception => "" }
          in.skipWhitespace()

          if (potentialString.nonEmpty && in.ptr < in.limit && in.buf(in.ptr) == ':') {
            // It's a RECORD
            in.ptr = startPtr // Rewind
            val fields  = Vector.newBuilder[(String, DynamicValue)]
            var parsing = true

            while (parsing && in.ptr < in.limit) {
              val keyStart = in.ptr
              try {
                val keys  = in.readKey()
                val value = decodeValue(in, null)
                fields += (keys -> value)
              } catch {
                case _: Exception =>
                  in.ptr = keyStart
                  parsing = false
              }
              in.skipWhitespace()
              if (in.ptr >= in.limit || in.buf(in.ptr) == '}' || in.buf(in.ptr) == ']') parsing = false
            }
            DynamicValue.Record(fields.result())
          } else {
            // Primitive
            in.ptr = startPtr
            val s = in.readString()
            if (s == "null") DynamicValue.Primitive(PrimitiveValue.Unit) // Map null to Unit?
            else if (s == "true") DynamicValue.Primitive(PrimitiveValue.Boolean(true))
            else if (s == "false") DynamicValue.Primitive(PrimitiveValue.Boolean(false))
            else {
              val isLeadingZero = (s.length > 1 && s.startsWith("0") && s(1) != '.') ||
                (s.length > 2 && s.startsWith("-0") && s(2) != '.')
              if (isLeadingZero) {
                DynamicValue.Primitive(PrimitiveValue.String(s))
              } else {
                try DynamicValue.Primitive(PrimitiveValue.Int(s.toInt))
                catch {
                  case _: NumberFormatException =>
                    try DynamicValue.Primitive(PrimitiveValue.Double(s.toDouble))
                    catch { case _: NumberFormatException => DynamicValue.Primitive(PrimitiveValue.String(s)) }
                }
              }
            }
          }
        }
      }

      def encodeValue(x: DynamicValue, out: ToonWriter): Unit = x match {
        case DynamicValue.Record(values) =>
          values.foreach { case (k, v) =>
            out.writeKey(k)
            out.currentIndentLevel += 1
            out.newLine()
            encodeValue(v, out)
            out.currentIndentLevel -= 1
            out.newLine()
          }
        case DynamicValue.Sequence(values) =>
          out.writeRaw("[" + values.size + "]:")
          out.newLine()
          values.foreach { v =>
            out.writeIndent()
            out.writeRaw("- ")
            out.currentIndentLevel += 1
            encodeValue(v, out)
            out.currentIndentLevel -= 1
            out.newLine()
          }
        case DynamicValue.Primitive(p) => encodePrimitive(p, out)
        case DynamicValue.Map(entries) =>
          out.writeRaw("[" + entries.size + "]:")
          out.newLine()
          entries.foreach { case (k, v) =>
            out.writeIndent()
            out.writeRaw("- [")
            encodeValue(k, out)
            out.writeRaw(", ")
            encodeValue(v, out)
            out.writeRaw("]")
            out.newLine()
          }
        case _ => out.writeNull()
      }

      private def encodePrimitive(p: PrimitiveValue, out: ToonWriter): Unit = p match {
        case PrimitiveValue.String(s)     => if (out.requiresQuoting(s)) out.writeQuotedString(s) else out.writeRawString(s)
        case PrimitiveValue.Int(v)        => out.writeRaw(v.toString)
        case PrimitiveValue.Long(v)       => out.writeRaw(v.toString)
        case PrimitiveValue.Float(v)      => out.writeRaw(v.toString)
        case PrimitiveValue.Double(v)     => out.writeRaw(v.toString)
        case PrimitiveValue.Boolean(v)    => out.writeRaw(v.toString)
        case PrimitiveValue.Byte(v)       => out.writeRaw(v.toString)
        case PrimitiveValue.Short(v)      => out.writeRaw(v.toString)
        case PrimitiveValue.BigDecimal(v) => out.writeRaw(v.bigDecimal.toPlainString)
        case PrimitiveValue.BigInt(v)     => out.writeRaw(v.toString)
        case PrimitiveValue.Unit          => out.writeNull()
        case PrimitiveValue.Char(c)       => out.writeRaw(c.toString)
        case _                            => out.writeRaw(p.toString)
      }
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

  // Type aliases for internal use
  private type Elem       = Any
  private type Key        = Any
  private type Value      = Any
  private type Wrapped    = Any
  private type Col[X]     = Any
  private type MapT[X, Y] = Any
  private type TC[X]      = ToonBinaryCodec[X]

  private def getRenamed(modifiers: Seq[Modifier.Term]): Option[String] =
    modifiers.collectFirst { case Modifier.rename(name) => name }

  private def isTransient(modifiers: Seq[Modifier.Term]): Boolean =
    modifiers.exists { case _: Modifier.transient => true; case _ => false }

  private def isSimpleKey(codec: ToonBinaryCodec[_]): Boolean =
    codec.valueType != ToonBinaryCodec.objectType

  /**
   * Derives a codec for primitive types.
   */
  private def derivePrimitiveCodec[A](primitive: Reflect.Primitive[Binding, A]): ToonBinaryCodec[A] =
    if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
      (primitive.primitiveType match {
        case _: PrimitiveType.Unit.type      => ToonBinaryCodec.unitCodec
        case _: PrimitiveType.Boolean        => ToonBinaryCodec.booleanCodec
        case _: PrimitiveType.Byte           => ToonBinaryCodec.byteCodec
        case _: PrimitiveType.Short          => ToonBinaryCodec.shortCodec
        case _: PrimitiveType.Int            => ToonBinaryCodec.intCodec
        case _: PrimitiveType.Long           => ToonBinaryCodec.longCodec
        case _: PrimitiveType.Float          => ToonBinaryCodec.floatCodec
        case _: PrimitiveType.Double         => ToonBinaryCodec.doubleCodec
        case _: PrimitiveType.Char           => ToonBinaryCodec.charCodec
        case _: PrimitiveType.String         => ToonBinaryCodec.stringCodec
        case _: PrimitiveType.BigInt         => ToonBinaryCodec.bigIntCodec
        case _: PrimitiveType.BigDecimal     => ToonBinaryCodec.bigDecimalCodec
        case _: PrimitiveType.Duration       => ToonBinaryCodec.durationCodec
        case _: PrimitiveType.Instant        => ToonBinaryCodec.instantCodec
        case _: PrimitiveType.LocalDate      => ToonBinaryCodec.localDateCodec
        case _: PrimitiveType.LocalDateTime  => ToonBinaryCodec.localDateTimeCodec
        case _: PrimitiveType.LocalTime      => ToonBinaryCodec.localTimeCodec
        case _: PrimitiveType.MonthDay       => ToonBinaryCodec.monthDayCodec
        case _: PrimitiveType.OffsetDateTime => ToonBinaryCodec.offsetDateTimeCodec
        case _: PrimitiveType.Period         => ToonBinaryCodec.periodCodec
        case _: PrimitiveType.Year           => ToonBinaryCodec.yearCodec
        case _: PrimitiveType.YearMonth      => ToonBinaryCodec.yearMonthCodec
        case _: PrimitiveType.ZoneId         => ToonBinaryCodec.zoneIdCodec
        case _: PrimitiveType.ZoneOffset     => ToonBinaryCodec.zoneOffsetCodec
        case _: PrimitiveType.ZonedDateTime  => ToonBinaryCodec.zonedDateTimeCodec
        case _: PrimitiveType.UUID           => ToonBinaryCodec.uuidCodec
        case _: PrimitiveType.Month          => ToonBinaryCodec.monthCodec
        case _: PrimitiveType.DayOfWeek      => ToonBinaryCodec.dayOfWeekCodec
        case _: PrimitiveType.Currency       => ToonBinaryCodec.currencyCodec
        case _                               => ToonBinaryCodec.stringCodec
      }).asInstanceOf[ToonBinaryCodec[A]]
    } else primitive.primitiveBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force

  /**
   * Derives a codec for record types (case classes/product types).
   */
  private def deriveRecordCodec[A](record: Reflect.Record[Binding, A]): ToonBinaryCodec[A] =
    if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
      val binding         = record.recordBinding.asInstanceOf[Binding.Record[A]]
      val fields          = record.fields
      val fieldCodecs     = fields.map(f => deriveCodec(f.value).asInstanceOf[ToonBinaryCodec[Any]])
      val fieldNames      = fields.map(f => getRenamed(f.modifiers).getOrElse(fieldNameMapper(f.name)))
      val fieldTransients = fields.map(f => isTransient(f.modifiers))

      // Compute proper register offsets - sum of all field valueOffsets
      val fieldOffsets = new Array[Long](fields.length)
      var offset       = 0L
      var idx          = 0
      while (idx < fields.length) {
        fieldOffsets(idx) = offset
        offset = RegisterOffset.add(fieldCodecs(idx).valueOffset, offset)
        idx += 1
      }
      val usedRegisters: RegisterOffset = offset

      new ToonBinaryCodec[A](ToonBinaryCodec.objectType) {
        override val recordFields: Option[IndexedSeq[String]] = Some(
          fields
            .lazyZip(fieldTransients)
            .lazyZip(fieldNames)
            .collect { case (_, false, name) =>
              name
            }
            .toIndexedSeq
        )

        override val recordFieldComplexities: Option[IndexedSeq[Boolean]] = Some(
          fieldCodecs
            .lazyZip(fieldTransients)
            .collect { case (codec, false) =>
              codec.isComplexType
            }
            .toIndexedSeq
        )

        override val isComplexType: Boolean = true

        private[this] val deconstructor = binding.deconstructor
        private[this] val constructor   = binding.constructor
        private[this] val regOffset     = usedRegisters
        private[this] val offsets       = fieldOffsets

        def decodeValue(in: ToonReader, default: A): A = {
          val regs = Registers(regOffset)
          val len  = fields.length
          var i    = 0

          while (i < len) {
            if (!fieldTransients(i)) {
              in.expectKey(fieldNames(i))
              val codec       = fieldCodecs(i)
              val value       = codec.decodeValue(in, null.asInstanceOf[Any])
              val fieldOffset = fieldOffsets(i)
              // Set value based on codec valueType
              (codec.valueType: @scala.annotation.switch) match {
                case 0 => regs.setObject(fieldOffset, value.asInstanceOf[AnyRef])
                case 1 => regs.setInt(fieldOffset, value.asInstanceOf[Int])
                case 2 => regs.setLong(fieldOffset, value.asInstanceOf[Long])
                case 3 => regs.setFloat(fieldOffset, value.asInstanceOf[Float])
                case 4 => regs.setDouble(fieldOffset, value.asInstanceOf[Double])
                case 5 => regs.setBoolean(fieldOffset, value.asInstanceOf[Boolean])
                case 6 => regs.setByte(fieldOffset, value.asInstanceOf[Byte])
                case 7 => regs.setChar(fieldOffset, value.asInstanceOf[Char])
                case 8 => regs.setShort(fieldOffset, value.asInstanceOf[Short])
                case _ => regs.setObject(fieldOffset, value.asInstanceOf[AnyRef])
              }
            }
            i += 1
          }
          constructor.construct(regs, 0L)
        }

        override def encodeFields(x: A, out: ToonWriter): Unit = {
          val regs = Registers(regOffset)
          deconstructor.deconstruct(regs, 0L, x)
          val len = fields.length
          var i   = 0
          while (i < len) {
            if (!fieldTransients(i)) {
              if (fieldCodecs(i).isSequence) out.writeKeyNoColon(fieldNames(i))
              else out.writeKey(fieldNames(i))
              val codec       = fieldCodecs(i)
              val fieldOffset = fieldOffsets(i)
              (codec.valueType: @scala.annotation.switch) match {
                case 0 => codec.encodeValue(regs.getObject(fieldOffset), out)
                case 1 => codec.asInstanceOf[ToonBinaryCodec[Int]].encodeValue(regs.getInt(fieldOffset), out)
                case 2 => codec.asInstanceOf[ToonBinaryCodec[Long]].encodeValue(regs.getLong(fieldOffset), out)
                case 3 => codec.asInstanceOf[ToonBinaryCodec[Float]].encodeValue(regs.getFloat(fieldOffset), out)
                case 4 => codec.asInstanceOf[ToonBinaryCodec[Double]].encodeValue(regs.getDouble(fieldOffset), out)
                case 5 => codec.asInstanceOf[ToonBinaryCodec[Boolean]].encodeValue(regs.getBoolean(fieldOffset), out)
                case 6 => codec.asInstanceOf[ToonBinaryCodec[Byte]].encodeValue(regs.getByte(fieldOffset), out)
                case 7 => codec.asInstanceOf[ToonBinaryCodec[Char]].encodeValue(regs.getChar(fieldOffset), out)
                case 8 => codec.asInstanceOf[ToonBinaryCodec[Short]].encodeValue(regs.getShort(fieldOffset), out)
                case _ => codec.encodeValue(regs.getObject(fieldOffset), out)
              }
            }
            i += 1
          }
        }

        override def encodeValue(x: A, out: ToonWriter): Unit = {
          val shouldIndent = out.currentIndentLevel > 0 || out.count > 0

          if (shouldIndent) {
            out.newLine()
            out.currentIndentLevel += 1
          }

          encodeFields(x, out)

          if (shouldIndent) out.currentIndentLevel -= 1
        }

        override def decodeValues(in: ToonReader, default: A): A = {
          val regs  = Registers(regOffset)
          val len   = fields.length
          var idx   = 0
          var comma = false

          while (idx < len) {
            if (!fieldTransients(idx)) {
              in.skipWhitespace()
              if (comma && in.ptr < in.limit && in.buf(in.ptr) == ',') in.ptr += 1
              val codec       = fieldCodecs(idx)
              val fieldOffset = offsets(idx)

              (codec.valueType: @scala.annotation.switch) match {
                case 0 =>
                  regs.setObject(fieldOffset, codec.decodeValue(in, null.asInstanceOf[Any]).asInstanceOf[AnyRef])
                case 1 => regs.setInt(fieldOffset, codec.asInstanceOf[ToonBinaryCodec[Int]].decodeValue(in, 0))
                case 2 => regs.setLong(fieldOffset, codec.asInstanceOf[ToonBinaryCodec[Long]].decodeValue(in, 0L))
                case 3 => regs.setFloat(fieldOffset, codec.asInstanceOf[ToonBinaryCodec[Float]].decodeValue(in, 0.0f))
                case 4 =>
                  regs.setDouble(fieldOffset, codec.asInstanceOf[ToonBinaryCodec[Double]].decodeValue(in, 0.0))
                case 5 =>
                  regs.setBoolean(fieldOffset, codec.asInstanceOf[ToonBinaryCodec[Boolean]].decodeValue(in, false))
                case 6 => regs.setByte(fieldOffset, codec.asInstanceOf[ToonBinaryCodec[Byte]].decodeValue(in, 0))
                case 7 =>
                  regs.setChar(fieldOffset, codec.asInstanceOf[ToonBinaryCodec[Char]].decodeValue(in, '\u0000'))
                case 8 => regs.setShort(fieldOffset, codec.asInstanceOf[ToonBinaryCodec[Short]].decodeValue(in, 0))
                case _ =>
                  regs.setObject(fieldOffset, codec.decodeValue(in, null.asInstanceOf[Any]).asInstanceOf[AnyRef])
              }
              comma = true
            }
            idx += 1
          }
          constructor.construct(regs, 0L)
        }

        override def encodeValues(x: A, out: ToonWriter): Unit = {
          val regs = Registers(regOffset)
          deconstructor.deconstruct(regs, 0L, x)

          val len   = fields.length
          var idx   = 0
          var comma = false
          while (idx < len) {
            if (!fieldTransients(idx)) {
              if (comma) out.writeRaw(",")
              val codec       = fieldCodecs(idx)
              val fieldOffset = offsets(idx)
              // Get value based on codec valueType
              (codec.valueType: @scala.annotation.switch) match {
                case 0 => codec.encodeValue(regs.getObject(fieldOffset), out)
                case 1 => codec.asInstanceOf[ToonBinaryCodec[Int]].encodeValue(regs.getInt(fieldOffset), out)
                case 2 => codec.asInstanceOf[ToonBinaryCodec[Long]].encodeValue(regs.getLong(fieldOffset), out)
                case 3 => codec.asInstanceOf[ToonBinaryCodec[Float]].encodeValue(regs.getFloat(fieldOffset), out)
                case 4 => codec.asInstanceOf[ToonBinaryCodec[Double]].encodeValue(regs.getDouble(fieldOffset), out)
                case 5 => codec.asInstanceOf[ToonBinaryCodec[Boolean]].encodeValue(regs.getBoolean(fieldOffset), out)
                case 6 => codec.asInstanceOf[ToonBinaryCodec[Byte]].encodeValue(regs.getByte(fieldOffset), out)
                case 7 => codec.asInstanceOf[ToonBinaryCodec[Char]].encodeValue(regs.getChar(fieldOffset), out)
                case 8 => codec.asInstanceOf[ToonBinaryCodec[Short]].encodeValue(regs.getShort(fieldOffset), out)
                case _ => codec.encodeValue(regs.getObject(fieldOffset), out)
              }
              comma = true
            }
            idx += 1
          }
        }
      }
    } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force

  /**
   * Derives a codec for variant types (sealed traits/sum types).
   */
  private def deriveVariantCodec[A](
    variant: Reflect.Variant[Binding, A],
    reflect: Reflect[Binding, A]
  ): ToonBinaryCodec[A] =
    if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
      val binding    = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
      val cases      = variant.cases
      val caseCodecs = cases.map(c => deriveCodec(c.value).asInstanceOf[ToonBinaryCodec[Any]])
      val caseNames  = cases.map(c => getRenamed(c.modifiers).getOrElse(caseNameMapper(c.name)))
      val typeName   = reflect.typeName

      // Check if all cases are case objects (for enumValuesAsStrings handling)
      val isCaseObjectEnum = cases.forall { c =>
        c.value match {
          case r: Reflect.Record[Binding @unchecked, _] => r.fields.isEmpty
          case _                                        => false
        }
      }

      val isOption = typeName.name == "Option" && (typeName.namespace.packages == List(
        "scala"
      ) || typeName.namespace.packages == List("java", "util"))

      if (isOption) {
        // Optimized Option handling: decode null as None, values as Some(v)
        val someCase  = cases.find(_.name == "Some").get
        val someCodec = deriveCodec(someCase.value).asInstanceOf[ToonBinaryCodec[Any]]
        // Simplified element codec extraction to avoid GADT issues
        // We need to unwrap the 'value' field from Some[A] to get the codec for A.
        // someCase.value should be a Record.
        val elementCodec = someCase.value match {
          case r: Reflect.Record[Binding @unchecked, _] =>
            if (r.fields.nonEmpty)
              deriveCodec(r.fields.head.value.asInstanceOf[Reflect[Binding, Any]]).asInstanceOf[ToonBinaryCodec[Any]]
            else someCodec
          case _ => someCodec
        }

        new ToonBinaryCodec[A]() {
          def decodeValue(in: ToonReader, default: A): A = {
            in.skipWhitespace()
            if (in.peek() == 'n') {
              val s = in.readUnquotedString()
              if (s == "null") None.asInstanceOf[A]
              else in.decodeError(s"Expected 'null' for None, got $s")
            } else {
              val value = elementCodec.decodeValue(in, null.asInstanceOf[Any])
              // Reconstruct Some(value)
              // We need to use the binding constructor for Some?
              // Or just assume standard Option?
              // Since we have the binding, we can allow the schema to construct it.
              // But binding.constructor needs a variant case choice.
              // We can use the 'Some' case constructor.
              // This is tricky via Binding/Reflect alone without type evidence.
              // Hack: We know it's Option.
              Some(value).asInstanceOf[A]
            }
          }

          def encodeValue(x: A, out: ToonWriter): Unit =
            x match {
              case Some(v) => elementCodec.encodeValue(v.asInstanceOf[Any], out)
              case None    => out.writeNull()
              case _       => // Should not happen
            }
        }.asInstanceOf[ToonBinaryCodec[A]]
      } else {
        discriminatorKind match {
          case DiscriminatorKind.Key =>
            new ToonBinaryCodec[A]() {
              def decodeValue(in: ToonReader, default: A): A =
                if (enumValuesAsStrings && isCaseObjectEnum) {
                  val name = in.readString()
                  val idx  = caseNames.indexOf(name)
                  if (idx < 0) in.decodeError(s"Unknown case: $name")
                  caseCodecs(idx).decodeValue(in, null.asInstanceOf[Any]).asInstanceOf[A]
                } else {
                  val key = in.readKey()
                  val idx = caseNames.indexOf(key)
                  if (idx < 0) in.decodeError(s"Unknown case: $key")
                  caseCodecs(idx).decodeValue(in, null.asInstanceOf[Any]).asInstanceOf[A]
                }

              def encodeValue(x: A, out: ToonWriter): Unit = {
                val idx = binding.discriminator.discriminate(x)
                if (enumValuesAsStrings && isCaseObjectEnum) {
                  out.writeRaw(caseNames(idx))
                } else {
                  out.writeKey(caseNames(idx))
                  out.currentIndentLevel += 1
                  caseCodecs(idx).encodeValue(x.asInstanceOf[Any], out)
                  out.currentIndentLevel -= 1
                }
              }
            }

          case DiscriminatorKind.Field(fieldName) =>
            new ToonBinaryCodec[A]() {
              def decodeValue(in: ToonReader, default: A): A = {
                val firstKey = in.readKey()
                if (firstKey != fieldName)
                  in.decodeError(s"Expected discriminator field '$fieldName', got '$firstKey'")
                val caseName = in.readString()
                in.skipWhitespace()

                val idx = caseNames.indexOf(caseName)
                if (idx < 0) in.decodeError(s"Unknown case: $caseName")
                caseCodecs(idx).decodeValue(in, null.asInstanceOf[Any]).asInstanceOf[A]
              }

              def encodeValue(x: A, out: ToonWriter): Unit = {
                val idx = binding.discriminator.discriminate(x)
                out.writeKey(fieldName)
                out.writeRaw(caseNames(idx))
                out.newLine()
                caseCodecs(idx).encodeFields(x.asInstanceOf[Any], out)
              }
            }

          case DiscriminatorKind.None =>
            new ToonBinaryCodec[A]() {
              def decodeValue(in: ToonReader, default: A): A = {
                val startPtr = in.ptr
                var idx      = 0
                while (idx < cases.length) {
                  try {
                    in.ptr = startPtr
                    return caseCodecs(idx).decodeValue(in, null.asInstanceOf[Any]).asInstanceOf[A]
                  } catch {
                    case _: SchemaError =>
                      idx += 1
                  }
                }
                in.decodeError("Could not decode value as any case of the variant")
              }

              def encodeValue(x: A, out: ToonWriter): Unit = {
                val idx = binding.discriminator.discriminate(x)
                caseCodecs(idx).encodeValue(x.asInstanceOf[Any], out)
              }
            }
        }
      }
    } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force

  /**
   * Derives a codec for sequence types (List, Vector, etc.).
   */
  private def deriveSequenceCodec[A](reflect: Reflect[Binding, A]): ToonBinaryCodec[A] = {
    val sequence = reflect.asSequenceUnknown.get.sequence
    if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
      val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
      val elementCodec = deriveCodec(sequence.element).asInstanceOf[ToonBinaryCodec[Elem]]

      val elementFields       = elementCodec.recordFields
      val elementComplexities = elementCodec.recordFieldComplexities

      // Tabular eligibility requirement:
      // 1. Must be a record (fields defined)
      // 2. Must not contain complex nested types (Records, Sequences, Maps)
      val isTabularEligible = elementFields.isDefined &&
        elementFields.get.nonEmpty &&
        elementComplexities.exists(_.forall(_ == false))

      val isPrimitive = elementCodec.valueType != ToonBinaryCodec.objectType

      new ToonBinaryCodec[Col[Elem]]() {
        def decodeValue(in: ToonReader, default: Col[Elem]): Col[Elem] = {
          if (in.lastArraySize < 0 && in.peek() == '[') {
            in.readArrayHeader()
          }
          val size      = in.lastArraySize
          val isTabular = in.lastArrayTabular
          in.lastArraySize = -1
          in.lastArrayTabular = false

          val builder = binding.constructor.newObjectBuilder[Elem](if (size > 0) size else 8)

          var sawDash = false
          if (isTabular && elementFields.isDefined) {
            var i = 0
            while (i < size) {
              in.skipWhitespace()
              val elem = elementCodec.decodeValues(in, null.asInstanceOf[Elem])
              binding.constructor.addObject(builder, elem)
              i += 1
            }
          } else {
            var i = 0
            while (i < size) {
              in.skipWhitespace()
              if (in.ptr + 1 < in.limit && in.buf(in.ptr) == '-' && Character.isWhitespace(in.buf(in.ptr + 1).toChar)) {
                sawDash = true
                in.ptr += 1
                in.skipWhitespace()
              } else if (i > 0 && in.ptr < in.limit && in.buf(in.ptr) == ',') {
                in.ptr += 1
              }
              binding.constructor.addObject(builder, elementCodec.decodeValue(in, null.asInstanceOf[Elem]))
              i += 1
            }
          }

          // Context-aware strict validation
          if (in.config.strictArrayLength) {
            val next = in.peekNextSignificant()
            if (next == ',') {
              in.decodeError("Strict array validation failed: Found extra elements (trailing comma)")
            }
            if (sawDash && next == '-') {
              in.decodeError("Strict array validation failed: Found extra elements (trailing dash)")
            }
          }
          binding.constructor.resultObject(builder)
        }

        def encodeValue(x: Col[Elem], out: ToonWriter): Unit = {
          val it1   = binding.deconstructor.deconstruct(x)
          var count = 0
          while (it1.hasNext) { it1.next(); count += 1 }

          val useTabular = arrayFormat match {
            case ArrayFormat.Tabular => isTabularEligible
            case ArrayFormat.Inline  => false
            case ArrayFormat.List    => false
            case ArrayFormat.Auto    => isTabularEligible && count > 0
          }

          val useList = arrayFormat match {
            case ArrayFormat.List => true
            case ArrayFormat.Auto => !isPrimitive && !isTabularEligible
            case _                => false
          }

          if (useTabular && elementFields.isDefined) {
            val fields = elementFields.get
            out.writeRaw("[" + count + "]{" + fields.mkString(",") + "}:")
            out.newLine()
            out.currentIndentLevel += 1
            val it2 = binding.deconstructor.deconstruct(x)
            while (it2.hasNext) {
              out.writeIndent()
              elementCodec.encodeValues(it2.next(), out)
              out.newLine()
            }
            out.currentIndentLevel -= 1
          } else if (useList) {
            out.writeRaw("[" + count + "]:")
            out.newLine()

            val it2 = binding.deconstructor.deconstruct(x)
            while (it2.hasNext) {
              out.writeIndent()
              out.writeRaw("- ")
              out.currentIndentLevel += 1
              elementCodec.encodeValue(it2.next(), out)
              out.currentIndentLevel -= 1
              out.newLine()
            }
          } else {

            if (count > 0) out.writeRaw("[" + count + "]: ") else out.writeRaw("[" + count + "]:")

            val it2   = binding.deconstructor.deconstruct(x)
            var first = true
            while (it2.hasNext) {
              if (!first) out.writeRaw(delimiter.toString)
              elementCodec.encodeValue(it2.next(), out)
              first = false
            }
          }
        }
        override val isComplexType: Boolean = true
        override val isSequence: Boolean    = true
      }.asInstanceOf[ToonBinaryCodec[A]]
    } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
  }.asInstanceOf[ToonBinaryCodec[A]]

  /**
   * Derives a codec for map types.
   */
  private def deriveMapCodec[A](reflect: Reflect[Binding, A]): ToonBinaryCodec[A] = {
    val map = reflect.asMapUnknown.get.map
    if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
      val binding   = map.mapBinding.asInstanceOf[Binding.Map[MapT, Key, Value]]
      val keyCodec  = deriveCodec(map.key).asInstanceOf[ToonBinaryCodec[Key]]
      val valCodec  = deriveCodec(map.value).asInstanceOf[ToonBinaryCodec[Value]]
      val simpleKey = isSimpleKey(keyCodec)

      new ToonBinaryCodec[MapT[Key, Value]]() {
        override val isComplexType: Boolean                                          = true
        def decodeValue(in: ToonReader, default: MapT[Key, Value]): MapT[Key, Value] = {
          val builder = binding.constructor.newObjectBuilder[Key, Value]()
          in.skipWhitespace()

          if (in.ptr < in.limit && in.buf(in.ptr) == '[') {
            // Parse as list of pairs [[k,v], [k,v]] or List-style entries
            // Robustly read header [N]:
            in.readArrayHeader()
            val count = if (in.lastArraySize >= 0) in.lastArraySize else 0
            in.lastArraySize = -1

            if (!simpleKey) {
              var i = 0
              while (i < count) {
                in.skipWhitespace()
                if (in.ptr < in.limit && in.buf(in.ptr) == '-') {
                  in.ptr += 1
                  in.skipWhitespace()
                }

                if (in.ptr < in.limit && in.buf(in.ptr) == '[') in.ptr += 1

                val k = keyCodec.decodeValue(in, null.asInstanceOf[Key])
                in.skipWhitespace()
                if (in.ptr < in.limit && in.buf(in.ptr) == ',') in.ptr += 1

                val v = valCodec.decodeValue(in, null.asInstanceOf[Value])

                in.skipWhitespace()
                if (in.ptr < in.limit && in.buf(in.ptr) == ']') in.ptr += 1

                binding.constructor.addObject(builder, k, v)
                i += 1
              }
              in.validateArrayEnd()
            } else {
              // Fallback for simple map if encoded as array (optional robustness)
              in.decodeError("Unexpected array format for simple map")
            }
          } else {
            while (in.ptr < in.limit && !in.isNewline(in.buf(in.ptr))) {
              val keyStr = in.readKey()
              val k      = keyCodec
                .decodeFromString(keyStr)
                .getOrElse(
                  in.decodeError(s"Invalid key: $keyStr")
                )
              val v = valCodec.decodeValue(in, null.asInstanceOf[Value])
              binding.constructor.addObject(builder, k, v)
              in.skipWhitespace()
            }
          }
          binding.constructor.resultObject(builder)
        }

        def encodeValue(x: MapT[Key, Value], out: ToonWriter): Unit = {
          val it = binding.deconstructor.deconstruct(x)

          if (simpleKey) {
            out.currentIndentLevel += 1
            while (it.hasNext) {
              val kv     = it.next()
              val k: Any = binding.deconstructor.getKey(kv).asInstanceOf[Any]
              val v: Any = binding.deconstructor.getValue(kv).asInstanceOf[Any]
              out.writeKey(k.toString)
              valCodec.asInstanceOf[ToonBinaryCodec[Any]].encodeValue(v, out)
              out.newLine()
            }
            out.currentIndentLevel -= 1
          } else {
            val itCount = binding.deconstructor.deconstruct(x)
            var count   = 0
            while (itCount.hasNext) { itCount.next(); count += 1 }

            out.writeRaw("[" + count + "]:")
            out.newLine()

            val it2 = binding.deconstructor.deconstruct(x)
            while (it2.hasNext) {
              out.writeIndent()
              out.writeRaw("- [")
              val kv     = it2.next()
              val k: Any = binding.deconstructor.getKey(kv).asInstanceOf[Any]
              val v: Any = binding.deconstructor.getValue(kv).asInstanceOf[Any]

              keyCodec.asInstanceOf[ToonBinaryCodec[Any]].encodeValue(k, out)
              out.writeRaw(", ")
              valCodec.asInstanceOf[ToonBinaryCodec[Any]].encodeValue(v, out)
              out.writeRaw("]")
              out.newLine()
            }
          }
        }
      }.asInstanceOf[ToonBinaryCodec[A]]
    } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
  }.asInstanceOf[ToonBinaryCodec[A]]

  /**
   * Derives a codec for wrapper types (newtypes).
   */
  private def deriveWrapperCodec[A](reflect: Reflect[Binding, A]): ToonBinaryCodec[A] = {
    val wrapper = reflect.asWrapperUnknown.get.wrapper
    if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
      val binding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
      val codec   = deriveCodec(wrapper.wrapped).asInstanceOf[ToonBinaryCodec[Wrapped]]

      new ToonBinaryCodec[A]() {
        private[this] val unwrap       = binding.unwrap
        private[this] val wrap         = binding.wrap
        private[this] val wrappedCodec = codec

        def decodeValue(in: ToonReader, default: A): A = {
          val inner =
            try wrappedCodec.decodeValue(in, null.asInstanceOf[Wrapped])
            catch { case error if NonFatal(error) => in.decodeError(s"Decode error: ${error.getMessage}") }
          wrap(inner) match {
            case Right(x)    => x
            case Left(error) => in.decodeError(error)
          }
        }

        def encodeValue(x: A, out: ToonWriter): Unit =
          wrappedCodec.encodeValue(unwrap(x), out)
      }
    } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
  }

  /**
   * Derives a codec for dynamic values (schema-less encoding/decoding).
   */
  private def deriveDynamicCodec[A](): ToonBinaryCodec[A] =
    new ToonBinaryCodec[DynamicValue]() {
      def decodeValue(in: ToonReader, default: DynamicValue): DynamicValue = {
        in.skipWhitespace()
        if (in.ptr >= in.limit) in.decodeError("Unexpected end of input")
        val c = in.buf(in.ptr)

        if (c == '[') {
          // Sequence (Array)
          in.readArrayHeader()
          val size = if (in.lastArraySize >= 0) in.lastArraySize else 0
          in.lastArraySize = -1 // Reset

          val builder = scala.collection.mutable.ListBuffer.empty[DynamicValue]
          var i       = 0
          // For dynamic arrays, we iterate size times
          while (i < size) {
            in.skipWhitespace()
            if (in.ptr < in.limit && in.buf(in.ptr) == '-') {
              in.ptr += 1 // skip -
            } else if (i > 0 && in.ptr < in.limit && in.buf(in.ptr) == ',') {
              in.ptr += 1 // skip ,
            }
            builder += decodeValue(in, default)
            i += 1
          }
          in.validateArrayEnd()
          DynamicValue.Sequence(Vector.from(builder))
        } else if (c == '"' || (c >= '0' && c <= '9') || c == '-' || c == 't' || c == 'f' || c == 'n') {
          // Primitive (String, Number, Boolean, Null)
          val s = if (c == '"') in.readQuotedString() else in.readUnquotedString()
          if (s == "null")
            DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Unit) // Map null to Unit/None representation
          else if (s == "true") DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(true))
          else if (s == "false") DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(false))
          else {
            // Number detection with leading-zero rules
            val isLeadingZero = (s.length > 1 && s.startsWith("0") && s(1) != '.') ||
              (s.length > 2 && s.startsWith("-0") && s(2) != '.')

            if (isLeadingZero) {
              DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String(s))
            } else {
              try {
                if (s.contains('.') || s.length > 9 || s.contains('e') || s.contains('E'))
                  DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.BigDecimal(BigDecimal(s)))
                else DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Int(s.toInt))
              } catch {
                case _: NumberFormatException => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String(s))
              }
            }
          }
        } else {
          // Assume Object/Record
          val values     = scala.collection.mutable.Map.empty[String, DynamicValue]
          var continuing = true

          // We need to support reading multiple fields.
          // Since TOON relies on indentation/newlines for scope, and we are in a streaming context without schema,
          // we can only guess the end of the record by checking if the next token looks like a key.
          // This is a naive implementation: it reads as long as it finds a valid key (unquoted or quoted) followed by colon.

          while (continuing) {
            try {
              in.skipWhitespace()
              // If we hit EOF or something that isn't a key, stop
              if (in.ptr >= in.limit) {
                continuing = false
              } else {
                // Check for key pattern: identifier followed by :
                // This is speculative. A robust dynamic decoder is hard without schema.
                // We'll rely on the existing readKey behavior which throws if no colon.
                // But we must PEEK first to see if we should stop.
                val nextByte = in.peek()
                if (nextByte == ']' || nextByte == '}' || nextByte == '-') {
                  // End of array or likely next list item
                  continuing = false
                } else {
                  val key   = in.readKey()
                  val value = decodeValue(in, default)
                  values.put(key, value)
                }
              }
            } catch {
              case e: SchemaError =>
                if (values.nonEmpty) continuing = false // Assume end of multi-field record
                else throw e                            // Actual decoding error
            }
          }
          DynamicValue.Record(Vector.from(values).map { case (k, v) => k -> v })
        }
      }

      def encodeValue(x: DynamicValue, out: ToonWriter): Unit = x match {
        case DynamicValue.Record(values) =>
          values.foreach { case (k, v) =>
            out.writeKey(k)
            out.currentIndentLevel += 1
            encodeValue(v, out)
            out.currentIndentLevel -= 1
            out.newLine()
          }
        case DynamicValue.Sequence(values) =>
          out.writeRaw("[" + values.size + "]:")
          out.newLine()
          values.foreach { v =>
            out.writeIndent()
            out.writeRaw("- ")
            out.currentIndentLevel += 1
            encodeValue(v, out)
            out.currentIndentLevel -= 1
            out.newLine()
          }
        case DynamicValue.Primitive(p) =>
          out.writeRaw(p.toString)
        case _ => out.writeRaw(x.toString)
      }
    }.asInstanceOf[ToonBinaryCodec[A]]

  /**
   * Derives a TOON binary codec for the given schema.
   */
  def derive[A](schema: Schema[A]): ToonBinaryCodec[A] =
    deriveCodec(schema.reflect.asInstanceOf[Reflect[Binding, A]])

  /**
   * Internal method to derive a codec from a Reflect tree.
   */
  def deriveCodec[A](reflect: Reflect[Binding, A]): ToonBinaryCodec[A] = {
    if (reflect.isPrimitive) {
      derivePrimitiveCodec(reflect.asPrimitive.get).asInstanceOf[ToonBinaryCodec[A]]
    } else if (reflect.isRecord) {
      deriveRecordCodec(reflect.asRecord.get)
    } else if (reflect.isVariant) {
      deriveVariantCodec(reflect.asVariant.get, reflect)
    } else if (reflect.isSequence) {
      deriveSequenceCodec(reflect)
    } else if (reflect.isMap) {
      deriveMapCodec(reflect)
    } else if (reflect.isWrapper) {
      deriveWrapperCodec(reflect)
    } else if (reflect.isDynamic) {
      val dynamic = reflect.asDynamic.get
      if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) {
        deriveDynamicCodec[A]()
      } else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else {
      throw new IllegalArgumentException(s"Cannot derive TOON codec for: $reflect")
    }
  }.asInstanceOf[ToonBinaryCodec[A]]
}
