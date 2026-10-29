# ZIO Blocks Agent Guidelines

Zero-dependency Scala building blocks (2.13 + 3.x; JVM/JS).

## Setup

If `.git/bin/sbtn` doesn't exist, download [sbtn](https://github.com/sbt/sbtn-dist/releases) and place it there. Always run: `export PATH="$PWD/.git/bin:$PATH"`

If `sbt --client` is causing trouble, try:

```bash
sbt --client shutdown 2>/dev/null; pkill -f sbt 2>/dev/null; rm -rf .bsp project/target/active.json project/target/.sbt-server-connection.json
```

### Documentation Skills

Write docs with the ZIO documentation skills. Install:

```bash
BASE=https://github.com/zio/zio-skills/tree/main/plugins/documentation/skills
npx skills add $BASE/docs-{document-pr,data-type-ref,module-ref,companion-examples,writing-style,mdoc-conventions}
```

## Policies

- [Symbolic Operator Policy](SYMBOLIC_OPS_POLICY.md) — Rules for when symbolic operators are allowed in APIs. Consult before adding or reviewing any symbolic method.

### Sentinel performance policy (streams)

The streams module's primitive lanes signal end-of-stream without boxing or allocating, so the hot drain loops (`Sink.foldSyncReader`, `Reader#readAll`, `Sink.exists`, `Sink.find`) stay a **single primitive comparison per element**. `Long` and `Double` use no sentinel: the loop reads into a one-element scratch array and ends on a negative returned count (`readLongs`/`readDoubles`), so every value stays readable. The other lanes compare the pulled value against a sentinel outside the element's domain (`Int.MinValue` for `Short`, `Long.MinValue` for `Int`, `Double.MaxValue` for `Float`, any negative value for `Boolean`/`Byte`/`Char`), carried in a widened type, so no element can collide with it. See [EOF Signalling Per Lane](docs/reference/streams/execution-and-compatibility/zero-boxing.md#eof-signalling-per-lane) for the table. These loops are deliberately optimized; their shape is a feature, not a bug.

**Any change to end-of-stream handling is disallowed unless it has virtually no impact on performance.** In particular, bug-finding or "correctness" passes (human or agentic) must NOT add per-element work to these loops — no extra branches, flag reads, rawbits conversions, boxing, or allocation. The approved remedies, in order of preference:

1. **Count-returning bulk reads**: for a `Long` or `Double` sink, drain with `readLongs(scratch, 0, 1)` / `readDoubles(scratch, 0, 1)`, allocate `scratch` once outside the loop, and end on a negative count. Never end a loop on a value from `readLong(sentinel)` / `readDouble(sentinel)`: any value you reserve is also data, and the truncation is silent.
2. **Keep the existing one-comparison sentinel test** on the `Short`/`Int`/`Float`/`Boolean`/`Byte`/`Char` lanes, and take a new sentinel only from outside the element's domain (`Byte` takes no sentinel parameter).
3. **Document only**: when neither applies, document the limitation; do not "fix" it.

Validate any change near these loops against the streams JMH benchmarks (`streams-benchmark`) before and after; an unexplained regression means the change is rejected.

## Mindset

**sbt is slow—minutes per compile/test.** Wasted cycles waste hours.

- **Batch edits.** Do not: edit → compile → edit → compile. Do: edit everything → compile once.
- **Design tests with code.** Cover every statement and branch; if coverage reveals gaps, fix all at once.
- **Extreme ownership.** "Optional" means required; do the work completely.

## Commands

```bash
# Discover project names and Scala versions
sbt projects
sbt 'show crossScalaVersions'              # note the 2.13.x version

# sbt command template (use for ALL sbt invocations):
GD="$(git rev-parse --absolute-git-dir)" && mkdir -p "$GD/agent-logs"   # works in worktrees, where .git is a file
LOG="$GD/agent-logs/sbt-$(date +%s)-$$.log"
sbt --client -Dsbt.color=false <command> >"$LOG" 2>&1; echo "Exit: $? | Log: $LOG"
# Query: tail -50 "$LOG" or grep -i error "$LOG"

# <command> examples:
#   "++3.9.0; <project>/test"                             — fast loop, Scala 3
#   "++<scala2>; <project>/test"                          — fast loop, Scala 2
#   "++3.9.0; project <project>; coverage; test; coverageReport"   — coverage, Scala 3
#   "++<scala2>; project <project>; coverage; test; coverageReport"  — coverage, Scala 2
#   "++3.9.0; <project>/test; ++<scala2>; <project>/test" — cross-Scala
#   "++3.9.0; fmtDirty"                                  — format main sources
#   "++3.9.0; project <project>; fmtDirty"               — format sources in specific project
#   scalafmtSbt                                           — format build files
#
# IMPORTANT: --client mode preserves Scala version across invocations.
# ALWAYS specify ++<version> at the start of commands to avoid version drift.
```

`<project>` = any project name from `sbt projects` (e.g., `schemaJVM`, `schemaJS`, `schema-avro`).

Docs pages that open JDBC connections (the `sql` pages use `sqlite-jdbc`) pass `docs/mdoc` only once per sbt server: the second run fails with `No suitable driver found for jdbc:sqlite:...`, because `java.sql.DriverManager` loads drivers once per JVM and the new mdoc classloader can't see the old driver. Run `sbt --client shutdown` before re-running mdoc on such a page, and render several such pages in ONE `docs/mdoc` call by repeating `--in <src> --out <dst>` pairs (two `docs/mdoc` calls in one sbt command fail the second the same way). CI is unaffected (fresh JVM).

`sql-zio` is on the docs classpath and in `sql-examples`, and its package is `zio.blocks.sql.zio`, so `import zio.blocks.sql.*` (or `._`) brings a package named `zio` into scope. Any later `import zio.blocks...` or `zio.X`, in the same block or in a later block of the same mdoc scope, fails with `value blocks is not a member of zio.blocks.sql.zio`. Put `import zio.blocks.sql.*` after every other `zio` import, don't re-import it in a continuation block, and before a self-contained `mdoc:compile-only` block that follows a block importing it (mdoc compiles `compile-only` blocks inside the page's earlier scope) add an `mdoc:invisible` block with `import _root_.zio` (see `docs/reference/sql/repo.md`). An `mdoc:reset` block starts a fresh scope, so a page can begin each section with `mdoc:reset:silent` and its own imports (with `import zio.blocks.sql.*` last) and reuse names such as `Book` per section (see `docs/guides/map-custom-column-types.md`). The bug is recorded in `docs/dev/issues.md`.

