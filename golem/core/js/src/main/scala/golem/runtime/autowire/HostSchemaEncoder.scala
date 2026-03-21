package golem.runtime.autowire

import golem.data._
import golem.host.js._

import scala.scalajs.js

private[autowire] object HostSchemaEncoder {
  def encode(schema: StructuredSchema): JsDataSchema =
    schema match {
      case StructuredSchema.Tuple(elements) =>
        JsDataSchema.tuple(encodeNamedElements(elements))
      case StructuredSchema.Multimodal(elements) =>
        JsDataSchema.multimodal(encodeNamedElements(elements))
    }

  private def encodeNamedElements(elements: List[NamedElementSchema]): js.Array[js.Tuple2[String, JsElementSchema]] = {
    val array = new js.Array[js.Tuple2[String, JsElementSchema]]()
    elements.foreach { elem =>
      array.push(js.Tuple2(elem.name, encodeElement(elem.schema)))
    }
    array
  }

  private def encodeElement(element: ElementSchema): JsElementSchema =
    element match {
      case ElementSchema.Component(dataType) =>
        JsElementSchema.componentModel(WitTypeBuilder.build(dataType))
      case ElementSchema.UnstructuredText(restrictions) =>
        JsElementSchema.unstructuredText(encodeTextRestrictions(restrictions))
      case ElementSchema.UnstructuredBinary(restrictions) =>
        JsElementSchema.unstructuredBinary(encodeBinaryRestrictions(restrictions))
    }

  private def encodeTextRestrictions(restrictions: Option[List[String]]): JsTextDescriptor =
    restrictions match {
      case Some(values) =>
        val arr = new js.Array[JsTextType]()
        values.foreach(code => arr.push(JsTextType(code)))
        JsTextDescriptor(arr)
      case None =>
        JsTextDescriptor()
    }

  private def encodeBinaryRestrictions(restrictions: Option[List[String]]): JsBinaryDescriptor =
    restrictions match {
      case Some(values) =>
        val arr = new js.Array[JsBinaryType]()
        values.foreach(mime => arr.push(JsBinaryType(mime)))
        JsBinaryDescriptor(arr)
      case None =>
        JsBinaryDescriptor()
    }
}
