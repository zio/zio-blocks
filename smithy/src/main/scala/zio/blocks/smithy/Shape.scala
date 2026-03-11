package zio.blocks.smithy

/**
 * Represents a shape definition in a Smithy model.
 *
 * Every shape has a name and a list of traits applied to it. Subtypes represent
 * the different shape categories defined by the Smithy specification: simple
 * shapes, enum shapes, aggregate shapes, and service shapes.
 */
sealed trait Shape {

  /** The shape name (e.g., "MyStructure"). */
  def name: String

  /** The traits applied to this shape. */
  def traits: List[TraitApplication]
}

// ---------------------------------------------------------------------------
// Simple shapes
// ---------------------------------------------------------------------------

/**
 * A blob shape representing arbitrary binary data.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class BlobShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A boolean shape representing true/false values.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class BooleanShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A string shape representing UTF-8 text.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class StringShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A byte shape representing an 8-bit signed integer.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class ByteShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A short shape representing a 16-bit signed integer.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class ShortShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * An integer shape representing a 32-bit signed integer.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class IntegerShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A long shape representing a 64-bit signed integer.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class LongShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A float shape representing a single-precision IEEE 754 floating point number.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class FloatShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A double shape representing a double-precision IEEE 754 floating point
 * number.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class DoubleShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A bigInteger shape representing an arbitrarily large signed integer.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class BigIntegerShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A bigDecimal shape representing an arbitrary-precision decimal number.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class BigDecimalShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A timestamp shape representing a point in time.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class TimestampShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

/**
 * A document shape representing protocol-agnostic open content.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 */
final case class DocumentShape(name: String, traits: List[TraitApplication] = Nil) extends Shape

// ---------------------------------------------------------------------------
// Enum shapes
// ---------------------------------------------------------------------------

/**
 * A string enum shape containing a fixed set of string values.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param members
 *   the enum members defining allowed values
 */
final case class EnumShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  members: List[EnumMember] = Nil
) extends Shape

/**
 * An integer enum shape containing a fixed set of integer values.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param members
 *   the enum members defining allowed integer values
 */
final case class IntEnumShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  members: List[IntEnumMember] = Nil
) extends Shape

// ---------------------------------------------------------------------------
// Aggregate shapes
// ---------------------------------------------------------------------------

/**
 * A list shape representing an ordered homogeneous collection.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param member
 *   the member definition specifying the element type
 */
final case class ListShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  member: MemberDefinition
) extends Shape

/**
 * A map shape representing a collection of key-value pairs.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param key
 *   the member definition specifying the key type
 * @param value
 *   the member definition specifying the value type
 */
final case class MapShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  key: MemberDefinition,
  value: MemberDefinition
) extends Shape

/**
 * A structure shape representing a fixed set of named, heterogeneous members.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param members
 *   the member definitions
 */
final case class StructureShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  members: List[MemberDefinition] = Nil
) extends Shape

/**
 * A union shape representing a tagged union of named members.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param members
 *   the member definitions (exactly one is set at any time)
 */
final case class UnionShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  members: List[MemberDefinition] = Nil
) extends Shape

// ---------------------------------------------------------------------------
// Service shapes
// ---------------------------------------------------------------------------

/**
 * A service shape representing an API service entry point.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param version
 *   the optional version string for the service
 * @param operations
 *   the ShapeIds of operations bound to this service
 * @param resources
 *   the ShapeIds of resources bound to this service
 * @param errors
 *   the ShapeIds of common errors for the service
 */
final case class ServiceShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  version: Option[String] = None,
  operations: List[ShapeId] = Nil,
  resources: List[ShapeId] = Nil,
  errors: List[ShapeId] = Nil
) extends Shape

/**
 * An operation shape representing a single API operation.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param input
 *   the optional ShapeId of the input structure
 * @param output
 *   the optional ShapeId of the output structure
 * @param errors
 *   the ShapeIds of errors this operation can return
 */
final case class OperationShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  input: Option[ShapeId] = None,
  output: Option[ShapeId] = None,
  errors: List[ShapeId] = Nil
) extends Shape

/**
 * A resource shape representing a RESTful resource with lifecycle operations.
 *
 * @param name
 *   the shape name
 * @param traits
 *   the traits applied to this shape
 * @param identifiers
 *   the resource identifiers mapping names to their target ShapeIds
 * @param create
 *   the optional ShapeId of the create lifecycle operation
 * @param read
 *   the optional ShapeId of the read lifecycle operation
 * @param update
 *   the optional ShapeId of the update lifecycle operation
 * @param delete
 *   the optional ShapeId of the delete lifecycle operation
 * @param list
 *   the optional ShapeId of the list lifecycle operation
 * @param operations
 *   the ShapeIds of non-lifecycle operations bound to this resource
 * @param collectionOperations
 *   the ShapeIds of collection-level operations
 * @param resources
 *   the ShapeIds of child resources
 */
final case class ResourceShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  identifiers: Map[String, ShapeId] = Map.empty,
  create: Option[ShapeId] = None,
  read: Option[ShapeId] = None,
  update: Option[ShapeId] = None,
  delete: Option[ShapeId] = None,
  list: Option[ShapeId] = None,
  operations: List[ShapeId] = Nil,
  collectionOperations: List[ShapeId] = Nil,
  resources: List[ShapeId] = Nil
) extends Shape

// ---------------------------------------------------------------------------
// Supporting types
// ---------------------------------------------------------------------------

/**
 * Defines a member within a shape (structure field, list element, map
 * key/value, union variant).
 *
 * @param name
 *   the member name
 * @param target
 *   the ShapeId of the target type
 * @param traits
 *   the traits applied to this member
 */
final case class MemberDefinition(
  name: String,
  target: ShapeId,
  traits: List[TraitApplication] = Nil
)

/**
 * Defines a member of a string enum shape.
 *
 * @param name
 *   the enum member name
 * @param value
 *   the optional string value (defaults to the member name if absent)
 * @param traits
 *   the traits applied to this enum member
 */
final case class EnumMember(
  name: String,
  value: Option[String] = None,
  traits: List[TraitApplication] = Nil
)

/**
 * Defines a member of an integer enum shape.
 *
 * @param name
 *   the enum member name
 * @param value
 *   the integer value for this member
 * @param traits
 *   the traits applied to this enum member
 */
final case class IntEnumMember(
  name: String,
  value: Int,
  traits: List[TraitApplication] = Nil
)
