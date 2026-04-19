---
name: docs-report-method-coverage
description: Use when checking if documentation covers all public members of a Scala data type.
---

# Report Method Coverage

```bash
./check-method-coverage.sh <TypeName> <doc-file.md> [members-file]
```

Compares extracted members against documentation. Reads input from file or stdin (from `/docs-data-type-list-members` skill).

Exit codes: 0=complete coverage, 1=gaps found, 2=error
