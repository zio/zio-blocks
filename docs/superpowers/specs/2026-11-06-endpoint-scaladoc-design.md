# Endpoint module Scaladoc design

Follows `docs/superpowers/specs/2026-11-05-jwt-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `endpoint` module (declarative HTTP endpoints, path and segment codecs, route matching) has comments on 65 of 314 public API members (20%). Nobody has checked that the existing comments are true; earlier rollouts found 15% to 40% of inherited comments wrong or imprecise. The module has one `{{{` block (in `Endpoint.scala`), never compiled. The Scala 2 and Scala 3 copies of `ErrorBuilder` carry different Scaladoc (the `--drift` report lists one entry).

## Goal

Every public member of `endpoint` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` is empty.

## Decisions (Milad, 2026-11-06)

- **One PR** (checker entry, the existing block made to compile, all Scaladoc).
- **Tier 1:** `Endpoint`, `HttpCodec`, `PathCodec`, `SegmentCodec`, `RoutePattern`, `RouteTree`, `AuthType`, and the Scala 3.7 group syntax and macro.
- **AGENTS.md:** add `endpoint` to the `scaladoc-check` bullet and a downstream line `endpoint*` -> `scaladoc-check`, `docs` (diff shown to Milad before the edit).

## Layout

`endpoint` is a `crossProject(JSPlatform, JVMPlatform)` with `CrossType.Full`, depending on `http-model`, `schema`, `combinators`, `mediatype` and `markdown`. All sources are under `endpoint/shared/src/main/`: `scala/` (`SegmentCodec` 902 lines, `Endpoint`, `PathCodec`, `HttpCodec`, `PathCodecRuntime`, `RouteTree`, `AuthType`, `RoutePattern`, `Alternator`), `scala-2/` and `scala-3/` (`AlternatorPlatformSpecific`, `PathCodecPlatformSpecific`, `SegmentCodecPlatformSpecific`, `EndpointUnionErrorBuilder`), and `scala-3.7/` (`EndpointGroupMacro` 727 lines, `EndpointGroupSyntax`). There is no `jvm/` or `js/` source. `BuildHelper.findApplicableScala3MinorDirs` includes `scala-3.7` on 3.9 and 3.10 (best exact match with X <= target) and excludes it on 2.13. Cross versions are the `stdSettings` default, the same as `scaladoc-check`'s. The package is `zio.blocks.endpoint`, so the wrapper import needs no override. The module is on `/api` (`endpoint.jvm` is in `unidocProjectFilter`). Reference pages: `docs/reference/endpoint/` (`index`, `endpoint`, `http-codec`, `path-codec`, `segment-codec`, `route-pattern`, `route-tree`, `auth-type`, `bulk-creation`).

`endpoint` main sources at `07f71de18` are identical to `v0.0.56`, so every probe runs against the published 0.0.56 artifacts on the JVM (Scala.js where a claim could differ: percent-encoding, number parsing).

## Tasks

- **Task 1 (Opus):** add `endpoint.jvm` to `scaladocCheckedModules`, the style-guide module and tier-1 lists. Make the existing block compile. Tier 1 and every gap in `Endpoint`, `HttpCodec`, `PathCodec`, `SegmentCodec`, `RoutePattern`, `RouteTree`, `AuthType`, the group syntax and macro. Fact-check every existing comment in the module (all files), with a table (member, claim, kind, evidence) and probes for path matching and precedence (literal vs parameter, trailing slash, `Method.ANY`), percent-encoding, segment parse failures and messages, `HttpCodec` composition and status handling, `AuthType` header formats, group macro output. Bug write-ups. Only Task 1 runs sbt.
- **Task 2 (Sonnet, in parallel, disjoint files, no sbt):** gaps in `PathCodecRuntime`, `Alternator`, the `*PlatformSpecific` files and `EndpointUnionErrorBuilder` (identical text in both copies), with scala-cli probes for behavioural claims. Suspected bugs go to a suspects list for triage.
- **Review:** a fresh-context Opus fact-check over every changed comment (probes on 2.13.18 and 3.9.0, every bug reproducer re-run), a fix round, a re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `endpointJVM/doc` and `endpointJS/doc` on 3.9.0 and 2.13.18: no new warnings (read the logs)
3. `python3 -I .claude/scripts/scaladoc-coverage.py endpoint` (314/314) and `--drift` (empty)
4. `comments-only.py 07f71de18 endpoint`
5. `++2.13.18; check; headerCheckAll`, `scalafmtAll`, `scalafmtSbt`

## Known related bugs

`Method.ANY` expands to every standard method in `RoutePattern` (a library-issues entry on `Method.ANY` already exists), and the Scala 3 union error builder may route a value to the wrong error codec for hierarchies (read from code, not probed). Probe both; send amendments to the existing entries, not new ones.

## Security findings

A probe that shows a real weakness goes to a gitignored ledger file and to the coordinator marked SECURITY, never into a tracked file, commit or PR.

## Out of scope

Library code changes, reference-page rewrites (errors go to the coordinator as docs-issues entries), `docs/dev/` files, other modules, CI files.
