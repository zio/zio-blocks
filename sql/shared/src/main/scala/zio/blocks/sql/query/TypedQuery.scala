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

import zio.blocks.chunk.Chunk
import zio.blocks.maybe.Maybe
import zio.blocks.sql.{DbCodec, DbCon, Frag, SqlDialect}
import zio.blocks.streams.Stream

/**
 * Typed query with codec-driven projection.
 *
 * Holds the underlying [[SqlQuery]] IR plus an ordered projection list whose
 * SQL rendering is alias-qualified and whose column count matches `codec`.
 * Execution delegates to the existing [[Frag]] extension family; no custom JDBC
 * loops or result transformers are introduced.
 */
final class TypedQuery[T] private[query] (
  val underlying: SqlQuery[_],
  val projections: Vector[Expr[_]],
  val codec: DbCodec[T]
) {

  def toFrag(dialect: SqlDialect): Frag =
    QueryRenderer.renderTyped(underlying, projections, codec, dialect)

  def sql(dialect: SqlDialect): String = toFrag(dialect).sql(dialect)

  // Execution delegates to Frag extensions — ambient DbCon/DbTx matches Frag.
  def run(using con: DbCon): List[T] =
    toFrag(con.dialect).query[T](using con, codec)

  def query(using con: DbCon): List[T] = run

  def queryOne(using con: DbCon): Maybe[T] =
    toFrag(con.dialect).queryOne[T](using con, codec)

  def queryLimit(limit: Int)(using con: DbCon): List[T] =
    toFrag(con.dialect).queryLimit[T](limit)(using con, codec)

  def queryStream(using con: DbCon): Stream[Throwable, Chunk[T]] =
    toFrag(con.dialect).queryStream[T](using con, codec)

  def queryChunked(chunkSize: Int)(using con: DbCon): Stream[Throwable, Chunk[T]] =
    toFrag(con.dialect).queryChunked[T](chunkSize)(using con, codec)

  // Preserve query-building after select without losing projection state.

  def where(expr: Expr[Boolean]): TypedQuery[T] =
    new TypedQuery(underlying.where(expr), projections, codec)

  def filter(expr: Expr[Boolean]): TypedQuery[T] = where(expr)

  def groupBy(expr: Expr[_], exprs: Expr[_]*): TypedQuery[T] =
    new TypedQuery(underlying.groupBy(expr, exprs*), projections, codec)

  def having(expr: Expr[Boolean]): TypedQuery[T] =
    new TypedQuery(underlying.having(expr), projections, codec)

  def orderBy(col: String, dir: SortOrder = SortOrder.Asc): TypedQuery[T] =
    new TypedQuery(underlying.orderBy(col, dir), projections, codec)

  def orderByMany(cols: OrderBy*): TypedQuery[T] =
    new TypedQuery(underlying.orderByMany(cols*), projections, codec)

  def limit(n: Int): TypedQuery[T] =
    new TypedQuery(underlying.limit(n), projections, codec)

  def offset(n: Int): TypedQuery[T] =
    new TypedQuery(underlying.offset(n), projections, codec)

  def innerJoin[From, To](rel: Rel[From, To]): TypedQuery[T] =
    new TypedQuery(underlying.innerJoin(rel), projections, codec)

  def leftJoin[From, To](rel: Rel[From, To]): TypedQuery[T] =
    new TypedQuery(underlying.leftJoin(rel), projections, codec)

  def join[From, To](rel: Rel[From, To]): TypedQuery[T] =
    new TypedQuery(underlying.join(rel), projections, codec)

  def joinLeft[From, To](rel: Rel[From, To]): TypedQuery[T] =
    new TypedQuery(underlying.joinLeft(rel), projections, codec)
}

object TypedQuery {
  private[query] def create[T](query: SqlQuery[?], projections: Vector[Expr[_]], codec: DbCodec[T]): TypedQuery[T] =
    new TypedQuery(query, projections, codec)
}
