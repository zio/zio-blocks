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

package zio.blocks.docs

/**
 * Converts a value of type `A` to an [[Inline]] element.
 *
 * The `md` string interpolator needs an instance for the type of every value
 * spliced into the text. The companion object has instances for `String`,
 * `Int`, `Long`, `Double`, `Boolean`, [[Inline]] and [[Block]], and for `List`,
 * `Chunk`, `Vector` and `Seq` of any type that has one. Other types, such as
 * `Option` or `Float`, have none, so `md` rejects them at compile time until
 * you define an instance. The type parameter is contravariant, so an
 * `Inline`-typed instance also serves its subtypes such as `Text`.
 *
 * @tparam A
 *   The type to convert to an inline element
 *
 * @example
 *   {{{
 * final case class Port(number: Int)
 *
 * implicit val portToMarkdown: ToMarkdown[Port] =
 *   (port: Port) => Code(port.number.toString)
 *
 * val doc = md"Listening on \${Port(8080)}"
 *
 * val markdown = doc.toString
 * // Listening on `8080`
 *   }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/docs/ Docs]]
 */
trait ToMarkdown[-A] {

  /**
   * Convert a value to an inline element.
   *
   * @param a
   *   The value to convert
   * @return
   *   An inline markdown element
   */
  def toMarkdown(a: A): Inline
}

/** Instances for the common types, and `apply` to summon one. */
object ToMarkdown {
  import zio.blocks.chunk.Chunk

  /**
   * Summon an implicit ToMarkdown instance.
   *
   * @tparam A
   *   The type to get the instance for
   * @return
   *   The implicit ToMarkdown instance
   */
  def apply[A](implicit ev: ToMarkdown[A]): ToMarkdown[A] = ev

  /**
   * A string becomes a plain text node. The text is later written out as
   * Markdown and parsed again, so Markdown syntax inside the string takes
   * effect in an `md` interpolation.
   */
  implicit val stringToMarkdown: ToMarkdown[String] = (a: String) => Text(a)

  /** An `Int` becomes a text node with its decimal digits. */
  implicit val intToMarkdown: ToMarkdown[Int] = (a: Int) => Text(a.toString)

  /** A `Long` becomes a text node with its decimal digits. */
  implicit val longToMarkdown: ToMarkdown[Long] = (a: Long) => Text(a.toString)

  /**
   * A `Double` becomes a text node holding `Double#toString`, which differs by
   * platform: ten billion is `1.0E10` on the JVM and `10000000000` on Scala.js.
   */
  implicit val doubleToMarkdown: ToMarkdown[Double] = (a: Double) => Text(a.toString)

  /** A `Boolean` becomes the text node `true` or `false`. */
  implicit val booleanToMarkdown: ToMarkdown[Boolean] = (a: Boolean) => Text(a.toString)

  /** An [[Inline]] converts to itself. */
  implicit val inlineToMarkdown: ToMarkdown[Inline] = (a: Inline) => a

  /**
   * A `List` becomes one text node: each element is converted with its own
   * instance, written out as Markdown, and the pieces are joined with `, `.
   */
  implicit def listToMarkdown[A](implicit ev: ToMarkdown[A]): ToMarkdown[List[A]] = (as: List[A]) =>
    Text(as.map(a => Renderer.renderInline(ev.toMarkdown(a))).mkString(", "))

  /**
   * A `Chunk` becomes one text node: each element is converted with its own
   * instance, written out as Markdown, and the pieces are joined with `, `.
   */
  implicit def chunkToMarkdown[A](implicit ev: ToMarkdown[A]): ToMarkdown[Chunk[A]] = (as: Chunk[A]) =>
    Text(as.map(a => Renderer.renderInline(ev.toMarkdown(a))).mkString(", "))

  /**
   * A `Vector` becomes one text node: each element is converted with its own
   * instance, written out as Markdown, and the pieces are joined with `, `.
   */
  implicit def vectorToMarkdown[A](implicit ev: ToMarkdown[A]): ToMarkdown[Vector[A]] = (as: Vector[A]) =>
    Text(as.map(a => Renderer.renderInline(ev.toMarkdown(a))).mkString(", "))

  /**
   * A `Seq` becomes one text node: each element is converted with its own
   * instance, written out as Markdown, and the pieces are joined with `, `.
   */
  implicit def seqToMarkdown[A](implicit ev: ToMarkdown[A]): ToMarkdown[Seq[A]] = (as: Seq[A]) =>
    Text(as.map(a => Renderer.renderInline(ev.toMarkdown(a))).mkString(", "))

  /**
   * A [[Block]] becomes a text node holding the block rendered as Markdown by
   * [[Renderer.render]], with surrounding whitespace trimmed.
   */
  implicit val blockToMarkdown: ToMarkdown[Block] = (b: Block) => Text(Renderer.render(Doc(Chunk(b))).trim)
}
