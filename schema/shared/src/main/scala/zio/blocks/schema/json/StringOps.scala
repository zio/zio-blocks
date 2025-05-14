package zio.blocks.schema.json

object StringOps {

  implicit private[json] class StringEx(val self: String) extends AnyVal {
    final def escape: String = self.flatMap {
      case '"'          => "\\\""
      case '\\'         => "\\\\"
      case '\b'         => "\\b"
      case '\f'         => "\\f"
      case '\n'         => "\\n"
      case '\r'         => "\\r"
      case '\t'         => "\\t"
      case c if c < ' ' => "\\u%04x".format(c.toInt)
      case c            => c.toString
    }
  }
}
