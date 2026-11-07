# Markdown module Scaladoc design

Follows `docs/superpowers/specs/2026-11-05-jwt-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `markdown` module (a Markdown parser, document model, GFM writer, HTML and terminal renderers and the `md"..."` interpolator) has comments on 91 of 107 public API members (85%). The 16 gaps are the `MdInterpolator` trait and its `md` method on both Scala versions (plus the Scala 2 `MdStringContext`), `MdInterpolatorRuntime` and `parseAndBuild`, the objects `ToMarkdown`, `Doc`, `HeadingLevel`, `Alignment` and `Inline`, `Doc#toHtml`, `Doc#toHtmlFragment`, `Doc#toTerminal` and `Doc.empty`. Nobody has checked that the 91 existing comments are true; earlier rollouts found 15% to 40% of inherited comments wrong or imprecise. The six `{{{` blocks (in `Parser`, `Doc`, `package`, `ToMarkdown` and `Renderer`) were reflowed by scalafmt into run-on lines, so none of them can compile.

## Goal

Every public member of `markdown` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` stays empty.

## Decisions (Milad, 2026-11-06)

- **One PR** (tooling, the existing blocks made to compile, all Scaladoc).
- **Tier 1:** `Doc`, `Block`, `Inline`, `Parser`, `Renderer`, `HtmlRenderer`, `TerminalRenderer`, `ToMarkdown`, the `md"..."` interpolator.
- **AGENTS.md:** add `markdown` to the `scaladoc-check` bullet and a downstream row `markdown*` -> `scaladoc-check`, `schema*`, `endpoint*`, `openapi*`, `schema-yaml*`, `docs` (the projects whose `dependsOn` includes `markdown` in `build.sbt`).

## Layout

`markdown` is a `crossProject(JSPlatform, JVMPlatform)` with `CrossType.Full`, depending on `chunk`. Shared sources are in `markdown/shared/src/main/scala/zio/blocks/docs/`: `Parser`, `HtmlRenderer`, `TerminalRenderer`, `Renderer`, `Doc`, `Inline`, `Block`, `ToMarkdown`, `HeadingLevel`, `Alignment`, `TableRow`, `ParseError`, `MdInterpolatorRuntime` and `package.scala` (`package object docs extends MdInterpolator`). The only version-specific file is `MdInterpolator.scala`, with copies in `scala-2/` and `scala-3/` that must carry identical text. The package is `zio.blocks.docs`, not `zio.blocks.markdown`, so the checker needs `"markdown" -> "zio.blocks.docs"` in `ScaladocSnippets.packageOverrides` (with an extractor test) and `markdown.jvm` in `scaladocCheckedModules`. Cross versions are the `stdSettings` default, the same as `scaladoc-check`'s. Reference page: `docs/reference/docs.md`.

`markdown` main sources at `04b56bc92` are identical to `v0.0.56`, so every probe runs against the published 0.0.56 artifact (`zio-blocks-markdown`), and bug citations use base lines.

No markdown test reads source text, so a comments-only change cannot fail one.

## Tasks

- **Task 1 (tooling):** `packageOverrides` entry, extractor test, `markdown.jvm` in `scaladocCheckedModules`, the `build.sbt` comment, the style-guide module list, the AGENTS.md lines.
- **Task 2 (writing and fact-check):** rewrite the six old blocks so they compile, write tier 1 and the 16 gaps, and fact-check all 91 existing comments with scala-cli probes (parser edge cases: nested lists, code fences, emphasis, HTML blocks, tables; exact HTML and terminal output as full strings; escaping; the interpolator on Scala 2 and 3; `ToMarkdown` and round trips). Record a table (member, claim, kind, evidence) and bug write-ups.
- **Review:** a fresh-context fact-check that re-runs probes and every reproducer, a fix round, and a re-review of the fix diff.
- **Close-out:** the backlog section 13r text and metric, sent to the coordinator.

Only one agent runs sbt at a time, and only when free memory is at least 6 GB.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `markdownJVM/doc` and `markdownJS/doc` on 3.9.0 and 2.13.18, no new warnings (read the logs)
3. `python3 -I .claude/scripts/scaladoc-coverage.py markdown` (107/107) and `--drift` (empty)
4. `comments-only.py 04b56bc92 markdown` prints 0 with code changes
5. `++2.13.18; check; headerCheckAll`, `scalafmtAll`, `scalafmtSbt`

## Security

An HTML renderer that does not escape, or any similar weakness, is not written into a tracked file, commit or PR. It goes to a gitignored ledger file and to the coordinator marked SECURITY.

## Out of scope

Library code changes, reference-page rewrites (errors go to `docs-issues.md` entries via the coordinator), `docs/dev/` files, other modules, CI files.
