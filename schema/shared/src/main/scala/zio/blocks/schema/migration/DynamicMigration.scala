package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A reified, fully serializable description of a schema migration.
 *
 * `DynamicMigration` is pure data — it describes *how* to transform one
 * `DynamicValue` into another without any opaque functions. This enables:
 *
 *   - Inspection (e.g., DDL generation, migration visualization)
 *   - Optimization (action merging, dead-action elimination)
 *   - Serialization (storing migrations alongside data versions)
 *   - Reversibility (structural inverse for bidirectional compat)
 *
 * All actions are path-based via `DynamicOptic`, so every transformation
 * specifies exactly *where* in the data tree it operates.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Applies this migration to a `DynamicValue`, executing actions sequentially.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    migrate(value)

  /**
   * Applies this migration to a `DynamicValue`, executing actions sequentially.
   */
  def migrate(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = value
    var idx                   = 0
    val len                   = actions.length
    while (idx < len) {
      DynamicMigration.executeAction(actions(idx), current) match {
        case Right(v) => current = v
        case left     => return left
      }
      idx += 1
    }
    Right(current)
  }

  /**
   * Composes two migrations sequentially.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(this.actions ++ that.actions)

  /**
   * Structural reverse: inverts the order and reverses each action.
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {

  /**
   * The identity migration — does nothing.
   */
  val identity: DynamicMigration = new DynamicMigration(Vector.empty)

  /**
   * Creates a migration from a single action.
   */
  def apply(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  // ──────────────── Convenience Builders ────────────────

  def renameField(name: String, newName: String): DynamicMigration =
    DynamicMigration(MigrationAction.Rename(DynamicOptic.root, name, newName))

  def addField(name: String, default: DynamicValue): DynamicMigration =
    DynamicMigration(MigrationAction.AddField(DynamicOptic.root, name, default))

  def dropField(name: String): DynamicMigration =
    DynamicMigration(MigrationAction.DropField(DynamicOptic.root, name, DynamicValue.Null))

  def dropField(name: String, defaultForReverse: DynamicValue): DynamicMigration =
    DynamicMigration(MigrationAction.DropField(DynamicOptic.root, name, defaultForReverse))

  def nest(fieldNames: Vector[String], intoField: String): DynamicMigration =
    DynamicMigration(MigrationAction.Nest(DynamicOptic.root, fieldNames, intoField))

  def unnest(fieldName: String): DynamicMigration =
    DynamicMigration(MigrationAction.Unnest(DynamicOptic.root, fieldName, Vector.empty))

  def renameCase(from: String, to: String): DynamicMigration =
    DynamicMigration(MigrationAction.RenameCase(DynamicOptic.root, from, to))

  def optionalize(fieldName: String): DynamicMigration =
    DynamicMigration(MigrationAction.Optionalize(DynamicOptic.root, fieldName))

  def mandate(fieldName: String, default: DynamicValue): DynamicMigration =
    DynamicMigration(MigrationAction.Mandate(DynamicOptic.root, fieldName, default))

  def changeFieldTypeExpr(fieldName: String, expr: MigrationExpr): DynamicMigration =
    DynamicMigration(MigrationAction.ChangeTypeExpr(DynamicOptic.root, fieldName, expr))

  def joinFields(
    sourcePaths: Vector[String],
    targetField: String,
    combiner: MigrationExpr
  ): DynamicMigration =
    DynamicMigration(MigrationAction.Join(DynamicOptic.root, sourcePaths, targetField, combiner))

  def splitField(
    sourceField: String,
    targetFields: Vector[String],
    splitter: MigrationExpr
  ): DynamicMigration =
    DynamicMigration(MigrationAction.Split(DynamicOptic.root, sourceField, targetFields, splitter))

  /** Alias for [[nest]] */
  def nestFields(fieldNames: Vector[String], intoField: String): DynamicMigration =
    nest(fieldNames, intoField)

  /** Alias for [[optionalize]] */
  def optionalizeField(fieldName: String): DynamicMigration =
    optionalize(fieldName)

  /** Alias for [[mandate]] */
  def mandateField(fieldName: String, default: DynamicValue): DynamicMigration =
    mandate(fieldName, default)

  // ──────────────── Action Execution Engine ────────────────

  // format: off
  private[migration] def executeAction(
    action: MigrationAction,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    if (action.at.nodes.isEmpty) executeAtRoot(action, value)
    else executeAtPath(action, value, action.at, 0)
  // format: on

  /**
   * Execute an action at the root level (path is empty).
   */
  // format: off
  private def executeAtRoot(
    action: MigrationAction,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = action match {

    case MigrationAction.Rename(at, from, to) =>
      value match {
        case DynamicValue.Record(fields) =>
          val idx = indexOfField(fields, from)
          if (idx >= 0) {
            val (_, v) = fields(idx)
            Right(DynamicValue.Record(fields.updated(idx, (to, v))))
          } else Left(MigrationError.FieldNotFound(from, at))
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.AddField(at, fieldName, default) =>
      value match {
        case DynamicValue.Record(fields) =>
          if (fields.exists(_._1 == fieldName)) Left(MigrationError.FieldAlreadyExists(fieldName, at))
          else Right(DynamicValue.Record(fields :+ (fieldName, default)))
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.DropField(at, fieldName, _) =>
      value match {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.TransformValue(_, migration) =>
      migration.migrate(value)

    case MigrationAction.Mandate(at, fieldName, default) =>
      value match {
        case DynamicValue.Record(fields) =>
          val idx = indexOfField(fields, fieldName)
          if (idx < 0) Left(MigrationError.FieldNotFound(fieldName, at))
          else {
            val (_, v) = fields(idx)
            v match {
              case DynamicValue.Null =>
                Right(DynamicValue.Record(fields.updated(idx, (fieldName, default))))
              case _ => Right(value) // already non-null, keep as-is
            }
          }
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.Optionalize(_, _) =>
      // Optionalize is a type-level change; at runtime, the value stays the same
      Right(value)

    case MigrationAction.ChangeType(at, fieldName, converter) =>
      value match {
        case DynamicValue.Record(fields) =>
          val idx = indexOfField(fields, fieldName)
          if (idx < 0) Left(MigrationError.FieldNotFound(fieldName, at))
          else {
            val (_, v) = fields(idx)
            converter.migrate(v).map { newVal =>
              DynamicValue.Record(fields.updated(idx, (fieldName, newVal)))
            }
          }
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.ChangeTypeExpr(at, fieldName, expr) =>
      value match {
        case DynamicValue.Record(fields) =>
          val idx = indexOfField(fields, fieldName)
          if (idx < 0) Left(MigrationError.FieldNotFound(fieldName, at))
          else {
            val (_, v) = fields(idx)
            expr(v).map { newVal =>
              DynamicValue.Record(fields.updated(idx, (fieldName, newVal)))
            }
          }
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.Join(at, sourcePaths, targetField, combiner) =>
      value match {
        case DynamicValue.Record(fields) =>
          val sourceFields = fields.filter { case (k, _) => sourcePaths.contains(k) }
          val remaining    = fields.filterNot { case (k, _) => sourcePaths.contains(k) }
          val sourceRecord = DynamicValue.Record(sourceFields)
          combiner(sourceRecord).map { combined =>
            DynamicValue.Record(remaining :+ (targetField, combined))
          }
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.Split(at, sourceField, targetFields, splitter) =>
      value match {
        case DynamicValue.Record(fields) =>
          val idx = indexOfField(fields, sourceField)
          if (idx < 0) Left(MigrationError.FieldNotFound(sourceField, at))
          else {
            val (_, v)   = fields(idx)
            val remaining = fields.filterNot(_._1 == sourceField)
            splitter(v).map {
              case DynamicValue.Record(splitFields) =>
                DynamicValue.Record(remaining ++ splitFields)
              case other =>
                DynamicValue.Record(remaining :+ (targetFields.headOption.getOrElse(sourceField), other))
            }
          }
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.Nest(at, fieldNames, intoField) =>
      value match {
        case DynamicValue.Record(fields) =>
          val toNest = fields.filter { case (k, _) => fieldNames.contains(k) }
          val keep   = fields.filterNot { case (k, _) => fieldNames.contains(k) }
          Right(DynamicValue.Record(keep :+ (intoField, DynamicValue.Record(toNest))))
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.Unnest(at, fieldName, _) =>
      value match {
        case DynamicValue.Record(fields) =>
          fields.find(_._1 == fieldName) match {
            case Some((_, DynamicValue.Record(nestedFields))) =>
              val others = fields.filterNot(_._1 == fieldName)
              Right(DynamicValue.Record(others ++ nestedFields))
            case Some(_) => Left(MigrationError.TypeMismatch("Record", "non-Record field", at))
            case None    => Left(MigrationError.FieldNotFound(fieldName, at))
          }
        case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, at))
      }

    case MigrationAction.RenameCase(at, from, to) =>
      value match {
        case DynamicValue.Variant(caseName, payload) =>
          if (caseName == from) Right(DynamicValue.Variant(to, payload))
          else Right(value) // not this case, pass through
        case _ => Left(MigrationError.TypeMismatch("Variant", value.getClass.getSimpleName, at))
      }

    case MigrationAction.TransformCase(at, caseName, caseActions) =>
      value match {
        case DynamicValue.Variant(cn, payload) =>
          if (cn == caseName) {
            val caseMigration = new DynamicMigration(caseActions)
            caseMigration.migrate(payload).map(v => DynamicValue.Variant(cn, v))
          } else Right(value) // not this case, pass through
        case _ => Left(MigrationError.TypeMismatch("Variant", value.getClass.getSimpleName, at))
      }

    case MigrationAction.TransformElements(at, migration) =>
      value match {
        case DynamicValue.Sequence(elements) =>
          val builder = Chunk.newBuilder[DynamicValue]
          var idx = 0
          val len = elements.length
          while (idx < len) {
            migration.migrate(elements(idx)) match {
              case Right(v) => builder += v
              case Left(e)  => return Left(e)
            }
            idx += 1
          }
          Right(DynamicValue.Sequence(builder.result()))
        case _ => Left(MigrationError.TypeMismatch("Sequence", value.getClass.getSimpleName, at))
      }

    case MigrationAction.TransformKeys(at, migration) =>
      value match {
        case DynamicValue.Map(entries) =>
          val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
          var idx = 0
          val len = entries.length
          while (idx < len) {
            val (k, v) = entries(idx)
            migration.migrate(k) match {
              case Right(newK) => builder += ((newK, v))
              case Left(e)     => return Left(e)
            }
            idx += 1
          }
          Right(DynamicValue.Map(builder.result()))
        case _ => Left(MigrationError.TypeMismatch("Map", value.getClass.getSimpleName, at))
      }

    case MigrationAction.TransformValues(at, migration) =>
      value match {
        case DynamicValue.Map(entries) =>
          val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
          var idx = 0
          val len = entries.length
          while (idx < len) {
            val (k, v) = entries(idx)
            migration.migrate(v) match {
              case Right(newV) => builder += ((k, newV))
              case Left(e)     => return Left(e)
            }
            idx += 1
          }
          Right(DynamicValue.Map(builder.result()))
        case _ => Left(MigrationError.TypeMismatch("Map", value.getClass.getSimpleName, at))
      }
  }
  // format: on

  /**
   * Navigate into a nested structure following the DynamicOptic path, then
   * execute the action at the target location.
   */
  // format: off
  private def executeAtPath(
    action: MigrationAction,
    value: DynamicValue,
    path: DynamicOptic,
    nodeIdx: Int
  ): Either[MigrationError, DynamicValue] = {
    if (nodeIdx >= path.nodes.length) {
      // We've reached the target — execute with a root-adjusted action
      val rootAction = adjustActionToRoot(action)
      executeAtRoot(rootAction, value)
    } else {
      path.nodes(nodeIdx) match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val idx = indexOfField(fields, name)
              if (idx < 0) Left(MigrationError.FieldNotFound(name, path))
              else {
                val (_, v) = fields(idx)
                executeAtPath(action, v, path, nodeIdx + 1).map { newVal =>
                  DynamicValue.Record(fields.updated(idx, (name, newVal)))
                }
              }
            case _ => Left(MigrationError.TypeMismatch("Record", value.getClass.getSimpleName, path))
          }

        case DynamicOptic.Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(cn, payload) if cn == caseName =>
              executeAtPath(action, payload, path, nodeIdx + 1).map(v => DynamicValue.Variant(cn, v))
            case _: DynamicValue.Variant => Right(value) // not this case, pass through
            case _ => Left(MigrationError.TypeMismatch("Variant", value.getClass.getSimpleName, path))
          }

        case DynamicOptic.Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val builder = Chunk.newBuilder[DynamicValue]
              var i = 0
              val len = elements.length
              while (i < len) {
                executeAtPath(action, elements(i), path, nodeIdx + 1) match {
                  case Right(v) => builder += v
                  case Left(e) => return Left(e)
                }
                i += 1
              }
              Right(DynamicValue.Sequence(builder.result()))
            case _ => Left(MigrationError.TypeMismatch("Sequence", value.getClass.getSimpleName, path))
          }

        case DynamicOptic.Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
              var i = 0
              val len = entries.length
              while (i < len) {
                val (k, v) = entries(i)
                executeAtPath(action, k, path, nodeIdx + 1) match {
                  case Right(newK) => builder += ((newK, v))
                  case Left(e) => return Left(e)
                }
                i += 1
              }
              Right(DynamicValue.Map(builder.result()))
            case _ => Left(MigrationError.TypeMismatch("Map", value.getClass.getSimpleName, path))
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
              var i = 0
              val len = entries.length
              while (i < len) {
                val (k, v) = entries(i)
                executeAtPath(action, v, path, nodeIdx + 1) match {
                  case Right(newV) => builder += ((k, newV))
                  case Left(e) => return Left(e)
                }
                i += 1
              }
              Right(DynamicValue.Map(builder.result()))
            case _ => Left(MigrationError.TypeMismatch("Map", value.getClass.getSimpleName, path))
          }

        case _ =>
          Left(MigrationError.Custom(s"Unsupported path node at index $nodeIdx", path))
      }
    }
  }
  // format: on

  /**
   * Creates a version of the action that operates at root (empty path).
   */
  private def adjustActionToRoot(action: MigrationAction): MigrationAction = {
    val root = DynamicOptic.root
    action match {
      case a: MigrationAction.AddField          => a.copy(at = root)
      case a: MigrationAction.DropField         => a.copy(at = root)
      case a: MigrationAction.Rename            => a.copy(at = root)
      case a: MigrationAction.TransformValue    => a.copy(at = root)
      case a: MigrationAction.Mandate           => a.copy(at = root)
      case a: MigrationAction.Optionalize       => a.copy(at = root)
      case a: MigrationAction.ChangeType        => a.copy(at = root)
      case a: MigrationAction.ChangeTypeExpr    => a.copy(at = root)
      case a: MigrationAction.Nest              => a.copy(at = root)
      case a: MigrationAction.Unnest            => a.copy(at = root)
      case a: MigrationAction.Join              => a.copy(at = root)
      case a: MigrationAction.Split             => a.copy(at = root)
      case a: MigrationAction.RenameCase        => a.copy(at = root)
      case a: MigrationAction.TransformCase     => a.copy(at = root)
      case a: MigrationAction.TransformElements => a.copy(at = root)
      case a: MigrationAction.TransformKeys     => a.copy(at = root)
      case a: MigrationAction.TransformValues   => a.copy(at = root)
    }
  }

  private def indexOfField(fields: Chunk[(String, DynamicValue)], name: String): Int = {
    var i = 0
    while (i < fields.length) {
      if (fields(i)._1 == name) return i
      i += 1
    }
    -1
  }
}
