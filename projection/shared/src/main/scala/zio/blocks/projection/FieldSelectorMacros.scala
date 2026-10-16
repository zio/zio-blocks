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

package zio.blocks.projection

// $COVERAGE-OFF$
import scala.quoted.*
import zio.blocks.sql.SqlNameMapper

/**
 * Scala 3 macro implementations that turn a field selector such as
 * `_.userCount` into the name of the field, checked at compile time.
 *
 * They back the selector-based methods of [[FieldUpdate]] (`increment`,
 * `decrement`, `setValue`, `maxValue`, `minValue`) and the
 * `HandlerBuilder.update` macro, so that a misspelled field is a compile error
 * and not a runtime failure. The selector must be a lambda that reads a field,
 * such as `_.userCount`. Only the last field name of the selector is used, so
 * `_.stats.userCount` gives `userCount`. A selector that is not a lambda, or
 * that reads no field, such as `x => x`, is rejected with a compile error.
 *
 * Field names are mapped to SQL `snake_case` column names with
 * `SqlNameMapper.SnakeCase`, for example `userCount` becomes `user_count`. The
 * name is worked out at compile time and appears as a string literal in the
 * expanded code, so no reflection happens at runtime.
 */
object FieldSelectorMacros {

  def extractAndMapFieldImpl[A: Type](selector: Expr[A => Any])(using q: Quotes): Expr[String] = {
    val fieldName  = extractFieldNameRaw(selector)
    val snakeCased = SqlNameMapper.SnakeCase(fieldName)
    Expr(snakeCased)
  }

  def extractFieldNameImpl[A: Type](selector: Expr[A => Any])(using q: Quotes): Expr[String] = {
    val fieldName = extractFieldNameRaw(selector)
    Expr(fieldName)
  }

  def setValueImpl[A: Type](selector: Expr[A => Any], value: Expr[Any])(using q: Quotes): Expr[FieldUpdate.Set] =
    '{ FieldUpdate.Set(${ extractAndMapFieldImpl[A](selector) }, $value) }

  def incrementImpl[A: Type](selector: Expr[A => Any], by: Expr[Long])(using q: Quotes): Expr[FieldUpdate.Increment] =
    '{ FieldUpdate.Increment(${ extractAndMapFieldImpl[A](selector) }, $by) }

  def decrementImpl[A: Type](selector: Expr[A => Any], by: Expr[Long])(using q: Quotes): Expr[FieldUpdate.Decrement] =
    '{ FieldUpdate.Decrement(${ extractAndMapFieldImpl[A](selector) }, $by) }

  def maxValueImpl[A: Type](selector: Expr[A => Any], value: Expr[Long])(using q: Quotes): Expr[FieldUpdate.Max] =
    '{ FieldUpdate.Max(${ extractAndMapFieldImpl[A](selector) }, $value) }

  def minValueImpl[A: Type](selector: Expr[A => Any], value: Expr[Long])(using q: Quotes): Expr[FieldUpdate.Min] =
    '{ FieldUpdate.Min(${ extractAndMapFieldImpl[A](selector) }, $value) }

  private def extractFieldNameRaw[A: Type](selector: Expr[A => Any])(using q: Quotes): String = {
    import q.reflect.*

    @scala.annotation.tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractLastFieldName(term: Term): String = term match {
      case Select(_, fieldName) => fieldName
      case Ident(_)             => report.errorAndAbort("Selector must access at least one field; use _.fieldName")
      case _                    => report.errorAndAbort(s"Cannot extract field name from: ${term.show}")
    }

    val pathBody  = toPathBody(selector.asTerm)
    val fieldName = extractLastFieldName(pathBody)
    fieldName
  }
}
// $COVERAGE-ON$
