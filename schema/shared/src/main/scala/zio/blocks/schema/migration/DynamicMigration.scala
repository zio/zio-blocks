package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, DynamicSchemaExpr, SchemaError}

/**
 * A pure, serializable migration that operates on `DynamicValue`. This is the
 * untyped core of the migration system, containing a sequence of
 * `MigrationAction`s that are applied in order.
 *
 * `DynamicMigration` is fully serializable because:
 *   - It contains no closures or functions
 *   - All transformations are represented as `DynamicSchemaExpr`
 *   - All paths are represented as data (`DynamicOptic`)
 *
 * This enables migrations to be:
 *   - Stored in registries
 *   - Transmitted over the network
 *   - Applied dynamically
 *   - Inspected and transformed
 *   - Used to generate SQL DDL, upgraders, etc.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a `DynamicValue`.
   *
   * @param value
   *   The input value to migrate
   * @return
   *   Either a `SchemaError` or the migrated value
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicMigration.execute(actions, value)

  /**
   * Compose this migration with another, applying this migration first, then
   * the other.
   *
   * @param that
   *   The migration to apply after this one
   * @return
   *   A new migration that applies both in sequence
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /**
   * Alias for `++`.
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the structural reverse of this migration. The reverse migration has
   * all actions reversed and in reverse order.
   *
   * Note: Runtime execution of the reverse migration is best-effort. It may
   * fail if information was lost during the forward migration (e.g., dropping a
   * field without capturing its value).
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  /**
   * Returns true if this migration has no actions (identity migration).
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * Returns the number of actions in this migration.
   */
  def size: Int = actions.size
}

object DynamicMigration {

  /**
   * An empty migration that performs no transformations.
   */
  val empty: DynamicMigration = new DynamicMigration(Vector.empty)

