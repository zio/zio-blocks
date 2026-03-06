package zio.blocks.combinators

import scala.compiletime.constValue
import scala.util.NotGiven

object Tuples {

  type Flatten[T] <: Tuple = T match {
    case EmptyTuple => EmptyTuple
    case h *: t     =>
      h match {
        case Tuple => Tuple.Concat[Flatten[h & Tuple], Flatten[t]]
        case _     => h *: Flatten[t]
      }
    case _ => T *: EmptyTuple
  }

  type Combined[A, B] = Flatten[A *: B *: EmptyTuple]

  type Init[T <: Tuple] <: Tuple = T match {
    case x *: EmptyTuple => EmptyTuple
    case x *: xs         => x *: Init[xs]
  }

  type Last[T <: Tuple] = T match {
    case x *: EmptyTuple => x
    case x *: xs         => Last[xs]
  }

  trait Tuples[L, R] {
    type Out

    def combine(l: L, r: R): Out

    def separate(out: Out): (L, R)
  }

  object Tuples extends TuplesLowPriority {
    type WithOut[L, R, O] = Tuples[L, R] { type Out = O }

    given leftUnit[R]: WithOut[Unit, R, R] = new Tuples[Unit, R] {
      type Out = R
      def combine(l: Unit, r: R): R   = r
      def separate(out: R): (Unit, R) = ((), out)
    }

    given rightUnit[L]: WithOut[L, Unit, L] = new Tuples[L, Unit] {
      type Out = L
      def combine(l: L, r: Unit): L   = l
      def separate(out: L): (L, Unit) = (out, ())
    }

    given leftEmptyTuple[R]: WithOut[EmptyTuple, R, R] = new Tuples[EmptyTuple, R] {
      type Out = R
      def combine(l: EmptyTuple, r: R): R   = r
      def separate(out: R): (EmptyTuple, R) = (EmptyTuple, out)
    }

    given rightEmptyTuple[L]: WithOut[L, EmptyTuple, L] = new Tuples[L, EmptyTuple] {
      type Out = L
      def combine(l: L, r: EmptyTuple): L   = l
      def separate(out: L): (L, EmptyTuple) = (out, EmptyTuple)
    }

    inline given tupleTuple[L <: NonEmptyTuple, R <: NonEmptyTuple]: WithOut[L, R, Combined[L, R]] =
      TupleTupleInstance[L, R](constValue[Tuple.Size[Flatten[L]]])

    inline given tupleValue[L <: NonEmptyTuple, R](using
      NotGiven[R <:< Tuple]
    ): WithOut[L, R, Tuple.Concat[L, Tuple1[R]]] =
      TupleValueInstance[L, R](constValue[Tuple.Size[L]])

    given valueTuple[L, R <: NonEmptyTuple](using NotGiven[L <:< Tuple]): WithOut[L, R, Tuple.Concat[Tuple1[L], R]] =
      ValueTupleInstance[L, R]()

    private[combinators] class TupleTupleInstance[L <: NonEmptyTuple, R <: NonEmptyTuple](flatLeftSize: Int)
        extends Tuples[L, R] {
      type Out = Combined[L, R]

      def combine(left: L, right: R): Combined[L, R] =
        flattenTuple(left ++ right).asInstanceOf[Combined[L, R]]

      def separate(out: Combined[L, R]): (L, R) = {
        val t      = out.asInstanceOf[Tuple]
        val (l, r) = t.splitAt(flatLeftSize)
        (l.asInstanceOf[L], r.asInstanceOf[R])
      }
    }

    private[combinators] class TupleValueInstance[L <: NonEmptyTuple, R](leftSize: Int) extends Tuples[L, R] {
      type Out = Tuple.Concat[L, Tuple1[R]]

      def combine(left: L, right: R): Tuple.Concat[L, Tuple1[R]] =
        (left ++ Tuple1(right)).asInstanceOf[Tuple.Concat[L, Tuple1[R]]]

      def separate(out: Tuple.Concat[L, Tuple1[R]]): (L, R) = {
        val t      = out.asInstanceOf[Tuple]
        val (l, r) = t.splitAt(leftSize)
        (l.asInstanceOf[L], r.asInstanceOf[Tuple1[R]].head)
      }
    }

    private[combinators] class ValueTupleInstance[L, R <: NonEmptyTuple]() extends Tuples[L, R] {
      type Out = Tuple.Concat[Tuple1[L], R]

      def combine(left: L, right: R): Tuple.Concat[Tuple1[L], R] =
        (Tuple1(left) ++ right).asInstanceOf[Tuple.Concat[Tuple1[L], R]]

      def separate(out: Tuple.Concat[Tuple1[L], R]): (L, R) = {
        val t      = out.asInstanceOf[Tuple]
        val (l, r) = t.splitAt(1)
        (l.asInstanceOf[Tuple1[L]].head, r.asInstanceOf[R])
      }
    }
  }

  trait TuplesLowPriority {
    given fallback[L, R]: Tuples.WithOut[L, R, (L, R)] = new Tuples[L, R] {
      type Out = (L, R)
      def combine(l: L, r: R): (L, R)   = (l, r)
      def separate(out: (L, R)): (L, R) = out
    }
  }

  private def flattenTuple(t: Tuple): Tuple = t match {
    case EmptyTuple            => EmptyTuple
    case (head: Tuple) *: tail => flattenTuple(head) ++ flattenTuple(tail)
    case head *: tail          => head *: flattenTuple(tail)
  }

  def combine[L, R](l: L, r: R)(using t: Tuples[L, R]): t.Out = t.combine(l, r)

  /** Separates a combined tuple `out` back into its original left and right parts.
    *
    * @tparam L   the left component type before combining
    * @tparam R   the right component type before combining
    * @param t    the typeclass instance that knows how to separate `Out` back into `(L, R)`
    * @param out  the combined tuple value to separate
    * @return     a pair `(L, R)` recovered from the combined value
    */
  def separate[L, R](using t: Tuples[L, R])(out: t.Out): (L, R) = t.separate(out)
}