Docs pages that define flag objects (`StaticFlag`/`DynamicFlag` from `config`) share two global registries across every block and across reruns in the same sbt server. Start the page with an `mdoc:invisible` block that calls `FlagSource.Registry.clear()` and `Flag.registry.clear()`, and give every flag object on the page a distinct name, or you get `FlagDuplicateNameException`. A flag's name is its JVM class name, so in mdoc it carries the `repl.MdocSession.MdocApp.` prefix (see `docs/guides/roll-out-feature-gradually.md`).
mdoc captures only Scala's `Console.out` (`println`), not `System.out` or `System.err`. The `telemetry` module's default log output and its `StdoutWriter`/`StderrWriter` write to the JVM streams, so on a page that logs, the lines go to the sbt log and the rendered page shows nothing. `docs/guides/instrument-a-program-with-logs-traces-and-metrics.md` opens with an `mdoc:invisible` block that forwards both streams into the page and masks timestamps and ids, and closes with one that restores them; copy that pattern. The telemetry globals (`log`, `trace`, `metric`) live in one JVM for the whole mdoc run but start fresh on each `docs/mdoc` invocation.
A page that starts the JDK HTTP server (`com.sun.net.httpserver`) inside mdoc loses everything the handler threads print: the JDK creates its dispatcher thread without inheriting thread-locals, so `Console.out` on that thread is the terminal, not mdoc's capture. Capture `Console.out` once in an `mdoc:invisible` block at the top of the page and have every thread write to that stream (see `PageOutput` in `docs/guides/small-json-backend-from-blocks.md`); mdoc keeps one console per page, so lines still land under the statement that is running.
If mdoc reports `skipping file, the compiler produced no classfiles and reported no errors`, the Scala 3 compiler crashed on the page; mdoc hides the stack trace. mdoc compiles a page's blocks as members of a class, so reproduce with `scala-cli compile` on the same code wrapped in `class Outer { ... }` to see the crash. Bisect a page by running mdoc on one truncated copy at a time: with several files in one `--in` directory, mdoc reports only the first failure and leaves the other outputs unevaluated. One known trigger is in `docs/dev/issues.md` (a sealed trait whose case classes sit in its companion, inside a class).

