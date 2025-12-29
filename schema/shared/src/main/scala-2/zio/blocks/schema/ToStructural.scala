package zio.blocks.schema

/**
 * Typeclass to convert a nominal `Schema[A]` into a schema for a structural
 * representation `S`.
 */
trait ToStructural[A] {
  type StructuralType
  def apply(schema: Schema[A]): Schema[StructuralType]
}

trait LowPriorityToStructural {
  implicit def fallbackToDynamic[A]: ToStructural.Aux[A, DynamicValue] = new ToStructural[A] {
    type StructuralType = DynamicValue
    def apply(schema: Schema[A]): Schema[DynamicValue] = Schema.dynamic
  }
}

object ToStructural extends LowPriorityToStructural {
  type Aux[A, S] = ToStructural[A] { type StructuralType = S }

  // Scala 2 materializers are provided in `ToStructuralVersionSpecific`.
  // We intentionally do not declare a fallback `derived` here so that the
  // macro-generated implicit in `ToStructuralVersionSpecific` can be used
  // by implicit search without introducing ordering issues.
}
