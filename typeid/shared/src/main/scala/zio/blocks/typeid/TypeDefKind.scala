package zio.blocks.typeid

/**
 * Classifies what kind of type definition a `TypeId` represents.
 *
 * This enables distinguishing between:
 *   - Classes (regular, case, abstract, final, value)
 *   - Traits (regular, sealed with known subtypes)
 *   - Objects (singleton objects)
 *   - Enums (Scala 3 enums with cases)
 *   - Type aliases and opaque types
 *   - Abstract type members
 */
sealed trait TypeDefKind {

  /** The base types (parent classes/traits) of this type definition. */
  def baseTypes: List[TypeRepr] = Nil
}

object TypeDefKind {

  /**
   * A class definition.
   *
   * @param isFinal
   *   Whether the class is final
   * @param isAbstract
   *   Whether the class is abstract
   * @param isCase
   *   Whether the class is a case class
   * @param isValue
   *   Whether the class extends AnyVal
   * @param bases
   *   Parent classes and traits this class extends
   */
  final case class Class(
    isFinal: Boolean = false,
    isAbstract: Boolean = false,
    isCase: Boolean = false,
    isValue: Boolean = false,
    bases: List[TypeRepr] = Nil
  ) extends TypeDefKind {
    override def baseTypes: List[TypeRepr] = bases
  }

  /**
   * A trait definition.
   *
   * @param isSealed
   *   Whether the trait is sealed
   * @param knownSubtypes
   *   List of known subtypes (for sealed traits)
   * @param bases
   *   Parent traits this trait extends
   */
  final case class Trait(
    isSealed: Boolean = false,
    knownSubtypes: List[TypeRepr] = Nil,
    bases: List[TypeRepr] = Nil
  ) extends TypeDefKind {
    override def baseTypes: List[TypeRepr] = bases
  }

  /**
   * A singleton object.
   *
   * @param bases
   *   Parent classes and traits this object extends
   */
  final case class Object(
    bases: List[TypeRepr] = Nil
  ) extends TypeDefKind {
    override def baseTypes: List[TypeRepr] = bases
  }

  /**
   * A Scala 3 enum definition.
   *
   * @param cases
   *   List of enum cases
   * @param bases
   *   Parent traits this enum extends
   */
  final case class Enum(
    cases: List[EnumCaseInfo],
    bases: List[TypeRepr] = Nil
  ) extends TypeDefKind {
    override def baseTypes: List[TypeRepr] = bases
  }

  /**
   * An individual enum case (when represented as its own type).
   *
   * @param parentEnum
   *   Reference to the parent enum type
   * @param ordinal
   *   The ordinal index of this case
   * @param isObjectCase
   *   True for `case Red`, false for `case RGB(...)`
   */
  final case class EnumCase(
    parentEnum: TypeRepr,
    ordinal: Int,
    isObjectCase: Boolean
  ) extends TypeDefKind {
    // The base type of an enum case is its parent enum
    override def baseTypes: List[TypeRepr] = List(parentEnum)
  }

  /** A type alias: `type Foo = Bar`. */
  case object TypeAlias extends TypeDefKind

  /**
   * An opaque type: `opaque type Foo = Bar`.
   *
   * @param publicBounds
   *   Bounds visible outside the defining scope
   */
  final case class OpaqueType(
    publicBounds: TypeBounds = TypeBounds.Unbounded
  ) extends TypeDefKind

  /** An abstract type member. */
  case object AbstractType extends TypeDefKind

  /** Unknown or unclassified type definition kind. */
  case object Unknown extends TypeDefKind
}

/**
 * Information about an enum case for serialization.
 *
 * @param name
 *   The name of the enum case
 * @param ordinal
 *   The ordinal index
 * @param params
 *   Parameters for parameterized cases (empty for object cases)
 * @param isObjectCase
 *   True for `case Red`, false for `case RGB(...)`
 */
final case class EnumCaseInfo(
  name: String,
  ordinal: Int,
  params: List[EnumCaseParam] = Nil,
  isObjectCase: Boolean = true
) {

  /** The number of parameters for this case. */
  def arity: Int = params.size
}

/**
 * A parameter of an enum case.
 *
 * @param name
 *   The parameter name
 * @param tpe
 *   The parameter type
 */
final case class EnumCaseParam(
  name: String,
  tpe: TypeRepr
)
