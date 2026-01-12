package zio.blocks.schema.toon.codec

import scala.util.control.NonFatal

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, Discriminator}
import zio.blocks.schema.derive.BindingInstance
import zio.blocks.schema.toon._
import zio.blocks.schema.toon.ToonCodecUtils._

private[toon] final class VariantCodecBuilder(
  discriminatorKind: DiscriminatorKind,
  caseNameMapper: NameMapper,
  enumValuesAsStrings: Boolean,
  discriminatorFields: ThreadLocal[List[ToonDiscriminatorFieldInfo]],
  codecDeriver: CodecDeriver
) {

  private def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] =
    codecDeriver.derive(reflect)

  def build[F[_, _], A](
    variant: Reflect.Variant[F, A],
    discr: Discriminator[A]
  ): ToonBinaryCodec[A] = {
    option(variant) match {
      case Some(innerReflect) =>
        buildOptionCodec(innerReflect)
      case None =>
        if (isEnumeration(variant, enumValuesAsStrings)) {
          buildEnumCodec(variant, discr)
        } else {
          discriminatorKind match {
            case DiscriminatorKind.Field(fieldName) if hasOnlyRecordAndVariantCases(variant) =>
              buildFieldDiscriminatorCodec(variant, discr, fieldName)
            case DiscriminatorKind.None =>
              buildNoneDiscriminatorCodec(variant, discr)
            case _ =>
              buildKeyDiscriminatorCodec(variant, discr)
          }
        }
    }
  }.asInstanceOf[ToonBinaryCodec[A]]

  private def buildOptionCodec[F[_, _], A](innerReflect: Reflect[F, A]): ToonBinaryCodec[Option[A]] =
    new ToonBinaryCodec[Option[Any]]() {
      private[this] val innerCodec = codecDeriver.derive(innerReflect).asInstanceOf[ToonBinaryCodec[Any]]

      override def decodeValue(in: ToonReader, default: Option[Any]): Option[Any] = {
        in.skipBlankLines()
        val content = in.peekTrimmedContent
        if (content == "null" || content.isEmpty) {
          if (content == "null") in.advanceLine()
          None
        } else {
          try Some(innerCodec.decodeValue(in, innerCodec.nullValue))
          catch {
            case error if NonFatal(error) =>
              throw new ToonBinaryCodecError(
                new ::(DynamicOptic.Node.Case("Some"), new ::(DynamicOptic.Node.Field("value"), Nil)),
                error.getMessage
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
        } else {
          innerCodec.encodeAsField(fieldName, x.get, out)
        }

      override def nullValue: Option[Any] = None
    }.asInstanceOf[ToonBinaryCodec[Option[A]]]

  private def buildEnumCodec[F[_, _], A](
    variant: Reflect.Variant[F, A],
    discr: Discriminator[A]
  ): ToonBinaryCodec[A] = {
    val caseInfos = buildEnumCaseInfos(variant)
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

  private def buildKeyDiscriminatorCodec[F[_, _], A](
    variant: Reflect.Variant[F, A],
    discr: Discriminator[A]
  ): ToonBinaryCodec[A] = {
    val caseInfos = buildCaseInfos(variant)
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
            case error if NonFatal(error) =>
              throw new ToonBinaryCodecError(
                new ::(DynamicOptic.Node.Case(key), Nil),
                error.getMessage
              )
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

  private def buildFieldDiscriminatorCodec[F[_, _], A](
    variant: Reflect.Variant[F, A],
    discr: Discriminator[A],
    fieldName: String
  ): ToonBinaryCodec[A] = {
    val caseMap = new java.util.HashMap[String, ToonCaseLeafInfo]()

    def getInfos(v: Reflect.Variant[F, A]): Array[ToonCaseInfo] = {
      val cases = v.cases
      val len   = cases.length
      val infos = new Array[ToonCaseInfo](len)
      var idx   = 0
      while (idx < len) {
        val case_       = cases(idx)
        val caseReflect = case_.value
        infos(idx) = if (caseReflect.isVariant) {
          val caseVariant = caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]
          new ToonCaseNodeInfo(discriminator(caseReflect), getInfos(caseVariant))
        } else {
          var name: String = null
          case_.modifiers.foreach {
            case m: Modifier.rename => if (name eq null) name = m.name
            case _: Modifier.alias  =>
            case _                  =>
          }
          if (name eq null) name = caseNameMapper(case_.name)
          discriminatorFields.set(new ToonDiscriminatorFieldInfo(fieldName, name) :: discriminatorFields.get)
          val codec = deriveCodec(caseReflect)
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
      private[this] val root                   = new ToonCaseNodeInfo(discr, getInfos(variant))
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
              return decodeFromLines(savedLines, discriminatorValue, in)
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

        if (discriminatorValue == null) {
          in.decodeError(s"Missing discriminator field: $discriminatorFieldName")
        }

        decodeFromLines(savedLines, discriminatorValue, in)
      }

      private def decodeFromLines(
        savedLines: java.util.ArrayList[String],
        discriminatorValue: String,
        in: ToonReader
      ): A = {
        val caseInfo = map.get(discriminatorValue)
        if (caseInfo eq null) {
          in.decodeError(s"Unknown variant case: $discriminatorValue")
        }

        val linesArray = new Array[String](savedLines.size)
        savedLines.toArray(linesArray)
        val combinedContent = linesArray.mkString("\n")
        val caseReader      = createReaderForValue(combinedContent)
        val codec           = caseInfo.codec.asInstanceOf[ToonBinaryCodec[A]]
        try codec.decodeValue(caseReader, codec.nullValue)
        catch {
          case error if NonFatal(error) =>
            throw new ToonBinaryCodecError(
              new ::(DynamicOptic.Node.Case(discriminatorValue), Nil),
              error.getMessage
            )
        }
      }

      def encodeValue(x: A, out: ToonWriter): Unit =
        root.discriminate(x).codec.asInstanceOf[ToonBinaryCodec[A]].encodeValue(x, out)
    }
  }

  private def buildNoneDiscriminatorCodec[F[_, _], A](
    variant: Reflect.Variant[F, A],
    discr: Discriminator[A]
  ): ToonBinaryCodec[A] = {
    val codecs    = new java.util.ArrayList[ToonBinaryCodec[?]]()
    val caseInfos = buildCaseInfos(variant)
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

      def encodeValue(x: A, out: ToonWriter): Unit = {
        val caseInfo = root.discriminate(x)
        caseInfo.codec.asInstanceOf[ToonBinaryCodec[A]].encodeValue(x, out)
      }
    }
  }

  private def buildEnumCaseInfos[F[_, _], A](variant: Reflect.Variant[F, A]): Array[ToonEnumInfo] = {
    val cases = variant.cases
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
        new ToonEnumNodeInfo(discr, buildEnumCaseInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]))
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

  private def buildCaseInfos[F[_, _], A](variant: Reflect.Variant[F, A]): Array[ToonCaseInfo] = {
    val cases = variant.cases
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
        new ToonCaseNodeInfo(discr, buildCaseInfos(caseReflect.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]))
      } else {
        val codec        = deriveCodec(caseReflect)
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
}
