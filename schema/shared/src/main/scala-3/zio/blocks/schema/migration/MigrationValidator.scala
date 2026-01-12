package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

object MigrationValidator {

  def validateOrThrow(
      program: DynamicMigration,
      source: Schema[_],
      target: Schema[_]
  ): Unit =
    validate(program, source, target) match {
      case Left(err)  => throw err
      case Right(())  => ()
    }

  def validate(
      program: DynamicMigration,
      source: Schema[_],
      target: Schema[_]
  ): Either[MigrationError, Unit] = {
    // BEGINNER V1: shallow validation
    // You can expand this to full #519 constraints later.

    program.actions.foldLeft[Either[MigrationError, Unit]](Right(())) { (acc, a) =>
      acc.flatMap(_ => validateAction(a, source, target))
    }
  }

  private def validateAction(
      a: MigrationAction,
      source: Schema[_],
      target: Schema[_]
  ): Either[MigrationError, Unit] = {
    // TODO: implement real schema walking by optic path.
    // For now: accept everything.
    Right(())
  }
}
