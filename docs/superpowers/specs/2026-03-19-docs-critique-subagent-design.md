# Design: Subagent-Based Documentation Review

**Date:** 2026-03-19
**Status:** Draft
**Scope:** Add a maker-critic workflow to all documentation creation skills

## Problem

The existing documentation pipeline has layered mechanical checks (writing style, mdoc conventions, compilation gates) but lacks a content-level reviewer. No system verifies that documentation is clear, technically accurate against source code, complete in coverage, or consistent with related pages. These gaps are caught only by human review — if at all.

## Decisions

| Decision | Choice |
|----------|--------|
| Critique level | Full spectrum — content quality, technical accuracy, completeness, consistency |
| Trigger scope | All 6 creation skills automatically |
| Iteration model | Severity-gated — iterate for HIGH/MEDIUM, single pass for LOW |
| Architecture | Hybrid — inline for mechanical checks, subagent for content/accuracy |
| Output format | Structured report + commit-per-fix |
| Critic context | Doc file + source code + related docs |

## Architecture: Agent Definition + Orchestrating Skill

Two new artifacts:

1. **`.claude/agents/docs-critic.md`** — Reusable agent definition with persona, review dimensions, severity rubric, and report format. Read-only tools (`Read`, `Glob`, `Grep`).
2. **`.claude/skills/docs-critique/SKILL.md`** — Thin orchestrator that gathers context, runs mechanical checks inline, spawns the critic agent, merges findings, and drives the fix loop.

Each creation skill calls `/docs-critique <file>` as its final step.

## Agent Definition: `docs-critic`

**Persona:** Senior technical writer and Scala developer. Skeptical by default — assumes the doc has problems and looks for them.

**Tools:** `Read`, `Glob`, `Grep` only. No write access.

**Review Dimensions:**

| Dimension | What it checks |
|-----------|---------------|
| Content Quality | Clarity, narrative flow, example realism, audience fit, motivation before code |
| Technical Accuracy | API signatures match source, examples correct beyond compilation, described behavior matches implementation |
| Completeness | Required sections present, edge cases mentioned, error scenarios covered, cross-references adequate |
| Consistency | Matches terminology/tone of related docs, no contradictions with other pages |

**Severity Rubric:**

| Severity | Definition | Iteration? |
|----------|-----------|------------|
| HIGH | Factually wrong, misleading, or missing critical content. Reader would be confused or write buggy code. | Yes — must iterate until fixed |
| MEDIUM | Incomplete, unclear, or inconsistent. Reader could figure it out but shouldn't have to. | Yes — iterate once |
| LOW | Stylistic nit, minor improvement. Reader wouldn't notice. | No — single pass, fix if easy |

**Report Format:**

```
## Docs Critic Report: <filename>

### Summary
<1-2 sentence overall assessment>

### Findings

#### [HIGH/accuracy] <title>
**Location:** <section name or line range>
**Issue:** <what's wrong>
**Evidence:** <quote from source code or related doc that proves it>
**Suggested fix:** <concrete suggestion>

#### [MEDIUM/completeness] <title>
...

### Verdict
<APPROVED | ITERATE — N high, M medium issues remain>
```

## Orchestrating Skill: `docs-critique`

**Invocation:** `/docs-critique <path-to-doc-file>`

### Phase 1: Context Gathering (inline)

1. The doc file — path provided as argument
2. Source files — extract type names from doc title/frontmatter, find corresponding Scala sources and tests
3. Related docs — scan for cross-reference links, check `sidebars.js` for sibling pages
4. Rule skill summaries — load `docs-writing-style` and `docs-mdoc-conventions`

### Phase 2: Mechanical Checks (inline)

Cheap checks that don't need fresh eyes:

- Required sections present? (detect doc type from path/frontmatter, compare against appropriate section list: `docs-data-type-ref` for reference pages, `docs-how-to-guide` for guides, `docs-tutorial` for tutorials)
- Heading hierarchy valid? (no skipped levels)
- All internal links resolve? (glob for targets)
- mdoc modifiers used correctly? (pattern match against conventions)

### Phase 3: Content & Accuracy Review (subagent)

Spawn `docs-critic` agent with:

- Doc file content
- Relevant source file paths
- Related doc file paths
- Phase 2 results (so critic skips mechanical checks)

### Phase 4: Merge & Triage

Combine Phase 2 and Phase 3 findings. Deduplicate. Sort by severity.

- Any HIGH or MEDIUM → enter fix loop (Phase 5)
- Only LOW → single-pass fix, done
- No findings → APPROVED, done

### Phase 5: Fix Loop (severity-gated, max 3 rounds)

```
Round 1: Fix all HIGH + MEDIUM (commit-per-fix)
  → Re-run Phase 3 subagent
  → If new HIGH/MEDIUM → Round 2

Round 2: Fix remaining HIGH + MEDIUM (commit-per-fix)
  → Re-run Phase 3 subagent
  → If still HIGH/MEDIUM → Round 3 (final)

Round 3 (cap): Fix what you can, report remaining to user
```

**Commit format:** `docs(<file-stem>): fix <severity>/<dimension> — <short description>`

## Integration Into Creation Skills

| Skill | New step | Position |
|-------|----------|----------|
| `docs-data-type-ref` | Step 7: `/docs-critique <doc-path>` | After integration |
| `docs-how-to-guide` | Step 8: `/docs-critique <doc-path>` | After review checklist |
| `docs-tutorial` | Step 8: `/docs-critique <doc-path>` | After review checklist |
| `docs-document-pr` | Step 7: `/docs-critique <doc-path>` | After lint verification |
| `docs-enrich-section` | Step 6: `/docs-critique <doc-path>` | After verification |
| `docs-add-missing-section` | Step 7: `/docs-critique <doc-path>` | After compliance checks |

The critique step runs after compliance verification and mdoc compilation, so the critic reviews a doc that already passes mechanical rules.

## Data Flow

```
User invokes creation skill (e.g., /docs-data-type-ref Schema)
        │
        ▼
  Maker skill (Steps 1-N: research, write, verify, format, integrate)
        │
        ▼
  docs-critique (orchestrator)
    Phase 1: Gather context (inline)
    Phase 2: Mechanical checks (inline)
    Phase 3: Spawn docs-critic subagent (fresh context, read-only)
    Phase 4: Merge findings, triage by severity
    Phase 5: Fix loop (commit-per-fix, max 3 rounds, severity-gated)
        │
        ▼
  Final state: APPROVED or remaining issues reported to user
```

## File Inventory

**New files (2):**

- `.claude/agents/docs-critic.md`
- `.claude/skills/docs-critique/SKILL.md`

**Modified files (6):**

- `.claude/skills/docs-data-type-ref/SKILL.md` — add Step 7
- `.claude/skills/docs-how-to-guide/SKILL.md` — add Step 8
- `.claude/skills/docs-tutorial/SKILL.md` — add Step 8
- `.claude/skills/docs-document-pr/SKILL.md` — add Step 7
- `.claude/skills/docs-enrich-section/SKILL.md` — add Step 6
- `.claude/skills/docs-add-missing-section/SKILL.md` — add Step 7

## Token Cost Estimate

- Phase 1-2 (inline): ~2K tokens
- Phase 3 (subagent): ~15-25K tokens
- Phase 5 per round: ~15-25K tokens
- Typical total: ~20-50K tokens (1 subagent call + 0-1 re-reviews)
