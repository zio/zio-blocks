package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * Represents an error that occurred during migration execution or validation.
 *
 * @param message
 *   Human-readable description of the error
 * @param path
 *   The DynamicOptic path where the error occurred
 * @param actionIndex
 *   0-based position of the failing action in the action vector
 * @param action
 *   The MigrationAction that failed
 * @param cause
 *   Underlying SchemaError if this was caused by a schema operation
 * @param actualShape
 *   Truncated shape at the failure point (populated during .build() validation)
 * @param expectedShape
 *   Truncated expected shape (populated during .build() validation)
 * @param inputSlice
 *   Truncated DynamicValue snippet at path (opt-in, for runtime debugging)
 */
final case class MigrationError(
  message: String,
  path: DynamicOptic,
  actionIndex: Option[Int] = None,
  action: Option[MigrationAction] = None,
  cause: Option[SchemaError] = None,
  actualShape: Option[String] = None,
  expectedShape: Option[String] = None,
  inputSlice: Option[String] = None
) extends Exception(MigrationError.formatMessage(message, path, actionIndex))

object MigrationError {
  def formatMessage(message: String, path: DynamicOptic, actionIndex: Option[Int]): String =
    actionIndex.fold(s"$path: $message")(i => s"[action $i] $path: $message")
}

/**
 * Thrown by `.build()` when migration validation fails.
 *
 * Contains accumulated validation errors for pattern matching and diagnostics.
 *
 * @param errors
 *   Non-empty list of validation errors
 */
final case class MigrationValidationException(
  errors: List[MigrationError]
) extends IllegalArgumentException(
      s"Migration validation failed (${errors.size} error${if (errors.size != 1) "s" else ""}):\n" +
        errors.map(e => s"  ${e.getMessage}").mkString("\n")
    )
