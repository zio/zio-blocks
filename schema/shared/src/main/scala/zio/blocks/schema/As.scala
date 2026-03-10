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

package zio.blocks.schema

/**
 * Bidirectional conversion between types A and B.
 *
 * As[A, B] extends Into[A, B], so it can be used anywhere an Into is expected.
 * Use `reverse` to get an As[B, A] that reverses the direction.
 *
 * For As[A, B] to be derivable, the bidirectional conversion must be
 * compatible:
 *
 * Compatibility Rules:
 *   - Field mappings must be consistent: The same field correspondence in both
 *     directions
 *   - Coercions must be invertible with runtime validation (Int ↔ Long, Float ↔
 *     Double, etc.)
 *   - Optional fields: Can add optional fields in one direction (becomes None
 *     in reverse)
 *   - Default values: Allowed only on fields that exist in both types. Fields
 *     with defaults that don't exist in the other type break round-trip
 *     guarantee.
 *   - Collection types: Can convert between different collection types (may be
 *     lossy for Set/List conversions)
 */
trait As[A, B] extends Into[A, B] {
  def from(input: B): Either[SchemaError, A]

  /** Reverses the direction of this As, producing an As[B, A]. */
  def reverse: As[B, A] = {
    val self = this
    new As[B, A] {
      def into(input: B): Either[SchemaError, A] = self.from(input)
      def from(input: A): Either[SchemaError, B] = self.into(input)
    }
  }
}

object As extends AsVersionSpecific with AsLowPriorityImplicits {

  /** Creates an As[A, B] from two Into instances. */
  def apply[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] = new As[A, B] {
    def into(input: A): Either[SchemaError, B] = intoAB.into(input)
    def from(input: B): Either[SchemaError, A] = intoBA.into(input)
  }

  def apply[A, B](implicit ev: As[A, B]): As[A, B] = ev
}

trait AsLowPriorityImplicits {

  implicit def reverseInto[A, B](implicit as: As[A, B]): Into[B, A] =
    (input: B) => as.from(input)
}
