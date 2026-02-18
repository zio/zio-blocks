---
name: write-how-to-guide
description: Write a how-to guide on a specific topic in ZIO Blocks. Use when the user asks to write a guide, tutorial, or walkthrough that teaches how to accomplish a concrete goal using ZIO Blocks data types and APIs.
argument-hint: "[guide title or topic description]"
allowed-tools: Read, Glob, Grep, Bash(sbt:*)
---

# Write a How-To Guide

Write a comprehensive, goal-oriented how-to guide for ZIO Blocks.

## Guide Topic

$ARGUMENTS

## Overview: What Makes a Good How-To Guide

A how-to guide is **goal-oriented** — it helps the reader accomplish a specific, concrete task. It is neither a reference page (which documents an API exhaustively) nor a tutorial (which teaches concepts step by step). A how-to guide assumes the reader already has basic familiarity and wants to get something done.

Key properties of a good how-to guide:

- **Starts with a clear goal**: The reader knows exactly what they will accomplish by the end.
- **Shows a practical, realistic example**: Not toy examples — something close to what a real user would build.
- **Introduces types and APIs only as needed**: No exhaustive API coverage; only what serves the goal.
- **Builds incrementally**: Each section builds on the previous one, progressing toward the goal.
- **Ends with a working result**: The reader has something functional at the end.

---

## Step 1: Deep Research — Understand the Topic Landscape

Before writing a single word, you must build a complete mental model of every type, method, pattern, and integration point relevant to the guide topic. This is the most critical step.

### 1a. Identify the Core Data Types

Based on the guide title/topic, identify which ZIO Blocks data types are central to the goal. Use Glob and Grep to find their source files:

```
Glob: **/src/main/scala*/**/<TypeName>.scala
Grep: "class <TypeName>" or "trait <TypeName>" or "object <TypeName>"
```

For each core type:

1. **Read the full source file** — understand every public method, type parameter, companion object, and factory method.
2. **Read existing documentation** — check `docs/reference/` and `docs/` for any existing page about this type. Understand what is already documented vs. what you need to explain in context.
3. **Read the tests** — search `*/src/test/scala/` for test files. Tests reveal idiomatic usage patterns, edge cases, and realistic examples that you should mirror in your guide.

### 1b. Identify Supporting Types

Beyond the core types, find every supporting type that the reader will encounter:

1. **Grep for imports** in test files related to the core types — these reveal the full dependency graph.
2. **Trace the type signatures** — if a core method returns `Validation[String, A]`, then `Validation` is a supporting type the reader needs to understand.
3. **Find implicit instances and type class derivation** — if the guide involves `Schema`, the reader may need to understand `Reflect`, `Binding`, `Modifier`, etc.

