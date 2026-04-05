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

package util

import scala.quoted.*

object ShowExprMacro {

  /**
   * Handles both call styles:
   *   - show(a, b, c) — comma-separated varargs
   *   - show { a; b; c } — single block argument, statements unpacked
   */
  def showImpl(exprs: Expr[Seq[Any]])(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    val pos      = Position.ofMacroExpansion
    val filePath = pos.sourceFile.path
    val line     = pos.startLine + 1

    def extractStmts(term: Term): List[Term] = term match {
      case Block(stmts, last) =>
        val termStmts = stmts.collect { case t: Term => t }
        // Only unpack if every stmt is a Term (user-written multi-expression block).
        // If there are ValDef/DefDef stmts the block is compiler-generated (desugared
        // expression with temp bindings) — treat it as a single unit so references
        // to the synthetic vals remain in scope.
        if (termStmts.size == stmts.size)
          termStmts.flatMap(extractStmts) ::: extractStmts(last)
        else
          List(term)
      case Inlined(_, _, inner) => extractStmts(inner)
      case other                => List(other)
    }

    // Use the Varargs extractor — the standard approach for inline vararg macros.
    // If there is exactly one argument and it is a block, unpack its statements
    // (block form: show { a; b; c }).  Otherwise treat each argument individually
    // (comma form: show(a, b, c)).
    val terms: List[Term] = exprs match {
      case Varargs(args) =>
        args.toList match {
          case List(single) => extractStmts(single.asTerm)
          case multiple     => multiple.map(_.asTerm)
        }
      case other => extractStmts(other.asTerm)
    }

    val printExprs: List[Expr[Unit]] = terms.map { term =>
      val src  = term.pos.sourceCode.getOrElse("<unknown>").trim
      val expr = term.asExprOf[Any]
      '{
        println(${ Expr(src) })
        val result = $expr
        result.toString.linesIterator.foreach(l => println(s"// $l"))
      }
    }

    val printAll = printExprs.foldLeft('{ () }: Expr[Unit])((acc, e) => '{ $acc; $e })

    '{
      util.SourceHelper.commentsAbove(${ Expr(filePath) }, ${ Expr(line) }).foreach(println)
      $printAll
      println()
    }
  }
}
