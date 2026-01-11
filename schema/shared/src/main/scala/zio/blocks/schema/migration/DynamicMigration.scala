package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, SchemaError}

/**
 * A pure, serializable migration that operates on `DynamicValue`.
 * This is the untyped core of the migration system, containing a sequence
 * of `MigrationAction`s that are applied in order.
 *
 * `DynamicMigration` is fully serializable because:
 * - It contains no closures or functions
 * - All transformations are represented as data (`DynamicTransform`)
 * - All paths are represented as data (`DynamicOptic`)
 *
 * This enables migrations to be:
 * - Stored in registries
 * - Transmitted over the network
 * - Applied dynamically
 * - Inspected and transformed
 * - Used to generate SQL DDL, upgraders, etc.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a `DynamicValue`.
   *
   * @param value The input value to migrate
   * @return Either a `SchemaError` or the migrated value
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicMigration.execute(actions, value)

  /**
   * Compose this migration with another, applying this migration first,
   * then the other.
   *
   * @param that The migration to apply after this one
   * @return A new migration that applies both in sequence
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /**
   * Alias for `++`.
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the structural reverse of this migration.
   * The reverse migration has all actions reversed and in reverse order.
   *
   * Note: Runtime execution of the reverse migration is best-effort.
   * It may fail if information was lost during the forward migration
   * (e.g., dropping a field without capturing its value).
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
   * Execute a sequence of migration actions on a value.
   * Actions are applied in order, with the output of each action
   * becoming the input to the next.
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
 * Internal executor for migration actions.
 * This will be expanded in Phase 2 with full execution logic.
 */
