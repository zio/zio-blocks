package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, DynamicOptic, SchemaError}

/**
 * Represents a single migration action that can be applied to a DynamicValue.
 *
 * All actions are:
 *   - Pure data (fully serializable)
 *   - Path-based (operate at a specific DynamicOptic location)
 *   - Reversible (have a structural inverse)
 *
 * Actions are the building blocks of migrations.
 */
sealed trait MigrationAction {

  /**
   * The path where this action operates.
   */
  def at: DynamicOptic

  /**
   * Apply this action to a DynamicValue.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue]

  /**
   * Return the structural reverse of this action. The reverse may not be a
   * perfect semantic inverse (best-effort).
   */
  def reverse: MigrationAction
}

object MigrationAction {

  /**
   * Add a new field to a record with a default value.
   *
   * Example: Add "country" field with default "USA"
   */
  case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            // Check if field already exists
            if (fields.exists(_._1 == fieldName)) {
              Left(SchemaError.fieldAlreadyExists(at, fieldName))
            } else {
              // Evaluate default value
              default.apply(value).map { defaultValue =>
                DynamicValue.Record(fields :+ (fieldName, defaultValue))
              }
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction = DropField(at, fieldName, Some(default))
  }

  /**
   * Remove a field from a record. Optionally store a default value for reverse
   * migration.
   */
  case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: Option[SchemaExpr]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            val filtered = fields.filterNot(_._1 == fieldName)
            if (filtered.length == fields.length) {
              Left(SchemaError.missingField(at, fieldName))
            } else {
              Right(DynamicValue.Record(filtered))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      AddField(at, fieldName, defaultForReverse.getOrElse(SchemaExpr.DefaultValue))
  }

