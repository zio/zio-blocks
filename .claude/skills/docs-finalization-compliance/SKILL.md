---
name: docs-finalization-compliance
description: >
  Fix compliance issues in finalization.md against writing style and mdoc conventions.
  Runs /docs-check-compliance twice to audit and fix all violations.
allowed-tools: Bash
---

# Fix finalization.md Compliance

## Overview

This skill ensures `docs/reference/resource-management-di/finalization.md` is compliant with
ZIO Blocks documentation standards.

## Workflow

### Step 1: Check Writing Style Compliance

Run the `/docs-check-compliance` skill to audit `finalization.md` against writing style rules:

```bash
/docs-check-compliance docs/reference/resource-management-di/finalization.md docs-writing-style
```

This checks for:
- Intro sentences ending with colons before code blocks
- Method references qualified with type names (e.g., `Finalization#isEmpty`)
- No lone subheaders
- Prose before all subsections

Fix all violations identified. Commit each fix separately.

### Step 2: Check mdoc Conventions Compliance

Run the `/docs-check-compliance` skill to audit `finalization.md` against mdoc conventions:

```bash
/docs-check-compliance docs/reference/resource-management-di/finalization.md docs-mdoc-conventions
```

This checks for:
- Correct mdoc modifiers (executable blocks only; definitions use plain ` ```scala `)
- No hardcoded result comments
- Proper import statements in code blocks
- Correct Setup + Evaluated Output pattern usage

Fix all violations identified. Commit each fix separately.

### Step 3: Verify

Once both compliance checks pass, run mdoc to verify the file compiles:

```bash
sbt "docs/mdoc --in docs/reference/resource-management-di/finalization.md"
```

Ensure zero compilation errors before considering the file complete.

---

## Success Criteria

✅ All writing style rules pass (zero violations)
✅ All mdoc conventions rules pass (zero violations)
✅ mdoc compilation succeeds (0 errors)
