package golem.config

import zio.blocks.schema.Schema

final class Secret[T](private[golem] val path: List[String], private[golem] val loader: () => T) {
  def get: T = loader()
}

object Secret {
  implicit def schema[A](implicit underlying: Schema[A]): Schema[Secret[A]] =
    underlying.transform[Secret[A]](
      a => new Secret[A](Nil, () => a),
      _.get
    )
}
