package zio.blocks.markdown

trait ToMarkdown[-A] {
  def toMarkdown(a: A): Inline
}

object ToMarkdown {
  def apply[A](implicit ev: ToMarkdown[A]): ToMarkdown[A] = ev

  implicit val stringToMarkdown: ToMarkdown[String]   = (a: String) => Text(a)
  implicit val intToMarkdown: ToMarkdown[Int]         = (a: Int) => Text(a.toString)
  implicit val longToMarkdown: ToMarkdown[Long]       = (a: Long) => Text(a.toString)
  implicit val doubleToMarkdown: ToMarkdown[Double]   = (a: Double) => Text(a.toString)
  implicit val booleanToMarkdown: ToMarkdown[Boolean] = (a: Boolean) => Text(a.toString)
  implicit val inlineToMarkdown: ToMarkdown[Inline]   = (a: Inline) => a
}
