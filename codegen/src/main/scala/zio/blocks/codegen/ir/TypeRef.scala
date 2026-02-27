package zio.blocks.codegen.ir

/**
 * Represents a Scala type reference in the IR, supporting generic types with
 * optional and nullable variants.
 *
 * @param name
 *   The name of the type (e.g., "String", "List", "Option")
 * @param typeArgs
 *   Type arguments for generic types (defaults to empty list)
 * @param isOptional
 *   Whether this type is wrapped in Option (defaults to false)
 * @param isNullable
 *   Whether this type can be null (defaults to false)
 *
 * @example
 *   {{{
 * // Simple type
 * val stringType = TypeRef("String")
 *
 * // Generic type with arguments
 * val listOfInt = TypeRef("List", List(TypeRef("Int")))
 *
 * // Using factory methods
 * val optionalString = TypeRef.optional(TypeRef.String)
 * val mapStringInt = TypeRef.map(TypeRef.String, TypeRef.Int)
 *   }}}
 */
final case class TypeRef(
  name: String,
  typeArgs: List[TypeRef] = Nil,
  isOptional: Boolean = false,
  isNullable: Boolean = false
)

object TypeRef {

  // Primitive types
  val Unit: TypeRef       = TypeRef("Unit")
  val Boolean: TypeRef    = TypeRef("Boolean")
  val Byte: TypeRef       = TypeRef("Byte")
  val Short: TypeRef      = TypeRef("Short")
  val Int: TypeRef        = TypeRef("Int")
  val Long: TypeRef       = TypeRef("Long")
  val Float: TypeRef      = TypeRef("Float")
  val Double: TypeRef     = TypeRef("Double")
  val String: TypeRef     = TypeRef("String")
  val BigInt: TypeRef     = TypeRef("BigInt")
  val BigDecimal: TypeRef = TypeRef("BigDecimal")
  val Any: TypeRef        = TypeRef("Any")
  val Nothing: TypeRef    = TypeRef("Nothing")

  /**
   * Creates a TypeRef with the given name and type arguments.
   *
   * @param name
   *   The name of the type
   * @param args
   *   Type arguments (variable arguments)
   * @return
   *   A TypeRef with the given name and arguments
   *
   * @example
   *   {{{
   * val either = TypeRef.of("Either", TypeRef.String, TypeRef.Int)
   * val list = TypeRef.of("List", TypeRef.String)
   *   }}}
   */
  def of(name: String, args: TypeRef*): TypeRef =
    TypeRef(name, args.toList)

  /**
   * Wraps a TypeRef in Option, representing an optional type.
   *
   * @param typeRef
   *   The type to wrap
   * @return
   *   A TypeRef representing Option[typeRef]
   *
   * @example
   *   {{{
   * val optString = TypeRef.optional(TypeRef.String)
   * // Creates: Option[String]
   *   }}}
   */
  def optional(typeRef: TypeRef): TypeRef =
    TypeRef("Option", List(typeRef))

  /**
   * Wraps a TypeRef in List, representing a list of elements.
   *
   * @param typeRef
   *   The element type
   * @return
   *   A TypeRef representing List[typeRef]
   *
   * @example
   *   {{{
   * val listOfInt = TypeRef.list(TypeRef.Int)
   * // Creates: List[Int]
   *   }}}
   */
  def list(typeRef: TypeRef): TypeRef =
    TypeRef("List", List(typeRef))

  /**
   * Wraps a TypeRef in Set, representing a set of unique elements.
   *
   * @param typeRef
   *   The element type
   * @return
   *   A TypeRef representing Set[typeRef]
   *
   * @example
   *   {{{
   * val setOfStrings = TypeRef.set(TypeRef.String)
   * // Creates: Set[String]
   *   }}}
   */
  def set(typeRef: TypeRef): TypeRef =
    TypeRef("Set", List(typeRef))

  /**
   * Creates a TypeRef representing a Map with key and value types.
   *
   * @param key
   *   The key type
   * @param value
   *   The value type
   * @return
   *   A TypeRef representing Map[key, value]
   *
   * @example
   *   {{{
   * val mapStringInt = TypeRef.map(TypeRef.String, TypeRef.Int)
   * // Creates: Map[String, Int]
   *   }}}
   */
  def map(key: TypeRef, value: TypeRef): TypeRef =
    TypeRef("Map", List(key, value))

  /**
   * Wraps a TypeRef in Chunk, representing a high-performance immutable
   * sequence.
   *
   * @param typeRef
   *   The element type
   * @return
   *   A TypeRef representing Chunk[typeRef]
   *
   * @example
   *   {{{
   * val chunkOfLongs = TypeRef.chunk(TypeRef.Long)
   * // Creates: Chunk[Long]
   *   }}}
   */
  def chunk(typeRef: TypeRef): TypeRef =
    TypeRef("Chunk", List(typeRef))
}
