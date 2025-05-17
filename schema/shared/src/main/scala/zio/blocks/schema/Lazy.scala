package zio.blocks.schema

import scala.reflect.ClassTag

sealed trait Lazy[+A] {
  import Lazy.{Cont, Defer, FlatMap}

  private var value: Any       = null.asInstanceOf[Any]
  private var error: Throwable = null.asInstanceOf[Throwable]

  final def as[B](b: => B): Lazy[B] = map(_ => b)

  final def catchAll[A1 >: A](f: Throwable => Lazy[A1]): Lazy[A1] =
    FlatMap[A, A1](this, Cont(Lazy(_), f))

  final def ensuring(finalizer: Lazy[Any]): Lazy[A] =
    FlatMap[A, A](this, Cont(a => finalizer.map(_ => a), e => finalizer.map(_ => throw e)))

  final def flatMap[B](f: A => Lazy[B]): Lazy[B] = FlatMap(this, Cont(f))

  final def flatten[B](implicit ev: A <:< Lazy[B]): Lazy[B] = flatMap(a => a)

  final def force: A = {
    @annotation.tailrec
    def loop(current: Lazy[Any], stack: List[Cont[Any, Any]]): Any = current match {
      case Defer(thunk) =>
        if (stack.isEmpty) thunk()
        else {
          val result =
            try Right(thunk())
            catch { case e: Throwable => Left(e) }

          result match {
            case Right(value) => loop(stack.head.ifSuccess(value), stack.tail)
            case Left(error)  => loop(stack.head.ifError(error), stack.tail)
          }
        }
      case FlatMap(first, k) =>
        loop(first, k.erase :: stack)
    }

    if (value == null) {
      if (error == null) {
        try {
          value = loop(this, Nil)

          value.asInstanceOf[A]
        } catch {
          case e: Throwable =>
            error = e
            throw e
        }
      } else {
        throw error
      }
    } else {
      value.asInstanceOf[A]
    }
  }

  final def isEvaluated: Boolean = !(value == null && error == null)

  final def map[B](f: A => B): Lazy[B] = flatMap(a => Lazy(f(a)))

  override final def equals(that: Any): Boolean = that match {
    case other: Lazy[A] @unchecked => other.force == force
    case _                         => false
  }

  override final def hashCode: Int = force.hashCode

  override final def toString: String =
    if (isEvaluated) s"Lazy($value)"
    else s"Lazy(<not evaluated>)"

  final def unit: Lazy[Unit] = as(())

  final def zip[B](that: Lazy[B]): Lazy[(A, B)] = flatMap(a => that.map(b => (a, b)))
}

object Lazy {
  private final case class Cont[-A, +B](ifSuccess: A => Lazy[B], ifError: Throwable => Lazy[B]) {
    def erase: Cont[Any, Any] = this.asInstanceOf[Cont[Any, Any]]
  }
  private object Cont {
    def apply[A, B](ifSuccess: A => Lazy[B]): Cont[A, B] = Cont(ifSuccess, fail)
  }

  private final case class Defer[+A](thunk: () => A)                        extends Lazy[A]
  private final case class FlatMap[A, +B](first: Lazy[A], cont: Cont[A, B]) extends Lazy[B]

  def apply[A](expression: => A): Lazy[A] = Defer(() => expression)

  def collectAll[A](values: List[Lazy[A]]): Lazy[List[A]] =
    values.foldRight(Lazy(List[A]()))((lazyValue, lazyResult) =>
      lazyValue.flatMap(value => lazyResult.map(values => value :: values))
    )

  def collectAll[A](values: Vector[Lazy[A]]): Lazy[Vector[A]] =
    values.foldLeft(Lazy(Vector[A]()))((lazyResult, lazyValue) =>
      lazyValue.flatMap(value => lazyResult.map(values => values :+ value))
    )

  def collectAll[A: ClassTag](values: Array[Lazy[A]]): Lazy[Array[A]] =
    values.foldLeft(Lazy(Array.empty[A]))((lazyResult, lazyValue) =>
      lazyValue.flatMap(value => lazyResult.map(values => values :+ value))
    )

  def collectAll[A](values: Set[Lazy[A]]): Lazy[Set[A]] =
    values.foldLeft(Lazy(Set[A]()))((lazyResult, lazyValue) =>
      lazyValue.flatMap(value => lazyResult.map(values => values + value))
    )

  def fail(throwable: Throwable): Lazy[Nothing] =
    Defer(() => throw throwable)

  def foreach[A, B](values: List[A])(f: A => Lazy[B]): Lazy[List[B]] =
    collectAll(values.map(f))

  def foreach[A, B](values: Vector[A])(f: A => Lazy[B]): Lazy[Vector[B]] =
    collectAll(values.map(f))

  def foreach[A: ClassTag, B: ClassTag](values: Array[A])(f: A => Lazy[B]): Lazy[Array[B]] =
    collectAll(values.map(f))

  def foreach[A, B](values: Set[A])(f: A => Lazy[B]): Lazy[Set[B]] =
    collectAll(values.map(f))
}
