---
name: docs-examples
description: Shared procedure for creating and documenting companion examples. Covers directory structure, file templates, example creation, compilation, linting, and embedding with SourceFile. Used by docs-data-type-ref, docs-module-ref, docs-how-to-guide, and docs-tutorial.
allowed-tools: Read, Glob, Grep, Bash(sbt:*), Bash(scalafmt), Bash(git)
---

# Creating and Documenting Companion Examples

Use this procedure when creating companion example code for reference pages, modules, how-to guides, and tutorials.

---

## Creating Example Files

### Step 1: Directory and Package Structure

Create a package directory matching the document's kebab-case id (convert to valid Scala package name):

```
<examples-module>/src/main/scala/<packagename>/
```

Where `<examples-module>` is one of:
- `<module>-examples` (for module references, e.g., `http-model-examples`)
- `zio-blocks-examples` (for guides and tutorials, or integration examples spanning multiple modules)

**Name conversion rule**: Drop hyphens. For example:
- `query-dsl-sql` → `querydsl` (lowercase, hyphens removed)
- `http-model` → `httpmodel`
- `scope-resource-management` → `scoperesourcemanagement`

### Step 2: Example File Structure

Create **one Scala file per major step/concept/use case**, plus a final file for the complete example. Each file should be a standalone runnable `object` extending `App`.

**Naming convention depends on document type:**

| Document Type | File Naming |
|---------------|------------|
| How-to guides | `Step1BasicExample.scala`, `Step2AdvancedExample.scala`, ..., `CompleteExample.scala` |
| Tutorials | `Concept1Example.scala`, `Concept2Example.scala`, ..., `CompleteExample.scala` |
| Data type refs | `BasicUsage.scala`, `AdvancedPatterns.scala`, `CompleteExample.scala` (or descriptive names like `CompleteHttpRequest.scala`) |
| Module refs | `MultiTypeComposition.scala`, `CommonPattern1.scala`, ..., `CompleteExample.scala` (titles emphasizing multi-type usage) |

**Judgment**: Create 3-5 files total. Include a file only for sections/concepts/patterns that introduce a meaningful, self-contained code example.

### Step 3: Example File Template

Each example file follows this pattern:

```scala
package <packagename>

import zio.blocks.schema._
// ... other imports as needed

/**
 * <Documentation Title> — Step/Concept/Pattern: <Title>
 *
 * <1-2 sentence description of what this example demonstrates.>
 *
 * Run with: sbt "<examples-module>/runMain <packagename>.<ObjectName>"
 */
object <ObjectName> extends App {

  // --- Domain Types ---
  // (repeat domain types needed for this step/concept)

  // --- Step/Concept/Pattern Logic ---
  // (the code from this step/concept of the document)

  // --- Output ---
  // (print statements showing the result)
  println(result)
}
```

**Key rules:**

- **Self-contained**: Must compile and run independently. Do not rely on types from other example files. Duplicate domain types across files if needed.
- **Complete imports**: Every file must have all necessary imports at the top.
- **Meaningful output**: Use `println` to show results. Print intermediate results and observations, not just the final answer.
- **Scaladoc comment**: Include documentation title, step/concept name, description, and the `sbt runMain` command.
- **Mirror document code**: Example code should match the document closely, with only additions needed to make it runnable (e.g., wrapping in `object ... extends App`, adding `println`).
- **Descriptive names**: `Step1Expressions` is better than `Step1`. `CompleteHttpRequest` is better than `Complete`.
- **Multi-type examples in modules**: Each example should demonstrate **multi-type composition** — how different types from the module work together to solve a real problem.

### Step 4: The Complete Example

The final example file (`CompleteExample.scala` or descriptively-named equivalent like `CompleteHttpRequest.scala`) must contain the **entire "Putting It Together" or most complex code block** from the document, wrapped in a runnable `object`. This is the most important example file.

### Step 5: Verify Examples Compile

After creating all example files, verify they compile:

```bash
sbt "<examples-module>/compile"
```

