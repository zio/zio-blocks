---
name: docs-verify-compliance
description: >
  Fix compliance issues in a documentation file against writing style and mdoc conventions. 
argument-hint: "[docs-file.md]"
allowed-tools: Bash
---

# Verify Documentation Compliance

## Workflow

Run these commands in order:

```bash
/docs-check-compliance $ARGUMENTS docs-writing-style
/docs-check-compliance $ARGUMENTS docs-mdoc-conventions
sbt "docs/mdoc --in $ARGUMENTS"
```

Fix all violations identified by `/docs-check-compliance`, committing each separately. Ensure the final mdoc compilation succeeds with zero errors.
