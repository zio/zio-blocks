package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, SchemaError}
import scala.util.control.NoStackTrace

/**
 * Represents an error that occurred during JSON parsing, encoding, or
 * processing.
 *
 * @param message
 *   A human-readable description of the error
 * @param path
 *   The location in the JSON structure where the error occurred
 * @param offset
 *   Optional byte offset in the input where the error occurred
 * @param line
 *   Optional 1-indexed line number where the error occurred
 * @param column
 *   Optional 1-indexed column number where the error occurred
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
    JsonError(
      message = s"${this.message}; ${other.message}",
      path = this.path,
      offset = this.offset.orElse(other.offset),
      line = this.line.orElse(other.line),
      column = this.column.orElse(other.column)
    )

  /**
   * Returns a new error with the given path prepended.
   */
  def atPath(newPath: DynamicOptic): JsonError =
    copy(path = newPath(path))

  /**
   * Returns a new error with the given field prepended to the path.
   */
  def atField(name: String): JsonError =
    atPath(DynamicOptic.root.field(name))

  /**
   * Returns a new error with the given index prepended to the path.
   */
  def atIndex(index: Int): JsonError =
    atPath(DynamicOptic.root.at(index))
}

object JsonError {

  /**
   * Creates an error with only a message, using root path and no position info.
   */
  def apply(message: String): JsonError =
    JsonError(message, DynamicOptic.root, None, None, None)

  /**
   * Creates an error with a message and path, no position info.
   */
  def apply(message: String, path: DynamicOptic): JsonError =
    JsonError(message, path, None, None, None)

  /**
   * Creates an error indicating an unexpected JSON value type.
   */
  def typeMismatch(expected: String, actual: String): JsonError =
    JsonError(s"Expected $expected but found $actual")

  /**
   * Creates an error indicating a missing field in a JSON object.
   */
  def missingField(name: String): JsonError =
    JsonError(s"Missing required field: $name")

  /**
   * Creates an error indicating a parsing failure.
   */
  def parseError(message: String, offset: Int, line: Int, column: Int): JsonError =
    JsonError(message, DynamicOptic.root, Some(offset.toLong), Some(line), Some(column))

  /**
   * Creates an error indicating an invalid JSON value.
   */
  def invalidValue(message: String): JsonError =
    JsonError(s"Invalid value: $message")

  /**
   * Creates an error indicating a null value was encountered where one was not
   * expected.
   */
  def unexpectedNull: JsonError =
    JsonError("Unexpected null value")

  /**
   * Creates an error indicating an index was out of bounds.
   */
  def indexOutOfBounds(index: Int, size: Int): JsonError =
    JsonError(s"Index $index out of bounds for array of size $size")

  /**
   * Creates an error indicating a key was not found in a JSON object.
   */
  def keyNotFound(key: String): JsonError =
    JsonError(s"Key not found: $key")

  /**
   * Converts a [[SchemaError]] to a [[JsonError]].
   */
  def fromSchemaError(error: SchemaError): JsonError =
    JsonError(error.message, DynamicOptic.root, None, None, None)
}
