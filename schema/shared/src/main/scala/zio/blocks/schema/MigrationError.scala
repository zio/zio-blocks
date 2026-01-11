package zio.blocks.schema

/**
 * Errors that can occur during migration operations.
 */
sealed trait MigrationError {
  def optic: DynamicOptic
  def message: String
}

object MigrationError {

  /**
   * The value at the specified path is not a record.
   */
  final case class NotARecord(optic: DynamicOptic) extends MigrationError {
    def message: String = s"Expected a record at path $optic"
  }

  /**
   * The value at the specified path is not a sequence.
   */
  final case class NotASequence(optic: DynamicOptic) extends MigrationError {
    def message: String = s"Expected a sequence at path $optic"
  }

  /**
   * The value at the specified path is not a map.
   */
  final case class NotAMap(optic: DynamicOptic) extends MigrationError {
    def message: String = s"Expected a map at path $optic"
  }

  /**
   * A required field was not found in the record.
   */
  final case class FieldNotFound(optic: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' not found at path $optic"
  }

  /**
   * A field already exists when trying to add a new one.
   */
  final case class FieldAlreadyExists(optic: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' already exists at path $optic"
  }

  /**
   * A case was removed and no fallback was provided.
   */
  final case class CaseRemoved(optic: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Case '$caseName' was removed at path $optic and no fallback was provided"
  }

  /**
   * An unsupported optic node was encountered.
   */
  final case class UnsupportedOpticNode(optic: DynamicOptic) extends MigrationError {
    def message: String = s"Unsupported optic node in path $optic"
  }

  /**
   * A transformation function failed.
   */
  final case class TransformFailed(optic: DynamicOptic, cause: String) extends MigrationError {
    def message: String = s"Transform failed at path $optic: $cause"
  }

  /**
   * Type mismatch during migration.
   */
  final case class TypeMismatch(optic: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch at path $optic: expected $expected, got $actual"
  }
}
