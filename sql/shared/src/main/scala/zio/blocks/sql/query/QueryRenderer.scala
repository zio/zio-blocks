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

import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr}
import zio.blocks.sql.{DbCodec, Frag, SqlDialect, SqlIdentifier, Table}

object QueryRenderer {

  def render(query: SqlQuery[?, ?], @scala.annotation.unused dialect: SqlDialect): Frag = {
    val allTables: Vector[(Table[_], String)] =
      Vector((query.source, "t0")) ++ query.joins.map(j => (j.table, j.alias))

    val selectCols: Vector[String] = allTables.flatMap { case (tbl, alias) =>
      tbl.columns.map { col =>
        val c = SqlIdentifier.validate("column", col)
        s"""$alias."$c""""
      }
    }

    val tableName = SqlIdentifier.validate("table", query.source.name)

    var frag: Frag =
      if (selectCols.isEmpty) Frag.literal(s"""SELECT * FROM "$tableName" AS t0""")
      else {
        val selectColFrags = selectCols.map(Frag.literal)
        val selectCombined = selectColFrags.reduce(_ ++ Frag.literal(", ") ++ _)
        Frag.literal("SELECT ") ++ selectCombined ++ Frag.literal(s""" FROM "$tableName" AS t0""")
      }

    frag = appendClauses(query, allTables, frag)
    frag
  }

