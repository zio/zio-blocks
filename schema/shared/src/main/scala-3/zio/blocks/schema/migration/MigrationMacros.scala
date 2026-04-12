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

import scala.annotation.tailrec
import scala.quoted._
import zio.blocks.schema.DynamicOptic

/**
 * Scala 3 macros for the migration DSL.
 *
 * The selector macro converts a selector lambda like `_.foo.bar` into a
 * [[zio.blocks.schema.DynamicOptic]] at compile time.
 */
private[migration] object MigrationMacros {
  import zio.blocks.schema.CommonMacroOps._

  def selectorToDynamicOptic[S: Type, A: Type](
    selector: Expr[S => A]
  )(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect._

    @tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => fail(s"Expected a lambda expression, got '${term.show}'")
    }

    def hasName(term: Term, name: String): Boolean = term match {
      case Ident(s)     => name == s
      case Select(_, s) => name == s
      case _            => false
    }

    def decodeCaseName(typeTree: TypeTree): String =
      typeTree.tpe.dealias.typeSymbol.name

    def toNode(term: Term): Option[Expr[DynamicOptic]] = term match {
      case Inlined(_, _, inner) =>
        toNode(inner)

      // Root of the selector lambda
      case Ident(_) =>
        None

      // _.each
      case Apply(TypeApply(eachTerm, _), List(parent)) if hasName(eachTerm, "each") =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.each })

      // _.when[Case]
      case TypeApply(Apply(TypeApply(whenTerm, _), List(parent)), List(caseTypeTree)) if hasName(whenTerm, "when") =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        val caseName   = decodeCaseName(caseTypeTree)
        Some('{ $parentExpr.caseOf(${ Expr(caseName) }) })

      // structural types: _.fieldName desugars to selectDynamic("fieldName")
      case Apply(Select(recv, "selectDynamic"), List(Literal(StringConstant(fieldName)))) =>
        val parentExpr = toNode(recv).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.field(${ Expr(fieldName) }) })

      // _.fieldName
      case Select(parent, fieldName) =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.field(${ Expr(fieldName) }) })

      // Ignore reflectiveSelectable wrappers used by structural selections
      case Apply(Select(_, "reflectiveSelectable"), List(parent)) =>
        toNode(parent)

      // Ignore casts introduced by the compiler in some structural scenarios
      case TypeApply(Select(parent, "$asInstanceOf$"), _) =>
        toNode(parent)

      case other =>
        fail(
          s"Unsupported selector element. Expected: .<field>, .each, .when[<T>], or structural selectDynamic; got '${other.show}'"
        )
    }

    val body      = toPathBody(selector.asTerm)
    val opticExpr = toNode(body).getOrElse('{ DynamicOptic.root })
    opticExpr
  }
}

