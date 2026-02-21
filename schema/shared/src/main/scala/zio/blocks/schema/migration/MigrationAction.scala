package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * MigrationAction: The Core Algebra (Fixed Type Arguments)
 * -------------------------------------------------------- Represents an
 * atomic, structural transformation on data.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  // =================================================================================
  // 1. Record Actions
  // =================================================================================

  // [FIX] Changed SchemaExpr[_, _] to SchemaExpr[_] based on compiler error.

  final case class AddField(at: DynamicOptic, default: SchemaExpr[_]) extends MigrationAction {
    override def reverse: MigrationAction = DropField(at, default)
  }

  final case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[_]) extends MigrationAction {
    override def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    override def reverse: MigrationAction = {
      val nodes   = at.nodes
      val oldName = nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(name)) => name
        case _                                   => "unknown"
      }
      val newPathNodes = nodes.dropRight(1) :+ DynamicOptic.Node.Field(to)
      Rename(DynamicOptic(newPathNodes), oldName)
    }
  }

  final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[_]) extends MigrationAction {
    override def reverse: MigrationAction = this
  }

  final case class Mandate(at: DynamicOptic, default: SchemaExpr[_]) extends MigrationAction {
    override def reverse: MigrationAction = Optionalize(at)
  }

  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    override def reverse: MigrationAction = {
      val placeholderDefault = SchemaExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Unit))
      Mandate(at, placeholderDefault)
    }
  }

  final case class Join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[_])
      extends MigrationAction {
    override def reverse: MigrationAction = Split(at, sourcePaths, combiner)
  }

  final case class Split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[_])
      extends MigrationAction {
    override def reverse: MigrationAction = Join(at, targetPaths, splitter)
  }

  final case class ChangeType(at: DynamicOptic, converter: SchemaExpr[_]) extends MigrationAction {
    override def reverse: MigrationAction = this
  }

  // =================================================================================
  // 2. Enum Actions
  // =================================================================================

  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    override def reverse: MigrationAction = RenameCase(at, to, from)
  }

  final case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    override def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
  }

  // =================================================================================
  // 3. Collection & Map Actions
  // =================================================================================

  final case class TransformElements(at: DynamicOptic, transform: SchemaExpr[_]) extends MigrationAction {
    override def reverse: MigrationAction = this
  }

  final case class TransformKeys(at: DynamicOptic, transform: SchemaExpr[_]) extends MigrationAction {
    override def reverse: MigrationAction = this
  }

  final case class TransformValues(at: DynamicOptic, transform: SchemaExpr[_]) extends MigrationAction {
    override def reverse: MigrationAction = this
  }
}
