package zio.blocks.schema.yaml

import zio.blocks.schema.DynamicOptic

class YamlError(
  val spans: List[DynamicOptic.Node],
  message: String,
  val line: Option[Int] = None,
  val column: Option[Int] = None
) extends Throwable(message, null, false, false) {
  override def getMessage: String = (line, column) match {
    case (Some(l), Some(c)) => s"$message (at line $l, column $c)"
    case (Some(l), None)    => s"$message (at line $l)"
    case _                  => message
  }

  def path: DynamicOptic = DynamicOptic(spans.reverse.toIndexedSeq)

  def atSpan(span: DynamicOptic.Node): YamlError =
    new YamlError(span :: spans, message, line, column)
}

object YamlError {

  def apply(message: String): YamlError = new YamlError(Nil, message)

  def apply(message: String, spans: List[DynamicOptic.Node]): YamlError =
    new YamlError(spans, message)

  def apply(message: String, line: Int, column: Int): YamlError =
    new YamlError(Nil, message, Some(line), Some(column))

  def parseError(message: String, line: Int, column: Int): YamlError =
    new YamlError(Nil, s"Parse error: $message", Some(line), Some(column))

  def validationError(message: String): YamlError =
    new YamlError(Nil, s"Validation error: $message")

  def encodingError(message: String): YamlError =
    new YamlError(Nil, s"Encoding error: $message")
}
