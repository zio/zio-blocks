package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

import scala.util.control.NoStackTrace

/**
 * An error that occurred during a migration operation.
 *
 * All errors capture path information via [[DynamicOptic]] to enable
 * diagnostics such as "Failed to apply TransformValue at
 * .addresses.each.streetNumber".
 */
final case class MigrationError(errors: ::[MigrationError.Single]) extends Exception with NoStackTrace {
  def ++(other: MigrationError): MigrationError =
    new MigrationError(new ::(errors.head, errors.tail ++ other.errors))

  def message: String = errors.map(_.message).mkString("\n")

  override def getMessage: String = message
}

object MigrationError {

  def single(error: Single): MigrationError =
    new MigrationError(new ::(error, Nil))

  sealed trait Single {
    def path: DynamicOptic
    def message: String
  }

  /**
   * A required field was not found in the source value.
   */
  case class FieldNotFound(path: DynamicOptic, fieldName: String) extends Single {
    def message: String = s"Field '$fieldName' not found at path $path"
  }

  /**
   * A field already exists when trying to add it.
   */
  case class FieldAlreadyExists(path: DynamicOptic, fieldName: String) extends Single {
    def message: String = s"Field '$fieldName' already exists at path $path"
  }

  /**
   * Expected a record but found a different kind of value.
   */
  case class NotARecord(path: DynamicOptic, actual: String) extends Single {
    def message: String = s"Expected a record at path $path, but found $actual"
  }

  /**
   * Expected a variant but found a different kind of value.
   */
  case class NotAVariant(path: DynamicOptic, actual: String) extends Single {
    def message: String = s"Expected a variant at path $path, but found $actual"
  }

  /**
   * Expected a sequence but found a different kind of value.
   */
  case class NotASequence(path: DynamicOptic, actual: String) extends Single {
    def message: String = s"Expected a sequence at path $path, but found $actual"
  }

  /**
   * Expected a map but found a different kind of value.
   */
  case class NotAMap(path: DynamicOptic, actual: String) extends Single {
    def message: String = s"Expected a map at path $path, but found $actual"
  }

  /**
   * Expected an optional value but found a non-optional one or vice versa.
   */
  case class OptionalMismatch(path: DynamicOptic, message: String) extends Single

  /**
   * A case name was not found in a variant.
   */
  case class CaseNotFound(path: DynamicOptic, caseName: String) extends Single {
    def message: String = s"Case '$caseName' not found in variant at path $path"
  }

  /**
   * Type conversion failed.
   */
  case class TypeConversionFailed(path: DynamicOptic, from: String, to: String, reason: String) extends Single {
    def message: String = s"Failed to convert from '$from' to '$to' at path $path: $reason"
  }

  /**
   * A transformation expression failed to evaluate.
   */
  case class TransformFailed(path: DynamicOptic, reason: String) extends Single {
    def message: String = s"Transform failed at path $path: $reason"
  }

  /**
   * Path navigation failed.
   */
  case class PathNavigationFailed(path: DynamicOptic, reason: String) extends Single {
    def message: String = s"Failed to navigate at path $path: $reason"
  }

  /**
   * Default value was required but not available.
   */
  case class DefaultValueMissing(path: DynamicOptic, fieldName: String) extends Single {
    def message: String = s"Default value required but not available for field '$fieldName' at path $path"
  }

  /**
   * Index out of bounds when accessing sequence elements.
   */
  case class IndexOutOfBounds(path: DynamicOptic, index: Int, size: Int) extends Single {
    def message: String = s"Index $index out of bounds (size: $size) at path $path"
  }

  /**
   * Key not found when accessing map entries.
   */
  case class KeyNotFound(path: DynamicOptic, key: String) extends Single {
    def message: String = s"Key '$key' not found in map at path $path"
  }

  /**
   * Numeric constraint violations during transforms.
   */
  case class NumericOverflow(path: DynamicOptic, operation: String) extends Single {
    def message: String = s"Numeric overflow during '$operation' at path $path"
  }

  /**
   * General migration action failure.
   */
  case class ActionFailed(path: DynamicOptic, action: String, reason: String) extends Single {
    def message: String = s"Action '$action' failed at path $path: $reason"
  }
}
