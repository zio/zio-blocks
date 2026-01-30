package zio.blocks.markdown

sealed abstract class HeadingLevel(val value: Int) extends Product with Serializable

object HeadingLevel {
  case object H1 extends HeadingLevel(1)
  case object H2 extends HeadingLevel(2)
  case object H3 extends HeadingLevel(3)
  case object H4 extends HeadingLevel(4)
  case object H5 extends HeadingLevel(5)
  case object H6 extends HeadingLevel(6)

  def fromInt(n: Int): Option[HeadingLevel] = n match {
    case 1 => Some(H1)
    case 2 => Some(H2)
    case 3 => Some(H3)
    case 4 => Some(H4)
    case 5 => Some(H5)
    case 6 => Some(H6)
    case _ => None
  }

  def unsafeFromInt(n: Int): HeadingLevel =
    fromInt(n).getOrElse(throw new IllegalArgumentException(s"Invalid heading level: $n"))
}
