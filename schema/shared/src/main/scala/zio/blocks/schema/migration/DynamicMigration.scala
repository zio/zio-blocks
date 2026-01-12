package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.{DynamicValue, OpticCheck, SchemaExpr, DynamicOptic}

/** Pure, serializable migration program (no closures).
  *
  * Matches the design of zio-blocks issue #519: DynamicMigration(actions:
  * Vector[MigrationAction])
  */
final case class DynamicMigration(actions: Vector[MigrationAction])
    extends Product
    with Serializable { self =>

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(self.actions ++ that.actions)

  def andThen(that: DynamicMigration): DynamicMigration =
    self ++ that

  /** Structural reverse.
    *
    * NOTE: Whether the reverse is semantically correct depends on whether
    * expressions are invertible; structurally, we always produce a reverse
    * program.
    */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)
  val id: DynamicMigration = empty

  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(Vector.from(actions))
}

/** The algebra of migrations.
  *
  * IMPORTANT:
  *   - keep it data-only (serializable)
  *   - do not store lambdas/closures
  *   - store SchemaExpr as SchemaExpr[Any, Any] (erased) to keep the ADT
  *     monomorphic
  */
sealed trait MigrationAction extends Product with Serializable { self =>
  def reverse: MigrationAction
}

object MigrationAction {

  // ─────────────────────────────────────────────
  // Record operations
  // ─────────────────────────────────────────────

  object MigrationAction {

    // ─────────────────────────────────────────────
    // Record operations (PATH-ONLY)
    // ─────────────────────────────────────────────

    /** Add a field at `at` (which must end in `.field("x")`), using
      * `defaultExpr`.
      */
    final case class AddField(
        at: DynamicOptic,
        defaultExpr: SchemaExpr[Any, Any]
    ) extends MigrationAction {
      override def reverse: MigrationAction =
        DropField(
          at = at,
          fieldName = fieldName,
          defaultForReverse = defaultExpr
        )
      // NOTE: you will likely want to supply the correct schema in the DSL;
      // see Step 4 where we do it properly.
    }

    /** Drop a field at `at`. `defaultForReverse` is used when reversing. */
    final case class DropField(
        at: DynamicOptic,
        defaultForReverse: SchemaExpr[Any, Any]
    ) extends MigrationAction {
      override def reverse: MigrationAction =
        AddField(at = at, defaultExpr = defaultForReverse)
    }

    /** Rename the field at `at` to `to`. */
    final case class Rename(
        at: DynamicOptic,
        to: String
    ) extends MigrationAction {
      override def reverse: MigrationAction =
        Rename(
          at = at.field(to),
          to =
            MigrationDsl.RuntimeOptic.lastFieldNameOrFail(at, "Rename.reverse")
        )
    }

    /** Transform value at `at` using `transformExpr` */
    final case class TransformValue(
        at: DynamicOptic,
        transformExpr: SchemaExpr[Any, Any]
    ) extends MigrationAction {
      override def reverse: MigrationAction =
        TransformValue(at = at, transformExpr = transformExpr) // best-effort
    }

    /** Make optional value mandatory (Option[A] -> A) */
    final case class MandateField(
        sourceOpt: DynamicOptic, // points to Option[A] in the source
        target: DynamicOptic, // points to A in the target
        defaultExpr: SchemaExpr[Any, Any] // used when source is None
    ) extends MigrationAction {
      override def reverse: MigrationAction =
        OptionalizeField(
          source = target, // A becomes source
          targetOpt = sourceOpt, // Option[A] becomes target
          defaultForReverse = defaultExpr // keep it for lawful reverse
        )
    }

    /** Make value optional (A -> Option[A]) */
    final case class OptionalizeField(
        source: DynamicOptic, // points to A in the source
        targetOpt: DynamicOptic, // points to Option[A] in the target
        defaultForReverse: SchemaExpr[
          Any,
          Any
        ] // stored so reverse Mandate is defined
    ) extends MigrationAction {
      override def reverse: MigrationAction =
        MandateField(
          sourceOpt = targetOpt,
          target = source,
          defaultExpr = defaultForReverse
        )
    }

    /** Change primitive type at `at` using converter */
    final case class ChangeType(
        at: DynamicOptic,
        converterExpr: SchemaExpr[Any, Any]
    ) extends MigrationAction {
      override def reverse: MigrationAction =
        ChangeType(at = at, converterExpr = converterExpr) // best-effort
    }

    // keep your Enum/Collection/Map ops for now, but convert them later similarly
  }

  // ─────────────────────────────────────────────
  // Enum operations
  // ─────────────────────────────────────────────

  final case class RenameCase(at: DynamicOptic, from: String, to: String)
      extends MigrationAction {
    override def reverse: MigrationAction =
      RenameCase(at = at, from = to, to = from)
  }

  /** Apply a nested migration (actions) only for a specific enum case.
    */
  final case class TransformCase(
      at: DynamicOptic,
      caseName: String,
      actions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformCase(
        at = at,
        caseName = caseName,
        actions = actions.reverse.map(_.reverse)
      )
  }

  // ─────────────────────────────────────────────
  // Collections & Maps (kept as data; interpreter can implement later)
  // ─────────────────────────────────────────────

  final case class TransformElements(
      at: DynamicOptic,
      actions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformElements(at = at, actions = actions.reverse.map(_.reverse))
  }

  final case class TransformKeys(at: DynamicOptic, expr: SchemaExpr[Any, Any])
      extends MigrationAction {
    override def reverse: MigrationAction =
      TransformKeys(at = at, expr = expr) // best-effort
  }

  final case class TransformValues(at: DynamicOptic, expr: SchemaExpr[Any, Any])
      extends MigrationAction {
    override def reverse: MigrationAction =
      TransformValues(at = at, expr = expr) // best-effort
  }

  /** Transform a value at a path (the #519-style primitive operation). */
  final case class TransformValue(
      at: DynamicOptic,
      transformExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformValue(at = at, transformExpr = transformExpr) // best-effort
  }

  /** Join multiple primitive fields into a single primitive field. */
  final case class Join(
      at: DynamicOptic, // parent record optic
      fieldNames: Vector[String], // input fields
      into: String, // output field
      joinExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      Split(
        at = at,
        fieldName = into,
        into = fieldNames,
        splitExpr = joinExpr
      ) // best-effort
  }

  /** Split a primitive field into multiple primitive fields. */
  final case class Split(
      at: DynamicOptic, // parent record optic
      fieldName: String, // input field
      into: Vector[String], // output fields
      splitExpr: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      Join(
        at = at,
        fieldNames = into,
        into = fieldName,
        joinExpr = splitExpr
      ) // best-effort
  }

}
