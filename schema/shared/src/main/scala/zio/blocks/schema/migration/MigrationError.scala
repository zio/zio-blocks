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

sealed trait MigrationError {
  def message: String
}

object MigrationError {
  final case class PathError(path: DynamicOptic, reason: String) extends MigrationError {
    def message: String = s"Failed to apply action at ${path}: $reason"
  }

  final case class PathNotFound(path: DynamicOptic) extends MigrationError {
    def message: String = s"Path not found: ${path}"
  }

  final case class EvaluationError(reason: String) extends MigrationError {
    def message: String = s"Evaluation error: $reason"
  }

  final case class Other(reason: String) extends MigrationError {
    def message: String = s"Migration error: $reason"
  }
}
