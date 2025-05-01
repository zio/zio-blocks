package zio.blocks.schema

import zio.blocks.schema.binding._

final case class Term[F[_, _], S, A](name: String, value: Reflect[F, A], doc: Doc, modifiers: Seq[Modifier.Term])
    extends Reflectable[A] { self =>
  def refineBinding[G[_, _]](f: RefineBinding[F, G]): Term[G, S, A] = Term(name, value.refineBinding(f), doc, modifiers)
}

object Term {
  type Bound[S, A] = Term[Binding, S, A]

  trait Updater[F[_, _]] {
    def update[S, A](input: Term[F, S, A]): Option[Term[F, S, A]]
  }
}
