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

package zio.blocks.scope

import scala.quoted.*
import zio.blocks.scope.internal.MacroCore

/**
 * Scala 3 macro implementation for Scope.leak.
 *
 * Emits a compiler warning via MacroCore.warnLeak and returns the unwrapped
 * value (using asInstanceOf which is sound since $[A] = A at runtime).
 */
private[scope] object LeakMacros {

  def leakImpl[A: Type](sa: Expr[Any], self: Expr[Scope])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    val sourceCode = sa.asTerm.pos.sourceCode.getOrElse(sa.show)
    val scopeName  = self.asTerm.tpe.widen.show

    MacroCore.warnLeak(sa.asTerm.pos, sourceCode, scopeName)

    '{ $sa.asInstanceOf[A] }
  }
}
