---
name: docs-examples
description: Shared procedure for creating and documenting companion examples. Covers directory structure, file templates, example creation, compilation, linting, and embedding with SourceFile. Used by docs-data-type-ref, docs-module-ref, docs-how-to-guide, and docs-tutorial.
allowed-tools: Read, Glob, Grep, Bash(sbt:*), Bash(scalafmt), Bash(git)
---

## Creating Example Files

### Step 1: Directory and Package Structure

Create a package directory matching the following pattern:

```
<examples-module>/src/main/scala/<packagename>/
```

Where `<examples-module>` is one of:
- `<module>-examples` (for module references, e.g., `http-model-examples`)
- `zio-blocks-examples` (for guides and tutorials, or integration examples spanning multiple modules)

**Name conversion rule**: Drop hyphens. e.g.:
- `query-dsl-sql` → `querydsl` (lowercase, hyphens removed)
- `http-model` → `httpmodel`
- `scope-resource-management` → `scoperesourcemanagement`

### Step 2: Example File Structure

Create **one Scala file per major step/concept/use case**, plus a final file for the complete example. Each file should be a standalone runnable program, either an `object` extending `App` or a Scala 3 `@main def` function.

**Naming convention depends on document type:**

| Document Type | File Naming |
|---------------|------------|
| How-to guides | `Step1BasicExample.scala`, `Step2AdvancedExample.scala`, ..., `CompleteExample.scala` |
| Tutorials | `Concept1Example.scala`, `Concept2Example.scala`, ..., `CompleteExample.scala` |
| Data type refs | `BasicUsage.scala`, `AdvancedPatterns.scala`, `CompleteExample.scala` (or descriptive names like `CompleteHttpRequest.scala`) |
| Module refs | `MultiTypeComposition.scala`, `CommonPattern1.scala`, ..., `CompleteExample.scala` (titles emphasizing multi-type usage) |

Create 3-5 files total (feel free to write more examples if number of concepts warrants it).

### Step 3: Example File Template

Each example file follows this pattern:

For Scala 3:

```scala
package <packagename>

import <requiredImports>

/**
 * <Documentation Title> — Step/Concept/Pattern: <Title>
 *
 * <1-2 sentence description of what this example demonstrates.>
 *
 * Run with: sbt "<examples-module>/runMain <packagename>.<FunctionName>"
 */
@main def <FunctionName>(): Unit = {
   // Example code
}
```

For Scala 2.13:

```scala
package <packagename>

import <requiredImports>

/**
 * <Documentation Title> — Step/Concept/Pattern: <Title>
 *
 * <1-2 sentence description of what this example demonstrates.>
 *
 * Run with: sbt "<examples-module>/runMain <packagename>.<ObjectName>"
 */
object <ObjectName> extends App {
   // Example code
}
```

### Step 4: The Complete Example

The final example file (`CompleteExample.scala` or descriptively-named equivalent like `CompleteHttpRequest.scala`) must contain the **entire "Putting It Together" or most complex code block** from the document, wrapped in a runnable program (either a Scala 3 `@main def` function or a Scala 2.13 `object` extending `App`). This is the most important example file.

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

### Step 7: Documenting Examples

#### When Examples Use SourceFile Embedding

For data type references and module references where examples need detailed documentation:

Place the "Running the Examples" section at the end of the documentation, after all type/module documentation. Use this template:

```
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

**Optional parameters:**
- `lines = Seq((from, to))` — include only specific line ranges (1-indexed)
- `showLineNumbers = true` — render with line numbers
- `showTitle = false` — suppress the file path title

#### When Examples Use Basic Shell Commands

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

    
- List **every `App` object** in the examples module, one entry per object
- For each entry: use a `###` heading (simple, concise title), followed by a short descriptive paragraph
- The paragraph explains what the example demonstrates and the use case/pattern it covers
- For modules: emphasize which types compose in each example
- Embed full source with `SourceFile.print` (keeps docs and examples in sync automatically)
- Include source link and run command
- Keep the two numbered steps (clone, run individually) in that order
