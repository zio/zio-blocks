package zio.blocks.typeid

/**
 * Represents members of structural/refinement types.
 *
 * Structural types in Scala can contain value members, method definitions, and
 * abstract type members. This sealed trait captures all these variants.
 *
 * ==Example==
 * The structural type `{ def size: Int; val isEmpty: Boolean; type T }`:
 * {{{
 * List(
 *   Member.Def("size", Nil, TypeRepr.intType),
 *   Member.Val("isEmpty", TypeRepr.booleanType),
 *   Member.TypeMember("T", Nil, None, None)
 * )
 * }}}
 */
sealed trait Member extends Product with Serializable {

  /**
   * The name of this member.
   */
  def name: String

  /**
   * Returns a human-readable representation of this member.
   */
  def show: String

  /**
   * Substitutes type parameter references with concrete types.
   */
  def substitute(substitutions: Map[TypeParam, TypeRepr]): Member
}

object Member {

  /**
   * A val or var member: `val name: Type` or `var name: Type`.
   *
   * @param name
   *   The member name
   * @param tpe
   *   The type of this member
   * @param isVar
   *   Whether this is a `var` (mutable) or `val` (immutable)
   */
  final case class Val(
    name: String,
    tpe: TypeRepr,
    isVar: Boolean = false
  ) extends Member {
    def show: String = {
      val keyword = if (isVar) "var" else "val"
      s"$keyword $name: ${tpe.show}"
    }

    def substitute(substitutions: Map[TypeParam, TypeRepr]): Member =
      copy(tpe = tpe.substitute(substitutions))
  }

  /**
   * A method member: `def name(params...): ReturnType`.
   *
   * @param name
   *   The method name
   * @param typeParams
   *   Type parameters for this method (if any)
   * @param paramLists
   *   The parameter lists (supporting curried methods)
   * @param result
   *   The return type
   */
  final case class Def(
    name: String,
    typeParams: List[TypeParam] = Nil,
    paramLists: List[List[Param]] = Nil,
    result: TypeRepr
  ) extends Member {

    /**
     * Total number of value parameters.
     */
    def paramCount: Int = paramLists.map(_.size).sum

    /**
     * Whether this method has no parameters.
     */
    def isNullary: Boolean = paramLists.isEmpty

    def show: String = {
      val typeParamsStr =
        if (typeParams.isEmpty) ""
        else typeParams.map(_.signature).mkString("[", ", ", "]")

      val paramsStr = paramLists.map { params =>
        params.map(p => s"${p.name}: ${p.tpe.show}").mkString("(", ", ", ")")
      }.mkString

      s"def $name$typeParamsStr$paramsStr: ${result.show}"
    }

    def substitute(substitutions: Map[TypeParam, TypeRepr]): Member =
      copy(
        paramLists = paramLists.map(_.map(p => p.copy(tpe = p.tpe.substitute(substitutions)))),
        result = result.substitute(substitutions)
      )
  }

  object Def {

    /**
     * Creates a nullary method (no parameters).
     */
    def apply(name: String, result: TypeRepr): Def =
      new Def(name, Nil, Nil, result)

    /**
     * Creates a method with a single parameter list.
     */
    def apply(name: String, params: List[Param], result: TypeRepr): Def =
      new Def(name, Nil, List(params), result)
  }

  /**
   * An abstract type member: `type T [>: L] [<: U]`.
   *
   * @param name
   *   The type member name
   * @param typeParams
   *   Type parameters for this type member (if any)
   * @param lowerBound
   *   Optional lower bound
   * @param upperBound
   *   Optional upper bound
   */
  final case class TypeMember(
    name: String,
    typeParams: List[TypeParam] = Nil,
    lowerBound: Option[TypeRepr] = None,
    upperBound: Option[TypeRepr] = None
  ) extends Member {

    /**
     * Whether this type member has any bounds.
     */
    def isBounded: Boolean = lowerBound.isDefined || upperBound.isDefined

    /**
     * Whether this is an alias (has both bounds equal).
     */
    def isAliasDefinition: Boolean =
      (lowerBound, upperBound) match {
        case (Some(l), Some(u)) => l == u
        case _                  => false
      }

    def show: String = {
      val typeParamsStr =
        if (typeParams.isEmpty) ""
        else typeParams.map(_.signature).mkString("[", ", ", "]")

      val boundsStr = (lowerBound, upperBound) match {
        case (Some(l), Some(u)) if l == u => s" = ${l.show}"
        case (Some(l), Some(u))           => s" >: ${l.show} <: ${u.show}"
        case (Some(l), None)              => s" >: ${l.show}"
        case (None, Some(u))              => s" <: ${u.show}"
        case (None, None)                 => ""
      }

      s"type $name$typeParamsStr$boundsStr"
    }

    def substitute(substitutions: Map[TypeParam, TypeRepr]): Member =
      copy(
        lowerBound = lowerBound.map(_.substitute(substitutions)),
        upperBound = upperBound.map(_.substitute(substitutions))
      )
  }

  object TypeMember {

    /**
     * Creates an abstract type member with no bounds.
     */
    def apply(name: String): TypeMember =
      new TypeMember(name, Nil, None, None)

    /**
     * Creates a type alias member: `type T = U`.
     */
    def alias(name: String, aliased: TypeRepr): TypeMember =
      new TypeMember(name, Nil, Some(aliased), Some(aliased))
  }
}

/**
 * A value parameter in a method signature.
 *
 * @param name
 *   The parameter name
 * @param tpe
 *   The parameter type
 * @param isImplicit
 *   Whether this is an implicit parameter
 * @param hasDefault
 *   Whether this parameter has a default value
 */
final case class Param(
  name: String,
  tpe: TypeRepr,
  isImplicit: Boolean = false,
  hasDefault: Boolean = false
) {
  def show: String = {
    val implicitStr = if (isImplicit) "implicit " else ""
    val defaultStr  = if (hasDefault) " = ..." else ""
    s"$implicitStr$name: ${tpe.show}$defaultStr"
  }
}

object Param {

  /**
   * Creates a simple parameter.
   */
  def apply(name: String, tpe: TypeRepr): Param =
    new Param(name, tpe, false, false)
}
