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

package zio.blocks.schema.toon

import zio.blocks.docs.Doc
import zio.blocks.schema.toon.ToonCodec._
import zio.blocks.schema.toon.ToonReader._
import zio.blocks.schema.binding.{Binding, Constructor, Discriminator, HasBinding, RegisterOffset, Registers}
import zio.blocks.schema._
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.TypeId
import java.util
import scala.annotation.{switch, tailrec}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object ToonCodecDeriver
    extends ToonCodecDeriver(
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

class ToonCodecDeriver private (
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
) extends Deriver[ToonCodec] {

  def withFieldNameMapper(fieldNameMapper: NameMapper): ToonCodecDeriver =
    copy(fieldNameMapper = fieldNameMapper)

  def withCaseNameMapper(caseNameMapper: NameMapper): ToonCodecDeriver =
    copy(caseNameMapper = caseNameMapper)

  def withDiscriminatorKind(discriminatorKind: DiscriminatorKind): ToonCodecDeriver =
    copy(discriminatorKind = discriminatorKind)

  def withArrayFormat(arrayFormat: ArrayFormat): ToonCodecDeriver =
    copy(arrayFormat = arrayFormat)

  def withDelimiter(delimiter: Delimiter): ToonCodecDeriver =
    copy(delimiter = delimiter)

  def withRejectExtraFields(rejectExtraFields: Boolean): ToonCodecDeriver =
    copy(rejectExtraFields = rejectExtraFields)

  def withEnumValuesAsStrings(enumValuesAsStrings: Boolean): ToonCodecDeriver =
    copy(enumValuesAsStrings = enumValuesAsStrings)

  def withTransientNone(transientNone: Boolean): ToonCodecDeriver =
    copy(transientNone = transientNone)

  def withRequireOptionFields(requireOptionFields: Boolean): ToonCodecDeriver =
    copy(requireOptionFields = requireOptionFields)

  def withTransientEmptyCollection(transientEmptyCollection: Boolean): ToonCodecDeriver =
    copy(transientEmptyCollection = transientEmptyCollection)

  def withRequireCollectionFields(requireCollectionFields: Boolean): ToonCodecDeriver =
    copy(requireCollectionFields = requireCollectionFields)

  def withTransientDefaultValue(transientDefaultValue: Boolean): ToonCodecDeriver =
    copy(transientDefaultValue = transientDefaultValue)

  def withRequireDefaultValueFields(requireDefaultValueFields: Boolean): ToonCodecDeriver =
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
  ) = new ToonCodecDeriver(
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
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[ToonCodec[A]] = {
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
    } else binding.asInstanceOf[BindingInstance[ToonCodec, ?, A]].instance
  }.asInstanceOf[Lazy[ToonCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val len           = fields.length
      if (len == 0) Lazy {
        new ToonCodec[A] {
          private[this] val constructor = recordBinding.constructor

          def decodeValue(in: ToonReader): A = {
            in.skipBlankLines()
            constructor.construct(null, 0)
          }

          def encodeValue(x: A, out: ToonWriter): Unit = ()
        }
      }
      else if (len == 1 && fields(0).value.isMap) {
        val mapField = fields(0)
        D.instance(mapField.value.metadata).map { mapCodec =>
          new ToonCodec[A] {
            private[this] val mCodec        = mapCodec.asInstanceOf[ToonCodec[AnyRef]]
            private[this] val deconstructor = recordBinding.deconstructor
            private[this] val constructor   = recordBinding.constructor
            private[this] val usedRegisters = constructor.usedRegisters

            def decodeValue(in: ToonReader): A = {
              in.skipBlankLines()
              if (!in.hasMoreContent || in.peekTrimmedContent.isEmpty) {
                in.advanceLine()
                in.skipBlankLines()
              }
              val mapValue = mCodec.decodeValue(in)
              val regs     = Registers(usedRegisters)
              regs.setObject(0, mapValue)
              constructor.construct(regs, 0)
            }

            def encodeValue(x: A, out: ToonWriter): Unit = {
              val regs = Registers(usedRegisters)
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
              val field                      = fields(idx)
              val fieldReflect               = field.value
              val emptyCollectionConstructor =
                (if (requireCollectionFields) null
                 else if (fieldReflect.isSequence) {
                   val seq                         = fieldReflect.asSequenceUnknown.get.sequence
                   implicit val ct: ClassTag[Elem] = seq.elemClassTag.asInstanceOf[ClassTag[Elem]]
                   val constructor                 =
                     (if (seq.seqBinding.isInstanceOf[Binding[?, ?]]) seq.seqBinding
                      else seq.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding)
                       .asInstanceOf[Binding.Seq[Col, Elem]]
                       .constructor
                   () => constructor.empty
                 } else if (fieldReflect.isMap) {
                   val map         = fieldReflect.asMapUnknown.get.map
                   val constructor =
                     (if (map.mapBinding.isInstanceOf[Binding[?, ?]]) map.mapBinding
                      else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].binding)
                       .asInstanceOf[Binding.Map[Map, Key, Value]]
                       .constructor
                   () => constructor.emptyObject
                 } else null).asInstanceOf[() => AnyRef]
              infos(idx) = new ToonFieldInfo(
                span = new DynamicOptic.Node.Field(field.name),
                defaultValue = getDefaultValue(fieldReflect),
                emptyCollectionConstructor = emptyCollectionConstructor,
                typeTag = Reflect.typeTag(fieldReflect),
                idx = idx,
                isOptional = !requireOptionFields && fieldReflect.isOption,
                isCollection = !requireCollectionFields && fieldReflect.isCollection
              )
              idx += 1
            }
            if (isRecursive) recursiveRecordCache.get.put(typeId, infos)
            discriminatorFields.set(null :: discriminatorFields.get)
          }
          val fieldMap = new java.util.HashMap[String, ToonFieldInfo]()
          var offset   = 0L
          var idx      = 0
          while (idx < len) {
            val field     = fields(idx)
            val fieldInfo = infos(idx)
            if (deriveCodecs) {
              val fieldReflect = field.value
              val codec        = D.instance(fieldReflect.metadata).force
              fieldInfo.setCodec(codec)
              fieldInfo.setOffset(offset)
              offset = RegisterOffset.add(Reflect.registerOffset(fieldReflect), offset)
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
          new ToonCodec[A] {
            private[this] val deconstructor       = recordBinding.deconstructor
            private[this] val constructor         = recordBinding.constructor
            private[this] val fieldInfos          = infos
            private[this] val fieldIndex          = fieldMap
            private[this] val usedRegisters       = constructor.usedRegisters
            private[this] val discriminatorField  = discriminatorFields.get.headOption.orNull
            private[this] val skipNone            = transientNone
            private[this] val skipEmptyCollection = transientEmptyCollection
            private[this] val skipDefaultValue    = transientDefaultValue
            private[this] val doReject            = rejectExtraFields

            def decodeValue(in: ToonReader): A = {
              in.skipBlankLines()
              val fieldLen = fieldInfos.length
              val regs     = Registers(usedRegisters)
              val missing  = new Array[Boolean](fieldLen)
              java.util.Arrays.fill(missing, true)
              if (!in.hasMoreContent || in.peekTrimmedContent.isEmpty) {
                in.advanceLine()
                in.skipBlankLines()
              }
              val startDepth = in.getDepth
              while (in.hasMoreLines) {
                in.skipBlankLines()
                if (!in.hasMoreLines) return setMissingAndConstruct(missing, regs)
                val currentDepth = in.getDepth
                if (currentDepth < startDepth) return setMissingAndConstruct(missing, regs)
                if (in.isListItem && currentDepth <= startDepth) return setMissingAndConstruct(missing, regs)
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
                      case err if NonFatal(err) => error(fieldInfo.span, err)
                    }
                  } else skipOrReject(in, key)
                }
              }
              setMissingAndConstruct(missing, regs)
            }

            private[this] def stripQuotes(s: String, from: Int, to: Int): String =
              if (to - from >= 2 && s.charAt(from) == '"' && s.charAt(to - 1) == '"') s.substring(from + 1, to - 1)
              else s.substring(from, to)

            private[this] def skipOrReject(in: ToonReader, key: String): Unit =
              if (doReject && ((discriminatorField eq null) || discriminatorField.name != key)) {
                error(s"Unexpected field: $key")
              } else in.advanceLine()

            private[this] def setMissingAndConstruct(missing: Array[Boolean], regs: Registers) = {
              var idx = 0
              while (idx < missing.length) {
                if (missing(idx)) fieldInfos(idx).setMissingValueOrError(regs)
                idx += 1
              }
              constructor.construct(regs, 0)
            }

            def encodeValue(x: A, out: ToonWriter): Unit = {
              val fieldLen = fieldInfos.length
              val regs     = Registers(usedRegisters)
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

            override val fieldNames: Array[String] = {
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
              val regs     = Registers(usedRegisters)
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
              val regs     = Registers(usedRegisters)
              val missing  = new Array[Boolean](fieldLen)
              java.util.Arrays.fill(missing, true)
              var valueIdx = 0
              while (valueIdx < fieldNames.length && valueIdx < values.length) {
                val fieldName = fieldNames(valueIdx)
                val fieldInfo = fieldIndex.get(fieldName)
                if (fieldInfo ne null) {
                  missing(fieldInfo.idx) = false
                  val rawValue = values(valueIdx).trim
                  val value    =
                    if (rawValue.startsWith("\"") && rawValue.endsWith("\"") && rawValue.length >= 2) {
                      unescapeQuoted(rawValue)
                    } else rawValue
                  try fieldInfo.readTabularValue(value, regs)
                  catch {
                    case err if NonFatal(err) => error(fieldInfo.span, err)
                  }
                }
                valueIdx += 1
              }
              var idx = 0
              while (idx < missing.length) {
                if (missing(idx)) {
                  val fieldInfo = fieldInfos(idx)
                  if (fieldInfo.hasDefault) fieldInfo.setDefaultValue(regs)
                  else if (fieldInfo.isOptional) fieldInfo.setOptionalNone(regs)
                  else if (fieldInfo.isCollection) fieldInfo.setEmptyCollection(regs)
                  else error(s"Missing required field in tabular row: ${fieldInfo.name}")
                }
                idx += 1
              }
              constructor.construct(regs, 0L)
            }
          }
        }
    } else binding.asInstanceOf[BindingInstance[ToonCodec, ?, A]].instance

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      if (typeId.isOption) {
        D.instance(cases(1).value.asRecord.get.fields(0).value.metadata).map { codec =>
          new ToonCodec[Option[Any]] {
            private[this] val innerCodec = codec.asInstanceOf[ToonCodec[Any]]

            override def decodeValue(in: ToonReader): Option[Any] = {
              in.skipBlankLines()
              val content = in.peekTrimmedContent
              if (content == "null" || content.isEmpty) {
                if (content == "null") in.advanceLine()
                None
              } else {
                try Some(innerCodec.decodeValue(in))
                catch {
                  case err if NonFatal(err) =>
                    throw new ToonCodecError(
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
          }
        }
      } else {
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
                new ToonEnumNodeInfo(
                  discriminator(caseReflect),
                  getInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]].cases)
                )
              } else {
                val constructor = caseReflect.asRecord.get.recordBinding
                  .asInstanceOf[BindingInstance[ToonCodec, _, _]]
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

          new ToonCodec[A] {
            private[this] val root = new ToonEnumNodeInfo(discr, caseInfos)
            private[this] val map  = caseMap

            def decodeValue(in: ToonReader): A = {
              in.skipBlankLines()
              val value = in.readString()
              val leaf  = map.get(value)
              if (leaf ne null) leaf.constructor.construct(null, 0).asInstanceOf[A]
              else error(s"Unknown enum value: $value")
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

                new ToonCodec[A] {
                  private[this] val root                   = new ToonCaseNodeInfo(discr, getInfos(cases))
                  private[this] val map                    = caseMap
                  private[this] val discriminatorFieldName = fieldName

                  def decodeValue(in: ToonReader): A = {
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
                        if (discriminatorValue == null) error(s"Missing discriminator field: $discriminatorFieldName")
                      } else {
                        val currentDepth = in.getDepth
                        if (currentDepth < startDepth) {
                          if (discriminatorValue == null) {
                            error(s"Missing discriminator field: $discriminatorFieldName")
                          }
                          return decodeFromLines(savedLines, discriminatorValue)
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
                    if (discriminatorValue == null) error(s"Missing discriminator field: $discriminatorFieldName")
                    decodeFromLines(savedLines, discriminatorValue)
                  }

                  private[this] def decodeFromLines(
                    savedLines: java.util.ArrayList[String],
                    discriminatorValue: String
                  ): A = {
                    val caseInfo = map.get(discriminatorValue)
                    if (caseInfo eq null) error(s"Unknown variant case: $discriminatorValue")
                    val linesArray = new Array[String](savedLines.size)
                    savedLines.toArray(linesArray)
                    val combinedContent = linesArray.mkString("\n")
                    val caseReader      = createReaderForValue(combinedContent)
                    val codec           = caseInfo.codec.asInstanceOf[ToonCodec[A]]
                    try codec.decodeValue(caseReader)
                    catch {
                      case err if NonFatal(err) => error(DynamicOptic.Node.Case(discriminatorValue), err)
                    }
                  }

                  def encodeValue(x: A, out: ToonWriter): Unit =
                    root.discriminate(x).codec.asInstanceOf[ToonCodec[A]].encodeValue(x, out)
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
                      new ToonCaseNodeInfo(
                        discriminator(caseReflect),
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

                val codecs    = new java.util.ArrayList[ToonCodec[?]]()
                val caseInfos = getInfos(cases)
                caseInfos.foreach {
                  case leaf: ToonCaseLeafInfo => codecs.add(leaf.codec)
                  case _                      =>
                }

                new ToonCodec[A] {
                  private[this] val root           = new ToonCaseNodeInfo(discr, caseInfos)
                  private[this] val caseLeafCodecs = codecs.toArray(new Array[ToonCodec[?]](codecs.size))

                  def decodeValue(in: ToonReader): A = {
                    var idx = 0
                    while (idx < caseLeafCodecs.length) {
                      in.setMark()
                      val codec = caseLeafCodecs(idx).asInstanceOf[ToonCodec[A]]
                      try {
                        val x = codec.decodeValue(in)
                        in.resetMark()
                        return x
                      } catch {
                        case error if NonFatal(error) => in.rollbackToMark()
                      }
                      idx += 1
                    }
                    error("expected a variant value")
                  }

                  def encodeValue(x: A, out: ToonWriter): Unit =
                    root.discriminate(x).codec.asInstanceOf[ToonCodec[A]].encodeValue(x, out)
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
                      new ToonCaseNodeInfo(
                        discriminator(caseReflect),
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

                new ToonCodec[A] {
                  private[this] val root = new ToonCaseNodeInfo(discr, caseInfos)
                  private[this] val map  = caseMap

                  def decodeValue(in: ToonReader): A = {
                    in.skipBlankLines()
                    val key      = in.readKey()
                    val caseInfo = map.get(key)
                    if (caseInfo ne null) {
                      val codec = caseInfo.codec.asInstanceOf[ToonCodec[A]]
                      try codec.decodeValue(in)
                      catch {
                        case err if NonFatal(err) => error(DynamicOptic.Node.Case(key), err)
                      }
                    } else error(s"Unknown variant case: $key")
                  }

                  def encodeValue(x: A, out: ToonWriter): Unit = {
                    val caseInfo = root.discriminate(x)
                    out.writeKeyOnly(caseInfo.name)
                    out.incrementDepth()
                    caseInfo.codec.asInstanceOf[ToonCodec[A]].encodeValue(x, out)
                    out.decrementDepth()
                  }
                }
            }
          }
      }
    } else binding.asInstanceOf[BindingInstance[ToonCodec, ?, A]].instance
  }.asInstanceOf[Lazy[ToonCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { elemCodec =>
        val useInline = arrayFormat match {
          case ArrayFormat.Auto | ArrayFormat.Inline => element.isPrimitive
          case _                                     => false
        }
        val configuredDelimiter = delimiter
        val isRecordElement     = element.isRecord
        val useTabular          = arrayFormat match {
          case ArrayFormat.Tabular => isRecordElement
          case _                   => false
        }
        new ToonCodec[Col[Elem]] {
          private[this] val deconstructor          = seqBinding.deconstructor
          private[this] val constructor            = seqBinding.constructor
          private[this] val elementCodec           = elemCodec.asInstanceOf[ToonCodec[Elem]]
          private[this] val elemClassTag           = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]
          private[this] val inlineDelimiter        = configuredDelimiter
          private[this] val useInlineFormat        = useInline
          private[this] val useTabularFormat       = useTabular
          private[this] val isRecordElement        = element.isRecord
          private[this] val hasOnlyPrimitiveFields =
            isRecordElement && element.asRecord.get.fields.forall(_.value.isPrimitive)

          def decodeValue(in: ToonReader): Col[Elem] = {
            in.skipBlankLines()
            val content = in.peekTrimmedContent
            if (content == "null") {
              in.advanceLine()
              return constructor.empty(elemClassTag)
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
                      val elem = elementCodec.decodeValue(createReaderForValue(firstValue))
                      constructor.add(builder, elem)
                      actualCount += 1
                    } catch {
                      case err if NonFatal(err) => error(DynamicOptic.Node.AtIndex(idx), err)
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
                      constructor.add(builder, elementCodec.decodeValue(in))
                      actualCount += 1
                    } catch {
                      case err if NonFatal(err) => error(DynamicOptic.Node.AtIndex(idx), err)
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
                          else elementCodec.decodeValue(createReaderForValue(value))
                        )
                        actualCount += 1
                      } catch {
                        case err if NonFatal(err) => error(DynamicOptic.Node.AtIndex(idx), err)
                      }
                      idx += 1
                    }
                    idx -= 1
                  }
                  idx += 1
                }
              }
              if (actualCount != length) {
                error(s"Array count mismatch: expected $length items but got $actualCount")
              }
              constructor.result[Elem](builder)
            }
          }

          def encodeValue(x: Col[Elem], out: ToonWriter): Unit = {
            val size = deconstructor.size(x)
            if (useTabularFormat && size > 0) {
              val fieldNames = elementCodec.fieldNames
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
            val iter = deconstructor.deconstruct(x)
            while (iter.hasNext) {
              out.writeListItemMarker()
              out.enterListItemContext()
              elementCodec.encodeValue(iter.next(), out)
              out.exitListItemContext()
              if (!isRecordElement) out.newLine()
            }
            out.decrementDepth()
          }

          override def encodeAsField(fieldName: String, x: Col[Elem], out: ToonWriter): Unit = {
            val size = deconstructor.size(x)
            if (size == 0) {
              out.writeArrayHeader(fieldName, 0, null, inlineDelimiter)
              out.newLine()
            } else {
              val shouldUseTabular = useTabularFormat || (out.isInListItemContext && hasOnlyPrimitiveFields)
              if (shouldUseTabular) {
                val fieldNames = elementCodec.fieldNames
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
              error(s"Array count mismatch: expected $expectedLength items but got ${values.length}")
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
                  else elementCodec.decodeValue(createReaderForValue(value))
                )
              } catch {
                case err if NonFatal(err) => error(DynamicOptic.Node.AtIndex(idx), err)
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
                try constructor.add(builder, elementCodec.decodeValue(in))
                catch {
                  case err if NonFatal(err) => error(DynamicOptic.Node.AtIndex(idx), err)
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
                      else elementCodec.decodeValue(createReaderForValue(value))
                    )
                  } catch {
                    case err if NonFatal(err) => error(DynamicOptic.Node.AtIndex(idx), err)
                  }
                  actualCount += 1
                  idx += 1
                }
                idx -= 1
              }
              idx += 1
            }
            if (actualCount != expectedLength) {
              error(s"Array count mismatch: expected $expectedLength items but got $actualCount")
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
                  case err if NonFatal(err) => error(DynamicOptic.Node.AtIndex(idx), err)
                }
                idx += 1
              }
            }
            if (idx != expectedLength) {
              error(s"Array count mismatch: expected $expectedLength rows but got $idx")
            }
            constructor.result[Elem](builder)
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[ToonCodec, ?, C[A]]].instance
  }.asInstanceOf[Lazy[ToonCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (keyCodec, valueCodec) =>
        new ToonCodec[Map[Key, Value]] {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val kCodec        = keyCodec.asInstanceOf[ToonCodec[Key]]
          private[this] val vCodec        = valueCodec.asInstanceOf[ToonCodec[Value]]
          private[this] val keyReflect    = key.asInstanceOf[Reflect.Bound[Key]]

          def decodeValue(in: ToonReader): Map[Key, Value] = {
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
                try kCodec.decodeValue(keyReader)
                catch {
                  case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                }
              val v =
                try vCodec.decodeValue(in)
                catch {
                  case err if NonFatal(err) =>
                    error(new DynamicOptic.Node.AtMapKey(keyReflect.toDynamicValue(k)), err)
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
        }
      }
    } else binding.asInstanceOf[BindingInstance[ToonCodec, ?, M[K, V]]].instance
  }.asInstanceOf[Lazy[ToonCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonCodec[DynamicValue]] =
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[ToonCodec, ?, DynamicValue]].instance

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ToonCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
      D.instance(wrapped.metadata).map { codec =>
        new ToonCodec[A] {
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val wrappedCodec = codec

          override def decodeValue(in: ToonReader): A =
            try wrap(wrappedCodec.decodeValue(in))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeValue(x: A, out: ToonWriter): Unit = wrappedCodec.encodeValue(unwrap(x), out)

          override def decodeKey(in: ToonReader): A =
            try wrap(wrappedCodec.decodeKey(in))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeKey(x: A, out: ToonWriter): Unit = wrappedCodec.encodeKey(unwrap(x), out)
        }
      }
    } else binding.asInstanceOf[BindingInstance[ToonCodec, ?, A]].instance

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

  private[this] def getDefaultValue[F[_, _], A](fieldReflect: Reflect[F, A]): Option[?] =
    if (requireDefaultValueFields) None
    else fieldReflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue

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
      .asInstanceOf[BindingInstance[ToonCodec, _, A]]
      .binding
      .asInstanceOf[Binding.Variant[A]]
      .discriminator
}

