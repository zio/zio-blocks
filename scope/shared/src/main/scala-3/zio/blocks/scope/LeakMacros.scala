package zio.blocks.scope

import zio.blocks.scope.internal.MacroCore
import scala.quoted.*

private[scope] object LeakMacros {

  def leakImpl[A: Type, S: Type](scopedExpr: Expr[A @@ S])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    // Get the source code and position of the scoped expression
    val scopedTerm = scopedExpr.asTerm
    val pos        = scopedTerm.pos
    val sourceCode = pos.sourceCode.getOrElse(scopedTerm.show)
    val scopeTag   = Type.show[S]

    // Extract a human-readable scope name from the tag type
    val scopeName = extractScopeName(scopeTag)

    // Emit compiler warning using shared renderer
    MacroCore.warnLeak(pos, sourceCode, scopeName)

    // Return the unwrapped value
    '{ @@.unscoped($scopedExpr) }
  }

  private def extractScopeName(scopeTag: String): String =
    // The scope tag is often something like "scope.Tag" or a more complex type
    // Try to extract a meaningful name
    scopeTag
      .replace("zio.blocks.scope.", "")
      .replace("_root_.", "")
      .split("\\.")
      .lastOption
      .getOrElse(scopeTag)
}
