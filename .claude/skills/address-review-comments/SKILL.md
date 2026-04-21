---
name: address-review-comments
description: >
   Work through GitHub pull request review feedback end-to-end: discover unresolved
   threads, decide which ones need code changes vs. a reply, commit and verify fixes,
   post threaded replies, and mark conversations resolved. Use whenever the user asks
   to "address review comments," "reply to reviewer feedback," "resolve PR
   conversations," "apply review suggestions," or is otherwise triaging the comments
   left on one of their pull requests.
---

# Addressing GitHub Review Comments

Use this skill to run the full loop on a PR: find what's unresolved, fix or explain
each thread, and close it out. The workflow has three phases — **Discover**,
**Address**, **Close out** — and they share one canonical set of GitHub API calls.

## Key GitHub facts that shape this workflow

A few things trip people up; keep them in mind:

- Inline review comments (the ones attached to a line of code) live at
  `/repos/{owner}/{repo}/pulls/{n}/comments`. They are **not** returned by
  `gh pr view <PR> --json comments`, which returns the issue-style conversation
  comments instead. Use the REST endpoint or the GraphQL `reviewThreads` query.
- Posting a reply does **not** mark a thread resolved. Resolution is a separate
  action. The only reliable programmatic path is the GraphQL `resolveReviewThread`
  mutation — there is no REST endpoint with a `resolved` parameter.
- A reply to a review comment needs the comment's **numeric** `databaseId`, while
  resolving a thread needs the thread's **GraphQL** `id`. Fetch both at once so
  you don't re-query later.
- Threads on lines that have changed since the review become **outdated**. They
  still exist and still need a response, but the inline context is gone — handle
  them differently (see "Patterns" below).

## Phase 1 — Discover unresolved threads

Run this once. It returns everything the later phases need, including pagination
info. Replace `OWNER`, `REPO`, `PR_NUMBER`:

```bash
gh api graphql -f query='
query($owner: String!, $repo: String!, $pr: Int!, $cursor: String) {
  repository(owner: $owner, name: $repo) {
    pullRequest(number: $pr) {
      reviewThreads(first: 50, after: $cursor) {
        pageInfo { hasNextPage endCursor }
        nodes {
          id
          isResolved
          isOutdated
          path
          line
          comments(first: 10) {
            nodes {
              databaseId
              author { login }
              body
              url
            }
          }
        }
      }
    }
  }
}' -F owner=OWNER -F repo=REPO -F pr=PR_NUMBER
```

If `hasNextPage` is `true`, re-run with `-F cursor=<endCursor>` and merge results.

Filter to unresolved threads and save the essentials to a working file (thread
`id`, first comment `databaseId`, `path`, `line`, `isOutdated`, and body). You'll
reference this list throughout the next two phases.

## Phase 2 — Address each thread

For every unresolved thread, make one decision: **does it need a code change, or
just a reply?** Then work the matching flow.

### Decision tree

- **Needs code change** → edit, verify, commit, reply with the commit SHA,
  resolve.
- **Disagreement or intentional behavior** → reply with technical reasoning,
  resolve once you've made your case. If the reviewer might push back, leave it
  unresolved and wait for their response.
- **Clarification needed from the reviewer** → reply with the question. Do **not**
  resolve.
- **Multiple threads point at the same underlying issue** → fix once, reply to
  each pointing at the same SHA, resolve together.

### Evaluate the feedback before acting

For each thread, ask:

- Is the reviewer's reasoning technically correct?
- Does the change improve correctness, maintainability, performance, or
  readability — or is it personal style?
- Is it required to merge, or optional polish? (Both are worth addressing, but
  they inform how you reply.)

Accepting reasonable feedback is cheap; pushing back on well-meaning but wrong
feedback is part of the reviewer's job being useful. Don't do either reflexively.

### If it's a GitHub "suggested change" (a diff in the review UI)

Three options, in order of preference when the suggestion is sound:

1. **Commit the suggestion.** The GitHub web UI's "Commit suggestion" button is
   the only officially supported way to do this; no REST endpoint commits the
   patch directly. If you're working entirely from the CLI, extract the diff from
   the suggestion body, apply it manually, and commit — the resulting commit is
   functionally identical.
2. **Apply and modify.** Use the suggestion as a starting point, refine it, and
   note the adjustments in your reply.
3. **Decline with reasoning.** Reply explaining why the suggestion doesn't fit,
   and leave unresolved until there's agreement.

### Verify before replying

Never reply with a commit SHA until you've confirmed the fix locally. "Verify"
depends on the stack:

- Scala / sbt: compile affected modules; run `mdoc` for docs changes.
- Node: `npm test` / `npm run build` / lint as appropriate.
- Rust: `cargo check` plus `cargo test` on touched crates.
- Python: run tests and your linter of choice.
- Docs-only: build the site locally and visually inspect the changed pages.

If the project has its own conventions, defer to those. A dedicated
project-local skill is the right place to record them — keep this skill generic.

### Post the reply

Replies go **inline on the thread**, not as top-level PR comments. Use the first
comment's numeric `databaseId` from Phase 1:

```bash
gh api repos/OWNER/REPO/pulls/PR_NUMBER/comments/COMMENT_ID/replies \
  -f body="Fixed in abc1234 — added the missing null check before dereferencing."
```

For several replies at once, see "Batch operations" below.

