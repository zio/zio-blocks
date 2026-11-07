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

/**
 * An `As[A, B]` converts values of type `A` into `B` with [[Into#into]] and
 * back with [[As#from]]. Use it when data travels in both directions, such as a
 * local model synchronised with a remote one; for a one-way conversion, use
 * [[Into]], whose derivation is less strict. An `As[A, B]` is an `Into[A, B]`,
 * and [[As.reverseInto]] also provides the `Into[B, A]`.
 *
 * [[As.derived]] derives both directions and rejects, at compile time, pairs
 * that cannot round-trip: a field that exists on one side only must be an
 * `Option` without a default, and fields with the same name must convert both
 * ways. A round trip still does not always restore the value exactly: an
 * `Option` field missing from `B` comes back as `None`, a narrowing direction
 * fails at run time for values out of range, and a collection converted to a
 * `Set` loses duplicates and order.
 *
 * {{{
 * case class BookV1(title: String, pages: Int)
 * case class BookV2(title: String, pages: Long)
 *
 * val books = As.derived[BookV1, BookV2]
 * books.into(BookV1("Zionomicon", 600))  // Right(BookV2("Zionomicon", 600L))
 * books.from(BookV2("Zionomicon", 600L)) // Right(BookV1("Zionomicon", 600))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/schema-evolution/as/ As]]
 */
trait As[A, B] extends Into[A, B] {

  /**
   * Converts `input` back into an `A`, or returns `Left` with the reasons it
   * cannot be represented as one.
   */
  def from(input: B): Either[SchemaError, A]

  /**
   * Returns an `As[B, A]` whose `into` is this `from` and whose `from` is this
   * `into`.
   */
  def reverse: As[B, A] = {
    val self = this
    new As[B, A] {
      def into(input: B): Either[SchemaError, A] = self.from(input)
      def from(input: A): Either[SchemaError, B] = self.into(input)
    }
  }
}

/**
 * Constructors for [[As]], the [[As.derived]] macro, and [[As.reverseInto]].
 */
object As extends AsVersionSpecific with AsLowPriorityImplicits {

  /**
   * Creates an `As[A, B]` whose `into` is `intoAB` and whose `from` is
   * `intoBA`. Nothing checks that the two directions round-trip.
   */
  def apply[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] = new As[A, B] {
    def into(input: A): Either[SchemaError, B] = intoAB.into(input)
    def from(input: B): Either[SchemaError, A] = intoBA.into(input)
  }

  /** Returns the implicit `As[A, B]` in scope. */
  def apply[A, B](implicit ev: As[A, B]): As[A, B] = ev
}

/** The low-priority implicit that turns an `As[A, B]` into an `Into[B, A]`. */
trait AsLowPriorityImplicits {

  /**
   * Provides the `Into[B, A]` of an implicit `As[A, B]`, which converts with
   * [[As#from]].
   */
  implicit def reverseInto[A, B](implicit as: As[A, B]): Into[B, A] =
    (input: B) => as.from(input)
}
