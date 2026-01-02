package zio.blocks.schema

/**
 * Type class for converting nominal schemas to structural schemas.
 *
 * Given a Schema[A] for a nominal type like a case class, this type class
 * generates the corresponding structural type and provides a conversion method.
 *
 * Example:
 * {{{
 * case class Person(name: String, age: Int)
 *
 * val nominalSchema: Schema[Person] = Schema.derived[Person]
 * val structuralSchema: Schema[{ def name: String; def age: Int }] = nominalSchema.structural
 * }}}
 *
 * Note: This is JVM-only due to reflection requirements for structural types.
 */
trait ToStructural[A] {
  type StructuralType
  def apply(schema: Schema[A]): Schema[StructuralType]
}

object ToStructural extends ToStructuralVersionSpecific {
  type Aux[A, S] = ToStructural[A] { type StructuralType = S }
}
