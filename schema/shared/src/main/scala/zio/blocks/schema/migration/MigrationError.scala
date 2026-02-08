package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic

/**
 * Represents all possible failures that can occur during migration of a
 * [[zio.blocks.schema.DynamicValue]].
 *
 * Every error variant carries a [[DynamicOptic]] `path` indicating where in the
 * data tree the failure occurred, enabling precise diagnostic messages.
 *
 * `MigrationError` is pure serializable data: no exceptions, no stack traces,
 * no functions.
 */
sealed trait MigrationError {

  /** The path into the data tree where the error occurred. */
  def path: DynamicOptic

  /** A human-readable description of the error, including the path. */
  def message: String
}

object MigrationError {

  /** A referenced field does not exist at the given path. */
  final case class FieldNotFound(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' not found at path: $path"
  }

  /** Trying to add a field that already exists at the given path. */
  final case class FieldAlreadyExists(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' already exists at path: $path"
  }

  /** Expected one type at the path, but found another. */
  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch at path: $path — expected $expected, got $actual"
  }

  /** A type coercion failed at the given path. */
  final case class InvalidCoercion(path: DynamicOptic, fromType: String, toType: String) extends MigrationError {
    def message: String = s"Cannot coerce from $fromType to $toType at path: $path"
  }

  /** A required value was not provided at the given path. */
  final case class MissingRequiredValue(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Missing required value for field '$fieldName' at path: $path"
  }

  /** The optic path could not be followed in the data. */
  final case class NavigationFailure(path: DynamicOptic, reason: String) extends MigrationError {
    def message: String = s"Navigation failure at path: $path — $reason"
  }

  /** Multiple errors accumulated during migration. */
  final case class CompositeError(path: DynamicOptic, errors: Chunk[MigrationError]) extends MigrationError {
    def message: String = {
      val sb = new java.lang.StringBuilder
      sb.append("Multiple migration errors at path: ")
      sb.append(path.toString)
      val len = errors.length
      var idx = 0
      while (idx < len) {
        sb.append('\n')
        sb.append("  - ")
        sb.append(errors(idx).message)
        idx += 1
      }
      sb.toString
    }
  }

  /** A custom error with a free-form reason string. */
  final case class CustomError(path: DynamicOptic, reason: String) extends MigrationError {
    def message: String = s"Migration error at path: $path — $reason"
  }
}
