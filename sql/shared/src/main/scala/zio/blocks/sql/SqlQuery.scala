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

import SqlStatement._

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

  def join[B](
    other: Table[B],
    leftColumn: String,
    rightColumn: String,
    kind: JoinKind = JoinKind.Inner
  ): SqlQuery[A] = {
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
    val gb = GroupBy(columns.toVector)
    new SqlQuery(source, sourceAlias, joins, filters, Some(gb), orderBy, limit, offset, columnsByAlias)
  }

  def groupBy(table: Table[_], columns: String*): SqlQuery[A] = {
    val alias = aliasFor(table)
    groupBy(columns.map(c => ColumnRef(alias, c)): _*)
  }

  def orderBy(column: ColumnRef, direction: OrderDirection = OrderDirection.Asc): SqlQuery[A] = {
    val ob = OrderBy(column, direction)
    new SqlQuery(source, sourceAlias, joins, filters, groupBy, orderBy :+ ob, limit, offset, columnsByAlias)
  }

  def orderBy(table: Table[_], column: String, direction: OrderDirection): SqlQuery[A] = {
    val alias = aliasFor(table)
    orderBy(ColumnRef(alias, column), direction)
  }

  def limit(n: Int): SqlQuery[A] =
    new SqlQuery(source, sourceAlias, joins, filters, groupBy, orderBy, Some(Limit(n)), offset, columnsByAlias)

  def offset(n: Int): SqlQuery[A] =
    new SqlQuery(source, sourceAlias, joins, filters, groupBy, orderBy, limit, Some(Offset(n)), columnsByAlias)

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
    val allTables: Vector[(String, IndexedSeq[String], String)] =
      Vector((source.name, columnsByAlias(sourceAlias), sourceAlias)) ++
        joins.map(j => (j.table, columnsByAlias.getOrElse(j.alias, IndexedSeq.empty), j.alias))

    val selectList = allTables.flatMap { case (_, cols, alias) =>
      cols.map(c => s"$alias.$c")
    }.mkString(", ")

    val parts  = scala.collection.mutable.ArrayBuffer[String]()
    val params = scala.collection.mutable.ArrayBuffer[DbValue]()

    val head = new StringBuilder
    head.append(s"SELECT $selectList FROM ${source.name} $sourceAlias")
    for (j <- joins) {
      val kindStr = j.kind match {
        case JoinKind.Inner => "INNER JOIN"
        case JoinKind.Left  => "LEFT JOIN"
      }
      head.append(s" $kindStr ${j.table} ${j.alias} ON ${j.onLeft.qualified} = ${j.onRight.qualified}")
    }

    if (filters.isEmpty) {
      val tail = new StringBuilder
      groupBy.foreach(gb => tail.append(s" GROUP BY ${gb.columns.map(_.qualified).mkString(", ")}"))
      if (orderBy.nonEmpty) {
        val obStr = orderBy.map { ob =>
          val dir = ob.direction match {
            case OrderDirection.Asc  => "ASC"
            case OrderDirection.Desc => "DESC"
          }
          s"${ob.column.qualified} $dir"
        }.mkString(", ")
        tail.append(s" ORDER BY $obStr")
      }
      limit.foreach(l => tail.append(s" LIMIT ${l.value}"))
      offset.foreach(o => tail.append(s" OFFSET ${o.value}"))
      head.append(tail.toString())
      parts += head.toString()
    } else {
      val preWhere = head.toString() + " WHERE "
      var current  = new StringBuilder(preWhere)

      filters.zipWithIndex.foreach { case (f, idx) =>
        if (idx > 0) current.append(" AND ")
        current.append(s"${f.column.qualified} ${f.operator} ")
        parts += current.toString()
        params += f.param
        current = new StringBuilder()
      }
      val tail = new StringBuilder
      groupBy.foreach(gb => tail.append(s" GROUP BY ${gb.columns.map(_.qualified).mkString(", ")}"))
      if (orderBy.nonEmpty) {
        val obStr = orderBy.map { ob =>
          val dir = ob.direction match {
            case OrderDirection.Asc  => "ASC"
            case OrderDirection.Desc => "DESC"
          }
          s"${ob.column.qualified} $dir"
        }.mkString(", ")
        tail.append(s" ORDER BY $obStr")
      }
      limit.foreach(l => tail.append(s" LIMIT ${l.value}"))
      offset.foreach(o => tail.append(s" OFFSET ${o.value}"))
      current.append(tail.toString())
      parts += current.toString()
    }

    val frag = Frag(parts.toIndexedSeq, params.toIndexedSeq)
    val st   = SqlStatement(
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
