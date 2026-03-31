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

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Parser for YAML 1.2 input, converting YAML text into a [[Yaml]] AST.
 */
object YamlReader {
  def read(input: String): Yaml =
    new YamlParser(input.replace("\r\n", "\n").replace("\r", "\n")).parse()

  def readFromBytes(input: Array[Byte]): Yaml =
    read(new String(input, UTF_8))

  private class YamlParser(input: String) {
    private val lines: Array[String] = input.split("\n", -1)
    private var lineIdx: Int         = 0

    def parse(): Yaml = {
      skipBlanksAndComments()
      if (lineIdx >= lines.length) Yaml.NullValue
      else {
        val line = lines(lineIdx).trim
        if (line == "---") {
          lineIdx += 1
          skipBlanksAndComments()
        }
        if (lineIdx >= lines.length) Yaml.NullValue
        else parseValue(0)
      }
    }

    private[this] def skipBlanksAndComments(): Unit =
      while (
        lineIdx < lines.length && {
          val trimmed = lines(lineIdx).trim
          trimmed.isEmpty || trimmed.startsWith("#")
        }
      ) lineIdx += 1

    private[this] def currentIndent: Int =
      if (lineIdx >= lines.length) 0
      else countLeadingSpaces(lines(lineIdx))

    private[this] def countLeadingSpaces(s: String): Int = {
      var idx = 0
      while (idx < s.length && s.charAt(idx) == ' ') idx += 1
      idx
    }

    private[this] def parseValue(indent: Int): Yaml = {
      skipBlanksAndComments()
      if (lineIdx >= lines.length) Yaml.NullValue
      else {
        val line = lines(lineIdx).trim
        if (line.startsWith("{")) parseFlowMapping()
        else if (line.startsWith("[")) parseFlowSequence()
        else if (line.startsWith("- ") || line == "-") parseBlockSequence(currentIndent)
        else if (line.startsWith("|") || line.startsWith(">")) parseLiteralBlock(indent)
        else {
          val colonIdx = findMappingColon(line)
          if (colonIdx > 0) parseBlockMapping(currentIndent)
          else parseScalar(indent)
        }
      }
    }

    private[this] def findMappingColon(line: String): Int = {
      val len      = line.length
      var idx      = 0
      var inSingle = false
      var inDouble = false
      while (idx < len) {
        val c = line.charAt(idx)
        if (c == '\'' && !inDouble) inSingle = !inSingle
        else if (c == '"' && !inSingle) inDouble = !inDouble
        else if (!inSingle && !inDouble) {
          if (c == ':' && (idx + 1 >= len || line.charAt(idx + 1) == ' ')) return idx
          if (c == '#') return -1
        }
        idx += 1
      }
      -1
    }

    private[this] def parseBlockMapping(indent: Int): Yaml = {
      val entries = ChunkBuilder.make[(Yaml, Yaml)]()
      while (lineIdx < lines.length) {
        skipBlanksAndComments()
        if (lineIdx >= lines.length || currentIndent < indent || currentIndent != indent) {
          return new Yaml.Mapping(entries.result())
        }
        val line     = lines(lineIdx).trim
        val colonIdx = findMappingColon(line)
        if (colonIdx <= 0) return new Yaml.Mapping(entries.result())
        val keyStr     = line.substring(0, colonIdx).trim
        val key        = new Yaml.Scalar(unquoteScalar(keyStr))
        val afterColon = line.substring(colonIdx + 1).trim
        lineIdx += 1
        entries.addOne(
          (
            key, {
              if (afterColon.isEmpty) {
                skipBlanksAndComments()
                if (lineIdx < lines.length && currentIndent > indent) parseValue(currentIndent)
                else Yaml.NullValue
              } else if (
                afterColon == "|" || afterColon == ">" || afterColon.startsWith("|") || afterColon.startsWith(">")
              ) {
                parseLiteralBlockContent(indent, afterColon.charAt(0) == '|')
              } else parseInlineValue(afterColon)
            }
          )
        )
      }
      new Yaml.Mapping(entries.result())
    }

