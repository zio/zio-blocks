package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, SchemaError}

import scala.util.control.NoStackTrace

/**
 * Represents an error that occurred during JSON parsing, encoding, patching, or processing.
 *
 * @param message A human-readable description of the error
 * @param path The location in the JSON structure where the error occurred,
 *             represented as a [[DynamicOptic]]
 * @param offset Optional byte offset in the input where the error occurred
 * @param line Optional 1-indexed line number where the error occurred
 * @param column Optional 1-indexed column number where the error occurred
 */
final case class JsonError(
  message: String,
  path: DynamicOptic,
  offset: Option[Long],
  line: Option[Int],
  column: Option[Int]
) extends Exception
    with NoStackTrace {

  override def getMessage: String = {
    val posInfo = (line, column) match {
      case (Some(l), Some(c)) => s" at line $l, column $c"
      case _                  => offset.map(o => s" at offset $o").getOrElse("")
    }
    val pathInfo = if (path.nodes.isEmpty) "" else s" at path $path"
    s"$message$pathInfo$posInfo"
  }

  /**
   * Combines this error with another, preserving both error messages.
   */
  def ++(other: JsonError): JsonError =
    JsonError(s"${this.message}; ${other.message}", this.path, this.offset, this.line, this.column)
}

object JsonError {

  /**
   * Creates a JsonError with only a message, using root path and no position info.
   */
  def apply(message: String): JsonError =
    JsonError(message, DynamicOptic.root, None, None, None)

  /**
   * Creates a JsonError with a message and path, no position info.
   */
  def apply(message: String, path: DynamicOptic): JsonError =
    JsonError(message, path, None, None, None)

  /**
   * Converts a [[SchemaError]] to a [[JsonError]].
   */
  def fromSchemaError(error: SchemaError): JsonError =
    JsonError(error.message, DynamicOptic.root, None, None, None)

  /**
   * Creates an error for a type mismatch.
   */
  def typeMismatch(path: DynamicOptic, expected: String, actual: String): JsonError =
    JsonError(s"Expected $expected but got $actual", path, None, None, None)

  /**
   * Creates an error for a missing field.
   */
  def missingField(path: DynamicOptic, fieldName: String): JsonError =
    JsonError(s"Missing field: $fieldName", path, None, None, None)

  /**
   * Creates an error for an index out of bounds.
   */
  def indexOutOfBounds(path: DynamicOptic, index: Int, length: Int): JsonError =
    JsonError(s"Index $index out of bounds for array of length $length", path, None, None, None)

  /**
   * Creates an error for a key not found.
   */
  def keyNotFound(path: DynamicOptic, key: String): JsonError =
    JsonError(s"Key not found: $key", path, None, None, None)

  /**
   * Creates an error for an invalid operation.
   */
  def invalidOperation(path: DynamicOptic, message: String): JsonError =
    JsonError(message, path, None, None, None)
}
