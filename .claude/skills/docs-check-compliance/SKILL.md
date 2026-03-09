---
name: docs-check-compliance
description: >
  Audit a documentation file against a rule skill. Checks each rule, fixes violations
  with separate commits, then compiles with mdoc. Reusable across any rule skill and doc file.
argument-hint: "[docs-file.md] [rule-skill-name]"
allowed-tools: Read, Grep, Edit, Bash
---

# Check Documentation Compliance

## Arguments

1. **docs-file** — Path to documentation file (e.g., `docs/reference/xml.md`)
2. **rule-skill** — Rule skill name (e.g., `docs-writing-style`, `docs-mdoc-conventions`)

## Workflow

### Step 1: Load Rule Skill

Invoke the specified rule skill to load its rules into context.

```
Skill: $ARGUMENTS[rule-skill]
```

### Step 2: Read Doc File

Read the full doc file to understand its current state.

```
Read: $ARGUMENTS[docs-file]
```

### Step 3: Check and Fix Each Rule

For each rule from the skill:

1. **Identify violations** — Scan the doc file for breaches of this specific rule
2. **Fix violation** — Apply the minimal fix (edit, add, remove, or restructure)
3. **Commit separately** — `git add` and `git commit` with focused message: `docs(<docs-file-stem>): fix <section> [rule name]`

Repeat until the rule has no violations.

### Step 4: Compile

Run mdoc to verify the doc compiles:

```bash
sbt "docs/mdoc --in $ARGUMENTS[docs-file]"
```

If mdoc fails, identify the error and commit a fix.

### Step 5: Report

Output a summary: total violations found/fixed per rule, and final mdoc status.

---

## Key Principles

- **One commit per rule violation** — not one big commit
- **Commit immediately after each fix** — don't batch
- **Verify mdoc passes at the end** — this is the final proof of correctness
- **Be concise** — minimal changes, preserve intent
