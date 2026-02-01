package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * Interpreter that executes DynamicMigration actions on DynamicValue instances.
 *
 * The interpreter processes each action sequentially, applying Resolved
 * expressions to transform values. All expression evaluation happens through
 * the Resolved.evalDynamic method.
 */
object DynamicMigrationInterpreter {

  /**
   * Apply a migration to a DynamicValue.
   */
  def apply(migration: DynamicMigration, value: DynamicValue): Either[MigrationError, DynamicValue] =
    executeActions(migration.actions, value, Chunk.empty)

  /**
   * Run a migration on a DynamicValue, returning Either[String, DynamicValue].
   *
   * This is the primary entry point for DynamicMigration.apply(value).
   */
  def run(migration: DynamicMigration, value: DynamicValue): Either[String, DynamicValue] =
    executeActions(migration.actions, value, Chunk.empty).left.map(_.render)

  /**
   * Execute a sequence of actions, threading the value through each.
   */
  private def executeActions(
    actions: Chunk[MigrationAction],
    value: DynamicValue,
    scopeStack: Chunk[DynamicOptic]
  ): Either[MigrationError, DynamicValue] = {
    var current = value
    var idx     = 0
    val len     = actions.size
    while (idx < len) {
      applyAction(actions(idx), current, scopeStack) match {
        case Right(next) => current = next
        case Left(error) => return Left(error)
      }
      idx += 1
    }
    Right(current)
  }

