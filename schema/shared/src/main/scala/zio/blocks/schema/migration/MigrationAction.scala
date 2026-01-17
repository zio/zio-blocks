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
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue]

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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          if (fields.exists(_._1 == fieldName)) Right(DynamicValue.Record(fields))
          else Right(DynamicValue.Record(fields :+ (fieldName -> default)))
        case other =>
          Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
        case other =>
          Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          val renamed = fields.map {
            case (name, v) if name == from => (to, v)
            case other                     => other
          }
          Right(DynamicValue.Record(renamed))
        case other =>
          Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) { current =>
        transform.evalDynamic(current) match {
          case Right(results) if results.nonEmpty => Right(results.head)
          case Right(_)                           => Left(SchemaError.evaluationFailed(at, "Transform returned no value"))
          case Left(err)                          => Left(SchemaError.evaluationFailed(at, err.toString))
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
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
          Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    def reverse: MigrationAction = Optionalize(at, fieldName)
  }

  /**
   * Make a field optional by wrapping its value in Some.
   *
   * Note: Option types in Scala are represented as:
   *   - Some(x) -> DynamicValue.Variant("Some",
   *     DynamicValue.Record(Vector("value" -> x)))
   *   - None -> DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
   */
  final case class Optionalize(
    at: DynamicOptic,
    fieldName: String
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Record(fields) =>
          val updated = fields.map {
            case (name, v) if name == fieldName =>
              // Wrap in Some with the correct structure: Variant("Some", Record(Vector("value" -> v)))
              (name, DynamicValue.Variant("Some", DynamicValue.Record(Vector("value" -> v))))
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
          Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }

    // Optionalize reverse needs a default - use empty record as placeholder
    def reverse: MigrationAction = Mandate(at, fieldName, DynamicValue.Record(Vector.empty))
  }

  /**
   * Join multiple source fields into a single target field.
   *
   * Supports cross-nesting-level joins where source paths can be at different
   * nesting depths (e.g., `_.address.street` + `_.origin.country` ->
   * `_.address.fullAddress`).
   *
   * The combiner receives a `DynamicValue.Sequence` containing the source
   * values in the same order as `sourcePaths`, making it easy to work with.
   *
   * @param at
   *   The base path (typically root for cross-level joins)
   * @param sourcePaths
   *   Full paths to source fields (can be at different nesting levels)
   * @param targetPath
   *   Full path to target field
   * @param combiner
   *   Expression that receives Sequence of source values and produces target
   *   value
   * @param splitterForReverse
   *   Optional splitter for reverse operation (Split)
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    targetPath: DynamicOptic,
    combiner: SchemaExpr[DynamicValue, DynamicValue],
    splitterForReverse: Option[SchemaExpr[DynamicValue, DynamicValue]] = None
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      // Extract values from all source paths
      val sourceValuesResult = sourcePaths.foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) {
        (acc, path) =>
          acc.flatMap { accumulated =>
            getAt(value, path) match {
              case Some(v) => Right(accumulated :+ v)
              case None    => Left(SchemaError.missingField(Nil, path.toString))
            }
          }
      }

      sourceValuesResult.flatMap { sourceValues =>
        // Pass source values as a Sequence (clean API for combiner)
        val sourceSequence = DynamicValue.Sequence(sourceValues)

        // Apply combiner
        combiner.evalDynamic(sourceSequence) match {
          case Right(results) if results.nonEmpty =>
            val combinedValue = results.head
            // Remove source fields and set target field
            val withRemovals = sourcePaths.foldLeft[Either[SchemaError, DynamicValue]](Right(value)) { (acc, path) =>
              acc.flatMap(removeAt(_, path))
            }
            withRemovals.flatMap(setAt(_, targetPath, combinedValue))
          case Right(_) =>
            Left(SchemaError.evaluationFailed(at, "Join combiner returned no value"))
          case Left(err) =>
            Left(SchemaError.evaluationFailed(at, err.toString))
        }
      }
    }

    /** Reverse of Join is Split (if splitter is provided). */
    def reverse: MigrationAction = splitterForReverse match {
      case Some(splitter) =>
        Split(at, targetPath, sourcePaths, splitter, Some(combiner))
      case None =>
        // Best-effort: return self (not semantically reversible without splitter)
        this
    }
  }

  /**
   * Split a source field into multiple target fields.
   *
   * Supports cross-nesting-level splits where target paths can be at different
   * nesting depths (e.g., `_.fullName` -> `_.address.street` +
   * `_.origin.country`).
   *
   * The splitter receives a single source value and should return a `Sequence`
   * containing values for each target path in the same order.
   *
   * @param at
   *   The base path (typically root for cross-level splits)
   * @param sourcePath
   *   Full path to source field
   * @param targetPaths
   *   Full paths to target fields (can be at different nesting levels)
   * @param splitter
   *   Expression that receives source value and produces Sequence of target
   *   values
   * @param combinerForReverse
   *   Optional combiner for reverse operation (Join)
   */
  final case class Split(
    at: DynamicOptic,
    sourcePath: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[DynamicValue, DynamicValue],
    combinerForReverse: Option[SchemaExpr[DynamicValue, DynamicValue]] = None
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      // Get the source value
      getAt(value, sourcePath) match {
        case None =>
          Left(SchemaError.missingField(Nil, sourcePath.toString))
        case Some(sourceValue) =>
          // Apply splitter to get target values
          splitter.evalDynamic(sourceValue) match {
            case Right(results) =>
              // Handle both direct results and Sequence-wrapped results
              val splitValues: Vector[DynamicValue] = results.headOption match {
                case Some(DynamicValue.Sequence(elements)) => elements
                case _                                     => results.toVector
              }

              if (splitValues.length >= targetPaths.length) {
                // Remove source field first
                removeAt(value, sourcePath).flatMap { withoutSource =>
                  // Set each target path with corresponding result
                  targetPaths.zip(splitValues).foldLeft[Either[SchemaError, DynamicValue]](Right(withoutSource)) {
                    case (acc, (path, v)) => acc.flatMap(setAt(_, path, v))
                  }
                }
              } else {
                Left(
                  SchemaError.evaluationFailed(
                    at,
                    s"Split splitter returned ${splitValues.length} values, but ${targetPaths.length} expected"
                  )
                )
              }
            case Left(err) =>
              Left(SchemaError.evaluationFailed(at, err.toString))
          }
      }

    /** Reverse of Split is Join (if combiner is provided). */
    def reverse: MigrationAction = combinerForReverse match {
      case Some(combiner) =>
        Join(at, targetPaths, sourcePath, combiner, Some(splitter))
      case None =>
        // Best-effort: return self (not semantically reversible without combiner)
        this
    }
  }

  /**
   * Change the type of a field using a converter expression.
   */
  final case class ChangeType(
    at: DynamicOptic,
    fieldName: String,
    converter: SchemaExpr[DynamicValue, DynamicValue]
  ) extends MigrationAction {

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
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
                  Left(SchemaError.evaluationFailed(at.field(fieldName), "Converter returned no value"))
                case Left(err) =>
                  Left(SchemaError.evaluationFailed(at.field(fieldName), err.toString))
              }
            case None => Right(DynamicValue.Record(fields))
          }
        case other =>
          Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Variant(name, innerValue) if name == caseName =>
          actions
            .foldLeft[Either[SchemaError, DynamicValue]](Right(innerValue)) { (acc, action) =>
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Sequence(elements) =>
          val transformed = elements.foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) {
            (acc, elem) =>
              acc.flatMap { accumulated =>
                transform.evalDynamic(elem) match {
                  case Right(results) if results.nonEmpty => Right(accumulated :+ results.head)
                  case Right(_)                           => Left(SchemaError.evaluationFailed(at, "Transform returned no value"))
                  case Left(err)                          => Left(SchemaError.evaluationFailed(at, err.toString))
                }
              }
          }
          transformed.map(DynamicValue.Sequence(_))
        case other =>
          Left(SchemaError.typeMismatch(at, "Sequence", other.getClass.getSimpleName))
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Map(entries) =>
          val transformed =
            entries.foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
              (acc, entry) =>
                acc.flatMap { accumulated =>
                  transform.evalDynamic(entry._1) match {
                    case Right(results) if results.nonEmpty => Right(accumulated :+ (results.head -> entry._2))
                    case Right(_)                           => Left(SchemaError.evaluationFailed(at, "Transform returned no value"))
                    case Left(err)                          => Left(SchemaError.evaluationFailed(at, err.toString))
                  }
                }
            }
          transformed.map(DynamicValue.Map(_))
        case other =>
          Left(SchemaError.typeMismatch(at, "Map", other.getClass.getSimpleName))
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

    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      modifyAt(value, at) {
        case DynamicValue.Map(entries) =>
          val transformed =
            entries.foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
              (acc, entry) =>
                acc.flatMap { accumulated =>
                  transform.evalDynamic(entry._2) match {
                    case Right(results) if results.nonEmpty => Right(accumulated :+ (entry._1 -> results.head))
                    case Right(_)                           => Left(SchemaError.evaluationFailed(at, "Transform returned no value"))
                    case Left(err)                          => Left(SchemaError.evaluationFailed(at, err.toString))
                  }
                }
            }
          transformed.map(DynamicValue.Map(_))
        case other =>
          Left(SchemaError.typeMismatch(at, "Map", other.getClass.getSimpleName))
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
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] =
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
                  Left(SchemaError.missingField(Nil, name))
              }
            case _ =>
              Left(SchemaError.typeMismatch(path, "Record", value.getClass.getSimpleName))
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
                .foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) { (acc, elem) =>
                  acc.flatMap { accumulated =>
                    modifyAt(elem, remainingPath)(f).map(accumulated :+ _)
                  }
                }
                .map(DynamicValue.Sequence(_))
            case _ =>
              Left(SchemaError.typeMismatch(path, "Sequence", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val remainingPath = DynamicOptic(path.nodes.tail)
              entries
                .foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                  (acc, entry) =>
                    acc.flatMap { accumulated =>
                      modifyAt(entry._2, remainingPath)(f).map(newVal => accumulated :+ (entry._1 -> newVal))
                    }
                }
                .map(DynamicValue.Map(_))
            case _ =>
              Left(SchemaError.typeMismatch(path, "Map", value.getClass.getSimpleName))
          }

        case _ =>
          // Handle other node types as identity for now
          f(value)
      }
    }

  /** Get a value at a specific path in the DynamicValue. */
  private def getAt(value: DynamicValue, path: DynamicOptic): Option[DynamicValue] =
    if (path.nodes.isEmpty) {
      Some(value)
    } else {
      path.nodes.head match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              fields.find(_._1 == name).flatMap { case (_, fieldValue) =>
                getAt(fieldValue, DynamicOptic(path.nodes.tail))
              }
            case _ => None
          }

        case DynamicOptic.Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, innerValue) if name == caseName =>
              getAt(innerValue, DynamicOptic(path.nodes.tail))
            case _ => None
          }

        case _ => None
      }
    }

  /**
   * Set a value at a specific path in the DynamicValue, creating intermediate
   * records if needed.
   */
  private def setAt(
    value: DynamicValue,
    path: DynamicOptic,
    newValue: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    if (path.nodes.isEmpty) {
      Right(newValue)
    } else {
      path.nodes.head match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val remainingPath = DynamicOptic(path.nodes.tail)
              fields.find(_._1 == name) match {
                case Some((_, fieldValue)) =>
                  setAt(fieldValue, remainingPath, newValue).map { updated =>
                    val newFields = fields.map {
                      case (n, _) if n == name => (n, updated)
                      case other               => other
                    }
                    DynamicValue.Record(newFields)
                  }
                case None =>
                  // Field doesn't exist, create it
                  if (remainingPath.nodes.isEmpty) {
                    Right(DynamicValue.Record(fields :+ (name -> newValue)))
                  } else {
                    // Need to create intermediate structure
                    setAt(DynamicValue.Record(Vector.empty), remainingPath, newValue).map { nested =>
                      DynamicValue.Record(fields :+ (name -> nested))
                    }
                  }
              }
            case other =>
              Left(SchemaError.typeMismatch(path, "Record", other.getClass.getSimpleName))
          }

        case _ =>
          Left(SchemaError.evaluationFailed(path, "Unsupported path node for setAt"))
      }
    }

  /** Remove a value at a specific path in the DynamicValue. */
  private def removeAt(value: DynamicValue, path: DynamicOptic): Either[SchemaError, DynamicValue] =
    if (path.nodes.isEmpty) {
      // Can't remove root
      Left(SchemaError.evaluationFailed(path, "Cannot remove root value"))
    } else if (path.nodes.length == 1) {
      // Last node in path - remove this field
      path.nodes.head match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              Right(DynamicValue.Record(fields.filterNot(_._1 == name)))
            case other =>
              Left(SchemaError.typeMismatch(path, "Record", other.getClass.getSimpleName))
          }
        case _ =>
          Left(SchemaError.evaluationFailed(path, "Unsupported path node for removeAt"))
      }
    } else {
      // Navigate deeper
      path.nodes.head match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              fields.find(_._1 == name) match {
                case Some((_, fieldValue)) =>
                  val remainingPath = DynamicOptic(path.nodes.tail)
                  removeAt(fieldValue, remainingPath).map { updated =>
                    val newFields = fields.map {
                      case (n, _) if n == name => (n, updated)
                      case other               => other
                    }
                    DynamicValue.Record(newFields)
                  }
                case None =>
                  Right(value) // Field doesn't exist, nothing to remove
              }
            case other =>
              Left(SchemaError.typeMismatch(path, "Record", other.getClass.getSimpleName))
          }
        case _ =>
          Left(SchemaError.evaluationFailed(path, "Unsupported path node for removeAt"))
      }
    }
}
