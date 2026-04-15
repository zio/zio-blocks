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

import scala.annotation.tailrec
import scala.quoted.*

object MigrationMacros {

  /**
   * Converts a selector lambda like `_.field` or `_.a.b` into a `DynamicOptic`
   * at compile time by walking the lambda body and collecting each field-select
   * step.
   */
  def selectorToOpticImpl[A: Type](path: Expr[A => Any])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    @tailrec
    def toLambdaBody(term: Term): Term = term match {
      case Inlined(_, _, inner)                        => toLambdaBody(inner)
      case Block(List(DefDef(_, _, _, Some(body))), _) => body
      case _                                           =>
        report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractFields(term: Term): List[String] = term match {
      case Select(parent, fieldName) => extractFields(parent) :+ fieldName
      case _: Ident                  => Nil
      case Inlined(_, _, inner)      => extractFields(inner)
      case _                         =>
        report.errorAndAbort(
          s"Migration selector must be a chain of field accesses (e.g. _.field or _.a.b), got '${term.show}'"
        )
    }

    val body   = toLambdaBody(path.asTerm)
    val fields = extractFields(body)
    fields.foldLeft('{ DynamicOptic.root }) { (acc, name) =>
      val nameExpr = Expr(name)
      '{ $acc.field($nameExpr) }
    }
  }
}
