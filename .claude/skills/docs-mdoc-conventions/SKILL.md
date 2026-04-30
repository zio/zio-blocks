---
name: docs-mdoc-conventions
description: Shared reference for mdoc code block modifiers and Docusaurus admonitions used across ZIO Blocks documentation skills. Include when writing any documentation that contains Scala code blocks.
allowed_tools: Read, Glob, Grep
---

All Scala code blocks must have proper mdoc modifiers (e.g., `mdoc:compile-only`, `mdoc:silent`, etc.). Look at the "Choosing the Right Modifier" section below to pick the correct one based on your use case.

**Exception:** Data type definitions use plain `` ```scala `` without mdoc modifiers — they are structural illustrations, not executable examples.

## Modifiers & Rules

Each modifier has a specific role. Choose based on whether you need scope sharing and whether output should render:

- **`mdoc:compile-only`** — Renders source code only, isolated scope (no definitions carry over).
  This is the **default** for self-contained examples where you want to show the structure but not
  the evaluated output. Each block compiles alone; subsequent blocks cannot reference definitions
  from a `compile-only` block.

- **`mdoc:silent`** — Renders nothing (hidden), scope shared with subsequent blocks.
  Use to define types, values, or imports that later blocks will reference. Scope persists until
  you use `silent:reset`. You **cannot** redefine the same name in a later block — use
  `mdoc:silent:nest` for that.

- **`mdoc:silent:nest`** — Renders nothing, scope shared, code wrapped in anonymous `object`.
  Like `silent`, but allows you to **shadow/redefine names** from earlier blocks (e.g., redefining
  `Person` with different fields in a later section). Use when `silent` would fail due to name collision.

- **`mdoc:silent:reset`** — Renders nothing, clears all prior scope.
  Wipes the entire accumulated scope and starts fresh. Use when switching to a completely different
  context (new domain, new imports) mid-document and `silent:nest` wouldn't suffice.

- **`mdoc`** (no qualifier) — Renders source + evaluated output, scope shared with subsequent blocks.
  Shows both the code and its REPL-style result (as if you'd written `// Right(42L)` by hand, but
  evaluated). Can build on definitions from prior `silent`/`silent:nest` blocks, or run standalone
  for self-contained examples that just need to show their output.

- **`mdoc:invisible`** — Invisible code block, scope shared with subsequent blocks.
  Signals "hidden imports only" — rare in practice. Prefer including imports directly in a
  `mdoc:silent` setup block (so they're visible in scope) or inside a `compile-only` block
  (for self-contained examples). Use `invisible` only when you need imports shared across blocks
  but must **not** appear anywhere in the rendered output.

- **No mdoc** (plain `` ```scala ``) — Renders source code only, not compiled.
  Use for pseudocode, ASCII diagrams, type signatures for illustration, or non-Scala syntax (e.g., sbt configuration).

- -**Never hardcode expression output in comments**: Let mdoc render output automatically, don't add comments like `// None` or `// "hello"`. Use bare `mdoc` to show all vals; only use `mdoc:silent` when output is verbose boilerplate.

**Bad vs. Good:**
- ❌ `val x = 42 // 42`  
  ✅ `val x = 42` (mdoc renders the output)

## Choosing the Right Modifier

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

## Common Patterns

### Pattern 1: Silent Setup + Output Rendering (Query DSL SQL Guide)

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

### Pattern 2: Redefining with Nesting

When you need to redefine a name (e.g., `Person`), use `nest` modifier:

```
Block A: mdoc
  ├─ case class Person(...)    ← in scope
  └─ val alice = ...           ← in scope

Block B: mdoc
  ├─ Can reference alice  ✓
  └─ Can reference Person ✓

Block C: mdoc:nest
  ├─ All prior scope accessible ✓
  └─ Can redefine Person ✓

Block D: mdoc
  ├─ Can reference new Person  ✓
  └─ Cannot reference old Person ✗
```

### Pattern 3: Self-Contained

```scala mdoc:compile-only
case class User(name: String, age: Int)
val user = User("Alice", 30)
```

Each `compile-only` block stands alone. The next example in the document doesn't have access to `person`.

### Pattern 4: Setup + Show Output

Only use this pattern when the first block defines multiple larger setup code (e.g., multiple case classes, imports) that later blocks will reference and the later block should be evaluated to show output.

```scala mdoc:silent
def add(a: Int, b: Int): Int = a + b
```

Now call it and show the result:

```scala mdoc
add(2, 3)
```

If the setup is just a single line or two, it's often cleaner to combine it with the output block:

```scala mdoc
def add(a: Int, b: Int): Int = a + b

add(2, 3)
```

### Pattern 5: Multi-Step Guide
1. **Setup block** → `mdoc:silent` (case classes, imports)
2. **Example 1** → `mdoc` (show output)
3. **Building on Example 1** → `mdoc` (reuse prior definitions)
4. **New Topic** → `mdoc:silent:reset` + `mdoc:silent` (fresh context)
5. **Final Copy-Paste** → `mdoc:compile-only` (standalone)

## When to Use `:reset`

- Switching to a **completely different domain** (Product → JSON → User)
- Starting a **new tutorial section** with independent examples
- Avoid if: just defining a new helper function (doesn't need reset)

## Tips

- **Never manually write `// result` comments** — use `mdoc` to show real output
- **Test locally with `sbt docs`** before committing mdoc blocks
- **Group related setup blocks** — define all prerequisites in one `silent` block if possible
- **Use `:reset` sparingly** — prefer `:nest` for minor redefinitions

## Troubleshooting

Common mistakes when writing mdoc blocks? See **`references/troubleshooting.md`** for solutions to.

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
