package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * MigrationError defines the diagnostic hierarchy for schema migration
 * failures. Each error is strictly associated with a 'path' (DynamicOptic) to
 * ensure precise traceability during complex data transformations.
 */
sealed trait MigrationError extends Product with Serializable {
  def path: DynamicOptic
}

object MigrationError {

  /**
   * Indicates that a required field or member was not found at the specified
   * path.
   */
  case class FieldNotFound(path: DynamicOptic, fieldName: String) extends MigrationError

  /**
   * Represents a type inconsistency encountered during the migration process.
   */
  case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError

  /**
   * Signals a failure within the transformation logic at the value level. This
   * is typically used when schema expressions or custom functions fail.
   */
  case class TransformationFailed(path: DynamicOptic, actionType: String, reason: String) extends MigrationError

  /**
   * Indicates that a specific enum or sealed trait variant was not recognized.
   */
  case class CaseNotFound(path: DynamicOptic, caseName: String) extends MigrationError

  /**
   * Represents a failure in the final decoding stage when mapping back to a
   * typed schema.
   */
  case class DecodingError(path: DynamicOptic, reason: String) extends MigrationError
}
