---
name: docs-data-type-ref
description: Write a reference documentation page for a specific data type in ZIO Blocks. Use when the user asks to document a data type, write an API reference for a type, or create a reference page for a class/trait/object.
argument-hint: "[fully-qualified-type-name or simple-type-name]"
allowed-tools: Read, Glob, Grep, Bash(sbt:*), Bash(sbt gh-query*)
---

# Write Data Type Reference Page

**REQUIRED BACKGROUND:** Import the following skill files before starting:
- `.claude/skills/docs-writing-style/SKILL.md` for prose conventions
- `.claude/skills/docs-mdoc-conventions/SKILL.md` for code block syntax throughout the document

## Target Type

$ARGUMENTS

## Step 1: Deep Source Code Research

Use the **`docs-research`** skill to find the source file, read tests, identify examples, find usages, read related docs, and search GitHub history. It covers steps for identifying the type, finding supporting information, and building a complete mental model.

**Additional guidance for reference pages**: Ensure you also locate the type's full public API (all public methods and companion object methods), as this will form the core of your documentation.

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

**NO HEADING FOR THIS SECTION.** Start with a concise, technical definition immediately after the frontmatter—do NOT add any heading (## or otherwise). This content forms the natural opening of the document.

Use inline code for the type signature. Explain the type parameters. State the core purpose in 1-3 sentences.

Pattern:

```
`TypeName[A]` is a **key concept in two or three words** that does X. The fundamental operations are `op1` and `op2`.
```

Then list key properties as bullet points if applicable:

```
`TypeName`:
- Lock-Free — safely shared across fibers with no synchronization overhead
- Atomic — no observer can witness a partially updated state
```

The definition should be concise but informative, with enough detail about type parameters and variance. For example, the `Chunk[A]` is an immutable, indexed sequence of elements of type `A`, optimized for high-performance operations.

After the definition paragraph, include the source definition of the data type in a Scala code block (using plain `` ```scala `` without mdoc, since this is for illustration):

- Show only the structural shape — the trait/class declaration with type parameters, variance annotations, and extends clauses
- Strip method bodies, private members, and extra keywords like `final`; show only the structural shape of the type

After the structural definition, follow immediately with a section header (e.g., `## Quick Showcase`) for the next section.

#### 2. Quick Showcase (required)

Show core capabilities through examples. For simple types (e.g., `Writer`, `Reader`), one example suffices. For rich types (e.g., `Chunk`), combine 2–3 scenarios in a single `mdoc:reset` block (10–20 lines). Goal: readers grasp the core idea without reading further.

#### 3. Motivation / Use Case (if applicable)

Please write what the problem is and why this type is the solution in storytelling style by describing a realistic scenario.

#### 4. Installation (if applicable)

Only include this for top-level module types (e.g., `Chunk`, `Context`, `TypeId`). Skip for internal types that come as part of a larger module.

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-<module>" % "@VERSION@"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-<module>" % "@VERSION@"
```

Note supported Scala versions: 2.13.x and 3.x.

#### 5. Construction / Creating Instances (required)

Document all ways to create values of the type, organized by method:

- Factory methods on the companion object (`apply`, `empty`, `from*`, `of`, `derived`)
- Smart constructors
- Builder patterns
- Conversion from other types
- Predefined instances (if any)

Each method gets its own Markdown subsection with a short explanation and a code example.

#### 6. Predefined Instances (if applicable)

List predefined instances (like `TypeId.int`, `TypeId.string`) organized by category in a table or code block.

#### 7. Core Operations (Required)

Document the primary API organized by category. Group related methods under markdown subsections:

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
a. **Use a Markdown subheader** with the method name using the pattern: `` `MethodName` — Brief Description ``
   - Example: `` `Resource.apply` — Wrap a Value ``
   - Example: `` `Resource#map` — Transform the Value ``
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
d. **Show a usage example** using the Setup + Evaluated Output pattern:
   - Combine setup and output in a **single code block** using `mdoc:silent:reset` (or just `mdoc:reset` if resetting state)
   - Setup code goes first (define types/values needed), followed by the method call and output
   - This demonstrates both how to call the method AND what it returns

   Example pattern:
   ```
   ​```scala mdoc:reset
   case class Person(name: String)
   val p = Person("Alice")

   p.name  // Shows: val res0: String = Alice
   ```

   **Style rule:** Between any two code blocks, include an **explanatory paragraph** that introduces or describes what the following code demonstrates. Do NOT leave empty lines between code blocks.

   ✅ Correct:
   ```
   ​```scala mdoc
   val x = 1
   ```
   Now let's use x to compute a result:

   ​```scala mdoc
   val y = x + 1
   ```
   ```

   ❌ Wrong:
   ```
   ​```scala mdoc
   val x = 1
   ```

   ​```scala mdoc
   val y = x + 1
   ```
   ```

e. **Note important caveats** using [Docusaurus admonitions](#docusaurus-admonitions)

#### 8. Subtypes / Variants (if applicable)

Document important subtypes (e.g., `NonEmptyChunk` for `Chunk`) with: when to use, how to create, operations that differ, and conversion examples.

#### 9. Comparison Sections (when applicable)

Compare with analogous concepts from Java, Scala stdlib, or theoretical CS when it adds clarity. Examples:
- "Ref vs AtomicReference in Java"
- "Ref vs State Monad"
- "Promise vs Scala's Promise"
- "Chunk vs List vs Array"
- "TypeId vs Scala's TypeTag vs Java's Class"
- "Lazy vs lazy val vs def"

Use padded table columns for readability (see **`docs-writing-style`** for table formatting rules).

#### 10. Advanced Usage / Building Blocks (when applicable)

Show how the type composes with other types or how it can be used to build higher-level abstractions.

#### 11. Integration (if applicable)

Show how this type integrates with other ZIO Blocks data types and module. For example:
- How `TypeId` is used in `Schema`
- How `Chunk` is used in `Reflect`
- How `DynamicValue` connects to `Schema` and formats

Add cross-references to related docs (e.g., `[Schema](./schema.md)`, `[Reflect](./reflect.md)`) after explaining the integration of each related type.

#### 12. Running the Examples (required when examples exist)

Add this section at the very end of the page, after Integration. For each `App` example, embed the full source using `SourceFile.print`, then show the command to run it. Use this template verbatim:

```markdown
## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### <Example Title>

<Short description of what this App demonstrates and the use case it covers.>

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/<package>/<ObjectName>.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/<package>/<ObjectName>.scala))