  /**
   * Execute a single migration action.
   *
   * This is public so that MigrationAction.execute() can delegate to it.
   */
  def applyAction(
    action: MigrationAction,
    value: DynamicValue,
    scopeStack: Chunk[DynamicOptic] = Chunk.empty
  ): Either[MigrationError, DynamicValue] = {
    val fullPath = composePath(scopeStack, action.at)

    action match {
      case MigrationAction.AddField(at, fieldName, default) =>
        modifyRecord(value, at, fullPath) { fields =>
          // Evaluate default with the record as context
          val recordValue = DynamicValue.Record(fields: _*)
          default.evalDynamic(recordValue) match {
            case Right(defaultValue) =>
              Right(fields :+ (fieldName -> defaultValue))
            case Left(err) =>
              Left(MigrationError.TransformFailed(fullPath, err))
          }
        }

      case MigrationAction.DropField(at, fieldName, _) =>
        modifyRecord(value, at, fullPath) { fields =>
          Right(fields.filter(_._1 != fieldName))
        }

      case MigrationAction.Rename(at, from, to) =>
        modifyRecord(value, at, fullPath) { fields =>
          Right(fields.map {
            case (name, v) if name == from => (to, v)
            case other                     => other
          })
        }

      case MigrationAction.TransformValue(at, fieldName, transform, _) =>
        val fieldPath = at.field(fieldName)
        modifyAtPath(value, fieldPath, fullPath.field(fieldName)) { fieldValue =>
          transform.evalDynamic(fieldValue) match {
            case Right(result) => Right(result)
            case Left(err)     => Left(MigrationError.TransformFailed(fullPath.field(fieldName), err))
          }
        }

      case MigrationAction.Mandate(at, fieldName, default) =>
        modifyRecord(value, at, fullPath) { fields =>
          val recordValue = DynamicValue.Record(fields: _*)
          val fieldIdx    = fields.indexWhere(_._1 == fieldName)
          if (fieldIdx < 0) {
            Right(fields)
          } else {
            val (name, fieldValue) = fields(fieldIdx)
            unwrapOption(fieldValue, default, recordValue) match {
              case Right(newValue) => Right(fields.updated(fieldIdx, (name, newValue)))
              case Left(err)       => Left(MigrationError.TransformFailed(fullPath.field(fieldName), err))
            }
          }
        }

      case MigrationAction.Optionalize(at, fieldName) =>
        val fieldPath = at.field(fieldName)
        modifyAtPath(value, fieldPath, fullPath.field(fieldName)) { fieldValue =>
          val someRecord = DynamicValue.Record(("value", fieldValue))
          Right(DynamicValue.Variant("Some", someRecord))
        }

      case MigrationAction.ChangeType(at, fieldName, converter, _) =>
        val fieldPath = at.field(fieldName)
        modifyAtPath(value, fieldPath, fullPath.field(fieldName)) { fieldValue =>
          converter.evalDynamic(fieldValue) match {
            case Right(result) => Right(result)
            case Left(err)     => Left(MigrationError.TransformFailed(fullPath.field(fieldName), err))
          }
        }

      case MigrationAction.RenameCase(at, from, to) =>
        modifyAtPath(value, at, fullPath) {
          case DynamicValue.Variant(caseName, caseValue) if caseName == from =>
            Right(DynamicValue.Variant(to, caseValue))
          case other =>
            Right(other)
        }

      case MigrationAction.TransformCase(at, caseName, caseActions) =>
        modifyAtPath(value, at, fullPath) {
          case DynamicValue.Variant(name, caseValue) if name == caseName =>
            executeActions(caseActions, caseValue, scopeStack :+ at).map(DynamicValue.Variant(name, _))
          case other =>
            Right(other)
        }

      case MigrationAction.TransformElements(at, elementTransform, _) =>
        modifyAtPath(value, at, fullPath) {
          case DynamicValue.Sequence(elements) =>
            transformAll(elements.toVector, elementTransform, fullPath).map(v => DynamicValue.Sequence(v: _*))
          case other =>
            Left(MigrationError.TypeMismatch(fullPath, "Sequence", other.valueType.toString))
        }

      case MigrationAction.TransformKeys(at, keyTransform, _) =>
        modifyAtPath(value, at, fullPath) {
          case DynamicValue.Record(fields) =>
            transformRecordKeys(fields.toVector, keyTransform, fullPath).map(v => DynamicValue.Record(v: _*))
          case DynamicValue.Map(entries) =>
            transformMapKeys(entries.toVector, keyTransform, fullPath).map(v => DynamicValue.Map(v: _*))
          case other =>
            Left(MigrationError.TypeMismatch(fullPath, "Map or Record", other.valueType.toString))
        }

      case MigrationAction.TransformValues(at, valueTransform, _) =>
        modifyAtPath(value, at, fullPath) {
          case DynamicValue.Record(fields) =>
            transformRecordValues(fields.toVector, valueTransform, fullPath).map(v => DynamicValue.Record(v: _*))
          case DynamicValue.Map(entries) =>
            transformMapValues(entries.toVector, valueTransform, fullPath).map(v => DynamicValue.Map(v: _*))
          case other =>
            Left(MigrationError.TypeMismatch(fullPath, "Map or Record", other.valueType.toString))
        }

      case MigrationAction.Join(at, targetFieldName, sourcePaths, combiner, _) =>
        // Join: gather values from source paths and combine into target field
        modifyRecord(value, at, fullPath) { fields =>
          val recordValue = DynamicValue.Record(fields: _*)
          // Get values using DynamicValue.get(optic) for each source path (relative to at)
          val sourceValues: Chunk[DynamicValue] = sourcePaths.flatMap { optic =>
            recordValue.get(optic).toChunk.headOption
          }
          if (sourceValues.size != sourcePaths.size) {
            val pathStrs = sourcePaths.map(_.toScalaString).toSet
            Left(MigrationError.PathNotFound(fullPath, pathStrs))
          } else {
            // Build context for combiner - extract leaf field names as keys
            val contextFields: Chunk[(String, DynamicValue)] =
              sourcePaths.zip(sourceValues).map { case (o, v: DynamicValue) =>
                val keyName = o.nodes.lastOption match {
                  case Some(DynamicOptic.Node.Field(name)) => name
                  case _                                   => o.toScalaString
                }
                (keyName, v)
              }
            val contextRecord = DynamicValue.Record(contextFields: _*)
            combiner.evalDynamic(contextRecord) match {
              case Right(combined) =>
                // Remove the exact source paths using DynamicOptic delete
                val withDeleted = sourcePaths.foldLeft[DynamicValue](recordValue) { (rec, optic) =>
                  rec.delete(optic)
                }
                // Extract fields from the modified record and add target field
                withDeleted match {
                  case rec: DynamicValue.Record =>
                    Right(rec.fields :+ (targetFieldName -> combined))
                  case _ =>
                    // If somehow not a record after delete, fallback to original approach
                    Right(fields :+ (targetFieldName -> combined))
                }
              case Left(err) =>
                Left(MigrationError.TransformFailed(fullPath, err))
            }
          }
        }

      case MigrationAction.Split(at, sourceFieldName, targetPaths, splitter, _) =>
        // Split: remove source field and distribute to target paths using pure DynamicOptic semantics
        modifyRecord(value, at, fullPath) { fields =>
          fields.find(_._1 == sourceFieldName) match {
            case Some((_, sourceValue)) =>
              splitter.evalDynamic(sourceValue) match {
                case Right(splitResult) =>
                  // Remove source field and start with a clean record
                  val filteredFields             = fields.filter(_._1 != sourceFieldName)
                  var resultRecord: DynamicValue = DynamicValue.Record(filteredFields: _*)

                  // For each target path, get value from splitResult using DynamicOptic.get()
                  // Pure DynamicOptic semantics: no string-based fallbacks
                  var idx      = 0
                  var errorOpt = Option.empty[MigrationError]
                  while (idx < targetPaths.size && errorOpt.isEmpty) {
                    val optic = targetPaths(idx)
                    // Use DynamicOptic.get() to extract value from split result
                    splitResult.get(optic).toChunk.headOption match {
                      case Some(v) =>
                        // Use DynamicOptic.insert() for potentially nested target paths
                        resultRecord = resultRecord.insert(optic, v)
                      case None =>
                        // No fallback - pure DynamicOptic semantics
                        errorOpt = Some(MigrationError.PathNotFound(fullPath(optic), Set.empty))
                    }
                    idx += 1
                  }

                  errorOpt match {
                    case Some(err) => Left(err)
                    case None      =>
                      // Extract the final fields from the result record
                      resultRecord match {
                        case rec: DynamicValue.Record =>
                          Right(rec.fields)
                        case _ =>
                          Right(filteredFields)
                      }
                  }
                case Left(err) =>
                  Left(MigrationError.TransformFailed(fullPath, err))
              }
            case None =>
              Left(MigrationError.PathNotFound(fullPath.field(sourceFieldName), Set.empty))
          }
        }
    }
  }

