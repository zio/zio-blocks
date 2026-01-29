package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaExpr}

/**
 * A migration action represents a single transformation step in a schema migration.
 *
 * All actions operate at a path represented by [[DynamicOptic]] and can be reversed
 * to support bidirectional migrations.
 */
sealed trait MigrationAction {

  /**
   * The path where this action operates.
   */
  def at: DynamicOptic

  /**
   * Returns the reverse of this action for bidirectional migrations.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ==========================================================================
  // Record Actions
  // ==========================================================================

  /**
   * Adds a new field with a default value.
   *
   * @param at the path where the field should be added
   * @param default the default value expression
   */
  final case class AddField(at: DynamicOptic, default: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  /**
   * Removes a field. For reverse migration, a default value must be provided.
   *
   * @param at the path of the field to drop
   * @param defaultForReverse the default value to use when reversing this action
   */
  final case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Renames a field.
   *
   * @param at the path of the field to rename
   * @param to the new name for the field
   */
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = {
      // Extract the parent path and old field name
      val nodes = at.nodes
      if (nodes.nonEmpty) {
        nodes.last match {
          case DynamicOptic.Node.Field(oldName) =>
            val parentPath = DynamicOptic(nodes.init)
            Rename(parentPath.field(to), oldName)
          case _ => this // Cannot reverse non-field rename
        }
      } else this
    }
  }

  /**
   * Transforms a field's value using an expression.
   *
   * @param at the path of the field to transform
   * @param transform the transformation expression
   * @param reverseTransform optional reverse transformation
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[_, _],
    reverseTransform: Option[SchemaExpr[_, _]] = None
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(
      at,
      reverseTransform.getOrElse(transform),
      Some(transform)
    )
  }

  /**
   * Makes an optional field mandatory by providing a default for None values.
   *
   * @param at the path of the optional field
   * @param default the default value to use when the field is None
   */
  final case class Mandate(at: DynamicOptic, default: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Makes a mandatory field optional.
   *
   * @param at the path of the field to make optional
   */
  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = {
      // Reverse requires a default, which must be provided by the user
      // This is a placeholder - the actual reverse should be constructed with a proper default
      Mandate(at, SchemaExpr.Literal(None, Schema.option(Schema.unit)))
    }
  }

  /**
   * Joins multiple fields into a single field.
   *
   * @param at the path where the joined field will be created
   * @param sourcePaths the paths of the fields to join
   * @param combiner the expression that combines the source fields
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      // Reverse of join is split - but we need more information for a proper reverse
      // This is a best-effort reverse
      Split(at, sourcePaths, combiner)
    }
  }

  /**
   * Splits a field into multiple fields.
   *
   * @param at the path of the field to split
   * @param targetPaths the paths where the split fields will be created
   * @param splitter the expression that splits the source field
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitter)
  }

  /**
   * Changes the type of a field (primitive-to-primitive only).
   *
   * @param at the path of the field to change
   * @param converter the conversion expression
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: SchemaExpr[_, _],
    reverseConverter: Option[SchemaExpr[_, _]] = None
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(
      at,
      reverseConverter.getOrElse(converter),
      Some(converter)
    )
  }

  // ==========================================================================
  // Enum Actions
  // ==========================================================================

  /**
   * Renames a case in an enum/sum type.
   *
   * @param at the path to the enum
   * @param from the original case name
   * @param to the new case name
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transforms a specific case in an enum/sum type.
   *
   * @param at the path to the enum
   * @param caseName the name of the case to transform
   * @param actions the migration actions to apply to the case
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(
      at,
      caseName,
      actions.reverse.map(_.reverse)
    )
  }

  // ==========================================================================
  // Collection Actions
  // ==========================================================================

  /**
   * Transforms all elements in a collection.
   *
   * @param at the path to the collection
   * @param transform the transformation expression to apply to each element
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      // Element transformation is not generally reversible
      // This is a best-effort that assumes the transform is its own inverse
      TransformElements(at, transform)
    }
  }

  // ==========================================================================
  // Map Actions
  // ==========================================================================

  /**
   * Transforms all keys in a map.
   *
   * @param at the path to the map
   * @param transform the transformation expression to apply to each key
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      // Key transformation is not generally reversible
      TransformKeys(at, transform)
    }
  }

  /**
   * Transforms all values in a map.
   *
   * @param at the path to the map
   * @param transform the transformation expression to apply to each value
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      // Value transformation is not generally reversible
      TransformValues(at, transform)
    }
  }

  // ==========================================================================
  // Utility Methods
  // ==========================================================================

  /**
   * Reverses a sequence of actions.
   *
   * The reverse of a sequence [a1, a2, ..., an] is [an.reverse, ..., a2.reverse, a1.reverse].
   */
  def reverseActions(actions: Vector[MigrationAction]): Vector[MigrationAction] =
    actions.reverse.map(_.reverse)
}
