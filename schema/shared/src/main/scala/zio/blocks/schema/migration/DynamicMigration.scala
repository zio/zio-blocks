package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaError}

/**
 * A pure, serializable migration that operates on `DynamicValue`. This is the
 * untyped core of the migration system, containing a sequence of
 * `MigrationAction`s that are applied in order.
 *
 * `DynamicMigration` is fully serializable because:
 *   - It contains no closures or functions
 *   - All transformations use `DynamicValue` directly
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
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /** Alias for `++`. */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the structural reverse of this migration. The reverse migration has
   * all actions reversed and in reverse order.
   *
   * Note: Runtime execution of the reverse migration is best-effort. It may
   * fail if information was lost during the forward migration.
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  /** Returns true if this migration has no actions (identity migration). */
  def isEmpty: Boolean = actions.isEmpty

  /** Returns the number of actions in this migration. */
  def size: Int = actions.size
}

object DynamicMigration {

  /** An empty migration that performs no transformations. */
  val empty: DynamicMigration = new DynamicMigration(Vector.empty)

  /** Create a migration from a single action. */
  def single(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  /** Create a migration from multiple actions. */
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

/** Internal executor for migration actions. */
private[migration] object ActionExecutor {
  import MigrationAction._

  def execute(action: MigrationAction, value: DynamicValue): Either[SchemaError, DynamicValue] =
    action match {
      case a @ AddField(at, default) =>
        executeAddField(at, a.fieldName, default, value)

      case a @ DropField(at, _) =>
        executeDropField(at, a.fieldName, value)

      case Rename(at, to) =>
        executeRename(at, to, value)

      case TransformValue(at, newValue) =>
        executeTransformValue(at, newValue, value)

      case TransformValueExpr(at, expr, _) =>
        executeTransformValueExpr(at, expr, value)

      case Mandate(at, default) =>
        executeMandate(at, default, value)

      case Optionalize(at) =>
        executeOptionalize(at, value)

      case Nest(at, fieldName, sourceFields) =>
        executeNest(at, fieldName, sourceFields, value)

      case Unnest(at, fieldName, extractedFields) =>
        executeUnnest(at, fieldName, extractedFields, value)

      case Join(at, sourcePaths, combinedValue) =>
        executeJoin(at, sourcePaths, combinedValue, value)

      case JoinExpr(at, sourcePaths, combineExpr, _) =>
        executeJoinExpr(at, sourcePaths, combineExpr, value)

      case Split(_, targetPaths, splitValue) =>
        executeSplit(targetPaths, splitValue, value)

      case SplitExpr(_, targetPaths, splitExprs, _) =>
        executeSplitExpr(targetPaths, splitExprs, value)

      case ChangeType(at, convertedValue) =>
        executeChangeType(at, convertedValue, value)

      case ChangeTypeExpr(at, convertExpr, _) =>
        executeChangeTypeExpr(at, convertExpr, value)

      case RenameCase(at, from, to) =>
        executeRenameCase(at, from, to, value)

      case TransformCase(at, caseName, actions) =>
        executeTransformCase(at, caseName, actions, value)

      case TransformElements(at, elementActions) =>
        executeTransformElements(at, elementActions, value)

      case TransformKeys(at, keyActions) =>
        executeTransformKeys(at, keyActions, value)

      case TransformValues(at, valueActions) =>
        executeTransformValues(at, valueActions, value)
    }

  // ==================== Record Action Execution ====================

  private def executeAddField(
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    val parentPath = DynamicOptic(at.nodes.dropRight(1))
    modifyAt(parentPath, value, at) {
      case DynamicValue.Record(fields) =>
        if (fields.exists(_._1 == fieldName)) {
          Left(SchemaError.message(s"Field '$fieldName' already exists", at))
        } else {
          Right(DynamicValue.Record(fields :+ (fieldName -> default)))
        }
      case other =>
        Left(SchemaError.message(s"Expected Record, got ${other.valueType}", at))
    }
  }

  private def executeDropField(
    at: DynamicOptic,
    fieldName: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    val parentPath = DynamicOptic(at.nodes.dropRight(1))
    modifyAt(parentPath, value, at) {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == fieldName)
        if (idx < 0) {
          Left(SchemaError.message(s"Field '$fieldName' not found", at))
        } else {
          Right(DynamicValue.Record(fields.patch(idx, Nil, 1)))
        }
      case other =>
        Left(SchemaError.message(s"Expected Record, got ${other.valueType}", at))
    }
  }

  private def executeRename(
    at: DynamicOptic,
    to: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    val from = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => return Left(SchemaError.message("Rename path must end with a Field node", at))
    }
    val parentPath = DynamicOptic(at.nodes.dropRight(1))

    modifyAt(parentPath, value, at) {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == from)
        if (idx < 0) {
          Left(SchemaError.message(s"Field '$from' not found", at))
        } else if (fields.exists(_._1 == to)) {
          Left(SchemaError.message(s"Field '$to' already exists", at))
        } else {
          val (_, v) = fields(idx)
          Right(DynamicValue.Record(fields.updated(idx, (to, v))))
        }
      case other =>
        Left(SchemaError.message(s"Expected Record, got ${other.valueType}", at))
    }
  }

  private def executeTransformValue(
    at: DynamicOptic,
    newValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at)(_ => Right(newValue))

  private def executeTransformValueExpr(
    at: DynamicOptic,
    expr: MigrationExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) { current =>
      expr.eval(current).left.map(err => SchemaError.message(err, at))
    }

  private def executeChangeType(
    at: DynamicOptic,
    convertedValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at)(_ => Right(convertedValue))

  private def executeChangeTypeExpr(
    at: DynamicOptic,
    convertExpr: MigrationExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) { current =>
      convertExpr.eval(current).left.map(err => SchemaError.message(err, at))
    }

  private def executeMandate(
    at: DynamicOptic,
    default: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) {
      case DynamicValue.Variant("None", _)     => Right(default)
      case DynamicValue.Variant("Some", inner) => Right(inner)
      case other                               => Right(other) // Already not optional
    }

  private def executeOptionalize(
    at: DynamicOptic,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at)(v => Right(DynamicValue.Variant("Some", v)))

  // ==================== Nest/Unnest Action Execution ====================

  private def executeNest(
    at: DynamicOptic,
    fieldName: String,
    sourceFields: Vector[String],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) {
      case DynamicValue.Record(fields) =>
        val extracted = Chunk.fromIterable(sourceFields.flatMap { name =>
          fields.find(_._1 == name)
        })
        if (extracted.length != sourceFields.length) {
          val missing = sourceFields.filterNot(n => fields.exists(_._1 == n))
          Left(SchemaError.message(s"Fields not found: ${missing.mkString(", ")}", at))
        } else {
          val remaining    = fields.filterNot(f => sourceFields.contains(f._1))
          val nestedRecord = DynamicValue.Record(extracted)
          Right(DynamicValue.Record(remaining :+ (fieldName -> nestedRecord)))
        }
      case other =>
        Left(SchemaError.message(s"Expected Record, got ${other.valueType}", at))
    }

  private def executeUnnest(
    at: DynamicOptic,
    fieldName: String,
    extractedFields: Vector[String],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) {
      case DynamicValue.Record(fields) =>
        val nestedIdx = fields.indexWhere(_._1 == fieldName)
        if (nestedIdx < 0) {
          Left(SchemaError.message(s"Nested field '$fieldName' not found", at))
        } else {
          fields(nestedIdx)._2 match {
            case DynamicValue.Record(nestedFields) =>
              val toExtract = Chunk.fromIterable(extractedFields.flatMap { name =>
                nestedFields.find(_._1 == name)
              })
              if (toExtract.length != extractedFields.length) {
                val missing = extractedFields.filterNot(n => nestedFields.exists(_._1 == n))
                Left(SchemaError.message(s"Fields not found in nested record: ${missing.mkString(", ")}", at))
              } else {
                val withoutNested = fields.patch(nestedIdx, Nil, 1)
                Right(DynamicValue.Record(withoutNested ++ toExtract))
              }
            case other =>
              Left(SchemaError.message(s"Expected nested Record, got ${other.valueType}", at))
          }
        }
      case other =>
        Left(SchemaError.message(s"Expected Record, got ${other.valueType}", at))
    }

  // ==================== Join/Split Action Execution ====================

  private def executeJoin(
    at: DynamicOptic,
    @annotation.unused sourcePaths: Vector[DynamicOptic],
    combinedValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    val parentPath = DynamicOptic(at.nodes.dropRight(1))
    val fieldName  = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => return Left(SchemaError.message("Join target path must end with a Field node", at))
    }
    modifyAt(parentPath, value, at) {
      case DynamicValue.Record(fields) =>
        Right(DynamicValue.Record(fields :+ (fieldName -> combinedValue)))
      case other =>
        Left(SchemaError.message(s"Expected Record, got ${other.valueType}", at))
    }
  }

  private def executeJoinExpr(
    at: DynamicOptic,
    @annotation.unused sourcePaths: Vector[DynamicOptic],
    combineExpr: MigrationExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    val parentPath = DynamicOptic(at.nodes.dropRight(1))
    val fieldName  = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => return Left(SchemaError.message("JoinExpr target path must end with a Field node", at))
    }
    // Evaluate the combine expression against the full input value
    combineExpr.eval(value) match {
      case Right(combinedValue) =>
        modifyAt(parentPath, value, at) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields :+ (fieldName -> combinedValue)))
          case other =>
            Left(SchemaError.message(s"Expected Record, got ${other.valueType}", at))
        }
      case Left(err) =>
        Left(SchemaError.message(err, at))
    }
  }

  private def executeSplit(
    targetPaths: Vector[DynamicOptic],
    splitValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    targetPaths.foldLeft[Either[SchemaError, DynamicValue]](Right(value)) {
      case (Right(current), targetPath) =>
        val parentPath = DynamicOptic(targetPath.nodes.dropRight(1))
        targetPath.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(fieldName)) =>
            modifyAt(parentPath, current, targetPath) {
              case DynamicValue.Record(fields) =>
                Right(DynamicValue.Record(fields :+ (fieldName -> splitValue)))
              case other =>
                Left(SchemaError.message(s"Expected Record, got ${other.valueType}", targetPath))
            }
          case _ =>
            Left(SchemaError.message("Split target path must end with a Field node", targetPath))
        }
      case (left, _) => left
    }

  private def executeSplitExpr(
    targetPaths: Vector[DynamicOptic],
    splitExprs: Vector[MigrationExpr],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    if (targetPaths.length != splitExprs.length) {
      return Left(
        SchemaError.message(
          s"SplitExpr: targetPaths (${targetPaths.length}) and splitExprs (${splitExprs.length}) must have same length",
          DynamicOptic.root
        )
      )
    }
    targetPaths.zip(splitExprs).foldLeft[Either[SchemaError, DynamicValue]](Right(value)) {
      case (Right(current), (targetPath, splitExpr)) =>
        val parentPath = DynamicOptic(targetPath.nodes.dropRight(1))
        targetPath.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(fieldName)) =>
            // Evaluate the split expression against the full input value
            splitExpr.eval(value) match {
              case Right(splitValue) =>
                modifyAt(parentPath, current, targetPath) {
                  case DynamicValue.Record(fields) =>
                    Right(DynamicValue.Record(fields :+ (fieldName -> splitValue)))
                  case other =>
                    Left(SchemaError.message(s"Expected Record, got ${other.valueType}", targetPath))
                }
              case Left(err) =>
                Left(SchemaError.message(err, targetPath))
            }
          case _ =>
            Left(SchemaError.message("SplitExpr target path must end with a Field node", targetPath))
        }
      case (left, _) => left
    }
  }

  // ==================== Enum Action Execution ====================

  private def executeRenameCase(
    at: DynamicOptic,
    from: String,
    to: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) {
      case DynamicValue.Variant(caseName, inner) if caseName == from =>
        Right(DynamicValue.Variant(to, inner))
      case v @ DynamicValue.Variant(_, _) =>
        Right(v) // Different case, no change needed
      case other =>
        Left(SchemaError.message(s"Expected Variant, got ${other.valueType}", at))
    }

  private def executeTransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) {
      case DynamicValue.Variant(name, inner) if name == caseName =>
        DynamicMigration.execute(actions, inner).map(DynamicValue.Variant(name, _))
      case v @ DynamicValue.Variant(_, _) =>
        Right(v) // Different case, no change needed
      case other =>
        Left(SchemaError.message(s"Expected Variant, got ${other.valueType}", at))
    }

  // ==================== Collection/Map Action Execution ====================

  private def executeTransformElements(
    at: DynamicOptic,
    elementActions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) {
      case DynamicValue.Sequence(elements) =>
        val results = elements.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
          case (Right(acc), elem) =>
            DynamicMigration.execute(elementActions, elem).map(acc :+ _)
          case (left, _) => left
        }
        results.map(DynamicValue.Sequence(_))
      case other =>
        Left(SchemaError.message(s"Expected Sequence, got ${other.valueType}", at))
    }

  private def executeTransformKeys(
    at: DynamicOptic,
    keyActions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) {
      case DynamicValue.Map(entries) =>
        val results = entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
          case (Right(acc), (k, v)) =>
            DynamicMigration.execute(keyActions, k).map(newK => acc :+ (newK -> v))
          case (left, _) => left
        }
        results.map(DynamicValue.Map(_))
      case other =>
        Left(SchemaError.message(s"Expected Map, got ${other.valueType}", at))
    }

  private def executeTransformValues(
    at: DynamicOptic,
    valueActions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) {
      case DynamicValue.Map(entries) =>
        val results = entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
          case (Right(acc), (k, v)) =>
            DynamicMigration.execute(valueActions, v).map(newV => acc :+ (k -> newV))
          case (left, _) => left
        }
        results.map(DynamicValue.Map(_))
      case other =>
        Left(SchemaError.message(s"Expected Map, got ${other.valueType}", at))
    }

  // ==================== Helper Methods ====================

  /**
   * Navigate to a path and apply a modification function. If the path is empty
   * (root), apply directly to the value.
   */
  private def modifyAt(
    path: DynamicOptic,
    value: DynamicValue,
    fullPath: DynamicOptic
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) f(value)
    else modifyAtPath(nodes, 0, value, fullPath)(f)
  }

  private def modifyAtPath(
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: DynamicValue,
    fullPath: DynamicOptic
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    import DynamicOptic.Node

    if (idx >= nodes.length) {
      f(value)
    } else {
      nodes(idx) match {
        case Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0) {
                Left(SchemaError.message(s"Field '$name' not found", fullPath))
              } else {
                val (fieldName, fieldValue) = fields(fieldIdx)
                modifyAtPath(nodes, idx + 1, fieldValue, fullPath)(f).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            case other =>
              Left(SchemaError.message(s"Expected Record, got ${other.valueType}", fullPath))
          }

        case Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, inner) if name == caseName =>
              modifyAtPath(nodes, idx + 1, inner, fullPath)(f).map { newInner =>
                DynamicValue.Variant(name, newInner)
              }
            case DynamicValue.Variant(_, _) =>
              Left(SchemaError.message(s"Case '$caseName' not found", fullPath))
            case other =>
              Left(SchemaError.message(s"Expected Variant, got ${other.valueType}", fullPath))
          }

        case Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val results = elements.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
                case (Right(acc), elem) =>
                  modifyAtPath(nodes, idx + 1, elem, fullPath)(f).map(acc :+ _)
                case (left, _) => left
              }
              results.map(DynamicValue.Sequence(_))
            case other =>
              Left(SchemaError.message(s"Expected Sequence, got ${other.valueType}", fullPath))
          }

        case Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val results =
                entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (Right(acc), (k, v)) =>
                    modifyAtPath(nodes, idx + 1, k, fullPath)(f).map(newK => acc :+ (newK -> v))
                  case (left, _) => left
                }
              results.map(DynamicValue.Map(_))
            case other =>
              Left(SchemaError.message(s"Expected Map, got ${other.valueType}", fullPath))
          }

        case Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val results =
                entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (Right(acc), (k, v)) =>
                    modifyAtPath(nodes, idx + 1, v, fullPath)(f).map(newV => acc :+ (k -> newV))
                  case (left, _) => left
                }
              results.map(DynamicValue.Map(_))
            case other =>
              Left(SchemaError.message(s"Expected Map, got ${other.valueType}", fullPath))
          }

        case Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length) {
                Left(SchemaError.message(s"Index $index out of bounds", fullPath))
              } else {
                modifyAtPath(nodes, idx + 1, elements(index), fullPath)(f).map { newElem =>
                  DynamicValue.Sequence(elements.updated(index, newElem))
                }
              }
            case other =>
              Left(SchemaError.message(s"Expected Sequence, got ${other.valueType}", fullPath))
          }

        case Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val entryIdx = entries.indexWhere(_._1 == key)
              if (entryIdx < 0) {
                Left(SchemaError.message(s"Key not found in map", fullPath))
              } else {
                val (k, v) = entries(entryIdx)
                modifyAtPath(nodes, idx + 1, v, fullPath)(f).map { newV =>
                  DynamicValue.Map(entries.updated(entryIdx, (k, newV)))
                }
              }
            case other =>
              Left(SchemaError.message(s"Expected Map, got ${other.valueType}", fullPath))
          }

        case Node.Wrapped =>
          modifyAtPath(nodes, idx + 1, value, fullPath)(f)

        case other =>
          Left(SchemaError.message(s"Unsupported path node: $other", fullPath))
      }
    }
  }
}
