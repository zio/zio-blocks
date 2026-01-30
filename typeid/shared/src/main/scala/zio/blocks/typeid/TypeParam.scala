package zio.blocks.typeid

/**
 * Represents a type parameter specification.
 *
 * A TypeParam captures the essential information about a type parameter,
 * including its name, position, variance, bounds, and kind.
 *
 * For example, for `Either[+A, +B]`:
 *   - `A` would be `TypeParam("A", 0, Variance.Covariant)`
 *   - `B` would be `TypeParam("B", 1, Variance.Covariant)`
 *
 * For `Functor[F[_]]`:
 *   - `F` would be
 *     `TypeParam("F", 0, Variance.Invariant, TypeBounds.Unbounded, Kind.Star1)`
 *
 * @param name
 *   The name of the type parameter
 * @param index
 *   The position in the type parameter list (0-indexed)
 * @param variance
 *   The variance annotation (+, -, or none)
 * @param bounds
 *   The type bounds (>: Lower <: Upper)
 * @param kind
 *   The kind of this type parameter (*, * -> *, etc.)
 */
final case class TypeParam(
  name: String,
  index: Int,
  variance: Variance = Variance.Invariant,
  bounds: TypeBounds = TypeBounds.Unbounded,
  kind: Kind = Kind.Type
) {

  /**
   * Returns true if this type parameter is covariant (+).
   */
  def isCovariant: Boolean = variance.isCovariant

  /**
   * Returns true if this type parameter is contravariant (-).
   */
  def isContravariant: Boolean = variance.isContravariant

  /**
   * Returns true if this type parameter is invariant.
   */
  def isInvariant: Boolean = variance.isInvariant

  /**
   * Returns true if this type parameter has an upper bound.
   */
  def hasUpperBound: Boolean = bounds.upper.isDefined

  /**
   * Returns true if this type parameter has a lower bound.
   */
  def hasLowerBound: Boolean = bounds.lower.isDefined

  /**
   * Returns true if this type parameter is a proper type (kind *).
   */
  def isProperType: Boolean = kind.isProperType

  /**
   * Returns true if this type parameter is a type constructor (kind * -> *
   * etc.).
   */
  def isTypeConstructor: Boolean = !kind.isProperType

  /**
   * Returns a string representation of this type parameter.
   */
  override def toString: String = {
    val varianceStr = variance.symbol
    val kindStr     = if (kind.isProperType) "" else s"[${kind.arity}]"
    s"$varianceStr$name$kindStr@$index"
  }
}

object TypeParam {

  private[typeid] val A: TypeParam        = TypeParam("A", 0, Variance.Covariant)
  private[typeid] val B: TypeParam        = TypeParam("B", 1, Variance.Covariant)
  private[typeid] val C: TypeParam        = TypeParam("C", 2, Variance.Covariant)
  private[typeid] val K: TypeParam        = TypeParam("K", 0, Variance.Invariant)
  private[typeid] val V: TypeParam        = TypeParam("V", 1, Variance.Covariant)
  private[typeid] val F: TypeParam        = TypeParam("F", 0, Variance.Invariant, kind = Kind.Star1)
  private[typeid] val Contra: TypeParam   = TypeParam("T", 0, Variance.Contravariant)
  private[typeid] val CoResult: TypeParam = TypeParam("R", 1, Variance.Covariant)

  /**
   * Creates a covariant type parameter.
   */
  def covariant(name: String, index: Int): TypeParam =
    TypeParam(name, index, Variance.Covariant)

  /**
   * Creates a contravariant type parameter.
   */
  def contravariant(name: String, index: Int): TypeParam =
    TypeParam(name, index, Variance.Contravariant)

  /**
   * Creates a type parameter with an upper bound.
   */
  def bounded(name: String, index: Int, upper: TypeRepr): TypeParam =
    TypeParam(name, index, bounds = TypeBounds.upper(upper))

  /**
   * Creates a higher-kinded type parameter (F[_]).
   */
  def higherKinded(name: String, index: Int, arity: Int = 1): TypeParam =
    TypeParam(name, index, kind = Kind.constructor(arity))
}
