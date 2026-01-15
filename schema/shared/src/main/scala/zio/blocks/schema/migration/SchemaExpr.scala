package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}

sealed trait SchemaExpr[A]

object SchemaExpr {

  case class DefaultValue[A](schema: Schema[A]) extends SchemaExpr[A]

  case class Constant[A](value: DynamicValue) extends SchemaExpr[A]

  case class Identity[A]() extends SchemaExpr[A]

  case class Converted[A, B](
    operand: SchemaExpr[A],
    f: A => Either[String, B],
    g: B => Either[String, A]
  ) extends SchemaExpr[B]

}
