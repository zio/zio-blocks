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
 */
class XmlError(var spans: List[DynamicOptic.Node], message: String) extends Throwable(message, null, false, false) {
  override def getMessage: String = message

  /** Returns the path as a DynamicOptic. */
  def path: DynamicOptic = DynamicOptic(spans.reverse.toIndexedSeq)

  /** Creates a new XmlError with an additional span prepended. */
  def atSpan(span: DynamicOptic.Node): XmlError = {
    spans = span :: spans
    this
  }
}

object XmlError {

  /** Creates an XmlError with just a message. */
  def apply(message: String): XmlError = new XmlError(Nil, message)

  /** Creates an XmlError with a message and path. */
  def apply(message: String, spans: List[DynamicOptic.Node]): XmlError =
    new XmlError(spans, message)

  /** Parse error - malformed XML. */
  def parseError(message: String, line: Int, column: Int): XmlError =
    new XmlError(Nil, s"Parse error at line $line, column $column: $message")

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
