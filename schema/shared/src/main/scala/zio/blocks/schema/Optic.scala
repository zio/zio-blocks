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
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace

/**
 * An optic is a first-class path into a value of type `S` that focuses on parts
 * of type `A`. It reads, replaces and modifies those parts without rebuilding
 * the enclosing value by hand. It also carries the schemas of both ends
 * (`source` and `focus`), so a failed access can say where and why it failed,
 * and the path can be turned into a [[DynamicOptic]] or a [[SchemaExpr]] query.
 *
 * Every optic is one of four kinds:
 *
 *   - [[Lens]]: a part that is always there, such as a field of a case class;
 *   - [[Prism]]: one case of a sealed trait or enum;
 *   - [[Optional]]: a part that may be missing, such as a field inside one
 *     case, the element at an index or the value at a map key;
 *   - [[Traversal]]: zero or more parts, such as every element of a collection.
 *
 * Get optics from the `$` (or `optic`) macro of [[CompanionOptics]], or from
 * the `optics` member of [[DerivedOptics]]. Compose them with `apply`. Two
 * lenses give a lens, two prisms give a prism, anything composed with a
 * traversal gives a traversal, and every other pair gives an optional.
 *
 * This trait has the operations that every kind supports: `modify`,
 * `modifyOption`, `modifyOrFail`, `check` and `toDynamic`, plus operators such
 * as `===`, `>`, `+` and `&&` that build a [[SchemaExpr]]. Reading and
 * replacing depend on the kind: `get` and `replace` on [[Lens]], `getOption`
 * and `replace` on [[Prism]] and [[Optional]], and `fold` on [[Traversal]].
 *
 * {{{
 * case class Author(name: String)
 * case class Book(title: String, author: Author)
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val authorName: Lens[Book, String] = $(_.author.name)
 * }
 *
 * val zionomicon = Book("Zionomicon", Author("John A. De Goes"))
 * Book.authorName.modify(zionomicon, _.toUpperCase) // Book(Zionomicon,Author(JOHN A. DE GOES))
 * Book.authorName.toDynamic.toString                 // .author.name
 * }}}
 *
 * @tparam S
 *   the type of the value the optic reads from and updates
 * @tparam A
 *   the type of the parts it focuses on
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
sealed trait Optic[S, A] { self =>

  /**
   * The schema of the value this optic starts from. For a composed optic, it is
   * the source of the first step.
   */
  def source: Reflect.Bound[S]

  /**
   * The schema of the parts this optic focuses on. For a composed optic, it is
   * the focus of the last step.
   */
  def focus: Reflect.Bound[A]

  /**
   * Composes this optic with a lens on its focus. The result focuses on the
   * lens's focus `B` inside every part this optic focuses on. It is a [[Lens]]
   * when this optic is a lens, a [[Traversal]] when this optic is a traversal,
   * and an [[Optional]] otherwise.
   *
   * {{{
   * case class Author(name: String)
   * object Author extends CompanionOptics[Author] {
   *   implicit val schema: Schema[Author] = Schema.derived
   *   val name: Lens[Author, String] = $(_.name)
   * }
   * case class Book(title: String, author: Author)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val author: Lens[Book, Author] = $(_.author)
   * }
   *
   * val authorName: Lens[Book, String] = Book.author(Author.name)
   * authorName.get(Book("Zionomicon", Author("John A. De Goes"))) // "John A. De Goes"
   * }}}
   */
  def apply[B](that: Lens[A, B]): Optic[S, B]

  /**
   * Composes this optic with a prism on its focus. The result is a [[Prism]]
   * when this optic is a prism, a [[Traversal]] when this optic is a traversal,
   * and an [[Optional]] otherwise.
   */
  def apply[B <: A](that: Prism[A, B]): Optic[S, B]

  /**
   * Composes this optic with an optional on its focus. The result is a
   * [[Traversal]] when this optic is a traversal, and an [[Optional]]
   * otherwise.
   */
  def apply[B](that: Optional[A, B]): Optic[S, B]

  /**
   * Composes this optic with a traversal on its focus. The result is always a
   * [[Traversal]].
   */
  def apply[B](that: Traversal[A, B]): Traversal[S, B]

  /**
   * Returns `None` when this optic reaches its focus in `s`, and otherwise an
   * [[OpticCheck]] that says why not. The possible problems are another case of
   * a variant, a wrapper whose `unwrap` throws a [[SchemaError]] (both errors),
   * an index past the end, a missing map key, an empty collection, and, for
   * `searchFor`, no match at all (all four warnings). Except for `searchFor`,
   * which decodes every candidate with its schema, `check` never runs a
   * wrapper's `wrap`, so it does not see failures that only `replace` or
   * `modify` meet.
   *
   * A [[Lens]] always returns `None`. A [[Prism]] and an [[Optional]] report
   * the first problem on the path. A [[Traversal]] reports the problems in
   * every branch it visits, even when it reaches other parts: `atIndices(0, 2)`
   * on a one-element list reports index 2.
   */
  def check(s: S): Option[OpticCheck]

  /**
   * Returns `s` with `f` applied to every part this optic focuses on. When
   * there is nothing to focus on, the result equals `s`: a [[Prism]] on another
   * case, an [[Optional]] on a missing index or key, a [[Traversal]] over an
   * empty collection. It also equals `s` when a `wrapped` step on the path
   * throws a [[SchemaError]], including one that `f` throws below that step
   * (a bug, see `docs/dev/issues.md`); other exceptions, from the path or from
   * `f`, propagate. `modifyOption` and `modifyOrFail` say when nothing was
   * modified.
   *
   * {{{
   * case class Book(title: String, tags: List[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val tags: Traversal[Book, String] = $(_.tags.each)
   * }
   *
   * Book.tags.modify(Book("Zionomicon", List("zio", "scala")), _.toUpperCase)
   * // Book(Zionomicon,List(ZIO, SCALA))
   * }}}
   */
  def modify(s: S, f: A => A): S

  /**
   * Like `modify`, but returns `None` instead of an unchanged value when there
   * is nothing to focus on, and when a `wrapped` step on the path throws a
   * [[SchemaError]], including one that `f` throws below that step (a bug, see
   * `docs/dev/issues.md`). A [[Lens]] always returns `Some`.
   */
  def modifyOption(s: S, f: A => A): Option[S]

  /**
   * Like `modify`, but returns `Left` with an [[OpticCheck]] that says why
   * nothing was modified, as `check` does, and a [[OpticCheck.WrappingError]]
   * when a `wrapped` step on the path throws a [[SchemaError]], including one
   * that `f` throws below that step (a bug, see `docs/dev/issues.md`). A
   * [[Lens]] always returns `Right`. A [[Traversal]] returns `Right` once it
   * has modified at least one part, even when other parts are missing; `check`
   * reports those.
   */
  def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S]

  /**
   * Returns the path of this optic as a [[DynamicOptic]], with one node per
   * step. For example, `$(_.format.when[Paperback].pages).toDynamic.toString`
   * is `.format<Paperback>.pages`.
   */
  def toDynamic: DynamicOptic

  private[schema] def toDynamic(index: Int): DynamicOptic = new DynamicOptic(toDynamic.nodes.take(index + 1))

  /**
   * Composes this optic, whose focus is a `List`, with a traversal over every
   * element of that list.
   *
   * @throws java.lang.RuntimeException
   *   ("Expected List") if the focus schema is not a `List` sequence schema
   */
  final def listValues[B](implicit ev: A =:= List[B]): Traversal[S, B] = {
    import Reflect.Extractors.List

    val list = self.asEquivalent[List[B]]
    list.focus match {
      case List(element) => list(Traversal.listValues(element))
      case _             => sys.error("Expected List")
    }
  }

  /**
   * Composes this optic, whose focus is a `Vector`, with a traversal over every
   * element of that vector.
   *
   * @throws java.lang.RuntimeException
   *   ("Expected Vector") if the focus schema is not a `Vector` sequence schema
   */
  final def vectorValues[B](implicit ev: A =:= Vector[B]): Traversal[S, B] = {
    import Reflect.Extractors.Vector

    val vector = self.asEquivalent[Vector[B]]
    vector.focus match {
      case Vector(element) => vector(Traversal.vectorValues(element))
      case _               => sys.error("Expected Vector")
    }
  }

  /**
   * Composes this optic, whose focus is a `Set`, with a traversal over every
   * element of that set.
   *
   * @throws java.lang.RuntimeException
   *   ("Expected Set") if the focus schema is not a `Set` sequence schema
   */
  final def setValues[B](implicit ev: A =:= Set[B]): Traversal[S, B] = {
    import Reflect.Extractors.Set

    val set = self.asEquivalent[Set[B]]
    set.focus match {
      case Set(element) => set(Traversal.setValues(element))
      case _            => sys.error("Expected Set")
    }
  }

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic equals
   * the literal `that`. Evaluate it on an `S` with `SchemaExpr#eval`.
   */
  final def ===(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] =
    SchemaExpr.relational(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.literal(that),
      SchemaExpr.RelationalOperator.Equal
    )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic equals
   * the focus of `that`.
   */
  final def ===(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.relational(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.optic(that.toDynamic, new Schema(that.source)),
      SchemaExpr.RelationalOperator.Equal
    )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic is
   * greater than the focus of `that`.
   */
  final def >(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.relational(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.optic(that.toDynamic, new Schema(that.source)),
      SchemaExpr.RelationalOperator.GreaterThan
    )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic is
   * greater than the literal `that`.
   */
  final def >(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] = SchemaExpr.relational(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that),
    SchemaExpr.RelationalOperator.GreaterThan
  )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic is
   * greater than or equal to the focus of `that`.
   */
  final def >=(that: Optic[S, A]): SchemaExpr[S, Boolean] = SchemaExpr.relational(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.optic(that.toDynamic, new Schema(that.source)),
    SchemaExpr.RelationalOperator.GreaterThanOrEqual
  )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic is
   * greater than or equal to the literal `that`.
   */
  final def >=(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] = SchemaExpr.relational(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that),
    SchemaExpr.RelationalOperator.GreaterThanOrEqual
  )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic is less
   * than the focus of `that`.
   */
  final def <(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.relational(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.optic(that.toDynamic, new Schema(that.source)),
      SchemaExpr.RelationalOperator.LessThan
    )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic is less
   * than the literal `that`.
   */
  final def <(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] = SchemaExpr.relational(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that),
    SchemaExpr.RelationalOperator.LessThan
  )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic is less
   * than or equal to the focus of `that`.
   */
  final def <=(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.relational(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.optic(that.toDynamic, new Schema(that.source)),
      SchemaExpr.RelationalOperator.LessThanOrEqual
    )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic is less
   * than or equal to the literal `that`.
   */
  final def <=(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] = SchemaExpr.relational(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that),
    SchemaExpr.RelationalOperator.LessThanOrEqual
  )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic differs
   * from the focus of `that`.
   */
  final def !=(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.relational(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.optic(that.toDynamic, new Schema(that.source)),
      SchemaExpr.RelationalOperator.NotEqual
    )

  /**
   * Builds a [[SchemaExpr]] that is true where the focus of this optic differs
   * from the literal `that`.
   */
  final def !=(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] =
    SchemaExpr.relational(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.literal(that),
      SchemaExpr.RelationalOperator.NotEqual
    )

  /**
   * Builds a [[SchemaExpr]] for the logical and of this optic's `Boolean` focus
   * and the focus of `that`.
   */
  final def &&(that: Optic[S, A])(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] = SchemaExpr.logical(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.optic(that.toDynamic, new Schema(that.source)),
    SchemaExpr.LogicalOperator.And
  )

  /**
   * Builds a [[SchemaExpr]] for the logical and of this optic's `Boolean` focus
   * and the literal `that`.
   */
  final def &&(that: Boolean)(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] =
    SchemaExpr.logical(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.literal(that),
      SchemaExpr.LogicalOperator.And
    )

  /**
   * Builds a [[SchemaExpr]] for the logical or of this optic's `Boolean` focus
   * and the focus of `that`.
   */
  final def ||(that: Optic[S, A])(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] = SchemaExpr.logical(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.optic(that.toDynamic, new Schema(that.source)),
    SchemaExpr.LogicalOperator.Or
  )

  /**
   * Builds a [[SchemaExpr]] for the logical or of this optic's `Boolean` focus
   * and the literal `that`.
   */
  final def ||(that: Boolean)(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] =
    SchemaExpr.logical(
      SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
      SchemaExpr.literal(that),
      SchemaExpr.LogicalOperator.Or
    )

  /**
   * Builds a [[SchemaExpr]] for the negation of this optic's `Boolean` focus.
   */
  final def unary_!(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] =
    SchemaExpr.not(SchemaExpr.optic(this.toDynamic, new Schema(this.source)))

  /**
   * Builds a [[SchemaExpr]] for this optic's `String` focus followed by the
   * literal `that`.
   */
  final def concat(that: String)(implicit ev: A =:= String): SchemaExpr[S, String] =
    SchemaExpr.stringConcat(SchemaExpr.optic(this.toDynamic, new Schema(this.source)), SchemaExpr.literal(that))

  /**
   * Builds a [[SchemaExpr]] that is true where this optic's `String` focus
   * matches the Java regular expression `that` as a whole
   * (`java.util.regex.Pattern.matches`).
   */
  final def matches(that: String)(implicit ev: A =:= String): SchemaExpr[S, Boolean] =
    SchemaExpr.stringRegexMatch(SchemaExpr.literal(that), SchemaExpr.optic(this.toDynamic, new Schema(this.source)))

  /**
   * Builds a [[SchemaExpr]] for this optic's numeric focus plus the literal
   * `that`.
   */
  final def +(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.arithmetic(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Add,
    isNumeric.primitiveType.asInstanceOf[NumericPrimitiveType[A]]
  )

  /**
   * Builds a [[SchemaExpr]] for this optic's numeric focus minus the literal
   * `that`.
   */
  final def -(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.arithmetic(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Subtract,
    isNumeric.primitiveType.asInstanceOf[NumericPrimitiveType[A]]
  )

  /**
   * Builds a [[SchemaExpr]] for this optic's numeric focus times the literal
   * `that`.
   */
  final def *(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.arithmetic(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Multiply,
    isNumeric.primitiveType.asInstanceOf[NumericPrimitiveType[A]]
  )

  /**
   * Builds a [[SchemaExpr]] for this optic's numeric focus divided by the
   * literal `that`.
   */
  final def /(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.arithmetic(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Divide,
    isNumeric.primitiveType.asInstanceOf[NumericPrimitiveType[A]]
  )

  /**
   * Builds a [[SchemaExpr]] for the remainder of this optic's numeric focus
   * divided by the literal `that`.
   */
  final def %(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.arithmetic(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Modulo,
    isNumeric.primitiveType.asInstanceOf[NumericPrimitiveType[A]]
  )

  /**
   * Builds a [[SchemaExpr]] for this optic's numeric focus raised to the power
   * of the literal `that`.
   */
  final def pow(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.arithmetic(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Pow,
    isNumeric.primitiveType.asInstanceOf[NumericPrimitiveType[A]]
  )

  // Bitwise operations
  /**
   * Builds a [[SchemaExpr]] for the bitwise and of this optic's integral focus
   * and the literal `that`.
   */
  final def &(that: A)(implicit ev: IsIntegral[A]): SchemaExpr[S, A] = SchemaExpr.bitwise(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(ev.schema),
    SchemaExpr.BitwiseOperator.And
  )

  /**
   * Builds a [[SchemaExpr]] for the bitwise or of this optic's integral focus
   * and the literal `that`.
   */
  final def |(that: A)(implicit ev: IsIntegral[A]): SchemaExpr[S, A] = SchemaExpr.bitwise(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(ev.schema),
    SchemaExpr.BitwiseOperator.Or
  )

  /**
   * Builds a [[SchemaExpr]] for the bitwise exclusive or of this optic's
   * integral focus and the literal `that`.
   */
  final def ^(that: A)(implicit ev: IsIntegral[A]): SchemaExpr[S, A] = SchemaExpr.bitwise(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(ev.schema),
    SchemaExpr.BitwiseOperator.Xor
  )

  /**
   * Builds a [[SchemaExpr]] for this optic's integral focus shifted left by the
   * literal `that`.
   */
  final def <<(that: A)(implicit ev: IsIntegral[A]): SchemaExpr[S, A] = SchemaExpr.bitwise(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(ev.schema),
    SchemaExpr.BitwiseOperator.LeftShift
  )

  /**
   * Builds a [[SchemaExpr]] for this optic's integral focus shifted right by
   * the literal `that`, keeping the sign.
   */
  final def >>(that: A)(implicit ev: IsIntegral[A]): SchemaExpr[S, A] = SchemaExpr.bitwise(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(ev.schema),
    SchemaExpr.BitwiseOperator.RightShift
  )

  /**
   * Builds a [[SchemaExpr]] for this optic's integral focus shifted right by
   * the literal `that`, filling with zeros.
   */
  final def >>>(that: A)(implicit ev: IsIntegral[A]): SchemaExpr[S, A] = SchemaExpr.bitwise(
    SchemaExpr.optic(this.toDynamic, new Schema(this.source)),
    SchemaExpr.literal(that)(ev.schema),
    SchemaExpr.BitwiseOperator.UnsignedRightShift
  )

  /**
   * Builds a [[SchemaExpr]] for the bitwise complement of this optic's integral
   * focus.
   */
  final def unary_~(implicit ev: IsIntegral[A]): SchemaExpr[S, A] = {
    val _ = ev
    SchemaExpr.bitwiseNot(SchemaExpr.optic(this.toDynamic, new Schema(this.source)))
  }

  /**
   * Builds a [[SchemaExpr]] for the length of this optic's `String` focus.
   */
  final def length(implicit ev: A =:= String): SchemaExpr[S, Int] =
    SchemaExpr.stringLength(SchemaExpr.optic(this.toDynamic, new Schema(this.source)))

  /**
   * Returns this optic with its focus type changed to `B`, which `ev` proves is
   * the same type. The optic is cast, not copied.
   */
  final def asEquivalent[B](implicit ev: A =:= B): Optic[S, B] = self.asInstanceOf[Optic[S, B]]
}

/**
 * A lens focuses on a part of `S` that is always there, such as a field of a
 * case class, or a field of a field. Use it to read or replace that part
 * without rebuilding the record by hand. A lens never reports a missing focus:
 * `check` always returns `None`, `modifyOption` always returns `Some`, and
 * `modifyOrFail` always returns `Right`.
 *
 * Get a lens from the `$` macro of [[CompanionOptics]], from [[DerivedOptics]],
 * from `Reflect.Record#lensByName`, or with the `Lens.apply` constructor.
 *
 * {{{
 * case class Book(title: String, year: Int)
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val year: Lens[Book, Int] = $(_.year)
 * }
 *
 * val zionomicon = Book("Zionomicon", 2021)
 * Book.year.get(zionomicon)          // 2021
 * Book.year.replace(zionomicon, 2024) // Book(Zionomicon,2024)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
sealed trait Lens[S, A] extends Optic[S, A] {

  /**
   * Returns the part of `s` that this lens focuses on.
   *
   * {{{
   * case class Book(title: String, year: Int)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String] = $(_.title)
   * }
   *
   * Book.title.get(Book("Zionomicon", 2021)) // "Zionomicon"
   * }}}
   */
  def get(s: S): A

  /**
   * Returns a copy of `s` with the part this lens focuses on replaced by `a`.
   *
   * {{{
   * case class Book(title: String, year: Int)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String] = $(_.title)
   * }
   *
   * Book.title.replace(Book("Programming in Scala", 2021), "Programming in Scala, Fifth Edition")
   * // Book(Programming in Scala, Fifth Edition,2021)
   * }}}
   */
  def replace(s: S, a: A): S

  /**
   * Composes this lens with `that` lens. The result is a [[Lens]].
   */
  def apply[B](that: Lens[A, B]): Lens[S, B] = Lens(this, that)

  /**
   * Composes this lens with `that` prism. The result is an [[Optional]].
   */
  def apply[B <: A](that: Prism[A, B]): Optional[S, B] = Optional(this, that)

  /**
   * Composes this lens with `that` optional. The result is an [[Optional]].
   */
  def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  /**
   * Composes this lens with `that` traversal. The result is a [[Traversal]].
   */
  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
}

/**
 * Constructors for [[Lens]].
 */
object Lens {

  /**
   * Creates a lens on the field `focusTerm` of the record `source`. The field
   * is looked up by the term's name, so `focusTerm` must be one of `source`'s
   * fields. If it is not, the lens throws `ArrayIndexOutOfBoundsException` the
   * first time it reads or writes a value (see `docs/dev/issues.md`).
   * `Reflect.Record#lensByName` and the `$` macro of [[CompanionOptics]] check
   * the field for you.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `source` or `focusTerm` is null
   */
  def apply[S, A](source: Reflect.Record.Bound[S], focusTerm: Term.Bound[S, A]): Lens[S, A] = {
    require((source ne null) && (focusTerm ne null))
    new LensImpl(Array(source), Array(focusTerm))
  }

  /**
   * Composes two lenses: the result focuses on `second`'s focus inside
   * `first`'s focus. It is the same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T, A](first: Lens[S, T], second: Lens[T, A]): Lens[S, A] = {
    require((first ne null) && (second ne null))
    val lens1 = first.asInstanceOf[LensImpl[?, ?]]
    val lens2 = second.asInstanceOf[LensImpl[?, ?]]
    new LensImpl(lens1.sources ++ lens2.sources, lens1.focusTerms ++ lens2.focusTerms)
  }

  private[schema] case class LensImpl[S, A](
    sources: Array[Reflect.Record.Bound[?]],
    focusTerms: Array[Term.Bound[?, ?]]
  ) extends Lens[S, A] {
    private[this] var bindings: Array[LensBinding]  = null
    private[this] var usedRegisters: RegisterOffset = 0L

    private[this] def init(): Unit = {
      var offset   = 0L
      val len      = sources.length
      val bindings = new Array[LensBinding](len)
      var idx      = 0
      while (idx < len) {
        val source        = sources(idx)
        val focusTermName = focusTerms(idx).name
        bindings(idx) = new LensBinding(
          deconstructor = source.deconstructor.asInstanceOf[Deconstructor[Any]],
          constructor = source.constructor.asInstanceOf[Constructor[Any]],
          register = source.registers(source.fieldIndexByName(focusTermName)),
          offset = offset
        )
        offset = RegisterOffset.add(offset, source.usedRegisters)
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def check(s: S): None.type = None

    def get(s: S): A = {
      if (bindings eq null) init()
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        x = binding.register.get(registers, offset)
        idx += 1
      }
      x.asInstanceOf[A]
    }

    def replace(s: S, a: A): S = {
      if (bindings eq null) init()
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        if (idx < len) x = binding.register.get(registers, offset)
        idx += 1
      }
      x = a
      while (idx > 0) {
        idx -= 1
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.register.set(registers, offset, x)
        x = binding.constructor.construct(registers, offset)
      }
      x.asInstanceOf[S]
    }

    def modify(s: S, f: A => A): S = {
      if (bindings eq null) init()
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        x = binding.register.get(registers, offset)
        idx += 1
      }
      x = f(x.asInstanceOf[A])
      while (idx > 0) {
        idx -= 1
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.register.set(registers, offset, x)
        x = binding.constructor.construct(registers, offset)
      }
      x.asInstanceOf[S]
    }

    def modifyOption(s: S, f: A => A): Option[S] = new Some(modify(s, f))

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = new Right(modify(s, f))

    lazy val toDynamic: DynamicOptic =
      new DynamicOptic(ArraySeq.unsafeWrapArray(focusTerms.map(term => new DynamicOptic.Node.Field(term.name))))

    override def toString: String = {
      val path = focusTerms.map(term => s".${term.name}").mkString
      s"Lens(_$path)"
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: LensImpl[?, ?] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

/**
 * A prism focuses on one case `A` of a sealed trait, enum or other variant `S`.
 * On a value of another case, `getOption` returns `None`, and `replace` and
 * `modify` return the value unchanged. The `*OrFail` methods return an
 * [[OpticCheck.UnexpectedCase]] instead, which names the expected and the
 * actual case.
 *
 * {{{
 * sealed trait Format
 * case class Paperback(pages: Int) extends Format
 * case class Ebook(sizeMb: Double) extends Format
 * object Format extends CompanionOptics[Format] {
 *   implicit val schema: Schema[Format] = Schema.derived
 *   val paperback: Prism[Format, Paperback] = $(_.when[Paperback])
 * }
 *
 * Format.paperback.getOption(Paperback(870))  // Some(Paperback(870))
 * Format.paperback.getOption(Ebook(5.2))      // None
 * Format.paperback.reverseGet(Paperback(870)) // Paperback(870), typed as a Format
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
sealed trait Prism[S, A <: S] extends Optic[S, A] {

  /**
   * Returns `s` as the case `A` when it is that case, and `None` otherwise.
   *
   * {{{
   * sealed trait Format
   * case class Paperback(pages: Int) extends Format
   * case class Ebook(sizeMb: Double) extends Format
   * object Format extends CompanionOptics[Format] {
   *   implicit val schema: Schema[Format] = Schema.derived
   *   val paperback: Prism[Format, Paperback] = $(_.when[Paperback])
   * }
   *
   * Format.paperback.getOption(Paperback(870)) // Some(Paperback(870))
   * Format.paperback.getOption(Ebook(5.2))     // None
   * }}}
   */
  def getOption(s: S): Option[A]

  /**
   * Like `getOption`, but returns `Left` with an [[OpticCheck.UnexpectedCase]]
   * when `s` is another case, for example
   * `During attempted access at .when[Paperback], encountered an unexpected case at .when[Paperback]: expected Paperback, but got Ebook`.
   * For a composed prism, it names the first step that does not match.
   */
  def getOrFail(s: S): Either[OpticCheck, A] =
    getOption(s) match {
      case Some(a) => new Right(a)
      case _       => new Left(check(s).get)
    }

  /**
   * Returns `a` as a value of the variant `S`. It never fails, because a case
   * is always a value of its variant.
   */
  def reverseGet(a: A): S

  /**
   * Returns `a` when `s` is the case `A`, and `s` unchanged otherwise.
   *
   * {{{
   * sealed trait Format
   * case class Paperback(pages: Int) extends Format
   * case class Ebook(sizeMb: Double) extends Format
   * object Format extends CompanionOptics[Format] {
   *   implicit val schema: Schema[Format] = Schema.derived
   *   val paperback: Prism[Format, Paperback] = $(_.when[Paperback])
   * }
   *
   * Format.paperback.replace(Paperback(870), Paperback(920)) // Paperback(920)
   * Format.paperback.replace(Ebook(5.2), Paperback(920))     // Ebook(5.2)
   * }}}
   */
  def replace(s: S, a: A): S

  /**
   * Like `replace`, but returns `None` when `s` is another case.
   */
  def replaceOption(s: S, a: A): Option[S]

  /**
   * Like `replace`, but returns `Left` with an [[OpticCheck.UnexpectedCase]]
   * when `s` is another case.
   */
  def replaceOrFail(s: S, a: A): Either[OpticCheck, S] =
    replaceOption(s, a) match {
      case Some(s) => new Right(s)
      case _       => new Left(check(s).get)
    }

  /**
   * Composes this prism with `that` prism. The result is a [[Prism]].
   */
  def apply[B <: A](that: Prism[A, B]): Prism[S, B] = Prism(this, that)

  /**
   * Composes this prism with `that` lens. The result is an [[Optional]].
   */
  def apply[B](that: Lens[A, B]): Optional[S, B] = Optional(this, that)

  /**
   * Composes this prism with `that` optional. The result is an [[Optional]].
   */
  def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  /**
   * Composes this prism with `that` traversal. The result is a [[Traversal]].
   */
  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
}

/**
 * Constructors for [[Prism]].
 */
object Prism {

  /**
   * Creates a prism on the case `focusTerm` of the variant `source`. The case
   * is looked up by the term's name, so `focusTerm` must be one of `source`'s
   * cases. If it is not, this method throws `ArrayIndexOutOfBoundsException`
   * (see `docs/dev/issues.md`). `Reflect.Variant#prismByName` and the `$` macro
   * of [[CompanionOptics]] check the case for you.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `source` or `focusTerm` is null
   * @throws java.lang.ArrayIndexOutOfBoundsException
   *   if `source` has no case named like `focusTerm`
   */
  def apply[S, A <: S](source: Reflect.Variant.Bound[S], focusTerm: Term.Bound[S, A]): Prism[S, A] = {
    require((source ne null) && (focusTerm ne null))
    new PrismImpl(Array(source), Array(focusTerm))
  }

  /**
   * Composes two prisms: the result focuses on the case `A` of `first`'s case
   * `T`. It is the same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T <: S, A <: T](first: Prism[S, T], second: Prism[T, A]): Prism[S, A] = {
    require((first ne null) && (second ne null))
    val prism1 = first.asInstanceOf[PrismImpl[?, ?]]
    val prism2 = second.asInstanceOf[PrismImpl[?, ?]]
    new PrismImpl(prism1.sources ++ prism2.sources, prism1.focusTerms ++ prism2.focusTerms)
  }

  private[schema] case class PrismImpl[S, A <: S](
    sources: Array[Reflect.Variant.Bound[?]],
    focusTerms: Array[Term.Bound[?, ?]]
  ) extends Prism[S, A] {
    private[this] var matchers: Array[Matcher[Any]]             = new Array[Matcher[Any]](sources.length)
    private[this] var discriminators: Array[Discriminator[Any]] = new Array[Discriminator[Any]](sources.length)

    {
      val len            = sources.length
      val matchers       = new Array[Matcher[Any]](len)
      val discriminators = new Array[Discriminator[Any]](len)
      var idx            = 0
      while (idx < len) {
        val source        = sources(idx)
        val focusTermName = focusTerms(idx).name
        matchers(idx) = source.matchers.apply(source.caseIndexByName(focusTermName))
        discriminators(idx) = source.discriminator.asInstanceOf[Discriminator[Any]]
        idx += 1
      }
      this.discriminators = discriminators
      this.matchers = matchers
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def check(s: S): Option[OpticCheck] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        val lastX = x
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return new Some(toOpticCheck(idx, lastX))
        idx += 1
      }
      None
    }

    def getOption(s: S): Option[A] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return None
        idx += 1
      }
      new Some(x.asInstanceOf[A])
    }

    def reverseGet(a: A): S = a

    def replace(s: S, a: A): S = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return s
        idx += 1
      }
      a
    }

    def replaceOption(s: S, a: A): Option[S] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return None
        idx += 1
      }
      new Some(a)
    }

    def modify(s: S, f: A => A): S = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return s
        idx += 1
      }
      f(x.asInstanceOf[A])
    }

    def modifyOption(s: S, f: A => A): Option[S] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return None
        idx += 1
      }
      new Some(f(x.asInstanceOf[A]))
    }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        val lastX = x
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return new Left(toOpticCheck(idx, lastX))
        idx += 1
      }
      new Right(f(x.asInstanceOf[A]))
    }

    private[this] def toOpticCheck(idx: Int, lastX: Any): OpticCheck = {
      val actualCaseIdx  = discriminators(idx).discriminate(lastX)
      val actualCase     = sources(idx).cases(actualCaseIdx).name
      val focusTermName  = focusTerms(idx).name
      val unexpectedCase = new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), lastX)
      new OpticCheck(new ::(unexpectedCase, Nil))
    }

    lazy val toDynamic: DynamicOptic =
      new DynamicOptic(ArraySeq.unsafeWrapArray(focusTerms.map(term => new DynamicOptic.Node.Case(term.name))))

    override def toString: String = {
      val path = focusTerms.map(term => s".when[${term.name}]").mkString
      s"Prism(_$path)"
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: PrismImpl[?, ?] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

/**
 * An optional focuses on a part of `S` that may be missing: a field inside one
 * case of a variant, the element at an index of a sequence, the value at a key
 * of a map, or the value inside a wrapper. Composing a [[Lens]] with a
 * [[Prism]], in either order, gives an optional.
 *
 * When the part is missing, `getOption` returns `None`, and `replace` and
 * `modify` return a value equal to `s`. The `*OrFail` methods say why in an
 * [[OpticCheck]]: another case ([[OpticCheck.UnexpectedCase]]), an index past
 * the end ([[OpticCheck.SequenceIndexOutOfBounds]]), a missing key
 * ([[OpticCheck.MissingKey]]), or a wrapper whose `wrap` or `unwrap` throws a
 * [[SchemaError]] ([[OpticCheck.WrappingError]]). `replace` never adds a
 * missing element or key.
 *
 * {{{
 * case class Book(title: String, tags: List[String])
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val firstTag: Optional[Book, String] = $(_.tags.at(0))
 * }
 *
 * Book.firstTag.getOption(Book("Zionomicon", List("zio", "scala"))) // Some(zio)
 * Book.firstTag.getOption(Book("Zionomicon", Nil))                  // None
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
sealed trait Optional[S, A] extends Optic[S, A] {

  /**
   * Returns the part of `s` that this optional focuses on, or `None` when it is
   * missing.
   *
   * {{{
   * case class Book(title: String, tags: List[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val firstTag: Optional[Book, String] = $(_.tags.at(0))
   * }
   *
   * Book.firstTag.getOption(Book("Zionomicon", List("zio", "scala"))) // Some(zio)
   * Book.firstTag.getOption(Book("Zionomicon", Nil))                  // None
   * }}}
   */
  def getOption(s: S): Option[A]

  /**
   * Like `getOption`, but returns `Left` with an [[OpticCheck]] that says why
   * the part is missing, for example
   * `During attempted access at .tags.at(0), encountered a sequence out of bounds at .tags.at(0): index is 0, but size is 0`.
   */
  def getOrFail(s: S): Either[OpticCheck, A] =
    getOption(s) match {
      case Some(a) => new Right(a)
      case _       => new Left(check(s).get)
    }

  /**
   * Returns a copy of `s` with the part this optional focuses on replaced by
   * `a`. When the part is missing, or a `wrapped` step on the path throws a
   * [[SchemaError]], the result equals `s`.
   *
   * {{{
   * case class Book(title: String, tags: List[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val firstTag: Optional[Book, String] = $(_.tags.at(0))
   * }
   *
   * Book.firstTag.replace(Book("Zionomicon", List("zio", "scala")), "effects") // Book(Zionomicon,List(effects, scala))
   * Book.firstTag.replace(Book("Zionomicon", Nil), "effects")                  // Book(Zionomicon,List())
   * }}}
   */
  def replace(s: S, a: A): S

  /**
   * Like `replace`, but returns `None` when the part is missing or a `wrapped`
   * step on the path throws a [[SchemaError]].
   */
  def replaceOption(s: S, a: A): Option[S]

  /**
   * Like `replace`, but returns `Left` with an [[OpticCheck]] when the part is
   * missing, or with an [[OpticCheck.WrappingError]] when a `wrapped` step on
   * the path throws a [[SchemaError]].
   */
  def replaceOrFail(s: S, a: A): Either[OpticCheck, S]

  /**
   * Composes this optional with `that` lens. The result is an [[Optional]].
   */
  def apply[B](that: Lens[A, B]): Optional[S, B] = Optional(this, that)

  /**
   * Composes this optional with `that` prism. The result is an [[Optional]].
   */
  def apply[B <: A](that: Prism[A, B]): Optional[S, B] = Optional(this, that)

  /**
   * Composes this optional with `that` optional. The result is an [[Optional]].
   */
  def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  /**
   * Composes this optional with `that` traversal. The result is a
   * [[Traversal]].
   */
  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
}

/**
 * Constructors for [[Optional]]: compositions of two optics that give an
 * optional, and optionals on an index, a map key or a wrapped value.
 */
object Optional {

  /**
   * Composes an optional with a lens. The same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T, A](first: Optional[S, T], second: Lens[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val optional1 = first.asInstanceOf[OptionalImpl[?, ?]]
    val lens2     = second.asInstanceOf[Lens.LensImpl[?, ?]]
    new OptionalImpl(
      optional1.sources ++ lens2.sources,
      optional1.focusTerms ++ lens2.focusTerms,
      optional1.params ++ new Array[Any](lens2.sources.length)
    )
  }

  /**
   * Composes an optional with a prism. The same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T, A <: T](first: Optional[S, T], second: Prism[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val optional1 = first.asInstanceOf[OptionalImpl[?, ?]]
    val prism2    = second.asInstanceOf[Prism.PrismImpl[?, ?]]
    new OptionalImpl(
      optional1.sources ++ prism2.sources,
      optional1.focusTerms ++ prism2.focusTerms,
      optional1.params ++ new Array[Any](prism2.sources.length)
    )
  }

  /**
   * Composes two optionals. The same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T, A](first: Optional[S, T], second: Optional[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val optional1 = first.asInstanceOf[OptionalImpl[?, ?]]
    val optional2 = second.asInstanceOf[OptionalImpl[?, ?]]
    new OptionalImpl(
      optional1.sources ++ optional2.sources,
      optional1.focusTerms ++ optional2.focusTerms,
      optional1.params ++ optional2.params
    )
  }

  /**
   * Composes a lens with a prism. The same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T, A <: T](first: Lens[S, T], second: Prism[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val lens1  = first.asInstanceOf[Lens.LensImpl[?, ?]]
    val prism2 = second.asInstanceOf[Prism.PrismImpl[?, ?]]
    new OptionalImpl(
      lens1.sources ++ prism2.sources,
      lens1.focusTerms ++ prism2.focusTerms,
      new Array[Any](lens1.sources.length + prism2.sources.length)
    )
  }

  /**
   * Composes a lens with an optional. The same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T, A](first: Lens[S, T], second: Optional[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val lens1     = first.asInstanceOf[Lens.LensImpl[?, ?]]
    val optional2 = second.asInstanceOf[OptionalImpl[?, ?]]
    new OptionalImpl(
      lens1.sources ++ optional2.sources,
      lens1.focusTerms ++ optional2.focusTerms,
      new Array[Any](lens1.sources.length) ++ optional2.params
    )
  }

  /**
   * Composes a prism with a lens. The same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T <: S, A](first: Prism[S, T], second: Lens[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val prism1 = first.asInstanceOf[Prism.PrismImpl[?, ?]]
    val lens2  = second.asInstanceOf[Lens.LensImpl[?, ?]]
    new OptionalImpl(
      prism1.sources ++ lens2.sources,
      prism1.focusTerms ++ lens2.focusTerms,
      new Array[Any](prism1.sources.length + lens2.sources.length)
    )
  }

  /**
   * Composes a prism with an optional. The same as `first(second)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `first` or `second` is null
   */
  def apply[S, T <: S, A](first: Prism[S, T], second: Optional[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val prism1    = first.asInstanceOf[Prism.PrismImpl[?, ?]]
    val optional2 = second.asInstanceOf[OptionalImpl[?, ?]]
    new OptionalImpl(
      prism1.sources ++ optional2.sources,
      prism1.focusTerms ++ optional2.focusTerms,
      new Array[Any](prism1.sources.length) ++ optional2.params
    )
  }

  /**
   * Creates an optional on the element at `index` of a sequence. A sequence
   * with `index` elements or fewer has no focus
   * ([[OpticCheck.SequenceIndexOutOfBounds]]).
   *
   * @throws java.lang.IllegalArgumentException
   *   if `seq` is null or `index` is negative
   */
  def at[A, C[_]](seq: Reflect.Sequence.Bound[A, C], index: Int): Optional[C[A], A] = {
    require((seq ne null) && index >= 0)
    new OptionalImpl(Array(seq), Array(seq.element.asTerm("at")), Array[Any](index))
  }

  /**
   * Creates an optional on the value at `key` in a map. A map without `key` has
   * no focus ([[OpticCheck.MissingKey]]), and `replace` does not add the key.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `map` is null
   */
  def atKey[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M], key: K): Optional[M[K, V], V] = {
    require(map ne null)
    new OptionalImpl(Array(map), Array(map.value.asTerm("atKey")), Array[Any](key))
  }

  /**
   * Creates an optional on the value inside a wrapper, such as a schema built
   * with `Schema#transform`. It reads with the wrapper's `unwrap` and writes
   * with its `wrap`. When either throws a [[SchemaError]], the focus counts as
   * missing ([[OpticCheck.WrappingError]]); other exceptions propagate.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `wrapper` is null
   */
  def wrapped[A, B](wrapper: Reflect.Wrapper.Bound[A, B]): Optional[A, B] = {
    require(wrapper ne null)
    new OptionalImpl(Array(wrapper), Array(wrapper.wrapped.asTerm("wrapped")), Array[Any](null))
  }

  private[schema] case class OptionalImpl[S, A](
    sources: Array[Reflect.Bound[?]],
    focusTerms: Array[Term.Bound[?, ?]],
    params: Array[Any]
  ) extends Optional[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = 0L

    type Key
    type Value
    type Map[_, _]
    type Elem
    type Col[_]
    type Wrapping
    type Wrapped

    private[this] def init(): Unit = {
      var offset   = 0L
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var idx      = 0
      while (idx < len) {
        val focusTermName = focusTerms(idx).name
        sources(idx) match {
          case record: Reflect.Record.Bound[?] =>
            bindings(idx) = new LensBinding(
              deconstructor = record.deconstructor.asInstanceOf[Deconstructor[Any]],
              constructor = record.constructor.asInstanceOf[Constructor[Any]],
              register = record.registers(record.fieldIndexByName(focusTermName)),
              offset = offset
            )
            offset = RegisterOffset.add(offset, record.usedRegisters)
          case variant: Reflect.Variant.Bound[?] =>
            bindings(idx) = new PrismBinding(
              matcher = variant.matchers.apply(variant.caseIndexByName(focusTermName)),
              discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
            )
          case wrapper: Reflect.Wrapper.Bound[Wrapping, Wrapped] @scala.unchecked =>
            bindings(idx) = new WrappedBinding(
              wrap = wrapper.binding.wrap,
              unwrap = wrapper.binding.unwrap
            )
          case sequence: Reflect.Sequence.Bound[Elem, Col] @scala.unchecked =>
            bindings(idx) = new AtBinding(
              seqDeconstructor = sequence.seqDeconstructor,
              seqConstructor = sequence.seqConstructor,
              index = params(idx).asInstanceOf[Int],
              elemClassTag = sequence.elemClassTag
            )
          case source =>
            val map = source.asInstanceOf[Reflect.Map.Bound[Key, Value, Map]]
            bindings(idx) = new AtKeyBinding(
              mapDeconstructor = map.mapDeconstructor,
              mapConstructor = map.mapConstructor,
              keySchema = map.key,
              key = params(idx).asInstanceOf[Key]
            )
        }
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def check(s: S): Option[OpticCheck] = {
      if (bindings eq null) init()
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.deconstructor.deconstruct(registers, offset, x)
            x = lensBinding.register.get(registers, offset)
          case prismBinding: PrismBinding =>
            val lastX = x
            x = prismBinding.matcher.downcastOrNull(x)
            if (x == null) {
              val actualCaseIdx  = prismBinding.discriminator.discriminate(lastX)
              val actualCase     = sources(idx).asInstanceOf[Reflect.Variant.Bound[Any]].cases(actualCaseIdx).name
              val focusTermName  = focusTerms(idx).name
              val unexpectedCase =
                new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), lastX)
              return new Some(new OpticCheck(new ::(unexpectedCase, Nil)))
            }
          case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
            try {
              x = wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            } catch {
              case error: SchemaError =>
                val wrappingError = new OpticCheck.WrappingError(toDynamic, toDynamic(idx), error)
                return new Some(new OpticCheck(new ::(wrappingError, Nil)))
            }
          case atBinding: AtBinding[Col] @scala.unchecked =>
            val deconstructor = atBinding.seqDeconstructor
            val col           = x.asInstanceOf[Col[A]]
            deconstructor match {
              case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
                val colSize = indexed.size(col)
                val colIdx  = atBinding.index
                if (colSize <= colIdx) {
                  val sequenceIndexOutOfBounds =
                    new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, colSize)
                  return new Some(new OpticCheck(new ::(sequenceIndexOutOfBounds, Nil)))
                }
                indexed.elementType(col) match {
                  case _: RegisterType.Boolean.type => x = indexed.booleanAt(x.asInstanceOf[Col[Boolean]], colIdx)
                  case _: RegisterType.Byte.type    => x = indexed.byteAt(x.asInstanceOf[Col[Byte]], colIdx)
                  case _: RegisterType.Char.type    => x = indexed.charAt(x.asInstanceOf[Col[Char]], colIdx)
                  case _: RegisterType.Short.type   => x = indexed.shortAt(x.asInstanceOf[Col[Short]], colIdx)
                  case _: RegisterType.Float.type   => x = indexed.floatAt(x.asInstanceOf[Col[Float]], colIdx)
                  case _: RegisterType.Int.type     => x = indexed.intAt(x.asInstanceOf[Col[Int]], colIdx)
                  case _: RegisterType.Double.type  => x = indexed.doubleAt(x.asInstanceOf[Col[Double]], colIdx)
                  case _: RegisterType.Long.type    => x = indexed.longAt(x.asInstanceOf[Col[Long]], colIdx)
                  case _                            => x = indexed.objectAt(x.asInstanceOf[Col[AnyRef]], colIdx)
                }
              case _ =>
                val it      = deconstructor.deconstruct(col)
                val colIdx  = atBinding.index
                var currIdx = 0
                while (currIdx < colIdx && it.hasNext) {
                  it.next(): Unit
                  currIdx += 1
                }
                if (currIdx == colIdx && it.hasNext) x = it.next()
                else {
                  val sequenceIndexOutOfBounds =
                    new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, currIdx)
                  return new Some(new OpticCheck(new ::(sequenceIndexOutOfBounds, Nil)))
                }
            }
          case binding =>
            val atKeyBinding  = binding.asInstanceOf[AtKeyBinding[Key, Map]]
            val deconstructor = atKeyBinding.mapDeconstructor
            val key           = atKeyBinding.key
            deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
              case Some(value) =>
                x = value
              case _ =>
                return new Some(new OpticCheck(new ::(new OpticCheck.MissingKey(toDynamic, toDynamic(idx), key), Nil)))
            }
        }
        idx += 1
      }
      None
    }

    def getOption(s: S): Option[A] = {
      if (bindings eq null) init()
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.deconstructor.deconstruct(registers, offset, x)
            x = lensBinding.register.get(registers, offset)
          case prismBinding: PrismBinding =>
            x = prismBinding.matcher.downcastOrNull(x)
            if (x == null) return None
          case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
            try {
              x = wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            } catch {
              case _: SchemaError => return None
            }
          case atBinding: AtBinding[Col] @scala.unchecked =>
            val deconstructor = atBinding.seqDeconstructor
            val col           = x.asInstanceOf[Col[A]]
            deconstructor match {
              case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
                val colSize = indexed.size(col)
                val colIdx  = atBinding.index
                if (colSize <= colIdx) return None
                indexed.elementType(col) match {
                  case _: RegisterType.Boolean.type => x = indexed.booleanAt(x.asInstanceOf[Col[Boolean]], colIdx)
                  case _: RegisterType.Byte.type    => x = indexed.byteAt(x.asInstanceOf[Col[Byte]], colIdx)
                  case _: RegisterType.Char.type    => x = indexed.charAt(x.asInstanceOf[Col[Char]], colIdx)
                  case _: RegisterType.Short.type   => x = indexed.shortAt(x.asInstanceOf[Col[Short]], colIdx)
                  case _: RegisterType.Float.type   => x = indexed.floatAt(x.asInstanceOf[Col[Float]], colIdx)
                  case _: RegisterType.Int.type     => x = indexed.intAt(x.asInstanceOf[Col[Int]], colIdx)
                  case _: RegisterType.Double.type  => x = indexed.doubleAt(x.asInstanceOf[Col[Double]], colIdx)
                  case _: RegisterType.Long.type    => x = indexed.longAt(x.asInstanceOf[Col[Long]], colIdx)
                  case _                            => x = indexed.objectAt(x.asInstanceOf[Col[AnyRef]], colIdx)
                }
              case _ =>
                val it      = deconstructor.deconstruct(col)
                val colIdx  = atBinding.index
                var currIdx = 0
                while (currIdx < colIdx && it.hasNext) {
                  it.next(): Unit
                  currIdx += 1
                }
                if (currIdx == colIdx && it.hasNext) x = it.next()
                else return None
            }
          case binding =>
            val atKeyBinding  = binding.asInstanceOf[AtKeyBinding[Key, Map]]
            val deconstructor = atKeyBinding.mapDeconstructor
            deconstructor.get(x.asInstanceOf[Map[Key, Value]], atKeyBinding.key) match {
              case Some(value) => x = value
              case _           => return None
            }
        }
        idx += 1
      }
      new Some(x.asInstanceOf[A])
    }

    def replace(s: S, a: A): S = {
      if (bindings eq null) init()
      try modifyRecursive(Registers(usedRegisters), 0, s, _ => a).asInstanceOf[S]
      catch {
        case _: OpticCheckBuilder => s
      }
    }

    def replaceOption(s: S, a: A): Option[S] = {
      if (bindings eq null) init()
      try {
        var success = false
        val x       = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          _ => {
            success = true
            a
          }
        )
        if (success) new Some(x.asInstanceOf[S])
        else None
      } catch {
        case _: OpticCheckBuilder => None
      }
    }

    def replaceOrFail(s: S, a: A): Either[OpticCheck, S] = {
      if (bindings eq null) init()
      try {
        var success = false
        val x       = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          _ => {
            success = true
            a
          }
        )
        if (success) new Right(x.asInstanceOf[S])
        else new Left(check(s).get)
      } catch {
        case ocb: OpticCheckBuilder => new Left(ocb.toOpticCheck())
      }
    }

    def modify(s: S, f: A => A): S = {
      if (bindings eq null) init()
      try modifyRecursive(Registers(usedRegisters), 0, s, f).asInstanceOf[S]
      catch {
        case _: OpticCheckBuilder => s
      }
    }

    private[this] def modifyRecursive(registers: Registers, idx: Int, x: Any, f: A => A): Any =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          var x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 == bindings.length) x1 = f(x1.asInstanceOf[A])
          else x1 = modifyRecursive(registers, idx + 1, x1, f)
          lensBinding.register.set(registers, offset, x1)
          lensBinding.constructor.construct(registers, offset)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) x
          else if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
          else modifyRecursive(registers, idx + 1, x1, f)
        case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
          val x1 =
            try wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            catch {
              case error: SchemaError => throw toOpticCheckBuilder(idx, error)
            }
          try
            wrapperBinding.wrap({
              if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
              else modifyRecursive(registers, idx + 1, x1, f)
            }.asInstanceOf[Wrapped])
          catch {
            case error: SchemaError => throw toOpticCheckBuilder(idx, error)
          }
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val constructor   = atBinding.seqConstructor
          val colIdx        = atBinding.index
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length)
            modifySeqAt(deconstructor, constructor, col, f, colIdx, atBinding.elemClassTag)
          else {
            val sizeHint =
              deconstructor match {
                case indexed: SeqDeconstructor.SpecializedIndexed[Col] => indexed.size(col)
                case _                                                 => 8
              }
            implicit val classTag: ClassTag[Any] = atBinding.elemClassTag.asInstanceOf[ClassTag[Any]]
            val builder                          = constructor.newBuilder[Any](sizeHint)
            val it                               = deconstructor.deconstruct(col)
            var currIdx                          = 0
            while (it.hasNext) {
              constructor.add(
                builder, {
                  val value = it.next()
                  if (currIdx != colIdx) value
                  else modifyRecursive(registers, idx + 1, value, f)
                }
              )
              currIdx += 1
            }
            constructor.result(builder)
          }
        case binding =>
          val atKeyBinding  = binding.asInstanceOf[AtKeyBinding[Key, Map]]
          val deconstructor = atKeyBinding.mapDeconstructor
          val constructor   = atKeyBinding.mapConstructor
          val map           = x.asInstanceOf[Map[Key, Any]]
          val key           = atKeyBinding.key
          deconstructor.get(map, key) match {
            case Some(value) =>
              constructor.updated(
                map,
                key,
                if (idx + 1 == bindings.length) f(value.asInstanceOf[A])
                else modifyRecursive(registers, idx + 1, value, f)
              )
            case _ => map
          }
      }

    private[this] def modifySeqAt(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      s: Col[A],
      f: A => A,
      colIdx: Int,
      elemClassTag: ClassTag[?]
    ): Col[A] = {
      implicit val classTag: ClassTag[A] = elemClassTag.asInstanceOf[ClassTag[A]]
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size    = indexed.size(s)
          val builder = constructor.newBuilder[A](size)
          var idx     = 0
          while (idx < size) {
            constructor.add(
              builder, {
                val value = indexed.objectAt(s, idx)
                if (idx == colIdx) f(value)
                else value
              }
            )
            idx += 1
          }
          constructor.result(builder)
        case _ =>
          val builder = constructor.newBuilder[A]()
          val it      = deconstructor.deconstruct(s)
          var currIdx = -1
          while (it.hasNext)
            constructor.add(
              builder, {
                currIdx += 1
                val value = it.next()
                if (currIdx != colIdx) value
                else f(value)
              }
            )
          constructor.result(builder)
      }
    }

    def modifyOption(s: S, f: A => A): Option[S] = {
      if (bindings eq null) init()
      try {
        var success = false
        val x       = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          a => {
            success = true
            f(a)
          }
        )
        if (success) new Some(x.asInstanceOf[S])
        else None
      } catch {
        case _: OpticCheckBuilder => None
      }
    }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
      if (bindings eq null) init()
      try {
        var success = false
        val x       = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          a => {
            success = true
            f(a)
          }
        )
        if (success) new Right(x.asInstanceOf[S])
        else new Left(check(s).get)
      } catch {
        case ocb: OpticCheckBuilder => new Left(ocb.toOpticCheck())
      }
    }

    private[this] def toOpticCheckBuilder(idx: Int, error: SchemaError): OpticCheckBuilder =
      new OpticCheckBuilder(toOpticCheck =
        () => new OpticCheck(new ::(new OpticCheck.WrappingError(toDynamic, toDynamic(idx), error), Nil))
      )

    lazy val toDynamic: DynamicOptic = new DynamicOptic({
      if (bindings eq null) init()
      val nodes = ChunkBuilder.make[DynamicOptic.Node]()
      val len   = bindings.length
      var idx   = 0
      while (idx < len) {
        nodes.addOne {
          bindings(idx) match {
            case _: LensBinding =>
              new DynamicOptic.Node.Field(focusTerms(idx).name)
            case _: PrismBinding =>
              new DynamicOptic.Node.Case(focusTerms(idx).name)
            case _: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
              DynamicOptic.Node.Wrapped
            case at: AtBinding[Col] @scala.unchecked =>
              new DynamicOptic.Node.AtIndex(at.index)
            case binding =>
              val atKeyBinding = binding.asInstanceOf[AtKeyBinding[Key, Map]]
              new DynamicOptic.Node.AtMapKey(atKeyBinding.keySchema.toDynamicValue(atKeyBinding.key))
          }
        }
        idx += 1
      }
      nodes.result()
    })

    override def toString: String = {
      if (bindings eq null) init()
      val sb  = new java.lang.StringBuilder
      val len = bindings.length
      var idx = 0
      while (idx < len) {
        bindings(idx) match {
          case _: LensBinding =>
            sb.append('.').append(focusTerms(idx).name)
          case _: PrismBinding =>
            sb.append(".when[").append(focusTerms(idx).name).append(']')
          case _: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
            sb.append(".wrapped[").append(focus.typeId.name).append(']')
          case at: AtBinding[Col] @scala.unchecked =>
            sb.append(".at(").append(at.index).append(')')
          case _ =>
            sb.append(".atKey(<key>)")
        }
        idx += 1
      }
      s"Optional(_${sb.toString})"
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(params.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: OptionalImpl[?, ?] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.params.asInstanceOf[Array[AnyRef]], params.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

/**
 * A traversal focuses on zero or more parts of `S` at once: every element of a
 * collection, the elements at some indices, every key or value of a map, the
 * values at some keys, or every value of a type found anywhere in `S`
 * (`searchFor`). Composing any optic with a traversal gives a traversal.
 *
 * `fold` and `reduceOrFail` read the parts, and `modify` updates all of them.
 * When there are no parts, for example in an empty collection, `fold` returns
 * its `zero`, `modify` returns a value equal to `s`, `modifyOption` returns
 * `None`, and `modifyOrFail` and `reduceOrFail` return `Left` with an
 * [[OpticCheck]] ([[OpticCheck.EmptySequence]] for an empty sequence).
 *
 * {{{
 * case class Book(title: String, tags: List[String])
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val tags: Traversal[Book, String] = $(_.tags.each)
 * }
 *
 * val zionomicon = Book("Zionomicon", List("zio", "scala"))
 * Book.tags.fold[Int](zionomicon)(0, (count, _) => count + 1) // 2
 * Book.tags.modify(zionomicon, _.toUpperCase)                 // Book(Zionomicon,List(ZIO, SCALA))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
sealed trait Traversal[S, A] extends Optic[S, A] { self =>

  /**
   * Combines the parts of `s` that this traversal focuses on with `f`, starting
   * from `zero`, in the order of the underlying collections. Returns `zero`
   * when there are no parts. On Scala 2, give `Z` explicitly, as in
   * `fold[Int](s)(0, ...)`: Scala 2 cannot infer the parameter types of `f`
   * from `zero` in the same parameter list.
   *
   * {{{
   * case class Book(title: String, tags: List[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val tags: Traversal[Book, String] = $(_.tags.each)
   * }
   *
   * Book.tags.fold[List[String]](Book("Zionomicon", List("zio", "scala")))(Nil, (acc, tag) => tag :: acc)
   * // List(scala, zio)
   * }}}
   */
  def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z

  /**
   * Combines the parts of `s` with `f`, starting from the first part. Returns
   * `Left` with the [[OpticCheck]] from `check` when there are no parts.
   */
  def reduceOrFail(s: S)(f: (A, A) => A): Either[OpticCheck, A] = {
    var one     = false
    val reduced = fold[A](s)(
      null.asInstanceOf[A],
      (acc, a) => {
        if (one) f(acc, a)
        else {
          one = true
          a
        }
      }
    )
    if (one) new Right(reduced)
    else new Left(check(s).get)
  }

  /**
   * Composes this traversal with `that` lens. The result is a [[Traversal]].
   */
  def apply[B](that: Lens[A, B]): Traversal[S, B] = Traversal(this, that)

  /**
   * Composes this traversal with `that` prism. The result is a [[Traversal]]
   * over the parts that are the case `B`.
   */
  def apply[B <: A](that: Prism[A, B]): Traversal[S, B] = Traversal(this, that)

  /**
   * Composes this traversal with `that` optional. The result is a [[Traversal]]
   * over the parts where `that` finds its focus.
   */
  def apply[B](that: Optional[A, B]): Traversal[S, B] = Traversal(this, that)

  /**
   * Composes this traversal with `that` traversal. The result is a
   * [[Traversal]].
   */
  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
}

/**
 * Constructors for [[Traversal]]: compositions of two optics that give a
 * traversal, and traversals over sequences and maps.
 */
object Traversal {

  /**
   * Creates a traversal on the elements at `indices` of a sequence, visited in
   * increasing index order whatever the order of `indices`. Indices past the
   * end of a sequence are skipped; `check` reports them as
   * [[OpticCheck.SequenceIndexOutOfBounds]]. When another step follows
   * `atIndices` and the sequence is not indexed (a `List`, for example),
   * `check` also reports reachable indices after the first as out of bounds;
   * `fold` and `modify` are not affected (a bug, see `docs/dev/issues.md`).
   *
   * @throws java.lang.IllegalArgumentException
   *   if `seq` or `indices` is null, `indices` is empty, or it holds a negative
   *   or a repeated index
   */
  def atIndices[A, C[_]](seq: Reflect.Sequence.Bound[A, C], indices: Seq[Int]): Traversal[C[A], A] = {
    require((seq ne null) && (indices ne null) && indices.nonEmpty)
    val sortedIndices = indices.toArray
    java.util.Arrays.sort(sortedIndices)
    var prev = sortedIndices(0)
    require(prev >= 0)
    var idx = 1
    while (idx < sortedIndices.length) {
      val curr = sortedIndices(idx)
      require(prev < curr)
      prev = curr
      idx += 1
    }
    new TraversalImpl(Array(seq), Array(seq.element.asTerm("atIndices")), Array[Any](sortedIndices))
  }

  /**
   * Creates a traversal on the values at `keys` in a map, visited in the order
   * of `keys`. Keys the map does not have are skipped; `check` reports them as
   * [[OpticCheck.MissingKey]].
   *
   * @throws java.lang.IllegalArgumentException
   *   if `map` or `keys` is null, or `keys` is empty
   */
  def atKeys[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M], keys: Seq[K]): Traversal[M[K, V], V] = {
    require((map ne null) && (keys ne null) && keys.nonEmpty)
    new TraversalImpl(Array(map), Array(map.value.asTerm("atKeys")), Array[Any](keys))
  }

  /**
   * Composes two traversals. The same as `first(second)`.
   */
  def apply[S, T, A](first: Traversal[S, T], second: Traversal[T, A]): Traversal[S, A] =
    // Handle search-based traversal composition specially
    // Note: ComposedSearchTraversal and PrefixedSearchTraversal also extend Traversal but are not TraversalImpl
    (first, second) match {
      case (search1: SearchTraversal[S, T] @unchecked, search2: SearchTraversal[T, A] @unchecked) =>
        // SearchTraversal → SearchTraversal: compose via ComposedSearchTraversal
        new ComposedSearchTraversal(search1, search2)
      case (search: SearchTraversal[S, T] @unchecked, _) =>
        // SearchTraversal → Traversal: use ComposedSearchTraversal
        new ComposedSearchTraversal(search, second)
      case (composed: ComposedSearchTraversal[S, ?, T] @unchecked, _) =>
        // ComposedSearchTraversal → Traversal: delegate to instance method
        composed.apply(second)
      case (prefixed: PrefixedSearchTraversal[S, ?, T] @unchecked, _) =>
        // PrefixedSearchTraversal → Traversal: delegate to instance method
        prefixed.apply(second)
      case (_, search: SearchTraversal[T, A] @unchecked) =>
        // Traversal → SearchTraversal: use PrefixedSearchTraversal
        new PrefixedSearchTraversal(first, search)
      case (_, composed: ComposedSearchTraversal[T, ?, A] @unchecked) =>
        // Traversal → ComposedSearchTraversal: wrap in PrefixedSearchTraversal
        new PrefixedSearchTraversal(first, composed)
      case (_, prefixed: PrefixedSearchTraversal[T, ?, A] @unchecked) =>
        // Traversal → PrefixedSearchTraversal: wrap in PrefixedSearchTraversal
        new PrefixedSearchTraversal(first, prefixed)
      case _ =>
        // Regular TraversalImpl composition
        val traversal1 = first.asInstanceOf[TraversalImpl[?, ?]]
        val traversal2 = second.asInstanceOf[TraversalImpl[?, ?]]
        new TraversalImpl(
          traversal1.sources ++ traversal2.sources,
          traversal1.focusTerms ++ traversal2.focusTerms,
          traversal1.params ++ traversal2.params
        )
    }

  /**
   * Composes a traversal with a lens. The same as `first(second)`.
   */
  def apply[S, T, A](first: Traversal[S, T], second: Lens[T, A]): Traversal[S, A] =
    // Handle search-based traversal composition specially
    first match {
      case search: SearchTraversal[S, T] @unchecked =>
        new ComposedSearchTraversal(search, second)
      case composed: ComposedSearchTraversal[S, ?, T] @unchecked =>
        composed.apply(second)
      case prefixed: PrefixedSearchTraversal[S, ?, T] @unchecked =>
        prefixed.apply(second)
      case _ =>
        val traversal1 = first.asInstanceOf[TraversalImpl[?, ?]]
        val lens2      = second.asInstanceOf[Lens.LensImpl[?, ?]]
        new TraversalImpl(
          traversal1.sources ++ lens2.sources,
          traversal1.focusTerms ++ lens2.focusTerms,
          traversal1.params ++ new Array[Any](lens2.sources.length)
        )
    }

  /**
   * Composes a traversal with a prism. The same as `first(second)`.
   */
  def apply[S, T, A <: T](first: Traversal[S, T], second: Prism[T, A]): Traversal[S, A] =
    // Handle search-based traversal composition specially
    first match {
      case search: SearchTraversal[S, T] @unchecked =>
        new ComposedSearchTraversal(search, second)
      case composed: ComposedSearchTraversal[S, ?, T] @unchecked =>
        composed.apply(second)
      case prefixed: PrefixedSearchTraversal[S, ?, T] @unchecked =>
        prefixed.apply(second)
      case _ =>
        val traversal1 = first.asInstanceOf[TraversalImpl[?, ?]]
        val prism2     = second.asInstanceOf[Prism.PrismImpl[?, ?]]
        new TraversalImpl(
          traversal1.sources ++ prism2.sources,
          traversal1.focusTerms ++ prism2.focusTerms,
          traversal1.params ++ new Array[Any](prism2.sources.length)
        )
    }

  /**
   * Composes a traversal with an optional. The same as `first(second)`.
   */
  def apply[S, T, A](first: Traversal[S, T], second: Optional[T, A]): Traversal[S, A] =
    // Handle search-based traversal composition specially
    first match {
      case search: SearchTraversal[S, T] @unchecked =>
        new ComposedSearchTraversal(search, second)
      case composed: ComposedSearchTraversal[S, ?, T] @unchecked =>
        composed.apply(second)
      case prefixed: PrefixedSearchTraversal[S, ?, T] @unchecked =>
        prefixed.apply(second)
      case _ =>
        val traversal1 = first.asInstanceOf[TraversalImpl[?, ?]]
        val optional2  = second.asInstanceOf[Optional.OptionalImpl[?, ?]]
        new TraversalImpl(
          traversal1.sources ++ optional2.sources,
          traversal1.focusTerms ++ optional2.focusTerms,
          traversal1.params ++ optional2.params
        )
    }

  /**
   * Composes a lens with a traversal. The same as `first(second)`.
   */
  def apply[S, T, A](first: Lens[S, T], second: Traversal[T, A]): Traversal[S, A] =
    // Handle search-based traversal composition specially
    second match {
      case search: SearchTraversal[T, A] @unchecked =>
        new PrefixedSearchTraversal(first, search)
      case composed: ComposedSearchTraversal[T, ?, A] @unchecked =>
        new PrefixedSearchTraversal(first, composed)
      case prefixed: PrefixedSearchTraversal[T, ?, A] @unchecked =>
        new PrefixedSearchTraversal(first, prefixed)
      case _ =>
        val lens1      = first.asInstanceOf[Lens.LensImpl[?, ?]]
        val traversal2 = second.asInstanceOf[TraversalImpl[?, ?]]
        new TraversalImpl(
          lens1.sources ++ traversal2.sources,
          lens1.focusTerms ++ traversal2.focusTerms,
          new Array[Any](lens1.sources.length) ++ traversal2.params
        )
    }

  /**
   * Composes a prism with a traversal. The same as `first(second)`.
   */
  def apply[S, T <: S, A](first: Prism[S, T], second: Traversal[T, A]): Traversal[S, A] =
    // Handle search-based traversal composition specially
    second match {
      case search: SearchTraversal[T, A] @unchecked =>
        new PrefixedSearchTraversal(first, search)
      case composed: ComposedSearchTraversal[T, ?, A] @unchecked =>
        new PrefixedSearchTraversal(first, composed)
      case prefixed: PrefixedSearchTraversal[T, ?, A] @unchecked =>
        new PrefixedSearchTraversal(first, prefixed)
      case _ =>
        val prism1     = first.asInstanceOf[Prism.PrismImpl[?, ?]]
        val traversal2 = second.asInstanceOf[TraversalImpl[?, ?]]
        new TraversalImpl(
          prism1.sources ++ traversal2.sources,
          prism1.focusTerms ++ traversal2.focusTerms,
          new Array[Any](prism1.sources.length) ++ traversal2.params
        )
    }

  /**
   * Composes an optional with a traversal. The same as `first(second)`.
   */
  def apply[S, T, A](first: Optional[S, T], second: Traversal[T, A]): Traversal[S, A] =
    // Handle search-based traversal composition specially
    second match {
      case search: SearchTraversal[T, A] @unchecked =>
        new PrefixedSearchTraversal(first, search)
      case composed: ComposedSearchTraversal[T, ?, A] @unchecked =>
        new PrefixedSearchTraversal(first, composed)
      case prefixed: PrefixedSearchTraversal[T, ?, A] @unchecked =>
        new PrefixedSearchTraversal(first, prefixed)
      case _ =>
        val optional1  = first.asInstanceOf[Optional.OptionalImpl[?, ?]]
        val traversal2 = second.asInstanceOf[TraversalImpl[?, ?]]
        new TraversalImpl(
          optional1.sources ++ traversal2.sources,
          optional1.focusTerms ++ traversal2.focusTerms,
          optional1.params ++ traversal2.params
        )
    }

  /**
   * Creates a traversal over every element of a `List` whose elements have the
   * schema `reflect`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `reflect` is null
   */
  def listValues[A](reflect: Reflect.Bound[A]): Traversal[List[A], A] = {
    require(reflect ne null)
    seqValues(Reflect.list(reflect))
  }

  /**
   * Creates a traversal over every key of a map. `modify` rebuilds the map with
   * the new keys.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `map` is null
   */
  def mapKeys[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M]): Traversal[M[K, V], K] = {
    require(map ne null)
    new TraversalImpl(Array(map), Array(map.key.asTerm("key")), Array[Any](null))
  }

  /**
   * Creates a traversal over every value of a map.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `map` is null
   */
  def mapValues[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M]): Traversal[M[K, V], V] = {
    require(map ne null)
    new TraversalImpl(Array(map), Array(map.value.asTerm("value")), Array[Any](null))
  }

  /**
   * Creates a traversal over every element of a sequence, such as a `List`,
   * `Vector`, `Set` or `Chunk`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `seq` is null
   */
  def seqValues[A, C[_]](seq: Reflect.Sequence.Bound[A, C]): Traversal[C[A], A] = {
    require(seq ne null)
    new TraversalImpl(Array(seq), Array(seq.element.asTerm("element")), Array[Any](null))
  }

  /**
   * Creates a traversal over every element of a `Set` whose elements have the
   * schema `reflect`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `reflect` is null
   */
  def setValues[A](reflect: Reflect.Bound[A]): Traversal[Set[A], A] = {
    require(reflect ne null)
    seqValues(Reflect.set(reflect))
  }

  /**
   * Creates a traversal over every element of a `Vector` whose elements have
   * the schema `reflect`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `reflect` is null
   */
  def vectorValues[A](reflect: Reflect.Bound[A]): Traversal[Vector[A], A] = {
    require(reflect ne null)
    seqValues(Reflect.vector(reflect))
  }

  private[schema] case class TraversalImpl[S, A](
    sources: Array[Reflect.Bound[?]],
    focusTerms: Array[Term.Bound[?, ?]],
    params: Array[Any]
  ) extends Traversal[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = 0L

    type Key
    type Value
    type Map[_, _]
    type Elem
    type Col[_]
    type Wrapping
    type Wrapped

    private[this] def init(): Unit = {
      var offset   = 0L
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var idx      = 0
      while (idx < len) {
        val focusTermName = focusTerms(idx).name
        sources(idx) match {
          case record: Reflect.Record.Bound[?] =>
            bindings(idx) = new LensBinding(
              deconstructor = record.deconstructor.asInstanceOf[Deconstructor[Any]],
              constructor = record.constructor.asInstanceOf[Constructor[Any]],
              register = record.registers(record.fieldIndexByName(focusTermName)),
              offset = offset
            )
            offset = RegisterOffset.add(offset, record.usedRegisters)
          case variant: Reflect.Variant.Bound[?] =>
            bindings(idx) = new PrismBinding(
              matcher = variant.matchers.apply(variant.caseIndexByName(focusTermName)),
              discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
            )
          case wrapper: Reflect.Wrapper.Bound[Wrapping, Wrapped] @scala.unchecked =>
            bindings(idx) = new WrappedBinding(
              wrap = wrapper.binding.wrap,
              unwrap = wrapper.binding.unwrap
            )
          case sequence: Reflect.Sequence.Bound[Elem, Col] @scala.unchecked =>
            if (focusTermName == "at") {
              bindings(idx) = new AtBinding[Col](
                seqDeconstructor = sequence.seqDeconstructor,
                seqConstructor = sequence.seqConstructor,
                index = params(idx).asInstanceOf[Int],
                elemClassTag = sequence.elemClassTag
              )
            } else if (focusTermName == "atIndices") {
              bindings(idx) = new AtIndicesBinding[Col](
                seqDeconstructor = sequence.seqDeconstructor,
                seqConstructor = sequence.seqConstructor,
                indices = params(idx).asInstanceOf[Array[Int]],
                elemClassTag = sequence.elemClassTag
              )
            } else {
              bindings(idx) = new SeqBinding[Col](
                seqDeconstructor = sequence.seqDeconstructor,
                seqConstructor = sequence.seqConstructor,
                elemClassTag = sequence.elemClassTag
              )
            }
          case source =>
            val map = source.asInstanceOf[Reflect.Map.Bound[Key, Value, Map]]
            if (focusTermName == "atKey") {
              bindings(idx) = new AtKeyBinding[Key, Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor,
                keySchema = map.key,
                key = params(idx).asInstanceOf[Key]
              )
            } else if (focusTermName == "atKeys") {
              bindings(idx) = new AtKeysBinding[Key, Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor,
                keySchema = map.key,
                keys = params(idx).asInstanceOf[Seq[Key]]
              )
            } else if (focusTermName == "key") {
              bindings(idx) = new MapKeyBinding[Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor
              )
            } else {
              bindings(idx) = new MapValueBinding[Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor
              )
            }
        }
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def check(s: S): Option[OpticCheck] = {
      if (bindings eq null) init()
      val errors = List.newBuilder[OpticCheck.Single]
      checkRecursive(Registers(usedRegisters), 0, s, errors)
      errors.result() match {
        case errs: ::[OpticCheck.Single] => new Some(new OpticCheck(errs))
        case _                           => None
      }
    }

    private[this] def checkRecursive(
      registers: Registers,
      idx: Int,
      x: Any,
      errors: mutable.Builder[OpticCheck.Single, List[OpticCheck.Single]]
    ): Unit =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          val x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, x1, errors)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) {
            val actualCaseIdx = prismBinding.discriminator.discriminate(x)
            val actualCase    = sources(idx).asInstanceOf[Reflect.Variant.Bound[Any]].cases(actualCaseIdx).name
            val focusTermName = focusTerms(idx).name
            errors.addOne(new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), x))
          } else if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, x1, errors)
        case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
          try {
            val x1 = wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, x1, errors)
          } catch {
            case error: SchemaError =>
              errors.addOne(new OpticCheck.WrappingError(toDynamic, toDynamic(idx), error))
          }
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val col           = x.asInstanceOf[Col[A]]
          deconstructor match {
            case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
              val colSize = indexed.size(col)
              val colIdx  = atBinding.index
              if (colSize <= colIdx) {
                errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, colSize))
              } else if (idx + 1 != bindings.length) {
                checkRecursive(registers, idx + 1, indexed.objectAt(col, colIdx), errors)
              }
            case _ =>
              val it      = deconstructor.deconstruct(col)
              val colIdx  = atBinding.index
              var currIdx = 0
              while (currIdx < colIdx && it.hasNext) {
                it.next(): Unit
                currIdx += 1
              }
              if (currIdx != colIdx || !it.hasNext) {
                errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, currIdx))
              } else if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, it.next(), errors)
          }
        case atKeyBinding: AtKeyBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeyBinding.mapDeconstructor
          val key           = atKeyBinding.key
          deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
            case Some(value) => if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, value, errors)
            case _           => errors.addOne(new OpticCheck.MissingKey(toDynamic, toDynamic(idx), key))
          }
        case atIndicesBinding: AtIndicesBinding[Col] @scala.unchecked =>
          val deconstructor = atIndicesBinding.seqDeconstructor
          val col           = x.asInstanceOf[Col[A]]
          deconstructor match {
            case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
              val colSize    = indexed.size(col)
              val indices    = atIndicesBinding.indices
              var indicesIdx = 0
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize <= colIdx) {
                  errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, colSize))
                } else if (idx + 1 != bindings.length) {
                  checkRecursive(registers, idx + 1, indexed.objectAt(col, colIdx), errors)
                }
                indicesIdx += 1
              }
            case _ =>
              val it                  = deconstructor.deconstruct(col)
              val indices             = atIndicesBinding.indices
              var currIdx, indicesIdx = 0
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                while (currIdx < colIdx && it.hasNext) {
                  it.next(): Unit
                  currIdx += 1
                }
                if (currIdx != colIdx || !it.hasNext) {
                  errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, currIdx))
                } else if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, it.next(), errors)
                indicesIdx += 1
              }
          }
        case atKeysBinding: AtKeysBinding[Key, Map] @scala.unchecked =>
          atKeysBinding.keys.foreach {
            val deconstructor = atKeysBinding.mapDeconstructor
            key =>
              deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
                case Some(value) => if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, value, errors)
                case _           => errors.addOne(new OpticCheck.MissingKey(toDynamic, toDynamic(idx), key))
              }
          }
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Col[Elem]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptySequence(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRecursive(registers, idx + 1, it.next(), errors)
          }
        case mapKeyBinding: MapKeyBinding[Map] @scala.unchecked =>
          val deconstructor = mapKeyBinding.mapDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptyMap(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRecursive(registers, idx + 1, deconstructor.getKey(it.next()), errors)
          }
        case binding =>
          val mapValueBinding = binding.asInstanceOf[MapValueBinding[Map]]
          val deconstructor   = mapValueBinding.mapDeconstructor
          val it              = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptyMap(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRecursive(registers, idx + 1, deconstructor.getValue(it.next()), errors)
          }
      }

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z = {
      if (bindings eq null) init()
      foldRecursive(Registers(usedRegisters), 0, s, zero, f)
    }

    private[this] def foldRecursive[Z](registers: Registers, idx: Int, x: Any, zero: Z, f: (Z, A) => Z): Z =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          val x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 == bindings.length) f(zero, x1.asInstanceOf[A])
          else foldRecursive(registers, idx + 1, x1, zero, f)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) zero
          else if (idx + 1 == bindings.length) f(zero, x1.asInstanceOf[A])
          else foldRecursive(registers, idx + 1, x1, zero, f)
        case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
          try {
            val x1 = wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            if (idx + 1 == bindings.length) f(zero, x1.asInstanceOf[A])
            else foldRecursive(registers, idx + 1, x1, zero, f)
          } catch {
            case _: SchemaError => zero
          }
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val col           = x.asInstanceOf[Col[A]]
          deconstructor match {
            case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
              val colSize = indexed.size(col)
              val colIdx  = atBinding.index
              if (colSize <= colIdx) zero
              else if (idx + 1 == bindings.length) {
                f(
                  zero,
                  (
                    indexed.elementType(col) match {
                      case _: RegisterType.Boolean.type => indexed.booleanAt(col.asInstanceOf[Col[Boolean]], colIdx)
                      case _: RegisterType.Byte.type    => indexed.byteAt(col.asInstanceOf[Col[Byte]], colIdx)
                      case _: RegisterType.Char.type    => indexed.charAt(col.asInstanceOf[Col[Char]], colIdx)
                      case _: RegisterType.Short.type   => indexed.shortAt(col.asInstanceOf[Col[Short]], colIdx)
                      case _: RegisterType.Float.type   => indexed.floatAt(col.asInstanceOf[Col[Float]], colIdx)
                      case _: RegisterType.Int.type     => indexed.intAt(col.asInstanceOf[Col[Int]], colIdx)
                      case _: RegisterType.Double.type  => indexed.doubleAt(col.asInstanceOf[Col[Double]], colIdx)
                      case _: RegisterType.Long.type    => indexed.longAt(col.asInstanceOf[Col[Long]], colIdx)
                      case _                            => indexed.objectAt(col, colIdx)
                    }
                  ).asInstanceOf[A]
                )
              } else foldRecursive(registers, idx + 1, indexed.objectAt(col, colIdx), zero, f)
            case _ =>
              val it      = deconstructor.deconstruct(col)
              val colIdx  = atBinding.index
              var currIdx = 0
              while (currIdx < colIdx && it.hasNext) {
                it.next(): Unit
                currIdx += 1
              }
              if (currIdx != colIdx || !it.hasNext) zero
              else {
                val value = it.next()
                if (idx + 1 == bindings.length) f(zero, value)
                else foldRecursive(registers, idx + 1, value, zero, f)
              }
          }
        case atKeyBinding: AtKeyBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeyBinding.mapDeconstructor
          val key           = atKeyBinding.key
          deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
            case Some(value) =>
              if (idx + 1 == bindings.length) f(zero, value.asInstanceOf[A])
              else foldRecursive(registers, idx + 1, value, zero, f)
            case _ =>
              zero
          }
        case atIndicesBinding: AtIndicesBinding[Col] @scala.unchecked =>
          val deconstructor = atIndicesBinding.seqDeconstructor
          val indices       = atIndicesBinding.indices
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length) foldAtIndices(indices, deconstructor, col, zero, f)
          else {
            var z          = zero
            var indicesIdx = 0
            deconstructor match {
              case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
                val colSize = indexed.size(col)
                while (indicesIdx < indices.length) {
                  val colIdx = indices(indicesIdx)
                  if (colSize > colIdx) z = foldRecursive(registers, idx + 1, indexed.objectAt(col, colIdx), z, f)
                  indicesIdx += 1
                }
              case _ =>
                val it      = deconstructor.deconstruct(col)
                var currIdx = 0
                while (indicesIdx < indices.length) {
                  val colIdx = indices(indicesIdx)
                  while (currIdx < colIdx && it.hasNext) {
                    it.next(): Unit
                    currIdx += 1
                  }
                  if (currIdx == colIdx && it.hasNext) {
                    z = foldRecursive(registers, idx + 1, it.next(), z, f)
                    currIdx += 1
                  }
                  indicesIdx += 1
                }
            }
            z
          }
        case atKeysBinding: AtKeysBinding[Key, Map] @scala.unchecked =>
          var z             = zero
          val deconstructor = atKeysBinding.mapDeconstructor
          val m             = x.asInstanceOf[Map[Key, Value]]
          if (idx + 1 == bindings.length) {
            atKeysBinding.keys.foreach { key =>
              deconstructor.get(m, key) match {
                case Some(value) => z = f(z, value.asInstanceOf[A])
                case _           =>
              }
            }
          } else {
            atKeysBinding.keys.foreach { key =>
              deconstructor.get(m, key) match {
                case Some(value) => z = foldRecursive(registers, idx + 1, value, z, f)
                case _           =>
              }
            }
          }
          z
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          if (idx + 1 == bindings.length) foldSeq(deconstructor, x.asInstanceOf[Col[A]], zero, f)
          else {
            val it = deconstructor.deconstruct(x.asInstanceOf[Col[Elem]])
            var z  = zero
            while (it.hasNext) z = foldRecursive(registers, idx + 1, it.next(), z, f)
            z
          }
        case mapKeyBinding: MapKeyBinding[Map] @scala.unchecked =>
          val deconstructor = mapKeyBinding.mapDeconstructor
          var z             = zero
          if (idx + 1 == bindings.length) {
            val it = deconstructor.deconstruct(x.asInstanceOf[Map[A, Value]])
            while (it.hasNext) z = f(z, deconstructor.getKey(it.next()))
          } else {
            val it = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
            while (it.hasNext) z = foldRecursive(registers, idx + 1, deconstructor.getKey(it.next()), z, f)
          }
          z
        case mapValueBinding: MapValueBinding[Map] @scala.unchecked =>
          val deconstructor = mapValueBinding.mapDeconstructor
          var z             = zero
          if (idx + 1 == bindings.length) {
            val it = deconstructor.deconstruct(x.asInstanceOf[Map[Key, A]])
            while (it.hasNext) z = f(z, deconstructor.getValue(it.next()))
          } else {
            val it = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
            while (it.hasNext) z = foldRecursive(registers, idx + 1, deconstructor.getValue(it.next()), z, f)
          }
          z
      }

    private[this] def foldAtIndices[Z](
      indices: Array[Int],
      deconstructor: SeqDeconstructor[Col],
      x: Col[A],
      zero: Z,
      f: (Z, A) => Z
    ): Z =
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val colSize    = indexed.size(x)
          var indicesIdx = 0
          indexed.elementType(x) match {
            case _: RegisterType.Int.type =>
              val col = x.asInstanceOf[Col[Int]]
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val sf     = f.asInstanceOf[(Int, Int) => Int]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.intAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val sf      = f.asInstanceOf[(Long, Int) => Long]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.intAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val sf        = f.asInstanceOf[(Double, Int) => Double]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.intAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val sf = f.asInstanceOf[(Z, Int) => Z]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.intAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z
              }
            case _: RegisterType.Long.type =>
              val col = x.asInstanceOf[Col[Long]]
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val sf     = f.asInstanceOf[(Int, Long) => Int]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.longAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val sf      = f.asInstanceOf[(Long, Long) => Long]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.longAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val sf        = f.asInstanceOf[(Double, Long) => Double]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.longAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val sf = f.asInstanceOf[(Z, Long) => Z]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.longAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z
              }
            case _: RegisterType.Double.type =>
              val col = x.asInstanceOf[Col[Double]]
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val sf     = f.asInstanceOf[(Int, Double) => Int]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.doubleAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val sf      = f.asInstanceOf[(Long, Double) => Long]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.doubleAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val sf        = f.asInstanceOf[(Double, Double) => Double]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.doubleAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val sf = f.asInstanceOf[(Z, Double) => Z]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.doubleAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z
              }
            case _: RegisterType.Boolean.type =>
              val col = x.asInstanceOf[Col[Boolean]]
              val sf  = f.asInstanceOf[(Z, Boolean) => Z]
              var z   = zero
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.booleanAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _: RegisterType.Byte.type =>
              val col = x.asInstanceOf[Col[Byte]]
              val sf  = f.asInstanceOf[(Z, Byte) => Z]
              var z   = zero
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.byteAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _: RegisterType.Short.type =>
              var z   = zero
              val col = x.asInstanceOf[Col[Short]]
              val sf  = f.asInstanceOf[(Z, Short) => Z]
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.shortAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _: RegisterType.Float.type =>
              var z   = zero
              val col = x.asInstanceOf[Col[Float]]
              val sf  = f.asInstanceOf[(Z, Float) => Z]
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.floatAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _: RegisterType.Char.type =>
              var z   = zero
              val col = x.asInstanceOf[Col[Char]]
              val sf  = f.asInstanceOf[(Z, Char) => Z]
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.charAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _ =>
              var z = zero
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = f(z, indexed.objectAt(x, colIdx))
                indicesIdx += 1
              }
              z
          }
        case _ =>
          val it                  = deconstructor.deconstruct(x)
          var z                   = zero
          var currIdx, indicesIdx = 0
          while (indicesIdx < indices.length) {
            val colIdx = indices(indicesIdx)
            while (currIdx < colIdx && it.hasNext) {
              it.next(): Unit
              currIdx += 1
            }
            if (currIdx == colIdx && it.hasNext) {
              z = f(z, it.next())
              currIdx += 1
            }
            indicesIdx += 1
          }
          z
      }

    private[this] def foldSeq[Z](deconstructor: SeqDeconstructor[Col], x: Col[A], zero: Z, f: (Z, A) => Z): Z =
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size    = indexed.size(x)
          var currIdx = 0
          indexed.elementType(x) match {
            case _: RegisterType.Int.type =>
              val col = x.asInstanceOf[Col[Int]]
              zero match {
                case zi: Int =>
                  val sf     = f.asInstanceOf[(Int, Int) => Int]
                  var z: Int = zi
                  while (currIdx < size) {
                    z = sf(z, indexed.intAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val sf      = f.asInstanceOf[(Long, Int) => Long]
                  var z: Long = zl
                  while (currIdx < size) {
                    z = sf(z, indexed.intAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val sf        = f.asInstanceOf[(Double, Int) => Double]
                  var z: Double = zd
                  while (currIdx < size) {
                    z = sf(z, indexed.intAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val sf = f.asInstanceOf[(Z, Int) => Z]
                  var z  = zero
                  while (currIdx < size) {
                    z = sf(z, indexed.intAt(col, currIdx))
                    currIdx += 1
                  }
                  z
              }
            case _: RegisterType.Long.type =>
              val col = x.asInstanceOf[Col[Long]]
              zero match {
                case zi: Int =>
                  val sf     = f.asInstanceOf[(Int, Long) => Int]
                  var z: Int = zi
                  while (currIdx < size) {
                    z = sf(z, indexed.longAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val sf      = f.asInstanceOf[(Long, Long) => Long]
                  var z: Long = zl
                  while (currIdx < size) {
                    z = sf(z, indexed.longAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val sf        = f.asInstanceOf[(Double, Long) => Double]
                  var z: Double = zd
                  while (currIdx < size) {
                    z = sf(z, indexed.longAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val sf = f.asInstanceOf[(Z, Long) => Z]
                  var z  = zero
                  while (currIdx < size) {
                    z = sf(z, indexed.longAt(col, currIdx))
                    currIdx += 1
                  }
                  z
              }
            case _: RegisterType.Double.type =>
              val col = x.asInstanceOf[Col[Double]]
              zero match {
                case zi: Int =>
                  val sf     = f.asInstanceOf[(Int, Double) => Int]
                  var z: Int = zi
                  while (currIdx < size) {
                    z = sf(z, indexed.doubleAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val sf      = f.asInstanceOf[(Long, Double) => Long]
                  var z: Long = zl
                  while (currIdx < size) {
                    z = sf(z, indexed.doubleAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val sf        = f.asInstanceOf[(Double, Double) => Double]
                  var z: Double = zd
                  while (currIdx < size) {
                    z = sf(z, indexed.doubleAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val sf = f.asInstanceOf[(Z, Double) => Z]
                  var z  = zero
                  while (currIdx < size) {
                    z = sf(z, indexed.doubleAt(col, currIdx))
                    currIdx += 1
                  }
                  z
              }
            case _: RegisterType.Boolean.type =>
              val col = x.asInstanceOf[Col[Boolean]]
              val sf  = f.asInstanceOf[(Z, Boolean) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.booleanAt(col, currIdx))
                currIdx += 1
              }
              z
            case _: RegisterType.Byte.type =>
              val col = x.asInstanceOf[Col[Byte]]
              val sf  = f.asInstanceOf[(Z, Byte) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.byteAt(col, currIdx))
                currIdx += 1
              }
              z
            case _: RegisterType.Short.type =>
              val col = x.asInstanceOf[Col[Short]]
              val sf  = f.asInstanceOf[(Z, Short) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.shortAt(col, currIdx))
                currIdx += 1
              }
              z
            case _: RegisterType.Float.type =>
              val col = x.asInstanceOf[Col[Float]]
              val sf  = f.asInstanceOf[(Z, Float) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.floatAt(col, currIdx))
                currIdx += 1
              }
              z
            case _: RegisterType.Char.type =>
              val col = x.asInstanceOf[Col[Char]]
              val sf  = f.asInstanceOf[(Z, Char) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.charAt(col, currIdx))
                currIdx += 1
              }
              z
            case _ =>
              var z = zero
              while (currIdx < size) {
                z = f(z, indexed.objectAt(x, currIdx))
                currIdx += 1
              }
              z
          }
        case _ =>
          val it = deconstructor.deconstruct(x)
          var z  = zero
          while (it.hasNext) z = f(z, it.next())
          z
      }

    def modify(s: S, f: A => A): S = {
      if (bindings eq null) init()
      try modifyRecursive(Registers(usedRegisters), 0, s, f).asInstanceOf[S]
      catch {
        case _: OpticCheckBuilder => s
      }
    }

    private[this] def modifyRecursive(registers: Registers, idx: Int, x: Any, f: A => A): Any =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          var x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 == bindings.length) x1 = f(x1.asInstanceOf[A])
          else x1 = modifyRecursive(registers, idx + 1, x1, f)
          lensBinding.register.set(registers, offset, x1)
          lensBinding.constructor.construct(registers, offset)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) x
          else if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
          else modifyRecursive(registers, idx + 1, x1, f)
        case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
          val x1 =
            try wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            catch {
              case error: SchemaError => throw toOpticCheckBuilder(idx, error)
            }
          try
            wrapperBinding.wrap({
              if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
              else modifyRecursive(registers, idx + 1, x1, f)
            }.asInstanceOf[Wrapped])
          catch {
            case error: SchemaError => throw toOpticCheckBuilder(idx, error)
          }
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val constructor   = atBinding.seqConstructor
          val colIdx        = atBinding.index
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length)
            modifySeqAt(deconstructor, constructor, col, f, colIdx, atBinding.elemClassTag)
          else {
            val sizeHint =
              deconstructor match {
                case indexed: SeqDeconstructor.SpecializedIndexed[Col] => indexed.size(col)
                case _                                                 => 8
              }
            implicit val classTag: ClassTag[Any] = atBinding.elemClassTag.asInstanceOf[ClassTag[Any]]
            val builder                          = constructor.newBuilder[Any](sizeHint)
            val it                               = deconstructor.deconstruct(col)
            var currIdx                          = 0
            while (it.hasNext) {
              constructor.add(
                builder, {
                  val value = it.next()
                  if (currIdx != colIdx) value
                  else modifyRecursive(registers, idx + 1, value, f)
                }
              )
              currIdx += 1
            }
            constructor.result(builder)
          }
        case atKeyBinding: AtKeyBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeyBinding.mapDeconstructor
          val constructor   = atKeyBinding.mapConstructor
          val map           = x.asInstanceOf[Map[Key, Any]]
          val key           = atKeyBinding.key
          deconstructor.get(map, key) match {
            case Some(value) =>
              constructor.updated(
                map,
                key,
                if (idx + 1 == bindings.length) f(value.asInstanceOf[A])
                else modifyRecursive(registers, idx + 1, value, f)
              )
            case _ => map
          }
        case atIndicesBinding: AtIndicesBinding[Col] @scala.unchecked =>
          val deconstructor = atIndicesBinding.seqDeconstructor
          val constructor   = atIndicesBinding.seqConstructor
          val indices       = atIndicesBinding.indices
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length)
            modifySeqAtIndices(indices, deconstructor, constructor, col, f, atIndicesBinding.elemClassTag)
          else {
            val sizeHint =
              deconstructor match {
                case indexed: SeqDeconstructor.SpecializedIndexed[Col] => indexed.size(col)
                case _                                                 => 8
              }
            val builder             = constructor.newBuilder[Any](sizeHint)
            val it                  = deconstructor.deconstruct(col)
            var colIdx              = indices(0)
            var currIdx, indicesIdx = 0
            while (it.hasNext) {
              constructor.add(
                builder, {
                  val value = it.next()
                  if (currIdx != colIdx) value
                  else {
                    indicesIdx += 1
                    if (indicesIdx < indices.length) colIdx = indices(indicesIdx)
                    modifyRecursive(registers, idx + 1, value, f)
                  }
                }
              )
              currIdx += 1
            }
            constructor.result(builder)
          }
        case atKeysBinding: AtKeysBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeysBinding.mapDeconstructor
          val constructor   = atKeysBinding.mapConstructor
          var map           = x.asInstanceOf[Map[Key, Any]]
          if (idx + 1 == bindings.length) {
            atKeysBinding.keys.foreach { key =>
              deconstructor.get(map, key) match {
                case Some(value) => map = constructor.updated(map, key, f(value.asInstanceOf[A]))
                case _           =>
              }
            }
          } else {
            atKeysBinding.keys.foreach { key =>
              deconstructor.get(map, key) match {
                case Some(value) => map = constructor.updated(map, key, modifyRecursive(registers, idx + 1, value, f))
                case _           =>
              }
            }
          }
          map
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          val constructor   = seqBinding.seqConstructor
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length) modifySeq(deconstructor, constructor, col, f, seqBinding.elemClassTag)
          else {
            val sizeHint =
              deconstructor match {
                case indexed: SeqDeconstructor.SpecializedIndexed[Col] => indexed.size(col)
                case _                                                 => 8
              }
            implicit val classTag: ClassTag[Any] = seqBinding.elemClassTag.asInstanceOf[ClassTag[Any]]
            val builder                          = constructor.newBuilder[Any](sizeHint)
            val it                               = deconstructor.deconstruct(col)
            while (it.hasNext) constructor.add(builder, modifyRecursive(registers, idx + 1, it.next(), f))
            constructor.result(builder)
          }
        case mapKeyBinding: MapKeyBinding[Map] @scala.unchecked =>
          val deconstructor = mapKeyBinding.mapDeconstructor
          val constructor   = mapKeyBinding.mapConstructor
          if (idx + 1 == bindings.length) {
            val builder = constructor.newObjectBuilder[A, Value]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Map[A, Value]])
            while (it.hasNext) {
              val next = it.next()
              constructor.addObject(builder, f(deconstructor.getKey(next)), deconstructor.getValue(next))
            }
            constructor.resultObject(builder)
          } else {
            val builder = constructor.newObjectBuilder[Any, Value]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Map[Any, Value]])
            while (it.hasNext) {
              val next = it.next()
              constructor.addObject(
                builder,
                modifyRecursive(registers, idx + 1, deconstructor.getKey(next), f),
                deconstructor.getValue(next)
              )
            }
            constructor.resultObject(builder)
          }
        case mapValueBinding: MapValueBinding[Map] @scala.unchecked =>
          val deconstructor = mapValueBinding.mapDeconstructor
          val constructor   = mapValueBinding.mapConstructor
          if (idx + 1 == bindings.length) {
            val builder = constructor.newObjectBuilder[Key, A]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Map[Key, A]])
            while (it.hasNext) {
              val next = it.next()
              constructor.addObject(builder, deconstructor.getKey(next), f(deconstructor.getValue(next)))
            }
            constructor.resultObject(builder)
          } else {
            val builder = constructor.newObjectBuilder[Key, Any]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Any]])
            while (it.hasNext) {
              val next = it.next()
              constructor.addObject(
                builder,
                deconstructor.getKey(next),
                modifyRecursive(registers, idx + 1, deconstructor.getValue(next), f)
              )
            }
            constructor.resultObject(builder)
          }
      }

    private[this] def modifySeqAt(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      x: Col[A],
      f: A => A,
      colIdx: Int,
      elemClassTag: ClassTag[?]
    ): Col[A] = {
      implicit val classTag: ClassTag[A] = elemClassTag.asInstanceOf[ClassTag[A]]
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size    = indexed.size(x)
          val builder = constructor.newBuilder[A](size)
          var currIdx = 0
          while (currIdx < size) {
            constructor.add(
              builder, {
                val value = indexed.objectAt(x, currIdx)
                if (currIdx != colIdx) value
                else f(value)
              }
            )
            currIdx += 1
          }
          constructor.result(builder)
        case _ =>
          val builder = constructor.newBuilder[A]()
          val it      = deconstructor.deconstruct(x)
          var currIdx = -1
          while (it.hasNext)
            constructor.add(
              builder, {
                currIdx += 1
                val value = it.next()
                if (currIdx != colIdx) value
                else f(value)
              }
            )
          constructor.result(builder)
      }
    }

    private[this] def modifySeqAtIndices(
      indices: Array[Int],
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      x: Col[A],
      f: A => A,
      elemClassTag: ClassTag[?]
    ): Col[A] = {
      implicit val classTag: ClassTag[A] = elemClassTag.asInstanceOf[ClassTag[A]]
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size                = indexed.size(x)
          var colIdx              = indices(0)
          var currIdx, indicesIdx = 0
          val builder             = constructor.newBuilder[A](size)
          while (currIdx < size) {
            constructor.add(
              builder, {
                val value = indexed.objectAt(x, currIdx)
                if (currIdx != colIdx) value
                else {
                  indicesIdx += 1
                  if (indicesIdx < indices.length) colIdx = indices(indicesIdx)
                  f(value)
                }
              }
            )
            currIdx += 1
          }
          constructor.result(builder)
        case _ =>
          val builder             = constructor.newBuilder[A]()
          val it                  = deconstructor.deconstruct(x)
          var colIdx              = indices(0)
          var currIdx, indicesIdx = 0
          while (it.hasNext) {
            constructor.add(
              builder, {
                val value = it.next()
                if (currIdx != colIdx) value
                else {
                  indicesIdx += 1
                  if (indicesIdx < indices.length) colIdx = indices(indicesIdx)
                  f(value)
                }
              }
            )
            currIdx += 1
          }
          constructor.result(builder)
      }
    }

    private[this] def modifySeq(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      x: Col[A],
      f: A => A,
      elemClassTag: ClassTag[?]
    ): Col[A] = {
      implicit val classTag: ClassTag[A] = elemClassTag.asInstanceOf[ClassTag[A]]
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size    = indexed.size(x)
          val builder = constructor.newBuilder[A](size)
          var currIdx = 0
          while (currIdx < size) {
            constructor.add(builder, f(indexed.objectAt(x, currIdx)))
            currIdx += 1
          }
          constructor.result(builder)
        case _ =>
          val builder = constructor.newBuilder[A]()
          val it      = deconstructor.deconstruct(x)
          while (it.hasNext) constructor.add(builder, f(it.next()))
          constructor.result(builder)
      }
    }

    def modifyOption(s: S, f: A => A): Option[S] = {
      if (bindings eq null) init()
      try {
        var modified = false
        val x        = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          (a: A) => {
            modified = true
            f(a)
          }
        )
        if (modified) new Some(x.asInstanceOf[S])
        else None
      } catch {
        case _: OpticCheckBuilder => None
      }
    }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
      if (bindings eq null) init()
      try {
        var modified = false
        val x        = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          (a: A) => {
            modified = true
            f(a)
          }
        )
        if (modified) new Right(x.asInstanceOf[S])
        else new Left(check(s).get)
      } catch {
        case ocb: OpticCheckBuilder => new Left(ocb.toOpticCheck())
      }
    }

    private[this] def toOpticCheckBuilder(idx: Int, error: SchemaError): OpticCheckBuilder =
      new OpticCheckBuilder(toOpticCheck =
        () => new OpticCheck(new ::(new OpticCheck.WrappingError(toDynamic, toDynamic(idx), error), Nil))
      )

    lazy val toDynamic: DynamicOptic = new DynamicOptic({
      if (bindings eq null) init()
      val nodes = ChunkBuilder.make[DynamicOptic.Node]()
      val len   = bindings.length
      var idx   = 0
      while (idx < len) {
        nodes.addOne {
          bindings(idx) match {
            case _: LensBinding =>
              new DynamicOptic.Node.Field(focusTerms(idx).name)
            case _: PrismBinding =>
              new DynamicOptic.Node.Case(focusTerms(idx).name)
            case _: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
              DynamicOptic.Node.Wrapped
            case at: AtBinding[Col] @scala.unchecked =>
              new DynamicOptic.Node.AtIndex(at.index)
            case atKey: AtKeyBinding[Key, Map] @scala.unchecked =>
              new DynamicOptic.Node.AtMapKey(atKey.keySchema.toDynamicValue(atKey.key))
            case atIndices: AtIndicesBinding[Col] @scala.unchecked =>
              new DynamicOptic.Node.AtIndices(ArraySeq.unsafeWrapArray(atIndices.indices))
            case atKeys: AtKeysBinding[Key, Map] @scala.unchecked =>
              new DynamicOptic.Node.AtMapKeys(atKeys.keys.map(atKeys.keySchema.toDynamicValue))
            case _: SeqBinding[Col] @scala.unchecked =>
              DynamicOptic.Node.Elements
            case _: MapKeyBinding[Map] @scala.unchecked =>
              DynamicOptic.Node.MapKeys
            case _ =>
              DynamicOptic.Node.MapValues
          }
        }
        idx += 1
      }
      nodes.result()
    })

    override def toString: String = {
      if (bindings eq null) init()
      val sb  = new java.lang.StringBuilder
      val len = bindings.length
      var idx = 0
      while (idx < len) {
        bindings(idx) match {
          case _: LensBinding =>
            sb.append('.').append(focusTerms(idx).name)
          case _: PrismBinding =>
            sb.append(".when[").append(focusTerms(idx).name).append(']')
          case _: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
            sb.append(".wrapped[").append(focus.typeId.name).append(']')
          case at: AtBinding[Col] @scala.unchecked =>
            sb.append(".at(").append(at.index).append(')')
          case _: AtKeyBinding[Key, Map] @scala.unchecked =>
            sb.append(".atKey(<key>)")
          case _: AtIndicesBinding[Col] @scala.unchecked =>
            sb.append(".atIndices(<indices>)")
          case _: AtKeysBinding[Key, Map] @scala.unchecked =>
            sb.append(".atKeys(<keys>)")
          case _: SeqBinding[Col] @scala.unchecked =>
            sb.append(".each")
          case _: MapKeyBinding[Map] @scala.unchecked =>
            sb.append(".eachKey")
          case _ =>
            sb.append(".eachValue")
        }
        idx += 1
      }
      s"Traversal(_${sb.toString})"
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(params.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: TraversalImpl[?, ?] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.params.asInstanceOf[Array[AnyRef]], params.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }

  /**
   * A Traversal that searches for all values matching a specific type within a
   * data structure. Uses runtime value recursion (not precomputed paths) to
   * handle recursive types correctly.
   *
   * @tparam S
   *   The source type to search within
   * @tparam A
   *   The focus type to search for
   */
  private[schema] case class SearchTraversal[S, A](
    sourceReflect: Reflect.Bound[S],
    focusReflect: Reflect.Bound[A]
  ) extends Traversal[S, A] {

    def source: Reflect.Bound[S] = sourceReflect

    def focus: Reflect.Bound[A] = focusReflect

    /**
     * Folds over all matching values using runtime value recursion. Converts to
     * DynamicValue, searches for matches via single-pass decode, and folds.
     */
    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z = {
      // Convert source value to DynamicValue for searching
      val dv = sourceReflect.toDynamicValue(s)

      // Collect all decoded matches using iterative DFS (single decode per candidate)
      val matches = searchCollectDecoded(dv)

      // Fold over already-decoded values
      var result = zero
      matches.foreach { a =>
        result = f(result, a)
      }
      result
    }

    def check(s: S): Option[OpticCheck] = {
      // Convert to DynamicValue and search
      val dv = sourceReflect.toDynamicValue(s)

      // Check if at least one candidate decodes successfully (single decode per candidate)
      val anyMatch = searchHasMatch(dv)

      if (anyMatch) None
      else Some(new OpticCheck(new ::(OpticCheck.EmptySequence(toDynamic, toDynamic), Nil)))
    }

    def modify(s: S, f: A => A): S = {
      val dv       = sourceReflect.toDynamicValue(s)
      val modified = searchModify(dv, f)
      sourceReflect.fromDynamicValue(modified, Nil) match {
        case Right(result) => result
        case Left(_)       => s // Should not happen if source schema is correct
      }
    }

    def modifyOption(s: S, f: A => A): Option[S] = {
      val dv                   = sourceReflect.toDynamicValue(s)
      var anyModified: Boolean = false
      val modified             = searchModify(
        dv,
        (a: A) => {
          anyModified = true
          f(a)
        }
      )
      if (anyModified) {
        sourceReflect.fromDynamicValue(modified, Nil) match {
          case Right(result) => Some(result)
          case Left(_)       => None
        }
      } else None
    }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
      val dv                   = sourceReflect.toDynamicValue(s)
      var anyModified: Boolean = false
      val modified             = searchModify(
        dv,
        (a: A) => {
          anyModified = true
          f(a)
        }
      )
      if (anyModified) {
        sourceReflect.fromDynamicValue(modified, Nil) match {
          case Right(result) => Right(result)
          case Left(error)   =>
            Left(new OpticCheck(new ::(OpticCheck.WrappingError(toDynamic, toDynamic, error), Nil)))
        }
      } else Left(new OpticCheck(new ::(OpticCheck.EmptySequence(toDynamic, toDynamic), Nil)))
    }

    lazy val toDynamic: DynamicOptic = new DynamicOptic(Vector(DynamicOptic.Node.TypeSearch(focusReflect.typeId)))

    override def toString: String = s"Traversal(_.searchFor[${focusReflect.typeId.name}])"

    // Override composition methods to handle SearchTraversal specially
    override def apply[B](that: Lens[A, B]): Traversal[S, B] =
      new ComposedSearchTraversal(this, that)

    override def apply[B <: A](that: Prism[A, B]): Traversal[S, B] =
      new ComposedSearchTraversal(this, that)

    override def apply[B](that: Optional[A, B]): Traversal[S, B] =
      new ComposedSearchTraversal(this, that)

    override def apply[B](that: Traversal[A, B]): Traversal[S, B] =
      new ComposedSearchTraversal(this, that)

    override def hashCode: Int = sourceReflect.hashCode ^ focusReflect.hashCode ^ 31

    override def equals(obj: Any): Boolean = obj match {
      case other: SearchTraversal[?, ?] =>
        sourceReflect == other.sourceReflect && focusReflect == other.focusReflect
      case _ => false
    }

    /**
     * Iterative stack-based depth-first traversal to collect all decoded values
     * matching the focus type. Each candidate is decoded exactly once. Order is
     * depth-first, left-to-right.
     */
    private def searchCollectDecoded(root: DynamicValue): List[A] = {
      var stack: List[DynamicValue]       = List(root)
      val results: mutable.ArrayBuffer[A] = mutable.ArrayBuffer.empty

      while (stack.nonEmpty) {
        val current = stack.head
        stack = stack.tail

        // Try to decode — single decode per candidate
        tryDecodeFocus(current).foreach(results += _)

        // Push children onto stack for DFS
        current match {
          case r: DynamicValue.Record =>
            stack = r.fields.iterator.map(_._2).toList ++ stack
          case v: DynamicValue.Variant =>
            stack = v.value :: stack
          case s: DynamicValue.Sequence =>
            stack = s.elements.iterator.toList ++ stack
          case m: DynamicValue.Map =>
            // Search both keys and values
            stack = m.entries.iterator.flatMap { case (k, v) => Iterator(k, v) }.toList ++ stack
          case _: DynamicValue.Primitive | DynamicValue.Null =>
            ()
        }
      }

      results.toList
    }

    /**
     * Iterative stack-based depth-first check for at least one match. Returns
     * true as soon as a decodable candidate is found.
     */
    private def searchHasMatch(root: DynamicValue): Boolean = {
      var stack: List[DynamicValue] = List(root)

      while (stack.nonEmpty) {
        val current = stack.head
        stack = stack.tail

        if (tryDecodeFocus(current).isDefined) return true

        current match {
          case r: DynamicValue.Record =>
            stack = r.fields.iterator.map(_._2).toList ++ stack
          case v: DynamicValue.Variant =>
            stack = v.value :: stack
          case s: DynamicValue.Sequence =>
            stack = s.elements.iterator.toList ++ stack
          case m: DynamicValue.Map =>
            stack = m.entries.iterator.flatMap { case (k, v) => Iterator(k, v) }.toList ++ stack
          case _: DynamicValue.Primitive | DynamicValue.Null =>
            ()
        }
      }

      false
    }

    /**
     * Try to decode a DynamicValue as the focus type. Returns Some(a) if
     * successful, None otherwise. Single decode — no double decoding.
     */
    private def tryDecodeFocus(dv: DynamicValue): Option[A] =
      focusReflect.fromDynamicValue(dv, Nil) match {
        case Right(a) => Some(a)
        case Left(_)  => None
      }

    /**
     * Iterative modification of all values matching the focus type. Uses
     * stack-based traversal (via DynamicValue.iterativeTransform) to avoid
     * stack overflow on deeply nested structures.
     */
    private def searchModify(dv: DynamicValue, f: A => A): DynamicValue =
      DynamicValue.iterativeTransform(dv) { value =>
        tryDecodeFocus(value) match {
          case Some(a) =>
            val modified = f(a)
            focusReflect.toDynamicValue(modified)
          case None => value
        }
      }
  }

  /**
   * A Traversal that composes a SearchTraversal with another optic. First
   * searches for values of type T, then applies the inner optic to each found
   * value.
   *
   * @tparam S
   *   The source type
   * @tparam T
   *   The intermediate type (what SearchTraversal finds)
   * @tparam A
   *   The final focus type
   */
  private[schema] case class ComposedSearchTraversal[S, T, A](
    search: SearchTraversal[S, T],
    inner: Optic[T, A]
  ) extends Traversal[S, A] {

    def source: Reflect.Bound[S] = search.source

    def focus: Reflect.Bound[A] = inner.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z =
      // First, fold over all T values found by search
      search.fold[Z](s)(
        zero,
        (acc, t) =>
          // For each T, use the inner optic to get/fold over A values
          inner match {
            case lens: Lens[T, A] @unchecked =>
              f(acc, lens.get(t))
            case prism: Prism[T, A] @unchecked =>
              prism.getOption(t) match {
                case Some(a) => f(acc, a)
                case None    => acc
              }
            case optional: Optional[T, A] @unchecked =>
              optional.getOption(t) match {
                case Some(a) => f(acc, a)
                case None    => acc
              }
            case traversal: Traversal[T, A] @unchecked =>
              traversal.fold[Z](t)(acc, f)
          }
      )

    def check(s: S): Option[OpticCheck] = {
      val searchCheck = search.check(s)
      if (searchCheck.isDefined) searchCheck
      else {
        // Check inner optic on all found values
        val errors = List.newBuilder[OpticCheck.Single]
        search.fold[Unit](s)(
          (),
          (_, t) =>
            inner.check(t) match {
              case Some(opticCheck) => errors ++= opticCheck.errors
              case None             => ()
            }
        )
        errors.result() match {
          case Nil          => None
          case head :: tail => Some(new OpticCheck(new ::(head, tail)))
        }
      }
    }

    def modify(s: S, f: A => A): S = search.modify(s, t => inner.modify(t, f))

    def modifyOption(s: S, f: A => A): Option[S] = {
      var anyModified = false
      val result      = search.modify(
        s,
        t => {
          inner.modifyOption(t, f) match {
            case Some(modified) =>
              anyModified = true
              modified
            case None => t
          }
        }
      )
      if (anyModified) Some(result) else None
    }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
      var anyModified                          = false
      var firstError: Either[OpticCheck, Unit] = Right(())
      val result                               = search.modify(
        s,
        t => {
          if (firstError.isLeft) t
          else {
            inner.modifyOrFail(t, f) match {
              case Right(modified) =>
                anyModified = true
                modified
              case Left(error) =>
                firstError = Left(error)
                t
            }
          }
        }
      )
      firstError match {
        case Left(error) => Left(error)
        case Right(_)    =>
          if (anyModified) Right(result)
          else Left(new OpticCheck(new ::(OpticCheck.EmptySequence(toDynamic, toDynamic), Nil)))
      }
    }

    lazy val toDynamic: DynamicOptic = search.toDynamic(inner.toDynamic)

    override def toString: String =
      s"Traversal(_.searchFor[${search.focus.typeId.name}]${inner.toDynamic.toScalaString})"

    override def hashCode: Int = search.hashCode ^ inner.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: ComposedSearchTraversal[?, ?, ?] =>
        search == other.search && inner == other.inner
      case _ => false
    }

    // Override apply methods to handle composition with this non-TraversalImpl traversal
    override def apply[B](that: Lens[A, B]): Traversal[S, B] =
      new ComposedSearchTraversal(search, inner.apply(that))

    override def apply[B <: A](that: Prism[A, B]): Traversal[S, B] =
      new ComposedSearchTraversal(search, inner.apply(that))

    override def apply[B](that: Optional[A, B]): Traversal[S, B] =
      new ComposedSearchTraversal(search, inner.apply(that))

    override def apply[B](that: Traversal[A, B]): Traversal[S, B] =
      new ComposedSearchTraversal(search, inner.apply(that))
  }

  /**
   * A Traversal that composes a regular optic with a search-based traversal.
   * First applies the prefix optic to get T value(s), then applies the inner
   * traversal to each T.
   *
   * This handles composition like: `optic(_.field.searchFor[T])` where the
   * search comes AFTER a lens/prism/optional.
   *
   * @tparam S
   *   The source type
   * @tparam T
   *   The intermediate type (what prefix optic focuses on)
   * @tparam A
   *   The final focus type (what the inner traversal finds)
   */
  private[schema] case class PrefixedSearchTraversal[S, T, A](
    prefix: Optic[S, T],
    inner: Traversal[T, A]
  ) extends Traversal[S, A] {

    def source: Reflect.Bound[S] = prefix.source

    def focus: Reflect.Bound[A] = inner.focus

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z =
      // Apply prefix to get T value(s), then apply inner traversal to each
      prefix match {
        case lens: Lens[S, T] @unchecked =>
          val t = lens.get(s)
          inner.fold[Z](t)(zero, f)
        case prism: Prism[S, T] @unchecked =>
          prism.getOption(s) match {
            case Some(t) => inner.fold[Z](t)(zero, f)
            case None    => zero
          }
        case optional: Optional[S, T] @unchecked =>
          optional.getOption(s) match {
            case Some(t) => inner.fold[Z](t)(zero, f)
            case None    => zero
          }
        case traversal: Traversal[S, T] @unchecked =>
          traversal.fold[Z](s)(zero, (acc, t) => inner.fold[Z](t)(acc, f))
      }

    def check(s: S): Option[OpticCheck] =
      // First check prefix
      prefix.check(s) match {
        case Some(prefixError) => Some(prefixError)
        case None              =>
          // Then check inner traversal on all T values from prefix
          prefix match {
            case lens: Lens[S, T] @unchecked =>
              inner.check(lens.get(s))
            case prism: Prism[S, T] @unchecked =>
              prism.getOption(s).flatMap(inner.check)
            case optional: Optional[S, T] @unchecked =>
              optional.getOption(s).flatMap(inner.check)
            case traversal: Traversal[S, T] @unchecked =>
              val errors = List.newBuilder[OpticCheck.Single]
              traversal.fold[Unit](s)(
                (),
                (_, t) =>
                  inner.check(t) match {
                    case Some(opticCheck) => errors ++= opticCheck.errors
                    case None             => ()
                  }
              )
              errors.result() match {
                case Nil          => None
                case head :: tail => Some(new OpticCheck(new ::(head, tail)))
              }
          }
      }

    def modify(s: S, f: A => A): S =
      prefix match {
        case lens: Lens[S, T] @unchecked =>
          val t        = lens.get(s)
          val modified = inner.modify(t, f)
          lens.replace(s, modified)
        case prism: Prism[S, T] @unchecked =>
          prism.getOption(s) match {
            case Some(t) =>
              val modified = inner.modify(t, f)
              prism.replaceOption(s, modified).getOrElse(s)
            case None => s
          }
        case optional: Optional[S, T] @unchecked =>
          optional.getOption(s) match {
            case Some(t) =>
              val modified = inner.modify(t, f)
              optional.replaceOption(s, modified).getOrElse(s)
            case None => s
          }
        case traversal: Traversal[S, T] @unchecked =>
          traversal.modify(s, (t: T) => inner.modify(t, f))
      }

    def modifyOption(s: S, f: A => A): Option[S] =
      prefix match {
        case lens: Lens[S, T] @unchecked =>
          val t = lens.get(s)
          inner.modifyOption(t, f).map(lens.replace(s, _))
        case prism: Prism[S, T] @unchecked =>
          prism.getOption(s) match {
            case Some(t) =>
              inner.modifyOption(t, f).flatMap(modified => prism.replaceOption(s, modified))
            case None => None
          }
        case optional: Optional[S, T] @unchecked =>
          optional.getOption(s) match {
            case Some(t) =>
              inner.modifyOption(t, f).flatMap(modified => optional.replaceOption(s, modified))
            case None => None
          }
        case traversal: Traversal[S, T] @unchecked =>
          var anyModified = false
          val result      = traversal.modify(
            s,
            (t: T) => {
              inner.modifyOption(t, f) match {
                case Some(modified) =>
                  anyModified = true
                  modified
                case None => t
              }
            }
          )
          if (anyModified) Some(result) else None
      }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] =
      prefix match {
        case lens: Lens[S, T] @unchecked =>
          val t = lens.get(s)
          inner.modifyOrFail(t, f).map(lens.replace(s, _))
        case prism: Prism[S, T] @unchecked =>
          prism.getOption(s) match {
            case Some(t) =>
              inner.modifyOrFail(t, f).flatMap(modified => prism.replaceOrFail(s, modified))
            case None =>
              Left(new OpticCheck(new ::(OpticCheck.EmptySequence(toDynamic, toDynamic), Nil)))
          }
        case optional: Optional[S, T] @unchecked =>
          optional.getOption(s) match {
            case Some(t) =>
              inner.modifyOrFail(t, f).flatMap(modified => optional.replaceOrFail(s, modified))
            case None =>
              Left(new OpticCheck(new ::(OpticCheck.EmptySequence(toDynamic, toDynamic), Nil)))
          }
        case traversal: Traversal[S, T] @unchecked =>
          var anyModified                          = false
          var firstError: Either[OpticCheck, Unit] = Right(())
          val result                               = traversal.modify(
            s,
            (t: T) => {
              if (firstError.isLeft) t
              else {
                inner.modifyOrFail(t, f) match {
                  case Right(modified) =>
                    anyModified = true
                    modified
                  case Left(error) =>
                    firstError = Left(error)
                    t
                }
              }
            }
          )
          firstError match {
            case Left(error) => Left(error)
            case Right(_)    =>
              if (anyModified) Right(result)
              else Left(new OpticCheck(new ::(OpticCheck.EmptySequence(toDynamic, toDynamic), Nil)))
          }
      }

    lazy val toDynamic: DynamicOptic = prefix.toDynamic(inner.toDynamic)

    override def toString: String = s"Traversal(${prefix.toDynamic.toScalaString}${inner.toDynamic.toScalaString})"

    override def hashCode: Int = prefix.hashCode ^ inner.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: PrefixedSearchTraversal[?, ?, ?] =>
        prefix == other.prefix && inner == other.inner
      case _ => false
    }

    // Override apply methods to handle composition with this non-TraversalImpl traversal
    // PrefixedSearchTraversal composed with another optic becomes: prefix → inner → that
    override def apply[B](that: Lens[A, B]): Traversal[S, B] =
      new PrefixedSearchTraversal(prefix, inner.apply(that))

    override def apply[B <: A](that: Prism[A, B]): Traversal[S, B] =
      new PrefixedSearchTraversal(prefix, inner.apply(that))

    override def apply[B](that: Optional[A, B]): Traversal[S, B] =
      new PrefixedSearchTraversal(prefix, inner.apply(that))

    override def apply[B](that: Traversal[A, B]): Traversal[S, B] =
      new PrefixedSearchTraversal(prefix, inner.apply(that))
  }
}

