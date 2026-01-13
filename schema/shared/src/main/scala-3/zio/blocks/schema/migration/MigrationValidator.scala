package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

object MigrationValidator {

  def validateOrThrow(
      program: DynamicMigration,
      source: Schema[_],
      target: Schema[_]
  ): Unit =
    validate(program, source, target) match {
      case Left(err) => throw err
      case Right(()) => ()
    }

  def validate(
      program: DynamicMigration,
      source: Schema[_],
      target: Schema[_]
  ): Either[MigrationError, Unit] = {
    program.actions.foldLeft[Either[MigrationError, Unit]](Right(())) {
      case (acc, a) => acc.flatMap(_ => validateAction(a))
    }
  }

  private def endsWithField(at: DynamicOptic): Boolean =
    at.nodes.lastOption.exists(_.isInstanceOf[DynamicOptic.Node.Field])

  private def containsCase(at: DynamicOptic): Boolean =
    at.nodes.exists {
      case DynamicOptic.Node.Case(_) => true
      case _                         => false
    }

  private def validateAction(a: MigrationAction): Either[MigrationError, Unit] = {
    import MigrationAction.*

    a match {
      case AddField(at, _) =>
        if (!endsWithField(at))
          Left(MigrationError.InvalidOp("AddField", s"at must end in .field(...), got: $at"))
        else Right(())

      case DropField(at, _) =>
        if (!endsWithField(at))
          Left(MigrationError.InvalidOp("DropField", s"at must end in .field(...), got: $at"))
        else Right(())

      case Rename(at, _) =>
        if (!endsWithField(at))
          Left(MigrationError.InvalidOp("Rename", s"at must end in .field(...), got: $at"))
        else Right(())

      case TransformValue(_, _) =>
        Right(())

      case Mandate(_, _) =>
        Right(())

      case Optionalize(_) =>
        Right(())

      case ChangeType(_, _) =>
        Right(())

      case Join(_, sourcePaths, _) =>
        if (sourcePaths.isEmpty) Left(MigrationError.InvalidOp("Join", "sourcePaths cannot be empty"))
        else Right(())

      case Split(_, targetPaths, _) =>
        if (targetPaths.isEmpty) Left(MigrationError.InvalidOp("Split", "targetPaths cannot be empty"))
        else Right(())

      case RenameCase(_, from, to) =>
        if (from == to) Left(MigrationError.InvalidOp("RenameCase", "from and to must differ"))
        else Right(())

      case TransformCase(at, _) =>
        // In #519, the case lives in `at` via .when[Case] selector :contentReference[oaicite:5]{index=5}
        if (!containsCase(at))
          Left(MigrationError.InvalidOp("TransformCase", s"at must include .when[Case] / Node.Case(...), got: $at"))
        else Right(())

      case TransformElements(_, _) =>
        Right(())

      case TransformKeys(_, _) =>
        Right(())

      case TransformValues(_, _) =>
        Right(())
    }
  }
}
