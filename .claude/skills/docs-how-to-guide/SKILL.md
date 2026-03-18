---
name: docs-how-to-guide
description: Write a how-to guide on a specific topic in ZIO Blocks. Use when the user asks to write a guide or walkthrough that teaches how to accomplish a concrete goal using ZIO Blocks data types and APIs.
argument-hint: "[guide title or topic description]"
allowed-tools: Read, Glob, Grep, Bash(sbt:*), Bash(sbt gh-query*)
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

**For the source research procedure (finding source files, tests, examples, and GitHub history), use the `docs-research` skill.** It covers steps 1a–1d: identifying core data types, supporting types, real-world patterns, and GitHub history search.

### 1e. Answer These Research Questions

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
7. Running the Examples (how to clone the repo and run companion code)
8. Going Further (optional: variations, advanced techniques, links)
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

#### Running the Examples

Follow the **"Running the Examples" section template** from the `docs-examples` skill. It provides the exact Markdown pattern to use in your guide, substituting the correct `<packagename>` and example object names.

#### Going Further (Optional)

If relevant, end with:
- Links to related reference pages for deeper API coverage
- Variations or extensions the reader might try
- Links to other guides that build on this one

### Writing Style Rules

See the **`docs-writing-style`** skill for universal prose style, Scala version rules, and code
block conventions.

### Compile-Checked Code Blocks with mdoc

See the **`docs-mdoc-conventions`** skill for the complete mdoc modifier table, key rules, and
the "For How-To Guides (Progressive Narrative)" section which explains the recommended modifier
sequence for guides specifically.

### Docusaurus Admonitions

See the **`docs-mdoc-conventions`** skill for admonition syntax and usage guidelines.

---

## Step 4: Create Companion Examples

Use the **`docs-examples`** skill to create companion examples. It covers all aspects: directory structure, file templates, compilation verification, and the lint check procedure. Follow steps 4a–4f exactly as documented in that skill.

---

## Step 5: Verify Mdoc Compilation

Before integrating, verify that all code examples in the guide compile:

```bash
sbt "docs/mdoc --in docs/guides/<guide-name>.md"
```

**Important:** Always use `--in <file.md>` to compile only this file. Never use bare `sbt docs/mdoc` without `--in` — it recompiles all documentation (~90 seconds).

Fix any compilation errors before proceeding to integration.

---

## Step 6: Integrate

See the **`docs-integrate`** skill for the complete integration checklist (sidebars.js, index.md,
cross-references, link verification).

Additional notes for how-to guides:
- Place the file in `docs/guides/` directory.
- In `sidebars.js`, add under a "Guides" category (create it if absent).
- Cross-reference: add links from any related reference pages (e.g., if the guide uses `Schema`,
  add a "See also" link from `docs/reference/schema.md`).

---

## Step 7: Review Checklist

Before submitting, work through the checklist in [CHECKLIST.md](./CHECKLIST.md).
