---
name: docs-tutorial
description: Write a tutorial for newcomers learning a topic in ZIO Blocks. Use when the user asks to write a tutorial or learning guide that teaches concepts step-by-step in a linear path. Tutorials are learning-oriented (for newcomers with no prior knowledge) unlike how-to guides which are task-oriented.
argument-hint: "[tutorial title or topic description]"
allowed-tools: Read, Glob, Grep, Bash(sbt:*), Bash(sbt gh-query*)
---

# Write a Tutorial

Write a comprehensive, learning-oriented tutorial for ZIO Blocks newcomers.

## Tutorial Topic

$ARGUMENTS

## Overview: What Makes a Good Tutorial

A tutorial is **learning-oriented** — it teaches concepts and builds mental models for newcomers encountering a topic for the first time. It is neither a reference page (which documents an API exhaustively) nor a how-to guide (which helps practitioners accomplish a specific task). A tutorial assumes the reader has no prior knowledge and follows a linear, carefully controlled learning path.

Key properties of a good tutorial:

- **Targets newcomers**: The reader is encountering this topic for the first time. Assume nothing.
- **Teaches concepts, not tasks**: The goal is understanding, not accomplishing a specific thing yet.
- **Linear path**: No branching ("if you need X, do Y instead"). Pick one path and follow it.
- **Minimal, annotated code**: Code demonstrates concepts; it is not production-ready. Every code example is annotated line-by-line.
- **Learning objectives stated upfront**: The reader knows what they will understand by the end.
- **Intermediate output**: After each step, show results so the learner can verify they're on track.
- **Warm, welcoming tone**: Use "Welcome", "Let's", "notice that", "try changing X to see Y".
- **Recap at the end**: "What You've Learned" restates objectives as completed achievements.

---

## Step 1: Deep Research — Understand the Topic Landscape

Before writing a single word, you must build a complete mental model of every type, method, pattern, and concept relevant to the tutorial topic. This is the most critical step.

### 1a. Identify the Core Concept(s)

Based on the tutorial title/topic, identify which ZIO Blocks data types or concepts are central to teaching this topic. Use Glob and Grep to find their source files:

```
Glob: **/src/main/scala*/**/<TypeName>.scala
Grep: "class <TypeName>" or "trait <TypeName>" or "object <TypeName>"
```

For each core type:

1. **Read the full source file** — understand every public method, type parameter, companion object, and factory method.
2. **Read existing documentation** — check `docs/reference/` and `docs/` for any existing page about this type. Understand what is already documented vs. what you need to explain conceptually.
3. **Read the tests** — search `*/src/test/scala/` for test files. Tests reveal idiomatic usage patterns, edge cases, and realistic examples that you should mirror in your tutorial.

### 1b. Identify Supporting Concepts

Beyond the core concepts, find every supporting type or pattern that the learner will encounter:

1. **Grep for imports** in test files related to the core types — these reveal the full dependency graph.
2. **Trace the type signatures** — if a core method returns `Result[String, A]`, then `Result` is a supporting concept the learner needs to understand.
3. **Find implicit instances and type class derivation** — identify what derives automatically vs. what must be created manually.

