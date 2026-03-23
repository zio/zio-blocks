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

package util

import sourcecode.{File, Line, Text}

import java.io.{File => JFile}
import scala.io.Source

/**
 * Prints one or more expressions together with their evaluated results.
 *
 * All consecutive `//` comment lines immediately above the `show(...)` call are
 * printed first as a label. Multiple expressions can be passed in a single call
 * and are printed sequentially, giving output of the form:
 *
 * {{{
 *   // Converts all elements from Int to Long.
 *   // Duplicates are removed because the target is a Set.
 *   Into[List[Int], Set[Long]].into(List(1, 2, 2, 3))
 *   // Right(HashSet(1, 2, 3))
 * }}}
 *
 * Multiple expressions in one call:
 * {{{
 *   show(
 *     isPrimitive(TypeId.of[Int]),
 *     isPrimitive(TypeId.of[String])
 *   )
 *   // isPrimitive(TypeId.of[Int])
 *   // true
 *   // isPrimitive(TypeId.of[String])
 *   // false
 * }}}
 */
object ShowExpr {

  def show(exprs: Text[?]*)(implicit file: File, line: Line): Unit = {
    if (exprs.isEmpty) return
    commentsAbove(file.value, line.value).foreach(println)
    exprs.foreach { expr =>
      println(expr.source)
      expr.value.toString.linesIterator.foreach(l => println(s"// $l"))
    }
    println()
  }

  private def commentsAbove(filePath: String, lineNum: Int): List[String] = {
    val f = new JFile(filePath)
    if (!f.exists()) return Nil
    val source = Source.fromFile(f)
    try {
      // Only load lines up to the call site to minimize memory usage
      val lines  = source.getLines().take(lineNum).toList
      var idx    = Math.min(lineNum - 2, lines.length - 1)
      var result = List.empty[String]
      while (idx >= 0 && lines(idx).trim.startsWith("//")) {
        result = lines(idx).trim :: result
        idx -= 1
      }
      result
    } finally source.close()
  }

}
