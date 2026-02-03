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
 */
case class JsonError(message: String, path: DynamicOptic) extends Exception with NoStackTrace {

  /**
   * Combines this error with another, preserving both error messages.
   */
  def ++(other: JsonError): JsonError = copy(message = s"${this.message}; ${other.message}")

  /**
   * Creates a new JsonError with the path prepended with a field access.
   */
  def atField(name: String): JsonError = copy(path = DynamicOptic.root.field(name)(path))

  /**
   * Creates a new JsonError with the path prepended with an index access.
   */
  def atIndex(index: Int): JsonError = copy(path = DynamicOptic.root.at(index)(path))

  override def getMessage: String = toString

  override def toString: String =
    if (path.nodes.isEmpty) message
    else s"$message at: ${path.toString}"
}

object JsonError {

  /**
   * Creates a JsonError with just a message, at the root path.
   */
  def apply(message: String): JsonError = new JsonError(message, DynamicOptic.root)

  /**
   * Converts a [[SchemaError]] to a [[JsonError]].
   */
  def fromSchemaError(error: SchemaError): JsonError = JsonError(error.message, error.errors.head.source)

  /**
   * Creates a JsonError from an existing JsonBinaryCodecError for backward
   * compatibility.
   */
  def fromJsonBinaryCodecError(error: JsonBinaryCodecError): JsonError = {
    val path = error.spans.foldRight(DynamicOptic.root)((node, acc) => new DynamicOptic(node +: acc.nodes))
    new JsonError(error.getMessage, path)
  }
}
