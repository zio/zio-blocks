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

  // Scala 2/3 specific derived implementations are provided in version-specific
  // source files. They materialize `ToStructural` instances that set a normalized
  // structural `TypeName` and return `Schema.dynamic` as the runtime binding.
  // Delegate a `derived` given into this companion so it is found during
  // implicit search without additional imports.
  inline given derived[A]: ToStructural.Aux[A, DynamicValue] =
    zio.blocks.schema.ToStructuralVersionSpecific.derived[A]
}

// Re-export Scala 3/2 version-specific materializers into the companion
// so they participate in implicit search. The version-specific files provide
// `derived` givens; delegate to them when available.
object ToStructuralVersionExports {
  inline given derivedExport[A]: ToStructural.Aux[A, DynamicValue] =
    zio.blocks.schema.ToStructuralVersionSpecific.derived[A]
}

export ToStructuralVersionExports.*
