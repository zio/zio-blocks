package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A single atomic migration action that can be applied to a DynamicValue.
 *
 * Each action represents a structural transformation on data. Actions are
 * designed to be composable, serializable, and reversible where semantically
 * meaningful.
 *
 * The core set of actions covers the most common schema evolution patterns:
 *
 * **Field Operations:**
 *   - [[MigrationAction.RenameField]] - Rename a field in a record
 *   - [[MigrationAction.DropField]] - Remove a field (non-reversible)
 *   - [[MigrationAction.AddField]] - Add a field with default value
 *   - [[MigrationAction.Optionalize]] - Make a field optional (wrap in Some)
 *   - [[MigrationAction.Mandate]] - Make optional field required (extract from
 *     Some)
 *
 * **Case Operations:**
 *   - [[MigrationAction.RenameCase]] - Rename a case in an enum/variant
 *   - [[MigrationAction.RemoveCase]] - Remove a case (non-reversible)
 *
 * **Advanced Operations (using SchemaExpr):**
 *   - [[MigrationAction.Join]] - Combine multiple fields into one
 *   - [[MigrationAction.Split]] - Split one field into multiple
 *   - [[MigrationAction.ChangeType]] - Transform field type with coercion
 *   - [[MigrationAction.TransformCase]] - Transform case value
 *
 * Example usage:
 * {{{
 *   val action = MigrationAction.RenameField("oldName", "newName")
 *   val result = action.apply(dynamicValue)
 *
 *   // Reversible actions can be inverted
 *   val reverseAction = action.reverse  // Right(RenameField("newName", "oldName"))
 * }}}
 *
 * @see
 *   [[DynamicMigration]] for composing multiple actions
 * @see
 *   [[Migration]] for type-safe migrations between schemas
 */
sealed trait MigrationAction extends Product with Serializable {

  /**
   * Apply this migration action to a DynamicValue.
   *
   * @param value
   *   The input value to transform
   * @return
   *   Either an error or the transformed value
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]

  /**
   * Attempt to reverse this migration action.
   *
   * @return
   *   Either the reversed action or an error if not reversible
   */
  def reverse: Either[MigrationError, MigrationAction]
}

object MigrationAction {

  // ============================================================================
  // Record Actions
  // ============================================================================

