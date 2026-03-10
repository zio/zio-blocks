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

package into

import zio.blocks.schema.Into
import util.ShowExpr.show

// Numeric widening is always lossless — these always return Right.
object IntoWideningExample extends App {
  show(Into[Byte, Int].into(42.toByte))
  show(Into[Int, Long].into(100))
  show(Into[Long, Double].into(9999999L))
  show(Into[Float, Double].into(3.14f))
}

// Numeric narrowing validates the value at runtime.
// Returns Right when the value fits; Left when it is out of range
// or cannot be represented precisely.
object IntoNarrowingExample extends App {
  show(Into[Long, Int].into(42L))
  show(Into[Long, Int].into(Long.MaxValue))
  show(Into[Double, Float].into(1.5))
  show(Into[Double, Int].into(3.14))
}
