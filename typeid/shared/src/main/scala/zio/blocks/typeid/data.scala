package zio.blocks.typeid

/**
 * Represents the variance of a type parameter.
 */
sealed trait Variance {
  def symbol: String
  def flip: Variance
}

object Variance {
  case object Invariant extends Variance {
    val symbol: String = ""
    def flip: Variance = Invariant
  }

  case object Covariant extends Variance {
    val symbol: String = "+"
    def flip: Variance = Contravariant
  }

  case object Contravariant extends Variance {
    val symbol: String = "-"
    def flip: Variance = Covariant
  }
}

/**
 * Represents type bounds for a type parameter or wildcard.
 */
final case class TypeBounds(
  lower: Option[TypeRepr], // >: bound
  upper: Option[TypeRepr]  // <: bound
) {
  def isUnbounded: Boolean = lower.isEmpty && upper.isEmpty
  def hasLower: Boolean    = lower.isDefined
  def hasUpper: Boolean    = upper.isDefined

  /** Combine bounds (intersection of constraints) */
  def &(other: TypeBounds): TypeBounds = TypeBounds(
    lower = (lower, other.lower) match {
      case (Some(l1), Some(l2)) => Some(TypeRepr.Union(l1, l2))
      case (Some(l), None)      => Some(l)
      case (None, Some(l))      => Some(l)
      case (None, None)         => None
    },
    upper = (upper, other.upper) match {
      case (Some(u1), Some(u2)) => Some(TypeRepr.Intersection(u1, u2))
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

/**
 * Represents the kind of a type (proper type vs type constructor).
 */
sealed trait Kind {
  def arity: Int
  def isProperType: Boolean   = this == Kind.Type
  def isHigherKinded: Boolean = !isProperType
}

object Kind {
  case object Type extends Kind {
    def arity: Int = 0
  }

  final case class Arrow(params: List[Kind], result: Kind) extends Kind {
    def arity: Int = params.size
  }

  val `* -> *`: Kind      = Arrow(List(Type), Type)
  val `* -> * -> *`: Kind = Arrow(List(Type, Type), Type)

  def arity(n: Int): Kind =
    if (n == 0) Type
    else Arrow(List.fill(n)(Type), Type)
}

/**
 * Complete type parameter specification.
 */
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

  override def equals(other: Any): Boolean = other match {
    case that: TypeParam => this.index == that.index // Simplified for structural comparison
    case _               => false
  }
  override def hashCode(): Int = index
}

object TypeParam {
  def invariant(name: String, index: Int): TypeParam =
    TypeParam(name, index, Variance.Invariant)

  def covariant(name: String, index: Int): TypeParam =
    TypeParam(name, index, Variance.Covariant)

  def contravariant(name: String, index: Int): TypeParam =
    TypeParam(name, index, Variance.Contravariant)
}
