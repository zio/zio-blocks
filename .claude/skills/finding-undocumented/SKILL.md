---
name: finding-undocumented
description: Find and report undocumented areas of the ZIO Blocks project that need documentation coverage in the docs directory. Generates a TODO-style report.
argument-hint: "[optional: module-name or 'all']"
allowed-tools: Read, Glob, Grep, Bash(scan-undocumented:*), Bash(bash:*)
---

# Finding Undocumented Areas

Scan the ZIO Blocks project to identify documentation gaps — public types, modules, and packages that lack coverage in the `docs/` directory. Produce a comprehensive report saved to `docs/undocumented-report.md`.

## Scope

$ARGUMENTS

If a specific module name is given (e.g., `schema`, `chunk`, `scope`), focus the analysis and recommendations on that module. Otherwise, scan the entire project.

## Step 1: Run the Scanner Script

Run the automated scanner to produce raw coverage data:

```bash
bash .claude/skills/finding-undocumented/scan-undocumented.sh
```

Save the output to `docs/undocumented-report.md`.

## Step 2: Enrich the Report with Manual Analysis

The bash script provides a mechanical scan. Now add intelligence by doing the following:

### 2a. Review the undocumented types

For each undocumented type flagged by the script:

1. **Read the source file** to understand what the type does.
2. **Classify its documentation priority**:
   - **Critical**: Core public API types that users interact with directly (e.g., `Schema`, `Codec`, `Chunk`). These need dedicated reference pages.
   - **High**: Important supporting types that appear in public API signatures (e.g., `Validation`, `Modifier`, `DynamicValue`). These need at least a section in a related page.
   - **Medium**: Internal-but-visible types that advanced users may encounter (e.g., `Deriver`, `ReflectTransformer`). These need brief mention.
   - **Low**: Truly internal types, platform-specific implementations, or test helpers. These can be skipped.
3. **Update the priority** in the report — move types into the correct priority section.

### 2b. Check documentation depth

For types that DO have documentation pages, check whether the coverage is adequate:

1. Read the existing doc page.
2. Read the source file for the type.
3. Look for:
   - **Missing methods**: Public methods on the type or companion that are not documented.
   - **Missing examples**: Types documented with prose but no code examples.
   - **Outdated signatures**: Method signatures in docs that don't match current source.
   - **Missing cross-references**: Related types that should link to each other but don't.

Add a "Documentation Depth" section to the report with these findings.

### 2c. Identify conceptual gaps

Beyond type-level coverage, look for missing conceptual documentation:

1. **Getting started guide**: Is there a quick-start for new users?
2. **Migration guides**: Any version migration docs needed?
3. **How-to guides**: Common tasks that need step-by-step guides.
4. **Architecture overview**: High-level design docs for contributors.

Add these as a separate "Conceptual Gaps" section.

## Step 3: Write the Final Report

Write the final report to `docs/undocumented-report.md` with this structure:

```markdown
---
id: undocumented-report
title: "Documentation Coverage Report"
---

# Documentation Coverage Report

## Summary
[Coverage statistics table from script]

## Critical: Missing Reference Pages
[Types that need dedicated doc pages, with brief description of each]

## High Priority: Incomplete Coverage
[Types with pages that need expansion, or important types without pages]

## Medium Priority: Brief Mentions Needed
[Types that should be mentioned in related pages]

## Documentation Depth Issues
[Existing pages that need updates — missing methods, examples, etc.]

## Conceptual Gaps
[Missing guides, overviews, tutorials]

## Low Priority / Skip
[Internal types that don't need documentation, with brief justification]

## Suggested Actions
[Ordered TODO checklist of documentation tasks]
```

### Report Guidelines

- Use `- [ ]` checkbox syntax for actionable items so they serve as a TODO list.
- Include source file paths so a documentation writer can quickly find the code.
- For each suggested action, estimate scope: "new page", "new section", "brief mention", or "update existing".
- Group suggestions by module for easy assignment.

## Step 4: Update Index

Do NOT add the report to `docs/index.md` — it is an internal tracking document, not user-facing documentation.
