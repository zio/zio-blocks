package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A single, path-based transformation step in a migration. Every action
 * targets a location in the source structure via a [[DynamicOptic]] and
 * knows how to produce its structural inverse.
 *
 * Actions are pure data — no closures, no reflection — so they can be
 * serialized, stored, inspected, and used to generate DDL or offline
 * transforms.
 */
sealed trait MigrationAction {

  /** The path in the structure where this action applies. */
  def at: DynamicOptic

  /** Structural inverse of this action. */
  def reverse: MigrationAction
}

object MigrationAction {

  // ── Record operations ──────────────────────────────────────────────

  /**
   * Adds a field to a record at the given path. The `default` provides the
   * value to insert for records that don't already have the field.
   */
  final case class AddField(at: DynamicOptic, fieldName: String, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, default)
  }

  /**
   * Removes a field from a record. `defaultForReverse` is stored so the
   * inverse `AddField` can reconstruct the dropped field.
   */
  final case class DropField(at: DynamicOptic, fieldName: String, defaultForReverse: DynamicValue)
      extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)
  }

  /**
   * Renames a field within a record.
   */
  final case class RenameField(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameField(at, to, from)
  }

  /**
   * Replaces the value of a field using a pure, serializable expression
   * represented as a [[DynamicValue]] holding the transformation descriptor.
   * For the initial implementation, this supports constant replacement;
   * richer SchemaExpr-backed transforms can be layered on later.
   */
  final case class TransformValue(at: DynamicOptic, transform: DynamicValue, inverseTransform: DynamicValue)
      extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, inverseTransform, transform)
  }

  /**
   * Converts an optional field to a required field. `default` is the value
   * used when the source field is `None` / `Null`.
   */
  final case class Mandate(at: DynamicOptic, fieldName: String, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at, fieldName)
  }

  /**
   * Converts a required field to an optional field.
   */
  final case class Optionalize(at: DynamicOptic, fieldName: String) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, fieldName, DynamicValue.Null)
  }

  /**
   * Changes the type of a field value using a converter expression.
   * Restricted to primitive-to-primitive conversions for now.
   */
  final case class ChangeFieldType(
    at: DynamicOptic,
    fieldName: String,
    converter: DynamicValue,
    inverseConverter: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeFieldType(at, fieldName, inverseConverter, converter)
  }

  // ── Enum operations ────────────────────────────────────────────────

  /**
   * Renames a variant case.
   */
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Applies a nested sequence of migration actions to a specific variant
   * case's payload.
   */
  final case class TransformCase(at: DynamicOptic, caseName: String, actions: Vector[MigrationAction])
      extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverseIterator.map(_.reverse).toVector)
  }

  // ── Collection operations ──────────────────────────────────────────

  /**
   * Transforms each element of a sequence at the given path.
   */
  final case class TransformElements(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, actions.reverseIterator.map(_.reverse).toVector)
  }

  /**
   * Transforms the keys of a map at the given path.
   */
  final case class TransformKeys(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, actions.reverseIterator.map(_.reverse).toVector)
  }

  /**
   * Transforms the values of a map at the given path.
   */
  final case class TransformValues(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, actions.reverseIterator.map(_.reverse).toVector)
  }
}
