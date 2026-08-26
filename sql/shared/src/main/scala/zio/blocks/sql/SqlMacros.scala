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

  def sqlImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Frag] = {
    import quotes.reflect._

    val parts: Option[Seq[String]] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        Some(rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort })
      case _ => None
    }

    parts.foreach { ps =>
      SqlValidator.validate(ps).foreach(report.errorAndAbort(_))
    }

    val convertedArgs: Expr[IndexedSeq[DbValue]] = args match {
      case Varargs(argExprs) =>
        val converted: Seq[Expr[DbValue]] = argExprs.map { arg =>
          arg match {
            case '{ $a: DbValue } => a
            case '{ $a: t }       =>
              val widened = TypeRepr.of[t].widen.asType
              widened match {
                case '[w] =>
                  Expr.summon[DbParam[w]] match {
                    case Some(param) => '{ $param.toDbValue(${ a.asExprOf[w] }) }
                    case None        =>
                      report.errorAndAbort(
                        s"No DbParam instance found for type ${Type.show[w]}. " +
                          "Only types with a DbParam[T] instance can be interpolated into sql\"...\".",
                        arg.asTerm.pos
                      )
                  }
              }
          }
        }
        '{ IndexedSeq(${ Varargs(converted) }: _*) }
      case _ =>
        '{
          $args.map {
            case v: DbValue => v; case other => throw new IllegalArgumentException(s"Unexpected value: $other")
          }.toIndexedSeq
        }
    }

    '{ Frag($sc.parts.toIndexedSeq, $convertedArgs) }
  }

  def sqlCheckedImpl(
    sc: Expr[StringContext],
    tables: Expr[Seq[Table[?]]],
    args: Expr[Seq[Any]]
  )(using Quotes): Expr[Frag] = {
    import quotes.reflect._

    val parts: Option[Seq[String]] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        Some(rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort })
      case _ => None
    }

    parts.foreach { ps =>
      SqlValidator.validate(ps).foreach(report.errorAndAbort(_))
      val (knownTables, knownColumns) = extractTables(tables)
      val diagnostics                 = SqlIdentifierChecker.validate(ps, knownTables, knownColumns)
      diagnostics.take(5).foreach(d => report.errorAndAbort(d.message))
      try {
        val ownerName = {
          var sym = Symbol.spliceOwner
          while (
            sym != Symbol.noSymbol && (sym.flags
              .is(Flags.Synthetic) || sym.name == "<init>" || sym.name.startsWith("$anon") || sym.name == "$package")
          ) {
            sym = sym.owner
          }
          if (sym == Symbol.noSymbol) "query"
          else {
            val full = sym.fullName
            val last = if (full.contains(".")) full.split("\\.").last else full
            val cand = if (last.nonEmpty && last != "<init>") last else sym.name
            if (cand == null || cand.isEmpty || cand == "<none>") "query" else cand
          }
        }
        val safe =
          try SqlIdentifier.validate("table", ownerName)
          catch { case _: Throwable => ownerName.replaceAll("[^A-Za-z0-9_]", "_") }
        val placeholderSql = ps.mkString("?")
        for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
          Dump.emit(safe, dialect, placeholderSql)
        }
      } catch { case _: Throwable => }
    }

    val convertedArgs: Expr[IndexedSeq[DbValue]] = args match {
      case Varargs(argExprs) =>
        val converted: Seq[Expr[DbValue]] = argExprs.map { arg =>
          arg match {
            case '{ $a: DbValue } => a
            case '{ $a: t }       =>
              val widened = TypeRepr.of[t].widen.asType
              widened match {
                case '[w] =>
                  Expr.summon[DbParam[w]] match {
                    case Some(param) => '{ $param.toDbValue(${ a.asExprOf[w] }) }
                    case None        =>
                      report.errorAndAbort(
                        s"No DbParam instance found for type ${Type.show[w]}. " +
                          "Only types with a DbParam[T] instance can be interpolated into sqlChecked\"...\".",
                        arg.asTerm.pos
                      )
                  }
              }
          }
        }
        '{ IndexedSeq(${ Varargs(converted) }: _*) }
      case _ =>
        '{
          $args.map {
            case v: DbValue => v; case other => throw new IllegalArgumentException(s"Unexpected value: $other")
          }.toIndexedSeq
        }
    }

    '{ Frag($sc.parts.toIndexedSeq, $convertedArgs) }
  }

  def sqlUncheckedImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Frag] = {
    import quotes.reflect._

    val parts: Option[Seq[String]] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        Some(rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort })
      case _ => None
    }

    parts.foreach { ps =>
      SqlValidator.validate(ps).foreach(report.errorAndAbort(_))
    }

    val convertedArgs: Expr[IndexedSeq[DbValue]] = args match {
      case Varargs(argExprs) =>
        val converted: Seq[Expr[DbValue]] = argExprs.map { arg =>
          arg match {
            case '{ $a: DbValue } => a
            case '{ $a: t }       =>
              val widened = TypeRepr.of[t].widen.asType
              widened match {
                case '[w] =>
                  Expr.summon[DbParam[w]] match {
                    case Some(param) => '{ $param.toDbValue(${ a.asExprOf[w] }) }
                    case None        =>
                      report.errorAndAbort(
                        s"No DbParam instance found for type ${Type.show[w]}. " +
                          "Only types with a DbParam[T] instance can be interpolated into sqlUnchecked\"...\".",
                        arg.asTerm.pos
                      )
                  }
              }
          }
        }
        '{ IndexedSeq(${ Varargs(converted) }: _*) }
      case _ =>
        '{
          $args.map {
            case v: DbValue => v; case other => throw new IllegalArgumentException(s"Unexpected value: $other")
          }.toIndexedSeq
        }
    }

    '{ Frag($sc.parts.toIndexedSeq, $convertedArgs) }
  }

  private def extractTables(tables: Expr[Seq[Table[?]]])(using Quotes): (Set[String], Set[String]) = {
    import quotes.reflect._
    tables match {
      case Varargs(tableExprs) =>
        val names = scala.collection.mutable.Set.empty[String]
        val cols  = scala.collection.mutable.Set.empty[String]
        tableExprs.foreach { tExpr =>
          tExpr match {
            case '{ $tbl: Table[t] } =>
              val tpe      = TypeRepr.of[t]
              val sym      = tpe.typeSymbol
              val typeName = {
                val n = sym.name
                if (n == "<none>" || n.isEmpty) "" else n
              }
              if (typeName.nonEmpty) {
                names += SqlNameMapper.SnakeCase(typeName)
              }
              val fields =
                try sym.caseFields
                catch { case _: Exception => Nil }
              val fieldSyms =
                if (fields.nonEmpty) fields
                else sym.declaredFields.filterNot(_.isNoSymbol)
              if (fieldSyms.nonEmpty) {
                fieldSyms.foreach { f =>
                  cols += SqlNameMapper.SnakeCase(f.name)
                }
              } else {
                val ctorParams = sym.primaryConstructor.paramSymss.flatten
                ctorParams.foreach { p =>
                  val n = p.name
                  if (n.nonEmpty && n != "x$0" && !n.startsWith("$")) cols += SqlNameMapper.SnakeCase(n)
                }
              }
              try {
                val term = tbl.asTerm
                term match {
                  case Apply(_, args) =>
                    args.foreach {
                      case Literal(StringConstant(s)) if s.nonEmpty && s.matches("[A-Za-z_][A-Za-z0-9_]*") =>
                        names += s
                      case _ =>
                    }
                  case _ =>
                }
              } catch { case _: Exception => () }
            case _ =>
              try {
                val term                          = tExpr.asTerm
                def collectStrings(t: Term): Unit = t match {
                  case Literal(StringConstant(s)) if s.matches("[A-Za-z_][A-Za-z0-9_]*") => names += s
                  case Apply(fun, args)                                                  =>
                    collectStrings(fun); args.foreach(collectStrings)
                  case Select(qual, _)   => collectStrings(qual)
                  case Inlined(_, _, t2) => collectStrings(t2)
                  case Block(_, t2)      => collectStrings(t2)
                  case Typed(t2, _)      => collectStrings(t2)
                  case _                 =>
                }
                collectStrings(term)
              } catch { case _: Exception => () }
          }
        }
        (names.toSet, cols.toSet)
      case _ =>
        (Set.empty, Set.empty)
    }
  }
}
