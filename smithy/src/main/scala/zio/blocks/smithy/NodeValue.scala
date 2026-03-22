/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  final case class String(value: scala.Predef.String) extends NodeValue

  /**
   * A numeric value, supporting arbitrary precision.
   *
   * @param value
   *   the number represented as BigDecimal
   */
  final case class Number(value: BigDecimal) extends NodeValue

  /**
   * A boolean value (true or false).
   *
   * @param value
   *   the boolean value
   */
  final case class Boolean(value: scala.Boolean) extends NodeValue

  /**
   * The null value, a singleton instance.
   */
  case object Null extends NodeValue

  /**
   * An array value, containing a list of NodeValues.
   *
   * @param values
   *   the list of values in the array
   */
  final case class Array(values: List[NodeValue]) extends NodeValue

  /**
   * An object value, containing a list of key-value pairs.
   *
   * Each field is a tuple of (String, NodeValue).
   *
   * @param fields
   *   the list of (key, value) pairs in the object
   */
  final case class Object(fields: List[(scala.Predef.String, NodeValue)]) extends NodeValue
}
