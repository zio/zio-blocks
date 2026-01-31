/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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
 * Errors that can occur during schema migration.
 *
 * All errors include path information via `DynamicOptic` for precise diagnostics.
 */
sealed trait MigrationError extends Exception {
  def path: DynamicOptic
  def message: String
  override def getMessage: String = s"Migration error at ${path}: $message"
}

object MigrationError {

  /**
   * A required field was not found in the source value.
   */
  final case class MissingField(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Missing field '$fieldName'"
  }

  /**
   * Type conversion failed during migration.
   */
  final case class ConversionFailed(path: DynamicOptic, from: String, to: String, reason: String)
      extends MigrationError {
    def message: String = s"Failed to convert from $from to $to: $reason"
  }

  /**
   * Expected a certain structure but found something else.
   */
  final case class UnexpectedStructure(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Expected $expected but found $actual"
  }

  /**
   * A required default value was not provided for reverse migration.
   */
  final case class MissingDefault(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"No default value provided for field '$fieldName' (required for reverse migration)"
  }

  /**
   * The variant case was not found.
   */
  final case class UnknownCase(path: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Unknown variant case '$caseName'"
  }

  /**
   * An expression evaluation failed.
   */
  final case class ExpressionFailed(path: DynamicOptic, expr: String, reason: String) extends MigrationError {
    def message: String = s"Expression '$expr' failed: $reason"
  }

  /**
   * Incompatible schemas for migration.
   */
  final case class IncompatibleSchemas(path: DynamicOptic, sourceType: String, targetType: String)
      extends MigrationError {
    def message: String = s"Incompatible schemas: cannot migrate from $sourceType to $targetType"
  }

  // Smart constructors
  def missingField(path: DynamicOptic, fieldName: String): MigrationError =
    MissingField(path, fieldName)

  def conversionFailed(path: DynamicOptic, from: String, to: String, reason: String): MigrationError =
    ConversionFailed(path, from, to, reason)

  def unexpectedStructure(path: DynamicOptic, expected: String, actual: String): MigrationError =
    UnexpectedStructure(path, expected, actual)

  def missingDefault(path: DynamicOptic, fieldName: String): MigrationError =
    MissingDefault(path, fieldName)

  def unknownCase(path: DynamicOptic, caseName: String): MigrationError =
    UnknownCase(path, caseName)

  def expressionFailed(path: DynamicOptic, expr: String, reason: String): MigrationError =
    ExpressionFailed(path, expr, reason)

  def incompatibleSchemas(path: DynamicOptic, sourceType: String, targetType: String): MigrationError =
    IncompatibleSchemas(path, sourceType, targetType)
}
