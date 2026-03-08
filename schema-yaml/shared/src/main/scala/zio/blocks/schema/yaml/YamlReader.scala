package zio.blocks.schema.yaml

import zio.blocks.chunk.Chunk
import java.nio.charset.StandardCharsets.UTF_8

object YamlReader {

  def read(input: String): Either[YamlError, Yaml] =
    try {
      val normalized = input.replace("\r\n", "\n").replace("\r", "\n")
      val parser     = new YamlParser(normalized)
      parser.parse()
    } catch {
      case e: YamlError => Left(e)
      case e: Exception => Left(YamlError.parseError(e.getMessage, 0, 0))
    }

  def readFromBytes(input: Array[Byte]): Either[YamlError, Yaml] =
    read(new String(input, UTF_8))

  private class YamlParser(input: String) {
    private val lines: Array[String] = input.split("\n", -1)
    private var lineIdx: Int         = 0

    def parse(): Either[YamlError, Yaml] = {
      skipBlanksAndComments()
      if (lineIdx >= lines.length) Right(Yaml.NullValue)
      else {
        val line = lines(lineIdx).trim
        if (line == "---") {
          lineIdx += 1
          skipBlanksAndComments()
        }
        if (lineIdx >= lines.length) Right(Yaml.NullValue)
        else {
          val result = parseValue(0)
          result
        }
      }
    }

    private def skipBlanksAndComments(): Unit =
      while (
        lineIdx < lines.length && {
          val trimmed = lines(lineIdx).trim
          trimmed.isEmpty || trimmed.startsWith("#")
        }
      ) lineIdx += 1

    private def currentIndent: Int =
      if (lineIdx >= lines.length) 0
      else countLeadingSpaces(lines(lineIdx))

    private def countLeadingSpaces(s: String): Int = {
      var i = 0
      while (i < s.length && s.charAt(i) == ' ') i += 1
      i
    }

    private def parseValue(indent: Int): Either[YamlError, Yaml] = {
      skipBlanksAndComments()
      if (lineIdx >= lines.length) Right(Yaml.NullValue)
      else {
        val line    = lines(lineIdx)
        val trimmed = line.trim

        if (trimmed.startsWith("{")) parseFlowMapping()
        else if (trimmed.startsWith("[")) parseFlowSequence()
        else if (trimmed.startsWith("- ") || trimmed == "-") {
          val seqIndent = currentIndent
          parseBlockSequence(seqIndent)
        } else if (trimmed.startsWith("|") || trimmed.startsWith(">")) parseLiteralBlock(indent)
        else {
          val colonIdx = findMappingColon(trimmed)
          if (colonIdx > 0) {
            val mapIndent = currentIndent
            parseBlockMapping(mapIndent)
          } else parseScalar(indent)
        }
      }
    }

    private def findMappingColon(trimmed: String): Int = {
      var i        = 0
      var inSingle = false
      var inDouble = false
      while (i < trimmed.length) {
        val c = trimmed.charAt(i)
        if (c == '\'' && !inDouble) inSingle = !inSingle
        else if (c == '"' && !inSingle) inDouble = !inDouble
        else if (!inSingle && !inDouble) {
          if (c == ':' && (i + 1 >= trimmed.length || trimmed.charAt(i + 1) == ' ')) return i
          if (c == '#') return -1
        }
        i += 1
      }
      -1
    }

    private def parseBlockMapping(indent: Int): Either[YamlError, Yaml] = {
      val entries = Chunk.newBuilder[(Yaml, Yaml)]
      while (lineIdx < lines.length) {
        skipBlanksAndComments()
        if (lineIdx >= lines.length || currentIndent < indent) {
          return Right(Yaml.Mapping(entries.result()))
        }
        if (currentIndent != indent) {
          return Right(Yaml.Mapping(entries.result()))
        }

        val line     = lines(lineIdx)
        val trimmed  = line.trim
        val colonIdx = findMappingColon(trimmed)
        if (colonIdx <= 0) {
          return Right(Yaml.Mapping(entries.result()))
        }

        val keyStr     = trimmed.substring(0, colonIdx).trim
        val key        = Yaml.Scalar(unquoteScalar(keyStr))
        val afterColon = trimmed.substring(colonIdx + 1).trim

        if (afterColon.isEmpty) {
          lineIdx += 1
          skipBlanksAndComments()
          if (lineIdx < lines.length && currentIndent > indent) {
            parseValue(currentIndent) match {
              case Right(v) => entries += ((key, v))
              case left     => return left
            }
          } else {
            entries += ((key, Yaml.NullValue))
          }
        } else if (afterColon == "|" || afterColon == ">" || afterColon.startsWith("|") || afterColon.startsWith(">")) {
          val blockChar = afterColon.charAt(0)
          lineIdx += 1
          parseLiteralBlockContent(indent, blockChar == '|') match {
            case Right(v) => entries += ((key, v))
            case left     => return left
          }
        } else {
          lineIdx += 1
          val value = parseInlineValue(afterColon)
          entries += ((key, value))
        }
      }
      Right(Yaml.Mapping(entries.result()))
    }

