package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A single atomic migration action that can be applied to a DynamicValue.
 *
 * Each action represents a structural transformation on data. Actions are
 * designed to be composable, serializable, and reversible where semantically
 * meaningful.
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

  implicit class DynamicOpticOps(val optic: DynamicOptic) extends AnyVal {
    def getDV(value: DynamicValue): Either[MigrationError, DynamicValue] =
      optic.get(value).left.map(MigrationError.EvaluationFailed(optic, _))

    def setDV(root: DynamicValue, value: DynamicValue): Either[MigrationError, DynamicValue] =
      optic.set(root, value).left.map(MigrationError.EvaluationFailed(optic, _))
  }

  def dropField(name: String): DropField = DropField(DynamicOptic.root.field(name))

  def renameField(oldName: String, newName: String): Rename =
    Rename(DynamicOptic.root.field(oldName), newName)

  def addField(name: String, defaultValue: DynamicValue): AddField =
    AddField(DynamicOptic.root.field(name), defaultValue)

  def optionalize(name: String): Optionalize = Optionalize(DynamicOptic.root.field(name))

  def mandate(name: String, defaultValue: DynamicValue): Mandate =
    Mandate(DynamicOptic.root.field(name), defaultValue)

  def renameCase(oldName: String, newName: String): RenameCase =
    RenameCase(DynamicOptic.root, oldName, newName)

  def removeCase(name: String): RemoveCase = RemoveCase(DynamicOptic.root, name)

  def transformElements(name: String, transform: SchemaExpr[DynamicValue, DynamicValue]): TransformElements =
    TransformElements(DynamicOptic.root.field(name), transform)

  def transformKeys(name: String, transform: SchemaExpr[DynamicValue, DynamicValue]): TransformKeys =
    TransformKeys(DynamicOptic.root.field(name), transform)

  def transformValues(name: String, transform: SchemaExpr[DynamicValue, DynamicValue]): TransformValues =
    TransformValues(DynamicOptic.root.field(name), transform)

  def transformCase(caseName: String, transform: SchemaExpr[DynamicValue, DynamicValue]): TransformCase =
    TransformCase(DynamicOptic.root, caseName, transform)

  // ============================================================================
  // Record Actions
  // ============================================================================

  /**
   * Drop a field from a record.
   *
   * @param at
   *   DynamicOptic path to the field to remove
   */
  final case class DropField(at: DynamicOptic) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.nodes.toList match {
        case nodes :+ DynamicOptic.Node.Field(fieldName) =>
          val parentAt = DynamicOptic(nodes.toVector)
          parentAt.getDV(value).flatMap {
            case DynamicValue.Record(fields) =>
              val updated = fields.filterNot(_._1 == fieldName)
              if (updated.size == fields.size)
                Left(MigrationError.MissingField(at, fieldName))
              else parentAt.setDV(value, DynamicValue.Record(updated))
            case _ =>
              Left(MigrationError.TypeMismatch(parentAt, "Record", "parent is not a record"))
          }
        case _ =>
          Left(MigrationError.EvaluationFailed(at, "DropField only supports field paths"))
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"DropField($at) cannot be reversed"))
  }

  object DropField {
    def apply(name: String): DropField = DropField(DynamicOptic.root.field(name))
  }

  /**
   * Rename a field in a record.
   *
   * @param at
   *   DynamicOptic path to the field to rename
   * @param to
   *   New field name
   */
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.nodes.toList match {
        case nodes :+ DynamicOptic.Node.Field(oldName) =>
          val parentAt = DynamicOptic(nodes.toVector)
          parentAt.getDV(value).flatMap {
            case DynamicValue.Record(fields) =>
              val updated = fields.map {
                case (name, v) if name == oldName => (to, v)
                case other                        => other
              }
              parentAt.setDV(value, DynamicValue.Record(updated))
            case _ =>
              Left(MigrationError.TypeMismatch(parentAt, "Record", "parent is not a record"))
          }
        case _ =>
          Left(MigrationError.EvaluationFailed(at, "Rename only supports field paths"))
      }

    def reverse: Either[MigrationError, MigrationAction] =
      at.nodes.toList match {
        case nodes :+ DynamicOptic.Node.Field(_) =>
          val newAt = DynamicOptic((nodes :+ DynamicOptic.Node.Field(to)).toVector)
          Right(Rename(newAt, at.nodes.last.asInstanceOf[DynamicOptic.Node.Field].name))
        case _ => Left(MigrationError.NotReversible(s"Rename($at) cannot be reversed"))
      }
  }

  object Rename {
    def apply(oldName: String, newName: String): Rename =
      Rename(DynamicOptic.root.field(oldName), newName)
  }

  /**
   * Add a new field to a record with a constant default value.
   *
   * @param at
   *   DynamicOptic path where the field should be added
   * @param defaultValue
   *   The default value for the new field
   */
  final case class AddField(at: DynamicOptic, defaultValue: DynamicValue) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.nodes.toList match {
        case nodes :+ DynamicOptic.Node.Field(fieldName) =>
          val parentAt = DynamicOptic(nodes.toVector)
          parentAt.getDV(value).flatMap {
            case DynamicValue.Record(fields) =>
              if (fields.exists(_._1 == fieldName)) Right(value)
              else parentAt.setDV(value, DynamicValue.Record(fields :+ (fieldName -> defaultValue)))
            case _ =>
              Left(MigrationError.TypeMismatch(parentAt, "Record", "parent is not a record"))
          }
        case _ =>
          Left(MigrationError.EvaluationFailed(at, "AddField only supports field paths"))
      }

    def reverse: Either[MigrationError, MigrationAction] = Right(DropField(at))
  }

  object AddField {
    def apply(name: String, defaultValue: DynamicValue): AddField =
      AddField(DynamicOptic.root.field(name), defaultValue)
  }

  /**
   * Make a field optional by wrapping its value in Some.
   *
   * @param at
   *   DynamicOptic path to the field to make optional
   */
  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value).map(v => DynamicValue.Variant("Some", v)).flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Right(Mandate(at, DynamicValue.Primitive(PrimitiveValue.Unit)))
  }

  object Optionalize {
    def apply(name: String): Optionalize = Optionalize(DynamicOptic.root.field(name))
  }

  /**
   * Make an optional field required.
   *
   * @param at
   *   DynamicOptic path to the field to mandate
   * @param defaultValue
   *   Value to use if the field is None
   */
  final case class Mandate(at: DynamicOptic, defaultValue: DynamicValue) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value)
        .flatMap {
          case DynamicValue.Variant("Some", v) => Right(v)
          case DynamicValue.Variant("None", _) => Right(defaultValue)
          case other                           => Left(MigrationError.TypeMismatch(at, "Optional", other.getClass.getSimpleName))
        }
        .flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Right(Optionalize(at))
  }

  object Mandate {
    def apply(name: String, defaultValue: DynamicValue): Mandate =
      Mandate(DynamicOptic.root.field(name), defaultValue)
  }

  // ============================================================================
  // Enum/Variant Actions
  // ============================================================================

  /**
   * Rename a case in an enum/variant.
   *
   * @param at
   *   DynamicOptic path to the enum/variant
   * @param oldName
   *   The case name to change
   * @param newName
   *   The new case name
   */
  final case class RenameCase(at: DynamicOptic, oldName: String, newName: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value)
        .flatMap {
          case DynamicValue.Variant(name, v) if name == oldName =>
            Right(DynamicValue.Variant(newName, v))
          case other => Right(other)
        }
        .flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Right(RenameCase(at, newName, oldName))
  }

  object RenameCase {
    def apply(oldName: String, newName: String): RenameCase =
      RenameCase(DynamicOptic.root, oldName, newName)
  }

  /**
   * Remove a case from an enum/variant.
   *
   * @param at
   *   DynamicOptic path to the enum/variant
   * @param caseName
   *   The case to remove
   */
  final case class RemoveCase(at: DynamicOptic, caseName: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value)
        .flatMap {
          case DynamicValue.Variant(name, _) if name == caseName =>
            Left(MigrationError.UnknownCase(at, caseName))
          case other =>
            Right(other)
        }
        .flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"RemoveCase($at, $caseName) - cannot recover removed case"))
  }

  object RemoveCase {
    def apply(name: String): RemoveCase = RemoveCase(DynamicOptic.root, name)
  }

  // ============================================================================
  // Advanced Actions (using SchemaExpr)
  // ============================================================================

  /**
   * Join multiple fields into a single field using an expression.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val sources             = sourcePaths.map(_.getDV(value))
      val (errors, successes) = sources.partitionMap(identity)
      if (errors.nonEmpty) Left(errors.head)
      else {
        // Join usually happens within a record context, but here we evaluate the combiner
        // on the current state and set the result at 'at'.
        combiner.evalDynamic(value) match {
          case Right(results) if results.nonEmpty =>
            at.setDV(value, results.head)
          case Right(_) =>
            Left(MigrationError.EvaluationFailed(at, "Join expression returned no value"))
          case Left(err) =>
            Left(MigrationError.EvaluationFailed(at, err.toString))
        }
      }
    }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"Join at $at cannot be reversed"))
  }

  /**
   * Split a single field into multiple fields using expressions.
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value).flatMap { v =>
        splitter.evalDynamic(v).left.map(e => MigrationError.EvaluationFailed(at, e.message)).flatMap { results =>
          val valuesToDistribute: Vector[DynamicValue] =
            if (results.size >= targetPaths.size) results.toVector
            else
              results.headOption match {
                case Some(DynamicValue.Sequence(vals)) => vals
                case _                                 => results.toVector
              }

          if (valuesToDistribute.size < targetPaths.size)
            Left(
              MigrationError.EvaluationFailed(
                at,
                s"Split expression returned ${valuesToDistribute.size} values, expected at least ${targetPaths.size}"
              )
            )
          else {
            targetPaths
              .zip(valuesToDistribute)
              .foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { case (acc, (path, valToSet)) =>
                acc.flatMap(v => path.setDV(v, valToSet))
              }
          }
        }
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"Split at $at cannot be reversed"))
  }

  /**
   * Transform a value at a path using an expression.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value).flatMap { v =>
        transform
          .evalDynamic(v)
          .left
          .map(e => MigrationError.EvaluationFailed(at, e.message))
          .map(_.head)
          .flatMap(newValue => at.setDV(value, newValue))
      }

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"TransformValue($at) cannot be reversed"))
  }

  /**
   * Change the type of a field using a coercion expression.
   */
  final case class ChangeType(
    at: DynamicOptic,
    coercion: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value)
        .flatMap { fieldValue =>
          coercion.evalDynamic(fieldValue) match {
            case Right(results) if results.nonEmpty => Right(results.head)
            case Right(_)                           => Left(MigrationError.EvaluationFailed(at, "ChangeType returned no value"))
            case Left(err)                          => Left(MigrationError.EvaluationFailed(at, err.toString))
          }
        }
        .flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"ChangeType($at) cannot be reversed"))
  }

  /**
   * Transform a case in an enum/variant.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value)
        .flatMap {
          case DynamicValue.Variant(name, v) if name == caseName =>
            transform.evalDynamic(v).map(res => DynamicValue.Variant(name, res.head)).left.map { error =>
              MigrationError.EvaluationFailed(at, error.message)
            }
          case other => Right(other)
        }
        .flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"TransformCase($at, $caseName) - reverse not implemented"))
  }

  object TransformCase {
    def apply(caseName: String, transform: SchemaExpr[DynamicValue, DynamicValue]): TransformCase =
      TransformCase(DynamicOptic.root, caseName, transform)
  }

  // ============================================================================
  // Collection Actions
  // ============================================================================

  /**
   * Transform elements in a collection.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value)
        .flatMap {
          case DynamicValue.Sequence(elements) =>
            val results             = elements.map(transform.evalDynamic)
            val (errors, successes) = results.partitionMap(identity)
            if (errors.nonEmpty) {
              Left(MigrationError.EvaluationFailed(at, errors.head.message))
            } else {
              Right(DynamicValue.Sequence(successes.flatten))
            }
          case other =>
            Left(MigrationError.TypeMismatch(at, "Sequence", other.getClass.getSimpleName))
        }
        .flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"TransformElements($at) - reverse not implemented"))
  }

  object TransformElements {
    def apply(name: String, transform: SchemaExpr[DynamicValue, DynamicValue]): TransformElements =
      TransformElements(DynamicOptic.root.field(name), transform)
  }

  /**
   * Transform keys in a map.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value)
        .flatMap {
          case DynamicValue.Map(entries) =>
            val results = entries.map { case (k, v) =>
              transform.evalDynamic(k).map(newK => (newK.head, v))
            }
            val (errors, successes) = results.partitionMap(identity)
            if (errors.nonEmpty) {
              Left(MigrationError.EvaluationFailed(at, errors.head.message))
            } else {
              Right(DynamicValue.Map(successes))
            }
          case other =>
            Left(MigrationError.TypeMismatch(at, "Map", other.getClass.getSimpleName))
        }
        .flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"TransformKeys($at) - reverse not implemented"))
  }

  object TransformKeys {
    def apply(name: String, transform: SchemaExpr[DynamicValue, DynamicValue]): TransformKeys =
      TransformKeys(DynamicOptic.root.field(name), transform)
  }

  /**
   * Transform values in a map.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      at.getDV(value)
        .flatMap {
          case DynamicValue.Map(entries) =>
            val results = entries.map { case (k, v) =>
              transform.evalDynamic(v).map(newV => (k, newV.head))
            }
            val (errors, successes) = results.partitionMap(identity)
            if (errors.nonEmpty) {
              Left(MigrationError.EvaluationFailed(at, errors.head.message))
            } else {
              Right(DynamicValue.Map(successes))
            }
          case other =>
            Left(MigrationError.TypeMismatch(at, "Map", other.getClass.getSimpleName))
        }
        .flatMap(newValue => at.setDV(value, newValue))

    def reverse: Either[MigrationError, MigrationAction] =
      Left(MigrationError.NotReversible(s"TransformValues($at) - reverse not implemented"))
  }

  object TransformValues {
    def apply(name: String, transform: SchemaExpr[DynamicValue, DynamicValue]): TransformValues =
      TransformValues(DynamicOptic.root.field(name), transform)
  }
}
