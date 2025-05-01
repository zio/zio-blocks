package zio.blocks.schema

sealed trait Lazy[+A] {
  import Lazy.{Defer, FlatMap}

  private var value: Any = null.asInstanceOf[Any]

  def flatMap[B](f: A => Lazy[B]): Lazy[B] = FlatMap(this, f)

  def force: A = {
    @annotation.tailrec
    def loop(current: Lazy[Any], stack: List[Any => Lazy[Any]]): Any = current match {
      case Defer(thunk) =>
        if (stack.isEmpty) thunk()
        else loop(stack.head(thunk()), stack.tail)
      case FlatMap(first, andThen) =>
        loop(first, andThen.asInstanceOf[Any => Lazy[Any]] :: stack)
    }

    if (value == null) {
      value = loop(this, Nil).asInstanceOf[A]
    }

    value.asInstanceOf[A]
  }

  def isEvaluated: Boolean = value != null

  def map[B](f: A => B): Lazy[B] = flatMap(a => Defer(() => f(a)))

  override def equals(that: Any): Boolean = that match {
    case other: Lazy[_] => force == other.force
    case _              => false
  }

  override def hashCode(): Int = force.hashCode()

  override def toString: String =
    if (isEvaluated) s"Lazy($value)"
    else s"Lazy(<not evaluated>)"
}

object Lazy {
  private final case class Defer[+A](thunk: () => A)                             extends Lazy[A]
  private final case class FlatMap[A, +B](first: Lazy[A], andThen: A => Lazy[B]) extends Lazy[B]

  def apply[A](expression: => A): Lazy[A] = Defer(() => expression)
}
