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

package zio.blocks.schema.json

import scala.compiletime.error

trait NonNegativeIntCompanionVersionSpecific {

  /**
   * Creates a NonNegativeInt from a literal integer value, validated at compile
   * time. Use this when the value is known at compile time for zero runtime
   * overhead.
   *
   * {{{
   * val min = NonNegativeInt.literal(3)   // Compiles - returns NonNegativeInt directly
   * val bad = NonNegativeInt.literal(-1)  // Compile error!
   * }}}
   */
  inline def literal(inline n: Int): NonNegativeInt =
    inline if (n >= 0) NonNegativeInt.unsafe(n)
    else error("NonNegativeInt requires n >= 0")
}
