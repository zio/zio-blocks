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

/** Adds `literal` to the [[PositiveNumber]] companion. */
trait PositiveNumberCompanionVersionSpecific {

  /**
   * Returns a [[PositiveNumber]] for an `Int` literal, checked at compile time:
   * a literal below 1, or an argument that is not a literal, does not compile.
   * Use `PositiveNumber.apply` for runtime values.
   *
   * On Scala 2.13, the macro calls the package-private constructor, so it
   * compiles only inside the `zio.blocks.schema.json` package (a known bug).
   *
   * {{{
   * // scala-3-only
   * import zio.blocks.schema.json.PositiveNumber
   *
   * val mult: PositiveNumber = PositiveNumber.literal(5) // compiles
   * // PositiveNumber.literal(0)                         // does not compile
   * }}}
   */
  inline def literal(inline n: Int): PositiveNumber =
    inline if (n > 0) PositiveNumber.unsafe(BigDecimal(n))
    else error("PositiveNumber requires n > 0")
}
