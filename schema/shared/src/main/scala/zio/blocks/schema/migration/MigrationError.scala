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

import scala.util.control.NoStackTrace

/**
 * An error that occurs during schema migration. Captures both a message and
 * the path at which the error occurred, enabling precise diagnostics.
 *
 * For example: "Failed to apply TransformValue at `.addresses.each.streetNumber`"
 */
final case class MigrationError(message: String, path: DynamicOptic)
    extends Exception(s"Migration failed at $path: $message")
    with NoStackTrace

object MigrationError {

  /** Create a MigrationError at the root path. */
  def apply(message: String): MigrationError = new MigrationError(message, DynamicOptic.root)

  /** Create a MigrationError at the given path. */
  def atPath(path: DynamicOptic, message: String): MigrationError = new MigrationError(message, path)

  /** Create a MigrationError for a missing field. */
  def missingField(path: DynamicOptic, fieldName: String): MigrationError =
    new MigrationError(s"Field '$fieldName' not found", path)

  /** Create a MigrationError for a field that already exists. */
  def duplicateField(path: DynamicOptic, fieldName: String): MigrationError =
    new MigrationError(s"Field '$fieldName' already exists", path)

  /** Create a MigrationError for a type mismatch. */
  def typeMismatch(path: DynamicOptic, expected: String, actual: String): MigrationError =
    new MigrationError(s"Expected $expected but got $actual", path)

  /** Create a MigrationError for an unknown case. */
  def unknownCase(path: DynamicOptic, caseName: String): MigrationError =
    new MigrationError(s"Unknown case '$caseName'", path)
}
