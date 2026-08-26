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

import scala.quoted.*
import zio.blocks.sql.SqlNameMapper

/**
 * Scala 3 macros for extracting field names from selector expressions and
 * mapping them to SQL snake_case column names.
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
