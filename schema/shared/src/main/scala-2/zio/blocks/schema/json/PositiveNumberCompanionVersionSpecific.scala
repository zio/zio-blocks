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

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** Adds `literal` to the [[PositiveNumber]] companion. */
trait PositiveNumberCompanionVersionSpecific {

  /**
   * Returns a [[PositiveNumber]] for an `Int` literal, checked at compile time: a literal below 1,
   * or an argument that is not a literal, does not compile. Use
   * `PositiveNumber.apply` for runtime values.
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
  def literal(n: Int): PositiveNumber = macro PositiveNumberMacros.literalImpl
}

private[json] object PositiveNumberMacros {
  /** The macro behind `PositiveNumber.literal`. */
  def literalImpl(c: blackbox.Context)(n: c.Expr[Int]): c.Expr[PositiveNumber] = {
    import c.universe._

    n.tree match {
      case Literal(Constant(value: Int)) =>
        if (value > 0) {
          c.Expr[PositiveNumber](q"new _root_.zio.blocks.schema.json.PositiveNumber(BigDecimal($value))")
        } else {
          c.abort(c.enclosingPosition, s"PositiveNumber requires n > 0, got $value")
        }
      case _ =>
        c.abort(
          c.enclosingPosition,
          "PositiveNumber.literal requires a literal Int. Use PositiveNumber.apply for runtime values."
        )
    }
  }
}
