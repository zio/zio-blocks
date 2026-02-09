package zio.blocks.schema.migration

/**
 * Scala 2 stub for `MigrationBuilderVersionSpecific`.
 *
 * Selector-based methods (e.g., `renameFieldS(_.name, _.fullName)`) are only
 * available on Scala 3 due to the use of inline macros. On Scala 2, use the
 * string-based methods (e.g., `renameField("name", "fullName")`).
 *
 * Note: The string-based API is fully functional and produces identical
 * migrations. The selector API is syntactic sugar for compile-time safety.
 */
trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>
  // Selector macros are only available on Scala 3.
  // Use the string-based API on Scala 2:
  //   .renameField("name", "fullName") instead of .renameFieldS(_.name, _.fullName)
}
