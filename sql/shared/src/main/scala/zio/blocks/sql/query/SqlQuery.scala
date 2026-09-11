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
 * Scope phantom `S` tracks LEFT JOIN nullability: each LEFT JOIN adds
 * `Option[JoinedType]` to the tuple, each INNER JOIN adds `JoinedType`.
 * Query-bound column builders inspect the slot tuple `(A *: S)` to decide
 * whether a table's columns are nullable (`Expr[Option[B]]` vs `Expr[B]`).
 * Source always non-optional.
 *
 * The path-dependent member `type Scope = this.type` gives every query value a
 * distinct identity: `val q1 = SqlQuery.from(t); val q2 = SqlQuery.from(t)`
 * have `q1.Scope = q1.type != q2.Scope = q2.type`, and expressions built from
 * `q1` cannot be mixed into `q2`. Transformations (joins, where, groupBy,
 * having, select) return the refined type `SqlQuery[A, S'] { type Scope =
 * self.Scope }`, so the whole lineage of a query value shares one scope and
 * fluent chaining keeps working.
 */
final case class SqlQuery[A, S <: Tuple] private[query] (
  source: Table[A],
  joins: Vector[JoinNode],
  filters: Vector[Frag],
  groupBy: Vector[String],
  having: Option[Frag],
  orderBy: Vector[OrderExpr],
  limit: Option[Int],
  offset: Option[Int],
  typedFilters: Vector[Expr[Boolean, ?]] = Vector.empty,
  typedGroupBy: Vector[Expr[?, ?]] = Vector.empty,
  typedHaving: Option[Expr[Boolean, ?]] = None
) { self =>

  /** Query-bound scope: the singleton type of this query value. */
  type Scope = this.type

  def innerJoin[From, To](rel: Rel[From, To]): SqlQuery[A, Tuple.Append[S, SqlQuery.JoinTarget[From, To, A]]] {
    type Scope = self.Scope
  } =
    addJoin(rel, JoinKind.Inner)
      .asInstanceOf[SqlQuery[A, Tuple.Append[S, SqlQuery.JoinTarget[From, To, A]]] { type Scope = self.Scope }]

  def leftJoin[From, To](rel: Rel[From, To]): SqlQuery[A, Tuple.Append[S, Option[SqlQuery.JoinTarget[From, To, A]]]] {
    type Scope = self.Scope
  } =
    addJoin(rel, JoinKind.Left)
      .asInstanceOf[SqlQuery[A, Tuple.Append[S, Option[SqlQuery.JoinTarget[From, To, A]]]] { type Scope = self.Scope }]

  def join[From, To](rel: Rel[From, To], kind: JoinKind): SqlQuery[A, S] { type Scope = self.Scope } =
    addJoin(rel, kind).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  /** Alias for `innerJoin` — satisfies the `SqlQuery.join(rel)` contract. */
  def join[From, To](rel: Rel[From, To]): SqlQuery[A, Tuple.Append[S, SqlQuery.JoinTarget[From, To, A]]] {
    type Scope = self.Scope
  } =
    innerJoin(rel)

  /** Alias for `leftJoin` — satisfies the `SqlQuery.joinLeft(rel)` contract. */
  def joinLeft[From, To](rel: Rel[From, To]): SqlQuery[A, Tuple.Append[S, Option[SqlQuery.JoinTarget[From, To, A]]]] {
    type Scope = self.Scope
  } =
    leftJoin(rel)

  private def addJoin[From, To](rel: Rel[From, To], kind: JoinKind): SqlQuery[A, S] = {
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

  private[sql] def filter(frag: Frag): SqlQuery[A, S] { type Scope = self.Scope } =
    copy(filters = filters :+ frag).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  private[sql] def where(frag: Frag): SqlQuery[A, S] { type Scope = self.Scope } = filter(frag)

  def filter[Sc2 <: Scope](expr: Expr[Boolean, Sc2]): SqlQuery[A, S] { type Scope = self.Scope } = where(expr)

  def where[Sc2 <: Scope](expr: Expr[Boolean, Sc2]): SqlQuery[A, S] { type Scope = self.Scope } =
    copy(typedFilters = typedFilters :+ expr).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  /**
   * Apply several query-bound filters in one step (expressions must all be
   * bound to this query).
   */
  def whereAll[Sc2 <: Scope](exprs: Expr[Boolean, Sc2]*): SqlQuery[A, S] { type Scope = self.Scope } =
    copy(typedFilters = typedFilters ++ exprs).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  def groupBy(cols: String*): SqlQuery[A, S] { type Scope = self.Scope } = {
    cols.foreach(c => SqlIdentifier.validate("column", c))
    copy(groupBy = cols.toVector).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]
  }

  def groupBy[Sc2 <: Scope](expr: Expr[?, Sc2], exprs: Expr[?, Sc2]*): SqlQuery[A, S] { type Scope = self.Scope } =
    copy(typedGroupBy = (expr +: exprs).toVector).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  private[sql] def having(frag: Frag): SqlQuery[A, S] { type Scope = self.Scope } =
    copy(having = Some(frag)).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  def having[Sc2 <: Scope](expr: Expr[Boolean, Sc2]): SqlQuery[A, S] { type Scope = self.Scope } =
    copy(typedHaving = Some(expr)).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  /**
   * Add a query-scoped `ORDER BY` term. The expression must carry this query's
   * scope (`Sc2 <: Scope`) or be scope-neutral (`Nothing`, e.g. a literal);
   * expressions from another query value are rejected at compile time. Multiple
   * calls accumulate in order with independent directions.
   */
  def orderBy[Sc2 <: Scope](expr: Expr[?, Sc2], dir: SortOrder = SortOrder.Asc): SqlQuery[A, S] {
    type Scope = self.Scope
  } =
    copy(orderBy = orderBy :+ OrderExpr(expr, dir)).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  /**
   * Add several query-scoped `ORDER BY` terms as `(expr, direction)` pairs.
   * Each expression may independently be query-scoped or scope-neutral.
   */
  def orderByMany(exprs: (Expr[?, ? <: Scope], SortOrder)*): SqlQuery[A, S] { type Scope = self.Scope } =
    copy(orderBy = orderBy ++ exprs.toVector.map { case (e, d) => OrderExpr(e, d) })
      .asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]

  def limit(n: Int): SqlQuery[A, S] { type Scope = self.Scope } = {
    require(n >= 0, "limit must be >= 0")
    copy(limit = Some(n)).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]
  }

  def offset(n: Int): SqlQuery[A, S] { type Scope = self.Scope } = {
    require(n >= 0, "offset must be >= 0")
    copy(offset = Some(n)).asInstanceOf[SqlQuery[A, S] { type Scope = self.Scope }]
  }

  def toFrag(dialect: SqlDialect): Frag = QueryRenderer.render(this.asInstanceOf[SqlQuery[?, ?]], dialect)

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
      val pred         = QueryRenderer.renderExpr(expr.asInstanceOf[Expr[?, ?]], allTables)
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
    val ob = orderBy.map { o =>
      val expr = o.expr.asInstanceOf[Expr[?, ?]]
      SqlStatement.OrderBy(
        QueryRenderer.renderExpr(expr, allTables),
        extractSingleColumn(expr, allTables),
        o.direction match {
          case SortOrder.Asc  => SqlStatement.OrderDirection.Asc
          case SortOrder.Desc => SqlStatement.OrderDirection.Desc
        }
      )
    }
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
    expr: Expr[Boolean, ?],
    allTables: Vector[(Table[_], String)]
  ): (Option[SqlStatement.ColumnRef], String) = expr match {
    case r: Relational[_, _] =>
      val opStr = r.operator match {
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.Equal              => "="
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
        case zio.blocks.schema.DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      (extractSingleColumn(r.left, allTables), opStr)
    case i: InExpr[_, _] =>
      (extractSingleColumn(i.col, allTables), "IN")
    case l: LikeExpr[_] =>
      (extractSingleColumn(l.left, allTables), "LIKE")
    case l: Logical[_] =>
      val opStr = l.operator match {
        case zio.blocks.schema.DynamicSchemaExpr.LogicalOperator.And => "AND"
        case zio.blocks.schema.DynamicSchemaExpr.LogicalOperator.Or  => "OR"
      }
      (None, opStr)
    case _: NotExpr[_] =>
      (None, "NOT")
    case _ =>
      (None, "PREDICATE")
  }

  private def extractSingleColumn(
    expr: Expr[?, ?],
    allTables: Vector[(Table[_], String)]
  ): Option[SqlStatement.ColumnRef] =
    expr match {
      case c: Column[_, _, _] =>
        val alias = QueryRenderer.resolveAliasForColumn(c.table, c.alias, allTables)
        Some(SqlStatement.ColumnRef(alias, c.column))
      case o: OptExpr[_, _] => extractSingleColumn(o.inner, allTables)
      case _                => None
    }

  private def extractColumnRefsStrict(
    allTables: Vector[(Table[_], String)]
  )(expr: Expr[?, ?]): Vector[SqlStatement.ColumnRef] = {
    def loop(e: Expr[?, ?], acc: Vector[SqlStatement.ColumnRef]): Vector[SqlStatement.ColumnRef] = e match {
      case c: Column[_, _, _] =>
        val alias = QueryRenderer.resolveAliasForColumn(c.table, c.alias, allTables)
        acc :+ SqlStatement.ColumnRef(alias, c.column)
      case o: OptExpr[_, _] => loop(o.inner, acc)
      case _                => acc
    }
    loop(expr, Vector.empty)
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

  // ---- Query-bound column builders (scope-carrying) ----

  /**
   * Query-bound column builder. Returns a builder whose `apply` produces
   * `Expr[B, Scope]` for source/inner slots and `Expr[Option[B], Scope]` for
   * LEFT JOIN slots; tables not present in the query (or present more than
   * once, e.g. self-joins) are rejected at compile time.
   */
  def col[T]: QueryColumnBuilder[A, S, T, Scope] = new QueryColumnBuilder[A, S, T, Scope]

  /**
   * Query-bound column builder with an explicit alias slot (`t0` is the source,
   * `tN` the N-th join). Nullability is derived positionally from the slot; a
   * slot whose table does not match the selector is rejected at compile time.
   */
  def colAt[T]: QueryColumnAtBuilder[A, S, T, Scope] = new QueryColumnAtBuilder[A, S, T, Scope]

  // ---- Query-bound aggregate constructors (scope-carrying, concrete result types) ----

  def sum[A, Sc2 <: Scope](col: Expr[A, Sc2])(using Summable[A]): Expr[Option[SumOut[A]], Scope] =
    Agg[Option[SumOut[A]], Scope](AggFunc.Sum, col).asInstanceOf[Expr[Option[SumOut[A]], Scope]]

  def avg[A, Sc2 <: Scope](col: Expr[A, Sc2])(using Averagable[A]): Expr[Option[AvgOut[A]], Scope] =
    Agg[Option[AvgOut[A]], Scope](AggFunc.Avg, col).asInstanceOf[Expr[Option[AvgOut[A]], Scope]]

  @scala.annotation.nowarn
  def min[A: Ordering, Sc2 <: Scope](col: Expr[A, Sc2]): Expr[Option[A], Scope] =
    Agg[Option[A], Scope](AggFunc.Min, col).asInstanceOf[Expr[Option[A], Scope]]

  @scala.annotation.nowarn
  def max[A: Ordering, Sc2 <: Scope](col: Expr[A, Sc2]): Expr[Option[A], Scope] =
    Agg[Option[A], Scope](AggFunc.Max, col).asInstanceOf[Expr[Option[A], Scope]]

  def count[A, Sc2 <: Scope](col: Expr[A, Sc2]): Expr[Long, Scope] =
    Agg[Long, Scope](AggFunc.Count, col).asInstanceOf[Expr[Long, Scope]]

  // ---- Projection ----

  /**
   * Projection. The public signature always returns `TypedQuery[T, Scope]` —
   * the receiver's path-dependent scope — so the lineage of the result never
   * depends on which projection expression comes first or whether every
   * expression is scope-neutral (`countStar`, `lit(...)`). `selectTyped`
   * forwards the receiver scope to the macro as an explicit type argument; the
   * macro only validates projection content.
   */
  transparent inline def select[T](inline exprs: Expr[?, ? <: Scope]*)(using codec: DbCodec[T]): TypedQuery[T, Scope] =
    selectTyped[T, Scope](exprs, codec)

  private[query] transparent inline def selectTyped[T, Sc <: Scope](
    inline exprs: Seq[Expr[?, ?]],
    codec: DbCodec[T]
  ): TypedQuery[T, Sc] =
    ${ SelectMacros.selectImpl[T, Sc]('exprs, 'codec, '{ this }) }

  private def aliasOf(table: Table[_]): Option[String] =
    if (table.name == source.name) Some("t0")
    else joins.find(_.table.name == table.name).map(_.alias)
}

object SqlQuery {
  type JoinTarget[From, To, Src] = From match {
    case Src => To
    case _   => From
  }

  def from[A](table: Table[A]): SqlQuery[A, EmptyTuple] =
    SqlQuery(table, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty, None, None)
}

/**
 * Query-bound column builder. `Sc` is the receiver query's path-dependent
 * scope; the macro receives it as an explicit type argument (never inferred, so
 * it is never widened) and stamps the produced column with it.
 */
final class QueryColumnBuilder[A, S <: Tuple, T, Sc] private[query] {
  transparent inline def apply[B](inline selector: T => B) =
    ${ ExprMacros.queryColImpl[A, S, T, Sc, B]('selector) }
}

final class QueryColumnAtBuilder[A, S <: Tuple, T, Sc] private[query] {
  transparent inline def apply[B](inline alias: String, inline selector: T => B) =
    ${ ExprMacros.queryColAtImpl[A, S, T, Sc, B]('alias, 'selector) }
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

/**
 * Typed `ORDER BY` term: an arbitrary query-scoped expression plus a direction.
 * The constructor and `copy` are package-private (`private[query]`), matching
 * the visibility of the `SqlQuery` IR itself, so ordering storage cannot be
 * forged from outside the query package. Scope is enforced by
 * `SqlQuery.orderBy`'s `Sc2 <: Scope` bound and the per-element `? <: Scope`
 * wildcard of `orderByMany`, mirroring `typedFilters`/ `typedGroupBy`.
 */
private[query] final case class OrderExpr(expr: Expr[?, ?], direction: SortOrder)
