# Documentation Scripts for ZIO Blocks

Utility scripts for documentation maintenance and quality assurance.

## mdoc Code Block Scanner & Fixer Tools

### Overview

Three scripts for managing mdoc modifiers in Scala code blocks:

1. **find-unmodified-mdoc-blocks.sh** — Scan and report
2. **apply-mdoc-modifiers.sh** — Batch apply modifiers  
3. **MDOC_SCANNER_GUIDE.md** — Decision guide

### Problem They Solve

Code blocks without mdoc modifiers are compiled and executed by mdoc during docs generation. This causes:

- ✅ **Good for**: Real examples that demonstrate working code
- ❌ **Bad for**: Illustrative pseudocode, code with side effects, incomplete examples

These scripts help identify and fix blocks that need modifiers.

---

## Script 1: find-unmodified-mdoc-blocks.sh

**Purpose**: Identify all Scala code blocks lacking mdoc directives

**Usage**:
```bash
# Basic scan
./scripts/find-unmodified-mdoc-blocks.sh docs/reference

# Show context around each block
./scripts/find-unmodified-mdoc-blocks.sh docs/reference --show-context

# Scan specific file
./scripts/find-unmodified-mdoc-blocks.sh docs/reference/http-model-schema.md
```

**Output**:
```
🔍 Scanning for Scala code blocks without mdoc modifiers...

📄 docs/reference/http-model-schema.md
  Line 14: ```scala (NO MODIFIER)
  Line 29: ```scala (NO MODIFIER)
  ...

Summary:
Total unmodified blocks: 5 files
```

**What It Does**:
- Finds all `\`\`\`scala$` patterns (Scala blocks without modifiers)
- Shows file and line number for each
- Optionally shows surrounding code context
- Lists common modifiers and decision guide

---

## Script 2: apply-mdoc-modifiers.sh

**Purpose**: Batch-apply mdoc modifiers to unmodified blocks

**Usage**:
```bash
# Dry run (show what would change)
./scripts/apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only --dry-run

# Apply modifier to all blocks in file
./scripts/apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only

# Apply to specific line range
./scripts/apply-mdoc-modifiers.sh docs/reference/http-model.md silent --start-line 100 --end-line 200
```

**Supported Modifiers**:
- `compile-only` — Compile but don't execute
- `silent` — Compile and run, hide output
- `reset` — Clear scope (between unrelated examples)
- `fail` — Code should fail to compile (anti-pattern)
- `silent:nest:1` — Silent execution, nested in function

**Example**:

Before:
```markdown
\`\`\`scala
val userId = params.query[UUID]("userId")
\`\`\`
```

After applying `compile-only`:
```markdown
\`\`\`scala mdoc:compile-only
val userId = params.query[UUID]("userId")
\`\`\`
```

---

## Script 3: MDOC_SCANNER_GUIDE.md

**Purpose**: Comprehensive guide for deciding which modifier to use

**Key Decision Tree**:

1. **Should code execute?**
   - YES → Use no modifier or `:silent`
   - NO → Go to next question

2. **Why can't it compile?**
   - References external types → Use `:compile-only`
   - Needs setup first → Use `:silent` before, `:reset` between sections
   - Should intentionally fail → Use `:fail`
   - Complex initialization → Use `:silent:nest:1` for setup

**Example Scenarios**:

| Scenario | Modifier | Reason |
|----------|----------|--------|
| Working code example | (none) | Demonstrates real behavior |
| API usage illustration | `:compile-only` | Shows pattern, references external context |
| Setup/initialization | `:silent` | Hidden boilerplate |
| Anti-pattern example | `:fail` | Demonstrates what NOT to do |
| Multiple independent blocks | `:reset` | Clears state between examples |

---

## Workflow for docs-mdoc-conventions Skill

### Phase 1: Scan
```bash
./scripts/find-unmodified-mdoc-blocks.sh docs/reference/http-model-schema.md --show-context
```

