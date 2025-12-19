package zio.blocks.typeid

/**
 * Representation of type expressions in the Scala type system.
 * 
 * `TypeRepr` captures the full structure of types including:
 * - References to named types
 * - Type applications (generics)
 * - Structural/refinement types
 * - Intersection and union types
 * - Function and tuple types
 * - Singleton and constant types
 * 
 * This representation enables full type introspection and manipulation
 * at runtime while maintaining structural fidelity to the source types.
 * 
 * == Example ==
 * The type `Map[String, List[Int]]` would be represented as:
 * {{{
 * TypeRepr.Applied(
 *   TypeRepr.Ref(TypeId.map),
 *   List(
 *     TypeRepr.Ref(TypeId.string),
 *     TypeRepr.Applied(
 *       TypeRepr.Ref(TypeId.list),
 *       List(TypeRepr.Ref(TypeId.int))
 *     )
 *   )
 * )
 * }}}
 */
sealed trait TypeRepr extends Product with Serializable {
  
  /**
   * Returns a human-readable string representation of this type.
   */
  def show: String

  /**
   * Whether this is a reference to a named type (unapplied).
   */
  def isRef: Boolean = false

  /**
   * Whether this is a type application (generic usage).
   */
  def isApplied: Boolean = false

  /**
   * Whether this is a function type.
   */
  def isFunction: Boolean = false

  /**
   * Whether this is a tuple type.
   */
  def isTuple: Boolean = false

  /**
   * Substitutes type parameter references with concrete types.
   * 
   * @param substitutions Mapping from type parameters to their replacements
   * @return A new TypeRepr with substitutions applied
   */
  def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr
}

object TypeRepr {

  // ============================================================================
  // References to Named Types
  // ============================================================================

  /**
   * Reference to a named type constructor (unapplied).
   * 
   * If `id.arity == 0`, this is already a proper type (e.g., `Int`).
   * If `id.arity > 0`, this is a type constructor (e.g., `List` without args).
   * 
   * @param id The TypeId of the referenced type
   */
  final case class Ref(id: TypeId[_]) extends TypeRepr {
    override def isRef: Boolean = true

    def show: String = id.fullName

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr = this
  }

  /**
   * Reference to a type parameter.
   * 
   * Used to represent type variables in polymorphic contexts.
   * 
   * @param param The type parameter being referenced
   */
  final case class ParamRef(param: TypeParam) extends TypeRepr {
    def show: String = param.name

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr =
      substitutions.getOrElse(param, this)
  }

  // ============================================================================
  // Type Application (Generics)
  // ============================================================================

  /**
   * Application of a type constructor to type arguments.
   * 
   * == Examples ==
   * - `List[Int]` → `Applied(Ref(listId), List(Ref(intId)))`
   * - `F[A]` → `Applied(ParamRef(F), List(ParamRef(A)))`
   * - `Either[String, Int]` → `Applied(Ref(eitherId), List(Ref(stringId), Ref(intId)))`
   * 
   * @param tycon The type constructor being applied
   * @param args  The type arguments
   */
  final case class Applied(tycon: TypeRepr, args: List[TypeRepr]) extends TypeRepr {
    override def isApplied: Boolean = true

    def show: String = {
      val tyconStr = tycon.show
      val argsStr = args.map(_.show).mkString(", ")
      s"$tyconStr[$argsStr]"
    }

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr =
      Applied(
        tycon.substitute(substitutions),
        args.map(_.substitute(substitutions))
      )
  }

  // ============================================================================
  // Structural/Refinement Types
  // ============================================================================

  /**
   * Structural or refinement type: `{ def foo: Int; type T; ... }`
   * 
   * Represents types defined by their members rather than by name.
   * 
   * @param parents The parent types this structural type extends
   * @param members The members defined in this structural type
   */
  final case class Structural(
    parents: List[TypeRepr],
    members: List[Member]
  ) extends TypeRepr {
    def show: String = {
      val parentsStr = if (parents.isEmpty) "" else parents.map(_.show).mkString(" with ") + " "
      val membersStr = members.map(_.show).mkString("; ")
      s"$parentsStr{ $membersStr }"
    }

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr =
      Structural(
        parents.map(_.substitute(substitutions)),
        members.map(_.substitute(substitutions))
      )
  }

  // ============================================================================
  // Compound Types
  // ============================================================================

  /**
   * Intersection type: `A & B` (Scala 3) or `A with B` (Scala 2).
   * 
   * @param left  The left type
   * @param right The right type
   */
  final case class Intersection(left: TypeRepr, right: TypeRepr) extends TypeRepr {
    def show: String = s"${left.show} & ${right.show}"

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr =
      Intersection(
        left.substitute(substitutions),
        right.substitute(substitutions)
      )
  }

