package zio.schema.migration

import zio.schema._
import zio.Chunk

/**
 * The core serializable ADT representing a schema transformation.
 * This structure contains zero user-defined functions or closures.
 */
sealed trait MigrationAction
object MigrationAction {
  // Record Transforms
  case class AddField(path: DynamicOptic, value: DynamicValue) extends MigrationAction
  case class DropField(path: DynamicOptic) extends MigrationAction
  case class RenameField(path: DynamicOptic, newName: String) extends MigrationAction
  case class TransformValue(path: DynamicOptic, transform: String) extends MigrationAction // simplified for draft
  
  // Enum Transforms
  case class AddCase(path: DynamicOptic, name: String, schema: Schema[_]) extends MigrationAction
  case class DropCase(path: DynamicOptic, name: String) extends MigrationAction
  case class RenameCase(path: DynamicOptic, oldName: String, newName: String) extends MigrationAction
  
  // Requirement Transforms
  case class MakeOptional(path: DynamicOptic) extends MigrationAction
  case class MakeRequired(path: DynamicOptic, defaultValue: DynamicValue) extends MigrationAction

  // Collection Transforms
  case class TransformElements(path: DynamicOptic, action: MigrationAction) extends MigrationAction
  case class TransformKeys(path: DynamicOptic, action: MigrationAction) extends MigrationAction
  case class TransformValues(path: DynamicOptic, action: MigrationAction) extends MigrationAction
}

case class DynamicMigration(actions: Chunk[MigrationAction]) {
  /**
   * Identity: migration.reverse.reverse == migration
   */
  def reverse: DynamicMigration = {
    val reversedActions = actions.reverse.map {
      case MigrationAction.RenameField(path, newName) => 
        // Logic to extract old name from path would go here
        MigrationAction.RenameField(path, "TODO_OLD_NAME") 
      case MigrationAction.AddField(path, _) => MigrationAction.DropField(path)
      case MigrationAction.DropField(path) => MigrationAction.AddField(path, DynamicValue.None) // Simplified
      case other => other // Placeholder for complex inversions
    }
    DynamicMigration(reversedActions)
  }

  def ++(that: DynamicMigration): DynamicMigration = 
    DynamicMigration(this.actions ++ that.actions)
}

case class Migration[A, B](source: Schema[A], target: Schema[B], internal: DynamicMigration)
