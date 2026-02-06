package zio.blocks.scope

import zio.blocks.scope.internal.MacroCore
import scala.quoted.*

private[scope] object LeakMacros {

  def leakImpl[A: Type, S: Type](scopedExpr: Expr[A @@ S])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    val color = MacroCore.Colors.shouldUseColor

    // Get the source code and position of the scoped expression
    val scopedTerm      = scopedExpr.asTerm
    val pos             = scopedTerm.pos
    val sourceCode      = pos.sourceCode.getOrElse(scopedTerm.show)
    val scopeTag        = Type.show[S]

    // Extract a human-readable scope name from the tag type
    val scopeName = extractScopeName(scopeTag)

    // Build the warning message using the same conventions as error rendering
    val warning = renderLeakWarning(sourceCode, scopeName, color)

    report.warning(warning, pos)

    // Return the unwrapped value
    '{ @@.unscoped($scopedExpr) }
  }

  private def extractScopeName(scopeTag: String): String =
    // The scope tag is often something like "scope.Tag" or a more complex type
    // Try to extract a meaningful name
    scopeTag
      .replace("zio.blocks.scope.", "")
      .replace("_root_.", "")
      .split("\\.").lastOption.getOrElse(scopeTag)

  private def renderLeakWarning(sourceCode: String, scopeName: String, color: Boolean): String = {
    import MacroCore.Colors.*

    val lineWidth = 80

    def header(title: String): String = {
      val sep = "─" * (lineWidth - title.length - 4)
      s"${gray("──", color)} ${bold(title, color)} ${gray(sep, color)}"
    }

    def footer(): String =
      gray("─" * lineWidth, color)

    // Build the pointer line
    // The ^ should point to the start of the expression
    val caretLine  = " " * ("leak(".length) + "^"
    val pointerLine = " " * ("leak(".length) + "|"

    s"""${header("Scope Warning")}
       |
       |  leak($sourceCode)
       |  $caretLine
       |  $pointerLine
       |
       |  ${yellow("Warning:", color)} ${cyan(sourceCode, color)} is being leaked from scope ${cyan(scopeName, color)}.
       |  This may result in undefined behavior.
       |
       |  ${yellow("Hint:", color)}
       |     If you know this data type is not resourceful, then add a ${cyan("given ScopeEscape", color)}
       |     for it so you do not need to leak it.
       |
       |${footer()}""".stripMargin
  }
}
