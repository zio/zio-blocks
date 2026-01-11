package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.DynamicValue

/**
 * Pure, serializable migration program (no closures).
 * Introspectable: pattern match on ops.
 */
sealed trait DynamicMigration extends Product with Serializable { self =>
  def ++(that: DynamicMigration): DynamicMigration =
    (self, that) match {
      case (DynamicMigration.Sequence(xs), DynamicMigration.Sequence(ys)) => DynamicMigration.Sequence(xs ++ ys)
      case (DynamicMigration.Sequence(xs), _)                             => DynamicMigration.Sequence(xs :+ that)
      case (_, DynamicMigration.Sequence(ys))                             => DynamicMigration.Sequence(self +: ys)
      case _                                                              => DynamicMigration.Sequence(Vector(self, that))
    }
}

object DynamicMigration {
  final case class Sequence(ops: Vector[DynamicMigration]) extends DynamicMigration

  // Records
  final case class AddField(at: Path, field: String, default: DynamicValue) extends DynamicMigration
  final case class DeleteField(at: Path, field: String)                     extends DynamicMigration
  final case class RenameField(at: Path, from: String, to: String)          extends DynamicMigration

  // Arrays
  final case class WrapInArray(at: Path) extends DynamicMigration
  final case class UnwrapArray(at: Path) extends DynamicMigration

  // Enums (string-based mapping; extend if you use tagged unions)
  final case class MapEnumCase(at: Path, mapping: Vector[(String, String)]) extends DynamicMigration

  // Deterministic primitive conversions (no user lambdas)
  final case class ConvertPrimitive(at: Path, from: Primitive, to: Primitive) extends DynamicMigration

  sealed trait Primitive extends Product with Serializable
  object Primitive {
    case object String  extends Primitive
    case object Int     extends Primitive
    case object Long    extends Primitive
    case object Double  extends Primitive
    case object Boolean extends Primitive
  }

  case object Id extends DynamicMigration

  def sequence(ops: DynamicMigration*): DynamicMigration =
    Sequence(Vector.from(ops).filterNot(_ == Id))
}
