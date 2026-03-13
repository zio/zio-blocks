package golem.runtime.autowire

import golem.data._

import scala.scalajs.js

private[autowire] object HostValueEncoder {
  def encode(schema: StructuredSchema, value: StructuredValue): Either[String, js.Dynamic] =
    (schema, value) match {
      case (StructuredSchema.Tuple(schemaElements), StructuredValue.Tuple(valueElements)) =>
        encodeEntries(schemaElements, valueElements).map { entries =>
          js.Dynamic.literal("tag" -> "tuple", "val" -> entries)
        }
      case (StructuredSchema.Multimodal(schemaElements), StructuredValue.Multimodal(valueElements)) =>
        encodeEntries(schemaElements, valueElements).map { entries =>
          js.Dynamic.literal("tag" -> "multimodal", "val" -> entries)
        }
      case (StructuredSchema.Tuple(_), _) =>
        Left("Structured value mismatch: expected tuple payload")
      case (StructuredSchema.Multimodal(_), _) =>
        Left("Structured value mismatch: expected multimodal payload")
    }

  private def encodeEntries(
    schemaElements: List[NamedElementSchema],
    valueElements: List[NamedElementValue]
  ): Either[String, js.Array[js.Any]] =
    if (schemaElements.length != valueElements.length)
      Left(s"Structured element count mismatch. Expected ${schemaElements.length}, found ${valueElements.length}")
    else {
      val array = new js.Array[js.Any]()
      schemaElements
        .zip(valueElements)
        .foldLeft[Either[String, Unit]](Right(())) { case (acc, (schemaElem, valueElem)) =>
          acc.flatMap { _ =>
            if (schemaElem.name != valueElem.name)
              Left(s"Structured element name mismatch. Expected '${schemaElem.name}', found '${valueElem.name}'")
            else
              encodeElement(schemaElem.schema, valueElem.value).map { encoded =>
                array.push(encoded)
              }
          }
        }
        .map(_ => array)
    }

  private def encodeElement(schema: ElementSchema, value: ElementValue): Either[String, js.Dynamic] =
    (schema, value) match {
      case (ElementSchema.Component(dataType), ElementValue.Component(dataValue)) =>
        WitValueBuilder.build(dataType, dataValue).map { witValue =>
          js.Dynamic.literal(
            "tag" -> "component-model",
            "val" -> witValue
          )
        }
      case (ElementSchema.UnstructuredText(_), ElementValue.UnstructuredText(textValue)) =>
        Right(
          js.Dynamic.literal(
            "tag" -> "unstructured-text",
            "val" -> encodeTextValue(textValue)
          )
        )
      case (ElementSchema.UnstructuredBinary(_), ElementValue.UnstructuredBinary(binaryValue)) =>
        Right(
          js.Dynamic.literal(
            "tag" -> "unstructured-binary",
            "val" -> encodeBinaryValue(binaryValue)
          )
        )
      case (expected, found) =>
        Left(s"Element schema/value mismatch. Expected $expected, found $found")
    }

  private def encodeTextValue(value: UnstructuredTextValue): js.Dynamic =
    value match {
      case UnstructuredTextValue.Url(url) =>
        js.Dynamic.literal(
          "tag" -> "url",
          "val" -> url
        )
      case UnstructuredTextValue.Inline(data, language) =>
        val textType = language match {
          case Some(code) =>
            js.Dynamic.literal(
              "tag" -> "some",
              "val" -> js.Dynamic.literal("language-code" -> code)
            )
          case None =>
            js.Dynamic.literal("tag" -> "none")
        }
        js.Dynamic.literal(
          "tag" -> "inline",
          "val" -> js.Dynamic.literal(
            "data"      -> data,
            "text-type" -> textType
          )
        )
    }

  private def encodeBinaryValue(value: UnstructuredBinaryValue): js.Dynamic =
    value match {
      case UnstructuredBinaryValue.Url(url) =>
        js.Dynamic.literal(
          "tag" -> "url",
          "val" -> url
        )
      case UnstructuredBinaryValue.Inline(data, mimeType) =>
        val typedArray = new js.typedarray.Uint8Array(data.length)
        var idx        = 0
        while (idx < data.length) {
          typedArray(idx) = ((data(idx) & 0xff).toShort)
          idx += 1
        }
        val typed = js.Dynamic.literal("mime-type" -> mimeType)
        js.Dynamic.literal(
          "tag" -> "inline",
          "val" -> js.Dynamic.literal(
            "data"        -> typedArray,
            "binary-type" -> typed
          )
        )
    }
}
