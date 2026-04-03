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

import zio.blocks.schema.{DynamicOptic, SchemaExpr}

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  private def replaceLastNode(optic: DynamicOptic, newNode: DynamicOptic.Node): DynamicOptic =
    if (optic.nodes.isEmpty) optic
    else DynamicOptic(optic.nodes.init :+ newNode)

  private def extractRenameSource(optic: DynamicOptic): String =
    optic.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case Some(DynamicOptic.Node.Case(name))  => name
      case _                                   => ""
    }

  // --- Record Actions ---

  final case class AddField(
    at: DynamicOptic,
    default: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      val fromField = extractRenameSource(at)
      Rename(replaceLastNode(at, DynamicOptic.Node.Field(to)), fromField)
    }
  }

  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = this // Runtime best-effort semantics for expressions
  }

  final case class Mandate(
    at: DynamicOptic,
    default: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    // We cannot reliably mandate without a runtime default
    def reverse: MigrationAction = this
  }

  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class ChangeType(
    at: DynamicOptic,
    converter: SchemaExpr[Any, Any] // primitive-to-primitive only
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  // --- Enum Actions ---

  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction =
      RenameCase(replaceLastNode(at, DynamicOptic.Node.Case(to)), to, from)
  }

  final case class TransformCase(
    at: DynamicOptic,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
  }

  // --- Collection / Map Actions ---

  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }
}