    private def parseBlockSequence(indent: Int): Either[YamlError, Yaml] = {
      val elements = Chunk.newBuilder[Yaml]
      while (lineIdx < lines.length) {
        skipBlanksAndComments()
        if (lineIdx >= lines.length || currentIndent < indent) {
          return Right(Yaml.Sequence(elements.result()))
        }
        if (currentIndent != indent) {
          return Right(Yaml.Sequence(elements.result()))
        }

        val line    = lines(lineIdx)
        val trimmed = line.trim
        if (!trimmed.startsWith("- ") && trimmed != "-") {
          return Right(Yaml.Sequence(elements.result()))
        }

        val afterDash = if (trimmed == "-") "" else trimmed.substring(2).trim
        if (afterDash.isEmpty) {
          lineIdx += 1
          skipBlanksAndComments()
          if (lineIdx < lines.length && currentIndent > indent) {
            parseValue(currentIndent) match {
              case Right(v) => elements += v
              case left     => return left
            }
          } else {
            elements += Yaml.NullValue
          }
        } else {
          val dashContentIndent = indent + 2
          val colonIdx          = findMappingColon(afterDash)
          if (colonIdx > 0) {
            lineIdx += 1
            val keyStr     = afterDash.substring(0, colonIdx).trim
            val key        = Yaml.Scalar(unquoteScalar(keyStr))
            val afterColon = afterDash.substring(colonIdx + 1).trim

            val firstEntry = if (afterColon.isEmpty) {
              skipBlanksAndComments()
              if (lineIdx < lines.length && currentIndent > indent) {
                parseValue(currentIndent) match {
                  case Right(v) => (key, v)
                  case left     => return left
                }
              } else (key, Yaml.NullValue)
            } else {
              (key, parseInlineValue(afterColon))
            }

            val mapEntries = Chunk.newBuilder[(Yaml, Yaml)]
            mapEntries += firstEntry

            while (lineIdx < lines.length) {
              skipBlanksAndComments()
              if (lineIdx >= lines.length || currentIndent < dashContentIndent) {
                elements += Yaml.Mapping(mapEntries.result())
                mapEntries.clear()
                return parseBlockSequenceContinue(indent, elements)
              }
              if (currentIndent != dashContentIndent) {
                elements += Yaml.Mapping(mapEntries.result())
                return parseBlockSequenceContinue(indent, elements)
              }

              val innerLine     = lines(lineIdx).trim
              val innerColonIdx = findMappingColon(innerLine)
              if (innerColonIdx <= 0) {
                elements += Yaml.Mapping(mapEntries.result())
                return parseBlockSequenceContinue(indent, elements)
              }

              val innerKeyStr     = innerLine.substring(0, innerColonIdx).trim
              val innerKey        = Yaml.Scalar(unquoteScalar(innerKeyStr))
              val innerAfterColon = innerLine.substring(innerColonIdx + 1).trim

              if (innerAfterColon.isEmpty) {
                lineIdx += 1
                skipBlanksAndComments()
                if (lineIdx < lines.length && currentIndent > dashContentIndent) {
                  parseValue(currentIndent) match {
                    case Right(v) => mapEntries += ((innerKey, v))
                    case left     => return left
                  }
                } else {
                  mapEntries += ((innerKey, Yaml.NullValue))
                }
              } else {
                lineIdx += 1
                mapEntries += ((innerKey, parseInlineValue(innerAfterColon)))
              }
            }
            elements += Yaml.Mapping(mapEntries.result())
          } else {
            lineIdx += 1
            elements += parseInlineValue(afterDash)
          }
        }
      }
      Right(Yaml.Sequence(elements.result()))
    }