  private def composePath(scopeStack: Chunk[DynamicOptic], leafPath: DynamicOptic): DynamicOptic =
    scopeStack.foldLeft(DynamicOptic.root)((acc, next) => acc(next))(leafPath)

  private def modifyRecord(
    value: DynamicValue,
    at: DynamicOptic,
    errorPath: DynamicOptic
  )(
    f: Chunk[(String, DynamicValue)] => Either[MigrationError, Chunk[(String, DynamicValue)]]
  ): Either[MigrationError, DynamicValue] =
    if (at.nodes.isEmpty) {
      value match {
        case DynamicValue.Record(fields) => f(fields).map(fs => DynamicValue.Record(fs: _*))
        case other                       => Left(MigrationError.TypeMismatch(errorPath, "Record", other.valueType.toString))
      }
    } else {
      value.modifyOrFail(at) {
        case DynamicValue.Record(fields) =>
          f(fields).map(fs => DynamicValue.Record(fs: _*)) match {
            case Right(v)  => v
            case Left(err) => throw new RuntimeException(err.render)
          }
        case other => throw new RuntimeException(s"Expected Record, got ${other.valueType}")
      } match {
        case Right(result) => Right(result)
        case Left(_)       => Left(MigrationError.PathNotFound(errorPath, Set.empty))
      }
    }

