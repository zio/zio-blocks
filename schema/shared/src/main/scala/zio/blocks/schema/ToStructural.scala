package zio.blocks.schema

trait ToStructural[A] {

  type StructuralType

  def toStructural(value: A): StructuralType

  def structuralSchema(implicit schema: Schema[A]): Schema[StructuralType]
}

object ToStructural extends ToStructuralVersionSpecific {
  def apply[A](implicit ts: ToStructural[A]): ToStructural[A] = ts
}
