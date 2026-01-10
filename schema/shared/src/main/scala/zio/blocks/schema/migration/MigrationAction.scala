package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A single atomic migration action that can be applied to a DynamicValue.
 *
 * All actions operate at a path, represented by `DynamicOptic`. Actions are
 * pure data: fully serializable, composable, and reversible.
 */
sealed trait MigrationAction extends Product with Serializable {

  /** The path at which this action operates. */
  def at: DynamicOptic

  /** Apply this migration action to a DynamicValue. */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]

  /** Structural reverse of this action. */
  def reverse: MigrationAction
}

object MigrationAction {

  // ============================================================================
  // Record Actions
  // ============================================================================

  /**
   * Add a new field at the specified path with a default value.
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          if (fields.exists(_._1 == fieldName)) Right(DynamicValue.Record(fields))
          else Right(DynamicValue.Record(fields :+ (fieldName -> default)))
        case other =>
          Left(MigrationError.TypeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = DropField(at, fieldName, Some(default))
  }

  /**
   * Drop a field at the specified path.
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: Option[DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
        case other =>
          Left(MigrationError.TypeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = defaultForReverse match {
      case Some(default) => AddField(at, fieldName, default)
      case None          => this // Can't reverse without default
    }
  }

  /**
   * Rename a field at the specified path.
   */
  final case class Rename(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          val renamed = fields.map {
            case (name, v) if name == from => (to, v)
            case other                     => other
          }
          Right(DynamicValue.Record(renamed))
        case other =>
          Left(MigrationError.TypeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = Rename(at, to, from)
  }

  /**
   * Transform a field value using a SchemaExpr.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) { current =>
        transform.evalDynamic(current) match {
          case Right(results) if results.nonEmpty => Right(results.head)
          case Right(_)                           => Left(MigrationError.EvaluationFailed(at, "Transform returned no value"))
          case Left(err)                          => Left(MigrationError.EvaluationFailed(at, err.toString))
        }
      }

    def reverse: MigrationAction = this
  }

  /**
   * Make an optional field required, using default for None.
   */
  final case class Mandate(
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          fields.find(_._1 == fieldName) match {
            case Some((_, DynamicValue.Variant("None", _))) =>
              val updated = fields.map {
                case (name, _) if name == fieldName => (name, default)
                case other                          => other
              }
              Right(DynamicValue.Record(updated))
            case Some((_, DynamicValue.Variant("Some", innerValue))) =>
              val updated = fields.map {
                case (name, _) if name == fieldName => (name, innerValue)
                case other                          => other
              }
              Right(DynamicValue.Record(updated))
            case Some(_) => Right(DynamicValue.Record(fields))
            case None    => Right(DynamicValue.Record(fields :+ (fieldName -> default)))
          }
        case other =>
          Left(MigrationError.TypeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = Optionalize(at, fieldName)
  }

  /**
   * Make a field optional by wrapping its value in Some.
   */
  final case class Optionalize(
    at: DynamicOptic,
    fieldName: String
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          val updated = fields.map {
            case (name, v) if name == fieldName =>
              (name, DynamicValue.Variant("Some", v))
            case other => other
          }
          if (!fields.exists(_._1 == fieldName)) {
            Right(
              DynamicValue.Record(
                updated :+ (fieldName -> DynamicValue.Variant("None", DynamicValue.Record(Vector.empty)))
              )
            )
          } else {
            Right(DynamicValue.Record(updated))
          }
        case other =>
          Left(MigrationError.TypeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    // Optionalize reverse needs a default - use empty record as placeholder
    def reverse: MigrationAction = Mandate(at, fieldName, DynamicValue.Record(Vector.empty))
  }

  /**
   * Join multiple source fields into a single target field.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[String],
    targetField: String,
    combiner: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          val remaining = fields.filterNot(f => sourcePaths.contains(f._1))
          combiner.evalDynamic(value) match {
            case Right(results) if results.nonEmpty =>
              Right(DynamicValue.Record(remaining :+ (targetField -> results.head)))
            case Right(_) =>
              Left(MigrationError.EvaluationFailed(at, "Join combiner returned no value"))
            case Left(err) =>
              Left(MigrationError.EvaluationFailed(at, err.toString))
          }
        case other =>
          Left(MigrationError.TypeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = this
  }

  /**
   * Split a source field into multiple target fields.
   */
  final case class Split(
    at: DynamicOptic,
    sourceField: String,
    targetPaths: Vector[String],
    splitter: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          val remaining = fields.filterNot(_._1 == sourceField)
          splitter.evalDynamic(value) match {
            case Right(results) =>
              val newFields = targetPaths.zip(results).toVector
              Right(DynamicValue.Record(remaining ++ newFields))
            case Left(err) =>
              Left(MigrationError.EvaluationFailed(at, err.toString))
          }
        case other =>
          Left(MigrationError.TypeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = this
  }

  /**
   * Change the type of a field using a converter expression.
   */
  final case class ChangeType(
    at: DynamicOptic,
    fieldName: String,
    converter: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          fields.find(_._1 == fieldName) match {
            case Some((_, fieldValue)) =>
              converter.evalDynamic(fieldValue) match {
                case Right(results) if results.nonEmpty =>
                  val updated = fields.map {
                    case (n, _) if n == fieldName => (n, results.head)
                    case other                    => other
                  }
                  Right(DynamicValue.Record(updated))
                case Right(_) =>
                  Left(MigrationError.EvaluationFailed(at.field(fieldName), "Converter returned no value"))
                case Left(err) =>
                  Left(MigrationError.EvaluationFailed(at.field(fieldName), err.toString))
              }
            case None => Right(DynamicValue.Record(fields))
          }
        case other =>
          Left(MigrationError.TypeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = this
  }

  // ============================================================================
  // Enum Actions
  // ============================================================================

  /**
   * Rename a case in an enum/variant.
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Variant(name, innerValue) if name == from =>
          Right(DynamicValue.Variant(to, innerValue))
        case other => Right(other)
      }

    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transform the contents of a specific case using nested actions.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Variant(name, innerValue) if name == caseName =>
          actions
            .foldLeft[Either[MigrationError, DynamicValue]](Right(innerValue)) { (acc, action) =>
              acc.flatMap(action.apply)
            }
            .map(transformed => DynamicValue.Variant(caseName, transformed))
        case other => Right(other)
      }

    def reverse: MigrationAction =
      TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }

  // ============================================================================
  // Collection / Map Actions
  // ============================================================================

  /**
   * Transform all elements in a sequence field.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Sequence(elements) =>
          val transformed = elements.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
            (acc, elem) =>
              acc.flatMap { accumulated =>
                transform.evalDynamic(elem) match {
                  case Right(results) if results.nonEmpty => Right(accumulated :+ results.head)
                  case Right(_)                           => Left(MigrationError.EvaluationFailed(at, "Transform returned no value"))
                  case Left(err)                          => Left(MigrationError.EvaluationFailed(at, err.toString))
                }
              }
          }
          transformed.map(DynamicValue.Sequence(_))
        case other =>
          Left(MigrationError.TypeMismatch(at, "Sequence", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = this
  }

  /**
   * Transform all keys in a map.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Map(entries) =>
          val transformed =
            entries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
              (acc, entry) =>
                acc.flatMap { accumulated =>
                  transform.evalDynamic(entry._1) match {
                    case Right(results) if results.nonEmpty => Right(accumulated :+ (results.head -> entry._2))
                    case Right(_)                           => Left(MigrationError.EvaluationFailed(at, "Transform returned no value"))
                    case Left(err)                          => Left(MigrationError.EvaluationFailed(at, err.toString))
                  }
                }
            }
          transformed.map(DynamicValue.Map(_))
        case other =>
          Left(MigrationError.TypeMismatch(at, "Map", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = this
  }

  /**
   * Transform all values in a map.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Map(entries) =>
          val transformed =
            entries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
              (acc, entry) =>
                acc.flatMap { accumulated =>
                  transform.evalDynamic(entry._2) match {
                    case Right(results) if results.nonEmpty => Right(accumulated :+ (entry._1 -> results.head))
                    case Right(_)                           => Left(MigrationError.EvaluationFailed(at, "Transform returned no value"))
                    case Left(err)                          => Left(MigrationError.EvaluationFailed(at, err.toString))
                  }
                }
            }
          transformed.map(DynamicValue.Map(_))
        case other =>
          Left(MigrationError.TypeMismatch(at, "Map", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = this
  }

  // ============================================================================
  // Helper Functions
  // ============================================================================

  /** Apply a transformation at a specific path in the DynamicValue. */
  private def modifyAt(
    value: DynamicValue,
    path: DynamicOptic
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] =
    if (path.nodes.isEmpty) {
      f(value)
    } else {
      path.nodes.head match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              fields.find(_._1 == name) match {
                case Some((_, fieldValue)) =>
                  val remainingPath = DynamicOptic(path.nodes.tail)
                  modifyAt(fieldValue, remainingPath)(f).map { newFieldValue =>
                    val updated = fields.map {
                      case (n, _) if n == name => (n, newFieldValue)
                      case other               => other
                    }
                    DynamicValue.Record(updated)
                  }
                case None =>
                  Left(MigrationError.MissingField(path, name))
              }
            case _ =>
              Left(MigrationError.TypeMismatch(path, "Record", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, innerValue) if name == caseName =>
              val remainingPath = DynamicOptic(path.nodes.tail)
              modifyAt(innerValue, remainingPath)(f).map { newInner =>
                DynamicValue.Variant(name, newInner)
              }
            case other => Right(other) // Not the case we're looking for, pass through
          }

        case DynamicOptic.Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val remainingPath = DynamicOptic(path.nodes.tail)
              elements
                .foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) { (acc, elem) =>
                  acc.flatMap { accumulated =>
                    modifyAt(elem, remainingPath)(f).map(accumulated :+ _)
                  }
                }
                .map(DynamicValue.Sequence(_))
            case _ =>
              Left(MigrationError.TypeMismatch(path, "Sequence", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val remainingPath = DynamicOptic(path.nodes.tail)
              entries
                .foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                  (acc, entry) =>
                    acc.flatMap { accumulated =>
                      modifyAt(entry._2, remainingPath)(f).map(newVal => accumulated :+ (entry._1 -> newVal))
                    }
                }
                .map(DynamicValue.Map(_))
            case _ =>
              Left(MigrationError.TypeMismatch(path, "Map", value.getClass.getSimpleName))
          }

        case _ =>
          // Handle other node types as identity for now
          f(value)
      }
    }
}
