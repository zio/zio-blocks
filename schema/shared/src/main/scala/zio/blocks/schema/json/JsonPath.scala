package zio.blocks.schema.json

/**
 * Represents a path into a JSON structure for navigation and manipulation.
 * 
 * JsonPath is used by JsonPatch and for general JSON navigation.
 */
sealed trait JsonPath { self =>
  
  /**
   * Navigate to the value at this path in the given JSON.
   */
  def navigate(json: Json): Option[Json] = self match {
    case JsonPath.Root => Some(json)
    case JsonPath.Field(parent, name) =>
      parent.navigate(json).flatMap {
        case Json.Obj(fields) => fields.get(name)
        case _ => None
      }
    case JsonPath.Index(parent, idx) =>
      parent.navigate(json).flatMap {
        case Json.Arr(elements) =>
          if (idx >= 0 && idx < elements.length) Some(elements(idx))
          else None
        case _ => None
      }
  }
  
  /**
   * Update the value at this path in the given JSON.
   */
  def update(json: Json, value: Json): Option[Json] = self match {
    case JsonPath.Root => Some(value)
    case JsonPath.Field(parent, name) =>
      parent.navigate(json).flatMap {
        case Json.Obj(fields) =>
          parent.update(json, Json.Obj(fields.updated(name, value)))
        case _ => None
      }
    case JsonPath.Index(parent, idx) =>
      parent.navigate(json).flatMap {
        case Json.Arr(elements) =>
          if (idx >= 0 && idx < elements.length) {
            parent.update(json, Json.Arr(elements.updated(idx, value)))
          } else None
        case _ => None
      }
  }
  
  /**
   * Delete the value at this path in the given JSON.
   */
  def delete(json: Json): Option[Json] = self match {
    case JsonPath.Root => None // Can't delete root
    case JsonPath.Field(parent, name) =>
      parent.navigate(json).flatMap {
        case Json.Obj(fields) =>
          parent.update(json, Json.Obj(fields - name))
        case _ => None
      }
    case JsonPath.Index(parent, idx) =>
      parent.navigate(json).flatMap {
        case Json.Arr(elements) =>
          if (idx >= 0 && idx < elements.length) {
            val (before, after) = elements.splitAt(idx)
            parent.update(json, Json.Arr(before ++ after.tail))
          } else None
        case _ => None
      }
  }
  
  /**
   * Append a field access to this path.
   */
  def /(field: String): JsonPath = JsonPath.Field(self, field)
  
  /**
   * Append an index access to this path.
   */
  def apply(index: Int): JsonPath = JsonPath.Index(self, index)
  
  /**
   * Convert to a JSON Pointer string (RFC 6901).
   */
  def toPointer: String = {
    def loop(path: JsonPath, acc: List[String]): List[String] = path match {
      case JsonPath.Root => acc
      case JsonPath.Field(parent, name) => 
        loop(parent, escape(name) :: acc)
      case JsonPath.Index(parent, idx) => 
        loop(parent, idx.toString :: acc)
    }
    
    val tokens = loop(self, Nil)
    if (tokens.isEmpty) "" else tokens.mkString("/", "/", "")
  }
  
  private def escape(s: String): String = 
    s.replace("~", "~0").replace("/", "~1")
}

object JsonPath {
  
  /**
   * Root path.
   */
  case object Root extends JsonPath
  
  /**
   * Field access path.
   */
  final case class Field(parent: JsonPath, name: String) extends JsonPath
  
  /**
   * Array index access path.
   */
  final case class Index(parent: JsonPath, index: Int) extends JsonPath
  
  /**
   * Root path singleton.
   */
  val root: JsonPath = Root
  
  /**
   * Parse a JSON Pointer string (RFC 6901) into a JsonPath.
   */
  def fromPointer(pointer: String): Either[String, JsonPath] = {
    if (pointer.isEmpty || pointer == "/") {
      Right(Root)
    } else if (!pointer.startsWith("/")) {
      Left(s"JSON Pointer must start with '/': $pointer")
    } else {
      val tokens = pointer.substring(1).split("/", -1)
      tokens.foldLeft[Either[String, JsonPath]](Right(Root)) { (acc, token) =>
        acc.flatMap { path =>
          val unescaped = unescape(token)
          // Try to parse as integer for array index
          try {
            val idx = unescaped.toInt
            Right(Index(path, idx))
          } catch {
            case _: NumberFormatException =>
              Right(Field(path, unescaped))
          }
        }
      }
    }
  }
  
  private def unescape(s: String): String =
    s.replace("~1", "/").replace("~0", "~")
}
