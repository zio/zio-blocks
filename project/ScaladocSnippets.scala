import scala.collection.mutable.ListBuffer

/**
 * Extracts the code examples from Scaladoc comments so that the
 * `scaladoc-check` project can compile them. sbt compiles this file (Scala
 * 2.12) to run the source generator, and `scaladoc-check / Test` compiles it
 * again (Scala 2.13 and 3) to unit-test it, so it has to stay plain Scala that
 * all three versions accept.
 */
object ScaladocSnippets {

  /**
   * One code example.
   *
   * @param sourcePath
   *   path of the source file, relative to the repository root
   * @param line
   *   1-based line of the opening marker
   * @param code
   *   the example, with the comment margin removed
   * @param onlyScalaMajor
   *   `Some(3)` for a `// scala-3-only` example, `Some(2)` for a
   *   `// scala-2-only` one
   */
  final case class Snippet(sourcePath: String, line: Int, code: List[String], onlyScalaMajor: Option[Int])

  /**
   * Removes the leading ` * ` of a Scaladoc line, keeping the indentation after
   * it.
   */
  def stripMargin(line: String): String = line.replaceFirst("""^\s*\*(?: |$)""", "")

  /**
   * Returns the `{{{ }}}` and ```` ```scala ```` blocks inside the Scaladoc
   * comments of `content`, leaving out the ones marked `// no-check: <reason>`.
   */
  def extract(sourcePath: String, content: String): List[Snippet] = {
    val lines  = content.split("\n", -1)
    val result = ListBuffer.empty[Snippet]
    var inDoc  = false
    var i      = 0
    while (i < lines.length) {
      if (!inDoc && lines(i).contains("/**")) inDoc = true
      if (inDoc) {
        closingMarker(stripMargin(lines(i)).trim) match {
          case Some(close) =>
            val start = i + 1
            val code  = ListBuffer.empty[String]
            i += 1
            while (i < lines.length && stripMargin(lines(i)).trim != close) {
              if (lines(i).contains("*/")) fail(sourcePath, start, "code block is never closed")
              if (close == "}}}" && stripMargin(lines(i)).contains("}}}"))
                fail(
                  sourcePath,
                  start,
                  "a code line contains }}}, which ends the Scaladoc code block early; reword it (e.g. split the braces or drop the output comment)"
                )
              code += stripMargin(lines(i))
              i += 1
            }
            if (i == lines.length) fail(sourcePath, start, "code block is never closed")
            result ++= toSnippet(sourcePath, start, code.toList)
          case None => ()
        }
        if (lines(i).contains("*/")) inDoc = false
      }
      i += 1
    }
    result.toList
  }

  /**
   * The module a source file belongs to: its first path segment, as in
   * `streams` for `streams/shared/src/main/scala/...`.
   */
  def moduleOf(sourcePath: String): String = sourcePath.takeWhile(_ != '/')

  /**
   * The import that every example of a module gets: the module directory
   * `streams` gives `zio.blocks.streams._`, and a nested module such as
   * `schema-avro` gives `zio.blocks.schema.avro._`. This holds only for a
   * module whose package follows its directory; see the comment on
   * `scaladocCheckedModules` in `build.sbt` for the ones that don't.
   */
  def moduleImport(sourcePath: String): String =
    "zio.blocks." + moduleOf(sourcePath).replace('-', '.') + "._"

  /**
   * The name of the wrapper object for the example at `line` of `sourcePath`.
   * The path starts with the module directory, so modules with same-named files
   * get different names; `generate` fails if two examples still map to one name.
   */
  def objectName(sourcePath: String, line: Int): String =
    "Snippet_" + sourcePath
      .stripSuffix(".scala")
      .replace("/src/main/", "/")
      .replace("/zio/blocks/" + moduleOf(sourcePath).replace('-', '/') + "/", "/")
      .replaceAll("[^A-Za-z0-9]", "_") + "_L" + line

  /** The source file that wraps `snippet` in an object. */
  def render(snippet: Snippet): String =
    (List(
      s"// Generated from ${snippet.sourcePath}:${snippet.line} by scaladoc-check. Do not edit.",
      "package scaladoccheck",
      "",
      s"import ${moduleImport(snippet.sourcePath)}",
      "",
      s"object ${objectName(snippet.sourcePath, snippet.line)} {"
    ) ++ snippet.code.map(line => if (line.trim.isEmpty) "" else "  " + line) ++ List("}", "")).mkString("\n")

  /**
   * Turns `(path, content)` source files into `(fileName, fileContent)` wrapper
   * files for a compiler of the given major Scala version.
   */
  def generate(sources: Seq[(String, String)], scalaMajor: Int): Seq[(String, String)] = {
    val files = sources.flatMap { case (path, content) => extract(path, content) }
      .filter(_.onlyScalaMajor.forall(_ == scalaMajor))
      .map(snippet => (objectName(snippet.sourcePath, snippet.line) + ".scala", render(snippet)))
    files.groupBy(_._1).collectFirst {
      case (name, same) if same.size > 1 =>
        throw new IllegalArgumentException(s"two examples generate $name; rename one of their source files")
    }
    files
  }

  private def closingMarker(trimmed: String): Option[String] =
    if (trimmed == "{{{") Some("}}}")
    else if (trimmed == "```scala") Some("```")
    else None

  private def toSnippet(sourcePath: String, line: Int, code: List[String]): Option[Snippet] = {
    val marker = code.map(_.trim).find(_.nonEmpty).getOrElse("")
    if (marker.startsWith("// no-check")) {
      if (!marker.startsWith("// no-check:") || marker.stripPrefix("// no-check:").trim.isEmpty)
        fail(sourcePath, line, "`// no-check` needs a reason, as in `// no-check: needs a running database`")
      None
    } else if (marker == "// scala-3-only") Some(Snippet(sourcePath, line, code, Some(3)))
    else if (marker == "// scala-2-only") Some(Snippet(sourcePath, line, code, Some(2)))
    else if (marker.startsWith("// scala") || marker.startsWith("// no-"))
      fail(
        sourcePath,
        line,
        s"unknown marker `$marker`; use `// scala-3-only`, `// scala-2-only` or `// no-check: <reason>`"
      )
    else Some(Snippet(sourcePath, line, code, None))
  }

  private def fail(sourcePath: String, line: Int, message: String): Nothing =
    throw new IllegalArgumentException(s"$sourcePath:$line: $message")
}
