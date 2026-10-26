# Schema Module Scaladoc Implementation Plan

> Since #1743 the versions are 2.13.18, 3.9.0 and 3.10.0; read 3.3.7 below as 3.10.0.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every user-facing type and member of the `schema` module accurate Scaladoc that reads well on `/api` and in IDE hovers, with every code example compiled in CI.

**Architecture:** PR 0 adds the tooling: a build-time extractor (`project/ScaladocSnippets.scala`) that turns each `{{{ }}}` block in `schema` Scaladoc into a wrapper object, an unpublished sbt project `schema-scaladoc-check` that compiles those objects on every CI Scala version, a report-only coverage script, and a style guide. Nine content waves then document the module one group of related files at a time, one PR per wave.

**Tech Stack:** sbt 1.13 (build code is Scala 2.12), Scala 2.13.18 / 3.3.7 / 3.9.0, ZIO Test 2.1.27 (already used by the build), Python 3 standard library for the coverage script, Scala 3 Scaladoc for `/api`.

**Spec:** `docs/superpowers/specs/2026-10-21-schema-scaladoc-design.md`

## Global Constraints

- Scaladoc and tooling only. No behaviour change in `schema` main sources: bugs found go into `docs/dev/issues.md`, in the shape of the existing entries, in the same PR.
- Write once: a member declared in `scala-2/` and `scala-3/` (or `jvm/` and `js/`) gets the same Scaladoc text in every copy.
- Every claim in a Scaladoc matches the implementation or a test. Read the code before writing.
- Every multi-line `{{{ }}}` block must compile on Scala 2.13.18, 3.3.7 and 3.9.0 (the CI matrix is `2.13.x`, `3.3.x`, `3.9.x`), unless its first line is `// scala-3-only`, `// scala-2-only` or `// no-check: <reason>`.
- Snippets get an implicit `import zio.blocks.schema._`; anything else is imported inside the block. Braces, not indentation syntax (Scala 3 builds use `-no-indent`). Sample data uses real Scala books, such as Zionomicon.
- No new dependencies. ZIO Test 2.1.27 is already in the build; the coverage script uses only the Python standard library.
- The coverage script reports; it never fails a build.
- Prose follows the `docs-writing-style` skill: present tense, active voice, no "simply" or "just".
- Every sbt run uses the AGENTS.md template and names the Scala version:
  ```bash
  GD="$(git rev-parse --absolute-git-dir)" && mkdir -p "$GD/agent-logs"
  LOG="$GD/agent-logs/sbt-$(date +%s)-$$.log"
  sbt --client -Dsbt.color=false "<command>" >"$LOG" 2>&1; echo "Exit: $? | Log: $LOG"
  ```
  In the steps below, "Run (sbt): `<command>`" means this template with `<command>` filled in.
- Before each commit that touches Scala or build files: `sbt --client "++3.9.0; fmtDirty"` then `sbt --client "++2.13.18; check"`.
- Commit messages: Conventional Commits (`build:`, `test:`, `docs(schema):` ...). Push to the `khajavi` remote. PR bodies explain the change in plain English.

## Review Focus

1. **Scala 3 syntax in a shared file.** A snippet in `shared/src/main/scala` that uses `using`, `given`, `derives` or union types must not break the 2.13 build. Expected: it is marked `// scala-3-only` and is compiled on Scala 3 only. Pinned by the `generate keeps scala-3-only snippets for Scala 3 only` test in Task 2.
2. **Unclosed code block.** A `{{{` whose `}}}` never comes, or comes on the `*/` line, must not swallow the rest of the file silently. Expected: the build fails with `<path>:<line>: code block is never closed`. Pinned by two tests in Task 2.
3. **Code lines that look like comment margin.** `val area = width * height` or a line starting with `||` inside a block must survive margin stripping and rendering unchanged. Pinned by the margin test and the `||` render test in Task 2.
4. **Stale generated files.** A deleted or moved snippet must not leave a stale wrapper object that still compiles (or still fails). Expected: the generator wipes its output directory on every run. Pinned by Step 5 of Task 4.
5. **Coverage script edge cases.** Annotations between a doc comment and its declaration (`@deprecated`) count as documented, while `private[schema]` and `override` members are skipped. Pinned by tests in Task 5.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `project/ScaladocSnippets.scala` (create) | Pure functions: extract snippets from a source file, name and render wrapper objects. Compiled by sbt (2.12) and by the check project's tests (2.13, 3). | 2 |
| `schema-scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala` (create) | Unit tests for the extractor. | 2 |
| `build.sbt` (modify) | `schema-scaladoc-check` project, source generator, root aggregate entry, CI test commands. | 2, 3 |
| `.claude/scripts/scaladoc-coverage.py` (create) | Coverage and cross-version drift report. Lives next to the other docs scripts in `.claude/scripts/`; the spec said `scripts/`, Task 6 updates the spec. | 5 |
| `.claude/scripts/test_scaladoc_coverage.py` (create) | Unit tests for the coverage script. | 5 |
| `docs/dev/scaladoc-style.md` (create) | The style guide every wave follows. | 6 |
| `AGENTS.md` (modify) | Link to the style guide; how to run the snippet check. | 6 |
| `docs/dev/backlog.md` (modify) | Section "11. Scaladoc: schema module" with the wave progress table. | 6 |
| `schema/**/src/main/**/*.scala` (modify) | Existing snippets fixed (Task 4); Scaladoc written (Tasks 7–15). | 4, 7–15 |

---

## PR 0: tooling

All of PR 0 is on branch `schema-scaladoc`, in worktree `/home/milad/sources/scala/zio-blocks-worktrees/schema-scaladoc`.

### Task 1: Spike: how Scala 3 Scaladoc renders `{{{` and fences (throwaway)

**Files:**
- Temporarily modify: `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala` (reverted at the end of the task)

**Interfaces:**
- Produces: the syntax decision used by Task 6's style guide (rule "Syntax"): either "`{{{ }}}`" or "```` ```scala ```` fences". The extractor in Task 2 accepts both, so Task 2 does not depend on the result.

- [ ] **Step 1: Add a probe comment.** Directly above `lazy val jsonCodec: JsonCodec[A] = getInstance(JsonFormat)` in `Schema.scala`, insert:

```scala
  /**
   * SPIKE wiki block:
   * {{{
   * val spikeWiki: Int = 1 + 2
   * }}}
   * SPIKE markdown fence:
   * ```scala
   * val spikeFence: Int = 3 + 4
   * ```
   * SPIKE links: [[Schema#derive]], [[zio.blocks.schema.Reflect]], [[https://zioblocks.com/reference/schema/schema]]
   */
```

- [ ] **Step 2: Render with Scala 3 and Scala 2 Scaladoc.**

Run (sbt): `++3.9.0; schemaJVM/doc`, then `++2.13.18; schemaJVM/doc`.
Expected: both exit 0.

- [ ] **Step 3: Inspect the HTML.**

```bash
cd /home/milad/sources/scala/zio-blocks-worktrees/schema-scaladoc
grep -rl "spikeWiki" schema/jvm/target/scala-3.9.0/api | head -1 | xargs grep -o '.\{0,120\}spike[A-Za-z]*.\{0,120\}' | head
grep -rl "spikeWiki" schema/jvm/target/scala-2.13/api | head -1 | xargs grep -o '.\{0,120\}spike[A-Za-z]*.\{0,120\}' | head
grep -rl "SPIKE links" schema/jvm/target/scala-3.9.0/api | head -1 | xargs grep -o 'SPIKE links.\{0,400\}'
curl -s -o /dev/null -w "%{http_code}\n" https://zioblocks.com/reference/schema/schema
curl -s -o /dev/null -w "%{http_code}\n" https://zioblocks.com/reference/schema/reflect
```

Record:
- (a) Is `spikeWiki` inside `<pre>`/`<code>` in the Scala 3 output? In the Scala 2 output?
- (b) Is `spikeFence` inside `<pre>`/`<code>` in the Scala 3 output? In Scala 2 (expected: no, Scala 2 Scaladoc has no Markdown)?
- (c) Do the two `[[...]]` type links become `<a href>` in Scala 3?
- (d) Do both URLs return 200?

