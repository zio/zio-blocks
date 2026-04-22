---
name: address-review-comments
description: >
  Address GitHub review feedback comprehensively: evaluate regular review comments
  for soundness, commit suggested changes, write resolution comments, and mark
  conversations resolved. Use when asked to address review comments or suggestions
  on a pull request, or when reviewing feedback left by maintainers/reviewers.
---

## Overview

This skill helps you systematically work through review feedback on GitHub pull requests. It handles two types of feedback:

1. **Review Comments** — Text feedback and suggestions from reviewers
2. **Suggested Changes** — Proposed code edits that reviewers have marked as suggestions (in GitHub's review UI)

## Understanding Comments

### Comment Types
- **Suggested Changes**: GitHub shows a code diff. Can auto-commit or manually apply.
- **Text Comments**: Feedback only. Requires you to decide: fix or explain.

### Finding Unresolved Comments

**GraphQL** (list all unresolved threads):
```bash
gh api graphql -f query='query {
  repository(owner: "OWNER", name: "REPO") {
    pullRequest(number: PR_NUMBER) {
      reviewThreads(first: 100) {
        nodes {
          isResolved
          comments(first: 1) { nodes { body path } }
        }
      }
    }
  }
}' | jq '.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)'
```

### Key Point
**Replying to a comment doesn't mark it resolved.** You must explicitly click "Resolve conversation" or use the API.

## Workflow

### Step 1: Identify Unresolved Comments

List all unresolved review threads:

```bash
# Replace OWNER, REPO, PR_NUMBER
gh api graphql -f query='query {
  repository(owner: "OWNER", name: "REPO") {
    pullRequest(number: PR_NUMBER) {
      reviewThreads(first: 100) {
        nodes {
          isResolved
          comments(first: 1) { nodes { body path } }
        }
      }
    }
  }
}' | jq '.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)'
```

This shows all unresolved threads with their content and file location.

### Step 2: Categorize and Evaluate Feedback

For each piece of feedback, determine:
- **Type**: Is it a text comment or a suggested change?
- **Scope**: Does it affect one line, a function, or a broader architectural decision?
- **Soundness**: Is the feedback technically correct and does it improve the code?
- **Necessity**: Is this change required to merge, or is it optional polish?

Questions to ask yourself:
- Is the reviewer's reasoning valid?
- Would this change improve code quality, correctness, maintainability, or performance?
- Is there a legitimate technical reason to accept or decline the feedback?

### Understanding the Workflow: Fix → Verify → Reply → Resolve

For each unresolved comment, follow this decision tree:

**Does it require code changes?**

**YES → Fix Flow:**
1. Make the code change
2. Test locally (compile/mdoc/run)
3. Commit: `git commit -m "fix: description"`
4. Get SHA: `git rev-parse --short HEAD`
5. Reply: `✅ Fixed in <SHA> — description`
6. Mark resolved

**NO → Reply Flow:**
1. Write explanation or disagreement
2. Reply with reasoning
3. Mark resolved

**Multiple similar comments → Bulk Fix:**
1. Identify pattern (e.g., 10 runMain commands)
2. Fix ALL instances at once
3. Single commit
4. Reply to each: `✅ Fixed in <SHA> — applied to all similar`
5. Mark all resolved

### Step 3: Address Feedback

#### For Suggested Changes (GitHub Suggestions)

Suggested changes appear as diffs in the review. You have three options:

**Option A: Commit the suggestion**
- If the suggestion is sound and aligns with the codebase style, commit it directly
- GitHub allows committing suggestions with a single button click
- This automatically marks the conversation as resolved
- You can batch commit multiple suggestions

**Option B: Apply and modify**
- If the suggestion is partially correct but needs adjustments, apply it
- Make additional edits to refine the change
- Add a comment explaining the modifications
- Mark as resolved once complete

**Option C: Decline respectfully**
- If the suggestion is unsound, reply with technical reasoning
- Explain why you're not applying it
- Ask for clarification if needed
- Don't mark as resolved until there's agreement

#### For Text Comments

For each comment:

1. **Determine required action**:
   - Code changes needed? Make them and commit
   - Clarification needed? Ask the reviewer for details
   - Disagreement? Explain your reasoning with evidence

2. **Write a resolution response**:
   - Be concise and technical (1-2 sentences)
   - Reference the commit SHA if you made changes: `Fixed in <commit-hash>`
   - If declining, explain the reasoning
   - Be respectful and collaborative

3. **Example responses**:
   - ✅ "Fixed in 48a9c5f — added null check before dereferencing"
   - ✅ "Good catch! Actually, this is intentional because [technical reason]. The current behavior is correct because..."
   - ✅ "I see the concern. Let me refactor this to make it clearer. Will update shortly."
   - ❌ "Disagree" (too terse, no explanation)

### Writing Effective Replies

- **For fixes:** `✅ Fixed in <SHA> — description`
- **For intentional behavior:** `✅ This is intentional because [reason]. We chose [approach] because [benefit].`
- **For disagreements:** `✅ I see the concern. However, [technical reasoning]. This approach [benefits].`
- **For partial fixes:** `✅ Applied and adjusted in <SHA>. Made X change to align with our style.`
- **For clarifications:** `✅ [Explanation]. See [file] for usage examples.`

## Testing Your Fixes Before Replying

Always verify fixes locally before replying:

**For code:** Run tests, compile affected modules.
**For docs:** Run `sbtn --client "docs/mdoc --in file.md"` and `sbtn --client "++2.13; check"`.

Never reply with a commit SHA until verified.

### Step 4: Reply to the Comment Thread

For each review comment, post your resolution response as a **reply to that specific comment thread** (not as a top-level PR comment). This keeps the conversation organized and makes it clear which response addresses which feedback.

**Reply to a specific review comment using the GitHub API:**

The endpoint for threaded replies is:
```
POST /repos/{owner}/{repo}/pulls/{pull_number}/comments/{comment_id}/replies
```

**Important**: Use numeric comment IDs (not GraphQL IDs). Fetch them with:
```bash
gh api repos/<OWNER>/<REPO>/pulls/<PR_NUMBER>/comments | jq '.[] | {id, body}'
```

Use this gh CLI command to reply:

```bash
gh api repos/<OWNER>/<REPO>/pulls/<PR_NUMBER>/comments/<NUMERIC_ID>/replies -f body="Your response here"
```

**Practical example - replying to a single comment:**

```bash
gh api repos/zio/zio-blocks/pulls/1206/comments/2912714559/replies -f body="✅ Fixed in commit 218469cb — corrected link"
```

**Batch script - reply to multiple review comments:**

```bash
#!/bin/bash

REPO="zio/zio-blocks"
PR=1206

# First, fetch comment IDs
COMMENTS=$(gh api repos/$REPO/pulls/$PR/comments | jq '.[] | {id, body}')

# Reply to each comment (example)
gh api repos/$REPO/pulls/$PR/comments/2912714559/replies -f body="✅ Fixed in commit 218469cb — <description>"
gh api repos/$REPO/pulls/$PR/comments/2912714586/replies -f body="✅ Fixed in commit 218469cb — <description>"
gh api repos/$REPO/pulls/$PR/comments/2912714653/replies -f body="✅ Fixed in commit 218469cb — <description>"
```

**Finding comment IDs (numeric):**

```bash
# Get all PR review comments with their numeric IDs
gh api repos/<OWNER>/<REPO>/pulls/<PR_NUMBER>/comments | jq '.[] | {id, body}'
```

**Example threaded responses:**
- ✅ "Fixed in 48a9c5f — added null check before dereferencing"
- ✅ "Good catch! Actually, this is intentional because [technical reason]. The current behavior is correct because..."
- ✅ "I see the concern. Let me refactor this to make it clearer. Will update shortly."

### Step 5: Mark Conversations as Resolved

After posting your response in the comment thread, you can mark the conversation as resolved. Note: GitHub's REST API for marking review comments as resolved requires posting to a specific endpoint.

**Mark a review comment conversation as resolved:**

```bash
gh api repos/<OWNER>/<REPO>/pulls/<PR_NUMBER>/comments/<COMMENT_ID>/replies -f body="Resolved" -F "resolved=true"
```

Alternative approach - reply and then use GitHub's web UI to mark resolved (if API limitations apply):

```bash
# Reply to the comment first (see Step 4)
gh api repos/<OWNER>/<REPO>/pulls/<PR_NUMBER>/comments/<COMMENT_ID>/replies -f body="✅ Fixed in <commit>"

# Then visit the PR on GitHub and click "Resolve conversation" in the web UI
# OR use the GraphQL API for marking resolved (advanced)
```

**GraphQL approach for marking resolved (advanced):**

```bash
gh api graphql -f query='mutation {
  resolveReviewThread(input: {threadId: "<THREAD_ID>"}) {
    thread {
      isResolved
    }
  }
}'
```

**When to mark resolved:**
- ✅ You've made the requested change and committed it (with threaded response showing the commit)
- ✅ You've explained why you're not making the change (with sound reasoning in the thread)
- ✅ You've clarified something the reviewer misunderstood (in the thread response)

**When NOT to mark resolved:**
- ❌ You've asked the reviewer for clarification in the thread
- ❌ There's disagreement and you haven't reached consensus
- ❌ The comment is still pending action

## Best Practices

1. **Reply in the comment thread** — Post your resolution response as a reply to the specific review comment (not as a top-level PR comment). This keeps the conversation organized and makes it clear which response addresses which feedback.

2. **Address all feedback** — Even if you disagree, respond to every comment. Leaving unanswered feedback looks like you ignored the review.

3. **Be technical** — Explain *why*, not just *what*. Use specific evidence (performance metrics, design patterns, architectural constraints).

4. **Group related changes** — If multiple comments relate to the same issue, fix them all and respond once, then mark all as resolved together.

4. **Commit atomically** — Each commit should address one logical piece of feedback. Avoid bundling unrelated fixes.

5. **Show respect for the reviewer's time** — Their feedback is a gift. Even if you disagree, acknowledge the effort and explain your position clearly.

6. **Use reply-to-comment chains** — In GitHub, you can reply to specific lines within a comment thread. Use this to keep discussion organized.

## Common Scenarios

### Scenario 1: Formatting Suggestions
**Feedback:** "This line is too long, consider breaking it up"
**Action:** If the suggestion is reasonable and matches project style, commit it. Format changes are low-risk.

### Scenario 2: Architectural Disagreement
**Feedback:** "This approach is inefficient, consider using X instead"
**Action:** Research their suggestion. If valid, apply it. If not, explain your design rationale with evidence (benchmarks, complexity analysis, readability).

### Scenario 3: Missing Tests
**Feedback:** "This code path isn't tested"
**Action:** Either add tests or explain why the path is already covered (existing tests, defensive design, etc.).

### Scenario 4: Partial Suggestion
**Feedback:** A suggested change that's mostly good but needs tweaking
**Action:** Apply the suggestion, make your refinements, commit, and comment: "Applied and adjusted in <commit>. Made X change to better fit our style."

## Tools & Helpers

### Batch Reply Script

For PRs with 5+ comments, create a reusable batch reply script:

```bash
#!/bin/bash
# reply-comments.sh - Post replies to multiple review comments

REPO="<owner>/<repo>"  # e.g., zio/zio-blocks
PR=<PR_NUMBER>         # e.g., 1304

# Map comment_id to response message
# Format: "id:response" (use \\n for line breaks in response)
declare -a COMMENTS=(
  "3041376465:✅ Fixed in abc123de — description"
  "3041376517:✅ Fixed in abc123de — description"
  "3041376537:✅ Fixed in abc123de — description"
)

echo "Posting replies to $PR..."

for mapping in "${COMMENTS[@]}"; do
  comment_id="${mapping%%:*}"
  response="${mapping#*:}"
  if gh api repos/$REPO/pulls/$PR/comments/$comment_id/replies -f body="$response"; then
    echo "✓ Replied to comment $comment_id"
  else
    echo "✗ Failed to reply to comment $comment_id"
  fi
done

echo "Done!"
```

**Usage:**
1. Copy the script above to `reply-comments.sh`
2. Update `REPO` and `PR` variables
3. Build the `COMMENTS` array with your comment IDs and responses (from Step 1 and Step 3)
4. Run: `bash reply-comments.sh`

## Using gh CLI

**View review feedback:**

```bash
# View all reviews on a PR
gh pr view <PR> --json reviews

# View all comments (includes review comments)
gh pr view <PR> --json comments

# View reviews with inline comments
gh pr view <PR> --json reviews,comments

# Get review comments with IDs from a specific review
gh api repos/<OWNER>/<REPO>/pulls/<PR>/reviews/<REVIEW_ID>/comments | jq '.[] | {id, body}'

# Pretty-print all comment IDs and bodies
gh pr view <PR> --json comments | jq '.comments[] | {id, author: .author.login, body: (.body | split("\n")[0])}'
```

**Reply to review comments (CORRECT):**

```bash
# Reply to a specific review comment (creates threaded reply)
gh api repos/<OWNER>/<REPO>/pulls/<PR>/comments/<COMMENT_ID>/replies -f body="Your response"

# Example with commit reference
gh api repos/zio/zio-blocks/pulls/1191/comments/2900317654/replies -f body="✅ Fixed in 71db75c4 — updated to use @VERSION@ placeholder"
```

**Batch reply for multiple comments:**

When replying to many comments, use an inline bash loop. First, prepare your comment-to-response mapping in a script:

```bash
#!/bin/bash

REPO="<owner>/<repo>"
PR=<PR_NUMBER>

# Define mappings: comment_id -> response
COMMENTS=(
  "3041376465:✅ Fixed in 5d7dbb1d — description"
  "3041376517:✅ Fixed in 5d7dbb1d — description"
  "3041376537:✅ Fixed in 5d7dbb1d — description"
)

# Post reply to each
for mapping in "${COMMENTS[@]}"; do
  comment_id="${mapping%%:*}"
  response="${mapping#*:}"
  gh api repos/$REPO/pulls/$PR/comments/$comment_id/replies -f body="$response" && \
    echo "✓ Replied to comment $comment_id"
done
```

**Quick helper to extract comment IDs and build scaffold:**

If you need to reply to many comments, first extract comment IDs and bodies:

```bash
# List comment IDs and previews for easy mapping
gh api repos/<OWNER>/<REPO>/pulls/<PR>/comments | \
  jq -r '.[] | "\(.id): \(.body | split("\n")[0])"'

# Then build your COMMENTS array above with the IDs
```

**Mark conversations resolved:**

The most reliable approach is the web UI: visit the PR on GitHub and click "Resolve conversation" next to each reply thread. However, if you prefer automation:

```bash
# Batch mark as resolved via GraphQL (requires GraphQL IDs from Step 1)
gh api graphql -f query='mutation {
  resolveReviewThread(input: {threadId: "<THREAD_GRAPHQL_ID>"}) {
    thread { isResolved }
  }
}'

# To collect THREAD_GRAPHQL_IDs, use the GraphQL query from Step 1 and extract .id from each thread
```

**Recommendation:** After posting replies, mark conversations resolved via GitHub's web UI or by iterating with GraphQL mutation for each thread ID. This is clearer than REST API approaches and always works reliably.
