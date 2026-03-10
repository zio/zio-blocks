---
name: docs-organize-types
description: Organize related ZIO Blocks data types into logical categories within sidebars.js. Use this skill to group data types by functionality (e.g., "Collections", "Type System", "Resource Management", "DI & Configuration"). Supports manual categorization (specify types and category) or automatic analysis (scan docs/reference/ and suggest intelligent groupings). The skill updates sidebars.js while preserving existing structure, maintains alphabetical order, and verifies syntax validity.
allowed-tools: Glob, Grep, Read, Edit, Bash
---

# Organize Data Types in Sidebars

Improve documentation structure by organizing related ZIO Blocks data types into meaningful categories.

## Two Modes of Operation

### Mode 1: Manual Categorization

When you know exactly which types should go into a category.

**Invocation:**
```
docs-organize-types [type1] [type2] [type3] --category "[Category Name]"
```

**Example:**
```
docs-organize-types chunk list vector --category "Collections"
```

This moves (or creates links for) `chunk`, `list`, and `vector` into a "Collections" category.

### Mode 2: Automatic Categorization

When you want the skill to analyze all data types and suggest intelligent groupings.

**Invocation:**
```
docs-organize-types --auto
```

The skill will:
1. **Scan** `docs/reference/` for all data type documentation files
2. **Extract** type signatures, descriptions, and relationships
3. **Analyze** integration patterns (which types depend on others)
4. **Group** types by functional area:
   - **Collections**: Chunk, List, Vector, etc.
   - **Type System**: TypeId, Schema, DynamicValue, etc.
   - **Resource Management**: Resource, Scope, Wire, etc.
   - **Context & DI**: Context, Wire, etc.
   - **Error Handling**: SchemaError, Validation, etc.
   - **Utilities**: MediaType, Syntax, Docs, etc.
5. **Suggest** category assignments with confidence levels
6. **Preview** the new sidebars.js structure

## Workflow: Manual Mode

### Step 1: Validate Input

- Verify each type has a corresponding `.md` file in `docs/reference/`
- Confirm the category name is reasonable (avoid duplicates with existing categories)
- If any type is missing, report and stop

### Step 2: Check Existing Structure

- Read `docs/sidebars.js`
- Identify the Reference section
- Check if the category already exists; if so, note its current contents

### Step 3: Update sidebars.js

If the category doesn't exist, create it:
```javascript
{
  type: "category",
  label: "[Category Name]",
  items: [
    "reference/[type1]",
    "reference/[type2]",
    "reference/[type3]"
  ]
}
```

If it does exist, append new types in alphabetical order.

Maintain alphabetical order of categories within the Reference section.

### Step 4: Verify Syntax

Parse the updated `sidebars.js` with Node.js to ensure valid JavaScript syntax:
```bash
node -c docs/sidebars.js
```

If there are errors, report and revert.

### Step 5: Report Changes

Show:
- **Added Category**: Yes/No (new category created)
- **Types Added**: list of types moved into the category
- **Verification**: ✅ Syntax valid | ❌ Syntax error (reverted)
- **Preview**: before/after snippet of the Reference section

---

## Workflow: Automatic Mode

### Step 1: Scan Documentation

Use `Glob` to find all `.md` files in `docs/reference/`:
```bash
glob("docs/reference/*.md")
```

Extract the `id` from each file's frontmatter (line 2, `id: <name>`).

### Step 2: Analyze Type Relationships

For each type file:

**Read** the file to extract:
- **Title** (frontmatter `title:`)
- **Definition** (opening 1-3 sentences)
- **Key features** (bullet points, if present)
- **Mentions of other types** (grep for references: `[TypeName](./type-name.md)`)

Build a **relationship graph**: if Type A mentions Type B, record that edge.

### Step 3: Propose Categories

Based on type names, descriptions, and relationships, assign each type to a category:

**Collection Types** → Chunk, List, Vector, NonEmptyList, etc.
- Signal: type name contains "chunk" | "list" | "vector" | "sequence" | "collection"
- Context: used for organizing/storing data

**Type System & Schemas** → Schema, TypeId, DynamicValue, SchemaError, etc.
- Signal: type name contains "schema" | "type" | "dynamic" | "error" | "validation"
- Context: used for type representation, validation, transformation

**Resource Management & DI** → Resource, Scope, Wire, Finalizer, etc.
- Signal: type name contains "resource" | "scope" | "wire" | "finalizer" | "context"
- Context: used for lifecycle management, dependency injection

**Error & Validation** → SchemaError, Validation, etc.
- Signal: type name or content mentions error handling, constraints
- Context: used for error representation and validation

**Utilities & Formats** → MediaType, Syntax, Docs, JSON, XML, etc.
- Signal: type name is format/utility name (not a core abstraction)
- Context: add-on functionality, specific formats

For each proposed grouping, compute a **confidence level**:
- **High** (90%+): Clear semantic signal (name + description alignment)
- **Medium** (70-89%): Some signal (description or relationships align)
- **Low** (<70%): Weak signal (consider alternatives)

### Step 4: Preview Proposed Structure

Show the user:
- **Proposed Categories** with types grouped and confidence levels
- **Before/After** snippet of sidebars.js
- **Unassigned Types** (if any) — types that don't fit well in any category

### Step 5: User Confirmation

Wait for user input:
- **Accept All**: Apply all proposed categories
- **Selective**: Accept specific categories only
- **Reject**: Keep current flat structure

### Step 6: Update sidebars.js

Once approved, update sidebars.js with the new structure, maintaining:
- Alphabetical order of categories
- Alphabetical order of types within each category
- Existing non-categorized types (if kept)

### Step 7: Verify & Report

Same as manual mode: verify syntax, report changes.

---

## Output Format

**Summary Report** (displayed to user):

```
✅ Categorization Complete

Added Categories: [N]
- [Category 1]: [type1], [type2], [type3]
- [Category 2]: [type4], [type5]

Modified Categories: [N]
- [Category]: added [type6]

Verification: ✅ Syntax valid

Preview:
  Reference
    ├─ [Category 1]
    │  ├─ chunk
    │  ├─ list
    │  └─ vector
    ├─ [Category 2]
    │  ├─ schema
    │  └─ typeid
    └─ [Uncategorized] (if any)
       └─ mediatype
```

---

## Implementation Notes

- **Alphabetical order** is maintained within each category and at the category level
- **Type paths** use the format `"reference/[type-id]"` (with `reference/` prefix)
- **Existing categories** are preserved if they already exist in sidebars.js
- **Syntax validation** is mandatory — invalid changes are reverted
- **No breaking changes** — existing structure is preserved; only new categories are added

