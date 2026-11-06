# HTML Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-05-html-scaladoc-design.md`

## In plain English

**The problem.** The `html` module lets Scala code build web pages as values: `div(className := "card", h1("Hello"))` is an element you can render to an HTML string. Only 16% of its public members have comments, nobody has checked that those comments are true, the Scala 2 and Scala 3 copies of the tag functions say different things in ten places, and the one code example in a comment is never compiled.

**The goal.** Every public member has a true, short comment, identical in the Scala 2 and Scala 3 copies. The main types have a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The helper project `scaladoc-check` pulls code examples out of comments and compiles them. Add `html` to its list and one test; the controller edits two `AGENTS.md` lines.
2. **Core types and fact-check (Opus).** A strong-model agent makes the existing example compile, makes the Scala 2 and Scala 3 texts identical, writes an overview, example and link for the main types, and checks every existing comment against the code with real probes.
3. **Helpers (two Sonnet agents).** Cheaper-model agents write the one-line comments for the tag and attribute functions and the conversion type classes ("type class" = an interface the compiler picks automatically for a given type, such as "how to turn an `Int` into attribute text").
4. **Review.** Two fresh agents test every changed claim against the code on Scala 2.13 and Scala 3, against the published 0.0.56 library. The authors fix what they find; a last agent re-checks only the fixes.
5. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-05-html-scaladoc/global-constraints.md` (skill template with `html` filled in, plus the html-specific rules).

## Task 1 (Opus): tooling, drift, tier 1, fact-check

**Files:** `build.sbt` (`scaladocCheckedModules` only), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, and in `html/shared/src/main/`: both `HtmlElements` copies (drift, existing comments, trait overview only), both `TemplateInterpolators` copies, `Dom`, `DomModifier`, `DomSelection`, `AttributeKey`, `MultiAttributeKey`, `Css`, `CssSelector`, `Js`, `Escape`, `InterpolatorRuntime`, `package.scala`. `AGENTS.md` is edited by the controller (diff approved).

**Tier 1 and key methods:** `Dom` (`render`, `renderMinified`/indent variants, `select`, `Element`, `Text`, `RawHtml`, `Fragment`, `Attribute`, `Empty`), `DomModifier`, `HtmlElements` (overview), `AttributeKey` (`:=`), `MultiAttributeKey`, `Css`, `CssSelector` (combinators, `render`), `DomSelection`, `Js`, `html`/`css`/`js`/`selector` interpolators.
**Reference page:** `docs/reference/html.md` (`https://zioblocks.com/reference/html/`, anchors allowed).
**Known issues:** coordinator copy `/home/milad/sources/scala/zio-blocks-worktrees/tutorials-and-guides-lists/docs/dev/library-issues.md`: lines 2814 (`html"..."` escapes attribute values twice), 2818 (`selector"..."` never matches in `Dom#select`), 2822 (`p(...)` ambiguous with schema on Scala 3), 13296 (`search` and same-named helpers), 13840 (`span`/`main` shadowed).

- [ ] Step 1: add `html.jvm`, the extractor test; make the `DomSelection` block compile (gate run deferred until memory allows).
- [ ] Step 2: `HtmlElements` both copies: unify the 10 drifting members, fact-check the existing comments, trait overview. Commit, then tell the controller (Task 2 starts).
- [ ] Step 3: tier 1 and tier 3 Scaladoc in the remaining Task 1 files; fact-check table for all existing comments.
- [ ] Step 4: after Tasks 2 and 3 have committed: all gates over the whole module, format, one commit per file group, report.

## Task 2 (Sonnet): HtmlElements helpers

- [ ] Step 1: list every member of both `HtmlElements` copies the coverage script reports undocumented (after Task 1 Step 2).
- [ ] Step 2: one-line comments, identical text in both copies (Scala 2-only ADTs only in `scala-2/`); probe rendering claims; no absolute words unless verified.
- [ ] Step 3: hygiene grep, commit, report (no sbt).

## Task 3 (Sonnet): type classes and CssSelectable

- [ ] Step 1: list undocumented members of `ToAttrValue`, `ToCss`, `ToDom`, `ToElements`, `ToJs`, `ToModifier`, `ToText`, `CssSelectable`.
- [ ] Step 2: comments; probe each conversion's output (number formatting on JVM; note Scala.js only if probed).
- [ ] Step 3: hygiene grep, commit, report (no sbt).

## Task 4: review and ship

- [ ] Two parallel Opus fact-check reviews (fresh context, disjoint files), fix round (authors resumed), Sonnet scoped re-review.
- [ ] Merge origin/main, re-run gates, push `khajavi html-scaladoc`, open PR, watch CI to green.
- [ ] Report coverage before/after, bug entries and the §13i text and metric to the coordinator.