private sealed trait ToonEnumInfo

private final class ToonEnumLeafInfo(val name: String, val constructor: Constructor[?]) extends ToonEnumInfo

private final class ToonEnumNodeInfo(discr: Discriminator[?], children: Array[ToonEnumInfo]) extends ToonEnumInfo {
  @tailrec
  def discriminate(x: Any): String = children(discr.asInstanceOf[Discriminator[Any]].discriminate(x)) match {
    case leaf: ToonEnumLeafInfo => leaf.name
    case node: ToonEnumNodeInfo => node.discriminate(x)
  }
}

private sealed trait ToonCaseInfo

private final class ToonCaseLeafInfo(val name: String, val codec: ToonCodec[?]) extends ToonCaseInfo

private final class ToonCaseNodeInfo(discr: Discriminator[?], children: Array[ToonCaseInfo]) extends ToonCaseInfo {
  @tailrec
  def discriminate(x: Any): ToonCaseLeafInfo = children(discr.asInstanceOf[Discriminator[Any]].discriminate(x)) match {
    case leaf: ToonCaseLeafInfo => leaf
    case node: ToonCaseNodeInfo => node.discriminate(x)
  }
}

private final class ToonDiscriminatorFieldInfo(val name: String, val value: String)

private final class ToonFieldInfo(
  val span: DynamicOptic.Node.Field,
  defaultValue: Option[?],
  emptyCollectionConstructor: () => AnyRef,
  val typeTag: Int,
  val idx: Int,
  val isOptional: Boolean,
  val isCollection: Boolean
) {
  private[this] var codec: ToonCodec[?]                   = null
  private[this] var _name: String                         = null
  private[this] var offset: RegisterOffset.RegisterOffset = 0
  var nonTransient: Boolean                               = true

  def name: String = _name

  def setName(name: String): Unit = this._name = name

  def setCodec(codec: ToonCodec[?]): Unit = this.codec = codec

  def setOffset(offset: RegisterOffset.RegisterOffset): Unit = this.offset = offset

  @inline
  def hasDefault: Boolean = defaultValue ne None

  def readValue(in: ToonReader, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top
    (typeTag: @switch) match {
      case 0 => regs.setObject(off, codec.asInstanceOf[ToonCodec[AnyRef]].decodeValue(in))
      case 1 => regs.setInt(off, codec.asInstanceOf[ToonCodec[Int]].decodeValue(in))
      case 2 => regs.setLong(off, codec.asInstanceOf[ToonCodec[Long]].decodeValue(in))
      case 3 => regs.setFloat(off, codec.asInstanceOf[ToonCodec[Float]].decodeValue(in))
      case 4 => regs.setDouble(off, codec.asInstanceOf[ToonCodec[Double]].decodeValue(in))
      case 5 => regs.setBoolean(off, codec.asInstanceOf[ToonCodec[Boolean]].decodeValue(in))
      case 6 => regs.setByte(off, codec.asInstanceOf[ToonCodec[Byte]].decodeValue(in))
      case 7 => regs.setChar(off, codec.asInstanceOf[ToonCodec[Char]].decodeValue(in))
      case 8 => regs.setShort(off, codec.asInstanceOf[ToonCodec[Short]].decodeValue(in))
      case _ => codec.asInstanceOf[ToonCodec[Unit]].decodeValue(in)
    }
  }

  def readArrayFieldValue(in: ToonReader, regs: Registers, top: RegisterOffset.RegisterOffset, rawKey: String): Unit = {
    val off          = this.offset + top
    val bracketStart = if (rawKey.startsWith("\"")) {
      val closeQuoteIdx = rawKey.indexOf('"', 1)
      if (closeQuoteIdx > 0) rawKey.indexOf('[', closeQuoteIdx + 1)
      else rawKey.indexOf('[')
    } else rawKey.indexOf('[')
    val bracketEnd = rawKey.indexOf(']', bracketStart)
    val lengthStr  =
      if (bracketStart >= 0 && bracketEnd > bracketStart) rawKey.substring(bracketStart + 1, bracketEnd)
      else "0"
    val delimChar = if (lengthStr.nonEmpty) {
      val lastChar = lengthStr.charAt(lengthStr.length - 1)
      if (lastChar == '\t') Delimiter.Tab
      else if (lastChar == '|') Delimiter.Pipe
      else Delimiter.Comma
    } else Delimiter.Comma
    val length =
      try {
        (if (delimChar != Delimiter.Comma) lengthStr.dropRight(1).trim
         else lengthStr).toInt
      } catch {
        case _: NumberFormatException => 0
      }
    val braceStart = rawKey.indexOf('{', bracketEnd)
    val braceEnd   = rawKey.indexOf('}', braceStart)
    if (braceStart > 0 && braceEnd > braceStart) {
      val fieldNamesStr = rawKey.substring(braceStart + 1, braceEnd)
      val fieldNames    = splitFieldNames(fieldNamesStr, delimChar).map { f =>
        val trimmed = f.trim
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
          trimmed.substring(1, trimmed.length - 1)
        } else trimmed
      }
      in.advanceLine()
      in.skipBlankLines()
      regs.setObject(
        off,
        codec.asInstanceOf[ToonCodec[AnyRef]].decodeTabularArray(in, fieldNames, length, delimChar)
      )
    } else {
      val remaining = in.peekTrimmedContent
      if (remaining.isEmpty) {
        in.advanceLine()
        in.skipBlankLines()
        regs.setObject(off, codec.asInstanceOf[ToonCodec[AnyRef]].decodeListArray(in, length))
      } else {
        in.setActiveDelimiter(delimChar)
        val values = in.readInlineArray()
        regs.setObject(off, codec.asInstanceOf[ToonCodec[AnyRef]].decodeInlineArray(in, values, length))
      }
    }
  }

  def setMissingValueOrError(regs: Registers): Unit =
    if (defaultValue ne None) {
      val dv = defaultValue.get
      (typeTag: @switch) match {
        case 0 => regs.setObject(offset, dv.asInstanceOf[AnyRef])
        case 1 => regs.setInt(offset, dv.asInstanceOf[Int])
        case 2 => regs.setLong(offset, dv.asInstanceOf[Long])
        case 3 => regs.setFloat(offset, dv.asInstanceOf[Float])
        case 4 => regs.setDouble(offset, dv.asInstanceOf[Double])
        case 5 => regs.setBoolean(offset, dv.asInstanceOf[Boolean])
        case 6 => regs.setByte(offset, dv.asInstanceOf[Byte])
        case 7 => regs.setChar(offset, dv.asInstanceOf[Char])
        case 8 => regs.setShort(offset, dv.asInstanceOf[Short])
        case _ =>
      }
    } else if (isOptional) regs.setObject(offset, None)
    else if (isCollection) regs.setObject(offset, emptyCollectionConstructor())
    else throw new ToonCodecError(Nil, s"Missing required field: $name")

  def writeRequired(out: ToonWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top
    (typeTag: @switch) match {
      case 0 =>
        val value = regs.getObject(off)
        codec.asInstanceOf[ToonCodec[AnyRef]].encodeAsField(name, value, out)
      case 1 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Int]].encodeValue(regs.getInt(off), out)
        out.newLine()
      case 2 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Long]].encodeValue(regs.getLong(off), out)
        out.newLine()
      case 3 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Float]].encodeValue(regs.getFloat(off), out)
        out.newLine()
      case 4 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Double]].encodeValue(regs.getDouble(off), out)
        out.newLine()
      case 5 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Boolean]].encodeValue(regs.getBoolean(off), out)
        out.newLine()
      case 6 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Byte]].encodeValue(regs.getByte(off), out)
        out.newLine()
      case 7 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Char]].encodeValue(regs.getChar(off), out)
        out.newLine()
      case 8 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Short]].encodeValue(regs.getShort(off), out)
        out.newLine()
      case _ =>
        out.writeKey(name)
        codec.asInstanceOf[ToonCodec[Unit]].encodeValue((), out)
        out.newLine()
    }
  }

  def writeOptional(out: ToonWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off   = this.offset + top
    val value = regs.getObject(off).asInstanceOf[Option[?]]
    if (value.isDefined) codec.asInstanceOf[ToonCodec[Option[Any]]].encodeAsField(name, value, out)
  }

  def writeCollection(out: ToonWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off      = this.offset + top
    val value    = regs.getObject(off)
    val nonEmpty = value match {
      case s: Iterable[?] => s.nonEmpty
      case _              => false
    }
    if (nonEmpty) codec.asInstanceOf[ToonCodec[AnyRef]].encodeAsField(name, value, out)
  }

  def writeDefaultValue(out: ToonWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off          = this.offset + top
    val currentValue = (typeTag: @switch) match {
      case 0 => regs.getObject(off)
      case 1 => regs.getInt(off)
      case 2 => regs.getLong(off)
      case 3 => regs.getFloat(off)
      case 4 => regs.getDouble(off)
      case 5 => regs.getBoolean(off)
      case 6 => regs.getByte(off)
      case 7 => regs.getChar(off)
      case 8 => regs.getShort(off)
      case _ => ()
    }
    if (defaultValue.get != currentValue) writeRequired(out, regs, top)
  }

  def writeTabularValue(
    out: ToonWriter,
    regs: Registers,
    top: RegisterOffset.RegisterOffset,
    delimiter: Delimiter
  ): Unit = {
    val off = this.offset + top
    (typeTag: @switch) match {
      case 0 =>
        val value = regs.getObject(off)
        if (value == null || value == None) out.writeNull()
        else {
          value match {
            case s: String => out.writeString(s, delimiter)
            case _         => codec.asInstanceOf[ToonCodec[AnyRef]].encodeValue(value, out)
          }
        }
      case 1 => codec.asInstanceOf[ToonCodec[Int]].encodeValue(regs.getInt(off), out)
      case 2 => codec.asInstanceOf[ToonCodec[Long]].encodeValue(regs.getLong(off), out)
      case 3 => codec.asInstanceOf[ToonCodec[Float]].encodeValue(regs.getFloat(off), out)
      case 4 => codec.asInstanceOf[ToonCodec[Double]].encodeValue(regs.getDouble(off), out)
      case 5 => codec.asInstanceOf[ToonCodec[Boolean]].encodeValue(regs.getBoolean(off), out)
      case 6 => codec.asInstanceOf[ToonCodec[Byte]].encodeValue(regs.getByte(off), out)
      case 7 => codec.asInstanceOf[ToonCodec[Char]].encodeValue(regs.getChar(off), out)
      case 8 => codec.asInstanceOf[ToonCodec[Short]].encodeValue(regs.getShort(off), out)
      case _ => codec.asInstanceOf[ToonCodec[Unit]].encodeValue((), out)
    }
  }

  def readTabularValue(value: String, regs: Registers): Unit = {
    val reader = createReaderForValue(value)
    (typeTag: @switch) match {
      case 0 => regs.setObject(offset, codec.asInstanceOf[ToonCodec[AnyRef]].decodeValue(reader))
      case 1 => regs.setInt(offset, codec.asInstanceOf[ToonCodec[Int]].decodeValue(reader))
      case 2 => regs.setLong(offset, codec.asInstanceOf[ToonCodec[Long]].decodeValue(reader))
      case 3 => regs.setFloat(offset, codec.asInstanceOf[ToonCodec[Float]].decodeValue(reader))
      case 4 => regs.setDouble(offset, codec.asInstanceOf[ToonCodec[Double]].decodeValue(reader))
      case 5 => regs.setBoolean(offset, codec.asInstanceOf[ToonCodec[Boolean]].decodeValue(reader))
      case 6 => regs.setByte(offset, codec.asInstanceOf[ToonCodec[Byte]].decodeValue(reader))
      case 7 => regs.setChar(offset, codec.asInstanceOf[ToonCodec[Char]].decodeValue(reader))
      case 8 => regs.setShort(offset, codec.asInstanceOf[ToonCodec[Short]].decodeValue(reader))
      case _ => codec.asInstanceOf[ToonCodec[Unit]].decodeValue(reader)
    }
  }

  def setDefaultValue(regs: Registers): Unit = {
    val dv = defaultValue.get
    (typeTag: @switch) match {
      case 0 => regs.setObject(offset, dv.asInstanceOf[AnyRef])
      case 1 => regs.setInt(offset, dv.asInstanceOf[Int])
      case 2 => regs.setLong(offset, dv.asInstanceOf[Long])
      case 3 => regs.setFloat(offset, dv.asInstanceOf[Float])
      case 4 => regs.setDouble(offset, dv.asInstanceOf[Double])
      case 5 => regs.setBoolean(offset, dv.asInstanceOf[Boolean])
      case 6 => regs.setByte(offset, dv.asInstanceOf[Byte])
      case 7 => regs.setChar(offset, dv.asInstanceOf[Char])
      case 8 => regs.setShort(offset, dv.asInstanceOf[Short])
      case _ =>
    }
  }

  def setOptionalNone(regs: Registers): Unit = regs.setObject(offset, None)

  def setEmptyCollection(regs: Registers): Unit = regs.setObject(offset, emptyCollectionConstructor())

  private[this] def splitFieldNames(s: String, delim: Delimiter): Array[String] = {
    val result  = new scala.collection.mutable.ArrayBuffer[String]()
    var start   = 0
    var inQuote = false
    var i       = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"') inQuote = !inQuote
      else if (!inQuote && c == delim.char) {
        result.addOne(s.substring(start, i).trim)
        start = i + 1
      }
      i += 1
    }
    if (start <= s.length) result.addOne(s.substring(start).trim)
    result.toArray
  }
}
