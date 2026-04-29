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

import scala.quoted.*

private[datastar] trait SignalVersionSpecific {
  inline def apply[A](inline name: String): Signal[A] =
    ${ SignalMacros.applyImpl[A]('name) }
}

private[datastar] object SignalMacros {
  def applyImpl[A: Type](name: Expr[String])(using Quotes): Expr[Signal[A]] =
    name.value match {
      case Some(signalName) =>
        if (!Signal.isValidName(signalName)) {
          quotes.reflect.report.errorAndAbort(Signal.invalidNameMessage(signalName))
        }
        '{ Signal.unsafeApply[A](${ Expr(signalName) }) }
      case None =>
        '{ Signal.checkedApply[A]($name) }
    }
}
