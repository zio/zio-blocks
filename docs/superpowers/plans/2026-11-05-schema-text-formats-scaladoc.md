# schema-toon, schema-xml and schema-yaml Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-05-schema-text-formats-scaladoc-design.md`

## In plain English

**The problem.** ZIO Blocks can read and write data as TOON, XML and YAML, each in its own module (`schema-toon`, `schema-xml`, `schema-yaml`). A user reads about these modules on the `/api` site and in IDE hovers, which show the Scaladoc comments in the source. Only 184 of their 542 public members have a comment, nobody has checked that those comments are true, and the two code examples in the XML comments are never compiled, so they can go stale.

**The goal.** Every public member has a true, short comment. The types a user meets first (for example `ToonCodec`, `Xml`, `Yaml`) also get an overview, a code example that the build compiles on every Scala version, and a link to the reference page. Real library bugs found on the way are written up, not fixed.

**The steps.**
1. **Tooling.** A helper project, `scaladoc-check`, pulls the code examples out of comments and compiles them. Add the three modules to its list, add one test, and update the style guide and `AGENTS.md` (Milad approves the `AGENTS.md` text first).
2. **Core types and fact-check (three Opus agents, one per module, in parallel).** Each writes the overview, example and link for its module's main types, makes the existing examples compile, and checks every existing comment against the code by running small programs ("probes") against the published library on Scala 2.13 and Scala 3.
3. **Gaps (three Sonnet agents, one per module, in parallel).** Cheaper agents write the short comments still missing. When something looks like a bug, they note it in a "suspects" file instead of guessing.
4. **Bug write-ups (one Opus agent).** It turns each suspect into a full entry with a reproducer it actually ran.
5. **Gates.** One agent at a time runs sbt (memory is tight): compile every example on three Scala versions, build the API docs, check coverage and that only comments changed.
6. **Review.** Fresh Opus agents, one per module, test every changed claim. The authors fix what they find; a Sonnet agent re-checks only the fixes.
7. **Ship.** One pull request; bugs go to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-05-schema-text-formats-scaladoc/global-constraints.md` (the skill template with the three modules filled in, plus the rules below).

- Writers never run sbt; they probe with scala-cli against `dev.zio::zio-blocks-schema-<fmt>:0.0.56` on `//> using scala 2.13.18` and `3.9.0` (Scala.js probe for number-formatting claims: `--js --js-version 1.22.0 --js-cli-on-jvm`). Only the controller runs sbt, one run at a time, after `free -g` shows 6 GB or more available.
- Writers share one worktree, so each commits only its own paths: `git commit -m "..." -- <paths>` (retry on `index.lock`), formatted first with `scala-cli fmt --scalafmt-conf .scalafmt.conf <files>`.
- Sample data fits the API (app settings, a product feed, sensor readings, a deployment manifest...); no books, no Zionomicon.
- Bugs: entries in `task-<n>-issues.md` (AGENTS.md shape, reproducer run for real); Sonnet agents write `task-3<m>-suspects.md` instead. In Scaladoc, "a known bug". Never edit `docs/dev/` issue files or the reference pages.

## Task 1 (controller): tooling

**Files:** `build.sbt` (`scaladocCheckedModules`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `docs/dev/scaladoc-style.md` (module list only), `AGENTS.md` (two lines, diff approved by Milad).

- [ ] Step 1: add `` `schema-toon`.jvm ``, `` `schema-xml`.jvm ``, `` `schema-yaml`.jvm ``; one extractor test (a toon path without `shared/` and the xml `scala-2.12-2.13` path give the right import and object name).
- [ ] Step 2: run the snippet check on the three versions; record the two xml blocks' state for Task 2a.
- [ ] Step 3: commit (`build: ...`, `docs(dev): ...`).

## Task 2 (three Opus agents in parallel): tier 1 and fact-check

Each agent owns every file of its module in this task. It fact-checks every existing comment (table: member, claim, kind, evidence) and writes the tier-1 docs.

- **2a schema-xml.** Tier 1: `Xml` (and its cases), `XmlCodec` (`encode`/`decode` family), `XmlFormat`, `syntax` (both copies, identical text), `XmlSelection`, `XmlPatch`. Make the `XmlBuilder` and `XmlSelection` blocks compile. 120 existing comments to fact-check.
- **2b schema-toon.** Tier 1: `ToonFormat`, `ToonCodec`, `ToonCodecDeriver` (its `with*` options), `ReaderConfig`, `WriterConfig`. Known bugs: library-issues toon entries (coordinator copy lines 189, 201, 213, 9057, 11611, 5804).
- **2c schema-yaml.** Tier 1: `Yaml` (and its cases), `YamlCodec`, `YamlFormat`, `YamlOptions`, the `yaml"..."` interpolator (`package.scala`, both copies, identical text). Known bugs: lines 70, 116, 5155, 5243, 5302, 5345, 5385, 5427, 5472, 5520, 5560, 7106, 12523, 12567.

- [ ] Step 1: research (source, tests, reference page), probes on 2.13.18 and 3.9.0.
- [ ] Step 2: write, fix false comments, record suspects/bugs in `task-2<x>-issues.md`.
- [ ] Step 3: format, commit per file group, report `task-2<x>-report.md`.

## Task 3 (three Sonnet agents in parallel, after Task 2): tier 2 and 3 gaps

- [ ] Step 1: controller lists each module's undocumented members (coverage script `scan()`), into `task-3<x>-brief.md`.
- [ ] Step 2: write comments; probe anything behavioural; no absolute words unless verified; suspects to `task-3<x>-suspects.md`.
- [ ] Step 3: format, commit, report.

## Task 4 (Opus): bug write-ups

- [ ] Triage all suspects; run each reproducer (scala-cli, pinned versions); write entries in `task-4-issues.md`; list Scaladoc lines to correct.

## Task 5 (controller): gates

- [ ] Gates 1–5 of the spec; fix failures with the owning agent.

## Task 6: review

- [ ] Three fresh-context Opus reviewers (one per module), probes on 2.13.18 and 3.9.0, reproducers re-run.
- [ ] Rulings recorded; fix round (authors resumed, every suggested sentence re-probed); Sonnet re-review of the fix diff until APPROVE.

## Task 7: ship

- [ ] `git fetch origin && git merge origin/main`, re-run gates; push `khajavi schema-text-formats-scaladoc`; open the PR (body in plain English with coverage before/after and review counts).
- [ ] CI to green; send the coordinator the PR number, bug entries, §13h text and metric.