  def renderTyped[T](
    query: SqlQuery[?, ?],
    projections: Vector[Expr[?, ?]],
    codec: DbCodec[T],
    @scala.annotation.unused dialect: SqlDialect
  ): Frag = {
    if (projections.isEmpty)
      throw new IllegalArgumentException("select: empty projection")
    if (projections.size != codec.columnCount)
      throw new IllegalArgumentException(
        s"projection column count ${projections.size} does not match codec column count ${codec.columnCount} (codec columns: ${codec.columns.mkString(", ")})"
      )
    val allTables: Vector[(Table[_], String)] =
      Vector((query.source, "t0")) ++ query.joins.map(j => (j.table, j.alias))
    val selectFrags: Vector[Frag] = projections.zipWithIndex.map { case (expr, idx) =>
      val rendered = renderExpr(expr, allTables)
      val alias    = codec.columns(idx)
      val colName  = SqlIdentifier.validate("column", alias)
      rendered ++ Frag.literal(s""" AS "$colName"""")
    }
    val tableName      = SqlIdentifier.validate("table", query.source.name)
    val selectCombined =
      if (selectFrags.size == 1) selectFrags.head
      else selectFrags.reduce(_ ++ Frag.literal(", ") ++ _)
    var frag: Frag = Frag.literal("SELECT ") ++ selectCombined ++ Frag.literal(s""" FROM "$tableName" AS t0""")
    frag = appendClauses(query, allTables, frag)
    frag
  }

  private def appendClauses(
    query: SqlQuery[?, ?],
    allTables: Vector[(Table[_], String)],
    base: Frag
  ): Frag = {
    var frag = base
    query.joins.foreach { j =>
      val tName   = SqlIdentifier.validate("table", j.table.name)
      val kindStr = j.kind match {
        case JoinKind.Inner => "INNER JOIN"
        case JoinKind.Left  => "LEFT JOIN"
      }
      frag = frag ++ Frag.literal(s""" $kindStr "$tName" AS ${j.alias} ON """) ++ j.on
    }
    val whereFrags: Vector[Frag] = {
      val typed  = query.typedFilters.map(renderExpr(_, allTables)).toVector
      val legacy = query.filters
      typed ++ legacy
    }
    if (whereFrags.nonEmpty) {
      val combined = whereFrags.reduce((a, b) => a ++ Frag.literal(" AND ") ++ b)
      frag = frag ++ Frag.literal(" WHERE ") ++ combined
    }
    val groupByFrags: Vector[Frag] = {
      val legacy = query.groupBy.map { col =>
        val c = SqlIdentifier.validate("column", col)
        Frag.literal(s"""t0."$c"""")
      }.toVector
      val typed = query.typedGroupBy.map(renderExpr(_, allTables))
      legacy ++ typed
    }
    if (groupByFrags.nonEmpty) {
      val gbCombined = groupByFrags.reduce(_ ++ Frag.literal(", ") ++ _)
      frag = frag ++ Frag.literal(" GROUP BY ") ++ gbCombined
    }
    val havingFrags: Vector[Frag] = {
      val legacy = query.having.toVector
      val typed  = query.typedHaving.map(renderExpr(_, allTables)).toVector
      legacy ++ typed
    }
    if (havingFrags.nonEmpty) {
      val combined = havingFrags.reduce((a, b) => a ++ Frag.literal(" AND ") ++ b)
      frag = frag ++ Frag.literal(" HAVING ") ++ combined
    }
    if (query.orderBy.nonEmpty) {
      val obFrags = query.orderBy.map { o =>
        val c   = SqlIdentifier.validate("column", o.column)
        val dir = o.direction match {
          case SortOrder.Asc  => "ASC"
          case SortOrder.Desc => "DESC"
        }
        Frag.literal(s"""t0."$c" $dir""")
      }
      val obCombined = obFrags.reduce(_ ++ Frag.literal(", ") ++ _)
      frag = frag ++ Frag.literal(" ORDER BY ") ++ obCombined
    }
    query.limit.foreach { n =>
      frag = frag ++ Frag.literal(s" LIMIT $n")
    }
    query.offset.foreach { n =>
      frag = frag ++ Frag.literal(s" OFFSET $n")
    }
    frag
  }

  private[query] def resolveAliasForColumn(
    colTable: Table[_],
    aliasOpt: Option[String],
    allTables: Vector[(Table[_], String)]
  ): String = aliasOpt match {
    case Some(a) =>
      SqlIdentifier.validate("alias", a)
      allTables.find(_._2 == a) match {
        case None =>
          throw new IllegalArgumentException(
            s"unknown alias '$a' for table '${colTable.name}'; available aliases: ${allTables.map(_._2).mkString(", ")}"
          )
        case Some((t, _)) =>
          if (t.name != colTable.name)
            throw new IllegalArgumentException(
              s"alias '$a' refers to table '${t.name}' but column belongs to table '${colTable.name}'"
            )
          a
      }
    case None =>
      val matches = allTables.filter(_._1.name == colTable.name)
      if (matches.isEmpty)
        throw new IllegalArgumentException(
          s"table '${colTable.name}' not found in query; available tables: ${allTables.map(_._1.name).mkString(", ")}"
        )
      else if (matches.size > 1)
        throw new IllegalArgumentException(
          s"ambiguous column reference for table '${colTable.name}': candidates ${matches.map(_._2).mkString(", ")} — use colAt(...) with explicit alias"
        )
      else matches.head._2
  }

  // Single shared interpreter for native DynamicSchemaExpr cases, adapted from docs/guides/query-dsl-sql.md.
  // Handles Select (via columnContext alias-qualified), Literal (via DbValue), Relational, Logical, Not, Arithmetic, StringRegexMatch.
  private[query] def renderDynamic(
    dyn: DynamicSchemaExpr,
    columnContext: Map[DynamicOptic, (Table[_], String, Option[String])],
    allTables: Vector[(Table[_], String)]
  ): Frag = dyn match {
    case DynamicSchemaExpr.Select(path) =>
      columnContext.get(path) match {
        case Some((table, col, aliasOpt)) =>
          val alias   = resolveAliasForColumn(table, aliasOpt, allTables)
          val colName = SqlIdentifier.validate("column", col)
          if (!table.columns.contains(colName))
            throw new IllegalArgumentException(
              s"column '$colName' not found in table '${table.name}' columns: ${table.columns.mkString(", ")}"
            )
          Frag.literal(s"""$alias."$colName"""")
        case None =>
          val field      = path.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.lastOption.getOrElse("")
          val colName    = SqlIdentifier.validate("column", field)
          val candidates = allTables.filter(_._1.columns.contains(colName)).map(_._2)
          if (candidates.isEmpty) {
            val available = allTables.map { case (t, a) => s"${t.name} as $a (${t.columns.mkString(", ")})" }
              .mkString("; ")
            throw new IllegalArgumentException(
              s"unresolved column '$colName' (field '$field', path $path) not found in query; available tables: $available"
            )
          } else if (candidates.size > 1) {
            throw new IllegalArgumentException(
              s"ambiguous column '$colName' (field '$field') candidates: ${candidates.mkString(", ")} — use colAt(...) with explicit alias"
            )
          } else {
            val alias = candidates.head
            Frag.literal(s"""$alias."$colName"""")
          }
      }

    case DynamicSchemaExpr.Literal(value, _) =>
      val db = ExprInternal.dynamicValueToDbValue(value)
      Frag(IndexedSeq("", ""), IndexedSeq(db))

    case DynamicSchemaExpr.Relational(left, right, op) =>
      val l     = renderDynamic(left, columnContext, allTables)
      val r     = renderDynamic(right, columnContext, allTables)
      val opStr = op match {
        case DynamicSchemaExpr.RelationalOperator.Equal              => "="
        case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
        case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
        case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
        case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      l ++ Frag.literal(s" $opStr ") ++ r

    case DynamicSchemaExpr.Logical(left, right, op) =>
      val l     = renderDynamic(left, columnContext, allTables)
      val r     = renderDynamic(right, columnContext, allTables)
      val opStr = op match {
        case DynamicSchemaExpr.LogicalOperator.And => "AND"
        case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
      }
      Frag.literal("(") ++ l ++ Frag.literal(s" $opStr ") ++ r ++ Frag.literal(")")

    case DynamicSchemaExpr.Not(inner) =>
      val i = renderDynamic(inner, columnContext, allTables)
      Frag.literal("NOT (") ++ i ++ Frag.literal(")")

    case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
      val l     = renderDynamic(left, columnContext, allTables)
      val r     = renderDynamic(right, columnContext, allTables)
      val opStr = op match {
        case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
        case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
        case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
        case DynamicSchemaExpr.ArithmeticOperator.Divide   => "/"
        case DynamicSchemaExpr.ArithmeticOperator.Pow      => "^"
        case DynamicSchemaExpr.ArithmeticOperator.Modulo   => "%"
      }
      Frag.literal("(") ++ l ++ Frag.literal(s" $opStr ") ++ r ++ Frag.literal(")")

    case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
      val s   = renderDynamic(string, columnContext, allTables)
      val reg = renderDynamic(regex, columnContext, allTables)
      s ++ Frag.literal(" LIKE ") ++ reg

    case DynamicSchemaExpr.StringConcat(left, right) =>
      val l = renderDynamic(left, columnContext, allTables)
      val r = renderDynamic(right, columnContext, allTables)
      Frag.literal("CONCAT(") ++ l ++ Frag.literal(", ") ++ r ++ Frag.literal(")")

    case DynamicSchemaExpr.StringLength(string) =>
      val s = renderDynamic(string, columnContext, allTables)
      Frag.literal("LENGTH(") ++ s ++ Frag.literal(")")

    case other => throw new IllegalArgumentException(s"unsupported DynamicSchemaExpr for SQL: $other")
  }

  private[query] def collectColumns(expr: Expr[?, ?]): Map[DynamicOptic, (Table[_], String, Option[String])] = {
    def loop(
      e: Expr[?, ?],
      acc: Map[DynamicOptic, (Table[_], String, Option[String])]
    ): Map[DynamicOptic, (Table[_], String, Option[String])] =
      e match {
        case Column(_, _, _, dyn) =>
          dyn match {
            case s: DynamicSchemaExpr.Select =>
              e match {
                case Column(table, col, alias, _) => acc + (s.path -> ((table, col, alias)))
                case _                            => acc
              }
            case _ => acc
          }
        case Lit(_, _, _)              => acc
        case Relational(l, r, _, _)    => loop(r, loop(l, acc))
        case Logical(l, r, _, _)       => loop(r, loop(l, acc))
        case NotExpr(inner, _)         => loop(inner, acc)
        case Arithmetic(l, r, _, _, _) => loop(r, loop(l, acc))
        case LikeExpr(l, p, _)         => loop(p, loop(l, acc))
        case InExpr(col, _, _)         => loop(col, acc)
        case Agg(_, arg)               => loop(arg, acc)
        case AggStar(_)                => acc
        case OptExpr(inner)            => loop(inner, acc)
      }
    loop(expr, Map.empty)
  }

  private[query] def renderExpr(expr: Expr[?, ?], allTables: Vector[(Table[_], String)]): Frag = expr match {
    // Native cases — delegate to shared renderDynamic
    case c: Column[_, _, _] =>
      renderDynamic(c.dynamic, collectColumns(c), allTables)
    case l: Lit[_] =>
      renderDynamic(l.dynamic, Map.empty, allTables)
    case r: Relational[_, _] =>
      r.dynamic match {
        case Some(dyn) =>
          val ctx = collectColumns(r)
          renderDynamic(dyn, ctx, allTables)
        case None =>
          val leftFrag  = renderExpr(r.left, allTables)
          val rightFrag = renderExpr(r.right, allTables)
          val opStr     = r.operator match {
            case DynamicSchemaExpr.RelationalOperator.Equal              => "="
            case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
            case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
            case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
            case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
            case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
          }
          leftFrag ++ Frag.literal(s" $opStr ") ++ rightFrag
      }
    case l: Logical[_] =>
      l.dynamic match {
        case Some(dyn) =>
          val ctx = collectColumns(l)
          renderDynamic(dyn, ctx, allTables)
        case None =>
          val leftFrag  = renderExpr(l.left, allTables)
          val rightFrag = renderExpr(l.right, allTables)
          val opStr     = l.operator match {
            case DynamicSchemaExpr.LogicalOperator.And => "AND"
            case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
          }
          Frag.literal("(") ++ leftFrag ++ Frag.literal(s" $opStr ") ++ rightFrag ++ Frag.literal(")")
      }
    case n: NotExpr[_] =>
      n.dynamic match {
        case Some(dyn) => renderDynamic(dyn, collectColumns(n), allTables)
        case None      => Frag.literal("NOT (") ++ renderExpr(n.expr, allTables) ++ Frag.literal(")")
      }
    case a: Arithmetic[_, _] =>
      a.dynamic match {
        case Some(dyn) => renderDynamic(dyn, collectColumns(a), allTables)
        case None      =>
          val leftFrag  = renderExpr(a.left, allTables)
          val rightFrag = renderExpr(a.right, allTables)
          val opStr     = a.operator match {
            case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
            case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
            case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
            case DynamicSchemaExpr.ArithmeticOperator.Divide   => "/"
            case _                                             => throw new IllegalArgumentException(s"unsupported arithmetic operator: ${a.operator}")
          }
          Frag.literal("(") ++ leftFrag ++ Frag.literal(s" $opStr ") ++ rightFrag ++ Frag.literal(")")
      }
    case l: LikeExpr[_] =>
      renderDynamic(l.dynamic, collectColumns(l), allTables)
    // SQL-only extensions
    case InExpr(col, values, schema) =>
      if (values.isEmpty) Frag.literal("1=0")
      else {
        val colFrag = renderExpr(col, allTables)
        // GADT existential for InExpr's A; asInstanceOf unavoidable due to erasure, tested via In placeholder parity
        val dbValues = values
          .asInstanceOf[Seq[Any]]
          .map(v =>
            ExprInternal.dynamicValueToDbValue(schema.asInstanceOf[zio.blocks.schema.Schema[Any]].toDynamicValue(v))
          )
        val placeholders: Frag =
          dbValues.map(v => Frag(IndexedSeq("", ""), IndexedSeq(v))).reduce((a, b) => a ++ Frag.literal(", ") ++ b)
        colFrag ++ Frag.literal(" IN (") ++ placeholders ++ Frag.literal(")")
      }
    case Agg(func, arg) =>
      val argFrag = renderExpr(arg, allTables)
      val funcStr = func match {
        case AggFunc.Count     => "COUNT"
        case AggFunc.Sum       => "SUM"
        case AggFunc.Avg       => "AVG"
        case AggFunc.Min       => "MIN"
        case AggFunc.Max       => "MAX"
        case AggFunc.CountStar => "COUNT"
      }
      Frag.literal(s"$funcStr(") ++ argFrag ++ Frag.literal(")")
    case AggStar(func) =>
      val funcStr = func match {
        case AggFunc.CountStar => "COUNT"
        case AggFunc.Count     => "COUNT"
        case _                 => "COUNT"
      }
      Frag.literal(s"$funcStr(*)")
    case OptExpr(inner) =>
      renderExpr(inner, allTables)
  }
}
