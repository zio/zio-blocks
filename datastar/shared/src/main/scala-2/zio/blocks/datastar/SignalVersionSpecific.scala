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

package zio.blocks.datastar

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

private[datastar] trait SignalVersionSpecific {
  def apply[A](name: String): Signal[A] = macro SignalMacros.applyImpl[A]
}

private[datastar] object SignalMacros {
  def applyImpl[A: c.WeakTypeTag](c: blackbox.Context)(name: c.Expr[String]): c.Expr[Signal[A]] = {
    import c.universe._

    val aTpe = weakTypeOf[A]

    name.tree match {
      case Literal(Constant(signalName: String)) =>
        if (!Signal.isValidName(signalName)) {
          c.abort(c.enclosingPosition, Signal.invalidNameMessage(signalName))
        }
        c.Expr[Signal[A]](q"_root_.zio.blocks.datastar.Signal.unsafeApply[$aTpe]($signalName)")
      case _                                   =>
        c.Expr[Signal[A]](q"_root_.zio.blocks.datastar.Signal.checkedApply[$aTpe]($name)")
    }
  }
}