- [ ] **Step 4: Decide.** If (a) is yes for both versions, the rule is "`{{{ }}}`". Otherwise it is "```` ```scala ```` fences" (IDE hovers on 2.13 then show fences as plain text; accept that). If (c) is no, the rule becomes "link types with backticks and `@see`; `[[...]]` only for URLs". If (d) gives 404, find the right path from the live site navigation and use that URL pattern in Task 6. Write the four answers and the decision into `/tmp/claude-1000/-home-milad-sources-scala-zio-blocks-worktrees-schema-scaladoc/078f7eae-c6e2-43f8-8227-dc762dfc3f18/scratchpad/spike-result.md`.

- [ ] **Step 5: Revert the probe.**

```bash
git checkout -- schema/shared/src/main/scala/zio/blocks/schema/Schema.scala
git status --short   # expected: clean
```

No commit: the spike is throwaway.

### Task 2: Snippet extractor and the `schema-scaladoc-check` project

**Files:**
- Create: `project/ScaladocSnippets.scala`
- Create: `schema-scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`
- Modify: `build.sbt` (new project after `scalaNextTests`, before `lazy val benchmarks`; root aggregate)

**Interfaces:**
- Produces (default package, object `ScaladocSnippets`):
  - `final case class Snippet(sourcePath: String, line: Int, code: List[String], onlyScalaMajor: Option[Int])`
  - `def stripMargin(line: String): String`
  - `def extract(sourcePath: String, content: String): List[Snippet]`, which throws `IllegalArgumentException("<path>:<line>: <message>")` on bad input
  - `def objectName(sourcePath: String, line: Int): String`
  - `def render(snippet: Snippet): String`
  - `def generate(sources: Seq[(String, String)], scalaMajor: Int): Seq[(String, String)]`, returning `(fileName, fileContent)` pairs
- Produces: sbt project id `schema-scaladoc-check`.

- [ ] **Step 1: Add the project to `build.sbt` with tests only (no generator yet).** Insert directly before `lazy val benchmarks = project`:

```scala
// Compiles every code example in the Scaladoc of `schema` (see docs/dev/scaladoc-style.md).
// Not published. The extractor lives in project/ScaladocSnippets.scala so that sbt can run it as a
// source generator; the Test configuration compiles that file again to unit-test it.
lazy val `schema-scaladoc-check` = project
  .in(file("schema-scaladoc-check"))
  .settings(stdSettings("zio-blocks-schema-scaladoc-check"))
  .dependsOn(schema.jvm)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % "2.1.27" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.27" % Test
    ),
    Test / unmanagedSources += (ThisBuild / baseDirectory).value / "project" / "ScaladocSnippets.scala"
  )
```

In the `root` project's `.aggregate(` list, add `` `schema-scaladoc-check`, `` on the line after `scalaNextTests.js,`.

- [ ] **Step 2: Write the failing tests.** Create `schema-scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`:

```scala
import zio.test._

import scala.util.Try

object ScaladocSnippetsSpec extends ZIOSpecDefault {
  private val path = "schema/shared/src/main/scala/zio/blocks/schema/A.scala"

  private def source(lines: String*): String = lines.mkString("\n")

  private def failure(content: String): Option[String] =
    Try(ScaladocSnippets.extract(path, content)).failed.toOption.map(_.getMessage)

  def spec: Spec[TestEnvironment, Any] = suite("ScaladocSnippets")(
    test("extract returns a {{{ block with the line of its opening marker and the margin removed") {
      val content = source(
        "package p",
        "/**",
        " * Area of a book cover:",
        " * {{{",
        " * val area = width * height",
        " *   .toString",
        " *",
        " * }}}",
        " */",
        "object A"
      )
      assertTrue(
        ScaladocSnippets.extract(path, content) == List(
          ScaladocSnippets.Snippet(path, 4, List("val area = width * height", "  .toString", ""), None)
        )
      )
    },
    test("extract returns a ```scala fence") {
      val content = source("/**", " * ```scala", " * val title = \"Zionomicon\"", " * ```", " */")
      assertTrue(
        ScaladocSnippets.extract(path, content) ==
          List(ScaladocSnippets.Snippet(path, 2, List("val title = \"Zionomicon\""), None))
      )
    },
    test("extract ignores blocks outside Scaladoc comments") {
      val content = source("/* plain comment", " * {{{", " * val x = 1", " * }}}", " */", "// {{{")
      assertTrue(ScaladocSnippets.extract(path, content) == Nil)
    },
    test("extract keeps scanning after a one-line Scaladoc") {
      val content = source("/** One line. */", "def f = 1", "/**", " * {{{", " * val y = 2", " * }}}", " */")
      assertTrue(ScaladocSnippets.extract(path, content).map(_.line) == List(4))
    },
    test("extract skips a block whose first line is // no-check with a reason") {
      val content = source("/**", " * {{{", " * // no-check: needs a running database", " * db.run()", " * }}}", " */")
      assertTrue(ScaladocSnippets.extract(path, content) == Nil)
    },
    test("extract rejects // no-check without a reason") {
      val content = source("/**", " * {{{", " * // no-check", " * db.run()", " * }}}", " */")
      assertTrue(
        failure(content) == Some(
          s"$path:2: `// no-check` needs a reason, as in `// no-check: needs a running database`"
        )
      )
    },
    test("extract fails with path and line when the comment ends before the block closes") {
      val content = source("/**", " * {{{", " * val x = 1", " */", "object A")
      assertTrue(failure(content) == Some(s"$path:2: code block is never closed"))
    },
    test("extract fails with path and line when the file ends before the block closes") {
      val content = source("/**", " * {{{", " * val x = 1")
      assertTrue(failure(content) == Some(s"$path:2: code block is never closed"))
    },
    test("extract records scala-3-only and scala-2-only markers") {
      val content = source(
        "/**",
        " * {{{",
        " * // scala-3-only",
        " * given Int = 1",
        " * }}}",
        " * {{{",
        " * // scala-2-only",
        " * implicit val i: Int = 1",
        " * }}}",
        " */"
      )
      assertTrue(ScaladocSnippets.extract(path, content).map(_.onlyScalaMajor) == List(Some(3), Some(2)))
    },
    test("stripMargin removes only the leading margin") {
      assertTrue(
        ScaladocSnippets.stripMargin("   *   val y = 2 * 3") == "  val y = 2 * 3",
        ScaladocSnippets.stripMargin("  *") == "",
        ScaladocSnippets.stripMargin("  */") == "  */",
        ScaladocSnippets.stripMargin("val z = 1") == "val z = 1"
      )
    },
    test("objectName drops src/main and the schema package and keeps the line") {
      assertTrue(
        ScaladocSnippets.objectName("schema/shared/src/main/scala-3/zio/blocks/schema/DerivedOptics.scala", 28) ==
          "Snippet_schema_shared_scala_3_DerivedOptics_L28",
        ScaladocSnippets.objectName("schema/jvm/src/main/scala/zio/blocks/schema/json/JsonReader.scala", 7) ==
          "Snippet_schema_jvm_scala_json_JsonReader_L7"
      )
    },
    test("render wraps the code in an object with the schema import and the source location") {
      val snippet = ScaladocSnippets.Snippet(path, 4, List("val a = 1", "", "  || b"), None)
      assertTrue(
        ScaladocSnippets.render(snippet) ==
          s"""// Generated from $path:4 by schema-scaladoc-check. Do not edit.
             |package scaladoccheck
             |
             |import zio.blocks.schema._
             |
             |object Snippet_schema_shared_scala_A_L4 {
             |  val a = 1
             |
             |    || b
             |}
             |""".stripMargin
      )
    },
    test("generate keeps scala-3-only snippets for Scala 3 only") {
      val content = source("/**", " * {{{", " * // scala-3-only", " * given Int = 1", " * }}}", " */")
      assertTrue(
        ScaladocSnippets.generate(Seq(path -> content), 2) == Nil,
        ScaladocSnippets.generate(Seq(path -> content), 3).map(_._1) == List("Snippet_schema_shared_scala_A_L2.scala")
      )
    }
  )
}
```

- [ ] **Step 3: Run the tests and check they fail.**

Run (sbt): `++3.9.0; schema-scaladoc-check/test`
Expected: compile error, `Not found: ScaladocSnippets` (the file does not exist yet).

- [ ] **Step 4: Write the extractor.** Create `project/ScaladocSnippets.scala`:

```scala
import scala.collection.mutable.ListBuffer