For each supporting concept, read enough of its source and docs to explain it concisely in context (you do not need to be exhaustive — just what serves the tutorial's learning goal).

### 1c. Find Real-World Patterns

Search for realistic usage patterns:

1. **Examples directory**: Glob for `**/examples/**/*.scala` and read any examples related to the topic.
2. **Test suites**: The best source of idiomatic code. Look for integration tests that combine multiple types.
3. **Cross-module usages**: Grep for how the core types are used across different modules — this reveals integration patterns.

### 1d. Search GitHub History

Run `sbt "gh-query --verbose <topic>"` to search GitHub issues, PRs, and comments for discussions related to the tutorial topic. Use the results to:

- Understand design decisions and rationale behind the APIs involved
- Find known caveats, gotchas, or non-obvious behavior surfaced in issues
- Discover common beginner mistakes or misconceptions to address explicitly in the tutorial
- Identify examples or idioms shared by contributors that exemplify good practice
- Find real-world use cases that motivate the concepts being taught

Run multiple queries as needed — use the tutorial topic, the names of core types involved, and related feature keywords to get thorough coverage.

### 1e. Answer These Research Questions

Before proceeding to writing, you must be able to answer every one of these questions. Write the answers down (internally) as they will directly inform the tutorial structure:

**About the learning goals:**

1. What is the ONE core concept or skill this tutorial teaches?
2. What prerequisite knowledge must the learner already have? (e.g., "basic Scala syntax", "understanding of case classes")
3. What will the learner be able to do after completing this tutorial?
4. What mental model or conceptual framework will the learner have?

**About the types involved:**

5. For each core type: What is it, in one sentence? What role does it play in learning this concept?
6. What is the dependency/composition order? (e.g., "First understand X, then understand how Y builds on X, then combine them with Z")
7. Which factory methods and constructors will the learner actually use?
8. What type class instances are derived automatically vs. must be created manually?

**About the narrative arc:**

9. What is the simplest possible starting point? (The "hello world" for this concept)
10. What layers of complexity can be added incrementally? (e.g., start with a flat structure, then add nesting, then add variation)
11. Where should you pause to show intermediate results? (e.g., print output, display a value, demonstrate behavior)
12. What is the natural ending point — the "complete" version?
13. What is one key "aha moment" you want the learner to have?

**About the ecosystem:**

14. What imports does the learner need?
15. What sbt dependencies are required?
16. Are there Scala 2 vs. Scala 3 differences the learner should know about?
17. Does this integrate with other ZIO libraries (ZIO HTTP, ZIO Streams, etc.)?

---

## Step 2: Design the Tutorial Structure

Based on your research answers, design the tutorial's section structure before writing. A tutorial follows this general skeleton:

### Structural Template

```
1. Introduction
   - Who this is for (newcomer with no prior knowledge)
   - Learning objectives (bullet list)
   - Overview of what the tutorial covers (brief outline)
   - "We recommend reading from top to bottom"

2. Background / The Big Picture (optional, 1-2 paragraphs)
   - Conceptual framing: what problem this API was designed to solve
   - No code — just mental model

3. Concept sections (3-6 sections, each one new idea)
   - Explanation of the concept (1-3 sentences)
   - Minimal working code block (annotated line-by-line)
   - Output or result showing it worked
   - No branching, no "alternatively"

4. Putting It Together
   - The complete, runnable example combining all concepts
   - mdoc:compile-only block

5. Running the Examples
   - Git clone + sbt runMain per step (same format as how-to guides)

6. What You've Learned
   - Bullet-point recap of each learning objective

7. Where to Go Next
   - Links to how-to guides (for applying the knowledge in practice)
   - Links to reference pages (for API depth)
```

### Section Design Rules

- **Linear progression**: No branching. Never say "if you need X, do Y instead". Pick one path.
- **One concept per section**: Each section introduces exactly one new idea or builds incrementally on previous sections.
- **Concept before code**: Always explain what the code will do and why before showing it.
- **Every section has code**: No pure-prose sections. Every concept is demonstrated with code.
- **Line-by-line annotation**: Every code block is followed by a bullet-point breakdown explaining each line or block of lines.
- **Show intermediate output**: After meaningful steps, show or print results so the learner can verify they're on track.
- **Limit scope aggressively**: A tutorial about "understanding Scope" should not become "everything you can do with Scope". Stay on the learning objective.

### Narrative Planning

Plan the tutorial arc:

1. **Choose a relatable domain**: Pick something the learner can understand without domain expertise (e.g., "a simple configuration system", "managing a resource").
2. **Start with the simplest example**: Something that can be explained in 3-4 lines and demonstrates the core concept.
3. **Build incrementally**: Each subsequent section adds one layer of complexity or introduces one supporting concept.
4. **Plan the "show moments"**: Points where you print, observe, or demonstrate behavior. These are crucial for learner confidence.

---

## Step 3: Write the Tutorial

### File Location and Frontmatter

Place the file in `docs/guides/` directory (same location as how-to guides):

```
---
id: <kebab-case-id>
title: "<Tutorial Title>"
---
```

The `id` must match the filename (without `.md`).

### Writing the Sections

#### Introduction

Start with a welcome and clear statement of who this tutorial is for:

```
Welcome to [Tutorial Title]! This tutorial is for [target learner] who [assumed prior knowledge]. You don't need any prior experience with [topic] to follow along.
```

Immediately follow with **Learning Objectives** — a bulleted list of what the learner will understand by the end:

```
## Learning Objectives

By the end of this tutorial, you will understand:

- What [concept A] is and why it matters
- How to [do task B] with [API C]
- The relationship between [concept D] and [concept E]
- How to [construct/use] [type F]
```

Then include a brief outline of the tutorial sections:

```
## What We'll Cover

1. [Section Title] — [one-sentence summary]
2. [Section Title] — [one-sentence summary]
...

We recommend reading from top to bottom — each section builds on the previous one.
```

**Pattern:**

```
Welcome to [Topic]! This tutorial is designed for newcomers who [assumed prior knowledge — be specific].

## Learning Objectives

By the end, you will understand:
- [objective 1]
- [objective 2]
- [objective 3]

## What We'll Cover

1. [Introduction to core concept]
2. [Building on concept: variation 1]
3. [Building on concept: variation 2]
...

We recommend reading from top to bottom — each section builds on the previous one.
```

#### Background / The Big Picture (Optional)

If helpful, include 1-2 paragraphs that frame the conceptual motivation:

- What problem was this API designed to solve?
- What is the big mental model?
- Why does this matter?

**Important**: No code in this section. This is pure conceptual framing.

**Pattern:**

```
## Background

[1-2 paragraphs explaining the conceptual motivation, the problem this type solves, or the mental model you should have. No code.]
```

#### Concept Sections (3-6 sections, 1 new concept per section)

For each concept section:

1. **Lead with 1-3 sentences** explaining the concept and why it matters.
2. **Show minimal, annotated code** in an appropriate mdoc block.
3. **Annotate the code line-by-line** with bullet points immediately after the code block:

```
​```scala mdoc
val x = foo()  // create something
println(x)
​```

The code above:
- `foo()` — creates [what]
- `println(x)` — prints the result to see what was created
```

4. **Show the result** — if the code produces output, use `mdoc` (not `mdoc:compile-only`) to show evaluated output.
5. **Add a brief explanation** if the result is non-obvious.
6. **Use admonitions sparingly** for tips or things to watch out for:

```
:::tip
Notice that [important observation]. This is key because [why].
:::
```

7. **Never branch**: Do not write "alternatively, you could..." or "if you need X, use Y instead". Pick one approach and follow it.

#### Putting It Together

Near the end, show the complete working example that combines everything from the tutorial into a single cohesive block. Use `mdoc:compile-only` or `mdoc:silent:reset` + `mdoc:compile-only` so it demonstrates all concepts together without necessarily showing output.

Add a brief explanation: "This example combines all the concepts we've learned — [quick summary]."

#### Running the Examples

After the "Putting It Together" section, include a **"Running the Examples"** section that tells the reader how to download and run the companion example code. This section must always be present and follow this exact pattern (substituting `<packagename>` and example object names to match the tutorial):

```markdown
## Running the Examples

All code from this tutorial is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

​```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
​```

**2. Run individual examples with sbt:**

​```bash
# Concept 1: <brief description>
sbt "schema-examples/runMain <packagename>.<Concept1ObjectName>"

# Concept 2: <brief description>
sbt "schema-examples/runMain <packagename>.<Concept2ObjectName>"

# ...additional concepts...

# Complete example
sbt "schema-examples/runMain <packagename>.<CompleteObjectName>"
​```

**3. Or compile all examples at once:**

​```bash
sbt "schema-examples/compile"
​```
```

**Key rules for this section:**

- Use plain `` ```bash `` code blocks (not mdoc — these are shell commands).
- List every companion example file with its `sbt "schema-examples/runMain ..."` command and a short comment describing what it demonstrates.
- The clone URL must be `https://github.com/zio/zio-blocks.git`.
- Keep the section concise and mechanical — no extra prose beyond what is needed to run the code.

#### What You've Learned

Recap what the learner accomplished. Mirror the "Learning Objectives" section from the introduction, but restate them as achievements:

```
## What You've Learned

In this tutorial, you learned:

- What [concept A] is and why it matters
- How to [do task B] with [API C]
- The relationship between [concept D] and [concept E]
- How to [construct/use] [type F]

You now have a solid foundation in [topic]. The next step is to see how to [apply this in practice].
```

#### Where to Go Next

Provide links to help the learner deepen their knowledge:

```
## Where to Go Next

- **Ready to use this in practice?** Check out the how-to guide [Guide Name](../guides/guide-name.md) which walks through a real-world example.
- **Want to dive deeper into the API?** Read the reference page for [`TypeName`](../reference/type-name.md).
- **Interested in related concepts?** Explore [Related Topic](./related-topic.md).
```

### Writing Style Rules

See the **`docs-writing-style`** skill for universal prose style, Scala version rules, and code block conventions.

**Additional style notes for tutorials:**

- Use warm, welcoming language: "Welcome", "Let's", "Notice that", "Try changing X to see what happens"
- Use present tense: "we learn", "we see", "we observe"
- Address the learner directly: "you now understand", "you can now do"
- Keep explanations brief and clear — a tutorial is about understanding, not about exhaustively covering every detail

### Compile-Checked Code Blocks with mdoc

See the **`docs-mdoc-conventions`** skill for the complete mdoc modifier table, key rules, and the "For Tutorials (Linear Learning Path)" section (or equivalent) which explains the recommended modifier sequence for tutorials specifically.

### Docusaurus Admonitions

See the **`docs-mdoc-conventions`** skill for admonition syntax and usage guidelines.

---

## Step 4: Create Companion Examples

Each tutorial must have a companion example module in `schema-examples/src/main/scala/` that provides **runnable code** for the key concepts and the final result. This gives readers working code they can clone and run immediately.

### 4a. Directory and Package Structure

Create a package directory matching the tutorial's kebab-case id (converted to a valid Scala package name):

```
schema-examples/src/main/scala/<packagename>/
```

For example, a tutorial with id `scope-resource-management` would use the package `scoperesourcemanagement` (drop hyphens). A tutorial with id `context-basics` would use `contextbasics`.

### 4b. Example File Structure

Create **one Scala file per major concept** of the tutorial, plus a final file for the complete example. Each file should be a standalone runnable `object` extending `App` (or defining a `@main` method).

**Naming convention:**

| File | Purpose |
|------|---------|
| `Concept1Example.scala` | First concept of the tutorial |
| `Concept2Example.scala` | Second concept |
| `...` | Additional concepts as needed |
| `CompleteExample.scala` | The "Putting It Together" example from the tutorial |

You do not need a file for every single section — only for sections that introduce a meaningful, self-contained code example. Use your judgment to decide which concepts are substantial enough to warrant their own file. Typically 3-5 files is appropriate.

### 4c. Example File Template

Each example file follows this pattern:

```scala
package <packagename>

import zio.blocks.schema._
// ... other imports as needed

/**
 * <Tutorial Title> — <Concept Name>
 *
 * <1-2 sentence description of what this example demonstrates.>
 *
 * Run with: sbt "schema-examples/runMain <packagename>.<ObjectName>"
 */
object <ObjectName> extends App {

  // --- Concept Explanation ---
  // (code demonstrating the concept)

  // --- Output ---
  // (print statements showing the result)
  println(result)
}
```

**Key rules for example files:**

- **Each file must be fully self-contained.** It must compile and run independently — do not rely on types or values defined in other example files. Duplicate domain types across files if needed.
- **Include all imports.** Every file must have complete imports at the top.
- **Include `println` output.** The reader should see meaningful output when they run the example. Print results and observations, not just the final answer.
- **Include a scaladoc comment** with the tutorial title, concept name, description, and the `sbt runMain` command.
- **Mirror the tutorial's code closely.** The example code should match what the tutorial shows, with only the additions needed to make it runnable (e.g., wrapping in `object ... extends App`, adding `println`).
- **Use descriptive object names.** `Concept1Introduction` is better than `Concept1`. `CompleteResourceManagement` is better than `Complete`.

### 4d. The Complete Example

The final `CompleteExample.scala` (or a descriptively-named equivalent like `CompleteScopeManagement.scala`) must contain the **entire "Putting It Together" code block** from the tutorial, wrapped in a runnable `object`. This is the most important example file — it is the working artifact the learner takes away.

### 4e. Verify Examples Compile

After creating all example files, verify they compile:

```bash
sbt "schema-examples/compile"
```

If any example fails to compile, fix it before proceeding. The examples must compile successfully.

### 4f. Lint Check (Mandatory Before Integration)

After all examples compile, stage them in git first, then run Scalafmt to ensure all Scala files pass the CI formatting gate:

```bash
git add schema-examples/src/main/scala/**/*.scala
sbt fmtChanged
```

If any files were reformatted, commit the changes immediately:

```bash
git add -A
git commit -m "docs(<tutorial-id>): apply scalafmt to examples"
```

Verify the CI lint gate locally:

```bash
sbt check
```

**Success criterion:** zero formatting violations reported.

---

## Step 5: Integrate

See the **`docs-integrate`** skill for the complete integration checklist (sidebars.js, index.md, cross-references, link verification).

Additional notes for tutorials:

- Place the file in `docs/guides/` directory (same location as how-to guides).
- In `sidebars.js`, add under the "Guides" category alongside how-to guides.
- Cross-reference: add links from any related reference pages (e.g., if the tutorial teaches `Scope`, add a "See also" link from `docs/reference/scope.md`).
- Tutorials should link to related how-to guides in the "Where to Go Next" section to guide learners toward practical application.

---

## Step 6: Review Checklist

After writing, verify every item on this checklist:

### Content Quality

- [ ] The tutorial clearly states who it is for (newcomer, assumed prior knowledge)
- [ ] Learning objectives are stated upfront as a bullet list
- [ ] Learning objectives are restated at the end in "What You've Learned"
- [ ] The tutorial follows a strict linear path (no branching, no "alternatively")
- [ ] Every section introduces exactly one new concept or builds incrementally
- [ ] No section is pure prose without a code example
- [ ] Every code example is annotated line-by-line with bullet-point explanations
- [ ] Intermediate results are shown (printed or observed) after each major step
- [ ] The running example is simple and clearly demonstrates the core concepts
- [ ] Types and APIs are introduced only as needed (no front-loaded theory)
- [ ] The tone is warm and welcoming (uses "welcome", "let's", "notice that")
- [ ] The "Putting It Together" section is a complete, self-contained, copy-paste-ready example
- [ ] The "Background" section (if present) explains motivation without code

### Technical Accuracy

- [ ] All method signatures and type names match the actual source code
- [ ] All code examples use correct mdoc modifiers and would compile
- [ ] Imports are complete and correct in every code block
- [ ] The sbt dependency (if mentioned) is correct
- [ ] No deprecated methods or outdated patterns are used
- [ ] Run `sbt "docs/mdoc --in docs/tutorials/<tutorial-id>.md"` and confirm zero `[error]` lines (this is mandatory before claiming the tutorial is done)

### Companion Examples

- [ ] A package directory exists in `schema-examples/src/main/scala/<packagename>/`
- [ ] There is one example file per major tutorial concept (typically 3-5 files)
- [ ] There is a `CompleteExample.scala` (or descriptively named equivalent) with the full "Putting It Together" code
- [ ] Each example file is fully self-contained (compiles and runs independently)
- [ ] Each example file has complete imports
- [ ] Each example file has a scaladoc with tutorial title, concept name, description, and `sbt runMain` command
- [ ] Each example file includes `println` output showing meaningful results
- [ ] All examples compile successfully (`sbt "schema-examples/compile"`)

### Running the Examples Section

- [ ] The tutorial includes a "Running the Examples" section after "Putting It Together"
- [ ] The section includes `git clone https://github.com/zio/zio-blocks.git` and `cd zio-blocks`
- [ ] Every companion example file is listed with its `sbt "schema-examples/runMain ..."` command
- [ ] The section includes `sbt "schema-examples/compile"` as an alternative

### Style and Integration

- [ ] The frontmatter `id` matches the filename
- [ ] The tutorial is in `docs/guides/` (same directory as how-to guides)
- [ ] The tutorial is added to `sidebars.js` under the "Guides" category
- [ ] The tutorial is linked from `docs/index.md`
- [ ] Related reference pages link back to this tutorial
- [ ] Writing style follows the rules (warm tone, present tense, "we"/"you", concise, no emojis)
- [ ] Admonitions are used sparingly and for genuinely important callouts
