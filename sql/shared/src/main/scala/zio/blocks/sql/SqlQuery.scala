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

import zio.blocks.sql.SqlStatement._

/**
 * Immutable builder for SELECT queries over a [[Table]].
 *
 * Obtain an instance via [[SqlQuery.from]] and chain `join`, `where`,
 * `groupBy`, `orderBy`, `limit` and `offset`. Render the query for a dialect
 * with:
 *   - [[toFrag]] — `Frag` with `?` placeholders and typed params for execution
 *   - [[statement]] — structured [[SqlStatement]] for programmatic inspection
 *   - [[explain]] — single-line SQL with `?N` placeholders plus
 *     `-- params: ...` footer for logging
 *
 * Every identifier is validated and double-quoted at render time (like
 * `zio.blocks.sql.query.QueryRenderer`), so mixed-case and reserved-word
 * columns resolve correctly. `LIMIT`/`OFFSET` are literal-by-design (they are
 * validated `Int`s, matching `QueryRenderer` and `Repo.pageAfter`); only filter
 * values bind as `?` params.
 */
@deprecated(
  "Use zio.blocks.sql.query.SqlQuery for new queries: it validates joins through typed Rels and composes Frag throughout. This builder remains for SqlStatement/explain inspection until the full merge lands.",
  "0.1.0"
)
// Self-references (method return types, `from` constructor call) would warn;
// external users still get the deprecation warning at their call sites.
@scala.annotation.nowarn("cat=deprecation")
final class SqlQuery[A] private (
  private val source: Table[A],
  private val sourceAlias: String,
  private val joins: Vector[Join],
  private val filters: Vector[Filter],
  private val groupBy: Option[GroupBy],
  private val orderBy: Vector[OrderBy],
  private val limit: Option[Limit],
  private val offset: Option[Offset],
  private val columnsByAlias: Map[String, IndexedSeq[String]]
) {

  private val allowedOperators: Set[String] = Set("=", "!=", ">", "<", ">=", "<=", "LIKE", "IN")

  private def validateColumn(column: String): Unit = {
    SqlIdentifier.validate("column", column)
    val _ = SqlIdentifierChecker.validate(Seq(column), Set(column), Set.empty[String])
    ()
  }

  private def validateTableAlias(alias: String): Unit = {
    SqlIdentifier.validate("tableAlias", alias)
    val _              = SqlIdentifierChecker.validate(Seq(alias), Set(alias), Set.empty[String])
    val allowedAliases = Set(sourceAlias) ++ joins.map(_.alias)
    if (!allowedAliases.contains(alias))
      throw new IllegalArgumentException(
        s"Unknown table alias '$alias'. Allowed aliases are: ${allowedAliases.toSeq.sorted.mkString(", ")}"
      )
    ()
  }

  private def validateOperator(operator: String): Unit =
    require(
      allowedOperators.contains(operator.toUpperCase),
      s"Invalid operator '$operator'. Allowed operators are: ${allowedOperators.mkString(", ")}"
    )

  private def dbValuesForIn(value: DbValue): IndexedSeq[DbValue] = value match {
    case DbValue.DbArray(_, elems) =>
      if (elems.isEmpty)
        throw new IllegalArgumentException("IN operator requires non-empty collection")
      elems.map {
        case dv: DbValue          => dv
        case s: String            => DbValue.DbString(s)
        case i: Int               => DbValue.DbInt(i)
        case j: java.lang.Integer => DbValue.DbInt(j.intValue())
        case l: Long              => DbValue.DbLong(l)
        case j: java.lang.Long    => DbValue.DbLong(j.longValue())
        case d: Double            => DbValue.DbDouble(d)
        case j: java.lang.Double  => DbValue.DbDouble(j.doubleValue())
        case f: Float             => DbValue.DbFloat(f)
        case j: java.lang.Float   => DbValue.DbFloat(j.floatValue())
        case b: Boolean           => DbValue.DbBoolean(b)
        case j: java.lang.Boolean => DbValue.DbBoolean(j.booleanValue())
        case s: Short             => DbValue.DbShort(s)
        case b: Byte              => DbValue.DbByte(b)
        case c: Char              => DbValue.DbChar(c)
        case u: java.util.UUID    => DbValue.DbUUID(u)
        case other                => DbValue.DbString(other.toString)
      }.toIndexedSeq
    case other =>
      throw new IllegalArgumentException(
        s"IN operator requires DbArray value with collection, got ${other.getClass.getSimpleName}: $other"
      )
  }

  def join[B](
    other: Table[B],
    leftColumn: String,
    rightColumn: String,
    kind: JoinKind = JoinKind.Inner
  ): SqlQuery[A] = {
    validateColumn(leftColumn)
    validateColumn(rightColumn)
    val newAlias  = s"t${joins.size + 1}"
    val prevAlias =
      if (joins.isEmpty) sourceAlias else joins.last.alias
    val onLeft  = ColumnRef(prevAlias, leftColumn)
    val onRight = ColumnRef(newAlias, rightColumn)
    val j       = Join(kind, other.name, newAlias, onLeft, onRight)
    new SqlQuery(
      source,
      sourceAlias,
      joins :+ j,
      filters,
      groupBy,
      orderBy,
      limit,
      offset,
      columnsByAlias + (newAlias -> other.columns)
    )
  }

  def joinLeft[B](
    other: Table[B],
    leftColumn: String,
    rightColumn: String
  ): SqlQuery[A] =
    join(other, leftColumn, rightColumn, JoinKind.Left)

  def joinOn[B](
    other: Table[B],
    onLeft: ColumnRef,
    onRight: ColumnRef,
    kind: JoinKind = JoinKind.Inner
  ): SqlQuery[A] = {
    validateColumn(onLeft.column)
    validateColumn(onRight.column)
    validateTableAlias(onLeft.tableAlias)
    validateTableAlias(onRight.tableAlias)
    val newAlias = s"t${joins.size + 1}"
    val j        = Join(kind, other.name, newAlias, onLeft, onRight)
    new SqlQuery(
      source,
      sourceAlias,
      joins :+ j,
      filters,
      groupBy,
      orderBy,
      limit,
      offset,
      columnsByAlias + (newAlias -> other.columns)
    )
  }

  def where(column: ColumnRef, operator: String, value: DbValue): SqlQuery[A] = {
    validateColumn(column.column)
    validateTableAlias(column.tableAlias)
    validateOperator(operator)
    if (operator.equalsIgnoreCase("IN")) {
      // Validate IN value early to fail fast
      dbValuesForIn(value)
    }
    val f = Filter(column, operator, value)
    new SqlQuery(source, sourceAlias, joins, filters :+ f, groupBy, orderBy, limit, offset, columnsByAlias)
  }

  def where(column: ColumnRef, value: DbValue): SqlQuery[A] =
    where(column, "=", value)

  def where(table: Table[_], column: String, value: DbValue): SqlQuery[A] =
    where(table, column, "=", value)

  def where(table: Table[_], column: String, operator: String, value: DbValue): SqlQuery[A] = {
    val alias = aliasFor(table)
    where(ColumnRef(alias, column), operator, value)
  }

  def groupBy(columns: ColumnRef*): SqlQuery[A] = {
    columns.foreach { c =>
      validateColumn(c.column)
      validateTableAlias(c.tableAlias)
    }
    val gb = GroupBy(columns.toVector)
    new SqlQuery(source, sourceAlias, joins, filters, Some(gb), orderBy, limit, offset, columnsByAlias)
  }

  def groupBy(table: Table[_], columns: String*): SqlQuery[A] = {
    columns.foreach(validateColumn)
    val alias = aliasFor(table)
    groupBy(columns.map(c => ColumnRef(alias, c)): _*)
  }

  def orderBy(column: ColumnRef, direction: OrderDirection = OrderDirection.Asc): SqlQuery[A] = {
    validateColumn(column.column)
    validateTableAlias(column.tableAlias)
    val ob = OrderBy(column, direction)
    new SqlQuery(source, sourceAlias, joins, filters, groupBy, orderBy :+ ob, limit, offset, columnsByAlias)
  }

  def orderBy(table: Table[_], column: String, direction: OrderDirection): SqlQuery[A] = {
    validateColumn(column)
    val alias = aliasFor(table)
    orderBy(ColumnRef(alias, column), direction)
  }

  def limit(n: Int): SqlQuery[A] = {
    require(n >= 0, "limit must be >= 0")
    new SqlQuery(source, sourceAlias, joins, filters, groupBy, orderBy, Some(Limit(n)), offset, columnsByAlias)
  }

  def offset(n: Int): SqlQuery[A] = {
    require(n >= 0, "offset must be >= 0")
    new SqlQuery(source, sourceAlias, joins, filters, groupBy, orderBy, limit, Some(Offset(n)), columnsByAlias)
  }

  def statement(dialect: SqlDialect): SqlStatement = build(dialect)._1

  def toFrag(dialect: SqlDialect): Frag = build(dialect)._1.frag

  def explain(dialect: SqlDialect): String = {
    val st   = build(dialect)._1
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
      val types = frag.params.zipWithIndex.map { case (v, n) => s"${n + 1}:${SqlQuery.typeLabel(v)}" }.mkString(", ")
      s"$sql\n-- params: $types"
    }
  }

  private def aliasFor(table: Table[_]): String = {
    if (table.name == source.name) return sourceAlias
    joins.find(_.table == table.name).map(_.alias).getOrElse {
      throw new IllegalArgumentException(s"Table '${table.name}' not part of query (source is '${source.name}')")
    }
  }

  private def build(@scala.annotation.unused dialect: SqlDialect): (SqlStatement, Frag) = {
    // `dialect` is currently informational: both dialects render `?`
    // placeholders (see `Frag.renderSql`), so rendering is dialect-identical
    // today. The parameter is kept (rather than removed) so a future `$N`-style
    // dialect can thread through `toFrag`/`statement`/`explain` without an API
    // break; revisit if such a dialect lands.
    val allTables: Vector[(String, IndexedSeq[String], String)] =
      Vector((source.name, columnsByAlias(sourceAlias), sourceAlias)) ++
        joins.map(j => (j.table, columnsByAlias.getOrElse(j.alias, IndexedSeq.empty), j.alias))

    val selectList = allTables.flatMap { case (_, cols, alias) =>
      cols.map(c => s"""$alias."$c"""")
    }.mkString(", ")

    val head = new StringBuilder
    head.append(s"""SELECT $selectList FROM "${source.name}" $sourceAlias""")
    for (j <- joins) {
      val kindStr = j.kind match {
        case JoinKind.Inner => "INNER JOIN"
        case JoinKind.Left  => "LEFT JOIN"
      }
      head.append(s""" $kindStr "${j.table}" ${j.alias} ON ${j.onLeft.qualified} = ${j.onRight.qualified}""")
    }

    val tailBuilder = new StringBuilder
    groupBy.foreach(gb => tailBuilder.append(s" GROUP BY ${gb.columns.map(_.qualified).mkString(", ")}"))
    if (orderBy.nonEmpty) {
      val obStr = orderBy.map { ob =>
        val dir = ob.direction match {
          case OrderDirection.Asc  => "ASC"
          case OrderDirection.Desc => "DESC"
        }
        s"${ob.column.qualified} $dir"
      }.mkString(", ")
      tailBuilder.append(s" ORDER BY $obStr")
    }
    limit.foreach(l => tailBuilder.append(s" LIMIT ${l.value}"))
    offset.foreach(o => tailBuilder.append(s" OFFSET ${o.value}"))
    val tailStr = tailBuilder.toString()

    val frag: Frag = if (filters.isEmpty) {
      Frag.literal(head.toString() + tailStr)
    } else {
      val filterFrags: Vector[Frag] = filters.map { f =>
        if (f.operator.equalsIgnoreCase("IN")) {
          val inVals  = dbValuesForIn(f.param)
          val inParts = IndexedSeq(s"${f.column.qualified} IN (") ++
            IndexedSeq.fill(inVals.size - 1)(", ") ++ IndexedSeq(")")
          Frag(inParts, inVals)
        } else {
          Frag(IndexedSeq(s"${f.column.qualified} ${f.operator} ", ""), IndexedSeq(f.param))
        }
      }
      val combined = filterFrags.reduce((a, b) => a ++ Frag.literal(" AND ") ++ b)
      val base     = Frag.literal(head.toString()) ++ Frag.literal(" WHERE ") ++ combined
      if (tailStr.isEmpty) base else base ++ Frag.literal(tailStr)
    }

    val st = SqlStatement(
      source = SqlStatement.Source(source.name, sourceAlias),
      joins = joins,
      filters = filters,
      groupBy = groupBy,
      orderBy = orderBy,
      limit = limit,
      offset = offset,
      frag = frag
    )
    (st, frag)
  }

  @scala.annotation.unused
  private def buildStatement(dialect: SqlDialect): SqlStatement = build(dialect)._1
}

object SqlQuery {

  @scala.annotation.nowarn("cat=deprecation")
  def from[A](table: Table[A]): SqlQuery[A] =
    new SqlQuery[A](table, "t0", Vector.empty, Vector.empty, None, Vector.empty, None, None, Map("t0" -> table.columns))

  private[sql] def typeLabel(v: DbValue): String = v match {
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
}
