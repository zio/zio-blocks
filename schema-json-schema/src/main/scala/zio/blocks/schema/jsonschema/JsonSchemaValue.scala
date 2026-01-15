package zio.blocks.schema.jsonschema

/**
 * Represents a JSON Schema value that can be serialized to JSON.
 * This is a simplified representation of JSON Schema Draft 2020-12.
 */
sealed trait JsonSchemaValue {
  def toJson: String = JsonSchemaValue.toJson(this)

  def toPrettyJson: String = JsonSchemaValue.toPrettyJson(this, 0)
}

object JsonSchemaValue {
  case object Null extends JsonSchemaValue

  case class Bool(value: Boolean) extends JsonSchemaValue

  case class Num(value: BigDecimal) extends JsonSchemaValue

  case class Str(value: String) extends JsonSchemaValue

  case class Arr(values: IndexedSeq[JsonSchemaValue]) extends JsonSchemaValue

  case class Obj(fields: IndexedSeq[(String, JsonSchemaValue)]) extends JsonSchemaValue {
    def ++(other: Obj): Obj = Obj(fields ++ other.fields)

    def +(field: (String, JsonSchemaValue)): Obj = Obj(fields :+ field)
  }

  object Obj {
    val empty: Obj = Obj(IndexedSeq.empty)

    def apply(fields: (String, JsonSchemaValue)*): Obj = new Obj(fields.toIndexedSeq)
  }

  private def escapeString(s: String): String = {
    val sb = new StringBuilder()
    sb.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case _ =>
          if (c < 32) sb.append(f"\\u${c.toInt}%04x")
          else sb.append(c)
      }
      i += 1
    }
    sb.append('"')
    sb.toString
  }

  def toJson(value: JsonSchemaValue): String = value match {
    case Null       => "null"
    case Bool(v)    => v.toString
    case Num(v)     => v.toString
    case Str(v)     => escapeString(v)
    case Arr(vs)    => vs.map(toJson).mkString("[", ",", "]")
    case Obj(fields) =>
      fields.map { case (k, v) => s"${escapeString(k)}:${toJson(v)}" }.mkString("{", ",", "}")
  }

  def toPrettyJson(value: JsonSchemaValue, indent: Int): String = {
    val pad = "  " * indent
    val padInner = "  " * (indent + 1)
    value match {
      case Null       => "null"
      case Bool(v)    => v.toString
      case Num(v)     => v.toString
      case Str(v)     => escapeString(v)
      case Arr(vs) if vs.isEmpty => "[]"
      case Arr(vs)    =>
        vs.map(v => padInner + toPrettyJson(v, indent + 1)).mkString("[\n", ",\n", s"\n$pad]")
      case Obj(fields) if fields.isEmpty => "{}"
      case Obj(fields) =>
        fields.map { case (k, v) =>
          s"$padInner${escapeString(k)}: ${toPrettyJson(v, indent + 1)}"
        }.mkString("{\n", ",\n", s"\n$pad}")
    }
  }
}