    private[this] def parseBlockSequence(indent: Int): Yaml = {
      val elements = ChunkBuilder.make[Yaml]()
      while (lineIdx < lines.length) {
        skipBlanksAndComments()
        if (lineIdx >= lines.length || currentIndent < indent || currentIndent != indent) {
          return new Yaml.Sequence(elements.result())
        }
        val line = lines(lineIdx).trim
        if (!line.startsWith("- ") && line != "-") return new Yaml.Sequence(elements.result())
        val afterDash =
          if (line == "-") ""
          else line.substring(2).trim
        if (afterDash.isEmpty) {
          lineIdx += 1
          skipBlanksAndComments()
          elements.addOne(
            if (lineIdx < lines.length && currentIndent > indent) parseValue(currentIndent)
            else Yaml.NullValue
          )
        } else {
          val dashContentIndent = indent + 2
          val colonIdx          = findMappingColon(afterDash)
          if (colonIdx > 0) {
            lineIdx += 1
            val keyStr     = afterDash.substring(0, colonIdx).trim
            val key        = new Yaml.Scalar(unquoteScalar(keyStr))
            val afterColon = afterDash.substring(colonIdx + 1).trim
            val firstValue =
              if (afterColon.isEmpty) {
                skipBlanksAndComments()
                if (lineIdx < lines.length && currentIndent > indent) parseValue(currentIndent)
                else Yaml.NullValue
              } else parseInlineValue(afterColon)
            val mapEntries = ChunkBuilder.make[(Yaml, Yaml)]()
            mapEntries.addOne((key, firstValue))
            while (lineIdx < lines.length) {
              skipBlanksAndComments()
              if (lineIdx >= lines.length || currentIndent < dashContentIndent) {
                elements.addOne(new Yaml.Mapping(mapEntries.result()))
                mapEntries.clear()
                return parseBlockSequenceContinue(indent, elements)
              }
              if (currentIndent != dashContentIndent) {
                elements.addOne(new Yaml.Mapping(mapEntries.result()))
                return parseBlockSequenceContinue(indent, elements)
              }
              val innerLine     = lines(lineIdx).trim
              val innerColonIdx = findMappingColon(innerLine)
              if (innerColonIdx <= 0) {
                elements.addOne(new Yaml.Mapping(mapEntries.result()))
                return parseBlockSequenceContinue(indent, elements)
              }
              val innerKeyStr     = innerLine.substring(0, innerColonIdx).trim
              val innerKey        = new Yaml.Scalar(unquoteScalar(innerKeyStr))
              val innerAfterColon = innerLine.substring(innerColonIdx + 1).trim
              lineIdx += 1
              mapEntries.addOne(
                (
                  innerKey, {
                    if (innerAfterColon.isEmpty) {
                      skipBlanksAndComments()
                      if (lineIdx < lines.length && currentIndent > dashContentIndent) parseValue(currentIndent)
                      else Yaml.NullValue
                    } else parseInlineValue(innerAfterColon)
                  }
                )
              )
            }
            elements.addOne(new Yaml.Mapping(mapEntries.result()))
          } else {
            lineIdx += 1
            elements.addOne(parseInlineValue(afterDash))
          }
        }
      }
      new Yaml.Sequence(elements.result())
    }

