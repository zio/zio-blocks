package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * ZIO Schema Migration System
 * Pure, algebraic migration with serializable actions.
 */

sealed trait MigrationError {
  def message: String
}

object MigrationError {
  case class PathError(path: String, override val message: String) extends MigrationError
  case class TransformError(override val message: String) extends MigrationError
  case class ValidationError(override val message: String) extends MigrationError
  case class TypeError(expected: String, actual: String) extends MigrationError {
    def message: String = s"Expected $expected but got $actual"
  }
}

/** All migration actions are path-based and reversible */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  /** Add a new field with a default value */
  case class AddField(at: DynamicOptic, fieldName: String, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, default)
  }

  /** Remove a field (storing its value for reverse) */
  case class DropField(at: DynamicOptic, fieldName: String, defaultForReverse: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)
  }

  /** Rename a field from one name to another */
  case class Rename(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, to, from)
  }

  /** Set a field to a specific value */
  case class SetValue(at: DynamicOptic, fieldName: String, value: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort - needs original value
  }

  /** Wrap a field value in Option (Some) */
  case class Optionalize(at: DynamicOptic, fieldName: String) extends MigrationAction {
    def reverse: MigrationAction = this // Cannot fully reverse
  }
}

/** Pure data migration - fully serializable */
case class DynamicMigration(actions: Vector[MigrationAction]) {
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(v), action) => applyAction(v, action)
      case (left, _) => left
    }

  private def applyAction(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    val path = action.at.nodes

    if (path.isEmpty) {
      // Apply at root
      applyAtRoot(value, action)
    } else {
      // Navigate to target and apply
      navigateAndApply(value, path, 0, action)
    }
  }

  private def applyAtRoot(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    action match {
      case MigrationAction.AddField(_, fieldName, default) =>
        value match {
          case DynamicValue.Record(fields) =>
            if (fields.exists(_._1 == fieldName)) {
              Left(MigrationError.ValidationError(s"Field '$fieldName' already exists"))
            } else {
              Right(DynamicValue.Record(fields :+ (fieldName, default)))
            }
          case other =>
            Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.DropField(_, fieldName, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            val newFields = fields.filterNot(_._1 == fieldName)
            if (newFields.length == fields.length) {
              Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            } else {
              Right(DynamicValue.Record(newFields))
            }
          case other =>
            Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Rename(_, from, to) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == from)
            if (idx < 0) {
              Left(MigrationError.ValidationError(s"Field '$from' not found"))
            } else if (fields.exists(_._1 == to)) {
              Left(MigrationError.ValidationError(s"Field '$to' already exists"))
            } else {
              val (_, fieldValue) = fields(idx)
              Right(DynamicValue.Record(fields.updated(idx, (to, fieldValue))))
            }
          case other =>
            Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.SetValue(_, fieldName, newValue) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == fieldName)
            if (idx < 0) {
              Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            } else {
              Right(DynamicValue.Record(fields.updated(idx, (fieldName, newValue))))
            }
          case other =>
            Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Optionalize(_, fieldName) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == fieldName)
            if (idx < 0) {
              Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            } else {
              val (_, fieldValue) = fields(idx)
              // Wrap in Variant with "Some" case
              val optionValue = DynamicValue.Variant("Some", fieldValue)
              Right(DynamicValue.Record(fields.updated(idx, (fieldName, optionValue))))
            }
          case other =>
            Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }
    }
  }

  private def navigateAndApply(
    value: DynamicValue,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val node = path(pathIdx)
    val isLast = pathIdx == path.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) {
              Left(MigrationError.PathError(action.at.toString, s"Field '$name' not found"))
            } else {
              val (fieldName, fieldValue) = fields(fieldIdx)

              if (isLast) {
                applyAtRoot(fieldValue, action).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              } else {
                navigateAndApply(fieldValue, path, pathIdx + 1, action).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            }
          case other =>
            Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            if (index < 0 || index >= elements.length) {
              Left(MigrationError.PathError(action.at.toString, s"Index $index out of bounds"))
            } else {
              val element = elements(index)

              if (isLast) {
                applyAtRoot(element, action).map { newElement =>
                  DynamicValue.Sequence(elements.updated(index, newElement))
                }
              } else {
                navigateAndApply(element, path, pathIdx + 1, action).map { newElement =>
                  DynamicValue.Sequence(elements.updated(index, newElement))
                }
              }
            }
          case other =>
            Left(MigrationError.TypeError("Sequence", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Case(caseName) =>
        value match {
          case DynamicValue.Variant(actualCase, innerValue) =>
            if (actualCase != caseName) {
              Left(MigrationError.PathError(action.at.toString, s"Expected case '$caseName' but got '$actualCase'"))
            } else if (isLast) {
              applyAtRoot(innerValue, action).map { newInner =>
                DynamicValue.Variant(caseName, newInner)
              }
            } else {
              navigateAndApply(innerValue, path, pathIdx + 1, action).map { newInner =>
                DynamicValue.Variant(caseName, newInner)
              }
            }
          case other =>
            Left(MigrationError.TypeError("Variant", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Elements =>
        value match {
          case DynamicValue.Sequence(elements) =>
            // Apply to all elements
            val results = elements.map { elem =>
              if (isLast) applyAtRoot(elem, action)
              else navigateAndApply(elem, path, pathIdx + 1, action)
            }

            // Collect results - fail on first error
            results.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
              case (Right(acc), Right(v)) => Right(acc :+ v)
              case (Right(_), Left(e)) => Left(e)
              case (left, _) => left
            }.map(DynamicValue.Sequence(_))

          case other =>
            Left(MigrationError.TypeError("Sequence", other.getClass.getSimpleName))
        }

      case other =>
        Left(MigrationError.PathError(action.at.toString, s"Unsupported path node: $other"))
    }
  }
}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  /** Create a migration that adds a field at root */
  def addField(fieldName: String, default: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.AddField(DynamicOptic.root, fieldName, default)))

  /** Create a migration that drops a field at root */
  def dropField(fieldName: String, defaultForReverse: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.DropField(DynamicOptic.root, fieldName, defaultForReverse)))

  /** Create a migration that renames a field at root */
  def renameField(from: String, to: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root, from, to)))

  /** Create a migration that adds a field at a specific path */
  def addFieldAt(path: DynamicOptic, fieldName: String, default: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.AddField(path, fieldName, default)))

  /** Create a migration that drops a field at a specific path */
  def dropFieldAt(path: DynamicOptic, fieldName: String, defaultForReverse: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.DropField(path, fieldName, defaultForReverse)))

  /** Create a migration that renames a field at a specific path */
  def renameFieldAt(path: DynamicOptic, from: String, to: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.Rename(path, from, to)))
}

/** Typed migration - wraps DynamicMigration with schemas */
case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  /** Apply migration to transform A to B */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamic = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamic).flatMap { dv =>
      targetSchema.fromDynamicValue(dv) match {
        case Right(b) => Right(b)
        case Left(e) => Left(MigrationError.TransformError(e.toString))
      }
    }
  }

  /** Compose migrations sequentially */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /** Alias for ++ */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Reverse migration (structural inverse) */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {
  /** Identity migration - no changes */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)
}
