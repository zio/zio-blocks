package zio.blocks.schema

import zio.blocks.schema.binding.Binding

trait SchemaAspect[-Upper, +Lower, F[_, _]] {
  def apply[A >: Lower <: Upper](reflect: Reflect[F, A]): Reflect[F, A]

  def recursive(implicit ev1: Any <:< Upper, ev2: Lower <:< Nothing): SchemaAspect[Upper, Lower, F]
}

object SchemaAspect {
  val identity: SchemaAspect[Any, Nothing, Binding] = new SchemaAspect[Any, Nothing, Binding] {
    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect

    def recursive(implicit ev1: Any <:< Any, ev2: Nothing <:< Nothing): SchemaAspect[Any, Nothing, Binding] = identity
  }

  def doc(value: String): SchemaAspect[Any, Nothing, Binding] = new SchemaAspect[Any, Nothing, Binding] {
    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect.doc(value)

    def recursive(implicit ev1: Any <:< Any, ev2: Nothing <:< Nothing): SchemaAspect[Any, Nothing, Binding] = doc(value)
  }

  def examples[A0](value: A0, values: A0*): SchemaAspect[A0, A0, Binding] = new SchemaAspect[A0, A0, Binding] {
    def apply[A >: A0 <: A0](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect.examples(value, values: _*)

    def recursive(implicit ev1: Any <:< A0, ev2: A0 <:< Nothing): SchemaAspect[A0, A0, Binding] =
      examples(value, values)
  }
}
