---
name: docs-mdoc-conventions
description: Shared reference for mdoc code block modifiers and Docusaurus admonitions used across ZIO Blocks documentation skills. Include when writing any documentation that contains Scala code blocks.
allowed_tools: Read, Glob, Grep
---

# ZIO Blocks Documentation Conventions

## Compile-Checked Code Blocks with mdoc

This project uses mdoc to compile-check executable code blocks (examples, use cases). 

**Exception:** Data type definitions use plain `` ```scala `` without mdoc modifiers — they are structural illustrations, not executable examples.

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
  the actual evaluated output instead. Can be used with definitions from prior `silent`/`silent:nest`
  blocks, OR used standalone for self-contained examples that just need to show their output.
- **`mdoc:invisible`** is like `silent` but signals "hidden imports only." Rare — prefer including
  imports directly in the `mdoc:silent` setup block (so they are in shared scope) or inside a
  `compile-only` block (for self-contained examples). Use `invisible` only when you need imports
  shared across blocks but must not appear anywhere in the rendered output.
- **No mdoc** (plain `` ```scala ``) — not compiled. Use for pseudocode, ASCII diagrams, type
  signatures for illustration, or sbt/non-Scala syntax.

### Choosing the Right Modifier

Use this decision tree to pick the right modifier:

```
Is this real executable Scala code?
│
├─ NO → Use plain ```scala (pseudocode, ASCII art, type signatures)
│
└─ YES → Do later blocks need these definitions?
   │
   ├─ NO → Do you want to show the output/result?
   │  │
   │  ├─ NO → Use mdoc:compile-only (source only, isolated)
   │  │
   │  └─ YES → Use mdoc (source + output, isolated)
   │
   └─ YES → Is this a later block showing a result?
      │
      ├─ YES → Use mdoc (source + output)
      │
      └─ NO → Are you redefining a name from an earlier block?
         │
         ├─ YES → Use mdoc:silent:nest (shadow existing names)
         │
         └─ NO → Use mdoc:silent (regular setup)
```

**After any mdoc:silent block**, if you later need a completely different context (new domain, new imports), use `mdoc:silent:reset` to clear all state.

### Real-World Examples from ZIO Blocks

The ZIO Blocks documentation uses these patterns throughout. Here are real examples:

#### Pattern 1: Silent Setup + Output Rendering (Query DSL SQL Guide)

```scala mdoc:silent
import zio.blocks.schema._

case class Product(
  name: String,
  price: Double,
  category: String,
  inStock: Boolean,
  rating: Int
)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived
  val price: Lens[Product, Double] = optic(_.price)
}

def columnName(optic: zio.blocks.schema.Optic[?, ?]): String = {
  val nodes = optic.toDynamic.nodes
  nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")
}
```

Now with `columnName` in scope, we can call it and see the result:

```scala mdoc
columnName(Product.price)
columnName(Product.name)
```

This pair shows the two-block pattern: `silent` for setup (which doesn't render), then `mdoc` for expressions where the output is meaningful to show.

#### Pattern 2: Silent Reset for New Context (JSON Differ Reference)

When switching to a different example topic within the same document, reset all prior scope:

```scala mdoc:silent:reset
import zio.blocks.schema.json.{Json, JsonDiffer}

val numberToString = JsonDiffer.diff(Json.Number(42), Json.String("hello"))
```

The `:reset` wipes the previous `Product` type and imports, preventing name collisions.

#### Pattern 3: Self-Contained Compile-Only (DynamicValue Reference)

For structural examples that don't need to share state:

```scala mdoc:compile-only
import zio.blocks.schema.DynamicValue

val str = DynamicValue.string("hello")
val num = DynamicValue.int(42)
val flag = DynamicValue.boolean(true)

val person = DynamicValue.Record(
  "name" -> DynamicValue.string("Alice"),
  "age" -> DynamicValue.int(30)
)
```

Each `compile-only` block stands alone. The next example in the document doesn't have access to `person`.

---

## Quick Reference

Need a one-liner? See **`references/quick-reference.md`** for a cheat sheet of modifiers, patterns, and templates.

## Troubleshooting

Common mistakes when writing mdoc blocks? See **`references/troubleshooting.md`** for solutions to:
- "My code won't compile"
- "Redefining a name failed"
- "The output doesn't show"
- Silent scope gotchas

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

Use Docusaurus admonition syntax for callouts: (Titles are optional)

```
:::note[Title of the note]
Additional context or clarification.
:::

:::tip[Title of the tip]
Helpful shortcut or best practice.
:::

:::warning[Title of the warning]
Common mistake or gotcha to avoid.
:::

:::info[Title of the info]
Background information that is useful but not essential.
:::

:::danger[Title of the danger]
Serious risk of data loss, incorrect behavior, or security issue.
:::
```

Use admonitions **sparingly** — at most 3–4 in a typical document. They should highlight genuinely
important information, not decorate every section.
