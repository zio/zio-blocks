package zio.blocks.typeid

/**
 * Represents a member of a structural (refinement) type.
 */
sealed trait Member

object Member {
  /**
   * A val or var member.
   *
   * @param name  The name of the val/var
   * @param tpe   The type of the val/var
   * @param isVar Whether this is a var (mutable) or val (immutable)
   */
  final case class Val(
    name: String,
    tpe: TypeRepr,
    isVar: Boolean = false
  ) extends Member

  /**
   * A def (method) member.
   *
   * @param name       The name of the method
   * @param paramLists The parameter lists (supporting multiple parameter lists)
   * @param result     The result type
   */
  final case class Def(
    name: String,
    paramLists: List[List[Param]],
    result: TypeRepr
  ) extends Member

  /**
   * A type member (abstract or concrete type).
   *
   * @param name       The name of the type member
   * @param typeParams Type parameters of the type member (for higher-kinded type members)
   * @param lowerBound Lower bound (>:) if any
   * @param upperBound Upper bound (<:) if any
   */
  final case class TypeMember(
    name: String,
    typeParams: List[TypeParam],
    lowerBound: Option[TypeRepr],
    upperBound: Option[TypeRepr]
  ) extends Member
}

/**
 * Represents a parameter in a method signature.
 *
 * @param name The parameter name
 * @param tpe  The parameter type
 */
final case class Param(name: String, tpe: TypeRepr)
