package zio.blocks.schema

import scala.util.control.NonFatal

sealed trait Lazy[+A] {
  import Lazy._

  private[this] var value: Any       = null.asInstanceOf[Any]
  private[this] var error: Throwable = null

  @inline final def as[B](b: => B): Lazy[B] = map(_ => b)

  final def catchAll[B >: A](f: Throwable => Lazy[B]): Lazy[B] =
    new FlatMap[A, B](this, new Cont(a => new Defer(() => a), f))

  final def ensuring(finalizer: Lazy[Any]): Lazy[A] =
    new FlatMap[A, A](
      this,
      new Cont(
        a =>
          new Defer({ () =>
            finalizer.force: Unit
            a
          }),
        e =>
          new Defer({ () =>
            finalizer.force: Unit
            throw e
          })
      )
    )

  final def flatMap[B](f: A => Lazy[B]): Lazy[B] = new FlatMap(this, new Cont(f, e => new Defer(() => throw e)))

  @inline final def flatten[B](implicit ev: A <:< Lazy[B]): Lazy[B] = flatMap(a => a)

  final def force: A = {
    @annotation.tailrec
    def loop(current: Lazy[Any], stack: List[Cont[Any, Any]]): Any = current match {
      case Defer(thunk) =>
        if (stack.isEmpty) thunk()
        else {
          val cont = stack.head
          loop(
            try cont.ifSuccess(thunk())
            catch { case e if NonFatal(e) => cont.ifError(e) },
            stack.tail
          )
        }
      case FlatMap(first, cont) =>
        loop(first, cont.asInstanceOf[Cont[Any, Any]] :: stack)
    }

    (if (value == null) {
       if (error ne null) throw error
       try {
         value = loop(this, Nil)
         value
       } catch {
         case e if NonFatal(e) =>
           error = e
           throw e
       }
     } else value)
    .asInstanceOf[A]
  }

  final def isEvaluated: Boolean = value != null || (error ne null)

  final def map[B](f: A => B): Lazy[B] = flatMap(a => new Defer(() => f(a)))

  @inline final def unit: Lazy[Unit] = map(_ => ())

  final def zip[B](that: Lazy[B]): Lazy[(A, B)] = flatMap(a => that.map(b => (a, b)))

  override final def equals(that: Any): Boolean = that match {
    case other: Lazy[A] @unchecked => other.force == force
    case _                         => false
  }

  override final def hashCode: Int = force.hashCode

  override final def toString: String =
    if (isEvaluated) s"Lazy($value)"
    else "Lazy(<not evaluated>)"
}

object Lazy {
  private case class Cont[-A, +B](ifSuccess: A => Lazy[B], ifError: Throwable => Lazy[B])

  private case class Defer[+A](thunk: () => A) extends Lazy[A]

  private case class FlatMap[A, +B](first: Lazy[A], cont: Cont[A, B]) extends Lazy[B]

  @inline def apply[A](expression: => A): Lazy[A] = new Defer(() => expression)

  def collectAll[A](values: IndexedSeq[Lazy[A]]): Lazy[IndexedSeq[A]] =
    values
      .foldLeft(Lazy(IndexedSeq.newBuilder[A]))((lazyResult, lazyValue) =>
        lazyValue.flatMap(value => lazyResult.map(_.addOne(value)))
      )
      .map(_.result())

  @inline def fail(throwable: Throwable): Lazy[Nothing] = new Defer(() => throw throwable)

  def foreach[A, B](values: IndexedSeq[A])(f: A => Lazy[B]): Lazy[IndexedSeq[B]] = collectAll(values.map(f))
}
