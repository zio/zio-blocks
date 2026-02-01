package zio.blocks.typeid

/**
 * Represents the variance of a type parameter.
 *
 * Variance determines how subtyping of type arguments affects subtyping of
 * parameterized types:
 *
 *   - Covariant (+A): If `Dog <: Animal`, then `List[Dog] <: List[Animal]`
 *   - Contravariant (-A): If `Dog <: Animal`, then
 *     `Handler[Animal] <: Handler[Dog]`
 *   - Invariant (A): No subtyping relationship between `Box[Dog]` and
 *     `Box[Animal]`
 */
sealed trait Variance {

  /**
   * Returns the Scala syntax for this variance annotation.
   */
  def symbol: String = this match {
    case Variance.Covariant     => "+"
    case Variance.Contravariant => "-"
    case Variance.Invariant     => ""
  }

  /**
   * Returns true if this is covariant.
   */
  def isCovariant: Boolean = this == Variance.Covariant

  /**
   * Returns true if this is contravariant.
   */
  def isContravariant: Boolean = this == Variance.Contravariant

  /**
   * Returns true if this is invariant.
   */
  def isInvariant: Boolean = this == Variance.Invariant

  /**
   * Flips the variance (covariant <-> contravariant, invariant stays).
   */
  def flip: Variance = this match {
    case Variance.Covariant     => Variance.Contravariant
    case Variance.Contravariant => Variance.Covariant
    case Variance.Invariant     => Variance.Invariant
  }

  /**
   * Combines two variances. Used when a type parameter appears in a nested
   * position.
   */
  def *(other: Variance): Variance = (this, other) match {
    case (Variance.Invariant, _)                          => Variance.Invariant
    case (_, Variance.Invariant)                          => Variance.Invariant
    case (Variance.Covariant, Variance.Covariant)         => Variance.Covariant
    case (Variance.Contravariant, Variance.Contravariant) => Variance.Covariant
    case (Variance.Covariant, Variance.Contravariant)     => Variance.Contravariant
    case (Variance.Contravariant, Variance.Covariant)     => Variance.Contravariant
  }
}

object Variance {

  /**
   * Covariant: +A Preserves subtyping: if Dog <: Animal, then F[Dog] <:
   * F[Animal]
   */
  case object Covariant extends Variance

  /**
   * Contravariant: -A Reverses subtyping: if Dog <: Animal, then F[Animal] <:
   * F[Dog]
   */
  case object Contravariant extends Variance

  /**
   * Invariant: A No subtyping relationship between F[Dog] and F[Animal]
   */
  case object Invariant extends Variance
}
