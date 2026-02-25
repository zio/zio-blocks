---
name: write-data-type-ref
description: Write a reference documentation page for a specific data type in ZIO Blocks. Use when the user asks to document a data type, write an API reference for a type, or create a reference page for a class/trait/object.
argument-hint: "[fully-qualified-type-name or simple-type-name]"
allowed-tools: Read, Glob, Grep, Bash(sbt:*)
---

# Write Data Type Reference Page

Write a comprehensive reference documentation page for a ZIO Blocks data type.

## Target Type

$ARGUMENTS

## Step 1: Deep Source Code Research

Before writing anything, build a complete mental model of the type:

1. **Find the source file**: Use Glob to find the primary source file for the type. Check all Scala version directories (`*/src/main/scala*/`). The type may have platform-specific or version-specific variants.
2. **Read the full source**: Read the entire source file. Identify:
   - The type signature (class/trait/object, type parameters, variance, extends clauses)
   - All public methods, their signatures, and what they do
   - Companion object methods (factory methods, smart constructors, predefined instances)
   - Nested types and type aliases
   - Implicit instances and extension methods
3. **Find tests**: Search `*/src/test/scala/` for test files referencing the type. Tests reveal:
   - Intended usage patterns and idioms
   - Edge cases and expected behavior
   - Real-world examples
4. **Find existing examples**: Use Glob and Grep to locate examples in `zio-blocks-examples/` or any directory with "examples" in its name.
5. **Find usages**: Grep for the type name across the codebase to find how it's used by other modules — this reveals integration points and relationships.
6. **Read related docs**: Check `docs/` and `docs/reference/` for pages that reference this type.

## Step 2: Write the Documentation

### File Location and Frontmatter

Place the file in `docs/reference/<type-name-kebab-case>.md`:

```
---
id: <kebab-case-id>
title: "<TypeName>"
---
```

The `id` must match the filename (without `.md`).

### Document Structure

Follow this structure precisely. Every section below marked **(required)** must appear. Sections marked **(if applicable)** should only appear when relevant.

#### 1. Opening Definition (required)

Start with a concise, technical definition. Use inline code for the type signature. Explain the type parameters. State the core purpose in 1-3 sentences.

Pattern:

```
`TypeName[A]` is a **key concept in two or three words** that does X. The fundamental operations are `op1` and `op2`.
```

Then list key properties as bullet points if applicable:

```
`TypeName`:
- is purely functional and referentially transparent
- is concurrent-safe and lock-free
- updates and modifies atomically
```

The definition should be concise but informative, with enough detail about type parameters and variance. For example, the `Chunk[A]` is an immutable, indexed sequence of elements of type `A`, optimized for high-performance operations.

