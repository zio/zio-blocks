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

package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Macro that parses a selector function (e.g.
 * `(p: Person) => p.address.street`) into a pure-data [[DynamicOptic]] path at
 * compile time. No runtime reflection. Part of the algebraic migration system
 * (MigrationBuilder → DynamicMigration).
 */
object SelectorMacro {

  inline def extractPath[S, A](inline selector: S => A): DynamicOptic =
    ${ extractPathImpl('selector) }

  def extractPathImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def lambdaBody(term: Term): Term = term match {
      case Inlined(_, _, body) =>
        lambdaBody(body)
      case Block(List(DefDef(_, _, _, Some(body))), _) =>
        body
      case _ =>
        report.errorAndAbort(
          s"Expected a lambda expression, got: ${term.show}. Use a simple selector (e.g. (p: Person) => p.address.street)."
        )
    }

    def parseTerm(term: Term): List[String] = term match {
      // 1. Standard case class field access
      case Select(qualifier, name) =>
        parseTerm(qualifier) :+ name

      // 2. Structural type field access (Selectable): qualifier.selectDynamic("fieldName")
      case Apply(Select(qualifier, "selectDynamic"), List(Literal(StringConstant(fieldName)))) =>
        parseTerm(qualifier) :+ fieldName

      // 3. Lambda parameter base
      case Ident(_) =>
        Nil

      // 4. Inlined wrapper (often surrounds macros/lambdas)
      case Inlined(_, _, body) =>
        parseTerm(body)

      // 5. Lambda definition
      case Block(List(DefDef(_, _, _, Some(body))), _) =>
        parseTerm(body)

      // 6. Type ascription (e.g. (_.age: Int))
      case Typed(expr, _) =>
        parseTerm(expr)

      case _ =>
        report.errorAndAbort(s"Unsupported selector syntax: ${term.show}. Must be simple field access.")
    }

    val body     = lambdaBody(selector.asTerm)
    val pathList = parseTerm(body)

    if (pathList.isEmpty) {
      report.errorAndAbort("Selector path cannot be empty.")
    }

    def buildOptic(path: List[String]): Expr[DynamicOptic] = path match {
      case head :: Nil =>
        '{ DynamicOptic.Field(${ Expr(head) }, None) }
      case head :: tail =>
        '{ DynamicOptic.Field(${ Expr(head) }, Some(${ buildOptic(tail) })) }
      case Nil =>
        report.errorAndAbort("Unreachable")
    }

    buildOptic(pathList)
  }
}
