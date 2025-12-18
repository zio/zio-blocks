package zio.blocks.schema.convert

import zio.blocks.schema.SchemaError

trait As[A, B] {
  def into(input: A): Either[SchemaError, B]
  def from(input: B): Either[SchemaError, A]
}

