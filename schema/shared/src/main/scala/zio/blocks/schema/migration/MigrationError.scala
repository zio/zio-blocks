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

package zio.blocks.schema

import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * Failures from applying a migration. Uses [[SchemaError]], which attaches
 * [[DynamicOptic]] paths to each diagnostic line.
 */
package object migration {

  type MigrationError = SchemaError

  object MigrationError {

    def apply(message: String, path: DynamicOptic = DynamicOptic.root): MigrationError =
      SchemaError.message(message, path)
  }
}