/**
 * Constructors for search traversals, which the `searchFor[T]` step of
 * [[CompanionOptics]] builds.
 *
 * A search traversal converts the value to a [[DynamicValue]]. `fold` and
 * `check` visit it depth first, left to right: the value itself, then record
 * fields, case values, sequence elements, and both the keys and the values of
 * maps. `modify` visits the same nodes except map keys, so a match that is a
 * map key is read but never modified (see `docs/dev/issues.md`). Every node
 * that decodes with the focus schema is a match, and the children of a match
 * are searched too. `modify` applies `f` to each match and decodes the result
 * with the source schema; if that decoding fails, it returns `s` unchanged.
 * `check` reports an [[OpticCheck.EmptySequence]] when nothing matches.
 */
object SearchTraversal {

  /**
   * Creates a traversal over every value inside an `S` (described by `source`)
   * that decodes with the schema `focus`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `source` or `focus` is null
   */
  def apply[S, A](source: Reflect.Bound[S], focus: Reflect.Bound[A]): Traversal[S, A] = {
    require((source ne null) && (focus ne null))
    new Traversal.SearchTraversal(source, focus)
  }

  /**
   * Creates a traversal over every value inside an `S` that decodes as an `A`,
   * with both schemas taken from implicit scope.
   */
  def apply[S, A](implicit sourceSchema: Schema[S], focusSchema: Schema[A]): Traversal[S, A] =
    apply(sourceSchema.reflect, focusSchema.reflect)
}

