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

package zio.blocks.sql.query

import scala.quoted.*

import zio.blocks.sql.{SqlNameMapper, Table}

private[query] object RelMacros {

  def manyToOneImpl[From: Type, To: Type](
    fromTable: Expr[Table[From]],
    fkSelector: Expr[From => Any],
    toTable: Expr[Table[To]],
    pkSelector: Expr[To => Any]
  )(using Quotes): Expr[Rel[From, To]] = {
    val fkCol = extractColumn[From](fkSelector)
    val pkCol = extractColumn[To](pkSelector)
    '{ Rel[From, To]($fromTable, ${ Expr(fkCol) }, $toTable, ${ Expr(pkCol) }) }
  }

  def oneToManyImpl[From: Type, To: Type](
    fromTable: Expr[Table[From]],
    fkSelector: Expr[From => Any],
    toTable: Expr[Table[To]],
    pkSelector: Expr[To => Any]
  )(using Quotes): Expr[Rel[From, To]] = {
    val fkCol = extractColumn[From](fkSelector)
    val pkCol = extractColumn[To](pkSelector)
    '{ Rel[From, To]($fromTable, ${ Expr(fkCol) }, $toTable, ${ Expr(pkCol) }) }
  }

  private def extractColumn[T: Type](selector: Expr[T => Any])(using Quotes): String = {
    import quotes.reflect.*
    val fieldName = extractFieldName(selector)
    val tpeRepr   = TypeRepr.of[T]
    val sym       = tpeRepr.typeSymbol
    // caseFields empty for non-case classes; fallback to fieldMembers
    val validSymbols = {
      val cf = sym.caseFields
      if (cf.nonEmpty) cf
      else sym.fieldMembers.filter(s => s.isValDef || s.flags.is(quotes.reflect.Flags.ParamAccessor))
    }
    val validNames = validSymbols.map(_.name)
    if (!validNames.contains(fieldName)) {
      // Also allow if field is part of nested? Single check enough
      report.errorAndAbort(
        s"unknown field '$fieldName' for type ${Type.show[T]}. Valid fields: ${validNames.mkString(", ")}"
      )
    }
    SqlNameMapper.SnakeCase(fieldName)
  }

  private def extractFieldName[T: Type](selector: Expr[T => Any])(using Quotes): String = {
    import quotes.reflect.*
    val term = selector.asTerm

    // Recursively collect first Select(Ident(param), field) name
    def findInTerm(t: Term): Option[String] = t match {
      case Inlined(_, _, inner) => findInTerm(inner)
      case Lambda(_, body)      => findInTerm(body)
      case Block(stats, expr)   =>
        // Block may contain DefDef for lambda; look in expr then stats
        findInTerm(expr).orElse(
          stats.collectFirst { case d: DefDef => d.rhs.flatMap(findInTerm).getOrElse("") }.filter(_.nonEmpty)
        )
      case DefDef(_, _, _, Some(rhs)) => findInTerm(rhs)
      case Apply(fun, _)              => findInTerm(fun)
      case TypeApply(fun, _)          => findInTerm(fun)
      case Select(qual, name)         =>
        qual match {
          case Ident(_) => Some(name)
          case _        =>
            // For chained selects like _.foo.bar -> outermost Select qual is inner Select
            // Find inner field first
            findInTerm(qual).orElse(Some(name))
        }
      case Typed(inner, _) => findInTerm(inner)
      case _               => None
    }

    // More robust: traverse full tree and find Select with Ident qualifier
    def traverse(t: Tree): Option[String] = {
      var result: Option[String] = None
      object traverser extends TreeTraverser {
        override def traverseTree(tree: Tree)(owner: Symbol): Unit =
          if (result.isEmpty) {
            tree match {
              case Select(Ident(_), name) if name != "apply" && !name.startsWith("$") =>
                result = Some(name)
              case _ =>
            }
            super.traverseTree(tree)(owner)
          }
      }
      traverser.traverseTree(t)(Symbol.spliceOwner)
      result
    }

    // Prefer direct find, fallback to traverser for closures
    findInTerm(term).orElse(traverse(term)) match {
      case Some(name) => name
      case None       =>
        report.errorAndAbort(
          s"selector must be a simple field access like _.field or x => x.field, got: ${term
              .show(using Printer.TreeStructure)}"
        )
    }
  }
}
