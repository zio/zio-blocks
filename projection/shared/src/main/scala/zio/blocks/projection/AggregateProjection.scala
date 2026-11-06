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

package zio.blocks.projection

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{Modifier, Schema}

/**
 * Helpers for global aggregate projections.
 *
 * A global projection keeps all its rows in one store, `global/<name>.db` when
 * the engine creates it, and applies the events of every source to it, whatever
 * their routing. The entity ID an event is recorded under picks the row, so it
 * acts as the grouping key (for example a date string `"2025-08-26"`).
 *
 * On a [[SQLiteProjectionStore]], `Increment`, `Decrement`, `Max` and `Min`
 * [[FieldUpdate]]s become single SQL `UPDATE`s (`COALESCE(col, 0) + ?`,
 * `MAX(...)`, `MIN(...)`), and a missing row is created with `INSERT OR IGNORE`
 * before them, all in one transaction. `Upsert` uses
 * `INSERT ... ON CONFLICT DO UPDATE`, and falls back to `INSERT OR REPLACE`
 * when SQLite rejects it.
 */
object AggregateProjection {

  /**
   * Same as `Projection.global[A](name)`: an empty global projection, to which
   * sources and handlers are added as usual. `bindings` is ignored.
   */
  def globalWithCounters[A: Schema](
    name: String,
    @annotation.nowarn("msg=unused") bindings: List[(String, Any => String)] = Nil
  ): Projection[A] =
    Projection.global[A](name)

  /**
   * A sample row type for daily statistics, one row per date: the date is the
   * ID, marked with `@Modifier.id`.
   */
  final case class DailyStats(
    @Modifier.id date: String,
    userCount: Int,
    repoCount: Int,
    peak: Long
  )

  /** The schema of [[DailyStats]]. */
  object DailyStats {

    /** The derived schema of [[DailyStats]]. */
    implicit val schema: Schema[DailyStats] = Schema.derived[DailyStats]
  }

  /** Same as `FieldUpdate.Increment(field, by)`; `by` defaults to `1`. */
  def inc(field: String, by: Long = 1L): FieldUpdate.Increment = FieldUpdate.Increment(field, by)

  /** Same as `FieldUpdate.Decrement(field, by)`; `by` defaults to `1`. */
  def dec(field: String, by: Long = 1L): FieldUpdate.Decrement = FieldUpdate.Decrement(field, by)

  /** Same as `FieldUpdate.Max(field, value)`. */
  def max(field: String, value: Long): FieldUpdate.Max = FieldUpdate.Max(field, value)

  /** Same as `FieldUpdate.Min(field, value)`. */
  def min(field: String, value: Long): FieldUpdate.Min = FieldUpdate.Min(field, value)

  /** Same as `FieldUpdate.Set(field, value)`. */
  def set(field: String, value: Any): FieldUpdate.Set = FieldUpdate.Set(field, value)

  /**
   * Puts `updates` in a `Chunk`, in order, for `HandlerBuilder.aggregate`. Any
   * kind of [[FieldUpdate]] can be in it.
   */
  def counters(updates: FieldUpdate*): Chunk[FieldUpdate] = Chunk(updates: _*)
}