  /**
   * Remove a field from a record.
   *
   * This action is not reversible since the removed data cannot be recovered.
   *
   * @param fieldName
   *   The name of the field to drop
   */
  final case class DropField(fieldName: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
        case _ =>
          Left(
            MigrationError.TypeMismatch(
              DynamicOptic.root,
              "Record",
              value.getClass.getSimpleName
            )
          )
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"DropField($fieldName) - cannot recover dropped data"))
  }

  /**
   * Rename a field in a record.
   *
   * This action is fully reversible.
   *
   * @param oldName
   *   The current field name
   * @param newName
   *   The new field name
   */
  final case class RenameField(oldName: String, newName: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          val renamed = fields.map {
            case (name, v) if name == oldName => (newName, v)
            case other                        => other
          }
          Right(DynamicValue.Record(renamed))
        case _ =>
          Left(
            MigrationError.TypeMismatch(
              DynamicOptic.root,
              "Record",
              value.getClass.getSimpleName
            )
          )
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Right(RenameField(newName, oldName))
  }

  /**
   * Add a new field to a record with a constant default value.
   *
   * This is reversible via DropField.
   *
   * @param fieldName
   *   The name of the new field
   * @param defaultValue
   *   The default value for the new field
   */
  final case class AddField(
    fieldName: String,
    defaultValue: DynamicValue
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          // Check if field already exists
          if (fields.exists(_._1 == fieldName)) {
            Right(value) // Field already exists, no-op
          } else {
            Right(DynamicValue.Record(fields :+ (fieldName -> defaultValue)))
          }
        case _ =>
          Left(
            MigrationError.TypeMismatch(
              DynamicOptic.root,
              "Record",
              value.getClass.getSimpleName
            )
          )
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Right(DropField(fieldName))
  }

  /**
   * Make a field optional by wrapping its value in Some.
   *
   * If the field is missing, it becomes None. This is not fully reversible
   * since None cases lose information.
   *
   * @param fieldName
   *   The field to make optional
   */
  final case class Optionalize(fieldName: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          val updated = fields.map {
            case (name, v) if name == fieldName =>
              // Wrap in Some variant
              (name, DynamicValue.Variant("Some", v))
            case other => other
          }
          // If field was missing, add as None
          if (!fields.exists(_._1 == fieldName)) {
            Right(
              DynamicValue.Record(
                updated :+ (fieldName -> DynamicValue.Variant("None", DynamicValue.Record(Vector.empty)))
              )
            )
          } else {
            Right(DynamicValue.Record(updated))
          }
        case _ =>
          Left(
            MigrationError.TypeMismatch(
              DynamicOptic.root,
              "Record",
              value.getClass.getSimpleName
            )
          )
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(
        MigrationError.NotReversible(
          s"Optionalize($fieldName) - reverse would lose None cases"
        )
      )
  }

  /**
   * Make an optional field required by extracting from Some or using a default
   * for None.
   *
   * @param fieldName
   *   The field to make mandatory
   * @param defaultForNone
   *   Default value to use when encountering None
   */
  final case class Mandate(
    fieldName: String,
    defaultForNone: DynamicValue
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          fields.find(_._1 == fieldName) match {
            case Some((_, DynamicValue.Variant("None", _))) =>
              // Replace None with default
              val updated = fields.map {
                case (name, _) if name == fieldName => (name, defaultForNone)
                case other                          => other
              }
              Right(DynamicValue.Record(updated))

            case Some((_, DynamicValue.Variant("Some", innerValue))) =>
              // Extract from Some
              val updated = fields.map {
                case (name, _) if name == fieldName => (name, innerValue)
                case other                          => other
              }
              Right(DynamicValue.Record(updated))

            case Some(_) =>
              // Field exists but not an Option, keep as-is
              Right(value)

            case None =>
              // Field missing, add with default
              Right(DynamicValue.Record(fields :+ (fieldName -> defaultForNone)))
          }
        case _ =>
          Left(
            MigrationError.TypeMismatch(
              DynamicOptic.root,
              "Record",
              value.getClass.getSimpleName
            )
          )
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Right(Optionalize(fieldName))
  }

  // ============================================================================
  // Enum/Variant Actions
  // ============================================================================

  /**
   * Rename a case in an enum/variant.
   *
   * This action is fully reversible.
   *
   * @param oldName
   *   The current case name
   * @param newName
   *   The new case name
   */
  final case class RenameCase(oldName: String, newName: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Variant(name, innerValue) if name == oldName =>
          Right(DynamicValue.Variant(newName, innerValue))
        case other =>
          Right(other) // Not the case we're renaming, pass through
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Right(RenameCase(newName, oldName))
  }

  /**
   * Remove a case from an enum/variant.
   *
   * If data with the removed case is encountered, it returns an error. This is
   * not reversible.
   *
   * @param caseName
   *   The case to remove
   */
  final case class RemoveCase(caseName: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Variant(name, _) if name == caseName =>
          Left(MigrationError.UnknownCase(DynamicOptic.root, caseName))
        case other =>
          Right(other)
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(
        MigrationError.NotReversible(
          s"RemoveCase($caseName) - cannot recover removed case"
        )
      )
  }

  // ============================================================================
  // Advanced Actions (using SchemaExpr)
  // ============================================================================

  /**
   * Join multiple fields into a single field using an expression.
   *
   * Example: Join firstName + lastName into fullName
   *
   * @param fieldNames
   *   The fields to join
   * @param resultName
   *   The name of the resulting field
   * @param expr
   *   Expression to compute the joined value from the fields
   */
  final case class Join(
    fieldNames: Vector[String],
    resultName: String,
    expr: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          val remaining = fields.filterNot(f => fieldNames.contains(f._1))
          expr.evalDynamic(value) match {
            case Right(results) if results.nonEmpty =>
              Right(DynamicValue.Record(remaining :+ (resultName -> results.head)))
            case Right(_) =>
              Left(
                MigrationError.EvaluationFailed(
                  DynamicOptic.root,
                  s"Join expression returned no value"
                )
              )
            case Left(err) =>
              Left(MigrationError.EvaluationFailed(DynamicOptic.root, err.toString))
          }
        case _ =>
          Left(MigrationError.TypeMismatch(DynamicOptic.root, "Record", value.getClass.getSimpleName))
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(
        MigrationError.NotReversible(
          "Join(" + fieldNames.mkString(", ") + " => " + resultName + ") cannot be reversed"
        )
      )
  }

  /**
   * Split a single field into multiple fields using expressions.
   *
   * @param fieldName
   *   The field to split
   * @param into
   *   Vector of (newFieldName, expression) pairs
   */
  final case class Split(
    fieldName: String,
    into: Vector[(String, SchemaExpr[DynamicValue, DynamicValue])]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          val remaining = fields.filterNot(_._1 == fieldName)
          val results   = into.foldLeft[Either[MigrationError, Vector[(String, DynamicValue)]]](
            Right(Vector.empty)
          ) { case (acc, (name, expr)) =>
            acc.flatMap { accumulated =>
              expr.evalDynamic(value) match {
                case Right(values) if values.nonEmpty =>
                  Right(accumulated :+ (name -> values.head))
                case Right(_) =>
                  Left(
                    MigrationError.EvaluationFailed(
                      DynamicOptic.root,
                      s"Split expression for '$name' returned no value"
                    )
                  )
                case Left(err) =>
                  Left(MigrationError.EvaluationFailed(DynamicOptic.root, err.toString))
              }
            }
          }
          results.map(newFields => DynamicValue.Record(remaining ++ newFields))
        case _ =>
          Left(MigrationError.TypeMismatch(DynamicOptic.root, "Record", value.getClass.getSimpleName))
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(
        MigrationError.NotReversible(
          "Split(" + fieldName + " => " + into.map(_._1).mkString(", ") + ") cannot be reversed"
        )
      )
  }

  /**
   * Change the type of a field using a coercion expression.
   *
   * @param fieldName
   *   The field whose type to change
   * @param coercion
   *   Expression to convert the value
   */
  final case class ChangeType(
    fieldName: String,
    coercion: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          fields.find(_._1 == fieldName) match {
            case Some((name, fieldValue)) =>
              val wrappedValue = DynamicValue.Record(Vector((name, fieldValue)))
              coercion.evalDynamic(wrappedValue) match {
                case Right(results) if results.nonEmpty =>
                  val updated = fields.map {
                    case (n, _) if n == fieldName => (n, results.head)
                    case other                    => other
                  }
                  Right(DynamicValue.Record(updated))
                case Right(_) =>
                  Left(
                    MigrationError.EvaluationFailed(
                      DynamicOptic.root.field(fieldName),
                      s"ChangeType coercion returned no value"
                    )
                  )
                case Left(err) =>
                  Left(MigrationError.EvaluationFailed(DynamicOptic.root.field(fieldName), err.toString))
              }
            case None => Right(value)
          }
        case _ =>
          Left(MigrationError.TypeMismatch(DynamicOptic.root, "Record", value.getClass.getSimpleName))
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"ChangeType($fieldName) cannot be reversed"))
  }

  /**
   * Transform the value of a specific case in an enum/variant.
   *
   * @param caseName
   *   The case to transform
   * @param transform
   *   Expression to transform the case value
   */
  final case class TransformCase(
    caseName: String,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Variant(name, innerValue) if name == caseName =>
          transform.evalDynamic(innerValue) match {
            case Right(results) if results.nonEmpty =>
              Right(DynamicValue.Variant(name, results.head))
            case Right(_) =>
              Left(
                MigrationError.EvaluationFailed(
                  DynamicOptic.root,
                  s"TransformCase expression returned no value"
                )
              )
            case Left(err) =>
              Left(MigrationError.EvaluationFailed(DynamicOptic.root, err.toString))
          }
        case other => Right(other)
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"TransformCase($caseName) cannot be reversed"))
  }
}
