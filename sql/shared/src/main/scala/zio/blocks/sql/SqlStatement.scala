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

/**
 * Structured, inspectable representation of a `SqlQuery` for a specific
 * dialect.
 *
 * Produced by [[SqlQuery.statement]] / `SqlQuery#build`; mirrors the same
 * joins, filters, grouping and pagination as the query but decomposed into
 * typed fields. The original [[Frag]] is retained as [[frag]] for re-rendering
 * or execution, and `statement.frag.params` aligns with the `?N` placeholders
 * shown by [[SqlQuery.explain]].
 */
final case class SqlStatement(
  source: SqlStatement.Source,
  joins: Vector[SqlStatement.Join],
  filters: Vector[SqlStatement.Filter],
  groupBy: Option[SqlStatement.GroupBy],
  orderBy: Vector[SqlStatement.OrderBy],
  limit: Option[SqlStatement.Limit],
  offset: Option[SqlStatement.Offset],
  frag: Frag
) {
  def toFrag: Frag = frag
}

object SqlStatement {

  final case class ColumnRef(tableAlias: String, column: String) {
    def qualified: String = s"$tableAlias.$column"
  }

  sealed trait JoinKind
  object JoinKind {
    case object Inner extends JoinKind
    case object Left  extends JoinKind
  }

  final case class Source(table: String, alias: String)

  final case class Join(
    kind: JoinKind,
    table: String,
    alias: String,
    onLeft: ColumnRef,
    onRight: ColumnRef
  )

  final case class Filter(column: ColumnRef, operator: String, param: DbValue)

  final case class GroupBy(columns: Vector[ColumnRef])

  final case class OrderBy(column: ColumnRef, direction: OrderDirection)

  sealed trait OrderDirection
  object OrderDirection {
    case object Asc  extends OrderDirection
    case object Desc extends OrderDirection
  }

  final case class Limit(value: Int)

  final case class Offset(value: Int)
}
