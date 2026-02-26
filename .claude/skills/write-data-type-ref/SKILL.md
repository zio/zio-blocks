---
name: write-data-type-ref
description: Write a reference documentation page for a specific data type in ZIO Blocks. Use when the user asks to document a data type, write an API reference for a type, or create a reference page for a class/trait/object.
argument-hint: "[fully-qualified-type-name or simple-type-name]"
allowed-tools: Read, Glob, Grep, Bash(sbt:*), Bash(sbt gh-query*)
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
4. **Find existing examples**: Use Glob and Grep to locate examples in `schema-examples/` or any directory with "examples" in its name.
5. **Find usages**: Grep for the type name across the codebase to find how it's used by other modules — this reveals integration points and relationships.
6. **Read related docs**: Check `docs/` and `docs/reference/` for pages that reference this type.
7. **Search GitHub history**: Run `sbt "gh-query --verbose <TypeName>"` to search GitHub issues, PRs, and comments for discussions about the type. Use the results to:
   - Understand design decisions and rationale behind the API
   - Find known caveats, gotchas, or non-obvious behavior surfaced in issues
   - Discover common user questions or pain points to address in the docs
   - Identify changelog entries or breaking changes worth noting
   - Surface examples or idioms shared by contributors in PRs

   Run multiple queries as needed (e.g., the simple type name, the fully-qualified name, related feature keywords) to get thorough coverage.

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

### Compile-Checked Code Blocks with mdoc

See the **`docs-mdoc-conventions`** skill for the complete mdoc modifier table, key rules, and
the Setup + Evaluated Output pattern. Apply those rules here — reference pages use primarily
`mdoc:compile-only` for self-contained examples and `mdoc:silent` + `mdoc` for showing evaluated
output.

### Writing Rules

See the **`docs-writing-style`** skill for universal prose style, referencing conventions, heading
layout rules, and code block rules.

Additional rules specific to reference pages:

- **Be exhaustive on the public API**: Every public method on the type and its companion should be
  documented. Group them logically, but don't skip methods.
- **Use ASCII art** for type hierarchies, data structures, and flows.
- **Link to related docs**: Use relative paths `[TypeName](./type-name.md)`.

### Docusaurus Admonitions

See the **`docs-mdoc-conventions`** skill for admonition syntax and usage guidelines.

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

See the **`docs-integrate`** skill for the complete integration checklist (sidebars.js, index.md,
cross-references, link verification).

Additional note for reference pages: if creating a new file, place it in the appropriate
`docs/reference/` subdirectory based on where it logically belongs.

## Step 5: Review

After writing, re-read the document and verify:
- All method signatures match the actual source code
- All code examples would compile with `mdoc`
- The frontmatter `id` matches what `sidebars.js` expects (if an entry exists)
- The document is self-contained—a reader shouldn't need to look at the source code to understand the type's API
- The example file compiles and runs without errors
