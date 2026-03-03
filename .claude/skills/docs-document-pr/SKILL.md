# Skill: pr-to-docs

## Description

Generates documentation from a GitHub pull request. Automatically gathers related issues, commits, and PR metadata, then writes a new docs page or appends a subsection to an existing page based on the content type.

**Trigger:** User says something like "document PR #123", "write docs for PR 456", "generate documentation from this PR", or "doc this PR".

---

## Phase 1: Collect PR Data

### Step 1a — Get PR metadata and commits

Use the `gh` CLI to fetch the PR number (the user provides this):

```bash
gh pr view <PR_NUMBER> \
  --json title,body,labels,commits,closingIssuesReferences \
  --repo <auto-detect from current git remote>
```

**What to extract:**
- **PR title**: The main feature/fix name
- **PR body**: Context, motivation, and any additional notes
- **Labels**: Look for `feat`, `enhancement`, `new-module`, `schema-*`, `fix`, etc.
- **Commits**: List of commit messages (useful for "What changed" section)
- **Closing issues**: Issue references the PR closes/fixes/resolves

### Step 1b — Fetch linked issue details

For each issue referenced in the PR (via `closingIssuesReferences` or found via regex scan of PR body):

```bash
gh issue view <ISSUE_NUMBER> \
  --json title,body,labels \
  --repo <same as above>
```

**What to extract:**
- Issue title and body (motivation, requirements, discussion)
- Issue labels (helps understand priority, feature area)

### Step 1c — Optional: Regex scan PR body

If the PR body mentions issues via keywords like:
- `[Cc]loses?|[Ff]ixes?|[Rr]esolves?|[Rr]elates? to|see #`

Extract issue numbers and fetch them. This catches manually-added issue references.

---

## Phase 2: Decide — New Page or Subsection?

**Create a NEW PAGE** if:
- The PR introduces a **new module, data type, or substantial feature** (e.g., "Add XML support", "Add new codec type")
- **No existing doc page** closely covers the topic
- Labels include `new-module`, `feat` (not `enhancement`), or the PR title suggests something brand-new
- The PR is **significant enough** to warrant its own documentation space

**Add a SUBSECTION** if:
- The PR is an **enhancement or bug fix** to an existing feature
- An existing page in `docs/reference/` or `docs/guides/` already covers the parent topic
- Labels include `enhancement`, `fix`, or the PR touches an already-documented area
- You can logically fit the new content under an existing section

### Heuristics

1. **Match PR title against existing doc filenames:**
   - Scan `docs/reference/` and `docs/guides/` for `.md` files
   - Extract the `id` field from each file's frontmatter
   - If the PR topic matches an existing `id`, plan for a subsection

2. **Check PR labels:**
   - `schema-*` labels → likely fits under `docs/reference/schema.md` (if it exists)
   - `feat` + new name → likely a new page
   - `enhancement` + existing area → likely a subsection
   - `fix` → usually a subsection (documents the fix under an existing feature)

3. **Example decisions:**
   - PR: "Add XML support" → New page: `docs/reference/schema-xml.md`
   - PR: "Fix schema derivation" → Subsection in: `docs/reference/schema.md` → add under "Schema derivation"
   - PR: "Add new module: Temporal" → New page: `docs/reference/temporal.md`

---

## Phase 3: Write Documentation

### Path 3a — NEW PAGE

**File path:** `docs/reference/<kebab-id>.md` or `docs/guides/<kebab-id>.md`

Choose `reference/` for API docs, technical features, and modules. Choose `guides/` for tutorials and "how to use" content.

**Content template:**

```markdown
---
id: <kebab-case-id>
title: "<Feature Title from PR>"
---

## Overview

<1–2 sentences from PR motivation and linked issues. Answer: "Why was this needed?">

## Usage

<Code example(s) derived from PR commits or PR diff. Show the key use case.>

### Example

\`\`\`scala
<brief runnable example>
\`\`\`

## Key Types

<List the main types, classes, or functions introduced. Brief description of each.>

## See Also

- [Related feature](#) — if there's a closely related doc page
```

### Path 3b — SUBSECTION in existing page

