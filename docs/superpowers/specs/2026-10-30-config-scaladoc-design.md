# Config module family Scaladoc design

Follows `docs/superpowers/specs/2026-10-30-scope-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The config family has comments on 90 of 230 public API members (39%): `config` 79/202, `config.hocon` 9/22, `config.json` 0/2, `config.yaml` 2/4. Nobody has checked that the existing comments are true; the `chunk` and `scope` pilots found one in five to one in three inherited comments wrong. Six `{{{` blocks (all in `config`) are never compiled. Two members (`EnvSource`, `SysPropSource` in `PlatformConfigSource`) have different text in their `jvm/` and `js/` copies. About 20 known bugs touch these modules (HOCON parse errors without a position, `include` skipped silently, `${VAR}` not falling back to the environment, `${?x}` becoming `null`, provenance `dump` printing the whole environment, `Secret` printed by provenance, shared rollout buckets, and others); the comments must describe real behaviour.

## Goal

Every public member of `config`, `config-hocon`, `config-json` and `config-yaml` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

## Decisions (Milad, 2026-10-30)

- **One PR** for all four modules (tooling, existing blocks made to compile, Scaladoc).
- **Tier 1:** `Config`, `ConfigSource`, `ConfigDecoder` / `ConfigDecoderDeriver`, `ConfigError`, `Provenance` / `ProvenanceMap`, `Secret`, `DynamicFlag`, `StaticFlag`, `FlagSource`, `Rollout`, `HoconConfigSource`, `JsonConfigSource`, `YamlConfigSource`, each with key methods and companion.
- **Flags in examples:** examples are compiled, not run, so the flag registries are untouched. Every flag object on a page or file still gets a distinct name, and result comments come from a real run.
- **AGENTS.md:** the two lines (scaladoc-check bullet, downstream list) are edited after Milad sees the diff.
- **Platform differences** (`EnvSource`, `SysPropSource` on JVM vs Scala.js) get one shared text that states each platform's behaviour, verified with a Scala.js probe.

## Layout

`config/{shared,jvm,js}` (22 files), `config-hocon/{shared,jvm,js}` (9), `config-json/shared` (2), `config-yaml/shared` (2); packages `zio.blocks.config`, `.hocon`, `.json`, `.yaml`. All four are JVM+JS crossProjects. Wrapper import is only the module's own package, so hocon/json/yaml examples import `zio.blocks.config._` inside the block.

## Tasks

- **Task 1 (Opus):** add `config.jvm`, `` `config-hocon`.jvm ``, `` `config-json`.jvm ``, `` `config-yaml`.jvm `` to `scaladocCheckedModules`; extractor test; style-guide module list; AGENTS.md lines. Make the 6 blocks compile. Unify the 2 drifting members. Tier 1. Fact-check every existing comment (table: member, claim, kind, evidence). Bug write-ups.
- **Task 2 (Sonnet), after Task 1:** tier 2 and 3 gaps from the coverage report.
- **Review:** fresh-context Opus fact-check over every changed comment (probes against 0.0.56, JVM and JS where behaviour differs), a fix round, a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `configJVM/doc`, `configJS/doc` and the same for `config-hocon`, `config-json`, `config-yaml`: no new warnings (3.9.0 and 2.13.18)
3. `python3 -I .claude/scripts/scaladoc-coverage.py <module>` for each module, and `--drift`: no drift
4. `comments-only.py` against the base, excluding tooling files
5. `++2.13; check; headerCheckAll; ++3.9; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`

## Out of scope

Library code changes, reference-page rewrites (errors go to docs-issues.md entries), `docs/dev/` files, other modules. Open PRs #1666 and #1662 touch config docs; mentioned in the PR body, not waited for.
