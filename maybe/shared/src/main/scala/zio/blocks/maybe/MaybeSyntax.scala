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

package zio.blocks.maybe

/**
 * Lists the operations a `Maybe` has on both Scala versions. Nothing in the
 * library extends it: the operations come from `MaybeValue.MaybeOps` on Scala 2
 * and from extension methods in `Maybe` on Scala 3, with the same behaviour.
 *
 * @tparam A
 *   the type of the value
 */
trait MaybeSyntax[A] {

  /**
   * Returns `true` if this `Maybe` is absent. A present `null`, as in
   * `Maybe.present(null)`, is not absent.
   */
  def isAbsent: Boolean

  /**
   * Returns `true` if this `Maybe` holds a value, even a `null` one.
   */
  def isPresent: Boolean

  /**
   * Returns `true` if this `Maybe` is absent; the same as `isAbsent`.
   */
  def isEmpty: Boolean

  /**
   * Returns `true` if this `Maybe` holds a value; the same as `isPresent`.
   */
  def isDefined: Boolean

  /**
   * Returns `true` if this `Maybe` holds a value; the same as `isPresent`.
   */
  def nonEmpty: Boolean

  /**
   * Returns the value. Prefer `getOrElse` or `fold` unless the value is known
   * to be present.
   */
  def get: A

  /**
   * Returns the value, or `default` if this `Maybe` is absent. `default` is
   * evaluated only in that case.
   */
  def getOrElse[B >: A](default: => B): B

  /**
   * Returns this `Maybe` if it is present, and `alternative` otherwise.
   * `alternative` is evaluated only when this `Maybe` is absent.
   */
  def orElse[B >: A](alternative: => Maybe[B]): Maybe[B]

  /**
   * Returns the value, or `null` if this `Maybe` is absent. It compiles only
   * when `null` is a valid `B`, so for a reference type.
   */
  def orNull[B >: A](implicit ev: Null <:< B): B

  /**
   * Returns `ifPresent` applied to the value, or `ifAbsent` if this `Maybe` is
   * absent. `ifAbsent` is evaluated only in that case. It works the same on
   * both Scala versions, so prefer it to a pattern match in cross-built code.
   */
  def fold[B](ifAbsent: => B)(ifPresent: A => B): B

  /**
   * Returns `Some` of the value, or `None` if this `Maybe` is absent. A present
   * `null` gives `Some(null)`.
   */
  def toOption: Option[A]

  /**
   * Returns a one-element `List` of the value, or `Nil` if this `Maybe` is
   * absent.
   */
  def toList: List[A]

  /**
   * Returns a one-element `Seq` of the value, or an empty `Seq` if this `Maybe`
   * is absent.
   */
  def toSeq: Seq[A]

  /**
   * Returns an iterator over the value, which is empty if this `Maybe` is
   * absent.
   */
  def iterator: Iterator[A]

  /**
   * Returns a present `Maybe` of `f` applied to the value, or an absent one if
   * this `Maybe` is absent. The result is present even when `f` returns `null`.
   */
  def map[B](f: A => B): Maybe[B]

  /**
   * Returns `f` applied to the value, or an absent `Maybe` if this `Maybe` is
   * absent. Use it to chain steps that can each produce no value.
   */
  def flatMap[B](f: A => Maybe[B]): Maybe[B]

  /**
   * Removes one level of nesting: a present `Maybe` that holds a present value
   * gives that value, and one that holds an absent `Maybe` gives absent.
   */
  def flatten[B](implicit ev: A <:< Maybe[B]): Maybe[B]

  /**
   * Runs `f` on the value if this `Maybe` is present, and does nothing
   * otherwise.
   */
  def foreach[U](f: A => U): Unit

  /**
   * Returns `true` if this `Maybe` is present and its value equals `elem`.
   */
  def contains[A1 >: A](elem: A1): Boolean

  /**
   * Returns `true` if this `Maybe` is present and its value satisfies `p`.
   */
  def exists(p: A => Boolean): Boolean

  /**
   * Returns `true` if this `Maybe` is absent or its value satisfies `p`.
   */
  def forall(p: A => Boolean): Boolean

  /**
   * Returns this `Maybe` if it is present and its value satisfies `p`, and an
   * absent `Maybe` otherwise.
   */
  def filter(p: A => Boolean): Maybe[A]

  /**
   * Returns this `Maybe` if it is present and its value does not satisfy `p`,
   * and an absent `Maybe` otherwise.
   */
  def filterNot(p: A => Boolean): Maybe[A]

  /**
   * Returns a present `Maybe` of `pf` applied to the value if `pf` is defined
   * at it, and an absent `Maybe` otherwise.
   */
  def collect[B](pf: PartialFunction[A, B]): Maybe[B]

  /**
   * Returns a view of this `Maybe` filtered by `p`, so that a for-comprehension
   * can use an `if` guard. Mapping the view gives the same result as
   * `filter(p)` followed by the same call.
   */
  def withFilter(p: A => Boolean): MaybeWithFilter[A]

  /**
   * Returns `Right` of the value, or `Left(left)` if this `Maybe` is absent.
   * `left` is evaluated only in that case.
   */
  def toRight[X](left: => X): Either[X, A]

  /**
   * Returns `Left` of the value, or `Right(right)` if this `Maybe` is absent.
   * `right` is evaluated only in that case.
   */
  def toLeft[X](right: => X): Either[A, X]

  /**
   * Returns a present `Maybe` of both values as a pair if both `Maybe`s are
   * present, and an absent `Maybe` otherwise.
   */
  def zip[B](that: Maybe[B]): Maybe[(A, B)]

  /**
   * Splits a `Maybe` of a pair into a pair of `Maybe`s, both absent if this
   * `Maybe` is absent.
   */
  def unzip[A1, A2](implicit ev: A <:< (A1, A2)): (Maybe[A1], Maybe[A2])

  /**
   * Splits a `Maybe` of a triple into three `Maybe`s, all absent if this
   * `Maybe` is absent.
   */
  def unzip3[A1, A2, A3](implicit ev: A <:< (A1, A2, A3)): (Maybe[A1], Maybe[A2], Maybe[A3])
}

/**
 * Declares the version-specific `MaybeWithFilter` type that
 * `MaybeSyntax#withFilter` returns.
 */
trait MaybeSyntaxCompat {

  /** The type `withFilter` returns, defined per Scala version. */
  type MaybeWithFilter[A]
}
