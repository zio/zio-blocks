package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * An untyped, fully serializable migration that operates on `DynamicValue`.
 *
 * A `DynamicMigration` is a sequence of `MigrationAction`s that describe how to
 * transform data from one schema version to another. Because it contains no
 * functions or closures, it can be serialized, stored in registries, and used
 * to generate DDL, upgraders, downgraders, and offline data transforms.
 *
 * Laws:
 *   - Identity: `DynamicMigration.identity.apply(v) == Right(v)`
 *   - Associativity: `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
 *   - Structural reverse: `m.reverse.reverse == m`
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a `DynamicValue`, producing a transformed value or
   * an error.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current = value
    var idx     = 0
    while (idx < actions.length) {
      DynamicMigration.applyAction(current, actions(idx)) match {
        case Right(updated) => current = updated
        case left           => return left
      }
      idx += 1
    }
    Right(current)
  }

  /**
   * Compose two migrations. The result applies this migration first, then
   * `that`.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  /**
   * Alias for `++`.
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * The structural reverse of this migration.
   *
   * Satisfies: `m.reverse.reverse == m`
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  /**
   * Whether this migration has no actions (is an identity).
   */
  def isEmpty: Boolean = actions.isEmpty

  override def toString: String =
    if (actions.isEmpty) "DynamicMigration {}"
    else {
      val sb = new StringBuilder("DynamicMigration {\n")
      actions.foreach { action =>
        sb.append("  ").append(action).append("\n")
      }
      sb.append("}")
      sb.toString
    }
}

object DynamicMigration {

  /**
   * The identity migration (no actions).
   */
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  /**
   * An empty migration (alias for `identity`).
   */
  val empty: DynamicMigration = identity

  /**
   * Create a migration with a single action.
   */
  def apply(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))

  // Apply a single MigrationAction to a DynamicValue.
  private[schema] def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.AddField(at, fieldName, default) =>
        applyAddField(value, at, fieldName, default)

      case MigrationAction.DropField(at, fieldName, _) =>
        applyDropField(value, at, fieldName)

      case MigrationAction.Rename(at, fromName, toName) =>
        applyRename(value, at, fromName, toName)

      case MigrationAction.TransformValue(at, transform, _) =>
        applyTransformValue(value, at, transform)

      case MigrationAction.Mandate(at, default) =>
        applyMandate(value, at, default)

      case MigrationAction.Optionalize(at) =>
        applyOptionalize(value, at)

      case MigrationAction.Join(at, sourcePaths, combiner, _) =>
        applyJoin(value, at, sourcePaths, combiner)

      case MigrationAction.Split(at, targetPaths, splitter, _) =>
        applySplit(value, at, targetPaths, splitter)

      case MigrationAction.ChangeType(at, converter, _) =>
        applyChangeType(value, at, converter)

      case MigrationAction.RenameCase(at, fromName, toName) =>
        applyRenameCase(value, at, fromName, toName)

      case MigrationAction.TransformCase(at, caseName, nestedActions) =>
        applyTransformCase(value, at, caseName, nestedActions)

      case MigrationAction.TransformElements(at, transform, _) =>
        applyTransformElements(value, at, transform)

      case MigrationAction.TransformKeys(at, transform, _) =>
        applyTransformKeys(value, at, transform)

      case MigrationAction.TransformValues(at, transform, _) =>
        applyTransformValues(value, at, transform)
    }

  // ─── Record operations ──────────────────────────────────────────────────

  private def applyAddField(
    value: DynamicValue,
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    modifyRecord(value, at, fieldName) { record =>
      if (record.fields.exists(_._1 == fieldName))
        Left(MigrationError.FieldAlreadyExists(at.field(fieldName), fieldName))
      else
        Right(DynamicValue.Record(record.fields :+ (fieldName, default)))
    }

  private def applyDropField(
    value: DynamicValue,
    at: DynamicOptic,
    fieldName: String
  ): Either[MigrationError, DynamicValue] =
    modifyRecord(value, at, fieldName) { record =>
      val newFields = record.fields.filterNot(_._1 == fieldName)
      if (newFields.length == record.fields.length)
        Left(MigrationError.MissingField(at.field(fieldName), fieldName))
      else
        Right(DynamicValue.Record(newFields))
    }

  private def applyRename(
    value: DynamicValue,
    at: DynamicOptic,
    fromName: String,
    toName: String
  ): Either[MigrationError, DynamicValue] =
    modifyRecord(value, at, fromName) { record =>
      if (!record.fields.exists(_._1 == fromName))
        Left(MigrationError.MissingField(at.field(fromName), fromName))
      else if (record.fields.exists(_._1 == toName))
        Left(MigrationError.FieldAlreadyExists(at.field(toName), toName))
      else {
        val newFields = record.fields.map {
          case (name, v) if name == fromName => (toName, v)
          case other                         => other
        }
        Right(DynamicValue.Record(newFields))
      }
    }

  private def applyTransformValue(
    value: DynamicValue,
    at: DynamicOptic,
    transform: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    // For now, TransformValue replaces the value at the path with the
    // transform value. Full SchemaExpr evaluation will be added in the
    // typed Migration[A, B] layer.
    value.setOrFail(at, transform) match {
      case Right(v) => Right(v)
      case Left(_)  => Left(MigrationError.InvalidPath(at, s"Cannot set value at path"))
    }

  private def applyMandate(
    value: DynamicValue,
    at: DynamicOptic,
    default: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    getAtPath(value, at).flatMap {
      case DynamicValue.Variant("None", _)     => setAtPath(value, at, default)
      case DynamicValue.Variant("Some", inner) => setAtPath(value, at, inner)
      case DynamicValue.Null                   => setAtPath(value, at, default)
      case _                                   => Right(value) // already non-optional
    }

  private def applyOptionalize(
    value: DynamicValue,
    at: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    getAtPath(value, at).flatMap {
      case DynamicValue.Null => setAtPath(value, at, DynamicValue.Variant("None", DynamicValue.Record.empty))
      case inner             => setAtPath(value, at, DynamicValue.Variant("Some", inner))
    }

  // ─── Enum operations ────────────────────────────────────────────────────

  private def applyRenameCase(
    value: DynamicValue,
    at: DynamicOptic,
    fromName: String,
    toName: String
  ): Either[MigrationError, DynamicValue] =
    getAtPath(value, at).flatMap {
      case DynamicValue.Variant(name, inner) if name == fromName =>
        setAtPath(value, at, DynamicValue.Variant(toName, inner))
      case DynamicValue.Variant(_, _) =>
        // Not the target case, leave unchanged
        Right(value)
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Variant", value.valueType.toString))
    }

  private def applyTransformCase(
    value: DynamicValue,
    at: DynamicOptic,
    caseName: String,
    nestedActions: Vector[MigrationAction]
  ): Either[MigrationError, DynamicValue] =
    getAtPath(value, at).flatMap {
      case DynamicValue.Variant(name, inner) if name == caseName =>
        val nested = DynamicMigration(nestedActions)
        nested(inner).flatMap(transformed => setAtPath(value, at, DynamicValue.Variant(name, transformed)))
      case DynamicValue.Variant(_, _) =>
        // Not the target case, leave unchanged
        Right(value)
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Variant", value.valueType.toString))
    }

  // ─── Collection / Map operations ────────────────────────────────────────

  private def applyTransformElements(
    value: DynamicValue,
    at: DynamicOptic,
    transform: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    getAtPath(value, at).flatMap {
      case seq: DynamicValue.Sequence =>
        // Replace each element with the transform value.
        // Full SchemaExpr evaluation in typed layer.
        val newElements = Chunk.fill(seq.elements.length)(transform)
        setAtPath(value, at, DynamicValue.Sequence(newElements))
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Sequence", value.valueType.toString))
    }

  private def applyTransformKeys(
    value: DynamicValue,
    at: DynamicOptic,
    transform: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    getAtPath(value, at).flatMap {
      case m: DynamicValue.Map =>
        val newEntries = m.entries.map { case (_, v) => (transform, v) }
        setAtPath(value, at, DynamicValue.Map(newEntries))
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Map", value.valueType.toString))
    }

  private def applyTransformValues(
    value: DynamicValue,
    at: DynamicOptic,
    transform: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    getAtPath(value, at).flatMap {
      case m: DynamicValue.Map =>
        val newEntries = m.entries.map { case (k, _) => (k, transform) }
        setAtPath(value, at, DynamicValue.Map(newEntries))
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Map", value.valueType.toString))
    }

  // ─── Join / Split ───────────────────────────────────────────────────────

  private def applyJoin(
    value: DynamicValue,
    at: DynamicOptic,
    @scala.annotation.unused sourcePaths: Vector[DynamicOptic],
    combiner: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    // For now, replaces the value at `at` with the combiner value.
    // Full SchemaExpr-based combination in typed layer.
    setAtPath(value, at, combiner)

  private def applySplit(
    value: DynamicValue,
    at: DynamicOptic,
    @scala.annotation.unused targetPaths: Vector[DynamicOptic],
    splitter: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    // For now, replaces the value at `at` with the splitter value.
    // Full SchemaExpr-based splitting in typed layer.
    setAtPath(value, at, splitter)

  // ─── Change type ────────────────────────────────────────────────────────

  private def applyChangeType(
    value: DynamicValue,
    at: DynamicOptic,
    converter: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    // For now, replaces the value at the path with the converter value.
    // Full primitive-to-primitive conversion in typed layer.
    setAtPath(value, at, converter)

  // ─── Helpers ────────────────────────────────────────────────────────────

  // Navigate to the value at a path, returning a MigrationError on failure.
  private def getAtPath(
    value: DynamicValue,
    at: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    if (at.nodes.isEmpty) Right(value)
    else
      value.get(at).one match {
        case Right(v) => Right(v)
        case Left(_)  => Left(MigrationError.InvalidPath(at, "Path not found"))
      }

  // Set a value at a path, returning a MigrationError on failure.
  private def setAtPath(
    value: DynamicValue,
    at: DynamicOptic,
    newValue: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    if (at.nodes.isEmpty) Right(newValue)
    else
      value.setOrFail(at, newValue) match {
        case Right(v) => Right(v)
        case Left(_)  => Left(MigrationError.InvalidPath(at, s"Cannot set value at path"))
      }

  // Modify a record at a given path. The `at` path locates the record; the
  // modification function receives the record and returns the modified value.
  private def modifyRecord(
    value: DynamicValue,
    at: DynamicOptic,
    @scala.annotation.unused fieldContext: String
  )(
    f: DynamicValue.Record => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {
    val target =
      if (at.nodes.isEmpty) value
      else
        value.get(at).one match {
          case Right(v) => v
          case Left(_)  => return Left(MigrationError.InvalidPath(at, "Path not found"))
        }

    target match {
      case record: DynamicValue.Record =>
        f(record).flatMap { modified =>
          if (at.nodes.isEmpty) Right(modified)
          else setAtPath(value, at, modified)
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", target.valueType.toString))
    }
  }
}
