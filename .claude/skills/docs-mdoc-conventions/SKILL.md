---
name: docs-mdoc-conventions
description: Shared reference for mdoc code block modifiers and Docusaurus admonitions used across ZIO Blocks documentation skills. Include when writing any documentation that contains Scala code blocks.
allowed-tools: Read, Glob, Grep
---

# ZIO Blocks Documentation Conventions

## Compile-Checked Code Blocks with mdoc

This project uses mdoc to compile-check executable code blocks (examples, use cases). 

**Exception:** Data type definitions use plain ` ```scala ` without mdoc modifiers — they are structural illustrations, not executable examples.

### Modifier Summary

| Modifier                   | Rendered Output           | Scope                         | Use When                                                                 |
|----------------------------|---------------------------|-------------------------------|--------------------------------------------------------------------------|
| `scala mdoc:compile-only`  | Source code only          | Isolated (no shared state)    | Self-contained examples where evaluated output is NOT needed             |
| `scala mdoc:silent`        | Nothing (hidden)          | Shared with subsequent blocks | Setting up definitions needed by later blocks                            |
| `scala mdoc:silent:nest`   | Nothing (hidden)          | Shared, wrapped in `object`   | Re-defining names already in scope                                       |
| `scala mdoc`               | Source + evaluated output | Shared with subsequent blocks | When the evaluated result of expressions should be shown to the reader   |
| `scala mdoc:invisible`     | Nothing (hidden)          | Shared with subsequent blocks | Importing hidden prerequisites                                           |
| `scala mdoc:silent:reset`  | Nothing (hidden)          | Resets all prior scope        | Starting a clean scope mid-document                                      |
| `scala` (no mdoc)          | Source code only          | Not compiled                  | Pseudocode, ASCII diagrams, conceptual snippets                          |

### Key Rules

- **`mdoc:compile-only`** is the **default** for structural or setup-only examples where no output
  needs to be shown. Each block is compiled in isolation — definitions do NOT carry over between
  `compile-only` blocks.
- **`mdoc:silent`** defines types/values that **subsequent blocks** can reference (scope persists
  until reset). Nothing is rendered. You cannot redefine the same name — use `silent:nest` for that.
- **`mdoc:silent:nest`** is like `silent` but wraps code in an anonymous `object`, allowing you to
  **shadow names** from earlier blocks (e.g., redefining `Person` with different fields in a later
  section).
- **`mdoc:silent:reset`** wipes **all** accumulated scope and starts fresh. Use when `silent:nest`
  wouldn't suffice (e.g., switching to a completely different topic mid-document).
- **`mdoc`** (no qualifier) shows **source + evaluated output** (REPL-style). Use this whenever you
  would otherwise write `// Right(42L)`, `// Some("hello")`, or any result comment — let mdoc render
  the actual evaluated output instead. Requires definitions to be in scope from a prior
  `silent`/`silent:nest` block.
- **`mdoc:invisible`** is like `silent` but signals "hidden imports only." Rare — prefer including
  imports directly in the `mdoc:silent` setup block (so they are in shared scope) or inside a
  `compile-only` block (for self-contained examples). Use `invisible` only when you need imports
  shared across blocks but must not appear anywhere in the rendered output.
- **No mdoc** (plain ` ```scala `) — not compiled. Use for pseudocode, ASCII diagrams, type
  signatures for illustration, or sbt/non-Scala syntax.

### Choosing the Right Modifier

1. Self-contained example where output doesn't need to be shown? → `mdoc:compile-only`
2. Later blocks need these definitions? → `mdoc:silent` (first time) or `mdoc:silent:nest`
   (redefining)
3. Need a completely clean scope? → `mdoc:silent:reset`
4. **Showing the result of expressions** (return values, decoded output, computed values)? →
   `mdoc:silent` for setup + `mdoc` to render evaluated output. **Never manually write `// result`
   comments when mdoc can show the real output.**
5. Not real Scala? → plain ` ```scala ` or ` ```text `

### Pattern: Setup + Evaluated Output

When a code snippet evaluates expressions whose results are meaningful to the reader, split it into
two blocks:

````
```scala mdoc:silent
import zio.blocks.schema.Into

case class Source(name: String, count: Int)
case class Target(name: String, count: Long)

val conv = Into.derived[Source, Target]
```

With `conv` in scope, we can call `into` and see the result:

```scala mdoc
conv.into(Source("events", 100))
```
````

The `mdoc` block renders as:
```
conv.into(Source("events", 100))
// val res0: Either[zio.blocks.schema.SchemaError, Target] = Right(Target(events,100))
```

Do **not** use `mdoc:compile-only` and manually write `// Right(Target("events", 100L))` — always
prefer the live evaluated output from `mdoc`.

### For How-To Guides (Progressive Narrative)

How-to guides have a **progressive narrative** where code builds on itself, so shared-scope
modifiers are used more than in reference pages:

1. **Domain setup** (case classes, imports): `mdoc:silent` — hidden, but in scope for all
   subsequent blocks.
2. **First example** (showing how something works): `mdoc` — show source + output so the reader
   sees the result.
3. **Building on the example** (adding a feature): `mdoc:silent:nest` if redefining, `mdoc` if
   showing output.
4. **New topic within the guide**: `mdoc:silent:reset` to start clean, then `mdoc:silent` for new
   setup.
5. **Final "putting it together"**: `mdoc:compile-only` — fully self-contained, copy-paste ready.

---

## Tabbed Scala 2 / Scala 3 Examples

When a section shows syntax that differs between Scala 2 and Scala 3, use Docusaurus tabs
instead of sequential prose blocks. This lets readers pick their version once and have all
tab groups on the page sync together.

### Required MDX imports

Add these two lines at the top of any `.md` file that uses tabs (right after the closing
`---` of the frontmatter, before any prose):

```mdx
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
```

### Tab structure

````mdx
<Tabs groupId="scala-version" defaultValue="scala2">
  <TabItem value="scala2" label="Scala 2">

```scala mdoc:compile-only
// Scala 2 syntax here
```

  </TabItem>
  <TabItem value="scala3" label="Scala 3">

```scala mdoc:compile-only
// Scala 3 syntax here
```

  </TabItem>
</Tabs>
````

### Rules

- Always use `groupId="scala-version"` — this syncs all tab groups on the page when the
  reader picks a version.
- Always use `defaultValue="scala2"` — Scala 2 is shown first by default.
- Blank lines inside `<TabItem>` are required for mdoc to process fenced code blocks
  correctly.
- `mdoc:compile-only` is the correct modifier for code inside tabs (same as everywhere
  else).
- mdoc passes JSX components through unchanged — only fenced `scala mdoc:*` blocks are
  rewritten.
- Do **not** use tabs for examples that are identical in both versions — only use them
  when the syntax genuinely differs.

---

## Docusaurus Admonitions

Use Docusaurus admonition syntax for callouts:

```
:::note
Additional context or clarification.
:::

:::tip
Helpful shortcut or best practice.
:::

:::warning
Common mistake or gotcha to avoid.
:::

:::info
Background information that is useful but not essential.
:::

:::danger
Serious risk of data loss, incorrect behavior, or security issue.
:::
```

Use admonitions **sparingly** — at most 3–4 in a typical document. They should highlight genuinely
important information, not decorate every section.
