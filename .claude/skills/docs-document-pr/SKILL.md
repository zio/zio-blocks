---
name: docs-document-pr
description: >
  Generates documentation from a GitHub pull request. Automatically gathers
  related issues, commits, and PR metadata, then creates a new reference page,
  how-to guide, or appends a subsection to an existing page based on the PR's
  content type and scope. Delegates to specialized documentation skills
  (docs-data-type-ref, docs-how-to-guide) to ensure consistent style and
  formatting across all ZIO Blocks docs.
argument-hint: "[PR number (e.g., #1016 or 1016)]"
allowed-tools: Read, Glob, Grep, Bash(gh:*)
triggers:
  - "document PR"
  - "doc this PR"
  - "write docs for PR"
  - "generate documentation from"
  - "create docs from"
  - "document this pull request"
---

# Skill: docs-document-pr

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

### Decision: Delegate to Specialized Skills

**Do NOT write documentation directly.** Instead, determine the doc type and use the appropriate ZIO Blocks documentation skill:

#### Path 3a — NEW REFERENCE PAGE (API / Data Type)

**When:** PR introduces a new data type, module, codec, or technical feature
- Labels: `feat`, `new-module`, `schema-*`
- No existing parent doc to extend

**Invoke:** Use the **`docs-data-type-ref`** skill

```
User: "Create a reference page for this new Schema type from PR #1234"
```

**The skill will:**
- Gather the feature info from the PR (title, body, linked issues)
- Write a structured reference page with Overview, Usage, Examples, API Reference
- Follow ZIO Blocks documentation conventions (style, code blocks, frontmatter)
- Return the file path

#### Path 3b — NEW HOW-TO GUIDE

**When:** PR introduces a workflow, pattern, or tutorial-style feature
- Labels: `guide`, `enhancement`, `tutorial`
- Teaches "how to accomplish X" with the new feature

**Invoke:** Use the **`docs-how-to-guide`** skill

```
User: "Create a how-to guide for the new temporal processing feature from PR #1234"
```

**The skill will:**
- Extract motivation and use-cases from PR and linked issues
- Write a step-by-step guide with concrete examples
- Follow ZIO Blocks style and code block conventions
- Return the file path

#### Path 3c — ADD SUBSECTION to existing page

**When:** PR enhances an existing feature or fixes a documented area
- Labels: `enhancement`, `fix`
- Existing doc page covers the parent topic

**Manual process:**
1. Read the existing page at `docs/reference/<id>.md` or `docs/guides/<id>.md`
2. Extract PR context (issues, motivation, commits)
3. Append a new section using this structure:
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
4. Follow `docs-writing-style` for prose (refer to the skill for rules)
5. Follow `docs-mdoc-conventions` for code block syntax (refer to the skill for modifiers and admonitions)

### Guidelines Across All Paths

1. **Source content from the PR:**
   - PR title → doc title
   - PR body + linked issue bodies → motivation, use-cases, context
   - Commit messages → what changed (summarize key commits)
   - Labels → doc type and categorization

2. **Integrate with existing skills:**
   - Consult **`docs-writing-style`** for prose rules and tone
   - Consult **`docs-mdoc-conventions`** for code block modifiers (`:mdoc` markers) and Docusaurus admonitions (:::note, :::warning)
   - These skills provide shared guidelines used across all ZIO Blocks docs

3. **File naming and frontmatter:**
   - Use kebab-case for file names and `id` fields
   - Always include frontmatter: `id`, `title`, optional `sidebar_label`
   - Example:
     ```markdown
     ---
     id: schema-xml
     title: "XML Schema Support"
     sidebar_label: "XML"
     ---
     ```

---

## Phase 4: Integrate with Docs Site

### For new reference or how-to pages:

After the `docs-data-type-ref` or `docs-how-to-guide` skill returns the file path, use **`docs-integrate`** to finalize:

```
User: "Integrate the new schema-xml docs page"
```

**The skill will:**
- Check that frontmatter (id, title) is correct
- Verify the page exists at the correct path
- Update `docs/sidebars.js` in the appropriate category
- Confirm the sidebar entry is in alphabetical or logical position
- Provide verification steps

**Manual backup:** If you need to update the sidebar yourself:

1. Open `docs/sidebars.js`
2. Find the appropriate category (e.g., "Reference", "Guides")
3. Insert the `id` in correct alphabetical or logical position
4. Example:
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

