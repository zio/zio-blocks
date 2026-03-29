package zio.blocks.schema.migration

import zio.blocks.schema.{ DynamicOptic, DynamicValue, SchemaExpr }
import MigrationAction.*

// ─────────────────────────────────────────────────────────────────────────────
//  Error ADT
// ─────────────────────────────────────────────────────────────────────────────

sealed trait MigrationError extends Exception {
  def message: String
  override def getMessage: String = message
}

object MigrationError {
  final case class FieldNotFound(path: DynamicOptic, value: DynamicValue)
    extends MigrationError {
    val message = s"Field not found at path '$path' in value: $value"
  }

  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String)
    extends MigrationError {
    val message = s"Type mismatch at '$path': expected $expected, got $actual"
  }

  final case class TransformFailed(path: DynamicOptic, cause: String)
    extends MigrationError {
    val message = s"Transform failed at '$path': $cause"
  }

  final case class InvalidAction(action: MigrationAction, cause: String)
    extends MigrationError {
    val message = s"Invalid migration action ${action.getClass.getSimpleName}: $cause"
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DynamicMigration — pure data, fully serializable
// ─────────────────────────────────────────────────────────────────────────────

final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a DynamicValue, transforming it sequentially
   * through all actions. Returns either a MigrationError or the migrated value.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Left(err), _)    => Left(err)
      case (Right(v), action) => applyAction(v, action)
    }

  /**
   * Compose this migration with another, running this first then that.
   * Satisfies associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Structural reverse of this migration.
   * Satisfies: m.reverse.reverse == m (structurally)
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  // ── Private action interpreter ─────────────────────────────────────────────

  private def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    action match {

      case AddField(at, default) =>
        for {
          defaultVal <- evalExpr(default, value, at)
          updated    <- insertAt(value, at, defaultVal)
        } yield updated

      case DropField(at, _) =>
        removeAt(value, at)

      case Rename(at, to) =>
        renameFieldAt(value, at, to)

      case TransformValue(at, transform) =>
        for {
          current    <- getAt(value, at)
          transformed <- evalExpr(transform, current, at)
          updated    <- setAt(value, at, transformed)
        } yield updated

      case Mandate(at, default) =>
        for {
          current <- getAt(value, at)
          result  <- current match {
            case DynamicValue.SomeValue(inner) => Right(inner)
            case DynamicValue.NoneValue =>
              evalExpr(default, value, at)
            case other =>
              Left(MigrationError.TypeMismatch(at, "Option[_]", other.getClass.getSimpleName))
          }
          updated <- setAt(value, at, result)
        } yield updated

      case Optionalize(at) =>
        for {
          current <- getAt(value, at)
          updated <- setAt(value, at, DynamicValue.SomeValue(current))
        } yield updated

      case Join(at, sourcePaths, combiner) =>
        for {
          sourceValues <- sourcePaths.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
            case (Left(e), _)    => Left(e)
            case (Right(acc), p) => getAt(value, p).map(acc :+ _)
          }
          combined <- evalExpr(combiner, DynamicValue.Sequence(sourceValues), at)
          updated  <- insertAt(value, at, combined)
        } yield updated

      case Split(at, targetPaths, splitter) =>
        for {
          current <- getAt(value, at)
          parts   <- evalExpr(splitter, current, at)
          partSeq <- parts match {
            case DynamicValue.Sequence(elems) => Right(elems)
            case other => Left(MigrationError.TypeMismatch(at, "Sequence", other.getClass.getSimpleName))
          }
          _ <- if (partSeq.length != targetPaths.length)
            Left(MigrationError.TransformFailed(at, s"Split produced ${partSeq.length} parts but ${targetPaths.length} target paths"))
          else Right(())
          updated <- targetPaths.zip(partSeq).foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
            case (Left(e), _)           => Left(e)
            case (Right(v), (path, part)) => insertAt(v, path, part)
          }
          cleaned <- removeAt(updated, at)
        } yield cleaned

      case ChangeType(at, converter) =>
        for {
          current    <- getAt(value, at)
          converted  <- evalExpr(converter, current, at)
          updated    <- setAt(value, at, converted)
        } yield updated

      case RenameCase(at, from, to) =>
        renameCaseAt(value, at, from, to)

      case TransformCase(at, caseActions) =>
        for {
          current <- getAt(value, at)
          migrated <- DynamicMigration(caseActions)(current)
          updated  <- setAt(value, at, migrated)
        } yield updated

      case TransformElements(at, transform) =>
        for {
          current <- getAt(value, at)
          result  <- current match {
            case DynamicValue.Sequence(elems) =>
              elems.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
                case (Left(e), _)   => Left(e)
                case (Right(acc), elem) =>
                  evalExpr(transform, elem, at).map(acc :+ _)
              }.map(DynamicValue.Sequence(_))
            case other =>
              Left(MigrationError.TypeMismatch(at, "Sequence", other.getClass.getSimpleName))
          }
          updated <- setAt(value, at, result)
        } yield updated

      case TransformKeys(at, transform) =>
        for {
          current <- getAt(value, at)
          result  <- current match {
            case DynamicValue.Dictionary(pairs) =>
              pairs.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                case (Left(e), _)         => Left(e)
                case (Right(acc), (k, v)) =>
                  evalExpr(transform, k, at).map(newK => acc :+ (newK -> v))
              }.map(DynamicValue.Dictionary(_))
            case other =>
              Left(MigrationError.TypeMismatch(at, "Dictionary", other.getClass.getSimpleName))
          }
          updated <- setAt(value, at, result)
        } yield updated

      case TransformValues(at, transform) =>
        for {
          current <- getAt(value, at)
          result  <- current match {
            case DynamicValue.Dictionary(pairs) =>
              pairs.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                case (Left(e), _)         => Left(e)
                case (Right(acc), (k, v)) =>
                  evalExpr(transform, v, at).map(newV => acc :+ (k -> newV))
              }.map(DynamicValue.Dictionary(_))
            case other =>
              Left(MigrationError.TypeMismatch(at, "Dictionary", other.getClass.getSimpleName))
          }
          updated <- setAt(value, at, result)
        } yield updated
    }

  // ── DynamicValue navigation helpers ───────────────────────────────────────

  private def getAt(
    value: DynamicValue,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    path.get(value).toRight(MigrationError.FieldNotFound(path, value))

  private def setAt(
    value: DynamicValue,
    path: DynamicOptic,
    newVal: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    path.set(value, newVal).toRight(MigrationError.FieldNotFound(path, value))

  private def insertAt(
    value: DynamicValue,
    path: DynamicOptic,
    newVal: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value match {
      case DynamicValue.Record(fields) =>
        val fieldName = path.lastFieldName.getOrElse(
          return Left(MigrationError.InvalidAction(AddField(path, SchemaExpr.DefaultValue), "Cannot determine field name from optic"))
        )
        Right(DynamicValue.Record(fields :+ (fieldName -> newVal)))
      case other =>
        setAt(other, path, newVal)
    }

  private def removeAt(
    value: DynamicValue,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    value match {
      case DynamicValue.Record(fields) =>
        val fieldName = path.lastFieldName.getOrElse(
          return Left(MigrationError.InvalidAction(DropField(path), "Cannot determine field name from optic"))
        )
        Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
      case other =>
        Left(MigrationError.TypeMismatch(path, "Record", other.getClass.getSimpleName))
    }

  private def renameFieldAt(
    value: DynamicValue,
    path: DynamicOptic,
    to: String
  ): Either[MigrationError, DynamicValue] =
    value match {
      case DynamicValue.Record(fields) =>
        val fromName = path.lastFieldName.getOrElse(
          return Left(MigrationError.InvalidAction(Rename(path, to), "Cannot determine field name from optic"))
        )
        val updated = fields.map {
          case (`fromName`, v) => (to, v)
          case other           => other
        }
        if (updated == fields)
          Left(MigrationError.FieldNotFound(path, value))
        else
          Right(DynamicValue.Record(updated))
      case other =>
        Left(MigrationError.TypeMismatch(path, "Record", other.getClass.getSimpleName))
    }

  private def renameCaseAt(
    value: DynamicValue,
    path: DynamicOptic,
    from: String,
    to: String
  ): Either[MigrationError, DynamicValue] =
    value match {
      case DynamicValue.Enumeration(tag, v) if tag == from =>
        Right(DynamicValue.Enumeration(to, v))
      case _: DynamicValue.Enumeration =>
        Right(value) // different case, not affected
      case other =>
        Left(MigrationError.TypeMismatch(path, "Enumeration", other.getClass.getSimpleName))
    }

  private def evalExpr(
    expr: SchemaExpr[?],
    input: DynamicValue,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    expr.evalDynamic(input).left.map { err =>
      MigrationError.TransformFailed(path, err)
    }
}

object DynamicMigration {

  /** The identity migration — satisfies Migration.identity[A].apply(a) == Right(a) */
  val identity: DynamicMigration = DynamicMigration(Vector.empty)
}