After the definition paragraph, include the source definition of the data type in a Scala code block (using plain `` ```scala `` without mdoc, since this is for illustration):

- Show only the structural shape — the trait/class declaration with type parameters, variance annotations, and extends clauses
- Strip method bodies, private members, and extra keywords like `final`; show only the structural shape of the type

#### 2. Motivation / Use Case (if applicable)

This section answers the following questions:
1. What is the purpose of this data type?
2. What problem does it solve?
3. Why was it created, and when should we use it?
4. What are its key advantages over alternatives? Compare with alternatives if it helps clarify.

Tools:

1. Use an ASCII art diagram showing the type structure.
2. For comparing with alternatives, use a comparison table showing key differences and advantages or use bullet points to list advantages.
3. Include a short code example showing the type in action — the "hello world" for this type.

#### 3. Installation (if applicable)

Only include this for top-level module types (e.g., `Chunk`, `Context`, `TypeId`). Skip for internal types that come as part of a larger module.

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-<module>" % "<version>"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-<module>" % "<version>"
```

Note supported Scala versions: 2.13.x and 3.x.

#### 4. Construction / Creating Instances (required)

Document all ways to create values of the type, organized by method:

- Factory methods on the companion object (`apply`, `empty`, `from*`, `of`, `derived`)
- Smart constructors
- Builder patterns
- Conversion from other types
- Predefined instances (if any)

Each method gets its own `###` subsection with a short explanation and a code example.

#### 5. Predefined Instances (if applicable)

If the companion object provides predefined instances (like `TypeId.int`, `TypeId.string`), list them organized by category with a brief table or grouped code block.

#### 6. Core Operations (Required)

Document the primary API organized by category. Group related methods under `###` subsections:

For example:
- **Element Access** (get, apply, head, etc.)
- **Transformations** (map, flatMap, filter, etc.)
- **Combining** (++, combine, merge, etc.)
- **Querying** (exists, forall, find, contains, etc.)
- **Conversion** (toList, toArray, toString, etc.)

For each group:
- List methods with brief descriptions
- Show a code example demonstrating 2-4 methods together
- Note performance characteristics inline when relevant (e.g., "O(1)", "O(n)")

For each method:
a. **Use a `####` heading** with the method name
b. **Explain what it does** in plain language
c. **Show the method signature** in a plain `scala` code block using the simplest trait interface format — just the method name, parameters, and return type, without extra keywords like `override`, `final`, `sealed`. For example:

```scala
trait Chunk[+A] {
  def map[B](f: A => B): Chunk[B]
}
```

If the method is in the companion object, show it as a function in the companion object's simplest form:

```scala
object Chunk {
  def apply[A](as: A*): Chunk[A]
}
```
d. **Show a usage example** in a [compile-checked code blocks with mdoc](#compile-checked-code-blocks-with-mdoc)
e. **Note important caveats** using [Docusaurus admonitions](#docusaurus-admonitions)

#### 7. Subtypes / Variants (if applicable)

If the type has important subtypes, variants, or related types (e.g., `NonEmptyChunk` for `Chunk`, `Nominal`/`Alias`/`Opaque` for `TypeId`), document each in a dedicated section. For each subtype:

- What it is and when to use it
- How to create it
- Key operations that differ from the parent/related type
- How to convert between the parent and subtype

#### 8. Comparison Sections (when applicable)

Compare with analogous concepts from Java, Scala stdlib, or theoretical CS when it adds clarity. Examples:
- "Ref vs AtomicReference in Java"
- "Ref vs State Monad"
- "Promise vs Scala's Promise"
- "Chunk vs List vs Array"
- "TypeId vs Scala's TypeTag vs Java's Class"
- "Lazy vs lazy val vs def"

#### 9. Advanced Usage / Building Blocks (when applicable)

Show how the type composes with other types or how it can be used to build higher-level abstractions.

#### 10. Integration (if applicable)

Show how this type integrates with other ZIO Blocks data types and module. For example:
- How `TypeId` is used in `Schema`
- How `Chunk` is used in `Reflect`
- How `DynamicValue` connects to `Schema` and formats

Add cross-references to related docs (e.g., `[Schema](./schema.md)`, `[Reflect](./reflect.md)`) after explaining the integration of each related type.

### Compile-checked Code Blocks with mdoc

This project uses [mdoc](https://scalameta.org/mdoc/) to compile-check all Scala code blocks in documentation. Every Scala code block must use one of the mdoc modifiers below. **Choosing the right modifier is critical** — incorrect usage causes mdoc compilation failures or broken rendered output.

#### Modifier Summary

| Modifier                   | Rendered Output           | Scope                         | Use When                                                                 |
|----------------------------|---------------------------|-------------------------------|--------------------------------------------------------------------------|
| `scala mdoc:compile-only`  | Source code only          | Isolated (no shared state)    | Self-contained examples where evaluated output is NOT needed             |
| `scala mdoc:silent`        | Nothing (hidden)          | Shared with subsequent blocks | Setting up definitions needed by later blocks                            |
| `scala mdoc:silent:nest`   | Nothing (hidden)          | Shared, wrapped in `object`   | Re-defining names already in scope                                       |
| `scala mdoc`               | Source + evaluated output | Shared with subsequent blocks | When the evaluated result of expressions should be shown to the reader   |
| `scala mdoc:invisible`     | Nothing (hidden)          | Shared with subsequent blocks | Importing hidden prerequisites                                           |
| `scala mdoc:silent:reset`  | Nothing (hidden)          | Resets all prior scope        | Starting a clean scope mid-document                                      |
| `scala` (no mdoc)          | Source code only          | Not compiled                  | Pseudocode, ASCII diagrams, conceptual snippets                          |

#### Key Rules

- **`mdoc:compile-only`** is the **default** for structural or setup-only examples where no output needs to be shown. Each block is compiled in isolation — definitions do NOT carry over between `compile-only` blocks.
- **`mdoc:silent`** defines types/values that **subsequent blocks** can reference (scope persists until reset). Nothing is rendered. You cannot redefine the same name — use `silent:nest` for that.
- **`mdoc:silent:nest`** is like `silent` but wraps code in an anonymous `object`, allowing you to **shadow names** from earlier blocks (e.g., redefining `Person` with different fields in a later section).
- **`mdoc:silent:reset`** wipes **all** accumulated scope and starts fresh. Use when `silent:nest` wouldn't suffice (e.g., switching to a completely different topic mid-document).
- **`mdoc`** (no qualifier) shows **source + evaluated output** (REPL-style). Use this whenever you would otherwise write `// Right(42L)`, `// Some("hello")`, or any result comment — let mdoc render the actual evaluated output instead. Requires definitions to be in scope from a prior `silent`/`silent:nest` block.
- **`mdoc:invisible`** is like `silent` but signals "hidden imports only." Rare — prefer including imports in the `compile-only` block itself.
- **No mdoc** (plain `` ```scala ``) — not compiled. Use for pseudocode, ASCII diagrams, type signatures for illustration, or sbt/non-Scala syntax.

#### Choosing the Right Modifier

1. Self-contained example where output doesn't need to be shown? → `mdoc:compile-only`
2. Later blocks need these definitions? → `mdoc:silent` (first time) or `mdoc:silent:nest` (redefining)
3. Need a completely clean scope? → `mdoc:silent:reset`
4. **Showing the result of expressions** (return values, decoded output, computed values)? → `mdoc:silent` for setup + `mdoc` to render evaluated output. **Never manually write `// result` comments when mdoc can show the real output.**
5. Not real Scala? → plain `` ```scala `` or `` ```text ``

#### Pattern: Setup + Evaluated Output

When a code snippet evaluates expressions whose results are meaningful to the reader, split it into two blocks:

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

Do **not** use `mdoc:compile-only` and manually write `// Right(Target("events", 100L))` — always prefer the live evaluated output from `mdoc`.

### Writing Rules

- **Be exhaustive on the public API**: Every public method on the type and its companion should be documented. Group them logically, but don't skip methods.
- **One concept per code block**: Each code block demonstrates one cohesive idea.
- **Always include imports**: Every code block must start with the necessary import statements.
- **Show evaluated output with mdoc, not comments**: When expressions have results that are meaningful to the reader (return values, decoded output, computed values), use `mdoc:silent` + `mdoc` so mdoc renders the real output. Do **not** write `// Right(42L)` or `// Some("hello")` manually — these go stale and can be wrong.
- **Prefer `val` over `var`**: Use immutable patterns everywhere.
- **Use ASCII art** for type hierarchies, data structures, and flows.
- **Link to related docs**: Use relative paths `[TypeName](./type-name.md)`.
- **Use "ZIO Blocks"** (not "zio-blocks") for the project name.
- **Don't pad**: Keep prose concise. Let the code examples do the talking. Short explanatory sentence, then code block.
- **No bare subheaders**: Never place a `###` or `####` subheader immediately after a `##` header with nothing in between. Always write at least one sentence of explanation before the first subheader — introduce the group, state the purpose, or give context. The same rule applies at every heading level: a heading must be followed by prose before any child heading.
- **No lone subheaders**: Never create a subsection with only one child. If a `##` section would have only one `###`, or a `###` would have only one `####`, remove the subheader entirely and place the content directly under the parent heading. A subheader is only justified when there are two or more siblings.
- **Always bridge consecutive code blocks with prose**: Two code blocks must never be separated by a blank line alone. Between every pair of consecutive code blocks, write a short descriptive sentence that either summarises what the previous block set up or introduces what the next block demonstrates. This sentence must be concise (one line) and genuinely informative — not filler.
- **Person**: Use "we" when walking through examples or any time you want to guide the reader through a process or example. ("we can create...", "we need to...").
- **Tense**: Use present tense ("returns", "creates", "modifies").
- **Code snippet description**: When showing example code snippets, explain what they do and why they are relevant. Don't just show code without context.
- **Instance methods and companion object members**: When referencing an instance method, use the `#` notation (e.g., `Ref#update`). For companion object members, use the `.` notation (e.g., `Ref.make`).

### Docusaurus Admonitions

Use Docusaurus admonition syntax for adding any of `note`, `tip`, `info`, `warning`, and `danger` to highlight important information. Example for a `note`:

```
:::note
Additional context or clarification.
:::
```

## Step 3: Write Examples

Create focused `App` objects in `zio-blocks-examples/src/main/scala/<type-name-lowercase>/`. Each `App` demonstrates **one use case**. Avoid bundling unrelated scenarios into a single `App`.

### File granularity

- **One `App` per concept** — schema evolution, collection reshaping, error accumulation, etc. are separate `App` objects.
- **Small, related `App`s can share a file** — if several `App`s are short and tightly related (e.g., numeric widening variants), place them together in one file so the reader can run them in sequence.
- **Large `App`s get their own file** — if an `App` needs many types or substantial setup, give it a dedicated file.

### Conventions

- **Package**: matches the directory name (e.g., `package into` for `into/`)
- **Object**: `extends App` so each unit is independently runnable
- **Output**: use `util.ShowExpr.show(expr)` to print both the expression and its result — e.g., `show(Into[Int, Long].into(100))` prints `Into[Int, Long].into(100)  =>  Right(100)`. Never print just the result alone; the reader should see what was evaluated without looking at the source. The `show` helper lives in `zio-blocks-examples/src/main/scala/util/ShowExpr.scala` and is powered by `sourcecode.Text` to capture the source text at compile time.
- **Naming**: name files after the scenario(s) they contain (e.g., `IntoSchemaEvolutionExample.scala`, `IntoCollectionsExample.scala`) not just the type name

### What to Cover

Each `App` should:

- Focus on **one coherent use case**
- Use **realistic domain types** (`Person`, `Order`, `Address`) rather than abstract `Source`/`Target`
- Cover the **happy path and at least one failure/edge case**
- Be **self-contained** — all types and imports are defined within the file

### Example: multiple small Apps in one file

```scala
package mytype

import zio.blocks.schema.Into
import util.ShowExpr.show

// Small related examples share a file — reader runs them one after another

object IntoWideningExample extends App {
  show(Into[Int, Long].into(100))
  show(Into[Float, Double].into(3.14f))
}

object IntoNarrowingExample extends App {
  show(Into[Long, Int].into(42L))
  show(Into[Long, Int].into(Long.MaxValue))
}
```

### Example: large App in its own file

```scala
// IntoSchemaEvolutionExample.scala
package mytype

import zio.blocks.schema.Into
import util.ShowExpr.show

object IntoSchemaEvolutionExample extends App {

  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long, email: Option[String])

  val migrate = Into.derived[PersonV1, PersonV2]

  show(migrate.into(PersonV1("Alice", 30)))
  show(migrate.into(PersonV1("Bob",   25)))
}
```

## Step 4: Integrate

After writing the reference page and examples:

1. If updating an existing file, edit it in place.
2. If creating a new file, place it in the appropriate `docs/reference/` subdirectory based on where it logically belongs and update `sidebars.js` to add it to the sidebar.
3. **Update `docs/index.md`**: Add the new page under the appropriate section in the "Documentation" heading.
4. **Cross-reference**: Add links from related existing docs to the new page.
5. **Verify all links**: Ensure relative links in the new page and in updated pages are correct.

## Step 5: Review

After writing, re-read the document and verify:
- All method signatures match the actual source code
- All code examples would compile with `mdoc`
- The frontmatter `id` matches what `sidebars.js` expects (if an entry exists)
- The document is self-contained—a reader shouldn't need to look at the source code to understand the type's API
- The example file compiles and runs without errors
