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

import scala.quoted._

private[sql] object SqlMacros {

  def sqlImpl(sc: Expr[StringContext], tables: Expr[Seq[Table[?]]], args: Expr[Seq[Any]])(using
    Quotes
  ): Expr[Frag] = {
    import quotes.reflect._

    val partsOpt: Option[Seq[String]] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        Some(rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort })
      case _ => None
    }

    partsOpt match {
      case None =>
        report.errorAndAbort("sql interpolation requires a compile-time StringContext literal")
      case Some(ps) =>
        SqlValidator.validate(ps).foreach(report.errorAndAbort(_))
        tables match {
          case Varargs(tableExprs) if tableExprs.nonEmpty =>
            val (knownTables, knownColumns) = extractTables(tables)
            val diagnostics                 = SqlIdentifierChecker.validate(ps, knownTables, knownColumns)
            diagnostics.take(5).foreach(d => report.error(d.message))
          case _ => // no tables or dynamic tables — skip identifier checking
        }
    }

    buildFragExpr(partsOpt, args, sc)
  }

  def sqlImplChecked(
    sc: Expr[StringContext],
    first: Expr[Table[?]],
    rest: Expr[Seq[Table[?]]],
    args: Expr[Seq[Any]]
  )(using Quotes): Expr[Frag] = {
    import quotes.reflect._

    val partsOpt: Option[Seq[String]] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        Some(rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort })
      case _ => None
    }

    partsOpt match {
      case None =>
        report.errorAndAbort("sql interpolation requires a compile-time StringContext literal")
      case Some(ps) =>
        SqlValidator.validate(ps).foreach(report.errorAndAbort(_))
        val (knownTables, knownColumns) = extractTablesChecked(first, rest)
        val diagnostics                 = SqlIdentifierChecker.validate(ps, knownTables, knownColumns)
        diagnostics.take(5).foreach(d => report.error(d.message))
    }

    buildFragExpr(partsOpt, args, sc)
  }

  // ---- shared table extraction ----

  // Limitation: Table.name derived from @Modifier.config("sql.table_name") is only reflected in the
  // checker when the Table is constructed with an explicit name literal (e.g. Table.derived[User]("my_table")
  // or Table("my_table", ...)). When the table is built via `Table.derived[User]` with no literal, the
  // macro falls back to SnakeCase(typeName) which may mismatch the runtime name if a config annotation
  // drives it. Prefer explicit names for checked queries when using custom table names; see guide Limits.
  @scala.annotation.nowarn("msg=unused")
  private def addTableMeta(
    tExpr: Expr[Table[?]],
    names: scala.collection.mutable.Set[String],
    cols: scala.collection.mutable.Set[String]
  )(using
    Quotes
  ): Unit = {
    import quotes.reflect._
    tExpr match {
      case '{ $tbl: Table[t] } =>
        val tpe      = TypeRepr.of[t]
        val sym      = tpe.typeSymbol
        val typeName = {
          val n = sym.name
          if (n == "<none>" || n.isEmpty) "" else n
        }
        // Keep derived name separately so it can be removed if an explicit literal is present (#23)
        val derivedSnakeOpt: Option[String] =
          if (typeName.nonEmpty) {
            val snake = SqlNameMapper.SnakeCase(typeName)
            names += snake
            Some(snake)
          } else None
        val fields =
          try sym.caseFields
          catch { case _: Exception => Nil }
        if (fields.nonEmpty) {
          fields.foreach { f =>
            cols += SqlNameMapper.SnakeCase(f.name)
          }
        } else {
          val ctorParams =
            try sym.primaryConstructor.paramSymss.flatten
            catch { case _: Exception => Nil }
          ctorParams.foreach { p =>
            val n = p.name
            if (n.nonEmpty && n != "x$0" && !n.startsWith("$")) cols += SqlNameMapper.SnakeCase(n)
          }
        }
        // Strict: only extract explicit Table.apply/.derived first arg (the table name), not any string literal.
        // Narrow to Table constructors via owner check (#28).
        try {
          tbl.asTerm match {
            case Apply(fun, arg :: _) if fun.symbol.owner.fullName.startsWith("zio.blocks.sql.Table") =>
              arg match {
                case Literal(StringConstant(s)) if s.nonEmpty =>
                  derivedSnakeOpt.foreach(names -= _)
                  names += s
                case _ => ()
              }
            case _ => ()
          }
        } catch { case _: Exception => () }
      case _ => () // no generic fallback — do not collect arbitrary strings
    }
  }

  private def extractKnownFromExprs(
    tableExprs: Seq[Expr[Table[?]]]
  )(using Quotes): (Set[String], Set[String]) = {
    val names = scala.collection.mutable.Set.empty[String]
    val cols  = scala.collection.mutable.Set.empty[String]
    tableExprs.foreach(addTableMeta(_, names, cols))
    (names.toSet, cols.toSet)
  }

  @scala.annotation.nowarn("msg=unused")
  private def extractTables(tables: Expr[Seq[Table[?]]])(using Quotes): (Set[String], Set[String]) = {
    import quotes.reflect._
    tables match {
      case Varargs(tableExprs) =>
        extractKnownFromExprs(tableExprs)
      case _ =>
        (Set.empty, Set.empty)
    }
  }

  @scala.annotation.nowarn("msg=unused")
  private def extractTablesChecked(first: Expr[Table[?]], rest: Expr[Seq[Table[?]]])(using
    Quotes
  ): (Set[String], Set[String]) = {
    import quotes.reflect._
    val names = scala.collection.mutable.Set.empty[String]
    val cols  = scala.collection.mutable.Set.empty[String]
    addTableMeta(first, names, cols)
    rest match {
      case Varargs(restExprs) => restExprs.foreach(addTableMeta(_, names, cols))
      case _                  => () // no generic fallback
    }
    (names.toSet, cols.toSet)
  }

  // ---- shared frag assembly ----

  @scala.annotation.nowarn("msg=unused")
  private def buildArgFrags(argExprs: Seq[Expr[Any]])(using Quotes): Seq[Expr[Frag]] = {
    import quotes.reflect._
    argExprs.map { arg =>
      arg match {
        case '{ $a: SqlLiteral } => '{ $a.toFrag }
        case '{ $a: Frag }       => a
        case '{ $a: DbValue }    => '{ Frag(IndexedSeq("", ""), IndexedSeq($a)) }
        case '{ $a: t }          =>
          val widened = TypeRepr.of[t].widen.asType
          widened match {
            case '[w] =>
              if (TypeRepr.of[w] <:< TypeRepr.of[SqlLiteral]) {
                '{ ${ a.asExprOf[SqlLiteral] }.toFrag }
              } else if (TypeRepr.of[w] <:< TypeRepr.of[Frag]) {
                a.asExprOf[Frag]
              } else {
                Expr.summon[DbParam[w]] match {
                  case Some(param) =>
                    '{ Frag(IndexedSeq("", ""), IndexedSeq($param.toDbValue(${ a.asExprOf[w] }))) }
                  case None =>
                    report.errorAndAbort(
                      s"No DbParam/Frag/SqlLiteral/DbValue instance found for type ${Type.show[w]}. " +
                        "Supported: DbParam[T], Frag (verbatim), SqlLiteral (verbatim), DbValue. " +
                        "Use sql\"...\" or StringContext(...).sql(table)(...) accordingly.",
                      arg.asTerm.pos
                    )
                }
              }
          }
      }
    }
  }

  private def assembleFrags(ps: Seq[String], literalFrags: Seq[Expr[Frag]], argFrags: Seq[Expr[Frag]])(using
    Quotes
  ): Expr[Frag] =
    if (argFrags.isEmpty) {
      '{ Frag(IndexedSeq(${ Varargs(ps.map(Expr(_))) }: _*), IndexedSeq.empty) }
    } else {
      val allFrags: Seq[Expr[Frag]] = {
        val buf = Seq.newBuilder[Expr[Frag]]
        buf += literalFrags.head
        var i = 0
        while (i < argFrags.length) {
          buf += argFrags(i)
          buf += literalFrags(i + 1)
          i += 1
        }
        buf.result()
      }
      '{ Frag.sequence(${ Varargs(allFrags) }: _*) }
    }

  private def buildInlineFrag(ps: Seq[String], argExprs: Seq[Expr[Any]])(using Quotes): Expr[Frag] = {
    val literalFrags: Seq[Expr[Frag]] = ps.map(p => '{ Frag.literal(${ Expr(p) }) })
    val argFrags: Seq[Expr[Frag]]     = buildArgFrags(argExprs)
    assembleFrags(ps, literalFrags, argFrags)
  }

  private def buildFallbackFrag(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Frag] =
    '{
      val partsSeq: IndexedSeq[String] = $sc.parts.toIndexedSeq
      val argsSeq: Seq[Any]            = $args
      var frag: Frag                   = Frag.literal(partsSeq.head)
      var idx                          = 0
      while (idx < argsSeq.length) {
        val argFrag: Frag = argsSeq(idx) match {
          case f: Frag       => f
          case s: SqlLiteral => s.toFrag
          case v: DbValue    => Frag(IndexedSeq("", ""), IndexedSeq(v))
          case other         =>
            throw new IllegalArgumentException(
              s"Unexpected interpolated value: $other (type ${other.getClass.getName}). " +
                "Runtime fallback only supports Frag (verbatim), SqlLiteral (verbatim) or DbValue directly. " +
                "For DbParam[T] use a statically known sql\"...\" or StringContext(...).sql(table)(...) call with inline args."
            )
        }
        frag = frag ++ argFrag ++ Frag.literal(partsSeq(idx + 1))
        idx += 1
      }
      frag
    }

  private def buildFragExpr(partsOpt: Option[Seq[String]], args: Expr[Seq[Any]], sc: Expr[StringContext])(using
    Quotes
  ): Expr[Frag] =
    (partsOpt, args) match {
      case (Some(ps), Varargs(argExprs)) => buildInlineFrag(ps, argExprs)
      case _                             => buildFallbackFrag(sc, args)
    }
}
