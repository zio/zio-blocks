---
name: docs-module-ref
description: Write reference documentation for a module containing multiple related data types. Use when documenting a cohesive domain model (HTTP model, resource management) where types work together. Produces comprehensive type-level pages plus module-level narrative showing relationships, patterns, and composition.
argument-hint: "[module-name (e.g., 'http-model', 'resource-management')]"
allowed-tools: Read, Glob, Grep, Bash(sbt:*), Bash(sbt gh-query*)
---

# Module Reference Documentation

**REQUIRED BACKGROUND:** Use `docs-writing-style` for prose conventions, `docs-mdoc-conventions` for code block syntax, and `docs-data-type-ref` structure as baseline for type-level pages.

## Target Module

$ARGUMENTS

## Overview

This skill produces comprehensive reference documentation for modules with multiple related types. Unlike `docs-data-type-ref` (single type), `docs-module-ref` emphasizes:
- **Module narrative:** How types work together, common patterns, architectural relationships
- **Type-level comprehensiveness:** Each type gets full docs-data-type-ref coverage, contextualized within the module
- **Multi-type examples:** Show composition and cross-type usage, not just single-type API

---

## Step 1: Research & Map the Module

Use the **`docs-research`** skill to:
1. Find all core and supporting types in the module
2. Identify type relationships and dependencies (which types use which types)
3. Find tests, examples, and real-world usage patterns
4. Search GitHub history for design rationale and evolution
5. Review any existing partial documentation

**Additional mapping:** Create a mental model of:
- **Core types** (primary exports, main abstractions): e.g., Request, Response for http-model
- **Supporting types** (helpers, variants): e.g., Method, Status, Version
- **Relationships** (uses/composes with): e.g., Request contains Headers, URL, Body
- **Data flow** (typical usage pattern): e.g., create URL → create Request → send → receive Response

---

## Step 2: Decide Structure (Flat vs. Hierarchical)

Ask the user which structure they prefer for this module:

**Option A: Flat** (.md single file)
- Single file: `docs/reference/<module-name>.md`
- All types documented inline with `##` headings
- Best for modules with ≤4 core types or focused, concise APIs
- Pattern example: `http-model.md` (1,716 lines, 140+ types)
- Use when types are always used together and separating them is artificial

**Option B: Hierarchical** (subdirectory/)
- Module index: `docs/reference/<module-name>/index.md`
- Individual type pages: `docs/reference/<module-name>/<type>.md`
- Best for modules with ≥3 core types with rich, diverse APIs
- Pattern example: `resource-management/` (index.md + scope.md, resource.md, wire.md)
- Use when types have significant self-contained value and readers benefit from per-type pages

**User decision:** Ask explicitly which structure the user wants. Do not auto-detect or recommend — let the user choose based on their module and documentation goals.

---

## Step 3: Write Module-Level Documentation

### File Location & Frontmatter

**Flat:** `docs/reference/<module-name>.md`

```yaml
---
id: <module-name-kebab-case>
title: "<Module Title>"
---
```

**Hierarchical:** `docs/reference/<module-name>/index.md`

```yaml
---
id: index
title: "<Module Title>"
---
```

### Module-Level Sections (BOTH STRUCTURES)

#### 1. Opening Definition (NO HEADING)

