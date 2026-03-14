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

package zio.blocks.sql

import java.lang.Character._
import java.lang

/**
 * A sealed trait that represents a strategy for mapping column names. Classes
 * or objects that extend `SqlNameMapper` provide an implementation of the
 * `apply` method, which specifies how a field name is transformed to a column
 * name.
 */
sealed trait SqlNameMapper extends (String => String)

/**
 * The `SqlNameMapper` object provides predefined strategies for transforming
 * field names into SQL column naming conventions.
 *
 * This object defines several `SqlNameMapper` implementations:
 *
 *   - `SnakeCase`: Transforms strings to snake_case (default for SQL). For
 *     example, "firstName" → "first_name", "userID" → "user_id".
 *   - `Identity`: Returns the input string as-is, performing no transformation.
 *   - `Custom`: Allows for user-defined transformations by applying a given
 *     function to the input column name.
 */
object SqlNameMapper {

  private[this] def enforceSnakeCase(s: String): String = {
    val len                      = s.length
    val sb                       = new lang.StringBuilder(len << 1)
    var i                        = 0
    var isPrecedingNotUpperCased = false
    while (i < len) isPrecedingNotUpperCased = {
      val ch = s.charAt(i)
      i += 1
      if (ch == '_' || ch == '-') {
        sb.append('_')
        false
      } else if (!isUpperCase(ch)) {
        sb.append(ch)
        true
      } else {
        if (isPrecedingNotUpperCased || i > 1 && i < len && !isUpperCase(s.charAt(i)))
          sb.append('_')
        sb.append(toLowerCase(ch))
        false
      }
    }
    sb.toString
  }

  /**
   * A predefined implementation of the [[SqlNameMapper]] trait that converts a
   * given field name into snake_case format by replacing transitions between
   * uppercase and lowercase letters with underscores (`_`) and converting all
   * characters to lowercase.
   *
   * For example, "firstName" is transformed into "first_name", and "userID" is
   * transformed into "user_id".
   */
  case object SnakeCase extends SqlNameMapper {
    override def apply(fieldName: String): String = enforceSnakeCase(fieldName)
  }

  /**
   * An implementation of the `SqlNameMapper` trait that performs no
   * transformation on the provided field name. The identity operation is
   * applied, where the input string is returned unchanged.
   */
  case object Identity extends SqlNameMapper {
    override def apply(fieldName: String): String = fieldName
  }

  /**
   * A case class that provides a custom implementation of the `SqlNameMapper`
   * trait.
   *
   * The `Custom` class allows for the transformation of field names using a
   * user-defined function. This transformation logic is encapsulated in the
   * function `f` provided at instantiation.
   *
   * @param f
   *   A function that defines how to transform a field name. The function takes
   *   a string as input and returns the transformed column name as output.
   */
  final case class Custom(f: String => String) extends SqlNameMapper {
    override def apply(fieldName: String): String = f(fieldName)
  }
}
