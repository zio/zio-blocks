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

import zio.blocks.sql.{DbCodec, DbValue, Frag, SqlDialect, SqlIdentifier, SqlStatement, Table}

/**
 * Immutable query IR starting from a source table.
 *
 * Alias allocation is deterministic: t0 = source, t1..tN in join order
 * (self-join safe — same Table joined twice gets distinct aliases). Rendering
 * is performed by [[QueryRenderer]] and produces a [[Frag]] via `Frag.++`
 * composition only, using quoted identifiers and dialect placeholders.
 *
 * Inspection is provided via [[statement]] / [[explain]] which expose a stable
 * [[SqlStatement]] view and a single-line SQL with `?N` placeholders plus
 * `-- params: ...` footer for logging, derived directly from the typed IR via
 * [[QueryRenderer]] (no duplicate renderer).
 */
final case class SqlQuery[A] private[query] (
  source: Table[A],
  joins: Vector[JoinNode],
  filters: Vector[Frag],
  groupBy: Vector[String],
  having: Option[Frag],
  orderBy: Vector[OrderBy],
  limit: Option[Int],
  offset: Option[Int],
  typedFilters: Vector[Expr[Boolean]] = Vector.empty,
  typedGroupBy: Vector[Expr[_]] = Vector.empty,
  typedHaving: Option[Expr[Boolean]] = None
) {

  def innerJoin[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Inner)

  def leftJoin[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Left)

  def join[From, To](rel: Rel[From, To], kind: JoinKind): SqlQuery[A] =
    addJoin(rel, kind)

  /** Alias for `innerJoin` — satisfies the `SqlQuery.join(rel)` contract. */
  def join[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Inner)

  /** Alias for `leftJoin` — satisfies the `SqlQuery.joinLeft(rel)` contract. */
  def joinLeft[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Left)

  private def addJoin[From, To](rel: Rel[From, To], kind: JoinKind): SqlQuery[A] = {
    val nextAlias                                              = s"t${joins.size + 1}"
    val isSelfJoin                                             = rel.fromTable.name == rel.toTable.name
    val pair: (Table[_], Frag, String, String, String, String) = if (isSelfJoin) {
      val fk                                      = SqlIdentifier.validate("column", rel.fkColumn)
      val pk                                      = SqlIdentifier.validate("column", rel.pkColumn)
      val allExisting: Vector[(Table[_], String)] =
        Vector((source, "t0")) ++ joins.map(j => (j.table, j.alias))
      val fkAlias = allExisting.reverse
        .find(_._1.name == rel.fromTable.name)
        .map(_._2)
        .getOrElse(
          throw new IllegalStateException(
            s"self-join requires source table '${rel.fromTable.name}' present as t0; existing aliases: ${allExisting
                .map(_._2)
                .mkString(", ")}"
          )
        )
      val on = Frag.literal(s"""$fkAlias."$fk" = $nextAlias."$pk"""")
      (rel.toTable.asInstanceOf[Table[_]], on, fkAlias, nextAlias, fk, pk)
    } else {
      val fromOpt          = aliasOf(rel.fromTable)
      val toOpt            = aliasOf(rel.toTable)
      var target: Table[_] = null.asInstanceOf[Table[_]]
      var fkAlias: String  = ""
      var pkAlias: String  = ""
      (fromOpt, toOpt) match {
        case (Some(fa), None) =>
          target = rel.toTable.asInstanceOf[Table[_]]
          fkAlias = fa
          pkAlias = nextAlias
        case (None, Some(ta)) =>
          target = rel.fromTable.asInstanceOf[Table[_]]
          fkAlias = nextAlias
          pkAlias = ta
        case (None, None) =>
          throw new IllegalArgumentException(
            s"neither side of relation ${rel.fromTable.name}.${rel.fkColumn} = ${rel.toTable.name}.${rel.pkColumn} is present in query; source is '${source.name}' and joins are [${joins.map(j => j.table.name + " as " + j.alias).mkString(", ")}]"
          )
        case (Some(_), Some(_)) =>
          // Both sides already present — treat as joining duplicate alias for target? Use from as existing and create new alias for target duplicate.
          target = rel.toTable.asInstanceOf[Table[_]]
          fkAlias = fromOpt.get
          pkAlias = nextAlias
      }
      val fk = SqlIdentifier.validate("column", rel.fkColumn)
      val pk = SqlIdentifier.validate("column", rel.pkColumn)
      val on = Frag.literal(s"""$fkAlias."$fk" = $pkAlias."$pk"""")
      (target, on, fkAlias, pkAlias, fk, pk)
    }
    val (targetTable, onFrag, fkAlias, pkAlias, fkCol, pkCol) = pair

    val node = JoinNode(
      targetTable,
      nextAlias,
      kind,
      onFrag,
      fkCol,
      pkCol,
      fkAlias,
      pkAlias
    )
    copy(joins = joins :+ node)
  }

  private[sql] def filter(frag: Frag): SqlQuery[A] =
    copy(filters = filters :+ frag)

  private[sql] def where(frag: Frag): SqlQuery[A] = filter(frag)

  def filter(expr: Expr[Boolean]): SqlQuery[A] = where(expr)

  def where(expr: Expr[Boolean]): SqlQuery[A] =
    copy(typedFilters = typedFilters :+ expr)

  def groupBy(cols: String*): SqlQuery[A] = {
    cols.foreach(c => SqlIdentifier.validate("column", c))
    copy(groupBy = cols.toVector)
  }

  def groupBy(expr: Expr[_], exprs: Expr[?]*): SqlQuery[A] =
    copy(typedGroupBy = (expr +: exprs).toVector)

  private[sql] def having(frag: Frag): SqlQuery[A] =
    copy(having = Some(frag))

  def having(expr: Expr[Boolean]): SqlQuery[A] =
    copy(typedHaving = Some(expr))

  def orderBy(col: String, dir: SortOrder = SortOrder.Asc): SqlQuery[A] = {
    SqlIdentifier.validate("column", col)
    copy(orderBy = orderBy :+ OrderBy(col, dir))
  }

  def orderByMany(cols: OrderBy*): SqlQuery[A] =
    copy(orderBy = orderBy ++ cols.toVector)

  def limit(n: Int): SqlQuery[A] = {
    require(n >= 0, "limit must be >= 0")
    copy(limit = Some(n))
  }

  def offset(n: Int): SqlQuery[A] = {
    require(n >= 0, "offset must be >= 0")
    copy(offset = Some(n))
  }

  def toFrag(dialect: SqlDialect): Frag = QueryRenderer.render(this, dialect)

  def sql(dialect: SqlDialect): String = toFrag(dialect).sql(dialect)

  def statement(dialect: SqlDialect): SqlStatement = {
    val frag = toFrag(dialect)
    val src  = SqlStatement.Source(source.name, "t0")
    val js   = joins.map { j =>
      val kind = j.kind match {
        case JoinKind.Inner => SqlStatement.JoinKind.Inner
        case JoinKind.Left  => SqlStatement.JoinKind.Left
      }
      SqlStatement.Join(
        kind,
        j.table.name,
        j.alias,
        SqlStatement.ColumnRef(j.fkAlias, j.fkColumn),
        SqlStatement.ColumnRef(j.pkAlias, j.pkColumn)
      )
    }
    val allTables: Vector[(Table[_], String)] =
      Vector((source, "t0")) ++ joins.map(j => (j.table, j.alias))
    val typedFilterFrags: Vector[SqlStatement.Filter] = typedFilters.map { expr =>
      val pred         = QueryRenderer.renderExpr(expr, allTables)
      val (colOpt, op) = filterMetadata(expr, allTables)
      SqlStatement.Filter(pred, colOpt, op)
    }.toVector
    val legacyFilterFrags: Vector[SqlStatement.Filter] = filters.map { f =>
      SqlStatement.Filter(f, None, "RAW")
    }.toVector
    val allFilters = (typedFilterFrags ++ legacyFilterFrags).toVector
    val grp        =
      if (groupBy.isEmpty && typedGroupBy.isEmpty) None
      else {
        val legacy = groupBy.map(c => SqlStatement.ColumnRef("t0", c))
        val typed  = typedGroupBy.flatMap(extractColumnRefsStrict(allTables))
        val all    = (legacy ++ typed).toVector
        if (all.isEmpty) None else Some(SqlStatement.GroupBy(all))
      }
    val ob = orderBy.map(o =>
      SqlStatement.OrderBy(
        SqlStatement.ColumnRef("t0", o.column),
        o.direction match {
          case SortOrder.Asc  => SqlStatement.OrderDirection.Asc
          case SortOrder.Desc => SqlStatement.OrderDirection.Desc
        }
      )
    )
    val lim = limit.map(SqlStatement.Limit(_))
    val off = offset.map(SqlStatement.Offset(_))
    SqlStatement(src, js.toVector, allFilters, grp, ob.toVector, lim, off, frag)
  }

  def explain(dialect: SqlDialect): String = {
    val st   = statement(dialect)
    val frag = st.frag
    val sb   = new StringBuilder
    var idx  = 1
    var i    = 0
    while (i < frag.parts.length) {
      sb.append(frag.parts(i))
      if (i < frag.params.length) {
        sb.append(s"?$idx")
        idx += 1
      }
      i += 1
    }
    val sql = sb.toString()
    if (frag.params.isEmpty) s"$sql\n-- params: (none)"
    else {
      val types = frag.params.zipWithIndex.map { case (v, n) => s"${n + 1}:${typeLabel(v)}" }.mkString(", ")
      s"$sql\n-- params: $types"
    }
  }

  private def filterMetadata(
    expr: Expr[Boolean],
    allTables: Vector[(Table[_], String)]
  ): (Option[SqlStatement.ColumnRef], String) = expr match {
    case r: Relational[_] =>
      val opStr = r.operator match {
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.Equal              => "="
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      (extractSingleColumn(r.left, allTables), opStr)
    case i: InExpr[_] =>
      (extractSingleColumn(i.col, allTables), "IN")
    case l: LikeExpr =>
      (extractSingleColumn(l.left, allTables), "LIKE")
    case l: Logical =>
      val opStr = l.operator match {
        case zio.blocks.schema.DynamicSchemaExpr.LogicalOperator.And => "AND"
        case zio.blocks.schema.DynamicSchemaExpr.LogicalOperator.Or  => "OR"
      }
      (None, opStr)
    case _: NotExpr =>
      (None, "NOT")
    case _ =>
      (None, "PREDICATE")
  }

  private def extractSingleColumn(
    expr: Expr[_],
    allTables: Vector[(Table[_], String)]
  ): Option[SqlStatement.ColumnRef] =
    expr match {
      case c: Column[_, _] =>
        val alias = QueryRenderer.resolveAliasForColumn(c.table, c.alias, allTables)
        Some(SqlStatement.ColumnRef(alias, c.column))
      case o: OptExpr[_] => extractSingleColumn(o.inner, allTables)
      case _             => None
    }

  private def extractColumnRefsStrict(
    allTables: Vector[(Table[_], String)]
  )(expr: Expr[_]): Vector[SqlStatement.ColumnRef] = {
    def loop(e: Expr[_], acc: Vector[SqlStatement.ColumnRef]): Vector[SqlStatement.ColumnRef] = e match {
      case c: Column[_, _] =>
        val alias = QueryRenderer.resolveAliasForColumn(c.table, c.alias, allTables)
        acc :+ SqlStatement.ColumnRef(alias, c.column)
      case o: OptExpr[_] => loop(o.inner, acc)
      case _             => acc
    }
    loop(expr, Vector.empty)
  }

  @deprecated("Use extractColumnRefsStrict", "task-7")
  @scala.annotation.nowarn("msg=unused")
  private def extractColumnRefs(expr: Expr[_]): Vector[SqlStatement.ColumnRef] = {
    val allTables: Vector[(Table[_], String)] = Vector((source, "t0")) ++ joins.map(j => (j.table, j.alias))
    extractColumnRefsStrict(allTables)(expr)
  }

  private def typeLabel(v: DbValue): String = v match {
    case DbValue.DbNull             => "Null"
    case _: DbValue.DbInt           => "Int"
    case _: DbValue.DbLong          => "Long"
    case _: DbValue.DbDouble        => "Double"
    case _: DbValue.DbFloat         => "Float"
    case _: DbValue.DbBoolean       => "Boolean"
    case _: DbValue.DbString        => "String"
    case _: DbValue.DbBigDecimal    => "BigDecimal"
    case _: DbValue.DbBytes         => "Bytes"
    case _: DbValue.DbShort         => "Short"
    case _: DbValue.DbByte          => "Byte"
    case _: DbValue.DbChar          => "Char"
    case _: DbValue.DbLocalDate     => "LocalDate"
    case _: DbValue.DbLocalDateTime => "LocalDateTime"
    case _: DbValue.DbLocalTime     => "LocalTime"
    case _: DbValue.DbInstant       => "Instant"
    case _: DbValue.DbDuration      => "Duration"
    case _: DbValue.DbUUID          => "UUID"
    case _: DbValue.DbArray         => "Array"
  }

  transparent inline def select[T](inline exprs: Expr[?]*)(using codec: DbCodec[T]): TypedQuery[T] =
    ${ SelectMacros.selectImpl[T]('exprs, 'codec, '{ this }) }

  private def aliasOf(table: Table[_]): Option[String] =
    if (table.name == source.name) Some("t0")
    else joins.find(_.table.name == table.name).map(_.alias)
}

object SqlQuery {
  def from[A](table: Table[A]): SqlQuery[A] =
    SqlQuery(table, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty, None, None)
}

private[query] final case class JoinNode(
  table: Table[_],
  alias: String,
  kind: JoinKind,
  on: Frag,
  fkColumn: String,
  pkColumn: String,
  fkAlias: String,
  pkAlias: String
)

final case class OrderBy(column: String, direction: SortOrder)