Immediately after frontmatter, state what the module provides:
- Concise statement of module purpose in 1-3 sentences
- List core types as inline code: `` `Type1`, `Type2`, `Type3` ``
- Scala code block showing structural shape of 2-3 main types (plain `` ```scala `` without mdoc)

**Example:**
```
`zio-http-model` is a **pure, zero-dependency HTTP data model** for building clients and servers. 
It provides immutable types representing requests, responses, headers, URLs, paths, and HTTP primitives.
Core types: `Request`, `Response`, `URL`, `Headers`, `Body`, `Method`, `Status`.

```scala
final case class Request(method: Method, url: URL, headers: Headers, body: Body, version: Version)
final case class Response(status: Status, headers: Headers, body: Body, version: Version)
final case class URL(scheme: Option[Scheme], host: Option[String], port: Option[Int], ...)
```

Then continue with `## Introduction` or `## Motivation` heading.
```

#### 2. Introduction (if hierarchical) OR Motivation (if flat)

**Hierarchical:** Brief welcome section explaining the module's role and what readers will learn.

**Flat:** Why use this module over alternatives? Problem it solves, advantages, bullet points.

#### 3. Motivation / Use Case

Answer: What problem does it solve? Why use it over alternatives?
- Include advantages as bullet points or ASCII art
- Compare with standard library or other libraries if relevant

#### 4. Installation

Standard format (same as `docs-data-type-ref`):

```scala
libraryDependencies += "dev.zio" %% "zio-http-model" % "<version>"
```

For Scala.js: use `%%%` instead of `%%`.

Supported Scala versions: 2.13.x and 3.x

#### 5. Overview (Hierarchical ONLY, optional for Flat)

Brief introduction to each core type (2-3 sentences each):
- What each type does
- Its role in the module
- Link to individual type page (hierarchical) or section (flat)

#### 6. How They Work Together (CRITICAL)

**THIS SECTION IS THE CENTERPIECE — don't skip it.**

Explain the typical workflow or data flow:
- Numbered steps showing usage sequence (e.g., "1. Create URL → 2. Create Request → 3. Send → 4. Receive Response")
- ASCII diagram showing type relationships and interactions
- Example: How does Type1 use Type2? How does Type3 depend on them?
- Show composition patterns (if Type1 contains Type2, Type3 is a variant of Type2, etc.)

**Example for Resource Management:**
```
1. Define dependencies using Wire.shared[T] (macro inspects constructors)
2. Compose wires with Resource.from[App](wire1, wire2, ...)
3. Allocate within a scope: scope.allocate(resource)
4. Use scoped values via $ accessor
5. Cleanup automatic when scope exits
```

**Example for HTTP Model:**
```
Request ──> Method (HTTP verb: GET, POST, etc.)
         ├─> URL ──> Scheme (HTTP, HTTPS, WS, WSS)
         │       ├─> Path (URL path segments)
         │       └─> QueryParams (parameters)
         ├─> Headers (collection of typed headers)
         └─> Body (content + ContentType)

Response ──> Status (HTTP code: 200, 404, etc.)
          ├─> Headers (same as Request)
          └─> Body (response content)
```

#### 7. Common Patterns

Named architectural patterns specific to the module:
- Decision trees for choosing between types/variants (e.g., "use Shared vs Unique?")
- Typical use cases organized by scenario
- Examples showing realistic multi-type composition (not just single-type snippets)

**Example for Resource Management:**
- Shared Singletons (database connections)
- Per-Request Instances (session state)
- Manual Construction (custom initialization)
- Resource Composition (chaining dependencies)

#### 8. Integration Points

How types relate architecturally and integrate with other ZIO Blocks modules:
- Which types use which other types internally
- How the module integrates with other modules (e.g., Resource ↔ Schema)
- Cross-references to related docs

**Example:**
```
- Wire uses Resource to manage lifecycles
- Resource uses Scope for finalization
- Headers are used by Request and Response
- URL parsing uses Path and QueryParams
```

---

## Step 4: Write Type-Level Documentation (Flat Structure)

**For flat (.md) files:** Write type sections inline using `##` headings.

**Structure for each type:**
1. **Opening definition (no heading for first type):** Brief definition, type signature, key properties
2. **Subsections by category:**
   - **Predefined Instances** (if applicable): List variants, constants
   - **Parsing/Creating** (if applicable): How to construct or parse values
   - **Key Operations**: 2-3 main methods per functionality group
   - **Rendering** (if applicable): How to convert to string/wire format

**Coverage:** Use `docs-data-type-ref` structure as reference, but lighter:
- Document every public method, but group concisely
- Show 1 example per operation group, not exhaustive edge cases
- Performance notes inline where relevant (O(1), O(n), etc.)
- Link to module-level integration section for composition examples

**Example (http-model.md):**
```markdown
## Method

`Method` represents standard HTTP methods as case objects.

### Predefined Methods
GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, CONNECT with examples.

### Parsing
fromString("GET") returns Some(Method.GET).

### Rendering
Method.GET.name returns "GET".

## Status

`Status` is an opaque type alias for Int, providing zero-allocation status codes.

### Predefined Status Codes
1xx Informational, 2xx Success, 3xx Redirection, 4xx Client Errors, 5xx Server Errors with examples.
```

---

## Step 5: Write Type-Level Documentation (Hierarchical Structure)

**For hierarchical structures:** Create individual type pages in the module subdirectory.

**File location:** `docs/reference/<module-name>/<type-name-kebab-case>.md`

**Frontmatter:**
```yaml
---
id: <type-name-kebab-case>
title: "<TypeName>"
---
```

**Structure:** Follow `docs-data-type-ref` COMPLETELY, with one adjustment:

**Recontextualization rule:** In each section, note how the type relates to other types in the module:
- In Motivation, mention if this type is core or supporting
- In Construction, note if using with other module types
- In Core Operations, show composition with other types if relevant
- In Integration, highlight module-level relationships (not just external modules)

**Special handling:**
- **Running the Examples:** Can be at module level (one section covering all types) or omitted
  - If module has companion examples covering all types together, put section in module index
  - If each type has standalone examples, put section on individual type pages
  - If no examples, omit entirely
- **Comparison sections:** Can stay per-type (vs other languages, vs related types) or move to module index if comparing types within the module

---

## Step 5.5: Running the Examples (Optional)

**Only include this section if the module has companion example files.** If no dedicated example files exist, omit this section entirely and rely on inline code examples throughout the documentation.

### When to Add This Section

**Before proceeding with type documentation, ask the user:**

> Would you like to include standalone, runnable examples for this module? If yes, should we create them in a `<module>-examples` module before writing the documentation, or do you want to rely on inline code examples within the type sections?

**Decision tree based on user response:**

**Option A: Create standalone examples first (recommended for substantial modules)**
1. User confirms they want standalone examples
2. Create a new `<module>-examples` module in the project if it doesn't exist
3. Write one `App` object per realistic use case (typically 3-5 examples per module)
4. Each example should demonstrate **multi-type composition** — how different types from the module work together to solve a real problem
5. Each example should be self-contained and runnable with `sbt <module>-examples/runMain <package>.<ObjectName>`
6. After examples are created and tested, write the "Running the Examples" documentation section that embeds and describes these examples

**Option B: Use inline examples only (for smaller modules or rapid documentation)**
1. User confirms they prefer inline examples only
2. Skip the "Running the Examples" section entirely
3. Embed code examples throughout the type documentation sections to demonstrate multi-type composition
4. These inline examples should still show realistic patterns and how types work together

**Option C: Examples already exist**
1. User confirms examples already exist in `<module>-examples`
2. Review them to ensure each demonstrates multi-type composition and a realistic pattern
3. Write the "Running the Examples" section describing each example

### Structure for Running the Examples

Place this section at the end of the **module index** (for hierarchical) or at the end of the **flat file** (for flat structure), after all type documentation.

Use this template:

```markdown
## Running the Examples

All code from this guide is available as runnable examples in the `<module>-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### <Example Title>

<Short description of what this App demonstrates and the use case it covers. Explain which types from the module work together in this example.>

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("<module>-examples/src/main/scala/<package>/<ObjectName>.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/<module>-examples/src/main/scala/<package>/<ObjectName>.scala))

```bash
sbt "<module>-examples/runMain <package>.<ObjectName>"
```

### <Next Example Title>

<Short description highlighting which types compose in this example.>

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("<module>-examples/src/main/scala/<package>/<ObjectName2>.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/<module>-examples/src/main/scala/<package>/<ObjectName2>.scala))

```bash
sbt "<module>-examples/runMain <package>.<ObjectName2>"
```
```

### Rules for This Section

**Scope:**
- List **every `App` object** in the examples module, one entry per object
- Each example should demonstrate **multi-type composition** (how types from the module work together), not isolated single-type usage
- Each example should solve a **realistic problem** or demonstrate a **practical pattern**

**Formatting:**
- For each entry: use a `###` heading (simple, concise title like "Basic HTTP Request", "Error Handling"), followed by a short descriptive paragraph
- The paragraph explains what the example demonstrates and which types/patterns it covers
- Embed full source with `SourceFile.print` (keeps docs and examples in sync automatically)
- Include source link and run command
- Keep the numbered steps (clone, run individually) in that order; do not add or remove steps

**Embedding Example Files with `SourceFile`:**

Use `SourceFile.print` to embed full source from `<module>-examples/` for each example. This reads the file at mdoc compile time and emits a fenced code block with the file path shown as the title.

**Pattern:**
```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("<module>-examples/src/main/scala/<package>/<ExampleFile>.scala")
```

**Important:** Import as `import docs.SourceFile` and call `SourceFile.print(...)` — do NOT use `import docs.SourceFile._` with bare `print(...)` because `print` conflicts with `Predef.print` inside mdoc sessions.

**Optional parameters for `SourceFile.print`:**
- `lines = Seq((from, to))` — include only specific line ranges (1-indexed)
- `showLineNumbers = true` — render with line numbers
- `showTitle = false` — suppress the file path title

---

## Step 6: Integration

Use the **`docs-integrate`** skill for the full checklist:
1. Update `sidebars.js` with category entry (hierarchical) or single entry (flat)
2. Update `docs/index.md` with module link and brief description
3. Add cross-references from related docs
4. Verify mdoc compilation (zero [error] lines)
5. Verify all relative links work

### sidebars.js Updates

**Flat structure:**
```javascript
{
  type: "doc",
  id: "reference/http-model"
}
```

**Hierarchical structure:**
```javascript
{
  type: "category",
  label: "HTTP Model",
  link: { type: "doc", id: "reference/http-model/index" },
  items: [
    "reference/http-model/request",
    "reference/http-model/response",
    "reference/http-model/url",
    "reference/http-model/headers",
    // ... more types
  ]
}
```

### docs/index.md Update

Add line under "Reference Documentation" section:

```markdown
- [HTTP Model](./reference/http-model.md) — Pure, zero-dependency HTTP data model for requests, responses, and primitives.
```

---

## Step 7: Format & Verify

### Scala Code Formatting
```bash
sbt scalafmtAll
```

### Lint Check
```bash
sbt check
```

### mdoc Verification

**Single flat file:**
```bash
sbt "docs/mdoc --in docs/reference/http-model.md"
```

**Hierarchical directory:**
```bash
sbt "docs/mdoc --in docs/reference/http-model/"
```

**Success criterion:** Zero `[error]` lines in output.

### Link Verification

Check all relative links work:
- From module index to type pages (hierarchical)
- From type pages back to module index
- From "How They Work Together" examples to relevant type sections
- From sidebar entries to correct files

---

## Step 8: Test & Iterate

After initial draft:
1. **Test:** Can a new reader understand how to use the module by reading the module index first?
2. **Test:** Can a reader find comprehensive API coverage per type?
3. **Test:** Do "How They Work Together" examples show realistic composition?
4. **Iterate:** Fix any gaps, clarify relationships, enhance examples if needed

---

## Decision Tree: docs-module-ref vs docs-data-type-ref

Use **`docs-module-ref`** when:
- Documenting a **module with multiple related types** designed to work together
- Need to show **module narrative, relationships, and composition patterns**
- Want readers to understand **the "why" and "how together"**, not just isolated APIs
- Examples: HTTP Model, Resource Management, Schema Evolution

Use **`docs-data-type-ref`** when:
- Documenting a **single, standalone type**
- Type doesn't require understanding other types to be useful
- Want **exhaustive, encyclopedic reference** for one type in isolation
- Examples: Chunk, TypeId, DynamicValue (when documented standalone)

---

## Writing Rules

### Prose (Reference `docs-writing-style`)
- Qualify methods: `Type#method` or `Type.method` (never bare `method`)
- Use "we" when instructing, present tense when describing
- List advantages with bullet points (avoid prose lists)
- No filler sentences or passive voice
- Use inline code for types and operations: `` `Type`, `operation` ``
- Relative links: `[Module](../module/index.md)`, `[Type](./type.md)`

### Code Blocks (Reference `docs-mdoc-conventions`)
- Plain `` ```scala `` for structural type definitions (not compiled)
- `mdoc:compile-only` for self-contained signature examples
- `mdoc:silent` for setup, `mdoc` for output (Setup + Evaluated Output pattern)
- `mdoc:silent:reset` to clear scope between unrelated sections
- Every code block preceded by prose sentence ending with `:`

### Examples in Modules
- Show **cross-type composition** when possible (Type1 + Type2 together)
- Keep examples **realistic** (domain types: Person, Order, DatabaseConnection)
- Keep examples **concise** but complete (self-contained, all imports present)
- Link examples to module-level patterns section for context

### Headings
- `##` for major sections (Motivation, Installation, How They Work Together)
- `###` for subsections (Predefined Instances, Core Operations groups)
- Never place `###` immediately after `##` without intervening prose
- No standalone subheader — always precede with explanatory text

---

## Tools & Skills

### Required Skills
- **`docs-research`** (Phase 1): Find types, tests, examples, usage patterns
- **`docs-data-type-ref`** (reference): Use structure for type-level pages
- **`docs-integrate`** (Phase 6): Integration checklist for sidebars, docs/index.md, links
- **`docs-writing-style`** (reference): Prose conventions
- **`docs-mdoc-conventions`** (reference): Code block modifiers

### Forbidden Skills
- Do NOT use `docs-data-type-ref` to document individual types — instead, apply its structure directly to this skill's guidance
- Do NOT use `docs-how-to-guide` (task-oriented, not reference)
- Do NOT use `docs-tutorial` (learning path, not reference)

---

## Checklist: Before Publishing

- [ ] **Research complete:** All types identified, relationships mapped, patterns understood
- [ ] **Structure chosen:** Flat or hierarchical, with clear rationale
- [ ] **Module index written:** Opening Definition, Motivation, Installation, Overview (if hierarchical), **How They Work Together**, Common Patterns, Integration Points
- [ ] **Type pages written:** Comprehensive coverage following `docs-data-type-ref` structure, contextualized to module
- [ ] **Examples:** Multi-type composition shown, all examples compile (mdoc zero errors)
- [ ] **Prose:** All method references qualified, relative links work, follows `docs-writing-style`
- [ ] **Code blocks:** All follow `docs-mdoc-conventions` (modifiers correct, Setup + Evaluated Output pattern)
- [ ] **Links:** Tested all relative links (module ↔ types, sidebar entries, index.md)
- [ ] **Lint:** `sbt scalafmtAll` and `sbt check` pass
- [ ] **mdoc:** `sbt "docs/mdoc --in ..."` zero [error] lines
- [ ] **Integration:** `docs-integrate` checklist complete (sidebars.js, index.md, cross-references)
- [ ] **Quality:** Reader can understand module composition and relationships from index alone