  private def modifyAtPath(
    value: DynamicValue,
    path: DynamicOptic,
    errorPath: DynamicOptic
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] =
    if (path.nodes.isEmpty) {
      f(value)
    } else {
      value.modifyOrFail(path) { dv =>
        f(dv) match {
          case Right(v)  => v
          case Left(err) => throw new RuntimeException(err.render)
        }
      } match {
        case Right(result) => Right(result)
        case Left(_)       => Left(MigrationError.PathNotFound(errorPath, Set.empty))
      }
    }

  private def unwrapOption(
    fieldValue: DynamicValue,
    default: Resolved,
    context: DynamicValue
  ): Either[String, DynamicValue] = fieldValue match {
    case DynamicValue.Variant("Some", DynamicValue.Record(innerFields)) =>
      innerFields.find(_._1 == "value").map(kv => Right(kv._2)).getOrElse(default.evalDynamic(context))
    case DynamicValue.Variant("Some", inner) =>
      Right(inner)
    case DynamicValue.Variant("None", _) =>
      default.evalDynamic(context)
    case DynamicValue.Null =>
      default.evalDynamic(context)
    case other =>
      Right(other)
  }

  private def transformAll(
    elements: Vector[DynamicValue],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[DynamicValue]] = {
    val results = Vector.newBuilder[DynamicValue]
    var idx     = 0
    while (idx < elements.length) {
      transform.evalDynamic(elements(idx)) match {
        case Right(v) =>
          results += v
          idx += 1
        case Left(err) =>
          return Left(MigrationError.TransformFailed(path, err))
      }
    }
    Right(results.result())
  }

  private def transformRecordKeys(
    entries: Vector[(String, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(String, DynamicValue)]] = {
    val results = Vector.newBuilder[(String, DynamicValue)]
    var idx     = 0
    while (idx < entries.length) {
      val (k, v) = entries(idx)
      val keyDV  = DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String(k))
      transform.evalDynamic(keyDV) match {
        case Right(DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String(newK))) =>
          results += ((newK, v))
          idx += 1
        case Right(other) =>
          results += ((other.toString, v))
          idx += 1
        case Left(err) =>
          return Left(MigrationError.TransformFailed(path, err))
      }
    }
    Right(results.result())
  }

  private def transformMapKeys(
    entries: Vector[(DynamicValue, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = {
    val results = Vector.newBuilder[(DynamicValue, DynamicValue)]
    var idx     = 0
    while (idx < entries.length) {
      val (k, v) = entries(idx)
      transform.evalDynamic(k) match {
        case Right(newK) =>
          results += ((newK, v))
          idx += 1
        case Left(err) =>
          return Left(MigrationError.TransformFailed(path, err))
      }
    }
    Right(results.result())
  }

  private def transformRecordValues(
    entries: Vector[(String, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(String, DynamicValue)]] = {
    val results = Vector.newBuilder[(String, DynamicValue)]
    var idx     = 0
    while (idx < entries.length) {
      val (k, v) = entries(idx)
      transform.evalDynamic(v) match {
        case Right(newV) =>
          results += ((k, newV))
          idx += 1
        case Left(err) =>
          return Left(MigrationError.TransformFailed(path, err))
      }
    }
    Right(results.result())
  }

  private def transformMapValues(
    entries: Vector[(DynamicValue, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = {
    val results = Vector.newBuilder[(DynamicValue, DynamicValue)]
    var idx     = 0
    while (idx < entries.length) {
      val (k, v) = entries(idx)
      transform.evalDynamic(v) match {
        case Right(newV) =>
          results += ((k, newV))
          idx += 1
        case Left(err) =>
          return Left(MigrationError.TransformFailed(path, err))
      }
    }
    Right(results.result())
  }
}