    private def parseBlockSequenceContinue(
      indent: Int,
      elements: zio.blocks.chunk.ChunkBuilder[Yaml]
    ): Either[YamlError, Yaml] = {
      while (lineIdx < lines.length) {
        skipBlanksAndComments()
        if (lineIdx >= lines.length || currentIndent < indent) {
          return Right(Yaml.Sequence(elements.result()))
        }
        if (currentIndent != indent) {
          return Right(Yaml.Sequence(elements.result()))
        }

        val line    = lines(lineIdx)
        val trimmed = line.trim
        if (!trimmed.startsWith("- ") && trimmed != "-") {
          return Right(Yaml.Sequence(elements.result()))
        }

        val afterDash = if (trimmed == "-") "" else trimmed.substring(2).trim
        if (afterDash.isEmpty) {
          lineIdx += 1
          skipBlanksAndComments()
          if (lineIdx < lines.length && currentIndent > indent) {
            parseValue(currentIndent) match {
              case Right(v) => elements += v
              case left     => return left
            }
          } else elements += Yaml.NullValue
        } else {
          val colonIdx = findMappingColon(afterDash)
          if (colonIdx > 0) {
            val dashContentIndent = indent + 2
            lineIdx += 1
            val keyStr     = afterDash.substring(0, colonIdx).trim
            val key        = Yaml.Scalar(unquoteScalar(keyStr))
            val afterColon = afterDash.substring(colonIdx + 1).trim
            val firstEntry = if (afterColon.isEmpty) {
              skipBlanksAndComments()
              if (lineIdx < lines.length && currentIndent > indent) {
                parseValue(currentIndent) match {
                  case Right(v) => (key, v)
                  case left     => return left
                }
              } else (key, Yaml.NullValue)
            } else (key, parseInlineValue(afterColon))

            val mapEntries = Chunk.newBuilder[(Yaml, Yaml)]
            mapEntries += firstEntry

            while (lineIdx < lines.length) {
              skipBlanksAndComments()
              if (lineIdx >= lines.length || currentIndent < dashContentIndent) {
                elements += Yaml.Mapping(mapEntries.result())
                return parseBlockSequenceContinue(indent, elements)
              }
              if (currentIndent != dashContentIndent) {
                elements += Yaml.Mapping(mapEntries.result())
                return parseBlockSequenceContinue(indent, elements)
              }

              val innerLine     = lines(lineIdx).trim
              val innerColonIdx = findMappingColon(innerLine)
              if (innerColonIdx <= 0) {
                elements += Yaml.Mapping(mapEntries.result())
                return parseBlockSequenceContinue(indent, elements)
              }

              val innerKeyStr     = innerLine.substring(0, innerColonIdx).trim
              val innerKey        = Yaml.Scalar(unquoteScalar(innerKeyStr))
              val innerAfterColon = innerLine.substring(innerColonIdx + 1).trim

              if (innerAfterColon.isEmpty) {
                lineIdx += 1
                skipBlanksAndComments()
                if (lineIdx < lines.length && currentIndent > dashContentIndent) {
                  parseValue(currentIndent) match {
                    case Right(v) => mapEntries += ((innerKey, v))
                    case left     => return left
                  }
                } else mapEntries += ((innerKey, Yaml.NullValue))
              } else {
                lineIdx += 1
                mapEntries += ((innerKey, parseInlineValue(innerAfterColon)))
              }
            }
            elements += Yaml.Mapping(mapEntries.result())
          } else {
            lineIdx += 1
            elements += parseInlineValue(afterDash)
          }
        }
      }
      Right(Yaml.Sequence(elements.result()))
    }

    private def parseLiteralBlock(indent: Int): Either[YamlError, Yaml] = {
      val line      = lines(lineIdx).trim
      val isLiteral = line.charAt(0) == '|'
      lineIdx += 1
      parseLiteralBlockContent(indent, isLiteral)
    }

