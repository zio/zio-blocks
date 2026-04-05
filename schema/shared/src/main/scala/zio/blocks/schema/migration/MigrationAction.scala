/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * A single serializable migration step. All locations are expressed as
 * [[DynamicOptic]] paths (never user-defined closures).
 */
sealed trait MigrationAction {
  def at: DynamicOptic

  /** Structural inverse: used to build `reverse` on [[DynamicMigration]]. */
  def reverse: MigrationAction
}

object MigrationAction {

  final case class AddField(at: DynamicOptic, defaultValue: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, defaultValue.reverse)
  }

  final case class DropField(at: DynamicOptic, defaultForReverse: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = at.nodes.lastOption match {
      case Some(oldField: DynamicOptic.Node.Field) =>
        val parent = new DynamicOptic(at.nodes.dropRight(1))
        Rename(parent.field(to), oldField.name)
      case _ =>
        Rename(at, to)
    }
  }

  final case class TransformValue(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform.reverse)
  }

  final case class Mandate(at: DynamicOptic, defaultValue: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction =
      Mandate(at, MigrationExpr.Literal(zio.blocks.schema.DynamicValue.Null))
  }

  final case class ChangeType(at: DynamicOptic, converter: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter.reverse)
  }

  final case class Join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: MigrationExpr)
      extends MigrationAction {
    def reverse: MigrationAction =
      Split(at, sourcePaths, combiner.reverse) // combiner inverse is best-effort; structural only
  }

  final case class Split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: MigrationExpr)
      extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitter.reverse)
  }

  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  final case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction =
      TransformCase(at, actions.map(_.reverse).reverse)
  }

  final case class TransformElements(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform.reverse)
  }

  final case class TransformKeys(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform.reverse)
  }

  final case class TransformValues(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform.reverse)
  }

}
