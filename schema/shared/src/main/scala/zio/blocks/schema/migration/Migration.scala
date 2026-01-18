package zio.blocks.schema.migration

import zio.Chunk
import zio.blocks.schema.{DynamicValue, Schema, DynamicOptic}

final case class MigrationError(message: String, path: DynamicOptic = DynamicOptic.root)

final case class DynamicMigration(actions: Chunk[MigrationAction]) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = 
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(v => Right(v)) // Aqui você implementará a lógica de cada ação
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
      targetSchema.fromDynamicValue(dynamicNew).left.map { err =>
        MigrationError(s"Falha ao reconstruir schema: ${err.message}")
      }
    }
  }
}