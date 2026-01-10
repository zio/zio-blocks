package zio.blocks.schema.migration

import zio.schema.DynamicValue
import zio.blocks.schema.migration.optic.{DynamicOptic, OpticStep}
import scala.collection.immutable.ListMap

object MigrationEngine {

  def run(value: DynamicValue, migration: DynamicMigration): Either[MigrationError, DynamicValue] = {
    migration.actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(currentVal), action) => applyAction(currentVal, action)
      case (Left(error), _)            => Left(error)
    }
  }

  private def applyAction(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    action match {
      // --- Record Actions ---
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

      // --- Collection Actions (FIXED) ---
      case MigrationAction.TransformElements(at, transform) =>
        updateAt(value, at) {
          case DynamicValue.Sequence(values) =>
            val newValues = values.map { v =>
              transform.evalDynamic(v).map(_.head)
            }
            if (newValues.exists(_.isLeft)) Left(OpticCheck("Error transforming elements"))
            else Right(DynamicValue.Sequence(newValues.map(_.right.get)))
          case other => Right(other)
        }

      // --- Map Actions (FIXED) ---
      case MigrationAction.TransformKeys(at, transform) =>
        updateAt(value, at) {
          case DynamicValue.Dictionary(entries) =>
            val newEntries = entries.map { case (k, v) =>
               transform.evalDynamic(k).map(_.head).map(newK => (newK, v))
            }
            if (newEntries.exists(_.isLeft)) Left(OpticCheck("Error transforming keys"))
            else Right(DynamicValue.Dictionary(newEntries.map(_.right.get)))
          case other => Right(other)
        }

      case MigrationAction.TransformValues(at, transform) =>
        updateAt(value, at) {
           case DynamicValue.Dictionary(entries) =>
             val newEntries = entries.map { case (k, v) =>
               transform.evalDynamic(v).map(_.head).map(newV => (k, newV))
             }
             if (newEntries.exists(_.isLeft)) Left(OpticCheck("Error transforming values"))
             else Right(DynamicValue.Dictionary(newEntries.map(_.right.get)))
           case other => Right(other)
        }

      // --- Enum Actions (FIXED) ---
      case MigrationAction.RenameCase(at, from, to) =>
        updateAt(value, at) {
          // DynamicValue.Enumeration represents Sum Types (Enums)
          case DynamicValue.Enumeration((id, inner)) if id == from =>
            Right(DynamicValue.Enumeration((to, inner)))
          case other => Right(other)
        }

      case MigrationAction.TransformCase(at, innerActions) =>
        updateAt(value, at) {
          case DynamicValue.Enumeration((id, inner)) =>
            // Recursively run the inner migration on the content of the case
            val subMigration = DynamicMigration(innerActions)
            MigrationEngine.run(inner, subMigration).map { newInner =>
              DynamicValue.Enumeration((id, newInner))
            }.left.map(e => OpticCheck(e.message))
          case other => Right(other)
        }

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
        
        // Support for traversing Options (Some/None)
        case DynamicValue.SomeValue(inner) =>
           updateAt(inner, path)(f).map(DynamicValue.SomeValue)
        case DynamicValue.NoneValue =>
           Right(DynamicValue.NoneValue)

        // Support for traversing Enums
        case DynamicValue.Enumeration((id, inner)) =>
           updateAt(inner, path)(f).map(updatedInner => DynamicValue.Enumeration((id, updatedInner)))

        case _ => Left(MigrationError(path, "Traversal supported mainly for Records, Enums and Options"))
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