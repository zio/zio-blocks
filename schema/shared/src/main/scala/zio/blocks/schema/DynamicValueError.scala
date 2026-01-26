package zio.blocks.schema

import scala.util.control.NoStackTrace

/**
 * Represents an error that occurred during DynamicValue processing.
 *
 * Extends Exception with NoStackTrace for efficient throwing in unsafe methods
 * without the overhead of stack trace generation.
 *
 * @param message
 *   A descriptive message explaining the error.
 * @param path
 *   The path in the DynamicValue structure where the error occurred,
 *   represented as a `DynamicOptic`.
 */
case class DynamicValueError(message: String, path: DynamicOptic) extends Exception with NoStackTrace {

  /**
   * Combines this error with another, preserving both error messages.
   */
  def ++(other: DynamicValueError): DynamicValueError =
    copy(message = s"${this.message}; ${other.message}")

  /**
   * Creates a new DynamicValueError with the path prepended with a field
   * access.
   */
  def atField(name: String): DynamicValueError =
    copy(path = DynamicOptic.root.field(name)(path))

  /**
   * Creates a new DynamicValueError with the path prepended with an index
   * access.
   */
  def atIndex(index: Int): DynamicValueError =
    copy(path = DynamicOptic.root.at(index)(path))

  /**
   * Creates a new DynamicValueError with the path prepended with a map key
   * access.
   */
  def atKey(key: DynamicValue): DynamicValueError =
    copy(path = new DynamicOptic(DynamicOptic.Node.AtMapKey(key) +: path.nodes))

  /**
   * Creates a new DynamicValueError with the path prepended with a variant
   * case.
   */
  def atCase(name: String): DynamicValueError =
    copy(path = DynamicOptic.root.caseOf(name)(path))

  override def getMessage: String = toString

  override def toString: String =
    if (path.nodes.isEmpty) message
    else s"$message at: ${path.toString}"
}

object DynamicValueError {

  /**
   * Creates a DynamicValueError with just a message, at the root path.
   */
  def apply(message: String): DynamicValueError =
    new DynamicValueError(message, DynamicOptic.root)
}
