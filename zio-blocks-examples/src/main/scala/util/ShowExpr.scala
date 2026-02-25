package util

import sourcecode.{File, Line, Text}

import java.io.{File => JFile}
import scala.io.Source

/**
 * Prints an expression together with its evaluated result.
 *
 * All consecutive `//` comment lines immediately above the `show(...)` call
 * are printed first as a label, giving output of the form:
 *
 * {{{
 *   // Converts all elements from Int to Long.
 *   // Duplicates are removed because the target is a Set.
 *   Into[List[Int], Set[Long]].into(List(1, 2, 2, 3))
 *   // Right(HashSet(1, 2, 3))
 * }}}
 */
object ShowExpr {

  def show[A](expr: Text[A])(implicit file: File, line: Line): Unit = {
    commentsAbove(file.value, line.value).foreach(println)
    println(expr.source)
    println(s"// ${expr.value}")
    println()
  }

  private def commentsAbove(filePath: String, lineNum: Int): List[String] = {
    val f = new JFile(filePath)
    if (!f.exists()) return Nil
    val lines = Source.fromFile(f).getLines().toArray
    // Walk backwards from the line above the call site, collecting // comment lines.
    // lineNum is 1-indexed, so the line above is at 0-indexed position lineNum - 2.
    var idx    = lineNum - 2
    var result = List.empty[String]
    while (idx >= 0 && lines(idx).trim.startsWith("//")) {
      result = lines(idx).trim :: result
      idx -= 1
    }
    result
  }

}