  /**
   * Rename a field in a record.
   */
  case class Rename(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            val renamed = fields.map {
              case (name, fieldValue) if name == from => (to, fieldValue)
              case other                              => other
            }
            if (renamed == fields) {
              Left(SchemaError.missingField(at, from))
            } else {
              Right(DynamicValue.Record(renamed))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction = Rename(at, to, from)
  }

  /**
   * Transform a field's value using an expression.
   */
  case class TransformValue(
    at: DynamicOptic,
    fieldName: String,
    transform: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            fields.find(_._1 == fieldName) match {
              case Some((name, fieldValue)) =>
                transform.apply(fieldValue).map { newValue =>
                  DynamicValue.Record(fields.map {
                    case (n, _) if n == name => (n, newValue)
                    case other               => other
                  })
                }
              case None =>
                Left(SchemaError.missingField(at, fieldName))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      // Reverse transformation is not always possible
      // This is a best-effort structural reverse
      this
  }

  /**
   * Make an optional field mandatory by providing a default for None values.
   */
  case class Mandate(
    at: DynamicOptic,
    fieldName: String,
    default: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            fields.find(_._1 == fieldName) match {
              case Some((name, DynamicValue.Variant("None", _))) =>
                // Field is None, replace with default
                default.apply(value).map { defaultValue =>
                  DynamicValue.Record(fields.map {
                    case (n, _) if n == name => (n, defaultValue)
                    case other               => other
                  })
                }
              case Some((name, DynamicValue.Variant("Some", innerValue))) =>
                // Field has a value, unwrap it
                Right(DynamicValue.Record(fields.map {
                  case (n, _) if n == name => (n, innerValue)
                  case other               => other
                }))
              case Some(_) =>
                // Field is already mandatory
                Right(target)
              case None =>
                Left(SchemaError.missingField(at, fieldName))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction = Optionalize(at, fieldName)
  }

  /**
   * Make a mandatory field optional by wrapping it in Some.
   */
  case class Optionalize(
    at: DynamicOptic,
    fieldName: String
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            fields.find(_._1 == fieldName) match {
              case Some((name, _)) =>
                // Wrap the value in Variant("Some", value)
                Right(DynamicValue.Record(fields.map {
                  case (n, v) if n == name => (n, DynamicValue.Variant("Some", v))
                  case other               => other
                }))
              case None =>
                Left(SchemaError.missingField(at, fieldName))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction = Mandate(at, fieldName, SchemaExpr.DefaultValue)
  }

  /**
   * Change the type of a field using a converter expression. Only supports
   * primitive-to-primitive conversions.
   */
  case class ChangeType(
    at: DynamicOptic,
    fieldName: String,
    converter: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            fields.find(_._1 == fieldName) match {
              case Some((name, fieldValue)) =>
                converter.apply(fieldValue).map { convertedValue =>
                  DynamicValue.Record(fields.map {
                    case (n, _) if n == name => (n, convertedValue)
                    case other               => other
                  })
                }
              case None =>
                Left(SchemaError.missingField(at, fieldName))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      // Type conversion reverse is not always possible
      this
  }

  /**
   * Join multiple fields into one using a combiner expression. Example:
   * firstName + lastName -> fullName
   */
  case class Join(
    at: DynamicOptic,
    sourceFields: Vector[String],
    targetField: String,
    combiner: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            // Check all source fields exist
            val missingFields = sourceFields.filterNot(f => fields.exists(_._1 == f))
            if (missingFields.nonEmpty) {
              Left(SchemaError.missingField(at, missingFields.head))
            } else {
              // Apply combiner to get new value
              combiner.apply(target).flatMap { combinedValue =>
                // Remove source fields and add target field
                val filtered = fields.filterNot(f => sourceFields.contains(f._1))
                Right(DynamicValue.Record(filtered :+ (targetField, combinedValue)))
              }
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      // Reverse of join is split, but we can't automatically determine the splitter
      // This is a best-effort structural reverse
      Split(at, targetField, sourceFields, SchemaExpr.DefaultValue)
  }

  /**
   * Split one field into multiple fields using a splitter expression. Example:
   * fullName -> firstName + lastName
   */
  case class Split(
    at: DynamicOptic,
    sourceField: String,
    targetFields: Vector[String],
    splitter: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Record(fields) =>
            fields.find(_._1 == sourceField) match {
              case Some((_, fieldValue)) =>
                // Apply splitter - it should return a Record with the target fields
                splitter.apply(fieldValue).flatMap {
                  case DynamicValue.Record(splitFields) =>
                    // Remove source field and add split fields
                    val filtered = fields.filterNot(_._1 == sourceField)
                    Right(DynamicValue.Record(filtered ++ splitFields))
                  case _ =>
                    Left(SchemaError.transformFailed(at, "Splitter must return a Record"))
                }
              case None =>
                Left(SchemaError.missingField(at, sourceField))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Record", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      Join(at, targetFields, sourceField, SchemaExpr.DefaultValue)
  }

  /**
   * Rename a case in an enum/sum type.
   */
  case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Variant(caseName, caseValue) if caseName == from =>
            Right(DynamicValue.Variant(to, caseValue))
          case DynamicValue.Variant(caseName, _) =>
            // Not the case we're renaming, leave it as-is
            Right(target)
          case _ =>
            Left(SchemaError.typeMismatch(at, "Variant", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transform the value of a specific enum case. Applies nested migration
   * actions to the case value.
   */
  case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Variant(name, caseValue) if name == caseName =>
            // Apply all actions to the case value
            actions
              .foldLeft[Either[SchemaError, DynamicValue]](Right(caseValue)) {
                case (Right(v), action) => action.apply(v)
                case (left, _)          => left
              }
              .map(transformed => DynamicValue.Variant(name, transformed))
          case DynamicValue.Variant(_, _) =>
            // Not the case we're transforming
            Right(target)
          case _ =>
            Left(SchemaError.typeMismatch(at, "Variant", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      TransformCase(at, caseName, actions.map(_.reverse).reverse)
  }

  /**
   * Transform all elements in a collection (Vector, List, etc).
   */
  case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Sequence(elements) =>
            // Apply transform to each element
            val transformed = elements.map(elem => transform.apply(elem))
            val errors      = transformed.collect { case Left(err) => err }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              val newElements = transformed.collect { case Right(v) => v }
              Right(DynamicValue.Sequence(newElements))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Sequence", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      // Element transformation reverse is not always possible
      this
  }

  /**
   * Transform all keys in a map.
   */
  case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Map(entries) =>
            // Apply transform to each key
            val transformed = entries.map { case (k, v) =>
              transform.apply(k).map(newKey => (newKey, v))
            }
            val errors = transformed.collect { case Left(err) => err }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              val newEntries = transformed.collect { case Right(pair) => pair }
              Right(DynamicValue.Map(newEntries))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Map", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      // Key transformation reverse is not always possible
      this
  }

  /**
   * Transform all values in a map.
   */
  case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      navigateAndTransform(value, at.nodes) { target =>
        target match {
          case DynamicValue.Map(entries) =>
            // Apply transform to each value
            val transformed = entries.map { case (k, v) =>
              transform.apply(v).map(newValue => (k, newValue))
            }
            val errors = transformed.collect { case Left(err) => err }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              val newEntries = transformed.collect { case Right(pair) => pair }
              Right(DynamicValue.Map(newEntries))
            }
          case _ =>
            Left(SchemaError.typeMismatch(at, "Map", target.getClass.getSimpleName))
        }
      }

    def reverse: MigrationAction =
      // Value transformation reverse is not always possible
      this
  }

  // Helper function to navigate to a path and transform the value there
  private def navigateAndTransform(
    value: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node]
  )(transform: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] =
    if (nodes.isEmpty) {
      // We're at the target, apply the transformation
      transform(value)
    } else {
      // Navigate deeper
      val head = nodes.head
      val tail = nodes.tail

      head match {
        case DynamicOptic.Node.Field(fieldName) =>
          value match {
            case DynamicValue.Record(fields) =>
              // Find the field and recursively navigate
              fields.indexWhere(_._1 == fieldName) match {
                case -1 =>
                  Left(SchemaError.missingField(DynamicOptic(nodes), fieldName))
                case idx =>
                  val (name, fieldValue) = fields(idx)
                  navigateAndTransform(fieldValue, tail)(transform).map { transformed =>
                    DynamicValue.Record(fields.updated(idx, (name, transformed)))
                  }
              }
            case _ =>
              Left(SchemaError.typeMismatch(DynamicOptic(nodes), "Record", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length) {
                Left(SchemaError.invalidPath(DynamicOptic(nodes), s"Index $index out of bounds"))
              } else {
                navigateAndTransform(elements(index), tail)(transform).map { transformed =>
                  DynamicValue.Sequence(elements.updated(index, transformed))
                }
              }
            case _ =>
              Left(SchemaError.typeMismatch(DynamicOptic(nodes), "Sequence", value.getClass.getSimpleName))
          }

        case _ =>
          Left(SchemaError.invalidPath(DynamicOptic(nodes), s"Unsupported node type: $head"))
      }
    }
}
