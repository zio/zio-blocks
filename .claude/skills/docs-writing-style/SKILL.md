---
name: docs-writing-style
description: Shared prose style rules for ZIO Blocks documentation. Include when writing any ZIO Blocks documentation page — reference pages, how-to guides, or tutorials. Also include docs-mdoc-conventions for code block modifiers.
allowed-tools: Read, Glob, Grep
---

# ZIO Blocks Documentation Writing Style

## Prose Style Rules

1. **Person pronouns**: Use "we" when guiding the reader or walking through examples ("we can create...", "we need to..."). Use "you" when addressing the reader's choices ("if you need...", "you might want to...").
2. **Tense**: Present tense only ("returns", "creates", "modifies").
3. **No padding/filler**: No filler phrases like "as we can see" or "it's worth noting that". Just state the fact.
4. **Bullet capitalization**: When a bullet point is a full sentence, start it with a capital letter.
5. **No manual line breaks in prose**: Do not hard-wrap paragraph text at a fixed column. Write each paragraph as one continuous line.
6. **ASCII art usage**: Use it for diagrams showing data flow, type relationships, or architecture. Readers find these very helpful for understanding how pieces fit together.
7. **Link to related docs**: Use relative paths, e.g., `[TypeName](./type-name.md)`.

## Referencing Types, Operations, and Constructors

8. **Always qualify method/constructor names**: Never refer to a method or constructor by its bare name. Write `BindingResolver.empty`, not `empty`; write `ZIO#map`, not `map`. The type name is essential context for the reader. This rule applies everywhere: in prose, headings, comparative phrases ("use `Context#add` vs. `Context.apply`", not "use `add` vs. `apply`"), and bullet points. Method/operation references must be wrapped in backticks (inline code blocks) to make them searchable and visually distinct as code, ensuring consistency across all contexts (including subsection headings at ### and #### levels).

   **Examples:**
   - **Instance methods** — use `TypeName#methodName`: `As#from`, `As#into`, `As#reverse`, `Into#into`
   - **Companion object/static members** — use `TypeName.methodName`: `As.derived`, `As.apply`, `Into.derived`, `Into.apply`
   - **In headings** — same rules apply: `### Context#add`, `#### Wire.shared[T]` (always in backticks)

9. **Type name alone rule**: When talking about the type itself (not a method), use only its name with no qualifier: "derives automatically via `As`", "`Into` is a one-way conversion".

## Frontmatter Titles

10. **No duplicate markdown heading**: Do not create a markdown heading (`#`) that duplicates the frontmatter title. The frontmatter title is sufficient. Start the document content with a `##` section heading.

## Heading and Code Block Layout Rules

11. **Heading hierarchy**: Use `##` for major sections, `###` for subsections, and `####` for subsubsections. All three levels are fully supported and encouraged.
12. **No bare subheaders**: Never place a `###` or `####` subheader immediately after a `##` header with nothing in between. Always write at least one sentence of explanation before the first subheader.
13. **No lone subheaders**: Never create a subsection with only one child. If a `##` section would have only one `###`, remove the subheader entirely and place the content directly under the parent heading. The same rule applies to `###` → `####`.
14. **When to use `####`**: Group multiple related items (use cases, examples, sub-patterns) under a single `###` heading by using `####` for each item. This creates visual hierarchy and makes the section more scannable. Example: `### Use Cases` → `#### Polyglot configuration systems` → `#### Schema-driven migrations`.
15. **Every code block must be preceded by an introductory prose sentence**: The content immediately before a code block's opening fence must always be a prose sentence — never a heading alone and never blank space alone. This applies universally:
    - After a heading: write at least one sentence before the first code block.
    - Between two consecutive code blocks: write a short bridging sentence.
    - The sentence must be surrounded by blank lines on both sides (standard Markdown spacing).
    - **The sentence must end with a colon (`:`)**. A colon signals to the reader that code follows.

## Code Block Rules

16. **Always include imports**: Every code block must start with the necessary import statements.
17. **One concept per code block**: Each code block demonstrates one cohesive idea.
18. **Prefer `val` over `var`**: Use immutable patterns everywhere.
19. **Never hardcode expression output in comments**: Do not annotate expression results with inline comments such as `// None`, `// Some(SchemaError)`, or `// "hello"` — these go stale and can be wrong. The fix is **not** to remove the comment and leave the block as `mdoc:compile-only` — that still hides the result. The fix is to restructure the block so mdoc evaluates and renders the output:
    1. Move all type definitions and setup `val`s into a `mdoc:silent:reset` block (or `mdoc:silent` if scope continuity is needed). Use `mdoc:silent:reset` in reference pages to avoid name conflicts between independent sections.
    2. Add a short bridging sentence ending in `:`.
    3. Put the expressions whose results matter in a bare `mdoc` block — mdoc renders them as REPL-style output automatically.
    
    See **`docs-mdoc-conventions`** for the complete modifier table and the Setup + Evaluated Output pattern.
20. **Code snippet description**: When showing example code snippets, explain what they do and why they are relevant. Don't just show code without context.

## Table Formatting

21. **Pad column alignment**: Pad column headers and separators with spaces to align content vertically. This makes tables more scannable and readable than minimal-width formatting.

## Scala Version

22. **Default to Scala 2.13.x syntax**: All code in documentation and companion example files **MUST use Scala 2.13.x syntax**. When in doubt, check the companion example files — they are the source of truth for syntax style. Specifically, MUST use `import x._` (Scala 2.13) for wildcard imports, never `import x.*` (Scala 3.x), unless explicitly demonstrating version-specific syntax in a tabbed comparison.
23. **Use tabs for version-specific syntax**: When a section shows syntax that genuinely differs between Scala 2 and Scala 3 (e.g., `using` vs `implicit`, native union types vs backtick infix, or wildcard imports), use tabbed code blocks instead of sequential prose. See `docs-mdoc-conventions` for the exact tab structure. Scala 2 is always the default tab (`defaultValue="scala2"`).

---
