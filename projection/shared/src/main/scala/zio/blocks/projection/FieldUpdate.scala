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

/**
 * A change to one field of a row, as part of a [[ProjectionAction.Update]].
 *
 * The field is named by its column name, in snake_case. There are two ways to
 * build one:
 *   - '''String-based''' (direct): `FieldUpdate.Increment("user_count", 1L)`
 *   - '''Selector-based''' (macro): `FieldUpdate.increment[User](_.userCount)`
 *     which maps `userCount` to `user_count` via `SqlNameMapper.SnakeCase`
 *
 * {{{
 * import zio.blocks.chunk.Chunk
 * import zio.blocks.schema.Modifier
 *
 * case class DailyStats(@Modifier.id day: String, userCount: Long, peak: Long)
 *
 * val onSignUp: Chunk[FieldUpdate] = Chunk(
 *   FieldUpdate.increment[DailyStats](_.userCount),
 *   FieldUpdate.maxValue[DailyStats](_.peak, 42L)
 * )
 * // Chunk(Increment(user_count,1),Max(peak,42))
 * }}}
 *
 * A [[ProjectionStore]] applies the changes of one `Update` in order, and
 * creates the row first when it is missing.
 *
 * @see
 *   [[https://zioblocks.com/reference/projection/projection/ Projection]]
 */
enum FieldUpdate {

  /**
   * Replaces the field's value with `value`. A SQLite store writes the value as
   * it is, without checking it against the field's type.
   */
  case Set(field: String, value: Any) extends FieldUpdate

  /** Adds `by` to the field. A SQLite store treats a `NULL` value as `0`. */
  case Increment(field: String, by: Long) extends FieldUpdate

  /**
   * Subtracts `by` from the field. A SQLite store treats a `NULL` value as `0`.
   */
  case Decrement(field: String, by: Long) extends FieldUpdate

  /**
   * Keeps the larger of the field's value and `value`. A SQLite store treats a
   * `NULL` value as `value`.
   */
  case Max(field: String, value: Long) extends FieldUpdate

  /**
   * Keeps the smaller of the field's value and `value`. A SQLite store treats a
   * `NULL` value as `value`.
   */
  case Min(field: String, value: Long) extends FieldUpdate
}

/**
 * Builds [[FieldUpdate]]s, with the field picked by a selector checked at
 * compile time, or named by a string.
 */
object FieldUpdate {

  // ---------------------------------------------------------------------------
  // Selector-based macro factory methods
  // ---------------------------------------------------------------------------

  /**
   * Replaces the value of the selected field. The field is chosen with a
   * selector such as `_.userCount`, and the name of the last member it reads is
   * mapped to its `snake_case` column name at compile time (see
   * [[FieldSelectorMacros]] for what a selector may be).
   *
   * {{{
   * case class Stats(day: String, peak: Long)
   *
   * FieldUpdate.setValue[Stats](_.peak, 10L) // Set(peak,10)
   * }}}
   */
  inline def setValue[A](inline selector: A => Any, value: Any): FieldUpdate.Set =
    ${ FieldSelectorMacros.setValueImpl[A]('selector, 'value) }

  /**
   * Adds `by` to the selected field, atomically in the store. A missing row is
   * created first, with `0` in the counter, so incrementing a counter that does
   * not exist yet gives `by`.
   *
   * {{{
   * case class Stats(day: String, userCount: Long)
   *
   * FieldUpdate.increment[Stats](_.userCount)     // Increment(user_count,1)
   * FieldUpdate.increment[Stats](_.userCount, 5L) // Increment(user_count,5)
   * }}}
   */
  inline def increment[A](inline selector: A => Any, by: Long = 1L): FieldUpdate.Increment =
    ${ FieldSelectorMacros.incrementImpl[A]('selector, 'by) }

  /** Subtracts `by` from the selected field, atomically in the store. */
  inline def decrement[A](inline selector: A => Any, by: Long = 1L): FieldUpdate.Decrement =
    ${ FieldSelectorMacros.decrementImpl[A]('selector, 'by) }

  /**
   * Keeps the larger of the stored value and `value` for the selected field.
   */
  inline def maxValue[A](inline selector: A => Any, value: Long): FieldUpdate.Max =
    ${ FieldSelectorMacros.maxValueImpl[A]('selector, 'value) }

  /**
   * Keeps the smaller of the stored value and `value` for the selected field.
   */
  inline def minValue[A](inline selector: A => Any, value: Long): FieldUpdate.Min =
    ${ FieldSelectorMacros.minValueImpl[A]('selector, 'value) }

  // ---------------------------------------------------------------------------
  // Raw field-name helpers (no macro, for runtime use)
  // ---------------------------------------------------------------------------

  /** Same as `Set(field, value)`, using a column name written out in full. */
  def apply(field: String, value: Any): FieldUpdate.Set =
    FieldUpdate.Set(field, value)

  /**
   * Same as `Increment(field, by)`, using a column name written out in full.
   */
  def increment(field: String, by: Long): FieldUpdate.Increment =
    FieldUpdate.Increment(field, by)

  /**
   * Same as `Decrement(field, by)`, using a column name written out in full.
   */
  def decrement(field: String, by: Long): FieldUpdate.Decrement =
    FieldUpdate.Decrement(field, by)

  /** Same as `Max(field, value)`, using a column name written out in full. */
  def maxValue(field: String, value: Long): FieldUpdate.Max =
    FieldUpdate.Max(field, value)

  /** Same as `Min(field, value)`, using a column name written out in full. */
  def minValue(field: String, value: Long): FieldUpdate.Min =
    FieldUpdate.Min(field, value)
}
