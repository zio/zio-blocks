package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}

object MigrationAction {

  // Record actions
  final case class AddField(at: DynamicOptic, default: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, SchemaExpr.DefaultValue())
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val path = DynamicOptic(at.nodes.dropRight(1).toIndexedSeq)
          for {
            defaultValue <- default.apply(null).map(_.asInstanceOf[DynamicValue])
            updatedValue <- DynamicOptic.modify(path, value) {
                             case DynamicValue.Record(fields) =>
                               if (fields.exists(_._1 == fieldName))
                                 Left(MigrationError.TransformationError(at, s"Field '$fieldName' already exists."))
                               else
                                 Right(DynamicValue.Record(fields :+ (fieldName -> defaultValue)))
                             case other => Left(MigrationError.PathError(at, s"Cannot add field '$fieldName' to non-record value: $other"))
                           }
          } yield updatedValue
        case _ => Left(MigrationError.PathError(at, "AddField requires a field name at the end of the path."))
      }
    }
  }

  final case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(fieldName)) =>
          val path = DynamicOptic(at.nodes.dropRight(1).toIndexedSeq)
          DynamicOptic.modify(path, value) {
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
          DynamicOptic.modify(path, value) {
            case DynamicValue.Record(fields) =>
              fields.indexWhere(_._1 == oldName) match {
                case -1 => Left(MigrationError.TransformationError(at, s"Field '$oldName' does not exist."))
                case i  => Right(DynamicValue.Record(fields.updated(i, (newName, fields(i)._2))))
              }
            case other => Left(MigrationError.PathError(at, s"Cannot rename field '$oldName' in non-record value: $other"))
          }
        case _ => Left(MigrationError.PathError(at, "RenameField requires a field name at the end of the path."))
      }
    }
  }

  final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = transform.reverse match {
      case Some(reverseTransform) => TransformValue(at, reverseTransform.asInstanceOf[SchemaExpr[Any, _]])
      case None => TransformValue(at, SchemaExpr.DefaultValue())
    }
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      DynamicOptic.modify(at, value) {
        transform(_).map(_.asInstanceOf[DynamicValue])
      }
  }

  // Enum actions
  final case class RenameCase(at: DynamicOptic, oldName: String, newName: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, newName, oldName)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      DynamicOptic.modify(at, value) {
        case DynamicValue.Variant(tag, v) if tag == oldName =>
          Right(DynamicValue.Variant(newName, v))
        case DynamicValue.Variant(tag, _) =>
            Left(MigrationError.TransformationError(at, s"Case '$oldName' does not match current variant tag '$tag'."))
        case other => Left(MigrationError.PathError(at, s"Cannot rename case in non-variant value: $other"))
      }
  }
  final case class TransformCase(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
        at.nodes.lastOption match {
            case Some(DynamicOptic.Node.Case(caseName)) =>
            val path = DynamicOptic(at.nodes.dropRight(1).toIndexedSeq)
            DynamicOptic.modify(path, value) {
                case DynamicValue.Variant(tag, v) if tag == caseName =>
                migration(v).map(newValue => DynamicValue.Variant(tag, newValue))
                case DynamicValue.Variant(tag, _) =>
                Left(MigrationError.TransformationError(at, s"Case '$caseName' does not match current variant tag '$tag'."))
                case other => Left(MigrationError.PathError(at, s"Cannot transform case in non-variant value: $other"))
            }
            case _ => Left(MigrationError.PathError(at, "TransformCase requires a case name at the end of the path."))
        }
    }
  }

  // Collection actions
  final case class TransformElements(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      DynamicOptic.modify(at.elements, value) { element =>
        migration(element)
      }
  }

  final case class TransformKeys(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      DynamicOptic.modify(at.mapKeys, value) { element =>
        migration(element)
      }
  }

  final case class TransformValues(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      DynamicOptic.modify(at.mapValues, value) { element =>
        migration(element)
      }
  }

  final case class Mandate(at: DynamicOptic, default: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      DynamicOptic.modify(at, value) {
        case DynamicValue.Variant("None", _) =>
          default.apply(null).map(_.asInstanceOf[DynamicValue])
        case DynamicValue.Variant("Some", v) =>
          Right(v)
        case other =>
          Right(other) // Already mandatory
      }
    }
  }

  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, SchemaExpr.DefaultValue())
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      DynamicOptic.modify(at, value) { v =>
        Right(DynamicValue.Variant("Some", v))
      }
    }
  }

  final case class Join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = combiner.reverse match {
      case Some(reverseExpr) => Split(at, sourcePaths, reverseExpr.asInstanceOf[SchemaExpr[Any, _]])
      case None => Split(at, sourcePaths, SchemaExpr.DefaultValue())
    }
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      for {
        sourceValues <- sourcePaths.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
          (acc, path) => acc.flatMap(vec => DynamicOptic.get(path, value).map(v => vec :+ v))
        }
        combined <- combiner.apply(sourceValues)
        result <- DynamicOptic.set(at, value, combined.asInstanceOf[DynamicValue])
      } yield result
    }
  }

  final case class Split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = splitter.reverse match {
      case Some(reverseExpr) => Join(at, targetPaths, reverseExpr.asInstanceOf[SchemaExpr[Any, _]])
      case None => Join(at, targetPaths, SchemaExpr.DefaultValue())
    }
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      for {
        sourceValue <- DynamicOptic.get(at, value)
        splitValues <- splitter.apply(sourceValue).map(_.asInstanceOf[Vector[DynamicValue]])
        _ <- if (splitValues.size == targetPaths.size) Right(()) 
             else Left(MigrationError.ConversionError(s"Split produced ${splitValues.size} values but expected ${targetPaths.size} paths"))
        result <- targetPaths.zip(splitValues).foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
          case (acc, (path, newValue)) => acc.flatMap(v => DynamicOptic.set(path, v, newValue))
        }
      } yield result
    }
  }

  final case class ChangeType(at: DynamicOptic, converter: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = converter.reverse match {
      case Some(reverseConverter) => ChangeType(at, reverseConverter.asInstanceOf[SchemaExpr[Any, _]])
      case None => ChangeType(at, SchemaExpr.DefaultValue())
    }
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      DynamicOptic.modify(at, value) { currentValue =>
        converter.apply(currentValue).map(_.asInstanceOf[DynamicValue])
      }
    }
  }
}
