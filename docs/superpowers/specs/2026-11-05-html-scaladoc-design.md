# HTML module Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `html` module (the HTML, CSS and JavaScript DSL that `datastar`, `htmx` and nine web guides build on) has comments on 161 of 1,001 public API members (16%). The biggest gaps are the two `HtmlElements` copies (`scala-2/` 302 members, 51 documented; `scala-3/` 262, 32 documented), `Dom` (106, 33), `ToCss` (49, 3), `CssSelector` (47, 0), `CssSelectable` (40, 0) and `Css` (21, 0). Nobody has checked that the existing comments are true; earlier rollouts found that 15% to 36% of inherited comments were wrong or imprecise, and the html module's riskiest claims (escaping, rendering order, void and boolean attributes, selector matching, the interpolators) are exactly the kind that go wrong. The one `{{{` block (`DomSelection.scala`) is never compiled. Ten members of `HtmlElements` have different text in their `scala-2/` and `scala-3/` copies: `ol #2`, `optgroup #2`, `option`, `option #2`, `script #2`, `select #2`, `style #2`, `table #2`, `tr #2`, `ul #2`.

## Goal

Every public member of `html` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` is empty.

## Decisions (Milad, 2026-11-05)

- **One PR** (tooling, the existing block made to compile, all Scaladoc).
- **Tier 1:** `Dom` and its node types (`Element`, `Text`, `RawHtml`, `Fragment`, `Attribute`, ...), `DomModifier`, `HtmlElements` (the tag functions as a group: one trait-level overview), `AttributeKey` / `MultiAttributeKey`, `Css`, `CssSelector`, `DomSelection`, `Js`, and the `html"..."` / `css"..."` / `js"..."` / `selector"..."` interpolators (`TemplateInterpolators`, both copies).
- **Tier 2:** every tag and attribute helper (one line each, identical across `scala-2/` and `scala-3/`), the `To*` type classes and their instances, `CssSelectable`, the Scala 2-only argument ADTs (`ScriptArg`, `StyleArg`, `ListArg`, `CellArg`, ...).
- **Tier 3:** `Escape` (`private[html]`), `InterpolatorRuntime` (public only for Scala 2 macro expansion), `TemplateMacros`.
- **Agents:** Opus writes tier 1, fact-checks all existing comments, writes bug entries and does the fresh review; Sonnet writes the tier-2 helpers. Only one agent runs sbt at a time.
- **AGENTS.md:** add `html` to the `scaladoc-check` bullet, and a downstream line `html*` → `scaladoc-check`, `datastar*`, `htmx*`, `docs` (diff approved).
- **Scala 2/3 differences:** identical text in both copies; real differences (Scala 3 union-typed arguments versus Scala 2 argument ADTs, macro error messages) are named in that text and verified by a probe on both versions.

## Layout

All sources are in `html/shared/src/main/`: `scala/zio/blocks/html/` (21 files), plus `scala-2/` and `scala-3/` copies of `HtmlElements` and `TemplateInterpolators`. `html/jvm/` and `html/js/` hold only `.gitkeep`. Package `zio.blocks.html`, so the wrapper import needs no override; html's cross versions (3.9.0, 3.10.0, 2.13.18) match `scaladoc-check`'s. The module is on `/api` (`unidocProjectFilter`). Reference page: `docs/reference/html.md` (`https://zioblocks.com/reference/html/`). The html main sources at the base `a7014433f` are byte-identical to `v0.0.56`, so probes run against the published 0.0.56 artifacts and bug citations stay at base lines.

Name clashes matter for examples: `scaladoc-check` wraps each example with `import zio.blocks.html._` and its classpath also has `schema`, `chunk`, `scope`, `streams`, `config` and `typeid`. Known clashes: `p(...)` ambiguous on Scala 3 with `schema` imported, `search` and other tag values versus same-named helpers, `span`/`main` shadowed by common Scala names.

## Tasks

- **Task 1 (Opus):** add `html.jvm` to `scaladocCheckedModules`; extractor test; make the `DomSelection` block compile. `HtmlElements` first (unify the 10 drifting members, fact-check its 83 existing comments, trait overview), committed before Task 2 starts. Then tier 1 in the shared files and both `TemplateInterpolators`; tier 3; fact-check every existing comment (table: member, claim, kind, evidence); bug write-ups. Runs all sbt gates.
- **Task 2 (Sonnet):** tier-2 gaps in both `HtmlElements` copies (tag and attribute helpers, Scala 2 argument ADTs), after Task 1's `HtmlElements` commit.
- **Task 3 (Sonnet, parallel with Task 1):** tier-2 gaps in `ToAttrValue`, `ToCss`, `ToDom`, `ToElements`, `ToJs`, `ToModifier`, `ToText`, `CssSelectable`.
- **Review:** two fresh-context Opus fact-checkers on disjoint file groups (probes on 2.13.18 and 3.9.0; Scala.js where rendering or number formatting may differ), a fix round, a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `htmlJVM/doc` and `htmlJS/doc` on 3.9.0 and 2.13.18: no new warnings, read from the log (sbt `--client` can exit 0 on failure)
3. `python3 -I .claude/scripts/scaladoc-coverage.py html` (1001/1001) and `--drift` (empty)
4. `comments-only.py a7014433f html`
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`

## Out of scope

Library code changes, reference-page rewrites (errors go to `docs-issues.md` entries via the coordinator), `docs/dev/` files (including the module list in `scaladoc-style.md`, which the coordinator updates), PR #1644, `datastar`, `htmx` and other modules.
