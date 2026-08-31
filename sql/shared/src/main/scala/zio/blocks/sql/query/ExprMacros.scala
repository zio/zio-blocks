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

  /** Factory for the sealed [[Column]] node, callable from macro quotes (inlined trees). */
  private[query] def newColumn[A, B, Sc](
    table: Table[A],
    column: String,
    alias: Option[String],
    dynamic: DynamicSchemaExpr
  ): Column[A, B, Sc] =
    new Column[A, B, Sc](table, column, alias, dynamic)

  /** Factory for the sealed [[OptExpr]] node, callable from macro quotes (inlined trees). */
  private[query] def newOptExpr[A, Sc](inner: zio.blocks.sql.query.Expr[A, Sc]): OptExpr[A, Sc] =
    new OptExpr[A, Sc](inner)

  /**
   * Query-bound column builder. `Sc` is the receiver query's path-dependent
   * scope (`q.Scope`); the result carries that exact scope as
   * `Expr[B, Sc]` (or `Expr[Option[B], Sc]` when the column's table slot is a
   * LEFT JOIN), with `B` inferred from the selector lambda's result type.
   * Table resolution is performed against the query's slot tuple `(A *: S)`
   * before any column matching:
   *
   *   - a table that is not present in the query fails at compile time;
   *   - a table that appears more than once (self-joins) requires `colAt`;
   *   - left-joined slots yield `Option`, source/inner slots stay non-optional.
   */
  def queryColImpl[A: Type, S: Type, T: Type, Sc: Type, B: Type](
    selector: scala.quoted.Expr[T => B]
  )(using Quotes): scala.quoted.Expr[zio.blocks.sql.query.Expr[?, ?]] = {
    import quotes.reflect.*

    val path          = extractFieldPath[T](selector)
    val columnName    = validatePathAndGetColumn[T](path)
    val schemaExprOpt = scala.quoted.Expr.summon[zio.blocks.schema.Schema[T]]
    val schemaExpr    = schemaExprOpt.getOrElse(
      report.errorAndAbort(s"no implicit Schema[${TypeRepr.of[T].show}] found for col")
    )
    val base: scala.quoted.Expr[Column[T, B, Sc]] = '{
      val table: Table[T] = Table.derived[T](using $schemaExpr)
      val nodes           = Vector(${ Varargs(path.map(p => '{ new DynamicOptic.Node.Field(${ Expr(p) }) })) }: _*)
      val dynPath         = new DynamicOptic(nodes)
      val dynamic         = DynamicSchemaExpr.Select(dynPath)
      ExprMacros.newColumn[T, B, Sc](table, ${ Expr(columnName) }, None, dynamic)
    }
    slotInfo[A, S, T] match {
      case None =>
        report.errorAndAbort(
          s"table '${TypeRepr.of[T].show}' is not part of this query; available table slots: ${slotNames[A, S]
              .mkString(", ")} — build columns from a query that contains the table"
        )
      case Some((_, _, true)) =>
        report.errorAndAbort(
          s"table '${TypeRepr.of[T].show}' appears more than once in this query; use q.colAt(alias, ...) with an explicit alias (t0, t1, ...) to disambiguate"
        )
      case Some((_, nullable, false)) =>
        if (nullable) '{ ExprMacros.newOptExpr[B, Sc]($base) }
        else base
    }
  }

  /**
   * Query-bound column builder with an explicit alias. A literal `tN` alias is
   * resolved positionally against the slot tuple: slot 0 is the source, slot
   * `N-1` is the `N`-th join. The slot must exist and its (unwrapped) table
   * type must match the selector's table; nullability is derived from the slot
   * (LEFT JOIN slots are `Option`). A non-literal alias falls back to the
   * unaliased resolution rules (presence + ambiguity) and is resolved against
   * the render-time alias map.
   */
  def queryColAtImpl[A: Type, S: Type, T: Type, Sc: Type, B: Type](
    alias: scala.quoted.Expr[String],
    selector: scala.quoted.Expr[T => B]
  )(using Quotes): scala.quoted.Expr[zio.blocks.sql.query.Expr[?, ?]] = {
    import quotes.reflect.*

    val path          = extractFieldPath[T](selector)
    val columnName    = validatePathAndGetColumn[T](path)
    val schemaExprOpt = scala.quoted.Expr.summon[zio.blocks.schema.Schema[T]]
    val schemaExpr    = schemaExprOpt.getOrElse(
      report.errorAndAbort(s"no implicit Schema[${TypeRepr.of[T].show}] found for colAt")
    )
    val base: scala.quoted.Expr[Column[T, B, Sc]] = '{
      val table: Table[T] = Table.derived[T](using $schemaExpr)
      val nodes           = Vector(${ Varargs(path.map(p => '{ new DynamicOptic.Node.Field(${ Expr(p) }) })) }: _*)
      val dynPath         = new DynamicOptic(nodes)
      val dynamic         = DynamicSchemaExpr.Select(dynPath)
      ExprMacros.newColumn[T, B, Sc](table, ${ Expr(columnName) }, Some($alias), dynamic)
    }
    extractAliasLiteral(alias) match {
      case Some(aliasStr) =>
        aliasSlotInfo[A, S, T](aliasStr) match {
          case None =>
            report.errorAndAbort(
              s"alias '$aliasStr' does not resolve to a table slot of this query (available: t0..t${slotCount[A, S] - 1}); selectors must name a slot whose table matches '${TypeRepr.of[T].show}'"
            )
          case Some(slotNullable) =>
            if (slotNullable) '{ ExprMacros.newOptExpr[B, Sc]($base) }
            else base
        }
      case None =>
        // Runtime alias: enforce presence/ambiguity now, let the renderer resolve the alias.
        slotInfo[A, S, T] match {
          case None =>
            report.errorAndAbort(
              s"table '${TypeRepr.of[T].show}' is not part of this query; available table slots: ${slotNames[A, S]
                  .mkString(", ")} — build columns from a query that contains the table"
            )
          case Some((_, _, true)) =>
            report.errorAndAbort(
              s"table '${TypeRepr.of[T].show}' appears more than once in this query; use q.colAt(alias, ...) with an explicit literal alias (t0, t1, ...) to disambiguate"
            )
          case Some((_, nullable, false)) =>
            if (nullable) '{ ExprMacros.newOptExpr[B, Sc]($base) }
            else base
        }
    }
  }

  /**
   * Slot model: the query's tables are `source :: joins`, i.e. `A *: S`.
   * Returns `(slotIndex, nullable, duplicated)` for `T`, where `nullable`
   * means the slot is `Option`-wrapped (LEFT JOIN) and `duplicated` means `T`
   * occupies more than one slot (self-join — requires `colAt`).
   */
  private def slotInfo[A: Type, S: Type, T: Type](using
    quotes: Quotes
  ): Option[(Int, Boolean, Boolean)] = {
    import quotes.reflect.*
    val tRepr = TypeRepr.of[T].dealias.simplified
    val slots = slotList[A, S]
    val hits  = slots.zipWithIndex.collect {
      case (slot, idx) if unwrapOption(slot).dealias.simplified =:= tRepr => (idx, isOptionSlot(slot))
    }
    hits match {
      case Nil           => None
      case single :: Nil => Some((single._1, single._2, false))
      case many          => Some((many.head._1, many.head._2, true))
    }
  }

  /** Resolve a literal `tN` alias to a slot's nullability, verifying the slot's table matches `T`. */
  private def aliasSlotInfo[A: Type, S: Type, T: Type](alias: String)(using
    quotes: Quotes
  ): Option[Boolean] = {
    import quotes.reflect.*
    if (!alias.startsWith("t")) return None
    val idxOpt = scala.util.Try(alias.drop(1).toInt).toOption
    idxOpt match {
      case None => None
      case Some(n) =>
        val slots = slotList[A, S]
        val tRepr = TypeRepr.of[T].dealias.simplified
        if (n < 0 || n >= slots.size) None
        else {
          val slot = slots(n)
          if (unwrapOption(slot).dealias.simplified =:= tRepr) Some(isOptionSlot(slot))
          else None
        }
    }
  }

  private def slotList[A: Type, S: Type](using quotes: Quotes): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    TypeRepr.of[A] :: tupleElems(TypeRepr.of[S])
  }

  private def slotCount[A: Type, S: Type](using quotes: Quotes): Int = slotList[A, S].size

  private def slotNames[A: Type, S: Type](using quotes: Quotes): List[String] =
    slotList[A, S].zipWithIndex.map { case (t, i) => s"t$i (${t.dealias.simplified.show})" }

  private def tupleElems(using quotes: Quotes)(tr: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    if (tr =:= TypeRepr.of[EmptyTuple]) Nil
    else
      tr match {
        case AppliedType(_, List(head, tail)) if tr.typeSymbol.name == "*:" =>
          head :: tupleElems(tail)
        case AppliedType(_, args) if tr.typeSymbol.fullName.startsWith("scala.Tuple") =>
          args
        case _ => List(tr)
      }
  }

  private def isOptionSlot(using quotes: Quotes)(tr: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tr.dealias.simplified match {
      case AppliedType(tycon, _) => tycon.typeSymbol.fullName == "scala.Option"
      case _                     => false
    }
  }

  private def unwrapOption(using quotes: Quotes)(tr: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    tr.dealias.simplified match {
      case AppliedType(tycon, List(inner)) if tycon.typeSymbol.fullName == "scala.Option" => inner
      case other                                                                          => other
    }
  }

  private def extractAliasLiteral(expr: scala.quoted.Expr[String])(using Quotes): Option[String] = {
    import quotes.reflect.*
    def loop(term: Term): Option[String] = term match {
      case Literal(StringConstant(s)) => Some(s)
      case Inlined(_, _, inner)       => loop(inner)
      case _                          => None
    }
    loop(expr.asTerm)
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