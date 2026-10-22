/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.blocks.chunk.ChunkBuilder
import scala.util.control.NonFatal

/**
 * A `Lazy[A]` is a computation of an `A` that runs when `force` is first
 * called. The schema module uses it where parts of a structure must refer to
 * parts that are not built yet, as in a recursive type: the methods of a
 * [[ReflectTransformer]] return their nodes as `Lazy` values, and type class
 * derivation pairs the binding of each node with a `Lazy` instance.
 *
 * `map`, `flatMap` and the other combinators build a new `Lazy` without running
 * anything. `force` runs the whole chain with an explicit stack, so a long
 * chain does not overflow the stack, and caches the result, or the exception,
 * in the `Lazy` it is called on. The cache belongs to that instance: a `Lazy`
 * that is part of another one's chain runs its computation again when the other
 * one is forced, even if it was forced before.
 *
 * {{{
 * var evaluations = 0
 * val title: Lazy[String] = Lazy {
 *   evaluations += 1
 *   "Zionomicon"
 * }
 *
 * val before = title.isEvaluated // false
 * val first  = title.force       // Zionomicon
 * val second = title.force       // Zionomicon
 * val after  = (title.isEvaluated, evaluations) // (true,1)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/lazy/ Lazy]]
 */
sealed trait Lazy[+A] {
  import Lazy._

  private[this] var value: Any       = null.asInstanceOf[Any]
  private[this] var error: Throwable = null

  /**
   * Returns a `Lazy` that runs this one, discards its result and evaluates `b`.
   */
  @inline final def as[B](b: => B): Lazy[B] = map(_ => b)

  /**
   * Returns a `Lazy` that runs this one and, when it throws a non-fatal
   * exception, runs the `Lazy` that `f` returns for that exception instead.
   */
  final def catchAll[B >: A](f: Throwable => Lazy[B]): Lazy[B] =
    new FlatMap[A, B](this, new Cont(a => new Defer(() => a), f))

  /**
   * Returns a `Lazy` that runs this one and then forces `finalizer`, whether
   * this one returns or throws a non-fatal exception. The exception is thrown
   * again after `finalizer` runs; an exception from `finalizer` replaces it.
   */
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

  /**
   * Returns a `Lazy` that runs this one, then the `Lazy` that `f` returns for
   * its result.
   */
  final def flatMap[B](f: A => Lazy[B]): Lazy[B] = new FlatMap(this, new Cont(f, e => new Defer(() => throw e)))

  /** Returns a `Lazy` that runs this one, then the `Lazy` it returns. */
  @inline final def flatten[B](implicit ev: A <:< Lazy[B]): Lazy[B] = flatMap(a => a)

  /**
   * Runs this computation and returns its result, or returns the cached result
   * of an earlier call. A non-fatal exception is cached as well: later calls
   * throw it again without running the computation. A `null` result is not
   * cached, so the next call runs the computation again.
   */
  final def force: A = {
    @annotation.tailrec
    def loop(current: Lazy[Any], stack: List[Cont[Any, Any]]): Any = current match {
      case d: Defer[Any @unchecked] =>
        if (stack.isEmpty) d.thunk()
        else {
          val cont = stack.head
          loop(
            try cont.ifSuccess(d.thunk())
            catch { case e if NonFatal(e) => cont.ifError(e) },
            stack.tail
          )
        }
      case fm: FlatMap[Any @unchecked, Any @unchecked] => loop(fm.first, fm.cont :: stack)
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

  /**
   * Returns `true` once `force` has returned a non-null result or thrown a
   * non-fatal exception.
   */
  final def isEvaluated: Boolean = value != null || (error ne null)

  /** Returns a `Lazy` that runs this one and applies `f` to its result. */
  final def map[B](f: A => B): Lazy[B] = flatMap(a => new Defer(() => f(a)))

  /** Returns a `Lazy` that runs this one and discards its result. */
  @inline final def unit: Lazy[Unit] = map(_ => ())

  /**
   * Returns a `Lazy` that runs this one, then `that`, and pairs their results.
   */
  final def zip[B](that: Lazy[B]): Lazy[(A, B)] = flatMap(a => that.map(b => (a, b)))

  /** Forces this `Lazy` and `that` one, and compares their results. */
  override final def equals(that: Any): Boolean = that match {
    case other: Lazy[A] @unchecked => other.force == force
    case _                         => false
  }

  /** Forces this `Lazy` and returns the hash code of its result. */
  override final def hashCode: Int = force.hashCode

  /**
   * Returns `Lazy(<not evaluated>)` before `force`, and `Lazy(` followed by the
   * cached result and `)` after it (`Lazy(null)` after a failure).
   */
  override final def toString: String =
    if (isEvaluated) s"Lazy($value)"
    else "Lazy(<not evaluated>)"
}

/** Constructors for [[Lazy]]. */
object Lazy {
  private class Cont[-A, +B](val ifSuccess: A => Lazy[B], val ifError: Throwable => Lazy[B])

  private class Defer[+A](val thunk: () => A) extends Lazy[A]

  private class FlatMap[A, +B](val first: Lazy[A], val cont: Cont[A, B]) extends Lazy[B]

  /** Returns a `Lazy` that evaluates `expression` when it is forced. */
  @inline def apply[A](expression: => A): Lazy[A] = new Defer(() => expression)

  /**
   * Returns a `Lazy` of the results of `values`, in the same order. Forcing it
   * runs the elements from the last one to the first.
   */
  def collectAll[A](values: IndexedSeq[Lazy[A]]): Lazy[IndexedSeq[A]] =
    values
      .foldLeft(Lazy(ChunkBuilder.make[A]()))((lazyResult, lazyValue) =>
        lazyValue.flatMap(value => lazyResult.map(_.addOne(value)))
      )
      .map(_.result())

  /** Returns a `Lazy` that throws `throwable` when it is forced. */
  @inline def fail(throwable: Throwable): Lazy[Nothing] = new Defer(() => throw throwable)

  /**
   * Applies `f` to each of `values` and collects the `Lazy` values it returns
   * as `collectAll` does. `f` itself runs when `foreach` is called; the `Lazy`
   * values it returns run when the result is forced.
   */
  def foreach[A, B](values: IndexedSeq[A])(f: A => Lazy[B]): Lazy[IndexedSeq[B]] = collectAll(values.map(f))
}
