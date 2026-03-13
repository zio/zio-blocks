---
name: docs-verify-compliance
description: >
  Fix compliance issues in any documentation file against writing style and mdoc conventions.
  Runs /docs-check-compliance twice to audit and fix all violations.
argument-hint: "[docs-file.md]"
allowed-tools: Bash
---

# Verify Documentation Compliance

## Arguments

1. **docs-file** — Path to documentation file (e.g., `docs/reference/data-type.md`)

## Overview

This skill ensures a documentation file is compliant with ZIO Blocks documentation standards by
checking against both writing style and mdoc conventions rules.

## Workflow

### Step 1: Check Writing Style Compliance

Run the `/docs-check-compliance` skill to audit the document against writing style rules:

```bash
/docs-check-compliance $ARGUMENTS[docs-file] docs-writing-style
```

This checks for:
- Intro sentences ending with colons before code blocks
- Method references qualified with type names
- No lone subheaders
- Prose before all subsections

Fix all violations identified. Commit each fix separately.

### Step 2: Check mdoc Conventions Compliance

Run the `/docs-check-compliance` skill to audit the document against mdoc conventions:

```bash
/docs-check-compliance $ARGUMENTS[docs-file] docs-mdoc-conventions
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
sbt "docs/mdoc --in $ARGUMENTS[docs-file]"
```

Ensure zero compilation errors before considering the file complete.

---

## Success Criteria

✅ All writing style rules pass (zero violations)
✅ All mdoc conventions rules pass (zero violations)
✅ mdoc compilation succeeds (0 errors)
