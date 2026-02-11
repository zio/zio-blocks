package golem.runtime.autowire

import golem.data._

import scala.scalajs.js

private[autowire] object HostSchemaEncoder {
  def encode(schema: StructuredSchema): js.Dynamic =
    schema match {
      case StructuredSchema.Tuple(elements) =>
        js.Dynamic.literal(
          "tag" -> "tuple",
          "val" -> encodeNamedElements(elements)
        )
      case StructuredSchema.Multimodal(elements) =>
        js.Dynamic.literal(
          "tag" -> "multimodal",
          "val" -> encodeNamedElements(elements)
        )
    }

  private def encodeNamedElements(elements: List[NamedElementSchema]): js.Array[js.Any] = {
    val array = new js.Array[js.Any]()
    elements.foreach { elem =>
      array.push(js.Array(elem.name, encodeElement(elem.schema)))
    }
    array
  }

  private def encodeElement(element: ElementSchema): js.Dynamic =
    element match {
      case ElementSchema.Component(dataType) =>
        js.Dynamic.literal(
          "tag" -> "component-model",
          "val" -> WitTypeBuilder.build(dataType)
        )
      case ElementSchema.UnstructuredText(restrictions) =>
        js.Dynamic.literal(
          "tag" -> "unstructured-text",
          "val" -> encodeTextRestrictions(restrictions)
        )
      case ElementSchema.UnstructuredBinary(restrictions) =>
        js.Dynamic.literal(
          "tag" -> "unstructured-binary",
          "val" -> encodeBinaryRestrictions(restrictions)
        )
    }

  private def encodeTextRestrictions(restrictions: Option[List[String]]): js.Dynamic =
    restrictions match {
      case Some(values) =>
        val arr = new js.Array[js.Any]()
        values.foreach { code =>
          arr.push(js.Dynamic.literal("language-code" -> code))
        }
        js.Dynamic.literal(
          "tag" -> "some",
          "val" -> arr
        )
      case None =>
        js.Dynamic.literal("tag" -> "none")
    }

  private def encodeBinaryRestrictions(restrictions: Option[List[String]]): js.Dynamic =
    restrictions match {
      case Some(values) =>
        val arr = new js.Array[js.Any]()
        values.foreach { mime =>
          arr.push(js.Dynamic.literal("mime-type" -> mime))
        }
        js.Dynamic.literal(
          "tag" -> "some",
          "val" -> arr
        )
      case None =>
        js.Dynamic.literal("tag" -> "none")
    }
}
