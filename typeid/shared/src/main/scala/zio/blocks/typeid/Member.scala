package zio.blocks.typeid

/**
 * Represents a member of a structural (refinement) type.
 *
 * Members include:
 *   - Val/Var declarations (`val x: Int`, `var y: String`)
 *   - Method declarations (`def foo(x: Int): String`)
 *   - Type members (`type T`, `type T <: Bound`)
 */
sealed trait Member {
  def name: String
}

object Member {

  /**
   * A value member (val or var).
   *
   * @param name
   *   The name of the value
   * @param tpe
   *   The type of the value
   * @param isVar
   *   Whether this is a var (mutable) or val (immutable)
   */
  final case class Val(
    name: String,
    tpe: TypeRepr,
    isVar: Boolean = false
  ) extends Member

  /**
   * A method member.
   *
   * @param name
   *   The name of the method
   * @param typeParams
   *   Type parameters of the method (for polymorphic methods)
   * @param paramLists
   *   Parameter lists (supports curried methods)
   * @param result
   *   The return type
   */
  final case class Def(
    name: String,
    typeParams: List[TypeParam] = Nil,
    paramLists: List[List[Param]] = Nil,
    result: TypeRepr
  ) extends Member

  /**
   * A type member.
   *
   * @param name
   *   The name of the type member
   * @param typeParams
   *   Type parameters (for type constructors)
   * @param lowerBound
   *   Lower bound (if any)
   * @param upperBound
   *   Upper bound (if any)
   */
  final case class TypeMember(
    name: String,
    typeParams: List[TypeParam] = Nil,
    lowerBound: Option[TypeRepr] = None,
    upperBound: Option[TypeRepr] = None
  ) extends Member {

    /**
     * Returns true if this is an abstract type member (no bounds specified or
     * not an alias).
     */
    def isAbstract: Boolean = lowerBound.isEmpty || upperBound.isEmpty || lowerBound != upperBound

    /**
     * Returns true if this is a type alias (lower and upper bounds are the
     * same).
     */
    def isAlias: Boolean = lowerBound.isDefined && lowerBound == upperBound
  }
}

/**
 * A method parameter.
 *
 * @param name
 *   The name of the parameter
 * @param tpe
 *   The type of the parameter
 * @param isImplicit
 *   Whether this is an implicit parameter
 * @param defaultValue
 *   Whether this parameter has a default value (we don't store the value
 *   itself)
 */
final case class Param(
  name: String,
  tpe: TypeRepr,
  isImplicit: Boolean = false,
  hasDefault: Boolean = false
)
