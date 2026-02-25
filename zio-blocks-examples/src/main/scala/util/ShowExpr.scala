package util

import sourcecode.{File, Line, Text}

import java.io.{File => JFile}
import scala.io.Source

/**
 * Prints an expression together with its evaluated result.
 *
 * If a `//` comment appears on the line immediately above the `show(...)` call,
 * it is printed first as a label, giving output of the form:
 *
 * {{{
 *   // Failure — email validation rejects the value
 *   toUser.into(UserDto("Bob", "not-an-email", 25))
 *   // Left(SchemaError(...))
 * }}}
 */
object ShowExpr {

  def show[A](expr: Text[A])(implicit file: File, line: Line): Unit = {
    commentAbove(file.value, line.value).foreach(println)
    println(expr.source)
    println(s"// ${expr.value}")
    println()
  }

  private def commentAbove(filePath: String, lineNum: Int): Option[String] = {
    val f = new JFile(filePath)
    if (!f.exists()) return None
    val lines = Source.fromFile(f).getLines().toArray
    val idx   = lineNum - 2 // lineNum is 1-indexed; the line above is at index lineNum-2
    if (idx < 0) return None
    val above = lines(idx).trim
    if (above.startsWith("//")) Some(above) else None
  }
  
}
