package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A pure, serializable migration that operates on [[DynamicValue]] instances.
 * Contains no user functions, closures, reflection, or code generation—only
 * data describing the structural transformation.
 *
 * `DynamicMigration` forms the untyped core of the migration system. The typed
 * [[Migration]] wrapper provides compile-time safety on top.
 *
 * Laws:
 *   - '''Identity:''' `DynamicMigration.identity.apply(v) == Right(v)`
 *   - '''Associativity:''' `(m1 ++ m2) ++ m3` behaves identically to
 *     `m1 ++ (m2 ++ m3)`
 *   - '''Structural Reverse:''' `m.reverse.reverse == m`
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Composes this migration with another, applying this migration first and
   * then `that`.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(this.actions ++ that.actions)

  /**
   * Returns the structural reverse of this migration. Each action is reversed
   * and the order is reversed.
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  /**
   * Applies this migration to a [[DynamicValue]].
   *
   * @param value
   *   the input value to transform
   * @return
   *   Right with the migrated value, or Left with a [[MigrationError]]
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    DynamicMigration.applyActions(actions, value)
}

object DynamicMigration {

  /** A migration that does nothing—the identity element for `++`. */
  val identity: DynamicMigration = new DynamicMigration(Vector.empty)

  /**
   * Creates a migration from a single action.
   */
  def fromAction(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  /**
   * Creates a migration from multiple actions.
   */
  def fromActions(actions: MigrationAction*): DynamicMigration =
    new DynamicMigration(actions.toVector)

  // ─── Internal application logic ─────────────────────────────────────

  private def applyActions(
    actions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue                     = value
    var error: Either[MigrationError, DynamicValue] = null
    val len                                       = actions.length
    var i                                         = 0
    while (i < len && error == null) {
      applyAction(actions(i), current) match {
        case Right(v)    => current = v
        case left        => error = left
      }
      i += 1
    }
    if (error != null) error else Right(current)
  }

  private[migration] def applyAction(
    action: MigrationAction,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = action match {
    case a: MigrationAction.AddField        => applyAddField(a, value)
    case a: MigrationAction.DropField       => applyDropField(a, value)
    case a: MigrationAction.Rename          => applyRename(a, value)
    case a: MigrationAction.TransformValue  => applyTransformValue(a, value)
    case a: MigrationAction.Mandate         => applyMandate(a, value)
    case a: MigrationAction.Optionalize     => applyOptionalize(a, value)
    case a: MigrationAction.ChangeType      => applyChangeType(a, value)
    case a: MigrationAction.RenameCase      => applyRenameCase(a, value)
    case a: MigrationAction.TransformCase   => applyTransformCase(a, value)
    case a: MigrationAction.Join             => applyJoin(a, value)
    case a: MigrationAction.Split            => applySplit(a, value)
    case a: MigrationAction.TransformElements => applyTransformElements(a, value)
    case a: MigrationAction.TransformKeys   => applyTransformKeys(a, value)
    case a: MigrationAction.TransformValues => applyTransformValues(a, value)
  }

  // ─── Record Operations ──────────────────────────────────────────────

  private def applyAddField(
    action: MigrationAction.AddField,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val targetNodes = action.target.nodes
    if (targetNodes.isEmpty)
      return Left(MigrationError.General(action.target, "AddField requires a non-empty path"))

    targetNodes.last match {
      case fieldNode: DynamicOptic.Node.Field =>
        val parentPath = new DynamicOptic(targetNodes.init)
        val fieldName  = fieldNode.name
        navigateToRecord(value, parentPath) match {
          case Right((record, rebuild)) =>
            // Check field doesn't already exist
            val existing = record.fields
            val alreadyExists = {
              var found = false
              var j     = 0
              while (j < existing.length && !found) {
                if (existing(j)._1 == fieldName) found = true
                j += 1
              }
              found
            }
            if (alreadyExists)
              Left(MigrationError.FieldAlreadyExists(action.target, fieldName))
            else {
              val newFields = existing.appended((fieldName, action.defaultValue))
              Right(rebuild(new DynamicValue.Record(newFields)))
            }
          case Left(err) => Left(err)
        }
      case _ =>
        Left(MigrationError.General(action.target, "AddField target must end with a field node"))
    }
  }

  private def applyDropField(
    action: MigrationAction.DropField,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val sourceNodes = action.source.nodes
    if (sourceNodes.isEmpty)
      return Left(MigrationError.General(action.source, "DropField requires a non-empty path"))

    sourceNodes.last match {
      case fieldNode: DynamicOptic.Node.Field =>
        val parentPath = new DynamicOptic(sourceNodes.init)
        val fieldName  = fieldNode.name
        navigateToRecord(value, parentPath) match {
          case Right((record, rebuild)) =>
            val existing = record.fields
            val filtered = existing.filter(_._1 != fieldName)
            if (filtered.length == existing.length)
              Left(MigrationError.MissingField(action.source, fieldName))
            else
              Right(rebuild(new DynamicValue.Record(filtered)))
          case Left(err) => Left(err)
        }
      case _ =>
        Left(MigrationError.General(action.source, "DropField source must end with a field node"))
    }
  }

  private def applyRename(
    action: MigrationAction.Rename,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    navigateToRecord(value, action.sourcePath) match {
      case Right((record, rebuild)) =>
        val fields  = record.fields
        val len     = fields.length
        val builder = Vector.newBuilder[(String, DynamicValue)]
        builder.sizeHint(len)
        var found = false
        var i     = 0
        while (i < len) {
          val (name, v) = fields(i)
          if (name == action.fromName) {
            found = true
            builder.addOne((action.toName, v))
          } else {
            builder.addOne((name, v))
          }
          i += 1
        }
        if (!found) Left(MigrationError.MissingField(action.sourcePath, action.fromName))
        else Right(rebuild(new DynamicValue.Record(Chunk.fromIterable(builder.result()))))
      case Left(err) => Left(err)
    }

  private def applyTransformValue(
    action: MigrationAction.TransformValue,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    // Get the source value
    val sourceSelection = value.get(action.source)
    sourceSelection.one match {
      case Right(sourceVal) =>
        action.transform(sourceVal) match {
          case Right(transformed) =>
            if (action.source == action.target) {
              // In-place transformation
              Right(value.set(action.target, transformed))
            } else {
              // Cross-field transformation: set the target, then drop the source if different
              val withTarget = value.set(action.target, transformed)
              Right(withTarget)
            }
          case Left(errMsg) =>
            Left(MigrationError.TransformFailed(action.source, errMsg))
        }
      case Left(_) =>
        Left(MigrationError.MissingField(action.source, action.source.toString))
    }
  }

  private def applyMandate(
    action: MigrationAction.Mandate,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val sourceSelection = value.get(action.source)
    sourceSelection.one match {
      case Right(sourceVal) =>
        val mandated = sourceVal match {
          case _: DynamicValue.Null.type =>
            action.defaultValue
          case v: DynamicValue.Variant if v.caseName.contains("None") =>
            action.defaultValue
          case v: DynamicValue.Variant if v.caseName.contains("Some") =>
            v.caseValue.getOrElse(action.defaultValue)
          case other => other
        }
        if (action.source == action.target)
          Right(value.set(action.target, mandated))
        else {
          val sourceNodes = action.source.nodes
          if (sourceNodes.nonEmpty) {
            sourceNodes.last match {
              case f: DynamicOptic.Node.Field =>
                val parentPath = new DynamicOptic(sourceNodes.init)
                navigateToRecord(value, parentPath) match {
                  case Right((record, rebuild)) =>
                    val newFields = record.fields.filter(_._1 != f.name)
                    val targetNodes = action.target.nodes
                    targetNodes.last match {
                      case tf: DynamicOptic.Node.Field =>
                        Right(rebuild(new DynamicValue.Record(newFields.appended((tf.name, mandated)))))
                      case _ => Right(value.set(action.target, mandated))
                    }
                  case Left(err) => Left(err)
                }
              case _ => Right(value.set(action.target, mandated))
            }
          } else Right(value.set(action.target, mandated))
        }
      case Left(_) =>
        Left(MigrationError.MissingField(action.source, action.source.toString))
    }
  }

  private def applyOptionalize(
    action: MigrationAction.Optionalize,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val sourceSelection = value.get(action.source)
    sourceSelection.one match {
      case Right(sourceVal) =>
        // Wrap in Some variant
        val optionalized = sourceVal match {
          case _: DynamicValue.Null.type =>
            new DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
          case other =>
            new DynamicValue.Variant("Some", other)
        }
        if (action.source == action.target)
          Right(value.set(action.target, optionalized))
        else {
          val sourceNodes = action.source.nodes
          if (sourceNodes.nonEmpty) {
            sourceNodes.last match {
              case f: DynamicOptic.Node.Field =>
                val parentPath = new DynamicOptic(sourceNodes.init)
                navigateToRecord(value, parentPath) match {
                  case Right((record, rebuild)) =>
                    val newFields = record.fields.filter(_._1 != f.name)
                    val targetNodes = action.target.nodes
                    targetNodes.last match {
                      case tf: DynamicOptic.Node.Field =>
                        Right(rebuild(new DynamicValue.Record(newFields.appended((tf.name, optionalized)))))
                      case _ => Right(value.set(action.target, optionalized))
                    }
                  case Left(err) => Left(err)
                }
              case _ => Right(value.set(action.target, optionalized))
            }
          } else Right(value.set(action.target, optionalized))
        }
      case Left(_) =>
        Left(MigrationError.MissingField(action.source, action.source.toString))
    }
  }

  private def applyChangeType(
    action: MigrationAction.ChangeType,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val sourceSelection = value.get(action.source)
    sourceSelection.one match {
      case Right(sourceVal) =>
        action.converter(sourceVal) match {
          case Right(converted) =>
            if (action.source == action.target)
              Right(value.set(action.target, converted))
            else {
              val sourceNodes = action.source.nodes
              if (sourceNodes.nonEmpty) {
                sourceNodes.last match {
                  case f: DynamicOptic.Node.Field =>
                    val parentPath = new DynamicOptic(sourceNodes.init)
                    navigateToRecord(value, parentPath) match {
                      case Right((record, rebuild)) =>
                        val newFields = record.fields.filter(_._1 != f.name)
                        val targetNodes = action.target.nodes
                        targetNodes.last match {
                          case tf: DynamicOptic.Node.Field =>
                            Right(rebuild(new DynamicValue.Record(newFields.appended((tf.name, converted)))))
                          case _ => Right(value.set(action.target, converted))
                        }
                      case Left(err) => Left(err)
                    }
                  case _ => Right(value.set(action.target, converted))
                }
              } else Right(value.set(action.target, converted))
            }
          case Left(errMsg) =>
            Left(MigrationError.TransformFailed(action.source, errMsg))
        }
      case Left(_) =>
        Left(MigrationError.MissingField(action.source, action.source.toString))
    }
  }

  // ─── Join/Split Operations ───────────────────────────────────────────

  private def applyJoin(
    action: MigrationAction.Join,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    navigateToRecord(value, action.at) match {
      case Right((record, rebuild)) =>
        if (action.sourceFields.isEmpty)
          Left(MigrationError.General(action.at, "Join requires at least one source field"))
        else {
          // The combiner receives the parent record to access source fields
          action.combiner(record) match {
            case Right(combined) =>
              // Remove source fields, add target field with combined value
              val removeSet = action.sourceFields.toSet
              val fields    = record.fields
              val len       = fields.length
              val builder   = Vector.newBuilder[(String, DynamicValue)]
              builder.sizeHint(len - removeSet.size + 1)
              var i = 0
              while (i < len) {
                val (name, v) = fields(i)
                if (!removeSet.contains(name)) builder.addOne((name, v))
                i += 1
              }
              builder.addOne((action.targetField, combined))
              Right(rebuild(new DynamicValue.Record(Chunk.fromIterable(builder.result()))))
            case Left(errMsg) =>
              Left(MigrationError.TransformFailed(action.at, errMsg))
          }
        }
      case Left(err) => Left(err)
    }

  private def applySplit(
    action: MigrationAction.Split,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    navigateToRecord(value, action.at) match {
      case Right((record, rebuild)) =>
        // Find the source field value
        val fields   = record.fields
        val fieldMap = fields.toMap
        fieldMap.get(action.sourceField) match {
          case Some(sourceVal) =>
            // The splitter receives the source field value and produces a Record
            action.splitter(sourceVal) match {
              case Right(splitResult: DynamicValue.Record) =>
                // Remove source field, add target fields from split result
                val splitFieldMap = splitResult.fields.toMap
                val len           = fields.length
                val builder       = Vector.newBuilder[(String, DynamicValue)]
                builder.sizeHint(len - 1 + action.targetFields.length)
                var i = 0
                while (i < len) {
                  val (name, v) = fields(i)
                  if (name != action.sourceField) builder.addOne((name, v))
                  i += 1
                }
                var j = 0
                while (j < action.targetFields.length) {
                  val targetName = action.targetFields(j)
                  val targetVal  = splitFieldMap.getOrElse(targetName, DynamicValue.Null)
                  builder.addOne((targetName, targetVal))
                  j += 1
                }
                Right(rebuild(new DynamicValue.Record(Chunk.fromIterable(builder.result()))))
              case Right(other) =>
                Left(MigrationError.TransformFailed(action.at, s"Split result must be a Record, got ${other.valueType}"))
              case Left(errMsg) =>
                Left(MigrationError.TransformFailed(action.at, errMsg))
            }
          case None =>
            Left(MigrationError.MissingField(action.at, action.sourceField))
        }
      case Left(err) => Left(err)
    }

  // ─── Enum Operations ────────────────────────────────────────────────

  private def applyRenameCase(
    action: MigrationAction.RenameCase,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    def renameInVariant(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case v: DynamicValue.Variant if v.caseName.contains(action.fromName) =>
        Right(new DynamicValue.Variant(action.toName, v.caseValue.getOrElse(DynamicValue.Null)))
      case v: DynamicValue.Variant =>
        // Case doesn't match; leave it unchanged
        Right(v)
      case _ =>
        Left(MigrationError.TypeMismatch(action.path, "Variant", dv.valueType.toString))
    }

    if (action.path.nodes.isEmpty) renameInVariant(value)
    else {
      val selection = value.get(action.path)
      selection.one match {
        case Right(target) =>
          renameInVariant(target) match {
            case Right(renamed) => Right(value.set(action.path, renamed))
            case Left(err)      => Left(err)
          }
        case Left(_) =>
          Left(MigrationError.MissingField(action.path, action.path.toString))
      }
    }
  }

  private def applyTransformCase(
    action: MigrationAction.TransformCase,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    def transformInVariant(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case v: DynamicValue.Variant if v.caseName.contains(action.caseName) =>
        val caseVal = v.caseValue.getOrElse(DynamicValue.Null)
        applyActions(action.actions, caseVal) match {
          case Right(transformed) =>
            Right(new DynamicValue.Variant(action.targetCaseName, transformed))
          case Left(err) => Left(err)
        }
      case v: DynamicValue.Variant =>
        Right(v) // Not the target case, leave unchanged
      case _ =>
        Left(MigrationError.TypeMismatch(action.path, "Variant", dv.valueType.toString))
    }

    if (action.path.nodes.isEmpty) transformInVariant(value)
    else {
      val selection = value.get(action.path)
      selection.one match {
        case Right(target) =>
          transformInVariant(target) match {
            case Right(transformed) => Right(value.set(action.path, transformed))
            case Left(err)          => Left(err)
          }
        case Left(_) =>
          Left(MigrationError.MissingField(action.path, action.path.toString))
      }
    }
  }

  // ─── Collection/Map Operations ──────────────────────────────────────

  private def applyTransformElements(
    action: MigrationAction.TransformElements,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    def transformSeq(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case s: DynamicValue.Sequence =>
        val elems   = s.elements
        val len     = elems.length
        val builder = Vector.newBuilder[DynamicValue]
        builder.sizeHint(len)
        var i = 0
        while (i < len) {
          action.transform(elems(i)) match {
            case Right(v)  => builder.addOne(v)
            case Left(msg) =>
              return Left(MigrationError.TransformFailed(
                action.path.at(i),
                msg
              ))
          }
          i += 1
        }
        Right(new DynamicValue.Sequence(Chunk.fromIterable(builder.result())))
      case _ =>
        Left(MigrationError.TypeMismatch(action.path, "Sequence", dv.valueType.toString))
    }

    if (action.path.nodes.isEmpty) transformSeq(value)
    else {
      val selection = value.get(action.path)
      selection.one match {
        case Right(target) =>
          transformSeq(target) match {
            case Right(transformed) => Right(value.set(action.path, transformed))
            case Left(err)          => Left(err)
          }
        case Left(_) =>
          Left(MigrationError.MissingField(action.path, action.path.toString))
      }
    }
  }

  private def applyTransformKeys(
    action: MigrationAction.TransformKeys,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    def transformMap(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case m: DynamicValue.Map =>
        val entries = m.entries
        val len     = entries.length
        val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
        builder.sizeHint(len)
        var i = 0
        while (i < len) {
          val (k, v) = entries(i)
          action.transform(k) match {
            case Right(newKey) => builder.addOne((newKey, v))
            case Left(msg)     =>
              return Left(MigrationError.TransformFailed(action.path, s"key transform: $msg"))
          }
          i += 1
        }
        Right(new DynamicValue.Map(Chunk.fromIterable(builder.result())))
      case _ =>
        Left(MigrationError.TypeMismatch(action.path, "Map", dv.valueType.toString))
    }

    if (action.path.nodes.isEmpty) transformMap(value)
    else {
      val selection = value.get(action.path)
      selection.one match {
        case Right(target) =>
          transformMap(target) match {
            case Right(transformed) => Right(value.set(action.path, transformed))
            case Left(err)          => Left(err)
          }
        case Left(_) =>
          Left(MigrationError.MissingField(action.path, action.path.toString))
      }
    }
  }

  private def applyTransformValues(
    action: MigrationAction.TransformValues,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    def transformMap(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case m: DynamicValue.Map =>
        val entries = m.entries
        val len     = entries.length
        val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
        builder.sizeHint(len)
        var i = 0
        while (i < len) {
          val (k, v) = entries(i)
          action.transform(v) match {
            case Right(newVal) => builder.addOne((k, newVal))
            case Left(msg)     =>
              return Left(MigrationError.TransformFailed(action.path, s"value transform: $msg"))
          }
          i += 1
        }
        Right(new DynamicValue.Map(Chunk.fromIterable(builder.result())))
      case _ =>
        Left(MigrationError.TypeMismatch(action.path, "Map", dv.valueType.toString))
    }

    if (action.path.nodes.isEmpty) transformMap(value)
    else {
      val selection = value.get(action.path)
      selection.one match {
        case Right(target) =>
          transformMap(target) match {
            case Right(transformed) => Right(value.set(action.path, transformed))
            case Left(err)          => Left(err)
          }
        case Left(_) =>
          Left(MigrationError.MissingField(action.path, action.path.toString))
      }
    }
  }

  // ─── Helpers ────────────────────────────────────────────────────────

  /**
   * Navigates to a Record at the given path, returning the record and a
   * function to rebuild the original value with a replacement record.
   */
  private def navigateToRecord(
    value: DynamicValue,
    path: DynamicOptic
  ): Either[MigrationError, (DynamicValue.Record, DynamicValue.Record => DynamicValue)] =
    if (path.nodes.isEmpty) {
      value match {
        case r: DynamicValue.Record => Right((r, (rec: DynamicValue.Record) => rec))
        case _                      => Left(MigrationError.TypeMismatch(path, "Record", value.valueType.toString))
      }
    } else {
      val selection = value.get(path)
      selection.one match {
        case Right(target) =>
          target match {
            case r: DynamicValue.Record =>
              Right((r, (newRecord: DynamicValue.Record) => value.set(path, newRecord)))
            case _ =>
              Left(MigrationError.TypeMismatch(path, "Record", target.valueType.toString))
          }
        case Left(_) =>
          Left(MigrationError.General(path, s"Path not found: $path"))
      }
    }
}
