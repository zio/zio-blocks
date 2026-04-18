package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}
import zio.blocks.schema.DynamicOptic

/**
 * A purely algebraic data type (ADT) representing structural transformation between Schema versions.
 * Serializability and deep introspectability are the prime directives of this design.
 */
sealed trait DynamicMigration { self =>

  /**
   * Semantically composes two migrations together.
   * `m1 >>> m2` yields a migration that runs `m1` and then `m2`.
   */
  def >>>(that: DynamicMigration): DynamicMigration =
    DynamicMigration.Compose(self, that)

  /**
   * Attempts to syntactically compute the structural inverse of this migration.
   * e.g., Rename(A, B).invert == Rename(B, A).
   */
  def invert: DynamicMigration
}

object DynamicMigration {

  // --- Identity & Composition ---
  case object Identity extends DynamicMigration {
    def invert: DynamicMigration = this
  }

  final case class Compose(left: DynamicMigration, right: DynamicMigration) extends DynamicMigration {
    def invert: DynamicMigration = Compose(right.invert, left.invert)
  }

  // --- Record Actions (Fields) ---
  final case class AddField(optic: DynamicOptic, value: DynamicValue) extends DynamicMigration {
    def invert: DynamicMigration = DeleteField(optic)
  }

  final case class DeleteField(optic: DynamicOptic) extends DynamicMigration {
    // Structural inversion naturally loses the original value unless tracked in a higher context
    // This provides a strictly structural inversion, which may require manual value insertion.
    def invert: DynamicMigration = AddField(optic, DynamicValue.Null) 
  }

  final case class RenameField(optic: DynamicOptic, newName: String) extends DynamicMigration {
    def invert: DynamicMigration = ???
  }

  final case class MandateField(optic: DynamicOptic, fallback: DynamicValue) extends DynamicMigration {
    def invert: DynamicMigration = OptionalizeField(optic)
  }

  final case class OptionalizeField(optic: DynamicOptic) extends DynamicMigration {
    def invert: DynamicMigration = MandateField(optic, DynamicValue.Null) // Fallback depends on user logic
  }

  // --- Value Actions ---
  final case class ChangeType[A, B](
    optic: DynamicOptic, 
    from: Schema[A], 
    to: Schema[B], 
    transform: SchemaExpr[A, B]
  ) extends DynamicMigration {
    def invert: DynamicMigration = ChangeType(optic, to, from, transform.invert)
  }

  // --- Enum Actions ---
  final case class RenameCase(optic: DynamicOptic, newName: String) extends DynamicMigration {
    def invert: DynamicMigration = ???
  }
}

/**
 * Placeholder for the primitive mappings
 */
sealed trait SchemaExpr[A, B] {
  def invert: SchemaExpr[B, A]
}
