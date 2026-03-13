---
name: docs-check-compliance
description: >
  Audit a documentation file against a rule skill. Checks each rule, fixes violations
  with separate commits, then compiles with mdoc. Reusable across any rule skill and doc file.
argument-hint: "[docs-file.md] [rule-skill-name]"
allowed-tools: Skill, Read, Grep, Edit, Bash
---

# Check Documentation Compliance

## Arguments

1. **docs-file** — Path to documentation file (e.g., `docs/reference/xml.md`)
2. **rule-skill** — Rule skill name (e.g., `docs-writing-style`, `docs-mdoc-conventions`)

## Workflow

### Step 1: Load Rule Skill

**You MUST use the `Skill` tool to invoke the rule skill.** Do not substitute a different skill.
Do not rely on memory or training knowledge about what the skill contains — load it fresh.

Invoke the rule skill now:

```
Skill: $ARGUMENTS[rule-skill]
```

Read all rules it defines. For enumerated rule skills (like `docs-writing-style`), note the rule numbers and organize your verification around each specific rule. These are the only rules you will enforce in Step 3.

### Step 2: Read Doc File

Read the full doc file to understand its current state.

```
Read: $ARGUMENTS[docs-file]
```

### Step 3: Check and Fix Each Rule

**Process each rule individually. Do not move to the next rule until the current rule has zero violations.**

For each rule from the skill:

1. **Identify violations for THIS RULE** — Assume the document is fully compliant with THIS SPECIFIC RULE. Prove yourself wrong by finding evidence of violations against ONLY THIS RULE. Cite exact line numbers, quote problematic text, and explain why it violates THIS RULE. If you cannot prove yourself wrong, THIS RULE has zero violations.

2. **Fix violation** — Apply the minimal fix (edit, add, remove, or restructure)

3. **Commit separately** — `git add` and `git commit` with focused message: `docs(<docs-file-stem>): fix <rule-name>`

**Complete steps 1-3 for THIS RULE until zero violations remain. Then move to the NEXT RULE and repeat.**

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
