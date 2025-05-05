package zio.blocks.schema

trait IsCollection[A] {
  type Collection[_]
  type Elem

  def proof: Collection[Elem] =:= A
}

object IsCollection {
  type Typed[A, C0[_], Elem0] = IsCollection[A] {
    type Collection[X] = C0[X]
    type Elem          = Elem0
  }

  implicit def isCollection[C[_], A]: IsCollection.Typed[C[A], C, A] =
    new IsCollection[C[A]] {
      type Collection[X] = C[X]
      type Elem          = A

      def proof: Collection[Elem] =:= C[A] =
        implicitly[Collection[Elem] =:= C[A]]
    }
}
