package zio.blocks.codegen.ir

/**
 * Represents a Scala type definition in the IR.
 *
 * All compound types (case classes, sealed traits, enums, objects, newtypes)
 * extend this trait, providing a uniform interface for code generation.
 */
sealed trait TypeDefinition {

  /**
   * The name of the type.
   */
  def name: String

  /**
   * Annotations applied to the type.
   */
  def annotations: List[Annotation]

  /**
   * Optional documentation for the type.
   */
  def doc: Option[String]
}

/**
 * Represents a Scala case class in the IR.
 *
 * @param name
 *   The name of the case class
 * @param fields
 *   The fields (constructor parameters) of the case class
 * @param typeParams
 *   Type parameters for generic case classes (defaults to empty list)
 * @param extendsTypes
 *   Types that this case class extends (defaults to empty list)
 * @param derives
 *   Type class derivations (defaults to empty list)
 * @param annotations
 *   Annotations on the case class (defaults to empty list)
 * @param companion
 *   Optional companion object (defaults to None)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * val person = CaseClass(
 *   "Person",
 *   fields = List(
 *     Field("name", TypeRef.String),
 *     Field("age", TypeRef.Int)
 *   ),
 *   derives = List("Schema")
 * )
 *   }}}
 */
final case class CaseClass(
  name: String,
  fields: List[Field],
  typeParams: List[TypeRef] = Nil,
  extendsTypes: List[TypeRef] = Nil,
  derives: List[String] = Nil,
  annotations: List[Annotation] = Nil,
  companion: Option[CompanionObject] = None,
  doc: Option[String] = None
) extends TypeDefinition

/**
 * Represents a Scala sealed trait in the IR.
 *
 * @param name
 *   The name of the sealed trait
 * @param typeParams
 *   Type parameters for generic sealed traits (defaults to empty list)
 * @param cases
 *   The cases (subtypes) of the sealed trait (defaults to empty list)
 * @param annotations
 *   Annotations on the sealed trait (defaults to empty list)
 * @param companion
 *   Optional companion object (defaults to None)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * val shape = SealedTrait(
 *   "Shape",
 *   cases = List(
 *     SealedTraitCase.CaseClassCase(CaseClass("Circle", List(Field("radius", TypeRef.Double)))),
 *     SealedTraitCase.CaseObjectCase("Unknown")
 *   )
 * )
 *   }}}
 */
final case class SealedTrait(
  name: String,
  typeParams: List[TypeRef] = Nil,
  cases: List[SealedTraitCase] = Nil,
  annotations: List[Annotation] = Nil,
  companion: Option[CompanionObject] = None,
  doc: Option[String] = None
) extends TypeDefinition

/**
 * Represents a case within a sealed trait hierarchy.
 */
sealed trait SealedTraitCase

object SealedTraitCase {

  /**
   * A case class variant of a sealed trait.
   *
   * @param cc
   *   The case class definition
   */
  final case class CaseClassCase(cc: CaseClass) extends SealedTraitCase

  /**
   * A case object variant of a sealed trait.
   *
   * @param name
   *   The name of the case object
   */
  final case class CaseObjectCase(name: String) extends SealedTraitCase
}

/**
 * Represents a Scala 3 enum in the IR.
 *
 * @param name
 *   The name of the enum
 * @param cases
 *   The enum cases
 * @param extendsTypes
 *   Types that this enum extends (defaults to empty list)
 * @param annotations
 *   Annotations on the enum (defaults to empty list)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * val color = Enum(
 *   "Color",
 *   cases = List(
 *     EnumCase.SimpleCase("Red"),
 *     EnumCase.SimpleCase("Green"),
 *     EnumCase.SimpleCase("Blue")
 *   )
 * )
 *   }}}
 */
final case class Enum(
  name: String,
  cases: List[EnumCase],
  extendsTypes: List[TypeRef] = Nil,
  annotations: List[Annotation] = Nil,
  doc: Option[String] = None
) extends TypeDefinition

/**
 * Represents a case within a Scala 3 enum.
 */
sealed trait EnumCase

object EnumCase {

  /**
   * A simple enum case without parameters.
   *
   * @param name
   *   The name of the enum case
   */
  final case class SimpleCase(name: String) extends EnumCase

  /**
   * A parameterized enum case with fields.
   *
   * @param name
   *   The name of the enum case
   * @param fields
   *   The fields of the parameterized case
   */
  final case class ParameterizedCase(name: String, fields: List[Field]) extends EnumCase
}

/**
 * Represents a Scala object definition in the IR.
 *
 * @param name
 *   The name of the object
 * @param members
 *   The members of the object (defaults to empty list)
 * @param extendsTypes
 *   Types that this object extends (defaults to empty list)
 * @param annotations
 *   Annotations on the object (defaults to empty list)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * val utils = ObjectDef(
 *   "Utils",
 *   members = List(
 *     ObjectMember.ValMember("MaxRetries", TypeRef.Int, "3")
 *   )
 * )
 *   }}}
 */
final case class ObjectDef(
  name: String,
  members: List[ObjectMember] = Nil,
  extendsTypes: List[TypeRef] = Nil,
  annotations: List[Annotation] = Nil,
  doc: Option[String] = None
) extends TypeDefinition

/**
 * Represents a member within an object definition.
 */
sealed trait ObjectMember

object ObjectMember {

  /**
   * A val member within an object.
   *
   * @param name
   *   The name of the val
   * @param typeRef
   *   The type of the val
   * @param value
   *   The value expression as a string
   */
  final case class ValMember(name: String, typeRef: TypeRef, value: String) extends ObjectMember

  /**
   * A def member within an object.
   *
   * @param method
   *   The method definition
   */
  final case class DefMember(method: Method) extends ObjectMember

  /**
   * A type alias member within an object.
   *
   * @param name
   *   The name of the type alias
   * @param typeRef
   *   The aliased type
   */
  final case class TypeAlias(name: String, typeRef: TypeRef) extends ObjectMember

  /**
   * A nested type definition within an object.
   *
   * @param typeDef
   *   The nested type definition
   */
  final case class NestedType(typeDef: TypeDefinition) extends ObjectMember
}

/**
 * Represents a companion object in the IR.
 *
 * @param members
 *   The members of the companion object (defaults to empty list)
 */
final case class CompanionObject(
  members: List[ObjectMember] = Nil
)

/**
 * Represents a newtype wrapper in the IR.
 *
 * @param name
 *   The name of the newtype
 * @param wrappedType
 *   The underlying type being wrapped
 * @param annotations
 *   Annotations on the newtype (defaults to empty list)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * val userId = Newtype("UserId", wrappedType = TypeRef.Long)
 *   }}}
 */
final case class Newtype(
  name: String,
  wrappedType: TypeRef,
  annotations: List[Annotation] = Nil,
  doc: Option[String] = None
) extends TypeDefinition
