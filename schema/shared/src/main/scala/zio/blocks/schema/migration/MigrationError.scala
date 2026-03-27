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

import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * Errors that can occur during schema migration.
 */
sealed trait MigrationError extends Exception {
  override def fillInStackTrace(): Throwable = this
}

object MigrationError {

  /** The specified field was not found in the record at the given path. */
  final case class FieldNotFound(path: DynamicOptic, fieldName: String) extends MigrationError {
    override def getMessage: String = s"Field '$fieldName' not found at path $path"
  }

  /**
   * A field with the given name already exists in the record at the given path.
   */
  final case class FieldAlreadyExists(path: DynamicOptic, fieldName: String) extends MigrationError {
    override def getMessage: String = s"Field '$fieldName' already exists at path $path"
  }

  /** The specified variant case was not found at the given path. */
  final case class CaseNotFound(path: DynamicOptic, caseName: String) extends MigrationError {
    override def getMessage: String = s"Case '$caseName' not found at path $path"
  }

  /** The value at the given path was not the expected type. */
  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    override def getMessage: String = s"Type mismatch at path $path: expected $expected, got $actual"
  }

  /** A value transformation failed at the given path. */
  final case class TransformFailed(path: DynamicOptic, details: String) extends MigrationError {
    override def getMessage: String = s"Transform failed at path $path: $details"
  }

  /** The given path could not be resolved in the value. */
  final case class InvalidPath(path: DynamicOptic, details: String) extends MigrationError {
    override def getMessage: String = s"Invalid path $path: $details"
  }

  /**
   * Schema conversion failed when converting the migrated DynamicValue back to
   * a typed value.
   */
  final case class SchemaConversionFailed(cause: SchemaError) extends MigrationError {
    override def getMessage: String = s"Schema conversion failed: ${cause.getMessage}"
  }
}
