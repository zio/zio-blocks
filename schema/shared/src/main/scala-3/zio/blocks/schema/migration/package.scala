package zio.blocks.schema

/**
 * Migration package provides tools for type-safe schema migrations.
 *
 * The main entry points are:
 *   - `select[S](_.field)` - Create a field selector for use with
 *     MigrationBuilder
 *   - `Migration[A, B]` - A typed migration between two schema versions
 *   - `MigrationBuilder` - Builder for constructing migrations with
 *     compile-time validation
 *
 * Example:
 * {{{
 * import zio.blocks.schema.migration._
 *
 * case class PersonV1(name: String)
 * case class PersonV2(fullName: String, age: Int)
 *
 * val migration = MigrationBuilder[PersonV1, PersonV2]
 *   .renameField(select[PersonV1](_.name), select[PersonV2](_.fullName))
 *   .addField(select[PersonV2](_.age), 0)
 *   .build
 * }}}
 */
package object migration {

  /**
   * Create a field selector from a lambda expression.
   *
   * The selector extracts the field name at compile time and captures it as a
   * singleton string type, enabling type-level field tracking.
   *
   * Example:
   * {{{
   * val nameSelector = select[Person](_.name)
   * // Type: FieldSelector[Person, String, "name"]
   * }}}
   *
   * @tparam S
   *   The schema/record type to select from
   * @return
   *   A SelectBuilder that can be applied to a field accessor lambda
   */
  inline def select[S]: SelectorMacros.SelectBuilder[S] = SelectorMacros.select[S]

  /**
   * Derive a [[SchemaFields]] instance for type A.
   *
   * This extracts field names from the schema at compile time, enabling full
   * compile-time validation of migration completeness.
   *
   * Example:
   * {{{
   * case class Person(name: String, age: Int)
   * val fields = schemaFields[Person]
   * // fields.fieldNames == List("name", "age")
   * }}}
   */
  inline given schemaFields[A]: SchemaFields[A] =
    SchemaFieldsMacros.derived[A]

  /**
   * Create a [[SchemaFields]] from the type structure (case class fields).
   *
   * This extracts field names directly from the type without requiring a
   * Schema.
   */
  inline def schemaFieldsFromType[A]: SchemaFields[A] =
    SchemaFieldsMacros.fromType[A]
}
