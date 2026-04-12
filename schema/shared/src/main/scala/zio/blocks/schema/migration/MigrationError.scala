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
 * An error that can occur while applying a schema migration to a
 * [[zio.blocks.schema.DynamicValue]].
 *
 * Errors carry the [[zio.blocks.schema.DynamicOptic]] path where the failure
 * occurred (or was detected).
 */
sealed trait MigrationError extends Product with Serializable

/** The migration expected a record field to exist, but it was not present. */
final case class FieldNotFound(path: DynamicOptic, field: String) extends MigrationError

/** The migration reached a value of an unexpected dynamic type. */
final case class TypeMismatch(path: DynamicOptic, expected: String, got: String) extends MigrationError

/** The migration failed for a domain-specific reason at a particular path. */
final case class MigrationFailed(path: DynamicOptic, cause: String) extends MigrationError
