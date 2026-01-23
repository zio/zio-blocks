package zio.blocks.typeid

sealed trait Variance {
  def symbol: String = this match {
    case Variance.Invariant     => ""
    case Variance.Covariant     => "+"
    case Variance.Contravariant => "-"
  }

  def flip: Variance = this match {
    case Variance.Invariant     => Variance.Invariant
    case Variance.Covariant     => Variance.Contravariant
    case Variance.Contravariant => Variance.Covariant
  }
}

object Variance {
  case object Invariant     extends Variance
  case object Covariant     extends Variance
  case object Contravariant extends Variance
}
