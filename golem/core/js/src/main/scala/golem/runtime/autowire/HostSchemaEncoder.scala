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