Fix any compilation failures before proceeding. The examples must compile successfully.

### Step 6: Lint Check (Mandatory Before Integration)

After all examples compile, stage them in git, then run Scalafmt:

```bash
git add <examples-module>/src/main/scala/**/*.scala
sbt fmtChanged
```

If any files were reformatted, commit them immediately:

```bash
git add -A
git commit -m "docs(<doc-id>): apply scalafmt to examples"
```

Verify the CI lint gate locally:

```bash
sbt check
```

**Success criterion**: Zero formatting violations reported.

---

## Documenting Examples

### When Examples Use Basic Shell Commands

For how-to guides and tutorials where examples are listed simply:

```markdown
## Running the Examples

All code from this guide/tutorial is available as runnable examples in the `<examples-module>` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

```bash
# Step/Concept 1: <brief description>
sbt "<examples-module>/runMain <packagename>.<Step1ObjectName>"

# Step/Concept 2: <brief description>
sbt "<examples-module>/runMain <packagename>.<Step2ObjectName>"

# ...additional steps/concepts...

# Complete example
sbt "<examples-module>/runMain <packagename>.<CompleteObjectName>"
```

**3. Or compile all examples at once:**

```bash
sbt "<examples-module>/compile"
```
```

**Key rules:**
- Use plain `` ```bash `` code blocks (not mdoc—these are shell commands).
- List every companion example file with its command and description.
- Clone URL must be `https://github.com/zio/zio-blocks.git`.
- Keep the section concise and mechanical.

### When Examples Use SourceFile Embedding

For data type references and module references where examples need detailed documentation:

Place the "Running the Examples" section at the end of the documentation, after all type/module documentation. Use this template:

```markdown
## Running the Examples

All code from this guide is available as runnable examples in the `<examples-module>` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### <Example Title>

<Short description of what this App demonstrates and the use case it covers. For module examples, explain which types work together. For type examples, describe the usage pattern.>

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("<examples-module>/src/main/scala/<package>/<ObjectName>.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/<examples-module>/src/main/scala/<package>/<ObjectName>.scala))

```bash
sbt "<examples-module>/runMain <package>.<ObjectName>"
```

### <Next Example Title>

<Short description highlighting key patterns or type composition.>

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("<examples-module>/src/main/scala/<package>/<ObjectName2>.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/<examples-module>/src/main/scala/<package>/<ObjectName2>.scala))

```bash
sbt "<examples-module>/runMain <package>.<ObjectName2>"
```
```

**Rules for this section:**
- List **every `App` object** in the examples module, one entry per object
- For each entry: use a `###` heading (simple, concise title), followed by a short descriptive paragraph
- The paragraph explains what the example demonstrates and the use case/pattern it covers
- For modules: emphasize which types compose in each example
- Embed full source with `SourceFile.print` (keeps docs and examples in sync automatically)
- Include source link and run command
- Keep the two numbered steps (clone, run individually) in that order

### Embedding Example Files with `SourceFile`

**Required for reference pages using SourceFile embedding:** Use `SourceFile.print` to embed full source from `<examples-module>/` for each example.

`SourceFile.print` reads the file at mdoc compile time and emits a fenced code block with the file path shown as the title. This keeps docs and examples in sync automatically.

**Pattern:**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("<examples-module>/src/main/scala/<package>/<ExampleFile>.scala")
```

**Important:** Import as `import docs.SourceFile` and call `SourceFile.print(...)` — do NOT use `import docs.SourceFile._` with bare `print(...)` because `print` conflicts with `Predef.print` inside mdoc sessions.

**Optional parameters:**
- `lines = Seq((from, to))` — include only specific line ranges (1-indexed)
- `showLineNumbers = true` — render with line numbers
- `showTitle = false` — suppress the file path title

---

## Design Rule

This skill consolidates example procedures across all documentation skills (docs-data-type-ref, docs-module-ref, docs-how-to-guide, docs-tutorial) to ensure consistency and reduce redundancy. Parent skills reference this skill for example guidance rather than duplicating instructions.
