package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaExpr}

/**
 * Algebraic data type representing a single structural transformation step
 * within a migration.
 *
 * Each action operates at a specific path (`at`) within a DynamicValue tree and
 * carries explicit lossy/reversibility markers. Actions are applied
 * sequentially by [[DynamicMigration]].
 *
 * Lossy actions (those that destroy information) have `lossy = true` and
 * `reverse = None`. Lossless actions provide a `reverse` that undoes the
 * transformation.
 */
sealed trait MigrationAction {

  /** The DynamicOptic path where this action operates. */
  def at: DynamicOptic

  /**
   * The reverse action that undoes this transformation, if one exists. Returns
   * `None` for lossy/irreversible actions.
   */
  def reverse: Option[MigrationAction]

  /**
   * Whether this action destroys information. When `true`, the migration
   * containing this action cannot be fully reversed.
   */
  def lossy: Boolean
}

object MigrationAction {

  // ── Record Operations ──────────────────────────────────────────────────

  /**
   * Adds a new field to a record at the specified path.
   *
   * The `defaultExpr` provides the value for the new field. It must be a
   * concrete, evaluable `SchemaExpr` (resolved eagerly by MigrationBuilder).
   *
   * Reverse: `DropField` that removes the added field.
   */
  final case class AddField(
    at: DynamicOptic,
    defaultExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = Some(DropField(at, reverseDefault = None))
    def lossy: Boolean                   = false
  }

  /**
   * Drops a field from a record at the specified path.
   *
   * @param reverseDefault
   *   If provided, the reverse action re-adds the field with this default. If
   *   `None`, the action is lossy (data is lost and cannot be recovered).
   */
  final case class DropField(
    at: DynamicOptic,
    reverseDefault: Option[SchemaExpr[Any, Any]]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = reverseDefault.map(d => AddField(at, d))
    def lossy: Boolean                   = reverseDefault.isEmpty
  }

  /**
   * Renames a field within a record. The `at` path points to the existing
   * field, and `newName` is the target name.
   *
   * Always lossless — reverse simply renames back.
   */
  final case class Rename(
    at: DynamicOptic,
    newName: String
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = {
      val oldName = at.nodes.last match {
        case DynamicOptic.Node.Field(name) => name
        case _                             => throw new IllegalStateException(s"Rename path must end with a Field node: $at")
      }
      Some(Rename(parentPath(at).field(newName), oldName))
    }
    def lossy: Boolean = false
  }

  /**
   * Transforms the value at a path using a `SchemaExpr`.
   *
   * @param transform
   *   Expression that transforms the value
   * @param inverse
   *   Optional inverse expression. If `None`, the action is lossy.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any],
    inverse: Option[SchemaExpr[Any, Any]]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = inverse.map(inv => TransformValue(at, inv, Some(transform)))
    def lossy: Boolean                   = inverse.isEmpty
  }

  /**
   * Converts an optional field to a mandatory field. The value at `at` must be
   * an `Option`-like variant (Some/None). If the value is None, the
   * `defaultExpr` provides the replacement value.
   *
   * Reverse: `Optionalize`.
   */
  final case class Mandate(
    at: DynamicOptic,
    defaultExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = Some(Optionalize(at))
    def lossy: Boolean                   = false
  }