private[schema] sealed trait OpticBinding

private[schema] case class LensBinding(
  offset: RegisterOffset,
  deconstructor: Deconstructor[Any],
  constructor: Constructor[Any],
  register: Register[Any]
) extends OpticBinding

private[schema] case class PrismBinding(
  matcher: Matcher[Any],
  discriminator: Discriminator[Any]
) extends OpticBinding

private[schema] case class SeqBinding[C[_]](
  seqDeconstructor: SeqDeconstructor[C],
  seqConstructor: SeqConstructor[C],
  elemClassTag: ClassTag[?]
) extends OpticBinding

private[schema] case class MapKeyBinding[M[_, _]](
  mapDeconstructor: MapDeconstructor[M],
  mapConstructor: MapConstructor[M]
) extends OpticBinding

private[schema] case class MapValueBinding[M[_, _]](
  mapDeconstructor: MapDeconstructor[M],
  mapConstructor: MapConstructor[M]
) extends OpticBinding

private[schema] case class AtBinding[C[_]](
  seqDeconstructor: SeqDeconstructor[C],
  seqConstructor: SeqConstructor[C],
  index: Int,
  elemClassTag: ClassTag[?]
) extends OpticBinding

private[schema] case class AtKeyBinding[K, M[_, _]](
  mapDeconstructor: MapDeconstructor[M],
  mapConstructor: MapConstructor[M],
  keySchema: Reflect.Bound[K],
  key: K
) extends OpticBinding

private[schema] case class AtIndicesBinding[C[_]](
  seqDeconstructor: SeqDeconstructor[C],
  seqConstructor: SeqConstructor[C],
  indices: Array[Int],
  elemClassTag: ClassTag[?]
) extends OpticBinding

private[schema] case class AtKeysBinding[K, M[_, _]](
  mapDeconstructor: MapDeconstructor[M],
  mapConstructor: MapConstructor[M],
  keySchema: Reflect.Bound[K],
  keys: Seq[K]
) extends OpticBinding

private[schema] case class WrappedBinding[A, B](
  wrap: B => A,
  unwrap: A => B
) extends OpticBinding

private[schema] case class OpticCheckBuilder(toOpticCheck: () => OpticCheck) extends Exception with NoStackTrace
