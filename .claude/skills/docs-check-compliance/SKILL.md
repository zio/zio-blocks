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

**You MUST use the `Skill` tool to invoke the rule skill.** Do not substitute a different skill. Load it now:

Use the Skill tool with the rule-skill name. For example, if the rule-skill argument is `docs-writing-style`, invoke:

```
Skill: docs-writing-style
```

Read all rules it defines. For enumerated rule skills, note the rule numbers and create a checklist. These are the only rules you will enforce.

### Step 2: Read Doc File

Read the full documentation file to understand its current state.

Use the Read tool with the docs-file path. For example, if the docs-file argument is `docs/reference/chunk.md`, invoke:

```
Read: /home/milad/sources/scala/zio-blocks-modern/docs/reference/chunk.md
```

Note: Always use absolute paths (starting from `/home/milad/sources/scala/zio-blocks-modern/`)

### Step 3: Check and Fix Each Rule

Create a checklist of all rules defined by the rule skill. **Process each rule sequentially, ensuring zero violations before moving to the next.**

For each rule:

1. **Identify violations** — Use adversarial verification: assume the document fully complies with THIS RULE, then prove yourself wrong by finding evidence of violations. Cite exact line numbers, quote problematic text, and explain why it violates THIS RULE. If you cannot find evidence, this rule has zero violations.

2. **Fix violations** — Apply minimal edits (add, remove, edit, or restructure as needed). Preserve the intent of the document.

3. **Commit immediately** — Run:
   ```bash
   git add <docs-file>
   git commit -m "docs(<docs-file-stem>): fix <rule-name>"
   ```
   Example: `git commit -m "docs(chunk): fix rule-2-sentence-clarity"`

**Repeat steps 1–3 for each rule until zero violations remain, then proceed to the next rule.**

### Step 4: Compile

Run mdoc to verify the doc compiles without errors:

```bash
sbt "docs/mdoc --in docs/reference/chunk.md"
```

(Substitute the actual docs-file path.)

If mdoc fails, identify the error, fix it, and commit:
```bash
git add <docs-file>
git commit -m "docs(<docs-file-stem>): fix mdoc error"
```

### Step 5: Report

Output a summary including:
- Total violations found and fixed per rule
- Which rules had zero violations (no changes needed)
- Final mdoc status (pass/fail)

---

## Key Principles

- **One commit per rule violation** — not one big commit
- **Commit immediately after each fix** — don't batch
- **Verify mdoc passes at the end** — this is the final proof of correctness
- **Be concise** — minimal changes, preserve intent

---

## Example Invocation

To check `docs/reference/chunk.md` against the `docs-writing-style` rule skill:

1. Load the rule skill: `Skill: docs-writing-style`
2. Read the doc file: `Read: /home/milad/sources/scala/zio-blocks-modern/docs/reference/chunk.md`
3. For each rule, find violations, fix, and commit
4. Run: `sbt "docs/mdoc --in docs/reference/chunk.md"`
5. Report results

Example commit message:
```
docs(chunk): fix rule-3-active-voice
```
