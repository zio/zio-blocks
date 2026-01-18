package zio.blocks.schema.migration

import zio.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

final case class MigrationError(message: String, path: DynamicOptic = DynamicOptic.root)

final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(v => runAction(v, action))
    }

  private def runAction(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.AddField(path, expr) =>
        value.set(path, expr.evaluate).left.map(e => MigrationError(e.message, path))
      case MigrationAction.DropField(path, _) =>
        value.remove(path).left.map(e => MigrationError(e.message, path))
      case MigrationAction.Rename(path, newName) =>
        for {
          oldVal  <- value.get(path).left.map(e => MigrationError(s"Field not found: ${e.message}", path))
          removed <- value.remove(path).left.map(e => MigrationError(e.message, path))
          updated <- removed.set(path.parent.field(newName), oldVal).left.map(e => MigrationError(e.message, path))
        } yield updated
      case MigrationAction.TransformValue(path, expr) =>
        value.set(path, expr.evaluate).left.map(e => MigrationError(e.message, path))
      case MigrationAction.RenameCase(path, from, to) =>
        value.update(path)(dv => dv.renameCase(from, to)).left.map(e => MigrationError(e.message, path))
      case MigrationAction.TransformCase(path, actions) =>
        value.update(path) { dv =>
          DynamicMigration(actions).apply(dv) match {
            case Right(updated) => updated
            case Left(_)        => dv
          }
        }.left.map(e => MigrationError(e.message, path))
    }

  def reverse: DynamicMigration = DynamicMigration(actions.reverse.map(_.reverse))
}

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  def apply(value: A): Either[MigrationError, B] = {
    val dynamicOld = sourceSchema.toDynamicValue(value)
    dynamicMigration.apply(dynamicOld).flatMap { dynamicNew =>
      targetSchema.fromDynamicValue(dynamicNew).left.map { schemaError =>
        MigrationError(s"Reconstruction failed: ${schemaError.message}")
      }
    }
  }

  def reverse: Migration[B, A] = Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}