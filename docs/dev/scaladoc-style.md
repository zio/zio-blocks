# Scaladoc style guide

How to write Scaladoc in ZIO Blocks. First applied to the `schema` module (see `docs/superpowers/specs/2026-10-21-schema-scaladoc-design.md`), then to `streams` (`docs/superpowers/specs/2026-10-26-streams-scaladoc-design.md`).

## Who reads it

Two readers: the `/api` site, built by Scala 3 Scaladoc from the JVM projects, and IDE hovers on both Scala 2.13 and 3. Only the second reader sees `js/` and `scala-2/` sources, so document those too.

## Tiers

**Tier 1: core types.** In `jwt`: `Jwt` (with `JwtSignOptions`, `JwtDecodeOptions`, `JwtLimits`), `JwtClaims`, `JwtHeader`, `Algorithm`, `JwtError`, `JwtAudience`. In `sql`: `Frag` and the `sql"..."` interpolator, `Transactor`, `JdbcTransactor`, `DbCon`, `DbTx`, `DbCodec`, `Table`, `Repo`, `SqlQuery`, `Rel`; in `sql-zio`: `TransactorZIO`. In `chunk`: `Chunk`, `NonEmptyChunk`, `ChunkBuilder`, `ChunkMap`. In `streams`: `Stream`, `Sink`, `Pipeline`, `Reader`, `Writer`. In `http-model`: `Request`, `Response`, `Body`, `URL`, `Path`, `QueryParams`, `Headers`, `Header` (with `Header.Codec`), `Method`, `Status`, `Cookie`, `ServerSentEvent`. In `schema`: `Schema`, `Reflect`, `Optic` (`Lens`, `Prism`, `Optional`, `Traversal`), `DynamicValue`, `PrimitiveValue`, `Binding`, `Patch`, `Migration`, `Into`, `As`, `JsonCodec`, `Json`, `JsonSchema`, `Deriver`, `Modifier`, `Validation`, `SchemaError`, `Allows`. In `async`: `Async` (the type alias and its companion), `Async.Running`, `Completer`, `Cancelable`, `Failure`, `AsyncSelector`, `AsyncInterop`, `Pollable`.

- The type: what it is, when to use it, one example, and `@see` the reference page.
- Its key methods (the ones its reference page shows): one example each.

```scala
/**
 * A `Lens` focuses on a part of a record that is always present, such as a field of a case class.
 * Use it to read or replace that part without rebuilding the record by hand.
 *
 * {{{
 * case class Server(host: String, port: Int)
 * object Server extends CompanionOptics[Server] {
 *   implicit val schema: Schema[Server] = Schema.derived
 *   val port: Lens[Server, Int] = $(_.port)
 * }
 *
 * Server.port.replace(Server("localhost", 80), 8080) // Server(localhost,8080)
 * }}}
 *
 * @see [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
```

**Tier 2: every other public member.** One to three sentences. Add `@param`, `@tparam` and `@return` only when the name does not already say it, and `@throws` only when the member really throws. Write the exception's full name (`@throws java.lang.IllegalArgumentException`): the Scala 2 doc tool cannot link the short name and fails the `doc` task.

```scala
/** Returns the default value of this schema's type, if its schema declares one. */
def getDefaultValue: Option[A]
```

**Tier 3: internals.** `private[...]` code and `*VersionSpecific` internals. In `streams`: the `streams.internal` package. In `schema`: `MacroUtils`, `CommonMacroOps`, `IntoVersionSpecific`, `MigrationBuilderMacros`. One line, or nothing.

## Rules

1. **Write once.** A member declared in `scala-2/` and `scala-3/`, or `jvm/` and `js/`, gets the same text in every copy. Check with `python3 .claude/scripts/scaladoc-coverage.py <module> --drift`.
2. **Tell the truth.** Read the implementation before writing. Every claim ("returns `Left` when ...", "throws ...") must match the code or a test. When the code looks wrong, do not describe the wrong behaviour as intended: describe what it really does, call it "a known bug", and report it as AGENTS.md describes (a ready-to-paste entry for `docs/dev/library-issues.md`, sent to the coordinating session).
3. **Prose.** Follow the `docs-writing-style` skill: present tense, active voice, no "simply" or "just". Start with what the member does, not "This method ...".
4. **Format.** Leave wrapping to `scalafmt`. One blank comment line between paragraphs and before the tags.
5. **Syntax.** Code in multi-line `{{{` / `}}}` blocks, each marker on its own line (single-line `{{{ x }}}` is not checked). Link types with `[[Type]]` or `[[Type#member]]`. Link a reference page with `@see [[https://zioblocks.com/reference/<module>/<page>/ <Title>]]`, as in `reference/schema/optics/` or `reference/streams/core/sink/` (keep the trailing slash).