For each supporting type, read enough of its source and docs to explain it concisely in context (you do not need to be exhaustive — just what serves the guide's goal).

### 1c. Find Real-World Patterns

Search for realistic usage patterns:

1. **Examples directory**: Glob for `**/examples/**/*.scala` and read any examples related to the topic.
2. **Test suites**: The best source of idiomatic code. Look for integration tests that combine multiple types.
3. **Cross-module usages**: Grep for how the core types are used across different modules — this reveals integration patterns.

### 1d. Answer These Research Questions

Before proceeding to writing, you must be able to answer every one of these questions. Write the answers down (internally) as they will directly inform the guide structure:

**About the problem:**

1. What specific problem does this guide solve? State it concretely, not abstractly.
2. What happens if the reader does NOT have this solution? (boilerplate, runtime errors, maintenance burden, etc.)
3. How would the reader attempt to solve this without ZIO Blocks? Can you show a "before" code example?
4. Is this a common pain point that many developers face, or a niche scenario? (This affects how much you need to motivate the problem.)

**About the goal:**

5. What concrete thing will the reader have built/accomplished by the end of this guide?
6. What is the minimal set of types and operations needed to achieve this goal?
7. What are the key decision points where the reader must choose between approaches?
8. What are the common mistakes or pitfalls a reader might hit?

**About the types involved:**

9. For each core type: What is it, in one sentence? What role does it play in achieving the goal?
10. What is the dependency/composition order? (e.g., "First define a Schema, then derive a Codec, then use a Format")
11. Which factory methods and constructors will the reader actually use?
12. What type class instances are derived automatically vs. must be created manually?

**About the narrative arc:**

13. What is the simplest possible starting point? (The "hello world" for this goal)
14. What layers of complexity can be added incrementally? (e.g., start with a flat record, then add nesting, then add collections, then add custom types)
15. Where should you pause to show intermediate results? (e.g., print output, show JSON, demonstrate validation)
16. What is the natural ending point — the "complete" version?

**About the ecosystem:**

17. What imports does the reader need?
18. What sbt dependencies are required?
19. Are there Scala 2 vs. Scala 3 differences the reader should know about?
20. Does this integrate with other ZIO libraries (ZIO HTTP, ZIO Streams, etc.)?

---

## Step 2: Design the Guide Structure

Based on your research answers, design the guide's section structure before writing. A how-to guide follows this general skeleton:

### Structural Template

```
1. Introduction (what we're building and why)
2. The Problem (what problem we're solving, why it matters, example of the pain)
3. Prerequisites (dependencies, imports, assumed knowledge)
4. The Core Model (define the domain types we'll work with)
5. Step-by-step sections (each building toward the goal)
   - Each section: brief explanation → code → result/output
6. Putting It Together (the complete working example)
7. Going Further (optional: variations, advanced techniques, links)
```

### Section Design Rules

- **Each section has exactly one new concept or capability.** If you find yourself explaining two unrelated things in one section, split it.
- **Every section has at least one code example.** No section should be pure prose.
- **Sections should be independently valuable** where possible — a reader skimming should be able to jump to a section and get value.
- **Use progressive disclosure:** Start with the simplest version that works, then add complexity. Do not front-load all the types and theory.
- **Limit scope aggressively.** A guide about "writing a query DSL" should not become a guide about "everything you can do with Schema." Stay on topic.

### Narrative Planning

Plan the running example that threads through the guide:

1. **Choose a realistic domain** — e.g., an e-commerce system, a blog platform, a user management system. Pick something readers can relate to.
2. **Define 3-5 domain types** that demonstrate the features you need. Start simple (1-2 fields) and grow them as the guide progresses.
3. **Plan the "show moments"** — points where you print, serialize, validate, or otherwise demonstrate that the code works. These are crucial for reader confidence.

---

## Step 3: Write the Guide

### File Location and Frontmatter

Place the file in `docs/guides/` directory:

```
---
id: <kebab-case-id>
title: "<Guide Title>"
---
```

The `id` must match the filename (without `.md`).

### Writing the Sections

#### Introduction

Start with a single paragraph stating:
- What the reader will accomplish (the goal)
- Why this is useful (the motivation)
- What approach we will take (the strategy, in one sentence)

Then include a brief outline of what the guide covers — a bulleted list or a table of contents if the guide is long (more than 6 sections).

Do NOT start with theory or type definitions. Start with the promise of what the reader will build.

**Pattern:**
```
In this guide, we will build [concrete thing] using [key ZIO Blocks types]. By the end,
you will have [tangible result] that [does something useful].

We'll take an incremental approach: starting with [simple version], then adding
[feature], [feature], and [feature] until we have a complete [thing].
```

#### The Problem

Immediately after the introduction, include a dedicated section that clearly states the problem this guide solves. This section has three parts:

1. **State the problem concretely.** Describe the specific pain point, challenge, or gap that motivates this guide. Be precise — "serializing data is hard" is too vague; "you need to serialize a deeply nested case class hierarchy to JSON without writing boilerplate encoders for each type" is concrete.

2. **Explain why it matters.** Connect the problem to real consequences the reader cares about: wasted time, runtime errors, maintenance burden, boilerplate explosion, fragile code, etc. Help the reader feel the weight of the problem so the solution feels earned.

3. **Show examples of the problem.** When possible, include a short code example (or a description of a scenario) that makes the problem tangible. Show what the reader's code looks like *without* the solution — verbose, error-prone, or brittle. This creates a clear "before/after" contrast with the rest of the guide.

**Pattern:**
```
## The Problem

[1-2 sentences naming the specific problem.]

[1-2 sentences explaining why this matters — what goes wrong if you don't solve it.]

For example, consider [a realistic scenario]:

​```scala
// Without ZIO Blocks, you might write something like this:
// [show the painful/boilerplate/fragile approach]
​```

This approach [breaks down when X / doesn't scale because Y / is error-prone because Z].

In this guide, we'll solve this by [brief preview of the ZIO Blocks approach].
```

**Guidelines for this section:**
- Keep it to 1-2 short paragraphs plus an optional code example. Do not over-explain.
- The code example should be plain `scala` (no mdoc) since it shows non-ZIO-Blocks code or pseudocode.
- If the problem is architectural or conceptual (not easily shown in code), use a concrete scenario description instead of a code example. For instance: "Imagine you have 40 case classes representing your API schema and you need to keep JSON codecs, database mappings, and OpenAPI specs in sync."
- The problem section naturally sets up the rest of the guide. The reader should finish this section thinking "yes, I have this exact problem" and be motivated to read on.

#### Prerequisites

A short section listing:
- **sbt dependency** (the `libraryDependencies` line)
- **Base imports** that will be used throughout (put in an `mdoc:silent` block so subsequent blocks can use them)
- **Assumed knowledge** — what the reader should already know (link to relevant reference pages)

#### Core Model / Domain Setup

Define the domain types the guide will use. Use an `mdoc:silent` block so subsequent sections can reference these types:

```scala mdoc:silent
case class User(name: String, email: String, age: Int)
case class Order(id: Long, userId: Long, items: List[Item])
case class Item(name: String, price: Double, quantity: Int)
```

Briefly explain why you chose these types and how they relate to the goal.

#### Step-by-Step Sections

For each section:

1. **Lead with 1-3 sentences** explaining what we're doing and why.
2. **Show the code** in an appropriate mdoc block.
3. **Show the result** — if the code produces output, use `mdoc` (not `mdoc:compile-only`) to show the evaluated result. If it's a type-level or structural result, add a comment showing what was created.
4. **Add a brief "what happened" explanation** if the code does something non-obvious.
5. **Use admonitions** for tips, warnings, or gotchas:

```
:::tip
This pattern also works for [related use case].
:::

:::warning
Do not [common mistake] — it will [bad consequence].
:::
```

#### Putting It Together

Near the end, show the complete working example that combines everything from the guide into a single cohesive block. This should be a `mdoc:compile-only` or `mdoc:silent:reset` + `mdoc:compile-only` block that a reader could copy-paste and run.

#### Going Further (Optional)

If relevant, end with:
- Links to related reference pages for deeper API coverage
- Variations or extensions the reader might try
- Links to other guides that build on this one

### Scala Version

All code in the guide and companion example files **must use Scala 2.13.x syntax**. When in doubt, check the companion example files — they are the source of truth for syntax style.

### Writing Style Rules

Follow these rules precisely:

- **Person**: Use "we" when guiding the reader ("we can create...", "we need to..."). Use "you" when addressing the reader's choices ("if you need...", "you might want to...").
- **Tense**: Present tense ("returns", "creates", "produces").
- **Concision**: Keep prose short. Let code examples do the heavy lifting. If you find yourself writing more than 4 sentences of prose without a code block, you're being too verbose.
- **Project name**: "ZIO Blocks" (not "zio-blocks").
- **Don't pad**: No filler phrases like "as we can see" or "it's worth noting that". Just state the fact.
- **No exhaustive API coverage**: Only document the methods and types that serve the guide's goal. Link to reference pages for full API details.
- **No emojis**: Unless the user explicitly requests them.
- **ASCII art**: Use it for diagrams showing data flow, type relationships, or architecture. Readers find these very helpful for understanding how pieces fit together.

### Compile-Checked Code Blocks with mdoc

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

#### Choosing the Right Modifier — Guide-Specific Advice

How-to guides have a **progressive narrative** where code builds on itself. This means you will use shared-scope modifiers more often than reference pages:

1. **Domain setup** (case classes, imports): `mdoc:silent` — hidden, but in scope for all subsequent blocks.
2. **First example** (showing how something works): `mdoc` — show source + output so the reader sees the result.
3. **Building on the example** (adding a feature): `mdoc:silent:nest` if redefining, `mdoc` if showing output.
4. **New topic within the guide**: `mdoc:silent:reset` to start clean, then `mdoc:silent` for new setup.
5. **Final "putting it together"**: `mdoc:compile-only` — fully self-contained, copy-paste ready.

### Docusaurus Admonitions

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
```

Use admonitions sparingly — at most 3-4 in a typical guide. They should highlight genuinely important information, not decorate every section.

---

## Step 4: Create Companion Examples

Each guide must have a companion example module in `zio-blocks-examples/src/main/scala/` that provides **runnable code** for the key steps and the final result. This gives readers working code they can clone and run immediately.

### 4a. Directory and Package Structure

Create a package directory matching the guide's kebab-case id (converted to a valid Scala package name):

```
zio-blocks-examples/src/main/scala/<packagename>/
```

For example, a guide with id `query-dsl-sql` would use the package `querydsl` (drop hyphens). A guide with id `typeclass-derivation` would use `typeclassderivation`.

### 4b. Example File Structure

Create **one Scala file per major step** of the guide, plus a final file for the complete example. Each file should be a standalone runnable `object` extending `App` (or defining a `@main` method).

**Naming convention:**

| File | Purpose |
|------|---------|
| `Step1BasicExample.scala` | First step of the guide |
| `Step2AdvancedExample.scala` | Second step |
| `...` | Additional steps as needed |
| `CompleteExample.scala` | The "Putting It Together" example from the guide |

You do not need a file for every single section — only for sections that introduce a meaningful, self-contained code example. Use your judgment to decide which steps are substantial enough to warrant their own file. Typically 3-5 files is appropriate.

### 4c. Example File Template

Each example file follows this pattern:

```scala
package <packagename>

import zio.blocks.schema._
// ... other imports as needed

/**
 * <Guide Title> — Step N: <Step Title>
 *
 * <1-2 sentence description of what this example demonstrates.>
 *
 * Run with: sbt "examples/runMain <packagename>.<ObjectName>"
 */
object <ObjectName> extends App {

  // --- Domain Types ---
  // (repeat the domain types needed for this step)

  // --- Step Logic ---
  // (the code from this step of the guide)

  // --- Output ---
  // (print statements showing the result)
  println(result)
}
```

**Key rules for example files:**

- **Each file must be fully self-contained.** It must compile and run independently — do not rely on types or values defined in other example files. Duplicate domain types across files if needed.
- **Include all imports.** Every file must have complete imports at the top.
- **Include `println` output.** The reader should see meaningful output when they run the example. Print intermediate results, not just the final answer.
- **Include a scaladoc comment** with the guide title, step number, description, and the `sbt runMain` command.
- **Mirror the guide's code closely.** The example code should match what the guide shows, with only the additions needed to make it runnable (e.g., wrapping in `object ... extends App`, adding `println`).
- **Use descriptive object names.** `Step1Expressions` is better than `Step1`. `CompleteQueryDSL` is better than `Complete`.

### 4d. The Complete Example

The final `CompleteExample.scala` (or a descriptively-named equivalent like `CompleteSqlGenerator.scala`) must contain the **entire "Putting It Together" code block** from the guide, wrapped in a runnable `object`. This is the most important example file — it is the working artifact the reader takes away.

### 4e. Verify Examples Compile

After creating all example files, verify they compile:

```bash
sbt "examples/compile"
```

If any example fails to compile, fix it before proceeding. The examples must compile successfully.

---

## Step 5: Integrate

After writing the guide and creating examples:

1. **Add to `sidebars.js`**: Add the guide's `id` to the sidebar. Place it after reference pages, grouped with other guides if any exist. If there is no "Guides" category yet, add one:

```javascript
{
  type: "category",
  label: "Guides",
  items: [
    "guides/guide-id-here",
  ]
}
```

2. **Update `docs/index.md`**: Add a link to the guide under an appropriate section. If a "Guides" section does not exist, create one after the reference documentation links.

3. **Cross-reference**: Add links from related existing reference pages to the new guide (e.g., if you wrote a guide about query DSLs using `Schema` and `DynamicOptic`, add a "See also" link from `docs/reference/schema.md` and `docs/reference/dynamic-optic.md`).

4. **Verify all links**: Ensure relative links in the guide and in updated pages are correct.

---

## Step 6: Review Checklist

After writing, verify every item on this checklist:

### Content Quality
- [ ] The guide has a clear, stated goal in the introduction
- [ ] The guide has a "Problem" section that concretely states what problem is being solved
- [ ] The problem section explains why the problem matters (real consequences)
- [ ] The problem section includes an example of the pain (code or concrete scenario)
- [ ] A reader who follows every step will have a working result at the end
- [ ] Every section introduces exactly one new concept or capability
- [ ] No section is pure prose without a code example
- [ ] The running example is realistic and relatable
- [ ] Types and APIs are introduced only when needed (no front-loaded theory dumps)
- [ ] The "Putting It Together" section is a complete, self-contained, copy-paste-ready example

### Technical Accuracy
- [ ] All method signatures and type names match the actual source code
- [ ] All code examples use correct mdoc modifiers and would compile
- [ ] Imports are complete and correct in every code block
- [ ] The sbt dependency in Prerequisites is correct
- [ ] No deprecated methods or outdated patterns are used

### Companion Examples
- [ ] A package directory exists in `zio-blocks-examples/src/main/scala/<packagename>/`
- [ ] There is one example file per major guide step (typically 3-5 files)
- [ ] There is a `CompleteExample.scala` (or descriptively named equivalent) with the full "Putting It Together" code
- [ ] Each example file is fully self-contained (compiles and runs independently)
- [ ] Each example file has complete imports
- [ ] Each example file has a scaladoc with guide title, step description, and `sbt runMain` command
- [ ] Each example file includes `println` output showing meaningful results
- [ ] All examples compile successfully (`sbt "examples/compile"`)

### Style and Integration
- [ ] The frontmatter `id` matches the filename
- [ ] The guide is added to `sidebars.js`
- [ ] The guide is linked from `docs/index.md`
- [ ] Related reference pages link back to this guide
- [ ] Writing style follows the rules (present tense, "we"/"you", concise, no emojis)
- [ ] Admonitions are used sparingly and for genuinely important callouts
