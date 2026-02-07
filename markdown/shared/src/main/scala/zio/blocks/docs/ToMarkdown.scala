package zio.blocks.docs

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

  /** Convert List[A] to comma-separated text when A has ToMarkdown. */
  implicit def listToMarkdown[A](implicit ev: ToMarkdown[A]): ToMarkdown[List[A]] = (as: List[A]) =>
    Text(as.map(a => Renderer.renderInline(ev.toMarkdown(a))).mkString(", "))

  /** Convert Chunk[A] to comma-separated text when A has ToMarkdown. */
  implicit def chunkToMarkdown[A](implicit ev: ToMarkdown[A]): ToMarkdown[Chunk[A]] = (as: Chunk[A]) =>
    Text(as.map(a => Renderer.renderInline(ev.toMarkdown(a))).mkString(", "))

  /** Convert Vector[A] to comma-separated text when A has ToMarkdown. */
  implicit def vectorToMarkdown[A](implicit ev: ToMarkdown[A]): ToMarkdown[Vector[A]] = (as: Vector[A]) =>
    Text(as.map(a => Renderer.renderInline(ev.toMarkdown(a))).mkString(", "))

  /** Convert Seq[A] to comma-separated text when A has ToMarkdown. */
  implicit def seqToMarkdown[A](implicit ev: ToMarkdown[A]): ToMarkdown[Seq[A]] = (as: Seq[A]) =>
    Text(as.map(a => Renderer.renderInline(ev.toMarkdown(a))).mkString(", "))

  /** Convert Block to rendered markdown text. */
  implicit val blockToMarkdown: ToMarkdown[Block] = (b: Block) => Text(Renderer.render(Doc(Chunk(b))).trim)
}
