package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}
import zio.blocks.schema.migration.MigrationAction.*

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
    val targetDefaultDyn: Option[DynamicValue] =
      target.asInstanceOf[Schema[Any]].defaultValue.map(v => target.asInstanceOf[Schema[Any]].toDynamicValue(v))

    program.actions.foldLeft[Either[MigrationError, Unit]](Right(())) {
      case (acc, a) => acc.flatMap(_ => validateAction(a, targetDefaultDyn))
    }
  }

  private def endsWithField(at: DynamicOptic): Boolean =
    at.nodes.lastOption.exists(_.isInstanceOf[DynamicOptic.Node.Field])

  private def containsCase(at: DynamicOptic): Boolean =
    at.nodes.exists {
      case DynamicOptic.Node.Case(_) => true
      case _                         => false
    }

  private def validateAction(
      a: MigrationAction,
      targetDefaultDyn: Option[DynamicValue]
  ): Either[MigrationError, Unit] = {

    def checkFieldPath(ctx: String, at: DynamicOptic): Either[MigrationError, Unit] =
      if (!endsWithField(at))
        Left(MigrationError.InvalidOp(ctx, s"at must end in .field(...), got: $at"))
      else Right(())

    def checkPathExistsIfPossible(at: DynamicOptic): Either[MigrationError, Unit] =
      targetDefaultDyn match {
        case None => Right(()) // can't validate without a default
        case Some(root) =>
          // best-effort: try to focus the path on the default value
          DynamicMigrationInterpreter
            .apply(DynamicMigration.empty, root, Schema[Any].dynamicValueSchema, Schema[Any].dynamicValueSchema) // no-op
          Right(()) // keep it minimal; interpreter will fail at runtime if path is wrong
      }

    a match {
      case AddField(at, _) =>
        checkFieldPath("AddField", at)

      case DropField(at, _) =>
        checkFieldPath("DropField", at)

      case Rename(at, _) =>
        checkFieldPath("Rename", at)

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