`<scala2>` = the 2.13.x version from `show crossScalaVersions`.

Not every project supports every version — check `show <project>/crossScalaVersions` if
`++3.9.0` doesn't apply. One exception worth knowing: `streamsJVM`/`streamsJS`/
`asyncJVM`/`asyncJS` don't support `3.8.3` by default (it's deliberately excluded
so `sbt ci-release` never mixes it with modules that don't — see
`golemStreamsAsyncVersion`'s doc comment in `build.sbt`), so testing them at
`3.8.3` (the Scala version the external `golemcloud/golem` Scala SDK is pinned to)
requires prefixing with the `golemStreamsAsyncVersion` command alias.

## Workflow

Phases 1-3 repeat until verify passes. Phase 4 runs once at the end.

### 1. Edit
Batch code and tests together. For large changes, split into batches—each batch includes its tests.

### 2. Fast Loop
One project, one Scala version: get `<project>/test` green. Default Scala 3; use Scala 2 if editing `scala-2/` sources. No coverage here.

### 3. Verify
Enter only when fast loop is green. Run in order:

1. **Coverage** — Scala version you developed with
2. **Cross-Scala** — the other Scala version, same project
3. **Cross-platform** — other platform projects, if cross-built and you touched shared/ or platform sources
4. **Downstream** — all projects that depend on what you changed:
    - `chunk*` → `schema*`, `benchmarks`
    - `schema*` → `scaladoc-check`, `schema-avro`, `schema-bson`, `schema-thrift`, `schema-messagepack*`, `schema-toon*`, `scalaNextTests*`, `benchmarks`, `docs`
    - `streams*` → `scaladoc-check`, `sql*`, `sql-zio`, `http-model*`, `http-model-schema*`, `endpoint*`, `datastar*`, `htmx*`, `dataMigration*`, `projection*`, `streams-benchmark`, `sql-benchmarks`, `docs`

    If unsure, check `dependsOn` in `build.sbt` / `project/*.scala`.

If any step fails: return to phase 1, fix, get green in phase 2, rerun the failing step.

### 4. Format

Run once after verify passes.

## Example Projects

