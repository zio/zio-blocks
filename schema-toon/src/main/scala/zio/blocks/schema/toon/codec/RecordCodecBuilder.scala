package zio.blocks.schema.toon.codec

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, Registers, RegisterOffset}
import zio.blocks.schema.toon._
import zio.blocks.schema.toon.ToonCodecUtils.unescapeQuoted
import zio.blocks.typeid.TypeId

import scala.util.control.NonFatal

private[toon] final class RecordCodecBuilder(
  fieldNameMapper: NameMapper,
  rejectExtraFields: Boolean,
  transientNone: Boolean,
  transientEmptyCollection: Boolean,
  transientDefaultValue: Boolean,
  requireOptionFields: Boolean,
  requireCollectionFields: Boolean,
  requireDefaultValueFields: Boolean,
  recursiveRecordCache: ThreadLocal[java.util.HashMap[TypeId[_], Array[ToonFieldInfo]]],
  discriminatorFields: ThreadLocal[List[ToonDiscriminatorFieldInfo]],
  codecDeriver: CodecDeriver
) {

  private def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] =
    codecDeriver.derive(reflect)

  private def isOptional[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    !requireOptionFields && reflect.isOption

  private def isCollection[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    !requireCollectionFields && reflect.isCollection

  private def defaultValue[F[_, _], A](fieldReflect: Reflect[F, A]): Option[() => Any] =
    if (requireDefaultValueFields) None
    else fieldReflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue.map(v => () => v)

  private def stripQuotes(s: String, from: Int, to: Int): String =
    if (to - from >= 2 && s.charAt(from) == '"' && s.charAt(to - 1) == '"') s.substring(from + 1, to - 1)
    else s.substring(from, to)

  def build[F[_, _], A](record: Reflect.Record[F, A], binding: Binding.Record[A]): ToonBinaryCodec[A] = {
    val fields = record.fields
    val len    = fields.length
    if (len == 0) { buildEmptyRecordCodec(binding) }
    else if (len == 1 && fields(0).value.isMap) buildSingleMapFieldCodec(record, binding)
    else buildGeneralRecordCodec(record, binding)
  }

  private def buildEmptyRecordCodec[A](binding: Binding.Record[A]): ToonBinaryCodec[A] =
    new ToonBinaryCodec[A]() {
      private[this] val constructor = binding.constructor

      def decodeValue(in: ToonReader, default: A): A = {
        in.skipBlankLines()
        constructor.construct(null, 0)
      }

      def encodeValue(x: A, out: ToonWriter): Unit = ()
    }

  private def buildSingleMapFieldCodec[F[_, _], A](
    record: Reflect.Record[F, A],
    binding: Binding.Record[A]
  ): ToonBinaryCodec[A] = {
    val mapField = record.fields(0)
    val mapCodec = deriveCodec(mapField.value).asInstanceOf[ToonBinaryCodec[AnyRef]]
    new ToonBinaryCodec[A]() {
      private[this] val mCodec        = mapCodec
      private[this] val deconstructor = binding.deconstructor
      private[this] val constructor   = binding.constructor

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

  private def buildGeneralRecordCodec[F[_, _], A](
    record: Reflect.Record[F, A],
    binding: Binding.Record[A]
  ): ToonBinaryCodec[A] = {
    val fields       = record.fields
    val len          = fields.length
    val isRecursive  = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
    val typeId       = record.typeId
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
          defaultValue(fieldReflect).orNull,
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
        val codec = deriveCodec(field.value)
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
      private[this] val deconstructor       = binding.deconstructor
      private[this] val constructor         = binding.constructor
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

      private def skipOrReject(in: ToonReader, key: String): Unit =
        if (doReject && ((discriminatorField eq null) || discriminatorField.name != key)) {
          in.decodeError(s"Unexpected field: $key")
        } else in.advanceLine()

      private def setMissingAndConstruct(in: ToonReader, missing: Array[Boolean], regs: Registers) = {
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
              if (rawValue.startsWith("\"") && rawValue.endsWith("\"") && rawValue.length >= 2) unescapeQuoted(rawValue)
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
}
