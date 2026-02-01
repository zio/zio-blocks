package golem.runtime.autowire

import golem.data._

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

private[autowire] object HostValueDecoder {
  def decode(schema: StructuredSchema, value: js.Dynamic): Either[String, StructuredValue] =
    if (js.isUndefined(value) || value == null)
      Left("Missing DataValue payload")
    else {
      val tag = value.selectDynamic("tag").asInstanceOf[String]
      schema match {
        case tuple: StructuredSchema.Tuple =>
          if (tag != "tuple") Left(s"Expected tuple payload, found: $tag")
          else {
            val entries = value.selectDynamic("val").asInstanceOf[js.Array[js.Dynamic]]
            decodeEntries(tuple.elements, entries).map(values => StructuredValue.Tuple(values))
          }
        case multi: StructuredSchema.Multimodal =>
          if (tag != "multimodal") Left(s"Expected multimodal payload, found: $tag")
          else {
            val entries = value.selectDynamic("val").asInstanceOf[js.Array[js.Dynamic]]
            decodeEntries(multi.elements, entries).map(values => StructuredValue.Multimodal(values))
          }
      }
    }

  private def decodeEntries(
    schemaElements: List[NamedElementSchema],
    payload: js.Array[js.Dynamic]
  ): Either[String, List[NamedElementValue]] =
    if (schemaElements.length != payload.length)
      Left(s"Structured element count mismatch. Expected ${schemaElements.length}, found ${payload.length}")
    else {
      val builder                 = List.newBuilder[NamedElementValue]
      var idx                     = 0
      var failure: Option[String] = None
      while (idx < schemaElements.length && failure.isEmpty) {
        val schemaElem                      = schemaElements(idx)
        val raw                             = payload(idx).asInstanceOf[js.Any]
        val elementValueDynamic: js.Dynamic =
          if (js.Array.isArray(raw)) {
            val entry = raw.asInstanceOf[js.Array[js.Any]]
            if (entry.length < 2) {
              failure = Some("Malformed tuple element: expected [name, value]")
              js.undefined.asInstanceOf[js.Dynamic]
            } else {
              val name = entry(0).asInstanceOf[String]
              if (name != schemaElem.name) {
                failure = Some(s"Structured element name mismatch. Expected '${schemaElem.name}', found '$name'")
                js.undefined.asInstanceOf[js.Dynamic]
              } else entry(1).asInstanceOf[js.Dynamic]
            }
          } else raw.asInstanceOf[js.Dynamic]

        if (failure.isEmpty) {
          decodeElement(schemaElem.schema, elementValueDynamic) match {
            case Left(err)    => failure = Some(err)
            case Right(value) => builder += NamedElementValue(schemaElem.name, value)
          }
        }
        idx += 1
      }
      failure.fold[Either[String, List[NamedElementValue]]](Right(builder.result()))(Left(_))
    }

  private def decodeElement(schema: ElementSchema, value: js.Dynamic): Either[String, ElementValue] = {
    val tag = value.selectDynamic("tag").asInstanceOf[String]
    schema match {
      case ElementSchema.Component(dataType) =>
        if (tag != "component-model")
          Left(s"Expected component-model value, found: $tag")
        else {
          val witValue = value.selectDynamic("val")
          WitValueCodec.decode(dataType, witValue).map(ElementValue.Component.apply)
        }
      case ElementSchema.UnstructuredText(_) =>
        val payload  = value.selectDynamic("val")
        val innerTag =
          if (tag == "unstructured-text") payload.selectDynamic("tag").asInstanceOf[String]
          else tag
        val innerPayload =
          if (tag == "unstructured-text") payload.selectDynamic("val")
          else payload
        decodeTextValue(innerTag, innerPayload).map(ElementValue.UnstructuredText.apply)
      case ElementSchema.UnstructuredBinary(_) =>
        val payload  = value.selectDynamic("val")
        val innerTag =
          if (tag == "unstructured-binary") payload.selectDynamic("tag").asInstanceOf[String]
          else tag
        val innerPayload =
          if (tag == "unstructured-binary") payload.selectDynamic("val")
          else payload
        decodeBinaryValue(innerTag, innerPayload).map(ElementValue.UnstructuredBinary.apply)
    }
  }

  private def decodeTextValue(tag: String, payload: js.Dynamic): Either[String, UnstructuredTextValue] =
    tag match {
      case "url" =>
        Right(UnstructuredTextValue.Url(payload.asInstanceOf[String]))
      case "inline" =>
        val data     = payload.selectDynamic("data").asInstanceOf[String]
        val textType = payload.selectDynamic("text-type")
        val language =
          if (js.isUndefined(textType) || textType == null) None
          else {
            val optionTag = textType.selectDynamic("tag").asInstanceOf[String]
            optionTag match {
              case "none" => None
              case "some" =>
                val descriptor = textType.selectDynamic("val")
                val code       = descriptor.selectDynamic("language-code").asInstanceOf[String]
                Some(code)
              case other =>
                return Left(s"Unexpected option tag for text-type: $other")
            }
          }
        Right(UnstructuredTextValue.Inline(data, language))
      case other =>
        Left(s"Unsupported unstructured-text payload: $other")
    }

  private def decodeBinaryValue(tag: String, payload: js.Dynamic): Either[String, UnstructuredBinaryValue] =
    tag match {
      case "url" =>
        Right(UnstructuredBinaryValue.Url(payload.asInstanceOf[String]))
      case "inline" =>
        val dataBuffer = payload.selectDynamic("data").asInstanceOf[Uint8Array]
        val mimeType   = payload.selectDynamic("binary-type").selectDynamic("mime-type").asInstanceOf[String]
        val bytes      = new Array[Byte](dataBuffer.length)
        var i          = 0
        while (i < dataBuffer.length) {
          bytes(i) = (dataBuffer(i) & 0xff).toByte
          i += 1
        }
        Right(UnstructuredBinaryValue.Inline(bytes, mimeType))

      case other =>
        Left(s"Unsupported unstructured-binary payload: $other")
    }
}
