package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}
import zio.blocks.schema.migration.MigrationError._

/**
 * Interpreter for applying DynamicMigration to DynamicValue.
 * This provides a functional approach to migration application.
 */
class DynamicMigrationInterpreter {

  /**
   * Applies a migration to a DynamicValue.
   */
  def apply(migration: DynamicMigration, value: DynamicValue): Either[MigrationError, DynamicValue] =
    migration(value)

  /**
   * Applies a single migration action.
   */
  def applyAction(
    action: MigrationAction,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.Identity(at) =>
        Right(value)

      case MigrationAction.AddField(at, default) =>
        applyAddField(at, default, value)

      case MigrationAction.DropField(at, _) =>
        applyDropField(at, value)

      case MigrationAction.Rename(at, newName) =>
        applyRename(at, newName, value)

      case MigrationAction.TransformValue(at, transform) =>
        applyTransformValue(at, transform, value)

      case MigrationAction.Mandate(at, default) =>
        applyMandate(at, default, value)

      case MigrationAction.Optionalize(at) =>
        Right(value) // No change needed

      case MigrationAction.ChangeType(at, converter) =>
        applyChangeType(at, converter, value)

      case MigrationAction.RenameCase(at, from, to) =>
        applyRenameCase(at, from, to, value)

      case MigrationAction.TransformCase(at, actions) =>
        applyTransformCase(at, actions, value)

      case MigrationAction.TransformElements(at, transform) =>
        applyTransformElements(at, transform, value)

      case MigrationAction.TransformKeys(at, transform) =>
        applyTransformKeys(at, transform, value)

      case MigrationAction.TransformValues(at, transform) =>
        applyTransformValues(at, transform, value)

      case MigrationAction.Join(at, sourcePaths, combiner) =>
        applyJoin(at, sourcePaths, combiner, value)

      case MigrationAction.Split(at, targetPaths, splitter) =>
        applySplit(at, targetPaths, splitter, value)
    }

  private def applyAddField(
    at: DynamicOptic,
    default: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    at.get(value) match {
      case Some(parent) =>
        parent match {
          case record: DynamicValue.Record =>
            val newFieldName = extractFieldName(at)
            val newFieldValue = default.evalDynamic(()) match {
              case Right(values) if values.nonEmpty => values.head
              case _ => DynamicValue.Null
            }
            // Check if field already exists
            if (record.fields.exists(_._1 == newFieldName)) {
              Right(value) // Field already exists
            } else {
              val newFields = record.fields :+ (newFieldName -> newFieldValue)
              at.set(value, DynamicValue.Record(newFields))
            }
          case _ =>
            Left(TransformationFailed(at, "Parent is not a record"))
        }
      case None =>
        // Adding to root - create a new record with the field
        val newFieldName = extractFieldName(at)
        val newFieldValue = default.evalDynamic(()) match {
          case Right(values) if values.nonEmpty => values.head
          case _ => DynamicValue.Null
        }
        Right(DynamicValue.Record(Seq(newFieldName -> newFieldValue)))
    }

  private def applyDropField(
    at: DynamicOptic,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    at.get(value) match {
      case Some(parent) =>
        parent match {
          case record: DynamicValue.Record =>
            val fieldName = extractFieldName(at)
            val newFields = record.fields.filter(_._1 != fieldName)
            at.set(value, DynamicValue.Record(newFields))
          case _ =>
            Left(TransformationFailed(at, "Parent is not a record"))
        }
      case None =>
        // Field doesn't exist, nothing to drop
        Right(value)
    }

  private def applyRename(
    at: DynamicOptic,
    newName: String,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    at.get(value) match {
      case Some(parent) =>
        parent match {
          case record: DynamicValue.Record =>
            val oldName = extractFieldName(at)
            val newFields = record.fields.map { case (k, v) =>
              if (k == oldName) (newName, v) else (k, v)
            }
            at.set(value, DynamicValue.Record(newFields))
          case _ =>
            Left(TransformationFailed(at, "Parent is not a record"))
        }
      case None =>
        Left(MissingField(at, extractFieldName(at)))
    }

  private def applyTransformValue(
    at: DynamicOptic,
    transform: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    at.get(value) match {
      case Some(targetValue) =>
        transform.evalDynamic(()) match {
          case Right(values) if values.nonEmpty =>
            at.set(value, values.head)
          case Right(Seq.empty) =>
            Left(TransformationFailed(at, "Transform produced no values"))
          case Left(check) =>
            Left(TransformationFailed(at, s"Transform failed: $check"))
        }
      case None =>
        Left(MissingField(at, extractFieldName(at)))
    }

  private def applyMandate(
    at: DynamicOptic,
    default: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    at.get(value) match {
      case Some(DynamicValue.Null) =>
        default.evalDynamic(()) match {
          case Right(values) if values.nonEmpty =>
            at.set(value, values.head)
          case _ =>
            at.set(value, DynamicValue.Null)
        }
      case Some(_) =>
        Right(value)
      case None =>
        default.evalDynamic(()) match {
          case Right(values) if values.nonEmpty =>
            at.set(value, values.head)
          case _ =>
            Right(value)
        }
    }

  private def applyChangeType(
    at: DynamicOptic,
    converter: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    applyTransformValue(at, converter, value)

  private def applyRenameCase(
    at: DynamicOptic,
    from: String,
    to: String,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value match {
      case variant: DynamicValue.Variant =>
        if (variant.tag == from) {
          Right(DynamicValue.Variant(to, variant.value))
        } else if (variant.tag == to) {
          Right(value) // Already renamed
        } else {
          Left(TransformationFailed(at, s"Case '$from' not found"))
        }
      case _ =>
        Left(TransformationFailed(at, "Value is not a variant"))
    }

  private def applyTransformCase(
    at: DynamicOptic,
    actions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value match {
      case variant: DynamicValue.Variant =>
        val nestedMigration = DynamicMigration(actions)
        nestedMigration(variant.value).map { newValue =>
          DynamicValue.Variant(variant.tag, newValue)
        }
      case _ =>
        Left(TransformationFailed(at, "Value is not a variant"))
    }

  private def applyTransformElements(
    at: DynamicOptic,
    transform: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
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

  private def applyTransformKeys(
    at: DynamicOptic,
    transform: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
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

  private def applyTransformValues(
    at: DynamicOptic,
    transform: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
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

  private def applyJoin(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    // Combine multiple source values
    val sourceValues = sourcePaths.flatMap { path =>
      path.get(value).toSeq
    }
    combiner.evalDynamic(()) match {
      case Right(values) if values.nonEmpty =>
        at.set(value, values.head)
      case _ =>
        Right(value)
    }
  }

  private def applySplit(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: zio.blocks.schema.SchemaExpr[?],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    // Simplified implementation
    at.get(value) match {
      case Some(sourceValue) =>
        Right(sourceValue)
      case None =>
        Right(value)
    }
  }

  private def extractFieldName(at: DynamicOptic): String =
    at.toString.split('.').lastOption.getOrElse("field")
}

object DynamicMigrationInterpreter {

  val default = new DynamicMigrationInterpreter()
}
