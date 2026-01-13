package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding}

final case class BindingInstance[TC[_], T, A](binding: Binding[T, A], instance: Lazy[TC[A]])

object BindingInstance {
  implicit def hasBinding[TC[_]]: HasBinding[({ type F[A, B] = BindingInstance[TC, A, B] })#F] = {
    type F[A, B] = BindingInstance[TC, A, B]
    new HasBinding[F] {
      def binding[A, B](fa: F[A, B]): Binding[A, B] = fa.binding

      def updateBinding[A, B](fa: F[A, B], f: Binding[A, B] => Binding[A, B]): F[A, B] =
        fa.copy(binding = f(fa.binding))
    }
  }

  implicit def hasInstance[TC[_]]: HasInstance[({ type F[A, B] = BindingInstance[TC, A, B] })#F, TC] = {
    type F[A, B] = BindingInstance[TC, A, B]
    new HasInstance[F, TC] {
      def instance[A, B](fa: F[A, B]): Lazy[TC[B]] = fa.instance
    }
  }
}