    private[this] def parseBlockSequenceContinue(indent: Int, elements: ChunkBuilder[Yaml]): Yaml = {
      while (lineIdx < lines.length) {
        skipBlanksAndComments()
        if (lineIdx >= lines.length || currentIndent < indent || currentIndent != indent) {
          return new Yaml.Sequence(elements.result())
        }
        val line = lines(lineIdx).trim
        if (!line.startsWith("- ") && line != "-") return new Yaml.Sequence(elements.result())
        val afterDash = if (line == "-") "" else line.substring(2).trim
        if (afterDash.isEmpty) {
          lineIdx += 1
          skipBlanksAndComments()
          elements += {
            if (lineIdx < lines.length && currentIndent > indent) parseValue(currentIndent)
            else Yaml.NullValue
          }
        } else {
          val colonIdx = findMappingColon(afterDash)
          lineIdx += 1
          elements.addOne(if (colonIdx > 0) {
            val dashContentIndent = indent + 2
            val keyStr            = afterDash.substring(0, colonIdx).trim
            val key               = new Yaml.Scalar(unquoteScalar(keyStr))
            val afterColon        = afterDash.substring(colonIdx + 1).trim
            val firstValue        = if (afterColon.isEmpty) {
              skipBlanksAndComments()
              if (lineIdx < lines.length && currentIndent > indent) parseValue(currentIndent)
              else Yaml.NullValue
            } else parseInlineValue(afterColon)
            val mapEntries = ChunkBuilder.make[(Yaml, Yaml)]()
            mapEntries.addOne((key, firstValue))
            while (lineIdx < lines.length) {
              skipBlanksAndComments()
              if (lineIdx >= lines.length || currentIndent < dashContentIndent || currentIndent != dashContentIndent) {
                elements.addOne(new Yaml.Mapping(mapEntries.result()))
                return parseBlockSequenceContinue(indent, elements)
              }
              val innerLine     = lines(lineIdx).trim
              val innerColonIdx = findMappingColon(innerLine)
              if (innerColonIdx <= 0) {
                elements.addOne(new Yaml.Mapping(mapEntries.result()))
                return parseBlockSequenceContinue(indent, elements)
              }
              val innerKeyStr     = innerLine.substring(0, innerColonIdx).trim
              val innerKey        = new Yaml.Scalar(unquoteScalar(innerKeyStr))
              val innerAfterColon = innerLine.substring(innerColonIdx + 1).trim
              lineIdx += 1
              mapEntries.addOne(
                (
                  innerKey, {
                    if (innerAfterColon.isEmpty) {
                      skipBlanksAndComments()
                      if (lineIdx < lines.length && currentIndent > dashContentIndent) parseValue(currentIndent)
                      else Yaml.NullValue
                    } else parseInlineValue(innerAfterColon)
                  }
                )
              )
            }
            new Yaml.Mapping(mapEntries.result())
          } else parseInlineValue(afterDash))
        }
      }
      Yaml.Sequence(elements.result())
    }

    private[this] def parseLiteralBlock(indent: Int): Yaml = {
      val line      = lines(lineIdx).trim
      val isLiteral = line.charAt(0) == '|'
      lineIdx += 1
      parseLiteralBlockContent(indent, isLiteral)
    }

    private[this] def parseLiteralBlockContent(indent: Int, isLiteral: Boolean): Yaml = {
      skipBlanksAndComments()
      if (lineIdx >= lines.length) new Yaml.Scalar("")
      else {
        val blockIndent = currentIndent
        if (blockIndent <= indent) new Yaml.Scalar("")
        else {
          val sb    = new java.lang.StringBuilder
          var first = true
          while (lineIdx < lines.length) {
            val raw = lines(lineIdx)
            if (raw.trim.isEmpty) {
              sb.append('\n')
              lineIdx += 1
            } else {
              val ci = countLeadingSpaces(raw)
              if (ci < blockIndent) {
                val lenM1 = sb.length() - 1
                if (lenM1 >= 0 && sb.charAt(lenM1) == '\n') sb.setLength(lenM1)
                return new Yaml.Scalar(sb.toString)
              }
              if (!first) {
                if (isLiteral) sb.append('\n')
                else sb.append(' ')
              }
              sb.append(raw.substring(blockIndent))
              first = false
              lineIdx += 1
            }
          }
          val lenM1 = sb.length() - 1
          if (lenM1 >= 0 && sb.charAt(lenM1) == '\n') sb.setLength(lenM1)
          new Yaml.Scalar(sb.toString)
        }
      }
    }

