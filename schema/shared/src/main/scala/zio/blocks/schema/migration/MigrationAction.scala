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
    def reverse: MigrationAction = ??? // requires reverse transform
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
        case DynamicValue.None => default.apply(null).map(_.asInstanceOf[DynamicValue])
        case someValue => Right(someValue)
      }
    }
  }

  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, SchemaExpr.DefaultValue())
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      DynamicOptic.modify(at, value) { currentValue =>
        Right(DynamicValue.Some(currentValue))
      }
    }
  }

  final case class Join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = ??? // Complex reverse logic needed
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Extract values from source paths and combine them
      val sourceValues = sourcePaths.map(path =>
        DynamicOptic.get(path, value).left.map(err => MigrationError.PathError(path, err.toString))
      )
      sourceValues.sequence.flatMap { values =>
        combiner.apply(values.toArray).map(_.asInstanceOf[DynamicValue])
      }.map(result => DynamicOptic.set(at, value, result).getOrElse(value))
    }
  }

  final case class Split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = ??? // Complex reverse logic needed
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      DynamicOptic.get(at, value) match {
        case Right(valueToSplit) =>
          splitter.apply(valueToSplit).flatMap {
            case DynamicValue.Sequence(elements) if elements.size == targetPaths.size =>
              val updates = targetPaths.zip(elements).map { case (path, elem) =>
                DynamicOptic.set(path, value, elem).toRight(MigrationError.PathError(path, "Cannot set split value"))
              }
              updates.sequence.map(_ => value)
            case _ => Left(MigrationError.TransformationError(at, "Splitter must return sequence with correct number of elements"))
          }
        case Left(err) => Left(MigrationError.PathError(at, err.toString))
      }
    }
  }

  final case class ChangeType(at: DynamicOptic, converter: SchemaExpr[Any, _]) extends MigrationAction {
    def reverse: MigrationAction = ??? // Would need reverse converter
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      DynamicOptic.modify(at, value) { currentValue =>
        converter.apply(currentValue).map(_.asInstanceOf[DynamicValue])
      }
    }
  }
}
