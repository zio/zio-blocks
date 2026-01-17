package zio.blocks.schema.json

/**
 * A minimal JSON Abstract Syntax Tree (AST) for `zio-blocks`.
 * 
 * Represents the six core JSON types defined by RFC 8259:
 * - Null
 * - Boolean
 * - Number (represented as BigDecimal for arbitrary precision)
 * - String
 * - Array
 * - Object
 * 
 * This AST is designed to work seamlessly with `JsonPatch` for efficient
 * diffing and patching operations, as well as conversion to/from `DynamicValue`.
 */
sealed trait Json

object Json {
  /** Represents JSON null. */
  case object Null extends Json
  
  /** Represents a JSON boolean value.
   * @param value the boolean value
   */
  final case class Bool(value: Boolean) extends Json
  
  /** Represents a JSON number using BigDecimal for arbitrary precision.
   * @param value the numeric value
   */
  final case class Num(value: BigDecimal) extends Json
  
  /** Represents a JSON string.
   * @param value the string value
   */
  final case class Str(value: String) extends Json
  
  /** Represents a JSON array.
   * @param elements the ordered sequence of JSON values
   */
  final case class Arr(elements: Vector[Json]) extends Json
  
  /** Represents a JSON object.
   * @param fields the ordered sequence of key-value pairs
   */
  final case class Obj(fields: Vector[(String, Json)]) extends Json
}
