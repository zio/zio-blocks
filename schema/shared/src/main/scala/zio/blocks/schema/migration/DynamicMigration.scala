package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * A DynamicMigration is a pure, serializable representation of a schema migration.
 * 
 * It operates on DynamicValue and consists of a sequence of MigrationActions.
 * 
 * Key properties:
 * - Fully serializable (no functions, no closures)
 * - Composable (can chain migrations with ++)
 * - Reversible (has a structural inverse)
 * - Introspectable (can inspect the actions)
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {
  
  /**
   * Apply this migration to a DynamicValue.
   * Actions are applied sequentially from left to right.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(v), action) => action.apply(v)
      case (left @ Left(_), _) => left
    }
  }

  /**
   * Compose this migration with another migration sequentially.
   * The result applies this migration first, then the other.
   */
  def ++(that: DynamicMigration): DynamicMigration = {
    DynamicMigration(this.actions ++ that.actions)
  }

  /**
   * Alias for ++
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Return the structural reverse of this migration.
   * 
   * The reverse migration:
   * - Reverses the order of actions
   * - Reverses each individual action
   * 
   * Note: This is a structural reverse, not necessarily a perfect semantic inverse.
   * Some transformations (like type conversions) may not be perfectly reversible.
   */
  def reverse: DynamicMigration = {
    DynamicMigration(actions.reverse.map(_.reverse))
  }

  /**
   * Get a human-readable description of this migration.
   */
  def describe: String = {
    if (actions.isEmpty) {
      "Empty migration (identity)"
    } else {
      val descriptions = actions.map {
        case MigrationAction.AddField(at, fieldName, _) =>
          s"Add field '$fieldName' at $at"
        case MigrationAction.DropField(at, fieldName, _) =>
          s"Drop field '$fieldName' at $at"
        case MigrationAction.Rename(at, from, to) =>
          s"Rename field '$from' to '$to' at $at"
        case MigrationAction.TransformValue(at, fieldName, _) =>
          s"Transform field '$fieldName' at $at"
        case MigrationAction.Mandate(at, fieldName, _) =>
          s"Make field '$fieldName' mandatory at $at"
        case MigrationAction.Optionalize(at, fieldName) =>
          s"Make field '$fieldName' optional at $at"
        case MigrationAction.ChangeType(at, fieldName, _) =>
          s"Change type of field '$fieldName' at $at"
        case MigrationAction.Join(at, sourceFields, targetField, _) =>
          s"Join fields ${sourceFields.mkString(", ")} into '$targetField' at $at"
        case MigrationAction.Split(at, sourceField, targetFields, _) =>
          s"Split field '$sourceField' into ${targetFields.mkString(", ")} at $at"
        case MigrationAction.RenameCase(at, from, to) =>
          s"Rename case '$from' to '$to' at $at"
        case MigrationAction.TransformCase(at, caseName, _) =>
          s"Transform case '$caseName' at $at"
        case MigrationAction.TransformElements(at, _) =>
          s"Transform elements at $at"
        case MigrationAction.TransformKeys(at, _) =>
          s"Transform keys at $at"
        case MigrationAction.TransformValues(at, _) =>
          s"Transform values at $at"
      }
      descriptions.mkString("\n")
    }
  }

  /**
   * Check if this migration is empty (identity).
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * Get the number of actions in this migration.
   */
  def size: Int = actions.length
}

object DynamicMigration {
  
  /**
   * Create an empty migration (identity).
   */
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  /**
   * Create a migration from a single action.
   */
  def single(action: MigrationAction): DynamicMigration = 
    DynamicMigration(Vector(action))

  /**
   * Create a migration from multiple actions.
   */
  def fromActions(actions: MigrationAction*): DynamicMigration = 
    DynamicMigration(actions.toVector)
}