  /**
   * Union type: `A | B` (Scala 3 only).
   * 
   * @param left  The left type
   * @param right The right type
   */
  final case class Union(left: TypeRepr, right: TypeRepr) extends TypeRepr {
    def show: String = s"${left.show} | ${right.show}"

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr =
      Union(
        left.substitute(substitutions),
        right.substitute(substitutions)
      )
  }

  // ============================================================================
  // Function and Tuple Types
  // ============================================================================

  /**
   * Tuple type: `(A, B, C)`.
   * 
   * @param elems The element types
   */
  final case class Tuple(elems: List[TypeRepr]) extends TypeRepr {
    override def isTuple: Boolean = true

    def show: String = elems.map(_.show).mkString("(", ", ", ")")

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr =
      Tuple(elems.map(_.substitute(substitutions)))
  }

  /**
   * Function type: `(A, B) => C`.
   * 
   * @param params The parameter types
   * @param result The result type
   */
  final case class Function(params: List[TypeRepr], result: TypeRepr) extends TypeRepr {
    override def isFunction: Boolean = true

    def show: String = {
      val paramsStr = params.map(_.show).mkString("(", ", ", ")")
      s"$paramsStr => ${result.show}"
    }

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr =
      Function(
        params.map(_.substitute(substitutions)),
        result.substitute(substitutions)
      )
  }

  // ============================================================================
  // Special Types
  // ============================================================================

  /**
   * Singleton type: `x.type`.
   * 
   * @param path The term path to the singleton value
   */
  final case class Singleton(path: TermPath) extends TypeRepr {
    def show: String = s"${path.show}.type"

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr = this
  }

  /**
   * Constant/literal type: `42`, `"foo"`, `true`.
   * 
   * @param value The literal value
   */
  final case class Constant(value: Any) extends TypeRepr {
    def show: String = value match {
      case s: String => s""""$s""""
      case c: Char   => s"'$c'"
      case other     => other.toString
    }

    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr = this
  }

  /**
   * The Any type (top type).
   */
  case object AnyType extends TypeRepr {
    def show: String = "Any"
    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr = this
  }

  /**
   * The Nothing type (bottom type).
   */
  case object NothingType extends TypeRepr {
    def show: String = "Nothing"
    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr = this
  }

  /**
   * The Null type.
   */
  case object NullType extends TypeRepr {
    def show: String = "Null"
    def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr = this
  }

  // ============================================================================
  // Factory Methods
  // ============================================================================

  /**
   * Creates a reference to a TypeId.
   */
  def ref[A](id: TypeId[A]): Ref = Ref(id)

  /**
   * Creates a type application.
   */
  def applied(tycon: TypeRepr, args: TypeRepr*): Applied = Applied(tycon, args.toList)

  /**
   * Creates a function type.
   */
  def function(params: List[TypeRepr], result: TypeRepr): Function = Function(params, result)

  /**
   * Creates a function type with single parameter.
   */
  def function1(param: TypeRepr, result: TypeRepr): Function = Function(List(param), result)

  /**
   * Creates a tuple type.
   */
  def tuple(elems: TypeRepr*): Tuple = Tuple(elems.toList)

  // ============================================================================
  // Convenience Constructors for Common Types
  // ============================================================================

  val unitType: Ref = Ref(TypeId.unit)
  val booleanType: Ref = Ref(TypeId.boolean)
  val byteType: Ref = Ref(TypeId.byte)
  val shortType: Ref = Ref(TypeId.short)
  val intType: Ref = Ref(TypeId.int)
  val longType: Ref = Ref(TypeId.long)
  val floatType: Ref = Ref(TypeId.float)
  val doubleType: Ref = Ref(TypeId.double)
  val charType: Ref = Ref(TypeId.char)
  val stringType: Ref = Ref(TypeId.string)

  /**
   * Creates a List[A] type representation.
   */
  def listOf(elem: TypeRepr): Applied = Applied(Ref(TypeId.list), List(elem))

  /**
   * Creates an Option[A] type representation.
   */
  def optionOf(elem: TypeRepr): Applied = Applied(Ref(TypeId.option), List(elem))

  /**
   * Creates a Set[A] type representation.
   */
  def setOf(elem: TypeRepr): Applied = Applied(Ref(TypeId.set), List(elem))

  /**
   * Creates a Map[K, V] type representation.
   */
  def mapOf(key: TypeRepr, value: TypeRepr): Applied = Applied(Ref(TypeId.map), List(key, value))
}
