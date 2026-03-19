---
name: docs-critique
description: >
  Run a documentation creation skill with automatic maker-critic review loop.
  Spawns a maker agent to run the skill, then a critic agent to review the output.
  The maker receives critique and fixes its own work. Iterates until approved or
  max 3 rounds. Pure coordinator — never edits files itself.
argument-hint: "<skill-name> <skill-args>"
allowed-tools: Agent, Glob, Grep, Read, SendMessage
---

# Documentation Critique Loop

## Arguments

1. **skill-name** — The documentation skill to run (e.g., `docs-data-type-ref`, `docs-how-to-guide`, `docs-tutorial`, `docs-document-pr`, `docs-enrich-section`, `docs-add-missing-section`)
2. **skill-args** — Arguments to pass to the skill (e.g., `Schema`, `TypeId`)

Example invocation: `/docs-critique docs-data-type-ref Schema`

## Role

You are a **pure coordinator**. You NEVER read, write, or edit documentation files yourself. You ONLY:
1. Spawn agents
2. Pass messages between agents
3. Parse critic reports to decide next action
4. Report final status to the user

## Phase 1: Spawn Maker Agent

Spawn a general-purpose agent via the `Agent` tool:

```
Agent(
  description: "Run doc creation skill",
  prompt: "Run /<skill-name> <skill-args>. Complete all steps of the skill.
           When done, report the absolute path of the generated/modified
           documentation file as the LAST line of your response, in the format:
           DOC_PATH: <absolute-path>"
)
```

Parse the maker's response to extract the doc file path from the `DOC_PATH:` line.

**Error handling:** If the maker does not return a `DOC_PATH:` line, ask the user which file was generated and use that path.

If the Agent tool returns an error or the maker reports that the skill failed, report the error to the user and stop. Do not proceed to Phase 2.

Save the maker's agent ID for later `SendMessage` calls. The `Agent` tool returns an `agentId` in its result — store this value. You will use it as the `to` field in `SendMessage` to route critique back to the maker.

## Phase 2: Gather Critic Context

Using the doc file path from Phase 1, gather context for the critic. You MAY use `Glob`, `Grep`, and `Read` for this phase only — this is the one exception to the "never read files" rule, because you need file paths (not content) to pass to the critic. Use `Read` only for `sidebars.js` to extract sibling page IDs.

1. **Source files** — Extract the type name from the doc path (e.g., `docs/reference/schema.md` → `Schema`). Find source files:
   ```
   Glob: **/<TypeName>.scala
   Grep: "class <TypeName>" or "trait <TypeName>" or "object <TypeName>"
   ```
   Also find test files:
   ```
   Glob: **/<TypeName>Spec.scala or **/<TypeName>Test.scala
   ```

2. **Related docs** — Find sibling pages using two methods:
   - **sidebars.js** (preferred): Read `sidebars.js` and find the array containing the doc's ID. Extract sibling page IDs from the same array. Map IDs to file paths.
   - **Fallback glob** (if sidebars.js parsing fails): Glob the parent directory:
     ```
     Glob: docs/reference/*.md (for reference pages)
     Glob: docs/guides/*.md (for guides)
     Glob: docs/tutorials/*.md (for tutorials)
     ```

3. Collect all found paths into two lists: `source_files` and `related_docs`.

## Phase 3: Spawn Critic Agent

Spawn the `docs-critic` agent:

```
Agent(
  description: "Review documentation",
  subagent_type: "docs-critic",
  prompt: "Review the following documentation file for content quality,
           technical accuracy, completeness, and consistency.

           Documentation file: <doc-path>

           Source files to check accuracy against:
           <list of source_files, one per line>

           Related documentation to check consistency against:
           <list of related_docs, one per line>

           Read each file yourself using the Read tool. Return your
           structured report."
)
```

**Error handling:** If the critic's response does not contain a `### Findings` section or a `### Verdict` line, treat it as an agent failure. Retry by spawning a fresh critic with the same prompt. If the second attempt also fails, report the raw response to the user and stop.

## Phase 4: Triage

Parse the critic's `### Verdict` line:

- **`APPROVED`** → Report success to user. Done.
- **`ITERATE`** with HIGH or MEDIUM findings → Enter Phase 5.
- Only LOW findings → Send LOWs to maker for a single-pass fix:
  ```
  SendMessage(
    to: <maker-agent-id>,
    message: "The documentation critic found minor issues. Fix them if easy,
              skip if not. One commit per fix.
              Commit format: docs(<file-stem>): fix LOW/<dimension> — <description>

              <paste LOW findings here>"
  )
  ```
  Done after maker responds.

## Phase 5: Fix Loop

**Maximum 3 rounds.** Track the current round number.

**Severity-based iteration rules:**
- **HIGH** findings: iterate until fixed (up to round 3)
- **MEDIUM** findings: iterate at most once — if a MEDIUM finding persists after round 1, do not iterate further for it
- After round 1, only HIGH findings drive further iteration

### Each Round:

**Step A — Send critique to maker:**

For round 1, send all HIGH and MEDIUM findings:
```
SendMessage(
  to: <maker-agent-id>,
  message: "The documentation critic found issues that need fixing.
            Fix ALL HIGH and MEDIUM findings below. For each fix:
            - Make a separate git commit
            - Commit format: docs(<file-stem>): fix <SEVERITY>/<dimension> — <description>
            - If multiple findings target the same paragraph, combine into one commit
              using the highest severity level

            <paste HIGH and MEDIUM findings here>"
)
```

For rounds 2+, send only HIGH findings (MEDIUM issues have had their one iteration).

Wait for the maker to respond confirming fixes are done.

If the maker responds that it could not fix one or more findings, note those as unresolvable and exclude them from subsequent critic reviews. If SendMessage fails, report the error to the user and stop.

**Step B — Spawn fresh critic:**

Spawn a NEW `docs-critic` agent (do NOT reuse the previous one — fresh eyes each round):

```
Agent(
  description: "Re-review documentation round N",
  subagent_type: "docs-critic",
  prompt: <same prompt as Phase 3, but if there are unresolvable findings,
           append to the prompt:
           "The following findings have been declared unresolvable by the maker.
            Do NOT re-flag these in your report:
            <list of unresolvable findings>">
)
```

**Step C — Check verdict:**

When parsing the verdict, filter out any findings that match unresolvable items before deciding whether to iterate.

- `APPROVED` → Report success to user. Done.
- `ITERATE` with only MEDIUM findings remaining (no HIGH) → Done. MEDIUM had its one iteration.
- `ITERATE` with HIGH findings and round < 3 → Go to next round.
- `ITERATE` and round = 3 → Report remaining issues to user:
  "The documentation was reviewed 3 times. These issues remain unresolved:
   <paste remaining findings>
   Please review manually."

## Output

When done, report to the user:
- Whether the doc was APPROVED or has remaining issues
- How many rounds were needed
- Summary of findings fixed (count by severity)