```bash
sbt "schema-examples/runMain <package>.<ObjectName>"
```

### <Next Example Title>

<Short description of what this App demonstrates and the use case it covers.>

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("<module_name>-examples/src/main/scala/<package>/<ObjectName2>.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/<package>/<ObjectName2>.scala))

```bash
sbt "<module_name>-examples/runMain <package>.<ObjectName2>"
```

Rules for this section:
- List **every `App` object** written in Step 4, one entry per object.
- For each entry: use a `###` heading (simple title), followed by a short descriptive paragraph, then embed the full source with `SourceFile.print`, source link, and run command.
- The heading should be a simple, concise title (e.g., "Basic Usage", "Error Handling"). The paragraph below explains what the example demonstrates and the use case it covers.
- Keep the two numbered steps (clone, run individually) in that order; do not add or remove steps.
- If no example `App` objects were written (rare), omit this section entirely.
- **Always embed full source** — `SourceFile.print` keeps docs and examples in sync automatically.

### Embedding Example Files with `SourceFile`

**Required for "Running the Examples" section:** Use `SourceFile.print` to embed full source from `<module_name>-examples/` for each example.

`SourceFile.print` reads the file at mdoc compile time and emits a fenced code block with the file path shown as the title. This keeps docs and examples in sync automatically.

**Pattern:**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("<module_name>-examples/src/main/scala/<package>/<ExampleFile>.scala")
```

**Important:** Import as `import docs.SourceFile` and call `SourceFile.print(...)` — do NOT use `import docs.SourceFile._` with bare `print(...)` because `print` conflicts with `Predef.print` inside mdoc sessions.

**Optional parameters:**
- `lines = Seq((from, to))` — include only specific line ranges (1-indexed)
- `showLineNumbers = true` — render with line numbers
- `showTitle = false` — suppress the file path title

### Writing Rules

- Document every public method on the type and its companion object.
- Use ASCII art for type hierarchies and data structures.
- Link to related docs using relative paths: `[TypeName](./type-name.md)`.

## Step 3: Verify Documentation Compliance

Run `/docs-verify-compliance` skill.

## Step 3.5: Verify Method Coverage

Use the **`docs-data-type-list-members`** skill to extract public methods, then check coverage against documentation:

```bash
# Run the skill to extract members and pipe to coverage checker
/docs-data-type-list-members <TypeName> | \
  ./.claude/skills/docs-data-type-ref/check-method-coverage.sh <TypeName> docs/reference/<type-name>.md
```

Or save extracted members to file, then check:
```bash
# Save members to file (run skill, save output)
./.claude/skills/docs-data-type-ref/check-method-coverage.sh <TypeName> docs/reference/<type-name>.md members.txt
```

The coverage checker categorizes members by:
- **Companion Object Members** — static factories and utilities
- **Public API** — instance methods
- **Inherited Methods** — methods from parent types (when available)

Then compares against backtick-quoted method names in documentation.

Exit codes: 0=complete coverage, 1=gaps found, 2=error

## Step 4: Write Examples

Create focused `App` objects in `<module_name>-examples/src/main/scala/<type-name-lowercase>/`. Each demonstrates one use case — one `App` per concept.

- **Package**: matches directory name (e.g., `package into` for `into/`)
- **Object**: extends `App` for independent execution
- **Output**: use `util.ShowExpr.show(expr)` to print expression and result
- **Naming**: name files after the scenario, not just the type (e.g., `IntoSchemaEvolutionExample.scala`)
- **Coverage**: happy path + at least one failure/edge case, realistic domain types (`Person`, `Order`)
- **Self-contained**: define all types and imports in the file

## Step 5: Format and Verify

Format all Scala files:

```bash
sbt scalafmtAll
```

Verify lint checks pass:

```bash
sbt check
```

Verify mdoc compilation:

```bash
# Single file:
sbt "docs/mdoc --in docs/reference/<type-name-kebab-case>.md"

# Multiple files — repeat --in/--out pairs:
sbt "docs/mdoc --in docs/reference/file1.md --out out/file1.md --in docs/reference/file2.md --out out/file2.md"

# Or use a directory to cover all files in it:
sbt "docs/mdoc --in docs/reference/<subdirectory>/"
```

> **Never use bare `sbt docs/mdoc`** without `--in` — it recompiles all documentation (~90 seconds).

**Success criterion:** zero `[error]` lines in mdoc output.

## Step 6: Integrate

Use the **`docs-integrate`** skill for integration checklist (sidebars.js, index.md, cross-references).
