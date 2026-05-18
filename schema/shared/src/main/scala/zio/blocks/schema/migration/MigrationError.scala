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
 * Represents errors that can occur during migration execution.
 *
 * All errors capture path information via [[DynamicOptic]] for diagnostics,
 * enabling messages like:
 * {{{
 * "Failed to apply TransformValue at .addresses.each.streetNumber"
 * }}}
 */
sealed trait MigrationError { self =>
  def message: String

  def ++(that: MigrationError): MigrationError = (self, that) match {
    case (MigrationError.Multiple(es1), MigrationError.Multiple(es2)) => MigrationError.Multiple(es1 ++ es2)
    case (MigrationError.Multiple(es1), _)                            => MigrationError.Multiple(es1 :+ that)
    case (_, MigrationError.Multiple(es2))                            => MigrationError.Multiple(self +: es2)
    case _                                                            => MigrationError.Multiple(Vector(self, that))
  }
}

object MigrationError {

  /**
   * A migration action failed at the specified path.
   *
   * @param action
   *   the name of the action that failed (e.g., "AddField", "Rename")
   * @param at
   *   the path where the failure occurred
   * @param details
   *   a human-readable description of the failure
   */
  final case class ActionFailed(action: String, at: DynamicOptic, details: String) extends MigrationError {
    override def message: String = s"Failed to apply $action at ${at.toString}: $details"
  }

  /**
   * The specified path was not found in the DynamicValue being migrated.
   */
  final case class PathNotFound(at: DynamicOptic) extends MigrationError {
    override def message: String = s"Path not found: ${at.toString}"
  }

  /**
   * A type conversion failed during migration.
   */
  final case class TypeConversionFailed(at: DynamicOptic, from: String, to: String) extends MigrationError {
    override def message: String = s"Type conversion failed at ${at.toString}: cannot convert $from to $to"
  }

  /**
   * Multiple migration errors occurred.
   */
  final case class Multiple(errors: Vector[MigrationError]) extends MigrationError {
    override def message: String = errors.map(_.message).mkString("; ")
  }
}