**File path:** `docs/reference/<existing-id>.md` or `docs/guides/<existing-id>.md`

**Append to the matched page:**

```markdown
## <Feature Name or "New Feature: Name">

<Context from linked issues — what problem does this solve?>

### Changes in this PR

- <Bullet 1: What changed>
- <Bullet 2: What changed>

### Example

\`\`\`scala
<brief code example showing the new feature>
\`\`\`

### API Reference

<If applicable, list new types/methods. Link to reference docs if they exist.>
```

### Guidelines for both paths:

1. **Use content from the PR:**
   - PR title → doc title
   - PR body + linked issue bodies → motivation and context
   - Commit messages → what changed (summarize key commits)
   - Labels → topic categorization

2. **Code examples:**
   - Extract from PR commits or PR diff if available
   - Keep examples short and focused on the new feature
   - Use `scala` or `bash` code blocks

3. **Structure:**
   - Start with "Why?" (Overview)
   - Then "How?" (Usage/Examples)
   - Then "What?" (API Reference, types introduced)

---

## Phase 4: Update Sidebar (if new page)

If a **new page** was created, add its `id` to `docs/sidebars.js`:

1. **Open** `docs/sidebars.js`
2. **Find the appropriate category** (e.g., "Reference", "Guides")
3. **Insert the `id`** in the correct alphabetical or logical position
4. **Example:**
   ```javascript
   {
     type: 'category',
     label: 'Reference',
     items: [
       'schema',
       'schema-xml',       // <- inserted here (alphabetically)
       'codec',
       // ...
     ],
   }
   ```

If a **subsection** was added to an existing page, no sidebar changes are needed.

---

## Phase 5: Report to User

Once documentation is written, tell the user:

1. **Data gathered:**
   - PR title: `<title>`
   - Linked issues: `#123, #456, ...` or "None found"
   - Commits included: `<count>` commits
   - Key labels: `<labels>`

2. **Decision made:**
   - "Created new page: `docs/reference/schema-xml.md`"
   - *or* "Added subsection to: `docs/reference/schema.md`"

3. **File(s) written:**
   - Path(s) to the created or modified file(s)
   - Sidebar changes (if any)

4. **Next steps (optional):**
   - "You can now review the generated docs and make refinements."
   - "Consider adding code examples if the PR includes complex changes."

---

## Implementation Checklist

When you invoke this skill:

- [ ] **Phase 1:** Use `gh pr view` to fetch PR metadata and commits
- [ ] **Phase 1:** Use `gh issue view` for each linked issue (max ~5 issues per PR is typical)
- [ ] **Phase 2:** Check existing docs in `docs/reference/` and `docs/guides/` to decide new page vs. subsection
- [ ] **Phase 2:** Use PR labels and title as tiebreakers
- [ ] **Phase 3:** Create new file or Edit existing file with frontmatter and content
- [ ] **Phase 4:** If new page, update `docs/sidebars.js`
- [ ] **Phase 5:** Report findings and file paths to user

---

## Example Invocation

**User:** "Document PR #1138"

**Your response:**
1. Run `gh pr view 1138 --json title,body,labels,commits,closingIssuesReferences`
2. Parse the PR (e.g., "CI: Replace JDK 11 with JDK 17")
3. Fetch any linked issues
4. Decide: Is this substantial enough for a new page? (Probably not — it's a CI fix. Plan for subsection in docs/guides/setup.md or similar, *if* one exists. If no existing setup guide, create docs/guides/ci-setup.md as a new page, or skip docs if it's purely internal CI.)
5. Write or append docs
6. Update sidebar if new page
7. Report: "Created docs/guides/ci-setup.md" or "Added subsection to docs/guides/setup.md" or "This PR is a CI improvement; documentation not needed in user-facing docs."

---

## Notes

- **Auto-detect repo:** Use `git remote -v` to find the GitHub repo URL if needed
- **PR numbers:** Assume user provides `#123` format; extract the number
- **No commits/issues:** If a PR has no linked issues, use only PR title and body
- **Ambiguous cases:** If unsure whether to create a new page or subsection, default to a new page (easier to reorganize later) or ask the user
