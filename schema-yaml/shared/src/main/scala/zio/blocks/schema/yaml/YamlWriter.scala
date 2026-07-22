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

package zio.blocks.schema.yaml

import zio.blocks.chunk.Chunk
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Serializer for the [[Yaml]] AST, producing YAML 1.2 text output. Formatting
 * behavior (indentation, flow style, document markers) is controlled via
 * [[YamlOptions]].
 */
object YamlWriter {
  def write(yaml: Yaml, options: YamlOptions = YamlOptions.default): String = {
    val sb = new java.lang.StringBuilder
    if (options.documentMarkers) sb.append("---\n")
    writeNode(sb, yaml, 0, options, isTopLevel = true)
    sb.toString
  }

  def writeToBytes(yaml: Yaml, options: YamlOptions = YamlOptions.default): Array[Byte] =
    write(yaml, options).getBytes(UTF_8)

  private[this] def writeNode(
    sb: java.lang.StringBuilder,
    yaml: Yaml,
    indent: Int,
    options: YamlOptions,
    isTopLevel: Boolean
  ): Unit = yaml match {
    case Yaml.Mapping(entries) =>
      if (options.flowStyle) writeFlowMapping(sb, entries, options)
      else writeBlockMapping(sb, entries, indent, options, isTopLevel)
    case Yaml.Sequence(elements) =>
      if (options.flowStyle) writeFlowSequence(sb, elements, options)
      else writeBlockSequence(sb, elements, indent, options, isTopLevel)
    case Yaml.Scalar(value, tag) =>
      writeScalar(sb, value, tag)
    case Yaml.NullValue =>
      sb.append("null")
  }

  private[this] def writeBlockMapping(
    sb: java.lang.StringBuilder,
    entries: Chunk[(Yaml, Yaml)],
    indent: Int,
    options: YamlOptions,
    isTopLevel: Boolean
  ): Unit = {
    if (entries.isEmpty) {
      sb.append("{}")
      return
    }
    val iter  = entries.iterator
    var first = true
    while (iter.hasNext) {
      val (key, value) = iter.next()
      if (!first || !isTopLevel) {
        sb.append('\n')
        appendIndent(sb, indent)
      }
      writeScalarKey(sb, key)
      sb.append(':')
      value match {
        case Yaml.Mapping(subEntries) if subEntries.nonEmpty =>
          writeBlockMapping(sb, subEntries, indent + options.indentStep, options, isTopLevel = false)
        case Yaml.Sequence(elements) if elements.nonEmpty =>
          writeBlockSequence(sb, elements, indent + options.indentStep, options, isTopLevel = false)
        case _ =>
          sb.append(' ')
          writeNode(sb, value, indent + options.indentStep, options, isTopLevel = false)
      }
      first = false
    }
  }

  private[this] def writeBlockSequence(
    sb: java.lang.StringBuilder,
    elements: Chunk[Yaml],
    indent: Int,
    options: YamlOptions,
    isTopLevel: Boolean
  ): Unit = {
    if (elements.isEmpty) {
      sb.append("[]")
      return
    }
    val iter  = elements.iterator
    var first = true
    while (iter.hasNext) {
      val elem = iter.next()
      if (!first || !isTopLevel) {
        sb.append('\n')
        appendIndent(sb, indent)
      }
      sb.append("- ")
      elem match {
        case Yaml.Mapping(entries) if entries.nonEmpty =>
          val entriesIter = entries.iterator
          var firstEntry  = true
          while (entriesIter.hasNext) {
            val (k, v) = entriesIter.next()
            if (!firstEntry) {
              sb.append('\n')
              appendIndent(sb, indent + 2)
            }
            writeScalarKey(sb, k)
            sb.append(':')
            v match {
              case Yaml.Mapping(subEntries) if subEntries.nonEmpty =>
                writeBlockMapping(sb, subEntries, indent + 2 + options.indentStep, options, isTopLevel = false)
              case Yaml.Sequence(subElems) if subElems.nonEmpty =>
                writeBlockSequence(sb, subElems, indent + 2 + options.indentStep, options, isTopLevel = false)
              case _ =>
                sb.append(' ')
                writeNode(sb, v, indent + 2 + options.indentStep, options, isTopLevel = false)
            }
            firstEntry = false
          }
        case _ =>
          writeNode(sb, elem, indent + 2, options, isTopLevel = false)
      }
      first = false
    }
  }

