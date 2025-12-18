package zio.blocks.schema

trait ToStructuralVersionSpecific {
  inline def derived[A]: ToStructural[A] = ${ DeriveToStructural.derivedImpl[A] }
}
