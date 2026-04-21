package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

import scala.util.control.NoStackTrace

/**
 * An error that occurred during migration execution.
 *
 * All errors capture the path at which the failure occurred, enabling
 * diagnostics such as: "Failed to apply TransformValue at
 * `.addresses.each.streetNumber`"
 */
sealed trait MigrationError extends Exception with NoStackTrace {
  def path: DynamicOptic
  def details: String

  override def getMessage: String = {
    val pathStr = path.toString
    if (pathStr == ".") details
    else s"$details (at $pathStr)"
  }
}

object MigrationError {

  /** A field that was expected to exist was not found. */
  final case class MissingField(path: DynamicOptic, fieldName: String) extends MigrationError {
    def details: String = s"Missing field '$fieldName'"
  }

  /** A field that should not exist was found. */
  final case class UnexpectedField(path: DynamicOptic, fieldName: String) extends MigrationError {
    def details: String = s"Unexpected field '$fieldName'"
  }

  /** The value at the path has the wrong type. */
  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def details: String = s"Type mismatch: expected $expected, got $actual"
  }

  /** A required default value was not available. */
  final case class MissingDefault(path: DynamicOptic, fieldName: String) extends MigrationError {
    def details: String = s"No default value for field '$fieldName'"
  }

  /** A variant case was not found. */
  final case class UnknownCase(path: DynamicOptic, caseName: String) extends MigrationError {
    def details: String = s"Unknown variant case '$caseName'"
  }

  /** A conversion between types failed. */
  final case class ConversionFailed(path: DynamicOptic, from: String, to: String, reason: String)
      extends MigrationError {
    def details: String = s"Cannot convert $from to $to: $reason"
  }

  /** A generic migration failure. */
  final case class Failed(path: DynamicOptic, details: String) extends MigrationError

  /** Creates a Failed error at the root path. */
  def failed(details: String): MigrationError =
    Failed(DynamicOptic.root, details)

  /** Creates a Failed error at the given path. */
  def failed(path: DynamicOptic, details: String): MigrationError =
    Failed(path, details)
}
