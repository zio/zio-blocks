package zio.blocks.schema.migration

import zio.blocks.schema.PrimitiveValue

sealed trait SchemaExpr[-A, +B] {
  def apply(value: A): Either[MigrationError, B]
}

object SchemaExpr {
  final case class Constant[B](value: B) extends SchemaExpr[Any, B] {
    def apply(value: Any): Either[MigrationError, B] = Right(this.value)
  }

  final case class DefaultValue[B]() extends SchemaExpr[Any, B] {
    def apply(value: Any): Either[MigrationError, B] = Left(MigrationError.NotYetImplemented) // Needs schema access
  }
  
  final case class StringTo[B](fromString: String => Either[String, B]) extends SchemaExpr[String, B] {
      def apply(value: String): Either[MigrationError, B] = fromString(value).left.map(MigrationError.ConversionError)
  }
}
