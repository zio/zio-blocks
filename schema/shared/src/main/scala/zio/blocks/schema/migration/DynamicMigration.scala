package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}
import zio.blocks.schema.migration.MigrationError._
import zio.chunk.Chunk

/**
 * A pure, fully serializable representation of a migration.
 * This is the untyped core that operates on DynamicValue.
 */
final case class DynamicMigration(
  actions: Vector[MigrationAction]
) { self =>

  /**
   * Applies this migration to a DynamicValue, transforming it from one schema to another.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var currentValue: DynamicValue = value
    var errors: Seq[MigrationError] = Seq.empty

    for (action <- actions) {
      applyAction(action, currentValue) match {
        case Right(newValue) =>
          currentValue = newValue
        case Left(error) =>
          errors = errors :+ error
      }
    }

    if (errors.isEmpty) {
      Right(currentValue)
    } else if (errors.length == 1) {
      Left(errors.head)
    } else {
      Left(Accumulated(errors))
    }
  }

  private def applyAction(
    action: MigrationAction,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    action match {
      case MigrationAction.Identity(at) =>
        Right(value)

      case MigrationAction.AddField(at, default) =>
        // Get the parent record and add the new field
        at.get(value) match {
          case Some(parent) =>
            parent match {
              case record: DynamicValue.Record =>
                val newField = default.evalDynamic(()) match {
                  case Right(values) if values.nonEmpty => values.head
                  case _ => DynamicValue.Null
                }
                // Add the new field to the record
                val newFields = record.fields :+ (at.toString.split('.').last -> newField)
                Right(DynamicValue.Record(newFields))
              case _ =>
                Left(TransformationFailed(at, "Parent is not a record"))
            }
          case None =>
            // If path doesn't exist, we're adding at root
            Right(value)
        }

      case MigrationAction.DropField(at, _) =>
        at.get(value) match {
          case Some(parent) =>
            parent match {
              case record: DynamicValue.Record =>
                val fieldName = at.toString.split('.').last
                val newFields = record.fields.filter(_._1 != fieldName)
                Right(DynamicValue.Record(newFields))
              case _ =>
                Left(TransformationFailed(at, "Parent is not a record"))
            }
          case None =>
            // Field doesn't exist, nothing to drop
            Right(value)
        }

      case MigrationAction.Rename(at, newName) =>
        at.get(value) match {
          case Some(parent) =>
            parent match {
              case record: DynamicValue.Record =>
                val oldName = at.toString.split('.').last
                val newFields = record.fields.map { case (k, v) =>
                  if (k == oldName) (newName, v) else (k, v)
                }
                Right(DynamicValue.Record(newFields))
              case _ =>
                Left(TransformationFailed(at, "Parent is not a record"))
            }
          case None =>
            Left(MissingField(at, at.toString.split('.').last))
        }

      case MigrationAction.TransformValue(at, transform) =>
        at.get(value) match {
          case Some(targetValue) =>
            // Apply transformation
            transform.evalDynamic(()) match {
              case Right(values) if values.nonEmpty =>
                at.set(value, values.head)
              case Right(Seq.empty) =>
                Left(TransformationFailed(at, "Transform produced no values"))
              case Left(check) =>
                Left(TransformationFailed(at, s"Transform failed: $check"))
            }
          case None =>
            Left(MissingField(at, at.toString.split('.').last))
        }

      case MigrationAction.Mandate(at, default) =>
        at.get(value) match {
          case Some(DynamicValue.Null) =>
            // Replace null with default
            default.evalDynamic(()) match {
              case Right(values) if values.nonEmpty =>
                at.set(value, values.head)
              case _ =>
                at.set(value, DynamicValue.Null)
            }
          case Some(_) =>
            Right(value) // Already has a value
          case None =>
            // Field doesn't exist, add with default
            default.evalDynamic(()) match {
              case Right(values) if values.nonEmpty =>
                at.set(value, values.head)
              case _ =>
                Right(value)
            }
        }

      case MigrationAction.Optionalize(at) =>
        // Nothing to do - just keep the value as-is
        Right(value)

      case MigrationAction.ChangeType(at, converter) =>
        // Similar to TransformValue
        at.get(value) match {
          case Some(targetValue) =>
            converter.evalDynamic(()) match {
              case Right(values) if values.nonEmpty =>
                at.set(value, values.head)
              case _ =>
                Right(value)
            }
          case None =>
            Right(value)
        }

      case MigrationAction.RenameCase(at, from, to) =>
        // Handle enum/tag renaming
        value match {
          case variant: DynamicValue.Variant =>
            if (variant.tag == from) {
              Right(DynamicValue.Variant(to, variant.value))
            } else {
              Right(value)
            }
          case _ =>
            Left(TransformationFailed(at, "Value is not a variant"))
        }

      case MigrationAction.TransformCase(at, nestedActions) =>
        // Apply nested migration actions to the case
        value match {
          case variant: DynamicValue.Variant =>
            val nestedMigration = DynamicMigration(nestedActions)
            nestedMigration(variant.value).map { newValue =>
              DynamicValue.Variant(variant.tag, newValue)
            }
          case _ =>
            Left(TransformationFailed(at, "Value is not a variant"))
        }

      case MigrationAction.TransformElements(at, transform) =>
        at.get(value) match {
          case Some(seq: DynamicValue.Sequence) =>
            val transformedElements = seq.elements.map { elem =>
              transform.evalDynamic(()) match {
                case Right(values) if values.nonEmpty => values.head
                case _ => elem
              }
            }
            at.set(value, DynamicValue.Sequence(transformedElements))
          case _ =>
            Left(TransformationFailed(at, "Value is not a sequence"))
        }

      case MigrationAction.TransformKeys(at, transform) =>
        at.get(value) match {
          case Some(map: DynamicValue.Map) =>
            val transformedEntries = map.entries.map { case (k, v) =>
              transform.evalDynamic(()) match {
                case Right(values) if values.nonEmpty => (values.head, v)
                case _ => (k, v)
              }
            }
            at.set(value, DynamicValue.Map(transformedEntries))
          case _ =>
            Left(TransformationFailed(at, "Value is not a map"))
        }

      case MigrationAction.TransformValues(at, transform) =>
        at.get(value) match {
          case Some(map: DynamicValue.Map) =>
            val transformedEntries = map.entries.map { case (k, v) =>
              transform.evalDynamic(()) match {
                case Right(values) if values.nonEmpty => (k, values.head)
                case _ => (k, v)
              }
            }
            at.set(value, DynamicValue.Map(transformedEntries))
          case _ =>
            Left(TransformationFailed(at, "Value is not a map"))
        }

      case MigrationAction.Join(at, sourcePaths, combiner) =>
        // Combine multiple source values into one
        val sourceValues = sourcePaths.flatMap { path =>
          path.get(value).toSeq
        }
        combiner.evalDynamic(()) match {
          case Right(values) if values.nonEmpty =>
            at.set(value, values.head)
          case _ =>
            Right(value)
        }

      case MigrationAction.Split(at, targetPaths, splitter) =>
        // Split a value into multiple targets
        at.get(value) match {
          case Some(sourceValue) =>
            // This is simplified - actual implementation would be more complex
            Right(sourceValue)
          case None =>
            Right(value)
        }
    }
  }

  /**
   * Composes this migration with another migration sequentially.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(self.actions ++ that.actions)

  /**
   * Creates the structural reverse of this migration.
   * Note: This is a best-effort reverse; some transformations may not be reversible.
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.map(_.reverse).reverse)
}

object DynamicMigration {

  /**
   * Creates an identity migration that does nothing.
   */
  def identity: DynamicMigration =
    DynamicMigration(Vector(MigrationAction.Identity()))

  /**
   * Creates a migration from a single action.
   */
  def apply(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))
}
