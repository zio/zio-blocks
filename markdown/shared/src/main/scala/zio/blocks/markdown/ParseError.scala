package zio.blocks.markdown

final case class ParseError(
  message: String,
  line: Int,
  column: Int,
  input: String
) extends Product
    with Serializable {

  override def toString: String =
    s"ParseError at line $line, column $column: $message\n  $input"
}