## Phase 3 — Close out

### Mark resolved

Use the thread's GraphQL `id` (not a numeric ID) with the `resolveReviewThread`
mutation:

```bash
gh api graphql -f query='
mutation($threadId: ID!) {
  resolveReviewThread(input: {threadId: $threadId}) {
    thread { isResolved }
  }
}' -F threadId=THREAD_GRAPHQL_ID
```

Resolve when:

- The fix is committed and the reply names the SHA, **or**
- You've explained a disagreement clearly and don't expect a back-and-forth, **or**
- You've cleared up a misunderstanding.

Don't resolve when you've asked the reviewer a question, or when the fix is still
in progress.

### Re-request review

After addressing everything, re-request review so the reviewer sees the PR in
their queue again:

```bash
gh api -X POST repos/OWNER/REPO/pulls/PR_NUMBER/requested_reviewers \
  -f 'reviewers[]=REVIEWER_LOGIN'
```

### If CI fails after you've resolved threads

This is fine — resolution is about the reviewer's feedback, not about CI. Fix the
CI failure in a new commit, then either:

- Leave the resolved threads alone if the failure is unrelated, or
- Re-open the relevant thread (web UI → "Unresolve conversation") if your fix
  invalidated a previously-resolved concern, address it again, and re-resolve.

### Squashing review-fixup commits

If the repo squash-merges, don't squash fixup commits yourself — reviewers often
want to see the diffs that addressed their comments. The squash happens at merge
time. If the repo requires a clean linear history, squash after the PR is
approved, not before.

## Patterns

### Outdated threads

When `isOutdated` is `true`, the line the thread was attached to no longer
exists. Options:

- If the underlying concern is already handled by later changes, reply briefly
  (`Addressed in <SHA> when <module> was refactored.`) and resolve.
- If the concern is still live but the inline anchor is lost, leave a top-level
  PR comment summarizing the remaining thread(s) so the reviewer can find them,
  then resolve the outdated ones.

### Disagreement

Be specific. "Disagree" is never a complete reply. Point to a constraint, a
benchmark, a convention, or a prior decision. Example:

> This is intentional — we rely on the lazy initialization here so that the
> config module doesn't depend on the database client at startup. Inverting it
> would create a cycle via `AppContext`. Happy to revisit if there's a cleaner
> way to break the cycle.

### Clusters of similar comments

If a reviewer flags the same pattern across ten files (naming, a lint rule, a
missing annotation), fix all instances in one commit, then post the same reply
to each thread with a pointer:

> Fixed in `abc1234` — applied to all matching call sites in the same commit.

Resolve each thread after replying.

## Writing effective replies

Keep them short and specific. A good reply names the action and the reason.

- **Fix:** `Fixed in abc1234 — <what changed>.`
- **Intentional:** `This is intentional because <reason>. <why the alternative is worse>.`
- **Partial:** `Applied and adjusted in abc1234 — kept <X> because <reason>.`
- **Clarification request:** `Could you say more about <specific point>? I want
  to make sure I'm addressing the right concern.`
- **Disagreement:** `I see the concern, but <technical reasoning>. Leaving as-is
  for now — happy to reconsider if <condition>.`

Leading markers like `✅` are optional and mostly useful for scanning long PRs.
Skip them on disagreements or questions — a green check on "I don't think we
should do this" reads strangely.

## Batch operations

For PRs with many threads, batch the replies. Use a TSV file rather than a
`:`-separated string so URLs, code, and quoted text in replies don't break the
parser.

`replies.tsv` (tab-separated: `numeric_comment_id<TAB>reply_body`):

```
3041376465	Fixed in abc1234 — renamed to `parseConfig`.
3041376517	Fixed in abc1234 — same rename applied here.
3041376537	Intentional: see comment on line 42 of config.scala.
```

Driver script:

```bash
#!/bin/bash
set -euo pipefail

REPO="${REPO:?set REPO=owner/name}"
PR="${PR:?set PR=number}"
DRY_RUN="${DRY_RUN:-0}"

while IFS=$'\t' read -r comment_id body; do
  [ -z "$comment_id" ] && continue
  if [ "$DRY_RUN" = "1" ]; then
    printf 'would reply to %s: %s\n' "$comment_id" "$body"
    continue
  fi
  if gh api "repos/$REPO/pulls/$PR/comments/$comment_id/replies" -f body="$body" \
      >/dev/null; then
    echo "replied to $comment_id"
  else
    echo "FAILED on $comment_id" >&2
  fi
done < replies.tsv
```

Run `REPO=owner/name PR=123 DRY_RUN=1 bash reply.sh` first to sanity-check, then
drop `DRY_RUN=1`.

A parallel TSV (`resolve.tsv` with thread GraphQL IDs, one per line) and a
similar loop handles Phase 3 resolutions.

## Best practices

- **Reply in the thread, not on the PR.** Threaded replies keep the mapping
  between feedback and response obvious; top-level comments don't.
- **Address every thread, even ones you disagree with.** Silent non-response
  reads as "ignored."
- **Explain *why*, not just *what*.** A reviewer usually doesn't need to be told
  what changed — they need to know why.
- **Group related fixes.** One commit, one reply template, one batch of
  resolutions.
- **Don't resolve your way out of a real disagreement.** If the reviewer hasn't
  agreed, leave the thread open.
