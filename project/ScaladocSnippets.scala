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
    "markdown"           -> "zio.blocks.docs",
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
   * The import that the wrapper of an example of a module gets when
   * `needsModuleImport` says so: the module's package, from `modulePackage`, as
   * in `zio.blocks.streams._` or `zio.http._`.
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

  /**
   * Whether `snippet` already imports everything it gets from its module
   * package, so the wrapper's module import would be unused: a line starting
   * `import <package>._`, or `import <package>.syntax` (`_` or `{`), as the
   * schema-xml `syntax` examples do with `import zio.blocks.schema.xml.syntax._`
   * (the `syntax` objects hold extension methods, which no name check can
   * find). Other sub-package imports (such as `zio.blocks.schema.patch.X`) do
   * not count: they bring in only part of the package.
   *
   * Such an example does not need the wrapper's module import. Keeping it would
   * be an unused import, which scalac warns about in every copy of the example
   * (the example's own imports stay, so that they can be copied).
   */
  def importsModule(snippet: Snippet): Boolean = {
    val quoted   = java.util.regex.Pattern.quote(modulePackage(snippet.sourcePath))
    val covering = ("import " + quoted + """(?:\._(?!\w)|\.\*(?!\w)|\.syntax[._{])""").r
    snippet.code.exists(line => covering.findPrefixOf(line.trim).isDefined)
  }

  // A name the code uses unqualified: an identifier not preceded by a letter, digit, `_` or `.`.
  // `Stream` in `zio.blocks.streams.Stream` is qualified, so it does not count.
  private val mentionedIdentifier = """(?<![\w.])[A-Za-z_]\w*""".r

  // A name an import line brings into scope: an identifier not followed by `.` (a path segment) or
  // `=>` (a rename, whose left side is hidden). `Stream` in `import a.b.Stream` and in `import a.{Stream, X}` counts.
  private val importedIdentifier = """(?<!\w)[A-Za-z_]\w*(?!\w)(?!\s*\.)(?!\s*=>)""".r

  // A declaration: `class X`, `object X`, `def x`, ... with optional non-private modifiers. Matched on a
  // trimmed line, so `private` and `private[pkg]` declarations never match.
  private val declaration =
    """(?:(?:final|sealed|abstract|case|implicit|inline|open|opaque|override|lazy|transparent|infix|erased)\s+)*(?:class|trait|object|type|enum|def|val|var)\s+([A-Za-z_]\w*)""".r

  // Matches from the start of `line`: callers pass the line as it is for a top-level declaration
  // (column 0), or trimmed for one inside a block.
  private def declaredName(line: String): List[String] =
    declaration.findPrefixMatchOf(line).map(_.group(1)).toList

  /**
   * The names that the files of one module, for one Scala version, contribute
   * to the module package. The files are read from `sources`, which are all
   * the files of one module for one Scala version. A file's package is read
   * from its `package` clauses, not its directory, because some modules keep
   * files of `zio.http.datastar` under `zio/blocks/datastar/`. For each file of
   * the package itself, every declaration (`class`, `trait`, `object`, `type`,
   * `enum`, `def`, `val`, `var`, not `private`) counts, at any indentation: that
   * covers top-level declarations, members of a `package object`, and members
   * of an `implicit class` (`md` in `md"..."`). Indented names over-include,
   * which only keeps the import. For each file of a sub-package, its first
   * name below the package counts (`io` for `package zio.blocks.streams.io`).
   *
   * Givens and anonymous implicit instances are not names. An example that
   * needs one only through the wrapper's import fails to compile, so it then
   * needs its own import.
   */
  def moduleNames(sources: Seq[(String, String)]): Set[String] =
    sources.flatMap { case (path, content) =>
      val lines = content.split("\n", -1).toList.map(_.trim)
      val full  = lines.flatMap(packageClause).foldLeft("")((acc, c) => if (acc.isEmpty) c else acc + "." + c)
      val pkg   = modulePackage(path)
      if (full == pkg) lines.flatMap(declaredName)
      else if (full.startsWith(pkg + ".")) List(full.drop(pkg.length + 1).takeWhile(_ != '.'))
      else Nil
    }.toSet

  // The name a `package` or `package object` clause adds to the package, or Nil for other lines. In Scala 2 a
  // file may nest clauses (`package zio.http` then `package datastar`), so `moduleNames` joins them.
  private def packageClause(trimmed: String): List[String] = {
    val name =
      if (trimmed.startsWith("package object ")) Some(trimmed.stripPrefix("package object "))
      else if (trimmed.startsWith("package ")) Some(trimmed.stripPrefix("package "))
      else None
    name.map(_.trim.takeWhile(c => !c.isWhitespace && c != '{')).filter(_.nonEmpty).toList
  }

  /**
   * Whether the wrapper of `snippet` needs its module import: the example
   * uses one of the module's names (`moduleNames`) that it does not bind
   * itself, by an import or a declaration of its own (those shadow the
   * wrapper's import, so the wrapper's import is then unused). An example that
   * uses none of them, or imports its module package with `importsModule`,
   * gets no module import, so scalac has no unused import to warn about.
   *
   * The check errs towards keeping the import: a name it cannot tell apart
   * keeps the import, which is then used or at worst warns as before. The one
   * way to drop it wrongly is a name the example reaches without naming it,
   * which `moduleNames` does not list: a given or an anonymous implicit. That
   * fails to compile loudly, so the example then needs its own import.
   */
  def needsModuleImport(snippet: Snippet, moduleNames: Set[String]): Boolean = {
    val used  = snippet.code.flatMap(line => mentionedIdentifier.findAllIn(line).toList).toSet
    val imported = snippet.code
      .filter(_.trim.startsWith("import "))
      .flatMap(line => importedIdentifier.findAllIn(line).toList)
      .toSet
    val declared = snippet.code.flatMap(line => declaredName(line.trim)).toSet
    !importsModule(snippet) && ((used intersect moduleNames) -- (imported ++ declared)).nonEmpty
  }

  /**
   * The source file that wraps `snippet` in an object. The module import of
   * `moduleImport` is written only when `needsModuleImport` is true for the
   * module's `moduleNames`.
   */
  def render(snippet: Snippet, moduleNames: Set[String]): String = {
    val moduleImportLines =
      if (needsModuleImport(snippet, moduleNames)) List(s"import ${moduleImport(snippet.sourcePath)}", "")
      else Nil
    (List(
      s"// Generated from ${snippet.sourcePath}:${snippet.line} by scaladoc-check. Do not edit.",
      "package scaladoccheck",
      ""
    ) ++ moduleImportLines ++ List(
      s"object ${objectName(snippet.sourcePath, snippet.line)} {"
    ) ++ snippet.code.map(line => if (line.trim.isEmpty) "" else "  " + line) ++ List("}", "")).mkString("\n")
  }

  /**
   * Turns `(path, content)` source files into `(fileName, fileContent)` wrapper
   * files for a compiler of the given major Scala version. `sources` must be
   * the files of the modules for that version, so that `moduleNames` of each
   * module reads the names the compiler sees.
   */
  def generate(sources: Seq[(String, String)], scalaMajor: Int): Seq[(String, String)] = {
    val namesByModule = sources.groupBy { case (path, _) => moduleOf(path) }.map { case (module, files) =>
      module -> moduleNames(files)
    }
    val files = sources.flatMap { case (path, content) => extract(path, content) }
      .filter(_.onlyScalaMajor.forall(_ == scalaMajor))
      .map { snippet =>
        val names = namesByModule.getOrElse(moduleOf(snippet.sourcePath), Set.empty[String])
        (objectName(snippet.sourcePath, snippet.line) + ".scala", render(snippet, names))
      }
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
