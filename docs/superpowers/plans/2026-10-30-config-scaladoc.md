# Config Family Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-10-30-config-scaladoc-design.md`

## In plain English

**The problem.** The config modules (loading typed settings from environment variables, files and feature flags) have comments on 39% of their public members. Nobody has checked that they are true, and about 20 known bugs mean some natural-sounding sentences would be wrong. Six code examples in comments are never compiled by the build.

**The goal.** Every public member has a true, short comment. The main types have a compiled example and a link to their reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The helper project `scaladoc-check` pulls code examples out of comments and compiles them. Add the four config modules to its list, add one test, update the style guide and two `AGENTS.md` lines.
2. **Core types and fact-check (Opus).** A strong-model agent makes the 6 examples compile, writes overview, example and link for the main types, and checks every existing comment against the code.
3. **Gaps (Sonnet).** A cheaper-model agent writes the small comments still missing.
4. **Review.** A fresh agent tests every changed claim against the code and the published 0.0.56 library on JVM (and JS where behaviour differs). The authors fix what it finds; a last agent re-checks only the fixes.
5. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to `article-coordinator`.

## Global Constraints

Full text: `.superpowers/sdd/2026-10-30-config-scaladoc/global-constraints.md` (skill template with the config modules filled in, plus the flags rule).

## Task 1 (Opus): tooling, seed, tier 1, fact-check

**Files:** `build.sbt` (`scaladocCheckedModules`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `docs/dev/scaladoc-style.md` (module list only), `AGENTS.md` (after Milad approves the diff), and all of `config/`, `config-hocon/`, `config-json/`, `config-yaml/`.

**Tier 1 and key methods:** `Config` (`load`, `loadOrThrow`, `wire`, ...), `ConfigSource` (`fromMap`, `fromEnv`, `fromProperties`, `orElse`, layering), `ConfigDecoder` / `ConfigDecoderDeriver`, `ConfigError`, `Provenance` / `ProvenanceMap` (`dump`), `Secret`, `DynamicFlag`, `StaticFlag`, `FlagSource`, `Rollout`, `HoconConfigSource`, `JsonConfigSource`, `YamlConfigSource`.
**Reference pages:** `docs/reference/config/{index,config-source,config-decoder,errors,flags,formats,rollout}.md` (`https://zioblocks.com/reference/config/<page>/`).
**Known issues:** the `config:`, `config-hocon:` and `config-yaml:` entries in `library-issues.md` (full coordinator copy: `/home/milad/sources/scala/zio-blocks-worktrees/tutorials-and-guides-lists/docs/dev/library-issues.md`).

- [ ] Step 1: add the four projects, the extractor test, run the checker, fix the 6 blocks.
- [ ] Step 2: unify the `EnvSource` / `SysPropSource` drift (state per-platform behaviour, Scala.js probe).
- [ ] Step 3: tier 1 Scaladoc; fact-check table for all existing comments.
- [ ] Step 4: gates, one commit per file group, report.

## Task 2 (Sonnet): tier 2 and 3 gaps

- [ ] Step 1: list every member the coverage script reports undocumented (after Task 1).
- [ ] Step 2: write comments; probe anything behavioural; no absolute words unless verified.
- [ ] Step 3: gates, commit, report.

## Task 3: review and ship

- [ ] Opus fact-check review (fresh context), fix round (authors resumed), Sonnet scoped re-review.
- [ ] Push `khajavi scaladoc-config`, open PR, watch CI to green.
- [ ] Report coverage before/after and the §13c text and metric to the coordinator.
