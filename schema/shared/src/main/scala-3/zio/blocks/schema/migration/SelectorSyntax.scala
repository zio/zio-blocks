package zio.blocks.schema.migration

/**
 * SelectorSyntax (Scala 3) ------------------------ Provides the extension
 * methods '.each' and '.when' for the migration DSL. These are strictly used
 * for compile-time inspection by AccessorMacros.
 */
object SelectorSyntax {

  /**
   * Collection Traversal Syntax Usage: _.addresses.each.street
   */
  extension [A](self: Iterable[A]) {
    def each: A = ??? // Marker for macros
  }

  /**
   * Sum Type / Enum Case Selection Syntax Usage: _.country.when[UK]
   */
  extension [A](self: A) {
    def when[Sub <: A]: Sub = ??? // Marker for macros
  }
}
