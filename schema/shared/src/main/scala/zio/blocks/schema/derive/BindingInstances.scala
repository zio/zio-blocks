package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding}

final case class BindingInstances[TC[_], T, A](binding: Binding[T, A], instances: Instances[TC, A])

object BindingInstances {
  implicit def hasBinding[TC[_]]: HasBinding[({ type F[A, B] = BindingInstances[TC, A, B] })#F] = {
    type F[A, B] = BindingInstances[TC, A, B]
    new HasBinding[F] {
      def binding[A, B](fa: F[A, B]): Binding[A, B] = fa.binding

      def updateBinding[A, B](fa: F[A, B], f: Binding[A, B] => Binding[A, B]): F[A, B] =
        fa.copy(binding = f(fa.binding))
    }
  }

  implicit def hasInstances[TC[_]]: HasInstances[({ type F[A, B] = BindingInstances[TC, A, B] })#F, TC] = {
    type F[A, B] = BindingInstances[TC, A, B]
    new HasInstances[F, TC] {
      def instances[A, B](fa: F[A, B]): Instances[TC, B] = fa.instances

      def updateInstances[A, B](fa: F[A, B], f: Instances[TC, B] => Instances[TC, B]): F[A, B] =
        fa.copy(instances = f(fa.instances))
    }
  }
}