/**
 * Extracts the code examples from Scaladoc comments so that the `schema-scaladoc-check` project can
 * compile them. sbt compiles this file (Scala 2.12) to run the source generator, and
 * `schema-scaladoc-check / Test` compiles it again (Scala 2.13 and 3) to unit-test it, so it has to
 * stay plain Scala that all three versions accept.
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
   *   `Some(3)` for a `// scala-3-only` example, `Some(2)` for a `// scala-2-only` one
   */
  final case class Snippet(sourcePath: String, line: Int, code: List[String], onlyScalaMajor: Option[Int])

  /** Removes the leading ` * ` of a Scaladoc line, keeping the indentation after it. */
  def stripMargin(line: String): String = line.replaceFirst("""^\s*\*(?: |$)""", "")

  /**
   * Returns the `{{{ }}}` and ```` ```scala ```` blocks inside the Scaladoc comments of `content`,
   * leaving out the ones marked `// no-check: <reason>`.
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

  /** The name of the wrapper object for the example at `line` of `sourcePath`. */
  def objectName(sourcePath: String, line: Int): String =
    "Snippet_" + sourcePath
      .stripSuffix(".scala")
      .replace("/src/main/", "/")
      .replace("/zio/blocks/schema/", "/")
      .replaceAll("[^A-Za-z0-9]", "_") + "_L" + line

  /** The source file that wraps `snippet` in an object. */
  def render(snippet: Snippet): String =
    (List(
      s"// Generated from ${snippet.sourcePath}:${snippet.line} by schema-scaladoc-check. Do not edit.",
      "package scaladoccheck",
      "",
      "import zio.blocks.schema._",
      "",
      s"object ${objectName(snippet.sourcePath, snippet.line)} {"
    ) ++ snippet.code.map(line => if (line.trim.isEmpty) "" else "  " + line) ++ List("}", "")).mkString("\n")

  /**
   * Turns `(path, content)` source files into `(fileName, fileContent)` wrapper files for a compiler
   * of the given major Scala version.
   */
  def generate(sources: Seq[(String, String)], scalaMajor: Int): Seq[(String, String)] =
    sources
      .flatMap { case (path, content) => extract(path, content) }
      .filter(_.onlyScalaMajor.forall(_ == scalaMajor))
      .map(snippet => (objectName(snippet.sourcePath, snippet.line) + ".scala", render(snippet)))

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
    else Some(Snippet(sourcePath, line, code, None))
  }

  private def fail(sourcePath: String, line: Int, message: String): Nothing =
    throw new IllegalArgumentException(s"$sourcePath:$line: $message")
}
```

- [ ] **Step 5: Run the tests on all three CI Scala versions.**

Run (sbt): `++3.9.0; schema-scaladoc-check/test; ++3.3.7; schema-scaladoc-check/test; ++2.13.18; schema-scaladoc-check/test`
Expected: `13 tests passed. 0 tests failed.` three times. If sbt reloads the build first, that is the new `project/` file being compiled by sbt itself, which proves it compiles on 2.12.

- [ ] **Step 6: Format and lint.**

Run (sbt): `++3.9.0; schema-scaladoc-check/scalafmt; schema-scaladoc-check/Test/scalafmt; scalafmtSbt`, then `++2.13.18; check`. Expected: exit 0.

- [ ] **Step 7: Commit.**

```bash
git add project/ScaladocSnippets.scala schema-scaladoc-check build.sbt
git commit -m "build: add schema-scaladoc-check project with Scaladoc snippet extractor"
```

### Task 3: Generate and compile the snippets, and run the check in CI

**Files:**
- Modify: `build.sbt` (the `schema-scaladoc-check` settings; `testJVMScala2Command`, `testJVMScala3Command`)

**Interfaces:**
- Consumes: `ScaladocSnippets.generate(sources: Seq[(String, String)], scalaMajor: Int): Seq[(String, String)]` from Task 2.
- Produces: `sbt "<version>; schema-scaladoc-check/test"` compiles every snippet of `schema` main sources for that Scala version, and the `testJVM` alias runs it in CI.

- [ ] **Step 1: Add the generator.** In the `schema-scaladoc-check` `.settings(...)` block from Task 2, add after the `Test / unmanagedSources` line:

```scala
    // Examples are user code: a warning in one (an unused import, say) must not fail the build.
    Compile / scalacOptions ~= (_.filterNot(Set("-Werror", "-Xfatal-warnings"))),
    Compile / sourceGenerators += Def.task {
      val root       = (ThisBuild / baseDirectory).value
      val outDir     = (Compile / sourceManaged).value / "scaladoc-snippets"
      val scalaMajor = CrossVersion.partialVersion(scalaVersion.value).fold(3)(_._1.toInt)
      // `schema.jvm`'s own source list already holds exactly the shared, jvm and version-specific
      // (scala-2, scala-3, scala-3.5+ ...) files for the current Scala version.
      val sources = (schema.jvm / Compile / unmanagedSources).value.collect {
        case file if file.getName.endsWith(".scala") =>
          (IO.relativize(root, file).getOrElse(file.getPath).replace('\\', '/'), IO.read(file))
      }
      IO.delete(outDir) // drop wrappers of snippets that were removed or moved
      ScaladocSnippets.generate(sources, scalaMajor).map { case (name, content) =>
        val file = outDir / name
        IO.write(file, content)
        file
      }
    }.taskValue
