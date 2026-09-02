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

private[sql] object SqlValidator {

  def validate(parts: Seq[String]): Option[String] = {
    if (parts.length == 1 && parts.head.trim.isEmpty)
      return Some("sql interpolator requires a non-empty SQL string")

    val combined = parts.mkString(" ")

    val quoteError = checkQuotes(combined)
    if (quoteError.isDefined) return quoteError

    checkParentheses(combined)
  }

  private def checkQuotes(sql: String): Option[String] = {
    var i = 0
    while (i < sql.length) {
      val ch = sql.charAt(i)
      if (ch == '\'' || ch == '"') {
        val end = findClosingQuote(sql, i, ch)
        if (end < 0) {
          val name = if (ch == '\'') "single" else "double"
          return Some(s"Unclosed $name quote in SQL template")
        }
        i = end
      } else {
        i += 1
      }
    }
    None
  }

  private def findClosingQuote(sql: String, start: Int, quote: Char): Int = {
    var i = start + 1
    while (i < sql.length) {
      if (sql.charAt(i) == quote) {
        if (i + 1 < sql.length && sql.charAt(i + 1) == quote) {
          i += 2
        } else {
          return i + 1
        }
      } else {
        i += 1
      }
    }
    -1
  }

  private def checkParentheses(sql: String): Option[String] = {
    var depth = 0
    var i     = 0
    while (i < sql.length) {
      val ch = sql.charAt(i)
      if (ch == '\'' || ch == '"') {
        val end = findClosingQuote(sql, i, ch)
        if (end < 0) return None
        i = end
      } else {
        if (ch == '(') depth += 1
        else if (ch == ')') {
          depth -= 1
          if (depth < 0) return Some("Unbalanced parentheses in SQL: unexpected closing ')'")
        }
        i += 1
      }
    }
    if (depth > 0) Some("Unbalanced parentheses in SQL: unclosed '('")
    else None
  }
}
