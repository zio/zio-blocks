package zio.blocks.schema

import zio.blocks.docs.Doc
import zio.blocks.schema.binding._

final case class Term[F[_, _], S, A](
  name: String,
  value: Reflect[F, A],
  doc: Doc = Doc.empty,
  modifiers: Seq[Modifier.Term] = Nil
) extends Reflectable[A] {
  require(value ne null)

  type Source = S
  type Focus  = A

  def transform[G[_, _]](path: DynamicOptic, termType: Term.Type, f: ReflectTransformer[F, G]): Lazy[Term[G, S, A]] =
    for {
      value <- value.transform(if (termType == Term.Type.Record) path.field(name) else path.caseOf(name), f)
    } yield new Term(name, value, doc, modifiers)

  override def toString: String = ReflectPrinter.printTerm(this)
}

object Term {
  sealed trait Type

  object Type {
    case object Record  extends Type
    case object Variant extends Type
  }

  type Bound[S, A] = Term[Binding, S, A]

  trait Updater[F[_, _]] {
    def update[S, A](input: Term[F, S, A]): Option[Term[F, S, A]]
  }
}
