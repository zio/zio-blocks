package zio.blocks.schema.toon

import zio.blocks.docs.Doc
import zio.blocks.schema.toon.ToonBinaryCodec._
import zio.blocks.schema.toon.ToonCodecUtils._
import zio.blocks.schema.binding.{Binding, BindingType, Discriminator, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema._
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.{TypeId, Owner}
import java.util
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object ToonBinaryCodecDeriver
    extends ToonBinaryCodecDeriver(
      fieldNameMapper = NameMapper.Identity,
      caseNameMapper = NameMapper.Identity,
      discriminatorKind = DiscriminatorKind.Key,
      arrayFormat = ArrayFormat.Auto,
      delimiter = Delimiter.Comma,
      rejectExtraFields = false,
      enumValuesAsStrings = true,
      transientNone = true,
      requireOptionFields = false,
      transientEmptyCollection = true,
      requireCollectionFields = false,
      transientDefaultValue = true,
      requireDefaultValueFields = false
    )

class ToonBinaryCodecDeriver private[toon] (
  fieldNameMapper: NameMapper,
  caseNameMapper: NameMapper,
  discriminatorKind: DiscriminatorKind,
  arrayFormat: ArrayFormat,
  delimiter: Delimiter,
  rejectExtraFields: Boolean,
  enumValuesAsStrings: Boolean,
  transientNone: Boolean,
  requireOptionFields: Boolean,
  transientEmptyCollection: Boolean,
  requireCollectionFields: Boolean,
  transientDefaultValue: Boolean,
  requireDefaultValueFields: Boolean
) extends Deriver[ToonBinaryCodec] {

  def withFieldNameMapper(fieldNameMapper: NameMapper): ToonBinaryCodecDeriver =
    copy(fieldNameMapper = fieldNameMapper)

  def withCaseNameMapper(caseNameMapper: NameMapper): ToonBinaryCodecDeriver =
    copy(caseNameMapper = caseNameMapper)

  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): ToonBinaryCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  def withArrayFormat(arrayFormat: ArrayFormat): ToonBinaryCodecDeriver =
    copy(arrayFormat = arrayFormat)

  def withDelimiter(delimiter: Delimiter): ToonBinaryCodecDeriver =
    copy(delimiter = delimiter)

  def withRejectExtraFields(rejectExtraFields: Boolean): ToonBinaryCodecDeriver =
    copy(rejectExtraFields = rejectExtraFields)

  def withEnumValuesAsStrings(enumValuesAsStrings: Boolean): ToonBinaryCodecDeriver =
    copy(enumValuesAsStrings = enumValuesAsStrings)

  def withTransientNone(transientNone: Boolean): ToonBinaryCodecDeriver =
    copy(transientNone = transientNone)

  def withRequireOptionFields(requireOptionFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireOptionFields = requireOptionFields)

  def withTransientEmptyCollection(transientEmptyCollection: Boolean): ToonBinaryCodecDeriver =
    copy(transientEmptyCollection = transientEmptyCollection)

  def withRequireCollectionFields(requireCollectionFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireCollectionFields = requireCollectionFields)

  def withTransientDefaultValue(transientDefaultValue: Boolean): ToonBinaryCodecDeriver =
    copy(transientDefaultValue = transientDefaultValue)

  def withRequireDefaultValueFields(requireDefaultValueFields: Boolean): ToonBinaryCodecDeriver =
    copy(requireDefaultValueFields = requireDefaultValueFields)

  private[this] def copy(
    fieldNameMapper: NameMapper = fieldNameMapper,
    caseNameMapper: NameMapper = caseNameMapper,
    discriminatorKind: DiscriminatorKind = discriminatorKind,
    arrayFormat: ArrayFormat = arrayFormat,
    delimiter: Delimiter = delimiter,
    rejectExtraFields: Boolean = rejectExtraFields,
    enumValuesAsStrings: Boolean = enumValuesAsStrings,
    transientNone: Boolean = transientNone,
    requireOptionFields: Boolean = requireOptionFields,
    transientEmptyCollection: Boolean = transientEmptyCollection,
    requireCollectionFields: Boolean = requireCollectionFields,
    transientDefaultValue: Boolean = transientDefaultValue,
    requireDefaultValueFields: Boolean = requireDefaultValueFields
  ) = new ToonBinaryCodecDeriver(
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
    requireDefaultValueFields
  )

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[ToonBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      Lazy(primitiveType match {
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
      })
    } else binding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance
  }.asInstanceOf[Lazy[ToonBinaryCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val len           = fields.length
      if (len == 0) Lazy {
        new ToonBinaryCodec[A]() {
          private[this] val constructor = recordBinding.constructor

          def decodeValue(in: ToonReader, default: A): A = {
            in.skipBlankLines()
            constructor.construct(null, 0)
          }

          def encodeValue(x: A, out: ToonWriter): Unit = ()
        }
      }
      else if (len == 1 && fields(0).value.isMap) {
        val mapField = fields(0)
        D.instance(mapField.value.metadata).map { mapCodec =>
          new ToonBinaryCodec[A]() {
            private[this] val mCodec        = mapCodec.asInstanceOf[ToonBinaryCodec[AnyRef]]
            private[this] val deconstructor = recordBinding.deconstructor
            private[this] val constructor   = recordBinding.constructor

            def decodeValue(in: ToonReader, default: A): A = {
              in.skipBlankLines()
              if (!in.hasMoreContent || in.peekTrimmedContent.isEmpty) {
                in.advanceLine()
                in.skipBlankLines()
              }
              val mapValue = mCodec.decodeValue(in, mCodec.nullValue)
              val regs     = Registers(mCodec.valueOffset)
              regs.setObject(0, mapValue)
              constructor.construct(regs, 0)
            }

            def encodeValue(x: A, out: ToonWriter): Unit = {
              val regs = Registers(mCodec.valueOffset)
              deconstructor.deconstruct(regs, 0, x)
              val mapValue = regs.getObject(0)
              mCodec.encodeValue(mapValue, out)
            }

            override def encodeAsField(fieldName: String, x: A, out: ToonWriter): Unit = {
              out.writeKeyOnly(fieldName)
              out.incrementDepth()
              encodeValue(x, out)
              out.decrementDepth()
            }
          }
        }
      } else
        Lazy {
          val isRecursive  = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
          var infos        = if (isRecursive) recursiveRecordCache.get.get(typeId) else null
          val deriveCodecs = infos eq null
          if (deriveCodecs) {
            infos = new Array[ToonFieldInfo](len)
            var idx = 0
            while (idx < len) {
              val field        = fields(idx)
              val fieldReflect = field.value
              infos(idx) = new ToonFieldInfo(
                DynamicOptic.Node.Field(field.name),
                defaultValueConstructor(fieldReflect),
                idx,
                isOptional(fieldReflect),
                isCollection(fieldReflect)
              )
              idx += 1
            }
            if (isRecursive) recursiveRecordCache.get.put(typeId, infos)
            discriminatorFields.set(null :: discriminatorFields.get)
          }
          var offset   = 0L
          val fieldMap = new java.util.HashMap[String, ToonFieldInfo]()
          var idx      = 0
          while (idx < len) {
            val field     = fields(idx)
            val fieldInfo = infos(idx)
            if (deriveCodecs) {
              val codec = D.instance(field.value.metadata).force
              fieldInfo.setCodec(codec)
              fieldInfo.setOffset(offset)
              offset = RegisterOffset.add(codec.valueOffset, offset)
            }
            var name: String = null
            field.modifiers.foreach {
              case m: Modifier.rename    => if (name eq null) name = m.name
              case m: Modifier.alias     => fieldMap.put(m.name, fieldInfo)
              case _: Modifier.transient => fieldInfo.nonTransient = false
              case _                     =>
            }
            if (name eq null) name = fieldNameMapper(field.name)
            fieldMap.put(name, fieldInfo)
            fieldInfo.setName(name)
            idx += 1
          }
          if (deriveCodecs) discriminatorFields.set(discriminatorFields.get.tail)
          val finalOffset = offset
          new ToonBinaryCodec[A]() {
            private[this] val deconstructor       = recordBinding.deconstructor
            private[this] val constructor         = recordBinding.constructor
            private[this] val fieldInfos          = infos
            private[this] val fieldIndex          = fieldMap
            private[this] var usedRegisters       = finalOffset
            private[this] val skipNone            = transientNone
            private[this] val skipEmptyCollection = transientEmptyCollection
            private[this] val skipDefaultValue    = transientDefaultValue
            private[this] val doReject            = rejectExtraFields
            private[this] val discriminatorField  = discriminatorFields.get.headOption.orNull

            def decodeValue(in: ToonReader, default: A): A = {
              in.skipBlankLines()
              val fieldLen = fieldInfos.length
              if (fieldLen > 0 && usedRegisters == 0) usedRegisters = fieldInfos(fieldLen - 1).usedRegisters
              val regs    = Registers(usedRegisters)
              val missing = new Array[Boolean](fieldLen)
              java.util.Arrays.fill(missing, true)
              if (!in.hasMoreContent || in.peekTrimmedContent.isEmpty) {
                in.advanceLine()
                in.skipBlankLines()
              }
              val startDepth = in.getDepth
              while (in.hasMoreLines) {
                in.skipBlankLines()
                if (!in.hasMoreLines) {
                  return setMissingAndConstruct(in, missing, regs)
                }
                val currentDepth = in.getDepth
                if (currentDepth < startDepth) {
                  return setMissingAndConstruct(in, missing, regs)
                }
                if (in.isListItem && currentDepth <= startDepth) {
                  return setMissingAndConstruct(in, missing, regs)
                }
                if (!in.hasMoreContent || in.peekTrimmedContent.isEmpty) in.advanceLine()
                else {
                  val rawKey  = in.readKeyWithArrayNotation()
                  var isArray = false
                  val key     = {
                    val bracketIdx =
                      if (rawKey.startsWith("\"")) {
                        val closeQuoteIdx = rawKey.indexOf('"', 1)
                        if (closeQuoteIdx > 0) rawKey.indexOf('[', closeQuoteIdx + 1)
                        else rawKey.indexOf('[')
                      } else rawKey.indexOf('[')
                    if (bracketIdx > 0) {
                      isArray = true
                      stripQuotes(rawKey, 0, bracketIdx)
                    } else stripQuotes(rawKey, 0, rawKey.length)
                  }
                  val fieldInfo = fieldIndex.get(key)
                  if (fieldInfo ne null) {
                    missing(fieldInfo.idx) = false
                    try {
                      if (isArray) fieldInfo.readArrayFieldValue(in, regs, 0, rawKey)
                      else fieldInfo.readValue(in, regs, 0)
                    } catch {
                      case err if NonFatal(err) => in.decodeError(fieldInfo.span, err)
                    }
                  } else skipOrReject(in, key)
                }
              }
              setMissingAndConstruct(in, missing, regs)
            }

            private[this] def stripQuotes(s: String, from: Int, to: Int): String =
              if (to - from >= 2 && s.charAt(from) == '"' && s.charAt(to - 1) == '"') s.substring(from + 1, to - 1)
              else s.substring(from, to)

            private[this] def skipOrReject(in: ToonReader, key: String): Unit =
              if (doReject && ((discriminatorField eq null) || discriminatorField.name != key)) {
                in.decodeError(s"Unexpected field: $key")
              } else in.advanceLine()

            private[this] def setMissingAndConstruct(in: ToonReader, missing: Array[Boolean], regs: Registers) = {
              var idx = 0
              while (idx < missing.length) {
                if (missing(idx)) fieldInfos(idx).setMissingValueOrError(in, regs, 0)
                idx += 1
              }
              constructor.construct(regs, 0)
            }

            def encodeValue(x: A, out: ToonWriter): Unit = {
              val fieldLen = fieldInfos.length
              if (fieldLen > 0 && usedRegisters == 0) usedRegisters = fieldInfos(fieldLen - 1).usedRegisters
              val regs = Registers(usedRegisters)
              deconstructor.deconstruct(regs, 0, x)
              if (discriminatorField ne null) {
                out.writeKey(discriminatorField.name)
                out.writeString(discriminatorField.value)
                out.newLine()
              }
              val inListItem   = out.isInListItemContext
              var firstWritten = false
              var depthAdded   = false
              var idx          = 0
              while (idx < fieldLen) {
                val fieldInfo = fieldInfos(idx)
                if (fieldInfo.nonTransient) {
                  if (inListItem && firstWritten && !depthAdded) {
                    out.incrementDepth()
                    depthAdded = true
                  }
                  if (skipDefaultValue && fieldInfo.hasDefault) fieldInfo.writeDefaultValue(out, regs, 0)
                  else if (skipNone && fieldInfo.isOptional) fieldInfo.writeOptional(out, regs, 0)
                  else if (skipEmptyCollection && fieldInfo.isCollection) fieldInfo.writeCollection(out, regs, 0)
                  else fieldInfo.writeRequired(out, regs, 0)
                  firstWritten = true
                }
                idx += 1
              }
              if (depthAdded) out.decrementDepth()
            }

            override def encodeAsField(fieldName: String, x: A, out: ToonWriter): Unit = {
              out.writeKeyOnly(fieldName)
              out.incrementDepth()
              encodeValue(x, out)
              out.decrementDepth()
            }

            override def isRecordCodec: Boolean = true

            override def hasOnlyPrimitiveFields: Boolean = {
              var idx = 0
              while (idx < fieldInfos.length) {
                if (!fieldInfos(idx).isPrimitiveCodec) return false
                idx += 1
              }
              true
            }

            override def getFieldNames: Array[String] = {
              val names = new Array[String](fieldInfos.length)
              var idx   = 0
              while (idx < fieldInfos.length) {
                names(idx) = fieldInfos(idx).name
                idx += 1
              }
              names
            }

            override def encodeTabularRow(x: A, out: ToonWriter, delimiter: Delimiter): Unit = {
              out.ensureIndent()
              out.enterInlineContext()
              val fieldLen = fieldInfos.length
              if (fieldLen > 0 && usedRegisters == 0) usedRegisters = fieldInfos(fieldLen - 1).usedRegisters
              val regs = Registers(usedRegisters)
              deconstructor.deconstruct(regs, 0, x)
              var idx   = 0
              var first = true
              while (idx < fieldLen) {
                val fieldInfo = fieldInfos(idx)
                if (fieldInfo.nonTransient) {
                  if (!first) out.writeDelimiter(delimiter)
                  first = false
                  fieldInfo.writeTabularValue(out, regs, 0, delimiter)
                }
                idx += 1
              }
              out.exitInlineContext()
            }

            override def decodeTabularRow(in: ToonReader, values: Array[String], fieldNames: Array[String]): A = {
              val fieldLen = fieldInfos.length
              if (fieldLen > 0 && usedRegisters == 0) usedRegisters = fieldInfos(fieldLen - 1).usedRegisters
              val regs    = Registers(usedRegisters)
              val missing = new Array[Boolean](fieldLen)
              java.util.Arrays.fill(missing, true)
              var valueIdx = 0
              while (valueIdx < fieldNames.length && valueIdx < values.length) {
                val fieldName = fieldNames(valueIdx)
                val fieldInfo = fieldIndex.get(fieldName)
                if (fieldInfo ne null) {
                  missing(fieldInfo.idx) = false
                  val rawValue = values(valueIdx).trim
                  val value    =
                    if (rawValue.startsWith("\"") && rawValue.endsWith("\"") && rawValue.length >= 2)
                      unescapeQuoted(rawValue)
                    else rawValue
                  try fieldInfo.readTabularValue(value, regs, 0)
                  catch {
                    case error if NonFatal(error) => in.decodeError(fieldInfo.span, error)
                  }
                }
                valueIdx += 1
              }
              var idx = 0
              while (idx < missing.length) {
                if (missing(idx)) {
                  val fieldInfo = fieldInfos(idx)
                  if (fieldInfo.hasDefault) fieldInfo.setDefaultValue(regs, 0)
                  else if (fieldInfo.isOptional) fieldInfo.setOptionalNone(regs, 0)
                  else if (fieldInfo.isCollection) fieldInfo.setEmptyCollection(regs, 0)
                  else in.decodeError(s"Missing required field in tabular row: ${fieldInfo.name}")
                }
                idx += 1
              }
              constructor.construct(regs, 0)
            }
          }
        }
    } else binding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      option(typeId, cases) match {
        case Some(innerReflect) =>
          D.instance(innerReflect.metadata).map { codec =>
            new ToonBinaryCodec[Option[Any]]() {
              private[this] val innerCodec = codec.asInstanceOf[ToonBinaryCodec[Any]]

              override def decodeValue(in: ToonReader, default: Option[Any]): Option[Any] = {
                in.skipBlankLines()
                val content = in.peekTrimmedContent
                if (content == "null" || content.isEmpty) {
                  if (content == "null") in.advanceLine()
                  None
                } else {
                  try Some(innerCodec.decodeValue(in, innerCodec.nullValue))
                  catch {
                    case err if NonFatal(err) =>
                      throw new ToonBinaryCodecError(
                        new ::(DynamicOptic.Node.Case("Some"), new ::(DynamicOptic.Node.Field("value"), Nil)),
                        err.getMessage
                      )
                  }
                }
              }

              override def encodeValue(x: Option[Any], out: ToonWriter): Unit =
                if (x eq None) out.writeNull()
                else innerCodec.encodeValue(x.get, out)

              override def encodeAsField(fieldName: String, x: Option[Any], out: ToonWriter): Unit =
                if (x eq None) {
                  out.writeKey(fieldName)
                  out.writeNull()
                  out.newLine()
                } else innerCodec.encodeAsField(fieldName, x.get, out)

              override def nullValue: Option[Any] = None
            }
          }
        case None =>
          val discr = binding.asInstanceOf[Binding.Variant[A]].discriminator
          if (isEnumeration(cases)) Lazy {
            def getInfos(cases: IndexedSeq[Term[F, A, ?]]): Array[ToonEnumInfo] = {
              val len   = cases.length
              val infos = new Array[ToonEnumInfo](len)
              var idx   = 0
              while (idx < len) {
                val case_       = cases(idx)
                val caseReflect = case_.value
                infos(idx) = if (caseReflect.isVariant) {
                  val discr = caseReflect.asVariant.get.variantBinding
                    .asInstanceOf[BindingInstance[ToonBinaryCodec, _, _]]
                    .binding
                    .asInstanceOf[Binding.Variant[_]]
                    .discriminator
                  new ToonEnumNodeInfo(
                    discr,
                    getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]].cases)
                  )
                } else {
                  val constructor = caseReflect.asRecord.get.recordBinding
                    .asInstanceOf[BindingInstance[ToonBinaryCodec, _, _]]
                    .binding
                    .asInstanceOf[Binding.Record[_]]
                    .constructor
                  var name: String = null
                  case_.modifiers.foreach {
                    case m: Modifier.rename => if (name eq null) name = m.name
                    case _                  =>
                  }
                  if (name eq null) name = caseNameMapper(case_.name)
                  new ToonEnumLeafInfo(name, constructor)
                }
                idx += 1
              }
              infos
            }

            val caseInfos = getInfos(cases)
            val caseMap   = new java.util.HashMap[String, ToonEnumLeafInfo]()
            caseInfos.foreach {
              case leaf: ToonEnumLeafInfo => caseMap.put(leaf.name, leaf)
              case _                      =>
            }

            new ToonBinaryCodec[A]() {
              private[this] val root = new ToonEnumNodeInfo(discr, caseInfos)
              private[this] val map  = caseMap

              def decodeValue(in: ToonReader, default: A): A = {
                in.skipBlankLines()
                val value = in.readString()
                val leaf  = map.get(value)
                if (leaf ne null) leaf.constructor.construct(null, 0).asInstanceOf[A]
                else in.decodeError(s"Unknown enum value: $value")
              }

              def encodeValue(x: A, out: ToonWriter): Unit = out.writeString(root.discriminate(x))
            }
          }
          else
            Lazy {
              discriminatorKind match {
                case DiscriminatorKind.Field(fieldName) if hasOnlyRecordAndVariantCases(cases) =>
                  val caseMap = new java.util.HashMap[String, ToonCaseLeafInfo]()

                  def getInfos(cases: IndexedSeq[Term[F, A, ?]]): Array[ToonCaseInfo] = {
                    val len   = cases.length
                    val infos = new Array[ToonCaseInfo](len)
                    var idx   = 0
                    while (idx < len) {
                      val case_       = cases(idx)
                      val caseReflect = case_.value
                      infos(idx) = if (caseReflect.isVariant) {
                        val caseVariant = caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]
                        new ToonCaseNodeInfo(discriminator(caseReflect), getInfos(caseVariant.cases))
                      } else {
                        var name: String = null
                        case_.modifiers.foreach {
                          case m: Modifier.rename => if (name eq null) name = m.name
                          case _: Modifier.alias  =>
                          case _                  =>
                        }
                        if (name eq null) name = caseNameMapper(case_.name)
                        discriminatorFields.set(
                          new ToonDiscriminatorFieldInfo(fieldName, name) :: discriminatorFields.get
                        )
                        val codec = D.instance(caseReflect.metadata).force
                        discriminatorFields.set(discriminatorFields.get.tail)
                        val caseLeafInfo = new ToonCaseLeafInfo(name, codec)
                        caseMap.put(name, caseLeafInfo)
                        case_.modifiers.foreach {
                          case m: Modifier.alias => caseMap.put(m.name, caseLeafInfo)
                          case _                 =>
                        }
                        caseLeafInfo
                      }
                      idx += 1
                    }
                    infos
                  }

                  new ToonBinaryCodec[A]() {
                    private[this] val root                   = new ToonCaseNodeInfo(discr, getInfos(cases))
                    private[this] val map                    = caseMap
                    private[this] val discriminatorFieldName = fieldName

                    def decodeValue(in: ToonReader, default: A): A = {
                      in.skipBlankLines()
                      if (!in.hasMoreContent || in.peekTrimmedContent.isEmpty) {
                        in.advanceLine()
                        in.skipBlankLines()
                      }
                      val startDepth                 = in.getDepth
                      var discriminatorValue: String = null
                      val savedLines                 = new java.util.ArrayList[String]()
                      while (in.hasMoreLines) {
                        in.skipBlankLines()
                        if (!in.hasMoreLines) {
                          if (discriminatorValue == null) {
                            in.decodeError(s"Missing discriminator field: $discriminatorFieldName")
                          }
                        } else {
                          val currentDepth = in.getDepth
                          if (currentDepth < startDepth) {
                            if (discriminatorValue == null) {
                              in.decodeError(s"Missing discriminator field: $discriminatorFieldName")
                            }
                            return decodeFromLines(in, savedLines, discriminatorValue)
                          } else if (currentDepth == startDepth && in.hasMoreContent) {
                            savedLines.add(in.getCurrentLine)
                            val lineContent = in.peekTrimmedContent
                            val colonIdx    = lineContent.indexOf(':')
                            if (colonIdx > 0) {
                              val key = lineContent.substring(0, colonIdx).trim
                              if (key == discriminatorFieldName) {
                                discriminatorValue = lineContent.substring(colonIdx + 1).trim
                                if (discriminatorValue.startsWith("\"")) {
                                  discriminatorValue = unescapeQuoted(discriminatorValue)
                                }
                              }
                            }
                            in.advanceLine()
                          } else {
                            savedLines.add(in.getCurrentLine)
                            in.advanceLine()
                          }
                        }
                      }
                      if (discriminatorValue == null)
                        in.decodeError(s"Missing discriminator field: $discriminatorFieldName")
                      decodeFromLines(in, savedLines, discriminatorValue)
                    }

                    private[this] def decodeFromLines(
                      in: ToonReader,
                      savedLines: java.util.ArrayList[String],
                      discriminatorValue: String
                    ): A = {
                      val caseInfo = map.get(discriminatorValue)
                      if (caseInfo eq null) in.decodeError(s"Unknown variant case: $discriminatorValue")
                      val linesArray = new Array[String](savedLines.size)
                      savedLines.toArray(linesArray)
                      val combinedContent = linesArray.mkString("\n")
                      val caseReader      = createReaderForValue(combinedContent)
                      val codec           = caseInfo.codec.asInstanceOf[ToonBinaryCodec[A]]
                      try codec.decodeValue(caseReader, codec.nullValue)
                      catch {
                        case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.Case(discriminatorValue), err)
                      }
                    }

                    def encodeValue(x: A, out: ToonWriter): Unit =
                      root.discriminate(x).codec.asInstanceOf[ToonBinaryCodec[A]].encodeValue(x, out)
                  }
                case DiscriminatorKind.None =>
                  def getInfos(cases: IndexedSeq[Term[F, A, ?]]): Array[ToonCaseInfo] = {
                    val len   = cases.length
                    val infos = new Array[ToonCaseInfo](len)
                    var idx   = 0
                    while (idx < len) {
                      val case_       = cases(idx)
                      val caseReflect = case_.value
                      infos(idx) = if (caseReflect.isVariant) {
                        val discr = caseReflect.asVariant.get.variantBinding
                          .asInstanceOf[BindingInstance[ToonBinaryCodec, _, _]]
                          .binding
                          .asInstanceOf[Binding.Variant[_]]
                          .discriminator
                        new ToonCaseNodeInfo(
                          discr,
                          getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]].cases)
                        )
                      } else {
                        val codec        = D.instance(caseReflect.metadata).force
                        var name: String = null
                        case_.modifiers.foreach {
                          case m: Modifier.rename => if (name eq null) name = m.name
                          case _                  =>
                        }
                        if (name eq null) name = caseNameMapper(case_.name)
                        new ToonCaseLeafInfo(name, codec)
                      }
                      idx += 1
                    }
                    infos
                  }

                  val codecs    = new java.util.ArrayList[ToonBinaryCodec[?]]()
                  val caseInfos = getInfos(cases)
                  caseInfos.foreach {
                    case leaf: ToonCaseLeafInfo => codecs.add(leaf.codec)
                    case _                      =>
                  }

                  new ToonBinaryCodec[A]() {
                    private[this] val root           = new ToonCaseNodeInfo(discr, caseInfos)
                    private[this] val caseLeafCodecs = codecs.toArray(new Array[ToonBinaryCodec[?]](codecs.size))

                    def decodeValue(in: ToonReader, default: A): A = {
                      var idx = 0
                      while (idx < caseLeafCodecs.length) {
                        in.setMark()
                        val codec = caseLeafCodecs(idx).asInstanceOf[ToonBinaryCodec[A]]
                        try {
                          val x = codec.decodeValue(in, codec.nullValue)
                          in.resetMark()
                          return x
                        } catch {
                          case error if NonFatal(error) => in.rollbackToMark()
                        }
                        idx += 1
                      }
                      in.decodeError("expected a variant value")
                    }

                    def encodeValue(x: A, out: ToonWriter): Unit =
                      root.discriminate(x).codec.asInstanceOf[ToonBinaryCodec[A]].encodeValue(x, out)
                  }
                case _ =>
                  def getInfos(cases: IndexedSeq[Term[F, A, ?]]): Array[ToonCaseInfo] = {
                    val len   = cases.length
                    val infos = new Array[ToonCaseInfo](len)
                    var idx   = 0
                    while (idx < len) {
                      val case_       = cases(idx)
                      val caseReflect = case_.value
                      infos(idx) = if (caseReflect.isVariant) {
                        val discr = caseReflect.asVariant.get.variantBinding
                          .asInstanceOf[BindingInstance[ToonBinaryCodec, _, _]]
                          .binding
                          .asInstanceOf[Binding.Variant[_]]
                          .discriminator
                        new ToonCaseNodeInfo(
                          discr,
                          getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]].cases)
                        )
                      } else {
                        val codec        = D.instance(caseReflect.metadata).force
                        var name: String = null
                        case_.modifiers.foreach {
                          case m: Modifier.rename => if (name eq null) name = m.name
                          case _                  =>
                        }
                        if (name eq null) name = caseNameMapper(case_.name)
                        new ToonCaseLeafInfo(name, codec)
                      }
                      idx += 1
                    }
                    infos
                  }

                  val caseInfos = getInfos(cases)
                  val caseMap   = new java.util.HashMap[String, ToonCaseLeafInfo]()
                  caseInfos.foreach {
                    case leaf: ToonCaseLeafInfo => caseMap.put(leaf.name, leaf)
                    case _                      =>
                  }

                  new ToonBinaryCodec[A]() {
                    private[this] val root = new ToonCaseNodeInfo(discr, caseInfos)
                    private[this] val map  = caseMap

                    def decodeValue(in: ToonReader, default: A): A = {
                      in.skipBlankLines()
                      val key      = in.readKey()
                      val caseInfo = map.get(key)
                      if (caseInfo ne null) {
                        val codec = caseInfo.codec.asInstanceOf[ToonBinaryCodec[A]]
                        try codec.decodeValue(in, codec.nullValue)
                        catch {
                          case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.Case(key), err)
                        }
                      } else in.decodeError(s"Unknown variant case: $key")
                    }

                    def encodeValue(x: A, out: ToonWriter): Unit = {
                      val caseInfo = root.discriminate(x)
                      out.writeKeyOnly(caseInfo.name)
                      out.incrementDepth()
                      caseInfo.codec.asInstanceOf[ToonBinaryCodec[A]].encodeValue(x, out)
                      out.decrementDepth()
                    }
                  }
              }
            }
      }
    } else binding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance
  }.asInstanceOf[Lazy[ToonBinaryCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { elemCodec =>
        val useInline = arrayFormat match {
          case ArrayFormat.Auto | ArrayFormat.Inline => elemCodec.isPrimitive
          case _                                     => false
        }
        val configuredDelimiter = delimiter
        val isRecordElement     = elemCodec.isRecordCodec
        val useTabular          = arrayFormat match {
          case ArrayFormat.Tabular => isRecordElement
          case _                   => false
        }
        new ToonBinaryCodec[Col[Elem]]() {
          private[this] val deconstructor    = seqBinding.deconstructor
          private[this] val constructor      = seqBinding.constructor
          private[this] val elementCodec     = elemCodec.asInstanceOf[ToonBinaryCodec[Elem]]
          private[this] val elemClassTag     = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]
          private[this] val useInlineFormat  = useInline
          private[this] val useTabularFormat = useTabular
          private[this] val inlineDelimiter  = configuredDelimiter

          def decodeValue(in: ToonReader, default: Col[Elem]): Col[Elem] = {
            in.skipBlankLines()
            val content = in.peekTrimmedContent
            if (content == "null") {
              in.advanceLine()
              return default
            }
            val header = in.parseArrayHeader(useInlineFormat)
            val length = header.length
            if (useInlineFormat) decodeInlineArray(in, in.readInlineArray(), length)
            else {
              val builder     = constructor.newBuilder[Elem](8)(elemClassTag)
              var actualCount = 0
              if (header.fields != null && header.fields.nonEmpty) {
                var idx = 0
                while (idx < length && in.hasMoreLines) {
                  in.skipBlankLinesInArray(idx == 0)
                  val values = in.readInlineArray()
                  if (values.nonEmpty) {
                    val firstValue = if (values(0).startsWith("\"")) unescapeQuoted(values(0)) else values(0)
                    try {
                      val elem = elementCodec.decodeValue(createReaderForValue(firstValue), elementCodec.nullValue)
                      constructor.add(builder, elem)
                      actualCount += 1
                    } catch {
                      case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.AtIndex(idx), err)
                    }
                  }
                  idx += 1
                }
              } else {
                var idx = 0
                while (idx < length && in.hasMoreLines) {
                  in.skipBlankLinesInArray(idx == 0)
                  if (in.isListItem) {
                    in.consumeListItemMarker()
                    try {
                      constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                      actualCount += 1
                    } catch {
                      case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.AtIndex(idx), err)
                    }
                  } else if (in.hasMoreContent) {
                    val values = in.readInlineArray()
                    values.foreach { v =>
                      val wasQuoted = v.startsWith("\"") && v.endsWith("\"")
                      val value     = if (wasQuoted) unescapeQuoted(v) else v
                      try {
                        constructor.add(
                          builder,
                          if (wasQuoted && (elementCodec eq stringCodec)) value.asInstanceOf[Elem]
                          else elementCodec.decodeValue(createReaderForValue(value), elementCodec.nullValue)
                        )
                        actualCount += 1
                      } catch {
                        case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.AtIndex(idx), err)
                      }
                      idx += 1
                    }
                    idx -= 1
                  }
                  idx += 1
                }
              }
              if (actualCount != length) {
                in.decodeError(s"Array count mismatch: expected $length items but got $actualCount")
              }
              constructor.result[Elem](builder)
            }
          }

          def encodeValue(x: Col[Elem], out: ToonWriter): Unit = {
            val size = deconstructor.size(x)
            if (useTabularFormat && size > 0) {
              val fieldNames = elementCodec.getFieldNames
              if (fieldNames != null && fieldNames.nonEmpty) {
                out.writeArrayHeader(null, size, fieldNames, inlineDelimiter)
                out.newLine()
                out.incrementDepth()
                val iter = deconstructor.deconstruct(x)
                while (iter.hasNext) {
                  elementCodec.encodeTabularRow(iter.next(), out, inlineDelimiter)
                  out.newLine()
                }
                out.decrementDepth()
              } else encodeAsListFormat(null, x, size, out)
            } else if (useInlineFormat && size > 0) {
              out.writeArrayHeaderInline(null, size, inlineDelimiter)
              out.setActiveDelimiter(inlineDelimiter)
              out.enterInlineContext()
              val iter  = deconstructor.deconstruct(x)
              var first = true
              while (iter.hasNext) {
                if (!first) out.writeDelimiter(inlineDelimiter)
                first = false
                elementCodec.encodeValue(iter.next(), out)
              }
              out.exitInlineContext()
              out.newLine()
            } else encodeAsListFormat(null, x, size, out)
          }

          private[this] def encodeAsListFormat(fieldName: String, x: Col[Elem], size: Int, out: ToonWriter): Unit = {
            out.writeArrayHeader(fieldName, size, null, inlineDelimiter)
            out.newLine()
            out.incrementDepth()
            val iter         = deconstructor.deconstruct(x)
            val isRecordElem = elementCodec.isRecordCodec
            while (iter.hasNext) {
              out.writeListItemMarker()
              out.enterListItemContext()
              elementCodec.encodeValue(iter.next(), out)
              out.exitListItemContext()
              if (!isRecordElem) out.newLine()
            }
            out.decrementDepth()
          }

          override def nullValue: Col[Elem] = constructor.empty[Elem](elemClassTag)

          override def encodeAsField(fieldName: String, x: Col[Elem], out: ToonWriter): Unit = {
            val size = deconstructor.size(x)
            if (size == 0) {
              out.writeArrayHeader(fieldName, 0, null, inlineDelimiter)
              out.newLine()
            } else {
              val shouldUseTabular = useTabularFormat ||
                (out.isInListItemContext && isRecordElement && elementCodec.hasOnlyPrimitiveFields)
              if (shouldUseTabular) {
                val fieldNames = elementCodec.getFieldNames
                if (fieldNames != null && fieldNames.nonEmpty) {
                  out.writeArrayHeader(fieldName, size, fieldNames, inlineDelimiter)
                  out.newLine()
                  out.incrementDepth()
                  if (out.isInListItemContext) out.incrementDepth()
                  val iter = deconstructor.deconstruct(x)
                  while (iter.hasNext) {
                    elementCodec.encodeTabularRow(iter.next(), out, inlineDelimiter)
                    out.newLine()
                  }
                  if (out.isInListItemContext) out.decrementDepth()
                  out.decrementDepth()
                } else encodeAsListFormat(fieldName, x, size, out)
              } else if (useInlineFormat) {
                out.writeArrayHeaderInline(fieldName, size, inlineDelimiter)
                out.setActiveDelimiter(inlineDelimiter)
                out.enterInlineContext()
                val iter  = deconstructor.deconstruct(x)
                var first = true
                while (iter.hasNext) {
                  if (!first) out.writeDelimiter(inlineDelimiter)
                  first = false
                  elementCodec.encodeValue(iter.next(), out)
                }
                out.exitInlineContext()
                out.newLine()
              } else encodeAsListFormat(fieldName, x, size, out)
            }
          }

          override def decodeInlineArray(in: ToonReader, values: Array[String], expectedLength: Int): Col[Elem] = {
            if (values.length != expectedLength) {
              throw new ToonBinaryCodecError(
                Nil,
                s"Array count mismatch: expected $expectedLength items but got ${values.length}"
              )
            }
            val builder = constructor.newBuilder[Elem](expectedLength)(elemClassTag)
            var idx     = 0
            while (idx < values.length) {
              val v         = values(idx)
              val wasQuoted = v.startsWith("\"") && v.endsWith("\"")
              val value     = if (wasQuoted) unescapeQuoted(v) else v
              try {
                constructor.add(
                  builder,
                  if (wasQuoted && (elementCodec eq stringCodec)) value.asInstanceOf[Elem]
                  else elementCodec.decodeValue(createReaderForValue(value), elementCodec.nullValue)
                )
              } catch {
                case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.AtIndex(idx), err)
              }
              idx += 1
            }
            constructor.result[Elem](builder)
          }

          override def decodeListArray(in: ToonReader, expectedLength: Int): Col[Elem] = {
            val builder     = constructor.newBuilder[Elem](expectedLength)(elemClassTag)
            var actualCount = 0
            var idx         = 0
            while (idx < expectedLength && in.hasMoreLines) {
              in.skipBlankLinesInArray(idx == 0)
              if (in.isListItem) {
                in.consumeListItemMarker()
                try constructor.add(builder, elementCodec.decodeValue(in, elementCodec.nullValue))
                catch {
                  case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.AtIndex(idx), err)
                }
                actualCount += 1
              } else if (in.hasMoreContent) {
                val values = in.readInlineArray()
                values.foreach { v =>
                  val wasQuoted = v.startsWith("\"") && v.endsWith("\"")
                  val value     = if (wasQuoted) unescapeQuoted(v) else v
                  try {
                    constructor.add(
                      builder,
                      if (wasQuoted && (elementCodec eq stringCodec)) value.asInstanceOf[Elem]
                      else elementCodec.decodeValue(createReaderForValue(value), elementCodec.nullValue)
                    )
                  } catch {
                    case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.AtIndex(idx), err)
                  }
                  actualCount += 1
                  idx += 1
                }
                idx -= 1
              }
              idx += 1
            }
            if (actualCount != expectedLength) {
              in.decodeError(s"Array count mismatch: expected $expectedLength items but got $actualCount")
            }
            constructor.result[Elem](builder)
          }

          override def decodeTabularArray(
            in: ToonReader,
            fieldNames: Array[String],
            expectedLength: Int,
            delimiter: Delimiter
          ): Col[Elem] = {
            val builder = constructor.newBuilder[Elem](expectedLength)(elemClassTag)
            in.setActiveDelimiter(delimiter)
            val startDepth = in.getDepth
            var idx        = 0
            while (idx < expectedLength && in.hasMoreLines) {
              in.skipBlankLinesInArray(idx == 0)
              if (in.hasMoreLines && in.getDepth >= startDepth) {
                val values = in.readInlineArray()
                try constructor.add(builder, elementCodec.decodeTabularRow(in, values, fieldNames))
                catch {
                  case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.AtIndex(idx), err)
                }
                idx += 1
              }
            }
            if (idx != expectedLength) {
              in.decodeError(s"Array count mismatch: expected $expectedLength rows but got $idx")
            }
            constructor.result[Elem](builder)
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, C[A]]].instance
  }.asInstanceOf[Lazy[ToonBinaryCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (keyCodec, valueCodec) =>
        new ToonBinaryCodec[Map[Key, Value]]() {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val kCodec        = keyCodec.asInstanceOf[ToonBinaryCodec[Key]]
          private[this] val vCodec        = valueCodec.asInstanceOf[ToonBinaryCodec[Value]]
          private[this] val keyReflect    = key.asInstanceOf[Reflect.Bound[Key]]

          def decodeValue(in: ToonReader, default: Map[Key, Value]): Map[Key, Value] = {
            val startDepth =
              if (in.isFirstLine) in.getDepth
              else in.getDepth + 1
            in.skipBlankLines()
            if (!in.hasMoreContent || in.peekTrimmedContent.isEmpty) {
              in.advanceLine()
              in.skipBlankLines()
            }
            val builder = constructor.newObjectBuilder[Key, Value](8)
            var idx     = 0
            while (in.hasMoreLines) {
              in.skipBlankLines()
              if (!in.hasMoreLines || in.getDepth < startDepth) {
                return constructor.resultObject[Key, Value](builder)
              }
              val keyReader = ToonReader(ReaderConfig)
              keyReader.reset(in.readKey())
              val k =
                try kCodec.decodeValue(keyReader, kCodec.nullValue)
                catch {
                  case err if NonFatal(err) => in.decodeError(new DynamicOptic.Node.AtIndex(idx), err)
                }
              val v =
                try vCodec.decodeValue(in, vCodec.nullValue)
                catch {
                  case err if NonFatal(err) =>
                    in.decodeError(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), err)
                }
              constructor.addObject(builder, k, v)
              idx += 1
            }
            constructor.resultObject[Key, Value](builder)
          }

          def encodeValue(x: Map[Key, Value], out: ToonWriter): Unit = {
            val iter = deconstructor.deconstruct(x)
            while (iter.hasNext) {
              val kv        = iter.next()
              val key       = deconstructor.getKey(kv)
              val value     = deconstructor.getValue(kv)
              val keyWriter = ToonWriter.fresh(WriterConfig)
              kCodec.encodeValue(key, keyWriter)
              val encodedKey = new String(keyWriter.toByteArray, java.nio.charset.StandardCharsets.UTF_8)
              vCodec.encodeAsField(encodedKey, value, out)
            }
          }

          override def encodeAsField(fieldName: String, x: Map[Key, Value], out: ToonWriter): Unit = {
            out.writeKeyOnly(fieldName)
            out.incrementDepth()
            encodeValue(x, out)
            out.decrementDepth()
          }

          override def nullValue: Map[Key, Value] = constructor.emptyObject[Key, Value]
        }
      }
    } else binding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, M[K, V]]].instance
  }.asInstanceOf[Lazy[ToonBinaryCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[DynamicValue]] =
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, DynamicValue]].instance

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonBinaryCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
      D.instance(wrapped.metadata).map { codec =>
        new ToonBinaryCodec[A](PrimitiveType.fromTypeId(typeId).fold(ToonBinaryCodec.objectType) {
          case _: PrimitiveType.Boolean   => ToonBinaryCodec.booleanType
          case _: PrimitiveType.Byte      => ToonBinaryCodec.byteType
          case _: PrimitiveType.Char      => ToonBinaryCodec.charType
          case _: PrimitiveType.Short     => ToonBinaryCodec.shortType
          case _: PrimitiveType.Float     => ToonBinaryCodec.floatType
          case _: PrimitiveType.Int       => ToonBinaryCodec.intType
          case _: PrimitiveType.Double    => ToonBinaryCodec.doubleType
          case _: PrimitiveType.Long      => ToonBinaryCodec.longType
          case _: PrimitiveType.Unit.type => ToonBinaryCodec.unitType
          case _                          => ToonBinaryCodec.objectType
        }) {
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val wrappedCodec = codec

          override def decodeValue(in: ToonReader, default: A): A =
            try {
              wrap(
                wrappedCodec.decodeValue(
                  in, {
                    if (default == null) null
                    else unwrap(default)
                  }.asInstanceOf[B]
                )
              )
            } catch {
              case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeValue(x: A, out: ToonWriter): Unit = wrappedCodec.encodeValue(unwrap(x), out)

          override def decodeKey(in: ToonReader): A =
            try wrap(wrappedCodec.decodeKey(in))
            catch {
              case err if NonFatal(err) => in.decodeError(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeKey(x: A, out: ToonWriter): Unit = wrappedCodec.encodeKey(unwrap(x), out)
        }
      }
    } else binding.asInstanceOf[BindingInstance[ToonBinaryCodec, ?, A]].instance

  override def instanceOverrides: IndexedSeq[InstanceOverride] = {
    recursiveRecordCache.remove()
    super.instanceOverrides
  }

  type Elem
  type Key
  type Value
  type Wrapped
  type Col[_]
  type Map[_, _]
  type TC[_]

  private[this] val recursiveRecordCache: ThreadLocal[util.HashMap[TypeId[?], Array[ToonFieldInfo]]] =
    new ThreadLocal[java.util.HashMap[TypeId[?], Array[ToonFieldInfo]]] {
      override def initialValue: java.util.HashMap[TypeId[?], Array[ToonFieldInfo]] = new java.util.HashMap
    }

  private[this] val discriminatorFields: ThreadLocal[List[ToonDiscriminatorFieldInfo]] =
    new ThreadLocal[List[ToonDiscriminatorFieldInfo]] {
      override def initialValue: List[ToonDiscriminatorFieldInfo] = Nil
    }

  private[this] def isOptional[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    !requireOptionFields && reflect.isOption

  private[this] def isCollection[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    !requireCollectionFields && reflect.isCollection

  private[this] def defaultValueConstructor[F[_, _], A](fieldReflect: Reflect[F, A]): () => ? =
    if (requireDefaultValueFields) null
    else fieldReflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue.map(v => () => v).orNull

  private[this] def isEnumeration[F[_, _], A](cases: IndexedSeq[Term[F, A, ?]]): Boolean =
    enumValuesAsStrings && cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.asRecord.exists(_.fields.isEmpty) ||
      caseReflect.isVariant && caseReflect.asVariant.map(_.cases).forall(isEnumeration)
    }

  private[this] def hasOnlyRecordAndVariantCases[F[_, _], A](cases: IndexedSeq[Term[F, A, ?]]): Boolean =
    cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.isRecord ||
      caseReflect.isVariant && caseReflect.asVariant.map(_.cases).forall(hasOnlyRecordAndVariantCases)
    }

  private[this] def discriminator[F[_, _], A](caseReflect: Reflect[F, A]): Discriminator[A] =
    caseReflect.asVariant.get.variantBinding
      .asInstanceOf[BindingInstance[ToonBinaryCodec, _, A]]
      .binding
      .asInstanceOf[Binding.Variant[A]]
      .discriminator

  private[this] def option[F[_, _], A](typeId: TypeId[A], cases: IndexedSeq[Term[F, A, ?]]): Option[Reflect[F, ?]] =
    if (
      typeId.owner == Owner.fromPackagePath("scala") && typeId.name == "Option" &&
      cases.length == 2 && cases(1).name == "Some"
    ) cases(1).value.asRecord.map(_.fields(0).value)
    else None
}
