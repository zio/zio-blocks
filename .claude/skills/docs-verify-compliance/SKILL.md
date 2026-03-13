---
name: docs-verify-compliance
description: >
  Fix compliance issues in any documentation file against writing style and mdoc conventions.
argument-hint: "[docs-file.md]"
allowed-tools: Bash
---

# Verify Documentation Compliance

## Workflow

1. Run `/docs-check-compliance` on the document against `docs-writing-style` rules
2. Run `/docs-check-compliance` on the document against `docs-mdoc-conventions` rules
3. Run `/docs-check-compliance` on the document against `docs-writing-style` rules again
4. Run `/docs-check-compliance` on the document against `docs-mdoc-conventions` rules again
5. Run mdoc compilation to verify zero errors

Fix all violations identified, committing each separately. The second round catches cascading violations.
