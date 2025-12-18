package zio.blocks.schema.convert

import zio.blocks.schema.SchemaError

trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}

object Into extends IntoVersionSpecific {
  def apply[A, B](implicit ev: Into[A, B]): Into[A, B] = ev
}
