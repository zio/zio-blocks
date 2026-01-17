package zio.blocks.schema.json.interpolators

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicOptic.Node
import scala.collection.mutable.ListBuffer

object PathParser {
  
  def parse(path: String): Either[String, DynamicOptic] = {
    if (path.isEmpty) Right(DynamicOptic.root)
    else {
      var i = 0
      val len = path.length
      val nodes = ListBuffer[Node]()
      
      try {
        while (i < len) {
          val c = path.charAt(i)
          if (c == '.') {
            i += 1
            if (i >= len) throw new Exception("Unexpected end of path after dot")
            val start = i
            while (i < len && path.charAt(i) != '.' && path.charAt(i) != '[') {
              i += 1
            }
            if (i == start) throw new Exception("Empty field name")
            nodes += Node.Field(path.substring(start, i))
          } else if (c == '[') {
            i += 1
            val start = i
            while (i < len && path.charAt(i) != ']') {
              i += 1
            }
            if (i >= len) throw new Exception("Unclosed bracket")
            val content = path.substring(start, i)
            i += 1 // skip ]
            
            if (content == "*") {
              nodes += Node.Elements
            } else if ((content.startsWith("'") && content.endsWith("'")) || (content.startsWith("\"") && content.endsWith("\""))) {
              if (content.length < 2) throw new Exception("Empty quoted key")
              val key = content.substring(1, content.length - 1)
              nodes += Node.Field(key)
            } else {
              try {
                nodes += Node.AtIndex(content.toInt)
              } catch {
                case _: NumberFormatException => throw new Exception(s"Invalid index or unquoted key: $content")
              }
            }
          } else {
            // Field at start without dot
            if (nodes.nonEmpty) throw new Exception(s"Unexpected character '$c' at $i")
            val start = i
            while (i < len && path.charAt(i) != '.' && path.charAt(i) != '[') {
              i += 1
            }
            nodes += Node.Field(path.substring(start, i))
          }
        }
        Right(new DynamicOptic(nodes.toVector))
      } catch {
        case e: Exception => Left(e.getMessage)
      }
    }
  }

  def parseUnsafe(path: String): DynamicOptic = 
    parse(path).fold(err => throw new IllegalArgumentException(s"Invalid path '$path': $err"), identity)
}
