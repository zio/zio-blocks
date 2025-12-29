package zio.blocks.schema

object StructuralConversions {
  /** Return the structural schema for `A` (runtime: `DynamicValue`). */
  def structuralSchema[A](using schema: Schema[A]): Schema[DynamicValue] = schema.structural

  /** Convert a nominal value to its structural `DynamicValue`. */
  def fromNominal[A](value: A)(using schema: Schema[A]): DynamicValue = schema.toDynamicValue(value)

  /** Convert a structural `DynamicValue` to nominal `A` using `Schema[A]`. */
  def toNominal[A](dv: DynamicValue)(using schema: Schema[A]): Either[SchemaError, A] = schema.fromDynamicValue(dv)
}
