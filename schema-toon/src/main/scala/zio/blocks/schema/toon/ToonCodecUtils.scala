package zio.blocks.schema.toon

import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.derive.BindingInstance
import zio.blocks.schema.toon.ToonBinaryCodec._

private[toon] object ToonCodecUtils {

  def isPrimitiveCodec(codec: ToonBinaryCodec[?]): Boolean =
    (codec eq intCodec) || (codec eq longCodec) || (codec eq floatCodec) || (codec eq doubleCodec) ||
      (codec eq booleanCodec) || (codec eq stringCodec) || (codec eq byteCodec) || (codec eq shortCodec) ||
      (codec eq charCodec) || (codec eq bigIntCodec) || (codec eq bigDecimalCodec)

  def isRecordCodec(codec: ToonBinaryCodec[?]): Boolean = codec.isRecordCodec

  def isEnumeration[F[_, _], A](variant: Reflect.Variant[F, A], enumValuesAsStrings: Boolean): Boolean =
    enumValuesAsStrings && variant.cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.asRecord.exists(_.fields.isEmpty) ||
      caseReflect.isVariant && caseReflect.asVariant.forall(v => isEnumeration(v, enumValuesAsStrings))
    }

  def option[F[_, _], A](variant: Reflect.Variant[F, A]): Option[Reflect[F, ?]] = {
    val typeName = variant.typeName
    val cases    = variant.cases
    if (
      typeName.namespace == Namespace.scala && typeName.name == "Option" &&
      cases.length == 2 && cases(1).name == "Some"
    ) cases(1).value.asRecord.map(_.fields(0).value)
    else None
  }

  def isOptional[F[_, _], A](reflect: Reflect[F, A], requireOptionFields: Boolean): Boolean =
    !requireOptionFields && reflect.isVariant && {
      val variant  = reflect.asVariant.get
      val typeName = reflect.typeName
      val cases    = variant.cases
      typeName.namespace == Namespace.scala && typeName.name == "Option" &&
      cases.length == 2 && cases(1).name == "Some"
    }

  def isCollection[F[_, _], A](reflect: Reflect[F, A], requireCollectionFields: Boolean): Boolean =
    !requireCollectionFields && (reflect.isSequence || reflect.isMap)

  def defaultValue[F[_, _], A](
    fieldReflect: Reflect[F, A],
    requireDefaultValueFields: Boolean
  ): Option[() => Any] =
    if (requireDefaultValueFields) None
    else
      {
        if (fieldReflect.isPrimitive) fieldReflect.asPrimitive.get.primitiveBinding
        else if (fieldReflect.isRecord) fieldReflect.asRecord.get.recordBinding
        else if (fieldReflect.isVariant) fieldReflect.asVariant.get.variantBinding
        else if (fieldReflect.isSequence) fieldReflect.asSequenceUnknown.get.sequence.seqBinding
        else if (fieldReflect.isMap) fieldReflect.asMapUnknown.get.map.mapBinding
        else if (fieldReflect.isWrapper) fieldReflect.asWrapperUnknown.get.wrapper.wrapperBinding
        else fieldReflect.asDynamic.get.dynamicBinding
      }.asInstanceOf[BindingInstance[ToonBinaryCodec, _, A]].binding.defaultValue

  def hasOnlyRecordAndVariantCases[F[_, _], A](variant: Reflect.Variant[F, A]): Boolean =
    variant.cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.isRecord || caseReflect.isVariant && caseReflect.asVariant.forall(hasOnlyRecordAndVariantCases)
    }

  def stripArrayNotation(key: String): (String, Boolean) = {
    val bracketIdx = key.indexOf('[')
    if (bracketIdx > 0) {
      (key.substring(0, bracketIdx), true)
    } else {
      (key, false)
    }
  }

  def createReaderForValue(value: String): ToonReader = {
    val reader = ToonReader(ReaderConfig.withDelimiter(Delimiter.None))
    reader.reset(value.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, value.length)
    reader
  }

  def unescapeQuoted(s: String): String = {
    if (!s.startsWith("\"") || !s.endsWith("\"")) return s
    val inner = s.substring(1, s.length - 1)
    if (inner.indexOf('\\') < 0) return inner
    val sb = new StringBuilder(inner.length)
    var i  = 0
    while (i < inner.length) {
      val c = inner.charAt(i)
      if (c == '\\' && i + 1 < inner.length) {
        inner.charAt(i + 1) match {
          case '"'   => sb.append('"'); i += 2
          case '\\'  => sb.append('\\'); i += 2
          case 'n'   => sb.append('\n'); i += 2
          case 'r'   => sb.append('\r'); i += 2
          case 't'   => sb.append('\t'); i += 2
          case other => sb.append('\\'); sb.append(other); i += 2
        }
      } else {
        sb.append(c)
        i += 1
      }
    }
    sb.toString
  }

  def discriminator[F[_, _], A](caseReflect: Reflect[F, A]): zio.blocks.schema.binding.Discriminator[A] =
    caseReflect.asVariant.get.variantBinding
      .asInstanceOf[BindingInstance[ToonBinaryCodec, _, A]]
      .binding
      .asInstanceOf[Binding.Variant[A]]
      .discriminator
}
