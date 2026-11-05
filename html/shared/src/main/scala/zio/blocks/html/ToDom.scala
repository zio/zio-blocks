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

package zio.blocks.html

import zio.blocks.chunk.Chunk

/**
 * Typeclass for values that can be converted to a [[Dom]] node.
 *
 * Similar to [[ToCss]] and [[ToJs]], `ToDom` provides a uniform way to convert
 * Scala values into DOM representations. Primitive types are converted to
 * [[Dom.Text]] nodes, while [[Dom]] values pass through unchanged.
 *
 * @tparam A
 *   the input type to convert
 */
trait ToDom[-A] {

  /**
   * Converts the given value to a [[Dom]] node.
   *
   * @param a
   *   the value to convert
   * @return
   *   a DOM representation of the value
   */
  def toDom(a: A): Dom
}

/**
 * Instances of [[ToDom]] for DOM nodes, strings, numbers, booleans, options and
 * iterables.
 */
object ToDom {

  /** Summons the implicit [[ToDom]] instance for `A`. */
  def apply[A](implicit ev: ToDom[A]): ToDom[A] = ev

  /** Returns the [[Dom]] node unchanged. */
  implicit val domToDom: ToDom[Dom] = new ToDom[Dom] {
    def toDom(a: Dom): Dom = a
  }

  /** Returns the element unchanged. */
  implicit val elementToDom: ToDom[Dom.Element] = new ToDom[Dom.Element] {
    def toDom(a: Dom.Element): Dom = a
  }

  /** Returns the text node unchanged. */
  implicit val textToDom: ToDom[Dom.Text] = new ToDom[Dom.Text] {
    def toDom(a: Dom.Text): Dom = a
  }

  /**
   * Wraps the `String` in a [[Dom.Text]] node, which escapes it when rendered.
   */
  implicit val stringToDom: ToDom[String] = new ToDom[String] {
    def toDom(a: String): Dom = Dom.Text(a)
  }

  /** Wraps the decimal form of the `Int` in a [[Dom.Text]] node. */
  implicit val intToDom: ToDom[Int] = new ToDom[Int] {
    def toDom(a: Int): Dom = Dom.Text(a.toString)
  }

  /** Wraps the decimal form of the `Long` in a [[Dom.Text]] node. */
  implicit val longToDom: ToDom[Long] = new ToDom[Long] {
    def toDom(a: Long): Dom = Dom.Text(a.toString)
  }

  /** Wraps the `Double#toString` form of the value in a [[Dom.Text]] node. */
  implicit val doubleToDom: ToDom[Double] = new ToDom[Double] {
    def toDom(a: Double): Dom = Dom.Text(a.toString)
  }

  /** Wraps `true` or `false` in a [[Dom.Text]] node. */
  implicit val booleanToDom: ToDom[Boolean] = new ToDom[Boolean] {
    def toDom(a: Boolean): Dom = Dom.Text(a.toString)
  }

  /**
   * Converts the wrapped value of a `Some` with the instance for `A`, and
   * yields [[Dom.Empty]] for `None`.
   */
  implicit def optionToDom[A](implicit ev: ToDom[A]): ToDom[Option[A]] = new ToDom[Option[A]] {
    def toDom(a: Option[A]): Dom = a match {
      case Some(v) => ev.toDom(v)
      case None    => Dom.Empty
    }
  }

  /**
   * Converts the elements with the instance for `A`. An empty collection yields
   * [[Dom.Empty]], one element yields its node, and several elements become the
   * children of a `span` element.
   */
  implicit def iterableToDom[A](implicit ev: ToDom[A]): ToDom[Iterable[A]] = new ToDom[Iterable[A]] {
    def toDom(a: Iterable[A]): Dom = {
      val builder = Chunk.newBuilder[Dom]
      val iter    = a.iterator
      while (iter.hasNext) builder += ev.toDom(iter.next())
      val children = builder.result()
      if (children.isEmpty) Dom.Empty
      else if (children.length == 1) children(0)
      else Dom.Element.Generic("span", Chunk.empty, children)
    }
  }
}
