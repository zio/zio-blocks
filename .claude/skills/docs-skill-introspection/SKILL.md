---
name: docs-skill-introspection
description: >
  Analyze a recent docs-* skill execution against its documented workflow. Identifies
  deviations, unclear instructions, missing edge cases, and better approaches. Applies
  targeted improvements to the skill file. Run after any docs-* skill to close the
  feedback loop.
argument-hint: "<skill-name>"
allowed-tools: Read, Glob, Grep, Edit, Bash
---

# Docs Skill Introspection

## Argument

**skill-name** — the docs-* skill that was just executed (e.g., `docs-data-type-ref`,
`docs-how-to-guide`, `docs-tutorial`).

## Workflow

### Step 1: Locate and Read the Skill File

Find the skill file. Check project-level first, then global:

```bash
# Project-level (most doc skills live here)
ls /home/milad/sources/scala/zio-blocks-modern/.claude/skills/<skill-name>/SKILL.md

# Global fallback
ls /home/milad/.claude/skills/<skill-name>/SKILL.md
```

Read the full content of the SKILL.md found. Treat each numbered step in the
**Workflow** section as the ground truth for what *should* happen.

### Step 2: Reconstruct the Execution

Review the current conversation history — specifically the most recent execution of
`<skill-name>`. Trace, in order:

- Which numbered workflow steps were followed
- Which tools were called (Read, Grep, Glob, Bash, Edit, Write, Skill, etc.) and in what sequence
- Any steps skipped, reordered, or substituted
- Any tool calls made that are not described in the skill
- Any mistakes encountered and how they were resolved

If this is a cross-session introspection (the skill was run in a previous conversation),
read the most recent JSONL file:

```bash
ls -t /home/milad/.claude/projects/-home-milad-sources-scala-zio-blocks-modern/*.jsonl | head -1
```

Then grep for the skill invocation:

```bash
grep -n '"<skill-name>"' <jsonl-path> | head -5
```

Read the surrounding lines to extract the execution trace.

### Step 3: Classify Every Deviation

For each discrepancy between documented and actual behavior, classify it as one of:

| Category | Definition | Skill Fix |
|---|---|---|
| **Gap** | Something needed that the skill didn't mention | Add the missing step or instruction |
| **Ambiguity** | Step was unclear, leading to guessing or backtracking | Rewrite for precision |
| **Wrong instruction** | Skill said X but X failed or produced a worse result | Correct or replace the instruction |
| **Better approach** | A different tool or sequence produced a clearly better outcome | Update skill to use the better approach |

Deviations that are purely contextual (e.g., a different filename was used in an example)
are **not** worth updating — skip them.

### Step 4: Apply Improvements

Edit the skill file with **minimal, targeted changes**:

- Add missing steps at the correct position
- Rewrite ambiguous instructions in-place (preserve structure)
- Correct wrong instructions
- Update examples only if they demonstrate the better approach concretely

Rules:
- Do **not** restructure sections that weren't broken
- Do **not** add steps for edge cases that are unlikely to recur
- Do **not** change tone or rewrite working instructions for style
- Prefer one precise sentence over a paragraph

### Step 5: Commit

```bash
git add .claude/skills/<skill-name>/SKILL.md
git commit -m "skill(<skill-name>): introspection improvements from <task-slug>"
```

Use a brief `<task-slug>` that identifies what the skill was run for (e.g.,
`scope-resource-ref`, `chunk-tutorial`).

### Step 6: Report

Output a concise summary:

- **Deviations found**: count per category (Gap / Ambiguity / Wrong / Better)
- **Changes applied**: list each change as a one-liner (e.g., "Added missing mdoc `--in` flag reminder in Step 4")
- **Changes skipped**: any classification you decided not to act on and why

---

## Implementation Notes

### Why inline, not a subagent

Skills run inline (the `Skill` tool loads content into Claude's prompt). This means Claude
has the entire conversation history available during introspection — no JSONL parsing is
needed for the common case (same-session, just ran the skill). The JSONL path is included
as a fallback for cross-session use.

### Deviation categories (design rationale)

Four categories — Gap, Ambiguity, Wrong instruction, Better approach — map directly to
the four types of feedback that improve a skill. "Contextual" deviations (different filenames,
different output) are explicitly excluded to keep the skill file stable and prevent churn.

### Commit scope

One commit per introspection run (not one per deviation). The introspection output is
cohesive — it describes a single execution's findings — so batching is appropriate here,
unlike the documentation compliance skills where one commit per rule violation makes sense.
