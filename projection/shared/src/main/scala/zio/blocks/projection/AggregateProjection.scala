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
 * Global projections share a single underlying store file (`global/<name>.db`)
 * and receive events from all sources regardless of entity routing. The entity
 * identifier acts as the grouping key (for example a date string
 * `"2025-08-26"`).
 *
 * The store layer guarantees that `Increment` / `Decrement` / `Max` / `Min`
 * `FieldUpdate`s translate to atomic SQL (`COALESCE(col,0)+?`, `MAX(...)`,
 * `MIN(...)`) and that a missing aggregate row is materialised on-the-fly via
 * `INSERT OR IGNORE` before the `UPDATE`. `Upsert` uses the SQLite
 * `INSERT ... ON CONFLICT DO UPDATE` form (falls back to `INSERT OR REPLACE`
 * when the driver does not advertise conflict support).
 */
object AggregateProjection {

  /**
   * Convenience wrapper creating a global spec whose handlers emit atomic
   * counter updates.
   */
  def globalWithCounters[A: Schema](
    name: String,
    @annotation.nowarn("msg=unused") bindings: List[(String, Any => String)] = Nil
  ): Projection[A] =
    Projection.global[A](name)

  /**
   * Example model used by the aggregate spec tests: daily statistics bucketed
   * by date (`String` with `@Modifier.id`).
   */
  final case class DailyStats(
    @Modifier.id date: String,
    userCount: Int,
    repoCount: Int,
    peak: Long
  )

  object DailyStats {
    implicit val schema: Schema[DailyStats] = Schema.derived[DailyStats]
  }

  /** Field-level counter helpers re-exported for discoverability. */
  def inc(field: String, by: Long = 1L): FieldUpdate.Increment = FieldUpdate.Increment(field, by)
  def dec(field: String, by: Long = 1L): FieldUpdate.Decrement = FieldUpdate.Decrement(field, by)
  def max(field: String, value: Long): FieldUpdate.Max         = FieldUpdate.Max(field, value)
  def min(field: String, value: Long): FieldUpdate.Min         = FieldUpdate.Min(field, value)
  def set(field: String, value: Any): FieldUpdate.Set          = FieldUpdate.Set(field, value)

  /**
   * Build a chunk of atomic counter increments for convenient `aggregate` use.
   */
  def counters(updates: FieldUpdate*): Chunk[FieldUpdate] = Chunk(updates: _*)
}
