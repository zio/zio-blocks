package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Simple JSON parser for the shared module.
 * 
 * This is a minimal implementation that handles standard JSON parsing
 * without platform-specific dependencies.
 */
object JsonParser {
  
  def parseString(s: String): Either[JsonError, Json] = {
    try {
      Right(parseJson(s.trim))
    } catch {
      case e: JsonError => Left(e)
      case e: Throwable => 
        Left(JsonError(s"Parse error: ${e.getMessage}", DynamicOptic.root, None, None, None))
    }
  }
  
  private def parseJson(s: String): Json = {
    if (s.isEmpty) {
      throw JsonError("Empty input", DynamicOptic.root)
    } else if (s.startsWith("{")) {
      parseObject(s)
    } else if (s.startsWith("[")) {
      parseArray(s)
    } else if (s.startsWith("\"")) {
      Json.String(parseStringLiteral(s))
    } else if (s == "true") {
      Json.Boolean(true)
    } else if (s == "false") {
      Json.Boolean(false)
    } else if (s == "null") {
      Json.Null
    } else {
      parseNumber(s)
    }
  }
  
  private def parseObject(s: String): Json.Object = {
    if (!s.startsWith("{") || !s.endsWith("}")) {
      throw JsonError("Invalid object format", DynamicOptic.root)
    }
    
    val content = s.substring(1, s.length - 1).trim
    if (content.isEmpty) {
      return Json.Object.empty
    }
    
    val fields = parseObjectContent(content)
    Json.Object(fields.toVector)
  }
  
  private def parseObjectContent(s: String): List[(String, Json)] = {
    var result = List.empty[(String, Json)]
    var current = s
    
    while (current.nonEmpty) {
      val commaIndex = findCommaAtDepth(current, 0)
      val part = if (commaIndex == -1) current else current.substring(0, commaIndex)
      
      val colonIndex = findColonAtDepth(part, 0)
      if (colonIndex == -1) {
        throw JsonError("Invalid object field", DynamicOptic.root)
      }
      
      val key = parseStringLiteral(part.substring(0, colonIndex).trim)
      val valueStr = part.substring(colonIndex + 1).trim
      val value = parseJson(valueStr)
      
      result = result :+ (key -> value)
      
      if (commaIndex == -1) {
        current = ""
      } else {
        current = current.substring(commaIndex + 1).trim
      }
    }
    
    result
  }
  
  private def parseArray(s: String): Json.Array = {
    if (!s.startsWith("[") || !s.endsWith("]")) {
      throw JsonError("Invalid array format", DynamicOptic.root)
    }
    
    val content = s.substring(1, s.length - 1).trim
    if (content.isEmpty) {
      return Json.Array.empty
    }
    
    val elements = parseArrayContent(content)
    Json.Array(elements.toVector)
  }
  
  private def parseArrayContent(s: String): List[Json] = {
    var result = List.empty[Json]
    var current = s
    
    while (current.nonEmpty) {
      val commaIndex = findCommaAtDepth(current, 0)
      val part = if (commaIndex == -1) current else current.substring(0, commaIndex)
      val value = parseJson(part.trim)
      result = result :+ value
      
      if (commaIndex == -1) {
        current = ""
      } else {
        current = current.substring(commaIndex + 1).trim
      }
    }
    
    result
  }
  
  private def parseStringLiteral(s: String): String = {
    if (!s.startsWith("\"") || !s.endsWith("\"")) {
      throw JsonError("Invalid string format", DynamicOptic.root)
    }
    
    val content = s.substring(1, s.length - 1)
    unescapeString(content)
  }
  
  private def unescapeString(s: String): String = {
    val result = new StringBuilder()
    var i = 0
    while (i < s.length) {
      if (s.charAt(i) == '\\' && i + 1 < s.length) {
        s.charAt(i + 1) match {
          case '"' => result += '"'
          case '\\' => result += '\\'
          case '/' => result += '/'
          case 'b' => result += '\b'
          case 'f' => result += '\f'
          case 'n' => result += '\n'
          case 'r' => result += '\r'
          case 't' => result += '\t'
          case 'u' if i + 5 < s.length =>
            val hex = s.substring(i + 2, i + 6)
            try {
              val code = Integer.parseInt(hex, 16)
              result += code.toChar
              i += 4
            } catch {
              case _: NumberFormatException =>
                throw JsonError("Invalid Unicode escape", DynamicOptic.root)
            }
          case other => result += other
        }
        i += 2
      } else {
        result += s.charAt(i)
        i += 1
      }
    }
    result.toString
  }
  
  private def parseNumber(s: String): Json.Number = {
    if (!s.matches("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")) {
      throw JsonError("Invalid number format", DynamicOptic.root)
    }
    Json.Number(s)
  }
  
  private def findCommaAtDepth(s: String, initialDepth: Int): Int = {
    var depth = initialDepth
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case ',' if depth == 0 => return i
        case '{' | '[' => depth += 1
        case '}' | ']' => depth -= 1
        case '"' =>
          i += 1
          while (i < s.length && s.charAt(i) != '"') {
            if (s.charAt(i) == '\\' && i + 1 < s.length) i += 2
            else i += 1
          }
        case _ => // do nothing
      }
      i += 1
    }
    -1
  }
  
  private def findColonAtDepth(s: String, initialDepth: Int): Int = {
    var depth = initialDepth
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case ':' if depth == 0 => return i
        case '{' | '[' => depth += 1
        case '}' | ']' => depth -= 1
        case '"' =>
          i += 1
          while (i < s.length && s.charAt(i) != '"') {
            if (s.charAt(i) == '\\' && i + 1 < s.length) i += 2
            else i += 1
          }
        case _ => // do nothing
      }
      i += 1
    }
    -1
  }
}
