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

package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * A pure-data description of a primitive value coercion. No functions; every
 * case is fully serializable.
 */
sealed trait FieldTransform

object FieldTransform {

  /** Widen an [[Int]] value to [[Long]]. */
  case object IntToLong extends FieldTransform

  /** Truncate a [[Long]] value to [[Int]] (may lose precision). */
  case object LongToInt extends FieldTransform

  /** Widen an [[Int]] value to [[Double]]. */
  case object IntToDouble extends FieldTransform

  /** Widen a [[Long]] value to [[Double]]. */
  case object LongToDouble extends FieldTransform

  /**
   * Format an [[Int]] as a [[String]] using the given `radix` (default 10).
   */
  final case class IntToString(radix: Int) extends FieldTransform

  /**
   * Parse a [[String]] as an [[Int]] using the given `radix` (default 10).
   */
  final case class StringToInt(radix: Int) extends FieldTransform

  /**
   * Replace the target value entirely with the fixed [[DynamicValue]]
   * `value`, ignoring the original value.
   */
  final case class Constant(value: DynamicValue) extends FieldTransform
}
