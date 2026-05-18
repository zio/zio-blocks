package zio.blocks.schema.migration

import scala.util.control.NoStackTrace

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * Represents errors that occur during migration execution.
 *
 * Migration errors capture the path where the failure occurred, enabling
 * precise diagnostics like "Failed to apply TransformValue at
 * .addresses.each.streetNumber".
 *
 * Each error variant provides specific context about the failure mode:
 *   - Schema mismatches (expected record but got sequence)
 *   - Missing data (field not found)
 *   - Expression evaluation failures
 *   - Type conversion failures
 *
 * Extends Exception with NoStackTrace for efficient error handling without the
 * overhead of stack trace generation, since migration errors are expected
 * control flow rather than exceptional conditions.
 */
sealed trait MigrationError extends Exception with NoStackTrace {

  /** The path in the DynamicValue structure where the error occurred */
  def path: DynamicOptic

  /** A descriptive message explaining the error */
  def message: String

  /** Prepend a field access to the error path */
  def atField(name: String): MigrationError

  /** Prepend an index access to the error path */
  def atIndex(index: Int): MigrationError

  override def getMessage: String = toString

  override def toString: String =
    if (path.nodes.isEmpty) message
    else s"$message at: ${path.toString}"
}

object MigrationError {

  // ─────────────────────────────────────────────────────────────────────────
  // Schema Type Mismatch Errors
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Expected a Record but found a different type.
   *
   * Occurs when record operations (addField, dropField, rename) are applied to
   * non-record values like sequences or primitives.
   */
  final case class ExpectedRecord(path: DynamicOptic, actual: DynamicValue) extends MigrationError {
    def message: String = s"Expected Record but found ${actual.valueType}"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  /**
   * Expected a Sequence but found a different type.
   *
   * Occurs when collection operations (transformElements) are applied to
   * non-sequence values.
   */
  final case class ExpectedSequence(path: DynamicOptic, actual: DynamicValue) extends MigrationError {
    def message: String = s"Expected Sequence but found ${actual.valueType}"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  /**
   * Expected a Map but found a different type.
   *
   * Occurs when map operations (transformKeys, transformValues) are applied to
   * non-map values.
   */
  final case class ExpectedMap(path: DynamicOptic, actual: DynamicValue) extends MigrationError {
    def message: String = s"Expected Map but found ${actual.valueType}"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  /**
   * Expected a Variant but found a different type.
   *
   * Occurs when enum operations (renameCase, transformCase) are applied to
   * non-variant values.
   */
  final case class ExpectedVariant(path: DynamicOptic, actual: DynamicValue) extends MigrationError {
    def message: String = s"Expected Variant but found ${actual.valueType}"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Missing Data Errors
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A required field was not found in a record.
   *
   * Occurs when operations reference fields that don't exist in the source
   * data, such as renaming or transforming a non-existent field.
   */
  final case class FieldNotFound(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' not found"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  /**
   * A required variant case was not found.
   *
   * Occurs when enum operations reference cases that don't exist in the schema.
   */
  final case class CaseNotFound(path: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Case '$caseName' not found"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Expression Evaluation Errors
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A SchemaExpr failed to evaluate.
   *
   * Occurs when default value expressions, transforms, or converters fail
   * during execution. The underlying cause provides details about the failure.
   */
  final case class ExpressionFailed(path: DynamicOptic, cause: String) extends MigrationError {
    def message: String = s"Expression evaluation failed: $cause"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  /**
   * A split operation produced the wrong number of values.
   *
   * Split operations must produce exactly the number of values expected by the
   * target field list.
   */
  final case class SplitResultMismatch(
    path: DynamicOptic,
    expectedCount: Int,
    actual: DynamicValue
  ) extends MigrationError {
    def message: String = s"Split expected $expectedCount values but got ${actual.elements.size}"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type Conversion Errors
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A primitive type conversion failed.
   *
   * Occurs when changeFieldType operations cannot convert between the specified
   * primitive types, such as converting "abc" to Int.
   */
  final case class ConversionFailed(path: DynamicOptic, cause: String) extends MigrationError {
    def message: String = s"Type conversion failed: $cause"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  object ConversionFailed {

    /** Create a ConversionFailed at the root path */
    def apply(cause: String): ConversionFailed =
      new ConversionFailed(DynamicOptic.root, cause)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Path Navigation Errors
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Failed to navigate to the specified path.
   *
   * Occurs when the path specified in a migration action does not exist in the
   * source data structure.
   */
  final case class PathNotFound(path: DynamicOptic) extends MigrationError {
    def message: String = "Path not found"

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  /**
   * General migration error for cases not covered by specific variants.
   */
  final case class General(path: DynamicOptic, message: String) extends MigrationError {

    def atField(name: String): MigrationError =
      copy(path = DynamicOptic.root.field(name)(path))

    def atIndex(index: Int): MigrationError =
      copy(path = DynamicOptic.root.at(index)(path))
  }

  object General {

    /** Create a General error at the root path */
    def apply(message: String): General =
      new General(DynamicOptic.root, message)
  }
}
