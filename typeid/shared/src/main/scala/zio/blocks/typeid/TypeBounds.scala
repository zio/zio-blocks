package zio.blocks.typeid

final case class TypeBounds(
  lower: Option[TypeRepr], // >: bound
  upper: Option[TypeRepr]  // <: bound
) {
  def isUnbounded: Boolean = lower.isEmpty && upper.isEmpty

  def hasLower: Boolean = lower.isDefined
  def hasUpper: Boolean = upper.isDefined

  /** Combine bounds (intersection of constraints) */
  def &(other: TypeBounds): TypeBounds = TypeBounds(
    lower = (lower, other.lower) match {
      case (Some(l1), Some(l2)) => Some(TypeRepr.Union(List(l1, l2))) // Use List explicitly
      case (Some(l), None)      => Some(l)
      case (None, Some(l))      => Some(l)
      case (None, None)         => None
    },
    upper = (upper, other.upper) match {
      case (Some(u1), Some(u2)) => Some(TypeRepr.Intersection(List(u1, u2))) // Use List explicitly
      case (Some(u), None)      => Some(u)
      case (None, Some(u))      => Some(u)
      case (None, None)         => None
    }
  )
}

object TypeBounds {
  val empty: TypeBounds = TypeBounds(None, None)

  def upper(tpe: TypeRepr): TypeBounds = TypeBounds(None, Some(tpe))
  def lower(tpe: TypeRepr): TypeBounds = TypeBounds(Some(tpe), None)
  def exact(tpe: TypeRepr): TypeBounds = TypeBounds(Some(tpe), Some(tpe))
}
