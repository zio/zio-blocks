package zio.blocks.schema.derive

import zio.blocks.schema.Lazy

trait HasInstance[F[_, _], TC[_]] {
  def instance[T, A](fa: F[T, A]): Lazy[TC[A]]
}
