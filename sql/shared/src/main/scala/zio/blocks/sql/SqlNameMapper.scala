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
 * Turns a Scala field name into a column name. [[DbCodecDeriver]] and
 * `TableMetadata.columnsFor` apply it to every field without
 * `@Modifier.rename`; the default is [[SqlNameMapper.SnakeCase]].
 *
 * {{{
 * val snake    = SqlNameMapper.SnakeCase("unitPriceCents")       // unit_price_cents
 * val acronym  = SqlNameMapper.SnakeCase("productSKU")           // product_sku
 * val custom   = SqlNameMapper.Custom("col_" + _)("unitPriceCents") // col_unitPriceCents
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/sql-name-mapper/ SqlNameMapper]]
 */
sealed trait SqlNameMapper extends (String => String)

/** The predefined [[SqlNameMapper]]s. */
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
   * Converts a name to snake case: an underscore before each word that starts
   * with a capital, `-` replaced by `_`, and everything in lower case.
   * `firstName` becomes `first_name`, `userID` becomes `user_id`, and
   * `HTTPServer` becomes `http_server`.
   *
   * A capital that follows `_` or `-` and starts a lower-case word gets a
   * second underscore, so `order_Line` becomes `order__line`, a known bug in
   * 0.0.56.
   */
  case object SnakeCase extends SqlNameMapper {
    override def apply(fieldName: String): String = enforceSnakeCase(fieldName)
  }

  /** Returns the name unchanged. */
  case object Identity extends SqlNameMapper {
    override def apply(fieldName: String): String = fieldName
  }

  /** Applies `f` to the name. */
  final case class Custom(f: String => String) extends SqlNameMapper {
    override def apply(fieldName: String): String = f(fieldName)
  }
}
