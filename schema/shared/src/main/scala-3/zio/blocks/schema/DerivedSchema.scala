package zio.blocks.schema

trait DerivedSchema[A] {
  /**
   * Derives a Schema for the type A.
   * This is defined as an inline implicit def to allow automatic derivation
   * when the trait is mixed into a companion object.
   */
  inline implicit def schema: Schema[A] = Schema.derived[A]
}