private[migration] object ActionExecutor {
  import MigrationAction._

  def execute(action: MigrationAction, value: DynamicValue): Either[SchemaError, DynamicValue] =
    action match {
      case AddField(at, fieldName, default) =>
        executeAddField(at, fieldName, default, value)

      case DropField(at, fieldName, _) =>
        executeDropField(at, fieldName, value)

      case Rename(at, to) =>
        executeRename(at, to, value)

      case TransformValue(at, transform, _) =>
        executeTransformValue(at, transform, value)

      case Mandate(at, default) =>
        executeMandate(at, default, value)

      case Optionalize(at) =>
        executeOptionalize(at, value)

      case Join(at, sourcePaths, combiner, _) =>
        executeJoin(at, sourcePaths, combiner, value)

      case Split(at, targetPaths, splitter, _) =>
        executeSplit(at, targetPaths, splitter, value)

      case ChangeType(at, converter, _) =>
        executeChangeType(at, converter, value)

      case RenameCase(at, from, to) =>
        executeRenameCase(at, from, to, value)

      case TransformCase(at, caseName, actions) =>
        executeTransformCase(at, caseName, actions, value)

      case TransformElements(at, transform, _) =>
        executeTransformElements(at, transform, value)

      case TransformKeys(at, transform, _) =>
        executeTransformKeys(at, transform, value)

      case TransformValues(at, transform, _) =>
        executeTransformValues(at, transform, value)
    }

  // ==================== Record Action Execution ====================

  private def executeAddField(
    at: zio.blocks.schema.DynamicOptic,
    fieldName: String,
    default: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Record(fields) =>
        if (fields.exists(_._1 == fieldName)) {
          Left(SchemaError.fieldAlreadyExists(at, fieldName))
        } else {
          Right(DynamicValue.Record(fields :+ (fieldName -> default)))
        }
      case other =>
        Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
    }

  private def executeDropField(
    at: zio.blocks.schema.DynamicOptic,
    fieldName: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
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

  private def executeRename(
    at: zio.blocks.schema.DynamicOptic,
    to: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // Extract from name and parent path
    val from = at.nodes.lastOption match {
      case Some(zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
      case _ => return Left(SchemaError.transformFailed(at, "Rename path must end with a Field node"))
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

  private def executeTransformValue(
    at: zio.blocks.schema.DynamicOptic,
    transform: DynamicTransform,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { v =>
      applyTransform(transform, v, at)
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

  private def executeJoin(
    at: zio.blocks.schema.DynamicOptic,
    sourcePaths: Vector[zio.blocks.schema.DynamicOptic],
    combiner: DynamicTransform,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // Collect values from all source paths
    val collectedValues = sourcePaths.foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) {
      case (Right(acc), path) =>
        getAt(path, value) match {
          case Some(v) => Right(acc :+ v)
          case None    => Left(SchemaError.pathNotFound(path))
        }
      case (left, _) => left
    }

    collectedValues.flatMap { values =>
      // Create a record containing the collected values for the combiner
      val inputRecord = DynamicValue.Record(
        values.zipWithIndex.map { case (v, i) => (s"_$i", v) }
      )

      // Apply the combiner transform
      applyTransform(combiner, inputRecord, at).flatMap { combinedValue =>
        // Now we need to:
        // 1. Remove the source fields from the record
        // 2. Add the combined value at the target location
        modifyAt(at, value) { targetRecord =>
          targetRecord match {
            case DynamicValue.Record(fields) =>
              // Get field names from source paths (assuming they're direct field accesses)
              val sourceFieldNames = sourcePaths.flatMap(_.nodes.lastOption).collect {
                case zio.blocks.schema.DynamicOptic.Node.Field(name) => name
              }.toSet

              // Remove source fields and add the combined value
              val filteredFields = fields.filterNot { case (name, _) => sourceFieldNames.contains(name) }

              // The combined value should be added - we need to extract target field name from 'at'
              at.nodes.lastOption match {
                case Some(zio.blocks.schema.DynamicOptic.Node.Field(targetName)) =>
                  Right(DynamicValue.Record(filteredFields :+ (targetName -> combinedValue)))
                case _ =>
                  // If 'at' is root, the combined value replaces the entire record
                  Right(combinedValue)
              }
            case other =>
              Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
          }
        }
      }
    }
  }

  private def executeSplit(
    at: zio.blocks.schema.DynamicOptic,
    targetPaths: Vector[zio.blocks.schema.DynamicOptic],
    splitter: DynamicTransform,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // Get the value to split
    getAt(at, value) match {
      case None => Left(SchemaError.pathNotFound(at))
      case Some(sourceValue) =>
        // Apply the splitter transform - should produce a record with fields _0, _1, etc.
        applyTransform(splitter, sourceValue, at).flatMap {
          case DynamicValue.Record(splitFields) =>
            if (splitFields.length != targetPaths.length) {
              Left(SchemaError.transformFailed(at,
                s"Splitter produced ${splitFields.length} values, but ${targetPaths.length} target paths specified"))
            } else {
              // Get the parent record and modify it
              val parentPath = zio.blocks.schema.DynamicOptic(at.nodes.dropRight(1))

              modifyAt(parentPath, value) {
                case DynamicValue.Record(fields) =>
                  // Remove the source field
                  val sourceFieldName = at.nodes.lastOption.collect {
                    case zio.blocks.schema.DynamicOptic.Node.Field(name) => name
                  }

                  val filteredFields = sourceFieldName match {
                    case Some(name) => fields.filterNot(_._1 == name)
                    case None       => fields
                  }

                  // Add the split values at their target locations
                  val newFields = targetPaths.zip(splitFields).foldLeft[Either[SchemaError, Vector[(String, DynamicValue)]]](
                    Right(filteredFields)
                  ) {
                    case (Right(acc), (targetPath, (_, splitValue))) =>
                      targetPath.nodes.lastOption match {
                        case Some(zio.blocks.schema.DynamicOptic.Node.Field(targetName)) =>
                          Right(acc :+ (targetName -> splitValue))
                        case _ =>
                          Left(SchemaError.transformFailed(targetPath, "Target path must end with a field name"))
                      }
                    case (left, _) => left
                  }

                  newFields.map(DynamicValue.Record(_))

                case other =>
                  Left(SchemaError.typeMismatch(parentPath, "Record", other.getClass.getSimpleName))
              }
            }
          case other =>
            Left(SchemaError.typeMismatch(at, "Record (from splitter)", other.getClass.getSimpleName))
        }
    }
  }

  /**
   * Get a value at a path (read-only navigation).
   */
  private def getAt(
    path: zio.blocks.schema.DynamicOptic,
    value: DynamicValue
  ): Option[DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) {
      Some(value)
    } else {
      getAtPath(nodes, 0, value)
    }
  }

  private def getAtPath(
    nodes: IndexedSeq[zio.blocks.schema.DynamicOptic.Node],
    idx: Int,
    value: DynamicValue
  ): Option[DynamicValue] = {
    import zio.blocks.schema.DynamicOptic.Node

    if (idx >= nodes.length) {
      Some(value)
    } else {
      nodes(idx) match {
        case Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              fields.find(_._1 == name).flatMap { case (_, v) =>
                getAtPath(nodes, idx + 1, v)
              }
            case _ => None
          }

        case Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, inner) if name == caseName =>
              getAtPath(nodes, idx + 1, inner)
            case _ => None
          }

        case Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) if index >= 0 && index < elements.length =>
              getAtPath(nodes, idx + 1, elements(index))
            case _ => None
          }

        case Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val keyValue = key.asInstanceOf[DynamicValue]
              entries.find(_._1 == keyValue).flatMap { case (_, v) =>
                getAtPath(nodes, idx + 1, v)
              }
            case _ => None
          }

        case Node.Wrapped =>
          getAtPath(nodes, idx + 1, value)

        case _ => None // Elements, MapKeys, MapValues return multiple values - not supported for getAt
      }
    }
  }

  private def executeChangeType(
    at: zio.blocks.schema.DynamicOptic,
    converter: DynamicTransform,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { v =>
      applyTransform(converter, v, at)
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
      case v @ DynamicValue.Variant(caseName, _) =>
        // Different case, no change needed
        Right(v)
      case other =>
        Left(SchemaError.typeMismatch(at, "Variant", other.getClass.getSimpleName))
    }

  private def executeTransformCase(
    at: zio.blocks.schema.DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Variant(name, inner) if name == caseName =>
        DynamicMigration.execute(actions, inner).map(DynamicValue.Variant(name, _))
      case v @ DynamicValue.Variant(_, _) =>
        // Different case, no change needed
        Right(v)
      case other =>
        Left(SchemaError.typeMismatch(at, "Variant", other.getClass.getSimpleName))
    }

  // ==================== Collection Action Execution ====================

  private def executeTransformElements(
    at: zio.blocks.schema.DynamicOptic,
    transform: DynamicTransform,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Sequence(elements) =>
        val transformed = elements.foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) {
          case (Right(acc), elem) =>
            applyTransform(transform, elem, at).map(acc :+ _)
          case (left, _) =>
            left
        }
        transformed.map(DynamicValue.Sequence(_))
      case other =>
        Left(SchemaError.typeMismatch(at, "Sequence", other.getClass.getSimpleName))
    }

  private def executeTransformKeys(
    at: zio.blocks.schema.DynamicOptic,
    transform: DynamicTransform,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Map(entries) =>
        val transformed = entries.foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
          case (Right(acc), (k, v)) =>
            applyTransform(transform, k, at).map(newK => acc :+ (newK -> v))
          case (left, _) =>
            left
        }
        transformed.map(DynamicValue.Map(_))
      case other =>
        Left(SchemaError.typeMismatch(at, "Map", other.getClass.getSimpleName))
    }

  private def executeTransformValues(
    at: zio.blocks.schema.DynamicOptic,
    transform: DynamicTransform,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Map(entries) =>
        val transformed = entries.foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
          case (Right(acc), (k, v)) =>
            applyTransform(transform, v, at).map(newV => acc :+ (k -> newV))
          case (left, _) =>
            left
        }
        transformed.map(DynamicValue.Map(_))
      case other =>
        Left(SchemaError.typeMismatch(at, "Map", other.getClass.getSimpleName))
    }

  // ==================== Helper Methods ====================

  /**
   * Navigate to a path and apply a modification function.
   * If the path is empty (root), apply directly to the value.
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
              val results = elements.foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) {
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
              val results = entries.foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
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
              val results = entries.foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
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

  /**
   * Apply a DynamicTransform to a DynamicValue.
   */
  private def applyTransform(
    transform: DynamicTransform,
    value: DynamicValue,
    path: zio.blocks.schema.DynamicOptic
  ): Either[SchemaError, DynamicValue] = {
    import zio.blocks.schema.PrimitiveValue

    transform match {
      case DynamicTransform.Identity =>
        Right(value)

      case DynamicTransform.Literal(lit) =>
        Right(lit)

      case DynamicTransform.DefaultValue =>
        // This should be resolved at build time with the actual default
        Left(SchemaError.missingDefault(path, "DefaultValue not resolved"))

      case DynamicTransform.ToString =>
        value match {
          case DynamicValue.Primitive(pv) =>
            val str = pv match {
              case PrimitiveValue.String(s)  => s
              case PrimitiveValue.Int(i)     => i.toString
              case PrimitiveValue.Long(l)    => l.toString
              case PrimitiveValue.Double(d)  => d.toString
              case PrimitiveValue.Float(f)   => f.toString
              case PrimitiveValue.Boolean(b) => b.toString
              case PrimitiveValue.Byte(b)    => b.toString
              case PrimitiveValue.Short(s)   => s.toString
              case PrimitiveValue.Char(c)    => c.toString
              case PrimitiveValue.Unit       => "()"
              case other                     => other.toString
            }
            Right(DynamicValue.Primitive(PrimitiveValue.String(str)))
          case _ =>
            Left(SchemaError.typeMismatch(path, "Primitive", value.getClass.getSimpleName))
        }

      case DynamicTransform.ParseInt =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            try {
              Right(DynamicValue.Primitive(PrimitiveValue.Int(s.toInt)))
            } catch {
              case _: NumberFormatException =>
                Left(SchemaError.transformFailed(path, s"Cannot parse '$s' as Int"))
            }
          case _ =>
            Left(SchemaError.typeMismatch(path, "String", value.getClass.getSimpleName))
        }

      case DynamicTransform.ParseLong =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            try {
              Right(DynamicValue.Primitive(PrimitiveValue.Long(s.toLong)))
            } catch {
              case _: NumberFormatException =>
                Left(SchemaError.transformFailed(path, s"Cannot parse '$s' as Long"))
            }
          case _ =>
            Left(SchemaError.typeMismatch(path, "String", value.getClass.getSimpleName))
        }

      case DynamicTransform.ParseDouble =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            try {
              Right(DynamicValue.Primitive(PrimitiveValue.Double(s.toDouble)))
            } catch {
              case _: NumberFormatException =>
                Left(SchemaError.transformFailed(path, s"Cannot parse '$s' as Double"))
            }
          case _ =>
            Left(SchemaError.typeMismatch(path, "String", value.getClass.getSimpleName))
        }

      case DynamicTransform.ParseBoolean =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            s.toLowerCase match {
              case "true"  => Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
              case "false" => Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
              case _       => Left(SchemaError.transformFailed(path, s"Cannot parse '$s' as Boolean"))
            }
          case _ =>
            Left(SchemaError.typeMismatch(path, "String", value.getClass.getSimpleName))
        }

      case DynamicTransform.IntToLong =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.Long(i.toLong)))
          case _ =>
            Left(SchemaError.typeMismatch(path, "Int", value.getClass.getSimpleName))
        }

      case DynamicTransform.IntToDouble =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.Double(i.toDouble)))
          case _ =>
            Left(SchemaError.typeMismatch(path, "Int", value.getClass.getSimpleName))
        }

      case DynamicTransform.LongToInt =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.Long(l)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.Int(l.toInt)))
          case _ =>
            Left(SchemaError.typeMismatch(path, "Long", value.getClass.getSimpleName))
        }

      case DynamicTransform.LongToDouble =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.Long(l)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.Double(l.toDouble)))
          case _ =>
            Left(SchemaError.typeMismatch(path, "Long", value.getClass.getSimpleName))
        }

      case DynamicTransform.DoubleToInt =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.Double(d)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.Int(d.toInt)))
          case _ =>
            Left(SchemaError.typeMismatch(path, "Double", value.getClass.getSimpleName))
        }

      case DynamicTransform.DoubleToLong =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.Double(d)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.Long(d.toLong)))
          case _ =>
            Left(SchemaError.typeMismatch(path, "Double", value.getClass.getSimpleName))
        }

      case DynamicTransform.ConcatFields(separator, fieldNames) =>
        value match {
          case DynamicValue.Record(fields) =>
            val values = fieldNames.flatMap { name =>
              fields.find(_._1 == name).map(_._2).flatMap {
                case DynamicValue.Primitive(PrimitiveValue.String(s)) => Some(s)
                case DynamicValue.Primitive(pv)                       => Some(pv.toString)
                case _                                                => None
              }
            }
            if (values.length == fieldNames.length) {
              Right(DynamicValue.Primitive(PrimitiveValue.String(values.mkString(separator))))
            } else {
              Left(SchemaError.transformFailed(path, "Some fields not found or not convertible to String"))
            }
          case _ =>
            Left(SchemaError.typeMismatch(path, "Record", value.getClass.getSimpleName))
        }

      case DynamicTransform.SplitToFields(separator, fieldNames) =>
        value match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            val parts = s.split(separator, fieldNames.length)
            if (parts.length == fieldNames.length) {
              val fields = fieldNames.zip(parts).map { case (name, part) =>
                name -> DynamicValue.Primitive(PrimitiveValue.String(part))
              }
              Right(DynamicValue.Record(fields))
            } else {
              Left(SchemaError.transformFailed(path, s"Expected ${fieldNames.length} parts, got ${parts.length}"))
            }
          case _ =>
            Left(SchemaError.typeMismatch(path, "String", value.getClass.getSimpleName))
        }

      case DynamicTransform.Compose(first, second) =>
        applyTransform(first, value, path).flatMap(v => applyTransform(second, v, path))
    }
  }
}

