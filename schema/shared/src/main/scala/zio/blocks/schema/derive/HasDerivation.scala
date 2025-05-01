package zio.blocks.schema

trait HasDerivation[F[_, _], TC[_]] {
  def instances[T, A](fa: F[T, A]): Instances[TC, A]
}