  /**
   * Wraps a mandatory field in an `Option`-like variant (Some/None).
   *
   * Reverse: `Mandate` — but the reverse mandate has no meaningful default
   * since the value is always present after optionalizing.
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] =
      // When reversing optionalize, we mandate with a dummy default
      // that will never actually be used (the value is always Some after optionalizing).
      // We use SchemaExpr.Literal with DynamicValue.Null as a sentinel that the
      // execution engine treats specially: "use the existing value".
      None // Conservative: mark as lossy since we can't provide a proper default
    def lossy: Boolean = true
  }

  /**
   * Changes the type of a value at a path using a converter expression.
   *
   * @param converter
   *   Expression that converts from old type to new type
   * @param inverseConverter
   *   Optional inverse. If `None`, the action is lossy.
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: SchemaExpr[Any, Any],
    inverseConverter: Option[SchemaExpr[Any, Any]]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] =
      inverseConverter.map(inv => ChangeType(at, inv, Some(converter)))
    def lossy: Boolean = inverseConverter.isEmpty
  }

  /**
   * Joins multiple source fields into a single target field.
   *
   * @param sourcePaths
   *   Paths to extract values from (all consumed/removed)
   * @param combiner
   *   Expression that combines the extracted values
   * @param inverseSplitter
   *   Optional inverse. If `None`, the action is lossy.
   * @param targetShape
   *   Explicit output shape set by MigrationBuilder (for validation)
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[Any, Any],
    inverseSplitter: Option[SchemaExpr[Any, Any]],
    targetShape: SchemaShape
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] =
      inverseSplitter.map(inv =>
        Split(at, sourcePaths, inv, inverseJoiner = Some(combiner), targetShapes = Vector.empty)
      )
    def lossy: Boolean = inverseSplitter.isEmpty
  }

  /**
   * Splits one source field into multiple target fields.
   *
   * @param targetPaths
   *   Paths where the split pieces are placed
   * @param splitter
   *   Expression that produces the pieces
   * @param inverseJoiner
   *   Optional inverse. If `None`, the action is lossy.
   * @param targetShapes
   *   One per target path, explicit shapes set by MigrationBuilder (for
   *   validation)
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[Any, Any],
    inverseJoiner: Option[SchemaExpr[Any, Any]],
    targetShapes: Vector[SchemaShape]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] =
      inverseJoiner.map(inv =>
        Join(at, targetPaths, inv, inverseSplitter = Some(splitter), targetShape = SchemaShape.Dyn)
      )
    def lossy: Boolean = inverseJoiner.isEmpty
  }

  // ── Enum Operations ────────────────────────────────────────────────────

  /**
   * Renames a case within a variant at the specified path.
   *
   * Always lossless — reverse renames back.
   */
  final case class RenameCase(
    at: DynamicOptic,
    fromName: String,
    toName: String
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = Some(RenameCase(at, toName, fromName))
    def lossy: Boolean                   = false
  }

  /**
   * Transforms the value of a specific case within a variant by applying
   * sub-actions to the case's inner value.
   *
   * @param caseName
   *   The case to match on
   * @param subActions
   *   Actions to apply to the matched case's value
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    subActions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = {
      val reversedSubs = subActions.reverseIterator.map(_.reverse).toVector
      if (reversedSubs.exists(_.isEmpty)) None
      else Some(TransformCase(at, caseName, reversedSubs.flatten))
    }
    def lossy: Boolean = subActions.exists(_.lossy)
  }

  // ── Collection/Map Operations ──────────────────────────────────────────

  /**
   * Transforms each element of a sequence at the specified path.
   *
   * @param transform
   *   Expression applied to each element
   * @param inverse
   *   Optional inverse. If `None`, the action is lossy.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any],
    inverse: Option[SchemaExpr[Any, Any]]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = inverse.map(inv => TransformElements(at, inv, Some(transform)))
    def lossy: Boolean                   = inverse.isEmpty
  }

  /**
   * Transforms each key of a map at the specified path.
   *
   * @param transform
   *   Expression applied to each key
   * @param inverse
   *   Optional inverse. If `None`, the action is lossy.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any],
    inverse: Option[SchemaExpr[Any, Any]]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = inverse.map(inv => TransformKeys(at, inv, Some(transform)))
    def lossy: Boolean                   = inverse.isEmpty
  }

  /**
   * Transforms each value of a map at the specified path.
   *
   * @param transform
   *   Expression applied to each value
   * @param inverse
   *   Optional inverse. If `None`, the action is lossy.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any],
    inverse: Option[SchemaExpr[Any, Any]]
  ) extends MigrationAction {
    def reverse: Option[MigrationAction] = inverse.map(inv => TransformValues(at, inv, Some(transform)))
    def lossy: Boolean                   = inverse.isEmpty
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  /**
   * Extracts the parent path from a DynamicOptic (all nodes except the last).
   */
  private[migration] def parentPath(optic: DynamicOptic): DynamicOptic =
    if (optic.nodes.isEmpty) optic
    else new DynamicOptic(optic.nodes.init)

  /** Extracts the last field name from a DynamicOptic path. */
  private[migration] def lastFieldName(optic: DynamicOptic): Option[String] =
    optic.nodes.lastOption.collect { case DynamicOptic.Node.Field(name) => name }
}
