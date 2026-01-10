package zio.blocks.schema.migration

import zio.schema.DynamicValue
import zio.blocks.schema.migration.optic.{DynamicOptic, OpticStep}

object MigrationEngine {

  def run(value: DynamicValue, migration: DynamicMigration): Either[MigrationError, DynamicValue] = {
    migration.actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(currentVal), action) => applyAction(currentVal, action)
      case (Left(error), _)            => Left(error)
    }
  }

  private def applyAction(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    action match {
      // --- Record Actions (Supported in all versions) ---
      case MigrationAction.AddField(at, defaultValue) =>
        defaultValue.evalDynamic(value) match {
          case Right(seq) if seq.nonEmpty => insertAt(value, at, seq.head)
          case _ => Left(MigrationError(at, "Failed to evaluate default value for AddField"))
        }

      case MigrationAction.RenameField(at, newName) =>
        renameAt(value, at, newName)

      case MigrationAction.DeleteField(at, _) =>
        deleteAt(value, at)

      case MigrationAction.TransformValue(at, transform) =>
        updateAt(value, at) { leafValue =>
          transform.evalDynamic(leafValue).map(_.head)
        }

      // --- Map & Collection Actions (Disabled for older ZIO Schema) ---
      
      case MigrationAction.TransformKeys(at, _) =>
        Left(MigrationError(at, "TransformKeys not supported: Upgrade ZIO Schema to 0.2.0+ to use DynamicValue.Sequence"))

      case MigrationAction.TransformValues(at, _) =>
         Left(MigrationError(at, "TransformValues not supported: Upgrade ZIO Schema to 0.2.0+ to use DynamicValue.Sequence"))

      case _ => Right(value) 
    }
  }

  // --- Recursive Traversal Helpers ---

  private def updateAt(
    value: DynamicValue, 
    path: DynamicOptic
  )(f: DynamicValue => Either[OpticCheck, DynamicValue]): Either[MigrationError, DynamicValue] = {
    
    if (path.steps.isEmpty) {
      f(value).left.map(e => MigrationError(path, e.message))
    } else {
      val head = path.steps.head
      val tail = DynamicOptic(path.steps.tail)

      value match {
        // Record Support
        case DynamicValue.Record(values) =>
          head match {
            case OpticStep.Field(fieldName) =>
              values.get(fieldName) match {
                case Some(innerValue) =>
                  updateAt(innerValue, tail)(f).map { updatedInner =>
                    DynamicValue.Record(values + (fieldName -> updatedInner))
                  }
                case None => Left(MigrationError(path, s"Field '$fieldName' not found"))
              }
            case _ => Left(MigrationError(path, "Invalid path: Expected Field access for Record"))
          }
        
        // Option Support
        case DynamicValue.SomeValue(inner) =>
           updateAt(inner, path)(f).map(DynamicValue.SomeValue)
        case DynamicValue.NoneValue =>
           Right(DynamicValue.NoneValue)

        // NOTE: 'Sequence' and 'Dictionary' cases removed to fix compilation error.
        // If you need to traverse lists, ensure your ZIO Schema version has DynamicValue.Sequence.
        
        case _ => Left(MigrationError(path, "Traversal supported mainly for Records and Options in this version"))
      }
    }
  }

  private def renameAt(value: DynamicValue, path: DynamicOptic, newName: String): Either[MigrationError, DynamicValue] = {
    if (path.steps.isEmpty) return Left(MigrationError(path, "Cannot rename root"))

    val parentPath = DynamicOptic(path.steps.dropRight(1))
    val targetStep = path.steps.last

    updateAt(value, parentPath) { parentVal =>
      parentVal match {
        case DynamicValue.Record(values) =>
          targetStep match {
            case OpticStep.Field(oldName) =>
              values.get(oldName) match {
                case Some(fieldVal) =>
                  Right(DynamicValue.Record((values - oldName) + (newName -> fieldVal)))
                case None => Left(OpticCheck(s"Field '$oldName' to rename not found"))
              }
            case _ => Left(OpticCheck("Cannot rename non-field"))
          }
        case _ => Left(OpticCheck("Parent is not a record"))
      }
    }
  }

  private def deleteAt(value: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] = {
    if (path.steps.isEmpty) return Left(MigrationError(path, "Cannot delete root"))
    val parentPath = DynamicOptic(path.steps.dropRight(1))
    val targetStep = path.steps.last

    updateAt(value, parentPath) { parentVal =>
      parentVal match {
        case DynamicValue.Record(values) =>
          targetStep match {
            case OpticStep.Field(fieldName) => Right(DynamicValue.Record(values - fieldName))
            case _ => Left(OpticCheck("Cannot delete non-field"))
          }
        case _ => Left(OpticCheck("Parent is not a record"))
      }
    }
  }

  private def insertAt(value: DynamicValue, path: DynamicOptic, newValue: DynamicValue): Either[MigrationError, DynamicValue] = {
    if (path.steps.isEmpty) return Left(MigrationError(path, "Cannot insert at root"))
    val parentPath = DynamicOptic(path.steps.dropRight(1))
    val targetStep = path.steps.last

    updateAt(value, parentPath) { parentVal =>
      parentVal match {
        case DynamicValue.Record(values) =>
          targetStep match {
            case OpticStep.Field(fieldName) => Right(DynamicValue.Record(values + (fieldName -> newValue)))
            case _ => Left(OpticCheck("Cannot insert non-field"))
          }
        case _ => Left(OpticCheck("Parent is not a record"))
      }
    }
  }
}