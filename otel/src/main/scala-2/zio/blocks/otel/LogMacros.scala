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

import scala.reflect.macros.blackbox

private[otel] object LogMacros {

  def traceImpl(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] =
    logImpl(c)(message, c.universe.reify(Severity.Trace))

  def debugImpl(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] =
    logImpl(c)(message, c.universe.reify(Severity.Debug))

  def infoImpl(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] =
    logImpl(c)(message, c.universe.reify(Severity.Info))

  def warnImpl(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] =
    logImpl(c)(message, c.universe.reify(Severity.Warn))

  def errorImpl(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] =
    logImpl(c)(message, c.universe.reify(Severity.Error))

  def fatalImpl(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] =
    logImpl(c)(message, c.universe.reify(Severity.Fatal))

  private def logImpl(
    c: blackbox.Context
  )(message: c.Expr[String], severity: c.Expr[Severity]): c.Expr[Unit] = {
    import c.universe._

    val pos      = c.enclosingPosition
    val filePath = Literal(Constant(pos.source.path))
    val lineNo   = Literal(Constant(pos.line))

    val methodName = Literal(Constant(findEnclosingMethod(c)))
    val namespace  = Literal(Constant(findEnclosingClass(c)))

    val self = c.prefix.tree

    c.Expr[Unit](q"""
      {
        val state = _root_.zio.blocks.otel.GlobalLogState.get()
        if (state != null && $severity.number >= state.minSeverity) {
          $self.emit(
            $severity,
            $message,
            _root_.zio.blocks.otel.SourceLocation($filePath, $namespace, $methodName, $lineNo)
          )
        }
      }
    """)
  }

  private def findEnclosingMethod(c: blackbox.Context): String = {
    var owner = c.internal.enclosingOwner
    while (owner != c.universe.NoSymbol) {
      if (owner.isMethod && !owner.isConstructor) {
        return owner.name.decodedName.toString
      }
      owner = owner.owner
    }
    "<unknown>"
  }

  private def findEnclosingClass(c: blackbox.Context): String = {
    var owner = c.internal.enclosingOwner
    while (owner != c.universe.NoSymbol) {
      if (owner.isClass) {
        return owner.fullName
      }
      owner = owner.owner
    }
    "<unknown>"
  }
}