  private[this] def writeFlowMapping(
    sb: java.lang.StringBuilder,
    entries: Chunk[(Yaml, Yaml)],
    options: YamlOptions
  ): Unit = {
    sb.append('{')
    val iter  = entries.iterator
    var first = true
    while (iter.hasNext) {
      if (!first) sb.append(", ")
      val (key, value) = iter.next()
      writeScalarKey(sb, key)
      sb.append(": ")
      writeNode(sb, value, 0, options, isTopLevel = false)
      first = false
    }
    sb.append('}')
  }

  private[this] def writeFlowSequence(
    sb: java.lang.StringBuilder,
    elements: Chunk[Yaml],
    options: YamlOptions
  ): Unit = {
    sb.append('[')
    val iter  = elements.iterator
    var first = true
    while (iter.hasNext) {
      if (!first) sb.append(", ")
      writeNode(sb, iter.next(), 0, options, isTopLevel = false)
      first = false
    }
    sb.append(']')
  }

  private[this] def writeScalarKey(sb: java.lang.StringBuilder, key: Yaml): Unit = key match {
    case Yaml.Scalar(value, tag) => writeScalar(sb, value, tag)
    case _                       => sb.append("null")
  }

  private[this] def writeScalar(sb: java.lang.StringBuilder, value: String, tag: Option[YamlTag]): Unit =
    if (needsQuoting(value, tag)) {
      sb.append('"')
      var idx = 0
      while (idx < value.length) {
        value.charAt(idx) match {
          case '"'           => sb.append("\\\"")
          case '\\'          => sb.append("\\\\")
          case '\n'          => sb.append("\\n")
          case '\t'          => sb.append("\\t")
          case '\r'          => sb.append("\\r")
          case '\b'          => sb.append("\\b")
          case c if c < 0x20 =>
            sb.append("\\u")
            sb.append(String.format("%04x", Int.box(c.toInt)))
          case c => sb.append(c)
        }
        idx += 1
      }
      sb.append('"')
    } else sb.append(value)

  private[this] def needsQuoting(value: String, tag: Option[YamlTag]): Boolean = {
    if (value.isEmpty) return true
    tag match {
      case Some(YamlTag.Bool) | Some(YamlTag.Int) | Some(YamlTag.Float) | Some(YamlTag.Null) =>
        return false
      case _ => ()
    }
    if (isSpecialValue(value)) return true
    val c0 = value.charAt(0)
    if (
      c0 == '\'' || c0 == '"' || c0 == '{' || c0 == '[' || c0 == '|' || c0 == '>' ||
      c0 == '%' || c0 == '@' || c0 == '`' || c0 == '&' || c0 == '*' || c0 == '!' || c0 == '?'
    ) return true
    if (looksNumeric(value)) return true
    var idx = 0
    while (idx < value.length) {
      val c = value.charAt(idx)
      if (c < 0x20 && c != '\t') return true
      if (c == '\n' || c == '\r') return true
      if (c == ':' && idx + 1 < value.length && value.charAt(idx + 1) == ' ') return true
      if (c == '#' && idx > 0 && value.charAt(idx - 1) == ' ') return true
      idx += 1
    }
    false
  }

  private[this] def isSpecialValue(value: String): Boolean = value match {
    case "null" | "~" | "Null" | "NULL"                         => true
    case "true" | "false" | "True" | "False" | "TRUE" | "FALSE" => true
    case "yes" | "no" | "Yes" | "No" | "YES" | "NO"             => true
    case "on" | "off" | "On" | "Off" | "ON" | "OFF"             => true
    case ".inf" | "-.inf" | ".Inf" | "-.Inf" | ".INF" | "-.INF" => true
    case ".nan" | ".NaN" | ".NAN"                               => true
    case _                                                      => false
  }

  private[this] def looksNumeric(value: String): Boolean = {
    val c0 = value.charAt(0)
    if (c0 >= '0' && c0 <= '9') return true
    if ((c0 == '+' || c0 == '-') && value.length > 1) {
      val c1 = value.charAt(1)
      if (c1 >= '0' && c1 <= '9') return true
      if (c1 == '.') return true
    }
    if (c0 == '.' && value.length > 1) {
      val c1 = value.charAt(1)
      if (c1 >= '0' && c1 <= '9') return true
    }
    false
  }

  private[this] def appendIndent(sb: java.lang.StringBuilder, indent: Int): Unit = {
    var idx = 0
    while (idx < indent) {
      sb.append(' ')
      idx += 1
    }
  }
}
