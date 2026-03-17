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

package zio.blocks.otel

import scala.quoted.*

private[otel] object LogMacros {

  def logImpl(
    self: Expr[log.type],
    message: Expr[String],
    severity: Expr[Severity]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    val pos = Position.ofMacroExpansion

    val filePath   = Expr(pos.sourceFile.path)
    val lineNumber = Expr(pos.startLine + 1)

    val methodName = Expr(findEnclosingMethod(Symbol.spliceOwner))
    val namespace  = Expr(findEnclosingClass(Symbol.spliceOwner))

    '{
      val state = GlobalLogState.get()
      if (state != null && $severity.number >= state.minSeverity) {
        $self.emit(
          $severity,
          $message,
          SourceLocation($filePath, $namespace, $methodName, $lineNumber)
        )
      }
    }
  }

  private def findEnclosingMethod(using Quotes)(sym: quotes.reflect.Symbol): String = {
    import quotes.reflect.*
    var current = sym
    while (current != Symbol.noSymbol) {
      if (current.isDefDef && !current.name.startsWith("$") && current.name != "<init>") {
        return current.name
      }
      current = current.owner
    }
    "<unknown>"
  }

  private def findEnclosingClass(using Quotes)(sym: quotes.reflect.Symbol): String = {
    import quotes.reflect.*
    var current = sym
    while (current != Symbol.noSymbol) {
      if (current.isClassDef) {
        return current.fullName
      }
      current = current.owner
    }
    "<unknown>"
  }
}
