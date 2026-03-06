package docs

import scala.io.Source

object SourceFile {

  def read(path: String, lines: Seq[(Int, Int)]): String = {
    def readFile(path: String) =
      try {
        Source.fromFile("../" + path)
      } catch {
        case _: Throwable => Source.fromFile(path)
      }

    if (lines.isEmpty) {
      val content = readFile(path).getLines().mkString("\n")
      content
    } else {
      val chunks = for {
        (from, to) <- lines
      } yield readFile(path)
        .getLines()
        .toArray[String]
        .slice(from - 1, to)
        .mkString("\n")

      chunks.mkString("\n\n")
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
    showLineNumbers: Boolean = false,
  ) = {
    val title     = if (showTitle) s"""title="$path"""" else ""
    val showLines = if (showLineNumbers) "showLineNumbers" else ""
    println(s"""```${fileExtension(path)} $title $showLines""")
    println(read(path, lines))
    println("```")
  }

}
