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
   *   the example, with the comment margin removed and each `\$` read as `$`,
   *   as Scaladoc renders it
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
              // Scaladoc renders `\$` as `$` (`$name` alone would be a doc variable), so compile what
              // the reader sees: `sql"... \$id"` is the interpolation `sql"... $id"`.
              code += stripMargin(lines(i)).replace("\\$", "$")
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
   * The modules whose package does not follow their directory, from the module
   * directory to the package. Add a module here before adding it to
   * `scaladocCheckedModules` in `build.sbt` when its package is not
   * `zio.blocks.<module-dir>` with `-` read as `.`.
   */
  val packageOverrides: Map[String, String] = Map(
    "dataMigration"      -> "zio.blocks.data.migration",
    "datastar"           -> "zio.http.datastar",
    "htmx"               -> "zio.http.htmx",
    "http-model"         -> "zio.http",
    "http-model-schema"  -> "zio.http.schema",
    "otel"               -> "zio.blocks.telemetry.otel",
    "schema-messagepack" -> "zio.blocks.schema.msgpack"
  )

  /**
   * The package of the module a source file belongs to: its entry in
   * `packageOverrides`, or else the module directory read as a package under
   * `zio.blocks` (`streams` gives `zio.blocks.streams`, `schema-avro` gives
   * `zio.blocks.schema.avro`).
   */
  def modulePackage(sourcePath: String): String = {
    val module = moduleOf(sourcePath)
    packageOverrides.getOrElse(module, "zio.blocks." + module.replace('-', '.'))
  }

  /**
   * The import that every example of a module gets: its package, from
   * `modulePackage`, as in `zio.blocks.streams._` or `zio.http._`.
   *
   * `sql` is the exception: `sql-zio` shares its checker's classpath, and a
   * plain `zio.blocks.sql._` would bring the package `zio.blocks.sql.zio` into
   * scope as `zio`, so an example's own `import zio.blocks.schema._` would
   * fail. Its examples get `zio.blocks.sql.{zio => _, _}` and compile as for a
   * user without `sql-zio`.
   */
  def moduleImport(sourcePath: String): String =
    moduleOf(sourcePath) match {
      case "sql" => "zio.blocks.sql.{zio => _, _}"
      case _     => modulePackage(sourcePath) + "._"
    }

  /**
   * The name of the wrapper object for the example at `line` of `sourcePath`.
   * The path starts with the module directory, and the module's package
   * directories are dropped, so modules with same-named files get different
   * names; `generate` fails if two examples still map to one name.
   */
  def objectName(sourcePath: String, line: Int): String =
    "Snippet_" + sourcePath
      .stripSuffix(".scala")
      .replace("/src/main/", "/")
      .replace("/" + modulePackage(sourcePath).replace('.', '/') + "/", "/")
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
