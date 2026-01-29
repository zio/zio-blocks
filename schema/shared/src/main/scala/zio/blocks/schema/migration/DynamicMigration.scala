package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaExpr}
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.MigrationError._

/**
 * An untyped, fully serializable migration that operates on DynamicValue.
 *
 * DynamicMigration consists of a sequence of [[MigrationAction]] that can be
 * applied to transform values between schema versions. It supports composition
 * and reversal for bidirectional migrations.
 *
 * @param actions the sequence of migration actions
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Applies this migration to a DynamicValue.
   *
   * @param value the input value
   * @return either an error or the migrated value
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(currentValue), action) => applyAction(action, currentValue)
      case (Left(error), _)              => Left(error)
    }

  /**
   * Composes two migrations sequentially.
   *
   * The resulting migration first applies this migration, then applies `that`.
   *
   * @param that the migration to apply after this one
   * @return the composed migration
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Alias for `++`.
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the reverse of this migration.
   *
   * The reverse of a sequence [a1, a2, ..., an] is [an.reverse, ..., a2.reverse, a1.reverse].
   */
  def reverse: DynamicMigration =
    DynamicMigration(MigrationAction.reverseActions(actions))

  // ==========================================================================
  // Action Application
  // ==========================================================================

  private def applyAction(action: MigrationAction, value: DynamicValue): Either[MigrationError, DynamicValue] =
    action match {
      case AddField(at, default) =>
        applyAddField(at, default, value)

      case DropField(at, _) =>
        applyDropField(at, value)

      case Rename(at, to) =>
        applyRename(at, to, value)

      case TransformValue(at, transform, _) =>
        applyTransformValue(at, transform, value)

      case Mandate(at, default) =>
        applyMandate(at, default, value)

      case Optionalize(at) =>
        applyOptionalize(at, value)

      case Join(at, sourcePaths, combiner) =>
        applyJoin(at, sourcePaths, combiner, value)

      case Split(at, targetPaths, splitter) =>
        applySplit(at, targetPaths, splitter, value)

      case ChangeType(at, converter, _) =>
        applyChangeType(at, converter, value)

      case RenameCase(at, from, to) =>
        applyRenameCase(at, from, to, value)

      case TransformCase(at, caseName, caseActions) =>
        applyTransformCase(at, caseName, caseActions, value)

      case TransformElements(at, transform) =>
        applyTransformElements(at, transform, value)

      case TransformKeys(at, transform) =>
        applyTransformKeys(at, transform, value)

      case TransformValues(at, transform) =>
        applyTransformValues(at, transform, value)
    }

  // ==========================================================================
  // Record Actions
  // ==========================================================================

  private def applyAddField(
    at: DynamicOptic,
    default: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    // Convert SchemaExpr to DynamicSchemaExpr and evaluate
    val dynamicExpr = DynamicSchemaExpr.fromSchemaExpr(default)
    DynamicSchemaExpr.eval(dynamicExpr, value).flatMap { defaultValue =>
      // Get the parent path and field name
      val nodes = at.nodes
      if (nodes.isEmpty) {
        Left(ValidationError("Cannot add field at root"))
      } else {
        nodes.last match {
          case DynamicOptic.Node.Field(fieldName) =>
            val parentPath = DynamicOptic(nodes.init)
            if (parentPath.nodes.isEmpty) {
              // Adding to root record
              value match {
                case record: DynamicValue.Record =>
                  if (record.fields.exists(_._1 == fieldName)) {
                    Left(FieldError(at, fieldName, "Field already exists"))
                  } else {
                    Right(DynamicValue.Record(record.fields :+ (fieldName, defaultValue)))
                  }
                case _ =>
                  Left(TypeMismatchError(at, "Record", value.valueType.toString))
              }
            } else {
              // Adding to nested record
              modifyAtPath(value, parentPath) {
                case record: DynamicValue.Record =>
                  if (record.fields.exists(_._1 == fieldName)) {
                    Left(FieldError(at, fieldName, "Field already exists"))
                  } else {
                    Right(DynamicValue.Record(record.fields :+ (fieldName, defaultValue)))
                  }
                case other =>
                  Left(TypeMismatchError(parentPath, "Record", other.valueType.toString))
              }
            }
          case _ =>
            Left(UnsupportedOperationError(at, "AddField requires a field path"))
        }
      }
    }
  }

  private def applyDropField(
    at: DynamicOptic,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val nodes = at.nodes
    if (nodes.isEmpty) {
      Left(ValidationError("Cannot drop field at root"))
    } else {
      nodes.last match {
        case DynamicOptic.Node.Field(fieldName) =>
          val parentPath = DynamicOptic(nodes.init)
          if (parentPath.nodes.isEmpty) {
            // Dropping from root record
            value match {
              case record: DynamicValue.Record =>
                val newFields = record.fields.filterNot(_._1 == fieldName)
                if (newFields.length == record.fields.length) {
                  Left(FieldError(at, fieldName, "Field not found"))
                } else {
                  Right(DynamicValue.Record(newFields))
                }
              case _ =>
                Left(TypeMismatchError(at, "Record", value.valueType.toString))
            }
          } else {
            // Dropping from nested record
            modifyAtPath(value, parentPath) {
              case record: DynamicValue.Record =>
                val newFields = record.fields.filterNot(_._1 == fieldName)
                if (newFields.length == record.fields.length) {
                  Left(FieldError(at, fieldName, "Field not found"))
                } else {
                  Right(DynamicValue.Record(newFields))
                }
              case other =>
                Left(TypeMismatchError(parentPath, "Record", other.valueType.toString))
            }
          }
        case _ =>
          Left(UnsupportedOperationError(at, "DropField requires a field path"))
      }
    }
  }

  private def applyRename(
    at: DynamicOptic,
    to: String,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val nodes = at.nodes
    if (nodes.isEmpty) {
      Left(ValidationError("Cannot rename root"))
    } else {
      val parentPath = DynamicOptic(nodes.init)
      nodes.last match {
        case DynamicOptic.Node.Field(oldName) =>
          val renameFn: DynamicValue => Either[MigrationError, DynamicValue] = {
            case record: DynamicValue.Record =>
              val index = record.fields.indexWhere(_._1 == oldName)
              if (index < 0) {
                Left(FieldError(at, oldName, "Field not found"))
              } else if (record.fields.exists(_._1 == to)) {
                Left(FieldError(at, to, "Target field name already exists"))
              } else {
                val (_, fieldValue) = record.fields(index)
                val newFields = record.fields.updated(index, (to, fieldValue))
                Right(DynamicValue.Record(newFields))
              }
            case other =>
              Left(TypeMismatchError(at, "Record", other.valueType.toString))
          }

          if (parentPath.nodes.isEmpty) {
            renameFn(value)
          } else {
            modifyAtPath(value, parentPath)(renameFn)
          }

        case _ =>
          Left(UnsupportedOperationError(at, "Can only rename fields"))
      }
    }
  }

  private def applyTransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val selection = value.get(at)
    selection.values.flatMap(_.headOption) match {
      case Some(currentValue) =>
        val dynamicExpr = DynamicSchemaExpr.fromSchemaExpr(transform)
        // Evaluate with the current value as context
        DynamicSchemaExpr.eval(dynamicExpr, value).flatMap { newValue =>
          value.setOrFail(at, newValue).left.map(e => EvaluationError(at, e.message))
        }
      case None =>
        Left(PathNotFound(at))
    }
  }

  private def applyMandate(
    at: DynamicOptic,
    default: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val selection = value.get(at)
    selection.values.flatMap(_.headOption) match {
      case Some(DynamicValue.Null) | None =>
        val dynamicExpr = DynamicSchemaExpr.fromSchemaExpr(default)
        DynamicSchemaExpr.eval(dynamicExpr, value).flatMap { defaultValue =>
          value.setOrFail(at, defaultValue).left.map(e => EvaluationError(at, e.message))
        }
      case Some(_) =>
        Right(value)
    }
  }

  private def applyOptionalize(
    at: DynamicOptic,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    // Optionalize wraps the value in an Option (Some)
    // This is primarily a type-level change in the schema
    // At the DynamicValue level, we just pass through
    Right(value)
  }

  private def applyJoin(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    // Collect values from source paths
    val sourceValues = sourcePaths.map(path =>
      value.get(path).values.flatMap(_.headOption).toRight(PathNotFound(path))
    )

    // Sequence the results
    sourceValues.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
      case (Right(acc), Right(v)) => Right(acc :+ v)
      case (Left(e), _)           => Left(e)
      case (_, Left(e))           => Left(e)
    } match {
      case Right(sv) =>
        // For now, just concatenate string values
        // A full implementation would evaluate the combiner expression
        val joined = sv.collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) => s }.mkString(" ")
        val parentPath = DynamicOptic(at.nodes.init)
        val fieldName = at.nodes.last match {
          case DynamicOptic.Node.Field(name) => name
          case _                             => return Left(UnsupportedOperationError(at, "Join requires a field path"))
        }

        modifyAtPath(value, parentPath) {
          case record: DynamicValue.Record =>
            if (record.fields.exists(_._1 == fieldName)) {
              Left(FieldError(at, fieldName, "Field already exists"))
            } else {
              Right(DynamicValue.Record(record.fields :+ (fieldName, DynamicValue.Primitive(PrimitiveValue.String(joined)))))
            }
          case other =>
            Left(TypeMismatchError(parentPath, "Record", other.valueType.toString))
        }
      case Left(e) => Left(e)
    }
  }

  private def applySplit(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    // Get the value to split
    val selection = value.get(at)
    selection.values.flatMap(_.headOption) match {
      case Some(DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        // Simple split by whitespace for demonstration
        val parts = s.split("\\s+").toVector
        var result: Either[MigrationError, DynamicValue] = Right(value)

        for ((path, part) <- targetPaths.zip(parts)) {
          result = result.flatMap { v =>
            val parentPath = DynamicOptic(path.nodes.init)
            val fieldName = path.nodes.last match {
              case DynamicOptic.Node.Field(name) => name
              case _                             => return Left(UnsupportedOperationError(path, "Split target must be a field path"))
            }

            modifyAtPath(v, parentPath) {
              case record: DynamicValue.Record =>
                if (record.fields.exists(_._1 == fieldName)) {
                  Left(FieldError(path, fieldName, "Field already exists"))
                } else {
                  Right(DynamicValue.Record(record.fields :+ (fieldName, DynamicValue.Primitive(PrimitiveValue.String(part)))))
                }
              case other =>
                Left(TypeMismatchError(parentPath, "Record", other.valueType.toString))
            }
          }
        }
        result

      case Some(other) =>
        Left(TypeMismatchError(at, "String", other.valueType.toString))
      case None =>
        Left(PathNotFound(at))
    }
  }

  private def applyChangeType(
    at: DynamicOptic,
    converter: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    // For primitive-to-primitive conversions
    applyTransformValue(at, converter, value)
  }

  // ==========================================================================
  // Enum Actions
  // ==========================================================================

  private def applyRenameCase(
    at: DynamicOptic,
    from: String,
    to: String,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val selection = value.get(at)
    selection.values.flatMap(_.headOption) match {
      case Some(variant: DynamicValue.Variant) =>
        if (variant.caseName == from) {
          val renamed = DynamicValue.Variant(to, variant.caseValue)
          value.setOrFail(at, renamed).left.map(e => EvaluationError(at, e.message))
        } else {
          Right(value) // Not the case we're renaming
        }
      case Some(other) =>
        Left(TypeMismatchError(at, "Variant", other.valueType.toString))
      case None =>
        Left(PathNotFound(at))
    }
  }

  private def applyTransformCase(
    at: DynamicOptic,
    caseName: String,
    caseActions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val selection = value.get(at)
    selection.values.flatMap(_.headOption) match {
      case Some(variant: DynamicValue.Variant) =>
        if (variant.caseName == caseName) {
          // Apply the case-specific migration
          val caseMigration = DynamicMigration(caseActions)
          caseMigration(variant.caseValue) match {
            case Right(newCaseValue) =>
              val transformed = DynamicValue.Variant(caseName, newCaseValue)
              value.setOrFail(at, transformed).left.map(e => EvaluationError(at, e.message))
            case Left(e) => Left(e)
          }
        } else {
          Right(value) // Not the case we're transforming
        }
      case Some(other) =>
        Left(TypeMismatchError(at, "Variant", other.valueType.toString))
      case None =>
        Left(PathNotFound(at))
    }
  }

  // ==========================================================================
  // Collection Actions
  // ==========================================================================

  private def applyTransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val selection = value.get(at)
    selection.values.flatMap(_.headOption) match {
      case Some(seq: DynamicValue.Sequence) =>
        val dynamicExpr = DynamicSchemaExpr.fromSchemaExpr(transform)
        val transformedElements = seq.elements.map { elem =>
          DynamicSchemaExpr.eval(dynamicExpr, elem)
        }

        transformedElements.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
          case (Right(acc), Right(v)) => Right(acc :+ v)
          case (Left(e), _)           => Left(e)
          case (_, Left(e))           => Left(e)
        } match {
          case Right(newElements) =>
            value.setOrFail(at, DynamicValue.Sequence(newElements)).left.map(e => EvaluationError(at, e.message))
          case Left(e) => Left(e)
        }
      case Some(other) =>
        Left(TypeMismatchError(at, "Sequence", other.valueType.toString))
      case None =>
        Left(PathNotFound(at))
    }
  }

  // ==========================================================================
  // Map Actions
  // ==========================================================================

  private def applyTransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val selection = value.get(at)
    selection.values.flatMap(_.headOption) match {
      case Some(map: DynamicValue.Map) =>
        val dynamicExpr = DynamicSchemaExpr.fromSchemaExpr(transform)
        val transformedEntries = map.entries.map { case (k, v) =>
          DynamicSchemaExpr.eval(dynamicExpr, k).map(newKey => (newKey, v))
        }

        transformedEntries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
          case (Right(acc), Right(entry)) => Right(acc :+ entry)
          case (Left(e), _)               => Left(e)
          case (_, Left(e))               => Left(e)
        } match {
          case Right(newEntries) =>
            value.setOrFail(at, DynamicValue.Map(newEntries)).left.map(e => EvaluationError(at, e.message))
          case Left(e) => Left(e)
        }
      case Some(other) =>
        Left(TypeMismatchError(at, "Map", other.valueType.toString))
      case None =>
        Left(PathNotFound(at))
    }
  }

  private def applyTransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[_, _],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val selection = value.get(at)
    selection.values.flatMap(_.headOption) match {
      case Some(map: DynamicValue.Map) =>
        val dynamicExpr = DynamicSchemaExpr.fromSchemaExpr(transform)
        val transformedEntries = map.entries.map { case (k, v) =>
          DynamicSchemaExpr.eval(dynamicExpr, v).map(newVal => (k, newVal))
        }

        transformedEntries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
          case (Right(acc), Right(entry)) => Right(acc :+ entry)
          case (Left(e), _)               => Left(e)
          case (_, Left(e))               => Left(e)
        } match {
          case Right(newEntries) =>
            value.setOrFail(at, DynamicValue.Map(newEntries)).left.map(e => EvaluationError(at, e.message))
          case Left(e) => Left(e)
        }
      case Some(other) =>
        Left(TypeMismatchError(at, "Map", other.valueType.toString))
      case None =>
        Left(PathNotFound(at))
    }
  }

  // ==========================================================================
  // Helper Methods
  // ==========================================================================

  private def modifyAtPath(
    value: DynamicValue,
    path: DynamicOptic
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] = {
    if (path.nodes.isEmpty) {
      f(value)
    } else {
      // Get the value at the path
      val selection = value.get(path)
      selection.values.flatMap(_.headOption) match {
        case Some(targetValue) =>
          f(targetValue) match {
            case Right(newValue) =>
              value.setOrFail(path, newValue).left.map(e => EvaluationError(path, e.message))
            case Left(e) => Left(e)
          }
        case None =>
          Left(PathNotFound(path))
      }
    }
  }
}

object DynamicMigration {

  /**
   * Creates an identity migration that returns the input unchanged.
   */
  def identity: DynamicMigration = DynamicMigration(Vector.empty)

  /**
   * Creates a migration from a single action.
   */
  def single(action: MigrationAction): DynamicMigration = DynamicMigration(Vector(action))

  /**
   * Creates a migration from multiple actions.
   */
  def apply(actions: MigrationAction*): DynamicMigration = DynamicMigration(actions.toVector)
}
