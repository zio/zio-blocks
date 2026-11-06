# schema-messagepack and http-model-schema Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-05-msgpack-http-schema-scaladoc-design.md`

## In plain English

**The problem.** `schema-messagepack` turns Scala values into MessagePack bytes (a compact binary format, like JSON but smaller) and back. `http-model-schema` reads query parameters and headers of an HTTP request into Scala values. Only 11 of their 189 public members have comments, nobody has checked those comments are true, and no comment has a code example that CI compiles.

**The goal.** Every public member has a true, short comment. The main types have a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The helper project `scaladoc-check` pulls code examples out of comments and compiles them. Teach it the two modules' package names, add the modules to its list, and add a test.
2. **MessagePack (strong-model agent, then a cheaper one).** Write the comments, and check each claim (byte widths, record layout, `Option` handling) by running real code against the published 0.0.56 library.
3. **HTTP schema (strong-model agent).** Same for query and header decoding rules.
4. **Review.** A fresh agent re-runs the probes and every bug reproducer; authors fix findings; a last agent checks only the fixes.
5. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-05-msgpack-http-schema-scaladoc/global-constraints.md` (skill template with the two modules filled in).

## Task 1 (Opus): tooling and schema-messagepack tier 1

**Files:** `project/ScaladocSnippets.scala`, `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `build.sbt` (`scaladocCheckedModules` and its comment), `MessagePackFormat`, `MessagePackCodec`, and fact-check of existing comments in all five msgpack files.
**Reference page:** `docs/reference/schema/built-in-codecs/messagepack.md`.

- [ ] Step 1: the two `packageOverrides` entries, extractor tests, `scaladocCheckedModules`, build.sbt comment.
- [ ] Step 2: tier 1 for `MessagePackFormat` and `MessagePackCodec` with a compiled example (sensor readings); probe widths, string versus bin, records, `Option`, variants, errors.
- [ ] Step 3: fact-check table (member, claim, kind, evidence) for existing comments; bug write-ups with run reproducers.

## Task 2 (Sonnet): msgpack tier 2

- [ ] `MessagePackReader`, `MessagePackWriter`, `MessagePackCodecDeriver`: one to three sentences each, probing any behavioural claim; no absolute words unless verified; hygiene grep; commit; no sbt.

## Task 3 (Opus): http-model-schema

**Files:** all 13 shared files. **Tier 1:** `QueryCodec`, `HeaderCodec`, `RequestSchemaOps`, `ResponseSchemaOps`, `HeadersSchemaOps`, `QueryParamsSchemaOps`, `StringDecoder`. **Reference pages:** `docs/reference/http-model/schema.md`, `schema-codecs.md`.

- [ ] Step 1: tier 1 with compiled examples (orders API query parameters and headers); probe repeated params, missing versus empty, case-insensitive names, lists, defaults, error messages.
- [ ] Step 2: tier 2/3 for the rest (`ParamCodecSupport`, the derivers, formats, errors, `package.scala`).
- [ ] Step 3: fact-check table; bug write-ups reflecting the four known entries.

## Task 4: review and ship

- [ ] Fresh-context Opus fact-check (re-runs probes and every reproducer); fix round; Sonnet re-review of the fix diff.
- [ ] All gates; `git fetch origin && git merge origin/main`; push `khajavi msgpack-http-schema-scaladoc`; open PR; watch CI to green; no force-push, no merge.
- [ ] Send the coordinator: bug-entries file, PR number, review counts, CI result, §13m text and metric.
