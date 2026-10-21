# Schema module Scaladoc: design

Date: 2026-10-21. Branch: `schema-scaladoc`.

## Problem

The Scaladoc of the `schema` module is thin, uneven, and sometimes wrong.

- About 85k lines of main sources hold roughly 6,400 API-level declarations (members of top-level types, excluding `private`/`protected`/`override`). About 19% have a Scaladoc comment (heuristic count, 2026-10-21).
- The core types are the least covered: `Reflect` 3 of 322, `Optic` 4 of 221, `PrimitiveValue` 0 of 193, `Binding` 2 of 102, `Schema` 6 of 98 (`getInstance`, `derive`, `decode` and others have none).
- Some existing Scaladoc contradicts the code. `docs/dev/issues.md` already lists several cases (`Deriver#withInstance` merge order, `Schema.fromJsonSchema`, toon `ArrayFormat`).
- Only 18 `@example` tags and 19 files with `{{{ }}}` blocks exist, and nothing compiles those blocks, so they drift.
- The `/api` site is a Scala 3 unidoc of the JVM projects only. Scaladoc in `js/` and `scala-2/` sources never appears there, only in IDE hovers.

## Goal

Every user-facing type and member of `schema` has accurate Scaladoc that reads well both on `/api` and in IDE hovers on Scala 2.13 and 3, and every code example in it compiles.

## Decisions

| Question                | Decision                                                                                                         |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| Audience                | Both `/api` and IDE hovers. Copies in `scala-2/`, `scala-3/`, `jvm/` and `js/` are documented too.               |
| Depth                   | Tiered (see "Style guide").                                                                                      |
| Keeping examples correct | A snippet checker compiles every `{{{ }}}` block on both Scala versions in CI.                                  |
| Rollout                 | Tooling first (PR 0), then one PR per wave of related types.                                                     |
| Checker location        | A new, unpublished sbt subproject `schema-scaladoc-check` (approved as the AGENTS.md "ask first" for subprojects). |

## Style guide

Lives in `docs/dev/scaladoc-style.md`, linked from `AGENTS.md`. Every wave follows it.

1. **Tiers.**
   - **Tier 1, core types:** `Schema`, `Reflect`, `Optic` (`Lens`, `Prism`, `Optional`, `Traversal`), `DynamicValue`, `PrimitiveValue`, `Binding`, `Patch`, `Migration`, `Into`, `As`, `JsonCodec`, `Json`, `JsonSchema`, `Deriver`, `Modifier`, `Validation`, `SchemaError`, `Allows`. The type gets an overview (what it is, when to use it), one compiled `{{{ }}}` example, and `@see` to its `docs/reference/schema/*.md` page on the website. Its key methods get one example each.
   - **Tier 2, every other public member:** one to three sentences. `@param`, `@tparam` and `@return` only when the name does not already say it. `@throws` only when the member really throws.
   - **Tier 3, macro plumbing:** `private[schema]` code, `*VersionSpecific` internals, `MacroUtils`, `CommonMacroOps`, `IntoVersionSpecific`, `MigrationBuilderMacros`. One line or nothing.
2. **Write once.** A member declared in both `scala-2/` and `scala-3/` (or `jvm/` and `js/`) gets the same text in every copy, so hovers agree across versions. The coverage script reports copies whose text differs.
3. **Truth rule.** Read the implementation before writing. Every claim ("returns `Left` when ...", "throws ...") must match the code or a test. When the code looks wrong, do not document the wrong behaviour as intended: file it in `docs/dev/issues.md` in the same PR, following that file's entry shape.
4. **Prose.** Follow the `docs-writing-style` skill: present tense, active voice, no "simply" or "just".
5. **Syntax.** `{{{ }}}` code blocks, `[[Type]]` and `[[Type#member]]` links, `@see` with the website URL of the reference page. PR 0 confirms that Scala 3 Scaladoc renders `{{{` blocks and wiki links correctly in `/api`; if it does not, the rule switches to Markdown fences and links before any wave starts.
6. **Snippets.** Each block compiles on its own after an implicit `import zio.blocks.schema._`. Further imports go inside the block. A block that cannot compile on its own starts with a `// no-check: <reason>` line. Sample data uses real Scala books (for example Zionomicon), in line with the docs.

## PR 0: tooling

No Scaladoc content beyond fixing existing snippets.

1. **Spike (throwaway).** Run `docs/generateApiDocs` with one `{{{` block and one `[[link]]` added, inspect the HTML, and fix rule 5 of the style guide.
2. **Subproject `schema-scaladoc-check`.** JVM only, `publish / skip := true`, cross-built on the same Scala versions as `schema.jvm`, `dependsOn(schema.jvm)`. A `Compile / sourceGenerators` task:
   - reads every `.scala` file under `schema/shared/src/main` and `schema/jvm/src/main`, including the `scala-2/` or `scala-3/` (and `scala-3.5+`/`scala-3.5-`) directories that match the Scala version being compiled;
   - extracts each `{{{ ... }}}` block inside a `/** ... */` comment, strips the ` * ` margin, and skips blocks whose first line is `// no-check`;
   - writes one `object Snippet_<File>_<line> { import zio.blocks.schema._; <code> }` per block, so a compile error names the source file and line.

   Snippets are compiled, not run, so `// result` comments in them are not checked.
