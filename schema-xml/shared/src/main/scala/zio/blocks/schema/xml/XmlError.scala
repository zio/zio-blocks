package zio.blocks.schema.xml

import zio.blocks.schema.DynamicOptic

/**
 * A custom exception class without stack trace representing errors that occur
 * during XML codec operations.
 *
 * @param spans
 *   A list of DynamicOptic.Node objects representing the traversal path within
 *   the data structure where the error occurred.
 * @param message
 *   A descriptive message providing additional context about the error.
 * @param line
 *   Optional line number where the error occurred (for parse errors).
 * @param column
 *   Optional column number where the error occurred (for parse errors).
 */
class XmlError(
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

  /** Returns the path as a DynamicOptic. */
  def path: DynamicOptic = DynamicOptic(spans.reverse.toIndexedSeq)

  /** Creates a new XmlError with an additional span prepended. */
  def atSpan(span: DynamicOptic.Node): XmlError =
    new XmlError(span :: spans, message, line, column)
}

object XmlError {

  /** Creates an XmlError with just a message. */
  def apply(message: String): XmlError = new XmlError(Nil, message)

  /** Creates an XmlError with a message and path. */
  def apply(message: String, spans: List[DynamicOptic.Node]): XmlError =
    new XmlError(spans, message)

  /** Creates an XmlError with a message and line/column position. */
  def apply(message: String, line: Int, column: Int): XmlError =
    new XmlError(Nil, message, Some(line), Some(column))

  /** Parse error - malformed XML. */
  def parseError(message: String, line: Int, column: Int): XmlError =
    new XmlError(Nil, s"Parse error: $message", Some(line), Some(column))

  /** Validation error - invalid content. */
  def validationError(message: String): XmlError =
    new XmlError(Nil, s"Validation error: $message")

  /** Encoding error - cannot encode value. */
  def encodingError(message: String): XmlError =
    new XmlError(Nil, s"Encoding error: $message")

  /** Selection error - path not found. */
  def selectionError(message: String): XmlError =
    new XmlError(Nil, s"Selection error: $message")

  /** Patch error - patch operation failed. */
  def patchError(message: String): XmlError =
    new XmlError(Nil, s"Patch error: $message")
}