    private def parseLiteralBlockContent(indent: Int, isLiteral: Boolean): Either[YamlError, Yaml] = {
      skipBlanksAndComments()
      if (lineIdx >= lines.length) Right(Yaml.Scalar(""))
      else {
        val blockIndent = currentIndent
        if (blockIndent <= indent) Right(Yaml.Scalar(""))
        else {
          val sb    = new StringBuilder
          var first = true
          while (lineIdx < lines.length) {
            val raw = lines(lineIdx)
            if (raw.trim.isEmpty) {
              sb.append('\n')
              lineIdx += 1
            } else {
              val ci = countLeadingSpaces(raw)
              if (ci < blockIndent) {
                val result        = sb.toString
                val trimmedResult = if (result.endsWith("\n")) result.substring(0, result.length - 1) else result
                return Right(Yaml.Scalar(trimmedResult))
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
          val result        = sb.toString
          val trimmedResult = if (result.endsWith("\n")) result.substring(0, result.length - 1) else result
          Right(Yaml.Scalar(trimmedResult))
        }
      }
    }

    private def parseFlowMapping(): Either[YamlError, Yaml] = {
      val line    = lines(lineIdx)
      val trimmed = line.trim
      lineIdx += 1

      if (trimmed == "{}") Right(Yaml.Mapping.empty)
      else {
        val content = collectFlowContent(trimmed, '{', '}')
        val inner   = content.substring(1, content.length - 1).trim
        if (inner.isEmpty) Right(Yaml.Mapping.empty)
        else {
          val entries = Chunk.newBuilder[(Yaml, Yaml)]
          val parts   = splitFlowItems(inner)
          var i       = 0
          while (i < parts.length) {
            val part     = parts(i).trim
            val colonIdx = part.indexOf(':')
            if (colonIdx > 0) {
              val k = unquoteScalar(part.substring(0, colonIdx).trim)
              val v = part.substring(colonIdx + 1).trim
              entries += ((Yaml.Scalar(k), parseInlineValue(v)))
            }
            i += 1
          }
          Right(Yaml.Mapping(entries.result()))
        }
      }
    }

    private def parseFlowSequence(): Either[YamlError, Yaml] = {
      val line    = lines(lineIdx)
      val trimmed = line.trim
      lineIdx += 1

      if (trimmed == "[]") Right(Yaml.Sequence.empty)
      else {
        val content = collectFlowContent(trimmed, '[', ']')
        val inner   = content.substring(1, content.length - 1).trim
        if (inner.isEmpty) Right(Yaml.Sequence.empty)
        else {
          val elements = Chunk.newBuilder[Yaml]
          val parts    = splitFlowItems(inner)
          var i        = 0
          while (i < parts.length) {
            elements += parseInlineValue(parts(i).trim)
            i += 1
          }
          Right(Yaml.Sequence(elements.result()))
        }
      }
    }

    private def collectFlowContent(start: String, open: Char, close: Char): String = {
      var depth = 0
      var i     = 0
      val s     = start
      while (i < s.length) {
        if (s.charAt(i) == open) depth += 1
        else if (s.charAt(i) == close) depth -= 1
        i += 1
      }
      if (depth == 0) s
      else {
        val sb = new StringBuilder(s)
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

    private def splitFlowItems(s: String): Array[String] = {
      val result = new java.util.ArrayList[String]()
      var depth  = 0
      var start  = 0
      var i      = 0
      while (i < s.length) {
        val c = s.charAt(i)
        if (c == '{' || c == '[') depth += 1
        else if (c == '}' || c == ']') depth -= 1
        else if (c == ',' && depth == 0) {
          result.add(s.substring(start, i))
          start = i + 1
        }
        i += 1
      }
      if (start < s.length) result.add(s.substring(start))
      val arr = new Array[String](result.size)
      result.toArray(arr)
      arr
    }

    private def parseScalar(@scala.annotation.unused indent: Int): Either[YamlError, Yaml] = {
      val line    = lines(lineIdx)
      val trimmed = line.trim
      lineIdx += 1
      Right(parseInlineValue(trimmed))
    }

    private def parseInlineValue(s: String): Yaml = {
      val trimmed = stripComment(s).trim
      if (trimmed.isEmpty || trimmed == "null" || trimmed == "~") Yaml.NullValue
      else if (trimmed.startsWith("'")) Yaml.Scalar(unquoteSingle(trimmed))
      else if (trimmed.startsWith("\"")) Yaml.Scalar(unquoteDouble(trimmed))
      else if (trimmed.startsWith("{")) {
        parseFlowMappingInline(trimmed)
      } else if (trimmed.startsWith("[")) {
        parseFlowSequenceInline(trimmed)
      } else Yaml.Scalar(trimmed)
    }

    private def parseFlowMappingInline(s: String): Yaml =
      if (s == "{}") Yaml.Mapping.empty
      else {
        val inner = s.substring(1, s.length - 1).trim
        if (inner.isEmpty) Yaml.Mapping.empty
        else {
          val entries = Chunk.newBuilder[(Yaml, Yaml)]
          val parts   = splitFlowItems(inner)
          var i       = 0
          while (i < parts.length) {
            val part     = parts(i).trim
            val colonIdx = part.indexOf(':')
            if (colonIdx > 0) {
              val k = unquoteScalar(part.substring(0, colonIdx).trim)
              val v = part.substring(colonIdx + 1).trim
              entries += ((Yaml.Scalar(k), parseInlineValue(v)))
            }
            i += 1
          }
          Yaml.Mapping(entries.result())
        }
      }

    private def parseFlowSequenceInline(s: String): Yaml =
      if (s == "[]") Yaml.Sequence.empty
      else {
        val inner = s.substring(1, s.length - 1).trim
        if (inner.isEmpty) Yaml.Sequence.empty
        else {
          val elements = Chunk.newBuilder[Yaml]
          val parts    = splitFlowItems(inner)
          var i        = 0
          while (i < parts.length) {
            elements += parseInlineValue(parts(i).trim)
            i += 1
          }
          Yaml.Sequence(elements.result())
        }
      }

    private def stripComment(s: String): String = {
      var inSingle = false
      var inDouble = false
      var i        = 0
      while (i < s.length) {
        val c = s.charAt(i)
        if (c == '\\' && inDouble && i + 1 < s.length) {
          i += 2
        } else {
          if (c == '\'' && !inDouble) inSingle = !inSingle
          else if (c == '"' && !inSingle) inDouble = !inDouble
          else if (c == '#' && !inSingle && !inDouble && i > 0 && s.charAt(i - 1) == ' ') {
            return s.substring(0, i).trim
          }
          i += 1
        }
      }
      s
    }

    private def unquoteScalar(s: String): String =
      if (s.startsWith("'") && s.endsWith("'") && s.length >= 2) unquoteSingle(s)
      else if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) unquoteDouble(s)
      else s

    private def unquoteSingle(s: String): String = {
      val inner = s.substring(1, s.length - 1)
      inner.replace("''", "'")
    }

    private def unquoteDouble(s: String): String = {
      val inner = s.substring(1, s.length - 1)
      val sb    = new StringBuilder
      var i     = 0
      while (i < inner.length) {
        if (inner.charAt(i) == '\\' && i + 1 < inner.length) {
          inner.charAt(i + 1) match {
            case 'n'                         => sb.append('\n')
            case 't'                         => sb.append('\t')
            case 'r'                         => sb.append('\r')
            case '\\'                        => sb.append('\\')
            case '"'                         => sb.append('"')
            case '/'                         => sb.append('/')
            case 'b'                         => sb.append('\b')
            case 'a'                         => sb.append('\u0007')
            case '0'                         => sb.append('\u0000')
            case 'u' if i + 5 < inner.length =>
              val hex = inner.substring(i + 2, i + 6)
              try {
                sb.append(Integer.parseInt(hex, 16).toChar)
                i += 4
              } catch {
                case _: NumberFormatException =>
                  sb.append('\\')
                  sb.append('u')
              }
            case 'x' if i + 3 < inner.length =>
              val hex = inner.substring(i + 2, i + 4)
              try {
                sb.append(Integer.parseInt(hex, 16).toChar)
                i += 2
              } catch {
                case _: NumberFormatException =>
                  sb.append('\\')
                  sb.append('x')
              }
            case other => sb.append('\\'); sb.append(other)
          }
          i += 2
        } else {
          sb.append(inner.charAt(i))
          i += 1
        }
      }
      sb.toString
    }
  }
}
