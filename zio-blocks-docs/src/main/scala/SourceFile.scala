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

package docs

import scala.io.Source
import scala.util.Using
import scala.util.control.NonFatal

object SourceFile {

  def read(path: String, lines: Seq[(Int, Int)]): String = {
    def openSource(path: String): Source =
      try {
        Source.fromFile("../" + path)
      } catch {
        case NonFatal(_) => Source.fromFile(path)
      }

    Using.resource(openSource(path)) { source =>
      val allLines = source.getLines().toVector
      if (lines.isEmpty) {
        allLines.mkString("\n")
      } else {
        val chunks = for {
          (from, to) <- lines
        } yield allLines
          .slice(from - 1, to)
          .mkString("\n")

        chunks.mkString("\n\n")
      }
    }
  }

  def fileExtension(path: String): String = {
    val javaPath      = java.nio.file.Paths.get(path)
    val fileExtension =
      javaPath.getFileName.toString
        .split('.')
        .lastOption
        .getOrElse("")
    fileExtension
  }

  def print(
    path: String,
    lines: Seq[(Int, Int)] = Seq.empty,
    showTitle: Boolean = true,
    showLineNumbers: Boolean = false
  ) = {
    val title     = if (showTitle) s"""title="$path"""" else ""
    val showLines = if (showLineNumbers) "showLineNumbers" else ""
    println(s"""```${fileExtension(path)} $title $showLines""")
    println(read(path, lines))
    println("```")
  }

}
