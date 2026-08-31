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
 * Structured, inspectable representation of a `zio.blocks.sql.query.SqlQuery`
 * for a specific dialect.
 *
 * Produced by `zio.blocks.sql.query.SqlQuery#statement`; mirrors the same
 * joins, grouping and pagination as the query but decomposed into typed fields.
 * The original [[Frag]] is retained as [[frag]] for re-rendering or execution,
 * and `statement.frag.params` aligns with the `?N` placeholders shown by
 * `SqlQuery#explain`. This is the stable inspection view for the typed
 * relational IR; checked raw SQL uses [[Frag]] directly.
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

  /**
   * Filter inspection that preserves the exact predicate Frag and its
   * operator/column metadata honestly. `predicate` is the alias-qualified
   * `Frag` as rendered by `QueryRenderer` (with `?` placeholders and
   * `predicate.params` carrying the bound values). `column` is populated when
   * the filter is a simple relational / IN / LIKE on a single column; otherwise
   * it is `None` and the full predicate must be inspected. `operator` is the
   * honest SQL operator (e.g. "=", "IN", "LIKE", "AND", "OR").
   */
  final case class Filter(predicate: Frag, column: Option[ColumnRef], operator: String) {
    def params: IndexedSeq[DbValue] = predicate.params
    def param: Option[DbValue]      = predicate.params.headOption
  }

  object Filter {
    def apply(column: ColumnRef, operator: String, param: DbValue): Filter =
      Filter(
        Frag(IndexedSeq(s"""${column.tableAlias}."${column.column}" $operator """, ""), IndexedSeq(param)),
        Some(column),
        operator
      )
  }

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