    private[this] def parseFlowMapping(): Yaml = {
      val line    = lines(lineIdx)
      val trimmed = line.trim
      lineIdx += 1
      if (trimmed == "{}") Yaml.Mapping.empty
      else {
        val content = collectFlowContent(trimmed, '{', '}')
        val inner   = content.substring(1, content.length - 1).trim
        if (inner.isEmpty) Yaml.Mapping.empty
        else {
          val entries = ChunkBuilder.make[(Yaml, Yaml)]()
          val parts   = splitFlowItems(inner)
          var idx     = 0
          while (idx < parts.length) {
            val part     = parts(idx).trim
            val colonIdx = part.indexOf(':')
            if (colonIdx > 0) {
              val k = unquoteScalar(part.substring(0, colonIdx).trim)
              val v = part.substring(colonIdx + 1).trim
              entries.addOne((new Yaml.Scalar(k), parseInlineValue(v)))
            }
            idx += 1
          }
          new Yaml.Mapping(entries.result())
        }
      }
    }

    private[this] def parseFlowSequence(): Yaml = {
      val line    = lines(lineIdx)
      val trimmed = line.trim
      lineIdx += 1
      if (trimmed == "[]") Yaml.Sequence.empty
      else {
        val content = collectFlowContent(trimmed, '[', ']')
        val inner   = content.substring(1, content.length - 1).trim
        if (inner.isEmpty) Yaml.Sequence.empty
        else {
          val parts    = splitFlowItems(inner)
          val len      = parts.length
          val elements = new Array[Yaml](len)
          var idx      = 0
          while (idx < len) {
            elements(idx) = parseInlineValue(parts(idx).trim)
            idx += 1
          }
          new Yaml.Sequence(Chunk.fromArray(elements))
        }
      }
    }

    private[this] def collectFlowContent(start: String, open: Char, close: Char): String = {
      var depth = 0
      var idx   = 0
      val s     = start
      while (idx < s.length) {
        if (s.charAt(idx) == open) depth += 1
        else if (s.charAt(idx) == close) depth -= 1
        idx += 1
      }
      if (depth == 0) s
      else {
        val sb = new java.lang.StringBuilder(s)
        while (depth > 0 && lineIdx < lines.length) {
          sb.append(' ')
          val nextLine = lines(lineIdx).trim
          lineIdx += 1
          var j = 0
          while (j < nextLine.length) {
            if (nextLine.charAt(j) == open) depth += 1
            else if (nextLine.charAt(j) == close) depth -= 1
            j += 1
          }
          sb.append(nextLine)
        }
        sb.toString
      }
    }

    private[this] def splitFlowItems(s: String): Array[String] = {
      val result   = new java.util.ArrayList[String]()
      var depth    = 0
      var start    = 0
      var idx      = 0
      var inSingle = false
      var inDouble = false
      while (idx < s.length) {
        val c = s.charAt(idx)
        if (inDouble) {
          if (c == '\\') idx += 1
          else if (c == '"') inDouble = false
        } else if (inSingle) {
          if (c == '\'') inSingle = false
        } else if (c == '"') inDouble = true
        else if (c == '\'') inSingle = true
        else if (c == '{' || c == '[') depth += 1
        else if (c == '}' || c == ']') depth -= 1
        else if (c == ',' && depth == 0) {
          result.add(s.substring(start, idx))
          start = idx + 1
        }
        idx += 1
      }
      if (start < s.length) result.add(s.substring(start))
      val arr = new Array[String](result.size)
      result.toArray(arr)
      arr
    }

    private[this] def parseScalar(@scala.annotation.unused indent: Int): Yaml = {
      val line = lines(lineIdx)
      lineIdx += 1
      parseInlineValue(line.trim)
    }

    private[this] def parseInlineValue(s: String): Yaml = {
      val trimmed = stripComment(s).trim
      if (trimmed.isEmpty || trimmed == "null" || trimmed == "~") Yaml.NullValue
      else if (trimmed.startsWith("'")) new Yaml.Scalar(unquoteSingle(trimmed))
      else if (trimmed.startsWith("\"")) new Yaml.Scalar(unquoteDouble(trimmed))
      else if (trimmed.startsWith("{")) parseFlowMappingInline(trimmed)
      else if (trimmed.startsWith("[")) parseFlowSequenceInline(trimmed)
      else new Yaml.Scalar(trimmed)
    }

