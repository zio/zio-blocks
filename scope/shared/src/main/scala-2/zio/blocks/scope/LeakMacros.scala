package zio.blocks.scope

import scala.reflect.macros.whitebox
import zio.blocks.scope.internal.{MacroCore => MC}

private[scope] object LeakMacros {

  def leakImpl[A, S](c: whitebox.Context)(
    scoped: c.Expr[A @@ S]
  )(implicit
    atag: c.WeakTypeTag[A],
    stag: c.WeakTypeTag[S]
  ): c.Expr[A] = {
    import c.universe._

    val color = MC.Colors.shouldUseColor

    // Get source code of the scoped expression
    val sourceCode = show(scoped.tree)

    // Extract a human-readable scope name from the tag type
    val scopeName = extractScopeName(stag.tpe.toString)

    // Build the warning message
    val warning = renderLeakWarning(sourceCode, scopeName, color)

    // Emit compiler warning
    c.warning(scoped.tree.pos, warning)

    // Return the unwrapped value
    c.Expr[A](q"_root_.zio.blocks.scope.@@.unscoped($scoped)")
  }

  private def extractScopeName(scopeTag: String): String =
    scopeTag
      .replace("zio.blocks.scope.", "")
      .replace("_root_.", "")
      .split("\\.").lastOption.getOrElse(scopeTag)

  private def renderLeakWarning(sourceCode: String, scopeName: String, color: Boolean): String = {
    import MC.Colors._

    val lineWidth = 80

    def header(title: String): String = {
      val sep = "─" * (lineWidth - title.length - 4)
      s"${gray("──", color)} ${bold(title, color)} ${gray(sep, color)}"
    }

    def footer(): String =
      gray("─" * lineWidth, color)

    // Build the pointer line
    val caretLine   = " " * ("leak(".length) + "^"
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
