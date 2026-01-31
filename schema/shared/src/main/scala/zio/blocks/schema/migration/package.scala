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

/**
 * Schema Migration System for ZIO Schema.
 *
 * This package provides a pure, algebraic migration system that represents
 * structural transformations between schema versions as first-class,
 * serializable data.
 *
 * ==Key Features==
 *
 *   - '''Pure Data Migrations''': All migrations are represented as case
 *     classes with no closures, making them fully serializable.
 *   - '''Hierarchical Actions''': Supports nested migrations via AtField,
 *     AtCase, AtElements, enabling migrations like
 *     `_.address.street -> _.address.streetName`.
 *   - '''Bidirectional''': Every migration has a structural reverse.
 *   - '''Introspectable''': Migration logic can be inspected, transformed, and
 *     used to generate DDL, upgraders, or downgraders.
 *
 * ==Architecture==
 *
 * The system has two layers:
 *
 *   - '''DynamicMigration''': Untyped, fully serializable core that operates on
 *     DynamicValue.
 *   - '''Migration[A, B]''': Typed wrapper with schemas for compile-time
 *     safety.
 *
 * ==Example Usage==
 *
 * {{{
 * import zio.blocks.schema.migration._
 *
 * // Define schemas (structural or concrete)
 * case class PersonV1(firstName: String, lastName: String)
 * case class PersonV2(fullName: String, age: Int)
 *
 * implicit val v1Schema: Schema[PersonV1] = Schema.derived
 * implicit val v2Schema: Schema[PersonV2] = Schema.derived
 *
 * // Build a migration
 * val migration = MigrationBuilder[PersonV1, PersonV2]
 *   .joinFields(Vector("firstName", "lastName"), "fullName",
 *     ResolvedExpr.concatWith(" ")(
 *       ResolvedExpr.field("firstName"),
 *       ResolvedExpr.field("lastName")
 *     ))
 *   .addFieldInt("age", 0)
 *   .build
 *
 * // Apply migration
 * val v1 = PersonV1("John", "Doe")
 * val v2 = migration(v1) // Right(PersonV2("John Doe", 0))
 *
 * // Reverse migration
 * val v1Again = migration.reverse(v2.toOption.get)
 * }}}
 *
 * ==Nested Migrations==
 *
 * For nested structures, use `atField`:
 *
 * {{{
 * val migration = MigrationBuilder[AddressV1, AddressV2]
 *   .atField("address")(
 *     _.renameField("street", "streetName")
 *      .addFieldString("zipCode", "00000")
 *   )
 *   .build
 * }}}
 */
package object migration
