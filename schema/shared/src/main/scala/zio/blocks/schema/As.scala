package zio.blocks.schema

trait As[A, B] {
  def into(value: A): Either[SchemaError, B]
  def from(value: B): Either[SchemaError, A]
}

object As {
  def apply[A, B](implicit as: As[A, B]): As[A, B] = as

  implicit def derived[A, B](implicit schemaA: Schema[A], schemaB: Schema[B]): As[A, B] =
    new As[A, B] {
      def into(value: A): Either[SchemaError, B] =
        schemaA.toDynamicValue(value) match {
          case dv => schemaB.fromDynamicValue(dv)
        }

      def from(value: B): Either[SchemaError, A] =
        schemaB.toDynamicValue(value) match {
          case dv => schemaA.fromDynamicValue(dv)
        }
    }
}
