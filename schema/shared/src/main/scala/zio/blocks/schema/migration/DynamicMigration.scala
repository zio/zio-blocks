package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A fully serializable migration that operates on DynamicValue.
 *
 * DynamicMigration is the core of the migration system. It contains a sequence
 * of MigrationActions that are applied in order to transform data. Because it
 * operates on DynamicValue and uses only serializable MigrationExpr
 * transformations, the entire migration can be serialized and transmitted.
 *
 * Key properties:
 *   - Fully serializable (no runtime closures)
 *   - Composable via ++ operator
 *   - Structurally reversible (where possible)
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Applies this migration to a DynamicValue.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
    var current = value
    var idx     = 0
    while (idx < actions.length) {
      DynamicMigration.applyAction(current, actions(idx)) match {
        case Right(updated) => current = updated
        case Left(err)      => return Left(err)
      }
      idx += 1
    }
    Right(current)
  }

  /**
   * Composes this migration with another.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  /**
   * Creates a structural reverse of this migration. Note that not all actions
   * are reversible (e.g., DropField loses data).
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.flatMap(MigrationAction.reverseAction))

  /**
   * Returns true if this migration has no actions.
   */
  def isEmpty: Boolean = actions.isEmpty
}

object DynamicMigration {

  /**
   * An empty migration that performs no transformations.
   */
  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  /**
   * Creates a migration from a single action.
   */
  def single(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))

  /**
   * Applies a single action to a DynamicValue.
   */
  def applyAction(value: DynamicValue, action: MigrationAction): Either[SchemaError, DynamicValue] =
    action match {
      case MigrationAction.AddField(path, fieldName, defaultValue) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields :+ (fieldName -> defaultValue)))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.DropField(path, fieldName) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.RenameField(path, oldName, newName) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields.map {
              case (name, v) if name == oldName => (newName, v)
              case other                        => other
            }))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.TransformField(path, fieldName, expr) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            val result = fields.foldLeft[Either[SchemaError, Vector[(String, DynamicValue)]]](Right(Vector.empty)) {
              case (acc, (name, v)) if name == fieldName =>
                for {
                  soFar       <- acc
                  transformed <- expr(v)
                } yield soFar :+ (name -> transformed)
              case (acc, pair) =>
                acc.map(_ :+ pair)
            }
            result.map(DynamicValue.Record(_))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.MandateField(path, fieldName, defaultValue) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            val result = fields.foldLeft[Either[SchemaError, Vector[(String, DynamicValue)]]](Right(Vector.empty)) {
              case (acc, (name, v)) if name == fieldName =>
                val unwrapped = v match {
                  case DynamicValue.Variant("Some", DynamicValue.Record(innerFields)) =>
                    innerFields.find(_._1 == "value").map(_._2).getOrElse(defaultValue)
                  case DynamicValue.Variant("None", _) => defaultValue
                  case other                           => other
                }
                acc.map(_ :+ (name -> unwrapped))
              case (acc, pair) =>
                acc.map(_ :+ pair)
            }
            result.map(DynamicValue.Record(_))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.OptionalizeField(path, fieldName) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields.map {
              case (name, v) if name == fieldName =>
                (name, DynamicValue.Variant("Some", DynamicValue.Record(Vector("value" -> v))))
              case other => other
            }))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.ChangeFieldType(path, fieldName, expr) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            val result = fields.foldLeft[Either[SchemaError, Vector[(String, DynamicValue)]]](Right(Vector.empty)) {
              case (acc, (name, v)) if name == fieldName =>
                for {
                  soFar       <- acc
                  transformed <- expr(v)
                } yield soFar :+ (name -> transformed)
              case (acc, pair) =>
                acc.map(_ :+ pair)
            }
            result.map(DynamicValue.Record(_))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.JoinFields(path, sourceFields, targetField, expr) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            val sourceValues = sourceFields.flatMap(name => fields.find(_._1 == name))
            if (sourceValues.length != sourceFields.length) {
              val missing = sourceFields.filterNot(n => fields.exists(_._1 == n))
              Left(SchemaError.missingField(Nil, missing.mkString(", ")))
            } else {
              val sourceRecord = DynamicValue.Record(sourceValues)
              expr(sourceRecord).map { joined =>
                val remainingFields = fields.filterNot(f => sourceFields.contains(f._1))
                DynamicValue.Record(remainingFields :+ (targetField -> joined))
              }
            }
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.SplitField(path, sourceField, targetFields) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            fields.find(_._1 == sourceField) match {
              case Some((_, sourceValue)) =>
                val newFields =
                  targetFields.foldLeft[Either[SchemaError, Vector[(String, DynamicValue)]]](Right(Vector.empty)) {
                    case (acc, (name, expr)) =>
                      for {
                        soFar <- acc
                        value <- expr(sourceValue)
                      } yield soFar :+ (name -> value)
                  }
                newFields.map { nf =>
                  val remainingFields = fields.filterNot(_._1 == sourceField)
                  DynamicValue.Record(remainingFields ++ nf)
                }
              case None =>
                Left(SchemaError.missingField(Nil, sourceField))
            }
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.RenameCase(path, oldName, newName) =>
        modifyAtPath(value, path) {
          case DynamicValue.Variant(name, payload) if name == oldName =>
            Right(DynamicValue.Variant(newName, payload))
          case other => Right(other)
        }

      case MigrationAction.TransformCase(path, caseName, expr) =>
        modifyAtPath(value, path) {
          case DynamicValue.Variant(name, payload) if name == caseName =>
            expr(payload).map(DynamicValue.Variant(name, _))
          case other => Right(other)
        }

      case MigrationAction.TransformElements(path, expr) =>
        modifyAtPath(value, path) {
          case DynamicValue.Sequence(elements) =>
            elements
              .foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) { (acc, elem) =>
                for {
                  soFar       <- acc
                  transformed <- expr(elem)
                } yield soFar :+ transformed
              }
              .map(DynamicValue.Sequence(_))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Sequence but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.TransformKeys(path, expr) =>
        modifyAtPath(value, path) {
          case DynamicValue.Map(entries) =>
            entries
              .foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                case (acc, (k, v)) =>
                  for {
                    soFar  <- acc
                    newKey <- expr(k)
                  } yield soFar :+ (newKey -> v)
              }
              .map(DynamicValue.Map(_))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Map but got ${other.getClass.getSimpleName}"))
        }

      case MigrationAction.TransformValues(path, expr) =>
        modifyAtPath(value, path) {
          case DynamicValue.Map(entries) =>
            entries
              .foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                case (acc, (k, v)) =>
                  for {
                    soFar    <- acc
                    newValue <- expr(v)
                  } yield soFar :+ (k -> newValue)
              }
              .map(DynamicValue.Map(_))
          case other =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected Map but got ${other.getClass.getSimpleName}"))
        }
    }

  /**
   * Modifies a DynamicValue at a specific path.
   */
  private def modifyAtPath(
    value: DynamicValue,
    path: DynamicOptic
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] =
    if (path.nodes.isEmpty) {
      f(value)
    } else {
      path.nodes.head match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val idx = fields.indexWhere(_._1 == name)
              if (idx < 0) {
                Left(SchemaError.missingField(Nil, name))
              } else {
                val (fieldName, fieldValue) = fields(idx)
                modifyAtPath(fieldValue, DynamicOptic(path.nodes.tail))(f).map { updated =>
                  DynamicValue.Record(fields.updated(idx, (fieldName, updated)))
                }
              }
            case _ =>
              Left(
                SchemaError.expectationMismatch(Nil, s"Expected Record at path but got ${value.getClass.getSimpleName}")
              )
          }

        case DynamicOptic.Node.Case(name) =>
          value match {
            case DynamicValue.Variant(caseName, payload) if caseName == name =>
              modifyAtPath(payload, DynamicOptic(path.nodes.tail))(f).map { updated =>
                DynamicValue.Variant(caseName, updated)
              }
            case _ => Right(value) // Different case, leave unchanged
          }

        case DynamicOptic.Node.AtIndex(idx) =>
          value match {
            case DynamicValue.Sequence(elements) if idx >= 0 && idx < elements.length =>
              modifyAtPath(elements(idx), DynamicOptic(path.nodes.tail))(f).map { updated =>
                DynamicValue.Sequence(elements.updated(idx, updated))
              }
            case DynamicValue.Sequence(_) =>
              Left(SchemaError.expectationMismatch(Nil, s"Index $idx out of bounds"))
            case _ =>
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Expected Sequence at path but got ${value.getClass.getSimpleName}"
                )
              )
          }

        case DynamicOptic.Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              elements
                .foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) { (acc, elem) =>
                  for {
                    soFar   <- acc
                    updated <- modifyAtPath(elem, DynamicOptic(path.nodes.tail))(f)
                  } yield soFar :+ updated
                }
                .map(DynamicValue.Sequence(_))
            case _ =>
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Expected Sequence at path but got ${value.getClass.getSimpleName}"
                )
              )
          }

        case DynamicOptic.Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              entries
                .foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                  case (acc, (k, v)) =>
                    for {
                      soFar   <- acc
                      updated <- modifyAtPath(k, DynamicOptic(path.nodes.tail))(f)
                    } yield soFar :+ (updated -> v)
                }
                .map(DynamicValue.Map(_))
            case _ =>
              Left(
                SchemaError.expectationMismatch(Nil, s"Expected Map at path but got ${value.getClass.getSimpleName}")
              )
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              entries
                .foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                  case (acc, (k, v)) =>
                    for {
                      soFar   <- acc
                      updated <- modifyAtPath(v, DynamicOptic(path.nodes.tail))(f)
                    } yield soFar :+ (k -> updated)
                }
                .map(DynamicValue.Map(_))
            case _ =>
              Left(
                SchemaError.expectationMismatch(Nil, s"Expected Map at path but got ${value.getClass.getSimpleName}")
              )
          }

        case DynamicOptic.Node.Wrapped =>
          // For wrapped values, apply to the inner value
          f(value)

        case _ =>
          // Unsupported path node types
          Left(SchemaError.expectationMismatch(Nil, s"Unsupported path node type"))
      }
    }
}
