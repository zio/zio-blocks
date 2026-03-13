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

**You MUST use the `Skill` tool to invoke the rule skill.** Do not substitute a different skill. Invoke the rule skill now:

```
Skill: $ARGUMENTS[rule-skill]
```

Read all rules it defines. 

### Step 2: Read Doc File

Read the full doc file to understand its current state.

```
Read: $ARGUMENTS[docs-file]
```

### Step 3: Check and Fix Each Rule

Create a checklist of all rules defined by the rule skill. **Process each rule sequentially, ensuring zero violations before moving to the next.**

For each rule:

1. **Identify violations** — Use adversarial verification: assume the document fully complies with THIS RULE, then prove yourself wrong. Cite exact line numbers and quote problematic text. Explain why it violates THIS RULE. If you cannot find evidence, the rule has zero violations.

2. **Fix violations** — Apply minimal edits (add, remove, edit, or restructure as needed).

3. **Commit** — Run `git add` and `git commit -m "docs(<docs-file-stem>): fix <rule-name>"`

**Repeat steps 1–3 for each rule until zero violations remain, then proceed to the next rule.**

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
