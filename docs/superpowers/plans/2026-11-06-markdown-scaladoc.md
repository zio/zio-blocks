# Markdown module Scaladoc plan

Design: `docs/superpowers/specs/2026-11-06-markdown-scaladoc-design.md`.

## The problem, in plain English

The markdown module turns Markdown text into a document model and back out as Markdown, HTML or terminal text. Its public API has 107 members; 16 have no comment, and nobody has checked whether the other 91 comments are true. The six code examples in the comments are broken: the formatter squashed them into run-on lines, so the build cannot compile them.

## The goal

Every public member has a true comment, the main types have a short overview with an example that the build compiles on all three Scala versions, and one pull request ships it.

## Global constraints

- Scala changes in `markdown/` are comments only (checked by `comments-only.py 04b56bc92 markdown`). Other edits allowed: `build.sbt`, `project/ScaladocSnippets.scala` and its spec, the module list in `docs/dev/scaladoc-style.md`, and the AGENTS.md lines Milad approved.
- Truth rule: read the code and probe the published 0.0.56 artifact before every behaviour claim; call bugs "a known bug"; never link `docs/dev/`.
- Write once: the two `MdInterpolator.scala` copies carry identical text.
- Examples use braces, compile on their own after `import zio.blocks.docs._`, and show real output. Prefer samples that fit Markdown (notes, release notes, READMEs), not books. Use `\$` for dollar signs. Do not put `/*`, `^` or `<h1>` in comments.
- Bugs go to the coordinator in one file with target lines; none are written to `docs/dev/` or `backlog.md`.
- Memory: check `free -g` before each sbt run (wait under 6 GB available); `sbt --client shutdown` after each gate round; read results from logs, not exit codes.

## Steps

1. **Tooling.** Add the package override, its extractor test, the `markdown.jvm` entry and the style-guide and AGENTS.md lines. Commit.
2. **Writing and fact-check.** Rewrite the six old blocks, add tier 1 and the 16 gap comments, fact-check the 91 old comments, write the claim table. Commit per file group, run `scalafmt` before each commit and check the blocks survive.
3. **Gates.** Run the five gates above.
4. **Review.** Fresh-context fact-check, fix round, re-review.
5. **Ship.** Merge `origin/main`, re-run the snippet check, push to `khajavi`, open the PR with a plain-English body, watch CI. Do not merge.
6. **Close-out.** Send the coordinator the bug file and the backlog 13r text with the metric.
