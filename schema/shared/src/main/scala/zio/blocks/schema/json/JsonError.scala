package zio.blocks.schema.json

import scala.util.control.NoStackTrace
import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * Represents an error that occurred during JSON parsing or processing.
 *
 * Extends Exception with NoStackTrace for efficient throwing in unsafe methods
 * without the overhead of stack trace generation.
 *
 * @param message
 *   A descriptive message explaining the error.
 * @param path
 *   The path in the JSON structure where the error occurred, represented as a
 *   `DynamicOptic`.
 * @param offset
 *   The byte offset in the input where the error occurred, if available.
 * @param line
 *   The line number (1-based) where the error occurred, if available.
 * @param column
 *   The column number (1-based) where the error occurred, if available.
 */
final case class JsonError(
  message: String,
  path: DynamicOptic,
  offset: Option[Long] = None,
  line: Option[Int] = None,
  column: Option[Int] = None
) extends Exception with NoStackTrace {

  /**
   * Combines this error with another, preserving both error messages.
   */
  def ++(other: JsonError): JsonError =
    copy(message = s"${this.message}; ${other.message}")

  /**
   * Creates a new JsonError with the path prepended with a field access.
   */
  def atField(name: String): JsonError =
    copy(path = DynamicOptic.root.field(name)(path))

  /**
   * Creates a new JsonError with the path prepended with an index access.
   */
  def atIndex(index: Int): JsonError =
    copy(path = DynamicOptic.root.at(index)(path))

  override def getMessage: String = toString

  override def toString: String = {
    val locationInfo = (line, column) match {
      case (Some(l), Some(c)) => s" at line $l, column $c"
      case (Some(l), None)    => s" at line $l"
      case _                  => offset.fold("")(o => s" at offset $o")
    }
    val pathStr = if (path.nodes.isEmpty) "" else s" at path ${path.toString}"
    s"JsonError: $message$pathStr$locationInfo"
  }
}

object JsonError {

  /**
   * Creates a JsonError with just a message, at the root path.
   */
  def apply(message: String): JsonError =
    JsonError(message, DynamicOptic.root, None, None, None)

  /**
   * Creates a JsonError with a message and path.
   */
  def apply(message: String, path: DynamicOptic): JsonError =
    JsonError(message, path, None, None, None)

  /**
   * Creates a JsonError from a message and location information.
   */
  def apply(message: String, offset: Long, line: Int, column: Int): JsonError =
    JsonError(message, DynamicOptic.root, Some(offset), Some(line), Some(column))

  /**
   * Converts a [[SchemaError]] to a [[JsonError]].
   */
  def fromSchemaError(error: SchemaError): JsonError =
    JsonError(error.message, DynamicOptic.root, None, None, None)

  /**
   * Creates a JsonError from an existing JsonBinaryCodecError for backward compatibility.
   */
  def fromJsonBinaryCodecError(error: JsonBinaryCodecError): JsonError = {
    val path = error.spans.foldRight(DynamicOptic.root) { (node, acc) =>
      new DynamicOptic(node +: acc.nodes)
    }
    JsonError(error.getMessage, path, None, None, None)
  }
}
