package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A pure, serializable expression language for value-level transformations
 * within migrations.
 *
 * `MigrationExpr` replaces closures and functions in migration actions,
 * ensuring that every migration is fully serializable, inspectable, and
 * storable. Expressions are constrained to primitive-to-primitive
 * transformations.
 *
 * '''Zero closures, zero `Function` types, zero `Any => Any`, zero
 * reflection.'''
 */
sealed trait MigrationExpr

object MigrationExpr {

  // ─────────────────────────────────────────────────────────────────────────
  // Value Expressions
  // ─────────────────────────────────────────────────────────────────────────

  /** A constant [[DynamicValue]]. */
  final case class Literal(value: DynamicValue) extends MigrationExpr

  /** Read a value from a path via [[DynamicOptic]]. */
  final case class FieldRef(path: DynamicOptic) extends MigrationExpr

  /**
   * Resolves to a field's schema default value. The default is represented as a
   * [[DynamicValue]] because resolution happens at construction time, not
   * evaluation time, ensuring serializability.
   */
  final case class DefaultValue(value: DynamicValue) extends MigrationExpr

  // ─────────────────────────────────────────────────────────────────────────
  // String Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Concatenate two expression results as strings. */
  final case class Concat(left: MigrationExpr, right: MigrationExpr) extends MigrationExpr

  // ─────────────────────────────────────────────────────────────────────────
  // Arithmetic Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Add two numeric expressions. */
  final case class Add(left: MigrationExpr, right: MigrationExpr) extends MigrationExpr

  /** Subtract the right numeric expression from the left. */
  final case class Subtract(left: MigrationExpr, right: MigrationExpr) extends MigrationExpr

  /** Multiply two numeric expressions. */
  final case class Multiply(left: MigrationExpr, right: MigrationExpr) extends MigrationExpr

  /** Divide the left numeric expression by the right. */
  final case class Divide(left: MigrationExpr, right: MigrationExpr) extends MigrationExpr

  // ─────────────────────────────────────────────────────────────────────────
  // Type Coercion
  // ─────────────────────────────────────────────────────────────────────────

  /** Coerce the result of `expr` to the given `targetType`. */
  final case class Coerce(expr: MigrationExpr, targetType: String) extends MigrationExpr
}
