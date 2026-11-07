# JWT Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-05-jwt-scaladoc-design.md`

## In plain English

**The problem.** The `jwt` module creates and checks JSON Web Tokens (JWTs): signed strings a server hands to a client to prove who the client is. Only 42% of its public members have comments, nobody has checked that those comments are true, and at least three are already known to be wrong. In a security library, a wrong comment ("this is rejected") can lead a user to skip a check they need. The one code example in the comments uses `...` placeholders and is never compiled.

**The goal.** Every public member has a true, short comment. The main types (`Jwt`, its option types, `JwtClaims`, `JwtHeader`, `Algorithm`, `JwtError`, `JwtAudience`) have a compiled example and a link to the reference page. Where the JVM and Scala.js versions behave differently (which algorithms and key formats they accept), the text says so. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The helper project `scaladoc-check` pulls code examples out of comments and compiles them. Add `jwt` to its list, add one test, update the style guide's module list and two `AGENTS.md` lines.
2. **Core types and fact-check (Opus).** A strong-model agent makes the existing example compile, writes the overview, example and link for the main types, fills every gap in their files, and checks every existing comment in the module against the code, with real tokens on the JVM and on Scala.js.
3. **Small gaps (Sonnet, at the same time, other files).** A cheaper-model agent writes the comments missing in `JwtValue`, `Base64Url` and the two crypto backends.
4. **Review.** A fresh agent tests every changed claim against the code and the published 0.0.56 library. The authors fix what it finds; a last agent re-checks only the fixes.
5. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator. A real security weakness, if found, stays out of every public place until Milad decides how to report it.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-05-jwt-scaladoc/global-constraints.md` (skill template with `jwt` filled in, plus the jwt-specific rules: probes per platform, fake keys, the SECURITY rule, overloaded-member links).

## Task 1 (Opus): tooling, seed, tier 1, fact-check

**Files:** `build.sbt` (`scaladocCheckedModules`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `docs/dev/scaladoc-style.md` (module list only), `jwt/shared/.../Jwt.scala`, `JwtClaims.scala`, `JwtHeader.scala`, `Algorithm.scala`, `JwtError.scala`, `JwtAudience.scala`, `EcdsaDer.scala`; after Task 2 commits, existing comments only in `JwtValue.scala`, `Base64Url.scala` and both backends. `AGENTS.md` is edited by the controller (diff approved by Milad).

**Tier 1 and key methods:** `Jwt` (`sign` overloads, `decode` overloads, `decodeUnsafe`, `decodeWithoutVerification`), `JwtSignOptions`, `JwtDecodeOptions`, `JwtLimits`, `JwtClaims` (fields, `parse`, `render`), `JwtHeader` (`parse`, `render`), `Algorithm` (each case, `name`, lookup), `JwtError` (each case and `message`), `JwtAudience` (`Single`, `Multiple`, constructors).
**Reference page:** `docs/reference/jwt.md` (`https://zioblocks.com/reference/jwt/`, anchors allowed).
**Known issues:** `library-issues.md` jwt entries at lines 1429 and 1474 (coordinator copy) and `docs-issues.md:338`.

- [ ] Step 1: add `jwt.jvm`, the extractor test, run the checker, make the `Jwt` overview block compile.
- [ ] Step 2: tier 1 Scaladoc and every gap in the Task 1 files.
- [ ] Step 3: fact-check table for all 62 existing comments, with probes (JVM and Scala.js where backends differ); fix the false ones; bug write-ups; triage Task 2's suspects.
- [ ] Step 4: gates, one commit per file group, report.

## Task 2 (Sonnet): gaps in JwtValue, Base64Url and the backends

- [ ] Step 1: list the undocumented members of the four files (baseline list in the ledger).
- [ ] Step 2: write the comments; probe anything behavioural with scala-cli against 0.0.56 (Scala.js for `JsJwtCryptoBackend`); no absolute words unless verified; suspects to `task-2-suspects.md`.
- [ ] Step 3: format, commit, report. No sbt.

## Task 3: review and ship

- [ ] Opus fact-check review (fresh context), fix round (authors resumed), Sonnet scoped re-review.
- [ ] Push `khajavi jwt-scaladoc`, open PR, watch CI to green.
- [ ] Report coverage before/after, the §13k text and metric to the coordinator.