### For subsection additions:

No sidebar changes needed—the subsection is part of an existing page that's already in the sidebar.

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

## Phase 6: Verify Lint (If Examples Created)

If documentation involved creating or modifying `.scala` example files in `schema-examples/`, verify that all Scala code passes the CI formatting gate before reporting completion:

```bash
sbt fmt
```

If any files were reformatted, commit the changes:

```bash
git add -A
git commit -m "docs(<topic>): apply scalafmt to examples"
```

Then verify the CI lint gate locally:

```bash
sbt check
```

**Success criterion:** zero formatting violations reported.

**If no `.scala` files were created or modified**, skip this phase.

---

## Implementation Checklist

When you invoke this skill:

- [ ] **Phase 1:** Use `gh pr view` to fetch PR metadata and commits
- [ ] **Phase 1:** Use `gh issue view` for each linked issue (max ~5 issues per PR is typical)
- [ ] **Phase 2:** Check existing docs in `docs/reference/` and `docs/guides/` to decide new page vs. subsection
- [ ] **Phase 2:** Use PR labels and title as tiebreakers
- [ ] **Phase 3a:** If new reference/API page → invoke `docs-data-type-ref` skill with PR context
- [ ] **Phase 3b:** If new how-to guide → invoke `docs-how-to-guide` skill with PR context
- [ ] **Phase 3c:** If subsection → manually edit existing page, consult `docs-writing-style` and `docs-mdoc-conventions` skills
- [ ] **Phase 4:** If new page → invoke `docs-integrate` skill to update sidebar
- [ ] **Phase 5:** Report findings and file paths to user
- [ ] **Phase 6:** If `.scala` examples were created, run `sbt fmt` and `sbt check` to verify lint compliance

---

## Example Invocation

### Example 1: New Reference Page

**User:** "Document PR #1016 - Add XML support module"

**Your response:**
1. Run `gh pr view 1016 --json title,body,labels,commits,closingIssuesReferences`
2. Parse: "feat(schema-xml): Add XML support module with schema-driven codec derivation" + label `feat`
3. Fetch linked issues for motivation
4. **Decision:** New reference page (substantial feature, no existing XML doc)
5. **Invoke** `docs-data-type-ref` skill:
   ```
   "Create a reference page for the new Schema XML type from PR #1016.
    The PR introduces XML codec support.
    Key motivation: [from issues].
    Key types: [from commits]."
   ```
6. Skill returns: `docs/reference/schema-xml.md` created
7. **Invoke** `docs-integrate` skill to add to sidebar
8. **Report:** "Created `docs/reference/schema-xml.md` and added to sidebar under Reference"

### Example 2: How-To Guide

**User:** "Document PR #1032 - Schema derivation improvements"

**Your response:**
1. Fetch PR metadata
2. **Decision:** New how-to guide (teaches users a technique)
3. **Invoke** `docs-how-to-guide` skill:
   ```
   "Create a how-to guide for deriving schemas using the Derivable type class.
    From PR #1032. Key example from commits: [extract example]."
   ```
4. Skill returns: `docs/guides/deriving-schemas.md` created
5. **Invoke** `docs-integrate` skill
6. **Report:** "Created `docs/guides/deriving-schemas.md`"

### Example 3: Subsection Addition

**User:** "Document PR #1138 - CI improvements"

**Your response:**
1. Fetch PR metadata
2. **Decision:** CI fix, not user-facing docs (or add subsection to setup guide if one exists)
3. If subsection needed:
   - Find existing page: `docs/guides/setup.md`
   - Read it, consult `docs-writing-style` skill for prose rules
   - Append section: "## CI Configuration" with the CI changes
   - Consult `docs-mdoc-conventions` skill for any code block formatting
4. **Report:** "Added subsection to `docs/guides/setup.md`" or "This PR is a CI-only change; no user-facing docs needed."

---

## Notes

- **Auto-detect repo:** Use `git remote -v` to find the GitHub repo URL if needed
- **PR numbers:** Assume user provides `#123` format; extract the number
- **No commits/issues:** If a PR has no linked issues, use only PR title and body
- **Ambiguous cases:** If unsure whether to create a new page or subsection, default to a new page (easier to reorganize later) or ask the user
