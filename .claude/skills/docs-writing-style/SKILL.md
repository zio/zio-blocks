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

9. **Type name alone rule**: When talking about the type itself (not a method), use only its name with no qualifier, and always wrap it in backticks: "`As` derives automatically", "`Into` is a one-way conversion", "convert the data to `List`". This applies to all type references in prose, including standard library types like `List`, `Vector`, `Set`, `String`, `Int`, `Array`, etc.

## Frontmatter Titles

10. **No duplicate markdown heading**: Do not create a markdown heading (`#`) that duplicates the frontmatter title. The frontmatter title is sufficient. Start the document content with a `##` section heading.

## Heading and Code Block Layout Rules

11. **Heading hierarchy**: Use `##` for major sections, `###` for subsections, and `####` for subsubsections. All three levels are fully supported and encouraged.
12. **No bare subheaders**: Never place a `###` or `####` subheader immediately after a `##` header with nothing in between. Always write a brief, narrative introduction that explains the purpose and context—tell readers *why* they need this section and what problems it solves. Example: "Often you need to work with portions of a chunk rather than the whole. These operations let you keep elements from the ends, skip unwanted portions, extract contiguous ranges by position, and intelligently partition chunks based on predicates or conditions:"
13. **No lone subheaders**: Never create a subsection with only one child. If a `##` section would have only one `###`, remove the subheader entirely and place the content directly under the parent heading. The same rule applies to `###` → `####`.
14. **When to use `####`**: Group multiple related items (use cases, examples, sub-patterns) under a single `###` heading by using `####` for each item. This creates visual hierarchy and makes the section more scannable. Example: `### Use Cases` → `#### Polyglot configuration systems` → `#### Schema-driven migrations`.
15. **Every code block must be preceded by an introductory prose sentence**: The content immediately before a code block's opening fence must always be a prose sentence — never a heading alone and never blank space alone. This applies universally:
    - After a heading: write at least one sentence before the first code block.
    - Between two consecutive code blocks: write a contextualized bridging sentence that explains what the next block demonstrates (especially important for execution blocks that follow signature blocks).
    - The sentence must be surrounded by blank lines on both sides (standard Markdown spacing).
    - **The sentence must end with a colon (`:`)**. A colon signals to the reader that code follows.
    
    **Example of correct structure:**
    
    When you write: "Here's how to create a chunk with initial elements:" followed by a code fence, the sentence ends with colon — correct. ✅
    
    **Example of incorrect structure:**
    
    When you write a heading like "#### `Chunk#map` — Transform Elements" and immediately follow it with a code fence (with no prose in between), it violates Rule 15. ❌

## Code Block Rules

16. **Always include imports**: Every code block must start with the necessary import statements.
17. **One concept per code block**: Each code block demonstrates one cohesive idea.
18. **Prefer `val` over `var`**: Use immutable patterns everywhere.
19. **Never hardcode expression output in comments**: Do not annotate results with inline comments like `// None` or `// "hello"` — they go stale. Don't just add `mdoc:compile-only` to hide them. Instead, let mdoc render the output automatically. Default: use a single bare `mdoc` block and let all vals display their rendered output. Only split into `mdoc:silent:reset` + bare `mdoc` when setup produces verbose boilerplate that obscures the final result. See **`docs-mdoc-conventions`** for full modifier reference.
20. **Code snippet description**: When showing example code snippets, explain what they do and why they are relevant. Provide context before every code block with a sentence that introduces it, explains its purpose, and ends with a colon (`:`). The introduction must be contextualized — relate it to what the code demonstrates or why it matters in context (avoid generic phrases like "here's an example" or "we can see this in action").

## Table Formatting

21. **Pad column alignment**: Pad column headers and separators with spaces to align content vertically. This makes tables more scannable and readable than minimal-width formatting.

## Scala Version

22. **Default to Scala 2.13.x syntax**: All code in documentation and companion example files **MUST use Scala 2.13.x syntax**. When in doubt, check the companion example files — they are the source of truth for syntax style. Specifically, MUST use `import x._` (Scala 2.13) for wildcard imports, never `import x.*` (Scala 3.x), unless explicitly demonstrating version-specific syntax in a tabbed comparison.
23. **Use tabs for version-specific syntax**: When a section shows syntax that genuinely differs between Scala 2 and Scala 3 (e.g., `using` vs `implicit`, native union types vs backtick infix, or wildcard imports), use tabbed code blocks instead of sequential prose. See `docs-mdoc-conventions` for the exact tab structure. Scala 2 is always the default tab (`defaultValue="scala2"`).

## Dependency Declarations

24. **Use @VERSION@ for versions**: In installation sections, always use the literal `@VERSION@` (`@VERSION@` placeholder) in sbt dependency coordinates. The build system substitutes it during publish. Do not instruct readers to replace a placeholder.

## Manual Verification for Non-Mechanical Rules

Rules 2–4, 7–10, 12, 14, 17–23 require manual verification. Here are key examples to watch for:

**Rule 2 (Tense)** — Use present tense only:
- ❌ "will create" (future)
- ❌ "created" (past)
- ✅ "creates"

**Rule 3 (No padding)** — Remove filler phrases:
- ❌ "As we can see, the chunk will accept..."
- ❌ "It's worth noting that chunks are immutable"
- ✅ "Chunks are immutable"

**Rule 8 (Qualify method names)** — Always include the type:
- ❌ "Use `map` to transform elements"
- ✅ "Use `Chunk#map` to transform elements"

**Rule 9 (Type name alone)** — No qualifier when referring to the type itself:
- ❌ "Use `Chunk#Chunk` for building chunks"
- ✅ "Use `Chunk` for building chunks"

**Rule 20 (Contextualized descriptions)** — Avoid generic intro phrases:
- ❌ "Here's an example:"
- ❌ "We can see this in action:"
- ✅ "Splitting creates two chunks from a single index:"

## Mechanical Validation

To validate documentation against mechanical style rules, run:

```
bash .claude/skills/docs-writing-style/check-docs-style.sh <file.md>
```

This checks Rules 5, 6, 8, 11, 13, and 15 for mechanical violations. Exit code `0` means all checked rules pass; exit code `1` means violations were found with details printed to stdout.

**Rule 8** detects unqualified methods by identifying camelCase names in backticks, excluding safe names (variables, types), and flagging only methods that appear qualified elsewhere. Update `SAFE_NAMES` in `check-docs-style.sh` with new commonly-used names to avoid false positives. After running the check, please update the `safeMethodNames` set in `check-docs-style.sh` with any new method names that are commonly used and should not be flagged when unqualified.

**Note:** Rule 16 (imports) is not checked mechanically. Verify that all code blocks include necessary imports by visual inspection.

---
