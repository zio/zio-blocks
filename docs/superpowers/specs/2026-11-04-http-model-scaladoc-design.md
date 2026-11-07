# http-model module Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `http-model` module (package `zio.http`) has comments on 57 of 1,113 public API members (5%), the largest gap in the repository. `Header.scala` alone holds 648 members (about 75 typed headers, each with `headerName`, `renderedValue`, `name`, `parse` and `render`), with 2 documented. The `Status` pair (`scala-2/` and `scala-3/`) has 133 members and none documented. The module has no `{{{` examples, so nothing it shows is compiled. Nobody has checked the 57 existing comments; earlier rollouts found 19% to 36% of inherited comments wrong or imprecise. `endpoint`, `htmx`, `datastar`, `http-model-schema` and nine web guides build on this module, so its hovers matter.

The example checker, `scaladoc-check`, cannot take the module as it is: every example gets `import zio.blocks.<module-dir>._`, which for `http-model` names a package that does not exist (`zio.blocks.http.model`).

## Goal

Every public member of `http-model` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` stays empty.

## Decisions (Milad, 2026-11-04)

- **One PR:** tooling and all 1,113 members.
- **Tier 1:** `Request`, `Response`, `Body`, `URL`, `Path`, `QueryParams`, `Headers`, `Header` (with `Header.Codec`), `Method`, `Status` (both copies), `Cookie`, `ServerSentEvent`, each with its key methods. Everything else is tier 2: `Scheme`, `Version`, `Form`, `FormField`, `ContentType`, `Charset`, `Boundary`, `PercentEncoder`, and the typed header case classes and companions.
- **Checker override:** a lookup table `packageOverrides` in `project/ScaladocSnippets.scala`, from module directory to package (`"http-model" -> "zio.http"`). `moduleImport` uses it for the import; `objectName` uses it to strip the package directory from the wrapper name, so names read `Snippet_http_model_shared_scala_Request_L12`. Modules not in the table keep the old rule. A unit test covers it, and the `build.sbt` comment above `scaladocCheckedModules` points at the table.
- **AGENTS.md** (diff approved): a downstream line `http-model*` → `scaladoc-check`, `http-model-schema*`, `endpoint*`, `datastar*`, `htmx*`, `docs`; the `scaladoc-check` bullet names `http-model` and points new modules at `packageOverrides`.

## Layout

`http-model/shared/src/main/scala/zio/http/` (19 files), `shared/src/main/scala-2/` and `scala-3/` (`Status.scala`), `jvm/` and `js/` (`CharsetPlatformSpecific.scala`; the JS copy is an empty `private[http]` trait). All files are package `zio.http`. The module is on `/api` (`unidocProjectFilter`). It compiles on 2.13.18 (main, Test, JS); its tests are not in the Scala 2 test aliases, which is out of scope here.

Reference pages: `docs/reference/http-model/model.md` (`https://zioblocks.com/reference/http-model/model/`), `headers.md` (`.../headers/`), `server-sent-event.md` (`.../server-sent-event/`). Errors found there go to the coordinator as `docs-issues.md` entries; the pages are not edited in this PR.

`http-model` main sources at the base (`5eb809c8f`) are byte-identical to `v0.0.56`, so every probe runs against the published `zio-blocks-http-model:0.0.56`, and bug citations use base lines ("lines at v0.0.56 (= 5eb809c8f)").

## Known bugs to reflect

From `library-issues.md`: `WWW-Authenticate` and other auth-parameter headers neither escape nor parse quoted strings; `QueryParams` keeps `+`; `Request#cookies` reads only the first `Cookie` header; no `text/event-stream` content type; `ServerSentEvent` always writes a `data:` line; `Header.Authorization.Basic`, `Request` and `Headers` print credentials in `toString`. Scaladoc describes the real behaviour and calls each "a known bug".

## Tasks

- **Task 1 (controller):** the `packageOverrides` table and its test, `http-model.jvm` in `scaladocCheckedModules`, the `build.sbt` comment, the style guide's module list and the AGENTS.md diff.
- **Task 2 (Opus):** tier 1 for the 12 types plus every other member in their files (`Request`, `Response`, `Body`, `URL`, `Path`, `QueryParams`, `Method`, the `Status` pair, `Headers`, `Cookie`, `ServerSentEvent`, and the top of `Header.scala`: `Header`, `Codec`, `Typed`, `Custom`, the `Cookie`/`SetCookie` aliases). Fact-check every existing comment in the module (table: member, claim, kind, evidence). Bug write-ups.
- **Task 3a (Sonnet), after Task 2:** the typed headers in `Header.scala`.
- **Task 3b (Sonnet), in parallel with 3a:** `Scheme`, `Version`, `Form`, `FormField`, `ContentType`, `Charset` (+ the JVM `toJava`), `Boundary`, `PercentEncoder`.
- **Review:** two fresh-context Opus fact-checkers on disjoint file groups (A: `Header.scala`, `Headers`, `Cookie`; B: everything else plus tooling), probing parsing and rendering with real inputs (empty, encoded, repeated, case), on 2.13.18 and 3.9.0, and on Scala.js where platform code differs. A fix round, then a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `http-modelJVM/doc` and `http-modelJS/doc` on 3.9.0 and 2.13.18: no new warnings
3. `python3 -I .claude/scripts/scaladoc-coverage.py http-model` (1113/1113) and `--drift` (empty)
4. `comments-only.py` against the base for `http-model/` (tooling files excluded)
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`

## Out of scope

Library code changes, reference-page rewrites, `docs/dev/` files, `http-model-schema` and other modules, the missing Scala 2 test aliases.
