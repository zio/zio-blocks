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

## Step 2: Write the Reference Page

### File Location and Frontmatter

Place the file in `docs/reference/<type-name-kebab-case>.md`:

```
---
id: <kebab-case-id>
title: "<TypeName>"
---
```

The `id` must match the filename (without `.md`).

### Required Structure

Follow this structure precisely. Every section below marked **(required)** must appear. Sections marked **(if applicable)** should only appear when relevant.

#### 1. Opening Definition (required)

A single paragraph starting with the type name in backticks, followed by a concise definition. This is the most important sentence — it tells the reader what the type IS and what it's FOR.

Pattern: `` `TypeName[A]` is a <what-it-is> that <what-it-does>. ``

Examples from existing docs:
- `` `Chunk[A]` is an immutable, indexed sequence of elements of type `A`, optimized for high-performance operations. ``
- `` `Context[+R]` is a type-indexed heterogeneous collection. ``
- `` `TypeId[A]` represents the identity of a type or type constructor at runtime. ``
- `` `DynamicValue` is a schema-less, dynamically-typed representation of any structured value in ZIO Blocks. ``

The definition should be concise but informative, with enough detail to type parameters and variance. For example, the `Chunk[A]` is an immutable, indexed sequence of elements of type `A`, optimized for high-performance operations.

After the definition paragraph, include the source definition of the data type in a Scala code block (using plain `` ```scala `` without mdoc, since this is for illustration). This should be the actual type signature from the source code — the class/trait/object declaration with its type parameters, variance annotations, and extends clauses. Strip method bodies and private members; show only the structural shape of the type.

#### 2. Motivation / Why? (required)

This section answers following questions:
1. What is the purpose of this data type?
2. What problem does it solve?
3. Why was it created?
4. What are its key advantages over alternatives?

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

Use `` ```scala mdoc:compile-only `` for all code blocks. Always include imports.

#### 5. Core Operations (required)

Document the primary API organized by category. Group related methods under `###` subsections:

- **Element access** (get, apply, head, etc.)
- **Transformations** (map, flatMap, filter, etc.)
- **Combining** (++, combine, merge, etc.)
- **Querying** (exists, forall, find, contains, etc.)
- **Conversion** (toList, toArray, toString, etc.)

For each group:
- List methods with brief descriptions
- Show a code example demonstrating 2-4 methods together
- Note performance characteristics inline when relevant (e.g., "O(1)", "O(n)")

#### 6. Subtypes / Variants (if applicable)

If the type has important subtypes, variants, or related types (e.g., `NonEmptyChunk` for `Chunk`, `Nominal`/`Alias`/`Opaque` for `TypeId`), document each with:

- What it is and when to use it
- How to create it
- Key operations that differ from the parent type
- How to convert between the parent and subtype

#### 7. Integration (if applicable)

Show how this type integrates with other ZIO Blocks data types and module. For example:
- How `TypeId` is used in `Schema`
- How `Chunk` is used in `Reflect`
- How `DynamicValue` connects to `Schema` and formats

Add cross-references to related docs (e.g., `[Schema](./schema.md)`, `[Reflect](./reflect.md)`) after explaining the integration of each related type.

#### 8. Predefined Instances (if applicable)

If the companion object provides predefined instances (like `TypeId.int`, `TypeId.string`), list them organized by category with a brief table or grouped code block.

### mdoc Code Block Reference

This project uses [mdoc](https://scalameta.org/mdoc/) to compile-check all Scala code blocks in documentation. Every Scala code block must use one of the mdoc modifiers below. **Choosing the right modifier is critical** — incorrect usage causes mdoc compilation failures or broken rendered output.

#### Modifier Summary

| Modifier                   | Rendered Output           | Scope                         | Use When                                        |
|----------------------------|---------------------------|-------------------------------|-------------------------------------------------|
| `scala mdoc:compile-only ` | Source code only          | Isolated (no shared state)    | Default choice for most examples                |
| `scala mdoc:silent`        | Nothing (hidden)          | Shared with subsequent blocks | Setting up definitions needed by later blocks   |
| `scala mdoc:silent:nest`   | Nothing (hidden)          | Shared, wrapped in `object`   | Re-defining names already in scope              |
| `scala mdoc`               | Source + evaluated output | Shared with subsequent blocks | Showing REPL-style output to the reader         |
| `scala mdoc:invisible`     | Nothing (hidden)          | Shared with subsequent blocks | Importing hidden prerequisites                  |
| `scala mdoc:silent:reset`  | Nothing (hidden)          | Resets all prior scope        | Starting a clean scope mid-document             |
| `scala` (no mdoc)          | Source code only          | Not compiled                  | Pseudocode, ASCII diagrams, conceptual snippets |

#### Key Rules

- **`mdoc:compile-only`** is the **default**. Use it for self-contained examples. Each block is compiled in isolation — definitions do NOT carry over between `compile-only` blocks.
- **`mdoc:silent`** defines types/values that **subsequent blocks** can reference (scope persists until reset). Nothing is rendered. You cannot redefine the same name — use `silent:nest` for that.
- **`mdoc:silent:nest`** is like `silent` but wraps code in an anonymous `object`, allowing you to **shadow names** from earlier blocks (e.g., redefining `Person` with different fields in a later section).
- **`mdoc:silent:reset`** wipes **all** accumulated scope and starts fresh. Use when `silent:nest` wouldn't suffice (e.g., switching to a completely different topic mid-document).
- **`mdoc`** (no qualifier) shows **source + evaluated output** (REPL-style). Requires definitions in scope from a prior `silent`/`silent:nest` block. Use to show `toJson`, `show`, encoding results, etc.
- **`mdoc:invisible`** is like `silent` but signals "hidden imports only." Rare — prefer including imports in the `compile-only` block itself.
- **No mdoc** (plain `` ```scala ``) — not compiled. Use for pseudocode, ASCII diagrams, type signatures for illustration, or sbt/non-Scala syntax.

#### Choosing the Right Modifier

1. Self-contained example? → `mdoc:compile-only`
2. Later blocks need these definitions? → `mdoc:silent` (first time) or `mdoc:silent:nest` (redefining)
3. Need a completely clean scope? → `mdoc:silent:reset`
4. Want to show evaluated output? → `mdoc` (after a `silent` setup)
5. Not real Scala? → plain `` ```scala `` or `` ```text ``

### Writing Rules

- **Be exhaustive on the public API**: Every public method on the type and its companion should be documented. Group them logically, but don't skip methods.
- **One concept per code block**: Each `` ```scala mdoc:compile-only `` block demonstrates one cohesive idea.
- **Always include imports**: Every code block must start with the necessary import statements.
- **Show return types in comments**: Use `// Type` comments to clarify non-obvious return types.
- **Prefer `val` over `var`**: Use immutable patterns everywhere.
- **Use ASCII art** for type hierarchies, data structures, and flows.
- **Link to related docs**: Use relative paths `[TypeName](./type-name.md)`.
- **Use "ZIO Blocks"** (not "zio-blocks") for the project name.
- **Don't pad**: Keep prose concise. Let the code examples do the talking. Short explanatory sentence, then code block.
- **Person**: Use "we" when walking through examples or any time you want to guide the reader through a process or example. ("we can create...", "we need to...").

## Step 3: Integrate

After writing the reference page:

1. **Update `docs/index.md`**: Add the new page under the appropriate section in the "Documentation" heading.
2. **Cross-reference**: Add links from related existing docs to the new page.
3. **Verify all links**: Ensure relative links in the new page and in updated pages are correct.
