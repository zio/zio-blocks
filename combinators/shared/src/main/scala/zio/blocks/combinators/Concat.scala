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

package zio.blocks.combinators

/**
 * Picks the element type of a sequence that holds values of type `L` followed
 * by values of type `R`, such as a stream concatenated with another stream, and
 * converts each value to it. `Out` is that type.
 *
 * On Scala 3, `Out` is the union `L | R`, and the values need no conversion. On
 * Scala 2, which has no unions, a macro picks `Out`:
 *
 *   - same type: that type;
 *   - one a subtype of the other: the supertype;
 *   - otherwise, the one most specific common supertype left after dropping
 *     `Any`, `AnyRef`, `AnyVal`, `Object`, `Product`, `Serializable`,
 *     `java.io.Serializable` and `Comparable` (two case classes of a sealed
 *     `Shape` give `Shape`);
 *   - when no such supertype is left, or more than one: `Either[L, R]`, and
 *     `left` and `right` wrap values in `Left` and `Right`.
 *
 * Stream methods such as `Stream#concat` and `Stream#catchAll` take a
 * `Concat.WithOut` and use `isIdentityLike` to reuse elements as they are.
 *
 * {{{
 * sealed trait Shape
 * final case class Circle(radius: Double) extends Shape
 * final case class Square(side: Double) extends Shape
 *
 * def asOut[O](circle: Circle)(implicit c: Concat.WithOut[Circle, Square, O]): O =
 *   c.left(circle)
 *
 * val shape: Shape = asOut(Circle(1.0)) // Circle(1.0), not wrapped
 * }}}
 *
 * Unrelated types give `Either` on Scala 2:
 * {{{
 * // scala-2-only
 * def sides[O](implicit c: Concat.WithOut[String, Int, O]): (O, O) =
 *   (c.left("timeout"), c.right(504))
 *
 * val both: (Either[String, Int], Either[String, Int]) = sides // (Left(timeout),Right(504))
 * }}}
 *
 * and a union on Scala 3:
 * {{{
 * // scala-3-only
 * val c = summon[Concat[String, Int]]
 * val status: String | Int = c.left("timeout") // timeout
 * }}}
 *
 * @tparam L
 *   the type of the first values
 * @tparam R
 *   the type of the values that follow
 * @see
 *   [[https://zioblocks.com/reference/combinators/ Combinators]]
 */
trait Concat[L, R] {

  /** The element type of the concatenation. */
  type Out

  /**
   * Returns `true` when `left` and `right` return their argument unchanged,
   * typed as `Out`. It is `false` only for the Scala 2 `Either[L, R]` fallback,
   * whose `left` and `right` allocate a `Left` or a `Right`.
   */
  def isIdentityLike: Boolean

  /**
   * Converts a value of the first type to `Out`: the value itself, or `Left(l)`
   * for the Scala 2 `Either` fallback.
   */
  def left(l: L): Out

  /**
   * Converts a value of the second type to `Out`: the value itself, or
   * `Right(r)` for the Scala 2 `Either` fallback.
   */
  def right(r: R): Out
}

private[combinators] trait EqualTypeConcat {

  /**
   * The instance for two equal types: `Out` is `A`, and values pass through
   * unchanged. It lives in the shared sources, so Scala 3 code that uses a
   * library built with Scala 2 finds it without expanding the Scala 2
   * derivation macro.
   */
  implicit def same[A]: Concat.WithOut[A, A, A] =
    new _root_.zio.blocks.combinators.Concat[A, A] {
      type Out = A
      def isIdentityLike: Boolean = true
      def left(l: A): A           = l
      def right(r: A): A          = r
    }
}

/**
 * Holds the `Concat` instances: the ones for `Nothing` and for equal types, and
 * the derived instance for any other pair (on Scala 2, `derive`, or the
 * `Either` fallback `disjoint` when `derive` finds no supertype).
 */
object Concat extends ConcatCompanionPlatform with EqualTypeConcat {

  /**
   * A `Concat` instance whose `Out` is `O`. Ask for it with `O` as a type
   * parameter of the method, so the compiler infers the result type.
   *
   * @tparam L
   *   the type of the first values
   * @tparam R
   *   the type of the values that follow
   * @tparam O
   *   the element type of the result
   */
  type WithOut[L, R, O] = _root_.zio.blocks.combinators.Concat[L, R] { type Out = O }

  /** The instance for two `Nothing` sides: `Out` is `Nothing`. */
  implicit val bothNothing: WithOut[Nothing, Nothing, Nothing] =
    new _root_.zio.blocks.combinators.Concat[Nothing, Nothing] {
      type Out = Nothing
      def isIdentityLike: Boolean    = true
      def left(l: Nothing): Nothing  = l
      def right(r: Nothing): Nothing = r
    }

  /**
   * The instance for a `Nothing` first side, such as an empty stream: `Out` is
   * `R`.
   */
  implicit def leftNothing[R]: WithOut[Nothing, R, R] =
    new _root_.zio.blocks.combinators.Concat[Nothing, R] {
      type Out = R
      def isIdentityLike: Boolean = true
      def left(l: Nothing): R     = l
      def right(r: R): R          = r
    }

  /**
   * The instance for a `Nothing` second side, such as an empty stream: `Out` is
   * `L`.
   */
  implicit def rightNothing[L]: WithOut[L, Nothing, L] =
    new _root_.zio.blocks.combinators.Concat[L, Nothing] {
      type Out = L
      def isIdentityLike: Boolean = true
      def left(l: L): L           = l
      def right(r: Nothing): L    = r
    }
}
