package zio.blocks.typeid

final case class TypeParam(
  name: String,
  index: Int,
  variance: Variance = Variance.Invariant,
  bounds: TypeBounds = TypeBounds.empty,
  kind: Kind = Kind.Type
) {
  def isHigherKinded: Boolean  = kind.isHigherKinded
  def isCovariant: Boolean     = variance == Variance.Covariant
  def isContravariant: Boolean = variance == Variance.Contravariant
  def isInvariant: Boolean     = variance == Variance.Invariant
}

object TypeParam {
  def invariant(name: String, index: Int, bounds: TypeBounds = TypeBounds.empty): TypeParam =
    TypeParam(name, index, Variance.Invariant, bounds)

  def covariant(name: String, index: Int, bounds: TypeBounds = TypeBounds.empty): TypeParam =
    TypeParam(name, index, Variance.Covariant, bounds)

  def contravariant(name: String, index: Int, bounds: TypeBounds = TypeBounds.empty): TypeParam =
    TypeParam(name, index, Variance.Contravariant, bounds)
}
