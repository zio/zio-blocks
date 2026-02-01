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

package zio.blocks.schema

import zio.blocks.schema.migration.*

/**
 * Package-level extensions for the migration API.
 *
 * Provides the `typed` entry point for creating migrations with:
 *   - Direct selector syntax: `_.field.nested`
 *   - Compile-time field tracking validation
 *   - No DynamicOptic exposure in the public API
 */
package object migration {

  /**
   * Extension methods for Migration companion object.
   */
  extension (m: Migration.type) {

    /**
     * Start building a type-safe migration with selector-based API.
     *
     * This is the recommended entry point for creating migrations. It provides:
     *   - Direct nested selector support: `_.address.street`
     *   - Compile-time field tracking and validation
     *   - No exposure of internal DynamicOptic type
     *
     * Example:
     * {{{
     *   Migration.typed[PersonV0, PersonV1]
     *     .renameField(_.firstName, _.fullName)
     *     .addField(_.age, 0)
     *     .keepField(_.lastName, _.lastName)
     *     .build  // Validates all fields are handled
     * }}}
     *
     * For nested migrations with direct path syntax:
     * {{{
     *   Migration.typed[WithAddressV0, WithAddressV1]
     *     .keepField(_.name, _.name)
     *     .renameField(_.address.street, _.address.streetName)
     *     .addField(_.address.zipCode, "00000")
     *     .keepField(_.address.city, _.address.city)
     *     .build
     * }}}
     *
     * @tparam A
     *   Source type
     * @tparam B
     *   Target type
     * @return
     *   A TypedMigrationBuilder for constructing the migration
     */
    def typed[A, B](using
      sourceSchema: Schema[A],
      targetSchema: Schema[B]
    ): TypedMigrationBuilder[A, B] =
      TypedMigrationBuilder[A, B]
  }
}
