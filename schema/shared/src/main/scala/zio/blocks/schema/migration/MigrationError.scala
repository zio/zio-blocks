package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

import scala.util.control.NoStackTrace

/**
 * Represents a failure during migration execution. Captures the path where the
 * error occurred so diagnostics can pinpoint which field or nested structure
 * caused the problem.
 */
final case class MigrationError(message: String, path: DynamicOptic) extends Exception with NoStackTrace {
  override def getMessage: String =
    if (path.nodes.isEmpty) message
    else s"$message at ${path.toScalaString}"

  def atField(name: String): MigrationError =
    copy(path = DynamicOptic.root.field(name)(path))

  def atCase(name: String): MigrationError =
    copy(path = DynamicOptic.root.caseOf(name)(path))
}

object MigrationError {
  def apply(message: String): MigrationError = new MigrationError(message, DynamicOptic.root)

  def atPath(message: String, path: DynamicOptic): MigrationError = new MigrationError(message, path)
}
