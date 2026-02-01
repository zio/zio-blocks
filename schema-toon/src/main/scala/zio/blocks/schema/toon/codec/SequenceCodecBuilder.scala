package zio.blocks.schema.toon.codec

import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.toon._
import zio.blocks.schema.toon.ToonBinaryCodec.stringCodec
import zio.blocks.schema.toon.ToonCodecUtils.{createReaderForValue, unescapeQuoted}

import scala.reflect.ClassTag
import scala.util.control.NonFatal

private[toon] final class SequenceCodecBuilder(
  arrayFormat: ArrayFormat,
  delimiter: Delimiter,
  codecDeriver: CodecDeriver
) {
  private def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] = codecDeriver.derive(reflect)

  def build[F[_, _], Elem, Col[_]](
    sequence: Reflect.Sequence[F, Elem, Col],
    binding: Binding.Seq[Col, Elem]
  ): ToonBinaryCodec[Col[Elem]] = {
    val elemCodec   = deriveCodec(sequence.element).asInstanceOf[ToonBinaryCodec[Elem]]
    val isPrimitive = elemCodec.isPrimitive
    val useInline   = arrayFormat match {
      case ArrayFormat.Auto    => isPrimitive
      case ArrayFormat.Inline  => isPrimitive
      case ArrayFormat.List    => false
      case ArrayFormat.Tabular => false
    }
    val configuredDelimiter = delimiter
    val isRecordElement     = elemCodec.isRecordCodec
    val useTabular          = arrayFormat match {
      case ArrayFormat.Tabular => isRecordElement
      case ArrayFormat.Auto    => false
      case _                   => false
    }
    new ToonBinaryCodec[Col[Elem]]() {
      private[this] val deconstructor    = binding.deconstructor
      private[this] val constructor      = binding.constructor
      private[this] val elementCodec     = elemCodec
      private[this] val elemClassTag     = sequence.elemClassTag.asInstanceOf[ClassTag[Elem]]
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

      private def encodeAsListFormat(fieldName: String, x: Col[Elem], size: Int, out: ToonWriter): Unit = {
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
}
