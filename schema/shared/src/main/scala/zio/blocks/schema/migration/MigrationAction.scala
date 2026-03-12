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

import zio.blocks.schema.{DynamicOptic, DynamicValue}

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  final case class AddField(at: DynamicOptic, default: DynamicSchemaExpr) extends MigrationAction {
    override def reverse: MigrationAction = DropField(at, default)

    def fieldName: String = at.nodes.last match {
      case DynamicOptic.Node.Field(name) => name
      case other                         => throw new IllegalStateException(s"AddField path must end with a Field node, got: $other")
    }
  }

  final case class DropField(at: DynamicOptic, defaultForReverse: DynamicSchemaExpr) extends MigrationAction {
    override def reverse: MigrationAction = AddField(at, defaultForReverse)

    def fieldName: String = at.nodes.last match {
      case DynamicOptic.Node.Field(name) => name
      case other                         => throw new IllegalStateException(s"DropField path must end with a Field node, got: $other")
    }
  }

  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    override def reverse: MigrationAction = {
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      Rename(parentPath.field(to), from)
    }

    def from: String = at.nodes.last match {
      case DynamicOptic.Node.Field(name) => name
      case other                         => throw new IllegalStateException(s"Rename path must end with a Field node, got: $other")
    }
  }

  final case class TransformValue(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformValue")
  }

  final case class Mandate(at: DynamicOptic, default: DynamicSchemaExpr) extends MigrationAction {
    override def reverse: MigrationAction = Optionalize(at)
  }

  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    override def reverse: MigrationAction = Mandate(at, DynamicSchemaExpr.Literal(DynamicValue.Record.empty))
  }

  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "Join")
  }

  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "Split")
  }

  final case class ChangeType(at: DynamicOptic, converter: DynamicSchemaExpr) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "ChangeType")
  }

  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    override def reverse: MigrationAction = RenameCase(at, to, from)
  }

  final case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    override def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
  }

  final case class ApplyMigration(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    override def reverse: MigrationAction = ApplyMigration(at, migration.reverse)
  }

  final case class TransformElements(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformElements")
  }

  final case class TransformKeys(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformKeys")
  }

  final case class TransformValues(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformValues")
  }

  final case class Irreversible(at: DynamicOptic, originalAction: String) extends MigrationAction {
    override def reverse: MigrationAction = this
  }
}