Most `*-examples/` directories are **standalone sbt builds** (own `build.sbt` and `project/build.properties`). Each depends on the *published* `zio-blocks` artifacts (`zioBlocksVersion` in its `build.sbt`), so it works as a copy-paste template. Run from inside the directory: `cd schema-examples && sbt "runMain ..."`. The root `build.sbt` only references them (`RootProject`, the way zio-http does) and aggregates them; a new examples build needs both a `lazy val ... = RootProject(file("<name>-examples"))` line and an entry in the root `aggregate(...)` list (CI's example loop picks it up automatically because it has a `build.sbt`). When an API change should show up in an example, bump `zioBlocksVersion` after the release. The root `sbt scalafmtAll` does not format these standalone builds; format their sources with the root config instead: `scala-cli fmt --scalafmt-conf .scalafmt.conf <name>-examples/src/<main|test>/scala/<your-package>/`. Scope it to the files you changed: many existing example files were never formatted, and running it on a whole `src/` reflows them too, including splitting their Scaladoc `Run with: sbt "runMain ..."` lines across two lines.

`projection-examples` is the exception: it is a **root subproject** that depends on the local `projection.jvm` (like zio-http's `zio-http-example`), so its examples use the latest API without waiting for a release. It has no `build.sbt` of its own, and the CI example loop skips directories without one. Run it from the repository root: `sbt "projection-examples/runMain ..."`. Its SQLite files go in `projection-examples/.projection-examples/` (git ignores it), except `global/`, which the engine fixes.

CI compiles the example projects on JDK 17 and on JDK 25 (the `testJVM` matrix, Scala 3.9.x), so example code must not call JDK 21+ APIs such as `Executors.newVirtualThreadPerTaskExecutor()` or `Thread.ofVirtual()`. `docs/mdoc` runs on JDK 25 only (`buildDocs`; the `docs` project depends on `telemetry`, which is built for Java 21), so CI does not catch a JDK 21+ call in mdoc code; keep it out anyway, because readers copy docs code into JDK 17 projects (the published `streams`, `telemetry` and `otel` jars are built for Java 21, so code that uses them needs JDK 21 anyway). Take an `ExecutorService` parameter instead and show the JDK 21 line in a plain fence. An examples build whose module itself needs JDK 21 (the `telemetry` jar is class file 65, and its log macros load inside the compiler, so even `sbt compile` fails on JDK 17) skips its sources below JDK 21 with a warning; see `telemetry-examples/build.sbt`. scala-cli hides this: `scala-cli run --jvm 17` still compiles on its own newer JVM unless you pass `--server=false`. An example that needs a database (e.g. `sql-examples/.../postgrespool`) only has to compile in CI; run it locally against `docker run ... postgres:16` before committing.

The standalone examples builds have no scalafmt plugin, so `sbt scalafmtAll` at the root does not format them. Format a new or changed example package with the repo config instead: `scala-cli fmt --scalafmt-conf ../.scalafmt.conf src/main/scala/<package>` from inside the examples directory (add `--check` to only report).

The standalone example builds set `run / fork := true`, so a `-D` flag on the `sbt` command line reaches only sbt, not the example. Pass a system property as `sbt 'set run / javaOptions += "-Dkey=value"' "runMain ..."`; environment variables do reach the forked JVM. These builds declare no `crossScalaVersions`, so checking an example on Scala 2.13 needs the forced form, `sbt "++2.13.18!" compile`.

When you change, rename or move an example, update the docs that embed it (`grep -rn "mdoc:embed:.*<ExampleFile>" docs/`) in the same change, so their prose and quoted output still match; then run the example and mdoc on those pages.

## Cross-Version Code Structure

For version-specific code: ONE shared `package.scala` extending a trait with per-version implementations in `scala-2/` and `scala-3/`. See `markdown/` module (`MdInterpolator`). Never separate package objects per version.

**Streams:** `Stream`, `Sink`, `Pipeline`, `Reader` and `Writer` live once in `streams/shared/src/main/scala/` (unified in #1396). Only `LowPriorityJvmTypeInferPlatform.scala` and `internal/InternalVersionSpecific.scala` have `scala-2/` and `scala-3/` copies; keep those in sync.

## Testing

ZIO Test framework. Search codebase for `SchemaBaseSpec` for patterns.

### Render Assertions

Always assert the **full rendered string** with `==`. Never use `.contains()`:

```scala
// ✅ Correct — catches all regressions (attribute order, whitespace, closing tags)
assertTrue(result.render == """<div class="main">content</div>""")

// ❌ Wrong — misses attribute order, extra whitespace, missing tags
assertTrue(result.render.contains("main"))
```

## Git & CI (Prefer `gh` CLI)

Commit often, whenever fast loop is green.

PR already open (check!) and think you're done? Push, update PR title/description, then loop: monitor CI and review comments, fix all CI issues (including conflicts) and all **valid** review comments via Workflow. Don't stop until CI is green, & PR is approved & merged.

When waiting on PR checks, suppress watch output to avoid context bloat:
`sleep 30 && gh pr checks <PR> --watch --fail-fast > /dev/null 2>&1 || true && gh pr checks <PR>`

## Boundaries

### Always
- Follow workflow phases in order
- Batch edits; keep sbt runs scoped to one project
- Update AGENTS.md if you find errors or gaps
- In the middle of executing a skill, if you discover a deviation from the skill's instructions, encounter missing information or unclear guidance, or discover a better approach than what was written, update that skill file to reflect what you learned.
- Treat docs as part of the change: new data type, new feature, API change, or API removal isn't done until its `docs/` page(s) match
- Write Scaladoc by [docs/dev/scaladoc-style.md](docs/dev/scaladoc-style.md). Examples in `schema` and `streams` Scaladoc are compiled by one project, `scaladoc-check`: run `sbt --client "++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test"` after touching them (its cross versions; `show scaladoc-check/crossScalaVersions` lists them, and 3.3.7 is no longer one, so `++3.3.7` fails at once). To check another module whose package is `zio.blocks.<module-dir>` (with `-` read as `.`), add it to `scaladocCheckedModules` in `build.sbt`; the comment there lists the modules that need an override first.
- Use the [documentation skills](#documentation-skills) for every docs change
- In tutorials and guides, prefer ZIO Blocks APIs over JDK, Scala or third-party ones when a module covers the need.
- **Report every bug you find while writing docs, but don't edit `docs/dev/issues.md` yourself.** One coordinating session maintains that file, so guide PRs don't conflict on it. Send the coordinator the complete entry, ready to paste, and name its section (Library bugs, Usability traps, Test and CI, or Documentation errors). If no coordinating session is running, add the entry yourself in a separate `docs(dev)` commit. This includes library bugs and reference pages that contradict the source. Follow the shape of the existing entries: where it was found (page, date, commit, and whether it is the same at the latest release tag), a summary, a complete reproducer, the expected behaviour, the root cause with `file:line`, the impact, the test gap, and a suggested fix. The reproducer must be self-contained: imports, Scala and artifact versions, the file name and run command (`Repro.sc` for a script with top-level statements), and the real output pasted as `// ...` comments. Run it exactly as written before sending it (`scala-cli` against the published artifacts works), and never hand-write the output. Never link `docs/dev/` from a published page: the website excludes that directory, so the link fails the site build (`Build Docs`) even though scoped mdoc only warns. Name the bug in plain text ("a known bug in 0.0.56") or link the GitHub issue.
- **README.md is auto-generated.** Never edit `README.md` directly. Edit `docs/index.md` (or the `readme*` settings on the `docs` project in `build.sbt`) instead, then regenerate with `sbt --client 'set ThisBuild / version := "<latest release>"; ++3.9.0; docs/mdoc --include index.md; docs/generateReadme'`. (`generateReadme` lives on the `docs` project; running it unscoped at the root fails with "Not a valid command".)
  - `docs/generateReadme` is overridden in `build.sbt` to render the logo banner without a duplicate `# ZIO Blocks` heading. It uses the mdoc output of `docs/index.md` when it is fresh (keeps evaluated `// res: ...` lines), otherwise falls back to the source page with `@VERSION@` substituted. Pin `version` to the latest release tag locally, or the README gets a snapshot version.
  - There are no hand-maintained README sections any more: everything, including Config, comes from `docs/index.md`. The `update-readme` CI job runs `docs/generateReadme` after each release and opens an auto-merged PR.
- **The website lives in `website/`** (Docusaurus: landing page at `/`, docs under `/docs/`). The landing content is generated from `docs/index.md` (table shapes, section titles and the `@VERSION@` placeholder are a contract; see `website/README.md`). After touching `docs/index.md`, run `cd website && yarn install --frozen-lockfile && yarn test && yarn check:landing`. Never hand-edit `website/src/data/site.json` or `website/static/brand/` (generated, gitignored).

### Ask First
- Adding dependencies (even test-only)
- Creating or removing subprojects
- Any repo-wide test or coverage run
- **New modules:** When adding a new module, update the `testJVM`, `testJS`, `docJVM`, and `docJS` command aliases in `build.sbt`, and add a published module to the `docs` project's `ScalaUnidoc / unidoc / unidocProjectFilter` so it appears in the API reference at `/api`.

### Never
- Use coverage as starting point for test design (think first, verify with coverage)
- Iterate coverage in tiny steps (if gaps exist, fix ALL at once, then rerun once)
- Cheat: delete code/tests, lower thresholds, or game metrics to appear compliant
- Retest after formatting (unless formatting broke the build)
- Use `sbt test` (repo-wide) when `sbt <project>/test` works
- Call work "optional" or "good enough" to justify not doing it