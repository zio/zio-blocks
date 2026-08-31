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

import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr}
import zio.blocks.sql.{SqlNameMapper, Table}

private[query] object ExprMacros {

  def colImpl[A: Type, B: Type](
    selector: scala.quoted.Expr[A => B]
  )(using Quotes): scala.quoted.Expr[zio.blocks.sql.query.Expr[B]] = {
    val path          = extractFieldPath[A](selector)
    val columnName    = validatePathAndGetColumn[A](path)
    val schemaExprOpt = scala.quoted.Expr.summon[zio.blocks.schema.Schema[A]]
    val schemaExpr    = schemaExprOpt.getOrElse(
      quotes.reflect.report.errorAndAbort(s"no implicit Schema[${quotes.reflect.TypeRepr.of[A].show}] found for col")
    )
    '{
      val table: Table[A] = Table.derived[A](using $schemaExpr)
      val nodes           = Vector(${ Varargs(path.map(p => '{ new DynamicOptic.Node.Field(${ Expr(p) }) })) }: _*)
      val dynPath         = new DynamicOptic(nodes)
      val dynamic         = DynamicSchemaExpr.Select(dynPath)
      new Column[A, B](table, ${ Expr(columnName) }, None, dynamic)
    }
  }

  def colAtImpl[A: Type, B: Type](
    alias: scala.quoted.Expr[String],
    selector: scala.quoted.Expr[A => B]
  )(using Quotes): scala.quoted.Expr[zio.blocks.sql.query.Expr[B]] = {
    val path          = extractFieldPath[A](selector)
    val columnName    = validatePathAndGetColumn[A](path)
    val schemaExprOpt = scala.quoted.Expr.summon[zio.blocks.schema.Schema[A]]
    val schemaExpr    = schemaExprOpt.getOrElse(
      quotes.reflect.report.errorAndAbort(s"no implicit Schema[${quotes.reflect.TypeRepr.of[A].show}] found for colAt")
    )
    '{
      val table: Table[A] = Table.derived[A](using $schemaExpr)
      val nodes           = Vector(${ Varargs(path.map(p => '{ new DynamicOptic.Node.Field(${ Expr(p) }) })) }: _*)
      val dynPath         = new DynamicOptic(nodes)
      val dynamic         = DynamicSchemaExpr.Select(dynPath)
      new Column[A, B](table, ${ Expr(columnName) }, Some($alias), dynamic)
    }
  }

  private def validatePathAndGetColumn[A: Type](path: List[String])(using Quotes): String = {
    import quotes.reflect.*
    var currentTpe  = TypeRepr.of[A]
    var resultParts = List.empty[String]
    for ((fieldName, idx) <- path.zipWithIndex) {
      val sym         = currentTpe.typeSymbol
      val fieldSymOpt = sym.caseFields
        .find(_.name == fieldName)
        .orElse(sym.fieldMembers.find(s => s.name == fieldName && (s.isValDef || s.flags.is(Flags.ParamAccessor))))
      val fieldSym = fieldSymOpt.getOrElse {
        val valid = sym.caseFields.map(_.name).mkString(", ")
        report.errorAndAbort(
          s"unknown field '$fieldName' for type ${Type.show[A]} at segment ${idx + 1} of ${path.mkString(".")}. Valid fields for ${currentTpe.show}: $valid"
        )
      }
      // Hardened rename extraction: try reflect-based extraction first, fallback to show parsing
      val renameOpt = {
        val fromReflect = fieldSym.annotations.collectFirst {
          case term if term.tpe <:< TypeRepr.of[zio.blocks.schema.Modifier.rename] =>
            var found: Option[String] = None
            object accu extends TreeAccumulator[Unit] {
              def foldTree(x: Unit, tree: Tree)(owner: Symbol): Unit = tree match {
                case Literal(StringConstant(s)) if found.isEmpty => found = Some(s)
                case _                                           => foldOverTree(x, tree)(owner)
              }
            }
            accu.foldTree((), term)(Symbol.spliceOwner)
            found
        }.flatten
        fromReflect.orElse {
          val show = fieldSym.annotations.map(_.show).mkString
          if (show.contains("rename")) {
            val regex = """"([^"]+)"""".r
            regex.findFirstMatchIn(show).map(_.group(1))
          } else None
        }
      }
      val colPart = renameOpt.getOrElse(SqlNameMapper.SnakeCase(fieldName))
      resultParts :+= colPart
      if (idx < path.size - 1) {
        currentTpe = currentTpe.memberType(fieldSym).widen.dealias
      }
    }
    resultParts.mkString("_")
  }

  private def extractFieldPath[A: Type](selector: scala.quoted.Expr[A => Any])(using Quotes): List[String] = {
    import quotes.reflect.*
    val term = selector.asTerm

    def findPath(t: Term): Option[List[String]] = t match {
      case Inlined(_, _, inner) => findPath(inner)
      case Lambda(_, body)      => findPath(body)
      case Block(stats, expr)   =>
        findPath(expr).orElse(stats.collectFirst { case d: DefDef =>
          d.rhs.flatMap(findPath).getOrElse(Nil)
        }.filter(_.nonEmpty))
      case DefDef(_, _, _, Some(rhs)) => findPath(rhs)
      case Apply(fun, _)              => findPath(fun)
      case TypeApply(fun, _)          => findPath(fun)
      case Select(qual, name)         =>
        def collect(s: Term, acc: List[String]): List[String] = s match {
          case Ident(_)         => acc :+ name
          case Select(q, n)     => collect(q, acc) :+ n
          case Inlined(_, _, i) => collect(i, acc)
          case Typed(inner, _)  => collect(inner, acc)
          case Block(_, expr)   => collect(expr, acc)
          case _                => acc :+ name
        }
        qual match {
          case Ident(_) => Some(List(name))
          case _        =>
            val prefix = findPath(qual)
            prefix.map(_ :+ name).orElse(Some(collect(qual, Nil) :+ name))
        }
      case Typed(inner, _) => findPath(inner)
      case _               => None
    }

    def traverse(t: Tree): Option[List[String]] = {
      var result: Option[List[String]] = None
      object traverser extends TreeTraverser {
        override def traverseTree(tree: Tree)(owner: Symbol): Unit =
          if (result.isEmpty) {
            tree match {
              case Select(Select(Ident(_), n1), n2) if n1 != "apply" && n2 != "apply" =>
                result = Some(List(n1, n2))
              case Select(Ident(_), name) if name != "apply" && !name.startsWith("$") =>
                result = Some(List(name))
              case _ =>
            }
            super.traverseTree(tree)(owner)
          }
      }
      traverser.traverseTree(t)(Symbol.spliceOwner)
      result
    }

    findPath(term).orElse(traverse(term)) match {
      case Some(path) if path.nonEmpty => path
      case _                           =>
        report.errorAndAbort(
          s"selector must be a simple field access like _.field or x => x.field, got: ${term
              .show(using Printer.TreeStructure)}"
        )
    }
  }
}
