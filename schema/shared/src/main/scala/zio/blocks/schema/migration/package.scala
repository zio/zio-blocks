package zio.blocks.schema

/**
 * Schema migration system for ZIO Blocks.
 *
 * This package provides a pure, algebraic migration system for transforming
 * data between different schema versions. It supports:
 *
 *  - Type-safe migrations with compile-time validation
 *  - Fully serializable migration definitions
 *  - Bidirectional migrations (forward and reverse)
 *  - Composition of migrations
 *  - Rich set of migration operations (add, drop, rename, transform, etc.)
 *
 * == Basic Usage ==
 *
 * {{`
 * import zio.blocks.schema._
 * import zio.blocks.schema.migration._
 *
 * // Define your schema versions
 * case class PersonV1(name: String)
 * case class PersonV2(name: String, age: Int)
 *
 * implicit val v1Schema: Schema[PersonV1] = Schema.derived
 * implicit val v2Schema: Schema[PersonV2] = Schema.derived
 *
 * // Create a migration
 * val migration = Migration
 *   .newBuilder[PersonV1, PersonV2]
 *   .addField(_.age, 0)
 *   .build
 *
 * // Apply the migration
 * val personV1 = PersonV1("John")
 * val result: Either[MigrationError, PersonV2] = migration(personV1)
 * // result == Right(PersonV2("John", 0))
 * `}}
 *
 * == Composition ==
 *
 * Migrations can be composed sequentially:
 *
 * {{`
 * val v1ToV2 = Migration.newBuilder[PersonV1, PersonV2].addField(_.age, 0).build
 * val v2ToV3 = Migration.newBuilder[PersonV2, PersonV3].addField(_.email, "").build
 *
 * val v1ToV3 = v1ToV2 ++ v2ToV3
 * `}}
 *
 * == Reversibility ==
 *
 * Migrations support reversal for bidirectional transformations:
 *
 * {{`
 * val v1ToV2 = Migration.newBuilder[PersonV1, PersonV2].addField(_.age, 0).build
 * val v2ToV1 = v1ToV2.reverse
 * `}}
 *
 * == Available Operations ==
 *
 *  - `addField` - Add a new field with a default value
 *  - `dropField` - Remove a field
 *  - `renameField` - Rename a field
 *  - `transformField` - Transform a field's value
 *  - `mandateField` - Make an optional field mandatory
 *  - `optionalizeField` - Make a mandatory field optional
 *  - `changeFieldType` - Change a field's type
 *  - `joinFields` - Join multiple fields into one
 *  - `splitField` - Split a field into multiple fields
 *  - `renameCase` - Rename an enum case
 *  - `transformCase` - Transform a specific enum case
 *  - `transformElements` - Transform collection elements
 *  - `transformKeys` - Transform map keys
 *  - `transformValues` - Transform map values
 */
package object migration {

  /**
   * Type alias for migration errors.
   */
  type MigrationResult[A] = Either[MigrationError, A]

  /**
   * Implicit class for convenient migration syntax.
   */
  implicit class MigrationOps[A](val value: A) extends AnyVal {

    /**
     * Applies a migration to this value.
     */
    def migrateTo[B](migration: Migration[A, B]): MigrationResult[B] =
      migration(value)
  }
}
