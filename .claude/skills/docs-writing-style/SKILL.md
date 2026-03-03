---
name: docs-writing-style
description: Shared prose style rules for ZIO Blocks documentation. Include when writing any ZIO Blocks documentation page — reference pages, how-to guides, or tutorials.
---

# ZIO Blocks Documentation Writing Style

## Prose Style Rules

- **Project name**: "ZIO Blocks" (not "zio-blocks").
- **Person**: Use "we" when guiding the reader or walking through examples ("we can create...",
  "we need to..."). Use "you" when addressing the reader's choices ("if you need...", "you might
  want to...").
- **Tense**: Present tense ("returns", "creates", "modifies").
- **Concision**: Keep prose short. Let code examples do the heavy lifting. If you find yourself
  writing more than 4 sentences of prose without a code block, you're being too verbose.
- **Don't pad**: No filler phrases like "as we can see" or "it's worth noting that". Just state
  the fact.
- **No emojis**: Unless the user explicitly requests them.
- **Capitalize bullet sentences**: When a bullet point is a full sentence, start it with a capital letter.
- **No manual line breaks in prose**: Do not hard-wrap paragraph text at a fixed column. Write each paragraph as one continuous line.
- **ASCII art**: Use it for diagrams showing data flow, type relationships, or architecture.
  Readers find these very helpful for understanding how pieces fit together.
- **No exhaustive API coverage in guides**: Only document the methods and types that serve the
  guide's goal. Link to reference pages for full API details.
- **Link to related docs**: Use relative paths, e.g., `[TypeName](./type-name.md)`.

## Referencing Types, Operations, and Constructors

Apply these conventions consistently in all prose, section headings, and inline code:

- **Always qualify with the type name** — never refer to a method or constructor by its bare name. Write `BindingResolver.empty`, not `empty`; write `ZIO#map`, not `map`. The type name is essential context for the reader.
- **Type name alone** — when talking about the type itself, use only its name with no qualifier:
  "derives automatically via `As`", "`Into` is a one-way conversion".
- **Instance method** — use `TypeName#methodName` (the `#` convention signals a non-static member):
  `As#from`, `As#into`, `As#reverse`, `Into#into`.
- **Companion object operation or constructor** — use `TypeName.methodName` (the `.` convention
  signals a companion/static member): `As.derived`, `As.apply`, `Into.derived`, `Into.apply`.

## Heading and Code Block Layout Rules

- **No bare subheaders**: Never place a `###` or `####` subheader immediately after a `##` header
  with nothing in between. Always write at least one sentence of explanation before the first
  subheader.
- **No lone subheaders**: Never create a subsection with only one child. If a `##` section would
  have only one `###`, remove the subheader entirely and place the content directly under the
  parent heading.
- **Every code block must be preceded by an introductory prose sentence**: The content immediately
  before a code block's opening fence must always be a prose sentence — never a heading alone and
  never blank space alone. This applies universally:
  - After a heading: write at least one sentence before the first code block.
  - Between two consecutive code blocks: write a short bridging sentence.
  - The sentence must be surrounded by blank lines on both sides (standard Markdown spacing).
  - **The sentence must end with a colon (`:`)**. A colon signals to the reader that code follows.

## Code Block Rules

- **Always include imports**: Every code block must start with the necessary import statements.
- **One concept per code block**: Each code block demonstrates one cohesive idea.
- **Prefer `val` over `var`**: Use immutable patterns everywhere.
- **Never hardcode expression output in comments**: Do not annotate expression results with inline
  comments such as `// None`, `// Some(SchemaError)`, or `// "hello"` — these go stale and can be
  wrong. The fix is **not** to remove the comment and leave the block as `mdoc:compile-only` — that
  still hides the result. The fix is to restructure the block so mdoc evaluates and renders the
  output:
  1. Move all type definitions and setup `val`s into a `mdoc:silent:reset` block (or `mdoc:silent`
     if scope continuity is needed). Use `mdoc:silent:reset` in reference pages to avoid name
     conflicts between independent sections.
  2. Add a short bridging sentence ending in `:`.
  3. Put the expressions whose results matter in a bare `mdoc` block — mdoc renders them as
     REPL-style output automatically.
  See **`docs-mdoc-conventions`** for the complete modifier table and the Setup + Evaluated Output
  pattern.
- **Code snippet description**: When showing example code snippets, explain what they do and why
  they are relevant. Don't just show code without context.

## Scala Version

All code in documentation and companion example files **must use Scala 2.13.x syntax**. When in
doubt, check the companion example files — they are the source of truth for syntax style.

---