3. **CI.** Add `schema-scaladoc-check/compile` to `testJVMScala2Command` and `testJVMScala3Command` in `build.sbt` (the commands behind the `testJVM` alias that CI runs) so a broken example fails CI on both Scala versions.
4. **Coverage script** `scripts/scaladoc-coverage.py`. Prints the share of documented API-level declarations per package and lists cross-version copies whose Scaladoc differs. Report only; no threshold, so nobody games a number.
5. **Docs.** `docs/dev/scaladoc-style.md` and a link from `AGENTS.md`.
6. **Seed.** The checker runs over the existing `{{{` blocks (about 19 files). Each failing block is fixed, or marked `// no-check` with an `issues.md` entry when the fix needs real work.

## Content waves

One PR per wave after PR 0. The waves touch disjoint files, so they can run in parallel worktrees. A wave whose diff grows past about 3,000 lines splits into `a` and `b`.

| Wave                     | Files                                                                                                                                                                             | Main tier |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| 1. Entry point           | `Schema`, `package.scala`, `syntax` (2 and 3), front methods of `SchemaCompanionVersionSpecific` (2 and 3), `SchemaVersionSpecific`, `SchemaError`, `Modifier`, `Validation`, `SchemaAspect` | 1 |
| 2. Structure             | `Reflect`, `Term`, `Lazy`, `Reflectable`, `ReflectTransformer`, `RebindTransformer`, `ReflectPrinter`, `PrimitiveType`, `PrimitiveValue`                                          | 1, 2      |
| 3. Binding               | `binding/*` in `shared`, `scala-2`, `scala-3`, `scala-3.5+`, `scala-3.5-`, `jvm`, `js`                                                                                            | 2         |
| 4. Optics                | `Optic`, `OpticCheck`, `DynamicOptic`, `PathParser`, `CompanionOptics`, `DerivedOptics`, `PathInterpolator`, `PathMacros` (2 and 3)                                              | 1         |
| 5. Dynamic               | `DynamicValue`, `DynamicValueSelection`, `DynamicValueMergeStrategy`, `DynamicValueType`, `DynamicSchema`, `SchemaExpr`, `DynamicSchemaExpr`, `SchemaMatch`, `SchemaRepr`, `SchemaParser`, `SchemaMetadata` | 1, 2 |
| 6. Derivation and codecs | `derive/*`, `codec/*`, `IsCollection`, `IsIntegral`, `IsMap`, `IsNumeric`, `NameMapper`, `comptime/*` (2 and 3), `DocsSchemas`, `TypeIdSchemas`, `Platform` (jvm and js)          | 1, 2      |
| 7. Transform and evolve  | `Into`, `As`, `ToStructural` (with 2 and 3 parts), `patch/*`, `migration/*` (with 2 and 3 `MigrationBuilder` syntax and macros)                                                  | 1         |
| 8a. JSON model           | `json/*` in `shared`, `scala-2`, `scala-3`, except `JsonReader` and `JsonWriter`                                                                                                  | 1, 2      |
| 8b. JSON I/O             | `JsonReader` and `JsonWriter` in `jvm` and `js` (same API, same text in both)                                                                                                     | 2         |

Progress lives in a new "Scaladoc: schema" section of `docs/dev/backlog.md` with columns wave, status, PR, coverage before and after.

## Workflow for each wave

1. **Research.** Read each file, its tests under `schema/shared/src/test`, and its reference page under `docs/reference/schema/`. Reference pages are mdoc-checked, so they are the first source for examples. Large waves may delegate this to the `docs-researcher` agent.
2. **Write** the Scaladoc by tier.
3. **Fact-check.** A `docs-reviewer` pass compares each claim with the source. Contradictions in the code or in reference pages go into `docs/dev/issues.md` in the same PR; trivial reference-page errors are fixed in the same PR.
4. **Verify.** The change is comments only, so test suites are not rerun. Run:
   - `schema-scaladoc-check/compile` on Scala 3.9.0 and on 2.13;
   - `schemaJVM/doc` on both versions, with no new warnings (broken `[[links]]` show up here);
   - `fmtDirty`, then `sbt "++2.13; check"`.
5. **PR.** The body says in plain English what was documented, the coverage before and after, and any issues filed. The backlog row is updated. Review fixes go in separate commits per related group.

## Done

- Every wave is merged.
- Every tier 1 and tier 2 public member has Scaladoc (checked against the tiers, not a percentage).
- Every snippet compiles on Scala 2.13 and 3.
- Cross-version copies carry the same text.
- No known Scaladoc contradicts the code, except where the contradiction is filed in `docs/dev/issues.md`.

## Out of scope

- Behaviour changes. Bugs found are filed, not fixed.
- Other modules (`schema-avro`, `schema-toon`, and the rest). The tooling from PR 0 is written so that it can be pointed at them later.
- Test sources.
