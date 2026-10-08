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
 * A sealed enumeration of field-level update operations.
 *
 * Each variant describes how a single field should be modified during an
 * `Update` action on a `ProjectionAction`.
 *
 * Provides two APIs:
 *   - '''String-based''' (direct): `FieldUpdate.Increment("user_count", 1L)`
 *   - '''Selector-based''' (macro): `FieldUpdate.increment[User](_.userCount)`
 *     which maps `userCount` → `user_count` via `SqlNameMapper.SnakeCase`
 */
enum FieldUpdate {

  case Set(field: String, value: Any)     extends FieldUpdate
  case Increment(field: String, by: Long) extends FieldUpdate
  case Decrement(field: String, by: Long) extends FieldUpdate
  case Max(field: String, value: Long)    extends FieldUpdate
  case Min(field: String, value: Long)    extends FieldUpdate
}

object FieldUpdate {

  // ---------------------------------------------------------------------------
  // Selector-based macro factory methods
  // ---------------------------------------------------------------------------

  /**
   * Replaces the value of the selected field. The field is chosen with a
   * selector such as `_.userCount`, checked at compile time, and mapped to its
   * `snake_case` column name.
   *
   * {{{
   * FieldUpdate.setValue[Stats](_.peak, 10L)   // Set("peak", 10L)
   * }}}
   */
  inline def setValue[A](inline selector: A => Any, value: Any): FieldUpdate.Set =
    ${ FieldSelectorMacros.setValueImpl[A]('selector, 'value) }

  /**
   * Adds `by` to the selected field, atomically in the store. A missing row is
   * created first, so incrementing a counter that does not exist yet gives
   * `by`.
   *
   * {{{
   * FieldUpdate.increment[Stats](_.userCount)       // Increment("user_count", 1)
   * FieldUpdate.increment[Stats](_.userCount, 5L)   // Increment("user_count", 5)
   * }}}
   */
  inline def increment[A](inline selector: A => Any, by: Long = 1L): FieldUpdate.Increment =
    ${ FieldSelectorMacros.incrementImpl[A]('selector, 'by) }

  /** Subtracts `by` from the selected field, atomically in the store. */
  inline def decrement[A](inline selector: A => Any, by: Long = 1L): FieldUpdate.Decrement =
    ${ FieldSelectorMacros.decrementImpl[A]('selector, 'by) }

  /** Keeps the larger of the stored value and `value` for the selected field. */
  inline def maxValue[A](inline selector: A => Any, value: Long): FieldUpdate.Max =
    ${ FieldSelectorMacros.maxValueImpl[A]('selector, 'value) }

  /** Keeps the smaller of the stored value and `value` for the selected field. */
  inline def minValue[A](inline selector: A => Any, value: Long): FieldUpdate.Min =
    ${ FieldSelectorMacros.minValueImpl[A]('selector, 'value) }

  // ---------------------------------------------------------------------------
  // Raw field-name helpers (no macro, for runtime use)
  // ---------------------------------------------------------------------------

  /** Same as `Set(field, value)`, using a column name written out in full. */
  def apply(field: String, value: Any): FieldUpdate.Set =
    FieldUpdate.Set(field, value)

  def increment(field: String, by: Long): FieldUpdate.Increment =
    FieldUpdate.Increment(field, by)

  def decrement(field: String, by: Long): FieldUpdate.Decrement =
    FieldUpdate.Decrement(field, by)

  def maxValue(field: String, value: Long): FieldUpdate.Max =
    FieldUpdate.Max(field, value)

  def minValue(field: String, value: Long): FieldUpdate.Min =
    FieldUpdate.Min(field, value)
}