  /**
   * Create a migration from a single action.
   */
  def single(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  /**
   * Create a migration from multiple actions.
   */
  def apply(actions: MigrationAction*): DynamicMigration =
    new DynamicMigration(actions.toVector)

  /**
   * Execute a sequence of migration actions on a value. Actions are applied in
   * order, with the output of each action becoming the input to the next.
   */
  private[migration] def execute(
    actions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    var current: DynamicValue = value
    var idx                   = 0
    val len                   = actions.length

    while (idx < len) {
      ActionExecutor.execute(actions(idx), current) match {
        case Right(newValue) =>
          current = newValue
          idx += 1
        case left @ Left(_) =>
          return left
      }
    }

    Right(current)
  }
}

/**
 * Internal executor for migration actions. This executor uses DynamicSchemaExpr
 * directly for all expression evaluation.
 */
private[migration] object ActionExecutor {
  import MigrationAction._

  def execute(action: MigrationAction, value: DynamicValue): Either[SchemaError, DynamicValue] =
    action match {
      case a @ AddField(at, default) =>
        evalExpr(default, value).flatMap { defaultValue =>
          executeAddField(at, a.fieldName, defaultValue, value)
        }

      case a @ DropField(at, _) =>
        executeDropField(at, a.fieldName, value)

      case Rename(at, to) =>
        executeRename(at, to, value)

      case TransformValue(at, transform) =>
        evalExpr(transform, value).flatMap { transformValue =>
          executeTransformValueLiteral(at, transformValue, value)
        }

      case Mandate(at, default) =>
        evalExpr(default, value).flatMap { defaultValue =>
          executeMandate(at, defaultValue, value)
        }

      case Optionalize(at) =>
        executeOptionalize(at, value)

      case Join(at, sourcePaths, combiner) =>
        evalExpr(combiner, value).flatMap { combinedValue =>
          executeJoin(at, sourcePaths, combinedValue, value)
        }

      case Split(at, targetPaths, splitter) =>
        evalExpr(splitter, value).flatMap { splitValue =>
          executeSplit(at, targetPaths, splitValue, value)
        }

      case ChangeType(at, converter) =>
        evalExpr(converter, value).flatMap { convertedValue =>
          executeChangeTypeLiteral(at, convertedValue, value)
        }

      case RenameCase(at, from, to) =>
        executeRenameCase(at, from, to, value)

      case TransformCase(at, actions) =>
        executeTransformCase(at, actions, value)

      case TransformElements(at, transform) =>
        evalExpr(transform, value).flatMap { transformValue =>
          executeTransformElements(at, transformValue, value)
        }

      case TransformKeys(at, transform) =>
        evalExpr(transform, value).flatMap { transformValue =>
          executeTransformKeys(at, transformValue, value)
        }

      case TransformValues(at, transform) =>
        evalExpr(transform, value).flatMap { transformValue =>
          executeTransformValues(at, transformValue, value)
        }
    }

  /**
   * Evaluate a DynamicSchemaExpr and extract a single DynamicValue. For now, we
   * only support expressions that return a single value (first value in the
   * sequence).
   */
  private def evalExpr(
    expr: DynamicSchemaExpr,
    input: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    expr.eval(input).flatMap { results =>
      results.headOption match {
        case Some(value) => Right(value)
        case None        =>
          Left(
            SchemaError.transformFailed(
              zio.blocks.schema.DynamicOptic.root,
              s"Expression evaluation returned no values"
            )
          )
      }
    }

  // ==================== Record Action Execution ====================

  private def executeAddField(
    at: zio.blocks.schema.DynamicOptic,
    fieldName: String,
    default: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // The path includes the field name, so we navigate to the parent
    val parentPath = zio.blocks.schema.DynamicOptic(at.nodes.dropRight(1))
    modifyAt(parentPath, value) {
      case DynamicValue.Record(fields) =>
        if (fields.exists(_._1 == fieldName)) {
          Left(SchemaError.fieldAlreadyExists(at, fieldName))
        } else {
          Right(DynamicValue.Record(fields :+ (fieldName -> default)))
        }
      case other =>
        Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
    }
  }

  private def executeDropField(
    at: zio.blocks.schema.DynamicOptic,
    fieldName: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // The path includes the field name, so we navigate to the parent
    val parentPath = zio.blocks.schema.DynamicOptic(at.nodes.dropRight(1))
    modifyAt(parentPath, value) {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == fieldName)
        if (idx < 0) {
          Left(SchemaError.fieldNotFound(at, fieldName))
        } else {
          Right(DynamicValue.Record(fields.patch(idx, Nil, 1)))
        }
      case other =>
        Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
    }
  }

  private def executeRename(
    at: zio.blocks.schema.DynamicOptic,
    to: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // Extract from name and parent path
    val from = at.nodes.lastOption match {
      case Some(zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
      case _                                                     => return Left(SchemaError.transformFailed(at, "Rename path must end with a Field node"))
    }
    val parentPath = zio.blocks.schema.DynamicOptic(at.nodes.dropRight(1))

    modifyAt(parentPath, value) {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == from)
        if (idx < 0) {
          Left(SchemaError.fieldNotFound(at, from))
        } else if (fields.exists(_._1 == to)) {
          Left(SchemaError.fieldAlreadyExists(at, to))
        } else {
          val (_, v) = fields(idx)
          Right(DynamicValue.Record(fields.updated(idx, (to, v))))
        }
      case other =>
        Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
    }
  }

  private def executeTransformValueLiteral(
    at: zio.blocks.schema.DynamicOptic,
    newValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { _ =>
      Right(newValue)
    }

  private def executeChangeTypeLiteral(
    at: zio.blocks.schema.DynamicOptic,
    convertedValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { _ =>
      Right(convertedValue)
    }

  // ==================== Collection/Map Action Execution ====================

  private def executeTransformElements(
    at: zio.blocks.schema.DynamicOptic,
    transformValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Sequence(elements) =>
        Right(DynamicValue.Sequence(elements.map(_ => transformValue)))
      case other =>
        Left(SchemaError.typeMismatch(at, "Sequence", other.getClass.getSimpleName))
    }

  private def executeTransformKeys(
    at: zio.blocks.schema.DynamicOptic,
    transformValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Map(entries) =>
        Right(DynamicValue.Map(entries.map { case (_, v) => (transformValue, v) }))
      case other =>
        Left(SchemaError.typeMismatch(at, "Map", other.getClass.getSimpleName))
    }

  private def executeTransformValues(
    at: zio.blocks.schema.DynamicOptic,
    transformValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Map(entries) =>
        Right(DynamicValue.Map(entries.map { case (k, _) => (k, transformValue) }))
      case other =>
        Left(SchemaError.typeMismatch(at, "Map", other.getClass.getSimpleName))
    }

  // ==================== Join/Split Action Execution ====================

  private def executeJoin(
    at: zio.blocks.schema.DynamicOptic,
    @scala.annotation.unused sourcePaths: Vector[zio.blocks.schema.DynamicOptic],
    combinedValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // For Literal-based join: set the target field to the combined value
    // The source fields are NOT removed (caller should use DropField if needed)
    val parentPath = zio.blocks.schema.DynamicOptic(at.nodes.dropRight(1))
    val fieldName  = at.nodes.lastOption match {
      case Some(zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
      case _                                                     => return Left(SchemaError.transformFailed(at, "Join target path must end with a Field node"))
    }
    modifyAt(parentPath, value) {
      case DynamicValue.Record(fields) =>
        Right(DynamicValue.Record(fields :+ (fieldName -> combinedValue)))
      case other =>
        Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
    }
  }

  private def executeSplit(
    @scala.annotation.unused at: zio.blocks.schema.DynamicOptic,
    targetPaths: Vector[zio.blocks.schema.DynamicOptic],
    splitValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    targetPaths.foldLeft[Either[SchemaError, DynamicValue]](Right(value)) {
      case (Right(current), targetPath) =>
        val parentPath = zio.blocks.schema.DynamicOptic(targetPath.nodes.dropRight(1))
        targetPath.nodes.lastOption match {
          case Some(zio.blocks.schema.DynamicOptic.Node.Field(fieldName)) =>
            modifyAt(parentPath, current) {
              case DynamicValue.Record(fields) =>
                Right(DynamicValue.Record(fields :+ (fieldName -> splitValue)))
              case other =>
                Left(SchemaError.typeMismatch(targetPath, "Record", other.getClass.getSimpleName))
            }
          case _ =>
            Left(SchemaError.transformFailed(targetPath, "Split target path must end with a Field node"))
        }
      case (left, _) =>
        left
    }

  private def executeMandate(
    at: zio.blocks.schema.DynamicOptic,
    default: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      // Option is represented as a Variant with cases "None" and "Some"
      case DynamicValue.Variant("None", _) =>
        Right(default)
      case DynamicValue.Variant("Some", inner) =>
        Right(inner)
      case other =>
        // If it's not an Option variant, assume it's already the value
        Right(other)
    }

  private def executeOptionalize(
    at: zio.blocks.schema.DynamicOptic,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { v =>
      // Wrap the value in Some
      Right(DynamicValue.Variant("Some", v))
    }

  // ==================== Enum Action Execution ====================

  private def executeRenameCase(
    at: zio.blocks.schema.DynamicOptic,
    from: String,
    to: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Variant(caseName, inner) if caseName == from =>
        Right(DynamicValue.Variant(to, inner))
      case v @ DynamicValue.Variant(_, _) =>
        // Different case, no change needed
        Right(v)
      case other =>
        Left(SchemaError.typeMismatch(at, "Variant", other.getClass.getSimpleName))
    }

  private def executeTransformCase(
    at: zio.blocks.schema.DynamicOptic,
    actions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Variant(name, inner) =>
        // Apply the migration actions to the inner value of any variant
        DynamicMigration.execute(actions, inner).map(DynamicValue.Variant(name, _))
      case other =>
        Left(SchemaError.typeMismatch(at, "Variant", other.getClass.getSimpleName))
    }

  // ==================== Helper Methods ====================

  /**
   * Navigate to a path and apply a modification function. If the path is empty
   * (root), apply directly to the value.
   */
  private def modifyAt(
    path: zio.blocks.schema.DynamicOptic,
    value: DynamicValue
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) {
      f(value)
    } else {
      modifyAtPath(nodes, 0, value, path)(f)
    }
  }

  private def modifyAtPath(
    nodes: IndexedSeq[zio.blocks.schema.DynamicOptic.Node],
    idx: Int,
    value: DynamicValue,
    fullPath: zio.blocks.schema.DynamicOptic
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    import zio.blocks.schema.DynamicOptic.Node

    if (idx >= nodes.length) {
      f(value)
    } else {
      nodes(idx) match {
        case Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0) {
                Left(SchemaError.fieldNotFound(fullPath, name))
              } else {
                val (fieldName, fieldValue) = fields(fieldIdx)
                modifyAtPath(nodes, idx + 1, fieldValue, fullPath)(f).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Record", other.getClass.getSimpleName))
          }

        case Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, inner) if name == caseName =>
              modifyAtPath(nodes, idx + 1, inner, fullPath)(f).map { newInner =>
                DynamicValue.Variant(name, newInner)
              }
            case DynamicValue.Variant(_, _) =>
              Left(SchemaError.caseNotFound(fullPath, caseName))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Variant", other.getClass.getSimpleName))
          }

        case Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val results = elements.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
                case (Right(acc), elem) =>
                  modifyAtPath(nodes, idx + 1, elem, fullPath)(f).map(acc :+ _)
                case (left, _) =>
                  left
              }
              results.map(DynamicValue.Sequence(_))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Sequence", other.getClass.getSimpleName))
          }

        case Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val results =
                entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (Right(acc), (k, v)) =>
                    modifyAtPath(nodes, idx + 1, k, fullPath)(f).map(newK => acc :+ (newK -> v))
                  case (left, _) =>
                    left
                }
              results.map(DynamicValue.Map(_))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val results =
                entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (Right(acc), (k, v)) =>
                    modifyAtPath(nodes, idx + 1, v, fullPath)(f).map(newV => acc :+ (k -> newV))
                  case (left, _) =>
                    left
                }
              results.map(DynamicValue.Map(_))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length) {
                Left(SchemaError.pathNotFound(fullPath))
              } else {
                modifyAtPath(nodes, idx + 1, elements(index), fullPath)(f).map { newElem =>
                  DynamicValue.Sequence(elements.updated(index, newElem))
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Sequence", other.getClass.getSimpleName))
          }

        case Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val keyValue = key.asInstanceOf[DynamicValue]
              val entryIdx = entries.indexWhere(_._1 == keyValue)
              if (entryIdx < 0) {
                Left(SchemaError.pathNotFound(fullPath))
              } else {
                val (k, v) = entries(entryIdx)
                modifyAtPath(nodes, idx + 1, v, fullPath)(f).map { newV =>
                  DynamicValue.Map(entries.updated(entryIdx, (k, newV)))
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.Wrapped =>
          // For wrapper types, just continue to the inner value
          modifyAtPath(nodes, idx + 1, value, fullPath)(f)

        case _ =>
          Left(SchemaError.transformFailed(fullPath, s"Unsupported path node: ${nodes(idx)}"))
      }
    }
  }
}
