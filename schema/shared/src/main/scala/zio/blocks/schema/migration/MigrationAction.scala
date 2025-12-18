package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}

object MigrationAction {

  private def modify(
    optic: DynamicOptic,
    value: DynamicValue
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] = {
    def loop(
      nodes: List[DynamicOptic.Node],
      currentValue: DynamicValue
    ): Either[MigrationError, DynamicValue] =
      nodes match {
        case Nil => f(currentValue)
        case node :: tail =>
          node match {
            case DynamicOptic.Node.Field(name) =>
              currentValue match {
                case DynamicValue.Record(fields) =>
                  fields.indexWhere(_._1 == name) match {
                    case -1 =>
                      Left(MigrationError.PathError(optic, s"Field '$name' not found."))
                    case i =>
                      val (oldName, oldValue) = fields(i)
                      loop(tail, oldValue).map { newValue =>
                        DynamicValue.Record(fields.updated(i, oldName -> newValue))
                      }
                  }
                case _ => Left(MigrationError.PathError(optic, "Field optic can only be used on a record."))
              }
            case _ => Left(MigrationError.NotYetImplemented)
          }
      }
    loop(optic.nodes.toList, value)
  }

  // Record actions
  final case class AddField(at: DynamicOptic, default: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, SchemaExpr.DefaultValue())
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val path = DynamicOptic(at.nodes.dropRight(1).toIndexedSeq)
          for {
            defaultValue <- default.apply(null) // Assuming Constant or DefaultValue for now
            updatedValue <- modify(path, value) {
                             case DynamicValue.Record(fields) =>
                               if (fields.exists(_._1 == fieldName))
                                 Left(MigrationError.TransformationError(at, s"Field '$fieldName' already exists."))
                               else
                                 Right(DynamicValue.Record(fields :+ (fieldName -> defaultValue.asInstanceOf[DynamicValue])))
                             case other => Left(MigrationError.PathError(at, s"Cannot add field '$fieldName' to non-record value: $other"))
                           }
          } yield updatedValue
        case _ => Left(MigrationError.PathError(at, "AddField requires a field name at the end of the path."))
      }
    }
  }

  final case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val path = DynamicOptic(at.nodes.dropRight(1).toIndexedSeq)
          modify(path, value) {
            case DynamicValue.Record(fields) =>
              if (fields.exists(_._1 == fieldName))
                Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
              else
                Left(MigrationError.TransformationError(at, s"Field '$fieldName' does not exist."))
            case other => Left(MigrationError.PathError(at, s"Cannot drop field '$fieldName' from non-record value: $other"))
          }
        case _ => Left(MigrationError.PathError(at, "DropField requires a field name at the end of the path."))
      }
    }
  }

  final case class RenameField(at: DynamicOptic, newName: String) extends MigrationAction {
    def reverse: MigrationAction = {
      val oldName = at.nodes.last.asInstanceOf[DynamicOptic.Node.Field].name
      val newPath = DynamicOptic(at.nodes.dropRight(1) :+ DynamicOptic.Node.Field(newName))
      RenameField(newPath, oldName)
    }
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(oldName)) =>
          val path = DynamicOptic(at.nodes.dropRight(1).toIndexedSeq)
          modify(path, value) {
            case DynamicValue.Record(fields) =>
              fields.indexWhere(_._1 == oldName) match {
                case -1 =>
                  Left(MigrationError.TransformationError(at, s"Field '$oldName' does not exist."))
                case i =>
                  if (fields.exists(_._1 == newName))
                    Left(MigrationError.TransformationError(at, s"Field '$newName' already exists."))
                  else {
                    val (name, value) = fields(i)
                    Right(DynamicValue.Record(fields.updated(i, newName -> value)))
                  }
              }
            case other => Left(MigrationError.PathError(at, s"Cannot rename field '$oldName' in non-record value: $other"))
          }
        case _ => Left(MigrationError.PathError(at, "RenameField requires a field name at the end of the path."))
      }
    }
  }

  final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = ??? // requires reverse transform
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }

  // Enum actions
  final case class RenameCase(at: DynamicOptic, oldName: String, newName: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, newName, oldName)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }
  final case class TransformCase(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }

  // Collection actions
  final case class TransformElements(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }

  final case class TransformKeys(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }

  final case class TransformValues(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }
}
