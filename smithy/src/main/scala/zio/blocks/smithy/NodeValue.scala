package zio.blocks.smithy

/**
 * Represents a JSON-like value that can appear in a Smithy node.
 *
 * NodeValue is a sealed trait that models all possible value types found in
 * Smithy node structures: primitives (string, number, boolean, null) and
 * collections (arrays and objects).
 */
sealed trait NodeValue

object NodeValue {

  /**
   * A string value.
   *
   * @param value
   *   the string content
   */
  final case class StringValue(value: String) extends NodeValue

  /**
   * A numeric value, supporting arbitrary precision.
   *
   * @param value
   *   the number represented as BigDecimal
   */
  final case class NumberValue(value: BigDecimal) extends NodeValue

  /**
   * A boolean value (true or false).
   *
   * @param value
   *   the boolean value
   */
  final case class BooleanValue(value: Boolean) extends NodeValue

  /**
   * The null value, a singleton instance.
   */
  case object NullValue extends NodeValue

  /**
   * An array value, containing a list of NodeValues.
   *
   * @param values
   *   the list of values in the array
   */
  final case class ArrayValue(values: List[NodeValue]) extends NodeValue

  /**
   * An object value, containing a list of key-value pairs.
   *
   * Each field is a tuple of (String, NodeValue).
   *
   * @param fields
   *   the list of (key, value) pairs in the object
   */
  final case class ObjectValue(fields: List[(String, NodeValue)]) extends NodeValue
}
