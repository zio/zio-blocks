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
 * @see [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
```

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
5. **Syntax.** Code in multi-line `{{{` / `}}}` blocks, each marker on its own line (single-line `{{{ x }}}` is not checked). Link types with `[[Type]]` or `[[Type#member]]`. Link a reference page with `@see [[https://zioblocks.com/reference/schema/<page>/ <Title>]]` (keep the trailing slash).

## Examples are compiled

`sbt "++<version>; schema-scaladoc-check/test"` turns every multi-line block in the Scaladoc of `schema` into an object and compiles it. CI runs it on Scala 2.13, 3.3 and 3.9. Run it locally on all three: `sbt --client "++3.9.0; schema-scaladoc-check/test; ++3.3.7; schema-scaladoc-check/test; ++2.13.18; schema-scaladoc-check/test"`.

- Each block compiles on its own. The wrapper adds `import zio.blocks.schema._`; import anything else inside the block.
- Use braces, not indentation syntax.
- Sample data uses real Scala books, such as Zionomicon.
- Show results in comments (`// Book("Zionomicon", 2024)`). They are not checked, so run the example once (with `scala-cli` or in a test) before writing the comment.
- The first line of a block can be a marker:
  - `// scala-3-only`: compiled on Scala 3 only (`given`, `using`, `derives`, union types in a shared file).
  - `// scala-2-only`: compiled on Scala 2 only.
  - `// no-check: <reason>`: not compiled. The reason is required. Use it only when the block cannot compile on its own; if a library bug is the reason, file it in `docs/dev/issues.md`.
- Markers apply only to the first non-empty line of the block. The opening `{{{` and closing `}}}` must each be on their own line: a closing `}}}` on the `*/` line is an error.

## Tracking

`python3 .claude/scripts/scaladoc-coverage.py schema` prints the share of documented declarations per package. It is a progress report, not a target: completeness is judged against the tiers above.
