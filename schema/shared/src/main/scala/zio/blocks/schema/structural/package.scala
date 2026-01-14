package zio.blocks.schema

/**
 * Structural schemas support for ZIO Schema.
 *
 * This package provides the ability to convert nominal type schemas (like case
 * classes) to structural equivalents, and to derive schemas directly from
 * structural types.
 *
 * ==Overview==
 *
 * Structural types represent the "shape" of a type without its nominal
 * identity. For example, two case classes with the same fields but different
 * names would have the same structural type.
 *
 * ==Usage==
 *
 * {{{
 * import zio.blocks.schema._
 * import zio.blocks.schema.structural._
 *
 * case class Person(name: String, age: Int)
 * object Person {
 *   implicit val schema: Schema[Person] = Schema.derived
 * }
 *
 * // Convert to structural representation
 * val person = Person("Alice", 30)
 * val structural = person.toStructural
 *
 * // Get structural type name
 * println(structural.typeName) // {age:Int,name:String}
 * }}}
 *
 * ==Limitations==
 *
 *   - Sum types (sealed traits, enums) can only be converted to structural
 *     types in Scala 3
 *   - Recursive types cannot be converted to structural types
 *   - Structural types use alphabetically sorted field names for deterministic
 *     representation
 */
package object structural {

  /**
   * Extension methods for Schema to support structural conversion.
   */
  implicit class SchemaStructuralOps[A](private val schema: Schema[A]) extends AnyVal {

    /**
     * Get the structural type name for this schema.
     */
    def structuralTypeName(implicit ts: ToStructural[A]): String = ts.structuralTypeName

    /**
     * Get a ToStructural instance for this schema's type.
     */
    def toStructuralInstance(implicit ts: ToStructural[A]): ToStructural[A] = ts
  }

  /**
   * Extension methods for values to convert to structural representation.
   */
  implicit class StructuralValueOps[A](private val value: A) extends AnyVal {

    /**
     * Convert this value to its structural representation.
     */
    def toStructural(implicit ts: ToStructural[A]): StructuralValue = ts.toStructural(value)
  }
}
