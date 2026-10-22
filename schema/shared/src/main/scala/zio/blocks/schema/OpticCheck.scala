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

package zio.blocks.schema

import java.lang
import scala.util.control.NoStackTrace

/**
 * The reason an optic could not reach its focus. The `*OrFail` methods of
 * [[Optic]] return it in a `Left`, `Optic#check` returns it, and so does
 * `SchemaExpr#eval` when evaluation fails.
 *
 * It holds one or more problems ([[OpticCheck.Single]]). Each one is an error
 * ([[OpticCheck.Error]]: another case of a variant, a failing wrapper, a failed
 * conversion) or a warning ([[OpticCheck.Warning]]: an empty collection, an
 * index past the end, a missing key). Each problem carries the path of the
 * whole optic and the path up to the step where it was found, rendered in its
 * message with `DynamicOptic#toScalaString`:
 *
 * `During attempted access at .tags.at(0), encountered a sequence out of bounds at .tags.at(0): index is 0, but size is 0`
 *
 * An `OpticCheck` is also an exception without a stack trace, whose message is
 * `message`.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
final case class OpticCheck(errors: ::[OpticCheck.Single]) extends Exception with NoStackTrace {

  /**
   * Returns a check with the problems of this one followed by those of `other`.
   */
  def ++(other: OpticCheck): OpticCheck = new OpticCheck(new ::(errors.head, errors.tail ++ other.errors))

  /**
   * Returns `true` if at least one problem is a warning.
   */
  def hasWarning: Boolean = errors.exists(_.isWarning)

  /**
   * Returns `true` if at least one problem is an error.
   */
  def hasError: Boolean = errors.exists(_.isError)

  /**
   * The messages of all problems, one per line.
   */
  def message: String = {
    val sb = new lang.StringBuilder
    errors.foreach { e =>
      if (sb.length > 0) sb.append('\n')
      sb.append(e.message)
    }
    sb.toString
  }

  /**
   * Returns `message`.
   */
  override def getMessage: String = message
}

/**
 * The problems that an [[OpticCheck]] can hold.
 */
object OpticCheck {

  /**
   * One problem found while following an optic: an [[OpticCheck.Error]] or an
   * [[OpticCheck.Warning]].
   */
  sealed trait Single {

    /**
     * The path of the whole optic that was being followed.
     */
    def full: DynamicOptic

    /**
     * The path from the start up to and including the step where the problem
     * was found.
     */
    def prefix: DynamicOptic

    /**
     * A sentence that describes the problem and names both paths.
     */
    def message: String

    /**
     * Returns `true` if this problem is an [[OpticCheck.Warning]].
     */
    def isWarning: Boolean = this match {
      case _: Warning => true
      case _          => false
    }

    /**
     * Returns `true` if this problem is an [[OpticCheck.Error]].
     */
    def isError: Boolean = !isWarning
  }

  /**
   * A problem that stops the optic: another case of a variant, a wrapper that
   * throws, or a failed conversion.
   */
  sealed trait Error extends Single

  /**
   * A problem caused by a missing part: an empty collection, an index past the
   * end, or a missing key.
   */
  sealed trait Warning extends Single

  /**
   * A prism step found another case of the variant. `expectedCase` and
   * `actualCase` are case names. `actualValue` is the value at that step; in a
   * check from `SchemaExpr#eval`, it is the whole input.
   */
  case class UnexpectedCase(
    expectedCase: String,
    actualCase: String,
    full: DynamicOptic,
    prefix: DynamicOptic,
    actualValue: Any
  ) extends Error {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered an unexpected case at ${prefix.toScalaString}: expected $expectedCase, but got $actualCase"
  }

  /**
   * A step over every element found an empty sequence. Traversals built with
   * `searchFor` also report it when they find nothing.
   */
  case class EmptySequence(full: DynamicOptic, prefix: DynamicOptic) extends Warning {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered an empty sequence at ${prefix.toScalaString}"
  }

  /**
   * An index step needed the element at `index`, but the sequence has only
   * `size` elements.
   */
  case class SequenceIndexOutOfBounds(full: DynamicOptic, prefix: DynamicOptic, index: Int, size: Int) extends Warning {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered a sequence out of bounds at ${prefix.toScalaString}: index is $index, but size is $size"
  }

  /**
   * A key step found a map without `key`. In a check from `SchemaExpr#eval`,
   * `key` is a `String` rendering of the key.
   */
  case class MissingKey(full: DynamicOptic, prefix: DynamicOptic, key: Any) extends Warning {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered missing key at ${prefix.toScalaString}"
  }

  /**
   * A step over every key or value found an empty map.
   */
  case class EmptyMap(full: DynamicOptic, prefix: DynamicOptic) extends Warning {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered an empty map at ${prefix.toScalaString}"
  }

  /**
   * A wrapper step's `wrap` or `unwrap` threw `error`. A `searchFor` traversal
   * also reports it when the modified value no longer decodes with the source
   * schema.
   */
  case class WrappingError(full: DynamicOptic, prefix: DynamicOptic, error: SchemaError) extends Error {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered an error at ${prefix.toScalaString}: ${error.message}"
  }

  /**
   * `SchemaExpr#eval` could not decode a result with the expected schema, or
   * failed for another reason that has no case of its own. `full` and `prefix`
   * are both the root path.
   */
  case class DynamicConversionError(error: String) extends Error {
    def full: DynamicOptic   = DynamicOptic.root
    def prefix: DynamicOptic = DynamicOptic.root
    def message: String      = s"Dynamic value conversion error: $error"
  }
}
