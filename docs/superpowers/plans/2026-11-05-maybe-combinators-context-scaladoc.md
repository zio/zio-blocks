# maybe, combinators and context Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-05-maybe-combinators-context-scaladoc-design.md`

## In plain English

**The problem.** Three small modules have almost no comments on their public members. `maybe` gives an optional value (`Maybe`) that tries to avoid allocating; `combinators` computes, at compile time, the type you get when you join two values into a tuple, an `Either` or a union; `context` is a typed bag of services you look up by type. Only 25 of their 251 public members have comments, nobody has checked those comments against the code, and the Scala 2 and Scala 3 copies of three `Maybe` members say different things. Two code examples in comments are never compiled.

**The goal.** Every public member has a true, short comment, identical in the Scala 2 and Scala 3 copies. Where the two Scala versions really behave differently (they often do here: Scala 2 uses macros, Scala 3 uses match types), the shared text says so. The main types have a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The helper project `scaladoc-check` pulls code examples out of comments and compiles them. Add the three modules to its list (`build.sbt`), update the module list in the style guide and two `AGENTS.md` lines.
2. **Writing and fact-checking (one Opus agent).** Makes the 2 existing examples compile, makes the three drifting `Maybe` texts identical, writes an overview, example and link for the main types, fills every remaining gap, and checks each of the 25 existing comments against the code with real probes on both Scala versions.
3. **Review.** A fresh agent tests every changed claim against the code on Scala 2.13 and Scala 3, against the published 0.0.56 library. The writer fixes what it finds; a last agent re-checks only the fixes.
4. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-05-maybe-combinators-context-scaladoc/global-constraints.md` (skill template with the three modules filled in, plus module-specific rules).

## Task 1 (Opus): tooling, seed, drift, tier 1, gaps, fact-check

**Files:** `build.sbt` (`scaladocCheckedModules`: `maybe.jvm`, `combinators.jvm`, `context.jvm`), `docs/dev/scaladoc-style.md` (module list only), and all main sources of `maybe/`, `combinators/`, `context/`. `AGENTS.md` is edited by the controller (diff approved by Milad).

**Tier 1 and key methods:**
- `Maybe` (`Present`, `Absent`, `present`, `absent`, `apply`, `fromOption`, `isPresent`/`isAbsent`, `get`, `getOrElse`, `getOrNull`, `map`, `flatMap`, `filter`, `fold`, `toOption`), `MaybeSyntax`;
- `Concat` (`Out`, `left`, `right`, `isIdentityLike`, `same`, `leftNothing`, `rightNothing`, `derive`), `Tuples`, `Eithers`, `Unions` (the type class, `Out`, `combine`, `separate`, `WithOut`);
- `Context` (`empty`, `apply`, `add`, `get`, `getOption`, `update`, `++`, `prune`, `size`, `isEmpty`), `ContextHas`.

**Reference pages:** `docs/reference/maybe.md`, `combinators.md`, `context.md`.
**Known issues:** "combinators: on Scala 2, `Tuples` nests a tuple on the right instead of concatenating it" (coordinator copy of `library-issues.md`, `/home/milad/sources/scala/zio-blocks-worktrees/tutorials-and-guides-lists/docs/dev/library-issues.md`); describe it as a known bug, don't re-file.

- [ ] Step 1: add the three modules, run the checker, fix the 2 `ContextHas` blocks.
- [ ] Step 2: unify the 3 drifting `Maybe` members (probes on 2.13.18 and 3.9.0).
- [ ] Step 3: tier 1 Scaladoc; fact-check table for all 25 existing comments.
- [ ] Step 4: tier 2 and tier 3 gaps (Scala 2 macro plumbing is tier 3).
- [ ] Step 5: gates, one commit per file group, report and issues file.

## Task 2: review and ship

- [ ] Opus fact-check review (fresh context), fix round (writer resumed), Sonnet scoped re-review.
- [ ] Merge `origin/main`, re-run gates, push `khajavi maybe-combinators-context-scaladoc`, open PR, watch CI to green.
- [ ] Report coverage before/after, the §13l text and the metric to the coordinator.