## Examples are compiled

`sbt "++<version>; scaladoc-check/test"` turns every multi-line block in the Scaladoc of the checked modules (`async`, `chunk`, `codegen`, `config`, `config-hocon`, `config-json`, `config-yaml`, `html`, `http-model`, `jwt`, `mux`, `openapi`, `ringbuffer`, `schema`, `schema-avro`, `schema-bson`, `schema-csv`, `schema-thrift`, `schema-toon`, `schema-xml`, `schema-yaml`, `scope`, `smithy`, `streams` and `typeid`) into an object and compiles it. One project serves every module. To check another one, add it to `scaladocCheckedModules` in `build.sbt`; when the module's package is not `zio.blocks.<module-dir>` with `-` read as `.`, first add it to `packageOverrides` in `project/ScaladocSnippets.scala` (`http-model` is `zio.http`). CI runs it in the `testJVM` job on Scala 2.13.x, 3.9.x and 3.10.x (JDK 17 and 25). Run it locally on all three: `sbt --client "++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test"`.

Modules built for Scala 3 only (`sql` and `sql-zio`) are checked the same way by a second project, `scaladoc-check-scala3`, which builds on 3.9.x and 3.10.x only: `sbt --client "++3.9.0; scaladoc-check-scala3/test; ++3.10.0; scaladoc-check-scala3/test"`. Add such a module to `scaladocCheckedScala3Modules` in `build.sbt`. Its examples need no `// scala-3-only` marker. `sql` examples get `import zio.blocks.sql.{zio => _, _}` instead of `import zio.blocks.sql._`: `sql-zio` shares the classpath, and its package `zio.blocks.sql.zio` would otherwise hide the root `zio` package from the example. In a `sql-zio` example, put `import zio.blocks.sql._` after every other `zio` import.

- Each block compiles on its own. The wrapper adds the import of the module the file belongs to (`import zio.blocks.chunk._` for `chunk`, `import zio.blocks.schema._` for `schema`, `import zio.http._` for `http-model`); import anything else inside the block.
- Use braces, not indentation syntax.
- Sample data fits the API: numbers, bytes, chars and short strings for collections, port numbers, user ids or word counts for maps. Vary it from one example to the next.
- Show results in comments (`// Some(80)`). They are not checked, so run the example once (with `scala-cli` or in a test) before writing the comment.
- The first line of a block can be a marker:
  - `// scala-3-only`: compiled on Scala 3 only (`given`, `using`, `derives`, union types in a shared file).
  - `// scala-2-only`: compiled on Scala 2 only.
  - `// no-check: <reason>`: not compiled. The reason is required. Use it only when the block cannot compile on its own; if a library bug is the reason, report it as AGENTS.md describes.
- A first line that starts like a marker but is not one of these three exact forms (`// scala-3-only: uses given`, `// scala3-only`) is an error, not a plain comment.
- Markers apply only to the first non-empty line of the block. The opening `{{{` and closing `}}}` must each be on their own line: a closing `}}}` on the `*/` line is an error.
- A code line inside a `{{{` block must not contain `}}}`, for example in an output comment such as `// {"a":{"b":1}}}`: Scaladoc ends the code block at the first `}}}`, so the extractor reports an error. Reword the line or drop the output comment.

Compiler warnings in examples do not fail the build (deprecations included), so check the sbt log for them.

The extractor also compiles ```` ```scala ```` fences, but the house style is `{{{`.

A compile error names the generated file `Snippet_<path>_L<n>.scala`; the source line is n + (generated line - 6), and the generated file's first line names the source `path:line`.

## Tracking

`python3 .claude/scripts/scaladoc-coverage.py <module>` (for example `schema` or `streams`) prints the share of documented API members per package: declarations at the top level of a file or inside types, not locals inside method bodies or members of private and protected types, except those of a `private[pkg]` trait, which a public object mixes in. It is a progress report, not a target: completeness is judged against the tiers above.
