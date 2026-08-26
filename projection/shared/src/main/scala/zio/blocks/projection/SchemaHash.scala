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

package zio.blocks.projection

import java.security.MessageDigest

import zio.blocks.schema.{Reflect, Schema}

object SchemaHash {

  def compute[A: Schema]: String = {
    val schema = summon[Schema[A]]
    val sb     = new StringBuilder
    appendSchema(sb, schema.reflect, 0)
    val md    = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(sb.toString.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    bytes.map("%02x".format(_)).mkString
  }

  private def appendSchema(sb: StringBuilder, reflect: Reflect[?, ?], depth: Int): Unit =
    reflect.asRecord match {
      case Some(record) =>
        sb.append("Record(").append(record.typeId.name).append("){")
        record.fields.zipWithIndex.foreach { case (field, idx) =>
          sb.append(field.name).append(":")
          sb.append(typeDescriptor(field.value))
          sb.append(":").append(idx).append(";")
        // For nested records with inline handling, do not recurse deeper to keep hash top-level only
        // but include nested structure if field is record and could be inlined – we hash top-level only per spec
        }
        sb.append("}")
      case None =>
        reflect.asVariant match {
          case Some(variant) =>
            sb.append("Variant(").append(variant.typeId.name).append("){")
            variant.cases.zipWithIndex.foreach { case (c, idx) =>
              sb.append(c.name).append(":").append(typeDescriptor(c.value)).append(":").append(idx).append(";")
            }
            sb.append("}")
          case None =>
            reflect.asWrapperUnknown match {
              case Some(w) =>
                // unwrap one level
                sb.append("Wrapper(").append(reflect.typeId.name).append(")->")
                appendSchema(sb, w.wrapper.wrapped, depth + 1)
              case None =>
                sb.append(reflect.typeId.name)
                // sequence/map/primitive fallback includes name only
                reflect.asSequenceUnknown.foreach { _ =>
                  sb.append("[]")
                }
                reflect.asMapUnknown.foreach { _ =>
                  sb.append("{}")
                }
            }
        }
    }

  private def typeDescriptor(reflect: Reflect[?, ?]): String =
    // include inner type for Option/Maybe/collections to avoid collisions
    if (reflect.isOption || reflect.isMaybe) {
      val inner = reflect.optionInnerType.map(_.typeId.name).getOrElse("unknown")
      s"${reflect.typeId.name}[$inner]"
    } else if (reflect.isSequence || reflect.isMap) {
      // for collections, include element type name
      val elemName = reflect.asSequenceUnknown
        .map(_.sequence.element.typeId.name)
        .orElse(reflect.asMapUnknown.map(m => s"${m.map.key.typeId.name}->${m.map.value.typeId.name}"))
        .getOrElse("")
      if (elemName.nonEmpty) s"${reflect.typeId.name}[$elemName]" else reflect.typeId.name
    } else {
      reflect.typeId.name
    }
}
