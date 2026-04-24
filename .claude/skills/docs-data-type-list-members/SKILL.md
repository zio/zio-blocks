---
name: docs-data-type-list-members
description: Use when extracting and categorizing public members from a Scala data type for documentation completeness checks.
---

# List Data Type Members

```bash
./extract-members.scala <source-file> [<type-name>]
```

Extracts and categorizes public methods alphabetically, organized into:
- **Companion Object Members** — factory methods, utilities, and static operations
- **Public API** — instance methods on the type itself
- **Inherited Methods** — methods from parent classes/traits (when cross-file analysis is available)

Excludes private/protected members and internal helpers.
