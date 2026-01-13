package zio.blocks

/**
 * The typeid package provides type identification and reflection capabilities for Scala.
 *
 * Key types:
 * - [[typeid.TypeId]] - Identity of a type or type constructor
 * - [[typeid.Owner]] - Package and scope hierarchy
 * - [[typeid.TypeParam]] - Type parameter specification
 * - [[typeid.TypeRepr]] - Type structure representation
 */
package object typeid {

  /**
   * Utility functions for working with TypeRepr.
   */
  object TypeReprOps {

    /**
     * Substitute type parameters in a type representation.
     *
     * @param repr The type representation to substitute into
     * @param substitutions Mapping from type parameters to their replacements
     * @return The type representation with substitutions applied
     */
    def substitute(
      repr: TypeRepr,
      substitutions: Map[TypeParam, TypeRepr]
    ): TypeRepr =
      repr match {
        case TypeRepr.ParamRef(param) =>
          substitutions.getOrElse(param, repr)

        case TypeRepr.Ref(_) =>
          repr

        case TypeRepr.Applied(tycon, args) =>
          TypeRepr.Applied(
            substitute(tycon, substitutions),
            args.map(substitute(_, substitutions))
          )

        case TypeRepr.Structural(parents, members) =>
          TypeRepr.Structural(
            parents.map(substitute(_, substitutions)),
            members.map(substituteMember(_, substitutions))
          )

        case TypeRepr.Intersection(l, r) =>
          TypeRepr.Intersection(
            substitute(l, substitutions),
            substitute(r, substitutions)
          )

        case TypeRepr.Union(l, r) =>
          TypeRepr.Union(
            substitute(l, substitutions),
            substitute(r, substitutions)
          )

        case TypeRepr.Tuple(elems) =>
          TypeRepr.Tuple(elems.map(substitute(_, substitutions)))

        case TypeRepr.Function(params, result) =>
          TypeRepr.Function(
            params.map(substitute(_, substitutions)),
            substitute(result, substitutions)
          )

        case TypeRepr.Singleton(_) | TypeRepr.Constant(_) | TypeRepr.AnyType | TypeRepr.NothingType =>
          repr
      }

    /**
     * Substitute type parameters in a structural type member.
     */
    private def substituteMember(
      m: Member,
      substitutions: Map[TypeParam, TypeRepr]
    ): Member =
      m match {
        case Member.Val(name, tpe, isVar) =>
          Member.Val(name, substitute(tpe, substitutions), isVar)

        case Member.Def(name, paramLists, result) =>
          Member.Def(
            name,
            paramLists.map(_.map { p => Param(p.name, substitute(p.tpe, substitutions)) }),
            substitute(result, substitutions)
          )

        case Member.TypeMember(name, typeParams, lower, upper) =>
          Member.TypeMember(
            name,
            typeParams,
            lower.map(substitute(_, substitutions)),
            upper.map(substitute(_, substitutions))
          )
      }
  }

  /**
   * Utility functions for working with TypeId.
   */
  object TypeIdOps {

    /**
     * Get the underlying type for a type alias or opaque type.
     *
     * For nominal types, returns None.
     * For aliases and opaque types, returns Some(underlying type).
     *
     * @param id The TypeId to inspect
     * @param args Type arguments to apply (for generic aliases)
     * @return The underlying type, if applicable
     */
    def underlyingType(
      id: TypeId[_],
      args: List[TypeRepr] = Nil
    ): Option[TypeRepr] = id match {
      case TypeId.Alias(_, _, typeParams, aliased) =>
        if (args.isEmpty) {
          Some(aliased)
        } else {
          val subs = typeParams.zip(args).toMap
          Some(TypeReprOps.substitute(aliased, subs))
        }

      case TypeId.Opaque(_, _, typeParams, representation) =>
        if (args.isEmpty) {
          Some(representation)
        } else {
          val subs = typeParams.zip(args).toMap
          Some(TypeReprOps.substitute(representation, subs))
        }

      case _ =>
        None
    }

    /**
     * Check if a TypeId represents a type alias.
     */
    def isAlias(id: TypeId[_]): Boolean = id match {
      case _: TypeId.Alias[_] => true
      case _                  => false
    }

    /**
     * Check if a TypeId represents an opaque type.
     */
    def isOpaque(id: TypeId[_]): Boolean = id match {
      case _: TypeId.Opaque[_] => true
      case _                   => false
    }

    /**
     * Check if a TypeId represents a nominal type.
     */
    def isNominal(id: TypeId[_]): Boolean = id match {
      case _: TypeId.Nominal[_] => true
      case _                    => false
    }
  }
}
