package zio.blocks.schema

trait Into[A, B] {
  def into(value: A): Either[SchemaError, B]
}

object Into {
  def apply[A, B](implicit into: Into[A, B]): Into[A, B] = into

  implicit def derived[A, B](implicit schemaA: Schema[A], schemaB: Schema[B]): Into[A, B] =
    new Into[A, B] {
      def into(value: A): Either[SchemaError, B] =
        schemaA.toDynamicValue(value) match {
          case dv => schemaB.fromDynamicValue(dv)
        }
    }
}
