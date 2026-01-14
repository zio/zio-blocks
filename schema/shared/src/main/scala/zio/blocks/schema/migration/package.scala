package zio.blocks.schema

/**
 * Package providing schema migration utilities.
 *
 * This package contains:
 *   - `Migration[A, B]`: Type-safe migration between schemas
 *   - `DynamicMigration`: Serializable migration on DynamicValue
 *   - `MigrationAction`: Individual migration operations
 *   - `MigrationExpr`: Serializable value transformations
 *
 * Example usage:
 * {{{
 * case class PersonV1(name: String, age: Int)
 * case class PersonV2(fullName: String, age: Int, email: String)
 *
 * val migration = Migration.from[PersonV1, PersonV2]
 *   .renameField("name", "fullName")
 *   .addField("email", "")
 *   .buildPartial()
 *
 * val v1 = PersonV1("John", 30)
 * val v2: Either[SchemaError, PersonV2] = migration(v1)
 * // Right(PersonV2("John", 30, ""))
 * }}}
 */
package object migration {}
