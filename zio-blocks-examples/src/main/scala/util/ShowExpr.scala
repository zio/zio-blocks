package util

import sourcecode.Text

/**
 * Prints an expression together with its evaluated result.
 */
object ShowExpr {
  def show[A](expr: Text[A]): Unit =
    println(s"${expr.source}\n// ${expr.value}\n")
}
