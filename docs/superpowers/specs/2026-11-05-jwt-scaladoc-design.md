# JWT module Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `jwt` module (signing and verifying JSON Web Tokens) has comments on 62 of 146 public API members (42%). The biggest gaps are `Algorithm` (26 of 31 members undocumented: every case object and its `name`), `JwtError` (16 of 33: every `message`), `JwtAudience` (7 of 7), `JwtValue` (7 of 19), `JwtHeader` (5 of 5) and both crypto backends (5 of 5 each). Nobody has checked that the existing comments are true; earlier rollouts found 15% to 36% of inherited comments wrong or imprecise, and one jwt comment is already known to be wrong (the `JwtDecodeOptions` Scaladoc names `InvalidToken` for an audience mismatch, which is `InvalidClaim("aud", ...)`), as are two comments on `JwtSignOptions.limits`. For a security module a false claim about what is validated or rejected is worse than none. The one `{{{` block (the `Jwt` object overview) uses `...` placeholders and is never compiled.

## Goal

Every public member of `jwt` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` is empty.

## Decisions (Milad, 2026-11-05)

- **One PR** (tooling, the existing block made to compile, all Scaladoc).
- **Tier 1:** `Jwt` (with `JwtSignOptions`, `JwtDecodeOptions`, `JwtLimits`), `JwtClaims`, `JwtHeader`, `Algorithm`, `JwtError`, `JwtAudience`.
- **AGENTS.md:** add `jwt` to the `scaladoc-check` bullet and a downstream line `jwt*` → `scaladoc-check`, `docs` (diff shown to Milad before the edit).

## Layout

`jwt` is a `crossProject(JSPlatform, JVMPlatform)` with `CrossType.Full`, depending on `chunk` only. Shared sources (`jwt/shared/src/main/scala/zio/blocks/jwt/`): `Jwt.scala` (the `Jwt` entry point, options, `JwtCrypto` backend registry and `private[jwt]` JSON, HMAC and SHA-2 code), `JwtClaims`, `JwtHeader`, `JwtAudience`, `JwtValue`, `JwtError`, `Algorithm`, `Base64Url`, `EcdsaDer` (`private[jwt]`). Platform backends: `jvm/.../JvmJwtCryptoBackend.scala` (JCA) and `js/.../JsJwtCryptoBackend.scala` (Node `crypto`). No `scala-2/` or `scala-3/` splits. Cross versions are the `stdSettings` default, the same as `scaladoc-check`'s. The package is `zio.blocks.jwt`, so the wrapper import needs no override. The module is on `/api` (`unidocProjectFilter` has `jwt.jvm`). Reference page: `docs/reference/jwt.md` (`https://zioblocks.com/reference/jwt/`); guides `docs/guides/issue-and-verify-jwts.md` and `docs/guides/protect-endpoints-with-jwt.md`.

`jwt` main sources at `3ac1121a9` are identical to `v0.0.56`, so every probe runs against the published 0.0.56 artifacts, on the JVM and, where the backends differ, on Scala.js.

## Tasks

- **Task 1 (Opus):** add `jwt.jvm` to `scaladocCheckedModules`, an extractor test, the style-guide module list. Make the existing block compile. Tier 1 and every gap in `Jwt.scala`, `JwtClaims`, `JwtHeader`, `Algorithm`, `JwtError`, `JwtAudience`. Fact-check every existing comment in the module (all files, including `private[jwt]` code), with a table (member, claim, kind, evidence) and probes for algorithms per platform, `alg: none` and algorithm-confusion handling, `exp`/`nbf`/`iat` and clock skew, `aud` string vs array, key size and type checks, base64url padding, ECDSA DER/JOSE conversion, error types and messages. Bug write-ups. Only Task 1 runs sbt.
- **Task 2 (Sonnet, in parallel, disjoint files, no sbt):** the gaps in `JwtValue`, `Base64Url`, `JvmJwtCryptoBackend` and `JsJwtCryptoBackend`, with scala-cli probes for behavioural claims. Suspected bugs go to a suspects list for Opus to triage. Task 1 fact-checks the existing comments in these files after Task 2 commits.
- **Review:** a fresh-context Opus fact-check over every changed comment (probes on 2.13.18 and 3.9.0, Scala.js for backend claims, every bug reproducer re-run), a fix round, a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `jwtJVM/doc` and `jwtJS/doc` on 3.9.0 and 2.13.18: no new warnings
3. `python3 -I .claude/scripts/scaladoc-coverage.py jwt` (146/146) and `--drift` (empty)
4. `comments-only.py 3ac1121a9 jwt`
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`

## Security findings

A probe that shows a real weakness is not written into any tracked file, commit or PR. It goes to a gitignored ledger file and to the coordinator marked SECURITY; Milad decides how to report it.

## Out of scope

Library code changes, reference-page and guide rewrites (errors go to `docs-issues.md` entries via the coordinator), `docs/dev/` files, other modules.
