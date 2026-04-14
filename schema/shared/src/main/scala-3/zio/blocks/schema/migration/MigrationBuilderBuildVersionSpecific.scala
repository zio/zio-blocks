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

import scala.quoted.*

/**
 * Scala 3 version-specific implementation of [[MigrationBuilder.build]].
 *
 * This delegates to [[MigrationBuilderMacros]] which validates the builder's
 * type-level `Actions` state (a tuple) at compile time. This approach is robust
 * to `val` extraction because it does not inspect the builder call chain.
 */
trait MigrationBuilderBuildVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>
  inline def build: Migration[A, B] =
    ${ MigrationBuilderMacros.validateAndBuild[A, B, self.Actions]('{ this }) }
}