### Phase 2: Analyze
For each block, read the guide to determine the right modifier:
- Is it illustrative or executable?
- Does it reference external types?
- Would it have side effects if executed?

### Phase 3: Apply
```bash
# Dry run first
./scripts/apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only --dry-run

# Review changes
git diff docs/reference/http-model-schema.md

# Apply
./scripts/apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only
```

### Phase 4: Verify
```bash
# Test docs compilation
sbt "docs/mdoc --in docs/reference/http-model-schema.md"

# Should output: Compiled in X.XXs (0 errors)
```

---

## Current Status: http-model-schema.md

**Analysis**:
- Total unmodified blocks: 27
- Block type: Illustrative (show API usage patterns)
- References: External types (QueryParams, Headers, Schema, Request, Response)
- Recommended modifier: `mdoc:compile-only`
- Rationale: Code shows realistic patterns but isn't self-contained; needs external context

**To Fix**:
```bash
./scripts/apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only --dry-run
./scripts/apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only
sbt "docs/mdoc --in docs/reference/http-model-schema.md"
```

---

## Integration with docs-mdoc-conventions Skill

The skill should:

1. **Scan phase**: Use `find-unmodified-mdoc-blocks.sh` to inventory
2. **Classify phase**: Apply decision tree from GUIDE to each block
3. **Suggest phase**: Show user what modifier should be added + why
4. **Apply phase**: Use `apply-mdoc-modifiers.sh` with user confirmation
5. **Verify phase**: Run mdoc compilation to confirm fixes work

---

## Reference: All mdoc Modifiers

| Modifier | Behavior | Use Case |
|----------|----------|----------|
| (none) | Compile + Execute + Show output | Real working examples |
| `:compile-only` | Compile only, no execution | Illustrative pseudocode |
| `:silent` | Compile + Execute, hide output | Setup code, verbose output |
| `:reset` | Clear previous scope | New section, unrelated examples |
| `:fail` | Should fail to compile | Anti-patterns, wrong approaches |
| `:nest:1` | Nest in outer scope | Helper functions |
| `:nest:1:reset` | Combination | Complex setup |

---

## Common Issues & Solutions

### Issue 1: Block References Undefined Types

```scala
// ❌ Error: value params is not defined
val userId = params.query[UUID]("userId")
```

**Solution**: Add `:compile-only`
```scala mdoc:compile-only
val userId = params.query[UUID]("userId")
```

### Issue 2: Unintended Side Effects

```scala
// ❌ Creates file during doc generation
new File("/tmp/test.txt").createNewFile()
```

**Solution**: Add `:silent` or `:silent:nest:1`
```scala mdoc:silent
new File("/tmp/test.txt").createNewFile()
```

### Issue 3: Multiple Examples with Shared State

```scala mdoc:silent
val data = List(1, 2, 3)
```

```scala
// Uses 'data' from previous block
val sum = data.sum
println(sum)  // Output: 6
```

```scala mdoc:reset
// New section, doesn't see 'data'
val x = 42
```

---

## Testing

To verify scripts work on your codebase:

```bash
# Check http-model-schema.md specifically
./scripts/find-unmodified-mdoc-blocks.sh docs/reference/http-model-schema.md

# Count total blocks across all docs
find docs/reference -name "*.md" -exec grep -l "\`\`\`scala$" {} \; | wc -l

# Test applying fix to a file
./scripts/apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only --dry-run
```

---

## For Documentation Contributors

When writing new code examples:

1. **Self-contained examples** → Plain `` ```scala ``
2. **Illustrative patterns** → `` ```scala mdoc:compile-only ``
3. **Hidden setup** → `` ```scala mdoc:silent ``
4. **Between sections** → Use `` ```scala mdoc:reset ``

Ask yourself: **"Would someone copy-paste this code and run it?"**
- YES → Plain block
- NO → Add appropriate modifier
