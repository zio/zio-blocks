package zio.blocks.scope

import scala.quoted.*
import zio.blocks.scope.internal.MacroCore

/**
 * Scala 3 macro implementation for Scope.leak.
 *
 * Emits a compiler warning via MacroCore.warnLeak and returns the
 * unwrapped value (using asInstanceOf which is sound since $[A] = A at runtime).
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