```

- [ ] **Step 2: Run the generator and see which existing snippets fail.**

Run (sbt): `++3.9.0; schema-scaladoc-check/compile`
Expected: exit 1, with compile errors in files under `schema-scaladoc-check/target/scala-3.9.0/src_managed/main/scaladoc-snippets/`. Most of the 45 existing blocks are not self-contained (for example `Patch.scala:35` uses an undefined `Person`), so this is the expected starting point for Task 4. If instead the task fails with `IllegalArgumentException: <path>:<line>: ...`, an existing block is malformed: note it for Task 4.

- [ ] **Step 3: Check that version-specific files are picked per Scala version.**

```bash
ls schema-scaladoc-check/target/scala-3.9.0/src_managed/main/scaladoc-snippets/ | grep -c scala_2_    # expected: 0
```

Run (sbt): `++2.13.18; schema-scaladoc-check/compile` (exit 1 is fine here), then:

```bash
ls schema-scaladoc-check/target/scala-2.13/src_managed/main/scaladoc-snippets/ | grep -c scala_3_    # expected: 0
ls schema-scaladoc-check/target/scala-2.13/src_managed/main/scaladoc-snippets/ | grep -c scala_2_    # expected: > 0
```

- [ ] **Step 4: Add the check to CI.** In `build.sbt`, in both `testJVMScala2Command` and `testJVMScala3Command`, replace `schemaJVM/test; ` with `schemaJVM/test; schema-scaladoc-check/test; `. (`test` compiles the generated snippets and runs the extractor tests; `commandForScalaVersion` already skips projects that do not support the selected version.)

- [ ] **Step 5: Format, lint, commit.** `schema-scaladoc-check` stays red until Task 4 fixes the snippets. Tasks 3 and 4 land in the same PR, and Task 4 follows straight away; do not push in between.

Run (sbt): `scalafmtSbt`, then `++2.13.18; check`. Expected: exit 0.

```bash
git add build.sbt
git commit -m "build: compile schema Scaladoc examples in schema-scaladoc-check and CI"
```

### Task 4: Make the existing snippets compile

**Files:**
- Modify: the 19 files that contain `{{{` today:
  `schema/shared/src/main/scala/zio/blocks/schema/{Schema,DynamicSchema,DynamicValueSelection,ToStructural}.scala`,
  `schema/shared/src/main/scala/zio/blocks/schema/{binding/BindingResolver,codec/Format,comptime/Allows,derive/DerivationBuilder,json/Json,json/JsonSelection,patch/Patch}.scala`,
  `schema/shared/src/main/scala-2/zio/blocks/schema/{DerivedOptics,SchemaVersionSpecific,json/NonNegativeIntCompanionVersionSpecific,json/PositiveNumberCompanionVersionSpecific}.scala`,
  `schema/shared/src/main/scala-3/zio/blocks/schema/{DerivedOptics,SchemaVersionSpecific,json/NonNegativeIntCompanionVersionSpecific,json/PositiveNumberCompanionVersionSpecific}.scala`
- Possibly modify: `docs/dev/issues.md`

**Interfaces:**
- Consumes: the `schema-scaladoc-check` compile from Task 3.
- Produces: a green `schema-scaladoc-check/test` on 2.13.18, 3.3.7 and 3.9.0.

- [ ] **Step 1: List the failures per Scala version.**

Run (sbt): `++3.9.0; schema-scaladoc-check/compile`, then `grep -E "^\[error\] -- .*scaladoc-snippets/" "$LOG" | sed -E 's|.*scaladoc-snippets/||' | sort -u`. Repeat on `++3.3.7` and `++2.13.18`. Each generated file's first line names the source location: `head -1 <generated file>`.

- [ ] **Step 2: Fix each failing block in its source file**, following these rules, in order of preference:
  1. **Missing definitions:** add them at the top of the block, so the example stands alone. For example, `Patch.scala:35` becomes:
     ```scala
       * {{{
       * case class Person(name: String, age: Int)
       * object Person extends CompanionOptics[Person] {
       *   implicit val schema: Schema[Person] = Schema.derived
       *   val name: Lens[Person, String] = $(_.name)
       *   val age: Lens[Person, Int]     = $(_.age)
       * }
       *
       * val patch1 = Patch.set(Person.name, "John")
       * val patch2 = Patch.increment(Person.age, 1)
       *
       * val patch3 = patch1 ++ patch2
       *
       * patch3(Person("Jane", 25)) // Person("John", 26)
       * }}}
     ```
     First check how the tests define optics (`grep -rn "extends CompanionOptics" schema/shared/src/test | head -3`) and copy that shape exactly, because `CompanionOptics`' API differs between the `scala-2/` and `scala-3/` versions.
  2. **Scala 3 syntax in a `shared/` file** (`using`, `given`, `derives`, union types `A | B`, `extension`): put `// scala-3-only` as the first line of the block. This applies to `comptime/Allows.scala:39` and its other union-type blocks.
  3. **Fragments that are not statements** (`Allows[List[Int], Sequence[Primitive]]` used as a type): make them statements, for example `implicitly[Allows[List[Int], Sequence[Primitive]]]`, or `type Example = ...`.
  4. **Wrong example:** if the block contradicts the code (wrong method name, wrong result comment), fix it to match the code and check the fix with `scala-cli` against the published artifact, as `AGENTS.md` describes for `issues.md` reproducers.
  5. **Only when the block cannot compile on its own** (it needs a database, a network, or a compiler crash stands in the way): `// no-check: <reason>`. If the reason is a library bug, add an entry to `docs/dev/issues.md` in the existing shape.

  Keep the text identical in `scala-2/` and `scala-3/` copies wherever the API allows.

- [ ] **Step 3: Run the check on all three versions.**

Run (sbt): `++3.9.0; schema-scaladoc-check/test; ++3.3.7; schema-scaladoc-check/test; ++2.13.18; schema-scaladoc-check/test`
Expected: exit 0 each; `13 tests passed`.

- [ ] **Step 4: Check that Scaladoc still renders.**

Run (sbt): `++3.9.0; schemaJVM/doc; ++2.13.18; schemaJVM/doc`. Expected: exit 0, and `grep -ci "warn" "$LOG"` is no higher than on `main` (run the same on `main` once for the baseline and write it down).

- [ ] **Step 5: Check that stale wrappers disappear.** Commit or note your Task 4 changes first. Then temporarily add this block at the end of the Scaladoc of `Schema#getDefaultValue` (any doc comment works), just before its closing `*/`:

```scala
   * {{{
   * val staleProbe: Int = 1
   * }}}
```

Run (sbt): `++3.9.0; schema-scaladoc-check/compile`. Then `grep -rl staleProbe schema-scaladoc-check/target/scala-3.9.0/src_managed` prints one file. Delete the three probe lines with the editor (not `git checkout`, which would also drop your snippet fixes in `Schema.scala`), run the compile again, and rerun the `grep`. Expected: it prints nothing, so the wrapper of a removed snippet is gone.

- [ ] **Step 6: Format, lint, commit.**

Run (sbt): `++3.9.0; schemaJVM/scalafmt`, then `++2.13.18; check`.

```bash
git add schema docs/dev/issues.md
git commit -m "docs(schema): make existing Scaladoc examples compile"
```

### Task 5: Coverage and drift report

**Files:**
- Create: `.claude/scripts/scaladoc-coverage.py`
- Create: `.claude/scripts/test_scaladoc_coverage.py`

**Interfaces:**
- Produces (Python module `scaladoc_coverage`, loaded by the test through `importlib`, since the file name has a dash):
  - `Decl(name: str, line: int, doc: str | None)` (dataclass), with `documented` meaning `doc is not None`
  - `scan(text: str) -> list[Decl]`
  - `package_of(path: str) -> str`
  - `coverage(root: str) -> dict[str, tuple[int, int]]` mapping package to `(declarations, documented)`
  - `drift(root: str) -> list[str]`, one line per cross-version copy whose Scaladoc differs
  - CLI: `python3 .claude/scripts/scaladoc-coverage.py <module-dir> [--drift]`

- [ ] **Step 1: Write the failing tests.** Create `.claude/scripts/test_scaladoc_coverage.py`:

```python
import importlib.util
import pathlib
import tempfile
import textwrap
import unittest

_spec = importlib.util.spec_from_file_location(
    "scaladoc_coverage", pathlib.Path(__file__).with_name("scaladoc-coverage.py")
)
sc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sc)


class ScanTest(unittest.TestCase):
    def test_documented_and_undocumented_members(self):
        text = textwrap.dedent("""\
            /** A book. */
            final case class Book(title: String) {
              /** The title in upper case. */
              def loud: String = title.toUpperCase
              def quiet: String = title
            }
            """)
        self.assertEqual(
            [(d.name, d.line, d.doc is not None) for d in sc.scan(text)],
            [("class Book", 2, True), ("def loud", 4, True), ("def quiet", 5, False)],
        )

    def test_annotation_between_doc_and_declaration_counts_as_documented(self):
        text = '/** Old. */\n@deprecated("use b", "0.1")\ndef a: Int = 1\n'
        self.assertEqual([(d.name, d.doc is not None) for d in sc.scan(text)], [("def a", True)])

    def test_private_protected_override_and_nested_members_are_skipped(self):
        text = textwrap.dedent("""\
            object O {
              private[schema] def hidden = 1
              protected def prot = 1
              override def toString = "O"
              def outer = {
                  def inner = 1
                  inner
              }
            }
            """)
        self.assertEqual([d.name for d in sc.scan(text)], ["object O", "def outer"])

    def test_doc_text_is_normalised(self):
        text = "  /**\n   * Reads   a\n   * book.\n   */\n  def read = 1\n"
        self.assertEqual(sc.scan(text)[0].doc, "Reads a book.")


class PackageTest(unittest.TestCase):
    def test_package_of_drops_platform_and_version_dirs(self):
        self.assertEqual(sc.package_of("shared/src/main/scala-3/zio/blocks/schema/json/Json.scala"), "schema.json")
        self.assertEqual(sc.package_of("jvm/src/main/scala/zio/blocks/schema/Platform.scala"), "schema")


class DriftTest(unittest.TestCase):
    def test_drift_reports_copies_with_different_text(self):
        with tempfile.TemporaryDirectory() as root:
            for version, doc in (("scala-2", "Derives a schema."), ("scala-3", "Derives the schema.")):
                d = pathlib.Path(root, "shared/src/main", version, "zio/blocks/schema")
                d.mkdir(parents=True)
                (d / "X.scala").write_text(f"/** {doc} */\ndef derived = 1\n/** Same. */\ndef same = 1\n")
            self.assertEqual(
                sc.drift(root),
                ["shared/src/main/{scala-2,scala-3}/zio/blocks/schema/X.scala: def derived"],
            )


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run them and check they fail.**

Run: `python3 -I .claude/scripts/test_scaladoc_coverage.py`
Expected: `FileNotFoundError` for `scaladoc-coverage.py`.

- [ ] **Step 3: Write the script.** Create `.claude/scripts/scaladoc-coverage.py`:

```python
#!/usr/bin/env python3
"""Report Scaladoc coverage of a module's main sources, per package.

Counts API-level declarations: members at an indentation of at most four spaces (top-level types and
their members), excluding private, protected and override members. It is a heuristic for tracking
progress, not a gate: it never fails a build.

Usage: python3 .claude/scripts/scaladoc-coverage.py <module-dir> [--drift]
  --drift  also list members declared in scala-2/ and scala-3/ (or jvm/ and js/) copies of a file
           whose Scaladoc text differs between the copies.
"""
import dataclasses
import os
import re
import sys

_DECL = re.compile(
    r"^((final|sealed|abstract|implicit|lazy|inline|transparent|case|override|private|protected|opaque)"
    r"(\[\w+\])?\s+)*(def|val|var|class|trait|object|type|enum|given)\s+([^\s\[(:=]+)"
)
_HIDDEN = re.compile(r"\b(private|protected|override)\b")


@dataclasses.dataclass
class Decl:
    name: str
    line: int
    doc: "str | None"


def _normalise(comment_lines):
    text = " ".join(re.sub(r"^\s*(/\*\*|\*/|\*)?\s?", "", l).replace("*/", "") for l in comment_lines)
    return " ".join(text.split())


def scan(text):
    lines = text.split("\n")
    decls = []
    for i, line in enumerate(lines):
        indent = len(line) - len(line.lstrip())
        m = _DECL.match(line.strip())
        if indent > 4 or not m or _HIDDEN.search(line.split("(")[0].split("=")[0]):
            continue
        j = i - 1
        while j >= 0 and lines[j].strip().startswith("@"):
            j -= 1
        doc = None
        if j >= 0 and lines[j].strip().endswith("*/"):
            k = j
            while k >= 0 and "/**" not in lines[k]:
                k -= 1
            doc = _normalise(lines[k : j + 1])
        decls.append(Decl(f"{m.group(4)} {m.group(5)}", i + 1, doc))
    return decls


def package_of(path):
    parts = path.replace("\\", "/").split("/")
    pkg = parts[parts.index("blocks") + 1 : -1] if "blocks" in parts else parts[:-1]
    return ".".join(pkg)


def _main_sources(root):
    for dirpath, _, files in os.walk(root):
        if "/src/main" not in dirpath.replace("\\", "/"):
            continue
        for f in files:
            if f.endswith(".scala"):
                path = os.path.join(dirpath, f)
                yield os.path.relpath(path, root).replace("\\", "/"), path


def coverage(root):
    result = {}
    for rel, path in _main_sources(root):
        with open(path, encoding="utf-8") as fh:
            decls = scan(fh.read())
        total, documented = result.get(package_of(rel), (0, 0))
        result[package_of(rel)] = (total + len(decls), documented + sum(d.doc is not None for d in decls))
    return result


_PAIRS = ((r"/scala-2/", "/scala-3/", "{scala-2,scala-3}"), (r"^jvm/", "js/", "{jvm,js}"))


def drift(root):
    files = {rel: path for rel, path in _main_sources(root)}
    report = []
    for rel in sorted(files):
        for left, right, label in _PAIRS:
            if not re.search(left, rel):
                continue
            other = re.sub(left, right, rel, count=1)
            if other not in files:
                continue
            with open(files[rel], encoding="utf-8") as a, open(files[other], encoding="utf-8") as b:
                docs_a = {d.name: d.doc for d in scan(a.read())}
                docs_b = {d.name: d.doc for d in scan(b.read())}
            shown = re.sub(left, "/" + label + "/" if left.startswith("/") else label + "/", rel, count=1)
            for name in sorted(docs_a.keys() & docs_b.keys()):
                if docs_a[name] != docs_b[name]:
                    report.append(f"{shown}: {name}")
    return report


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    root = argv[1]
    rows = sorted(coverage(root).items(), key=lambda kv: -kv[1][0])
    total = sum(t for _, (t, _) in rows)
    documented = sum(d for _, (_, d) in rows)
    print(f"{'package':32} {'decls':>6} {'doc':>6} {'%':>4}")
    for pkg, (t, d) in rows:
        print(f"{pkg:32} {t:6} {d:6} {100 * d // max(t, 1):4}")
    print(f"{'TOTAL':32} {total:6} {documented:6} {100 * documented // max(total, 1):4}")
    if "--drift" in argv:
        print("\nScaladoc that differs between cross-version copies:")
        for line in drift(root):
            print("  " + line)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
```

- [ ] **Step 4: Run the tests and check they pass.**

Run: `python3 -I .claude/scripts/test_scaladoc_coverage.py -v`
Expected: `Ran 6 tests ... OK`. If `test_drift_reports_copies_with_different_text` fails on the shown path, fix the `shown` line so that it prints exactly `shared/src/main/{scala-2,scala-3}/zio/blocks/schema/X.scala`.

- [ ] **Step 5: Take the baseline.**

Run: `python3 -I .claude/scripts/scaladoc-coverage.py schema --drift | tee /tmp/claude-1000/-home-milad-sources-scala-zio-blocks-worktrees-schema-scaladoc/078f7eae-c6e2-43f8-8227-dc762dfc3f18/scratchpad/coverage-baseline.txt`
Expected: a `TOTAL` line close to `6394 1194 18` (the estimate in the spec). The numbers go into Task 6's backlog table.

- [ ] **Step 6: Commit.**

```bash
chmod +x .claude/scripts/scaladoc-coverage.py
git add .claude/scripts/scaladoc-coverage.py .claude/scripts/test_scaladoc_coverage.py
git commit -m "build: add Scaladoc coverage and drift report script"
```

### Task 6: Style guide, AGENTS.md, backlog table, PR 0

**Files:**
- Create: `docs/dev/scaladoc-style.md`
- Modify: `AGENTS.md` (in `## Boundaries` → `### Always`, after the "Treat docs as part of the change" bullet)
- Modify: `docs/dev/backlog.md` (append section 11)
- Modify: `docs/superpowers/specs/2026-10-21-schema-scaladoc-design.md` (script path)

**Interfaces:**
- Consumes: the spike decision (Task 1), the check commands (Task 3), the coverage script (Task 5) and its baseline.
- Produces: `docs/dev/scaladoc-style.md`, the rule book every wave task cites.

- [ ] **Step 1: Write the style guide.** Create `docs/dev/scaladoc-style.md`. If the spike chose fences, replace every `{{{`/`}}}` in this file with ```` ```scala ````/```` ``` ````. If it rejected `[[Type]]` links, replace rule 5's link sentence with the spike's rule. Use the URL pattern the spike confirmed.

````markdown
# Scaladoc style guide

How to write Scaladoc in ZIO Blocks. First applied to the `schema` module (see `docs/superpowers/specs/2026-10-21-schema-scaladoc-design.md`).

## Who reads it

Two readers: the `/api` site, built by Scala 3 Scaladoc from the JVM projects, and IDE hovers on both Scala 2.13 and 3. Only the second reader sees `js/` and `scala-2/` sources, so document those too.

## Tiers

**Tier 1: core types.** In `schema`: `Schema`, `Reflect`, `Optic` (`Lens`, `Prism`, `Optional`, `Traversal`), `DynamicValue`, `PrimitiveValue`, `Binding`, `Patch`, `Migration`, `Into`, `As`, `JsonCodec`, `Json`, `JsonSchema`, `Deriver`, `Modifier`, `Validation`, `SchemaError`, `Allows`.

- The type: what it is, when to use it, one example, and `@see` the reference page.
- Its key methods (the ones its reference page shows): one example each.

```scala
/**
 * A `Lens` focuses on a part of a record that is always present, such as a field of a case class.
 * Use it to read or replace that part without rebuilding the record by hand.
 *
 * {{{
 * case class Book(title: String, year: Int)
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val year: Lens[Book, Int] = $(_.year)
 * }
 *
 * Book.year.replace(Book("Zionomicon", 2021), 2024) // Book("Zionomicon", 2024)
 * }}}
 *
 * @see [[https://zioblocks.com/reference/schema/optics Optics]]
 */
```

(Check this example against the real `CompanionOptics` API before copying its shape.)

**Tier 2: every other public member.** One to three sentences. Add `@param`, `@tparam` and `@return` only when the name does not already say it, and `@throws` only when the member really throws.

```scala
/** Returns the default value of this schema's type, if its schema declares one. */
def getDefaultValue: Option[A]
```

**Tier 3: macro plumbing.** `private[...]` code, `*VersionSpecific` internals, `MacroUtils`, `CommonMacroOps`, `IntoVersionSpecific`, `MigrationBuilderMacros`. One line, or nothing.

## Rules

1. **Write once.** A member declared in `scala-2/` and `scala-3/`, or `jvm/` and `js/`, gets the same text in every copy. Check with `python3 .claude/scripts/scaladoc-coverage.py schema --drift`.
2. **Tell the truth.** Read the implementation before writing. Every claim ("returns `Left` when ...", "throws ...") must match the code or a test. When the code looks wrong, do not describe the wrong behaviour as intended: add an entry to `docs/dev/issues.md` in the same PR.
3. **Prose.** Follow the `docs-writing-style` skill: present tense, active voice, no "simply" or "just". Start with what the member does, not "This method ...".
4. **Format.** Leave wrapping to `scalafmt`. One blank comment line between paragraphs and before the tags.
5. **Syntax.** Code in multi-line `{{{` / `}}}` blocks, each marker on its own line (single-line `{{{ x }}}` is not checked). Link types with `[[Type]]` or `[[Type#member]]`. Link a reference page with `@see [[https://zioblocks.com/reference/schema/<page> <Title>]]`.

## Examples are compiled

`sbt "++<version>; schema-scaladoc-check/test"` turns every multi-line block in the Scaladoc of `schema` into an object and compiles it. CI runs it on Scala 2.13, 3.3 and 3.9.

- Each block compiles on its own. The wrapper adds `import zio.blocks.schema._`; import anything else inside the block.
- Use braces, not indentation syntax.
- Sample data uses real Scala books, such as Zionomicon.
- Show results in comments (`// Book("Zionomicon", 2024)`). They are not checked, so run the example once (with `scala-cli` or in a test) before writing the comment.
- The first line of a block can be a marker:
  - `// scala-3-only`: compiled on Scala 3 only (`given`, `using`, `derives`, union types in a shared file).
  - `// scala-2-only`: compiled on Scala 2 only.
  - `// no-check: <reason>`: not compiled. The reason is required. Use it only when the block cannot compile on its own; if a library bug is the reason, file it in `docs/dev/issues.md`.

## Tracking

`python3 .claude/scripts/scaladoc-coverage.py schema` prints the share of documented declarations per package. It is a progress report, not a target: completeness is judged against the tiers above.
````

- [ ] **Step 2: Link it from `AGENTS.md`.** In `### Always`, after the bullet that starts `- Treat docs as part of the change`, add:

```markdown
- Write Scaladoc by [docs/dev/scaladoc-style.md](docs/dev/scaladoc-style.md). Examples in `schema` Scaladoc are compiled: run `sbt --client "++3.9.0; schema-scaladoc-check/test; ++2.13.18; schema-scaladoc-check/test"` after touching them.
```

- [ ] **Step 3: Add the progress table to `docs/dev/backlog.md`.** Append at the end of the file, filling in the PR 0 coverage from the Task 5 baseline:

```markdown
## 11. Scaladoc: schema module

Plan: `docs/superpowers/plans/2026-10-21-schema-scaladoc.md`. Style: `docs/dev/scaladoc-style.md`. Coverage: `python3 .claude/scripts/scaladoc-coverage.py schema` (TOTAL row, documented / declarations).

| Wave                     | Status      | PR | Coverage before | Coverage after |
|--------------------------|-------------|----|-----------------|----------------|
| 0. Tooling               | In progress |    | <baseline>      | <baseline>     |
| 1. Entry point           | Not started |    |                 |                |
| 2. Structure             | Not started |    |                 |                |
| 3. Binding               | Not started |    |                 |                |
| 4. Optics                | Not started |    |                 |                |
| 5. Dynamic               | Not started |    |                 |                |
| 6. Derivation and codecs | Not started |    |                 |                |
| 7. Transform and evolve  | Not started |    |                 |                |
| 8a. JSON model           | Not started |    |                 |                |
| 8b. JSON I/O             | Not started |    |                 |                |
```

Replace `<baseline>` with the TOTAL figures, for example `1194 / 6394 (18%)`.

- [ ] **Step 4: Update the spec's script path.** In the spec, replace `scripts/scaladoc-coverage.py` with `.claude/scripts/scaladoc-coverage.py` (next to the other docs scripts).

- [ ] **Step 5: Lint and commit.**

Run (sbt): `++2.13.18; check`. Expected: exit 0.

```bash
git add docs/dev/scaladoc-style.md AGENTS.md docs/dev/backlog.md docs/superpowers/specs/2026-10-21-schema-scaladoc-design.md docs/superpowers/plans/2026-10-21-schema-scaladoc.md
git commit -m "docs(dev): Scaladoc style guide and schema Scaladoc progress table"
```

- [ ] **Step 6: Final checks for PR 0.**

Run (sbt): `++3.9.0; schema-scaladoc-check/test; ++3.3.7; schema-scaladoc-check/test; ++2.13.18; schema-scaladoc-check/test`. Expected: exit 0.
Run: `python3 -I .claude/scripts/test_scaladoc_coverage.py`. Expected: `OK`.

- [ ] **Step 7: Push and open PR 0.**

```bash
git push khajavi schema-scaladoc
gh pr create --repo zio/zio-blocks --head khajavi:schema-scaladoc --title "build: compile schema Scaladoc examples, add Scaladoc style guide" --body "$(cat <<'EOF'
## What and why

Scaladoc examples in `schema` were never compiled, so they drifted from the code, and nothing said how deep Scaladoc should go. This PR adds the tooling for a module-wide Scaladoc pass (spec and plan under `docs/superpowers/`).

- `schema-scaladoc-check` (not published): at build time, `project/ScaladocSnippets.scala` copies every multi-line `{{{ }}}` block in the Scaladoc of `schema` into its own object, and the project compiles them on Scala 2.13, 3.3 and 3.9. CI runs it as part of `testJVM`. A block can opt out with `// no-check: <reason>` or limit itself to one Scala major version with `// scala-3-only` / `// scala-2-only`.
- The existing examples now compile (most used types they never defined).
- `.claude/scripts/scaladoc-coverage.py`: reports documented declarations per package and Scaladoc that differs between `scala-2`/`scala-3` copies. Report only.
- `docs/dev/scaladoc-style.md`: the tiers and rules the following PRs use, linked from `AGENTS.md`.

No library code changes.
EOF
)"
```

Then follow AGENTS.md "Git & CI": watch checks with `sleep 30 && gh pr checks <PR> --watch --fail-fast > /dev/null 2>&1 || true && gh pr checks <PR>`, fix failures (`fix-ci-failure` skill), address review comments (one commit per related group). Update the backlog row (status "Done", PR number) once merged.

---

## Content waves

Waves 1–8b start after PR 0 is merged. Each wave is one PR from its own worktree off `main`, so waves can run in parallel: `git worktree add ../scaladoc-wave-<n> -b scaladoc-wave-<n> origin/main`. Every wave follows the **wave procedure** below; each wave task lists what is specific to it.

### Wave procedure (applies to every task from 7 to 15)

1. **Baseline:** `python3 -I .claude/scripts/scaladoc-coverage.py schema > "$GD/agent-logs/scaladoc-wave-<n>-before.txt"` (`GD` as in the sbt template in Global Constraints).
2. **Research:** for each file in the wave, read the source, its tests (`grep -rln "<TypeName>" schema/shared/src/test`), and the listed reference pages. Use reference-page and test code as the source for examples. For waves with more than ~1,500 lines of source, delegate per-file research to the `documentation:docs-researcher` agent, asking for: public members, their behaviour from the code (with `file:line`), edge cases from tests, and contradictions with existing Scaladoc or reference pages.
3. **Write** Scaladoc for every member in the wave's files according to `docs/dev/scaladoc-style.md`: tier 1 types and the listed key methods get an overview or example; everything else public gets tier 2; macro plumbing tier 3. Keep cross-version copies identical. Fix any existing Scaladoc that contradicts the code.
4. **Known issues:** for each `issues.md` entry listed in the wave, make the Scaladoc describe the real current behaviour (and point out the surprise, if the entry says users hit it). Do not change code.
5. **Fact-check:** dispatch `documentation:docs-reviewer` in fact-check mode on the diff (`git diff origin/main -- schema`), asking it to check every behavioural claim against the source. Apply its corrections. New contradictions in code or reference pages go into `docs/dev/issues.md` (existing shape, run-verified reproducer); fix trivial reference-page errors in the same PR.
6. **Verify:**
   - Run (sbt): `++3.9.0; schema-scaladoc-check/test; ++3.3.7; schema-scaladoc-check/test; ++2.13.18; schema-scaladoc-check/test`. Expected: exit 0.
   - Run (sbt): `++3.9.0; schemaJVM/doc; ++2.13.18; schemaJVM/doc`. Expected: exit 0, and `grep -c "warn" "$LOG"` is no higher than on `main`.
   - Run: `python3 -I .claude/scripts/scaladoc-coverage.py schema --drift`. Expected: no drift lines for the wave's files.
   - Tests are not rerun: the change touches comments only. Check that: `git diff origin/main -- schema | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' | grep -vE '^[+-]\s*(\*|/\*\*|\*/|//|$)'` prints nothing. If it prints anything, a non-comment line changed; revert it.
7. **Format and lint:** Run (sbt): `++3.9.0; schemaJVM/scalafmt; schemaJS/scalafmt`, then `++2.13.18; check`.
8. **Commit** per file group (one commit per bullet in the wave's file list), message `docs(schema): Scaladoc for <group>`.
9. **PR:** push to `khajavi`, open the PR with title `docs(schema): Scaladoc for <wave name>`, a plain-English body (what was documented, coverage before → after from step 1 and a fresh run, `issues.md` entries added, anything left at tier 3 on purpose). Update the wave's backlog row in the same PR. Then watch CI and handle review per AGENTS.md "Git & CI".
10. **Split rule:** if `git diff --stat origin/main` shows more than about 3,000 changed lines before step 8, split the remaining file groups into a second PR `<n>b` on its own branch.

### Task 7: Wave 1, entry point

**Files (all under `schema/shared/src/main/`):**
- `scala/zio/blocks/schema/Schema.scala`, `scala/zio/blocks/schema/package.scala`
- `{scala-2,scala-3}/zio/blocks/schema/{SchemaCompanionVersionSpecific,SchemaVersionSpecific,syntax}.scala`
- Tier 3 only: `{scala-2,scala-3}/zio/blocks/schema/CommonMacroOps.scala`, `scala-3/zio/blocks/schema/MacroUtils.scala`
- `scala/zio/blocks/schema/{SchemaError,Modifier,Validation,SchemaAspect}.scala`

**Tier 1 and key methods:** `Schema` (type overview); `Schema.derived`, `Schema#jsonCodec`, `Schema#getInstance`, `Schema#derive`, `Schema#deriving`, `Schema#decode`, `Schema#encode`, `Schema#transform`, `Schema#defaultValue`, `Schema#doc`, `Schema.fromJsonSchema`; `SchemaError` (type, plus `validationFailed`); `Modifier` (type); `Validation` (type); the `toJsonString` / `fromJson` syntax.
**Reference pages:** `docs/reference/schema/{schema,schema-error,modifier,validation,syntax,type-class-derivation}.md`.
**Known issues to reflect:** `issues.md` "`Schema.fromJsonSchema` treats every property as required" (line 19), "schema: error paths leak the internal `.wrapped` segment" (line 1325), "`Schema.derived` crashes the compiler for a sealed trait nested in a class" (line 1335), "An implicit field `Schema` is picked up by `Schema.derived`, but an implicit codec is not" (line 1739), "A custom codec is silently not used when given as an implicit or through a configured deriver plus `toJsonString`" (line 1830), "html and schema: `p(...)` is ambiguous on Scala 3" (line 1513).

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Wave procedure steps 3–4 for `Schema.scala` and `package.scala`.
- [ ] **Step 3:** Steps 3–4 for the `scala-2`/`scala-3` `Schema*VersionSpecific` and `syntax` files (identical text in both).
- [ ] **Step 4:** Steps 3–4 for `SchemaError`, `Modifier`, `Validation`, `SchemaAspect`.
- [ ] **Step 5:** Wave procedure steps 5–10.

### Task 8: Wave 2, structure

**Files (`schema/shared/src/main/scala/zio/blocks/schema/`):**
- `Reflect.scala`, `Term.scala`, `Lazy.scala`, `Reflectable.scala`
- `ReflectTransformer.scala`, `RebindTransformer.scala`, `ReflectPrinter.scala`
- `PrimitiveType.scala`, `PrimitiveValue.scala`

**Tier 1 and key methods:** `Reflect` (overview of the node kinds `Record`, `Variant`, `Sequence`, `Map`, `Primitive`, `Wrapper`, `Dynamic`, `Deferred`, plus `Bound`/`Unbound`), `Reflect#fromDynamicValue`, `Reflect#toDynamicValue`, `Reflect#get` (by optic), `Reflect#doc`, `Reflect#modifiers`; `PrimitiveValue` (type overview); `Lazy` (overview: why it exists).
**Tier 2 bulk:** each `PrimitiveType` and `PrimitiveValue` case: one line naming the Scala type it stands for and any validation it carries.
**Reference pages:** `docs/reference/schema/{reflect,lazy,reflect-transformer}.md`.

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Steps 3–4 for `Reflect`, `Term`, `Lazy`, `Reflectable`.
- [ ] **Step 3:** Steps 3–4 for the three transformer/printer files.
- [ ] **Step 4:** Steps 3–4 for `PrimitiveType` and `PrimitiveValue`.
- [ ] **Step 5:** Wave procedure steps 5–10.

### Task 9: Wave 3, binding

**Files:**
- `schema/shared/src/main/scala/zio/blocks/schema/binding/*.scala` (16 files: `Binding`, `BindingResolver`, `BindingType`, `Constructor`, `Deconstructor`, `Discriminator`, `FromBinding`, `HasBinding`, `MapConstructor`, `MapDeconstructor`, `Matcher`, `Matchers`, `NoBinding`, `Register`, `RegisterType`, `Unapply`)
- `schema/shared/src/main/scala-2/zio/blocks/schema/binding/*.scala` and `scala-3/zio/blocks/schema/binding/*.scala` (incl. `SeqConstructor`, `SeqDeconstructor`, `BindingCompanionVersionSpecific`), `scala-3.5+/…/binding/*`, `scala-3.5-/…/binding/*`
- `schema/jvm/src/main/{scala,scala-2,scala-3}/zio/blocks/schema/binding/*.scala` and `schema/js/src/main/scala/zio/blocks/schema/binding/*.scala` (identical text across jvm/js)

**Tier 1 and key methods:** `Binding` (overview: what a binding adds to a `Reflect`, the binding kinds, the `Bound` vs `Unbound` link back to `Reflect`); `Constructor` and `Deconstructor` (one example each, using `Register`s).
**Reference pages:** `docs/reference/schema/{binding,binding-resolver,registers}.md`.

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Steps 3–4 for shared `binding/*`.
- [ ] **Step 3:** Steps 3–4 for `scala-2`/`scala-3`/`scala-3.5±` `binding/*`.
- [ ] **Step 4:** Steps 3–4 for `jvm`/`js` `binding/*`.
- [ ] **Step 5:** Wave procedure steps 5–10.

### Task 10: Wave 4, optics

**Files:**
- `schema/shared/src/main/scala/zio/blocks/schema/{Optic,OpticCheck,DynamicOptic,PathParser}.scala`
- `schema/shared/src/main/{scala-2,scala-3}/zio/blocks/schema/{CompanionOptics,DerivedOptics,PathInterpolator,PathMacros}.scala`

**Tier 1 and key methods:** `Optic` (overview), `Lens`, `Prism`, `Optional`, `Traversal` (overview and one example each), `Optic#get`/`getOption`, `Optic#replace`, `Optic#modify`, `Optic#apply` (composition), `CompanionOptics` and `DerivedOptics` (how to obtain optics), `DynamicOptic` (overview), the `p"..."` path interpolator.
**Reference pages:** `docs/reference/schema/{optics,dynamic-optic,path-interpolator}.md`.

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Steps 3–4 for `Optic` and `OpticCheck`.
- [ ] **Step 3:** Steps 3–4 for `DynamicOptic` and `PathParser`.
- [ ] **Step 4:** Steps 3–4 for the `scala-2`/`scala-3` optics and path files.
- [ ] **Step 5:** Wave procedure steps 5–10.

### Task 11: Wave 5, dynamic

**Files (`schema/shared/src/main/scala/zio/blocks/schema/`):**
- `DynamicValue.scala`, `DynamicValueSelection.scala`, `DynamicValueMergeStrategy.scala`, `DynamicValueType.scala`
- `DynamicSchema.scala`, `SchemaExpr.scala`, `DynamicSchemaExpr.scala`
- `SchemaMatch.scala`, `SchemaRepr.scala`, `SchemaParser.scala`, `SchemaMetadata.scala`

**Tier 1 and key methods:** `DynamicValue` (overview, each case one line, `get`/`select`, `merge`, conversions to and from typed values through a `Schema`), `DynamicSchema` (overview), `SchemaExpr` (overview).
**Reference pages:** `docs/reference/schema/{dynamic-value,dynamic-schema,schema-expr,schema-search}.md`.

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Steps 3–4 for the four `DynamicValue*` files.
- [ ] **Step 3:** Steps 3–4 for `DynamicSchema`, `SchemaExpr`, `DynamicSchemaExpr`.
- [ ] **Step 4:** Steps 3–4 for `SchemaMatch`, `SchemaRepr`, `SchemaParser`, `SchemaMetadata`.
- [ ] **Step 5:** Wave procedure steps 5–10.

### Task 12: Wave 6, derivation and codecs

**Files:**
- `schema/shared/src/main/scala/zio/blocks/schema/derive/*.scala` (10 files)
- `schema/shared/src/main/scala/zio/blocks/schema/codec/*.scala` (`BinaryCodec`, `Codec`, `Format`, `TextCodec`)
- `schema/shared/src/main/scala/zio/blocks/schema/{IsCollection,IsIntegral,IsMap,IsNumeric,NameMapper,DocsSchemas,TypeIdSchemas}.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/comptime/**/*.scala`, `schema/shared/src/main/{scala-2,scala-3}/zio/blocks/schema/comptime/*.scala`
- `schema/{jvm,js}/src/main/scala/zio/blocks/schema/Platform.scala` (identical text)

**Tier 1 and key methods:** `Deriver` (overview, `withInstance`, `withModifier`), `DerivationBuilder` (`instance`, `modifier`, `derive`), `Format` and `Codec` (overview), `Allows` (already 94% documented: review for accuracy and compile-check only).
**Reference pages:** `docs/reference/schema/{type-class-derivation,codec,format,allows}.md`.
**Known issues to reflect:** `issues.md` "A per-derivation `DerivationBuilder#instance` override silently loses to a deriver-level `Deriver#withInstance` override" (line 1782): state the precedence on both `Deriver#withInstance` and `DerivationBuilder#instance`.

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Steps 3–4 for `derive/*`.
- [ ] **Step 3:** Steps 3–4 for `codec/*`, `Is*`, `NameMapper`, `DocsSchemas`, `TypeIdSchemas`, `Platform`.
- [ ] **Step 4:** Steps 3–4 for `comptime/*` (shared, `scala-2`, `scala-3`).
- [ ] **Step 5:** Wave procedure steps 5–10.

### Task 13: Wave 7, transform and evolve

**Files:**
- `schema/shared/src/main/scala/zio/blocks/schema/{Into,As,ToStructural}.scala`, `schema/shared/src/main/{scala-2,scala-3}/zio/blocks/schema/{IntoVersionSpecific,AsVersionSpecific,ToStructuralVersionSpecific}.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/patch/*.scala` (`Differ`, `DynamicPatch`, `LCS`, `Patch`, `PatchMode`)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/*.scala`, `schema/shared/src/main/{scala-2,scala-3}/zio/blocks/schema/migration/*.scala`

**Tier 1 and key methods:** `Into` and `As` (overview, `derived`, one example each), `Patch` (overview, `set`, `increment`, `++`, `apply`, `diff`), `Migration` (overview, the builder entry point, one example). `IntoVersionSpecific`, `AsVersionSpecific`, `MigrationBuilderMacros` are tier 3. `migration/` is already 71% documented in `shared`: review for accuracy.
**Reference pages:** `docs/reference/schema/{patch,migration,structural-types}.md`, `docs/reference/schema/schema-evolution/{index,into,as}.md`.

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Steps 3–4 for `Into`, `As`, `ToStructural` and their version-specific files.
- [ ] **Step 3:** Steps 3–4 for `patch/*`.
- [ ] **Step 4:** Steps 3–4 for `migration/*` (shared, `scala-2`, `scala-3`).
- [ ] **Step 5:** Wave procedure steps 5–10.

### Task 14: Wave 8a, JSON model

**Files:**
- `schema/shared/src/main/scala/zio/blocks/schema/json/*.scala` (19 files)
- `schema/shared/src/main/{scala-2,scala-3}/zio/blocks/schema/json/*.scala`

**Tier 1 and key methods:** `Json` (overview, construction, `get`/navigation, `render`, `parse`), `JsonCodec` (overview, `encode`/`decode`), `JsonCodecDeriver` (configuration methods), `JsonSchema` (overview, `check`, `fromSchema`), `JsonPatch` (overview, `diff`, `apply`).
**Reference pages:** `docs/reference/schema/built-in-codecs/json/{json,json-config,json-differ,json-patch,json-schema,json-selection}.md`.
**Known issues to reflect:** `issues.md` "`JsonSchema#check` enforces `pattern` only when the pattern is anchored" (line 143); "`Schema.fromJsonSchema` treats every property as required" (line 19) on `JsonSchemaToReflect`.

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Steps 3–4 for `Json`, `JsonType`, `JsonSelection`, `JsonMatch`, `JsonDiffer`, `JsonPatch`, `MergeStrategy`.
- [ ] **Step 3:** Steps 3–4 for `JsonCodec`, `JsonCodecDeriver`, `JsonCodecError`, `DiscriminatorKind`, `ReaderConfig`, `WriterConfig`.
- [ ] **Step 4:** Steps 3–4 for `JsonSchema`, `JsonSchemaType`, `JsonSchemaToReflect`, the interpolator files, and the `scala-2`/`scala-3` `json/*`.
- [ ] **Step 5:** Wave procedure steps 5–10.

### Task 15: Wave 8b, JSON I/O

**Files:**
- `schema/jvm/src/main/scala/zio/blocks/schema/json/{JsonReader,JsonWriter}.scala`
- `schema/js/src/main/scala/zio/blocks/schema/json/{JsonReader,JsonWriter}.scala`

**Tier:** 2 throughout. These are low-level APIs for codec authors: describe what each read/write method consumes or produces and what it does on malformed input (which exception, and when). The jvm and js copies get identical text for each shared member; members that exist on one platform only say so.
**Reference pages:** `docs/reference/schema/built-in-codecs/json/json.md`, `docs/reference/schema/codec.md`.

- [ ] **Step 1:** Wave procedure steps 1–2.
- [ ] **Step 2:** Steps 3–4 for `JsonReader` (jvm), then copy each member's text to the js file and adjust only where behaviour differs.
- [ ] **Step 3:** Steps 3–4 for `JsonWriter` (jvm), then js the same way.
- [ ] **Step 4:** Wave procedure steps 5–10.

### Task 16: Close-out

**Files:**
- Modify: `docs/dev/backlog.md` (section 11)

- [ ] **Step 1: Check the done criteria from the spec** on an up-to-date `main`:
  - Every backlog row in section 11 says "Done" with a PR number.
  - Run (sbt): `++3.9.0; schema-scaladoc-check/test; ++3.3.7; schema-scaladoc-check/test; ++2.13.18; schema-scaladoc-check/test`. Expected: exit 0.
  - `python3 -I .claude/scripts/scaladoc-coverage.py schema --drift`. Expected: no drift lines.
  - For each package in the coverage output below 100%, list the undocumented declarations and confirm each is tier 3. Document any that are not, in a small follow-up PR that follows the wave procedure.
  - `grep -n "Scaladoc" docs/dev/issues.md`: every entry about a schema Scaladoc that contradicts the code is either fixed or still filed.
- [ ] **Step 2:** Add a final line under the section 11 table: `Completed <date>: coverage <before> → <after>.` Commit `docs(dev): close schema Scaladoc backlog`, then push and open a PR as in the wave procedure.
