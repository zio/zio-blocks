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

import zio.blocks.scope.internal.{MacroCore, WireCodeGen}
import zio.blocks.scope.internal.WireCodeGen.WireKind
import scala.quoted.*

private[scope] object ScopeMacros {

  def sharedImpl[T: Type](using Quotes): Expr[Wire.Shared[?, T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
      MacroCore.abortNotAClass(tpe.show)
    }

    val (_, wireExpr) = WireCodeGen.deriveWire[T](WireKind.Shared)
    wireExpr.asExprOf[Wire.Shared[?, T]]
  }

  def uniqueImpl[T: Type](using Quotes): Expr[Wire.Unique[?, T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
      MacroCore.abortNotAClass(tpe.show)
    }

    val (_, wireExpr) = WireCodeGen.deriveWire[T](WireKind.Unique)
    wireExpr.asExprOf[Wire.Unique[?, T]]
  }
}
