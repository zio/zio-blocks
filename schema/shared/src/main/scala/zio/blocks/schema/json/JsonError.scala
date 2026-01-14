package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * Represents an error that occurred during JSON parsing, encoding, or
 * manipulation.
 *
 * @param message
 *   A descriptive error message
 * @param path
 *   The path within the JSON structure where the error occurred
 * @param offset
 *   The byte offset in the input where the error occurred (-1 if unknown)
 * @param line
 *   The line number where the error occurred (-1 if unknown)
 * @param column
 *   The column number where the error occurred (-1 if unknown)
 */
final case class JsonError(
  message: String,
  path: DynamicOptic = DynamicOptic.root,
  offset: Long = -1L,
  line: Long = -1L,
  column: Long = -1L
) extends Exception(message)
    with Product
    with Serializable {

  override def getMessage: String = {
    val sb = new StringBuilder(message)

    val pathStr = path.toString
    if (pathStr != ".") {
      sb.append(" at path: ").append(pathStr)
    }

    if (line >= 0 && column >= 0) {
      sb.append(" (line ").append(line).append(", column ").append(column).append(")")
    } else if (offset >= 0) {
      sb.append(" (offset ").append(offset).append(")")
    }

    sb.toString
  }

  /**
   * Combines this error with another, creating a composite error message.
   */
  def ++(that: JsonError): JsonError =
    JsonError(
      message = s"${this.message}; ${that.message}",
      path = this.path,
      offset = if (this.offset >= 0) this.offset else that.offset,
      line = if (this.line >= 0) this.line else that.line,
      column = if (this.column >= 0) this.column else that.column
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
  def parseError(message: String, offset: Long = -1L, line: Long = -1L, column: Long = -1L): JsonError =
    JsonError(message, DynamicOptic.root, offset, line, column)

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
    JsonError(error.message, DynamicOptic.root)
}
