# Scaladoc style guide

How to write Scaladoc in ZIO Blocks. First applied to the `schema` module (see `docs/superpowers/specs/2026-10-21-schema-scaladoc-design.md`), then to `streams` (`docs/superpowers/specs/2026-10-26-streams-scaladoc-design.md`).

## Who reads it

Two readers: the `/api` site, built by Scala 3 Scaladoc from the JVM projects, and IDE hovers on both Scala 2.13 and 3. Only the second reader sees `js/` and `scala-2/` sources, so document those too.

## Tiers

**Tier 1: core types.** In `chunk`: `Chunk`, `NonEmptyChunk`, `ChunkBuilder`, `ChunkMap`. In `streams`: `Stream`, `Sink`, `Pipeline`, `Reader`, `Writer`. In `schema`: `Schema`, `Reflect`, `Optic` (`Lens`, `Prism`, `Optional`, `Traversal`), `DynamicValue`, `PrimitiveValue`, `Binding`, `Patch`, `Migration`, `Into`, `As`, `JsonCodec`, `Json`, `JsonSchema`, `Deriver`, `Modifier`, `Validation`, `SchemaError`, `Allows`.

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

**Tier 2: every other public member.** One to three sentences. Add `@param`, `@tparam` and `@return` only when the name does not already say it, and `@throws` only when the member really throws.

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

`sbt "++<version>; scaladoc-check/test"` turns every multi-line block in the Scaladoc of the checked modules (`chunk`, `schema` and `streams`) into an object and compiles it. One project serves every module. To check another one, add it to `scaladocCheckedModules` in `build.sbt`; this works when the module's package is `zio.blocks.<module-dir>` with `-` read as `.`, and the comment there lists the modules that need an override first. CI runs it in the `testJVM` job on Scala 2.13.x, 3.9.x and 3.10.x (JDK 17 and 25). Run it locally on all three: `sbt --client "++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test"`.

- Each block compiles on its own. The wrapper adds the import of the module the file belongs to (`import zio.blocks.chunk._` for `chunk`, `import zio.blocks.schema._` for `schema`, `import zio.blocks.streams._` for `streams`); import anything else inside the block.
- Use braces, not indentation syntax.
- Sample data fits the API: numbers, bytes, chars and short strings for collections, port numbers, user ids or word counts for maps. Vary it from one example to the next. Books appear at most once or twice per file; use real Scala books, and if Zionomicon's authors are named, name all three: "John A. De Goes, Adam Fraser and Milad Khajavi".
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
