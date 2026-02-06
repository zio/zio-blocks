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

    // Build the warning message using the shared renderer
    val warning = MC.ScopeMacroWarning.LeakWarning(sourceCode, scopeName).render(color)

    // Emit compiler warning
    c.warning(scoped.tree.pos, warning)

    // Return the unwrapped value
    c.Expr[A](q"_root_.zio.blocks.scope.@@.unscoped($scoped)")
  }

  private def extractScopeName(scopeTag: String): String =
    scopeTag
      .replace("zio.blocks.scope.", "")
      .replace("_root_.", "")
      .split("\\.")
      .lastOption
      .getOrElse(scopeTag)
}