    private[this] def parseFlowMappingInline(s: String): Yaml =
      if (s == "{}") Yaml.Mapping.empty
      else {
        val inner = s.substring(1, s.length - 1).trim
        if (inner.isEmpty) Yaml.Mapping.empty
        else {
          val entries = ChunkBuilder.make[(Yaml, Yaml)]()
          val parts   = splitFlowItems(inner)
          var idx     = 0
          while (idx < parts.length) {
            val part     = parts(idx).trim
            val colonIdx = part.indexOf(':')
            if (colonIdx > 0) {
              val k = unquoteScalar(part.substring(0, colonIdx).trim)
              val v = part.substring(colonIdx + 1).trim
              entries.addOne((Yaml.Scalar(k), parseInlineValue(v)))
            }
            idx += 1
          }
          new Yaml.Mapping(entries.result())
        }
      }

    private[this] def parseFlowSequenceInline(s: String): Yaml =
      if (s == "[]") Yaml.Sequence.empty
      else {
        val inner = s.substring(1, s.length - 1).trim
        if (inner.isEmpty) Yaml.Sequence.empty
        else {
          val parts    = splitFlowItems(inner)
          val len      = parts.length
          val elements = new Array[Yaml](len)
          var idx      = 0
          while (idx < len) {
            elements(idx) = parseInlineValue(parts(idx).trim)
            idx += 1
          }
          new Yaml.Sequence(Chunk.fromArray(elements))
        }
      }

    private[this] def stripComment(s: String): String = {
      var inSingle = false
      var inDouble = false
      var idx      = 0
      while (idx < s.length) {
        val c = s.charAt(idx)
        if (c == '\\' && inDouble && idx + 1 < s.length) idx += 2
        else {
          if (c == '\'' && !inDouble) inSingle = !inSingle
          else if (c == '"' && !inSingle) inDouble = !inDouble
          else if (c == '#' && !inSingle && !inDouble && idx > 0 && s.charAt(idx - 1) == ' ') {
            return s.substring(0, idx).trim
          }
          idx += 1
        }
      }
      s
    }

    private[this] def unquoteScalar(s: String): String =
      if (s.startsWith("'") && s.endsWith("'") && s.length >= 2) unquoteSingle(s)
      else if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) unquoteDouble(s)
      else s

    private[this] def unquoteSingle(s: String): String = s.substring(1, s.length - 1).replace("''", "'")

    private[this] def unquoteDouble(s: String): String = {
      val inner = s.substring(1, s.length - 1)
      val len   = inner.length
      val sb    = new java.lang.StringBuilder(len)
      var idx   = 0
      while (idx < len) {
        if (inner.charAt(idx) == '\\' && idx + 1 < len) {
          inner.charAt(idx + 1) match {
            case 'n'                  => sb.append('\n')
            case 't'                  => sb.append('\t')
            case 'r'                  => sb.append('\r')
            case '\\'                 => sb.append('\\')
            case '"'                  => sb.append('"')
            case '/'                  => sb.append('/')
            case 'b'                  => sb.append('\b')
            case 'a'                  => sb.append('\u0007')
            case '0'                  => sb.append('\u0000')
            case 'u' if idx + 5 < len =>
              val hex = inner.substring(idx + 2, idx + 6)
              try {
                sb.append(Integer.parseInt(hex, 16).toChar)
                idx += 4
              } catch {
                case _: NumberFormatException => sb.append('\\').append('u')
              }
            case 'x' if idx + 3 < len =>
              val hex = inner.substring(idx + 2, idx + 4)
              try {
                sb.append(Integer.parseInt(hex, 16).toChar)
                idx += 2
              } catch {
                case _: NumberFormatException => sb.append('\\').append('x')
              }
            case other => sb.append('\\').append(other)
          }
          idx += 2
        } else {
          sb.append(inner.charAt(idx))
          idx += 1
        }
      }
      sb.toString
    }
  }
}
