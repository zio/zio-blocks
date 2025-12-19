package zio.blocks.typeid

/**
 * Represents a type parameter specification for a parameterized type.
 *
 * Type parameters capture not just the name but also positional information,
 * variance, and bounds that are essential for accurate type representation.
 *
 * ==Example==
 * For `List[+A]`:
 * {{{
 * TypeParam("A", 0, variance = Variance.Covariant)
 * }}}
 *
 * For `Map[K, +V]`:
 * {{{
 * List(
 *   TypeParam("K", 0, variance = Variance.Invariant),
 *   TypeParam("V", 1, variance = Variance.Covariant)
 * )
 * }}}
 *
 * @param name
 *   The name of the type parameter (e.g., "A", "K", "V")
 * @param index
 *   The 0-based position of this parameter in the type parameter list
 * @param variance
 *   The variance annotation of this type parameter
 * @param bounds
 *   Optional bounds for this type parameter
 * @param isPhantom
 *   Whether this type parameter is a phantom type (not used at runtime)
 */
final case class TypeParam(
  name: String,
  index: Int,
  variance: Variance = Variance.Invariant,
  bounds: TypeParam.Bounds = TypeParam.Bounds.Unbounded,
  isPhantom: Boolean = false
) {

  /**
   * Returns true if this type parameter has any bound constraints.
   */
  def isBounded: Boolean = bounds != TypeParam.Bounds.Unbounded

  /**
   * Creates a copy with covariant variance.
   */
  def covariant: TypeParam = copy(variance = Variance.Covariant)

  /**
   * Creates a copy with contravariant variance.
   */
  def contravariant: TypeParam = copy(variance = Variance.Contravariant)

  /**
   * Creates a copy with invariant variance.
   */
  def invariant: TypeParam = copy(variance = Variance.Invariant)

  /**
   * Returns a string representation suitable for signatures.
   *
   * @return
   *   e.g., "+A", "-B", "T <: AnyRef"
   */
  def signature: String = {
    val variancePrefix = variance match {
      case Variance.Covariant     => "+"
      case Variance.Contravariant => "-"
      case Variance.Invariant     => ""
    }
    val boundsStr = bounds match {
      case TypeParam.Bounds.Unbounded          => ""
      case TypeParam.Bounds.Upper(bound)       => s" <: $bound"
      case TypeParam.Bounds.Lower(bound)       => s" >: $bound"
      case TypeParam.Bounds.Both(lower, upper) => s" >: $lower <: $upper"
    }
    s"$variancePrefix$name$boundsStr"
  }

  override def toString: String = s"TypeParam($signature, index=$index)"
}

object TypeParam {

  /**
   * Creates a simple type parameter with just a name and index.
   */
  def apply(name: String, index: Int): TypeParam =
    new TypeParam(name, index, Variance.Invariant, Bounds.Unbounded, false)

  /**
   * Bounds specification for type parameters.
   */
  sealed trait Bounds extends Product with Serializable

  object Bounds {

    /** No bounds */
    case object Unbounded extends Bounds

    /** Upper bound: T <: Bound */
    final case class Upper(bound: String) extends Bounds

    /** Lower bound: T >: Bound */
    final case class Lower(bound: String) extends Bounds

    /** Both bounds: Bound1 >: T <: Bound2 */
    final case class Both(lower: String, upper: String) extends Bounds
  }

  // Common type parameter conventions
  val A: TypeParam = TypeParam("A", 0)
  val B: TypeParam = TypeParam("B", 1)
  val C: TypeParam = TypeParam("C", 2)
  val K: TypeParam = TypeParam("K", 0)
  val V: TypeParam = TypeParam("V", 1)
  val T: TypeParam = TypeParam("T", 0)
  val F: TypeParam = TypeParam("F", 0)
  val G: TypeParam = TypeParam("G", 1)
}

/**
 * Represents the variance of a type parameter.
 *
 *   - '''Covariant''' (+): If A <: B, then F[A] <: F[B]
 *   - '''Contravariant''' (-): If A <: B, then F[B] <: F[A]
 *   - '''Invariant''': No subtyping relationship
 */
sealed trait Variance extends Product with Serializable {
  def symbol: String
  def isCovariant: Boolean     = this == Variance.Covariant
  def isContravariant: Boolean = this == Variance.Contravariant
  def isInvariant: Boolean     = this == Variance.Invariant
}

object Variance {
  case object Covariant extends Variance {
    val symbol = "+"
  }

  case object Contravariant extends Variance {
    val symbol = "-"
  }

  case object Invariant extends Variance {
    val symbol = ""
  }
}
