---
name: docs-examples
description: Shared procedure for creating companion examples for documentation. Covers directory structure, file templates, example creation, compilation, and linting. Used by docs-how-to-guide and docs-tutorial.
allowed-tools: Read, Glob, Grep, Bash(sbt:*), Bash(scalafmt), Bash(git)
---

# Creating Companion Examples for Documentation

Use this procedure when creating companion example code for how-to guides and tutorials.

## Step 4a: Directory and Package Structure

Create a package directory matching the document's kebab-case id (convert to valid Scala package name):

```
schema-examples/src/main/scala/<packagename>/
```

**Name conversion rule**: Drop hyphens. For example:
- `query-dsl-sql` → `querydsl` (lowercase, hyphens removed)
- `typeclass-derivation` → `typeclassderivation`
- `scope-resource-management` → `scoperesourcemanagement`

## Step 4b: Example File Structure

Create **one Scala file per major step/concept**, plus a final file for the complete example. Each file should be a standalone runnable `object` extending `App`.

**Naming convention:**

For **how-to guides**:

| File | Purpose |
|------|---------|
| `Step1BasicExample.scala` | First step of the guide |
| `Step2AdvancedExample.scala` | Second step |
| `...` | Additional steps as needed |
| `CompleteExample.scala` | The "Putting It Together" example |

For **tutorials**:

| File | Purpose |
|------|---------|
| `Concept1Example.scala` | First concept of the tutorial |
| `Concept2Example.scala` | Second concept |
| `...` | Additional concepts as needed |
| `CompleteExample.scala` | The "Putting It Together" example |

**Judgment**: Create 3-5 files total. Include a file only for sections/concepts that introduce a meaningful, self-contained code example.

## Step 4c: Example File Template

Each example file follows this pattern:

```scala
package <packagename>

import zio.blocks.schema._
// ... other imports as needed

/**
 * <Guide/Tutorial Title> — Step/Concept N: <Title>
 *
 * <1-2 sentence description of what this example demonstrates.>
 *
 * Run with: sbt "schema-examples/runMain <packagename>.<ObjectName>"
 */
object <ObjectName> extends App {

  // --- Domain Types ---
  // (repeat domain types needed for this step/concept)

  // --- Step/Concept Logic ---
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
- **Scaladoc comment**: Include guide/tutorial title, step/concept name, description, and the `sbt runMain` command.
- **Mirror document code**: Example code should match the document closely, with only additions needed to make it runnable (e.g., wrapping in `object ... extends App`, adding `println`).
- **Descriptive names**: `Step1Expressions` is better than `Step1`. `CompleteQueryDSL` is better than `Complete`.

## Step 4d: The Complete Example

The final `CompleteExample.scala` (or a descriptively-named equivalent like `CompleteSqlGenerator.scala`) must contain the **entire "Putting It Together" code block** from the document, wrapped in a runnable `object`. This is the most important example file.

## Step 4e: Verify Examples Compile

After creating all example files, verify they compile:

```bash
sbt "schema-examples/compile"
```

Fix any compilation failures before proceeding. The examples must compile successfully.

## Step 4f: Lint Check (Mandatory Before Integration)

After all examples compile, stage them in git, then run Scalafmt:

```bash
git add schema-examples/src/main/scala/**/*.scala
sbt fmtChanged
```

If any files were reformatted, commit them immediately:

```bash
git add -A
git commit -m "docs(<guide-or-tutorial-id>): apply scalafmt to examples"
```

Verify the CI lint gate locally:

```bash
sbt check
```

**Success criterion**: Zero formatting violations reported.

---

## "Running the Examples" Section Template

Include this section in the document itself (after "Putting It Together") and adapt the example object names:

```markdown
## Running the Examples

All code from this guide/tutorial is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

​```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
​```

**2. Run individual examples with sbt:**

​```bash
# Step/Concept 1: <brief description>
sbt "schema-examples/runMain <packagename>.<Step1ObjectName>"

# Step/Concept 2: <brief description>
sbt "schema-examples/runMain <packagename>.<Step2ObjectName>"

# ...additional steps/concepts...

# Complete example
sbt "schema-examples/runMain <packagename>.<CompleteObjectName>"
​```

**3. Or compile all examples at once:**

​```bash
sbt "schema-examples/compile"
​```
```

**Key rules for this section:**

- Use plain `` ```bash `` code blocks (not mdoc—these are shell commands).
- List every companion example file with its command and description.
- Clone URL must be `https://github.com/zio/zio-blocks.git`.
- Keep the section concise and mechanical.

---

## Design Rule

This sub-skill targets ≤130 lines and covers the shared procedure and template only. Document-specific variations (where to place examples, which types to use) remain in the parent skill.
