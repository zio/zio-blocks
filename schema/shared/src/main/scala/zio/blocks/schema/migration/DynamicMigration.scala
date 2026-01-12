package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.{DynamicValue, OpticCheck, SchemaExpr, DynamicOptic}

/**
 * Pure, serializable migration program (no closures).
 *
 * Matches the design of zio-blocks issue #519:
 *   DynamicMigration(actions: Vector[MigrationAction])
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) extends Product with Serializable { self =>

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(self.actions ++ that.actions)

  def andThen(that: DynamicMigration): DynamicMigration =
    self ++ that

  /**
   * Structural reverse.
   *
   * NOTE: Whether the reverse is semantically correct depends on whether expressions
   * are invertible; structurally, we always produce a reverse program.
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)
  val id: DynamicMigration    = empty

  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(Vector.from(actions))
}

/**
 * Migration-only marker expression: "use schema default".
 *
 * This cannot be evaluated from runtime input alone; the interpreter must
 * resolve it using the field schema at the place it is applied.
 */
case object DefaultValueExpr extends SchemaExpr[Any, Any] {
  override def eval(input: Any): Either[OpticCheck, Seq[Any]] =
    Left(new OpticCheck("DefaultValueExpr must be resolved using schema defaults"))

  override def evalDynamic(input: Any): Either[OpticCheck, Seq[DynamicValue]] =
    Left(new OpticCheck("DefaultValueExpr must be resolved using schema defaults"))
}

/**
 * The algebra of migrations.
 *
 * IMPORTANT:
 * - keep it data-only (serializable)
 * - do not store lambdas/closures
 * - store SchemaExpr as SchemaExpr[Any, Any] (erased) to keep the ADT monomorphic
 */
sealed trait MigrationAction extends Product with Serializable { self =>
  def reverse: MigrationAction
}

object MigrationAction {

  // ─────────────────────────────────────────────
  // Record operations
  // ─────────────────────────────────────────────

  /**
   * Add a field (in a record selected by `at`) with a given field name and default expression.
   *
   * defaultExpr is SchemaExpr[Any, Any] because this is the serializable core;
   * the typed DSL is responsible for supplying a correctly typed SchemaExpr.
   */
  final case class AddField(
      at: DynamicOptic,
      fieldName: String,
      defaultExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      DropField(at = at, fieldName = fieldName, defaultForReverse = DefaultValueExpr)
  }

  /**
   * Drop a field. Reverse needs a way to re-create it: defaultForReverse.
   * In most cases, reverse should use DefaultValueExpr.
   */
  final case class DropField(
      at: DynamicOptic,
      fieldName: String,
      defaultForReverse: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      AddField(at = at, fieldName = fieldName, defaultExpr = defaultForReverse)
  }

  final case class RenameField(
      at: DynamicOptic,
      from: String,
      to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      RenameField(at = at, from = to, to = from)
  }

  /**
   * Transform a field from one optic to another using a SchemaExpr.
   * (Later: you may refine the constraints based on #519 rules.)
   */
  final case class TransformField(
      from: DynamicOptic,
      to: DynamicOptic,
      transformExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformField(from = to, to = from, transformExpr = transformExpr) // best-effort: keep same expr
  }

  /**
   * Make an optional field mandatory (Option[A] -> A), using defaultExpr when None.
   */
  final case class MandateField(
      sourceOpt: DynamicOptic,
      target: DynamicOptic,
      defaultExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      OptionalizeField(source = target, targetOpt = sourceOpt)
  }

  /**
   * Make a mandatory field optional (A -> Option[A]).
   */
  final case class OptionalizeField(
      source: DynamicOptic,
      targetOpt: DynamicOptic
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      MandateField(sourceOpt = targetOpt, target = source, defaultExpr = DefaultValueExpr)
  }

  /**
   * Change field type (primitive->primitive etc.) using a converter expression.
   */
  final case class ChangeFieldType(
      source: DynamicOptic,
      target: DynamicOptic,
      converterExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      ChangeFieldType(source = target, target = source, converterExpr = converterExpr) // best-effort
  }

  // ─────────────────────────────────────────────
  // Enum operations
  // ─────────────────────────────────────────────

  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    override def reverse: MigrationAction = RenameCase(at = at, from = to, to = from)
  }

  /**
   * Apply a nested migration (actions) only for a specific enum case.
   */
  final case class TransformCase(at: DynamicOptic, caseName: String, actions: Vector[MigrationAction]) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformCase(at = at, caseName = caseName, actions = actions.reverse.map(_.reverse))
  }

  // ─────────────────────────────────────────────
  // Collections & Maps (kept as data; interpreter can implement later)
  // ─────────────────────────────────────────────

  final case class TransformElements(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformElements(at = at, actions = actions.reverse.map(_.reverse))
  }

  final case class TransformKeys(at: DynamicOptic, expr: SchemaExpr[Any, Any]) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformKeys(at = at, expr = expr) // best-effort
  }

  final case class TransformValues(at: DynamicOptic, expr: SchemaExpr[Any, Any]) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformValues(at = at, expr = expr) // best-effort
  }
}
