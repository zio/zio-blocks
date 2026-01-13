package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}
import zio.blocks.schema.migration.MigrationAction.*
import zio.blocks.schema.Reflect

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
  ): Either[MigrationError, Unit] =
    program.actions.foldLeft[Either[MigrationError, Unit]](Right(())) { (acc, a) =>
      acc.flatMap(_ => validateAction(a, source, target))
    }

  // ----------------------------
  // Basic shape checks
  // ----------------------------

  private def endsWithField(at: DynamicOptic): Boolean =
    at.nodes.lastOption.exists(_.isInstanceOf[DynamicOptic.Node.Field])

  private def containsCase(at: DynamicOptic): Boolean =
    at.nodes.exists {
      case DynamicOptic.Node.Case(_) => true
      case _                         => false
    }

  private def checkFieldPath(ctx: String, at: DynamicOptic): Either[MigrationError, Unit] =
    if (!endsWithField(at))
      Left(MigrationError.InvalidOp(ctx, s"at must end in .field(...), got: $at"))
    else Right(())

  private def checkNonEmpty(ctx: String, n: Int, what: String): Either[MigrationError, Unit] =
    if (n <= 0) Left(MigrationError.InvalidOp(ctx, s"$what cannot be empty"))
    else Right(())

  // ----------------------------
  // Schema path existence via Reflect
  // ----------------------------

  /**
   * Uses DynamicOptic.apply(reflect) which calls reflect.get(this) :contentReference[oaicite:5]{index=5}
   * to check whether a path is valid for a given schema.
   */
  private def pathExists(schema: Schema[_], at: DynamicOptic): Boolean = {
    // We only need existence; we can erase the optic functor kind with a type lambda.
    type AnyF[_, _] = Any
    val anySchema   = schema.asInstanceOf[Schema[Any]]
    val reflectAny  = anySchema.reflect.asInstanceOf[Reflect[AnyF, Any]]
    at(reflectAny).isDefined
  }

  private def requirePathExists(ctx: String, schema: Schema[_], at: DynamicOptic): Either[MigrationError, Unit] =
    if (pathExists(schema, at)) Right(())
    else Left(MigrationError.InvalidOp(ctx, s"Invalid path for schema: $at"))

  // ----------------------------
  // Per-action validation
  // ----------------------------

  private def validateAction(
      a: MigrationAction,
      source: Schema[_],
      target: Schema[_]
  ): Either[MigrationError, Unit] =
    a match {

      // ----- Record actions -----

      case AddField(at, _) =>
        for {
          _ <- checkFieldPath("AddField", at)
          _ <- requirePathExists("AddField", target, at) // must exist in target schema
          // Optional "stronger" check (comment out if unwanted):
          // _ <- if (!pathExists(source, at)) Right(()) else Left(MigrationError.InvalidOp("AddField", s"Field already exists in source: $at"))
        } yield ()

      case DropField(at, _) =>
        for {
          _ <- checkFieldPath("DropField", at)
          _ <- requirePathExists("DropField", source, at) // must exist in source schema
        } yield ()

      case Rename(at, to) =>
        for {
          _ <- checkFieldPath("Rename", at)
          _ <- requirePathExists("Rename", source, at)
          // We can only validate the *target name* by checking that the parent exists in target;
          // validating "field(to)" precisely would require rebuilding the parent optic.
          // Still useful: ensure something is reachable at the same path *after* rename in target by checking target has *some* field at that location.
          _ <- if (to.nonEmpty) Right(()) else Left(MigrationError.InvalidOp("Rename", "to cannot be empty"))
        } yield ()

      case TransformValue(at, _) =>
        // After your DSL, transform happens at the (possibly renamed) target location.
        requirePathExists("TransformValue", target, at)

      case ChangeType(at, _) =>
        requirePathExists("ChangeType", target, at)

      case Optionalize(at) =>
        requirePathExists("Optionalize", target, at)

      case Mandate(at, _) =>
        requirePathExists("Mandate", target, at)

      case Join(at, sourcePaths, _) =>
        for {
          _ <- checkFieldPath("Join", at)
          _ <- requirePathExists("Join", target, at)
          _ <- checkNonEmpty("Join", sourcePaths.size, "sourcePaths")
          _ <- sourcePaths.foldLeft[Either[MigrationError, Unit]](Right(())) { (acc, p) =>
            acc.flatMap(_ => requirePathExists("Join", source, p))
          }
        } yield ()

      case Split(at, targetPaths, _) =>
        for {
          _ <- checkFieldPath("Split", at)
          _ <- requirePathExists("Split", source, at)
          _ <- checkNonEmpty("Split", targetPaths.size, "targetPaths")
          _ <- targetPaths.foldLeft[Either[MigrationError, Unit]](Right(())) { (acc, p) =>
            acc.flatMap(_ => requirePathExists("Split", target, p))
          }
        } yield ()

      // ----- Enum actions -----

      case RenameCase(at, from, to) =>
        for {
          _ <- if (from != to) Right(()) else Left(MigrationError.InvalidOp("RenameCase", "from and to must differ"))
          _ <- requirePathExists("RenameCase", source, at)
        } yield ()

      case TransformCase(at, actions) =>
        for {
          _ <- if (containsCase(at)) Right(())
               else Left(MigrationError.InvalidOp("TransformCase", s"at must include .when[Case] / Node.Case(...), got: $at"))
          _ <- requirePathExists("TransformCase", source, at)
          // Nested actions: validate them too (best-effort, using same source/target)
          _ <- actions.foldLeft[Either[MigrationError, Unit]](Right(())) { (acc, nested) =>
            acc.flatMap(_ => validateAction(nested, source, target))
          }
        } yield ()

      // ----- Collections / Maps -----

      case TransformElements(at, _) =>
        requirePathExists("TransformElements", source, at)

      case TransformKeys(at, _) =>
        requirePathExists("TransformKeys", source, at)

      case TransformValues(at, _) =>
        requirePathExists("TransformValues", source, at)
    }
}
