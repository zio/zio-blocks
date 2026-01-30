package zio.blocks.markdown

/**
 * Type class for converting values to markdown inline elements.
 *
 * Used by the `md` string interpolator to support interpolation of arbitrary
 * types. Implement this trait for custom types to enable markdown
 * interpolation.
 *
 * @tparam A
 *   The type to convert to markdown
 *
 * @example
 *   {{{ case class Person(name: String, age: Int)
 *
 * implicit val personToMarkdown: ToMarkdown[Person] = (p: Person) =>
 * Strong(Chunk(Text(p.name)))
 *
 * val person = Person("Alice", 30) val doc = md"# User: $$person" }}}
 */
trait ToMarkdown[-A] {

  /**
   * Convert a value to a markdown inline element.
   *
   * @param a
   *   The value to convert
   * @return
   *   An inline markdown element
   */
  def toMarkdown(a: A): Inline
}

object ToMarkdown {

  /**
   * Summon an implicit ToMarkdown instance.
   *
   * @tparam A
   *   The type to get the instance for
   * @return
   *   The implicit ToMarkdown instance
   */
  def apply[A](implicit ev: ToMarkdown[A]): ToMarkdown[A] = ev

  /** Convert strings to plain text. */
  implicit val stringToMarkdown: ToMarkdown[String] = (a: String) => Text(a)

  /** Convert integers to plain text. */
  implicit val intToMarkdown: ToMarkdown[Int] = (a: Int) => Text(a.toString)

  /** Convert longs to plain text. */
  implicit val longToMarkdown: ToMarkdown[Long] = (a: Long) => Text(a.toString)

  /** Convert doubles to plain text. */
  implicit val doubleToMarkdown: ToMarkdown[Double] = (a: Double) => Text(a.toString)

  /** Convert booleans to plain text. */
  implicit val booleanToMarkdown: ToMarkdown[Boolean] = (a: Boolean) => Text(a.toString)

  /** Identity conversion for inline elements. */
  implicit val inlineToMarkdown: ToMarkdown[Inline] = (a: Inline) => a
}
