package zio.blocks.typeid

/**
 * A type expression that can represent complex types like `List[Int]` or
 * `Map[String, Int]`.
 *
 * TypeRepr is the "type level AST" that describes how types are constructed.
 * This is a minimal MVP implementation - extensible for future Structural,
 * Union, etc.
 */
sealed trait TypeRepr {

  /** Check if this is a reference to a simple type (not applied) */
  def isRef: Boolean = this.isInstanceOf[TypeRepr.Ref]

  /** Check if this is a type parameter reference */
  def isParamRef: Boolean = this.isInstanceOf[TypeRepr.ParamRef]

  /** Check if this is an applied type (e.g., List[Int]) */
  def isApplied: Boolean = this.isInstanceOf[TypeRepr.Applied]
}

object TypeRepr {

  /**
   * Reference to a named type (could be unapplied type constructor or proper
   * type).
   *
   * Examples:
   *   - `Ref(intId)` for `Int`
   *   - `Ref(listId)` for the unapplied `List` type constructor
   */
  final case class Ref(id: TypeId[_]) extends TypeRepr {
    override def toString: String = id.fullName
  }

  /**
   * Reference to a type parameter.
   *
   * Used in alias and opaque type definitions, e.g., in
   * `type MyList[A] = List[A]`, the RHS contains a ParamRef to A.
   */
  final case class ParamRef(param: TypeParam) extends TypeRepr {
    override def toString: String = param.name
  }

  /**
   * Application of a type constructor to type arguments.
   *
   * Examples:
   *   - `Applied(Ref(listId), List(Ref(intId)))` for `List[Int]`
   *   - `Applied(Ref(mapId), List(Ref(stringId), Ref(intId)))` for
   *     `Map[String, Int]`
   */
  final case class Applied(tycon: TypeRepr, args: List[TypeRepr]) extends TypeRepr {
    override def toString: String = args match {
      case Nil => tycon.toString
      case _   => s"$tycon[${args.mkString(", ")}]"
    }
  }

  // Future additions (commented out, kept for extensibility):
  // final case class Structural(parents: List[TypeRepr], members: List[Member]) extends TypeRepr
  // final case class Intersection(left: TypeRepr, right: TypeRepr) extends TypeRepr
  // final case class Union(left: TypeRepr, right: TypeRepr) extends TypeRepr
  // final case class Tuple(elems: List[TypeRepr]) extends TypeRepr
  // final case class Function(params: List[TypeRepr], result: TypeRepr) extends TypeRepr
  // final case class Singleton(path: TermPath) extends TypeRepr
  // final case class Constant(value: Any) extends TypeRepr
  // case object AnyType extends TypeRepr
  // case object NothingType extends TypeRepr
}
