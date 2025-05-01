package zio.blocks.schema.derive

trait HasInstances[F[_, _], TC[_]] {
  def instances[T, A](fa: F[T, A]): Instances[TC, A]

  def updateInstances[T, A](fa: F[T, A], f: Instances[TC, A] => Instances[TC, A]): F[T, A]
}
