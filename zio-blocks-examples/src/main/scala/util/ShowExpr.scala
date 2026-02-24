package util

import sourcecode.Text

/** Prints an expression together with its evaluated result.
  *
  * Usage:
  * {{{
  *   import util.ShowExpr.show
  *   show(Into[Int, Long].into(100))
  *   // Into[Int, Long].into(100)  =>  Right(100)
  * }}}
  */
object ShowExpr {

  /** Prints `<source>  =>  <value>` for the given expression. */
  def show[A](expr: Text[A]): Unit =
    println(s"${expr.source}  =>  ${expr.value}")
}
