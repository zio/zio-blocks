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
 * Represents an error that occurred while applying a [[DynamicMigration]] or
 * [[Migration]].
 *
 * All errors carry the [[DynamicOptic]] path at which the failure occurred,
 * enabling precise diagnostics such as:
 * {{{
 *   "Failed to apply TransformValue at .addresses.each.streetNumber"
 * }}}
 */
sealed trait MigrationError {

  /** The optic path at which the failure occurred. */
  def at: DynamicOptic

  /** Human-readable description of the failure. */
  def message: String

  override def toString: String =
    s"MigrationError at ${at.toScalaString}: $message"
}

object MigrationError {

  /**
   * A required field was absent in the source
   * [[zio.blocks.schema.DynamicValue]] at the given path.
   */
  final case class FieldNotFound(at: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' not found"
  }

  /**
   * The [[zio.blocks.schema.DynamicValue]] at the given path had an unexpected
   * type.
   */
  final case class TypeMismatch(at: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Expected $expected but got $actual"
  }

  /**
   * A value-level transformation (e.g., [[MigrationAction.TransformValue]])
   * failed at the given path.
   */
  final case class TransformFailed(at: DynamicOptic, cause: String) extends MigrationError {
    def message: String = s"Transform failed: $cause"
  }

  /**
   * A variant case referenced by [[MigrationAction.RenameCase]] or
   * [[MigrationAction.TransformCase]] was not found.
   */
  final case class CaseNotFound(at: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Case '$caseName' not found"
  }

  /**
   * The source [[zio.blocks.schema.Schema]] could not decode the migrated
   * [[zio.blocks.schema.DynamicValue]].
   */
  final case class DecodeFailed(at: DynamicOptic, cause: String) extends MigrationError {
    def message: String = s"Decode failed: $cause"
  }
}
