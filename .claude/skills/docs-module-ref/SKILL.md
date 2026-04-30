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

Run the `docs-reasearch` skill:

skill({ name: "docs-research" })

Key Goals:
1. Find all core and supporting types in the module
2. Identify type relationships and dependencies (which types use which types)
3. Find tests, examples, and real-world usage patterns
5. Review any existing partial documentation

**Additional mapping:** Create a mental model of data types:
- **Core types** (primary exports, main abstractions)
- **Supporting types** (helpers, variants)
- **Relationships**
- **Data flow**

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

## Step 3: Load Writing Rule Skills

1. Load Skill({ name: "docs-writing-style" }) for prose conventions
2. Load Skill({ name: "docs-mdoc-conventions" }) for code block syntax rules

---

## Step 4: Write Module-Level Documentation

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
```

Then continue with `## Introduction` or `## Motivation` heading.

#### 2. Introduction (if hierarchical) OR Motivation (if flat)

**Hierarchical:** Brief welcome section explaining the module's role and what readers will learn.

**Flat:** Why use this module over alternatives? Problem it solves, advantages, bullet points.

#### 3. Motivation / Use Case

Answer: What problem does it solve? Why use it over alternatives?
- Include advantages as bullet points or ASCII art
- Compare with standard library or other libraries if relevant

#### 4. Installation

```scala
libraryDependencies += "dev.zio" %% "<module-name>" % "@VERSION@"
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

#### 9. Running the Examples (Optional)

Use the Skill({ name : "docs-examples"}) for writing example project and documenting them on the documentation.

---

## Step 5: Write Type-Level Documentation (Flat Structure)

**For flat (.md) files:** Write type sections inline using `##` headings.

**Structure for each type:**
1. **Opening definition (no heading for first type):** Brief definition, type signature, key properties
2. **Subsections by category:**
   - **Predefined Instances** (if applicable): List variants, constants
   - **Parsing/Creating** (if applicable): How to construct or parse values
   - **Key Operations**: 2-3 main methods per functionality group
   - **Rendering** (if applicable): How to convert to string/wire format

**Coverage:** Load and call Skill(`docs-data-type-ref`) for structure as a reference, but lighter:
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

## Step 6: Write Type-Level Documentation (Hierarchical Structure)

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

## Step 7: Integration

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
  id: "reference/<module-name>"
}
```

**Hierarchical structure:**
```javascript
{
  type: "category",
  label: "HTTP Model",
  link: { type: "doc", id: "reference/<module-name>/index" },
  items: [
    "reference/<module-name>/<type-name-1>",
    "reference/<module-name>/<type-name-2>",
    "reference/<module-name>/<type-name-3>",
    // ... more types
  ]
}
```

### docs/index.md Update

Add line under "Reference Documentation" section:

```markdown
- [HTTP Model](./reference/<module-name>.md) — Pure, zero-dependency HTTP data model for requests, responses, and primitives.
```

---

## Step 8: Format & Verify

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
sbt "docs/mdoc --in docs/reference/<module-name>.md"
```

**Hierarchical directory:**
```bash
sbt "docs/mdoc --in docs/reference/<module-name>/"
```

**Success criterion:** Zero `[error]` lines in output.

### Link Verification

Check all relative links work:
- From module index to type pages (hierarchical)
- From type pages back to module index
- From "How They Work Together" examples to relevant type sections
- From sidebar entries to correct files

---

## Step 9: Test & Iterate

After initial draft:
1. **Test:** Can a new reader understand how to use the module by reading the module index first?
2. **Test:** Can a reader find comprehensive API coverage per type?
3. **Test:** Do "How They Work Together" examples show realistic composition?
4. **Iterate:** Fix any gaps, clarify relationships, enhance examples if needed

---

## Decision Tree: docs-module-ref vs docs-data-type-ref

Use skill({ name: "docs-module-ref" }) when:
- Documenting a **module with multiple related types** designed to work together
- Need to show **module narrative, relationships, and composition patterns**
- Want readers to understand **the "why" and "how together"**, not just isolated APIs
- Examples: HTTP Model, Resource Management, Schema Evolution

Use skill({ name: "docs-data-type-ref" }) when:
- Documenting a **single, standalone type**
- Type doesn't require understanding other types to be useful
- Want **exhaustive, encyclopedic reference** for one type in isolation
- Examples: Chunk, TypeId, DynamicValue (when documented standalone)

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
