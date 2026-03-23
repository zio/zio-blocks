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
  def literal(n: Int): NonNegativeInt = macro NonNegativeIntMacros.literalImpl
}

private[json] object NonNegativeIntMacros {
  def literalImpl(c: blackbox.Context)(n: c.Expr[Int]): c.Expr[NonNegativeInt] = {
    import c.universe._

    n.tree match {
      case Literal(Constant(value: Int)) =>
        if (value >= 0) {
          c.Expr[NonNegativeInt](q"new _root_.zio.blocks.schema.json.NonNegativeInt($value)")
        } else {
          c.abort(c.enclosingPosition, s"NonNegativeInt requires n >= 0, got $value")
        }
      case _ =>
        c.abort(
          c.enclosingPosition,
          "NonNegativeInt.literal requires a literal Int. Use NonNegativeInt.apply for runtime values."
        )
    }
  }
}
